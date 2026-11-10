package app.seguridad.imports

import app.seguridad.core.appJson
import app.seguridad.tools.qr.ParsedLink
import app.seguridad.tools.qr.SPECIAL_SCHEMES
import app.seguridad.tools.qr.asBrowserReadsIt
import app.seguridad.tools.qr.parseLink
import app.seguridad.tools.qr.schemeOf
import app.seguridad.vault.BACKUP_MAX_ENTRY_BYTES
import app.seguridad.vault.EntryType
import app.seguridad.vault.MAX_TAGS
import app.seguridad.vault.VaultEntry
import app.seguridad.vault.VaultStore
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
 * [becameNotes] are the reader's. [newTags] are the tags the [newItems] bring that no entry of the vault has, within
 * [IMPORT_MAX_NEW_TAGS] ([TagBudget]; the preview's own list comes from [mergeImport], with the choices) (the
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
 * Compares [read] with [existing] (spec «Duplicado», with rulings R34 and R36). [existing] and [revision] must be read
 * together, from one [VaultStore.importSnapshot] (the import screen does that, and so does [planImport]).
 * - a `LOGIN` matches by title, user and the scheme and host of the URL. Title and user are compared as they are shown
 *   ([compareKey]: without controls, direction marks and what draws nothing, in NFKC, every run of spaces as one and
 *   none at the ends; the title without case, the user with it, since a different capital is visible), so a look-alike
 *   is a duplicate, not a shadow entry (RT-2, RT-6, RT-7). The host is the one a browser connects to, read by
 *   [parseLink] ([urlHost]: «\» ends the authority in a web link, a text without a scheme is read as `https://`, without
 *   `www.`), so `https://evil.example\@banco.es/` is evil.example (RT-1); the scheme counts too ([urlKey], RT-4), so
 *   `http://banco.es/` or `javascript://banco.es/` is not a duplicate of `https://banco.es/`. When the file's item has
 *   no URL, title and user are enough; when only the vault's entry has none, they are enough for an item with an https
 *   URL (or one without a scheme, read as https), and an item of any other scheme is a new entry (ruling R48, RT-8);
 * - a `TOTP` matches by its key, the rule of `totpImportEntries` ([totpKeyOf]): the key of any vault entry that carries
 *   a 2FA, a login's too (ruling R67); «Reemplazar» on such a login changes nothing ([replaceWith] keeps an entry of
 *   another type);
 * - a `NOTE` matches by its title's key (as a login's) and its text trimmed.
 * Only entries of the same type are compared, so `IDENTITY`, `CONTACT` and `SEED` entries never match. The file is
 * checked against itself first: an item equal to an earlier one is only counted ([MergePlan.repeatedInFile]). Linear
 * in the size of both lists (hash maps, no pairwise comparison).
 */
fun planMerge(read: ImportRead, existing: List<VaultEntry>, revision: Long): MergePlan {
    val inVault = Matcher<String>(codesOfEveryType = true)
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
    val budget = TagBudget(existing.flatMapTo(HashSet()) { it.tags })
    for (item in newItems) budget.tagsFor(item.folders, read.format)
    return MergePlan(revision, read.format, newItems, duplicates, repeated, read.skipped, read.becameNotes, budget.created)
}

/**
 * The plan of [read] against the vault as it is now ([app.seguridad.vault.VaultStore.importSnapshot]: its entries and
 * revision read together, under the store's lock); null when it is locked. Entries and revision read apart could pair
 * an old list with a newer revision and pass the check of [app.seguridad.vault.VaultStore.applyImport]. Used by the
 * tests (VaultStoreImportTest, ImportFlowTest). The import screen does the same in two steps, because it keeps the
 * snapshot's entries for the preview's estimate: `importSnapshot()`, then [planMerge] on `Dispatchers.Default` (this
 * function runs [planMerge] on the caller's dispatcher).
 */
