package app.seguridad.vault

import app.seguridad.imports.compareKey
import java.util.Locale
import java.util.UUID

/*
 * 2.0, task 4: the vault's organisation (design «Carpetas anidadas», «Vistas guardadas», «Etiquetas de color»): a tree
 * of folders, saved views and tag colours, all inside the encrypted [VaultData]. Pure functions over [VaultData]; the
 * store saves what they give ([VaultStore.createFolder] and the rest), and its save checks the limits again
 * ([checkStructure]). A folder whose parent is not in the vault (a copy made elsewhere) counts as a top folder.
 */

/** What happens to the entries of a folder that is deleted ([VaultStore.deleteFolder]). */
enum class FolderDeletion {
    /** They go to the folder above (no folder for a top one); its subfolders go up with them. */
    PARENT,

    /** They go to the trash, with those of every subfolder, which are deleted too. */
    TRASH,
}

/** The longest name of a folder or a view, in code points. */
const val ORGANIZE_NAME_MAX = 60
const val FOLDER_NAME_MAX = ORGANIZE_NAME_MAX

/** A change of the organisation refused before anything is saved; [message] is a fixed Spanish text that says what to do. */
class OrganizeRefused(message: String) : IllegalStateException(message)

const val FOLDER_NAME_EMPTY = "Ponle un nombre a la carpeta."
const val FOLDER_NAME_TAKEN = "Ya hay una carpeta con ese nombre en el mismo sitio. Elige otro."
const val FOLDER_NAME_LONG = "El nombre de una carpeta puede tener como mucho 60 caracteres. Acórtalo."
const val FOLDER_GONE = "Esa carpeta ya no está en la bóveda. Elige otra."
const val VIEW_NAME_EMPTY = "Ponle un nombre a la vista."
const val VIEW_NAME_TAKEN = "Ya hay una vista con ese nombre. Elige otro."
const val VIEW_NAME_LONG = "El nombre de una vista puede tener como mucho 60 caracteres. Acórtalo."
const val TAG_NOT_VALID = "Esa etiqueta no es válida."

/** A new id for a folder or a view: random, like an entry's. */
internal fun newOrganizeId(): String = UUID.randomUUID().toString()

/** How two names of folders (or of views) compare: [compareKey], without case, as the import compares titles. */
internal fun organizeKey(name: String): String = compareKey(name).lowercase(Locale.ROOT)

private fun parents(folders: List<Folder>): Map<String, String?> {
    val parent = HashMap<String, String?>()
    folders.forEach { parent.putIfAbsent(it.id, it.parentId) }
    return parent
}

/**
 * The folders from the top one down to [id], [id] last; empty if it is not in [folders]. Stops at a folder seen twice
 * (a cycle in a hostile copy, which the store never saves) or at a parent that is not there.
 */
fun folderPath(folders: List<Folder>, id: String): List<Folder> {
    val byId = folders.associateBy { it.id }
    val path = ArrayList<Folder>()
    val seen = HashSet<String>()
    var at: String? = id
    while (at != null && seen.add(at)) {
        val f = byId[at] ?: break
        path += f
        at = f.parentId
    }
    return path.asReversed()
}

/** The ids of every folder below [id], at any depth, [id] itself never included (even in a cycle). */
fun descendants(folders: List<Folder>, id: String): Set<String> {
    val children = folders.groupBy { it.parentId }
    val out = LinkedHashSet<String>()
    val queue = ArrayDeque(listOf(id))
    while (queue.isNotEmpty()) {
        for (c in children[queue.removeFirst()].orEmpty()) {
            if (c.id != id && out.add(c.id)) queue += c.id
        }
    }
    return out
}

/** How many levels [id] is down (1 for a top folder), or 0 when it is not there. */
internal fun folderDepth(folders: List<Folder>, id: String): Int = folderPath(folders, id).size

/** How many levels the branch of [id] holds (1 for a folder with no subfolders). */
internal fun branchHeight(folders: List<Folder>, id: String): Int {
    val children = folders.groupBy { it.parentId }
    fun height(at: String, seen: Set<String>): Int =
        1 + (children[at].orEmpty().filter { it.id !in seen }.maxOfOrNull { height(it.id, seen + it.id) } ?: 0)
    return height(id, setOf(id))
}

/**
 * The folders in the order the tree shows them, each with its depth (0 for a top folder): depth first, siblings by
 * [Folder.order] and then by name. A folder whose parent is missing is a top one; one in a cycle is shown once, at the top.
 */
fun folderTree(folders: List<Folder>): List<Pair<Folder, Int>> {
    val ids = folders.mapTo(HashSet()) { it.id }
    val children = folders.groupBy { it.parentId?.takeIf { p -> p in ids } }
    val order = compareBy<Folder>({ it.order }, { organizeKey(it.name) }, { it.id })
    val out = ArrayList<Pair<Folder, Int>>()
    val placed = HashSet<String>()
    fun walk(parent: String?, depth: Int) {
        for (f in children[parent].orEmpty().sortedWith(order)) {
            if (!placed.add(f.id)) continue
            out += f to depth
            walk(f.id, depth + 1)
        }
    }
    walk(null, 0)
    // What a cycle left out (no folder of it has a top parent): each at the top, with what hangs from it.
    for (f in folders.sortedWith(order)) {
        if (f.id in placed) continue
        placed += f.id
        out += f to 0
        walk(f.id, 1)
    }
    return out
}

