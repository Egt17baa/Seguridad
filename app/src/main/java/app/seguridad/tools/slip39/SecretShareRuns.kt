package app.seguridad.tools.slip39

import app.seguridad.crypto.KdfLock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

/**
 * The runs of «Repartir un secreto»: one at a time, the work on [work] (PBKDF2 on [Dispatchers.Default] in the app),
 * launched in [scope] (the ViewModel's, on the main thread). Each run works on the snapshot its caller made (the
 * request is built once, before the launch) and gets a number; only the last one started may show its result.
 * [stop] (a wipe, a cancel, an edit, a new run) cancels the run going on and bumps the number, so a result that arrives
 * later is refused: never shown, and a recovered secret is zeroed. The operation's `check` is its coroutine's
 * `ensureActive`, which Slip39 calls between PBKDF2 rounds and groups.
 *
 * 2.1.2, R-10:
 * - each run is launched with [CoroutineStart.ATOMIC], so its body (and its `finally`, which runs the cleanup that zeroes
 *   the snapshot) always starts, also when the run is cancelled before its dispatcher runs it; a run cancelled then
 *   stops before the work ([work]);
 * - the work runs holding the process' derivation lock ([KdfLock.derive]), as every other password derivation of the
 *   app: waiting for it suspends (no thread blocked) and can be cancelled; once the work starts it runs to its next
 *   `check`, which still sees a cancel (it reads the run's own job), and the lock goes when the work ends.
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
        job = scope.launch(start = CoroutineStart.ATOMIC) {
            try {
                val parts = derive(op)
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
     * Runs [op] on [work] holding [KdfLock], unless this run was cancelled first; its `check` is this run's own job
     * (the lock's block runs non-cancellable, so its own context would never say a cancel). A run cancelled while [op]
     * worked throws once it returns, as `withContext` did, so what it made is never taken as this run's result.
     */
    private suspend fun <T> derive(op: (check: () -> Unit) -> T): T {
        val run = currentCoroutineContext().job
        run.ensureActive()
        return KdfLock.derive(work) { op { run.ensureActive() } }.also { run.ensureActive() }
    }

    /**
     * Combines with [op]; the secret is shown only if this is still the last run, and zeroed otherwise, also when the
     * run is cancelled after [op] returned ([derive] then throws, so the value is kept aside to be zeroed).
     */
    fun combine(op: (check: () -> Unit) -> ByteArray, cleanup: () -> Unit) {
        stop()
        val mine = ++runId
        session.error = null
        session.recovered = null
        session.busy = true
        val produced = AtomicReference<ByteArray?>(null)
        job = scope.launch(start = CoroutineStart.ATOMIC) {
            var accepted = false
            try {
                val secret = derive { check -> op(check).also { produced.set(it) } }
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
