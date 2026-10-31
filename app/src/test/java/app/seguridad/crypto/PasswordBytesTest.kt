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
 * before, or a vault, a `.sgbak`, an `SG1:`/`CP1:` text or a `.sgenc`/`.cpenc` file would no longer open. For a password
 * that is not valid text, what opens existing data follows Bouncy Castle 1.80 (Seguridad 1.0.x and 1.1.x), and nothing
 * new is protected with it (review F5, M1).
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
        // 1 MiB, 1 pass, 1 lane, 32 bytes. Bouncy Castle 1.80 (Seguridad 1.0.x and 1.1.x) gives the same bytes for all
        // valid text (review F5, checked exhaustively), so these keys open data written by every earlier version.
        val before = mapOf(
            "" to "5f8170a7d9d25b3cb8f4c95eb7598401758fb706047ff739a7f7e3ca0b04db99",
            "maestra" to "7bd734a7d08c3cfdea66e9a25aee396e2a1d72ce99dee776a509cc0c59d4dc38",
            "contraseña ñ 😀" to "b84344ae47c152db060a1a78f02a1e25adfd8b0648bb08f5e9eac91723472cdf",
            "éࠀ�𐀀 Ω" to "14eb6df9428b82cc648f7899c4ee3c8189a02ae25c6085e6c36659ff0d999ecf",
        )
        for ((pw, key) in before) {
            for (use in PasswordUse.entries) assertEquals("pw=<$pw> $use", key, hexOf(argon2id(pw.toCharArray(), salt, quick, use = use)))
        }
    }

    @Test fun argon2idEncodesThePasswordAsBouncyCastleDid() {
        // Same bytes as Bouncy Castle's own conversion (PasswordConverter.UTF8), so the same key, for any text.
        for (pw in samples + randomTexts(40)) {
            val chars = pw.toCharArray()
            assertArrayEquals("pw=<$pw>", PBEParametersGenerator.PKCS5PasswordToUTF8Bytes(chars), passwordUtf8(chars, PasswordUse.NEW))
        }
        // Every BMP character that is not a surrogate, one by one.
        for (c in 0..0xFFFF) {
            if (c in 0xD800..0xDFFF) continue
            val chars = charArrayOf(c.toChar())
            assertArrayEquals("c=${c.toString(16)}", PBEParametersGenerator.PKCS5PasswordToUTF8Bytes(chars), passwordUtf8(chars, PasswordUse.NEW))
        }
    }

    @Test fun aNewPasswordThatIsNotTextIsRefused() {
        // Nothing new is protected with a password that is not valid UTF-16 (as Bouncy Castle 1.82+ refuses it).
        for (pw in listOf("lone\uD800x", "\uD83D\uD83Dz", "x\uDC00", "x\uD800", "\uDC00\uD800", "\uD83D")) {
            assertThrows(IllegalStateException::class.java) { PBEParametersGenerator.PKCS5PasswordToUTF8Bytes(pw.toCharArray()) }
            val e = assertThrows(CryptoFailure::class.java) { argon2id(pw.toCharArray(), salt, quick, use = PasswordUse.NEW) }
            assertEquals(PASSWORD_NOT_TEXT, e.message)
            assertThrows(CryptoFailure::class.java) { passwordUtf8(pw.toCharArray(), PasswordUse.NEW) }
        }
    }

    @Test fun anExistingPasswordIsEncodedAsBouncyCastle180Did() {
        // Review F5, M1. Seguridad 1.0.x and 1.1.x used Bouncy Castle 1.80 (jar SHA-256 e8ad209f…3249, the one in this
        // repo's verification history), which took a high surrogate and WHATEVER unit followed as a pair. Its bytes and
        // keys, computed with that jar (`PKCS5PasswordToUTF8Bytes` and `generateBytes(char[])`, salt 01..10, 1 MiB, 1 pass):
        val utf8 = mapOf(
            "lone\uD800x" to "6c6f6e65f09081b8",
            "\uD83D\uD83Dz" to "f09f90bd7a",
            "\uD800x" to "f09081b8",
            "\uDBFF\u0000" to "f48fb080",
            "a\uD83D\uFFFFb" to "61f09f9fbf62",
            "\uD800\uDC00" to "f0908080",
        )
        for ((pw, bytes) in utf8) assertEquals("pw=<$pw>", bytes, hexOf(passwordUtf8(pw.toCharArray(), PasswordUse.EXISTING)))
        val keys = mapOf(
            "lone\uD800x" to "2643e1004ac4d6f1e6e0c83ec986341aad61e13b9294b9e60d1a85a39ed785dd",
            "\uD83D\uD83Dz" to "af222506aba757b45ddc2e3a43ff742c3733630e7d11aa84b3387c7cce84d3ce",
            "\uD800x" to "0d1af034beee3f09003647697434902703e27ea729dd08832f5e480e1170d503",
            "\uDBFF\u0000" to "348551dccfbc25db4a4f7631793d39eac372582789a42cdf1482d255d67aedce",
            "a\uD83D\uFFFFb" to "93059c323659dec967c871740df5a5b12556f462a9f211299e31a85bb0a27b48",
        )
        for ((pw, key) in keys) assertEquals("pw=<$pw>", key, hexOf(argon2id(pw.toCharArray(), salt, quick, use = PasswordUse.EXISTING)))
        // What every version refused (a lone low surrogate, a high one at the end) is still refused.
        for (pw in listOf("x\uDC00", "x\uD800", "\uDC00\uD800", "\uD83D")) {
            val e = assertThrows(CryptoFailure::class.java) { argon2id(pw.toCharArray(), salt, quick, use = PasswordUse.EXISTING) }
            assertEquals(PASSWORD_NOT_TEXT, e.message)
        }
    }

    @Test fun forValidTextBothUsesGiveTheSameBytes() {
        // Every BMP character and a sample of pairs in context: NEW and EXISTING only differ for what is not text.
        for (c in 0..0xFFFF) {
            if (c in 0xD800..0xDFFF) continue
            val chars = charArrayOf('a', c.toChar(), 'z')
            assertArrayEquals(passwordUtf8(chars, PasswordUse.NEW), passwordUtf8(chars, PasswordUse.EXISTING))
        }
        for (hi in 0xD800..0xDBFF step 7) for (lo in 0xDC00..0xDFFF step 13) {
            val chars = charArrayOf('a', hi.toChar(), lo.toChar(), '\u0000')
            val bc = PBEParametersGenerator.PKCS5PasswordToUTF8Bytes(chars)
            assertArrayEquals(bc, passwordUtf8(chars, PasswordUse.NEW))
            assertArrayEquals(bc, passwordUtf8(chars, PasswordUse.EXISTING))
        }
    }

    @Test fun dataMadeUnderSuchAPasswordStillOpensAndNothingNewIsMade() {
        // SG1: and .sgenc written as 1.1.x did (the header, then the key Bouncy Castle 1.80 derived): they open.
        val pw = "lone\uD800x"
        val secret = Secret.Password(pw.toCharArray())
        val header = newHeader(Header.KIND_TEXT, Aead.AES_256_GCM, secret, quick, secureRandom)
        val aad = header.bytes()
        val key = keyForHeader(secret, header, PasswordUse.EXISTING)
        val sealed = aead(Aead.AES_256_GCM, true, key, header.nonce, aad, "hola".toByteArray(), header.dialect)
        val token = Dialect.SEGURIDAD.textPrefix + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(aad + sealed)
        assertEquals("hola", decryptText(token, pw.toCharArray()))
        val fileHeader = newHeader(Header.KIND_FILE, Aead.AES_256_GCM, secret, quick, secureRandom)
        val fileAad = fileHeader.bytes()
        val fileKey = keyForHeader(secret, fileHeader, PasswordUse.EXISTING)
        val file = java.io.ByteArrayOutputStream().apply {
            write(fileAad)
            val name = aead(Aead.AES_256_GCM, true, fileKey, streamNonce(fileHeader.nonce, 0, false), fileAad, "a.txt".toByteArray(), fileHeader.dialect)
            write(byteArrayOf((name.size shr 8).toByte(), name.size.toByte()))
            write(name)
            write(aead(Aead.AES_256_GCM, true, fileKey, streamNonce(fileHeader.nonce, 1, true), fileAad, "datos".toByteArray(), fileHeader.dialect))
        }.toByteArray()
        val plain = java.io.ByteArrayOutputStream()
        assertEquals("a.txt", decryptStream(java.io.ByteArrayInputStream(file), plain, pw.toCharArray()))
        assertEquals("datos", plain.toString(Charsets.UTF_8.name()))
        // Encrypting something new with it: refused, with the message.
        assertEquals(PASSWORD_NOT_TEXT, assertThrows(CryptoFailure::class.java) { encryptText("hola", pw.toCharArray(), kdf = quick) }.message)
        assertEquals(
            PASSWORD_NOT_TEXT,
            assertThrows(CryptoFailure::class.java) {
                encryptStream(java.io.ByteArrayInputStream(ByteArray(3)), java.io.ByteArrayOutputStream(), "a", pw.toCharArray(), kdf = quick)
            }.message,
        )
    }

    @Test fun aVaultUnderSuchAMasterStillOpensButNoneIsCreatedOrChangedToOne() {
        // A vault wrapped under the 1.1.x key of «lone\uD800x» (afterMasterUnlock wraps again under the master that opened
        // it, with the EXISTING encoding): it unlocks, and keeps unlocking after the upgrade wrap.
        val vault = app.seguridad.vault.VaultCrypto
        val legacy = "lone\uD800x"
        val (file, dek) = vault.create("maestra-valida".toCharArray(), 1L, quick)
        val stronger = KdfParams(memoryKiB = 1024, iterations = 2, parallelism = 1) // So afterMasterUnlock wraps again.
        val underLegacy = vault.afterMasterUnlock(file, dek, legacy.toCharArray(), 2L, stronger)
        val opened = vault.unlock(underLegacy, legacy.toCharArray())
        assertArrayEquals(dek, opened)
        val again = vault.afterMasterUnlock(underLegacy, opened, legacy.toCharArray(), 3L, KdfParams(memoryKiB = 1024, iterations = 3, parallelism = 1))
        assertArrayEquals(dek, vault.unlock(again, legacy.toCharArray()))
        // But no new vault, and no new master, is made with it.
        assertEquals(PASSWORD_NOT_TEXT, assertThrows(CryptoFailure::class.java) { vault.create(legacy.toCharArray(), 1L, quick) }.message)
        assertEquals(
            PASSWORD_NOT_TEXT,
            assertThrows(CryptoFailure::class.java) { vault.changeMaster(underLegacy, dek, "x\uD800y".toCharArray(), 4L, quick) }.message,
        )
        // Changing the master away from it works.
        val (changed, newDek) = vault.changeMaster(underLegacy, dek, "nueva-valida".toCharArray(), 4L, quick)
        assertArrayEquals(newDek, vault.unlock(changed, "nueva-valida".toCharArray()))
    }

    @Test fun theMessageForANewPasswordFailureIsTheRightOne() {
        assertEquals(PASSWORD_NOT_TEXT, newPasswordFailureMessage(CryptoFailure(PASSWORD_NOT_TEXT), "x"))
        assertEquals(KDF_OUT_OF_MEMORY, newPasswordFailureMessage(CryptoFailure(KDF_OUT_OF_MEMORY), "x"))
        assertEquals("x", newPasswordFailureMessage(CryptoFailure(), "x"))
        assertEquals("x", newPasswordFailureMessage(IllegalStateException("ruta /data/…"), "x"))
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
        assertEquals(0, passwordUtf8(CharArray(0), PasswordUse.NEW).size)
        assertEquals(4 + 2 + 3 + 1, passwordUtf8("\uD83D\uDE00\u00F1\u6587a".toCharArray(), PasswordUse.NEW).size)
        assertEquals(4, passwordUtf8("\uD83Dz".toCharArray(), PasswordUse.EXISTING).size) // The «z» is taken as the pair, as in 1.80.
        try {
            passwordUtf8("\uD83D".toCharArray(), PasswordUse.EXISTING)
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
