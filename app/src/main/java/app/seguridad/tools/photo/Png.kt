package app.seguridad.tools.photo

import java.util.zip.CRC32

/**
 * PNG cleaning by rewriting the container with a closed list of kept chunks (design §1).
 *
 * Kept, byte for byte with their CRC: IHDR, PLTE, IDAT, IEND, tRNS, gAMA, cHRM, sRGB, iCCP and
 * sBIT. Everything else is dropped and reported (text chunks by keyword, tIME, eXIf, the C2PA
 * `caBX`, unknown chunks as OTHER), and so is everything after IEND. Compressed text is never
 * inflated, so a decompression bomb has nothing to bite.
 *
 * Input is hostile: lengths are checked in Long against the array, every chunk advances by at
 * least 12 bytes, every chunk has its CRC checked (kept or not), and the only exception that
 * leaves this object is [PhotoFailure].
 */
object Png {
    /** [check]: the caller's cancellation checkpoint, called once per chunk and in the XMP reader (HERR-1). */
    fun inspect(bytes: ByteArray, check: () -> Unit = {}): PhotoReport = parse(bytes, check).report

    fun clean(bytes: ByteArray, check: () -> Unit = {}): ByteArray {
        val spans = parse(bytes, check).spans
        var size = SIGNATURE.size.toLong()
        for (s in spans) size += s.end - s.start
        if (size > Int.MAX_VALUE - 8) damaged()
        val out = ByteArray(size.toInt())
        SIGNATURE.copyInto(out)
        var o = SIGNATURE.size
        for (s in spans) { bytes.copyInto(out, o, s.start, s.end); o += s.end - s.start }
        return out
    }

    private class Span(val start: Int, val end: Int)
    private class Parsed(val report: PhotoReport, val spans: List<Span>)

    /** A chunk whose bounds, type letters and CRC have been checked. */
    private class Chunk(val type: String, val dataAt: Int, val dataEnd: Int, val next: Int)

    private fun damaged(): Nothing = throw PhotoFailure(PHOTO_DAMAGED)

    /** The chunk at [p], or null if it does not fit, its type is not four letters or its CRC is wrong. */
    private fun chunkAt(b: ByteArray, p: Int): Chunk? {
        val n = b.size
        if (n - p.toLong() < 12) return null
        val len = Bytes.u32be(b, p)
        if (len > 0x7FFFFFFFL || len > n - p - 12L) return null
        for (i in 4 until 8) {
            val c = b[p + i].toInt() and 0xFF
            if (c !in 'A'.code..'Z'.code && c !in 'a'.code..'z'.code) return null
        }
        val dataAt = p + 8
        val dataEnd = dataAt + len.toInt()
        val crc = CRC32().apply { update(b, p + 4, 4 + len.toInt()) }.value
        if (crc != Bytes.u32be(b, dataEnd)) return null
        return Chunk(String(b, p + 4, 4, Charsets.ISO_8859_1), dataAt, dataEnd, dataEnd + 4)
    }

    /**
     * The size table of the kept chunks, used by [parse] and [isCleanOutput]: a kept chunk of
     * the wrong size could carry arbitrary bytes, so it is damage (not dropped).
     */
    private fun sizeOk(c: Chunk): Boolean {
        val n = c.dataEnd - c.dataAt
        return when (c.type) {
            "gAMA" -> n == 4
            "cHRM" -> n == 32
            "sRGB" -> n == 1
            "sBIT" -> n in 1..4
            "PLTE" -> n in 3..768 && n % 3 == 0
            "tRNS" -> n in 1..256
            else -> true // iCCP is a compressed profile of any size
        }
    }

    private fun ihdrOk(b: ByteArray, c: Chunk): Boolean {
        if (c.type != "IHDR" || c.dataEnd - c.dataAt != 13) return false
        val w = Bytes.u32be(b, c.dataAt)
        val h = Bytes.u32be(b, c.dataAt + 4)
        return w in 1..0x7FFFFFFFL && h in 1..0x7FFFFFFFL
    }

    private fun parse(b: ByteArray, check: () -> Unit): Parsed {
        if (!Bytes.startsWith(b, 0, b.size, SIGNATURE)) damaged()
        val found = HashSet<Found>()
        val spans = ArrayList<Span>()
        val seen = HashSet<String>()
        var exif: ExifInfo? = null
        var sawIdat = false
        var p = SIGNATURE.size
        var first = true
        while (true) {
            check()
            val c = chunkAt(b, p) ?: damaged()
            if (first != (c.type == "IHDR")) damaged() // IHDR first, and only there
            if (first && !ihdrOk(b, c)) damaged()
            first = false
            val keep = when (c.type) {
                "IHDR" -> true
                "IDAT" -> { sawIdat = true; true }
                "IEND" -> {
                    if (!sawIdat || c.dataEnd != c.dataAt) damaged()
                    spans += Span(p, c.next)
                    p = c.next
                    break
                }
                in KEPT_ONCE -> {
                    if (sawIdat || !seen.add(c.type) || !sizeOk(c)) damaged()
                    if (c.type == "iCCP") found += Found.ICC_KEPT
                    true
                }
                else -> false
            }
            if (keep) {
                spans += Span(p, c.next)
            } else {
                // Several eXIf chunks: gps and orientation come from the first, `found` merges all.
                val info = report(b, c, found, check)
                if (exif == null) exif = info
            }
            p = c.next
        }
        if (p < b.size) found += Found.OTHER // anything after IEND
        return Parsed(PhotoReport(PhotoFormat.PNG, found.toSet(), exif?.gps, exif?.orientation ?: 1), spans)
    }

