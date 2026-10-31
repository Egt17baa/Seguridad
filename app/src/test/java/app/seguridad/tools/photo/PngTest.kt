package app.seguridad.tools.photo

import app.seguridad.tools.photo.TestImages.PNG_SIGNATURE
import app.seguridad.tools.photo.TestImages.ascii
import app.seguridad.tools.photo.TestImages.bytes
import app.seguridad.tools.photo.TestImages.concat
import app.seguridad.tools.photo.TestImages.idats
import app.seguridad.tools.photo.TestImages.pngChunk
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteOrder
import java.util.Random

/** PNG: closed list of kept chunks with their CRC, the report, and hostile input. */
class PngTest {
    private fun damaged(bytes: ByteArray) {
        assertEquals(PHOTO_DAMAGED, assertThrows(PhotoFailure::class.java) { Png.inspect(bytes) }.message)
        assertEquals(PHOTO_DAMAGED, assertThrows(PhotoFailure::class.java) { Png.clean(bytes) }.message)
    }

    private fun png(vararg chunks: ByteArray) = concat(PNG_SIGNATURE, *chunks)

    @Test fun keepsOnlyTheListedChunks() {
        val out = Png.clean(TestImages.everythingPng())
        val expected = png(
            TestImages.ihdr(), TestImages.gama(), TestImages.iccp(), TestImages.plte(), TestImages.trns(),
            *idats().toTypedArray(), TestImages.iend(),
        )
        assertArrayEquals(expected, out)
        assertTrue(
            Png.inspect(TestImages.everythingPng()).found.containsAll(
                setOf(Found.XMP_COMMENTS, Found.AUTHOR_RIGHTS, Found.C2PA, Found.ICC_KEPT, Found.DATE_SOFTWARE, Found.OTHER),
            ),
        )
        PhotoCleaner.verify(PhotoFormat.PNG, out)
        assertEquals(setOf(Found.ICC_KEPT), Png.inspect(out).found)
    }

    @Test fun reportComesFromTheKeywords() {
        fun found(vararg c: ByteArray) = Png.inspect(png(TestImages.ihdr(), *c, *idats().toTypedArray(), TestImages.iend())).found
        assertEquals(setOf(Found.AUTHOR_RIGHTS), found(TestImages.text("Author", "x")))
        assertEquals(setOf(Found.AUTHOR_RIGHTS), found(TestImages.text("Copyright", "x")))
        assertEquals(setOf(Found.AUTHOR_RIGHTS), found(TestImages.ztxt("Author", "x")))
        assertEquals(setOf(Found.DATE_SOFTWARE), found(TestImages.text("Creation Time", "x")))
        assertEquals(setOf(Found.DATE_SOFTWARE), found(TestImages.itxt("Software", "x")))
        assertEquals(setOf(Found.XMP_COMMENTS), found(TestImages.ztxt("Comment", "x")))
        assertEquals(setOf(Found.DEVICE), found(TestImages.text("Source", "Aparato Inventado")))
        assertEquals(setOf(Found.OTHER), found(TestImages.text("Clave inventada", "x")))
        assertEquals(setOf(Found.DATE_SOFTWARE), found(pngChunk("tIME", bytes(0x07, 0xD1, 2, 3, 4, 5, 6))))
        assertEquals(setOf(Found.OTHER), found(pngChunk("pHYs", ByteArray(9))))
        assertEquals(setOf(Found.C2PA), found(pngChunk("caBX", ByteArray(20))))
        // Uncompressed XMP is read; compressed XMP is not inflated, so only the keyword counts.
        val xmp = TestImages.xmpPacket("xmlns:dc='http://purl.org/dc/elements/1.1/' dc:creator='Autora Inventada' tiff:Make='Marca'")
        assertEquals(setOf(Found.XMP_COMMENTS, Found.AUTHOR_RIGHTS, Found.DEVICE), found(TestImages.itxt("XML:com.adobe.xmp", xmp)))
        assertEquals(setOf(Found.XMP_COMMENTS), found(TestImages.itxt("XML:com.adobe.xmp", xmp, compressed = true)))
        assertEquals(setOf(Found.XMP_COMMENTS), found(TestImages.ztxt("XML:com.adobe.xmp", xmp)))
        // A keyword with no end inside 80 bytes, and a text chunk with no data at all.
        assertEquals(setOf(Found.OTHER), found(pngChunk("tEXt", ByteArray(100) { 'a'.code.toByte() })))
        assertEquals(setOf(Found.OTHER), found(pngChunk("iTXt")))
    }

