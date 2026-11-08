package app.seguridad.imports

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CsvTest {
    private fun failure(block: () -> Unit): String {
        try {
            block()
        } catch (e: ImportFailure) {
            return e.message.orEmpty()
        }
        fail("Expected ImportFailure")
        error("unreachable")
    }

    // --- RFC 4180 ---

    @Test fun quotesKeepTheDelimiter() {
        assertEquals(listOf(listOf("a", "b", "c"), listOf("1", "dos, tres", "4")), parseCsv("a,b,c\n1,\"dos, tres\",4\n"))
    }

    @Test fun doubledQuotesAreOneQuote() {
        assertEquals(listOf(listOf("a"), listOf("di \"hola\"")), parseCsv("a\n\"di \"\"hola\"\"\""))
        assertEquals(listOf(listOf("a", "b"), listOf("\"", "")), parseCsv("a,b\n\"\"\"\",\"\""))
    }

    @Test fun newlineInsideQuotesStaysInTheField() {
        assertEquals(listOf(listOf("a", "b"), listOf("uno\ndos", "x"), listOf("tres\r\ncuatro", "y")), parseCsv("a,b\n\"uno\ndos\",x\n\"tres\r\ncuatro\",y"))
    }

    @Test fun emptyLastFieldIsKept() {
        assertEquals(listOf(listOf("a", "b", "c"), listOf("1", "2", "")), parseCsv("a,b,c\n1,2,\n"))
        assertEquals(listOf(listOf("a", "b"), listOf("", "")), parseCsv("a,b\n,"))
    }

    @Test fun crlfEndsARecord() {
        assertEquals(listOf(listOf("a", "b"), listOf("1", "2"), listOf("3", "4")), parseCsv("a,b\r\n1,2\r\n3,4\r\n"))
    }

    @Test fun semicolonFromTheHeaderIsTheDelimiter() {
        // A spreadsheet in Spanish saves with «;»: a comma in a field is then just text.
        assertEquals(listOf(listOf("name", "url", "note"), listOf("Mi web", "https://a.example", "uno, dos")), parseCsv("name;url;note\nMi web;https://a.example;uno, dos\n"))
        // A «;» inside quotes in the header does not count.
        assertEquals(listOf(listOf("a;b", "c")), parseCsv("\"a;b\",c"))
        // And the other way round: commas inside a quoted header name do not count against the «;».
        assertEquals(';', csvDelimiter("\"a,b\";c;d\n1;2;3"))
        assertEquals(listOf(listOf("a,b", "c", "d"), listOf("1", "2,5", "3")), parseCsv("\"a,b\";c;d\n1;2,5;3"))
        // A tie (or no delimiter at all) is a comma.
        assertEquals(',', csvDelimiter("a;b,c"))
        assertEquals(',', csvDelimiter("nombre"))
        // Blank lines before the header are passed over, as the parser does.
        assertEquals(';', csvDelimiter("\n\r\nname;url\n"))
    }

    @Test fun delimiterDetectionUsesTheParsersQuoteRule() {
        // A quote in the middle of a header field is text (it opens nothing), for the detection as for the parse.
        assertEquals(';', csvDelimiter("ab\"c;d;e\n1;2;3"))
        assertEquals(listOf(listOf("ab\"c", "d", "e"), listOf("1", "2", "3")), parseCsv("ab\"c;d;e\n1;2;3"))
        assertEquals(',', csvDelimiter("na\"me;url,x"))
        assertEquals(listOf(listOf("na\"me;url", "x")), parseCsv("na\"me;url,x"))
        assertEquals(';', csvDelimiter("na\"me;url;x,y"))
        assertEquals(listOf(listOf("na\"me", "url", "x,y")), parseCsv("na\"me;url;x,y"))
        // A quote at the start of a field does open one, with the delimiter inside it as text.
        assertEquals(listOf(listOf("a;b;c", "d")), parseCsv("\"a;b;c\",d"))
        assertEquals(listOf(listOf("x", "a,b", "d", "e")), parseCsv("x;\"a,b\";d;e"))
    }

    @Test fun delimiterDetectionAndParseAgreeOnAnyHeader() {
        // Headers of commas, semicolons, quotes, spaces, letters and line breaks, at random but repeatable: the
        // delimiter chosen is the one whose reading gives more cells, and the parse gives exactly that many.
        val random = java.util.Random(6)
        val alphabet = "ab,;\" \n\r"
        repeat(20_000) {
            val text = String(CharArray(random.nextInt(14)) { alphabet[random.nextInt(alphabet.length)] })
            val d = csvDelimiter(text)
            assertEquals(text, if (csvFirstRecordCells(text, ';') > csvFirstRecordCells(text, ',')) ';' else ',', d)
            val rows = try {
                parseCsv(text)
            } catch (e: ImportFailure) {
                assertEquals(text, IMPORT_CSV_UNCLOSED_QUOTE, e.message)
                return@repeat
            }
            assertEquals(text, csvFirstRecordCells(text, d), rows.firstOrNull()?.size ?: 0)
        }
    }

    @Test fun aCellLeftOpenByAQuoteCountsForTheDelimiter() {
        // Final fix wave: the cell an unclosed quote opened is counted, as if the quote closed where the text ends.
        assertEquals(2, csvFirstRecordCells("a;\"b", ';'))
        assertEquals(1, csvFirstRecordCells("a;\"b", ','))
        assertEquals(1, csvFirstRecordCells("\"", ','))
        // So adding the closing quote never changes the count of a record that has a cell.
        val random = java.util.Random(7)
        val alphabet = "ab,;\" \n\r"
        repeat(20_000) {
            val text = String(CharArray(random.nextInt(14)) { alphabet[random.nextInt(alphabet.length)] })
            for (d in listOf(',', ';')) {
                val cells = csvFirstRecordCells(text, d)
                if (cells > 0) assertEquals("$d «$text»", cells, csvFirstRecordCells(text + "\"", d))
            }
        }
    }

    @Test fun anUnclosedQuoteEndsInItsErrorWhateverTheDelimiter() {
        // A quote that the chosen delimiter's reading leaves open is IMPORT_CSV_UNCLOSED_QUOTE, never rows.
        // With «;» (two cells against one) the quote opens a field.
        assertEquals(';', csvDelimiter("a;\"b\n1;2"))
        assertEquals(IMPORT_CSV_UNCLOSED_QUOTE, failure { parseCsv("a;\"b\n1;2") })
        // At the start of the text both readings open it.
        for (text in listOf("\"a,b;c", "\"a;b;c,d\n1;2", "\"a,b,c;d\n1,2"))
            assertEquals(text, IMPORT_CSV_UNCLOSED_QUOTE, failure { parseCsv(text) })
        // In a later row, with either delimiter.
        assertEquals(IMPORT_CSV_UNCLOSED_QUOTE, failure { parseCsv("a;b;c\n1;\"2;3\n") })
        assertEquals(IMPORT_CSV_UNCLOSED_QUOTE, failure { parseCsv("a,b,c\n1,\"2,3\n") })
        // The header alone, to tell the format, cannot be read either.
        assertNull(csvHeader("a;\"b"))
        // The comma reading of «a,b;"c» has the quote in the middle of a cell: it opens nothing, two cells (tie with «;»).
        assertEquals(listOf(listOf("a", "b;\"c")), parseCsv("a,b;\"c"))
    }

    // --- Text decoding ---

    @Test fun utf8WithAndWithoutBom() {
        val plain = "name,note\nÑandú,café ☕ 𝄞\n"
        assertEquals(plain, decodeText(plain.toByteArray(Charsets.UTF_8)))
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + plain.toByteArray(Charsets.UTF_8)
        assertEquals(plain, decodeText(bom))
        assertEquals(listOf("name", "note"), parseCsv(decodeText(bom)!!)[0])
    }

    @Test fun utf16LittleEndianWithBom() {
        val text = "name,note\nÑandú,𝄞\n"
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + text.toByteArray(Charsets.UTF_16LE)
        assertEquals(text, decodeText(bytes))
    }

    @Test fun utf16BigEndianWithBom() {
        val text = "name,note\nÑandú,𝄞\n"
        val bytes = byteArrayOf(0xFE.toByte(), 0xFF.toByte()) + text.toByteArray(Charsets.UTF_16BE)
        assertEquals(text, decodeText(bytes))
    }

    @Test fun invalidUtf8IsRejected() {
        fun bytes(vararg b: Int) = ByteArray(b.size) { b[it].toByte() }
        assertNull(decodeText(bytes('a'.code, 0xC3, 0x28))) // a lead byte without its continuation
        assertNull(decodeText(bytes(0xC0, 0xAF))) // overlong «/»
        assertNull(decodeText(bytes(0xED, 0xA0, 0x80))) // a surrogate written as UTF-8
        assertNull(decodeText(bytes('a'.code, 0xE2, 0x82))) // cut at the end
        assertNull(decodeText(bytes(0xF4, 0x90, 0x80, 0x80))) // past U+10FFFF
        assertNull(decodeText(bytes(0xFF, 'a'.code)))
    }

    @Test fun utf16WithALoneSurrogateOrAnOddLengthIsRejected() {
        fun le(vararg units: Int) = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + ByteArray(units.size * 2) { i ->
            (if (i % 2 == 0) units[i / 2] and 0xFF else units[i / 2] shr 8).toByte()
        }
        assertEquals("a", decodeText(le('a'.code)))
        assertNull(decodeText(le(0xD800, 'a'.code))) // high surrogate without its pair
        assertNull(decodeText(le('a'.code, 0xDC00))) // lone low surrogate
        assertNull(decodeText(le(0xD800))) // high surrogate at the end
        assertNull(decodeText(le('a'.code) + byteArrayOf(0x62))) // odd number of bytes
        val be = byteArrayOf(0xFE.toByte(), 0xFF.toByte(), 0xDC.toByte(), 0x00, 0x00, 0x61)
        assertNull(decodeText(be))
    }

    @Test fun emptyBytesAreEmptyText() {
        assertEquals("", decodeText(ByteArray(0)))
        assertEquals(emptyList<List<String>>(), parseCsv(""))
    }

    // --- Hostile or odd input: each case has one defined answer ---

    @Test fun unterminatedQuoteIsAFailure() {
        assertEquals(IMPORT_CSV_UNCLOSED_QUOTE, failure { parseCsv("a,b\n\"sin cerrar,2\n3,4\n") })
    }

    @Test fun crAloneEndsARecord() {
        // Old Mac line endings: each CR is a line break, as LF and CRLF are.
        assertEquals(listOf(listOf("a", "b"), listOf("1", "2"), listOf("3", "4")), parseCsv("a,b\r1,2\r3,4\r"))
    }

    @Test fun blankLinesAndTheFinalNewlineAddNoRow() {
        assertEquals(listOf(listOf("a"), listOf("1")), parseCsv("a\n\n1\n\r\n\n"))
        // A quoted empty field is a real (empty) cell, not a blank line.
        assertEquals(listOf(listOf("a"), listOf("")), parseCsv("a\n\"\"\n"))
    }

    @Test fun strayQuotesAreKeptAsText() {
        // RFC 4180 does not allow them; they are kept as written, never a crash.
        assertEquals(listOf(listOf("a", "b"), listOf("ab\"c", "x\"")), parseCsv("a,b\nab\"c,x\""))
        // Text right after a closing quote stays in the same field.
        assertEquals(listOf(listOf("a", "b"), listOf("abcx", "y")), parseCsv("a,b\n\"abc\"x,y"))
    }

    @Test fun nulAndControlCharactersAreKeptByTheParser() {
        // The parser stores text exactly; the readers decide (a NUL in a known field is a damaged row).
        assertEquals(listOf(listOf("a", "b"), listOf("x\u0000y", "\u0007")), parseCsv("a,b\nx\u0000y,\u0007"))
    }

    @Test fun aLongFieldIsCutPastEveryLimitWhileItIsRead() {
        val long = "x".repeat(200_000)
        val rows = parseCsv("a,b\n$long,\"$long\"\n")
        assertEquals(CSV_MAX_FIELD_CHARS, rows[1][0].length)
        assertEquals(CSV_MAX_FIELD_CHARS, rows[1][1].length)
        for (limit in listOf(MAX_TITLE, MAX_USERNAME, MAX_URL, MAX_PASSWORD, MAX_NOTES)) assertTrue(CSV_MAX_FIELD_CHARS > limit)
    }

    @Test fun tooManyColumnsIsAFailure() {
        assertEquals(1, parseCsv(List(CSV_MAX_COLUMNS) { "c$it" }.joinToString(",")).size)
        assertEquals(IMPORT_CSV_TOO_MANY_COLUMNS, failure { parseCsv(List(CSV_MAX_COLUMNS + 1) { "c$it" }.joinToString(",")) })
        // In a data row as well, not only in the header.
        assertEquals(IMPORT_CSV_TOO_MANY_COLUMNS, failure { parseCsv("a,b\n" + ",".repeat(CSV_MAX_COLUMNS)) })
    }

    @Test fun tooManyCellsIsAFailure() {
        val row = ",".repeat(49) // 50 cells per row
        val rows = CSV_MAX_CELLS / 50
        assertEquals(rows, parseCsv((row + "\n").repeat(rows)).size)
        assertEquals(IMPORT_CSV_TOO_MANY_CELLS, failure { parseCsv((row + "\n").repeat(rows + 1)) })
    }

    @Test fun tooManyRowsIsAFailure() {
        assertEquals(IMPORT_MAX_ITEMS + 1, parseCsv("a\n" + "x\n".repeat(IMPORT_MAX_ITEMS)).size)
        assertEquals(IMPORT_TOO_MANY_ITEMS, failure { parseCsv("a\n" + "x\n".repeat(IMPORT_MAX_ITEMS + 1)) })
    }

    /**
     * Bytes this thread allocates while [block] runs: every allocation, garbage included, so a bound on it is a bound on
     * memory. [block] always runs; the answer is null where the probe is missing (it is HotSpot's), and then only the
     * memory bounds are not checked. Reached by name because the Android unit-test classpath does not declare
     * java.lang.management (the tests run on a desktop JVM that has it).
     */
    private fun allocated(block: () -> Unit): Long? {
        val bean = runCatching {
            Class.forName("java.lang.management.ManagementFactory").getMethod("getThreadMXBean").invoke(null)
        }.getOrNull()
        val type = runCatching { Class.forName("com.sun.management.ThreadMXBean") }.getOrNull()
        if (bean == null || type == null || !type.isInstance(bean)) {
            block()
            return null
        }
        type.getMethod("setThreadAllocatedMemoryEnabled", Boolean::class.javaPrimitiveType).invoke(bean, true)
        val get = type.getMethod("getThreadAllocatedBytes", Long::class.javaPrimitiveType)
        @Suppress("DEPRECATION") // Thread.threadId() needs Java 19; the unit tests also run on older JVMs.
        val id = Thread.currentThread().id
        val before = get.invoke(bean, id) as Long
        block()
        return (get.invoke(bean, id) as Long) - before
    }

    /** [measured] is below [limit] bytes, when the probe could measure it. */
    private fun assertBelow(limit: Long, measured: Long?) {
        if (measured != null) assertTrue("allocated $measured", measured < limit)
    }

    @Test(timeout = 20_000)
    fun sixteenMegabytesOfCommasIsRefusedFastWithBoundedMemory() {
        // The probe itself sees an allocation (so a small number below is a real measure, not a broken probe).
        var sink: ByteArray? = null
        allocated { sink = ByteArray(4 * 1024 * 1024) }?.let { assertTrue(it >= 4L * 1024 * 1024) }
        assertEquals(4 * 1024 * 1024, sink!!.size)

        // One line of commas: 16 million empty fields if nothing stopped it.
        val oneLine = ",".repeat(IMPORT_MAX_BYTES)
        var message = ""
        val bytesOneLine = allocated { message = failure { parseCsv(oneLine) } }
        assertEquals(IMPORT_CSV_TOO_MANY_COLUMNS, message)
        assertBelow(1024 * 1024, bytesOneLine)

        // Lines of 64 empty cells: every row is allowed, the cells in all are not.
        val manyLines = (",".repeat(CSV_MAX_COLUMNS - 1) + "\n").repeat(IMPORT_MAX_BYTES / CSV_MAX_COLUMNS)
        val bytesManyLines = allocated { message = failure { parseCsv(manyLines) } }
        assertEquals(IMPORT_CSV_TOO_MANY_CELLS, message)
        // Empty cells share one string: what is left is the lists that hold them.
        assertBelow(64L * 1024 * 1024, bytesManyLines)

        // Through readImport, from the bytes: the decoding and the parse together stay bounded too.
        val bytes = oneLine.toByteArray(Charsets.UTF_8)
        var result: ReadResult? = null
        val bytesRead = allocated { result = readImport(bytes) }
        assertTrue(result is ReadResult.Failed)
        assertBelow(3L * IMPORT_MAX_BYTES, bytesRead)
    }

    @Test(timeout = 20_000)
    fun sixteenMegabytesOfBlankLinesAllocateNothingPerLine() {
        // A blank line makes no row and no list: 16 million of them, after a header, read as an empty file.
        val header = "name,url,username,password,note\n"
        for (eol in listOf("\n", "\r", "\r\n")) {
            val text = header + eol.repeat((IMPORT_MAX_BYTES - header.length) / eol.length)
            var rows: List<List<String>> = emptyList()
            val bytes = allocated { rows = parseCsv(text) }
            assertEquals(1, rows.size)
            // A list per line would be about 400 MiB of garbage; the header row is all there is.
            assertBelow(1024 * 1024, bytes)
        }
        assertTrue((readImport(("\n".repeat(IMPORT_MAX_BYTES - 40) + "name,url,username,password,note").toByteArray()) as ReadResult.Ok).read.items.isEmpty())
    }

    @Test(timeout = 20_000)
    fun aHugeQuotedFieldIsReadInLinearTime() {
        // 16 MiB of doubled quotes inside one field: one pass, nothing built past the cap.
        val text = "a,b\n\"" + "\"\"".repeat(IMPORT_MAX_BYTES / 2) + "\",x\n"
        val rows = parseCsv(text)
        assertEquals(CSV_MAX_FIELD_CHARS, rows[1][0].length)
        assertEquals("x", rows[1][1])
        // And one unclosed quote at the start of a 16 MiB file fails after one pass, not by trying every split.
        assertEquals(IMPORT_CSV_UNCLOSED_QUOTE, failure { parseCsv("a\n\"" + "x,\n".repeat(IMPORT_MAX_BYTES / 3)) })
    }
}
