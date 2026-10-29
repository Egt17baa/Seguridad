package app.seguridad.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClockMarkTest {
    private val t = 1_700_000_000_000L

    @Test fun aLowerTimeNeverLowersTheMark() {
        val s = Settings(clockHighWaterMs = t)
        assertEquals(t, s.seeingClock(t - 1).clockHighWaterMs)
        assertEquals(t, s.seeingClock(t).clockHighWaterMs)
        assertEquals(t + 5, s.seeingClock(t + 5).clockHighWaterMs)
        assertFalse(clockMarkWorthWriting(t, t - 3_600_000))
    }

    @Test fun nothingIsWrittenBelowMarkPlusThirtySeconds() {
        assertFalse(clockMarkWorthWriting(t, t))
        assertFalse(clockMarkWorthWriting(t, t + CLOCK_WRITE_STEP_MS - 1))
        assertTrue(clockMarkWorthWriting(t, t + CLOCK_WRITE_STEP_MS))
        assertTrue(clockMarkWorthWriting(0, t)) // First ever sight.
    }

    @Test fun theMasterPasswordResetCanLowerTheMark() {
        val future = Settings(clockHighWaterMs = t + 365L * 24 * 60 * 60_000)
        assertEquals(t, future.resettingClock(t).clockHighWaterMs)
        assertEquals(t, Settings(clockHighWaterMs = t).resettingClock(t).clockHighWaterMs)
    }
}
