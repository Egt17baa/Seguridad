package app.seguridad.imports

import app.seguridad.core.EMOJI_SELECTOR
import app.seguridad.core.ZERO_WIDTH_JOINER
import app.seguridad.core.drawsNothing
import app.seguridad.core.emojiSelectorShows
import app.seguridad.crypto.OtpAuthRead
import app.seguridad.crypto.OTPAUTH_MAX_CHARS
import app.seguridad.crypto.TotpSecret
import app.seguridad.crypto.isSupported
import app.seguridad.crypto.otpEncode
import app.seguridad.crypto.otpNameFits
import app.seguridad.crypto.readOtpAuth
import app.seguridad.vault.EntryType
import app.seguridad.vault.VaultEntry
import java.text.Normalizer

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
 * - every code point that draws nothing is dropped ([drawsNothing], the one list that the names of files and the QR
 *   Inspector also use, rulings R43 and R49): Unicode's default ignorables, such as the bidi controls that turn text
 *   around (U+202A–U+202E, U+2066–U+2069, U+200E, U+200F, U+061C), the invisible ones (U+00AD, U+034F, U+180B–U+180F,
 *   U+200B–U+200D, U+2060–U+206F, U+FEFF), the Hangul fillers (U+115F, U+1160, U+3164, U+FFA0), the Khmer inherent
 *   vowels U+17B4 and U+17B5, the variation selectors U+FE00–U+FE0F, U+FFF0–U+FFF8 and the whole of plane 14 (the tag
 *   characters and the supplementary variation selectors U+E0100–U+E01EF); and the Braille blank U+2800, the
 *   interlinear annotations U+FFF9–U+FFFB and the Egyptian hieroglyph format controls U+13430–U+1343F. The prepended
 *   concatenation marks (U+0600–U+0605, U+06DD, U+070F, U+0890, U+0891, U+08E2, U+110BD, U+110CD) are format
 *   characters that are drawn, so they stay (RT-13). U+200D (the zero-width joiner) goes: an emoji sequence such as a
 *   family shows as its separate emoji, which is accepted (a file name keeps it between two emoji). U+FE0F stays only
 *   where the names of files keep it ([emojiSelectorShows]: after a non-ASCII code point that is not a space, an emoji
 *   in practice, or in a keycap, «1️⃣»), so «❤️» keeps its emoji look; [compareKey] drops it anyway;
 * - a lone surrogate is dropped;
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
    var previous = -1
    var i = 0
    while (i < text.length) {
        val cp = text.codePointAt(i)
        i += Character.charCount(cp)
        val type = Character.getType(cp)
        if (type == Character.SURROGATE.toInt()) continue
        if (drawsNothing(cp) && !(cp == EMOJI_SELECTOR && emojiSelectorShows(previous, nextForSelector(text, i)))) {
            // As in a file name, where the joiner is still there when the selectors are looked at: none right after it.
            if (cp == ZERO_WIDTH_JOINER) previous = cp
            continue
        }
        if (type == Character.NON_SPACING_MARK.toInt() || type == Character.ENCLOSING_MARK.toInt()) {
            if (marks >= MAX_COMBINING_MARKS) continue
            marks++
        } else {
            marks = 0
        }
        val shown = if (Character.isISOControl(cp) || cp == 0x2028 || cp == 0x2029) ' '.code else cp
        if (out.length + Character.charCount(shown) > maxChars) break
        out.appendCodePoint(shown)
        previous = shown
    }
    return out.toString()
}

/**
 * The code point from [from] on that a U+FE0F before it sits next to once the rest of what draws nothing is gone (a
 * zero-width joiner and another U+FE0F count, as in a file name, where they are still there), or -1 at the end.
 */
private fun nextForSelector(text: String, from: Int): Int {
    var j = from
    while (j < text.length) {
        val cp = text.codePointAt(j)
        val dropped = drawsNothing(cp) || Character.getType(cp) == Character.SURROGATE.toInt()
        if (cp == ZERO_WIDTH_JOINER || cp == EMOJI_SELECTOR || !dropped) return cp
        j += Character.charCount(cp)
    }
    return -1
}

/**
 * [text] as the merge compares it (RT-2, ruling R34; RT-6, RT-7 and RT-9, rulings R43, R44 and R49): what [plainReportText] shows,
 * without its cut, in NFKC, without the U+FE0F it keeps after an emoji (nor anything else of [drawsNothing]), with every
 * run of spaces (a no-break space, or a control that it shows as a space, included) as one space and none at the ends.
 * So «Ban\u200Bco», «Banco\u2060», «Banco\u2065», «Banco\u2800», «Cafe\u0301» and « Banco\u00A0» compare as «Banco» and «Café», the
 * way they look, and a look-alike cannot enter as a new entry next to the one it copies. NFKC also folds the
 * compatibility forms: the ligature «ﬁ» is «fi», «Ⅱ» is «II», and full-width and mathematical letters («Ｂａｎｃｏ»,
 * «𝐁anco») are the plain ones; «²» is «2». Case is kept (the title's key lowers it, the user's does not). Only for
 * comparing: the stored text is never changed (R9, R10). One pass plus NFKC (whose quick check makes ordinary text
 * cheap), so it does for 20 000 entries; it never throws (a lone surrogate is already gone). NFKC can lengthen a text
 * (one code point can become up to 18), which only costs memory while the key lives.
 */
fun compareKey(text: String): String {
    var shown = Normalizer.normalize(plainReportText(text, Int.MAX_VALUE), Normalizer.Form.NFKC)
    if (shown.codePoints().anyMatch(::drawsNothing)) {
        // The U+FE0F kept after an emoji: one emoji with and without it is the same picture. Normalised again, so a mark
        // that the selector kept apart from its base composes with it.
        val visible = StringBuilder(shown.length)
        shown.codePoints().forEach { if (!drawsNothing(it)) visible.appendCodePoint(it) }
        shown = Normalizer.normalize(visible, Normalizer.Form.NFKC)
    }
    val out = StringBuilder(shown.length)
    var space = false
    for (c in shown) {
        if (c.isWhitespace()) {
            space = out.isNotEmpty()
        } else {
            if (space) out.append(' ')
            space = false
            out.append(c)
        }
    }
    return out.toString()
}

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
