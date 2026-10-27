package app.seguridad.tools.photo

/**
 * Entry point for «Limpiar foto»: picks the reader by format and re-checks every output.
 * Only [PhotoFailure] leaves this object, with [PHOTO_DAMAGED] or [PHOTO_UNSUPPORTED].
 */
object PhotoCleaner {
    fun inspect(bytes: ByteArray): PhotoReport = guarded {
        when (detectFormat(bytes)) {
            PhotoFormat.JPEG -> Jpeg.inspect(bytes)
            // PNG and WebP come in the next task; HEIF is converted to JPEG on Android.
            else -> throw PhotoFailure(PHOTO_UNSUPPORTED)
        }
    }

    fun clean(bytes: ByteArray): ByteArray = guarded {
        val format = detectFormat(bytes)
        val out = when (format) {
            PhotoFormat.JPEG -> Jpeg.clean(bytes)
            else -> throw PhotoFailure(PHOTO_UNSUPPORTED)
        }
        verify(format, out)
        out
    }

    /**
     * Fails closed unless [out] is, segment by segment, only what the format's whitelist allows
     * (each format gives its own `isCleanOutput`), reports nothing but the kept colour profile,
     * and cleans to itself.
     */
    internal fun verify(format: PhotoFormat, out: ByteArray) {
        val ok = try {
            detectFormat(out) == format && when (format) {
                // Structural whitelist first, then the report, then idempotence.
                PhotoFormat.JPEG -> Jpeg.isCleanOutput(out) &&
                    (Jpeg.inspect(out).found - Found.ICC_KEPT).isEmpty() &&
                    Jpeg.clean(out).contentEquals(out)
                else -> false
            }
        } catch (e: PhotoFailure) {
            false
        }
        if (!ok) throw PhotoFailure(PHOTO_DAMAGED)
    }

    /** Defence in depth: a bug in a reader on hostile input becomes «damaged», not a crash. */
    private inline fun <T> guarded(block: () -> T): T = try {
        block()
    } catch (e: PhotoFailure) {
        throw e
    } catch (e: RuntimeException) {
        throw PhotoFailure(PHOTO_DAMAGED)
    }
}
