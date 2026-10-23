package app.seguridad.crypto

import java.security.SecureRandom
import kotlin.math.ln

const val LOWER = "abcdefghijklmnopqrstuvwxyz"
const val UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
const val DIGITS = "0123456789"
const val SYMBOLS = "!@#\$%^&*()-_=+[]{};:,.<>?/~"
/** Easy to misread: 0/O, 1/l/I, and similar symbols. */
const val AMBIGUOUS = "0O1lI|`'\""

data class PasswordOptions(
    val length: Int = 20,
    val lower: Boolean = true,
    val upper: Boolean = true,
    val digits: Boolean = true,
    val symbols: Boolean = true,
    val excludeAmbiguous: Boolean = false,
)

private fun log2(x: Double) = ln(x) / ln(2.0)

private fun PasswordOptions.sets(): List<String> =
    listOfNotNull(LOWER.takeIf { lower }, UPPER.takeIf { upper }, DIGITS.takeIf { digits }, SYMBOLS.takeIf { symbols })
        .map { set -> if (excludeAmbiguous) set.filterNot { it in AMBIGUOUS } else set }

/** Random password with at least one character of every chosen set; null if no set is chosen. */
fun generatePassword(o: PasswordOptions, random: SecureRandom = secureRandom): String? {
    val sets = o.sets()
    if (sets.isEmpty()) return null
    val length = o.length.coerceIn(maxOf(8, sets.size), 128)
    val pool = sets.joinToString("")
    val chars = CharArray(length) { pool[random.nextInt(pool.length)] }
    // Put one from each set at distinct random positions, so every chosen type is present.
    val positions = (0 until length).shuffled(random).take(sets.size)
    sets.forEachIndexed { i, set -> chars[positions[i]] = set[random.nextInt(set.length)] }
    return String(chars)
}

/** Bits of entropy of a password drawn uniformly from the pool (a tiny over-estimate given the one-of-each rule). */
fun passwordEntropy(o: PasswordOptions): Double {
    val pool = o.sets().sumOf { it.length }
    if (pool == 0) return 0.0
    return o.length.coerceIn(8, 128) * log2(pool.toDouble())
}

data class PassphraseOptions(val words: Int = 6, val separator: String = "-", val capitalize: Boolean = false, val addNumber: Boolean = false)

fun generatePassphrase(list: List<String>, o: PassphraseOptions, random: SecureRandom = secureRandom): String {
    val words = List(o.words.coerceIn(4, 12)) {
        val w = list[random.nextInt(list.size)]
        if (o.capitalize) w.replaceFirstChar { c -> c.uppercase() } else w
    }
    val base = words.joinToString(o.separator)
    return if (o.addNumber) base + o.separator + random.nextInt(100) else base
}

fun passphraseEntropy(listSize: Int, o: PassphraseOptions): Double =
    o.words.coerceIn(4, 12) * log2(listSize.toDouble()) + if (o.addNumber) log2(100.0) else 0.0

enum class Strength(val label: String) { WEAK("Débil"), FAIR("Aceptable"), STRONG("Fuerte"), VERY_STRONG("Muy fuerte") }

fun strength(bits: Double): Strength = when {
    bits < 50 -> Strength.WEAK
    bits < 80 -> Strength.FAIR
    bits < 100 -> Strength.STRONG
    else -> Strength.VERY_STRONG
}
