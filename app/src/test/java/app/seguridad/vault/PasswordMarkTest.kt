package app.seguridad.vault

import app.seguridad.crypto.KdfParams
import app.seguridad.health.Issue
import app.seguridad.health.vaultHealth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File

/**
 * Review fixes, CR-1: for an entry of before 2.0 (no [VaultEntry.passwordChangedAt]), an edit that does not change the
 * password («Favorita», «Etiquetar», an attachment added or removed, «Borrar historial», «Duplicar») freezes the date of
 * its last password change ([frozenPasswordMark]) instead of letting it fall back on the new [VaultEntry.updatedAt]:
 * «Salud» keeps «Antigua» and the reminder to change it keeps its date. The mark never makes a version, an entry with no
 * password stays without one, and a new password is dated now.
 */
class PasswordMarkTest {
    @get:Rule val tmp = TemporaryFolder()

    private val year = 365L * 24 * 3600 * 1000
    private val old = 1_700_000_000_000L
    private var clock = old + 2 * year
    private val master = "maestra-de-prueba-de-la-marca"

    private fun store(dir: File) = VaultStore(File(dir, "vault.bin"), Dispatchers.Unconfined, Dispatchers.Unconfined, now = { clock }, kdf = { KdfParams(1024, 1, 1) })

    /** A store whose vault holds [entries] as they were written before 2.0 (no mark), open with the master. */
    private fun withEntries(vararg entries: VaultEntry): VaultStore = runBlocking {
        val dir = tmp.newFolder()
        val s = store(dir).apply { load(); create(master.toCharArray()) }
        val key = s.dataKeyCopy()!!
        val file = VaultCrypto.decodeFile(File(dir, "vault.bin").readText())
        File(dir, "vault.bin").writeText(VaultCrypto.encodeFile(VaultCrypto.write(file, key, VaultCrypto.read(file, key).copy(entries = entries.toList()))))
        store(dir).apply { load(); unlock(master.toCharArray()) }
    }

    private fun VaultStore.entry(id: String) = (state.value as VaultState.Unlocked).data.entries.single { it.id == id }

    private fun login(id: String = "e", password: String = "clave-antigua", rotateMonths: Int? = 6) =
        VaultEntry(id, EntryType.LOGIN, "Banco", password = password, createdAt = old, updatedAt = old, rotateMonths = rotateMonths)

    @Test fun editsThatKeepThePasswordKeepItsDate() = runBlocking {
        val s = withEntries(login())
        assertEquals(old, lastPasswordChange(s.entry("e")))
        val dueBefore = Expiry.items(s.entry("e")).map { it.date }
        clock += 1_000
        s.setFavorite(setOf("e"), true, sinceLock = s.lockEpoch)
        clock += 1_000
        s.addTag(setOf("e"), "banco", sinceLock = s.lockEpoch)
        clock += 1_000
        assertTrue(s.addAttachment("e", ByteArrayInputStream("hola".toByteArray()), "hola.txt"))
        val att = s.entry("e").attachments.single().id
        clock += 1_000
        assertTrue(s.removeAttachment("e", att))
        val e = s.entry("e")
        assertTrue(e.favorite && "banco" in e.tags && e.updatedAt > old)
        assertEquals("the date of the last change of its password", old, lastPasswordChange(e))
        assertTrue("«Salud» still says it is old", Issue.OLD in vaultHealth(listOf(e), clock).single().issues)
        assertEquals("the reminder keeps its date", dueBefore, Expiry.items(e).map { it.date })
    }

    /** An entry of 1.4.3 with its legacy list and a later edit of its notes: «Borrar historial» keeps the list's date. */
    @Test fun clearingTheHistoryKeepsTheDateOfTheList() = runBlocking {
        val changed = old + 1_000
        val s = withEntries(login().copy(passwordHistory = listOf(PasswordChange("aún-más-antigua", changed))))
        clock += 1_000
        s.upsert(s.entry("e").copy(notes = "nota nueva"))
        assertEquals(changed, lastPasswordChange(s.entry("e")))
        assertTrue(s.clearHistory("e"))
        val e = s.entry("e")
        assertTrue(e.history.isEmpty() && e.passwordHistory.isEmpty())
        assertEquals(changed, lastPasswordChange(e))
    }

    @Test fun aSaveWithoutChangesAddsNoVersion() = runBlocking {
        val s = withEntries(login())
        clock += 1_000
        s.upsert(s.entry("e"))
        assertEquals(emptyList<EntryVersion>(), s.entry("e").history)
        // And the mark alone never makes a version.
        val e = login()
        assertTrue(!e.copy(passwordChangedAt = old).changedFrom(e))
    }

    @Test fun anEntryWithoutPasswordStaysWithoutAMarkAndANewPasswordIsNow() = runBlocking {
        val s = withEntries(login("n", password = "", rotateMonths = null), login("p"))
        clock += 1_000
        s.setFavorite(setOf("n"), true, sinceLock = s.lockEpoch)
        assertNull(s.entry("n").passwordChangedAt)
        clock += 1_000
        s.upsert(s.entry("p").copy(password = "clave-nueva"))
        assertEquals(clock, s.entry("p").passwordChangedAt)
        assertEquals(clock, lastPasswordChange(s.entry("p")))
    }

