package app.seguridad.vault

/*
 * 2.0, task 5 (design «Notas con formato limitado»): the app's own reader of the notes' format, written from scratch
 * (Abogado Digital, R-5: nothing copied from any published specification, and the screens never call it by another
 * name). It knows only `# `, `## `, `### ` (titles), `- ` (a list), `1. ` (a numbered list) at the start of a line, and
 * `**…**` (bold), `*…*` (italic) inside it. Everything else is text as it is: links, addresses, `<`, `[`, `](`, HTML,
 * images. The card turns the blocks into styled text with nothing to tap ([app.seguridad.ui.screens.MarkupText]);
 * nothing here ever becomes a link, an `Intent` or a web view (decision (c) of 1.4.0). The notes are kept as typed.
 *
 * Bounded (decision (a) of 1.4.0): no recursion, one pass over each line, and at most [LimitedMarkup.MAX_LINES] lines
 * and [LimitedMarkup.MAX_CHARS] characters read as format; the rest comes back, as it is, as one plain paragraph.
 */

/** A run of text of one style. */
data class Span(val text: String, val bold: Boolean, val italic: Boolean)

/** One line of the notes, as the card shows it. */
sealed interface MarkupBlock {
    val spans: List<Span>

    /** `# `, `## ` or `### `: [level] 1 to 3. */
    data class Heading(val level: Int, override val spans: List<Span>) : MarkupBlock

    data class Paragraph(override val spans: List<Span>) : MarkupBlock

    /** `- `. */
    data class Bullet(override val spans: List<Span>) : MarkupBlock

    /** `1. `, with the number as typed (at most 9 digits). */
    data class Numbered(val n: Int, override val spans: List<Span>) : MarkupBlock
}

object LimitedMarkup {
    /** The most characters read as format (64 KiB of text); what follows is plain text. */
    const val MAX_CHARS = 64 * 1024

    /** The most lines read as format; what follows is plain text. */
    const val MAX_LINES = 10_000

    /** The longest number of a numbered line. */
    private const val NUMBER_DIGITS_MAX = 9

    /** [text] as blocks, one per line (a line break between each); see the file's comment for what is read. */
    fun parse(text: String): List<MarkupBlock> {
        val out = ArrayList<MarkupBlock>()
        var limit = minOf(text.length, MAX_CHARS)
        // Never in the middle of a character outside the basic plane.
        if (limit in 1 until text.length && Character.isHighSurrogate(text[limit - 1])) limit--
        var pos = 0
        while (out.size < MAX_LINES) {
            val nl = text.indexOf('\n', pos)
            val end = if (nl < 0) text.length else nl
            // A line that does not fit in what is read as format goes, whole, with the rest.
            if (end > limit) break
            out += block(text.substring(pos, end).removeSuffix("\r"))
            if (nl < 0) return out
            pos = nl + 1
        }
        val rest = text.substring(pos)
        out += MarkupBlock.Paragraph(if (rest.isEmpty()) emptyList() else listOf(Span(rest, bold = false, italic = false)))
        return out
    }

    private fun block(line: String): MarkupBlock = when {
        line.startsWith("### ") -> MarkupBlock.Heading(3, spans(line.substring(4)))
        line.startsWith("## ") -> MarkupBlock.Heading(2, spans(line.substring(3)))
        line.startsWith("# ") -> MarkupBlock.Heading(1, spans(line.substring(2)))
        line.startsWith("- ") -> MarkupBlock.Bullet(spans(line.substring(2)))
        else -> numbered(line) ?: MarkupBlock.Paragraph(spans(line))
    }

    private fun numbered(line: String): MarkupBlock? {
        var i = 0
        while (i < line.length && i <= NUMBER_DIGITS_MAX && line[i] in '0'..'9') i++
        if (i == 0 || i > NUMBER_DIGITS_MAX || !line.startsWith(". ", i)) return null
        return MarkupBlock.Numbered(line.substring(0, i).toInt(), spans(line.substring(i + 2)))
    }

