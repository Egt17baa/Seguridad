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
}
