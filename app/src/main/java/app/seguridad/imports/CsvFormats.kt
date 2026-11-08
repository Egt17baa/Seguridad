package app.seguridad.imports

import app.seguridad.vault.EntryType
import java.net.URI
import java.util.Locale

/*
 * The CSV exports of browsers and password apps (spec «CSV de navegadores»), written from their public documentation:
 * - Chromium browsers and their password manager: name,url,username,password,note;
 * - Firefox: url,username,password,httpRealm,formActionOrigin,guid,timeCreated,timeLastUsed,timePasswordChanged;
 * - Apple: Title,URL,Username,Password,Notes,OTPAuth;
 * - Bitwarden (ruling R19; https://bitwarden.com/help/condition-bitwarden-import/): the personal vault's
 *   folder,favorite,type,name,notes,fields,reprompt,login_uri,login_username,login_password,login_totp, and the
 *   organisation's, with collections in place of folder and favorite. It is found by the names of its columns, in any
 *   order and case ([isBitwardenCsv]); its login_uri cell holds every URI of a login, separated by commas (pending
 *   confirmation with a real export: docs/superpowers/handoff/sdd-1.4.0/task-10-report.md, «Pendiente de confirmar»).
 * Columns are found by name, in any order and case; a column Clavero does not know is never given a meaning. The
 * mapping is fixed: nothing in the file decides which code runs.
 */

/** The columns a format gives a meaning to, by their names in lower case. */
private const val COL_NAME = "name"
private const val COL_TITLE = "title"
private const val COL_URL = "url"
private const val COL_USERNAME = "username"
private const val COL_PASSWORD = "password"
private const val COL_NOTE = "note"
private const val COL_NOTES = "notes"
private const val COL_OTPAUTH = "otpauth"

/** The columns every Bitwarden CSV has (ruling R19), and one of [BITWARDEN_CSV_PLACE] (personal or organisation). */
private val BITWARDEN_CSV_NEEDS = listOf("type", "name", "login_uri", "login_username", "login_password")
private val BITWARDEN_CSV_PLACE = listOf("folder", "collections")

/** Columns that only Firefox writes, so a header with url, username and password and one of them is Firefox's. */
private val FIREFOX_ONLY = setOf("httprealm", "formactionorigin", "guid")

/** Header names as compared: trimmed, lower case, without a BOM left in the first one. */
private fun columnName(s: String) = s.trim().removePrefix("\uFEFF").trim().lowercase(Locale.ROOT)

/** Each column name's position; with a name written twice, the first column counts. */
private class Columns(header: List<String>) {
    private val byName = HashMap<String, Int>()

    init {
        header.forEachIndexed { i, h -> byName.putIfAbsent(columnName(h), i) }
    }

    fun has(name: String) = name in byName

    fun cell(row: List<String>, name: String): String {
        val i = byName[name] ?: return ""
        return if (i < row.size) row[i] else ""
    }
}

/** The format a CSV header belongs to, or null if it is none of those Clavero reads. */
internal fun csvFormat(header: List<String>): ImportFormat? {
    val c = Columns(header)
    if (isBitwardenCsv(c)) return ImportFormat.BITWARDEN
    if (!(c.has(COL_URL) && c.has(COL_USERNAME) && c.has(COL_PASSWORD))) return null
    return when {
        c.has(COL_NAME) -> ImportFormat.BROWSER_CSV
        c.has(COL_TITLE) -> ImportFormat.APPLE_CSV
        FIREFOX_ONLY.any(c::has) -> ImportFormat.FIREFOX_CSV
        else -> null
    }
}

/**
 * Bitwarden's CSV: its columns by name, in any order and case, and nothing else asked of the header (a column it adds
 * later is passed over). The browser formats need url, username and password, which Bitwarden's has not.
 */
private fun isBitwardenCsv(c: Columns) = BITWARDEN_CSV_NEEDS.all(c::has) && BITWARDEN_CSV_PLACE.any(c::has)

/**
 * The entries of a CSV export, or null when its header is not one Clavero knows (then nothing else is read). Throws
 * [ImportFailure] for a broken file or one over the limits ([parseCsv]).
 */
internal fun readCsvImport(text: String): ImportRead? {
    val format = csvHeader(text)?.let(::csvFormat) ?: return null
    val rows = parseCsv(text)
    checkItemCount(rows.size - 1)
    val columns = Columns(rows[0])
    val out = ImportReadBuilder(format)
    for (r in 1 until rows.size) readRow(format, columns, rows[r], out)
    return out.build()
}

/** The host of [url] (never its user or password), or "" if it has none or is not a URL. */
internal fun hostOf(url: String): String {
    val u = url.trim()
    if (u.isEmpty() || u.length > MAX_URL) return ""
    return try {
        URI(u).host.orEmpty()
    } catch (e: Exception) {
        ""
    }
}

/**
 * One row as a LOGIN (spec «Entradas que se crean»): title, user, password, URL and notes as written, never trimmed
 * nor interpreted. In order:
 * - nothing but (at most) a title → [SkipReason.EMPTY];
 * - a NUL in a known column → [SkipReason.BROKEN];
 * - a field over its limit → [SkipReason.FIELD_TOO_LONG]: the whole row, never cut;
 * - otherwise the LOGIN; with an Apple `OTPAuth` link, a TOTP item right after it with the same title, or a
 *   [Skipped] for that 2FA alone ([ImportReadBuilder.addOtpAuth]).
 * The title is `name` or `Title` (the URL's host in Firefox, which has no title); if it is blank, the URL's host, the
 * user, or «Acceso».
 */
