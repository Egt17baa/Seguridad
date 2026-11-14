package app.seguridad.tools.qr

import app.seguridad.vault.CustomField
import app.seguridad.vault.EntryType
import app.seguridad.vault.FieldKind
import app.seguridad.vault.VaultEntry
import app.seguridad.vault.WIFI_HIDDEN_KEY
import app.seguridad.vault.WIFI_PASSWORD_KEY
import app.seguridad.vault.WIFI_SECURITY_KEY
import app.seguridad.vault.WIFI_SECURITY_VALUES
import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * 2.0, task 9: the wifi QR (`WIFI:T:…;S:…;P:…;H:…;;`, the format of the ZXing documentation). Encoding escapes `\`, `;`,
 * `,`, `:` and `"`, and puts a name or password made only of hexadecimal digits between unescaped quotes (the
 * documentation: «Enclose in double quotes if it is an ASCII name, but could be interpreted as hex»). Reading is bounded,
 * takes the fields in any order and never guesses: a hostile text has one defined result each.
 */
class WifiQrTest {
    private fun wpa(ssid: String, password: String, hidden: Boolean = false) = WifiConfig(ssid, password, "WPA", hidden)

    // ---- The brief's tests ----

    @Test fun roundTripWithSpecialChars() {
        val c = wpa("a;b:c\\d,e\"f", "p;q:r\\s,t\"u", hidden = true)
        val text = WifiQr.encode(c)
        assertEquals("WIFI:T:WPA;S:a\\;b\\:c\\\\d\\,e\\\"f;P:p\\;q\\:r\\\\s\\,t\\\"u;H:true;;", text)
        assertEquals(c, WifiQr.parse(text))
        // Each of the five alone, at the start and at the end.
        for (ch in listOf("\\", ";", ",", ":", "\"")) {
            val d = wpa("${ch}Casa$ch", "${ch}clave$ch")
            assertEquals(ch, d, WifiQr.parse(WifiQr.encode(d)))
        }
    }

    @Test fun openNetworkHasNoPassword() {
        val open = WifiConfig("Bar", "", "nopass", false)
        assertEquals("WIFI:T:nopass;S:Bar;H:false;;", WifiQr.encode(open))
        assertEquals(open, WifiQr.parse(WifiQr.encode(open)))
        // A password kept on an entry marked open does not go in the code: an open network has none.
        val text = WifiQr.encode(WifiConfig("Bar", "clave", "nopass", false))
        assertEquals("WIFI:T:nopass;S:Bar;H:false;;", text)
        assertFalse("P:" in text)
        assertEquals(open, WifiQr.parse("WIFI:T:nopass;S:Bar;;"))
        assertEquals(open, WifiQr.parse("WIFI:S:Bar;;")) // No type: no password (the documentation).
    }

    @Test fun parseAnyOrder() {
        val c = wpa("Casa", "clave", hidden = true)
        assertEquals(c, WifiQr.parse("WIFI:S:Casa;H:true;P:clave;T:WPA;;"))
        assertEquals(c, WifiQr.parse("WIFI:H:true;P:clave;S:Casa;T:WPA;;"))
        assertEquals(WifiConfig("Viejo", "12345", "WEP", false), WifiQr.parse("WIFI:P:12345;T:WEP;S:Viejo;;"))
        // The prefix, the keys and the type in any case.
        assertEquals(wpa("x", "y"), WifiQr.parse("wifi:t:wpa;s:x;p:y;;"))
        assertEquals(wpa("x", "y"), WifiQr.parse("WIFI:T:Wpa;S:x;P:y;H:FALSE;;"))
    }

    @Test fun parseRefusesHugeInput() {
        val ok = "WIFI:T:WPA;S:Casa;P:" + "x".repeat(WIFI_QR_MAX_CHARS - 22) + ";;"
        assertEquals(WIFI_QR_MAX_CHARS, ok.length)
        assertEquals("x".repeat(WIFI_QR_MAX_CHARS - 22), WifiQr.parse(ok)?.password)
        val huge = "WIFI:T:WPA;S:Casa;P:" + "x".repeat(WIFI_QR_MAX_CHARS - 21) + ";;"
        assertEquals(WIFI_QR_MAX_CHARS + 1, huge.length)
        assertNull(WifiQr.parse(huge))
        assertEquals(WifiRead.Malformed, WifiQr.read(huge))
        // A very long one is refused as quickly, without being read.
        assertNull(WifiQr.parse("WIFI:S:" + ";".repeat(1_000_000)))
    }

