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
}
