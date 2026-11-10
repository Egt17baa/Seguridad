package app.seguridad.imports

import app.seguridad.vault.EntryType
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/*
 * The backup of Aegis (spec «Aegis»), written from its public documentation. In clear, `db` is an object with
 * `entries` and `groups` ({uuid, name}); each entry has `type`, `name` (the account), `issuer`, `note`, `favorite`,
 * `info` ({secret, algo, digits, period}) and `groups` (uuids; older backups have one `group` name instead).
 */

/**
 * The entries of a clear `db`. Only `type: "totp"` enters, as a TOTP through [ImportReadBuilder.addTotp] (the rules
 * of CRY-B02: a setting Clavero does not calculate is [SkipReason.TOTP_UNSUPPORTED], never changed); `hotp`, `steam`,
 * `motp`, `yandex` and any other type are [SkipReason.NOT_TOTP]. Groups become folders; `favorite` and `note` are kept.
 * The title is the issuer; if blank, the account or the type. Throws [ImportFailure] if `entries` is not an array or
 * has more than [IMPORT_MAX_ITEMS] elements.
 */
fun readAegisPlain(db: JsonObject): ImportRead {
    val entries = db["entries"] as? JsonArray ?: throw ImportFailure(IMPORT_BROKEN)
    checkItemCount(entries.size)
    val groups = folderNames(db["groups"], idKey = "uuid")
    val out = ImportReadBuilder(ImportFormat.AEGIS)
    for (e in entries) readEntry(e, groups, out)
    return out.build()
}

private fun readEntry(e: JsonElement, groups: Map<String, String>, out: ImportReadBuilder) {
    val o = e as? JsonObject ?: return out.skip("", SkipReason.BROKEN)
    val shown = shownText(o["issuer"]).ifBlank { shownText(o["name"]) }
    try {
        val type = o.textOrNull("type") ?: throw WrongType()
        val issuer = o.text("issuer")
        val account = o.text("name")
        val note = o.text("note")
        val favorite = o.flag("favorite") ?: false
        val folders = o.list("groups")?.let { ids ->
            ids.map { id -> (id as? JsonPrimitive)?.takeIf { it.isString }?.content ?: throw WrongType() }.mapNotNull(groups::get).distinct()
        } ?: listOfNotNull(o.textOrNull("group")?.takeIf(::wellFormed))
        if (!type.equals("totp", ignoreCase = true)) return out.skip(shown, SkipReason.NOT_TOTP)
        val info = o.obj("info") ?: throw WrongType()
        val secret = info.text("secret")
        val algorithm = info.textOrNull("algo")
        val digits = info.number("digits")
        val period = info.number("period")
        if (!listOf(issuer, account, note, secret, algorithm.orEmpty()).all(::wellFormed)) return out.skip(shown, SkipReason.BROKEN)
        val title = issuer.ifBlank { account }.ifBlank { EntryType.TOTP.label }
        if (title.length > MAX_TITLE || note.length > MAX_NOTES) return out.skip(title, SkipReason.FIELD_TOO_LONG)
        out.addTotp(secret, issuer, account, algorithm, digits, period, title, folders, notes = note, favorite = favorite)
    } catch (w: WrongType) {
        out.skip(shown, SkipReason.BROKEN)
    }
}
