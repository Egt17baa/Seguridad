package app.seguridad.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test fun aReadThatFailsPartwayThrowsAndReturnsNothing() {
        val failing = object : InputStream() {
            var given = 0
            override fun read(): Int = throw java.io.IOException("gone")
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (given > 0) throw java.io.IOException("gone")
                given = minOf(len, 10)
                for (i in 0 until given) b[off + i] = 7
                return given
            }
        }
        try {
            failing.readAtMost(1000, expected = 100)
            org.junit.Assert.fail("A provider that fails must not give a half-read image")
        } catch (e: java.io.IOException) {
            // expected: the caller shows its own fixed text
        }
    }

    /** A [Trickle] that also remembers the largest array it was asked to fill: what [readAtMost] allocated. */
    private class Watching(data: ByteArray, chunk: Int) : InputStream() {
        private val inner = Trickle(data, chunk)
        var largest = 0
        override fun read(): Int = inner.read()
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            largest = maxOf(largest, b.size)
            return inner.read(b, off, len)
        }
    }

    // CR-8: a stale or lying size does not decide the first allocation: 50 MB reported for 10 KB never allocates 50 MB.
    @Test fun aReportedSizeLargerThanTheStreamIsNotAllocatedUpFront() {
        val limit = 50 * 1024 * 1024
        val data = bytes(10 * 1024)
        val s = Watching(data, 4096)
        assertArrayEquals(data, s.readAtMost(limit, expected = limit.toLong()))
        assertTrue("allocated ${s.largest}", s.largest <= 1024 * 1024)
    }

    @Test fun aReportedSizeSmallerThanTheStreamGrowsUpToTheLimitOnly() {
        val data = bytes(3 * 1024 * 1024 + 17)
        val s = Watching(data, 64 * 1024)
        assertArrayEquals(data, s.readAtMost(8 * 1024 * 1024, expected = 10 * 1024))
        assertTrue("allocated ${s.largest}", s.largest <= 8 * 1024 * 1024)
        // Over the limit: one byte over, never more, whatever was reported.
        val over = Watching(bytes(5 * 1024 * 1024), 64 * 1024)
        val got = over.readAtMost(2 * 1024 * 1024 + 1, expected = 1000)
        assertEquals(2 * 1024 * 1024 + 1, got.size)
        assertTrue("allocated ${over.largest}", over.largest <= 2 * 1024 * 1024 + 1)
    }

    @Test fun aTruthfulLargeSizeEndsInOneArrayOfThatSize() {
        // Past the first megabyte the report is used as a hint for the next step: no buffer bigger than the file.
        val data = bytes(3 * 1024 * 1024 + 5)
        val s = Watching(data, 64 * 1024)
        assertArrayEquals(data, s.readAtMost(50 * 1024 * 1024, expected = data.size.toLong()))
        assertEquals(data.size, s.largest)
    }
}
