package app.seguridad.tools.qr

/**
 * The QR text a screen took from [QrHandoff] for [target], held for the life of that screen's flow (CR-4): it lives in
 * the screen's ViewModel, so a rotation before saving still has it, and never in saved state (a Bundle can go to disk,
 * and a 2FA key must not outlive the process). Taken from the handoff at once, so nothing is left there for another
 * screen. It goes:
 * - when the vault locks after having been open in this flow ([vaultStateIs]), as the app empties [QrHandoff] when the
 *   vault is not open; waiting at the locked gate before the first unlock keeps it, as the handoff did;
 * - on [clear]: saving, going back, leaving the screen without a configuration change, or the ViewModel being cleared.
 * Once gone it never comes back.
 */
class QrPendingText(target: QrTarget) {
    @Volatile var text: String? = QrHandoff.take(target)
        private set

    /** The vault has been open at some point of this flow: a lock from here on ends it. */
    @Volatile var wasOpen: Boolean = false
        private set

    fun vaultStateIs(unlocked: Boolean) {
        if (unlocked) {
            wasOpen = true
        } else if (wasOpen) {
            text = null
        }
    }

    fun clear() {
        text = null
    }
}
