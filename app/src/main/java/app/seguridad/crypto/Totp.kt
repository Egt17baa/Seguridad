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

/**
 * RFC 4648 Base32, tolerant of spaces, dashes, lower case and missing padding (as authenticator apps show keys). The
 * key is written straight into an array of its exact size (the whole bytes of 5 bits per character): no growing buffer
 * keeps a copy of it that nobody can clear (A-1), so the caller wipes the one array there is.
 */
fun base32Decode(text: String): ByteArray? {
    val clean = text.uppercase().filter { it != ' ' && it != '-' && it != '=' }
    if (clean.isEmpty() || clean.any { it !in BASE32 }) return null
    val out = ByteArray(clean.length * 5 / 8)
    var n = 0
    var buffer = 0
    var bits = 0
    for (c in clean) {
        buffer = (buffer shl 5) or BASE32.indexOf(c)
        bits += 5
        if (bits >= 8) {
            out[n++] = (buffer shr (bits - 8)).toByte()
            bits -= 8
        }
    }
    return out
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

/**
 * Shortest TOTP key accepted: 80 bits. That is below the 128 bits that RFC 4226 (R6) asks for at least, and the 160 it
 * recommends; it is accepted for compatibility, because many services issue keys of 16 Base32 characters.
 */
const val TOTP_MIN_KEY_BYTES = 10

/** The decoded key if it is a usable TOTP secret; a decoded key that is too short is cleared, not just dropped. */
fun totpKey(secret: String): ByteArray? = base32Decode(secret)?.let(::longEnoughOrWiped)

/** [key] if it has [TOTP_MIN_KEY_BYTES] at least; otherwise it is cleared and the answer is null. */
internal fun longEnoughOrWiped(key: ByteArray): ByteArray? {
    if (key.size >= TOTP_MIN_KEY_BYTES) return key
    key.fill(0)
    return null
}

/** If [secret] is a usable TOTP key, without keeping the decoded copy that the check needs: it is cleared before returning. */
internal fun isUsableTotpKey(secret: String): Boolean {
    val key = totpKey(secret) ?: return false
    key.fill(0)
    return true
}

/** The numbers of digits of a TOTP code that this app calculates (RFC 4226 §5.3: 6 at least, and possibly 7 and 8). */
private val TOTP_DIGITS = 6..8

/** The periods, in seconds, of a TOTP code that this app calculates (the app's own range; RFC 6238 suggests 30 s). */
private val TOTP_PERIODS = 10..120

/**
 * The HMAC of a stored algorithm name, in any case, or null if this app does not calculate it. An empty name is SHA-1,
 * what a code without a recorded algorithm has always been; any other unknown name is not turned into SHA-1.
 */
private fun hmacName(algorithm: String): String? = when (algorithm.uppercase()) {
    "SHA1", "" -> "HmacSHA1"
    "SHA256" -> "HmacSHA256"
    "SHA512" -> "HmacSHA512"
    else -> null
}

/** If this code uses an algorithm, a number of digits and a period that this app can calculate; [totpCode] gives null otherwise. */
fun TotpSecret.isSupported(): Boolean = hmacName(algorithm) != null && digits in TOTP_DIGITS && period in TOTP_PERIODS

/** Shown when a scanned or typed link is a valid 2FA code but uses a setting that [isSupported] refuses. */
const val TOTP_UNSUPPORTED_LINK = "Este código 2FA usa un algoritmo, un número de dígitos o un periodo que Clavero no admite."

/** Shown in the detail of a saved code that uses such a setting, instead of the code. */
const val TOTP_UNSUPPORTED_ENTRY = "Clavero no puede calcular este código: usa un ajuste que no admite."

/** Shown in the vault list, small, instead of the code of a saved code that uses such a setting (F4). */
const val TOTP_UNSUPPORTED_ROW = "2FA no admitido"

/**
 * HOTP (RFC 4226) with the given HMAC; TOTP feeds it the time step. An algorithm or a number of digits that [isSupported]
 * does not accept throws [IllegalArgumentException], instead of a code that no service would take.
 */
fun hotp(key: ByteArray, counter: Long, digits: Int, algorithm: String = "SHA1"): String {
    val macName = requireNotNull(hmacName(algorithm)) { "Unsupported HOTP algorithm" }
    require(digits in TOTP_DIGITS) { "Unsupported number of HOTP digits" }
    val mac = Mac.getInstance(macName).apply { init(SecretKeySpec(key, "RAW")) }
    val h = mac.doFinal(ByteBuffer.allocate(8).putLong(counter).array())
    val offset = h.last().toInt() and 0x0F
    val binary = ((h[offset].toInt() and 0x7F) shl 24) or ((h[offset + 1].toInt() and 0xFF) shl 16) or
        ((h[offset + 2].toInt() and 0xFF) shl 8) or (h[offset + 3].toInt() and 0xFF)
    var mod = 1
    repeat(digits) { mod *= 10 }
    return (binary % mod).toString().padStart(digits, '0')
}

/**
 * Current code and the seconds it stays valid; null if the key is not a usable one or the settings are not
 * [supported][isSupported]. The settings are never changed to make a code: the screens say why there is none.
 */
fun totpCode(s: TotpSecret, nowMillis: Long): Pair<String, Int>? {
    if (!s.isSupported()) return null
    val key = totpKey(s.secret) ?: return null
    val seconds = nowMillis / 1000
    try {
        return hotp(key, seconds / s.period, s.digits, s.algorithm) to (s.period - (seconds % s.period).toInt())
    } finally {
        key.fill(0)
    }
}

/**
 * Longest otpauth:// link read (a real one is under 300 characters), in UTF-16 units, and longest issuer or account
 * kept from it, in code points (so a cut never splits a surrogate pair).
 */
const val OTPAUTH_MAX_CHARS = 4096
const val OTPAUTH_MAX_NAME_CHARS = 256

/** [text] without control characters nor the ones that turn text around ([withoutBidiControls]), trimmed. */
private fun otpNameClean(text: String): String = withoutBidiControls(text.filterNot { it.isISOControl() }).trim()

/**
 * An issuer or account from a scanned link or a Google Authenticator export, as it will be shown: [otpNameClean], and
 * at most [OTPAUTH_MAX_NAME_CHARS] code points, cut at the end of a code point.
 */
internal fun otpName(text: String): String = takeCodePoints(otpNameClean(text), OTPAUTH_MAX_NAME_CHARS)

/** If [otpName] keeps all of [text] (it does not cut it). The importer refuses a code whose names would be cut. */
internal fun otpNameFits(text: String): Boolean = otpNameClean(text).let { it.codePointCount(0, it.length) <= OTPAUTH_MAX_NAME_CHARS }

/** What [readOtpAuth] makes of a text: the one answer the editor and the QR inspector decide on (F4). */
sealed interface OtpAuthRead {
    /**
     * The service and the account the link names (in that order, as they are shown), whether or not this app calculates
     * its settings; null when it is not a link with a usable key. For the QR inspector. Never the key.
     */
    val names: Pair<String, String>?

    /** Not an otpauth://totp/ link, or one without a usable key, or a broken one. */
    object NotUsable : OtpAuthRead {
        override val names: Pair<String, String>? get() = null
    }

    /**
     * A link with a usable key but an algorithm, a number of digits or a period that [isSupported] refuses. It keeps the
     * service and the account the link names (as [Usable] would have kept them), for [names].
     */
    class UnsupportedSettings(val issuer: String, val account: String) : OtpAuthRead {
        override val names: Pair<String, String> get() = issuer to account
    }

    /**
     * [namesCut]: the issuer or the account was longer than [OTPAUTH_MAX_NAME_CHARS] and [secret] keeps it cut. The
     * scanner accepts that; the importer lists such a code instead (a field is never cut in silence).
     */
    class Usable(val secret: TotpSecret, val namesCut: Boolean = false) : OtpAuthRead {
        override val names: Pair<String, String> get() = secret.issuer to secret.account
    }
}

/**
 * Parses an otpauth://totp/… link as QR codes carry it: [OtpAuthRead.Usable] with the code,
 * [OtpAuthRead.UnsupportedSettings] for a valid 2FA link whose settings this app does not calculate, and
 * [OtpAuthRead.NotUsable] for anything else. A setting is taken as written; the default (SHA1, 6 digits, 30 s) only
 * when the parameter is missing. A setting that is there and is not supported never becomes the default.
 */
fun readOtpAuth(uri: String): OtpAuthRead {
    if (uri.length > OTPAUTH_MAX_CHARS) return OtpAuthRead.NotUsable
    val u = try {
        URI(uri.trim())
    } catch (e: Exception) {
        return OtpAuthRead.NotUsable
    }
    if (!u.scheme.equals("otpauth", ignoreCase = true) || !u.host.equals("totp", ignoreCase = true)) return OtpAuthRead.NotUsable

    // A scanned QR code is untrusted input. Malformed percent-escapes make URLDecoder throw;
    // treat them as an invalid QR instead of crashing the caller.
    return try {
        val query = (u.rawQuery ?: "").split('&').mapNotNull {
            val kv = it.split('=', limit = 2)
            if (kv.size == 2) kv[0].lowercase() to URLDecoder.decode(kv[1], "UTF-8") else null
        }.toMap()
        val secret = query["secret"]?.takeIf { isUsableTotpKey(it) } ?: return OtpAuthRead.NotUsable
        val label = URLDecoder.decode((u.rawPath ?: "").removePrefix("/"), "UTF-8")
        val labelIssuer = label.substringBefore(':', "").trim()
        val rawIssuer = query["issuer"] ?: labelIssuer
        val rawAccount = label.substringAfter(':').trim()
        val issuer = otpName(rawIssuer)
        val account = otpName(rawAccount)
        // The default only when the parameter is missing; one that is there and is not a number is refused at once, and
        // one that is a number out of range, or an unknown algorithm, is refused by isSupported.
        val algorithm = (query["algorithm"] ?: "SHA1").uppercase()
        val digits = query["digits"]?.let { it.toIntOrNull() ?: return OtpAuthRead.UnsupportedSettings(issuer, account) } ?: 6
        val period = query["period"]?.let { it.toIntOrNull() ?: return OtpAuthRead.UnsupportedSettings(issuer, account) } ?: 30
        val code = TotpSecret(secret.uppercase().filter { it in BASE32 }, issuer = issuer, account = account, algorithm = algorithm, digits = digits, period = period)
        // isSupported reads an empty algorithm as SHA-1, as it must for a saved code; a link with «algorithm=» names none.
        if (algorithm.isNotEmpty() && code.isSupported()) {
            OtpAuthRead.Usable(code, namesCut = !otpNameFits(rawIssuer) || !otpNameFits(rawAccount))
        } else {
            OtpAuthRead.UnsupportedSettings(issuer, account)
        }
    } catch (e: Exception) {
        OtpAuthRead.NotUsable
    }
}

/** Percent-encoding for otpauth:// labels and values: UTF-8, spaces as %20 (never '+', which [readOtpAuth] reads as a space). */
internal fun otpEncode(text: String): String = java.net.URLEncoder.encode(text, "UTF-8").replace("+", "%20")

/**
 * The otpauth://totp/ link of this code (Key-Uri-Format), for showing as a QR to another app; [readOtpAuth] reads it back
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
        // A saved code without an algorithm is SHA-1 (isSupported); the link says so, because «algorithm=» alone is refused.
        add("algorithm=" + algorithm.uppercase().ifEmpty { "SHA1" })
        add("digits=$digits")
        add("period=$period")
    }
    // ':' between issuer and account stays literal, as Google's examples write it; everything else is encoded.
    return "otpauth://totp/" + label.split(':').joinToString(":") { otpEncode(it) } + "?" + query.joinToString("&")
}
