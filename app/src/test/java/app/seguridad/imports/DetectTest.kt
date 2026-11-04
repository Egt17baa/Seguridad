package app.seguridad.imports

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectTest {
    private fun failed(r: ReadResult): String {
        assertTrue("$r", r is ReadResult.Failed)
        return (r as ReadResult.Failed).message
    }

    @Test(timeout = 5_000)
    fun over16MbIsRefused() {
        // Invalid UTF-8 everywhere: the size is refused before any decoding, so the answer is the size, not the text.
        val bytes = ByteArray(IMPORT_MAX_BYTES + 1) { 0xFF.toByte() }
        assertEquals(IMPORT_FILE_TOO_BIG, failed(readImport(bytes)))
        assertEquals("El archivo pasa de 16 MB. Clavero no lo lee.", IMPORT_FILE_TOO_BIG)
    }

    @Test fun exactly16MbIsReadForItsContent() {
        assertEquals(IMPORT_UNKNOWN, failed(readImport(ByteArray(IMPORT_MAX_BYTES) { ' '.code.toByte() })))
    }

    @Test fun unknownFileSaysSo() {
        assertEquals("Clavero no reconoce este archivo.", failed(readImport("hola\nmundo\n".toByteArray())))
        assertEquals(IMPORT_UNKNOWN, failed(readImport(ByteArray(0))))
        // A text whose first line is broken is not a format Clavero knows either.
        assertEquals(IMPORT_UNKNOWN, failed(readImport("\"sin cerrar\nname,url\n".toByteArray())))
    }

    @Test fun invalidTextIsDamaged() {
        assertEquals(IMPORT_NOT_TEXT, failed(readImport(byteArrayOf(0x6E, 0xC3.toByte(), 0x28))))
    }

    @Test fun itemCountOverLimitIsRefused() {
        val header = "name,url,username,password,note\n"
        val atLimit = readImport((header + "w,,a,p,\n".repeat(IMPORT_MAX_ITEMS)).toByteArray())
        assertEquals(IMPORT_MAX_ITEMS, (atLimit as ReadResult.Ok).read.items.size)
        val over = readImport((header + "w,,a,p,\n".repeat(IMPORT_MAX_ITEMS + 1)).toByteArray())
        assertEquals(IMPORT_TOO_MANY_ITEMS, failed(over))
    }

    @Test fun aBrokenCsvOfAKnownFormatIsAFailureNotACrash() {
        assertEquals(IMPORT_CSV_UNCLOSED_QUOTE, failed(readImport("name,url,username,password,note\nw,,a,\"p\n".toByteArray())))
        assertEquals(IMPORT_CSV_TOO_MANY_COLUMNS, failed(readImport(("name,url,username,password,note\n" + ",".repeat(100)).toByteArray())))
    }

    @Test fun readerExceptionsNeverEscape() {
        val secret = "clave-del-archivo-123"
        val failure: TextReader = { _, _ -> throw ImportFailure(IMPORT_CSV_UNCLOSED_QUOTE) }
        assertEquals(IMPORT_CSV_UNCLOSED_QUOTE, failed(readImport("x".toByteArray(), null, listOf(failure))))
        // Any other exception becomes the generic message: its own text could carry a value from the file.
        for (e in listOf(IllegalStateException(secret), NumberFormatException("For input string: $secret"), IndexOutOfBoundsException(secret))) {
            val r = readImport("x".toByteArray(), null, listOf { _, _ -> throw e })
            assertEquals(IMPORT_BROKEN, failed(r))
            assertFalse(failed(r).contains(secret))
        }
    }

    @Test fun theFirstReaderThatKnowsTheFileAnswers() {
        val read = ImportRead(ImportFormat.OTPAUTH, emptyList(), emptyList(), 0)
        val readers: List<TextReader> = listOf({ _, _ -> null }, { _, _ -> ReadResult.Ok(read) }, { _, _ -> error("not reached") })
        assertEquals(ReadResult.Ok(read), readImport("x".toByteArray(), null, readers))
        assertEquals(IMPORT_UNKNOWN, failed(readImport("x".toByteArray(), null, listOf { _, _ -> null })))
    }

    @Test fun utf16CsvWithBomIsRead() {
        val text = "name,url,username,password,note\nweb,,ana,clave,\n"
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + text.toByteArray(Charsets.UTF_16LE)
        assertEquals("clave", ((readImport(bytes) as ReadResult.Ok).read.items.single().entry.password))
    }
}
