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

    /** The names of the files in the folder that start with [prefix]. */
    fun list(prefix: String): List<String>

    fun delete(name: String)
}

/** A file [BackupTarget.create] made. */
interface BackupFile {
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

/** Exactly the names this class writes: the only files it ever deletes. */
private val COPY_NAME = Regex("seguridad-auto-\\d{8}-\\d{6}\\.sgbak")
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

/**
 * How a copy went: [folder] is the one it was made for (as [AutoBackup]'s folder gave it), [writtenAt] when the copy
 * was taken (its name's time) if it was written, even when [error] says its old copies could not be deleted; [error]
 * null when all went well.
 */
data class AutoBackupResult(val folder: String, val writtenAt: Long?, val error: String?)

/**
 * Writes a `seguridad-backup` copy ([VaultStore.exportBackup], still encrypted with the master password) to the
 * folder [target] gives, [AUTO_BACKUP_DEBOUNCE_MS] after the last change saved ([VaultStore.revision]) or after
 * [request], as `seguridad-auto-AAAAMMDD-HHMMSS.sgbak`, keeping the newest [keep] copies (3, 5 or 10; anything
 * else counts as 5). It is first written under a `.tmp` name and only renamed once complete, so the folder never
 * holds a cut copy with the real name. A provider that cannot rename gets it written straight under the real name
 * (deleted if that fails), the only way to have a copy there at all. A name already in the folder is never reused (the next free second is), so
 * no complete copy is deleted to make room. It only ever deletes files with exactly those names, never the copy
 * just written, and only after a copy was written and checked.
 *
 * Copies are only made from an open vault, where [VaultStore.exportBackup] checks every attachment against its
 * SHA-256: a copy due while the vault is locked waits, and is made as soon as the vault is opened again. So that
 * this survives the app being closed meanwhile, opening the vault also makes a copy when the last change saved
 * ([VaultStore.lastChangeSavedAt]) is newer than the last copy ([lastCopyAt]), or there is none yet.
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
    private val onResult: (AutoBackupResult) -> Unit,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    private val requests = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** A copy was due while the vault was locked: it is made when the vault is next open. */
    @Volatile private var pending = false

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

    /** A change saved after the last copy (or no copy yet): one was due when the app was closed. */
    private fun missed(): Boolean = try {
        val changed = store.lastChangeSavedAt()
        val last = lastCopyAt()
        changed != null && (last == null || changed > last)
    } catch (e: Exception) {
        false
    }

    private suspend fun runOnce() {
        pending = false
        val uri = folder() ?: return
        try {
            val target = open(uri) ?: return
            if (store.state.value !is VaultState.Unlocked) return defer()
            val at = now()
            val written = copyTo(target, at) ?: return
            val rotated = try {
                rotate(target, written)
            } catch (e: Exception) {
                AUTO_BACKUP_OLD_NOT_DELETED // The copy is there all the same.
            }
            onResult(AutoBackupResult(uri, at, rotated))
        } catch (e: CancellationException) {
            // Only this class's scope being cancelled ends the copies; one thrown by the folder is just a failure.
            currentCoroutineContext().ensureActive()
            onResult(AutoBackupResult(uri, null, AUTO_BACKUP_FAILED))
        } catch (e: Throwable) {
            // Never closes the app: even an Error here only means there is no copy this time.
            onResult(AutoBackupResult(uri, null, messageFor(e)))
        }
    }

    /** No vault: nothing to copy. A locked one: the copy waits for it to be opened. */
    private fun defer() {
        pending = store.state.value !is VaultState.NoVault
    }

    /**
     * Writes the copy under its temporary name, then renames it; its name, or null if there was nothing to copy.
     * If the provider cannot rename, the copy is written again straight under its name.
     */
    private suspend fun copyTo(folder: BackupTarget, now: Long): String? {
        val name = freeName(folder.list(AUTO_BACKUP_PREFIX).toSet(), now)
        val temp = folder.create(name + TEMP_SUFFIX)
        if (temp.canRename) {
            if (!fill(temp)) return null
            try {
                temp.rename(name)
                return name
            } catch (e: Exception) {
                discard(temp) // Refused (some providers cannot rename): written again below.
            }
        } else {
            discard(temp)
        }
        return if (fill(folder.create(name))) name else null
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

    /** Deletes the oldest copies beyond [keep] and leftover temporary ones; null if all went well. */
    private fun rotate(folder: BackupTarget, newest: String): String? {
        val names = folder.list(AUTO_BACKUP_PREFIX)
        val kept = keep().takeIf { it in AUTO_BACKUP_KEEP_CHOICES } ?: AUTO_BACKUP_KEEP_DEFAULT
        // The name sorts by date (fixed width): the one just written always stays, plus the newest others.
        val old = names.filter { COPY_NAME.matches(it) && it != newest }.sortedDescending().drop(kept - 1)
        val leftovers = names.filter { TEMP_NAME.matches(it) }
        var failed = false
        for (n in old + leftovers) {
            try {
                folder.delete(n)
            } catch (e: Exception) {
                failed = true
            }
        }
        return if (failed) AUTO_BACKUP_OLD_NOT_DELETED else null
    }

    private fun messageFor(e: Throwable): String = when (e) {
        is SecurityException -> AUTO_BACKUP_NO_PERMISSION
        is FileNotFoundException -> AUTO_BACKUP_NO_FOLDER
        is CryptoFailure -> AUTO_BACKUP_VAULT_PROBLEM
        is IOException -> AUTO_BACKUP_WRITE_FAILED
        else -> AUTO_BACKUP_FAILED
    }
}
