package app.seguridad.tools.photo

/**
 * JPEG cleaning by rewriting the container with a closed list of kept segments (design §1).
 *
 * Kept: DQT, DHT, DRI, SOFn (not C4, C8, CC), DAC, SOS with its entropy-coded data,
 * APP2 "ICC_PROFILE" and APP14 "Adobe" (trimmed to its 12 bytes). Everything else is dropped,
 * and so is everything after the first top-level EOI. A non-default EXIF orientation is
 * written back as a fresh APP1 with that single tag.
 *
 * Input is hostile: every length and offset is checked against the array before use, every
 * loop advances, and the only exception that leaves this object is [PhotoFailure].
 */
object Jpeg {
    fun inspect(bytes: ByteArray): PhotoReport = parse(bytes).report

    fun clean(bytes: ByteArray): ByteArray {
        val parsed = parse(bytes)
        val orientation = if (parsed.report.orientation != 1) orientationApp1(parsed.report.orientation) else null
        var size = 4L + (orientation?.size ?: 0)
        for (p in parsed.pieces) size += p.length
        if (size > Int.MAX_VALUE - 8) throw PhotoFailure(PHOTO_DAMAGED)
        val out = ByteArray(size.toInt())
        out[0] = 0xFF.toByte(); out[1] = 0xD8.toByte()
        var o = 2
        if (orientation != null) { orientation.copyInto(out, o); o += orientation.size }
        for (p in parsed.pieces) {
            if (p.bytes != null) p.bytes.copyInto(out, o) else bytes.copyInto(out, o, p.start, p.end)
            o += p.length
        }
        out[o] = 0xFF.toByte(); out[o + 1] = 0xD9.toByte()
        return out
    }

    // --- Parsing ---------------------------------------------------------------------------

    /** A run of the input to copy, or replacement bytes. */
    private class Piece(val start: Int, val end: Int, val bytes: ByteArray? = null) {
        val length get() = bytes?.size ?: (end - start)
    }

    private class Parsed(val report: PhotoReport, val pieces: List<Piece>)

    private fun damaged(): Nothing = throw PhotoFailure(PHOTO_DAMAGED)

    private fun parse(b: ByteArray): Parsed {
        val n = b.size
        fun u8(i: Int) = b[i].toInt() and 0xFF
        if (n < 4 || u8(0) != 0xFF || u8(1) != 0xD8) damaged()

        val found = HashSet<Found>()
        val pieces = ArrayList<Piece>()
        var exif: ExifInfo? = null
        var sawSof = false
        var sawSos = false
        var p = 2
        var imageEnd = -1
        while (true) {
            if (p >= n || u8(p) != 0xFF) damaged()
            while (p < n && u8(p) == 0xFF) p++ // fill bytes
            if (p >= n) damaged()
            val m = u8(p)
            val segStart = p - 1 // the FF right before the marker
            p++
            when (m) {
                0xD9 -> { imageEnd = p; break }
                0x00, 0xD8 -> damaged()
                0x01, in 0xD0..0xD7 -> { found += Found.OTHER; continue } // standalone, no length: dropped
            }
            if (p + 2 > n) damaged()
            val len = (u8(p) shl 8) or u8(p + 1)
            if (len < 2) damaged()
            val payload = p + 2
            val segEnd = p + len
            if (segEnd > n) damaged()
            p = segEnd
            when (m) {
                0xDB, 0xC4, 0xDD, 0xCC -> pieces += Piece(segStart, segEnd)
                in 0xC0..0xCF -> if (m == 0xC8) found += Found.OTHER else { sawSof = true; pieces += Piece(segStart, segEnd) }
                0xDA -> {
                    if (!sawSof) damaged()
                    sawSos = true
                    val dataEnd = scanEnd(b, segEnd)
                    pieces += Piece(segStart, dataEnd)
                    p = dataEnd
                }
                0xFE -> found += Found.XMP_COMMENTS
                in 0xE0..0xEF -> {
                    val keep = app(b, m, payload, segEnd, found)
                    if (keep != null) pieces += keep
                    if (m == 0xE1 && Bytes.startsWith(b, payload, segEnd, EXIF)) {
                        // Every EXIF segment is reported; the first one gives orientation and GPS.
                        val info = Exif.read(b, payload + 6, segEnd)
                        found += info.found
                        if (exif == null) exif = info
                    }
                }
                else -> found += Found.OTHER // DNL, DHP, EXP, JPGn, reserved: dropped
            }
        }
        if (!sawSos) damaged()

        if (hasTrailingData(b, imageEnd)) found += Found.MOTION_VIDEO
        val report = PhotoReport(PhotoFormat.JPEG, found.toSet(), exif?.gps, exif?.orientation ?: 1)
        return Parsed(report, pieces)
    }

