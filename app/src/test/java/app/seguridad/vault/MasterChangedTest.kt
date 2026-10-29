package app.seguridad.vault

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** M-3: what the dialog after a change of master password shows lives outside the activity. */
@OptIn(ExperimentalCoroutinesApi::class)
class MasterChangedTest {
    @Test fun theDialogOpensFromOutsideAnyScreenAndClosesOnce() = runTest {
        val m = MasterChanged(backgroundScope, replace = { error("not asked") }, now = { 0 })
        assertFalse(m.state.value.open)
        m.changed(fingerprintFailed = true)
        assertTrue(m.state.value.open)
        assertTrue(m.state.value.fingerprintFailed)
        m.close()
        assertFalse(m.state.value.open)
        assertFalse(m.state.value.fingerprintFailed)
    }

    @Test fun theAnswerIsKeptEvenIfNoScreenWaitsForIt() = runTest {
        val answer = CompletableDeferred<AutoBackupResult>()
        var calls = 0
        val m = MasterChanged(backgroundScope, replace = { calls++; answer.await() }, now = { 500 })
        m.changed(fingerprintFailed = false)
        assertTrue(m.replace())
        runCurrent()
        assertTrue(m.state.value.busy)
        assertEquals(500, m.state.value.requestedAt)
        assertFalse("One at a time", m.replace())
        // The activity is re-created here: nothing in it was waiting. The answer still lands in the holder.
        answer.complete(AutoBackupResult("f", null, AUTO_BACKUP_REPLACE_WAITS))
        runCurrent()
        assertFalse(m.state.value.busy)
        assertTrue(m.state.value.answered)
        assertEquals(AUTO_BACKUP_REPLACE_WAITS, m.state.value.answerError)
        assertEquals(1, calls)
    }

    @Test fun aFailingCallIsAnAnswerAndNeverEscapes() = runTest {
        val m = MasterChanged(backgroundScope, replace = { throw IllegalStateException("x") }, now = { 1 })
        m.changed(fingerprintFailed = false)
        m.replace()
        runCurrent()
        assertTrue(m.state.value.answered)
        assertEquals(AUTO_BACKUP_FAILED, m.state.value.answerError)
    }

    @Test fun anAnswerAfterClosingDoesNotReopenIt() = runTest {
        val answer = CompletableDeferred<AutoBackupResult>()
        val m = MasterChanged(backgroundScope, replace = { answer.await() }, now = { 1 })
        m.changed(fingerprintFailed = false)
        m.replace()
        runCurrent()
        m.close()
        answer.complete(AutoBackupResult("f", 1, null))
        runCurrent()
        assertFalse(m.state.value.open)
        assertNull(m.state.value.answerError)
        assertFalse(m.state.value.answered)
    }
}
