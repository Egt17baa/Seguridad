package app.seguridad.imports

import app.seguridad.core.appJson
import app.seguridad.vault.BACKUP_MAX_ENTRY_BYTES
import app.seguridad.vault.EntryType
import app.seguridad.vault.MAX_TAGS
import app.seguridad.vault.VaultEntry
import app.seguridad.vault.normalizeTags
import app.seguridad.vault.totpKeyOf
import app.seguridad.vault.withNewPassword
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/*
 * The merge plan of «Importar de otra app» (spec «El plan de fusión»): pure functions from what a reader made of a file
 * ([ImportRead]) and the vault's entries to the preview ([MergePlan]) and the merged list ([mergedEntries]) that
 * [app.seguridad.vault.VaultStore.applyImport] saves once. What comes from the file is data (owner's decision (c)): it is
 * stored as it was read, never run, opened or followed; only normalised copies of it are compared to find duplicates.
 */

/** What to do with an item that is already in the vault. [SKIP] is the default (owner's decision 3). */
enum class DuplicateChoice { SKIP, KEEP_BOTH, REPLACE }

/** An [item] of the file that matches the vault's entry [existingId]. */
data class Duplicate(val item: ImportedItem, val existingId: String)

/**
 * The preview of an import, built against the vault at [revision] ([app.seguridad.vault.VaultStore.revision]); saving it
 * after any other change is refused ([ApplyResult.Stale]). [newItems] are not in the vault, [duplicates] are, and
 * [repeatedInFile] counts the items equal to an earlier one of the same file (they never enter). [skipped] and
 * [becameNotes] are the reader's. [newTags] are the tags the [newItems] bring that no entry of the vault has (the
 * duplicates may bring more, if the user chooses to add or replace them).
 */
data class MergePlan(
    val revision: Long,
    val format: ImportFormat,
    val newItems: List<ImportedItem>,
    val duplicates: List<Duplicate>,
    val repeatedInFile: Int,
    val skipped: List<Skipped>,
    val becameNotes: Int,
    val newTags: Set<String>,
)

/** How [app.seguridad.vault.VaultStore.applyImport] ended. */
sealed interface ApplyResult {
    /** Saved, in a single write: [added] new entries and [replaced] entries of the vault changed by «Reemplazar». */
    data class Done(val added: Int, val replaced: Int) : ApplyResult

    /** The vault changed since the plan was built, or it is locked: nothing was saved. */
    data object Stale : ApplyResult

    /**
     * The vault would no longer fit in a `.sgbak` copy (spec «Límites de la bóveda resultante»): nothing was saved.
     * [overBytes] is how much the vault file passes [BACKUP_MAX_ENTRY_BYTES] (or the copy [app.seguridad.vault.BACKUP_MAX_TOTAL_BYTES]);
     * when the file is refused before it is encrypted, it is a lower bound. [overFiles] is how many files the copy
     * would have beyond [app.seguridad.vault.BACKUP_MAX_ENTRIES] (attachments: an import adds none, but it is checked).
     */
    data class TooBig(val overBytes: Long, val overFiles: Int = 0) : ApplyResult
}

/**
 * Compares [read] with [existing] (spec «Duplicado»):
 * - a `LOGIN` matches by title (trimmed, case ignored), user (trimmed) and the host of the URL (lowercase, without
 *   `www.`); when either side has no URL, title and user are enough;
 * - a `TOTP` matches by its key, the rule of `totpImportEntries` ([totpKeyOf]);
 * - a `NOTE` matches by title (as a login's) and text (trimmed).
 * Only entries of the same type are compared, so `IDENTITY`, `CONTACT` and `SEED` entries never match. The file is
 * checked against itself first: an item equal to an earlier one is only counted ([MergePlan.repeatedInFile]). Linear
 * in the size of both lists (hash maps, no pairwise comparison).
 */
fun planMerge(read: ImportRead, existing: List<VaultEntry>, revision: Long): MergePlan {
    val inVault = Matcher<String>()
    for (e in existing) inVault.add(e, e.id)
    val inFile = Matcher<Unit>()
    val newItems = ArrayList<ImportedItem>()
    val duplicates = ArrayList<Duplicate>()
    var repeated = 0
    for (item in read.items) {
        if (inFile.find(item.entry) != null) {
            repeated++
            continue
        }
        inFile.add(item.entry, Unit)
        val id = inVault.find(item.entry)
        if (id != null) duplicates += Duplicate(item, id) else newItems += item
    }
    val had = existing.flatMapTo(HashSet()) { it.tags }
    val newTags = LinkedHashSet<String>()
    for (item in newItems) importTags(item.folders, read.format).filterTo(newTags) { it !in had }
    return MergePlan(revision, read.format, newItems, duplicates, repeated, read.skipped, read.becameNotes, newTags)
}

/**
 * The tags of an entry imported from [format] with [folders] (spec «Etiquetas»): the origin tag first, then each folder
 * through [normalizeTags] (its commas become spaces, so a folder stays one tag, and `a/b` stays `a/b`), cut to
 * [app.seguridad.vault.MAX_TAG_LENGTH] code points. At most [MAX_TAGS]: over it, the last folders go, never the origin tag.
 */
