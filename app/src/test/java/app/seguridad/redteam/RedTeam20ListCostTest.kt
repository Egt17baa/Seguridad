package app.seguridad.redteam

import app.seguridad.core.appJson
import app.seguridad.vault.COPY_TREE_MAX_BYTES
import app.seguridad.vault.CustomField
import app.seguridad.vault.EntryFilter
import app.seguridad.vault.EntryType
import app.seguridad.vault.EntryVersion
import app.seguridad.vault.FieldKind
import app.seguridad.vault.Folder
import app.seguridad.vault.SavedView
import app.seguridad.vault.SearchIndex
import app.seguridad.vault.TagColor
import app.seguridad.vault.VaultData
import app.seguridad.vault.VaultEntry
import app.seguridad.vault.VaultLimits
import app.seguridad.vault.checkCopyEntries
import app.seguridad.vault.checkCopyShape
import app.seguridad.vault.checkCopySize
import app.seguridad.vault.checkCopyStructure
import app.seguridad.vault.copyExtraTags
import app.seguridad.vault.copyTreeEstimate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import java.util.UUID

/**
 * RT-25, part b: the criterion of what a copy accepted by every check of the import may cost to show (decoded tree +
 * search index + tag chips, the attack's `shownCost` of RedTeam20RecheckTest), as the director redefined it in round 2
 * of the fixes of the attack (option (i)). It replaces the «1.25 times the dearest legitimate shape per byte» of
 * advisor-t12-redteam-fixes-2.md (lines 37-51), which a copy in compact JSON passes (1.28 times) with nothing over what
 * the screens allow, so no cap on tags closes it:
 *
 * - **B, the criterion:** what the cap of part b ([copyExtraTags], at most [app.seguridad.vault.COPY_LIST_MAX] tags over
 *   [app.seguridad.vault.MAX_TAGS]) still lets in is judged in absolute terms. The floor, two entries of 5 010 distinct
 *   tags each (exactly 10 000 over), costs at most [FLOOR_MAX_BYTES] (4 MiB; measured, 2.95 MB): a constant, whatever
 *   the size of the copy.
 * - **C, each shape is what it says:** each passes every check of the import ([accepted]), and [copyExtraTags] gives its
 *   exact excess: 0 for each pair without excess, 10 000 for the cap, the versions and the floor, 9 990 for the
 *   repeated tags.
 * - **D, a guard against regressions (it decides no finding):** the densest pair without excess (today the 10 distinct
 *   tags an entry in minimal JSON, 21.40 B/B) stays at most [GUARD] (1.5) times the dearest legitimate shape written by
 *   the app (today the logins with 10 distinct tags of 5 letters, 17.65 B/B: 1.21 times). That density of the compact
 *   encoding is a named risk (pendientes.md, next to row 51), not a finding.
 * - **Informative rows, no assertion:** each hostile shape against its pair made from the same Gen() (the cap, about
 *   1.055); the cap against the legitimate shape with UUID ids (1.28, published as it is); the names of fields; and
 *   [copyTreeEstimate] per byte of the pair and of the cap, times [VaultLimits.VAULT_MAX_DATA_BYTES] +
 *   [VaultLimits.COPY_DATA_MARGIN_BYTES], against [COPY_TREE_MAX_BYTES].
 *
 * How it is measured: the live heap after GC, on samples of about [SAMPLE] bytes of JSON; each shape from its own
 * Random(20261118L) (the same data whatever the order of the tests), one warm-up first (the first [SearchIndex.build]
 * loads the Collator), the median of [REPEATS], and always in the same order ([measured]). The legitimate shapes are written with appJson from the app's own
 * objects (UUID ids, as the app mints them, and 13-digit dates); the hostile ones in minimal JSON. Invented data only.
 */
class RedTeam20ListCostTest {
    companion object {
        private const val SAMPLE = 1 shl 20
        private const val REPEATS = 5
        private const val FLOOR_MAX_BYTES = 4L shl 20
        private const val GUARD = 1.5
        private const val T = 1_800_000_000_000L

        private fun used(): Long {
            repeat(4) {
                System.gc()
                Thread.yield()
            }
            return Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() }
        }

