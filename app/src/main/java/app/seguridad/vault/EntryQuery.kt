package app.seguridad.vault

import java.text.Collator
import java.text.Normalizer
import java.util.Locale

const val MAX_TAGS = 10
const val MAX_TAG_LENGTH = 24

data class EntryFilter(val text: String = "", val tag: String? = null, val type: EntryType? = null)

private val accents = Regex("\\p{M}+")

/** Lowercase, without accents: the form in which searches compare. */
private fun fold(s: String): String =
    accents.replace(Normalizer.normalize(s.lowercase(Locale.ROOT), Normalizer.Form.NFD), "")

private fun collator(): Collator = Collator.getInstance(Locale.forLanguageTag("es-ES")).apply { strength = Collator.PRIMARY }

/**
 * Entries that match [f], favorites first and then by title (es-ES, ignoring case and accents).
 * The text only looks at the title, user, URL, 2FA service name and tags: never at passwords, seed words, notes or 2FA secrets.
 */
fun List<VaultEntry>.query(f: EntryFilter): List<VaultEntry> {
    val needle = fold(f.text.trim())
    val tag = f.tag?.let { fold(it.trim()) }
    val c = collator()
    return filter { e ->
        (f.type == null || e.type == f.type) &&
            (tag == null || e.tags.any { fold(it) == tag }) &&
            (needle.isEmpty() || listOf(e.title, e.username, e.url, e.totp?.issuer.orEmpty()).any { fold(it).contains(needle) } ||
                e.tags.any { fold(it).contains(needle) })
    }.sortedWith(
        compareByDescending<VaultEntry> { it.favorite }.thenComparator { a, b -> c.compare(a.title, b.title) },
    )
}

/** Comma-separated [raw] to labels: trimmed, lowercase, no empties or duplicates, 24 characters, at most 10. */
fun normalizeTags(raw: String): List<String> =
    raw.split(',').map { it.trim().lowercase(Locale.ROOT).take(MAX_TAG_LENGTH).trim() }
        .filter { it.isNotEmpty() }.distinct().take(MAX_TAGS)
