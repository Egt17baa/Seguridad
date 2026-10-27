package app.seguridad.tools.qr

import java.net.IDN
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
private val SPECIAL_SCHEMES = setOf("http", "https", "ftp", "ftps", "ws", "wss")

private val DEFAULT_PORTS = mapOf("http" to 80, "https" to 443, "ftp" to 21, "ftps" to 990, "ws" to 80, "wss" to 443)

/** More than this many subdomain levels in front of the registered name and the ending is [Signal.MANY_LEVELS]. */
private const val MAX_SUBDOMAINS = 4

/**
 * What [parseLink] reads out of a link the way a browser would: [host] lower-case and without the trailing dot,
 * [unicodeHost] its Unicode form, and what is needed for the signals.
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
)

/**
 * Reads [uri] without throwing, whatever it holds. It does not use `java.net.URI`: that rejects spaces, backslashes,
 * underscores and non-ASCII names, and where it does parse it can disagree with the browser that will really open the
 * link. This follows the browser (WHATWG URL) on what matters for the signals: tabs and line breaks inside are
 * dropped, «\» counts as «/» in web links, the user info ends at the **last** «@» of the authority, and the host is
 * percent-decoded and lower-cased.
 */
internal fun parseLink(uri: String): ParsedLink {
    val u = uri.filterNot { it == '\t' || it == '\n' || it == '\r' }.trim { it <= ' ' || it.isWhitespace() }
    val scheme = schemeOf(u) ?: return ParsedLink("", false, null, null, null, false, false)
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
        val colon = hostPort.lastIndexOf(':')
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

    val host = percentDecode(rawHost)
        .map { if (it == '。' || it == '．' || it == '｡') '.' else it }
        .joinToString("")
        .lowercase(Locale.ROOT)
        .trimEnd('.')
        .ifEmpty { null }
    val unicodeHost = host?.let { if (it.startsWith("[")) it else toUnicodeOrNull(it) }
    return ParsedLink(scheme, hierarchical, host, unicodeHost, port, oddPort, at >= 0)
}

/** The scheme of [text], lower-case, if it starts with a valid «scheme:»; null otherwise (a drive letter included). */
internal fun schemeOf(text: String): String? {
    val colon = text.indexOf(':')
    if (colon < 2) return null
    val ok = text[0].let { it in 'a'..'z' || it in 'A'..'Z' } &&
        (1 until colon).all { i -> text[i].let { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '+' || it == '-' || it == '.' } }
    return if (ok) text.substring(0, colon).lowercase(Locale.ROOT) else null
}

private fun toUnicodeOrNull(host: String): String? = try {
    IDN.toUnicode(host, IDN.ALLOW_UNASSIGNED)
} catch (e: Exception) {
    null
}

/** The signals of an already read link; [uri] is the original text (hidden characters are looked for in all of it). */
internal fun signalsOf(p: ParsedLink, uri: String): List<Signal> {
    val host = p.host
    val isIp = host != null && isIpHost(host)
    val asciiLabels = if (host != null && !isIp) host.split('.') else emptyList()
    val unicodeLabels = if (host != null && !isIp) (p.unicodeHost ?: host).split('.') else emptyList()
    return Signal.entries.filter { s ->
        when (s) {
            Signal.MIXED_SCRIPTS -> unicodeLabels.any(::mixesScripts)
            Signal.PUNYCODE -> asciiLabels.any { it.startsWith("xn--") }
            Signal.IP_HOST -> isIp
            Signal.PLAIN_HTTP -> p.scheme == "http"
            Signal.USERINFO -> p.hasUserInfo
            Signal.ODD_PORT -> p.oddPort
            Signal.SHORTENER -> host != null && SHORTENERS.any { host == it || host.endsWith(".$it") }
            Signal.MANY_LEVELS -> asciiLabels.size - 2 > MAX_SUBDOMAINS
            Signal.DANGEROUS_SCHEME -> p.scheme in DANGEROUS_SCHEMES
            // A line break inside a link is dropped by a browser, so it can hide a different address.
            Signal.HIDDEN_CHARS -> hasHiddenChars(uri) || '\n' in uri.trim()
        }
    }
}

/** The signals of [uri], in the order of [Signal]. Never throws. */
fun linkSignals(uri: String): List<Signal> = signalsOf(parseLink(uri), uri)

/**
 * IPv6 between brackets, or what a browser reads as an IPv4 address: the last label is a number, written in decimal
 * («192.168.1.1», «3232235777», «127.1») or hexadecimal («0x7f.1»). A real name never ends in a number.
 */
private fun isIpHost(host: String): Boolean {
    if (host.startsWith("[")) return true
    val last = host.substringAfterLast('.')
    if (last.isEmpty()) return false
    if (last.all { it in '0'..'9' }) return true
    return (last.startsWith("0x") || last.startsWith("0X")) && last.drop(2).all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
}

/** More than one alphabet in [label], not counting digits, hyphens and combining marks (COMMON and INHERITED). */
private fun mixesScripts(label: String): Boolean {
    var first: Character.UnicodeScript? = null
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
        if (first == null) first = script else if (first != script) return true
    }
    return false
}
