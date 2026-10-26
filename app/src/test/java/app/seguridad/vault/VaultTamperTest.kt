package app.seguridad.vault

import app.seguridad.core.appJson
import app.seguridad.crypto.Aead
import app.seguridad.crypto.CryptoFailure
import app.seguridad.crypto.KEY_BYTES
import app.seguridad.crypto.KdfParams
import app.seguridad.crypto.NONCE_BYTES
import app.seguridad.crypto.SALT_BYTES
import app.seguridad.crypto.aead
import app.seguridad.crypto.argon2id
import app.seguridad.crypto.randomBytes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.Base64

private val QUICK = KdfParams(1024, 1, 1)
private const val DAY = 24 * 60 * 60_000L

/** What someone with root (write access to the app's files) might try on a version-2 vault. */
class VaultTamperTest {
    private val note = VaultEntry("1", EntryType.NOTE, "Wallet", notes = "secreto", createdAt = 0, updatedAt = 0)

    private fun vault(now: Long = 10 * DAY): Pair<VaultFile, ByteArray> {
        val (file, dek) = VaultCrypto.create("maestra".toCharArray(), now, QUICK)
        return VaultCrypto.write(file, dek, listOf(note)) to dek
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
        refused { VaultCrypto.read(forged, dek) } // …but opening the entries is not.
        assertFalse(VaultCrypto.biometricAllowed(file, 21 * DAY))
    }

    @Test fun `a check date in the future never allows biometrics`() {
        val (file, _) = vault(now = 50 * DAY)
        assertFalse(VaultCrypto.biometricAllowed(file, 49 * DAY))
        assertTrue(VaultCrypto.biometricAllowed(file, 50 * DAY + 1))
    }

    @Test fun `older data spliced into the current file is refused`() {
        val (first, dek) = vault()
        val second = VaultCrypto.write(first, dek, listOf(note, note.copy(id = "2")))
        refused { VaultCrypto.read(second.copy(data = first.data), dek) }
        assertEquals(2, VaultCrypto.read(second, dek).size)
    }

    @Test fun `changing the cost, salt or version breaks the unlock`() {
        val (file, _) = vault()
        val otherSalt = Base64.getEncoder().encodeToString(ByteArray(SALT_BYTES) { 7 })
        listOf(
            file.copy(kdf = file.kdf.copy(iterations = 2)),
            file.copy(salt = otherSalt),
            file.copy(version = 1), // Downgrade to the old, less bound format.
        ).forEach { refused { VaultCrypto.unlock(it, "maestra".toCharArray()) } }
        VaultCrypto.unlock(file, "maestra".toCharArray())
    }

    @Test fun `version 1 vaults still open and become version 2 at the next master unlock`() {
        val master = "vieja".toCharArray()
        val dek = randomBytes(KEY_BYTES)
        val salt = randomBytes(SALT_BYTES)
        val kek = argon2id(master, salt, QUICK)
        fun v1Seal(key: ByteArray, plain: ByteArray, aad: String): String {
            val nonce = randomBytes(NONCE_BYTES)
            return Base64.getEncoder().encodeToString(nonce + aead(Aead.AES_256_GCM, true, key, nonce, aad.toByteArray(), plain))
        }
        val v1 = VaultFile(
            version = 1,
            kdf = VaultKdf(QUICK.memoryKiB, QUICK.iterations, QUICK.parallelism),
            salt = Base64.getEncoder().encodeToString(salt),
            wrappedKey = v1Seal(kek, dek, "criptopanel-vault-key-v1"),
            data = v1Seal(dek, appJson.encodeToString(listOf(note)).toByteArray(), "criptopanel-vault-data-v1"),
            masterCheckedAt = 0,
        )
        val opened = VaultCrypto.unlock(v1, master)
        assertEquals(listOf("Wallet"), VaultCrypto.read(v1, opened).map { it.title })
        assertFalse(VaultCrypto.biometricAllowed(v1, 1)) // Its date is not authenticated.
        val upgraded = VaultCrypto.afterMasterUnlock(v1, opened, master, now = 5, kdf = QUICK)
        assertEquals(VaultFile.VERSION, upgraded.version)
        assertTrue(VaultCrypto.biometricAllowed(upgraded, 6))
        // Same data key, so a fingerprint set up before the upgrade keeps working.
        assertEquals(listOf("Wallet"), VaultCrypto.read(upgraded, dek).map { it.title })
        assertEquals(listOf("Wallet"), VaultCrypto.read(upgraded, VaultCrypto.unlock(upgraded, master)).map { it.title })
    }

    /** A 1.9.2 vault on disk: version 1, and its JSON has no "version" key (it was the default then). */
    private fun v192Json(master: CharArray): String {
        val dek = randomBytes(KEY_BYTES)
        val salt = randomBytes(SALT_BYTES)
        val kek = argon2id(master, salt, QUICK)
        fun v1Seal(key: ByteArray, plain: ByteArray, aad: String): String {
            val nonce = randomBytes(NONCE_BYTES)
            return Base64.getEncoder().encodeToString(nonce + aead(Aead.AES_256_GCM, true, key, nonce, aad.toByteArray(), plain))
        }
        return """{"kdf":{"memoryKiB":${QUICK.memoryKiB},"iterations":${QUICK.iterations},"parallelism":${QUICK.parallelism}},""" +
            """"salt":"${Base64.getEncoder().encodeToString(salt)}","wrappedKey":"${v1Seal(kek, dek, "criptopanel-vault-key-v1")}",""" +
            """"data":"${v1Seal(dek, appJson.encodeToString(listOf(note)).toByteArray(), "criptopanel-vault-data-v1")}","masterCheckedAt":5}"""
    }

    @Test fun `vault files without a version key, as 1_9_2 and 1_9_3 wrote them, still open`() {
        val master = "maestra".toCharArray()
        // 1.9.2: version 1 without the key.
        val old = VaultCrypto.decodeFile(v192Json(master))
        assertEquals(VaultFile.VERSION_UNKNOWN, old.version)
        val oldKey = VaultCrypto.unlock(old, master)
        assertEquals(listOf("Wallet"), VaultCrypto.read(old, oldKey).map { it.title })
        assertEquals(1, VaultCrypto.resolve(old, oldKey).version)
        val upgraded = VaultCrypto.afterMasterUnlock(old, oldKey, master, now = 10, kdf = QUICK)
        assertTrue(VaultCrypto.encodeFile(upgraded).contains("\"version\":3"))
        // 1.9.3: version 2 without the key.
        val (v2, dek) = vault()
        val stripped = VaultCrypto.decodeFile(VaultCrypto.encodeFile(v2).replace("\"version\":3,", ""))
        assertEquals(VaultFile.VERSION_UNKNOWN, stripped.version)
        assertTrue(VaultCrypto.unlock(stripped, "maestra".toCharArray()).contentEquals(dek))
        assertEquals(VaultFile.VERSION, VaultCrypto.resolve(stripped, dek).version)
        assertEquals(1, VaultCrypto.read(stripped, dek).size)
    }

    @Test fun `new files always say their version`() {
        assertTrue(VaultCrypto.encodeFile(vault().first).contains("\"version\":3"))
    }
}
