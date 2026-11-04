package app.seguridad.imports

/**
 * A reader of one family of formats: [ReadResult] if [text] is one of its formats, null if it is not (then the next
 * reader is asked). It may throw [ImportFailure] for a whole-file failure. [password] is the one the user typed for an
 * encrypted file, or null.
 */
internal typealias TextReader = (text: String, password: CharArray?) -> ReadResult?

/**
 * The readers, in the order they are asked. The format is told by the content, never by the file's name or extension
 * (spec «Detección»):
 * - a CSV with a header Clavero knows ([readCsvImport]), asked first as before;
 * - a JSON object of a known shape ([readJsonImport]): only a text that starts with `{`;
 * - a list of `otpauth://` links ([readOtpAuthListImport]): only a text whose first line is a link.
 * Task 10 adds the password to the JSON readers; until then an encrypted file only says that it needs one.
 */
private val READERS: List<TextReader> = listOf(
    { text, _ -> readCsvImport(text)?.let { ReadResult.Ok(it) } },
    { text, _ -> readJsonImport(text) },
    { text, _ -> readOtpAuthListImport(text)?.let { ReadResult.Ok(it) } },
)

/**
 * What [bytes] hold, as the import shows it. The file is refused past [IMPORT_MAX_BYTES] before anything is decoded
 * (the caller reads at most one byte more). The text must be UTF-8 or UTF-16 with BOM ([decodeText]). Then each
 * reader is asked in turn; if none knows the file, [IMPORT_UNKNOWN].
 *
 * Nothing escapes: an [ImportFailure] becomes [ReadResult.Failed] with its message, and any other exception becomes
 * [IMPORT_BROKEN] (its own message is never used: it could carry a value from the file). Errors such as
 * OutOfMemoryError are not caught; the limits are what prevent them.
 *
 * Wiping (spec «Todo lector»): the caller owns [bytes] and [password]. readImport never changes, keeps or wipes
 * them, so the caller can ask again with a password from the same bytes; the caller zeroes both in a `finally` as
 * soon as it is done with them (`bytes.fill(0)`, `password.fill('\u0000')`), also when the result is a failure.
 * What readImport makes on its own (the decoder's buffer, the CSV field buffer) it wipes itself.
 */
fun readImport(bytes: ByteArray, password: CharArray? = null): ReadResult = readImport(bytes, password, READERS)

internal fun readImport(bytes: ByteArray, password: CharArray?, readers: List<TextReader>): ReadResult {
    if (bytes.size > IMPORT_MAX_BYTES) return ReadResult.Failed(IMPORT_FILE_TOO_BIG)
    return try {
        val text = decodeText(bytes) ?: return ReadResult.Failed(IMPORT_NOT_TEXT)
        readers.firstNotNullOfOrNull { it(text, password) } ?: ReadResult.Failed(IMPORT_UNKNOWN)
    } catch (e: ImportFailure) {
        ReadResult.Failed(e.message ?: IMPORT_BROKEN)
    } catch (e: Exception) {
        ReadResult.Failed(IMPORT_BROKEN)
    }
}
