package app.seguridad.vault

import app.seguridad.crypto.PassphraseOptions
import app.seguridad.crypto.PasswordOptions
import app.seguridad.crypto.generatePassphrase
import app.seguridad.crypto.generatePassword
import app.seguridad.health.WEAK_BITS
import app.seguridad.health.estimateBits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.SecureRandom

/**
 * CRY-A01 (review 1.3.1): a NEW master password has to be long enough and pass the strength estimate of «Salud». The
 * rule is only for creating the vault and for changing the master; opening and importing never go through it.
 */
class MasterPolicyTest {
    // ---- The rule ----

    @Test fun `the texts are the ones the Abogado Digital reviews`() {
        assertEquals(10, MIN_MASTER)
        assertEquals("Mínimo 10 caracteres", MASTER_TOO_SHORT)
        assertEquals(
            "Esta contraseña es fácil de adivinar. Usa una frase de 5 o 6 palabras que solo tú conozcas (el Generador te puede dar una).",
            MASTER_TOO_WEAK,
        )
    }

    @Test fun `a short password says it is too short, before saying it is weak`() {
        assertEquals(MASTER_TOO_SHORT, newMasterProblem("corta"))
        assertEquals(MASTER_TOO_SHORT, newMasterProblem(""))
        assertEquals(MASTER_TOO_SHORT, newMasterProblem("a".repeat(MIN_MASTER - 1)))
        assertEquals(MASTER_TOO_SHORT, newMasterProblem("Xq7#vB2!m")) // 9 characters, strong by the estimate: length comes first.
    }

    @Test fun `ten characters are enough for the length, the strength decides next`() {
        assertEquals(MASTER_TOO_WEAK, newMasterProblem("a".repeat(MIN_MASTER)))
        assertNull(newMasterProblem("Xq7#vB2!mK"))
    }

    @Test fun `the obvious passwords of ten characters or more are too weak`() {
        for (weak in listOf("contraseña", "Contraseña", "CONTRASEÑA", "1234567890", "aaaaaaaaaa", "qwertyuiop", "          ", "abcdefghij", "0000000000")) {
            assertEquals("«$weak» must be refused", MASTER_TOO_WEAK, newMasterProblem(weak))
        }
    }

    @Test fun `a phrase of words is accepted`() {
        assertNull(newMasterProblem("casa perro luz mar sol"))
        assertNull(newMasterProblem("casa-perro-luz-mar-sol"))
    }

    /** The limit of the estimate, on purpose: it has no dictionary, so a word with a capital and a digit passes. */
    @Test fun `Barcelona1 is the known limit of the estimate and passes`() {
        val bits = estimateBits("Barcelona1")
        assertTrue("$bits", bits >= WEAK_BITS && bits < 65.0)
        assertNull(newMasterProblem("Barcelona1"))
    }

    @Test fun `the threshold is the one of Salud`() {
        // Same function and same constant as the «Débil» mark of Salud: a password refused here is flagged weak there.
        assertEquals(50.0, WEAK_BITS, 0.0)
        for (c in listOf("contraseña", "1234567890", "aaaaaaaaaa", "qwertyuiop", "Barcelona1", "casa perro luz mar sol", "Xq7#vB2!mK")) {
            assertEquals(c, estimateBits(c) < WEAK_BITS, newMasterProblem(c) == MASTER_TOO_WEAK)
        }
    }

    // ---- What the Generator proposes always passes ----

    private fun asset(name: String): List<String> {
        // Run from app/ (Gradle Android tests) or from jvm-check/.
        val file = listOf("src/main/assets", "../app/src/main/assets").map { File(it, name) }.first { it.exists() }
        return file.readLines().filter { it.isNotBlank() }
    }

    /** The separators the Generator screen offers (GeneratorScreen): guion, espacio, punto, barra baja. */
    private val separators = listOf("-", " ", ".", "_")

    @Test fun `a phrase of 5 words of the Generator passes, in both lists and with every separator`() {
        val random = SecureRandom()
        for (listName in listOf("eff_large_wordlist.txt", "bip39_spanish.txt", "bip39_english.txt")) {
            val words = asset(listName)
            for (sep in separators) {
                repeat(200) {
                    val phrase = generatePassphrase(words, PassphraseOptions(words = 5, separator = sep), random)
                    assertNull("$listName «$sep»: «$phrase» (${estimateBits(phrase)} bits)", newMasterProblem(phrase))
                }
            }
        }
    }

