package app.seguridad.vault

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import app.seguridad.core.readAtMost
import app.seguridad.core.writeTextAtomically
import app.seguridad.crypto.CryptoFailure
import app.seguridad.crypto.KdfLock
import app.seguridad.crypto.KdfParams
import app.seguridad.crypto.randomBytes
import app.seguridad.imports.ApplyResult
import app.seguridad.imports.DuplicateChoice
import app.seguridad.imports.MergePlan
import app.seguridad.imports.mergedEntries
import app.seguridad.imports.escapedTextBytes
import app.seguridad.imports.utf8Length
import java.util.UUID

/**
 * Why the vault was last locked ([VaultStore.lastLockReason]): screens that hold their own secrets (as «PIN y códigos»)
 * treat a lock by hand differently from the idle one and from leaving the app. Not a secret, not saved.
 */
enum class LockReason {
    /** «Bloquear», a change of master password, an import, deleting the vault: everything that is not the two below. */
    MANUAL,

    /** [VaultStore.lockIfIdle]: unused for the chosen time. */
    IDLE,

    /** MainActivity.onStop: the user left the app. */
    LEFT_APP,
}

sealed interface VaultState {
    data object Loading : VaultState
    data object NoVault : VaultState
    data object Locked : VaultState
    /**
     * The open vault: [data] as stored (format v4; a vault of version 2 or 3 reads the same). [entries] are those not in
     * the trash, in the order stored, and [trash] the others.
     */
    data class Unlocked(val data: VaultData) : VaultState {
        val entries: List<VaultEntry> = data.live
        val trash: List<VaultEntry> = data.trash
    }
}

/** Shown instead of the 7-day notice when the clock gate refused the fingerprint (A11). */
const val CLOCK_CHANGED_TEXT = "La hora del móvil se ha atrasado. Por seguridad, escribe la contraseña maestra."

/** Shown when a fingerprint unlock fails for a reason the store has no text of its own for (LIBRE-4). */
const val BIOMETRIC_UNLOCK_FAILED = "No se pudo abrir la bóveda con la huella"

/**
 * Shown when the fingerprint released a key that does not authenticate this vault's entries: the blob it guards is
 * another vault's (the master password was changed or a copy imported, and turning the fingerprint off then failed or
 * was cut short), or the entries' blob is damaged. AES-GCM cannot tell those apart, so the text says «Puede deberse a»
 * and names the three causes, not one as a fact. The fingerprint is turned off at the same time
 * ([launchUnlockWithKey], CRY-A02). Reviewed by the Abogado Digital (final review of 1.3.1, O-1).
 */
const val FINGERPRINT_STALE =
    "La huella ya no abre esta bóveda. Puede deberse a un cambio de la contraseña maestra, a una copia importada o a " +
        "datos dañados. Entra con la contraseña maestra y vuelve a activarla si quieres."

/**
 * What the unlock screen shows when [VaultStore.unlockWithKey] throws [e]: the store's own Spanish texts (a stale key,
 * damaged or undecodable entries, the master password due, the clock gone back, a file this version cannot open) and a
 * fixed text for anything else, so an unexpected exception (in English, maybe with a path) is never shown as it is.
 */
fun biometricUnlockError(e: Exception): String = when (e) {
    is CryptoFailure, is MasterRequired, is StaleBiometricKey -> e.message ?: BIOMETRIC_UNLOCK_FAILED
    else -> BIOMETRIC_UNLOCK_FAILED
}

/**
 * Hands the key the fingerprint released to [store], also from a scope already cancelled (the prompt of a screen that
 * a rotation re-created may answer late): started atomically, so [VaultStore.unlockWithKey] always runs and either
 * keeps the key in the open vault or wipes it, never drops it (BOV-3). A cancellation is not reported.
 *
 * Only [StaleBiometricKey] turns the fingerprint off: the key does not authenticate the vault's entries under any
 * version the file may be, so the blob the fingerprint guards is another vault's (CRY-A02), or the entries' blob is
 * damaged, which AES-GCM cannot tell apart and the master password cannot open either. Then [onWrongKey] runs once (the
 * screen turns the fingerprint off; it is suspend, so that has ended before the text) and [onError] gets
 * [FINGERPRINT_STALE]. [onWrongKey] is not expected to throw, but if it does the text is shown all the same and the
 * exception does not reach the caller's scope. Nothing else reaches
 * [onWrongKey]: a key that authenticates but whose entries do not decode ([CryptoFailure], final review F1),
 * [MasterRequired] (the 7-day rule, the clock, or a file this version cannot open, whose key is never tried) and any
 * other failure go to [onError] with [biometricUnlockError], and the fingerprint stays. For a file that states its
 * version, the 7-day rule and the clock are looked at before the key is used ([VaultStore.unlockWithKey]): a stale key
 * is then only found at the first fingerprint unlock after the master password.
 *
 * Limit, as for any state of the screen (BOV-3): if the screen is rotated while the answer is on its way, [onWrongKey]
 * still turns the fingerprint off, but the new screen does not repeat [FINGERPRINT_STALE] (the old composition got it).
 */
@OptIn(DelicateCoroutinesApi::class) // ATOMIC: the body must run even on a cancelled scope, to own the key.
fun CoroutineScope.launchUnlockWithKey(
    store: VaultStore,
    key: ByteArray,
    onWrongKey: suspend () -> Unit = {},
    onError: (String) -> Unit,
): Job =
    launch(start = CoroutineStart.ATOMIC) {
        try {
            store.unlockWithKey(key)
        } catch (e: CancellationException) {
            throw e
        } catch (e: StaleBiometricKey) {
            try {
                onWrongKey()
            } catch (off: Exception) {
                // Defensive: an unexpected exception from onWrongKey must not crash the screen's scope nor hide the text below.
            }
            onError(FINGERPRINT_STALE)
        } catch (e: Exception) {
            onError(biometricUnlockError(e))
        }
    }

/**
 * The fingerprint released a key that does not authenticate this vault's entries ([VaultCrypto.authenticated]): a stale
 * fingerprint (CRY-A02). Not a [CryptoFailure], so that only this, and not entries that fail to decode, turns it off.
 */
class StaleBiometricKey : Exception(FINGERPRINT_STALE)

/**
 * Biometric unlock refused: the master password was not typed for [MASTER_RECHECK_MS], the clock went backwards, or
 * this version cannot open the file ([reason]); the message names the one reason. The last one carries the existing
 * [NOT_A_VAULT], never the 7-day text (the screen does not offer the fingerprint for such a file).
 */
class MasterRequired(val reason: BiometricRefusal = BiometricRefusal.MASTER_DUE) :
    Exception(
        when (reason) {
            BiometricRefusal.CLOCK -> CLOCK_CHANGED_TEXT
            BiometricRefusal.UNSUPPORTED -> NOT_A_VAULT
            BiometricRefusal.UPGRADE -> UPGRADE_NEEDS_MASTER
            BiometricRefusal.MASTER_DUE -> "Escribe tu contraseña maestra (se pide cada 7 días)"
        },
    )

/**
 * The password vault on disk (files/vault/vault.bin, never in Android backups) and its unlocked state.
 * The data key only lives in memory while unlocked and is wiped on [lock].
 */
