package app.seguridad.tools.qr

import java.net.IDN
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkSignalsTest {
    private fun link(text: String) = classifyQr(text) as QrKind.Link

    @Test
    fun cyrillicAInsideALatinLabelIsMixedScripts() {
        // «bаnco»: the second letter is U+0430 CYRILLIC SMALL LETTER A.
        // The browser connects to the xn-- form, so PUNYCODE comes with it.
        assertEquals(listOf(Signal.MIXED_SCRIPTS, Signal.PUNYCODE), linkSignals("https://b\u0430nco.es"))
        assertEquals(listOf(Signal.MIXED_SCRIPTS, Signal.PUNYCODE), link("https://b\u0430nco.es").signals)
    }

    @Test
    fun greekMixedWithLatinIsMixedScripts() {
        // omicron (U+03BF) for «o»
        assertEquals(listOf(Signal.MIXED_SCRIPTS, Signal.PUNYCODE), linkSignals("https://g\u03BFogle.com"))
    }

    @Test
    fun aWholeLabelInOneForeignScriptIsNotMixedButIsPunycode() {
        // «банк.es»: each label in a single script is a legitimate IDN, so no MIXED_SCRIPTS; but the browser connects
        // to an xn-- name, and a whole-script lookalike («аррӏе.com», all Cyrillic) is only visible through that.
        assertEquals(listOf(Signal.PUNYCODE), linkSignals("https://\u0431\u0430\u043D\u043A.es"))
        assertEquals(listOf(Signal.PUNYCODE), linkSignals("https://\u0430\u0440\u0440\u04CF\u0435.com"))
        val l = link("https://\u0430\u0440\u0440\u04CF\u0435.com")
        assertTrue(l.host!!.startsWith("xn--"))
        assertEquals("\u0430\u0440\u0440\u04CF\u0435.com", l.unicodeHost)
    }

    @Test
    fun scriptsWrittenTogetherInOneLanguageAreNotMixed() {
        assertFalse(Signal.MIXED_SCRIPTS in linkSignals("https://\u65E5\u672C\u8A9E\u306E\u30B5\u30A4\u30C8.jp")) // Han + Hiragana + Katakana
        assertFalse(Signal.MIXED_SCRIPTS in linkSignals("https://\uD55C\uAD6D\u6F22\u5B57.kr")) // Hangul + Han
        assertFalse(Signal.MIXED_SCRIPTS in linkSignals("https://\u3105\u3106\u4E2D.tw")) // Bopomofo + Han
        // Not the same language: still mixed.
        assertTrue(Signal.MIXED_SCRIPTS in linkSignals("https://\u3042\uD55C.jp")) // Hiragana + Hangul
        assertTrue(Signal.MIXED_SCRIPTS in linkSignals("https://\u65E5b.jp")) // Han + Latin
        assertTrue(Signal.MIXED_SCRIPTS in linkSignals("https://\u65E5\u0430.jp")) // Han + Cyrillic
        assertTrue(Signal.MIXED_SCRIPTS in linkSignals("https://b\u0430nco.es")) // Latin + Cyrillic
    }

    @Test
    fun digitsAndHyphensDoNotCountAsAScript() {
        assertEquals(emptyList<Signal>(), linkSignals("https://banco-24.es"))
        assertEquals(listOf(Signal.PUNYCODE), linkSignals("https://\u0431\u0430\u043D\u043A-24.es"))
    }

    @Test
    fun mixedScriptsAreFoundInsideAPunycodeLabelToo() {
        val ascii = IDN.toASCII("b\u0430nco.es")
        assertTrue(ascii.startsWith("xn--"))
        val signals = linkSignals("https://$ascii")
        assertTrue(Signal.PUNYCODE in signals)
        assertTrue(Signal.MIXED_SCRIPTS in signals)
    }

    @Test
    fun punycodeLabelAndItsUnicodeForm() {
        val l = link("https://xn--bnco-ofa.es")
        assertTrue(Signal.PUNYCODE in l.signals)
        assertEquals("xn--bnco-ofa.es", l.host)
        assertEquals(IDN.toUnicode("xn--bnco-ofa.es", IDN.ALLOW_UNASSIGNED), l.unicodeHost)
        assertTrue(l.unicodeHost != l.host)
        assertTrue(Signal.PUNYCODE in linkSignals("https://XN--bnco-ofa.es"))
    }

    @Test
    fun plainHostHasTheSameUnicodeHost() {
        val l = link("https://banco.es/ruta")
        assertEquals("banco.es", l.host)
        assertEquals("banco.es", l.unicodeHost)
    }

    @Test
    fun ipHosts() {
        assertTrue(Signal.IP_HOST in linkSignals("https://192.168.1.1/login"))
        assertTrue(Signal.IP_HOST in linkSignals("https://[2001:db8::1]/"))
        assertTrue(Signal.IP_HOST in linkSignals("https://[::1]:8443/"))
        assertTrue(Signal.IP_HOST in linkSignals("http://3232235777"))
        // The browser also reads these as addresses.
        assertTrue(Signal.IP_HOST in linkSignals("https://127.1/"))
        assertTrue(Signal.IP_HOST in linkSignals("https://0x7f.0.0.1/"))
        // A name that merely contains digits is not.
        assertFalse(Signal.IP_HOST in linkSignals("https://banco24.es"))
        assertFalse(Signal.IP_HOST in linkSignals("https://1banco.es"))
    }

    @Test
    fun ipHostFields() {
        val l = link("https://[2001:DB8::1]:8443/x")
        assertEquals("[2001:db8::1]", l.host)
        assertEquals(8443, l.port)
        assertEquals(setOf(Signal.IP_HOST, Signal.ODD_PORT), l.signals.toSet())
    }

    @Test
    fun plainHttp() {
        assertEquals(listOf(Signal.PLAIN_HTTP), linkSignals("http://banco.es"))
        assertEquals(listOf(Signal.PLAIN_HTTP), linkSignals("HTTP://banco.es"))
        assertFalse(Signal.PLAIN_HTTP in linkSignals("https://banco.es"))
    }

    @Test
    fun userInfoHidesTheRealHost() {
        val l = link("https://banco.es@evil.com")
        assertEquals("evil.com", l.host)
        assertEquals(listOf(Signal.USERINFO), l.signals)
        // The last «@» ends the userinfo.
        assertEquals("evil.com", link("https://a@b@evil.com/x").host)
        // «banco.es:443» before the «@» is a user and password, not a port.
        val p = link("https://banco.es:443@evil.com/")
        assertEquals("evil.com", p.host)
        assertNull(p.port)
        assertEquals(listOf(Signal.USERINFO), p.signals)
    }

    @Test
    fun backslashEndsTheAuthorityLikeABrowserReadsIt() {
        // Browsers treat «\» as «/» in http(s): the host here is banco.es, and «@evil.com» is part of the path.
        val l = link("https://banco.es\\@evil.com")
        assertEquals("banco.es", l.host)
        assertFalse(Signal.USERINFO in l.signals)
        // Missing or extra slashes still reach the same host in a browser.
        assertEquals("evil.com", link("http:evil.com").host)
        assertEquals("evil.com", link("https:\\\\evil.com").host)
        assertEquals("evil.com", link("https:///evil.com").host)
    }

    @Test
    fun tabsAndNewlinesInsideAreRemovedLikeABrowserDoes() {
        val l = link("https://banco.es\n.evil.com/")
        assertEquals("banco.es.evil.com", l.host)
        assertTrue(Signal.HIDDEN_CHARS in l.signals)
    }

    @Test
    fun oddPort() {
        assertEquals(listOf(Signal.ODD_PORT), linkSignals("https://banco.es:8443"))
        assertEquals(8443, link("https://banco.es:8443").port)
        assertEquals(emptyList<Signal>(), linkSignals("https://banco.es:443"))
        assertEquals(emptyList<Signal>(), linkSignals("https://banco.es:0443/"))
        assertEquals(emptyList<Signal>(), linkSignals("http://banco.es:80").filter { it != Signal.PLAIN_HTTP })
        assertTrue(Signal.ODD_PORT in linkSignals("http://banco.es:443"))
        assertTrue(Signal.ODD_PORT in linkSignals("https://banco.es:99999999999999"))
        assertEquals(emptyList<Signal>(), linkSignals("https://banco.es:/ruta"))
    }

    @Test
    fun shorteners() {
        assertTrue(Signal.SHORTENER in linkSignals("https://bit.ly/abc"))
        assertTrue(Signal.SHORTENER in linkSignals("https://www.bit.ly/abc"))
        assertTrue(Signal.SHORTENER in linkSignals("https://BIT.LY./abc"))
        assertFalse(Signal.SHORTENER in linkSignals("https://evilbit.ly/abc"))
        assertFalse(Signal.SHORTENER in linkSignals("https://bit.ly.evil.com/abc"))
        for (s in SHORTENERS) assertTrue(s, Signal.SHORTENER in linkSignals("https://$s/x"))
        assertEquals(
            setOf("bit.ly", "t.co", "tinyurl.com", "goo.gl", "ow.ly", "is.gd", "buff.ly", "cutt.ly", "rebrand.ly", "shorturl.at", "t.ly"),
            SHORTENERS,
        )
    }

    @Test
    fun manyLevels() {
        assertEquals(listOf(Signal.MANY_LEVELS), linkSignals("https://a.b.c.d.e.banco.es"))
        // Four subdomains are still fine.
        assertEquals(emptyList<Signal>(), linkSignals("https://a.b.c.d.banco.es"))
        assertEquals(emptyList<Signal>(), linkSignals("https://www.banco.es"))
    }

    @Test
    fun dangerousSchemes() {
        assertEquals(listOf(Signal.DANGEROUS_SCHEME), linkSignals("javascript:alert(1)"))
        assertEquals(listOf(Signal.DANGEROUS_SCHEME), linkSignals("data:text/html;base64,PGI+"))
        assertEquals(listOf(Signal.DANGEROUS_SCHEME), linkSignals("file:///etc/passwd"))
        assertEquals(listOf(Signal.DANGEROUS_SCHEME), linkSignals("intent://scan/#Intent;scheme=zxing;end"))
        assertEquals(listOf(Signal.DANGEROUS_SCHEME), linkSignals("JAVASCRIPT:alert(1)"))
        assertEquals("javascript", link("JavaScript:alert(1)").scheme)
        assertNull(link("javascript:alert(1)").host)
        assertNull(link("file:///etc/passwd").host)
    }

    @Test
    fun aNormalLinkHasNoSignals() {
        assertEquals(emptyList<Signal>(), linkSignals("https://banco.es/ruta"))
        val l = link("https://banco.es/ruta?x=1#y")
        assertEquals("https", l.scheme)
        assertEquals("banco.es", l.host)
        assertNull(l.port)
        assertEquals(emptyList<Signal>(), l.signals)
    }

    @Test
    fun hiddenCharsInThePath() {
        assertEquals(listOf(Signal.HIDDEN_CHARS), linkSignals("https://banco.es/ru\u202Eta"))
        assertEquals(listOf(Signal.HIDDEN_CHARS), linkSignals("https://banco.es/ru\u200Bta"))
        assertEquals(listOf(Signal.HIDDEN_CHARS), linkSignals("https://banco.es/ru\u0007ta"))
        assertEquals(listOf(Signal.HIDDEN_CHARS), linkSignals("https://banco.es/ru\uDB40\uDC01ta")) // U+E0001 tag
    }

    @Test
    fun hostIsLowercaseWithoutTrailingDot() {
        val l = link("https://WWW.Banco.ES./x")
        assertEquals("www.banco.es", l.host)
        // Ideographic full stop and fullwidth dots separate labels in a browser too.
        assertEquals("a.banco.es", link("https://a\u3002banco\uFF0Ees/").host)
    }

    @Test
    fun percentEncodedHostIsDecodedLikeABrowserDoes() {
        assertEquals("banco.es", link("https://%62anco.es/").host)
    }

    @Test
    fun queryAndFragmentEndTheAuthority() {
        assertEquals("banco.es", link("https://banco.es?x=@evil.com").host)
        assertEquals("banco.es", link("https://banco.es#@evil.com").host)
        assertEquals(emptyList<Signal>(), linkSignals("https://banco.es?x=@evil.com"))
    }

    @Test
    fun signalsComeInEnumOrderWithoutRepeats() {
        val s = linkSignals("http://a@xn--bnco-ofa.es:81/\u202E")
        assertEquals(s.sortedBy { it.ordinal }, s)
        assertEquals(s.distinct(), s)
        assertEquals(
            setOf(Signal.PLAIN_HTTP, Signal.USERINFO, Signal.ODD_PORT, Signal.PUNYCODE, Signal.HIDDEN_CHARS),
            s.toSet(),
        )
    }

    @Test
    fun linkSignalsNeverThrowsOnOddInput() {
        for (t in listOf("", ":", "//", "http://", "http://[", "http://]:", "https://@", "https://:80", "x", "\u0000", "http://%zz", "http://%", "http://xn--", "http://xn--a-", "http://...", "http://.", "http://a..b")) {
            linkSignals(t)
        }
    }

    @Test
    fun punycodeThatDoesNotDecodeStillGivesAHost() {
        val l = link("https://xn--zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz--.es")
        assertTrue(Signal.PUNYCODE in l.signals)
    }

    // ---- The host as a browser maps it (UTS #46) ----

    @Test
    fun invisibleCharacterInTheHostDoesNotHideAShortener() {
        // %E2%80%8B is U+200B: the browser drops it and opens bit.ly.
        val l = link("https://bit%E2%80%8B.ly/x")
        assertEquals("bit.ly", l.host)
        assertEquals(setOf(Signal.SHORTENER, Signal.HIDDEN_CHARS), l.signals.toSet())
        // The same, written plainly in the text.
        assertEquals(setOf(Signal.SHORTENER, Signal.HIDDEN_CHARS), linkSignals("https://bit\u200B.ly/x").toSet())
        assertEquals(setOf(Signal.SHORTENER, Signal.HIDDEN_CHARS), linkSignals("https://bi\u00ADt.ly/x").toSet())
    }

    @Test
    fun fullWidthDigitsStillMakeAnIpHost() {
        val l = link("https://192.168.1.\uFF11/login")
        assertEquals("192.168.1.1", l.host)
        assertEquals(listOf(Signal.IP_HOST), l.signals)
    }

    @Test
    fun fullWidthLettersStillMakeAShortener() {
        val l = link("https://\uFF42\uFF49\uFF54.\uFF4C\uFF59/x")
        assertEquals("bit.ly", l.host)
        assertEquals(listOf(Signal.SHORTENER), l.signals)
    }

    @Test
    fun fullWidthXnStillMakesPunycode() {
        val l = link("https://\uFF58\uFF4E--bnco-ofa.es")
        assertEquals("xn--bnco-ofa.es", l.host)
        assertTrue(Signal.PUNYCODE in l.signals)
        // Upper-case and mixed case too.
        assertTrue(Signal.PUNYCODE in linkSignals("https://Xn--bnco-ofa.es"))
    }

    @Test
    fun aLabelTheEncoderRejectsStillCountsAsPunycode() {
        // Over 63 characters in one non-ASCII label: not encodable, but not a plain name either.
        val l = link("https://" + "\u0431".repeat(70) + ".es")
        assertTrue(Signal.PUNYCODE in l.signals)
    }

    @Test
    fun numericHostsShowTheAddressTheBrowserOpens() {
        assertEquals("192.168.1.1", link("http://3232235777").host)
        assertEquals("127.0.0.1", link("http://0x7f.1/").host)
        assertEquals("127.0.0.1", link("http://127.1/").host)
        assertEquals("127.0.0.1", link("http://0x7f.0.0.1/").host)
        assertEquals("192.168.0.1", link("http://0300.0250.0.1/").host)
        assertEquals("127.0.0.1", link("http://0x7f000001/").host)
        assertEquals("1.2.3.4", link("http://1.2.3.4./").host)
        // Not an address a browser accepts: shown as written, still flagged.
        assertEquals("1.2.3.256", link("http://1.2.3.256/").host)
        assertTrue(Signal.IP_HOST in linkSignals("http://1.2.3.256/"))
        assertEquals("999999999999", link("http://999999999999/").host)
        assertEquals("evil.123", link("http://evil.123/").host)
        assertTrue(Signal.IP_HOST in linkSignals("http://evil.123/"))
        assertEquals("1.2.3.4", link("http://1.2.3.4").unicodeHost)
    }

    @Test
    fun onlyOneTrailingDotGoes() {
        assertEquals("bit.ly", link("https://bit.ly./x").host)
        assertEquals("bit.ly.", link("https://bit.ly../x").host)
        assertEquals("bit.ly", link("https://bit.ly\u3002/x").host)
    }

    @Test
    fun thePortStartsAtTheFirstColonAfterTheHost() {
        val l = link("https://a.es:80:90/x")
        assertEquals("a.es", l.host)
        assertNull(l.port)
        assertEquals(emptyList<Signal>(), l.signals)
        assertEquals(8443, link("https://a.es:8443/x").port)
    }

    @Test
    fun ftpsHasNoSpecialSlashHandling() {
        assertNull(link("ftps:evil.com").host)
        assertEquals("evil.com", link("ftps://evil.com").host)
        assertEquals("evil.com", link("ftp:evil.com").host)
    }

    // ---- Where Java's IDNA2003 differs from a browser ----

    @Test
    fun sharpSAndFinalSigmaAreKeptLikeABrowserDoes() {
        // IDN.toASCII("fa\u00DF") is «fass»; a browser opens xn--fa-hia.
        val l = link("https://fa\u00DF.de")
        assertTrue(l.host != "fass.de")
        assertEquals("fa\u00DF.de", l.host)
        assertEquals("fa\u00DF.de", l.unicodeHost)
        assertEquals(listOf(Signal.PUNYCODE), l.signals)
        assertEquals(listOf(Signal.PUNYCODE), linkSignals("https://stra\u00DFe-bank.de"))
        assertTrue(link("https://stra\u00DFe-bank.de").host != "strasse-bank.de")
        // Capital sharp s folds to the lower-case one.
        assertEquals("fa\u00DF.de", link("https://fa\u1E9E.de").host)
        // Final sigma is not turned into a plain sigma.
        val g = link("https://\u03C2\u03B1.gr")
        assertEquals("\u03C2\u03B1.gr", g.host)
        assertEquals(listOf(Signal.PUNYCODE), g.signals)
        // A normal «ss» is nothing.
        assertEquals(emptyList<Signal>(), linkSignals("https://fass.de"))
    }

    @Test
    fun aCapitalSigmaAtTheEndIsAPlainSigmaAsInTheBrowser() {
        // MN (T6 of §2.5): String.lowercase turns a final «Σ» into «ς» (Final_Sigma); a browser (UTS #46) maps «Σ» to «σ»
        // and connects to xn--mxa0b, so the name shown and the one used are the same.
        val l = link("https://\u0391\u03A3.gr")
        assertEquals(IDN.toASCII("\u03B1\u03C3") + ".gr", l.host)
        assertEquals("xn--mxa0b.gr", l.host)
        assertEquals("\u03B1\u03C3.gr", l.unicodeHost)
        assertEquals(listOf(Signal.PUNYCODE), l.signals)
        // The same with a full-width or mixed-case label, and a final sigma written as such stays one.
        assertEquals("xn--mxa0b.gr", link("https://\u03B1\u03A3.gr").host)
        assertEquals("\u03C2\u03B1.gr", link("https://\u03C2\u0391.gr").host)
    }

    @Test
    fun anXnLabelWithSharpSStaysInItsAsciiFormAndIsPunycode() {
        // MN (T6 of §2.5): Java's IDN (IDNA2003) cannot decode «xn--fa-hia» (faß): its check turns «ß» into «ss». The
        // name stays as the browser connects to it, with the punycode signal; no look-alike reading is made up.
        val l = link("https://xn--fa-hia.de/")
        assertEquals("xn--fa-hia.de", l.host)
        assertEquals("xn--fa-hia.de", l.unicodeHost)
        assertEquals(listOf(Signal.PUNYCODE), l.signals)
    }

    @Test
    fun charactersNewerThanUnicode32AreFoldedBeforeEncoding() {
        // Squared letters 🄱🄸🅃 are «bit» after NFKC.
        val l = link("https://\uD83C\uDD31\uD83C\uDD38\uD83C\uDD43.ly/x")
        assertEquals("bit.ly", l.host)
        assertEquals(listOf(Signal.SHORTENER), l.signals)
    }
}
