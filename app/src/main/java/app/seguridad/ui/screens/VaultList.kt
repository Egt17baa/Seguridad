package app.seguridad.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import app.seguridad.android.copySensitive
import app.seguridad.imports.IMPORT_TITLE
import app.seguridad.tools.TwoStepCopy
import app.seguridad.tools.rowCodeToCopy
import app.seguridad.ui.theme.AlertDialog
import app.seguridad.ui.theme.GlassPanel
import app.seguridad.ui.theme.PanelLevel
import app.seguridad.vault.CANCEL
import app.seguridad.vault.CODE_COPIED
import app.seguridad.vault.EMPTY_CREATE_FIRST
import app.seguridad.vault.EMPTY_IMPORT
import app.seguridad.vault.MOVE_TO_TRASH
import app.seguridad.vault.MOVE_TO_TRASH_TEXT
import app.seguridad.vault.NEXT_CODE_COPIED
import app.seguridad.vault.PASSWORD_COPIED
import app.seguridad.vault.SELECTION_END
import app.seguridad.vault.SELECTION_FAVORITE
import app.seguridad.vault.SELECTION_MOVE
import app.seguridad.vault.SELECTION_TAG
import app.seguridad.vault.SELECTION_TRASH
import app.seguridad.vault.SELECTION_UNFAVORITE
import app.seguridad.vault.SELECT_ALL
import app.seguridad.vault.Selection
import app.seguridad.vault.USERNAME_COPIED
import app.seguridad.vault.UndoTrash
import app.seguridad.vault.duplicatedText
import app.seguridad.vault.favoriteSelected
import app.seguridad.vault.moveSelectedToTrashTitle
import app.seguridad.vault.moveToTrashTitle
import app.seguridad.vault.passwordForHealth
import app.seguridad.vault.selectedCount
import app.seguridad.vault.selectionFilterKey
import kotlinx.coroutines.CancellationException
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import app.seguridad.ui.theme.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.seguridad.android.FilterObscuredTouches
import app.seguridad.health.Issue
import app.seguridad.health.loginsWithout2fa
import app.seguridad.health.vaultHealth
import app.seguridad.imports.plainReportText
import app.seguridad.ui.theme.NegativeColor
import app.seguridad.vault.ALL_ENTRIES
import app.seguridad.vault.ALL_TITLE
import app.seguridad.vault.CLEAR_FILTERS
import app.seguridad.vault.DROP_HERE
import app.seguridad.vault.DROP_MORE
import app.seguridad.vault.EMPTY_VAULT
import app.seguridad.vault.EntryFilter
import app.seguridad.vault.EntryType
import app.seguridad.vault.FAVORITES_TITLE
import app.seguridad.vault.FILTER_EXPIRING
import app.seguridad.vault.EXPIRY_NOTICE_OPEN
import app.seguridad.vault.Expiry
import app.seguridad.vault.expiryNotice
import app.seguridad.vault.FILTER_FAVORITES
import app.seguridad.vault.FILTER_NO_2FA
import app.seguridad.vault.FILTER_WEAK
import app.seguridad.vault.FOLDERS_TITLE
import app.seguridad.vault.FOLDER_FILTER_OFF
import app.seguridad.vault.FOLDER_NONE
import app.seguridad.vault.FOLDER_WITH_SUBFOLDERS
import app.seguridad.vault.Folder
import app.seguridad.vault.NO_RESULTS
import app.seguridad.vault.RECENT_TITLE
import app.seguridad.vault.SEARCH_LABEL
import app.seguridad.vault.SEARCH_MORE
import app.seguridad.vault.SORT_LABEL
import app.seguridad.vault.SavedView
import app.seguridad.vault.SearchIndex
import app.seguridad.vault.SortOrder
import app.seguridad.vault.TAG_COLORS_OPEN
import app.seguridad.vault.TagColor
import app.seguridad.vault.TagPalette
import app.seguridad.ui.theme.isDarkTheme
import app.seguridad.vault.VAULT_ENTRY_TYPES
import app.seguridad.vault.VIEWS_MANAGE
import app.seguridad.vault.VIEW_SAVE
import app.seguridad.vault.VaultData
import app.seguridad.vault.VaultEntry
import app.seguridad.vault.VaultState
import app.seguridad.vault.VaultStore
import app.seguridad.vault.favoriteEntries
import app.seguridad.vault.folderCrumb
import app.seguridad.vault.folderPath
import app.seguridad.vault.folderTree
import app.seguridad.vault.movedTo
import app.seguridad.vault.query
import app.seguridad.vault.searchText
import app.seguridad.vault.recentEntries
import app.seguridad.vault.sortName
import app.seguridad.vault.storeSaveErrorText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * What the list shows for one vault, one search and one filter, worked out off the main thread by [EntryListViewModel].
 * [recent] and [favorites] are only filled while nothing narrows the list (no text, no filter, no folder).
 */
