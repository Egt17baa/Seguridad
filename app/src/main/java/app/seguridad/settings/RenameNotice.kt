package app.seguridad.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The one notice that the app used to be called «Seguridad» (owner's decision, 31-10-2026): only for those who updated
 * from an earlier version ([SettingsStore.decideRenameNotice]), at most once. It is marked as shown on disk the moment it
 * is shown ([showIfDue]), so a restart before «Entendido» does not bring it back; [visible] keeps the dialog up (also
 * across a rotation, as this lives in AppContainer) until [closed]. Never shown again in this run either, even if that
 * mark could not be saved.
 */
class RenameNotice(private val settings: SettingsStore) {
    private val _visible = MutableStateFlow(false)
    val visible: StateFlow<Boolean> = _visible.asStateFlow()

    @Volatile private var taken = false

    /** Shows it if it is due and was not shown yet; true when it was shown now. Must not throw. */
    suspend fun showIfDue(): Boolean {
        if (taken || settings.settings.value.renameNoticePending != true) return false
        taken = true
        _visible.value = true
        try {
            settings.update { it.copy(renameNoticePending = false) }
        } catch (e: Exception) {
            // A full disk: it is not shown again in this run ([taken]); the next start may show it once more.
        }
        return true
    }

    /** «Entendido». */
    fun closed() {
        _visible.value = false
    }
}
