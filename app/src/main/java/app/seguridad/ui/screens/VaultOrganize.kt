package app.seguridad.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import app.seguridad.ui.theme.AlertDialog
import app.seguridad.ui.theme.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.seguridad.android.FilterObscuredTouches
import app.seguridad.imports.plainReportText
import app.seguridad.ui.theme.NegativeColor
import app.seguridad.vault.CANCEL
import app.seguridad.vault.CLOSE
import app.seguridad.vault.CONFIRM_DELETE
import app.seguridad.vault.FOLDERS_EMPTY
import app.seguridad.vault.FOLDERS_SHORT
import app.seguridad.vault.FOLDERS_TITLE
import app.seguridad.vault.FOLDER_ALL
import app.seguridad.vault.FOLDER_DELETE
import app.seguridad.vault.FOLDER_DELETE_TO_NONE
import app.seguridad.vault.FOLDER_DELETE_TO_TRASH
import app.seguridad.vault.FOLDER_DELETE_TRASH_NOTE
import app.seguridad.vault.FOLDER_MOVE
import app.seguridad.vault.FOLDER_NAME_LABEL
import app.seguridad.vault.FOLDER_NEW
import app.seguridad.vault.FOLDER_NEW_INSIDE
import app.seguridad.vault.FOLDER_NONE
import app.seguridad.vault.FOLDER_OPTIONS
import app.seguridad.vault.FOLDER_RENAME
import app.seguridad.vault.FOLDER_TOP
import app.seguridad.vault.Folder
import app.seguridad.vault.FolderDeletion
import app.seguridad.vault.SAVE
import app.seguridad.vault.SavedView
import app.seguridad.vault.TAGS_EMPTY
import app.seguridad.vault.TAG_COLORS_SHORT
import app.seguridad.vault.TAG_COLORS_TITLE
import app.seguridad.vault.TAG_COLOR_NONE
import app.seguridad.vault.TagColor
import app.seguridad.vault.VIEWS_TITLE
import app.seguridad.vault.VIEW_DELETE
import app.seguridad.vault.VIEW_DELETE_SHORT
import app.seguridad.vault.VIEW_NAME_LABEL
import app.seguridad.vault.VIEW_SAVE
import app.seguridad.vault.VIEW_SAVE_SHORT
import app.seguridad.vault.VaultLimits
import app.seguridad.vault.VaultStore
import app.seguridad.vault.descendants
import app.seguridad.vault.folderDeleteTitle
import app.seguridad.vault.folderDeleteToParent
import app.seguridad.vault.folderMoveTitle
import app.seguridad.vault.folderPath
import app.seguridad.vault.folderTree
import app.seguridad.vault.moveToTitle
import app.seguridad.vault.movedTo
import app.seguridad.vault.storeSaveErrorText
import app.seguridad.vault.tagColorName
import kotlinx.coroutines.launch
import java.util.UUID

/** A dialog whose folder, entry or vault is gone (a lock, another device's change): it closes, after this frame. */
@Composable
private fun Gone(onDone: () -> Unit) {
    LaunchedEffect(Unit) { onDone() }
}

/** Which dialog of the list's organisation is open. Only ids and names: what an entry holds is never kept here. */
internal sealed interface OrganizeDialog {
    data object Folders : OrganizeDialog
    data class NewFolder(val parentId: String?) : OrganizeDialog
    data class RenameFolder(val id: String) : OrganizeDialog
    data class MoveFolder(val id: String) : OrganizeDialog
    data class DeleteFolder(val id: String) : OrganizeDialog
    data class MoveEntry(val id: String) : OrganizeDialog
    data class SaveView(val id: String?) : OrganizeDialog
    data object Views : OrganizeDialog
    data object TagColors : OrganizeDialog
}

/**
 * The dialogs of the folders, the moves, the views and the tag colours. Each save goes through the store, which
 * checks it again ([VaultStore.createFolder] and the rest); a refusal shows its fixed text ([storeSaveErrorText]) in
 * the dialog, and nothing is saved. When the vault locks, the list leaves the composition and these with it.
 */
