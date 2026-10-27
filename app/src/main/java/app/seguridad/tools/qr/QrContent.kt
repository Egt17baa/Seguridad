package app.seguridad.tools.qr

import app.seguridad.crypto.pk.CARD_TEXT_PREFIX
import app.seguridad.crypto.pk.MESSAGE_PREFIX
import app.seguridad.crypto.pk.SIGNATURE_PREFIX
import app.seguridad.tools.QR_TOO_BIG
import java.io.ByteArrayOutputStream
import java.util.Locale

/** Largest QR text analysed, in UTF-8 bytes; checked before anything is read (untrusted input). A QR holds less. */
const val MAX_QR_TEXT_BYTES = 4096

/**
 * What a QR text is, with the fields the screen shows. Nothing here opens, connects or pays: the screen only shows and
 * copies. The strings are as the QR carries them (control and bidi characters included): the screen shows them through
 * [visibleText].
 */
sealed interface QrKind {
    /** [host] is lower-case without the trailing dot, [unicodeHost] its Unicode form (the same for a plain name). */
    data class Link(
        val uri: String,
        val scheme: String,
        val host: String?,
        val unicodeHost: String?,
        val port: Int?,
        val signals: List<Signal>,
    ) : QrKind

    /** [security] is «nopass» (open) or the type in capitals («WPA», «WEP»…). */
    data class Wifi(val ssid: String, val security: String, val password: String?, val hidden: Boolean) : QrKind
    data class Sepa(val name: String, val iban: String, val amount: String?, val reference: String?) : QrKind
    data class Crypto(val scheme: String, val address: String, val amount: String?) : QrKind
    data class Contact(val name: String, val phones: List<String>, val emails: List<String>) : QrKind
    data class Sms(val number: String, val body: String) : QrKind
    data class Phone(val number: String) : QrKind
    data class Email(val to: String, val subject: String, val body: String) : QrKind
    data class Geo(val lat: String, val lon: String) : QrKind
    data class SeguridadCard(val text: String) : QrKind
    data class SeguridadReceived(val text: String) : QrKind
    data class OtpAuth(val text: String) : QrKind
    data class PlainText(val text: String) : QrKind
}

/**
 * Decides what [text] is. Throws [IllegalArgumentException] ([QR_TOO_BIG]) if it is over [MAX_QR_TEXT_BYTES] in UTF-8,
 * and nothing else: a malformed Wi-Fi, payment, contact or link falls back to [QrKind.PlainText] (or a [QrKind.Link]
 * with the fields that could be read).
 */
fun classifyQr(text: String): QrKind {
    // A char is at least one byte, so a longer string is too big without encoding it.
    if (text.length > MAX_QR_TEXT_BYTES || text.toByteArray(Charsets.UTF_8).size > MAX_QR_TEXT_BYTES) {
        throw IllegalArgumentException(QR_TOO_BIG)
    }
    return try {
        classifyQrUnguarded(text)
    } catch (e: Exception) {
        // Second line of defence: the parsers below are written not to throw (a fuzz test checks it).
        QrKind.PlainText(text)
    }
}

/** [classifyQr] without the guard, for the tests that check the parsers themselves never throw. */
internal fun classifyQrUnguarded(text: String): QrKind {
    val t = text.trim()
    return otpAuth(t) ?: seguridadAtStart(t) ?: wifi(t) ?: sepa(t) ?: vcard(t) ?: mecard(t) ?: matmsg(t) ?: sms(t) ?: phone(t) ?:
        email(t) ?: geo(t) ?: crypto(t) ?: link(t) ?: seguridadInside(t) ?: QrKind.PlainText(text)
}

// ---- Seguridad's own texts ----

private fun otpAuth(t: String): QrKind? =
    if (t.startsWithIgnoreCase("otpauth://") || t.startsWithIgnoreCase("otpauth-migration://")) QrKind.OtpAuth(t) else null

/** A text that starts with «SGK1:» is a card: its Base45 body can carry «SGM1:» or «SGS1:» by chance. */
private fun seguridadAtStart(t: String): QrKind? = if (t.startsWith(CARD_TEXT_PREFIX)) QrKind.SeguridadCard(t) else null

/**
 * A prefix further in (a signed text carries its «SGS1:» line last): the **last** prefix decides, as the app's own
 * reader does. After the link check, so a web address that happens to contain «SGM1:» stays a link.
 */
