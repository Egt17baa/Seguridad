package app.seguridad.android

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.graphics.createBitmap
import app.seguridad.tools.ImageTooLarge
import app.seguridad.tools.MAX_REDACT_PIXELS
import app.seguridad.tools.photo.PHOTO_DAMAGED
import app.seguridad.tools.photo.PHOTO_UNSUPPORTED
import app.seguridad.tools.photo.PhotoCleaner
import app.seguridad.tools.photo.PhotoFailure
import app.seguridad.tools.photo.PhotoFormat
import app.seguridad.tools.photo.detectFormat
import app.seguridad.tools.photo.isAvif
import app.seguridad.tools.redact.Box
import app.seguridad.tools.redact.declaredSizeAllowed
import app.seguridad.tools.redact.redactSampleSize
import app.seguridad.tools.redact.redactTargetSize
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/**
 * An image decoded for «Tachar»: software pixels, upright, at most [MAX_REDACT_PIXELS]. [downscaled] when it had to be
 * reduced to fit. Lives only in memory; the screen recycles [bitmap] when it is done with it.
 */
class RedactSource(val bitmap: Bitmap, val format: PhotoFormat, val downscaled: Boolean)

/**
 * Decodes [bytes] (already limited to MAX_IMAGE_BYTES) for «Tachar». The format comes from the content, never from
 * the name or the type the picker gives.
 * - Android 9 and later: ImageDecoder, which applies the EXIF orientation, with setTargetSize above the pixel limit.
 * - Android 8: BitmapFactory with inSampleSize, and the JPEG's EXIF orientation applied with a Matrix.
 *
 * Fails closed with [PhotoFailure]: [PHOTO_UNSUPPORTED] for an unknown format, HEIC before Android 9 and AVIF before
 * Android 12; [PHOTO_DAMAGED] for anything else, out of memory included. A header that declares more than
 * declaredSizeAllowed accepts throws [ImageTooLarge] before any pixel is inflated. Whatever the preview shows is what
 * the copy gets: both come from this one bitmap.
 */
fun decodeForRedaction(bytes: ByteArray): RedactSource {
    val format = detectFormat(bytes) ?: throw PhotoFailure(PHOTO_UNSUPPORTED)
    if (format == PhotoFormat.HEIF && Build.VERSION.SDK_INT < Build.VERSION_CODES.P) throw PhotoFailure(PHOTO_UNSUPPORTED)
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && isAvif(bytes)) throw PhotoFailure(PHOTO_UNSUPPORTED)
    var bitmap: Bitmap? = null
    try {
        val (decoded, downscaled) =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) withImageDecoder(bytes) else withBitmapFactory(bytes, format)
        bitmap = decoded
        // A decoder that ignored the requested size must not take the memory the limit is there to protect.
        if (decoded.width <= 0 || decoded.height <= 0 || decoded.width.toLong() * decoded.height > MAX_REDACT_PIXELS) {
            throw PhotoFailure(PHOTO_DAMAGED)
        }
        return RedactSource(decoded, format, downscaled)
    } catch (e: PhotoFailure) {
        bitmap?.recycle()
        throw e
    } catch (e: ImageTooLarge) {
        bitmap?.recycle()
        throw e
    } catch (e: OutOfMemoryError) {
        bitmap?.recycle()
        throw PhotoFailure(PHOTO_DAMAGED)
    } catch (e: Exception) {
        bitmap?.recycle()
        throw PhotoFailure(PHOTO_DAMAGED)
    }
}

@RequiresApi(Build.VERSION_CODES.P)
private fun withImageDecoder(bytes: ByteArray): Pair<Bitmap, Boolean> {
    var downscaled = false
    val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, info, _ ->
        // Software memory, so it can be drawn on a software Canvas and read back; no partial images.
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        decoder.setOnPartialImageListener { false }
        val w = info.size.width
        val h = info.size.height
        if (w <= 0 || h <= 0) throw PhotoFailure(PHOTO_DAMAGED)
        // Thrown here, the decode stops before inflating anything.
        if (!declaredSizeAllowed(w, h)) throw ImageTooLarge()
        redactTargetSize(w, h)?.let { (tw, th) ->
            decoder.setTargetSize(tw, th)
            downscaled = true
        }
    }
    return bitmap to downscaled
}