private fun readRow(format: ImportFormat, c: Columns, row: List<String>, out: ImportReadBuilder) {
    if (format == ImportFormat.BITWARDEN) return readBitwardenRow(c, row, out)
    val url = c.cell(row, COL_URL)
    val username = c.cell(row, COL_USERNAME)
    val password = c.cell(row, COL_PASSWORD)
    val notes = when (format) {
        ImportFormat.BROWSER_CSV -> c.cell(row, COL_NOTE)
        ImportFormat.APPLE_CSV -> c.cell(row, COL_NOTES)
        else -> ""
    }
    val otpauth = if (format == ImportFormat.APPLE_CSV) c.cell(row, COL_OTPAUTH) else ""
    val written = when (format) {
        ImportFormat.BROWSER_CSV -> c.cell(row, COL_NAME)
        ImportFormat.APPLE_CSV -> c.cell(row, COL_TITLE)
        else -> hostOf(url)
    }
    if (url.isBlank() && username.isBlank() && password.isEmpty() && notes.isBlank() && otpauth.isBlank()) {
        return out.skip(written, SkipReason.EMPTY)
    }
    if (listOf(written, url, username, password, notes, otpauth).any { '\u0000' in it }) {
        return out.skip(written, SkipReason.BROKEN)
    }
    val title = written.ifBlank { hostOf(url) }.ifBlank { username }.ifBlank { EntryType.LOGIN.label }
    if (title.length > MAX_TITLE || url.length > MAX_URL || username.length > MAX_USERNAME ||
        password.length > MAX_PASSWORD || notes.length > MAX_NOTES
    ) {
        return out.skip(title, SkipReason.FIELD_TOO_LONG)
    }
    out.add(importedEntry(EntryType.LOGIN, title, username = username, password = password, url = url, notes = notes))
    if (otpauth.isNotBlank()) out.addOtpAuth(otpauth, title)
}

/**
 * One row of Bitwarden's CSV, with the rules of its JSON reader ([readBitwardenPlain]): `type` `login` is a LOGIN
 * (title, user, password; `login_uri` split on its commas, each URI trimmed and the empty ones dropped, the first as
 * its URL and each other one a «URL: …» line, after the notes and before the `fields` cell, as written) and its
 * `login_totp` goes through the same strict path ([addLoginTotp]: an `otpauth://` link, a bare Base32 key or a Steam
 * code); `note` and any other type are a NOTE (another type counted in [ImportRead.becameNotes]). `favorite` is `1`;
 * the folder (or the organisation's collection) is the item's folder. In order: nothing → [SkipReason.EMPTY]; a NUL or
 * a lone surrogate → [SkipReason.BROKEN]; a field over its limit → [SkipReason.FIELD_TOO_LONG], never cut.
 */
private fun readBitwardenRow(c: Columns, row: List<String>, out: ImportReadBuilder) {
    val type = c.cell(row, "type").trim().lowercase(Locale.ROOT)
    val login = type == "login"
    val name = c.cell(row, COL_NAME)
    val uris = if (login) loginUris(c.cell(row, "login_uri")) else emptyList()
    val url = uris.firstOrNull().orEmpty()
    val username = if (login) c.cell(row, "login_username") else ""
    val password = if (login) c.cell(row, "login_password") else ""
    val totp = if (login) c.cell(row, "login_totp") else ""
    val fields = c.cell(row, "fields")
    val folder = (if (c.has("collections")) c.cell(row, "collections") else c.cell(row, "folder")).takeIf { it.isNotBlank() }
    val notes = NotesBuilder().apply {
        text(c.cell(row, COL_NOTES))
        for (other in uris.drop(1)) line(NOTE_LINE_URL, other)
        text(fields)
    }
    if (url.isBlank() && username.isBlank() && password.isEmpty() && notes.isEmpty && totp.isBlank() && (login || name.isBlank())) {
        return out.skip(name, SkipReason.EMPTY)
    }
    if (notes.broken || !listOf(name, url, username, password, totp, folder.orEmpty()).all(::wellFormed)) {
        return out.skip(name, SkipReason.BROKEN)
    }
    val entryType = if (login) EntryType.LOGIN else EntryType.NOTE
    val title = if (login) {
        name.ifBlank { hostOf(url) }.ifBlank { username }.ifBlank { EntryType.LOGIN.label }
    } else {
        name.ifBlank { EntryType.NOTE.label }
    }
    if (title.length > MAX_TITLE || url.length > MAX_URL || username.length > MAX_USERNAME || password.length > MAX_PASSWORD ||
        notes.tooLong
    ) {
        return out.skip(title, SkipReason.FIELD_TOO_LONG)
    }
    val folders = listOfNotNull(folder)
    val favorite = c.cell(row, "favorite").trim() == "1"
    out.add(importedEntry(entryType, title, username = username, password = password, url = url, notes = notes.build(), favorite = favorite), folders)
    if (!login && type != "note") out.becameNote()
    if (totp.isNotBlank()) addLoginTotp(totp, title, folders, out)
}

/** The URIs of a `login_uri` cell: split on its commas, each trimmed, the empty ones dropped (as the JSON reader passes them over). */
private fun loginUris(cell: String): List<String> = cell.split(',').map { it.trim() }.filter { it.isNotEmpty() }
