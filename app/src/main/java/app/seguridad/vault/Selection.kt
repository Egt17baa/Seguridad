package app.seguridad.vault

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/*
 * 2.0, task 6 (design «La lista y la ficha»): the selection of the vault list and what its bar does. Only ids: what an
 * entry holds is never kept here. What is selected is always a part of what the list shows now (focus 4 of the plan):
 * «Seleccionar todo» takes the visible entries only, an entry that leaves the list leaves the selection, and a lock or a
 * change of the search or the filter empties it.
 */

/** The selected entries of the list, held by its ViewModel while the vault is open. */
class Selection {
    private val _ids = MutableStateFlow<Set<String>>(emptySet())

    /** The ids selected now; empty when the list is not selecting. */
    val ids: StateFlow<Set<String>> = _ids.asStateFlow()

    /** False while the vault is not open ([followVault]): nothing can be selected then. */
    @Volatile private var open = true

    /** The key of the search and the filter the selection was made under ([onFilter]). */
    private var filterKey: Any? = null

    /** Selects [id], or unselects it if it was. */
    fun toggle(id: String) {
        if (!open) return
        _ids.value = _ids.value.let { if (id in it) it - id else it + id }
    }

    /**
     * «Seleccionar todo»: exactly the entries of [visible] (what the list shows now) that the list may hold at all, never
     * one in the trash, the identity or a contact, whatever the caller hands over.
     */
    fun selectAll(visible: List<VaultEntry>) {
        if (!open) return
        _ids.value = visible.filter { it.deletedAt == null && !it.type.isPersonal }.mapTo(LinkedHashSet()) { it.id }
    }

    /** After each new result of the list: what is no longer shown (moved to the trash, out of the filter) is unselected. */
    fun retainVisible(visible: List<VaultEntry>) {
        val current = _ids.value
        if (current.isEmpty()) return
        val shown = visible.filter { it.deletedAt == null && !it.type.isPersonal }.mapTo(HashSet()) { it.id }
        val kept = current.filterTo(LinkedHashSet()) { it in shown }
        if (kept.size != current.size) _ids.value = kept
    }

    fun clear() {
        _ids.value = emptySet()
    }

    /** The search and the filter now ([selectionFilterKey]): another one than the last empties the selection. */
    fun onFilter(key: Any?) {
        if (key != filterKey) {
            filterKey = key
            clear()
        }
    }

    /** Empties the selection as soon as the vault is not open, and keeps it empty until it opens again. */
    fun followVault(state: Flow<VaultState>, scope: CoroutineScope): Job = scope.launch {
        state.collect { s ->
            open = s is VaultState.Unlocked
            if (!open) clear()
        }
    }
}

/** What [Selection.onFilter] compares: the search and the filter, but not the order (it shows the same entries). */
fun selectionFilterKey(text: String, filter: EntryFilter): Any = text.trim() to filter.copy(text = "", sort = SortOrder.NAME)

/**
 * This vault with the entries of [ids] that the list holds (not in the trash, not the identity nor a contact) marked as
 * favourites, or not ([favorite]), and how many changed. Saved as the editor saves it: each one keeps how it was in its
 * history ([withVersion]) and its time of change is [now]. Null when none changes.
 */
internal fun VaultData.withFavorite(ids: Set<String>, favorite: Boolean, now: Long): Pair<VaultData, Int>? =
    changing(ids, now) { e -> if (e.favorite == favorite) null else e.copy(favorite = favorite) }

/**
 * This vault with [tag] ([normalizedTag]) added to the entries of [ids] the list holds that do not have it and have room
 * for it ([MAX_TAGS]), as [withFavorite]; how many got it. Null when none, or the tag is empty.
 */
internal fun VaultData.withTagAdded(ids: Set<String>, tag: String, now: Long): Pair<VaultData, Int>? {
    val t = normalizedTag(tag) ?: return null
    return changing(ids, now) { e -> if (t in e.tags || e.tags.size >= MAX_TAGS) null else e.copy(tags = e.tags + t) }
}

private fun VaultData.changing(ids: Set<String>, now: Long, change: (VaultEntry) -> VaultEntry?): Pair<VaultData, Int>? {
    var count = 0
    val next = entries.map { e ->
        if (e.id !in ids || e.deletedAt != null || e.type.isPersonal) return@map e
        val changed = change(e) ?: return@map e
        count++
        changed.copy(updatedAt = now).withVersion(e, now)
    }
    return if (count == 0) null else copy(entries = next) to count
}

/** One tag as the editor keeps it ([normalizeTags]: trimmed, lower case, at most 24 code points); null when empty. */
fun normalizedTag(raw: String): String? = normalizeTags(raw.replace(',', ' ')).firstOrNull()

/** The confirmation of «Papelera» in the selection bar: the entry's title when it is one ([moveToTrashTitle]). */
fun moveSelectedToTrashTitle(count: Int, firstTitle: String): String =
    if (count == 1) moveToTrashTitle(firstTitle) else moveToTrashCountTitle(count)
