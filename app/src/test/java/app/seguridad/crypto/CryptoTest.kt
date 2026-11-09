package app.seguridad.crypto

import org.bouncycastle.crypto.BlockCipher
import org.bouncycastle.crypto.CipherParameters
import org.bouncycastle.crypto.engines.CamelliaEngine
import org.bouncycastle.crypto.engines.Salsa20Engine
import org.bouncycastle.crypto.engines.SerpentEngine
import org.bouncycastle.crypto.engines.TwofishEngine
import org.bouncycastle.crypto.modes.ChaCha20Poly1305
import org.bouncycastle.crypto.modes.GCMBlockCipher
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.KeyParameter
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.lang.reflect.Modifier
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

/**
 * Fixed known answers for the Seguridad dialect, one per algorithm: key 00..1f, nonce a0..ab, 15 bytes of AAD and a 41-byte
 * plaintext (two whole blocks and a partial one, so the tail of GCM and of the tag is exercised).
 *
 * Generated with the code of d0702a3 (before CRY-A03), and then checked without Bouncy Castle's modes: GCM composed by hand
 * (CTR + GHASH) over single-block calls, with OpenSSL's AES and Camellia through `cryptography` and with Bouncy Castle's raw
 * Twofish and Serpent engines (so for those two the check of GCM is independent of Bouncy Castle but the engine is not:
 * the raw Twofish engine has a published vector of its own in SeedCryptoTest, the raw Serpent engine only a round trip
 * there); AES-GCM and ChaCha20-Poly1305 also with the AEAD API of `cryptography`; and the cascade layer by layer, with
 * HKDF-SHA256 subkeys derived by `cryptography`. Since 1.3.1 they are the cascade's known answer in place of the CriptoPanel
 * fixture, which went with CriptoPanel's format; DialectTest pins the whole SG1 and .sgenc formats, the cascade among them,
 * with texts and files written by 1.3.0.
 */
private object Kat {
    val key = hexBytes("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f")
    val nonce = hexBytes("a0a1a2a3a4a5a6a7a8a9aaab")
    val aad = "SG1-cabecera-13".toByteArray(Charsets.US_ASCII)
    val pt = "Texto fijo de Clavero para probar CRY-A03".toByteArray(Charsets.US_ASCII)
    val answers = mapOf(
        Aead.AES_256_GCM to "b27d04592aeb64d6080aa7b7625a83b211da3c62fd97320dee6f06f60dc41760a05604adf60f120d6c977965d639ea80302beb85b65ec7e1bf",
        Aead.CHACHA20_POLY1305 to "58ce002b22c6a4c4ca60d37099dabe97fc28b6cd2c4a10c2c6b9fbc1030da16d1ea055d8f7e4bebaf5564950c8949bb854b52639dacac2fcae",
        Aead.TWOFISH_256_GCM to "7b314e77dfc1332256609ee15d2574c449a3031f230ce02e0679f981695b5a666e971b642035d25b3a55922da184d5d4a2a3df1b1f7ed0e253",
        Aead.SERPENT_256_GCM to "fa2dec2e4fb0409c556aa3949209f9b4f67f99a263c8767f9a057102ff2627fdb21d82c7c501df4d5df45e3ed70993a4a71ed282a2c3f80b8b",
        Aead.CAMELLIA_256_GCM to "b7f6d1473e2a7d21cb57d57d9e99f122094cdfa8ec1184e5daaa32222dfa3f934f3e6816a02e5c6ac6a60f5a92c13ada8d63dce7bece91f86e",
        Aead.CASCADE to "c6421f016baccdf026a5faf937c971750a0226a40557102ed7bf2eb4f5506a85ec474bc9f807de09e9925dac7b20ef2fbe60197ed09356863500ebe0062d5d70acad06ae39c4c81b3972ddee7b377689e221882c86d57eb797",
    )
}

/**
 * CRY-A03: wraps a block cipher and remembers the [KeyParameter] that [GCMBlockCipher] hands to it. That is the object
 * [blockGcm] built from the caller's key, so the test sees the very copy the function used and not one of its own.
 */
private class KeySpy(private val inner: BlockCipher) : BlockCipher by inner {
    val seen = mutableListOf<KeyParameter>()

    override fun init(forEncryption: Boolean, params: CipherParameters) {
        seen += params as KeyParameter
        inner.init(forEncryption, params)
    }
}

private fun assertWiped(what: String, bytes: ByteArray, size: Int = KEY_BYTES) {
    assertEquals("$what: size", size, bytes.size)
    assertTrue("$what is not all zeros: ${hex(bytes)}", bytes.all { it == 0.toByte() })
}

private fun flipLastBit(bytes: ByteArray) = bytes.copyOf().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() }

/** A private field of Bouncy Castle, for the tests that pin what the key wipe relies on; says what to re-check if it is gone. */
private fun bcField(target: Any, owner: Class<*>, name: String): Any {
    val field = try {
        owner.getDeclaredField(name)
    } catch (e: NoSuchFieldException) {
        throw AssertionError("bcprov no longer has ${owner.simpleName}.$name: check again that the key wipe still reaches every copy it keeps")
    }
    field.isAccessible = true
    return requireNotNull(field.get(target)) { "${owner.simpleName}.$name is null" }
}

