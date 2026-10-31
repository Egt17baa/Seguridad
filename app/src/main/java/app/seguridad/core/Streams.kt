package app.seguridad.core

/** The most [readAtMost] allocates before the stream has shown it really holds more (CR-8). */
private const val FIRST_BUFFER_BYTES = 1024 * 1024

/**
 * Up to [limit] bytes of the stream (InputStream.readNBytes needs Android 13). [expected], the size the provider
 * reports, is only a hint (CR-8): the first array is at most [FIRST_BUFFER_BYTES] (or [expected] if smaller), so a
 * stale or lying size of 50 MB for a 10 KB file never allocates 50 MB. Once the stream has filled that and still has
 * more, the next array is [expected] when it is larger (a true report then ends in one array of exactly its size, with
 * no final copy: M-7, about half the memory for a 50 MB photo), and after that it doubles, never past [limit]. A
 * shorter stream gives what there is, a longer one goes on, still never past [limit]. Every array left behind (one
 * outgrown, or the one copied from at the end) is zeroed: what was read may be a secret, such as the picture of a 2FA QR.
 */
fun java.io.InputStream.readAtMost(limit: Int, expected: Long? = null): ByteArray {
    if (limit <= 0) return ByteArray(0)
    val hint = expected?.takeIf { it in 1..limit.toLong() }?.toInt()
    val out = GrowingBuffer(minOf(hint ?: 8192, FIRST_BUFFER_BYTES, limit), hint, limit)
    try {
        while (out.size < limit) {
            if (out.full) {
                // Full: one byte tells whether there is more before a larger array is allocated for it.
                val next = read()
                if (next < 0) break
                out.grow()
                out.append(next.toByte())
                continue
            }
            val n = read(out.data, out.size, minOf(out.data.size, limit) - out.size)
            if (n < 0) break
            out.size += n
        }
        return out.take()
    } finally {
        out.wipe()
    }
}

/**
 * The array [readAtMost] reads straight into: it grows to [hint] first (if larger), then doubles, never past [limit],
 * and zeroes each array it outgrows. [take] hands over the array itself when it is exactly full, a copy otherwise.
 */
private class GrowingBuffer(initial: Int, private val hint: Int?, private val limit: Int) {
    var data = ByteArray(maxOf(initial, 1))
        private set
    var size = 0
    private var handedOver = false

    val full: Boolean get() = size == data.size

    fun grow() {
        val cap = data.size
        val next = when {
            hint != null && hint > cap -> hint
            cap > limit / 2 -> limit
            else -> cap * 2
        }.coerceAtMost(limit)
        val bigger = data.copyOf(next)
        data.fill(0)
        data = bigger
    }

    fun append(b: Byte) {
        data[size++] = b
    }

    /** What was read, in an array of its own size. */
    fun take(): ByteArray {
        if (size == data.size) {
            handedOver = true
            return data
        }
        return data.copyOf(size)
    }

    /** Zeroes the array unless [take] handed it over. */
    fun wipe() {
        if (!handedOver) data.fill(0)
    }
}
