package app.seguridad.crypto

import org.bouncycastle.crypto.PBEParametersGenerator
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.security.MessageDigest
import java.util.Random

/**
 * CIFRADO-1 (audit 1.3.0): Argon2id and the HIBP check encode the password to UTF-8 themselves (into an array that is
 * wiped), instead of letting Bouncy Castle or `String` leave a copy on the heap. The bytes must be exactly the ones of
 * before, or a vault, a `.sgbak`, an `SG1:`/`CP1:` text or a `.sgenc`/`.cpenc` file would no longer open.
 */
class PasswordBytesTest {
    private val salt = ByteArray(SALT_BYTES) { (it + 1).toByte() }
    private val quick = KdfParams(memoryKiB = 1024, iterations = 1, parallelism = 1)

    private fun hexOf(b: ByteArray) = hex(b).lowercase()

    /** The texts every check below goes through: ASCII, Latin-1, 2-, 3- and 4-byte UTF-8, U+FFFD and noncharacters. */
    private val samples = listOf(
        "", "maestra", "contraseña ñ 😀", "éࠀ�𐀀 Ω", "￾￿\u007F\u0000",
        "􏿿", "a".repeat(200), "文".repeat(50),
    )

    @Test fun argon2idGivesTheSameKeysAsBefore() {
        // Computed with the code before CIFRADO-1 (Bouncy Castle 1.86, `generateBytes(char[], byte[])`), salt 01..10,
        // 1 MiB, 1 pass, 1 lane, 32 bytes: these keys open data written by every earlier version.
        val before = mapOf(
            "" to "5f8170a7d9d25b3cb8f4c95eb7598401758fb706047ff739a7f7e3ca0b04db99",
            "maestra" to "7bd734a7d08c3cfdea66e9a25aee396e2a1d72ce99dee776a509cc0c59d4dc38",
            "contraseña ñ 😀" to "b84344ae47c152db060a1a78f02a1e25adfd8b0648bb08f5e9eac91723472cdf",
            "éࠀ�𐀀 Ω" to "14eb6df9428b82cc648f7899c4ee3c8189a02ae25c6085e6c36659ff0d999ecf",
        )
        for ((pw, key) in before) assertEquals("pw=<$pw>", key, hexOf(argon2id(pw.toCharArray(), salt, quick)))
    }

    @Test fun argon2idEncodesThePasswordAsBouncyCastleDid() {
        // Same bytes as Bouncy Castle's own conversion (PasswordConverter.UTF8), so the same key, for any text.
        for (pw in samples + randomTexts(40)) {
            val chars = pw.toCharArray()
            assertArrayEquals("pw=<$pw>", PBEParametersGenerator.PKCS5PasswordToUTF8Bytes(chars), passwordUtf8(chars))
        }
        // Every BMP character that is not a surrogate, one by one.
        for (c in 0..0xFFFF) {
            if (c in 0xD800..0xDFFF) continue
            val chars = charArrayOf(c.toChar())
            assertArrayEquals("c=${c.toString(16)}", PBEParametersGenerator.PKCS5PasswordToUTF8Bytes(chars), passwordUtf8(chars))
        }
    }

    @Test fun aPasswordWithALoneSurrogateIsNotTextAndNeverGivesAKey() {
        // Before CIFRADO-1 Bouncy Castle threw IllegalStateException for these, so no data can exist under one; now it
        // is a CryptoFailure with its message, which every screen shows (Cifrar texto only caught CryptoFailure).
        for (pw in listOf("lone\uD800x", "x\uDC00", "x\uD800", "\uDC00\uD800", "\uD83D")) {
            assertThrows(IllegalStateException::class.java) { PBEParametersGenerator.PKCS5PasswordToUTF8Bytes(pw.toCharArray()) }
            val e = assertThrows(CryptoFailure::class.java) { argon2id(pw.toCharArray(), salt, quick) }
            assertEquals(PASSWORD_NOT_TEXT, e.message)
            assertThrows(CryptoFailure::class.java) { passwordUtf8(pw.toCharArray()) }
        }
    }

    @Test fun argon2idLeavesThePasswordCharsAsTheyWere() {
        // The caller wipes its CharArray; argon2id must not change it (or a retry would derive another key).
        val chars = "contraseña".toCharArray()
        argon2id(chars, salt, quick)
        assertEquals("contraseña", String(chars))
    }

    @Test fun theHibpHashIsTheSameAsBefore() {
        // Before CIFRADO-1: SHA-1 of String(password).toByteArray(UTF_8), where a lone surrogate is «?».
        val inputs = samples + randomTexts(40) + listOf("a\uD800", "\uDC00b", "\uD800𐀀", "x\uDBFFy", "password")
        for (pw in inputs) {
            val old = hex(MessageDigest.getInstance("SHA-1").digest(pw.toByteArray(Charsets.UTF_8)))
            assertEquals("pw=<$pw>", old, String(sha1HexChars(pw.toCharArray())))
        }
        assertEquals("5BAA61E4C9B93F3F0682250B6CF8331B7EE68FD8", String(sha1HexChars("password".toCharArray())))
    }

    @Test fun theReplacingEncoderIsStringToByteArrayForEveryCharacter() {
        // Every UTF-16 unit alone (surrogates included), and random texts with loose surrogates.
        for (c in 0..0xFFFF) {
            val s = c.toChar().toString()
            assertArrayEquals("c=${c.toString(16)}", s.toByteArray(Charsets.UTF_8), utf8Replacing(s.toCharArray()))
        }
        for (s in randomTexts(200, withLooseSurrogates = true)) assertArrayEquals(s.toByteArray(Charsets.UTF_8), utf8Replacing(s.toCharArray()))
    }

    @Test fun theEncodedArrayIsExactlyTheBytes() {
        // An exact array: nothing of the password beyond its end, and Argon2id hashes the whole array.
        assertEquals(0, passwordUtf8(CharArray(0)).size)
        assertEquals(4 + 2 + 3 + 1, passwordUtf8("😀ñ文a".toCharArray()).size)
        try {
            passwordUtf8("\uD83D".toCharArray())
            fail("a lone surrogate must throw")
        } catch (e: CryptoFailure) {
            assertTrue(e.message!!.isNotBlank())
        }
    }

    private fun randomTexts(n: Int, withLooseSurrogates: Boolean = false): List<String> {
        val r = Random(1_303)
        val pool = intArrayOf(0x41, 0x7A, 0x00F1, 0x07FF, 0x0800, 0xFFFD, 0x1F600, 0x10FFFF, 0x10000, 0x20, 0x00)
        return List(n) {
            val sb = StringBuilder()
            repeat(r.nextInt(12)) {
                if (withLooseSurrogates && r.nextInt(4) == 0) sb.append((0xD800 + r.nextInt(0x800)).toChar())
                else sb.appendCodePoint(pool[r.nextInt(pool.size)])
            }
            sb.toString()
        }
    }
}
