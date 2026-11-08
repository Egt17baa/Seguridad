package app.seguridad.vault

import app.seguridad.crypto.PassphraseOptions
import app.seguridad.crypto.PasswordOptions
import app.seguridad.crypto.generatePassphrase
import app.seguridad.crypto.generatePassword
import app.seguridad.health.WEAK_BITS
import app.seguridad.health.commonPasswords
import app.seguridad.health.estimateBits
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.security.SecureRandom
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.Executors

/**
 * CRY-A01 (review 1.3.1): a NEW master password has to be long enough and pass the strength estimate of «Salud». The
 * rule is only for creating the vault and for changing the master; opening and importing never go through it.
 *
 * Owner's amendment (1-11-2026): besides, a new master that is ONE known word (the common list of «Salud» or a word of
 * the EFF, BIP-39 English or BIP-39 Spanish list, whatever its capitals, accents, digits and symbols) is refused too.
 * The tests without `words` are the rule as it was; the ones with [allWords] are the rule with the lists loaded.
 */
class MasterPolicyTest {
    // ---- The rule ----

    @Test fun `the texts are the ones the Abogado Digital reviews`() {
        assertEquals(10, MIN_MASTER)
        assertEquals("Mínimo 10 caracteres", MASTER_TOO_SHORT)
        assertEquals(
            "Esta contraseña es fácil de adivinar. Usa una frase de 5 o 6 palabras que solo tú conozcas (el Generador te puede dar una).",
            MASTER_TOO_WEAK,
        )
    }

    @Test fun `a short password says it is too short, before saying it is weak`() {
        assertEquals(MASTER_TOO_SHORT, newMasterProblem("corta"))
        assertEquals(MASTER_TOO_SHORT, newMasterProblem(""))
        assertEquals(MASTER_TOO_SHORT, newMasterProblem("a".repeat(MIN_MASTER - 1)))
        assertEquals(MASTER_TOO_SHORT, newMasterProblem("Xq7#vB2!m")) // 9 characters, strong by the estimate: length comes first.
    }

    /** F3 (N-02): ten characters in both counts, the UTF-16 units typed and the code points of NFC without Cf. */
    @Test fun `F3 five emojis, five letters typed decomposed or five digits with invisible characters are too short`() {
        val nfd = { t: String -> Normalizer.normalize(t, Normalizer.Form.NFD) }
        for (c in listOf("😀😀😀😀😀", nfd("ąąąąą"), "1\u200B2\u200B3\u200B4\u200B5\u200B", "\u095C".repeat(5))) {
            assertEquals(c, MASTER_TOO_SHORT, newMasterProblem(c))
            assertEquals(c, MASTER_TOO_SHORT, newMasterProblem(c, allWords))
        }
        // Long enough in both counts, and still a run or a sequence: the estimate refuses them.
        val digitsApart = "0123456789".toList().joinToString("\u200B")
        for (c in listOf("😀".repeat(10), nfd("ą".repeat(10)), digitsApart, "\u095C".repeat(10))) {
            assertEquals(c, MASTER_TOO_WEAK, newMasterProblem(c))
            assertEquals(c, MASTER_TOO_WEAK, newMasterProblem(c, allWords))
        }
    }

    @Test fun `ten characters are enough for the length, the strength decides next`() {
        assertEquals(MASTER_TOO_WEAK, newMasterProblem("a".repeat(MIN_MASTER)))
        assertNull(newMasterProblem("Xq7#vB2!mK"))
    }

    /**
     * Why each one is refused: «contraseña», «Contraseña» and «CONTRASEÑA» only because that exact word (in lower case) is
     * in the common list of «Salud», not because of their strength (see the next test: add a digit and the estimate alone accepts them); the
     * others because a run of equal characters, or a sequence of the alphabet, the digits or a keyboard row, counts as one.
     */
    @Test fun `the obvious passwords of ten characters or more are too weak`() {
        for (weak in listOf("contraseña", "Contraseña", "CONTRASEÑA", "1234567890", "aaaaaaaaaa", "qwertyuiop", "          ", "abcdefghij", "0000000000")) {
            assertEquals("«$weak» must be refused", MASTER_TOO_WEAK, newMasterProblem(weak))
        }
    }

    @Test fun `a phrase of words is accepted`() {
        assertNull(newMasterProblem("casa perro luz mar sol"))
        assertNull(newMasterProblem("casa-perro-luz-mar-sol"))
    }

    /** The limit of the estimate, on purpose: it has no dictionary, so a word with a capital and a digit passes. */
    @Test fun `Barcelona1 is the known limit of the estimate and passes`() {
        val bits = estimateBits("Barcelona1")
        assertTrue("$bits", bits >= WEAK_BITS && bits < 65.0)
        assertNull(newMasterProblem("Barcelona1"))
    }

