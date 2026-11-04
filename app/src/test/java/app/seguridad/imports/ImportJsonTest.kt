package app.seguridad.imports

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test fun theEstimateIsNeverBelowTheMeasuredTree() {
        // Live heap after GC of the tree kotlinx-serialization-json 1.11.0 builds, per element (JDK 21, compressed
        // references, SerialGC; the review's script and the KDoc of ImportJson.kt). Deterministic: the estimate is
        // compared with those measured numbers, not with a heap probe.
        val n = 10_000
        assertTrue(jsonTreeEstimate(many("{\"a\":[", "{}", n, "]}")) >= n * 85L) // review: 84.6 MB / 999 990
        assertTrue(jsonTreeEstimate(many("[", "1", n, "]")) >= n * 77L) // review: 76.9 MB / 1 M
        assertTrue(jsonTreeEstimate(many("{\"a\":[", "{\"k\":1}", n, "]}")) >= n * 325L) // review: 108 MB / 333 000
        assertTrue(jsonTreeEstimate(many("[", "[]", n, "]")) >= n * 45L)
        assertTrue(jsonTreeEstimate(many("[", "\"\"", n, "]")) >= n * 26L)
        // Objects whose hash table grows in steps, with null values (which cost nothing in the tree).
        for ((members, measured) in listOf(1 to 254L, 2 to 319L, 13 to 1238L, 25 to 2291L, 49 to 4394L, 97 to 8601L)) {
            val obj = (0 until members).joinToString(",", "{", "}") { "\"k$it\":null" }
            assertTrue("$members", jsonTreeEstimate(many("[", obj, 1_000, "]")) >= 1_000 * measured)
        }
        // A long string outside Latin-1: 68 + 2·L bytes.
        val length = 1_000_000
        assertTrue(jsonTreeEstimate("{\"a\":\"" + "Ж".repeat(length) + "\"}") >= 68L + 2L * length)
    }

    @Test(timeout = 20_000)
    fun aTreeOverTheBudgetIsRefusedBeforeParsing() {
        fun numbers(n: Int) = many("{\"a\":[", "1", n, "]}")
        val per = jsonTreeEstimate(numbers(2)) - jsonTreeEstimate(numbers(1))
        val base = jsonTreeEstimate(numbers(1)) - per
        val most = ((JSON_MAX_TREE_BYTES - base) / per).toInt()
        assertEquals(JSON_MAX_TREE_BYTES - (JSON_MAX_TREE_BYTES - base) % per, jsonTreeEstimate(numbers(most)))
        // At the budget it is read (as an unknown shape here); one more number is refused.
        assertEquals(IMPORT_UNKNOWN, failed(numbers(most)))
        assertEquals(IMPORT_JSON_TOO_MANY_VALUES, failed(numbers(most + 1)))
        // Refused before parsing: the same file left open is still «too much data», not unknown.
        assertEquals(IMPORT_JSON_TOO_MANY_VALUES, failed(many("{\"a\":[", "1", most + 1, "")))
        assertEquals(IMPORT_JSON_TOO_MANY_VALUES, failed(many("{\"a\":[", "{\"k\":1}", 200_000, "]}")))
        assertEquals(IMPORT_JSON_TOO_MANY_VALUES, failed(many("{\"a\":[", "{}", 300_000, "]}")))
        assertEquals("El archivo tiene demasiados datos. Clavero no lo lee.", IMPORT_JSON_TOO_MANY_VALUES)
    }

    @Test(timeout = 20_000)
    fun aLongStringIsWithinTheBudget() {
        // One string of 8 M characters (16 MB of tree as UTF-16) is read: the bound is on the tree, not on the file.
        val text = "{\"a\":\"" + "x".repeat(8_000_000) + "\"}"
        assertTrue(jsonTreeEstimate(text) < JSON_MAX_TREE_BYTES)
        assertEquals(IMPORT_UNKNOWN, failed(text))
    }

    @Test fun theConstantsCoverTheMeasuredNodes() {
        // The member's name is first counted as a string, then turned into a member at «:».
        assertTrue(JSON_TREE_MEMBER_BYTES >= JSON_TREE_PRIMITIVE_BYTES)
        assertEquals(48L * 1024 * 1024, JSON_MAX_TREE_BYTES)
        assertEquals(JSON_TREE_OBJECT_BYTES, jsonTreeEstimate("{}"))
        assertEquals(JSON_TREE_ARRAY_BYTES, jsonTreeEstimate("[]"))
        assertEquals(JSON_TREE_PRIMITIVE_BYTES + 3 * JSON_TREE_CHAR_BYTES, jsonTreeEstimate("123"))
        assertEquals(JSON_TREE_PRIMITIVE_BYTES + 2 * JSON_TREE_CHAR_BYTES, jsonTreeEstimate("\"ab\""))
        assertEquals(
            JSON_TREE_OBJECT_BYTES + JSON_TREE_MEMBER_BYTES + JSON_TREE_CHAR_BYTES + JSON_TREE_PRIMITIVE_BYTES + JSON_TREE_CHAR_BYTES,
            jsonTreeEstimate("{\"k\" : 1}"),
        )
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
