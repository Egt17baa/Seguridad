package app.seguridad.vault

import org.junit.Assert.assertEquals
import org.junit.Test

/** The editor keeps one password per type: switching type never carries a login password into a seed passphrase. */
class EntryFormTest {
    private fun entry(type: EntryType, password: String) =
        VaultEntry(id = "x", type = type, title = "t", password = password, createdAt = 1, updatedAt = 1)

    @Test
    fun aNewEntryStartsWithBothFieldsEmpty() {
        assertEquals("", initialPassword(null, EntryType.LOGIN))
        assertEquals("", initialPassword(null, EntryType.SEED))
    }

    @Test
    fun switchingFromLoginToSeedDoesNotCarryThePassword() {
        // The user generates a password in «Acceso», then picks «Frase semilla» before saving.
        val loginPassword = "Xk9#generada-en-acceso"
        val seedPassphrase = ""
        assertEquals("", passwordToSave(EntryType.SEED, loginPassword, seedPassphrase))
        assertEquals(loginPassword, passwordToSave(EntryType.LOGIN, loginPassword, seedPassphrase))
    }

    @Test
    fun switchingFromSeedToLoginDoesNotCarryThePassphrase() {
        assertEquals("", passwordToSave(EntryType.LOGIN, "", "mi passphrase"))
        assertEquals("mi passphrase", passwordToSave(EntryType.SEED, "", "mi passphrase"))
    }

    @Test
    fun editingAnExistingSeedKeepsItsPassphrase() {
        val seed = entry(EntryType.SEED, "passphrase-bip39")
        val login = initialPassword(seed, EntryType.LOGIN)
        val passphrase = initialPassword(seed, EntryType.SEED)
        assertEquals("", login)
        assertEquals("passphrase-bip39", passphrase)
        assertEquals("passphrase-bip39", passwordToSave(EntryType.SEED, login, passphrase))
    }

    @Test
    fun editingAnExistingLoginKeepsItsPassword() {
        val existing = entry(EntryType.LOGIN, "secreta")
        assertEquals("secreta", initialPassword(existing, EntryType.LOGIN))
        assertEquals("", initialPassword(existing, EntryType.SEED))
    }

    @Test
    fun noteAndTotpSaveNoPassword() {
        assertEquals("", passwordToSave(EntryType.NOTE, "a", "b"))
        assertEquals("", passwordToSave(EntryType.TOTP, "a", "b"))
        // An old entry of another type never seeds either field.
        assertEquals("", initialPassword(entry(EntryType.NOTE, "resto"), EntryType.LOGIN))
        assertEquals("", initialPassword(entry(EntryType.TOTP, "resto"), EntryType.SEED))
    }
}
