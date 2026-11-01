package app.seguridad.vault

import app.seguridad.crypto.CryptoFailure
import app.seguridad.crypto.KdfParams
import app.seguridad.crypto.OtpAuthRead
import app.seguridad.crypto.TOTP_MIN_KEY_BYTES
import app.seguridad.crypto.TOTP_UNSUPPORTED_ENTRY
import app.seguridad.crypto.TOTP_UNSUPPORTED_LINK
import app.seguridad.crypto.TOTP_UNSUPPORTED_ROW
import app.seguridad.crypto.TotpSecret
import app.seguridad.crypto.base32Decode
import app.seguridad.crypto.base32Encode
import app.seguridad.crypto.hotp
import app.seguridad.crypto.isSupported
import app.seguridad.crypto.OTPAUTH_MAX_CHARS
import app.seguridad.crypto.OTPAUTH_MAX_NAME_CHARS
import app.seguridad.crypto.longEnoughOrWiped
import app.seguridad.crypto.readOtpAuth
import app.seguridad.crypto.totpCode
import app.seguridad.crypto.totpKey
import app.seguridad.tools.ForbiddenWords
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.util.zip.ZipInputStream

private val FAST = KdfParams(1024, 1, 1)

class VaultTotpTest {
    // The three readings of a link these tests check, all from the one sealed answer the screens use (F4).
    private fun parseOtpAuth(text: String) = (readOtpAuth(text) as? OtpAuthRead.Usable)?.secret
    private fun otpAuthUnsupported(text: String) = readOtpAuth(text) is OtpAuthRead.UnsupportedSettings
    private fun otpAuthNames(text: String) = readOtpAuth(text).names

    private fun entry(id: String, password: String = "p") =
        VaultEntry(id, EntryType.LOGIN, "Banco", username = "ana", password = password, createdAt = 1, updatedAt = 1)

    @Test fun `vault round trip and wrong master`() {
        val (file, dek) = VaultCrypto.create("maestra larga".toCharArray(), now = 5, kdf = FAST)
        assertTrue(VaultCrypto.read(file, dek).isEmpty())
        val saved = VaultCrypto.write(file, dek, listOf(entry("1"), entry("2", "ñ 🔐")))
        val text = VaultCrypto.encodeFile(saved)
        assertTrue("password must not appear in the file", !text.contains("ñ 🔐") && !text.contains("ana"))
        val again = VaultCrypto.decodeFile(text)
        val dek2 = VaultCrypto.unlock(again, "maestra larga".toCharArray())
        assertArrayEquals(dek, dek2)
        assertEquals(listOf("1", "2"), VaultCrypto.read(again, dek2).map { it.id })
        try {
            VaultCrypto.unlock(again, "otra".toCharArray())
            fail()
        } catch (e: CryptoFailure) {
            // expected
        }
    }

    @Test fun `tampered data or swapped key blob is detected`() {
        val (file, dek) = VaultCrypto.create("m".toCharArray(), 0, FAST)
        val saved = VaultCrypto.write(file, dek, listOf(entry("1")))
        val raw = java.util.Base64.getDecoder().decode(saved.data)
        raw[20] = (raw[20].toInt() xor 1).toByte()
        val bad = saved.copy(data = java.util.Base64.getEncoder().encodeToString(raw))
        try {
            VaultCrypto.read(bad, dek)
            fail()
        } catch (e: CryptoFailure) {
            // expected
        }
        // The key blob used as data (or vice versa) fails thanks to the separate associated data.
        try {
            VaultCrypto.read(saved.copy(data = saved.wrappedKey), dek)
            fail()
        } catch (e: CryptoFailure) {
            // expected
        }
    }

