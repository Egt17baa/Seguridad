package app.seguridad.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.seguridad.imports.plainReportText
import app.seguridad.ui.theme.BackScaffold
import app.seguridad.ui.theme.SectionCard
import app.seguridad.vault.BIG_DIGITS
import app.seguridad.vault.BIG_LETTERS
import app.seguridad.vault.BIG_SPACE
import app.seguridad.vault.BIG_SYMBOLS
import app.seguridad.vault.BIG_TITLE
import app.seguridad.vault.EntryType
import app.seguridad.vault.VaultEntry
import app.seguridad.vault.VaultState
import app.seguridad.vault.VaultStore
import app.seguridad.vault.isSecretField
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** What kind of character a cell of «Ver en grande» shows: each with its colour and its own mark (never colour alone, R-1). */
internal enum class CharKind { LETTER, DIGIT, SYMBOL, SPACE }

/** One character of «Ver en grande»: [shown] (a mark for one that does not show, such as a space), its [kind]. */
internal data class BigCell(val shown: String, val kind: CharKind)

/** How many characters each group of «Ver en grande» has. */
internal const val BIG_GROUP = 4

/**
 * [password] as «Ver en grande» shows it: one cell per code point (a surrogate pair is never split, and a bidi control
 * cannot reorder the others, since each cell is drawn on its own), in groups of [BIG_GROUP]. A space shows «␣», and a
 * control, a format character (bidi marks, zero widths) or any other that does not show, «�», so none hides.
 */
internal fun bigPasswordCells(password: String): List<List<BigCell>> {
    val cells = ArrayList<BigCell>()
    var i = 0
    while (i < password.length) {
        val cp = password.codePointAt(i)
        i += Character.charCount(cp)
        cells += when {
            Character.isLetter(cp) -> BigCell(String(Character.toChars(cp)), CharKind.LETTER)
            Character.isDigit(cp) -> BigCell(String(Character.toChars(cp)), CharKind.DIGIT)
            Character.isWhitespace(cp) || Character.isSpaceChar(cp) -> BigCell("␣", CharKind.SPACE)
            invisible(cp) -> BigCell("�", CharKind.SYMBOL)
            else -> BigCell(String(Character.toChars(cp)), CharKind.SYMBOL)
        }
    }
    return cells.chunked(BIG_GROUP)
}

private fun invisible(cp: Int): Boolean = when (Character.getType(cp).toByte()) {
    Character.CONTROL, Character.FORMAT, Character.UNASSIGNED, Character.PRIVATE_USE, Character.SURROGATE,
    Character.LINE_SEPARATOR, Character.PARAGRAPH_SEPARATOR, Character.NON_SPACING_MARK, Character.ENCLOSING_MARK,
    -> true
    else -> false
}

/**
 * The secret «Ver en grande» shows, read from the open vault: with no [fieldId], the password of a login or the
 * passphrase of a seed; with one, that field of the entry, only if it is a secret ([isSecretField]) and the entry has it
 * once. Null otherwise (the screen closes).
 */
internal fun bigPasswordValue(entry: VaultEntry?, fieldId: String?): String? {
    if (entry == null || entry.type.isPersonal) return null
    val value = if (fieldId == null) {
        entry.password.takeIf { entry.type == EntryType.LOGIN || entry.type == EntryType.SEED }
    } else {
        entry.fields.filter { it.id == fieldId }.singleOrNull()?.takeIf { isSecretField(entry.type, it) }?.value
    }
    return value?.takeIf { it.isNotEmpty() }
}

/**
 * Holds what «Ver en grande» shows for the entry [id] (and its field [fieldId]): read from the vault's state, so the
 * secret never goes through the navigation's arguments, a Bundle or saved state; null as soon as the vault locks.
 */
private class BigPasswordViewModel(store: VaultStore, private val id: String, private val fieldId: String?) : ViewModel() {
    private fun read(s: VaultState): Pair<String, String>? {
        val entry = (s as? VaultState.Unlocked)?.entries?.firstOrNull { it.id == id }
        return bigPasswordValue(entry, fieldId)?.let { plainReportText(entry!!.title) to it }
    }

    val shown: StateFlow<Pair<String, String>?> = store.state.map(::read).stateIn(viewModelScope, SharingStarted.Eagerly, read(store.state.value))
}

/**
 * 2.0, task 6 (design «Ficha»): a password in big monospaced letters, in groups of 4, each character with the colour and
 * the mark of its kind (letter, digit, symbol). It turns with the phone and closes when the vault locks or the entry, or
 * its field, is gone. No text selection: the system «Copiar» would skip the sensitive flag and the timed clearing.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BigPasswordScreen(store: VaultStore, id: String, fieldId: String?, onBack: () -> Unit) {
    VaultIdleLock(store)
    val vm = viewModel { BigPasswordViewModel(store, id, fieldId) }
    val shown by vm.shown.collectAsStateWithLifecycle()
    val (title, password) = shown ?: return LaunchedEffect(Unit) { onBack() }
    val groups = bigPasswordCells(password)
    BackScaffold("$BIG_TITLE · $title", onBack) { padding ->
        Column(
            Modifier.padding(padding).touchesVault(store).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((kind, label) in listOf(CharKind.LETTER to BIG_LETTERS, CharKind.DIGIT to BIG_DIGITS, CharKind.SYMBOL to BIG_SYMBOLS)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Cell(BigCell(if (kind == CharKind.LETTER) "a" else if (kind == CharKind.DIGIT) "1" else "#", kind), small = true)
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            SectionCard(Modifier.fillMaxWidth()) {
                FlowRow(
                    Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    groups.forEach { group ->
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { group.forEach { Cell(it, small = false) } }
                    }
                }
            }
        }
    }
}

/** One character: letters plain, digits bold and underlined, symbols in a box; each kind also in its own colour. */
@Composable
private fun Cell(c: BigCell, small: Boolean) {
    val color: Color = when (c.kind) {
        CharKind.LETTER -> MaterialTheme.colorScheme.onSurface
        CharKind.DIGIT -> MaterialTheme.colorScheme.primary
        CharKind.SYMBOL, CharKind.SPACE -> MaterialTheme.colorScheme.tertiary
    }
    val boxed = c.kind == CharKind.SYMBOL || c.kind == CharKind.SPACE
    Box(
        (if (boxed) Modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp)) else Modifier)
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .then(if (c.kind == CharKind.SPACE) Modifier.semantics { contentDescription = BIG_SPACE } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            c.shown,
            color = color,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (c.kind == CharKind.DIGIT) FontWeight.Bold else FontWeight.Normal,
            textDecoration = if (c.kind == CharKind.DIGIT) TextDecoration.Underline else null,
            fontSize = if (small) 18.sp else 34.sp,
        )
    }
}