fun importTags(folders: List<String>, format: ImportFormat): List<String> {
    val tags = LinkedHashSet<String>()
    tags += format.originTag
    for (folder in folders) {
        if (tags.size >= MAX_TAGS) break
        normalizeTags(folder.replace(',', ' ')).firstOrNull()?.let { tags += it }
    }
    return tags.toList()
}

/**
 * The vault after the import of [plan] into [existing] (the list the plan was built against): [existing] in its order,
 * with the duplicates chosen for «Reemplazar» changed in place by [replaceWith], then the new entries in the file's
 * order. Each duplicate takes its choice from [overrides] (by [ImportedItem.index]) or [default]. A new entry gets
 * [newId], [now] as both dates, [importTags] and no history or attachments. Nothing else of [existing] changes: an
 * entry is only touched when it is a duplicate chosen for «Reemplazar» of the same type, never `IDENTITY` or `CONTACT`.
 * Two duplicates of the same entry are applied in turn.
 */
fun mergedEntries(
    plan: MergePlan,
    default: DuplicateChoice,
    overrides: Map<Int, DuplicateChoice>,
    existing: List<VaultEntry>,
    now: Long,
    newId: () -> String,
    zone: ZoneId = ZoneId.systemDefault(),
): List<VaultEntry> {
    val out = existing.toMutableList()
    val position = HashMap<String, Int>(existing.size * 2)
    existing.forEachIndexed { i, e -> position.putIfAbsent(e.id, i) }
    val duplicateOf = plan.duplicates.associateBy { it.item.index }
    val added = ArrayList<VaultEntry>()
    for (item in (plan.newItems + plan.duplicates.map { it.item }).sortedBy { it.index }) {
        val tagged = item.entry.copy(tags = importTags(item.folders, plan.format), passwordHistory = emptyList(), attachments = emptyList())
        val duplicate = duplicateOf[item.index]
        when (if (duplicate == null) DuplicateChoice.KEEP_BOTH else overrides[item.index] ?: default) {
            DuplicateChoice.SKIP -> Unit
            DuplicateChoice.KEEP_BOTH -> added += tagged.copy(id = newId(), createdAt = now, updatedAt = now)
            DuplicateChoice.REPLACE -> {
                val at = position[duplicate!!.existingId] ?: continue
                out[at] = replaceWith(out[at], tagged, now, zone)
            }
        }
    }
    return out + added
}

/**
 * [existing] after «Reemplazar» with [incoming] (spec «Las opciones», so that replacing never loses anything):
 * - it keeps its id, type, `createdAt`, favourite and attachments;
 * - it takes the title, user, URL and 2FA of [incoming] only where they are not empty (blank counts as empty);
 * - notes: [incoming]'s if [existing] has none; if both have and they differ, [existing]'s, then a line
 *   [importedOnHeading] with the day of [now] in [zone], then [incoming]'s;
 * - tags: its own, then [incoming]'s it lacks, up to [MAX_TAGS] (its own are never dropped);
 * - a new non-empty password goes through [withNewPassword], so the old one goes to the history.
 * An `IDENTITY` or `CONTACT` [existing], or an [incoming] of another type, is returned unchanged.
 */
fun replaceWith(existing: VaultEntry, incoming: VaultEntry, now: Long, zone: ZoneId = ZoneId.systemDefault()): VaultEntry {
    if (existing.type.isPersonal || incoming.type != existing.type) return existing
    val notes = when {
        incoming.notes.isBlank() || incoming.notes == existing.notes -> existing.notes
        existing.notes.isBlank() -> incoming.notes
        else -> {
            val day = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            existing.notes + "\n\n" + importedOnHeading(day.dayOfMonth, day.monthValue, day.year) + "\n" + incoming.notes
        }
    }
    val merged = existing.copy(
        title = incoming.title.ifBlank { existing.title },
        username = incoming.username.ifBlank { existing.username },
        url = incoming.url.ifBlank { existing.url },
        totp = incoming.totp ?: existing.totp,
        notes = notes,
        tags = (existing.tags + incoming.tags.filter { it !in existing.tags }).distinct().take(maxOf(MAX_TAGS, existing.tags.size)),
        updatedAt = now,
    )
    return if (incoming.password.isEmpty()) merged else merged.withNewPassword(incoming.password, now)
}

/**
 * The preview's estimate of the vault file holding [entries]: `ceil(plainJsonBytes * 4 / 3) + 4096`, the entries' JSON
 * in Base64 (as [app.seguridad.vault.VaultCrypto] stores them) plus room for the nonce, the tag and the file's other
 * fields, so it is never below the real file. If even the entries' characters alone ([plainCharsLowerBound], fewer
 * than their JSON bytes) give an estimate over [BACKUP_MAX_ENTRY_BYTES], that one is returned without encoding them:
 * the answer (too big) is the same, and a huge import is not turned into an even larger string first.
 * [app.seguridad.vault.VaultStore.applyImport]'s exact check of the encrypted file decides.
 */
