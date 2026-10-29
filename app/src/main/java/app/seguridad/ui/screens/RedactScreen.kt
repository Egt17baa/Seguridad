package app.seguridad.ui.screens

import android.graphics.Bitmap
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.graphics.scale
import app.seguridad.android.RedactSource
import app.seguridad.android.decodeForRedaction
import app.seguridad.android.renderRedacted
import app.seguridad.android.shareImage
import app.seguridad.tools.PHOTO_DONE_SHORT
import app.seguridad.tools.PHOTO_SAVE_COPY
import app.seguridad.tools.PHOTO_SAVE_FAILED
import app.seguridad.tools.PHOTO_SHARE_FAILED
import app.seguridad.tools.imageOpenFailureText
import app.seguridad.tools.redactCanvasDescription
import app.seguridad.tools.REDACT_CHANGED
import app.seguridad.tools.REDACT_COPY_FAILED
import app.seguridad.tools.REDACT_DAMAGED
import app.seguridad.tools.REDACT_EXPLANATION
import app.seguridad.tools.REDACT_LIMITS
import app.seguridad.tools.REDACT_LOST
import app.seguridad.tools.REDACT_OPENING
import app.seguridad.tools.REDACT_PICK
import app.seguridad.tools.REDACT_PICK_OTHER
import app.seguridad.tools.REDACT_REDO
import app.seguridad.tools.REDACT_RENDERING
import app.seguridad.tools.REDACT_TOOL_TITLE
import app.seguridad.tools.REDACT_TOO_BIG
import app.seguridad.tools.REDACT_TOO_LARGE
import app.seguridad.tools.REDACT_UNDO
import app.seguridad.tools.SHARE
import app.seguridad.tools.photo.PHOTO_DAMAGED
import app.seguridad.tools.photo.PHOTO_UNSUPPORTED
import app.seguridad.tools.photo.PhotoFailure
import app.seguridad.tools.photo.PhotoFormat
import app.seguridad.tools.redact.Box as RedactBox
import app.seguridad.tools.redact.RedactHistory
import app.seguridad.tools.redact.ViewRect
import app.seguridad.tools.redact.boxFromDrag
import app.seguridad.tools.redact.boxToView
import app.seguridad.tools.redact.redactFileName
import app.seguridad.tools.redact.redactMegapixels
import app.seguridad.tools.redact.redactMime
import app.seguridad.tools.redact.redactOutputFormat
import app.seguridad.tools.redactDownscaledText
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
import kotlin.math.max

/** Longest side of the bitmap the screen draws: a full 24 MP one may be too big for the GPU to show. */
private const val PREVIEW_MAX_SIDE = 2048

/**
 * The full-size decoded image, with a count of the copies being made from it. [close] (the screen gone, or another
 * image picked) recycles it at once if no copy is in progress, or else when the last one gives it back: never while
 * a copy is still drawing from it.
 */
private class SourceHolder(private val bitmap: Bitmap) {
    private var users = 0
    private var closed = false

    @Synchronized
    fun borrow(): Bitmap? {
        if (closed) return null
        users++
        return bitmap
    }

    @Synchronized
    fun giveBack() {
        users--
        if (closed && users == 0) bitmap.recycle()
    }

    @Synchronized
    fun close() {
        if (closed) return
        closed = true
        if (users == 0) bitmap.recycle()
    }
}

/**
 * The picked image, only in memory: in the screen's [RedactViewModel], so a rotation, a theme change or a split screen
 * keeps it with its boxes (M-1), and never in saved state (a bitmap; a Bundle can go to disk): the process gone means
 * «vuelve a elegir la imagen». The boxes are in pixels of the full-size image ([width]×[height]); the [preview] is the same image, smaller, and the
 * screen draws the boxes on it at the same image coordinates the copy uses.
 */
private class Picture(
    val holder: SourceHolder,
    val previewBitmap: Bitmap,
    val preview: ImageBitmap,
    val width: Int,
    val height: Int,
    val output: PhotoFormat,
    val downscaledNote: String?,
) {
    val history = RedactHistory()
}

/** A copy in the share folder, waiting for the screen to open the share sheet with it. */
internal class PreparedShare(val file: File, val mime: String)

