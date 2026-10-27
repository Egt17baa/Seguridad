package app.seguridad.tools.photo

import app.seguridad.tools.photo.TestImages.ALPHA_FLAG
import app.seguridad.tools.photo.TestImages.ANIM_FLAG
import app.seguridad.tools.photo.TestImages.EXIF_FLAG
import app.seguridad.tools.photo.TestImages.ICC_FLAG
import app.seguridad.tools.photo.TestImages.VP8L_DATA
import app.seguridad.tools.photo.TestImages.VP8_DATA
import app.seguridad.tools.photo.TestImages.XMP_FLAG
import app.seguridad.tools.photo.TestImages.anmf
import app.seguridad.tools.photo.TestImages.ascii
import app.seguridad.tools.photo.TestImages.bytes
import app.seguridad.tools.photo.TestImages.concat
import app.seguridad.tools.photo.TestImages.riff
import app.seguridad.tools.photo.TestImages.riffChunk
import app.seguridad.tools.photo.TestImages.vp8x
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

/** WebP: closed list of kept chunks, the VP8X flags, the RIFF size, and hostile input. */
class WebpTest {
    private fun damaged(bytes: ByteArray) {
        assertEquals(PHOTO_DAMAGED, assertThrows(PhotoFailure::class.java) { Webp.inspect(bytes) }.message)
        assertEquals(PHOTO_DAMAGED, assertThrows(PhotoFailure::class.java) { Webp.clean(bytes) }.message)
    }

    private val icc = riffChunk("ICCP", ascii("perfil de color inventado"))
    private val vp8 = riffChunk("VP8 ", VP8_DATA)
    private val vp8l = riffChunk("VP8L", VP8L_DATA)

    private fun le32At(b: ByteArray, i: Int) = (b[i].toLong() and 0xFF) or ((b[i + 1].toLong() and 0xFF) shl 8) or ((b[i + 2].toLong() and 0xFF) shl 16) or ((b[i + 3].toLong() and 0xFF) shl 24)

    @Test fun extendedClearsExifAndXmpBits() {
        val input = TestImages.everythingWebp()
        val out = Webp.clean(input)
        val expected = riff(vp8x(ICC_FLAG), icc, vp8)
        assertArrayEquals(expected, out)
        // RIFF size: 4 ("WEBP") + the sum of the chunks with even padding, exact.
        val sum = 18 + (8 + 25 + 1) + (8 + VP8_DATA.size)
        assertEquals((4 + sum).toLong(), le32At(out, 4))
        assertEquals(out.size - 8L, le32At(out, 4))
        assertEquals(Found.ICC_KEPT, Webp.inspect(out).found.single())
        val r = Webp.inspect(input)
        assertEquals(PhotoFormat.WEBP, r.format)
        assertTrue(r.found.containsAll(setOf(Found.GPS, Found.DEVICE, Found.AUTHOR_RIGHTS, Found.XMP_COMMENTS, Found.ICC_KEPT, Found.DATE_SOFTWARE)))
        assertEquals(40.42 to -3.7, r.gps)
        PhotoCleaner.verify(PhotoFormat.WEBP, out)
    }

    @Test fun vp8xFlagsAreSanitised() {
        // The alpha flag stays, the animation flag stays, reserved bits and bytes go.
        val x = riff(vp8x(ALPHA_FLAG or EXIF_FLAG or 0xC1), riffChunk("ALPH", bytes(0, 1, 2)), vp8)
        val raw = x.copyOf(); raw[12 + 8 + 1] = 7 // reserved byte
        val out = Webp.clean(raw)
        assertArrayEquals(riff(vp8x(ALPHA_FLAG), riffChunk("ALPH", bytes(0, 1, 2)), vp8), out)
        assertTrue(Found.OTHER in Webp.inspect(raw).found)
    }

