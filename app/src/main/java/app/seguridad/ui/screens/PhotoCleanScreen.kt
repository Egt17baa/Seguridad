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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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

/** Writes [bytes] over the document at [uri] (from CreateDocument); throws if it cannot be opened. Blocking: IO. */
internal fun writeDocument(context: Context, uri: Uri, bytes: ByteArray) {
    val out = context.contentResolver.openOutputStream(uri, "wt") ?: throw java.io.IOException()
    try {
        out.write(bytes)
    } finally {
        out.close() // Explicit rather than use {}: lint's Recycle check does not follow use here.
    }
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
 * Reading, cleaning, saving and preparing a share run in [viewModelScope] with the application's resolver, so a
 * rotation cuts none of them; only opening the share sheet is the screen's (it needs the activity).
 */
private class PhotoCleanViewModel(private val appContext: Context, private val shareTemp: ShareTemp) : ViewModel() {
    var copy by mutableStateOf<CleanCopy?>(null)
    var opening by mutableStateOf(false)

    /** Saving or preparing a share. */
    var working by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var done by mutableStateOf<String?>(null)
    var shareReady by mutableStateOf<PreparedShare?>(null)
        private set
    private var job: Job? = null
    private var leftWhileBusy = false

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

    /** Saves the copy to [dest]; a document not fully written is deleted, whatever ended the work. */
    fun save(dest: Uri) {
        val c = copy ?: run {
            viewModelScope.launch(NonCancellable + Dispatchers.IO) { discardDocument(appContext, dest) }
            error = PHOTO_LOST
            return
        }
        working = true
        viewModelScope.launch {
            var written = false
            try {
                withContext(Dispatchers.IO) {
                    writeDocument(appContext, dest, c.bytes)
                    written = true
                }
                done = photoDoneMessage(c.report, c.converted)
                error = null
            } catch (e: CancellationException) {
                // The screen gone for good: a fully written copy stays; an empty document does not.
                if (!written) withContext(NonCancellable + Dispatchers.IO) { discardDocument(appContext, dest) }
                throw e
            } catch (e: Exception) {
                withContext(NonCancellable + Dispatchers.IO) { discardDocument(appContext, dest) } // Never a half-made copy.
                done = null
                error = PHOTO_SAVE_FAILED
            } finally {
                working = false
                releaseIfLeft()
            }
        }
    }

    /** Writes the copy for «Compartir»; [shareReady] then holds it until the screen has handed it over ([shared]). */
    fun share() {
        val c = copy ?: return
        working = true
        viewModelScope.launch {
            var prepared: File? = null
            var handedOver = false
            try {
                prepared = withContext(Dispatchers.IO) { shareTemp.prepare("$PHOTO_FILE_BASE.${c.ext}", c.bytes) }
                // The screen was left meanwhile: never a share sheet out of the blue when it comes back (finally drops it).
                if (!leftWhileBusy) {
                    shareReady = PreparedShare(prepared!!, c.mime)
                    handedOver = true
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                done = null
                error = PHOTO_SHARE_FAILED
            } finally {
                // Nothing was handed over: only the file this attempt wrote goes, and it need not wait the 10 minutes.
                if (!handedOver) {
                    prepared?.let { withContext(NonCancellable + Dispatchers.IO) { shareTemp.discard(it) } }
                    working = false
                    releaseIfLeft()
                }
            }
        }
    }

    /** The screen opened the share sheet for [ready] ([ok]), or could not. */
    fun shared(ready: PreparedShare, ok: Boolean) {
        if (shareReady !== ready) return
        shareReady = null
        val c = copy
        if (ok && c != null) {
            done = photoDoneMessage(c.report, c.converted)
            error = null
        } else {
            viewModelScope.launch(NonCancellable + Dispatchers.IO) { shareTemp.discard(ready.file) }
            done = null
            error = PHOTO_SHARE_FAILED
        }
        working = false
        releaseIfLeft()
    }

    fun shown() {
        leftWhileBusy = false
    }

    /**
     * The screen left without a configuration change (another tab, back): the copy (up to 50 MB) goes now, not when the
     * entry is destroyed. Opening is cancelled; a save under way first ends, unless the screen comes back first.
     */
    fun left() {
        shareReady?.let { shared(it, ok = false) } // Not handed over: never opened later, out of the blue.
        if (working) {
            leftWhileBusy = true
            return
        }
        job?.cancel()
        opening = false
        copy = null
        error = null
        done = null
    }

    private fun releaseIfLeft() {
        if (leftWhileBusy) {
            leftWhileBusy = false
            copy = null
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
    val appContext = context.applicationContext
    val vm = viewModel { PhotoCleanViewModel(appContext, shareTemp) }
    val copy = vm.copy
    val busy = vm.working || vm.opening
    var pickerError by remember { mutableStateOf<String?>(null) }

    val activity = context.fragmentActivity()
    // Only a rotation (or another configuration change) keeps the copy: any other way out lets it go now.
    DisposableEffect(vm) {
        vm.shown()
        onDispose { if (activity?.isChangingConfigurations != true) vm.left() }
    }
    // The copy for «Compartir» is ready: the share sheet needs this activity, so the screen opens it.
    vm.shareReady?.let { ready ->
        LaunchedEffect(ready) {
            val ok = try {
                shareImage(context, ready.file, ready.mime)
                true
            } catch (e: Exception) {
                false
            }
            vm.shared(ready, ok)
        }
    }

    val onSaveTarget: (Uri?) -> Unit = { dest -> if (dest != null) vm.save(dest) }
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
                        onClick = vm::share,
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