    @Test fun `changing the master keeps entries and drops the old password`() {
        val (file, dek) = VaultCrypto.create("vieja".toCharArray(), 0, FAST)
        val withData = VaultCrypto.write(file, dek, listOf(entry("1")))
        val (changed, newDek) = VaultCrypto.changeMaster(withData, dek, "nueva".toCharArray(), now = 9, kdf = FAST)
        assertNotEquals(withData.salt, changed.salt)
        // A new data key: the old one (from an old export and the old password) opens nothing saved afterwards.
        assertFalse(newDek.contentEquals(dek))
        try {
            VaultCrypto.read(changed, dek)
            fail()
        } catch (e: CryptoFailure) {
            // expected
        }
        assertEquals(9L, changed.masterCheckedAt)
        assertEquals(listOf("1"), VaultCrypto.read(changed, VaultCrypto.unlock(changed, "nueva".toCharArray())).map { it.id })
        try {
            VaultCrypto.unlock(changed, "vieja".toCharArray())
            fail()
        } catch (e: CryptoFailure) {
            // expected
        }
    }

    @Test fun `not a vault`() {
        try {
            VaultCrypto.decodeFile("{\"hola\":1}")
            fail()
        } catch (e: CryptoFailure) {
            // expected
        }
    }

    // RFC 6238 appendix B: 8-digit codes at T = 59 s and 1111111109 s.
    @Test fun `totp matches RFC 6238`() {
        val sha1 = "12345678901234567890".toByteArray()
        val sha256 = "12345678901234567890123456789012".toByteArray()
        val sha512 = "1234567890123456789012345678901234567890123456789012345678901234".toByteArray()
        assertEquals("94287082", hotp(sha1, 59 / 30, 8, "SHA1"))
        assertEquals("46119246", hotp(sha256, 59 / 30, 8, "SHA256"))
        assertEquals("90693936", hotp(sha512, 59 / 30, 8, "SHA512"))
        assertEquals("07081804", hotp(sha1, 1111111109L / 30, 8, "SHA1"))
        assertEquals("68084774", hotp(sha256, 1111111109L / 30, 8, "SHA256"))
        assertEquals("25091201", hotp(sha512, 1111111109L / 30, 8, "SHA512"))
    }

    @Test fun `base32 and totp code with time left`() {
        // "12345678901234567890" in Base32.
        val secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"
        assertArrayEquals("12345678901234567890".toByteArray(), base32Decode(secret.lowercase().chunked(4).joinToString(" ")))
        assertNull(base32Decode("not base32!"))
        val (code, left) = totpCode(TotpSecret(secret, digits = 8), 59_000)!!
        assertEquals("94287082", code)
        assertEquals(1, left)
    }

    /** Final review A-1, F5: the decoder gives the whole bytes of 5 bits per character, as a plain bit string. */
    @Test fun `base32 decodes into an array of its exact size`() {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val rnd = java.util.Random(5)
        for (length in 1..64) repeat(20) {
            val text = String(CharArray(length) { alphabet[rnd.nextInt(32)] })
            val bits = text.map { Integer.toBinaryString(alphabet.indexOf(it)).padStart(5, '0') }.joinToString("")
            val expected = ByteArray(bits.length / 8) { i -> bits.substring(8 * i, 8 * i + 8).toInt(2).toByte() }
            assertArrayEquals(text, expected, base32Decode(text))
        }
    }

    /** Final review A-1, F5: a decoded key that is too short is cleared before it is dropped. */
    @Test fun `a decoded key too short for TOTP is cleared, a long enough one is handed over as it is`() {
        val short = ByteArray(TOTP_MIN_KEY_BYTES - 1) { 7 }
        assertNull(longEnoughOrWiped(short))
        assertTrue(short.all { it == 0.toByte() })
        val enough = ByteArray(TOTP_MIN_KEY_BYTES) { 7 }
        assertSame(enough, longEnoughOrWiped(enough))
        assertTrue(enough.all { it == 7.toByte() })
    }

    @Test fun `otpauth links`() {
        val t = parseOtpAuth("otpauth://totp/Example:alice@google.com?secret=JBSWY3DPEHPK3PXP&issuer=Example")!!
        assertEquals("Example", t.issuer)
        assertEquals("alice@google.com", t.account)
        assertEquals("JBSWY3DPEHPK3PXP", t.secret)
        assertEquals(6, t.digits)
        val t2 = parseOtpAuth("otpauth://totp/ACME%20Co:john?secret=JBSWY3DPEHPK3PXP&algorithm=SHA256&digits=8&period=60")!!
        assertEquals("ACME Co", t2.issuer)
        assertEquals("SHA256", t2.algorithm)
        assertEquals(8, t2.digits)
        assertEquals(60, t2.period)
        assertNull(parseOtpAuth("otpauth://hotp/x?secret=JBSWY3DPEHPK3PXP&counter=1"))
        assertNull(parseOtpAuth("https://example.com"))
        assertNull(parseOtpAuth("otpauth://totp/x?secret=!!!"))
        assertNull(parseOtpAuth("otpauth://totp/x?secret=JBSWY3DPEHPK3PXP%"))
        assertNull(parseOtpAuth("otpauth://totp/x%"))
    }

