package app.seguridad.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import app.seguridad.core.pickerLaunchError

/**
 * Launches a system file picker; null when it opened, or the message to show (no picker app, or any other failure)
 * instead of a crash.
 */
internal fun <I> ActivityResultLauncher<I>.launchOrError(input: I): String? =
    pickerLaunchError({ it is ActivityNotFoundException }) { launch(input) }

/**
 * Opens the share sheet with [text] (always public: an encrypted text, a signed text, a contact card) under [title]. True
 * if it opened. Never sets `VaultLock.keepOpenOnce`: that flag is reset by a result callback (pickers, the QR scanner) and
 * ACTION_SEND has none, so it would stay on; leaving for the chat locks the vault as usual. No read permission is granted
 * to the receiver: it is only text.
 */
internal fun shareText(context: Context, text: String, title: String): Boolean = try {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, title))
    true
} catch (e: Exception) {
    false
}
