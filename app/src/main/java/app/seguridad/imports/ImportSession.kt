package app.seguridad.imports

import app.seguridad.tools.SingleRun
import app.seguridad.tools.cipher.GuardedSession
import app.seguridad.tools.codes.CellFactory
import app.seguridad.tools.codes.PlainCells
import app.seguridad.tools.codes.getValue
import app.seguridad.tools.codes.setValue
import app.seguridad.vault.EntryType
import app.seguridad.vault.IMPORT_COPY_PREFIX
import app.seguridad.vault.VaultEntry
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** The steps of «Importar de otra app» (spec «Interfaz», «Pasos»). */
sealed interface ImportStep {
    /** 1. Choose the file. */
    data object Pick : ImportStep

    /** Reading the file, trying its password or building the plan: «Cancelar» stops it. */
    data object Reading : ImportStep

    /** 2. The password of an encrypted file of [format]. */
    data class Password(val format: ImportFormat) : ImportStep

    /** 3. The preview, with the choice for each duplicate. */
    data object Preview : ImportStep

    /**
     * 4. Confirmed: the copy before importing, then the save. Leaving the screen does not cut it. A lock before the save
     * starts cancels it (ruling R37): a lock during the copy refuses the copy, and a lock between the copy and the save
     * gives [ApplyResult.Stale], so nothing is imported and the copy, if made, stays. A save already started finishes.
     */
    data object Saving : ImportStep

    /** 4, without a folder for the automatic copies: the screen asks where to save the copy (`CREATE_DOCUMENT`). */
    data object ChooseCopyPlace : ImportStep

    /** 5. Imported: [added] new entries and [replaced] entries changed; the copy is [where] (a name). */
    data class Done(val added: Int, val replaced: Int, val where: String, val inAutoFolder: Boolean) : ImportStep

    /** The save failed after the copy was made ([message], a fixed text): nothing more can be done with this file here. */
    data class Ended(val message: String) : ImportStep
}

/** «Quitar el archivo exportado», after an import. */
enum class RemoveState { OFFERED, REMOVING, REMOVED, FAILED, UNSUPPORTED, DECLINED }

/** How deleting the exported file went: [UNSUPPORTED] when its provider does not delete (or it is not a document). */
enum class RemoveResult { REMOVED, FAILED, UNSUPPORTED }

/**
 * One password attempt at a time in the whole app (spec «Todo lector», «Nunca corren dos cálculos a la vez»): the KDF of
 * an encrypted export (Task 10) cannot be cut, so a new attempt waits for the one before to end.
 */
val IMPORT_KDF_RUN = SingleRun()

/** The name proposed for the copy before importing when it is saved by hand: the same as in the automatic folder. */
fun importCopyFileName(at: LocalDateTime): String = IMPORT_COPY_PREFIX + COPY_STAMP.format(at) + ".sgbak"

private val COPY_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

/**
 * What the screen «Importar de otra app» holds (on Android, its ViewModel's state, never saved state): the bytes read
 * from the file (once, owner's decision (b)), the password typed, the plan and the choices, and the result. The read
 * bytes are zeroed as soon as a reader is done with them, and by any wipe; the password leaves the field as a CharArray
 * the caller zeroes ([takePassword]). [wipe] (back, the «PIN y códigos» rule of [guard]) and [vaultStateIs] with a locked
 * vault (any lock, owner's rule for the import) drop all of it, and [onWipe] stops what runs.
 *
 * Every result is given with the run it belongs to ([begin], [startSaving]): one that arrives after a wipe, a lock, a
 * new pick or another step is dropped. The steps only move as the spec says, so a second tap does nothing.
 */