    /**
     * The second limit of the estimate, pinned on purpose: any character outside ASCII (ñ, an accent) adds a pool of 100
     * symbols in estimateBits, about 7 bits per character, so a Spanish word with one of them looks strong. «contraseña»
     * alone is refused only because it is in the common list; with a digit after it, or with a year, the estimate alone accepts it. If
     * estimateBits changes (an accent-folding pass, a dictionary, another threshold), this test shows what that does to
     * the rule for a new master: change it then, knowing the effect.
     *
     * This is the estimate alone, with no `words`. The owner's amendment closes the gap for ONE known word: with the lists
     * loaded «contraseña123» and «Contraseña2024» are refused (`what the estimate lets through and the word rule catches`).
     */
    @Test fun `a word with an accent or an enye gets a large bonus, so the estimate alone accepts contrasena123 and Contrasena2024`() {
        for (c in listOf("contraseña1", "contraseña123", "Contraseña2024")) {
            val bits = estimateBits(c)
            assertTrue("«$c»: $bits bits", bits >= WEAK_BITS)
            assertNull("«$c» is accepted by the estimate alone (no word lists)", newMasterProblem(c))
        }
        // Without the ñ the bonus is gone and the estimate is lower, but the estimate alone still accepts it: it does not refuse words.
        assertTrue(estimateBits("contrasena123") < estimateBits("contraseña123"))
        assertNull(newMasterProblem("contrasena123"))
        // What the common list catches is only the exact word.
        assertEquals(MASTER_TOO_WEAK, newMasterProblem("contraseña"))
    }

    /** The list the word rule takes from «Salud» is the one the estimate already gives 0 bits (Health.kt), not a copy. */
    @Test fun `the common list the rule reads is the one of Salud`() {
        val common = commonPasswords()
        assertTrue(common.size >= 10)
        for (c in common) assertEquals("«$c»", 0.0, estimateBits(c), 0.0)
        assertTrue(common.all { it.isNotEmpty() })
    }

    @Test fun `the threshold is the one of Salud`() {
        // Same function and same constant as the «Débil» mark of Salud: a password refused here is flagged weak there.
        assertEquals(50.0, WEAK_BITS, 0.0)
        for (c in listOf("contraseña", "1234567890", "aaaaaaaaaa", "qwertyuiop", "Barcelona1", "casa perro luz mar sol", "Xq7#vB2!mK")) {
            assertEquals(c, estimateBits(c) < WEAK_BITS, newMasterProblem(c) == MASTER_TOO_WEAK)
        }
    }

    // ---- One known word is not a master (owner's amendment, 1-11-2026) ----

    @Test fun `the core of a password is its letters, lowercase and without accents`() {
        assertEquals("contrasena", masterCore("Contraseña2024!"))
        assertEquals("abaco", masterCore("Ábaco-12345"))
        assertEquals("abaco", masterCore("A\u0301baco-12345")) // the same with a combining accent (the Spanish list is stored so)
        assertEquals("nandu", masterCore("ÑANDÚ 99"))
        assertEquals("password", masterCore("pass word-1!"))
        assertEquals("password", masterCore("password\uD83D\uDE00")) // an emoji is not a letter
        assertEquals("", masterCore("1234567890"))
        assertEquals("", masterCore(""))
    }

