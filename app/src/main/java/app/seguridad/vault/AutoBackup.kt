package app.seguridad.vault

import app.seguridad.crypto.CryptoFailure
import app.seguridad.settings.AUTO_BACKUP_KEEP_CHOICES
import app.seguridad.settings.AUTO_BACKUP_KEEP_DEFAULT
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.IOException
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * A folder the automatic copies go to (on Android, a folder chosen with the system picker). Names are plain file
 * names inside that folder; every call may block, and throws on any failure (a revoked permission is a
 * SecurityException, a folder that is gone a FileNotFoundException).
 */
interface BackupTarget {
    /**
     * Creates an empty file named [name] and returns that very file: it is written, renamed or deleted through the
     * result, never looked up again by its name (a provider may give it another name, or allow two with one name).
     */
    suspend fun create(name: String): BackupFile

    /** The files in the folder whose names start with [prefix]. */
    fun list(prefix: String): List<BackupEntry>

    /** Deletes the file whose [BackupEntry.id] is [id]; nothing if there is none. */
    fun delete(id: String)
}

/** A file in a [BackupTarget]: [id] is the provider's handle for it (on Android, its document id), [name] what it is called. */
data class BackupEntry(val id: String, val name: String)

/** A file [BackupTarget.create] made. */
interface BackupFile {
    /** Its [BackupEntry.id] as it is now (a rename may change it). */
    val id: String

    /** False when the provider says it cannot rename it: the copy is then written straight under its real name. */
    val canRename: Boolean get() = true

    /** Lets [writer] fill the file; [writer]'s stream is closed afterwards. */
    suspend fun write(writer: suspend (OutputStream) -> Unit)

    /** Renames it to [to], a name not in the folder; throws if the provider cannot. */
    fun rename(to: String)

    fun delete()
}

/** How long after the last saved change the copy is made. */
const val AUTO_BACKUP_DEBOUNCE_MS = 3_000L

const val AUTO_BACKUP_PREFIX = "seguridad-auto-"
private const val SUFFIX = ".sgbak"
private const val TEMP_SUFFIX = ".tmp"

/** The temporary names this class writes: the only files it deletes by name (cut copies, never complete ones). */
private val TEMP_NAME = Regex("seguridad-auto-\\d{8}-\\d{6}\\.sgbak\\.tmp")
private val STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

// Shown in Ajustes. Fixed texts: never a path, a URI, an attachment name or what an exception said.
const val AUTO_BACKUP_NO_PERMISSION = "La app ya no tiene permiso para usar la carpeta de la copia automática. Vuelve a elegirla."
const val AUTO_BACKUP_NO_FOLDER = "No se encuentra la carpeta de la copia automática: puede que se haya borrado. Vuelve a elegirla."
const val AUTO_BACKUP_WRITE_FAILED = "No se pudo escribir la copia automática en la carpeta elegida. ¿Queda espacio?"
const val AUTO_BACKUP_VAULT_PROBLEM = "No se pudo hacer la copia automática: un adjunto falta o está dañado, o la bóveda no cabe en una copia. " +
    "Exporta la bóveda a mano para ver el motivo."
const val AUTO_BACKUP_FAILED = "No se pudo hacer la copia automática."
const val AUTO_BACKUP_OLD_NOT_DELETED = "La copia automática se hizo, pero no se pudieron borrar las antiguas."

// «Borrar las copias automáticas anteriores y hacer una nueva» (tras cambiar la contraseña maestra).
const val AUTO_BACKUP_REPLACED = "Se hizo una copia nueva y se borraron las anteriores."
const val AUTO_BACKUP_REPLACE_WAITS = "Se hará en cuanto abras la bóveda con la contraseña nueva, si no cierras la app antes."

/**
 * How a copy went: [folder] is the one it was made for (as [AutoBackup]'s folder gave it), [writtenAt] when the copy
 * was taken (its name's time) if it was written, even when [error] says its old copies could not be deleted; [error]
 * null when all went well. [copies] is, after a written copy, the ids of this app's copies in that folder, oldest
 * first, to keep for the next rotation; null when nothing was written (the list stays as it was).
 */
