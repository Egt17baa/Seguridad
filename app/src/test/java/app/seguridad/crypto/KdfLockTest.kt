package app.seguridad.crypto

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Ruling R38 (branch review B, M-4): [KdfLock] lets one large key derivation run at a time in the process, and a caller
 * cancelled during its derivation keeps the lock until the derivation really ends (Bouncy Castle cannot be cut).
 */
class KdfLockTest {
    private fun scope() = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Test fun twoDerivationsAtOnceNeverOverlap() = runBlocking {
        val s = scope()
        val running = AtomicInteger()
        val most = AtomicInteger()
        val runs = (1..6).map {
            s.async {
                KdfLock.derive(Dispatchers.Default) {
                    most.accumulateAndGet(running.incrementAndGet(), ::maxOf)
                    Thread.sleep(30)
                    running.decrementAndGet()
                    it
                }
            }
        }
        assertEquals((1..6).toList(), withTimeout(10_000) { runs.map { it.await() } })
        assertEquals("one at a time", 1, most.get())
        assertFalse(KdfLock.isLocked)
        s.cancel()
    }

    @Test fun aCancelledDerivationKeepsTheLockUntilItEnds() = runBlocking {
        val s = scope()
        val events = Collections.synchronizedList(mutableListOf<String>())
        val started = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        var kept: ByteArray? = null
        val first = s.launch {
            // What a derivation gives reaches its caller even after a cancel, so the caller can wipe it (BOV-3).
            kept = KdfLock.derive(Dispatchers.Default) {
                events += "empieza 1"
                started.complete(Unit)
                check(release.await(10, TimeUnit.SECONDS))
                events += "acaba 1"
                ByteArray(32) { 7 }
            }
        }
        val second = try {
            started.await()
            first.cancel()
            val waiting = s.async { KdfLock.derive(Dispatchers.Default) { events += "empieza 2" } }
            delay(300)
            assertEquals("the second waits for the cancelled one's work", listOf("empieza 1"), events.toList())
            assertTrue(KdfLock.isLocked)
            waiting
        } finally {
            release.countDown() // Never left held: the lock is the whole process's, other tests derive too.
        }
        withTimeout(10_000) { second.await() }
        first.join()
        assertEquals(listOf("empieza 1", "acaba 1", "empieza 2"), events.toList())
        assertEquals(32, kept?.size)
        assertFalse(KdfLock.isLocked)
        s.cancel()
    }

    @Test fun aCallerCancelledWhileItWaitsNeverDerives() = runBlocking {
        val s = scope()
        val started = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val ran = AtomicInteger()
        val holder = s.launch {
            KdfLock.derive(Dispatchers.Default) {
                started.complete(Unit)
                check(release.await(10, TimeUnit.SECONDS))
            }
        }
        val waiting = try {
            started.await()
            s.launch { KdfLock.derive(Dispatchers.Default) { ran.incrementAndGet() } }.also {
                delay(100)
                it.cancel()
            }
        } finally {
            release.countDown()
        }
        withTimeout(10_000) {
            holder.join()
            waiting.join()
        }
        // A third one gets the lock at once: the cancelled waiter left the queue without running.
        withTimeout(10_000) { KdfLock.derive(Dispatchers.Default) { ran.addAndGet(10) } }
        assertEquals(10, ran.get())
        s.cancel()
    }

    @Test fun anExceptionInTheBlockReleasesTheLock() = runBlocking {
        try {
            KdfLock.derive(Dispatchers.Default) { throw CryptoFailure(KDF_OUT_OF_MEMORY) }
        } catch (e: CryptoFailure) {
            assertEquals(KDF_OUT_OF_MEMORY, e.message)
        }
        assertFalse(KdfLock.isLocked)
        assertEquals(3, withTimeout(10_000) { KdfLock.derive(Dispatchers.Default) { 3 } })
    }
}
