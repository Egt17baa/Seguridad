package app.seguridad.imports

import app.seguridad.crypto.OTPAUTH_MAX_NAME_CHARS
import app.seguridad.vault.EntryType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The plain Aegis backup, from its public documentation; the fixture is hand-made with invented data. */
class AegisJsonTest {
    private fun fixture(): ByteArray =
        requireNotNull(javaClass.getResourceAsStream("/import/aegis/aegis-claro.json")).use { it.readBytes() }

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

    /** A plain backup with [entries] (JSON objects, already written) and one group «g1» named «Trabajo». */
    private fun backup(vararg entries: String) =
        """{"version":1,"header":{"slots":null,"params":null},"db":{"version":3,"entries":[${entries.joinToString(",")}],""" +
            """"groups":[{"uuid":"g1","name":"Trabajo"}]}}"""

    private fun totp(issuer: String = "Emisor", info: String = """{"secret":"JBSWY3DPEHPK3PXP","algo":"SHA1","digits":6,"period":30}""", extra: String = "") =
        """{"type":"totp","uuid":"u","name":"cuenta","issuer":"$issuer","note":"","favorite":false,"info":$info$extra}"""

    @Test fun totpEntersWithItsIssuerAndAccount() {
        val read = ok(fixture())
        assertEquals(ImportFormat.AEGIS, read.format)
        assertEquals("importado-aegis", read.format.originTag)
        assertEquals(listOf("Prueba SHA1", "Prueba SHA256"), read.items.map { it.entry.title })
        assertTrue(read.items.all { it.entry.type == EntryType.TOTP })
        val (a, b) = read.items.map { it.entry.totp!! }
        assertEquals(listOf("JBSWY3DPEHPK3PXP", "Prueba SHA1", "ana.prueba@example.com", "SHA1", 6, 30), listOf(a.secret, a.issuer, a.account, a.algorithm, a.digits, a.period))
        assertEquals(listOf("SHA256", 8, 60), listOf(b.algorithm, b.digits, b.period))
        assertEquals("Nota de la entrada", read.items[0].entry.notes)
        assertEquals(0, read.becameNotes)
    }

    @Test fun otherCodeTypesAreNotTotp() {
        val read = ok(fixture())
        assertEquals(
            listOf(
                "Prueba HOTP" to SkipReason.NOT_TOTP,
                "Prueba Steam" to SkipReason.NOT_TOTP,
                "Prueba mOTP" to SkipReason.NOT_TOTP,
                "Prueba Yandex" to SkipReason.NOT_TOTP,
                // A TOTP with 10 digits is refused, not changed to 6.
                "Prueba diez cifras" to SkipReason.TOTP_UNSUPPORTED,
            ),
            read.skipped.map { it.title to it.reason },
        )
        // A type Clavero does not know is not TOTP either.
        assertEquals(SkipReason.NOT_TOTP, ok(backup("""{"type":"nuevo","name":"x","info":{}}""")).skipped.single().reason)
    }

