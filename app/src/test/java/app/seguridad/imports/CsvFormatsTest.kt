package app.seguridad.imports

import app.seguridad.vault.EntryType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvFormatsTest {
    private fun fixture(name: String): ByteArray =
        requireNotNull(javaClass.getResourceAsStream("/import/csv/$name")) { name }.use { it.readBytes() }

    private fun ok(bytes: ByteArray): ImportRead {
        val r = readImport(bytes)
        assertTrue("$r", r is ReadResult.Ok)
        return (r as ReadResult.Ok).read
    }

    private fun ok(text: String) = ok(text.toByteArray(Charsets.UTF_8))

    private fun logins(read: ImportRead) = read.items.map { it.entry }.filter { it.type == EntryType.LOGIN }

    @Test fun chromiumHeader() {
        val read = ok(fixture("chromium.csv"))
        assertEquals(ImportFormat.BROWSER_CSV, read.format)
        assertEquals("importado-navegador", read.format.originTag)
        assertEquals(3, read.items.size)
        assertTrue(read.skipped.isEmpty())
        val (a, b, c) = read.items.map { it.entry }
        assertEquals(EntryType.LOGIN, a.type)
        assertEquals("ejemplo.com", a.title)
        assertEquals("https://ejemplo.com/login", a.url)
        assertEquals("ana@ejemplo.com", a.username)
        assertEquals("pa,ss\"word", a.password)
        assertEquals("", a.notes)
        assertEquals("Pregunta: color\nRespuesta: azul", b.notes)
        // No name: the title is the URL's host, never its user or password.
        assertEquals("sin-nombre.example.net", c.title)
        assertEquals("https://user:secreto@sin-nombre.example.net/", c.url)
        assertEquals(0, read.becameNotes)
    }

    @Test fun firefoxHeaderTitleIsTheHost() {
        val read = ok(fixture("firefox.csv"))
        assertEquals(ImportFormat.FIREFOX_CSV, read.format)
        val (a, b) = logins(read)
        assertEquals("www.ejemplo.com", a.title)
        assertEquals("https://www.ejemplo.com", a.url)
        assertEquals("ana", a.username)
        assertEquals("clave-firefox-1", a.password)
        assertEquals("intranet.example.org", b.title)
        assertEquals("clave firefox 2", b.password)
        // httpRealm, guid and the times are columns Clavero gives no meaning to.
        assertEquals("", b.notes)
        assertEquals(2, read.items.size)
    }

    @Test fun firefoxTitleFallsBackWhenTheUrlHasNoHost() {
        val read = ok("url,username,password,httpRealm,formActionOrigin,guid\nno es una url,luis,x,,,{1}\n,,y,,,{2}\n")
        assertEquals(listOf("luis", "Acceso"), logins(read).map { it.title })
    }

    @Test fun appleHeaderOtpAuthAlsoGivesTotp() {
        val read = ok(fixture("apple.csv"))
        assertEquals(ImportFormat.APPLE_CSV, read.format)
        assertEquals(4, logins(read).size)
        val totp = read.items.map { it.entry }.filter { it.type == EntryType.TOTP }
        assertEquals(1, totp.size)
        val login = logins(read)[0]
        assertEquals("ejemplo.com (ana@ejemplo.com)", login.title)
        assertEquals("Nota de prueba", login.notes)
        assertEquals(login.title, totp[0].title)
        assertEquals("JBSWY3DPEHPK3PXP", totp[0].totp!!.secret)
        assertEquals("Ejemplo", totp[0].totp!!.issuer)
        assertEquals("ana@ejemplo.com", totp[0].totp!!.account)
        // The TOTP item comes right after its login, with the same folders (none in a CSV).
        assertEquals(read.items[0].index + 1, read.items[1].index)
        assertEquals(read.items[0].folders, read.items[1].folders)
        // A 10-digit code is refused, not changed to 6; an HOTP link is not TOTP. Their logins still enter.
        assertEquals(
            listOf("banco.example.net" to SkipReason.TOTP_UNSUPPORTED, "viejo.example.com" to SkipReason.NOT_TOTP),
            read.skipped.map { it.title to it.reason },
        )
        assertTrue(logins(read).any { it.title == "banco.example.net" } && logins(read).any { it.title == "viejo.example.com" })
    }

    @Test fun appleBrokenOtpAuthIsBrokenAndNeverACrash() {
        val links = listOf(
            "otpauth://totp/x?secret=%ZZ", // bad percent-escape
            "otpauth://totp/x?secret=1", // not a usable key
            "no es un enlace",
            "otpauth://totp/x?secret=JBSWY3DPEHPK3PXP&digits=seis",
            "otpauth://totp/x?secret=JBSWY3DPEHPK3PXP&algorithm=MD5",
            "otpauth://totp/" + "x".repeat(5000) + "?secret=JBSWY3DPEHPK3PXP",
            "javascript:alert(1)",
        )
        val csv = "Title,URL,Username,Password,Notes,OTPAuth\n" + links.mapIndexed { i, l -> "t$i,,u,p,,\"$l\"" }.joinToString("\n")
        val read = ok(csv)
        assertEquals(links.size, logins(read).size)
        assertTrue(read.items.none { it.entry.type == EntryType.TOTP })
        assertEquals(
            listOf(SkipReason.BROKEN, SkipReason.BROKEN, SkipReason.BROKEN, SkipReason.TOTP_UNSUPPORTED, SkipReason.TOTP_UNSUPPORTED, SkipReason.FIELD_TOO_LONG, SkipReason.BROKEN),
            read.skipped.map { it.reason },
        )
    }

    @Test fun unknownColumnsAreIgnored() {
        val read = ok("name,url,username,password,note,extra,passkey\nweb,https://w.example,ana,clave,nota,\"lo que sea\",xyz\n")
        val e = logins(read).single()
        assertEquals(listOf("web", "https://w.example", "ana", "clave", "nota"), listOf(e.title, e.url, e.username, e.password, e.notes))
        assertFalse(listOf(e.title, e.url, e.username, e.password, e.notes).any { "lo que sea" in it || "xyz" in it })
    }

    @Test fun columnsInAnyOrderAndCase() {
        val read = ok("PASSWORD,Note,UserName,URL,Name\nclave,nota,ana,https://w.example,web\n")
        assertEquals(ImportFormat.BROWSER_CSV, read.format)
        val e = logins(read).single()
        assertEquals(listOf("web", "https://w.example", "ana", "clave", "nota"), listOf(e.title, e.url, e.username, e.password, e.notes))
        val apple = ok("otpauth,notes,password,username,url,TITLE\n,n,p,u,https://a.example,T\n")
        assertEquals(ImportFormat.APPLE_CSV, apple.format)
        assertEquals("T", logins(apple).single().title)
    }

    @Test fun semicolonExportIsRead() {
        val read = ok("name;url;username;password;note\nweb;https://w.example;ana;cla,ve;uno, dos\n")
        assertEquals(listOf("cla,ve" to "uno, dos"), logins(read).map { it.password to it.notes })
    }

    @Test fun shortRowsHaveEmptyCells() {
        val read = ok("name,url,username,password,note\nweb,https://w.example,ana\n")
        assertEquals(listOf("" to ""), logins(read).map { it.password to it.notes })
    }

    @Test fun fieldOverLimitIsSkipped() {
        fun row(title: String = "t", url: String = "u", user: String = "a", pass: String = "p", note: String = "") =
            "\"$title\",\"$url\",\"$user\",\"$pass\",\"$note\""
        val rows = listOf(
            row(title = "x".repeat(MAX_TITLE)), // at the limit: enters
            row(title = "x".repeat(MAX_TITLE + 1)),
            row(url = "x".repeat(MAX_URL + 1)),
            row(user = "x".repeat(MAX_USERNAME + 1)),
            row(pass = "x".repeat(MAX_PASSWORD + 1)),
            row(note = "x".repeat(MAX_NOTES + 1)),
            row(note = "x".repeat(MAX_NOTES), pass = "x".repeat(MAX_PASSWORD), url = "x".repeat(MAX_URL), user = "x".repeat(MAX_USERNAME)),
            row(note = "x".repeat(500_000)), // cut by the parser, still over the limit
        )
        val read = ok("name,url,username,password,note\n" + rows.joinToString("\n"))
        assertEquals(2, read.items.size)
        assertEquals(List(6) { SkipReason.FIELD_TOO_LONG }, read.skipped.map { it.reason })
        // Never cut in silence: nothing that entered is shorter than in the file.
        assertEquals(MAX_TITLE, read.items[0].entry.title.length)
        assertEquals(MAX_NOTES, read.items[1].entry.notes.length)
        // The title of a skipped row is bounded, and only the title.
        assertTrue(read.skipped.all { it.title.length <= MAX_TITLE })
        assertEquals("t", read.skipped[4].title)
    }

    @Test fun rowWithNothingIsEmpty() {
        val read = ok("name,url,username,password,note\n,,,,\nsolo un título,,,,\n\"\",\"\",\"\",\"\",\"\"\n  ,  ,  ,,  \n")
        assertTrue(read.items.isEmpty())
        assertEquals(List(4) { SkipReason.EMPTY }, read.skipped.map { it.reason })
        assertEquals("solo un título", read.skipped[1].title)
        // A password of spaces is a password.
        assertEquals("   ", logins(ok("name,url,username,password,note\n,,,   ,\n")).single().password)
    }

    @Test fun nulInAKnownFieldIsADamagedRow() {
        val read = ok("name,url,username,password,note,extra\nweb,,ana,cla\u0000ve,,\nbien,,luis,clave,,x\u0000y\n")
        assertEquals(SkipReason.BROKEN, read.skipped.single().reason)
        assertEquals("web", read.skipped.single().title)
        // A NUL in a column Clavero does not know changes nothing.
        assertEquals("bien", logins(read).single().title)
    }

    @Test fun hostileTextIsStoredExactlyAndTheReportIsPlain() {
        // Decision (c): imported values are data. Controls and bidi overrides are kept in the entry as read, the
        // URL is kept as text whatever its scheme, and the report title is plain.
        val title = "factura\u202Efdp.exe\u2066\u0007\u001B[31m"
        val note = "línea\u2028dos\u0085tres\u202D\u2069"
        val urls = listOf("javascript:alert(1)", "intent://x#Intent;scheme=http;end", "file:///sdcard/a", "content://x/y")
        val csv = "name,url,username,password,note\n" + urls.joinToString("\n") { "\"$title\",\"$it\",ana,\"p\u202Ew\",\"$note\"" } +
            "\n\"$title\",,,\"" + "x".repeat(MAX_PASSWORD + 1) + "\",\n"
        val read = ok(csv)
        assertEquals(urls, logins(read).map { it.url })
        assertTrue(logins(read).all { it.title == title && it.notes == note && it.password == "p\u202Ew" })
        val skipped = read.skipped.single()
        assertEquals(SkipReason.FIELD_TOO_LONG, skipped.reason)
        assertEquals("facturafdp.exe  [31m", skipped.title)
    }

    @Test fun itemsAreNotMergedYet() {
        val read = ok(fixture("apple.csv"))
        assertTrue(read.items.all { it.entry.id == "" && it.entry.createdAt == 0L && it.entry.updatedAt == 0L && it.entry.tags.isEmpty() })
        assertTrue(read.items.all { it.folders.isEmpty() && !it.entry.favorite })
        assertTrue(read.items.all { it.entry.type in setOf(EntryType.LOGIN, EntryType.TOTP) })
    }

    @Test fun indexesAreUniqueAcrossItemsAndSkipped() {
        val read = ok(fixture("apple.csv"))
        val all = read.items.map { it.index } + read.skipped.map { it.index }
        assertEquals(all.size, all.toSet().size)
        assertEquals((0 until all.size).toList(), all.sorted())
    }

    @Test fun aHeaderAloneIsAnEmptyRead() {
        val read = ok("name,url,username,password,note\n")
        assertTrue(read.items.isEmpty() && read.skipped.isEmpty())
    }

    @Test fun duplicateHeaderNamesUseTheFirstColumn() {
        assertEquals("primera", logins(ok("name,url,username,password,password,note\nw,,a,primera,segunda,\n")).single().password)
    }

    @Test fun headersThatAreNotAKnownFormat() {
        for (h in listOf("url,username,password", "title,username,password", "login_uri,login_username,login_password,name", "a;b;c", "")) {
            assertNull(h, readCsvImport("$h\nx,y,z\n"))
        }
    }

    // ---- Task 10, rulings R19, R21 and R22 ----

    @Test fun googlePasswordsCsvReadsAsChromium() {
        // «Google Passwords.csv», the only file Chrome exports (R21): the columns of chromium.csv, random data.
        val read = ok(fixture("Google Passwords.csv"))
        assertEquals(ImportFormat.BROWSER_CSV, read.format)
        assertEquals(listOf("tienda.example.com", "correo.example.org", "app.example.net"), read.items.map { it.entry.title })
        assertTrue(read.items[1].entry.password.startsWith("cl,ave\""))
        assertEquals("Nota con\nsalto", read.items[1].entry.notes)
        assertTrue(read.skipped.isEmpty())
    }

    @Test fun edgeCsvWithAndWithoutNotesReadsAsChromium() {
        // R22: «Microsoft Edge Passwords.csv» has no note column; another export has it.
        val noNote = ok(fixture("Microsoft Edge Passwords.csv"))
        assertEquals(ImportFormat.BROWSER_CSV, noNote.format)
        assertEquals(listOf("banco.example.com", "foro.example.org"), noNote.items.map { it.entry.title })
        assertTrue(noNote.items.all { it.entry.notes.isEmpty() && it.entry.password.startsWith("Pw-") })
        val withNote = ok(fixture("edge-con-notas.csv"))
        assertEquals(ImportFormat.BROWSER_CSV, withNote.format)
        assertEquals("Asiento junto a la ventana", withNote.items.single().entry.notes)
    }

    @Test fun bitwardenCsvIsReadByItsExactHeader() {
        val read = ok(fixture("bitwarden.csv"))
        assertEquals(ImportFormat.BITWARDEN, read.format)
        assertEquals("importado-bitwarden", read.format.originTag)
        val e = read.items.map { it.entry }
        assertEquals(
            listOf(
                EntryType.LOGIN to "Correo inventado", EntryType.TOTP to "Correo inventado", EntryType.LOGIN to "Tienda",
                EntryType.TOTP to "Tienda", EntryType.NOTE to "Nota segura", EntryType.LOGIN to "Juego",
                EntryType.LOGIN to "Dos webs",
            ),
            e.map { it.type to it.title },
        )
        val mail = e[0]
        assertEquals("https://correo.example.com/", mail.url)
        assertTrue(mail.username.startsWith("ana") && mail.password.startsWith("Pw-"))
        assertTrue(mail.favorite)
        assertTrue(mail.notes.startsWith("Nota del acceso\nPIN: "))
        assertEquals(listOf("Trabajo"), read.items[0].folders)
        // A bare key takes the defaults; an otpauth link is read by the strict reader.
        assertEquals(6, e[1].totp!!.digits)
        assertEquals("Tienda", e[3].totp!!.issuer)
        assertEquals("Línea 1\nLínea 2", e[4].notes)
        assertEquals(listOf("Casa"), read.items[4].folders)
        assertEquals(0, read.becameNotes)
        // A Steam code is not TOTP; a row with nothing is empty.
        assertEquals(listOf("Juego" to SkipReason.NOT_TOTP, "" to SkipReason.EMPTY), read.skipped.map { it.title to it.reason })
    }

    @Test fun bitwardenOrganizationCsvTakesCollectionsAsFolders() {
        val read = ok(fixture("bitwarden-organizacion.csv"))
        assertEquals(ImportFormat.BITWARDEN, read.format)
        assertEquals(listOf(EntryType.LOGIN to "Panel compartido", EntryType.NOTE to "Nota del equipo"), read.items.map { it.entry.type to it.entry.title })
        assertEquals(listOf("Equipo"), read.items[0].folders)
    }

    @Test fun bitwardenCsvOtherTypesBecomeNotesAndBrokenTotpIsListed() {
        val header = "folder,favorite,type,name,notes,fields,reprompt,login_uri,login_username,login_password,login_totp\n"
        val read = ok(
            header +
                ",,card,Tarjeta,Texto,\"Número: 4111\",0,,,,\n" +
                ",,login,Con 2FA raro,,,0,https://a.example/,u,p,otpauth://totp/a?secret=JBSWY3DPEHPK3PXP&digits=10\n" +
                ",x,login,Favorito raro,,,0,https://b.example/,u,p,\n",
        )
        assertEquals(listOf(EntryType.NOTE, EntryType.LOGIN, EntryType.LOGIN), read.items.map { it.entry.type })
        assertEquals("Texto\nNúmero: 4111", read.items[0].entry.notes)
        assertEquals(1, read.becameNotes)
        assertEquals(listOf(SkipReason.TOTP_UNSUPPORTED), read.skipped.map { it.reason })
        assertFalse(read.items[2].entry.favorite)
    }

    @Test fun bitwardenCsvWithSeveralUrisKeepsTheFirstAsTheUrl() {
        // I-1: Bitwarden writes every URI of a login into the one login_uri cell, separated by commas. The first is the
        // URL; each other one is a «URL: …» line of the notes, as the JSON reader does.
        val twoUris = ok(fixture("bitwarden.csv")).items.map { it.entry }.single { it.title == "Dos webs" }
        assertEquals("https://uno.example.com/", twoUris.url)
        assertEquals("URL: https://dos.example.com/entrar", twoUris.notes)
        assertEquals("uno.example.com", hostOf(twoUris.url))
        // Spaces around each one are trimmed and an empty one dropped; the notes come first and the fields last, as in JSON.
        val header = "folder,favorite,type,name,notes,fields,reprompt,login_uri,login_username,login_password,login_totp\n"
        val e = ok(header + ",,login,Tres,Nota,\"PIN: 1\",0,\" ,https://a.example/ , https://b.example/,https://c.example/ \",u,p,\n")
            .items.single().entry
        assertEquals("https://a.example/", e.url)
        assertEquals("Nota\nURL: https://b.example/\nURL: https://c.example/\nPIN: 1", e.notes)
    }

    @Test fun bitwardenCsvSplitsOnlyBeforeAScheme() {
        // R26 (N-1): a comma inside a URI is kept; a comma before another URI's scheme splits.
        val header = "folder,favorite,type,name,notes,fields,reprompt,login_uri,login_username,login_password,login_totp\n"
        val one = ok(header + ",,login,Mapa,,,0,\"https://a.example/x?q=1,2\",u,p,\n").items.single().entry
        assertEquals("https://a.example/x?q=1,2", one.url)
        assertEquals("", one.notes)
        val two = ok(header + ",,login,App,,,0,\"https://a/,androidapp://b\",u,p,\n").items.single().entry
        assertEquals("https://a/", two.url)
        assertEquals("URL: androidapp://b", two.notes)
    }

    @Test fun bitwardenCsvAndJsonGiveTheSameLogin() {
        val csv = ok(fixture("bitwarden.csv")).items.map { it.entry }.single { it.title == "Dos webs" }
        val json = ok(
            """{"encrypted":false,"folders":[],"items":[{"type":1,"name":"Dos webs","notes":null,"favorite":false,""" +
                """"login":{"uris":[{"uri":"https://uno.example.com/"},{"uri":"https://dos.example.com/entrar"}],""" +
                """"username":"marta7e1","password":"Pw-a4a959547aa0","totp":null}}]}""",
        ).items.single().entry
        assertEquals(json.type, csv.type)
        assertEquals(json.title, csv.title)
        assertEquals(json.url, csv.url)
        assertEquals(json.notes, csv.notes)
        assertEquals(json.username, csv.username)
        assertEquals(json.password, csv.password)
        assertEquals(json.favorite, csv.favorite)
    }

    @Test fun bitwardenCsvLimitsAreCheckedAfterTheSplit() {
        val header = "folder,favorite,type,name,notes,fields,reprompt,login_uri,login_username,login_password,login_totp\n"
        // The cell is longer than one URL may be, but each URL fits: the login enters.
        val second = "https://b.example/" + "x".repeat(MAX_URL - 18)
        val fits = ok(header + ",,login,Larga,,,0,\"https://a.example/,$second\",u,p,\n")
        assertEquals("https://a.example/", fits.items.single().entry.url)
        assertEquals("URL: $second", fits.items.single().entry.notes)
        // The first URL over the limit: the whole row is listed, never cut.
        val tooLong = ok(header + ",,login,Larga,,,0,\"https://a.example/${"x".repeat(MAX_URL)},https://b.example/\",u,p,\n")
        assertTrue(tooLong.items.isEmpty())
        assertEquals(listOf("Larga" to SkipReason.FIELD_TOO_LONG), tooLong.skipped.map { it.title to it.reason })
    }

    @Test fun bitwardenCsvIsFoundByItsColumnNames() {
        // I-2: by name, in any order and case; a column Clavero does not know is passed over.
        val cols = "folder,favorite,type,name,notes,fields,reprompt,login_uri,login_username,login_password,login_totp"
        assertEquals(ImportFormat.BITWARDEN, csvFormat(cols.split(',')))
        assertEquals(ImportFormat.BITWARDEN, csvFormat(cols.uppercase().split(',')))
        assertEquals(ImportFormat.BITWARDEN, csvFormat("$cols,extra".split(',')))
        assertEquals(ImportFormat.BITWARDEN, csvFormat(cols.split(',').reversed()))
        assertEquals(ImportFormat.BITWARDEN, csvFormat("collections,type,name,login_uri,login_username,login_password".split(',')))
        // Without login_password, or without folder and collections, it is not Bitwarden's.
        assertNull(csvFormat(cols.split(',').filter { it != "login_password" }))
        assertNull(csvFormat(cols.split(',').drop(1)))
        // Extra and reordered columns read the same items as the fixture.
        val lines = String(fixture("bitwarden.csv"), Charsets.UTF_8).split('\n')
        val reference = ok(fixture("bitwarden.csv"))
        val reordered = reorderCsv(lines, cols.split(',').reversed() + "extra")
        val read = ok(reordered)
        assertEquals(reference.items.map { it.entry }, read.items.map { it.entry })
        assertEquals(reference.items.map { it.folders }, read.items.map { it.folders })
        assertEquals(reference.skipped, read.skipped)
        // A header that misses login_password says the file is not one Clavero knows.
        val noPassword = (listOf(cols.replace("login_password", "contraseña")) + lines.drop(1)).joinToString("\n")
        assertEquals(ReadResult.Failed(IMPORT_UNKNOWN), readImport(noPassword.toByteArray()))
    }

    /** [lines] of a CSV (header first) with its columns written in the order [order]; a name not in it is an empty column. */
    private fun reorderCsv(lines: List<String>, order: List<String>): String {
        val rows = parseCsv(lines.joinToString("\n"))
        val header = rows[0]
        fun quote(c: String) = "\"" + c.replace("\"", "\"\"") + "\""
        return rows.joinToString("\n") { row ->
            order.joinToString(",") { name ->
                val i = header.indexOf(name)
                if (row === header) name else quote(if (i in row.indices) row[i] else "")
            }
        } + "\n"
    }
}
