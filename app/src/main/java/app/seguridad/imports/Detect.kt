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
 * - a JSON object of a known shape ([readJsonImport]): only a text that starts with `{`; an encrypted one asks for
 *   its password ([ReadResult.NeedsPassword]) and is opened with it (Task 10);
 * - a list of `otpauth://` links ([readOtpAuthListImport]): only a text whose first line is a link.
 */
private val READERS: List<TextReader> = listOf(
    { text, _ -> readCsvImport(text)?.let { ReadResult.Ok(it) } },
    { text, password -> readJsonImport(text, password = password) },
    { text, _ -> readOtpAuthListImport(text)?.let { ReadResult.Ok(it) } },
)

/**
 * What [bytes] hold, as the import shows it. Before anything else, its first bytes tell two kinds of file Clavero does
 * not read, each with what to do instead (rulings R19 and R21): a `.zip` ([isZip], [IMPORT_ZIP]) and an HTML page
 * ([isHtml], [IMPORT_HTML]; never rendered nor parsed). Then the file is refused past [IMPORT_MAX_BYTES] before
 * anything is decoded (the caller reads at most one byte more). The text must be UTF-8 or UTF-16 with BOM ([decodeText]). Then each
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
    if (isZip(bytes)) return ReadResult.Failed(IMPORT_ZIP)
    if (isHtml(bytes)) return ReadResult.Failed(IMPORT_HTML)
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

/** A ZIP archive: its local file header magic `PK\u0003\u0004` (as Bitwarden's «.zip (with attachments)»). */
internal fun isZip(bytes: ByteArray): Boolean =
    bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() && bytes[2] == 0x03.toByte() && bytes[3] == 0x04.toByte()

/** How many bytes of the start are looked at to tell an HTML page. */
private const val HTML_PROBE_BYTES = 512

/**
 * An HTML page: after an optional BOM (UTF-8, UTF-16 LE or BE) and white space, `<!DOCTYPE` or `<html` followed by
 * a space, `>` or the end, in any case. Only the first [HTML_PROBE_BYTES] are decoded, leniently, to look; nothing of
 * it is parsed or shown.
 */
internal fun isHtml(bytes: ByteArray): Boolean {
    val (charset, start) = when {
        bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() -> Charsets.UTF_8 to 3
        bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() -> Charsets.UTF_16LE to 2
        bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() -> Charsets.UTF_16BE to 2
        else -> Charsets.UTF_8 to 0
    }
    val head = String(bytes, start, minOf(bytes.size - start, HTML_PROBE_BYTES), charset).trimStart()
    for (tag in listOf("<!doctype", "<html")) {
        if (head.regionMatches(0, tag, 0, tag.length, ignoreCase = true)) {
            val next = head.getOrNull(tag.length)
            if (next == null || next == '>' || next.isWhitespace()) return true
        }
    }
    return false
}