/** [name] trimmed, or [OrganizeRefused] when it shows nothing or is over [ORGANIZE_NAME_MAX] code points. */
private fun checkedName(name: String, empty: String, long: String): String {
    val t = name.trim()
    if (compareKey(t).isEmpty()) throw OrganizeRefused(empty)
    if (t.codePointCount(0, t.length) > ORGANIZE_NAME_MAX) throw OrganizeRefused(long)
    return t
}

private fun VaultData.checkFreeName(name: String, parentId: String?, except: String?) {
    val key = organizeKey(name)
    val ids = folders.mapTo(HashSet()) { it.id }
    val taken = folders.any { f ->
        f.id != except && (f.parentId?.takeIf { it in ids }) == parentId && organizeKey(f.name) == key
    }
    if (taken) throw OrganizeRefused(FOLDER_NAME_TAKEN)
}

private fun VaultData.checkFolder(id: String?) {
    if (id != null && folders.none { it.id == id }) throw OrganizeRefused(FOLDER_GONE)
}

private fun VaultData.nextOrder(parentId: String?): Int = (folders.filter { it.parentId == parentId }.maxOfOrNull { it.order } ?: -1) + 1

/** This vault with a new folder [name] inside [parentId] (null for the top), last among its siblings, with [id]. */
internal fun VaultData.withNewFolder(name: String, parentId: String?, id: String = newOrganizeId()): VaultData {
    val n = checkedName(name, FOLDER_NAME_EMPTY, FOLDER_NAME_LONG)
    if (folders.size >= VaultLimits.FOLDERS_MAX) throw VaultLimitExceeded(TOO_MANY_FOLDERS)
    checkFolder(parentId)
    if (parentId != null && folderDepth(folders, parentId) + 1 > VaultLimits.FOLDER_DEPTH_MAX) throw VaultLimitExceeded(FOLDERS_TOO_DEEP)
    checkFreeName(n, parentId, except = null)
    return copy(folders = folders + Folder(id, n, parentId, nextOrder(parentId)))
}

/** This vault with folder [id] named [name]; null when it already has that name exactly (nothing to save). */
internal fun VaultData.withFolderRenamed(id: String, name: String): VaultData? {
    val n = checkedName(name, FOLDER_NAME_EMPTY, FOLDER_NAME_LONG)
    val f = folders.firstOrNull { it.id == id } ?: throw OrganizeRefused(FOLDER_GONE)
    if (f.name == n) return null
    checkFreeName(n, f.parentId?.takeIf { p -> folders.any { it.id == p } }, except = id)
    return copy(folders = folders.map { if (it.id == id) it.copy(name = n) else it })
}

/**
 * This vault with folder [id] moved inside [newParentId] (null for the top), last among its new siblings: never
 * inside itself or one of its subfolders ([FOLDER_CYCLE]), never past [VaultLimits.FOLDER_DEPTH_MAX] levels counting
 * its own branch, never next to a folder of the same name. Null when it is already there.
 */
internal fun VaultData.withFolderMoved(id: String, newParentId: String?): VaultData? {
    val f = folders.firstOrNull { it.id == id } ?: throw OrganizeRefused(FOLDER_GONE)
    if (newParentId == id || (newParentId != null && newParentId in descendants(folders, id))) throw VaultLimitExceeded(FOLDER_CYCLE)
    checkFolder(newParentId)
    val current = f.parentId?.takeIf { p -> folders.any { it.id == p } }
    if (current == newParentId) return null
    val above = if (newParentId == null) 0 else folderDepth(folders, newParentId)
    if (above + branchHeight(folders, id) > VaultLimits.FOLDER_DEPTH_MAX) throw VaultLimitExceeded(FOLDERS_TOO_DEEP)
    checkFreeName(f.name, newParentId, except = id)
    val order = nextOrder(newParentId)
    return copy(folders = folders.map { if (it.id == id) it.copy(parentId = newParentId, order = order) else it })
}

/**
 * This vault without folder [id] (null when it is not there), in one change:
 * - [FolderDeletion.PARENT]: its entries and its subfolders go to the folder above it (none for a top folder);
 * - [FolderDeletion.TRASH]: it goes with all its subfolders, and their entries go to the trash at [now], as
 *   [VaultStore.moveToTrash] sends them ([VaultEntry.deletedAt]); an identity or a contact (never in a folder through
 *   the app) only leaves the folder.
 * Entries already in the trash keep their folder id; [VaultStore.restore] brings them back to no folder when it is gone.
 * A view that looked at a deleted folder looks at the nearest folder above that is left (all the vault for none).
 */