internal class ListResult(
    val data: VaultData?,
    val shown: List<VaultEntry>,
    val recent: List<VaultEntry>,
    val favorites: List<VaultEntry>,
    /** The entries the list may hold at all (not in the trash, not the identity nor the contacts). */
    val total: Int,
    val typeCounts: Map<EntryType, Int>,
    /** Every tag of those entries, sorted. */
    val tags: List<String>,
    /** 2.0, task 5: how many of them expire within 30 days or already have ([Expiry]). */
    val dueSoon: Int = 0,
) {
    companion object {
        val EMPTY = ListResult(null, emptyList(), emptyList(), emptyList(), 0, emptyMap(), emptyList())
    }
}

/**
 * What the vault list shows while the vault stays open: the search, the filter (types, quick filters, folder, the view
 * applied, the order) and the scroll position (1.4.1, F-4). They live here, in the ViewModel of the tab's back-stack
 * entry, so opening, editing or deleting an entry brings the list back as it was. Never in saved state: the search can
 * name an entry. [reset] runs as soon as the vault is not open, from the vault's own state, so a lock while another tab
 * is on screen drops them too, with the result and the search index (2.0, task 4).
 *
 * The search and the filters run off the main thread ([Dispatchers.Default]): each change of the vault, the text or the
 * filter starts a new run and cancels the one before ([collectLatest]). The search index ([SearchIndex]) and what «Salud»
 * says of each login (weak, without 2FA) are worked out once per change of the vault and kept here until it locks.
 */
internal class EntryListViewModel(private val store: VaultStore, private val totpOnly: Boolean) : ViewModel() {
    var query by mutableStateOf("")
    var filter by mutableStateOf(initialFilter())

    /**
     * Where the list was, as two numbers: never the LazyListState itself, which holds the last layout of its list (its
     * rows' text and, after a rotation, the old Activity) while the tab is not shown (the 1.4.1 audit, finding 2).
     */
    var firstIndex = 0
    var firstOffset = 0

    private val _result = MutableStateFlow(ListResult.EMPTY)
    val result: StateFlow<ListResult> = _result.asStateFlow()

    /**
     * 2.0, task 6: the entries selected (ids only), always a part of what the list shows ([Selection]): emptied at every
     * lock (also with another tab on screen) and at every change of the search or the filter.
     */
    val selection = Selection()

    /** The lock counter of the opening whose search box already got the focus (2.0, task 6: once per opening). */
    var focusedOpening = -1

    /** [dueSoon] counted on [day]: review M-7, the next day counts again (as «Caducan pronto», with its own `now`). */
    private class Cache(val data: VaultData, val index: SearchIndex, val weak: Set<String>, val no2fa: Set<String>, val day: java.time.LocalDate, val dueSoon: Int)

    /** Only read and written by the one run of [collectLatest] at a time (each waits for the one before to end). */
    @Volatile private var cache: Cache? = null

    fun initialFilter(): EntryFilter = if (totpOnly) EntryFilter(types = setOf(EntryType.TOTP)) else EntryFilter()

    /** Whether nothing narrows the list (the order may be any). */
    fun unfiltered(): Boolean = query.isBlank() && plain(filter)

    /** Whether [f] narrows nothing: its order may be any, and «Con subcarpetas» means nothing without a folder (review M-5). */
    private fun plain(f: EntryFilter): Boolean =
        f.copy(sort = SortOrder.NAME, includeSubfolders = f.includeSubfolders || f.folderId == null) == initialFilter()

