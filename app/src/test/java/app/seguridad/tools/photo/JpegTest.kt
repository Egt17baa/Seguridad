package app.seguridad.tools.photo

import app.seguridad.tools.photo.Found.AUTHOR_RIGHTS
import app.seguridad.tools.photo.Found.C2PA
import app.seguridad.tools.photo.Found.DATE_SOFTWARE
import app.seguridad.tools.photo.Found.DEVICE
import app.seguridad.tools.photo.Found.GPS
import app.seguridad.tools.photo.Found.ICC_KEPT
import app.seguridad.tools.photo.Found.MOTION_VIDEO
import app.seguridad.tools.photo.Found.OTHER
import app.seguridad.tools.photo.Found.THUMBNAIL
import app.seguridad.tools.photo.Found.XMP_COMMENTS
import app.seguridad.tools.photo.TestImages.EOI
import app.seguridad.tools.photo.TestImages.SOI
import app.seguridad.tools.photo.TestImages.adobe
import app.seguridad.tools.photo.TestImages.ascii
import app.seguridad.tools.photo.TestImages.baseline
import app.seguridad.tools.photo.TestImages.bytes
import app.seguridad.tools.photo.TestImages.concat
import app.seguridad.tools.photo.TestImages.exifSegment
import app.seguridad.tools.photo.TestImages.icc
import app.seguridad.tools.photo.TestImages.seg
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.ByteOrder
import java.util.Random

/** JPEG: closed list of kept segments, the report, and hostile input. */
class JpegTest {
    private fun damaged(bytes: ByteArray) {
        val e = assertThrows(PhotoFailure::class.java) { Jpeg.inspect(bytes) }
        assertEquals(PHOTO_DAMAGED, e.message)
        val e2 = assertThrows(PhotoFailure::class.java) { Jpeg.clean(bytes) }
        assertEquals(PHOTO_DAMAGED, e2.message)
    }

    private fun indexOf(hay: ByteArray, needle: ByteArray): Int {
        outer@ for (i in 0..hay.size - needle.size) {
            for (j in needle.indices) if (hay[i + j] != needle[j]) continue@outer
            return i
        }
        return -1
    }

    @Test fun keepsOnlyTheListedSegments() {
        val out = Jpeg.clean(TestImages.everything())
        val expected = concat(SOI, icc(), adobe(), baseline.body(), EOI)
        assertArrayEquals(expected, out)
    }

    @Test fun reportNamesWhatIsThere() {
        val r = Jpeg.inspect(TestImages.everything())
        assertEquals(PhotoFormat.JPEG, r.format)
        assertEquals(setOf(GPS, DEVICE, DATE_SOFTWARE, THUMBNAIL, XMP_COMMENTS, ICC_KEPT, MOTION_VIDEO, AUTHOR_RIGHTS), r.found)
        assertEquals(40.42 to -3.7, r.gps)
        assertEquals(1, r.orientation)
    }

    @Test fun gpsIsReadInLittleEndianToo() {
        val r = Jpeg.inspect(concat(SOI, TestImages.fullExif(order = ByteOrder.LITTLE_ENDIAN), baseline.body(), EOI))
        assertEquals(40.42 to -3.7, r.gps)
        assertTrue(r.found.containsAll(setOf(GPS, DEVICE, DATE_SOFTWARE, THUMBNAIL)))
    }

    @Test fun mpfSecondImageAfterEoiIsDropped() {
        val first = concat(SOI, TestImages.mpf(), baseline.body(), EOI)
        val input = concat(first, TestImages.plainJpeg())
        val out = Jpeg.clean(input)
        assertArrayEquals(TestImages.plainJpeg(), out)
        assertTrue(Jpeg.inspect(out).found.isEmpty())
        assertTrue(MOTION_VIDEO in Jpeg.inspect(input).found)
        // A second image alone after EOI, without MPF, is still reported.
        assertTrue(MOTION_VIDEO in Jpeg.inspect(concat(TestImages.plainJpeg(), TestImages.plainJpeg())).found)
    }