    @Test fun `seed word counts`() {
        assertEquals(12 to true, seedWordCount(" abandon ".repeat(12)))
        assertEquals(13 to false, seedWordCount("a ".repeat(13)))
    }

    @Test fun `too short TOTP secrets are refused instead of crashing`() {
        assertNull(totpKey("A"))
        assertNull(totpKey("AAAAAAAA")) // 5 bytes
        assertNull(parseOtpAuth("otpauth://totp/x?secret=A"))
        assertNull(totpCode(TotpSecret("A"), 0))
        assertEquals(10, totpKey("JBSWY3DPEHPK3PXP")!!.size)
    }

    @Test fun `a scanned link cannot turn its names around or make them huge`() {
        // U+202E (right-to-left override) and U+2066 (isolate) would show «Banco» as something else; a newline breaks lines.
        val t = parseOtpAuth("otpauth://totp/%E2%80%AEocnaB:al%0Aice?secret=JBSWY3DPEHPK3PXP&issuer=%E2%81%A6Banco%E2%80%8F")!!
        assertEquals("Banco", t.issuer)
        assertEquals("alice", t.account)
        val long = "a".repeat(1000)
        val big = parseOtpAuth("otpauth://totp/x:$long?secret=JBSWY3DPEHPK3PXP&issuer=$long")!!
        assertEquals(OTPAUTH_MAX_NAME_CHARS, big.issuer.length)
        assertEquals(OTPAUTH_MAX_NAME_CHARS, big.account.length)
        assertNull(parseOtpAuth("otpauth://totp/x?secret=JBSWY3DPEHPK3PXP&issuer=" + "b".repeat(OTPAUTH_MAX_CHARS)))
    }

    // ---- CRY-B02: TOTP does not invent settings ----

    private val rfcKey = "12345678901234567890".toByteArray() // The RFC 6238 test key for SHA-1 (20 bytes).
    private val rfcSecret = base32Encode(rfcKey)
    private fun link(extra: String) = "otpauth://totp/Example:ana?secret=JBSWY3DPEHPK3PXP&issuer=Example" + extra

    @Test fun `a link with a setting this app cannot calculate is refused, not changed`() {
        for (setting in listOf("digits=10", "digits=5", "digits=9", "digits=0", "digits=-6", "digits=abc", "digits=", "period=5", "period=9", "period=121", "period=300", "period=0", "period=abc", "period=", "algorithm=MD5", "algorithm=", "algorithm=SHA384")) {
            assertNull(setting, parseOtpAuth(link("&$setting")))
            assertTrue(setting, otpAuthUnsupported(link("&$setting")))
        }
        // A bad setting among good ones, and the setting before the secret.
        assertNull(parseOtpAuth(link("&algorithm=SHA256&digits=8&period=300")))
        assertTrue(otpAuthUnsupported(link("&algorithm=SHA256&digits=8&period=300")))
        assertNull(parseOtpAuth("otpauth://totp/x?digits=10&secret=JBSWY3DPEHPK3PXP"))
        assertTrue(otpAuthUnsupported("otpauth://totp/x?digits=10&secret=JBSWY3DPEHPK3PXP"))
        // Also with the scheme and host in capitals and spaces around the text, as parseOtpAuth reads it.
        assertTrue(otpAuthUnsupported("  OTPAUTH://TOTP/x?secret=JBSWY3DPEHPK3PXP&digits=10 "))
    }

