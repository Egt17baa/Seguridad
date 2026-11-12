package app.seguridad.ui.screens

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em
import app.seguridad.vault.LimitedMarkup
import app.seguridad.vault.MarkupBlock
import app.seguridad.vault.Span

/*
 * 2.0, task 5: the notes with format ([app.seguridad.vault.NotesFormat.LIMITED]) as styled text. Only styles (bold,
 * italic, the size of a title) and the bullets and numbers of the lists: never an annotation of any kind, so nothing in
 * it is a link or can be tapped, and the text is never read by anything that finds links (decision (c) of 1.4.0).
 */

private fun SpanStyle.with(span: Span): SpanStyle = merge(
    SpanStyle(
        fontWeight = if (span.bold) FontWeight.Bold else null,
        fontStyle = if (span.italic) FontStyle.Italic else null,
    ),
)

private fun headingStyle(level: Int) = SpanStyle(
    fontWeight = FontWeight.Bold,
    fontSize = when (level) {
        1 -> 1.4.em
        2 -> 1.2.em
        else -> 1.05.em
    },
)

/** [blocks] as one text, a line per block: styles only, no annotations. */
internal fun markupAnnotated(blocks: List<MarkupBlock>): AnnotatedString = buildAnnotatedString {
    blocks.forEachIndexed { i, block ->
        if (i > 0) append('\n')
        val base = when (block) {
            is MarkupBlock.Heading -> headingStyle(block.level)
            else -> SpanStyle()
        }
        when (block) {
            is MarkupBlock.Bullet -> append("•  ")
            is MarkupBlock.Numbered -> append("${block.n}.  ")
            else -> Unit
        }
        for (span in block.spans) withStyle(base.with(span)) { append(span.text) }
    }
}

/** [text] read with [LimitedMarkup] (bounded, once per text) and shown as styled text with nothing to tap. */
@Composable
internal fun MarkupText(text: String) {
    val styled = remember(text) { markupAnnotated(LimitedMarkup.parse(text)) }
    Text(styled)
}
