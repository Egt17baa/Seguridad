package app.seguridad.tools

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** «Copiar» tapped several times shows one notice, the last, instead of queueing one per tap (MN, T7 of §2.5). */
@OptIn(ExperimentalCoroutinesApi::class)
class LatestOnlyTest {
    @Test fun aNewRunCancelsTheOneStillRunning() = runTest {
        val latest = LatestOnly()
        val shown = mutableListOf<String>()
        val ended = mutableListOf<String>()
        val never = CompletableDeferred<Unit>()
        for (n in listOf("1", "2", "3")) {
            latest.launch(this) {
                try {
                    shown += n
                    never.await() // A snackbar on screen until it is dismissed.
                } finally {
                    ended += n
                }
            }
            advanceUntilIdle()
        }
        assertEquals(listOf("1", "2", "3"), shown)
        assertEquals("Each earlier notice is gone when the next one comes", listOf("1", "2"), ended)
        latest.cancel()
        advanceUntilIdle()
        assertEquals(listOf("1", "2", "3"), ended)
    }

    @Test fun runsQueuedBeforeStartingAreDroppedToo() = runTest {
        val latest = LatestOnly()
        val shown = mutableListOf<String>()
        // Three taps before any of them got to run: only the last one shows.
        for (n in listOf("1", "2", "3")) latest.launch(this) { shown += n }
        advanceUntilIdle()
        assertEquals(listOf("3"), shown)
    }
}
