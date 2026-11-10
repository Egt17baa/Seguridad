package app.seguridad.redteam

import app.seguridad.crypto.KdfLock
import app.seguridad.imports.IMPORT_ENCRYPTED_TOO_BIG
import app.seguridad.imports.IMPORT_KDF_TOO_BIG
import app.seguridad.imports.ImportFormat
import app.seguridad.imports.ImportRead
import app.seguridad.imports.ImportedItem
import app.seguridad.imports.ReadResult
import app.seguridad.imports.Secrets
import app.seguridad.imports.compareKey
import app.seguridad.imports.duplicateSubline
import app.seguridad.imports.importPasswordBytes
import app.seguridad.imports.importedEntry
import app.seguridad.imports.pbkdf2Sha256
import app.seguridad.imports.planMerge
import app.seguridad.imports.plainReportText
import app.seguridad.imports.readImport
import app.seguridad.imports.urlHost
import app.seguridad.tools.hashlab.HashLabSession
import app.seguridad.vault.EntryType
import app.seguridad.vault.MASTER_TOO_WEAK
import app.seguridad.vault.MASTER_WORD_LISTS
import app.seguridad.vault.PASSWORD_FIELD_MAX_CHARS
import app.seguridad.vault.VaultEntry
import app.seguridad.vault.masterWords
import app.seguridad.vault.newMasterProblem
import app.seguridad.vault.passwordFieldTakes
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

private fun appAssetLines(name: String): List<String> =
    listOf("src/main/assets", "../app/src/main/assets").map { File(it, name) }.first { it.exists() }.readLines().filter { it.isNotBlank() }

/*
 * Second attack of the red team (R32, owner's decisions (d) and (f)), on the code after the whole-branch review's fix
 * round (b1902ed): fresh attacks around the fixes of RT-1 to RT-3, the new code (KdfLock, the checks before an export's
 * password, the duplicate row, the 65 536 cap, the Hash lab race) and a regression sweep. Same naming as before: rtN_…
 * asserts the SAFE behaviour and failed while RT-N was open (@Ignore'd only for the gate then); resists… passes. RT-4 to
 * RT-7 are fixed (rulings R41 to R44, the second attack's fix round): every test here runs in the gate.
 * Report: docs/superpowers/handoff/sdd-1.4.0/redteam-report.md, «Segundo ataque (8-11-2026)».
 */
class RedTeamSecondAttackTest {
    private val known by lazy { masterWords(MASTER_WORD_LISTS.map(::appAssetLines)) }

    private fun vaultLogin(url: String) =
        VaultEntry("v1", EntryType.LOGIN, "Banco", username = "ana", password = "la-buena", url = url, createdAt = 1, updatedAt = 1)

    private fun fileLogin(title: String = "Banco", user: String = "ana", url: String) =
        ImportedItem(0, importedEntry(EntryType.LOGIN, title, username = user, password = "la-del-atacante", url = url), emptyList())

    private fun plan(file: ImportedItem, vault: VaultEntry) =
        planMerge(ImportRead(ImportFormat.BROWSER_CSV, listOf(file), emptyList(), 0), listOf(vault), revision = 1)

