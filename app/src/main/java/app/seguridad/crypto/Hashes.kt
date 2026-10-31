package app.seguridad.crypto

import java.io.InputStream
import java.security.MessageDigest

fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02X".format(it) }

private val HEX_DIGITS = "0123456789ABCDEF".toCharArray()

/**
 * The SHA-1 of [password] in UTF-8, as uppercase hex in a new [CharArray] the caller wipes (what Have I Been Pwned
 * expects). CIFRADO-1 (audit 1.3.0): no `String` of the password or of its hash is made, and the UTF-8 copy and the
 * digest are wiped here. A lone surrogate is «?», as `String(password).toByteArray(UTF_8)` did, so the lookup gives the
 * same answer as before ([utf8Replacing]).
 */
fun sha1HexChars(password: CharArray): CharArray {
    val bytes = utf8Replacing(password)
    val digest = try {
        MessageDigest.getInstance("SHA-1").digest(bytes)
    } finally {
        bytes.fill(0)
    }
    try {
        val out = CharArray(digest.size * 2)
        for (i in digest.indices) {
            val b = digest[i].toInt() and 0xFF
            out[2 * i] = HEX_DIGITS[b shr 4]
            out[2 * i + 1] = HEX_DIGITS[b and 0x0F]
        }
        return out
    } finally {
        digest.fill(0)
    }
}

/**
 * SHA-256 of a stream, as lowercase hex (how download pages publish it). [check] is the caller's cancellation checkpoint
 * (COV1-2, audit 1.3.0: `ensureActive()` on Android), called before each 64 KiB read, so a file of several GB is not read
 * to the end after the screen is gone or another file was picked.
 */
fun sha256Hex(input: InputStream, check: () -> Unit = {}): String {
    val md = MessageDigest.getInstance("SHA-256")
    val buf = ByteArray(64 * 1024)
    while (true) {
        check()
        val n = input.read(buf)
        if (n < 0) break
        md.update(buf, 0, n)
    }
    return hex(md.digest()).lowercase()
}

/** Compares a published hash with ours, ignoring case, spaces and colons. */
fun sameHash(a: String, b: String): Boolean {
    val norm = { s: String -> s.filter { it.isLetterOrDigit() }.lowercase() }
    return norm(a).isNotEmpty() && norm(a) == norm(b)
}