    @Test fun parseRefusesNonWifi() {
        for (t in listOf("", "WIFI", "WIF:S:a;;", "https://banco.es/", "MECARD:N:Ana;;", "otpauth://totp/x?secret=JBSWY3DPEHPK3PXP", " S:a;;", "XWIFI:S:a;;")) {
            assertNull(t, WifiQr.parse(t))
            assertNull(t, WifiQr.read(t))
        }
    }

    // ---- Every short string of the alphabet of the escapes ----

    /** Every string of length 0 to 3 over `\ ; , : " a` (259 of them). «a» is a hexadecimal digit, so quoting is in it too. */
    private val shortStrings: List<String> = run {
        val alphabet = listOf("\\", ";", ",", ":", "\"", "a")
        var level = listOf("")
        val all = ArrayList<String>(level)
        repeat(3) {
            level = level.flatMap { s -> alphabet.map { s + it } }
            all += level
        }
        all
    }

    @Test fun roundTripOverEveryShortString() {
        assertEquals(259, shortStrings.size)
        var checked = 0
        for (ssid in shortStrings) {
            if (ssid.isEmpty()) continue // An empty name is an error ([emptyNameIsATextNotACrash]).
            for (password in shortStrings) {
                for (security in listOf("WPA", "WEP")) {
                    val c = WifiConfig(ssid, password, security, hidden = checked % 2 == 0)
                    val text = WifiQr.encode(c)
                    assertEquals("«$ssid» / «$password» → $text", c, WifiQr.parse(text))
                    checked++
                }
            }
            // Open: the name alone goes and comes back.
            val open = WifiConfig(ssid, "", "nopass", false)
            assertEquals(open, WifiQr.parse(WifiQr.encode(open)))
        }
        assertEquals(258 * 259 * 2, checked)
    }

    // ---- Hostile input: one defined result each ----

    @Test fun aTrailingBackslashIsRefused() {
        // An escape with nothing after it: reading it either way would be a guess (ZXing drops it; the old reader kept it).
        for (t in listOf("WIFI:T:WPA;S:Casa;P:clave\\", "WIFI:T:WPA;S:Casa\\", "WIFI:S:a;P:b\\\\\\", "WIFI:\\")) {
            assertNull(t, WifiQr.parse(t))
            assertEquals(t, WifiRead.Malformed, WifiQr.read(t))
        }
        // An even run is escaped backslashes: «clave\», and the missing «;;» is tolerated.
        assertEquals(wpa("Casa", "clave\\"), WifiQr.parse("WIFI:T:WPA;S:Casa;P:clave\\\\"))
    }

    @Test fun aRepeatedFieldIsRefused() {
        // Another reader may take the other one: the name or password shown would not be the one used.
        for (t in listOf(
            "WIFI:S:a;S:b;;", "WIFI:T:WPA;S:a;P:x;P:y;;", "WIFI:T:WPA;S:a;P:x;p:y;;", "WIFI:T:WPA;T:WEP;S:a;P:x;;",
            "WIFI:T:WPA;S:a;P:x;H:true;H:false;;", "WIFI:S:a; S:b;;", "WIFI:S:a;S:a;;",
        )) {
            assertNull(t, WifiQr.parse(t))
            assertEquals(t, WifiRead.Malformed, WifiQr.read(t))
        }
    }

    @Test fun unknownFieldsAreIgnored() {
        // ZXing's documentation lists E, A, I and PH2 (enterprise networks); any other key is skipped the same way.
        assertEquals(wpa("Casa", "clave"), WifiQr.parse("WIFI:T:WPA;S:Casa;P:clave;E:TTLS;X:1;PH2:MSCHAPV2;;"))
        assertEquals(wpa("Casa", "clave"), WifiQr.parse("WIFI:X:\\;y;T:WPA;S:Casa;P:clave;X:z;;"))
    }

