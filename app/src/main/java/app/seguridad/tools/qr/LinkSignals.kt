package app.seguridad.tools.qr

import java.net.IDN
import java.text.Normalizer
import java.util.Locale

/**
 * Typical tricks in a link, each shown with a sentence that explains it. No signal is not «safe»: the screen says so.
 * The order is the order they are listed in.
 */
enum class Signal { MIXED_SCRIPTS, PUNYCODE, IP_HOST, PLAIN_HTTP, USERINFO, ODD_PORT, SHORTENER, MANY_LEVELS, DANGEROUS_SCHEME, HIDDEN_CHARS }

/** Well-known link shorteners (a short list written here; the destination of a short link is only known by opening it). */
val SHORTENERS = setOf("bit.ly", "t.co", "tinyurl.com", "goo.gl", "ow.ly", "is.gd", "buff.ly", "cutt.ly", "rebrand.ly", "shorturl.at", "t.ly")

private val DANGEROUS_SCHEMES = setOf("javascript", "data", "file", "intent")

/** Schemes whose «scheme:» alone, with no «//», is already a link (a browser or the system opens them). */
internal val LINK_SCHEMES = setOf("http", "https", "ftp", "ftps", "ws", "wss") + DANGEROUS_SCHEMES

/** Schemes a browser reads with the host after any number of «/» or «\», so «http:evil.com» goes to evil.com. */
private val SPECIAL_SCHEMES = setOf("http", "https", "ftp", "ws", "wss")

private val DEFAULT_PORTS = mapOf("http" to 80, "https" to 443, "ftp" to 21, "ftps" to 990, "ws" to 80, "wss" to 443)

/** More than this many subdomain levels in front of the registered name and the ending is [Signal.MANY_LEVELS]. */
private const val MAX_SUBDOMAINS = 4

/**
 * What [parseLink] reads out of a link the way a browser would: [host] in the form the browser connects to (ASCII,
 * lower-case, without one trailing dot; a numeric host as the dotted IPv4 it means), [unicodeHost] the form it is
 * shown in, and what is needed for the signals.
 */
internal class ParsedLink(
    val scheme: String,
    /** Right after «scheme:» comes «//». */
    val hierarchical: Boolean,
    val host: String?,
    val unicodeHost: String?,
    val port: Int?,
    val oddPort: Boolean,
    val hasUserInfo: Boolean,
    /** The host is an IP address: IPv6 in brackets, or a number a browser reads as IPv4. */
    val isIp: Boolean = false,
    /** The host, once percent-decoded, holds characters [hasHiddenChars] finds (a browser drops some of them). */
    val hostHidden: Boolean = false,
)

private val NOT_A_LINK = ParsedLink("", false, null, null, null, false, false)

/**
 * Reads [uri] without throwing, whatever it holds. It does not use `java.net.URI`: that rejects spaces, backslashes,
 * underscores and non-ASCII names, and where it does parse it can disagree with the browser that will really open the
 * link. This follows the browser (WHATWG URL) on what matters for the signals: tabs and line breaks inside are
 * dropped, «\» counts as «/» in web links, the user info ends at the **last** «@» of the authority, the port starts at
 * the first «:» after the host, and the host is percent-decoded and then mapped like a browser does (UTS #46: full-width
 * letters become ASCII, invisible characters vanish, non-ASCII labels become `xn--`).
 */
internal fun parseLink(uri: String): ParsedLink {
    val u = uri.filterNot { it == '\t' || it == '\n' || it == '\r' }.trim { it <= ' ' || it.isWhitespace() }
    val scheme = schemeOf(u) ?: return NOT_A_LINK
    val rest = u.substring(scheme.length + 1)
    val hierarchical = rest.startsWith("//")
    val special = scheme in SPECIAL_SCHEMES
    val afterSlashes = when {
        special -> rest.dropWhile { it == '/' || it == '\\' }
        hierarchical -> rest.substring(2)
        else -> return ParsedLink(scheme, false, null, null, null, false, false)
    }
    val authority = afterSlashes.takeWhile { !(it == '/' || it == '?' || it == '#' || (special && it == '\\')) }
    val at = authority.lastIndexOf('@')
    val hostPort = authority.substring(at + 1)

    val rawHost: String
    var portText: String? = null
    if (hostPort.startsWith("[")) {
        val close = hostPort.indexOf(']')
        if (close < 0) {
            rawHost = hostPort
        } else {
            rawHost = hostPort.substring(0, close + 1)
            val after = hostPort.substring(close + 1)
            if (after.startsWith(":")) portText = after.substring(1)
        }
    } else {
        val colon = hostPort.indexOf(':')
        if (colon >= 0) {
            rawHost = hostPort.substring(0, colon)
            portText = hostPort.substring(colon + 1)
        } else {
            rawHost = hostPort
        }
    }

    val digits = portText?.takeIf { it.isNotEmpty() && it.all { c -> c in '0'..'9' } }
    val port = digits?.toIntOrNull()
    val oddPort = digits != null && port != DEFAULT_PORTS[scheme]

    val decoded = percentDecode(rawHost)
    val hostHidden = hasHiddenChars(decoded, allowEmojiVariation = false)
    val host: String?
    val isIp: Boolean
    if (rawHost.startsWith("[")) {
        host = decoded.lowercase(Locale.ROOT).ifEmpty { null }
        isIp = true
    } else {
        val mapped = mapHost(decoded)
        isIp = isIpHost(mapped)
        host = (if (isIp) canonicalIpv4(mapped) ?: mapped else mapped).ifEmpty { null }
    }
    val unicodeHost = host?.let { if (isIp) it else toUnicodeHost(it) }
    return ParsedLink(scheme, hierarchical, host, unicodeHost, port, oddPort, at >= 0, isIp, hostHidden)
}