@Composable
internal fun OrganizeDialogs(
    store: VaultStore,
    d: OrganizeDialog,
    list: EntryListViewModel,
    result: ListResult,
    onDone: () -> Unit,
    onNext: (OrganizeDialog) -> Unit,
    onMessage: (String) -> Unit,
) {
    val data = result.data ?: return Gone(onDone)
    val folders = data.folders
    fun folder(id: String) = folders.firstOrNull { it.id == id }
    when (d) {
        OrganizeDialog.Folders -> FoldersDialog(folders, result, list, onDone, onNext)
        is OrganizeDialog.NewFolder -> NameDialog(FOLDER_NEW, FOLDER_NAME_LABEL, "", onDone) { name ->
            store.createFolder(name, d.parentId) != null
        }
        is OrganizeDialog.RenameFolder -> {
            val f = folder(d.id) ?: return Gone(onDone)
            NameDialog(FOLDER_RENAME, FOLDER_NAME_LABEL, f.name, onDone) { name -> store.renameFolder(f.id, name) || name.trim() == f.name }
        }
        is OrganizeDialog.MoveFolder -> {
            val f = folder(d.id) ?: return Gone(onDone)
            val exclude = descendants(folders, f.id) + f.id
            FolderPicker(folderMoveTitle(plainReportText(f.name)), folders, exclude, FOLDER_TOP, maxDepth = VaultLimits.FOLDER_DEPTH_MAX, onDone) { target ->
                store.moveFolder(f.id, target)
            }
        }
        is OrganizeDialog.DeleteFolder -> {
            val f = folder(d.id) ?: return Gone(onDone)
            DeleteFolderDialog(store, f, folders, list, onDone)
        }
        is OrganizeDialog.MoveEntry -> {
            val e = data.live.firstOrNull { it.id == d.id } ?: return Gone(onDone)
            FolderPicker(moveToTitle(plainReportText(e.title)), folders, emptySet(), FOLDER_NONE, maxDepth = Int.MAX_VALUE, onDone) { target ->
                val moved = store.moveEntries(setOf(e.id), target) > 0
                if (moved) onMessage(movedTo(target?.let { id -> folders.firstOrNull { it.id == id }?.name }?.let { plainReportText(it) }))
                true
            }
        }
        is OrganizeDialog.SaveView -> {
            val existing = d.id?.let { id -> data.views.firstOrNull { it.id == id } }
            NameDialog(VIEW_SAVE, VIEW_NAME_LABEL, existing?.name.orEmpty(), onDone, note = VIEW_SAVE_SHORT) { name ->
                store.saveView(SavedView(existing?.id ?: UUID.randomUUID().toString(), name, list.current())) || existing != null
            }
        }
        OrganizeDialog.Views -> ViewsDialog(store, data.views, onDone)
        OrganizeDialog.TagColors -> TagColorsDialog(store, result.tags, data.tagColors, onDone)
    }
}

