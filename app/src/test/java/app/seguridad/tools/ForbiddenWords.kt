package app.seguridad.tools

/** The owner's forbidden words for user-visible text (CLAUDE.md, context.md), shared by ToolTextsTest and SourceTextsTest. */
object ForbiddenWords {
    private val banned = listOf("anónim", "imposible", "100 %", "100%", "de verdad", "validez legal", "sospechos")
    private val limpio = Regex("(?<!\\p{L})limpi[oa]s?(?!\\p{L})")
    private val seguro = Regex("(?<!\\p{L})segur[oa]s?(?!\\p{L})")

    /** The denial that may name «seguro»: «no garantiza que sea seguro», with up to 3 words between «que» and «sea». */
    private val deniedSafe = Regex("(?<!\\p{L})no garantiza que(?: [\\p{L}\\p{N}-]+){0,3} sea segur[oa]s?(?!\\p{L})")

    /** What may stand right before a «garantiza»: a whole «no», optionally with «se» or «lo». */
    private val denial = Regex("(?<!\\p{L})no (?:se |lo )?$")

    /** Characters that do not show and so cannot split a word: soft hyphen, zero-width space, joiners, word joiner, BOM. */
    private val invisible = Regex("[\\u00AD\\u200B\\u200C\\u200D\\u2060\\uFEFF]")

    /** Any run of spacing: line breaks, tabs, no-break and narrow spaces (\p{Zs}). */
    private val spacing = Regex("[\\s\\p{Zs}]+")

    /** [text] as it is checked: lower case, invisible characters dropped, every run of spacing one space. */
    fun normalise(text: String): String = text.lowercase().replace(invisible, "").replace(spacing, " ")

    /**
     * What is wrong with [text]; empty if nothing. «Limpiar» (the verb of the tool's name) and «Seguridad» are fine:
     * only «limpio/a/os/as» and «seguro/a/os/as» as whole words. Allowed: «no garantiza que [hasta 3 palabras] sea
     * seguro», and «garantiza» only right after a whole «no», «no se» or «no lo».
     */
    fun problems(text: String): List<String> {
        val t = normalise(text)
        val out = mutableListOf<String>()
        for (b in banned) if (b in t) out += "«$b»"
        if (limpio.containsMatchIn(t)) out += "«limpio/a»"
        if (seguro.containsMatchIn(t.replace(deniedSafe, " "))) out += "«seguro/a»"
        Regex("garantiza").findAll(t).forEach { m ->
            if (!denial.containsMatchIn(t.substring(0, m.range.first))) out += "«garantiza» sin «no»"
        }
        return out
    }
}
