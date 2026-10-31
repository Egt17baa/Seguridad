package app.seguridad.tools.photo

import java.io.ByteArrayOutputStream

/**
 * WebP cleaning by rewriting the RIFF container with a closed list of kept chunks (design §1).
 *
 * Kept: VP8X (EXIF and XMP flag bits and the reserved bits and bytes cleared), ICCP, ALPH, VP8,
 * VP8L, ANIM and ANMF. Inside an ANMF only ALPH, VP8 and VP8L stay. Everything else (EXIF, XMP,
 * C2PA, unknown chunks), the padding bytes and anything after the RIFF is dropped and reported.
 * The RIFF size is recomputed and every chunk is written with even padding of zero bytes.
 *
 * Input is hostile: sizes are checked in Long against the array, every chunk advances by at
 * least 8 bytes, and the only exception that leaves this object is [PhotoFailure].
 */
object Webp {
    /** [check]: the caller's cancellation checkpoint, called once per chunk and in the XMP reader (HERR-1). */
    fun inspect(bytes: ByteArray, check: () -> Unit = {}): PhotoReport = parse(bytes, check).report

    fun clean(bytes: ByteArray, check: () -> Unit = {}): ByteArray = parse(bytes, check).out

    private class Parsed(val report: PhotoReport, val out: ByteArray)

    /** A chunk header and payload that fit inside [end]; [next] is after the even padding. */
    private class Chunk(val id: String, val dataAt: Int, val dataEnd: Int, val next: Int) {
        val size get() = dataEnd - dataAt
    }

    private fun damaged(): Nothing = throw PhotoFailure(PHOTO_DAMAGED)

    private fun chunkAt(b: ByteArray, p: Int, end: Int): Chunk? {
        if (end - p.toLong() < 8) return null
        val size = Bytes.u32le(b, p + 4)
        if (size > end - p - 8L) return null
        val dataAt = p + 8
        val dataEnd = dataAt + size.toInt()
        val next = dataEnd.toLong() + (size and 1)
        if (next > end) return null
        return Chunk(String(b, p, 4, Charsets.ISO_8859_1), dataAt, dataEnd, next.toInt())
    }

    /** True if the padding byte of an odd-sized chunk is not zero (it can hide a byte). */
    private fun dirtyPad(b: ByteArray, c: Chunk) = c.next > c.dataEnd && b[c.dataEnd] != 0.toByte()

    /** The end of the RIFF content, or null if the header lies about the size. */
    private fun riffEnd(b: ByteArray): Int? {
        if (b.size < 12 || !Bytes.startsWith(b, 0, 4, RIFF) || !Bytes.startsWith(b, 8, 12, WEBP)) return null
        val size = Bytes.u32le(b, 4)
        if (size < 4 || size > b.size - 8L || size % 2L != 0L) return null
        return (8 + size).toInt()
    }

    /**
     * The order the kept chunks must come in, shared by the cleaner and the structural check:
     * ICCP, ALPH and ANIM before any image data, one VP8 or VP8L or else frames after ANIM.
     */
    private class Order {
        var iccp = false; var alph = false; var anim = false; var bitstream = false; var frames = 0
        private val noImage get() = !bitstream && frames == 0

        fun accept(id: String): Boolean = when (id) {
            "ICCP" -> (!iccp && !alph && !anim && noImage).also { if (it) iccp = true }
            "ANIM" -> (!anim && !alph && noImage).also { if (it) anim = true }
            // ALPH belongs to a still VP8 image: not in an animation, and never before VP8L.
            "ALPH" -> (!alph && !anim && noImage).also { if (it) alph = true }
            "VP8 " -> noImage.also { if (it) bitstream = true }
            "VP8L" -> (noImage && !alph).also { if (it) bitstream = true }
            "ANMF" -> (!bitstream && anim).also { if (it) frames++ }
            else -> false
        }

        /** ALPH needs its VP8 (frames cannot follow an ALPH, so this holds for animations too). */
        fun complete() = bitstream || frames > 0

        val animated get() = anim || frames > 0

        /** The VP8X flags that follow from the kept chunks; alpha is kept from [input] unless ALPH is there. */
        fun flags(input: Int): Int {
            var f = input and ALPHA_BIT
            if (alph) f = f or ALPHA_BIT
            if (iccp) f = f or ICC_BIT
            if (animated) f = f or ANIM_BIT
            return f
        }
    }

