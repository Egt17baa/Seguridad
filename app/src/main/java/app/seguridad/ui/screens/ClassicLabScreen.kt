package app.seguridad.ui.screens

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import app.seguridad.android.copySensitive
import app.seguridad.tools.ENIGMA_SHORT
import app.seguridad.tools.TOOLS_AWAY_WIPE_NOTE
import app.seguridad.tools.TOOLS_AWAY_WIPE_SHORT
import app.seguridad.tools.classic.ANALYSIS_FREQUENCIES
import app.seguridad.tools.classic.ANALYSIS_INTRO
import app.seguridad.tools.classic.ANALYSIS_IN_BETWEEN
import app.seguridad.tools.classic.ANALYSIS_LOOKS_NORMAL
import app.seguridad.tools.classic.ANALYSIS_LOOKS_RANDOM
import app.seguridad.tools.classic.ANALYSIS_REFERENCE
import app.seguridad.tools.classic.ANALYSIS_TEXT_LABEL
import app.seguridad.tools.classic.ANALYSIS_TOO_FEW
import app.seguridad.tools.classic.CAESAR_SHIFT_INVALID
import app.seguridad.tools.classic.CAESAR_SHIFT_LABEL
import app.seguridad.tools.classic.CLASSIC_COPY
import app.seguridad.tools.classic.CLASSIC_DECRYPT
import app.seguridad.tools.classic.CLASSIC_ENCRYPT
import app.seguridad.tools.classic.CLASSIC_LETTERS_ONLY
import app.seguridad.tools.classic.CLASSIC_NOTICE
import app.seguridad.tools.classic.CLASSIC_NOT_SAVED
import app.seguridad.tools.classic.CLASSIC_RESULT
import app.seguridad.tools.classic.CLASSIC_TAB_ANALYSIS
import app.seguridad.tools.classic.CLASSIC_TAB_CAESAR
import app.seguridad.tools.classic.CLASSIC_TAB_ENIGMA
import app.seguridad.tools.classic.CLASSIC_TAB_VIGENERE
import app.seguridad.tools.classic.CLASSIC_TEXT_LABEL
import app.seguridad.tools.classic.CLASSIC_TITLE
import app.seguridad.tools.classic.CLASSIC_TOO_LONG
import app.seguridad.tools.classic.CLASSIC_VALUE_TOO_LONG
import app.seguridad.tools.classic.ClassicLabSession
import app.seguridad.tools.classic.ENIGMA_INTRO
import app.seguridad.tools.classic.ENIGMA_LETTERS_ONLY
import app.seguridad.tools.classic.ENIGMA_PLUGS_LABEL
import app.seguridad.tools.classic.ENIGMA_POSITIONS_LABEL
import app.seguridad.tools.classic.ENIGMA_REFLECTOR
import app.seguridad.tools.classic.ENIGMA_RINGS_LABEL
import app.seguridad.tools.classic.ENIGMA_ROTORS
import app.seguridad.tools.classic.ENIGMA_ROTOR_LEFT
import app.seguridad.tools.classic.ENIGMA_ROTOR_MIDDLE
import app.seguridad.tools.classic.ENIGMA_ROTOR_RIGHT
import app.seguridad.tools.classic.ENIGMA_SAME_KEY
import app.seguridad.tools.classic.Enigma
import app.seguridad.tools.classic.IC_ENGLISH
import app.seguridad.tools.classic.IC_UNIFORM
import app.seguridad.tools.classic.MAX_CLASSIC_CHARS
import app.seguridad.tools.classic.MAX_CLASSIC_KEY_CHARS
import app.seguridad.tools.classic.Reflector
import app.seguridad.tools.classic.Rotor
import app.seguridad.tools.classic.VIGENERE_KEY_INVALID
import app.seguridad.tools.classic.VIGENERE_KEY_LABEL
import app.seguridad.tools.classic.VIGENERE_NOTE
import app.seguridad.tools.classic.analysisIc
import app.seguridad.tools.classic.analysisLetters
import app.seguridad.tools.classic.buildEnigma
import app.seguridad.tools.classic.caesar
import app.seguridad.tools.classic.enigmaFinalPositions
import app.seguridad.tools.classic.indexOfCoincidence
import app.seguridad.tools.classic.letterCounts
import app.seguridad.tools.classic.vigenere
import app.seguridad.ui.theme.NegativeColor
import app.seguridad.ui.theme.SECRET_TEXT_KEYBOARD
import app.seguridad.ui.theme.SectionCard
import app.seguridad.vault.VaultStore
import java.util.Locale
import kotlinx.coroutines.flow.StateFlow