/** The scheme of [text], lower-case, if it starts with a valid «scheme:»; null otherwise (a drive letter included). */
internal fun schemeOf(text: String): String? {
    val colon = text.indexOf(':')
    if (colon < 2) return null
    val ok = text[0].let { it in 'a'..'z' || it in 'A'..'Z' } &&
        (1 until colon).all { i -> text[i].let { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '+' || it == '-' || it == '.' } }
    return if (ok) text.substring(0, colon).lowercase(Locale.ROOT) else null
}

/**
 * The host as a browser connects to it: the dots written as ideographic or full-width full stops are dots, one trailing
 * dot goes, and each label is mapped (UTS #46 / nameprep: NFKC, case folding, invisible characters dropped) and
 * encoded as `xn--` if it is not ASCII. A label the encoder rejects is still normalised, and stays Unicode.
 */
private fun mapHost(decoded: String): String {
    val dotted = decoded.map { if (it == '。' || it == '．' || it == '｡') '.' else it }.joinToString("").removeSuffix(".")
    return dotted.split('.').joinToString(".") { label ->
        if (label.all { it.code < 128 }) label.lowercase(Locale.ROOT) else mapLabel(label)
    }
}

/**
 * A non-ASCII label as a browser maps it. Java's [IDN] is IDNA2003, which turns «ß» into «ss» and «ς» into «σ»; a
 * browser (UTS #46, non-transitional) keeps them and connects to an `xn--` name. So a label that still has one of those
 * after NFKC and lower-casing is not given to [IDN]: it stays Unicode (and so counts as punycode, and shows the «ß»).
 * The NFKC step is done here too, because with ALLOW_UNASSIGNED [IDN] skips it for characters newer than Unicode 3.2
 * (a squared «🄱🄸🅃» is «bit»).
 */
private fun mapLabel(label: String): String {
    val folded = browserLowercase(Normalizer.normalize(label, Normalizer.Form.NFKC))
    if ('\u00DF' in folded || '\u03C2' in folded) return normalizeLabel(label)
    if (folded.all { it.code < 128 }) return folded
    return (toAsciiOrNull(folded) ?: normalizeLabel(label)).lowercase(Locale.ROOT)
}

private fun toAsciiOrNull(label: String): String? = try {
    IDN.toASCII(label, IDN.ALLOW_UNASSIGNED)
} catch (e: Exception) {
    null
}

/**
 * The Unicode form of [host], label by label: only an `xn--` label is decoded, so a «ß» kept as it is stays «ß».
 *
 * An `xn--` label that [IDN] cannot decode stays as it is (MN, T6 of §2.5, accepted): Java's IDN is IDNA2003, whose
 * check after decoding encodes the result again with «ß» as «ss» and «ς» as «σ», so «xn--fa-hia» (faß, the IDNA2008
 * name a browser opens) does not come back the same and is refused. The JDK has no other Punycode decoder, and one
 * written here would also need the IDNA2008 validity rules (or a hostile label could decode to bidi controls or
 * look-alikes that this check now keeps out). Nothing is lost: the name shown in large is the ASCII one the browser
 * really connects to, PUNYCODE still fires (the label starts with `xn--`), and its alert shows that same form, so no
 * look-alike reading is ever made up.
 */
private fun toUnicodeHost(host: String): String = host.split('.').joinToString(".") { label ->
    if (!label.startsWith("xn--")) label else try {
        IDN.toUnicode(label, IDN.ALLOW_UNASSIGNED)
    } catch (e: Exception) {
        label
    }
}

/** Fallback for a label [IDN.toASCII] rejects (too long, say): NFKC, lower-case, and without the default-ignorable characters. */
private fun normalizeLabel(label: String): String {
    val sb = StringBuilder()
    Normalizer.normalize(label, Normalizer.Form.NFKC).codePoints().forEach { cp ->
        val ignorable = Character.getType(cp) == Character.FORMAT.toInt() || cp in 0xFE00..0xFE0F || cp in 0xE0100..0xE01EF ||
            cp == 0x00AD || cp == 0x115F || cp == 0x1160 || cp == 0x3164 || cp == 0xFFA0 || cp == 0x180E
        if (!ignorable) sb.appendCodePoint(cp)
    }
    return browserLowercase(sb.toString())
}

