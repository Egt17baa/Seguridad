package app.seguridad.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Audit 1.3.0, F7: what the app, the privacy policy and the Play listing say about where the data stays.
 * - LIBRE-2 + COV1-3: no absolute «everything stays on this phone». What is decrypted goes through a temporary copy in
 *   the app's private storage, the automatic copy goes to the folder the user picks, and the leak check sends 5
 *   characters of the SHA-1 to Have I Been Pwned.
 * - LIBRE-3: the EFF list is not «used without changes»: the dice-number column was removed (same words, same order).
 * The texts themselves are for the final review of the Abogado Digital; this only keeps the false claims out.
 */
class PublishedTextsTest {
    private fun repo(): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            if (File(dir, "app/src/main/java").isDirectory && File(dir, "docs").isDirectory) return dir
            dir = dir.parentFile
        }
        error("repository root not found")
    }

    private fun read(path: String) = File(repo(), path).readText()

    /** The absolute claims found by the audit, in any case and spacing. */
    private val absoluteClaims = listOf(
        "todo se queda en", "solo en tu móvil", "todo en tu móvil", "todo se guarda cifrado", "sin cuentas ni nube.",
        "sin cuentas, sin nube y", "no tiene cuentas, nube,",
    )

    private fun claimsIn(text: String): List<String> {
        val t = ForbiddenWords.normalise(text)
        return absoluteClaims.filter { it in t }
    }

    @Test
    fun noTextSaysEverythingStaysOnThePhone() {
        val bad = mutableListOf<String>()
        val java = File(repo(), "app/src/main/java")
        for (f in java.walkTopDown().filter { it.isFile && it.extension == "kt" }) {
            for ((line, lit) in kotlinLiterals(f.readText())) claimsIn(lit).forEach { bad += "${f.name}:$line «$it»" }
        }
        for (doc in listOf("docs/privacidad.html", "docs/ficha-google-play.md", "docs/PUBLICAR.md", "README.md")) {
            claimsIn(read(doc)).forEach { bad += "$doc «$it»" }
        }
        assertTrue("Absolute «only on this phone» claims:\n" + bad.joinToString("\n"), bad.isEmpty())
    }

    @Test
    fun theScreensSayWhatLeavesThePhone() {
        // Start notice: the vault stays encrypted here; the exports, shares, the automatic copy and 5 SHA-1 characters
        // leave it.
        for (fact in listOf("cifrada en este móvil", "exportas, guardas o compartes", "copia automática", "carpeta que elijas", "5 primeros caracteres")) {
            assertTrue("NOTICE_INTRO: $fact", fact in NOTICE_INTRO)
        }
        for (fact in listOf("cifrada en este móvil", "copia automática", "carpeta que elijas")) {
            assertTrue("ABOUT_SUMMARY: $fact", fact in ABOUT_SUMMARY)
        }
        for (fact in listOf("Solo se guarda en este móvil", "copia automática", "carpeta que elijas")) {
            assertTrue("VAULT_CREATE_INTRO: $fact", fact in VAULT_CREATE_INTRO)
        }
        // The screens show these constants, not a copy of their own.
        val screens = File(repo(), "app/src/main/java/app/seguridad/ui/screens")
        assertTrue(Regex("\\bNOTICE_INTRO\\b") in File(screens, "NoticeScreen.kt").readText())
        assertTrue(Regex("\\bABOUT_SUMMARY\\b") in File(screens, "SettingsScreen.kt").readText())
        assertTrue(Regex("\\bVAULT_CREATE_INTRO\\b") in File(screens, "VaultScreen.kt").readText())
    }

    @Test
    fun thePolicyNamesTheTemporaryCopiesTheFolderAndTheHashPrefix() {
        val policy = ForbiddenWords.normalise(htmlText(read("docs/privacidad.html")))
        for (fact in listOf("copia temporal sin cifrar", "almacenamiento privado", "carpeta que elijas", "5 primeros caracteres")) {
            assertTrue("privacidad.html: $fact", fact in policy)
        }
        val listing = ForbiddenWords.normalise(read("docs/ficha-google-play.md"))
        for (fact in listOf("carpeta que elijas", "5 primeros caracteres")) assertTrue("ficha: $fact", fact in listing)
    }

    @Test
    fun theShortPlayDescriptionFits() {
        val doc = read("docs/ficha-google-play.md").lines()
        val i = doc.indexOfFirst { it.startsWith("## Descripción breve") }
        val short = doc.drop(i + 1).first { it.isNotBlank() }
        assertTrue("${short.length}: $short", short.length <= 80)
    }

    @Test
    fun thePolicyAndTheListingUseNoForbiddenWord() {
        // The listing's own header names the forbidden words to forbid them: only what goes to Play is checked.
        val listing = read("docs/ficha-google-play.md").substringAfter("## Descripción breve")
        val texts = mapOf("privacidad.html" to htmlText(read("docs/privacidad.html")), "ficha-google-play.md" to listing)
        for ((name, text) in texts) {
            val p = ForbiddenWords.problems(text)
            assertTrue("$name: $p", p.isEmpty())
        }
    }

    @Test
    fun theEffListIsSaidToBeAdaptedEverywhere() {
        // LIBRE-3: the same words in the same order, without the dice-number column. BIP-39 is used without changes.
        val licenses = read("app/src/main/java/app/seguridad/ui/screens/LicensesScreen.kt").lines()
        val eff = licenses.single { "EFF" in it && "Component(" in it }
        assertFalse(eff, "sin cambios" in eff)
        assertTrue(eff, "mismas palabras" in eff && "mismo orden" in eff && "números de dado" in eff)
        assertTrue(licenses.single { "BIP-39" in it && "Component(" in it }.contains("sin cambios"))

        val notice = read("NOTICE")
        val effNotice = notice.substringAfter("EFF Long Wordlist").substringBefore("\n- ")
        assertFalse(effNotice, "without changes" in effNotice)
        assertTrue(effNotice, "dice-number column" in ForbiddenWords.normalise(effNotice))

        val ccBy = read("app/src/main/assets/licenses/cc-by-3.0.txt")
        assertFalse("without changes" in ccBy)
        assertTrue(ccBy.lines().first().startsWith("EFF Long Wordlist"))
        val adapted = ForbiddenWords.normalise(ccBy)
        assertTrue("same words, in the same order" in adapted && "dice-number column" in adapted)
    }

    @Test
    fun htmlTextDropsTagsAndEntities() {
        assertEquals("a b «c»", htmlText("<p>a <b>b</b> &laquo;c&raquo;</p>").trim().replace(Regex("\\s+"), " "))
    }

    /** The visible text of [html]: tags and the style block dropped, the few entities it uses decoded. */
    private fun htmlText(html: String): String = html
        .replace(Regex("<style[\\s\\S]*?</style>"), " ")
        .replace(Regex("<[^>]+>"), " ")
        .replace("&laquo;", "«").replace("&raquo;", "»").replace("&nbsp;", " ").replace("&amp;", "&")
}