suspend fun VaultStore.planImport(read: ImportRead): MergePlan? = importSnapshot()?.let { planMerge(read, it.entries, it.revision) }

/**
 * Most tags one import may create that the vault did not have (ruling R18): a hostile export can put each of its 20 000
 * items in a folder of its own, and every tag is a filter chip of the vault. A new folder tag past it is not given; the
 * item keeps its origin tag (never dropped) and the folder tags that already exist; [MergeResult.foldersNotTags] says
 * how many were really left out.
 */
const val IMPORT_MAX_NEW_TAGS = 100

/**
 * The tags given to imported items, in the file's order, with at most [max] folder tags created that are not in [had]
 * (the vault's). An item whose new folder tags would pass it keeps its origin tag ([ImportFormat.originTag]) and only
 * the folder tags that are in [had] or already [created]. [created] are the new tags given. An item for «Reemplazar»
 * passes the tags of the vault's entry as `onto`: only the ones [replaceWith] can still add (those it lacks, up to
 * [MAX_TAGS]) are given and counted (ruling R68).
 */
internal class TagBudget(private val had: Set<String>, private val max: Int = IMPORT_MAX_NEW_TAGS) {
    val created = LinkedHashSet<String>()
    private var createdFolders = 0

    fun tagsFor(folders: List<String>, format: ImportFormat, onto: List<String>? = null): List<String> {
        // Ruling R68: for «Reemplazar» onto an entry with tags [onto], only those [replaceWith] can still add count.
        val full = importTags(folders, format).let { all ->
            if (onto == null) all else all.filter { it !in onto }.take(maxOf(0, MAX_TAGS - onto.size))
        }
        val origin = format.originTag
        val freshFolders = full.count { it != origin && it !in had && it !in created }
        val tags = if (createdFolders + freshFolders <= max) full else full.filter { it == origin || it in had || it in created }
        for (t in tags) if (t !in had && created.add(t) && t != origin) createdFolders++
        return tags
    }
}

/**
 * The tags of an entry imported from [format] with [folders] (spec «Etiquetas»): the origin tag first, then each folder
 * read as the duplicate rule compares it ([compareKey]: what draws nothing goes, a control is a space, NFKC, so a
 * decomposed accent, a no-break space or full-width letters are the plain ones, and every run of spaces is one; rulings
 * R60, R61 and R64, RT-17 and RT-18), then through [normalizeTags] (its commas become spaces and a run of spaces one, so
 * a folder stays one tag, «Trabajo, Casa» is «trabajo casa», and `a/b` stays `a/b`); a folder that shows nothing gives
 * no tag. Cut to
 * [app.seguridad.vault.MAX_TAG_LENGTH] code points. At most [MAX_TAGS]: over it, the last folders go, never the origin tag.
 */
fun importTags(folders: List<String>, format: ImportFormat): List<String> {
    val tags = LinkedHashSet<String>()
    tags += format.originTag
    for (folder in folders) {
        if (tags.size >= MAX_TAGS) break
        // Rulings R60 and R64: the folder as the duplicate rule compares it (compareKey: what draws nothing goes, NFKC,
        // every run of spaces one), so it is never an empty chip nor a second «#banco» that only looks the same.
        val shown = compareKey(folder)
        normalizeTags(SPACES.replace(shown.replace(',', ' '), " ")).firstOrNull()?.let { tags += it }
    }
    return tags.toList()
}

/**
 * The vault after the import of [plan] into [existing] (the list the plan was built against): [existing] in its order,
 * with the duplicates chosen for «Reemplazar» changed in place by [replaceWith], then the new entries in the file's
 * order. Each duplicate takes its choice from [overrides] (by [ImportedItem.index]) or [default]. A new entry gets
 * [newId], [now] as both dates, [importTags] (within [IMPORT_MAX_NEW_TAGS], [TagBudget]) and no history or attachments. Nothing else of [existing] changes: an
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
): List<VaultEntry> = mergeImport(plan, default, overrides, existing, now, newId, zone).entries

/**
 * [mergedEntries] and what the preview says of them: [newTags], the tags of [entries] that no entry of the vault had
 * before (so a replaced entry already at [MAX_TAGS] adds none), and [foldersNotTags], how many distinct new folder tags
 * (folder names as [importTags] normalises them) [IMPORT_MAX_NEW_TAGS] kept off an entry that would otherwise have had
 * them.
 */
