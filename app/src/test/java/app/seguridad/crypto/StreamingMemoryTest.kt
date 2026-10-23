package app.seguridad.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.security.MessageDigest
import kotlin.concurrent.thread

/** A large file made up on the fly: [size] bytes of a repeating pattern, never held in memory. */
private class PatternStream(private val size: Long) : InputStream() {
    private var pos = 0L
    override fun read(): Int = if (pos >= size) -1 else ((pos++ * 31) and 0xFF).toInt()
    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (pos >= size) return -1
        val n = minOf(len.toLong(), size - pos).toInt()
        for (i in 0 until n) b[off + i] = (((pos + i) * 31) and 0xFF).toByte()
        pos += n
        return n
    }
}

private class DigestSink : OutputStream() {
    val digest: MessageDigest = MessageDigest.getInstance("SHA-256")
    var count = 0L
    override fun write(b: Int) {
        digest.update(b.toByte())
        count++
    }
    override fun write(b: ByteArray, off: Int, len: Int) {
        digest.update(b, off, len)
        count += len
    }
}

class StreamingMemoryTest {
    /**
     * A file bigger than the whole test heap goes through encryption and decryption piped together:
     * it only works if neither side ever loads the file into memory.
     */
    @Test fun `files larger than the available memory are encrypted and decrypted in fixed-size records`() {
        val size = Runtime.getRuntime().maxMemory() + 64L * 1024 * 1024
        assertTrue("file must exceed the heap", size > Runtime.getRuntime().maxMemory())
        val key = parseRawKey(newRawKeyHex())!!
        val pipeIn = PipedInputStream(1024 * 1024)
        val pipeOut = PipedOutputStream(pipeIn)
        var failure: Throwable? = null
        val writer = thread {
            try {
                pipeOut.use { encryptStream(PatternStream(size), it, "video.mp4", Secret.RawKey(key.copyOf())) }
            } catch (t: Throwable) {
                failure = t
            }
        }
        val sink = DigestSink()
        val name = decryptStream(pipeIn, sink, Secret.RawKey(key))
        writer.join()
        failure?.let { throw it }
        assertEquals("video.mp4", name)
        assertEquals(size, sink.count)
        val expected = MessageDigest.getInstance("SHA-256").also { d -> PatternStream(size).use { s -> val b = ByteArray(1 shl 16); while (true) { val n = s.read(b); if (n < 0) break; d.update(b, 0, n) } } }
        assertArrayEquals(expected.digest(), sink.digest.digest())
    }
}
