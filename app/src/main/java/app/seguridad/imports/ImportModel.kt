package app.seguridad.imports

import app.seguridad.crypto.OtpAuthRead
import app.seguridad.crypto.OTPAUTH_MAX_CHARS
import app.seguridad.crypto.TotpSecret
import app.seguridad.crypto.isSupported
import app.seguridad.crypto.readOtpAuth
import app.seguridad.crypto.withoutBidiControls
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
 * [MAX_TITLE] characters and without control or bidi characters ([plainReportText]): never a password, a note or a key.
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

/**
 * [text] as the import report may show it (decision (c) of the owner: what comes from the file is data): every control
 * character (C0, C1, and the line and paragraph separators U+2028 and U+2029) becomes a space, and the characters that
 * turn text around (U+202A–U+202E, U+2066–U+2069, U+200E, U+200F, U+061C: [withoutBidiControls]) are dropped, so a
 * crafted title cannot make one line look like another. Then it is cut to [maxChars] UTF-16 units without splitting a
 * surrogate pair. [Skipped.title] always goes through it; the import screen uses it for anything else it shows.
 */
fun plainReportText(text: String, maxChars: Int = MAX_TITLE): String {
    val out = StringBuilder(minOf(text.length, maxChars.coerceAtLeast(0)))
    for (c in text) {
        if (out.length >= maxChars) break
        if (isBidiControl(c)) continue
        out.append(if (c.isISOControl() || c == '\u2028' || c == '\u2029') ' ' else c)
    }
    // A high surrogate left alone at the cut is dropped, so the text stays valid.
    if (out.isNotEmpty() && Character.isHighSurrogate(out[out.length - 1]) && out.length == maxChars) out.setLength(out.length - 1)
    return out.toString()
}

/** The characters [withoutBidiControls] drops (the same list; `ImportModelTest` checks that they stay the same). */
internal fun isBidiControl(c: Char): Boolean =
    c in '\u202A'..'\u202E' || c in '\u2066'..'\u2069' || c == '\u200E' || c == '\u200F' || c == '\u061C'

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
 */
internal class ImportReadBuilder(private val format: ImportFormat) {
    private val items = ArrayList<ImportedItem>()
    private val skipped = ArrayList<Skipped>()
    private var next = 0
    private var becameNotes = 0

    fun add(entry: VaultEntry, folders: List<String> = emptyList()) {
        require(entry.type in IMPORTED_TYPES) { "Unsupported imported type" }
        items += ImportedItem(next++, entry, folders)
    }

    fun skip(title: String, reason: SkipReason) {
        skipped += Skipped(next++, plainReportText(title), reason)
    }

    /** One element of another type (card, identity) entered as a note. */
    fun becameNote() {
        becameNotes++
    }

    /**
     * The 2FA of [link], as a TOTP entry named [title]: every code goes through [readOtpAuth] and [isSupported] (rule
     * of CRY-B02), so a setting Clavero does not calculate is refused, never changed. Otherwise a [Skipped]:
     * [SkipReason.TOTP_UNSUPPORTED] for such settings, [SkipReason.NOT_TOTP] for another otpauth kind (HOTP),
     * [SkipReason.FIELD_TOO_LONG] past [OTPAUTH_MAX_CHARS], and [SkipReason.BROKEN] for anything else.
     */
    fun addOtpAuth(link: String, title: String, folders: List<String> = emptyList()) {
        if (link.length > OTPAUTH_MAX_CHARS) return skip(title, SkipReason.FIELD_TOO_LONG)
        when (val r = readOtpAuth(link)) {
            is OtpAuthRead.Usable ->
                if (r.secret.isSupported()) add(importedEntry(EntryType.TOTP, title, totp = r.secret), folders)
                else skip(title, SkipReason.TOTP_UNSUPPORTED)
            is OtpAuthRead.UnsupportedSettings -> skip(title, SkipReason.TOTP_UNSUPPORTED)
            OtpAuthRead.NotUsable -> skip(title, if (isOtherOtpAuth(link)) SkipReason.NOT_TOTP else SkipReason.BROKEN)
        }
    }

    fun build(): ImportRead = ImportRead(format, items.toList(), skipped.toList(), becameNotes)
}

/** Refuses a file with more than [IMPORT_MAX_ITEMS] rows or elements. */
internal fun checkItemCount(count: Int) {
    if (count > IMPORT_MAX_ITEMS) throw ImportFailure(IMPORT_TOO_MANY_ITEMS)
}
