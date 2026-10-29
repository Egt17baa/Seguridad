package app.seguridad.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.seguridad.AppContainer
import app.seguridad.android.FilterObscuredTouches
import app.seguridad.android.copySensitiveNow
import app.seguridad.tools.CLIPBOARD_HISTORY_ACCEPT
import app.seguridad.tools.CLIPBOARD_HISTORY_CANCEL
import app.seguridad.tools.clipboardHistoryNoticeText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * A14: the notice before the first copy of a secret on Android 8 to 12, once for the whole app, at its root
 * (MainActivity), so no screen has to show it. The copy is made only on «Entendido, copiar», which is also saved in
 * Ajustes; «Cancelar», a tap outside or Back copy nothing. The text to copy never reaches this composition: it stays
 * in [AppContainer.clipboardNotice] and is handed over only on accept.
 */
@Composable
fun ClipboardHistoryNotice(container: AppContainer) {
    val notice = container.clipboardNotice
    val asking by notice.asking.collectAsStateWithLifecycle()
    if (!asking) return
    val settings by container.settings.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = notice::cancel,
        text = {
            FilterObscuredTouches()
            // The time chosen in Ajustes now: the copy made on accept is cleared after it.
            Text(clipboardHistoryNoticeText(settings.clipboard.seconds))
        },
        confirmButton = {
            TextButton(onClick = {
                val text = notice.accept() ?: return@TextButton
                copySensitiveNow(context, text)
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
