package app.seguridad.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.seguridad.android.heifToJpeg
import app.seguridad.android.shareImage
import app.seguridad.core.readAtMost
import app.seguridad.tools.MAX_IMAGE_BYTES
import app.seguridad.tools.PHOTO_ANALYSING
import app.seguridad.tools.PHOTO_EXPLANATION
import app.seguridad.tools.PHOTO_FILE_NAME_NOTE
import app.seguridad.tools.PHOTO_FOUND_TITLE
import app.seguridad.tools.PHOTO_HEIF_NOTICE
import app.seguridad.tools.PHOTO_LOST
import app.seguridad.tools.PHOTO_NOTHING_FOUND
import app.seguridad.tools.PHOTO_PICK
import app.seguridad.tools.PHOTO_PICK_OTHER
import app.seguridad.tools.PHOTO_SAVE_COPY
import app.seguridad.tools.PHOTO_SAVE_FAILED
import app.seguridad.tools.PHOTO_SHARE_FAILED
import app.seguridad.tools.PHOTO_TOOL_TITLE
import app.seguridad.tools.PHOTO_TOO_BIG
import app.seguridad.tools.SHARE
import app.seguridad.tools.photo.PHOTO_DAMAGED
import app.seguridad.tools.photo.PHOTO_UNSUPPORTED
import app.seguridad.tools.photo.PhotoCleaner
import app.seguridad.tools.photo.PhotoFailure
import app.seguridad.tools.photo.PhotoFormat
import app.seguridad.tools.photo.PhotoReport
import app.seguridad.tools.photo.detectFormat
import app.seguridad.tools.photoDoneMessage
import app.seguridad.tools.photoFoundLines
import app.seguridad.tools.photoHasSomethingToRemove
import app.seguridad.tools.share.ShareTemp
import app.seguridad.ui.theme.BackScaffold
import app.seguridad.ui.theme.NegativeColor
import app.seguridad.ui.theme.NoCopy
import app.seguridad.ui.theme.SectionCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The cleaned copy, kept only in memory (never in saved state: the screen rebuilt means «vuelve a elegir la foto»).
 * [report] is null for HEIC/HEIF/AVIF, which is re-encoded from the pixels and keeps nothing of the original file.
 */
private class CleanCopy(val report: PhotoReport?, val bytes: ByteArray, val ext: String, val mime: String)

private class PhotoTooBig : Exception()

/**
 * Reads the picked file, refusing anything over [MAX_IMAGE_BYTES] before it is loaded: first by the size the provider
 * reports (when it does), then by reading at most one byte more than the limit (the reported size may be missing or
 * wrong). Never writes to [uri]: the original is only opened for reading.
 */
private fun readPhoto(context: Context, uri: Uri): ByteArray {
    val size = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null
        }
    }.getOrNull()
    if (size != null && size > MAX_IMAGE_BYTES) throw PhotoTooBig()
    val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readAtMost(MAX_IMAGE_BYTES + 1) }
    if (bytes.size > MAX_IMAGE_BYTES) throw PhotoTooBig()
    return bytes
}

/** CPU work, on Dispatchers.Default: only HEIF goes to the JPEG conversion; an unknown format is refused. */
private fun cleanCopy(bytes: ByteArray): CleanCopy = when (detectFormat(bytes)) {
    PhotoFormat.HEIF -> CleanCopy(null, heifToJpeg(bytes), "jpg", "image/jpeg")
    PhotoFormat.JPEG -> CleanCopy(PhotoCleaner.inspect(bytes), PhotoCleaner.clean(bytes), "jpg", "image/jpeg")
    PhotoFormat.PNG -> CleanCopy(PhotoCleaner.inspect(bytes), PhotoCleaner.clean(bytes), "png", "image/png")
    PhotoFormat.WEBP -> CleanCopy(PhotoCleaner.inspect(bytes), PhotoCleaner.clean(bytes), "webp", "image/webp")
    null -> throw PhotoFailure(PHOTO_UNSUPPORTED)
}

