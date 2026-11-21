package app.seguridad.tools.qr

import app.seguridad.tools.QR_KIND_TEXT
import app.seguridad.tools.QR_KIND_WIFI
import app.seguridad.tools.QR_TEXT_HIDDEN_OTP
import app.seguridad.tools.QR_TEXT_HIDDEN_PASSWORD
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2.0, task 9: the QR inspector reads a wifi code with [WifiQr] (one reader for what is shown and what is kept), offers
 * «Añadir a la bóveda» only for one the vault form can hold, and treats any text that starts like a wifi code as one
 * that may carry a password: hidden until tapped, and gone when the app stops or the vault locks ([QrView.carriesSecret]).
 */
class WifiQrInspectTest {
    private fun QrView.field(label: String) = fields.firstOrNull { it.label == label }?.value

    @Test fun aWifiTheVaultCanHoldOffersAddToVault() {
        val v = qrView("WIFI:T:WPA;S:Casa;P:clave;H:true;;")
        assertEquals(QR_KIND_WIFI, v.title)
        assertEquals(QrAction.ADD_WIFI, v.action)
        assertTrue(v.notes.isEmpty())
        assertEquals("clave", v.secret)
        assertTrue(v.textHidden && v.carriesSecret)
        assertEquals(QR_TEXT_HIDDEN_PASSWORD, v.hiddenLine)
        // Open: offered too, and hidden all the same (ronda 1, I-2: the secret does not depend on what Clavero read).
        val open = qrView("WIFI:T:nopass;S:Bar;;")
        assertEquals(QrAction.ADD_WIFI, open.action)
        assertTrue(open.textHidden && open.carriesSecret)
        assertEquals(QR_TEXT_HIDDEN_WIFI, open.hiddenLine)
        assertEquals(QrAction.ADD_WIFI, qrView("WIFI:T:WEP;S:Viejo;P:12345;;").action)
        // Owner's decision (Duda 2): WPA2 is offered, read as WPA.
        val wpa2 = qrView("WIFI:T:WPA2;S:Casa;P:clave;;")
        assertEquals(QrAction.ADD_WIFI, wpa2.action)
        assertTrue(wpa2.notes.isEmpty())
        // Ronda 2 (n-2): shown as the code says it; kept, and written again, as WPA.
        assertEquals("WPA2", wpa2.field("Cifrado"))
        assertEquals("WPA", (classifyQr("WIFI:T:WPA2;S:Casa;P:clave;;") as QrKind.Wifi).security)
        assertEquals("WPA2", qrView("WIFI:T:wpa2;S:Casa;P:clave;;").field("Cifrado"))
        assertEquals("WPA", qrView("WIFI:T:wpa;S:Casa;P:clave;;").field("Cifrado"))
    }

    @Test fun aWifiTheVaultCannotHoldIsShownButNotOfferedAndSaysWhy() {
        // Each reason with its own true text.
        val cases = mapOf(
            "WIFI:T:WPA3;S:Casa;P:clave;;" to qrWifiOtherSecurity("WPA3"),
            "WIFI:T:SAE;S:Casa;P:clave;;" to qrWifiOtherSecurity("SAE"),
            "WIFI:T:WPA2-EAP;S:Casa;P:clave;;" to qrWifiOtherSecurity("WPA2-EAP"),
            "WIFI:S:Casa;P:clave;;" to QR_WIFI_PASSWORD_WITHOUT_TYPE,
            "WIFI:T:nopass;S:Casa;P:clave;;" to QR_WIFI_PASSWORD_WITHOUT_TYPE,
            "WIFI:T:WPA;S:Casa;P:clave;H:x;;" to QR_WIFI_NOT_ADDABLE,
        )
        for ((t, note) in cases) {
            val v = qrView(t)
            assertEquals(t, QR_KIND_WIFI, v.title)
            assertEquals(t, "Casa", v.field("Red"))
            assertEquals(t, QrAction.NONE, v.action)
            assertEquals(t, listOf(note), v.notes)
            assertEquals(t, "clave", v.secret)
            assertTrue(t, v.carriesSecret)
            assertEquals(t, QR_TEXT_HIDDEN_PASSWORD, v.hiddenLine)
        }
        assertEquals("WPA3", qrView("WIFI:T:WPA3;S:Casa;P:clave;;").field("Cifrado"))
        // m-3: a name with a space at an end, shown as it is (marked), not offered, and said why.
        val spaces = qrView("WIFI:T:WPA;S: Casa ;P:clave;;")
        assertEquals(listOf(QR_WIFI_NAME_SPACES), spaces.notes)
        assertEquals(QrAction.NONE, spaces.action)
        // Without a password: still not offered, and hidden all the same.
        val noPassword = qrView("WIFI:T:SAE;S:Casa;;")
        assertEquals(QrAction.NONE, noPassword.action)
        assertTrue(noPassword.carriesSecret)
        assertEquals(QR_TEXT_HIDDEN_WIFI, noPassword.hiddenLine)
    }