    @Test fun anUnknownTypeIsShownButNotKept() {
        // Only WPA, WEP and nopass are kept (the three the form offers); any other type is read for the inspector only.
        for (type in listOf("WPA2", "WPA3", "SAE", "WPA2-EAP", "xyz")) {
            val t = "WIFI:T:$type;S:Casa;P:clave;;"
            assertNull(t, WifiQr.parse(t))
            assertEquals(t, WifiRead.Unsupported("Casa", type.uppercase(), "clave", false), WifiQr.read(t))
        }
        // A password with no type, or with «nopass»: contradictory, so not kept (the documentation ignores it).
        assertEquals(WifiRead.Unsupported("Casa", "nopass", "clave", false), WifiQr.read("WIFI:S:Casa;P:clave;;"))
        assertEquals(WifiRead.Unsupported("Casa", "nopass", "clave", false), WifiQr.read("WIFI:T:nopass;S:Casa;P:clave;;"))
        // «H:» that is not a boolean (ZXing reads it as an enterprise setting): not kept. An empty one is «false».
        assertEquals(WifiRead.Unsupported("Casa", "WPA", "clave", false), WifiQr.read("WIFI:T:WPA;S:Casa;P:clave;H:MSCHAPV2;;"))
        assertEquals(wpa("Casa", "clave"), WifiQr.parse("WIFI:T:WPA;S:Casa;P:clave;H:;;"))
        // An empty type is no type.
        assertEquals(WifiConfig("Casa", "", "nopass", false), WifiQr.parse("WIFI:T:;S:Casa;;"))
    }

    @Test fun anEmptyOrMissingNameIsRefused() {
        for (t in listOf("WIFI:T:WPA;S:;P:x;;", "WIFI:T:WPA;S:\"\";P:x;;", "WIFI:T:WPA;P:x;;", "WIFI:", "WIFI:;;")) {
            assertNull(t, WifiQr.parse(t))
            assertEquals(t, WifiRead.Malformed, WifiQr.read(t))
        }
        // A name of spaces is a name (the network's, as it is written).
        assertEquals(WifiConfig(" ", "", "nopass", false), WifiQr.parse("WIFI:S: ;;"))
    }

    @Test fun aMissingTerminatorIsTolerated() {
        assertEquals(wpa("Casa", "clave"), WifiQr.parse("WIFI:T:WPA;S:Casa;P:clave"))
        assertEquals(wpa("Casa", "clave"), WifiQr.parse("WIFI:T:WPA;S:Casa;P:clave;"))
        // Spaces and line breaks around the whole text are not part of it.
        assertEquals(wpa("Casa", "clave"), WifiQr.parse("  WIFI:T:WPA;S:Casa;P:clave;;\n"))
    }

    @Test fun whatDoesNotReadAsFieldsIsRefused() {
        for (t in listOf(
            "WIFI:S:a;;P:b;;", // Something after the end: another reader may take it.
            "WIFI:T:WPA;;S:a;P:b;;",
            "WIFI:S:a;;;",
            "WIFI:T:WPA;S:Casa;P:ab;cd;;", // A «;» not escaped in the password: a piece without «:».
            "WIFI::x;S:a;;", // No key.
            "WIFI:S\\:x:a;;", // A key that is not letters and digits.
            "WIFI:1S:a;;",
            "WIFI://banco.es/",
        )) {
            assertNull(t, WifiQr.parse(t))
            assertEquals(t, WifiRead.Malformed, WifiQr.read(t))
        }
    }

    @Test fun neverThrowsOnAnything() {
        val pieces = listOf("WIFI:", "T:", "S:", "P:", "H:", "WPA", "WEP", "nopass", "true", ";", ";;", ":", "\\", "\"", ",", "a", "0", " ", "\n", "\u202E", "\uD83D")
        val rnd = Random(20261114L)
        repeat(30_000) {
            val sb = StringBuilder(if (rnd.nextBoolean()) "WIFI:" else "")
            repeat(rnd.nextInt(14)) { sb.append(pieces[rnd.nextInt(pieces.size)]) }
            val t = sb.toString()
            val read = WifiQr.read(t)
            val parsed = WifiQr.parse(t)
            // A text read as usable gives back the same thing when written again and read once more.
            if (read is WifiRead.Usable) {
                assertEquals(read.config, parsed)
                assertEquals(t, read.config, WifiQr.parse(WifiQr.encode(read.config)))
            } else {
                assertNull(parsed)
            }
        }
    }

    // ---- A name or password of hexadecimal digits only ----

