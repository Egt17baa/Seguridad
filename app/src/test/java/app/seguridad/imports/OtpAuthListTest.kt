package app.seguridad.imports

import app.seguridad.crypto.MIGRATION_MAX_PARAMETERS
import app.seguridad.crypto.OTPAUTH_MAX_NAME_CHARS
import app.seguridad.vault.EntryType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.Base64

/** A minimal protobuf writer for the migration payload, independent of the reader under test. */
private class Proto {
    val out = ByteArrayOutputStream()
    fun varint(v: Long): Proto {
        var x = v
        while (x and 0x7FL.inv() != 0L) {
            out.write(((x and 0x7F) or 0x80).toInt())
            x = x ushr 7
        }
        out.write(x.toInt())
        return this
    }
    fun int(field: Int, v: Long) = varint(((field shl 3) or 0).toLong()).varint(v)
    fun bytes(field: Int, b: ByteArray): Proto {
        varint(((field shl 3) or 2).toLong()).varint(b.size.toLong())
        out.write(b)
        return this
    }
    fun str(field: Int, s: String) = bytes(field, s.toByteArray())
    fun msg(field: Int, m: Proto) = bytes(field, m.out.toByteArray())
}

private val KEY = "12345678901234567890".toByteArray()

/** One account: [type] 2 is TOTP and 1 HOTP; [alg] 1 is SHA-1 and 4 MD5. */
private fun account(issuer: String, name: String, secret: ByteArray = KEY, alg: Long = 1, type: Long = 2) =
    Proto().bytes(1, secret).str(2, name).str(3, issuer).int(4, alg).int(5, 1).int(6, type)

private fun migration(vararg accounts: Proto): String {
    val p = Proto()
    accounts.forEach { p.msg(1, it) }
    return "otpauth-migration://offline?data=" + java.net.URLEncoder.encode(Base64.getEncoder().encodeToString(p.out.toByteArray()), "UTF-8")
}

class OtpAuthListTest {
    private fun fixture(): ByteArray =
        requireNotNull(javaClass.getResourceAsStream("/import/otpauth/lista.txt")).use { it.readBytes() }

    private fun ok(bytes: ByteArray): ImportRead {
        val r = readImport(bytes)
        assertTrue("$r", r is ReadResult.Ok)
        return (r as ReadResult.Ok).read
    }

    private fun ok(text: String) = ok(text.toByteArray())

    private fun failed(text: String): String {
        val r = readImport(text.toByteArray())
        assertTrue("$r", r is ReadResult.Failed)
        return (r as ReadResult.Failed).message
    }

    @Test fun oneLinkPerLineAndBlankLinesAreIgnored() {
        val read = ok(fixture())
        assertEquals(ImportFormat.OTPAUTH, read.format)
        assertEquals("importado-otpauth", read.format.originTag)
        assertEquals(listOf("Prueba SHA1", "Prueba SHA256", "Entre comillas"), read.items.map { it.entry.title })
        assertTrue(read.items.all { it.entry.type == EntryType.TOTP && it.folders.isEmpty() })
        val (a, b, c) = read.items.map { it.entry.totp!! }
        assertEquals(listOf("Prueba SHA1", "ana.prueba@example.com"), listOf(a.issuer, a.account))
        assertEquals(listOf("SHA256", 8, 60), listOf(b.algorithm, b.digits, b.period))
        // A line between quotes (a CSV of one column) is the link inside them.
        assertEquals("JBSWY3DPEHPK3PXP", c.secret)
        // An HOTP, a code of 10 digits and a line that is not a link; the last one is named by its line (8, CRLF).
        assertEquals(
            listOf("Prueba HOTP" to SkipReason.NOT_TOTP, "Diez" to SkipReason.TOTP_UNSUPPORTED, "Línea 8" to SkipReason.BROKEN),
            read.skipped.map { it.title to it.reason },
        )
    }

    @Test fun lineEndingsOfEveryKind() {
        val link = "otpauth://totp/A?secret=JBSWY3DPEHPK3PXP"
        for (sep in listOf("\n", "\r\n", "\r")) assertEquals(sep, 3, ok("$link$sep$link$sep$sep  \t$sep$link").items.size)
    }

    @Test fun migrationLinesAreExpanded() {
        val text = "otpauth://totp/Uno?secret=JBSWY3DPEHPK3PXP\n" +
            migration(account("Dos", "Dos:ana"), account("Tres", "Tres:luis")) + "\n" +
            migration(account("Hotp", "Hotp:a", type = 1), account("Viejo", "Viejo:b", alg = 4), account("Corta", "Corta:c", secret = ByteArray(9) { 1 })) + "\n"
        val read = ok(text)
        assertEquals(listOf("Uno", "Dos", "Tres"), read.items.map { it.entry.title })
        assertEquals(listOf("ana", "luis"), read.items.drop(1).map { it.entry.totp!!.account })
        assertEquals(
            listOf("Hotp" to SkipReason.NOT_TOTP, "Viejo" to SkipReason.TOTP_UNSUPPORTED, "Corta" to SkipReason.BROKEN),
            read.skipped.map { it.title to it.reason },
        )
        val all = read.items.map { it.index } + read.skipped.map { it.index }
        assertEquals((0 until all.size).toList(), all.sorted())
    }