fun estimatedVaultFileBytes(entries: List<VaultEntry>): Long {
    val low = base64Estimate(plainCharsLowerBound(entries))
    if (low > BACKUP_MAX_ENTRY_BYTES) return low
    return base64Estimate(utf8Length(appJson.encodeToString(entries)))
}

/** How much [estimatedVaultFileBytes] passes [BACKUP_MAX_ENTRY_BYTES]; 0 when it fits. */
fun estimatedOverBytes(entries: List<VaultEntry>): Long = (estimatedVaultFileBytes(entries) - BACKUP_MAX_ENTRY_BYTES).coerceAtLeast(0)

private fun base64Estimate(plain: Long): Long = (plain * 4 + 2) / 3 + 4096

/**
 * The UTF-16 units of every text field of [entries]: never more than the UTF-8 bytes of their JSON (a UTF-16 unit is at
 * least one UTF-8 byte, a surrogate pair is four, and escaping only adds), so a lower bound of it.
 */
internal fun plainCharsLowerBound(entries: List<VaultEntry>): Long {
    var n = 0L
    for (e in entries) {
        n += e.id.length + e.title.length + e.username.length + e.password.length + e.url.length + e.notes.length +
            e.seedWords.length + e.identitySeed.length + e.card.length
        for (t in e.tags) n += t.length
        for (h in e.passwordHistory) n += h.password.length
        e.totp?.let { n += it.secret.length + it.issuer.length + it.account.length }
        for (a in e.attachments) n += a.id.length + a.name.length + a.sha256.length + a.key.length
    }
    return n
}

/** The bytes of [s] in UTF-8 as `toByteArray(Charsets.UTF_8)` gives them (a lone surrogate is one byte, «?»), without building them. */
internal fun utf8Length(s: String): Long {
    var n = 0L
    var i = 0
    while (i < s.length) {
        val c = s[i]
        n += when {
            c.code < 0x80 -> 1
            c.code < 0x800 -> 2
            c.isHighSurrogate() && i + 1 < s.length && s[i + 1].isLowSurrogate() -> {
                i++
                4
            }
            c.isSurrogate() -> 1
            else -> 3
        }
        i++
    }
    return n
}

/** Title as compared: trimmed, case ignored. */
private fun titleKey(title: String) = title.trim().lowercase(Locale.ROOT)

/**
 * The host of [url] as compared: lowercase, without scheme, user, port, path or `www.`; null for no URL. A URL whose
 * host comes out empty is compared whole (trimmed, lowercase). Plain string work, never a parser that could throw or
 * resolve anything.
 */
internal fun urlHost(url: String): String? {
    val t = url.trim()
    if (t.isEmpty()) return null
    val scheme = t.indexOf("://")
    val rest = if (scheme >= 0) t.substring(scheme + 3) else t
    val end = rest.indexOfFirst { it == '/' || it == '?' || it == '#' }
    val authority = (if (end >= 0) rest.substring(0, end) else rest).substringAfterLast('@')
    val host = if (authority.startsWith('[')) authority.substringBefore(']') + "]" else authority.substringBefore(':')
    val key = host.lowercase(Locale.ROOT).removePrefix("www.")
    return key.ifEmpty { t.lowercase(Locale.ROOT) }
}

/**
 * The duplicate rules over a set of entries, with constant-time lookups. For logins, each title and user keeps the first
 * entry, the first without a URL and the first of each host, so «either side has no URL» needs no scan.
 */
private class Matcher<T : Any> {
    private class Logins<T>(val any: T) {
        var hostless: T? = null
        val byHost = HashMap<String, T>()
    }

    private val logins = HashMap<Pair<String, String>, Logins<T>>()
    private val codes = HashMap<String, T>()
    private val notes = HashMap<Pair<String, String>, T>()

    fun add(e: VaultEntry, value: T) {
        when (e.type) {
            EntryType.LOGIN -> {
                val group = logins.getOrPut(titleKey(e.title) to e.username.trim()) { Logins(value) }
                val host = urlHost(e.url)
                if (host == null) {
                    if (group.hostless == null) group.hostless = value
                } else {
                    group.byHost.putIfAbsent(host, value)
                }
            }
            EntryType.TOTP -> e.totp?.let { totpKeyOf(it.secret) }?.let { codes.putIfAbsent(it, value) }
            EntryType.NOTE -> notes.putIfAbsent(titleKey(e.title) to e.notes.trim(), value)
            else -> Unit // Seeds, identities and contacts are never imported, so never compared.
        }
    }

    fun find(e: VaultEntry): T? = when (e.type) {
        EntryType.LOGIN -> logins[titleKey(e.title) to e.username.trim()]?.let { g ->
            val host = urlHost(e.url)
            if (host == null) g.any else g.byHost[host] ?: g.hostless
        }
        EntryType.TOTP -> e.totp?.let { totpKeyOf(it.secret) }?.let { codes[it] }
        EntryType.NOTE -> notes[titleKey(e.title) to e.notes.trim()]
        else -> null
    }
}
