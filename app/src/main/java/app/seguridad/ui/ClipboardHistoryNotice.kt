package app.seguridad.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.seguridad.AppContainer
import app.seguridad.android.FilterObscuredTouches
import app.seguridad.tools.AcceptOutcome
import app.seguridad.tools.CLIPBOARD_COPY_FAILED
import app.seguridad.tools.CLIPBOARD_COPY_FAILED_OK
import app.seguridad.tools.CLIPBOARD_HISTORY_ACCEPT
import app.seguridad.tools.CLIPBOARD_HISTORY_CANCEL
import app.seguridad.tools.clipboardHistoryNoticeText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * A14: the notice before the first copy of a secret on Android 8 to 12, once for the whole app, at its root
 * (MainActivity), so no screen has to show it. The copy is made only on «Entendido, copiar», which is also saved in
 * Ajustes; «Cancelar», a tap outside or Back copy nothing. The text to copy never reaches this composition: it stays
 * in [AppContainer.clipboardNotice], which writes it itself on accept.
 */
@Composable
fun ClipboardHistoryNotice(container: AppContainer) {
    val notice = container.clipboardNotice
    val asking by notice.asking.collectAsStateWithLifecycle()
    // The write failed on accept (the clipboard service): said in a short dialog, since the notice is already gone.
    var copyFailed by rememberSaveable { mutableStateOf(false) }
    if (copyFailed) {
        AlertDialog(
            onDismissRequest = { copyFailed = false },
            text = { Text(CLIPBOARD_COPY_FAILED) },
            confirmButton = { TextButton(onClick = { copyFailed = false }) { Text(CLIPBOARD_COPY_FAILED_OK) } },
        )
    }
    if (!asking) return
    val settings by container.settings.settings.collectAsStateWithLifecycle()
    AlertDialog(
        onDismissRequest = notice::cancel,
        text = {
            FilterObscuredTouches()
            // The time chosen in Ajustes now: the copy made on accept is cleared after it.
            Text(clipboardHistoryNoticeText(settings.clipboard.seconds))
        },
        confirmButton = {
            TextButton(onClick = {
                // Writes the waiting copy (and its onCopied); nothing if the vault locked meanwhile (then the screen is
                // gone too, so nothing is said). A failed write is told, and the notice is not recorded as accepted.
                when (notice.acceptOutcome()) {
                    AcceptOutcome.COPIED -> Unit
                    AcceptOutcome.WRITE_FAILED -> { copyFailed = true; return@TextButton }
                    AcceptOutcome.LOCKED, AcceptOutcome.NOTHING_WAITING -> return@TextButton
                }
                // If saving fails (full storage), the notice is not shown again in this run and comes back next time.
                container.appScope.launch {
                    try {
                        container.settings.update { it.copy(clipboardHistoryNoticeAccepted = true) }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // Not saved: notice.acceptedNow keeps it from asking again until the app restarts.
                    }
                }
            }) { Text(CLIPBOARD_HISTORY_ACCEPT) }
        },
        dismissButton = { TextButton(onClick = notice::cancel) { Text(CLIPBOARD_HISTORY_CANCEL) } },
    )
}
