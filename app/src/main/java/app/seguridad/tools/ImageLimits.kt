package app.seguridad.tools

/** Largest image file read by «Limpiar fotos» and «Tachar»; checked before reading (untrusted input). */
const val MAX_IMAGE_BYTES = 50 * 1024 * 1024

/** Largest image «Tachar» decodes (24 MP), and the cap for the HEIC → JPEG conversion. */
const val MAX_REDACT_PIXELS = 24_000_000

/**
 * Largest size an image may declare in its header, checked before any pixel is decoded: a small file can declare a
 * huge image (a PNG compresses about 1000 to 1), and inflating it all takes minutes even when the decode is scaled down.
 */
const val MAX_DECLARED_PIXELS = 100_000_000L
const val MAX_DECLARED_SIDE = 20_000

/** The declared size is over [MAX_DECLARED_PIXELS] or [MAX_DECLARED_SIDE]; each screen shows its own fixed text. */
class ImageTooLarge : Exception()
