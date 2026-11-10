package app.seguridad.imports

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.junit.Test

/** What every JSON reader shares: the scan before parsing, the detection and what a broken JSON gives. */
class ImportJsonTest {
    private fun failed(text: String): String {
        val r = readImport(text.toByteArray())
        assertTrue("$r", r is ReadResult.Failed)
        return (r as ReadResult.Failed).message
    }

    private fun scan(text: String): String? = try {
        checkJsonShape(text)
        null
    } catch (e: ImportFailure) {
        e.message
    }

    @Test fun deepJsonIsRefused() {
        assertEquals(null, scan("[".repeat(JSON_MAX_DEPTH) + "]".repeat(JSON_MAX_DEPTH)))
        assertEquals(IMPORT_JSON_TOO_DEEP, scan("[".repeat(JSON_MAX_DEPTH + 1) + "]".repeat(JSON_MAX_DEPTH + 1)))
        assertEquals(IMPORT_JSON_TOO_DEEP, scan("{\"a\":".repeat(JSON_MAX_DEPTH + 1)))
        // Objects and arrays count alike, and depth goes down again when they close.
        assertEquals(null, scan(("{\"a\":[" + "1]}").repeat(10_000)))
        assertEquals("El archivo tiene más de 64 niveles anidados. Clavero no lo lee.", IMPORT_JSON_TOO_DEEP)
    }

