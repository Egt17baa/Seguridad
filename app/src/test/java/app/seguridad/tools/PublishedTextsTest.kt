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
        // Start notice: the vault stays encrypted here; the exports, saves, copies to the clipboard, shares, the
        // automatic copy and 5 SHA-1 characters leave it.
        for (fact in listOf("cifrada en este móvil", "exportas, guardas, copias al portapapeles o compartes", "copia automática", "carpeta que elijas", "5 primeros caracteres")) {
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
        for (fact in listOf("copia temporal sin cifrar", "almacenamiento privado", "carpeta que elijas", "5 primeros caracteres", "portapapeles del sistema", "solo en este móvil")) {
            assertTrue("privacidad.html: $fact", fact in policy)
        }
        // 2.0, O-3 of the Abogado Digital: the text that is pasted in Play is the short full description (annex A).
        val listing = ForbiddenWords.normalise(playFullDescription())
        for (fact in listOf("donde elijas", "5 primeros caracteres", "exportas, guardas, copias o compartes")) assertTrue("ficha: $fact", fact in listing)
    }

    /**
     * 2.1.1, tarea 4 (O-7 y R-4 del dictamen del principio; Ruling del registro: «Google Play» y los textos anteriores no
     * cuentan): los textos nuevos de la 2.1.1 no nombran a nadie más ni prometen de más. Son las notas de GitHub y las
     * «Novedades» (los bloques de código de las notas), «La 2.1.1», la línea «Aspecto (2.1.1)» y «Pruebas de la 2.1.1» del
     * README, la línea de los estilos de la ficha y `STYLE_MORE`. Además: nada de «siempre» más translúcida (m-2 de la
     * tarea 1), de una pastilla que «se funde» (m-5 de la tarea 2) ni de medidas «de la guía» (m-6 de la tarea 2).
     */
    @Test
    fun the211TextsNameNoOneElse() {
        val texts = the211Texts()
        assertEquals(texts.keys.toString(), 7, texts.size)
        val banned = listOf(
            "material 3", "expressive", "de google", "como en", "líquid", "liquid", "accesible", "se funde", "siempre",
            "de la guía", "guía pública", "nuevo diseño", "exclusiv", "único", "única",
            // M-2 of the task 4 review: in «Transparente» what shows more is the app's layer, never the wallpaper.
            "fondo de cada estilo", "con los colores del fondo de pantalla",
        )
        for ((name, text) in texts) {
            assertTrue(name, text.length > 40)
            val t = ForbiddenWords.normalise(text)
            for (b in banned) assertFalse("$name: «$b»", b in t)
            // «Google» only as «Google Play».
            assertFalse("$name: Google", Regex("google(?! play)").containsMatchIn(t))
            assertTrue("$name: ${ForbiddenWords.problems(text)}", ForbiddenWords.problems(text).isEmpty())
            assertTrue("$name: ${lookNameProblems(text)}", lookNameProblems(text).isEmpty())
        }
        // The bar's line is the same in the README, GitHub and «Novedades» (M-2: «el fondo de la app», «la paleta …»).
        val line = "La barra de abajo flota, redondeada, en «Degradado», «Cristal», «Vidrio» y «Transparente». En los tres " +
            "últimos, desde Android 12, deja ver más el fondo de la app; con «Personalizado» y con la paleta «Colores del " +
            "fondo de pantalla», al menos tanto como antes. «Sólido» no cambia."
        assertEquals(line, texts.getValue("Novedades"))
        assertTrue(line in texts.getValue("GitHub") && texts.getValue("README, Aspecto (2.1.1)").endsWith(line))
        val news = texts.getValue("Novedades")
        assertTrue(news.length.toString(), news.length < 500 && news.codePointCount(0, news.length) < 500)
    }

    private fun the211Texts(): Map<String, String> {
        val notes = read("docs/superpowers/handoff/2026-11-24-clavero-2.1.1-notas.md")
        fun block(after: String) = notes.substringAfter(after).substringAfter("```\n").substringBefore("\n```").trim()
        val readme = read("README.md")
        assertTrue("**Pruebas de la 2.1.1.**" in readme && "## GitHub" in notes && "## Google Play, «Novedades»" in notes)
        fun line(prefix: String) = readme.lines().single { it.startsWith(prefix) }
        return mapOf(
            "GitHub" to block("## GitHub"),
            "Novedades" to block("## Google Play, «Novedades»"),
            "README, La 2.1.1" to line("**La 2.1.1**"),
            "README, Aspecto (2.1.1)" to line("- **Aspecto (2.1.1):**"),
            "README, Pruebas de la 2.1.1" to readme.substringAfter("**Pruebas de la 2.1.1.**").substringBefore("\n## "),
            "ficha" to read("docs/ficha-google-play.md").lines().single { it.startsWith("• Cinco estilos:") },
            // From the source: jvm-check leaves `ui` out; AppearanceTextsTest fixes the same text word for word.
            "STYLE_MORE" to kotlinLiterals(read("app/src/main/java/app/seguridad/ui/screens/AppearanceScreen.kt"))
                .map { it.second }.single { it.startsWith("En «Cristal», «Vidrio» y «Transparente», la barra de abajo") },
        )
    }

    /** 2.0, O-3 and R-8 of the Abogado Digital: the full description fits Play's 4 000, also if a line break counted twice. */
    @Test
    fun theFullPlayDescriptionFits() {
        val full = playFullDescription()
        assertTrue(full.length.toString(), full.length in 1000..4000)
        assertTrue((full.length + full.count { it == '\n' }).toString(), full.length + full.count { it == '\n' } <= 4000)
        assertTrue("Sin recuperación" in full && "Sin auditoría independiente" in full)
        assertFalse("**" in full)
    }

    /** What is pasted in Play as the full description: from «## Descripción completa» to the next heading. */
    private fun playFullDescription(): String =
        read("docs/ficha-google-play.md").substringAfter("## Descripción completa\n").substringBefore("\n## ").trim()

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

    /**
     * 2.1, Aspecto, R-A2 (O-A1): the README, the Play listing and the policy never name a style after another system's
     * look. The listing's header states the rule (it names «™» to forbid it): only what goes to Play is checked.
     */
    @Test
    fun theReadmeTheListingAndThePolicyNameNoLookAfterAnotherOne() {
        val texts = mapOf(
            "README.md" to read("README.md"),
            "ficha-google-play.md" to read("docs/ficha-google-play.md").substringAfter("## Descripción breve"),
            "privacidad.html" to htmlText(read("docs/privacidad.html")),
        )
        for ((name, text) in texts) {
            assertTrue(name, text.length > 1000)
            val p = lookNameProblems(text)
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
    /**
     * Audit B-4 (a): the offer of «Copiar contraseña» is said where it happens, in split screen or a floating window;
     * full screen, leaving Clavero locks the vault and ends it. Never «al volver a la app ofrece».
     */
    @Test
    fun theTwoStepCopyIsSaidWhereItHappens() {
        val readme = read("README.md")
        assertTrue(
            "tras «Copiar usuario», si Clavero comparte la pantalla con otra app (pantalla dividida o ventana flotante), " +
                "ofrece «Copiar contraseña» durante 30 segundos; a pantalla completa, salir de Clavero bloquea la bóveda y la " +
                "oferta se acaba." in readme,
        )
        assertTrue(
            "Tras «Copiar usuario», con Clavero en pantalla dividida, tocar la otra app antes de 30 s: Clavero ofrece " +
                "«Copiar contraseña». A pantalla completa, salir y volver: la bóveda está bloqueada y no se ofrece." in readme,
        )
        val notes = read("docs/superpowers/handoff/2026-11-15-clavero-2.0-notas.md")
        assertTrue("tras «Copiar usuario», en pantalla dividida o en una ventana flotante, ofrece «Copiar contraseña» durante 30 segundos" in notes)
        for ((doc, text) in listOf("README.md" to readme, "notas" to notes, "ficha" to read("docs/ficha-google-play.md"))) {
            val t = ForbiddenWords.normalise(text)
            for (old in listOf("al volver a la app ofrece", "al volver ofrece «copiar contraseña»", "ir a otra app y volver antes de 30 s")) {
                assertFalse("$doc: $old", ForbiddenWords.normalise(old) in t)
            }
        }
    }
}
