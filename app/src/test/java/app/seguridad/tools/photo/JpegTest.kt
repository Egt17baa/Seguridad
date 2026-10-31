package app.seguridad.tools.photo

import app.seguridad.tools.photo.Found.AUTHOR_RIGHTS
import app.seguridad.tools.photo.Found.C2PA
import app.seguridad.tools.photo.Found.DATE_SOFTWARE
import app.seguridad.tools.photo.Found.DEVICE
import app.seguridad.tools.photo.Found.EXTRA_IMAGE
import app.seguridad.tools.photo.Found.GPS
import app.seguridad.tools.photo.Found.HDR_GAIN_MAP
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
        // The junk after EOI is neither a video nor an image, so MPF names no image either (N4 of the F6 re-review).
        assertEquals(setOf(GPS, DEVICE, DATE_SOFTWARE, THUMBNAIL, XMP_COMMENTS, ICC_KEPT, AUTHOR_RIGHTS, OTHER), r.found)
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
        // Another image, not a video (Ultra HDR review, audit 1.3.0).
        assertEquals(setOf(EXTRA_IMAGE), Jpeg.inspect(input).found)
        // A second image alone after EOI, without MPF, is still reported, as an image.
        assertEquals(setOf(EXTRA_IMAGE), Jpeg.inspect(concat(TestImages.plainJpeg(), TestImages.plainJpeg())).found)
        // After zero or FF padding too.
        assertEquals(setOf(EXTRA_IMAGE), Jpeg.inspect(concat(TestImages.plainJpeg(), ByteArray(8), TestImages.bytes(0xFF, 0xFF), TestImages.plainJpeg())).found)
    }

    /** The primary XMP of an Ultra HDR JPEG (Android 14+): the gain map's version and its place in the container. */
    private val ultraHdrXmp = TestImages.xmp(
        TestImages.xmpPacket(
            "xmlns:Container='http://ns.google.com/photos/1.0/container/' " +
                "xmlns:Item='http://ns.google.com/photos/1.0/container/item/' " +
                "xmlns:hdrgm='http://ns.adobe.com/hdr-gain-map/1.0/' hdrgm:Version='1.0'><Container:Directory><rdf:Seq>" +
                "<rdf:li><Container:Item Item:Semantic='Primary' Item:Mime='image/jpeg'/></rdf:li>" +
                "<rdf:li><Container:Item Item:Semantic='GainMap' Item:Mime='image/jpeg' Item:Length='1234'/></rdf:li>" +
                "</rdf:Seq></Container:Directory></rdf:Description><rdf:Description",
        ),
    )

    @Test fun ultraHdrIsAGainMapNotAVideo() {
        // MPF, the gain map's XMP, and the gain map (a JPEG) after EOI.
        val input = concat(SOI, ultraHdrXmp, TestImages.mpf(), baseline.body(), EOI, TestImages.plainJpeg())
        val r = Jpeg.inspect(input)
        assertEquals(setOf(XMP_COMMENTS, HDR_GAIN_MAP), r.found)
        assertFalse(MOTION_VIDEO in r.found)
        // The copy is the base image alone: no XMP, no MPF, no gain map.
        val out = PhotoCleaner.clean(input)
        assertArrayEquals(TestImages.plainJpeg(), out)
        assertTrue(Jpeg.inspect(out).found.isEmpty())
        // ISO 21496-1 gain map metadata (Android 15+), in APP2 instead of XMP.
        val iso = TestImages.seg(0xE2, concat(ascii("urn:iso:std:iso:ts:21496:-1"), bytes(0, 0, 0, 0, 0)))
        val isoInput = concat(SOI, iso, TestImages.mpf(), baseline.body(), EOI, TestImages.plainJpeg())
        assertEquals(setOf(HDR_GAIN_MAP), Jpeg.inspect(isoInput).found)
        assertArrayEquals(TestImages.plainJpeg(), PhotoCleaner.clean(isoInput))
    }

    @Test fun motionPhotosAreStillVideos() {
        val mp4 = concat(bytes(0, 0, 0, 0x18), ascii("ftypmp42"), ByteArray(16) { 1 })
        // Google's marker in XMP, with the video after EOI.
        val google = concat(SOI, TestImages.xmp(TestImages.xmpPacket("xmlns:GCamera='http://ns.google.com/photos/1.0/camera/' GCamera:MotionPhoto='1'")), baseline.body(), EOI, mp4)
        assertEquals(setOf(XMP_COMMENTS, MOTION_VIDEO), Jpeg.inspect(google).found)
        assertArrayEquals(TestImages.plainJpeg(), PhotoCleaner.clean(google))
        // No marker, but an MP4 right after EOI.
        assertEquals(setOf(MOTION_VIDEO), Jpeg.inspect(concat(TestImages.plainJpeg(), mp4)).found)
        // Samsung's trailer names it.
        val samsung = concat(TestImages.plainJpeg(), ascii("MotionPhoto_Data"), mp4, ascii("SEFH"))
        assertEquals(setOf(MOTION_VIDEO), Jpeg.inspect(samsung).found)
        // An Ultra HDR motion photo: both.
        val both = concat(
            SOI, ultraHdrXmp, TestImages.xmp(TestImages.xmpPacket("GCamera:MotionPhoto='1'")), TestImages.mpf(), baseline.body(), EOI,
            TestImages.plainJpeg(), mp4,
        )
        assertEquals(setOf(XMP_COMMENTS, HDR_GAIN_MAP, MOTION_VIDEO), Jpeg.inspect(both).found)
        assertArrayEquals(TestImages.plainJpeg(), PhotoCleaner.clean(both))
    }

    @Test fun aVideoAfterTheGainMapIsStillAVideo() {
        // M4 of the F6 review: an Ultra HDR motion photo whose XMP has no motion marker, the video after the gain map.
        val mp4 = concat(bytes(0, 0, 0, 0x18), ascii("ftypmp42"), ByteArray(16) { 1 })
        val head = concat(SOI, ultraHdrXmp, TestImages.mpf(), baseline.body(), EOI)
        for (tail in listOf(
            concat(TestImages.plainJpeg(), mp4),
            concat(TestImages.plainJpeg(), ascii("MotionPhoto_Data"), mp4, ascii("SEFH")),
            // Deep in the trailer, past a large gain map.
            concat(TestImages.plainJpeg(), ByteArray(300_000) { 7 }, ascii("MotionPhoto_Data"), mp4),
        )) {
            val input = concat(head, tail)
            assertEquals(setOf(XMP_COMMENTS, HDR_GAIN_MAP, MOTION_VIDEO), Jpeg.inspect(input).found)
            assertArrayEquals(TestImages.plainJpeg(), PhotoCleaner.clean(input))
        }
        // Without a gain map: another image, then the video.
        assertEquals(setOf(EXTRA_IMAGE, MOTION_VIDEO), Jpeg.inspect(concat(TestImages.plainJpeg(), TestImages.plainJpeg(), mp4)).found)
        // «ftyp» with a brand that is not a video (HEIC) is not a video.
        val heic = concat(bytes(0, 0, 0, 0x18), ascii("ftypheic"), ByteArray(16) { 1 })
        assertEquals(setOf(EXTRA_IMAGE), Jpeg.inspect(concat(TestImages.plainJpeg(), TestImages.plainJpeg(), heic)).found)
    }

    @Test fun anMp4RightAfterTheImageIsAVideoWhateverItsBrand() {
        // N2 of the F6 re-review: right at the start of the trailer, any brand but a still image's is a video (as before
        // the M4 round); deeper in it, only a video brand counts, against chance matches.
        fun mp4(brand: String) = concat(bytes(0, 0, 0, 0x18), ascii("ftyp$brand"), ByteArray(16) { 1 })
        for (brand in listOf("iso3", "mp71", "XAVC", "f4v ", "nvr1", "mp42", "isom")) {
            assertEquals(brand, setOf(MOTION_VIDEO), Jpeg.inspect(concat(TestImages.plainJpeg(), mp4(brand))).found)
            assertArrayEquals(TestImages.plainJpeg(), PhotoCleaner.clean(concat(TestImages.plainJpeg(), mp4(brand))))
        }
        for (brand in listOf("heic", "avif", "mif1")) {
            assertEquals(brand, setOf(OTHER), Jpeg.inspect(concat(TestImages.plainJpeg(), mp4(brand))).found)
        }
        // Deeper, after another image: a video brand is a video, an unknown one is not taken for one.
        assertEquals(setOf(EXTRA_IMAGE, MOTION_VIDEO), Jpeg.inspect(concat(TestImages.plainJpeg(), TestImages.plainJpeg(), mp4("iso3"))).found)
        assertEquals(setOf(EXTRA_IMAGE), Jpeg.inspect(concat(TestImages.plainJpeg(), TestImages.plainJpeg(), mp4("nvr1"))).found)
    }

    @Test fun aGainMapIsNamedOnlyWhenTheSecondImageIsThere() {
        // M5 of the F6 review: an editor kept the Ultra HDR XMP and dropped the gain map. No «second image» is claimed.
        assertEquals(setOf(XMP_COMMENTS), Jpeg.inspect(concat(SOI, ultraHdrXmp, baseline.body(), EOI)).found)
        // The same with MPF still there, and nothing after EOI: MPF names an image that is not in the file.
        assertEquals(setOf(XMP_COMMENTS, OTHER), Jpeg.inspect(concat(SOI, ultraHdrXmp, TestImages.mpf(), baseline.body(), EOI)).found)
        // A lone MPF, a lone ISO 21496-1 block: other data, dropped.
        assertEquals(setOf(OTHER), Jpeg.inspect(concat(SOI, TestImages.mpf(), baseline.body(), EOI)).found)
        val iso = TestImages.seg(0xE2, concat(ascii("urn:iso:std:iso:ts:21496:-1"), bytes(0, 0, 0, 0, 0)))
        assertEquals(setOf(OTHER), Jpeg.inspect(concat(SOI, iso, baseline.body(), EOI)).found)
        // Gain map metadata and something after EOI that is not an image: no gain map, and no other image (N4).
        assertEquals(
            setOf(XMP_COMMENTS, OTHER),
            Jpeg.inspect(concat(SOI, ultraHdrXmp, TestImages.mpf(), baseline.body(), EOI, ascii("datos tras el final"))).found,
        )
        // The copy is the base image in every case.
        assertArrayEquals(TestImages.plainJpeg(), PhotoCleaner.clean(concat(SOI, ultraHdrXmp, iso, TestImages.mpf(), baseline.body(), EOI)))
    }

    @Test fun otherDataAfterEoiIsNamedNeutrally() {
        val r = Jpeg.inspect(concat(TestImages.plainJpeg(), ascii("datos tras el final")))
        assertEquals(setOf(OTHER), r.found)
        assertArrayEquals(TestImages.plainJpeg(), Jpeg.clean(concat(TestImages.plainJpeg(), ascii("datos tras el final"))))
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

    /** The Dublin Core namespace can be bound to any prefix. */
    @Test fun dublinCoreUnderAnyPrefixIsAuthorRights() {
        val ns = "xmlns:foo='http://purl.org/dc/elements/1.1/'"
        for (inner in listOf("$ns><foo:creator>Autora</foo:creator></rdf:Description", "$ns foo:rights='Nadie'", "xmlns:b = \"http://purl.org/dc/elements/1.1/\" b:creator='x'")) {
            val input = concat(SOI, TestImages.xmp(TestImages.xmpPacket(inner)), baseline.body(), EOI)
            assertEquals(inner, setOf(AUTHOR_RIGHTS, XMP_COMMENTS), Jpeg.inspect(input).found)
        }
        val titleOnly = concat(SOI, TestImages.xmp(TestImages.xmpPacket("$ns foo:title='x'")), baseline.body(), EOI)
        assertEquals(setOf(XMP_COMMENTS), Jpeg.inspect(titleOnly).found)
    }

    @Test fun standaloneMarkersOutsideAScanAreOther() {
        for (m in listOf(0x01, 0xD0, 0xD7)) {
            val input = concat(SOI, bytes(0xFF, m), baseline.body(), EOI)
            assertEquals(setOf(OTHER), Jpeg.inspect(input).found)
            assertArrayEquals(TestImages.plainJpeg(), Jpeg.clean(input))
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
                assertTrue("iteración $i: estructura", Jpeg.isCleanOutput(out))
                PhotoCleaner.verify(PhotoFormat.JPEG, out)
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
