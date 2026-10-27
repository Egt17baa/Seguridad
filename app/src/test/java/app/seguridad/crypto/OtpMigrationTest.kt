package app.seguridad.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.Base64

/** A minimal protobuf writer, independent of the reader under test, matching Google's MigrationPayload. */
private class Pb {
    val out = ByteArrayOutputStream()
    fun varint(v: Long): Pb {
        var x = v
        while (x and 0x7FL.inv() != 0L) {
            out.write(((x and 0x7F) or 0x80).toInt())
            x = x ushr 7
        }
        out.write(x.toInt())
        return this
    }
    fun tag(field: Int, wire: Int) = varint(((field shl 3) or wire).toLong())
    fun int(field: Int, v: Long) = tag(field, 0).varint(v)
    fun bytes(field: Int, b: ByteArray): Pb {
        tag(field, 2).varint(b.size.toLong())
        out.write(b)
        return this
    }
    fun str(field: Int, s: String) = bytes(field, s.toByteArray())
    fun msg(field: Int, m: Pb) = bytes(field, m.out.toByteArray())
    fun raw(vararg b: Int): Pb {
        b.forEach { out.write(it) }
        return this
    }
    fun toByteArray(): ByteArray = out.toByteArray()
}

private fun otp(secret: ByteArray, name: String, issuer: String = "", alg: Long = 1, digits: Long = 1, type: Long = 2, counter: Long? = null) =
    Pb().bytes(1, secret).str(2, name).str(3, issuer).int(4, alg).int(5, digits).int(6, type).apply { counter?.let { int(7, it) } }

private fun uri(payload: ByteArray) =
    "otpauth-migration://offline?data=" + java.net.URLEncoder.encode(Base64.getEncoder().encodeToString(payload), "UTF-8")

private val KEY = "12345678901234567890".toByteArray() // The RFC 6238 test key (20 bytes).

class OtpMigrationTest {
    @Test fun parsesSingleTotp() {
        val payload = Pb().msg(1, otp(KEY, "GitHub:ana@example.com", "GitHub", alg = 2, digits = 2))
            .int(2, 1).int(3, 1).int(4, 0).int(5, 12345)
        val r = parseOtpMigration(uri(payload.toByteArray()))!!
        assertEquals(1, r.imported.size)
        val s = r.imported.single()
        assertEquals("GitHub", s.issuer)
        assertEquals("ana@example.com", s.account)
        assertEquals("SHA256", s.algorithm)
        assertEquals(8, s.digits)
        assertEquals(30, s.period)
        assertArrayEquals(KEY, base32Decode(s.secret))
        assertEquals("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", s.secret)
        assertTrue(r.skipped.isEmpty())
        assertEquals(0, r.batchIndex)
        assertEquals(1, r.batchSize)
    }

    @Test fun namesCannotBeTurnedAroundNorBeHuge() {
        val payload = Pb().msg(1, otp(KEY, "\u2066x:\u202Eana\u200F", "\u202EBanco"))
        val s = parseOtpMigration(uri(payload.toByteArray()))!!.imported.single()
        assertEquals("Banco", s.issuer)
        assertEquals("ana", s.account)
        val long = parseOtpMigration(uri(Pb().msg(1, otp(KEY, "a".repeat(400), "b".repeat(400))).toByteArray()))!!.imported.single()
        assertEquals(OTPAUTH_MAX_NAME_CHARS, long.issuer.length)
        assertEquals(OTPAUTH_MAX_NAME_CHARS, long.account.length)
    }

    @Test fun issuerFromNameWhenFieldEmptyAndDefaults() {
        // Algorithm, digits and type left unset (0): SHA1, 6 digits, TOTP, as Google's own example below.
        val payload = Pb().msg(1, Pb().bytes(1, KEY).str(2, "Banco: ana "))
        val s = parseOtpMigration(uri(payload.toByteArray()))!!.imported.single()
        assertEquals(TotpSecret(base32Encode(KEY), issuer = "Banco", account = "ana"), s)
    }

    /**
     * Example from the README of dim13/otpauth (ISC licence), a Google Authenticator migration decoder:
     * https://github.com/dim13/otpauth#usage — it decodes to
     * otpauth://totp/Example:alice@google.com?issuer=Example&secret=JBSWY3DPEHPK3PXP
     * (JBSWY3DPEHPK3PXP is also the example key of Google's Key-Uri-Format wiki page).
     */
    @Test fun parsesKnownGoogleExample() {
        val r = parseOtpMigration("otpauth-migration://offline?data=CjEKCkhlbGxvId6tvu8SGEV4YW1wbGU6YWxpY2VAZ29vZ2xlLmNvbRoHRXhhbXBsZTAC")!!
        assertEquals(listOf(TotpSecret("JBSWY3DPEHPK3PXP", issuer = "Example", account = "alice@google.com")), r.imported)
        assertTrue(r.skipped.isEmpty())
        assertEquals(1, r.batchSize)
    }

