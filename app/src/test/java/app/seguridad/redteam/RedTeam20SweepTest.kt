package app.seguridad.redteam

import app.seguridad.AppContainer
import app.seguridad.SETTINGS_FILE
import app.seguridad.crypto.TotpSecret
import app.seguridad.imports.ApplyResult
import app.seguridad.imports.DuplicateChoice
import app.seguridad.imports.ImportFormat
import app.seguridad.imports.ImportRead
import app.seguridad.imports.ImportedItem
import app.seguridad.imports.importedEntry
import app.seguridad.imports.planMerge
import app.seguridad.settings.withTargetAdded
import app.seguridad.vault.BackupSchedule
import app.seguridad.vault.EntryType
import app.seguridad.vault.VAULTS_FILE
import app.seguridad.vault.VaultEntry
import app.seguridad.vault.VaultLimits
import app.seguridad.vault.VaultState
import app.seguridad.vault.VaultStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/*
 * Final attack of Clavero 2.0 (R32), phase 3: the regression sweep over the whole app (as in 1.4.0). The tests of 1.4.0's
 * attack (RedTeamCryptoTest, RedTeamImportTest, RedTeamSecondAttackTest, RedTeamRecheckTest) run again on the 2.0 code in
 * the same gate. Report: docs/superpowers/handoff/sdd-2.0/redteam-report.md.
 */
class RedTeam20SweepTest {
    @get:Rule val tmp = TemporaryFolder()

    private var clock = 1_800_000_000_000L

    private fun store() = VaultStore(
        File(tmp.newFolder(), "vault.bin"), Dispatchers.Unconfined, Dispatchers.Unconfined, now = { clock }, kdf = { RT20_KDF }, elapsed = { clock },
    )

    private fun entries(s: VaultStore) = (s.state.value as VaultState.Unlocked).entries

    /**
     * Sweep, «Importar de otra app» on a v4 vault. The vault has «Banco» / ana with a full history (20 versions), a twin
     * of it in the trash, and a 2FA in the trash whose key the file brings again. The plan only sees the live entries
     * (VaultStore.kt:62, :825), so:
     * - «Reemplazar» changes the live «Banco», whose history stays at 20 (the oldest goes, EntryHistory.kt:34-39) with the
     *   version it had first, and the import is not refused as too big;
     * - the twin in the trash is not touched, and the 2FA in the trash is no duplicate: a new 2FA comes in, and the one in
     *   the trash stays there (design «Papelera»: what is in the trash is not a duplicate of an import).
     */
    @Test fun resistsAnImportOverAV4VaultWithHistoryAndTrash() = runBlocking {
        val s = store().apply { load(); create(RT20_MASTER.toCharArray()) }
        repeat(VaultLimits.HISTORY_MAX_VERSIONS + 2) { i ->
            clock += 60_000
            s.upsert(VaultEntry("b", EntryType.LOGIN, "Banco", username = "ana", password = "clave-$i", createdAt = 1, updatedAt = clock))
        }
        s.upsert(VaultEntry("t", EntryType.LOGIN, "Banco", username = "ana", password = "en-la-papelera", createdAt = 1, updatedAt = 1))
        s.upsert(VaultEntry("z", EntryType.TOTP, "Correo", totp = TotpSecret("JBSWY3DPEHPK3PXP"), createdAt = 1, updatedAt = 1))
        assertEquals(setOf("t", "z"), s.trash(setOf("t", "z")))
        assertEquals(VaultLimits.HISTORY_MAX_VERSIONS, entries(s).single { it.id == "b" }.history.size)
        val read = ImportRead(
            ImportFormat.BROWSER_CSV,
            listOf(
                ImportedItem(0, importedEntry(EntryType.LOGIN, "Banco", username = "ana", password = "la-del-archivo"), emptyList()),
                ImportedItem(1, importedEntry(EntryType.TOTP, "Correo", totp = TotpSecret("JBSWY3DPEHPK3PXP")), emptyList()),
            ),
            emptyList(), 0,
        )
        val plan = planMerge(read, entries(s), s.revision.value)
        assertEquals(ApplyResult.Done(added = 1, replaced = 1), s.applyImport(plan, DuplicateChoice.REPLACE, emptyMap()))
        val data = (s.state.value as VaultState.Unlocked).data
        val bank = data.live.single { it.id == "b" }
        assertEquals("la-del-archivo", bank.password)
        assertEquals(VaultLimits.HISTORY_MAX_VERSIONS, bank.history.size)
        assertEquals("clave-${VaultLimits.HISTORY_MAX_VERSIONS + 1}", bank.history.first().entry.password)
        assertEquals("en-la-papelera", data.trash.single { it.id == "t" }.password)
        assertEquals(setOf("t", "z"), data.trash.map { it.id }.toSet())
        assertEquals(2, data.entries.count { it.type == EntryType.TOTP })
    }