    @Test fun aNameTooLongForTheCodeIsNeverCut() {
        val long = "x".repeat(OTPAUTH_MAX_NAME_CHARS + 1)
        val read = ok(
            "otpauth://totp/Corto:ana?secret=JBSWY3DPEHPK3PXP\n" +
                "otpauth://totp/$long?secret=JBSWY3DPEHPK3PXP\n" +
                "otpauth://totp/a?secret=JBSWY3DPEHPK3PXP&issuer=$long\n" +
                migration(account("Dos", "Dos:ana"), account("", "$long:b"), account(long, "x:c")) + "\n",
        )
        assertEquals(listOf("Corto", "Dos"), read.items.map { it.entry.title })
        assertEquals(List(4) { SkipReason.FIELD_TOO_LONG }, read.skipped.map { it.reason })
    }

    @Test fun aBrokenMigrationLineIsBroken() {
        val read = ok("otpauth://totp/Uno?secret=JBSWY3DPEHPK3PXP\notpauth-migration://offline?data=%%%\n")
        assertEquals(listOf("Línea 2" to SkipReason.BROKEN), read.skipped.map { it.title to it.reason })
    }

    @Test fun aMigrationLineCanStartTheList() {
        assertEquals(listOf("Dos"), ok(migration(account("Dos", "Dos:ana"))).items.map { it.entry.title })
    }

    @Test(timeout = 10_000)
    fun migrationExpansionIsBoundedEarly() {
        // Each line holds the most accounts a QR may have; 201 lines would give 20 100 codes.
        val line = migration(*Array(MIGRATION_MAX_PARAMETERS) { account("E$it", "E$it:a") })
        assertEquals(IMPORT_TOO_MANY_ITEMS, failed(List(IMPORT_MAX_ITEMS / MIGRATION_MAX_PARAMETERS + 1) { line }.joinToString("\n")))
        assertEquals(IMPORT_MAX_ITEMS, ok(List(IMPORT_MAX_ITEMS / MIGRATION_MAX_PARAMETERS) { line }.joinToString("\n")).items.size)
    }

    @Test(timeout = 10_000)
    fun lineCountAndLineLengthAreBounded() {
        val link = "otpauth://totp/A?secret=JBSWY3DPEHPK3PXP"
        assertEquals(IMPORT_TOO_MANY_ITEMS, failed(List(IMPORT_MAX_ITEMS + 1) { link }.joinToString("\n")))
        // Blank lines do not count.
        assertEquals(1, ok(link + "\n".repeat(1_000_000)).items.size)
        // A line too long is listed, never read or cut, whatever its length.
        val read = ok(link + "\notpauth://totp/B?secret=JBSWY3DPEHPK3PXP&x=" + "a".repeat(5_000_000) + "\n" + "otpauth-migration://" + "b".repeat(100_000))
        assertEquals(listOf("Línea 2" to SkipReason.FIELD_TOO_LONG, "Línea 3" to SkipReason.FIELD_TOO_LONG), read.skipped.map { it.title to it.reason })
    }

    @Test fun aFileThatDoesNotStartWithALinkIsNotAList() {
        assertEquals(IMPORT_UNKNOWN, failed("hola\notpauth://totp/A?secret=JBSWY3DPEHPK3PXP\n"))
        assertEquals(IMPORT_UNKNOWN, failed("otpauthx://totp/A?secret=JBSWY3DPEHPK3PXP\n"))
        // Leading blank lines and spaces are passed over.
        assertEquals(1, ok("\n\n   otpauth://totp/A?secret=JBSWY3DPEHPK3PXP  \n").items.size)
        assertEquals(1, ok("OTPAUTH://TOTP/A?secret=JBSWY3DPEHPK3PXP").items.size)
    }

    @Test fun hostileLinesAreData() {
        val read = ok(
            "otpauth://totp/A?secret=JBSWY3DPEHPK3PXP\njavascript:alert(1)\nintent://x#Intent;end\n\"otpauth://totp/B?secret=JBSWY3DPEHPK3PXP\n" +
                "otpauth://totp/C%E2%80%AE?secret=JBSWY3DPEHPK3PXP\notpauth://totp/x\u0000y?secret=JBSWY3DPEHPK3PXP\n",
        )
        assertEquals(listOf("A", "C"), read.items.map { it.entry.totp!!.let { t -> t.issuer.ifEmpty { t.account } } })
        assertEquals(List(4) { SkipReason.BROKEN }, read.skipped.map { it.reason })
        // No skipped line names its secret.
        assertFalse(read.skipped.any { "JBSWY3DPEHPK3PXP" in it.title })
    }
}
