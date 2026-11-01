package app.seguridad.health

import app.seguridad.crypto.TotpSecret
import app.seguridad.vault.EntryType
import app.seguridad.vault.PasswordChange
import app.seguridad.vault.MASTER_TOO_SHORT
import app.seguridad.vault.MASTER_TOO_WEAK
import app.seguridad.vault.MIN_MASTER
import app.seguridad.vault.VaultEntry
import app.seguridad.vault.newMasterProblem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.Normalizer
import java.util.Locale
import java.util.Random
import kotlin.math.ln

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

    // ---- Final review 1.3.1, F3 (option A): the estimate also by code points, never stronger than before ----

    /** [estimateBits] as it was before F3, over UTF-16 units only: a copy, the reference of the property tests. */
    private val oldCommon = setOf(
        "123456", "password", "qwerty", "contraseña", "111111", "admin", "iloveyou", "000000", "abc123", "12345678",
    )
    private val oldRows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm", "1234567890")

    private fun oldEstimateBits(password: String): Double {
        if (password.isEmpty() || password.lowercase(Locale.ROOT) in oldCommon) return 0.0
        var pool = 0
        if (password.any { it in 'a'..'z' }) pool += 26
        if (password.any { it in 'A'..'Z' }) pool += 26
        if (password.any { it in '0'..'9' }) pool += 10
        if (password.any { it.code < 128 && !it.isLetterOrDigit() }) pool += 33
        if (password.any { it.code >= 128 }) pool += 100
        return oldEffectiveLength(password) * ln(pool.toDouble()) / ln(2.0)
    }

    private fun oldStep(a: Char, b: Char): Int {
        if (a == b) return 0
        val x = a.lowercaseChar()
        val y = b.lowercaseChar()
        if ((x.isLetterOrDigit() && y.isLetterOrDigit()) && x.code - y.code in -1..1) return y.code - x.code
        for (row in oldRows) {
            val i = row.indexOf(x)
            val j = row.indexOf(y)
            if (i >= 0 && j >= 0 && i - j in -1..1) return j - i
        }
        return Int.MIN_VALUE
    }

    private fun oldEffectiveLength(p: String): Int {
        var count = 0
        var i = 0
        while (i < p.length) {
            var end = i
            if (i + 1 < p.length) {
                val d = oldStep(p[i], p[i + 1])
                if (d != Int.MIN_VALUE) {
                    end = i + 1
                    while (end + 1 < p.length && oldStep(p[end], p[end + 1]) == d) end++
                }
            }
            i = if (end - i + 1 >= 3) end + 1 else i + 1
            count++
        }
        return count
    }

    /** The rule of a new master before F3, without the word lists: UTF-16 length, then the old estimate. */
    private fun oldMasterProblem(c: String) = when {
        c.length < MIN_MASTER -> MASTER_TOO_SHORT
        oldEstimateBits(c) < WEAK_BITS -> MASTER_TOO_WEAK
        else -> null
    }

    private val ascii = (0x20..0x7E).toList()
    private val spanish = "áéíóúüñÁÉÍÓÚÜÑ¿¡€ºª·çÇ".map { it.code }
    private val spanishNfd = Normalizer.normalize("áéíóúüñÁÉÍÓÚÜÑ", Normalizer.Form.NFD).map { it.code }
    private val formatChars = listOf(
        0x00AD, 0x061C, 0x180E, 0x200B, 0x200C, 0x200D, 0x200E, 0x200F, 0x202A, 0x202E, 0x2060, 0x2066, 0xFEFF, 0xE0001,
        0xE0041,
    )
    private val emojis = listOf(0x1F600, 0x1F601, 0x1F44D, 0x1F512, 0x1F680, 0x1F9D1, 0x2764, 0x1F1EA, 0x1F1F8)
    // NFC splits these into two code points (composition exclusions)…
    private val exclusions = listOf(
        0x0344, 0x0958, 0x095C, 0x09DC, 0x0A33, 0x0A59, 0x0B5C, 0x0F43, 0x0F73, 0x0F76, 0x2ADC, 0xFB1D, 0xFB2F, 0xFB4E,
        0x1D15E,
    )

    // …and turns these into another character, some of them ASCII (singletons).
    private val singletons = listOf(
        0x0340, 0x0341, 0x0343, 0x0374, 0x037E, 0x0387, 0x1FEF, 0x2000, 0x2126, 0x212A, 0x212B, 0x2329, 0xF900, 0x2F800,
    )

    /** A code point that is assigned and not a surrogate nor private use, anywhere below U+30000 or in the BMP only. */
    private fun anyAssigned(r: Random, bmpOnly: Boolean): Int {
        while (true) {
            val cp = r.nextInt(if (bmpOnly) 0x10000 else 0x30000)
            val type = Character.getType(cp)
            val skipped = listOf(Character.UNASSIGNED, Character.SURROGATE, Character.PRIVATE_USE).map { it.toInt() }
            if (type !in skipped) return cp
        }
    }

    private fun text(r: Random, pick: (Random) -> Int): String {
        val b = StringBuilder()
        repeat(1 + r.nextInt(24)) { b.appendCodePoint(pick(r)) }
        return b.toString()
    }

    private fun isNormalPlain(s: String) = Normalizer.isNormalized(s, Normalizer.Form.NFC) &&
        s.none { Character.isSurrogate(it) || Character.getType(it) == Character.FORMAT.toInt() }

    /** (1) of F3: for text in NFC, in the basic plane and without format characters, exactly the old number. */
    @Test fun `F3 the estimate is exactly the old one for NFC text of the basic plane without format characters`() {
        val r = Random(20261101L)
        var cases = 0
        while (cases < 200_000) {
            val s = text(r) { x ->
                when (x.nextInt(10)) {
                    in 0..5 -> ascii[x.nextInt(ascii.size)]
                    in 6..7 -> spanish[x.nextInt(spanish.size)]
                    else -> anyAssigned(x, bmpOnly = true)
                }
            }
            if (!isNormalPlain(s)) continue
            assertEquals(s, oldEstimateBits(s), estimateBits(s), 0.0)
            cases++
        }
    }

    /** (2) of F3: for any text, never stronger than the old number, and the rule of a new master never weaker. */
    @Test fun `F3 the estimate is never stronger and the new master rule never weaker, for any text`() {
        val r = Random(20261102L)
        var lower = 0
        repeat(200_000) {
            val s = text(r) { x ->
                when (x.nextInt(16)) {
                    in 0..4 -> ascii[x.nextInt(ascii.size)]
                    5 -> spanish[x.nextInt(spanish.size)]
                    6, 7 -> spanishNfd[x.nextInt(spanishNfd.size)]
                    8 -> formatChars[x.nextInt(formatChars.size)]
                    9 -> emojis[x.nextInt(emojis.size)]
                    10 -> exclusions[x.nextInt(exclusions.size)]
                    11 -> singletons[x.nextInt(singletons.size)]
                    12 -> 0x0300 + x.nextInt(0x70) // A combining mark on whatever is before it.
                    13 -> (if (x.nextBoolean()) 0xD800 else 0xDC00) + x.nextInt(0x400) // A lone surrogate.
                    else -> anyAssigned(x, bmpOnly = false)
                }
            }
            val old = oldEstimateBits(s)
            val new = estimateBits(s)
            assertTrue("$s: $new > $old", new <= old)
            if (new < old) lower++
            if (oldMasterProblem(s) != null) assertTrue(s, newMasterProblem(s) != null)
        }
        assertTrue("the generator reaches the texts that change ($lower)", lower > 10_000)
    }

    @Test fun `F3 the second measure is the NFC form without format characters, by code points`() {
        assertEquals("\u00C1b", strengthForm("A\u0301\u200Bb"))
        assertEquals("😀😀", strengthForm("😀\u200D😀"))
        assertEquals("K", strengthForm("\u212A")) // A singleton: the reason the old measure is kept as well.
        assertEquals("\u0921\u093C", strengthForm("\u095C")) // A composition exclusion: one unit, two code points.
    }

    /** N-02: what passed as strong before F3 because the estimate counted UTF-16 units. */
    @Test fun `F3 equal emojis, letters typed decomposed and invisible characters between digits are weak`() {
        val nfd = { t: String -> Normalizer.normalize(t, Normalizer.Form.NFD) }
        val strongBefore = listOf("😀".repeat(5), nfd("ąąąąą"), "1\u200B2\u200B3\u200B4\u200B5\u200B", "😀".repeat(10))
        for (s in strongBefore + nfd("ą".repeat(10))) {
            assertTrue("$s: ${oldEstimateBits(s)} bits before", oldEstimateBits(s) >= WEAK_BITS)
            assertTrue("$s: ${estimateBits(s)} bits", estimateBits(s) < WEAK_BITS)
        }
        // A composition exclusion, five or ten times: weak before and still weak (the old measure is kept).
        for (n in listOf(5, 10)) assertTrue(estimateBits("\u095C".repeat(n)) < WEAK_BITS)
        // Normal text does not move.
        assertEquals(oldEstimateBits(strong), estimateBits(strong), 0.0)
        assertEquals(oldEstimateBits("contraseña123"), estimateBits("contraseña123"), 0.0)
    }

    @Test fun `F3 the copy of the old function uses the same common list`() {
        assertEquals(commonPasswords(), oldCommon)
    }
}