data class AutoBackupResult(val folder: String, val writtenAt: Long?, val error: String?, val copies: List<String>? = null)

/**
 * Writes a `seguridad-backup` copy ([VaultStore.exportBackup], still encrypted with the master password) to the
 * folder [target] gives, [AUTO_BACKUP_DEBOUNCE_MS] after the last change saved ([VaultStore.revision]) or after
 * [request], as `seguridad-auto-AAAAMMDD-HHMMSS.sgbak`, keeping the newest [keep] copies (3, 5 or 10; anything
 * else counts as 5). It is first written under a `.tmp` name and only renamed once complete, so the folder never
 * holds a cut copy with the real name. A provider that cannot rename gets it written straight under the real name
 * (deleted if that fails), the only way to have a copy there at all. A name already in the folder is never reused (the next free second is), so
 * no complete copy is deleted to make room. It only ever deletes the copies it made itself ([copies], by id, never by
 * name alone) and temporary files with exactly its names, never the copy just written, and only after a copy was
 * written and checked.
 *
 * Copies are only made from an open vault, where [VaultStore.exportBackup] checks every attachment against its
 * SHA-256: a copy due while the vault is locked waits, and is made as soon as the vault is opened again. So that
 * this survives the app being closed meanwhile, opening the vault also makes a copy when the last change saved
 * ([VaultStore.lastChangeSavedAt]) is newer than the last copy ([lastCopyAt]), or there is none yet.
 *
 * [replaceOldCopies] makes a copy now and then deletes every copy on the [copies] list but the new one (after a change of
 * master password, whose old copies still open with the old one): the new copy first, so that a failure never leaves
 * the folder with none. Like the others it only deletes by id, from that list.
 *
 * Nothing here can close the app or touch the vault: every outcome goes to [onResult], failures as a fixed Spanish
 * message. With no folder ([folder] null, or [open] null) or no vault nothing happens.
 */
