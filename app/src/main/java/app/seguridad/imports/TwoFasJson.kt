package app.seguridad.imports

import app.seguridad.vault.EntryType
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/*
 * The backup of 2FAS (spec «2FAS»), written from its public documentation: `services` and `groups` ({id, name}); each
 * service has `name`, `secret`, `otp` ({issuer, account, digits, period, algorithm, tokenType}) and `groupId`.
 */

/**
 * The services of a clear backup. `otp.tokenType` TOTP (or missing) enters as a TOTP through
 * [ImportReadBuilder.addTotp]; HOTP, STEAM and any other type are [SkipReason.NOT_TOTP]. The code's issuer is
 * `otp.issuer`, or the service's name; `groupId` gives the folder. The title is the name; if blank, the issuer, the
 * account or the type. Throws [ImportFailure] if `services` is not an array or has more than [IMPORT_MAX_ITEMS].
 */
fun readTwoFasPlain(root: JsonObject): ImportRead {
    val services = root["services"] as? JsonArray ?: throw ImportFailure(IMPORT_BROKEN)
    checkItemCount(services.size)
    val groups = folderNames(root["groups"], idKey = "id")
    val out = ImportReadBuilder(ImportFormat.TWOFAS)
    for (s in services) readService(s, groups, out)
    return out.build()
}

private fun readService(e: JsonElement, groups: Map<String, String>, out: ImportReadBuilder) {
    val o = e as? JsonObject ?: return out.skip("", SkipReason.BROKEN)
    val shown = shownText(o["name"])
    try {
        val name = o.text("name")
        val secret = o.text("secret")
        val folders = listOfNotNull(o.textOrNull("groupId")?.let(groups::get))
        val otp = o.obj("otp") ?: JsonObject(emptyMap())
        val tokenType = otp.textOrNull("tokenType") ?: "TOTP"
        val issuer = otp.text("issuer")
        val account = otp.text("account")
        val algorithm = otp.textOrNull("algorithm")
        val digits = otp.number("digits")
        val period = otp.number("period")
        if (!tokenType.equals("TOTP", ignoreCase = true)) return out.skip(shown, SkipReason.NOT_TOTP)
        if (!listOf(name, secret, issuer, account, algorithm.orEmpty()).all(::wellFormed)) return out.skip(shown, SkipReason.BROKEN)
        val title = name.ifBlank { issuer }.ifBlank { account }.ifBlank { EntryType.TOTP.label }
        if (title.length > MAX_TITLE) return out.skip(title, SkipReason.FIELD_TOO_LONG)
        out.addTotp(secret, issuer.ifBlank { name }, account, algorithm, digits, period, title, folders)
    } catch (w: WrongType) {
        out.skip(shown, SkipReason.BROKEN)
    }
}
