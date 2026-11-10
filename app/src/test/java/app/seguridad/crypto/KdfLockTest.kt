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

    @Test fun theBlockingEntryRefusesTheMainThread() {
        var ran = false
        try {
            KdfLock.deriveBlocking({ true }) { ran = true }
            org.junit.Assert.fail("waited on the main thread")
        } catch (e: IllegalStateException) {
            assertFalse(ran)
        }
        assertFalse(KdfLock.isLocked)
        // Off Android (these tests), the real check says no main thread, and it runs here.
        assertEquals(5, KdfLock.deriveBlocking { 5 })
    }

    /**
     * Ruling R40: «Cifrar archivo» takes the lock for the Argon2id of its key only. While another derivation holds it,
     * encrypting or decrypting a file with a password waits before it writes anything; a raw key (HKDF) does not wait;
     * and once the key is derived the stream is encrypted without the lock.
     */
    @Test fun aFileDerivationAndAnotherDerivationNeverOverlap() = runBlocking {
        val s = scope()
        val cheap = KdfParams(1024, 1, 1)
        val plain = ByteArray(200_000) { (it % 251).toByte() }
        val sealed = java.io.ByteArrayOutputStream().also {
            encryptStream(java.io.ByteArrayInputStream(plain), it, "a.bin", "clave-de-prueba".toCharArray(), kdf = cheap)
        }.toByteArray()
        val started = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val holder = s.launch { KdfLock.derive(Dispatchers.Default) { started.complete(Unit); check(release.await(10, TimeUnit.SECONDS)) } }
        val out = java.io.ByteArrayOutputStream()
        val decrypted = java.io.ByteArrayOutputStream()
        var name = ""
        val encrypting = Thread {
            encryptStream(java.io.ByteArrayInputStream(plain), out, "b.bin", "clave-de-prueba".toCharArray(), kdf = cheap)
        }
        val decrypting = Thread { name = decryptStream(java.io.ByteArrayInputStream(sealed), decrypted, "clave-de-prueba".toCharArray()) }
        try {
            started.await()
            encrypting.start()
            decrypting.start()
            delay(300)
            assertTrue("both wait for the lock", encrypting.isAlive && decrypting.isAlive)
            assertEquals("nothing written before the key", 0, out.size())
            assertEquals(0, decrypted.size())
            // A raw key derives nothing of large memory: it does not wait.
            val key = ByteArray(32) { 1 }
            val raw = java.io.ByteArrayOutputStream()
            encryptStream(java.io.ByteArrayInputStream(plain), raw, "c.bin", Secret.RawKey(key))
            assertTrue(raw.size() > plain.size)
        } finally {
            release.countDown() // Never left held: the lock is the whole process's.
        }
        encrypting.join(10_000)
        decrypting.join(10_000)
        holder.join()
        assertFalse(encrypting.isAlive || decrypting.isAlive)
        assertEquals("a.bin", name)
        org.junit.Assert.assertArrayEquals(plain, decrypted.toByteArray())
        val again = java.io.ByteArrayOutputStream()
        assertEquals("b.bin", decryptStream(java.io.ByteArrayInputStream(out.toByteArray()), again, "clave-de-prueba".toCharArray()))
        org.junit.Assert.assertArrayEquals(plain, again.toByteArray())
        assertFalse(KdfLock.isLocked)
        s.cancel()
    }
}
