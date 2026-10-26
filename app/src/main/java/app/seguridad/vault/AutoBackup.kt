package app.seguridad.vault

import app.seguridad.crypto.CryptoFailure
import app.seguridad.settings.AUTO_BACKUP_KEEP_CHOICES
import app.seguridad.settings.AUTO_BACKUP_KEEP_DEFAULT
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
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
    /** Creates the file [name] and lets [writer] fill it; [writer]'s stream is closed afterwards. */
    suspend fun write(name: String, writer: suspend (OutputStream) -> Unit)

    /** The names of the files in the folder that start with [prefix]. */
    fun list(prefix: String): List<String>

    fun delete(name: String)

    /** Renames [from] to [to] (which does not exist). */
    fun rename(from: String, to: String)
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
 * Writes a `seguridad-backup` copy ([VaultStore.exportBackup], still encrypted with the master password) to the
 * folder [target] gives, [AUTO_BACKUP_DEBOUNCE_MS] after the last change saved ([VaultStore.revision]) or after
 * [request], as `seguridad-auto-AAAAMMDD-HHMMSS.sgbak`, keeping the newest [keep] copies (3, 5 or 10; anything
 * else counts as 5). It is first written under a `.tmp` name and only renamed once complete, so the folder never
 * holds a cut copy with the real name. It only ever deletes files with exactly those names, and never the copy
 * just written.
 *
 * Nothing here can close the app or touch the vault: every failure goes to [onError] as a fixed Spanish message;
 * [onError] gets null after a copy that went well. With no folder ([target] null) or no vault nothing happens.
 */
@OptIn(FlowPreview::class)
class AutoBackup(
    private val store: VaultStore,
    private val target: () -> BackupTarget?,
    scope: CoroutineScope,
    private val now: () -> Long,
    private val keep: () -> Int,
    private val onError: (String?) -> Unit,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    private val requests = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    init {
        scope.launch {
            // drop(1): the revision the store starts with is not a change.
            merge(store.revision.drop(1).map { }, requests)
                .debounce(AUTO_BACKUP_DEBOUNCE_MS)
                .collect { runOnce() }
        }
    }

    /** Makes a copy soon (after [AUTO_BACKUP_DEBOUNCE_MS]) even without a change: a folder was just chosen. */
    fun request() {
        requests.tryEmit(Unit)
    }

    private suspend fun runOnce() {
        try {
            val folder = target() ?: return
            val written = copyTo(folder) ?: return
            onError(rotate(folder, written))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // Never closes the app: even an Error here only means there is no copy this time.
            onError(messageFor(e))
        }
    }

    /** Writes the copy under its temporary name, then renames it; its name, or null if there was nothing to copy. */
    private suspend fun copyTo(folder: BackupTarget): String? {
        val name = AUTO_BACKUP_PREFIX + STAMP.format(Instant.ofEpochMilli(now()).atZone(zone)) + SUFFIX
        val temp = name + TEMP_SUFFIX
        var exported = false
        try {
            folder.write(temp) { out -> exported = store.exportBackup(out) }
            if (!exported) {
                folder.delete(temp) // No vault: nothing to copy, and no empty file left.
                return null
            }
            // Only with the clock set back: the complete copy just written replaces the one with its name.
            if (name in folder.list(AUTO_BACKUP_PREFIX)) folder.delete(name)
            folder.rename(temp, name)
            return name
        } catch (e: Throwable) {
            // A cut or refused copy never stays in the folder, not even under its temporary name.
            withContext(NonCancellable) {
                try {
                    folder.delete(temp)
                } catch (ignored: Exception) {
                    // Gone with the folder, or cleaned up by the next copy.
                }
            }
            throw e
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