    @Test fun `a phrase of the Generator passes also with capitals and a number`() {
        val random = SecureRandom()
        for (listName in listOf("eff_large_wordlist.txt", "bip39_spanish.txt")) {
            val words = asset(listName)
            for (sep in separators) for (capitalize in listOf(false, true)) for (addNumber in listOf(false, true)) {
                repeat(50) {
                    val phrase = generatePassphrase(words, PassphraseOptions(5, sep, capitalize, addNumber), random)
                    assertNull("$listName «$sep»: «$phrase»", newMasterProblem(phrase))
                }
            }
        }
    }

    @Test fun `a password of 16 characters of the Generator passes`() {
        val random = SecureRandom()
        repeat(200) {
            val password = generatePassword(PasswordOptions(length = 16), random)!!
            assertNull("«$password» (${estimateBits(password)} bits)", newMasterProblem(password))
        }
        // Without symbols, and only letters: still above the threshold at 16 characters.
        repeat(200) {
            val password = generatePassword(PasswordOptions(length = 16, symbols = false), random)!!
            assertNull("«$password» (${estimateBits(password)} bits)", newMasterProblem(password))
        }
    }

    // ---- Where the rule is, and where it is not (source text: ui/ is not compiled on the JVM) ----

    private fun screens(): List<File> {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val f = File(dir, "app/src/main/java/app/seguridad/ui/screens")
            if (f.isDirectory) return f.listFiles { x -> x.isFile && x.extension == "kt" }!!.sortedBy { it.name }
            dir = dir.parentFile
        }
        error("app/src/main/java/app/seguridad/ui/screens not found")
    }

    /** Code only: a comment that names the call must not make the check pass. */
    private fun code(f: File) = f.readLines().filterNot { it.trimStart().let { l -> l.startsWith("//") || l.startsWith("*") || l.startsWith("/*") } }.joinToString("\n")

    private fun screen(name: String) = code(screens().first { it.name == name })

    /** The arguments of `"<route>" -> PasswordDialog(...)`, up to the trailing lambda `) { pw ->`. */
    private fun dialogArguments(route: String): String {
        val m = Regex("""\"$route\" -> PasswordDialog\((.*?)\) \{ pw ->""", RegexOption.DOT_MATCHES_ALL).find(screen("VaultBackup.kt"))
        assertNotNull("PasswordDialog of «$route» not found", m)
        return m!!.groupValues[1]
    }

    @Test fun `creating the vault and changing the master call newMasterProblem`() {
        assertTrue(Regex("""newMasterProblem\(""").containsMatchIn(screen("VaultScreen.kt")))
        assertTrue(Regex("""newMasterProblem\(""").containsMatchIn(screen("VaultBackup.kt")))
        // The create button waits for the rule: no problem, not only «long enough».
        assertTrue("problem == null" in screen("VaultScreen.kt"))
    }

    @Test fun `the master dialog asks for a new password and the import dialog does not`() {
        assertTrue("newPassword = true" in dialogArguments("master"))
        assertTrue("newPassword" !in dialogArguments("import"))
    }

    @Test fun `no screen but the dialog itself checks only the length of a master`() {
        val lengthOnly = Regex("""\blength\s*>=\s*MIN_MASTER""")
        val hits = screens().flatMap { f -> lengthOnly.findAll(code(f)).map { f.name }.toList() }
        assertEquals("only PasswordDialog (the import) keeps the length check: $hits", listOf("VaultBackup.kt"), hits)
        val backup = screen("VaultBackup.kt")
        val dialog = backup.substring(backup.indexOf("fun PasswordDialog("))
        assertEquals(1, lengthOnly.findAll(dialog).count())
        assertEquals("the length check is gone from the rest of the file", 0, lengthOnly.findAll(backup.substring(0, backup.indexOf("fun PasswordDialog("))).count())
    }

    @Test fun `MIN_MASTER has one definition, in vault`() {
        assertTrue(screens().none { Regex("""const val MIN_MASTER\b""").containsMatchIn(code(it)) })
        for (name in listOf("VaultScreen.kt", "VaultBackup.kt")) assertTrue("$name imports MIN_MASTER", "import app.seguridad.vault.MIN_MASTER" in screen(name))
    }
}
