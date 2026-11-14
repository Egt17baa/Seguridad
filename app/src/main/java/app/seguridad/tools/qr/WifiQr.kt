package app.seguridad.tools.qr

import app.seguridad.vault.VaultEntry
import app.seguridad.vault.WIFI_HIDDEN_KEY
import app.seguridad.vault.WIFI_PASSWORD_KEY
import app.seguridad.vault.WIFI_SECURITY_KEY
import app.seguridad.vault.WIFI_SECURITY_VALUES
import app.seguridad.vault.WIFI_SSID_KEY
import java.util.Locale

/*
 * 2.0, task 9 (design «QR de un wifi»): the wifi code `WIFI:T:<WPA|WEP|nopass>;S:<nombre>;P:<contraseña>;H:<true|false>;;`,
 * written and read from the public description of the format in ZXing's documentation (the library already in the APK
 * and in «Licencias»), with no code taken from any other app. Nothing here joins a network: the app only shows the code,
 * reads it and hands it to the vault's form.
 *
 * The format, as that description gives it:
 * - the fields go in any order, and only `S:` (the name) is required; `T:` is `WPA`, `WEP` or `nopass` («or omit for no
 *   password»); `P:` is ignored with `nopass`; `H:true` says the name is hidden;
 * - `\`, `;`, `,`, `"` and `:` inside a value are escaped with `\`;
 * - a name or password «that could be interpreted as hex» goes between double quotes, so it is read as text. Quotes
 *   that are part of the value are escaped (`S:\"foo\;bar\\baz\";;` is the name `"foo;bar\baz"`).
 */

/**
 * The longest text read or written, in UTF-16 units and in UTF-8 bytes (the inspector's bound too). A QR code holds less:
 * 2 330 bytes as the screen draws it (level M, UTF-8), where ZXing refuses the rest.
 */
const val WIFI_QR_MAX_CHARS = 4096

/**
 * A wifi network as the code carries it. [security] is one of [WIFI_SECURITY_VALUES] for a code Clavero writes or keeps;
 * [password] is empty for an open network. [toString] never carries the password (nor the name).
 */
data class WifiConfig(val ssid: String, val password: String, val security: String, val hidden: Boolean) {
    override fun toString() = "WifiConfig(security=$security, hidden=$hidden)"
}

/** What reading a text that starts with `WIFI:` gives. */
sealed interface WifiRead {
    /** A code the vault's form can hold and Clavero can write again the same: [WifiQr.parse] gives it. */
    data class Usable(val config: WifiConfig) : WifiRead {
        override fun toString() = "Usable($config)"
    }

    /**
     * A code read field by field, but with something the form cannot hold: a type other than WPA, WEP or nopass ([security]
     * in capitals as it came), a password with no type or with `nopass` (contradictory: the format ignores it), or an `H:`
     * that is not `true` or `false` (ZXing reads it as an enterprise setting). The inspector shows it; it is not kept.
     */
    data class Unsupported(val ssid: String, val security: String, val password: String, val hidden: Boolean) : WifiRead {
        override fun toString() = "Unsupported(security=$security, hidden=$hidden)"
    }

    /**
     * It cannot be read without guessing, so it is not read at all: longer than [WIFI_QR_MAX_CHARS], an escape with
     * nothing after it (a trailing `\`), a known field twice (another reader may take the other one), a piece with no
     * `KEY:` (an unescaped `;` in a value), a key that is not letters and digits, something after the closing `;;`, or no
     * name (missing or empty).
     */
    data object Malformed : WifiRead
}

object WifiQr {
    /**
     * What [text] is as a wifi code: null when it does not start with `WIFI:` (any case, after the spaces around the whole
     * text); otherwise [WifiRead.Usable], [WifiRead.Unsupported] or [WifiRead.Malformed]. Never throws, and reads at most
     * [WIFI_QR_MAX_CHARS] characters. Unknown fields (the enterprise ones, `E:`, `A:`, `I:`, `PH2:`, or any other) are
     * skipped; a missing `;;` at the end is tolerated. Values are never decoded as hexadecimal: quoted or not, a value is
     * the text it says.
     */
    fun read(text: String): WifiRead? {
        val t = text.trim()
        if (!t.regionMatches(0, PREFIX, 0, PREFIX.length, ignoreCase = true)) return null
        if (t.length > WIFI_QR_MAX_CHARS) return WifiRead.Malformed
        val fields = fields(t.substring(PREFIX.length)) ?: return WifiRead.Malformed
        val ssid = fields["S"].orEmpty()
        if (ssid.isEmpty()) return WifiRead.Malformed
        val type = fields["T"].orEmpty().trim()
        val password = fields["P"].orEmpty()
        val h = fields["H"]?.trim().orEmpty()
        val hidden = h.equals("true", ignoreCase = true)
        val security = when {
            type.isEmpty() || type.equals("nopass", ignoreCase = true) -> "nopass"
            type.equals("WPA", ignoreCase = true) -> "WPA"
            type.equals("WEP", ignoreCase = true) -> "WEP"
            else -> null
        }
        val supported = security != null && !(security == "nopass" && password.isNotEmpty()) &&
            (h.isEmpty() || hidden || h.equals("false", ignoreCase = true))
        if (!supported) return WifiRead.Unsupported(ssid, security ?: type.uppercase(Locale.ROOT), password, hidden)
        return WifiRead.Usable(WifiConfig(ssid, if (security == "nopass") "" else password, security, hidden))
    }

