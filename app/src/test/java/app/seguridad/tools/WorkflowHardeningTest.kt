package app.seguridad.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Audit C-7: what protects the signing key in `.github/workflows/build-apk.yml`, fixed by a test (with C-3 open, the
 * review of a PR is not the only guard). Read as text, as PlatformSourcesTest reads the sources: every action pinned to a
 * commit, no `pull_request_target`, read-only by default and only `release-apk` writing, the signing secrets only in
 * trusted runs, and the upload certificate checked against the one of docs/PUBLICAR.md. Each check is a function of the
 * YAML, so [eachCheckFailsOnAnAlteredCopy] shows it red on a copy with its protection taken out.
 */
class WorkflowHardeningTest {
    private val repo: File = generateSequence(File(".").absoluteFile) { it.parentFile }
        .first { File(it, ".github/workflows/build-apk.yml").isFile }
    private val yaml = File(repo, ".github/workflows/build-apk.yml").readText()
    private val publicar = File(repo, "docs/PUBLICAR.md").readText()

    private val trust = "((startsWith(github.ref, 'refs/tags/v') && github.event_name == 'push') || " +
        "(github.ref == 'refs/heads/main' && github.event_name == 'workflow_dispatch'))"

    /** The lines that are not comments, with their job (null outside `jobs:`) and without a trailing ` # comment`. */
    private fun lines(y: String): List<Pair<String?, String>> {
        var inJobs = false
        var job: String? = null
        return y.lines().mapNotNull { raw ->
            if (raw.trimStart().startsWith("#") || raw.isBlank()) return@mapNotNull null
            val line = raw.replace(Regex("""\s+#\s.*$"""), "")
            if (!line.startsWith(" ")) {
                inJobs = line.trimEnd() == "jobs:"
                job = null
            } else if (inJobs) {
                Regex("""^  ([A-Za-z0-9_-]+):\s*$""").find(line)?.let { job = it.groupValues[1] }
            }
            job to line
        }
    }

    private fun pinning(y: String): List<String> {
        val uses = lines(y).mapNotNull { (_, l) -> Regex("""^\s*(?:-\s+)?uses:\s*(\S+)""").find(l)?.groupValues?.get(1) }
        if (uses.isEmpty()) return listOf("no uses:")
        return uses.filterNot { Regex("""^[A-Za-z0-9_.-]+/[A-Za-z0-9_./-]+@[0-9a-f]{40}$""").matches(it) }
    }

    private fun readOnly(y: String): List<String> {
        val problems = mutableListOf<String>()
        val code = lines(y)
        if (code.any { (_, l) -> "pull_request_target" in l }) problems += "pull_request_target"
        if (code.any { (_, l) -> "write-all" in l }) problems += "write-all"
        val all = y.lines()
        val top = all.indexOf("permissions:")
        if (top < 0) problems += "no top permissions" else {
            val block = all.drop(top + 1).takeWhile { it.startsWith(" ") || it.isBlank() }.filter { it.isNotBlank() && !it.trimStart().startsWith("#") }
            if (block.map { it.trim() } != listOf("contents: read")) problems += "top permissions: $block"
        }
        val writers = code.filter { (_, l) -> Regex(""":\s*write\b""").containsMatchIn(l) }.map { it.first }
        if (writers != listOf("release-apk")) problems += "writers: $writers"
        return problems
    }

    private fun secrets(y: String): List<String> {
        val problems = mutableListOf<String>()
        val code = lines(y)
        val firma = code.filter { (_, l) -> l.trim() == "environment: firma" }.mapNotNull { it.first }.toSet()
        val signing = code.filter { (_, l) -> "secrets.SEGURIDAD_" in l }
        val inBuild = signing.filter { it.first == "build" }
        if (inBuild.size != 4) problems += "build has ${inBuild.size} signing secrets"
        for ((_, l) in inBuild) if ("$trust && secrets.SEGURIDAD_" !in l || !l.trimEnd().endsWith("|| '' }}")) problems += "untrusted: ${l.trim()}"
        for ((job, l) in signing - inBuild.toSet()) if (job !in firma) problems += "$job without environment firma: ${l.trim()}"
        if ("play-bundle" !in firma || signing.count { it.first == "play-bundle" } != 4) problems += "play-bundle"
        // Recheck C-7.m1: the jobs with the signing secrets run only in trusted runs (with C-3 open, that `if:` is the
        // guard of play-bundle's four secrets), and play-bundle only after build-firma.
        for (job in listOf("build-firma", "play-bundle")) {
            val ifs = code.filter { (j, l) -> j == job && l.startsWith("    if:") }.map { it.second.trimEnd() }
            if (ifs != listOf("    if: ${trust.removeSurrounding("(", ")")}")) problems += "$job if: $ifs"
        }
        val playNeeds = code.filter { (j, l) -> j == "play-bundle" && l.startsWith("    needs:") }.map { it.second.trim() }
        if (playNeeds.size != 1 || !Regex("""\bbuild-firma\b""").containsMatchIn(playNeeds.single())) problems += "play-bundle needs: $playNeeds"
        if (code.any { (_, l) -> Regex("""secrets\[|toJSON\(secrets""").containsMatchIn(l) }) problems += "all secrets"
        val all = y.lines()
        all.withIndex().filter { (_, l) -> Regex("""uses:\s*actions/checkout@""").containsMatchIn(l) && !l.trimStart().startsWith("#") }.forEach { (i, l) ->
            if (all.drop(i + 1).take(3).none { it.trim() == "persist-credentials: false" }) problems += "checkout without persist-credentials: false (${i + 1}: ${l.trim()})"
        }
        return problems
    }

