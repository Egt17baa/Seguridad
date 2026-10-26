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
import java.io.File
import app.seguridad.core.writeTextAtomically
import app.seguridad.crypto.CryptoFailure
import app.seguridad.crypto.KdfParams

sealed interface VaultState {
    data object Loading : VaultState
    data object NoVault : VaultState
    data object Locked : VaultState
    data class Unlocked(val entries: List<VaultEntry>) : VaultState
}

/** Biometric unlock refused because the master password was not typed for [MASTER_RECHECK_MS]. */
class MasterRequired : Exception("Escribe tu contraseña maestra (se pide cada 7 días)")

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
    /** Encrypted attachments, in files/vault/att/ next to the vault file. */
    private val attachments: AttachmentStore = AttachmentStore(File(file.absoluteFile.parentFile, "att")),
) {
    private val _state = MutableStateFlow<VaultState>(VaultState.Loading)
    val state: StateFlow<VaultState> = _state.asStateFlow()

    private val mutex = Mutex()
    private var vault: VaultFile? = null
    private var dek: ByteArray? = null
    private var lastUse = 0L

    /**
     * Bumped by every [lock]. [lock] runs on the main thread without the mutex (leaving the app must lock at once),
     * so operations work on their own copy of the key and only reopen the vault if no lock happened meanwhile.
     */
    @Volatile private var epoch = 0

    suspend fun load() = mutex.withLock {
        if (_state.value != VaultState.Loading) return@withLock
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
            save(next)
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
        if (epoch != since) {
            key.fill(0)
            if (_state.value !is VaultState.NoVault) _state.value = VaultState.Locked
            return
        }
        val entries = try {
            VaultCrypto.read(v, key).sortedBy { it.title.lowercase() }
        } catch (e: CryptoFailure) {
            key.fill(0)
            throw e
        }
        if (dek !== key) dek?.fill(0)
        dek = key
        lastUse = now()
        _state.value = VaultState.Unlocked(entries)
    }

    suspend fun create(master: CharArray) = mutex.withLock {
        val since = epoch
        val (v, key) = withContext(compute) { VaultCrypto.create(master, now(), kdf()) }
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
    suspend fun unlock(master: CharArray) = mutex.withLock {
        val v = vault ?: return@withLock
        val since = epoch
        val key = withContext(compute) { VaultCrypto.unlock(v, master) }
        val checked = try {
            withContext(compute) { VaultCrypto.afterMasterUnlock(v, key, master, now(), kdf()) }
        } catch (e: Exception) {
            key.fill(0)
            throw e
        }
        // A full disk must not lock the user out of their passwords: open anyway (see [commit]).
        commit(checked, key, since, mustSave = false)
    }

    val biometricAllowed: Boolean get() = vault?.let { VaultCrypto.biometricAllowed(it, now()) } ?: false

    /** With a data key released by the biometric prompt. Throws [MasterRequired] when the 7-day recheck is due. */
    suspend fun unlockWithKey(key: ByteArray) = mutex.withLock {
        val v = vault ?: return@withLock
        // The real version first: a version-1 file (date not authenticated) needs the master password once.
        val known = try {
            VaultCrypto.resolve(v, key)
        } catch (e: CryptoFailure) {
            key.fill(0)
            throw e
        }
        if (!VaultCrypto.biometricAllowed(known, now())) {
            key.fill(0)
            throw MasterRequired()
        }
        opened(known, key, epoch)
        collectGarbage()
    }

    /** A copy of the open data key, to wrap it with the biometric key; null while locked. */
    fun dataKeyCopy(): ByteArray? = dek?.copyOf()

    fun lock() {
        epoch++
        dek?.fill(0)
        dek = null
        if (vault != null && _state.value is VaultState.Unlocked) _state.value = VaultState.Locked
    }

    fun touch() {
        lastUse = now()
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
            lock()
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

    suspend fun upsert(entry: VaultEntry) = modify { list ->
        val saved = list.firstOrNull { it.id == entry.id }
        if (saved == null) {
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
        list + entries.filter { ids.add(it.id) }.map { it.copy(passwordHistory = emptyList(), attachments = emptyList()) }
    }

    /** Deletes the entry; its attachment files go with the collection after the save. */
    suspend fun delete(id: String) = modify { list -> list.filterNot { it.id == id } }

    /**
     * Encrypts [input] as a new attachment of entry [entryId] and saves the vault. Throws [AttachmentLimit] over
     * [MAX_ATTACHMENT_BYTES], or when the vault's attachments would pass [MAX_ATTACHMENTS_TOTAL_BYTES] (the file is
     * deleted then). False if the vault was locked or the entry no longer exists, so nothing was saved.
     * [input] (maybe a slow or stuck document provider) is read before taking the mutex, so it can never block
     * unlocking or saving. Only encrypting, writing the file and saving the vault run with the mutex held, so no
     * collection can run between writing the file and saving its reference; if the save fails, the next collection
     * deletes it.
     */
    suspend fun addAttachment(entryId: String, input: java.io.InputStream, name: String): Boolean {
        val plain = withContext(io) { attachments.read(input) }
        try {
            return modify { list ->
                val entry = list.firstOrNull { it.id == entryId } ?: return@modify null
                val ref = withContext(io) { attachments.add(plain, name) }
                if (attachments.totalBytes(list.flatMap { it.attachments }) + ref.size > MAX_ATTACHMENTS_TOTAL_BYTES) {
                    withContext(io) { attachments.delete(ref.id) }
                    throw AttachmentLimit(ATTACHMENTS_TOTAL_TOO_BIG)
                }
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

    /** Replaces this vault with an exported one after checking its master password. Throws [CryptoFailure]. */
    suspend fun importReplace(text: String, master: CharArray) = mutex.withLock {
        val since = epoch
        val incoming = withContext(compute) { VaultCrypto.decodeFile(text) }
        val key = withContext(compute) { VaultCrypto.unlock(incoming, master) }
        // Validates the data before replacing anything, and brings an old export to the current format.
        val checked = try {
            withContext(compute) { VaultCrypto.afterMasterUnlock(incoming, key, master, now(), kdf()) }
        } catch (e: Exception) {
            key.fill(0)
            throw e
        }
        // Importing with no readable vault (the "Crea tu bóveda" screen): a file that could not be read is kept, as in [create].
        if (vault == null) withContext(io) { keepAside() }
        commit(checked, key, since)
    }

    /** Deletes the vault for good (forgotten master password). */
    suspend fun destroy() = mutex.withLock {
        lock()
        withContext(io) { file.delete() }
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

/** Longest the vault stays open, with «Al salir», while a picker or the QR scanner we opened is on top. */
const val AWAY_BACKSTOP_MS = 2 * 60_000L

/** Set just before the vault opens a system screen (file picker, QR scanner) so leaving the app for it does not lock. */
object VaultLock {
    @Volatile var keepOpenOnce = false
}
