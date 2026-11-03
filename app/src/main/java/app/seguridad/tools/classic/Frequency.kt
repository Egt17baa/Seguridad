package app.seguridad.tools.classic

/** How many times each letter A-Z appears in [text] (index 0 is A), case folded. Other characters are not counted. */
fun letterCounts(text: String): IntArray {
    val counts = IntArray(26)
    for (c in text) {
        when (c) {
            in 'A'..'Z' -> counts[c - 'A']++
            in 'a'..'z' -> counts[c - 'a']++
        }
    }
    return counts
}

/**
 * Index of coincidence of the letters A-Z of [text]: the chance that two letters drawn from it are the same,
 * `sum(n * (n - 1)) / (N * (N - 1))`. 0.0 when there are fewer than two letters.
 */
fun indexOfCoincidence(text: String): Double {
    val counts = letterCounts(text)
    val n = counts.sum().toLong()
    if (n < 2) return 0.0
    val same = counts.sumOf { it.toLong() * (it - 1) }
    return same.toDouble() / (n * (n - 1))
}

/** Reference index of coincidence of ordinary English text (about 0.0667), Spanish (about 0.0775) and uniform letters (1/26). */
const val IC_ENGLISH = 0.0667
const val IC_SPANISH = 0.0775
const val IC_UNIFORM = 1.0 / 26
