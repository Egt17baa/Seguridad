package app.seguridad.tools.photo

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Test images built in code. Every piece of metadata here is invented: no real person,
 * device or place, except the GPS value the brief asks for (a city centre, 40°25'12"N 3°42'0"W).
 */
internal object TestImages {
    fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }
    fun ascii(s: String) = s.toByteArray(Charsets.ISO_8859_1)

    fun concat(vararg parts: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        for (p in parts) out.write(p)
        return out.toByteArray()
    }

    val SOI = bytes(0xFF, 0xD8)
    val EOI = bytes(0xFF, 0xD9)

    /** One marker segment: FF, marker, 2-byte big-endian length (counting itself), payload. */
    fun seg(marker: Int, payload: ByteArray): ByteArray {
        val len = payload.size + 2
        require(len <= 0xFFFF)
        return concat(bytes(0xFF, marker, len shr 8, len and 0xFF), payload)
    }

    // --- A real image, from the JDK encoder ---------------------------------------------

    /** A real baseline JPEG split into its parts: the tables before SOS (no APP0), the SOS header and the scan data. */
    class Baseline(val tables: List<ByteArray>, val sos: ByteArray, val scan: ByteArray) {
        fun body(): ByteArray = concat(*tables.toTypedArray(), sos, scan)
    }

    /**
     * A real 16x16 baseline JPEG (JFIF, no other metadata), written once by the JDK encoder
     * (ImageIO, a colour gradient). Hard-coded because Android unit tests have no ImageIO.
     */
    private const val BASELINE_HEX =
            "ffd8ffe000104a46494600010200000100010000ffdb004300080606070605080707070909080a0c140d0c0b0b0c1912" +
            "130f141d1a1f1e1d1a1c1c20242e2720222c231c1c2837292c30313434341f27393d38323c2e333432ffdb0043010909" +
            "090c0b0c180d0d1832211c21323232323232323232323232323232323232323232323232323232323232323232323232" +
            "3232323232323232323232323232ffc00011080010001003012200021101031101ffc4001f0000010501010101010100" +
            "000000000000000102030405060708090a0bffc400b5100002010303020403050504040000017d010203000411051221" +
            "31410613516107227114328191a1082342b1c11552d1f02433627282090a161718191a25262728292a3435363738393a" +
            "434445464748494a535455565758595a636465666768696a737475767778797a838485868788898a9293949596979899" +
            "9aa2a3a4a5a6a7a8a9aab2b3b4b5b6b7b8b9bac2c3c4c5c6c7c8c9cad2d3d4d5d6d7d8d9dae1e2e3e4e5e6e7e8e9eaf1" +
            "f2f3f4f5f6f7f8f9faffc4001f0100030101010101010101010000000000000102030405060708090a0bffc400b51100" +
            "020102040403040705040400010277000102031104052131061241510761711322328108144291a1b1c109233352f015" +
            "6272d10a162434e125f11718191a262728292a35363738393a434445464748494a535455565758595a63646566676869" +
            "6a737475767778797a82838485868788898a92939495969798999aa2a3a4a5a6a7a8a9aab2b3b4b5b6b7b8b9bac2c3c4" +
            "c5c6c7c8c9cad2d3d4d5d6d7d8d9dae2e3e4e5e6e7e8e9eaf2f3f4f5f6f7f8f9faffda000c03010002110311003f00f3" +
            "8b0d0ba7c95d358e85d3e4ae96c342e9f2574d61a174f92bc6c5e6be669c3f9deda9ffd9"

    val baseline: Baseline by lazy {
        split(ByteArray(BASELINE_HEX.length / 2) { BASELINE_HEX.substring(2 * it, 2 * it + 2).toInt(16).toByte() })
    }

    /** Trusted splitter for the JDK encoder output only (no fill bytes, ends with EOI). */
    private fun split(jpg: ByteArray): Baseline {
        check(jpg[0] == 0xFF.toByte() && jpg[1] == 0xD8.toByte())
        val tables = mutableListOf<ByteArray>()
        var p = 2
        while (true) {
            val m = jpg[p + 1].toInt() and 0xFF
            val len = ((jpg[p + 2].toInt() and 0xFF) shl 8) or (jpg[p + 3].toInt() and 0xFF)
            val s = jpg.copyOfRange(p, p + 2 + len)
            p += 2 + len
            if (m == 0xDA) {
                check(jpg[jpg.size - 2] == 0xFF.toByte() && jpg[jpg.size - 1] == 0xD9.toByte())
                return Baseline(tables, s, jpg.copyOfRange(p, jpg.size - 2))
            }
            if (m != 0xE0) tables += s
        }
    }

    /** A plain image with no metadata at all. */
    fun plainJpeg(): ByteArray = concat(SOI, baseline.body(), EOI)

    // --- Metadata segments ---------------------------------------------------------------

    fun jfif(thumbW: Int = 0, thumbH: Int = 0): ByteArray =
        seg(0xE0, concat(ascii("JFIF"), bytes(0, 1, 1, 0, 0, 1, 0, 1, thumbW, thumbH), ByteArray(thumbW * thumbH * 3)))

    fun exifSegment(tiff: ByteArray): ByteArray = seg(0xE1, concat(ascii("Exif"), bytes(0, 0), tiff))

    fun xmp(body: String): ByteArray =
        seg(0xE1, concat(ascii("http://ns.adobe.com/xap/1.0/"), bytes(0), ascii(body)))

    fun xmpPacket(inner: String) =
        "<x:xmpmeta xmlns:x='adobe:ns:meta/'><rdf:RDF xmlns:rdf='http://www.w3.org/1999/02/22-rdf-syntax-ns#'>" +
            "<rdf:Description $inner/></rdf:RDF></x:xmpmeta>"

    fun icc(): ByteArray = seg(0xE2, concat(ascii("ICC_PROFILE"), bytes(0, 1, 1), ascii("perfil de color inventado "), ByteArray(100) { it.toByte() }))

    fun mpf(): ByteArray = seg(0xE2, concat(ascii("MPF"), bytes(0), ascii("MM"), bytes(0, 0x2A, 0, 0, 0, 8), ByteArray(40)))

    /** APP13 Photoshop 3.0 with one 8BIM IPTC block holding a By-line (2:80). */
    fun iptcByline(name: String): ByteArray {
        val iptc = concat(bytes(0x1C, 0x02, 0x50, 0, name.length), ascii(name))
        val len = iptc.size
        return seg(0xED, concat(ascii("Photoshop 3.0"), bytes(0), ascii("8BIM"), bytes(0x04, 0x04, 0, 0, 0, 0, len shr 8, len and 0xFF), iptc))
    }

    /** APP14 Adobe, the canonical 12-byte payload (transform 1, YCbCr). */
    fun adobe(): ByteArray = seg(0xEE, concat(ascii("Adobe"), bytes(0, 100, 0, 0, 0, 0, 1)))

    fun com(text: String): ByteArray = seg(0xFE, ascii(text))

    /** APP11 JUMBF in the C2PA layout: "JP", En, Z, then a jumb superbox. */
    fun c2pa(): ByteArray = seg(0xEB, concat(ascii("JP"), bytes(0, 1, 0, 0, 0, 1, 0, 0, 0, 0x18), ascii("jumb"), bytes(0, 0, 0, 0x10), ascii("jumdc2pa"), ByteArray(4)))

    /** The output of an Orientation-only EXIF, written exactly as the brief describes. */
    fun orientationApp1(value: Int): ByteArray = seg(
        0xE1,
        concat(ascii("Exif"), bytes(0, 0), ascii("MM"), bytes(0, 0x2A, 0, 0, 0, 8, 0, 1, 0x01, 0x12, 0, 3, 0, 0, 0, 1, 0, value, 0, 0, 0, 0, 0, 0)),
    )

    // --- A small TIFF writer for EXIF ------------------------------------------------------

    class Entry(val tag: Int, val type: Int, val count: Int, val value: ByteArray)

    class Tiff(private val order: ByteOrder = ByteOrder.BIG_ENDIAN) {
        private fun buf(n: Int) = ByteBuffer.allocate(n).order(order)

        fun ascii(tag: Int, s: String) = Entry(tag, 2, s.length + 1, TestImages.concat(TestImages.ascii(s), bytes(0)))
        fun short(tag: Int, v: Int) = Entry(tag, 3, 1, buf(2).putShort(v.toShort()).array())
        fun long(tag: Int, v: Long) = Entry(tag, 4, 1, buf(4).putInt(v.toInt()).array())
        fun rationals(tag: Int, vararg r: Pair<Long, Long>): Entry {
            val b = buf(8 * r.size)
            for ((n, d) in r) { b.putInt(n.toInt()); b.putInt(d.toInt()) }
            return Entry(tag, 5, r.size, b.array())
        }
        fun undefined(tag: Int, v: ByteArray) = Entry(tag, 7, v.size, v)

        /** IFD0 (plus the ExifIFD and GPS pointers when given), ExifIFD, GPS IFD and IFD1, in that order. */
        fun build(ifd0: List<Entry>, exif: List<Entry>? = null, gps: List<Entry>? = null, ifd1: List<Entry>? = null): ByteArray {
            val zeroPtr = { tag: Int -> Entry(tag, 4, 1, ByteArray(4)) }
            val first = (ifd0 + listOfNotNull(exif?.let { zeroPtr(0x8769) }, gps?.let { zeroPtr(0x8825) })).sortedBy { it.tag }
            val blocks = listOf(first, exif, gps, ifd1)
            fun size(l: List<Entry>?): Int = if (l == null) 0 else 2 + 12 * l.size + 4 + l.sumOf { if (it.value.size > 4) (it.value.size + 1) and 1.inv() else 0 }
            val offsets = IntArray(4)
            var o = 8
            for (i in 0..3) { offsets[i] = o; o += size(blocks[i]) }
            val out = buf(o)
            out.put(if (order == ByteOrder.BIG_ENDIAN) TestImages.ascii("MM") else TestImages.ascii("II"))
            out.putShort(0x2A); out.putInt(8)
            for (i in 0..3) {
                val l = blocks[i] ?: continue
                val start = offsets[i]
                out.position(start)
                out.putShort(l.size.toShort())
                var data = start + 2 + 12 * l.size + 4
                for (e in l) {
                    out.putShort(e.tag.toShort()); out.putShort(e.type.toShort()); out.putInt(e.count)
                    val v = when (e.tag) {
                        0x8769 -> if (i == 0 && exif != null) buf(4).putInt(offsets[1]).array() else e.value
                        0x8825 -> if (i == 0 && gps != null) buf(4).putInt(offsets[2]).array() else e.value
                        else -> e.value
                    }
                    if (v.size <= 4) {
                        out.put(v.copyOf(4))
                    } else {
                        out.putInt(data)
                        val here = out.position()
                        out.position(data); out.put(v); out.position(here)
                        data += (v.size + 1) and 1.inv()
                    }
                }
                out.putInt(if (i == 0 && ifd1 != null) offsets[3] else 0)
            }
            return out.array()
        }
    }

    /** EXIF with GPS, Make/Model, DateTime, DateTimeOriginal and a thumbnail IFD1. */
    fun fullExif(orientation: Int? = null, order: ByteOrder = ByteOrder.BIG_ENDIAN): ByteArray {
        val t = Tiff(order)
        val ifd0 = listOfNotNull(
            t.ascii(0x010F, "Fabricante Inventado"),
            t.ascii(0x0110, "Modelo X1"),
            orientation?.let { t.short(0x0112, it) },
            t.ascii(0x0132, "2001:02:03 04:05:06"),
        )
        val exif = listOf(t.ascii(0x9003, "2001:02:03 04:05:06"), t.ascii(0xA431, "SN-0000-PRUEBA"))
        val gps = listOf(
            t.undefined(0x0000, bytes(2, 3, 0, 0)),
            t.ascii(0x0001, "N"),
            t.rationals(0x0002, 40L to 1L, 25L to 1L, 12L to 1L),
            t.ascii(0x0003, "W"),
            t.rationals(0x0004, 3L to 1L, 42L to 1L, 0L to 1L),
        )
        val ifd1 = listOf(t.short(0x0103, 6), t.long(0x0202, 0))
        return exifSegment(t.build(ifd0, exif, gps, ifd1))
    }

    /** EXIF with only an Orientation tag in IFD0. */
    fun orientationOnlyExif(orientation: Int, order: ByteOrder = ByteOrder.BIG_ENDIAN): ByteArray {
        val t = Tiff(order)
        return exifSegment(t.build(listOf(t.short(0x0112, orientation))))
    }

    /** The image with everything the brief lists, and junk after EOI. */
    fun everything(): ByteArray = concat(
        SOI, jfif(), fullExif(), xmp(xmpPacket("xmp:CreatorTool='Editor Inventado'")), icc(), mpf(),
        iptcByline("Autora Inventada"), adobe(), com("comentario inventado"),
        baseline.body(), EOI, ascii("datos tras el final"),
    )
}
