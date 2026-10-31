package app.seguridad.tools.codes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import app.seguridad.vault.LockReason

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
        assertEquals(2, s.rows.size)
        assertNotEquals(answers[0], s.rows[0].answer)
        assertNotEquals(answers[1], s.rows[1].answer)
        assertTrue(s.rows[0].answer.startsWith("answer-a-"))
        // Not secrets: the sites and the questions stay.
        assertEquals("¿Primera mascota?", s.rows[0].question)
        assertEquals("¿Calle?", s.rows[1].question)
        assertEquals("banco.es", s.recoverySite)
        assertEquals("correo.es", s.answersSite)
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

    /** The vault opens, then locks for [reason]; [usedAt] is the last tap on the screen, [at] the time of the lock. */
    private fun CodesSession.lockedBy(reason: LockReason, foreground: Boolean = true, usedAt: Long = 0, at: Long = 1_000) {
        used(usedAt)
        vaultStateIs(unlocked = true) { error("not a lock") }
        vaultStateIs(unlocked = false) { LockSeen(reason, foreground, at, lockAfterMs = 300_000) }
    }

    @Test fun onlyAChangeFromOpenToLockedIsALock() {
        val s = filled()
        val pin = s.pin
        s.vaultStateIs(unlocked = false) { error("Loading or locked at first: not a lock") }
        s.vaultStateIs(unlocked = true) { error("not a lock") }
        s.vaultStateIs(unlocked = true) { error("not a lock") }
        assertEquals(pin, s.pin)
    }

    @Test fun aLockByHandWipesItOnThisTabOrAnother() {
        val s = filled()
        val pin = s.pin
        s.lockedBy(LockReason.MANUAL)
        assertNotEquals(pin, s.pin)
        // «Bloquear» in Bóveda or Contactos, with «PIN y códigos» on the Herramientas tab (stopped): wiped too.
        val other = filled()
        val otherPin = other.pin
        other.stopped(0)
        other.lockedBy(LockReason.MANUAL, at = 2_000)
        assertNotEquals(otherPin, other.pin)
        assertEquals("banco.es", other.recoverySite)
    }

    @Test fun theIdleLockKeepsAScreenInUse() {
        val s = filled()
        val pin = s.pin
        s.lockedBy(LockReason.IDLE, usedAt = 1_000_000, at = 1_000_000 + 299_999)
        assertEquals("Typed on it within the lock time: kept", pin, s.pin)
    }

    @Test fun theIdleLockWipesAScreenLeftUnused() {
        val s = filled()
        val pin = s.pin
        s.lockedBy(LockReason.IDLE, usedAt = 1_000_000, at = 1_000_000 + 300_000)
        assertNotEquals(pin, s.pin)
        val other = filled()
        val otherPin = other.pin
        other.stopped(0) // On another tab, unused just as long: wiped as well.
        other.lockedBy(LockReason.IDLE, usedAt = 0, at = 400_000)
        assertNotEquals(otherPin, other.pin)
    }

    @Test fun theLockFromLeavingTheAppIsLeftToTheTimeAwayRule() {
        // Leaving the app locks the vault (MainActivity.onStop) with the foreground flag already false: copying a code,
        // pasting it in the bank's site and coming back within the time must not lose it.
        val s = filled()
        val pin = s.pin
        s.stopped(0)
        s.lockedBy(LockReason.LEFT_APP, foreground = false, usedAt = 0, at = 10_000_000)
        assertFalse(s.started(30_000, lockAfterMs = 0))
        assertEquals(pin, s.pin)
        // Any other lock seen while the app is in the background (the idle check of onStart) is the same.
        val t = filled()
        val tPin = t.pin
        t.lockedBy(LockReason.IDLE, foreground = false, usedAt = 0, at = 10_000_000)
        t.lockedBy(LockReason.MANUAL, foreground = false)
        assertEquals(tPin, t.pin)
    }

    @Test fun `M3 split screen, back from the other app after the time away, everything drawn is new`() {
        val s = filled()
        val pin = s.pin
        val codes = s.codes
        val answers = s.rows.map { it.answer }
        s.otherAppFocused(0)
        assertFalse(s.otherAppLeft(59_999, lockAfterMs = 0)) // A short trip to the browser keeps it.
        assertEquals(pin, s.pin)
        s.otherAppFocused(100_000)
        assertFalse(s.otherAppLeft(100_000 + 299_999, lockAfterMs = 300_000))
        assertEquals(pin, s.pin)
        s.otherAppFocused(1_000_000)
        assertTrue(s.otherAppLeft(1_000_000 + 60_000, lockAfterMs = 0))
        assertNotEquals(pin, s.pin)
        assertNotEquals(codes, s.codes)
        assertNotEquals(answers, s.rows.map { it.answer })
        // Not secrets: they stay.
        assertEquals("banco.es", s.recoverySite)
        assertEquals("¿Calle?", s.rows[1].question)
    }

    @Test fun `M3 a lock while the user is in the other app wipes, even right after using the screen`() {
        val s = filled()
        val pin = s.pin
        s.vaultStateIs(unlocked = true) { error("not a lock") }
        s.used(1_000)
        s.otherAppFocused(1_000)
        s.vaultStateIs(unlocked = false) { LockSeen(LockReason.IDLE, appInForeground = true, now = 2_000, lockAfterMs = 300_000) }
        assertNotEquals(pin, s.pin)
        // Back in the app afterwards: nothing more to do.
        val again = s.pin
        assertFalse(s.otherAppLeft(3_000, lockAfterMs = 0))
        assertEquals(again, s.pin)
    }

    @Test fun `M3 the idle lock in the app, after coming back from the other app, keeps a screen in use`() {
        val s = filled()
        val pin = s.pin
        s.vaultStateIs(unlocked = true) { error("not a lock") }
        s.otherAppFocused(0)
        assertFalse(s.otherAppLeft(10_000, lockAfterMs = 0))
        s.used(10_000)
        s.vaultStateIs(unlocked = false) { LockSeen(LockReason.IDLE, appInForeground = true, now = 20_000, lockAfterMs = 0) }
        assertEquals(pin, s.pin)
    }

    @Test fun `M3 the other app never uses up the stop of another tab or of leaving the app`() {
        // On another tab (the composition went, so the time away counts from there), then a trip to the other app.
        val s = filled()
        val pin = s.pin
        s.stopped(0)
        s.otherAppFocused(10_000)
        assertFalse(s.otherAppLeft(20_000, lockAfterMs = 0))
        assertTrue("The tab's stop still counts", s.started(60_000, lockAfterMs = 0))
        assertNotEquals(pin, s.pin)
        // In the other app, then Home (a real ON_STOP): the trip counts from the first of the two, once.
        val t = filled()
        val tPin = t.pin
        t.otherAppFocused(0)
        t.stopped(5_000)
        assertFalse(t.started(50_000, lockAfterMs = 0))
        assertFalse(t.otherAppLeft(50_001, lockAfterMs = 0)) // Top-resumed comes right after onStart on the phone.
        assertEquals(tPin, t.pin)
        // Never focused away: back does nothing.
        assertFalse(filled().otherAppLeft(10_000_000, lockAfterMs = 0))
    }

    @Test fun `N1 a re-creation while the user is in the other app does not forget the time away`() {
        // Split screen, the browser has the focus; the phone is rotated (or the divider dragged, the theme changes): the
        // activity is re-created, the old screen stops, the new one starts, and no top-resumed callback comes.
        val s = filled()
        val pin = s.pin
        val codes = s.codes
        s.otherAppFocused(0)
        s.stopped(1_000)
        assertFalse(s.started(1_100, lockAfterMs = 0))
        assertEquals(pin, s.pin)
        // Ten minutes later, back in Seguridad: everything drawn is new; the sites and the questions stay.
        assertTrue(s.otherAppLeft(600_000, lockAfterMs = 0))
        assertNotEquals(pin, s.pin)
        assertNotEquals(codes, s.codes)
        assertEquals("banco.es", s.recoverySite)
        assertEquals("¿Calle?", s.rows[1].question)
        // A re-start that comes after the time away (still in the other app) wipes then, and the trip goes on from there.
        val t = filled()
        t.otherAppFocused(0)
        t.stopped(1_000)
        val tPin = t.pin
        assertTrue(t.started(70_000, lockAfterMs = 0))
        assertNotEquals(tPin, t.pin)
        val again = t.pin
        assertFalse(t.otherAppLeft(70_000 + 59_999, lockAfterMs = 0))
        assertEquals(again, t.pin)
    }

    @Test fun `M3 the rule on its own, for other screens`() {
        val a = ScreenAway(createdAt = 0)
        a.otherAppFocused(0)
        assertTrue(a.inOtherApp)
        assertTrue(a.otherAppLeft(60_000, lockAfterMs = 0))
        assertFalse(a.inOtherApp)
        a.stopped(100_000)
        assertFalse(a.started(159_999, lockAfterMs = 0))
        a.stopped(200_000)
        assertTrue(a.started(260_000, lockAfterMs = 0))
        // The lock: wiped by hand always, idle only when unused, never one from leaving the app; always in the other app.
        a.used(1_000)
        assertTrue(a.lockWipes(LockSeen(LockReason.MANUAL, true, 1_001, 300_000)))
        assertFalse(a.lockWipes(LockSeen(LockReason.IDLE, true, 1_001, 300_000)))
        assertTrue(a.lockWipes(LockSeen(LockReason.IDLE, true, 301_000, 300_000)))
        assertFalse(a.lockWipes(LockSeen(LockReason.LEFT_APP, false, 10_000_000, 0)))
        assertFalse(a.lockWipes(LockSeen(LockReason.IDLE, false, 10_000_000, 0)))
        a.otherAppFocused(1_002)
        assertTrue(a.lockWipes(LockSeen(LockReason.IDLE, true, 1_003, 300_000)))
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

    @Test fun aRetryAfterAFailedWriteKeepsTheIdSoItNeverAddsASecondNote() {
        // MN (T8 of §2.5): a write that failed may still have been saved (upsert throwing after the file was written). The
        // retry of the same note uses the same id, so it replaces that note instead of adding another.
        val s = filled()
        val first = s.requestSave(SaveKind.RECOVERY, unlocked = true, now = 1, id = "a")!!
        s.beginWrite(first)
        s.endWrite(first, saved = false)
        val retry = s.requestSave(SaveKind.RECOVERY, unlocked = true, now = 2, id = "b")!!
        assertEquals("a", retry.entry.id)
        assertEquals(first.entry.notes, retry.entry.notes)
        // Also when the retry waits for the vault to open.
        s.beginWrite(retry)
        s.endWrite(retry, saved = false)
        assertNull(s.requestSave(SaveKind.RECOVERY, unlocked = false, now = 3, id = "c"))
        assertEquals("a", s.pending!!.entry.id)
        s.cancelSave()
        // The other kind has its own id.
        assertEquals("d", s.requestSave(SaveKind.ANSWERS, unlocked = true, now = 4, id = "d")!!.entry.id)
    }

    @Test fun aChangedNoteAfterAFailedWriteGetsANewId() {
        // Other codes, another site or a wipe: what failed may be in the vault, and it must not be overwritten.
        val s = filled()
        fun fail(id: String): PendingSave {
            val save = s.requestSave(SaveKind.RECOVERY, unlocked = true, now = 1, id = id)!!
            s.beginWrite(save)
            s.endWrite(save, saved = false)
            return save
        }
        fail("a")
        s.newCodes()
        assertEquals("b", fail("b").entry.id)
        s.recoverySite = "otro.es"
        assertEquals("c", fail("c").entry.id)
        s.wipe()
        assertEquals("d", fail("d").entry.id)
        s.clear()
        s.recoverySite = "otro.es"
        assertEquals("e", s.requestSave(SaveKind.RECOVERY, unlocked = true, now = 1, id = "e")!!.entry.id)
    }

    @Test fun aWrittenNoteIsNotReusedByTheNextSave() {
        val s = filled()
        val first = s.requestSave(SaveKind.ANSWERS, unlocked = true, now = 1, id = "a")!!
        s.beginWrite(first)
        s.endWrite(first, saved = false)
        val retry = s.requestSave(SaveKind.ANSWERS, unlocked = true, now = 2, id = "b")!!
        s.beginWrite(retry)
        s.endWrite(retry, saved = true)
        // The screen was emptied; the same site and questions again are a new note.
        s.answersSite = "correo.es"
        assertEquals("c", s.requestSave(SaveKind.ANSWERS, unlocked = true, now = 3, id = "c")!!.entry.id)
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
