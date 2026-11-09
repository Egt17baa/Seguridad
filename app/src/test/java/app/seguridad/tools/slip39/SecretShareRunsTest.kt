package app.seguridad.tools.slip39

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
}
