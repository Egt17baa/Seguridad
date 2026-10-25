package app.seguridad.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64

private fun hexBytes(s: String) = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

/** Cheap KDF so the tests stay fast; one test uses the real defaults. */
private val FAST = KdfParams(memoryKiB = 1024, iterations = 1, parallelism = 1)

private inline fun assertFails(block: () -> Unit) {
    try {
        block()
        fail("expected CryptoFailure")
    } catch (e: CryptoFailure) {
        // expected
    }
}

class CryptoTest {
    @Test fun `argon2id matches RFC 9106`() {
        val tag = argon2idRaw(ByteArray(32) { 1 }, ByteArray(16) { 2 }, ByteArray(8) { 3 }, ByteArray(12) { 4 }, KdfParams(32, 3, 4), 32)
        assertEquals("0d640df58d78766c08c037a34a8b53c9d01ef0452d75b65eb52520e96b01e659", hex(tag).lowercase())
    }

    @Test fun `aes gcm matches NIST test case 16`() {
        val key = hexBytes("feffe9928665731c6d6a8f9467308308feffe9928665731c6d6a8f9467308308")
        val iv = hexBytes("cafebabefacedbaddecaf888")
        val aad = hexBytes("feedfacedeadbeeffeedfacedeadbeefabaddad2")
        val pt = hexBytes(
            "d9313225f88406e5a55909c5aff5269a86a7a9531534f7da2e4c303d8a318a721c3c0c95956809532fcf0e2449a6b525b16aedf5aa0de657ba637b39",
        )
        val out = aead(Aead.AES_256_GCM, true, key, iv, aad, pt)
        assertEquals(
            "522dc1f099567d07f47f37a32a84427d643a8cdcbfe5c0c97598a2bd2555d1aa8cb08e48590dbb3da7b08b1056828838c5f61e6393ba7a0abcc9f662" +
                "76fc6ece0f4e1768cddf8853bb2d551b",
            hex(out).lowercase(),
        )
        assertArrayEquals(pt, aead(Aead.AES_256_GCM, false, key, iv, aad, out))
    }

    @Test fun `chacha20 poly1305 matches RFC 8439`() {
        val key = hexBytes("808182838485868788898a8b8c8d8e8f909192939495969798999a9b9c9d9e9f")
        val nonce = hexBytes("070000004041424344454647")
        val aad = hexBytes("50515253c0c1c2c3c4c5c6c7")
        val pt = "Ladies and Gentlemen of the class of '99: If I could offer you only one tip for the future, sunscreen would be it.".toByteArray()
        val out = aead(Aead.CHACHA20_POLY1305, true, key, nonce, aad, pt)
        assertEquals("1ae10b594f09e26a7e902ecbd0600691", hex(out.copyOfRange(out.size - 16, out.size)).lowercase())
        assertEquals("d31a8d34648e60db7b86afbc53ef7ec2", hex(out.copyOfRange(0, 16)).lowercase())
    }

    @Test fun `decrypts texts written by an independent implementation`() {
        val pw = "correct horse ñ".toCharArray()
        val aes = "CP1:Q1ABAQEBAAAEAAEBAAECAwQFBgcICQoLDA0OD2RlZmdoaWprbG1ub2loismlG8RNiFvPJx5I1k0nVb9bEoje6KduRwcTuem4VA"
        val chacha = "CP1:Q1ABAQIBAAAEAAEBAAECAwQFBgcICQoLDA0OD2RlZmdoaWprbG1ub5E-Bvs0ABX6YQQ4Sj5rVJut-hWYOUmh1uh1R6Zw5pADRg"
        assertEquals("Hola, señor 🔐", decryptText(aes, pw))
        assertEquals("Hola, señor 🔐", decryptText(chacha, pw))
    }

    @Test fun `text round trip with both ciphers and the real defaults`() {
        Aead.entries.forEach { alg ->
            val t = encryptText("secreto\nmultilínea ✓", "pw".toCharArray(), alg, FAST)
            assertTrue(t.startsWith("SG1:"))
            assertEquals("secreto\nmultilínea ✓", decryptText(t, "pw".toCharArray()))
        }
        val real = encryptText("x", "pw".toCharArray())
        assertEquals("x", decryptText(real, "pw".toCharArray()))
    }

    @Test fun `same text twice gives different ciphertexts`() {
        assertNotEquals(encryptText("a", "p".toCharArray(), kdf = FAST), encryptText("a", "p".toCharArray(), kdf = FAST))
    }

