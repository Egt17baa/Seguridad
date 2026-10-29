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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.seguridad.android.heifToJpeg
import app.seguridad.android.shareImage
import app.seguridad.core.readAtMost
import app.seguridad.tools.ImageTooBig
import app.seguridad.tools.imageOpenFailureText
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
import app.seguridad.tools.PHOTO_TOO_LARGE
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
import app.seguridad.tools.share.PHOTO_FILE_BASE
import app.seguridad.tools.share.ShareTemp
import app.seguridad.ui.theme.BackScaffold
import app.seguridad.ui.theme.NegativeColor
import app.seguridad.ui.theme.NoCopy
import app.seguridad.ui.theme.SectionCard
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The cleaned copy, kept only in memory: in the screen's [PhotoCleanViewModel], so a rotation, a theme change or a split
 * screen keeps it, and never in saved state (up to 50 MB; a Bundle can go to disk): the process gone means «vuelve a
 * elegir la foto».
 * When [converted] (HEIC/HEIF/AVIF re-encoded as JPEG), [report] is the converted JPEG's own, so a colour profile the
 * encoder embedded is shown; otherwise it is the original's.
 */
private class CleanCopy(val report: PhotoReport, val converted: Boolean, val bytes: ByteArray, val ext: String, val mime: String)

/**
 * Reads the picked file, refusing anything over [MAX_IMAGE_BYTES] ([ImageTooBig]) before it is loaded: first by the size
 * the provider reports (when it does), then by reading at most one byte more than the limit (the reported size may be
 * missing or wrong). With a reported size the bytes go into one array of that size (M-7: no growing buffer and no final
 * copy, so a 50 MB photo needs about 50 MB, not 115). Never writes to [uri]: the original is only opened for reading.
 * Shared with «Tachar en imagen» and the QR inspector.
 */
internal fun readPhoto(context: Context, uri: Uri): ByteArray {
    val size = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null
        }
    }.getOrNull()
    if (size != null && size > MAX_IMAGE_BYTES) throw ImageTooBig()
    val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readAtMost(MAX_IMAGE_BYTES + 1, expected = size) }
    if (bytes.size > MAX_IMAGE_BYTES) throw ImageTooBig()
    return bytes
}

/**
 * CPU work, on Dispatchers.Default: only HEIF goes to the JPEG conversion; an unknown format is refused. Stops between
 * steps once the screen is gone.
 */
private suspend fun cleanCopy(bytes: ByteArray): CleanCopy = withContext(Dispatchers.Default) {
    fun same(ext: String, mime: String): CleanCopy {
        val report = PhotoCleaner.inspect(bytes)
        ensureActive()
        return CleanCopy(report, false, PhotoCleaner.clean(bytes), ext, mime)
    }
    when (detectFormat(bytes)) {
        PhotoFormat.HEIF -> {
            val jpeg = heifToJpeg(bytes)
            ensureActive()
            CleanCopy(PhotoCleaner.inspect(jpeg), true, jpeg, "jpg", "image/jpeg")
        }
        PhotoFormat.JPEG -> same("jpg", "image/jpeg")
        PhotoFormat.PNG -> same("png", "image/png")
        PhotoFormat.WEBP -> same("webp", "image/webp")
        null -> throw PhotoFailure(PHOTO_UNSUPPORTED)
    }
}

/**
 * What «Limpiar foto» opened, for its navigation entry: kept across a configuration change (M-1), lost with the process.
 * Reading and cleaning run in [viewModelScope], so a rotation does not cut them; saving and sharing stay with the
 * screen (they need its activity, and a cut save already discards its document).
 */
private class PhotoCleanViewModel(private val appContext: Context) : ViewModel() {
    var copy by mutableStateOf<CleanCopy?>(null)
    var opening by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var done by mutableStateOf<String?>(null)
    private var job: Job? = null

    private fun failureText(t: Throwable) =
        imageOpenFailureText(t, PHOTO_TOO_BIG, PHOTO_TOO_LARGE) { (it as? PhotoFailure)?.message ?: PHOTO_DAMAGED }

