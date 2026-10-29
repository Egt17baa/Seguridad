package app.seguridad.tools

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A14: before Android 13 there is no EXTRA_IS_SENSITIVE, so the keyboard's clipboard history may keep a copied secret.
 * The user is told once, the first time a secret is copied, until [accepted].
 */
fun needsClipboardHistoryNotice(sdk: Int, accepted: Boolean): Boolean = sdk < 33 && !accepted

/**
 * The gate of copySensitive, without Android: [write] copies [text] at once unless the notice is needed on [sdk]; then
 * the copy waits in [notice] and is written only on its [ClipboardNoticeRequest.accept]. [onCopied] runs after the
 * write, now or on accept, never if nothing is written. Returns whether it was written now.
 */
fun copyOrAskNotice(
    text: String,
    notice: ClipboardNoticeRequest,
    accepted: Boolean,
    sdk: Int,
    write: (String) -> Unit,
    onCopied: (() -> Unit)? = null,
): Boolean {
    if (needsClipboardHistoryNotice(sdk, accepted || notice.acceptedNow)) {
        notice.ask(text, write, onCopied)
        return false
    }
    write(text)
    onCopied?.invoke()
    return true
}

/**
 * The copy waiting for the A14 notice to be accepted. One per process (in AppContainer), so the dialog at the root of
 * the app shows it whatever screen asked, and a rotation does not lose it. The text stays only here, in memory: never
 * in a Bundle or a saved state, never in the UI's state ([asking] says only whether there is one). It goes on
 * [accept] (written once), on [cancel] and on [drop] (the vault locks or the app is left). [lockEpoch] is the vault's
 * lock counter: a lock after [ask], even one followed by an unlock, makes [accept] write nothing.
 */
class ClipboardNoticeRequest(private val lockEpoch: () -> Int = { 0 }) {
    private class Pending(val text: String, val write: (String) -> Unit, val onCopied: (() -> Unit)?, val epoch: Int)

    private var pending: Pending? = null
    private val _asking = MutableStateFlow(false)

    /** Whether a copy is waiting for the notice. */
    val asking: StateFlow<Boolean> = _asking.asStateFlow()

    /**
     * Accepted during this run of the app: no second notice even if saving the acceptance in Ajustes failed or has not
     * landed yet (the next copy may come before the write).
     */
    @Volatile var acceptedNow: Boolean = false
        private set

    /** Waits for the notice with [text], to be copied by [write]; a newer copy replaces an older one still waiting. */
    @Synchronized fun ask(text: String, write: (String) -> Unit, onCopied: (() -> Unit)? = null) {
        pending = Pending(text, write, onCopied, lockEpoch())
        _asking.value = true
    }

    /**
     * The user accepted: writes the waiting copy once, then its onCopied. Returns false, writing nothing, if there was
     * none (already taken or dropped) or the vault locked since [ask]. If the write throws (the clipboard service failed),
     * it returns false too: [acceptedNow] stays false and onCopied does not run, so the notice comes back on the next copy
     * and the caller does not record it as accepted in Ajustes. The copy is not kept for a retry.
     */
    fun accept(): Boolean {
        val p = synchronized(this) {
            val p = pending ?: return false
            pending = null
            _asking.value = false
            if (p.epoch != lockEpoch()) return false
            p
        }
        try {
            p.write(p.text)
        } catch (e: Exception) {
            return false
        }
        acceptedNow = true // Only once the copy is really in the clipboard.
        p.onCopied?.invoke()
        return true
    }

    /** The user said no: nothing is copied. */
    fun cancel() = drop()

    /** Forgets the waiting copy and its onCopied, if any (the vault locked, or the app was left). */
    @Synchronized fun drop() {
        pending = null
        _asking.value = false
    }
}