data class MergeResult(val entries: List<VaultEntry>, val newTags: Set<String>, val foldersNotTags: Int)

/** [mergedEntries], with its [MergeResult]: the one place where an import's entries and tags are made. */
fun mergeImport(
    plan: MergePlan,
    default: DuplicateChoice,
    overrides: Map<Int, DuplicateChoice>,
    existing: List<VaultEntry>,
    now: Long,
    newId: () -> String,
    zone: ZoneId = ZoneId.systemDefault(),
): MergeResult {
    val had = existing.flatMapTo(HashSet()) { it.tags }
    val budget = TagBudget(had)
    // The new folder tags the cap kept off an entry that would otherwise have had them.
    val lost = HashSet<String>()
    val out = existing.toMutableList()
    val position = HashMap<String, Int>(existing.size * 2)
    existing.forEachIndexed { i, e -> position.putIfAbsent(e.id, i) }
    val duplicateOf = plan.duplicates.associateBy { it.item.index }
    val added = ArrayList<VaultEntry>()
    for (item in (plan.newItems + plan.duplicates.map { it.item }).sortedBy { it.index }) {
        val duplicate = duplicateOf[item.index]
        val choice = if (duplicate == null) DuplicateChoice.KEEP_BOTH else overrides[item.index] ?: default
        if (choice == DuplicateChoice.SKIP) continue
        // Only what enters spends the budget of new tags, in the file's order; a REPLACE, only what it can add (R68).
        val onto = if (choice == DuplicateChoice.REPLACE) position[duplicate!!.existingId]?.let { out[it].tags } else null
        val tags = budget.tagsFor(item.folders, plan.format, onto)
        val tagged = item.entry.copy(tags = tags, passwordHistory = emptyList(), attachments = emptyList())
        when (choice) {
            DuplicateChoice.SKIP -> Unit
            DuplicateChoice.KEEP_BOTH -> {
                added += tagged.copy(id = newId(), createdAt = now, updatedAt = now)
                lost += importTags(item.folders, plan.format) - tags.toSet()
            }
            DuplicateChoice.REPLACE -> {
                val at = position[duplicate!!.existingId] ?: continue
                val replaced = replaceWith(out[at], tagged, now, zone)
                val full = importTags(item.folders, plan.format)
                // Only what the cap really took away: an entry already at MAX_TAGS would have taken none of them.
                if (full.size != tags.size) lost += replaceWith(out[at], tagged.copy(tags = full), now, zone).tags - replaced.tags.toSet()
                out[at] = replaced
            }
        }
    }
    val entries = out + added
    val newTags = LinkedHashSet<String>()
    for (e in entries) for (t in e.tags) if (t !in had) newTags += t
    return MergeResult(entries, newTags, lost.count { it !in had })
}