    // ---------------------------------------------------------------------------------------------------------------
    // RT-4: a URL of another scheme (javascript:, intent:, file:, http:) is a duplicate of the https one, and the row
    // does not say the scheme changes.
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * RT-4. `urlHost` keeps only the host (MergePlan.kt:382-389), and `parseLink` reads any `scheme://` as hierarchical, so
     * `javascript://banco.es/%0Aalert(1)`, `intent://banco.es/#Intent;…;end`, `file://banco.es/x` and `http://banco.es/`
     * all have the host banco.es: each is a DUPLICATE of the vault's https://banco.es/ entry. The row says «ana ·
     * banco.es» (`duplicateSubline`, MergePlan.kt:400-406) for all of them, so with «Reemplazar» the vault entry silently
     * takes a URL of another scheme: a downgrade to plain http, or a link that is not a web page at all.
     * Safe behaviour (either fix): not a duplicate, or the row shows the scheme that comes in.
     */
    @Test fun rt4_aUrlOfAnotherSchemeIsNotTakenSilentlyForTheWebOne() {
        val vault = vaultLogin("https://banco.es/")
        for ((url, scheme) in listOf(
            "javascript://banco.es/%0Aalert(1)" to "javascript",
            "intent://banco.es/#Intent;scheme=https;package=com.example.evil;end" to "intent",
            "file://banco.es/sdcard/x.html" to "file",
            "http://banco.es/login" to "http",
        )) {
            val p = plan(fileLogin(url = url), vault)
            val row = duplicateSubline(fileLogin(url = url).entry)
            assertTrue("«$url»: a duplicate whose row «$row» hides the scheme", p.duplicates.isEmpty() || "$scheme:" in row)
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // RT-5: the leet rule (RT-3, decision (h)) misses the substitutions of a Spanish keyboard: ¡ for i, € for e, ø for o.
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * RT-5. `leetLetter` (MasterPolicy.kt:89-99) maps only ASCII digits and `@ $ + ! |`. «¡» (a key of its own on the
     * Spanish keyboard), «€» (AltGr+E) and «ø» stay as they are: «¡» and «€» are not letters, so `masterCore` drops them and
     * the word loses a letter; «ø» is a letter that is in no word. «Pr¡nc3ss2024», «C0ntra$€na2024!» and «Dr4gøn!2024» are
     * accepted as new masters.
     */
    @Test fun rt5_aKnownWordInSpanishKeyboardLeetIsRefused() {
        for (c in listOf("Pr¡nc3ss2024", "C0ntra\$€na2024!", "C0ntr4s€ñ4!!", "Dr4gøn!2024", "P4\$\$wørd2024")) {
            assertEquals("«$c» is one known word", MASTER_TOO_WEAK, newMasterProblem(c, known))
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // RT-6: invisible code points that plainReportText (and so compareKey) keeps: the shadow entry of RT-2 again.
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * RT-6. `plainReportText` (ImportModel.kt) drops Cf and a fixed list of invisibles, and keeps up to three combining
     * marks. Some code points that draw nothing are neither: U+2800 (the Braille blank, a symbol), U+17B4 and U+17B5 (Khmer
     * inherent vowels, Mn), the variation selectors U+FE00–U+FE0F and U+E0100–U+E01EF (Mn) and the Mongolian free
     * variation selectors U+180B–U+180D, U+180F (Mn). «Banco» + any of them is shown as «Banco» and is not a duplicate of
     * «Banco»: RT-2's shadow entry, with another character. `isHiddenInName` (FileCrypto.kt) already drops 17B4/17B5 and
     * FE00–FE0E from file names.
     */
    @Test fun rt6_invisibleCodePointsAreNotShownNorKeptApart() {
        for (c in listOf("⠀", "\u17B4", "\u17B5", "\uFE00", "\uFE0F", "\uDB40\uDD00", "\u180B", "\u180F")) {
            val cp = "U+%04X".format(c.codePointAt(0))
            assertEquals("$cp is shown", "Banco", plainReportText("Banco$c"))
            assertEquals("$cp keeps a look-alike apart", compareKey("Banco"), compareKey("Ban${c}co"))
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // RT-7 (Info, the controller decides): compatibility look-alikes that NFC keeps apart and NFKC would fold.
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * RT-7 (Info). `compareKey` folds with NFC (ImportModel.kt:139-153), which keeps compatibility characters: the
     * ligature «ﬁ» (U+FB01) looks like «fi», «Ⅱ» (U+2161) like «II». With NFKC they would compare equal (as `masterCore`
     * already does since RT-3). Full-width and mathematical letters fold too, though they look different. Not folded by
     * NFKC either, and so only in the report: other scripts' look-alikes (Cyrillic «а»), dash variants (U+2010, U+2212),
     * U+2024 for «.», U+2019 for «'».
     */
    @Test fun rt7_compatibilityLookAlikesCompareEqual() {
        for ((a, b) in listOf("Proﬁle" to "Profile", "Cuenta Ⅱ" to "Cuenta II", "Ｂanco" to "Banco", "𝐁anco" to "Banco")) {
            assertEquals("«$a» vs «$b»", compareKey(b), compareKey(a))
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Resisted: around the fixes of RT-1 to RT-3.
    // ---------------------------------------------------------------------------------------------------------------

    @Test fun resistsAuthorityConfusionsAroundTheRt1Fix() {
        // (URL, the host a browser that follows the URL Standard connects to).
        val cases = listOf(
            "https://evil.example\\@banco.es/" to "evil.example",
            "  HTTPS:\\\\EVIL.example\\@banco.es" to "evil.example",
            "https://evil.example:443\\@banco.es" to "evil.example",
            "evil.example\\@banco.es" to "evil.example",
            "//evil.example\\@banco.es" to "evil.example",
            "https://evil.example%5C@banco.es/" to "banco.es",
            "https://evil.example\t@banco.es" to "banco.es",
            "https://evil.example%09@banco.es" to "banco.es",
            "https://evil.example　@banco.es" to "banco.es",
            "https://banco.es\n.evil.example/" to "banco.es.evil.example",
            "https://banco.es。evil.example" to "banco.es.evil.example",
            "https://evil.example#@banco.es" to "evil.example",
            "https://evil.example?@banco.es" to "evil.example",
            "https://evil.example/@banco.es" to "evil.example",
            "https://banco.es@evil.example" to "evil.example",
            "https://ban%63o.es" to "banco.es",
            "https://ｂａｎｃｏ.es" to "banco.es",
            "https://WWW.BANCO.ES." to "banco.es",
        )
        for ((url, browserHost) in cases) assertEquals("«$url»", browserHost, urlHost(url))
        // IDN: a Cyrillic «а» is its xn-- form, never banco.es; a percent-encoded «@» in the host is no user info.
        assertTrue(urlHost("https://bаnco.es")!!.startsWith("xn--"))
        assertNotEquals("banco.es", urlHost("https://evil.example%40banco.es"))
        // None of them is a duplicate of banco.es unless the browser goes to banco.es.
        val vault = vaultLogin("https://banco.es/")
        for ((url, browserHost) in cases) {
            val dup = plan(fileLogin(url = url), vault).duplicates.isNotEmpty()
            assertEquals("«$url»", browserHost == "banco.es", dup)
        }
    }

    @Test fun resistsDuplicateRowSpoofing() {
        // The row's host is the one the browser goes to, in ASCII, with nothing invisible or turned around.
        for ((url, shown) in listOf(
            "https://evil.example\\@banco.es/" to "ana · evil.example",
            "https://bаnco.es/" to "ana · xn--bnco-53d.es",
            "https://%E2%80%AEse.ocnab/" to null,
            "https://ban\u200Bco.es/" to "ana · banco.es",
        )) {
            val row = duplicateSubline(fileLogin(url = url).entry)
            if (shown != null) assertEquals("«$url»", shown, row)
            assertFalse("«$row»", row.any { Character.getType(it) == Character.FORMAT.toInt() || it < ' ' })
        }
        // A user of the file cannot pose as a host: it is shown as written, plain, before « · ».
        val row = duplicateSubline(fileLogin(user = "ana · banco.es\u202E", url = "https://evil.example/").entry)
        assertTrue(row, row.endsWith(" · evil.example"))
    }

    @Test fun resistsLookAlikesThatCompareKeyFolds() {
        for (c in listOf("\u200B", "\u00AD", "\u2062", "\u180E", "ᅟ", "ᅠ", "ㅤ", "ﾠ", "\u034F", "\u061C", "\u200E", "\uDB40\uDC20", "\t", "　", "\u00A0")) {
            assertEquals("U+%04X".format(c.codePointAt(0)), compareKey("Banco"), compareKey("Banco$c").let { if (c.isBlank()) it.trimEnd() else it })
        }
        assertEquals(compareKey("Café"), compareKey("Cafe\u0301"))
        assertEquals(compareKey("Banco X"), compareKey("Banco　\u00A0 X"))
    }

    @Test fun resistsLeetAroundTheRt3Fix() {
        for (c in listOf("M1ll1on2024!", "Br1ll1ant2024", "L1b3rty2024!", "F4m1l1a2024!", "P4ssw0rd\u200B2024", "P4ss-w0rd-2024", "P4SSW0RD2024!", "Ｐ４ssw0rd2024")) {
            assertEquals("«$c»", MASTER_TOO_WEAK, newMasterProblem(c, known))
        }
        // No false refusal of a random password of the four classes, nor of a phrase of the Generator's kind.
        for (c in listOf("kT9#vQ2!mZp7@Lr4", "cactus-tímido-lunes-vapor-oruga", "R7#q!Xv2@Lm9\$Tz4")) assertNull("«$c»", newMasterProblem(c, known))
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Resisted: the new code of the fix round.
    // ---------------------------------------------------------------------------------------------------------------

    @Test fun resistsKdfLockStarvationAndCancellation() = runBlocking {
        val held = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val order = Collections.synchronizedList(mutableListOf<String>())
        // A hostile import's derivation holds the lock and its caller is cancelled: the lock stays held until it ends.
        val hostile = launch(Dispatchers.Default) {
            KdfLock.derive(Dispatchers.Default) {
                held.complete(Unit)
                release.await(10, TimeUnit.SECONDS)
                order += "import"
            }
        }
        held.await()
        hostile.cancel()
        yield()
        assertTrue(KdfLock.isLocked)
        // The unlock asked first runs first (the Mutex is fair); a waiter cancelled before its turn never runs.
        val unlock = async(Dispatchers.Default) { KdfLock.derive(Dispatchers.Default) { order += "unlock" } }
        delay(50)
        val dropped = AtomicBoolean(false)
        val gone = launch(Dispatchers.Default) { KdfLock.derive(Dispatchers.Default) { dropped.set(true) } }
        delay(50)
        val check = async(Dispatchers.Default) { KdfLock.derive(Dispatchers.Default) { order += "check" } }
        delay(50)
        gone.cancel()
        release.countDown()
        withTimeout(10_000) {
            unlock.await()
            check.await()
        }
        assertEquals(listOf("import", "unlock", "check"), order.toList())
        assertFalse(dropped.get())
        assertFalse(KdfLock.isLocked)
    }

    @Test fun resistsDeriveBlockingOnTheMainThread() {
        val ran = AtomicBoolean(false)
        try {
            KdfLock.deriveBlocking({ true }) { ran.set(true) }
            fail("waited on the main thread")
        } catch (e: IllegalStateException) {
            assertFalse(ran.get())
        }
        assertEquals(7, KdfLock.deriveBlocking({ false }) { 7 })
    }

    private fun bitwardenHeader(kdf: String, data: String) = """
        {"encrypted":true,"passwordProtected":true,"salt":"c2FsdA==",$kdf,
         "encKeyValidation_DO_NOT_EDIT":"2.AAAAAAAAAAAAAAAAAAAAAA==|AAAAAAAAAAAAAAAAAAAAAA==|AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
         "data":"$data"}
    """.trimIndent().toByteArray()

    @Test fun resistsWorkBeforeTheExportPassword() {
        val ok = "\"kdfType\":0,\"kdfIterations\":600000"
        val small = "2.AAAAAAAAAAAAAAAAAAAAAA==|AAAAAAAAAAAAAAAAAAAAAA==|AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
        val start = System.nanoTime()
        // Out-of-range KDF and an oversized part are refused before the password is asked, and nothing is derived.
        assertEquals(ReadResult.Failed(IMPORT_KDF_TOO_BIG), readImport(bitwardenHeader("\"kdfType\":0,\"kdfIterations\":2000000000", small)))
        assertEquals(
            ReadResult.Failed(IMPORT_KDF_TOO_BIG),
            readImport(bitwardenHeader("\"kdfType\":1,\"kdfIterations\":3,\"kdfMemory\":100000,\"kdfParallelism\":4", small)),
        )
        val tooBig = "2.AAAAAAAAAAAAAAAAAAAAAA==|" + "A".repeat(8 * 1024 * 1024 + 4) + "|AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
        assertEquals(ReadResult.Failed(IMPORT_ENCRYPTED_TOO_BIG), readImport(bitwardenHeader(ok, tooBig)))
        // Near the largest part the reader takes (8 MiB of Base64; 6 MB of whole AES blocks) is only decoded, once: no derivation, a few hundred ms.
        val biggest = "2.AAAAAAAAAAAAAAAAAAAAAA==|" + "A".repeat(64 * 131_000) + "|AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
        assertEquals(ReadResult.NeedsPassword(ImportFormat.BITWARDEN), readImport(bitwardenHeader(ok, biggest)))
        // Aegis: scrypt of 2 GiB in the first slot, and 17 slots.
        val slot = """{"type":1,"n":1048576,"r":16,"p":1,"salt":"${"00".repeat(32)}","key":"${"00".repeat(32)}",
            "key_params":{"nonce":"${"00".repeat(12)}","tag":"${"00".repeat(16)}"}}"""
        val aegis = """{"header":{"slots":[$slot],"params":{"nonce":"${"00".repeat(12)}","tag":"${"00".repeat(16)}"}},"db":"AAAA"}"""
        assertEquals(ReadResult.Failed(IMPORT_KDF_TOO_BIG), readImport(aegis.toByteArray()))
        val ms = (System.nanoTime() - start) / 1_000_000
        assertTrue("$ms ms", ms < 5_000)
        assertFalse(KdfLock.isLocked)
    }

    @Test fun resistsThePasswordFieldCap() {
        assertTrue(passwordFieldTakes("a".repeat(PASSWORD_FIELD_MAX_CHARS)))
        assertFalse(passwordFieldTakes("a".repeat(PASSWORD_FIELD_MAX_CHARS + 1)))
        // A password at the cap, of 3-byte characters (192 KiB in UTF-8): HMAC hashes a long key once, so PBKDF2 costs the
        // same as with a short one, and the bytes are wiped.
        val s = Secrets()
        val longPw = CharArray(PASSWORD_FIELD_MAX_CHARS) { '€' }
        val t0 = System.nanoTime()
        val bytes = importPasswordBytes(longPw, s)
        pbkdf2Sha256(bytes, ByteArray(16), 100_000, 32, s)
        val tLong = System.nanoTime() - t0
        val t1 = System.nanoTime()
        pbkdf2Sha256(importPasswordBytes("corta".toCharArray(), s), ByteArray(16), 100_000, 32, s)
        val tShort = System.nanoTime() - t1
        assertEquals(3 * PASSWORD_FIELD_MAX_CHARS, bytes.size)
        assertTrue("long ${tLong / 1_000_000} ms, short ${tShort / 1_000_000} ms", tLong < tShort * 4 + 500_000_000)
        s.wipe()
        assertTrue(bytes.all { it == 0.toByte() })
    }

    @Test fun resistsTheHashLabProgressRace() {
        val session = HashLabSession()
        val old = session.startFile()
        val stop = AtomicBoolean(false)
        val reader = Thread {
            var n = 1L
            while (!stop.get()) session.progress(old, n++)
        }
        reader.start()
        try {
            repeat(20_000) {
                val mine = session.startFile()
                // Right after a new file starts, nothing of the old reading can be on it.
                assertEquals(0L, session.readBytes)
                assertTrue(session.progress(mine, 5))
                assertEquals(5L, session.readBytes)
            }
        } finally {
            stop.set(true)
            reader.join()
        }
        session.wipe()
        assertEquals(0L, session.readBytes)
        assertNull(session.file.hash)
    }
}
