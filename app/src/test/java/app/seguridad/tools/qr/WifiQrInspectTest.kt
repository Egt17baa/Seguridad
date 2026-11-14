package app.seguridad.tools.qr

import app.seguridad.tools.QR_KIND_TEXT
import app.seguridad.tools.QR_KIND_WIFI
import app.seguridad.tools.QR_TEXT_HIDDEN_OTP
import app.seguridad.tools.QR_TEXT_HIDDEN_PASSWORD
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        // Open: offered too, and nothing to hide.
        val open = qrView("WIFI:T:nopass;S:Bar;;")
        assertEquals(QrAction.ADD_WIFI, open.action)
        assertFalse(open.textHidden || open.carriesSecret)
        assertNull(open.hiddenLine)
        assertEquals(QrAction.ADD_WIFI, qrView("WIFI:T:WEP;S:Viejo;P:12345;;").action)
    }

    @Test fun aWifiTheVaultCannotHoldIsShownButNotOffered() {
        for (t in listOf("WIFI:T:WPA2;S:Casa;P:clave;;", "WIFI:S:Casa;P:clave;;", "WIFI:T:nopass;S:Casa;P:clave;;", "WIFI:T:WPA;S:Casa;P:clave;H:x;;")) {
            val v = qrView(t)
            assertEquals(t, QR_KIND_WIFI, v.title)
            assertEquals(t, "Casa", v.field("Red"))
            assertEquals(t, QrAction.NONE, v.action)
            assertEquals(t, listOf(QR_WIFI_NOT_ADDABLE), v.notes)
            assertEquals(t, "clave", v.secret)
            assertTrue(t, v.carriesSecret)
            assertEquals(t, QR_TEXT_HIDDEN_PASSWORD, v.hiddenLine)
        }
        assertEquals("WPA2", qrView("WIFI:T:WPA2;S:Casa;P:clave;;").field("Cifrado"))
        // Without a password, nothing to hide, and still not offered.
        val noPassword = qrView("WIFI:T:SAE;S:Casa;;")
        assertEquals(QrAction.NONE, noPassword.action)
        assertFalse(noPassword.carriesSecret)
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

    @Test fun theLineInPlaceOfTheHiddenTextSaysWhatItHides() {
        assertEquals(QR_TEXT_HIDDEN_OTP, qrView("otpauth://totp/a?secret=JBSWY3DPEHPK3PXP").hiddenLine)
        assertEquals(QR_TEXT_HIDDEN_OTP, qrView("otpauth-migration://offline?data=AAAA").hiddenLine)
        // A line exactly when the text is hidden.
        for (t in listOf(
            "WIFI:T:WPA;S:Casa;P:clave;;", "WIFI:S:Bar;;", "WIFI:S:a;S:b;;", "https://ana:clave@banco.es/", "otpauth://totp/a?secret=JBSWY3DPEHPK3PXP",
            "hola", "SGK1:ABC", "WIFI:T:WPA2;S:x;;",
        )) {
            val v = qrView(t)
            assertEquals(t, v.textHidden, v.hiddenLine != null)
        }
    }
}
