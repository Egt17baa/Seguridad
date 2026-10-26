package app.seguridad.vault

import app.seguridad.crypto.KdfParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class HistoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun login(pw: String, history: List<PasswordChange> = emptyList()) =
        VaultEntry("a", EntryType.LOGIN, "Web", password = pw, createdAt = 0, updatedAt = 0, passwordHistory = history)

    private fun store() =
        VaultStore(File(tmp.root, "vault.bin"), Dispatchers.Unconfined, Dispatchers.Unconfined, now = { 5_000L }, kdf = { KdfParams(1024, 1, 1) })

    @Test fun changeKeepsPreviousFirst() {
        val a = login("uno").withNewPassword("dos", 10).withNewPassword("tres", 20)
        assertEquals("tres", a.password)
        assertEquals(listOf(PasswordChange("dos", 20), PasswordChange("uno", 10)), a.passwordHistory)
    }

    @Test fun keepsAtMostFive() {
        var e = login("p0")
        for (i in 1..8) e = e.withNewPassword("p$i", i.toLong())
        assertEquals(5, e.passwordHistory.size)
        assertEquals(listOf("p7", "p6", "p5", "p4", "p3"), e.passwordHistory.map { it.password })
    }

    @Test fun sameOrEmptyPasswordNoHistory() {
        assertTrue(login("x").withNewPassword("x", 1).passwordHistory.isEmpty())
        assertTrue(login("").withNewPassword("y", 1).passwordHistory.isEmpty())
        assertEquals("y", login("").withNewPassword("y", 1).password)
    }

    @Test fun nonLoginTypesNoHistory() {
        for (t in listOf(EntryType.SEED, EntryType.NOTE, EntryType.TOTP)) {
            val e = login("old").copy(type = t).withNewPassword("new", 1)
            assertEquals("new", e.password)
            assertTrue(e.passwordHistory.isEmpty())
        }
    }

    @Test fun storeUpsertRecordsHistory() = runTest {
        val s = store().apply { load(); create("m".toCharArray()) }
        s.upsert(login("uno"))
        // The editor builds a new entry without history: the store keeps the stored one.
        s.upsert(login("dos").copy(updatedAt = 9))
        val e = (s.state.value as VaultState.Unlocked).entries.single()
        assertEquals("dos", e.password)
        assertEquals(listOf("uno"), e.passwordHistory.map { it.password })
        assertEquals(5_000L, e.passwordHistory[0].changedAt)
    }

    @Test fun historySurvivesLockUnlock() = runTest {
        val s = store().apply { load(); create("m".toCharArray()) }
        s.upsert(login("uno"))
        s.upsert(login("dos"))
        s.lock()
        val again = store().apply { load(); unlock("m".toCharArray()) }
        val e = (again.state.value as VaultState.Unlocked).entries.single()
        assertEquals(listOf(PasswordChange("uno", 5_000L)), e.passwordHistory)
    }

    @Test fun oldJsonWithoutHistoryDecodes() {
        val json = """{"id":"a","type":"LOGIN","title":"t","createdAt":0,"updatedAt":0}"""
        val e = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }.decodeFromString(VaultEntry.serializer(), json)
        assertTrue(e.passwordHistory.isEmpty())
    }
}