    @Test fun paddingAfterEoiIsNotAVideo() {
        val r = Jpeg.inspect(concat(TestImages.plainJpeg(), ByteArray(64)))
        assertTrue(r.found.isEmpty())
        assertArrayEquals(TestImages.plainJpeg(), Jpeg.clean(concat(TestImages.plainJpeg(), ByteArray(64))))
    }

    @Test fun orientationIsKeptAlone() {
        for (order in listOf(ByteOrder.BIG_ENDIAN, ByteOrder.LITTLE_ENDIAN)) {
            val input = concat(SOI, TestImages.fullExif(orientation = 6, order = order), icc(), baseline.body(), EOI)
            assertEquals(6, Jpeg.inspect(input).orientation)
            val out = Jpeg.clean(input)
            assertArrayEquals(concat(SOI, TestImages.orientationApp1(6), icc(), baseline.body(), EOI), out)
            val again = Jpeg.inspect(out)
            assertEquals(6, again.orientation)
            assertEquals(setOf(ICC_KEPT), again.found)
        }
        for (o in listOf(1, 0, 9, 0xFFFF)) {
            val input = concat(SOI, TestImages.orientationOnlyExif(o), baseline.body(), EOI)
            assertEquals(1, Jpeg.inspect(input).orientation)
            assertArrayEquals(TestImages.plainJpeg(), Jpeg.clean(input))
        }
        assertArrayEquals(TestImages.plainJpeg(), Jpeg.clean(concat(SOI, TestImages.fullExif(), baseline.body(), EOI)))
    }

    @Test fun c2paIsReported() {
        val input = concat(SOI, TestImages.c2pa(), baseline.body(), EOI)
        assertEquals(setOf(C2PA), Jpeg.inspect(input).found)
        val out = Jpeg.clean(input)
        assertArrayEquals(TestImages.plainJpeg(), out)
        assertEquals(-1, indexOf(out, ascii("jumb")))
    }

    @Test fun artistAndCopyrightAreAuthorRights() {
        val t = TestImages.Tiff()
        val artist = exifSegment(t.build(listOf(t.ascii(0x013B, "Autora Inventada"))))
        val copyright = exifSegment(t.build(listOf(t.ascii(0x8298, "(c) Nadie"))))
        val creator = TestImages.xmp(TestImages.xmpPacket("><dc:creator><rdf:Seq><rdf:li>Autora</rdf:li></rdf:Seq></dc:creator></rdf:Description"))
        val rights = TestImages.xmp(TestImages.xmpPacket("dc:rights='Nadie'"))
        for (s in listOf(artist, copyright)) {
            assertEquals(setOf(AUTHOR_RIGHTS), Jpeg.inspect(concat(SOI, s, baseline.body(), EOI)).found)
        }
        for (s in listOf(creator, rights)) {
            assertEquals(setOf(AUTHOR_RIGHTS, XMP_COMMENTS), Jpeg.inspect(concat(SOI, s, baseline.body(), EOI)).found)
        }
    }

    @Test fun unknownSegmentsAreOtherAndDropped() {
        val input = concat(
            SOI, seg(0xE3, ascii("fabricante")), seg(0xEC, ascii("Ducky")), seg(0xE1, ascii("desconocido")),
            seg(0xE2, ascii("FPXR")), seg(0xEF, ascii("x")), baseline.body(), EOI,
        )
        assertEquals(setOf(OTHER), Jpeg.inspect(input).found)
        assertArrayEquals(TestImages.plainJpeg(), Jpeg.clean(input))
    }

    @Test fun jfifThumbnailIsReported() {
        assertEquals(setOf(THUMBNAIL), Jpeg.inspect(concat(SOI, TestImages.jfif(2, 2), baseline.body(), EOI)).found)
        assertTrue(Jpeg.inspect(concat(SOI, TestImages.jfif(), baseline.body(), EOI)).found.isEmpty())
    }

