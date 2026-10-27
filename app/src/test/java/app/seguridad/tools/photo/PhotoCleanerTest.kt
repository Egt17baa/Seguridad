package app.seguridad.tools.photo

import app.seguridad.tools.photo.TestImages.EOI
import app.seguridad.tools.photo.TestImages.SOI
import app.seguridad.tools.photo.TestImages.ascii
import app.seguridad.tools.photo.TestImages.baseline
import app.seguridad.tools.photo.TestImages.bytes
import app.seguridad.tools.photo.TestImages.concat
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Format detection, the dispatch and the check after cleaning. */
class PhotoCleanerTest {
    private fun fails(message: String, block: () -> Unit) {
        assertEquals(message, assertThrows(PhotoFailure::class.java) { block() }.message)
    }

    private fun ftyp(brand: String, vararg compatible: String) =
        concat(bytes(0, 0, 0, 16 + 4 * compatible.size), ascii("ftyp"), ascii(brand), bytes(0, 0, 0, 0), *compatible.map { ascii(it) }.toTypedArray())

    @Test fun detectsTheFormats() {
        assertEquals(PhotoFormat.JPEG, detectFormat(bytes(0xFF, 0xD8, 0xFF, 0xE0)))
        assertEquals(PhotoFormat.PNG, detectFormat(bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0)))
        assertEquals(PhotoFormat.WEBP, detectFormat(concat(ascii("RIFF"), bytes(1, 2, 3, 4), ascii("WEBPVP8 "))))
        for (b in listOf("heic", "heix", "hevc", "heim", "heis", "mif1", "msf1", "avif")) {
            assertEquals(b, PhotoFormat.HEIF, detectFormat(ftyp(b)))
        }
        assertEquals(PhotoFormat.HEIF, detectFormat(ftyp("isom", "mp41", "mif1")))
    }

    @Test fun avifIsToldApartFromHeic() {
        // ImageDecoder reads AVIF only from Android 12: the screen needs to know which one it has.
        assertTrue(isAvif(ftyp("avif", "mif1", "miaf")))
        assertTrue(isAvif(ftyp("avis", "msf1")))
        assertTrue(isAvif(ftyp("mif1", "avif")))
        assertFalse(isAvif(ftyp("heic", "mif1")))
        assertFalse(isAvif(ftyp("mif1", "heic", "avif"))) // A HEVC brand too: decodable as HEIC.
        assertFalse(isAvif(ftyp("mif1")))
        assertFalse(isAvif(bytes(0xFF, 0xD8, 0xFF, 0xE0)))
        assertFalse(isAvif(ByteArray(0)))
        assertFalse(isAvif(concat(bytes(0x7F, 0xFF, 0xFF, 0xFF), ascii("ftyp"), ascii("mif1"), bytes(0, 0, 0, 0), ascii("av"))))
    }

    @Test fun anythingElseIsNull() {
        assertNull(detectFormat(ByteArray(0)))
        assertNull(detectFormat(bytes(0xFF, 0xD8)))
        assertNull(detectFormat(ascii("GIF89a")))
        assertNull(detectFormat(ascii("BM")))
        assertNull(detectFormat(concat(ascii("RIFF"), bytes(1, 2, 3, 4), ascii("WAVE"))))
        assertNull(detectFormat(ftyp("mp42", "isom")))
        assertNull(detectFormat(ftyp("qt  ")))
        // A compatible-brand list longer than the head: no read past the end.
        assertNull(detectFormat(concat(bytes(0x7F, 0xFF, 0xFF, 0xFF), ascii("ftyp"), ascii("mp42"), bytes(0, 0, 0, 0), ascii("is"))))
        assertNull(detectFormat(bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A)))
    }

    @Test fun cleansJpeg() {
        val out = PhotoCleaner.clean(TestImages.everything())
        assertArrayEquals(Jpeg.clean(TestImages.everything()), out)
        assertEquals(setOf(Found.ICC_KEPT), PhotoCleaner.inspect(out).found)
        assertEquals(Jpeg.inspect(TestImages.everything()), PhotoCleaner.inspect(TestImages.everything()))
    }

    @Test fun heifAndUnknownAreUnsupported() {
        for (x in listOf(ftyp("heic", "mif1"), ascii("GIF89a"), ByteArray(0), ascii("BM0000"))) {
            fails(PHOTO_UNSUPPORTED) { PhotoCleaner.inspect(x) }
            fails(PHOTO_UNSUPPORTED) { PhotoCleaner.clean(x) }
        }
    }

    @Test fun damagedJpegIsDamaged() {
        fails(PHOTO_DAMAGED) { PhotoCleaner.clean(concat(SOI, *baseline.tables.toTypedArray(), EOI)) }
        fails(PHOTO_DAMAGED) { PhotoCleaner.inspect(concat(SOI, bytes(0xFF, 0xE1, 0xFF, 0xFF))) }
    }

    /** The check after cleaning fails closed on anything outside the list, or a broken structure. */
    @Test fun checkAfterCleaningFailsClosed() {
        PhotoCleaner.verify(PhotoFormat.JPEG, TestImages.plainJpeg())
        PhotoCleaner.verify(PhotoFormat.JPEG, concat(SOI, TestImages.icc(), baseline.body(), EOI))
        PhotoCleaner.verify(PhotoFormat.JPEG, concat(SOI, TestImages.orientationApp1(6), TestImages.icc(), TestImages.adobe(), baseline.body(), EOI))
        for (o in 2..8) PhotoCleaner.verify(PhotoFormat.JPEG, concat(SOI, TestImages.orientationApp1(o), baseline.body(), EOI))
        val dirty = listOf(
            concat(SOI, TestImages.com("x"), baseline.body(), EOI),
            concat(SOI, TestImages.fullExif(), baseline.body(), EOI),
            concat(TestImages.plainJpeg(), ByteArray(8)),
            concat(TestImages.plainJpeg(), TestImages.plainJpeg()),
            concat(SOI, baseline.body()),
            ascii("GIF89a"),
            // (a) JFIF with a 0x0 thumbnail and bytes hidden after it: inspect reports nothing.
            concat(SOI, TestImages.seg(0xE0, concat(ascii("JFIF"), bytes(0, 1, 1, 0, 0, 1, 0, 1, 0, 0), ascii("secreto"))), baseline.body(), EOI),
            // (b) EXIF with an empty IFD0, and the canonical Orientation APP1 with bytes appended.
            concat(SOI, TestImages.exifSegment(TestImages.Tiff().build(emptyList())), baseline.body(), EOI),
            concat(SOI, TestImages.seg(0xE1, concat(TestImages.orientationApp1(6).copyOfRange(4, 36), ascii("secreto"))), baseline.body(), EOI),
            // (c) APP14 Adobe longer than its 12 bytes.
            concat(SOI, TestImages.seg(0xEE, concat(ascii("Adobe"), bytes(0, 100, 0, 0, 0, 0, 1), ascii("secreto"))), baseline.body(), EOI),
            // (d) Standalone RST and TEM markers at the top level.
            concat(SOI, bytes(0xFF, 0xD0), baseline.body(), EOI),
            concat(SOI, bytes(0xFF, 0x01), baseline.body(), EOI),
            // Fill bytes: clean never writes them.
            concat(SOI, bytes(0xFF), TestImages.icc(), baseline.body(), EOI),
            // Orientation 1 is never written, and an APP1 out of the canonical bytes is refused.
            concat(SOI, TestImages.orientationApp1(1), baseline.body(), EOI),
            // SOS before SOF, and no SOS at all.
            concat(SOI, baseline.sos, baseline.scan, *baseline.tables.toTypedArray(), EOI),
            concat(SOI, *baseline.tables.toTypedArray(), EOI),
        )
        for ((i, d) in dirty.withIndex()) {
            fails(PHOTO_DAMAGED) { PhotoCleaner.verify(PhotoFormat.JPEG, d) }
            // The structural check alone refuses each one, without help from inspect or idempotence.
            assertFalse("caso $i", Jpeg.isCleanOutput(d))
        }
        assertTrue(Jpeg.isCleanOutput(concat(SOI, TestImages.orientationApp1(6), TestImages.icc(), TestImages.adobe(), baseline.body(), EOI)))
    }

    /** PNG and WebP: the same fail-closed check, one dirty shape at a time. */
    @Test fun checkAfterCleaningFailsClosedForPngAndWebp() {
        val png = TestImages.plainPng()
        PhotoCleaner.verify(PhotoFormat.PNG, png)
        PhotoCleaner.verify(PhotoFormat.PNG, concat(TestImages.PNG_SIGNATURE, TestImages.ihdr(), TestImages.gama(), TestImages.iccp(), TestImages.plte(), TestImages.trns(), *TestImages.idats().toTypedArray(), TestImages.iend()))
        val idat = TestImages.idats().toTypedArray()
        fun p(vararg c: ByteArray) = concat(TestImages.PNG_SIGNATURE, *c)
        val dirtyPng = listOf(
            p(TestImages.ihdr(), TestImages.text("Author", "Autora Inventada"), *idat, TestImages.iend()), // tEXt left in
            p(TestImages.ihdr(), *idat, TestImages.iend(), ByteArray(1)), // a byte after IEND
            concat(png, png), // a second image after IEND
            p(TestImages.ihdr(), TestImages.pngChunk("eXIf", TestImages.fullTiff()), *idat, TestImages.iend()),
            p(TestImages.ihdr(), TestImages.pngChunk("tIME", ByteArray(7)), *idat, TestImages.iend()),
            p(TestImages.ihdr(), TestImages.pngChunk("prVt", ascii("x")), *idat, TestImages.iend()),
            p(TestImages.ihdr(), *idat), // no IEND
            p(*idat, TestImages.iend()), // no IHDR
            p(TestImages.ihdr(), TestImages.plte(), TestImages.plte(), *idat, TestImages.iend()),
            p(TestImages.ihdr(), *idat, TestImages.plte(), TestImages.iend()),
            png.copyOf().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() }, // bad CRC
            ascii("GIF89a"),
        )
        for ((i, d) in dirtyPng.withIndex()) {
            fails(PHOTO_DAMAGED) { PhotoCleaner.verify(PhotoFormat.PNG, d) }
            assertFalse("png $i", Png.isCleanOutput(d))
        }

        val vp8 = TestImages.riffChunk("VP8 ", TestImages.VP8_DATA)
        val icc = TestImages.riffChunk("ICCP", ascii("perfil"))
        PhotoCleaner.verify(PhotoFormat.WEBP, TestImages.riff(vp8))
        PhotoCleaner.verify(PhotoFormat.WEBP, TestImages.riff(TestImages.vp8x(TestImages.ICC_FLAG), icc, vp8))
        val dirtyWebp = listOf(
            TestImages.riff(TestImages.vp8x(TestImages.EXIF_FLAG), vp8), // the EXIF bit set
            TestImages.riff(TestImages.vp8x(TestImages.XMP_FLAG), vp8),
            TestImages.riff(TestImages.vp8x(0x01), vp8), // a reserved bit
            TestImages.riff(TestImages.vp8x(0), vp8, TestImages.riffChunk("EXIF", TestImages.fullTiff())), // a leftover EXIF chunk
            TestImages.riff(TestImages.vp8x(0), vp8, TestImages.riffChunk("XMP ", ascii("x"))),
            TestImages.riff(TestImages.vp8x(0), vp8, TestImages.riffChunk("ZZZZ", ascii("x"))),
            TestImages.riff(vp8, TestImages.riffChunk("EXIF", ByteArray(4))), // a simple file with more
            concat(TestImages.riff(vp8), ByteArray(2)), // bytes after the RIFF
            TestImages.riff(vp8).also { it[4] = (it[4] + 2).toByte() }, // RIFF size lies
            TestImages.riff(TestImages.riffChunk("VP8L", TestImages.VP8L_DATA)).also { it[it.size - 1] = 0x41 }, // hidden byte in the padding
            TestImages.riff(TestImages.vp8x(TestImages.ANIM_FLAG), TestImages.riffChunk("ANIM", ByteArray(6)), TestImages.anmf(vp8, TestImages.riffChunk("ZZZZ", ascii("x")))), // junk inside a frame
            TestImages.riff(vp8, vp8),
            TestImages.riff(TestImages.vp8x(0)), // no image
            ascii("GIF89a"),
        )
        for ((i, d) in dirtyWebp.withIndex()) {
            fails(PHOTO_DAMAGED) { PhotoCleaner.verify(PhotoFormat.WEBP, d) }
            assertFalse("webp $i", Webp.isCleanOutput(d))
        }
    }

    @Test fun cleansPngAndWebp() {
        val png = PhotoCleaner.clean(TestImages.everythingPng())
        assertArrayEquals(Png.clean(TestImages.everythingPng()), png)
        assertEquals(setOf(Found.ICC_KEPT), PhotoCleaner.inspect(png).found)
        assertEquals(PhotoFormat.PNG, PhotoCleaner.inspect(TestImages.everythingPng()).format)
        val webp = PhotoCleaner.clean(TestImages.everythingWebp())
        assertArrayEquals(Webp.clean(TestImages.everythingWebp()), webp)
        assertEquals(setOf(Found.ICC_KEPT), PhotoCleaner.inspect(webp).found)
        assertEquals(PhotoFormat.WEBP, PhotoCleaner.inspect(TestImages.everythingWebp()).format)
        fails(PHOTO_DAMAGED) { PhotoCleaner.clean(concat(TestImages.PNG_SIGNATURE, ByteArray(30))) }
        fails(PHOTO_DAMAGED) { PhotoCleaner.inspect(concat(ascii("RIFF"), bytes(4, 0, 0, 0), ascii("WEBP"))) }
    }

    @Test fun messagesAreTheAnnexTexts() {
        assertEquals("No se ha podido limpiar esta foto: el archivo está dañado o tiene un formato que no conocemos. No se ha guardado nada.", PHOTO_DAMAGED)
        assertEquals("Este formato no se admite. Usa una foto JPEG, PNG, WebP o HEIC.", PHOTO_UNSUPPORTED)
    }
}
