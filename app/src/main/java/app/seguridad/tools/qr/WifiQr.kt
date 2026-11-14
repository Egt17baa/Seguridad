package app.seguridad.tools.qr

import app.seguridad.vault.VaultEntry
import app.seguridad.vault.WIFI_HIDDEN_KEY
import app.seguridad.vault.WIFI_PASSWORD_KEY
import app.seguridad.vault.WIFI_SECURITY_KEY
import app.seguridad.vault.WIFI_SECURITY_VALUES
import app.seguridad.vault.WIFI_SSID_KEY
import app.seguridad.core.drawsNothing
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

/** Why a wifi code read field by field is only shown, not kept ([WifiRead.Unsupported]); the inspector says each. */
enum class WifiNotKept {
    /** A type other than WPA, WPA2, WEP or nopass: WPA3, SAE, an enterprise one, or anything else. */
    SECURITY,

    /** A password with no type, or with `nopass`: contradictory (the format ignores it). */
    PASSWORD_WITHOUT_TYPE,

    /** An `H:` that is not `true` or `false` (ZXing reads it as an enterprise setting). */
    SETTING,

    /** A name with a space (or another character the form trims) at an end: the form would not keep it the same (m-3). */
    NAME_SPACES,
}

/** What reading a text that starts like a wifi code gives. */
sealed interface WifiRead {
    /**
     * A code the vault's form can hold and Clavero can write again the same: [WifiQr.parse] gives it. [written] is the
     * type as the code says it, in capitals, when it is not [WifiConfig.security] (ronda 2, n-2: «WPA2», kept as WPA, is
     * shown as «WPA2»); null otherwise.
     */
    data class Usable(val config: WifiConfig, val written: String? = null) : WifiRead {
        override fun toString() = "Usable($config)"
    }

    /**
     * A code read field by field, the same as ZXing reads it, but with something the form cannot hold ([why]); [security]
     * is the type in capitals as it came. The inspector shows it; it is not kept.
     */
    data class Unsupported(val ssid: String, val security: String, val password: String, val hidden: Boolean, val why: WifiNotKept) : WifiRead {
        override fun toString() = "Unsupported(security=$security, hidden=$hidden, why=$why)"
    }

    /**
     * It cannot be read safely: another reader may see another network or a password where Clavero would not, so it is not
     * read at all (2.0, task 9, ronda 1, I-2). Anything before `WIFI:` (a space, a byte order mark, an invisible), the
     * prefix or a known key not in capitals (ZXing reads only `WIFI:`, `S:`, `P:`, `T:` and `H:`), an `S:`, `P:`, `T:`
     * or `H:` anywhere but at the start of a field (ZXing finds them anywhere: `XP:secreta`), a last field with no `;`
     * after it (ZXing does not read it), longer than [WIFI_QR_MAX_CHARS], an escape with nothing after it, a known field
     * twice, a piece with no `KEY:`, a key that is not letters and digits, something but spaces after the closing `;;`,
     * or no name.
     */
    data object Malformed : WifiRead
}

