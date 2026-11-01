package app.seguridad.core

import java.util.Locale

/** The type of a document whose content the app does not describe. */
const val OCTET_STREAM = "application/octet-stream"

/**
 * The MIME type to ask for when saving a restored file (a decrypted file or attachment) under [name]: the type of its
 * extension ([lookup], `MimeTypeMap` on Android) when it is an image, a video, an audio file, plain text or a PDF, and
 * [OCTET_STREAM] for anything else (no extension, an unknown or long one, or a type that could be run or rendered as a
 * page, such as an APK, HTML or SVG). Owner's phone test, 1-11-2026: Android's documents provider keeps a name's
 * extension apart only when it maps to the type asked for (`FileUtils.splitFileName`); with [OCTET_STREAM] it does not,
 * so a name already taken got « (1)» after the extension («copia-de-foto.jpg (1)») and the file no longer opened. With
 * the extension's type it gives «copia-de-foto (1).jpg». The bytes written are the same.
 *
 * Side effect, harmless: if the user removes or changes the extension in the save dialog, the provider adds the one of
 * this type («foto» is saved as «foto.jpg»). The provider lowercases the extension with the phone's language
 * ([locale]); where that differs from a plain lowercase (Turkish or Azeri: «HEIC» → «heıc»), the extension would not
 * match and it would add a second one («IMG.HEIC.heic»), so [OCTET_STREAM] is kept, as before this fix.
 */
fun restoredFileMime(name: String, locale: Locale = Locale.getDefault(), lookup: (String) -> String?): String {
    val raw = name.substringAfterLast('.', "")
    val ext = raw.lowercase(Locale.ROOT)
    if (ext.isEmpty() || ext.length > 8 || raw.lowercase(locale) != ext) return OCTET_STREAM
    val mime = lookup(ext)?.lowercase(Locale.ROOT) ?: return OCTET_STREAM
    val kept = mime.startsWith("image/") && mime != "image/svg+xml" ||
        mime.startsWith("video/") || mime.startsWith("audio/") || mime == "text/plain" || mime == "application/pdf"
    return if (kept) mime else OCTET_STREAM
}
