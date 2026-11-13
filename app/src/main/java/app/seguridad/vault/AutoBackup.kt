package app.seguridad.vault

import app.seguridad.core.Notice
import app.seguridad.crypto.CryptoFailure
import app.seguridad.imports.IMPORT_COPY_FAILED
import app.seguridad.imports.IMPORT_COPY_LOCKED
import app.seguridad.imports.IMPORT_COPY_NO_FOLDER
import app.seguridad.imports.IMPORT_COPY_NO_PERMISSION
import app.seguridad.imports.IMPORT_COPY_VAULT_CHANGED
import app.seguridad.imports.IMPORT_COPY_VAULT_PROBLEM
import app.seguridad.imports.IMPORT_COPY_WRITE_FAILED
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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
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

/**
 * 1.4.0: the copy made just before «Importar de otra app» ([AutoBackup.copyBeforeImport]), in the same folder, with a
 * name of its own so the rotation never takes it for one of its copies: it is never rotated nor counted in [keep].
 */
const val IMPORT_COPY_PREFIX = "seguridad-antes-de-importar-"

/** [TEMP_NAME] for [IMPORT_COPY_PREFIX]: a cut import copy, deleted with the automatic ones' leftovers. */
private val IMPORT_TEMP_NAME = Regex("seguridad-antes-de-importar-\\d{8}-\\d{6}\\.sgbak\\.tmp")

/** [COPY_NAME] for [IMPORT_COPY_PREFIX]: only counted by the «copias sin apuntar» notice, never deleted by name. */
private val IMPORT_COPY_NAME = Regex("seguridad-antes-de-importar-\\d{8}-\\d{6}\\.sgbak")

/**
 * 2.0: the copy made just before the vault passes to format v4 ([AutoBackup.copyBeforeUpgrade]), of the file of version
 * 2 or 3 still on disk, which earlier versions open. Kept as the import copies are: listed with them (so A6 deletes it
 * after a change of master password, as it is under the password of before), never rotated.
 */
const val UPGRADE_COPY_PREFIX = "seguridad-antes-de-2-0-"

/** [TEMP_NAME] for [UPGRADE_COPY_PREFIX]. */
private val UPGRADE_TEMP_NAME = Regex("seguridad-antes-de-2-0-\\d{8}-\\d{6}\\.sgbak\\.tmp")

/** [COPY_NAME] for [UPGRADE_COPY_PREFIX]: only counted by the «copias sin apuntar» notice, never deleted by name. */
private val UPGRADE_COPY_NAME = Regex("seguridad-antes-de-2-0-\\d{8}-\\d{6}\\.sgbak")

/** What [AUTO_BACKUP_PREFIX], [IMPORT_COPY_PREFIX] and [UPGRADE_COPY_PREFIX] start with: the rotation lists the folder once for all. */
private const val COPIES_PREFIX = "seguridad-"

private val STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

// Shown in Ajustes. Fixed texts: never a path, a URI, an attachment name or what an exception said.
const val AUTO_BACKUP_NO_PERMISSION = "La app ya no tiene permiso para usar la carpeta de la copia automática. Vuelve a elegirla."
const val AUTO_BACKUP_NO_FOLDER = "No se encuentra la carpeta de la copia automática: puede que se haya borrado. Vuelve a elegirla."
const val AUTO_BACKUP_WRITE_FAILED = "No se pudo escribir la copia automática en la carpeta elegida. ¿Queda espacio?"
const val AUTO_BACKUP_VAULT_PROBLEM = "No se pudo hacer la copia automática: un adjunto falta o está dañado, o la bóveda no cabe en una copia. " +
    "Exporta la bóveda a mano para ver el motivo."
const val AUTO_BACKUP_FAILED = "No se pudo hacer la copia automática."

/**
 * R-6 (final review of the Abogado Digital): the next copy does try again, as the copies not deleted stay on the list (a
 * normal rotation) or as targets of the saved request (a replacement whose request is saved; it stays until every target
 * went). Only where that holds: otherwise [AUTO_BACKUP_OLD_NOT_DELETED_NO_RETRY].
 */
const val AUTO_BACKUP_OLD_NOT_DELETED =
    "La copia automática se hizo, pero no se pudieron borrar las antiguas. Se volverá a intentar en la próxima copia."

/**
 * A replacement with no request saved behind it (an import dropped it while the copy was written, or it could not be
 * saved): nothing tries those copies again, so no promise that it will.
 */
const val AUTO_BACKUP_OLD_NOT_DELETED_NO_RETRY = "La copia automática se hizo, pero no se pudieron borrar las antiguas."

