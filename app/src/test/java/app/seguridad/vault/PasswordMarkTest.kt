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
        s.setFavorite(setOf("e"), true)
        clock += 1_000
        s.addTag(setOf("e"), "banco")
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
        s.setFavorite(setOf("n"), true)
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
        s.setFavorite(setOf("e1"), false)
        assertEquals(date, lastPasswordChange(s.entry("e1")))
        // The other entries, untouched.
        assertEquals(listOf("e2", "e3", "e4").map { s.entry(it).passwordChangedAt }, listOf(null, null, null))
    }
}
