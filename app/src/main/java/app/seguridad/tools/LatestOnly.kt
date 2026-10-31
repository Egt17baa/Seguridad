package app.seguridad.tools

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Only the latest run: [launch] cancels the one before if it has not ended (running, or still waiting to start). For a
 * notice shown with `SnackbarHostState.showSnackbar`, whose cancellation takes it off the screen: «Copiar» tapped
 * several times shows one notice, the last, instead of queueing one per tap (MN, T7 of §2.5). Main thread only, as
 * the composition that holds it.
 */
class LatestOnly {
    private var job: Job? = null

    fun launch(scope: CoroutineScope, block: suspend CoroutineScope.() -> Unit) {
        job?.cancel()
        job = scope.launch(block = block)
    }

    fun cancel() {
        job?.cancel()
        job = null
    }
}
