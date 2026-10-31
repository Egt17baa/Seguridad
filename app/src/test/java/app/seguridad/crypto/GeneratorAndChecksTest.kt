package app.seguridad.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File

class GeneratorAndChecksTest {
    @Test fun `password has every chosen type and only allowed characters`() {
        repeat(200) {
            val p = generatePassword(PasswordOptions(length = 12, excludeAmbiguous = true))!!
            assertEquals(12, p.length)
            assertTrue(p.any { it in LOWER } && p.any { it in UPPER } && p.any { it in DIGITS } && p.any { it in SYMBOLS })
            assertTrue(p.none { it in AMBIGUOUS })
        }
        val digitsOnly = generatePassword(PasswordOptions(length = 10, lower = false, upper = false, symbols = false))!!
        assertTrue(digitsOnly.all { it.isDigit() })
        assertNull(generatePassword(PasswordOptions(lower = false, upper = false, digits = false, symbols = false)))
    }

    @Test fun `entropy and strength`() {
        assertEquals(20 * (Math.log((26 + 26 + 10 + SYMBOLS.length).toDouble()) / Math.log(2.0)), passwordEntropy(PasswordOptions()), 1e-9)
        assertEquals(6 * 12.925, passphraseEntropy(7776, PassphraseOptions(words = 6)), 0.01)
        assertEquals(Strength.WEAK, strength(40.0))
        assertEquals(Strength.VERY_STRONG, strength(130.0))
    }

    @Test fun `bundled word list is the EFF large list`() {
        // Run from app/ (Gradle Android tests) or from jvm-check/.
        val file = listOf("src/main/assets", "../app/src/main/assets").map { File(it, "eff_large_wordlist.txt") }.first { it.exists() }
        val words = file.readLines().filter { it.isNotBlank() }
        assertEquals(7776, words.size)
        assertEquals(7776, words.toSet().size)
        val phrase = generatePassphrase(words, PassphraseOptions(words = 5, separator = " ", addNumber = true))
        assertEquals(6, phrase.split(" ").size)
    }

    // The HTTP side (only the 5-character prefix is sent) is tested with the network client.
    @Test fun `sha1 of a password and range counts, ignoring padding`() {
        // SHA-1("password") = 5BAA61E4C9B93F3F0682250B6CF8331B7EE68FD8
        val hash = sha1HexChars("password".toCharArray())
        assertEquals("5BAA61E4C9B93F3F0682250B6CF8331B7EE68FD8", String(hash))
        val body = "0018A45C4D1DEF81644B54AB7F969B88D65:0\r\n1E4C9B93F3F0682250B6CF8331B7EE68FD8:9659365\r\n"
        // The suffix as a view of the CharArray (CIFRADO-1: no String of the hash), and as a String.
        assertEquals(9659365, pwnedCount(body, java.nio.CharBuffer.wrap(hash, 5, hash.size - 5)))
        assertEquals(9659365, pwnedCount(body, String(hash).drop(5).lowercase()))
        assertEquals(0, pwnedCount("0018A45C4D1DEF81644B54AB7F969B88D65:0", "0018A45C4D1DEF81644B54AB7F969B88D65"))
    }

    @Test fun `sha256 of abc and hash comparison`() {
        val h = sha256Hex(ByteArrayInputStream("abc".toByteArray()))
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", h)
        assertTrue(sameHash(h, "BA:78:16:BF:8F:01:CF:EA:41:41:40:DE:5D:AE:22:23:B0:03:61:A3:96:17:7A:9C:B4:10:FF:61:F2:00:15:AD"))
        assertFalse(sameHash(h, ""))
    }
}