    @Test fun hexOnlyIsWrittenInQuotesAndReadAsText() {
        // The ZXing documentation: «Enclose in double quotes if it is an ASCII name, but could be interpreted as hex».
        assertEquals("WIFI:T:WPA;S:\"CAFE\";P:\"12345678\";H:false;;", WifiQr.encode(wpa("CAFE", "12345678")))
        assertEquals("WIFI:T:WPA;S:\"a\";P:\"0\";H:false;;", WifiQr.encode(wpa("a", "0")))
        // Not only hexadecimal: as it is.
        assertEquals("WIFI:T:WPA;S:Café;P:abcdeg;H:false;;", WifiQr.encode(wpa("Café", "abcdeg")))
        // 64 hexadecimal digits (what WPA reads as a raw key when it is not quoted) are written in quotes as well:
        // Clavero keeps text and does not guess which one the user meant (in «Dudas» of the task's report).
        val raw = "0123456789abcdef".repeat(4)
        assertEquals("WIFI:T:WPA;S:Casa;P:\"$raw\";H:false;;", WifiQr.encode(wpa("Casa", raw)))
        // Read: the quotes go, and hexadecimal is never decoded, quoted or not.
        assertEquals(wpa("CAFE", "12345678"), WifiQr.parse("WIFI:T:WPA;S:\"CAFE\";P:\"12345678\";;"))
        assertEquals(wpa("CAFE", "12345678"), WifiQr.parse("WIFI:T:WPA;S:CAFE;P:12345678;;"))
        assertEquals(wpa("Casa", raw), WifiQr.parse("WIFI:T:WPA;S:Casa;P:$raw;;"))
    }

    @Test fun onlyQuotesThatAreNotEscapedEncloseAValue() {
        // The documentation's own example: quotes that are part of the name are escaped, and they stay in it.
        assertEquals(WifiConfig("\"foo;bar\\baz\"", "", "nopass", false), WifiQr.parse("WIFI:S:\\\"foo\\;bar\\\\baz\\\";;"))
        assertEquals("WIFI:T:nopass;S:\\\"foo\\;bar\\\\baz\\\";H:false;;", WifiQr.encode(WifiConfig("\"foo;bar\\baz\"", "", "nopass", false)))
        // An opening quote with an escaped closing one is not enclosing: both stay.
        assertEquals("\"abc\"", WifiQr.parse("WIFI:S:\"abc\\\";;")?.ssid)
        // An escaped backslash before the closing quote: enclosing, and the backslash is the name's.
        assertEquals("abc\\", WifiQr.parse("WIFI:S:\"abc\\\\\";;")?.ssid)
        // One quote alone, or one at one end only: as it is.
        assertEquals("\"", WifiQr.parse("WIFI:S:\";;")?.ssid)
        assertEquals("\"abc", WifiQr.parse("WIFI:S:\"abc;;")?.ssid)
        // Quoted text keeps what it holds, escapes read inside.
        assertEquals(wpa("a;b", "x y"), WifiQr.parse("WIFI:T:WPA;S:\"a\\;b\";P:\"x y\";;"))
    }

    // ---- Writing: a text, never a crash ----

    @Test fun emptyNameIsATextNotACrash() {
        val c = wpa("", "clave")
        assertEquals(WIFI_QR_NO_NAME, WifiQr.problem(c))
        try {
            WifiQr.encode(c)
            fail("an empty name must not be written")
        } catch (e: IllegalArgumentException) {
            assertEquals(WIFI_QR_NO_NAME, e.message)
        }
    }

    @Test fun anUnknownSecurityIsNeverWrittenAsItIs() {
        // A copy made elsewhere can bring any text there: written as it is, «WPA;S:otra» would add a field of its own.
        for (security in listOf("WPA;S:otra", "", "WPA2", "wpa", "Abierta")) {
            val c = WifiConfig("Casa", "clave", security, false)
            assertEquals(security, WIFI_QR_NO_SECURITY, WifiQr.problem(c))
            try {
                WifiQr.encode(c)
                fail(security)
            } catch (e: IllegalArgumentException) {
                assertEquals(WIFI_QR_NO_SECURITY, e.message)
            }
        }
        for (security in WIFI_SECURITY_VALUES) assertNull(WifiQr.problem(WifiConfig("Casa", "clave", security, false)))
    }

    @Test fun tooLongIsATextNotACrash() {
        // Longer than a QR code holds: the same bound as reading, so whatever is written can be read back.
        val c = wpa("Casa", "x".repeat(WIFI_QR_MAX_CHARS))
        assertEquals(WIFI_QR_TOO_BIG, WifiQr.problem(c))
        try {
            WifiQr.encode(c)
            fail("too long")
        } catch (e: IllegalArgumentException) {
            assertEquals(WIFI_QR_TOO_BIG, e.message)
        }
        // A field of the vault may hold 10 000 code points; four bytes each is still a text.
        assertEquals(WIFI_QR_TOO_BIG, WifiQr.problem(wpa("Casa", "😀".repeat(10_000))))
        // Just at the bound: written; one more character, or a character of more than one byte, is not (the inspector
        // reads at most 4 096 bytes, so whatever is written can be read there too).
        val atBound = wpa("Casa", "x".repeat(WIFI_QR_MAX_CHARS - 30))
        assertEquals(WIFI_QR_MAX_CHARS, WifiQr.encode(atBound).length)
        assertEquals(WIFI_QR_TOO_BIG, WifiQr.problem(wpa("Casa", "x".repeat(WIFI_QR_MAX_CHARS - 29))))
        assertEquals(WIFI_QR_TOO_BIG, WifiQr.problem(wpa("Casa", "ñ" + "x".repeat(WIFI_QR_MAX_CHARS - 31))))
    }