// «Borrar las copias automáticas anteriores y hacer una nueva» (tras cambiar la contraseña maestra).
// Only what is true whatever the folder holds: the copies deleted are those the app had listed (COPIAS-2). O-2 (final
// review of the Abogado Digital): the colon keeps «que la app tenía apuntadas» from being read as said of «contraseña».
const val AUTO_BACKUP_REPLACED =
    "Se hizo una copia nueva y se borraron las copias automáticas y las de antes de importar hechas antes del cambio de " +
        "contraseña: las que la app tenía apuntadas en esta carpeta. " +
        "Las que se hayan copiado a otro sitio (otra carpeta, la nube o su papelera) no se borran."

/**
 * 2.0, task 8: [AUTO_BACKUP_REPLACED] when the vault has several folders for its copies: each one deleted only the copies
 * it had listed.
 */
const val AUTO_BACKUP_REPLACED_SEVERAL =
    "Se hizo una copia nueva en cada carpeta y se borraron las copias automáticas y las de antes de importar hechas antes " +
        "del cambio de contraseña: las que la app tenía apuntadas en cada carpeta. " +
        "Las que se hayan copiado a otro sitio (otra carpeta, la nube o su papelera) no se borran."

/**
 * COPIAS-2: added to [AUTO_BACKUP_REPLACED] when [n] files with a copy's name, not on the app's list, are left in the folder
 * ([severalFolders]: in the folders, 2.0, task 8).
 */
fun autoBackupUnlistedText(n: Int, severalFolders: Boolean = false): String {
    val where = if (severalFolders) "En las carpetas" else "En la carpeta"
    return if (n == 1) {
        "$where queda 1 copia con nombre de copia de la app (automática o de antes de importar) que la app no tenía " +
            "apuntada (por ejemplo, de antes de " +
            "la versión 1.2.1 o de otra bóveda): no se ha borrado y puede que se abra con una contraseña anterior. Si no la " +
            "necesitas, bórrala a mano."
    } else {
        "$where quedan $n copias con nombre de copia de la app (automática o de antes de importar) que la app no " +
            "tenía apuntadas (por ejemplo, de antes " +
            "de la versión 1.2.1 o de otra bóveda): no se han borrado y puede que se abran con una contraseña anterior. Si no " +
            "las necesitas, bórralas a mano."
    }
}

/**
 * What a replacement that went well says ([AutoBackupResult.unlistedLeft]: 0 when unknown, as after a re-creation), for a
 * vault whose copies go to [folders] folders (2.0, task 8).
 */
fun replacedText(unlistedLeft: Int, folders: Int = 1): String {
    val several = folders > 1
    val done = if (several) AUTO_BACKUP_REPLACED_SEVERAL else AUTO_BACKUP_REPLACED
    return if (unlistedLeft > 0) done + " " + autoBackupUnlistedText(unlistedLeft, several) else done
}

const val AUTO_BACKUP_REPLACE_WAITS = "Se hará en cuanto abras la bóveda con la contraseña nueva."

/** 2.0, task 8: what [AutoBackup.runDue] says after [n] copies made by the calendar. */
fun scheduledCopiesText(n: Int): String = if (n == 1) "Se hizo la copia con calendario." else "Se hicieron $n copias con calendario."

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
    /**
     * 1.4.0: after a written copy, the import copies ([AutoBackup.copyBeforeImport]) still listed and still in the folder,
     * in the order they were made: those a replacement deleted, or that are gone, are no longer on it. Null when this
     * result says nothing about them (the list stays as saved).
     */
    val importCopies: List<String>? = null,
)

/** How [AutoBackup.copyBeforeImport] went. */
sealed interface CopyResult {
    /** The copy is complete under the name [where] (a file name, never a path or a URI). */
    data class Saved(val where: String) : CopyResult

    /** No copy; [message] is a fixed Spanish text that says nothing was imported. */
    data class Failed(val message: String) : CopyResult
}

