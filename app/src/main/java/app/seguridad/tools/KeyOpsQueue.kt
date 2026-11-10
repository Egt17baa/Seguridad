package app.seguridad.tools

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** What an operation on the fingerprint's Keystore alias does ([KeyOpsQueue]). */
enum class KeyOp { ENABLE, DISABLE, UNLOCK }

/**
 * One operation at a time on the fingerprint's Keystore alias (1.4.0): each one holds the queue from its first step to
 * its last, so a disable can never slip in between an enable's key generation, its prompt and its doFinal, nor delete
 * the key an enable has just created. Blocks run on [dispatcher] (a single thread in the app), in the order asked.
 *
 * A [KeyOp.DISABLE] first cancels any [KeyOp.ENABLE] in flight or still waiting its turn ([cancelEnable]): the user's
 * last intent wins. The cancelled block gets a [CancellationException][kotlinx.coroutines.CancellationException] and
 * keeps the queue until it has returned, its `finally` blocks included, so its cleanup ends before the disable starts.
 * Only that operation is cancelled: the caller of [run] gets the exception and is still active. [KeyOp.UNLOCK] cancels
 * nothing and waits.
 *
 * Never call [run] from inside a block: the queue is not reentrant and that would wait for ever.
 */
class KeyOpsQueue(private val dispatcher: CoroutineDispatcher) {
    private val mutex = Mutex()

    // The enables asked for and not ended yet, running or waiting their turn. Guarded by itself.
    private val enables = mutableSetOf<Job>()

    suspend fun <T> run(kind: KeyOp, block: suspend () -> T): T {
        if (kind == KeyOp.DISABLE) cancelEnable()
        // Its own scope, so that cancelEnable cancels this operation and not the caller's coroutine.
        return coroutineScope {
            val op = coroutineContext.job
            if (kind == KeyOp.ENABLE) synchronized(enables) { enables += op }
            try {
                // withContext returns only once the block has, also when cancelled: the lock is held until then.
                mutex.withLock { withContext(dispatcher) { block() } }
            } finally {
                if (kind == KeyOp.ENABLE) synchronized(enables) { enables -= op }
            }
        }
    }

    /** Cancels the enables running or waiting; each ends with its own cleanup ([run]). */
    fun cancelEnable() {
        val asked = synchronized(enables) { enables.toList() }
        asked.forEach { it.cancel() }
    }
}
