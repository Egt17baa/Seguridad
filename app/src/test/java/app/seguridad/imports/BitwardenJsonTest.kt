package app.seguridad.imports

import app.seguridad.crypto.OTPAUTH_MAX_CHARS
import app.seguridad.vault.EntryType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The plain Bitwarden JSON export, from its public documentation; the fixture is hand-made with invented data. */
class BitwardenJsonTest {
    private fun fixture(): ByteArray =
        requireNotNull(javaClass.getResourceAsStream("/import/bitwarden/bitwarden.json")).use { it.readBytes() }

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

    /** A plain export with [items] (JSON objects, already written) and no folders. */
    private fun export(vararg items: String) = """{"encrypted":false,"folders":[],"items":[${items.joinToString(",")}]}"""

    private fun entries(read: ImportRead) = read.items.map { it.entry }

    @Test fun fixtureReadsEveryType() {
        val read = ok(fixture())
        assertEquals(ImportFormat.BITWARDEN, read.format)
        assertEquals("importado-bitwarden", read.format.originTag)
        assertEquals(
            listOf(
                EntryType.LOGIN to "Correo",
                EntryType.LOGIN to "Banco, cuenta \"nómina\"",
                EntryType.LOGIN to "Tienda 🛒",
                EntryType.TOTP to "Tienda 🛒",
                EntryType.LOGIN to "Foro",
                EntryType.TOTP to "Foro",
                EntryType.LOGIN to "Sin web",
                EntryType.NOTE to "Nota de prueba",
                EntryType.NOTE to "Tarjeta de prueba",
                EntryType.NOTE to "Identidad de prueba",
            ),
            entries(read).map { it.type to it.title },
        )
        assertEquals(
            listOf(
                "Banco, cuenta \"nómina\"" to SkipReason.TOTP_UNSUPPORTED,
                "Tienda 🛒" to SkipReason.PASSKEY,
                "Clave SSH de prueba" to SkipReason.SSH_KEY,
            ),
            read.skipped.map { it.title to it.reason },
        )
        assertEquals(2, read.becameNotes)
        val all = read.items.map { it.index } + read.skipped.map { it.index }
        assertEquals((0 until all.size).toList(), all.sorted())
    }

    @Test fun loginTakesTheFirstUriAndTheRestGoesToTheNotes() {
        val correo = entries(ok(fixture())).first()
        assertEquals("https://mail.example.com", correo.url)
        assertEquals("ana.prueba@example.com", correo.username)
        assertEquals("clave-correo-1", correo.password)
        // The notes as written, then one line per other URI and per custom field; a linked field has no value.
        assertEquals("Cuenta principal\nURL: https://webmail.example.com/entrar\nPIN: 4321\nPregunta: color azul", correo.notes)
        val banco = entries(ok(fixture()))[1]
        assertEquals("clave \"banco\", 2", banco.password)
        assertEquals("Línea uno\nLínea dos\nLínea tres", banco.notes)
        // Without a URI the URL is empty.
        assertEquals("", entries(ok(fixture())).single { it.title == "Sin web" }.url)
    }

