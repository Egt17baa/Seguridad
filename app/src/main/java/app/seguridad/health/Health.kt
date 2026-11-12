package app.seguridad.health

import app.seguridad.vault.EntryType
import app.seguridad.vault.VaultEntry
import app.seguridad.vault.oldPasswords
import app.seguridad.vault.passwordForHealth
import java.net.URI
import java.text.Normalizer
import java.util.Locale
import kotlin.math.ln

enum class Issue { REUSED, WEAK, OLD, NO_2FA, BREACHED }

data class HealthItem(val entryId: String, val issues: Set<Issue>)

const val WEAK_BITS = 50.0
const val OLD_DAYS = 365L
internal const val DAY_MS = 86_400_000L

private val COMMON = setOf(
    "123456", "password", "qwerty", "contraseña", "111111", "admin", "iloveyou", "000000", "abc123", "12345678",
)
private val KEYBOARD_ROWS = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm", "1234567890")

/** The most used passwords that [estimateBits] gives 0 bits (as typed in the list, before any normalization). */
internal fun commonPasswords(): Set<String> = COMMON

/**
 * LOGIN entries with a non-empty password, grouped by password: password -> entry ids (in vault order). 2.0, task 3:
 * not those in the trash.
 */
fun uniquePasswords(entries: List<VaultEntry>): Map<String, List<String>> =
    entries.filter { it.type == EntryType.LOGIN && it.password.isNotEmpty() && it.deletedAt == null }
        .groupBy({ it.password }, { it.id })

/**
 * Problems of each LOGIN that has a password, and (2.0, task 5) of each wifi's password: reused or weak, never old,
 * without 2FA or leaked (the check of leaks reads only the logins, [uniquePasswords]); entries with none are left out. [breached] maps entry id to the
 * number of times its password appeared in leaks (filled in memory by the user-triggered check; empty otherwise).
 */
fun vaultHealth(entries: List<VaultEntry>, now: Long, breached: Map<String, Int> = emptyMap()): List<HealthItem> {
    // 2.0, task 3: what is in the trash is not in «Salud», nor a 2FA that counts for another entry.
    val live = entries.filter { it.deletedAt == null }
    val logins = live.filter { it.type == EntryType.LOGIN && it.password.isNotEmpty() }
    // 2.0, task 5: a wifi's password counts as a password too (reused with a login or another wifi, or weak); the other
    // secrets of the templates (a card's number, a CVV, a licence's key) are no passwords one reuses.
    val wifis = live.filter { it.type == EntryType.WIFI && it.passwordForHealth().isNotEmpty() }
    val counts = (logins + wifis).groupingBy { it.passwordForHealth() }.eachCount()
    val totpNames = totpServiceNames(live)
    val wifiItems = wifis.mapNotNull { e ->
        val p = e.passwordForHealth()
        val issues = buildSet {
            if ((counts[p] ?: 0) >= 2) add(Issue.REUSED)
            if (estimateBits(p) < WEAK_BITS) add(Issue.WEAK)
        }
        if (issues.isEmpty()) null else HealthItem(e.id, issues)
    }
    return logins.mapNotNull { e ->
        val issues = buildSet {
            if ((counts[e.password] ?: 0) >= 2) add(Issue.REUSED)
            if (estimateBits(e.password) < WEAK_BITS) add(Issue.WEAK)
            // The last change of the password: from the history and the list of 1.4.3 (task 3, M-4).
            val changed = e.oldPasswords().firstOrNull()?.changedAt ?: e.updatedAt
            if (now - changed > OLD_DAYS * DAY_MS) add(Issue.OLD)
            if (!has2fa(e, totpNames)) add(Issue.NO_2FA)
            if ((breached[e.id] ?: 0) > 0) add(Issue.BREACHED)
        }
        if (issues.isEmpty()) null else HealthItem(e.id, issues)
    } + wifiItems
}

/** Lowercase, without `www.` and without the TLD (`Google.com` -> `google`). Names that are not domains stay as they are. */
internal fun normalizeName(s: String): String {
    var t = s.trim().lowercase(Locale.ROOT).removePrefix("www.")
    if (' ' !in t && Regex("^[a-z0-9-]+(\\.[a-z0-9-]+)+$").matches(t)) t = t.substringBeforeLast('.')
    return t
}

private fun hostOf(url: String): String {
    val u = url.trim()
    if (u.isEmpty()) return ""
    return try {
        URI(if ("://" in u) u else "https://$u").host.orEmpty()
    } catch (e: Exception) {
        ""
    }
}

/**
 * 2.0, task 4: the ids of the logins (out of the trash) with no 2FA, as «Salud» reads it ([Issue.NO_2FA]): no code of
 * their own and none in the vault for their service. The list's filter «Sin 2FA» uses it, with or without a password.
 */
