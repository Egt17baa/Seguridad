package app.seguridad.vault

import app.seguridad.crypto.CryptoFailure
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * «Comprobar una copia», owner's decision (b): a second tap, leaving the screen (a wipe) or a lock of the vault while a
 * check runs never leaves its late result shown, and the master password typed is zeroed after the run and on any stop.
 * The runs are on the test's scheduler, so the order of events is the test's.
 */
class BackupCheckSessionTest {
    private val report = BackupCheckReport(2, mapOf(EntryType.NOTE to 1), 0, 0, emptyList())

    private class Rig(scope: TestScope, epoch: () -> Int = { 0 }) {
        val session: BackupCheckSession = BackupCheckSession { runs.stop() }
        val runs: BackupCheckRuns = BackupCheckRuns(session, scope, epoch)
    }

    private fun master() = "maestra-de-prueba".toCharArray()

    @Test fun aReportThatArrivesAfterAWipeIsNotShown() = runTest {
        val rig = Rig(this)
        val pw = master()
        rig.runs.start(pw) { rig.session.wipe(); report }
        assertTrue(rig.session.busy)
        testScheduler.advanceUntilIdle()
        assertNull(rig.session.report)
        assertNull(rig.session.error)
        assertFalse(rig.session.busy)
        assertArrayEquals(CharArray(pw.size), pw)
    }

    @Test fun theReportOfTheRunIsShownAndThePasswordZeroed() = runTest {
        val rig = Rig(this)
        val pw = master()
        var seen: String? = null
        rig.runs.start(pw) { seen = String(it); report }
        testScheduler.advanceUntilIdle()
        assertEquals("maestra-de-prueba", seen)
        assertEquals(report, rig.session.report)
        assertFalse(rig.session.busy)
        assertFalse(rig.runs.running)
        assertArrayEquals(CharArray(pw.size), pw)
    }

    @Test fun aSecondTapSupersedesTheFirstRun() = runTest {
        val rig = Rig(this)
        val first = master()
        val second = master()
        val late = report.copy(version = 1)
        rig.runs.start(first) {
            rig.runs.start(second) { report }
            late
        }
        testScheduler.advanceUntilIdle()
        assertEquals(report, rig.session.report)
        assertArrayEquals(CharArray(first.size), first)
        assertArrayEquals(CharArray(second.size), second)
        assertFalse(rig.session.busy)
    }

    @Test fun aLockDuringTheRunDropsItsResult() = runTest {
        var epoch = 4
        val rig = Rig(this) { epoch }
        rig.runs.start(master()) { epoch++; report }
        testScheduler.advanceUntilIdle()
        assertNull(rig.session.report)
        assertNull(rig.session.error)
        assertFalse(rig.session.busy)
        // Its failure is dropped the same way.
        rig.runs.start(master()) { epoch++; throw CryptoFailure("Contraseña incorrecta o datos dañados") }
        testScheduler.advanceUntilIdle()
        assertNull(rig.session.error)
    }

    @Test fun stopCancelsTheWorkAndZeroesThePasswordAtOnce() = runTest {
        val rig = Rig(this)
        val pw = master()
        var cancelled = false
        rig.runs.start(pw) {
            try {
                awaitCancellation()
            } finally {
                cancelled = true
            }
        }
        testScheduler.advanceUntilIdle()
        assertTrue(rig.runs.running)
        rig.runs.stop()
        // Zeroed now, not when the work notices.
        assertArrayEquals(CharArray(pw.size), pw)
        assertFalse(rig.session.busy)
        testScheduler.advanceUntilIdle()
        assertTrue(cancelled)
        assertFalse(rig.runs.running)
        assertNull(rig.session.report)
    }

    @Test fun aNewRunWaitsForTheOneItStopped() = runTest {
        val rig = Rig(this)
        val order = mutableListOf<String>()
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        rig.runs.start(master()) {
            // Work that cannot be cut (as Argon2id): it ignores the cancellation and ends on its own.
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { gate.await() }
            order += "primera"
            report
        }
        testScheduler.advanceUntilIdle()
        rig.runs.start(master()) { order += "segunda"; report }
        testScheduler.advanceUntilIdle()
        assertEquals(emptyList<String>(), order)
        gate.complete(Unit)
        testScheduler.advanceUntilIdle()
        assertEquals(listOf("primera", "segunda"), order)
        assertEquals(report, rig.session.report)
    }

    @Test fun aFailureShowsItsMessage() = runTest {
        val rig = Rig(this)
        rig.runs.start(master()) { throw CryptoFailure(BACKUP_NEWER) }
        testScheduler.advanceUntilIdle()
        assertEquals(BACKUP_NEWER, rig.session.error)
        assertNull(rig.session.report)
        rig.runs.start(master()) { throw java.io.IOException("x") }
        testScheduler.advanceUntilIdle()
        assertEquals(BACKUP_CHECK_READ_FAILED, rig.session.error)
        rig.runs.start(master()) { throw IllegalStateException("x") }
        testScheduler.advanceUntilIdle()
        assertEquals(BACKUP_CHECK_FAILED, rig.session.error)
        // A new run clears the old result first.
        rig.runs.start(master()) { report }
        assertNull(rig.session.error)
        testScheduler.advanceUntilIdle()
        assertEquals(report, rig.session.report)
    }

    @Test fun wipeForgetsThePasswordTheFileAndTheReport() {
        var stopped = 0
        val s = BackupCheckSession(onWipe = { stopped++ })
        s.password = "maestra-de-prueba"
        s.source = "content://copia"
        s.fileName = "seguridad-boveda-20261103.sgbak"
        s.report = report
        s.error = "x"
        s.busy = true
        val token = s.token()
        s.wipe()
        assertEquals(1, stopped)
        assertFalse(s.isCurrent(token))
        assertEquals("", s.password)
        assertNull(s.source)
        assertNull(s.fileName)
        assertNull(s.report)
        assertNull(s.error)
        assertFalse(s.busy)
    }
}
