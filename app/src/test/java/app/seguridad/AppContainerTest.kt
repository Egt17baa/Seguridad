package app.seguridad

import app.seguridad.settings.CLOCK_WRITE_STEP_MS
import app.seguridad.vault.BiometricRefusal
import app.seguridad.vault.VaultState
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

class AppContainerTest {
    @get:Rule val tmp = TemporaryFolder()

    private val cache by lazy { tmp.newFolder("cache") }

    private val folder = "content://com.android.externalstorage.documents/tree/primary%3ACopias"

    /**
     * vault.bin damaged and set aside, a new vault created: the automatic copies are turned off, so the rotation never
     * deletes the old vault's copies (the only good ones), and the folder's permission is given back.
     */
    @Test fun newVaultTurnsAutoBackupOffAndGivesTheFolderBack() = runTest {
        File(tmp.root, "vault").mkdirs()
        File(File(tmp.root, "vault"), "vault.bin").writeText("dañado")
        val released = mutableListOf<String>()
        val c = AppContainer(tmp.root, cache, appVersion = "test", releaseFolder = { released += it }, appScope = backgroundScope)
        c.settings.load()
        c.settings.update { it.copy(autoBackupUri = folder, lastAutoBackupAt = 5L) }
        c.vault.load()
        assertEquals(VaultState.NoVault, c.vault.state.value)
        c.vault.create("maestra-larga".toCharArray())
        assertTrue(c.vault.state.value is VaultState.Unlocked)
        assertNull(c.settings.settings.value.autoBackupUri)
        assertNull(c.settings.settings.value.lastAutoBackupAt)
        assertTrue(c.settings.settings.value.autoBackupOffForNewVault)
        assertEquals(listOf(folder), released)
    }

    @Test fun `COV2-3 another app taking the focus in multi-window, with Al salir`() = runTest {
        var elapsed = 0L
        val c = AppContainer(tmp.root, cache, appVersion = "test", appScope = backgroundScope, elapsedRealtime = { elapsed })
        c.settings.load()
        c.settings.update { it.copy(lockAfter = app.seguridad.settings.LockAfter.ON_EXIT) }
        c.vault.load()
        c.vault.create("maestra-larga".toCharArray())
        elapsed += 10 * 60_000L // In use, but no vault screen touched.
        c.leftForAnotherApp()
        assertTrue(c.awayInOtherApp.value)
        elapsed += app.seguridad.vault.AWAY_BACKSTOP_MS - 1
        c.backFromAnotherApp()
        assertFalse(c.awayInOtherApp.value)
        assertTrue("Back within 2 minutes: still open", c.vault.state.value is VaultState.Unlocked)
        c.leftForAnotherApp()
        // N3: the system takes the focus away again during the same trip (a second loss, no return in between): the
        // backstop still counts from the first one.
        elapsed += app.seguridad.vault.AWAY_BACKSTOP_MS - 1
        c.leftForAnotherApp()
        elapsed += 1
        assertTrue(c.vault.lockIfIdle())
        assertEquals(VaultState.Locked, c.vault.state.value)
        c.backFromAnotherApp()
        assertFalse(c.awayInOtherApp.value)
    }

    @Test fun newVaultWithAutoBackupOffReleasesNothing() = runTest {
        val released = mutableListOf<String>()
        val c = AppContainer(tmp.root, cache, appVersion = "test", releaseFolder = { released += it }, appScope = backgroundScope)
        c.settings.load()
        c.vault.load()
        c.vault.create("maestra-larga".toCharArray())
        assertFalse(c.settings.settings.value.autoBackupOffForNewVault)
        assertTrue(released.isEmpty())
    }

    @Test fun importingAVaultForgetsTheAutomaticCopiesMadeOfTheOldOne() = runTest {
        val c = AppContainer(tmp.root, cache, appVersion = "test", appScope = backgroundScope)
        c.settings.load()
        c.vault.load()
        c.vault.create("maestra-larga".toCharArray())
        c.settings.update { it.copy(autoBackupUri = folder, autoBackupCopies = listOf("doc-1"), autoBackupCopiesFolder = folder) }
        val other = AppContainer(File(tmp.root, "otro").apply { mkdirs() }, cache, appVersion = "test", appScope = backgroundScope)
        other.settings.load()
        other.vault.load()
        other.vault.create("otra-maestra-larga".toCharArray())
        c.vault.importReplace(other.vault.exportText()!!, "otra-maestra-larga".toCharArray())
        // Copies stay on, to the same folder, but the old vault's are never rotated away.
        assertEquals(folder, c.settings.settings.value.autoBackupUri)
        assertTrue(c.settings.settings.value.autoBackupCopies.isEmpty())
        // A wrong password changes nothing, the list included.
        c.settings.update { it.copy(autoBackupCopies = listOf("doc-2"), autoBackupCopiesFolder = folder) }
        try {
            c.vault.importReplace(other.vault.exportText()!!, "mala".toCharArray())
        } catch (e: app.seguridad.crypto.CryptoFailure) {
            // expected
        }
        assertEquals(listOf("doc-2"), c.settings.settings.value.autoBackupCopies)
    }

