package app.seguridad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Audit C-1: the project's rule «ningún nombre de modelo» in what is published (the repository's documents, the
 * sources, the build and the workflows), checked by a test instead of a `grep` by hand. The names are read at run time
 * from the row `modelo` of the table of `.claude/skills/advisor/SKILL.md` (the only place allowed to hold them, as
 * configuration), so this file never writes them. Whole words, in any case. Not looked at: `app/src/main/assets/` (word
 * lists), `.claude/`, `.git/` and every `build/`. One exception: the line of `branch-rereview-compilacion.md` that gives
 * the search expression itself, recognised by its file and its content.
 */
class PublishedFilesNameNoModelTest {
    private val repo: File = generateSequence(File(".").absoluteFile) { it.parentFile }
        .first { File(it, "app/src/main/java").isDirectory && File(it, "docs").isDirectory }

    /** The names of the row `modelo` of the advisor skill's table: each one between backticks, but `modelo` itself. */
    private fun modelNames(): Set<String> {
        val skill = File(repo, ".claude/skills/advisor/SKILL.md")
        assertTrue("Falta ${skill.path}: de su tabla salen los nombres que este test busca", skill.isFile)
        val row = skill.readLines().firstOrNull { it.trimStart().startsWith("| `modelo` |") }
            ?: error("${skill.path} no tiene la fila `modelo` en su tabla")
        val names = Regex("`([^`]+)`").findAll(row).map { it.groupValues[1].lowercase() }.filter { it != "modelo" }.toSet()
        assertTrue("la fila `modelo` no da ningún nombre", names.isNotEmpty())
        return names
    }

    /** The published paths under [root]: the files and folders of the rule, without the ones left out. */
    private fun published(root: File): Sequence<File> {
        val top = listOf("README.md", "NOTICE", "SECURITY.md", "CLAUDE.md", "docs", ".github", "scripts", ".maestro", "app/src", "jvm-check/src", "gradle")
            .map { File(root, it) } +
            (root.listFiles { f -> f.isFile && f.name.endsWith(".gradle.kts") }?.toList().orEmpty()) +
            listOf("app", "jvm-check").flatMap { d -> File(root, d).listFiles { f -> f.isFile && f.name.endsWith(".gradle.kts") }?.toList().orEmpty() }
        val assets = File(root, "app/src/main/assets").absoluteFile
        return top.asSequence().filter { it.exists() }.flatMap { start ->
            start.walkTopDown()
                .onEnter { d -> d.name != "build" && d.name != ".git" && d.name != ".claude" && d.absoluteFile != assets }
                .filter { it.isFile }
        }
    }

    private fun isException(root: File, file: File, line: String): Boolean =
        file.relativeTo(root).invariantSeparatorsPath == "docs/superpowers/handoff/sdd-2.0/branch-rereview-compilacion.md" &&
            "git grep -nIiE" in line && "grep -iE" in line

    /** Each `path:line` under [root] that names one of [names] as a whole word, in any case. */
    private fun offenders(root: File, names: Set<String>): List<String> {
        val word = Regex("""(?i)(?<![\p{L}\p{N}_])(${names.joinToString("|") { Regex.escape(it) }})(?![\p{L}\p{N}_])""")
        val found = mutableListOf<String>()
        for (f in published(root)) {
            val bytes = f.readBytes()
            if (bytes.take(8_000).any { it == 0.toByte() }) continue // A binary file (an image, a copy of a test).
            bytes.decodeToString().lines().forEachIndexed { i, line ->
                if (word.containsMatchIn(line) && !isException(root, f, line)) found += "${f.relativeTo(root).invariantSeparatorsPath}:${i + 1}"
            }
        }
        return found
    }

    @Test fun noPublishedFileNamesAModel() {
        assertEquals(emptyList<String>(), offenders(repo, modelNames()))
    }

    /** The scan itself: a tree with a name in a document, in a source and in a build file, and one only in `build/`. */
    @Test fun theScanFindsANameInATestTree() {
        val names = modelNames()
        val name = names.first().replaceFirstChar { it.uppercase() }
        val root = kotlin.io.path.createTempDirectory("c1-tree").toFile()
        try {
            File(root, "app/src/main/java").mkdirs()
            File(root, "docs/superpowers/handoff/sdd-2.0").mkdirs()
            File(root, "docs/a.md").writeText("sin nombres\nlo revisó $name, el director\n")
            File(root, "app/src/main/java/A.kt").writeText("// $name\n")
            File(root, "app/build/x.txt").apply { parentFile.mkdirs() }.writeText(name)
            File(root, "app/src/main/assets/w.txt").apply { parentFile.mkdirs() }.writeText(name)
            File(root, "build.gradle.kts").writeText("// ${name.lowercase()}x no es la palabra\n")
            File(root, "docs/superpowers/handoff/sdd-2.0/branch-rereview-compilacion.md")
                .writeText("`git grep -nIiE \"\" -- docs | grep -iE \"\\b($name)\\b\"`\n")
            assertEquals(listOf("app/src/main/java/A.kt:1", "docs/a.md:2"), offenders(root, names).sorted())
        } finally {
            root.deleteRecursively()
        }
    }
}