    fun open(uri: Uri) {
        job?.cancel()
        copy = null // The previous copy goes before the next file is read.
        done = null
        error = null
        opening = true
        job = viewModelScope.launch {
            val mine = coroutineContext[Job]
            try {
                val bytes = withContext(Dispatchers.IO) { readPhoto(appContext, uri) }
                ensureActive()
                copy = cleanCopy(bytes)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = failureText(e)
            } catch (e: OutOfMemoryError) {
                // Up to 50 MB plus its copy: on a small heap this is the error to show (M-7), not a crash.
                error = failureText(e)
            } finally {
                if (job === mine) opening = false
            }
        }
    }

    override fun onCleared() {
        copy = null // Not a secret, but up to 50 MB: it goes with the screen.
    }
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
    val appContext = context.applicationContext
    val vm = viewModel { PhotoCleanViewModel(appContext) }
    val copy = vm.copy
    // Saving or sharing (the screen's own work); opening is the ViewModel's.
    var working by remember { mutableStateOf(false) }
    val busy = working || vm.opening
    var pickerError by remember { mutableStateOf<String?>(null) }

    val onSaveTarget: (Uri?) -> Unit = saved@{ dest ->
        if (dest == null) return@saved
        val c = vm.copy ?: run {
            discardDocument(context, dest)
            vm.error = PHOTO_LOST
            return@saved
        }
        working = true
        scope.launch {
            var written = false
            try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(dest, "wt")!!.use { it.write(c.bytes) }
                    written = true
                }
                vm.done = photoDoneMessage(c.report, c.converted)
                vm.error = null
            } catch (e: CancellationException) {
                // Screen gone: a fully written copy stays; an empty document does not.
                if (!written) discardDocument(context, dest)
                throw e
            } catch (e: Exception) {
                discardDocument(context, dest) // Never a half-written copy.
                vm.done = null
                vm.error = PHOTO_SAVE_FAILED
            } finally {
                working = false
            }
        }
    }
    val saveJpg = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg"), onSaveTarget)
    val savePng = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png"), onSaveTarget)
    val saveWebp = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/webp"), onSaveTarget)

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.open(uri)
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
                vm.error?.let { Text(it, color = NegativeColor) }
                copy?.let { c -> CopyCard(c) }
                copy?.let { c ->
                    Text(PHOTO_FILE_NAME_NOTE, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    Button(
                        onClick = {
                            val launcher = when (c.ext) { "png" -> savePng; "webp" -> saveWebp; else -> saveJpg }
                            pickerError = launcher.launchOrError("$PHOTO_FILE_BASE.${c.ext}")
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(PHOTO_SAVE_COPY) }
                    FilledTonalButton(
                        onClick = {
                            working = true
                            scope.launch {
                                var prepared: File? = null
                                // Nothing was handed over: only the file this attempt wrote goes (prepare already
                                // removed any earlier one), and it need not wait the 10 minutes.
                                suspend fun discardPrepared() {
                                    prepared?.let { withContext(NonCancellable + Dispatchers.IO) { shareTemp.discard(it) } }
                                }
                                try {
                                    val file = withContext(Dispatchers.IO) {
                                        shareTemp.prepare("$PHOTO_FILE_BASE.${c.ext}", c.bytes).also { prepared = it }
                                    }
                                    shareImage(context, file, c.mime)
                                    vm.done = photoDoneMessage(c.report, c.converted)
                                    vm.error = null
                                } catch (e: CancellationException) {
                                    discardPrepared()
                                    throw e
                                } catch (e: Exception) {
                                    discardPrepared()
                                    vm.done = null
                                    vm.error = PHOTO_SHARE_FAILED
                                } finally {
                                    working = false
                                }
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(SHARE) }
                }
                vm.done?.let { Text(it) }
            }
        }
    }
}

/**
 * «Lo que trae la foto». A converted HEIC shows the notice and then what its JPEG keeps (only a colour profile can be
 * there), never «No hemos encontrado…»: the original's metadata was not read.
 */
@Composable
private fun CopyCard(c: CleanCopy) {
    SectionCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (c.converted) Text(PHOTO_HEIF_NOTICE)
            val lines = photoFoundLines(c.report)
            if (lines.isNotEmpty()) {
                if (!c.converted) Text(PHOTO_FOUND_TITLE, style = MaterialTheme.typography.titleSmall)
                lines.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
            }
            if (!c.converted && !photoHasSomethingToRemove(c.report)) {
                Text(PHOTO_NOTHING_FOUND, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
