package app.seguridad.settings

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The one notice that the app used to be called «Seguridad» (owner's decision, 31-10-2026): only for those who updated
 * from an earlier version ([SettingsStore.decideRenameNotice]). [visible] keeps the dialog up (this lives in AppContainer,
 * so a rotation keeps it) until [closed]. Marked as shown on disk only at «Entendido» ([closed]; controller decision, m5
 * and C2 of the CR re-review): if the process dies before, the next start shows it again (better seen twice than
 * never). That write runs in [scope] (the app's), not cancellable, so the screen going away cannot cut it. In this run it
 * is never shown again once taken, even if the mark could not be saved.
 */
class RenameNotice(private val settings: SettingsStore, private val scope: CoroutineScope) {
    private val _visible = MutableStateFlow(false)
    val visible: StateFlow<Boolean> = _visible.asStateFlow()

    @Volatile private var taken = false

    /** Shows it if it is due and was not shown yet in this run; true when it was shown now. Writes nothing. */
    fun showIfDue(): Boolean {
        if (taken || settings.settings.value.renameNoticePending != true) return false
        taken = true
        _visible.value = true
        return true
    }

    /** «Entendido»: the dialog goes, and the notice is marked as shown on disk. */
    fun closed() {
        _visible.value = false
        scope.launch {
            withContext(NonCancellable) {
                try {
                    settings.update { it.copy(renameNoticePending = false) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // A full disk: not shown again in this run ([taken]); the next start may show it once more.
                }
            }
        }
    }
}
