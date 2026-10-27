package app.seguridad.crypto

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.util.Base64

/**
 * What a Google Authenticator «Transfer accounts» QR carries. [batchIndex] counts from 0; a large export is split
 * into [batchSize] QR codes, each one parsed on its own. [skipped] says, for the user, which accounts were left out and why.
 */
data class MigrationResult(val imported: List<TotpSecret>, val skipped: List<String>, val batchIndex: Int, val batchSize: Int)

/** Longest otpauth-migration:// text read: Google's QR codes are far smaller; anything bigger is not one. */
const val MIGRATION_MAX_URI_CHARS = 64 * 1024

/** Most accounts read from one QR (Google puts about ten in each). */
const val MIGRATION_MAX_PARAMETERS = 100

/** Longest length-delimited field: secrets, names and each account's own message. */
const val MIGRATION_MAX_FIELD_BYTES = 1024

/** Longest batch Google could plausibly split an export into. */
private const val MIGRATION_MAX_BATCH = 1000

private class Malformed : Exception() {
    // Control flow only: no stack trace to fill in.
    override fun fillInStackTrace(): Throwable = this
}

/**
 * Minimal protobuf reader over untrusted bytes: varints of at most 10 bytes, length-delimited fields checked against
 * what is left and against [MIGRATION_MAX_FIELD_BYTES] before anything is copied, and unknown fields skipped. Groups
 * (wire types 3 and 4) and field number 0 are rejected. Every problem throws [Malformed]; nothing else escapes.
 */
private class ProtoReader(private val buf: ByteArray, private var pos: Int = 0, private val end: Int = buf.size) {
    val atEnd get() = pos >= end

    fun varint(): Long {
        var result = 0L
        for (i in 0 until 10) {
            if (pos >= end) throw Malformed()
            val b = buf[pos++].toInt() and 0xFF
            // The 10th byte holds only the 64th bit.
            if (i == 9 && b > 1) throw Malformed()
            result = result or ((b and 0x7F).toLong() shl (7 * i))
            if (b and 0x80 == 0) return result
        }
        throw Malformed()
    }

    /** (field number, wire type) of the next field. */
    fun tag(): Pair<Int, Int> {
        val t = varint()
        val field = t ushr 3
        if (field < 1 || field > 0x1FFFFFFF) throw Malformed()
        return field.toInt() to (t and 7).toInt()
    }

    /** Bounds of a length-delimited field, checked before use. */
    fun lengthDelimited(): Pair<Int, Int> {
        val len = varint()
        if (len < 0 || len > MIGRATION_MAX_FIELD_BYTES || len > end - pos) throw Malformed()
        val start = pos
        pos += len.toInt()
        return start to pos
    }

    fun bytes(): ByteArray = lengthDelimited().let { (s, e) -> buf.copyOfRange(s, e) }

    fun string(): String {
        val (s, e) = lengthDelimited()
        // Strict UTF-8: a malformed name means the payload is not what Google writes.
        val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        val text = try {
            decoder.decode(ByteBuffer.wrap(buf, s, e - s)).toString()
        } catch (x: CharacterCodingException) {
            throw Malformed()
        }
        // Control characters (newlines, NUL…) would only garble the names in the list.
        return text.filterNot { it.isISOControl() }
    }

    fun sub(): ProtoReader = lengthDelimited().let { (s, e) -> ProtoReader(buf, s, e) }

    fun skip(wire: Int) {
        when (wire) {
            0 -> varint()
            1 -> advance(8)
            2 -> lengthDelimited()
            5 -> advance(4)
            else -> throw Malformed()
        }
    }

    private fun advance(n: Int) {
        if (n > end - pos) throw Malformed()
        pos += n
    }

    fun expect(wire: Int, want: Int) {
        if (wire != want) throw Malformed()
    }
}

/** A varint field that must fit a non-negative Int (values Google writes are tiny enums and counts). */
private fun ProtoReader.smallInt(wire: Int, max: Int): Int {
    expect(wire, 0)
    val v = varint()
    if (v < 0 || v > max) throw Malformed()
    return v.toInt()
}