@Composable
private fun FoldersDialog(folders: List<Folder>, result: ListResult, list: EntryListViewModel, onDone: () -> Unit, onNext: (OrganizeDialog) -> Unit) {
    val tree = remember(folders) { folderTree(folders) }
    val counts = remember(result) { result.data?.live.orEmpty().groupingBy { it.folderId }.eachCount() }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(FOLDERS_TITLE) },
        text = {
            FilterObscuredTouches()
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(FOLDERS_SHORT, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = {
                    list.filter = list.filter.copy(folderId = null, includeSubfolders = true)
                    onDone()
                }) { Text(FOLDER_ALL) }
                if (tree.isEmpty()) Text(FOLDERS_EMPTY, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(tree, key = { lazyKey('f', it.first.id) }) { (f, depth) ->
                        var menu by remember { mutableStateOf(false) }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = (depth * 16).dp)) {
                            Text(
                                "📁 ${plainReportText(f.name)} (${counts[f.id] ?: 0})",
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f).clickable(role = Role.Button) {
                                    list.filter = list.filter.copy(folderId = f.id)
                                    onDone()
                                }.padding(vertical = 10.dp),
                            )
                            Box {
                                IconButton(onClick = { menu = true }, modifier = Modifier.semantics { contentDescription = "$FOLDER_OPTIONS «${plainReportText(f.name)}»" }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = null)
                                }
                                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                    FilterObscuredTouches()
                                    if (depth + 1 < VaultLimits.FOLDER_DEPTH_MAX) {
                                        DropdownMenuItem(text = { Text(FOLDER_NEW_INSIDE) }, onClick = { menu = false; onNext(OrganizeDialog.NewFolder(f.id)) })
                                    }
                                    DropdownMenuItem(text = { Text(FOLDER_RENAME) }, onClick = { menu = false; onNext(OrganizeDialog.RenameFolder(f.id)) })
                                    DropdownMenuItem(text = { Text(FOLDER_MOVE) }, onClick = { menu = false; onNext(OrganizeDialog.MoveFolder(f.id)) })
                                    DropdownMenuItem(text = { Text(FOLDER_DELETE, color = NegativeColor) }, onClick = { menu = false; onNext(OrganizeDialog.DeleteFolder(f.id)) })
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onNext(OrganizeDialog.NewFolder(null)) }) { Text(FOLDER_NEW) } },
        dismissButton = { TextButton(onClick = onDone) { Text(CLOSE) } },
    )
}

/** A name to type, saved by [save] (true when done); a refusal of the store is shown here. */
@Composable
private fun NameDialog(title: String, label: String, initial: String, onDone: () -> Unit, note: String? = null, save: suspend (String) -> Boolean) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(title) },
        text = {
            FilterObscuredTouches()
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                OutlinedTextField(
                    value = name, onValueChange = { name = it; error = null }, label = { Text(label) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false), modifier = Modifier.fillMaxWidth(),
                )
                error?.let { Text(it, color = NegativeColor) }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && name.isNotBlank(), onClick = {
                busy = true
                scope.launch {
                    try {
                        if (save(name)) onDone()
                    } catch (e: Exception) {
                        error = storeSaveErrorText(e)
                    } finally {
                        busy = false
                    }
                }
            }) { Text(SAVE) }
        },
        dismissButton = { TextButton(onClick = onDone) { Text(CANCEL) } },
    )
}

/**
 * Picks a folder: [none] (no folder, or the top) and the tree, without the folders of [exclude], and none deeper than
 * [maxDepth] levels (moving a folder checks its own branch in the store). [pick] gets the folder's id, null for [none].
 */
