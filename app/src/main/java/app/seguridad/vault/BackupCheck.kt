package app.seguridad.vault

import app.seguridad.crypto.CryptoFailure
import app.seguridad.crypto.KdfLock
import app.seguridad.crypto.NONCE_BYTES
import app.seguridad.crypto.TAG_BYTES
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.nio.file.Files

/*
 * «Comprobar una copia» (Ajustes, 1.4.0): [checkBackup] tells whether a `seguridad-backup` copy would import, without
 * importing it. The rules on the entries ([checkCopyEntries]) and on each attachment ([attachmentInCopy]) are the ones
 * [VaultStore.importReplace] applies before it replaces anything: both call these functions, so they cannot drift apart.
 */

/** The folder of the app's cache where the checks read their copies, one subfolder per run ([checkBackup]). */
const val BACKUP_CHECK_DIR = "comprobar"

private const val RUN_PREFIX = "run-"

/**
 * What [checkBackup] found: the copy's [version] (1 or 2), how many entries of each type it holds, how many attachments
 * its entries list and how many of them are in the copy as their entry records them, and the [problems] found (texts of
 * BackupCheckTexts.kt, with counts only: never an entry's content, title or attachment name). No problems means the copy
 * passes every check of its contents the import makes: it is a readable `seguridad-backup` within the limits, its
 * master password opens its vault, its entries pass [checkCopyEntries] and every attachment passes [attachmentInCopy].
 * What only the import does is not checked: wrapping the key again under the cost chosen on this phone when the copy's
 * is lower (A8, [VaultCrypto.afterMasterUnlock]: a second Argon2id, which can run out of memory with «Alto»), the state
 * of this phone's vault ([VaultStore.importReplace] needs it open, or none), saving the settings and the space to save.
 */
data class BackupCheckReport(
    val version: Int,
    val entries: Map<EntryType, Int>,
    val attachments: Int,
    val attachmentsOk: Int,
    val problems: List<String>,
)

/**
 * Reads the copy from [input] (left open) into a folder of its own under [tempDir], opens its vault with [master] (which
 * is only read: the caller zeroes it) and checks its entries and every attachment, as [VaultStore.importReplace] does.
 * Throws [CryptoFailure] with the import's own message for what the import would refuse before looking at the
 * attachments: a damaged or cut file, a copy of a newer version, one over the limits, a wrong password, entries the
 * import refuses. A missing or changed attachment is reported, not thrown, together with the others found.
 *
 * Writes nothing but that folder (never the vault, the settings nor a copy), deleted however this ends (cancelled too,
 * also while the folder is being made): two runs at once never share one. Bounded before any work by [BackupReader]'s
 * limits (100 MB in all, 6 MB per file, 2 000 files and [BACKUP_MAX_READ_BYTES] read) and then by the import's
 * ([checkCopyEntries]); cancellable at every read of [input] and between attachments. Argon2id ([unlock], replaceable
 * only by the tests) cannot be cut: once started it runs to its end without cancellation, one at a time in the process
 * ([KdfLock], ruling R38: with the vault's own derivations and those of «Importar de otra app»), so a check stopped
 * during its derivation still holds the lock until it ends and the next derivation waits
 * (BackupCheckTest.afterCancelTheNextDerivationWaits, aCancelledCheckMakesTheUnlockWait); the copy's data key it gives
 * is always zeroed, also when the check was cancelled meanwhile.
 */
suspend fun checkBackup(
    input: InputStream,
    master: CharArray,
    tempDir: File,
    io: CoroutineDispatcher = Dispatchers.IO,
    compute: CoroutineDispatcher = Dispatchers.Default,
    unlock: (VaultFile, CharArray) -> ByteArray = { file, pw -> VaultCrypto.unlock(file, pw) },
): BackupCheckReport {
    // Set inside the non-cancellable blocks, so a cancellation that drops their result never loses the folder or the key.
    var dir: File? = null
    var key: ByteArray? = null
    try {
        withContext(NonCancellable) { withContext(io) { dir = newRunDir(tempDir) } }
        currentCoroutineContext().ensureActive()
        val runDir = dir!!
        val contents = withContext(io) {
            val job = currentCoroutineContext().job
            BackupReader().read(input, runDir) { job.ensureActive() }
        }
        val incoming = withContext(compute) { VaultCrypto.decodeFile(contents.vaultJson) }
        currentCoroutineContext().ensureActive()
        KdfLock.derive(compute) { key = unlock(incoming, master) }
        currentCoroutineContext().ensureActive()
        val dek = key!!
        val entries = withContext(compute) { VaultCrypto.read(incoming, dek) }
        checkCopyEntries(entries)
        val refs = entries.flatMap { it.attachments }
        val found = withContext(io) {
            refs.map {
                currentCoroutineContext().ensureActive()
                attachmentInCopy(it, contents)
            }
        }
        val missing = found.count { it == CopyAttachment.MISSING }
        val changed = found.count { it == CopyAttachment.CHANGED }
        return BackupCheckReport(
            version = contents.version,
            entries = entries.groupingBy { it.type }.eachCount(),
            attachments = refs.size,
            attachmentsOk = found.count { it == CopyAttachment.OK },
            problems = listOfNotNull(
                backupCheckMissing(missing).takeIf { missing > 0 },
                backupCheckChanged(changed).takeIf { changed > 0 },
            ),
        )
    } finally {
        key?.fill(0)
        dir?.let { d -> withContext(NonCancellable) { withContext(io) { endRun(d) } } }
    }
}

