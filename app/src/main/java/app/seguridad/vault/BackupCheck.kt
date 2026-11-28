package app.seguridad.vault

import app.seguridad.crypto.CryptoFailure
import app.seguridad.crypto.KdfLock
import app.seguridad.crypto.NONCE_BYTES
import app.seguridad.crypto.TAG_BYTES
import app.seguridad.imports.plainReportText
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
 * importing it. The checks of the data ([checkCopyData]: structure, size and entries, in that order) and of each
 * attachment ([attachmentInCopy]) are the ones [VaultStore.importReplace] applies before it replaces anything, on the
 * same data: the copy's data as they are imported ([VaultCrypto.readAsImported]: one of before 2.0 upgraded to v4 and
 * fitted). Both call these functions, so they cannot drift apart (R5; round 2, point 8).
 */

/** The folder of the app's cache where the checks read their copies, one subfolder per run ([checkBackup]). */
const val BACKUP_CHECK_DIR = "comprobar"

private const val RUN_PREFIX = "run-"

/**
 * What [checkBackup] found: the copy's [version] (1, 2 or 3, its marker), how many entries of each type it holds, how many attachments
 * its entries list and how many of them are in the copy as their entry records them, and the [problems] found (texts of
 * BackupCheckTexts.kt, with counts only: never an entry's content, title or attachment name). No problems means the copy
 * passes every check of its contents the import makes: it is a readable `seguridad-backup` within the limits, its
 * master password opens its vault, its data as they are imported ([VaultCrypto.readAsImported]) pass [checkCopyData]
 * (structure, size and entries, the import's order) and every attachment passes [attachmentInCopy].
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
    /**
     * 2.0, task 8: the copy's [VaultData.vaultTag] as read from its data (decision (b): what was checked is what is
     * recorded); never the one the upgrade to v4 makes for a copy of before 2.0 (round 2, point 8), which has none: so it
     * is empty, and no date of a check is kept for it ([withBackupChecked]).
     */
    val vaultTag: String = "",
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
 * is always zeroed, also when the check was cancelled meanwhile. *
 * Round 3 of the fixes of the attack, named risk «la lectura de una copia no se corta a medias» (accepted by the director,
 * advisor-t12-redteam-fixes-3.md, RT-29): decrypting, decoding, bringing to v4, fitting and checking the copy's data run
 * to their end once started (one `decodeFromString`; a cancellation is thrown as the block returns, so no late result is
 * ever delivered). Bounded by the copy (33 MiB a file, COPY_TREE_MAX_BYTES, 24 MiB + 4 KiB of data) and, since
 * `fittedHistory` is linear, measured at about twice the read of the same copy without old passwords (RedTeam20Recheck2Test.rt29_).
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
        // Informative 3 of round 2: reading the copy and decoding its file, each in its protected block (COPY_NO_MEMORY;
        // the folder goes in the finally).
        val contents = withContext(io) {
            val job = currentCoroutineContext().job
            copyRead(CopyReadStep.READ) { BackupReader().read(input, runDir) { job.ensureActive() } }
        }
        val incoming = withContext(compute) { copyRead(CopyReadStep.DECODE_FILE) { VaultCrypto.decodeFile(contents.vaultJson) } }
        currentCoroutineContext().ensureActive()
        KdfLock.derive(compute) { key = unlock(incoming, master) }
        currentCoroutineContext().ensureActive()
        val dek = key!!
        // RT-21, part c: out of memory reading the copy (the last defence): nothing changed, the folder goes.
        // Round 2, point 8 (R5): what «Importar copia» would seal (a copy of before 2.0 upgraded to v4 and fitted), with
        // its checks in its order ([checkCopyData]), decoded once (RT-21, part b).
        val opened = try {
            withContext(compute) { VaultCrypto.readAsImported(incoming, dek, copy = true).also { checkCopyData(it.imported) } }
        } catch (e: OutOfMemoryError) {
            throw CryptoFailure(COPY_NO_MEMORY)
        }
        val data = opened.imported
        val entries = data.entries
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
            // The tag as read: never the one the upgrade makes for a copy of before 2.0, which has none (withBackupChecked).
            vaultTag = opened.read.vaultTag,
        )
    } finally {
        key?.fill(0)
        dir?.let { d -> withContext(NonCancellable) { withContext(io) { endRun(d) } } }
    }
}

/**
 * 2.0, task 8: these data after «Comprobar una copia» checked, at [now], a copy whose tag is [copyTag]: the date is kept
 * only when [copyTag] is this vault's own [VaultData.vaultTag] (a copy of this vault, or of the vault it was restored
 * from); null (nothing to save) for a copy of another vault, or one made before 2.0, whose tag is empty.
 */
fun VaultData.withBackupChecked(copyTag: String, now: Long): VaultData? =
    if (copyTag.isNotEmpty() && vaultTag.isNotEmpty() && copyTag == vaultTag) copy(lastCheckedBackupAt = now) else null