private fun withBitmapFactory(bytes: ByteArray, format: PhotoFormat): Pair<Bitmap, Boolean> {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw PhotoFailure(PHOTO_DAMAGED)
    if (!declaredSizeAllowed(bounds.outWidth, bounds.outHeight)) throw ImageTooLarge()
    val sample = redactSampleSize(bounds.outWidth, bounds.outHeight)
    val options = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.ARGB_8888
        inMutable = false
    }
    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: throw PhotoFailure(PHOTO_DAMAGED)
    // Only the JPEG's orientation, as ImageDecoder does from Android 9. Cosmetic: the copy is made from what is shown.
    val orientation = if (format == PhotoFormat.JPEG) runCatching { PhotoCleaner.inspect(bytes).orientation }.getOrDefault(1) else 1
    val matrix = orientationMatrix(orientation) ?: return decoded to (sample > 1)
    val upright = try {
        Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, false)
    } catch (e: Throwable) {
        decoded.recycle()
        throw e
    }
    if (upright !== decoded) decoded.recycle()
    return upright to (sample > 1)
}

/** EXIF orientation 2..8 as a Matrix (1 or anything else: null, nothing to do). */
private fun orientationMatrix(orientation: Int): Matrix? {
    val m = Matrix()
    when (orientation) {
        2 -> m.setScale(-1f, 1f)
        3 -> m.setRotate(180f)
        4 -> m.setScale(1f, -1f)
        5 -> { m.setRotate(90f); m.postScale(-1f, 1f) }
        6 -> m.setRotate(90f)
        7 -> { m.setRotate(-90f); m.postScale(-1f, 1f) }
        8 -> m.setRotate(-90f)
        else -> return null
    }
    return m
}

/**
 * The redacted copy: a new ARGB_8888 bitmap where [source] is drawn and **then** every box, in opaque black (alpha
 * 255, Style.FILL, no anti-aliasing, whole pixels). Before encoding, every pixel under every box is read back and must
 * be black, or nothing is produced. Encoded as PNG or JPEG 95 ([format] is the output one, from redactOutputFormat),
 * and the result goes through PhotoCleaner.clean, which keeps only the whitelisted blocks and fails closed. The copy
 * is made from pixels, so nothing of the original file's metadata can be in it.
 *
 * Fails with [PhotoFailure] ([PHOTO_DAMAGED]), out of memory included; never returns a half-made copy.
 */
fun renderRedacted(source: Bitmap, boxes: List<Box>, format: PhotoFormat): ByteArray {
    val compressFormat = when (format) {
        PhotoFormat.PNG -> Bitmap.CompressFormat.PNG
        PhotoFormat.JPEG -> Bitmap.CompressFormat.JPEG
        else -> throw PhotoFailure(PHOTO_DAMAGED)
    }
    val w = source.width
    val h = source.height
    val encoded = try {
        val out = createBitmap(w, h, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(out)
            canvas.drawBitmap(source, 0f, 0f, null)
            val black = Paint().apply {
                color = Color.BLACK
                alpha = 255
                style = Paint.Style.FILL
                isAntiAlias = false
            }
            val rects = boxes.map { b ->
                Rect(b.left.coerceIn(0, w), b.top.coerceIn(0, h), b.right.coerceIn(0, w), b.bottom.coerceIn(0, h))
            }
            for (r in rects) if (!r.isEmpty) canvas.drawRect(r, black)
            if (!allBlack(out, rects)) throw PhotoFailure(PHOTO_DAMAGED)
            ByteArrayOutputStream().use { stream ->
                val quality = if (compressFormat == Bitmap.CompressFormat.JPEG) 95 else 100
                if (!out.compress(compressFormat, quality, stream)) throw PhotoFailure(PHOTO_DAMAGED)
                stream.toByteArray()
            }
        } finally {
            out.recycle()
        }
    } catch (e: PhotoFailure) {
        throw e
    } catch (e: OutOfMemoryError) {
        throw PhotoFailure(PHOTO_DAMAGED)
    } catch (e: Exception) {
        throw PhotoFailure(PHOTO_DAMAGED)
    }
    val clean = PhotoCleaner.clean(encoded)
    // The cleaner keeps the format; a mismatch would mean the wrong name and type on the copy.
    if (detectFormat(clean) != format) throw PhotoFailure(PHOTO_DAMAGED)
    return clean
}

/** True when every pixel inside [rects] of [bitmap] is opaque black. Read a row at a time, so memory stays small. */
private fun allBlack(bitmap: Bitmap, rects: List<Rect>): Boolean {
    for (r in rects) {
        if (r.isEmpty) continue
        val row = IntArray(r.width())
        for (y in r.top until r.bottom) {
            bitmap.getPixels(row, 0, row.size, r.left, y, row.size, 1)
            for (p in row) if (p != Color.BLACK) return false
        }
    }
    return true
}