    @Test fun animationKeepsFrames() {
        val anim = riffChunk("ANIM", bytes(255, 255, 255, 255, 0, 0))
        val frames = arrayOf(anmf(vp8), anmf(vp8l))
        val a = riff(vp8x(ANIM_FLAG or ALPHA_FLAG), anim, *frames)
        assertArrayEquals(a, Webp.clean(a))
        assertTrue(Webp.inspect(a).found.isEmpty())
        // A hidden chunk inside a frame is dropped, reported, and the sizes are recomputed.
        val dirty = riff(vp8x(ANIM_FLAG), anim, anmf(riffChunk("ALPH", bytes(1, 2, 3)), vp8, riffChunk("SECR", ascii("secreto"))), anmf(vp8l))
        val out = Webp.clean(dirty)
        assertArrayEquals(riff(vp8x(ANIM_FLAG), anim, anmf(riffChunk("ALPH", bytes(1, 2, 3)), vp8), anmf(vp8l)), out)
        assertEquals(setOf(Found.OTHER), Webp.inspect(dirty).found)
        assertEquals(-1, indexOf(out, ascii("secreto")))
        assertEquals(out.size - 8L, le32At(out, 4))
    }

    @Test fun simpleLossyIsUnchanged() {
        val s = riff(vp8)
        assertArrayEquals(s, Webp.clean(s))
        assertTrue(Webp.inspect(s).found.isEmpty())
        val l = riff(vp8l)
        assertArrayEquals(l, Webp.clean(l))
        assertTrue(Webp.isCleanOutput(s) && Webp.isCleanOutput(l))
    }

    @Test fun oddChunkPadding() {
        assertEquals(1, VP8L_DATA.size % 2)
        val s = riff(vp8l)
        assertEquals(12 + 8 + VP8L_DATA.size + 1, s.size)
        assertEquals(0, s[s.size - 1].toInt())
        assertArrayEquals(s, Webp.clean(s))
        // A dropped odd chunk in between: padding respected when reading, none left when writing.
        val x = riff(vp8x(XMP_FLAG), riffChunk("XMP ", ascii("abc")), riffChunk("ZZZZ", bytes(1)), vp8)
        assertArrayEquals(riff(vp8x(0), vp8), Webp.clean(x))
        // A non-zero padding byte is hidden data: dropped and reported.
        val padded = s.copyOf(); padded[padded.size - 1] = 0x41
        assertArrayEquals(s, Webp.clean(padded))
        assertTrue(Found.OTHER in Webp.inspect(padded).found)
        // A missing final pad byte is damage.
        val short = s.copyOf(s.size - 1)
        damaged(short)
    }

    @Test fun riffSizeLiesFails() {
        val s = riff(vp8)
        val bigger = s.copyOf(); bigger[4] = (bigger[4] + 2).toByte()
        damaged(bigger)
        damaged(s.copyOf(s.size - 2))
        val odd = s.copyOf(); odd[4] = (odd[4] - 1).toByte()
        damaged(odd)
        val huge = s.copyOf(); huge[7] = 0xFF.toByte()
        damaged(huge)
        // A chunk size past the end of the RIFF.
        val c = s.copyOf(); c[12 + 4] = 0x7F
        damaged(c)
        val c2 = s.copyOf(); c2[12 + 7] = 0xFF.toByte()
        damaged(c2)
        // Bytes after the RIFF are dropped and reported.
        val tail = concat(s, ascii("datos tras RIFF"))
        assertArrayEquals(s, Webp.clean(tail))
        assertTrue(Found.OTHER in Webp.inspect(tail).found)
    }

    @Test fun exifXmpAndUnknownChunksAreReported() {
        fun found(vararg c: ByteArray) = Webp.inspect(riff(vp8x(0), *c, vp8)).found
        assertEquals(setOf(Found.OTHER), found(riffChunk("ZZZZ", bytes(1, 2))))
        assertEquals(setOf(Found.C2PA), found(riffChunk("C2PA", ByteArray(20))))
        assertEquals(setOf(Found.GPS, Found.DEVICE, Found.DATE_SOFTWARE, Found.THUMBNAIL), found(riffChunk("EXIF", TestImages.fullTiff())))
        // The «Exif\0\0» prefix some writers add is accepted.
        assertEquals(setOf(Found.GPS, Found.DEVICE, Found.DATE_SOFTWARE, Found.THUMBNAIL), found(riffChunk("EXIF", concat(ascii("Exif"), bytes(0, 0), TestImages.fullTiff()))))
        assertEquals(setOf(Found.OTHER), found(riffChunk("EXIF", ascii("nada"))))
        assertEquals(setOf(Found.XMP_COMMENTS, Found.AUTHOR_RIGHTS), found(riffChunk("XMP ", ascii(TestImages.xmpPacket("xmlns:dc='http://purl.org/dc/elements/1.1/' dc:rights='Derechos Inventados'")))))
    }

