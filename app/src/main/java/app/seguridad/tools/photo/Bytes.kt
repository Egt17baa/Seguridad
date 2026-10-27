package app.seguridad.tools.photo

/** Bounds-checked byte comparisons and integer reads shared by the container readers. */
internal object Bytes {
    fun startsWith(b: ByteArray, from: Int, end: Int, prefix: ByteArray): Boolean {
        if (from < 0 || end > b.size || end - from < prefix.size) return false
        for (i in prefix.indices) if (b[from + i] != prefix[i]) return false
        return true
    }

    fun contains(b: ByteArray, from: Int, end: Int, needle: ByteArray): Boolean {
        val last = minOf(end, b.size) - needle.size
        var i = maxOf(from, 0)
        while (i <= last) {
            if (startsWith(b, i, i + needle.size, needle)) return true
            i++
        }
        return false
    }

    /** Unsigned 32-bit big-endian at [i]; the caller has checked that four bytes are there. */
    fun u32be(b: ByteArray, i: Int): Long =
        ((b[i].toLong() and 0xFF) shl 24) or ((b[i + 1].toLong() and 0xFF) shl 16) or
            ((b[i + 2].toLong() and 0xFF) shl 8) or (b[i + 3].toLong() and 0xFF)

    /** Unsigned 32-bit little-endian at [i]; the caller has checked that four bytes are there. */
    fun u32le(b: ByteArray, i: Int): Long =
        (b[i].toLong() and 0xFF) or ((b[i + 1].toLong() and 0xFF) shl 8) or
            ((b[i + 2].toLong() and 0xFF) shl 16) or ((b[i + 3].toLong() and 0xFF) shl 24)

    fun ascii(s: String) = s.toByteArray(Charsets.ISO_8859_1)
}
