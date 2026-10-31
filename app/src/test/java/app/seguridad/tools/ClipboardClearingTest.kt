package app.seguridad.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** TECLADO-3 (audit 1.3.0): what clearStaleCopy does with a copy of ours found when the app gets the focus again. */
class ClipboardClearingTest {
    private val wait = 45_000L
    private val copiedAt = 1_800_000_000_000L

    @Test fun `a copy older than the time is cleared at once`() {
        assertEquals(0L, ownCopyClearDelay(nowMs = copiedAt + wait, copiedAtMs = copiedAt, clearAfterMs = wait))
        assertEquals(0L, ownCopyClearDelay(nowMs = copiedAt + 10 * wait, copiedAtMs = copiedAt, clearAfterMs = wait))
    }

    @Test fun `a younger copy (the app was closed and reopened before the time) is cleared when its time is up`() {
        // No timer of this run covers it: the process that copied it is gone.
        assertEquals(wait, ownCopyClearDelay(nowMs = copiedAt, copiedAtMs = copiedAt, clearAfterMs = wait))
        assertEquals(5_000L, ownCopyClearDelay(nowMs = copiedAt + 40_000L, copiedAtMs = copiedAt, clearAfterMs = wait))
        assertEquals(1L, ownCopyClearDelay(nowMs = copiedAt + wait - 1, copiedAtMs = copiedAt, clearAfterMs = wait))
    }

    @Test fun `a wall clock set back after copying never keeps the copy (coverage-2, mejora 5)`() {
        assertEquals(0L, ownCopyClearDelay(nowMs = copiedAt - 1, copiedAtMs = copiedAt, clearAfterMs = wait))
        assertEquals(0L, ownCopyClearDelay(nowMs = copiedAt - 3_600_000L, copiedAtMs = copiedAt, clearAfterMs = wait))
    }

    @Test fun `at its time, the reopened copy is cleared only if it is still that copy, or if the clipboard is hidden`() {
        // Hidden (Android 10+ with the app in the background) or empty: cleared, never a secret left behind.
        assertTrue(wipeReopenedCopy(seenLabel = null, seenTimestamp = null, expectedTimestamp = copiedAt))
        // Still ours, the same copy.
        assertTrue(wipeReopenedCopy(seenLabel = CLIPBOARD_OWN_LABEL, seenTimestamp = copiedAt, expectedTimestamp = copiedAt))
        // Another app's copy since: left alone.
        assertFalse(wipeReopenedCopy(seenLabel = "Otra", seenTimestamp = copiedAt + 1, expectedTimestamp = copiedAt))
        assertFalse(wipeReopenedCopy(seenLabel = "", seenTimestamp = copiedAt + 1, expectedTimestamp = copiedAt))
        // A newer copy of ours: its own timer clears it.
        assertFalse(wipeReopenedCopy(seenLabel = CLIPBOARD_OWN_LABEL, seenTimestamp = copiedAt + 1, expectedTimestamp = copiedAt))
    }
}