fun loginsWithout2fa(entries: List<VaultEntry>): Set<String> {
    val live = entries.filter { it.deletedAt == null }
    val names = totpServiceNames(live)
    return live.filter { it.type == EntryType.LOGIN && !has2fa(it, names) }.mapTo(HashSet()) { it.id }
}

private fun totpServiceNames(live: List<VaultEntry>): Set<String> =
    live.filter { it.type == EntryType.TOTP }
        .flatMap { listOfNotNull(it.totp?.issuer, it.title) }
        .map { normalizeName(it) }
        .filter { it.isNotEmpty() }
        .toSet()

private fun has2fa(e: VaultEntry, totpNames: Set<String>): Boolean {
    // 2.0, task 4: a login with a code of its own (R67 of 1.4.0) has 2FA.
    if (e.totp != null) return true
    if (totpNames.isEmpty()) return false
    val host = normalizeName(hostOf(e.url))
    // accounts.google.com -> "accounts.google": its whole form or any of its labels may be the service name.
    val hostForms = if (host.isEmpty()) emptyList() else listOf(host) + host.split('.')
    return hostForms.any { it in totpNames } || normalizeName(e.title).let { it.isNotEmpty() && it in totpNames }
}

/**
 * Rough strength: effective length x log2(size of the character sets used). Runs of 3+ equal symbols and sequences of
 * 3+ (abc, 321, qwe) count as one symbol; a very common password is 0. An estimate, not a guarantee: it does not know
 * dictionary words or personal data.
 *
 * It is measured twice, and the lower result is the answer (final review 1.3.1, F3): over the UTF-16 units of
 * [password] as typed (what it always measured, so text in NFC, in the basic plane and without format characters gets
 * exactly the number it got before), and over the code points of [strengthForm] (NFC without format characters). The
 * second keeps five equal emojis, letters typed decomposed (NFD) or invisible characters between digits from looking
 * strong; the first keeps the normalization from ever making a password look stronger than before (NFC can turn one
 * character into two code points or into an ASCII one).
 */
fun estimateBits(password: String): Double {
    val normalized = strengthForm(password)
    return minOf(
        bits(password, IntArray(password.length) { password[it].code }),
        bits(normalized, normalized.codePoints().toArray()),
    )
}

/**
 * [text] in NFC and without its format characters (Unicode Cf: U+200B, U+200D, U+FEFF, the bidirectional marks…): the
 * form whose code points [estimateBits] and the length rule of a new master also count (F3).
 */
internal fun strengthForm(text: String): String {
    val nfc = Normalizer.normalize(text, Normalizer.Form.NFC)
    val out = StringBuilder(nfc.length)
    nfc.codePoints().forEach { if (Character.getType(it) != Character.FORMAT.toInt()) out.appendCodePoint(it) }
    return out.toString()
}

/** The score of [estimateBits] for [text] read as [symbols] (its UTF-16 units or its code points, as ints). */
private fun bits(text: String, symbols: IntArray): Double {
    if (symbols.isEmpty() || text.lowercase(Locale.ROOT) in COMMON) return 0.0
    var pool = 0
    if (symbols.any { it in 'a'.code..'z'.code }) pool += 26
    if (symbols.any { it in 'A'.code..'Z'.code }) pool += 26
    if (symbols.any { it in '0'.code..'9'.code }) pool += 10
    if (symbols.any { it < 128 && !Character.isLetterOrDigit(it) }) pool += 33
    if (symbols.any { it >= 128 }) pool += 100
    return effectiveLength(symbols) * ln(pool.toDouble()) / ln(2.0)
}

/** Where [symbol] is in the keyboard [row], or -1 (the rows are ASCII). */
private fun indexIn(row: String, symbol: Int): Int = if (symbol < 128) row.indexOf(symbol.toChar()) else -1

private fun step(a: Int, b: Int): Int {
    if (a == b) return 0
    val x = Character.toLowerCase(a)
    val y = Character.toLowerCase(b)
    if ((Character.isLetterOrDigit(x) && Character.isLetterOrDigit(y)) && x - y in -1..1) return y - x
    for (row in KEYBOARD_ROWS) {
        val i = indexIn(row, x)
        val j = indexIn(row, y)
        if (i >= 0 && j >= 0 && i - j in -1..1) return j - i
    }
    return Int.MIN_VALUE
}

private fun effectiveLength(p: IntArray): Int {
    var count = 0
    var i = 0
    while (i < p.size) {
        var end = i
        if (i + 1 < p.size) {
            val d = step(p[i], p[i + 1])
            if (d != Int.MIN_VALUE) {
                end = i + 1
                while (end + 1 < p.size && step(p[end], p[end + 1]) == d) end++
            }
        }
        i = if (end - i + 1 >= 3) end + 1 else i + 1
        count++
    }
    return count
}