    @Test fun loginTotpGivesASecondItemWithTheSameTitleAndFolders() {
        val read = ok(fixture())
        val tienda = read.items.filter { it.entry.title == "Tienda 🛒" }
        assertEquals(listOf(EntryType.LOGIN, EntryType.TOTP), tienda.map { it.entry.type })
        assertEquals(tienda[0].index + 1, tienda[1].index)
        val totp = tienda[1].entry.totp!!
        assertEquals("JBSWY3DPEHPK3PXP", totp.secret)
        assertEquals("Tienda", totp.issuer)
        assertEquals("ana", totp.account)
        // A bare secret, as the app lets one type it, is a TOTP with the default settings; its spaces go.
        val foro = read.items.filter { it.entry.title == "Foro" }
        assertEquals(listOf("Trabajo/Correo"), foro[1].folders)
        assertEquals(foro[0].folders, foro[1].folders)
        val foroTotp = foro[1].entry.totp!!
        assertEquals(listOf("JBSWY3DPEHPK3PXP", "SHA1", 6, 30), listOf(foroTotp.secret, foroTotp.algorithm, foroTotp.digits, foroTotp.period))
        // A Steam code and an HOTP link are not TOTP; the login still enters.
        val read2 = ok(export(
            """{"type":1,"name":"Juego","login":{"username":"a","password":"p","totp":"steam://JBSWY3DPEHPK3PXP"}}""",
            """{"type":1,"name":"Viejo","login":{"username":"a","password":"p","totp":"otpauth://hotp/x?secret=JBSWY3DPEHPK3PXP&counter=1"}}""",
            """{"type":1,"name":"Roto","login":{"username":"a","password":"p","totp":"no es base32 !"}}""",
            """{"type":1,"name":"Largo","login":{"username":"a","password":"p","totp":"${"A".repeat(OTPAUTH_MAX_CHARS + 1)}"}}""",
            """{"type":1,"name":"Enlace largo","login":{"username":"a","password":"p","totp":"otpauth://totp/${"x".repeat(OTPAUTH_MAX_CHARS)}?secret=JBSWY3DPEHPK3PXP"}}""",
        ))
        assertEquals(listOf("Juego", "Viejo", "Roto", "Largo", "Enlace largo"), entries(read2).map { it.title })
        // A 2FA that is not read, or is too long, says the login entered (1.4.2, F-6, and the code review).
        assertEquals(
            listOf(SkipReason.NOT_TOTP, SkipReason.NOT_TOTP, SkipReason.LOGIN_TOTP_BROKEN, SkipReason.LOGIN_TOTP_BROKEN, SkipReason.LOGIN_TOTP_BROKEN),
            read2.skipped.map { it.reason },
        )
    }

    @Test fun secureNoteIsANote() {
        val note = entries(ok(fixture())).single { it.title == "Nota de prueba" }
        assertEquals(EntryType.NOTE, note.type)
        assertEquals("Texto de la nota", note.notes)
        assertEquals("", note.password)
    }

    @Test fun cardAndIdentityBecomeNotesWithTheirFieldsAsLines() {
        val read = ok(fixture())
        val card = entries(read).single { it.title == "Tarjeta de prueba" }
        assertEquals(EntryType.NOTE, card.type)
        assertEquals(
            "Titular: Ana Prueba\nMarca: Visa\nNúmero: 4111 1111 1111 1111\nMes de caducidad: 12\nAño de caducidad: 2030\n" +
                "Código de seguridad: 123\nSolo para pruebas",
            card.notes,
        )
        val identity = entries(read).single { it.title == "Identidad de prueba" }
        // Empty or null fields give no line.
        assertEquals(
            "Tratamiento: Sra.\nNombre: Ana\nApellidos: Prueba\nDirección: Calle Falsa 1\nCiudad: Madrid\nCódigo postal: 28000\n" +
                "País: ES\nCorreo: ana.prueba@example.com",
            identity.notes,
        )
        assertEquals(2, read.becameNotes)
    }

    @Test fun sshKeyIsSkippedAndItsKeyIsNowhere() {
        val read = ok(fixture())
        assertEquals(SkipReason.SSH_KEY, read.skipped.single { it.title == "Clave SSH de prueba" }.reason)
        assertTrue(entries(read).none { "PRIVATE KEY" in it.notes || "PRIVATE KEY" in it.title })
    }

    @Test fun passkeyIsAnExtraSkippedAndTheLoginStillEnters() {
        val read = ok(export("""{"type":1,"name":"Web","login":{"username":"a","password":"p","fido2Credentials":[{"credentialId":"x"}]}}"""))
        assertEquals("Web", entries(read).single().title)
        assertEquals(listOf("Web" to SkipReason.PASSKEY), read.skipped.map { it.title to it.reason })
        // An empty list is no passkey.
        assertTrue(ok(export("""{"type":1,"name":"Web","login":{"username":"a","fido2Credentials":[]}}""")).skipped.isEmpty())
    }

