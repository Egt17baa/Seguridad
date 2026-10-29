package app.seguridad.android

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import androidx.annotation.RequiresApi
import app.seguridad.tools.ImageOutOfMemory
import app.seguridad.tools.ImageTooLarge
import app.seguridad.tools.qr.MAX_QR_DECODE_SIDE
import app.seguridad.tools.qr.qrSampleSize
import app.seguridad.tools.qr.qrTargetSize
import app.seguridad.tools.redact.declaredSizeAllowed
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.ReaderException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.nio.ByteBuffer

/** The picked file is not an image Android can decode (or it ran out of memory): the screen says so, in Spanish. */
class QrImageUnreadable : Exception()

/**
 * The text of the QR code in [bytes] (a picked image, already limited to MAX_IMAGE_BYTES), or null if there is none.
 * Nothing is saved and nothing is logged.
 * - The size the header declares is checked first (declaredSizeAllowed): over it, [ImageTooLarge], before any pixel is
 *   inflated.
 * - Decoded with both sides at most [MAX_QR_DECODE_SIDE]: ImageDecoder.setTargetSize from Android 9, BitmapFactory's
 *   inSampleSize on Android 8. A decoder that ignores the size requested is refused, not used.
 * - Its pixels go to zxing through an [RGBLuminanceSource], with [MultiFormatReader] limited to QR_CODE and TRY_HARDER;
 *   a light-on-dark code is tried inverted. The Bitmap is recycled as soon as its pixels are copied.
 *
 * [QrImageUnreadable] for a file that is not a readable image, [ImageOutOfMemory] for running out of memory. Runs on a background
 * thread (CPU work, up to 16 MB of pixels).
 */
fun decodeQrFromImage(bytes: ByteArray): String? {
    val bitmap = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) withImageDecoder(bytes) else withBitmapFactory(bytes)
    } catch (e: ImageTooLarge) {
        throw e
    } catch (e: QrImageUnreadable) {
        throw e
    } catch (e: OutOfMemoryError) {
        throw ImageOutOfMemory() // M-7: said as such, never as an unreadable file.
    } catch (e: Exception) {
        throw QrImageUnreadable()
    }
    val w = bitmap.width
    val h = bitmap.height
    val pixels = try {
        if (w <= 0 || h <= 0 || w > MAX_QR_DECODE_SIDE || h > MAX_QR_DECODE_SIDE) throw QrImageUnreadable()
        IntArray(w * h).also { bitmap.getPixels(it, 0, w, 0, 0, w, h) }
    } catch (e: OutOfMemoryError) {
        throw ImageOutOfMemory() // M-7: said as such, never as an unreadable file.
    } finally {
        bitmap.recycle()
    }
    return try {
        val source = try {
            RGBLuminanceSource(w, h, pixels)
        } finally {
            pixels.fill(0) // zxing keeps its own grey copy: the colour one (a 2FA QR's picture) goes now.
        }
        readQr(source) ?: readQr(source.invert())
    } catch (e: OutOfMemoryError) {
        throw ImageOutOfMemory() // M-7: said as such, never as an unreadable file.
    }
}

private val QR_HINTS: Map<DecodeHintType, Any> = mapOf(
    DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
    DecodeHintType.TRY_HARDER to true,
)

/** The text of the QR in [source], or null when zxing finds none (or one it cannot read). */
private fun readQr(source: LuminanceSource): String? = try {
    MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(source)), QR_HINTS).text
} catch (e: ReaderException) {
    null
} catch (e: RuntimeException) {
    // zxing on hostile pixels: a bug there is «no code», never a crash.
    null
}

@RequiresApi(Build.VERSION_CODES.P)
private fun withImageDecoder(bytes: ByteArray): Bitmap =
    ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, info, _ ->
        // Software memory, so getPixels can read it; no partial images.
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        decoder.setOnPartialImageListener { false }
        val w = info.size.width
        val h = info.size.height
        if (w <= 0 || h <= 0) throw QrImageUnreadable()
        // Thrown here, the decode stops before inflating anything.
        if (!declaredSizeAllowed(w, h)) throw ImageTooLarge()
        qrTargetSize(w, h)?.let { (tw, th) -> decoder.setTargetSize(tw, th) }
    }

private fun withBitmapFactory(bytes: ByteArray): Bitmap {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw QrImageUnreadable()
    if (!declaredSizeAllowed(bounds.outWidth, bounds.outHeight)) throw ImageTooLarge()
    val options = BitmapFactory.Options().apply {
        inSampleSize = qrSampleSize(bounds.outWidth, bounds.outHeight)
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: throw QrImageUnreadable()
}