    @Test fun adobeIsTrimmedToItsTwelveBytes() {
        val long = seg(0xEE, concat(ascii("Adobe"), bytes(0, 100, 0, 0, 0, 0, 1), ascii("datos escondidos")))
        val out = Jpeg.clean(concat(SOI, long, baseline.body(), EOI))
        assertArrayEquals(concat(SOI, adobe(), baseline.body(), EOI), out)
        // Too short to be a real Adobe segment: dropped.
        val short = seg(0xEE, ascii("Adobe"))
        assertArrayEquals(TestImages.plainJpeg(), Jpeg.clean(concat(SOI, short, baseline.body(), EOI)))
    }

    @Test fun segmentLengthPastTheEndFails() {
        damaged(concat(SOI, bytes(0xFF, 0xE1, 0xFF, 0xFF), ascii("corto")))
        damaged(concat(SOI, bytes(0xFF, 0xE1, 0x00, 0x08, 1, 2)))
        damaged(concat(SOI, bytes(0xFF, 0xE1, 0x00)))
    }

    @Test fun lengthBelowTwoFails() {
        damaged(concat(SOI, bytes(0xFF, 0xE1, 0x00, 0x01), baseline.body(), EOI))
        damaged(concat(SOI, bytes(0xFF, 0xDB, 0x00, 0x00), baseline.body(), EOI))
    }

    @Test fun noSosFails() {
        damaged(concat(SOI, *baseline.tables.toTypedArray(), EOI))
        damaged(concat(SOI, EOI))
    }

    @Test fun otherStructuralDamageFails() {
        damaged(ByteArray(0))
        damaged(bytes(0xFF))
        damaged(SOI)
        damaged(bytes(0xFF, 0xD9, 0xFF, 0xD8))
        // No EOI: the scan data runs to the end of the file.
        damaged(concat(SOI, baseline.body()))
        // Truncated in the middle of the fill bytes.
        damaged(concat(SOI, baseline.body(), bytes(0xFF, 0xFF)))
        // SOS before any SOF.
        damaged(concat(SOI, baseline.sos, baseline.scan, EOI))
        // Garbage between segments.
        damaged(concat(SOI, bytes(0x00), baseline.body(), EOI))
        // A second SOI.
        damaged(concat(SOI, SOI, baseline.body(), EOI))
    }

    @Test fun fillBytesBetweenSegmentsAreAccepted() {
        val input = concat(SOI, bytes(0xFF, 0xFF, 0xFF), icc(), bytes(0xFF), *baseline.tables.toTypedArray(), bytes(0xFF, 0xFF), baseline.sos, baseline.scan, bytes(0xFF, 0xFF), EOI)
        assertArrayEquals(concat(SOI, icc(), baseline.body(), EOI), Jpeg.clean(input))
    }

    @Test fun restartMarkersAndStuffedBytesSurvive() {
        val dri = seg(0xDD, bytes(0, 4))
        val scan = bytes(0x12, 0xFF, 0x00, 0x34, 0xFF, 0xD0, 0x56, 0xFF, 0x00, 0xD9, 0x9A, 0xFF, 0xD7, 0x78, 0xFF, 0x00)
        val input = concat(SOI, dri, *baseline.tables.toTypedArray(), baseline.sos, scan, EOI, ascii("resto"))
        val out = Jpeg.clean(input)
        assertArrayEquals(concat(SOI, dri, *baseline.tables.toTypedArray(), baseline.sos, scan, EOI), out)
    }

    @Test fun progressiveWithSeveralScans() {
        val dqt = baseline.tables.first { it[1] == 0xDB.toByte() }
        val sof2 = seg(0xC2, bytes(8, 0, 16, 0, 16, 1, 1, 0x11, 0))
        fun dht(k: Int) = seg(0xC4, concat(bytes(k), ByteArray(16).also { it[0] = 1 }, bytes(k)))
        fun sos(ss: Int, se: Int) = seg(0xDA, bytes(1, 1, 0, ss, se, 0))
        val input = concat(
            SOI, dqt, sof2,
            dht(0), sos(0, 0), bytes(1, 2, 0xFF, 0x00, 3),
            dht(0x10), sos(1, 5), bytes(4, 5, 0xFF, 0xD1, 6),
            dht(0x11), sos(6, 63), bytes(7, 8, 9),
            EOI,
        )
        assertArrayEquals(input, Jpeg.clean(input))
        assertTrue(Jpeg.inspect(input).found.isEmpty())
    }

