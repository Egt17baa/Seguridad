package app.seguridad.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import app.seguridad.android.FilterObscuredTouches
import app.seguridad.core.documentAuthorityHost
import app.seguridad.core.isAcceptableDocumentUri
import app.seguridad.core.pickerLaunchError
import app.seguridad.tools.PICKER_REJECTED
import app.seguridad.tools.PICKER_REJECTED_OK

/**
 * Launches a system file picker; null when it opened, or the message to show (no picker app, or any other failure)
 * instead of a crash.
 */
internal fun <I> ActivityResultLauncher<I>.launchOrError(input: I): String? =
    pickerLaunchError({ it is ActivityNotFoundException }) { launch(input) }

/**
 * Every file or folder picker of the app (OpenDocument, CreateDocument, OpenDocumentTree) is registered here, never
 * with rememberLauncherForActivityResult directly (DocumentUriTest checks it). ANDROID-2 (+CADENAS-1/2, audit 1.3.0):
 * the URI handed back is checked before [onResult] sees it ([isAcceptableDocument]). One that is not another app's
 * document (a `file://`, or one of Seguridad's own providers) reaches [onResult] as null, exactly as a cancel, so
 * nothing is read, written nor discarded, and [PICKER_REJECTED] is shown in a dialog. Each screen's own cancel path
 * (resetting VaultLock.keepOpenOnce among others) runs as usual.
 */
@Composable
internal fun <I> rememberDocumentPicker(
    contract: ActivityResultContract<I, Uri?>,
    onResult: (Uri?) -> Unit,
): ManagedActivityResultLauncher<I, Uri?> {
    val context = LocalContext.current
    var rejected by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(contract) { uri ->
        val accepted = uri?.takeIf { isAcceptableDocument(context, it) }
        if (uri != null && accepted == null) rejected = true
        onResult(accepted)
    }
    if (rejected) {
        AlertDialog(
            onDismissRequest = { rejected = false },
            text = {
                FilterObscuredTouches()
                Text(PICKER_REJECTED)
            },
            confirmButton = { TextButton(onClick = { rejected = false }) { Text(PICKER_REJECTED_OK) } },
        )
    }
    return launcher
}

/**
 * [uri] is a `content://` of another app's provider ([isAcceptableDocumentUri]), and the provider of its authority is
 * not one of Seguridad's, whatever its name. Never throws.
 */
internal fun isAcceptableDocument(context: Context, uri: Uri): Boolean {
    if (!isAcceptableDocumentUri(uri.scheme, uri.authority, context.packageName)) return false
    val host = documentAuthorityHost(uri.authority) ?: return false
    val owner = try {
        val pm = context.packageManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.resolveContentProvider(host, PackageManager.ComponentInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.resolveContentProvider(host, 0)
        }?.packageName
    } catch (e: Exception) {
        null // Not visible to the app (package visibility) or not found: not one of ours, which are always visible.
    }
    return owner != context.packageName
}

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