    @Test fun skipsHotpAndMd5WithReason() {
        val payload = Pb()
            .msg(1, otp(KEY, "Hotp:a", "Hotp", type = 1, counter = 7))
            .msg(1, otp(KEY, "Viejo:b", "Viejo", alg = 4))
            .msg(1, otp(ByteArray(9) { 1 }, "Corta:c", "Corta"))
            .msg(1, otp(KEY, "Rara:d", "Rara", alg = 9))
            .msg(1, otp(KEY, "Bien:e", "Bien"))
        val r = parseOtpMigration(uri(payload.toByteArray()))!!
        assertEquals(listOf("Bien"), r.imported.map { it.issuer })
        assertEquals(4, r.skipped.size)
        assertTrue(r.skipped[0], r.skipped[0].contains("Hotp") && r.skipped[0].contains("HOTP"))
        assertTrue(r.skipped[1], r.skipped[1].contains("Viejo") && r.skipped[1].contains("MD5"))
        assertTrue(r.skipped[2], r.skipped[2].contains("Corta") && r.skipped[2].contains("corta"))
        assertTrue(r.skipped[3], r.skipped[3].contains("Rara"))
        // A skipped entry never leaks its secret into the reason.
        assertTrue(r.skipped.none { it.contains(base32Encode(KEY)) })
    }

    @Test fun batchInfo() {
        val payload = Pb().msg(1, otp(KEY, "A:a", "A")).int(2, 1).int(3, 3).int(4, 1).int(5, -42)
        val r = parseOtpMigration(uri(payload.toByteArray()))!!
        assertEquals(1, r.batchIndex)
        assertEquals(3, r.batchSize)
        // Index outside the batch, or an absurd size: malformed.
        assertNull(parseOtpMigration(uri(Pb().msg(1, otp(KEY, "A:a")).int(3, 2).int(4, 2).toByteArray())))
        assertNull(parseOtpMigration(uri(Pb().msg(1, otp(KEY, "A:a")).int(3, -1).toByteArray())))
        assertNull(parseOtpMigration(uri(Pb().msg(1, otp(KEY, "A:a")).int(4, -1).toByteArray())))
    }

    @Test fun skipsUnknownFields() {
        val inner = otp(KEY, "A:a", "A").int(15, 99).bytes(16, ByteArray(20)).tag(17, 5).raw(1, 2, 3, 4).tag(18, 1).raw(1, 2, 3, 4, 5, 6, 7, 8)
        val payload = Pb().int(9, 5).msg(1, inner).str(10, "x")
        assertEquals(1, parseOtpMigration(uri(payload.toByteArray()))!!.imported.size)
    }

    @Test fun rejectsOversizedAndMalformed() {
        val good = Pb().msg(1, otp(KEY, "A:a", "A")).toByteArray()
        assertNotNull(parseOtpMigration(uri(good)))
        // Truncated varint (continuation bit on the last byte), at the top level and inside a parameter.
        assertNull(parseOtpMigration(uri(good + byteArrayOf(0x10, 0x80.toByte()))))
        assertNull(parseOtpMigration(uri(Pb().bytes(1, Pb().raw(0x28, 0xFF).toByteArray()).toByteArray())))
        // Varint longer than 10 bytes.
        assertNull(parseOtpMigration(uri(Pb().tag(2, 0).raw(*IntArray(11) { 0xFF }).raw(0x01).toByteArray())))
        // Length larger than the buffer, and a huge length that must not be allocated.
        assertNull(parseOtpMigration(uri(Pb().tag(1, 2).varint(50).raw(1, 2, 3).toByteArray())))
        assertNull(parseOtpMigration(uri(Pb().tag(1, 2).varint(Int.MAX_VALUE.toLong()).toByteArray())))
        assertNull(parseOtpMigration(uri(Pb().tag(1, 2).varint(-1).toByteArray())))
        // A field over 1 KB.
        assertNull(parseOtpMigration(uri(Pb().msg(1, otp(KEY, "A:" + "a".repeat(1100))).toByteArray())))
        // Group wire types and field number 0.
        assertNull(parseOtpMigration(uri(good + Pb().tag(5, 3).toByteArray())))
        assertNull(parseOtpMigration(uri(good + byteArrayOf(0x00, 0x01))))
        // Fixed-size fields running past the end.
        assertNull(parseOtpMigration(uri(good + Pb().tag(6, 5).raw(1, 2).toByteArray())))
        // Wrong wire type for a known field.
        assertNull(parseOtpMigration(uri(Pb().int(1, 5).toByteArray())))
        // More than 100 parameters; exactly 100 is fine.
        val hundred = Pb().apply { repeat(100) { msg(1, otp(KEY, "A:$it", "A")) } }
        assertEquals(100, parseOtpMigration(uri(hundred.toByteArray()))!!.imported.size)
        assertNull(parseOtpMigration(uri(hundred.msg(1, otp(KEY, "A:x", "A")).toByteArray())))
        // More than 64 KB of URI, even with valid content.
        val big = Pb().apply { repeat(100) { msg(1, otp(KEY, "A:" + "a".repeat(600), "A")) } }.toByteArray()
        assertTrue(uri(big).length > 64 * 1024)
        assertNull(parseOtpMigration(uri(big)))
        // Not a migration link, not Base64, missing or repeated data, invalid UTF-8.
        assertNull(parseOtpMigration("otpauth://totp/A:a?secret=JBSWY3DPEHPK3PXP"))
        assertNull(parseOtpMigration("otpauth-migration://online?data=" + Base64.getEncoder().encodeToString(good)))
        assertNull(parseOtpMigration("otpauth-migration://offline?data=@@@"))
        assertNull(parseOtpMigration("otpauth-migration://offline?foo=bar"))
        assertNull(parseOtpMigration("otpauth-migration://offline?data=%zz"))
        assertNull(parseOtpMigration(uri(good) + "&data=" + Base64.getEncoder().encodeToString(good)))
        assertNull(parseOtpMigration(uri(Pb().msg(1, Pb().bytes(1, KEY).bytes(2, byteArrayOf(0xC3.toByte(), 0x28))).toByteArray())))
        // Empty payload: valid, nothing to import.
        assertEquals(0, parseOtpMigration("otpauth-migration://offline?data=")!!.imported.size)
        // Random garbage never throws.
        val rnd = java.util.Random(1)
        repeat(3000) {
            val b = ByteArray(rnd.nextInt(200)).also(rnd::nextBytes)
            parseOtpMigration(uri(b))
            parseOtpMigration(uri(good.copyOf(rnd.nextInt(good.size))))
        }
    }

