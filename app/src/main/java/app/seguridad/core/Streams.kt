package app.seguridad.core

/**
 * Up to [limit] bytes of the stream (InputStream.readNBytes needs Android 13). With [expected], the size the provider
 * reports, between 1 and [limit], the bytes go straight into one array of that size, without a growing buffer and its
 * final copy (M-7: half the memory for a 50 MB photo). The report is not trusted: a shorter stream gives what there is
 * and a longer one goes on, still never past [limit].
 */
fun java.io.InputStream.readAtMost(limit: Int, expected: Long? = null): ByteArray {
    if (expected != null && expected in 1..limit.toLong()) {
        val exact = ByteArray(expected.toInt())
        var filled = 0
        while (filled < exact.size) {
            val n = read(exact, filled, exact.size - filled)
            if (n < 0) return exact.copyOf(filled)
            filled += n
        }
        if (filled == limit) return exact
        val next = read()
        if (next < 0) return exact
        // The provider reported too little: the rest, as without a size.
        val out = java.io.ByteArrayOutputStream(minOf(limit, exact.size * 2))
        out.write(exact)
        out.write(next)
        copyAtMost(out, limit)
        return out.toByteArray()
    }
    val out = java.io.ByteArrayOutputStream()
    copyAtMost(out, limit)
    return out.toByteArray()
}

private fun java.io.InputStream.copyAtMost(out: java.io.ByteArrayOutputStream, limit: Int) {
    val buf = ByteArray(8192)
    while (out.size() < limit) {
        val n = read(buf, 0, minOf(buf.size, limit - out.size()))
        if (n < 0) break
        out.write(buf, 0, n)
    }
}