    private fun parse(b: ByteArray, check: () -> Unit): Parsed {
        val end = riffEnd(b) ?: damaged()
        val found = HashSet<Found>()
        if (end < b.size) found += Found.OTHER // bytes after the RIFF
        val body = ByteArrayOutputStream()
        val order = Order()
        var exif: ExifInfo? = null
        var extended = false
        var vp8xPayload: ByteArray? = null
        var p = 12
        var index = 0
        while (p < end) {
            check()
            val c = chunkAt(b, p, end) ?: damaged()
            if (dirtyPad(b, c)) found += Found.OTHER
            when (c.id) {
                "VP8X" -> {
                    if (index != 0 || c.size != VP8X_SIZE) damaged()
                    extended = true
                    vp8xPayload = vp8x(b, c, found)
                }
                "ICCP", "ALPH", "VP8 ", "VP8L", "ANIM", "ANMF" -> {
                    if (!extended && c.id != "VP8 " && c.id != "VP8L") damaged()
                    if (!order.accept(c.id)) damaged()
                    if (c.id == "ANIM" && c.size != ANIM_SIZE) damaged()
                    if (c.id == "ICCP") found += Found.ICC_KEPT
                    write(body, c.id, if (c.id == "ANMF") anmf(b, c, found, check) else b.copyOfRange(c.dataAt, c.dataEnd))
                }
                "EXIF" -> {
                    // Some writers prefix the TIFF with the JPEG-style «Exif\0\0».
                    val skip = if (Bytes.startsWith(b, c.dataAt, c.dataEnd, EXIF_PREFIX)) EXIF_PREFIX.size else 0
                    val info = Exif.read(b, c.dataAt + skip, c.dataEnd)
                    found += info.found
                    if (info.found.isEmpty()) found += Found.OTHER
                    // Several EXIF chunks: gps and orientation come from the first, `found` merges all.
                    if (exif == null) exif = info
                }
                "XMP " -> { found += Found.XMP_COMMENTS; found += Xmp.flags(b, c.dataAt, c.dataEnd, check) }
                "C2PA" -> found += Found.C2PA
                else -> found += Found.OTHER
            }
            p = c.next
            index++
        }
        if (!order.complete()) damaged()
        // The VP8X flags describe exactly the chunks that were kept.
        val head = ByteArrayOutputStream()
        if (vp8xPayload != null) {
            vp8xPayload[0] = order.flags(vp8xPayload[0].toInt() and 0xFF).toByte()
            write(head, "VP8X", vp8xPayload)
        }
        val total = 4L + head.size() + body.size()
        if (total > Int.MAX_VALUE - 16) damaged()
        val out = ByteArray(12 + head.size() + body.size())
        RIFF.copyInto(out)
        putLe32(out, 4, total)
        WEBP.copyInto(out, 8)
        head.toByteArray().copyInto(out, 12)
        body.toByteArray().copyInto(out, 12 + head.size())
        return Parsed(PhotoReport(PhotoFormat.WEBP, found.toSet(), exif?.gps, exif?.orientation ?: 1), out)
    }

    /**
     * The VP8X payload with what can never stay cleared: the EXIF and XMP bits, the reserved bits and the three reserved
     * bytes (a set one is reported as OTHER); the canvas size stays. The ICC, alpha and animation bits are not decided
     * here: [parse] sets them again from the chunks it kept ([Order.flags]), and [isCleanOutput] checks they match.
     */
    private fun vp8x(b: ByteArray, c: Chunk, found: MutableSet<Found>): ByteArray {
        val out = b.copyOfRange(c.dataAt, c.dataEnd)
        val flags = out[0].toInt() and 0xFF
        if (flags and RESERVED_BITS != 0 || out[1] != 0.toByte() || out[2] != 0.toByte() || out[3] != 0.toByte()) {
            found += Found.OTHER
        }
        out[0] = (flags and VP8X_KEPT).toByte()
        out[1] = 0; out[2] = 0; out[3] = 0
        return out
    }

