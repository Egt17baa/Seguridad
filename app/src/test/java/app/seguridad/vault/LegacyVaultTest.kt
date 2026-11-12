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
 * The vault formats this app opens (only `seguridad-vault`; a file without the "format" key is not one), and the lock
 * on time. Two real vaults written by CriptoPanel (1.9.3 for the one without a "version" key, 1.10.0 for v2) stay in
 * `criptopanel/` to check that, since 1.3.1, they are refused like any other file.
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

    /** A vault of this app, as [VaultCrypto.create] makes it. */
    private fun ours() = VaultCrypto.create(master.toCharArray(), clock, KdfParams(1024, 1, 1)).let { (file, dek) -> dek.fill(0); file }

    /**
     * Since 1.3.1 (owner's decision, 1-11-2026) a CriptoPanel vault (criptopanel/vault-v2.json, and vault-noversion.json
     * from before it wrote a version) is not imported: with its right master password, as text and as a file, it gets
     * what any JSON that is not a vault of this app gets, and the vault on the phone stays exactly as it was.
     */
    @Test fun `CriptoPanel data is refused`() = runTest {
        val s = store().apply { load() }
        s.create("otra".toCharArray())
        s.upsert(VaultEntry("1", EntryType.NOTE, "Nota", notes = "secreto", createdAt = 0, updatedAt = 0))
        val vaultFile = File(tmp.root, "vault.bin")
        val before = vaultFile.readBytes()
        val entries = (s.state.value as VaultState.Unlocked).entries
        val files = tmp.root.list()!!.sorted()

        suspend fun importFailure(text: String, asFile: Boolean): String? = try {
            if (asFile) s.importReplace(text.byteInputStream(), master.toCharArray()) else s.importReplace(text, master.toCharArray())
            fail("expected CryptoFailure")
            null
        } catch (e: CryptoFailure) {
            e.message
        }
        for (asFile in listOf(false, true)) {
            val notOurs = importFailure("""{"hola":"esto no es una bóveda"}""", asFile)
            assertEquals("No es una bóveda compatible", notOurs)
            for (name in listOf("vault-v2.json", "vault-noversion.json")) {
                val json = fixture(name)
                assertFalse(name, json.contains("\"format\""))
                assertEquals("$name, as a file: $asFile", notOurs, importFailure(json, asFile))
                assertArrayEquals(name, before, vaultFile.readBytes())
                assertEquals(name, entries, (s.state.value as VaultState.Unlocked).entries)
                assertEquals(name, files, tmp.root.list()!!.sorted())
            }
        }
        listOf("vault-v2.json", "vault-noversion.json").forEach {
            try {
                VaultCrypto.decodeFile(fixture(it))
                fail(it)
            } catch (e: CryptoFailure) {
                assertEquals("No es una bóveda compatible", e.message)
            }
        }
    }

    @Test fun unknownFormatIsRefused() {
        val other = ours().copy(format = "otra-cosa")
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
        try {
            VaultCrypto.decodeFile(json.replace("\"format\":\"seguridad-vault\",", ""))
            fail("a vault without its format must not be read")
        } catch (e: CryptoFailure) {
            assertEquals("No es una bóveda compatible", e.message) // Refused before any key is derived.
        }
        assertEquals(KEY_BYTES, VaultCrypto.unlock(file, master.toCharArray()).also { it.fill(0) }.size) // The original still opens.
    }

    @Test fun noBiometricsForAnotherFormatEvenWhenRecentlyChecked() {
        val file = ours()
        assertEquals(clock, file.masterCheckedAt)
        assertTrue(VaultCrypto.biometricAllowed(file, clock + 1_000))
        assertFalse(VaultCrypto.biometricAllowed(file.copy(format = "otra-cosa"), clock + 1_000)) // Only the format differs.
    }

    @Test fun writeRefusesAnotherFormat() {
        val (file, dek) = VaultCrypto.create(master.toCharArray(), clock, KdfParams(1024, 1, 1))
        try {
            VaultCrypto.write(file.copy(format = "otra-cosa"), dek, VaultData(entries = emptyList()))
            fail()
        } catch (e: IllegalStateException) {
            // expected: write only ever re-seals a file of this app's format.
        } finally {
            dek.fill(0)
        }
    }
}