/** The days after which [backupCheckNotice] reminds to check a copy. */
const val BACKUP_CHECK_REMIND_DAYS = 30

/**
 * 2.0, task 8: the line in Ajustes and in the vault: null until [BACKUP_CHECK_REMIND_DAYS] days have passed since the last
 * check of a copy of this vault ([VaultData.lastCheckedBackupAt], 0 for never); then how many days ago it was
 * («Comprobaste una copia por última vez hace N días.», D-8), or that none was ever checked. A date more than a day ahead
 * of [now] (checked with the clock set forward) cannot be counted from: [BACKUP_CHECK_DATE_AHEAD] (review M-6).
 */
fun backupCheckNotice(lastCheckedBackupAt: Long, now: Long): String? {
    if (lastCheckedBackupAt <= 0L) return BACKUP_CHECK_NEVER
    if (lastCheckedBackupAt - now > DAY_MS) return BACKUP_CHECK_DATE_AHEAD
    val days = (now - lastCheckedBackupAt) / DAY_MS
    return if (days >= BACKUP_CHECK_REMIND_DAYS) backupCheckDaysAgo(days) else null
}

private const val DAY_MS = 24L * 60 * 60 * 1000

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
internal const val COPY_DAMAGED = "La copia está dañada: no se puede importar. Elige otra copia."

/**
 * The import's refusal of a copy whose attachment is missing or changed; it names the attachment (only the import says it).
 * 2.1.2, R-20: the name comes from the copy (hostile data), so it is shown as plain text ([plainReportText]: no controls,
 * direction marks or invisibles) and cut to [ATTACHMENT_NAME_SHOWN] UTF-16 units.
 */
internal fun attachmentNotInCopy(name: String) =
    "La copia está incompleta o dañada: falta o no coincide el adjunto «${plainReportText(name, ATTACHMENT_NAME_SHOWN)}»"

/** 2.1.2, R-20: how much of an attachment's name [attachmentNotInCopy] shows, as received names are cut (A16b). */
private const val ATTACHMENT_NAME_SHOWN = 120

/**
 * Round 2 of the fixes of the attack, point 8 (R5): every check of a copy's data, as «Importar copia» runs them
 * (VaultStore.replace, in `prepare`) and «Comprobar una copia» too ([checkBackup]), in one order: the structure
 * ([checkCopyStructure]), the size ([checkCopySize]) and the entries ([checkCopyEntries]). On the data as they are
 * imported ([VaultCrypto.readAsImported]): the two always say the same of a copy, with the same message.
 */
internal fun checkCopyData(data: VaultData) {
    checkCopyStructure(data)
    checkCopySize(data) // Branch review, A m-2.
    checkCopyEntries(data.entries)
}

/**
 * RT-25, part b (ruling of round 2 of the fixes of the attack, as the director corrected it): the tags a copy has over
 * what the screens keep, summed over every entry (the trash's too) and every version of their history: for each,
 * `max(0, tags.size − MAX_TAGS)`. [checkCopyEntries] refuses a copy with more than [COPY_LIST_MAX] of them as
 * [COPY_DAMAGED], so the tags a copy can bring past the ones each entry or version may hold are at most [COPY_LIST_MAX]
 * in all, whatever its size: what showing them costs (the list's search index and its chips, RT-25) is bounded by a
 * constant, not by the copy.
 *
 * Why an excess, and not a cap that grows with the entries and versions (`MAX_TAGS × (entries + versions)`, the first
 * ruling): versions and entries of the trash could raise such a cap, and both go after the import (the history is cut to
 * [VaultLimits.HISTORY_MAX_VERSIONS] at each save, VaultLimits.kt `fittedHistory`; the trash is emptied after 30 days,
 * Trash.kt): the vault would then hold more than its own copies may, and they would be refused without a word. Here each
 * entry and version only ever adds to the sum, never to the allowance.
 *
 * - The screens never write more than [MAX_TAGS] to an entry or a version (EntryQuery.kt `normalizeTags`, Selection.kt,
 *   MergePlan.kt), so a vault made with them sums 0, at any size.
 * - A copy of 1.x with a few dozen tags written by hand in some entries passes: only what is over 10 counts.
 * - The accepted cost: a copy made by hand with more than 10 000 tags over 10 in its entries is refused, also when it is
 *   the copy of a vault of one's own that opens (the vault's own file has no such bound, RT-21 ruling).
 *
 * One pass, no set: it stops as soon as the sum passes [stopAbove] (the tests count it all).
 */
internal fun copyExtraTags(entries: List<VaultEntry>, stopAbove: Long = COPY_LIST_MAX.toLong()): Long {
    var extra = 0L
    for (e in entries) {
        extra += maxOf(0, e.tags.size - MAX_TAGS)
        for (v in e.history) extra += maxOf(0, v.entry.tags.size - MAX_TAGS)
        if (extra > stopAbove) return extra
    }
    return extra
}