object WifiQr {
    /**
     * Whether [text] starts like a wifi code: `WIFI:` in any case after any spaces, controls and characters that draw
     * nothing. Such a text may carry a password, so the inspector treats it as a secret whatever [read] finds in it.
     */
    fun startsLikeWifi(text: String): Boolean {
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            if (!(Character.isWhitespace(cp) || Character.isSpaceChar(cp) || Character.isISOControl(cp) || drawsNothing(cp))) break
            i += Character.charCount(cp)
        }
        return text.regionMatches(i, PREFIX, 0, PREFIX.length, ignoreCase = true)
    }

    /**
     * What [text] is as a wifi code: null when it does not start like one ([startsLikeWifi]); otherwise
     * [WifiRead.Usable], [WifiRead.Unsupported] or [WifiRead.Malformed]. Never throws, and reads at most
     * [WIFI_QR_MAX_CHARS] characters. Whenever it is Usable or Unsupported, ZXing's own reader gives the same four values
     * (WifiQrReferenceTest), except that ZXing keeps the quotes around a name or password that Clavero reads as text
     * without them. Unknown fields (the enterprise ones, `E:`, `A:`, `I:`, `PH2:`, or any other without `S:`, `P:`, `T:`
     * or `H:` in it) are skipped. Values are never decoded as hexadecimal, and never trimmed.
     */
    fun read(text: String): WifiRead? {
        if (!startsLikeWifi(text)) return null
        if (!text.startsWith(PREFIX) || text.length > WIFI_QR_MAX_CHARS) return WifiRead.Malformed
        val fields = fields(text.substring(PREFIX.length)) ?: return WifiRead.Malformed
        val ssid = fields["S"].orEmpty()
        if (ssid.isEmpty()) return WifiRead.Malformed
        val type = fields["T"].orEmpty()
        val password = fields["P"].orEmpty()
        val h = fields["H"].orEmpty()
        val hidden = h.equals("true", ignoreCase = true)
        val security = when {
            type.isEmpty() || type.equals("nopass", ignoreCase = true) -> "nopass"
            // Owner's decision (14-11-2026, Duda 2): WPA2 is the same family; kept and written again as «WPA».
            type.equals("WPA", ignoreCase = true) || type.equals("WPA2", ignoreCase = true) -> "WPA"
            type.equals("WEP", ignoreCase = true) -> "WEP"
            else -> null
        }
        val why = when {
            security == null -> WifiNotKept.SECURITY
            security == "nopass" && password.isNotEmpty() -> WifiNotKept.PASSWORD_WITHOUT_TYPE
            !(h.isEmpty() || hidden || h.equals("false", ignoreCase = true)) -> WifiNotKept.SETTING
            // The form keeps a name trimmed (fieldsToSave): one it would change is not offered.
            ssid != ssid.trim() -> WifiNotKept.NAME_SPACES
            else -> null
        }
        if (security == null || why != null) return WifiRead.Unsupported(ssid, security ?: type.uppercase(Locale.ROOT), password, hidden, why!!)
        return WifiRead.Usable(
            WifiConfig(ssid, if (security == "nopass") "" else password, security, hidden),
            written = type.uppercase(Locale.ROOT).takeIf { security != "nopass" && it != security },
        )
    }

    /** The network in [text] if the vault's form can hold it ([WifiRead.Usable]); null otherwise. Never throws. */
    fun parse(text: String): WifiConfig? = (read(text) as? WifiRead.Usable)?.config

    /**
     * Why [c] cannot be written as a code, as the text the screen shows; null when it can. An empty name; a security other
     * than [WIFI_SECURITY_VALUES] (a copy made elsewhere can bring any text there, and written as it is it could add
     * fields of its own); WPA or WEP with no password (m-1: such a code joins nothing); or a text over
     * [WIFI_QR_MAX_CHARS] characters or UTF-8 bytes, so whatever is written can be read back, here and in the inspector.
     * A QR code holds less still: the screen catches that where ZXing says so.
     */
    fun problem(c: WifiConfig): String? = when {
        c.ssid.isEmpty() -> WIFI_QR_NO_NAME
        c.security !in WIFI_SECURITY_VALUES -> WIFI_QR_NO_SECURITY
        c.security != "nopass" && c.password.isEmpty() -> WIFI_QR_NO_PASSWORD
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
        append("S:").append(value(c.ssid, rawKey = false)).append(';')
        if (c.security != "nopass") append("P:").append(value(c.password, rawKey = isRawKey(c.security, c.password))).append(';')
        append("H:").append(if (c.hidden) "true" else "false").append(';')
        append(';')
    }

    private fun isHex(s: String) = s.isNotEmpty() && s.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }

    /**
     * m-2: a password whose length can only be a raw key, never a passphrase, so it goes without quotes (which would make
     * every reader take it as text). WPA: 64 hexadecimal digits (Android's public documentation of the
     * pre-shared key of its wifi configuration: «a string of 64 hex digits for raw PSK»; a passphrase has 8 to 63
     * characters).
     * WEP: 10, 26, 32 or 58 hexadecimal digits (keys of 40, 104, 128 and 232 bits, whose text forms have 5, 13, 16 and 29
     * characters).
     */
    private fun isRawKey(security: String, password: String): Boolean = isHex(password) && when (security) {
        "WPA" -> password.length == 64
        "WEP" -> password.length in WEP_RAW_LENGTHS
        else -> false
    }

    /**
     * [s] escaped, and between quotes when it is made only of hexadecimal digits (so it is read as text), unless it is
     * [rawKey].
     */
    private fun value(s: String, rawKey: Boolean): String = if (isHex(s) && !rawKey) "\"$s\"" else escape(s)

    private fun escape(s: String): String = buildString(s.length + 8) {
        for (ch in s) {
            if (ch == '\\' || ch == ';' || ch == ',' || ch == ':' || ch == '"') append('\\')
            append(ch)
        }
    }

    /**
     * The known fields of [body] (the text after `WIFI:`), by key, with their values read; null when the body is
     * [WifiRead.Malformed]. Unknown keys are skipped. The rule of I-2 is checked on the raw body: every `S:`, `P:`, `T:`
     * and `H:` in it starts a field.
     */
    private fun fields(body: String): Map<String, String>? {
        val pieces = ArrayList<String>()
        val starts = HashSet<Int>()
        val piece = StringBuilder()
        var pieceStart = 0
        var i = 0
        var end = body.length
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
                    end = i
                    i++
                    break
                }
                ch == ';' -> {
                    pieces += piece.toString()
                    starts += pieceStart
                    piece.setLength(0)
                    i++
                    pieceStart = i
                }
                else -> {
                    piece.append(ch)
                    i++
                }
            }
        }
        if (closed) {
            if (!body.substring(i).isBlank()) return null // Something after the end.
        } else if (piece.isNotEmpty()) {
            return null // A last field with no «;» after it: ZXing does not read it.
        }
        // I-2: an «S:», «P:», «T:» or «H:» that does not start a field is one another reader takes as that field.
        for (j in 0 until end - 1) {
            if (body[j + 1] == ':' && body[j] in "SPTH" && j !in starts) return null
        }
        val out = HashMap<String, String>()
        for (p in pieces) {
            val colon = unescapedColon(p)
            if (colon < 0) return null
            val key = p.substring(0, colon)
            if (!KEY.matches(key)) return null
            val k = key.uppercase(Locale.ROOT)
            if (k !in KNOWN) continue
            if (key != k) return null // A known key not in capitals: ZXing does not read it, another reader may.
            if (k in out) return null // A known field twice.
            val raw = p.substring(colon + 1)
            // Only the name and the password are quoted by the format (as text); the type and «H:» are as written.
            out[k] = if (k == "S" || k == "P") valueOf(raw) else unescape(raw)
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
    private val WEP_RAW_LENGTHS = setOf(10, 26, 32, 58)
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

/**
 * What the screen of the QR says under the network's name about [c], in order: why no code can be drawn
 * ([WifiQr.problem]), alone; or the warning on view (O-4: with a password, who sees the code sees it; an open network has
 * none to see) and, for a network marked open that keeps a password, that the code goes without it.
 */
fun wifiQrLines(c: WifiConfig): List<String> {
    WifiQr.problem(c)?.let { return listOf(it) }
    val open = c.security == "nopass"
    return listOfNotNull(if (open) WIFI_QR_WARNING_OPEN else WIFI_QR_WARNING, WIFI_QR_OPEN_WITH_PASSWORD.takeIf { open && c.password.isNotEmpty() })
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
/** m-1: WPA or WEP with no password kept. */
const val WIFI_QR_NO_PASSWORD = "Esta red no tiene contraseña guardada: escríbela en «Editar», o márcala como abierta, para poder enseñar el QR."
const val WIFI_QR_TOO_BIG = "El nombre y la contraseña de esta red son demasiado largos para caber en un código QR."
const val WIFI_QR_FAILED = "No se pudo generar el QR."

/** The form, if a code it cannot hold ever reaches it (the inspector does not offer one). */
const val WIFI_QR_NOT_ADDED = "Este código de wifi no se puede añadir a la bóveda."

/** The inspector: a wifi code with an `H:` that is not «true» or «false» ([WifiNotKept.SETTING]). */
const val QR_WIFI_NOT_ADDABLE = "Clavero no reconoce todo lo que trae este código de wifi, así que no lo añade a la bóveda. Puedes crear la entrada a mano."

/** The inspector: a type of security the form does not keep ([WifiNotKept.SECURITY]); [type] as the code says it. */
fun qrWifiOtherSecurity(type: String) = "Clavero no guarda el tipo de seguridad «$type» de este código, así que no lo añade a la bóveda."

/** The inspector: a password with no type, or with «nopass» ([WifiNotKept.PASSWORD_WITHOUT_TYPE]). */
const val QR_WIFI_PASSWORD_WITHOUT_TYPE =
    "Este código lleva una contraseña, pero no dice que la red use una (le falta el tipo de seguridad o dice que es abierta), así que no se añade a la bóveda."

/** The inspector: a name the form would not keep the same ([WifiNotKept.NAME_SPACES], m-3). */
const val QR_WIFI_NAME_SPACES = "El nombre de esta red empieza o termina con un espacio en blanco, y la bóveda no lo guardaría igual, así que no se añade."

/**
 * The inspector: a text that starts like a wifi code and cannot be read safely ([WifiRead.Malformed]): it says Clavero
 * cannot read it, not that it is not a wifi code (ronda 1).
 */
const val QR_WIFI_UNREADABLE =
    "Clavero no puede leer este código de wifi con seguridad: otro lector podría ver en él otra red u otra contraseña. No se añade a la bóveda."

/** The inspector, in place of the full text of any wifi code while it is hidden: it may carry a password. */
const val QR_TEXT_HIDDEN_WIFI = "El texto completo puede llevar una contraseña: toca «Mostrar» para verlo."

/** Every text of the wifi QR, for the tests. */
val WIFI_QR_TEXTS: List<String> = listOf(
    WIFI_QR_OPEN, WIFI_QR_TITLE, WIFI_QR_WARNING, WIFI_QR_WARNING_OPEN, WIFI_QR_OPEN_WITH_PASSWORD, WIFI_QR_SHOW, WIFI_QR_HIDE,
    WIFI_QR_IMAGE, WIFI_QR_NO_NAME, WIFI_QR_NO_SECURITY, WIFI_QR_NO_PASSWORD, WIFI_QR_TOO_BIG, WIFI_QR_FAILED, WIFI_QR_NOT_ADDED,
    QR_WIFI_NOT_ADDABLE, qrWifiOtherSecurity("WPA3"), QR_WIFI_PASSWORD_WITHOUT_TYPE, QR_WIFI_NAME_SPACES, QR_WIFI_UNREADABLE,
    QR_TEXT_HIDDEN_WIFI,
)
