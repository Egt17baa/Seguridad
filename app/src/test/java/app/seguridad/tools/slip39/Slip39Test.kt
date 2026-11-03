package app.seguridad.tools.slip39

import app.seguridad.crypto.Bip39
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.security.SecureRandom

private fun asset(name: String) =
    listOf("src/main/assets", "../app/src/main/assets").map { File(it, name) }.first { it.exists() }.readLines().filter { it.isNotBlank() }

private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
private fun unhex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

/** The passphrase of every valid set in the official vectors (SLIP-0039, «Test vectors»). Test data only. */
private val VECTOR_PASSPHRASE = "TREZOR".toCharArray()

class Slip39Test {
    private val slip = Slip39(asset("slip39_english.txt"))
    private val bip39 = Bip39(asset("bip39_english.txt"))
    private val none = CharArray(0)

    private fun decoded(mnemonic: String, part: Int) = requireNotNull(slip.decode(mnemonic, part))

    private fun failure(block: () -> Unit): String {
        try {
            block()
        } catch (e: Slip39Failure) {
            return e.message.orEmpty()
        }
        fail("no Slip39Failure")
        error("unreachable")
    }

    @Test fun officialVectors() {
        val json = requireNotNull(javaClass.getResource("/vectors/slip39.json")) { "slip39.json" }.readText()
        val cases = Json.parseToJsonElement(json).jsonArray
        assertEquals(45, cases.size)
        var valid = 0
        for (case in cases) {
            val c = case.jsonArray
            val name = c[0].jsonPrimitive.content
            val mnemonics = c[1].jsonArray.map { it.jsonPrimitive.content }
            val secret = c[2].jsonPrimitive.content
            if (secret.isEmpty()) {
                // Review M-2: each invalid set must fail for its own reason, not just fail.
                val number = name.substringBefore(".").toInt()
                val want = requireNotNull(INVALID_VECTOR_REASONS[number]) { name }
                assertEquals(name, kind(want), kind(failure { slip.combineShares(mnemonics, VECTOR_PASSPHRASE) }))
            } else {
                valid++
                assertEquals(name, secret, hex(slip.combineShares(mnemonics, VECTOR_PASSPHRASE)))
            }
        }
        assertEquals(15, valid)
        assertEquals(30, INVALID_VECTOR_REASONS.size)
    }

    /** A message with its numbers taken out: the kind of failure, whatever part or group it names. */
    private fun kind(message: String) = message.replace(Regex("[0-9]+"), "#")

    /** The reason each invalid official vector must be refused for, by its number (the review's map). */
    private val INVALID_VECTOR_REASONS: Map<Int, String> = buildMap {
        for (n in listOf(2, 21)) put(n, slip39Checksum(1))
        for (n in listOf(3, 22, 40)) put(n, slip39BadLength(1))
        for (n in listOf(5, 24, 16, 35)) put(n, slip39WrongCount(1, 2, 1))
        for (n in listOf(6, 7, 8, 9, 25, 26, 27, 28)) put(n, SLIP39_NOT_SAME_SET)
        for (n in listOf(10, 29)) put(n, SLIP39_MORE_GROUPS_THAN_COUNT)
        for (n in listOf(11, 30)) put(n, slip39SameIndex(1))
        for (n in listOf(12, 31)) put(n, slip39ThresholdMismatch(1))
        for (n in listOf(13, 32)) put(n, SLIP39_DIGEST_MISMATCH)
        for (n in listOf(14, 15, 33, 34)) put(n, slip39TooFewGroups(2, 1))
        put(39, slip39WordCount(1, 19, SLIP39_MIN_WORDS, SLIP39_MAX_WORDS))
    }

    private fun roundTrip(master: ByteArray, gt: Int, groups: List<GroupSpec>, pick: (List<List<String>>) -> List<String>) {
        val parts = slip.splitSecret(master, "frase".toCharArray(), gt, groups, iterationExponent = 0)
        assertEquals(groups.map { it.count }, parts.map { it.size })
        assertArrayEquals(master, slip.combineShares(pick(parts), "frase".toCharArray()))
    }

