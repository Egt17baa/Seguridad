package app.seguridad

import app.seguridad.settings.oneTarget
import app.seguridad.settings.autoBackupUri
import app.seguridad.settings.lastAutoBackupAt
import app.seguridad.settings.autoBackupCopies
import app.seguridad.settings.autoBackupCopiesFolder
import app.seguridad.settings.principal
import app.seguridad.settings.updatePrincipal
import app.seguridad.settings.CLOCK_WRITE_STEP_MS
import app.seguridad.vault.BiometricRefusal
import app.seguridad.vault.PRIMARY_VAULT_ID
import app.seguridad.vault.VaultState
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
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
        c.settings.updatePrincipal { it.oneTarget(autoBackupUri = folder, lastAutoBackupAt = 5L) }
        c.vault.load()
        assertEquals(VaultState.NoVault, c.vault.state.value)
        c.vault.create("maestra-larga".toCharArray())
        assertTrue(c.vault.state.value is VaultState.Unlocked)
        assertNull(c.settings.settings.value.principal.autoBackupUri)
        assertNull(c.settings.settings.value.principal.lastAutoBackupAt)
        assertTrue(c.settings.settings.value.principal.autoBackupOffForNewVault)
        assertEquals(listOf(folder), released)
    }

    /** F6: the word lists come from the assets Android passes, read once; without assets, no crash and no words. */
    @Test fun `the word lists are read from the assets given, once`() = runTest {
        val opened = mutableListOf<String>()
        val c = AppContainer(tmp.root, cache, appVersion = "test", appScope = backgroundScope, openAsset = { name ->
            opened += name
            java.io.ByteArrayInputStream("hola\n\nmundo\n".toByteArray())
        })
        assertEquals(listOf("hola", "mundo"), c.wordLists.eff())
        assertEquals(listOf("hola", "mundo"), c.wordLists.eff())
        assertEquals(listOf(app.seguridad.vault.EFF_WORD_LIST), opened)
        assertEquals(listOf("hola", "mundo"), c.wordLists.bip39English())
        assertEquals(listOf("hola", "mundo"), c.wordLists.bip39English())
        assertEquals(listOf(app.seguridad.vault.EFF_WORD_LIST, app.seguridad.vault.BIP39_ENGLISH_LIST), opened)
        val noAssets = File(tmp.root, "sin").apply { mkdirs() }
        val none = AppContainer(noAssets, cache, appVersion = "test", appScope = backgroundScope)
        assertEquals(emptySet<String>(), none.wordLists.master())
        assertNull(none.wordLists.eff())
        assertNull(none.wordLists.bip39English())
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

    /**
     * Audit B-4 (c): in split screen or a floating window the vault stays open while another app has the focus, so the
     * offer of «Copiar contraseña» is there on the return; a lock (here, leaving the app) still ends it.
     */
    @Test fun theOfferIsShownInSplitScreen() = runTest {
        val elapsed = 0L
        val c = AppContainer(tmp.root, cache, appVersion = "test", appScope = backgroundScope, elapsedRealtime = { elapsed })
        c.settings.load()
        c.vault.load()
        c.vault.create("maestra-larga".toCharArray())
        c.twoStepCopy.offerPassword("e")
        c.leftForAnotherApp()
        assertEquals("e", c.twoStepCopy.takeReturnOffer())
        c.backFromAnotherApp()
        c.twoStepCopy.offerPassword("e")
        c.leftForAnotherApp()
        c.vault.lock(app.seguridad.vault.LockReason.LEFT_APP)
        assertNull(c.twoStepCopy.takeReturnOffer())
        assertNull(c.twoStepCopy.pending())
    }

    @Test fun newVaultWithAutoBackupOffReleasesNothing() = runTest {
        val released = mutableListOf<String>()
        val c = AppContainer(tmp.root, cache, appVersion = "test", releaseFolder = { released += it }, appScope = backgroundScope)
        c.settings.load()
        c.vault.load()
        c.vault.create("maestra-larga".toCharArray())
        assertFalse(c.settings.settings.value.principal.autoBackupOffForNewVault)
        assertTrue(released.isEmpty())
    }

    @Test fun importingAVaultForgetsTheAutomaticCopiesMadeOfTheOldOne() = runTest {
        val c = AppContainer(tmp.root, cache, appVersion = "test", appScope = backgroundScope)
        c.settings.load()
        c.vault.load()
        c.vault.create("maestra-larga".toCharArray())
        c.settings.updatePrincipal { it.oneTarget(autoBackupUri = folder, autoBackupCopies = listOf("doc-1"), autoBackupCopiesFolder = folder) }
        val other = AppContainer(File(tmp.root, "otro").apply { mkdirs() }, cache, appVersion = "test", appScope = backgroundScope)
        other.settings.load()
        other.vault.load()
        other.vault.create("otra-maestra-larga".toCharArray())
        c.vault.importReplace(other.vault.exportText()!!, "otra-maestra-larga".toCharArray())
        // Copies stay on, to the same folder, but the old vault's are never rotated away.
        assertEquals(folder, c.settings.settings.value.principal.autoBackupUri)
        assertTrue(c.settings.settings.value.principal.autoBackupCopies.isEmpty())
        // A wrong password changes nothing, the list included.
        c.settings.updatePrincipal { it.oneTarget(autoBackupCopies = listOf("doc-2"), autoBackupCopiesFolder = folder) }
        try {
            c.vault.importReplace(other.vault.exportText()!!, "mala".toCharArray())
        } catch (e: app.seguridad.crypto.CryptoFailure) {
            // expected
        }
        assertEquals(listOf("doc-2"), c.settings.settings.value.principal.autoBackupCopies)
    }

    // ---- 2.1.1: the leaks of «Salud» (task 5; branch review BR-1, BR-7 c) ----

    /** A login of a test vault with a made-up password. */
    private fun testLogin(id: String, password: String) =
        app.seguridad.vault.VaultEntry(id, app.seguridad.vault.EntryType.LOGIN, "Prueba $id", password = password, createdAt = 1, updatedAt = 1)

    /** The background collectors of [c] (appScope is the test's backgroundScope) run until [ok], or it fails. */
    private fun kotlinx.coroutines.test.TestScope.until(what: String, ok: () -> Boolean) {
        repeat(500) {
            if (ok()) return
            testScheduler.runCurrent()
            Thread.sleep(5)
        }
        throw AssertionError(what)
    }

    /** An open vault with two logins, and a finished check of «Salud» that found both leaked. */
    private suspend fun kotlinx.coroutines.test.TestScope.withLeaks(c: AppContainer) {
        c.settings.load()
        c.vault.load()
        c.vault.create("maestra-larga".toCharArray())
        c.vault.upsert(testLogin("a", "uno-de-prueba"))
        c.vault.upsert(testLogin("b", "dos-de-prueba"))
        val entries = (c.vault.state.value as VaultState.Unlocked).entries
        c.leakResults.start(entries, { 5 }, isOpen = { c.vault.state.value is VaultState.Unlocked }, awaitClosed = { awaitCancellation() })
        until("the check ends") { c.leakResults.state.value.end == app.seguridad.health.LeakResults.End.DONE }
        assertEquals(2, c.leakResults.breached(entries).size)
    }

    @Test fun `the leaks of Salud are forgotten at a lock`() = runTest {
        val c = AppContainer(tmp.root, cache, appVersion = "test", appScope = backgroundScope)
        withLeaks(c)
        c.vault.lock()
        // Dropped by AppContainer's collector, not only hidden by the epoch.
        until("forgotten") { c.leakResults.state.value === app.seguridad.health.LeakResults.EMPTY }
        c.vault.unlock("maestra-larga".toCharArray())
        assertTrue(c.leakResults.breached((c.vault.state.value as VaultState.Unlocked).entries).isEmpty())
    }

    @Test fun `the leaks of Salud are forgotten when another vault is opened`() = runTest {
        val c = AppContainer(tmp.root, cache, appVersion = "test", appScope = backgroundScope)
        withLeaks(c)
        val second = c.createVault("Dos", "maestra-de-prueba-b".toCharArray())
        until("forgotten") { c.leakResults.state.value === app.seguridad.health.LeakResults.EMPTY }
        c.switchTo(PRIMARY_VAULT_ID)
        c.vault.unlock("maestra-larga".toCharArray())
        assertTrue(c.leakResults.breached((c.vault.state.value as VaultState.Unlocked).entries).isEmpty())
        check(second != PRIMARY_VAULT_ID)
    }

    /**
     * Branch review BR-1: «Importar copia» over the open vault (Unlocked to Unlocked, the same lock epoch) forgets the
     * replaced vault's leaks at once, and a check running stops: no request after it.
     */
    @Test fun `importing a copy over the open vault forgets the leaks of Salud and stops the check`() = runTest {
        val c = AppContainer(tmp.root, cache, appVersion = "test", appScope = backgroundScope)
        withLeaks(c)
        val other = AppContainer(File(tmp.root, "otro").apply { mkdirs() }, cache, appVersion = "test", appScope = backgroundScope)
        other.settings.load()
        other.vault.load()
        other.vault.create("otra-maestra-larga".toCharArray())
        other.vault.upsert(testLogin("a", "uno-de-prueba"))
        val epoch = c.lockEpoch
        c.vault.importReplace(other.vault.exportText()!!, "otra-maestra-larga".toCharArray())
        assertTrue(c.vault.state.value is VaultState.Unlocked)
        assertEquals("no lock: the same epoch", epoch, c.lockEpoch)
        assertTrue(c.leakResults.state.value === app.seguridad.health.LeakResults.EMPTY)
        assertTrue(c.leakResults.breached((c.vault.state.value as VaultState.Unlocked).entries).isEmpty())
        // A check running when a copy replaces the vault: stopped, nothing more sent, nothing kept.
        val entries = (c.vault.state.value as VaultState.Unlocked).entries
        val gate = kotlinx.coroutines.CompletableDeferred<Int>()
        var calls = 0
        c.leakResults.start(entries + testLogin("z", "tres-de-prueba"), { calls++; gate.await() }, isOpen = { c.vault.state.value is VaultState.Unlocked }, awaitClosed = { awaitCancellation() })
        until("in flight") { calls == 1 }
        c.vault.importReplace(other.vault.exportText()!!, "otra-maestra-larga".toCharArray())
        gate.complete(5)
        until("dropped") { c.leakResults.state.value === app.seguridad.health.LeakResults.EMPTY }
        repeat(20) { testScheduler.runCurrent() }
        assertEquals("no request after the import", 1, calls)
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
        val saved = c.settings.settings.value.principal.clockHighWaterMs
        fillDisk()
        // The highest time seen is an hour ahead of the phone's clock now (the clock went back an hour).
        val ahead = System.currentTimeMillis() + 60 * 60_000L
        c.rememberClock(ahead)
        assertTrue(errors.isEmpty())
        assertEquals(saved, c.settings.settings.value.principal.clockHighWaterMs) // Not saved...
        assertEquals(ahead, c.clockMark(PRIMARY_VAULT_ID)) // ...but kept in memory,
        assertEquals(BiometricRefusal.CLOCK, c.vault.biometricRefusal) // so the fingerprint still asks for the master.
        // The master password with the disk still full: it opens, and its reset of the mark does not close the app.
        c.vault.lock()
        c.vault.unlock("maestra-larga".toCharArray())
        assertTrue(c.vault.state.value is VaultState.Unlocked)
        assertTrue(errors.isEmpty())
        assertNull(c.vault.biometricRefusal) // The reset holds in memory too.
        assertTrue(c.clockMark(PRIMARY_VAULT_ID) < ahead)
        // Space again: the next time seen, once the back-off has passed, saves the mark.
        freeDisk()
        c.rememberClock(System.currentTimeMillis())
        assertEquals(saved, c.settings.settings.value.principal.clockHighWaterMs) // Not yet: a failed write waits a little.
        elapsed += CLOCK_WRITE_STEP_MS
        c.rememberClock(System.currentTimeMillis())
        assertEquals(c.clockMark(PRIMARY_VAULT_ID), c.settings.settings.value.principal.clockHighWaterMs)
        assertTrue(c.settings.settings.value.principal.clockHighWaterMs < ahead)
        assertTrue(errors.isEmpty())
    }

    @Test fun `BOV-1 a mark that rises less than the step is kept in memory and not written`() = runTest {
        val c = AppContainer(tmp.root, cache, appVersion = "test", appScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined), settingsIo = Dispatchers.Unconfined)
        c.settings.load()
        c.settings.updatePrincipal { it.copy(clockHighWaterMs = 1_000_000L) }
        c.rememberClock(1_000_000L + CLOCK_WRITE_STEP_MS - 1)
        assertEquals(1_000_000L, c.settings.settings.value.principal.clockHighWaterMs)
        assertEquals(1_000_000L + CLOCK_WRITE_STEP_MS - 1, c.clockMark(PRIMARY_VAULT_ID))
        c.rememberClock(1_000_000L + CLOCK_WRITE_STEP_MS)
        assertEquals(1_000_000L + CLOCK_WRITE_STEP_MS, c.settings.settings.value.principal.clockHighWaterMs)
        // A lower time never lowers it (only a master password check does).
        c.rememberClock(5L)
        assertEquals(1_000_000L + CLOCK_WRITE_STEP_MS, c.clockMark(PRIMARY_VAULT_ID))
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

    /**
     * 2.0, task 9, ronda 1 (I-1): leaving the app (MainActivity.onStop, as the lock on leaving) drops every QR text
     * waiting for a vault screen, wifi or 2FA; a picker or the scanner the app opened ([VaultLock.keepOpenOnce]) does
     * not, as it does not lock the vault either.
     */
    @Test fun `leaving the app drops the QR texts waiting for the vault, a picker of ours does not`() = runTest {
        val c = AppContainer(tmp.root, cache, appVersion = "test", appScope = backgroundScope)
        try {
            app.seguridad.tools.qr.QrHandoff.put(app.seguridad.tools.qr.QrTarget.WIFI, "WIFI:T:WPA;S:Casa;P:clave;;")
            val wifi = app.seguridad.tools.qr.QrPendingText(app.seguridad.tools.qr.QrTarget.WIFI)
            app.seguridad.tools.qr.QrHandoff.put(app.seguridad.tools.qr.QrTarget.OTP, "otpauth://totp/x?secret=JBSWY3DPEHPK3PXP")
            val otp = app.seguridad.tools.qr.QrPendingText(app.seguridad.tools.qr.QrTarget.OTP)
            // The import picker at the gate, or the scanner: kept.
            c.leftApp(keepOpenOnce = true)
            assertEquals("WIFI:T:WPA;S:Casa;P:clave;;", wifi.text)
            assertEquals("otpauth://totp/x?secret=JBSWY3DPEHPK3PXP", otp.text)
            assertFalse(wifi.left || otp.left)
            // Home, another app, the screen off: gone, and the screens go back to the inspector.
            c.leftApp(keepOpenOnce = false)
            assertNull(wifi.text)
            assertNull(otp.text)
            assertTrue(wifi.left && otp.left)
        } finally {
            app.seguridad.tools.qr.QrHandoff.clear()
        }
    }
}
