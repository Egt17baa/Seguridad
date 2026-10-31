package app.seguridad.android

import android.graphics.Bitmap
import android.os.Build
import app.seguridad.tools.ImageOutOfMemory
import app.seguridad.tools.ImageTooLarge
import app.seguridad.tools.MAX_REDACT_PIXELS
import app.seguridad.tools.photo.PHOTO_DAMAGED
import app.seguridad.tools.photo.PHOTO_UNSUPPORTED
import app.seguridad.tools.photo.PhotoCleaner
import app.seguridad.tools.photo.PhotoFailure
import app.seguridad.tools.photo.isAvif
import app.seguridad.tools.redact.redactTargetSize
import java.io.ByteArrayOutputStream

/**
 * HEIC/HEIF has no metadata cleaner of ours: it is decoded and re-encoded as JPEG (quality 95), which carries none of
 * the original's metadata, and the result goes through PhotoCleaner, which also drops the JFIF APP0 the encoder adds.
 * Images above [MAX_REDACT_PIXELS] are scaled down while decoding, so a huge one never takes the memory.
 * Fails closed: [PHOTO_UNSUPPORTED] before Android 9 (no ImageDecoder) and for AVIF before Android 12 (ImageDecoder
 * reads AVIF only from API 31), [ImageTooLarge] when the header declares more than declaredSizeAllowed accepts
 * (thrown from the header listener, before any pixel is inflated), [ImageOutOfMemory] when the Java heap runs out,
 * [PHOTO_DAMAGED] for anything else that goes wrong.
 */
fun heifToJpeg(bytes: ByteArray): ByteArray {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) throw PhotoFailure(PHOTO_UNSUPPORTED)
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && isAvif(bytes)) throw PhotoFailure(PHOTO_UNSUPPORTED)
    val jpeg = try {
        // Software memory, so compress() can read it; the same target size as «Tachar» (CR-6), never over the limit.
        val bitmap = decodeBounded(bytes, invalidSize = { PhotoFailure(PHOTO_DAMAGED) }, targetSize = ::redactTargetSize).bitmap
        try {
            ByteArrayOutputStream().use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)) throw PhotoFailure(PHOTO_DAMAGED)
                out.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    } catch (e: ImageTooLarge) {
        throw e
    } catch (e: OutOfMemoryError) {
        throw ImageOutOfMemory() // M-7: said as such, never as a damaged file.
    } catch (e: Exception) {
        throw PhotoFailure(PHOTO_DAMAGED)
    }
    return PhotoCleaner.clean(jpeg)
}