    @Test fun folderIdIsTheFolderName() {
        val read = ok(fixture())
        assertEquals(listOf("Personal"), read.items.single { it.entry.title == "Correo" }.folders)
        assertEquals(emptyList<String>(), read.items.single { it.entry.title == "Sin web" }.folders)
        // An id that names no folder, and a folder record that cannot be read, give no folder; the item still enters.
        val text = """{"encrypted":false,"folders":[{"id":"f1","name":7},{"id":"f2","name":"Bien"},"x"],"items":[""" +
            """{"type":2,"name":"a","notes":"n","folderId":"nada"},{"type":2,"name":"b","notes":"n","folderId":"f1"},""" +
            """{"type":2,"name":"c","notes":"n","folderId":"f2"}]}"""
        assertEquals(listOf(emptyList(), emptyList(), listOf("Bien")), ok(text).items.map { it.folders })
    }

    @Test fun aFolderNameWithANulOrALoneSurrogateGivesNoFolder() {
        val text = """{"encrypted":false,"folders":[{"id":"f1","name":"mal\ud800"},{"id":"f2","name":"a\u0000b"},{"id":"f3","name":"bien 🛒"}],""" +
            """"items":[{"type":2,"name":"a","notes":"n","folderId":"f1"},{"type":2,"name":"b","notes":"n","folderId":"f2"},""" +
            """{"type":2,"name":"c","notes":"n","folderId":"f3"}]}"""
        // The items still enter; only the folder is dropped.
        assertEquals(listOf(emptyList(), emptyList(), listOf("bien 🛒")), ok(text).items.map { it.folders })
    }

    @Test fun theUrlIsTheFirstUriThatIsNotEmpty() {
        val read = ok(export(
            """{"type":1,"name":"w","login":{"username":"a","uris":[{"uri":null},{"uri":""},{"uri":"https://a.example"},{"uri":"https://b.example"}]}}""",
        ))
        val e = entries(read).single()
        assertEquals("https://a.example", e.url)
        assertEquals("URL: https://b.example", e.notes)
        // So the title falls back to its host as well.
        assertEquals("a.example", entries(ok(export("""{"type":1,"login":{"uris":[{"uri":null},{"uri":"https://a.example/x"}]}}"""))).single().title)
    }

    @Test fun aFieldWithoutANameGetsAGenericLabel() {
        val read = ok(export("""{"type":2,"name":"n","fields":[{"name":"","value":"uno"},{"name":"  ","value":"dos"},{"value":"tres"},{"name":"PIN","value":"4"}]}"""))
        assertEquals("Campo: uno\nCampo: dos\nCampo: tres\nPIN: 4", entries(read).single().notes)
        assertEquals("Campo", NOTE_LINE_UNNAMED_FIELD)
    }

    @Test fun favoriteIsKept() {
        val read = ok(fixture())
        assertEquals(listOf("Correo"), entries(read).filter { it.favorite }.map { it.title })
    }

    @Test fun itemWithNullLoginGoesToSkipped() {
        val read = ok(export(
            """{"type":1,"name":"Nulo","login":null}""",
            """{"type":1,"name":"Sin login"}""",
            """{"type":1,"name":"Bien","login":{"username":"a","password":"p"}}""",
        ))
        assertEquals(listOf("Bien"), entries(read).map { it.title })
        assertEquals(listOf("Nulo" to SkipReason.BROKEN, "Sin login" to SkipReason.BROKEN), read.skipped.map { it.title to it.reason })
    }

    @Test fun loginWithNothingIsEmpty() {
        val read = ok(export("""{"type":1,"name":"Solo título","login":{"uris":[],"username":"","password":"","totp":null}}"""))
        assertEquals(listOf("Solo título" to SkipReason.EMPTY), read.skipped.map { it.title to it.reason })
    }

    @Test fun titleFallsBackToTheHostTheUserOrTheType() {
        val read = ok(export(
            """{"type":1,"name":"","login":{"uris":[{"uri":"https://user:secreto@host.example.com/x"}],"username":"a","password":"p"}}""",
            """{"type":1,"name":" ","login":{"username":"luis","password":"p"}}""",
            """{"type":1,"login":{"password":"p"}}""",
            """{"type":2,"notes":"n"}""",
            """{"type":3,"card":{"number":"1"}}""",
        ))
        assertEquals(listOf("host.example.com", "luis", "Acceso", "Nota", "Nota"), entries(read).map { it.title })
    }

