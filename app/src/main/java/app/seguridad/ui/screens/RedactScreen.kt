package app.seguridad.ui.screens

import android.graphics.Bitmap
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.core.graphics.scale
import app.seguridad.android.RedactSource
import app.seguridad.android.decodeForRedaction
import app.seguridad.android.renderRedacted
import app.seguridad.android.shareImage
import app.seguridad.tools.PHOTO_DONE_SHORT
import app.seguridad.tools.PHOTO_SAVE_COPY
import app.seguridad.tools.PHOTO_SAVE_FAILED
import app.seguridad.tools.PHOTO_SHARE_FAILED
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
import app.seguridad.tools.REDACT_UNDO
import app.seguridad.tools.SHARE
import app.seguridad.tools.photo.PHOTO_DAMAGED
import app.seguridad.tools.photo.PHOTO_UNSUPPORTED
import app.seguridad.tools.photo.PhotoFailure
import app.seguridad.tools.photo.PhotoFormat
import app.seguridad.tools.redact.Box
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
 * The picked image, only in memory (never in saved state: the screen rebuilt means «vuelve a elegir la imagen»). The
 * boxes are in pixels of the full-size image ([width]×[height]); the [preview] is the same image, smaller, and the
 * screen draws the boxes on it at the same image coordinates the copy uses.
 */
private class Picture(
    val holder: SourceHolder,
    val preview: ImageBitmap,
    val width: Int,
    val height: Int,
    val output: PhotoFormat,
    val downscaledNote: String?,
) {
    val history = RedactHistory()
}

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
        preview.asImageBitmap(),
        src.width,
        src.height,
        redactOutputFormat(source.format),
        if (source.downscaled) redactDownscaledText(redactMegapixels(src.width, src.height)) else null,
    )
}

/** On Dispatchers.Default: the copy with [boxes]. Fails with PhotoFailure, never a half-made copy. */
private suspend fun renderCopy(p: Picture, boxes: List<Box>): ByteArray = withContext(Dispatchers.Default) {
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
 * «Tachar en imagen»: covers what the finger drags over with solid black boxes and makes a new copy, saved where the
 * user chooses or shared through the FileProvider. The original is never written. The copy is drawn from the pixels,
 * so it carries none of the original file's metadata, and it goes through PhotoCleaner. No vault access (like «Limpiar
 * foto»). Errors are always fixed texts from ToolTexts; nothing is logged.
 */
