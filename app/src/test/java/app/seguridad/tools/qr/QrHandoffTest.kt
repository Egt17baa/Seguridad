package app.seguridad.tools.qr

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    // CR-4: the 2FA import screen keeps the scanned text for the life of its flow (a rotation keeps it), in memory only.
    @Test
    fun theHeldTextIsTakenOnceAndSurvivesUntilTheFlowEnds() {
        QrHandoff.put(QrTarget.OTP, "otpauth://totp/x?secret=JBSWY3DPEHPK3PXP")
        val hold = QrPendingText(QrTarget.OTP)
        assertNull("Taken: nothing is left in the handoff", QrHandoff.take(QrTarget.OTP))
        // Waiting at the locked gate (the vault was never open in this flow): kept.
        hold.vaultStateIs(unlocked = false)
        assertEquals("otpauth://totp/x?secret=JBSWY3DPEHPK3PXP", hold.text)
        hold.vaultStateIs(unlocked = true)
        assertTrue(hold.wasOpen)
        // A rotation is the same holder read again: still there, as often as the screen is composed.
        assertEquals("otpauth://totp/x?secret=JBSWY3DPEHPK3PXP", hold.text)
        assertEquals("otpauth://totp/x?secret=JBSWY3DPEHPK3PXP", hold.text)
        hold.vaultStateIs(unlocked = true)
        assertEquals("otpauth://totp/x?secret=JBSWY3DPEHPK3PXP", hold.text)
    }

    @Test
    fun theHeldTextGoesWhenTheVaultLocksAfterBeingOpen() {
        QrHandoff.put(QrTarget.OTP, "otpauth://totp/x?secret=JBSWY3DPEHPK3PXP")
        val hold = QrPendingText(QrTarget.OTP)
        hold.vaultStateIs(unlocked = true)
        hold.vaultStateIs(unlocked = false)
        assertNull(hold.text)
        assertTrue(hold.wasOpen)
        // Opened again: nothing comes back.
        hold.vaultStateIs(unlocked = true)
        assertNull(hold.text)
    }

    @Test
    fun theHeldTextGoesOnSaveOrBackAndNeverTakesAnotherScreensText() {
        QrHandoff.put(QrTarget.OTP, "otpauth://totp/x?secret=JBSWY3DPEHPK3PXP")
        val hold = QrPendingText(QrTarget.OTP)
        hold.clear()
        assertNull(hold.text)
        QrHandoff.put(QrTarget.CARD, "SGK1:abc")
        assertNull(QrPendingText(QrTarget.OTP).text)
        assertNull("Another screen's text goes too, as take() always does", QrHandoff.take(QrTarget.CARD))
    }
}
