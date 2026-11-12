package app.seguridad.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.seguridad.android.FilterObscuredTouches
import app.seguridad.crypto.TOTP_UNSUPPORTED_ROW
import app.seguridad.crypto.isSupported
import app.seguridad.crypto.totpCode
import app.seguridad.health.Issue
import app.seguridad.health.loginsWithout2fa
import app.seguridad.health.vaultHealth
import app.seguridad.imports.plainReportText
import app.seguridad.ui.theme.NegativeColor
import app.seguridad.vault.ALL_ENTRIES
import app.seguridad.vault.ALL_TITLE
import app.seguridad.vault.CLEAR_FILTERS
import app.seguridad.vault.DRAG_HANDLE
import app.seguridad.vault.DROP_HERE
import app.seguridad.vault.DROP_MORE
import app.seguridad.vault.EMPTY_VAULT
import app.seguridad.vault.EntryFilter
import app.seguridad.vault.EntryType
import app.seguridad.vault.FAVORITES_TITLE
import app.seguridad.vault.FILTER_EXPIRING
import app.seguridad.vault.FILTER_FAVORITES
import app.seguridad.vault.FILTER_NO_2FA
import app.seguridad.vault.FILTER_WEAK
import app.seguridad.vault.FOLDERS_TITLE
import app.seguridad.vault.FOLDER_FILTER_OFF
import app.seguridad.vault.FOLDER_NONE
import app.seguridad.vault.FOLDER_WITH_SUBFOLDERS
import app.seguridad.vault.Folder
import app.seguridad.vault.MOVE_TO
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
import app.seguridad.vault.seedWordCount
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

    private class Cache(val data: VaultData, val index: SearchIndex, val weak: Set<String>, val no2fa: Set<String>)

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
        viewModelScope.launch {
            combine(store.state, snapshotFlow { query to filter }) { s, qf -> s to qf }.collectLatest { (s, qf) ->
                val data = (s as? VaultState.Unlocked)?.data
                if (data == null) {
                    cache = null
                    _result.value = ListResult.EMPTY
                    return@collectLatest
                }
                val r = withContext(Dispatchers.Default) { compute(data, qf.first, qf.second) }
                // A lock while it ran: nothing of the vault stays here.
                if (store.state.value is VaultState.Unlocked) _result.value = r
            }
        }
    }

    private fun compute(data: VaultData, text: String, f: EntryFilter): ListResult {
        val now = System.currentTimeMillis()
        val listable = data.live.filterNot { it.type.isPersonal }
        val c = cache?.takeIf { it.data === data } ?: run {
            val health = vaultHealth(data.entries, now)
            val weak = health.filter { Issue.WEAK in it.issues }.mapTo(HashSet()) { it.entryId }
            Cache(data, SearchIndex.build(listable, data.folders, previous = cache?.index), weak, loginsWithout2fa(data.entries))
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
        )
    }

    fun reset() {
        query = ""
        filter = initialFilter()
        firstIndex = 0
        firstOffset = 0
        cache = null
        _result.value = ListResult.EMPTY
    }
}

/** The colour a tag is written in: its own ([TagPalette], for the theme on screen), or the secondary text colour. */
@Composable
internal fun tagTextColor(color: TagColor?): Color {
    if (color == null) return MaterialTheme.colorScheme.onSurfaceVariant
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return Color(if (dark) TagPalette.dark(color) else TagPalette.light(color))
}