    @Test fun badCrcFails() {
        val good = TestImages.everythingPng()
        // Flip a bit in the CRC of a kept chunk (the first IDAT) and of a dropped one (tEXt).
        for (marker in listOf("IDAT", "tEXt")) {
            val x = good.copyOf()
            val at = indexOf(x, ascii(marker))
            val len = ((x[at - 4].toInt() and 0xFF) shl 24) or ((x[at - 3].toInt() and 0xFF) shl 16) or ((x[at - 2].toInt() and 0xFF) shl 8) or (x[at - 1].toInt() and 0xFF)
            x[at + 4 + len + 3] = (x[at + 4 + len + 3].toInt() xor 1).toByte()
            damaged(x)
        }
        // Flip a data bit instead.
        val y = good.copyOf()
        y[indexOf(y, ascii("tEXt")) + 5] = 'Z'.code.toByte()
        damaged(y)
    }

    @Test fun chunkLengthPastTheEndFails() {
        val good = png(TestImages.ihdr(), *idats().toTypedArray(), TestImages.iend())
        val x = good.copyOf()
        val at = indexOf(x, ascii("IDAT")) - 4
        x[at] = 0x7F; x[at + 1] = 0xFF.toByte(); x[at + 2] = 0xFF.toByte(); x[at + 3] = 0xFF.toByte()
        damaged(x)
        x[at] = 0xFF.toByte() // above 2^31-1
        damaged(x)
        val y = good.copyOf()
        y[at + 3] = (y[at + 3] + 5).toByte() // a little longer than what is there
        damaged(y)
        damaged(good.copyOf(good.size - 1))
    }

    @Test fun exifInsidePngIsRead() {
        val c = png(TestImages.ihdr(), pngChunk("eXIf", TestImages.fullTiff(orientation = 6)), *idats().toTypedArray(), TestImages.iend())
        val r = Png.inspect(c)
        assertEquals(PhotoFormat.PNG, r.format)
        assertTrue(r.found.containsAll(setOf(Found.GPS, Found.DEVICE, Found.DATE_SOFTWARE, Found.THUMBNAIL)))
        assertEquals(40.42 to -3.7, r.gps)
        assertEquals(6, r.orientation)
        assertFalse(Found.GPS in Png.inspect(Png.clean(c)).found)
        // Little-endian TIFF too.
        val le = png(TestImages.ihdr(), pngChunk("eXIf", TestImages.fullTiff(order = ByteOrder.LITTLE_ENDIAN)), *idats().toTypedArray(), TestImages.iend())
        assertEquals(40.42 to -3.7, Png.inspect(le).gps)
        // An EXIF with only an orientation still counts as something dropped.
        val t = TestImages.Tiff()
        val only = png(TestImages.ihdr(), pngChunk("eXIf", t.build(listOf(t.short(0x0112, 3)))), *idats().toTypedArray(), TestImages.iend())
        assertEquals(setOf(Found.OTHER), Png.inspect(only).found)
        assertEquals(3, Png.inspect(only).orientation)
        // Broken TIFF: reported, never thrown.
        val broken = png(TestImages.ihdr(), pngChunk("eXIf", ascii("nada que leer")), *idats().toTypedArray(), TestImages.iend())
        assertEquals(setOf(Found.OTHER), Png.inspect(broken).found)
    }

    @Test fun plainPngIsUnchanged() {
        val p = TestImages.plainPng()
        assertArrayEquals(p, Png.clean(p))
        assertTrue(Png.inspect(p).found.isEmpty())
        assertEquals(1, Png.inspect(p).orientation)
    }

    @Test fun nothingSurvivesAfterIend() {
        val out = Png.clean(TestImages.everythingPng())
        assertEquals(0, out.size - (indexOf(out, ascii("IEND")) + 8))
        assertEquals(-1, indexOf(out, ascii("datos tras IEND")))
        assertTrue(Found.OTHER in Png.inspect(concat(TestImages.plainPng(), ByteArray(3))).found)
    }

    @Test fun cleanIsIdempotent() {
        val once = Png.clean(TestImages.everythingPng())
        assertArrayEquals(once, Png.clean(once))
        assertTrue(Png.isCleanOutput(once))
    }

