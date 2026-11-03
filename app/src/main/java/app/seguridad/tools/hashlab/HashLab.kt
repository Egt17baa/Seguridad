package app.seguridad.tools.hashlab

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.util.Base64
import org.bouncycastle.crypto.Digest
import org.bouncycastle.crypto.digests.Blake2bDigest
import org.bouncycastle.crypto.digests.Blake3Digest
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.digests.SHA3Digest
import org.bouncycastle.crypto.digests.SHA512Digest
import org.bouncycastle.crypto.macs.HMac
import org.bouncycastle.crypto.params.KeyParameter

/**
 * The «Laboratorio de hashes» (1.4.0): digests, HMAC-SHA256 and the Base64 and hex codecs, all on Bouncy Castle's
 * standard primitives and `java.util.Base64`, with nothing of our own. Pure Kotlin, so `jvm-check` runs it.
 *
 * Every function that reads text from the user is bounded and never throws: a huge paste is refused (null), not
 * decoded, so it cannot freeze the screen or exhaust memory.
 */
enum class HashAlgo(val label: String) {
    SHA256("SHA-256"),
    SHA512("SHA-512"),
    SHA3_256("SHA3-256"),
    BLAKE2B_512("BLAKE2b-512"),
    BLAKE3_256("BLAKE3-256"),
    ;

    internal fun newDigest(): Digest = when (this) {
        SHA256 -> SHA256Digest()
        SHA512 -> SHA512Digest()
        SHA3_256 -> SHA3Digest(256)
        BLAKE2B_512 -> Blake2bDigest(512)
        BLAKE3_256 -> Blake3Digest(256)
    }
}

/** What [hexDecode] and [base64Decode] read at most (characters); the screen takes much less. */
const val MAX_CODEC_CHARS = 1_000_000

/** The longest text the screen takes to hash or to authenticate with HMAC (characters). */
const val MAX_TEXT_CHARS = 100_000

/** The longest text the Base64/hex tab takes: its output (hex of UTF-8) is drawn whole, so this one is smaller. */
const val MAX_CODEC_UI_CHARS = 10_000

/** The longest HMAC key the screen takes (characters; in hex, twice its bytes). */
const val MAX_KEY_CHARS = 4096

/** The longest «huella esperada» read: a SHA-512 in hex with a colon between bytes is 191 characters. */
private const val MAX_EXPECTED_CHARS = 512

private const val CHUNK = 64 * 1024

/**
 * The digest of [input], read in 64 KiB chunks. [check] is the caller's cancellation checkpoint (`ensureActive()` on
 * Android), called before each read, so a file of several GB stops soon after the screen is gone or another is picked.
 * The caller closes [input].
 */
fun digest(algo: HashAlgo, input: InputStream, check: () -> Unit = {}): ByteArray {
    val d = algo.newDigest()
    val buf = ByteArray(CHUNK)
    while (true) {
        check()
        val n = input.read(buf)
        if (n < 0) break
        d.update(buf, 0, n)
    }
    return finish(d)
}

fun digest(algo: HashAlgo, bytes: ByteArray): ByteArray {
    val d = algo.newDigest()
    var off = 0
    while (off < bytes.size) {
        val n = minOf(CHUNK, bytes.size - off)
        d.update(bytes, off, n)
        off += n
    }
    return finish(d)
}

private fun finish(d: Digest): ByteArray = ByteArray(d.digestSize).also { d.doFinal(it, 0) }

/** HMAC-SHA256 (RFC 2104). Bouncy Castle's `HMac` takes an empty key, which `javax.crypto.Mac` refuses. */
fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
    val mac = HMac(SHA256Digest())
    mac.init(KeyParameter(key))
    mac.update(data, 0, data.size)
    return ByteArray(mac.macSize).also { mac.doFinal(it, 0) }
}

/** Equal digests, in constant time for equal lengths. */
fun sameDigest(a: ByteArray, b: ByteArray): Boolean = MessageDigest.isEqual(a, b)

/** [text] as UTF-8, the bytes that are hashed. */
fun textBytes(text: String): ByteArray = text.toByteArray(Charsets.UTF_8)

fun hexEncode(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

fun base64Encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

/** [bytes] as text if they are valid UTF-8, or null (the screen then shows them as hex). */
fun utf8OrNull(bytes: ByteArray): String? = try {
    Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes))
        .toString()
} catch (e: CharacterCodingException) {
    null
}

private fun isSeparator(c: Char) = c == ' ' || c == ':' || c == '\n' || c == '\r' || c == '\t'

private fun hexValue(c: Char): Int = when (c) {
    in '0'..'9' -> c - '0'
    in 'a'..'f' -> c - 'a' + 10
    in 'A'..'F' -> c - 'A' + 10
    else -> -1
}

/**
 * Hex to bytes, in any case, with spaces, colons and line breaks ignored. Null for an odd count of digits, any other
 * character, or more than [MAX_CODEC_CHARS] characters. Never throws.
 */
fun hexDecode(text: String): ByteArray? {
    if (text.length > MAX_CODEC_CHARS) return null
    val digits = StringBuilder(text.length)
    for (c in text) if (!isSeparator(c)) digits.append(c)
    if (digits.length % 2 != 0) return null
    val out = ByteArray(digits.length / 2)
    for (i in out.indices) {
        val hi = hexValue(digits[2 * i])
        val lo = hexValue(digits[2 * i + 1])
        if (hi < 0 || lo < 0) return null
        out[i] = ((hi shl 4) or lo).toByte()
    }
    return out
}

/**
 * Base64 to bytes: the standard or the URL-safe alphabet, with or without padding, white space ignored. Null for any
 * other character, a wrong padding, a length no encoder makes, or more than [MAX_CODEC_CHARS] characters. Never throws.
 */
fun base64Decode(text: String): ByteArray? {
    if (text.length > MAX_CODEC_CHARS) return null
    val s = StringBuilder(text.length)
    for (c in text) if (!(c == ' ' || c == '\n' || c == '\r' || c == '\t')) s.append(c)
    var end = s.length
    while (end > 0 && s[end - 1] == '=') end--
    val padding = s.length - end
    if (padding > 2 || (padding > 0 && s.length % 4 != 0)) return null
    if (end % 4 == 1) return null
    for (i in 0 until end) {
        val c = s[i]
        val ok = c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == '+' || c == '/' || c == '-' || c == '_'
        if (!ok) return null
        if (c == '-') s[i] = '+' else if (c == '_') s[i] = '/'
    }
    return try {
        Base64.getDecoder().decode(s.substring(0, end))
    } catch (e: IllegalArgumentException) {
        null
    }
}

/**
 * A pasted «huella esperada» as bytes: hex (any case, with spaces or colons) or Base64, and only if it is 32 bytes
 * (SHA-256, SHA3-256, BLAKE3-256) or 64 (SHA-512, BLAKE2b-512). Text made only of hex digits and separators is read as
 * hex and nothing else, so a hex hash with a digit missing is refused and not taken for Base64. Null otherwise; never throws.
 */
fun parseExpected(text: String): ByteArray? {
    if (text.length > MAX_EXPECTED_CHARS) return null
    val allHex = text.all { isSeparator(it) || hexValue(it) >= 0 }
    val bytes = if (allHex) hexDecode(text) else base64Decode(text)
    return bytes?.takeIf { it.size == 32 || it.size == 64 }
}
