package app.seguridad.tools.hashlab

import java.io.ByteArrayInputStream
import java.util.concurrent.CancellationException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class HashLabTest {
    private val abc = "abc".toByteArray()

    private fun h(algo: HashAlgo, data: ByteArray) = app.seguridad.crypto.hexLower(digest(algo, data))

    // FIPS 180-4, Appendix B.1 (SHA-256 of «abc»).
    @Test fun sha256Abc() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", h(HashAlgo.SHA256, abc))
    }

    // FIPS 180-4, Appendix C.1 (SHA-512 of «abc»).
    @Test fun sha512Abc() {
        assertEquals(
            "ddaf35a193617abacc417349ae20413112e6fa4e89a97ea20a9eeee64b55d39a" +
                "2192992a274fc1a836ba3c23a3feebbd454d4423643ce80e2a9ac94fa54ca49f",
            h(HashAlgo.SHA512, abc),
        )
    }

    // FIPS 202 (NIST's SHA3-256 example for «abc»).
    @Test fun sha3_256Abc() {
        assertEquals("3a985da74fe225b2045c172d6bd390bd855f086e3e9d525b46bfe24511431532", h(HashAlgo.SHA3_256, abc))
    }

    // RFC 7693, Appendix A (BLAKE2b-512 of «abc»).
    @Test fun blake2b512Abc() {
        assertEquals(
            "ba80a53f981c4d0d6a2797b69f12f6e94c212f14685ac4b74b12bb6fdbffa2d1" +
                "7d87c5392aab792dc252d5de4533cc9518d38aa8dbf1925ab92386edd4009923",
            h(HashAlgo.BLAKE2B_512, abc),
        )
    }

    // The BLAKE3 team's test_vectors.json, case input_len = 0, first 32 bytes of the hash.
    @Test fun blake3Empty() {
        assertEquals("af1349b9f5f9a1a6a0404dea36dcc9499bcb25c9adc112b7cc9a93cae41f3262", h(HashAlgo.BLAKE3_256, ByteArray(0)))
    }

    // The BLAKE3 team's test_vectors.json (input byte i is i % 251), inputs of 1025 and 102400 bytes: they cross the
    // 1024-byte chunks, so the chaining and the tree merge are pinned against the reference, not only the empty input.
    private fun pattern(n: Int) = ByteArray(n) { (it % 251).toByte() }

    @Test fun blake3MultiChunkOfficialVectors() {
        assertEquals("d00278ae47eb27b34faecf67b4fe263f82d5412916c1ffd97c8cb7fb814b8444", h(HashAlgo.BLAKE3_256, pattern(1025)))
        assertEquals("bc3e3d41a1146b069abffad3c0d44860cf664390afce4d9661f7902e7943e085", h(HashAlgo.BLAKE3_256, pattern(102400)))
    }

    // RFC 4231, test case 1: key = 0x0b × 20, data = «Hi There».
    @Test fun hmacRfc4231Case1() {
        val mac = hmacSha256(ByteArray(20) { 0x0b }, "Hi There".toByteArray())
        assertEquals("b0344c61d8db38535ca8afceaf0bf12b881dc200c9833da726e9376c2e32cff7", app.seguridad.crypto.hex(mac).lowercase())
    }

    // RFC 4231, test case 2: key = «Jefe», data = «what do ya want for nothing?».
    @Test fun hmacRfc4231Case2() {
        val mac = hmacSha256("Jefe".toByteArray(), "what do ya want for nothing?".toByteArray())
        assertEquals("5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843", app.seguridad.crypto.hex(mac).lowercase())
    }

    @Test fun hmacAcceptsAnEmptyKey() {
        assertEquals(32, hmacSha256(ByteArray(0), abc).size)
    }

    @Test fun digestSizesMatchTheNames() {
        for (a in HashAlgo.entries) {
            val n = digest(a, abc).size
            assertEquals(a.label, if (a == HashAlgo.BLAKE2B_512 || a == HashAlgo.SHA512) 64 else 32, n)
        }
    }

    @Test fun streamingEqualsInMemory() {
        val big = ByteArray(10 * 1024 * 1024) { (it * 31 + (it shr 8)).toByte() }
        for (a in HashAlgo.entries) {
            assertArrayEquals(a.label, digest(a, big), digest(a, ByteArrayInputStream(big)))
        }
    }

    @Test fun streamingKeepsEveryBytePartialReadsIncluded() {
        // A stream that gives one or three bytes per read, as a slow provider may.
        val data = ByteArray(1000) { it.toByte() }
        val slow = object : java.io.InputStream() {
            var p = 0
            override fun read(): Int = if (p < data.size) data[p++].toInt() and 0xFF else -1
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (p >= data.size) return -1
                val n = minOf(len, 3, data.size - p)
                System.arraycopy(data, p, b, off, n)
                p += n
                return n
            }
        }
        assertArrayEquals(digest(HashAlgo.SHA256, data), digest(HashAlgo.SHA256, slow))
    }

    // Carried over from the «Huella de archivo» tests: the checkpoint is called before each read and a throw stops the reading.
    @Test fun checkCanCancel() {
        var calls = 0
        val big = ByteArrayInputStream(ByteArray(4 * 1024 * 1024))
        for (a in HashAlgo.entries) {
            calls = 0
            try {
                digest(a, big) { if (++calls == 3) throw CancellationException() }
                fail("not stopped")
            } catch (e: CancellationException) {
                assertEquals(3, calls)
            }
        }
        calls = 0
        val ok = digest(HashAlgo.SHA256, ByteArrayInputStream(abc)) { calls++ }
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", app.seguridad.crypto.hex(ok).lowercase())
        assertEquals(2, calls)
    }

    @Test fun expectedParsesHexWithColonsAndSpaces() {
        val want = digest(HashAlgo.SHA256, abc)
        val plain = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
        assertArrayEquals(want, parseExpected(plain))
        assertArrayEquals(want, parseExpected(plain.uppercase()))
        assertArrayEquals(want, parseExpected("  " + plain.chunked(2).joinToString(":") + "\n"))
        assertArrayEquals(want, parseExpected(plain.chunked(8).joinToString(" ")))
        assertArrayEquals(want, parseExpected("BA:78:16:BF:8F:01:CF:EA:41:41:40:DE:5D:AE:22:23:B0:03:61:A3:96:17:7A:9C:B4:10:FF:61:F2:00:15:AD"))
    }

    @Test fun expectedParsesBase64() {
        val want = digest(HashAlgo.SHA256, abc)
        val b64 = java.util.Base64.getEncoder().encodeToString(want)
        assertEquals(44, b64.length)
        assertArrayEquals(want, parseExpected(b64))
        assertArrayEquals(want, parseExpected(b64.trimEnd('=')))
        val big = digest(HashAlgo.SHA512, abc)
        assertArrayEquals(big, parseExpected(java.util.Base64.getUrlEncoder().encodeToString(big)))
    }

    @Test fun expectedRejectsWrongLength() {
        assertNull(parseExpected(""))
        assertNull(parseExpected("   "))
        assertNull(parseExpected("ba7816bf"))
        assertNull(parseExpected("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015")) // 63 digits
        assertNull(parseExpected("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad00")) // 33 bytes
    }

    @Test fun expectedNeverThrowsOnBadInput() {
        for (bad in listOf("zz".repeat(32), "ñ".repeat(64), "\u0000", "=".repeat(44), "a".repeat(100_000), "😀".repeat(40))) {
            assertNull(bad.take(12), parseExpected(bad))
        }
    }

    @Test fun decodersReturnNullOnBadInputAndNeverThrow() {
        assertNull(hexDecode("abc")) // odd
        assertNull(hexDecode("zz"))
        assertNull(hexDecode("0x00"))
        assertNull(hexDecode("é1"))
        assertArrayEquals(byteArrayOf(0x0a, 0xFF.toByte()), hexDecode("0A:ff"))
        assertArrayEquals(byteArrayOf(0x0a, 0xFF.toByte()), hexDecode(" 0a ff\n"))
        assertArrayEquals(ByteArray(0), hexDecode(""))
        assertNull(base64Decode("a")) // one symbol leftover
        assertNull(base64Decode("ab!d"))
        assertNull(base64Decode("ab=d"))
        assertNull(base64Decode("é"))
        assertArrayEquals("hola".toByteArray(), base64Decode("aG9sYQ=="))
        assertArrayEquals("hola".toByteArray(), base64Decode("aG9sYQ"))
        assertArrayEquals("hola".toByteArray(), base64Decode(" aG9s\nYQ== "))
        assertArrayEquals(byteArrayOf(-5, -1), base64Decode("-_8")) // URL-safe alphabet
        assertArrayEquals(byteArrayOf(-5, -1), base64Decode("+/8="))
        assertArrayEquals(ByteArray(0), base64Decode(""))
    }

    @Test fun decodersRefuseAHugeInput() {
        val huge = "A".repeat(MAX_CODEC_CHARS + 4)
        assertNull(hexDecode(huge))
        assertNull(base64Decode(huge))
        assertNotNullOfMax()
    }

    private fun assertNotNullOfMax() {
        assertEquals(MAX_CODEC_CHARS / 2, hexDecode("00".repeat(MAX_CODEC_CHARS / 2))!!.size)
    }

    @Test fun sameDigestIsExactAndLengthAware() {
        val a = digest(HashAlgo.SHA256, abc)
        assertTrue(sameDigest(a, a.copyOf()))
        assertFalse(sameDigest(a, digest(HashAlgo.SHA256, "abd".toByteArray())))
        assertFalse(sameDigest(a, a.copyOf(31)))
        assertFalse(sameDigest(ByteArray(0), ByteArray(1)))
    }

    @Test fun textIsHashedAsUtf8() {
        // «ñ» is two bytes in UTF-8, so the digest is that of C3 B1 and not of one Latin-1 byte.
        assertArrayEquals(digest(HashAlgo.SHA256, byteArrayOf(0xC3.toByte(), 0xB1.toByte())), digest(HashAlgo.SHA256, "ñ".toByteArray(Charsets.UTF_8)))
        assertArrayEquals(byteArrayOf(0xC3.toByte(), 0xB1.toByte()), textBytes("ñ"))
    }

    @Test fun codecRoundTripsAndShowsBinaryAsHex() {
        val bytes = byteArrayOf(0, 1, 2, -1, -128)
        assertEquals("000102ff80", app.seguridad.crypto.hexLower(bytes))
        assertEquals("AAEC/4A=", base64Encode(bytes))
        assertEquals("hola ñ", utf8OrNull("hola ñ".toByteArray()))
        assertNull(utf8OrNull(byteArrayOf(0xC3.toByte()))) // truncated sequence
        assertNull(utf8OrNull(bytes))
    }

    @Test fun wrongSizeMessageNamesTheAlgorithmsOfThatSize() {
        assertTrue("SHA-512" in hashLabWrongSize(64, false) && "BLAKE2b-512" in hashLabWrongSize(64, false))
        assertTrue("SHA-256" in hashLabWrongSize(32, false) && "BLAKE3-256" in hashLabWrongSize(32, false))
        assertTrue("HMAC-SHA256" in hashLabWrongSize(64, true))
    }

    @Test fun hexTableEncoderMatchesBothCases() {
        val all = ByteArray(256) { it.toByte() }
        val upper = app.seguridad.crypto.hex(all)
        assertEquals(512, upper.length)
        assertEquals(upper.lowercase(), app.seguridad.crypto.hexLower(all))
        assertEquals("00017F80FF", app.seguridad.crypto.hex(byteArrayOf(0, 1, 0x7F, -128, -1)))
    }

    @Test fun sessionWipesWhatWasTypedAndRefusesToBeRestoredByAnOldRun() {
        val s = HashLabSession()
        s.text = "t"; s.expected = "e"; s.hmacKey = "k"; s.hmacMessage = "m"; s.codecInput = "c"
        val token = s.token()
        s.wipe()
        assertEquals("", s.text + s.expected + s.hmacKey + s.hmacMessage + s.codecInput)
        assertFalse(s.isCurrent(token))
    }

    @Test fun sessionGuardWipesAfterTooLongAway() {
        val s = HashLabSession()
        s.hmacKey = "clave"
        s.guard.stopped(0)
        assertFalse(s.guard.started(30_000, lockAfterMs = 0))
        assertEquals("clave", s.hmacKey)
        s.guard.stopped(100_000)
        assertTrue(s.guard.started(400_000, lockAfterMs = 300_000))
        assertEquals("", s.hmacKey)
    }
}
