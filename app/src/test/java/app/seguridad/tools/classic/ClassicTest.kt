package app.seguridad.tools.classic

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ClassicTest {
    private fun settings(
        rotors: List<Rotor> = listOf(Rotor.I, Rotor.II, Rotor.III),
        reflector: Reflector = Reflector.B,
        rings: List<Int> = listOf(1, 1, 1),
        positions: String = "AAA",
        plugs: List<Pair<Char, Char>> = emptyList(),
    ) = EnigmaSettings(rotors, reflector, rings, positions, plugs)

    @Test fun enigmaAaaaa() {
        assertEquals("BDZGO", Enigma(settings()).process("AAAAA"))
    }

    @Test fun doubleStep() {
        val e = Enigma(settings(positions = "ADU"))
        val seen = (1..3).map { e.press('A'); e.positions }
        assertEquals(listOf("ADV", "AEW", "BFX"), seen)
    }

    @Test fun enigmaIsReciprocal() {
        val rnd = Random(20260311)
        repeat(200) {
            val rotors = Rotor.entries.shuffled(rnd).take(3)
            val letters = ('A'..'Z').shuffled(rnd)
            val plugs = (0 until rnd.nextInt(14)).map { letters[2 * it] to letters[2 * it + 1] }
            val s = settings(
                rotors, Reflector.entries.random(rnd), List(3) { rnd.nextInt(1, 27) },
                String(CharArray(3) { 'A' + rnd.nextInt(26) }), plugs,
            )
            val text = String(CharArray(rnd.nextInt(1, 600)) { 'A' + rnd.nextInt(26) })
            val once = Enigma(s).process(text)
            assertEquals(text, Enigma(s).process(once))
        }
    }

    @Test fun noLetterEncryptsToItself() {
        val rnd = Random(7)
        repeat(50) {
            val s = settings(Rotor.entries.shuffled(rnd).take(3), Reflector.entries.random(rnd), List(3) { rnd.nextInt(1, 27) }, "QEV", listOf('A' to 'Z', 'M' to 'N'))
            val e = Enigma(s)
            repeat(2000) {
                val c = 'A' + rnd.nextInt(26)
                assertNotEquals(c, e.press(c))
            }
        }
    }

    /**
     * Operation Barbarossa, 7 July 1941, from the Russian front (two parts of one message). Published, for example, in
     * https://github.com/pgrodrigues/node-enigma-m3/blob/main/README.md (section «Operation Barbarossa, 1941») from the
     * Crypto Museum / Enigma literature: Enigma I, reflector B, rotors II IV V, rings B U L (2 21 12), plugs
     * AV BS CG DL FU HZ IN KM OW RX. What is tested: the BODY decrypted at its message key (part 1 BLA, part 2 LSD) and
     * the INDICATOR groups (WXC: KCH -> BLA, CRS: YPJ -> LSD). The README gives the settings, the two bodies and their
     * plaintext; it does not give the indicators. I read no other source for this message: the indicators were checked by
     * decryption (they give the message keys, and the keys give the German text), not taken from a cited publication.
     */
    @Test fun historicalMessage() {
        val plugs = "AV BS CG DL FU HZ IN KM OW RX".split(' ').map { it[0] to it[1] }
        fun machine(key: String) = Enigma(settings(listOf(Rotor.II, Rotor.IV, Rotor.V), Reflector.B, listOf(2, 21, 12), key, plugs))
        val cipher1 = "EDPUD NRGYS ZRCXN UYTPO MRMBO FKTBZ REZKM LXLVE FGUEY SIOZV EQMIK UBPMM YLKLT TDEIS MDICA GYKUA CTCDO MOHWX MUUIA UBSTS LRNBZ SZWNR FXWFY SSXJZ VIJHI DISHP RKLKA YUPAD TXQSP INQMA TLPIF SVKDA SCTAC DPBOP VHJK"
        val plain1 = "AUFKL XABTE ILUNG XVONX KURTI NOWAX KURTI NOWAX NORDW ESTLX SEBEZ XSEBE ZXUAF FLIEG ERSTR ASZER IQTUN GXDUB ROWKI XDUBR OWKIX OPOTS CHKAX OPOTS CHKAX UMXEI NSAQT DREIN ULLXU HRANG ETRET ENXAN GRIFF XINFX RGTX"
        assertEquals(plain1, machine("BLA").process(cipher1))
        assertEquals(cipher1, machine("BLA").process(plain1))
        val cipher2 = "SFBWD NJUSE GQOBH KRTAR EEZMW KPPRB XOHDR OEQGB BGTQV PGVKB VVGBI MHUSZ YDAJQ IROAX SSSNR EHYGG RPISE ZBOVM QIEMM ZCYSG QDGRE RVBIL EKXYQ IRGIR QNRDN VRXCY YTNJR"
        val plain2 = "DREIG EHTLA NGSAM ABERS IQERV ORWAE RTSXE INSSI EBENN ULLSE QSXUH RXROE MXEIN SXINF RGTXD REIXA UFFLI EGERS TRASZ EMITA NFANG XEINS SEQSX KMXKM XOSTW XKAME NECXK"
        assertEquals(plain2, machine("LSD").process(cipher2))
        // The indicator: the message key, encrypted at the start position of the day (Grundstellung).
        assertEquals("BLA", machine("WXC").process("KCH"))
        assertEquals("LSD", machine("CRS").process("YPJ"))
    }

    @Test fun nonLettersPassThrough() {
        val a = Enigma(settings())
        assertEquals("ñ é 7 ", a.process("ñ é 7 "))
        assertEquals("AAA", a.positions) // no step
        // The same letters with extra characters between give the same letters.
        assertEquals(Enigma(settings()).process("HOLA"), Enigma(settings()).process("H ñ O é L 1 A").filter { it in 'A'..'Z' })
    }

    @Test fun lowercaseIsReadAsUppercaseAndComesOutUppercase() {
        assertEquals("BDZGO", Enigma(settings()).process("aaaaa"))
        assertEquals(Enigma(settings()).process("HOLA"), Enigma(settings()).process("hOlA"))
    }

    @Test fun ringChangesTheOutput() {
        assertNotEquals(Enigma(settings()).process("AAAAA"), Enigma(settings(rings = listOf(1, 1, 2))).process("AAAAA"))
        // Known vector: ring settings 01 01 02 and start AAA (rotors I II III, reflector B).
        assertEquals("UBDZG", Enigma(settings(rings = listOf(1, 1, 2))).process("AAAAA"))
    }

    @Test fun reflectorCAndPlugsAreUsed() {
        val b = Enigma(settings()).process("HELLO")
        assertNotEquals(b, Enigma(settings(reflector = Reflector.C)).process("HELLO"))
        assertNotEquals(b, Enigma(settings(plugs = listOf('H' to 'E'))).process("HELLO"))
    }

    @Test fun caesarRoundTrip() {
        val text = "Ataque al amanecer, ñandú ÉPICO 123 xyz XYZ"
        for (k in -30..60) assertEquals(text, caesar(caesar(text, k), -k))
        assertEquals("DWDTXH", caesar("ATAQUE", 3))
        assertEquals("ñ é", caesar("ñ é", 5))
        assertEquals("abc", caesar("xyz", 3))
        assertEquals("ABC", caesar("XYZ", 29))
    }

    @Test fun vigenereKnown() {
        assertEquals("LXFOPVEFRNHR", vigenere("ATTACKATDAWN", "LEMON", false))
        assertEquals("ATTACKATDAWN", vigenere("LXFOPVEFRNHR", "LEMON", true))
        // Non-letters do not use up a key letter, case is kept, lowercase key works.
        assertEquals("Lx fo-ñ", vigenere("At ta-ñ", "lemon", false))
        assertEquals("At ta-ñ", vigenere("Lx fo-ñ", "LeMoN", true))
        assertThrows(IllegalArgumentException::class.java) { vigenere("A", "123 ñ", false) }
    }

    @Test fun icOfEnglishSample() {
        val text = "Call me Ishmael. Some years ago, never mind how long precisely, having little or no money in my purse, " +
            "and nothing particular to interest me on shore, I thought I would sail about a little and see the watery part of " +
            "the world. It is a way I have of driving off the spleen and regulating the circulation. Whenever I find myself " +
            "growing grim about the mouth, whenever it is a damp, drizzly November in my soul, whenever I find myself " +
            "involuntarily pausing before coffin warehouses, and bringing up the rear of every funeral I meet, then I account " +
            "it high time to get to sea as soon as I can."
        assertEquals(0.066, indexOfCoincidence(text), 0.01)
    }

    @Test fun icOfUniformText() {
        assertEquals(0.038, indexOfCoincidence(("ABCDEFGHIJKLMNOPQRSTUVWXYZ").repeat(100)), 0.001)
        assertEquals(0.0, indexOfCoincidence(""), 0.0)
        assertEquals(0.0, indexOfCoincidence("A"), 0.0)
        assertEquals(1.0, indexOfCoincidence("aAaA"), 0.0)
    }

    @Test fun letterCountsFoldCaseAndIgnoreTheRest() {
        val c = letterCounts("aAbñé1 Z")
        assertEquals(26, c.size)
        assertEquals(2, c[0]); assertEquals(1, c[1]); assertEquals(1, c[25]); assertEquals(4, c.sum())
    }

    @Test fun settingsValidation() {
        assertThrows(IllegalArgumentException::class.java) { settings(rotors = listOf(Rotor.I, Rotor.I, Rotor.III)) }
        assertThrows(IllegalArgumentException::class.java) { settings(rotors = listOf(Rotor.I, Rotor.II)) }
        assertThrows(IllegalArgumentException::class.java) { settings(plugs = listOf('A' to 'B', 'B' to 'C')) }
        assertThrows(IllegalArgumentException::class.java) { settings(plugs = listOf('A' to 'A')) }
        // A plug joins two different letters, and both must be A-Z.
        assertThrows(IllegalArgumentException::class.java) { settings(plugs = listOf('A' to 'A')) }
        assertThrows(IllegalArgumentException::class.java) { settings(plugs = listOf('A' to 'ñ')) }
        assertThrows(IllegalArgumentException::class.java) { settings(plugs = listOf('a' to 'b')) }
        // 14 plugs: 26 letters cannot make 14 disjoint pairs, so the size rule is shown with a list that breaks only it first.
        assertThrows(IllegalArgumentException::class.java) { settings(plugs = List(14) { 'A' to 'B' }) }
        assertThrows(IllegalArgumentException::class.java) { settings(rings = listOf(0, 1, 1)) }
        assertThrows(IllegalArgumentException::class.java) { settings(rings = listOf(1, 27, 1)) }
        assertThrows(IllegalArgumentException::class.java) { settings(positions = "AB") }
        assertThrows(IllegalArgumentException::class.java) { settings(positions = "ab1") }
        // 13 disjoint plugs are fine.
        assertTrue(settings(plugs = (0 until 13).map { ('A' + it) to ('N' + it) }).plugs.size == 13)
    }

    @Test fun formBuildsOrSaysWhy() {
        val ok = buildEnigma(listOf(Rotor.I, Rotor.II, Rotor.III), Reflector.B, " 1, 1 1", "aaa", "av bs, cg")
        assertEquals(null, ok.error)
        assertEquals(listOf('A' to 'V', 'B' to 'S', 'C' to 'G'), ok.settings!!.plugs)
        assertEquals("AAA", ok.settings!!.positions)
        val r = listOf(Rotor.I, Rotor.II, Rotor.III)
        assertEquals(ENIGMA_REPEATED_ROTOR, buildEnigma(listOf(Rotor.I, Rotor.I, Rotor.III), Reflector.B, "1 1 1", "AAA", "").error)
        assertEquals(ENIGMA_RINGS_INVALID, buildEnigma(r, Reflector.B, "1 1", "AAA", "").error)
        assertEquals(ENIGMA_RINGS_INVALID, buildEnigma(r, Reflector.B, "1 27 1", "AAA", "").error)
        assertEquals(ENIGMA_RINGS_INVALID, buildEnigma(r, Reflector.B, "a b c", "AAA", "").error)
        assertEquals(ENIGMA_POSITIONS_INVALID, buildEnigma(r, Reflector.B, "1 1 1", "AÑA", "").error)
        assertEquals(ENIGMA_PLUG_FORMAT, buildEnigma(r, Reflector.B, "1 1 1", "AAA", "ABC").error)
        assertEquals(ENIGMA_PLUG_FORMAT, buildEnigma(r, Reflector.B, "1 1 1", "AAA", "AA").error)
        assertEquals(ENIGMA_PLUG_REPEATED, buildEnigma(r, Reflector.B, "1 1 1", "AAA", "AB BC").error)
        assertEquals(ENIGMA_PLUG_TOO_MANY, buildEnigma(r, Reflector.B, "1 1 1", "AAA", List(14) { "AB" }.joinToString(" ")).error)
        assertEquals(null, buildEnigma(r, Reflector.B, "1 1 1", "AAA", "").error)
        // M-1: only ASCII letters and digits. «ﬁ» (U+FB01) must not become the plug F-I, «ß» must not become SS, and
        // Arabic-Indic digits are not rings.
        assertEquals(ENIGMA_PLUG_FORMAT, buildEnigma(r, Reflector.B, "1 1 1", "AAA", "\uFB01").error)
        assertEquals(ENIGMA_POSITIONS_INVALID, buildEnigma(r, Reflector.B, "1 1 1", "A\u00DF", "").error)
        assertEquals(ENIGMA_RINGS_INVALID, buildEnigma(r, Reflector.B, "\u0661 \u0661 \u0661", "AAA", "").error)
        assertEquals(ENIGMA_RINGS_INVALID, buildEnigma(r, Reflector.B, "001 1 1", "AAA", "").error)
    }

    @Test fun decomposedAccentPassesLikeThePrecomposedOne() {
        val nfd = "e\u0301" // «é» as a base letter and a combining acute
        val nfc = "\u00E9"
        assertEquals(nfc, caesar(nfd, 3))
        assertEquals(nfc, vigenere(nfd, "LEMON", false))
        val e = Enigma(settings())
        assertEquals(nfc + "BDZGO", e.process(nfd + "AAAAA"))
        val f = Enigma(settings())
        assertEquals(nfc, f.process(nfd))
        assertEquals("AAA", f.positions) // no step
        assertEquals(1, letterCounts("e\u0301e")[4]) // the accented «é» is not an «e»
    }

    @Test fun enigmaIntroGivesTheReflectorAsTheReasonNoLetterMapsToItself() {
        assertTrue("El reflector devuelve la corriente por otro camino, y por eso una letra nunca sale como ella misma" in ENIGMA_INTRO.replace("  ", " "))
        assertTrue("y por eso una letra nunca sale como ella misma" !in ENIGMA_INTRO.substringBefore("El reflector"))
    }

    @Test fun sessionWipeEmptiesWhatWasTyped() {
        val s = ClassicLabSession()
        s.rotors = listOf(Rotor.V, Rotor.IV, Rotor.I); s.reflector = Reflector.C
        s.caesarText = "a"; s.shift = "9"; s.vigenereText = "b"; s.vigenereKey = "KEY"; s.enigmaText = "c"
        s.rings = "5 5 5"; s.positions = "XYZ"; s.plugs = "AB"; s.analysisText = "d"
        s.wipe()
        assertEquals(listOf("", DEFAULT_SHIFT, "", "", ""), listOf(s.caesarText, s.shift, s.vigenereText, s.vigenereKey, s.enigmaText))
        assertEquals(listOf(DEFAULT_RINGS, DEFAULT_POSITIONS, "", ""), listOf(s.rings, s.positions, s.plugs, s.analysisText))
        assertEquals(listOf(Rotor.I, Rotor.II, Rotor.III), s.rotors)
        assertEquals(Reflector.B, s.reflector)
    }
}