    /**
     * End of the entropy-coded data that starts at [start]: the first FF that is followed
     * (after any fill FFs) by a marker other than 00 (stuffed byte) or D0..D7 (restart).
     */
    private fun scanEnd(b: ByteArray, start: Int): Int {
        val n = b.size
        var i = start
        while (true) {
            if (i >= n) damaged() // no EOI
            if (b[i] != 0xFF.toByte()) { i++; continue }
            var j = i
            while (j + 1 < n && b[j + 1] == 0xFF.toByte()) j++
            if (j + 1 >= n) damaged()
            val next = b[j + 1].toInt() and 0xFF
            if (next == 0x00 || next in 0xD0..0xD7) { i = j + 2; continue }
            return i
        }
    }

    /** Anything after EOI other than zero or FF padding: a motion-photo video or another image. */
    private fun hasTrailingData(b: ByteArray, from: Int): Boolean {
        for (i in from until b.size) if (b[i] != 0.toByte() && b[i] != 0xFF.toByte()) return true
        return false
    }

    /** Reports an APPn segment and returns the piece to keep, if it is one of the two kept. */
    private fun app(b: ByteArray, m: Int, payload: Int, end: Int, found: MutableSet<Found>): Piece? {
        fun has(prefix: ByteArray) = Bytes.startsWith(b, payload, end, prefix)
        when (m) {
            0xE0 -> when {
                has(JFIF) -> {
                    // JFIF\0, version(2), units, Xdensity(2), Ydensity(2), Xthumbnail, Ythumbnail.
                    if (end - payload >= 14 && (b[payload + 12].toInt() and 0xFF) * (b[payload + 13].toInt() and 0xFF) > 0) {
                        found += Found.THUMBNAIL
                    }
                }
                has(JFXX) -> found += Found.THUMBNAIL
                else -> found += Found.OTHER
            }
            0xE1 -> when {
                has(EXIF) -> {} // read by the caller
                has(XMP) || has(XMP_EXT) -> { found += Found.XMP_COMMENTS; found += Xmp.flags(b, payload, end) }
                else -> found += Found.OTHER
            }
            0xE2 -> when {
                has(ICC) -> { found += Found.ICC_KEPT; return Piece(payload - 4, end) }
                has(MPF) -> found += Found.MOTION_VIDEO
                else -> found += Found.OTHER
            }
            0xEB -> found += if (has(JP) && Bytes.contains(b, payload, minOf(end, payload + 32), JUMB)) Found.C2PA else Found.OTHER
            0xED -> if (has(PHOTOSHOP)) {
                found += Found.XMP_COMMENTS
                if (Bytes.contains(b, payload, end, IPTC_BYLINE) || Bytes.contains(b, payload, end, IPTC_COPYRIGHT)) found += Found.AUTHOR_RIGHTS
            } else {
                found += Found.OTHER
            }
            0xEE -> if (has(ADOBE) && end - payload >= ADOBE_PAYLOAD) {
                // Only the fixed 12 bytes: version, flags0, flags1, transform. Extra bytes are dropped.
                val seg = ByteArray(4 + ADOBE_PAYLOAD)
                seg[0] = 0xFF.toByte(); seg[1] = 0xEE.toByte(); seg[2] = 0; seg[3] = (ADOBE_PAYLOAD + 2).toByte()
                b.copyInto(seg, 4, payload, payload + ADOBE_PAYLOAD)
                return Piece(0, 0, seg)
            } else {
                found += Found.OTHER
            }
            else -> found += Found.OTHER
        }
        return null
    }

