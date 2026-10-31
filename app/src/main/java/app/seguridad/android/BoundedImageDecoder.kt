package app.seguridad.android

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Build
import androidx.annotation.RequiresApi
import app.seguridad.tools.ImageTooLarge
import app.seguridad.tools.redact.declaredSizeAllowed
import java.nio.ByteBuffer

/** What [decodeBounded] gave: the bitmap, and whether it was asked for at a smaller size than the image declares. */
internal class BoundedBitmap(val bitmap: Bitmap, val downscaled: Boolean)

/**
 * The ImageDecoder setup every image tool uses (CR-7: «Leer QR» from an image, «Tachar» and the HEIC → JPEG conversion
 * of «Limpiar foto»), in one place so a hardening fix is applied once:
 * - software memory ([ImageDecoder.ALLOCATOR_SOFTWARE]), so the pixels can be read back, drawn on a software Canvas or
 *   compressed; no partial images (a cut file is a failure, never half a picture);
 * - a declared size that is not positive throws what [invalidSize] makes (each caller's own failure);
 * - a declared size over what [declaredSizeAllowed] accepts throws [ImageTooLarge] from the header listener, so the
 *   decode stops before any pixel is inflated;
 * - [targetSize] (the caller's own limit, from the declared width and height) is asked with setTargetSize when it is
 *   not null.
 * What it throws otherwise (a damaged file, an OutOfMemoryError) is left to the caller to map, as before.
 */
@RequiresApi(Build.VERSION_CODES.P)
internal fun decodeBounded(bytes: ByteArray, invalidSize: () -> Exception, targetSize: (Int, Int) -> Pair<Int, Int>?): BoundedBitmap {
    var downscaled = false
    val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, info, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        decoder.setOnPartialImageListener { false }
        val w = info.size.width
        val h = info.size.height
        if (w <= 0 || h <= 0) throw invalidSize()
        // Thrown here, the decode stops before inflating anything.
        if (!declaredSizeAllowed(w, h)) throw ImageTooLarge()
        targetSize(w, h)?.let { (tw, th) ->
            decoder.setTargetSize(tw, th)
            downscaled = true
        }
    }
    return BoundedBitmap(bitmap, downscaled)
}
