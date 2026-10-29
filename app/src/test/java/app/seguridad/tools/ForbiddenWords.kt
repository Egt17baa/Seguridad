package app.seguridad.tools

/** The owner's forbidden words for user-visible text (CLAUDE.md, context.md), shared by ToolTextsTest and SourceTextsTest. */
object ForbiddenWords {
    private val banned = listOf("anónim", "imposible", "100 %", "100%", "de verdad", "validez legal", "sospechos")
    private val limpio = Regex("(?<![\\p{L}])limpi[oa]s?(?![\\p{L}])")
    private val seguro = Regex("(?<![\\p{L}])segur[oa]s?(?![\\p{L}])")

    /**
     * What is wrong with [text]; empty if nothing. «Limpiar» (the verb of the tool's name) and «Seguridad» are fine:
     * only «limpio/a/os/as» and «seguro/a/os/as» as whole words. «no garantiza que sea seguro» is allowed.
     */
    fun problems(text: String): List<String> {
        val t = text.lowercase()
        val out = mutableListOf<String>()
        for (b in banned) if (b in t) out += "«$b»"
        if (limpio.containsMatchIn(t)) out += "«limpio/a»"
        if (seguro.containsMatchIn(t.replace("no garantiza que sea seguro", ""))) out += "«seguro/a»"
        Regex("garantiza").findAll(t).forEach { m ->
            if (!t.substring(0, m.range.first).endsWith("no ")) out += "«garantiza» sin «no»"
        }
        return out
    }
}