@Composable
private fun FolderPicker(title: String, folders: List<Folder>, exclude: Set<String>, none: String, maxDepth: Int, onDone: () -> Unit, pick: suspend (String?) -> Boolean) {
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    val tree = remember(folders, exclude) { folderTree(folders).filter { (f, depth) -> f.id !in exclude && depth < maxDepth } }
    fun choose(id: String?) {
        scope.launch {
            try {
                if (pick(id)) onDone()
            } catch (e: Exception) {
                error = storeSaveErrorText(e)
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            FilterObscuredTouches()
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                error?.let { Text(it, color = NegativeColor) }
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    item(key = "none") { Text(none, modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { choose(null) }.padding(vertical = 10.dp)) }
                    items(tree, key = { lazyKey('f', it.first.id) }) { (f, depth) ->
                        Text(
                            "📁 ${plainReportText(f.name)}", maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { choose(f.id) }.padding(start = (depth * 16).dp, top = 10.dp, bottom = 10.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDone) { Text(CANCEL) } },
    )
}

/** «Mover a…» of the entry's card: [onPick] gets the folder's id, null for none. */
@Composable
internal fun EntryFolderPicker(title: String, folders: List<Folder>, onDismiss: () -> Unit, onPick: (String?) -> Unit) {
    FolderPicker(moveToTitle(plainReportText(title)), folders, emptySet(), FOLDER_NONE, maxDepth = Int.MAX_VALUE, onDismiss) { target ->
        onPick(target)
        false
    }
}

/** «Borrar carpeta»: where its entries go ([FolderDeletion]), said with the folder's name. */
@Composable
private fun DeleteFolderDialog(store: VaultStore, f: Folder, folders: List<Folder>, list: EntryListViewModel, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var to by remember { mutableStateOf(FolderDeletion.PARENT) }
    var error by remember { mutableStateOf<String?>(null) }
    val parent = folderPath(folders, f.id).dropLast(1).lastOrNull()
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(folderDeleteTitle(plainReportText(f.name))) },
        text = {
            FilterObscuredTouches()
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(FolderDeletion.PARENT, FolderDeletion.TRASH).forEach { option ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable(role = Role.RadioButton) { to = option }) {
                        RadioButton(selected = to == option, onClick = { to = option })
                        Text(
                            when (option) {
                                FolderDeletion.PARENT -> parent?.let { folderDeleteToParent(plainReportText(it.name)) } ?: FOLDER_DELETE_TO_NONE
                                FolderDeletion.TRASH -> FOLDER_DELETE_TO_TRASH
                            },
                        )
                    }
                }
                if (to == FolderDeletion.TRASH) Text(FOLDER_DELETE_TRASH_NOTE, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                error?.let { Text(it, color = NegativeColor) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    try {
                        store.deleteFolder(f.id, to)
                        // The list was showing it: it shows the whole vault again.
                        if (list.filter.folderId == f.id) list.filter = list.filter.copy(folderId = null, includeSubfolders = true)
                        onDone()
                    } catch (e: Exception) {
                        error = storeSaveErrorText(e)
                    }
                }
            }) { Text(FOLDER_DELETE, color = NegativeColor) }
        },
        dismissButton = { TextButton(onClick = onDone) { Text(CANCEL) } },
    )
}

@Composable
private fun ViewsDialog(store: VaultStore, views: List<SavedView>, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var asking by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(VIEWS_TITLE) },
        text = {
            FilterObscuredTouches()
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(VIEW_DELETE_SHORT, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                error?.let { Text(it, color = NegativeColor) }
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(views.distinctBy { it.id }, key = { lazyKey('v', it.id) }) { v ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("👁 ${plainReportText(v.name)}", maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            if (asking == v.id) {
                                TextButton(onClick = {
                                    scope.launch {
                                        try {
                                            store.deleteView(v.id)
                                            asking = null
                                        } catch (e: Exception) {
                                            error = storeSaveErrorText(e)
                                        }
                                    }
                                }) { Text(CONFIRM_DELETE, color = NegativeColor) }
                                TextButton(onClick = { asking = null }) { Text(CANCEL) }
                            } else {
                                TextButton(onClick = { asking = v.id }) { Text(VIEW_DELETE) }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text(CLOSE) } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagColorsDialog(store: VaultStore, tags: List<String>, colors: Map<String, TagColor>, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    fun set(tag: String, color: TagColor?) {
        scope.launch {
            try {
                store.setTagColor(tag, color)
                error = null
            } catch (e: Exception) {
                error = storeSaveErrorText(e)
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(TAG_COLORS_TITLE) },
        text = {
            FilterObscuredTouches()
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(TAG_COLORS_SHORT, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (tags.isEmpty()) Text(TAGS_EMPTY, color = MaterialTheme.colorScheme.onSurfaceVariant)
                error?.let { Text(it, color = NegativeColor) }
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    items(tags, key = { lazyKey('t', it) }) { t ->
                        val current = colors[t]
                        Column {
                            Text(
                                "#${plainReportText(t)} · ${current?.let(::tagColorName) ?: TAG_COLOR_NONE}",
                                color = tagTextColor(current),
                                modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { open = if (open == t) null else t }.padding(vertical = 10.dp),
                            )
                            if (open == t) {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    FilterChip(selected = current == null, onClick = { set(t, null) }, label = { Text(TAG_COLOR_NONE) })
                                    TagColor.entries.forEach { c ->
                                        FilterChip(
                                            selected = current == c,
                                            onClick = { set(t, c) },
                                            label = {
                                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    Text("●", color = tagTextColor(c))
                                                    Text(tagColorName(c))
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text(CLOSE) } },
    )
}
