package app.seguridad.vault

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PendingUpgradeTimeoutTest {
    /**
     * Audit D-1, recheck D-1.m2: a copy that ends just as the wait runs out (the timeout lands while the copy's last,
     * non-suspending step runs, so withTimeoutOrNull gives null although the copy ended) is still the copy it made: the
     * result is kept outside withTimeoutOrNull, so the upgrade is saved and the copy told, not asked as if there were no
     * folder. The copy here holds its thread until the wait has run out, which makes that landing certain.
     */
    @Test fun aCopyThatEndsAsTheWaitRunsOutIsStillTheCopy() = runBlocking {
        val p = PendingUpgrade(Any())
        var saved = false
        withContext(Dispatchers.Default) {
            p.afterUnlock(
                copyBefore = {
                    yield() // Dispatched from here on: a copy that ends without running undispatched to the end.
                    // Past the wait, without a suspension point that would see the cancellation: the copy ends right after
                    // the timeout landed (a cap of 10 s, so a broken timeout fails instead of hanging).
                    val until = System.nanoTime() + 10_000_000_000L
                    while (currentCoroutineContext().isActive && System.nanoTime() < until) Thread.yield()
                    assertFalse("the wait ran out", currentCoroutineContext().isActive)
                    CopyResult.Saved("seguridad-antes-de-2-0-prueba.sgbak")
                },
                save = { saved = true; true },
                stillOpen = { true },
                waitMs = 50,
            )
        }
        assertEquals("seguridad-antes-de-2-0-prueba.sgbak", p.done.value)
        assertEquals(true, saved)
        assertFalse(p.autoCopyNotDone)
        assertFalse(p.autoCopyFailed)
    }
}