    @Test fun `the core does not depend on the language of the phone`() {
        val before = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR")) // with it, a plain lowercase of «I» gives the dotless «ı»
            assertEquals("iloveyou", masterCore("ILOVEYOU12"))
            assertEquals("title", masterCore("TITLE-1"))
        } finally {
            Locale.setDefault(before)
        }
    }

    @Test fun `the words are the cores of the lists and of the common list of Salud`() {
        val words = masterWords(listOf(listOf("Ábaco", "drop-down", "", "123", "  ", "NIÑO"), listOf("zebra")))
        assertTrue(setOf("abaco", "dropdown", "nino", "zebra").all { it in words })
        assertFalse("no empty word, or every password of digits would be refused", "" in words)
        // The common list of «Salud» (Health.kt), passed through the same normalization.
        assertTrue(setOf("password", "qwerty", "contrasena", "admin", "iloveyou").all { it in masterWords(emptyList()) })
        assertFalse("" in masterWords(emptyList()))
        // The three real lists: every word of every list is there, normalized, and nothing but that.
        for (name in MASTER_WORD_LISTS) assertTrue(name, asset(name).map(::masterCore).filter { it.isNotEmpty() }.all { it in allWords })
        assertTrue("${allWords.size} words", allWords.size > 9000)
        assertTrue("abaco" in allWords && "abandon" in allWords && "abdominal" in allWords)
    }

    @Test fun `the three lists are the ones of assets the app already reads`() {
        assertEquals(listOf("eff_large_wordlist.txt", "bip39_english.txt", "bip39_spanish.txt"), MASTER_WORD_LISTS)
        for (name in MASTER_WORD_LISTS) assertTrue(name, asset(name).size >= 2048)
        // The EFF list has no dice numbers any more (the app removed them), so a line is a word.
        assertTrue(asset("eff_large_wordlist.txt").all { line -> line.none { it.isDigit() } })
    }

    /** Final review M2, F7: the build fails if any list of the rule would be missing from the APK (see checkAssets). */
    @Test fun `checkAssets fails the build when a list of the master rule is not in the APK`() {
        val gradle = projectFile("app/build.gradle.kts").readText()
        val anchor = "val checkAssets = tasks.register(\"checkAssets\")"
        assertTrue("app/build.gradle.kts must declare $anchor", anchor in gradle)
        val task = gradle.substringAfter(anchor)
            .substringBefore("tasks.named(\"check\")")
        for (name in MASTER_WORD_LISTS) assertTrue(name, "\"$name\"" in task)
        assertTrue(task, Regex("""if \(missing\.isNotEmpty\(\)\) \{\s*throw GradleException""").containsMatchIn(task))
    }

    @Test fun `one known word is refused whatever is around it, once the lists are loaded`() {
        for (c in listOf(
            "contraseña123", "Contraseña2024", "Password1!", "iloveyou12", "abandon1234", "Ábaco12345", "A\u0301baco12345", "abaco123456",
            "ABANDON-2024!", "1234abandon", "abandon!!!!!!", "a.b.a.n.d.o.n.1", "  abandon  1234", "Admin12345", "QWERTY-12345", "Abc1234567",
        )) {
            assertEquals("«$c» must be refused with the lists", MASTER_TOO_WEAK, newMasterProblem(c, allWords))
        }
        // A word of the EFF list of 7 letters or more with 123 after it (10 characters or more), as it is or with a capital.
        val eff = asset("eff_large_wordlist.txt").filter { masterCore(it).length >= 7 }
        assertTrue(eff.size > 1000)
        for (w in eff) for (c in listOf(w + "123", w.replaceFirstChar { it.uppercase() } + "123")) {
            assertEquals("«$c» must be refused with the lists", MASTER_TOO_WEAK, newMasterProblem(c, allWords))
        }
    }

    /** The reason for the amendment: these pass the estimate (an ñ or an accent weighs about 7 bits), only the word refuses them. */
    @Test fun `what the estimate lets through and the word rule catches`() {
        for (c in listOf("contraseña123", "Contraseña2024", "Password1!", "iloveyou12")) {
            assertTrue("«$c»: ${estimateBits(c)} bits", estimateBits(c) >= WEAK_BITS)
            assertNull("«$c» passes without the lists", newMasterProblem(c))
            assertEquals("«$c» does not pass with them", MASTER_TOO_WEAK, newMasterProblem(c, allWords))
        }
    }

    @Test fun `the order is the length first, then the estimate and the word`() {
        assertEquals(MASTER_TOO_SHORT, newMasterProblem("abaco", allWords)) // a list word, but short
        assertEquals(MASTER_TOO_SHORT, newMasterProblem("Ábaco123", allWords))
        assertEquals(MASTER_TOO_SHORT, newMasterProblem("", allWords))
        assertEquals(MASTER_TOO_WEAK, newMasterProblem("aaaaaaaaaa", allWords)) // not a word, but the estimate refuses it
        assertEquals(MASTER_TOO_WEAK, newMasterProblem("1234567890", allWords)) // the core is empty: no word, the estimate refuses it
    }

    @Test fun `two words together pass if the estimate reaches, and so does a word that is not in any list`() {
        // The accepted limit, on purpose: «una sola palabra» is the whole core, not a word inside it.
        assertNull(newMasterProblem("abandon-ability", allWords))
        assertNull(newMasterProblem("casa perro luz mar sol", allWords))
        assertNull(newMasterProblem("Xq7#vB2!mK", allWords))
        assertFalse("«barcelona» is now in a word list: use another city in this example", "barcelona" in allWords)
        assertNull(newMasterProblem("Barcelona1", allWords))
    }

    // ---- RT-3 (owner's decision (h), 8-11-2026): a known word in leet or in full-width letters is refused too ----

    @Test fun `RT-3 the core is taken in NFKC, so full-width and styled letters are the plain ones`() {
        assertEquals("password", masterCore("ｐａｓｓｗｏｒｄ12345"))
        assertEquals("contrasena", masterCore("ｃｏｎｔｒａｓｅñａ2024"))
        assertEquals("pass", masterCore("𝐩𝐚𝐬𝐬")) // mathematical bold «𝐩𝐚𝐬𝐬»
        assertEquals("fiesta", masterCore("ﬁesta")) // the ligature «ﬁ»
        assertEquals("", masterCore("１２３４５６７８９０")) // full-width digits are digits
    }

    @Test fun `RT-3 a known word in leet or in full-width letters is refused as a new master`() {
        for (c in listOf(
            "P4ssw0rd2024!", "C0ntr4s3ñ4!!", "P@\$\$w0rd1234", "Dr4g0n!2024", "Pr1nc3ss2024", "ｐａｓｓｗｏｒｄ12345", "ｃｏｎｔｒａｓｅñａ2024",
            "1l0v3y0u2024", "4dm1n-2024!!", "!!P4SSW0RD!!", "2024-dr4g0n!", "p4ss-w0rd 99", "Ｐ４ｓｓｗ０ｒｄ２０２４",
        )) {
            assertTrue("«$c» passes the estimate (${estimateBits(c)} bits): only the word rule refuses it", estimateBits(c) >= WEAK_BITS)
            assertEquals("«$c» must be refused with the lists", MASTER_TOO_WEAK, newMasterProblem(c, allWords))
        }
    }

    @Test fun `RT-3 the substitutions, and 2 is never z`() {
        for ((typed, letter) in listOf(
            "0" to "o", "3" to "e", "4" to "a", "@" to "a", "5" to "s", "\$" to "s", "7" to "t", "+" to "t", "8" to "b", "6" to "g",
            "9" to "g", "1" to "i", "1" to "l", "!" to "i", "!" to "l", "|" to "i", "|" to "l",
        )) {
            assertTrue("$typed as $letter", isKnownWordInLeet("x${typed}y", setOf("x${letter}y")))
        }
        assertFalse(isKnownWordInLeet("x2y", setOf("xzy")))
        // «1» is read once as «i» everywhere and once as «l» everywhere, never mixed.
        assertTrue(isKnownWordInLeet("b4110on", setOf("balloon")))
        assertTrue(isKnownWordInLeet("1nf1ll", setOf("infill")))
        assertFalse(isKnownWordInLeet("1nf1ll", setOf("lnfill")))
    }

    @Test fun `RT-3 only digits and symbols may be left out around the word, never a letter`() {
        assertTrue(isKnownWordInLeet("2024!dr4g0n2024!", setOf("dragon")))
        assertFalse("a letter before the word", isKnownWordInLeet("xdr4g0n2024", setOf("dragon")))
        assertFalse("a letter after the word", isKnownWordInLeet("dr4g0n2024x", setOf("dragon")))
        assertFalse("no letters: left to the estimate", isKnownWordInLeet("7357735773577357", setOf("test", "t", "e", "s")))
        assertFalse("without the lists nothing is a word", isKnownWordInLeet("P4ssw0rd2024!", emptySet()))
    }

    @Test fun `RT-3 the leet check is bounded`() {
        assertTrue(isKnownWordInLeet("P4ssw0rd" + "!".repeat(LEET_MAX_CODE_POINTS - 8), allWords))
        assertFalse("past the bound it is not tried", isKnownWordInLeet("P4ssw0rd" + "!".repeat(LEET_MAX_CODE_POINTS - 7), allWords))
        // The worst case within the bound: one letter in the middle, as many cuts as possible on each side.
        val worst = "1".repeat(LEET_MAX_CODE_POINTS / 2 - 1) + "q" + "1".repeat(LEET_MAX_CODE_POINTS / 2)
        val start = System.nanoTime()
        repeat(20) { assertFalse(isKnownWordInLeet(worst, allWords)) }
        val ms = (System.nanoTime() - start) / 1_000_000
        assertTrue("$ms ms for 20 checks of the worst case", ms < 2_000)
    }

    @Test fun `RT-3 the accepted limits stay accepted`() {
        // The owner accepted these (CRY-A01): a word in no list, two words together, a Generator-style random password.
        for (c in listOf(
            "Barcelona1", "B4rc3l0n4!!", "abandon-ability", "4b4nd0n-4b1l1ty", "casa perro luz mar sol", "Xq7#vB2!mK",
            "Xq7#vB2!mK9\$wL4@pT1&", "k3#Tz9!Qm@4vRw7\$Lp2x",
        )) {
            assertNull("«$c»", newMasterProblem(c, allWords))
        }
    }

    @Test fun `without the lists newMasterProblem does what it did before`() {
        fun before(c: String) = when {
            c.length < MIN_MASTER -> MASTER_TOO_SHORT
            estimateBits(c) < WEAK_BITS -> MASTER_TOO_WEAK
            else -> null
        }
        for (c in listOf(
            "", "corta", "contraseña", "Contraseña", "contraseña123", "Contraseña2024", "Password1!", "iloveyou12", "abandon1234",
            "Ábaco12345", "1234567890", "aaaaaaaaaa", "qwertyuiop", "Barcelona1", "casa perro luz mar sol", "Xq7#vB2!mK",
        )) {
            assertEquals(c, before(c), newMasterProblem(c))
            assertEquals(c, before(c), newMasterProblem(c, emptySet()))
        }
    }

    @Test fun `the lists are read from assets, and a failure leaves the rule at the estimate`() {
        val opened = mutableListOf<String>()
        val closed = mutableListOf<String>()
        val loaded = loadMasterWords { name ->
            opened += name
            object : FileInputStream(assetFile(name)) {
                override fun close() { closed += name; super.close() }
            }
        }
        assertEquals(MASTER_WORD_LISTS, opened)
        assertEquals("every stream is closed", MASTER_WORD_LISTS, closed)
        assertEquals(allWords, loaded)
        assertTrue("${loaded.size} words: the word check is on with the repository's assets (F7)", loaded.size > 9000)

        // A list that cannot be read: nothing at all, never half of the lists.
        assertEquals(emptySet<String>(), loadMasterWords { name ->
            if (name == "bip39_english.txt") throw FileNotFoundException(name)
            ByteArrayInputStream("hello\nworld\n".toByteArray())
        })
        assertEquals(emptySet<String>(), loadMasterWords { throw IOException("no assets") })
        assertEquals("not a crash of the screen either", emptySet<String>(), loadMasterWords { throw IllegalStateException("closed") })
        assertNull("so the rule is the estimate: «contraseña123» passes", newMasterProblem("contraseña123", loadMasterWords { throw IOException() }))
    }

    // ---- Final review 1.3.1, F6: the lists are read once per process, off the main thread ----

    /** Opens the real assets, counting what it opens and closes and on which thread; [failing] makes it throw. */
    private inner class CountingAssets(var failing: Boolean = false) {
        val opened = mutableListOf<String>()
        val closed = mutableListOf<String>()
        val threads = mutableSetOf<String>()

        fun open(name: String): InputStream {
            opened += name
            // Without the suffix kotlinx adds to the name in debug mode.
            threads += Thread.currentThread().name.substringBefore(" @coroutine#")
            if (failing) throw IOException("no assets")
            return object : FileInputStream(assetFile(name)) {
                override fun close() { closed += name; super.close() }
            }
        }
    }

    @Test fun `F6 the lists are read once per process, on the dispatcher given, and every stream is closed`() {
        val io = Executors.newSingleThreadExecutor { Thread(it, "listas") }.asCoroutineDispatcher()
        try {
            val assets = CountingAssets()
            val lists = WordLists(assets::open, io)
            runBlocking {
                repeat(3) { assertEquals(allWords, lists.master()) }
                repeat(3) { assertEquals(asset(EFF_WORD_LIST), lists.eff()) }
            }
            // The EFF list twice: once among the master's lists, once for the Generator; never again after that.
            assertEquals(MASTER_WORD_LISTS + EFF_WORD_LIST, assets.opened)
            assertEquals(assets.opened, assets.closed)
            assertEquals("never the caller's thread", setOf("listas"), assets.threads)
        } finally {
            io.close()
        }
    }

    @Test fun `F6 a list that cannot be read is not kept, so the next screen reads it again`() = runBlocking {
        val assets = CountingAssets(failing = true)
        val lists = WordLists(assets::open, Dispatchers.Unconfined)
        assertEquals("the rule stays at the length and the estimate", emptySet<String>(), lists.master())
        assertNull("«Frase» stays off", lists.eff())
        assets.failing = false
        assertEquals(allWords, lists.master())
        assertEquals(asset(EFF_WORD_LIST), lists.eff())
        val read = assets.opened.size
        lists.master()
        lists.eff()
        assertEquals("kept once read", read, assets.opened.size)
    }

    // ---- What the Generator proposes passes ----

    private fun assetFile(name: String): File =
        // Run from app/ (Gradle Android tests) or from jvm-check/.
        listOf("src/main/assets", "../app/src/main/assets").map { File(it, name) }.first { it.exists() }

    private fun asset(name: String): List<String> = assetFile(name).readLines().filter { it.isNotBlank() }

    /** The three lists of assets/ the screens load (MASTER_WORD_LISTS), as the screens normalize them (masterWords). */
    private val allWords: Set<String> by lazy { masterWords(MASTER_WORD_LISTS.map(::asset)) }

    /** Accepted both by the estimate alone and with the word lists loaded (what the screens do). */
    private fun assertAccepted(message: String, candidate: String) {
        assertNull("$message (${estimateBits(candidate)} bits)", newMasterProblem(candidate))
        assertNull("$message, with the word lists", newMasterProblem(candidate, allWords))
    }

    /** The separators the Generator screen offers (GeneratorScreen): guion, espacio, punto, barra baja. */
    private val separators = listOf("-", " ", ".", "_")

    @Test fun `a phrase of 5 words of the Generator passes, in both lists and with every separator`() {
        val random = SecureRandom()
        for (listName in listOf("eff_large_wordlist.txt", "bip39_spanish.txt", "bip39_english.txt")) {
            val words = asset(listName)
            for (sep in separators) {
                repeat(200) {
                    val phrase = generatePassphrase(words, PassphraseOptions(words = 5, separator = sep), random)
                    assertAccepted("$listName «$sep»: «$phrase»", phrase)
                }
            }
        }
    }

    @Test fun `a phrase of the Generator passes also with capitals and a number`() {
        val random = SecureRandom()
        for (listName in listOf("eff_large_wordlist.txt", "bip39_spanish.txt")) {
            val words = asset(listName)
            for (sep in separators) for (capitalize in listOf(false, true)) for (addNumber in listOf(false, true)) {
                repeat(50) {
                    val phrase = generatePassphrase(words, PassphraseOptions(5, sep, capitalize, addNumber), random)
                    assertAccepted("$listName «$sep»: «$phrase»", phrase)
                }
            }
        }
    }

    /**
     * A fixed sequence (SHA1PRNG with a seed), not SecureRandom(): with the word lists loaded, about 1 password in 54,000 of
     * 16 characters with the four classes is refused (1 in 3,100 at 12, 1 in 1.1 million at 20; measured with the leet
     * check of RT-3, see the KDoc of newMasterProblem), because its few letters (2 to 5), as they are or in leet, happen
     * to spell a list word and the rest are digits and symbols (see the next test). 200 fresh draws would fail about one
     * run in 270 by chance.
     */
    private fun fixedRandom(): SecureRandom = SecureRandom.getInstance("SHA1PRNG").apply { setSeed(20261101L) }

    @Test fun `a password of 16 characters of the Generator passes`() {
        val random = fixedRandom()
        repeat(200) {
            val password = generatePassword(PasswordOptions(length = 16), random)!!
            assertAccepted("«$password»", password)
        }
        // Without symbols (letters and digits), and letters only: still above the threshold at 16 characters.
        for (options in listOf(PasswordOptions(length = 16, symbols = false), PasswordOptions(length = 16, digits = false, symbols = false))) {
            repeat(200) {
                val password = generatePassword(options, random)!!
                assertAccepted("«$password»", password)
            }
        }
    }

    /** The price of the rule, pinned: a long random password with only a few letters, and they spell a word, is refused. */
    @Test fun `a random password whose only letters spell a list word is refused, and Otra gives another`() {
        val password = "!7#Act4\$9&2@1"
        assertTrue("${estimateBits(password)} bits", estimateBits(password) >= WEAK_BITS)
        assertNull("the estimate alone accepts it", newMasterProblem(password))
        assertEquals(MASTER_TOO_WEAK, newMasterProblem(password, allWords))
    }

    // ---- Where the rule is, and where it is not (source text: ui/ is not compiled on the JVM) ----

    private fun screens(): List<File> {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val f = File(dir, "app/src/main/java/app/seguridad/ui/screens")
            if (f.isDirectory) return f.listFiles { x -> x.isFile && x.extension == "kt" }!!.sortedBy { it.name }
            dir = dir.parentFile
        }
        error("app/src/main/java/app/seguridad/ui/screens not found")
    }

    /** Code only: a comment that names the call must not make the check pass. */
    private fun code(f: File) = f.readLines().filterNot { it.trimStart().let { l -> l.startsWith("//") || l.startsWith("*") || l.startsWith("/*") } }.joinToString("\n")

    private fun screen(name: String) = code(screens().first { it.name == name })

    /** A file of the repository, from app/ or jvm-check/. */
    private fun projectFile(path: String): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            File(dir, path).takeIf { it.isFile }?.let { return it }
            dir = dir.parentFile
        }
        error("$path not found")
    }

    /** The arguments of `"<route>" -> PasswordDialog(...)`, up to the trailing lambda `) { pw ->`. */
    private fun dialogArguments(route: String): String {
        val m = Regex("""\"$route\" -> PasswordDialog\((.*?)\) \{ pw ->""", RegexOption.DOT_MATCHES_ALL).find(screen("VaultBackup.kt"))
        assertNotNull("PasswordDialog of «$route» not found", m)
        return m!!.groupValues[1]
    }

    @Test fun `creating the vault and changing the master call newMasterProblem`() {
        assertTrue(Regex("""newMasterProblem\(""").containsMatchIn(screen("VaultScreen.kt")))
        assertTrue(Regex("""newMasterProblem\(""").containsMatchIn(screen("VaultBackup.kt")))
        // The create button waits for the rule: no problem, not only «long enough».
        assertTrue("problem == null" in screen("VaultScreen.kt"))
    }

    /**
     * The two screens load the lists (once, off the main thread) and give them to newMasterProblem; the button of creating or
     * changing waits for them. The import dialog neither loads them nor waits.
     */
    @Test fun `the two screens pass the loaded words to newMasterProblem and wait for them`() {
        for (name in listOf("VaultScreen.kt", "VaultBackup.kt")) {
            val src = screen(name)
            assertEquals("$name: one call of newMasterProblem", 1, Regex("""newMasterProblem\(""").findAll(src).count())
            assertTrue("$name passes the loaded words", Regex("""newMasterProblem\(a, words\b""").containsMatchIn(src))
        }
        val create = screen("VaultScreen.kt")
        assertTrue("CreateVault loads the words", "val words = rememberMasterWords()" in create)
        val button = create.lines().single { it.trim().startsWith("enabled = ") && "problem == null" in it }
        assertTrue(button, "words != null" in button)

        val dialog = screen("VaultBackup.kt").let { it.substring(it.indexOf("fun PasswordDialog(")) }
        // Only the dialog of a new master loads them; the import one does not even read the assets.
        assertTrue("val words = if (newPassword) rememberMasterWords() else null" in dialog)
        assertEquals(1, Regex("""rememberMasterWords\(""").findAll(dialog).count())
        val ok = dialog.lines().single { it.trim().startsWith("val ok = ") }
        assertTrue(ok, "if (newPassword) words != null && problem == null else a.length >= MIN_MASTER" in ok)
    }

    /**
     * The loader of the screens (F6): the process' [WordLists] (AppContainer, off the main thread, read once), waited
     * for in each screen, never saved; what it reads is MASTER_WORD_LISTS.
     */
    @Test fun `the words are loaded once per process, off the main thread, and never saved`() {
        val src = screen("MasterWords.kt")
        assertTrue("fun rememberMasterWords(" in src)
        val processLists = "(LocalContext.current.applicationContext as SeguridadApp).container.wordLists"
        assertTrue("the process' lists", processLists in src)
        assertTrue("each screen waits for them", "LaunchedEffect(Unit) { words = lists.master() }" in src)
        assertFalse("the screen never reads the lists itself", Regex("""loadMasterWords\s*[({]""").containsMatchIn(src))
        assertTrue("null until loaded", "remember { mutableStateOf<Set<String>?>(null) }" in src)
        val container = projectFile("app/src/main/java/app/seguridad/AppContainer.kt").readText()
        assertTrue("one per process, Dispatchers.IO", "val wordLists = WordLists(openAsset)" in container)
        val app = projectFile("app/src/main/java/app/seguridad/SeguridadApp.kt").readText()
        assertTrue("from the APK's assets", "openAsset = { name -> assets.open(name) }" in app)
        assertFalse("rememberSaveable" in src)
        assertFalse("Bundle" in src)
        // A read that fails is loadMasterWords' emptySet(): the state is set either way, so the button is never left waiting.
        assertFalse("catch" in src)
        // The lists live in one place: the screens do not name them again.
        assertTrue(screens().filter { it.name != "SecurityToolsScreen.kt" && it.name != "CodesScreen.kt" }.none { Regex("""eff_large_wordlist|bip39_""").containsMatchIn(code(it)) })
    }

    /** Final review aplazado 1 and A-2, F6: the Generator's EFF list, off the main thread, closed, once per process. */
    @Test fun `the Generator reads the EFF list off the main thread and Frase waits for it`() {
        val gen = screen("SecurityToolsScreen.kt").substringAfter("fun GeneratorScreen(")
            .substringBefore("private fun Option(")
        assertTrue(gen, "val words by produceState<List<String>?>(null) { value = lists.eff() }" in gen)
        assertTrue(gen, Regex("""onClick = \{ phrase = true \},\s*enabled = words != null""").containsMatchIn(gen))
        assertFalse(gen, "assets.open(" in gen || "eff_large_wordlist" in gen)
    }

    @Test fun `the master dialog asks for a new password and the import dialog does not`() {
        assertTrue("newPassword = true" in dialogArguments("master"))
        assertTrue("newPassword" !in dialogArguments("import"))
    }

    /** The two new red texts are a polite live region, so TalkBack announces them; the old «No coinciden» does not change. */
    @Test fun `the problem of a new master is announced by TalkBack and No coinciden is left as it was`() {
        for (name in listOf("VaultScreen.kt", "VaultBackup.kt")) {
            val src = screen(name)
            val problemLine = src.lines().single { "problem?.let" in it }
            assertTrue("$name: $problemLine", "Modifier.semantics { liveRegion = LiveRegionMode.Polite }" in problemLine)
            assertEquals("$name: only the problem is a live region", 1, Regex("""liveRegion\s*=""").findAll(src).count())
            assertTrue(src.lines().filter { "No coinciden" in it }.none { "liveRegion" in it })
        }
    }

    /** MASTER_TOO_WEAK already says «usa una frase de 5 o 6 palabras»: the fixed hint below the field waits for it to go. */
    @Test fun `the hint of a phrase is not shown together with the same advice`() {
        val src = screen("VaultScreen.kt")
        assertTrue(Regex("""if \(\w+ != MASTER_TOO_WEAK\)\s*\{\s*Text\(\s*"Consejo: usa una frase""").containsMatchIn(src))
        assertEquals("the hint is shown once", 1, Regex("""Consejo: usa una frase""").findAll(src).count())
    }

    @Test fun `no screen but the dialog itself checks only the length of a master`() {
        val lengthOnly = Regex("""\blength\s*>=\s*MIN_MASTER""")
        val hits = screens().flatMap { f -> lengthOnly.findAll(code(f)).map { f.name }.toList() }
        // The password of an existing copy (CRY-A01: never a new master) keeps the length check: the import's dialog and
        // «Comprobar una copia» (1.4.0), which asks for the same password.
        assertEquals("only PasswordDialog (the import) and the copy check keep the length check: $hits", listOf("BackupCheckScreen.kt", "VaultBackup.kt"), hits.sorted())
        assertEquals(1, lengthOnly.findAll(screen("BackupCheckScreen.kt")).count())
        assertTrue("session.password.length >= MIN_MASTER" in screen("BackupCheckScreen.kt"))
        val backup = screen("VaultBackup.kt")
        val dialog = backup.substring(backup.indexOf("fun PasswordDialog("))
        assertEquals(1, lengthOnly.findAll(dialog).count())
        assertEquals("the length check is gone from the rest of the file", 0, lengthOnly.findAll(backup.substring(0, backup.indexOf("fun PasswordDialog("))).count())
    }

    @Test fun `MIN_MASTER has one definition, in vault`() {
        assertTrue(screens().none { Regex("""const val MIN_MASTER\b""").containsMatchIn(code(it)) })
        for (name in listOf("VaultScreen.kt", "VaultBackup.kt")) assertTrue("$name imports MIN_MASTER", "import app.seguridad.vault.MIN_MASTER" in screen(name))
    }

    /**
     * Branch review B, M-3: «Comprobar una copia» and «Importar de otra app» share one cap for a pasted password, far
     * past any master a person types or pastes from a manager (creating the vault, changing the master and «Importar
     * copia» have none), so a check takes the masters an import takes; a paste over it is refused, never cut.
     */
    @Test fun thePasswordFieldsShareOneCapFarPastAnyMaster() {
        assertEquals(65_536, PASSWORD_FIELD_MAX_CHARS)
        assertTrue(passwordFieldTakes(""))
        assertTrue(passwordFieldTakes("a".repeat(1500))) // The reviewer's case: a 1 500-character master is checked now.
        assertTrue(passwordFieldTakes("x".repeat(PASSWORD_FIELD_MAX_CHARS)))
        assertFalse(passwordFieldTakes("x".repeat(PASSWORD_FIELD_MAX_CHARS + 1)))
        // Counted in UTF-16 units, as the text field counts them: an emoji is two.
        assertFalse(passwordFieldTakes("😀".repeat(PASSWORD_FIELD_MAX_CHARS / 2) + "x"))
        // Both screens use it, and neither keeps a cap of its own.
        for (screen in listOf("BackupCheckScreen.kt", "ImportScreen.kt")) {
            val src = projectFile("app/src/main/java/app/seguridad/ui/screens/$screen").readText()
            assertTrue(screen, "refused = !passwordFieldTakes(value)" in src)
            assertFalse(screen, Regex("""MAX_(FILE_)?PASSWORD_CHARS""").containsMatchIn(src))
        }
    }
}