    // ---- What the screen of the QR says ----

    @Test fun theScreenSaysTheWarningByTheNetworkOrWhyNot() {
        // O-4, on view: with a password, who sees the code sees it; an open network has none to see.
        assertEquals(listOf(WIFI_QR_WARNING), wifiQrLines(wpa("Casa", "clave")))
        assertEquals(listOf(WIFI_QR_WARNING), wifiQrLines(WifiConfig("Casa", "clave", "WEP", false)))
        assertEquals(listOf(WIFI_QR_WARNING_OPEN), wifiQrLines(WifiConfig("Bar", "", "nopass", false)))
        // Marked open with a password kept: the code goes without it, and the screen says so.
        assertEquals(listOf(WIFI_QR_WARNING_OPEN, WIFI_QR_OPEN_WITH_PASSWORD), wifiQrLines(WifiConfig("Bar", "clave", "nopass", false)))
        // No code: only why, and no warning about a code that is not there.
        assertEquals(listOf(WIFI_QR_NO_NAME), wifiQrLines(wpa("", "clave")))
        assertEquals(listOf(WIFI_QR_NO_SECURITY), wifiQrLines(WifiConfig("Casa", "clave", "WPA2", false)))
        assertEquals(listOf(WIFI_QR_TOO_BIG), wifiQrLines(wpa("Casa", "x".repeat(WIFI_QR_MAX_CHARS))))
    }

    // ---- What never shows the password ----

    @Test fun toStringNeverCarriesThePassword() {
        val c = wpa("Casa", "clave-secreta")
        assertFalse("clave-secreta" in c.toString())
        assertFalse("clave-secreta" in WifiRead.Usable(c).toString())
        assertFalse("clave-secreta" in WifiRead.Unsupported("Casa", "WPA2", "clave-secreta", false).toString())
    }

    // ---- From a vault entry ----

    private fun entry(fields: List<CustomField>) = VaultEntry("w", EntryType.WIFI, "Casa", createdAt = 0, updatedAt = 0, fields = fields)

    private fun field(id: String, value: String, kind: FieldKind = FieldKind.TEXT) = CustomField(id, id, kind, value)

    @Test fun anEntryGivesItsTemplateFields() {
        val e = entry(listOf(
            field("ssid", "Casa"), field(WIFI_PASSWORD_KEY, "clave", FieldKind.HIDDEN),
            field(WIFI_SECURITY_KEY, "WEP"), field(WIFI_HIDDEN_KEY, "true"),
        ))
        assertEquals(WifiConfig("Casa", "clave", "WEP", true), wifiConfigOf(e))
        // Missing fields are empty, and «Red oculta» is only «true».
        assertEquals(WifiConfig("", "", "", false), wifiConfigOf(entry(emptyList())))
        assertFalse(wifiConfigOf(entry(listOf(field(WIFI_HIDDEN_KEY, "TRUE")))).hidden)
    }

    @Test fun aRepeatedIdTakesTheFirstAsTheCardShowsIt() {
        val e = entry(listOf(
            field("ssid", "Casa"), field("ssid", "Otra"),
            field(WIFI_PASSWORD_KEY, "primera", FieldKind.HIDDEN), field(WIFI_PASSWORD_KEY, "segunda", FieldKind.HIDDEN),
            field(WIFI_SECURITY_KEY, "WPA"), field(WIFI_SECURITY_KEY, "WPA;S:otra"),
        ))
        assertEquals(WifiConfig("Casa", "primera", "WPA", false), wifiConfigOf(e))
    }

    @Test fun theTextsSayNoOtherWifiSpellingAndShowNoPassword() {
        for (t in WIFI_QR_TEXTS) {
            assertFalse(t, "Wi-Fi" in t || "WiFi" in t)
            assertTrue(t, t.isNotBlank())
        }
        // The image's description is a fixed text: it never carries the network's password.
        assertTrue(WIFI_QR_IMAGE in WIFI_QR_TEXTS)
    }
}
