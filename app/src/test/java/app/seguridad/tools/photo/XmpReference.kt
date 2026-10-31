package app.seguridad.tools.photo

/**
 * The XMP reader of 14ec824 (regex-based, before M6 and N1 of the F6 reviews), kept in the tests as the reference the
 * windowed reader must agree with on every input (N1 of the F6 re-review). Quadratic-like on hostile input: only for
 * short texts and boundary placements here.
 */
internal object XmpReference {
    private val AUTHOR = listOf("dc:creator", "dc:rights", "xmpRights:")
    private val GPS = listOf("GPSLatitude", "GPSLongitude")
    private val DEVICE = listOf("tiff:Make", "tiff:Model", "SerialNumber")
    private val MOTION = listOf("MotionPhoto", "MicroVideo")
    private const val DC_NS = "http://purl.org/dc/elements/1.1/"
    private val DC_PREFIX = Regex("""xmlns:([A-Za-z_][A-Za-z0-9_.-]{0,63})\s{0,16}=\s{0,16}["']http://purl\.org/dc/elements/1\.1/["']""")

    /**
     * HERR-1 (audit 1.3.0): the most distinct prefixes bound to Dublin Core that are looked under. Each one costs two
     * passes over the text, so without a cap a packet of many declarations cost quadratic time (hours for 50 MB). A real
     * XMP binds it once or twice; one with more is reported as carrying author data, which is the safe side: the screen
     * only warns, and the copy drops the packet whatever it says.
     */
    internal const val MAX_DC_PREFIXES = 16

    /** How many declarations are read between two calls of the caller's checkpoint. */
    private const val MATCHES_PER_CHECK = 1024

    /**
     * Linear in the text: a fixed list of substring searches plus at most [MAX_DC_PREFIXES] pairs of them. [check] is the
     * caller's cancellation checkpoint (`ensureActive()` on Android): called between the searches, and while the
     * declarations are read.
     */
    fun flags(b: ByteArray, from: Int, to: Int, check: () -> Unit = {}): Set<Found> {
        if (from < 0 || to > b.size || from > to) return emptySet()
        val text = String(b, from, to - from, Charsets.ISO_8859_1)
        check()
        val f = HashSet<Found>()
        if (AUTHOR.any { it in text } || dublinCoreAuthor(text, check)) f += Found.AUTHOR_RIGHTS
        check()
        if (GPS.any { it in text }) f += Found.GPS
        if (DEVICE.any { it in text }) f += Found.DEVICE
        if (MOTION.any { it in text }) f += Found.MOTION_VIDEO
        return f
    }

    /**
     * creator or rights under whatever prefix the XMP binds to the Dublin Core namespace; true as well when it binds more
     * than [MAX_DC_PREFIXES] distinct prefixes (fail safe, see there).
     */
    private fun dublinCoreAuthor(text: String, check: () -> Unit): Boolean {
        if (DC_NS !in text) return false
        val prefixes = LinkedHashSet<String>()
        var read = 0
        for (m in DC_PREFIX.findAll(text)) {
            if (++read % MATCHES_PER_CHECK == 0) check()
            if (prefixes.add(m.groupValues[1]) && prefixes.size > MAX_DC_PREFIXES) return true
        }
        for (prefix in prefixes) {
            check()
            if ("$prefix:creator" in text || "$prefix:rights" in text) return true
        }
        return false
    }
}