class VaultStore(
    private val file: File,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val compute: CoroutineDispatcher = Dispatchers.Default,
    private val now: () -> Long = System::currentTimeMillis,
    /** Argon2id cost for whatever is wrapped next (new vault, new master password, upgrade); read each time. */
    private val kdf: () -> KdfParams = { KdfParams() },
    /** How long without use before it locks; 0 means never on time alone (it still locks on leaving the app). */
    private val idleLockMs: () -> Long = { 5 * 60_000L },
    /** Whether a picker or the QR scanner we opened keeps the vault open in the background ([VaultLock.keepOpenOnce]). */
    private val keptOpen: () -> Boolean = { VaultLock.keepOpenOnce },
    /** Whether, in split screen or a floating window, another app has the focus (AppContainer.awayInOtherApp, COV2-3). */
    private val awayInMultiWindow: () -> Boolean = { false },
    /** Whether the app is in use (AppContainer.foreground), recorded with each lock ([lastLockInForeground]). */
    private val inForeground: () -> Boolean = { true },
    /** Encrypted attachments, in files/vault/att/ next to the vault file. */
    private val attachments: AttachmentStore = AttachmentStore(File(file.absoluteFile.parentFile, "att")),
    /**
     * Runs before [create] writes a new vault (the app turns the automatic copies off there, so their rotation never
     * deletes the previous vault's copies). If it throws, nothing is created.
     */
    private val beforeCreate: suspend () -> Unit = {},
    /**
     * Runs once an import's copy is checked, just before the vault is saved: the app forgets, durably, which automatic
     * copies it made (they are the replaced vault's, and must never be rotated away by the imported one). If it throws,
     * nothing is replaced and the import fails with [IMPORT_SETTINGS_FAILED]. If the save then fails, its
     * [ReplacePrepared.undo] puts the settings back (COPIAS-3). It is durable first so that a process death after the
     * save never leaves the imported vault with the old vault's list; one before the save only leaves the old vault's
     * copies unlisted, which never deletes any.
     */
    private val beforeReplace: suspend () -> ReplacePrepared = { ReplacePrepared(false) {} },
    /** The highest time the app ever saw (a mark that only rises), kept in the settings; see [VaultCrypto.biometricAllowed]. */
    private val clockHighWater: () -> Long = { 0 },
    /** Told the time at every unlock and every [touch]; the app keeps the highest in the settings. */
    private val onClockSeen: (Long) -> Unit = {},
    /** Told the time after the master password was verified (unlock, [confirmMaster], import): the mark restarts there, even lower. */
    private val onClockReset: (Long) -> Unit = {},
    /**
     * 2.0, task 3: the highest time this vault was seen in use before an unlock, which the trash's clock rule reads
     * ([purgedTrash], focus 3): read once at the start of each unlock, before that unlock tells the time. [clockHighWater]
     * by default; the app passes its own, which the start of the app does not raise ([app.seguridad.AppContainer.trashClockMark]).
     */
    private val trashClockMark: () -> Long = clockHighWater,
    /**
     * A clock that only moves forward and keeps counting while the phone sleeps (Android: SystemClock.elapsedRealtime),
     * in ms: the idle lock and its backstop measure time without use with it, so a wall clock set back (by hand, NITZ
     * or NTP) never delays them (COV2-2). [now] stays for the dates and the A11 clock mark.
     */
    private val elapsed: () -> Long = { System.nanoTime() / 1_000_000 },
    /**
     * 2.0: runs after an unlock with the master password of a vault of version 2 or 3, with the vault already open and
     * its version-4 file only in memory, every save refused: the app makes the copy `seguridad-antes-de-2-0-…` in the
     * automatic copies' folder ([AutoBackup.copyBeforeUpgrade], with [exportBeforeUpgrade]). [CopyResult.Saved] when the
     * copy is made: then the v4 is saved and [upgradeDone] tells it once. Null (no folder) or [CopyResult.Failed] (the
     * copy failed; [upgradeAutoCopyFailed]) leaves the choice to the user ([pendingUpgrade], [completeUpgrade]). Not
     * cancelled with the caller; whatever it throws counts as a failed copy.
     */
    private val beforeUpgrade: suspend () -> CopyResult? = { null },
    /** Writes the vault file; only the tests change it (a write that dies half way, [upgradeInterruptedKeepsV3]). */
    private val writeFile: (File, String) -> Unit = { f, text -> f.writeTextAtomically(text) },
    /**
     * 2.0, task 2: this vault's local id ([VaultRegistry]): names its fingerprint key and its settings. Never in the file.
     */
    val id: String = PRIMARY_VAULT_ID,
    /**
     * 2.0, task 2: whether this is the vault the app has active ([app.seguridad.AppContainer.activeVault]). Only the
     * active one is ever opened: an unlock, an import or a new vault of another one (a screen that kept its store after a
     * switch) leaves it locked and wipes the key, so two vaults are never open at the same time. Read at the moment the
     * vault would open, under the same lock as [lock].
     */
    private val active: () -> Boolean = { true },
) {
    private val _state = MutableStateFlow<VaultState>(VaultState.Loading)
    val state: StateFlow<VaultState> = _state.asStateFlow()

    private val _revision = MutableStateFlow(0L)

    /**
     * Goes up by one after each change the user made is saved to disk (create, edit, attachment, new master
     * password, import); not for bookkeeping saves such as the last master check on unlock. [AutoBackup] follows it.
     */
    val revision: StateFlow<Long> = _revision.asStateFlow()

    private fun changed() {
        _revision.value += 1
    }

    @Volatile private var _generation = 0L

    /**
     * Which vault this is, in memory: goes up (with the mutex held) when [create] makes a new one, when an import is about
     * to replace it and when [destroy] deletes it; never with an edit. [AutoBackup] reads it before taking its snapshot
     * and again after writing it: a copy whose vault was replaced meanwhile is never listed as the current vault's, so
     * its rotation and A6 never delete the replaced vault's copies (CADENAS-3, COPIAS-1). Not saved: it only has to tell
     * apart what happens within one process.
     */
    val generation: Long get() = _generation

    /** Call with the mutex held (it is the only writer). */
    private fun nextGeneration() {
        _generation += 1
    }

    private val mutex = Mutex()
    private val _needsMaster = MutableStateFlow(false)

    /**
     * Whether the vault file is not of version 3 or 4 (it was written by 1.1): identity and contacts cannot be saved. In
     * 2.0 an open vault is always a 4, but for one of version 2 whose upgrade waits for the user ([pendingUpgrade]),
     * when nothing can be saved anyway. False with no vault. Follows the file the store holds, so it changes after
     * [load], [unlock], [completeUpgrade], an import, [changeMaster] and [destroy]; [lock] leaves it as it is (the file on
     * disk does not change by locking).
     */
    val needsMasterForContacts: StateFlow<Boolean> = _needsMaster.asStateFlow()

    /**
     * 2.0: true while the open vault is still of version 2 or 3 on disk, its version-4 file ready in memory, and the
     * user has to choose: export a copy first, or go on without one ([completeUpgrade] in both cases; Abogado Digital,
     * O-3). Only once there is a choice to make (review I-1): with no automatic copies' folder, or when their copy
     * ([beforeUpgrade]) or the save after it failed; never while that copy runs. From the unlock until the v4 is saved,
     * shown or not, the vault shows its entries, every save throws [IllegalStateException] with [UPGRADE_PENDING], and
     * [exportBackup] exports the file on disk, which earlier versions open. A [lock] drops the version 4 (false again):
     * the file stays as it was, and the next unlock with the master password asks again.
     */
    val pendingUpgrade: StateFlow<Boolean> get() = upgrades.asking

    /**
     * 2.0 (review M-5): whether, before [pendingUpgrade] asked, there was an automatic copies' folder and its copy before
     * the upgrade was not made; the dialog says so. Read when [pendingUpgrade] turns true.
     */
    val upgradeAutoCopyFailed: Boolean get() = upgrades.autoCopyFailed

    /**
     * 2.0 (O-3 with a folder): the name of the copy made before the upgrade (a file name, never a path), after the
     * upgrade was saved with no question asked, until [upgradeDoneShown]: the vault screen tells it once that earlier
     * versions no longer open the vault nor its new copies. In memory only: a process death before it is shown loses it.
     */
    val upgradeDone: StateFlow<String?> get() = upgrades.done

    /** The notice of [upgradeDone] was shown. */
    fun upgradeDoneShown() = upgrades.doneShown()

    private var vault: VaultFile? = null
        set(value) {
            field = value
            _needsMaster.value = needsMaster(value)
        }
    private var dek: ByteArray? = null

    /** [elapsed] at the last use; only read while [dek] is set ([opened] sets both). */
    private var lastUse = 0L

    /**
     * Bumped by every [lock]. [lock] runs on the main thread without the mutex (leaving the app must lock at once),
     * so operations work on their own copy of the key and only reopen the vault if no lock happened meanwhile.
     * Every save through [modifyData] reads it when it is asked for (its default `sinceLock`), before waiting for the
     * mutex, and is refused if a lock happened since, even if the vault was opened again meanwhile (2.0, task 7, re-review
     * 2, Info-1); the editor's «Guardar» gives the epoch of its tap ([upsert]) and [addAttachment] the one before it reads
     * its document. [create], [unlock] and [unlockWithKey] also read it before waiting for the mutex.
     */
    @Volatile private var epoch = 0

    /** [epoch], read only: goes up with every [lock], so a holder can tell a lock happened since it last looked. */
    val lockEpoch: Int get() = epoch

    /**
     * Held by [lock] and by the last step of [opened] (check [epoch], then keep the key and show the entries), which
     * may run on another thread (an import runs on Dispatchers.IO): a lock can never land between the two.
     */
    private val opening = Any()

    /**
     * 2.0: the upgrade to version 4 waiting in memory ([PendingUpgrade], review M-8): its file ([PendingUpgrade.file],
     * what [pendingUpgrade] waits to save) is set and dropped with [opening] held, and read under the mutex.
     */
    private val upgrades = PendingUpgrade(opening)

    /** The version-4 file waiting to be saved ([upgrades]); null when none waits. */
    private val upgrade: VaultFile? get() = upgrades.file

    suspend fun load() = mutex.withLock {
        if (_state.value != VaultState.Loading) return@withLock
        withContext(io) { removeImportLeftovers() }
        vault = withContext(io) {
            if (!file.exists()) return@withContext null
            try {
                VaultCrypto.decodeFile(file.readText())
            } catch (e: CryptoFailure) {
                keepAside() // Damaged: kept for recovery, never overwritten by a new vault.
                null
            } catch (e: java.io.IOException) {
                null // Unreadable right now: [create] still moves it aside before writing anything.
            }
        }
        _state.value = if (vault == null) VaultState.NoVault else VaultState.Locked
    }

    /**
     * Renames an existing vault file to vault.bin.bad-<time> instead of losing it, and its attachments folder to
     * att.bad-<time>: the new vault's garbage collection must not delete files only the old one can open.
     */
    private fun keepAside() {
        if (file.exists()) {
            val stamp = now()
            file.renameTo(File(file.path + ".bad-" + stamp))
            // If this rename fails, the new vault's collection deletes these files: no loss, as their keys are only
            // in the vault.bin.bad-<time> just set aside, which no one opens again without manual recovery.
            if (attachments.dir.exists()) attachments.dir.renameTo(File(attachments.dir.path + ".bad-" + stamp))
        }
    }

    /**
     * Deletes the attachment files no entry of the open vault refers to (a deleted entry, a removed attachment, a
     * crash between writing a file and saving the vault). Only with the vault open, from the entries just read or
     * saved, so a live file is never deleted. Called with the mutex held. Any failure is ignored: the vault is
     * already saved and the next collection tries again.
     */
    private suspend fun collectGarbage() {
        // Every entry, those in the trash too: their attachments go only when the trash is emptied.
        val entries = (_state.value as? VaultState.Unlocked)?.data?.entries ?: return
        val live = entries.flatMapTo(HashSet()) { e -> e.attachments.map { it.id } }
        try {
            withContext(io) { attachments.collectGarbage(live) }
        } catch (e: Exception) {
            // Never affects the vault.
        }
    }

    /**
     * Runs the Argon2id of [block] on [compute], one at a time in the process ([KdfLock], ruling R38: with the mutex
     * held, which is the lock order), and returns what it made even if the caller was cancelled meanwhile, so a key it
     * derives always reaches the code that wipes it (BOV-3): `withContext(compute)` alone, or with NonCancellable added
     * to it, drops the result when it switches back to a cancelled caller.
     */
    private suspend fun <T> derive(block: () -> T): T = KdfLock.derive(compute, block)

    private suspend fun save(v: VaultFile) = withContext(io) { writeFile(file, VaultCrypto.encodeFile(v)) }

    /**
     * [save] for bookkeeping only (the last master check, a format upgrade): the file keeps the time of the last
     * change the user made, which [lastChangeSavedAt] gives. If the time cannot be set back, the worst is one more copy.
     */
    private suspend fun saveBookkeeping(v: VaultFile) = withContext(io) {
        val changedAt = file.lastModified()
        writeFile(file, VaultCrypto.encodeFile(v))
        if (changedAt > 0) file.setLastModified(changedAt)
    }

    /**
     * When the last change the user made was saved to the vault file (its modification time, which bookkeeping
     * saves leave as it was); null with no file. [AutoBackup] compares it with its last copy when the vault is opened,
     * so a copy that was due when the app closed is still made.
     */
    fun lastChangeSavedAt(): Long? = file.lastModified().takeIf { it > 0 }

    /**
     * Shows [v] unlocked with [key], which the store now owns; if a [lock] happened since [since], it stays locked
     * and [key] is wiped instead. The entries are read before the key is kept, so a wrong key is never held.
     */
    /**
     * Writes [next] and makes it the vault in memory, as one step that leaving the screen cannot cut in half
     * (otherwise disk and memory could disagree, and the next save would write the old file and key back).
     * With [mustSave] false a failed write still opens the vault: [next] then only differs from the disk by
     * bookkeeping (the last master check, the format upgrade), which the next successful save writes out.
     */
    private suspend fun commit(next: VaultFile, key: ByteArray, since: Int, mustSave: Boolean = true, read: VaultData? = null) = withContext(NonCancellable) {
        try {
            if (mustSave) {
                save(next)
                changed()
            } else {
                saveBookkeeping(next)
            }
        } catch (e: Exception) {
            if (mustSave) {
                key.fill(0)
                throw e
            }
        }
        // 2.0, review I-2 of task 4: with no [read], the file is decrypted and decoded here, off the caller's thread.
        val data = read ?: if (epoch != since) null else try {
            withContext(compute) { VaultCrypto.read(next, key) }
        } catch (e: CryptoFailure) {
            key.fill(0)
            throw e
        }
        opened(next, key, since, read = data)
        // After a failed save that did not have to succeed, the disk differs only by bookkeeping: same attachments.
        collectGarbage()
    }

    /**
     * [upgraded] is the version-4 file of a vault of version 2 or 3 that [v] still is on disk ([pendingUpgrade]): its data
     * is what opens (same data key), and it waits for [completeUpgrade]; null for any other opening, which ends a pending
     * upgrade (an import replaced the vault).
     */
    private fun opened(v: VaultFile, key: ByteArray, since: Int, upgraded: VaultFile? = null, read: VaultData? = null) {
        vault = v
        val data = if (epoch != since) null else try {
            // [read]: what [v] holds, already decrypted by the caller (review M-5: the fingerprint's unlock reads it once).
            (read ?: VaultCrypto.read(upgraded ?: v, key)).let { d -> d.copy(entries = d.entries.sortedBy { it.title.lowercase() }) }
        } catch (e: CryptoFailure) {
            key.fill(0)
            throw e
        }
        synchronized(opening) {
            if (data == null || epoch != since || !active()) {
                upgrades.drop()
                key.fill(0)
                // [v] is the vault in memory, and on disk: even an import from «Crea tu bóveda» cut by leaving the app
                // shows it locked, never «Crea tu bóveda» again (which would refuse a new import and set it aside, BOV-2).
                _state.value = VaultState.Locked
                return
            }
            if (dek !== key) dek?.fill(0)
            dek = key
            lastUse = elapsed()
            onClockSeen(now())
            // Shown only once there is a choice to make ([unlock], review I-1); an import that replaced the vault ends it.
            upgrades.openedWith(upgraded)
            _state.value = VaultState.Unlocked(data)
        }
    }

    /**
     * Creates a new vault; only with none readable (the «Crea tu bóveda» screen), else [IllegalStateException] and
     * nothing changes. Once Argon2id has run, a cancelled caller does not stop it: the vault is created (BOV-3).
     */
    suspend fun create(master: CharArray, name: String = "") {
        val since = epoch
        mutex.withLock { withContext(NonCancellable) { createLocked(master, since, name) } }
    }

    private suspend fun createLocked(master: CharArray, since: Int, name: String) {
        // Never over a readable vault (an import that finished while the screen still offered to create one): it would
        // be set aside as if damaged (BOV-2).
        if (vault != null) {
            if (_state.value is VaultState.NoVault) _state.value = VaultState.Locked
            throw IllegalStateException("A vault already exists")
        }
        val (v, key) = derive { VaultCrypto.create(master, now(), kdf(), name = name) }
        nextGeneration() // Before anything is turned off or written: a copy still being written is not this vault's.
        try {
            beforeCreate()
        } catch (e: Exception) {
            key.fill(0)
            throw e // Nothing moved or written: still no vault.
        }
        withContext(io) { keepAside() } // Only reached with no readable vault: whatever file is there is kept.
        _state.value = VaultState.Locked
        try {
            commit(v, key, since)
        } catch (e: Exception) {
            _state.value = VaultState.NoVault // Nothing was written: back to creating one.
            throw e
        }
    }

    /**
     * Throws [CryptoFailure] for a wrong password. Once it has the mutex it runs to the end even if the caller is
     * cancelled (a rotation during Argon2id): the key it derives is kept by the open vault or wiped, never left
     * behind (BOV-3); a lock meanwhile still leaves it locked.
     */
    suspend fun unlock(master: CharArray) {
        val since = epoch
        val upgrading = mutex.withLock { withContext(NonCancellable) { unlockLocked(master, since) } }
        if (!upgrading) return
        // 2.0: a vault of version 2 or 3 is open, its version 4 in memory. The master password is no longer needed (the
        // key is wrapped again already): wiped now, not after the copy (review M-1). The caller wipes it too.
        master.fill('\u0000')
        // With the mutex free, so the copy can export the file still on disk; not cut by the caller leaving (the copy
        // runs in AutoBackup's scope anyway). Then there is a choice to make (review I-1), unless the vault locked
        // meanwhile, which dropped the upgrade.
        upgrades.afterUnlock(copyBefore = beforeUpgrade, save = { completeUpgradeLocked(since) }, stillOpen = { epoch == since })
    }

    /** True when it opened a vault of version 2 or 3 whose version 4 waits in memory (not saved yet). */
    private suspend fun unlockLocked(master: CharArray, since: Int): Boolean {
        val v = vault ?: return false
        // 2.0, task 3: the time and the trash's mark of before this unlock, read before it tells the time to anyone.
        val at = now()
        val mark = trashClockMark()
        val key = derive { VaultCrypto.unlock(v, master) }
        val checked = try {
            derive { VaultCrypto.afterMasterUnlock(v, key, master, at, kdf(), prepare = { it.purgedTrash(at, mark) }) }
        } catch (e: Exception) {
            key.fill(0)
            throw e
        }
        // The master password was right: a clock set forward no longer blocks this vault's fingerprint. 2.0 (review I-1):
        // only for the active vault, which is the only one this unlock can open.
        if (active()) onClockReset(now())
        if (v.version == VaultFile.VERSION) {
            // A full disk must not lock the user out of their passwords: open anyway (see [commit]).
            commit(checked, key, since, mustSave = false)
            return false
        }
        // 2.0, the upgrade: nothing is written yet. The file on disk stays the 2 or 3 until the copy before it is made or
        // declined; a process death meanwhile leaves it as it was (focus 1).
        // Review I-2 of task 4: the version 4 waiting is decrypted off the caller's thread, as in [commit].
        val read = try {
            withContext(compute) { VaultCrypto.read(checked, key) }
        } catch (e: CryptoFailure) {
            key.fill(0)
            throw e
        }
        opened(v, key, since, upgraded = checked, read = read)
        collectGarbage()
        return upgrade != null
    }

    /**
     * 2.0: saves the version-4 file waiting since the unlock, after the copy before it was made or declined (Abogado
     * Digital, O-3). True when it was saved; false when there was nothing to save (no upgrade waiting: the vault locked
     * meanwhile, or it is done) or the write failed: then the file on disk is still the 2 or 3 of before, which opens
     * with the master password, and the choice stays ([pendingUpgrade]). A bookkeeping save ([saveBookkeeping]): the data does not change,
     * so no automatic copy is due. Not cancelled with the caller.
     */
    suspend fun completeUpgrade(): Boolean = completeUpgradeLocked(null)

    /** [completeUpgrade]; with [since], only if no lock happened since that epoch (the automatic path of [unlock]). */
    private suspend fun completeUpgradeLocked(since: Int?): Boolean = mutex.withLock {
        withContext(NonCancellable) {
            val next = upgrade
            if (next == null || since != null && epoch != since) return@withContext false
            try {
                saveBookkeeping(next)
            } catch (e: Exception) {
                return@withContext false // The file on disk is the one of before: the next try, or the next unlock.
            }
            synchronized(opening) {
                // Saved: from here the file is the 4, also if a lock came after the save.
                vault = next
                upgrades.saved()
            }
            true
        }
    }

    val biometricAllowed: Boolean get() = biometricRefusal == null && vault != null

    /** Why the fingerprint is refused now (null when allowed or there is no vault). */
    val biometricRefusal: BiometricRefusal? get() = vault?.let { VaultCrypto.biometricRefusal(it, now(), clockHighWater()) }

    /**
     * With a data key released by the biometric prompt, which the store owns from here: it ends in the open vault or
     * wiped, also when the caller is cancelled while it waits for the mutex (BOV-3). In this order:
     * 1. [MasterRequired] with [BiometricRefusal.UNSUPPORTED] for a file this version cannot open, without trying the
     *    key;
     * 2. [MasterRequired] with [BiometricRefusal.UPGRADE] for a file of version 2 or 3, or without a version (2.0: it
     *    passes to 4 with the master password), without trying the key either;
     * 3. [MasterRequired] when the 7-day recheck is due or the clock went back, before the key is used at all;
     * 4. [StaleBiometricKey] when the key does not authenticate the entries;
     * 5. [CryptoFailure] when the entries authenticate but do not decode.
     */
    suspend fun unlockWithKey(key: ByteArray) {
        val since = epoch
        try {
            mutex.withLock { withContext(NonCancellable) { unlockWithKeyLocked(key, since) } }
        } catch (e: Throwable) {
            synchronized(opening) { if (dek !== key) key.fill(0) }
            throw e
        }
    }

    private suspend fun unlockWithKeyLocked(key: ByteArray, since: Int) {
        val v = vault ?: return key.fill(0)
        // 2.0, task 3: read before this unlock tells the time (see [unlockLocked]).
        val at = now()
        val mark = trashClockMark()
        // A file this version cannot open: the key is not tried, so it is never taken for a stale one.
        if (!VaultCrypto.opens(v)) {
            key.fill(0)
            throw MasterRequired(BiometricRefusal.UNSUPPORTED)
        }
        // The access rule first (2.0, a file to upgrade; then the 7 days and the clock), so the fingerprint key decrypts
        // nothing when it refuses (as in 1.3.0). Only a version-4 file gets past it, and a 4 always states its version.
        VaultCrypto.biometricRefusal(v, now(), clockHighWater())?.let { refusal ->
            key.fill(0)
            throw MasterRequired(refusal)
        }
        // Whether the key authenticates the entries (AES-GCM only): only that failure is a stale fingerprint (F1).
        val known = VaultCrypto.authenticated(v, key) ?: run {
            key.fill(0)
            throw StaleBiometricKey()
        }
        // A CryptoFailure here is entries that authenticate but do not decode, and keeps the fingerprint.
        // 2.0, task 3: the unlock is recorded and the trash emptied of what is due ([purgedTrash]), as a bookkeeping save:
        // a full disk never keeps the vault closed ([commit] with mustSave false). Its attachments go with the collection.
        // Decrypted once (review M-5): what is written is what opens.
        val data = withContext(compute) { VaultCrypto.read(known, key).purgedTrash(at, mark) }
        val next = withContext(compute) { VaultCrypto.write(known, key, data) }
        commit(next, key, since, mustSave = false, read = data)
    }

    /**
     * Checks [master] against the open vault, which stays as it is (2.0: an open vault is a 4, or a 2 or a 3 whose
     * upgrade waits for the user, [pendingUpgrade], which only [completeUpgrade] saves). A wrong password throws what
     * [unlock] throws ([CryptoFailure]) and touches neither the file nor the open vault. Returns false, having checked
     * nothing, when there is no vault or it is locked ([unlock] is the way in; the caller must not go on to save), and
     * true when the password is right. Like [unlock], it leaves [master] to the caller to wipe. A vault whose Argon2id
     * is cheaper than the profile chosen in Ajustes is not wrapped again here (CR-3): that second Argon2id would delay
     * the save the user is waiting for, and the next [unlock] with the master password does it
     * ([VaultCrypto.afterMasterUnlock]).
     */
    suspend fun confirmMaster(master: CharArray): Boolean = mutex.withLock { withContext(NonCancellable) { confirmMasterLocked(master) } }

    /** [confirmMaster] with the mutex held; not cancellable, as [unlock] (BOV-3). */
    private suspend fun confirmMasterLocked(master: CharArray): Boolean {
        val v = vault ?: return false
        if (dek == null || _state.value !is VaultState.Unlocked) return false
        val key = derive { VaultCrypto.unlock(v, master) }
        onClockReset(now())
        key.fill(0) // Only the check.
        return true
    }

    /** A copy of the open data key, to wrap it with the biometric key; null while locked. */
    fun dataKeyCopy(): ByteArray? = dek?.copyOf()

    /** Why the last lock from open to locked happened; set before the state changes, so a collector of [state] reads its own. */
    @Volatile var lastLockReason: LockReason = LockReason.MANUAL
        private set

    /** Whether the app was in use at that lock (read at the lock, not when a collector gets to it). */
    @Volatile var lastLockInForeground: Boolean = true
        private set

    fun lock(reason: LockReason = LockReason.MANUAL) = synchronized(opening) {
        epoch++
        dek?.fill(0)
        dek = null
        // 2.0: an upgrade not saved yet is dropped; the file stays the 2 or 3 it is.
        upgrades.drop()
        if (vault != null && _state.value is VaultState.Unlocked) {
            lastLockReason = reason
            lastLockInForeground = inForeground()
            _state.value = VaultState.Locked
        }
    }

    /**
     * 2.0, task 2: returns once whatever holds the mutex now (an unlock, a save, an import) has ended; a switch to another
     * vault calls it after [lock], so nothing of this vault is still being written when the other one is shown.
     */
    suspend fun awaitIdle() = mutex.withLock { }

    fun touch() {
        lastUse = elapsed()
        onClockSeen(now())
    }

    /**
     * Another app takes the focus in multi-window (COV2-3). With «Al salir» the time away starts now, as before a picker
     * ([backstopFromLeaving], M1 of the F1 review): its [AWAY_BACKSTOP_MS] count from the app switch, not from the last
     * vault screen touched. A time chosen in Ajustes keeps counting from the real last use.
     */
    fun leftForAnotherWindow() {
        if (backstopFromLeaving(idleLockMs())) touch()
    }

    /**
     * Locks when unused for [idleLockMs]; returns true if it locked. 0 («Al salir») means no time lock while the
     * user is in the app, but it locks after [AWAY_BACKSTOP_MS] without use (see [idleLimitMs]) while:
     * - a picker or the QR scanner keeps the vault open in the background ([keptOpen]: MainActivity.onStop does not
     *   lock then), so Home or screen-off from there never leaves it open for hours;
     * - in split screen or a floating window, another app has the focus ([awayInMultiWindow]): Seguridad is still
     *   visible, so onStop never comes (COV2-3).
     */
    fun lockIfIdle(): Boolean {
        val limit = idleLimitMs(idleLockMs(), keptOpen(), awayInMultiWindow())
        if (limit > 0 && dek != null && elapsed() - lastUse >= limit) {
            lock(LockReason.IDLE)
            return true
        }
        return false
    }

    /**
     * [modifyData] on the entries not in the trash: [change] gets them and gives the new list, and the trash and the
     * rest of the vault's data are kept as they are.
     */
    private suspend fun modify(
        accept: suspend (VaultFile) -> Boolean = { true },
        sinceLock: Int = epoch,
        change: suspend (List<VaultEntry>) -> List<VaultEntry>?,
    ): Boolean = modifyData(accept, sinceLock = sinceLock) { data -> change(data.live)?.let { data.copy(entries = it + data.trash) } }

    /**
     * Saves the change even if the vault locks meanwhile (with a copy of the key), then reopens only if it did not.
     * False if the vault was already locked, or [change] returned null (nothing to change), so nothing was saved.
     * [change] runs with the mutex held: it must never wait on anything outside the app (a document provider).
     * What it gives is fitted to the limits ([fittedForSave]: the oldest history goes first) and a vault that still
     * does not fit throws [VaultLimitExceeded], with nothing saved. [accept] sees the file about to be saved, still with
     * the mutex held, and false refuses it (nothing saved, false). 2.0: while [pendingUpgrade], throws
     * [IllegalStateException] with [UPGRADE_PENDING] and saves nothing. 2.0, task 4: with [bookkeeping] the save is
     * like the last master check's ([commit] with `mustSave` false: the file keeps the time of the last change, [revision]
     * does not go up, a failed write leaves the change in memory only), and while the upgrade waits it saves nothing and
     * gives false instead of throwing.
     */
    private suspend fun modifyData(
        accept: suspend (VaultFile) -> Boolean = { true },
        bookkeeping: Boolean = false,
        sinceLock: Int = epoch,
        change: suspend (VaultData) -> VaultData?,
    ): Boolean = mutex.withLock {
        // 2.0, task 4: bookkeeping ([markUsed]) is no change the user made: while the upgrade waits it is only skipped.
        if (bookkeeping && upgrade != null) return@withLock false
        check(upgrade == null) { UPGRADE_PENDING }
        val v = vault ?: return@withLock false
        val since = epoch
        // 2.0, re-reviews 1 and 2 of task 7: a change asked for before a lock ([sinceLock], read when it was asked for) is
        // not written after it, even if the vault was opened again while it waited for the mutex (decision (b) of 1.4.0).
        if (since != sinceLock) return@withLock false
        val key = dek?.copyOf() ?: return@withLock false
        val current = (_state.value as? VaultState.Unlocked)?.data ?: return@withLock false.also { key.fill(0) }
        // 2.0, review I-2 of task 4: what is written is what opens (as the fingerprint's unlock, review M-5 of task 1): the
        // data saved goes to [commit] with the file, so the vault is never decrypted and decoded again on the caller's
        // thread (the main one for a tap) after each save, nor for [markUsed].
        var saved: VaultData? = null
        val next = try {
            withContext(compute) {
                change(current)?.fittedForSave(current)?.withPlainVersions()?.let { data ->
                    saved = data
                    VaultCrypto.write(v, key, data)
                }?.takeIf { accept(it) }
            }
        } catch (e: Exception) {
            key.fill(0)
            throw e
        }
        if (next == null) {
            key.fill(0)
            return@withLock false
        }
        // With [bookkeeping], a save that keeps the file's time and does not raise [revision] (no automatic copy for it).
        commit(next, key, since, mustSave = !bookkeeping, read = saved)
        true
    }

    /**
     * Adds or replaces [entry]. Throws [IllegalStateException] (nothing is saved) for an [EntryType.IDENTITY] when the
     * vault has another one, and for an identity or contact while the vault is still version 2 (see
     * [needsMasterForContacts]). Identities and contacts never keep attachments or history. 2.0, task 3: a change keeps
     * the entry as it was in its history ([withVersion]), and a new LOGIN password puts the old one in the list of the 5
     * newest ([withNewPassword], ruling T3 I-2); the history, that list and the attachments are the store's to keep,
     * whatever the caller sends. False, with nothing saved, for an id that is in the trash (a screen left open on it): it
     * is restored with [restore], never overwritten ([saveRefusedText] tells the editor why). With [sinceLock] (the
     * [lockEpoch] of the editor's «Guardar»), false with nothing saved when the vault locked since, also if it was
     * opened again meanwhile.
     */
    suspend fun upsert(entry: VaultEntry, sinceLock: Int? = null) = modifyData(sinceLock = sinceLock ?: epoch) { data ->
        if (data.entries.any { it.id == entry.id && it.deletedAt != null }) return@modifyData null
        val list = data.live
        checkCanHold(list, listOf(entry))
        val saved = list.firstOrNull { it.id == entry.id }
        val next = if (entry.type.isPersonal) {
            // No passwords, history or attachments of their own: whatever the caller sends is dropped. 2.0, task 4: no
            // folder either (they have their own tab).
            entry.copy(passwordHistory = emptyList(), attachments = emptyList(), history = emptyList(), deletedAt = null, folderId = null)
        } else if (saved == null) {
            // 2.0, task 4: a new entry may start in a folder of the vault; any other folder id is dropped.
            val folder = entry.folderId?.takeIf { id -> data.folders.any { it.id == id } }
            // 2.0, task 5 (review M-1): a new password is set now.
            entry.copy(
                passwordHistory = emptyList(), attachments = emptyList(), history = emptyList(), deletedAt = null, folderId = folder,
                passwordChangedAt = if (entry.passwordForHealth().isEmpty()) null else now(),
            )
        } else {
            // The attachments too: only [addAttachment] and [removeAttachment] change them, so an editor that does
            // not send them never drops one (and the collection after the save never deletes its file).
            // A type change never files a passphrase or another secret as an old password: the list stays as it was.
            val passwords = if (saved.type == EntryType.LOGIN && entry.type == EntryType.LOGIN) {
                saved.withNewPassword(entry.password, now()).passwordHistory
            } else {
                saved.passwordHistory
            }
            // 2.0, task 4: the folder and the last use too ([moveEntries] and [markUsed] change them, not the editor).
            entry.copy(
                history = saved.history, passwordHistory = passwords, attachments = saved.attachments, deletedAt = null,
                folderId = saved.folderId, lastUsedAt = saved.lastUsedAt,
            ).withVersion(saved, now())
        }
        val entries = if (saved == null) list + next else list.map { if (it.id == entry.id) next else it }
        data.copy(entries = entries + data.trash)
    }

    /**
     * 2.0, task 5 (design «Duplicar»): a new entry «<título> (copia)» with everything of entry [id] but its attachments,
     * its history and its last use: a new id, made and saved now (in its folder, with its tags, fields, notes and dates).
     * Null, with nothing saved, when [id] is not in the vault (or is in the trash), is the identity or a contact, or the
     * vault is locked. A copy that does not fit throws [VaultLimitExceeded], as any save.
     */
    suspend fun duplicate(id: String): VaultEntry? {
        var made: VaultEntry? = null
        val saved = modifyData { data ->
            made = null
            val source = data.live.firstOrNull { it.id == id && !it.type.isPersonal } ?: return@modifyData null
            val at = now()
            val copy = source.copy(
                id = UUID.randomUUID().toString(), title = duplicateTitle(source.title), createdAt = at, updatedAt = at,
                history = emptyList(), passwordHistory = emptyList(), attachments = emptyList(), lastUsedAt = null, deletedAt = null,
                // Review M-1: the same password as old as it was, not new.
                passwordChangedAt = if (source.passwordForHealth().isEmpty()) null else lastPasswordChange(source),
            )
            made = copy
            data.copy(entries = data.live + copy + data.trash)
        }
        return made.takeIf { saved }
    }

    /**
     * Adds new entries with a single write, all or none (a Google Authenticator batch). An id already in the vault is
     * left as it is, and new entries start without password history, as with [upsert].
     */
    suspend fun addAll(entries: List<VaultEntry>) = modifyData { data ->
        // The ids of the trash too: an entry there is never shadowed by a new one with its id.
        val ids = data.entries.mapTo(HashSet()) { it.id }
        val added = entries.filter { ids.add(it.id) }
        checkCanHold(data.live, added)
        data.copy(entries = data.live + added.map { it.copy(passwordHistory = emptyList(), attachments = emptyList(), history = emptyList(), deletedAt = null) } + data.trash)
    }

    /**
     * The open vault's entries and [revision], read together under the mutex (a save raises the revision just before it
     * shows the new entries, so reading the two apart could pair an old list with a newer revision); null when locked.
     * The only source of an import's plan: the import screen passes it to [app.seguridad.imports.planMerge] (keeping its
     * entries for the preview's estimate), and [app.seguridad.imports.planImport] does the same for the tests.
     */
    suspend fun importSnapshot(): VaultSnapshot? = mutex.withLock {
        val entries = (_state.value as? VaultState.Unlocked)?.entries ?: return@withLock null
        VaultSnapshot(entries, _revision.value)
    }

    /**
     * Saves the import of [plan] (spec «Confirmar: la copia y el guardado»): [mergedEntries] against the vault as it is,
     * in a single write through [modify], all or nothing (the atomic rename of [save]; once the write starts, neither a
     * lock nor leaving the screen cuts it, see [commit]). Inside the mutex, in the same critical section as the write:
     * - [ApplyResult.Stale] when [revision] is no longer the plan's (another change was saved since the preview; owner's
     *   decision (b)), or the vault is locked, or [sinceLock] is given and [lockEpoch] is no longer it (ruling R66, R37:
     *   the screen gives the epoch of when saving started, so a lock during the copy before importing cancels the
     *   import even if the vault is opened again before this runs; the copy, if made, stays);
     * - [ApplyResult.TooBig] when the vault would no longer fit in a `.sgbak` ([writeBackup]'s limits, owner's decision
     *   (a)): the attachments plus two over [BACKUP_MAX_ENTRIES] (counted first, an import adds none), the JSON bytes of
     *   the entries' texts alone ([escapedTextBytes], counted without building anything) over the cap of the copy the
     *   vault would get ([backupMaxEntryBytes]: 33 MiB with marker 3, ruling T1-2) in Base64 (before anything is encoded
     *   or encrypted), its data over [VaultLimits.VAULT_MAX_DATA_BYTES] once [fitted] (2.0), and then the exact encrypted
     *   file over that cap or the whole copy over [BACKUP_MAX_TOTAL_BYTES];
     * - [ApplyResult.EntryTooBig] (2.0) when an entry the import changes would pass the limits of one entry: «Reemplazar»
     *   joining two notes past 64 KiB (an item too big by itself is already out of the plan, ruling T1-3).
     * Nothing is written in any of these cases, and none of the vault's limits ([fittedForSave]) is left to throw. The data key does not change, so the fingerprint stays on (A11 and CRY-A02:
     * no clock reset, as the mark only rises, as with any save; no biometric call). A failed write throws, as [modify] does, with nothing saved. A plan
     * that changes nothing (every item skipped) is [ApplyResult.Done] with zeros and writes nothing.
     * Accepted cost (ruling R69, code-review 5): the vault is encoded twice under the mutex ([accept]'s exact size check
     * and the write), bounded by the copy's cap and only on «Importar»; so is the second [fitted] of the data.
     */
    suspend fun applyImport(plan: MergePlan, default: DuplicateChoice, overrides: Map<Int, DuplicateChoice>, sinceLock: Int? = null): ApplyResult {
        var result: ApplyResult = ApplyResult.Stale
        var attachmentRefs: List<AttachmentRef> = emptyList()
        // The copy this vault would get (ruling T1-2): marker 3 for a 4, the only kind that saves an import.
        var cap = backupMaxEntryBytes(VaultFile.VERSION)
        modify(
            change = change@{ list ->
                if (_revision.value != plan.revision) return@change null
                // Ruling R66 (R37): a lock since saving started cancels the import, even if the vault was opened again.
                if (sinceLock != null && epoch != sinceLock) return@change null
                // Every attachment the copy would hold, those of the trash too.
                attachmentRefs = ((_state.value as? VaultState.Unlocked)?.data?.entries ?: list).flatMap { it.attachments }
                val overFiles = attachmentRefs.size + 2 - BACKUP_MAX_ENTRIES
                if (overFiles > 0) {
                    result = ApplyResult.TooBig(overBytes = 0, overFiles = overFiles)
                    return@change null
                }
                cap = backupMaxEntryBytes(vault?.version ?: VaultFile.VERSION)
                val merged = mergedEntries(plan, default, overrides, list, now(), { UUID.randomUUID().toString() })
                val low = (escapedTextBytes(merged) * 4 + 2) / 3
                if (low > cap) {
                    result = ApplyResult.TooBig(overBytes = low - cap)
                    return@change null
                }
                val changed = merged.filterIndexed { i, e -> i >= list.size || e !== list[i] }
                result = ApplyResult.Done(added = merged.size - list.size, replaced = changed.size - (merged.size - list.size))
                if (changed.isEmpty()) return@change null // Everything skipped: nothing to save, no new copy.
                // 2.0: the vault's own limits, each with a result of its own, never a failed save (ruling T1-3).
                if (changed.any { it.entryLimitProblem() != null }) {
                    result = ApplyResult.EntryTooBig
                    return@change null
                }
                val data = (_state.value as? VaultState.Unlocked)?.data ?: VaultData(entries = list)
                val size = jsonBytes(data.copy(entries = merged + data.trash).fitted())
                if (size > VaultLimits.VAULT_MAX_DATA_BYTES) {
                    result = ApplyResult.TooBig(overBytes = size - VaultLimits.VAULT_MAX_DATA_BYTES)
                    return@change null
                }
                checkCanHold(list, changed)
                merged
            },
            accept = { next ->
                val json = utf8Length(VaultCrypto.encodeFile(next))
                val files = withContext(io) { attachmentRefs.sumOf { attachments.fileOf(it.id).length() } }
                val over = maxOf(json - cap, BACKUP_MARKER_BYTES + json + files - BACKUP_MAX_TOTAL_BYTES)
                if (over > 0) result = ApplyResult.TooBig(overBytes = over)
                over <= 0
            },
        )
        // Stale unless [change] ran on the plan's vault; then what it, or [accept], decided.
        return result
    }

    /**
     * Called with the mutex held, before anything is written, for the entries [incoming] about to join [current]:
     * no identity or contact in a vault that is not version 3 yet, and no second [EntryType.IDENTITY] (the same
     * entry edited, with its own id, is fine). [VaultCrypto.write] checks the version once more.
     */
    private fun checkCanHold(current: List<VaultEntry>, incoming: List<VaultEntry>) {
        check(!(needsMaster(vault) && incoming.any { it.type.isPersonal })) { NEEDS_MASTER_FOR_CONTACTS }
        val identities = incoming.filter { it.type == EntryType.IDENTITY }
        val taken = current.filter { it.type == EntryType.IDENTITY }.mapTo(HashSet()) { it.id }
        check(identities.map { it.id }.distinct().size <= 1 && identities.all { id -> taken.all { it == id.id } }) { ONLY_ONE_IDENTITY }
    }

    /**
     * Whether [file] is not a [VaultFile.FORMAT] file of version 3 or 4, so identity and contacts cannot be saved in it;
     * false with no vault. The one rule behind [needsMasterForContacts] and [checkCanHold].
     */
    private fun needsMaster(file: VaultFile?): Boolean =
        file != null && (file.format != VaultFile.FORMAT || (file.version != VaultFile.VERSION && file.version != VaultFile.VERSION_3))

    /**
     * 2.0, task 2: gives the open vault the name [name] (trimmed), saved inside it, encrypted ([VaultData.name]); the
     * registry never holds it. False if the vault is locked or already has that name, so nothing was saved; over
     * [VaultLimits.VAULT_NAME_MAX] code points, [VaultLimitExceeded] and nothing saved.
     */
    suspend fun rename(name: String): Boolean {
        val next = name.trim()
        return modifyData { data -> if (data.name == next) null else data.copy(name = next) }
    }

    // 2.0, task 4: folders, views, tag colours and the last use (Folders.kt). Each is one save with the mutex held, and
    // gives false (or null, or 0), with nothing saved, when the vault is locked or nothing changes. A change refused
    // throws [OrganizeRefused] or [VaultLimitExceeded] with its fixed text ([storeSaveErrorText]), with nothing saved.

    /** A new folder [name] inside [parentId] (null for the top): the folder, or null when the vault is locked. */
    suspend fun createFolder(name: String, parentId: String?): Folder? {
        val id = newOrganizeId()
        val saved = modifyData { data -> data.withNewFolder(name, parentId, id) }
        return if (!saved) null else (_state.value as? VaultState.Unlocked)?.data?.folders?.firstOrNull { it.id == id }
    }

    suspend fun renameFolder(id: String, name: String): Boolean = modifyData { data -> data.withFolderRenamed(id, name) }

    /** Moves folder [id] inside [newParentId] (null for the top); refuses a cycle and more than 5 levels. */
    suspend fun moveFolder(id: String, newParentId: String?): Boolean = modifyData { data -> data.withFolderMoved(id, newParentId) }

    /** Deletes folder [id]; its entries go to the folder above or to the trash ([FolderDeletion], [withoutFolder]). */
    suspend fun deleteFolder(id: String, entriesTo: FolderDeletion): Boolean = modifyData { data -> data.withoutFolder(id, entriesTo, now()) }

    /** Moves the entries of [ids] to [folderId] (null for none), in one save; how many moved ([withEntriesMoved]). */
    suspend fun moveEntries(ids: Set<String>, folderId: String?): Int {
        var count = 0
        modifyData { data ->
            count = 0
            data.withEntriesMoved(ids, folderId)?.let { (next, n) ->
                count = n
                next
            }
        }.let { saved -> if (!saved) count = 0 }
        return count
    }

    /**
     * 2.0, task 6: «Favorita» of the selection bar: the entries of [ids] the list holds marked as favourites, or not, in
     * one save, each with a version as the editor makes ([withFavorite]); how many changed (0, with nothing saved, when
     * none or the vault is locked).
     */
    suspend fun setFavorite(ids: Set<String>, favorite: Boolean): Int = changeSelected { data -> data.withFavorite(ids, favorite, now()) }

    /** 2.0, task 6: «Etiquetar» of the selection bar: [tag] added to the entries of [ids] ([withTagAdded]); how many got it. */
    suspend fun addTag(ids: Set<String>, tag: String): Int = changeSelected { data -> data.withTagAdded(ids, tag, now()) }

    private suspend fun changeSelected(change: (VaultData) -> Pair<VaultData, Int>?): Int {
        var count = 0
        modifyData { data ->
            count = 0
            change(data)?.let { (next, n) ->
                count = n
                next
            }
        }.let { saved -> if (!saved) count = 0 }
        return count
    }

    suspend fun saveView(view: SavedView): Boolean = modifyData { data -> data.withView(view) }

    suspend fun deleteView(id: String): Boolean = modifyData { data -> data.withoutView(id) }

    /** Gives [tag] the colour [color], or none for null ([withTagColor]). */
    suspend fun setTagColor(tag: String, color: TagColor?): Boolean = modifyData { data -> data.withTagColor(tag, color) }

    /**
     * Notes that entry [id] was used now ([VaultEntry.lastUsedAt], for «Recientes» and the order «por uso»): saved in the
     * vault, but no new version of its history and no change for the automatic copies (design «Ordenar»): the file keeps
     * the time of the last change and [revision] does not go up ([modifyData] with `bookkeeping`). At most once a minute
     * per entry ([MARK_USED_EVERY_MS]); never for one in the trash. True when it was written.
     */
    suspend fun markUsed(id: String): Boolean = modifyData(bookkeeping = true) { data -> data.withUsed(id, now()) }

    /**
     * 2.0, task 8: «Comprobar una copia» checked a copy whose [VaultData.vaultTag] is [copyTag]: keeps the date
     * ([VaultData.lastCheckedBackupAt], now) when it is this vault's own tag ([withBackupChecked]), compared here, under the
     * mutex, with the vault as it is. A bookkeeping save, as [markUsed]: no version, no change for the automatic copies.
     * True when it was kept; false when the copy is not of this vault (another vault's tag, or none: made before 2.0);
     * null when nothing could be compared: the vault is not open, its upgrade waits, or it locked since [sinceLock] (the
     * [lockEpoch] when the check started), also if it was opened again meanwhile.
     */
    suspend fun recordBackupChecked(copyTag: String, sinceLock: Int = epoch): Boolean? {
        var own: Boolean? = null
        val saved = modifyData(bookkeeping = true, sinceLock = sinceLock) { data ->
            data.withBackupChecked(copyTag, now()).also { own = it != null }
        }
        return if (saved) true else own
    }

    /**
     * 2.0, task 3: sends the entry to the trash ([moveToTrash]); true when it was there to move. The identity and the
     * contacts ([EntryType.isPersonal], their own tab) never go to the trash: they are deleted for good, as before, after
     * the screen's own confirmations, which say so; their files go with the collection after the save.
     */
    suspend fun delete(id: String): Boolean {
        val personal = (_state.value as? VaultState.Unlocked)?.entries?.any { it.id == id && it.type.isPersonal } == true
        if (!personal) return moveToTrash(setOf(id)) > 0
        return modify { list -> if (list.none { it.id == id && it.type.isPersonal }) null else list.filterNot { it.id == id } }
    }

    /**
     * 2.0, task 3: sends the entries of [ids] not in the trash there ([VaultEntry.deletedAt] = now), with their
     * attachments, in one save; how many went (0, with nothing saved, when none or the vault is locked). Never the
     * identity nor a contact ([delete]).
     */
    suspend fun moveToTrash(ids: Set<String>): Int = trash(ids).size

    /** [moveToTrash], with the ids it really moved ([UndoTrash] brings back only those). */
    internal suspend fun trash(ids: Set<String>): Set<String> {
        val moved = HashSet<String>()
        modifyData { data ->
            moved.clear()
            val at = now()
            val entries = data.entries.map { e ->
                if (e.deletedAt == null && !e.type.isPersonal && e.id in ids && moved.add(e.id)) e.copy(deletedAt = at) else e
            }
            if (moved.isEmpty()) null else data.copy(entries = entries)
        }.let { saved -> if (!saved) moved.clear() }
        return moved
    }

    /**
     * 2.0, task 3: brings the entries of [ids] back from the trash, in one save; how many came back. The app never sends
     * an identity there ([moveToTrash]), but a copy made elsewhere may bring one: it does not come back while the vault
     * has another one (there is only one identity); it stays in the trash.
     */
    suspend fun restore(ids: Set<String>): Int {
        var count = 0
        modifyData { data ->
            count = 0
            var identity = data.live.any { it.type == EntryType.IDENTITY }
            // 2.0, task 4: back to its folder if it is still there, to no folder if not ([deleteFolder]).
            val folders = data.folders.mapTo(HashSet()) { it.id }
            val entries = data.entries.map { e ->
                if (e.deletedAt == null || e.id !in ids) return@map e
                if (e.type == EntryType.IDENTITY) {
                    if (identity) return@map e
                    identity = true
                }
                count++
                e.copy(deletedAt = null, folderId = e.folderId?.takeIf { it in folders })
            }
            if (count == 0) null else data.copy(entries = entries)
        }.let { saved -> if (!saved) count = 0 }
        return count
    }

    /**
     * 2.0, task 3: «Borrar para siempre»: removes from the vault the entries of [ids] that are in the trash (never one
     * that is not); their attachments' files go with the collection after the save. How many went. The copies already
     * made keep them (Abogado Digital, O-2).
     */
    suspend fun deleteForever(ids: Set<String>): Int = removeFromTrash { it.id in ids }

    /** 2.0, task 3: «Vaciar papelera»: [deleteForever] of everything in the trash. */
    suspend fun emptyTrash(): Int = removeFromTrash { true }

    private suspend fun removeFromTrash(which: (VaultEntry) -> Boolean): Int {
        var count = 0
        modifyData { data ->
            val kept = data.entries.filterNot { it.deletedAt != null && which(it) }
            count = data.entries.size - kept.size
            if (count == 0) null else data.copy(entries = kept)
        }.let { saved -> if (!saved) count = 0 }
        return count
    }

    /**
     * 2.0, task 3: «Restaurar esta versión»: the version of entry [entryId] saved at [savedAt] becomes the entry again,
     * as a new save, which keeps what was there as a new version ([withVersion]) and its password in the list of old
     * ones ([withNewPassword]); its attachments stay. [index] is the version's place in the history, as the screen showed
     * it (review M-7: two versions of the same millisecond); with it, the version there must have [savedAt], and without
     * it the first with [savedAt] is taken. False, with nothing saved, when there is no such entry (out of the trash) or
     * version, the version is the entry as it is, or either is an identity or a contact (review M-2: a version from a
     * copy made elsewhere never turns an entry into one).
     */
    suspend fun restoreVersion(entryId: String, savedAt: Long, index: Int = -1): Boolean = modifyData { data ->
        val current = data.live.firstOrNull { it.id == entryId } ?: return@modifyData null
        val found = if (index >= 0) current.history.getOrNull(index)?.takeIf { it.savedAt == savedAt } else current.history.firstOrNull { it.savedAt == savedAt }
        val version = found?.entry ?: return@modifyData null
        if (version.type.isPersonal || current.type.isPersonal) return@modifyData null
        val passwords = if (current.type == EntryType.LOGIN && version.type == EntryType.LOGIN) {
            current.withNewPassword(version.password, now()).passwordHistory
        } else {
            current.passwordHistory
        }
        val restored = version.copy(
            id = current.id, createdAt = current.createdAt, updatedAt = now(), lastUsedAt = current.lastUsedAt, folderId = current.folderId,
            history = current.history, passwordHistory = passwords, attachments = current.attachments, deletedAt = null,
        )
        if (!restored.changedFrom(current)) return@modifyData null
        checkCanHold(data.live.filterNot { it.id == entryId }, listOf(restored))
        data.copy(entries = data.entries.map { if (it.id == entryId) restored.withVersion(current, now()) else it })
    }

    /**
     * 2.0, task 3: «Borrar historial» of entry [entryId] (out of the trash): its versions and the legacy list of old
     * passwords of 1.4.3. False, with nothing saved, when it had none. The copies already made keep them (O-2).
     */
    suspend fun clearHistory(entryId: String): Boolean = modifyData { data ->
        val e = data.live.firstOrNull { it.id == entryId } ?: return@modifyData null
        val cleared = e.withoutHistory()
        if (cleared === e) null else data.copy(entries = data.entries.map { if (it === e) cleared else it })
    }

    /** 2.0, task 3: «Borrar historial» of every entry, those in the trash too; how many had any. */
    suspend fun clearAllHistory(): Int {
        var count = 0
        modifyData { data ->
            count = 0
            val entries = data.entries.map { e -> e.withoutHistory().also { if (it !== e) count++ } }
            if (count == 0) null else data.copy(entries = entries)
        }.let { saved -> if (!saved) count = 0 }
        return count
    }

    /**
     * Encrypts [input] as a new attachment of entry [entryId] and saves the vault. Throws [AttachmentLimit] over
     * [MAX_ATTACHMENT_BYTES], or when the vault's attachments would pass [MAX_ATTACHMENTS_TOTAL_BYTES] (checked
     * before encrypting or writing anything, with the mutex held, so no other attachment can be added meanwhile).
     * False if the vault was locked, the entry no longer exists or it is an identity or a contact (they keep no
     * attachments), so nothing was saved.
     * False too, with nothing saved, if the vault locked while [input] was read (even if it was opened again).
     * [input] (maybe a slow or stuck document provider) is read before taking the mutex, so it can never block
     * unlocking or saving. Only encrypting, writing the file and saving the vault run with the mutex held, so no
     * collection can run between writing the file and saving its reference; if the save fails, the next collection
     * deletes it.
     */
    suspend fun addAttachment(entryId: String, input: java.io.InputStream, name: String): Boolean {
        // Re-review 2 of task 7 (M-A): the epoch before the document is read (it may take long): a lock meanwhile refuses
        // the save, also if the vault was opened again before the read ended.
        val sinceLock = epoch
        val plain = withContext(io) { attachments.read(input) }
        try {
            return modify(sinceLock = sinceLock) { list ->
                val entry = list.firstOrNull { it.id == entryId && !it.type.isPersonal } ?: return@modify null
                if (attachments.totalBytes(list.flatMap { it.attachments }) + plain.size > MAX_ATTACHMENTS_TOTAL_BYTES) {
                    throw AttachmentLimit(ATTACHMENTS_TOTAL_TOO_BIG)
                }
                val ref = withContext(io) { attachments.add(plain, name) }
                list.map { if (it === entry) it.copy(attachments = it.attachments + ref, updatedAt = now()) else it }
            }
        } finally {
            plain.fill(0)
        }
    }

    /**
     * Removes an attachment from entry [entryId]; its file goes with the collection after the save. False if locked
     * or there is no such attachment, so nothing was saved.
     */
    suspend fun removeAttachment(entryId: String, attachmentId: String): Boolean = modify { list ->
        val entry = list.firstOrNull { e -> e.id == entryId && e.attachments.any { it.id == attachmentId } } ?: return@modify null
        list.map { e -> if (e === entry) e.copy(attachments = e.attachments.filterNot { it.id == attachmentId }, updatedAt = now()) else e }
    }

    /**
     * The decrypted content of an attachment of the open vault; null if locked or it is no longer there. Throws
     * [CryptoFailure] when the file is damaged or was changed. The caller wipes the result when done.
     */
    suspend fun openAttachment(entryId: String, attachmentId: String): ByteArray? {
        val entries = (_state.value as? VaultState.Unlocked)?.entries ?: return null
        val ref = entries.firstOrNull { it.id == entryId }?.attachments?.firstOrNull { it.id == attachmentId } ?: return null
        touch()
        return withContext(io) { attachments.open(ref) }
    }

    /**
     * New master password and a new data key; false if the vault was locked, so nothing changed. Once the new file
     * is saved the vault locks (both data keys wiped), so the user opens it with the new password before going on,
     * which also proves they remember it. If anything fails, it throws and the vault stays open as it was.
     */
    suspend fun changeMaster(newMaster: CharArray): Boolean = mutex.withLock {
        check(upgrade == null) { UPGRADE_PENDING }
        val v = vault ?: return@withLock false
        val key = dek?.copyOf() ?: return@withLock false
        val (next, newKey) = try {
            derive { VaultCrypto.changeMaster(v, key, newMaster, now(), kdf()) }
        } finally {
            key.fill(0)
        }
        // As in [commit]: leaving the screen cannot cut the save and the switch to the new file in half.
        withContext(NonCancellable) {
            try {
                save(next)
                changed()
            } finally {
                newKey.fill(0) // Never kept: the vault locks below, or stays on the old key if the save failed.
            }
            vault = next
            lock()
        }
        true
    }

    /** The encrypted vault as stored; safe to put anywhere (useless without the master password). */
    suspend fun exportText(): String? = mutex.withLock { vault?.let(VaultCrypto::encodeFile) }

    /**
     * Writes the vault and the attachment files of its entries, as stored (still encrypted), to [out] as a
     * `seguridad-backup` copy ([writeBackup]); [out] is left open. Each attachment is checked against its SHA-256
     * while it is written: a missing or damaged one throws [CryptoFailure] naming it, as such a copy could never be
     * restored. False if there is no vault or it is locked (its entries, and so which attachments go and what they
     * must hash to, cannot be read): nothing was written, so an unchecked copy is never made.
     * Only the snapshot is taken with the mutex held; [out] (maybe a slow document provider) is written without it.
     * Attachment files never change once written, so the worst a concurrent change can do is delete one of them,
     * which makes this fail rather than write a wrong copy.
     */
    suspend fun exportBackup(out: OutputStream): Boolean = export(out, beforeUpgradeOnly = false)

    /**
     * 2.0 (review I-1): [exportBackup] only while the upgrade to v4 waits, so what it writes is always the file of
     * version 2 or 3 still on disk, with the marker 2 earlier versions open: the copy before the upgrade (the automatic
     * one, [AutoBackup.copyBeforeUpgrade], and «Exportar una copia antes»). False, with nothing written, once there is no
     * upgrade waiting (saved, dropped by a lock, or a vault that was always a 4): the caller deletes its empty document.
     * Checked with the mutex held, where the snapshot is taken; an upgrade saved after it does not change what is written.
     */
    suspend fun exportBeforeUpgrade(out: OutputStream): Boolean = export(out, beforeUpgradeOnly = true)

    private suspend fun export(out: OutputStream, beforeUpgradeOnly: Boolean): Boolean {
        val refs: List<AttachmentRef>
        val json: String
        val version: Int
        mutex.withLock {
            if (beforeUpgradeOnly && upgrade == null) return false
            val v = vault ?: return false
            version = v.version
            // The trash's attachments too: a copy brings the trash back (2.0).
            refs = (_state.value as? VaultState.Unlocked)?.data?.entries?.flatMap { it.attachments } ?: return false
            json = VaultCrypto.encodeFile(v)
            refs.firstOrNull { !isAttachmentId(it.id) }?.let { throw CryptoFailure(attachmentUnusable(it.name)) }
        }
        val files = refs.associate { it.id to attachments.fileOf(it.id) }
        val expected = refs.associate { it.id to it.sha256 }
        try {
            withContext(io) { writeBackup(out, json, files, expected, vaultVersion = version) }
        } catch (e: BackupAttachmentMismatch) {
            throw CryptoFailure(attachmentUnusable(refs.firstOrNull { it.id == e.id }?.name ?: e.id))
        }
        return true
    }

    /**
     * Replaces this vault with an exported one after checking its master password. Throws [CryptoFailure]. An export
     * whose entries have attachments is refused (its files are only in the .sgbak copy), and so is one with more than
     * one identity; nothing changes. True when the import dropped a request, still waiting, to replace the old
     * automatic copies (A6; see [beforeReplace]), so the caller says so.
     */
    suspend fun importReplace(text: String, master: CharArray): Boolean {
        checkCanReplace()
        return importText(text, master, epoch)
    }

    /**
     * Only an open vault, or none (the "Crea tu bóveda" screen), is ever replaced: never one that is locked. A
     * [CryptoFailure], as the import dialog shows its message.
     */
    private fun checkCanReplace() {
        if (vault != null && _state.value !is VaultState.Unlocked) throw CryptoFailure(REPLACE_NEEDS_OPEN)
        // 2.0: not while the upgrade waits for the user's choice (its dialog is in front of everything anyway).
        if (upgrade != null) throw CryptoFailure(UPGRADE_PENDING)
    }

    private suspend fun importText(text: String, master: CharArray, since: Int, newTag: Boolean = false): Boolean {
        val incoming = withContext(compute) { VaultCrypto.decodeFile(text) }
        return replace(incoming, master, null, since, newTag)
    }

    /**
     * [importReplace] from a file: a `seguridad-backup` copy (.sgbak, recognised by its head with [isBackup]) or a
     * JSON export of Seguridad 1.0.x (at most [VAULT_IMPORT_MAX_BYTES]). The copy is read as a stream into
     * a temporary folder next to the attachments, before taking the mutex and always deleted afterwards. All or
     * nothing: its master password and every attachment (present, with the SHA-256 its entry records) are checked
     * before anything is replaced; if writing the vault then fails, the attachments are put back as they were.
     * The attachments the old vault had and the new one does not are collected after the save. Returns what the
     * other [importReplace] does. With [newTag] (2.0, «Importar como bóveda nueva»), the vault gets a new
     * [VaultData.vaultTag] instead of the copy's.
     */
    suspend fun importReplace(input: InputStream, master: CharArray, newTag: Boolean = false): Boolean {
        checkCanReplace()
        val since = epoch // Leaving the app while the file is read still locks: the copy is imported, but stays locked.
        val buffered = BufferedInputStream(input)
        val head = withContext(io) {
            buffered.mark(BACKUP_HEAD_BYTES)
            buffered.readAtMost(BACKUP_HEAD_BYTES).also { buffered.reset() }
        }
        if (!isBackup(head)) {
            val bytes = withContext(io) { buffered.readAtMost(VAULT_IMPORT_MAX_BYTES + 1) }
            if (bytes.size > VAULT_IMPORT_MAX_BYTES) throw CryptoFailure(IMPORT_TOO_BIG)
            return importText(bytes.decodeToString(), master, since, newTag)
        }
        val temp = File(attachments.dir.absoluteFile.parentFile, IMPORT_DIR_PREFIX + randomBytes(8).joinToString("") { "%02x".format(it) })
        return try {
            val contents = withContext(io) { BackupReader().read(buffered, temp) }
            val incoming = withContext(compute) { VaultCrypto.decodeFile(contents.vaultJson) }
            replace(incoming, master, contents to temp, since, newTag)
        } finally {
            withContext(NonCancellable) { withContext(io) { temp.deleteRecursively() } }
        }
    }

    /**
     * [importReplace]; [backup] is the copy read and its temporary folder, null for a JSON export. [since] is [epoch]
     * when the import started: a lock since then leaves the imported vault locked. Returns [ReplacePrepared.droppedRequest].
     */
    private suspend fun replace(
        incoming: VaultFile,
        master: CharArray,
        backup: Pair<BackupContents, File>?,
        since: Int,
        newTag: Boolean = false,
    ): Boolean = mutex.withLock {
        val key = derive { VaultCrypto.unlock(incoming, master) }
        // Validates the data before replacing anything, and brings an old export to the current format.
        val (checked, refs) = try {
            // 2.0, task 8 (D-12): «Importar como bóveda nueva» gives the new vault a tag of its own, so a copy of the vault
            // it came from never counts as checked for it (nor the other way round).
            val checked = derive {
                VaultCrypto.afterMasterUnlock(incoming, key, master, now(), kdf(), prepare = { if (newTag) it.copy(vaultTag = VaultCrypto.newTag()) else it })
            }
            val incomingData = withContext(compute) { VaultCrypto.read(checked, key) }
            val incomingEntries = incomingData.entries
            checkCopyStructure(incomingData)
            // The rules «Comprobar una copia» checks too (BackupCheck.kt), so the two never drift apart: one identity per
            // vault (see [upsert]), and what [upsert] and [addAttachment] keep. A copy that breaks one is refused before
            // anything changes.
            checkCopyEntries(incomingEntries)
            val refs = incomingEntries.flatMap { it.attachments }
            if (backup == null) {
                if (refs.isNotEmpty()) throw CryptoFailure(JSON_HAS_ATTACHMENTS)
            } else {
                // Each file, in the temporary folder the copy was read into, is the one moved into place below: what is
                // checked here is what is imported, from a single read of the document.
                withContext(io) { refs.forEach { if (attachmentInCopy(it, backup.first) != CopyAttachment.OK) throw CryptoFailure(attachmentNotInCopy(it.name)) } }
            }
            checked to refs
        } catch (e: Exception) {
            key.fill(0)
            throw e
        }
        withContext(NonCancellable) {
            // All of the copy is fine: from here a copy still being written is of the vault being replaced (CADENAS-3).
            // Bumped before the settings forget its copies, so such a copy can never list them again; if the import then
            // fails, a copy in flight only stays unlisted (never deleted), which is the safe side.
            nextGeneration()
            val prepared = try {
                beforeReplace()
            } catch (e: Exception) {
                key.fill(0)
                throw CryptoFailure(IMPORT_SETTINGS_FAILED) // Nothing written: the vault and its copies' list are as they were.
            }
            // From here a failure before the save puts the settings back; [changed] (the revision) tells whether it saved.
            val before = _revision.value
            try {
                // Importing with no readable vault (the "Crea tu bóveda" screen): a file that could not be read is kept, as in [create].
                if (vault == null) withContext(io) { keepAside() }
                if (backup == null) {
                    commit(checked, key, since)
                    onClockReset(now()) // Only once the copy is saved: a failed save lowers nothing.
                    return@withContext prepared.droppedRequest
                }
                val moved = try {
                    withContext(io) { moveIn(refs.mapTo(HashSet()) { it.id }, backup.first.attachments, backup.second) }
                } catch (e: Exception) {
                    key.fill(0)
                    throw e
                }
                try {
                    save(checked)
                    changed()
                } catch (e: Exception) {
                    withContext(io) { putBack(moved) }
                    key.fill(0)
                    throw e
                }
            } catch (e: Exception) {
                if (_revision.value == before) undo(prepared) // Not saved: still the old vault, with its list.
                throw e
            }
            onClockReset(now()) // Saved: the copy's master password was verified and it is the vault now.
            opened(checked, key, since)
            collectGarbage() // The old vault's attachments the new one does not use.
            prepared.droppedRequest
        }
    }

    /** Puts the settings back after an import that was not saved; best effort (a full disk keeps the forget, which is safe). */
    private suspend fun undo(prepared: ReplacePrepared) {
        try {
            prepared.undo()
        } catch (e: Exception) {
            // The old vault's copies stay unlisted: never deleted, only no longer rotated.
        }
    }

    /** An attachment file moved into place, and the file with the same id it displaced (kept in the temporary folder). */
    private class Moved(val target: File, val displaced: File?)

    /**
     * Moves the checked files of [ids] into the attachments folder (renames on the same file system). A file already
     * there with the same id is moved aside to [temp] first, to be put back if anything fails. If a move fails,
     * everything moved so far is put back before throwing.
     */
    private fun moveIn(ids: Set<String>, files: Map<String, File>, temp: File): List<Moved> {
        val aside = File(temp, "prev").apply { mkdirs() }
        attachments.dir.mkdirs()
        val done = ArrayList<Moved>()
        try {
            for (id in ids.sorted()) {
                val target = attachments.fileOf(id)
                val displaced = if (target.exists()) File(aside, "$id.bin").also { move(target, it) } else null
                done += Moved(target, displaced) // Before the move in, so a failure there still puts the old one back.
                move(files.getValue(id), target)
            }
        } catch (e: Exception) {
            putBack(done)
            throw e
        }
        return done
    }

    /** Undoes [moveIn]: takes the new files out and puts the displaced ones back. Best effort, never throws. */
    private fun putBack(moved: List<Moved>) {
        for (m in moved.asReversed()) {
            try {
                if (m.displaced != null) move(m.displaced, m.target) else m.target.delete()
            } catch (e: Exception) {
                // Nothing more can be done; the rest are still put back.
            }
        }
    }

    private fun move(from: File, to: File) {
        Files.move(from.toPath(), to.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    /** Temporary folders of imports cut short (the app closed meanwhile); called at start, when no import runs. */
    private fun removeImportLeftovers() {
        try {
            attachments.dir.absoluteFile.parentFile?.listFiles()?.forEach { f ->
                if (IMPORT_DIR.matches(f.name)) f.deleteRecursively()
            }
        } catch (e: Exception) {
            // Tried again at the next start.
        }
    }

    /** Deletes the vault for good (forgotten master password). */
    suspend fun destroy() = mutex.withLock {
        nextGeneration()
        lock()
        upgrades.doneShown() // Nothing to tell of a vault that is gone.
        withContext(io) {
            file.delete()
            // What keepAside() left (vault.bin.bad-<time>, att.bad-<time>) goes too: «He olvidado la contraseña» says so.
            val dir = file.absoluteFile.parentFile
            dir?.listFiles()?.filter { it.name.startsWith(file.name + ".bad-") || it.name.startsWith("att.bad-") }?.forEach { it.deleteRecursively() }
        }
        // Their keys were in the vault: the files are useless now.
        try {
            withContext(io) { attachments.collectGarbage(emptySet()) }
        } catch (e: Exception) {
            // Never affects the vault.
        }
        vault = null
        _state.value = VaultState.NoVault
    }
}

/**
 * Largest JSON export read (a real one is a few hundred KB): a huge file must not exhaust memory. 2.0: 40 MiB, in
 * proportion to the 33 MiB a vault may take in a copy with marker 3 ([BACKUP_V3_MAX_ENTRY_BYTES]).
 */
const val VAULT_IMPORT_MAX_BYTES = 40 * 1024 * 1024

/**
 * 2.0: what a save says while the vault waits for the choice before passing to v4 ([VaultStore.pendingUpgrade]); the
 * dialog that offers the choice is in front of every vault screen, so it is only a last defence.
 */
const val UPGRADE_PENDING = "Antes de guardar nada, elige en el aviso si exportas una copia de la bóveda."

/**
 * 2.0, re-review N-1 of task 3: what a screen says when a save of the store throws [e]. Only the store's own Spanish
 * texts (a limit, the upgrade waiting, the master for contacts, one identity) reach the screen; any other message, such
 * as an English one from a future check, gives [TRASH_SAVE_FAILED].
 */
fun storeSaveErrorText(e: Throwable): String {
    val text = e.message
    return when {
        e is VaultLimitExceeded && text != null -> text
        e is OrganizeRefused && text != null -> text
        e is IllegalStateException && text in setOf(UPGRADE_PENDING, NEEDS_MASTER_FOR_CONTACTS, ONLY_ONE_IDENTITY) -> text!!
        else -> TRASH_SAVE_FAILED
    }
}

private const val NEEDS_MASTER_FOR_CONTACTS = "Hace falta la contraseña maestra para guardar la identidad o los contactos"
private const val REPLACE_NEEDS_OPEN = "Abre la bóveda antes de importar una copia"

/** The settings could not record, before the import, that the old vault's automatic copies are not the new one's. */
const val IMPORT_SETTINGS_FAILED = "No se ha importado la copia: no se pudieron guardar los ajustes de la app. ¿Queda espacio en el móvil?"

/**
 * What [VaultStore]'s `beforeReplace` did before an import saves the vault: whether it dropped a request, still waiting,
 * to replace the old automatic copies (A6), which «Copia importada» then says; and [undo], which puts the settings back
 * if the vault is not saved.
 */
class ReplacePrepared(val droppedRequest: Boolean, val undo: suspend () -> Unit)
private const val ONLY_ONE_IDENTITY = "Solo puede haber una identidad"
private const val IMPORT_TOO_BIG = "El archivo pasa de 40 MB: no es una copia de la bóveda"
private const val JSON_HAS_ATTACHMENTS = "Esta copia tiene adjuntos: importa el archivo .sgbak"
private const val IMPORT_DIR_PREFIX = "import-"

/** What [VaultStore.importSnapshot] gives: the entries shown and the revision they belong to. */
data class VaultSnapshot(val entries: List<VaultEntry>, val revision: Long)

/** The marker entry's content in a `.sgbak` («2\n»), counted by [writeBackup] in the copy's total. */
private const val BACKUP_MARKER_BYTES = 2L
private val IMPORT_DIR = Regex("import-[0-9a-f]{16}")

/** Longest entry id a copy may bring (the app makes 36-character UUIDs). */
private const val MAX_ENTRY_ID_CHARS = 1024

/**
 * An entry id a copy may bring: not empty, at most [MAX_ENTRY_ID_CHARS] and without control characters. Anything else
 * that 1.0 to 1.2.x accepted is kept (the vault is read forever); the routes encode the id (LIBRE-1).
 */
internal fun isEntryId(id: String): Boolean = id.isNotEmpty() && id.length <= MAX_ENTRY_ID_CHARS && id.none { it.isISOControl() }

private fun attachmentUnusable(name: String) = "El adjunto «$name» falta o está dañado: quítalo de su entrada para poder hacer la copia"

/**
 * Longest the vault stays open without use, with «Al salir», while a picker or the QR scanner we opened is on top, or
 * while another app has the focus in multi-window.
 */
const val AWAY_BACKSTOP_MS = 2 * 60_000L

/**
 * How long without use before [VaultStore.lockIfIdle] locks; 0 means never on time alone. A time chosen in Ajustes
 * ([chosen] > 0) is used as it is; «Al salir» (0) gets [AWAY_BACKSTOP_MS] while the app is away without onStop:
 * [keptOpen] (a picker or the scanner) or [awayInMultiWindow] (another app has the focus in multi-window).
 */
internal fun idleLimitMs(chosen: Long, keptOpen: Boolean, awayInMultiWindow: Boolean): Long = when {
    chosen > 0 -> chosen
    keptOpen || awayInMultiWindow -> AWAY_BACKSTOP_MS
    else -> 0L
}

/**
 * Whether the [AWAY_BACKSTOP_MS] of a trip away (to another app in multi-window) count from the moment of leaving: only
 * with «Al salir» ([chosen] 0). A time chosen in Ajustes already counts the time without use.
 */
internal fun backstopFromLeaving(chosen: Long): Boolean = chosen <= 0L

object VaultLock {
    /** Set just before the vault opens a system screen (file picker, QR scanner) so leaving the app for it does not lock. */
    @Volatile var keepOpenOnce = false
}
