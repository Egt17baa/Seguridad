package app.seguridad.settings

import app.seguridad.crypto.KdfParams
import app.seguridad.crypto.decryptText
import app.seguridad.crypto.encryptText
import app.seguridad.vault.VaultStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
}
