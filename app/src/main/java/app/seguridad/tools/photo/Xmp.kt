package app.seguridad.tools.photo

/** What an XMP packet says, found by text search (no XML parser, no entity expansion). */
internal object Xmp {
    private val AUTHOR = listOf("dc:creator", "dc:rights", "xmpRights:")
    private val GPS = listOf("GPSLatitude", "GPSLongitude")
    private val DEVICE = listOf("tiff:Make", "tiff:Model", "SerialNumber")
    private val MOTION = listOf("MotionPhoto", "MicroVideo")
    private const val DC_NS = "http://purl.org/dc/elements/1.1/"
    private val DC_PREFIX = Regex("""xmlns:([A-Za-z_][A-Za-z0-9_.-]{0,63})\s{0,16}=\s{0,16}["']http://purl\.org/dc/elements/1\.1/["']""")

    fun flags(b: ByteArray, from: Int, to: Int): Set<Found> {
        if (from < 0 || to > b.size || from > to) return emptySet()
        val text = String(b, from, to - from, Charsets.ISO_8859_1)
        val f = HashSet<Found>()
        if (AUTHOR.any { it in text } || dublinCoreAuthor(text)) f += Found.AUTHOR_RIGHTS
        if (GPS.any { it in text }) f += Found.GPS
        if (DEVICE.any { it in text }) f += Found.DEVICE
        if (MOTION.any { it in text }) f += Found.MOTION_VIDEO
        return f
    }

    /** creator or rights under whatever prefix the XMP binds to the Dublin Core namespace. */
    private fun dublinCoreAuthor(text: String): Boolean {
        if (DC_NS !in text) return false
        return DC_PREFIX.findAll(text).any { m ->
            val prefix = m.groupValues[1]
            "$prefix:creator" in text || "$prefix:rights" in text
        }
    }
}
