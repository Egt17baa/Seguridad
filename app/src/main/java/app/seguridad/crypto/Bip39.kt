package app.seguridad.crypto

import java.security.MessageDigest

data class UnknownWord(val position: Int, val word: String, val suggestion: String?)

data class Bip39Check(
    val count: Int,
    val unknown: List<UnknownWord>,
    /** 12, 15, 18, 21 or 24 words. */
    val validCount: Boolean,
    /** Null until every word is known and the count is valid. */
    val checksumOk: Boolean?,
) {
    val valid: Boolean get() = checksumOk == true
}

/** BIP-39 English list checks: unknown words (with a likely fix) and the built-in checksum. */
class Bip39(val words: List<String>) {
    private val index = words.withIndex().associate { (i, w) -> w to i }

    init {
        require(words.size == 2048)
    }

    fun normalize(phrase: String): List<String> = phrase.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }

    /** Up to [max] list words starting with [prefix]. */
    fun complete(prefix: String, max: Int = 6): List<String> {
        val p = prefix.trim().lowercase()
        if (p.isEmpty()) return emptyList()
        return words.filter { it.startsWith(p) }.take(max)
    }

    /** The closest list word: unique 4-letter prefix (BIP-39 words are unique by their first 4 letters) or edit distance 1. */
    fun suggest(word: String): String? {
        if (word.length >= 4) words.filter { it.startsWith(word.take(4)) }.singleOrNull()?.let { return it }
        return words.firstOrNull { editDistanceAtMostOne(it, word) }
    }

    fun check(phrase: String): Bip39Check {
        val list = normalize(phrase)
        val unknown = list.withIndex().filter { it.value !in index }.map { UnknownWord(it.index + 1, it.value, suggest(it.value)) }
        val validCount = list.size in setOf(12, 15, 18, 21, 24)
        val checksum = if (unknown.isEmpty() && validCount) checksumOk(list) else null
        return Bip39Check(list.size, unknown, validCount, checksum)
    }

    private fun checksumOk(list: List<String>): Boolean {
        val bits = StringBuilder()
        list.forEach { bits.append(Integer.toBinaryString(index.getValue(it)).padStart(11, '0')) }
        val csLen = list.size * 11 / 33
        val entLen = bits.length - csLen
        val entropy = ByteArray(entLen / 8) { bits.substring(it * 8, it * 8 + 8).toInt(2).toByte() }
        val hash = MessageDigest.getInstance("SHA-256").digest(entropy)
        val expected = (0 until csLen).joinToString("") { i -> ((hash[i / 8].toInt() shr (7 - i % 8)) and 1).toString() }
        entropy.fill(0)
        return bits.substring(entLen) == expected
    }
}

private fun editDistanceAtMostOne(a: String, b: String): Boolean {
    if (kotlin.math.abs(a.length - b.length) > 1) return false
    var i = 0
    var j = 0
    var edits = 0
    while (i < a.length && j < b.length) {
        if (a[i] == b[j]) {
            i++; j++
        } else {
            if (++edits > 1) return false
            when {
                a.length > b.length -> i++
                a.length < b.length -> j++
                else -> { i++; j++ }
            }
        }
    }
    return edits + (a.length - i) + (b.length - j) <= 1
}
