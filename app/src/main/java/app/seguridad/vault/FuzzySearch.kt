package app.seguridad.vault

import app.seguridad.imports.compareKey
import java.text.Normalizer
import java.util.Locale

/**
 * 2.0, task 4: the search that forgives typos (design «Búsqueda que perdona erratas»). Texts are compared as
 * [normalize] gives them: [compareKey] (what the screen shows, in NFKC, without what draws nothing), in lower case and
 * without accents, so «Cañón», «CANON» and «ｃａｎｏｎ» are the same. Lower scores are better; null means no match:
 * - an exact match (the whole query inside one text) scores under [FUZZY_BASE]: the whole text first, then its start,
 *   then the start of a word, then anywhere;
 * - otherwise every word of the query must be found in some word of the texts: inside it exactly, or, for a word of 4
 *   to 7 letters, at one Damerau-Levenshtein edit (the optimal string alignment distance: a transposition is one) and,
 *   from 8, at two; a word of under 4 letters only exactly. The edits are counted against the closest start of the word
 *   (so «amazn» finds «Amazon» while it is typed). These score [FUZZY_BASE] plus [EDIT_COST] per edit.
 * So every exact match comes before any with a typo. Words over [FUZZY_MAX_WORD] code points are only matched exactly,
 * which bounds the work on a hostile text. Plain string work: it never throws and keeps nothing.
 */
object FuzzySearch {
    /** The lowest score of a match that is not one exact piece of text. */
    const val FUZZY_BASE = 100

    /** What each edit (a typo) adds. */
    const val EDIT_COST = 10

    /** Longer words (in code points) are only compared exactly. */
    const val FUZZY_MAX_WORD = 64

    private val marks = Regex("\\p{M}+")

    /** [text] as the search compares it: [compareKey], lower case, without accents. */
    fun normalize(text: String): String {
        val key = compareKey(text).lowercase(Locale.ROOT)
        if (key.all { it.code < 0x80 }) return key
        return marks.replace(Normalizer.normalize(key, Normalizer.Form.NFD), "")
    }

    /** The words of [normalized] (runs of letters and digits), each as its code points. */
    fun words(normalized: String): List<IntArray> {
        val out = ArrayList<IntArray>()
        var start = -1
        var i = 0
        while (i <= normalized.length) {
            val cp = if (i < normalized.length) normalized.codePointAt(i) else -1
            val inWord = cp >= 0 && Character.isLetterOrDigit(cp)
            if (inWord && start < 0) start = i
            if (!inWord && start >= 0) {
                out += normalized.substring(start, i).codePoints().toArray()
                start = -1
            }
            i += if (cp >= 0) Character.charCount(cp) else 1
        }
        return out
    }

    /** How many edits each word of the query may have: by its length in code points. */
    internal fun allowedEdits(length: Int): Int = when {
        length < 4 -> 0
        length < 8 -> 1
        else -> 2
    }

    /** [query], normalized once for scoring many texts. */
    fun prepare(query: String): Query = Query(normalize(query))

    /** [score] of [query] against the one [text]. */
    fun score(query: String, text: String): Int? = prepare(query).score(listOf(normalize(text)))

    class Query internal constructor(val normalized: String) {
        private val queryWords: List<IntArray> = words(normalized)

        val isEmpty: Boolean get() = normalized.isBlank()

        /** The best score of this query against [texts], already through [normalize]. */
        fun score(texts: List<String>): Int? = score(texts, texts.map(::words))

        /**
         * The best score against [texts] (through [normalize]), whose words are [textWords] (from [words], one list per
         * text, in the same order): the list index precomputes both. The earlier texts win a tie among exact matches.
         */
        fun score(texts: List<String>, textWords: List<List<IntArray>>): Int? {
            if (isEmpty) return 0
            var best = Int.MAX_VALUE
            for ((i, t) in texts.withIndex()) {
                val at = t.indexOf(normalized)
                if (at < 0) continue
                val rank = when {
                    t.length == normalized.length -> 0
                    at == 0 -> 1
                    !Character.isLetterOrDigit(t.codePointBefore(at)) -> 2
                    else -> 3
                }
                best = minOf(best, rank * 10 + minOf(i, 9))
            }
            if (best != Int.MAX_VALUE) return best
            if (queryWords.isEmpty()) return null
            var edits = 0
            for (q in queryWords) {
                val allowed = if (q.size > FUZZY_MAX_WORD) 0 else allowedEdits(q.size)
                var found = Int.MAX_VALUE
                loop@ for (list in textWords) {
                    for (w in list) {
                        if (contains(w, q)) {
                            found = 0
                            break@loop
                        }
                        if (allowed > 0 && w.size <= FUZZY_MAX_WORD) {
                            val d = prefixDistance(q, w, allowed)
                            if (d < found) found = d
                        }
                    }
                }
                if (found > allowed) return null
                edits += found
            }
            return FUZZY_BASE + edits * EDIT_COST
        }
    }

    private fun contains(word: IntArray, part: IntArray): Boolean {
        if (part.size > word.size) return false
        outer@ for (s in 0..word.size - part.size) {
            for (j in part.indices) if (word[s + j] != part[j]) continue@outer
            return true
        }
        return false
    }

    /**
     * The fewest edits (optimal string alignment) from [q] to [w] or to any start of [w] (one code point at least);
     * [max] + 1 for anything over [max] (the work stops as soon as a row is all over it).
     */
    internal fun prefixDistance(q: IntArray, w: IntArray, max: Int): Int {
        val n = q.size
        val m = w.size
        if (n == 0 || m == 0) return max + 1
        var prev2 = IntArray(m + 1)
        var prev = IntArray(m + 1) { it }
        var row = IntArray(m + 1)
        for (i in 1..n) {
            row[0] = i
            var rowMin = row[0]
            for (j in 1..m) {
                val cost = if (q[i - 1] == w[j - 1]) 0 else 1
                var d = minOf(prev[j] + 1, row[j - 1] + 1, prev[j - 1] + cost)
                if (i > 1 && j > 1 && q[i - 1] == w[j - 2] && q[i - 2] == w[j - 1]) d = minOf(d, prev2[j - 2] + 1)
                row[j] = d
                if (d < rowMin) rowMin = d
            }
            if (rowMin > max) return max + 1
            val spare = prev2
            prev2 = prev
            prev = row
            row = spare
        }
        var best = max + 1
        for (j in 1..m) if (prev[j] < best) best = prev[j]
        return best
    }
}
