package app.seguridad.tools.codes

import app.seguridad.vault.VaultEntry
import kotlin.reflect.KProperty

/** A value a screen observes: on Android a Compose state (so changes redraw it), in tests a plain field. */
interface Cell<T> {
    var value: T
}

operator fun <T> Cell<T>.getValue(thisRef: Any?, property: KProperty<*>): T = value

operator fun <T> Cell<T>.setValue(thisRef: Any?, property: KProperty<*>, v: T) {
    value = v
}

/** Makes the [Cell]s and lists of a [CodesSession]. */
interface CellFactory {
    fun <T> cell(initial: T): Cell<T>

    fun <T> list(): MutableList<T>
}

/** Plain fields, for the JVM tests. */
object PlainCells : CellFactory {
    override fun <T> cell(initial: T): Cell<T> = object : Cell<T> {
        override var value: T = initial
    }

    override fun <T> list(): MutableList<T> = ArrayList()
}

/** Shortest time away after which what was drawn goes, whatever the lock time (with «Al salir» it is 0). */
const val CODES_MIN_AWAY_MS = 60_000L

/**
 * I-1: [awayMs] (measured with the monotonic clock, from ON_STOP) is at least the vault's lock time [lockAfterMs], and at
 * least [CODES_MIN_AWAY_MS]. A negative time (it cannot happen with that clock) never counts as away.
 */
fun awayTooLong(awayMs: Long, lockAfterMs: Long): Boolean = awayMs >= maxOf(lockAfterMs, CODES_MIN_AWAY_MS)

enum class SaveKind { RECOVERY, ANSWERS }

/**
 * A save waiting for the vault to open. The note is built (with its id) when it is queued, so writing it again, whatever
 * the reason, replaces the same entry with the same content instead of adding another.
 */
class PendingSave(val kind: SaveKind, val entry: VaultEntry)

class AnswerRow internal constructor(cells: CellFactory, question: String, answer: String) {
    private val q = cells.cell(question)
    private val a = cells.cell(answer)
    var question: String by q
    var answer: String by a
}

/**
 * Everything «PIN y códigos» shows, outside the composition: on Android it lives in the screen's ViewModel, so a
 * rotation, a theme change or a split screen keeps it, and the death of the process loses it (never in saved state:
 * the PIN, the codes and the answers are secrets). What was drawn goes ([wipe]) when the user comes back after more than
 * the lock time away ([stopped], [started]), when the vault locks while the screen is shown ([vaultStateIs]), and for
 * good when the screen is left ([clear]). They are Strings (what Compose shows and copies), so they cannot be zeroed:
 * every reference to them is dropped.
 */
