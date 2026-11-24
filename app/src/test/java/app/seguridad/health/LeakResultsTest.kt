package app.seguridad.health

import app.seguridad.vault.EntryType
import app.seguridad.vault.VaultEntry
import app.seguridad.vault.lastPasswordChange
import app.seguridad.vault.withVersion
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 2.1.1, task 5 (owner, 24-11-2026): the result of «Comprobar filtraciones de los accesos» stays in memory, in
 * [LeakResults] (one per process, in AppContainer), until the vault locks or another one opens (the app's lock epoch),
 * so the leaked logins can be fixed one by one. It keeps per entry only the times seen and the mark of its last password
 * change, never the password or its hash.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LeakResultsTest {
    private var epoch = 0
    private var open = true

    private fun login(id: String, password: String, changed: Long = 1_000L) =
        VaultEntry(id, EntryType.LOGIN, "t-$id", password = password, createdAt = 1, updatedAt = 1, passwordChangedAt = changed)

    /** A HIBP that answers from a table (by the password's text), counting its calls. */
    private class FakeHibp(val counts: Map<String, Int>) {
        val asked = mutableListOf<String>()
        val wiped = mutableListOf<Boolean>()
        private val held = mutableListOf<CharArray>()
        suspend fun times(chars: CharArray): Int {
            val p = String(chars)
            asked += p
            held += chars
            return counts[p] ?: 0
        }
        fun allWiped() = held.all { a -> a.all { it == '\u0000' } }
    }

    /** The app's scope, on the test's clock (backgroundScope would not run on advanceUntilIdle). */
    private fun TestScope.newLeaks() = LeakResults(CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob()), lockEpoch = { epoch })

    private fun TestScope.check(leaks: LeakResults, entries: List<VaultEntry>, hibp: FakeHibp, closed: CompletableDeferred<Unit> = CompletableDeferred()) {
        leaks.start(entries, hibp::times, isOpen = { open }, awaitClosed = { closed.await() })
    }

    private val vault = listOf(login("a", "uno"), login("b", "dos"), login("c", "uno"), login("d", "tres"))
    private val table = mapOf("uno" to 7, "tres" to 2)

    @Test fun aFinishedCheckIsKeptWithTheTimesAndTheMarkOnly() = runTest(StandardTestDispatcher()) {
        val leaks = newLeaks()
        val hibp = FakeHibp(table)
        check(leaks, vault, hibp)
        advanceUntilIdle()
        // One request per distinct password, as before.
        assertEquals(listOf("uno", "dos", "tres"), hibp.asked)
        assertTrue("the chars handed over are wiped", hibp.allWiped())
        val s = leaks.current()
        assertFalse(s.running)
        assertEquals(LeakResults.End.DONE, s.end)
        assertEquals(3, s.done)
        assertEquals(3, s.total)
        assertEquals(mapOf("a" to 7, "c" to 7, "d" to 2), leaks.breached(vault))
        assertEquals(LeakResults.Found(7, 1_000L), s.found["a"])
        assertEquals("Comprobación terminada: 3 accesos filtrados.", LeakResults.message(s))
    }

    @Test fun anotherEpochForgetsIt() = runTest(StandardTestDispatcher()) {
        val leaks = newLeaks()
        check(leaks, vault, FakeHibp(table))
        advanceUntilIdle()
        assertEquals(3, leaks.breached(vault).size)
        epoch++ // A lock (idle, leaving the app, by hand) or a switch of vault.
        assertTrue(leaks.breached(vault).isEmpty())
        assertNull(LeakResults.message(leaks.current()))
        // Even when the epoch read later is the one of the check again, what was dropped stays dropped.
        epoch--
        assertTrue(leaks.breached(vault).isEmpty())
        assertTrue(leaks.current().found.isEmpty())
    }

    @Test fun forgetDropsItAtOnce() = runTest(StandardTestDispatcher()) {
        val leaks = newLeaks()
        check(leaks, vault, FakeHibp(table))
        advanceUntilIdle()
        leaks.forget()
        assertTrue(leaks.breached(vault).isEmpty())
        assertEquals(LeakResults.End.NONE, leaks.current().end)
    }

    @Test fun aLoginWhosePasswordChangedNoLongerCountsTheOthersDo() = runTest(StandardTestDispatcher()) {
        val leaks = newLeaks()
        check(leaks, vault, FakeHibp(table))
        advanceUntilIdle()
        // Saved through the editor's path: a new password moves the store's mark; another field does not.
        val a2 = vault[0].copy(password = "nueva-y-larga").withVersion(vault[0], now = 9_000L)
        val c2 = vault[2].copy(title = "otro título").withVersion(vault[2], now = 9_000L)
        assertEquals(1_000L, lastPasswordChange(c2))
        val now = listOf(a2, vault[1], c2, vault[3])
        assertEquals(mapOf("c" to 7, "d" to 2), leaks.breached(now))
    }

    @Test fun aLoginOfBeforeWithNoMarkIsReadAsTheReminderReadsIt() = runTest(StandardTestDispatcher()) {
        val leaks = newLeaks()
        val old = VaultEntry("v", EntryType.LOGIN, "vieja", password = "uno", createdAt = 1, updatedAt = 5_000L)
        check(leaks, listOf(old), FakeHibp(table))
        advanceUntilIdle()
        assertEquals(mapOf("v" to 7), leaks.breached(listOf(old)))
        val edited = old.copy(title = "vieja 2", updatedAt = 6_000L).withVersion(old, now = 6_000L)
        assertEquals(mapOf("v" to 7), leaks.breached(listOf(edited)))
        val changed = old.copy(password = "otra-cosa", updatedAt = 7_000L).withVersion(old, now = 7_000L)
        assertTrue(leaks.breached(listOf(changed)).isEmpty())
    }

    @Test fun aDeletedOrTrashedLoginIsNotShown() = runTest(StandardTestDispatcher()) {
        val leaks = newLeaks()
        check(leaks, vault, FakeHibp(table))
        advanceUntilIdle()
        val trashed = vault[0].copy(deletedAt = 5_000L)
        assertEquals(mapOf("c" to 7), leaks.breached(listOf(trashed, vault[1], vault[2])))
        // Nor an entry that is no longer a login with a password.
        assertTrue(leaks.breached(listOf(vault[0].copy(type = EntryType.NOTE), vault[2].copy(password = ""))).isEmpty())
    }

    @Test fun aLoginAddedAfterIsNeitherLeakedNorCountedAsChecked() = runTest(StandardTestDispatcher()) {
        val leaks = newLeaks()
        check(leaks, vault, FakeHibp(table))
        advanceUntilIdle()
        val added = login("e", "uno") // Same password as a leaked one: still not checked.
        val b = leaks.breached(vault + added)
        assertFalse("e" in b)
        assertFalse("e" in leaks.current().found)
        assertEquals(3, leaks.current().total)
    }

    @Test fun aNewCheckReplacesTheResult() = runTest(StandardTestDispatcher()) {
        val leaks = newLeaks()
        check(leaks, vault, FakeHibp(table))
        advanceUntilIdle()
        check(leaks, vault, FakeHibp(mapOf("dos" to 1)))
        // From zero at once: nothing of the result of before while the new one runs.
        assertTrue(leaks.breached(vault).isEmpty())
        assertTrue(leaks.current().running)
        advanceUntilIdle()
        assertEquals(mapOf("b" to 1), leaks.breached(vault))
        assertEquals("Comprobación terminada: 1 acceso filtrado.", LeakResults.message(leaks.current()))
    }

    @Test fun aCancelledCheckKeepsWhatItFoundAndSaysSo() = runTest(StandardTestDispatcher()) {
        val leaks = newLeaks()
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        leaks.start(vault, { chars -> calls++; if (calls == 2) gate.await(); if (String(chars) == "uno") 7 else 0 }, isOpen = { open }, awaitClosed = { awaitCancellation() })
        advanceUntilIdle()
        assertTrue(leaks.current().running)
        assertEquals(1, leaks.current().done)
        leaks.cancel()
        advanceUntilIdle()
        val s = leaks.current()
        assertFalse(s.running)
        assertEquals(LeakResults.End.CANCELLED, s.end)
        assertEquals(mapOf("a" to 7, "c" to 7), leaks.breached(vault))
        assertEquals("Comprobación cancelada (1 de 3).", LeakResults.message(s))
        assertEquals("no request after the cancel", 2, calls)
    }

    @Test fun aLockStopsTheCheckAndDropsEverything() = runTest(StandardTestDispatcher()) {
        val leaks = newLeaks()
        val gate = CompletableDeferred<Unit>()
        val closed = CompletableDeferred<Unit>()
        var calls = 0
        leaks.start(vault, { chars -> calls++; if (calls == 2) gate.await(); if (String(chars) == "uno") 7 else 0 }, isOpen = { open }, awaitClosed = { closed.await() })
        advanceUntilIdle()
        assertEquals(2, leaks.current().found.size)
        open = false
        epoch++
        closed.complete(Unit)
        advanceUntilIdle()
        assertEquals(2, calls)
        val s = leaks.current()
        assertFalse(s.running)
        assertTrue(s.found.isEmpty())
        assertNull(LeakResults.message(s))
    }

    @Test fun aFailureKeepsWhatWasFound() = runTest(StandardTestDispatcher()) {
        val leaks = newLeaks()
        var calls = 0
        leaks.start(vault, { chars -> calls++; if (calls == 2) throw java.io.IOException("x"); if (String(chars) == "uno") 7 else 0 }, isOpen = { open }, awaitClosed = { awaitCancellation() })
        advanceUntilIdle()
        val s = leaks.current()
        assertEquals(LeakResults.End.FAILED, s.end)
        assertEquals(mapOf("a" to 7, "c" to 7), leaks.breached(vault))
        assertEquals("No se pudo completar la comprobación (1 de 3). Revisa la conexión.", LeakResults.message(s))
    }

    /**
     * Review M-1: a lock (or a switch) while the last request is in flight, that neither the watcher nor AppContainer
     * saw (a lock and unlock quicker than both): the check ending normally drops everything, never keeps it.
     */
    @Test fun aLockDuringTheLastRequestDropsItWhenItEnds() = runTest(StandardTestDispatcher()) {
        val leaks = newLeaks()
        var calls = 0
        leaks.start(vault, { chars -> if (++calls == 3) epoch++; if (String(chars) == "uno") 7 else 0 }, isOpen = { open }, awaitClosed = { awaitCancellation() })
        advanceUntilIdle()
        assertEquals(3, calls)
        assertTrue(leaks.state.value === LeakResults.EMPTY)
    }

    /** Review M-1: the same with a failure of that last request. */
    @Test fun aLockDuringAFailedRequestDropsIt() = runTest(StandardTestDispatcher()) {
        val leaks = newLeaks()
        var calls = 0
        leaks.start(vault, { chars -> if (++calls == 3) { epoch++; throw java.io.IOException("x") }; if (String(chars) == "uno") 7 else 0 }, isOpen = { open }, awaitClosed = { awaitCancellation() })
        advanceUntilIdle()
        assertTrue(leaks.state.value === LeakResults.EMPTY)
    }

    /** Review M-1: and a vault no longer open (the epoch the same) at the end. */
    @Test fun aClosedVaultAtTheEndDropsIt() = runTest(StandardTestDispatcher()) {
        val leaks = newLeaks()
        var calls = 0
        leaks.start(vault, { chars -> if (++calls == 3) open = false; if (String(chars) == "uno") 7 else 0 }, isOpen = { open }, awaitClosed = { awaitCancellation() })
        advanceUntilIdle()
        assertTrue(leaks.state.value === LeakResults.EMPTY)
    }

    /**
     * Review M-2: once some leaked logins are fixed, the line says how many are left, so it agrees with the rows
     * «Filtrada»; the sentence of the check itself stays as it was (R-2 of the Abogado).
     */
    @Test fun theLineSaysHowManyAreLeftAfterFixingSome() = runTest(StandardTestDispatcher()) {
        val leaks = newLeaks()
        check(leaks, vault, FakeHibp(table))
        advanceUntilIdle()
        val s = leaks.current()
        fun line(entries: List<VaultEntry>) = LeakResults.message(s, left = leaks.breached(entries, s).size)
        assertEquals("Comprobación terminada: 3 accesos filtrados.", line(vault))
        val a2 = vault[0].copy(password = "nueva-y-larga").withVersion(vault[0], now = 9_000L)
        assertEquals("Comprobación terminada: 3 accesos filtrados. Quedan 2 sin cambiar.", line(listOf(a2, vault[1], vault[2], vault[3])))
        val c2 = vault[2].copy(password = "otra-nueva-y-larga").withVersion(vault[2], now = 9_000L)
        assertEquals("Comprobación terminada: 3 accesos filtrados. Queda 1 sin cambiar.", line(listOf(a2, vault[1], c2, vault[3])))
        val d2 = vault[3].copy(deletedAt = 9_000L)
        assertEquals("Comprobación terminada: 3 accesos filtrados. Ya no queda ninguno sin cambiar.", line(listOf(a2, vault[1], c2, d2)))
        // The cancelled and the failed lines keep their own sentence first.
        val cancelled = LeakResults.Check(s.epoch, s.found, running = false, done = 2, total = 3, end = LeakResults.End.CANCELLED)
        assertEquals("Comprobación cancelada (2 de 3). Queda 1 sin cambiar.", LeakResults.message(cancelled, left = 1))
        assertEquals("Comprobación cancelada (2 de 3).", LeakResults.message(cancelled, left = 3))
        val failed = cancelled.copy(end = LeakResults.End.FAILED)
        assertEquals("No se pudo completar la comprobación (2 de 3). Revisa la conexión. Quedan 2 sin cambiar.", LeakResults.message(failed, left = 2))
        // The screen passes what counts now.
        assertTrue("LeakResults.message(check, left = breached.size)" in code("ui/screens/HealthScreen.kt"))
    }

    /** What it keeps: ids, counts and marks; nothing of the password, its hash or the entry. */
    @Test fun itKeepsNoPasswordNorHash() {
        // The code, without its comments (which say where it never goes).
        val src = code("health/LeakResults.kt").lines().filterNot { it.trim().let { l -> l.startsWith("*") || l.startsWith("/**") || l.startsWith("//") } }.joinToString("\n")
        assertTrue(Regex("""data class Found\(val times: Int, val mark: Long\)""").containsMatchIn(src))
        assertTrue("found by entry id", "val found: Map<String, Found>" in src)
        for (word in listOf("sha1", "SHA-1", "MessageDigest", "Settings", "SharedPreferences", "File(", "Bundle", "Log.", "println")) {
            assertFalse(word, word in src)
        }
    }

    /**
     * «Salud» holds the result nowhere of its own: never in remember or rememberSaveable (where leaving the screen lost it
     * and saved state would write it), but in the session object it reads.
     */
    @Test fun theScreenKeepsNoResultOfItsOwn() {
        val src = code("ui/screens/HealthScreen.kt")
        assertFalse("rememberSaveable" in src)
        assertFalse(Regex("""var breached by remember""").containsMatchIn(src))
        assertFalse(Regex("""remember[^\n]*Map<String, Int>""").containsMatchIn(src))
        assertFalse("no job of its own", Regex("""var job by remember""").containsMatchIn(src))
        assertTrue("from the session object", "leaks.breached(entries" in src)
        assertFalse("leaving the screen does not stop it", "onDispose { job?.cancel() }" in src)
        val container = code("AppContainer.kt")
        assertTrue("one per process, with the app's lock epoch", "val leakResults = LeakResults(appScope, lockEpoch = { lockEpoch })" in container)
        assertTrue("dropped at a lock", Regex("""if \(!open\) leaks\.forget\(\)""").containsMatchIn(container))
    }

    private fun code(path: String) = generateSequence(File(".").absoluteFile) { it.parentFile }
        .map { File(it, "app/src/main/java/app/seguridad/$path") }.first { it.isFile }.readText()
}
