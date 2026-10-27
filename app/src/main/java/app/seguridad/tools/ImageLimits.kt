package app.seguridad.tools

/** Largest image file read by «Limpiar fotos» and «Tachar»; checked before reading (untrusted input). */
const val MAX_IMAGE_BYTES = 50 * 1024 * 1024

/** Largest image «Tachar» decodes (24 MP), and the cap for the HEIC → JPEG conversion. */
const val MAX_REDACT_PIXELS = 24_000_000
