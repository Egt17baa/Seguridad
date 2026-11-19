package app.seguridad.redteam

import app.seguridad.core.appJson
import app.seguridad.crypto.Aead
import app.seguridad.crypto.CryptoFailure
import app.seguridad.crypto.NONCE_BYTES
import app.seguridad.crypto.aead
import app.seguridad.crypto.randomBytes
import app.seguridad.vault.COPY_DAMAGED
import app.seguridad.vault.EntryType
import app.seguridad.vault.Folder
import app.seguridad.vault.MAX_TAGS
import app.seguridad.vault.VaultCrypto
import app.seguridad.vault.VaultData
import app.seguridad.vault.VaultFile
import app.seguridad.vault.VaultEntry
import app.seguridad.vault.VaultLimits
import app.seguridad.vault.VaultState
import app.seguridad.vault.VaultStore
import app.seguridad.vault.checkBackup
import app.seguridad.vault.dataAad
import app.seguridad.vault.withBackupChecked
import app.seguridad.vault.writeBackup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import java.util.Random
import java.util.UUID

/**
 * Round 2 of the fixes of the attack, point 8 (the director's closure, duda 3; R5): «Comprobar una copia» checks what
 * «Importar copia» would seal (a copy of before 2.0 upgraded to v4 and fitted, `VaultCrypto.readAsImported`), with the
 * same checks in the same order (`checkCopyData`: structure, size, entries). For every copy here, «Comprobar» passes if
 * and only if «Importar» does, and with the same message; the report shows the tag as read, never the one the upgrade
 * makes. Invented data and the real files of the tests' resources only.
 */
class RedTeam20CheckAsImportTest {
    @get:Rule val tmp = TemporaryFolder()

    private val rnd = Random(20261118L)
    private var k = 0
    private fun distinct(n: Int) = List(n) { (k++).toString(36).padStart(5, 'a') }
    private val v143 = "maestra-de-prueba-143"

    private fun resource(name: String) = javaClass.getResourceAsStream("/seguridad/$name")!!.use { it.readBytes() }
    private fun sgbak(vaultFileText: String, vaultVersion: Int? = null) =
        ByteArrayOutputStream().also { writeBackup(it, vaultFileText, emptyMap(), vaultVersion = vaultVersion) }.toByteArray()

    /**
     * The real copy of 1.4.3 (version 3) with its first [entries] entries changed by [change] (as 1.x let them be
     * written by hand), sealed again with its own key, as a `seguridad-backup` copy; with [marker3], under the marker of
     * 2.0 (33 MiB for its `vault.json`, which the reader takes with data of any version), as one made by hand.
     */
    private fun oneX(entries: Int = 1, marker3: Boolean = false, change: (JsonObject) -> JsonObject): ByteArray {
        val file = VaultCrypto.decodeFile(resource("vault-1.4.3.json").decodeToString())
        val dek = VaultCrypto.unlock(file, v143.toCharArray())
        val version = VaultCrypto.resolve(file, dek).version
        val list = appJson.parseToJsonElement(VaultCrypto.readJson(file, dek)).jsonArray
        check(list.size >= entries)
        val json = JsonArray(list.mapIndexed { i, e -> if (i < entries) change(e.jsonObject) else e }).toString().toByteArray()
        val nonce = randomBytes(NONCE_BYTES)
        val sealed = file.copy(data = Base64.getEncoder().encodeToString(nonce + aead(Aead.AES_256_GCM, true, dek, nonce, dataAad(file, version), json)))
        return sgbak(VaultCrypto.encodeFile(sealed), if (marker3) VaultFile.VERSION else null)
    }

    private fun tagsAndOldPasswords(tags: Int, oldPasswords: Int): (JsonObject) -> JsonObject = { o ->
        JsonObject(
            o + ("tags" to JsonArray(distinct(tags).map { JsonPrimitive(it) })) +
                ("passwordHistory" to JsonArray(List(oldPasswords) { JsonObject(mapOf("password" to JsonPrimitive("antigua-$it"), "changedAt" to JsonPrimitive(1_700_000_000_000L + it))) })),
        )
    }

    private fun store() = VaultStore(File(tmp.newFolder(), "vault.bin"), Dispatchers.Unconfined, Dispatchers.Unconfined, kdf = { RT20_KDF })
        .apply { runBlocking { load() } }

    /** «Importar copia» on an empty slot: "OK" or the message it refuses with (nothing imported then). */
    private fun imported(copy: ByteArray, master: String): String {
        val s = store()
        return try {
            runBlocking { s.importReplace(ByteArrayInputStream(copy), master.toCharArray()) }
            check(s.state.value is VaultState.Unlocked)
            "OK"
        } catch (e: CryptoFailure) {
            check(s.state.value == VaultState.NoVault)
            e.message ?: "?"
        }
    }

