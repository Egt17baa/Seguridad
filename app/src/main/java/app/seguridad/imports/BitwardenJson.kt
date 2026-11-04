package app.seguridad.imports

import app.seguridad.crypto.OTPAUTH_MAX_CHARS
import app.seguridad.vault.EntryType
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/*
 * The JSON export of Bitwarden (spec «Bitwarden JSON en claro»), written from its public documentation: `encrypted`,
 * `folders` ({id, name}) and `items`, with `type` 1 login, 2 secure note, 3 card, 4 identity and 5 SSH key. Every value
 * is taken by its fixed name (ImportJson.kt); a name Clavero does not know is never given a meaning.
 */

/**
 * A JSON with `encrypted` or `items`:
 * - `encrypted: true` with `passwordProtected: true` → [ReadResult.NeedsPassword] (Task 10 opens it);
 * - `encrypted: true` without it → [IMPORT_BITWARDEN_ACCOUNT_ENCRYPTED]: only that account can open it;
 * - `encrypted: false` or absent → [readBitwardenPlain].
 * Any other type for these two names is [IMPORT_BROKEN].
 */
internal fun readBitwarden(root: JsonObject): ReadResult {
    val encrypted = try {
        root.flag("encrypted") ?: false
    } catch (e: WrongType) {
        throw ImportFailure(IMPORT_BROKEN)
    }
    if (!encrypted) return ReadResult.Ok(readBitwardenPlain(root))
    val protected = try {
        root.flag("passwordProtected") ?: false
    } catch (e: WrongType) {
        throw ImportFailure(IMPORT_BROKEN)
    }
    if (protected) return ReadResult.NeedsPassword(ImportFormat.BITWARDEN)
    throw ImportFailure(IMPORT_BITWARDEN_ACCOUNT_ENCRYPTED)
}

/**
 * The elements of a plain export (spec «Entradas que se crean»), in the file's order:
 * - 1 → a LOGIN with the first URI as its URL; the notes are the item's notes, then a «URL: …» line per other URI
 *   and a «Name: value» line per custom field. `login.totp` (a link, or a bare key as the app lets one type it) adds a
 *   TOTP with the same title and folders, through [ImportReadBuilder.addOtpAuth]; a `steam://` code is
 *   [SkipReason.NOT_TOTP]. Passkeys (`login.fido2Credentials`) add a [SkipReason.PASSKEY]; the login still enters.
 *   A login that is `null` or missing is [SkipReason.BROKEN];
 * - 2 → a NOTE; 3 and 4 → a NOTE with a line per field ([BITWARDEN_CARD_LINES], [BITWARDEN_IDENTITY_LINES]), counted
 *   in [ImportRead.becameNotes];
 * - 5 → [SkipReason.SSH_KEY]; any other type → [SkipReason.BROKEN].
 * `folderId` gives the folder's name; `favorite` is kept. Every value of an item is read before anything of it is
 * added, so an item is whole or listed, never half. Throws [ImportFailure] if `items` is not an array or has more
 * than [IMPORT_MAX_ITEMS] elements.
 */
fun readBitwardenPlain(json: JsonObject): ImportRead {
    val items = json["items"] as? JsonArray ?: throw ImportFailure(IMPORT_BROKEN)
    checkItemCount(items.size)
    val folders = folderNames(json["folders"], idKey = "id")
    val out = ImportReadBuilder(ImportFormat.BITWARDEN)
    for (item in items) readItem(item, folders, out)
    return out.build()
}

private const val TYPE_LOGIN = 1
private const val TYPE_NOTE = 2
private const val TYPE_CARD = 3
private const val TYPE_IDENTITY = 4
private const val TYPE_SSH_KEY = 5

/** What an item gives, read whole before anything is added. */
private class ItemRead(
    val type: EntryType,
    val title: String,
    val username: String,
    val password: String,
    val url: String,
    val notes: NotesBuilder,
    val totp: String,
    val passkeys: Boolean,
    val favorite: Boolean,
    val folders: List<String>,
    val becameNote: Boolean,
)

private fun readItem(e: JsonElement, folders: Map<String, String>, out: ImportReadBuilder) {
    val o = e as? JsonObject ?: return out.skip("", SkipReason.BROKEN)
    val shown = shownText(o["name"])
    val read = try {
        readFields(o, folders) ?: return out.skip(shown, SkipReason.SSH_KEY)
    } catch (w: WrongType) {
        return out.skip(shown, SkipReason.BROKEN)
    }
    emit(read, shown, out)
}

