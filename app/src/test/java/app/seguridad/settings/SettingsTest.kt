package app.seguridad.settings

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
}