/**
 * [existing] after «Reemplazar» with [incoming] (spec «Las opciones»). Nothing is lost but the vault's 2FA (below), and
 * the copy made before importing keeps that too:
 * - it keeps its id, type, `createdAt`, favourite and attachments;
 * - it keeps its title and user when their keys ([compareKey]; the title without case) are the vault's, since they
 *   are what made it a duplicate (ruling R58, RT-15): a title that differs only in case or by what draws nothing keeps
 *   the vault's spelling. Otherwise (a 2FA, which matches by its key) it takes [incoming]'s, unless it shows nothing;
 * - it takes the URL of [incoming] only where it is not empty, with the duplicate rule's own notion of empty ([isNoUrl]:
 *   nothing left once read as a browser reads it, or nothing that shows; rulings R55 and R64);
 * - 2FA: [incoming]'s, its secret and settings included, whenever it has one (ruling R63, as the spec says: «el 2FA solo
 *   si en el archivo no está vacío»). The vault's secret is then replaced, also in a login that carries a 2FA, where the
 *   secret is not compared; the old one is only in the copy made before importing. Within it, the vault's issuer and
 *   account stay when [incoming]'s show nothing (R57, R59) or compare the same (R64, RT-19: the issuer without case, as
 *   a title; the account with it, as a user);
 * - notes: nothing if [incoming]'s show nothing (R59: no «Importado el …» line for them); [incoming]'s if [existing] has
 *   none; if both have and they differ, [existing]'s, then a line [importedOnHeading] with the day of [now] in [zone],
 *   then [incoming]'s trimmed; not again if [existing]'s already end with them (the same file replaced twice). Notes are
 *   compared trimmed, as the duplicate rule compares them, so notes that differ only by the spaces or line breaks around
 *   them are the same (branch review A, M-2);
 * - tags: its own, then [incoming]'s it lacks, up to [MAX_TAGS] (its own are never dropped);
 * - password: the one exception to R59 (ruling R62): any non-empty one ([String.isEmpty], not «shows nothing») goes
 *   through [withNewPassword], since a password is an opaque secret the file could set to anything visible anyway, and
 *   the old one goes to the history.
 * An `IDENTITY` or `CONTACT` [existing], an [incoming] of another type, or a replacement that would only change
 * `updatedAt`, gives [existing] itself.
 */
fun replaceWith(existing: VaultEntry, incoming: VaultEntry, now: Long, zone: ZoneId = ZoneId.systemDefault()): VaultEntry {
    if (existing.type.isPersonal || incoming.type != existing.type) return existing
    val notes = when {
        // Already there (the same notes, or the same file replaced again): never appended twice. Compared trimmed, as
        // the duplicate rule compares them (Matcher), so a line break or a space more at either end is the same text.
        // Ruling R59: notes that show nothing are not brought in, and add no «Importado el …» line.
        showsNothing(incoming.notes) || incoming.notes.trim() == existing.notes.trim() ||
            existing.notes.trimEnd().endsWith("\n" + incoming.notes.trim()) -> existing.notes
        existing.notes.isBlank() -> incoming.notes
        else -> {
            val day = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            existing.notes + "\n\n" + importedOnHeading(day.dayOfMonth, day.monthValue, day.year) + "\n" + incoming.notes.trim()
        }
    }
    val merged = existing.copy(
        // Ruling R58: the title and user whose keys match the vault's (what made it a duplicate) stay the vault's;
        // R55: what shows nothing never replaces them.
        title = if (showsNothing(incoming.title) || titleKey(incoming.title) == titleKey(existing.title)) existing.title else incoming.title,
        username = if (showsNothing(incoming.username) || userKey(incoming.username) == userKey(existing.username)) existing.username else incoming.username,
        url = if (isNoUrl(incoming.url)) existing.url else incoming.url,
        // The code's settings from the file; its issuer and account only where the file's show something (R57, R59).
        totp = incoming.totp?.let { t ->
            existing.totp?.let { e ->
                t.copy(
                    // Ruling R64 (RT-19), as R58: the vault's issuer and account stay when the file's show nothing or
                    // compare the same (the issuer without case, as a title; the account with it, as a user).
                    issuer = if (showsNothing(t.issuer) || titleKey(t.issuer) == titleKey(e.issuer)) e.issuer else t.issuer,
                    account = if (showsNothing(t.account) || userKey(t.account) == userKey(e.account)) e.account else t.account,
                )
            } ?: t
        } ?: existing.totp,
        notes = notes,
        tags = (existing.tags + incoming.tags.filter { it !in existing.tags }).distinct().take(maxOf(MAX_TAGS, existing.tags.size)),
        updatedAt = now,
    )
    val replaced = if (incoming.password.isEmpty()) merged else merged.withNewPassword(incoming.password, now)
    // Nothing but the date would change: the entry stays as it is (no save, no new copy for nothing).
    return if (replaced.copy(updatedAt = existing.updatedAt) == existing) existing else replaced
}