/** Where an entry being dragged by its handle is, in the window, and which entry. */
private class Dragging(val entry: VaultEntry, val at: Offset)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EntryList(
    store: VaultStore,
    totpOnly: Boolean,
    list: EntryListViewModel,
    onOpen: (String) -> Unit,
    onMessage: (String) -> Unit,
) {
    val result by list.result.collectAsStateWithLifecycle()
    val data = result.data
    val scope = rememberCoroutineScope()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
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

    val row: @Composable (VaultEntry) -> Unit = { e ->
        EntryRow(
            e, now, folders, tagColors, onOpen,
            dragEnabled = showFolders,
            onMoveTo = { dialog = OrganizeDialog.MoveEntry(e.id) },
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

    Box(Modifier.fillMaxSize()) {
        LazyColumn(state = listState, contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            item(key = "search") {
                Column {
                    OutlinedTextField(value = list.query, onValueChange = { list.query = searchText(it) }, label = { Text(SEARCH_LABEL) }, singleLine = true,
                        // No corrections or suggestions. autoCorrectEnabled = false does not stop the keyboard from learning: that
                        // is NoLearningInput (IME_FLAG_NO_PERSONALIZED_LEARNING, for every field of the app).
                        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                        modifier = Modifier.fillMaxWidth())
                    // What the search reads, folded, while there is text (M-3): not a row more over every list.
                    if (!totpOnly && list.query.isNotBlank()) MoreInfo { Text(SEARCH_MORE, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
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
                    Column(Modifier.padding(24.dp)) {
                        Text(if (result.total == 0) EMPTY_VAULT else NO_RESULTS, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (result.total > 0 && !list.unfiltered()) {
                            TextButton(onClick = { list.query = ""; list.filter = list.initialFilter().copy(sort = f.sort) }) { Text(CLEAR_FILTERS) }
                        }
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
    }
    dialog?.let { d -> OrganizeDialogs(store, d, list, result, onDone = { dialog = null }, onNext = { dialog = it }, onMessage = onMessage) }
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
        VAULT_ENTRY_TYPES.forEach { t ->
            val on = f.types == setOf(t)
            FilterChip(selected = on, onClick = { list.filter = f.copy(types = if (on) emptySet() else setOf(t)) }, label = { Text("${t.label} (${result.typeCounts[t] ?: 0})") })
        }
        FilterChip(selected = f.favorites, onClick = { list.filter = f.copy(favorites = !f.favorites) }, label = { Text(FILTER_FAVORITES) })
        FilterChip(selected = f.weak, onClick = { list.filter = f.copy(weak = !f.weak) }, label = { Text(FILTER_WEAK) })
        FilterChip(selected = f.noTotp, onClick = { list.filter = f.copy(noTotp = !f.noTotp) }, label = { Text(FILTER_NO_2FA) })
        FilterChip(selected = f.expiringSoon, onClick = { list.filter = f.copy(expiringSoon = !f.expiringSoon) }, label = { Text(FILTER_EXPIRING) })
    }
}

/**
 * One entry of the list. With [dragEnabled], a handle at its end drags it to a folder ([onDrag] with where it is in the
 * window, null when the drag is cut; [onDrop] when it is let go); TalkBack and anyone who cannot drag have «Mover a…»
 * as an action of the row and in the entry's card.
 */
@Composable
private fun EntryRow(
    e: VaultEntry,
    now: Long,
    folders: List<Folder>,
    tagColors: Map<String, TagColor>,
    onOpen: (String) -> Unit,
    dragEnabled: Boolean,
    onMoveTo: () -> Unit,
    onDrag: (VaultEntry, Offset?) -> Unit,
    onDrop: (VaultEntry) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().clickable { onOpen(e.id) }.padding(vertical = 10.dp)
            .then(if (dragEnabled) Modifier.semantics { customActions = listOf(CustomAccessibilityAction(MOVE_TO) { onMoveTo(); true }) } else Modifier),
    ) {
        Text(typeIcon(e.type), style = MaterialTheme.typography.titleLarge)
        Column(Modifier.weight(1f)) {
            Text(plainReportText(e.title), maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = when (e.type) {
                EntryType.LOGIN -> e.username.ifBlank { e.url }
                EntryType.SEED -> "${seedWordCount(e.seedWords).first} palabras"
                EntryType.NOTE -> "Nota"
                EntryType.TOTP -> listOf(e.totp?.issuer, e.totp?.account).filter { !it.isNullOrBlank() }.joinToString(" · ")
                EntryType.IDENTITY, EntryType.CONTACT -> "" // Never listed here.
                EntryType.CARD, EntryType.WIFI, EntryType.LICENSE, EntryType.PASSPORT -> e.type.label // 2.0, task 5.
            }
            if (sub.isNotBlank()) Text(plainReportText(sub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            val folder = e.folderId?.let { id -> folders.firstOrNull { it.id == id } }
            if (folder != null || e.tags.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    folder?.let { Text("📁 " + plainReportText(it.name, 30), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
                    e.tags.take(3).forEach { t -> Text("#${plainReportText(t)}", style = MaterialTheme.typography.labelSmall, color = tagTextColor(tagColors[t]), maxLines = 1) }
                }
            }
        }
        if (e.favorite) Text("★", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        e.totp?.let { t ->
            // A setting this app does not calculate: why there is no code, as small as the seconds of one (F4).
            // totpCode below gives null for it; any other null (a key that is not usable) still shows nothing.
            if (!t.isSupported()) {
                Text(
                    TOTP_UNSUPPORTED_ROW,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            totpCode(t, now)?.let { (code, left) ->
                Column(horizontalAlignment = Alignment.End) {
                    Text(code.chunked(3).joinToString(" "), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.titleMedium)
                    Text("${left}s", style = MaterialTheme.typography.labelSmall, color = if (left <= 5) NegativeColor else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (dragEnabled) {
            var origin by remember { mutableStateOf(Offset.Zero) }
            var at by remember { mutableStateOf(Offset.Zero) }
            Text(
                "⠿",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp)
                    .onGloballyPositioned { origin = it.positionInRoot() }
                    .semantics { contentDescription = DRAG_HANDLE }
                    .pointerInput(e.id) {
                        detectDragGestures(
                            onDragStart = { p ->
                                at = origin + p
                                onDrag(e, at)
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                at += amount
                                onDrag(e, at)
                            },
                            onDragEnd = { onDrop(e) },
                            onDragCancel = { onDrag(e, null) },
                        )
                    },
            )
        }
    }
    HorizontalDivider()
}
