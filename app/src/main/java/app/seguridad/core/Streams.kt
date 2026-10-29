package app.seguridad.core

/**
 * Up to [limit] bytes of the stream (InputStream.readNBytes needs Android 13). With [expected], the size the provider
 * reports, between 1 and [limit], the bytes go straight into one array of that size, without a growing buffer and its
 * final copy (M-7: half the memory for a 50 MB photo). The report is not trusted: a shorter stream gives what there is
 * and a longer one goes on, still never past [limit]. Every array left behind (the first one once copied, the read
 * buffer, a buffer outgrown) is zeroed: what was read may be a secret, such as the picture of a 2FA QR.
 */
fun java.io.InputStream.readAtMost(limit: Int, expected: Long? = null): ByteArray {
    if (expected != null && expected in 1..limit.toLong()) {
        val exact = ByteArray(expected.toInt())
        var filled = 0
        while (filled < exact.size) {
            val n = read(exact, filled, exact.size - filled)
            if (n < 0) return exact.copyOf(filled).also { exact.fill(0) }
            filled += n
        }
        if (filled == limit) return exact
        val next = read()
        if (next < 0) return exact
        // The provider reported too little: the rest, as without a size.
        val out = GrowingBuffer(minOf(limit, exact.size * 2))
        out.write(exact, 0, exact.size)
        exact.fill(0)
        out.write(byteArrayOf(next.toByte()), 0, 1)
        return copyAtMost(out, limit)
    }
    return copyAtMost(GrowingBuffer(8192), limit)
}

private fun java.io.InputStream.copyAtMost(out: GrowingBuffer, limit: Int): ByteArray {
    val buf = ByteArray(8192)
    try {
        while (out.size < limit) {
            val n = read(buf, 0, minOf(buf.size, limit - out.size))
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return out.take()
    } finally {
        buf.fill(0)
        out.wipe()
    }
}

/** A ByteArrayOutputStream that zeroes each array it outgrows, and its own at the end ([wipe]). */
private class GrowingBuffer(initial: Int) {
    private var data = ByteArray(maxOf(initial, 16))
    var size = 0
        private set

    fun write(b: ByteArray, off: Int, len: Int) {
        if (size + len > data.size) {
            var cap = data.size
            while (cap < size + len) cap = if (cap > Int.MAX_VALUE / 2) Int.MAX_VALUE else cap * 2
            val bigger = data.copyOf(cap)
            data.fill(0)
            data = bigger
        }
        System.arraycopy(b, off, data, size, len)
        size += len
    }

    /** What was written, in an array of its own size. */
    fun take(): ByteArray = data.copyOf(size)

    fun wipe() {
        data.fill(0)
    }
}
