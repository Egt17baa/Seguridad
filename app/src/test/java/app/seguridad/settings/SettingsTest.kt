package app.seguridad.settings

import app.seguridad.vault.BackupSchedule
import app.seguridad.vault.BackupTargetSettings
import app.seguridad.vault.PRIMARY_VAULT_ID
import app.seguridad.vault.AutoBackupResult
import app.seguridad.vault.AUTO_BACKUP_OLD_NOT_DELETED
import app.seguridad.vault.AUTO_BACKUP_FAILED
import app.seguridad.vault.AUTO_BACKUP_REPLACE_WAITS
import app.seguridad.crypto.CryptoFailure
import app.seguridad.crypto.KDF_OUT_OF_MEMORY
import app.seguridad.crypto.KdfParams
import app.seguridad.crypto.MAX_KDF_MEMORY_KIB
import app.seguridad.crypto.Secret
import app.seguridad.crypto.decryptText
import app.seguridad.crypto.encryptText
import app.seguridad.crypto.outOfMemoryAsFailure
import app.seguridad.vault.VaultCrypto
import app.seguridad.vault.VaultStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SettingsTest {
    @get:Rule val tmp = TemporaryFolder()
    private fun file() = File(tmp.root, "settings.json")
    private fun store(heapKiB: Long = 1024L * 1024) = SettingsStore(file(), Dispatchers.Unconfined, heapKiB = { heapKiB })

    @Test fun defaults() = runTest {
        val s = store().apply { load() }
        assertEquals(Settings(), s.settings.value)
        assertEquals(LockAfter.MIN_5, s.settings.value.lockAfter)
        assertEquals(ClipboardClear.S45, s.settings.value.clipboard)
        assertEquals(KdfProfile.STANDARD, s.settings.value.principal.kdf)
        assertEquals(ThemeMode.DARK, s.settings.value.theme)
        assertFalse(s.settings.value.noticeAccepted)
    }

    @Test fun persistsAndReloads() = runTest {
        store().apply {
            load()
            update { it.copy(lockAfter = LockAfter.MIN_1, clipboard = ClipboardClear.S15, theme = ThemeMode.SYSTEM, noticeAccepted = true) }
            updatePrincipal { it.copy(kdf = KdfProfile.HIGH) }
        }
        val again = store().apply { load() }
        assertEquals(LockAfter.MIN_1, again.settings.value.lockAfter)
        assertEquals(ClipboardClear.S15, again.settings.value.clipboard)
        assertEquals(ThemeMode.SYSTEM, again.settings.value.theme)
        assertEquals(KdfProfile.HIGH, again.settings.value.principal.kdf)
        assertTrue(again.settings.value.noticeAccepted)
    }

    /**
     * 2.0, tarea 5 bis: un settings.json de la 1.4.3 no tiene aspecto (ni estilo, ni paleta, ni «Alto contraste»; la 1.4.x
     * no tenía colores de acento): se lee en «Noche» y «Cristal», con el modo que tenía, y nada más cambia.
     */
    @Test fun oldSettingsGetNightAndGlass() = runTest {
        for (mode in ThemeMode.entries) {
            file().writeText(
                """{"lockAfter":"MIN_1","clipboard":"S15","theme":"${mode.name}","noticeAccepted":true,""" +
                    """"clockHighWaterMs":1761500000000,"renameNoticePending":false}""",
            )
            val s = store().apply { load() }.settings.value
            assertEquals(mode, s.theme)
            assertEquals(ThemeStyle.GLASS, s.style)
            assertEquals(PaletteId.NIGHT, s.palette)
            assertNull(s.custom)
            assertFalse(s.reduceTransparency)
            assertFalse(s.highContrast)
            assertEquals(LockAfter.MIN_1, s.lockAfter)
            assertEquals(1761500000000, s.principal.clockHighWaterMs)
        }
    }

    /** Un aspecto que no se entiende no tira los demás ajustes: la paleta vuelve a «Noche» y los tonos quedan entre los 12. */
    @Test fun aStrangeAppearanceKeepsTheOtherSettings() = runTest {
        file().writeText(
            """{"lockAfter":"MIN_1","theme":"LIGHT","style":"FROSTED","palette":"PINK",""" +
                """"custom":{"accentHue":99,"backgroundHue":-5,"glass":"HIGH"}}""",
        )
        val s = store().apply { load() }.settings.value
        assertEquals(LockAfter.MIN_1, s.lockAfter)
        assertEquals(ThemeMode.LIGHT, s.theme)
        assertEquals(ThemeStyle.GLASS, s.style)
        assertEquals(PaletteId.NIGHT, s.palette)
        assertEquals(CustomPalette(99.mod(CUSTOM_HUES), (-5).mod(CUSTOM_HUES), GlassLevel.HIGH), s.custom)
        // A «Personalizado» that cannot be read at all goes, and only it.
        for (bad in listOf("""{"accentHue":1,"backgroundHue":2,"glass":"ULTRA"}""", """{"accentHue":"x"}""", "7")) {
            file().writeText("""{"lockAfter":"MIN_1","theme":"LIGHT","palette":"CUSTOM","highContrast":true,"custom":$bad}""")
            val t = store().apply { load() }.settings.value
            assertEquals(bad, LockAfter.MIN_1, t.lockAfter)
            assertEquals(bad, ThemeMode.LIGHT, t.theme)
            assertNull(bad, t.custom)
            assertTrue(bad, t.highContrast)
            // Review M-5: without its tones «Personalizado» is drawn as «Noche», so it is «Noche».
            assertEquals(bad, PaletteId.NIGHT, t.palette)
        }
    }

    /** Review M-5: «Personalizado» saved without its tones reads as «Noche»; with them it stays. */
    @Test fun aCustomWithoutTonesIsNight() = runTest {
        file().writeText("""{"palette":"CUSTOM"}""")
        assertEquals(PaletteId.NIGHT, store().apply { load() }.settings.value.palette)
        file().writeText("""{"palette":"CUSTOM","custom":{"accentHue":2,"backgroundHue":3,"glass":"LOW"}}""")
        assertEquals(PaletteId.CUSTOM, store().apply { load() }.settings.value.palette)
    }

    @Test fun theAppearanceIsSavedAndReadBack() = runTest {
        val custom = CustomPalette(accentHue = 3, backgroundHue = 8, glass = GlassLevel.HIGH)
        store().apply {
            load()
            update { it.copy(style = ThemeStyle.SOLID, palette = PaletteId.CUSTOM, custom = custom, reduceTransparency = true, highContrast = true) }
        }
        val s = store().apply { load() }.settings.value
        assertEquals(ThemeStyle.SOLID, s.style)
        assertEquals(PaletteId.CUSTOM, s.palette)
        assertEquals(custom, s.custom)
        assertTrue(s.reduceTransparency)
        assertTrue(s.highContrast)
    }

    /**
     * R-C8: la línea de cómo volver a «Sólido» es para quien actualiza (había un settings.json o una bóveda), también si
     * ya había visto el aviso del nombre nuevo; una instalación nueva no la ve. Se decide una vez.
     */
    @Test fun theAppearanceNoticeIsForThoseWhoUpdated() = runTest {
        file().writeText("""{"theme":"LIGHT","renameNoticePending":false}""")
        val updated = store().apply { load(); decideRenameNotice(vaultExists = false) }
        assertEquals(true, updated.settings.value.appearanceNoticePending)
        assertEquals(false, updated.settings.value.renameNoticePending)
        updated.update { it.copy(appearanceNoticePending = false) }
        val again = store().apply { load(); decideRenameNotice(vaultExists = true) }
        assertEquals(false, again.settings.value.appearanceNoticePending)

        file().delete()
        val fresh = store().apply { load(); decideRenameNotice(vaultExists = false) }
        assertEquals(false, fresh.settings.value.appearanceNoticePending)
        val withVault = SettingsStore(File(tmp.root, "other.json"), Dispatchers.Unconfined).apply { load(); decideRenameNotice(vaultExists = true) }
        assertEquals(true, withVault.settings.value.appearanceNoticePending)
    }

    /**
     * 2.0, tarea 10: el settings.json de un usuario de la 1.4.3, escrito por el `SettingsStore` de la etiqueta `v1.4.3`
     * (`a3ac942`) con datos de prueba (`/seguridad/settings-1.4.3.json`). Quien actualiza no tiene el modo sencillo y le
     * toca la guía corta de novedades, no la completa (dueño, 14-11-2026), con bóveda y sin ella, también al volver a
     * abrir la app hasta verla; vista o saltada, ya no le toca ninguna.
     */
    @Test fun oldSettingsKeepSimpleModeOff() = runTest {
        val real = javaClass.getResourceAsStream("/seguridad/settings-1.4.3.json")!!.use { it.readBytes() }.decodeToString()
        for (vaultExists in listOf(true, false)) {
            file().writeText(real)
            val s = store().apply { load() }
            assertNull("1.4.3 has no simple mode", s.settings.value.simpleMode)
            s.decideRenameNotice(vaultExists)
            for (v in listOf(s.settings.value, store().apply { load() }.settings.value)) {
                assertEquals(false, v.simpleMode)
                assertFalse(v.simpleOn)
                assertEquals("The short guide of what is new", GuideDue.WHATS_NEW, v.guideDue)
                assertEquals(GUIDE_BEFORE_2_0, v.guideSeen)
                assertEquals(LockAfter.MIN_1, v.lockAfter)
            }
            // Their first vault made later (after «He olvidado la contraseña maestra», say) changes nothing.
            s.firstVault(created = true)
            assertEquals(false, s.settings.value.simpleMode)
            // A later start decides nothing again.
            store().apply { load(); decideRenameNotice(vaultExists = false) }
            assertEquals(false, store().apply { load() }.settings.value.simpleMode)
            assertEquals(GuideDue.WHATS_NEW, store().apply { load() }.settings.value.guideDue)
            // Seen or skipped: nothing more, now and at the next start.
            s.update { it.withGuideSeen() }
            assertEquals(GuideDue.NONE, s.settings.value.guideDue)
            assertEquals(GuideDue.NONE, store().apply { load(); decideRenameNotice(vaultExists = true) }.settings.value.guideDue)
        }
    }

    /**
     * The guide seen is a number, so a later version that brings a guide of its own only raises [GUIDE_CURRENT]: whoever
     * saw an older one gets the short one of what is new, a new install the whole one, and whoever saw this one nothing.
     */
    @Test fun theGuideDueGoesByTheVersionSeen() {
        assertEquals(GuideDue.FULL, Settings().guideDue)
        assertEquals(GuideDue.FULL, Settings(guideSeen = GUIDE_NONE).guideDue)
        assertEquals(GuideDue.WHATS_NEW, Settings(guideSeen = GUIDE_BEFORE_2_0).guideDue)
        for (seen in GUIDE_BEFORE_2_0 until GUIDE_CURRENT) assertEquals(GuideDue.WHATS_NEW, Settings(guideSeen = seen).guideDue)
        assertEquals(GuideDue.NONE, Settings(guideSeen = GUIDE_CURRENT).guideDue)
        assertEquals("A file of a later version (downgraded app)", GuideDue.NONE, Settings(guideSeen = GUIDE_CURRENT + 5).guideDue)
        assertEquals(GUIDE_CURRENT, Settings().withGuideSeen().guideSeen)
        // Seeing it never lowers what a later version had recorded.
        assertEquals(GUIDE_CURRENT + 5, Settings(guideSeen = GUIDE_CURRENT + 5).withGuideSeen().guideSeen)
        assertTrue(GUIDE_NONE < GUIDE_BEFORE_2_0 && GUIDE_BEFORE_2_0 < GUIDE_CURRENT)
        // A negative number (a file edited by hand) is a new install's: the whole guide, never an error.
        assertEquals(GuideDue.FULL, Settings(guideSeen = -3).guideDue)
    }

    /**
     * 2.0, tarea 10: una instalación nueva no ha decidido el modo sencillo (se ve todo) ni ha visto la guía, también tras
     * reiniciar la app antes de crear la bóveda (el settings.json ya existe entonces); crear la primera lo enciende, y nada
     * después lo vuelve a cambiar. Si la primera bóveda viene de una copia, se queda apagado.
     */
    @Test fun newInstallTurnsSimpleModeOn() = runTest {
        file().delete()
        repeat(2) {
            val s = store().apply { load(); decideRenameNotice(vaultExists = false) }
            assertNull(s.settings.value.simpleMode)
            assertFalse(s.settings.value.simpleOn)
            assertEquals("The whole guide", GuideDue.FULL, s.settings.value.guideDue)
            s.update { it.copy(noticeAccepted = true) }
        }
        val s = store().apply { load(); decideRenameNotice(vaultExists = false) }
        s.firstVault(created = true)
        assertEquals(true, s.settings.value.simpleMode)
        assertEquals(true, store().apply { load() }.settings.value.simpleMode)
        s.update { it.copy(simpleMode = false) }
        s.firstVault(created = true)
        assertEquals("Only once", false, s.settings.value.simpleMode)
        assertEquals(GuideDue.FULL, store().apply { load(); decideRenameNotice(vaultExists = true) }.settings.value.guideDue)

        file().delete()
        val imported = store().apply { load(); decideRenameNotice(vaultExists = false) }
        imported.firstVault(created = false)
        assertEquals(false, imported.settings.value.simpleMode)
        imported.firstVault(created = true)
        assertEquals(false, imported.settings.value.simpleMode)
    }

    /** A save that fails leaves the choice for this run only: the vault is made all the same. */
    @Test fun firstVaultNeverThrows() = runTest {
        // A file where its folder should be: every write fails (also as root, where a read-only folder is still written).
        val notADir = File(tmp.root, "no-es-carpeta").apply { writeText("x") }
        val s = SettingsStore(File(notADir, "settings.json"), Dispatchers.Unconfined).apply { load() }
        s.firstVault(created = true)
        assertEquals(true, s.settings.value.simpleMode)
    }

    @Test fun clipboardHistoryNoticeIsNotAcceptedByDefault() {
        assertFalse(Settings().clipboardHistoryNoticeAccepted)
    }

    /** A settings.json as 1.2.2 wrote it (only the values that are not the default), before the A14 notice existed. */
    @Test fun a122FileWithoutTheNoticeFieldStillLoads() = runTest {
        file().writeText(
            """{"lockAfter":"MIN_1","clipboard":"S15","theme":"SYSTEM","noticeAccepted":true,""" +
                """"autoBackupUri":"content://com.android.externalstorage.documents/tree/primary%3ACopias",""" +
                """"lastAutoBackupAt":1761500000000,"autoBackupCopies":["doc:1","doc:2"],""" +
                """"autoBackupCopiesFolder":"content://com.android.externalstorage.documents/tree/primary%3ACopias"}""",
        )
        val s = store().apply { load() }.settings.value
        // Read, not fallen back to the defaults: the user's values are there.
        assertEquals(LockAfter.MIN_1, s.lockAfter)
        assertEquals(ClipboardClear.S15, s.clipboard)
        assertTrue(s.noticeAccepted)
        assertEquals(listOf("doc:1", "doc:2"), s.principal.autoBackupCopies)
        assertFalse(s.clipboardHistoryNoticeAccepted)
    }

    // M2, M4: a request saved by 1.2.x has neither targets nor what was told: it still reads, as a request without targets.
    @Test fun aPendingRequestFrom122ReadsWithoutTargetsOrTold() = runTest {
        file().writeText(
            """{"autoBackupUri":"content://x/tree/primary%3ACopias","autoBackupReplacePending":true,""" +
                """"autoBackupCopies":["doc:1"],"autoBackupCopiesFolder":"content://x/tree/primary%3ACopias"}""",
        )
        val s = store().apply { load() }.settings.value.principal
        assertTrue(s.autoBackupReplacePending)
        assertEquals(null, s.autoBackupReplaceTargets)
        assertEquals(emptyList<String>(), s.autoBackupReplaceTold)
        // And what 1.3.0 saves reads back.
        store().apply { load(); updatePrincipal { it.oneTarget(autoBackupReplaceTargets = listOf("doc:1"), autoBackupReplaceTold = listOf("x")) } }
        val back = store().apply { load() }.settings.value.principal
        assertEquals(listOf("doc:1"), back.autoBackupReplaceTargets)
        assertEquals(listOf("x"), back.autoBackupReplaceTold)
    }

    @Test fun clipboardHistoryNoticeAcceptancePersists() = runTest {
        store().apply { load(); update { it.copy(clipboardHistoryNoticeAccepted = true) } }
        assertTrue(store().apply { load() }.settings.value.clipboardHistoryNoticeAccepted)
    }

    @Test fun corruptFileFallsBackToDefaults() = runTest {
        file().writeText("{no")
        val s = store().apply { load() }
        assertEquals(Settings(), s.settings.value)
    }

    @Test fun highUnavailableOnSmallHeap() {
        assertFalse(KdfProfile.HIGH.availableOn(200 * 1024))
        assertTrue(KdfProfile.HIGH.availableOn(256 * 1024))
        assertTrue(KdfProfile.STANDARD.availableOn(1))
    }

    @Test fun updateRejectsUnavailableHigh() = runTest {
        val s = store(heapKiB = 200L * 1024).apply { load() }
        s.updatePrincipal { it.copy(kdf = KdfProfile.HIGH) }
        assertEquals(KdfProfile.STANDARD, s.settings.value.principal.kdf)
        assertTrue(file().readText().let { !it.contains("HIGH") })
    }

    /** A budget phone (192 MiB per app): it never offers «Alto», but must open what another phone made with it. */
    private val smallHeapKiB = 192L * 1024

    @Test fun highTextOpensOnAPhoneThatDoesNotOfferHigh() {
        assertFalse(KdfProfile.HIGH.availableOn(smallHeapKiB)) // Not offered for new data there…
        KdfProfile.HIGH.params.checkSane(smallHeapKiB) // …but accepted when reading.
        val token = encryptText("frase semilla", "pw".toCharArray(), kdf = KdfProfile.HIGH.params)
        assertEquals("frase semilla", decryptText(token, Secret.Password("pw".toCharArray()), heapKiB = smallHeapKiB))
    }

    @Test fun highVaultUnlocksOnAPhoneThatDoesNotOfferHigh() {
        val (file, dek) = VaultCrypto.create("m".toCharArray(), 0, KdfProfile.HIGH.params)
        val again = VaultCrypto.unlock(VaultCrypto.decodeFile(VaultCrypto.encodeFile(file)), "m".toCharArray(), heapKiB = smallHeapKiB)
        assertArrayEquals(dek, again)
    }

    @Test fun highIsRefusedWhenTheHeapPlainlyCannotHoldIt() {
        // 128 MiB per app (Android Go): Argon2 alone would take the whole heap.
        assertRefused { KdfProfile.HIGH.params.checkSane(128L * 1024) }
        val token = encryptText("x", "pw".toCharArray(), kdf = KdfProfile.HIGH.params)
        assertRefused { decryptText(token, Secret.Password("pw".toCharArray()), heapKiB = 128L * 1024) }
    }

    @Test fun moreThanTheMaximumIsRefusedWhateverTheHeap() {
        assertRefused { KdfParams(MAX_KDF_MEMORY_KIB + 1, 3, 1).checkSane(heapKiB = 8L * 1024 * 1024) }
        assertRefused { KdfParams(MAX_KDF_MEMORY_KIB, 11, 1).checkSane(heapKiB = 8L * 1024 * 1024) }
        assertRefused { KdfParams(MAX_KDF_MEMORY_KIB, 3, 5).checkSane(heapKiB = 8L * 1024 * 1024) }
    }

    @Test fun runningOutOfMemoryInArgon2IsAClearFailure() {
        try {
            outOfMemoryAsFailure { throw OutOfMemoryError("Failed to allocate") }
            fail()
        } catch (e: CryptoFailure) {
            assertEquals(KDF_OUT_OF_MEMORY, e.message)
        }
        assertEquals(7, outOfMemoryAsFailure { 7 })
    }

    private fun assertRefused(block: () -> Unit) {
        try {
            block()
            fail()
        } catch (e: CryptoFailure) {
            assertEquals("Parámetros de cifrado no válidos", e.message)
        }
    }

    @Test fun highProfileRoundTrip() {
        KdfProfile.HIGH.params.checkSane(256 * 1024)
        val token = encryptText("hola", "pw".toCharArray(), kdf = KdfProfile.HIGH.params)
        assertEquals("hola", decryptText(token, "pw".toCharArray()))
    }

    @Test fun changingLockAfterWhileUnlocked() = runTest {
        var clock = 1_000L
        val settings = store().apply { load(); update { it.copy(lockAfter = LockAfter.MIN_15) } }
        val vault = VaultStore(File(tmp.root, "vault.bin"), Dispatchers.Unconfined, Dispatchers.Unconfined, now = { clock }, kdf = { KdfParams(1024, 1, 1) },
            idleLockMs = { settings.settings.value.lockAfter.ms }, elapsed = { clock })
        vault.load(); vault.create("m".toCharArray())
        settings.update { it.copy(lockAfter = LockAfter.MIN_1) }
        clock += 59_000
        assertFalse(vault.lockIfIdle())
        clock += 2_000
        assertTrue(vault.lockIfIdle())
    }

    @Test fun autoBackupDefaultsAndOldFiles() = runTest {
        // A 1.0.x settings file, without the new fields.
        file().writeText("""{"lockAfter":"MIN_1","theme":"LIGHT","noticeAccepted":true}""")
        val s = store().apply { load() }
        assertEquals(LockAfter.MIN_1, s.settings.value.lockAfter)
        assertEquals(null, s.settings.value.principal.autoBackupUri)
        assertEquals(5, s.settings.value.principal.autoBackupKeep)
        assertEquals(null, s.settings.value.principal.lastAutoBackupError)
        assertEquals(null, s.settings.value.principal.lastAutoBackupAt)
    }

    @Test fun autoBackupPersists() = runTest {
        store().apply {
            load()
            updatePrincipal { it.oneTarget(autoBackupUri = "content://x/tree/primary%3ACopias", autoBackupKeep = 10, lastAutoBackupError = "Falló", lastAutoBackupAt = 5L) }
        }
        val again = store().apply { load() }.settings.value.principal
        assertEquals("content://x/tree/primary%3ACopias", again.autoBackupUri)
        assertEquals(10, again.autoBackupKeep)
        assertEquals("Falló", again.lastAutoBackupError)
        assertEquals(5L, again.lastAutoBackupAt)
    }

    @Test fun autoBackupKeepOnlyThreeFiveOrTen() = runTest {
        val s = store().apply { load() }
        // 2.0, task 8: each target's own number of copies.
        s.updatePrincipal { it.withTargetAdded("content://x/tree/primary%3ACopias", BackupSchedule.ON_CHANGE) }
        for (k in listOf(3, 5, 10)) {
            s.updatePrincipal { it.oneTarget(autoBackupKeep = k) }
            assertEquals(k, s.settings.value.principal.autoBackupKeep)
        }
        for (k in listOf(0, -1, 1, 4, 11, 1000, Int.MAX_VALUE)) {
            s.updatePrincipal { it.oneTarget(autoBackupKeep = k) }
            assertEquals(5, s.settings.value.principal.autoBackupKeep)
        }
        file().writeText("""{"autoBackupKeep":7}""")
        assertEquals(5, store().apply { load() }.settings.value.principal.autoBackupKeep)
    }

    private val folder = "content://x/tree/primary%3ACopias"

    @Test fun autoBackupResultOnlyForTheFolderItWentTo() {
        val on = oldVaultSettings(autoBackupUri = folder, lastAutoBackupAt = 1L, lastAutoBackupError = "antes")
        // Written: date and no error.
        assertEquals(on.oneTarget(lastAutoBackupAt = 9L, lastAutoBackupError = null), on.withAutoBackupResult(AutoBackupResult(folder, 9L, null)))
        // Written, but old copies not deleted: the date too, and the error.
        assertEquals(
            on.oneTarget(lastAutoBackupAt = 9L, lastAutoBackupError = AUTO_BACKUP_OLD_NOT_DELETED),
            on.withAutoBackupResult(AutoBackupResult(folder, 9L, AUTO_BACKUP_OLD_NOT_DELETED)),
        )
        // Not written: only the error; the last copy is still the one before.
        assertEquals(on.oneTarget(lastAutoBackupError = AUTO_BACKUP_FAILED), on.withAutoBackupResult(AutoBackupResult(folder, null, AUTO_BACKUP_FAILED)))
        // Another folder chosen, or turned off, meanwhile: that copy says nothing about the current state.
        val other = on.oneTarget(autoBackupUri = "content://y/tree/otra")
        assertEquals(other, other.withAutoBackupResult(AutoBackupResult(folder, 9L, null)))
        val off = on.oneTarget(autoBackupUri = null)
        assertEquals(off, off.withAutoBackupResult(AutoBackupResult(folder, 9L, null)))
    }

    @Test fun autoBackupCopiesAreKeptOnlyForTheFolderTheyAreIn() {
        val on = oldVaultSettings(autoBackupUri = folder, autoBackupCopies = listOf("a"), autoBackupCopiesFolder = folder)
        val written = on.withAutoBackupResult(AutoBackupResult(folder, 9L, null, listOf("a", "b")))
        assertEquals(listOf("a", "b"), written.currentAutoBackupCopies)
        // Nothing written: the list stays.
        assertEquals(listOf("a"), on.withAutoBackupResult(AutoBackupResult(folder, null, AUTO_BACKUP_FAILED)).currentAutoBackupCopies)
        // Another folder chosen: none of these is in it. Back to the first: they are again, and go on rotating.
        val other = written.oneTarget(autoBackupUri = "content://y/tree/otra")
        assertEquals(emptyList<String>(), other.currentAutoBackupCopies)
        assertEquals(other, other.withAutoBackupResult(AutoBackupResult(folder, 9L, null, listOf("x"))))
        assertEquals(listOf("a", "b"), other.oneTarget(autoBackupUri = folder).currentAutoBackupCopies)
        // Turned off: none in use, but kept for the same folder chosen again.
        assertEquals(emptyList<String>(), written.oneTarget(autoBackupUri = null).currentAutoBackupCopies)
        // An imported vault: forgotten for good.
        assertEquals(emptyList<String>(), written.forgettingAutoBackupCopies().currentAutoBackupCopies)
    }

    @Test fun theReplacementRequestIsPersistedAndOlderFilesStillLoad() = runTest {
        // A settings.json from before the field existed.
        file().writeText("""{"autoBackupKeep":10}""")
        assertFalse(store().apply { load() }.settings.value.principal.autoBackupReplacePending)
        val s = store().apply { load() }
        s.updatePrincipal { it.oneTarget(autoBackupUri = folder, autoBackupReplacePending = true) }
        assertTrue(store().apply { load() }.settings.value.principal.autoBackupReplacePending)
    }

    @Test fun theReplacementRequestIsSetByTheWaitingResultAndClearedByTheCopyThatReplaced() {
        val on = oldVaultSettings(autoBackupUri = folder, lastAutoBackupError = "antes")
        // Asked while locked: only the flag; the error shown in Ajustes and the last copy do not change.
        val asked = on.withAutoBackupResult(AutoBackupResult(folder, null, AUTO_BACKUP_REPLACE_WAITS, replaceRequested = true))
        assertEquals(on.oneTarget(autoBackupReplacePending = true), asked)
        // A normal copy meanwhile does not clear it.
        assertTrue(asked.withAutoBackupResult(AutoBackupResult(folder, 9L, null, listOf("a"))).autoBackupReplacePending)
        // A failed replacement keeps it, with the error.
        val failed = asked.withAutoBackupResult(AutoBackupResult(folder, null, AUTO_BACKUP_FAILED))
        assertTrue(failed.autoBackupReplacePending)
        assertEquals(AUTO_BACKUP_FAILED, failed.lastAutoBackupError)
        // The copy that replaced the old ones (all of them deleted) clears it.
        val done = asked.withAutoBackupResult(AutoBackupResult(folder, 9L, null, listOf("b"), replaced = true))
        assertFalse(done.autoBackupReplacePending)
        assertEquals(listOf("b"), done.autoBackupCopies)
        // CR-1, m2: AutoBackup says «replaced» only when every target and leftover temporary copy went (M3, M4), and then
        // with no error, even if a copy beyond «Copias que guardar» could not be deleted (that one is just tried again).
        // Not replaced: kept, with the targets left and the failure told (M2, M4).
        val left = asked.withAutoBackupResult(
            AutoBackupResult(folder, 9L, AUTO_BACKUP_OLD_NOT_DELETED, listOf("a", "b"), replaceTargets = listOf("a"), toldKey = AUTO_BACKUP_OLD_NOT_DELETED),
        )
        assertTrue(left.autoBackupReplacePending)
        assertEquals(listOf("a"), left.autoBackupReplaceTargets)
        assertEquals(listOf(AUTO_BACKUP_OLD_NOT_DELETED), left.autoBackupReplaceTold)
        // A failure with nothing written is told and kept too; asked again with the vault open, what was told starts again.
        val offline = left.withAutoBackupResult(AutoBackupResult(folder, null, AUTO_BACKUP_FAILED, toldKey = AUTO_BACKUP_FAILED))
        assertEquals(listOf(AUTO_BACKUP_OLD_NOT_DELETED, AUTO_BACKUP_FAILED), offline.autoBackupReplaceTold)
        assertEquals(listOf("a"), offline.autoBackupReplaceTargets)
        assertEquals(emptyList<String>(), offline.withAutoBackupResult(AutoBackupResult(folder, null, AUTO_BACKUP_FAILED, newRequest = true)).autoBackupReplaceTold)
        // Every way the request goes takes its targets and what it told with it.
        for (gone in listOf(left.forgettingAutoBackupCopies(), left.withAutoBackupTurnedOff(), left.withFolderChosen("content://y/tree/otra"))) {
            assertFalse(gone.autoBackupReplacePending)
            assertNull(gone.autoBackupReplaceTargets)
            assertEquals(emptyList<String>(), gone.autoBackupReplaceTold)
        }
        // A new request while locked: its own targets, nothing told yet.
        val again = left.withAutoBackupResult(AutoBackupResult(folder, null, AUTO_BACKUP_REPLACE_WAITS, replaceRequested = true, replaceTargets = listOf("a", "b")))
        assertEquals(listOf("a", "b"), again.autoBackupReplaceTargets)
        assertEquals(emptyList<String>(), again.autoBackupReplaceTold)
        // For another folder, or with the copies off, it says nothing.
        assertEquals(on.oneTarget(autoBackupUri = null), on.oneTarget(autoBackupUri = null).withAutoBackupResult(AutoBackupResult(folder, null, AUTO_BACKUP_REPLACE_WAITS, replaceRequested = true)))
        // Forgotten with the copies (new or imported vault) or when they are turned off.
        assertFalse(asked.forgettingAutoBackupCopies().autoBackupReplacePending)
    }

    @Test fun theSameFolderChosenAgainKeepsTheRequestAndAnotherOrOffDropsIt() {
        val asked = oldVaultSettings(autoBackupUri = folder, autoBackupReplacePending = true, autoBackupOffForNewVault = true)
        val same = asked.withFolderChosen(folder)
        assertTrue(same.autoBackupReplacePending)
        assertFalse(same.autoBackupOffForNewVault)
        assertEquals(null, same.lastAutoBackupAt)
        assertFalse(asked.withFolderChosen("content://y/tree/otra").autoBackupReplacePending)
        assertEquals("content://y/tree/otra", asked.withFolderChosen("content://y/tree/otra").autoBackupUri)
        // From none (copies off): there is nothing to replace.
        assertFalse(oldVaultSettings(autoBackupReplacePending = true).withFolderChosen(folder).autoBackupReplacePending)
        val off = asked.withAutoBackupTurnedOff()
        assertEquals(null, off.autoBackupUri)
        assertFalse(off.autoBackupReplacePending)
    }

    @Test fun newVaultClearsTheReplacementRequest() = runTest {
        val s = store().apply { load() }
        s.updatePrincipal { it.oneTarget(autoBackupUri = folder, autoBackupReplacePending = true) }
        s.turnOffAutoBackupForNewVault(PRIMARY_VAULT_ID)
        assertFalse(s.settings.value.principal.autoBackupReplacePending)
    }

    @Test fun newVaultTurnsAutoBackupOff() = runTest {
        val s = store().apply { load() }
        s.updatePrincipal { it.oneTarget(autoBackupUri = folder, autoBackupKeep = 10, lastAutoBackupAt = 5L, lastAutoBackupError = "Falló", autoBackupCopies = listOf("a"), autoBackupCopiesFolder = folder) }
        assertEquals(listOf(folder), s.turnOffAutoBackupForNewVault(PRIMARY_VAULT_ID))
        val off = s.settings.value.principal
        assertEquals(null, off.autoBackupUri)
        // The old vault's copies are never rotated away, even if the same folder is chosen again.
        assertEquals(emptyList<String>(), off.autoBackupCopies)
        assertEquals(null, off.lastAutoBackupAt)
        assertEquals(null, off.lastAutoBackupError)
        assertTrue(off.autoBackupOffForNewVault)
        // 2.0, task 8: with no target there is no «Conservar las N últimas» to keep; a folder chosen again starts at 5.
        assertEquals(emptyList<BackupTargetSettings>(), off.targets)
        assertEquals(emptyList<BackupTargetSettings>(), off.pastTargets)
        // Saved: Ajustes still says why after a restart.
        assertTrue(store().apply { load() }.settings.value.principal.autoBackupOffForNewVault)
        // Already off: nothing to do, nothing to give back.
        assertEquals(emptyList<String>(), s.turnOffAutoBackupForNewVault(PRIMARY_VAULT_ID))
        assertEquals(off, s.settings.value.principal)
    }

    // I1 (CR re-review): with the copies off, a new vault still forgets the old vault's list (and any request), so choosing
    // the same folder again never rotates the previous vault's copies away; nothing is given back (nothing was held).
    @Test fun newVaultWithTheCopiesOffStillForgetsTheOldList() = runTest {
        val s = store().apply { load() }
        s.updatePrincipal {
            it.oneTarget(
                autoBackupUri = folder, autoBackupCopies = listOf("v1-a", "v1-b"), autoBackupCopiesFolder = folder,
                autoBackupReplacePending = true, autoBackupReplaceTargets = listOf("v1-a"), autoBackupReplaceTold = listOf("x"),
            )
        }
        s.turnOffAutoBackup(PRIMARY_VAULT_ID) // «Desactivar»: the list stays on purpose (the same folder chosen again goes on rotating it).
        s.updatePrincipal { it.oneTarget(autoBackupCopies = listOf("v1-a", "v1-b"), autoBackupCopiesFolder = folder) }
        assertEquals(emptyList<String>(), s.turnOffAutoBackupForNewVault(PRIMARY_VAULT_ID)) // The new vault.
        val after = s.settings.value.principal
        assertEquals(emptyList<String>(), after.autoBackupCopies)
        assertEquals(null, after.autoBackupCopiesFolder)
        assertFalse(after.autoBackupReplacePending)
        assertNull(after.autoBackupReplaceTargets)
        assertEquals(emptyList<String>(), after.autoBackupReplaceTold)
        assertEquals(emptyList<String>(), store().apply { load() }.settings.value.principal.autoBackupCopies) // Saved.
        // The same folder chosen again: no copy of the old vault is listed as this one's.
        assertEquals(emptyList<String>(), after.withFolderChosen(folder).currentAutoBackupCopies)
    }

    @Test fun newVaultWithAutoBackupOffChangesNothing() = runTest {
        val s = store().apply { load() }
        assertEquals(emptyList<String>(), s.turnOffAutoBackupForNewVault(PRIMARY_VAULT_ID))
        assertFalse(s.settings.value.principal.autoBackupOffForNewVault)
        assertFalse(file().exists())
    }

    @Test fun turningTheCopiesOffSaysIfItDroppedAWaitingReplacement() = runTest {
        val s = store().apply { load() }
        s.updatePrincipal { it.oneTarget(autoBackupUri = "content://carpeta", autoBackupReplacePending = true) }
        assertTrue("Decided from the very write that turned them off (T11-c)", s.turnOffAutoBackup(PRIMARY_VAULT_ID))
        assertEquals(null, s.settings.value.principal.autoBackupUri)
        assertFalse(s.settings.value.principal.autoBackupReplacePending)
        s.updatePrincipal { it.oneTarget(autoBackupUri = "content://carpeta") }
        assertFalse(s.turnOffAutoBackup(PRIMARY_VAULT_ID))
        assertFalse("Already off: nothing dropped", s.turnOffAutoBackup(PRIMARY_VAULT_ID))
    }
}
