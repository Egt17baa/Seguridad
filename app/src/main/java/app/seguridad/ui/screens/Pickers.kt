package app.seguridad.ui.screens

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import app.seguridad.ui.theme.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import app.seguridad.SeguridadApp
import app.seguridad.android.FilterObscuredTouches
import app.seguridad.core.PickerPlace
import app.seguridad.core.PickerPlaces
import app.seguridad.core.documentAuthorityHost
import app.seguridad.core.isAcceptableDocumentUri
import app.seguridad.core.pickerLaunchError
import app.seguridad.core.restoredFileMime
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
 *
 * 1.4.2, the owner's request: [place] is this picker's own place ([PickerPlace]). It opens where it was used last
 * (`EXTRA_INITIAL_URI`, which the provider may follow or not), and an accepted document becomes its place; null for a
 * picker that says where it starts itself (the folder of the automatic copies, [folderStart]).
 */
@Composable
internal fun <I> rememberDocumentPicker(
    contract: ActivityResultContract<I, Uri?>,
    place: PickerPlace?,
    onResult: (Uri?) -> Unit,
): ManagedActivityResultLauncher<I, Uri?> {
    val context = LocalContext.current
    val places = pickerPlaces(context)
    var rejected by remember { mutableStateOf(false) }
    val starting = if (place == null || places == null) contract else StartingAt(contract) { places.initial(place)?.let(Uri::parse) }
    val launcher = rememberLauncherForActivityResult(starting) { uri ->
        val accepted = uri?.takeIf { isAcceptableDocument(context, it) }
        if (uri != null && accepted == null) rejected = true
        if (accepted != null && place != null) places?.remember(place, accepted.toString())
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

/** The app's [PickerPlaces], or null outside it (a preview). */
internal fun pickerPlaces(context: Context): PickerPlaces? = (context.applicationContext as? SeguridadApp)?.container?.pickerPlaces

/** [inner], starting where [start] says (`EXTRA_INITIAL_URI`), unless [inner] already says where. */
private class StartingAt<I>(private val inner: ActivityResultContract<I, Uri?>, private val start: () -> Uri?) : ActivityResultContract<I, Uri?>() {
    override fun createIntent(context: Context, input: I): Intent = inner.createIntent(context, input).also { intent ->
        if (!intent.hasExtra(DocumentsContract.EXTRA_INITIAL_URI)) start()?.let { intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, it) }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? = inner.parseResult(resultCode, intent)
}

/**
 * Where the folder picker of the automatic copies starts: the folder chosen ([folder], a tree URI), as the document it
 * points at (`EXTRA_INITIAL_URI` wants a document). Null without a folder or if it cannot be read. Never throws.
 */
internal fun folderStart(folder: String?): Uri? = try {
    folder?.let(Uri::parse)?.takeIf { it.scheme == "content" }?.let { DocumentsContract.buildDocumentUriUsingTree(it, DocumentsContract.getTreeDocumentId(it)) }
} catch (e: Exception) {
    null
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
 * CreateDocument for a restored file (a decrypted file or attachment), with the input as the proposed name and the
 * MIME type of its extension ([restoredFileMime]), so that a name already taken becomes «foto (1).jpg» and not
 * «foto.jpg (1)», which no app opens (owner's phone test, 1-11-2026). Like [ActivityResultContracts.CreateDocument],
 * plus `CATEGORY_OPENABLE` (the file is written as a stream, so a virtual document cannot be chosen to overwrite); it
 * is registered through [rememberDocumentPicker] like every picker.
 */
internal class CreateRestoredDocument : ActivityResultContract<String, Uri?>() {
    override fun createIntent(context: Context, input: String): Intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(restoredFileMime(input) { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it) })
            .putExtra(Intent.EXTRA_TITLE, input)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? = intent.takeIf { resultCode == Activity.RESULT_OK }?.data
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