/** The key as ChaCha keeps it in a [ChaCha20Poly1305]: its own copy, and the 8 little-endian key words of the engine. */
private fun chaChaOwnKey(c: ChaCha20Poly1305) = bcField(c, ChaCha20Poly1305::class.java, "key") as ByteArray

private fun chaChaEngineKeyWords(c: ChaCha20Poly1305): List<Int> {
    val engine = bcField(c, ChaCha20Poly1305::class.java, "chacha20")
    return (bcField(engine, Salsa20Engine::class.java, "engineState") as IntArray).slice(4..11)
}

/** The int fields of the Poly1305 inside a [ChaCha20Poly1305] (r and s): derived from the key and the nonce at every init. */
private fun chaChaPolyState(c: ChaCha20Poly1305): List<Int> {
    val mac = bcField(c, ChaCha20Poly1305::class.java, "poly1305")
    return mac.javaClass.declaredFields.filter { !Modifier.isStatic(it.modifiers) && it.type == Int::class.javaPrimitiveType }
        .sortedBy { it.name }.map { it.isAccessible = true; it.getInt(mac) }
}

private fun littleEndianWords(key: ByteArray) = (0 until 8).map { i ->
    (0 until 4).fold(0) { acc, j -> acc or ((key[i * 4 + j].toInt() and 0xff) shl (8 * j)) }
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
                argon2id(password, salt, KdfParams(memoryKiB = MAX_KDF_MEMORY_KIB + 1, iterations = 1), use = PasswordUse.NEW)
                fail("expected CryptoFailure for excessive memory")
            } catch (e: CryptoFailure) {
                // Rejected before the expensive KDF allocation.
            }
            try {
                argon2id(password, salt, KdfParams(memoryKiB = 1024, iterations = 0), use = PasswordUse.NEW)
                fail("expected CryptoFailure for invalid iterations")
            } catch (e: CryptoFailure) {
                // Rejected before the expensive KDF allocation.
            }
        } finally {
            password.fill('\u0000')
        }
    }

    @Test fun `argon2id public API rejects malformed salt and output length`() {
        try {
            argon2id("pw".toCharArray(), ByteArray(SALT_BYTES - 1), FAST, use = PasswordUse.NEW)
            fail("expected IllegalArgumentException for malformed salt")
        } catch (e: IllegalArgumentException) {
            // expected
        }
        try {
            argon2id("pw".toCharArray(), ByteArray(SALT_BYTES), FAST, 1025, PasswordUse.NEW)
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
        // RLO would show «factura\u202Efdp.exe» as «facturaexe.pdf»; isolates and marks go too.
        assertEquals("facturafdp.exe", safeFileName("factura\u202Efdp.exe"))
        assertEquals("a.txt", safeFileName("\u2066a\u200F.txt\u2069"))
    }

    @Test fun safeFileNameKeepsWholeCodePoints() {
        // A16b: cut by code points (at most 120) and then to at most 255 UTF-8 bytes, never between the two halves of a
        // surrogate pair nor inside a UTF-8 character.
        val emoji = "\uD83D\uDE00" // 😀, outside the BMP: two UTF-16 units, 4 UTF-8 bytes.
        val cut = safeFileName("a".repeat(119) + emoji + "x")
        assertEquals("a".repeat(119) + emoji, cut) // 123 bytes: only the code-point limit applies.
        assertTrue(cut.endsWith(emoji))
        assertEquals(120, cut.codePointCount(0, cut.length))
        // 200 CJK characters (3 bytes each): 120 code points would be 360 bytes, so the byte limit keeps 85 (255 bytes).
        val cjk = safeFileName("文".repeat(200))
        assertEquals("文".repeat(85), cjk)
        assertTrue(cjk.toByteArray(Charsets.UTF_8).size <= MAX_SAVED_NAME_BYTES)
        // 120 emoji (480 bytes): 63 whole ones (252 bytes), ending on a whole emoji.
        for (n in listOf(120, 200)) {
            val emojis = safeFileName(emoji.repeat(n))
            assertEquals(emoji.repeat(63), emojis)
            assertTrue(emojis.toByteArray(Charsets.UTF_8).size <= 255)
            assertTrue(emojis.endsWith(emoji))
            assertTrue(!emojis.last().isHighSurrogate())
        }
        // With a suffix the caller appends («.sgenc», 6 bytes), the name plus the suffix stay within 255 bytes.
        for (name in listOf("文".repeat(200), emoji.repeat(120), "a".repeat(300))) {
            val withSuffix = safeFileName(name, ENCRYPTED_EXTENSION.length) + ENCRYPTED_EXTENSION
            assertTrue(withSuffix, withSuffix.toByteArray(Charsets.UTF_8).size <= 255)
        }
        assertEquals("文".repeat(83), safeFileName("文".repeat(200), 6)) // 249 bytes + 6.
        // Short names do not change.
        assertEquals("foto.jpg", safeFileName("foto.jpg"))
        assertEquals(emoji + ".png", safeFileName(emoji + ".png"))
        assertEquals(255, MAX_SAVED_NAME_BYTES)
        assertEquals(1024, MAX_FILE_NAME_BYTES) // The read limit does not change.
    }

    @Test fun safeFileNameNeverGivesDotsControlsOrLooseEnds() {
        // Never «.» or «..», nor empty.
        for (name in listOf(".", "..", " . ", "...", "/..", "a/.", "\u0085", "\u2028")) assertEquals("name=<$name>", "archivo", safeFileName(name))
        // Dots and spaces at either end go, also the no-break space.
        assertEquals("bashrc", safeFileName("..bashrc"))
        assertEquals("informe.pdf", safeFileName("  informe.pdf. . "))
        assertEquals("x", safeFileName("\u00A0x\u00A0"))
        // DEL, C1 controls and the line and paragraph separators are taken out, like C0.
        assertEquals("ab.txt", safeFileName("a\u007Fb.txt"))
        for (c in 0x80..0x9F) assertEquals("c=${c.toString(16)}", "ab.txt", safeFileName("a${c.toChar()}b.txt"))
        assertEquals("ab.txt", safeFileName("a\u2028b\u2029.txt"))
        assertEquals("ab.txt", safeFileName("a\nb\r.txt"))
        // After the cut, no space or dot is left at the end.
        assertEquals("a".repeat(119), safeFileName("a".repeat(119) + " b"))
        assertEquals("a".repeat(119), safeFileName("a".repeat(119) + ".pdf"))
        assertEquals("文".repeat(84), safeFileName("文".repeat(84) + " " + "文".repeat(50))) // 252 bytes + space, cut at 255.
        // Idempotent.
        for (n in listOf("..bashrc", "文".repeat(200), "a".repeat(119) + " b", " . ")) assertEquals(safeFileName(n), safeFileName(safeFileName(n)))
    }

    /** A `.sgenc` written by hand like [encryptStream] does, but with the raw [name] (no safeFileName): as an older build or another app could. */
    private fun oldStyleFile(name: String, password: String, plain: ByteArray): ByteArray {
        val secret = Secret.Password(password.toCharArray())
        val header = newHeader(Header.KIND_FILE, Aead.AES_256_GCM, secret, FAST, secureRandom)
        val aad = header.bytes()
        val key = keyForHeader(secret, header, PasswordUse.NEW)
        val out = ByteArrayOutputStream()
        out.write(aad)
        val sealedName = aead(Aead.AES_256_GCM, true, key, streamNonce(header.nonce, 0, false), aad, name.toByteArray(Charsets.UTF_8))
        out.write(byteArrayOf((sealedName.size shr 8).toByte(), sealedName.size.toByte()))
        out.write(sealedName)
        out.write(aead(Aead.AES_256_GCM, true, key, streamNonce(header.nonce, 1, true), aad, plain))
        return out.toByteArray()
    }

    @Test fun anOldFileWithANameLongerThan120CodePointsStillDecrypts() {
        val longName = "\u00F1".repeat(300) + ".txt" // 304 code points, 604 bytes.
        val plain = ByteArrayOutputStream()
        // Reading never cuts nor rejects it: the whole name comes back.
        val name = decryptStream(ByteArrayInputStream(oldStyleFile(longName, "clave", "hola".toByteArray())), plain, "clave".toCharArray())
        assertEquals(longName, name)
        assertEquals("hola", plain.toString(Charsets.UTF_8.name()))
        // Only the name to save under is shortened, by whole code points (120 «ñ» are 240 bytes).
        assertEquals("\u00F1".repeat(120), safeFileName(name))
    }

    @Test fun aNameOfExactly1024BytesStillDecryptsAnd1025DoesNot() {
        val name1024 = "\u00E9".repeat(512) // 1024 bytes: the format's limit, MAX_FILE_NAME_BYTES.
        assertEquals(MAX_FILE_NAME_BYTES, name1024.toByteArray(Charsets.UTF_8).size)
        val plain = ByteArrayOutputStream()
        assertEquals(name1024, decryptStream(ByteArrayInputStream(oldStyleFile(name1024, "clave", "x".toByteArray())), plain, "clave".toCharArray()))
        assertEquals("x", plain.toString(Charsets.UTF_8.name()))
        val saved = safeFileName(name1024)
        assertTrue(saved.toByteArray(Charsets.UTF_8).size <= MAX_SAVED_NAME_BYTES)
        // One byte more is not this format.
        assertFails { decryptStream(ByteArrayInputStream(oldStyleFile(name1024 + "a", "clave", "x".toByteArray())), ByteArrayOutputStream(), "clave".toCharArray()) }
    }

    @Test fun safeFileNameDropsZeroWidthAndFormatCharacters() {
        // Invisible characters that could make one name look like another (category Cf), one by one.
        val listed = (0x200B..0x200F) + (0x202A..0x202E) + (0x2060..0x2064) + (0x2066..0x2069) + listOf(0xFEFF, 0x00AD, 0x061C)
        for (cp in listed - 0x200D) {
            val c = String(Character.toChars(cp))
            assertEquals("cp=${cp.toString(16)}", "factura.pdf", safeFileName("fac${c}tura.p${c}df"))
            assertEquals("cp=${cp.toString(16)}", "archivo", safeFileName(c)) // only invisible: the fallback
        }
        // Every other Cf too, also outside the BMP: the tag characters (U+E0001, U+E0020–U+E007F).
        val tagA = String(Character.toChars(0xE0041))
        assertEquals("ab.txt", safeFileName("a${tagA}b${String(Character.toChars(0xE0001))}.txt"))
        // The zero-width joiner stays only inside an emoji sequence (see safeFileNameKeepsTheJoinerOnlyBetweenEmoji).
        val family = "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67" // 👨‍👩‍👧
        assertEquals("$family.png", safeFileName("$family.png"))
        assertEquals("ab.txt", safeFileName("a\u200Db.txt"))
        // Variation selectors (not Cf) and accents stay.
        assertEquals("\u2764\uFE0F.jpg", safeFileName("\u2764\uFE0F.jpg"))
        assertEquals("Ñandú.txt", safeFileName("Ñandú.txt"))
        // Idempotent, and the format characters do not count towards the limits.
        val zw = "\u200B"
        for (n in listOf("fac${zw}tura.pdf", zw, "$family.png", "a".repeat(119) + zw.repeat(50) + "b")) assertEquals(safeFileName(n), safeFileName(safeFileName(n)))
        assertEquals("a".repeat(119) + "b", safeFileName("a".repeat(119) + zw.repeat(50) + "b"))
    }

    @Test fun safeFileNameDropsTheBlankAndInvisibleCharactersOfTheNameList() {
        // CIFRADO-2 (audit 1.3.0): the Hangul fillers, CGJ, the Khmer inherent vowels, U+FE00–FE0E, the supplementary
        // selectors, private use, noncharacters and the plane-14 tags go, as in sanitizeReceivedName.
        // RT-6 (ruling R43): also the Braille blank U+2800 and the Mongolian free variation selectors U+180B–180D, U+180F.
        val hidden = listOf(0x3164, 0x115F, 0x1160, 0xFFA0, 0x034F, 0x17B4, 0x17B5, 0xFE00, 0xFE0E, 0xE0100, 0xE01EF,
            0xE000, 0xF8FF, 0xF0000, 0x10FFFD, 0xFDD0, 0xFDEF, 0xFFFE, 0xFFFF, 0x1FFFE, 0x10FFFF, 0xE0000, 0xE0080, 0x180E, 0x2065,
            0x2800, 0x180B, 0x180C, 0x180D, 0x180F)
        for (cp in hidden) {
            val c = String(Character.toChars(cp))
            assertEquals("cp=${cp.toString(16)}", "factura.pdf", safeFileName("fac${c}tura$c$c.pdf"))
            assertEquals("cp=${cp.toString(16)}", "archivo", safeFileName(c)) // nothing visible left
            assertEquals("cp=${cp.toString(16)}", "archivo", safeFileName("$c $c.$c"))
        }
        assertEquals("factura.pdf", safeFileName("factura\u3164\u3164.pdf"))
        assertEquals("factura.pdf", safeFileName("fac\u3164tura.pdf"))
        assertEquals("archivo", safeFileName("\u3164"))
        // A cut that drops the U+20E3 of a keycap drops its selector too (119 «a» + «1» is the 120-code-point limit).
        assertEquals("a".repeat(119) + "1", safeFileName("a".repeat(119) + "1\uFE0F\u20E3"))
        assertEquals("a".repeat(118) + "1", safeFileName("a".repeat(118) + "1\uFE0F\u20E3"))
        assertEquals("a".repeat(117) + "1\uFE0F\u20E3", safeFileName("a".repeat(117) + "1\uFE0F\u20E3")) // 120: whole.
        // U+FE0F (the emoji presentation selector) stays after an emoji, a symbol or a keycap base…
        assertEquals("\u2764\uFE0F.txt", safeFileName("\u2764\uFE0F.txt"))
        assertEquals("1\uFE0F\u20E3 foto.jpg", safeFileName("1\uFE0F\u20E3 foto.jpg"))
        assertEquals("\u2764\uFE0F\u200D\uD83D\uDD25.png", safeFileName("\u2764\uFE0F\u200D\uD83D\uDD25.png")) // ❤️‍🔥
        // …but not where it shows nothing: after an ASCII letter, a space, a joiner, another selector or at the start.
        assertEquals("factura.pdf", safeFileName("fac\uFE0Ftura.pdf"))
        assertEquals("a b", safeFileName("a \uFE0Fb"))
        assertEquals("\u2764\uFE0F", safeFileName("\u2764\uFE0F\uFE0F"))
        assertEquals("archivo", safeFileName("\uFE0F"))
        assertEquals("x", safeFileName("\uFE0Fx"))
        // Review F5, M4: after 0–9, # or * it only stays in a keycap, with U+20E3 after it.
        assertEquals("factura1.pdf", safeFileName("factura1\uFE0F.pdf"))
        assertEquals("#foto", safeFileName("#\uFE0Ffoto"))
        assertEquals("*.txt", safeFileName("*\uFE0F.txt"))
        assertEquals("#\uFE0F\u20E3 y *\uFE0F\u20E3", safeFileName("#\uFE0F\u20E3 y *\uFE0F\u20E3"))
        assertEquals("1\uFE0F\u20E3", safeFileName("1\uFE0F\uFE0F\u20E3"))
        // Idempotent.
        for (n in listOf(
            "fac\u3164tura.pdf", "\u2764\uFE0F\uFE0F", "a\uFE0F\u200D\uD83D\uDE00", "\u00A0\uFE0F\uD83D\uDE00\u200D\uFE0F",
            "factura1\uFE0F.pdf", "1\uFE0F\uFE0F\u20E3", "a".repeat(118) + "1\uFE0F\u20E3", "a".repeat(119) + "1\uFE0F\u20E3",
        )) {
            assertEquals("name=<$n>", safeFileName(n), safeFileName(safeFileName(n)))
        }
    }

    @Test fun safeFileNameWritesALoneSurrogateAsAQuestionMark() {
        // Not text, and not hidden either: the «?» that UTF-8 has always written for it inside a file.
        assertEquals("a?b.txt", safeFileName("a\uD800b.txt"))
        assertEquals("a?b", safeFileName("a\uDC00b"))
        assertEquals("?", safeFileName("\uD800"))
    }

    @Test fun nothingTheCardNameListForbidsSurvivesSafeFileName() {
        // The whole of Unicode, one code point at a time between two letters: whatever the fixed list of card names
        // forbids, and every format character but the joiner and the prepended concatenation marks (Cf with a glyph,
        // shown as lists show them since ruling R49), never reaches a file name.
        val prependedMarks = (0x0600..0x0605) + listOf(0x06DD, 0x070F, 0x0890, 0x0891, 0x08E2, 0x110BD, 0x110CD)
        val survivors = mutableListOf<Int>()
        for (cp in 0..0x10FFFF) {
            // A lone surrogate is visible («�») and becomes «?», as UTF-8 writes it (safeFileNameWritesALoneSurrogateAsAQuestionMark).
            if (cp == 0x200D || cp == '/'.code || cp == '\\'.code || cp in 0xD800..0xDFFF) continue
            val listed = app.seguridad.crypto.pk.hasForbiddenNameCharacter(String(Character.toChars(cp)))
            val format = Character.getType(cp) == Character.FORMAT.toInt() && cp !in prependedMarks
            val selector = cp in 0xFE00..0xFE0E || cp in 0xE0100..0xE01EF
            val nonchar = cp in 0xFDD0..0xFDEF || (cp and 0xFFFE) == 0xFFFE
            if ((listed || format || selector || nonchar) && safeFileName("a${String(Character.toChars(cp))}b") != "ab") survivors += cp
        }
        assertTrue("survivors: " + survivors.take(20).joinToString { it.toString(16) }, survivors.isEmpty())
        // And those marks are kept: they draw (R49, RT-13).
        for (cp in prependedMarks) assertEquals(cp.toString(16), "a${String(Character.toChars(cp))}b", safeFileName("a${String(Character.toChars(cp))}b"))
    }

    @Test fun safeFileNameKeepsTheJoinerOnlyBetweenEmoji() {
        val zwj = "\u200D"
        val man = "\uD83D\uDC68"
        val woman = "\uD83D\uDC69"
        val girl = "\uD83D\uDC67"
        val family = "$man$zwj$woman$zwj$girl" // 👨‍👩‍👧
        // The emoji sequence is kept whole.
        assertEquals("$family.png", safeFileName("$family.png"))
        assertEquals("foto $family", safeFileName("foto $family"))
        // Next to ASCII, at either end, next to a space or next to another joiner: it goes.
        assertEquals("factura.pdf", safeFileName("fac${zwj}tura.pdf"))
        assertEquals("$man.png", safeFileName("$zwj$man$zwj.png"))
        assertEquals("$man $woman", safeFileName("$man$zwj $woman"))
        assertEquals("$man$woman", safeFileName("$man$zwj$zwj$woman"))
        assertEquals("ñandú", safeFileName("${zwj}ñandú$zwj"))
        // Nothing visible left: «archivo».
        for (n in listOf(zwj, zwj.repeat(5), "$zwj . $zwj", "..$zwj..", " $zwj ")) assertEquals("name=<$n>", "archivo", safeFileName(n))
        // A joiner the byte cut leaves at the end goes: 82 «文» (246 bytes) + 😀 (250) + joiner (253) + 😀 (257).
        val smile = "\uD83D\uDE00"
        assertEquals("文".repeat(82) + smile, safeFileName("文".repeat(82) + smile + zwj + smile))
        // And one the code-point cut leaves at the end: 118 «a» + «é» + joiner is 120 code points.
        assertEquals("a".repeat(118) + "é", safeFileName("a".repeat(118) + "é" + zwj + "é"))
        // A trim that leaves a joiner at an end: it goes too.
        assertEquals(man, safeFileName("$man$zwj\u00A0"))
        // Idempotent.
        for (n in listOf("$family.png", "fac${zwj}tura", zwj, "文".repeat(82) + smile + zwj + smile, "$man$zwj\u00A0", "$man$zwj$zwj$woman")) {
            assertEquals("name=<$n>", safeFileName(n), safeFileName(safeFileName(n)))
        }
    }

    // --- CRY-A03: the key copies that Bouncy Castle's KeyParameter keeps ---

    @Test fun `bouncy castle KeyParameter keeps its own copy of the key and getKey returns that same array`() {
        val key = ByteArray(KEY_BYTES) { (it + 1).toByte() }
        val kp = KeyParameter(key)
        assertNotSame("the copy must not be the caller's array, or wiping it would wipe the caller's key", key, kp.key)
        assertSame("getKey() must not hand out a clone each time, or wiping it would wipe nothing", kp.key, kp.key)
        assertArrayEquals(key, kp.key)
        // Wiping what getKey() returns leaves the caller's array alone.
        kp.key.fill(0)
        assertWiped("KeyParameter.key", kp.key)
        assertArrayEquals(ByteArray(KEY_BYTES) { (it + 1).toByte() }, key)
    }

    @Test fun `GCMBlockCipher keeps the same array as the KeyParameter as its last key`() {
        val kp = KeyParameter(ByteArray(KEY_BYTES) { (it + 1).toByte() })
        val gcm = GCMBlockCipher.newInstance(TwofishEngine())
        gcm.init(true, AEADParameters(kp, TAG_BYTES * 8, ByteArray(NONCE_BYTES), ByteArray(0)))
        val lastKey = try {
            GCMBlockCipher::class.java.getDeclaredField("lastKey").apply { isAccessible = true }.get(gcm) as ByteArray
        } catch (e: NoSuchFieldException) {
            fail("bcprov no longer has GCMBlockCipher.lastKey: check again that wiping KeyParameter.key still reaches every copy GCM keeps")
            return
        }
        assertSame("lastKey must be the array of the KeyParameter, so that one fill(0) wipes both", kp.key, lastKey)
    }

    @Test fun `fixed known answers for every algorithm, written with the Seguridad dialect`() {
        assertEquals(Aead.entries.toSet(), Kat.answers.keys)
        for ((alg, expected) in Kat.answers) {
            val ct = aead(alg, true, Kat.key, Kat.nonce, Kat.aad, Kat.pt)
            assertEquals("$alg", expected, hex(ct).lowercase())
            assertArrayEquals("$alg round trip", Kat.pt, aead(alg, false, Kat.key, Kat.nonce, Kat.aad, ct))
        }
    }

    @Test fun `GCM over Twofish, Serpent and Camellia wipes the KeyParameter it built, on encrypting and on decrypting`() {
        val keyBefore = Kat.key.copyOf()
        for ((alg, engine) in listOf<Pair<Aead, () -> BlockCipher>>(
            Aead.TWOFISH_256_GCM to { TwofishEngine() },
            Aead.SERPENT_256_GCM to { SerpentEngine() },
            Aead.CAMELLIA_256_GCM to { CamelliaEngine() },
        )) {
            val encSpy = KeySpy(engine())
            val ct = blockGcm(encSpy, true, Kat.key, Kat.nonce, Kat.aad, Kat.pt)
            assertEquals("$alg: the bytes", Kat.answers.getValue(alg), hex(ct).lowercase())
            assertTrue("$alg: the engine saw no KeyParameter", encSpy.seen.isNotEmpty())
            for (kp in encSpy.seen) {
                assertNotSame("$alg: the KeyParameter must hold a copy", Kat.key, kp.key)
                assertWiped("$alg encrypt", kp.key)
            }
            assertArrayEquals("$alg: the caller's key is the caller's to wipe", keyBefore, Kat.key)

            val decSpy = KeySpy(engine())
            assertArrayEquals(Kat.pt, blockGcm(decSpy, false, Kat.key, Kat.nonce, Kat.aad, ct))
            assertTrue("$alg: the engine saw no KeyParameter", decSpy.seen.isNotEmpty())
            for (kp in decSpy.seen) assertWiped("$alg decrypt", kp.key)
            assertArrayEquals(keyBefore, Kat.key)
        }
    }

    @Test fun `GCM over Twofish, Serpent and Camellia wipes the KeyParameter also when the tag fails`() {
        val keyBefore = Kat.key.copyOf()
        for (make in listOf<() -> BlockCipher>({ TwofishEngine() }, { SerpentEngine() }, { CamelliaEngine() })) {
            val ct = blockGcm(make(), true, Kat.key, Kat.nonce, Kat.aad, Kat.pt)
            val name = make().algorithmName

            // A flipped bit in the ciphertext, in the tag, a wrong AAD and a wrong key: all end in CryptoFailure.
            class Attempt(val what: String, val key: ByteArray, val aad: ByteArray, val data: ByteArray)
            val attempts = listOf(
                Attempt("body", Kat.key, Kat.aad, ct.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }),
                Attempt("tag", Kat.key, Kat.aad, flipLastBit(ct)),
                Attempt("aad", Kat.key, "otra".toByteArray(), ct),
                Attempt("key", ByteArray(KEY_BYTES) { 9 }, Kat.aad, ct),
            )
            for (t in attempts) {
                val spy = KeySpy(make())
                assertFails { blockGcm(spy, false, t.key, Kat.nonce, t.aad, t.data) }
                assertTrue("$name/${t.what}: the engine saw no KeyParameter", spy.seen.isNotEmpty())
                for (kp in spy.seen) assertWiped("$name/${t.what}", kp.key)
            }
            assertArrayEquals(keyBefore, Kat.key)
        }
    }

    @Test fun `ChaCha20-Poly1305 wipes the KeyParameter it is given, on encrypting, on decrypting and when the tag fails`() {
        val keyBefore = Kat.key.copyOf()

        val encKp = KeyParameter(Kat.key)
        val ct = chaChaPoly(encKp, true, Kat.nonce, Kat.aad, Kat.pt)
        assertWiped("encrypt", encKp.key)
        assertEquals(Kat.answers.getValue(Aead.CHACHA20_POLY1305), hex(ct).lowercase())

        val decKp = KeyParameter(Kat.key)
        assertArrayEquals(Kat.pt, chaChaPoly(decKp, false, Kat.nonce, Kat.aad, ct))
        assertWiped("decrypt", decKp.key)

        val badTagKp = KeyParameter(Kat.key)
        assertFails { chaChaPoly(badTagKp, false, Kat.nonce, Kat.aad, flipLastBit(ct)) }
        assertWiped("bad tag", badTagKp.key)

        val badKeyKp = KeyParameter(ByteArray(KEY_BYTES) { 9 })
        assertFails { chaChaPoly(badKeyKp, false, Kat.nonce, Kat.aad, ct) }
        assertWiped("wrong key", badKeyKp.key)

        assertArrayEquals("the caller's key is the caller's to wipe", keyBefore, Kat.key)
    }

    @Test fun `ChaCha20-Poly1305 keeps its own copy of the key, the engine its key words and Poly1305 a key of its own, and chaChaPoly overwrites all three`() {
        // The control: after init they do hold the key (or what derives from it), so the reads below mean something.
        val control = ChaCha20Poly1305().apply { init(true, AEADParameters(KeyParameter(Kat.key), TAG_BYTES * 8, Kat.nonce, Kat.aad)) }
        assertArrayEquals("ChaCha20Poly1305.key is its own copy of the key", Kat.key, chaChaOwnKey(control))
        assertEquals("the key words of the engine", littleEndianWords(Kat.key), chaChaEngineKeyWords(control))
        // What init leaves in Poly1305 for an all-zero key is the state to expect once the real one is overwritten.
        val zeroKeyPoly = chaChaPolyState(
            ChaCha20Poly1305().apply { init(false, AEADParameters(KeyParameter(ByteArray(KEY_BYTES)), TAG_BYTES * 8, Kat.nonce, ByteArray(0))) },
        )
        assertNotEquals("Poly1305 holds a key derived from the real one", zeroKeyPoly, chaChaPolyState(control))

        val ct = chaChaPoly(KeyParameter(Kat.key), true, Kat.nonce, Kat.aad, Kat.pt)
        class Run(val what: String, val encrypt: Boolean, val key: ByteArray, val data: ByteArray, val fails: Boolean)
        val runs = listOf(
            Run("encrypt", true, Kat.key, Kat.pt, false),
            Run("decrypt", false, Kat.key, ct, false),
            Run("bad tag", false, Kat.key, flipLastBit(ct), true),
            Run("wrong key", false, ByteArray(KEY_BYTES) { 9 }, ct, true),
        )
        for (r in runs) {
            val cipher = ChaCha20Poly1305()
            val call = { chaChaPoly(KeyParameter(r.key), r.encrypt, Kat.nonce, Kat.aad, r.data, cipher) }
            if (r.fails) assertFails { call() } else call()
            assertWiped("${r.what}: ChaCha20Poly1305.key", chaChaOwnKey(cipher))
            assertEquals("${r.what}: the engine key words", List(8) { 0 }, chaChaEngineKeyWords(cipher))
            assertEquals("${r.what}: the Poly1305 key", zeroKeyPoly, chaChaPolyState(cipher))
        }
    }

    @Test fun `a bad tag leaves no plaintext in the buffer Bouncy Castle decrypted into, and success returns that buffer whole`() {
        class Path(val name: String, val encrypt: (ByteArray) -> ByteArray, val decrypt: (ByteArray, (Int) -> ByteArray) -> ByteArray)
        val paths = listOf<Path>(
            Path("Twofish", { blockGcm(TwofishEngine(), true, Kat.key, Kat.nonce, Kat.aad, it) }, { data, alloc -> blockGcm(TwofishEngine(), false, Kat.key, Kat.nonce, Kat.aad, data, alloc) }),
            Path("Serpent", { blockGcm(SerpentEngine(), true, Kat.key, Kat.nonce, Kat.aad, it) }, { data, alloc -> blockGcm(SerpentEngine(), false, Kat.key, Kat.nonce, Kat.aad, data, alloc) }),
            Path("Camellia", { blockGcm(CamelliaEngine(), true, Kat.key, Kat.nonce, Kat.aad, it) }, { data, alloc -> blockGcm(CamelliaEngine(), false, Kat.key, Kat.nonce, Kat.aad, data, alloc) }),
            Path(
                "ChaCha20-Poly1305",
                { chaChaPoly(KeyParameter(Kat.key), true, Kat.nonce, Kat.aad, it) },
                { data, alloc -> chaChaPoly(KeyParameter(Kat.key), false, Kat.nonce, Kat.aad, data, alloc = alloc) },
            ),
        )
        for (p in paths) {
            val ct = p.encrypt(Kat.pt)

            // Success: the buffer is what comes back, with the plaintext in it, so what the failure case finds empty was wiped.
            val ok = mutableListOf<ByteArray>()
            val back = p.decrypt(ct) { ByteArray(it).also(ok::add) }
            assertEquals("${p.name}: one output buffer", 1, ok.size)
            assertSame("${p.name}: the result is the buffer", ok[0], back)
            assertArrayEquals("${p.name}: success returns the plaintext whole", Kat.pt, back)

            // A bad tag: Bouncy Castle has already decrypted everything into the buffer; it must not be left there.
            val bad = mutableListOf<ByteArray>()
            assertFails { p.decrypt(flipLastBit(ct)) { ByteArray(it).also(bad::add) } }
            assertEquals("${p.name}: one output buffer", 1, bad.size)
            assertWiped("${p.name}: the buffer after a bad tag", bad[0], Kat.pt.size)
        }
    }

    @Test fun `aead reaches the Bouncy Castle paths only through the functions that wipe the key`() {
        // The wipe tests call blockGcm and chaChaPoly; this pins that aead itself goes through them and builds nothing of its own.
        val file = listOf("src/main/java/app/seguridad/crypto/Crypto.kt", "../app/src/main/java/app/seguridad/crypto/Crypto.kt")
            .map(::File).first { it.exists() }
        val code = file.readLines().filterNot { it.trim().let { l -> l.startsWith("*") || l.startsWith("/*") || l.startsWith("//") } }.joinToString("\n")
        for ((alg, engine) in listOf("TWOFISH_256_GCM" to "TwofishEngine", "SERPENT_256_GCM" to "SerpentEngine", "CAMELLIA_256_GCM" to "CamelliaEngine")) {
            assertTrue("$alg", "Aead.$alg -> blockGcm($engine(), encrypt, key, nonce, aad, input)" in code)
        }
        assertTrue("ChaCha", "Aead.CHACHA20_POLY1305 -> chaChaPoly(KeyParameter(key), encrypt, nonce, aad, input)" in code)
        assertEquals("one GCMBlockCipher, the one in blockGcm", 1, Regex("GCMBlockCipher\\.newInstance\\(").findAll(code).count())
        assertEquals("one ChaCha20Poly1305(), the default of chaChaPoly", 1, Regex("ChaCha20Poly1305\\(\\)").findAll(code).count())
        assertEquals("KeyParameter(key) in blockGcm and in the ChaCha branch only", 2, Regex("KeyParameter\\(key\\)").findAll(code).count())
    }

    @Test fun `aead never touches the key array it is given, with any algorithm`() {
        val nonce = ByteArray(NONCE_BYTES) { (it + 1).toByte() }
        val aad = "aad".toByteArray()
        val pt = "mismo texto, mismos bytes".toByteArray()
        for (alg in Aead.entries) {
            val key = ByteArray(KEY_BYTES) { (it * 5 + 1).toByte() }
            val keyBefore = key.copyOf()
            val ct = aead(alg, true, key, nonce, aad, pt)
            assertArrayEquals("$alg encrypt left the key alone", keyBefore, key)
            assertArrayEquals("$alg round trip", pt, aead(alg, false, key, nonce, aad, ct))
            assertArrayEquals("$alg decrypt left the key alone", keyBefore, key)
            // The same key and nonce give the same bytes again: nothing of the key was spent by the first call.
            assertArrayEquals("$alg is deterministic", ct, aead(alg, true, key, nonce, aad, pt))
            val bad = ct.copyOf().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() }
            assertFails { aead(alg, false, key, nonce, aad, bad) }
            assertArrayEquals("$alg after a failed tag, the key is still there", keyBefore, key)
        }
    }
}