        /** What the list needs to show [json] once open, as the attack measures it. */
        private fun shownCost(json: String): Long {
            val u0 = used()
            val data = appJson.decodeFromString(VaultData.serializer(), json)
            val index = SearchIndex.build(data.entries, data.folders)
            val chips = data.entries.flatMapTo(HashSet()) { it.tags }.sorted()
            val u1 = used()
            check(index.hashCode() != 0 || chips.isNotEmpty() || data.entries.isNotEmpty())
            return u1 - u0
        }

        /** Every check of the import, in its order (VaultStore.replace): the shape, decoding, structure, size, entries. */
        private fun accepted(json: String): VaultData {
            checkCopyShape(json.toByteArray(), 4)
            val data = appJson.decodeFromString(VaultData.serializer(), json)
            checkCopyStructure(data)
            checkCopySize(data)
            checkCopyEntries(data.entries)
            return data
        }

        private fun excess(json: String) = copyExtraTags(accepted(json).entries, stopAbove = Long.MAX_VALUE)

        /** Min, median and max of [REPEATS] measures of [json], in bytes kept per byte of JSON. */
        private fun perByte(json: String): Triple<Double, Double, Double> {
            val bytes = json.toByteArray().size.toDouble()
            val all = List(REPEATS) { shownCost(json) / bytes }.sorted()
            return Triple(all.first(), all[REPEATS / 2], all.last())
        }

        /** The generator of one shape: its own Random(20261118L); tags counted from [tagsFrom], ids that start with [idPrefix]. */
        private class Gen(tagsFrom: Int = 0, private val idPrefix: String = "e") {
            val rnd = Random(20261118L)
            var k = tagsFrom
            var ids = 0
            fun word(n: Int) = String(CharArray(n) { 'a' + rnd.nextInt(26) })
            fun tag() = (k++).toString(36).padStart(5, 'a')
            fun uuid() = UUID(rnd.nextLong(), rnd.nextLong()).toString()
            fun id() = idPrefix + ids++
        }

        /** The prefix of a hostile shape takes its tags from here, never shared with its pair's. */
        private const val PREFIX_TAGS = 50_000_000

        private fun legit(extra: VaultData = VaultData(), one: () -> VaultEntry): String {
            val entries = ArrayList<VaultEntry>()
            var size = appJson.encodeToString(VaultData.serializer(), extra).length + 64
            while (true) {
                val e = one()
                val add = appJson.encodeToString(VaultEntry.serializer(), e).length + 1
                if (size + add > SAMPLE) break
                size += add
                entries += e
            }
            return appJson.encodeToString(VaultData.serializer(), extra.copy(entries = entries, vaultTag = "b".repeat(32)))
        }

        private fun login(g: Gen, tags: List<String> = emptyList()) = VaultEntry(
            g.uuid(), EntryType.LOGIN, g.word(8), username = g.word(10), url = "https://${g.word(8)}.example",
            createdAt = T, updatedAt = T, tags = tags,
        )

        /** Minimal JSON: [prefix] entries, then entries made by [one] while it stays under [SAMPLE]. */
        private fun hostile(prefix: List<String> = emptyList(), one: () -> String): String = buildString(SAMPLE + 70_000) {
            append("{\"entries\":[")
            append(prefix.joinToString(","))
            var first = prefix.isEmpty()
            while (true) {
                val e = one()
                if (length + e.length + 60 > SAMPLE) break
                if (!first) append(',')
                first = false
                append(e)
            }
            append("],\"vaultTag\":\"").append("b".repeat(32)).append("\"}")
        }

        private fun minimal(g: Gen, tags: List<String> = emptyList(), more: String = "") =
            "{\"id\":\"${g.id()}\",\"type\":\"LOGIN\",\"title\":\"\",\"createdAt\":1,\"updatedAt\":1" +
                (if (tags.isEmpty()) "" else ",\"tags\":[" + tags.joinToString(",") { "\"$it\"" } + "]") + more + "}"

        private fun capPrefix() = Gen(PREFIX_TAGS, "p").let { p -> List(100) { minimal(p, List(110) { p.tag() }) } }
        private fun tenDistinct(g: Gen) = minimal(g, List(10) { g.tag() })
        private fun withVersions(g: Gen) =
            minimal(g, List(10) { g.tag() }, ",\"history\":[" + List(20) { "{\"savedAt\":$it,\"entry\":" + minimal(g, List(10) { g.tag() }) + "}" }.joinToString(",") + "]")
        private fun tenRepeated(g: Gen) = minimal(g, List(10) { "a" })

