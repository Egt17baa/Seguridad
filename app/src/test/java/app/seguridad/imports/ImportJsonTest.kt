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

    @Test(timeout = 10_000)
    fun tooManyValuesAreRefusedBeforeParsing() {
        val values = "{\"items\":[" + "1,".repeat(JSON_MAX_TOKENS) + "1]}"
        assertEquals(IMPORT_JSON_TOO_MANY_VALUES, failed(values))
        assertEquals(IMPORT_JSON_TOO_MANY_VALUES, failed("{\"a\":[" + "[],".repeat(JSON_MAX_TOKENS) + "[]]}"))
        assertEquals(IMPORT_JSON_TOO_MANY_VALUES, failed("{\"a\":[" + "\"\",".repeat(JSON_MAX_TOKENS) + "\"\"]}"))
        // Exactly at the bound is read (as an unknown shape here): «{», «"a"», «[» and the numbers.
        assertEquals(IMPORT_UNKNOWN, failed("{\"a\":[" + "1,".repeat(JSON_MAX_TOKENS - 4) + "1]}"))
        assertEquals(IMPORT_JSON_TOO_MANY_VALUES, failed("{\"a\":[" + "1,".repeat(JSON_MAX_TOKENS - 3) + "1]}"))
        assertEquals("El archivo tiene demasiados datos. Clavero no lo lee.", IMPORT_JSON_TOO_MANY_VALUES)
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