    @Test fun wrongTypesAreBroken() {
        val items = listOf(
            """{"type":1,"name":12,"login":{"username":"a"}}""",
            """{"type":"1","name":"tipo texto","login":{"username":"a"}}""",
            """{"type":1,"name":"usuario número","login":{"username":5}}""",
            """{"type":1,"name":"favorito texto","favorite":"true","login":{"username":"a"}}""",
            """{"type":1,"name":"uris objeto","login":{"username":"a","uris":{"uri":"x"}}}""",
            """{"type":1,"name":"uri número","login":{"username":"a","uris":[{"uri":3}]}}""",
            """{"type":1,"name":"campo número","fields":[{"name":"x","value":4}],"login":{"username":"a"}}""",
            """{"type":1,"name":"campos texto","fields":"x","login":{"username":"a"}}""",
            """{"type":1,"name":"passkeys texto","login":{"username":"a","fido2Credentials":"x"}}""",
            """{"type":1,"name":"totp número","login":{"username":"a","totp":123}}""",
            """{"type":1,"name":"login lista","login":[]}""",
            """{"type":2,"name":"notas lista","notes":["x"]}""",
            """{"type":3,"name":"tarjeta texto","card":"x"}""",
            """{"type":3,"name":"número número","card":{"number":4111}}""",
            """{"type":1,"name":"sin comillas","login":{"username":abc}}""",
            """{"type":99,"name":"tipo desconocido"}""",
            """{"name":"sin tipo"}""",
            "7",
            "\"texto\"",
            "null",
        )
        val read = ok(export(*items.toTypedArray(), """{"type":2,"name":"bien","notes":"n"}"""))
        assertEquals(listOf("bien"), entries(read).map { it.title })
        assertEquals(List(items.size) { SkipReason.BROKEN }, read.skipped.map { it.reason })
        // The title of a broken item is only its name, when the name is text.
        assertEquals("", read.skipped[0].title)
        assertEquals("tipo texto", read.skipped[1].title)
        assertEquals("", read.skipped[items.size - 1].title)
    }

    /**
     * A protected export asks for its password only when its header could be used (branch review A, M-3; the real
     * fixtures ask in BitwardenCryptoTest): these cipher strings are not of the right lengths, so it is broken at once.
     */
    @Test fun encryptedBitwardenWithABrokenHeaderDoesNotAskForAPassword() {
        val text = """{"encrypted":true,"passwordProtected":true,"salt":"c2FsdA==","kdfType":0,"kdfIterations":600000,""" +
            """"encKeyValidation_DO_NOT_EDIT":"2.AAAA|BBBB|CCCC","data":"2.AAAA|BBBB|CCCC"}"""
        assertEquals(ReadResult.Failed(IMPORT_BROKEN), readImport(text.toByteArray()))
    }

    @Test fun accountEncryptedBitwardenSaysHow() {
        val text = """{"encrypted":true,"encKeyValidation_DO_NOT_EDIT":"2.AAAA|BBBB|CCCC","folders":[],"items":[{"type":1,"name":"2.AAAA|BBBB|CCCC"}]}"""
        val message = failed(text)
        assertEquals(IMPORT_BITWARDEN_ACCOUNT_ENCRYPTED, message)
        assertEquals(
            "Esta exportación está cifrada con tu cuenta de Bitwarden y solo Bitwarden puede abrirla. Vuelve a exportar " +
                "con el formato «.json», o con «.json (Encrypted)» y el tipo «Protegida con contraseña» (no «Cuenta restringida»).",
            message,
        )
        // passwordProtected false is the same.
        assertEquals(IMPORT_BITWARDEN_ACCOUNT_ENCRYPTED, failed("""{"encrypted":true,"passwordProtected":false,"items":[]}"""))
    }

