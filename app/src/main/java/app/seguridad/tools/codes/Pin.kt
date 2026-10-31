package app.seguridad.tools.codes

import app.seguridad.crypto.secureRandom
import java.security.SecureRandom
import kotlin.math.log2

private const val MIN_DIGITS = 4
private const val MAX_DIGITS = 8

/** The 20 most used 4-digit PINs (public lists of leaked PINs); a 4-digit PIN in this set is weak. */
private val TOP_4 = BooleanArray(10_000).also { table ->
    listOf(
        "1234", "1111", "0000", "1212", "7777", "1004", "2000", "4444", "2222", "6969", "9999", "3333", "5555", "6666",
        "1122", "1313", "8888", "4321", "2001", "1010",
    ).forEach { table[it.toInt()] = true }
}

/** Days of each month; February has 29 (leap years are not checked). */
private val DAYS_IN_MONTH = intArrayOf(31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)

private fun validDate(day: Int, month: Int) = month in 1..12 && day in 1..DAYS_IN_MONTH[month - 1]

/**
 * Lines of the phone keypad (1 2 3 / 4 5 6 / 7 8 9 / 0 under the 8): the columns, the 0 included, and the two diagonals.
 * The outer columns reach the 0 too (1-4-7-0, 3-6-9-0), as the usual «1470» does. The rows are plain sequences.
 */
private val KEYPAD_LINES = listOf("1470", "2580", "3690", "159", "357")

/** [size] adjacent keys (3 or 4) of one line, in either direction, as a number: the building block of a keypad PIN. */
private fun keypadRuns(size: Int): BooleanArray {
    val runs = BooleanArray(if (size == 3) 1_000 else 10_000)
    for (line in KEYPAD_LINES) {
        for (way in listOf(line, line.reversed())) {
            for (start in 0..way.length - size) runs[way.substring(start, start + size).toInt()] = true
        }
    }
    return runs
}

private val KEYPAD_RUNS_3 = keypadRuns(3)
private val KEYPAD_RUNS_4 = keypadRuns(4)

/**
 * The columns swept left to right and back, as a finger does: any 4 to 8 digits in a row from one of these (14725836,
 * 14725, 36925, 8147) are a keypad line cut to length.
 */
private val KEYPAD_SWEEPS: Array<IntArray> =
    arrayOf("1472583690", "0963852741", "3692581470", "0741852963").map { sweep -> IntArray(sweep.length) { sweep[it] - '0' } }.toTypedArray()

private fun isKeypadSweep(d: IntArray, n: Int): Boolean {
    // Indexed loops: no iterator is allocated per call (strongPinCount runs this 10^8 times).
    for (k in KEYPAD_SWEEPS.indices) {
        val sweep = KEYPAD_SWEEPS[k]
        var start = 0
        while (start + n <= sweep.size) {
            if (sweep[start] == d[0]) {
                var i = 1
                while (i < n && sweep[start + i] == d[i]) i++
                if (i == n) return true
            }
            start++
        }
    }
    return false
}

private fun number(d: IntArray, from: Int, to: Int): Int {
    var v = 0
    for (i in from until to) v = v * 10 + d[i]
    return v
}

private fun isYear(v: Int) = v in 1900..2099