    /** The filter of a view as it would be saved: with the text of the box. */
    fun current(): EntryFilter = filter.copy(text = query)

    /** Whether [view] is the filter on now; its text read as [apply] reads it. */
    fun isOn(view: SavedView): Boolean = view.filter.copy(text = searchText(view.filter.text)) == current()

    /** Review M-1: a view's text is cut to [SEARCH_TEXT_MAX] code points as it is read (one from a copy may hold more). */
    fun apply(view: SavedView) {
        query = searchText(view.filter.text)
        filter = view.filter.copy(text = "")
    }

    init {
        viewModelScope.launch { store.state.collect { if (it !is VaultState.Unlocked) reset() } }
        selection.followVault(store.state, viewModelScope)
        viewModelScope.launch {
            combine(store.state, snapshotFlow { query to filter }) { s, qf -> s to qf }.collectLatest { (s, qf) ->
                selection.onFilter(selectionFilterKey(qf.first, qf.second))
                val data = (s as? VaultState.Unlocked)?.data
                if (data == null) {
                    cache = null
                    _result.value = ListResult.EMPTY
                    return@collectLatest
                }
                val r = withContext(Dispatchers.Default) { compute(data, qf.first, qf.second) }
                // A lock while it ran: nothing of the vault stays here.
                if (store.state.value is VaultState.Unlocked) {
                    _result.value = r
                    // What is no longer shown (moved to the trash, out of the filter after an edit) is no longer selected.
                    selection.retainVisible(r.shown)
                }
            }
        }
    }

    private fun compute(data: VaultData, text: String, f: EntryFilter): ListResult {
        val now = System.currentTimeMillis()
        val listable = data.live.filterNot { it.type.isPersonal }
        val today = Expiry.today(now)
        val c = cache?.takeIf { it.data === data && it.day == today } ?: run {
            val health = vaultHealth(data.entries, now)
            val weak = health.filter { Issue.WEAK in it.issues }.mapTo(HashSet()) { it.entryId }
            Cache(data, SearchIndex.build(listable, data.folders, previous = cache?.index), weak, loginsWithout2fa(data.entries), today, Expiry.dueSoon(listable, now).size)
        }.also { cache = it }
        val filter = f.copy(text = text)
        val shown = listable.query(filter, data, now, weak = { it.id in c.weak }, no2fa = { it.id in c.no2fa }, index = c.index)
        val top = !totpOnly && text.isBlank() && plain(f)
        return ListResult(
            data = data,
            shown = shown,
            recent = if (top) recentEntries(listable) else emptyList(),
            favorites = if (top) favoriteEntries(listable) else emptyList(),
            total = listable.size,
            typeCounts = listable.groupingBy { it.type }.eachCount(),
            tags = listable.flatMapTo(HashSet()) { it.tags }.sorted(),
            dueSoon = c.dueSoon,
        )
    }

    fun reset() {
        query = ""
        filter = initialFilter()
        firstIndex = 0
        firstOffset = 0
        cache = null
        _result.value = ListResult.EMPTY
        selection.clear()
    }
}

/** The colour a tag is written in: its own ([TagPalette], for the theme on screen), or the secondary text colour. */
@Composable
internal fun tagTextColor(color: TagColor?): Color {
    if (color == null) return MaterialTheme.colorScheme.onSurfaceVariant
    return Color(if (isDarkTheme) TagPalette.dark(color) else TagPalette.light(color))
}

