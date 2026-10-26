package app.seguridad.health

import app.seguridad.vault.EntryType
import app.seguridad.vault.VaultEntry
import java.net.URI
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

/** LOGIN entries with a non-empty password, grouped by password: password -> entry ids (in vault order). */
fun uniquePasswords(entries: List<VaultEntry>): Map<String, List<String>> =
    entries.filter { it.type == EntryType.LOGIN && it.password.isNotEmpty() }
        .groupBy({ it.password }, { it.id })

/**
 * Problems of each LOGIN that has a password; entries with none are left out. [breached] maps entry id to the
 * number of times its password appeared in leaks (filled in memory by the user-triggered check; empty otherwise).
 */
fun vaultHealth(entries: List<VaultEntry>, now: Long, breached: Map<String, Int> = emptyMap()): List<HealthItem> {
    val logins = entries.filter { it.type == EntryType.LOGIN && it.password.isNotEmpty() }
    val counts = logins.groupingBy { it.password }.eachCount()
    val totpNames = entries.filter { it.type == EntryType.TOTP }
        .flatMap { listOfNotNull(it.totp?.issuer, it.title) }
        .map { normalizeName(it) }
        .filter { it.isNotEmpty() }
        .toSet()
    return logins.mapNotNull { e ->
        val issues = buildSet {
            if ((counts[e.password] ?: 0) >= 2) add(Issue.REUSED)
            if (estimateBits(e.password) < WEAK_BITS) add(Issue.WEAK)
            val changed = e.passwordHistory.firstOrNull()?.changedAt ?: e.updatedAt
            if (now - changed > OLD_DAYS * DAY_MS) add(Issue.OLD)
            if (!has2fa(e, totpNames)) add(Issue.NO_2FA)
            if ((breached[e.id] ?: 0) > 0) add(Issue.BREACHED)
        }
        if (issues.isEmpty()) null else HealthItem(e.id, issues)
    }
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

private fun has2fa(e: VaultEntry, totpNames: Set<String>): Boolean {
    if (totpNames.isEmpty()) return false
    val host = normalizeName(hostOf(e.url))
    // accounts.google.com -> "accounts.google": its whole form or any of its labels may be the service name.
    val hostForms = if (host.isEmpty()) emptyList() else listOf(host) + host.split('.')
    return hostForms.any { it in totpNames } || normalizeName(e.title).let { it.isNotEmpty() && it in totpNames }
}

/**
 * Rough strength: effective length x log2(size of the character sets used). Runs of 3+ equal characters and
 * sequences of 3+ (abc, 321, qwe) count as one character; a very common password is 0. An estimate, not a guarantee:
 * it does not know dictionary words or personal data.
 */
fun estimateBits(password: String): Double {
    if (password.isEmpty() || password.lowercase(Locale.ROOT) in COMMON) return 0.0
    var pool = 0
    if (password.any { it in 'a'..'z' }) pool += 26
    if (password.any { it in 'A'..'Z' }) pool += 26
    if (password.any { it in '0'..'9' }) pool += 10
    if (password.any { it.code < 128 && !it.isLetterOrDigit() }) pool += 33
    if (password.any { it.code >= 128 }) pool += 100
    return effectiveLength(password) * ln(pool.toDouble()) / ln(2.0)
}

private fun step(a: Char, b: Char): Int {
    if (a == b) return 0
    val x = a.lowercaseChar()
    val y = b.lowercaseChar()
    if ((x.isLetterOrDigit() && y.isLetterOrDigit()) && x.code - y.code in -1..1) return y.code - x.code
    for (row in KEYBOARD_ROWS) {
        val i = row.indexOf(x)
        val j = row.indexOf(y)
        if (i >= 0 && j >= 0 && i - j in -1..1) return j - i
    }
    return Int.MIN_VALUE
}

private fun effectiveLength(p: String): Int {
    var count = 0
    var i = 0
    while (i < p.length) {
        var end = i
        if (i + 1 < p.length) {
            val d = step(p[i], p[i + 1])
            if (d != Int.MIN_VALUE) {
                end = i + 1
                while (end + 1 < p.length && step(p[end], p[end + 1]) == d) end++
            }
        }
        i = if (end - i + 1 >= 3) end + 1 else i + 1
        count++
    }
    return count
}