private enum class ClassicTab(val label: String) {
    CAESAR(CLASSIC_TAB_CAESAR),
    VIGENERE(CLASSIC_TAB_VIGENERE),
    ENIGMA(CLASSIC_TAB_ENIGMA),
    ANALYSIS(CLASSIC_TAB_ANALYSIS),
}

/**
 * What the «Laboratorio clásico» keeps for its navigation entry: the typed fields ([ClassicLabSession]) survive a rotation
 * or another tab and are never saved state (never `rememberSaveable` nor a Bundle); the session's guard empties them by
 * the «PIN y códigos» rule (lock of the vault, another app, time away) and [onCleared] drops them when the screen is
 * left. The rotors and the reflector are part of the Enigma key and go with the rest; the tab and the mode stay. Nothing is stored and the
 * vault is not used.
 */
private class ClassicLabViewModel(store: VaultStore, lockAfterMs: () -> Long, awayInOtherApp: StateFlow<Boolean>) : ViewModel() {
    val session = ClassicLabSession(ComposeCells, SystemClock.elapsedRealtime())
    var tab by mutableIntStateOf(0)
    var caesarDecrypt by mutableStateOf(false)
    var vigenereDecrypt by mutableStateOf(false)

    /** The field whose paste was refused for being too long (the field keeps what it had). */
    var refused by mutableStateOf<Field?>(null)
        private set

    init {
        watchAway(session.guard, store, lockAfterMs, awayInOtherApp)
    }

    enum class Field { CAESAR, VIGENERE, ENIGMA, ANALYSIS, SHIFT, KEY, RINGS, POSITIONS, PLUGS }

    /** Sets a field from [value] unless it is longer than [max] characters; a refused paste is said, not cut. Typing is use of the screen. */
    fun limited(field: Field?, value: String, max: Int, set: (String) -> Unit) {
        session.guard.typed()
        if (value.length <= max) {
            if (refused == field) refused = null
            set(value)
        } else {
            refused = field
        }
    }

    fun chooseTab(i: Int) {
        tab = i
        refused = null
    }

    /** The over-limit note belongs to the screen being looked at: it goes when the screen is left. */
    fun clearRefused() {
        refused = null
    }

