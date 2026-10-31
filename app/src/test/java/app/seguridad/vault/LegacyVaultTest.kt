package app.seguridad.vault

import app.seguridad.crypto.CryptoFailure
import app.seguridad.crypto.KEY_BYTES
import app.seguridad.crypto.KdfParams
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

/**
 * Vaults written by CriptoPanel (real files, made with its own code: 1.9.2 for v1, 1.9.3 for the one without a
 * "version" key, 1.10.0 for v2) open in Seguridad and are rewritten as seguridad-vault v2.
 */
class LegacyVaultTest {
    @get:Rule val tmp = TemporaryFolder()
    private var clock = 1_800_000_000_000L
    private val master = "maestra-de-prueba"

    private fun fixture(name: String) = javaClass.getResource("/criptopanel/$name")!!.readText()

    private var keptOpen = false

    private fun store(idleLockMs: () -> Long = { 5 * 60_000L }) =
        VaultStore(
            File(tmp.root, "vault.bin"), Dispatchers.Unconfined, Dispatchers.Unconfined, now = { clock }, kdf = { KdfParams(1024, 1, 1) },
            idleLockMs = idleLockMs, keptOpen = { keptOpen }, elapsed = { clock },
        )

    private suspend fun importsAndRewrites(name: String) {
        val s = store().apply { load() }
        s.create("otra".toCharArray())
        s.importReplace(fixture(name), master.toCharArray())
        val entries = (s.state.value as VaultState.Unlocked).entries
        assertEquals(2, entries.size)
        assertEquals(setOf(EntryType.LOGIN, EntryType.TOTP), entries.map { it.type }.toSet())
        assertEquals("JBSWY3DPEHPK3PXP", entries.single { it.type == EntryType.TOTP }.totp!!.secret)
        val onDisk = VaultCrypto.decodeFile(File(tmp.root, "vault.bin").readText())
        assertEquals("seguridad-vault", onDisk.format)
        assertEquals(VaultFile.VERSION, onDisk.version)
        assertEquals(clock, onDisk.masterCheckedAt)
        s.lock()
        s.unlock(master.toCharArray())
        assertEquals(2, (s.state.value as VaultState.Unlocked).entries.size)
        // And from a fresh start, as after restarting the app.
        val again = store().apply { load() }
        again.unlock(master.toCharArray())
        assertEquals(2, (again.state.value as VaultState.Unlocked).entries.size)
    }

    @Test fun importsCriptopanelV1() = runTest { importsAndRewrites("vault-v1.json") }

    @Test fun importsCriptopanelNoVersion() = runTest { importsAndRewrites("vault-noversion.json") }

    @Test fun importsCriptopanelV2() = runTest { importsAndRewrites("vault-v2.json") }

    @Test fun legacyFilesHaveNoFormatKeyAndReadAsLegacy() {
        listOf("vault-v1.json", "vault-noversion.json", "vault-v2.json").forEach {
            assertFalse(fixture(it).contains("\"format\""))
            assertEquals(VaultFile.LEGACY_FORMAT, VaultCrypto.decodeFile(fixture(it)).format)
        }
    }

    @Test fun upgradeKeepsTheDataKeyAndBumpsTheRevision() {
        val legacy = VaultCrypto.decodeFile(fixture("vault-v2.json"))
        val dek = VaultCrypto.unlock(legacy, master.toCharArray())
        val upgraded = VaultCrypto.afterMasterUnlock(legacy, dek.copyOf(), master.toCharArray(), now = 42, kdf = KdfParams(1024, 1, 1))
        assertEquals(VaultFile.FORMAT, upgraded.format)
        assertEquals(VaultFile.VERSION, upgraded.version)
        assertEquals(legacy.revision + 1, upgraded.revision)
        assertEquals(42L, upgraded.masterCheckedAt)
        assertArrayEquals(dek, VaultCrypto.unlock(upgraded, master.toCharArray()))
        assertTrue(VaultCrypto.encodeFile(upgraded).contains("\"format\":\"seguridad-vault\""))
    }

    @Test fun legacyFormatWithNewLabelsFails() {
        val relabelled = VaultCrypto.decodeFile(fixture("vault-v2.json")).copy(format = VaultFile.FORMAT)
        try {
            VaultCrypto.unlock(relabelled, master.toCharArray())
            fail()
        } catch (e: CryptoFailure) {
            // expected
        }
    }

    @Test fun unknownFormatIsRefused() {
        val other = VaultCrypto.decodeFile(fixture("vault-v2.json")).copy(format = "otra-cosa")
        try {
            VaultCrypto.unlock(other, master.toCharArray())
            fail()
        } catch (e: CryptoFailure) {
            assertEquals("No es una bóveda compatible", e.message)
        }
    }