/** The boxes changed while the copy was being made: it is thrown away. */
private class BoxesChanged : Exception()

/** On Dispatchers.Default: the preview of [source]. Its own bitmap, so recycling the source never touches it. */
private fun makePicture(source: RedactSource): Picture {
    val src = source.bitmap
    val scale = minOf(1.0, PREVIEW_MAX_SIDE.toDouble() / max(src.width, src.height))
    val pw = (src.width * scale).toInt().coerceAtLeast(1)
    val ph = (src.height * scale).toInt().coerceAtLeast(1)
    val scaled = src.scale(pw, ph, filter = true) // the same object when no scaling is needed
    val preview = if (scaled === src) src.copy(Bitmap.Config.ARGB_8888, false) ?: throw PhotoFailure(PHOTO_DAMAGED) else scaled
    return Picture(
        SourceHolder(src),
        preview,
        preview.asImageBitmap(),
        src.width,
        src.height,
        redactOutputFormat(source.format),
        if (source.downscaled) redactDownscaledText(redactMegapixels(src.width, src.height)) else null,
    )
}

/** On Dispatchers.Default: the copy with [boxes]. Fails with PhotoFailure, never a half-made copy. */
private suspend fun renderCopy(p: Picture, boxes: List<RedactBox>): ByteArray = withContext(Dispatchers.Default) {
    val bitmap = p.holder.borrow() ?: throw PhotoFailure(PHOTO_DAMAGED)
    try {
        renderRedacted(bitmap, boxes, p.output)
    } catch (e: OutOfMemoryError) {
        throw PhotoFailure(PHOTO_DAMAGED)
    } finally {
        p.holder.giveBack()
    }
}

/**
 * What «Tachar» has open, for its navigation entry: the [Picture] and its boxes survive a configuration change, and the
 * bitmaps are recycled when another image is picked (the preview once it has left the composition, [RedactCanvas]) or
 * when the screen is left without a configuration change ([left]) or the entry is gone for good ([onCleared]). Opening,
 * saving and preparing a share run in [viewModelScope] with the application's resolver, so a rotation cuts none of
 * them; only opening the share sheet is the screen's (it needs the activity).
 */
private class RedactViewModel(private val appContext: Context, private val shareTemp: ShareTemp) : ViewModel() {
    var picture by mutableStateOf<Picture?>(null)
        private set
    var boxes by mutableStateOf(emptyList<RedactBox>())
        private set
    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set
    var busy by mutableStateOf<String?>(null)
    var error by mutableStateOf<String?>(null)
    var done by mutableStateOf<String?>(null)

    /** A copy prepared for «Compartir», for the screen to hand to the system's share sheet (it needs the activity). */
    var shareReady by mutableStateOf<PreparedShare?>(null)
        private set
    private var job: Job? = null

    /** The screen left for good meanwhile: what it held goes once the copy being made is done ([releaseIfLeft]). */
    private var leftWhileBusy = false

    fun sync() {
        val p = picture
        boxes = p?.history?.boxes.orEmpty()
        canUndo = p?.history?.canUndo == true
        canRedo = p?.history?.canRedo == true
        done = null // A change after saving: the saved copy no longer matches what is shown.
    }

    private fun failureText(t: Throwable) = imageOpenFailureText(t, REDACT_TOO_BIG, REDACT_TOO_LARGE) {
        if (it is PhotoFailure && it.message == PHOTO_UNSUPPORTED) PHOTO_UNSUPPORTED else REDACT_DAMAGED
    }

    fun open(uri: Uri) {
        job?.cancel()
        // The previous image goes before the next file is read (its preview, once RedactCanvas leaves the composition).
        picture?.holder?.close()
        picture = null
        sync()
        error = null
        busy = REDACT_OPENING
        job = viewModelScope.launch {
            val mine = coroutineContext[Job]
            var opened: RedactSource? = null
            try {
                val bytes = withContext(Dispatchers.IO) { readPhoto(appContext, uri) }
                ensureActive()
                val p = withContext(Dispatchers.Default) {
                    val source = decodeForRedaction(bytes).also { opened = it }
                    ensureActive()
                    makePicture(source)
                }
                opened = null // Owned by the Picture from here on.
                picture = p
                sync()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = failureText(e)
            } catch (e: OutOfMemoryError) {
                // Up to 50 MB of file and a 24 MP bitmap: on a small heap this is the error to show (M-7), not a crash.
                error = failureText(e)
            } finally {
                opened?.bitmap?.recycle() // Decoded but never shown (cancelled or failed after decoding).
                if (job === mine) busy = null
            }
        }
    }

