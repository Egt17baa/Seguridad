package app.seguridad.imports

import app.seguridad.crypto.MIGRATION_MAX_URI_CHARS
import app.seguridad.crypto.MigrationLeftOutKind
import app.seguridad.crypto.OTPAUTH_MAX_CHARS
import app.seguridad.crypto.isSupported
import app.seguridad.crypto.isUsableTotpKey
import app.seguridad.crypto.otpName
import app.seguridad.crypto.parseOtpMigration
import app.seguridad.crypto.readOtpAuth
import app.seguridad.vault.EntryType
import java.net.URI
import java.net.URLDecoder

/*
 * A list of `otpauth://` links (spec «Lista de otpauth://»): one per line, in a `.txt` or a CSV of one column. Lines
 * end at LF, CRLF or a CR alone; blank lines are passed over; a line between double quotes is the text inside them
 * (`""` is one `"`). Each link goes through the vault's link reader with the rules of CRY-B02, and each
 * `otpauth-migration://` line through the existing migration reader.
 */

private const val OTPAUTH = "otpauth://"
private const val MIGRATION = "otpauth-migration://"

/** The list's entries, or null when its first line that is not blank is not a link (then it is not a list). */
internal fun readOtpAuthListImport(text: String): ImportRead? = if (startsWithLink(text)) readOtpAuthList(text) else null

/**
 * Every line of [text], in order. A line is cut nowhere and copied only when it is within its limit, so a hostile
 * file stops early with bounded memory (decision (a) of the owner):
 * - more than [IMPORT_MAX_ITEMS] lines that are not blank, or more than that many entries in all after the migration
 *   lines are expanded (each may hold 100 codes), throws [ImportFailure] ([IMPORT_TOO_MANY_ITEMS]) at once;
 * - an `otpauth://` line over [OTPAUTH_MAX_CHARS] or a migration line over [MIGRATION_MAX_URI_CHARS] is
 *   [SkipReason.FIELD_TOO_LONG];
 * - a line that is not a link, or has a NUL or a lone surrogate, is [SkipReason.BROKEN].
 * A link lists as its service or account; one that names neither as «Línea N» ([importLineTitle]). A migration line
 * gives its codes first, then the accounts it leaves out, each with its reason.
 */
fun readOtpAuthList(text: String): ImportRead {
    val out = ImportReadBuilder(ImportFormat.OTPAUTH)
    var pos = 0
    var lineNo = 0
    var lines = 0
    val n = text.length
    while (pos < n) {
        var end = pos
        while (end < n && text[end] != '\n' && text[end] != '\r') end++
        val next = if (end < n && text[end] == '\r' && end + 1 < n && text[end + 1] == '\n') end + 2 else end + 1
        lineNo++
        var s = pos
        var e = end
        pos = next
        while (s < e && text[s].isWhitespace()) s++
        while (e > s && text[e - 1].isWhitespace()) e--
        if (s == e) continue
        if (++lines > IMPORT_MAX_ITEMS) throw ImportFailure(IMPORT_TOO_MANY_ITEMS)
        val quoted = e - s >= 2 && text[s] == '"' && text[e - 1] == '"'
        if (quoted) {
            s++
            e--
        }
        readLine(text, s, e, quoted, importLineTitle(lineNo), out)
    }
    return out.build()
}

private fun readLine(text: String, s: Int, e: Int, quoted: Boolean, lineTitle: String, out: ImportReadBuilder) {
    val migration = text.regionMatches(s, MIGRATION, 0, MIGRATION.length, ignoreCase = true)
    val link = migration || text.regionMatches(s, OTPAUTH, 0, OTPAUTH.length, ignoreCase = true)
    if (!link) return out.skip(lineTitle, SkipReason.BROKEN)
    if (e - s > (if (migration) MIGRATION_MAX_URI_CHARS else OTPAUTH_MAX_CHARS)) return out.skip(lineTitle, SkipReason.FIELD_TOO_LONG)
    val line = text.substring(s, e).let { if (quoted) it.replace("\"\"", "\"") else it }
    if (!wellFormed(line)) return out.skip(lineTitle, SkipReason.BROKEN)
    if (migration) addMigration(line, lineTitle, out) else out.addOtpAuth(line, linkTitle(line) ?: lineTitle)
}

/**
 * The codes of one `otpauth-migration://` line. Every code is checked again with the vault's rules ([isSupported] and
 * a usable key) before it enters, and one whose issuer or account the reader kept cut is [SkipReason.FIELD_TOO_LONG].
 * Each account the reader leaves out is listed with its reason, by its kind, never by parsing its text. A line the
 * reader refuses is [SkipReason.BROKEN].
 */
private fun addMigration(line: String, lineTitle: String, out: ImportReadBuilder) {
    val result = parseOtpMigration(line) ?: return out.skip(lineTitle, SkipReason.BROKEN)
    for ((i, code) in result.imported.withIndex()) {
        val title = code.issuer.ifBlank { code.account }.ifBlank { EntryType.TOTP.label }
        when {
            result.importedNamesCut.getOrElse(i) { false } -> out.skip(title, SkipReason.FIELD_TOO_LONG)
            !isUsableTotpKey(code.secret) -> out.skip(title, SkipReason.BROKEN)
            !code.isSupported() -> out.skip(title, SkipReason.TOTP_UNSUPPORTED)
            else -> out.add(importedEntry(EntryType.TOTP, title, totp = code))
        }
    }
    for (left in result.leftOut) {
        val reason = when (left.kind) {
            MigrationLeftOutKind.UNSUPPORTED_SETTINGS -> SkipReason.TOTP_UNSUPPORTED
            MigrationLeftOutKind.NOT_TOTP -> SkipReason.NOT_TOTP
            MigrationLeftOutKind.SHORT_KEY -> SkipReason.BROKEN
        }
        out.skip(left.issuer.ifBlank { left.account }.ifBlank { EntryType.TOTP.label }, reason)
    }
}

/**
 * The service (or else the account) a link names, as [readOtpAuth] keeps it; for a link of another kind (HOTP), the
 * same from its label. Null if it names none. Never the key.
 */
private fun linkTitle(line: String): String? {
    val (issuer, account) = readOtpAuth(line).names ?: labelNames(line) ?: return null
    return issuer.ifBlank { account }.ifBlank { null }
}

private fun labelNames(line: String): Pair<String, String>? = try {
    val label = URLDecoder.decode(URI(line).rawPath.orEmpty().removePrefix("/"), "UTF-8")
    otpName(label.substringBefore(':', "")) to otpName(label.substringAfter(':'))
} catch (e: Exception) {
    null
}

/** If the first line that is not blank starts with a link (after an opening quote), in any case. */
private fun startsWithLink(text: String): Boolean {
    var i = 0
    while (i < text.length && text[i].isWhitespace()) i++
    if (i < text.length && text[i] == '"') i++
    return text.regionMatches(i, OTPAUTH, 0, OTPAUTH.length, ignoreCase = true) ||
        text.regionMatches(i, MIGRATION, 0, MIGRATION.length, ignoreCase = true)
}