class ImportSession(
    cells: CellFactory = PlainCells,
    createdAt: Long = 0L,
    private val onWipe: () -> Unit = {},
) : GuardedSession(createdAt) {
    private val stepCell = cells.cell<ImportStep>(ImportStep.Pick)
    var step: ImportStep by stepCell
        private set
    private val errorCell = cells.cell<String?>(null)
    var error: String? by errorCell
        private set
    private val fileNameCell = cells.cell<String?>(null)

    /** The chosen file's name (sanitised by the caller), shown through plainReportText. */
    var fileName: String? by fileNameCell
    private val passwordCell = cells.cell("")

    /** The password field (a String, what Compose edits); emptied when the attempt starts and by any wipe. */
    var password: String by passwordCell
    private val planCell = cells.cell<MergePlan?>(null)
    var plan: MergePlan? by planCell
        private set
    private val defaultCell = cells.cell(DuplicateChoice.SKIP)

    /** The option for all duplicates: «Saltar» unless the user changes it (owner's decision 3). */
    var default: DuplicateChoice by defaultCell
        private set
    private val overridesCell = cells.cell<Map<Int, DuplicateChoice>>(emptyMap())

    /** Each duplicate's own choice, by [ImportedItem.index] (never its position in [MergePlan.duplicates]). */
    var overrides: Map<Int, DuplicateChoice> by overridesCell
        private set
    private val skippedCell = cells.cell<List<Skipped>>(emptyList())

    /** «No entran», kept for the result once the plan is dropped: titles and reasons only. */
    var skipped: List<Skipped> by skippedCell
        private set
    private val overBytesCell = cells.cell<Long?>(null)

    /** The estimate for the choices shown ([estimated]): how much the vault would pass a copy's limit; null until known. */
    var overBytes: Long? by overBytesCell
        private set
    private val newTagsCell = cells.cell<Set<String>?>(null)

    /** The tags the import would create with the choices shown, from the same estimate ([mergeImport]); null until known. */
    var newTags: Set<String>? by newTagsCell
        private set
    private val foldersNotTagsCell = cells.cell(0)

    /** How many new folder tags [IMPORT_MAX_NEW_TAGS] leaves out with the choices shown (ruling R18). */
    var foldersNotTags: Int by foldersNotTagsCell
        private set
    private val removalCell = cells.cell<RemoveState?>(null)
    var removal: RemoveState? by removalCell
        private set

    /** The current run; every start and every wipe moves it, so older results are dropped. */
    private var run = 0L
    private var bytes: ByteArray? = null
    private var source: Any? = null
    private var removable: Any? = null

    /** The format of the password attempt being worked out, or null when none is. */
    private var attempting: ImportFormat? = null

    /** The vault's entries the plan was built against (its snapshot): for the estimate, never shown. */
    var existing: List<VaultEntry> = emptyList()
        private set

    /** Something runs that the user waits for (and can cancel while [ImportStep.Reading]). */
    val busy: Boolean get() = step == ImportStep.Reading || step == ImportStep.Saving

    fun isRun(run: Long): Boolean = run == this.run

    /** A file was chosen ([source], the document): what was shown before goes, and a new read starts. */
    fun begin(source: Any): Long {
        dropAll()
        this.source = source
        step = ImportStep.Reading
        return run
    }

    /** The bytes of [run]'s file, read once: kept for a password attempt; false (and zeroed) if the run is over. */
    fun hold(run: Long, bytes: ByteArray): Boolean {
        if (!isRun(run) || step != ImportStep.Reading || this.bytes != null) {
            bytes.fill(0)
            return false
        }
        this.bytes = bytes
        return true
    }

    /** The bytes held for [run]; null once a reader is done with them or the run is over. */
    fun bytesFor(run: Long): ByteArray? = if (isRun(run)) bytes else null

    /** The file could not be read at all ([message]: a fixed text). */
    fun readFailed(run: Long, message: String) {
        if (!isRun(run)) return
        dropAll()
        error = message
    }

    /**
     * The password typed, as a CharArray the caller owns and zeroes: only at the password step and with something typed.
     * The field empties at once. Each attempt is the user's own (ruling R13: nothing is tried again on its own); after a
     * wrong one the step comes back for another ([onRead]).
     */
    fun takePassword(): Pair<Long, CharArray>? {
        val at = step as? ImportStep.Password ?: return null
        if (password.isEmpty() || bytes == null) return null
        val pw = password.toCharArray()
        password = ""
        attempting = at.format
        error = null
        step = ImportStep.Reading
        return run to pw
    }

    /**
     * What [readImport] said for [run] (null: it ran out of memory, ruling R11b). [ReadResult.Ok] gives its read, for the
     * plan; everything else returns null. The bytes are zeroed unless a password is still to be tried:
     * - [ReadResult.NeedsPassword] without an attempt goes to the password step; after one it means a reader that asks
     *   again, which is never looped on: [IMPORT_BROKEN], and the file is dropped;
     * - [IMPORT_WRONG_PASSWORD] after an attempt goes back to the password step with that text and the bytes kept, so
     *   the user can type it again; any other failure drops the file with its text.
     */
    fun onRead(run: Long, result: ReadResult?): ImportRead? {
        if (!isRun(run) || step != ImportStep.Reading) return null
        when (result) {
            is ReadResult.Ok -> {
                zeroBytes()
                return result.read
            }
            is ReadResult.NeedsPassword ->
                if (attempting != null) {
                    dropAll()
                    error = IMPORT_BROKEN
                } else {
                    step = ImportStep.Password(result.format)
                }
            is ReadResult.Failed -> {
                val format = attempting
                if (format != null && result.message == IMPORT_WRONG_PASSWORD) {
                    attempting = null
                    step = ImportStep.Password(format)
                    error = result.message
                } else {
                    dropAll()
                    error = result.message
                }
            }
            null -> {
                dropAll()
                error = IMPORT_JSON_TOO_MANY_VALUES
            }
        }
        return null
    }

    /** The plan of [run] against [existing] (VaultStore.importSnapshot); null when the vault locked meanwhile. */
    fun onPlan(run: Long, plan: MergePlan?, existing: List<VaultEntry>) {
        if (!isRun(run) || step != ImportStep.Reading) return
        if (plan == null) return dropAll()
        this.plan = plan
        this.existing = existing
        skipped = plan.skipped
        default = DuplicateChoice.SKIP
        overrides = emptyMap()
        overBytes = null
        step = ImportStep.Preview
    }

    /** «Cancelar» while reading: the read stops (its result is dropped) and nothing is held. */
    fun cancel() {
        if (step == ImportStep.Reading || step is ImportStep.Password) dropAll()
    }

    /** The choice that applies to the duplicate [index]. */
    fun choiceOf(index: Int): DuplicateChoice = overrides[index] ?: default

    /** The option for all: every duplicate follows it (the choices made one by one go). */
    fun chooseAll(choice: DuplicateChoice) {
        if (step != ImportStep.Preview) return
        // I-1: the chip already chosen changes no state, so no new estimate would come: nothing is reset.
        if (choice == default && overrides.isEmpty()) return
        default = choice
        overrides = emptyMap()
        clearEstimate()
    }

    /** The choice for the duplicate [index] alone; an index that is not a duplicate's changes nothing. */
    fun choose(index: Int, choice: DuplicateChoice) {
        val p = plan ?: return
        if (step != ImportStep.Preview || p.duplicates.none { it.item.index == index }) return
        val next = if (choice == default) overrides - index else overrides + (index to choice)
        if (next == overrides) return // I-1, as in chooseAll.
        overrides = next
        clearEstimate()
    }

    private fun clearEstimate() {
        overBytes = null
        newTags = null
        foldersNotTags = 0
        error = null
    }

    /** The [estimate] for [plan] with [default] and [overrides]; kept only if those are still the ones shown. */
    fun estimated(plan: MergePlan, default: DuplicateChoice, overrides: Map<Int, DuplicateChoice>, estimate: PreviewEstimate) {
        if (step != ImportStep.Preview || plan !== this.plan || default != this.default || overrides != this.overrides) return
        overBytes = estimate.overBytes
        newTags = estimate.newTags
        foldersNotTags = estimate.foldersNotTags
    }

    /** What the preview counts for the choices shown. */
    val counts: PreviewCounts? get() = plan?.let { previewCounts(it, default, overrides) }

    /** «Importar» can be tapped: the preview, the estimate of these choices fits, and something enters or changes. */
    val canConfirm: Boolean
        get() {
            val c = counts ?: return false
            return step == ImportStep.Preview && overBytes == 0L && (c.addedTotal > 0 || c.replaced > 0)
        }

    /** «Importar»: the run that confirms, once; null when it cannot ([canConfirm]) or already did. */
    fun startSaving(): Long? {
        if (!canConfirm) return null
        error = null
        step = ImportStep.Saving
        return run
    }

    /** No folder for the automatic copies: the user is asked where to save the copy. */
    fun needsCopyPlace(run: Long) {
        if (isRun(run) && step == ImportStep.Saving) step = ImportStep.ChooseCopyPlace
    }

    /** The place was chosen: the run that saves, once; null if it was not asked for (then the document is discarded). */
    fun copyPlaceChosen(): Long? {
        if (step != ImportStep.ChooseCopyPlace) return null
        step = ImportStep.Saving
        return run
    }

    /** The save dialog was cancelled: no copy, so nothing is imported; back to the preview. */
    fun copyPlaceCancelled() {
        if (step != ImportStep.ChooseCopyPlace) return
        step = ImportStep.Preview
        error = IMPORT_COPY_CANCELLED
    }

    /** How «Importar» of [run] ended ([inAutoFolder]: the copy went to the automatic copies' folder). */
    fun onOutcome(run: Long, outcome: ImportOutcome, inAutoFolder: Boolean) {
        if (!isRun(run) || step != ImportStep.Saving) return
        when (outcome) {
            is ImportOutcome.Imported -> {
                val kept = skipped
                val doc = source
                dropAll()
                skipped = kept
                removable = doc
                removal = RemoveState.OFFERED
                step = ImportStep.Done(outcome.done.added, outcome.done.replaced, outcome.where, inAutoFolder)
            }
            is ImportOutcome.CopyFailed -> {
                step = ImportStep.Preview
                error = outcome.message
            }
            is ImportOutcome.NotApplied -> when (val r = outcome.result) {
                is ApplyResult.TooBig -> {
                    step = ImportStep.Preview
                    error = importTooBigText(r.overBytes, r.overFiles)
                    // M-2: the exact check said no: «Importar» stays off until a choice changes and a new estimate comes.
                    overBytes = maxOf(1L, r.overBytes)
                }
                // A Done here would be ImportFlow's mistake: treated as a plan that no longer holds.
                ApplyResult.Stale, is ApplyResult.Done -> {
                    dropAll()
                    error = IMPORT_VAULT_CHANGED
                }
            }
            is ImportOutcome.SaveFailed -> {
                dropAll()
                step = ImportStep.Ended(outcome.message)
            }
        }
    }

    /**
     * The document to delete for «Quitar el archivo exportado»: the one chosen in this run, only after an import, only
     * once (owner's decision (b)); null otherwise.
     */
    fun takeRemovable(): Any? {
        if (step !is ImportStep.Done || removal != RemoveState.OFFERED) return null
        val doc = removable ?: return null
        removable = null
        removal = RemoveState.REMOVING
        return doc
    }

    fun onRemoved(result: RemoveResult) {
        if (removal != RemoveState.REMOVING) return
        removal = when (result) {
            RemoveResult.REMOVED -> RemoveState.REMOVED
            RemoveResult.FAILED -> RemoveState.FAILED
            RemoveResult.UNSUPPORTED -> RemoveState.UNSUPPORTED
        }
    }

    /** «No, déjalo»: never asked again for this file. */
    fun declineRemoval() {
        if (removal != RemoveState.OFFERED) return
        removable = null
        removal = RemoveState.DECLINED
    }

    /** Any lock of the vault drops everything read (spec «Bloqueo»): an import must start again. */
    fun vaultStateIs(unlocked: Boolean) {
        if (!unlocked) wipe()
    }

    override fun forget() {
        val wasSaving = step == ImportStep.Saving
        onWipe()
        dropAll()
        // M-4: the import goes on (leaving does not cut it) and, after a lock, is cancelled unless its save had started
        // (ruling R37); either way its result will not be shown here, and the screen says it may have been done.
        if (wasSaving) error = IMPORT_WIPED_WHILE_SAVING
    }

    /** Back to the first step with nothing held: the bytes zeroed, every reference dropped, the run moved on. */
    private fun dropAll() {
        run++
        zeroBytes()
        source = null
        removable = null
        attempting = null
        password = ""
        fileName = null
        plan = null
        existing = emptyList()
        newTags = null
        foldersNotTags = 0
        default = DuplicateChoice.SKIP
        overrides = emptyMap()
        skipped = emptyList()
        overBytes = null
        removal = null
        error = null
        step = ImportStep.Pick
    }

    private fun zeroBytes() {
        bytes?.fill(0)
        bytes = null
    }
}