/** Where an entry being dragged by its handle is, in the window, and which entry. */
private class Dragging(val entry: VaultEntry, val at: Offset)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EntryList(
    store: VaultStore,
    undo: UndoTrash,
    twoStep: TwoStepCopy,
    totpOnly: Boolean,
    list: EntryListViewModel,
    onOpen: (String) -> Unit,
    onEdit: (String) -> Unit,
    onNew: (EntryType) -> Unit,
    onImport: () -> Unit,
    onImportOther: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val result by list.result.collectAsStateWithLifecycle()
    val data = result.data
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    // Read only by the 2FA of the rows (EntryRow), so the other rows are not drawn again every second.
    val now = remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now.longValue = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val folders = data?.folders.orEmpty()
    val tagColors = data?.tagColors.orEmpty()
    // A tag filter whose tag is gone is dropped, not only hidden: it would come back with a new tag of that name. A
    // folder that is gone is dropped the same way.
    LaunchedEffect(result) {
        if (data == null) return@LaunchedEffect
        val f = list.filter
        val tags = f.tags.filter { it in result.tags }.toSet()
        val folder = f.folderId?.takeIf { id -> folders.any { it.id == id } }
        // Review M-5: with no folder, «Con subcarpetas» goes back to on, as «Quitar carpeta» leaves it.
        if (tags != f.tags || folder != f.folderId) list.filter = f.copy(tags = tags, folderId = folder, includeSubfolders = f.includeSubfolders || folder == null)
    }
    // The list's place comes from the ViewModel and goes back to it when the list leaves the screen.
    val listState = remember { LazyListState(list.firstIndex, list.firstOffset) }
    DisposableEffect(listState) {
        onDispose {
            // Review M-4: after a lock the list leaves with its old place; [EntryListViewModel.reset] already dropped it.
            if (store.state.value is VaultState.Unlocked) {
                list.firstIndex = listState.firstVisibleItemIndex
                list.firstOffset = listState.firstVisibleItemScrollOffset
            }
        }
    }
    var dialog by remember { mutableStateOf<OrganizeDialog?>(null) }
    var dragging by remember { mutableStateOf<Dragging?>(null) }
    val targets = remember { mutableStateMapOf<String, Rect>() }
    var hovered by remember { mutableStateOf<String?>(null) }
    // 2.0, task 6: the move to the trash asked by a swipe or the menu (an id), or by the selection bar (ids).
    var trashAsk by remember { mutableStateOf<String?>(null) }
    var trashSelected by remember { mutableStateOf<Set<String>?>(null) }
    val selected by list.selection.ids.collectAsStateWithLifecycle()
    val selecting = selected.isNotEmpty()
    // Back ends the selection first.
    BackHandler(enabled = selecting) { list.selection.clear() }
    val f = list.filter
    val showFolders = !totpOnly && folders.isNotEmpty()

    fun move(entry: VaultEntry, folderId: String?) {
        scope.launch {
            try {
                if (store.moveEntries(setOf(entry.id), folderId) > 0) onMessage(movedTo(folders.firstOrNull { it.id == folderId }?.name?.let { plainReportText(it) }))
            } catch (e: Exception) {
                onMessage(storeSaveErrorText(e))
            }
        }
    }

    // Copying counts as a use («Recientes», the order «por uso»): a bookkeeping save that never blocks nor closes anything.
    fun used(id: String) {
        scope.launch { runCatching { store.markUsed(id) } }
    }

    // Each copy reads what it copies from the open vault at the tap, and goes with the clipboard's rules (sensitive, cleared
    // after the time of Ajustes, the A14 notice on Android 8 to 12): what follows a copy waits until it is really made.
    val actions = remember(store, twoStep, list) {
        RowActions(
            open = { e -> onOpen(e.id) },
            edit = { e -> onEdit(e.id) },
            askTrash = { e -> trashAsk = e.id },
            toggleSelect = { e -> list.selection.toggle(e.id) },
            copyUsername = { e ->
                liveEntry(store, e.id)?.let { live ->
                    copySensitive(context, live.username) {
                        used(live.id)
                        onMessage(USERNAME_COPIED)
                        if (live.passwordForHealth().isNotEmpty()) twoStep.offerPassword(live.id)
                    }
                }
            },
            copyPassword = { e ->
                liveEntry(store, e.id)?.takeIf { it.passwordForHealth().isNotEmpty() }?.let { live ->
                    copySensitive(context, live.passwordForHealth()) {
                        twoStep.clear()
                        used(live.id)
                        onMessage(PASSWORD_COPIED)
                    }
                }
            },
            copyCode = { e ->
                val live = liveEntry(store, e.id)
                live?.totp?.let { rowCodeToCopy(it, System.currentTimeMillis()) }?.let { c ->
                    copySensitive(context, c.code) {
                        twoStep.clear()
                        used(live.id)
                        onMessage(if (c.next) NEXT_CODE_COPIED else CODE_COPIED)
                    }
                }
            },
            duplicate = { e ->
                scope.launch {
                    try {
                        store.duplicate(e.id)?.let { onMessage(duplicatedText(plainReportText(it.title))) }
                    } catch (x: CancellationException) {
                        throw x
                    } catch (x: Exception) {
                        onMessage(storeSaveErrorText(x))
                    }
                }
            },
            moveTo = { e -> dialog = OrganizeDialog.MoveEntry(e.id) },
            onDrag = { entry, at ->
                // A new drag measures the folders again: none is left from the last one (a folder since deleted).
                if (dragging == null) targets.clear()
                dragging = at?.let { Dragging(entry, it) }
                hovered = at?.let { p -> targets.entries.firstOrNull { it.value.contains(p) }?.key }
            },
            onDrop = { entry ->
                val target = hovered
                dragging = null
                hovered = null
                if (target != null) move(entry, target.takeUnless { it == NO_FOLDER_TARGET })
            },
        )
    }

    val row: @Composable (VaultEntry) -> Unit = { e ->
        EntryRow(
            e, now, folders, tagColors,
            selecting = selecting,
            selected = e.id in selected,
            dragEnabled = showFolders,
            canMove = !totpOnly && (folders.isNotEmpty() || e.folderId != null),
            actions = actions,
        )
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(state = listState, contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = if (selecting) 176.dp else 96.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item(key = "search") {
                Column {
                    val focus = remember { FocusRequester() }
                    OutlinedTextField(value = list.query, onValueChange = { list.query = searchText(it) }, label = { Text(SEARCH_LABEL) }, singleLine = true,
                        // No corrections or suggestions. autoCorrectEnabled = false does not stop the keyboard from learning: that
                        // is NoLearningInput (IME_FLAG_NO_PERSONALIZED_LEARNING, for every field of the app). 2.0, task 6: the
                        // focus comes here when the vault opens, without the keyboard; a tap opens it.
                        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, showKeyboardOnFocus = false),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus))
                    // Once per opening of the vault (its lock counter), from inside this item: it is composed by then.
                    LaunchedEffect(Unit) {
                        val opening = store.lockEpoch
                        if (list.focusedOpening != opening) {
                            list.focusedOpening = opening
                            runCatching { focus.requestFocus() }
                        }
                    }
                    // What the search reads, folded, while there is text (M-3): not a row more over every list.
                    if (!totpOnly && list.query.isNotBlank()) MoreInfo { Text(SEARCH_MORE, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            // 2.0, task 5 (design «Caducidad», O-4): the notice over the list, only inside the app; it opens «Caducan pronto».
            if (!totpOnly && result.dueSoon > 0 && !f.expiringSoon) {
                item(key = "expiry") {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        // Review M-8 (R-1): the clock is decoration; TalkBack reads the notice only.
                        Text("⏰ ", Modifier.clearAndSetSemantics { }, style = MaterialTheme.typography.bodyMedium)
                        Text(expiryNotice(result.dueSoon), color = NegativeColor, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = { list.filter = f.copy(expiringSoon = true) }) { Text(EXPIRY_NOTICE_OPEN) }
                    }
                }
            }
            if (!totpOnly) {
                item(key = "tools") { ListTools(list, onFolders = { dialog = OrganizeDialog.Folders }, onSaveView = { dialog = OrganizeDialog.SaveView(null) }) }
                f.folderId?.let { id ->
                    item(key = "crumb") {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                folderCrumb(folderPath(folders, id).map { plainReportText(it.name) }),
                                style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                            )
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { list.filter = f.copy(includeSubfolders = !f.includeSubfolders) }) {
                                Checkbox(checked = f.includeSubfolders, onCheckedChange = { list.filter = f.copy(includeSubfolders = it) })
                                Text(FOLDER_WITH_SUBFOLDERS, style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(onClick = { list.filter = f.copy(folderId = null, includeSubfolders = true) }) { Text(FOLDER_FILTER_OFF) }
                        }
                    }
                }
                item(key = "chips") { QuickFilters(list, result) }
                // One chip per id, whatever a copy made elsewhere holds (checkStructure refuses repeated ids too).
                val views = data?.views.orEmpty().distinctBy { it.id }
                if (views.isNotEmpty()) {
                    item(key = "views") {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(views, key = { lazyKey('v', it.id) }) { v ->
                                val on = list.isOn(v)
                                FilterChip(selected = on, onClick = { if (on) { list.query = ""; list.filter = list.initialFilter().copy(sort = f.sort) } else list.apply(v) }, label = { Text("👁 ${plainReportText(v.name)}") })
                            }
                            item(key = "manage") { FilterChip(selected = false, onClick = { dialog = OrganizeDialog.Views }, label = { Text(VIEWS_MANAGE) }) }
                        }
                    }
                }
            }
            if (result.tags.isNotEmpty()) {
                item(key = "tags") {
                    // R18: lazy, so a vault with many tags composes only the chips on screen.
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(result.tags, key = { lazyKey('t', it) }) { t ->
                            val on = t in f.tags
                            FilterChip(
                                selected = on,
                                onClick = { list.filter = f.copy(tags = if (on) f.tags - t else f.tags + t) },
                                label = { Text("#${plainReportText(t)}", color = tagTextColor(tagColors[t])) },
                            )
                        }
                        if (!totpOnly) item(key = "colors") { FilterChip(selected = false, onClick = { dialog = OrganizeDialog.TagColors }, label = { Text(TAG_COLORS_OPEN) }) }
                    }
                }
            }
            fun LazyListScope.section(title: String, entries: List<VaultEntry>, prefix: Char) {
                if (entries.isEmpty()) return
                item(key = "h-$prefix") { Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp)) }
                items(entries, key = { lazyKey(prefix, it.id) }) { e -> row(e) }
            }
            val sections = result.recent.isNotEmpty() || result.favorites.isNotEmpty()
            section(RECENT_TITLE, result.recent, 'r')
            section(FAVORITES_TITLE, result.favorites, 'f')
            if (sections && result.shown.isNotEmpty()) {
                item(key = "h-a") { Text(ALL_TITLE, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp)) }
            }
            if (result.shown.isEmpty() && data != null) {
                item(key = "empty") {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (result.total == 0) EMPTY_VAULT else NO_RESULTS, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (result.total > 0 && !list.unfiltered()) {
                            TextButton(onClick = { list.query = ""; list.filter = list.initialFilter().copy(sort = f.sort) }) { Text(CLEAR_FILTERS) }
                        }
                        // 2.0, task 6 (design «Lista vacía útil»): the first entry, or an import.
                        if (result.total == 0) EmptyVaultActions(totpOnly, onNew, onImport, onImportOther)
                    }
                }
            }
            items(result.shown, key = { lazyKey('a', it.id) }) { e -> row(e) }
        }
        // While an entry is dragged by its handle: the folders to drop it on, over the top of the list.
        dragging?.let { d ->
            Surface(tonalElevation = 6.dp, shadowElevation = 6.dp, modifier = Modifier.fillMaxWidth().padding(8.dp).align(Alignment.TopCenter)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(DROP_HERE, style = MaterialTheme.typography.titleSmall)
                    val tree = folderTree(folders)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        DropTarget(NO_FOLDER_TARGET, FOLDER_NONE, hovered == NO_FOLDER_TARGET, targets)
                        tree.take(DROP_TARGETS_MAX).forEach { (folder, _) ->
                            DropTarget(folder.id, "📁 " + plainReportText(folder.name, 30), hovered == folder.id, targets)
                        }
                    }
                    if (tree.size > DROP_TARGETS_MAX) Text(DROP_MORE, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            var origin by remember { mutableStateOf(Offset.Zero) }
            Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot() }) {
                Card(Modifier.offset { IntOffset((d.at.x - origin.x).toInt() + 16, (d.at.y - origin.y).toInt() - 24) }) {
                    Text(plainReportText(d.entry.title, 40), modifier = Modifier.padding(8.dp), maxLines = 1)
                }
            }
        }
        if (selecting) {
            SelectionBar(
                count = selected.size,
                allFavorite = result.shown.filter { it.id in selected }.let { s -> s.isNotEmpty() && s.all { it.favorite } },
                canMove = !totpOnly && folders.isNotEmpty(),
                onSelectAll = { list.selection.selectAll(result.shown) },
                onMove = { dialog = OrganizeDialog.MoveSelected(selected) },
                onTag = { dialog = OrganizeDialog.TagSelected(selected) },
                onFavorite = { favorite ->
                    val ids = selected
                    scope.launch {
                        try {
                            onMessage(favoriteSelected(store.setFavorite(ids, favorite), favorite))
                            list.selection.clear()
                        } catch (x: CancellationException) {
                            throw x
                        } catch (x: Exception) {
                            onMessage(storeSaveErrorText(x))
                        }
                    }
                },
                onTrash = { trashSelected = selected },
                onEnd = { list.selection.clear() },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
    dialog?.let { d -> OrganizeDialogs(store, d, list, result, onDone = { dialog = null }, onNext = { dialog = it }, onMessage = onMessage) }
    trashAsk?.let { id ->
        val e = result.data?.live?.firstOrNull { it.id == id }
        if (e == null) {
            LaunchedEffect(id) { trashAsk = null }
        } else {
            TrashConfirm(moveToTrashTitle(plainReportText(e.title)), onDismiss = { trashAsk = null }) {
                trashAsk = null
                scope.launch {
                    try {
                        undo.moveToTrash(setOf(id)) // «Deshacer» for 5 s on the list (VaultScreen).
                    } catch (x: CancellationException) {
                        throw x
                    } catch (x: Exception) {
                        onMessage(storeSaveErrorText(x))
                    }
                }
            }
        }
    }
    trashSelected?.let { ids ->
        val shown = result.shown.filter { it.id in ids }
        if (shown.isEmpty()) {
            LaunchedEffect(ids) { trashSelected = null }
        } else {
            TrashConfirm(moveSelectedToTrashTitle(shown.size, plainReportText(shown.first().title)), onDismiss = { trashSelected = null }) {
                trashSelected = null
                val move = shown.mapTo(HashSet()) { it.id }
                scope.launch {
                    try {
                        undo.moveToTrash(move)
                        list.selection.clear()
                    } catch (x: CancellationException) {
                        throw x
                    } catch (x: Exception) {
                        onMessage(storeSaveErrorText(x))
                    }
                }
            }
        }
    }
}

/** «¿Mover … a la papelera?», with what it means (it can be restored for 30 days). */
@Composable
private fun TrashConfirm(title: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            FilterObscuredTouches()
            Text(MOVE_TO_TRASH_TEXT)
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(MOVE_TO_TRASH, color = NegativeColor) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(CANCEL) } },
    )
}

