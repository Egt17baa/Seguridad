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
    /** As an older reader would decode it: names it does not know are passed over. */
    private val lenientJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    @get:Rule val tmp = TemporaryFolder()

    private fun login(pw: String, history: List<PasswordChange> = emptyList()) =
        VaultEntry("a", EntryType.LOGIN, "Web", password = pw, createdAt = 0, updatedAt = 0, passwordHistory = history)

    private fun store() =
        VaultStore(File(tmp.root, "vault.bin"), Dispatchers.Unconfined, Dispatchers.Unconfined, now = { 5_000L }, kdf = { KdfParams(1024, 1, 1) })

    // 2.0, task 3 (M-4): the list of 1.4.3 is only read; a change goes to the history ([withVersion]), and the old
    // passwords are read from both ([oldPasswords]).

    @Test fun changeKeepsPreviousFirst() {
        val one = login("uno")
        val two = login("dos").withVersion(one, 10)
        val three = login("tres").copy(history = two.history).withVersion(two, 20)
        assertEquals("tres", three.password)
        assertEquals(listOf(PasswordChange("dos", 20), PasswordChange("uno", 10)), three.oldPasswords())
        assertTrue(three.passwordHistory.isEmpty())
    }

    @Test fun legacyListIsStillRead() {
        val legacy = (7 downTo 3).map { PasswordChange("p$it", it.toLong()) }
        assertEquals(MAX_PASSWORD_HISTORY, legacy.size)
        assertEquals(legacy, login("p8", legacy).oldPasswords())
    }

    @Test fun sameOrEmptyPasswordNoHistory() {
        assertTrue(login("x").withVersion(login("x"), 1).oldPasswords().isEmpty())
        assertTrue(login("y").withVersion(login(""), 1).oldPasswords().isEmpty())
        assertEquals("y", login("y").withVersion(login(""), 1).password)
    }

    @Test fun nonLoginTypesNoHistory() {
        for (t in listOf(EntryType.SEED, EntryType.NOTE, EntryType.TOTP)) {
            val e = login("new").copy(type = t).withVersion(login("old").copy(type = t), 1)
            assertEquals("new", e.password)
            assertTrue(e.oldPasswords().isEmpty())
        }
    }

    @Test fun storeUpsertRecordsHistory() = runTest {
        val s = store().apply { load(); create("m".toCharArray()) }
        s.upsert(login("uno"))
        // The editor builds a new entry without history: the store keeps the stored one.
        s.upsert(login("dos").copy(updatedAt = 9))
        val e = (s.state.value as VaultState.Unlocked).entries.single()
        assertEquals("dos", e.password)
        assertEquals(listOf("uno"), e.oldPasswords().map { it.password })
        assertEquals(5_000L, e.oldPasswords()[0].changedAt)
    }

    @Test fun historySurvivesLockUnlock() = runTest {
        val s = store().apply { load(); create("m".toCharArray()) }
        s.upsert(login("uno"))
        s.upsert(login("dos"))
        s.lock()
        val again = store().apply { load(); unlock("m".toCharArray()) }
        val e = (again.state.value as VaultState.Unlocked).entries.single()
        assertEquals(listOf(PasswordChange("uno", 5_000L)), e.oldPasswords())
    }

    @Test fun oldJsonWithoutHistoryDecodes() {
        val json = """{"id":"a","type":"LOGIN","title":"t","createdAt":0,"updatedAt":0}"""
        val e = lenientJson.decodeFromString(VaultEntry.serializer(), json)
        assertTrue(e.passwordHistory.isEmpty())
    }
}