    /**
     * Sweep, «Lo que se guarda sin cifrar» (row 8): with two vaults, their names, an entry each, a folder of copies and
     * the clock marks written, `settings.json` and `vaults.json` (the app's private files) never hold a vault's name, a
     * title, a user, a password or a 2FA key, and `vaults.json` holds only ids, colours, order and the last one opened
     * (vault/VaultRegistry.kt:35-40).
     */
    @Test fun resistsSecretsInThePlainFiles() = runTest {
        val c = AppContainer(tmp.root, tmp.newFolder("cache"), appVersion = "test", appScope = backgroundScope)
        c.settings.load()
        c.vault.load()
        c.vault.create(RT20_MASTER.toCharArray(), name = "NombreSecretoUno")
        c.vault.upsert(VaultEntry("x", EntryType.LOGIN, "TituloSecreto", username = "UsuarioSecreto", password = "ClaveSecreta", totp = TotpSecret("JBSWY3DPEHPK3PXQ"), createdAt = 1, updatedAt = 1))
        c.settings.updateVault(c.vault.id) { it.withTargetAdded("content://carpeta", BackupSchedule.DAILY) }
        val b = c.createVault("NombreSecretoDos", "maestra-de-prueba-dos-rt".toCharArray())
        c.vault.upsert(VaultEntry("y", EntryType.NOTE, "OtroTitulo", notes = "NotaSecreta", createdAt = 1, updatedAt = 1))
        c.vault.lock()
        val settings = File(tmp.root, SETTINGS_FILE).readText()
        val vaults = File(tmp.root, VAULTS_FILE).readText()
        for (secret in listOf("NombreSecreto", "TituloSecreto", "UsuarioSecreto", "ClaveSecreta", "JBSWY3DPEHPK3PXQ", "OtroTitulo", "NotaSecreta", RT20_MASTER)) {
            assertFalse("settings.json holds «$secret»", settings.contains(secret))
            assertFalse("vaults.json holds «$secret»", vaults.contains(secret))
        }
        fun keys(e: JsonElement): Set<String> = when (e) {
            is JsonObject -> e.keys + e.values.flatMap(::keys)
            is JsonArray -> e.flatMap(::keys).toSet()
            else -> emptySet()
        }
        assertTrue(keys(Json.parseToJsonElement(vaults)).let { "vaults" in it && it - setOf("vaults", "id", "color", "order", "lastOpened") == emptySet<String>() })
        assertTrue(vaults.contains(b))
    }

    /**
     * Sweep, every window filters obscured touches (android/SecureWindow.kt:36-52). PlatformSourcesTest (:548-567) checks
     * that a call of `FilterObscuredTouches()` is inside each `AlertDialog`, `Dialog`, `DropdownMenu`, `Popup`,
     * `ModalBottomSheet`… call. A window nested in another (a menu in a dialog) could pass that test with only the inner
     * one's call. This checks each window on its own: a call inside it and outside every window nested in it. No other
     * kind of window is used (no `TooltipBox`, `PopupWindow`, View `AlertDialog.Builder`, `DialogFragment`, search bars or
     * pickers: grep, report «Fase 3»).
     */
    @Test fun resistsAWindowWhoseOnlyFilterIsInAnotherWindow() {
        val root = generateSequence(File(".").absoluteFile) { it.parentFile }.map { File(it, "app/src/main/java/app/seguridad") }.first { it.isDirectory }
        val window = Regex("""\b(AlertDialog|BasicAlertDialog|Dialog|DropdownMenu|ExposedDropdownMenu|ModalBottomSheet|Popup|DatePickerDialog)\(""")
        val missing = ArrayList<String>()
        var windows = 0
        root.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { f ->
            val b = blankStringsAndComments(f.readText())
            val ranges = window.findAll(b).filter { m ->
                val line = b.substring(b.lastIndexOf('\n', m.range.first) + 1, m.range.first)
                !line.trimStart().startsWith("import") && !line.trimEnd().endsWith("fun")
            }.map { m -> m.range.first..callEnd(b, m.range.first) }.toList()
            for (r in ranges) {
                windows++
                val nested = ranges.filter { it != r && it.first > r.first && it.last <= r.last }
                val own = Regex("""FilterObscuredTouches\(\)""").findAll(b.substring(r.first, r.last + 1))
                    .any { m -> val at = r.first + m.range.first; nested.none { at in it } }
                if (!own) missing += "${f.name}:${b.substring(0, r.first).count { it == '\n' } + 1}"
            }
        }
        assertTrue("Too few windows found: $windows", windows >= 15)
        assertEquals(emptyList<String>(), missing)
    }

    private fun blankStringsAndComments(s: String): String {
        val out = s.toCharArray()
        var i = 0
        fun blank(from: Int, to: Int) { for (k in from until minOf(to, s.length)) if (out[k] != '\n') out[k] = ' ' }
        while (i < s.length) {
            when {
                s.startsWith("//", i) -> { val j = s.indexOf('\n', i).let { if (it < 0) s.length else it }; blank(i, j); i = j }
                s.startsWith("/*", i) -> { val j = s.indexOf("*/", i + 2).let { if (it < 0) s.length else it + 2 }; blank(i, j); i = j }
                s.startsWith("\"\"\"", i) -> { val j = s.indexOf("\"\"\"", i + 3).let { if (it < 0) s.length else it + 3 }; blank(i + 3, j - 3); i = j }
                s[i] == '"' -> {
                    var j = i + 1
                    while (j < s.length && s[j] != '"') { if (s[j] == '\\') j++; j++ }
                    blank(i + 1, j); i = j + 1
                }
                s[i] == '\'' && i + 2 < s.length && (s[i + 2] == '\'' || s[i + 1] == '\\') -> {
                    val j = s.indexOf('\'', i + 2); blank(i + 1, j); i = j + 1
                }
                else -> i++
            }
        }
        return String(out)
    }

    private fun closing(b: String, open: Int): Int {
        val o = b[open]
        val c = if (o == '(') ')' else '}'
        var depth = 0
        for (k in open until b.length) {
            if (b[k] == o) depth++ else if (b[k] == c && --depth == 0) return k
        }
        return b.length - 1
    }

    /** The end of the call that starts at [start]: its parentheses, and its trailing lambda if it has one. */
    private fun callEnd(b: String, start: Int): Int {
        var end = closing(b, b.indexOf('(', start))
        var k = end + 1
        while (k < b.length && b[k].isWhitespace()) k++
        if (k < b.length && b[k] == '{') end = closing(b, k)
        return end
    }
}
