package app.seguridad.core

/** The type of a document whose content the app does not describe. */
const val OCTET_STREAM = "application/octet-stream"

/**
 * The MIME type to ask for when saving a restored file (a decrypted file or attachment) under [name]: the type of its
 * extension ([lookup], `MimeTypeMap` on Android) when it is an image, a video, an audio file, plain text or a PDF, and
 * [OCTET_STREAM] for anything else (no extension, an unknown one, or a type that could be run or rendered as a page,
 * such as an APK, HTML or SVG). Owner's phone test, 1-11-2026: with [OCTET_STREAM] the documents provider cannot tell
 * where the name ends, so when the name is taken it appends « (1)» after the extension («copia-de-foto.jpg (1)») and
 * the file no longer opens; with the extension's type it gives «copia-de-foto (1).jpg». The bytes written are the same.
 */
fun restoredFileMime(name: String, lookup: (String) -> String?): String {
    val ext = name.substringAfterLast('.', "").lowercase()
    if (ext.isEmpty() || ext.length > 8) return OCTET_STREAM
    val mime = lookup(ext)?.lowercase() ?: return OCTET_STREAM
    val kept = mime.startsWith("image/") && mime != "image/svg+xml" ||
        mime.startsWith("video/") || mime.startsWith("audio/") || mime == "text/plain" || mime == "application/pdf"
    return if (kept) mime else OCTET_STREAM
}
