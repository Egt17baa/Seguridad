package app.seguridad.vault

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/*
 * 2.0, task 3: the trash (design «Borrar, papelera y deshacer»). Every deletion sends the entry there ([VaultEntry.deletedAt]);
 * it is out of the list, the search, «Salud», the notices and the duplicates of an import, but it is in the vault file
 * and so in every copy. It empties itself at an unlock ([purgedTrash]), and by hand.
 */

private const val DAY_MS = 24 * 60 * 60_000L

/** How long an entry stays in the trash before an unlock removes it ([VaultLimits.TRASH_DAYS] days). */
const val TRASH_MS = VaultLimits.TRASH_DAYS * DAY_MS

/**
 * A move of the clock this large since the last unlock and since the vault's clock mark is taken for a clock set
 * forward ([purgedTrash]): that unlock removes nothing.
 */
const val TRASH_CLOCK_JUMP_MS = 2 * DAY_MS

/** How long «Deshacer» stays after a move to the trash ([UndoTrash]). */
const val UNDO_TRASH_MS = 5_000L

/**
 * This vault at an unlock at [now]: [VaultData.lastUnlockAt] becomes [now], and the entries in the trash for more than
 * [VaultLimits.TRASH_DAYS] days ([TRASH_MS], strictly) go, unless the clock looks set forward (focus 3, ruling of the
 * pre-flight, row 3): [now] passes by more than [TRASH_CLOCK_JUMP_MS] both the last unlock and [clockMark], the highest
 * time the app saw this vault in use before this unlock. Then nothing goes in this unlock; the next normal one empties
 * it. An entry deleted «in the future» (a clock that went back) never goes. Its attachments' files go with the
 * collection after the save.
 */
fun VaultData.purgedTrash(now: Long, clockMark: Long): VaultData {
    val jumped = now - lastUnlockAt > TRASH_CLOCK_JUMP_MS && now - clockMark > TRASH_CLOCK_JUMP_MS
    val kept = if (jumped) entries else entries.filter { e -> e.deletedAt.let { it == null || now - it <= TRASH_MS } }
    return copy(entries = if (kept.size == entries.size) entries else kept, lastUnlockAt = now)
}

/**
 * «Deshacer» after a move to the trash (design: 5 s). Remembers, in memory only, the ids of the last move made through
 * [moveToTrash], with the time ([elapsed], a clock that only moves forward) and the vault's [VaultStore.lockEpoch]: a
 * lock (or a switch to another vault, which locks this one) forgets it, and so does [UNDO_TRASH_MS]. One per vault, in
 * `AppContainer`, which hands out the active vault's.
 */
class UndoTrash(private val store: VaultStore, private val elapsed: () -> Long) {
    private class Last(val ids: Set<String>, val at: Long, val epoch: Int)

    @Volatile private var last: Last? = null

    private val _offer = MutableStateFlow(0)

    /** How many entries the last move sent to the trash, while «Deshacer» may still be offered; 0 when none. */
    val offer: StateFlow<Int> = _offer.asStateFlow()

    private fun valid(): Last? = last?.takeIf { elapsed() - it.at < UNDO_TRASH_MS && store.lockEpoch == it.epoch }

    /** Whether [undo] would bring something back now. */
    fun canUndo(): Boolean = valid() != null

    /** [VaultStore.moveToTrash], remembering what it moved for [undo]. */
    suspend fun moveToTrash(ids: Set<String>): Int {
        val epoch = store.lockEpoch
        val moved = store.trash(ids)
        if (moved.isNotEmpty()) {
            last = Last(moved, elapsed(), epoch)
            _offer.value = moved.size
        }
        return moved.size
    }

    /** Brings back the entries of the last move, if within [UNDO_TRASH_MS] and with no lock since; how many came back. */
    suspend fun undo(): Int {
        val l = valid()
        forget()
        return if (l == null) 0 else store.restore(l.ids)
    }

    /** Forgets the last move (the message went away, or another action replaced it). */
    fun forget() {
        last = null
        _offer.value = 0
    }
}