@Composable
fun RedactScreen(shareTemp: ShareTemp, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var picture by remember { mutableStateOf<Picture?>(null) }
    var boxes by remember { mutableStateOf(emptyList<Box>()) }
    var canUndo by remember { mutableStateOf(false) }
    var canRedo by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf<String?>(null) }
    var pickerError by remember { mutableStateOf<String?>(null) }

    fun sync(p: Picture?) {
        boxes = p?.history?.boxes.orEmpty()
        canUndo = p?.history?.canUndo == true
        canRedo = p?.history?.canRedo == true
        done = null // A change after saving: the saved copy no longer matches what is shown.
    }

    // Leaving the screen: the full-size bitmap goes (once no copy is being made from it); the preview goes with the
    // composition.
    DisposableEffect(Unit) { onDispose { picture?.holder?.close() } }

    val onSaveTarget: (Uri?) -> Unit = saved@{ dest ->
        if (dest == null) return@saved
        val p = picture ?: run {
            discardDocument(context, dest)
            error = REDACT_LOST
            return@saved
        }
        val snapshot = p.history.boxes
        busy = REDACT_RENDERING
        scope.launch {
            var written = false
            try {
                val bytes = renderCopy(p, snapshot)
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(dest, "wt")!!.use { it.write(bytes) }
                    written = true
                }
                done = PHOTO_DONE_SHORT
                error = null
            } catch (e: CancellationException) {
                // Screen gone: a fully written copy stays; an empty document does not.
                if (!written) discardDocument(context, dest)
                throw e
            } catch (e: PhotoFailure) {
                discardDocument(context, dest) // Never a half-made copy.
                done = null
                error = REDACT_COPY_FAILED
            } catch (e: Exception) {
                discardDocument(context, dest)
                done = null
                error = PHOTO_SAVE_FAILED
            } finally {
                busy = null
            }
        }
    }
    val savePng = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png"), onSaveTarget)
    val saveJpg = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg"), onSaveTarget)

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        // The previous image goes before the next file is read.
        picture?.holder?.close()
        picture = null
        sync(null)
        error = null
        busy = REDACT_OPENING
        scope.launch {
            var opened: RedactSource? = null
            try {
                val bytes = withContext(Dispatchers.IO) { readPhoto(context, uri) }
                ensureActive()
                val p = withContext(Dispatchers.Default) {
                    val source = decodeForRedaction(bytes).also { opened = it }
                    ensureActive()
                    makePicture(source)
                }
                opened = null // Owned by the Picture from here on.
                picture = p
                sync(p)
            } catch (e: CancellationException) {
                throw e
            } catch (e: PhotoTooBig) {
                error = REDACT_TOO_BIG
            } catch (e: PhotoFailure) {
                error = if (e.message == PHOTO_UNSUPPORTED) PHOTO_UNSUPPORTED else REDACT_DAMAGED
            } catch (e: Exception) {
                error = REDACT_DAMAGED
            } catch (e: OutOfMemoryError) {
                // Up to 50 MB of file and a 24 MP bitmap: on a small heap this is the error to show, not a crash.
                error = REDACT_DAMAGED
            } finally {
                opened?.bitmap?.recycle() // Decoded but never shown (cancelled or failed after decoding).
                busy = null
            }
        }
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
                error?.let { Text(it, color = NegativeColor) }
                picture?.let { p ->
                    p.downscaledNote?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    RedactCanvas(p, boxes, onBox = { box ->
                        p.history.add(box)
                        sync(p)
                    })
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { if (p.history.undo()) sync(p) },
                            enabled = canUndo,
                            modifier = Modifier.weight(1f),
                        ) { Text(REDACT_UNDO) }
                        OutlinedButton(
                            onClick = { if (p.history.redo()) sync(p) },
                            enabled = canRedo,
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
                        onClick = {
                            val snapshot = p.history.boxes
                            busy = REDACT_RENDERING
                            scope.launch {
                                var prepared: File? = null
                                // Nothing was handed over: only the file this attempt wrote goes (prepare already
                                // removed any earlier one), and it need not wait the 10 minutes.
                                suspend fun discardPrepared() {
                                    prepared?.let { withContext(NonCancellable + Dispatchers.IO) { shareTemp.discard(it) } }
                                }
                                try {
                                    val bytes = renderCopy(p, snapshot)
                                    val file = withContext(Dispatchers.IO) {
                                        shareTemp.prepare(redactFileName(p.output), bytes).also { prepared = it }
                                    }
                                    shareImage(context, file, redactMime(p.output))
                                    done = PHOTO_DONE_SHORT
                                    error = null
                                } catch (e: CancellationException) {
                                    discardPrepared()
                                    throw e
                                } catch (e: PhotoFailure) {
                                    discardPrepared()
                                    done = null
                                    error = REDACT_COPY_FAILED
                                } catch (e: Exception) {
                                    discardPrepared()
                                    done = null
                                    error = PHOTO_SHARE_FAILED
                                } finally {
                                    busy = null
                                }
                            }
                        },
                        enabled = ready,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(SHARE) }
                }
                done?.let { Text(it) }
            }
        }
    }
}

/**
 * The preview: the image fitted to the width (and to 60 % of the window's height, so the page can still be scrolled
 * outside it), with every box painted opaque black at the image coordinates the copy uses ([boxToView] of the same
 * [Box]). The box being dragged is painted the same way, from the same [boxFromDrag] that will store it.
 */
@Composable
private fun RedactCanvas(p: Picture, boxes: List<Box>, onBox: (Box) -> Unit) {
    val currentOnBox by rememberUpdatedState(onBox)
    var dragFrom by remember(p) { mutableStateOf<Offset?>(null) }
    var dragTo by remember(p) { mutableStateOf<Offset?>(null) }
    val windowHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val aspect = p.width.toFloat() / p.height
        val maxHeight = windowHeight * 0.6f
        val w = min(maxWidth, maxHeight * aspect).coerceAtLeast(1.dp)
        val h = (w / aspect).coerceAtLeast(1.dp)
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Canvas(
                Modifier.size(w, h).pointerInput(p) {
                    awaitEachGesture {
                        // The box starts where the finger went down, not where the drag was recognised.
                        val down = awaitFirstDown()
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
                        if (ended && from != null && to != null) {
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
                if (from != null && to != null) {
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
