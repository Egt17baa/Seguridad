package app.seguridad.tools.qr

import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QrImageSizeTest {
    @Test
    fun targetSizeKeepsBothSidesWithinTheLimit() {
        assertEquals(2048, MAX_QR_DECODE_SIDE)
        assertNull(qrTargetSize(2048, 2048))
        assertNull(qrTargetSize(1, 1))
        assertEquals(2048 to 1024, qrTargetSize(4096, 2048))
        assertEquals(1024 to 2048, qrTargetSize(2048, 4096))
        assertEquals(2048 to 68, qrTargetSize(3000, 100))
        assertEquals(40 to 2048, qrTargetSize(100, 5000))
        // A strip: the short side never drops to 0.
        assertEquals(2048 to 1, qrTargetSize(32768, 1))
        assertEquals(2048 to 1536, qrTargetSize(16320, 12240))
    }

    @Test
    fun sampleSizeIsTheSmallestPowerOfTwoThatFits() {
        assertEquals(1, qrSampleSize(2048, 2048))
        assertEquals(2, qrSampleSize(2049, 10))
        assertEquals(2, qrSampleSize(4096, 4096))
        // libjpeg rounds each side up: 4097 / 2 is 2049.
        assertEquals(4, qrSampleSize(4097, 1))
        assertEquals(16, qrSampleSize(32768, 32768))
    }

    @Test
    fun randomSizesStayWithinTheLimit() {
        val r = Random(7)
        repeat(5000) {
            val w = 1 + r.nextInt(32768)
            val h = 1 + r.nextInt(32768)
            val t = qrTargetSize(w, h)
            if (t == null) {
                assertTrue(w <= MAX_QR_DECODE_SIDE && h <= MAX_QR_DECODE_SIDE)
            } else {
                assertTrue("$w×$h → $t", t.first in 1..MAX_QR_DECODE_SIDE && t.second in 1..MAX_QR_DECODE_SIDE)
                assertTrue("$w×$h → $t", t.first == MAX_QR_DECODE_SIDE || t.second == MAX_QR_DECODE_SIDE)
            }
            val s = qrSampleSize(w, h)
            fun fits(s: Int) = (w + s - 1) / s <= MAX_QR_DECODE_SIDE && (h + s - 1) / s <= MAX_QR_DECODE_SIDE
            assertTrue(fits(s))
            assertTrue(s == 1 || !fits(s / 2))
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun aSizeThatIsNotPositiveIsRefused() {
        qrTargetSize(0, 10)
    }
}
