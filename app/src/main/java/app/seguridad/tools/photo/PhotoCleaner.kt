package app.seguridad.tools.photo

import java.util.concurrent.CancellationException

/**
 * Entry point for «Limpiar foto»: picks the reader by format and re-checks every output.
 * Only [PhotoFailure] leaves this object, with [PHOTO_DAMAGED] or [PHOTO_UNSUPPORTED], or the [CancellationException]
 * thrown by the caller's [check].
 *
 * [check] is the caller's cancellation checkpoint (HERR-1, audit 1.3.0): on Android, `ensureActive()` of the coroutine
 * that runs this, so leaving the screen or opening another photo stops the work and frees its thread. The readers call
 * it once per segment or chunk, every MiB of JPEG scan data and while an XMP packet is read. It is plain code, with no
 * coroutines in it.
 */
object PhotoCleaner {
    fun inspect(bytes: ByteArray, check: () -> Unit = {}): PhotoReport = guarded {
        when (detectFormat(bytes)) {
            PhotoFormat.JPEG -> Jpeg.inspect(bytes, check)
            PhotoFormat.PNG -> Png.inspect(bytes, check)
            PhotoFormat.WEBP -> Webp.inspect(bytes, check)
            // HEIF is converted to JPEG on Android.
            else -> throw PhotoFailure(PHOTO_UNSUPPORTED)
        }
    }

    fun clean(bytes: ByteArray, check: () -> Unit = {}): ByteArray = guarded {
        val format = detectFormat(bytes)
        val out = when (format) {
            PhotoFormat.JPEG -> Jpeg.clean(bytes, check)
            PhotoFormat.PNG -> Png.clean(bytes, check)
            PhotoFormat.WEBP -> Webp.clean(bytes, check)
            else -> throw PhotoFailure(PHOTO_UNSUPPORTED)
        }
        verify(format, out, check)
        out
    }

    /**
     * Fails closed unless [out] is, segment by segment, only what the format's whitelist allows
     * (each format gives its own `isCleanOutput`), reports nothing but the kept colour profile,
     * and cleans to itself.
     */
    internal fun verify(format: PhotoFormat, out: ByteArray, check: () -> Unit = {}) {
        val ok = try {
            detectFormat(out) == format && when (format) {
                // Structural whitelist first, then the report, then idempotence.
                PhotoFormat.JPEG -> Jpeg.isCleanOutput(out, check) &&
                    (Jpeg.inspect(out, check).found - Found.ICC_KEPT).isEmpty() &&
                    Jpeg.clean(out, check).contentEquals(out)
                PhotoFormat.PNG -> Png.isCleanOutput(out, check) &&
                    (Png.inspect(out, check).found - Found.ICC_KEPT).isEmpty() &&
                    Png.clean(out, check).contentEquals(out)
                PhotoFormat.WEBP -> Webp.isCleanOutput(out, check) &&
                    (Webp.inspect(out, check).found - Found.ICC_KEPT).isEmpty() &&
                    Webp.clean(out, check).contentEquals(out)
                else -> false
            }
        } catch (e: PhotoFailure) {
            false
        }
        if (!ok) throw PhotoFailure(PHOTO_DAMAGED)
    }

    /**
     * Defence in depth: a bug in a reader on hostile input becomes «damaged», not a crash. A cancellation (an
     * IllegalStateException, so a RuntimeException) is the caller's and goes through untouched.
     */
    private inline fun <T> guarded(block: () -> T): T = try {
        block()
    } catch (e: PhotoFailure) {
        throw e
    } catch (e: CancellationException) {
        throw e
    } catch (e: RuntimeException) {
        throw PhotoFailure(PHOTO_DAMAGED)
    }
}
