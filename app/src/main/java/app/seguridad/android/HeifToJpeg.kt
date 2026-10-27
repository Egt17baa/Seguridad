package app.seguridad.android

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Build
import app.seguridad.tools.MAX_REDACT_PIXELS
import app.seguridad.tools.photo.PHOTO_DAMAGED
import app.seguridad.tools.photo.PHOTO_UNSUPPORTED
import app.seguridad.tools.photo.PhotoCleaner
import app.seguridad.tools.photo.PhotoFailure
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import kotlin.math.sqrt

/**
 * HEIC/HEIF has no metadata cleaner of ours: it is decoded and re-encoded as JPEG (quality 95), which carries none of
 * the original's metadata, and the result goes through PhotoCleaner, which also drops the JFIF APP0 the encoder adds.
 * Images above [MAX_REDACT_PIXELS] are scaled down while decoding, so a huge one never takes the memory.
 * Fails closed: [PHOTO_UNSUPPORTED] before Android 9 (no ImageDecoder), [PHOTO_DAMAGED] for anything else that goes wrong.
 */
fun heifToJpeg(bytes: ByteArray): ByteArray {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) throw PhotoFailure(PHOTO_UNSUPPORTED)
    val jpeg = try {
        val source = ImageDecoder.createSource(ByteBuffer.wrap(bytes))
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            // Software memory, so compress() can read it; no partial images.
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.setOnPartialImageListener { false }
            val w = info.size.width.toLong()
            val h = info.size.height.toLong()
            if (w <= 0 || h <= 0) throw PhotoFailure(PHOTO_DAMAGED)
            if (w * h > MAX_REDACT_PIXELS) {
                val scale = sqrt(MAX_REDACT_PIXELS.toDouble() / (w * h))
                decoder.setTargetSize((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
            }
        }
        try {
            ByteArrayOutputStream().use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)) throw PhotoFailure(PHOTO_DAMAGED)
                out.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    } catch (e: OutOfMemoryError) {
        throw PhotoFailure(PHOTO_DAMAGED)
    } catch (e: Exception) {
        throw PhotoFailure(PHOTO_DAMAGED)
    }
    return PhotoCleaner.clean(jpeg)
}
