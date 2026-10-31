package app.seguridad.tools.codes

import app.seguridad.vault.LockReason
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

/**
 * A lock of the vault, as the screen's holder saw it: [reason] from VaultStore.lastLockReason, [appInForeground] from
 * AppContainer.foreground (false once MainActivity.onStop began), [now] on the monotonic clock, and the vault's
 * [lockAfterMs].
 */
data class LockSeen(val reason: LockReason, val appInForeground: Boolean, val now: Long, val lockAfterMs: Long)

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
 * the lock time away ([stopped], [started]), when the vault locks with the app in use, on any tab ([vaultStateIs]),
 * and for good when the screen is left ([clear]). The site and the questions stay: they are not secrets. The secrets
 * are Strings (what Compose shows and copies), so they cannot be zeroed: every reference to them is dropped.
 */
class CodesSession(
    private val cells: CellFactory = PlainCells,
    private val pinOf: (Int) -> String = { generatePin(it) },
    private val codesOf: () -> List<String> = { recoveryCodes() },
    private val answerOf: (List<String>, Boolean) -> String = { words, plain -> inventedAnswer(words, plain) },
    /** When the session was made (monotonic ms): the first [lastUsed]. */
    createdAt: Long = 0L,
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

    /** When what was drawn must go: the time away, in the other app too, and the locks ([ScreenGuard], [ScreenAway]). */
    private val away = ScreenGuard(createdAt) { wipe() }

    /** Any tap or key on the screen (monotonic ms), for the idle lock: the screen never touches the vault. */
    fun used(now: Long) = away.used(now)

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
    fun stopped(now: Long) = away.stopped(now)

    /**
     * ON_START of the screen. True (and the screen is empty) when it was away at least the lock time ([awayTooLong]):
     * a short trip, to copy a code into a website and come back, keeps everything.
     */
    fun started(now: Long, lockAfterMs: Long): Boolean = away.started(now, lockAfterMs)

    /**
     * COV2-3 (M3 of the F1 review): another app took the focus while Seguridad stays visible, in split screen or a floating
     * window (no ON_STOP comes). The time in the other app counts as time away.
     */
    fun otherAppFocused(now: Long) = away.otherAppFocused(now)

    /** Back from the other app: true (and the screen is empty) when it was there at least [awayTooLong]. */
    fun otherAppLeft(now: Long, lockAfterMs: Long): Boolean = away.otherAppLeft(now, lockAfterMs)

    /**
     * The vault's state, each time it changes; [lock] is asked only for a change from open to locked. With the app in use
     * (on this tab or another) a lock wipes the screen: by hand always, the idle one only if this screen was not used for
     * the lock time either ([awayTooLong] since the last [used]), so typing here keeps it; while the user is in another
     * app in multi-window ([otherAppFocused]), always. A lock from leaving the app (always there, MainActivity.onStop) is
     * left to [started]: the time away decides, or copying a code into a website and coming back would lose it. The rule
     * itself is [ScreenAway.lockWipes].
     */
    fun vaultStateIs(unlocked: Boolean, lock: () -> LockSeen) = away.vaultStateIs(unlocked, lock)

    /**
     * What was drawn goes: the PIN, the codes and the answers are drawn again, and a save waiting for the vault (it holds
     * them) is dropped. The sites, the questions, the chosen length and the form stay: they are not secrets.
     */
    fun wipe() {
        pending = null
        pin = pinOf(digits)
        codes = codesOf()
        rowList.forEach { it.answer = answer() }
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
