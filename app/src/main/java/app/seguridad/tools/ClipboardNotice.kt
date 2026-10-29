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
 * The copy waiting for the A14 notice to be accepted. One per process (in AppContainer), so the dialog at the root of
 * the app shows it whatever screen asked, and a rotation does not lose it. The text stays only here, in memory: never
 * in a Bundle or a saved state, never in the UI's state ([asking] says only whether there is one). It goes on
 * [accept] (handed over once), on [cancel] and on [drop] (the vault locks or the app is left).
 */
class ClipboardNoticeRequest {
    private var pending: String? = null
    private val _asking = MutableStateFlow(false)

    /** Whether a copy is waiting for the notice. */
    val asking: StateFlow<Boolean> = _asking.asStateFlow()

    /**
     * Accepted during this run of the app: no second notice even if saving the acceptance in Ajustes failed or has not
     * landed yet (the next copy may come before the write).
     */
    @Volatile var acceptedNow: Boolean = false
        private set

    /** Waits for the notice with [text]; a newer copy replaces an older one still waiting. */
    @Synchronized fun ask(text: String) {
        pending = text
        _asking.value = true
    }

    /** The user accepted: the text to copy now, once (null if it was already taken or dropped). */
    @Synchronized fun accept(): String? {
        val text = pending ?: return null
        pending = null
        acceptedNow = true
        _asking.value = false
        return text
    }

    /** The user said no: nothing is copied. */
    fun cancel() = drop()

    /** Forgets the waiting copy, if any (the vault locked, or the app was left). */
    @Synchronized fun drop() {
        pending = null
        _asking.value = false
    }
}