    /**
     * Structural whitelist check of a cleaned file, independent of what [inspect] knows how to
     * report: SOI; then only the canonical Orientation APP1, APP2 ICC_PROFILE, the 12-byte
     * APP14 Adobe, DQT, DHT, DRI, SOFn (not C4, C8, CC), DAC and SOS with its scan data; at
     * least one SOF before the first SOS; and a final EOI with nothing after. No fill bytes,
     * no standalone markers, nothing else: exactly the shapes [clean] writes.
     */
    internal fun isCleanOutput(b: ByteArray): Boolean {
        val n = b.size
        fun u8(i: Int) = b[i].toInt() and 0xFF
        if (n < 4 || u8(0) != 0xFF || u8(1) != 0xD8) return false
        val canonicalApp1 = (2..8).map { orientationApp1(it) }
        var sawSof = false
        var sawSos = false
        var p = 2
        while (true) {
            if (p + 2 > n || u8(p) != 0xFF) return false
            val m = u8(p + 1)
            if (m == 0xD9) return sawSos && p + 2 == n
            if (p + 4 > n) return false
            val len = (u8(p + 2) shl 8) or u8(p + 3)
            val end = p + 2 + len
            if (len < 2 || end > n) return false
            val ok = when (m) {
                0xDB, 0xC4, 0xDD, 0xCC -> true
                in 0xC0..0xCF -> if (m == 0xC8) false else { sawSof = true; true }
                0xE1 -> p == 2 && canonicalApp1.any { Bytes.startsWith(b, p, end, it) && end - p == it.size }
                0xE2 -> Bytes.startsWith(b, p + 4, end, ICC)
                0xEE -> len == ADOBE_PAYLOAD + 2 && Bytes.startsWith(b, p + 4, end, ADOBE)
                0xDA -> sawSof
                else -> false
            }
            if (!ok) return false
            p = if (m == 0xDA) {
                sawSos = true
                try { scanEnd(b, end) } catch (e: PhotoFailure) { return false }
            } else {
                end
            }
        }
    }

    /** Big-endian APP1 with a TIFF holding one IFD0 entry (0x0112, SHORT, 1, value) and no IFD1. */
    private fun orientationApp1(value: Int): ByteArray {
        val tiff = intArrayOf(
            0x4D, 0x4D, 0x00, 0x2A, 0, 0, 0, 8, // MM\0*, IFD0 at 8
            0, 1, // one entry
            0x01, 0x12, 0, 3, 0, 0, 0, 1, 0, value, 0, 0, // Orientation, SHORT, 1, value
            0, 0, 0, 0, // no next IFD
        )
        val payload = EXIF.size + 1 + tiff.size // "Exif\0" + "\0"
        val out = ByteArray(4 + payload)
        out[0] = 0xFF.toByte(); out[1] = 0xE1.toByte(); out[2] = 0; out[3] = (payload + 2).toByte()
        EXIF.copyInto(out, 4)
        out[4 + EXIF.size] = 0
        for (i in tiff.indices) out[4 + EXIF.size + 1 + i] = tiff[i].toByte()
        return out
    }

    private fun ascii(s: String) = s.toByteArray(Charsets.ISO_8859_1)
    private val EXIF = ascii("Exif\u0000")
    private val JFIF = ascii("JFIF\u0000")
    private val JFXX = ascii("JFXX\u0000")
    private val XMP = ascii("http://ns.adobe.com/xap/1.0/\u0000")
    private val XMP_EXT = ascii("http://ns.adobe.com/xmp/extension/\u0000")
    private val ICC = ascii("ICC_PROFILE\u0000")
    private val MPF = ascii("MPF\u0000")
    private val JP = ascii("JP")
    private val JUMB = ascii("jumb")
    private val PHOTOSHOP = ascii("Photoshop 3.0\u0000")
    private val ADOBE = ascii("Adobe")
    private const val ADOBE_PAYLOAD = 12
    private val IPTC_BYLINE = byteArrayOf(0x1C, 0x02, 0x50) // 2:80 By-line
    private val IPTC_COPYRIGHT = byteArrayOf(0x1C, 0x02, 0x74) // 2:116 Copyright Notice
}

