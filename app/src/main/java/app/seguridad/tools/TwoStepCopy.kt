package app.seguridad.tools

import app.seguridad.crypto.TotpSecret
import app.seguridad.crypto.totpCode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** How long «Copiar contraseña» is offered after «Copiar usuario» (design «Copiar usuario y luego contraseña»). */
const val TWO_STEP_COPY_MS = 30_000L

/**
 * 2.0, task 6: after «Copiar usuario», the app offers «Copiar contraseña» of the same entry for [TWO_STEP_COPY_MS].
 * One per process (in AppContainer), so the offer is there when the user comes back to the app from wherever the user
 * was pasted. It keeps only the entry's id, the time and the lock counter: the password is read from the open vault at
 * the tap, so nothing secret is ever held here. [clock] only moves forward (elapsed realtime): a value lower than the
 * one of the offer ends it. [lockEpoch] is the app's lock counter (it goes up with every lock and every switch of
 * vault): a lock since the offer ends it, even if the vault opened again; the app also calls [clear] at every lock.
 */
class TwoStepCopy(private val clock: () -> Long, private val lockEpoch: () -> Int = { 0 }) {
    private class Offer(val id: String, val at: Long, val epoch: Int)

    @Volatile private var offer: Offer? = null

    /** Whether the app was left since the offer ([markLeft]) and the return has not offered it yet ([takeReturnOffer]). */
    @Volatile private var left = false

    private val _changes = MutableStateFlow(0L)

    /**
     * Goes up when an offer starts, when the app is left with one, and when it ends ([clear]): the screen that shows it
     * looks again ([takeReturnOffer]) and takes it off if it ended (review m-2 of task 6). It holds a count, nothing else.
     */
    val changes: StateFlow<Long> = _changes.asStateFlow()

    /** «Copiar usuario» of entry [entryId] went into the clipboard: «Copiar contraseña» is offered for 30 s. */
    fun offerPassword(entryId: String) {
        offer = Offer(entryId, clock(), lockEpoch())
        left = false
        _changes.value += 1
    }

    /** The app went to the background (MainActivity.onStop): the next return offers the password, once. */
    fun markLeft() {
        if (live() == null) return
        left = true
        _changes.value += 1
    }

    /**
     * What a screen offers when the app comes back (review m-1 of task 6: «al volver a la app» is from outside it): the
     * entry, once per return, if the app was left within its 30 s; null otherwise (also on a screen opened inside the app).
     */
    fun takeReturnOffer(): String? {
        val o = live() ?: return null
        if (!left) return null
        left = false
        return o.id
    }

    /** The entry whose password is offered now, or null (none, its 30 s are over, the clock went back, or a lock). */
    fun pending(): String? = live()?.id

    /** How long the offer has left, in milliseconds; 0 when there is none. */
    fun leftMs(): Long {
        val o = live() ?: return 0L
        return TWO_STEP_COPY_MS - (clock() - o.at)
    }

    /** Ends the offer: «Copiar contraseña» was used, another copy was made, or the vault locked. */
    fun clear() {
        if (offer == null) return
        offer = null
        left = false
        _changes.value += 1
    }

    private fun live(): Offer? {
        val o = offer ?: return null
        val age = clock() - o.at
        if (age < 0 || age >= TWO_STEP_COPY_MS || o.epoch != lockEpoch()) {
            offer = null
            left = false
            return null
        }
        return o
    }
}

/** With fewer seconds than this left, tapping a code in the list copies the next one (design «2FA en la lista»). */
const val ROW_NEXT_CODE_BELOW_S = 5

/** The code tapping the 2FA of a row copies: [code], and whether it is the [next] one (fewer than 5 s left of this one). */
class RowCode(val code: String, val next: Boolean)

/** What tapping the 2FA code of a row at [nowMillis] copies; null when the app does not calculate it ([totpCode]). */
fun rowCodeToCopy(s: TotpSecret, nowMillis: Long): RowCode? {
    val (code, left) = totpCode(s, nowMillis) ?: return null
    if (left >= ROW_NEXT_CODE_BELOW_S) return RowCode(code, next = false)
    val next = totpCode(s, nowMillis + left * 1_000L) ?: return null
    return RowCode(next.first, next = true)
}