private fun seguridadInside(t: String): QrKind? {
    val signed = t.lastIndexOf(SIGNATURE_PREFIX)
    val message = t.lastIndexOf(MESSAGE_PREFIX)
    val card = t.lastIndexOf(CARD_TEXT_PREFIX)
    return when {
        signed >= 0 && signed > message && signed > card -> QrKind.SeguridadReceived(t)
        message >= 0 && message > card -> QrKind.SeguridadReceived(t)
        card >= 0 -> QrKind.SeguridadCard(t)
        else -> null
    }
}

// ---- Wi-Fi ----

private fun wifi(t: String): QrKind? {
    if (!t.startsWithIgnoreCase("WIFI:")) return null
    val fields = splitFields(t.substring(5))
    fun field(key: String) = fields.firstOrNull { it.first.equals(key, ignoreCase = true) }?.second
    val ssid = field("S")?.let(::unquote)?.takeIf { it.isNotEmpty() } ?: return null
    val type = field("T")?.trim().orEmpty()
    val security = if (type.isEmpty() || type.equals("nopass", ignoreCase = true)) "nopass" else type.uppercase(Locale.ROOT)
    val password = field("P")?.let(::unquote)?.takeIf { it.isNotEmpty() }
    return QrKind.Wifi(ssid, security, password, field("H")?.trim().equals("true", ignoreCase = true))
}

/** A value in double quotes (the format's way to say «text, not hexadecimal») without them. */
private fun unquote(v: String): String = if (v.length >= 2 && v.startsWith('"') && v.endsWith('"')) v.substring(1, v.length - 1) else v

// ---- Payments ----

/** An EPC «BCD» code (the European Payments Council's SEPA QR): lines BCD, version, charset, SCT, BIC, name, IBAN, amount, purpose, reference, text. */
private fun sepa(t: String): QrKind? {
    if (!t.startsWith("BCD")) return null
    val lines = t.split(LINE_END)
    if (lines.size < 7 || lines[0].trim() != "BCD" || !lines[3].trim().startsWith("SCT")) return null
    val iban = lines[6].trim().ifEmpty { return null }
    fun line(i: Int) = lines.getOrNull(i)?.trim()?.ifEmpty { null }
    return QrKind.Sepa(lines[5].trim(), iban, line(7), line(9) ?: line(10))
}

private val CRYPTO_SCHEMES = setOf("bitcoin", "ethereum", "lightning")

private fun crypto(t: String): QrKind? {
    val scheme = schemeOf(t)?.takeIf { it in CRYPTO_SCHEMES } ?: return null
    val rest = t.substring(scheme.length + 1).removePrefix("//")
    val path = percentDecode(rest.substringBefore('?')).trim()
    val query = queryParams(rest.substringAfter('?', ""))
    if (scheme == "ethereum") return ethereum(path, query)
    if (path.isEmpty()) return null
    return QrKind.Crypto(scheme, path, query["amount"]?.trim()?.ifEmpty { null })
}

/**
 * An EIP-681 request: «ethereum:[pay-]target[@chain][/function]?query». A token transfer
 * («0xTOKEN@1/transfer?address=0xRECIPIENT&uint256=…») is paid to the `address` in the query, not to the contract in
 * front, so that is the one shown, and no amount: `uint256` is in the token's smallest unit, whose decimals are not
 * known here. A plain payment's `value` is in wei, and says so.
 */
private fun ethereum(path: String, query: Map<String, String>): QrKind? {
    val target = path.removePrefix("pay-")
    val function = target.substringAfter('/', "").trim()
    val recipient = query["address"]?.trim()?.ifEmpty { null }
    if (function.equals("transfer", ignoreCase = true) && recipient != null) return QrKind.Crypto("ethereum", recipient, null)
    val address = target.substringBefore('@').substringBefore('/').trim().ifEmpty { return null }
    val wei = query["value"]?.trim()?.ifEmpty { null }
    return QrKind.Crypto("ethereum", address, wei?.let { "$it wei" })
}

// ---- Contacts and messages ----

