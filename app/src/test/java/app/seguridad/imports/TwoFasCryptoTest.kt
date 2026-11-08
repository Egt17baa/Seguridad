package app.seguridad.imports

import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.Base64

/**
 * 1.4.0, Task 10: 2FAS's encrypted backup: `servicesEncrypted` is Base64(ciphertext and tag):Base64(salt):Base64(IV),
 * PBKDF2-HMAC-SHA256 (10 000 iterations, not in the file) and AES-256-GCM; what it opens is the array of services. No
 * format document from 2FAS was found (see scripts/import-fixtures/gen_encrypted.py): the fixture follows Clavero's
 * design and two published READMEs, with [FIXTURE_PASSWORD].
 */
class TwoFasCryptoTest {
    private companion object {
        /** Password bytes, PBKDF2 key and the services' plaintext. */
        const val OPEN_KEPT = 3

        /** A wrong password: password bytes, PBKDF2 key and the GCM output, wiped before the tag says no. */
        const val WRONG_KEPT = 3
    }

    private val plain = resource("2fas/2fas-cifrado-claro.2fas")
    private val encrypted = resource("2fas/2fas-cifrado.2fas")

    private fun encryptedField(root: kotlinx.serialization.json.JsonObject) = (root["servicesEncrypted"] as JsonPrimitive).content

    @Test fun withoutAPasswordItAsksForOne() {
        assertEquals(ReadResult.NeedsPassword(ImportFormat.TWOFAS), readImport(encrypted))
    }

    @Test fun opensAsItsPlainTwin() {
        val twin = okRead(plain)
        assertSameRead(twin, okRead(encrypted, FIXTURE_PASSWORD))
        assertEquals(2, twin.items.size)
        assertEquals(listOf("Trabajo"), twin.items[0].folders) // The groups, in clear beside the encrypted services.
        assertEquals(listOf(SkipReason.NOT_TOTP), twin.skipped.map { it.reason })
    }

    @Test fun aWrongPasswordFailsTheTagAndReleasesNothing() {
        val seen = ArrayList<ByteArray>()
        try {
            openTwoFas(jsonRoot(encrypted), "prueba-clavero-1.5".toCharArray(), JSON_MAX_TREE_BYTES, Secrets(seen::add))
            fail("opened")
        } catch (e: ImportFailure) {
            assertEquals(IMPORT_WRONG_PASSWORD, e.message)
        }
        assertAllZero(seen, exactly = WRONG_KEPT)
        assertEquals(IMPORT_WRONG_PASSWORD, failedRead(encrypted, "x"))
    }

    @Test fun everyByteFlippedFailsTheGcmTag() {
        val e = twoFasEncrypted(jsonRoot(encrypted))
        val s = Secrets()
        val key = twoFasKey(e, FIXTURE_PASSWORD.toCharArray(), s)
        assertTrue(twoFasOpen(e, key, s).isNotEmpty())
        var flips = 0
        for (part in 0..1) {
            val bytes = if (part == 0) e.sealed else e.iv
            for (i in bytes.indices) {
                val f = bytes.copyOf().also { it[i] = (it[i].toInt() xor 0x01).toByte() }
                try {
                    twoFasOpen(if (part == 0) e.copy(sealed = f) else e.copy(iv = f), key, s)
                    fail("flip $part/$i accepted")
                } catch (x: ImportFailure) {
                    assertEquals(IMPORT_WRONG_PASSWORD, x.message)
                }
                flips++
            }
        }
        assertTrue(flips > 12 + 16 + 100)
        s.wipe()
        assertArrayEquals(ByteArray(key.size), key)
    }

    @Test fun theIterationsAreFixedAndInRange() {
        checkPbkdf2Iterations(TWOFAS_PBKDF2_ITERATIONS)
        assertEquals(10_000, TWOFAS_PBKDF2_ITERATIONS)
    }

    @Test fun brokenFieldsAreRefusedBeforeWork() {
        val root = jsonRoot(encrypted)
        val (ct, salt, iv) = encryptedField(root).split(':')
        val shortIv = Base64.getEncoder().encodeToString(ByteArray(8))
        val broken = listOf(
            "$ct:$salt", "$ct:$salt:$iv:$iv", "$ct:$salt:$shortIv", "$ct::$iv", "AAAA:$salt:$iv", "%%%%:$salt:$iv",
            "$ct:${Base64.getEncoder().encodeToString(ByteArray(4))}:$iv",
        )
        for (b in broken) {
            val ms = millis { assertEquals(b.take(20), IMPORT_BROKEN, failedRead(root.with("servicesEncrypted", b).bytes())) }
            assertTrue("$ms ms", ms < 50)
        }
        assertEquals(IMPORT_BROKEN, failedRead(root.with("servicesEncrypted", 5).bytes()))
    }

    @Test fun theDecryptedServicesMustBeAnArray() {
        val e = twoFasEncrypted(jsonRoot(encrypted))
        val s = Secrets()
        val key = twoFasKey(e, FIXTURE_PASSWORD.toCharArray(), s)
        for (inner in listOf("""{"services":[]}""", "5", "no es json")) {
            val iv = ByteArray(12).also { java.security.SecureRandom().nextBytes(it) }
            val c = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
            c.init(javax.crypto.Cipher.ENCRYPT_MODE, javax.crypto.spec.SecretKeySpec(key, "AES"), javax.crypto.spec.GCMParameterSpec(128, iv))
            val q = e.copy(sealed = c.doFinal(inner.toByteArray()), iv = iv)
            try {
                twoFasRead(q, key, null, JSON_MAX_TREE_BYTES, s)
                fail(inner)
            } catch (x: ImportFailure) {
                assertEquals(inner, IMPORT_BROKEN, x.message)
            }
        }
        s.wipe()
    }

    @Test fun truncatedFilesFail() {
        assertTruncationsFail(encrypted)
        val root = jsonRoot(encrypted)
        val (ct, salt, iv) = encryptedField(root).split(':')
        for (k in 1..5) {
            val message = failedRead(root.with("servicesEncrypted", ct.substring(0, ct.length * k / 6) + ":$salt:$iv").bytes())
            assertTrue(message, message == IMPORT_BROKEN || message == IMPORT_WRONG_PASSWORD)
        }
    }

    @Test fun anEncryptedPartOverTheBoundIsRefusedBeforeDecoding() {
        val root = jsonRoot(encrypted)
        val (_, salt, iv) = encryptedField(root).split(':')
        val huge = "A".repeat(IMPORT_MAX_ENCRYPTED_CHARS) + ":$salt:$iv"
        assertEquals(IMPORT_ENCRYPTED_TOO_BIG, failedRead(root.with("servicesEncrypted", huge).bytes()))
    }

    @Test fun passwordAndKeysAreZeroed() {
        val seen = ArrayList<ByteArray>()
        openTwoFas(jsonRoot(encrypted), FIXTURE_PASSWORD.toCharArray(), JSON_MAX_TREE_BYTES, Secrets(seen::add))
        // M-4: exactly, and the key and the plaintext themselves.
        assertAllZero(seen, exactly = OPEN_KEPT)
        val steps = ArrayList<ByteArray>()
        val s = Secrets(steps::add)
        val e = twoFasEncrypted(jsonRoot(encrypted))
        val key = twoFasKey(e, FIXTURE_PASSWORD.toCharArray(), s)
        val services = twoFasOpen(e, key, s)
        assertKept(steps, key, services)
        s.wipe()
        assertAllZero(steps, exactly = OPEN_KEPT)
    }
}