/**
 * The preview's estimate of the vault file holding [entries]: `ceil(plainJsonBytes * 4 / 3) + 4096`, the entries' JSON
 * in Base64 (as [app.seguridad.vault.VaultCrypto] stores them) plus room for the nonce, the tag and the file's other
 * fields, so it is never below the real file. If the JSON bytes of the entries' texts alone ([escapedTextBytes], counted
 * without building anything) already give an estimate over [BACKUP_MAX_ENTRY_BYTES], that one is returned without
 * encoding them: the answer (too big) is the same, and a hostile import (text made of characters JSON escapes as six
 * bytes each) is never turned into a larger string first. [app.seguridad.vault.VaultStore.applyImport]'s exact check of
 * the encrypted file decides.
 */
fun estimatedVaultFileBytes(entries: List<VaultEntry>): Long {
    val low = base64Estimate(escapedTextBytes(entries))
    if (low > BACKUP_MAX_ENTRY_BYTES) return low
    return base64Estimate(utf8Length(appJson.encodeToString(entries)))
}

/** How much [estimatedVaultFileBytes] passes [BACKUP_MAX_ENTRY_BYTES]; 0 when it fits. */
fun estimatedOverBytes(entries: List<VaultEntry>): Long = (estimatedVaultFileBytes(entries) - BACKUP_MAX_ENTRY_BYTES).coerceAtLeast(0)

private fun base64Estimate(plain: Long): Long = (plain * 4 + 2) / 3 + 4096

/**
 * The bytes the text fields of [entries] take inside their JSON (`appJson`, as the vault encodes them), counted without
 * building anything ([jsonEscapedLength]). A lower bound of the whole JSON (the field names, the quotes, the numbers and
 * the other values are left out), close to it: within a few hundred bytes per entry.
 */
internal fun escapedTextBytes(entries: List<VaultEntry>): Long {
    var n = 0L
    for (e in entries) {
        n += jsonEscapedLength(e.id) + jsonEscapedLength(e.title) + jsonEscapedLength(e.username) +
            jsonEscapedLength(e.password) + jsonEscapedLength(e.url) + jsonEscapedLength(e.notes) +
            jsonEscapedLength(e.seedWords) + jsonEscapedLength(e.identitySeed) + jsonEscapedLength(e.card) +
            jsonEscapedLength(e.verifiedBy)
        for (t in e.tags) n += jsonEscapedLength(t)
        for (h in e.passwordHistory) n += jsonEscapedLength(h.password)
        e.totp?.let { n += jsonEscapedLength(it.secret) + jsonEscapedLength(it.issuer) + jsonEscapedLength(it.account) + jsonEscapedLength(it.algorithm) }
        for (a in e.attachments) n += jsonEscapedLength(a.id) + jsonEscapedLength(a.name) + jsonEscapedLength(a.sha256) + jsonEscapedLength(a.key)
    }
    return n
}

/**
 * The UTF-8 bytes of [s] once written inside a JSON string, without its quotes: `"` and `\`, and `\b`, `\f`, `\n`,
 * `\r` and `\t`, take two; any other control character below U+0020 takes six (`\u00XX`); anything else its UTF-8
 * length ([utf8Length]'s rule). The same as `appJson` writes; nothing is built.
 */