private fun vcard(t: String): QrKind? {
    if (!t.startsWithIgnoreCase("BEGIN:VCARD")) return null
    // Unfold: a line that starts with a space or a tab continues the one before.
    val lines = ArrayList<String>()
    for (l in t.split(LINE_END)) {
        if ((l.startsWith(" ") || l.startsWith("\t")) && lines.isNotEmpty()) lines[lines.size - 1] = lines.last() + l.substring(1) else lines += l
    }
    var fn = ""
    var n = ""
    val phones = ArrayList<String>()
    val emails = ArrayList<String>()
    for (l in lines) {
        val colon = l.indexOf(':')
        if (colon < 0) continue
        val name = l.substring(0, colon).substringBefore(';').substringAfterLast('.').trim().uppercase(Locale.ROOT)
        val value = l.substring(colon + 1)
        when (name) {
            "FN" -> if (fn.isEmpty()) fn = unescape(value, vcard = true).trim()
            "N" -> if (n.isEmpty()) {
                val parts = value.split(';').map { unescape(it, vcard = true).trim() }
                n = listOf(parts.getOrNull(1).orEmpty(), parts[0]).filter { it.isNotEmpty() }.joinToString(" ")
            }
            "TEL" -> unescape(value, vcard = true).trim().removePrefix("tel:").trim().takeIf { it.isNotEmpty() }?.let(phones::add)
            "EMAIL" -> unescape(value, vcard = true).trim().removePrefix("mailto:").trim().takeIf { it.isNotEmpty() }?.let(emails::add)
        }
    }
    return contactOrNull(fn.ifEmpty { n }, phones, emails)
}

private fun mecard(t: String): QrKind? {
    if (!t.startsWithIgnoreCase("MECARD:")) return null
    val fields = splitFields(t.substring(7))
    // N is «Family,Given».
    val name = fields.firstOrNull { it.first.equals("N", ignoreCase = true) }?.second.orEmpty().let {
        if (',' in it) "${it.substringAfter(',').trim()} ${it.substringBefore(',').trim()}".trim() else it.trim()
    }
    fun all(key: String) = fields.filter { it.first.equals(key, ignoreCase = true) }.map { it.second.trim() }.filter { it.isNotEmpty() }
    return contactOrNull(name, all("TEL"), all("EMAIL"))
}

private fun contactOrNull(name: String, phones: List<String>, emails: List<String>): QrKind? =
    if (name.isEmpty() && phones.isEmpty() && emails.isEmpty()) null else QrKind.Contact(name, phones, emails)

private fun sms(t: String): QrKind? {
    for (p in listOf("smsto:", "mmsto:")) {
        if (t.startsWithIgnoreCase(p)) {
            val rest = t.substring(p.length)
            val number = rest.substringBefore(':').trim().ifEmpty { return null }
            return QrKind.Sms(number, rest.substringAfter(':', ""))
        }
    }
    for (p in listOf("sms:", "mms:")) {
        if (t.startsWithIgnoreCase(p)) {
            val rest = t.substring(p.length)
            val number = percentDecode(rest.substringBefore('?').substringBefore(';')).trim().ifEmpty { return null }
            return QrKind.Sms(number, queryParams(rest.substringAfter('?', ""))["body"].orEmpty())
        }
    }
    return null
}

private fun phone(t: String): QrKind? {
    if (!t.startsWithIgnoreCase("tel:")) return null
    return QrKind.Phone(percentDecode(t.substring(4)).trim().ifEmpty { return null })
}

private fun email(t: String): QrKind? {
    if (!t.startsWithIgnoreCase("mailto:")) return null
    val rest = t.substring(7)
    val query = queryParams(rest.substringAfter('?', ""))
    val to = percentDecode(rest.substringBefore('?')).trim().ifEmpty { query["to"]?.trim().orEmpty() }.ifEmpty { return null }
    return QrKind.Email(to, query["subject"].orEmpty(), query["body"].orEmpty())
}

private fun matmsg(t: String): QrKind? {
    if (!t.startsWithIgnoreCase("MATMSG:")) return null
    val fields = splitFields(t.substring(7))
    fun field(key: String) = fields.firstOrNull { it.first.equals(key, ignoreCase = true) }?.second.orEmpty()
    return QrKind.Email(field("TO").trim().ifEmpty { return null }, field("SUB"), field("BODY"))
}

private val COORDINATE = Regex("[+-]?\\d+(\\.\\d+)?")

private fun geo(t: String): QrKind? {
    if (!t.startsWithIgnoreCase("geo:")) return null
    val parts = t.substring(4).substringBefore(';').substringBefore('?').split(',').map { it.trim() }
    if (parts.size < 2 || !COORDINATE.matches(parts[0]) || !COORDINATE.matches(parts[1])) return null
    if (parts[0].toDouble() !in -90.0..90.0 || parts[1].toDouble() !in -180.0..180.0) return null
    return QrKind.Geo(parts[0], parts[1])
}