/** The empty vault's way on: «Crear la primera» and «Importar» (a copy of Clavero, or another app's export). */
@Composable
private fun EmptyVaultActions(totpOnly: Boolean, onNew: (EntryType) -> Unit, onImport: () -> Unit, onImportOther: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { onNew(if (totpOnly) EntryType.TOTP else EntryType.LOGIN) }) { Text(EMPTY_CREATE_FIRST) }
        Box {
            OutlinedButton(onClick = { open = true }) { Text(EMPTY_IMPORT) }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                FilterObscuredTouches() // The menu is its own window: no taps through an overlay on top of it.
                DropdownMenuItem(text = { Text("Importar copia") }, onClick = {
                    open = false
                    onImport()
                })
                DropdownMenuItem(text = { Text(IMPORT_TITLE) }, onClick = {
                    open = false
                    onImportOther()
                })
            }
        }
    }
}

/**
 * 2.0, task 6: the bar of the selection, at the bottom of the list (a glass panel, blurred from Android 12): how many,
 * «Seleccionar todo» (what the list shows), «Mover a carpeta», «Etiquetar», «Favorita» and «Papelera» (asked first).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelectionBar(
    count: Int,
    allFavorite: Boolean,
    canMove: Boolean,
    onSelectAll: () -> Unit,
    onMove: () -> Unit,
    onTag: () -> Unit,
    onFavorite: (Boolean) -> Unit,
    onTrash: () -> Unit,
    onEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassPanel(modifier.fillMaxWidth().padding(8.dp), level = PanelLevel.OVERLAY) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(selectedCount(count), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite })
                IconButton(onClick = onEnd) { Icon(Icons.Default.Close, contentDescription = SELECTION_END) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onSelectAll) { Text(SELECT_ALL) }
                if (canMove) TextButton(onClick = onMove) { Text(SELECTION_MOVE) }
                TextButton(onClick = onTag) { Text(SELECTION_TAG) }
                TextButton(onClick = { onFavorite(!allFavorite) }) { Text(if (allFavorite) SELECTION_UNFAVORITE else SELECTION_FAVORITE) }
                TextButton(onClick = onTrash) { Text(SELECTION_TRASH, color = NegativeColor) }
            }
        }
    }
}

/**
 * The key of a row of a lazy list made from the vault's data: its [kind] and its id, with a «:» that no fixed key has
 * («search», «colors», «manage»…). So a tag called «colors» or a view whose id is «manage» never repeats a key in the
 * same list, which would close the app at every opening of the list (review C-1 of task 4).
 */
