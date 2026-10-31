package app.seguridad.tools

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** The fingerprint unlock runs once at a time (CU-B revisión 1, minor 2): two Cipher.init on StrongBox at once never. */
class SingleRunTest {
    @Test fun aSecondStartWhileOneRunsIsRefused() {
        val guard = SingleRun()
        val first = guard.start()
        assertNotNull(first)
        assertNull("Auto-start plus a tap on «Usar huella»: the second is ignored", guard.start())
        first!!.release()
        assertNotNull(guard.start())
    }

    @Test fun releasingTwiceOrLateNeverFreesANewerRun() {
        val guard = SingleRun()
        val old = guard.start()!!
        old.release()
        val current = guard.start()!!
        old.release() // A late callback of the old run (onKey after onError, a cancel after the activity went).
        assertNull("The newer run still holds it", guard.start())
        current.release()
        current.release()
        assertNotNull(guard.start())
    }
}