    @Test fun deepJsonIsRefused() {
        // Root object (1), items (2), the item (3), then its unknown field: depth 64 in all is read, 65 is refused.
        fun nested(total: Int) = export("""{"type":2,"name":"n","notes":"x","extra":""" + "[".repeat(total - 3) + "]".repeat(total - 3) + "}")
        assertEquals(1, ok(nested(JSON_MAX_DEPTH)).items.size)
        assertEquals(IMPORT_JSON_TOO_DEEP, failed(nested(JSON_MAX_DEPTH + 1)))
    }

    @Test fun fieldOverLimitIsSkipped() {
        fun login(name: String = "t", user: String = "a", pass: String = "p", uri: String = "u") =
            """{"type":1,"name":"$name","login":{"username":"$user","password":"$pass","uris":[{"uri":"$uri"}]}}"""
        val read = ok(export(
            login(name = "x".repeat(MAX_TITLE)),
            login(name = "x".repeat(MAX_TITLE + 1)),
            login(user = "x".repeat(MAX_USERNAME + 1)),
            login(pass = "x".repeat(MAX_PASSWORD + 1)),
            login(uri = "x".repeat(MAX_URL + 1)),
            """{"type":2,"name":"nota","notes":"${"x".repeat(MAX_NOTES + 1)}"}""",
            """{"type":2,"name":"nota justa","notes":"${"x".repeat(MAX_NOTES)}"}""",
        ))
        assertEquals(listOf(MAX_TITLE, "nota justa".length), entries(read).map { it.title.length })
        assertEquals(MAX_NOTES, entries(read)[1].notes.length)
        assertEquals(List(5) { SkipReason.FIELD_TOO_LONG }, read.skipped.map { it.reason })
        assertTrue(read.skipped.all { it.title.length <= MAX_TITLE })
    }

    @Test(timeout = 10_000)
    fun notesBuiltFromFieldsStopAtTheLimit() {
        // 60 000 fields of 10 characters: the notes would pass MAX_NOTES, so the item is refused, never cut.
        val fields = (1..60_000).joinToString(",") { """{"name":"n","value":"1234567"}""" }
        val read = ok(export("""{"type":2,"name":"muchos","notes":"","fields":[$fields]}"""))
        assertEquals(listOf("muchos" to SkipReason.FIELD_TOO_LONG), read.skipped.map { it.title to it.reason })
        // Exactly at the limit enters whole.
        val value = "v".repeat(MAX_NOTES - "n: ".length)
        assertEquals(MAX_NOTES, entries(ok(export("""{"type":2,"name":"justo","fields":[{"name":"n","value":"$value"}]}"""))).single().notes.length)
        // Extra URIs count too.
        val uris = (1..5_000).joinToString(",") { """{"uri":"https://example.com/abcdefghijklmn"}""" }
        assertEquals(SkipReason.FIELD_TOO_LONG, ok(export("""{"type":1,"name":"uris","login":{"username":"a","uris":[$uris]}}""")).skipped.single().reason)
    }

    @Test fun nulAndLoneSurrogatesAreBroken() {
        val read = ok(export(
            """{"type":1,"name":"nul","login":{"username":"a","password":"cla\u0000ve"}}""",
            """{"type":1,"name":"suelto\ud800","login":{"username":"a","password":"p"}}""",
            """{"type":2,"name":"bajo suelto","notes":"x\udc00y"}""",
            """{"type":2,"name":"campo","fields":[{"name":"a\u0000","value":"b"}]}""",
            """{"type":2,"name":"par 🛒","notes":"bien"}""",
        ))
        assertEquals(listOf("par 🛒"), entries(read).map { it.title })
        assertEquals(List(4) { SkipReason.BROKEN }, read.skipped.map { it.reason })
        // The report title of a broken item never keeps the lone surrogate.
        assertEquals("suelto", read.skipped[1].title)
    }

    @Test fun duplicateKeysTakeTheLastValue() {
        val read = ok(export("""{"type":5,"name":"primero","type":2,"name":"último","notes":"n"}"""))
        assertEquals(listOf(EntryType.NOTE to "último"), entries(read).map { it.type to it.title })
        // The same at the root: the last «encrypted» counts.
        val r = readImport("""{"encrypted":true,"encrypted":false,"items":[]}""".toByteArray())
        assertTrue("$r", r is ReadResult.Ok)
    }

