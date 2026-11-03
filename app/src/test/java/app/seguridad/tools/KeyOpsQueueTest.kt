package app.seguridad.tools

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.ContinuationInterceptor

/**
 * The fingerprint's operations on its one Keystore alias (1.4.0): one at a time from start to end, a disable cancels
 * an enable, an unlock waits. The queue runs its blocks on a single-thread test dispatcher, as the app's does.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class KeyOpsQueueTest {
    private fun TestScope.queue() = KeyOpsQueue(StandardTestDispatcher(testScheduler))

    @Test fun operationsNeverOverlap() = runTest {
        val queue = queue()
        val log = mutableListOf<String>()
        val first = launch { queue.run(KeyOp.ENABLE) { log += "in1"; delay(10); log += "out1" } }
        val second = launch { queue.run(KeyOp.UNLOCK) { log += "in2"; delay(10); log += "out2" } }
        joinAll(first, second)
        assertEquals(listOf("in1", "out1", "in2", "out2"), log)
    }

    @Test fun disableDuringEnableCancelsTheEnable() = runTest {
        val queue = queue()
        val log = mutableListOf<String>()
        var callerStillActive = false
        val enable = launch {
            try {
                queue.run(KeyOp.ENABLE) {
                    log += "enableStart"
                    try {
                        awaitCancellation()
                    } catch (e: CancellationException) {
                        log += "enableCancelled"
                        throw e
                    }
                }
            } catch (e: CancellationException) {
                // Only the operation was cancelled, not the coroutine that asked for it: it can still clean up and report.
                callerStillActive = currentCoroutineContext().isActive
            }
        }
        runCurrent()
        val disable = launch { queue.run(KeyOp.DISABLE) { log += "disable" } }
        joinAll(enable, disable)
        assertEquals(listOf("enableStart", "enableCancelled", "disable"), log)
        assertTrue(callerStillActive)
    }

    @Test fun disableAlsoDropsAnEnableStillWaitingItsTurn() = runTest {
        val queue = queue()
        val log = mutableListOf<String>()
        val unlock = launch { queue.run(KeyOp.UNLOCK) { log += "unlockIn"; delay(10); log += "unlockOut" } }
        runCurrent()
        val enable = launch { queue.run(KeyOp.ENABLE) { log += "enable" } }
        runCurrent()
        val disable = launch { queue.run(KeyOp.DISABLE) { log += "disable" } }
        joinAll(unlock, enable, disable)
        // The last intent wins: the enable asked before the disable never runs.
        assertEquals(listOf("unlockIn", "unlockOut", "disable"), log)
        assertTrue(enable.isCancelled)
    }

    @Test fun aCancelledEnableFinishesItsCleanupBeforeTheDisableRuns() = runTest {
        val queue = queue()
        val log = mutableListOf<String>()
        val enable = launch {
            try {
                queue.run(KeyOp.ENABLE) {
                    try {
                        awaitCancellation()
                    } finally {
                        // Deleting the half-made key takes time: the disable must not start before it is done.
                        withContext(NonCancellable) { delay(10) }
                        log += "enableCleanedUp"
                    }
                }
            } catch (e: CancellationException) {
                log += "enableEnded"
            }
        }
        runCurrent()
        val disable = launch { queue.run(KeyOp.DISABLE) { log += "disable" } }
        joinAll(enable, disable)
        assertEquals(listOf("enableCleanedUp", "enableEnded", "disable"), log)
    }

    @Test fun unlockWaitsForDisable() = runTest {
        val queue = queue()
        val log = mutableListOf<String>()
        val disable = launch { queue.run(KeyOp.DISABLE) { log += "disableStart"; delay(10); log += "disableEnd" } }
        runCurrent()
        val unlock = launch { queue.run(KeyOp.UNLOCK) { log += "unlock" } }
        joinAll(disable, unlock)
        assertEquals(listOf("disableStart", "disableEnd", "unlock"), log)
    }

    @Test fun disableWaitsForAnUnlockAndDoesNotCancelIt() = runTest {
        val queue = queue()
        val log = mutableListOf<String>()
        val unlock = launch { queue.run(KeyOp.UNLOCK) { log += "unlockStart"; delay(10); log += "unlockEnd" } }
        runCurrent()
        val disable = launch { queue.run(KeyOp.DISABLE) { log += "disable" } }
        joinAll(unlock, disable)
        assertEquals(listOf("unlockStart", "unlockEnd", "disable"), log)
        assertTrue(!unlock.isCancelled)
    }

    @Test fun cancelledEnableReleasesTheQueue() = runTest {
        val queue = queue()
        val log = mutableListOf<String>()
        val enable = launch { queue.run(KeyOp.ENABLE) { awaitCancellation() } }
        runCurrent()
        launch { queue.run(KeyOp.UNLOCK) { log += "unlock" } }
        runCurrent()
        assertEquals("the unlock waits while the enable holds the queue", emptyList<String>(), log)
        queue.cancelEnable()
        runCurrent()
        assertEquals(listOf("unlock"), log)
        assertEquals("at once: no virtual time went by", 0L, currentTime)
        assertTrue(enable.isCancelled)
    }

    @Test fun aFailingOperationReleasesTheQueue() = runTest {
        val queue = queue()
        val log = mutableListOf<String>()
        val failed = runCatching { queue.run(KeyOp.UNLOCK) { throw IllegalStateException("Keystore") } }
        assertTrue(failed.exceptionOrNull() is IllegalStateException)
        queue.run(KeyOp.DISABLE) { log += "disable" }
        assertEquals(listOf("disable"), log)
    }

    @Test fun blocksRunOnTheDispatcherGiven() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val queue = KeyOpsQueue(dispatcher)
        for (kind in KeyOp.entries) {
            val seen = queue.run(kind) { currentCoroutineContext()[ContinuationInterceptor] }
            assertSame(kind.name, dispatcher, seen)
        }
    }
}
