package app.seguridad.imports

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/** Characters the strict check decodes at a time: the only buffer it needs, whatever the size of the file. */
private const val CHECK_CHUNK_CHARS = 8 * 1024

/**
 * The text of an exported file: UTF-8 with or without BOM, or UTF-16 LE or BE with BOM (spec «Texto»). Null when the
 * bytes are not valid text in that encoding: a broken sequence, an overlong form, a surrogate written as UTF-8, a
 * value past U+10FFFF, a lone surrogate in UTF-16 or an odd number of UTF-16 bytes. Nothing is replaced with U+FFFD.
 *
 * The standard decoder checks the bytes first in a small buffer that is wiped afterwards ([CHECK_CHUNK_CHARS]), and
 * only valid bytes are turned into the String, in one step and with no intermediate copy of the whole text.
 */
fun decodeText(bytes: ByteArray): String? {
    val (charset, start) = when {
        bytes.startsWith(0xEF, 0xBB, 0xBF) -> Charsets.UTF_8 to 3
        bytes.startsWith(0xFF, 0xFE) -> Charsets.UTF_16LE to 2
        bytes.startsWith(0xFE, 0xFF) -> Charsets.UTF_16BE to 2
        else -> Charsets.UTF_8 to 0
    }
    if (!isValidText(bytes, start, charset)) return null
    return String(bytes, start, bytes.size - start, charset)
}

private fun ByteArray.startsWith(vararg prefix: Int): Boolean =
    size >= prefix.size && prefix.indices.all { this[it] == prefix[it].toByte() }

private fun isValidText(bytes: ByteArray, start: Int, charset: Charset): Boolean {
    val decoder = charset.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
    val input = ByteBuffer.wrap(bytes, start, bytes.size - start)
    val out = CharBuffer.allocate(CHECK_CHUNK_CHARS)
    try {
        while (true) {
            val r = decoder.decode(input, out, true)
            if (r.isError) return false
            out.clear()
            if (r.isUnderflow) break
        }
        while (true) {
            val r = decoder.flush(out)
            if (r.isError) return false
            out.clear()
            if (r.isUnderflow) break
        }
        return true
    } finally {
        // What was decoded may be a password from the file.
        out.array().fill('\u0000')
    }
}
