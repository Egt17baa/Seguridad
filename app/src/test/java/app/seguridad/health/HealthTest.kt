package app.seguridad.health

import app.seguridad.crypto.TotpSecret
import app.seguridad.vault.EntryType
import app.seguridad.vault.PasswordChange
import app.seguridad.vault.VaultEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthTest {
    private val day = 86_400_000L
    private val now = 1_000 * day
    private val strong = "k8#Vq2!zRw9@Lm4pXt7&"

    private fun login(
        id: String, pw: String = strong, url: String = "", title: String = id, updatedAt: Long = now,
        history: List<PasswordChange> = emptyList(),
    ) = VaultEntry(id, EntryType.LOGIN, title, password = pw, url = url, createdAt = 0, updatedAt = updatedAt, passwordHistory = history)

    private fun totp(id: String, issuer: String, title: String = issuer) =
        VaultEntry(id, EntryType.TOTP, title, totp = TotpSecret(secret = "JBSWY3DPEHPK3PXP", issuer = issuer), createdAt = 0, updatedAt = 0)

    private fun issues(list: List<HealthItem>, id: String) = list.firstOrNull { it.entryId == id }?.issues.orEmpty()

    @Test fun reusedDetected() {
        val r = vaultHealth(listOf(login("a", "k8#Vq2!zRw9@Lm4pXt7&"), login("b", "k8#Vq2!zRw9@Lm4pXt7&"), login("c", "x9$" + "Tj5^Qe1*Zu8(Bn3)")), now)
        assertTrue(Issue.REUSED in issues(r, "a"))
        assertTrue(Issue.REUSED in issues(r, "b"))
        assertFalse(Issue.REUSED in issues(r, "c"))
    }

    @Test fun weakVsStrong() {
        assertTrue(estimateBits(strong) > estimateBits("Tr0ub4dor&3"))
        assertTrue(estimateBits(strong) >= 50)
        assertTrue(estimateBits("abcdefgh1") < 50)
        assertTrue(Issue.WEAK in issues(vaultHealth(listOf(login("a", "hola1")), now), "a"))
        assertFalse(Issue.WEAK in issues(vaultHealth(listOf(login("a", strong)), now), "a"))
    }

    @Test fun sequencesPenalized() {
        assertEquals(estimateBits("Kx9!a"), estimateBits("Kx9!aaaaaaaa"), 1e-9)
        assertTrue(estimateBits("Kx9!abcdefgh") < estimateBits("Kx9!tqzmwfhp"))
        assertTrue(estimateBits("Kx9!87654321") < estimateBits("Kx9!48215937"))
        assertTrue(estimateBits("Kx9!qwertyui") < estimateBits("Kx9!tqzmwfhp"))
        assertTrue(estimateBits("Kx9!aaaaaaaa") < estimateBits("Kx9!tqzmwfhp"))
    }

    @Test fun commonListZero() {
        for (p in listOf("123456", "Password", "QWERTY", "contraseña", "111111", "admin", "iloveyou", "000000", "abc123", "12345678")) {
            assertEquals(p, 0.0, estimateBits(p), 0.0)
        }
        assertTrue(estimateBits("password-Zq7!Lm2") > 0.0)
    }

    @Test fun oldUsesHistoryDate() {
        val old = now - 400 * day
        val recentChange = listOf(PasswordChange("prev", now - 10 * day))
        val r = vaultHealth(
            listOf(
                login("noHist", updatedAt = old),
                login("hist", updatedAt = old, history = recentChange),
                login("histOld", updatedAt = now, history = listOf(PasswordChange("prev", old))),
                login("edge", updatedAt = now - 365 * day),
                login("over", updatedAt = now - 365 * day - 1),
            ),
            now,
        )
        assertTrue(Issue.OLD in issues(r, "noHist"))
        assertFalse(Issue.OLD in issues(r, "hist"))
        assertTrue(Issue.OLD in issues(r, "histOld"))
        assertFalse(Issue.OLD in issues(r, "edge"))
        assertTrue(Issue.OLD in issues(r, "over"))
    }

    @Test fun no2faMatching() {
        val entries = listOf(
            login("g", url = "https://accounts.google.com", title = "Google"),
            login("gh", url = "github.com/login", title = "Mi cuenta"),
            login("t", title = "Twitch"),
            login("x", url = "https://www.example.org", title = "Otra"),
            totp("t1", "Google"),
            totp("t2", "GitHub.com", title = "Código"),
            totp("t3", "Twitch"),
        )
        val r = vaultHealth(entries, now)
        assertFalse(Issue.NO_2FA in issues(r, "g"))
        assertFalse(Issue.NO_2FA in issues(r, "gh"))
        assertFalse(Issue.NO_2FA in issues(r, "t"))
        assertTrue(Issue.NO_2FA in issues(r, "x"))
    }

    @Test fun breachedFromMap() {
        val r = vaultHealth(listOf(login("a"), login("b", "z8#Vq2!zRw9@Lm4pXt7&")), now, breached = mapOf("a" to 12, "b" to 0))
        assertTrue(Issue.BREACHED in issues(r, "a"))
        assertFalse(Issue.BREACHED in issues(r, "b"))
        assertFalse(Issue.BREACHED in issues(vaultHealth(listOf(login("a")), now), "a"))
    }

    @Test fun onlyLoginsConsidered() {
        val note = VaultEntry("n", EntryType.NOTE, "Nota", password = "123456", createdAt = 0, updatedAt = 0)
        val seed = VaultEntry("s", EntryType.SEED, "Semilla", password = "123456", createdAt = 0, updatedAt = 0)
        val empty = login("e", pw = "")
        val r = vaultHealth(listOf(note, seed, empty, totp("t", "X"), login("l1", "123456"), login("l2", "123456")), now, breached = mapOf("n" to 5))
        assertEquals(listOf("l1", "l2"), r.map { it.entryId }.sorted())
        assertTrue(r.all { it.issues.isNotEmpty() })
    }

    @Test fun uniquePasswordsGroupsIdsAndSkipsEmpty() {
        val m = uniquePasswords(listOf(login("a", "x"), login("b", "x"), login("c", "y"), login("d", ""), totp("t", "T")))
        assertEquals(mapOf("x" to listOf("a", "b"), "y" to listOf("c")), m)
    }
}
