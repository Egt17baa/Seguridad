package app.seguridad.tools.codes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** «PIN y códigos» outside the screen: what survives a rotation, and when what was drawn goes (I-1). */
class CodesSessionTest {
    private var drawn = 0
    private val wordList = listOf("uno", "dos", "tres")

    /** Each draw is different, so a test can tell a new draw from the old one. */
    private fun session() = CodesSession(
        pinOf = { n -> "pin$n-${drawn++}" },
        codesOf = { listOf("code-${drawn++}") },
        answerOf = { _, plain -> "answer-${if (plain) "p" else "a"}-${drawn++}" },
    )

    private fun filled(): CodesSession = session().apply {
        wordsLoaded(wordList)
        recoverySite = "banco.es"
        answersSite = "correo.es"
        rows[0].question = "¿Primera mascota?"
        addQuestion()
        rows[1].question = "¿Calle?"
    }

    @Test fun awayTooLongUsesTheLockTimeWithAMinuteAtLeast() {
        assertFalse(awayTooLong(59_999, 0)) // «Al salir»: a fixed minute.
        assertTrue(awayTooLong(60_000, 0))
        assertFalse(awayTooLong(299_999, 300_000))
        assertTrue(awayTooLong(300_000, 300_000))
        assertTrue(awayTooLong(900_001, 900_000))
        assertFalse("A clock that went backwards is never «away too long»", awayTooLong(-5_000, 0))
    }

    @Test fun answersWaitForTheWordListAndAreDrawnWhenItArrives() {
        val s = session()
        assertNull(s.words)
        assertEquals("", s.rows.single().answer)
        assertFalse(s.canDrawAnswers)
        s.newAnswer(0) // No list yet: nothing happens.
        assertEquals("", s.rows.single().answer)
        s.wordsLoaded(wordList)
        assertTrue(s.canDrawAnswers)
        assertTrue(s.rows.single().answer.startsWith("answer-p-"))
    }

    @Test fun shortTripsAwayKeepEverything() {
        val s = filled()
        val pin = s.pin
        val codes = s.codes
        val answers = s.rows.map { it.answer }
        s.stopped(1_000)
        assertFalse(s.started(1_000 + 59_999, lockAfterMs = 0))
        s.stopped(100_000)
        assertFalse(s.started(100_000 + 299_000, lockAfterMs = 300_000))
        assertEquals(pin, s.pin)
        assertEquals(codes, s.codes)
        assertEquals(answers, s.rows.map { it.answer })
        assertEquals("banco.es", s.recoverySite)
    }

    @Test fun backAfterTheLockTimeEverythingDrawnIsGoneAndTheScreenIsEmpty() {
        val s = filled()
        s.chooseDigits(8)
        s.setPlainAnswers(false)
        val pin = s.pin
        val codes = s.codes
        val answers = s.rows.map { it.answer }
        s.requestSave(SaveKind.ANSWERS, unlocked = false, now = 0, id = "x")
        assertNotNull(s.pending)
        s.stopped(5_000)
        assertTrue(s.started(5_000 + 300_000, lockAfterMs = 300_000))
        assertNotEquals(pin, s.pin)
        assertTrue(s.pin.startsWith("pin8-")) // The chosen length and form stay: they are not secrets.
        assertNotEquals(codes, s.codes)
        assertEquals(1, s.rows.size)
        assertNotEquals(answers[0], s.rows[0].answer)
        assertTrue(s.rows[0].answer.startsWith("answer-a-"))
        assertEquals("", s.rows[0].question)
        assertEquals("", s.recoverySite)
        assertEquals("", s.answersSite)
        assertNull("A save waiting for the vault holds the secrets: it goes too", s.pending)
    }

    @Test fun startingWithoutAStopFirstDoesNothing() {
        val s = filled()
        val pin = s.pin
        assertFalse(s.started(10_000_000, lockAfterMs = 0)) // First composition, or ON_START after a rotation.
        assertEquals(pin, s.pin)
        s.stopped(0)
        assertFalse(s.started(1_000, 0))
        assertFalse("The stop is used once", s.started(10_000_000, 0))
        assertEquals(pin, s.pin)
    }

    @Test fun theFirstStopOfATripCounts() {
        val s = filled()
        s.stopped(0) // ON_STOP
        s.stopped(50_000) // The composition going after it (a rotation, another tab): the time away still counts from 0.
        assertTrue(s.started(60_000, lockAfterMs = 0))
    }

    @Test fun theVaultLockingWhileTheScreenIsShownWipesIt() {
        val s = filled()
        val pin = s.pin
        s.vaultStateIs(unlocked = false) // Loading or locked at first: nothing to do.
        assertEquals(pin, s.pin)
        s.vaultStateIs(unlocked = true)
        s.vaultStateIs(unlocked = true)
        assertEquals(pin, s.pin)
        s.vaultStateIs(unlocked = false)
        assertNotEquals(pin, s.pin)
        assertEquals("", s.recoverySite)
    }

    @Test fun theLockFromLeavingTheAppIsLeftToTheTimeAwayRule() {
        // Leaving the app locks the vault (MainActivity.onStop) after the screen saw ON_STOP: copying a code, pasting it
        // in the bank's site and coming back within the time must not lose it.
        val s = filled()
        val pin = s.pin
        s.vaultStateIs(unlocked = true)
        s.stopped(0)
        s.vaultStateIs(unlocked = false)
        assertFalse(s.started(30_000, lockAfterMs = 0))
        assertEquals(pin, s.pin)
    }

    @Test fun clearingTheHolderDropsEverySecret() {
        val s = filled()
        s.requestSave(SaveKind.RECOVERY, unlocked = false, now = 0, id = "x")
        s.clear()
        assertEquals("", s.pin)
        assertTrue(s.codes.isEmpty())
        assertTrue(s.rows.isEmpty())
        assertNull(s.pending)
        assertNull(s.words)
        assertEquals("", s.recoverySite)
        assertEquals("", s.answersSite)
    }

    @Test fun oneSaveAtATimeAndAWrittenNoteLeavesTheScreen() {
        val s = filled()
        val save = s.requestSave(SaveKind.RECOVERY, unlocked = true, now = 1, id = "a")!!
        assertTrue(s.beginWrite(save))
        assertNull("Busy: a second tap adds no second note", s.requestSave(SaveKind.ANSWERS, unlocked = true, now = 2, id = "b"))
        assertFalse(s.beginWrite(save))
        val codes = s.codes
        s.endWrite(save, saved = true)
        assertFalse(s.saving)
        assertNotEquals(codes, s.codes)
        assertEquals("", s.recoverySite)
        assertEquals("correo.es", s.answersSite) // Only what was saved goes.
    }

    @Test fun aFailedWriteClearsNothing() {
        val s = filled()
        val save = s.requestSave(SaveKind.ANSWERS, unlocked = true, now = 1, id = "a")!!
        s.beginWrite(save)
        val answers = s.rows.map { it.answer }
        s.endWrite(save, saved = false)
        assertEquals(answers, s.rows.map { it.answer })
        assertEquals("correo.es", s.answersSite)
    }

    @Test fun lockedVaultQueuesTheSaveAndCancelDropsIt() {
        val s = filled()
        assertNull(s.requestSave(SaveKind.RECOVERY, unlocked = false, now = 1, id = "a"))
        val queued = s.pending!!
        assertEquals("a", queued.entry.id)
        assertTrue("banco.es" in queued.entry.title)
        s.cancelSave()
        assertNull(s.pending)
    }
}
