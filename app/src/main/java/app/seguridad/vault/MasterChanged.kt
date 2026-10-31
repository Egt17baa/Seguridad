package app.seguridad.vault

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The dialog after a change of master password (A6), kept as long as the process (in AppContainer), not in the
 * composition: a rotation while Argon2id derives (seconds) re-creates the activity, and the new one must still show it
 * (M-3). Nothing here is a secret: whether it is open, whether the fingerprint could not be turned off, and when the
 * replacement of the old copies was asked and what it answered. [replace] runs in [scope], so its answer is recorded
 * even when no screen is waiting for it.
 */
class MasterChanged(
    private val scope: CoroutineScope,
    replace: suspend (List<String>?) -> AutoBackupResult,
    private val now: () -> Long,
    /** The copies listed now (Settings.currentAutoBackupCopies): read once, when the password has changed ([changed]). */
    private val oldCopies: () -> List<String>? = { null },
) {
    data class State(
        val open: Boolean = false,
        /** The password changed but the fingerprint could not be turned off (PASSWORD_CHANGED_FINGERPRINT_FAILED). */
        val fingerprintFailed: Boolean = false,
        /** When «Borrar las copias automáticas anteriores» was pressed; 0 if not yet. */
        val requestedAt: Long = 0L,
        val answered: Boolean = false,
        val answerError: String? = null,
        /** What the answer said of the copies with a copy's name left unlisted ([AutoBackupResult.unlistedLeft], COPIAS-2). */
        val answerUnlisted: Int = 0,
        val busy: Boolean = false,
        /**
         * m2 (CR re-review): the copies listed when the password changed, the only ones «Borrar…» and «Volver a intentarlo»
         * replace (copies made after it are under the new password); null if none could be read.
         */
        val oldCopies: List<String>? = null,
    )

    private val replaceCopies = replace

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    /** Called once the new password is saved (the vault is then locked), from whichever screen changed it. */
    fun changed(fingerprintFailed: Boolean) {
        _state.value = State(open = true, fingerprintFailed = fingerprintFailed, oldCopies = oldCopies())
    }

    fun close() {
        _state.value = State()
    }

    /** Starts the replacement; false (nothing done) while one is running or the dialog is closed. */
    fun replace(): Boolean {
        var started: State? = null
        _state.update { s ->
            if (!s.open || s.busy) {
                started = null
                s
            } else {
                s.copy(requestedAt = now(), answered = false, answerError = null, answerUnlisted = 0, busy = true).also { started = it }
            }
        }
        val asked = started ?: return false
        scope.launch {
            val r = try {
                replaceCopies(asked.oldCopies)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AutoBackupResult("", null, AUTO_BACKUP_FAILED) // replace() reports its own failures; never closes the app.
            }
            // Only into the dialog that asked: closed (or opened again for another change) meanwhile, it is not reopened.
            _state.update { s -> if (s.open && s.requestedAt == asked.requestedAt && s.busy) s.copy(answered = true, answerError = r.error, answerUnlisted = r.unlistedLeft, busy = false) else s }
        }
        return true
    }
}
