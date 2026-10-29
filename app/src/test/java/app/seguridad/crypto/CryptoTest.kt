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
            password.fill('\u0000')
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
        val key = keyForHeader(secret, header)
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
}