    /** «Comprobar una copia»: "OK" or the message it refuses with. */
    private fun checked(copy: ByteArray, master: String): String = try {
        runBlocking { checkBackup(ByteArrayInputStream(copy), master.toCharArray(), tmp.newFolder(), Dispatchers.Unconfined, Dispatchers.Unconfined) }
        "OK"
    } catch (e: CryptoFailure) {
        e.message ?: "?"
    }

    /** Both say the same: [expected] ("OK" or a message). */
    private fun same(name: String, copy: ByteArray, master: String, expected: String) {
        assertEquals("$name: «Importar copia»", expected, imported(copy, master))
        assertEquals("$name: «Comprobar una copia» says what «Importar copia» does", expected, checked(copy, master))
    }

    /**
     * Two entries of 2 010 tags with 5 old passwords each: 4 000 over 10 as stored, but every old password becomes a
     * version with the entry's tags once upgraded (fitted to 64 KiB of history), well over 10 000: both refuse.
     */
    @Test fun aCopyOfBefore20IsCheckedAsItIsImported() {
        same("1.x with versions made by the upgrade", oneX(entries = 2, change = tagsAndOldPasswords(MAX_TAGS + 2_000, 5)), v143, COPY_DAMAGED)
    }

    /** A 1.x (version 3) whose notes pass 24 MiB + 4 KiB: the import fits it and refuses it by its size; so does the check. */
    @Test fun aCopyOfBefore20OverTheSizeIsRefusedByBoth() {
        val notes = "n".repeat(VaultLimits.VAULT_MAX_DATA_BYTES + VaultLimits.COPY_DATA_MARGIN_BYTES + 1)
        same("1.x over 24 MiB", oneX(marker3 = true) { o -> JsonObject(o + ("notes" to JsonPrimitive(notes))) }, v143, COPY_DAMAGED)
    }

    /**
     * A copy with two defects (two folders with one id, a damaged structure; and two identities alive): the same message
     * from both, the one «Importar» meets first (the structure, COPY_DAMAGED).
     */
    @Test fun aCopyWithTwoDefectsGivesTheSameMessage() {
        fun identity(id: String) = VaultEntry(id, EntryType.IDENTITY, "Yo", createdAt = 1, updatedAt = 1)
        val data = VaultData(
            entries = listOf(identity(UUID(rnd.nextLong(), rnd.nextLong()).toString()), identity(UUID(rnd.nextLong(), rnd.nextLong()).toString())),
            folders = listOf(Folder("f", "Uno"), Folder("f", "Dos")),
            vaultTag = "b".repeat(32),
        )
        val copy = rt20Copy(rt20HostileFile(appJson.encodeToString(VaultData.serializer(), data)).first)
        same("two defects", copy, RT20_MASTER, COPY_DAMAGED)
    }

    /** Checking a copy of before 2.0 still gives the tag as read (none), so no date of a check is ever kept for it. */
    @Test fun aCopyOfBefore20StillHasNoTagWhenChecked() {
        val report = runBlocking {
            checkBackup(ByteArrayInputStream(oneX(change = tagsAndOldPasswords(50, 5))), v143.toCharArray(), tmp.newFolder(), Dispatchers.Unconfined, Dispatchers.Unconfined)
        }
        assertEquals("", report.vaultTag)
        assertNull(VaultData(vaultTag = "a".repeat(32)).withBackupChecked(report.vaultTag, 1_800_000_000_000L))
    }

    /**
     * The differential test: the four real files of the tests' resources (vault-1.1.json through backup-1.1.sgbak, which
     * holds it byte for byte with its attachment; the others wrapped as a copy) and a 1.x with 50 tags and 5 old passwords
     * pass both; and for every copy of this class, «Comprobar» passes if and only if «Importar» does, with the same message.
     */
    @Test fun checkPassesIfAndOnlyIfImportDoes() {
        val copies = listOf(
            Triple("vault-1.4.3.json", sgbak(resource("vault-1.4.3.json").decodeToString()), v143),
            Triple("export-1.0.2.json", sgbak(resource("export-1.0.2.json").decodeToString()), "maestra-de-prueba"),
            // Holds vault-1.1.json byte for byte, with its attachment (README.txt of the resources).
            Triple("backup-1.1.sgbak (vault-1.1.json)", resource("backup-1.1.sgbak"), "maestra-de-prueba"),
            Triple("1.x with 50 tags and 5 old passwords", oneX(change = tagsAndOldPasswords(50, 5)), v143),
        )
        for ((name, copy, master) in copies) same(name, copy, master, "OK")
        val refused = listOf(
            "1.x, versions made by the upgrade" to oneX(entries = 2, change = tagsAndOldPasswords(MAX_TAGS + 2_000, 5)),
            "1.x, 4 000 over 10 and no old password" to oneX(entries = 2, change = tagsAndOldPasswords(MAX_TAGS + 2_000, 0)),
        )
        for ((name, copy) in refused) {
            val i = imported(copy, v143)
            assertEquals("$name: «Comprobar» passes if and only if «Importar» does", i, checked(copy, v143))
        }
    }
}
