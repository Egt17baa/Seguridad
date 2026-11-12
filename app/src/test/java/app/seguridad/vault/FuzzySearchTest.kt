package app.seguridad.vault

import app.seguridad.crypto.KdfParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** 2.0, tarea 4: la búsqueda que perdona erratas (diseño «Búsqueda que perdona erratas»). */
class FuzzySearchTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun s(q: String, t: String) = FuzzySearch.score(q, t)

    @Test fun exactBeforeTypo() {
        val exact = s("gmail", "Gmail")!!
        val prefix = s("gma", "Gmail personal")!!
        val inside = s("mail", "Gmail")!!
        val typo = s("gmial", "Gmail")!!
        assertTrue(exact < prefix && prefix < inside)
        assertTrue("$inside < $typo", inside < typo)
        // Several words found exactly, in any order, before any typo.
        val words = s("personal gmail", "Gmail personal")!!
        assertTrue(words < typo)
        assertTrue(s("persnal gmail", "Gmail personal")!! > words)
        // In a list of texts (the fields of one entry), the best of them.
        val q = FuzzySearch.prepare("banco")
        assertEquals(s("banco", "Banco"), q.score(listOf("Banco", "Otra cosa").map(FuzzySearch::normalize)))
        // An earlier text (the title before the user) wins a tie, but never over a better kind of match.
        assertTrue(q.score(listOf("Otra cosa", "Banco").map(FuzzySearch::normalize))!! < s("banco", "Mi banco")!!)
        assertEquals(0, s("", "lo que sea"))
    }

    @Test fun oneTypoInFiveLetters() {
        assertNotNull(s("gmial", "Gmail")) // A transposition.
        assertNotNull(s("gmaul", "gmail")) // A substitution.
        assertNotNull(s("gmal", "gmail")) // A missing letter (4 letters).
        assertNotNull(s("gmaill", "gmail.com")) // One more.
        assertNull(s("gmiul", "gmail")) // Two typos in 5 letters: no.
        // While typing, the start of a longer word counts too.
        assertNotNull(s("amazn", "Amazon Prime"))
    }

    @Test fun twoTyposFromEight() {
        assertNotNull(s("contrasena", "contraseña")) // Exact once accents go.
        assertNotNull(s("facturacoin", "Facturación")) // 11 letters, a transposition.
        assertNotNull(s("fcturacoin", "Facturación")) // Two typos.
        assertNull(s("fcturcoin", "Facturación")) // Three: no.
        assertNotNull(s("bnaco", "banco"))
        assertNull(s("bnacp", "banco"))
        // 6 letters: one.
        assertNotNull(s("netflx", "Netflix"))
        assertNull(s("netlfx", "Netflix"))
    }

    /** Review M-3: the exact edges of [FuzzySearch.allowedEdits]: 3, 4, 7 and 8 letters. */
    @Test fun typoLimitsAtTheEdges() {
        assertEquals(listOf(0, 0, 0, 1, 1, 1, 1, 2, 2), (1..9).map(FuzzySearch::allowedEdits))
        assertNull(s("bvv", "bbv")) // 3 letters, one typo: no.
        assertNotNull(s("bnco", "banco")) // 4 letters, one typo (a letter missing).
        assertNull(s("entflxi", "Netflix")) // 7 letters, two transpositions: no.
        assertNotNull(s("entflix", "Netflix")) // 7 letters, one.
        assertNotNull(s("afctuars", "facturas")) // 8 letters, two transpositions: yes.
        assertNull(s("afctuasr", "facturas")) // 8 letters, three: no.
    }

    /**
     * Ruling T4, M-7: the start of a word counts while typing, with one typo at most whatever the length, and after
     * every match of whole words.
     */
    @Test fun startOfAWordOneTypoAndLast() {
        assertNotNull(s("gmia", "Gmail")) // «gmai» at one transposition.
        assertNull(s("contrato", "Contraseña del banco")) // Two from «contras»: never.
        val whole = s("gmial", "Gmail")!!
        val start = s("gmia", "Gmail")!!
        assertTrue("$whole < $start", whole < start && start >= FuzzySearch.PREFIX_BASE)
        // A whole word with two typos still comes before the start of one with one.
        assertTrue(s("fcturacoin", "Facturación")!! < start)
    }

    @Test fun shortWordsExactOnly() {
        assertNotNull(s("bbv", "BBVA"))
        assertNull(s("bvv", "BBVA"))
        assertNull(s("ig", "gi"))
        assertNotNull(s("ana", "Ana López"))
        assertNull(s("ama", "Ana López"))
    }

    @Test fun accentsIgnored() {
        assertNotNull(s("canon", "Cañón"))
        assertNotNull(s("CAÑÓN", "canon"))
        assertNotNull(s("electronica", "Electrónica"))
        assertNotNull(s("ｂａｎｃｏ", "banco")) // Full width, as compareKey folds it.
        assertNotNull(s("ban​co", "banco")) // What draws nothing does not count.
        assertEquals(s("cafe", "café"), s("café", "cafe"))
    }

    @Test fun hostileInputsDoNotThrow() {
        for (q in listOf("\uD800", "\u0000", "a".repeat(5_000), "🏦", " ")) {
            FuzzySearch.score(q, "Banco " + "b".repeat(5_000))
        }
        assertNull(s("x".repeat(200), "x".repeat(199) + "y" + "z"))
    }

    private fun entry(i: Int) = VaultEntry(
        id = "e$i", type = if (i % 5 == 0) EntryType.NOTE else EntryType.LOGIN, title = "Servicio número $i ${listOf("Banco", "Correo", "Tienda", "Foro")[i % 4]}",
        username = "usuario$i@ejemplo.es", password = "clave-secreta-$i", url = "https://sitio$i.ejemplo.es/entrar",
        notes = "notas de la entrada $i", createdAt = i.toLong(), updatedAt = i.toLong(), tags = listOf("etiqueta${i % 30}"),
        folderId = "f${i % 20}", lastUsedAt = if (i % 3 == 0) i.toLong() else null,
    )

    /**
     * 2 000 entries, on the JVM, as a reference (the phone's number is checked by hand): one search with the index of the
     * list built, as each key typed runs it; the index is built once per change of the vault. Best of several runs, after
     * warming up.
     */
    @Test fun twoThousandEntriesUnder50ms() {
        val entries = (0 until 2_000).map(::entry)
        val data = VaultData(entries = entries, folders = (0 until 20).map { Folder("f$it", "Carpeta $it") })
        val queries = listOf("banco", "corero", "usuario1999", "ejemplo", "etiqueta7", "carpeta 3", "tienad foro")
        val index = SearchIndex.build(entries, data.folders)
        fun once(q: String) = entries.query(EntryFilter(text = q), data, now = 0, weak = { false }, index = index)
        repeat(30) { queries.forEach(::once) }
        var best = Long.MAX_VALUE
        repeat(10) {
            val t0 = System.nanoTime()
            once("tienad")
            best = minOf(best, System.nanoTime() - t0)
        }
        var buildBest = Long.MAX_VALUE
        repeat(5) {
            val t0 = System.nanoTime()
            SearchIndex.build(entries, data.folders)
            buildBest = minOf(buildBest, System.nanoTime() - t0)
        }
        // After a save every entry is a new object: the next index takes the normalized texts from the one before.
        val edited = entries.map { it.copy() }.toMutableList().also { it[7] = it[7].copy(title = "Título nuevo") }
        var rebuildBest = Long.MAX_VALUE
        repeat(5) {
            val t0 = System.nanoTime()
            SearchIndex.build(edited, data.folders, previous = index)
            rebuildBest = minOf(rebuildBest, System.nanoTime() - t0)
        }
        val rebuilt = SearchIndex.build(edited, data.folders, previous = index)
        val editedData = data.copy(entries = edited)
        assertEquals(listOf("e7"), edited.query(EntryFilter(text = "titulo nuevo"), editedData, 0, { false }, index = rebuilt).map { it.id })
        println("FuzzySearch: 2 000 entradas, búsqueda con erratas ${best / 1_000} µs; índice ${buildBest / 1_000} µs; tras guardar ${rebuildBest / 1_000} µs")
        assertTrue("${best / 1_000_000} ms", best < 50_000_000)
        assertTrue(once("tienad").isNotEmpty())
        assertTrue(once("clave-secreta").isEmpty()) // Never the passwords.
    }

    /**
     * Review M-6 of task 1: an `upsert` on a vault near the cap of 24 MiB, measured on the JVM as a reference for the
     * report (a generous bound, not a hard number that could be flaky): it fits, checks the limits, encrypts and writes.
     */
    @Test fun upsertNearTheCapReference() = runTest {
        val clock = 1_800_000_000_000L
        val store = VaultStore(
            File(tmp.root, "vault.bin"), Dispatchers.Unconfined, Dispatchers.Unconfined, now = { clock }, kdf = { KdfParams(1024, 1, 1) },
            elapsed = { clock }, clockHighWater = { clock }, trashClockMark = { clock },
        )
        store.load()
        store.create("maestra-de-prueba-rendimiento".toCharArray())
        // About 23 MiB of notes in 2 000 entries of ~11.5 KiB each (each within 64 KiB), added in one save.
        val big = (0 until 2_000).map { i ->
            VaultEntry("g$i", EntryType.NOTE, "Nota grande $i", notes = "n".repeat(11_800), createdAt = 0, updatedAt = 0)
        }
        assertTrue(store.addAll(big))
        val size = jsonBytes((store.state.value as VaultState.Unlocked).data)
        assertTrue("$size", size > 22L * 1024 * 1024 && size < VaultLimits.VAULT_MAX_DATA_BYTES)
        var best = Long.MAX_VALUE
        repeat(3) { i ->
            val t0 = System.nanoTime()
            assertTrue(store.upsert(big[i].copy(title = "Editada $i", updatedAt = 1)))
            best = minOf(best, System.nanoTime() - t0)
        }
        val markT0 = System.nanoTime()
        assertTrue(store.markUsed("g10"))
        val mark = System.nanoTime() - markT0
        println("M-6: upsert con la bóveda en ${size / 1024} KiB: ${best / 1_000_000} ms; markUsed: ${mark / 1_000_000} ms")
        assertTrue("${best / 1_000_000} ms", best < 30_000_000_000)
    }
}