    @Test fun `without the parameters the defaults stay 6 digits, 30 seconds and SHA1`() {
        val t = parseOtpAuth("otpauth://totp/Example:ana?secret=JBSWY3DPEHPK3PXP")!!
        assertEquals(6, t.digits)
        assertEquals(30, t.period)
        assertEquals("SHA1", t.algorithm)
        assertTrue(t.isSupported())
        assertFalse(otpAuthUnsupported("otpauth://totp/Example:ana?secret=JBSWY3DPEHPK3PXP"))
        // Only one of the three missing: the other two are read as written.
        val some = parseOtpAuth(link("&digits=8"))!!
        assertEquals(Triple("SHA1", 8, 30), Triple(some.algorithm, some.digits, some.period))
        // Every value in range is read as written (the edges included); the algorithm in any case.
        for (d in 6..8) assertEquals(d, parseOtpAuth(link("&digits=$d"))!!.digits)
        for (p in listOf(10, 30, 60, 120)) assertEquals(p, parseOtpAuth(link("&period=$p"))!!.period)
        for ((written, kept) in listOf("sha1" to "SHA1", "SHA256" to "SHA256", "sha512" to "SHA512")) assertEquals(kept, parseOtpAuth(link("&algorithm=$written"))!!.algorithm)
    }

    @Test fun `only a valid TOTP link with a setting this app cannot calculate is reported as unsupported`() {
        // Anything else is not «this code uses a setting I cannot do»: the usual «not a 2FA code» answer.
        assertFalse(otpAuthUnsupported(link("")))
        assertFalse(otpAuthUnsupported(link("&digits=8&period=60&algorithm=SHA512")))
        assertFalse(otpAuthUnsupported(""))
        assertFalse(otpAuthUnsupported("https://example.com/?secret=JBSWY3DPEHPK3PXP&digits=10"))
        assertFalse(otpAuthUnsupported("otpauth://hotp/x?secret=JBSWY3DPEHPK3PXP&counter=1&digits=10"))
        assertFalse(otpAuthUnsupported("otpauth-migration://offline?data=CjEKCkhlbGxvId6tvu8SGEV4YW1wbGU6YWxpY2VAZ29vZ2xlLmNvbRoHRXhhbXBsZTAC"))
        assertFalse("no key", otpAuthUnsupported("otpauth://totp/x?digits=10"))
        assertFalse("key too short", otpAuthUnsupported("otpauth://totp/x?secret=A&digits=10"))
        assertFalse("key not Base32", otpAuthUnsupported("otpauth://totp/x?secret=!!!&digits=10"))
        assertFalse("broken escape", otpAuthUnsupported("otpauth://totp/x?secret=JBSWY3DPEHPK3PXP%&digits=10"))
        assertFalse("broken escape in the label", otpAuthUnsupported("otpauth://totp/x%?secret=JBSWY3DPEHPK3PXP&digits=10"))
        assertFalse("too long", otpAuthUnsupported(link("&digits=10&issuer=" + "b".repeat(OTPAUTH_MAX_CHARS))))
    }

    @Test fun `the service and the account of a link are read also when its settings are not supported`() {
        assertEquals("Example" to "ana", otpAuthNames(link("")))
        for (setting in listOf("digits=10", "period=300", "algorithm=MD5", "digits=abc", "period=", "algorithm=")) {
            assertEquals(setting, "Example" to "ana", otpAuthNames(link("&$setting")))
        }
        // As parseOtpAuth keeps them: from the label when the issuer parameter is missing, and without turning them around.
        assertEquals("Banco" to "ana", otpAuthNames("otpauth://totp/Banco:ana?secret=JBSWY3DPEHPK3PXP&digits=10"))
        assertEquals("Banco" to "alice", otpAuthNames("otpauth://totp/%E2%80%AEocnaB:al%0Aice?secret=JBSWY3DPEHPK3PXP&issuer=%E2%81%A6Banco%E2%80%8F&period=5"))
        assertEquals("" to "", otpAuthNames("otpauth://totp/?secret=JBSWY3DPEHPK3PXP&digits=10"))
        // The names of a link that is not a usable 2FA link are none.
        for (text in listOf(
            "", "https://example.com/Example:ana?secret=JBSWY3DPEHPK3PXP", "otpauth://hotp/Example:ana?secret=JBSWY3DPEHPK3PXP&counter=1",
            "otpauth://totp/Example:ana?digits=10", "otpauth://totp/Example:ana?secret=A&digits=10", "otpauth://totp/Example:ana?secret=!!!",
            "otpauth://totp/Example:ana?secret=JBSWY3DPEHPK3PXP%", "otpauth://totp/Example%:ana?secret=JBSWY3DPEHPK3PXP&digits=10",
            "otpauth-migration://offline?data=AAAA",
        )) assertNull(text, otpAuthNames(text))
    }