    @Test fun groupsAreFolders() {
        val read = ok(fixture())
        assertEquals(listOf(listOf("Trabajo"), emptyList()), read.items.map { it.folders })
        // Several groups, an unknown uuid (no folder) and the older «group» name of version 1 and 2 backups.
        val r2 = ok(backup(
            totp(extra = ""","groups":["g1","otro","g1"]"""),
            totp(extra = ""","group":"Antiguo""""),
        ))
        assertEquals(listOf(listOf("Trabajo"), listOf("Antiguo")), r2.items.map { it.folders })
    }

    @Test fun aGroupNameWithANulOrALoneSurrogateGivesNoFolder() {
        val text = """{"version":1,"header":{},"db":{"version":3,"groups":[{"uuid":"g1","name":"a\u0000"},{"uuid":"g2","name":"\ud800"}],""" +
            """"entries":[""" + totp(extra = ""","groups":["g1","g2"]""") + "," + totp(extra = ""","group":"x\udc00"""") + "]}}"
        val read = ok(text)
        assertEquals(2, read.items.size)
        assertTrue(read.items.all { it.folders.isEmpty() })
    }

    @Test fun anIssuerOrAccountTooLongForTheCodeIsNeverCut() {
        val emoji = "x".repeat(OTPAUTH_MAX_NAME_CHARS - 1) + "🛒" // 256 code points, 257 UTF-16 units
        val read = ok(backup(
            totp(issuer = "i".repeat(OTPAUTH_MAX_NAME_CHARS)),
            totp(issuer = emoji),
            totp(issuer = "i".repeat(OTPAUTH_MAX_NAME_CHARS + 1)),
            totp(issuer = emoji + "y"),
            """{"type":"totp","name":"${"c".repeat(OTPAUTH_MAX_NAME_CHARS + 1)}","issuer":"largo","info":{"secret":"JBSWY3DPEHPK3PXP"}}""",
        ))
        // At the bound they enter whole: the emoji is not split into a lone surrogate.
        assertEquals(listOf("i".repeat(OTPAUTH_MAX_NAME_CHARS), emoji), read.items.map { it.entry.totp!!.issuer })
        assertTrue(Character.isLowSurrogate(read.items[1].entry.totp!!.issuer.last()))
        // Past it the code would keep them cut: listed instead, never imported cut.
        assertEquals(List(3) { SkipReason.FIELD_TOO_LONG }, read.skipped.map { it.reason })
    }

    @Test fun favoriteIsKept() {
        val read = ok(fixture())
        assertEquals(listOf(true, false), read.items.map { it.entry.favorite })
    }

    @Test fun titleFallsBackToTheAccountThenTheType() {
        val read = ok(backup(
            """{"type":"totp","name":"solo cuenta","issuer":"","info":{"secret":"JBSWY3DPEHPK3PXP"}}""",
            """{"type":"totp","info":{"secret":"JBSWY3DPEHPK3PXP"}}""",
        ))
        assertEquals(listOf("solo cuenta", "Código 2FA"), read.items.map { it.entry.title })
    }

    @Test fun aMissingSettingTakesTheDefaultAndAPresentOneIsNeverChanged() {
        val read = ok(backup(
            totp(info = """{"secret":"JBSWY3DPEHPK3PXP"}"""),
            totp(info = """{"secret":"JBSWY3DPEHPK3PXP","algo":"MD5"}"""),
            totp(info = """{"secret":"JBSWY3DPEHPK3PXP","algo":""}"""),
            totp(info = """{"secret":"JBSWY3DPEHPK3PXP","period":5}"""),
            totp(info = """{"secret":"JBSWY3DPEHPK3PXP","digits":6.5}"""),
            totp(info = """{"secret":"JBSWY3DPEHPK3PXP","digits":123456789012345678901234567890}"""),
            totp(info = """{"secret":"JBSWY3DPEHPK3PXP","period":1e999}"""),
        ))
        val s = read.items.single().entry.totp!!
        assertEquals(listOf("SHA1", 6, 30), listOf(s.algorithm, s.digits, s.period))
        assertEquals(List(6) { SkipReason.TOTP_UNSUPPORTED }, read.skipped.map { it.reason })
    }

    @Test fun encryptedAegisNeedsPassword() {
        val text = """{"version":1,"header":{"slots":[{"type":1,"uuid":"x","key":"00","key_params":{"nonce":"00","tag":"00"},""" +
            """"n":32768,"r":8,"p":1,"salt":"00"}],"params":{"nonce":"00","tag":"00"}},"db":"QUJD"}"""
        assertEquals(ReadResult.NeedsPassword(ImportFormat.AEGIS), readImport(text.toByteArray()))
    }

    @Test fun wrongTypesAreBroken() {
        val entries = listOf(
            totp(issuer = "digits texto", info = """{"secret":"JBSWY3DPEHPK3PXP","digits":"6"}"""),
            totp(issuer = "digits lógico", info = """{"secret":"JBSWY3DPEHPK3PXP","digits":true}"""),
            totp(issuer = "secret número", info = """{"secret":12345}"""),
            totp(issuer = "algo número", info = """{"secret":"JBSWY3DPEHPK3PXP","algo":1}"""),
            totp(issuer = "info texto", info = "\"x\""),
            totp(issuer = "info nula", info = "null"),
            """{"type":"totp","name":"cuenta","issuer":5,"info":{"secret":"JBSWY3DPEHPK3PXP"}}""",
            """{"type":"totp","name":"cuenta","issuer":"nota lista","note":[],"info":{"secret":"JBSWY3DPEHPK3PXP"}}""",
            """{"type":"totp","name":"cuenta","issuer":"favorito texto","favorite":"si","info":{"secret":"JBSWY3DPEHPK3PXP"}}""",
            """{"type":"totp","name":"cuenta","issuer":"grupos texto","groups":"g1","info":{"secret":"JBSWY3DPEHPK3PXP"}}""",
            """{"type":"totp","name":"cuenta","issuer":"grupo número","groups":[1],"info":{"secret":"JBSWY3DPEHPK3PXP"}}""",
            """{"type":7,"name":"cuenta","issuer":"tipo número"}""",
            """{"name":"cuenta","issuer":"sin tipo"}""",
            // An unquoted value is not a number: it can never add a setting to the link Clavero reads.
            totp(issuer = "inyección", info = """{"secret":"JBSWY3DPEHPK3PXP","digits":6&period=10}"""),
            totp(issuer = "clave rota", info = """{"secret":"no es base32!"}"""),
            "3",
        )
        val read = ok(backup(*entries.toTypedArray(), totp(issuer = "bien")))
        assertEquals(listOf("bien"), read.items.map { it.entry.title })
        assertEquals(List(entries.size) { SkipReason.BROKEN }, read.skipped.map { it.reason })
        assertEquals("digits texto", read.skipped[0].title)
    }

    @Test fun deepJsonIsRefused() {
        // Root (1), db (2), entries (3), the entry (4), then its unknown field.
        fun nested(total: Int) = backup(totp(extra = ""","x":""" + "[".repeat(total - 4) + "]".repeat(total - 4)))
        assertEquals(1, ok(nested(JSON_MAX_DEPTH)).items.size)
        assertEquals(IMPORT_JSON_TOO_DEEP, failed(nested(JSON_MAX_DEPTH + 1)))
    }

    @Test fun nulAndLoneSurrogatesAreBroken() {
        val read = ok(backup(
            totp(issuer = "a\\u0000b"),
            totp(issuer = "a\\ud800b"),
            totp(info = """{"secret":"JBSWY3DPEHPK3PXP\u0000"}"""),
        ))
        assertTrue(read.items.isEmpty())
        assertEquals(List(3) { SkipReason.BROKEN }, read.skipped.map { it.reason })
    }

    @Test fun fieldOverLimitIsSkipped() {
        val read = ok(backup(
            totp(issuer = "x".repeat(MAX_TITLE + 1)),
            """{"type":"totp","issuer":"nota","note":"${"x".repeat(MAX_NOTES + 1)}","info":{"secret":"JBSWY3DPEHPK3PXP"}}""",
            // A secret or names too long for a link are never cut.
            totp(info = """{"secret":"${"A".repeat(5000)}"}"""),
        ))
        assertTrue(read.items.isEmpty())
        assertEquals(List(3) { SkipReason.FIELD_TOO_LONG }, read.skipped.map { it.reason })
    }

    @Test fun hostileNamesAreStoredAsTheLinkReaderKeepsThem() {
        val read = ok(backup(totp(issuer = "Banco\\u202E&secret=AAAA")))
        val e = read.items.single().entry
        // The title is stored as read; the code's issuer goes through the same rules as a scanned link.
        assertEquals("Banco\u202E&secret=AAAA", e.title)
        assertEquals("Banco&secret=AAAA", e.totp?.issuer)
        assertEquals("JBSWY3DPEHPK3PXP", e.totp?.secret)
        // A «:» in the account never makes an issuer of its first part.
        val colon = ok(backup("""{"type":"totp","name":"a:b","issuer":"","info":{"secret":"JBSWY3DPEHPK3PXP"}}""")).items.single().entry.totp
        assertEquals("" to "a:b", colon?.issuer to colon?.account)
    }

    @Test fun anEntriesListThatIsNotAListIsDamaged() {
        assertEquals(IMPORT_BROKEN, failed("""{"version":1,"header":{},"db":{"entries":{}}}"""))
        assertEquals(IMPORT_BROKEN, failed("""{"version":1,"header":{},"db":5}"""))
    }

    @Test fun itemCountOverLimitIsRefused() {
        assertEquals(IMPORT_TOO_MANY_ITEMS, failed(backup(*Array(IMPORT_MAX_ITEMS + 1) { "{}" })))
    }

    @Test fun secretsNeverReachTheSkippedList() {
        val read = ok(fixture())
        assertFalse(read.skipped.any { "JBSWY3DPEHPK3PXP" in it.title || "0123456789abcdef" in it.title })
    }
}
