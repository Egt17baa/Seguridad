package app.seguridad.tools

import app.seguridad.crypto.Dialect
import app.seguridad.crypto.ENCRYPTED_EXTENSION
import app.seguridad.crypto.pk.CARD_TEXT_PREFIX
import app.seguridad.crypto.pk.MESSAGE_PREFIX
import app.seguridad.crypto.pk.SIGNATURE_EXTENSION
import app.seguridad.crypto.pk.SIGNATURE_PREFIX
import app.seguridad.vault.AUTO_BACKUP_PREFIX
import app.seguridad.vault.BACKUP_MARKER
import app.seguridad.vault.VaultFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Task 16 (owner's decision, 31-10-2026): the app's visible name is «Clavero» (before, «Seguridad»). Only the name
 * the user sees changes; the installed identity, the Kotlin package, the formats and the clipboard label stay, so
 * that 1.2.x data, copies and clipboard contents are still recognised.
 */
class AppNameTest {
    private fun repo(): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            if (File(dir, "app/src/main/java").isDirectory && File(dir, "docs").isDirectory) return dir
            dir = dir.parentFile
        }
        error("repository root not found")
    }

    private fun read(path: String) = File(repo(), path).readText()

    /** The old name as a word, case-sensitive: «seguridad» (the common noun, or a format name) is fine. */
    private val oldName = Regex("\\bSeguridad\\b")

    /** [text] without links and the contact address, which still say «Seguridad» (the repository keeps its name). */
    private fun withoutAddresses(text: String) = text
        .replace(Regex("""href="[^"]*""""), "")
        .replace(Regex("""(https?://)?[\w.-]*(github\.com|github\.io)/[\w./#-]*"""), "")
        .replace(Regex("""[\w.]+@[\w.]+"""), "")

    /** Each «Seguridad» left in [text], with what surrounds it, except «antes «Seguridad»» and [allowed]. */
    private fun oldNamesIn(text: String, allowed: List<String> = emptyList()): List<String> {
        var t = withoutAddresses(text).replace("antes «Seguridad»", "")
        allowed.forEach { t = t.replace(it, "") }
        return oldName.findAll(t).map { t.substring((it.range.first - 30).coerceAtLeast(0), (it.range.last + 30).coerceAtMost(t.length - 1)) }.toList()
    }

    @Test
    fun theLauncherLabelIsClavero() {
        val strings = read("app/src/main/res/values/strings.xml")
        assertTrue(strings, "<string name=\"app_name\">Clavero</string>" in strings)
        assertTrue("@string/app_name" in read("app/src/main/AndroidManifest.xml"))
    }

    @Test
    fun noAppTextNamesTheAppSeguridad() {
        // Only the clipboard label keeps the old name (below); the links to the repository are not texts.
        val bad = mutableListOf<String>()
        val java = File(repo(), "app/src/main/java")
        for (f in java.walkTopDown().filter { it.isFile && it.extension == "kt" }) {
            for ((line, lit) in kotlinLiterals(f.readText())) {
                if (f.name == "ClipboardClearing.kt" && lit == CLIPBOARD_OWN_LABEL) continue
                // The one notice that says the app used to be called «Seguridad» (owner's decision, 31-10-2026).
                if (f.name == "ToolTexts.kt" && lit == APP_RENAMED_TEXT) continue
                if (oldName in withoutAddresses(lit)) bad += "${f.name}:$line «$lit»"
            }
        }
        assertTrue("Texts that still name the app «Seguridad»:\n" + bad.joinToString("\n"), bad.isEmpty())
    }

    @Test
    fun onlyTheRenameNoticeNamesTheOldName() {
        // The named exception above covers exactly this text, which must say both names.
        assertTrue(oldName in APP_RENAMED_TEXT && "Clavero" in APP_RENAMED_TEXT)
        val uses = kotlinLiterals(read("app/src/main/java/app/seguridad/tools/ToolTexts.kt")).count { (_, lit) -> oldName in lit }
        assertEquals("only APP_RENAMED_TEXT in ToolTexts.kt", 1, uses)
    }

    @Test
    fun theIdentityTheFormatsAndTheClipboardLabelDoNotChange() {
        val gradle = read("app/build.gradle.kts")
        assertTrue("applicationId = \"io.github.egt17baa.seguridad\"" in gradle)
        assertTrue("namespace = \"app.seguridad\"" in gradle)
        assertTrue("android:authorities=\"\${applicationId}.compartir\"" in read("app/src/main/AndroidManifest.xml"))
        val biometric = read("app/src/main/java/app/seguridad/android/BiometricVault.kt")
        assertTrue("private const val ALIAS = \"seguridad_vault_biometric\"" in biometric)
        assertTrue("private const val PREFS = \"vault_biometric\"" in biometric)

        // The clipboard clearing recognises the app's own copies by this label, also those made by 1.2.x.
        assertEquals("Seguridad", CLIPBOARD_OWN_LABEL)

        assertEquals("seguridad-vault", VaultFile.FORMAT)
        assertEquals("criptopanel-vault", VaultFile.LEGACY_FORMAT)
        assertEquals("seguridad-backup", BACKUP_MARKER)
        assertEquals("seguridad-auto-", AUTO_BACKUP_PREFIX)
        assertEquals("SG1:", Dialect.SEGURIDAD.textPrefix)
        assertEquals(".sgenc", Dialect.SEGURIDAD.fileExtension)
        assertEquals("seguridad", Dialect.SEGURIDAD.labelPrefix)
        assertEquals("CP1:", Dialect.CRIPTOPANEL.textPrefix)
        assertEquals(".cpenc", Dialect.CRIPTOPANEL.fileExtension)
        assertEquals(".sgenc", ENCRYPTED_EXTENSION)
        assertEquals(".sgsig", SIGNATURE_EXTENSION)
        assertEquals("SGK1:", CARD_TEXT_PREFIX)
        assertEquals("SGM1:", MESSAGE_PREFIX)
        assertEquals("SGS1:", SIGNATURE_PREFIX)
    }

    @Test
    fun thePolicyTheListingAndTheReadmeUseTheNewName() {
        val policy = read("docs/privacidad.html")
        assertTrue("<title>Política de privacidad de Clavero</title>" in policy)
        assertTrue("<h1>Política de privacidad de Clavero</h1>" in policy)
        assertTrue("antes «Seguridad»" in policy)
        // «Seguridad» as the heading of the section about security is the common noun.
        val inPolicy = oldNamesIn(policy, allowed = listOf("<h2>Seguridad</h2>"))
        assertTrue("privacidad.html:\n" + inPolicy.joinToString("\n"), inPolicy.isEmpty())

        val listing = read("docs/ficha-google-play.md")
        val forPlay = listing.substringAfter("## Nombre de la app")
        val name = forPlay.lines().drop(1).first { it.isNotBlank() }
        assertTrue("$name: ${name.length}", name.startsWith("Clavero") && name.length <= 30)
        val inListing = oldNamesIn(forPlay)
        assertTrue("ficha-google-play.md:\n" + inListing.joinToString("\n"), inListing.isEmpty())
        assertTrue("antes «Seguridad»" in forPlay)

        val readme = read("README.md")
        assertEquals("# Clavero", readme.lines().first())
        assertTrue("antes «Seguridad»" in readme)
    }

    @Test
    fun theReleaseApkIsNamedAfterClaveroAndTheWorkflowKeepsItsFile() {
        // The workflow's run number is the versionCode: its file name never changes (docs/PUBLICAR.md, 3.4).
        val workflow = read(".github/workflows/build-apk.yml")
        assertTrue("Clavero-\${{ github.ref_name }}.apk" in workflow)
        assertTrue("Clavero-\${{ github.ref_name }}.apk.sha256" in workflow)
        assertFalse(workflow, oldName in workflow)
    }
}
