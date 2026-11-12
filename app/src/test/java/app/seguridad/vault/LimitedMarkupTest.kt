package app.seguridad.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2.0, task 5 (design «Notas con formato limitado»): the app's own reader of the notes' format. Only `#`, `##`, `###`,
 * `- `, `1. `, `**…**` and `*…*`; everything else is literal text (links, HTML). One pass per line, no recursion, at most
 * [LimitedMarkup.MAX_LINES] lines and [LimitedMarkup.MAX_CHARS] characters read as format (the rest as plain text), so a
 * hostile note neither freezes nor crashes the card. Written from scratch (Abogado Digital, R-5).
 */
class LimitedMarkupTest {
    private fun plain(text: String) = Span(text, bold = false, italic = false)

    /** All the text the blocks hold, one block per line, as the card shows it (without the bullets or numbers). */
    private fun List<MarkupBlock>.text() = joinToString("\n") { b -> b.spans.joinToString("") { it.text } }

    @Test fun boldItalicHeadingsLists() {
        val blocks = LimitedMarkup.parse(
            "# Uno\n## Dos\n### Tres\n#### Cuatro\nUn **fuerte** y un *suave* y ***los dos***\n- compra\n12. pan\n-no\n1.no\n#no",
        )
        assertEquals(MarkupBlock.Heading(1, listOf(plain("Uno"))), blocks[0])
        assertEquals(MarkupBlock.Heading(2, listOf(plain("Dos"))), blocks[1])
        assertEquals(MarkupBlock.Heading(3, listOf(plain("Tres"))), blocks[2])
        // Only three levels: a fourth is text as it is.
        assertEquals(MarkupBlock.Paragraph(listOf(plain("#### Cuatro"))), blocks[3])
        assertEquals(
            MarkupBlock.Paragraph(
                listOf(
                    plain("Un "), Span("fuerte", bold = true, italic = false), plain(" y un "), Span("suave", bold = false, italic = true),
                    plain(" y "), Span("los dos", bold = true, italic = true),
                ),
            ),
            blocks[4],
        )
        assertEquals(MarkupBlock.Bullet(listOf(plain("compra"))), blocks[5])
        assertEquals(MarkupBlock.Numbered(12, listOf(plain("pan"))), blocks[6])
        // Without the space after the mark, it is no mark.
        assertEquals(listOf("-no", "1.no", "#no"), blocks.drop(7).map { (it as MarkupBlock.Paragraph).spans.single().text })
        assertEquals(10, blocks.size)
        // Format inside a heading and a list item too.
        assertEquals(MarkupBlock.Bullet(listOf(plain("leche "), Span("entera", bold = true, italic = false))), LimitedMarkup.parse("- leche **entera**").single())
    }

    @Test fun linksStayLiteral() {
        for (text in listOf("[a](https://x)", "https://x", "Mira [esto](javascript:alert(1)) o www.ejemplo.es", "<https://x>", "![i](https://x/i.png)")) {
            val blocks = LimitedMarkup.parse(text)
            assertEquals(text, listOf(MarkupBlock.Paragraph(listOf(plain(text)))), blocks)
        }
        // Inside bold, a link is bold text and nothing more.
        assertEquals(listOf(Span("https://x", bold = true, italic = false)), LimitedMarkup.parse("**https://x**").single().spans)
    }

    @Test fun htmlStaysLiteral() {
        for (text in listOf("<b>no</b>", "<script>alert(1)</script>", "&lt;b&gt;", "<img src=x onerror=y>", "<!-- c -->")) {
            assertEquals(text, listOf(MarkupBlock.Paragraph(listOf(plain(text)))), LimitedMarkup.parse(text))
        }
    }

    @Test fun unclosedBoldIsLiteral() {
        assertEquals(listOf(plain("**abierta")), LimitedMarkup.parse("**abierta").single().spans)
        assertEquals(listOf(plain("*abierta")), LimitedMarkup.parse("*abierta").single().spans)
        // A mark followed by a space does not open, and one after a space does not close.
        assertEquals(listOf(plain("2 * 3 * 4")), LimitedMarkup.parse("2 * 3 * 4").single().spans)
        assertEquals(listOf(plain("** no **")), LimitedMarkup.parse("** no **").single().spans)
        // Nothing between the marks, or four or more in a row: text.
        assertEquals(listOf(plain("****")), LimitedMarkup.parse("****").single().spans)
        assertEquals(listOf(plain("*****a*****")), LimitedMarkup.parse("*****a*****").single().spans)
        // A mark never runs over to the next line.
        assertEquals(listOf(listOf(plain("**uno")), listOf(plain("dos**"))), LimitedMarkup.parse("**uno\ndos**").map { it.spans })
        // An opened bold that never closes leaves its mark, and what follows still reads its own pairs.
        assertEquals(listOf(plain("**a "), Span("b", bold = false, italic = true)), LimitedMarkup.parse("**a *b*").single().spans)
    }

