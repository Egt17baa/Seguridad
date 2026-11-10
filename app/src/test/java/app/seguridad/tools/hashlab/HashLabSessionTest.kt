package app.seguridad.tools.hashlab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Branch review C, M-1 (the Task 2 minor): a wipe in the middle of a file's reading leaves nothing of that file, its
 * size, how much was read and its algorithm included, and the run going on can no longer write its progress.
 */
class HashLabSessionTest {
    @Test fun aWipeInTheMiddleOfAReadingLeavesNothingOfTheFile() {
        var stopped = 0
        val s = HashLabSession(onWipe = { stopped++ })
        s.algo = HashAlgo.BLAKE3_256
        s.source = "content://x/doc"
        val run = s.startFile()
        assertEquals(HashAlgo.BLAKE3_256, s.fileAlgo)
        assertTrue(s.sized(run, 4L shl 30))
        assertTrue(s.progress(run, 1L shl 20))
        assertEquals(4L shl 30, s.totalBytes)
        assertEquals(1L shl 20, s.readBytes)
        s.algo = HashAlgo.SHA512 // chosen while the file is read
        s.wipe()
        assertEquals(1, stopped)
        assertNull(s.source)
        assertNull(s.file.name)
        assertFalse(s.file.busy)
        assertEquals(0L, s.readBytes)
        assertEquals(-1L, s.totalBytes)
        assertEquals("The file's algorithm is the one chosen now", HashAlgo.SHA512, s.fileAlgo)
        // Late writes of the run that was going on are refused.
        assertFalse(s.progress(run, 2L shl 20))
        assertFalse(s.sized(run, 5))
        assertFalse(s.file.finish(run, "ab"))
        assertEquals(0L, s.readBytes)
        assertEquals(-1L, s.totalBytes)
        assertNull(s.file.hash)
        // The tab's choice is not a secret and stays.
        assertEquals(HashAlgo.SHA512, s.algo)
    }

    @Test fun aNewFileStartsFromZeroAndOnlyItsRunWrites() {
        val s = HashLabSession()
        val first = s.startFile()
        s.progress(first, 10)
        s.sized(first, 100)
        s.algo = HashAlgo.SHA3_256
        val second = s.startFile()
        assertEquals(0L, s.readBytes)
        assertEquals(-1L, s.totalBytes)
        assertEquals(HashAlgo.SHA3_256, s.fileAlgo)
        assertFalse(s.progress(first, 50))
        assertTrue(s.progress(second, 20))
        assertEquals(20L, s.readBytes)
    }

    /**
     * Re-review B, N-2: a reading of the old file, on its own thread, never leaves its progress on the new one. Before the
     * fix, [HashLabSession.progress] could pass the run check, [HashLabSession.startFile] reset the figure, and the old
     * reading then write it back.
     */
    @Test fun anOldReadingNeverWritesItsProgressOnTheNewFile() {
        val s = HashLabSession()
        repeat(2_000) { round ->
            val old = s.startFile()
            val go = java.util.concurrent.CountDownLatch(1)
            val reader = Thread {
                go.await()
                var read = 1L
                while (s.progress(old, read)) read++
            }
            reader.start()
            go.countDown()
            Thread.yield()
            val new = s.startFile()
            reader.join()
            assertEquals("round $round", 0L, s.readBytes)
            assertTrue(s.progress(new, 7))
            assertEquals(7L, s.readBytes)
        }
    }
}