internal class ExifInfo(val found: Set<Found>, val gps: Pair<Double, Double>?, val orientation: Int)

/**
 * Minimal TIFF reader for EXIF: II or MM, IFD0, the ExifIFD (0x8769) and GPS (0x8825)
 * pointers, and IFD1. No other chain is followed, so a looping file cannot loop us. Broken
 * structure is reported as [Found.OTHER] and never throws.
 */
internal object Exif {
    private class Entry(val tag: Int, val type: Int, val count: Long, val valueAt: Int?)

    private class Tiff(val b: ByteArray, val base: Int, val end: Int, val little: Boolean) {
        val length = end - base

        fun ok(off: Long, size: Long) = off >= 0 && size >= 0 && off + size <= length

        fun u16(off: Int): Int {
            val a = b[base + off].toInt() and 0xFF
            val c = b[base + off + 1].toInt() and 0xFF
            return if (little) a or (c shl 8) else (a shl 8) or c
        }

        fun u32(off: Int): Long {
            val x = u16(off).toLong()
            val y = u16(off + 2).toLong()
            return if (little) x or (y shl 16) else (x shl 16) or y
        }

        /** Entries of the IFD at [off] and the next-IFD offset, or null if it does not fit. */
        fun ifd(off: Long): Pair<List<Entry>, Long>? {
            if (!ok(off, 2)) return null
            val o = off.toInt()
            val count = u16(o)
            if (!ok(off + 2, 12L * count)) return null
            val entries = ArrayList<Entry>(count)
            for (k in 0 until count) {
                val e = o + 2 + 12 * k
                val type = u16(e + 2)
                val n = u32(e + 4)
                val size = typeSize(type)?.let { it * n }
                val at = when {
                    size == null -> null
                    size <= 4 -> e + 8
                    else -> u32(e + 8).takeIf { ok(it, size) }?.toInt()
                }
                entries += Entry(u16(e), type, n, at)
            }
            val nextAt = off + 2 + 12L * count
            val next = if (ok(nextAt, 4)) u32(nextAt.toInt()) else 0L
            return entries to next
        }

        fun rational(at: Int): Double? {
            val num = u32(at)
            val den = u32(at + 4)
            return if (den == 0L) null else num.toDouble() / den.toDouble()
        }

        private fun typeSize(type: Int): Long? = when (type) {
            1, 2, 6, 7 -> 1
            3, 8 -> 2
            4, 9, 11, 13 -> 4
            5, 10, 12 -> 8
            else -> null
        }
    }

    // IFD0 tags by category; anything else not structural counts as OTHER.
    private val IFD0_DEVICE = setOf(0x010F, 0x0110)
    private val IFD0_DATE = setOf(0x0131, 0x0132, 0x013C)
    private val IFD0_AUTHOR = setOf(0x013B, 0x8298, 0x9C9D)
    private val IFD0_COMMENTS = setOf(0x010E, 0x02BC, 0x83BB, 0x9C9B, 0x9C9C, 0x9C9E, 0x9C9F)
    private val EXIF_DATE = setOf(0x9003, 0x9004, 0x9010, 0x9011, 0x9012, 0x9290, 0x9291, 0x9292)
    private val EXIF_DEVICE = setOf(0x927C, 0xA430, 0xA431, 0xA432, 0xA433, 0xA434, 0xA435)
    private val EXIF_COMMENTS = setOf(0x9286)
    private const val ORIENTATION = 0x0112
    private const val EXIF_IFD = 0x8769
    private const val GPS_IFD = 0x8825