        /** A hostile shape, its pair (the same Gen(), without the excess), and the exact excess of each. */
        private class Shape(val name: String, val json: String, val excess: Long, val pair: String?, val pairExcess: Long = 0)

        private val shapes: List<Shape> by lazy {
            listOf(
                Shape("etiquetas distintas justo en el tope", Gen().let { g -> hostile(capPrefix()) { tenDistinct(g) } }, 10_000, Gen().let { g -> hostile { tenDistinct(g) } }),
                Shape("versiones con etiquetas distintas", Gen().let { g -> hostile(capPrefix()) { withVersions(g) } }, 10_000, Gen().let { g -> hostile { withVersions(g) } }),
                Shape("etiquetas repetidas, una entrada de 10 000", Gen().let { g -> hostile(listOf(minimal(Gen(PREFIX_TAGS, "p"), List(10_000) { "a" }))) { tenRepeated(g) } }, 9_990, Gen().let { g -> hostile { tenRepeated(g) } }),
                Shape("nombres de campo distintos", Gen().let { g ->
                    hostile { minimal(g, more = ",\"fields\":[" + List(VaultLimits.FIELDS_MAX) { "{\"id\":\"c$it\",\"name\":\"${g.tag()}\",\"kind\":\"TEXT\",\"value\":\"\"}" }.joinToString(",") + "]") }
                }, 0, null),
            )
        }

        /** The floor of B: two entries of 5 010 distinct tags each, exactly 10 000 over. */
        private val floorJson: String by lazy {
            val g = Gen()
            "{\"entries\":[" + minimal(g, List(5_010) { g.tag() }) + "," + minimal(g, List(5_010) { g.tag() }) + "],\"vaultTag\":\"" + "b".repeat(32) + "\"}"
        }

        init {
            // Warm-up: the Collator and the classes the first build loads are counted in no shape.
            val warm = appJson.encodeToString(VaultData.serializer(), VaultData(entries = Gen().let { g -> List(200) { login(g, List(10) { g.tag() }) } }))
            shownCost(warm)
            shownCost(warm)
        }

        /** The legitimate shapes of the first ruling, written by the app, each measured once. */
        private val legitimate: Map<String, Triple<Double, Double, Double>> by lazy {
            val shapes = linkedMapOf<String, String>()
            Gen().let { g -> shapes["notas largas"] = legit { VaultEntry(g.uuid(), EntryType.NOTE, g.word(12), notes = g.word(2_000), createdAt = T, updatedAt = T) } }
            Gen().let { g -> shapes["accesos mínimos distintos"] = legit { login(g) } }
            Gen().let { g -> shapes["accesos con 10 etiquetas distintas de 5 letras"] = legit { login(g, List(10) { g.tag() }) } }
            Gen().let { g ->
                shapes["30 campos visibles con nombres distintos"] = legit {
                    login(g).copy(fields = List(VaultLimits.FIELDS_MAX) { CustomField(g.uuid(), g.word(12), FieldKind.TEXT, g.word(6)) })
                }
            }
            Gen().let { g ->
                shapes["20 versiones con etiquetas"] = legit {
                    val e = login(g, List(10) { g.tag() })
                    e.copy(history = List(VaultLimits.HISTORY_MAX_VERSIONS) { v -> EntryVersion(T - v, e.copy(password = g.word(12))) })
                }
            }
            Gen().let { g ->
                val folders = List(VaultLimits.FOLDERS_MAX) { Folder(g.uuid(), g.word(20)) }
                val views = List(VaultLimits.VIEWS_MAX) { SavedView(g.uuid(), g.word(20), EntryFilter(text = g.word(10), tags = List(10) { g.word(5) }.toSet())) }
                val colors = List(VaultLimits.TAG_COLORS_MAX) { g.word(5) to TagColor.entries[it % TagColor.entries.size] }.toMap()
                shapes["carpetas, vistas y colores en sus topes"] = legit(VaultData(folders = folders, views = views, tagColors = colors)) { login(g, List(10) { g.tag() }) }
            }
            shapes.mapValues { (name, json) ->
                assertEquals("$name: a legitimate shape has nothing over 10", 0L, excess(json))
                perByte(json).also { println("RT-25 criterion|legítima|$name|${it.str()}") }
            }
        }