    private fun certificate(y: String, publicar: String): List<String> {
        val problems = mutableListOf<String>()
        val published = Regex("""certificate SHA-256 digest: ([0-9a-f]{64})""").find(publicar)?.groupValues?.get(1)
        val pinned = Regex("""^\s*UPLOAD_CERT_SHA256:\s*([0-9A-Fa-f]{64})\s*$""", RegexOption.MULTILINE).find(y)?.groupValues?.get(1)
        if (published == null || pinned == null || !published.equals(pinned, ignoreCase = true)) problems += "UPLOAD_CERT_SHA256 $pinned, PUBLICAR $published"
        val code = lines(y)
        val build = code.count { (job, l) -> job == "build" && "[ \"\$GITHUB_JOB\" != \"build\" ] && [ \"\$FP\" != \"\$UPLOAD_CERT_SHA256\" ]" in l }
        val play = code.count { (job, l) -> job == "play-bundle" && "if [ \"\$FP\" != \"\$UPLOAD_CERT_SHA256\" ]; then" in l }
        if (build != 1 || play != 1) problems += "comparisons: build $build, play-bundle $play"
        if (code.count { (_, l) -> "*build-steps" in l } != 1) problems += "build-firma does not reuse the steps of build"
        return problems
    }

    @Test fun everyActionIsPinnedToACommit() = assertEquals(emptyList<String>(), pinning(yaml))

    @Test fun noPullRequestTargetAndReadOnlyByDefault() = assertEquals(emptyList<String>(), readOnly(yaml))

    @Test fun signingSecretsOnlyInTrustedRuns() = assertEquals(emptyList<String>(), secrets(yaml))

    @Test fun theUploadCertificateIsChecked() = assertEquals(emptyList<String>(), certificate(yaml, publicar))

    /** The red of each case: a copy of the YAML with one protection taken out. */
    @Test fun eachCheckFailsOnAnAlteredCopy() {
        fun altered(from: String, to: String): String {
            assertTrue(from, from in yaml)
            return yaml.replaceFirst(from, to).also { assertTrue("the alteration changes the YAML", it != yaml) }
        }
        assertTrue(pinning(altered("actions/checkout@fbc6f3992d24b796d5a048ff273f7fcc4a7b6c09", "actions/checkout@v5")).isNotEmpty())
        assertTrue(readOnly(altered("  pull_request:\n", "  pull_request_target:\n")).isNotEmpty())
        assertTrue(readOnly(altered("permissions:\n  contents: read\n", "permissions: write-all\n")).isNotEmpty())
        assertTrue(readOnly(altered("permissions:\n  contents: read\n", "permissions:\n  contents: write\n")).isNotEmpty())
        val firstSecret = Regex("""KEYSTORE_BASE64: \$\{\{ .*\}\}""").find(yaml)!!.value
        assertTrue(secrets(altered(firstSecret, "KEYSTORE_BASE64: \${{ secrets.SEGURIDAD_KEYSTORE_BASE64 }}")).isNotEmpty())
        val playHead = yaml.substring(yaml.indexOf("  play-bundle:\n"))
        assertTrue(secrets(altered(playHead, playHead.replaceFirst("    environment: firma\n", ""))).isNotEmpty())
        assertTrue(secrets(altered("persist-credentials: false", "persist-credentials: true")).isNotEmpty())
        val trustedIf = "    if: ${trust.removeSurrounding("(", ")")}\n"
        assertTrue(secrets(altered(playHead, playHead.replaceFirst(trustedIf, "    if: always()\n"))).isNotEmpty())
        val firmaHead = yaml.substring(yaml.indexOf("  build-firma:\n"))
        assertTrue(secrets(altered(firmaHead, firmaHead.replaceFirst(trustedIf, ""))).isNotEmpty())
        assertTrue(secrets(altered(playHead, playHead.replaceFirst("    needs: [build-firma, jvm-check]\n", "    needs: [jvm-check]\n"))).isNotEmpty())
        assertTrue(certificate(altered("746398EA", "746398EB"), publicar).isNotEmpty())
        assertTrue(certificate(altered("if [ \"\$FP\" != \"\$UPLOAD_CERT_SHA256\" ]; then", "if false; then"), publicar).isNotEmpty())
    }
}