    /** Reads the TIFF that starts at [from] and ends at [end] in [b]. */
    fun read(b: ByteArray, from: Int, end: Int): ExifInfo {
        val found = HashSet<Found>()
        if (from < 0 || end > b.size || end - from < 8) return ExifInfo(setOf(Found.OTHER), null, 1)
        val little = when {
            b[from] == 'I'.code.toByte() && b[from + 1] == 'I'.code.toByte() -> true
            b[from] == 'M'.code.toByte() && b[from + 1] == 'M'.code.toByte() -> false
            else -> return ExifInfo(setOf(Found.OTHER), null, 1)
        }
        val t = Tiff(b, from, end, little)
        if (t.u16(2) != 0x2A) return ExifInfo(setOf(Found.OTHER), null, 1)
        val (ifd0, next) = t.ifd(t.u32(4)) ?: return ExifInfo(setOf(Found.OTHER), null, 1)

        var orientation = 1
        var gps: Pair<Double, Double>? = null
        for (e in ifd0) {
            when (e.tag) {
                in IFD0_DEVICE -> found += Found.DEVICE
                in IFD0_DATE -> found += Found.DATE_SOFTWARE
                in IFD0_AUTHOR -> found += Found.AUTHOR_RIGHTS
                in IFD0_COMMENTS -> found += Found.XMP_COMMENTS
                ORIENTATION -> if (e.type == 3 && e.count == 1L && e.valueAt != null) {
                    orientation = t.u16(e.valueAt).takeIf { it in 1..8 } ?: 1
                } else {
                    found += Found.OTHER
                }
                EXIF_IFD -> {
                    val sub = pointer(t, e)?.let { t.ifd(it) }
                    if (sub == null) found += Found.OTHER else for (s in sub.first) found += when (s.tag) {
                        in EXIF_DATE -> Found.DATE_SOFTWARE
                        in EXIF_DEVICE -> Found.DEVICE
                        in EXIF_COMMENTS -> Found.XMP_COMMENTS
                        else -> Found.OTHER // exposure data, unique IDs, interop pointer...
                    }
                }
                GPS_IFD -> {
                    val sub = pointer(t, e)?.let { t.ifd(it) }
                    if (sub == null) {
                        found += Found.OTHER
                    } else if (sub.first.any { it.tag != 0x0000 }) {
                        found += Found.GPS
                        gps = coordinates(t, sub.first)
                    }
                }
                else -> found += Found.OTHER
            }
        }
        if (next != 0L) {
            val ifd1 = t.ifd(next)
            found += if (ifd1 != null && ifd1.first.isNotEmpty()) Found.THUMBNAIL else Found.OTHER
        }
        return ExifInfo(found, gps, orientation)
    }

    private fun pointer(t: Tiff, e: Entry): Long? =
        if ((e.type == 4 || e.type == 13) && e.count == 1L && e.valueAt != null) t.u32(e.valueAt) else null

    private fun coordinates(t: Tiff, entries: List<Entry>): Pair<Double, Double>? {
        fun ref(tag: Int): Char? = entries.firstOrNull { it.tag == tag && it.type == 2 && it.count >= 1 }
            ?.valueAt?.let { (t.b[t.base + it].toInt() and 0xFF).toChar() }
        fun dms(tag: Int): Double? {
            val e = entries.firstOrNull { it.tag == tag && it.type == 5 && it.count == 3L } ?: return null
            val at = e.valueAt ?: return null
            val d = t.rational(at) ?: return null
            val m = t.rational(at + 8) ?: return null
            val s = t.rational(at + 16) ?: return null
            return d + m / 60 + s / 3600
        }
        val lat = dms(0x0002) ?: return null
        val lon = dms(0x0004) ?: return null
        val latSign = when (ref(0x0001)) { 'N' -> 1; 'S' -> -1; else -> return null }
        val lonSign = when (ref(0x0003)) { 'E' -> 1; 'W' -> -1; else -> return null }
        if (!lat.isFinite() || !lon.isFinite() || lat > 90 || lon > 180) return null
        return round2(latSign * lat) to round2(lonSign * lon)
    }

    private fun round2(v: Double): Double = Math.round(v * 100) / 100.0
}