    @Test fun duplicateKeepsTheDateAsBefore() = runBlocking {
        val s = withEntries(login())
        clock += 1_000
        val copy = s.duplicate("e")!!
        assertEquals(old, copy.passwordChangedAt)
    }

    /** Real data of before: vault-1.4.3.json opened, brought to v4, and its login marked «Favorita»: the same date. */
    @Test fun aRealVaultOf143KeepsItsDate() = runBlocking {
        val dir = tmp.newFolder()
        File(dir, "vault.bin").writeText(javaClass.getResourceAsStream("/seguridad/vault-1.4.3.json")!!.use { it.readBytes() }.decodeToString())
        clock = 1_800_000_000_000L + 3_600_000L
        val s = VaultStore(
            File(dir, "vault.bin"), Dispatchers.Unconfined, Dispatchers.Unconfined, now = { clock }, kdf = { KdfParams(1024, 1, 1) },
            beforeUpgrade = { app.seguridad.vault.CopyResult.Saved("seguridad-antes-de-2-0-20261120-100000.sgbak") },
        )
        s.load()
        s.unlock("maestra-de-prueba-143".toCharArray())
        assertEquals(4, VaultCrypto.decodeFile(File(dir, "vault.bin").readText()).version)
        val e1 = s.entry("e1")
        val date = lastPasswordChange(e1)
        assertEquals(1_799_999_998_000L, date)
        clock += 1_000
        s.setFavorite(setOf("e1"), false, sinceLock = s.lockEpoch)
        assertEquals(date, lastPasswordChange(s.entry("e1")))
        // The other entries, untouched.
        assertEquals(listOf("e2", "e3", "e4").map { s.entry(it).passwordChangedAt }, listOf(null, null, null))
    }

    /** A login of before 2.0 (no mark) whose JSON without history is [VaultLimits.ENTRY_MAX_JSON_BYTES] - 10 bytes. */
    private fun entryJustUnderTheCap(favorite: Boolean = false): VaultEntry {
        val base = login().copy(favorite = favorite, notes = "x")
        val n = 1 + (VaultLimits.ENTRY_MAX_JSON_BYTES - 10 - jsonBytes(VaultEntry.serializer(), base)).toInt()
        return base.copy(notes = "x".repeat(n)).also { assertEquals(VaultLimits.ENTRY_MAX_JSON_BYTES - 10L, jsonBytes(VaultEntry.serializer(), it)) }
    }

    /**
     * Audit E-1: the store's own mark ([VaultEntry.passwordChangedAt], frozen by an edit that keeps the password) does
     * not count in the cap of one entry: an entry of 1.4.3 just under the cap still takes an edit of the same size.
     */
    @Test fun anEntryOf143JustUnderTheEntryCapStillTakesAnEditThatKeepsItsPassword() = runBlocking {
        val e = entryJustUnderTheCap()
        val s = withEntries(e)
        clock += 1_000
        s.upsert(s.entry("e").copy(notes = e.notes.dropLast(1) + "y", updatedAt = clock))
        assertTrue(s.entry("e").notes.endsWith("y"))
        assertEquals(old, s.entry("e").passwordChangedAt)
        assertEquals(old, lastPasswordChange(s.entry("e")))
    }

    /** Audit E-1, with «Favorita»: taking the mark off a favourite just under the cap is saved too. */
    @Test fun anEntryOf143JustUnderTheEntryCapCanStopBeingAFavourite() = runBlocking {
        val s = withEntries(entryJustUnderTheCap(favorite = true))
        clock += 1_000
        assertEquals(1, s.setFavorite(setOf("e"), false, sinceLock = s.lockEpoch))
        assertTrue(!s.entry("e").favorite)
        assertEquals(old, lastPasswordChange(s.entry("e")))
    }

    /**
     * Audit E-1: the app never refuses its own copies. A vault with an entry just under the cap and its mark (what the
     * edits above leave) is exported, and «Comprobar una copia» and «Importar copia» accept it.
     */
    @Test fun aCopyWithAnEntryAtTheCapAndItsMarkIsAccepted() = runBlocking {
        val marked = entryJustUnderTheCap().copy(passwordChangedAt = old)
        assertTrue(jsonBytes(VaultEntry.serializer(), marked) > VaultLimits.ENTRY_MAX_JSON_BYTES)
        val maker = withEntries(marked)
        val backup = java.io.ByteArrayOutputStream().also { assertTrue(maker.exportBackup(it)) }.toByteArray()
        val report = checkBackup(ByteArrayInputStream(backup), master.toCharArray(), tmp.newFolder(), Dispatchers.Unconfined, Dispatchers.Unconfined)
        assertEquals(emptyList<String>(), report.problems)
        val mine = store(tmp.newFolder()).apply { load(); create(master.toCharArray()) }
        mine.importReplace(ByteArrayInputStream(backup), master.toCharArray())
        assertEquals(old, mine.entry("e").passwordChangedAt)
        assertEquals(marked.notes, mine.entry("e").notes)
    }
}