    @Test fun urlDecodingOfDataParam() {
        // Payload whose Base64 contains '+', '/' and '=' (secret bytes chosen for it).
        val payload = Pb().msg(1, otp(byteArrayOf(-5, -17, -1, -2, 0x3E, 0x3F, -5, -1, 0, 1, 2), "A:a", "A")).toByteArray()
        val b64 = Base64.getEncoder().encodeToString(payload)
        assertTrue(b64, b64.contains('+') && b64.contains('/') && b64.endsWith("="))
        val expected = parseOtpMigration("otpauth-migration://offline?data=" + b64.replace("+", "%2B").replace("/", "%2F").replace("=", "%3D"))!!
        assertEquals(1, expected.imported.size)
        // A raw '+' stays a Base64 '+' (never a space); lower-case escapes and unpadded data work too.
        assertEquals(expected, parseOtpMigration("otpauth-migration://offline?data=$b64"))
        assertEquals(expected, parseOtpMigration("otpauth-migration://offline?data=" + b64.replace("/", "%2f").replace("=", "%3d")))
        assertEquals(expected, parseOtpMigration("  OTPAUTH-MIGRATION://offline?data=" + b64.trimEnd('=') + "  "))
    }

    @Test fun base32EncodeMatchesRfc4648() {
        // RFC 4648 §10 test vectors (without padding, as authenticator apps write keys).
        val vectors = listOf("" to "", "f" to "MY", "fo" to "MZXQ", "foo" to "MZXW6", "foob" to "MZXW6YQ", "fooba" to "MZXW6YTB", "foobar" to "MZXW6YTBOI")
        vectors.forEach { (plain, b32) -> assertEquals(b32, base32Encode(plain.toByteArray())) }
        val rnd = java.util.Random(2)
        repeat(200) {
            val b = ByteArray(1 + rnd.nextInt(64)).also(rnd::nextBytes)
            assertArrayEquals(b, base32Decode(base32Encode(b)))
        }
    }

    @Test fun toOtpAuthRoundTrip() {
        val cases = listOf(
            TotpSecret("JBSWY3DPEHPK3PXP", issuer = "Example", account = "alice@google.com"),
            TotpSecret("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", issuer = "Mi Banco & Co+ ñ/?#%=", account = "ana+test@ejemplo.es: 🔐 x", algorithm = "SHA256", digits = 8, period = 60),
            TotpSecret("JBSWY3DPEHPK3PXP", issuer = "a:b", account = "c:d", algorithm = "SHA512"),
            TotpSecret("JBSWY3DPEHPK3PXP", issuer = "", account = "solo:cuenta"),
            TotpSecret("JBSWY3DPEHPK3PXP", issuer = "", account = "sin servicio"),
            TotpSecret("JBSWY3DPEHPK3PXP", issuer = "Solo servicio", account = ""),
        )
        cases.forEach { s ->
            val u = s.toOtpAuthUri()
            assertTrue(u, u.startsWith("otpauth://totp/"))
            assertTrue(u, java.net.URI(u).rawQuery != null && ' ' !in u)
            assertEquals(u, s, parseOtpAuth(u))
        }
    }
}