    // A copy is made from the boxes at the moment the button was pressed; if they differ when it is ready (input is
    // already blocked while busy, so this is the second line), nothing is saved or shared.
    private fun stillMatches(p: Picture, snapshot: List<RedactBox>) = picture === p && p.history.boxes == snapshot

    /**
     * Saves the copy to [dest] (from CreateDocument), in [viewModelScope] with the application's resolver: a rotation does
     * not cut it. A document that was not fully written is deleted, whatever ended the work.
     */
    fun save(dest: Uri) {
        val p = picture ?: run {
            viewModelScope.launch(NonCancellable + Dispatchers.IO) { discardDocument(appContext, dest) }
            error = REDACT_LOST
            return
        }
        val snapshot = p.history.boxes
        busy = REDACT_RENDERING
        viewModelScope.launch {
            var written = false
            try {
                val bytes = renderCopy(p, snapshot)
                if (!stillMatches(p, snapshot)) throw BoxesChanged()
                withContext(Dispatchers.IO) {
                    writeDocument(appContext, dest, bytes)
                    written = true
                }
                done = PHOTO_DONE_SHORT
                error = null
            } catch (e: CancellationException) {
                // The screen gone for good: a fully written copy stays; an empty document does not.
                if (!written) withContext(NonCancellable + Dispatchers.IO) { discardDocument(appContext, dest) }
                throw e
            } catch (e: BoxesChanged) {
                withContext(NonCancellable + Dispatchers.IO) { discardDocument(appContext, dest) }
                done = null
                error = REDACT_CHANGED
            } catch (e: PhotoFailure) {
                withContext(NonCancellable + Dispatchers.IO) { discardDocument(appContext, dest) } // Never a half-made copy.
                done = null
                error = REDACT_COPY_FAILED
            } catch (e: Exception) {
                withContext(NonCancellable + Dispatchers.IO) { discardDocument(appContext, dest) }
                done = null
                error = PHOTO_SAVE_FAILED
            } finally {
                busy = null
                releaseIfLeft()
            }
        }
    }

