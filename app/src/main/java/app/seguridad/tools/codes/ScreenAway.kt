package app.seguridad.tools.codes

import app.seguridad.vault.LockReason

/**
 * The owner's rule for a screen that shows secrets («PIN y códigos»; F6 reuses it for the tools that hold a plaintext,
 * a password or a result): when what it shows must go. Plain JVM, on the monotonic clock (SystemClock.elapsedRealtime):
 * - back after [awayTooLong] away (`max(lock time, 60 s)`): [stopped] then [started], from ON_STOP and ON_START, or the
 *   composition leaving (another tab);
 * - the same for the time spent in another app while Seguridad stays visible in split screen or a floating window
 *   (COV2-3, M3 of the F1 review): [otherAppFocused] then [otherAppLeft], from AppContainer.awayInOtherApp;
 * - a lock of the vault ([lockWipes]): with the app in use, by hand always, the idle one only if this screen was not
 *   used for the lock time either ([used]); a lock while the user is in the other app always; one from leaving the app
 *   (MainActivity.onStop) never, it is left to the time away.
 *
 * One trip away has one start, the first stop: a later stop (a rotation's dispose after its ON_STOP, Home while in the
 * other app) does not move it. A trip to the other app that began while the screen was already stopped (on another
 * tab) is not its own: coming back from the other app does not use up that tab's stop. A start while the other app
 * still has the focus (a re-creation, which brings no top-resumed callback) does not end the trip: only [otherAppLeft]
 * does, so the time in the other app is never forgotten.
 */
class ScreenAway(createdAt: Long = 0L) {
    /** When the screen stopped; null while it is shown. */
    private var stoppedAt: Long? = null

    /** The current stop began with [otherAppFocused], so [otherAppLeft] ends it. */
    private var stopIsOtherApp = false

    /** Another app has the focus in multi-window. */
    var inOtherApp: Boolean = false
        private set

    /** The last tap or key on the screen. */
    private var lastUsed: Long = createdAt

    fun used(now: Long) {
        lastUsed = now
    }

    fun stopped(now: Long) {
        if (stoppedAt == null) stoppedAt = now
    }

    /**
     * True when the screen must be emptied: it was away at least [awayTooLong]. While another app still has the focus
     * ([inOtherApp]: a re-creation by a rotation, a resize of the split, a theme change or a fold, or back to the split
     * pair from Home with the focus on the other app), the trip goes on: it stays open, and [otherAppLeft] ends it (N1 of
     * the F1 re-review). After a wipe here it counts again from [now].
     */
    fun started(now: Long, lockAfterMs: Long): Boolean {
        val at = stoppedAt ?: return false
        val tooLong = awayTooLong(now - at, lockAfterMs)
        if (inOtherApp) {
            stoppedAt = if (tooLong) now else at
            stopIsOtherApp = true
        } else {
            stoppedAt = null
            stopIsOtherApp = false
        }
        return tooLong
    }

    fun otherAppFocused(now: Long) {
        inOtherApp = true
        if (stoppedAt == null) {
            stoppedAt = now
            stopIsOtherApp = true
        }
    }

    /** Back from the other app; true when the screen must be emptied ([started], for a trip that began there). */
    fun otherAppLeft(now: Long, lockAfterMs: Long): Boolean {
        inOtherApp = false
        if (!stopIsOtherApp) return false
        return started(now, lockAfterMs)
    }

    /** Whether the lock [seen] empties the screen (see the class). */
    fun lockWipes(seen: LockSeen): Boolean {
        if (!seen.appInForeground || seen.reason == LockReason.LEFT_APP) return false
        if (inOtherApp) return true
        if (seen.reason == LockReason.IDLE && !awayTooLong(seen.now - lastUsed, seen.lockAfterMs)) return false
        return true
    }
}
