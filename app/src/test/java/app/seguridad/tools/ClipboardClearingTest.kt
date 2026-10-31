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

    @Test fun `at its time, a copy is cleared only if it is still that copy of ours, or if the clipboard is hidden`() {
        // One rule for both timers (the copy's own and the one armed on reopening), F4 review M3.
        // Hidden (Android 10+ with the app in the background) or empty: cleared, never a secret left behind.
        assertTrue(wipeOwnCopy(described = false, seenLabel = null, sameCopy = false))
        // Still ours, the same copy.
        assertTrue(wipeOwnCopy(described = true, seenLabel = CLIPBOARD_OWN_LABEL, sameCopy = true))
        // Another app's copy since: left alone, also when it has no label (an image, a ClipData without a label).
        assertFalse(wipeOwnCopy(described = true, seenLabel = "Otra", sameCopy = false))
        assertFalse(wipeOwnCopy(described = true, seenLabel = "", sameCopy = false))
        assertFalse(wipeOwnCopy(described = true, seenLabel = null, sameCopy = false))
        assertFalse(wipeOwnCopy(described = true, seenLabel = null, sameCopy = true))
        // A newer copy of ours: its own timer clears it.
        assertFalse(wipeOwnCopy(described = true, seenLabel = CLIPBOARD_OWN_LABEL, sameCopy = false))
    }
}
