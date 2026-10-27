package app.seguridad.tools.redact

import app.seguridad.tools.MAX_DECLARED_PIXELS
import app.seguridad.tools.MAX_DECLARED_SIDE
import app.seguridad.tools.MAX_REDACT_PIXELS
import app.seguridad.tools.photo.PhotoFormat
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.math.sqrt

/**
 * A solid black box, in pixels of the decoded image: [left] and [top] included, [right] and [bottom] excluded, so it
 * covers `(right - left) × (bottom - top)` pixels. Always inside the image and at least 1 px wide and high.
 */
data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int)

/** A box drawn back on the preview, in view pixels. */
data class ViewRect(val left: Float, val top: Float, val right: Float, val bottom: Float)

/**
 * The box a drag from ([x0], [y0]) to ([x1], [y1]) covers, for an [imageW]×[imageH] image shown at [viewW]×[viewH] view
 * pixels. Either direction works; it is clipped to the image, and rounded outward (floor for the start, ceil for the
 * end), so it never covers less than what was drawn. Null when what is left inside the image is under 1 image pixel
 * wide or high (a tap, or a drag outside the image), or for sizes and positions that are not finite numbers.
 */
fun boxFromDrag(x0: Float, y0: Float, x1: Float, y1: Float, viewW: Float, viewH: Float, imageW: Int, imageH: Int): Box? {
    if (!(viewW > 0f && viewW.isFinite() && viewH > 0f && viewH.isFinite()) || imageW <= 0 || imageH <= 0) return null
    if (!(x0.isFinite() && y0.isFinite() && x1.isFinite() && y1.isFinite())) return null
    // Multiply before dividing: whole-number scales stay exact.
    fun toX(x: Float) = (x.toDouble() * imageW / viewW).coerceIn(0.0, imageW.toDouble())
    fun toY(y: Float) = (y.toDouble() * imageH / viewH).coerceIn(0.0, imageH.toDouble())
    val l = toX(min(x0, x1))
    val r = toX(max(x0, x1))
    val t = toY(min(y0, y1))
    val b = toY(max(y0, y1))
    if (r - l < 1.0 || b - t < 1.0) return null
    return Box(
        floor(l).toInt().coerceIn(0, imageW),
        floor(t).toInt().coerceIn(0, imageH),
        ceil(r).toInt().coerceIn(0, imageW),
        ceil(b).toInt().coerceIn(0, imageH),
    )
}

/** [box] in the view's pixels, with the same scale as [boxFromDrag]: what the preview paints black. */
fun boxToView(box: Box, viewW: Float, viewH: Float, imageW: Int, imageH: Int) = ViewRect(
    (box.left.toDouble() * viewW / imageW).toFloat(),
    (box.top.toDouble() * viewH / imageH).toFloat(),
    (box.right.toDouble() * viewW / imageW).toFloat(),
    (box.bottom.toDouble() * viewH / imageH).toFloat(),
)

/** The boxes drawn so far, with undo and redo. A new box drops whatever could still be redone. */
class RedactHistory {
    private val done = ArrayList<Box>()
    private val undone = ArrayList<Box>()

    /** A copy, in the order they were drawn (the order they are painted). */
    val boxes: List<Box> get() = done.toList()
    val canUndo: Boolean get() = done.isNotEmpty()
    val canRedo: Boolean get() = undone.isNotEmpty()

    fun add(box: Box) {
        done += box
        undone.clear()
    }

    /** False when there was nothing to undo. */
    fun undo(): Boolean {
        if (done.isEmpty()) return false
        undone += done.removeAt(done.lastIndex)
        return true
    }

    /** False when there was nothing to redo. */
    fun redo(): Boolean {
        if (undone.isEmpty()) return false
        done += undone.removeAt(undone.lastIndex)
        return true
    }
}

/** PNG and WebP (screenshots) stay lossless as PNG; JPEG and HEIC (photos) become JPEG of quality 95. */
fun redactOutputFormat(input: PhotoFormat): PhotoFormat = when (input) {
    PhotoFormat.PNG, PhotoFormat.WEBP -> PhotoFormat.PNG
    PhotoFormat.JPEG, PhotoFormat.HEIF -> PhotoFormat.JPEG
}

/** The fixed name of the copy (ShareTemp accepts only these): nothing in it tells the date or the original's name. */
fun redactFileName(output: PhotoFormat) = if (output == PhotoFormat.PNG) "imagen-tachada.png" else "imagen-tachada.jpg"

fun redactMime(output: PhotoFormat) = if (output == PhotoFormat.PNG) "image/png" else "image/jpeg"

/**
 * The size to decode a [w]×[h] image at so it has at most [MAX_REDACT_PIXELS] (ImageDecoder.setTargetSize), keeping its
 * shape; null when it already fits. Never below 1 px a side.
 */
fun redactTargetSize(w: Int, h: Int): Pair<Int, Int>? {
    require(w > 0 && h > 0)
    val pixels = w.toLong() * h
    if (pixels <= MAX_REDACT_PIXELS) return null
    val scale = sqrt(MAX_REDACT_PIXELS.toDouble() / pixels)
    var tw = floor(w * scale).toLong().coerceIn(1, w.toLong())
    var th = floor(h * scale).toLong().coerceIn(1, h.toLong())
    // A side stuck at 1 px (a very long strip), or the last bit of the square root: the other side gives way.
    if (tw * th > MAX_REDACT_PIXELS) {
        if (tw >= th) tw = MAX_REDACT_PIXELS / th else th = MAX_REDACT_PIXELS / tw
    }
    return tw.toInt() to th.toInt()
}

/**
 * Android 8 has no ImageDecoder: BitmapFactory's inSampleSize, the smallest power of two that brings a [w]×[h] image to
 * at most [MAX_REDACT_PIXELS]. Each side is rounded up (libjpeg does), so the decoded image is never over the limit.
 */
fun redactSampleSize(w: Int, h: Int): Int {
    require(w > 0 && h > 0)
    var s = 1L
    while (((w + s - 1) / s) * ((h + s - 1) / s) > MAX_REDACT_PIXELS) s *= 2
    return s.toInt()
}

/** Megapixels for «se ha reducido a unos N megapíxeles», rounded, and never 0. */
fun redactMegapixels(w: Int, h: Int): Int = max(1L, (w.toLong() * h / 1_000_000.0).roundToLong()).toInt()

/**
 * False when a [w]×[h] header is not worth decoding: a side over [MAX_DECLARED_SIDE], more than [MAX_DECLARED_PIXELS],
 * or a size that is not positive. Checked from the header alone (ImageDecoder's header listener, BitmapFactory's
 * bounds), so a hostile file stops before its pixels are inflated.
 */
fun declaredSizeAllowed(w: Int, h: Int): Boolean =
    w in 1..MAX_DECLARED_SIDE && h in 1..MAX_DECLARED_SIDE && w.toLong() * h <= MAX_DECLARED_PIXELS
