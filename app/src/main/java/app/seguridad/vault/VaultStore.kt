package app.seguridad.vault

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
import app.seguridad.crypto.KdfParams
import app.seguridad.crypto.NONCE_BYTES
import app.seguridad.crypto.TAG_BYTES
import app.seguridad.crypto.randomBytes

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
    data class Unlocked(val entries: List<VaultEntry>) : VaultState
}

/** Shown instead of the 7-day notice when the clock gate refused the fingerprint (A11). */
const val CLOCK_CHANGED_TEXT = "La hora del móvil se ha atrasado. Por seguridad, escribe la contraseña maestra."

/**
 * Biometric unlock refused: the master password was not typed for [MASTER_RECHECK_MS], or the clock went backwards
 * ([reason]); the message names the one reason, never both.
 */
class MasterRequired(val reason: BiometricRefusal = BiometricRefusal.MASTER_DUE) :
    Exception(if (reason == BiometricRefusal.CLOCK) CLOCK_CHANGED_TEXT else "Escribe tu contraseña maestra (se pide cada 7 días)")

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
     * Runs before an import replaces the vault, once the copy is checked (the app forgets which automatic copies it
     * made, so they are never rotated away: they are the replaced vault's). If it throws, nothing is replaced.
     */
    private val beforeReplace: suspend () -> Unit = {},
    /** The highest time the app ever saw (a mark that only rises), kept in the settings; see [VaultCrypto.biometricAllowed]. */
    private val clockHighWater: () -> Long = { 0 },
    /** Told the time at every unlock and every [touch]; the app keeps the highest in the settings. */
    private val onClockSeen: (Long) -> Unit = {},
    /** Told the time after the master password was verified (unlock, [confirmMaster], import): the mark restarts there, even lower. */
    private val onClockReset: (Long) -> Unit = {},
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

    private val mutex = Mutex()
    private val _needsMaster = MutableStateFlow(false)

    /**
     * Whether the vault is not yet version 3 (it was written by 1.1 and opened here with the fingerprint, or is not
     * opened yet): identity and contacts cannot be saved until [confirmMaster] (or [unlock]) wraps the key again under
     * the master password. False with no vault. Follows the file the store holds, so it changes after [load], [unlock],
     * [unlockWithKey], [confirmMaster], an import, [changeMaster] and [destroy]; [lock] leaves it as it is (the file
     * on disk does not change by locking).
     */
    val needsMasterForContacts: StateFlow<Boolean> = _needsMaster.asStateFlow()

    private var vault: VaultFile? = null
        set(value) {
            field = value
            _needsMaster.value = needsMaster(value)
        }
    private var dek: ByteArray? = null
    private var lastUse = 0L

    /**
     * Bumped by every [lock]. [lock] runs on the main thread without the mutex (leaving the app must lock at once),
     * so operations work on their own copy of the key and only reopen the vault if no lock happened meanwhile.
     * Each one reads it when called, before waiting for the mutex: a lock while it waits counts too.
     */
    @Volatile private var epoch = 0

    /** [epoch], read only: goes up with every [lock], so a holder can tell a lock happened since it last looked. */
    val lockEpoch: Int get() = epoch

    /**
     * Held by [lock] and by the last step of [opened] (check [epoch], then keep the key and show the entries), which
     * may run on another thread (an import runs on Dispatchers.IO): a lock can never land between the two.
     */
    private val opening = Any()

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
        val entries = (_state.value as? VaultState.Unlocked)?.entries ?: return
        val live = entries.flatMapTo(HashSet()) { e -> e.attachments.map { it.id } }
        try {
            withContext(io) { attachments.collectGarbage(live) }
        } catch (e: Exception) {
            // Never affects the vault.
        }
    }

    private suspend fun save(v: VaultFile) = withContext(io) { file.writeTextAtomically(VaultCrypto.encodeFile(v)) }

    /**
     * [save] for bookkeeping only (the last master check, a format upgrade): the file keeps the time of the last
     * change the user made, which [lastChangeSavedAt] gives. If the time cannot be set back, the worst is one more copy.
     */
    private suspend fun saveBookkeeping(v: VaultFile) = withContext(io) {
        val changedAt = file.lastModified()
        file.writeTextAtomically(VaultCrypto.encodeFile(v))
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
    private suspend fun commit(next: VaultFile, key: ByteArray, since: Int, mustSave: Boolean = true) = withContext(NonCancellable) {
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
        opened(next, key, since)
        // After a failed save that did not have to succeed, the disk differs only by bookkeeping: same attachments.
        collectGarbage()
    }

    private fun opened(v: VaultFile, key: ByteArray, since: Int) {
        vault = v
        val entries = if (epoch != since) null else try {
            VaultCrypto.read(v, key).sortedBy { it.title.lowercase() }
        } catch (e: CryptoFailure) {
            key.fill(0)
            throw e
        }
        synchronized(opening) {
            if (entries == null || epoch != since) {
                key.fill(0)
                if (_state.value !is VaultState.NoVault) _state.value = VaultState.Locked
                return
            }
            if (dek !== key) dek?.fill(0)
            dek = key
            lastUse = now()
            onClockSeen(lastUse)
            _state.value = VaultState.Unlocked(entries)
        }
    }

    suspend fun create(master: CharArray) {
        val since = epoch
        mutex.withLock { createLocked(master, since) }
    }

    private suspend fun createLocked(master: CharArray, since: Int) {
        val (v, key) = withContext(compute) { VaultCrypto.create(master, now(), kdf()) }
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

    /** Throws [CryptoFailure] for a wrong password. */
    suspend fun unlock(master: CharArray) {
        val since = epoch
        mutex.withLock { unlockLocked(master, since) }
    }

    private suspend fun unlockLocked(master: CharArray, since: Int) {
        val v = vault ?: return
        val key = withContext(compute) { VaultCrypto.unlock(v, master) }
        val checked = try {
            withContext(compute) { VaultCrypto.afterMasterUnlock(v, key, master, now(), kdf()) }
        } catch (e: Exception) {
            key.fill(0)
            throw e
        }
        onClockReset(now()) // The master password was right: a clock set forward no longer blocks the fingerprint.
        // A full disk must not lock the user out of their passwords: open anyway (see [commit]).
        commit(checked, key, since, mustSave = false)
    }

    val biometricAllowed: Boolean get() = biometricRefusal == null && vault != null

    /** Why the fingerprint is refused now (null when allowed or there is no vault). */
    val biometricRefusal: BiometricRefusal? get() = vault?.let { VaultCrypto.biometricRefusal(it, now(), clockHighWater()) }

    /** With a data key released by the biometric prompt. Throws [MasterRequired] when the 7-day recheck is due. */
    suspend fun unlockWithKey(key: ByteArray) {
        val since = epoch
        mutex.withLock { unlockWithKeyLocked(key, since) }
    }

    private suspend fun unlockWithKeyLocked(key: ByteArray, since: Int) {
        val v = vault ?: return key.fill(0)
        // The real version first: a version-1 file (date not authenticated) needs the master password once.
        val known = try {
            VaultCrypto.resolve(v, key)
        } catch (e: CryptoFailure) {
            key.fill(0)
            throw e
        }
        val refusal = VaultCrypto.biometricRefusal(known, now(), clockHighWater())
        if (refusal != null) {
            key.fill(0)
            throw MasterRequired(refusal)
        }
        opened(known, key, since)
        collectGarbage()
    }

    /**
     * For an open vault that is not version 3 yet (see [needsMasterForContacts]): checks [master] and, if right, turns
     * it into version 3 (same data key, so the fingerprint keeps working). A version-3 vault is only checked and
     * stays as it is. A wrong password throws what [unlock] throws ([CryptoFailure]) and touches neither the file nor
     * the open vault. Returns false, having checked nothing, when there is no vault or it is locked ([unlock] is the
     * way in; the caller must not go on to save), and true when the password is right (checked or migrated). Like
     * [unlock], it leaves [master] to the caller to wipe, and a full disk does not matter here (see [commit]): the
     * next save writes the upgrade.
     */
    suspend fun confirmMaster(master: CharArray): Boolean = mutex.withLock {
        val v = vault ?: return@withLock false
        if (dek == null || _state.value !is VaultState.Unlocked) return@withLock false
        val since = epoch
        val key = withContext(compute) { VaultCrypto.unlock(v, master) }
        onClockReset(now())
        if (v.format == VaultFile.FORMAT && v.version == VaultFile.VERSION) {
            key.fill(0) // Only the check.
            return@withLock true
        }
        val upgraded = try {
            withContext(compute) { VaultCrypto.afterMasterUnlock(v, key, master, now(), kdf()) }
        } catch (e: Exception) {
            key.fill(0)
            throw e
        }
        commit(upgraded, key, since, mustSave = false)
        true
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
        if (vault != null && _state.value is VaultState.Unlocked) {
            lastLockReason = reason
            lastLockInForeground = inForeground()
            _state.value = VaultState.Locked
        }
    }

    fun touch() {
        lastUse = now()
        onClockSeen(lastUse)
    }

    /**
     * Locks when unused for [idleLockMs]; returns true if it locked. 0 («Al salir») means no time lock while the
     * user is in the app, but while a picker or the QR scanner keeps the vault open in the background ([keptOpen]:
     * MainActivity.onStop does not lock then) it locks after [AWAY_BACKSTOP_MS], so Home or screen-off from there
     * never leaves it open for hours.
     */
    fun lockIfIdle(): Boolean {
        val chosen = idleLockMs()
        val limit = if (chosen > 0) chosen else if (keptOpen()) AWAY_BACKSTOP_MS else 0L
        if (limit > 0 && dek != null && now() - lastUse >= limit) {
            lock(LockReason.IDLE)
            return true
        }
        return false
    }

    /**
     * Saves the change even if the vault locks meanwhile (with a copy of the key), then reopens only if it did not.
     * False if the vault was already locked, or [change] returned null (nothing to change), so nothing was saved.
     * [change] runs with the mutex held: it must never wait on anything outside the app (a document provider).
     */
    private suspend fun modify(change: suspend (List<VaultEntry>) -> List<VaultEntry>?): Boolean = mutex.withLock {
        val v = vault ?: return@withLock false
        val since = epoch
        val key = dek?.copyOf() ?: return@withLock false
        val current = (_state.value as? VaultState.Unlocked)?.entries ?: return@withLock false.also { key.fill(0) }
        val next = try {
            withContext(compute) { change(current)?.let { VaultCrypto.write(v, key, it) } }
        } catch (e: Exception) {
            key.fill(0)
            throw e
        }
        if (next == null) {
            key.fill(0)
            return@withLock false
        }
        commit(next, key, since)
        true
    }

    /**
     * Adds or replaces [entry]. Throws [IllegalStateException] (nothing is saved) for an [EntryType.IDENTITY] when the
     * vault has another one, and for an identity or contact while the vault is still version 2 (see
     * [needsMasterForContacts]). Identities and contacts never keep attachments or password history.
     */
    suspend fun upsert(entry: VaultEntry) = modify { list ->
        checkCanHold(list, listOf(entry))
        val saved = list.firstOrNull { it.id == entry.id }
        if (entry.type.isPersonal) {
            // No passwords, history or attachments of their own: whatever the caller sends is dropped.
            val plain = entry.copy(passwordHistory = emptyList(), attachments = emptyList())
            if (saved == null) list + plain else list.map { if (it.id == entry.id) plain else it }
        } else if (saved == null) {
            list + entry.copy(passwordHistory = emptyList(), attachments = emptyList())
        } else {
            // The history is the store's to keep: whatever the caller sends is replaced by the stored one plus this change.
            val history = if (saved.type == EntryType.LOGIN && entry.type == EntryType.LOGIN) {
                saved.withNewPassword(entry.password, now()).passwordHistory
            } else {
                emptyList() // A type change never files a passphrase or another secret as an old password.
            }
            // The attachments too: only [addAttachment] and [removeAttachment] change them, so an editor that does
            // not send them never drops one (and the collection after the save never deletes its file).
            list.map { if (it.id == entry.id) entry.copy(passwordHistory = history, attachments = saved.attachments) else it }
        }
    }

    /**
     * Adds new entries with a single write, all or none (a Google Authenticator batch). An id already in the vault is
     * left as it is, and new entries start without password history, as with [upsert].
     */
    suspend fun addAll(entries: List<VaultEntry>) = modify { list ->
        val ids = list.mapTo(HashSet()) { it.id }
        val added = entries.filter { ids.add(it.id) }
        checkCanHold(list, added)
        list + added.map { it.copy(passwordHistory = emptyList(), attachments = emptyList()) }
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
     * Whether [file] is not yet a [VaultFile.FORMAT] version-3 file, so identity and contacts need the master password
     * first; false with no vault. The one rule behind [needsMasterForContacts] and [checkCanHold].
     */
    private fun needsMaster(file: VaultFile?): Boolean =
        file != null && (file.format != VaultFile.FORMAT || file.version != VaultFile.VERSION)

    /** Deletes the entry; its attachment files go with the collection after the save. */
    suspend fun delete(id: String) = modify { list -> list.filterNot { it.id == id } }

    /**
     * Encrypts [input] as a new attachment of entry [entryId] and saves the vault. Throws [AttachmentLimit] over
     * [MAX_ATTACHMENT_BYTES], or when the vault's attachments would pass [MAX_ATTACHMENTS_TOTAL_BYTES] (checked
     * before encrypting or writing anything, with the mutex held, so no other attachment can be added meanwhile).
     * False if the vault was locked, the entry no longer exists or it is an identity or a contact (they keep no
     * attachments), so nothing was saved.
     * [input] (maybe a slow or stuck document provider) is read before taking the mutex, so it can never block
     * unlocking or saving. Only encrypting, writing the file and saving the vault run with the mutex held, so no
     * collection can run between writing the file and saving its reference; if the save fails, the next collection
     * deletes it.
     */
    suspend fun addAttachment(entryId: String, input: java.io.InputStream, name: String): Boolean {
        val plain = withContext(io) { attachments.read(input) }
        try {
            return modify { list ->
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
        val v = vault ?: return@withLock false
        val key = dek?.copyOf() ?: return@withLock false
        val (next, newKey) = try {
            withContext(compute) { VaultCrypto.changeMaster(v, key, newMaster, now(), kdf()) }
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
    suspend fun exportBackup(out: OutputStream): Boolean {
        val refs: List<AttachmentRef>
        val json: String
        mutex.withLock {
            val v = vault ?: return false
            refs = (_state.value as? VaultState.Unlocked)?.entries?.flatMap { it.attachments } ?: return false
            json = VaultCrypto.encodeFile(v)
            refs.firstOrNull { !isAttachmentId(it.id) }?.let { throw CryptoFailure(attachmentUnusable(it.name)) }
        }
        val files = refs.associate { it.id to attachments.fileOf(it.id) }
        val expected = refs.associate { it.id to it.sha256 }
        try {
            withContext(io) { writeBackup(out, json, files, expected) }
        } catch (e: BackupAttachmentMismatch) {
            throw CryptoFailure(attachmentUnusable(refs.firstOrNull { it.id == e.id }?.name ?: e.id))
        }
        return true
    }

    /**
     * Replaces this vault with an exported one after checking its master password. Throws [CryptoFailure]. An export
     * whose entries have attachments is refused (its files are only in the .sgbak copy), and so is one with more than
     * one identity; nothing changes.
     */
    suspend fun importReplace(text: String, master: CharArray) {
        checkCanReplace()
        importText(text, master, epoch)
    }

    /**
     * Only an open vault, or none (the "Crea tu bóveda" screen), is ever replaced: never one that is locked. A
     * [CryptoFailure], as the import dialog shows its message.
     */
    private fun checkCanReplace() {
        if (vault != null && _state.value !is VaultState.Unlocked) throw CryptoFailure(REPLACE_NEEDS_OPEN)
    }

    private suspend fun importText(text: String, master: CharArray, since: Int) {
        val incoming = withContext(compute) { VaultCrypto.decodeFile(text) }
        replace(incoming, master, null, since)
    }

    /**
     * [importReplace] from a file: a `seguridad-backup` copy (.sgbak, recognised by its head with [isBackup]) or a
     * JSON export of Seguridad or CriptoPanel (at most [VAULT_IMPORT_MAX_BYTES]). The copy is read as a stream into
     * a temporary folder next to the attachments, before taking the mutex and always deleted afterwards. All or
     * nothing: its master password and every attachment (present, with the SHA-256 its entry records) are checked
     * before anything is replaced; if writing the vault then fails, the attachments are put back as they were.
     * The attachments the old vault had and the new one does not are collected after the save.
     */
    suspend fun importReplace(input: InputStream, master: CharArray) {
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
            importText(bytes.decodeToString(), master, since)
            return
        }
        val temp = File(attachments.dir.absoluteFile.parentFile, IMPORT_DIR_PREFIX + randomBytes(8).joinToString("") { "%02x".format(it) })
        try {
            val contents = withContext(io) { BackupReader().read(buffered, temp) }
            val incoming = withContext(compute) { VaultCrypto.decodeFile(contents.vaultJson) }
            replace(incoming, master, contents to temp, since)
        } finally {
            withContext(NonCancellable) { withContext(io) { temp.deleteRecursively() } }
        }
    }

    /**
     * [importReplace]; [backup] is the copy read and its temporary folder, null for a JSON export. [since] is [epoch]
     * when the import started: a lock since then leaves the imported vault locked.
     */
    private suspend fun replace(incoming: VaultFile, master: CharArray, backup: Pair<BackupContents, File>?, since: Int) = mutex.withLock {
        val key = withContext(compute) { VaultCrypto.unlock(incoming, master) }
        // Validates the data before replacing anything, and brings an old export to the current format.
        val (checked, refs) = try {
            val checked = withContext(compute) { VaultCrypto.afterMasterUnlock(incoming, key, master, now(), kdf()) }
            val incomingEntries = withContext(compute) { VaultCrypto.read(checked, key) }
            // Only one identity per vault (see [upsert]): a copy with more is refused before anything changes.
            if (incomingEntries.count { it.type == EntryType.IDENTITY } > 1) throw CryptoFailure(SEVERAL_IDENTITIES)
            checkRestorable(incomingEntries)
            val refs = incomingEntries.flatMap { it.attachments }
            if (backup == null) {
                if (refs.isNotEmpty()) throw CryptoFailure(JSON_HAS_ATTACHMENTS)
            } else {
                withContext(io) { refs.forEach { checkInBackup(it, backup.first) } }
            }
            beforeReplace() // Last: only when all of the copy is fine.
            checked to refs
        } catch (e: Exception) {
            key.fill(0)
            throw e
        }
        withContext(NonCancellable) {
            // Importing with no readable vault (the "Crea tu bóveda" screen): a file that could not be read is kept, as in [create].
            if (vault == null) withContext(io) { keepAside() }
            if (backup == null) {
                commit(checked, key, since)
                onClockReset(now()) // Only once the copy is saved: a failed save lowers nothing.
                return@withContext
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
            onClockReset(now()) // Saved: the copy's master password was verified and it is the vault now.
            opened(checked, key, since)
            collectGarbage() // The old vault's attachments the new one does not use.
        }
    }

    /**
     * What [upsert] and [addAttachment] keep for entries added here holds for a copy too, or it is refused before
     * anything changes: no two entries with the same id, no attachments or password history in an identity or a
     * contact, and the attachments within [MAX_ATTACHMENT_BYTES] each and [MAX_ATTACHMENTS_TOTAL_BYTES] in all.
     * A copy this app made always passes; one written by hand (with its master password) may not.
     */
    private fun checkRestorable(entries: List<VaultEntry>) {
        if (entries.map { it.id }.distinct().size != entries.size) throw CryptoFailure(COPY_DAMAGED)
        if (entries.any { it.type.isPersonal && (it.attachments.isNotEmpty() || it.passwordHistory.isNotEmpty()) }) throw CryptoFailure(COPY_DAMAGED)
        val refs = entries.flatMap { it.attachments }
        if (refs.any { it.size !in 0..MAX_ATTACHMENT_BYTES.toLong() } || attachments.totalBytes(refs) > MAX_ATTACHMENTS_TOTAL_BYTES) {
            throw CryptoFailure(ATTACHMENTS_TOTAL_TOO_BIG)
        }
    }

    /** [ref] has its file in the copy, with the SHA-256 the entry records (authenticated by the copy's data key). */
    private fun checkInBackup(ref: AttachmentRef, contents: BackupContents) {
        val file = contents.attachments[ref.id]
        val limit = MAX_ATTACHMENT_BYTES.toLong() + NONCE_BYTES + TAG_BYTES
        // The file is nonce + content + tag: its length proves the size the entry declares (and [checkRestorable] counts).
        if (!isAttachmentId(ref.id) || file == null || file.length() != ref.size + NONCE_BYTES + TAG_BYTES || !fileMatchesSha256(file, ref.sha256, limit)) {
            throw CryptoFailure("La copia está incompleta o dañada: falta o no coincide el adjunto «${ref.name}»")
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
        lock()
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

/** Largest JSON export read (a real one is a few hundred KB): a huge file must not exhaust memory. */
const val VAULT_IMPORT_MAX_BYTES = 20 * 1024 * 1024

private const val NEEDS_MASTER_FOR_CONTACTS = "Hace falta la contraseña maestra para guardar la identidad o los contactos"
private const val SEVERAL_IDENTITIES = "La copia tiene más de una identidad: no se puede importar"
private const val COPY_DAMAGED = "La copia está dañada: no se puede importar"
private const val REPLACE_NEEDS_OPEN = "Abre la bóveda antes de importar una copia"
private const val ONLY_ONE_IDENTITY = "Solo puede haber una identidad"
private const val IMPORT_TOO_BIG = "El archivo pasa de 20 MB: no es una copia de la bóveda"
private const val JSON_HAS_ATTACHMENTS = "Esta copia tiene adjuntos: importa el archivo .sgbak"
private const val IMPORT_DIR_PREFIX = "import-"
private val IMPORT_DIR = Regex("import-[0-9a-f]{16}")

private fun attachmentUnusable(name: String) = "El adjunto «$name» falta o está dañado: quítalo de su entrada para poder hacer la copia"

/** Longest the vault stays open, with «Al salir», while a picker or the QR scanner we opened is on top. */
const val AWAY_BACKSTOP_MS = 2 * 60_000L

/** Set just before the vault opens a system screen (file picker, QR scanner) so leaving the app for it does not lock. */
object VaultLock {
    @Volatile var keepOpenOnce = false
}
