package app.seguridad.imports

import app.seguridad.crypto.OtpAuthRead
import app.seguridad.crypto.OTPAUTH_MAX_CHARS
import app.seguridad.crypto.TotpSecret
import app.seguridad.crypto.isSupported
import app.seguridad.crypto.otpEncode
import app.seguridad.crypto.otpNameFits
import app.seguridad.crypto.readOtpAuth
import app.seguridad.vault.EntryType
import app.seguridad.vault.VaultEntry

/** The family of a format read; [originTag] is the tag every entry imported from it gets (spec «Etiquetas»). */
enum class ImportFormat(val originTag: String) {
    BROWSER_CSV(ORIGIN_TAG_BROWSER),
    FIREFOX_CSV(ORIGIN_TAG_FIREFOX),
    APPLE_CSV(ORIGIN_TAG_APPLE),
    BITWARDEN(ORIGIN_TAG_BITWARDEN),
    AEGIS(ORIGIN_TAG_AEGIS),
    TWOFAS(ORIGIN_TAG_TWOFAS),
    OTPAUTH(ORIGIN_TAG_OTPAUTH),
}

/**
 * One entry read from the file, not merged yet: [entry] has `id = ""`, `createdAt = updatedAt = 0` and no tags (the
 * merge gives the id, the dates and the tags, origin tag included). [folders] are the file's folder or group names, as
 * read. [index] is the item's position in the read, unique across [ImportRead.items] and [ImportRead.skipped]: the key
 * of a duplicate's choice.
 */
data class ImportedItem(val index: Int, val entry: VaultEntry, val folders: List<String>)

enum class SkipReason(val text: String) {
    PASSKEY("Passkey: Clavero no las guarda"),
    SSH_KEY("Clave SSH: Clavero no las guarda"),
    TOTP_UNSUPPORTED("2FA con ajustes que Clavero no admite"),
    NOT_TOTP(SKIP_NOT_TOTP),
    FIELD_TOO_LONG("Un campo pasa del tamaño máximo"),
    BROKEN("Fila o elemento dañado"),
    EMPTY("Vacío"),
}

/**
 * Something in the file that does not enter, listed with its [reason]. [title] is only the plain title, at most
 * [MAX_TITLE] characters, as [plainReportText] shows it: never a password, a note or a key.
 */
data class Skipped(val index: Int, val title: String, val reason: SkipReason)

/** What a reader made of a whole file. [becameNotes] counts the elements of other types (cards, identities) turned into notes. */
data class ImportRead(val format: ImportFormat, val items: List<ImportedItem>, val skipped: List<Skipped>, val becameNotes: Int)

sealed interface ReadResult {
    data class Ok(val read: ImportRead) : ReadResult

    /** An encrypted file of [format]: [readImport] has to be called again with its password. */
    data class NeedsPassword(val format: ImportFormat) : ReadResult

    /** The whole file was refused; [message] is a Spanish text from `ImportTexts.kt`, never a value from the file. */
    data class Failed(val message: String) : ReadResult
}

/**
 * A whole-file failure a reader throws, with a message from `ImportTexts.kt` (never a value from the file). [readImport]
 * turns it into [ReadResult.Failed]; it never escapes it.
 */
class ImportFailure(message: String) : Exception(message)

/** The types a reader may build (spec «Entradas que se crean»). */
private val IMPORTED_TYPES = setOf(EntryType.LOGIN, EntryType.NOTE, EntryType.TOTP)

/** A not yet merged entry, as [ImportedItem] says. */
internal fun importedEntry(
    type: EntryType,
    title: String,
    username: String = "",
    password: String = "",
    url: String = "",
    notes: String = "",
    totp: TotpSecret? = null,
    favorite: Boolean = false,
): VaultEntry = VaultEntry(
    id = "", type = type, title = title, username = username, password = password, url = url, notes = notes,
    totp = totp, createdAt = 0, updatedAt = 0, favorite = favorite,
)

/** Most combining marks (Mn, Me) kept in a row on one base character: enough for any language's accents and tones. */
const val MAX_COMBINING_MARKS = 3