    /** Makes the copy for «Compartir»; [shareReady] then holds it until the screen has handed it over ([shared]). */
    fun share() {
        val p = picture ?: return
        val snapshot = p.history.boxes
        busy = REDACT_RENDERING
        viewModelScope.launch {
            var prepared: File? = null
            var handedOver = false
            try {
                val bytes = renderCopy(p, snapshot)
                if (!stillMatches(p, snapshot)) throw BoxesChanged()
                prepared = withContext(Dispatchers.IO) { shareTemp.prepare(redactFileName(p.output), bytes) }
                // The screen was left meanwhile: never a share sheet out of the blue when it comes back (finally drops it).
                if (!leftWhileBusy) {
                    shareReady = PreparedShare(prepared!!, redactMime(p.output))
                    handedOver = true
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: BoxesChanged) {
                done = null
                error = REDACT_CHANGED
            } catch (e: PhotoFailure) {
                done = null
                error = REDACT_COPY_FAILED
            } catch (e: Exception) {
                done = null
                error = PHOTO_SHARE_FAILED
            } finally {
                // Nothing was handed over: only the file this attempt wrote goes (prepare already removed any earlier
                // one), and it need not wait the 10 minutes.
                if (!handedOver) {
                    prepared?.let { withContext(NonCancellable + Dispatchers.IO) { shareTemp.discard(it) } }
                    busy = null
                    releaseIfLeft()
                }
            }
        }
    }

    /** The screen opened the share sheet for [file] ([ok]), or could not. */
    fun shared(ready: PreparedShare, ok: Boolean) {
        if (shareReady !== ready) return
        val file = ready.file
        shareReady = null
        if (ok) {
            done = PHOTO_DONE_SHORT
            error = null
        } else {
            viewModelScope.launch(NonCancellable + Dispatchers.IO) { shareTemp.discard(file) }
            done = null
            error = PHOTO_SHARE_FAILED
        }
        busy = null
        releaseIfLeft()
    }

    /** The screen is composed (again): nothing it shows may be released now. */
    fun shown() {
        leftWhileBusy = false
    }

    /**
     * The screen left the composition without a configuration change (another tab, back): the image goes now, not when
     * the entry is destroyed, so a tab switch does not keep up to 50 MB and a 24 MP bitmap while the vault opens. Opening
     * is cancelled; a copy being saved or shared first ends (then [releaseIfLeft]), unless the screen comes back first.
     */
    fun left() {
        val share = shareReady
        if (share != null) shared(share, ok = false) // Not handed over: never opened later, out of the blue.
        if (busy != null && busy != REDACT_OPENING) {
            leftWhileBusy = true
            return
        }
        job?.cancel()
        busy = null
        dropPicture()
    }

    private fun releaseIfLeft() {
        if (leftWhileBusy) {
            leftWhileBusy = false
            dropPicture()
        }
    }

    /** No composition draws it any more: its bitmaps go (the full-size one once no copy is being made from it). */
    private fun dropPicture() {
        picture?.let {
            it.holder.close()
            it.previewBitmap.recycle()
        }
        picture = null
        sync()
        error = null
    }

    override fun onCleared() {
        // The screen is gone for good (its composition already left): the full-size bitmap goes once no copy is being made
        // from it, and the preview now (recycling twice does nothing).
        dropPicture()
    }
}

/**
 * «Tachar en imagen»: covers what the finger drags over with solid black boxes and makes a new copy, saved where the
 * user chooses or shared through the FileProvider. The original is never written. The copy is drawn from the pixels,
 * so it carries none of the original file's metadata, and it goes through PhotoCleaner. No vault access (like «Limpiar
 * foto»). Errors are always fixed texts from ToolTexts; nothing is logged.
 */
@Composable
fun RedactScreen(shareTemp: ShareTemp, onBack: () -> Unit) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val vm = viewModel { RedactViewModel(appContext, shareTemp) }
    val picture = vm.picture
    val boxes = vm.boxes
    val busy = vm.busy
    var pickerError by remember { mutableStateOf<String?>(null) }

    val activity = context.fragmentActivity()
    // Only a rotation (or another configuration change) keeps the image: any other way out lets it go now.
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
    val savePng = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png"), onSaveTarget)
    val saveJpg = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg"), onSaveTarget)

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.open(uri)
    }

    BackScaffold(REDACT_TOOL_TITLE, onBack) { padding ->
        NoCopy {
            Column(
                Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(REDACT_EXPLANATION, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SectionCard(Modifier.fillMaxWidth()) {
                    Text(REDACT_LIMITS, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
                }
                OutlinedButton(
                    onClick = { pickerError = pick.launchOrError(arrayOf("image/*")) },
                    enabled = busy == null,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (picture == null) REDACT_PICK else REDACT_PICK_OTHER) }
                pickerError?.let { Text(it, color = NegativeColor) }
                busy?.let { label ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                vm.error?.let { Text(it, color = NegativeColor) }
                picture?.let { p ->
                    p.downscaledNote?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    RedactCanvas(p, boxes, enabled = busy == null, isShown = { vm.picture === p }, onBox = { box ->
                        if (vm.busy == null) {
                            p.history.add(box)
                            vm.sync()
                        }
                    })
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { if (vm.busy == null && p.history.undo()) vm.sync() },
                            enabled = vm.canUndo && busy == null,
                            modifier = Modifier.weight(1f),
                        ) { Text(REDACT_UNDO) }
                        OutlinedButton(
                            onClick = { if (vm.busy == null && p.history.redo()) vm.sync() },
                            enabled = vm.canRedo && busy == null,
                            modifier = Modifier.weight(1f),
                        ) { Text(REDACT_REDO) }
                    }
                    // Nothing covered yet: no copy to make, so nothing unredacted is shared by mistake.
                    val ready = busy == null && boxes.isNotEmpty()
                    Button(
                        onClick = {
                            val launcher = if (p.output == PhotoFormat.PNG) savePng else saveJpg
                            pickerError = launcher.launchOrError(redactFileName(p.output))
                        },
                        enabled = ready,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(PHOTO_SAVE_COPY) }
                    FilledTonalButton(
                        onClick = vm::share,
                        enabled = ready,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(SHARE) }
                }
                vm.done?.let { Text(it) }
            }
        }
    }
}