    override fun onCleared() {
        session.wipe()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClassicLabScreen(store: VaultStore, lockAfterMs: () -> Long, awayInOtherApp: StateFlow<Boolean>, onBack: () -> Unit) {
    val context = LocalContext.current
    val vm = viewModel { ClassicLabViewModel(store, lockAfterMs, awayInOtherApp) }
    val s = vm.session
    GuardEffects(s.guard, lockAfterMs)
    DisposableEffect(vm) { onDispose { vm.clearRefused() } }
    ToolColumn(CLASSIC_TITLE, onBack, s.guard) {
        SectionCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(CLASSIC_NOTICE)
                Text(CLASSIC_NOT_SAVED, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ClassicTab.entries.forEachIndexed { i, t ->
                FilterChip(selected = vm.tab == i, onClick = { vm.chooseTab(i) }, label = { Text(t.label) })
            }
        }
        when (ClassicTab.entries[vm.tab]) {
            ClassicTab.CAESAR -> {
                ModeChips(vm.caesarDecrypt) { vm.caesarDecrypt = it }
                TextInput(vm, ClassicLabViewModel.Field.CAESAR, CLASSIC_TEXT_LABEL, s.caesarText) { s.caesarText = it }
                OutlinedTextField(
                    value = s.shift,
                    onValueChange = { v -> vm.limited(ClassicLabViewModel.Field.SHIFT, v, 6) { s.shift = it } },
                    label = { Text(CAESAR_SHIFT_LABEL) },
                    singleLine = true,
                    keyboardOptions = SECRET_TEXT_KEYBOARD,
                    modifier = Modifier.fillMaxWidth(),
                )
                KeyTooLong(vm, ClassicLabViewModel.Field.SHIFT)
                val k = s.shift.trim().toIntOrNull()?.takeIf { it in 0..25 }
                if (k == null) {
                    Text(CAESAR_SHIFT_INVALID, color = NegativeColor)
                } else {
                    val out = remember(s.caesarText, k, vm.caesarDecrypt) { caesar(s.caesarText, if (vm.caesarDecrypt) -k else k) }
                    Output(context, out)
                }
            }
            ClassicTab.VIGENERE -> {
                ModeChips(vm.vigenereDecrypt) { vm.vigenereDecrypt = it }
                TextInput(vm, ClassicLabViewModel.Field.VIGENERE, CLASSIC_TEXT_LABEL, s.vigenereText) { s.vigenereText = it }
                OutlinedTextField(
                    value = s.vigenereKey,
                    onValueChange = { v -> vm.limited(ClassicLabViewModel.Field.KEY, v, MAX_CLASSIC_KEY_CHARS) { s.vigenereKey = it } },
                    label = { Text(VIGENERE_KEY_LABEL) },
                    singleLine = true,
                    keyboardOptions = SECRET_TEXT_KEYBOARD,
                    modifier = Modifier.fillMaxWidth(),
                )
                KeyTooLong(vm, ClassicLabViewModel.Field.KEY)
                if (s.vigenereKey.none { it in 'A'..'Z' || it in 'a'..'z' }) {
                    Text(VIGENERE_KEY_INVALID, color = NegativeColor)
                } else {
                    val out = remember(s.vigenereText, s.vigenereKey, vm.vigenereDecrypt) {
                        vigenere(s.vigenereText, s.vigenereKey, vm.vigenereDecrypt)
                    }
                    Output(context, out)
                }
                Text(VIGENERE_NOTE, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ClassicTab.ENIGMA -> EnigmaTab(context, vm)
            ClassicTab.ANALYSIS -> AnalysisTab(vm)
        }
        Text(TOOLS_AWAY_WIPE_SHORT, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        // 1.4.2 (texto mínimo): the explanations, folded.
        MoreInfo {
            Text(CLASSIC_LETTERS_ONLY, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(ENIGMA_INTRO, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(ENIGMA_LETTERS_ONLY, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(TOOLS_AWAY_WIPE_NOTE, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModeChips(decrypt: Boolean, set: (Boolean) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = !decrypt, onClick = { set(false) }, label = { Text(CLASSIC_ENCRYPT) })
        FilterChip(selected = decrypt, onClick = { set(true) }, label = { Text(CLASSIC_DECRYPT) })
    }
}

@Composable
private fun TextInput(vm: ClassicLabViewModel, field: ClassicLabViewModel.Field, label: String, value: String, set: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> vm.limited(field, v, MAX_CLASSIC_CHARS, set) },
        label = { Text(label) },
        minLines = 3,
        keyboardOptions = SECRET_TEXT_KEYBOARD,
        modifier = Modifier.fillMaxWidth(),
    )
    if (vm.refused == field) Text(CLASSIC_TOO_LONG, color = NegativeColor)
}

/** Under a key field: a paste over its limit is said, not just ignored. */
@Composable
private fun KeyTooLong(vm: ClassicLabViewModel, field: ClassicLabViewModel.Field) {
    if (vm.refused == field) Text(CLASSIC_VALUE_TOO_LONG, color = NegativeColor)
}

@Composable
private fun Output(context: android.content.Context, value: String, extra: String? = null) {
    SectionCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(CLASSIC_RESULT, style = MaterialTheme.typography.labelMedium)
            SelectionContainer { Text(value, fontFamily = FontFamily.Monospace) }
            extra?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            FilledTonalButton(onClick = { copySensitive(context, value) }) { Text(CLASSIC_COPY) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EnigmaTab(context: android.content.Context, vm: ClassicLabViewModel) {
    val s = vm.session
    Text(ENIGMA_SHORT, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(ENIGMA_ROTORS, style = MaterialTheme.typography.labelMedium)
    listOf(ENIGMA_ROTOR_LEFT, ENIGMA_ROTOR_MIDDLE, ENIGMA_ROTOR_RIGHT).forEachIndexed { slot, name ->
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(name, modifier = Modifier.padding(top = 8.dp, end = 4.dp))
            Rotor.entries.forEach { r ->
                FilterChip(
                    selected = s.rotors[slot] == r,
                    onClick = { s.rotors = s.rotors.toMutableList().also { it[slot] = r } },
                    label = { Text(r.name) },
                )
            }
        }
    }
    Text(ENIGMA_REFLECTOR, style = MaterialTheme.typography.labelMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Reflector.entries.forEach { r ->
            FilterChip(selected = s.reflector == r, onClick = { s.reflector = r }, label = { Text(r.name) })
        }
    }
    OutlinedTextField(
        value = s.rings,
        onValueChange = { v -> vm.limited(ClassicLabViewModel.Field.RINGS, v, MAX_CLASSIC_KEY_CHARS) { s.rings = it } },
        label = { Text(ENIGMA_RINGS_LABEL) },
        singleLine = true,
        keyboardOptions = SECRET_TEXT_KEYBOARD,
        modifier = Modifier.fillMaxWidth(),
    )
    KeyTooLong(vm, ClassicLabViewModel.Field.RINGS)
    OutlinedTextField(
        value = s.positions,
        onValueChange = { v -> vm.limited(ClassicLabViewModel.Field.POSITIONS, v, MAX_CLASSIC_KEY_CHARS) { s.positions = it } },
        label = { Text(ENIGMA_POSITIONS_LABEL) },
        singleLine = true,
        keyboardOptions = SECRET_TEXT_KEYBOARD,
        modifier = Modifier.fillMaxWidth(),
    )
    KeyTooLong(vm, ClassicLabViewModel.Field.POSITIONS)
    OutlinedTextField(
        value = s.plugs,
        onValueChange = { v -> vm.limited(ClassicLabViewModel.Field.PLUGS, v, MAX_CLASSIC_KEY_CHARS) { s.plugs = it } },
        label = { Text(ENIGMA_PLUGS_LABEL) },
        keyboardOptions = SECRET_TEXT_KEYBOARD,
        modifier = Modifier.fillMaxWidth(),
    )
    KeyTooLong(vm, ClassicLabViewModel.Field.PLUGS)
    TextInput(vm, ClassicLabViewModel.Field.ENIGMA, CLASSIC_TEXT_LABEL, s.enigmaText) { s.enigmaText = it }
    val form = remember(s.rotors, s.reflector, s.rings, s.positions, s.plugs) {
        buildEnigma(s.rotors, s.reflector, s.rings, s.positions, s.plugs)
    }
    val settings = form.settings
    if (settings == null) {
        Text(form.error.orEmpty(), color = NegativeColor)
    } else {
        // The text is at most MAX_CLASSIC_CHARS long, so running the machine on every change is cheap.
        val result = remember(settings, s.enigmaText) {
            val machine = Enigma(settings)
            machine.process(s.enigmaText) to machine.positions
        }
        Output(context, result.first, enigmaFinalPositions(result.second))
    }
    Text(ENIGMA_SAME_KEY, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private val ES = Locale.forLanguageTag("es-ES")

@Composable
private fun AnalysisTab(vm: ClassicLabViewModel) {
    val s = vm.session
    Text(ANALYSIS_INTRO, color = MaterialTheme.colorScheme.onSurfaceVariant)
    TextInput(vm, ClassicLabViewModel.Field.ANALYSIS, ANALYSIS_TEXT_LABEL, s.analysisText) { s.analysisText = it }
    // Only the bounded text (MAX_CLASSIC_CHARS) is measured.
    val counts = remember(s.analysisText) { letterCounts(s.analysisText) }
    val total = counts.sum()
    if (total < 2) {
        Text(ANALYSIS_TOO_FEW, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val ic = remember(s.analysisText) { indexOfCoincidence(s.analysisText) }
    SectionCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(analysisLetters(total))
            Text(analysisIc(String.format(ES, "%.4f", ic)))
            Text(ANALYSIS_REFERENCE, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val looks = when {
                ic >= (IC_ENGLISH + IC_UNIFORM) / 2 + 0.005 -> ANALYSIS_LOOKS_NORMAL
                ic <= (IC_ENGLISH + IC_UNIFORM) / 2 - 0.005 -> ANALYSIS_LOOKS_RANDOM
                else -> ANALYSIS_IN_BETWEEN
            }
            Text(looks)
        }
    }
    SectionCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(ANALYSIS_FREQUENCIES, style = MaterialTheme.typography.labelMedium)
            val max = counts.max()
            val rows = remember(counts) {
                counts.indices.filter { counts[it] > 0 }.sortedByDescending { counts[it] }.joinToString("\n") { i ->
                    val bar = "█".repeat((counts[i] * 20 + max - 1) / max)
                    String.format(ES, "%c %6d %5.1f %%  %s", 'A' + i, counts[i], counts[i] * 100.0 / total, bar)
                }
            }
            SelectionContainer { Text(rows, fontFamily = FontFamily.Monospace) }
        }
    }
}