    @Test fun splitCombineEveryThreshold() {
        val master = ByteArray(16) { it.toByte() }
        roundTrip(master, 1, listOf(GroupSpec(1, 1))) { it[0] }
        // 2-of-3: every pair, in both orders.
        val p23 = slip.splitSecret(master, none, 1, listOf(GroupSpec(2, 3)), iterationExponent = 0)[0]
        for (i in 0..2) for (j in 0..2) if (i != j) assertArrayEquals(master, slip.combineShares(listOf(p23[i], p23[j]), none))
        // 3-of-5: every triple.
        val m32 = ByteArray(32) { (255 - it).toByte() }
        val p35 = slip.splitSecret(m32, none, 1, listOf(GroupSpec(3, 5)), iterationExponent = 0)[0]
        for (i in 0..4) for (j in i + 1..4) for (k in j + 1..4) {
            assertArrayEquals(m32, slip.combineShares(listOf(p35[k], p35[i], p35[j]), none))
        }
        // Groups 2-of-(2-of-3, 1-of-1): two members of the first group and the only one of the second.
        roundTrip(master, 2, listOf(GroupSpec(2, 3), GroupSpec(1, 1))) { listOf(it[0][2], it[1][0], it[0][0]) }
        // Every even length from 16 to 32 bytes.
        for (n in 16..32 step 2) roundTrip(ByteArray(n) { (it * 7).toByte() }, 1, listOf(GroupSpec(2, 2))) { it[0] }
        // The largest scheme the format allows: 16 groups of 16.
        roundTrip(master, 16, List(16) { GroupSpec(16, 16) }) { g -> g.flatMap { it } }
    }

    @Test fun twentyAndThirtyThreeWords() {
        val p16 = slip.splitSecret(ByteArray(16), none, 1, listOf(GroupSpec(1, 1)), iterationExponent = 0)[0][0]
        val p32 = slip.splitSecret(ByteArray(32), none, 1, listOf(GroupSpec(1, 1)), iterationExponent = 0)[0][0]
        assertEquals(20, p16.split(" ").size)
        assertEquals(33, p32.split(" ").size)
    }

    @Test fun splitSetsTheExtendableFlagAndANewIdentifierEachTime() {
        val a = slip.splitSecret(ByteArray(16), none, 1, listOf(GroupSpec(2, 3)))
        val b = slip.splitSecret(ByteArray(16), none, 1, listOf(GroupSpec(2, 3)))
        val sa = decoded(a[0][0], 1)
        assertTrue(sa.extendable)
        assertEquals(SLIP39_DEFAULT_EXPONENT, sa.exponent)
        assertEquals(1, SLIP39_DEFAULT_EXPONENT)
        // Two splits of the same secret: different identifiers almost always (15 random bits), different parts always.
        assertNotEquals(a[0][0], b[0][0])
    }

    @Test fun bip39RoundTrip() {
        val random = SecureRandom()
        for (words in listOf(12, 15, 18, 21, 24)) {
            val entropy = ByteArray(words * 4 / 3).also(random::nextBytes)
            val phrase = bip39.phraseOf(entropy)
            assertEquals(words, phrase.size)
            assertTrue(bip39.check(phrase.joinToString(" ")).valid)
            val back = requireNotNull(bip39.entropyOf(phrase))
            assertArrayEquals(entropy, back)
            val parts = slip.splitSecret(back, "x".toCharArray(), 1, listOf(GroupSpec(2, 3)), iterationExponent = 0)[0]
            val master = slip.combineShares(listOf(parts[2], parts[0]), "x".toCharArray())
            assertEquals(phrase, bip39.phraseOf(master))
        }
        // The published BIP-39 vectors: all-zero entropy.
        assertEquals(List(11) { "abandon" } + "about", bip39.phraseOf(ByteArray(16)))
        assertEquals(List(23) { "abandon" } + "art", bip39.phraseOf(ByteArray(32)))
        assertEquals(List(11) { "zoo" } + "wrong", bip39.phraseOf(ByteArray(16) { -1 }))
        assertArrayEquals(ByteArray(16), bip39.entropyOf(List(11) { "abandon" } + "about"))
    }