/** One OtpParameters message: a TOTP secret, or the reason it is left out. Depth is fixed: nothing inside is a message. */
private fun ProtoReader.otpParameters(): Pair<TotpSecret?, String?> {
    var secret = ByteArray(0)
    var name = ""
    var issuer = ""
    var algorithm = 0
    var digits = 0
    var type = 0
    try {
        while (!atEnd) {
            val (field, wire) = tag()
            when (field) {
                1 -> { expect(wire, 2); secret.fill(0); secret = bytes() }
                2 -> { expect(wire, 2); name = string() }
                3 -> { expect(wire, 2); issuer = string() }
                4 -> algorithm = smallInt(wire, Int.MAX_VALUE)
                5 -> digits = smallInt(wire, Int.MAX_VALUE)
                6 -> type = smallInt(wire, Int.MAX_VALUE)
                7 -> { expect(wire, 0); varint() } // HOTP counter: not used.
                else -> skip(wire)
            }
        }
        // «Issuer:cuenta» in the name, as in parseOtpAuth; the issuer field wins when present.
        val labelIssuer = otpName(name.substringBefore(':', ""))
        val account = otpName(name.substringAfter(':'))
        val finalIssuer = otpName(issuer).ifEmpty { labelIssuer }
        val who = listOf(finalIssuer, account).filter { it.isNotEmpty() }.joinToString(" · ").ifEmpty { "Sin nombre" }.take(80)
        val alg = when (algorithm) {
            0, 1 -> "SHA1" // 0 is "unspecified", which Google's own exports use for SHA-1.
            2 -> "SHA256"
            3 -> "SHA512"
            4 -> return null to "«$who»: usa MD5, que esta app no admite"
            else -> return null to "«$who»: algoritmo desconocido"
        }
        val nDigits = when (digits) {
            0, 1 -> 6
            2 -> 8
            else -> return null to "«$who»: número de dígitos desconocido"
        }
        when (type) {
            0, 2 -> Unit // TOTP (0 is "unspecified"; Google writes 2).
            1 -> return null to "«$who»: es HOTP (por contador), que esta app no admite"
            else -> return null to "«$who»: tipo de código desconocido"
        }
        if (secret.size < TOTP_MIN_KEY_BYTES) return null to "«$who»: la clave es demasiado corta (menos de ${TOTP_MIN_KEY_BYTES * 8} bits)"
        return TotpSecret(base32Encode(secret), issuer = finalIssuer, account = account, algorithm = alg, digits = nDigits, period = 30) to null
    } finally {
        secret.fill(0)
    }
}

/** Percent-decodes a query value; a raw '+' stays '+' (it is a Base64 character here, not a space). Null if an escape is broken. */
private fun percentDecode(text: String): String? {
    val out = StringBuilder(text.length)
    var i = 0
    while (i < text.length) {
        val c = text[i]
        if (c == '%') {
            if (i + 2 >= text.length) return null
            val hi = Character.digit(text[i + 1], 16)
            val lo = Character.digit(text[i + 2], 16)
            if (hi < 0 || lo < 0) return null
            val v = hi * 16 + lo
            if (v >= 0x80) return null // Base64 is ASCII.
            out.append(v.toChar())
            i += 3
        } else {
            out.append(c)
            i++
        }
    }
    return out.toString()
}

/**
 * Reads a Google Authenticator export (otpauth-migration://offline?data=…, protobuf MigrationPayload in Base64).
 * Null if it is not one, or is malformed or over the limits ([MIGRATION_MAX_URI_CHARS], [MIGRATION_MAX_PARAMETERS],
 * [MIGRATION_MAX_FIELD_BYTES]); never throws. HOTP, MD5 and keys under [TOTP_MIN_KEY_BYTES] go to [MigrationResult.skipped].
 */
fun parseOtpMigration(uri: String): MigrationResult? {
    if (uri.length > MIGRATION_MAX_URI_CHARS) return null
    val text = uri.trim()
    val prefix = "otpauth-migration://offline?"
    if (!text.regionMatches(0, prefix, 0, prefix.length, ignoreCase = true)) return null
    val data = text.substring(prefix.length).split('&').filter { it.substringBefore('=').equals("data", ignoreCase = true) }
    if (data.size != 1 || '=' !in data[0]) return null
    val b64 = percentDecode(data[0].substringAfter('=')) ?: return null
    val payload = try {
        Base64.getDecoder().decode(b64)
    } catch (e: IllegalArgumentException) {
        return null
    }
    return try {
        parsePayload(payload)
    } catch (e: Malformed) {
        null
    } catch (e: RuntimeException) {
        null // Defence in depth: the reader checks every bound, but untrusted input must never crash the editor.
    } finally {
        payload.fill(0)
    }
}

private fun parsePayload(payload: ByteArray): MigrationResult {
    val r = ProtoReader(payload)
    val imported = ArrayList<TotpSecret>()
    val skipped = ArrayList<String>()
    var batchSize = 0
    var batchIndex = 0
    var parameters = 0
    while (!r.atEnd) {
        val (field, wire) = r.tag()
        when (field) {
            1 -> {
                r.expect(wire, 2)
                if (++parameters > MIGRATION_MAX_PARAMETERS) throw Malformed()
                val (secret, reason) = r.sub().otpParameters()
                secret?.let(imported::add)
                reason?.let(skipped::add)
            }
            2 -> { r.expect(wire, 0); r.varint() } // version
            3 -> batchSize = r.smallInt(wire, MIGRATION_MAX_BATCH)
            4 -> batchIndex = r.smallInt(wire, MIGRATION_MAX_BATCH)
            5 -> { r.expect(wire, 0); r.varint() } // batch_id (int32; may be negative)
            else -> r.skip(wire)
        }
    }
    val size = batchSize.coerceAtLeast(1) // Absent (0) in single-QR exports.
    if (batchIndex >= size) throw Malformed()
    return MigrationResult(imported, skipped, batchIndex, size)
}
