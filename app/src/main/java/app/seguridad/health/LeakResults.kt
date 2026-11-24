package app.seguridad.health

import app.seguridad.vault.EntryType
import app.seguridad.vault.VaultEntry
import app.seguridad.vault.lastPasswordChange
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch

/**
 * 2.1.1, task 5 (owner, 24-11-2026): the result of «Comprobar filtraciones de los accesos» of «Salud», kept for the
 * session so the leaked logins can be fixed one by one without checking again. One per process (AppContainer), only in
 * memory: never on disk, in the settings, in a Bundle or in saved state, and never logged. It keeps, per entry id, the
 * times its password was seen in leaks and the mark of its last password change when it was checked
 * ([lastPasswordChange]): never the password nor its hash. It holds for one [lockEpoch] (the app's lock counter, which
 * goes up with every lock of the active vault and every switch of vault) and one vault in memory ([vaultIdentity], the
 * store's generation, which goes up when «Importar copia» replaces the open vault without locking it: branch review,
 * BR-1): with another, it is forgotten, and the app also calls [forget] at every lock and right before an import
 * replaces the vault (AppContainer). A process that dies takes it with it.
 *
 * The check runs in [scope] (the app's, outside any screen), so it goes on while «Salud» is left to edit a login and
 * shows its progress when the user comes back. It stops at a lock ([start]'s `awaitClosed`, and the epoch read before
 * every request): nothing more is sent, the chars handed to the request are wiped, and everything is dropped. A new
 * check starts from zero and replaces the result. A cancelled or failed one keeps what it found, and says so ([message]).
 * One check at a time (BR-3): a new one waits for the one before (stopped, but maybe with its last request in flight,
 * which cannot be interrupted) to end before it sends its first request.
 */
