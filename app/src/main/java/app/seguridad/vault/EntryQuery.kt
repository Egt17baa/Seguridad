package app.seguridad.vault

import app.seguridad.crypto.takeCodePoints
import app.seguridad.health.loginsWithout2fa
import app.seguridad.imports.compareKey
import app.seguridad.imports.searchHost
import java.text.CollationKey
import java.text.Collator
import java.text.Normalizer
import java.util.Locale

const val MAX_TAGS = 10
const val MAX_TAG_LENGTH = 24

/** What the vault list and the contacts ask [query] for (until 2.0 named `EntryFilter`, now the saved views' filter). */
data class ListQuery(val text: String = "", val tag: String? = null, val type: EntryType? = null)

private val accents = Regex("\\p{M}+")

/** Lowercase, without accents: the form in which searches compare. */
private fun fold(s: String): String =
    accents.replace(Normalizer.normalize(s.lowercase(Locale.ROOT), Normalizer.Form.NFD), "")

private fun collator(): Collator = Collator.getInstance(Locale.forLanguageTag("es-ES")).apply { strength = Collator.PRIMARY }

/**
 * Entries that match [f], favorites first and then by title (es-ES, ignoring case and accents).
 * The text only looks at the title, user, URL, 2FA service name and tags: never at passwords, seed words, notes or 2FA secrets.
 */
fun List<VaultEntry>.query(f: ListQuery): List<VaultEntry> {
    val needle = fold(f.text.trim())
    val tag = f.tag?.let { fold(it.trim()) }
    val c = collator()
    return filter { e ->
        (f.type == null || e.type == f.type) &&
            (tag == null || e.tags.any { fold(it) == tag }) &&
            (needle.isEmpty() || listOf(e.title, e.username, e.url, e.totp?.issuer.orEmpty()).any { fold(it).contains(needle) } ||
                e.tags.any { fold(it).contains(needle) })
    }.sortedWith(
        compareByDescending<VaultEntry> { it.favorite }.thenComparator { a, b -> c.compare(a.title, b.title) },
    )
}

/**
 * Comma-separated [raw] to labels: trimmed, lowercase, no empties or duplicates, at most 24 code points (cut at the end of
 * one, so an emoji is never split into a lone surrogate), at most 10.
 */
fun normalizeTags(raw: String): List<String> =
    raw.split(',').map { takeCodePoints(it.trim().lowercase(Locale.ROOT), MAX_TAG_LENGTH).trim() }
        .filter { it.isNotEmpty() }.distinct().take(MAX_TAGS)

// 2.0, task 4: the list's query with [EntryFilter] (design «Vistas guardadas», «Ordenar», «Búsqueda que perdona erratas»).

/** The longest search text, and the longest text of a view, in code points (review M-1 of task 4). */
const val SEARCH_TEXT_MAX = 200

/** [text] cut to [SEARCH_TEXT_MAX] code points: what the search box takes, and what a view's text is read as. */
fun searchText(text: String): String = takeCodePoints(text, SEARCH_TEXT_MAX)

/** How many entries «Recientes» shows. */
const val RECENT_MAX = 5

private fun nameCollator(): Collator = collator()

/**
 * What the search reads of each entry, normalized once ([FuzzySearch.normalize]) and split into words, and the key the
 * order by name compares: built once per change of the vault (the list's ViewModel keeps it, off the main thread) so a
 * key typed only scores. It holds the title, the user, the host of the URL ([searchHost], never the rest of the link),
 * the service and account of a 2FA, the tags, the names of the folder and of those above it, and the names of the
 * fields that are not hidden: never a password, a hidden field's name or value, the notes, the seed words nor a 2FA
 * secret. Lives only while the vault is open (the ViewModel drops it on lock).
 */
class SearchIndex private constructor(
    private val items: Map<String, Item>,
    private val folders: List<Folder>,
    private val memo: Memo,
) {
    internal class Item(val entry: VaultEntry, val texts: List<String>, val words: List<List<IntArray>>, val nameKey: CollationKey)

    /** One text as the search reads it, kept so the next index (after a save) does not normalize it again. */
    internal class Normalized(val text: String, val words: List<IntArray>)

    /**
     * What a build worked out, by the raw text, for the next one: the normalized texts, the hosts of the URLs and the
     * keys of the titles, with the [collator] that made them (keys only compare with keys of the same collator). Holds
     * only what [Item] holds. Built and read by one build at a time ([EntryListViewModel] runs them one after another).
     */
    private class Memo(
        val collator: Collator,
        val texts: HashMap<String, Normalized> = HashMap(),
        val hosts: HashMap<String, String?> = HashMap(),
        val names: HashMap<String, CollationKey> = HashMap(),
    )

    internal fun item(e: VaultEntry): Item? = items[e.id]?.takeIf { it.entry === e }

    /** Whether it was built from [entries] as they are (the same objects) and these [folders]. */
    internal fun covers(entries: List<VaultEntry>, folders: List<Folder>): Boolean =
        (folders === this.folders || folders == this.folders) && entries.all { item(it) != null }

    companion object {
        /**
         * The index of [entries] in [folders]. With [previous] (the index before the last save), what it already worked
         * out for a text is taken from it: after an edit, only what changed is worked out again.
         */
        fun build(entries: List<VaultEntry>, folders: List<Folder>, previous: SearchIndex? = null): SearchIndex {
            val old = previous?.memo
            val memo = Memo(old?.collator ?: nameCollator())
            val byId = folders.associateBy { it.id }
            val pathNames = HashMap<String, List<String>>()
            fun pathOf(id: String) = pathNames.getOrPut(id) { folderPath(folders, id).asReversed().map { it.name } }
            fun norm(raw: String): Normalized = memo.texts.getOrPut(raw) {
                old?.texts?.get(raw) ?: FuzzySearch.normalize(raw).let { Normalized(it, FuzzySearch.words(it)) }
            }
            fun host(url: String): String? =
                if (url.isEmpty()) null
                else memo.hosts.getOrPut(url) { if (old != null && url in old.hosts) old.hosts[url] else searchHost(url) }
            fun nameKey(title: String): CollationKey =
                memo.names.getOrPut(title) { old?.names?.get(title) ?: memo.collator.getCollationKey(plainTitle(title)) }
            val items = HashMap<String, Item>(entries.size * 2)
            for (e in entries) {
                val raw = ArrayList<String>(8)
                raw += e.title
                raw += e.username
                host(e.url)?.let { raw += it }
                e.totp?.let {
                    raw += it.issuer
                    raw += it.account
                }
                raw += e.tags
                e.folderId?.takeIf { it in byId }?.let { raw += pathOf(it) }
                for (f in e.fields) if (f.kind != FieldKind.HIDDEN) raw += f.name
                val texts = raw.map(::norm).filter { it.text.isNotEmpty() }
                items[e.id] = Item(e, texts.map { it.text }, texts.map { it.words }, nameKey(e.title))
            }
            return SearchIndex(items, folders, memo)
        }
    }
}