/** The rules, on the digits [d] (n of them, 4 to 8); no allocation, so that [strongPinCount] can run them 10^8 times. */
private fun weakDigits(d: IntArray, n: Int): Boolean {
    // Sequences: every step +1 (ascending) or -1 (descending), 9 and 0 next to each other.
    val step = (d[1] - d[0] + 10) % 10
    if (step == 1 || step == 9) {
        var run = true
        for (i in 2 until n) if ((d[i] - d[i - 1] + 10) % 10 != step) { run = false; break }
        if (run) return true
    }
    // Repetitions: one digit, or a block that repeats to fill the PIN (1212, 123123).
    for (p in 1..n / 2) {
        if (n % p != 0) continue
        var same = true
        for (i in p until n) if (d[i] != d[i - p]) { same = false; break }
        if (same) return true
    }
    if (n == 4) {
        val whole = number(d, 0, 4)
        if (TOP_4[whole] || isYear(whole)) return true
        val a = d[0] * 10 + d[1]
        val b = d[2] * 10 + d[3]
        if (validDate(a, b) || validDate(b, a)) return true // DDMM, MMDD
        return KEYPAD_RUNS_4[whole] || isKeypadSweep(d, 4)
    }
    // From 5 digits, a year (1900 to 2099) at the start or at the end.
    if (isYear(number(d, 0, 4)) || isYear(number(d, n - 4, n))) return true
    return isKeypadSweep(d, n) || when (n) {
        6 -> {
            val a = d[0] * 10 + d[1]
            val b = d[2] * 10 + d[3]
            val c = d[4] * 10 + d[5]
            validDate(a, b) || validDate(b, a) || validDate(c, b) || // DDMMYY, MMDDYY, YYMMDD
                (KEYPAD_RUNS_3[number(d, 0, 3)] && KEYPAD_RUNS_3[number(d, 3, 6)])
        }
        7 -> (KEYPAD_RUNS_3[number(d, 0, 3)] && KEYPAD_RUNS_4[number(d, 3, 7)]) ||
            (KEYPAD_RUNS_4[number(d, 0, 4)] && KEYPAD_RUNS_3[number(d, 4, 7)])
        8 -> KEYPAD_RUNS_4[number(d, 0, 4)] && KEYPAD_RUNS_4[number(d, 4, 8)] // 8-digit dates carry a year: caught above
        else -> false // 5 digits: no date, and no way to split it into runs of 3 and 4 keys (sweeps are checked above)
    }
}

/**
 * True for a 4 to 8 digit PIN with one of the most used patterns: the 20 most used 4-digit PINs, a single digit or a
 * block repeated (1111, 1212, 123123), an ascending or descending run (1234, 9876, 8901), a year from 1900 to 2099 (the
 * whole PIN at 4 digits, at either end from 5), a date (DDMM and MMDD; DDMMYY, MMDDYY and YYMMDD; no leap-year check),
 * keypad lines of 3 or more keys, alone or one after another (2580, 0852, 147258), and the column sweeps cut to length
 * (14725836, 14725, 36925).
 */
fun isWeakPin(pin: String): Boolean {
    require(pin.length in MIN_DIGITS..MAX_DIGITS && pin.all { it in '0'..'9' }) { "A PIN has 4 to 8 digits" }
    val d = IntArray(pin.length) { pin[it] - '0' }
    return weakDigits(d, d.size)
}

private fun pow10(digits: Int): Int {
    var v = 1
    repeat(digits) { v *= 10 }
    return v
}

/**
 * A random PIN of [digits] digits (4 to 8) that is not weak. Rejection sampling: each draw is uniform over all the
 * values (`nextInt(bound)` rejects inside, with no modulo bias) and a weak one is drawn again, so every PIN that is
 * left is equally likely.
 */
fun generatePin(digits: Int = 6, random: SecureRandom = secureRandom): String {
    require(digits in MIN_DIGITS..MAX_DIGITS) { "A PIN has 4 to 8 digits" }
    val bound = pow10(digits)
    val d = IntArray(digits)
    while (true) {
        var v = random.nextInt(bound)
        for (i in digits - 1 downTo 0) {
            d[i] = v % 10
            v /= 10
        }
        if (!weakDigits(d, digits)) return String(CharArray(digits) { '0' + d[it] })
    }
}

/**
 * How many PINs of [digits] digits are not weak, by going through all of them (10^8 at 8 digits): a few seconds, so
 * callers compute it once off the main thread and keep it.
 */
fun strongPinCount(digits: Int): Long {
    require(digits in MIN_DIGITS..MAX_DIGITS) { "A PIN has 4 to 8 digits" }
    val d = IntArray(digits)
    var strong = 0L
    var left = pow10(digits)
    while (left > 0) {
        if (!weakDigits(d, digits)) strong++
        // Next value: add 1 to the last digit, carrying to the left.
        var i = digits - 1
        while (i >= 0 && ++d[i] == 10) d[i--] = 0
        left--
    }
    return strong
}

/** Bits of entropy of a PIN drawn uniformly from [count] possibilities. */
fun pinBits(count: Long): Double = if (count <= 1) 0.0 else log2(count.toDouble())
