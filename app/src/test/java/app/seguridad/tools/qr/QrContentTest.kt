package app.seguridad.tools.qr

import app.seguridad.tools.QR_TOO_BIG
import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class QrContentTest {
    private val epc = "BCD\n002\n1\nSCT\nBFSWDE33BER\nWikimedia Foerdergesellschaft\nDE33100205000001194700\nEUR123.45\n\n\n" +
        "Spende fuer Wikipedia\n"

    @Test
    fun wifiWithEscapesAndHidden() {
        assertEquals(
            QrKind.Wifi("Casa", "WPA", "clave;con", true),
            classifyQr("WIFI:T:WPA;S:Casa;P:clave\\;con;H:true;;"),
        )
        assertEquals(
            QrKind.Wifi("a;b,c:d\\e", "WPA", "p\\q:r,s;t", false),
            classifyQr("WIFI:T:WPA;S:a\\;b\\,c\\:d\\\\e;P:p\\\\q\\:r\\,s\\;t;;"),
        )
    }

    @Test
    fun wifiOpenAndWep() {
        assertEquals(QrKind.Wifi("Bar", "nopass", null, false), classifyQr("WIFI:T:nopass;S:Bar;;"))
        assertEquals(QrKind.Wifi("Bar", "nopass", null, false), classifyQr("WIFI:S:Bar;;"))
        assertEquals(QrKind.Wifi("Viejo", "WEP", "12345", false), classifyQr("WIFI:T:WEP;S:Viejo;P:12345;;"))
        assertEquals(QrKind.Wifi("x", "WPA", "y", false), classifyQr("wifi:t:wpa;S:x;P:y;;"))
    }

    @Test
    fun wifiQuotedValuesAndMissingSsid() {
        assertEquals(QrKind.Wifi("1234", "WPA", "5678", false), classifyQr("WIFI:T:WPA;S:\"1234\";P:\"5678\";;"))
        assertEquals(QrKind.PlainText("WIFI:T:WPA;P:abc;;"), classifyQr("WIFI:T:WPA;P:abc;;"))
        assertEquals(QrKind.PlainText("WIFI:"), classifyQr("WIFI:"))
    }

    @Test
    fun sepaEpc() {
        assertEquals(
            QrKind.Sepa("Wikimedia Foerdergesellschaft", "DE33100205000001194700", "EUR123.45", "Spende fuer Wikipedia"),
            classifyQr(epc),
        )
        // CRLF line ends and no amount or reference.
        assertEquals(
            QrKind.Sepa("Ana", "ES9121000418450200051332", null, null),
            classifyQr("BCD\r\n001\r\n1\r\nSCT\r\n\r\nAna\r\nES9121000418450200051332\r\n"),
        )
        // The structured reference wins over the free text.
        assertEquals(
            "RF18539007547034",
            (classifyQr("BCD\n002\n1\nSCT\n\nAna\nES9121000418450200051332\nEUR1\n\nRF18539007547034\ntexto\n") as QrKind.Sepa).reference,
        )
    }

    @Test
    fun notAnEpc() {
        assertEquals(QrKind.PlainText("BCD"), classifyQr("BCD"))
        assertEquals(QrKind.PlainText("BCD\n002\n1\nSCT"), classifyQr("BCD\n002\n1\nSCT"))
        assertEquals(QrKind.PlainText("BCD\n002\n1\nXXX\n\nAna\nES91\n"), classifyQr("BCD\n002\n1\nXXX\n\nAna\nES91\n"))
        assertEquals(QrKind.PlainText("BCDE\n1\n2\nSCT\n\nA\nB"), classifyQr("BCDE\n1\n2\nSCT\n\nA\nB"))
    }

    @Test
    fun cryptoPayments() {
        assertEquals(
            QrKind.Crypto("bitcoin", "bc1qar0srrr7xfkvy5l643lydnw9re59gtzzwf5mdq", "0.01"),
            classifyQr("bitcoin:bc1qar0srrr7xfkvy5l643lydnw9re59gtzzwf5mdq?amount=0.01&label=Ana"),
        )
        assertEquals(
            QrKind.Crypto("bitcoin", "bc1qar0srrr7xfkvy5l643lydnw9re59gtzzwf5mdq", null),
            classifyQr("BITCOIN:bc1qar0srrr7xfkvy5l643lydnw9re59gtzzwf5mdq"),
        )
        assertEquals(
            QrKind.Crypto("ethereum", "0x32Be343B94f860124dC4fEe278FDCBD38C102D88", "1000000 wei"),
            classifyQr("ethereum:0x32Be343B94f860124dC4fEe278FDCBD38C102D88?value=1000000"),
        )
        assertEquals(QrKind.Crypto("lightning", "lnbc1pvjluezpp5qqqsyq", null), classifyQr("lightning:lnbc1pvjluezpp5qqqsyq"))
        assertEquals(QrKind.PlainText("bitcoin:"), classifyQr("bitcoin:"))
    }

    @Test
    fun contacts() {
        val vcard = "BEGIN:VCARD\r\nVERSION:3.0\r\nN:Lopez;Ana;;;\r\nFN:Ana Lopez\r\nTEL;TYPE=CELL:600111222\r\n" +
            "item1.TEL:+34 910 000 000\r\nEMAIL;TYPE=INTERNET:ana@example.com\r\nEND:VCARD"
        assertEquals(
            QrKind.Contact("Ana Lopez", listOf("600111222", "+34 910 000 000"), listOf("ana@example.com")),
            classifyQr(vcard),
        )
        // Without FN, N gives «Given Family»; folded lines are joined.
        assertEquals(
            QrKind.Contact("Ana Lopez", listOf("6001112"), emptyList()),
            classifyQr("BEGIN:VCARD\nN:Lopez;Ana\nTEL:600\n 1112\nEND:VCARD"),
        )
        assertEquals(QrKind.Contact("Ana", listOf("600"), emptyList()), classifyQr("MECARD:N:Ana;TEL:600;;"))
        assertEquals(
            QrKind.Contact("Ana Lopez", listOf("600", "700"), listOf("a@b.es")),
            classifyQr("MECARD:N:Lopez,Ana;TEL:600;TEL:700;EMAIL:a@b.es;;"),
        )
        assertEquals(QrKind.Contact("A;B", emptyList(), emptyList()), classifyQr("MECARD:N:A\\;B;;"))
    }

    @Test
    fun smsPhoneEmailGeo() {
        assertEquals(QrKind.Sms("600", "hola"), classifyQr("SMSTO:600:hola"))
        assertEquals(QrKind.Sms("600", "hola: qué tal"), classifyQr("smsto:600:hola: qué tal"))
        assertEquals(QrKind.Sms("600", ""), classifyQr("SMSTO:600"))
        assertEquals(QrKind.Sms("600", "hola"), classifyQr("sms:600?body=hola"))
        assertEquals(QrKind.Sms("+34600", "hola mundo"), classifyQr("sms:+34600?body=hola%20mundo"))
        assertEquals(QrKind.Sms("600", ""), classifyQr("sms:600"))
        assertEquals(QrKind.Phone("+34600111222"), classifyQr("tel:+34600111222"))
        assertEquals(QrKind.Phone("+34600111222"), classifyQr("TEL:%2B34600111222"))
        assertEquals(QrKind.Email("ana@example.com", "", ""), classifyQr("mailto:ana@example.com"))
        assertEquals(
            QrKind.Email("ana@example.com", "Hola", "Qué tal"),
            classifyQr("mailto:ana@example.com?subject=Hola&body=Qu%C3%A9%20tal"),
        )
        assertEquals(QrKind.Email("a@b.es", "S", "B: c"), classifyQr("MATMSG:TO:a@b.es;SUB:S;BODY:B: c;;"))
        assertEquals(QrKind.Geo("40.4", "-3.7"), classifyQr("geo:40.4,-3.7"))
        assertEquals(QrKind.Geo("40.4", "-3.7"), classifyQr("geo:40.4,-3.7,650?q=Madrid"))
        assertEquals(QrKind.PlainText("geo:abc"), classifyQr("geo:abc"))
        assertEquals(QrKind.PlainText("geo:91,0"), classifyQr("geo:91,0"))
    }

    @Test
    fun badPercentEscapesDoNotThrow() {
        assertEquals(QrKind.Sms("600", "100%"), classifyQr("sms:600?body=100%"))
        assertEquals(QrKind.Sms("600", "a%zzb"), classifyQr("sms:600?body=a%zzb"))
    }

    @Test
    fun seguridadTexts() {
        assertEquals(QrKind.SeguridadCard("SGK1:abc"), classifyQr("SGK1:abc"))
        assertEquals(QrKind.SeguridadReceived("SGM1:abc"), classifyQr("SGM1:abc"))
        assertEquals(QrKind.SeguridadReceived("hola\nSGS1:abc"), classifyQr("hola\nSGS1:abc"))
        // A card's Base45 body can carry «SGM1:» by chance; a text that starts with SGK1: stays a card.
        assertEquals(QrKind.SeguridadCard("SGK1:AB SGM1:CD"), classifyQr("SGK1:AB SGM1:CD"))
        // The last prefix decides, as in the app's own reader.
        assertEquals(QrKind.SeguridadCard("SGM1:abc\nSGK1:def"), classifyQr("SGM1:abc\nSGK1:def"))
        assertEquals(QrKind.SeguridadReceived("x\nSGK1:abc\nSGM1:def"), classifyQr("x\nSGK1:abc\nSGM1:def"))
    }

    @Test
    fun otpAuth() {
        val uri = "otpauth://totp/Banco:ana?secret=JBSWY3DPEHPK3PXP&issuer=Banco"
        assertEquals(QrKind.OtpAuth(uri), classifyQr(uri))
        val mig = "otpauth-migration://offline?data=CjEK"
        assertEquals(QrKind.OtpAuth(mig), classifyQr(mig))
        assertEquals(QrKind.OtpAuth("OTPAUTH://totp/x?secret=AB"), classifyQr("OTPAUTH://totp/x?secret=AB"))
    }

    @Test
    fun linkClassification() {
        val l = classifyQr("https://banco.es:8443/ruta") as QrKind.Link
        assertEquals("https://banco.es:8443/ruta", l.uri)
        assertEquals("https", l.scheme)
        assertEquals("banco.es", l.host)
        assertEquals(8443, l.port)
        // Trailing line break from some scanners.
        assertEquals("https://banco.es", (classifyQr("https://banco.es\n") as QrKind.Link).uri)
        assertTrue(classifyQr("javascript:alert(1)") is QrKind.Link)
        assertTrue(classifyQr("ftp://x.es/f") is QrKind.Link)
        assertTrue(classifyQr("upi://pay?pa=a@b") is QrKind.Link)
    }

    @Test
    fun anythingElseIsPlainText() {
        for (t in listOf("hola", "", "   ", "Nota: comprar pan", "C:\\Users\\ana", "www", "www.", "www.a b.es", "hora:10:30", "https", "1:2", "a b:c", "@@@")) {
            assertEquals(t, QrKind.PlainText(t), classifyQr(t))
        }
    }

    @Test
    fun tooBigThrowsTheFixedText() {
        assertTrue(classifyQr("a".repeat(MAX_QR_TEXT_BYTES)) is QrKind.PlainText)
        try {
            classifyQr("a".repeat(MAX_QR_TEXT_BYTES + 1))
            fail()
        } catch (e: IllegalArgumentException) {
            assertEquals(QR_TOO_BIG, e.message)
        }
        // Counted in UTF-8 bytes: 2049 two-byte characters are 4098 bytes.
        try {
            classifyQr("é".repeat(2049))
            fail()
        } catch (e: IllegalArgumentException) {
            assertEquals(QR_TOO_BIG, e.message)
        }
        assertTrue(classifyQr("é".repeat(2048)) is QrKind.PlainText)
        // Supplementary characters: 4 bytes each.
        try {
            classifyQr("\uD83D\uDE00".repeat(1025))
            fail()
        } catch (e: IllegalArgumentException) {
            assertEquals(QR_TOO_BIG, e.message)
        }
    }

    @Test
    fun maxBytesIs4096() {
        assertEquals(4096, MAX_QR_TEXT_BYTES)
    }

    @Test
    fun visibleTextMarksWhatCannotBeSeen() {
        assertEquals("a\u27E8U+202E\u27E9b", visibleText("a\u202Eb"))
        assertEquals("a\u27E8U+200B\u27E9\u27E8U+200D\u27E9b", visibleText("a\u200B\u200Db"))
        assertEquals("\u27E8U+2060\u27E9\u27E8U+FEFF\u27E9", visibleText("\u2060\uFEFF"))
        assertEquals("\u27E8U+0000\u27E9\u27E8U+0007\u27E9\u27E8U+0009\u27E9\u27E8U+000D\u27E9\u27E8U+009F\u27E9", visibleText("\u0000\u0007\t\r\u009F"))
        // Every bidi control, taken from the app's own list.
        for (c in listOf('\u202A', '\u202B', '\u202C', '\u202D', '\u202E', '\u2066', '\u2067', '\u2068', '\u2069', '\u200E', '\u200F', '\u061C')) {
            assertEquals("\u27E8U+%04X\u27E9".format(c.code), visibleText(c.toString()))
        }
    }

    @Test
    fun visibleTextKeepsNewlinesAndOrdinaryText() {
        assertEquals("línea 1\nlínea 2 ñ é 😀 日本", visibleText("línea 1\nlínea 2 ñ é 😀 日本"))
        assertEquals("", visibleText(""))
    }

    @Test
    fun visibleTextHandlesSupplementaryCodePoints() {
        // U+E0001 LANGUAGE TAG and U+E0041 (tag A), invisible: marked with five digits.
        assertEquals("a\u27E8U+E0001\u27E9\u27E8U+E0041\u27E9b", visibleText("a\uDB40\uDC01\uDB40\uDC41b"))
        assertEquals("\u27E8U+E007F\u27E9", visibleText(String(Character.toChars(0xE007F))))
        // A visible emoji is left alone, and a lone surrogate does not throw.
        assertEquals("😀", visibleText("😀"))
        visibleText("a\uD800b")
        visibleText("\uDC00")
    }

    @Test
    fun hasHiddenChars() {
        assertTrue(hasHiddenChars("a\u202Eb"))
        assertTrue(hasHiddenChars("a\u200Bb"))
        assertTrue(hasHiddenChars("a\tb"))
        assertTrue(hasHiddenChars("a\uDB40\uDC01b"))
        assertFalse(hasHiddenChars("a\nb"))
        assertFalse(hasHiddenChars("hola ñ 😀"))
        assertFalse(hasHiddenChars(""))
    }

    private val alphabet: List<String> = listOf(
        "https://", "http://", "javascript:", "WIFI:", "T:", "S:", "P:", "H:", "BCD\n", "SCT", "bitcoin:", "ethereum:", "lightning:",
        "BEGIN:VCARD", "END:VCARD", "MECARD:", "N:", "TEL:", "EMAIL:", "SMSTO:", "sms:", "tel:", "mailto:", "MATMSG:", "geo:",
        "SGK1:", "SGM1:", "SGS1:", "otpauth://", "otpauth-migration://", "xn--", "[", "]", "::", "@", ":", ";", "\\", "/", "?", "&",
        "=", "%", "%2", "%zz", ".", "..", "-", "#", "\n", "\r\n", "\t", " ", "\u0000", "\u0007", "\u202E", "\u2066", "\u200B", "\uFEFF",
        "\u0430", "\u03BF", "\u3002", "\uD83D", "\uDE00", "\uDB40\uDC01", "é", "日", "a", "b", "0", "1", "443", "99999999999", "bit.ly",
    )

    private fun randomText(rnd: Random): String {
        val n = rnd.nextInt(12)
        val sb = StringBuilder()
        repeat(n) {
            if (rnd.nextInt(6) == 0) sb.append(rnd.nextInt(0x10000).toChar()) else sb.append(alphabet[rnd.nextInt(alphabet.size)])
        }
        return sb.toString()
    }

    @Test
    fun fuzzNeverThrowsAnythingButTheSizeError() {
        val rnd = Random(20261027L)
        repeat(20_000) {
            val t = randomText(rnd)
            // The parsers themselves are total: the guard in classifyQr is a second line, not the first.
            classifyQrUnguarded(t)
            val kind = classifyQr(t)
            if (kind is QrKind.Link) {
                linkSignals(kind.uri)
                assertEquals(kind.signals, linkSignals(kind.uri))
            }
            visibleText(t)
            hasHiddenChars(t)
            linkSignals(t)
        }
    }

    @Test
    fun fuzzFromRandomCharsAndLongInputs() {
        val rnd = Random(7L)
        repeat(3_000) {
            val len = rnd.nextInt(200)
            val sb = StringBuilder()
            repeat(len) { sb.append(rnd.nextInt(0x3000).toChar()) }
            val t = sb.toString()
            classifyQrUnguarded(t)
            classifyQr(t)
        }
        // A 4 KB text of repeated delimiters.
        for (piece in listOf(";", ":", "\\", "%", "\n", "@", "[", ".", "xn--", "\u202E")) {
            val t = piece.repeat(MAX_QR_TEXT_BYTES / piece.length)
            for (prefix in listOf("", "WIFI:", "https://", "BEGIN:VCARD\n", "MECARD:", "mailto:", "sms:", "BCD\n", "bitcoin:")) {
                val text = prefix + t.take(MAX_QR_TEXT_BYTES - prefix.length)
                // The parsers prove they never throw on their own, without the catch-all or the size check.
                classifyQrUnguarded(text)
                // Over the limit in bytes (a 3-byte character): the size error, and only that, is what comes out.
                try {
                    classifyQr(text)
                } catch (e: IllegalArgumentException) {
                    assertEquals(QR_TOO_BIG, e.message)
                }
            }
        }
    }

    @Test
    fun ethereumTokenTransferShowsTheRecipientNotTheContract() {
        val token = "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48"
        val recipient = "0x32Be343B94f860124dC4fEe278FDCBD38C102D88"
        // ERC-681: the contract is in front, the person paid is in the query; the amount is in token units (unknown).
        assertEquals(
            QrKind.Crypto("ethereum", recipient, null),
            classifyQr("ethereum:$token@1/transfer?address=$recipient&uint256=1000000"),
        )
        assertEquals(
            QrKind.Crypto("ethereum", recipient, null),
            classifyQr("ethereum:pay-$token@1/TRANSFER?address=$recipient&uint256=1"),
        )
        // Without a recipient in the query the contract is all there is; a chain id is not part of the address.
        assertEquals(QrKind.Crypto("ethereum", token, null), classifyQr("ethereum:$token@1/transfer?uint256=1"))
        assertEquals(QrKind.Crypto("ethereum", token, "5 wei"), classifyQr("ethereum:$token@1?value=5"))
        assertEquals(QrKind.Crypto("ethereum", token, null), classifyQr("ethereum:$token@1/approve?address=$recipient&uint256=1"))
        assertEquals(QrKind.PlainText("ethereum:"), classifyQr("ethereum:"))
    }

    @Test
    fun aLinkKeepsBeingALinkEvenWithASignedTextMarkerInIt() {
        // A first line that is a web address wins over an «SGS1:» or «SGM1:» further down: it is shown as a link, with
        // its signals (the line break inside raises HIDDEN_CHARS).
        for (marker in listOf("SGS1:abc", "SGM1:abc")) {
            val k = classifyQr("https://banco.es/x\n$marker")
            assertTrue(k.toString(), k is QrKind.Link)
            assertEquals("banco.es", (k as QrKind.Link).host)
            assertTrue(Signal.HIDDEN_CHARS in k.signals)
        }
        assertTrue(classifyQr("https://banco.es/SGM1:abc") is QrKind.Link)
    }

    @Test
    fun wwwWithoutSchemeIsALinkWithTheSignalsOfHttps() {
        val l = classifyQr("www.banco.es/ruta") as QrKind.Link
        assertEquals("www.banco.es/ruta", l.uri)
        assertEquals("", l.scheme)
        assertEquals("www.banco.es", l.host)
        assertEquals(emptyList<Signal>(), l.signals)
        assertEquals(8443, (classifyQr("WWW.banco.es:8443") as QrKind.Link).port)
        assertTrue(Signal.ODD_PORT in (classifyQr("www.banco.es:8443") as QrKind.Link).signals)
        assertEquals(listOf(Signal.MIXED_SCRIPTS, Signal.PUNYCODE), (classifyQr("www.b\u0430nco.es") as QrKind.Link).signals)
        assertTrue(Signal.HIDDEN_CHARS in (classifyQr("www.banco.es/r\u202Eta") as QrKind.Link).signals)
        assertTrue(Signal.SHORTENER in (classifyQr("www.bit.ly/x") as QrKind.Link).signals)
        // Not a link: a sentence, or too short to be a name.
        assertTrue(classifyQr("www.banco.es y mas") is QrKind.PlainText)
        assertTrue(classifyQr("www.") is QrKind.PlainText)
    }

    @Test
    fun visibleTextMarksTheOtherInvisibleCharacters() {
        for (cp in listOf(0x00AD, 0x115F, 0x1160, 0x3164, 0xFFA0, 0x2061, 0x2062, 0x2063, 0x2064, 0x180E, 0x2028, 0x2029, 0xFE00, 0xFE0F, 0xE0100, 0xE01EF)) {
            val s = String(Character.toChars(cp))
            assertEquals("\u27E8U+%04X\u27E9".format(cp), visibleText("a${s}b").removePrefix("a").removeSuffix("b"))
            assertTrue(cp.toString(), hasHiddenChars(s))
        }
        // Just outside the ranges.
        for (cp in listOf(0x00AC, 0x00AE, 0x2065, 0xFE10, 0xE00FF, 0xE01F0)) {
            val s = String(Character.toChars(cp))
            assertEquals(cp.toString(), s, visibleText(s))
        }
    }
}
