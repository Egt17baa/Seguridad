package app.seguridad.crypto

import java.io.InputStream
import java.security.MessageDigest

fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02X".format(it) }

fun sha1Hex(password: CharArray): String {
    val bytes = String(password).toByteArray(Charsets.UTF_8)
    try {
        return hex(MessageDigest.getInstance("SHA-1").digest(bytes))
    } finally {
        bytes.fill(0)
    }
}

/** SHA-256 of a stream, as lowercase hex (how download pages publish it). */
fun sha256Hex(input: InputStream): String {
    val md = MessageDigest.getInstance("SHA-256")
    val buf = ByteArray(64 * 1024)
    while (true) {
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