    @Test fun newVaultIsSeguridadFormat() {
        assertEquals("seguridad-vault", VaultCrypto.create("m".toCharArray(), 0, KdfParams(1024, 1, 1)).first.format)
    }

    @Test fun idleLockUsesProvider() = runTest {
        val s = store(idleLockMs = { 1_000 }).apply { load(); create("m".toCharArray()) }
        clock += 1_001
        assertTrue(s.lockIfIdle())
        assertEquals(VaultState.Locked, s.state.value)
    }

    @Test fun zeroIdleLockNeverLocksOnTime() = runTest {
        val s = store(idleLockMs = { 0 }).apply { load(); create("m".toCharArray()) }
        clock += 365L * 24 * 60 * 60_000
        assertFalse(s.lockIfIdle())
        assertTrue(s.state.value is VaultState.Unlocked)
    }

    @Test fun onExitStillLocksAfterTheBackstopWhileAPickerKeepsItOpen() = runTest {
        // «Al salir» + a picker or the QR scanner on top (MainActivity.onStop did not lock): Home or screen-off there
        // must not leave the vault open for hours.
        val s = store(idleLockMs = { 0 }).apply { load(); create("m".toCharArray()) }
        keptOpen = true
        clock += AWAY_BACKSTOP_MS - 1
        assertFalse(s.lockIfIdle())
        clock += 1
        assertTrue(s.lockIfIdle())
        assertEquals(VaultState.Locked, s.state.value)
    }

    @Test fun onExitBackstopCountsFromTheLastUse() = runTest {
        val s = store(idleLockMs = { 0 }).apply { load(); create("m".toCharArray()) }
        clock += 10 * 60_000L // Ten minutes in the app: «Al salir» does not lock on time.
        assertFalse(s.lockIfIdle())
        s.touch()
        keptOpen = true // Then the user opens the scanner.
        clock += AWAY_BACKSTOP_MS - 1
        assertFalse(s.lockIfIdle())
        clock += 1
        assertTrue(s.lockIfIdle())
    }

    @Test fun chosenIdleTimeStillAppliesWhileKeptOpen() = runTest {
        val s = store(idleLockMs = { 60_000 }).apply { load(); create("m".toCharArray()) }
        keptOpen = true
        clock += 60_000
        assertTrue(s.lockIfIdle())
    }

    @Test fun seguridadVaultWithoutFormatKeyFails() {
        val (file, dek) = VaultCrypto.create(master.toCharArray(), clock, KdfParams(1024, 1, 1))
        dek.fill(0)
        val json = VaultCrypto.encodeFile(file)
        assertTrue(json.contains("\"format\":\"seguridad-vault\","))
        val stripped = VaultCrypto.decodeFile(json.replace("\"format\":\"seguridad-vault\",", ""))
        assertEquals(VaultFile.LEGACY_FORMAT, stripped.format) // Reads as a legacy file…
        assertUnlockFails(stripped) // …whose legacy labels do not open it.
        assertUnlockFails(file.copy(format = VaultFile.LEGACY_FORMAT))
        assertEquals(KEY_BYTES, VaultCrypto.unlock(file, master.toCharArray()).also { it.fill(0) }.size) // The original still opens.
    }

    private fun assertUnlockFails(f: VaultFile) {
        try {
            VaultCrypto.unlock(f, master.toCharArray())
            fail()
        } catch (e: CryptoFailure) {
            // expected
        }
    }

    @Test fun noBiometricsForLegacyV2EvenWhenRecentlyChecked() {
        val legacy = VaultCrypto.decodeFile(fixture("vault-v2.json")).copy(masterCheckedAt = clock)
        assertEquals(2, legacy.version)
        assertFalse(VaultCrypto.biometricAllowed(legacy, clock + 1_000))
        assertTrue(VaultCrypto.biometricAllowed(legacy.copy(format = VaultFile.FORMAT), clock + 1_000)) // Only the format differs.
    }

    @Test fun writeRefusesALegacyFile() {
        val legacy = VaultCrypto.decodeFile(fixture("vault-v2.json"))
        val dek = VaultCrypto.unlock(legacy, master.toCharArray())
        try {
            VaultCrypto.write(legacy, dek, emptyList())
            fail()
        } catch (e: IllegalStateException) {
            // expected: a legacy file is only ever rewritten by afterMasterUnlock.
        } finally {
            dek.fill(0)
        }
    }
}
