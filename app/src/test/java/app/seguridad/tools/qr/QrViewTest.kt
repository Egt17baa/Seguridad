package app.seguridad.tools.qr

import app.seguridad.tools.ALL_TOOL_TEXTS
import app.seguridad.tools.QR_CRYPTO
import app.seguridad.tools.QR_HIDDEN_ELSEWHERE
import app.seguridad.tools.QR_HTTP
import app.seguridad.tools.QR_IP_HOST
import app.seguridad.tools.QR_MIXED_SCRIPTS
import app.seguridad.tools.QR_SEPA
import app.seguridad.tools.QR_SGK1
import app.seguridad.tools.QR_SHORTENER
import app.seguridad.tools.QR_SMS
import app.seguridad.tools.QR_SUBDOMAINS
import app.seguridad.tools.QR_TOO_BIG
import app.seguridad.tools.QR_WIFI_OPEN
import app.seguridad.tools.QR_WIFI_WEP
import app.seguridad.tools.qrBidi
import app.seguridad.tools.qrPort
import app.seguridad.tools.qrPunycode
import app.seguridad.tools.qrScheme
import app.seguridad.tools.qrUserInfo
import java.net.IDN
import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class QrViewTest {
    private fun QrView.field(label: String) = fields.firstOrNull { it.label == label }?.value

    @Test
    fun hiddenMarksAreTheOnesTheTextShows() {
        assertEquals(listOf("⟨U+202E⟩", "⟨U+200B⟩"), hiddenMarks("a‮b​c‮"))
        assertEquals(listOf("⟨U+E0041⟩"), hiddenMarks("x󠁁"))
        assertTrue(hiddenMarks("Casa ❤️").isEmpty())
        assertTrue(hiddenMarks("hola\nadiós").isEmpty())
        val r = Random(3)
        val pool = "ab \n‮​️❤­\u0000😀⁦"
        repeat(3000) {
            val t = buildString { repeat(r.nextInt(12)) { append(pool[r.nextInt(pool.length)]) } }
            val marks = hiddenMarks(t)
            assertEquals(t, marks.isEmpty(), !hasHiddenChars(t))
            val shown = visibleText(t)
            marks.forEach { assertTrue("$it in $shown", it in shown) }
        }
    }

    @Test
    fun aLinkWithoutSignalsHasNoAlertsAndShowsItsHost() {
        val v = qrView("https://www.banco.es/acceso")
        assertEquals(QrAction.NONE, v.action)
        assertTrue(v.alerts.isEmpty())
        assertTrue(v.notes.isEmpty())
        assertEquals(listOf("www", "banco", "es"), v.hostLabels)
        assertEquals("www.banco.es", v.field("Web"))
        assertNull(v.field("Se conecta a"))
        assertEquals("https://www.banco.es/acceso", v.shownText)
        assertFalse(v.textHidden)
        assertNull(v.secret)
    }

    @Test
    fun linkSignalsBecomeTheAnnexLinesInOrder() {
        assertEquals(listOf(QR_HTTP, qrPort(":8080")), qrView("http://banco.es:8080/").alerts)
        assertEquals(listOf(QR_IP_HOST), qrView("https://192.168.1.1/").alerts)
        assertEquals(listOf(qrUserInfo("evil.com")), qrView("https://banco.es@evil.com/").alerts)
        assertEquals(listOf(QR_SHORTENER), qrView("https://bit.ly/abc").alerts)
        assertEquals(listOf(QR_SUBDOMAINS), qrView("https://a.b.c.d.e.banco.es/").alerts)
        assertEquals(listOf(qrScheme("javascript:")), qrView("javascript:alert(1)").alerts)
        assertEquals(listOf(qrBidi("⟨U+202E⟩")), qrView("https://banco.es/‮txt.exe").alerts)
    }

    @Test
    fun punycodeShowsBothFormsOfTheName() {
        val unicode = "bаnco.es" // a Cyrillic «а»
        val ascii = IDN.toASCII(unicode)
        val v = qrView("https://$unicode/")
        assertEquals(listOf(QR_MIXED_SCRIPTS, qrPunycode(unicode)), v.alerts)
        assertEquals(unicode, v.field("Web"))
        assertEquals(ascii, v.field("Se conecta a"))
        // Written as `xn--` in the QR: the same.
        assertEquals(listOf(QR_MIXED_SCRIPTS, qrPunycode(unicode)), qrView("https://$ascii/").alerts)
    }

    @Test
    fun hiddenCharactersTheTextCannotShowGetTheirOwnLine() {
        // Percent-encoded in the host (the browser decodes it), and a line break a browser drops.
        assertEquals(listOf(QR_HIDDEN_ELSEWHERE), qrView("https://ban%E2%80%8Bco.es/").alerts)
        assertEquals(listOf(QR_HIDDEN_ELSEWHERE), qrView("https://banco.es/a\nb").alerts)
    }

    @Test
    fun hiddenCharactersOutsideTheLinkStillRaiseAnAlert() {
        // Whitespace at either end is trimmed before the link is read, and some of it is marked (a tab, U+2028, U+001F):
        // the full text still shows those marks, so never «Sin avisos» under it.
        val v = qrView("https://banco.es/\u2028")
        assertEquals("https://banco.es/⟨U+2028⟩", v.shownText)
        assertEquals(listOf(qrBidi("⟨U+2028⟩")), v.alerts)
        assertEquals(listOf(qrBidi("⟨U+0009⟩")), qrView("\twww.banco.es").alerts)
        // Inside the link and at its end: one line, naming both.
        assertEquals(listOf(qrBidi("⟨U+202E⟩, ⟨U+001F⟩")), qrView("https://banco.es/\u202Etxt\u001F").alerts)
    }

    @Test
    fun aDangerousSchemeIsNamedWithItsMarks() {
        assertEquals(listOf(qrScheme("data:")), qrView("data:text/html,<b>x</b>").alerts)
    }

    @Test
    fun aSchemelessLinkSaysSo() {
        val v = qrView("www.banco.es")
        assertEquals("www.banco.es", v.field("Web"))
        assertTrue(v.alerts.isEmpty())
    }

    @Test
    fun wifi() {
        val open = qrView("WIFI:T:nopass;S:Bar;;")
        assertEquals(listOf(QR_WIFI_OPEN), open.alerts)
        assertNull(open.secret)
        assertFalse(open.textHidden)
        assertEquals("Bar", open.field("Red"))

        val wep = qrView("WIFI:T:WEP;S:Viejo;P:12345;;")
        assertEquals(listOf(QR_WIFI_WEP), wep.alerts)
        assertEquals("12345", wep.secret)
        // The whole text carries the password: it is hidden too, until tapped.
        assertTrue(wep.textHidden)
        assertNull(wep.field("Contraseña"))

        val wpa = qrView("WIFI:T:WPA;S:Casa;P:clave;H:true;;")
        assertTrue(wpa.alerts.isEmpty())
        assertEquals("WPA", wpa.field("Cifrado"))
        assertEquals("Sí", wpa.field("Red oculta"))

        // A password but no type: not called open.
        val untyped = qrView("WIFI:S:Casa;P:clave;;")
        assertTrue(untyped.alerts.isEmpty())

        val bidi = qrView("WIFI:T:WPA;S:Casa‮;P:x;;")
        assertEquals(listOf(qrBidi("⟨U+202E⟩")), bidi.alerts)
        assertEquals("Casa⟨U+202E⟩", bidi.field("Red"))
    }

    @Test
    fun paymentsAlwaysCarryTheirNotice() {
        val sepa = qrView(
            "BCD\n002\n1\nSCT\nBFSWDE33BER\nWikimedia Foerdergesellschaft\nDE33100205000001194700\nEUR123.45\n\n\nSpende\n",
        )
        assertEquals(listOf(QR_SEPA), sepa.notes)
        assertEquals("Wikimedia Foerdergesellschaft", sepa.field("Beneficiario"))
        assertEquals("DE33100205000001194700", sepa.field("IBAN"))
        assertEquals("EUR123.45", sepa.field("Importe"))
        assertEquals(listOf(QR_CRYPTO), qrView("bitcoin:bc1qar0srrr7xfkvy5l643lydnw9re59gtzzwf5mdq?amount=0.1").notes)
    }

    @Test
    fun smsToAShortNumber() {
        assertEquals(listOf(QR_SMS), qrView("SMSTO:25521:ALTA").alerts)
        assertEquals(listOf(QR_SMS), qrView("sms:7 9 1 2 3?body=x").alerts)
        assertTrue(qrView("SMSTO:+34600111222:hola").alerts.isEmpty())
        assertTrue(qrView("SMSTO:600111222:hola").alerts.isEmpty())
    }

    @Test
    fun seguridadTextsGoToTheirOwnScreens() {
        val card = qrView("SGK1:ABC")
        assertEquals(QrAction.ADD_CONTACT, card.action)
        assertEquals(listOf(QR_SGK1), card.notes)
        assertEquals(QrAction.OPEN_RECEIVED, qrView("SGM1:ABC").action)
        assertEquals(QrAction.OPEN_RECEIVED, qrView("hola\nSGS1:ABC").action)
        val otp = qrView("otpauth://totp/Banco:ana?secret=JBSWY3DPEHPK3PXP&issuer=Banco")
        assertEquals(QrAction.ADD_TOTP, otp.action)
        // The text holds the 2FA key: hidden until tapped.
        assertTrue(otp.textHidden)
        assertEquals("Banco", otp.field("Servicio"))
        assertEquals("ana", otp.field("Cuenta"))
        assertEquals(QrAction.ADD_TOTP, qrView("otpauth-migration://offline?data=AAAA").action)
    }

    @Test
    fun plainTextAndTheOtherKinds() {
        val t = qrView("Hola, mundo")
        assertEquals(QrAction.NONE, t.action)
        assertTrue(t.fields.isEmpty() && t.alerts.isEmpty() && t.notes.isEmpty())
        assertEquals("612345678", qrView("tel:612345678").field("Número"))
        assertEquals("40.4", qrView("geo:40.4,-3.7").field("Latitud"))
        assertEquals("ana@example.com", qrView("mailto:ana@example.com?subject=Hola").field("Para"))
        assertEquals("Ana", qrView("MECARD:N:Ana;TEL:600;;").field("Nombre"))
    }

    @Test
    fun everyValueIsShownWithItsMarks() {
        val v = qrView("MECARD:N:An​a;TEL:600;;")
        assertEquals("An⟨U+200B⟩a", v.field("Nombre"))
        assertEquals(listOf(qrBidi("⟨U+200B⟩")), v.alerts)
    }

    @Test
    fun tooBigIsTheSizeError() {
        try {
            qrView("a".repeat(MAX_QR_TEXT_BYTES + 1))
            fail()
        } catch (e: IllegalArgumentException) {
            assertEquals(QR_TOO_BIG, e.message)
        }
    }

    @Test
    fun everyLabelAndTitleIsAToolText() {
        val samples = listOf(
            "https://xn--bnco-6pe.es:8080/", "www.banco.es", "WIFI:T:WEP;S:a;P:b;H:true;;", "WIFI:S:a;;", "WIFI:S:a;P:b;;",
            "BCD\n002\n1\nSCT\n\nN\nES12\nEUR1\n\nref\n", "bitcoin:x?amount=1", "MECARD:N:A;TEL:1;EMAIL:a@b;;", "SMSTO:1:x",
            "tel:1", "mailto:a@b?subject=s&body=b", "geo:1,2", "SGK1:x", "SGM1:x", "otpauth://totp/a?secret=JBSWY3DPEHPK3PXP",
            "texto",
        )
        for (s in samples) {
            val v = qrView(s)
            assertTrue(v.title, v.title in ALL_TOOL_TEXTS)
            v.fields.forEach { assertTrue(it.label, it.label in ALL_TOOL_TEXTS) }
        }
    }
}