/**
 * What [VaultStore.upsert] and [VaultStore.addAttachment] keep for entries added here holds for a copy too, or it is
 * refused (by the import, before anything changes; by [checkBackup], with the same message): only one identity alive, no two
 * entries with the same id, no attachments or password history in an identity or a contact, the fields within their
 * limits and without an id twice (2.0, [fieldsDamaged]), and the attachments within
 * [MAX_ATTACHMENT_BYTES] each and [MAX_ATTACHMENTS_TOTAL_BYTES] in all. A copy this app made always passes; one written
 * by hand (with its master password) may not. Bounds the work that follows: the attachments hashed are at most
 * [MAX_ATTACHMENTS_TOTAL_BYTES] in all.
 */
internal fun checkCopyEntries(entries: List<VaultEntry>) {
    // RT-25, part b: the tags over what the screens keep, before anything is built from them (the list's index, its chips).
    if (copyExtraTags(entries) > COPY_LIST_MAX) throw CryptoFailure(COPY_DAMAGED)
    // Branch review, D-1: only the identities alive count, as for [VaultStore.upsert]; one in the trash (a copy brings it
    // back) and another made after is a vault the app saves, so its own copy must pass.
    if (entries.count { it.type == EntryType.IDENTITY && it.deletedAt == null } > 1) throw CryptoFailure(SEVERAL_IDENTITIES)
    // The id goes, encoded, into the screens' routes (AppNavHost): only an id nothing could ever show or route is
    // refused, so a copy of a vault that 1.0 to 1.2.x let in with any other id still restores (LIBRE-1).
    if (entries.any { !isEntryId(it.id) }) throw CryptoFailure(COPY_DAMAGED)
    if (entries.map { it.id }.distinct().size != entries.size) throw CryptoFailure(COPY_DAMAGED)
    if (entries.any { it.type.isPersonal && (it.attachments.isNotEmpty() || it.passwordHistory.isNotEmpty()) }) throw CryptoFailure(COPY_DAMAGED)
    // 2.0, task 5 (review I-1 and I-2): the fields as the app writes them, in the entry and in every version of its history.
    if (entries.any { e -> e.fieldsDamaged() || e.history.any { it.entry.fieldsDamaged() } }) throw CryptoFailure(COPY_DAMAGED)
    val refs = entries.flatMap { it.attachments }
    // Branch review, row 37: a size no version of Clavero ever wrote (negative or over 5 MB) is a damaged copy; only a
    // sum over 50 MB is said as such.
    if (refs.any { it.size !in 0..MAX_ATTACHMENT_BYTES.toLong() }) throw CryptoFailure(COPY_DAMAGED)
    if (refs.sumOf { it.size } > MAX_ATTACHMENTS_TOTAL_BYTES) throw CryptoFailure(ATTACHMENTS_TOTAL_TOO_BIG)
}

/**
 * 2.0: the folders, views and tag colours of a copy within [VaultLimits] ([checkStructure]: no folder inside itself), and
 * their names within [ORGANIZE_NAME_MAX] (branch review, A m-4), or the copy is refused as damaged, before anything changes. A copy of 1.4.3 or before has none, and always passes.
 * Round 3 of the fixes of the attack, row 63: a [VaultData.vaultTag] that is not empty must be one the app writes
 * ([isVaultTag]: 32 ASCII `0-9a-f`), or the copy is damaged; so its size never depends on a tag the app would replace.
 */
internal fun checkCopyStructure(data: VaultData) {
    try {
        data.checkStructure()
    } catch (e: VaultLimitExceeded) {
        throw CryptoFailure(COPY_DAMAGED)
    }
    // Branch review, A m-4: the app never names a folder or a view with more than [ORGANIZE_NAME_MAX] code points.
    fun long(name: String) = name.codePointCount(0, name.length) > ORGANIZE_NAME_MAX
    if (data.folders.any { long(it.name) } || data.views.any { long(it.name) }) throw CryptoFailure(COPY_DAMAGED)
    // Round 3, row 63: a tag the app never writes (only [isVaultTag]; a copy of before 2.0 has none, and gets one when upgraded).
    if (data.vaultTag.isNotEmpty() && !isVaultTag(data.vaultTag)) throw CryptoFailure(COPY_DAMAGED)
}

/**
 * Branch review, A m-2: the data of a copy over [VaultLimits.VAULT_MAX_DATA_BYTES] (as the import holds them, an old copy
 * already upgraded and fitted) are a damaged copy: no vault of Clavero passes it, and one that did could not be saved
 * again (every save would refuse it).
 */
internal fun checkCopySize(data: VaultData) {
    // Re-review B i-2: with the margin of the bookkeeping writes, which no save fits (a vault at the limit, opened).
    if (jsonBytes(data) > VaultLimits.VAULT_MAX_DATA_BYTES + VaultLimits.COPY_DATA_MARGIN_BYTES) throw CryptoFailure(COPY_DAMAGED)
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