    @Test fun brokenStructureFails() {
        damaged(ByteArray(0))
        damaged(ascii("RIFF"))
        damaged(riff()) // no image
        damaged(riff(riffChunk("EXIF", ByteArray(4)))) // no image
        damaged(riff(vp8, vp8)) // two images
        damaged(riff(vp8, vp8x(0))) // VP8X not first
        damaged(riff(vp8x(0), vp8x(0), vp8))
        damaged(riff(riffChunk("VP8X", ByteArray(9)), vp8)) // short VP8X
        damaged(riff(icc, vp8)) // ICCP without VP8X
        damaged(riff(vp8x(ICC_FLAG), vp8, icc)) // ICCP after the image
        damaged(riff(vp8x(ICC_FLAG), icc, icc, vp8))
        damaged(riff(vp8x(ANIM_FLAG), anmf(vp8))) // frames without ANIM
        damaged(riff(vp8x(ANIM_FLAG), riffChunk("ANIM", ByteArray(6)), anmf(vp8), vp8))
        damaged(riff(vp8x(ANIM_FLAG), riffChunk("ANIM", ByteArray(6)), riffChunk("ANMF", ByteArray(10)))) // short frame
        damaged(riff(vp8x(ANIM_FLAG), riffChunk("ANIM", ByteArray(6)), anmf()))
        damaged(riff(vp8x(ANIM_FLAG), riffChunk("ANIM", ByteArray(6)), riffChunk("ANMF", concat(ByteArray(16), bytes(1, 2, 3))))) // sub-chunk fragment
        damaged(concat(ascii("RIFF"), bytes(4, 0, 0, 0), ascii("WAVE")))
    }

    private fun indexOf(hay: ByteArray, needle: ByteArray): Int {
        outer@ for (i in 0..hay.size - needle.size) {
            for (j in needle.indices) if (hay[i + j] != needle[j]) continue@outer
            return i
        }
        return -1
    }

    @Test fun cleanIsIdempotent() {
        for (x in listOf(
            TestImages.everythingWebp(),
            riff(vp8x(ANIM_FLAG), riffChunk("ANIM", ByteArray(6)), anmf(vp8, riffChunk("ZZZZ", bytes(1))), anmf(riffChunk("ALPH", bytes(1)), vp8)),
            concat(riff(vp8), ascii("cola")),
        )) {
            val once = Webp.clean(x)
            assertArrayEquals(once, Webp.clean(once))
            assertTrue(Webp.isCleanOutput(once))
        }
    }

    /** Random corruption: only PhotoFailure, and every output passes the structural check. */
    @Test fun corruptedInputNeverEscapesTheWhitelist() {
        val seeds = listOf(
            TestImages.everythingWebp(),
            riff(vp8x(ANIM_FLAG or EXIF_FLAG), riffChunk("ANIM", ByteArray(6)), anmf(vp8, riffChunk("ZZZZ", bytes(1))), anmf(vp8l), riffChunk("EXIF", TestImages.fullTiff())),
        )
        val rnd = Random(20261029)
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
                Webp.inspect(y)
                val out = Webp.clean(y)
                cleaned++
                assertTrue("iteración $i: ${Webp.inspect(out).found}", (Webp.inspect(out).found - Found.ICC_KEPT).isEmpty())
                assertTrue(Webp.clean(out).contentEquals(out))
                assertTrue("iteración $i: estructura", Webp.isCleanOutput(out))
                PhotoCleaner.verify(PhotoFormat.WEBP, out)
                assertNotNull(PhotoCleaner.clean(y))
            } catch (e: PhotoFailure) {
                assertEquals(PHOTO_DAMAGED, e.message)
            }
        }
        assertTrue("casi todas fallan: $cleaned", cleaned > 100)
    }
}
