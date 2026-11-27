package app.seguridad.tools.qr

import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    // 2.0, task 9, ronda 1 (I-1): leaving the app drops every QR text still waiting for a vault screen (a wifi password
    // or a 2FA key at the locked gate), and the screen goes back to the inspector; a text already handed to the form is
    // the form's, which its own rules clear.
    @Test
    fun leavingTheAppDropsEveryPendingTextAndSaysSo() {
        QrHandoff.put(QrTarget.WIFI, "WIFI:T:WPA;S:Casa;P:clave;;")
        val wifi = QrPendingText(QrTarget.WIFI)
        QrHandoff.put(QrTarget.OTP, "otpauth://totp/x?secret=JBSWY3DPEHPK3PXP")
        val otp = QrPendingText(QrTarget.OTP)
        QrHandoff.put(QrTarget.CARD, "SGK1:abc") // Not taken yet: it goes too.
        assertFalse(wifi.left || otp.left)
        val seen = wifi.leftState
        QrHandoff.appLeft()
        // Ronda 2 (n-1): a state the screen collects, so it goes back as soon as it is on screen again.
        assertTrue(seen.value)
        assertNull(wifi.text)
        assertNull(otp.text)
        assertTrue(wifi.left && otp.left)
        assertNull(QrHandoff.take(QrTarget.CARD))
        // Once gone it never comes back, and a later unlock does not bring it.
        wifi.vaultStateIs(unlocked = true)
        assertNull(wifi.text)
    }

    @Test
    fun aTextAlreadyHandedOverOrClearedIsNotMarkedAsLeft() {
        QrHandoff.put(QrTarget.WIFI, "WIFI:T:WPA;S:Casa;P:clave;;")
        val handed = QrPendingText(QrTarget.WIFI)
        handed.clear() // Handed to the form (onWifiQrTaken), or saved, or back.
        val never = QrPendingText(QrTarget.WIFI) // Nothing was waiting for it.
        QrHandoff.appLeft()
        assertFalse(handed.left)
        assertFalse(never.left)
    }

    // Ronda 2 (n-1): the import screens collect [QrPendingText.leftState]; a collector hears the change at once, so the
    // screen goes back to the inspector on the return to the app, with no other change of state (the vault was locked).
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun aCollectorHearsThatTheAppWasLeft() = kotlinx.coroutines.test.runTest {
        QrHandoff.put(QrTarget.WIFI, "WIFI:T:WPA;S:Casa;P:clave;;")
        val pending = QrPendingText(QrTarget.WIFI)
        val seen = mutableListOf<Boolean>()
        val job = launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) { pending.leftState.collect { seen += it } }
        QrHandoff.appLeft()
        assertEquals(listOf(false, true), seen)
        job.cancel()
    }

    /**
     * 2.0, tarea 10 (fase B, ronda 1, m-8): whether something is on its way is a state the screen observes, kept right by
     * every way in and out: [QrHandoff.put], [QrHandoff.take], [QrHandoff.clear], a screen holding a text
     * ([QrPendingText]), its [QrPendingText.clear] and [QrHandoff.appLeft]. Nothing depends on the garbage collector.
     */
    @Test
    fun pendingIsObservable() {
        QrHandoff.clear()
        assertFalse(QrHandoff.pendingState.value)
        QrHandoff.put(QrTarget.OTP, "otpauth://totp/x?secret=JBSWY3DPEHPK3PXP")
        assertTrue(QrHandoff.pendingState.value)
        QrHandoff.clear()
        assertFalse(QrHandoff.pendingState.value)
        QrHandoff.put(QrTarget.OTP, "otpauth://totp/x?secret=JBSWY3DPEHPK3PXP")
        val held = QrPendingText(QrTarget.OTP) // takes it from the handoff and holds it
        assertTrue("Held by a screen", QrHandoff.pendingState.value)
        held.clear()
        assertFalse(QrHandoff.pendingState.value)
        QrHandoff.put(QrTarget.WIFI, "WIFI:T:WPA;S:red;P:clave-de-prueba;;")
        assertEquals(null, QrHandoff.take(QrTarget.OTP))
        assertFalse("A take for another target empties it too", QrHandoff.pendingState.value)
        QrHandoff.put(QrTarget.WIFI, "WIFI:T:WPA;S:red;P:clave-de-prueba;;")
        QrPendingText(QrTarget.WIFI)
        assertTrue(QrHandoff.pendingState.value)
        QrHandoff.appLeft()
        assertFalse(QrHandoff.pendingState.value)
        // The picker's mark, observable too, and set as before.
        app.seguridad.vault.VaultLock.keepOpenOnce = true
        assertTrue(app.seguridad.vault.VaultLock.keepOpenOnceState.value)
        app.seguridad.vault.VaultLock.keepOpenOnce = false
        assertFalse(app.seguridad.vault.VaultLock.keepOpenOnceState.value)
    }

    /**
     * 2.1.2, R-9: the inspector, when it leaves, drops a text no screen reached ([QrHandoff.clearUnclaimed]); one whose
     * screen was reached and waits at the locked gate («Abrir lo recibido» reads it once the vault opens) stays.
     */
    @Test
    fun theInspectorLeavingDropsOnlyATextNoScreenReached() {
        QrHandoff.put(QrTarget.OTP, "otpauth://totp/x?secret=JBSWY3DPEHPK3PXP")
        QrHandoff.clearUnclaimed()
        assertFalse(QrHandoff.hasPending())
        assertNull(QrHandoff.take(QrTarget.OTP))
        // Reached by its own screen: kept for it.
        QrHandoff.put(QrTarget.RECEIVED, "SGM1:x")
        assertTrue(QrHandoff.claim(QrTarget.RECEIVED))
        QrHandoff.clearUnclaimed()
        assertEquals("SGM1:x", QrHandoff.take(QrTarget.RECEIVED))
        // Another screen does not claim it, and a new text is not claimed by an earlier claim.
        QrHandoff.put(QrTarget.RECEIVED, "SGM1:y")
        assertFalse(QrHandoff.claim(QrTarget.CARD))
        QrHandoff.clearUnclaimed()
        assertNull(QrHandoff.take(QrTarget.RECEIVED))
        QrHandoff.put(QrTarget.RECEIVED, "SGM1:z")
        assertTrue(QrHandoff.claim(QrTarget.RECEIVED))
        QrHandoff.put(QrTarget.RECEIVED, "SGM1:w")
        QrHandoff.clearUnclaimed()
        assertNull(QrHandoff.take(QrTarget.RECEIVED))
        // A clear (the app left, the vault not open) drops a claimed text too.
        QrHandoff.put(QrTarget.RECEIVED, "SGM1:v")
        QrHandoff.claim(QrTarget.RECEIVED)
        QrHandoff.clear()
        assertNull(QrHandoff.take(QrTarget.RECEIVED))
    }
}
