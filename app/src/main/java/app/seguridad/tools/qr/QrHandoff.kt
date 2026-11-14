package app.seguridad.tools.qr

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

    @Synchronized
    fun put(target: QrTarget, text: String) {
        this.target = target
        this.text = text
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
    }

    /** The texts screens took from here and still hold for a vault screen ([QrPendingText]); weak, so none is kept alive. */
    private val pending: MutableSet<QrPendingText> = java.util.Collections.newSetFromMap(java.util.WeakHashMap())

    @Synchronized
    internal fun hold(p: QrPendingText) {
        pending += p
    }

    @Synchronized
    internal fun release(p: QrPendingText) {
        pending -= p
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
    }
}