internal fun jsonEscapedLength(s: String): Long {
    var n = 0L
    var i = 0
    while (i < s.length) {
        val c = s[i]
        n += when {
            c == '"' || c == '\\' || c == '\b' || c == '\u000C' || c == '\n' || c == '\r' || c == '\t' -> 2
            c.code < 0x20 -> 6
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

private val SPACES = Regex("\\s+")

/** Title as compared: as it is shown ([compareKey]), case ignored ([Locale.ROOT]). */
private fun titleKey(title: String) = compareKey(title).lowercase(Locale.ROOT)

/** User as compared: as it is shown ([compareKey]); a different capital is visible, so it still counts (ruling R34). */
private fun userKey(username: String) = compareKey(username)

/**
 * The host of [url] as a browser connects to it, read by [parseLink] (RT-1, ruling R34), without `www.`; null for no
 * URL. The text is first read as a browser reads it ([asBrowserReadsIt], ruling R54, RT-14: tabs and line breaks go,
 * C0 controls and spaces at the ends go), so a tab inside the scheme or a control in front cannot hide it. With the scheme it is what [urlKey] compares, and what the duplicate's row shows ([duplicateSubline]). So the
 * authority ends where a browser ends it: at «/», «?», «#» and, in a web link (http, https, ftp, ws, wss), also at «\»,
 * and the user info goes up to the last «@» before that end. `https://evil.example\@banco.es/` is evil.example, as the
 * URL Standard (WHATWG) that browsers follow reads it, not banco.es. The port does not count. A text without a scheme
 * is read as a web link, as an address bar would ([comparedLink]): «banco.es/login», «banco.es:8443», «//banco.es»;
 * «word:digits» only when the word looks like a host (ruling R53).
 * A text that starts with a word and a colon has that word as its scheme (ruling R47), also «ana:clave@banco.es». A URL
 * whose host comes out empty («https://», «@», «mailto:ana@banco.es», «javascript:alert(1)», «ana:clave@banco.es») is
 * the whole text as the browser reads it, with only its scheme in lower case (ruling R51, RT-12:
 * «data:…;base64,SGVsbG8=» keeps the case of what follows).
 *
 * [parseLink] is plain string work: it never throws, never resolves a name and never opens anything (the IDN step only
 * maps a non-ASCII label to its `xn--` form, as the browser does).
 */
internal fun urlHost(url: String): String? {
    val t = asBrowserReadsIt(url)
    if (t.isEmpty()) return null
    val key = hostOf(parseLink(comparedLink(t)))
    return key.ifEmpty { withLowerScheme(t) }
}

/**
 * The URL of a login as the merge compares it (RT-4, ruling R41): its scheme and [urlHost], «https://banco.es»; null for
 * no URL, read through [asBrowserReadsIt] (ruling R54). So `http://banco.es/` and `https://banco.es/` are not duplicates, nor is a link of any other scheme
 * (`javascript://banco.es/…`, `intent://banco.es/…`, `file://banco.es/…`, `ftp://banco.es/`): «Reemplazar» can never
 * change the scheme of a vault's URL without the user seeing it as a new entry. A text without a scheme is read as
 * `https://` ([comparedLink]), as before, so «banco.es» and «https://www.banco.es/login» still match; a word and a colon
 * in front are a scheme (R47), so «ana:clave@banco.es» is «tana:clave@banco.es», not banco.es, and so is
 * `javascript:1/alert(1)` (R53); `ht\ttp:@banco.es` is «hhttp://banco.es» (R54). A URL whose host comes
 * out empty is compared whole, as [urlHost] gives it (only the scheme in lower case, R51); the two kinds of key never
 * meet («h» and «t» in front).
 */
internal fun urlKey(url: String): String? {
    if (isNoUrl(url)) return null
    val t = asBrowserReadsIt(url)
    val link = parseLink(comparedLink(t))
    val host = hostOf(link)
    return if (host.isEmpty()) "t" + withLowerScheme(t) else "h" + link.scheme + "://" + host
}

/**
 * Whether [url] is no URL (ruling R55, re-review I-2): nothing is left once it is read as a browser reads it
 * ([asBrowserReadsIt]: only C0 controls, spaces, tabs and line breaks), or it shows nothing (ruling R64, RT-20: only
 * what draws nothing, such as U+2800 or U+3164, which IDN would turn into a host the row shows as «xn--…»). The one notion of «no URL» of the merge: [urlKey]
 * gives null for it, so it is a duplicate by title and user, and [replaceWith] keeps the vault's URL for it, so
 * «Reemplazar» never empties a web the row did not mention.
 */
internal fun isNoUrl(url: String): Boolean = asBrowserReadsIt(url).isEmpty() || showsNothing(url)

/**
 * Whether [text] (a title or a user) shows nothing (ruling R55): its [compareKey], which the duplicate rule compares,
 * is empty (only controls, spaces or what draws nothing). [replaceWith] keeps the vault's one for it.
 */
private fun showsNothing(text: String): Boolean = compareKey(text).isEmpty()

/**
 * The host of [link] as the merge compares and shows it, without `www.`; empty when there is none or when it shows
 * nothing (ruling R55: a host of only spaces or what draws nothing, which no browser connects to, would give an https
 * key with an empty row; such a URL is compared and shown as its whole text instead).
 */
private fun hostOf(link: ParsedLink): String = link.host?.removePrefix("www.")?.takeUnless { showsNothing(it) }.orEmpty()

/** The key [urlKey] gives an https link, the only one a vault entry without a URL may take (ruling R48). */
private const val HTTPS_KEY = "hhttps://"

/** [t] with its scheme ([schemeOf]) in lower case and the rest as it is (ruling R51). */
private fun withLowerScheme(t: String): String = schemeOf(t)?.let { it + t.substring(it.length) } ?: t

/**
 * [t] (already through [asBrowserReadsIt], not empty) as the link [urlHost] and [urlKey] read: as it is when it starts
 * with a scheme, and with `https://` in front when it does not, as an address bar reads «banco.es/login».
 * «banco.es:8443/x» and «localhost:8080» look like a scheme to [schemeOf] but are a host and a port, so they are read as
 * web links too; only when the word has a «.» or is `localhost`, and only digits follow the «:», up to the end or to
 * «/», «?», «#» or «\» (ruling R53). A bracketed IPv6 literal, «[::1]:8080», has no scheme for [schemeOf] and is read
 * as a web link anyway. Any other word is a scheme, also before digits (`javascript:1/alert(1)`, `data:1/x`). Any other scheme
 * counts as one, as the URL Standard reads it (ruling R47): «mailto:ana@banco.es», «javascript:x@banco.es/» and
 * «ana:clave@banco.es» are not read as `https://…@banco.es`. That is what a browser does, and what keeps
 * `javascript:x@banco.es/` from being a duplicate of banco.es; the two cannot be told apart without a list of schemes.
 */
private fun comparedLink(t: String): String {
    val scheme = schemeOf(t) ?: return "https://$t"
    if (scheme in SPECIAL_SCHEMES) return t
    // Ruling R53: only a word that looks like a host («banco.es», `localhost`) can be a host before a port.
    if ('.' !in scheme && scheme != "localhost") return t
    var i = scheme.length + 1
    while (i < t.length && t[i] in '0'..'9') i++
    val hostAndPort = i > scheme.length + 1 && (i == t.length || t[i] == '/' || t[i] == '?' || t[i] == '#' || t[i] == '\\')
    return if (hostAndPort) "https://$t" else t
}

/**
 * The web of a duplicate as its row shows it (rulings R45 and R51), never with its user info:
 * - with a host: the host alone when the scheme is https (or there is none, read as https), and «esquema://host» for any
 *   other scheme, «http://banco.es», «javascript://banco.es»;
 * - without a host: the text as the browser reads it ([asBrowserReadsIt], ruling R54), with what goes from after the
 *   scheme (and its «//») to the last «@» of the whole text written as «…» (re-review N-1: a password with «/», «?» or
 *   «#» is hidden too): «mailto:…@banco.es», «ana:…@banco.es». It hides more rather than less: «mailto:a@b.es?cc=c@d.es»
 *   is «mailto:…@d.es».
 */
private fun shownUrl(url: String): String {
    val t = asBrowserReadsIt(url)
    if (t.isEmpty()) return ""
    val link = parseLink(comparedLink(t))
    val host = hostOf(link)
    if (host.isNotEmpty()) return if (link.scheme == "https") host else link.scheme + "://" + host
    var start = schemeOf(t)?.let { it.length + 1 } ?: 0
    while (start < t.length && (t[start] == '/' || t[start] == '\\')) start++
    val at = t.lastIndexOf('@')
    return if (at > start) t.substring(0, start) + "…" + t.substring(at) else t
}

/**
 * The line under a duplicate's title in the preview (branch review A, M-4), as plain text ([plainReportText]):
 * - a `LOGIN`: its user and the web «Reemplazar» brings in ([replaceWith] takes the file's URL), as a browser reads it
 *   ([shownUrl]): «ana · banco-acceso.example». So a file that matches «Banco» / «ana» by title and user cannot change
 *   the vault's web without the row saying where it goes. The scheme is shown when it is not https (ruling R45):
 *   «ana · http://banco.es»; a URL without a host is shown as the browser reads it, and the user info never is:
 *   «ana · mailto:…@banco.es»;
 * - a `TOTP`: its issuer and account;
 * - anything else: empty.
 * Only [entry]'s own (the file's) values; the vault's entry is not read.
 */
fun duplicateSubline(entry: VaultEntry): String = plainReportText(
    when (entry.type) {
        EntryType.LOGIN -> listOf(entry.username, shownUrl(entry.url))
        EntryType.TOTP -> listOf(entry.totp?.issuer.orEmpty(), entry.totp?.account.orEmpty())
        else -> emptyList()
    }.filterNot { showsNothing(it) }.joinToString(" · "), // R57: no « · » before or after what shows nothing.
)

/**
 * The duplicate rules over a set of entries, with constant-time lookups. For logins, each title and user keeps the first
 * entry, the first without a URL and the first of each host, so «either side has no URL» needs no scan. The first
 * without a URL is taken only by an item without a URL or with an https one ([HTTPS_KEY], ruling R48). A code's key
 * comes from a 2FA entry and, with [codesOfEveryType] (the vault's matcher), from any entry that carries a 2FA (R67).
 */
private class Matcher<T : Any>(private val codesOfEveryType: Boolean = false) {
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
                val group = logins.getOrPut(titleKey(e.title) to userKey(e.username)) { Logins(value) }
                val host = urlKey(e.url)
                if (host == null) {
                    if (group.hostless == null) group.hostless = value
                } else {
                    group.byHost.putIfAbsent(host, value)
                }
            }
            EntryType.TOTP -> Unit
            EntryType.NOTE -> notes.putIfAbsent(titleKey(e.title) to e.notes.trim(), value)
            else -> Unit // Seeds, identities and contacts are never imported, so never compared.
        }
        // A code: from a 2FA entry, and in the vault from any entry that carries one (a login's too; ruling R67).
        if (e.type == EntryType.TOTP || codesOfEveryType) e.totp?.let { totpKeyOf(it.secret) }?.let { codes.putIfAbsent(it, value) }
    }

    fun find(e: VaultEntry): T? = when (e.type) {
        EntryType.LOGIN -> logins[titleKey(e.title) to userKey(e.username)]?.let { g ->
            val host = urlKey(e.url)
            // An entry without a URL takes only an https one (ruling R48, RT-8): any other scheme is a new entry.
            if (host == null) g.any else g.byHost[host] ?: g.hostless?.takeIf { host.startsWith(HTTPS_KEY) }
        }
        EntryType.TOTP -> e.totp?.let { totpKeyOf(it.secret) }?.let { codes[it] }
        EntryType.NOTE -> notes[titleKey(e.title) to e.notes.trim()]
        else -> null
    }
}
