package app.seguridad.tools.slip39

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference

/**
 * The runs of «Repartir un secreto»: one at a time, the work on [work] (PBKDF2 on [Dispatchers.Default] in the app),
 * launched in [scope] (the ViewModel's, on the main thread). Each run works on the snapshot its caller made (the
 * request is built once, before the launch) and gets a number; only the last one started may show its result.
 * [stop] (a wipe, a cancel, an edit, a new run) cancels the run going on and bumps the number, so a result that arrives
 * later is refused: never shown, and a recovered secret is zeroed. The operation's `check` is its coroutine's
 * `ensureActive`, which Slip39 calls between PBKDF2 rounds and groups.
 */
class SecretShareRuns(
    private val session: SecretShareSession,
    private val scope: CoroutineScope,
    private val work: CoroutineDispatcher = Dispatchers.Default,
) {
    private var job: Job? = null
    private var runId = 0L

    /** Whether a run is going on (for the tests; the screen reads [SecretShareSession.busy]). */
    val running: Boolean get() = job?.isActive == true

    fun stop() {
        job?.cancel()
        job = null
        runId++
        session.busy = false
    }

    /** Splits with [op]; [cleanup] (which zeroes the snapshot's secret and passphrase) runs however the run ends. */
    fun split(op: (check: () -> Unit) -> List<List<String>>, cleanup: () -> Unit) {
        stop()
        val mine = ++runId
        session.error = null
        session.parts = null
        session.busy = true
        job = scope.launch {
            try {
                val parts = withContext(work) { op { ensureActive() } }
                if (runId == mine) session.parts = parts
            } catch (e: Slip39Failure) {
                if (runId == mine) session.error = e.message
            } finally {
                cleanup()
                if (runId == mine) {
                    session.busy = false
                    job = null
                }
            }
        }
    }

    /**
     * Combines with [op]; the secret is shown only if this is still the last run, and zeroed otherwise, also when the
     * run is cancelled after [op] returned (withContext then drops the value, so it is kept aside to be zeroed).
     */
    fun combine(op: (check: () -> Unit) -> ByteArray, cleanup: () -> Unit) {
        stop()
        val mine = ++runId
        session.error = null
        session.recovered = null
        session.busy = true
        val produced = AtomicReference<ByteArray?>(null)
        job = scope.launch {
            var accepted = false
            try {
                val secret = withContext(work) { op { ensureActive() }.also { produced.set(it) } }
                if (runId == mine) {
                    session.recovered = secret
                    accepted = true
                }
            } catch (e: Slip39Failure) {
                if (runId == mine) session.error = e.message
            } finally {
                if (!accepted) produced.get()?.fill(0)
                cleanup()
                if (runId == mine) {
                    session.busy = false
                    job = null
                }
            }
        }
    }
}
