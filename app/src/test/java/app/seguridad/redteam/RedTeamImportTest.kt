package app.seguridad.redteam

import app.seguridad.crypto.parseOtpMigration
import app.seguridad.imports.DuplicateChoice
import app.seguridad.imports.IMPORT_CSV_TOO_MANY_COLUMNS
import app.seguridad.imports.IMPORT_CSV_UNCLOSED_QUOTE
import app.seguridad.imports.IMPORT_HTML
import app.seguridad.imports.IMPORT_JSON_TOO_DEEP
import app.seguridad.imports.IMPORT_JSON_TOO_MANY_VALUES
import app.seguridad.imports.IMPORT_KDF_TOO_BIG
import app.seguridad.imports.IMPORT_NOT_TEXT
import app.seguridad.imports.IMPORT_TOO_MANY_ITEMS
import app.seguridad.imports.IMPORT_ZIP
import app.seguridad.imports.ImportFailure
import app.seguridad.imports.ImportFormat
import app.seguridad.imports.ImportRead
import app.seguridad.imports.ImportReadBuilder
import app.seguridad.imports.ImportedItem
import app.seguridad.imports.ReadResult
import app.seguridad.imports.checkScrypt
import app.seguridad.imports.importedEntry
import app.seguridad.imports.mergedEntries
import app.seguridad.imports.planMerge
import app.seguridad.imports.plainReportText
import app.seguridad.imports.readImport
import app.seguridad.imports.urlHost
import app.seguridad.tools.qr.parseLink
import app.seguridad.vault.EntryType
import app.seguridad.vault.VaultEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Ignore
import org.junit.Test
import java.text.Normalizer
import java.time.ZoneOffset
import java.util.Base64

/*
 * Red team of Clavero 1.4.0 (owner's decision (f), 8-11-2026): attacks on «Importar de otra app» with a file the
 * attacker gets the user to import. Report: docs/superpowers/handoff/sdd-1.4.0/redteam-report.md.
 *
 * A test named rtN_… asserts the SAFE behaviour: it fails while finding RT-N is open, and is @Ignore'd only so the
 * branch gate stays green. A test named resists… documents an attack the app resists today (a regression test).
 */
class RedTeamImportTest {
    private fun failed(r: ReadResult): String = (r as? ReadResult.Failed)?.message ?: error("not refused: $r")

    private fun millis(block: () -> Unit): Long {
        val t = System.nanoTime()
        block()
        return (System.nanoTime() - t) / 1_000_000
    }

    private fun vaultLogin(title: String, user: String, url: String, password: String = "la-buena") =
        VaultEntry("v1", EntryType.LOGIN, title, username = user, password = password, url = url, createdAt = 1, updatedAt = 1)

    private fun fileLogin(index: Int, title: String, user: String, url: String, password: String = "la-del-atacante") =
        ImportedItem(index, importedEntry(EntryType.LOGIN, title, username = user, password = password, url = url), emptyList())

    private fun read(vararg items: ImportedItem) = ImportRead(ImportFormat.BROWSER_CSV, items.toList(), emptyList(), 0)