/**
 * Writes `seguridad-backup` copies ([VaultStore.exportBackup], still encrypted with the master password) of one vault to
 * its targets (2.0, task 8: up to [VaultLimits.BACKUP_TARGETS_MAX] folders, [targets], read again before each copy), as
 * `seguridad-auto-AAAAMMDD-HHMMSS.sgbak`, each keeping its newest [BackupTargetSettings.keep] copies (3, 5 or 10;
 * anything else counts as 5). Copies are made one at a time ([lock]), in the order of the targets, in [scope] (off the
 * main thread), never two at once:
 * - a [BackupSchedule.ON_CHANGE] target gets one [AUTO_BACKUP_DEBOUNCE_MS] after the last change saved
 *   ([VaultStore.revision]), and at the unlock when one was missed ([VaultStore.lastChangeSavedAt] newer than its
 *   [BackupTargetSettings.lastAt]), as 1.x did;
 * - a target with a calendar gets one only from [runDue], when [due] says so: at the unlock and when the app is opened
 *   (no background work, no WorkManager, no permission);
 * - any target gets one after [request] (a folder just chosen, «Hacer una copia ahora»), and at the unlock if one waited
 *   for it, or for its A6 request.
 *
 * Each copy is first written under a `.tmp` name and only renamed once complete, so a folder never holds a cut copy with
 * the real name. A provider that cannot rename gets it written straight under the real name (deleted if that fails), the
 * only way to have a copy there at all. A name already in the folder is never reused (the next free second is), so no
 * complete copy is deleted to make room. A target only ever deletes the copies it made itself (its own
 * [BackupTargetSettings.copies], by id, never by name alone) and temporary files with exactly this class's names, never
 * the copy just written, and only after a copy was written and checked.
 *
 * Copies are only made from an open vault, where [VaultStore.exportBackup] checks every attachment against its SHA-256:
 * a copy due while the vault is locked waits, and is made as soon as the vault is opened again. A lock in the middle
 * stops the copies that follow (they wait for the unlock). This object is of one vault (AppContainer keeps one per
 * vault, with its own store): it never writes another vault's copy, and a copy whose vault was replaced while it was
 * written (an import, a new vault, [VaultStore.generation]) is left in its folder unlisted.
 *
 * [replaceOldCopies] makes a copy now in each target and then deletes the old copies (after a change of master password,
 * whose old copies still open with the old one, A6): the new copy first, so that a failure never leaves the folder with
 * none, by id from the target's own lists only. Asked while the vault is locked, each target's request is handed to
 * [onResult] ([AutoBackupResult.replaceRequested]) to be saved with the ids listed at that moment, its targets (M4); the
 * first copy of that target after the next unlock, even after a restart, whatever its calendar, deletes them. The request
 * is done ([AutoBackupResult.replaced], which clears it) only when every target and every leftover temporary copy is gone
 * (CR-1, M3). How a replacement that ran by itself went is kept in [replaceOutcome] until the user has been told
 * ([replaceOutcomeShown]); each different failure is told once per request (M2).
 *
 * Nothing here can close the app or touch the vault: every outcome goes to [onResult], failures as a fixed Spanish
 * message (never a path, a URI or what an exception said), with [AutoBackupResult.folder] the target it was for. With no
 * target, none usable, or no vault, nothing happens.
 */
