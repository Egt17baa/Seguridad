package app.seguridad.tools.qr

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The screen a QR text is handed to from the inspector (2.0, task 9: [WIFI], the vault's form for a wifi). */
enum class QrTarget { CARD, RECEIVED, OTP, WIFI }

/**
 * Hands a QR text from the inspector to the screen that takes it («Añadir contacto», «Abrir lo recibido», the 2FA
 * import, a wifi's «Añadir a la bóveda»), without putting it in the route (a route ends up in the back stack's saved state) or in any saved state: a
 * 2FA key or a wifi password must not outlive the process. Read once: [take] always leaves it empty, so a screen opened later on its own
 * never finds a stale text, and a screen gets nothing meant for another one. The app empties it each time the vault is
 * not open (SeguridadApp), like the files «Abrir lo recibido» decrypts.
 */
object QrHandoff {
    private var target: QrTarget? = null
    private var text: String? = null

    /** 2.1.2, R-9: whether the screen the text is for reached it ([claim]); a new text starts unclaimed. */
    private var claimed = false

    @Synchronized
    fun put(target: QrTarget, text: String) {
        this.target = target
        this.text = text
        claimed = false
        updatePending()
    }

    /**
     * 2.1.2, R-9: the screen for [target] was composed and reads the text later (once the vault opens: «Abrir lo
     * recibido»): [clearUnclaimed] leaves it. True if there is a text for [target]. Nothing else changes: [take],
     * [clear] and [appLeft] work as before.
     */
    @Synchronized
    fun claim(target: QrTarget): Boolean {
        if (text == null || this.target != target) return false
        claimed = true
        return true
    }

    /**
     * 2.1.2, R-9: the inspector left the composition (not for a change of configuration): a text no screen reached goes
     * (a navigation that never composed its target); one its screen took or [claim]ed is not this one's to drop.
     */
    @Synchronized
    fun clearUnclaimed() {
        if (!claimed) clear()
    }

    /** The text if it was meant for [target], or null; either way, nothing is left. */
    @Synchronized
    fun take(target: QrTarget): String? {
        val t = text.takeIf { this.target == target }
        clear()
        return t
    }

    @Synchronized
    fun clear() {
        target = null
        text = null
        claimed = false
        updatePending()
    }

    /** The texts screens took from here and still hold for a vault screen ([QrPendingText]); weak, so none is kept alive. */
    private val pending: MutableSet<QrPendingText> = java.util.Collections.newSetFromMap(java.util.WeakHashMap())

    /**
     * 2.0, task 10 (phase B): whether a QR's text is on its way to a vault screen (handed over, or held by a screen for
     * one): the guide waits ([app.seguridad.settings.openingItem]).
     */
    private val _pendingState = MutableStateFlow(false)

    /**
     * Ronda 1 of phase B (m-8): [hasPending] as a state the screens observe, set again by every way in and out ([put],
     * [take] and [clear], a screen holding a text and letting it go, [appLeft]); what those do is as before. Every holder
     * lets its text go by hand ([QrPendingText.clear]: saved, back, the screen leaving, its ViewModel cleared, a lock after
     * the vault was open; [appLeft] lets every one go), never only by the garbage collector.
     */
    val pendingState: StateFlow<Boolean> = _pendingState.asStateFlow()

    private fun updatePending() {
        _pendingState.value = text != null || pending.isNotEmpty()
    }

    @Synchronized
    fun hasPending(): Boolean = text != null || pending.isNotEmpty()

    @Synchronized
    internal fun hold(p: QrPendingText) {
        pending += p
        updatePending()
    }

    @Synchronized
    internal fun release(p: QrPendingText) {
        pending -= p
        updatePending()
    }

    /**
     * 2.0, task 9, ronda 1 (I-1): the app was left (AppContainer.leftApp, from MainActivity.onStop, under the rule of the
     * lock on leaving): the text here and every text a screen still holds for a vault screen go, a wifi password or a 2FA
     * key waiting at the locked gate included, and those screens go back ([QrPendingText.left]).
     */
    @Synchronized
    fun appLeft() {
        clear()
        val held = pending.toList()
        pending.clear()
        held.forEach { it.leftApp() }
        updatePending()
    }
}