    /** Reports a dropped chunk. Returns the EXIF reading for an eXIf chunk. */
    private fun report(b: ByteArray, c: Chunk, found: MutableSet<Found>, check: () -> Unit): ExifInfo? {
        when (c.type) {
            "tEXt", "zTXt", "iTXt" -> text(b, c, found, check)
            "tIME" -> found += Found.DATE_SOFTWARE
            "caBX" -> found += Found.C2PA
            "eXIf" -> {
                val info = Exif.read(b, c.dataAt, c.dataEnd)
                found += info.found
                // An EXIF holding only an orientation is still something that was dropped.
                if (info.found.isEmpty()) found += Found.OTHER
                return info
            }
            else -> found += Found.OTHER // pHYs, bKGD, hIST, sPLT, acTL, fcTL, fdAT, private chunks...
        }
        return null
    }

    /** Text chunks are reported from their keyword only; uncompressed XMP is also read. */
    private fun text(b: ByteArray, c: Chunk, found: MutableSet<Found>, check: () -> Unit) {
        var k = c.dataAt
        val limit = minOf(c.dataEnd, c.dataAt + 80)
        while (k < limit && b[k] != 0.toByte()) k++
        if (k >= limit || k == c.dataAt) { found += Found.OTHER; return }
        val keyword = String(b, c.dataAt, k - c.dataAt, Charsets.ISO_8859_1)
        when {
            keyword == "Author" || keyword == "Copyright" -> found += Found.AUTHOR_RIGHTS
            keyword == "Creation Time" || keyword == "Software" -> found += Found.DATE_SOFTWARE
            keyword == "Source" -> found += Found.DEVICE
            keyword == "XML:com.adobe.xmp" -> {
                found += Found.XMP_COMMENTS
                xmpStart(b, c, k + 1)?.let { found += Xmp.flags(b, it, c.dataEnd, check) }
            }
            keyword == "Comment" || keyword == "Description" || keyword == "Title" -> found += Found.XMP_COMMENTS
            else -> found += Found.OTHER
        }
    }

    /** Where the readable text starts after the keyword, or null if it is compressed (never inflated). */
    private fun xmpStart(b: ByteArray, c: Chunk, afterKeyword: Int): Int? = when (c.type) {
        "tEXt" -> afterKeyword
        "zTXt" -> null
        else -> {
            // iTXt: compression flag, method, language tag \0, translated keyword \0, text.
            if (afterKeyword + 2 > c.dataEnd || b[afterKeyword] != 0.toByte()) {
                null
            } else {
                var i = afterKeyword + 2
                var zeros = 0
                while (i < c.dataEnd && zeros < 2) { if (b[i] == 0.toByte()) zeros++; i++ }
                if (zeros == 2) i else null
            }
        }
    }

    /**
     * Structural whitelist check of a cleaned file: signature, IHDR first, then only the listed
     * chunks (each once, before the IDAT run), valid CRCs, at least one IDAT, and IEND last with
     * nothing after. Exactly the shapes [clean] writes.
     */
    internal fun isCleanOutput(b: ByteArray, check: () -> Unit = {}): Boolean {
        if (!Bytes.startsWith(b, 0, b.size, SIGNATURE)) return false
        val seen = HashSet<String>()
        var sawIdat = false
        var p = SIGNATURE.size
        var first = true
        while (true) {
            check()
            val c = chunkAt(b, p) ?: return false
            if (first != (c.type == "IHDR")) return false
            if (first && !ihdrOk(b, c)) return false
            first = false
            when (c.type) {
                "IHDR" -> {}
                "IDAT" -> sawIdat = true
                "IEND" -> return sawIdat && c.dataEnd == c.dataAt && c.next == b.size
                in KEPT_ONCE -> if (sawIdat || !seen.add(c.type) || !sizeOk(c)) return false
                else -> return false
            }
            p = c.next
        }
    }

    private val SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

    /** Kept chunks that come once, before the first IDAT. */
    private val KEPT_ONCE = setOf("PLTE", "tRNS", "gAMA", "cHRM", "sRGB", "iCCP", "sBIT")
}