/** The run folders in use in this process: [removeBackupCheckLeftovers] never deletes one of them. Guarded by itself. */
private val activeRuns = HashSet<String>()

/** A new, empty folder under [tempDir] that no other run has (created atomically, with a random name), marked in use. */
private fun newRunDir(tempDir: File): File = synchronized(activeRuns) {
    Files.createDirectories(tempDir.toPath())
    Files.createTempDirectory(tempDir.toPath(), RUN_PREFIX).toFile().also { activeRuns += it.absolutePath }
}

private fun endRun(dir: File) {
    try {
        dir.deleteRecursively()
    } finally {
        synchronized(activeRuns) { activeRuns -= dir.absolutePath }
    }
}

/**
 * Deletes what runs cut short by the death of the process left in [tempDir] (called at start), and never the folder of a
 * run going on. Never throws.
 */
fun removeBackupCheckLeftovers(tempDir: File) {
    try {
        val stale = synchronized(activeRuns) { tempDir.listFiles()?.filter { it.absolutePath !in activeRuns }.orEmpty() }
        stale.forEach { it.deleteRecursively() }
    } catch (e: Exception) {
        // Tried again at the next start; the system may also clear the cache.
    }
}

internal const val SEVERAL_IDENTITIES = "La copia tiene más de una identidad: no se puede importar"
internal const val COPY_DAMAGED = "La copia está dañada: no se puede importar"

/** The import's refusal of a copy whose attachment is missing or changed; it names the attachment (only the import says it). */
internal fun attachmentNotInCopy(name: String) = "La copia está incompleta o dañada: falta o no coincide el adjunto «$name»"

/**
 * What [VaultStore.upsert] and [VaultStore.addAttachment] keep for entries added here holds for a copy too, or it is
 * refused (by the import, before anything changes; by [checkBackup], with the same message): only one identity, no two
 * entries with the same id, no attachments or password history in an identity or a contact, and the attachments within
 * [MAX_ATTACHMENT_BYTES] each and [MAX_ATTACHMENTS_TOTAL_BYTES] in all. A copy this app made always passes; one written
 * by hand (with its master password) may not. Bounds the work that follows: the attachments hashed are at most
 * [MAX_ATTACHMENTS_TOTAL_BYTES] in all.
 */
internal fun checkCopyEntries(entries: List<VaultEntry>) {
    if (entries.count { it.type == EntryType.IDENTITY } > 1) throw CryptoFailure(SEVERAL_IDENTITIES)
    // The id goes, encoded, into the screens' routes (AppNavHost): only an id nothing could ever show or route is
    // refused, so a copy of a vault that 1.0 to 1.2.x let in with any other id still restores (LIBRE-1).
    if (entries.any { !isEntryId(it.id) }) throw CryptoFailure(COPY_DAMAGED)
    if (entries.map { it.id }.distinct().size != entries.size) throw CryptoFailure(COPY_DAMAGED)
    if (entries.any { it.type.isPersonal && (it.attachments.isNotEmpty() || it.passwordHistory.isNotEmpty()) }) throw CryptoFailure(COPY_DAMAGED)
    val refs = entries.flatMap { it.attachments }
    if (refs.any { it.size !in 0..MAX_ATTACHMENT_BYTES.toLong() } || refs.sumOf { it.size } > MAX_ATTACHMENTS_TOTAL_BYTES) {
        throw CryptoFailure(ATTACHMENTS_TOTAL_TOO_BIG)
    }
}

/** How an attachment an entry lists is in a copy ([attachmentInCopy]). */
internal enum class CopyAttachment { OK, MISSING, CHANGED }

/**
 * Whether [ref] has its file in [contents], with the SHA-256 the entry records (authenticated by the copy's data key),
 * compared in constant time ([fileMatchesSha256]). The file is nonce + content + tag: its length proves the size the
 * entry declares (and [checkCopyEntries] counts).
 */
internal fun attachmentInCopy(ref: AttachmentRef, contents: BackupContents): CopyAttachment {
    val file = contents.attachments[ref.id]
    if (!isAttachmentId(ref.id) || file == null) return CopyAttachment.MISSING
    val limit = MAX_ATTACHMENT_BYTES.toLong() + NONCE_BYTES + TAG_BYTES
    if (file.length() != ref.size + NONCE_BYTES + TAG_BYTES || !fileMatchesSha256(file, ref.sha256, limit)) return CopyAttachment.CHANGED
    return CopyAttachment.OK
}
