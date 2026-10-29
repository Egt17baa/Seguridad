package app.seguridad.tools

/** Largest image file read by «Limpiar fotos» and «Tachar»; checked before reading (untrusted input). */
const val MAX_IMAGE_BYTES = 50 * 1024 * 1024

/** Largest image «Tachar» decodes (24 MP), and the cap for the HEIC → JPEG conversion. */
const val MAX_REDACT_PIXELS = 24_000_000

/**
 * Largest size an image may declare in its header, checked before any pixel is decoded: a small file can declare a
 * huge image (a PNG compresses about 1000 to 1), and inflating it all takes minutes even when the decode is scaled down.
 * Leaves room for full-resolution 200 MP phone photos (16320×12240, as the owner's Honor Magic 8 Pro shoots), which
 * are then decoded scaled down to [MAX_REDACT_PIXELS].
 */
const val MAX_DECLARED_PIXELS = 260_000_000L
const val MAX_DECLARED_SIDE = 32_768

/** The declared size is over [MAX_DECLARED_PIXELS] or [MAX_DECLARED_SIDE]; each screen shows its own fixed text. */
class ImageTooLarge : Exception()

/** The picked file is over [MAX_IMAGE_BYTES], by the size its provider reports or by what was read. */
class ImageTooBig : Exception()

/**
 * The phone ran out of memory reading or decoding the image (a decoder turns its OutOfMemoryError into this, so it is
 * never told apart as a damaged file).
 */
class ImageOutOfMemory : Exception()

/**
 * What a screen says when opening a picked image failed with [t]: its own texts for a file too big ([tooBig]) or an
 * image too large ([tooLarge]), [IMAGE_NO_MEMORY] when memory ran out (M-7: never «el archivo está dañado»), and
 * [other] for anything else.
 */
fun imageOpenFailureText(t: Throwable, tooBig: String, tooLarge: String, other: (Throwable) -> String): String = when (t) {
    is ImageTooBig -> tooBig
    is ImageTooLarge -> tooLarge
    is ImageOutOfMemory, is OutOfMemoryError -> IMAGE_NO_MEMORY
    else -> other(t)
}
