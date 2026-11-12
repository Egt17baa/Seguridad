package app.seguridad.vault

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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
 * time the app saw this vault in use before this unlock. Then nothing goes in this unlock and
 * [VaultData.trashPurgeSkipped] says so; the next unlock empties it whatever the clock says (ruling T3, I-1: skipped at
 * most once in a row, so a vault opened every few days still empties its trash). An entry deleted «in the future» (the
 * clock was set forward then, or is set back now) counts from [now] instead (review M-6), so it stays 30 days of the
 * clock as it is now, never longer. Its attachments' files go with the collection after the save.
 */
fun VaultData.purgedTrash(now: Long, clockMark: Long): VaultData {
    val jumped = now - lastUnlockAt > TRASH_CLOCK_JUMP_MS && now - clockMark > TRASH_CLOCK_JUMP_MS
    val skip = jumped && !trashPurgeSkipped
    val dated = entries.map { e -> if (e.deletedAt != null && e.deletedAt > now) e.copy(deletedAt = now) else e }
    val kept = if (skip) dated else dated.filter { e -> e.deletedAt.let { it == null || now - it <= TRASH_MS } }
    val same = kept.size == entries.size && kept.indices.all { kept[it] === entries[it] }
    return copy(entries = if (same) entries else kept, lastUnlockAt = now, trashPurgeSkipped = skip)
}

/**
 * «Deshacer» after a move to the trash (design: 5 s). Remembers, in memory only, the ids of the last move made through
 * [moveToTrash], with the time ([elapsed], a clock that only moves forward) and the vault's [VaultStore.lockEpoch]: a
 * lock (or a switch to another vault, which locks this one) forgets it, and so does [UNDO_TRASH_MS]. One per vault, in
 * `AppContainer`, which hands out the active vault's. With [scope], a lock also clears [offer] at once (review M-1), so
 * a vault opened again never shows a «Deshacer» left over.
 */
class UndoTrash(private val store: VaultStore, private val elapsed: () -> Long, scope: CoroutineScope? = null) {
    private class Last(val ids: Set<String>, val at: Long, val epoch: Int)

    @Volatile private var last: Last? = null

    private val _offer = MutableStateFlow(0)

    /** How many entries the last move sent to the trash, while «Deshacer» may still be offered; 0 when none. */
    val offer: StateFlow<Int> = _offer.asStateFlow()

    init {
        scope?.launch { store.state.collect { if (it !is VaultState.Unlocked) forget() } }
    }

    private fun valid(): Last? {
        val l = last?.takeIf { elapsed() - it.at < UNDO_TRASH_MS && store.lockEpoch == it.epoch }
        if (l == null && last != null) forget()
        return l
    }

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
