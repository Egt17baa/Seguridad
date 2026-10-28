package app.seguridad.tools.codes

import app.seguridad.vault.EntryType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger
import java.security.SecureRandom

class RecoveryCodesTest {
    private val shape = Regex("[2-9A-HJ-NP-Z]{4}-[2-9A-HJ-NP-Z]{4}-[2-9A-HJ-NP-Z]{2}")

    @Test
    fun tenCodesShapeAndAlphabet() {
        val codes = recoveryCodes()
        assertEquals(10, codes.size)
        assertEquals(10, codes.toSet().size)
        for (c in codes) {
            assertTrue("«$c»", shape.matches(c))
            assertTrue(c.filter { it != '-' }.all { it in RECOVERY_ALPHABET })
        }
    }

    @Test
    fun alphabetHasNoLookAlikes() {
        assertEquals(32, RECOVERY_ALPHABET.length)
        assertEquals(32, RECOVERY_ALPHABET.toSet().size)
        for (c in "01OIl") assertFalse("«$c»", c in RECOVERY_ALPHABET)
        assertEquals("23456789ABCDEFGHJKLMNPQRSTUVWXYZ", RECOVERY_ALPHABET)
    }

    @Test
    fun noTwoEqualIn1000Runs() {
        repeat(1000) {
            val codes = recoveryCodes()
            assertEquals(10, codes.toSet().size)
        }
    }

    @Test
    fun aRunWithAnyDuplicateIsDrawnAgain() {
        // Scripted source, one symbol index per draw: the first set repeats its first code as its last, so it is discarded
        // whole and the second set is the one returned.
        val first = List(10) { i -> List(10) { j -> (i + j) % 32 } }.toMutableList().also { it[9] = it[0] }
        val second = List(10) { i -> List(10) { j -> (i * 3 + j * 5 + 1) % 32 } }
        val draws = ArrayDeque((first + second).flatten())
        val scripted = object : SecureRandom() {
            override fun nextInt(bound: Int): Int {
                assertEquals(32, bound)
                return draws.removeFirst()
            }
        }
        val expected = second.map { row ->
            val s = row.joinToString("") { RECOVERY_ALPHABET[it].toString() }
            "${s.substring(0, 4)}-${s.substring(4, 8)}-${s.substring(8)}"
        }
        assertEquals(expected, recoveryCodes(scripted))
        assertTrue(draws.isEmpty())
    }

    @Test
    fun fiftyBitsEach() {
        // 32 symbols over 10 places: 32^10 = 2^50.
        assertEquals(BigInteger.TWO.pow(50), BigInteger.valueOf(RECOVERY_ALPHABET.length.toLong()).pow(10))
    }

    @Test
    fun noteHoldsOneCodePerLineUnderTheExactTitle() {
        val codes = listOf("ABCD-EFGH-JK", "2345-6789-MN")
        val note = recoveryCodesNote("example.org", codes, now = 1_700_000_000_000, id = "id-1")
        assertEquals(EntryType.NOTE, note.type)
        assertEquals("Códigos de recuperación · example.org", note.title)
        assertEquals("ABCD-EFGH-JK\n2345-6789-MN", note.notes)
        assertEquals("id-1", note.id)
        assertEquals(1_700_000_000_000, note.createdAt)
        assertEquals(1_700_000_000_000, note.updatedAt)
        assertEquals("", note.password)
    }
}
