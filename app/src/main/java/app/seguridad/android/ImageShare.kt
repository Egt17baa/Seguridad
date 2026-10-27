package app.seguridad.android

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/** Must match android:authorities of the FileProvider in AndroidManifest.xml. */
private fun shareAuthority(context: Context) = "${context.packageName}.compartir"

/**
 * Hands [file] (prepared by ShareTemp in cacheDir/compartir/) to the app the user picks. The provider is not exported:
 * only the chosen app gets read access, to this one URI, through FLAG_GRANT_READ_URI_PERMISSION (the ClipData carries
 * the grant through the chooser). Like shareText, never sets VaultLock.keepOpenOnce: leaving for the other app locks
 * the vault as usual. Exceptions (no app can receive it, a file outside the provider's folder) go to the caller.
 */
fun shareImage(context: Context, file: File, mime: String) {
    val uri = FileProvider.getUriForFile(context, shareAuthority(context), file)
    val send = Intent(Intent.ACTION_SEND)
        .setType(mime)
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    send.clipData = ClipData.newRawUri(null, uri)
    context.startActivity(Intent.createChooser(send, null))
}
