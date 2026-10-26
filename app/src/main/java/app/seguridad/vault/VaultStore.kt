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
            if (mustSave) changed()
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
     * whose entries have attachments is refused (its files are only in the .sgbak copy) and nothing changes.
     */
    suspend fun importReplace(text: String, master: CharArray) {
        val incoming = withContext(compute) { VaultCrypto.decodeFile(text) }
        replace(incoming, master, null)
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
        val buffered = BufferedInputStream(input)
        val head = withContext(io) {
            buffered.mark(BACKUP_HEAD_BYTES)
            buffered.readAtMost(BACKUP_HEAD_BYTES).also { buffered.reset() }
        }
        if (!isBackup(head)) {
            val bytes = withContext(io) { buffered.readAtMost(VAULT_IMPORT_MAX_BYTES + 1) }
            if (bytes.size > VAULT_IMPORT_MAX_BYTES) throw CryptoFailure(IMPORT_TOO_BIG)
            importReplace(bytes.decodeToString(), master)
            return
        }
        val temp = File(attachments.dir.absoluteFile.parentFile, IMPORT_DIR_PREFIX + randomBytes(8).joinToString("") { "%02x".format(it) })
        try {
            val contents = withContext(io) { BackupReader().read(buffered, temp) }
            val incoming = withContext(compute) { VaultCrypto.decodeFile(contents.vaultJson) }
            replace(incoming, master, contents to temp)
        } finally {
            withContext(NonCancellable) { withContext(io) { temp.deleteRecursively() } }
        }
    }

    /** [importReplace]; [backup] is the copy read and its temporary folder, null for a JSON export. */
    private suspend fun replace(incoming: VaultFile, master: CharArray, backup: Pair<BackupContents, File>?) = mutex.withLock {
        val since = epoch
        val key = withContext(compute) { VaultCrypto.unlock(incoming, master) }
        // Validates the data before replacing anything, and brings an old export to the current format.
        val (checked, refs) = try {
            val checked = withContext(compute) { VaultCrypto.afterMasterUnlock(incoming, key, master, now(), kdf()) }
            val refs = withContext(compute) { VaultCrypto.read(checked, key) }.flatMap { it.attachments }
            if (backup == null) {
                if (refs.isNotEmpty()) throw CryptoFailure(JSON_HAS_ATTACHMENTS)
            } else {
                withContext(io) { refs.forEach { checkInBackup(it, backup.first) } }
            }
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
            opened(checked, key, since)
            collectGarbage() // The old vault's attachments the new one does not use.
        }
    }

    /** [ref] has its file in the copy, with the SHA-256 the entry records (authenticated by the copy's data key). */
    private fun checkInBackup(ref: AttachmentRef, contents: BackupContents) {
        val file = contents.attachments[ref.id]
        val limit = MAX_ATTACHMENT_BYTES.toLong() + NONCE_BYTES + TAG_BYTES
        if (!isAttachmentId(ref.id) || file == null || !fileMatchesSha256(file, ref.sha256, limit)) {
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

/** Largest JSON export read (a real one is a few hundred KB): a huge file must not exhaust memory. */
const val VAULT_IMPORT_MAX_BYTES = 20 * 1024 * 1024

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