    @Test fun bracketsInsideStringsDoNotCount() {
        val brackets = "[{".repeat(1000)
        assertEquals(null, scan("""{"a":"$brackets","b":"\"$brackets","c":"\\","d":"[\\\"[","e":"x\\\\\"$brackets"}"""))
        // After an escaped backslash the quote closes the string, so what follows counts again.
        assertEquals(IMPORT_JSON_TOO_DEEP, scan("""{"a":"\\"""" + "[".repeat(JSON_MAX_DEPTH)))
        // An escaped quote does not close it: the brackets after it are still text.
        assertEquals(null, scan("""{"a":"\"""" + "[".repeat(JSON_MAX_DEPTH) + "\"}"))
    }

    @Test(timeout = 5_000)
    fun theDepthIsCheckedBeforeParsing() {
        // Not valid JSON at all, and 8 million levels: refused for its depth, at once, without a stack overflow.
        val text = "{" + "[".repeat(8_000_000)
        assertEquals(IMPORT_JSON_TOO_DEEP, failed(text))
    }

    /** [n] copies of [item] in an array, comma-separated, between [prefix] and [suffix]. */
    private fun many(prefix: String, item: String, n: Int, suffix: String) = buildString {
        append(prefix)
        repeat(n) { if (it > 0) append(','); append(item) }
        append(suffix)
    }

    private fun nested(levels: Int) = "[".repeat(levels) + "1" + "]".repeat(levels)

    private fun obj(members: Int, value: String) = (0 until members).joinToString(",", "{", "}") { "\"k$it\":$value" }

    /**
     * Live heap after GC of the tree kotlinx-serialization-json 1.11.0 builds, in bytes per element of an outer array
     * (JDK 21, compressed references, SerialGC), measured for fix round 2 of task 7 (the report has the table and the
     * script) and rounded up. Deterministic: the estimate is compared with these numbers, not with a heap probe.
     */
    private val measured: List<Pair<String, Long>> = listOf(
        "{}" to 86L,
        "[]" to 45L,
        "1" to 78L,
        "true" to 78L,
        "null" to 6L,
        "\"\"" to 30L,
        "{\"k\":1}" to 325L,
        "{\"\":\"\"}" to 229L,
        "[1]" to 173L,
        "[true]" to 173L,
        "{\"a\":[1]}" to 421L,
        nested(2) to 270L,
        nested(10) to 1038L,
        nested(31) to 3054L,
        nested(62) to 6030L,
        "[" + List(10) { "1" }.joinToString(",") + "]" to 821L,
        "[" + List(11) { "1" }.joinToString(",") + "]" to 918L,
        "[" + List(15) { "1" }.joinToString(",") + "]" to 1205L,
        "[" + List(16) { "1" }.joinToString(",") + "]" to 1302L,
        "[" + List(22) { "1" }.joinToString(",") + "]" to 1733L,
        "[" + List(23) { "1" }.joinToString(",") + "]" to 1853L,
        "[" + List(33) { "1" }.joinToString(",") + "]" to 2573L,
        "[" + List(34) { "1" }.joinToString(",") + "]" to 2709L,
        "[" + List(35) { "1" }.joinToString(",") + "]" to 2781L,
        "[" + List(49) { "1" }.joinToString(",") + "]" to 3789L,
        "[" + List(50) { "1" }.joinToString(",") + "]" to 3957L,
        obj(1, "1") to 325L,
        obj(2, "1") to 485L,
        obj(12, "1") to 2086L,
        obj(13, "1") to 2310L,
        obj(24, "1") to 4069L,
        obj(25, "1") to 4357L,
        obj(48, "1") to 8039L,
        obj(49, "1") to 8453L,
        obj(97, "1") to 16646L,
        obj(1, "null") to 253L,
        obj(13, "null") to 1374L,
        obj(25, "null") to 2557L,
        obj(49, "null") to 4925L,
        "\"" + "Ж".repeat(100) + "\"" to 269L,
        "\"" + "x".repeat(100) + "\"" to 173L,
        "\"" + "Ж".repeat(10_000) + "\"" to 20070L,
        // The review's worst shapes, 1 000 objects in one array each.
        "{\"a\":[" + List(1000) { "{}" }.joinToString(",") + "]}" to 85246L,
        "{\"a\":[" + List(1000) { "{\"k\":1}" }.joinToString(",") + "]}" to 325246L,
    )

    @Test fun theEstimateIsNeverBelowTheMeasuredTree() {
        for ((shape, bytes) in measured) {
            val n = (200_000 / shape.length).coerceIn(20, 2_000)
            val estimate = jsonTreeEstimate(many("[", shape, n, "]"))
            assertTrue("${shape.take(40)}: $estimate < $n × $bytes", estimate >= n * bytes)
        }
        // The two real items of the Bitwarden fixture, measured the same way: 5 869 B and 2 109 B.
        val items = (Json.parseToJsonElement(bitwardenFixture()) as JsonObject)["items"] as JsonArray
        for ((item, bytes) in listOf(items[0] to 5869L, items[6] to 2109L)) {
            assertTrue(jsonTreeEstimate(many("[", item.toString(), 100, "]")) >= 100 * bytes)
        }
    }

    private fun bitwardenFixture(): String =
        requireNotNull(javaClass.getResourceAsStream("/import/bitwarden/bitwarden.json")).use { it.readBytes().toString(Charsets.UTF_8) }

    @Test fun theModelFollowsHowTheTreeGrows() {
        val number = JSON_TREE_PRIMITIVE_BYTES + JSON_TREE_CHAR_BYTES
        assertEquals(JSON_TREE_OBJECT_BYTES, jsonTreeEstimate("{}"))
        assertEquals(JSON_TREE_ARRAY_BYTES, jsonTreeEstimate("[]"))
        assertEquals(JSON_TREE_PRIMITIVE_BYTES + 3 * JSON_TREE_CHAR_BYTES, jsonTreeEstimate("123"))
        assertEquals(JSON_TREE_PRIMITIVE_BYTES + 2 * JSON_TREE_CHAR_BYTES, jsonTreeEstimate("\"ab\""))
        // An array makes room for 10 at its first element, then grows by half: 15, 22, 33…
        assertEquals(56L, refArrayBytes(10))
        assertEquals(JSON_TREE_ARRAY_BYTES + refArrayBytes(10) + number, jsonTreeEstimate("[1]"))
        assertEquals(JSON_TREE_ARRAY_BYTES + refArrayBytes(10) + 10 * number, jsonTreeEstimate("[" + List(10) { "1" }.joinToString(",") + "]"))
        assertEquals(JSON_TREE_ARRAY_BYTES + refArrayBytes(15) + 11 * number, jsonTreeEstimate("[" + List(11) { "1" }.joinToString(",") + "]"))
        assertEquals(JSON_TREE_ARRAY_BYTES + refArrayBytes(22) + 16 * number, jsonTreeEstimate("[" + List(16) { "1" }.joinToString(",") + "]"))
        assertEquals(JSON_TREE_ARRAY_BYTES + refArrayBytes(33) + 23 * number, jsonTreeEstimate("[" + List(23) { "1" }.joinToString(",") + "]"))
        assertEquals(JSON_TREE_ARRAY_BYTES + refArrayBytes(49) + 35 * number, jsonTreeEstimate("[" + List(35) { "1" }.joinToString(",") + "]"))
        // An object makes a table of 16 at its first member and doubles it past ¾: 32 at 13 members, 64 at 25.
        val member = JSON_TREE_MEMBER_BYTES + JSON_TREE_CHAR_BYTES // a one-letter name
        assertEquals(JSON_TREE_OBJECT_BYTES + refArrayBytes(16) + member + number, jsonTreeEstimate("{\"k\" : 1}"))
        fun letters(m: Int) = (0 until m).joinToString(",", "{", "}") { "\"${'a' + it % 26}\":1" }
        assertEquals(JSON_TREE_OBJECT_BYTES + refArrayBytes(16) + 12 * (member + number), jsonTreeEstimate(letters(12)))
        assertEquals(JSON_TREE_OBJECT_BYTES + refArrayBytes(32) + 13 * (member + number), jsonTreeEstimate(letters(13)))
        assertEquals(JSON_TREE_OBJECT_BYTES + refArrayBytes(64) + 25 * (member + number), jsonTreeEstimate(letters(25)))
        // A one-element array in each level: each level makes its own room for 10.
        assertEquals(62 * (JSON_TREE_ARRAY_BYTES + refArrayBytes(10)) + number, jsonTreeEstimate(nested(62)))
        assertTrue(JSON_TREE_MEMBER_BYTES >= JSON_TREE_PRIMITIVE_BYTES)
        assertEquals(48L * 1024 * 1024, JSON_MAX_TREE_BYTES)
    }

    /** The largest count of «1» in `{"a":[…]}` whose estimate is within [budget]: the estimate only grows with it. */
    private fun mostNumbers(budget: Long): Int {
        var low = 0
        var high = 1
        while (jsonTreeEstimate(numbers(high)) <= budget) high *= 2
        while (high - low > 1) {
            val mid = (low + high) / 2
            if (jsonTreeEstimate(numbers(mid)) <= budget) low = mid else high = mid
        }
        return low
    }

    private fun numbers(n: Int) = many("{\"a\":[", "1", n, "]}")

    private fun refusedBeforeParsing(text: String, budget: Long): Boolean = try {
        readJsonImport(text, budget)
        false
    } catch (e: ImportFailure) {
        assertEquals(IMPORT_JSON_TOO_MANY_VALUES, e.message)
        true
    }

    @Test fun aTreeOverTheBudgetIsRefusedBeforeParsing() {
        // A small budget, so the files are small: at the budget it is read (an unknown shape here), one more is refused.
        val budget = 200_000L
        val most = mostNumbers(budget)
        assertTrue(jsonTreeEstimate(numbers(most)) <= budget && jsonTreeEstimate(numbers(most + 1)) > budget)
        assertEquals(null, readJsonImport(numbers(most), budget))
        assertTrue(refusedBeforeParsing(numbers(most + 1), budget))
        // Refused before parsing: the same file left open, which the parser would refuse, is still «too much data».
        assertTrue(refusedBeforeParsing(many("{\"a\":[", "1", most + 1, ""), budget))
        // The shapes that cost more than they weigh.
        assertTrue(refusedBeforeParsing(many("{\"a\":[", nested(62), budget.toInt() / 6000 + 1, "]}"), budget))
        assertTrue(refusedBeforeParsing(many("{\"a\":[", "{\"k\":1}", budget.toInt() / 325 + 1, "]}"), budget))
        assertTrue(refusedBeforeParsing(many("{\"a\":[", "[1]", budget.toInt() / 173 + 1, "]}"), budget))
        // A long string is costed by its length: within the budget it is read, past it refused.
        val chars = ((budget - jsonTreeEstimate("{\"a\":\"\"}")) / JSON_TREE_CHAR_BYTES).toInt()
        assertEquals(null, readJsonImport("{\"a\":\"" + "x".repeat(chars) + "\"}", budget))
        assertTrue(refusedBeforeParsing("{\"a\":\"" + "x".repeat(chars + 1) + "\"}", budget))
        assertEquals("El archivo tiene demasiados datos. Clavero no lo lee.", IMPORT_JSON_TOO_MANY_VALUES)
    }

    @Test(timeout = 10_000)
    fun theRealBudgetIsTheOneReadImportUses() {
        // The one check at the real 48 MiB, and fast: an open file, so only the scan runs (about 1.3 MB of text).
        val n = (JSON_MAX_TREE_BYTES / (JSON_TREE_PRIMITIVE_BYTES + JSON_TREE_CHAR_BYTES)).toInt() + 1
        assertEquals(IMPORT_JSON_TOO_MANY_VALUES, failed(many("{\"a\":[", "1", n, "")))
    }

    @Test fun aJsonOfNoKnownShapeIsUnknown() {
        for (t in listOf("{}", "{\"a\":1}", "{\"items2\":[]}", " \n\t{\"x\":[1,2]}", "\uFEFF{\"x\":1}")) assertEquals(t, IMPORT_UNKNOWN, failed(t))
        // A JSON that is not an object is not a format Clavero reads.
        assertEquals(IMPORT_UNKNOWN, failed("[{\"encrypted\":false,\"items\":[]}]"))
    }

    @Test fun aBrokenJsonIsUnknownAndItsTextNeverShows() {
        val secret = "clave-del-archivo-123"
        for (t in listOf(
            "{\"encrypted\":false,\"items\":[{\"type\":1,\"login\":{\"password\":\"$secret\"",
            "{\"items\":[1,]}",
            "{\"a\":1} $secret",
            "{\"a\":\"\\x$secret\"}",
            "{\"a\":\"\\u12\"}",
            "{$secret}",
        )) {
            val message = failed(t)
            assertEquals(t, IMPORT_UNKNOWN, message)
            assertFalse(message.contains(secret))
        }
    }

    @Test fun csvIsStillReadAsCsv() {
        // A CSV is asked first, as before; a JSON never looks like a known CSV header.
        val r = readImport("name,url,username,password,note\nweb,,ana,clave,\n".toByteArray())
        assertEquals(ImportFormat.BROWSER_CSV, (r as ReadResult.Ok).read.format)
        assertEquals(ImportFormat.BITWARDEN, (readImport("{\"encrypted\":false,\"items\":[]}".toByteArray()) as ReadResult.Ok).read.format)
    }

    @Test fun aJsonInUtf16IsRead() {
        val text = "{\"encrypted\":false,\"items\":[{\"type\":2,\"name\":\"ñ\",\"notes\":\"x\"}]}"
        val bytes = byteArrayOf(0xFE.toByte(), 0xFF.toByte()) + text.toByteArray(Charsets.UTF_16BE)
        assertEquals("ñ", (readImport(bytes) as ReadResult.Ok).read.items.single().entry.title)
    }
}
