package app.seguridad.imports

import app.seguridad.vault.EntryType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The plain 2FAS backup, from its public documentation; the fixture is hand-made with invented data. */
class TwoFasJsonTest {
    private fun fixture(): ByteArray =
        requireNotNull(javaClass.getResourceAsStream("/import/2fas/2fas-claro.2fas")).use { it.readBytes() }

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

    /** A plain backup with [services] (JSON objects, already written) and one group «g1» named «Trabajo». */
    private fun backup(vararg services: String) =
        """{"services":[${services.joinToString(",")}],"groups":[{"id":"g1","name":"Trabajo"}],"schemaVersion":4}"""

    private fun service(name: String = "Servicio", otp: String = """{"account":"ana","digits":6,"period":30,"algorithm":"SHA1","tokenType":"TOTP"}""", extra: String = "") =
        """{"name":"$name","secret":"JBSWY3DPEHPK3PXP","otp":$otp$extra}"""

    @Test fun totpEntersWithItsIssuerAndAccount() {
        val read = ok(fixture())
        assertEquals(ImportFormat.TWOFAS, read.format)
        assertEquals("importado-2fas", read.format.originTag)
        assertEquals(listOf("Prueba SHA1", "Prueba SHA256"), read.items.map { it.entry.title })
        assertTrue(read.items.all { it.entry.type == EntryType.TOTP && !it.entry.favorite })
        val (a, b) = read.items.map { it.entry.totp!! }
        assertEquals(listOf("JBSWY3DPEHPK3PXP", "Prueba SHA1", "ana.prueba@example.com", "SHA1", 6, 30), listOf(a.secret, a.issuer, a.account, a.algorithm, a.digits, a.period))
        // Without an issuer, the service's name is the issuer.
        assertEquals(listOf("Prueba SHA256", "ana", "SHA256", 8, 60), listOf(b.issuer, b.account, b.algorithm, b.digits, b.period))
    }

    @Test fun hotpAndSteamAreNotTotp() {
        val read = ok(fixture())
        assertEquals(listOf("Prueba HOTP" to SkipReason.NOT_TOTP, "Prueba Steam" to SkipReason.NOT_TOTP), read.skipped.map { it.title to it.reason })
        assertEquals(SkipReason.NOT_TOTP, ok(backup(service(otp = """{"tokenType":"OTRO"}"""))).skipped.single().reason)
    }

    @Test fun groupIdIsTheFolder() {
        val read = ok(fixture())
        assertEquals(listOf(listOf("Trabajo"), emptyList()), read.items.map { it.folders })
        assertEquals(listOf(emptyList<String>()), ok(backup(service(extra = ""","groupId":"otro""""))).items.map { it.folders })
        assertEquals(listOf(emptyList<String>()), ok(backup(service(extra = ""","groupId":null"""))).items.map { it.folders })
    }

    @Test fun aMissingSettingTakesTheDefaultAndAPresentOneIsNeverChanged() {
        val read = ok(backup(
            service(otp = "{}"),
            """{"name":"sin otp","secret":"JBSWY3DPEHPK3PXP"}""",
            service(otp = """{"digits":10}"""),
            service(otp = """{"algorithm":"MD5"}"""),
            service(otp = """{"period":0}"""),
        ))
        assertEquals(listOf("Servicio", "sin otp"), read.items.map { it.entry.title })
        assertTrue(read.items.all { it.entry.totp!!.let { s -> s.algorithm == "SHA1" && s.digits == 6 && s.period == 30 } })
        assertEquals(List(3) { SkipReason.TOTP_UNSUPPORTED }, read.skipped.map { it.reason })
    }

    @Test fun encryptedTwoFasNeedsPassword() {
        val text = """{"services":[],"groups":[],"servicesEncrypted":"QUJD:REVG:R0hJ","schemaVersion":4}"""
        assertEquals(ReadResult.NeedsPassword(ImportFormat.TWOFAS), readImport(text.toByteArray()))
    }

    @Test fun wrongTypesAreBroken() {
        val services = listOf(
            """{"name":3,"secret":"JBSWY3DPEHPK3PXP"}""",
            """{"name":"secreto número","secret":1234}""",
            """{"name":"sin secreto"}""",
            service(name = "otp texto", otp = "\"x\""),
            service(name = "dígitos texto", otp = """{"digits":"6"}"""),
            service(name = "tipo número", otp = """{"tokenType":1}"""),
            service(name = "cuenta número", otp = """{"account":1}"""),
            service(name = "grupo número", extra = ""","groupId":1"""),
            service(name = "inyección", otp = """{"digits":6&secret=AAAA}"""),
            "[]",
        )
        val read = ok(backup(*services.toTypedArray(), service(name = "bien")))
        assertEquals(listOf("bien"), read.items.map { it.entry.title })
        assertEquals(List(services.size) { SkipReason.BROKEN }, read.skipped.map { it.reason })
        assertEquals("", read.skipped[0].title)
        assertEquals("secreto número", read.skipped[1].title)
    }

    @Test fun deepJsonIsRefused() {
        // Root (1), services (2), the service (3), then its unknown field.
        fun nested(total: Int) = backup(service(extra = ""","x":""" + "{\"a\":".repeat(total - 3) + "1" + "}".repeat(total - 3)))
        assertEquals(1, ok(nested(JSON_MAX_DEPTH)).items.size)
        assertEquals(IMPORT_JSON_TOO_DEEP, failed(nested(JSON_MAX_DEPTH + 1)))
    }

    @Test fun titleFallsBackToTheIssuerThenTheAccountThenTheType() {
        val read = ok(backup(
            service(name = "", otp = """{"issuer":"Emisor","account":"ana"}"""),
            service(name = "", otp = """{"account":"ana"}"""),
            service(name = "", otp = "{}"),
        ))
        assertEquals(listOf("Emisor", "ana", "Código 2FA"), read.items.map { it.entry.title })
    }

    @Test fun aServicesListThatIsNotAListIsDamaged() {
        assertEquals(IMPORT_BROKEN, failed("""{"services":{},"schemaVersion":4}"""))
        assertEquals(IMPORT_BROKEN, failed("""{"services":[],"servicesEncrypted":5}"""))
    }

    @Test fun nulAndLoneSurrogatesAreBroken() {
        val read = ok(backup(service(name = "a\\u0000"), service(name = "\\udfff")))
        assertEquals(List(2) { SkipReason.BROKEN }, read.skipped.map { it.reason })
    }
}
