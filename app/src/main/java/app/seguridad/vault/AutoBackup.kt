package app.seguridad.vault

import app.seguridad.core.Notice
import app.seguridad.crypto.CryptoFailure
import app.seguridad.settings.AUTO_BACKUP_KEEP_CHOICES
import app.seguridad.settings.AUTO_BACKUP_KEEP_DEFAULT
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

/** The names of complete copies: only counted (COPIAS-2), never deleted by name (NV-2). */
private val COPY_NAME = Regex("seguridad-auto-\\d{8}-\\d{6}\\.sgbak")

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
// Only what is true whatever the folder holds: the copies deleted are those the app had listed (COPIAS-2).
const val AUTO_BACKUP_REPLACED =
    "Se hizo una copia nueva y se borraron las copias automáticas anteriores que la app tenía apuntadas en esta carpeta. " +
        "Las que se hayan copiado a otro sitio (otra carpeta, la nube o su papelera) no se borran."

/** COPIAS-2: added to [AUTO_BACKUP_REPLACED] when [n] files with a copy's name, not on the app's list, are left in the folder. */
fun autoBackupUnlistedText(n: Int): String = if (n == 1) {
    "En la carpeta queda 1 copia con nombre de copia automática que la app no tenía apuntada (por ejemplo, de antes de " +
        "la versión 1.2.1 o de otra bóveda): no se ha borrado y puede que se abra con una contraseña anterior. Si no la " +
        "necesitas, bórrala a mano."
} else {
    "En la carpeta quedan $n copias con nombre de copia automática que la app no tenía apuntadas (por ejemplo, de antes " +
        "de la versión 1.2.1 o de otra bóveda): no se han borrado y puede que se abran con una contraseña anterior. Si no " +
        "las necesitas, bórralas a mano."
}

/** What a replacement that went well says ([AutoBackupResult.unlistedLeft]: 0 when unknown, as after a re-creation). */
fun replacedText(unlistedLeft: Int): String =
    if (unlistedLeft > 0) AUTO_BACKUP_REPLACED + " " + autoBackupUnlistedText(unlistedLeft) else AUTO_BACKUP_REPLACED

const val AUTO_BACKUP_REPLACE_WAITS = "Se hará en cuanto abras la bóveda con la contraseña nueva."

/**
 * How a copy went: [folder] is the one it was made for (as [AutoBackup]'s folder gave it), [writtenAt] when the copy
 * was taken (its name's time) if it was written, even when [error] says its old copies could not be deleted; [error]
 * null when all went well. [copies] is, after a written copy, the ids of this app's copies in that folder, oldest
 * first, to keep for the next rotation; null when nothing was written (the list stays as it was).
 */