    @Test fun cleanIsIdempotent() {
        val inputs = listOf(
            TestImages.everything(),
            concat(SOI, TestImages.fullExif(orientation = 8), icc(), adobe(), baseline.body(), EOI),
            TestImages.plainJpeg(),
        )
        for (x in inputs) {
            val once = Jpeg.clean(x)
            assertTrue(Jpeg.clean(once).contentEquals(once))
        }
    }

    /** Decoded ARGB pixels through the JDK's ImageIO, by reflection: Android unit tests have no ImageIO. */
    private fun pixels(jpg: ByteArray): IntArray? {
        val imageIO = try { Class.forName("javax.imageio.ImageIO") } catch (e: ClassNotFoundException) { return null }
        val img = imageIO.getMethod("read", java.io.InputStream::class.java).invoke(null, ByteArrayInputStream(jpg))!!
        val w = img.javaClass.getMethod("getWidth").invoke(img) as Int
        val h = img.javaClass.getMethod("getHeight").invoke(img) as Int
        val rgb = img.javaClass.getMethod("getRGB", Int::class.java, Int::class.java)
        return IntArray(w * h + 2) { i -> when (i) { 0 -> w; 1 -> h; else -> rgb.invoke(img, (i - 2) % w, (i - 2) / w) as Int } }
    }

    /** The cleaned image decodes to exactly the same pixels: nothing was re-encoded. */
    @Test fun pixelsAreUnchanged() {
        val input = concat(
            SOI, TestImages.jfif(), TestImages.fullExif(orientation = 3), TestImages.xmp(TestImages.xmpPacket("")),
            adobe(), TestImages.com("x"), baseline.body(), EOI, TestImages.plainJpeg(),
        )
        val a = pixels(TestImages.plainJpeg())
        assumeTrue("sin ImageIO (test de Android)", a != null)
        assertArrayEquals(a, pixels(Jpeg.clean(input)))
    }

    /** Broken EXIF never throws: it is reported as OTHER and dropped. */
    @Test fun hostileExifIsHarmless() {
        val bad = listOf(
            bytes(),
            ascii("MM"),
            concat(ascii("MM"), bytes(0, 0x2A, 0xFF, 0xFF, 0xFF, 0xFF)),
            concat(ascii("II"), bytes(0x2A, 0, 8, 0, 0, 0, 0xFF, 0xFF)),
            concat(ascii("XX"), bytes(0, 0x2A, 0, 0, 0, 8)),
            // One entry: GPS pointer far out of range.
            concat(ascii("MM"), bytes(0, 0x2A, 0, 0, 0, 8, 0, 1, 0x88, 0x25, 0, 4, 0, 0, 0, 1, 0x7F, 0xFF, 0xFF, 0xFF, 0, 0, 0, 0)),
            // One entry: a value of 0x7FFFFFFF rationals past the end; IFD1 pointing at IFD0.
            concat(ascii("MM"), bytes(0, 0x2A, 0, 0, 0, 8, 0, 1, 0x01, 0x10, 0, 5, 0x7F, 0xFF, 0xFF, 0xFF, 0, 0, 0, 8, 0, 0, 0, 8)),
            // Orientation with a type that is not SHORT.
            concat(ascii("MM"), bytes(0, 0x2A, 0, 0, 0, 8, 0, 1, 0x01, 0x12, 0, 0x55, 0, 0, 0, 1, 0, 6, 0, 0, 0, 0, 0, 0)),
        )
        for (tiff in bad) {
            val input = concat(SOI, exifSegment(tiff), baseline.body(), EOI)
            val r = Jpeg.inspect(input)
            assertEquals(1, r.orientation)
            assertNull(r.gps)
            assertArrayEquals(TestImages.plainJpeg(), Jpeg.clean(input))
        }
    }

