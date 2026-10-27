package app.seguridad.tools.photo

/** «Error» in the 1.3.0 annex: damaged file or a structure we do not know. */
const val PHOTO_DAMAGED =
    "No se ha podido limpiar esta foto: el archivo está dañado o tiene un formato que no conocemos. No se ha guardado nada."

/** «Formato no admitido» in the 1.3.0 annex. */
const val PHOTO_UNSUPPORTED = "Este formato no se admite. Usa una foto JPEG, PNG, WebP o HEIC."

enum class PhotoFormat { JPEG, PNG, WEBP, HEIF }

/** What a photo carries inside the file. ICC_KEPT is the only one the cleaned copy keeps. */
enum class Found { GPS, DEVICE, DATE_SOFTWARE, THUMBNAIL, XMP_COMMENTS, ICC_KEPT, MOTION_VIDEO, C2PA, AUTHOR_RIGHTS, OTHER }

/**
 * @property gps latitude and longitude rounded to 2 decimals, or null if absent or unreadable.
 * @property orientation EXIF tag 0x0112, or 1 when it is missing or not in 1..8.
 */
data class PhotoReport(
    val format: PhotoFormat,
    val found: Set<Found>,
    val gps: Pair<Double, Double>?,
    val orientation: Int = 1,
)

/** The message is always [PHOTO_DAMAGED] or [PHOTO_UNSUPPORTED]. */
class PhotoFailure(message: String) : Exception(message)

private val HEIF_BRANDS = setOf("heic", "heix", "hevc", "heim", "heis", "mif1", "msf1", "avif")

/** AVIF brands, still and sequence, and the HEVC ones that make a file decodable as HEIC whatever else it lists. */
private val AVIF_BRANDS = setOf("avif", "avis")
private val HEVC_BRANDS = setOf("heic", "heix", "hevc", "heim", "heis", "hevx")

private fun ByteArray.at(i: Int, vararg v: Int) = size >= i + v.size && v.indices.all { this[i + it] == v[it].toByte() }
private fun ByteArray.ascii(i: Int, s: String) = at(i, *IntArray(s.length) { s[it].code })

/** The brands of a leading ISO-BMFF `ftyp` box (major, then the compatible ones up to the box end), or null. */
private fun ftypBrands(head: ByteArray): List<String>? {
    if (!head.ascii(4, "ftyp") || head.size < 12) return null
    val boxSize = ((head[0].toLong() and 0xFF) shl 24) or ((head[1].toLong() and 0xFF) shl 16) or
        ((head[2].toLong() and 0xFF) shl 8) or (head[3].toLong() and 0xFF)
    val end = minOf(boxSize, head.size.toLong()).toInt()
    val starts = buildList {
        add(8)
        var i = 16
        while (i + 4 <= end) { add(i); i += 4 }
    }
    return starts.map { i -> String(CharArray(4) { (head[i + it].toInt() and 0xFF).toChar() }) }
}

/** The container format from the first bytes of a file, or null if it is not one we know. */
fun detectFormat(head: ByteArray): PhotoFormat? = when {
    head.at(0, 0xFF, 0xD8, 0xFF) -> PhotoFormat.JPEG
    head.at(0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) -> PhotoFormat.PNG
    head.ascii(0, "RIFF") && head.ascii(8, "WEBP") -> PhotoFormat.WEBP
    else -> ftypBrands(head)?.let { brands -> if (brands.any { it in HEIF_BRANDS }) PhotoFormat.HEIF else null }
}

/**
 * True for an AVIF in the HEIF family (an `avif`/`avis` brand and no HEVC one). Android decodes AVIF only from
 * Android 12 (API 31), so before that it is «no admitido», not «dañado».
 */
fun isAvif(head: ByteArray): Boolean {
    val brands = ftypBrands(head) ?: return false
    return brands.any { it in AVIF_BRANDS } && brands.none { it in HEVC_BRANDS }
}