class CodesSession(
    private val cells: CellFactory = PlainCells,
    private val pinOf: (Int) -> String = { generatePin(it) },
    private val codesOf: () -> List<String> = { recoveryCodes() },
    private val answerOf: (List<String>, Boolean) -> String = { words, plain -> inventedAnswer(words, plain) },
) {
    private val wordsCell = cells.cell<List<String>?>(null)

    /** The BIP-39 list for the answers; null until it was read (off the main thread). */
    var words: List<String>? by wordsCell
        private set

    private val digitsCell = cells.cell(6)
    var digits: Int by digitsCell
        private set

    private val pinCell = cells.cell(pinOf(6))
    var pin: String by pinCell
        private set

    private val codesCell = cells.cell(codesOf())
    var codes: List<String> by codesCell
        private set

    private val recoverySiteCell = cells.cell("")
    var recoverySite: String by recoverySiteCell

    private val plainCell = cells.cell(true)
    var plain: Boolean by plainCell
        private set

    private val answersSiteCell = cells.cell("")
    var answersSite: String by answersSiteCell

    private val rowList: MutableList<AnswerRow> = cells.list<AnswerRow>().apply { add(AnswerRow(cells, "", "")) }
    val rows: List<AnswerRow> get() = rowList

    private val pendingCell = cells.cell<PendingSave?>(null)

    /** A save waiting for the vault to be opened. */
    var pending: PendingSave? by pendingCell
        private set

    private val savingCell = cells.cell(false)
    var saving: Boolean by savingCell
        private set

    /** When the screen stopped (monotonic ms); null while it is shown. */
    private var stoppedAt: Long? = null

    /** The vault was open the last time its state was told: only a change from open to not open is a lock. */
    private var wasUnlocked = false

    val canDrawAnswers: Boolean get() = words != null

    private fun answer(): String = words?.let { answerOf(it, plain) } ?: ""

    fun wordsLoaded(list: List<String>) {
        words = list
        rowList.forEach { if (it.answer.isEmpty()) it.answer = answerOf(list, plain) }
    }

    /** True when the length changed (the count of PINs must be computed again). Draws a new PIN either way. */
    fun chooseDigits(n: Int): Boolean {
        val changed = n != digits
        digits = n
        pin = pinOf(n)
        return changed
    }

    fun newPin() {
        pin = pinOf(digits)
    }

    fun newCodes() {
        codes = codesOf()
    }

    /** Accents cannot be put back on a plain answer: every answer is drawn again in the chosen form. */
    fun setPlainAnswers(on: Boolean) {
        plain = on
        rowList.forEach { it.answer = answer() }
    }

    fun newAnswer(i: Int) {
        if (canDrawAnswers) rowList.getOrNull(i)?.answer = answer()
    }

    fun addQuestion() {
        rowList.add(AnswerRow(cells, "", answer()))
    }

    fun removeQuestion(i: Int) {
        if (rowList.size > 1 && i in rowList.indices) rowList.removeAt(i)
    }

    private fun noteFor(kind: SaveKind, now: Long, id: String): VaultEntry = when (kind) {
        SaveKind.RECOVERY -> recoveryCodesNote(recoverySite.trim(), codes, now, id)
        SaveKind.ANSWERS -> inventedAnswersNote(answersSite.trim(), rowList.map { InventedQuestion(it.question.trim(), it.answer) }, now, id)
    }

    /**
     * One save at a time: a second tap would add a second note. With the vault [unlocked] it returns the save to write now
     * ([beginWrite]); otherwise it is queued in [pending] until the vault opens, and it returns null.
     */
    fun requestSave(kind: SaveKind, unlocked: Boolean, now: Long, id: String): PendingSave? {
        if (saving || pending != null) return null
        val save = PendingSave(kind, noteFor(kind, now, id))
        if (unlocked) return save
        pending = save
        return null
    }

    fun cancelSave() {
        pending = null
    }

    /** False if a write is already running. */
    fun beginWrite(save: PendingSave): Boolean {
        if (saving) return false
        saving = true
        return true
    }

    /** Whenever the note was written, what it holds leaves the screen (drawn again or blank); on failure nothing is cleared. */
    fun endWrite(save: PendingSave, saved: Boolean) {
        saving = false
        pending = null
        if (!saved) return
        when (save.kind) {
            SaveKind.RECOVERY -> {
                codes = codesOf()
                recoverySite = ""
            }
            SaveKind.ANSWERS -> {
                answersSite = ""
                rowList.clear()
                rowList.add(AnswerRow(cells, "", answer()))
            }
        }
    }

    /**
     * ON_STOP of the screen (leaving the app, or another screen on top), or the screen leaving the composition (another
     * tab). [now] from SystemClock.elapsedRealtime(). The first stop of a trip away counts: a later one (a rotation's
     * dispose after its ON_STOP) does not move it.
     */
    fun stopped(now: Long) {
        if (stoppedAt == null) stoppedAt = now
    }

    /**
     * ON_START of the screen. True (and the screen is empty) when it was away at least the lock time ([awayTooLong]):
     * a short trip, to copy a code into a website and come back, keeps everything.
     */
    fun started(now: Long, lockAfterMs: Long): Boolean {
        val at = stoppedAt ?: return false
        stoppedAt = null
        if (!awayTooLong(now - at, lockAfterMs)) return false
        wipe()
        return true
    }

    /**
     * The vault's state, each time it changes. A lock while the screen is shown wipes it, as the vault's own screens
     * empty. A lock while it is stopped (leaving the app always locks the vault) is left to [started]: the time away
     * decides, or the copy-and-come-back use would be lost.
     */
    fun vaultStateIs(unlocked: Boolean) {
        val locked = wasUnlocked && !unlocked
        wasUnlocked = unlocked
        if (locked && stoppedAt == null) wipe()
    }

    /** Everything drawn goes and the screen is as on a new visit; only the chosen length and form stay (not secrets). */
    fun wipe() {
        pending = null
        pin = pinOf(digits)
        codes = codesOf()
        recoverySite = ""
        answersSite = ""
        rowList.clear()
        rowList.add(AnswerRow(cells, "", answer()))
    }

    /** The screen is gone for good: every reference to a secret is dropped. */
    fun clear() {
        pending = null
        pin = ""
        codes = emptyList()
        recoverySite = ""
        answersSite = ""
        rowList.clear()
        words = null
    }
}