    @Test fun `a code with a setting this app cannot calculate gives no code instead of a wrong one`() {
        val key = TotpSecret(rfcSecret, digits = 8)
        assertEquals("94287082", totpCode(key, 59_000)!!.first)
        assertNull(totpCode(key.copy(algorithm = "MD5"), 59_000))
        assertNull(totpCode(key.copy(algorithm = "SHA384"), 59_000))
        assertNull(totpCode(key.copy(digits = 10), 59_000))
        assertNull(totpCode(key.copy(digits = 5), 59_000))
        assertNull(totpCode(key.copy(period = 300), 59_000))
        assertNull(totpCode(key.copy(period = 5), 59_000))
        assertNull(totpCode(key.copy(period = 0), 59_000))
        // No longer pulled into range: 300 s used to be 120 s and 10 digits 8.
        assertFalse(key.copy(digits = 10).isSupported())
        assertFalse(key.copy(period = 300).isSupported())
        assertFalse(key.copy(algorithm = "MD5").isSupported())
    }

    @Test fun `an empty algorithm is SHA1, and the name is read in any case, as before`() {
        val key = TotpSecret(rfcSecret, digits = 8, algorithm = "")
        assertTrue(key.isSupported())
        val (code, left) = totpCode(key, 59_000)!!
        assertEquals("94287082", code) // RFC 6238, T = 59 s, SHA-1.
        assertEquals(1, left)
        assertEquals("07081804", totpCode(key, 1_111_111_109_000L)!!.first)
        assertEquals(totpCode(key.copy(algorithm = "SHA1"), 59_000), totpCode(key, 59_000))
        // RFC 6238 SHA-256 and SHA-512 in lower and mixed case: what was accepted stays accepted.
        val k256 = base32Encode("12345678901234567890123456789012".toByteArray())
        val k512 = base32Encode("1234567890123456789012345678901234567890123456789012345678901234".toByteArray())
        for (a in listOf("SHA256", "sha256", "Sha256")) assertEquals(a, "46119246", totpCode(TotpSecret(k256, algorithm = a, digits = 8), 59_000)!!.first)
        for (a in listOf("SHA512", "sha512", "Sha512")) assertEquals(a, "90693936", totpCode(TotpSecret(k512, algorithm = a, digits = 8), 59_000)!!.first)
    }

    @Test fun `every setting in range still gives a code, with the time left of its own period`() {
        for (digits in 6..8) for (period in listOf(10, 30, 60, 120)) {
            val (code, left) = totpCode(TotpSecret(rfcSecret, digits = digits, period = period), 59_000)!!
            assertEquals(digits, code.length)
            assertEquals(period - 59 % period, left)
        }
    }

    @Test fun `hotp itself refuses what it cannot calculate`() {
        assertEquals("94287082", hotp(rfcKey, 1, 8, ""))
        for (a in listOf("MD5", "SHA384", "nope")) assertTrue(a, runCatching { hotp(rfcKey, 1, 8, a) }.exceptionOrNull() is IllegalArgumentException)
        for (d in listOf(0, 5, 9, 10, -1)) assertTrue("$d", runCatching { hotp(rfcKey, 1, d, "SHA1") }.exceptionOrNull() is IllegalArgumentException)
    }

