package app.seguridad.crypto

import app.seguridad.imports.wellFormed
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
    /** The usable 2FA code of a link, from the one sealed answer the screens use (F4). */
    private fun parseOtpAuth(text: String) = (readOtpAuth(text) as? OtpAuthRead.Usable)?.secret

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
        assertTrue(s.isSupported())
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
        assertTrue(s.isSupported())
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
        assertTrue(r.imported.all { it.isSupported() })
        assertTrue(r.skipped.isEmpty())
        assertEquals(1, r.batchSize)
    }

    /** Every algorithm and digit count Google writes (and 0, "unspecified"): what is imported is something the app calculates. */
    @Test fun everyImportedEntryIsSupportedAndGivesACode() {
        val payload = Pb().apply {
            for (alg in listOf(0L, 1L, 2L, 3L)) for (digits in listOf(0L, 1L, 2L)) msg(1, otp(KEY, "A:a$alg$digits", "A", alg = alg, digits = digits))
        }
        val imported = parseOtpMigration(uri(payload.toByteArray()))!!.imported
        assertEquals(12, imported.size)
        for (s in imported) {
            assertTrue(s.toString(), s.isSupported())
            assertNotNull(s.toString(), totpCode(s, 59_000))
        }
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

    @Test fun otpNameCutsByCodePoints() {
        val emoji = "x".repeat(OTPAUTH_MAX_NAME_CHARS - 1) + "🛒"
        assertEquals(emoji, otpName(emoji + "yz"))
        assertEquals(emoji, otpName(emoji))
        assertTrue(otpNameFits(emoji))
        assertTrue(!otpNameFits(emoji + "y"))
        // What otpName removes does not count: 300 control characters around a short name fit.
        assertTrue(otpNameFits("\u0007".repeat(300) + "Banco"))
    }

    @Test fun readOtpAuthSaysWhenItCutAName() {
        val long = "x".repeat(OTPAUTH_MAX_NAME_CHARS + 1)
        val cut = readOtpAuth("otpauth://totp/a:$long?secret=JBSWY3DPEHPK3PXP") as OtpAuthRead.Usable
        assertTrue(cut.namesCut)
        assertEquals(OTPAUTH_MAX_NAME_CHARS, cut.secret.account.length)
        assertTrue(!(readOtpAuth("otpauth://totp/a:b?secret=JBSWY3DPEHPK3PXP&issuer=${"i".repeat(OTPAUTH_MAX_NAME_CHARS)}") as OtpAuthRead.Usable).namesCut)
        assertTrue((readOtpAuth("otpauth://totp/a:b?secret=JBSWY3DPEHPK3PXP&issuer=$long") as OtpAuthRead.Usable).namesCut)
    }

    @Test fun migrationSaysWhichCodesHaveANameCut() {
        val long = "x".repeat(OTPAUTH_MAX_NAME_CHARS + 1)
        val payload = Pb().msg(1, otp(KEY, "A:a", "A")).msg(1, otp(KEY, "B:$long", "B")).msg(1, otp(KEY, "$long:c", "")).msg(1, otp(KEY, "D:d", long))
        val r = parseOtpMigration(uri(payload.toByteArray()))!!
        assertEquals(listOf(false, true, true, true), r.importedNamesCut)
        assertEquals(4, r.imported.size)
    }

    @Test fun theNameInALeftOutTextIsCutWithoutSplittingAPair() {
        // 79 letters, an emoji (two UTF-16 units) and one more letter: cut at 80 code points, the emoji stays whole.
        val issuer = "x".repeat(79) + "🛒" + "y"
        val r = parseOtpMigration(uri(Pb().msg(1, otp(KEY, "", issuer, type = 1)).toByteArray()))!!
        val text = r.skipped.single()
        assertTrue(text, text.startsWith("«" + "x".repeat(79) + "🛒»: "))
        assertTrue(wellFormed(text))
    }

    @Test fun leftOutAccountsSayWhyWithoutTheText() {
        // The same accounts as skipsHotpAndMd5WithReason, plus an unknown type and an unknown number of digits: the
        // importer needs the kind, so it never reads the Spanish text back.
        val payload = Pb()
            .msg(1, otp(KEY, "Hotp:a", "Hotp", type = 1, counter = 7))
            .msg(1, otp(KEY, "Viejo:b", "Viejo", alg = 4))
            .msg(1, otp(ByteArray(9) { 1 }, "Corta:c", "Corta"))
            .msg(1, otp(KEY, "Rara:d", "Rara", alg = 9))
            .msg(1, otp(KEY, "Bien:e", "Bien"))
            .msg(1, otp(KEY, "Tipo:f", "Tipo", type = 7))
            .msg(1, otp(KEY, "Cifras:g", "Cifras", digits = 5))
        val r = parseOtpMigration(uri(payload.toByteArray()))!!
        assertEquals(
            listOf(
                MigrationLeftOut("Hotp", "a", MigrationLeftOutKind.NOT_TOTP),
                MigrationLeftOut("Viejo", "b", MigrationLeftOutKind.UNSUPPORTED_SETTINGS),
                MigrationLeftOut("Corta", "c", MigrationLeftOutKind.SHORT_KEY),
                MigrationLeftOut("Rara", "d", MigrationLeftOutKind.UNSUPPORTED_SETTINGS),
                MigrationLeftOut("Tipo", "f", MigrationLeftOutKind.NOT_TOTP),
                MigrationLeftOut("Cifras", "g", MigrationLeftOutKind.UNSUPPORTED_SETTINGS),
            ),
            r.leftOut,
        )
        // One text per account left out, in the same order, as before.
        assertEquals(r.leftOut.size, r.skipped.size)
        assertTrue(r.skipped.zip(r.leftOut).all { (text, left) -> text.startsWith("«${left.issuer} · ${left.account}»") })
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
        assertTrue(parseOtpMigration(uri(hundred.toByteArray()))!!.imported.all { it.isSupported() })
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
            assertTrue(u, s.isSupported())
            assertEquals(u, s, parseOtpAuth(u))
        }
    }

    /** A saved code with no algorithm is SHA-1 (isSupported); its link must say so, since «algorithm=» alone is refused. */
    @Test fun toOtpAuthWritesSha1ForAnEmptyAlgorithm() {
        val saved = TotpSecret("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", issuer = "Banco", account = "ana", algorithm = "", digits = 8)
        assertTrue(saved.isSupported())
        val u = saved.toOtpAuthUri()
        assertTrue(u, "algorithm=SHA1" in u && "algorithm=&" !in u && !u.endsWith("algorithm="))
        val read = parseOtpAuth(u)
        assertEquals(u, saved.copy(algorithm = "SHA1"), read)
        // The same code, from the saved entry and from its link: the RFC 6238 SHA-1 value at 59 s.
        assertEquals("94287082", totpCode(saved, 59_000)!!.first)
        assertEquals(totpCode(saved, 59_000), totpCode(read!!, 59_000))
        // Any other algorithm is written as it is, in capitals, as before.
        assertTrue("algorithm=SHA256" in saved.copy(algorithm = "sha256").toOtpAuthUri())
    }
}
