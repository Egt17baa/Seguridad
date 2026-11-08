package app.seguridad.imports

import app.seguridad.tools.ForbiddenWords
import app.seguridad.tools.kotlinLiterals
import app.seguridad.vault.EntryType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Rulings R2, R7, R8 and R8a: the names of other products appear only in the string literals of `imports/ImportTexts.kt`
 * (Task 9 adds the screen's texts to this test). Outside it, only the exact literals of [ALLOWED] may name one: texts
 * from before 1.4.0 and a licence attribution. No word is exempted: a new literal with «Google» anywhere else fails.
 * Comments and Kotlin identifiers are not visible text and are not checked.
 */
class ImportTextsTest {
    /**
     * Product names, found in any case as a part of a literal, so a name glued to other letters («GoogleDrive»,
     * «misBitwarden», «MiEdge») is caught too: the formats the import screen reads, and the two products the vault form
     * named as examples before 1.4.0 («Binance» and «Ledger»), so they cannot come back.
     */
    private val names = listOf(
        "bitwarden", "aegis", "2fas", "firefox", "mozilla", "apple", "safari", "icloud", "chrome", "chromium", "brave", "steam",
        "keepass", "google", "microsoft", "edge", "binance", "ledger",
    )

    /**
     * Ordinary words that contain a name of [names] and are not that product, found as whole words and taken out before
     * the search. Empty: no literal of the app has one today («edge» was only inside «Ledger», now gone). A word joins
     * this list only when a real text needs it, written here in lower case.
     */
    private val ordinaryWords = emptyList<String>()

    /** The only file where any literal may name another product. */
    private val TEXTS_FILE = "app/seguridad/imports/ImportTexts.kt"

    /**
     * The exact literals (as [kotlinLiterals] gives them: a chain joined by `+` is one, a `${}` template drops out)
     * that may name another product, by file (path under `app/src/main/java`):
     * - R8: «Google Authenticator» and «la copia de Google», texts from before 1.4.0 that passed the 1.3.x review;
     * - R8a: the attribution of Guava's licence, which the licence requires.
     */
    private val ALLOWED = setOf(
        "app/seguridad/ui/screens/VaultEntryScreen.kt" to "El QR de Google Authenticator no es válido o está dañado",
        "app/seguridad/ui/screens/VaultEntryScreen.kt" to "Importados  códigos de Google Authenticator. Esta entrada sigue abierta: guárdala si la has cambiado.",
        "app/seguridad/ui/screens/VaultEntryScreen.kt" to "Importar de Google Authenticator",
        "app/seguridad/tools/ToolTexts.kt" to "Guarda contraseñas, frases semilla de tus carteras de criptomonedas, notas y códigos 2FA, cifrados con " +
            "AES-256-GCM y una clave derivada de tu contraseña maestra con Argon2id. Solo se guarda en este móvil (no va a la copia " +
            "de Google); puedes exportar una copia cifrada o activar la copia automática en una carpeta que elijas.",
        "app/seguridad/ui/screens/LicensesScreen.kt" to "https://github.com/google/guava, https://github.com/JetBrains/java-annotations y https://jspecify.dev",
    )

    private fun mainRoot(): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            File(dir, "app/src/main/java").takeIf { it.isDirectory }?.let { return it }
            File(dir, "src/main/java").takeIf { it.isDirectory }?.let { return it }
            dir = dir.parentFile
        }
        error("app/src/main/java not found")
    }

    /** Every Kotlin source under `app/src/main/java`, by its path there, with its text. */
    private fun mainSources(): Map<String, String> {
        val java = mainRoot()
        return java.walkTopDown().filter { it.isFile && it.extension == "kt" }
            .associate { it.relativeTo(java).invariantSeparatorsPath to it.readText() }
    }

    private fun namesIn(text: String, exceptions: List<String> = ordinaryWords): List<String> {
        var lower = text.lowercase()
        for (w in exceptions) lower = Regex("(?<![\\p{L}\\p{N}])${Regex.escape(w)}(?![\\p{L}\\p{N}])").replace(lower, " ")
        return names.filter { it in lower }
    }

    /** Each literal of [sources] that names another product and is neither in [TEXTS_FILE] nor in [ALLOWED]. */
    private fun violations(sources: Map<String, String>): List<String> = sources.flatMap { (path, src) ->
        if (path == TEXTS_FILE) emptyList()
        else kotlinLiterals(src).filter { (_, lit) -> namesIn(lit).isNotEmpty() && (path to lit) !in ALLOWED }
            .map { (line, lit) -> "$path:$line ${namesIn(lit)} «$lit»" }
    }

    @Test fun otherProductNamesOnlyInImportTexts() {
        val sources = mainSources()
        assertTrue("sources not found", TEXTS_FILE in sources && sources.size > 50)
        val bad = violations(sources)
        assertTrue("Other products' names outside ImportTexts.kt:\n" + bad.joinToString("\n"), bad.isEmpty())
    }

    @Test fun everyAllowedLiteralStillExists() {
        // A list that no longer matches the code must not stay behind, allowing a text that is gone.
        val sources = mainSources()
        for ((path, lit) in ALLOWED) {
            val src = sources[path] ?: error("$path not found")
            assertTrue("$path: «$lit» is no longer there; take it off ALLOWED", kotlinLiterals(src).any { it.second == lit })
        }
    }

    @Test fun aNewLiteralWithANameFailsWhereverItIs() {
        val vault = "app/seguridad/ui/screens/VaultEntryScreen.kt"
        // The word alone is not allowed, not even in a file of the list or right next to an allowed literal.
        assertEquals(1, violations(mapOf(vault to "val a = \"Importar de Google Authenticator\"\nval b = \"Importar de Google Contraseñas\"")).size)
        assertEquals(1, violations(mapOf("app/seguridad/tools/Otra.kt" to "val a = \"Importar de Google Authenticator\"")).size)
        assertEquals(1, violations(mapOf("app/seguridad/imports/Detect.kt" to "val a = \"Formato de \" + \"Bitwarden\"")).size)
        assertEquals(0, violations(mapOf(TEXTS_FILE to "val a = \"importado-2fas\"", vault to "val a = \"Importar de Google Authenticator\"")).size)
    }

    @Test fun theCheckFindsANameInALiteral() {
        assertTrue(namesIn("Código de Steam").isNotEmpty())
        assertTrue(namesIn("importado-2fas").isNotEmpty())
        assertTrue(namesIn("GOOGLE").isNotEmpty())
        assertTrue(namesIn("importado-navegador").isEmpty())
        // Glued to other letters or digits, in any case: no name is matched as a whole word only.
        for (t in listOf("GoogleDrive", "misBitwarden", "copia2FASx", "iCloudDrive", "MicrosoftEdge", "msedge.exe", "Edge", "EdgeBrowser", "MiEdge", "mi_edge", "EDGE2"))
            assertTrue(t, namesIn(t).isNotEmpty())
        // The form's examples from before 1.4.0 are names now, glued or not; the generic ones that replace them are not.
        for (t in listOf("Nombre (p. ej. «Binance» o «Ledger»)", "MiBinance", "LedgerNano", "knowledge"))
            assertTrue(t, namesIn(t).isNotEmpty())
        assertTrue(namesIn("Nombre (p. ej. «Correo» o «Cartera»)").isEmpty())
    }

    @Test fun anOrdinaryWordIsTakenOutOnlyAsAWholeWord() {
        // How an entry of [ordinaryWords] would work: the whole word is not a name, but the product glued to it or next
        // to it still is.
        val words = listOf("knowledge")
        assertTrue(namesIn("knowledge", words).isEmpty())
        assertTrue(namesIn("Knowledge base", words).isEmpty())
        for (t in listOf("knowledgeEdge", "MiEdge knowledge", "knowledge, Edge", "knowledges"))
            assertTrue(t, namesIn(t, words).isNotEmpty())
    }

    @Test fun theVaultFormNamesNoProduct() {
        // Final fix wave: the name field's examples are generic.
        val entry = mainSources()["app/seguridad/ui/screens/VaultEntryScreen.kt"] ?: error("VaultEntryScreen.kt")
        val lits = kotlinLiterals(entry).map { it.second }
        assertTrue("Nombre (p. ej. «Correo» o «Cartera»)" in lits)
        assertFalse(lits.any { "Binance" in it || "Ledger" in it })
    }

    // ---- Task 9: the texts of the «Importar de otra app» screen ----

    /** Every fixed text the screen shows, and samples of those it builds. */
    private val screenTexts = listOf(
        IMPORT_TITLE, IMPORT_INTRO, IMPORT_FORMATS, IMPORT_TRADEMARKS, IMPORT_NOT_PROTECTED, IMPORT_WIPE_NOTE, IMPORT_PICK,
        IMPORT_PICK_OTHER, IMPORT_READING, IMPORT_CANCEL, IMPORT_READ_FAILED, IMPORT_PASSWORD_LABEL,
        IMPORT_PASSWORD_OPEN, IMPORT_PASSWORD_TOO_LONG, IMPORT_KDF_BUSY, IMPORT_PREVIEW_TITLE,
        IMPORT_DUPLICATES_ALL, IMPORT_REPLACE_NOTE, IMPORT_CHOICE_SKIP, IMPORT_CHOICE_KEEP_BOTH, IMPORT_CHOICE_REPLACE,
        IMPORT_SKIPPED_TITLE, IMPORT_NO_TITLE, IMPORT_NEW_TAGS_NONE, IMPORT_CONFIRM, IMPORT_CONFIRM_NOTE, IMPORT_ESTIMATING,
        IMPORT_NOTHING_TO_DO, IMPORT_SAVING, IMPORT_CHOOSE_COPY_PLACE, IMPORT_CHOOSE_COPY_PLACE_BUTTON, IMPORT_COPY_CANCELLED,
        IMPORT_COPY_DOCUMENT_FAILED, IMPORT_VAULT_CHANGED, IMPORT_DONE_TITLE, IMPORT_SKIPPED_DONE_TITLE, IMPORT_REMOVE,
        IMPORT_REMOVE_EXPLAIN, IMPORT_REMOVE_DECLINE, IMPORT_REMOVING, IMPORT_REMOVED, IMPORT_REMOVE_FAILED,
        IMPORT_REMOVE_UNSUPPORTED, IMPORT_REMOVE_DECLINED, IMPORT_ENDED_TITLE,
        IMPORT_WIPED_WHILE_SAVING, importFoldersNotTagsText(1), importFoldersNotTagsText(19_900),
        importNewTagsText((1..30).map { "t$it" }),
        importTooBigText(5L * 1024 * 1024, 3), importDoneText(2, 1), importDoneText(0, 0),
        previewEntersText(mapOf(EntryType.LOGIN to 2, EntryType.TOTP to 1)), importReplacedText(2), importDuplicatesText(4),
        importRepeatedText(2), importBecameNotesText(1), importNewTagsText(listOf("importado-bitwarden", "casa")),
        importCopyWhereText("copia.sgbak", inAutoFolder = true), importCopyWhereText("copia.sgbak", inAutoFolder = false),
        importSkippedLine("Banco", SkipReason.PASSKEY.text), importFileText("export.csv"),
        // Task 10 (rulings R19, R21).
        IMPORT_WRONG_PASSWORD, IMPORT_KDF_TOO_BIG, IMPORT_ENCRYPTED_TOO_BIG, IMPORT_ZIP, IMPORT_HTML, IMPORT_GOOGLE_AUTHENTICATOR,
        IMPORT_BITWARDEN_ACCOUNT_ENCRYPTED,
    ) + ImportFormat.entries.map(::importPasswordIntro)

    @Test fun theScreenTextsPassTheForbiddenWordRules() {
        for (t in screenTexts) assertEquals(t, emptyList<String>(), ForbiddenWords.problems(t))
    }

    @Test fun theTrademarkLineIsVerbatimAndOnTheScreen() {
        assertEquals("Los nombres de otras apps son marcas de sus titulares. Clavero no tiene relación con ellos.", IMPORT_TRADEMARKS)
        val screen = mainSources()[SCREEN_FILE] ?: error("$SCREEN_FILE not found")
        assertTrue("Text(IMPORT_TRADEMARKS" in screen)
    }

    @Test fun theScreenSaysWhatItDoesNotProtect() {
        for (place in listOf("nube", "papelera", "otras apps")) assertTrue(place, place in IMPORT_NOT_PROTECTED)
        assertTrue("Text(IMPORT_NOT_PROTECTED" in (mainSources()[SCREEN_FILE] ?: error("$SCREEN_FILE not found")))
    }

    @Test fun removingTheExportedFileSaysWhatItDoesNotErase() {
        for (place in listOf("nube", "papelera", "otras apps")) assertTrue(place, place in IMPORT_REMOVE_EXPLAIN)
        assertTrue("no garantiza" in IMPORT_REMOVE_EXPLAIN)
        // M-7: some providers do not let Clavero delete: both texts say it only tries.
        for (t in listOf(IMPORT_REMOVE_EXPLAIN, IMPORT_NOT_PROTECTED)) {
            assertTrue(t, "puede intentar borrar" in t && "algunas apps no lo dejan" in t)
            assertFalse(t, "Clavero puede borrar" in t)
        }
        // The explanation is shown with the offer, and a failure or a provider that cannot delete says «a mano».
        for (t in listOf(IMPORT_REMOVE_FAILED, IMPORT_REMOVE_UNSUPPORTED)) assertTrue(t, "a mano" in t)
        // R33 (Task 12 review, I-3): since Task 10 a password-protected export is read too, so neither text may say
        // the exported file is unencrypted as a fact: it is only when it was exported without a password. Re-review
        // N-1: IMPORT_NOT_PROTECTED is shown on every step, also next to the «Cuenta restringida» error (a file that is
        // encrypted without a password), so it says «suele», as the Play listing does.
        assertEquals(
            "Clavero no protege el archivo exportado. Un archivo exportado sin contraseña suele estar sin cifrar en tu " +
                "móvil; si lo protegiste con contraseña, sus entradas siguen cifradas con esa contraseña. En los dos casos puede " +
                "haber copias en la nube, en la papelera o en otras apps. Al terminar, Clavero puede intentar borrar ese " +
                "archivo (algunas apps no lo dejan), pero no esas copias.",
            IMPORT_NOT_PROTECTED,
        )
        assertEquals(
            "El archivo que has importado sigue en tu móvil, sin cifrar si lo exportaste sin contraseña. Clavero puede " +
                "intentar borrar ese mismo archivo; algunas apps no lo dejan. No borra las copias que haya en la nube, en " +
                "la papelera ni en otras apps, y no garantiza que el almacenamiento lo borre físicamente.",
            IMPORT_REMOVE_EXPLAIN,
        )
        for (t in listOf(IMPORT_REMOVE_EXPLAIN, IMPORT_NOT_PROTECTED)) {
            assertFalse(t, "está sin cifrar en tu móvil, y" in t || "sigue en tu móvil sin cifrar" in t)
        }
        val screen = mainSources()[SCREEN_FILE] ?: error("$SCREEN_FILE not found")
        assertTrue("Text(IMPORT_REMOVE_EXPLAIN" in screen)
    }

    @Test fun theScreenNamesNoOtherProductOutsideImportTexts() {
        val sources = mainSources()
        assertTrue(SCREEN_FILE in sources)
        assertEquals(emptyList<String>(), violations(mapOf(SCREEN_FILE to sources.getValue(SCREEN_FILE))))
        // Where the screen says which formats it reads: the names are in ImportTexts.kt.
        assertTrue(namesIn(IMPORT_FORMATS).size >= 5)
        assertFalse(namesIn(IMPORT_TRADEMARKS).isNotEmpty())
    }

    private val SCREEN_FILE = "app/seguridad/ui/screens/ImportScreen.kt"

    // ---- Task 10: rulings R19 and R21 ----

    @Test fun bitwardenAccountExportNamesTheOptionsAsBitwardenShowsThem() {
        assertEquals(
            "Esta exportación está cifrada con tu cuenta de Bitwarden y solo Bitwarden puede abrirla. Vuelve a exportar " +
                "con el formato «.json», o con «.json (Encrypted)» y el tipo «Protegida con contraseña» (no «Cuenta restringida»).",
            IMPORT_BITWARDEN_ACCOUNT_ENCRYPTED,
        )
    }

    @Test fun theFormatsTextSaysWhatIsReadAndWhatIsNot() {
        for (part in listOf("Bitwarden", "«Protegida con contraseña»", "cifrad", "Aegis", "2FAS", "TXT", "otpauth://", ".zip", "HTML"))
            assertTrue(part, part in IMPORT_FORMATS)
        assertFalse("todavía no" in IMPORT_FORMATS)
    }

    @Test fun googleAuthenticatorHelpNamesTheRealButtons() {
        // R21: its export QR cannot be captured, so it is scanned from another device with the vault's scanner.
        val t = IMPORT_GOOGLE_AUTHENTICATOR
        for (part in listOf("Google Authenticator", "otro dispositivo", "«Añadir»", "«${EntryType.TOTP.label}»", "«${app.seguridad.tools.QR_SCAN}»", "Aegis"))
            assertTrue(part, part in t)
        // M-7: several QR codes when there are many accounts (Google's help), the screenshot block on Android (2FAS's
        // help), and Aegis's option by its Spanish name; each scanned one by one.
        for (part in listOf("uno o varios códigos QR", "escanéalos uno a uno", "en Android", "«Exportar a Google Authenticator»"))
            assertTrue(part, part in t)
        assertFalse("como un código QR" in t)
        val screen = mainSources()[SCREEN_FILE] ?: error("$SCREEN_FILE not found")
        assertTrue("Text(IMPORT_GOOGLE_AUTHENTICATOR" in screen)
        // The vault's «Añadir» button and the TOTP form's scanner are still there under those names.
        val vault = mainSources()["app/seguridad/ui/screens/VaultScreen.kt"] ?: error("VaultScreen.kt")
        assertTrue("\"Añadir\"" in vault)
        val entry = mainSources()["app/seguridad/ui/screens/VaultEntryScreen.kt"] ?: error("VaultEntryScreen.kt")
        assertTrue("ScanQrLabel()" in entry && "otpauth-migration://" in entry)
    }

    @Test fun eachFormatsPasswordPromptNamesItsPassword() {
        assertTrue("bóveda de Aegis" in importPasswordIntro(ImportFormat.AEGIS))
        assertTrue("2FAS" in importPasswordIntro(ImportFormat.TWOFAS))
        assertTrue("Bitwarden" in importPasswordIntro(ImportFormat.BITWARDEN))
        assertTrue("Text(importPasswordIntro(" in (mainSources()[SCREEN_FILE] ?: error("$SCREEN_FILE not found")))
    }
}