private fun plainTitle(title: String) = compareKey(title)

/** The entries the list may show: not in the trash, and not the identity or the contacts (their own tab). */
private fun VaultEntry.listable(): Boolean = deletedAt == null && !type.isPersonal

/**
 * The entries of this list (the vault's, [VaultData.live] or all of [VaultData.entries]) that [filter] keeps, in its
 * order ([EntryFilter.sort]). Never one in the trash, the identity nor a contact. With text, the best matches come
 * first ([FuzzySearch]: every exact one before any with a typo), and the order chosen among equals. A folder that is
 * not in [data] keeps nothing (never everything). [weak] says which logins are weak («Salud»); [no2fa] which have no
 * 2FA (by default [app.seguridad.health.loginsWithout2fa], as «Salud» reads it); [index] is the [SearchIndex] of these
 * entries if the caller keeps one (otherwise one is built for this call). [now] is for «Caducan pronto».
 */
fun List<VaultEntry>.query(
    filter: EntryFilter,
    data: VaultData,
    now: Long,
    weak: (VaultEntry) -> Boolean,
    no2fa: ((VaultEntry) -> Boolean)? = null,
    index: SearchIndex? = null,
): List<VaultEntry> {
    val candidates = filter { it.listable() }
    val folderIds: Set<String>? = filter.folderId?.let { id ->
        if (data.folders.none { it.id == id }) emptySet()
        else if (filter.includeSubfolders) descendants(data.folders, id) + id
        else setOf(id)
    }
    val tags = filter.tags.map { it.trim().lowercase(Locale.ROOT) }.toSet()
    val missing2fa = if (!filter.noTotp) null else no2fa ?: loginsWithout2fa(data.entries.ifEmpty { this }).let { ids -> { e: VaultEntry -> e.id in ids } }
    val kept = candidates.filter { e ->
        (filter.types.isEmpty() || e.type in filter.types) &&
            (folderIds == null || e.folderId in folderIds) &&
            (tags.isEmpty() || tags.all { it in e.tags }) &&
            (!filter.favorites || e.favorite) &&
            // 2.0, task 5: the entry's date, its date fields marked «caduca» and the reminder to change the password.
            (!filter.expiringSoon || Expiry.isDue(e, now)) &&
            (missing2fa == null || missing2fa(e)) &&
            (!filter.weak || weak(e))
    }
    val idx = index?.takeIf { it.covers(kept, data.folders) } ?: SearchIndex.build(kept, data.folders)
    val query = FuzzySearch.prepare(filter.text)
    val scored: List<Pair<VaultEntry, Int>> = if (query.isEmpty) {
        kept.map { it to 0 }
    } else {
        kept.mapNotNull { e -> idx.item(e)?.let { item -> query.score(item.texts, item.words)?.let { e to it } } }
    }
    val byName = Comparator<VaultEntry> { a, b -> idx.item(a)!!.nameKey.compareTo(idx.item(b)!!.nameKey) }.thenBy { it.id }
    val order: Comparator<VaultEntry> = when (filter.sort) {
        SortOrder.NAME -> byName
        SortOrder.UPDATED -> compareByDescending<VaultEntry> { it.updatedAt }.then(byName)
        SortOrder.CREATED -> compareByDescending<VaultEntry> { it.createdAt }.then(byName)
        SortOrder.USED -> compareBy<VaultEntry> { it.lastUsedAt == null }.thenByDescending { it.lastUsedAt ?: 0L }.then(byName)
    }
    return scored.sortedWith(compareBy<Pair<VaultEntry, Int>> { it.second }.thenComparator { a, b -> order.compare(a.first, b.first) }).map { it.first }
}

/** «Recientes»: the [RECENT_MAX] entries used last ([VaultEntry.lastUsedAt]), never one in the trash nor a personal one. */
fun recentEntries(entries: List<VaultEntry>): List<VaultEntry> =
    entries.filter { it.listable() && it.lastUsedAt != null }.sortedByDescending { it.lastUsedAt }.take(RECENT_MAX)

/** «Favoritos»: the favourite entries by name, never one in the trash nor a personal one. */
fun favoriteEntries(entries: List<VaultEntry>): List<VaultEntry> {
    val c = nameCollator()
    return entries.filter { it.listable() && it.favorite }.map { it to c.getCollationKey(plainTitle(it.title)) }.sortedBy { it.second }.map { it.first }
}