/**
 * Lower case as UTS #46 maps it: [String.lowercase] turns a «Σ» at the end of a word into the final «ς» (Final_Sigma),
 * but a browser maps every «Σ» to «σ» (MN, T6 of §2.5: «ΑΣ.gr» is xn--mxa0b, «ασ»), so it is done first, by hand.
 * Only «ς» written as such stays one. The root locale has no other rule that depends on the neighbours.
 */
private fun browserLowercase(s: String): String = s.replace('\u03A3', '\u03C3').lowercase(Locale.ROOT)

/** The signals of an already read link; [uri] is the original text (hidden characters are looked for in all of it). */
internal fun signalsOf(p: ParsedLink, uri: String): List<Signal> {
    val host = p.host
    val asciiLabels = if (host != null && !p.isIp) host.split('.') else emptyList()
    val unicodeLabels = if (host != null && !p.isIp) (p.unicodeHost ?: host).split('.') else emptyList()
    return Signal.entries.filter { s ->
        when (s) {
            Signal.MIXED_SCRIPTS -> unicodeLabels.any(::mixesScripts)
            // The label the browser really connects to is the `xn--` one; a label it could not encode counts too.
            Signal.PUNYCODE -> asciiLabels.any { label -> label.startsWith("xn--") || label.any { it.code > 127 } }
            Signal.IP_HOST -> p.isIp
            Signal.PLAIN_HTTP -> p.scheme == "http"
            Signal.USERINFO -> p.hasUserInfo
            Signal.ODD_PORT -> p.oddPort
            Signal.SHORTENER -> host != null && SHORTENERS.any { host == it || host.endsWith(".$it") }
            Signal.MANY_LEVELS -> asciiLabels.size - 2 > MAX_SUBDOMAINS
            Signal.DANGEROUS_SCHEME -> p.scheme in DANGEROUS_SCHEMES
            // A line break inside a link is dropped by a browser, so it can hide a different address.
            Signal.HIDDEN_CHARS -> p.hostHidden || hasHiddenChars(uri) || '\n' in uri.trim()
        }
    }
}

/** The signals of [uri], in the order of [Signal]. Never throws. */
fun linkSignals(uri: String): List<Signal> = signalsOf(parseLink(uri), uri)

/**
 * What a browser reads as an IPv4 address: the last label is a number, written in decimal («192.168.1.1»,
 * «3232235777», «127.1») or hexadecimal («0x7f.1»). A real name never ends in a number.
 */
private fun isIpHost(host: String): Boolean {
    val last = host.substringAfterLast('.')
    if (last.isEmpty()) return false
    if (last.all { it in '0'..'9' }) return true
    return (last.startsWith("0x") || last.startsWith("0X")) && last.drop(2).all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
}

/**
 * The dotted IPv4 a browser opens for a numeric [host] (WHATWG): parts in decimal, octal («0300») or hex («0x7f»), the
 * last one filling the bytes that remain («3232235777», «127.1»). Null if it is not a valid address.
 */
private fun canonicalIpv4(host: String): String? {
    val parts = host.split('.')
    if (parts.size > 4 || parts.any { it.isEmpty() }) return null
    val numbers = parts.map { parseIpv4Part(it) ?: return null }
    if (numbers.last() >= 1L shl (8 * (5 - parts.size))) return null
    var value = numbers.last()
    for (i in 0 until numbers.size - 1) {
        if (numbers[i] > 255) return null
        value += numbers[i] shl (8 * (3 - i))
    }
    return "${(value shr 24) and 255}.${(value shr 16) and 255}.${(value shr 8) and 255}.${value and 255}"
}

private fun parseIpv4Part(s: String): Long? {
    val (digits, radix) = when {
        s.length >= 2 && (s.startsWith("0x") || s.startsWith("0X")) -> s.substring(2) to 16
        s.length >= 2 && s.startsWith("0") -> s.substring(1) to 8
        else -> s to 10
    }
    if (digits.isEmpty()) return 0
    if (digits.any { Character.digit(it, radix) < 0 || it.code > 127 }) return null
    return digits.toLongOrNull(radix)
}

/** More than one alphabet in [label], not counting digits, hyphens and combining marks (COMMON and INHERITED). */
private fun mixesScripts(label: String): Boolean {
    val scripts = HashSet<Character.UnicodeScript>()
    var i = 0
    while (i < label.length) {
        val cp = label.codePointAt(i)
        i += Character.charCount(cp)
        val script = try {
            Character.UnicodeScript.of(cp)
        } catch (e: IllegalArgumentException) {
            continue
        }
        if (script == Character.UnicodeScript.COMMON || script == Character.UnicodeScript.INHERITED) continue
        scripts += script
    }
    return scripts.size > 1 && ALLOWED_COMBINATIONS.none { it.containsAll(scripts) }
}

/** Scripts written together in one language (UTS #39): Japanese, Chinese with Bopomofo, Korean with Hanja. */
private val ALLOWED_COMBINATIONS = listOf(
    setOf(Character.UnicodeScript.HAN, Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA),
    setOf(Character.UnicodeScript.HAN, Character.UnicodeScript.BOPOMOFO),
    setOf(Character.UnicodeScript.HAN, Character.UnicodeScript.HANGUL),
)
