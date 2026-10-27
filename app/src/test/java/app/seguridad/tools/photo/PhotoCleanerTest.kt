package app.seguridad.tools.photo

import app.seguridad.tools.photo.TestImages.EOI
import app.seguridad.tools.photo.TestImages.SOI
import app.seguridad.tools.photo.TestImages.ascii
import app.seguridad.tools.photo.TestImages.baseline
import app.seguridad.tools.photo.TestImages.bytes
import app.seguridad.tools.photo.TestImages.concat
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
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
        val dirty = listOf(
            concat(SOI, TestImages.com("x"), baseline.body(), EOI),
            concat(SOI, TestImages.fullExif(), baseline.body(), EOI),
            concat(TestImages.plainJpeg(), ByteArray(8)),
            concat(TestImages.plainJpeg(), TestImages.plainJpeg()),
            concat(SOI, baseline.body()),
            ascii("GIF89a"),
        )
        for (d in dirty) fails(PHOTO_DAMAGED) { PhotoCleaner.verify(PhotoFormat.JPEG, d) }
    }

    @Test fun messagesAreTheAnnexTexts() {
        assertEquals("No se ha podido limpiar esta foto: el archivo está dañado o tiene un formato que no conocemos. No se ha guardado nada.", PHOTO_DAMAGED)
        assertEquals("Este formato no se admite. Usa una foto JPEG, PNG, WebP o HEIC.", PHOTO_UNSUPPORTED)
    }
}