    /** Every hostile wifi text of [WifiQrTest] that is refused: shown as text, hidden, and cleared on leaving and on lock. */
    @Test fun aWifiTextThatCannotBeReadIsTreatedAsASecret() {
        for (t in listOf(
            "WIFI:T:WPA;S:Casa;P:clave\\", // A trailing backslash.
            "WIFI:T:WPA;S:Casa;P:x;P:clave;;", // A repeated field.
            "WIFI:S:Casa;;P:clave;;", // Something after the end.
            "WIFI:T:WPA;P:clave;;", // No name.
            "WIFI:T:WPA;S:;P:clave;;", // An empty name.
            "WIFI:T:WPA;S:Casa;P:cla;ve;;", // A «;» not escaped.
            "WIFI:T:WPA;S:Casa;XP:secreta;;", // I-2: ZXing reads «secreta» as the password.
            "WIFI:T:WPA;PS:otra;S:Casa;P:clave;;", // I-2: ZXing reads the network «otra».
            "WIFI:T:WPA;S:xP:mala;P:buena;;", // I-2: ZXing reads the password «mala».
            "wifi:T:WPA;S:Casa;P:clave;;", " WIFI:S:Casa;P:clave;;", "\uFEFFWIFI:S:Casa;P:clave;;", "\u200BWIFI:S:Casa;;",
        )) {
            val v = qrView(t)
            assertEquals(t, QrKind.WifiUnreadable(t), classifyQr(t))
            assertEquals(t, QR_KIND_TEXT, v.title)
            assertEquals(t, listOf(QR_WIFI_UNREADABLE), v.notes)
            assertEquals(t, QrAction.NONE, v.action)
            assertNull(t, v.secret)
            assertTrue(t, v.textHidden)
            assertTrue(t, v.carriesSecret)
            assertEquals(t, QR_TEXT_HIDDEN_WIFI, v.hiddenLine)
        }
        // Not a link either, whatever follows the prefix.
        val link = qrView("WIFI://banco.es/")
        assertTrue(link.hostLabels.isEmpty())
        assertEquals(QrKind.WifiUnreadable("WIFI://banco.es/"), classifyQr("WIFI://banco.es/"))
    }

    @Test fun quotesThatArePartOfTheNameStayInIt() {
        // The reader before task 9 unescaped first and dropped these quotes.
        assertEquals("\"foo;bar\\baz\"", qrView("WIFI:S:\\\"foo\\;bar\\\\baz\\\";;").field("Red"))
        assertEquals("CAFE", qrView("WIFI:S:\"CAFE\";;").field("Red"))
    }

    /** m-4: the second line of defence of [classifyQr] keeps a wifi text a secret too. */
    @Test fun theLastDefenceKeepsAWifiTextASecret() {
        assertEquals(QrKind.WifiUnreadable("WIFI:S:a;;"), qrKindAfterAFailure("WIFI:S:a;;"))
        assertEquals(QrKind.WifiUnreadable(" wifi:x"), qrKindAfterAFailure(" wifi:x"))
        assertEquals(QrKind.PlainText("hola"), qrKindAfterAFailure("hola"))
    }

    @Test fun theLineInPlaceOfTheHiddenTextSaysWhatItHides() {
        assertEquals(QR_TEXT_HIDDEN_OTP, qrView("otpauth://totp/a?secret=JBSWY3DPEHPK3PXP").hiddenLine)
        assertEquals(QR_TEXT_HIDDEN_OTP, qrView("otpauth-migration://offline?data=AAAA").hiddenLine)
        // A line exactly when the text is hidden.
        for (t in listOf(
            "WIFI:T:WPA;S:Casa;P:clave;;", "WIFI:S:Bar;;", "WIFI:S:a;S:b;;", "wifi:S:a;;", "https://ana:clave@banco.es/", "otpauth://totp/a?secret=JBSWY3DPEHPK3PXP",
            "hola", "SGK1:ABC", "WIFI:T:WPA2;S:x;;",
        )) {
            val v = qrView(t)
            assertEquals(t, v.textHidden, v.hiddenLine != null)
        }
    }
}
