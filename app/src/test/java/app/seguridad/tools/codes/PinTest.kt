package app.seguridad.tools.codes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.security.SecureRandom

class PinTest {
    @Test
    fun weakOnes() {
        val weak = listOf(
            "0000", "1234", "9876", "8901", "1212", "123123", "1987", "2024", "19870", "311299", "1231", "2580", "1470",
            "0852", "147258", "1004",
        )
        for (pin in weak) assertTrue("«$pin» should be weak", isWeakPin(pin))
    }

    @Test
    fun theTopTwentyFourDigitPinsAreWeak() {
        val top20 = listOf(
            "1234", "1111", "0000", "1212", "7777", "1004", "2000", "4444", "2222", "6969", "9999", "3333", "5555", "6666",
            "1122", "1313", "8888", "4321", "2001", "1010",
        )
        for (pin in top20) assertTrue("«$pin» should be weak", isWeakPin(pin))
    }

    @Test
    fun fineOnes() {
        for (pin in listOf("7391", "502846")) assertFalse("«$pin» should not be weak", isWeakPin(pin))
    }

    @Test
    fun repetitionsAndSequencesOfEveryLength() {
        // Same digit, repeated block, ascending and descending runs (9 to 0 wraps).
        for (pin in listOf("55555", "777777", "12341234", "4545", "123456", "654321", "567890", "12345678", "98765432", "0123")) {
            assertTrue("«$pin» should be weak", isWeakPin(pin))
        }
    }

    @Test
    fun yearsAtTheStartOrEndOfLongerPins() {
        // 1900 to 2099, whole in 4 digits, at either end from 5 digits on.
        for (pin in listOf("1900", "2099", "19001", "81999", "209948", "372024", "20991357", "58732010")) {
            assertTrue("«$pin» should be weak", isWeakPin(pin))
        }
        // Outside the range, or in the middle: not a year pattern.
        for (pin in listOf("1899", "2100", "847193", "7390", "5173842")) assertFalse("«$pin» should not be weak", isWeakPin(pin))
        assertFalse("«18995» is not a year at either end", isWeakPin("18995"))
    }

    @Test
    fun datesAreValidDaysOfTheirMonthWithoutLeapYears() {
        // DDMM, MMDD, DDMMYY, MMDDYY and YYMMDD.
        for (pin in listOf("2802", "2902", "3112", "1231", "0229", "311299", "123199", "991231", "010195")) {
            assertTrue("«$pin» should be weak", isWeakPin(pin))
        }
        // 30 and 31 February, a 31st of a 30-day month, month 13 and day 00 read as no date in any order.
        for (pin in listOf("3002", "3102", "3104", "3511", "7300", "0068")) assertFalse("«$pin» should not be weak", isWeakPin(pin))
    }

    @Test
    fun keypadLinesInBothDirectionsAndTheirConcatenations() {
        val weak = listOf(
            // The columns (with the 0 under the middle one, and the outer ones read down to it) in both directions.
            "1470", "0741", "2580", "0852", "3690", "0963",
            // Two lines of 3 keys, one of 3 and one of 4, two of 4.
            "147258", "369147", "741852", "258369", "159357", "951753", "1472580", "1470369", "2580147", "14702580", "25800852",
        )
        for (pin in weak) assertTrue("«$pin» should be weak", isWeakPin(pin))
        // A line needs 3 keys in a row; 5 digits cannot be split into lines of 3 and 4 keys.
        for (pin in listOf("14729", "14793", "25847", "1597", "4710")) assertFalse("«$pin» should not be weak", isWeakPin(pin))
    }

    @Test
    fun generatedNeverWeak() {
        for (digits in 4..8) {
            repeat(10_000) {
                val pin = generatePin(digits)
                assertEquals(digits, pin.length)
                assertTrue("only digits: «$pin»", pin.all { it in '0'..'9' })
                assertFalse("weak PIN generated: «$pin»", isWeakPin(pin))
            }
        }
        assertEquals(6, generatePin().length)
    }

    @Test
    fun rejectionSamplingKeepsLeadingZerosAndSkipsWeakDraws() {
        // Scripted source: 1234 and 1111 are weak and are drawn again; 42 is the strong «0042». Only nextInt(bound) is used.
        val draws = ArrayDeque(listOf(1234, 1111, 42))
        val bounds = mutableListOf<Int>()
        val scripted = object : SecureRandom() {
            override fun nextInt(bound: Int): Int {
                bounds += bound
                return draws.removeFirst()
            }

            override fun nextInt(): Int = throw AssertionError("an unbounded Int would need a biased reduction")
        }
        assertEquals("0042", generatePin(4, scripted))
        assertEquals(listOf(10_000, 10_000, 10_000), bounds)
    }

    @Test
    fun digitsOutsideFourToEightAreRefused() {
        for (digits in listOf(3, 9, 0, -1)) {
            assertRefused("generatePin($digits)") { generatePin(digits) }
            assertRefused("strongPinCount($digits)") { strongPinCount(digits) }
        }
        for (pin in listOf("", "123", "123456789", "12a4", "١٢٣٤")) assertRefused("isWeakPin(«$pin»)") { isWeakPin(pin) }
    }

    @Test
    fun countMatchesBruteForce() {
        // 4 to 6 digits by brute force over the public isWeakPin: the counter and the checker are one rule.
        for (digits in 4..6) {
            var strong = 0L
            val limit = pow10(digits)
            for (v in 0 until limit) if (!isWeakPin(v.toString().padStart(digits, '0'))) strong++
            assertEquals("strong $digits-digit PINs", strong, strongPinCount(digits))
        }
    }

    @Test
    fun countsAreSaneAndEightDigitsRunInTime() {
        var previous = 0L
        for (digits in 4..8) {
            val started = System.nanoTime()
            val count = strongPinCount(digits)
            val seconds = (System.nanoTime() - started) / 1e9
            val all = pow10(digits).toLong()
            println("strongPinCount($digits) = $count of $all in ${"%.2f".format(seconds)} s")
            assertTrue("$digits digits: $count", count in all * 7 / 10 until all)
            assertTrue(count > previous)
            previous = count
            assertTrue("$digits digits took $seconds s", seconds < 30)
        }
    }

    @Test
    fun sevenDigitsMatchBruteForce() {
        // Slower (10^7 strings) but still a few seconds: 5 and 6 digits are covered above, this checks the 7-digit split of lines.
        var strong = 0L
        for (v in 0 until 10_000_000) if (!isWeakPin(v.toString().padStart(7, '0'))) strong++
        assertEquals(strong, strongPinCount(7))
    }

    private fun assertRefused(what: String, block: () -> Unit) {
        try {
            block()
            fail("$what should be refused")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    private fun pow10(n: Int): Int = (1..n).fold(1) { a, _ -> a * 10 }
}