    @Test fun badGpsGivesNoCoordinates() {
        val t = TestImages.Tiff()
        fun gps(vararg e: TestImages.Entry) = concat(SOI, exifSegment(t.build(emptyList(), gps = e.toList())), baseline.body(), EOI)
        val cases = listOf(
            gps(t.ascii(0x0001, "N"), t.rationals(0x0002, 40L to 0L, 0L to 1L, 0L to 1L), t.ascii(0x0003, "E"), t.rationals(0x0004, 3L to 1L, 0L to 1L, 0L to 1L)),
            gps(t.ascii(0x0001, "N"), t.rationals(0x0002, 95L to 1L, 0L to 1L, 0L to 1L), t.ascii(0x0003, "E"), t.rationals(0x0004, 3L to 1L, 0L to 1L, 0L to 1L)),
            gps(t.ascii(0x0001, "Q"), t.rationals(0x0002, 40L to 1L, 0L to 1L, 0L to 1L), t.ascii(0x0003, "E"), t.rationals(0x0004, 3L to 1L, 0L to 1L, 0L to 1L)),
            gps(t.ascii(0x0001, "N"), t.rationals(0x0002, 40L to 1L, 0L to 1L)),
        )
        for (c in cases) {
            val r = Jpeg.inspect(c)
            assertTrue(GPS in r.found)
            assertNull(r.gps)
        }
        val south = gps(t.ascii(0x0001, "S"), t.rationals(0x0002, 33L to 1L, 52L to 1L, 4L to 10L), t.ascii(0x0003, "E"), t.rationals(0x0004, 151L to 1L, 12L to 1L, 3L to 1L))
        assertEquals(-33.87 to 151.2, Jpeg.inspect(south).gps)
    }

    /**
     * Random corruption: inspect and clean only ever throw PhotoFailure, and every output
     * that comes back holds nothing but the colour profile.
     */
    @Test fun corruptedInputNeverEscapesTheWhitelist() {
        val seeds = listOf(TestImages.everything(), concat(SOI, TestImages.fullExif(orientation = 6), icc(), adobe(), TestImages.c2pa(), baseline.body(), EOI))
        val rnd = Random(20261027)
        var cleaned = 0
        repeat(4000) { i ->
            val x = seeds[i % seeds.size].copyOf()
            when (rnd.nextInt(3)) {
                0 -> repeat(1 + rnd.nextInt(8)) { x[rnd.nextInt(x.size)] = rnd.nextInt(256).toByte() }
                1 -> repeat(1 + rnd.nextInt(4)) { val k = rnd.nextInt(x.size); x[k] = if (rnd.nextBoolean()) 0xFF.toByte() else 0 }
                else -> {}
            }
            val y = if (rnd.nextInt(4) == 0) x.copyOf(rnd.nextInt(x.size + 1)) else x
            try {
                Jpeg.inspect(y)
                val out = Jpeg.clean(y)
                cleaned++
                val r = Jpeg.inspect(out)
                assertTrue("iteración $i: ${r.found}", (r.found - ICC_KEPT).isEmpty())
                assertTrue(Jpeg.clean(out).contentEquals(out))
            } catch (e: PhotoFailure) {
                assertEquals(PHOTO_DAMAGED, e.message)
            }
        }
        assertTrue("casi todas fallan: $cleaned", cleaned > 100)
    }

    @Test fun noOutputByteAfterEoi() {
        val out = Jpeg.clean(TestImages.everything())
        assertEquals(0xFF.toByte(), out[out.size - 2])
        assertEquals(0xD9.toByte(), out[out.size - 1])
        assertFalse(indexOf(out, ascii("datos tras el final")) >= 0)
    }
}