    /** The network in [text] if the vault's form can hold it ([WifiRead.Usable]); null otherwise. Never throws. */
    fun parse(text: String): WifiConfig? = (read(text) as? WifiRead.Usable)?.config

    /**
     * Why [c] cannot be written as a code, as the text the screen shows; null when it can. An empty name; a security other
     * than [WIFI_SECURITY_VALUES] (a copy made elsewhere can bring any text there, and written as it is it could add
     * fields of its own); or a text over [WIFI_QR_MAX_CHARS] characters or UTF-8 bytes, so whatever is written can be read
     * back, here and in the inspector. A QR code holds less still: the screen catches that where ZXing says so.
     */
    fun problem(c: WifiConfig): String? = when {
        c.ssid.isEmpty() -> WIFI_QR_NO_NAME
        c.security !in WIFI_SECURITY_VALUES -> WIFI_QR_NO_SECURITY
        else -> written(c).let { if (it.length > WIFI_QR_MAX_CHARS || it.toByteArray(Charsets.UTF_8).size > WIFI_QR_MAX_CHARS) WIFI_QR_TOO_BIG else null }
    }

    /**
     * The code of [c]: `WIFI:T:…;S:…;P:…;H:…;;`, with no `P:` for an open network (whatever password is kept with it).
     * Throws [IllegalArgumentException] with the text of [problem] when there is one.
     */
    fun encode(c: WifiConfig): String {
        problem(c)?.let { throw IllegalArgumentException(it) }
        return written(c)
    }

    private fun written(c: WifiConfig): String = buildString {
        append(PREFIX).append("T:").append(escape(c.security)).append(';')
        append("S:").append(value(c.ssid)).append(';')
        if (c.security != "nopass") append("P:").append(value(c.password)).append(';')
        append("H:").append(if (c.hidden) "true" else "false").append(';')
        append(';')
    }

    /** [s] escaped, and between quotes when it is made only of hexadecimal digits (so it is read as text). */
    private fun value(s: String): String = if (s.isNotEmpty() && s.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) "\"$s\"" else escape(s)

    private fun escape(s: String): String = buildString(s.length + 8) {
        for (ch in s) {
            if (ch == '\\' || ch == ';' || ch == ',' || ch == ':' || ch == '"') append('\\')
            append(ch)
        }
    }

    /**
     * The known fields of [body] (the text after `WIFI:`), by key in capitals and with their values read; null when the
     * body is [WifiRead.Malformed]. Unknown keys are skipped.
     */
    private fun fields(body: String): Map<String, String>? {
        val pieces = ArrayList<String>()
        val piece = StringBuilder()
        var i = 0
        var closed = false
        while (i < body.length) {
            val ch = body[i]
            when {
                ch == '\\' -> {
                    if (i + 1 >= body.length) return null // An escape with nothing after it.
                    piece.append(ch).append(body[i + 1])
                    i += 2
                }
                ch == ';' && piece.isEmpty() -> {
                    closed = true // The closing «;;» (or a «;» with nothing before it).
                    i++
                    break
                }
                ch == ';' -> {
                    pieces += piece.toString()
                    piece.setLength(0)
                    i++
                }
                else -> {
                    piece.append(ch)
                    i++
                }
            }
        }
        if (closed) {
            if (i < body.length) return null // Something after the end.
        } else if (piece.isNotEmpty()) {
            pieces += piece.toString() // The last field, with no «;» after it.
        }
        val out = HashMap<String, String>()
        for (p in pieces) {
            val colon = unescapedColon(p)
            if (colon < 0) return null
            val key = p.substring(0, colon).trim()
            if (!KEY.matches(key)) return null
            val k = key.uppercase(Locale.ROOT)
            if (k !in KNOWN) continue
            if (k in out) return null // A known field twice.
            out[k] = valueOf(p.substring(colon + 1))
        }
        return out
    }

    /** The index of the first `:` in [p] that is not escaped, or -1. */
    private fun unescapedColon(p: String): Int {
        var i = 0
        while (i < p.length) {
            when (p[i]) {
                '\\' -> i += 2
                ':' -> return i
                else -> i++
            }
        }
        return -1
    }

    /**
     * The value of the still escaped [raw]: without the quotes around it when both are there and the closing one is not
     * escaped (so it is text, never hexadecimal), and with every `\x` read as `x`.
     */
    private fun valueOf(raw: String): String {
        val quoted = raw.length >= 2 && raw[0] == '"' && raw[raw.length - 1] == '"' && backslashesBefore(raw, raw.length - 1) % 2 == 0
        return unescape(if (quoted) raw.substring(1, raw.length - 1) else raw)
    }

