package app.seguridad.tools.qr

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QrHandoffTest {
    @After
    fun tearDown() = QrHandoff.clear()

    @Test
    fun readOnceThenGone() {
        QrHandoff.put(QrTarget.CARD, "SGK1:abc")
        assertEquals("SGK1:abc", QrHandoff.take(QrTarget.CARD))
        assertNull(QrHandoff.take(QrTarget.CARD))
    }

    @Test
    fun anotherScreenNeverGetsItAndItIsGoneAfterAnyRead() {
        QrHandoff.put(QrTarget.OTP, "otpauth://totp/x?secret=JBSWY3DPEHPK3PXP")
        // «Añadir contacto» opened on its own later: it does not get a 2FA key meant for the vault, and the key goes.
        assertNull(QrHandoff.take(QrTarget.CARD))
        assertNull(QrHandoff.take(QrTarget.OTP))
    }

    @Test
    fun clearDropsItAndANewOneReplacesTheOld() {
        QrHandoff.put(QrTarget.RECEIVED, "SGM1:x")
        QrHandoff.clear()
        assertNull(QrHandoff.take(QrTarget.RECEIVED))
        QrHandoff.put(QrTarget.RECEIVED, "SGM1:old")
        QrHandoff.put(QrTarget.CARD, "SGK1:new")
        assertNull(QrHandoff.take(QrTarget.RECEIVED))
        QrHandoff.put(QrTarget.RECEIVED, "SGM1:old")
        QrHandoff.put(QrTarget.CARD, "SGK1:new")
        assertEquals("SGK1:new", QrHandoff.take(QrTarget.CARD))
    }
}
