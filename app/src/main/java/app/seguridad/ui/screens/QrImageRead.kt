package app.seguridad.ui.screens

import android.content.Context
import android.net.Uri
import app.seguridad.android.decodeQrFromImage
import app.seguridad.tools.QR_IMAGE_DAMAGED
import app.seguridad.tools.QR_IMAGE_TOO_BIG
import app.seguridad.tools.QR_IMAGE_TOO_LARGE
import app.seguridad.tools.QR_NOT_FOUND
import app.seguridad.tools.imageOpenFailureText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** What reading a QR code from a picked image gave ([readQrFromImage]). */
sealed interface QrImageRead {
    /** The code's [text]. It may be a wifi's password or a 2FA key: its [toString] never shows it. */
    class Text(val text: String) : QrImageRead {
        override fun toString() = "QrImageRead.Text"
    }

    /** Why nothing was read: one of the inspector's fixed texts ([message]), never anything of the image. */
    class Error(val message: String) : QrImageRead {
        override fun toString() = "QrImageRead.Error"
    }
}

/**
 * The QR code in the image at [uri], for the QR inspector and «Añadir → Red WiFi» (2.1, task 2): the file read in
 * [Dispatchers.IO] with its limits ([readPhoto]), decoded in [Dispatchers.Default] with its own ([decodeQrFromImage]),
 * and its bytes set to zero as soon as it is decoded (the picture of a 2FA or wifi QR is that secret too). No code in it
 * is [QR_NOT_FOUND]; a file too big, an image too large, memory running out (up to 50 MB read: on a small heap that is
 * the error, M-7, not a crash) or anything else have the inspector's texts. Cancelled with the caller's scope (the
 * screen left): nothing comes back then.
 */
suspend fun readQrFromImage(context: Context, uri: Uri): QrImageRead = try {
    val bytes = withContext(Dispatchers.IO) { readPhoto(context, uri) }
    currentCoroutineContext().ensureActive()
    val text = withContext(Dispatchers.Default) {
        try {
            decodeQrFromImage(bytes)
        } finally {
            bytes.fill(0)
        }
    }
    currentCoroutineContext().ensureActive()
    if (text == null) QrImageRead.Error(QR_NOT_FOUND) else QrImageRead.Text(text)
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    QrImageRead.Error(imageOpenFailureText(e, QR_IMAGE_TOO_BIG, QR_IMAGE_TOO_LARGE) { QR_IMAGE_DAMAGED })
} catch (e: OutOfMemoryError) {
    QrImageRead.Error(imageOpenFailureText(e, QR_IMAGE_TOO_BIG, QR_IMAGE_TOO_LARGE) { QR_IMAGE_DAMAGED })
}