    @Test fun hugeInputIsBoundedAndFast() {
        val mib = 1024 * 1024
        LimitedMarkup.parse("*".repeat(1000)) // Warm up.
        for (hostile in listOf("*".repeat(mib), "**a** ".repeat(mib / 6), "*a ".repeat(mib / 3), "# x\n".repeat(mib / 4), "\n".repeat(mib))) {
            val start = System.nanoTime()
            val blocks = LimitedMarkup.parse(hostile)
            val ms = (System.nanoTime() - start) / 1_000_000
            assertTrue("$ms ms", ms < 200)
            // Nothing is lost: what is not read as format comes, as it is, as plain text in one last paragraph.
            val rest = blocks.last()
            assertTrue(rest is MarkupBlock.Paragraph && rest.spans.size == 1 && rest.spans.all { !it.bold && !it.italic })
            val restText = rest.spans.single().text
            assertTrue(hostile.endsWith(restText))
            // At most 64 KiB and 10 000 lines are read as format.
            assertTrue(hostile.length - restText.length <= LimitedMarkup.MAX_CHARS)
            assertTrue(blocks.size - 1 <= LimitedMarkup.MAX_LINES)
        }
        // Review M-10: what the reader does read, at its worst: one line just under the limit full of marks, and 10 000
        // short lines with format; all of it read as format, in the same time.
        val max = LimitedMarkup.MAX_CHARS
        for (line in listOf("*a".repeat(max / 2).take(max - 1), "**a*".repeat(max / 4).take(max - 1), "***".repeat(max / 3).take(max - 1), "* ".repeat(max / 2).take(max - 1))) {
            val start = System.nanoTime()
            val blocks = LimitedMarkup.parse(line)
            val ms = (System.nanoTime() - start) / 1_000_000
            assertTrue("$ms ms", ms < 200)
            assertEquals(1, blocks.size) // Read whole, as format: nothing left over.
        }
        val lines = List(LimitedMarkup.MAX_LINES) { if (it % 2 == 0) "**a**" else "*b*" }.joinToString("\n")
        val start = System.nanoTime()
        val many = LimitedMarkup.parse(lines)
        assertTrue(System.nanoTime() - start < 200_000_000)
        assertEquals(LimitedMarkup.MAX_LINES, many.size)
        assertTrue(lines.length <= LimitedMarkup.MAX_CHARS)
        assertTrue(many.withIndex().all { (i, b) -> b.spans.single().let { s -> if (i % 2 == 0) s.bold && !s.italic else s.italic && !s.bold } })
        // Exactly the limit: all of it; one line break more leaves an empty last paragraph, nothing lost.
        assertEquals(1, LimitedMarkup.parse("x".repeat(max)).size)
        val over = LimitedMarkup.parse("x".repeat(max) + "\n")
        assertEquals(2, over.size)
        assertTrue(over.last().spans.isEmpty())
        assertEquals(64 * 1024, LimitedMarkup.MAX_CHARS)
        assertEquals(10_000, LimitedMarkup.MAX_LINES)
    }

    @Test fun plainNotesUnchanged() {
        val note = "Código de la puerta: 4512\n\nLlamar antes. 3 + 4 = 7; a_b_c; 50% (aprox.)\n  sangría\n"
        val blocks = LimitedMarkup.parse(note)
        assertTrue(blocks.all { it is MarkupBlock.Paragraph && it.spans.size <= 1 && it.spans.all { s -> !s.bold && !s.italic } })
        assertEquals(note, blocks.text())
        assertEquals(note.split('\n').size, blocks.size)
        assertEquals(emptyList<MarkupBlock>(), LimitedMarkup.parse("").filter { it.spans.isNotEmpty() })
        // Windows line ends: the \r goes with the line break.
        assertEquals("uno\ndos", LimitedMarkup.parse("uno\r\ndos").text())
    }

    @Test fun theEditorsButtonsWriteTheMarks() {
        assertEquals(Triple("un **fuerte** día", 5, 11), MarkupEdit.wrap("un fuerte día", 3, 9, "**"))
        assertEquals(Triple("a****", 3, 3), MarkupEdit.wrap("a", 1, 1, "**"))
        assertEquals(Triple("**", 1, 1), MarkupEdit.wrap("", 0, 0, "*"))
        assertEquals("uno\n# dos", MarkupEdit.toggleLinePrefix("uno\ndos", 6, "# ").first)
        assertEquals("uno\ndos", MarkupEdit.toggleLinePrefix("uno\n# dos", 7, "# ").first)
        assertEquals("# dos", MarkupEdit.toggleLinePrefix("### dos", 2, "# ").first)
        assertEquals("- compra", MarkupEdit.toggleLinePrefix("compra", 0, "- ").first)
        assertEquals(Triple("compra", 0, 0), MarkupEdit.toggleLinePrefix("- compra", 1, "- "))
        // Out of range positions (a text changed meanwhile) never throw.
        assertEquals("ab****", MarkupEdit.wrap("ab", 9, 3, "**").first)
        assertEquals("- ab", MarkupEdit.toggleLinePrefix("ab", 99, "- ").first)
    }
}