@OptIn(FlowPreview::class)
class AutoBackup(
    private val store: VaultStore,
    /** The vault's targets as they are saved now, in their order; read again before each copy. */
    private val targets: () -> List<BackupTargetSettings>,
    /** A target's folder, to write to; null if it cannot be used. */
    private val open: (String) -> BackupTarget?,
    private val scope: CoroutineScope,
    private val now: () -> Long,
    /** Keeps the result before the next copy starts (it holds [AutoBackupResult.copies]); must not throw. */
    private val onResult: suspend (AutoBackupResult) -> Unit,
    /** The time zone of the calendar and of the copies' names; null: the phone's, read at each use (it may change). */
    private val zone: ZoneId? = null,
    /**
     * Keeps the id of an import copy (or of before 2.0) just written to the target [folder], made from the vault of
     * [generation] (VaultStore.generation when it started); must not throw. Waited for before the next copy, as [onResult].
     */
    private val onImportCopy: suspend (folder: String, id: String, generation: Long) -> Unit = { _, _, _ -> },
) {
    private val requests = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** A change was saved since the last debounced run: the [BackupSchedule.ON_CHANGE] targets get a copy. */
    private val changed = AtomicBoolean(false)

    /** The targets [request] asked a copy for, made at the next debounced run. */
    private val asked: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** The targets whose copy was due while the vault was locked: made when the vault is next open. */
    private val pending: MutableSet<String> = ConcurrentHashMap.newKeySet()

    private val _replacing = MutableStateFlow(false)

    /** True while [replaceOldCopies] works (also after the screen that asked for it was recreated). */
    val replacing: StateFlow<Boolean> = _replacing.asStateFlow()

    private val outcome = Notice<AutoBackupResult>()

    /**
     * How a replacement that ran by itself (it was asked for while locked) went, until [replaceOutcomeShown]: it lives with
     * this object, not with a screen, so an activity re-created meanwhile still shows it, and once shown it is gone. With
     * several targets, one outcome for all: «replaced» only when every target that tried replaced its old copies.
     */
    val replaceOutcome: StateFlow<AutoBackupResult?> = outcome.value

    /**
     * The user has been told [shown], the outcome that was on screen. Only that one goes: an outcome that arrived between
     * showing it and closing it is still told (compare-and-set by identity, [Notice]).
     */
    fun replaceOutcomeShown(shown: AutoBackupResult) {
        outcome.shown(shown)
    }

    private val scheduled = Notice<String>()

    /**
     * 2.0, task 8 (design «Copias»: «se hace la copia y se dice»): what [runDue] did by the calendar
     * ([scheduledCopiesText]), until shown ([scheduledShown]). Dropped at every lock, so it is never shown after one.
     */
    val scheduledNotice: StateFlow<String?> = scheduled.value

    fun scheduledShown(shown: String) = scheduled.shown(shown)

    /** One copy at a time: the collector's, [runDue]'s, [replaceOldCopies]'s and the copies before importing never overlap. */
    private val lock = Mutex()

    init {
        scope.launch {
            // drop(1): the revision the store starts with is not a change.
            val changes = store.revision.drop(1).map { changed.set(true) }
            val due = merge(changes, requests).debounce(AUTO_BACKUP_DEBOUNCE_MS).map { true }
            // Each time the vault is opened (not each change saved while it is open: those wait for the debounce).
            val opened = store.state.map { it is VaultState.Unlocked }.distinctUntilChanged().filter { it }.map { false }
            // One collector: copies never overlap.
            merge(due, opened).collect { isDue -> if (isDue) runChanged() else runDue(now()) }
        }
        scope.launch {
            // A lock drops what the calendar's copies said: never shown after it.
            store.state.map { it is VaultState.Unlocked }.distinctUntilChanged().filter { !it }.collect {
                scheduled.value.value?.let(scheduled::shown)
            }
        }
    }

    /**
     * Makes a copy soon (after [AUTO_BACKUP_DEBOUNCE_MS]) even without a change, to the target of [folder] (a folder just
     * chosen, «Hacer una copia ahora»), whatever its calendar; with the vault locked it waits for the unlock.
     */
    fun request(folder: String) {
        asked += folder
        requests.tryEmit(Unit)
    }

    /**
     * The debounced run: the [BackupSchedule.ON_CHANGE] targets after a change, and the targets [request] asked for. A
     * target with an A6 request does it with this copy, as 1.x did.
     */
    private suspend fun runChanged() {
        val notices = ArrayList<AutoBackupResult>()
        lock.withLock {
            val change = changed.getAndSet(false)
            for (t in targets()) {
                val wanted = asked.remove(t.uri)
                if (!wanted && !(change && t.schedule == BackupSchedule.ON_CHANGE)) continue
                // Read inside the lock, from what is saved: a request made before a restart is done too.
                val replace = t.replacePending
                val result = attempt(t.uri, if (replace) Replace(t.replaceTargets, announce = true) else null) ?: continue
                if (replace) notices += result
            }
        }
        tell(notices)
    }

    /**
     * 2.0, task 8: called when the vault is unlocked (here) and when the app is opened (AppContainer): makes, one at a
     * time and in the order of the targets, the copies that are due at [now]: a target with a calendar when [due] says so,
     * an [BackupSchedule.ON_CHANGE] one when a change was missed, any one that waited for the unlock or has an A6 request.
     * With the vault locked, nothing is written: those copies wait for the unlock. A lock in the middle stops the copies
     * that follow. Returns once they are done; cancelled with this object's scope.
     */
    suspend fun runDue(now: Long) {
        val notices = ArrayList<AutoBackupResult>()
        var byCalendar = 0
        lock.withLock {
            for (t in targets()) {
                val replace = t.replacePending
                val calendar = t.schedule != BackupSchedule.ON_CHANGE && t.due(now, zone())
                val run = replace || t.uri in pending || calendar || (t.schedule == BackupSchedule.ON_CHANGE && missed(t))
                if (!run) continue
                // Locked: nothing is written, and nothing is kept as pending either: the unlock runs this again, from
                // what is saved then (the calendar, A6, a change missed), so a clock corrected meanwhile makes no copy.
                if (store.state.value !is VaultState.Unlocked) continue
                // Read inside the lock, from what is saved: a request made before a restart is done too.
                val result = attempt(t.uri, if (replace) Replace(t.replaceTargets, announce = true) else null) ?: continue
                if (replace) notices += result
                if (calendar && result.writtenAt != null && result.copies != null) byCalendar++
            }
        }
        tell(notices)
        if (byCalendar > 0 && store.state.value is VaultState.Unlocked) scheduled.post(scheduledCopiesText(byCalendar))
    }

    /**
     * M2: how the A6 requests that ran by themselves went ([notices], one per target that tried), told once: the success
     * when every one replaced its old copies (which ends the requests), or a failure not yet told.
     */
    private fun tell(notices: List<AutoBackupResult>) {
        if (notices.size == 1) {
            // One target: its own result, as 1.x told it.
            val only = notices.single()
            if (only.replaced || only.toldKey != null) outcome.post(only)
        } else if (notices.isNotEmpty()) {
            val told = notices.firstNotNullOfOrNull { it.toldKey }
            when {
                notices.all { it.replaced } -> outcome.post(combined(notices, null, replaced = true))
                told != null -> outcome.post(combined(notices, told, replaced = false))
            }
        }
    }

    /** One result for the dialog out of each target's: [error] its text, the copies left unlisted added up. */
    private fun combined(results: List<AutoBackupResult>, error: String?, replaced: Boolean) = AutoBackupResult(
        folder = results.first().folder,
        writtenAt = results.mapNotNull { it.writtenAt }.maxOrNull(),
        error = error,
        replaced = replaced,
        toldKey = error,
        unlistedLeft = results.sumOf { it.unlistedLeft },
    )

    /**
     * Makes a copy now in every target and, once each is written and checked, deletes that target's other copies (the
     * ids on its own lists, never a file by its name) and the leftover temporary ones. Returns one [AutoBackupResult] for
     * all ([AutoBackupResult.error] the first target's failure, null when all went well; [AutoBackupResult.unlistedLeft]
     * added up). If a target's new copy fails nothing is deleted there. With the vault locked (as it is right after a
     * change of master password) nothing can be copied yet: each target's request is given to [onResult] to be saved, and
     * it returns [AUTO_BACKUP_REPLACE_WAITS] ([AUTO_BACKUP_FAILED] if one could not be saved). With no target or no vault
     * it returns [AUTO_BACKUP_FAILED]. The work is not cancelled with the caller.
     *
     * [targets] are the copies to replace: the app passes those listed when the password changed ([MasterChanged]), so
     * neither the first request nor a «Volver a intentarlo» ever deletes a copy made after the change (m2 of the CR
     * re-review); each target only deletes those on its own lists. A pending request uses what it still has to delete.
     * Null (tests, and a request with no snapshot) means every copy listed now.
     */
    suspend fun replaceOldCopies(targets: List<String>? = null): AutoBackupResult = scope.async { replaceNow(targets) }.await()

    private suspend fun replaceNow(asked: List<String>?): AutoBackupResult = lock.withLock {
        _replacing.value = true
        try {
            val all = targets()
            if (all.isEmpty()) return@withLock AutoBackupResult("", null, AUTO_BACKUP_FAILED)
            val results = ArrayList<AutoBackupResult>()
            for (t in all) {
                results += when (store.state.value) {
                    is VaultState.Unlocked -> {
                        // A failure here is reported: asked while open, it is not retried behind the user's back (unless a
                        // request saved while locked is still pending: it goes on, with its own targets, and what it told
                        // starts again).
                        val ids = if (t.replacePending) t.replaceTargets ?: asked else asked
                        attempt(t.uri, Replace(ids, announce = false, newRequest = true))
                            ?: if (store.state.value is VaultState.Locked) waitForUnlock(t.uri, asked) else AutoBackupResult(t.uri, null, AUTO_BACKUP_FAILED)
                    }
                    is VaultState.Locked -> waitForUnlock(t.uri, asked)
                    else -> AutoBackupResult(t.uri, null, AUTO_BACKUP_FAILED)
                }
            }
            // One target: its own result, as 1.x gave it.
            if (results.size == 1) return@withLock results.single()
            val error = results.firstNotNullOfOrNull { it.error }
            AutoBackupResult(
                folder = all.first().uri,
                writtenAt = results.mapNotNull { it.writtenAt }.maxOrNull(),
                // «Se hará en cuanto abras la bóveda» only when every target waits; a real failure wins over it.
                error = results.firstNotNullOfOrNull { r -> r.error?.takeIf { it != AUTO_BACKUP_REPLACE_WAITS } } ?: error,
                unlistedLeft = results.sumOf { it.unlistedLeft },
            )
        } finally {
            _replacing.value = false
        }
    }

    /** The vault is locked: the request is saved (through [onResult]) and the first copy after the next unlock does it. */
    private suspend fun waitForUnlock(uri: String, asked: List<String>?): AutoBackupResult {
        defer(uri)
        // M4: the copies listed when the password changed ([asked]; or, without them, those listed now, which are the
        // same while the vault has stayed locked since) are the ones to replace; later ones follow the target's keep.
        onResult(AutoBackupResult(uri, null, AUTO_BACKUP_REPLACE_WAITS, replaceRequested = true, replaceTargets = asked ?: target(uri)?.copies.orEmpty()))
        // Not saved (a full disk): do not say it will be done.
        return AutoBackupResult(uri, null, if (target(uri)?.replacePending == true) AUTO_BACKUP_REPLACE_WAITS else AUTO_BACKUP_FAILED)
    }

    private fun zone(): ZoneId = zone ?: ZoneId.systemDefault()

    /** The target of [uri] as it is saved now; null once it is no longer one. */
    private fun target(uri: String): BackupTargetSettings? = targets().firstOrNull { it.uri == uri }

    /**
     * The copy before «Importar de otra app» (spec «Confirmar: la copia y el guardado»), with the vault as it is: the
     * same `seguridad-backup` copy, with the same master password, as the automatic ones ([VaultStore.exportBackup]),
     * named `seguridad-antes-de-importar-AAAAMMDD-HHMMSS.sgbak`, in the first target that takes it (2.0, task 8: in the
     * order of the targets; one that fails is skipped, a lock or a vault replaced stops). Written like them: under a
     * `.tmp` name and renamed once complete, or straight under its name when the provider cannot rename (deleted if that
     * fails), so the folder never holds a cut copy with the real name. Its id goes to [onImportCopy] (that target's list
     * of import copies, which A6 replaces after a change of master password); it is never rotated.
     *
     * Null when there is no target (the caller asks where to save it). Otherwise [CopyResult.Saved] with its name, or
     * [CopyResult.Failed] with a fixed text (the first target's): the folder unusable, the vault locked or replaced while
     * it was written (that copy is left unlisted), or the write failing. It runs in this class's scope and under its
     * lock, so it never overlaps an automatic copy or its rotation, and the caller leaving cannot cut it between the
     * rename and the list.
     */
    suspend fun copyBeforeImport(): CopyResult? = scope.async { lock.withLock { copyBeforeAny(IMPORT_COPY_PREFIX, store::exportBackup) } }.await()

    /**
     * 2.0: the copy before the vault passes to format v4 ([VaultStore.pendingUpgrade]), written as [copyBeforeImport]
     * and listed with the import copies, named `seguridad-antes-de-2-0-AAAAMMDD-HHMMSS.sgbak`. It is the file still on
     * disk ([VaultStore.exportBeforeUpgrade]), of version 2 or 3, with the marker 2: what earlier versions open. Once no
     * upgrade waits (review I-1) nothing is written: [CopyResult.Failed], and its temporary document is deleted. Null with
     * no target, so the store asks the user. The [CopyResult.Failed] texts are the import's: the store only tells a copy
     * made from one not made, and then the user chooses.
     */
    suspend fun copyBeforeUpgrade(): CopyResult? = scope.async { lock.withLock { copyBeforeAny(UPGRADE_COPY_PREFIX, store::exportBeforeUpgrade) } }.await()

    /** [copyBeforeNow] in each target in order until one is saved; a lock or a vault replaced ends it at once. */
    private suspend fun copyBeforeAny(prefix: String, export: suspend (OutputStream) -> Boolean): CopyResult? {
        var first: CopyResult? = null
        for (t in targets()) {
            val r = copyBeforeNow(t.uri, prefix, export)
            if (r is CopyResult.Saved) return r
            if (r is CopyResult.Failed && (r.message == IMPORT_COPY_LOCKED || r.message == IMPORT_COPY_VAULT_CHANGED)) return r
            if (first == null) first = r
        }
        return first
    }

    private suspend fun copyBeforeNow(uri: String, prefix: String, export: suspend (OutputStream) -> Boolean): CopyResult {
        return try {
            val target = open(uri) ?: return CopyResult.Failed(IMPORT_COPY_NO_FOLDER)
            if (store.state.value !is VaultState.Unlocked) return CopyResult.Failed(IMPORT_COPY_LOCKED)
            // As [attempt]: a copy whose vault was replaced while it was written is never listed as this vault's.
            val generation = store.generation
            // A lock meanwhile refuses the copy, and schedules no automatic one (nothing changed in the vault).
            val written = copyTo(uri, target, now(), prefix, defer = false, export = export) ?: return CopyResult.Failed(IMPORT_COPY_LOCKED)
            if (store.generation != generation) return CopyResult.Failed(IMPORT_COPY_VAULT_CHANGED)
            try {
                onImportCopy(uri, written.id, generation)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Not listed (a full disk): the copy is there all the same; A6 then counts it as unlisted, by its name.
            }
            CopyResult.Saved(written.name)
        } catch (e: CancellationException) {
            currentCoroutineContext().ensureActive()
            CopyResult.Failed(IMPORT_COPY_FAILED)
        } catch (e: Throwable) {
            CopyResult.Failed(
                when (e) {
                    is SecurityException -> IMPORT_COPY_NO_PERMISSION
                    is FileNotFoundException -> IMPORT_COPY_NO_FOLDER
                    is CryptoFailure -> IMPORT_COPY_VAULT_PROBLEM
                    is IOException -> IMPORT_COPY_WRITE_FAILED
                    else -> IMPORT_COPY_FAILED
                },
            )
        }
    }

    /** A change saved after [t]'s last copy (or no copy yet): one was due when the app was closed. */
    private fun missed(t: BackupTargetSettings): Boolean = try {
        val changed = store.lastChangeSavedAt()
        val last = t.lastAt
        changed != null && (last == null || changed > last)
    } catch (e: Exception) {
        false
    }

    /**
     * A replacement of the old copies: [targets] the ids to delete (null: every listed copy but the new one, as before
     * 1.3.0); [announce] when its failure may be told to the user ([AutoBackupResult.toldKey]); [newRequest] when it was
     * asked again just now.
     */
    private class Replace(val targets: List<String>?, val announce: Boolean, val newRequest: Boolean = false)

    /**
     * One copy to the target of [uri], then its rotation ([replace]: its targets go too, see [rotate]). The result it gave
     * [onResult], or null if nothing was done (no longer a target, its folder not usable, no vault, or the vault locked,
     * which defers it). Call with [lock] held.
     */
    private suspend fun attempt(uri: String, replace: Replace?): AutoBackupResult? {
        val replaceAll = replace != null
        pending -= uri
        if (target(uri) == null) return null
        val result = try {
            val folder = open(uri) ?: return null
            if (store.state.value !is VaultState.Unlocked) {
                defer(uri)
                return null
            }
            val at = now()
            // Read before the snapshot: if it is the same after the copy, no import, new vault or deletion came in between,
            // so the copy is of the vault the list belongs to. The vault's mutex only covers the snapshot, not the write
            // (a slow provider must not hold the vault), so an import can run while the copy is written (CADENAS-3).
            val generation = store.generation
            val written = copyTo(uri, folder, at) ?: return null
            if (store.generation != generation) {
                stale(uri, at, generation, replaceAll)
            } else {
                val owned = target(uri)?.copies.orEmpty()
                // A request without targets (saved before 1.3.0): every listed copy but the new one, as before (M4).
                // No import copy there: a request without targets was saved before 1.3.0, and every import copy (1.4.0)
                // was made after it, under the current password. So none from before the change exists, and
                // AUTO_BACKUP_REPLACED («y las de antes de importar hechas antes del cambio») stays true.
                val targets = replace?.let { it.targets ?: owned.filter { id -> id != written.id } }
                val rotation = try {
                    rotate(uri, folder, owned, written, targets, generation)
                } catch (e: Exception) {
                    // The copy is there all the same; nothing was deleted for sure, so every target is still one.
                    Rotation(AUTO_BACKUP_OLD_NOT_DELETED, owned + written.id, 0, targets, replaceDone = false, importsLeft = null)
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
                        importCopies = rotation.importsLeft,
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
        // R-6: a replacement promises the retry only while its request is saved (read now, after an import that may have
        // dropped it; one that failed puts it back, and then it is tried again).
        val said = if (replace != null && result.error == AUTO_BACKUP_OLD_NOT_DELETED && target(uri)?.replacePending != true) {
            result.copy(error = AUTO_BACKUP_OLD_NOT_DELETED_NO_RETRY)
        } else {
            result
        }
        val told = said.error?.takeIf { replace != null && replace.announce && !said.replaced && it !in target(uri)?.replaceTold.orEmpty() }
        val recorded = if (told != null) said.copy(toldKey = told) else said
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
        /** The import copies still listed and in the folder; null when unknown. */
        val importsLeft: List<String>? = null,
    )

    /** No vault: nothing to copy. A locked one: the copy of [uri] waits for it to be opened. */
    private fun defer(uri: String) {
        if (store.state.value !is VaultState.NoVault) pending += uri
    }

    /**
     * Writes the copy under its temporary name, then renames it; the copy, or null if there was nothing to copy.
     * If the provider cannot rename, the copy is written again straight under its name.
     */
    private suspend fun copyTo(
        uri: String,
        folder: BackupTarget,
        now: Long,
        prefix: String = AUTO_BACKUP_PREFIX,
        defer: Boolean = true,
        export: suspend (OutputStream) -> Boolean = store::exportBackup,
    ): BackupEntry? {
        val name = freeName(folder.list(prefix).mapTo(HashSet()) { it.name }, now, prefix)
        val temp = folder.create(name + TEMP_SUFFIX)
        if (temp.canRename) {
            if (!fill(uri, temp, defer, export)) return null
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
        return if (fill(uri, direct, defer, export)) BackupEntry(direct.id, name) else null
    }

    /**
     * Writes the copy to [file]; false if there was nothing to copy. On false or a failure, [file] is deleted. With
     * [defer], a vault locked meanwhile has an automatic copy made at the next unlock ([defer]).
     */
    private suspend fun fill(uri: String, file: BackupFile, defer: Boolean = true, export: suspend (OutputStream) -> Boolean = store::exportBackup): Boolean {
        try {
            var exported = false
            file.write { out -> exported = export(out) }
            if (!exported) {
                file.delete() // Locked or deleted meanwhile: no empty file left.
                if (defer) defer(uri)
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
    private fun freeName(taken: Set<String>, now: Long, prefix: String): String {
        var at = Instant.ofEpochMilli(now)
        while (true) {
            val name = prefix + STAMP.format(at.atZone(zone())) + SUFFIX
            if (name !in taken && name + TEMP_SUFFIX !in taken) return name
            at = at.plusSeconds(1)
        }
    }

    /**
     * Deletes the oldest of the target [uri]'s own copies beyond its keep, and leftover temporary ones; with [targets] (a
     * replacement), every copy among them too, and its keep applies to the others only (M4). Returns the error (null if
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
    private fun rotate(uri: String, folder: BackupTarget, owned: List<String>, newest: BackupEntry, targets: List<String>?, generation: Long): Rotation {
        val present = folder.list(COPIES_PREFIX)
        val ids = present.mapTo(HashSet()) { it.id }
        val keepN = target(uri)?.keep?.takeIf { it in AUTO_BACKUP_KEEP_CHOICES } ?: AUTO_BACKUP_KEEP_DEFAULT
        // The one just written always stays; a copy already gone is forgotten.
        val others = owned.filter { it in ids && it != newest.id }.distinct()
        val doomed = targets?.toHashSet() ?: emptySet()
        val old = others.filter { it in doomed }
        val rest = others.filter { it !in doomed }
        val surplus = rest.dropLast(keepN - 1)
        val leftovers = present.filter { TEMP_NAME.matches(it.name) || IMPORT_TEMP_NAME.matches(it.name) || UPGRADE_TEMP_NAME.matches(it.name) }.map { it.id }
        // Import copies: never rotated, only deleted as targets of a replacement (A6), by id from their own list.
        val listedImports = target(uri)?.importCopies.orEmpty()
        val imports = listedImports.filter { it in ids && it != newest.id }.distinct()
        val oldImports = imports.filter { it in doomed }
        val notDeleted = HashSet<String>()
        var failed = false
        var leftoverLeft = false
        for (id in old + oldImports + surplus + leftovers) {
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
        val importsLeft = imports.filter { it !in doomed || it in notDeleted }
        if (targets == null) return Rotation(if (failed) AUTO_BACKUP_OLD_NOT_DELETED else null, mine, 0, importsLeft = importsLeft)
        val ownedIds = owned.toHashSet()
        val importIds = listedImports.toHashSet()
        val unlisted = present.count {
            it.id != newest.id &&
                (COPY_NAME.matches(it.name) && it.id !in ownedIds || (IMPORT_COPY_NAME.matches(it.name) || UPGRADE_COPY_NAME.matches(it.name)) && it.id !in importIds)
        }
        val targetsLeft = (old + oldImports).filter { it in notDeleted }
        val done = targetsLeft.isEmpty() && !leftoverLeft
        // m2 (CR re-review): a replacement is said by its targets and leftovers only. Once they are all gone it is a
        // success, even if a copy beyond [keep] (made after the change, under the new password) could not be deleted:
        // that one stays listed ([mine]) and the next rotation tries it again, as any normal copy.
        return Rotation(if (done) null else AUTO_BACKUP_OLD_NOT_DELETED, mine, unlisted, targetsLeft, replaceDone = done, importsLeft = importsLeft)
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
 * [toldSinceRequest]: a failure of an attempt for this request was already told (the settings' told list is not empty).
 */
fun replaceStatus(
    requestedAt: Long,
    answered: Boolean,
    answerError: String?,
    pending: Boolean,
    lastError: String?,
    lastBackupAt: Long?,
    toldSinceRequest: Boolean = false,
): ReplaceStatus = when {
    requestedAt == 0L -> ReplaceStatus.NotAsked
    // T11-b: «waits» was true when the call answered; once the settings record the replacement (nothing waiting and a
    // copy written after the request), that record is what is shown.
    answered && answerError == AUTO_BACKUP_REPLACE_WAITS && !pending && lastBackupAt != null && lastBackupAt >= requestedAt ->
        lastError?.let { ReplaceStatus.Failed(it) } ?: ReplaceStatus.Done
    // CR-1, M1: a copy was written after the request (so the replacement ran) and the last attempt failed: the request
    // still waits to be retried, and the dialog says what happened (with «Volver a intentarlo») instead of «se hará en
    // cuanto abras la bóveda».
    // m3 (CR re-review): an attempt that wrote nothing (offline) leaves the date as it was; that a failure was told for
    // this request ([toldSinceRequest]: each target's replaceTold, emptied at each request) shows it ran too.
    pending && lastError != null && (toldSinceRequest || (lastBackupAt != null && lastBackupAt >= requestedAt)) -> ReplaceStatus.Failed(lastError)
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