    @Test fun brokenStructureFails() {
        val sig = PNG_SIGNATURE
        val idat = idats().toTypedArray()
        damaged(ByteArray(0))
        damaged(sig)
        damaged(concat(sig, TestImages.iend())) // IHDR not first
        damaged(png(TestImages.plte(), TestImages.ihdr(), *idat, TestImages.iend()))
        damaged(png(TestImages.ihdr(), TestImages.ihdr(), *idat, TestImages.iend()))
        damaged(png(pngChunk("IHDR", ByteArray(12)), *idat, TestImages.iend())) // short IHDR
        damaged(png(pngChunk("IHDR", concat(bytes(0, 0, 0, 0, 0, 0, 0, 1), bytes(8, 3, 0, 0, 0))), *idat, TestImages.iend())) // width 0
        damaged(png(TestImages.ihdr(), *idat)) // no IEND
        damaged(png(TestImages.ihdr(), TestImages.iend())) // no IDAT
        damaged(png(TestImages.ihdr(), *idat, pngChunk("IEND", bytes(1)), ByteArray(0))) // IEND with data
        damaged(png(TestImages.ihdr(), *idat, TestImages.plte(), TestImages.iend())) // PLTE after IDAT
        damaged(png(TestImages.ihdr(), TestImages.plte(), TestImages.plte(), *idat, TestImages.iend())) // twice
        // A chunk type that is not letters.
        val y = png(TestImages.ihdr(), *idat, TestImages.iend())
        y[indexOf(y, ascii("IEND"))] = '1'.code.toByte()
        damaged(y)
    }

    /** Kept chunks of a fixed size: the wrong size is damage, never bytes carried through. */
    @Test fun keptChunksHaveTheirSize() {
        fun withKept(c: ByteArray) = png(TestImages.ihdr(), c, *idats().toTypedArray(), TestImages.iend())
        val good = listOf(
            pngChunk("gAMA", ByteArray(4)), pngChunk("cHRM", ByteArray(32)), pngChunk("sRGB", ByteArray(1)),
            pngChunk("sBIT", ByteArray(1)), pngChunk("sBIT", ByteArray(4)),
            pngChunk("PLTE", ByteArray(3)), pngChunk("PLTE", ByteArray(768)), pngChunk("tRNS", ByteArray(1)), pngChunk("tRNS", ByteArray(256)),
        )
        for (c in good) { assertTrue(Png.isCleanOutput(withKept(c))); assertArrayEquals(withKept(c), Png.clean(withKept(c))) }
        val bad = listOf(
            pngChunk("gAMA", ByteArray(5)), pngChunk("gAMA", ByteArray(0)), pngChunk("cHRM", ByteArray(31)), pngChunk("cHRM", ByteArray(33)),
            pngChunk("sRGB", ByteArray(2)), pngChunk("sBIT", ByteArray(0)), pngChunk("sBIT", ByteArray(5)),
            pngChunk("PLTE", ByteArray(0)), pngChunk("PLTE", ByteArray(4)), pngChunk("PLTE", ByteArray(771)),
            pngChunk("tRNS", ByteArray(0)), pngChunk("tRNS", ByteArray(257)),
        )
        for ((i, c) in bad.withIndex()) {
            damaged(withKept(c))
            assertFalse("caso $i", Png.isCleanOutput(withKept(c)))
        }
    }

    private fun indexOf(hay: ByteArray, needle: ByteArray): Int {
        outer@ for (i in 0..hay.size - needle.size) {
            for (j in needle.indices) if (hay[i + j] != needle[j]) continue@outer
            return i
        }
        return -1
    }

    /** Random corruption: only PhotoFailure, and every output passes the structural check. */
    @Test fun corruptedInputNeverEscapesTheWhitelist() {
        val seeds = listOf(
            TestImages.everythingPng(),
            png(TestImages.ihdr(), pngChunk("eXIf", TestImages.fullTiff(orientation = 6)), TestImages.iccp(), TestImages.plte(), *idats().toTypedArray(), TestImages.iend()),
        )
        val rnd = Random(20261028)
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
                Png.inspect(y)
                val out = Png.clean(y)
                cleaned++
                assertTrue("iteración $i: ${Png.inspect(out).found}", (Png.inspect(out).found - Found.ICC_KEPT).isEmpty())
                assertTrue(Png.clean(out).contentEquals(out))
                assertTrue("iteración $i: estructura", Png.isCleanOutput(out))
                PhotoCleaner.verify(PhotoFormat.PNG, out)
                assertNotNull(PhotoCleaner.clean(y))
            } catch (e: PhotoFailure) {
                assertEquals(PHOTO_DAMAGED, e.message)
            }
        }
        assertTrue("casi todas fallan: $cleaned", cleaned > 100)
    }
}
