package app.seguridad.vault

import app.seguridad.crypto.CryptoFailure
import app.seguridad.crypto.KdfParams
import app.seguridad.crypto.SALT_BYTES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.Base64

private val QUICK = KdfParams(1024, 1, 1)
private const val DAY = 24 * 60 * 60_000L

/** What someone with root (write access to the app's files) might try on a vault (version 4 since 2.0). */
class VaultTamperTest {
    private val note = VaultEntry("1", EntryType.NOTE, "Wallet", notes = "secreto", createdAt = 0, updatedAt = 0)

    private fun vault(now: Long = 10 * DAY): Pair<VaultFile, ByteArray> {
        val (file, dek) = VaultCrypto.create("maestra".toCharArray(), now, QUICK)
        return VaultCrypto.write(file, dek, VaultData(entries = listOf(note))) to dek
    }

    private fun refused(block: () -> Unit) {
        try {
            block()
            fail("should have been refused")
        } catch (e: CryptoFailure) {
            // expected
        }
    }

    @Test fun `a later master check date is detected when the entries are read`() {
        val (file, dek) = vault(now = 0)
        val forged = file.copy(masterCheckedAt = 20 * DAY)
        assertTrue(VaultCrypto.biometricAllowed(forged, 21 * DAY)) // The quick check is fooled…
        refused { VaultCrypto.read(forged, dek).entries } // …but opening the entries is not.
        assertFalse(VaultCrypto.biometricAllowed(file, 21 * DAY))
    }

    @Test fun `a check date in the future never allows biometrics`() {
        val (file, _) = vault(now = 50 * DAY)
        assertFalse(VaultCrypto.biometricAllowed(file, 49 * DAY))
        assertTrue(VaultCrypto.biometricAllowed(file, 50 * DAY + 1))
    }

    @Test fun `older data spliced into the current file is refused`() {
        val (first, dek) = vault()
        val second = VaultCrypto.write(first, dek, VaultData(entries = listOf(note, note.copy(id = "2"))))
        refused { VaultCrypto.read(second.copy(data = first.data), dek).entries }
        assertEquals(2, VaultCrypto.read(second, dek).entries.size)
    }

    @Test fun `changing the cost, salt or version breaks the unlock`() {
        val (file, _) = vault()
        val otherSalt = Base64.getEncoder().encodeToString(ByteArray(SALT_BYTES) { 7 })
        listOf(
            file.copy(kdf = file.kdf.copy(iterations = 2)),
            file.copy(salt = otherSalt),
            file.copy(version = 1), // A version this format never had.
        ).forEach { refused { VaultCrypto.unlock(it, "maestra".toCharArray()) } }
        VaultCrypto.unlock(file, "maestra".toCharArray())
    }

    @Test fun `a vault file without a version key still opens`() {
        // 2.0: a 3 (the real vault-1.4.3.json) without its version still opens, as a 3.
        val text = javaClass.getResourceAsStream("/seguridad/vault-1.4.3.json")!!.use { it.readBytes() }.decodeToString()
        val stripped = VaultCrypto.decodeFile(text.replace("\"version\":3,", ""))
        assertEquals(VaultFile.VERSION_UNKNOWN, stripped.version)
        val dek = VaultCrypto.unlock(stripped, "maestra-de-prueba-143".toCharArray())
        assertEquals(VaultFile.VERSION_3, VaultCrypto.resolve(stripped, dek).version)
        assertEquals(4, VaultCrypto.read(stripped, dek).entries.size)
        // A 4 always says it, and is never tried without it.
        val (v4, _) = vault()
        refused { VaultCrypto.unlock(VaultCrypto.decodeFile(VaultCrypto.encodeFile(v4).replace("\"version\":4,", "")), "maestra".toCharArray()) }
    }

    @Test fun `new files always say their version`() {
        assertTrue(VaultCrypto.encodeFile(vault().first).contains("\"version\":4"))
    }
}
