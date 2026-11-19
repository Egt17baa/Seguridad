package app.seguridad.health

import app.seguridad.vault.EntryType
import app.seguridad.vault.VaultData
import app.seguridad.vault.VaultEntry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round 2 of the fixes of the attack, point 9 (the director's closure, duda 2): «Salud» is worked out by [healthView],
 * which turns running out of memory into [HealthView.NO_MEMORY] (nothing held, nothing thrown): the screen then shows
 * LIST_NO_MEMORY and the vault stays open. The error is made by hand through [beforeHealthCompute]: no test runs out of
 * memory for real.
 */
class HealthViewTest {
    @After fun reset() {
        beforeHealthCompute = {}
    }

    private val now = 1_800_000_000_000L
    private val data = VaultData(entries = listOf(VaultEntry("a", EntryType.LOGIN, "Foro", password = "hola1", createdAt = now, updatedAt = now)))

    @Test fun outOfMemoryGivesNoMemoryAndThrowsNothing() {
        beforeHealthCompute = { throw OutOfMemoryError() }
        val v = healthView(data, now, emptyMap(), missionsOff = false)
        assertTrue(v.noMemory)
        assertEquals(HealthView.NO_MEMORY, v)
        assertTrue(v.items.isEmpty() && v.missions == null)
    }

    @Test fun withoutTheSeamAWeakPasswordIsWeak() {
        val v = healthView(data, now, emptyMap(), missionsOff = false)
        assertEquals(false, v.noMemory)
        assertTrue(Issue.WEAK in v.items.single { it.entryId == "a" }.issues)
        assertTrue(v.missions != null)
        assertEquals(null, healthView(data, now, emptyMap(), missionsOff = true).missions)
    }

    /** Round 3: a CancellationException from `active` at its k-th call goes out as it is, never as NO_MEMORY. */
    @Test fun aCancellationFromActiveGoesOutAsItIs() {
        val many = VaultData(entries = List(2_000) { VaultEntry("a$it", EntryType.LOGIN, "Foro", password = "hola$it", createdAt = now, updatedAt = now) })
        for (k in listOf(1, 3, 8)) {
            var calls = 0
            try {
                healthView(many, now, emptyMap(), missionsOff = false) { if (++calls == k) throw kotlinx.coroutines.CancellationException("parada") }
                throw AssertionError("k = $k: not cancelled ($calls calls)")
            } catch (e: kotlinx.coroutines.CancellationException) {
                assertEquals("parada", e.message)
            }
            assertEquals(k, calls)
        }
    }

    /** Round 3: vaultHealth calls `active` at least once every HEALTH_ACTIVE_EVERY entries. */
    @Test fun vaultHealthCallsActiveBetweenEntries() {
        val entries = List(10_000) { VaultEntry("a$it", EntryType.LOGIN, "Foro", password = "hola$it", createdAt = now, updatedAt = now) }
        var calls = 0
        vaultHealth(entries, now) { calls++ }
        assertTrue("$calls calls for ${entries.size} entries", calls >= entries.size / HEALTH_ACTIVE_EVERY)
    }
}