/** Every value of [o], or null for an SSH key; [WrongType] for a value of the wrong type or an unknown type. */
private fun readFields(o: JsonObject, folders: Map<String, String>): ItemRead? {
    val type = o.number("type") ?: throw WrongType()
    if (type == TYPE_SSH_KEY) return null
    if (type !in TYPE_LOGIN..TYPE_IDENTITY) throw WrongType()
    val name = o.text("name")
    val ownNotes = o.text("notes")
    val favorite = o.flag("favorite") ?: false
    val folder = o.textOrNull("folderId")?.let { folders[it] }
    val notes = NotesBuilder()
    var username = ""
    var password = ""
    var url = ""
    var totp = ""
    var passkeys = false
    when (type) {
        TYPE_LOGIN -> {
            // A login of null, missing or of another type: there is nothing to enter (review focus 1).
            val login = o["login"] as? JsonObject ?: throw WrongType()
            username = login.text("username")
            password = login.text("password")
            totp = login.text("totp")
            passkeys = (login.list("fido2Credentials")?.size ?: 0) > 0
            notes.text(ownNotes)
            var first = true
            for (u in login.list("uris").orEmpty()) {
                val uri = (u as? JsonObject ?: throw WrongType()).text("uri")
                if (first) url = uri else notes.line(NOTE_LINE_URL, uri)
                first = false
            }
        }
        TYPE_NOTE -> notes.text(ownNotes)
        else -> {
            val (key, lines) = if (type == TYPE_CARD) "card" to BITWARDEN_CARD_LINES else "identity" to BITWARDEN_IDENTITY_LINES
            val fields = o.obj(key)
            for ((jsonName, label) in lines) notes.line(label, fields?.text(jsonName).orEmpty())
            notes.text(ownNotes)
        }
    }
    for (f in o.list("fields").orEmpty()) {
        val field = f as? JsonObject ?: throw WrongType()
        // A linked field (type 3) has no value of its own: it gives no line.
        notes.line(field.text("name"), field.textOrNull("value") ?: continue)
    }
    val entryType = if (type == TYPE_LOGIN) EntryType.LOGIN else EntryType.NOTE
    return ItemRead(
        entryType, name, username, password, url, notes, totp, passkeys, favorite, listOfNotNull(folder),
        becameNote = type == TYPE_CARD || type == TYPE_IDENTITY,
    )
}

/**
 * One item as [readBitwardenPlain] says, with the checks of the CSV readers, in order: nothing but a title →
 * [SkipReason.EMPTY]; a NUL or a lone surrogate → [SkipReason.BROKEN]; a field over its limit →
 * [SkipReason.FIELD_TOO_LONG] (never cut). The title is the name; if blank, the URL's host, the user or the type.
 */
private fun emit(r: ItemRead, shown: String, out: ImportReadBuilder) {
    val nothing = r.url.isBlank() && r.username.isBlank() && r.password.isEmpty() && r.notes.isEmpty && r.totp.isBlank()
    if (nothing && (r.type == EntryType.LOGIN || r.title.isBlank())) {
        out.skip(shown, SkipReason.EMPTY)
        if (r.passkeys) out.skip(shown, SkipReason.PASSKEY)
        return
    }
    if (r.notes.broken || !listOf(r.title, r.username, r.password, r.url, r.totp).all(::wellFormed)) {
        return out.skip(shown, SkipReason.BROKEN)
    }
    val title = if (r.type == EntryType.LOGIN) {
        r.title.ifBlank { hostOf(r.url) }.ifBlank { r.username }.ifBlank { EntryType.LOGIN.label }
    } else {
        r.title.ifBlank { EntryType.NOTE.label }
    }
    if (title.length > MAX_TITLE || r.url.length > MAX_URL || r.username.length > MAX_USERNAME ||
        r.password.length > MAX_PASSWORD || r.notes.tooLong
    ) {
        return out.skip(title, SkipReason.FIELD_TOO_LONG)
    }
    out.add(
        importedEntry(r.type, title, username = r.username, password = r.password, url = r.url, notes = r.notes.build(), favorite = r.favorite),
        r.folders,
    )
    if (r.becameNote) out.becameNote()
    if (r.totp.isNotBlank()) addLoginTotp(r.totp, title, r.folders, out)
    if (r.passkeys) out.skip(title, SkipReason.PASSKEY)
}

/**
 * The 2FA of a login, as Bitwarden stores it: an `otpauth://` link; a Steam code ([BITWARDEN_STEAM_PREFIX]), which is
 * [SkipReason.NOT_TOTP]; or a bare Base32 key, which is a link with only that key (so the defaults SHA-1, 6 digits and
 * 30 s), percent-encoded into its parameter. Both go through [ImportReadBuilder.addOtpAuth].
 */
private fun addLoginTotp(totp: String, title: String, folders: List<String>, out: ImportReadBuilder) {
    val t = totp.trim()
    when {
        t.startsWith("otpauth://", ignoreCase = true) -> out.addOtpAuth(t, title, folders)
        t.startsWith(BITWARDEN_STEAM_PREFIX, ignoreCase = true) -> out.skip(title, SkipReason.NOT_TOTP)
        t.length > OTPAUTH_MAX_CHARS -> out.skip(title, SkipReason.FIELD_TOO_LONG)
        else -> out.addTotp(t, issuer = "", account = "", algorithm = null, digits = null, period = null, title = title, folders = folders)
    }
}