    /** Every TOTP of the real files that Clavero's own versions wrote: they must give a code after the change as before it. */
    @Test fun `every TOTP in the real files of Clavero is still supported and gives a code`() {
        fun bytes(name: String) = javaClass.getResourceAsStream("/seguridad/$name")!!.use { it.readBytes() }
        fun entriesOf(json: String): List<VaultEntry> {
            val file = VaultCrypto.decodeFile(json)
            return VaultCrypto.read(file, VaultCrypto.unlock(file, "maestra-de-prueba".toCharArray()))
        }
        val backupJson = ZipInputStream(bytes("backup-1.1.sgbak").inputStream()).use { zip ->
            generateSequence { zip.nextEntry }.first { it.name == "vault.json" }.let { String(zip.readBytes()) }
        }
        val sources = mapOf(
            "vault-1.1.json" to entriesOf(bytes("vault-1.1.json").decodeToString()),
            "export-1.0.2.json" to entriesOf(bytes("export-1.0.2.json").decodeToString()),
            "backup-1.1.sgbak" to entriesOf(backupJson),
        )
        for ((name, entries) in sources) {
            val totps = entries.mapNotNull { it.totp }
            assertTrue("$name has a TOTP entry", totps.isNotEmpty())
            for (t in totps) {
                assertTrue(name, t.isSupported())
                assertNotNull(name, totpCode(t, 1_790_000_000_000L))
            }
        }
    }

    @Test fun `F4 the text of the list row is short and passes the forbidden words`() {
        assertEquals("2FA no admitido", TOTP_UNSUPPORTED_ROW)
        assertEquals(emptyList<String>(), ForbiddenWords.problems(TOTP_UNSUPPORTED_ROW))
    }

    @Test fun `the two new texts name Clavero, pass the forbidden words and say what happens`() {
        assertEquals("Este código 2FA usa un algoritmo, un número de dígitos o un periodo que Clavero no admite.", TOTP_UNSUPPORTED_LINK)
        assertEquals("Clavero no puede calcular este código: usa un ajuste que no admite.", TOTP_UNSUPPORTED_ENTRY)
        for (text in listOf(TOTP_UNSUPPORTED_LINK, TOTP_UNSUPPORTED_ENTRY)) assertEquals(text, emptyList<String>(), ForbiddenWords.problems(text))
    }

    /** The screens are not compiled on the JVM: their two uses of the decision are checked in the source. */
    @Test fun `the entry screens use the decision from Totp, not a copy of it`() {
        var dir: File? = File(".").absoluteFile
        var screen: File? = null
        while (dir != null && screen == null) {
            screen = File(dir, "app/src/main/java/app/seguridad/ui/screens/VaultEntryScreen.kt").takeIf { it.isFile }
            dir = dir.parentFile
        }
        val source = screen!!.readText()
        val apply = source.substringAfter("fun applyOtpAuth(").substringBefore("    fun save(")
        // F4: the link is read once, and its sealed answer decides (no second parse for the reason).
        assertEquals(apply, 1, Regex("""readOtpAuth\(""").findAll(apply).count())
        assertTrue(apply, "when (val read = readOtpAuth(text))" in apply)
        assertTrue(apply, "is OtpAuthRead.UnsupportedSettings -> form.error.value = TOTP_UNSUPPORTED_LINK" in apply)
        assertFalse(apply, "parseOtpAuth(" in apply || "otpAuthUnsupported(" in apply)
        val detail = source.substringAfter("fun VaultEntryDetailScreen(").substringBefore("private class EntryEditViewModel")
        assertTrue(detail, detail.contains("isSupported()") && detail.contains("TOTP_UNSUPPORTED_ENTRY"))
        // F5: the key typed in the editor is checked with the function that clears its decoded copy.
        assertTrue("type == EntryType.TOTP && !isUsableTotpKey(totp.secret) ->" in source)
        assertFalse("totpKey(" in source)
        // F4: the list shows the short text instead of the code, only for settings this app does not calculate.
        val list = File(screen.parentFile, "VaultScreen.kt").readText()
        val small = """style = MaterialTheme\.typography\.labelSmall"""
        val row = Regex("""if \(!t\.isSupported\(\)\) \{\s*Text\(\s*TOTP_UNSUPPORTED_ROW,\s*$small""")
        assertTrue(row.containsMatchIn(list))
        // The code itself is unchanged: totpCode gives null for such settings, and shows nothing for any other null.
        assertTrue(Regex("""maxLines = 1,\s*\)\s*\}\s*totpCode\(t, now\)\?\.let""").containsMatchIn(list))
        assertFalse(list.contains("TOTP_UNSUPPORTED_LINK") || list.contains("TOTP_UNSUPPORTED_ENTRY"))
    }
}