    // ---------------------------------------------------------------------------------------------------------------
    // RT-1: the merge's host (urlHost) and a browser disagree on «\»: a URL of another site is taken for a duplicate.
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * RT-1. A browser (WHATWG URL, and Clavero's own QR parser that follows it, `parseLink`) reads «\» as «/» in an
     * http(s) link, so `https://evil.example\@banco.es/` goes to evil.example. The merge's `urlHost` stops the authority
     * only at «/?#» and takes what follows the last «@»: banco.es. The file's entry is then a DUPLICATE of the vault's
     * «Banco», and the preview row shows only the user («ana»), not the URL. With «Reemplazar» (one tap for all), the
     * vault entry silently takes the attacker's URL and password.
     */
    @Ignore("RT-1: open, see report")
    @Test fun rt1_aUrlThatGoesToAnotherSiteIsNotADuplicate() {
        val evil = "https://evil.example\\@banco.es/login"
        assertEquals("a browser goes to evil.example", "evil.example", parseLink(evil).host)
        assertNotEquals("urlHost must agree with the browser", urlHost("https://banco.es/"), urlHost(evil))
        val plan = planMerge(read(fileLogin(0, "Banco", "ana", evil)), listOf(vaultLogin("Banco", "ana", "https://banco.es/")), revision = 1)
        assertTrue("the file's entry must not be a duplicate of banco.es", plan.duplicates.isEmpty())
    }

    /** What RT-1 lets through today (passes while open; documents the impact for the report). */
    @Test fun rt1Impact_replaceTakesTheAttackersUrlAndPassword() {
        val evil = "https://evil.example\\@banco.es/login"
        val vault = vaultLogin("Banco", "ana", "https://banco.es/")
        val plan = planMerge(read(fileLogin(0, "Banco", "ana", evil)), listOf(vault), revision = 1)
        if (plan.duplicates.isEmpty()) return // RT-1 fixed: nothing to show.
        val merged = mergedEntries(plan, DuplicateChoice.REPLACE, emptyMap(), listOf(vault), now = 2, newId = { "n" }, zone = ZoneOffset.UTC)
        assertEquals(1, merged.size)
        assertEquals(evil, merged[0].url)
        assertEquals("la-del-atacante", merged[0].password)
        assertEquals("la-buena", merged[0].passwordHistory.single().password)
    }

    // ---------------------------------------------------------------------------------------------------------------
    // RT-2: entries that look the same are not duplicates (invisible characters, decomposed accents).
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * RT-2. Every screen shows a title through `plainReportText`, which drops invisible characters, and the font draws
     * «é» the same whether it is one code point or «e» + U+0301. The merge compares titles and users raw (trim and
     * lower case only), so «Ban\u200Bco» / «ana» and «Cafe\u0301» enter as NEW entries next to «Banco» / «Café», shown
     * exactly like them: a shadow entry with the attacker's password or 2FA that the user cannot tell apart.
     */
    @Ignore("RT-2: open, see report")
    @Test fun rt2_aTitleOrUserThatLooksTheSameIsADuplicate() {
        val cases = listOf(
            Triple("Banco", "Ban\u200Bco", "ana"),
            Triple("Banco", "Banco\u2060", "ana"),
            Triple("Café", "Cafe\u0301", "ana"),
            Triple("Banco", "Banco", "an\u00ADa"),
        )
        for ((vaultTitle, fileTitle, fileUser) in cases) {
            val vault = vaultLogin(vaultTitle, "ana", "https://banco.es/")
            assertEquals(
                "shown the same",
                Normalizer.normalize(plainReportText(vault.title + vault.username), Normalizer.Form.NFC),
                Normalizer.normalize(plainReportText(fileTitle + fileUser), Normalizer.Form.NFC),
            )
            val plan = planMerge(read(fileLogin(0, fileTitle, fileUser, "https://banco.es/")), listOf(vault), revision = 1)
            assertEquals("«$fileTitle» / «$fileUser» must be a duplicate of «$vaultTitle»", 1, plan.duplicates.size)
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Attacks resisted: memory and CPU bombs, disguised files, injection through the parts of a 2FA code.
    // ---------------------------------------------------------------------------------------------------------------

    @Test fun resistsJsonNestingBomb() {
        val bomb = ("{\"items\":" + "[".repeat(1_000_000)).toByteArray()
        val ms = millis { assertEquals(IMPORT_JSON_TOO_DEEP, failed(readImport(bomb))) }
        assertTrue("$ms ms", ms < 5_000)
    }

    @Test fun resistsBracketsHiddenInStringsAndEscapes() {
        // A bracket in a string, after «\"» or after «\\» must be read as the parser reads it, both ways.
        val inString = "{\"items\":[],\"x\":\"" + "[".repeat(200) + "\"}"
        assertTrue(readImport(inString.toByteArray()) !is ReadResult.Failed || failed(readImport(inString.toByteArray())) != IMPORT_JSON_TOO_DEEP)
        val afterEscapedBackslash = "{\"items\":[],\"x\":\"\\\\\"," + "\"y\":" + "[".repeat(100) + "]".repeat(100) + "}"
        assertEquals(IMPORT_JSON_TOO_DEEP, failed(readImport(afterEscapedBackslash.toByteArray())))
    }

    @Test fun resistsJsonTreeBomb() {
        // 15 MB of «0,»: about 7.5 million values, a tree of more than 600 MB if it were built.
        val sb = StringBuilder(15_000_100).append("{\"items\":[")
        repeat(7_400_000) { sb.append("0,") }
        sb.append("0]}")
        val ms = millis { assertEquals(IMPORT_JSON_TOO_MANY_VALUES, failed(readImport(sb.toString().toByteArray()))) }
        assertTrue("$ms ms", ms < 10_000)
    }

    @Test fun resistsCsvColumnAndRowFloods() {
        val header = "name,url,username,password,note\n"
        assertEquals(IMPORT_CSV_TOO_MANY_COLUMNS, failed(readImport((header + ",".repeat(5_000_000)).toByteArray())))
        assertEquals(IMPORT_TOO_MANY_ITEMS, failed(readImport((header + "a,b,c,d,e\n".repeat(20_001)).toByteArray())))
        assertEquals(IMPORT_CSV_UNCLOSED_QUOTE, failed(readImport((header + "\"" + "x".repeat(1_000_000)).toByteArray())))
    }

    @Test fun resistsDisguisedFiles() {
        assertEquals(IMPORT_ZIP, failed(readImport(byteArrayOf(0x50, 0x4B, 0x03, 0x04) + "name,url,username,password,note".toByteArray())))
        assertEquals(IMPORT_HTML, failed(readImport("\uFEFF  <!DOCTYPE html><script>alert(1)</script>".toByteArray())))
        // Overlong UTF-8 «/» (0xC0 0xAF) and a surrogate written as UTF-8: refused, never replaced and read.
        assertEquals(IMPORT_NOT_TEXT, failed(readImport("name,url,username,password,note\n".toByteArray() + byteArrayOf(0xC0.toByte(), 0xAF.toByte()))))
        assertEquals(IMPORT_NOT_TEXT, failed(readImport("name\n".toByteArray() + byteArrayOf(0xED.toByte(), 0xA0.toByte(), 0x80.toByte()))))
    }

    private fun bitwardenProtected(kdf: String) = """
        {"encrypted":true,"passwordProtected":true,"salt":"c2FsdA==",$kdf,
         "encKeyValidation_DO_NOT_EDIT":"2.AAAAAAAAAAAAAAAAAAAAAA==|AAAAAAAAAAAAAAAAAAAAAA==|AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
         "data":"2.AAAAAAAAAAAAAAAAAAAAAA==|AAAAAAAAAAAAAAAAAAAAAA==|AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="}
    """.trimIndent().toByteArray()

    @Test fun resistsKdfBombsBeforeAnyWork() {
        val pw = "x".toCharArray()
        val ms = millis {
            assertEquals(IMPORT_KDF_TOO_BIG, failed(readImport(bitwardenProtected("\"kdfType\":0,\"kdfIterations\":2000000000"), pw)))
            assertEquals(IMPORT_KDF_TOO_BIG, failed(readImport(bitwardenProtected("\"kdfType\":0,\"kdfIterations\":1"), pw)))
            assertEquals(
                IMPORT_KDF_TOO_BIG,
                failed(readImport(bitwardenProtected("\"kdfType\":1,\"kdfIterations\":3,\"kdfMemory\":4096,\"kdfParallelism\":4"), pw)),
            )
            assertEquals(
                IMPORT_KDF_TOO_BIG,
                failed(readImport(bitwardenProtected("\"kdfType\":1,\"kdfIterations\":1000,\"kdfMemory\":64,\"kdfParallelism\":4"), pw)),
            )
            assertEquals(
                IMPORT_KDF_TOO_BIG,
                failed(readImport(bitwardenProtected("\"kdfType\":1,\"kdfIterations\":3,\"kdfMemory\":64,\"kdfParallelism\":64"), pw)),
            )
            // A number past Int, or written as a fraction or exponent, never becomes a valid count.
            assertTrue(readImport(bitwardenProtected("\"kdfType\":0,\"kdfIterations\":6e5"), pw) is ReadResult.Failed)
            assertTrue(readImport(bitwardenProtected("\"kdfType\":0,\"kdfIterations\":4294967296600000"), pw) is ReadResult.Failed)
        }
        assertTrue("refused before any derivation: $ms ms", ms < 2_000)
        // scrypt (Aegis): 2 GiB, a non power of two, the RFC 7914 bound with r = 1, p past the bound.
        for ((n, r, p) in listOf(Triple(1 shl 20, 16, 1), Triple((1 shl 15) + 1, 8, 1), Triple(1 shl 16, 1, 1), Triple(1 shl 15, 8, 64), Triple(Int.MIN_VALUE, 8, 1))) {
            try {
                checkScrypt(n, r, p)
                fail("scrypt n=$n r=$r p=$p accepted")
            } catch (e: ImportFailure) {
                assertEquals(IMPORT_KDF_TOO_BIG, e.message)
            }
        }
    }

    @Test fun resistsOtpMigrationLengthAndVarintTricks() {
        fun link(bytes: ByteArray) = "otpauth-migration://offline?data=" + Base64.getEncoder().encodeToString(bytes)
        // Field 1 with a length of 2^35 (a varint that would overflow an Int), and an 11-byte varint.
        assertNull(parseOtpMigration(link(byteArrayOf(0x0A, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x7F))))
        assertNull(parseOtpMigration(link(byteArrayOf(0x10) + ByteArray(10) { 0xFF.toByte() } + byteArrayOf(0x01))))
        // A group (wire type 3) and field number 0.
        assertNull(parseOtpMigration(link(byteArrayOf(0x0B))))
        assertNull(parseOtpMigration(link(byteArrayOf(0x00, 0x00))))
        // 101 empty accounts: past the limit of one QR.
        assertNull(parseOtpMigration(link(ByteArray(202) { if (it % 2 == 0) 0x0A else 0x00 })))
        // A negative length.
        assertNull(parseOtpMigration(link(byteArrayOf(0x0A) + ByteArray(9) { 0xFF.toByte() } + byteArrayOf(0x01))))
    }

    @Test fun resistsSettingsInjectedThroughTheNamesOfA2faCode() {
        val b = ImportReadBuilder(ImportFormat.AEGIS)
        b.addTotp(
            secret = "JBSWY3DPEHPK3PXP",
            issuer = "Banco&digits=10&period=1&secret=AAAAAAAAAAAAAAAA",
            account = "ana?algorithm=MD5#x",
            algorithm = null,
            digits = null,
            period = null,
            title = "Banco",
        )
        val t = b.build().items.single().entry.totp!!
        assertEquals("JBSWY3DPEHPK3PXP", t.secret)
        assertEquals("Banco&digits=10&period=1&secret=AAAAAAAAAAAAAAAA", t.issuer)
        assertEquals("ana?algorithm=MD5#x", t.account)
        assertEquals(6, t.digits)
        assertEquals(30, t.period)
        assertEquals("SHA1", t.algorithm)
    }

    @Test fun resistsRtlAndInvisibleCharactersInWhatIsShown() {
        // What the attacker's file names an entry is shown without the bidi controls and the invisible characters.
        val shown = plainReportText("moc.\u202Eknab\u202C\u200B\u2066x\u2069\uFEFF\u00AD" + "e" + "\u0301".repeat(50) + "\u0000\n")
        assertTrue(shown.none { Character.getType(it) == Character.FORMAT.toInt() || it < ' ' })
        assertTrue("a tower of accents is cut", shown.count { it == '\u0301' } <= 3)
    }
}