class LeakResults(
    private val scope: CoroutineScope,
    private val lockEpoch: () -> Int,
    private val vaultIdentity: () -> Long = { 0L },
) {
    /** One leaked login: [times] seen in leaks, and the [lastPasswordChange] of its entry when it was checked. */
    data class Found(val times: Int, val mark: Long)

    /** How the last check ended: [NONE] while there is none (or it is running), else finished, cancelled or failed. */
    enum class End { NONE, DONE, CANCELLED, FAILED }

    /**
     * The last check of [epoch] and of the vault [vault] ([vaultIdentity] when it started): the logins it found leaked
     * ([found], by entry id), whether it is [running], and how far it went ([done] of [total] distinct passwords).
     */
    data class Check(
        val epoch: Int,
        val found: Map<String, Found>,
        val running: Boolean,
        val done: Int,
        val total: Int,
        val end: End,
        val vault: Long = 0L,
    )

    private val lock = Any()

    /** Goes up with every [start] and [forget]: a check of before never writes after them. */
    private var run = 0
    private var job: Job? = null

    /** The last check started, kept after [forget] or its end: the next one joins it before its first request (BR-3). */
    private var last: Job? = null
    private val _state = MutableStateFlow(EMPTY)

    /** What the screen follows; read it through [valid] (or [current]), which drop a result of another epoch. */
    val state: StateFlow<Check> = _state.asStateFlow()

    /** [s] if it is of the app's lock epoch and of the vault in memory now; else nothing (a lock, a switch or an import since). */
    fun valid(s: Check): Check = if (isNow(s)) s else EMPTY

    private fun isNow(s: Check) = s.epoch == lockEpoch() && s.vault == vaultIdentity()

    /** The last check, if it is of this epoch and vault; one of another is dropped here. */
    fun current(): Check = synchronized(lock) {
        val s = _state.value
        if (s !== EMPTY && !isNow(s)) dropLocked()
        _state.value
    }

    /**
     * The leaked logins among [entries] (the open vault's, now), by id: those of [s] whose entry is still a login out of
     * the trash, with a password, and whose last password change is the one checked. A login whose password changed,
     * deleted or in the trash is not there; one added after the check was not checked, and is not there either.
     */
    fun breached(entries: List<VaultEntry>, s: Check = _state.value): Map<String, Int> {
        val found = valid(s).found
        if (found.isEmpty()) return emptyMap()
        val out = HashMap<String, Int>()
        for (e in entries) {
            val f = found[e.id] ?: continue
            if (e.deletedAt != null || e.type != EntryType.LOGIN || e.password.isEmpty()) continue
            if (lastPasswordChange(e) == f.mark) out[e.id] = f.times
        }
        return out
    }

    /**
     * Checks the logins of [entries] (those with a password, out of the trash, [uniquePasswords]): one request per
     * distinct password through [times], whose chars are wiped right after. [isOpen] is read and the epoch compared
     * before each request; [awaitClosed] returns when the vault locks, which stops the check at once. Replaces the
     * result of before, which is gone from now on.
     */
    fun start(entries: List<VaultEntry>, times: suspend (CharArray) -> Int, isOpen: () -> Boolean, awaitClosed: suspend () -> Unit) {
        val groups = uniquePasswords(entries)
        // The mark of each login checked, read from the same entries whose passwords are sent.
        val checked = HashSet<String>()
        groups.values.forEach { checked.addAll(it) }
        val marks = HashMap<String, Long>()
        for (e in entries) if (e.id in checked) marks[e.id] = lastPasswordChange(e)
        synchronized(lock) {
            job?.cancel()
            val before = last
            val r = ++run
            val epoch = lockEpoch()
            val vault = vaultIdentity()
            _state.value = Check(epoch, emptyMap(), running = true, done = 0, total = groups.size, end = End.NONE, vault = vault)
            job = scope.launch {
                val check = coroutineContext.job
                // The vault locks (idle, leaving the app, by hand, or a switch): the check stops at once, nothing more is
                // sent, the passwords it held are let go and its result is dropped.
                launch {
                    awaitClosed()
                    check.cancel()
                }
                val found = HashMap<String, Found>()
                var done = 0
                // Review M-1: whatever the end (a step, done, failed, cancelled), a lock or a switch since drops it all,
                // even if neither the watcher nor AppContainer saw it (a lock and unlock quicker than both).
                fun stillOpen() = isOpen() && lockEpoch() == epoch && vaultIdentity() == vault
                try {
                    before?.join() // BR-3: the check of before (cancelled) ends first, its last request included.
                    for ((password, ids) in groups) {
                        check.ensureActive() // Stopped («Cancelar», a lock, a new check): nothing more is sent.
                        if (!stillOpen()) throw CancellationException()
                        val chars = password.toCharArray()
                        val n = try {
                            times(chars)
                        } finally {
                            chars.fill('\u0000')
                        }
                        check.ensureActive()
                        if (!stillOpen()) throw CancellationException() // Locked while the request was in flight.
                        if (n > 0) ids.forEach { found[it] = Found(n, marks.getValue(it)) }
                        done++
                        val now = HashMap(found)
                        val d = done
                        publish(r) { it.copy(found = now, done = d) }
                    }
                    if (stillOpen()) publish(r) { it.copy(running = false, end = End.DONE) } else drop(r)
                } catch (e: CancellationException) {
                    if (stillOpen()) publish(r) { it.copy(running = false, end = End.CANCELLED) } else drop(r)
                    throw e
                } catch (e: Exception) {
                    if (stillOpen()) publish(r) { it.copy(running = false, end = End.FAILED) } else drop(r)
                } finally {
                    coroutineContext.cancelChildren() // The vault watcher: done with this check.
                    synchronized(lock) { if (r == run) job = null }
                }
            }
            last = job
        }
    }

    /** «Cancelar», or «Salud» out of memory: the check stops; what it found stays, as a cancelled check. */
    fun cancel() {
        synchronized(lock) { job }?.cancel()
    }

    /** A lock, a switch of vault or an import that replaces it (AppContainer): the check stops and its result is gone. */
    fun forget() = synchronized(lock) { dropLocked() }

    private fun dropLocked() {
        run++
        job?.cancel()
        job = null
        _state.value = EMPTY
    }

    private fun drop(r: Int) = synchronized(lock) { if (r == run) dropLocked() }

    private inline fun publish(r: Int, f: (Check) -> Check) = synchronized(lock) { if (r == run) _state.value = f(_state.value) }

    companion object {
        /** No check (none yet, or forgotten). Its epoch is never one of the app's. */
        val EMPTY = Check(Int.MIN_VALUE, emptyMap(), running = false, done = 0, total = 0, end = End.NONE)

        /**
         * The line under the button: how the check of [s] ended (null while there is none or it runs). [left] is how many
         * of the leaked logins it found still count now ([breached]); review M-2: when the user fixed some (or sent them
         * to the trash), a second sentence says how many are left, so the line and the rows «Filtrada» agree.
         */
        fun message(s: Check, left: Int = s.found.size): String? {
            val base = when (s.end) {
                End.NONE -> return null
                End.DONE -> "Comprobación terminada: ${s.found.size} ${if (s.found.size == 1) "acceso filtrado" else "accesos filtrados"}."
                End.CANCELLED -> "Comprobación cancelada (${s.done} de ${s.total})."
                End.FAILED -> "No se pudo completar la comprobación (${s.done} de ${s.total}). Revisa la conexión."
            }
            if (left == s.found.size) return base
            return base + " " + when (left) {
                0 -> "Ya no queda ninguno sin cambiar."
                1 -> "Queda 1 sin cambiar."
                else -> "Quedan $left sin cambiar."
            }
        }
    }
}
