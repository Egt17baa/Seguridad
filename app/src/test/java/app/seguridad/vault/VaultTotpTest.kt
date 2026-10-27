package app.seguridad.vault

import app.seguridad.crypto.CryptoFailure
import app.seguridad.crypto.KdfParams
import app.seguridad.crypto.TotpSecret
import app.seguridad.crypto.base32Decode
import app.seguridad.crypto.hotp
import app.seguridad.crypto.OTPAUTH_MAX_CHARS
import app.seguridad.crypto.OTPAUTH_MAX_NAME_CHARS
import app.seguridad.crypto.parseOtpAuth
import app.seguridad.crypto.totpCode
import app.seguridad.crypto.totpKey
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

private val FAST = KdfParams(1024, 1, 1)

class VaultTotpTest {
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
}
