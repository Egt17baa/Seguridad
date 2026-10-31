package app.seguridad.crypto

/**
 * Have I Been Pwned "range" API (k-anonymity): only the first 5 hex characters of the SHA-1 ([sha1HexChars]) ever leave
 * the phone. How many times the hash suffix appears in a range response; padding lines (count 0) count as absent.
 * [suffix] is any [CharSequence], so the caller can pass a view of its wiped `CharArray` (CIFRADO-1).
 */
fun pwnedCount(rangeBody: String, suffix: CharSequence): Int =
    rangeBody.lineSequence()
        .map { it.trim().split(':') }
        .firstOrNull { it.size == 2 && it[0].contentEquals(suffix, ignoreCase = true) }
        ?.get(1)?.trim()?.toIntOrNull() ?: 0