internal fun VaultData.withoutFolder(id: String, to: FolderDeletion, now: Long): VaultData? {
    val f = folders.firstOrNull { it.id == id } ?: return null
    val ids = folders.mapTo(HashSet()) { it.id }
    val up = f.parentId?.takeIf { it in ids && it != id }
    val gone = if (to == FolderDeletion.TRASH) descendants(folders, id) + id else setOf(id)
    val left = folders.filter { it.id !in gone }.map { if (it.parentId == id) it.copy(parentId = up, order = it.order) else it }
    val moved = entries.map { e ->
        when {
            e.deletedAt != null || e.folderId == null || e.folderId !in gone -> e
            to == FolderDeletion.PARENT -> e.copy(folderId = up)
            e.type.isPersonal -> e.copy(folderId = null)
            else -> e.copy(deletedAt = now)
        }
    }
    val nearest = { at: String? -> at?.let { start -> folderPath(folders, start).asReversed().firstOrNull { it.id !in gone }?.id } }
    val views = views.map { v ->
        val fid = v.filter.folderId
        if (fid == null || fid !in gone) v else v.copy(filter = v.filter.copy(folderId = nearest(fid)))
    }
    return copy(folders = left, entries = moved, views = views)
}

/**
 * This vault with the entries of [ids] that are not in the trash nor an identity or a contact moved to [folderId]
 * (null for none), with how many moved; null when none did (nothing to save). A move keeps the entry as it is
 * otherwise: no new version of its history and the same [VaultEntry.updatedAt] (the order «por modificación» is about
 * what it holds).
 */
internal fun VaultData.withEntriesMoved(ids: Set<String>, folderId: String?): Pair<VaultData, Int>? {
    checkFolder(folderId)
    var count = 0
    val moved = entries.map { e ->
        if (e.id in ids && e.deletedAt == null && !e.type.isPersonal && e.folderId != folderId) {
            count++
            e.copy(folderId = folderId)
        } else {
            e
        }
    }
    return if (count == 0) null else copy(entries = moved) to count
}

/**
 * This vault with [view] saved: replacing the one of its id, or as a new one at the end. Its name is trimmed and must
 * show something, be at most [ORGANIZE_NAME_MAX] code points and differ (as [organizeKey] reads it) from the other views';
 * its folder must be in the vault. Null when it is saved like that already.
 */
internal fun VaultData.withView(view: SavedView): VaultData? {
    val n = checkedName(view.name, VIEW_NAME_EMPTY, VIEW_NAME_LONG)
    checkFolder(view.filter.folderId)
    val key = organizeKey(n)
    if (views.any { it.id != view.id && organizeKey(it.name) == key }) throw OrganizeRefused(VIEW_NAME_TAKEN)
    val saved = view.copy(name = n)
    val i = views.indexOfFirst { it.id == view.id }
    if (i >= 0) return if (views[i] == saved) null else copy(views = views.toMutableList().also { it[i] = saved })
    if (views.size >= VaultLimits.VIEWS_MAX) throw VaultLimitExceeded(TOO_MANY_VIEWS)
    return copy(views = views + saved)
}

internal fun VaultData.withoutView(id: String): VaultData? = if (views.none { it.id == id }) null else copy(views = views.filterNot { it.id == id })

/**
 * This vault with [tag] (read as [normalizeTags] stores it: lower case) of [color], or without a colour for null; null
 * when it already is. Past [VaultLimits.TAG_COLORS_MAX] tags with a colour, the colours of tags no entry has (in the
 * trash either) go first; if that is not enough, [TOO_MANY_TAG_COLORS].
 */
internal fun VaultData.withTagColor(tag: String, color: TagColor?): VaultData? {
    val t = normalizeTags(tag).singleOrNull() ?: throw OrganizeRefused(TAG_NOT_VALID)
    if (tagColors[t] == color) return null
    if (color == null) return copy(tagColors = tagColors - t)
    var colors = tagColors
    if (t !in colors && colors.size >= VaultLimits.TAG_COLORS_MAX) {
        val used = entries.flatMapTo(HashSet()) { it.tags }
        colors = colors.filterKeys { it in used }
        if (colors.size >= VaultLimits.TAG_COLORS_MAX) throw VaultLimitExceeded(TOO_MANY_TAG_COLORS)
    }
    return copy(tagColors = colors + (t to color))
}

/**
 * This vault with entry [id] (not in the trash) used at [now] ([VaultEntry.lastUsedAt]); null when it is not there, or
 * it was used less than [MARK_USED_EVERY_MS] ago (nothing written for every tap).
 */
internal fun VaultData.withUsed(id: String, now: Long): VaultData? {
    val e = entries.firstOrNull { it.id == id && it.deletedAt == null } ?: return null
    val last = e.lastUsedAt
    if (last != null && now >= last && now - last < MARK_USED_EVERY_MS) return null
    return copy(entries = entries.map { if (it === e) it.copy(lastUsedAt = now) else it })
}

/** [VaultStore.markUsed] writes at most once per entry in this time. */
const val MARK_USED_EVERY_MS = 60_000L