@OptIn(FlowPreview::class)
class AutoBackup(
    private val store: VaultStore,
    /** The folder chosen for the copies (on Android, a SAF tree URI); null when they are off. */
    private val folder: () -> String?,
    /** That folder, to write to; null if it cannot be used. */
    private val open: (String) -> BackupTarget?,
    scope: CoroutineScope,
    private val now: () -> Long,
    private val keep: () -> Int,
    /** When the last copy to the chosen folder was taken (epoch ms); null if none yet. */
    private val lastCopyAt: () -> Long?,
    /** The ids of the copies this class made in the chosen folder, oldest first, as [AutoBackupResult.copies] last gave them. */
    private val copies: () -> List<String>,
    /** Keeps the result before the next copy starts (it holds [AutoBackupResult.copies]); must not throw. */
    private val onResult: suspend (AutoBackupResult) -> Unit,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    private val requests = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** A copy was due while the vault was locked: it is made when the vault is next open. */
    @Volatile private var pending = false

    /** [replaceOldCopies] was asked for while the vault was locked: the copy made when it is next open replaces the old ones. */
    @Volatile private var replacePending = false

    /** One copy at a time: the collector's and [replaceOldCopies]'s never overlap. */
    private val lock = Mutex()

    init {
        scope.launch {
            // drop(1): the revision the store starts with is not a change.
            val due = merge(store.revision.drop(1).map { }, requests).debounce(AUTO_BACKUP_DEBOUNCE_MS).map { true }
            // Each time the vault is opened (not each change saved while it is open: those wait for the debounce).
            val opened = store.state.map { it is VaultState.Unlocked }.distinctUntilChanged().filter { it }.map { false }
            // One collector: copies never overlap. An opened vault only copies if one is waiting or was missed.
            merge(due, opened).collect { isDue -> if (isDue || pending || missed()) runOnce() }
        }
    }

    /** Makes a copy soon (after [AUTO_BACKUP_DEBOUNCE_MS]) even without a change: a folder was just chosen. */
    fun request() {
        requests.tryEmit(Unit)
    }

    /**
     * Makes a copy now and, once it is written and checked, deletes this class's other copies (the ids on [copies], never
     * a file by its name) and the leftover temporary ones. Returns the same [AutoBackupResult] it gave [onResult]:
     * [AutoBackupResult.error] null when all went well. If the new copy fails nothing is deleted. With the vault locked
     * (as it is right after a change of master password) nothing can be copied yet: it returns [AUTO_BACKUP_REPLACE_WAITS]
     * (not given to [onResult]) and the next time the vault is opened the copy made replaces the old ones. With no folder
     * or no vault it returns [AUTO_BACKUP_FAILED].
     */
    suspend fun replaceOldCopies(): AutoBackupResult = lock.withLock {
        val uri = folder() ?: return@withLock AutoBackupResult("", null, AUTO_BACKUP_FAILED)
        when (store.state.value) {
            is VaultState.Unlocked -> replacePending = false // Asked for now: a failure is reported, not retried behind the user's back.
            is VaultState.Locked -> {
                replacePending = true
                defer()
                return@withLock AutoBackupResult(uri, null, AUTO_BACKUP_REPLACE_WAITS)
            }
            else -> return@withLock AutoBackupResult(uri, null, AUTO_BACKUP_FAILED)
        }
        attempt(replaceAll = true) ?: if (store.state.value is VaultState.Locked) {
            // It locked while the copy was being made (nothing was written): it waits for the next unlock like the other case.
            replacePending = true
            AutoBackupResult(uri, null, AUTO_BACKUP_REPLACE_WAITS)
        } else {
            AutoBackupResult(uri, null, AUTO_BACKUP_FAILED)
        }
    }

    /** A change saved after the last copy (or no copy yet): one was due when the app was closed. */
    private fun missed(): Boolean = try {
        val changed = store.lastChangeSavedAt()
        val last = lastCopyAt()
        changed != null && (last == null || changed > last)
    } catch (e: Exception) {
        false
    }

    private suspend fun runOnce() {
        lock.withLock { attempt(replaceAll = replacePending) }
    }

    /**
     * One copy to the chosen folder, then its rotation ([replaceAll]: every other copy of its own goes, whatever [keep]
     * says). The result it gave [onResult], or null if nothing was done (no folder, none usable, no vault, or the vault
     * locked, which defers it). Call with [lock] held.
     */
    private suspend fun attempt(replaceAll: Boolean): AutoBackupResult? {
        pending = false
        val uri = folder() ?: return null
        val result = try {
            val target = open(uri) ?: return null
            if (store.state.value !is VaultState.Unlocked) {
                defer()
                return null
            }
            val at = now()
            val written = copyTo(target, at) ?: return null
            if (replaceAll) replacePending = false // The copy is there: what was asked for is being done.
            // Read after the copy: an import that ran meanwhile (it waits for the vault, as the copy does) has already
            // forgotten the replaced vault's copies, so they are never put back on the list.
            val owned = copies()
            val (rotated, mine) = try {
                rotate(target, owned, written, replaceAll)
            } catch (e: Exception) {
                AUTO_BACKUP_OLD_NOT_DELETED to (owned + written.id) // The copy is there all the same.
            }
            AutoBackupResult(uri, at, rotated, mine)
        } catch (e: CancellationException) {
            // Only this class's scope being cancelled ends the copies; one thrown by the folder is just a failure.
            currentCoroutineContext().ensureActive()
            AutoBackupResult(uri, null, AUTO_BACKUP_FAILED)
        } catch (e: Throwable) {
            // Never closes the app: even an Error here only means there is no copy this time.
            AutoBackupResult(uri, null, messageFor(e))
        }
        onResult(result)
        return result
    }

    /** No vault: nothing to copy. A locked one: the copy waits for it to be opened. */
    private fun defer() {
        pending = store.state.value !is VaultState.NoVault
    }

    /**
     * Writes the copy under its temporary name, then renames it; the copy, or null if there was nothing to copy.
     * If the provider cannot rename, the copy is written again straight under its name.
     */
    private suspend fun copyTo(folder: BackupTarget, now: Long): BackupEntry? {
        val name = freeName(folder.list(AUTO_BACKUP_PREFIX).mapTo(HashSet()) { it.name }, now)
        val temp = folder.create(name + TEMP_SUFFIX)
        if (temp.canRename) {
            if (!fill(temp)) return null
            val renamed = try {
                temp.rename(name)
                true
            } catch (e: Exception) {
                discard(temp) // Refused (some providers cannot rename): written again below.
                false
            }
            // Outside the try: once renamed, the complete copy is never discarded, whatever reading its id does.
            if (renamed) return BackupEntry(temp.id, name)
        } else {
            discard(temp)
        }
        val direct = folder.create(name)
        return if (fill(direct)) BackupEntry(direct.id, name) else null
    }

    /** Writes the copy to [file]; false if there was nothing to copy. On false or a failure, [file] is deleted. */
    private suspend fun fill(file: BackupFile): Boolean {
        try {
            var exported = false
            file.write { out -> exported = store.exportBackup(out) }
            if (!exported) {
                file.delete() // Locked or deleted meanwhile: no empty file left.
                defer()
            }
            return exported
        } catch (e: Throwable) {
            // A cut or refused copy never stays in the folder, under either name.
            discard(file)
            throw e
        }
    }

    private suspend fun discard(file: BackupFile) = withContext(NonCancellable) {
        try {
            file.delete()
        } catch (ignored: Exception) {
            // Gone with the folder; a temporary one is cleaned up by the next copy.
        }
    }

    /** The name for [now], or (clock set back) the next second whose copy and temporary names are both free. */
    private fun freeName(taken: Set<String>, now: Long): String {
        var at = Instant.ofEpochMilli(now)
        while (true) {
            val name = AUTO_BACKUP_PREFIX + STAMP.format(at.atZone(zone)) + SUFFIX
            if (name !in taken && name + TEMP_SUFFIX !in taken) return name
            at = at.plusSeconds(1)
        }
    }

    /**
     * Deletes the oldest of this class's own copies beyond [keep] ([replaceAll]: all of them but the new one), and
     * leftover temporary ones. Returns the error (null if all went well) and the ids of its copies still in the folder,
     * oldest first, [newest] last.
     *
     * Its own copies are only those in [owned], the documents it created, by id: never a file by its name alone, as
     * another app that can write to the folder could leave some with a later date in the name to have the real ones
     * deleted. They are ordered by when they were made, not by the date in their names, so a clock set back changes
     * nothing either. Copies made before 1.2.1 are not on the list: they stay until deleted by hand.
     */
    private fun rotate(folder: BackupTarget, owned: List<String>, newest: BackupEntry, replaceAll: Boolean): Pair<String?, List<String>> {
        val present = folder.list(AUTO_BACKUP_PREFIX)
        val ids = present.mapTo(HashSet()) { it.id }
        val kept = if (replaceAll) 1 else keep().takeIf { it in AUTO_BACKUP_KEEP_CHOICES } ?: AUTO_BACKUP_KEEP_DEFAULT
        // The one just written always stays, plus the newest others; a copy already gone is forgotten.
        val others = owned.filter { it in ids && it != newest.id }.distinct()
        val old = others.dropLast(kept - 1)
        val leftovers = present.filter { TEMP_NAME.matches(it.name) }.map { it.id }
        val notDeleted = ArrayList<String>()
        var failed = false
        for (id in old + leftovers) {
            try {
                folder.delete(id)
            } catch (e: Exception) {
                failed = true
                if (id in old) notDeleted += id // Still ours: tried again next time.
            }
        }
        return (if (failed) AUTO_BACKUP_OLD_NOT_DELETED else null) to (notDeleted + others.takeLast(kept - 1) + newest.id)
    }

    private fun messageFor(e: Throwable): String = when (e) {
        is SecurityException -> AUTO_BACKUP_NO_PERMISSION
        is FileNotFoundException -> AUTO_BACKUP_NO_FOLDER
        is CryptoFailure -> AUTO_BACKUP_VAULT_PROBLEM
        is IOException -> AUTO_BACKUP_WRITE_FAILED
        else -> AUTO_BACKUP_FAILED
    }
}