data class AutoBackupResult(
    val folder: String,
    val writtenAt: Long?,
    val error: String?,
    val copies: List<String>? = null,
    /**
     * This copy replaced the old ones ([AutoBackup.replaceOldCopies]): written, and every target and every leftover
     * temporary copy (which may be a complete one under the old password) deleted (CR-1, M3). Once recorded, the saved
     * request is done. False when any was left ([error] then says so): the request stays.
     */
    val replaced: Boolean = false,
    /** Nothing was written: [AutoBackup.replaceOldCopies] was asked while the vault was locked, to be done after the next unlock. */
    val replaceRequested: Boolean = false,
    /**
     * With [replaceRequested]: the ids of the copies to replace (those listed when it was asked, M4). After an attempt
     * that wrote a copy: the targets still left; null when this result says nothing about them (they stay as saved).
     */
    val replaceTargets: List<String>? = null,
    /** The failure ([error]) this result tells the user about for the pending request, to be saved with it (M2); null if none. */
    val toldKey: String? = null,
    /** [AutoBackup.replaceOldCopies] was asked again with the vault open: what was told for the request starts again. */
    val newRequest: Boolean = false,
    /**
     * [VaultStore.generation] when the copy started; null when nothing was written. The settings keep [copies] only if
     * it is still the vault's when this is recorded (CADENAS-3).
     */
    val generation: Long? = null,
    /**
     * After [replaced]: the files left in the folder with exactly a copy's name that are not on the list (made before
     * 1.2.1, before choosing another folder and coming back, by another vault, or by someone else). Never deleted, only
     * counted, so the text does not claim more than was done (COPIAS-2).
     */
    val unlistedLeft: Int = 0,
)

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
 * [replaceOldCopies] makes a copy now and then deletes the old copies (after a change of master password, whose old
 * copies still open with the old one): the new copy first, so that a failure never leaves the folder with none. Like
 * the others it only deletes by id, from the [copies] list. It runs in this class's [scope], so the caller leaving (a
 * rotation) cannot cut it between the deletions and [onResult]. Asked while the vault is locked, the request is handed
 * to [onResult] ([AutoBackupResult.replaceRequested]) to be saved ([replacePending]) with the ids of the copies listed
 * at that moment, its targets ([AutoBackupResult.replaceTargets], M4): the copies made before the change. The first
 * copy after the next unlock, even after a restart, deletes those targets, and the copies made after the request (under
 * the new password) follow the normal rotation of [keep]. A settings file saved before 1.3.0 has a request without
 * targets: that first attempt deletes every listed copy but the new one, as before, and what it could not delete
 * becomes the targets.
 *
 * The request is done ([AutoBackupResult.replaced], which clears it) only when every target and every leftover
 * temporary copy is gone (CR-1, M3): a temporary copy whose rename and discard failed may be a complete copy under the
 * old password. Any failure keeps it (no copy written, the rotation failing, a target or a temporary copy that could not
 * be deleted), so the next copy tries again, still only by id from the list and only while the vault is the one of the
 * list ([VaultStore.generation]); an import or a new vault forgets the list and the request with it.
 *
 * How it went is kept in [replaceOutcome] until the user has been told ([replaceOutcomeShown]). Each different failure
 * is told once per request, and the ones already told are saved with the request ([replaceTold],
 * [AutoBackupResult.toldKey]), so neither failures that alternate nor a restart open the dialog again (M2); the success
 * is told once, and ends the request.
 *
 * A copy whose vault was replaced while it was written (an import, a new vault, [VaultStore.generation]) is left in the
 * folder unlisted: never rotated, never deleted by A6, and the list the import forgot is never written back.
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
    private val scope: CoroutineScope,
    private val now: () -> Long,
    private val keep: () -> Int,
    /** When the last copy to the chosen folder was taken (epoch ms); null if none yet. */
    private val lastCopyAt: () -> Long?,
    /** The ids of the copies this class made in the chosen folder, oldest first, as [AutoBackupResult.copies] last gave them. */
    private val copies: () -> List<String>,
    /** Whether the replacement of the old copies was asked for and is not done yet (saved with the settings). */
    private val replacePending: () -> Boolean,
    /**
     * The ids that replacement still has to delete ([AutoBackupResult.replaceTargets], saved with the settings); null for
     * a request saved before 1.3.0, which then deletes every listed copy but the new one, once.
     */
    private val replaceTargets: () -> List<String>?,
    /** The failures already told for the request now pending ([AutoBackupResult.toldKey], saved with the settings). */
    private val replaceTold: () -> List<String>,
    /** Keeps the result before the next copy starts (it holds [AutoBackupResult.copies]); must not throw. */
    private val onResult: suspend (AutoBackupResult) -> Unit,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    private val requests = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** A copy was due while the vault was locked: it is made when the vault is next open. */
    @Volatile private var pending = false

    private val _replacing = MutableStateFlow(false)

    /** True while [replaceOldCopies] works (also after the screen that asked for it was recreated). */
    val replacing: StateFlow<Boolean> = _replacing.asStateFlow()

    private val outcome = Notice<AutoBackupResult>()

    /**
     * How a replacement that ran by itself (it was asked for while locked) went, until [replaceOutcomeShown]: it lives with
     * this object, not with a screen, so an activity re-created meanwhile still shows it, and once shown it is gone.
     */
    val replaceOutcome: StateFlow<AutoBackupResult?> = outcome.value

    /**
     * The user has been told [shown], the outcome that was on screen. Only that one goes: an outcome that arrived between
     * showing it and closing it is still told (compare-and-set by identity, [Notice]).
     */
    fun replaceOutcomeShown(shown: AutoBackupResult) {
        outcome.shown(shown)
    }

    /** One copy at a time: the collector's and [replaceOldCopies]'s never overlap. */
    private val lock = Mutex()

    init {
        scope.launch {
            // drop(1): the revision the store starts with is not a change.
            val due = merge(store.revision.drop(1).map { }, requests).debounce(AUTO_BACKUP_DEBOUNCE_MS).map { true }
            // Each time the vault is opened (not each change saved while it is open: those wait for the debounce).
            val opened = store.state.map { it is VaultState.Unlocked }.distinctUntilChanged().filter { it }.map { false }
            // One collector: copies never overlap. An opened vault only copies if one is waiting or was missed.
            merge(due, opened).collect { isDue -> if (isDue || pending || replacePending() || missed()) runOnce() }
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
     * (as it is right after a change of master password) nothing can be copied yet: the request is given to [onResult]
     * to be saved, and it returns [AUTO_BACKUP_REPLACE_WAITS] ([AUTO_BACKUP_FAILED] if it could not be saved). With no
     * folder or no vault it returns [AUTO_BACKUP_FAILED]. The work is not cancelled with the caller.
     */
    suspend fun replaceOldCopies(): AutoBackupResult = scope.async { replaceNow() }.await()

    private suspend fun replaceNow(): AutoBackupResult = lock.withLock {
        _replacing.value = true
        try {
            val uri = folder() ?: return@withLock AutoBackupResult("", null, AUTO_BACKUP_FAILED)
            when (store.state.value) {
                is VaultState.Unlocked -> Unit
                is VaultState.Locked -> return@withLock waitForUnlock(uri)
                else -> return@withLock AutoBackupResult(uri, null, AUTO_BACKUP_FAILED)
            }
            // A failure here is reported: asked while open, it is not retried behind the user's back (unless a request
            // saved while locked is still pending: it goes on, with its own targets, and what it told starts again).
            val pendingTargets = if (replacePending()) replaceTargets() else null
            attempt(Replace(pendingTargets, announce = false, newRequest = true))
                ?: if (store.state.value is VaultState.Locked) waitForUnlock(uri) else AutoBackupResult(uri, null, AUTO_BACKUP_FAILED)
        } finally {
            _replacing.value = false
        }
    }

    /** The vault is locked: the request is saved (through [onResult]) and the first copy after the next unlock does it. */
    private suspend fun waitForUnlock(uri: String): AutoBackupResult {
        defer()
        // M4: the copies listed now are the ones to replace (all made before the change); later ones follow [keep].
        onResult(AutoBackupResult(uri, null, AUTO_BACKUP_REPLACE_WAITS, replaceRequested = true, replaceTargets = copies()))
        // Not saved (a full disk): do not say it will be done.
        return AutoBackupResult(uri, null, if (replacePending()) AUTO_BACKUP_REPLACE_WAITS else AUTO_BACKUP_FAILED)
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
        lock.withLock {
            // Read inside the lock, from what is saved: a request made before a restart is done too.
            val replace = replacePending()
            val result = attempt(if (replace) Replace(replaceTargets(), announce = true) else null)
            // M2: told when it is the success (which ends the request) or a failure not yet told for this request.
            if (replace && result != null && (result.replaced || result.toldKey != null)) outcome.post(result)
        }
    }

    /**
     * A replacement of the old copies: [targets] the ids to delete (null: every listed copy but the new one, as before
     * 1.3.0); [announce] when its failure may be told to the user ([AutoBackupResult.toldKey]); [newRequest] when it was
     * asked again just now.
     */
    private class Replace(val targets: List<String>?, val announce: Boolean, val newRequest: Boolean = false)

    /**
     * One copy to the chosen folder, then its rotation ([replace]: its targets go too, see [rotate]). The result it gave
     * [onResult], or null if nothing was done (no folder, none usable, no vault, or the vault locked, which defers it).
     * Call with [lock] held.
     */
    private suspend fun attempt(replace: Replace?): AutoBackupResult? {
        val replaceAll = replace != null
        pending = false
        val uri = folder() ?: return null
        val result = try {
            val target = open(uri) ?: return null
            if (store.state.value !is VaultState.Unlocked) {
                defer()
                return null
            }
            val at = now()
            // Read before the snapshot: if it is the same after the copy, no import, new vault or deletion came in between,
            // so the copy is of the vault the list belongs to. The vault's mutex only covers the snapshot, not the write
            // (a slow provider must not hold the vault), so an import can run while the copy is written (CADENAS-3).
            val generation = store.generation
            val written = copyTo(target, at) ?: return null
            if (store.generation != generation) {
                stale(uri, at, generation, replaceAll)
            } else {
                val owned = copies()
                // A request without targets (saved before 1.3.0): every listed copy but the new one, as before (M4).
                val targets = replace?.let { it.targets ?: owned.filter { id -> id != written.id } }
                val rotation = try {
                    rotate(target, owned, written, targets, generation)
                } catch (e: Exception) {
                    // The copy is there all the same; nothing was deleted for sure, so every target is still one.
                    Rotation(AUTO_BACKUP_OLD_NOT_DELETED, owned + written.id, 0, targets, replaceDone = false)
                }
                if (rotation.mine == null) {
                    stale(uri, at, generation, replaceAll)
                } else {
                    // CR-1: «replaced» (which clears the saved request) only when every target and every leftover temporary
                    // copy went. Otherwise the request stays with the targets left, and the next copy (after a change, or
                    // the next unlock) tries again.
                    AutoBackupResult(
                        uri, at, rotation.error, rotation.mine, replaced = replaceAll && rotation.replaceDone,
                        generation = generation, unlistedLeft = rotation.unlistedLeft,
                        replaceTargets = if (replaceAll) rotation.targetsLeft else null, newRequest = replace?.newRequest == true,
                    )
                }
            }
        } catch (e: CancellationException) {
            // Only this class's scope being cancelled ends the copies; one thrown by the folder is just a failure.
            currentCoroutineContext().ensureActive()
            AutoBackupResult(uri, null, AUTO_BACKUP_FAILED, newRequest = replace?.newRequest == true)
        } catch (e: Throwable) {
            // Never closes the app: even an Error here only means there is no copy this time.
            AutoBackupResult(uri, null, messageFor(e), newRequest = replace?.newRequest == true)
        }
        val told = result.error?.takeIf { replace != null && replace.announce && !result.replaced && it !in replaceTold() }
        val recorded = if (told != null) result.copy(toldKey = told) else result
        onResult(recorded)
        return recorded
    }

    /**
     * A copy of a vault replaced while it was written: it stays in the folder, never listed (so never deleted by a
     * rotation of the vault there is now, nor by A6), and the list already forgotten is not written back. A replacement
     * asked for says it deleted nothing, and its request (if any is left) stays.
     */
    private fun stale(uri: String, at: Long, generation: Long, replaceAll: Boolean) =
        AutoBackupResult(uri, at, if (replaceAll) AUTO_BACKUP_OLD_NOT_DELETED else null, copies = null, generation = generation)

    /**
     * What [rotate] did: [mine] null when the vault was replaced meanwhile (it stopped deleting). For a replacement,
     * [targetsLeft] the targets still in the folder and [replaceDone] whether every target and leftover temporary copy went.
     */
    private class Rotation(
        val error: String?,
        val mine: List<String>?,
        val unlistedLeft: Int,
        val targetsLeft: List<String>? = null,
        val replaceDone: Boolean = false,
    )

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
     * Deletes the oldest of this class's own copies beyond [keep], and leftover temporary ones; with [targets] (a
     * replacement), every copy among them too, and [keep] applies to the others only (M4). Returns the error (null if
     * all went well) and the ids of its copies still in the folder, oldest first, [newest] last.
     *
     * Its own copies are only those in [owned], the documents it created, by id: never a file by its name alone, as
     * another app that can write to the folder could leave some with a later date in the name to have the real ones
     * deleted, and a target is deleted only if it is on that list too. They are ordered by when they were made, not by the
     * date in their names, so a clock set back changes nothing either. Copies made before 1.2.1 are not on the list: they
     * stay until deleted by hand; with [targets] they are counted ([Rotation.unlistedLeft]). Before each deletion it
     * checks that the vault is still the one of [generation]: if an import replaced it meanwhile, it stops and lists
     * nothing (CADENAS-3).
     */
    private fun rotate(folder: BackupTarget, owned: List<String>, newest: BackupEntry, targets: List<String>?, generation: Long): Rotation {
        val present = folder.list(AUTO_BACKUP_PREFIX)
        val ids = present.mapTo(HashSet()) { it.id }
        val keepN = keep().takeIf { it in AUTO_BACKUP_KEEP_CHOICES } ?: AUTO_BACKUP_KEEP_DEFAULT
        // The one just written always stays; a copy already gone is forgotten.
        val others = owned.filter { it in ids && it != newest.id }.distinct()
        val doomed = targets?.toHashSet() ?: emptySet()
        val old = others.filter { it in doomed }
        val rest = others.filter { it !in doomed }
        val surplus = rest.dropLast(keepN - 1)
        val leftovers = present.filter { TEMP_NAME.matches(it.name) }.map { it.id }
        val notDeleted = HashSet<String>()
        var failed = false
        var leftoverLeft = false
        for (id in old + surplus + leftovers) {
            if (store.generation != generation) return Rotation(null, null, 0)
            try {
                folder.delete(id)
            } catch (e: Exception) {
                failed = true
                if (id in leftovers) leftoverLeft = true else notDeleted += id // Still ours: tried again next time.
            }
        }
        if (store.generation != generation) return Rotation(null, null, 0)
        val kept = rest.takeLast(keepN - 1).toHashSet()
        val mine = others.filter { it in notDeleted || it in kept } + newest.id
        if (targets == null) return Rotation(if (failed) AUTO_BACKUP_OLD_NOT_DELETED else null, mine, 0)
        val ownedIds = owned.toHashSet()
        val unlisted = present.count { COPY_NAME.matches(it.name) && it.id !in ownedIds && it.id != newest.id }
        val targetsLeft = old.filter { it in notDeleted }
        return Rotation(
            if (failed) AUTO_BACKUP_OLD_NOT_DELETED else null, mine, unlisted, targetsLeft,
            replaceDone = targetsLeft.isEmpty() && !leftoverLeft,
        )
    }

    private fun messageFor(e: Throwable): String = when (e) {
        is SecurityException -> AUTO_BACKUP_NO_PERMISSION
        is FileNotFoundException -> AUTO_BACKUP_NO_FOLDER
        is CryptoFailure -> AUTO_BACKUP_VAULT_PROBLEM
        is IOException -> AUTO_BACKUP_WRITE_FAILED
        else -> AUTO_BACKUP_FAILED
    }
}

