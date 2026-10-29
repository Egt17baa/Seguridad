package app.seguridad.settings

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
        assertEquals(KdfProfile.STANDARD, s.settings.value.kdf)
        assertEquals(ThemeMode.DARK, s.settings.value.theme)
        assertFalse(s.settings.value.noticeAccepted)
    }

    @Test fun persistsAndReloads() = runTest {
        store().apply {
            load()
            update { it.copy(lockAfter = LockAfter.MIN_1, clipboard = ClipboardClear.S15, theme = ThemeMode.SYSTEM, noticeAccepted = true, kdf = KdfProfile.HIGH) }
        }
        val again = store().apply { load() }
        assertEquals(LockAfter.MIN_1, again.settings.value.lockAfter)
        assertEquals(ClipboardClear.S15, again.settings.value.clipboard)
        assertEquals(ThemeMode.SYSTEM, again.settings.value.theme)
        assertEquals(KdfProfile.HIGH, again.settings.value.kdf)
        assertTrue(again.settings.value.noticeAccepted)
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
        assertEquals(listOf("doc:1", "doc:2"), s.autoBackupCopies)
        assertFalse(s.clipboardHistoryNoticeAccepted)
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
        s.update { it.copy(kdf = KdfProfile.HIGH) }
        assertEquals(KdfProfile.STANDARD, s.settings.value.kdf)
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
            idleLockMs = { settings.settings.value.lockAfter.ms })
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
        assertEquals(null, s.settings.value.autoBackupUri)
        assertEquals(5, s.settings.value.autoBackupKeep)
        assertEquals(null, s.settings.value.lastAutoBackupError)
        assertEquals(null, s.settings.value.lastAutoBackupAt)
    }

    @Test fun autoBackupPersists() = runTest {
        store().apply {
            load()
            update { it.copy(autoBackupUri = "content://x/tree/primary%3ACopias", autoBackupKeep = 10, lastAutoBackupError = "Falló", lastAutoBackupAt = 5L) }
        }
        val again = store().apply { load() }.settings.value
        assertEquals("content://x/tree/primary%3ACopias", again.autoBackupUri)
        assertEquals(10, again.autoBackupKeep)
        assertEquals("Falló", again.lastAutoBackupError)
        assertEquals(5L, again.lastAutoBackupAt)
    }

    @Test fun autoBackupKeepOnlyThreeFiveOrTen() = runTest {
        val s = store().apply { load() }
        for (k in listOf(3, 5, 10)) {
            s.update { it.copy(autoBackupKeep = k) }
            assertEquals(k, s.settings.value.autoBackupKeep)
        }
        for (k in listOf(0, -1, 1, 4, 11, 1000, Int.MAX_VALUE)) {
            s.update { it.copy(autoBackupKeep = k) }
            assertEquals(5, s.settings.value.autoBackupKeep)
        }
        file().writeText("""{"autoBackupKeep":7}""")
        assertEquals(5, store().apply { load() }.settings.value.autoBackupKeep)
    }

    private val folder = "content://x/tree/primary%3ACopias"

    @Test fun autoBackupResultOnlyForTheFolderItWentTo() {
        val on = Settings(autoBackupUri = folder, lastAutoBackupAt = 1L, lastAutoBackupError = "antes")
        // Written: date and no error.
        assertEquals(on.copy(lastAutoBackupAt = 9L, lastAutoBackupError = null), on.withAutoBackupResult(AutoBackupResult(folder, 9L, null)))
        // Written, but old copies not deleted: the date too, and the error.
        assertEquals(
            on.copy(lastAutoBackupAt = 9L, lastAutoBackupError = AUTO_BACKUP_OLD_NOT_DELETED),
            on.withAutoBackupResult(AutoBackupResult(folder, 9L, AUTO_BACKUP_OLD_NOT_DELETED)),
        )
        // Not written: only the error; the last copy is still the one before.
        assertEquals(on.copy(lastAutoBackupError = AUTO_BACKUP_FAILED), on.withAutoBackupResult(AutoBackupResult(folder, null, AUTO_BACKUP_FAILED)))
        // Another folder chosen, or turned off, meanwhile: that copy says nothing about the current state.
        val other = on.copy(autoBackupUri = "content://y/tree/otra")
        assertEquals(other, other.withAutoBackupResult(AutoBackupResult(folder, 9L, null)))
        val off = on.copy(autoBackupUri = null)
        assertEquals(off, off.withAutoBackupResult(AutoBackupResult(folder, 9L, null)))
    }

    @Test fun autoBackupCopiesAreKeptOnlyForTheFolderTheyAreIn() {
        val on = Settings(autoBackupUri = folder, autoBackupCopies = listOf("a"), autoBackupCopiesFolder = folder)
        val written = on.withAutoBackupResult(AutoBackupResult(folder, 9L, null, listOf("a", "b")))
        assertEquals(listOf("a", "b"), written.currentAutoBackupCopies)
        // Nothing written: the list stays.
        assertEquals(listOf("a"), on.withAutoBackupResult(AutoBackupResult(folder, null, AUTO_BACKUP_FAILED)).currentAutoBackupCopies)
        // Another folder chosen: none of these is in it. Back to the first: they are again, and go on rotating.
        val other = written.copy(autoBackupUri = "content://y/tree/otra")
        assertEquals(emptyList<String>(), other.currentAutoBackupCopies)
        assertEquals(other, other.withAutoBackupResult(AutoBackupResult(folder, 9L, null, listOf("x"))))
        assertEquals(listOf("a", "b"), other.copy(autoBackupUri = folder).currentAutoBackupCopies)
        // Turned off: none in use, but kept for the same folder chosen again.
        assertEquals(emptyList<String>(), written.copy(autoBackupUri = null).currentAutoBackupCopies)
        // An imported vault: forgotten for good.
        assertEquals(emptyList<String>(), written.forgettingAutoBackupCopies().currentAutoBackupCopies)
    }

    @Test fun theReplacementRequestIsPersistedAndOlderFilesStillLoad() = runTest {
        // A settings.json from before the field existed.
        file().writeText("""{"autoBackupKeep":10}""")
        assertFalse(store().apply { load() }.settings.value.autoBackupReplacePending)
        val s = store().apply { load() }
        s.update { it.copy(autoBackupReplacePending = true) }
        assertTrue(store().apply { load() }.settings.value.autoBackupReplacePending)
    }

    @Test fun theReplacementRequestIsSetByTheWaitingResultAndClearedByTheCopyThatReplaced() {
        val on = Settings(autoBackupUri = folder, lastAutoBackupError = "antes")
        // Asked while locked: only the flag; the error shown in Ajustes and the last copy do not change.
        val asked = on.withAutoBackupResult(AutoBackupResult(folder, null, AUTO_BACKUP_REPLACE_WAITS, replaceRequested = true))
        assertEquals(on.copy(autoBackupReplacePending = true), asked)
        // A normal copy meanwhile does not clear it.
        assertTrue(asked.withAutoBackupResult(AutoBackupResult(folder, 9L, null, listOf("a"))).autoBackupReplacePending)
        // A failed replacement keeps it, with the error.
        val failed = asked.withAutoBackupResult(AutoBackupResult(folder, null, AUTO_BACKUP_FAILED))
        assertTrue(failed.autoBackupReplacePending)
        assertEquals(AUTO_BACKUP_FAILED, failed.lastAutoBackupError)
        // The copy that replaced the old ones (even if some could not be deleted) clears it.
        val done = asked.withAutoBackupResult(AutoBackupResult(folder, 9L, null, listOf("b"), replaced = true))
        assertFalse(done.autoBackupReplacePending)
        assertEquals(listOf("b"), done.autoBackupCopies)
        assertFalse(asked.withAutoBackupResult(AutoBackupResult(folder, 9L, AUTO_BACKUP_OLD_NOT_DELETED, listOf("a", "b"), replaced = true)).autoBackupReplacePending)
        // For another folder, or with the copies off, it says nothing.
        assertEquals(on.copy(autoBackupUri = null), on.copy(autoBackupUri = null).withAutoBackupResult(AutoBackupResult(folder, null, AUTO_BACKUP_REPLACE_WAITS, replaceRequested = true)))
        // Forgotten with the copies (new or imported vault) or when they are turned off.
        assertFalse(asked.forgettingAutoBackupCopies().autoBackupReplacePending)
    }

    @Test fun theSameFolderChosenAgainKeepsTheRequestAndAnotherOrOffDropsIt() {
        val asked = Settings(autoBackupUri = folder, autoBackupReplacePending = true, autoBackupOffForNewVault = true)
        val same = asked.withFolderChosen(folder)
        assertTrue(same.autoBackupReplacePending)
        assertFalse(same.autoBackupOffForNewVault)
        assertEquals(null, same.lastAutoBackupAt)
        assertFalse(asked.withFolderChosen("content://y/tree/otra").autoBackupReplacePending)
        assertEquals("content://y/tree/otra", asked.withFolderChosen("content://y/tree/otra").autoBackupUri)
        // From none (copies off): there is nothing to replace.
        assertFalse(Settings(autoBackupReplacePending = true).withFolderChosen(folder).autoBackupReplacePending)
        val off = asked.withAutoBackupTurnedOff()
        assertEquals(null, off.autoBackupUri)
        assertFalse(off.autoBackupReplacePending)
    }

    @Test fun newVaultClearsTheReplacementRequest() = runTest {
        val s = store().apply { load() }
        s.update { it.copy(autoBackupUri = folder, autoBackupReplacePending = true) }
        s.turnOffAutoBackupForNewVault()
        assertFalse(s.settings.value.autoBackupReplacePending)
    }

    @Test fun newVaultTurnsAutoBackupOff() = runTest {
        val s = store().apply { load() }
        s.update { it.copy(autoBackupUri = folder, autoBackupKeep = 10, lastAutoBackupAt = 5L, lastAutoBackupError = "Falló", autoBackupCopies = listOf("a"), autoBackupCopiesFolder = folder) }
        assertEquals(folder, s.turnOffAutoBackupForNewVault())
        val off = s.settings.value
        assertEquals(null, off.autoBackupUri)
        // The old vault's copies are never rotated away, even if the same folder is chosen again.
        assertEquals(emptyList<String>(), off.autoBackupCopies)
        assertEquals(null, off.lastAutoBackupAt)
        assertEquals(null, off.lastAutoBackupError)
        assertTrue(off.autoBackupOffForNewVault)
        assertEquals(10, off.autoBackupKeep)
        // Saved: Ajustes still says why after a restart.
        assertTrue(store().apply { load() }.settings.value.autoBackupOffForNewVault)
        // Already off: nothing to do, nothing to give back.
        assertEquals(null, s.turnOffAutoBackupForNewVault())
        assertEquals(off, s.settings.value)
    }

    @Test fun newVaultWithAutoBackupOffChangesNothing() = runTest {
        val s = store().apply { load() }
        assertEquals(null, s.turnOffAutoBackupForNewVault())
        assertFalse(s.settings.value.autoBackupOffForNewVault)
        assertFalse(file().exists())
    }

    @Test fun turningTheCopiesOffSaysIfItDroppedAWaitingReplacement() = runTest {
        val s = store().apply { load() }
        s.update { it.copy(autoBackupUri = "content://carpeta", autoBackupReplacePending = true) }
        assertTrue("Decided from the very write that turned them off (T11-c)", s.turnOffAutoBackup())
        assertEquals(null, s.settings.value.autoBackupUri)
        assertFalse(s.settings.value.autoBackupReplacePending)
        s.update { it.copy(autoBackupUri = "content://carpeta") }
        assertFalse(s.turnOffAutoBackup())
        assertFalse("Already off: nothing dropped", s.turnOffAutoBackup())
    }
}