/**
 * The preview: the image fitted to the width (and to 60 % of the window's height, so the page can still be scrolled
 * outside it), with every box painted opaque black at the image coordinates the copy uses ([boxToView] of the same
 * [RedactBox]). The box being dragged is painted the same way, from the same [boxFromDrag] that will store it.
 * While a copy is being made ([enabled] false) it takes no input and shows no box in progress: the copy is made from
 * the boxes at the moment the button was pressed, and the screen must not show one the copy lacks. The preview bitmap
 * is recycled once this preview leaves the composition because another image was picked ([isShown] false); a
 * rotation re-creates the composition with the same picture, which keeps its preview (the ViewModel recycles it when
 * the screen is gone for good).
 *
 * The image area keeps clear of the system's horizontal gesture insets and is also excluded from the back gesture (M-2:
 * a drag from the left edge to cover a name there must draw a box, not leave the screen; the exclusion is API 29+ and
 * Android caps it at 200 dp of height per edge, hence the padding too), and tells TalkBack what it is and how many
 * blocks it has (M-11).
 */
@Composable
private fun RedactCanvas(p: Picture, boxes: List<RedactBox>, enabled: Boolean, isShown: () -> Boolean, onBox: (RedactBox) -> Unit) {
    val currentOnBox by rememberUpdatedState(onBox)
    val currentEnabled by rememberUpdatedState(enabled)
    val currentIsShown by rememberUpdatedState(isShown)
    DisposableEffect(p) { onDispose { if (!currentIsShown()) p.previewBitmap.recycle() } }
    var dragFrom by remember(p) { mutableStateOf<Offset?>(null) }
    var dragTo by remember(p) { mutableStateOf<Offset?>(null) }
    val windowHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val aspect = p.width.toFloat() / p.height
        val maxHeight = windowHeight * 0.6f
        val w = min(maxWidth, maxHeight * aspect).coerceAtLeast(1.dp)
        val h = (w / aspect).coerceAtLeast(1.dp)
        // Kept clear of the system's horizontal gesture edges (the exclusion below is capped at 200 dp per edge).
        Box(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.systemGestures.only(WindowInsetsSides.Horizontal)), contentAlignment = Alignment.Center) {
            Canvas(
                Modifier
                    .size(w, h)
                    .systemGestureExclusion()
                    .semantics { contentDescription = redactCanvasDescription(boxes.size) }
                    .pointerInput(p) {
                    awaitEachGesture {
                        // The box starts where the finger went down, not where the drag was recognised.
                        val down = awaitFirstDown()
                        // Busy: not consumed, so the page still scrolls.
                        if (!currentEnabled) return@awaitEachGesture
                        dragFrom = down.position
                        dragTo = down.position
                        val start = awaitTouchSlopOrCancellation(down.id) { change, _ ->
                            change.consume()
                            dragTo = change.position
                        }
                        val ended = start != null && drag(start.id) { change ->
                            change.consume()
                            dragTo = change.position
                        }
                        val from = dragFrom
                        val to = dragTo
                        dragFrom = null
                        dragTo = null
                        if (ended && currentEnabled && from != null && to != null) {
                            val viewW = size.width.toFloat()
                            val viewH = size.height.toFloat()
                            boxFromDrag(from.x, from.y, to.x, to.y, viewW, viewH, p.width, p.height)?.let { currentOnBox(it) }
                        }
                    }
                },
            ) {
                drawImage(
                    p.preview,
                    dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                    filterQuality = FilterQuality.Low,
                )
                for (b in boxes) drawBlack(boxToView(b, size.width, size.height, p.width, p.height))
                val from = dragFrom
                val to = dragTo
                if (enabled && from != null && to != null) {
                    boxFromDrag(from.x, from.y, to.x, to.y, size.width, size.height, p.width, p.height)?.let {
                        drawBlack(boxToView(it, size.width, size.height, p.width, p.height))
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawBlack(r: ViewRect) {
    drawRect(Color.Black, topLeft = Offset(r.left, r.top), size = Size(r.right - r.left, r.bottom - r.top))
}