/**
 * «Limpiar foto»: shows what the picked photo carries and makes a copy without it, saved where the user chooses or
 * shared through the FileProvider. The original is never written. No vault access, so the pickers do not keep the vault
 * open (like «Huella de archivo»). Errors are always annex texts or the fixed ones in ToolTexts; nothing is logged.
 */
@Composable
fun PhotoCleanScreen(shareTemp: ShareTemp, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var copy by remember { mutableStateOf<CleanCopy?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf<String?>(null) }
    var pickerError by remember { mutableStateOf<String?>(null) }

    val onSaveTarget: (Uri?) -> Unit = saved@{ dest ->
        if (dest == null) return@saved
        val c = copy ?: run {
            discardDocument(context, dest)
            error = PHOTO_LOST
            return@saved
        }
        busy = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(dest, "wt")!!.use { it.write(c.bytes) } }
                done = photoDoneMessage(c.report)
                error = null
            } catch (e: Exception) {
                discardDocument(context, dest) // Never a half-written copy.
                done = null
                error = PHOTO_SAVE_FAILED
            } finally {
                busy = false
            }
        }
    }
    val saveJpg = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg"), onSaveTarget)
    val savePng = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png"), onSaveTarget)
    val saveWebp = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/webp"), onSaveTarget)

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        copy = null // The previous copy goes before the next file is read.
        done = null
        error = null
        busy = true
        scope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) { readPhoto(context, uri) }
                copy = withContext(Dispatchers.Default) { cleanCopy(bytes) }
            } catch (e: PhotoTooBig) {
                error = PHOTO_TOO_BIG
            } catch (e: PhotoFailure) {
                error = e.message ?: PHOTO_DAMAGED
            } catch (e: Exception) {
                error = PHOTO_DAMAGED
            } catch (e: OutOfMemoryError) {
                // Up to 50 MB plus its copy: on a small heap this is the error to show, not a crash.
                error = PHOTO_DAMAGED
            } finally {
                busy = false
            }
        }
    }

    BackScaffold(PHOTO_TOOL_TITLE, onBack) { padding ->
        NoCopy {
            Column(
                Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(PHOTO_EXPLANATION, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(
                    onClick = { pickerError = pick.launchOrError(arrayOf("image/*")) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (copy == null) PHOTO_PICK else PHOTO_PICK_OTHER) }
                pickerError?.let { Text(it, color = NegativeColor) }
                if (busy) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(PHOTO_ANALYSING, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                error?.let { Text(it, color = NegativeColor) }
                copy?.let { c -> CopyCard(c) }
                copy?.let { c ->
                    Text(PHOTO_FILE_NAME_NOTE, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    Button(
                        onClick = {
                            val launcher = when (c.ext) { "png" -> savePng; "webp" -> saveWebp; else -> saveJpg }
                            pickerError = launcher.launchOrError("foto-limpia.${c.ext}")
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(PHOTO_SAVE_COPY) }
                    FilledTonalButton(
                        onClick = {
                            busy = true
                            scope.launch {
                                try {
                                    val file = withContext(Dispatchers.IO) { shareTemp.prepare("foto-limpia.${c.ext}", c.bytes) }
                                    shareImage(context, file, c.mime)
                                    done = photoDoneMessage(c.report)
                                    error = null
                                } catch (e: Exception) {
                                    // Nothing was handed over: the copy need not wait the 10 minutes.
                                    withContext(Dispatchers.IO) { shareTemp.wipeAll() }
                                    done = null
                                    error = PHOTO_SHARE_FAILED
                                } finally {
                                    busy = false
                                }
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(SHARE) }
                }
                done?.let { Text(it) }
            }
        }
    }
}

/** «Lo que trae la foto», or the HEIC notice (a converted photo has no list: nothing of the original goes with it). */
@Composable
private fun CopyCard(c: CleanCopy) {
    SectionCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val report = c.report
            if (report == null) {
                Text(PHOTO_HEIF_NOTICE)
                return@Column
            }
            val lines = photoFoundLines(report)
            if (lines.isNotEmpty()) {
                Text(PHOTO_FOUND_TITLE, style = MaterialTheme.typography.titleSmall)
                lines.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
            }
            if (!photoHasSomethingToRemove(report)) Text(PHOTO_NOTHING_FOUND, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