    @Test fun bip39EntropyOfRefusesWhatIsNotAPhrase() {
        assertNull(bip39.entropyOf(List(12) { "abandon" })) // checksum
        assertNull(bip39.entropyOf(List(11) { "abandon" } + "abou")) // unknown word
        assertNull(bip39.entropyOf(List(13) { "abandon" })) // count
        assertNull(bip39.entropyOf(emptyList()))
        assertFalse(bip39.check(List(12) { "abandon" }.joinToString(" ")).valid)
        try {
            bip39.phraseOf(ByteArray(17))
            fail("17 bytes")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test fun tooFewShares() {
        val parts = slip.splitSecret(ByteArray(16) { 1 }, none, 1, listOf(GroupSpec(3, 5)), iterationExponent = 0)[0]
        assertEquals("En el grupo 1 hacen falta 3 partes y hay 2.", failure { slip.combineShares(parts.take(2), none) })
        // One more than needed is refused too (SLIP-0039: exactly the threshold).
        assertEquals("En el grupo 1 hacen falta 3 partes y hay 4.", failure { slip.combineShares(parts.take(4), none) })
        val grouped = slip.splitSecret(ByteArray(16) { 1 }, none, 2, listOf(GroupSpec(1, 1), GroupSpec(1, 1), GroupSpec(1, 1)), iterationExponent = 0)
        assertEquals("Hacen falta partes de 2 grupos y hay de 1.", failure { slip.combineShares(grouped[0], none) })
        assertEquals("Sobran partes: hacen falta partes de 2 grupos y hay de 3.", failure { slip.combineShares(grouped.flatten(), none) })
        failure { slip.combineShares(emptyList(), none) }
        failure { slip.combineShares(listOf("", "   "), none) }
    }

    @Test fun sharesFromTwoSplits() {
        val a = slip.splitSecret(ByteArray(16) { 2 }, none, 1, listOf(GroupSpec(2, 3)), iterationExponent = 0)[0]
        var b = slip.splitSecret(ByteArray(16) { 2 }, none, 1, listOf(GroupSpec(2, 3)), iterationExponent = 0)[0]
        // 1 in 2^15 times both splits get the same identifier: split again.
        while (b[0].split(" ").take(2) == a[0].split(" ").take(2)) {
            b = slip.splitSecret(ByteArray(16) { 2 }, none, 1, listOf(GroupSpec(2, 3)), iterationExponent = 0)[0]
        }
        assertEquals(
            "Las partes no son del mismo reparto: no coinciden su identificador, sus grupos o su longitud.",
            failure { slip.combineShares(listOf(a[0], b[1]), none) },
        )
    }

    @Test fun oneWordChanged() {
        val parts = slip.splitSecret(ByteArray(16) { 3 }, none, 1, listOf(GroupSpec(2, 3)), iterationExponent = 0)[0]
        val words = parts[1].split(" ").toMutableList()
        for (position in words.indices) {
            val changed = words.toMutableList()
            changed[position] = if (changed[position] == "academic") "acid" else "academic"
            val message = failure { slip.combineShares(listOf(parts[0], changed.joinToString(" ")), none) }
            assertEquals("La parte 2 tiene alguna palabra mal: su suma de control no cuadra.", message)
        }
        // Two words swapped: RS1024 sees it too.
        val swapped = words.toMutableList().also { val t = it[5]; it[5] = it[6]; it[6] = t }
        if (swapped != words) failure { slip.combineShares(listOf(parts[0], swapped.joinToString(" ")), none) }
    }

    @Test fun wrongPassphraseGivesAnotherSecret() {
        // SLIP-0039 on purpose: no passphrase is wrong, each one gives its own secret, without an error. The screen says so.
        val master = ByteArray(16) { 4 }
        val parts = slip.splitSecret(master, "buena".toCharArray(), 1, listOf(GroupSpec(2, 3)), iterationExponent = 0)[0]
        val other = slip.combineShares(parts.take(2), "mala".toCharArray())
        assertEquals(16, other.size)
        assertFalse(master.contentEquals(other))
        assertFalse(master.contentEquals(slip.combineShares(parts.take(2), none)))
        assertArrayEquals(master, slip.combineShares(parts.take(2), "buena".toCharArray()))
    }

    @Test fun gf256TablesMatchSpec() {
        // GF(256) of AES (FIPS-197 §4.2), polynomial x^8 + x^4 + x^3 + x + 1 (0x11B), generator 3.
        assertEquals(0x11B, Gf256.POLYNOMIAL)
        assertEquals(listOf(1, 3, 5, 15, 17, 51, 85, 255, 26), (0..8).map { Gf256.exp(it) })
        assertEquals(0, Gf256.log(1))
        assertEquals(1, Gf256.log(3))
        assertEquals(25, Gf256.log(2))
        assertEquals((1..255).toSet(), (0..254).map { Gf256.exp(it) }.toSet())
        for (a in 1..255) assertEquals(a, Gf256.exp(Gf256.log(a)))
        // FIPS-197 §4.2 examples: {57}·{83} = {c1}, {57}·{13} = {fe}; §5.1.1: the inverse of {53} is {ca}.
        assertEquals(0xC1, Gf256.mul(0x57, 0x83))
        assertEquals(0xFE, Gf256.mul(0x57, 0x13))
        assertEquals(1, Gf256.mul(0x53, 0xCA))
        // The constant-time product agrees with the tables for every pair.
        for (a in 0..255) for (b in 0..255) {
            val want = if (a == 0 || b == 0) 0 else Gf256.exp((Gf256.log(a) + Gf256.log(b)) % 255)
            if (Gf256.mul(a, b) != want) fail("$a·$b")
        }
    }

    @Test fun rs1024CustomisationStrings() {
        assertEquals("shamir", Rs1024.CUSTOMIZATION)
        assertEquals("shamir_extendable", Rs1024.CUSTOMIZATION_EXTENDABLE)
        val data = IntArray(17) { it * 37 % 1024 }
        val sum = Rs1024.checksum(Rs1024.CUSTOMIZATION, data)
        assertTrue(Rs1024.verify(Rs1024.CUSTOMIZATION, data + sum))
        assertFalse(Rs1024.verify(Rs1024.CUSTOMIZATION_EXTENDABLE, data + sum))
    }

    // Hostile input: bounded before any work, a Slip39Failure in Spanish, never a crash, never a word in the message.

    @Test fun boundsComeBeforeAnyWork() {
        val part = slip.splitSecret(ByteArray(16), none, 1, listOf(GroupSpec(1, 1)), iterationExponent = 0)[0][0]
        assertEquals("Hay demasiadas partes: como mucho 256.", failure { slip.combineShares(List(SLIP39_MAX_PARTS + 1) { part }, none) })
        // Review M-1: the most parts at their longest pass the bounds (and are then read: here they fail their checksum).
        val longest = List(SLIP39_MAX_WORDS) { "academic" }.joinToString(" ")
        assertEquals(slip39Checksum(1), failure { slip.combineShares(List(SLIP39_MAX_PARTS) { longest }, none) })
        // Thousands of huge parts: refused by their number and their length before any is normalised or split.
        val huge = "academic ".repeat(1_000_000)
        val started = System.nanoTime()
        assertEquals("Hay demasiadas partes: como mucho 256.", failure { slip.combineShares(List(5_000) { huge }, none) })
        assertEquals("La parte 1 es demasiado larga.", failure { slip.combineShares(listOf(huge), none) })
        assertEquals("La contraseña es demasiado larga: como mucho 256 caracteres.", failure { slip.combineShares(listOf(huge), CharArray(1_000_000)) })
        assertTrue(System.nanoTime() - started < 1_000_000_000L)
        assertEquals("La parte 1 es demasiado larga.", failure { slip.combineShares(listOf(part + " ".repeat(SLIP39_MAX_PART_CHARS)), none) })
        assertEquals("La parte 1 tiene 19 palabras: una parte tiene de 20 a 59.", failure { slip.combineShares(listOf(part.substringBeforeLast(" ")), none) })
        val long = (part.split(" ") + List(40) { "academic" }).joinToString(" ")
        assertEquals("La parte 1 tiene 60 palabras: una parte tiene de 20 a 59.", failure { slip.combineShares(listOf(long), none) })
        assertEquals("La contraseña es demasiado larga: como mucho 256 caracteres.", failure { slip.combineShares(listOf(part), CharArray(257) { 'a' }) })
    }

    @Test fun unknownWordsAreNamedByPositionOnly() {
        val part = slip.splitSecret(ByteArray(16), none, 1, listOf(GroupSpec(1, 1)), iterationExponent = 0)[0][0]
        val words = part.split(" ").toMutableList()
        for (bad in listOf("zzzz", "acadèmic", "акадeмic", "ａｃａｄｅｍｉｃx", "😀", "aca-demic", "12")) {
            val w = words.toMutableList().also { it[3] = bad }
            val message = failure { slip.combineShares(listOf(w.joinToString(" ")), none) }
            assertEquals(bad, "La palabra 4 de la parte 1 no es de la lista SLIP-39.", message)
        }
        // A lone surrogate (a broken paste) is refused, not passed to the normaliser.
        val lone = words.toMutableList().also { it[2] = "ac\uD800ne" }
        assertEquals("La palabra 3 de la parte 1 no es de la lista SLIP-39.", failure { slip.combineShares(listOf(lone.joinToString(" ")), none) })
        // A message never carries a word of a part.
        val message = failure { slip.combineShares(listOf(words.joinToString(" ") + " zzzz"), none) }
        assertTrue(message, words.none { it in message })
    }

    @Test fun caseSpacingAndUnicodeFormsAreAccepted() {
        val master = ByteArray(16) { 9 }
        val parts = slip.splitSecret(master, none, 1, listOf(GroupSpec(2, 3)), iterationExponent = 0)[0]
        val upper = parts[0].uppercase()
        // No-break spaces, tabs, line ends and runs of spaces between words; a fullwidth form (NFKC) and a BOM at the start.
        val spaced = "\uFEFF  " + parts[1].split(" ").joinToString("\u00A0\t  ") { it } + " \r\n"
        assertArrayEquals(master, slip.combineShares(listOf(upper, spaced), none))
        val fullwidth = parts[1].split(" ").mapIndexed { i, w -> if (i == 0) w.map { (it.code - 'a'.code + 0xFF41).toChar() }.joinToString("") else w }
        assertArrayEquals(master, slip.combineShares(listOf(parts[0], fullwidth.joinToString(" ")), none))
        // Invisible format characters (a zero-width space inside a word, from a paste) are dropped, as they cannot be seen.
        val zw = parts[1].split(" ").mapIndexed { i, w -> if (i == 4) w.take(2) + "\u200B" + w.drop(2) else w }
        assertArrayEquals(master, slip.combineShares(listOf(parts[0], zw.joinToString(" ")), none))
    }

    @Test fun duplicatePartsAreCountedOnce() {
        val master = ByteArray(16) { 5 }
        val parts = slip.splitSecret(master, none, 1, listOf(GroupSpec(2, 3)), iterationExponent = 0)[0]
        assertArrayEquals(master, slip.combineShares(listOf(parts[0], parts[0].uppercase(), parts[2]), none))
        assertEquals("En el grupo 1 hacen falta 2 partes y hay 1.", failure { slip.combineShares(listOf(parts[1], parts[1]), none) })
    }

    @Test fun passphraseMustBePrintableAscii() {
        val part = slip.splitSecret(ByteArray(16), none, 1, listOf(GroupSpec(1, 1)), iterationExponent = 0)[0][0]
        val ascii = "La contraseña solo puede llevar caracteres ASCII imprimibles: letras sin acentos ni eñes, números, espacios y signos."
        for (bad in listOf("contraseña", "café", "tab\there", "nueva\nlínea", "\u0000", "€", "😀", "\uD800", "\u007F")) {
            assertEquals(bad, ascii, failure { slip.combineShares(listOf(part), bad.toCharArray()) })
            assertEquals(bad, ascii, failure { slip.splitSecret(ByteArray(16), bad.toCharArray(), 1, listOf(GroupSpec(1, 1))) })
        }
        // Every printable ASCII character is accepted.
        val all = (32..126).map { it.toChar() }.toCharArray()
        val parts = slip.splitSecret(ByteArray(16) { 6 }, all, 1, listOf(GroupSpec(1, 1)), iterationExponent = 0)[0]
        assertArrayEquals(ByteArray(16) { 6 }, slip.combineShares(parts, all.copyOf()))
    }

    @Test fun theIterationExponentIsCapped() {
        assertEquals(8, SLIP39_MAX_EXPONENT)
        val tooMuch = "El cálculo pedido es demasiado largo: Clavero acepta un exponente de iteraciones de 0 a 8."
        assertEquals(tooMuch, failure { slip.splitSecret(ByteArray(16), none, 1, listOf(GroupSpec(1, 1)), iterationExponent = 9) })
        failure { slip.splitSecret(ByteArray(16), none, 1, listOf(GroupSpec(1, 1)), iterationExponent = -1) }
        // A share that asks for e = 15 (327 million PBKDF2 iterations) is refused before any PBKDF2.
        val share = decoded(slip.splitSecret(ByteArray(16), none, 1, listOf(GroupSpec(1, 1)), iterationExponent = 0)[0][0], 1)
        val hostile = slip.encode(share.copy(exponent = 15))
        val started = System.nanoTime()
        assertEquals(tooMuch, failure { slip.combineShares(listOf(hostile), none) })
        assertTrue(System.nanoTime() - started < 2_000_000_000L)
    }

    @Test fun splitRefusesWhatSlip39Forbids() {
        val one = listOf(GroupSpec(1, 1))
        val length = "El secreto tiene que tener de 16 a 32 bytes, en número par."
        for (n in listOf(0, 14, 15, 17, 31, 33, 34, 64)) assertEquals("$n", length, failure { slip.splitSecret(ByteArray(n), none, 1, one) })
        assertEquals(
            "Un grupo que pide 1 parte solo puede tener 1 parte: si quieres más copias, copia esa misma parte.",
            failure { slip.splitSecret(ByteArray(16), none, 1, listOf(GroupSpec(1, 2))) },
        )
        val groupLimits = "Hacen falta de 1 a 16 grupos, y los grupos necesarios tienen que ser de 1 al número de grupos."
        assertEquals(groupLimits, failure { slip.splitSecret(ByteArray(16), none, 2, one) })
        assertEquals(groupLimits, failure { slip.splitSecret(ByteArray(16), none, 0, one) })
        assertEquals(groupLimits, failure { slip.splitSecret(ByteArray(16), none, 1, emptyList()) })
        assertEquals(groupLimits, failure { slip.splitSecret(ByteArray(16), none, 1, List(17) { GroupSpec(1, 1) }) })
        val memberLimits = "En cada grupo, las partes tienen que ser de 1 a 16, y las necesarias, de 1 al número de partes."
        for (g in listOf(GroupSpec(0, 3), GroupSpec(4, 3), GroupSpec(2, 17), GroupSpec(17, 17), GroupSpec(-1, 1))) {
            assertEquals("$g", memberLimits, failure { slip.splitSecret(ByteArray(16), none, 1, listOf(g)) })
        }
    }

    @Test fun combineChecksTheRulesOfTheFormat() {
        val share = decoded(slip.splitSecret(ByteArray(16), none, 1, listOf(GroupSpec(1, 1)), iterationExponent = 0)[0][0], 1)
        // Group threshold over the group count (a well-formed checksum, a forged header).
        assertEquals(
            "Las partes piden más grupos de los que tiene el reparto.",
            failure { slip.combineShares(listOf(slip.encode(share.copy(groupThreshold = 3, groupCount = 2))), none) },
        )
        // Two different parts with the same member index.
        val parts = slip.splitSecret(ByteArray(16), none, 1, listOf(GroupSpec(2, 3)), iterationExponent = 0)[0]
        val a = decoded(parts[0], 1)
        val b = decoded(parts[1], 2)
        assertEquals(
            "Hay dos partes distintas con el mismo número en el grupo 1.",
            failure { slip.combineShares(listOf(parts[0], slip.encode(b.copy(memberIndex = a.memberIndex))), none) },
        )
        // A forged member threshold.
        assertEquals(
            "Las partes del grupo 1 no coinciden en cuántas hacen falta.",
            failure { slip.combineShares(listOf(parts[0], slip.encode(b.copy(memberThreshold = 3))), none) },
        )
        // A changed value with a recomputed checksum: the digest sees it.
        val forged = b.value.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }
        assertEquals(
            "Las partes no recomponen el secreto: alguna está cambiada o es de otro reparto.",
            failure { slip.combineShares(listOf(parts[0], slip.encode(b.copy(value = forged))), none) },
        )
    }

    @Test fun theWordListIsTheSpecOne() {
        val words = asset("slip39_english.txt")
        assertEquals(1024, words.size)
        assertEquals("academic", words.first())
        assertEquals("zero", words.last())
        assertEquals(words.sorted(), words)
        assertTrue(words.all { it.matches(Regex("[a-z]{4,8}")) })
        assertEquals(1024, words.map { it.take(4) }.toSet().size)
        try {
            Slip39(words.drop(1))
            fail("1023 words")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }
}
