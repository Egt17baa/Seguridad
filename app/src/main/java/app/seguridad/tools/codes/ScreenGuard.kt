package app.seguridad.tools.codes

/**
 * The «PIN y códigos» rule for any screen that holds a secret or a result ([ScreenAway] decides, this applies it):
 * [wipe] runs when the screen comes back after `max(lock time, 60 s)` away (ON_STOP and ON_START, another tab, or
 * another app with the focus in multi-window) and when the vault locks with the app in use ([vaultStateIs]). Plain JVM;
 * on Android it lives in the screen's ViewModel, so a rotation keeps the screen and the death of the process loses it.
 *
 * COV1-1 (audit 1.3.0) puts «Cifrar texto», «Cifrar archivos» and «¿Contraseña filtrada?» under it; [CodesSession] uses
 * it too, so there is one copy of the rule.
 */
class ScreenGuard(createdAt: Long = 0L, private val wipe: () -> Unit) {
    private val away = ScreenAway(createdAt)

    /** The vault was open the last time its state was told: only a change from open to not open is a lock. */
    private var wasUnlocked = false

    /** Any tap or key on the screen (monotonic ms), for the idle lock. */
    fun used(now: Long) = away.used(now)

    /** ON_STOP of the screen, or the screen leaving the composition (another tab). */
    fun stopped(now: Long) = away.stopped(now)

    /** ON_START of the screen; true (and [wipe] ran) when it was away at least [awayTooLong]. */
    fun started(now: Long, lockAfterMs: Long): Boolean = away.started(now, lockAfterMs).also { if (it) wipe() }

    /** Another app took the focus while this one stays visible (split screen, floating window). */
    fun otherAppFocused(now: Long) = away.otherAppFocused(now)

    /** Back from the other app; true (and [wipe] ran) when it was there at least [awayTooLong]. */
    fun otherAppLeft(now: Long, lockAfterMs: Long): Boolean = away.otherAppLeft(now, lockAfterMs).also { if (it) wipe() }

    /** The vault's state, each time it changes; [lock] is asked only for a change from open to locked ([ScreenAway.lockWipes]). */
    fun vaultStateIs(unlocked: Boolean, lock: () -> LockSeen) {
        val locked = wasUnlocked && !unlocked
        wasUnlocked = unlocked
        if (!locked) return
        if (away.lockWipes(lock())) wipe()
    }
}