/** What the preview counts: the entries [added] by type (new ones and duplicates kept), and how many are [replaced]. */
data class PreviewCounts(val added: Map<EntryType, Int>, val replaced: Int, val skippedDuplicates: Int) {
    val addedTotal: Int get() = added.values.sum()
}

private fun choice(d: Duplicate, default: DuplicateChoice, overrides: Map<Int, DuplicateChoice>) = overrides[d.item.index] ?: default

fun previewCounts(plan: MergePlan, default: DuplicateChoice, overrides: Map<Int, DuplicateChoice>): PreviewCounts {
    val added = HashMap<EntryType, Int>()
    for (item in plan.newItems) added.merge(item.entry.type, 1, Int::plus)
    var replaced = 0
    var skippedDuplicates = 0
    for (d in plan.duplicates) {
        when (choice(d, default, overrides)) {
            DuplicateChoice.KEEP_BOTH -> added.merge(d.item.entry.type, 1, Int::plus)
            DuplicateChoice.REPLACE -> replaced++
            DuplicateChoice.SKIP -> skippedDuplicates++
        }
    }
    return PreviewCounts(added, replaced, skippedDuplicates)
}

/**
 * What the preview shows of the vault after the import with some choices: how much it passes a copy's limit
 * ([estimatedOverBytes], 0 when it fits), the tags it creates (null when unknown: then the preview names none and says
 * nothing about them) and the folders left without one (ruling R18; M-1: from
 * the merged entries themselves, so a replaced entry already at 10 tags adds none).
 */
data class PreviewEstimate(val overBytes: Long, val newTags: Set<String>?, val foldersNotTags: Int)

/**
 * The [PreviewEstimate] of the import with these choices, against [existing] (the snapshot the plan was built on), off
 * the main thread. The ids are placeholders of a UUID's length; the save's exact check decides.
 */
fun previewEstimate(plan: MergePlan, default: DuplicateChoice, overrides: Map<Int, DuplicateChoice>, existing: List<VaultEntry>, now: Long): PreviewEstimate {
    val r = mergeImport(plan, default, overrides, existing, now, { PLACEHOLDER_ID })
    return PreviewEstimate(estimatedOverBytes(r.entries), r.newTags, r.foldersNotTags)
}

private const val PLACEHOLDER_ID = "00000000-0000-0000-0000-000000000000"
