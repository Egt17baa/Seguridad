package app.seguridad.tools.photo

/** What an XMP packet says, found by text search (no XML parser, no entity expansion). */
internal object Xmp {
    private val AUTHOR = listOf("dc:creator", "dc:rights", "xmpRights:")
    private val GPS = listOf("GPSLatitude", "GPSLongitude")
    private val DEVICE = listOf("tiff:Make", "tiff:Model", "SerialNumber")
    private val MOTION = listOf("MotionPhoto", "MicroVideo")
    private const val DC_NS = "http://purl.org/dc/elements/1.1/"
    private const val DECLARATION = "xmlns:"
    private val USES = listOf(":creator", ":rights")

    /**
     * HERR-1 (audit 1.3.0): the most distinct prefixes bound to Dublin Core that are looked under. A real XMP binds it
     * once or twice; one with more is reported as carrying author data, which is the safe side: the screen only warns,
     * and the copy drops the packet whatever it says.
     */
    internal const val MAX_DC_PREFIXES = 16

    /**
     * M6 of the F6 review: the packet is read in windows of this many bytes, with the caller's checkpoint before each, so
     * a 45 MB packet is cancelled within one window. Consecutive windows overlap by [OVERLAP], more than the longest
     * thing looked for (a declaration: `xmlns:`, a 64-character name, 16 spaces, `=`, 16 spaces, a quote, the namespace
     * and a quote, about 140), so nothing is lost at a boundary.
     */
    internal const val WINDOW = 1 shl 20
    private const val OVERLAP = 256

    /** The longest name a declaration may bind (as the regex it replaces: a letter or `_` and up to 63 more). */
    private const val MAX_NAME = 64
    private const val MAX_SPACES = 16

    /**
     * Linear in the packet, with no regular expression (M6: the old one cost about 60 ns per byte on text made to
     * nearly match it): a fixed list of substring searches and one hand-written pass over the declarations, then, only
     * if Dublin Core is bound under some prefix, one more pass for `prefix:creator` and `prefix:rights`. [check] is the
     * caller's cancellation checkpoint (`ensureActive()` on Android), called before each window.
     */
    fun flags(b: ByteArray, from: Int, to: Int, check: () -> Unit = {}): Set<Found> {
        if (from < 0 || to > b.size || from > to) return emptySet()
        val f = HashSet<Found>()
        val prefixes = LinkedHashSet<String>()
        var tooMany = false
        windows(b, from, to, check) { text ->
            if (Found.AUTHOR_RIGHTS !in f && AUTHOR.any { it in text }) f += Found.AUTHOR_RIGHTS
            if (Found.GPS !in f && GPS.any { it in text }) f += Found.GPS
            if (Found.DEVICE !in f && DEVICE.any { it in text }) f += Found.DEVICE
            if (Found.MOTION_VIDEO !in f && MOTION.any { it in text }) f += Found.MOTION_VIDEO
            if (!tooMany && Found.AUTHOR_RIGHTS !in f && DC_NS in text) {
                declarations(text) { prefix -> if (prefixes.add(prefix) && prefixes.size > MAX_DC_PREFIXES) tooMany = true }
            }
        }
        if (Found.AUTHOR_RIGHTS !in f && (tooMany || (prefixes.isNotEmpty() && usedAnywhere(b, from, to, prefixes, check)))) {
            f += Found.AUTHOR_RIGHTS
        }
        return f
    }

    /** Runs [block] on each window of `b[from, to)` as Latin-1 text (so one char is one byte), after [check]. */
    private inline fun windows(b: ByteArray, from: Int, to: Int, check: () -> Unit, block: (String) -> Unit) {
        var start = from
        while (true) {
            check()
            val end = minOf(to.toLong(), start.toLong() + WINDOW + OVERLAP).toInt()
            block(String(b, start, end - start, Charsets.ISO_8859_1))
            if (end >= to) return
            start += WINDOW
        }
    }

    /**
     * creator or rights under one of [prefixes]: any `prefix:creator` or `prefix:rights` in the packet, also at the end
     * of a longer name (as a plain substring search found it). At most 2 × [MAX_DC_PREFIXES] searches per window.
     */
    private fun usedAnywhere(b: ByteArray, from: Int, to: Int, prefixes: Set<String>, check: () -> Unit): Boolean {
        var found = false
        windows(b, from, to, check) { text ->
            if (!found) found = prefixes.any { p -> USES.any { "$p$it" in text } }
        }
        return found
    }

    private fun isNameStart(c: Char) = c in 'A'..'Z' || c in 'a'..'z' || c == '_'
    private fun isNameChar(c: Char) = isNameStart(c) || c in '0'..'9' || c == '.' || c == '-'
    private fun isSpace(c: Char) = c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\u000B' || c == '\u000C'

    /**
     * Every `xmlns:NAME = "http://purl.org/dc/elements/1.1/"` in [text] (either quote, up to [MAX_SPACES] spaces on
     * each side of `=`), read by hand: each step moves forward, so the cost is linear.
     */
    private inline fun declarations(text: String, onPrefix: (String) -> Unit) {
        val n = text.length
        var i = text.indexOf(DECLARATION)
        while (i >= 0) {
            var j = i + DECLARATION.length
            val nameAt = j
            if (j < n && isNameStart(text[j])) {
                j++
                while (j < n && j - nameAt < MAX_NAME && isNameChar(text[j])) j++
                val nameEnd = j
                if (j < n && !isNameChar(text[j])) {
                    var spaces = 0
                    while (j < n && spaces < MAX_SPACES && isSpace(text[j])) { j++; spaces++ }
                    if (j < n && text[j] == '=') {
                        j++
                        spaces = 0
                        while (j < n && spaces < MAX_SPACES && isSpace(text[j])) { j++; spaces++ }
                        if (j < n && (text[j] == '"' || text[j] == '\'') && text.startsWith(DC_NS, j + 1)) {
                            val close = j + 1 + DC_NS.length
                            if (close < n && (text[close] == '"' || text[close] == '\'')) onPrefix(text.substring(nameAt, nameEnd))
                        }
                    }
                }
            }
            i = text.indexOf(DECLARATION, i + DECLARATION.length)
        }
    }
}
