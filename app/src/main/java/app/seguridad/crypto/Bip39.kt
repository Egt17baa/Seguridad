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
        val validCount = list.size in VALID_COUNTS
        val checksum = if (unknown.isEmpty() && validCount) checksumOk(list) else null
        return Bip39Check(list.size, unknown, validCount, checksum)
    }

    private fun checksumOk(list: List<String>): Boolean = entropyOf(list)?.also { it.fill(0) } != null

    /**
     * The entropy of [phrase] (normalized words: see [normalize]): 16 to 32 bytes for 12 to 24 words. Null if a word is
     * not in the list, the count is not 12, 15, 18, 21 or 24, or the built-in checksum fails. The caller wipes the result.
     */
    fun entropyOf(phrase: List<String>): ByteArray? {
        val n = phrase.size
        if (n !in VALID_COUNTS) return null
        val idx = IntArray(n)
        try {
            for (i in 0 until n) idx[i] = index[phrase[i]] ?: return null
            val totalBits = n * 11
            val checksumBits = n / 3
            val entropyBits = totalBits - checksumBits
            val entropy = ByteArray(entropyBits / 8)
            var checksum = 0
            for (k in 0 until totalBits) {
                val bit = (idx[k / 11] shr (10 - k % 11)) and 1
                if (k < entropyBits) {
                    entropy[k / 8] = (entropy[k / 8].toInt() or (bit shl (7 - k % 8))).toByte()
                } else {
                    checksum = (checksum shl 1) or bit
                }
            }
            val hash = MessageDigest.getInstance("SHA-256").digest(entropy)
            val expected = (hash[0].toInt() and 0xFF) shr (8 - checksumBits)
            hash.fill(0)
            if (checksum != expected) {
                entropy.fill(0)
                return null
            }
            return entropy
        } finally {
            idx.fill(0)
        }
    }

    /** The phrase of [entropy] (16, 20, 24, 28 or 32 bytes), with its checksum: the inverse of [entropyOf]. */
    fun phraseOf(entropy: ByteArray): List<String> {
        require(entropy.size in setOf(16, 20, 24, 28, 32)) { "BIP-39 entropy is 16 to 32 bytes, in steps of 4" }
        val entropyBits = entropy.size * 8
        val checksumBits = entropyBits / 32
        val hash = MessageDigest.getInstance("SHA-256").digest(entropy)
        val first = hash[0].toInt() and 0xFF
        hash.fill(0)
        val count = (entropyBits + checksumBits) / 11
        val idx = IntArray(count)
        try {
            for (k in 0 until entropyBits + checksumBits) {
                val bit = if (k < entropyBits) (entropy[k / 8].toInt() shr (7 - k % 8)) and 1 else (first shr (7 - (k - entropyBits))) and 1
                idx[k / 11] = idx[k / 11] or (bit shl (10 - k % 11))
            }
            return idx.map { words[it] }
        } finally {
            idx.fill(0)
        }
    }
}

private val VALID_COUNTS = setOf(12, 15, 18, 21, 24)

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