internal fun lazyKey(kind: Char, id: String): String = "$kind:$id"

private const val NO_FOLDER_TARGET = "\u0000sin-carpeta"

/** How many folders the drop bar offers; the rest through «Mover a…». */
private const val DROP_TARGETS_MAX = 12

@Composable
private fun DropTarget(id: String, label: String, hovered: Boolean, targets: MutableMap<String, Rect>) {
    FilterChip(
        selected = hovered,
        onClick = {},
        label = { Text(label, maxLines = 1) },
        modifier = Modifier.onGloballyPositioned { targets[id] = it.boundsInRoot() },
    )
}

/** The order, the folders and «Guardar vista». */
@Composable
private fun ListTools(list: EntryListViewModel, onFolders: () -> Unit, onSaveView: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.horizontalScroll(rememberScrollState())) {
        Box {
            TextButton(onClick = { open = true }) { Text("$SORT_LABEL: ${sortName(list.filter.sort).lowercase()} ▾") }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                FilterObscuredTouches() // The menu is its own window: no taps through an overlay on top of it.
                SortOrder.entries.forEach { s ->
                    DropdownMenuItem(text = { Text((if (s == list.filter.sort) "✓ " else "") + sortName(s)) }, onClick = {
                        open = false
                        list.filter = list.filter.copy(sort = s)
                    })
                }
            }
        }
        TextButton(onClick = onFolders) { Text("📁 $FOLDERS_TITLE") }
        if (!list.unfiltered()) TextButton(onClick = onSaveView) { Text(VIEW_SAVE) }
    }
}

