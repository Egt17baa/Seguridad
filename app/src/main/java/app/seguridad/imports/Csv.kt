package app.seguridad.imports

/**
 * The rows of a CSV (RFC 4180), header first: fields between double quotes may hold the delimiter, line breaks and
 * doubled quotes («""» is one «"»). The delimiter is «,» or «;», whichever the header line uses more ([csvDelimiter]).
 *
 * Defined answers for what RFC 4180 does not allow, never a crash or a hang:
 * - a record ends at LF, CRLF or a CR alone (old Mac files); inside quotes they are text, kept as written;
 * - a line with nothing on it and the final line break add no row; `""` is a real empty cell;
 * - a quote inside an unquoted field, and text right after a closing quote, are kept as text (`ab"c`, `"abc"x` → `abcx`);
 * - NUL and control characters are kept: the readers decide what a row with them is;
 * - a quote that never closes throws [ImportFailure] ([IMPORT_CSV_UNCLOSED_QUOTE]).
 *
 * Limits, checked while reading, so a hostile file stops early with bounded memory: [CSV_MAX_COLUMNS] per row,
 * [CSV_MAX_CELLS] in all and [IMPORT_MAX_ITEMS] rows after the header, each an [ImportFailure]; and a field keeps at
 * most [CSV_MAX_FIELD_CHARS] characters (the rest is read and dropped), which is past every field limit, so that row
 * goes to «no entran» instead of entering cut. One pass, character by character: no regular expression, and every
 * field is built in one reused StringBuilder that never grows past the cap.
 */
fun parseCsv(text: String): List<List<String>> {
    val reader = CsvReader(text, csvDelimiter(text))
    try {
        val rows = ArrayList<List<String>>()
        while (true) rows += reader.next() ?: break
        return rows
    } finally {
        reader.wipe()
    }
}

/** The header row only, or null if it cannot be read: enough to tell the format without reading the rest. */
internal fun csvHeader(text: String): List<String>? {
    val reader = CsvReader(text, csvDelimiter(text))
    return try {
        reader.next()
    } catch (e: ImportFailure) {
        null
    } finally {
        reader.wipe()
    }
}

/** «;» when the first line (outside quotes) has more semicolons than commas, as a spreadsheet in Spanish saves; «,» otherwise. */
internal fun csvDelimiter(text: String): Char {
    var commas = 0
    var semicolons = 0
    var quoted = false
    for (c in text) {
        when {
            c == '"' -> quoted = !quoted
            quoted -> {}
            c == '\n' || c == '\r' -> break
            c == ',' -> commas++
            c == ';' -> semicolons++
        }
    }
    return if (semicolons > commas) ';' else ','
}

private class CsvReader(private val text: String, private val delimiter: Char) {
    private var pos = 0
    private var cells = 0
    private var rows = 0
    private val field = StringBuilder()

    /** The next row, or null at the end; blank lines are passed over. */
    fun next(): List<String>? {
        while (pos < text.length) {
            val row = record() ?: continue
            if (++rows > IMPORT_MAX_ITEMS + 1) throw ImportFailure(IMPORT_TOO_MANY_ITEMS)
            return row
        }
        return null
    }

    /** One record from [pos]; null for a line with nothing on it. */
    private fun record(): List<String>? {
        val row = ArrayList<String>()
        var started = false // something of the current field was read (its opening quote included)
        var quoted = false
        var lineHasContent = false
        while (true) {
            if (pos >= text.length) {
                if (quoted) throw ImportFailure(IMPORT_CSV_UNCLOSED_QUOTE)
                break
            }
            val c = text[pos++]
            if (quoted) {
                if (c == '"') {
                    if (pos < text.length && text[pos] == '"') {
                        append('"')
                        pos++
                    } else {
                        quoted = false
                    }
                } else {
                    append(c)
                }
                continue
            }
            when (c) {
                delimiter -> {
                    endField(row)
                    started = false
                    lineHasContent = true
                }
                '\n' -> break
                '\r' -> {
                    if (pos < text.length && text[pos] == '\n') pos++
                    break
                }
                '"' -> {
                    if (!started) quoted = true else append('"')
                    started = true
                    lineHasContent = true
                }
                else -> {
                    append(c)
                    started = true
                    lineHasContent = true
                }
            }
        }
        if (!lineHasContent) return null
        endField(row)
        return row
    }

    private fun append(c: Char) {
        if (field.length < CSV_MAX_FIELD_CHARS) field.append(c)
    }

    private fun endField(row: ArrayList<String>) {
        if (row.size >= CSV_MAX_COLUMNS) throw ImportFailure(IMPORT_CSV_TOO_MANY_COLUMNS)
        if (++cells > CSV_MAX_CELLS) throw ImportFailure(IMPORT_CSV_TOO_MANY_CELLS)
        // Empty cells share the one empty string: a file of commas holds no string per cell.
        row += if (field.isEmpty()) "" else field.toString()
        field.setLength(0)
    }

    /** Overwrites the builder's array (it held the last fields read: they may be passwords). */
    fun wipe() {
        field.setLength(0)
        field.setLength(field.capacity())
        field.setLength(0)
    }
}