        private val dearest: Double by lazy { legitimate.values.maxOf { it.second } }

        /** Each pair without excess, measured once. */
        private val pairs: Map<String, Triple<Double, Double, Double>> by lazy {
            check(dearest > 0) // The legitimate shapes always first: see [measured].
            shapes.filter { it.pair != null }.associate { s -> s.name to perByte(s.pair!!).also { println("RT-25 criterion|pareja sin exceso|${s.name}|${it.str()}") } }
        }

        /**
         * Every measure in one fixed order (the legitimate shapes, the pairs, then the rest), whatever test runs first:
         * the live heap after GC depends on what the JVM did before (measured: the pair of the cap at 21.40 B/B after the
         * legitimate shapes, 24.40 right after the warm-up), so a fixed order is what makes the numbers the same at
         * every run.
         */
        private fun measured() = check(pairs.isNotEmpty())

        private fun Triple<Double, Double, Double>.str() = "%.2f|%.2f|%.2f".format(first, second, third)
    }

    /** B: the floor of what the cap still lets in is a constant, at most 4 MiB, in absolute terms. */
    @Test fun bTheFloorOfTheCapIsAtMostFourMiB() {
        measured()
        assertEquals(10_000L, excess(floorJson))
        val all = List(REPEATS) { shownCost(floorJson) }.sorted()
        println("RT-25 criterion|suelo|dos entradas de 5 010 etiquetas distintas (exceso 10 000)|${all.first()}|${all[REPEATS / 2]}|${all.last()} B")
        assertTrue("the floor costs ${all[REPEATS / 2]} bytes to show (at most $FLOOR_MAX_BYTES)", all[REPEATS / 2] <= FLOOR_MAX_BYTES)
    }

    /** C: each shape passes every check of the import and has exactly the excess it says. */
    @Test fun cEachShapeIsWhatItSays() {
        for (s in shapes) {
            assertEquals(s.name, s.excess, excess(s.json))
            s.pair?.let { assertEquals("${s.name}, its pair", s.pairExcess, excess(it)) }
        }
        assertEquals(10_000L, excess(floorJson))
    }

    /** D: the densest pair without excess stays at most 1.5 times the dearest legitimate shape (a guard, no finding). */
    @Test fun dTheCompactEncodingStaysUnderItsGuard() {
        measured()
        val densest = pairs.maxBy { it.value.second }
        val margin = densest.value.second / dearest
        println("RT-25 criterion|guardia|${densest.key} (pareja)|${"%.3f".format(margin)} x la legítima más cara (${"%.2f".format(dearest)})")
        assertTrue("${densest.key}: ${"%.3f".format(margin)} times the dearest legitimate shape (guard $GUARD)", margin <= GUARD)
    }

    /** The informative rows of the criterion: printed, never asserted. */
    @Test fun informativeRows() {
        measured()
        val budget = (VaultLimits.VAULT_MAX_DATA_BYTES + VaultLimits.COPY_DATA_MARGIN_BYTES).toDouble()
        for (s in shapes) {
            val h = perByte(s.json)
            val vsPair = pairs[s.name]?.let { "%.3f".format(h.second / it.second) } ?: "-"
            println("RT-25 criterion|hostil|${s.name}|${h.str()}|frente a su pareja $vsPair|frente a la legítima más cara ${"%.3f".format(h.second / dearest)}")
        }
        for (s in shapes.take(1)) {
            for ((what, json) in listOf("pareja" to s.pair!!, "tope" to s.json)) {
                val estimate = copyTreeEstimate(json.toByteArray(), 4).toDouble() / json.toByteArray().size
                println("RT-25 criterion|estimación|$what|${"%.2f".format(estimate)} B/B|x 24 MiB + 4 KiB = ${"%.0f".format(estimate * budget)} frente a COPY_TREE_MAX_BYTES $COPY_TREE_MAX_BYTES")
            }
        }
        assertTrue(shapes.isNotEmpty())
    }
}
