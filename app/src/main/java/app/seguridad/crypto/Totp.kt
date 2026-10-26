package app.seguridad.crypto

import kotlinx.serialization.Serializable
import java.net.URI
import java.net.URLDecoder
import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** A TOTP account (RFC 6238); stored inside vault entries. */
@Serializable
data class TotpSecret(
    val secret: String,
    val issuer: String = "",
    val account: String = "",
    val algorithm: String = "SHA1",
    val digits: Int = 6,
    val period: Int = 30,
)

private const val BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

/** RFC 4648 Base32, tolerant of spaces, dashes, lower case and missing padding (as authenticator apps show keys). */
fun base32Decode(text: String): ByteArray? {
    val clean = text.uppercase().filter { it != ' ' && it != '-' && it != '=' }
    if (clean.isEmpty() || clean.any { it !in BASE32 }) return null
    val out = java.io.ByteArrayOutputStream()
    var buffer = 0
    var bits = 0
    for (c in clean) {
        buffer = (buffer shl 5) or BASE32.indexOf(c)
        bits += 5
        if (bits >= 8) {
            out.write((buffer shr (bits - 8)) and 0xFF)
            bits -= 8
        }
    }
    return out.toByteArray()
}

/** RFC 4648 Base32, upper case and without padding (the way otpauth:// links and authenticator apps write keys). */
fun base32Encode(bytes: ByteArray): String {
    val out = StringBuilder((bytes.size * 8 + 4) / 5)
    var buffer = 0
    var bits = 0
    for (b in bytes) {
        buffer = (buffer shl 8) or (b.toInt() and 0xFF)
        bits += 8
        while (bits >= 5) {
            out.append(BASE32[(buffer shr (bits - 5)) and 0x1F])
            bits -= 5
        }
        buffer = buffer and ((1 shl bits) - 1) // Keep only the unused bits, so the Int never overflows.
    }
    if (bits > 0) out.append(BASE32[(buffer shl (5 - bits)) and 0x1F])
    return out.toString()
}

/** Shortest TOTP key accepted (80 bits, what RFC 4226 allows as a minimum); shorter ones break the HMAC. */
const val TOTP_MIN_KEY_BYTES = 10

/** The decoded key if it is a usable TOTP secret. */
fun totpKey(secret: String): ByteArray? = base32Decode(secret)?.takeIf { it.size >= TOTP_MIN_KEY_BYTES }

private fun hmacName(algorithm: String) = when (algorithm.uppercase()) {
    "SHA256" -> "HmacSHA256"
    "SHA512" -> "HmacSHA512"
    else -> "HmacSHA1"
}

/** HOTP (RFC 4226) with the given HMAC; TOTP feeds it the time step. */
fun hotp(key: ByteArray, counter: Long, digits: Int, algorithm: String = "SHA1"): String {
    val mac = Mac.getInstance(hmacName(algorithm)).apply { init(SecretKeySpec(key, "RAW")) }
    val h = mac.doFinal(ByteBuffer.allocate(8).putLong(counter).array())
    val offset = h.last().toInt() and 0x0F
    val binary = ((h[offset].toInt() and 0x7F) shl 24) or ((h[offset + 1].toInt() and 0xFF) shl 16) or
        ((h[offset + 2].toInt() and 0xFF) shl 8) or (h[offset + 3].toInt() and 0xFF)
    var mod = 1
    repeat(digits) { mod *= 10 }
    return (binary % mod).toString().padStart(digits, '0')
}

/** Current code and the seconds it stays valid. */
fun totpCode(s: TotpSecret, nowMillis: Long): Pair<String, Int>? {
    val key = totpKey(s.secret) ?: return null
    val seconds = nowMillis / 1000
    val step = s.period.coerceIn(10, 120)
    try {
        return hotp(key, seconds / step, s.digits.coerceIn(6, 8), s.algorithm) to (step - (seconds % step).toInt())
    } finally {
        key.fill(0)
    }
}

/** Parses an otpauth://totp/… link as QR codes carry it; null if it is not a usable TOTP link. */
fun parseOtpAuth(uri: String): TotpSecret? {
    val u = try {
        URI(uri.trim())
    } catch (e: Exception) {
        return null
    }
    if (!u.scheme.equals("otpauth", ignoreCase = true) || !u.host.equals("totp", ignoreCase = true)) return null

    // A scanned QR code is untrusted input. Malformed percent-escapes make URLDecoder throw;
    // treat them as an invalid QR instead of crashing the caller.
    return try {
        val query = (u.rawQuery ?: "").split('&').mapNotNull {
            val kv = it.split('=', limit = 2)
            if (kv.size == 2) kv[0].lowercase() to URLDecoder.decode(kv[1], "UTF-8") else null
        }.toMap()
        val secret = query["secret"]?.takeIf { totpKey(it) != null } ?: return null
        val label = URLDecoder.decode((u.rawPath ?: "").removePrefix("/"), "UTF-8")
        val labelIssuer = label.substringBefore(':', "").trim()
        val account = label.substringAfter(':').trim()
        TotpSecret(
            secret = secret.uppercase().filter { it in BASE32 },
            issuer = query["issuer"] ?: labelIssuer,
            account = account,
            algorithm = (query["algorithm"] ?: "SHA1").uppercase().takeIf { it in setOf("SHA1", "SHA256", "SHA512") } ?: return null,
            digits = query["digits"]?.toIntOrNull()?.takeIf { it in 6..8 } ?: 6,
            period = query["period"]?.toIntOrNull()?.takeIf { it in 10..120 } ?: 30,
        )
    } catch (e: Exception) {
        null
    }
}

/** Percent-encoding for otpauth:// labels and values: UTF-8, spaces as %20 (never '+', which [parseOtpAuth] reads as a space). */
private fun otpEncode(text: String) = java.net.URLEncoder.encode(text, "UTF-8").replace("+", "%20")

/**
 * The otpauth://totp/ link of this code (Key-Uri-Format), for showing as a QR to another app; [parseOtpAuth] reads it back
 * unchanged. The label is "Issuer:account", but a ':' in the issuer is left out of the label: the issuer parameter is the
 * one that counts, and the account is whatever follows the label's first ':'.
 */
fun TotpSecret.toOtpAuthUri(): String {
    val labelIssuer = issuer.replace(":", "")
    val label = when {
        labelIssuer.isNotEmpty() -> "$labelIssuer:$account"
        ':' in account -> ":$account"
        else -> account
    }
    val query = buildList {
        add("secret=" + secret.uppercase().filter { it in BASE32 })
        if (issuer.isNotEmpty()) add("issuer=" + otpEncode(issuer))
        add("algorithm=" + algorithm.uppercase())
        add("digits=$digits")
        add("period=$period")
    }
    // ':' between issuer and account stays literal, as Google's examples write it; everything else is encoded.
    return "otpauth://totp/" + label.split(':').joinToString(":") { otpEncode(it) } + "?" + query.joinToString("&")
}
