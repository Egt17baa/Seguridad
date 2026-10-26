package app.seguridad.vault

import app.seguridad.crypto.TotpSecret
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TotpImportTest {
    private val a = TotpSecret("JBSWY3DPEHPK3PXP", issuer = "GitHub", account = "ana")
    private val b = TotpSecret("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", issuer = "", account = "luis@ejemplo.es")
    private val c = TotpSecret("MZXW6YTBOIMZXW6YTBOI", issuer = "", account = "")

    @Test fun entriesForImportedCodes() {
        var n = 0
        val (entries, dupes) = totpImportEntries(listOf(a, b, c), existing = emptyList(), now = 9) { "id${n++}" }
        assertEquals(listOf("GitHub", "luis@ejemplo.es", "Código 2FA"), entries.map { it.title })
        assertEquals(listOf("id0", "id1", "id2"), entries.map { it.id })
        assertTrue(entries.all { it.type == EntryType.TOTP && it.createdAt == 9L && it.updatedAt == 9L })
        assertEquals(listOf(a, b, c), entries.map { it.totp })
        assertTrue(dupes.isEmpty())
    }

    // Scanning the same batch twice (or a code already saved by hand) does not duplicate it.
    @Test fun codesAlreadyInTheVaultAreLeftOut() {
        val saved = VaultEntry("x", EntryType.TOTP, "Mío", totp = a.copy(secret = "jbsw y3dp-ehpk3pxp", issuer = "Otro"), createdAt = 0, updatedAt = 0)
        val (entries, dupes) = totpImportEntries(listOf(a, b, b), existing = listOf(saved), now = 1) { "n" }
        assertEquals(listOf(b), entries.map { it.totp })
        assertEquals(2, dupes.size)
        assertTrue(dupes[0], dupes[0].contains("GitHub"))
        assertTrue(dupes.none { it.contains(a.secret) })
    }
}