/**
 * [text] as the import report may show it (decision (c) of the owner: what comes from the file is data, and rulings R9
 * and I2: the same helper wherever a title, user or URL is shown). One pass by code point:
 * - every control character (Cc: C0 and C1) and the line and paragraph separators U+2028 and U+2029 become a space;
 * - every format character (Cf) is dropped: the bidi controls that turn text around (U+202A–U+202E, U+2066–U+2069,
 *   U+200E, U+200F, U+061C), the invisible ones (U+00AD, U+180E, U+200B–U+200D, U+2060–U+2064, U+FEFF) and the tag
 *   characters (the whole block U+E0000–U+E007F, its unassigned code points too). U+200D (the zero-width joiner) goes
 *   too: an emoji sequence such as a family shows as its separate emoji, which is accepted;
 * - also dropped, though they are not Cf: the interlinear annotation characters U+FFF9–U+FFFB, the combining grapheme
 *   joiner U+034F and the Hangul fillers (U+115F, U+1160, U+3164, U+FFA0), all invisible, and a lone surrogate;
 * - after [MAX_COMBINING_MARKS] combining marks in a row (Mn, Me) on one base, the rest are dropped, so a tower of
 *   accents cannot cover the lines around it.
 * Then the text is cut to at most [maxChars] UTF-16 units, always at the end of a code point (a surrogate pair is never
 * split). So «ban\u200Bco» shows as «banco» and a title cannot make one line look like another by hidden characters.
 * It does not catch look-alike letters from other scripts (a Cyrillic «а» for a Latin «a»): no character is removed
 * for that. Ordinary text, accents, «ñ» and emoji without a joiner come out unchanged. [Skipped.title] always goes
 * through it.
 */
fun plainReportText(text: String, maxChars: Int = MAX_TITLE): String {
    val out = StringBuilder(minOf(text.length, maxChars.coerceAtLeast(0)))
    var marks = 0
    var i = 0
    while (i < text.length) {
        val cp = text.codePointAt(i)
        i += Character.charCount(cp)
        val type = Character.getType(cp)
        val invisible = type == Character.FORMAT.toInt() || type == Character.SURROGATE.toInt() ||
            cp in TAG_CHARACTERS || cp in INVISIBLE_NOT_FORMAT
        if (invisible) continue
        if (type == Character.NON_SPACING_MARK.toInt() || type == Character.ENCLOSING_MARK.toInt()) {
            if (marks >= MAX_COMBINING_MARKS) continue
            marks++
        } else {
            marks = 0
        }
        val shown = if (Character.isISOControl(cp) || cp == 0x2028 || cp == 0x2029) ' '.code else cp
        if (out.length + Character.charCount(shown) > maxChars) break
        out.appendCodePoint(shown)
    }
    return out.toString()
}

/** The whole tag block, also its unassigned code points (only U+E0001 and U+E0020–U+E007F are Cf). */
private val TAG_CHARACTERS = 0xE0000..0xE007F

/** Invisible characters that are not Cf: interlinear annotations, the combining grapheme joiner and the Hangul fillers. */
private val INVISIBLE_NOT_FORMAT = setOf(0xFFF9, 0xFFFA, 0xFFFB, 0x034F, 0x115F, 0x1160, 0x3164, 0xFFA0)

/** If [link] is an otpauth:// link of another kind than TOTP (`otpauth://hotp/…`): Clavero only has TOTP. */
private fun isOtherOtpAuth(link: String): Boolean {
    val t = link.trim()
    if (!t.startsWith("otpauth://", ignoreCase = true)) return false
    val kind = t.substring("otpauth://".length).takeWhile { it != '/' && it != '?' }
    return kind.isNotEmpty() && !kind.equals("totp", ignoreCase = true)
}

/**
 * Collects what a reader finds, in the file's order, and gives each item and each skipped element its own index.
 * Readers of every format use it, so the indexes, the titles of [Skipped] and the TOTP rules are the same for all.
 *
 * Decision (a) of the owner: everything a file gives, after every expansion (a login that also gives a TOTP, an
 * `otpauth-migration://` line that holds up to 100 codes, a passkey listed apart), counts against [IMPORT_MAX_ITEMS],
 * items and skipped elements alike. The one past it throws [ImportFailure] ([IMPORT_TOO_MANY_ITEMS]) at once, so a
 * hostile file stops while it is read, never after building everything.
 */
internal class ImportReadBuilder(private val format: ImportFormat) {
    private val items = ArrayList<ImportedItem>()
    private val skipped = ArrayList<Skipped>()
    private var next = 0
    private var becameNotes = 0

    fun add(entry: VaultEntry, folders: List<String> = emptyList()) {
        require(entry.type in IMPORTED_TYPES) { "Unsupported imported type" }
        items += ImportedItem(nextIndex(), entry, folders)
    }