    private fun backslashesBefore(s: String, at: Int): Int {
        var n = 0
        var j = at - 1
        while (j >= 0 && s[j] == '\\') {
            n++
            j--
        }
        return n
    }

    /** [s] with every `\x` as `x`. A lone `\` at the end cannot be here: [fields] refuses it before. */
    private fun unescape(s: String): String {
        if ('\\' !in s) return s
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val ch = s[i]
            if (ch == '\\' && i + 1 < s.length) {
                sb.append(s[i + 1])
                i += 2
            } else {
                sb.append(ch)
                i++
            }
        }
        return sb.toString()
    }

    private const val PREFIX = "WIFI:"
    private val KEY = Regex("[A-Za-z][A-Za-z0-9]*")
    private val KNOWN = setOf("T", "S", "P", "H")
}

/**
 * The network a wifi entry ([app.seguridad.vault.EntryType.WIFI]) keeps, from its template's fields: the first field of each id (as the entry's card shows
 * them), an empty text for a missing one, and «Red oculta» only when it is `true`. The security is as kept: [WifiQr]
 * writes only the three it knows.
 */
fun wifiConfigOf(entry: VaultEntry): WifiConfig {
    fun field(id: String) = entry.fields.firstOrNull { it.id == id }?.value.orEmpty()
    return WifiConfig(field(WIFI_SSID_KEY), field(WIFI_PASSWORD_KEY), field(WIFI_SECURITY_KEY), field(WIFI_HIDDEN_KEY) == "true")
}

// Texts (also in «Textos para el Abogado Digital (revisión final)» of the task's report and in the annex of texts).

/** «Enseñar QR» on a wifi entry's card. */
const val WIFI_QR_OPEN = "Enseñar QR"

/** The title of the screen of the QR. */
const val WIFI_QR_TITLE = "QR del wifi"

/** O-4, on view, with a password in the code. */
const val WIFI_QR_WARNING = "Quien vea este código ve la contraseña y puede conectarse a tu wifi."

/** The same warning for an open network, which has no password to see. */
const val WIFI_QR_WARNING_OPEN = "Quien vea este código puede conectarse a tu wifi."

/** An entry marked open that keeps a password: the code goes without it. */
const val WIFI_QR_OPEN_WITH_PASSWORD = "La red está marcada como abierta, así que la contraseña guardada no va en el código."

const val WIFI_QR_SHOW = "Mostrar QR"
const val WIFI_QR_HIDE = "Ocultar QR"

/** The image's description for TalkBack: a fixed text, never the password. */
const val WIFI_QR_IMAGE = "Código QR para conectarse a la red"

const val WIFI_QR_NO_NAME = "Esta red no tiene nombre: escríbelo en «Editar» para poder enseñar el QR."
const val WIFI_QR_NO_SECURITY = "Elige el tipo de seguridad de esta red en «Editar» para poder enseñar el QR."
const val WIFI_QR_TOO_BIG = "El nombre y la contraseña de esta red son demasiado largos para caber en un código QR."
const val WIFI_QR_FAILED = "No se pudo generar el QR."

/** The form, if a code it cannot hold ever reaches it (the inspector does not offer one). */
const val WIFI_QR_NOT_ADDED = "Este código de wifi no se puede añadir a la bóveda."

/** The inspector: a wifi code read, but with something the vault's form cannot hold ([WifiRead.Unsupported]). */
const val QR_WIFI_NOT_ADDABLE = "Clavero no reconoce todo lo que trae este código de wifi, así que no lo añade a la bóveda. Puedes crear la entrada a mano."

/** The inspector: a text that starts like a wifi code and cannot be read without guessing ([WifiRead.Malformed]). */
const val QR_WIFI_UNREADABLE = "Parece un código de wifi, pero está mal escrito: no se puede leer sin adivinar."

/** The inspector, in place of that text while it is hidden: it may carry a password. */
const val QR_TEXT_HIDDEN_WIFI = "El texto completo puede llevar una contraseña: toca «Mostrar» para verlo."

/** Every text of the wifi QR, for the tests. */
val WIFI_QR_TEXTS: List<String> = listOf(
    WIFI_QR_OPEN, WIFI_QR_TITLE, WIFI_QR_WARNING, WIFI_QR_WARNING_OPEN, WIFI_QR_OPEN_WITH_PASSWORD, WIFI_QR_SHOW, WIFI_QR_HIDE,
    WIFI_QR_IMAGE, WIFI_QR_NO_NAME, WIFI_QR_NO_SECURITY, WIFI_QR_TOO_BIG, WIFI_QR_FAILED, WIFI_QR_NOT_ADDED,
    QR_WIFI_NOT_ADDABLE, QR_WIFI_UNREADABLE, QR_TEXT_HIDDEN_WIFI,
)
