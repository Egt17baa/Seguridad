package app.seguridad.tools.qr

/** The screen a QR text is handed to from the inspector. */
enum class QrTarget { CARD, RECEIVED, OTP }

/**
 * Hands a QR text from the inspector to the screen that takes it («Añadir contacto», «Abrir lo recibido», the 2FA
 * import), without putting it in the route (a route ends up in the back stack's saved state) or in any saved state: a
 * 2FA key must not outlive the process. Read once: [take] always leaves it empty, so a screen opened later on its own
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
}