    fun skip(title: String, reason: SkipReason) {
        skipped += Skipped(nextIndex(), plainReportText(title), reason)
    }

    private fun nextIndex(): Int {
        if (next >= IMPORT_MAX_ITEMS) throw ImportFailure(IMPORT_TOO_MANY_ITEMS)
        return next++
    }

    /** One element of another type (card, identity) entered as a note. */
    fun becameNote() {
        becameNotes++
    }

    /**
     * The 2FA of [link], as a TOTP entry named [title]: every code goes through [readOtpAuth] and [isSupported] (rule
     * of CRY-B02), so a setting Clavero does not calculate is refused, never changed. Otherwise a [Skipped]:
     * [SkipReason.TOTP_UNSUPPORTED] for such settings, [SkipReason.NOT_TOTP] for another otpauth kind (HOTP),
     * [SkipReason.FIELD_TOO_LONG] past [OTPAUTH_MAX_CHARS] or when the link's issuer or account would be cut
     * ([OtpAuthRead.Usable.namesCut]: a field is never imported cut), and [SkipReason.BROKEN] for anything else.
     * [notes] and [favorite] are those of the element the code comes from (an Aegis entry), as read.
     */
    fun addOtpAuth(link: String, title: String, folders: List<String> = emptyList(), notes: String = "", favorite: Boolean = false) {
        if (link.length > OTPAUTH_MAX_CHARS) return skip(title, SkipReason.FIELD_TOO_LONG)
        when (val r = readOtpAuth(link)) {
            is OtpAuthRead.Usable ->
                if (r.namesCut) {
                    skip(title, SkipReason.FIELD_TOO_LONG)
                } else if (r.secret.isSupported()) {
                    add(importedEntry(EntryType.TOTP, title, notes = notes, totp = r.secret, favorite = favorite), folders)
                } else {
                    skip(title, SkipReason.TOTP_UNSUPPORTED)
                }
            is OtpAuthRead.UnsupportedSettings -> skip(title, SkipReason.TOTP_UNSUPPORTED)
            OtpAuthRead.NotUsable -> skip(title, if (isOtherOtpAuth(link)) SkipReason.NOT_TOTP else SkipReason.BROKEN)
        }
    }

    /**
     * A TOTP given by its parts, as the 2FA apps store it (Aegis, 2FAS), read by [addOtpAuth] like any link: one rule
     * for every code. Each part is percent-encoded into its own parameter, so no value can add or change a setting
     * (`issuer` = `x&digits=10` stays an issuer). The label is `:` + [account], so a `:` in the account never makes
     * an issuer of it. A null setting is missing and takes the default; a present one is written as given (an empty
     * [algorithm] or a [NOT_A_WHOLE_NUMBER] setting is refused by [readOtpAuth], never changed). Parts too long for a
     * link go to [SkipReason.FIELD_TOO_LONG] before the link is built, and so do an issuer or an account that the code
     * would keep cut ([otpNameFits]). The encoding is the one [readOtpAuth] reads ([otpEncode]).
     */
    fun addTotp(
        secret: String,
        issuer: String,
        account: String,
        algorithm: String?,
        digits: Int?,
        period: Int?,
        title: String,
        folders: List<String> = emptyList(),
        notes: String = "",
        favorite: Boolean = false,
    ) {
        if (secret.length.toLong() + issuer.length + account.length + (algorithm?.length ?: 0) > OTPAUTH_MAX_CHARS ||
            !otpNameFits(issuer) || !otpNameFits(account)
        ) {
            return skip(title, SkipReason.FIELD_TOO_LONG)
        }
        val link = buildString {
            append("otpauth://totp/:").append(otpEncode(account)).append("?secret=").append(otpEncode(secret))
            if (issuer.isNotEmpty()) append("&issuer=").append(otpEncode(issuer))
            if (algorithm != null) append("&algorithm=").append(otpEncode(algorithm))
            if (digits != null) append("&digits=").append(digits)
            if (period != null) append("&period=").append(period)
        }
        addOtpAuth(link, title, folders, notes, favorite)
    }

    fun build(): ImportRead = ImportRead(format, items.toList(), skipped.toList(), becameNotes)
}

/** Refuses a file with more than [IMPORT_MAX_ITEMS] rows or elements. */
internal fun checkItemCount(count: Int) {
    if (count > IMPORT_MAX_ITEMS) throw ImportFailure(IMPORT_TOO_MANY_ITEMS)
}