    // ---- Audit 1.3.0, F2: BOV-1 ----

    /** As a full disk: the settings file cannot be written (its temporary file is a folder), so every save throws. */
    private fun fillDisk() = check(File(tmp.root, "settings.json.tmp").mkdir())

    private fun freeDisk() = check(File(tmp.root, "settings.json.tmp").delete())

    @Test fun `BOV-1 a clock mark that cannot be saved never closes the app and still guards the fingerprint`() = runTest {
        val errors = CopyOnWriteArrayList<Throwable>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined + CoroutineExceptionHandler { _, e -> errors += e })
        var elapsed = 0L
        val c = AppContainer(tmp.root, cache, appVersion = "test", appScope = scope, elapsedRealtime = { elapsed }, settingsIo = Dispatchers.Unconfined)
        c.settings.load()
        c.vault.load()
        c.vault.create("maestra-larga".toCharArray())
        val saved = c.settings.settings.value.clockHighWaterMs
        fillDisk()
        // The highest time seen is an hour ahead of the phone's clock now (the clock went back an hour).
        val ahead = System.currentTimeMillis() + 60 * 60_000L
        c.rememberClock(ahead)
        assertTrue(errors.isEmpty())
        assertEquals(saved, c.settings.settings.value.clockHighWaterMs) // Not saved...
        assertEquals(ahead, c.clockMark) // ...but kept in memory,
        assertEquals(BiometricRefusal.CLOCK, c.vault.biometricRefusal) // so the fingerprint still asks for the master.
        // The master password with the disk still full: it opens, and its reset of the mark does not close the app.
        c.vault.lock()
        c.vault.unlock("maestra-larga".toCharArray())
        assertTrue(c.vault.state.value is VaultState.Unlocked)
        assertTrue(errors.isEmpty())
        assertNull(c.vault.biometricRefusal) // The reset holds in memory too.
        assertTrue(c.clockMark < ahead)
        // Space again: the next time seen, once the back-off has passed, saves the mark.
        freeDisk()
        c.rememberClock(System.currentTimeMillis())
        assertEquals(saved, c.settings.settings.value.clockHighWaterMs) // Not yet: a failed write waits a little.
        elapsed += CLOCK_WRITE_STEP_MS
        c.rememberClock(System.currentTimeMillis())
        assertEquals(c.clockMark, c.settings.settings.value.clockHighWaterMs)
        assertTrue(c.settings.settings.value.clockHighWaterMs < ahead)
        assertTrue(errors.isEmpty())
    }

    @Test fun `BOV-1 a mark that rises less than the step is kept in memory and not written`() = runTest {
        val c = AppContainer(tmp.root, cache, appVersion = "test", appScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined), settingsIo = Dispatchers.Unconfined)
        c.settings.load()
        c.settings.update { it.copy(clockHighWaterMs = 1_000_000L) }
        c.rememberClock(1_000_000L + CLOCK_WRITE_STEP_MS - 1)
        assertEquals(1_000_000L, c.settings.settings.value.clockHighWaterMs)
        assertEquals(1_000_000L + CLOCK_WRITE_STEP_MS - 1, c.clockMark)
        c.rememberClock(1_000_000L + CLOCK_WRITE_STEP_MS)
        assertEquals(1_000_000L + CLOCK_WRITE_STEP_MS, c.settings.settings.value.clockHighWaterMs)
        // A lower time never lowers it (only a master password check does).
        c.rememberClock(5L)
        assertEquals(1_000_000L + CLOCK_WRITE_STEP_MS, c.clockMark)
    }

    @Test fun `BOV-1 an exception in a background job never reaches the thread's uncaught handler, an Error does`() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        val got = CopyOnWriteArrayList<Throwable>()
        Thread.setDefaultUncaughtExceptionHandler { _, e -> got += e }
        try {
            val c = AppContainer(tmp.root, cache, appVersion = "test") // The production scope.
            runBlocking { c.appScope.launch { throw java.io.IOException("No space left on device") }.join() }
            Thread.sleep(100)
            assertTrue(got.isEmpty())
            val error = OutOfMemoryError("prueba")
            runBlocking { c.appScope.launch { throw error }.join() }
            Thread.sleep(100)
            assertEquals(listOf<Throwable>(error), got.toList())
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previous)
        }
    }
}