// ---- Links ----

private fun link(t: String): QrKind? {
    val p = parseLink(t)
    if (p.scheme.isNotEmpty() && (p.scheme in LINK_SCHEMES || p.hierarchical)) {
        return QrKind.Link(t, p.scheme, p.host, p.unicodeHost, p.port, signalsOf(p, t))
    }
    // «www.banco.es» with no scheme: a phone opens it as a web address, so the signals are those of «https://» + text.
    // The scheme is shown as the QR gives it: none.
    if (t.length > 4 && t.startsWithIgnoreCase("www.") && t.none { it.isWhitespace() }) {
        val w = parseLink("https://$t")
        return QrKind.Link(t, "", w.host, w.unicodeHost, w.port, signalsOf(w, t))
    }
    return null
}

// ---- Helpers ----

private val LINE_END = Regex("\r\n|\n|\r")

private fun String.startsWithIgnoreCase(prefix: String) = regionMatches(0, prefix, 0, prefix.length, ignoreCase = true)

/**
 * The «KEY:value» fields of a «;»-separated list as WIFI:, MECARD: and MATMSG: write them: «\» escapes the next
 * character (so «\;», «\,», «\:» and «\\» are literal), the key ends at the first «:», and the value is unescaped.
 */
internal fun splitFields(s: String): List<Pair<String, String>> {
    val out = ArrayList<Pair<String, String>>()
    val raw = StringBuilder()
    fun flush() {
        val r = raw.toString()
        raw.setLength(0)
        val colon = r.indexOf(':')
        if (colon >= 0) out += r.substring(0, colon).trim() to unescape(r.substring(colon + 1), vcard = false)
    }
    var i = 0
    while (i < s.length) {
        val c = s[i]
        if (c == '\\' && i + 1 < s.length) {
            raw.append(c).append(s[i + 1])
            i += 2
        } else {
            if (c == ';') flush() else raw.append(c)
            i++
        }
    }
    flush()
    return out
}

/** [s] with «\x» turned into «x» (a lone final «\» stays); in a vCard, «\n» is a line break. */
private fun unescape(s: String, vcard: Boolean): String {
    if ('\\' !in s) return s
    val sb = StringBuilder(s.length)
    var i = 0
    while (i < s.length) {
        val c = s[i]
        if (c == '\\' && i + 1 < s.length) {
            val next = s[i + 1]
            sb.append(if (vcard && (next == 'n' || next == 'N')) '\n' else next)
            i += 2
        } else {
            sb.append(c)
            i++
        }
    }
    return sb.toString()
}

/** «a=1&b=2» as a map with the keys in lower-case and the values percent-decoded (the first of a repeated key wins). */
private fun queryParams(query: String): Map<String, String> {
    val out = LinkedHashMap<String, String>()
    for (pair in query.split('&')) {
        if (pair.isEmpty()) continue
        val key = percentDecode(pair.substringBefore('=')).lowercase(Locale.ROOT)
        if (key !in out) out[key] = percentDecode(pair.substringAfter('=', ""))
    }
    return out
}

/** [s] with the valid «%XX» sequences decoded as UTF-8; a malformed one stays as written. Never throws. */
internal fun percentDecode(s: String): String {
    if ('%' !in s) return s
    val sb = StringBuilder(s.length)
    val bytes = ByteArrayOutputStream()
    fun flush() {
        if (bytes.size() > 0) {
            sb.append(String(bytes.toByteArray(), Charsets.UTF_8))
            bytes.reset()
        }
    }
    var i = 0
    while (i < s.length) {
        val c = s[i]
        val hi = if (c == '%' && i + 2 < s.length) hexValue(s[i + 1]) else -1
        val lo = if (hi >= 0) hexValue(s[i + 2]) else -1
        if (hi >= 0 && lo >= 0) {
            bytes.write(hi * 16 + lo)
            i += 3
        } else {
            flush()
            sb.append(c)
            i++
        }
    }
    flush()
    return sb.toString()
}

private fun hexValue(c: Char): Int = when (c) {
    in '0'..'9' -> c - '0'
    in 'a'..'f' -> c - 'a' + 10
    in 'A'..'F' -> c - 'A' + 10
    else -> -1
}