    @Test fun `wrong password, tampering and garbage fail`() {
        val t = encryptText("hola", "pw".toCharArray(), kdf = FAST)
        assertFails { decryptText(t, "PW".toCharArray()) }
        val raw = Base64.getUrlDecoder().decode(t.removePrefix(TEXT_PREFIX))
        for (i in raw.indices step 7) {
            val bad = raw.copyOf().also { it[i] = (it[i].toInt() xor 1).toByte() }
            assertFails { decryptText(TEXT_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bad), "pw".toCharArray()) }
        }
        assertFails { decryptText("hola", "pw".toCharArray()) }
        assertFails { decryptText("SG1:%%%", "pw".toCharArray()) }
        assertFails { decryptText("CP1:%%%", "pw".toCharArray()) }
    }

    @Test fun `chat line breaks are ignored`() {
        val t = encryptText("hola", "pw".toCharArray(), kdf = FAST)
        assertEquals("hola", decryptText(t.chunked(20).joinToString("\n "), "pw".toCharArray()))
    }


    @Test fun `argon2id public API rejects invalid parameters before allocating memory`() {
        val password = "pw".toCharArray()
        val salt = ByteArray(SALT_BYTES)
        try {
            try {
                argon2id(password, salt, KdfParams(memoryKiB = MAX_KDF_MEMORY_KIB + 1, iterations = 1))
                fail("expected CryptoFailure for excessive memory")
            } catch (e: CryptoFailure) {
                // Rejected before the expensive KDF allocation.
            }
            try {
                argon2id(password, salt, KdfParams(memoryKiB = 1024, iterations = 0))
                fail("expected CryptoFailure for invalid iterations")
            } catch (e: CryptoFailure) {
                // Rejected before the expensive KDF allocation.
            }
        } finally {
            password.fill('\\u0000')
        }
    }

    @Test fun `argon2id public API rejects malformed salt and output length`() {
        try {
            argon2id("pw".toCharArray(), ByteArray(SALT_BYTES - 1), FAST)
            fail("expected IllegalArgumentException for malformed salt")
        } catch (e: IllegalArgumentException) {
            // expected
        }
        try {
            argon2id("pw".toCharArray(), ByteArray(SALT_BYTES), FAST, 1025)
            fail("expected IllegalArgumentException for unbounded output")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test fun `memory bomb headers are refused`() {
        val raw = Base64.getUrlDecoder().decode(encryptText("a", "p".toCharArray(), kdf = FAST).removePrefix(TEXT_PREFIX))
        raw[6] = 0x7F // memoryKiB high byte
        assertFails { decryptText(TEXT_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(raw), "p".toCharArray()) }
    }

    private fun roundTrip(size: Int, alg: Aead = Aead.AES_256_GCM) {
        val data = ByteArray(size) { (it * 31 + 7).toByte() }
        val enc = ByteArrayOutputStream()
        encryptStream(ByteArrayInputStream(data), enc, "doc.pdf", "pw".toCharArray(), alg, FAST)
        val dec = ByteArrayOutputStream()
        val name = decryptStream(ByteArrayInputStream(enc.toByteArray()), dec, "pw".toCharArray())
        assertEquals("doc.pdf", name)
        assertArrayEquals("size $size", data, dec.toByteArray())
    }

    @Test fun `file round trips at chunk boundaries`() {
        listOf(0, 1, CHUNK_BYTES - 1, CHUNK_BYTES, CHUNK_BYTES + 1, 3 * CHUNK_BYTES + 5).forEach { roundTrip(it) }
        roundTrip(CHUNK_BYTES * 2, Aead.CHACHA20_POLY1305)
    }

    @Test fun `decrypts a file written by an independent implementation`() {
        val ref = requireNotNull(javaClass.classLoader.getResource("criptopanel/ref.cpenc")).readBytes()
        val out = ByteArrayOutputStream()
        assertEquals("foto ñ.jpg", decryptStream(ByteArrayInputStream(ref), out, "pw".toCharArray()))
        assertArrayEquals(ByteArray(65536 + 10) { ((it * 7) % 251).toByte() }, out.toByteArray())
    }

    @Test fun `truncation, reordering and bit flips in files are detected`() {
        val data = ByteArray(3 * CHUNK_BYTES) { it.toByte() }
        val enc = ByteArrayOutputStream().also { encryptStream(ByteArrayInputStream(data), it, "a", "pw".toCharArray(), kdf = FAST) }.toByteArray()
        val headerAndName = enc.size - 3 * (CHUNK_BYTES + TAG_BYTES)
        fun fails(bytes: ByteArray) = assertFails { decryptStream(ByteArrayInputStream(bytes), ByteArrayOutputStream(), "pw".toCharArray()) }
        fails(enc.copyOf(enc.size - (CHUNK_BYTES + TAG_BYTES))) // last record dropped
        fails(enc.copyOf(enc.size - 1))
        val r = CHUNK_BYTES + TAG_BYTES
        val swapped = enc.copyOf().also {
            System.arraycopy(enc, headerAndName + r, it, headerAndName, r)
            System.arraycopy(enc, headerAndName, it, headerAndName + r, r)
        }
        fails(swapped)
        fails(enc.copyOf().also { it[headerAndName + 100] = (it[headerAndName + 100].toInt() xor 1).toByte() })
        fails(enc.copyOf().also { it[3] = 1 }) // a file header claiming to be text
        assertFails { decryptStream(ByteArrayInputStream(enc), ByteArrayOutputStream(), "nope".toCharArray()) }
    }

    @Test fun `stored names cannot escape a folder`() {
        assertEquals("passwd", safeFileName("../../etc/passwd"))
        assertEquals("archivo", safeFileName("  "))
    }
}