/** The one-tap filters: all, each type (with how many), favourites, weak, without 2FA and expiring soon. */
@Composable
private fun QuickFilters(list: EntryListViewModel, result: ListResult) {
    val f = list.filter
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
        FilterChip(selected = f.types.isEmpty(), onClick = { list.filter = f.copy(types = emptySet()) }, label = { Text("$ALL_ENTRIES (${result.total})") })
        // 2.0, task 5: a template's chip only once the vault has one of it (or it is the filter on), so the row stays short.
        VAULT_ENTRY_TYPES.filter { !it.v4Only || (result.typeCounts[it] ?: 0) > 0 || f.types == setOf(it) }.forEach { t ->
            val on = f.types == setOf(t)
            FilterChip(selected = on, onClick = { list.filter = f.copy(types = if (on) emptySet() else setOf(t)) }, label = { Text("${t.label} (${result.typeCounts[t] ?: 0})") })
        }
        FilterChip(selected = f.favorites, onClick = { list.filter = f.copy(favorites = !f.favorites) }, label = { Text(FILTER_FAVORITES) })
        FilterChip(selected = f.weak, onClick = { list.filter = f.copy(weak = !f.weak) }, label = { Text(FILTER_WEAK) })
        FilterChip(selected = f.noTotp, onClick = { list.filter = f.copy(noTotp = !f.noTotp) }, label = { Text(FILTER_NO_2FA) })
        FilterChip(selected = f.expiringSoon, onClick = { list.filter = f.copy(expiringSoon = !f.expiringSoon) }, label = { Text(FILTER_EXPIRING) })
    }
}