/** What the dialog after a change of master password says about the replacement of the old copies. */
sealed interface ReplaceStatus {
    /** Not asked for yet. */
    data object NotAsked : ReplaceStatus

    /** Asked, but it cannot be told how it went (the screen was re-created while it ran): never shown as done. */
    data object Unknown : ReplaceStatus

    /** Asked while the vault is locked: it is done after the next unlock. */
    data object Waits : ReplaceStatus

    /** A copy was written and the old ones were deleted. */
    data object Done : ReplaceStatus

    data class Failed(val message: String) : ReplaceStatus
}

/**
 * The status to show. [requestedAt] is when the button was pressed (0 if not yet); [answered] and [answerError] what the call
 * said (kept across a re-creation); [pending], [lastError] and [lastBackupAt] what the settings say, which are what is
 * left when the answer was lost, and what wins over an answer «waits» once they show the replacement ran. «Done» only
 * comes from an answer without error or from a copy written after the request with no error and nothing waiting.
 */
fun replaceStatus(requestedAt: Long, answered: Boolean, answerError: String?, pending: Boolean, lastError: String?, lastBackupAt: Long?): ReplaceStatus = when {
    requestedAt == 0L -> ReplaceStatus.NotAsked
    // T11-b: «waits» was true when the call answered; once the settings record the replacement (nothing waiting and a
    // copy written after the request), that record is what is shown.
    answered && answerError == AUTO_BACKUP_REPLACE_WAITS && !pending && lastBackupAt != null && lastBackupAt >= requestedAt ->
        lastError?.let { ReplaceStatus.Failed(it) } ?: ReplaceStatus.Done
    // CR-1, M1: a copy was written after the request (so the replacement ran) and the last attempt failed: the request
    // still waits to be retried, and the dialog says what happened (with «Volver a intentarlo») instead of «se hará en
    // cuanto abras la bóveda».
    pending && lastError != null && lastBackupAt != null && lastBackupAt >= requestedAt -> ReplaceStatus.Failed(lastError)
    answered -> when (answerError) {
        null -> ReplaceStatus.Done
        AUTO_BACKUP_REPLACE_WAITS -> ReplaceStatus.Waits
        else -> ReplaceStatus.Failed(answerError)
    }
    pending -> ReplaceStatus.Waits
    lastError != null -> ReplaceStatus.Failed(lastError)
    lastBackupAt != null && lastBackupAt >= requestedAt -> ReplaceStatus.Done
    else -> ReplaceStatus.Unknown
}
