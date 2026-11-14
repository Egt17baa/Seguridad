package app.seguridad.tools.qr

/**
 * The QR text a screen took from [QrHandoff] for [target], held for the life of that screen's flow (CR-4): it lives in
 * the screen's ViewModel, so a rotation before saving still has it, and never in saved state (a Bundle can go to disk,
 * and a 2FA key must not outlive the process). Taken from the handoff at once, so nothing is left there for another
 * screen. It goes:
 * - when the vault locks after having been open in this flow ([vaultStateIs]), as the app empties [QrHandoff] when the
 *   vault is not open; waiting at the locked gate before the first unlock keeps it, as the handoff did;
 * - on [clear]: saving, going back, leaving the screen without a configuration change, or the ViewModel being cleared.
 * - when the app is left (2.0, task 9, ronda 1, I-1: [QrHandoff.appLeft], with the rule of the lock on leaving, so not
 *   for a picker or the scanner the app opened): then [left] is true and the screen goes back to the inspector.
 * Once the vault is open it is handed over, once, to the entry form's own ViewModel (EntryEditSession.takeHandOver),
 * which keeps it from there (a rotation included), and cleared here. Once gone it never comes back.
 */
class QrPendingText(target: QrTarget) {
    @Volatile var text: String? = QrHandoff.take(target)
        private set

    /** The text went because the app was left while it waited: the flow ends, back to the inspector. */
    @Volatile var left: Boolean = false
        private set

    init {
        if (text != null) QrHandoff.hold(this)
    }

    /** [QrHandoff.appLeft]: only a text still waiting is marked; one already handed over or cleared is not here. */
    internal fun leftApp() {
        if (text != null) {
            text = null
            left = true
        }
    }

    /** The vault has been open at some point of this flow: a lock from here on ends it. */
    @Volatile var wasOpen: Boolean = false
        private set

    fun vaultStateIs(unlocked: Boolean) {
        if (unlocked) {
            wasOpen = true
        } else if (wasOpen) {
            clear()
        }
    }

    fun clear() {
        text = null
        QrHandoff.release(this)
    }
}