    /**
     * The spans of one line. Each run of one to three `*` is a mark (one: italic, two: bold, three: both); a run of four
     * or more is text. A mark opens when a character other than a space follows it and closes when one comes before it;
     * a mark that finds no partner on its line is text. One pass to find the runs, one per style to pair them (at most
     * one open mark of each style at a time) and one to write the spans: linear in the line.
     */
    private fun spans(line: String): List<Span> {
        if ('*' !in line) return if (line.isEmpty()) emptyList() else listOf(Span(line, bold = false, italic = false))
        // The runs of `*`: where each starts and how long it is.
        val starts = ArrayList<Int>()
        val lengths = ArrayList<Int>()
        var i = 0
        while (i < line.length) {
            if (line[i] == '*') {
                val s = i
                while (i < line.length && line[i] == '*') i++
                starts += s
                lengths += i - s
            } else {
                i++
            }
        }
        val n = starts.size
        val canOpen = BooleanArray(n) { r -> starts[r] + lengths[r] < line.length && !line[starts[r] + lengths[r]].isWhitespace() }
        val canClose = BooleanArray(n) { r -> starts[r] > 0 && !line[starts[r] - 1].isWhitespace() }
        val boldPaired = BooleanArray(n)
        val italicPaired = BooleanArray(n)
        fun pair(uses: (Int) -> Boolean, paired: BooleanArray) {
            var open = -1
            for (r in 0 until n) {
                if (!uses(lengths[r])) continue
                if (canClose[r] && open >= 0) {
                    paired[open] = true
                    paired[r] = true
                    open = -1
                } else if (canOpen[r]) {
                    open = r
                }
            }
        }
        pair({ it == 2 || it == 3 }, boldPaired)
        pair({ it == 1 || it == 3 }, italicPaired)

        val out = ArrayList<Span>()
        val buf = StringBuilder()
        var bold = false
        var italic = false
        fun flush() {
            if (buf.isEmpty()) return
            val last = out.lastOrNull()
            if (last != null && last.bold == bold && last.italic == italic) out[out.size - 1] = last.copy(text = last.text + buf)
            else out += Span(buf.toString(), bold, italic)
            buf.setLength(0)
        }
        var at = 0
        for (r in 0 until n) {
            buf.append(line, at, starts[r])
            var left = lengths[r]
            if (boldPaired[r] || italicPaired[r]) {
                flush()
                if (boldPaired[r]) {
                    bold = !bold
                    left -= 2
                }
                if (italicPaired[r]) {
                    italic = !italic
                    left -= 1
                }
            }
            repeat(left) { buf.append('*') }
            at = starts[r] + lengths[r]
        }
        buf.append(line, at, line.length)
        flush()
        return out
    }
}

/**
 * The editor's buttons for the format (bold, italic, title, list): each gives the new text and where the selection goes
 * (start and end), and never reads more than the text it is given.
 */
object MarkupEdit {
    /** [mark] (`**` or `*`) around the selection [start]..[end]; with nothing selected, a pair with the cursor between. */
    fun wrap(text: String, start: Int, end: Int, mark: String): Triple<String, Int, Int> {
        val a = start.coerceIn(0, text.length)
        val b = end.coerceIn(a, text.length)
        val out = text.substring(0, a) + mark + text.substring(a, b) + mark + text.substring(b)
        return Triple(out, a + mark.length, b + mark.length)
    }

    /**
     * The line the cursor [at] is on with [prefix] (`# ` or `- `) at its start, or without it if it already had it. A
     * title replaces another level of title.
     */
    fun toggleLinePrefix(text: String, at: Int, prefix: String): Triple<String, Int, Int> {
        val c = at.coerceIn(0, text.length)
        val lineStart = if (c == 0) 0 else text.lastIndexOf('\n', c - 1) + 1
        val line = text.substring(lineStart)
        val existing = when {
            line.startsWith(prefix) -> prefix
            prefix.startsWith("#") -> listOf("### ", "## ", "# ").firstOrNull { line.startsWith(it) }
            else -> null
        }
        val removed = existing?.length ?: 0
        val added = if (existing == prefix) "" else prefix
        val out = text.substring(0, lineStart) + added + text.substring(lineStart + removed)
        val cursor = (c - removed + added.length).coerceIn(lineStart, out.length)
        return Triple(out, cursor, cursor)
    }
}
