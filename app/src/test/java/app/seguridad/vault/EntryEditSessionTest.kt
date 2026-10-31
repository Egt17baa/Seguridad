package app.seguridad.vault

import app.seguridad.crypto.TotpSecret
import app.seguridad.tools.codes.PlainCells
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M6 and concern 3 of the CR review: the entry editor's state lives in its ViewModel (this session), so a rotation keeps
 * what was typed, and it goes on a lock, on save, on back, on leaving and when the ViewModel is cleared. In memory only.
 */
class EntryEditSessionTest {
    private val saved = VaultEntry(
        id = "e1", type = EntryType.LOGIN, title = "Banco", username = "ana", password = "clave-guardada",
        url = "https://banco.example", notes = "nota", createdAt = 1, updatedAt = 1, favorite = true, tags = listOf("a", "b"),
    )

    private fun session(id: String? = null, type: EntryType = EntryType.LOGIN) = EntryEditSession(PlainCells, id, type, newId = "nuevo-1")

    /** Types something secret in every field. */
    private fun EntryEditSession.typeEverything() {
        title.value = "Wallet"
        username.value = "yo"
        loginPassword.value = "contraseña-escrita"
        seedPassphrase.value = "passphrase-escrita"
        url.value = "https://x.example"
        notes.value = "nota secreta"
        seed.value = "abandon ability able"
        totp.value = TotpSecret("JBSWY3DPEHPK3PXP", issuer = "X", account = "y")
        favorite.value = true
        tagsText.value = "t1, t2"
        error.value = "un error"
        info.value = "un aviso"
    }

    private fun EntryEditSession.assertEmpty() {
        assertEquals("", title.value)
        assertEquals("", username.value)
        assertEquals("", loginPassword.value)
        assertEquals("", seedPassphrase.value)
        assertEquals("", url.value)
        assertEquals("", notes.value)
        assertEquals("", seed.value)
        assertEquals(TotpSecret(""), totp.value)
        assertFalse(favorite.value)
        assertEquals("", tagsText.value)
        assertNull(error.value)
        assertNull(info.value)
        assertNull(migration.value)
        assertNull(existing.value)
        assertFalse(saving.value)
    }

    @Test fun anExistingEntryIsLoadedOnceAndWhatIsTypedSurvives() {
        val s = session(id = "e1")
        s.vaultStateIs(unlocked = true, listOf(saved))
        assertEquals(saved, s.existing.value)
        assertEquals("Banco", s.title.value)
        assertEquals("clave-guardada", s.loginPassword.value)
        assertEquals("", s.seedPassphrase.value) // One field per type.
        assertEquals("a, b", s.tagsText.value)
        assertTrue(s.favorite.value)
        s.title.value = "Banco nuevo"
        s.loginPassword.value = "otra"
        // A rotation composes the screen again over the same session: loading again changes nothing.
        s.vaultStateIs(unlocked = true, listOf(saved))
        s.load(listOf(saved))
        assertEquals("Banco nuevo", s.title.value)
        assertEquals("otra", s.loginPassword.value)
    }

    @Test fun aNewEntryKeepsOneIdForTheWholeSession() {
        val s = session()
        s.load(emptyList())
        assertEquals("nuevo-1", s.entryId)
        s.typeEverything()
        assertEquals("nuevo-1", s.entryId) // However many times «Guardar» is tapped, or the screen rotated.
        assertEquals("e1", session(id = "e1").apply { load(listOf(saved)) }.entryId)
    }

    @Test fun aLockClearsEverythingAndTheNextUnlockStartsFromTheVault() {
        val s = session(id = "e1")
        s.load(listOf(saved))
        s.typeEverything()
        s.migration.value = PendingMigration(app.seguridad.crypto.MigrationResult(listOf(TotpSecret("JBSWY3DPEHPK3PXP")), emptyList(), 0, 1), emptyList(), emptyList())
        s.saving.value = true
        s.vaultStateIs(unlocked = false, emptyList())
        s.assertEmpty()
        assertFalse(s.loaded)
        // Opened again (the screen still there): it starts from the saved entry, never from what was typed.
        s.vaultStateIs(unlocked = true, listOf(saved))
        assertEquals("Banco", s.title.value)
        assertEquals("clave-guardada", s.loginPassword.value)
    }

    @Test fun aLockBeforeAnythingWasLoadedDoesNothing() {
        val s = session()
        s.vaultStateIs(unlocked = false, emptyList())
        assertFalse(s.loaded)
        assertFalse(s.finished.value)
    }

    @Test fun saveOrBackFinishesAndClears() {
        val s = session()
        s.load(emptyList())
        s.typeEverything()
        s.finish()
        s.assertEmpty()
        assertTrue(s.finished.value)
        // Finished for good: a later unlock does not load it again.
        s.vaultStateIs(unlocked = true, listOf(saved))
        assertTrue(s.finished.value)
        assertEquals("", s.title.value)
    }

    @Test fun leavingOrBeingClearedLetsItGoAndComingBackReloads() {
        val s = session(id = "e1")
        s.load(listOf(saved))
        s.typeEverything()
        s.clear() // Another tab, or the ViewModel cleared.
        s.assertEmpty()
        s.load(listOf(saved)) // Back on the tab: from the vault again.
        assertEquals("Banco", s.title.value)
    }

    @Test fun theQrTextIsHandedOverOnce() {
        val s = session(type = EntryType.TOTP)
        s.load(emptyList())
        assertEquals("otpauth://totp/x?secret=JBSWY3DPEHPK3PXP", s.takeHandOver("otpauth://totp/x?secret=JBSWY3DPEHPK3PXP"))
        // A rotation composes the form again: the text is not applied a second time (a batch is never offered twice).
        assertNull(s.takeHandOver("otpauth://totp/x?secret=JBSWY3DPEHPK3PXP"))
        assertNull(session().takeHandOver(null))
        // Not even after a lock and a new unlock.
        s.vaultStateIs(unlocked = false, emptyList())
        s.vaultStateIs(unlocked = true, emptyList())
        assertNull(s.takeHandOver("otpauth://totp/x?secret=JBSWY3DPEHPK3PXP"))
    }
}