    /** An ANMF payload: the 16-byte frame header and only the ALPH, VP8 and VP8L sub-chunks. */
    private fun anmf(b: ByteArray, c: Chunk, found: MutableSet<Found>, check: () -> Unit): ByteArray {
        if (c.size < ANMF_HEADER) damaged()
        val out = ByteArrayOutputStream()
        val header = b.copyOfRange(c.dataAt, c.dataAt + ANMF_HEADER)
        // Last byte: reserved bits 2..7 cleared, blending and disposal kept.
        if (header[ANMF_HEADER - 1].toInt() and ANMF_RESERVED != 0) found += Found.OTHER
        header[ANMF_HEADER - 1] = (header[ANMF_HEADER - 1].toInt() and ANMF_RESERVED.inv()).toByte()
        out.write(header)
        var alph = false
        var bitstream = false
        var p = c.dataAt + ANMF_HEADER
        while (p < c.dataEnd) {
            check()
            val s = chunkAt(b, p, c.dataEnd) ?: damaged()
            if (dirtyPad(b, s)) found += Found.OTHER
            when (s.id) {
                "ALPH" -> if (alph || bitstream) damaged() else { alph = true; write(out, s.id, b.copyOfRange(s.dataAt, s.dataEnd)) }
                "VP8 ", "VP8L" -> if (bitstream) damaged() else { bitstream = true; write(out, s.id, b.copyOfRange(s.dataAt, s.dataEnd)) }
                else -> found += Found.OTHER
            }
            p = s.next
        }
        if (!bitstream) damaged()
        return out.toByteArray()
    }

    private fun write(out: ByteArrayOutputStream, id: String, data: ByteArray) {
        out.write(Bytes.ascii(id))
        val size = ByteArray(4)
        putLe32(size, 0, data.size.toLong())
        out.write(size)
        out.write(data)
        if (data.size % 2 == 1) out.write(0)
    }

    private fun putLe32(b: ByteArray, at: Int, v: Long) {
        for (i in 0 until 4) b[at + i] = (v shr (8 * i)).toByte()
    }

    /**
     * Structural whitelist check of a cleaned file: RIFF size exact and even, then either one
     * VP8 or VP8L, or VP8X first (10 bytes, EXIF, XMP and reserved bits clear, reserved bytes
     * zero) followed by only ICCP, ALPH, ANIM, VP8, VP8L and ANMF in the order [clean] writes
     * them. Padding is zero, and a frame holds nothing but ALPH, VP8 or VP8L.
     */
    internal fun isCleanOutput(b: ByteArray, check: () -> Unit = {}): Boolean {
        val end = riffEnd(b) ?: return false
        if (end != b.size) return false
        val order = Order()
        var extended = false
        var vp8xFlags = 0
        var p = 12
        while (p < end) {
            check()
            val c = chunkAt(b, p, end) ?: return false
            if (dirtyPad(b, c)) return false
            when (c.id) {
                "VP8X" -> {
                    if (p != 12 || c.size != VP8X_SIZE) return false
                    val found = HashSet<Found>()
                    if (!vp8x(b, c, found).contentEquals(b.copyOfRange(c.dataAt, c.dataEnd)) || found.isNotEmpty()) return false
                    extended = true
                    vp8xFlags = b[c.dataAt].toInt() and 0xFF
                }
                "ICCP", "ALPH", "VP8 ", "VP8L", "ANIM", "ANMF" -> {
                    if (!extended && c.id != "VP8 " && c.id != "VP8L") return false
                    if (!order.accept(c.id)) return false
                    if (c.id == "ANIM" && c.size != ANIM_SIZE) return false
                    if (c.id == "ANMF") {
                        val found = HashSet<Found>()
                        val again = try { anmf(b, c, found, check) } catch (e: PhotoFailure) { return false }
                        if (found.isNotEmpty() || !again.contentEquals(b.copyOfRange(c.dataAt, c.dataEnd))) return false
                    }
                }
                else -> return false
            }
            p = c.next
        }
        // A lone VP8X is not a file; a simple file is one VP8 or VP8L, which [Order] already limits to one.
        // The ICC and animation bits match the kept chunks, and so does alpha when ALPH is there; without ALPH an alpha bit
        // is the input's, kept because a VP8L bitstream or a frame can carry its own alpha ([Order.flags]).
        if (extended && vp8xFlags != order.flags(vp8xFlags)) return false
        return order.complete()
    }

    private val RIFF = Bytes.ascii("RIFF")
    private val WEBP = Bytes.ascii("WEBP")
    private val EXIF_PREFIX = Bytes.ascii("Exif\u0000\u0000")
    private const val VP8X_SIZE = 10
    private const val ANMF_HEADER = 16
    private const val ANIM_SIZE = 6
    private const val ANMF_RESERVED = 0xFC
    private const val ICC_BIT = 0x20
    private const val ALPHA_BIT = 0x10
    private const val ANIM_BIT = 0x02
    // Flags: ICC 0x20, alpha 0x10, EXIF 0x08, XMP 0x04, animation 0x02. Bits 0xC1 are reserved.
    private const val RESERVED_BITS = 0xC1
    private const val VP8X_KEPT = 0x32
}