    @Test fun hugeNumbersAreBroken() {
        val read = ok(export(
            """{"type":1e999,"name":"exponente"}""",
            """{"type":123456789012345678901234567890,"name":"enorme"}""",
            """{"type":1.0,"name":"decimal"}""",
            """{"type":-1,"name":"negativo"}""",
        ))
        assertTrue(read.items.isEmpty())
        assertEquals(List(4) { SkipReason.BROKEN }, read.skipped.map { it.reason })
    }

    @Test fun veryLongStringsAreBoundedNotCut() {
        val huge = "x".repeat(4_000_000)
        val read = ok(export("""{"type":1,"name":"t","login":{"username":"a","password":"$huge"}}"""))
        assertEquals(SkipReason.FIELD_TOO_LONG, read.skipped.single().reason)
        assertEquals("t", read.skipped.single().title)
    }

    @Test fun itemCountOverLimitIsRefused() {
        val item = """{"type":2,"name":"n","notes":"x"}"""
        assertEquals(IMPORT_MAX_ITEMS, ok(export(*Array(IMPORT_MAX_ITEMS) { item })).items.size)
        assertEquals(IMPORT_TOO_MANY_ITEMS, failed(export(*Array(IMPORT_MAX_ITEMS + 1) { item })))
        // Logins with a TOTP give two items each: the bound is on everything the file gives, after that expansion.
        val withTotp = """{"type":1,"name":"t","login":{"username":"a","totp":"JBSWY3DPEHPK3PXP"}}"""
        assertEquals(IMPORT_TOO_MANY_ITEMS, failed(export(*Array(IMPORT_MAX_ITEMS / 2 + 1) { withTotp })))
    }

    @Test fun hostileValuesAreStoredExactly() {
        val read = ok(export(
            """{"type":1,"name":"factura\u202Efdp.exe","notes":"línea\u2028dos","login":{"username":"a","password":"p\u202Ew",""" +
                """"uris":[{"uri":"javascript:alert(1)"},{"uri":"intent://x#Intent;scheme=http;end"}]}}""",
            """{"type":1,"name":"factura\u202Efdp.exe","login":{"username":"a","password":"${"x".repeat(MAX_PASSWORD + 1)}"}}""",
        ))
        val e = entries(read).single()
        assertEquals("factura\u202Efdp.exe", e.title)
        assertEquals("javascript:alert(1)", e.url)
        assertEquals("p\u202Ew", e.password)
        assertEquals("línea\u2028dos\nURL: intent://x#Intent;scheme=http;end", e.notes)
        assertEquals("facturafdp.exe", read.skipped.single().title)
    }

    @Test fun secretsNeverReachTheSkippedList() {
        val read = ok(export(
            """{"type":1,"name":"t","notes":"nota-secreta","login":{"username":"a","password":"clave-secreta","totp":"otpauth://totp/x?secret=JBSWY3DPEHPK3PXP&digits=10"}}""",
            """{"type":5,"name":"ssh","sshKey":{"privateKey":"clave-privada"}}""",
            """{"type":1,"name":7,"login":{"password":"clave-secreta"}}""",
        ))
        assertTrue(read.skipped.isNotEmpty())
        for (s in read.skipped) for (secret in listOf("clave-secreta", "nota-secreta", "JBSWY3DPEHPK3PXP", "clave-privada")) assertFalse(s.title, secret in s.title)
    }

    @Test fun itemsAreNotMergedYet() {
        val read = ok(fixture())
        assertTrue(read.items.all { it.entry.id == "" && it.entry.createdAt == 0L && it.entry.tags.isEmpty() })
    }

    @Test fun anItemsListThatIsNotAListIsDamaged() {
        assertEquals(IMPORT_BROKEN, failed("""{"encrypted":false,"items":{"type":1}}"""))
        assertEquals(IMPORT_BROKEN, failed("""{"encrypted":"no","items":[]}"""))
    }
}
