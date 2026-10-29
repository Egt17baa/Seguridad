package app.seguridad.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

/** Reading a picked image (M-7): never more than the limit, whatever size the provider reports. */
class StreamsTest {
    private fun bytes(n: Int) = ByteArray(n) { (it * 7).toByte() }

    /** Hands out at most [chunk] bytes per read, as a provider's pipe may. */
    private class Trickle(private val data: ByteArray, private val chunk: Int) : InputStream() {
        private var pos = 0
        override fun read(): Int = if (pos < data.size) data[pos++].toInt() and 0xFF else -1
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            if (pos >= data.size) return -1
            val n = minOf(len, chunk, data.size - pos)
            System.arraycopy(data, pos, b, off, n)
            pos += n
            return n
        }
    }

    @Test fun withoutASizeItReadsUpToTheLimit() {
        assertArrayEquals(bytes(100), ByteArrayInputStream(bytes(100)).readAtMost(1000))
        assertEquals(1000, ByteArrayInputStream(bytes(5000)).readAtMost(1000).size)
    }

    @Test fun theReportedSizeIsReadIntoOneArray() {
        val data = bytes(70_000)
        assertArrayEquals(data, Trickle(data, 4096).readAtMost(100_000, expected = 70_000))
    }

    @Test fun aProviderThatReportsTooLittleStillGivesEverythingUpToTheLimit() {
        val data = bytes(50_000)
        assertArrayEquals(data, Trickle(data, 3000).readAtMost(100_000, expected = 10_000))
        // Lying to get past the limit does not work: one byte over is what the caller checks.
        assertEquals(1001, Trickle(bytes(5000), 700).readAtMost(1001, expected = 10).size)
    }

    @Test fun aProviderThatReportsTooMuchGivesWhatThereIs() {
        val data = bytes(1234)
        assertArrayEquals(data, Trickle(data, 100).readAtMost(100_000, expected = 9_000))
        assertArrayEquals(ByteArray(0), ByteArrayInputStream(ByteArray(0)).readAtMost(10, expected = 5))
    }

    @Test fun aReportedSizeOverTheLimitOrNegativeIsIgnored() {
        assertEquals(10, ByteArrayInputStream(bytes(50)).readAtMost(10, expected = 1_000_000).size)
        assertArrayEquals(bytes(50), ByteArrayInputStream(bytes(50)).readAtMost(100, expected = -1))
        assertArrayEquals(bytes(50), ByteArrayInputStream(bytes(50)).readAtMost(100, expected = 0))
    }
}
