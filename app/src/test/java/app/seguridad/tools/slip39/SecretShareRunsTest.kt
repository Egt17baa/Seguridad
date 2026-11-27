package app.seguridad.tools.slip39

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import app.seguridad.crypto.KdfLock
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Owner's decision for task 4, (b): one run at a time, on a snapshot; a wipe, a cancel or a second tap while a split or
 * a combine runs means its late result is refused (never shown, a recovered secret zeroed). The runs are on a test
 * dispatcher, so the order of events is the test's.
 */
class SecretShareRunsTest {
    private class Rig(scope: TestScope) {
        val runs: SecretShareRuns
        val session = SecretShareSession { runs.stop() }

        init {
            runs = SecretShareRuns(session, scope, StandardTestDispatcher(scope.testScheduler))
        }
    }

    @Test fun aRecoveredSecretThatArrivesAfterAWipeIsRefusedAndZeroed() = runTest {
        val rig = Rig(this)
        val late = ByteArray(16) { 7 }
        var cleaned = 0
        rig.runs.combine({ _ -> rig.session.wipe(); late }) { cleaned++ }
        assertTrue(rig.session.busy)
        testScheduler.advanceUntilIdle()
        assertNull(rig.session.recovered)
        assertArrayEquals(ByteArray(16), late)
        assertFalse(rig.session.busy)
        assertEquals(1, cleaned)
    }

    @Test fun partsThatArriveAfterAWipeAreNotShown() = runTest {
        val rig = Rig(this)
        var cleaned = 0
        rig.runs.split({ _ -> rig.session.wipe(); listOf(listOf("parte tardía")) }) { cleaned++ }
        testScheduler.advanceUntilIdle()
        assertNull(rig.session.parts)
        assertFalse(rig.session.busy)
        assertEquals(1, cleaned)
    }

    @Test fun aCancelDuringTheWorkRefusesTheResult() = runTest {
        val rig = Rig(this)
        val late = ByteArray(16) { 3 }
        // The cancel lands after the work returned its value: withContext drops it, and the runner zeroes it.
        rig.runs.combine({ _ -> rig.runs.stop(); late }) {}
        testScheduler.advanceUntilIdle()
        assertNull(rig.session.recovered)
        assertArrayEquals(ByteArray(16), late)
    }

    @Test fun aSecondTapSupersedesTheFirstRun() = runTest {
        val rig = Rig(this)
        val first = ByteArray(16) { 1 }
        val second = ByteArray(16) { 2 }
        var firstRan = false
        var cleaned = 0
        // The second tap comes while the first run is working: only the second one's result is shown.
        rig.runs.combine({ _ ->
            firstRan = true
            rig.runs.combine({ _ -> second }) { cleaned++ }
            first
        }) { cleaned++ }
        testScheduler.advanceUntilIdle()
        assertTrue(firstRan)
        assertArrayEquals(ByteArray(16), first)
        assertTrue(rig.session.recovered === second)
        assertArrayEquals(ByteArray(16) { 2 }, second)
        assertEquals(2, cleaned)
        assertFalse(rig.session.busy)
        assertFalse(rig.runs.running)

        // Two taps before either starts: the first never runs, one job is left, and its result is shown.
        var ran = 0
        rig.runs.split({ _ -> ran++; listOf(listOf("a")) }) {}
        rig.runs.split({ _ -> ran++; listOf(listOf("b")) }) {}
        testScheduler.advanceUntilIdle()
        assertEquals(1, ran)
        assertEquals(listOf(listOf("b")), rig.session.parts)
    }

    @Test fun aFailureIsShownOnlyForTheLastRun() = runTest {
        val rig = Rig(this)
        rig.runs.combine({ _ -> throw Slip39Failure("primera") }) {}
        testScheduler.advanceUntilIdle()
        assertEquals("primera", rig.session.error)
        rig.runs.combine({ _ -> rig.session.wipe(); throw Slip39Failure("tardía") }) {}
        testScheduler.advanceUntilIdle()
        assertNull(rig.session.error)
    }

    @Test fun theRealWorkStopsAtTheNextRoundAfterAWipe() = runTest {
        // With the real Slip39: a wipe from inside the PBKDF2 check stops the work, and nothing is shown.
        val words = listOf("src/main/assets", "../app/src/main/assets").map { File(it, "slip39_english.txt") }.first { it.exists() }
            .readLines().filter { it.isNotBlank() }
        val slip = Slip39(words)
        val rig = Rig(this)
        var rounds = 0
        rig.runs.split({ check ->
            slip.splitSecret(ByteArray(16), CharArray(0), 1, listOf(GroupSpec(2, 3)), iterationExponent = 0) {
                if (++rounds == 2) rig.session.wipe()
                check()
            }
        }) {}
        testScheduler.advanceUntilIdle()
        assertEquals(2, rounds)
        assertNull(rig.session.parts)
        assertFalse(rig.session.busy)
    }

    /**
     * 2.1.2, R-10 (from the attack h7-sharerun): with a scope whose dispatcher is not immediate, a run cancelled before
     * its body starts still runs its cleanup (it zeroes the snapshot's secret and passphrase), and its work never runs.
     */
    @Test fun cleanupRunsWhenCancelledBeforeStart() = runTest {
        val child = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob(coroutineContext[Job]))
        lateinit var runs: SecretShareRuns
        val session = SecretShareSession { runs.stop() }
        runs = SecretShareRuns(session, child, StandardTestDispatcher(testScheduler))
        var opCalls = 0
        var cleaned = 0
        runs.combine({ _ -> opCalls++; ByteArray(32) { 9 } }) { cleaned++ }
        child.cancel()
        session.wipe()
        testScheduler.advanceUntilIdle()
        assertEquals(0, opCalls)
        assertEquals(1, cleaned)
        assertNull(session.recovered)
        // The same for a split.
        val other = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob(coroutineContext[Job]))
        lateinit var runs2: SecretShareRuns
        val session2 = SecretShareSession { runs2.stop() }
        runs2 = SecretShareRuns(session2, other, StandardTestDispatcher(testScheduler))
        runs2.split({ _ -> opCalls++; listOf(listOf("a")) }) { cleaned++ }
        other.cancel()
        testScheduler.advanceUntilIdle()
        assertEquals(0, opCalls)
        assertEquals(2, cleaned)
    }

    /** 2.1.2, R-10: SLIP-0039's PBKDF2 runs holding the process' derivation lock ([KdfLock]), and lets it go after. */
    @Test fun theWorkRunsInsideTheDerivationLock() = runTest {
        val rig = Rig(this)
        var heldDuringWork = false
        rig.runs.split({ _ -> heldDuringWork = KdfLock.isLocked; listOf(listOf("a")) }) {}
        testScheduler.advanceUntilIdle()
        assertTrue(heldDuringWork)
        assertFalse(KdfLock.isLocked)
        assertEquals(listOf(listOf("a")), rig.session.parts)
    }
}
