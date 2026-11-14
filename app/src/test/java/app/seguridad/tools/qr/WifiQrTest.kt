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
        // The value of «T:» and «H:» in any case (as ZXing reads «H:»); written again as «WPA» and «false».
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

    /**
     * Every string of length 0 to 3 over `\ ; , : " a`, a space and `P` (585 of them). «a» is a hexadecimal digit, so
     * quoting is in it too; the space proves no value is trimmed; `P` proves the writer never leaves a «P:» that the rule
     * of I-2 would refuse (m-5).
     */
    private val shortStrings: List<String> = run {
        val alphabet = listOf("\\", ";", ",", ":", "\"", "a", " ", "P")
        var level = listOf("")
        val all = ArrayList<String>(level)
        repeat(3) {
            level = level.flatMap { s -> alphabet.map { s + it } }
            all += level
        }
        all
    }

    /** The four values [read] gives, Usable or not. */
    private fun valuesOf(r: WifiRead?): WifiConfig? = when (r) {
        is WifiRead.Usable -> r.config
        is WifiRead.Unsupported -> WifiConfig(r.ssid, r.password, r.security, r.hidden)
        else -> null
    }

    @Test fun roundTripOverEveryShortString() {
        assertEquals(585, shortStrings.size)
        var checked = 0
        for (ssid in shortStrings) {
            if (ssid.isEmpty()) continue // An empty name is an error ([emptyNameIsATextNotACrash]).
            // A name with a space at either end comes back the same, and is not offered for the vault (m-3).
            val spaces = ssid != ssid.trim()
            for (password in shortStrings) {
                if (password.isEmpty()) continue // WPA or WEP with no password is an error (m-1).
                for (security in listOf("WPA", "WEP")) {
                    val c = WifiConfig(ssid, password, security, hidden = checked % 2 == 0)
                    val text = WifiQr.encode(c)
                    val read = WifiQr.read(text)
                    assertEquals("«$ssid» / «$password» → $text", c, valuesOf(read))
                    assertEquals(text, spaces, read is WifiRead.Unsupported)
                    checked++
                }
            }
            // Open: the name alone goes and comes back.
            val open = WifiConfig(ssid, "", "nopass", false)
            assertEquals(open, valuesOf(WifiQr.read(WifiQr.encode(open))))
        }
        assertEquals(584 * 584 * 2, checked)
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
        // Owner's decision (14-11-2026, Duda 2): WPA2 is read as WPA, kept, and written again as «T:WPA».
        for (type in listOf("WPA2", "wpa2", "Wpa2")) {
            val t = "WIFI:T:$type;S:Casa;P:clave;;"
            assertEquals(t, wpa("Casa", "clave"), WifiQr.parse(t))
            assertEquals("WIFI:T:WPA;S:Casa;P:clave;H:false;;", WifiQr.encode(WifiQr.parse(t)!!))
        }
        // WPA3 and SAE are not (a code with «T:WPA» does not serve a network that is only WPA3), nor any other type: read
        // for the inspector only, with the type as it came, in capitals.
        for (type in listOf("WPA3", "SAE", "sae", "WPA2-EAP", "xyz")) {
            val t = "WIFI:T:$type;S:Casa;P:clave;;"
            assertNull(t, WifiQr.parse(t))
            assertEquals(t, WifiRead.Unsupported("Casa", type.uppercase(), "clave", false, WifiNotKept.SECURITY), WifiQr.read(t))
        }
        // A password with no type, or with «nopass»: contradictory, so not kept (the documentation ignores it).
        assertEquals(WifiRead.Unsupported("Casa", "nopass", "clave", false, WifiNotKept.PASSWORD_WITHOUT_TYPE), WifiQr.read("WIFI:S:Casa;P:clave;;"))
        assertEquals(WifiRead.Unsupported("Casa", "nopass", "clave", false, WifiNotKept.PASSWORD_WITHOUT_TYPE), WifiQr.read("WIFI:T:nopass;S:Casa;P:clave;;"))
        // «H:» that is not a boolean (ZXing reads it as an enterprise setting): not kept. An empty one is «false».
        assertEquals(WifiRead.Unsupported("Casa", "WPA", "clave", false, WifiNotKept.SETTING), WifiQr.read("WIFI:T:WPA;S:Casa;P:clave;H:MSCHAPV2;;"))
        // A space around the value of «T:» is not trimmed (ZXing keeps it): a type Clavero does not keep.
        assertEquals(WifiRead.Unsupported("Casa", " WPA", "clave", false, WifiNotKept.SECURITY), WifiQr.read("WIFI:T: WPA;S:Casa;P:clave;;"))
        assertEquals(wpa("Casa", "clave"), WifiQr.parse("WIFI:T:WPA;S:Casa;P:clave;H:;;"))
        // An empty type is no type.
        assertEquals(WifiConfig("Casa", "", "nopass", false), WifiQr.parse("WIFI:T:;S:Casa;;"))
    }

    @Test fun anEmptyOrMissingNameIsRefused() {
        for (t in listOf("WIFI:T:WPA;S:;P:x;;", "WIFI:T:WPA;S:\"\";P:x;;", "WIFI:T:WPA;P:x;;", "WIFI:", "WIFI:;;")) {
            assertNull(t, WifiQr.parse(t))
            assertEquals(t, WifiRead.Malformed, WifiQr.read(t))
        }
        // A name of spaces is a name, but the form would not keep it as it is (m-3): shown, not kept.
        assertEquals(WifiRead.Unsupported(" ", "nopass", "", false, WifiNotKept.NAME_SPACES), WifiQr.read("WIFI:S: ;;"))
    }

    @Test fun aMissingTerminatorIsTolerated() {
        assertEquals(wpa("Casa", "clave"), WifiQr.parse("WIFI:T:WPA;S:Casa;P:clave"))
        assertEquals(wpa("Casa", "clave"), WifiQr.parse("WIFI:T:WPA;S:Casa;P:clave;"))
        // Spaces and line breaks after the closing «;;» are not part of it.
        assertEquals(wpa("Casa", "clave"), WifiQr.parse("WIFI:T:WPA;S:Casa;P:clave;;\n "))
        // Without «;;», what follows is the last value's, as for ZXing: a name ending in a line break is not kept (m-3).
        assertEquals(WifiRead.Unsupported("Casa\n", "nopass", "", false, WifiNotKept.NAME_SPACES), WifiQr.read("WIFI:S:Casa\n"))
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
            // I-2: ZXing looks for «S:», «P:», «T:» and «H:» anywhere in the text: one that does not start a field means
            // another reader may see another network or a password where Clavero sees none.
            "WIFI:T:WPA;S:Casa;XP:secreta;;", "WIFI:T:WPA;PS:otra;S:Casa;P:clave;;", "WIFI:T:WPA;S:xP:mala;P:buena;;",
            "WIFI:T:WPA;S:Casa;P:a\\;S:otra;;", "WIFI:T:WPA;S:Casa\\T:WEP;;", "WIFI:S:Casa;SH:true;;",
            // The prefix and the known keys as ZXing reads them: in capitals, at the very start, nothing before.
            "wifi:T:WPA;S:Casa;P:clave;;", "Wifi:S:Casa;;", "WIFI:t:WPA;S:Casa;;", "WIFI:s:Casa;;", "WIFI:S:Casa;p:clave;;",
            "WIFI:S:Casa;h:true;;", " WIFI:S:Casa;;", "\uFEFFWIFI:S:Casa;;", "\u200BWIFI:S:Casa;;", "\nWIFI:S:Casa;;",
            "WIFI:S:a; S:b;;", "WIFI:S:Casa ;;\u200B",
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
                if (WifiQr.problem(read.config) == null) assertEquals(t, read.config, WifiQr.parse(WifiQr.encode(read.config)))
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
        // m-2: a length that can only be a raw key is not quoted. WPA: 64 hexadecimal digits (Android's documentation of
        // WifiConfiguration.preSharedKey: «a string of 64 hex digits for raw PSK»; a passphrase is 8 to 63 characters).
        // WEP: 10, 26, 32 or 58 (the keys of 40, 104, 128 and 232 bits, whose text forms are 5, 13, 16 and 29 characters).
        val raw = "0123456789abcdef".repeat(4)
        assertEquals("WIFI:T:WPA;S:Casa;P:$raw;H:false;;", WifiQr.encode(wpa("Casa", raw)))
        assertEquals("WIFI:T:WPA;S:Casa;P:\"${raw.dropLast(1)}\";H:false;;", WifiQr.encode(wpa("Casa", raw.dropLast(1))))
        assertEquals("WIFI:T:WPA;S:Casa;P:\"${raw}0\";H:false;;", WifiQr.encode(wpa("Casa", raw + "0")))
        for (n in 1..64) {
            val key = raw.take(n)
            val quoted = WifiQr.encode(WifiConfig("Casa", key, "WEP", false)).contains("P:\"$key\";")
            assertEquals("WEP $n", n !in setOf(10, 26, 32, 58), quoted)
            assertEquals(WifiConfig("Casa", key, "WEP", false), WifiQr.parse(WifiQr.encode(WifiConfig("Casa", key, "WEP", false))))
        }
        // The name is always quoted when it is hexadecimal: an SSID has no raw form here.
        assertEquals("WIFI:T:WPA;S:\"$raw\";P:x;H:false;;", WifiQr.encode(wpa(raw, "x")))
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

    @Test fun aNetworkWithAPasswordTypeButNoPasswordIsATextNotACode() {
        // m-1: WPA or WEP with nothing to join with: no code, and the screen says what is missing.
        for (security in listOf("WPA", "WEP")) {
            val c = WifiConfig("Casa", "", security, false)
            assertEquals(security, WIFI_QR_NO_PASSWORD, WifiQr.problem(c))
            assertEquals(listOf(WIFI_QR_NO_PASSWORD), wifiQrLines(c))
            try {
                WifiQr.encode(c)
                fail(security)
            } catch (e: IllegalArgumentException) {
                assertEquals(WIFI_QR_NO_PASSWORD, e.message)
            }
        }
        // Read, it is still a network the vault can hold (the password can be written in «Editar»).
        assertEquals(wpa("Casa", ""), WifiQr.parse("WIFI:T:WPA;S:Casa;;"))
    }

    @Test fun anUnknownSecurityIsNeverWrittenAsItIs() {
        // A copy made elsewhere can bring any text there: written as it is, «WPA;S:otra» would add a field of its own. The
        // form keeps WPA2 as «WPA», so a stored «WPA2» is one of these too.
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
        assertEquals(listOf(WIFI_QR_NO_PASSWORD), wifiQrLines(wpa("Casa", "")))
        assertEquals(listOf(WIFI_QR_NO_SECURITY), wifiQrLines(WifiConfig("Casa", "clave", "WPA2", false)))
        assertEquals(listOf(WIFI_QR_TOO_BIG), wifiQrLines(wpa("Casa", "x".repeat(WIFI_QR_MAX_CHARS))))
    }

    // ---- What starts like a wifi code: a secret, whatever the reader finds in it ----

    @Test fun whatStartsLikeAWifiCodeIsSeenSoWithoutReadingIt() {
        for (t in listOf("WIFI:", "wifi:x", " WIFI:S:a;;", "\uFEFFWIFI:", "\u200B\u202EWiFi:", "\n\tWIFI:S:" + "x".repeat(10_000))) {
            assertTrue(t.take(12), WifiQr.startsLikeWifi(t))
            assertTrue(t.take(12), WifiQr.read(t) != null)
        }
        for (t in listOf("", "WIFI", "WIF:S:a;;", "xWIFI:S:a;;", "https://x/WIFI:S:a;;", "Clave WIFI:S:a;;")) {
            assertFalse(t, WifiQr.startsLikeWifi(t))
            assertNull(t, WifiQr.read(t))
        }
    }

    // ---- What never shows the password ----

    @Test fun toStringNeverCarriesThePassword() {
        val c = wpa("Casa", "clave-secreta")
        assertFalse("clave-secreta" in c.toString())
        assertFalse("clave-secreta" in WifiRead.Usable(c).toString())
        assertFalse("clave-secreta" in WifiRead.Unsupported("Casa", "WPA3", "clave-secreta", false, WifiNotKept.SECURITY).toString())
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
