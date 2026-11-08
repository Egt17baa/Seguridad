package app.seguridad.ui.screens

import android.content.Context
import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.seguridad.SeguridadApp
import app.seguridad.android.copySensitive
import app.seguridad.crypto.Bip39
import app.seguridad.crypto.hexLower
import app.seguridad.tools.TOOLS_AWAY_WIPE_NOTE
import app.seguridad.tools.slip39.GroupForm
import app.seguridad.tools.slip39.SHARE_ADD_GROUP
import app.seguridad.tools.slip39.SHARE_BUSY
import app.seguridad.tools.slip39.SHARE_CANCEL
import app.seguridad.tools.slip39.SHARE_COMBINE_BUTTON
import app.seguridad.tools.slip39.SHARE_COPY
import app.seguridad.tools.slip39.SHARE_GROUPS_NOTE
import app.seguridad.tools.slip39.SHARE_GROUP_THRESHOLD_LABEL
import app.seguridad.tools.slip39.SHARE_HEX_LABEL
import app.seguridad.tools.slip39.SHARE_INPUT_HEX
import app.seguridad.tools.slip39.SHARE_INPUT_PHRASE
import app.seguridad.tools.slip39.SHARE_INTRO
import app.seguridad.tools.slip39.SHARE_MAX_NUMBER_CHARS
import app.seguridad.tools.slip39.SHARE_MAX_PARTS_CHARS
import app.seguridad.tools.slip39.SHARE_MAX_SECRET_CHARS
import app.seguridad.tools.slip39.SHARE_MEMBER_COUNT_LABEL
import app.seguridad.tools.slip39.SHARE_MEMBER_THRESHOLD_LABEL
import app.seguridad.tools.slip39.SHARE_MODE_COMBINE
import app.seguridad.tools.slip39.SHARE_MODE_SPLIT
import app.seguridad.tools.slip39.SHARE_NOT_PROTECTED
import app.seguridad.tools.slip39.SHARE_NOT_PROTECTED_TITLE
import app.seguridad.tools.slip39.SHARE_PARTS_LABEL
import app.seguridad.tools.slip39.SHARE_PARTS_NOTE
import app.seguridad.tools.slip39.SHARE_PASSPHRASE_LABEL
import app.seguridad.tools.slip39.SHARE_PASSPHRASE_NOTE
import app.seguridad.tools.slip39.SHARE_PHRASE_LABEL
import app.seguridad.tools.slip39.SHARE_PHRASE_NOTE
import app.seguridad.tools.slip39.SHARE_REMOVE_GROUP
import app.seguridad.tools.slip39.SHARE_RESULT_HEX
import app.seguridad.tools.slip39.SHARE_RESULT_NOTE
import app.seguridad.tools.slip39.SHARE_RESULT_PHRASE
import app.seguridad.tools.slip39.SHARE_RESULT_PHRASE_NOTE
import app.seguridad.tools.slip39.SHARE_SPLIT_BUTTON
import app.seguridad.tools.slip39.SHARE_TITLE
import app.seguridad.tools.slip39.SHARE_TOO_LONG
import app.seguridad.tools.slip39.SHARE_WARNING
import app.seguridad.tools.slip39.SHARE_WARNING_TITLE
import app.seguridad.tools.slip39.SHARE_WORDS_MISSING
import app.seguridad.tools.slip39.SLIP39_MAX_GROUPS
import app.seguridad.tools.slip39.SLIP39_MAX_PASSPHRASE_CHARS
import app.seguridad.tools.slip39.SecretShareSession
import app.seguridad.tools.slip39.Slip39
import app.seguridad.tools.slip39.phraseOrNull
import app.seguridad.tools.slip39.shareGroupHeader
import app.seguridad.tools.slip39.shareGroupTitle
import app.seguridad.tools.slip39.shareGroupsNeeded
import app.seguridad.tools.slip39.sharePartLabel
import app.seguridad.tools.slip39.splitRequest
import app.seguridad.tools.slip39.combineRequest
import app.seguridad.tools.slip39.SecretShareRuns
import app.seguridad.ui.theme.NegativeColor
import app.seguridad.ui.theme.SECRET_TEXT_KEYBOARD
import app.seguridad.ui.theme.PasswordField
import app.seguridad.ui.theme.SectionCard
import app.seguridad.vault.VaultStore
import app.seguridad.vault.WordLists
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * «Repartir un secreto» (SLIP-0039) for its navigation entry: what is typed, the parts made and the secret recovered are
 * in [SecretShareSession], never in saved state (never `rememberSaveable` nor a Bundle). The session's guard empties it
 * by the «PIN y códigos» rule (lock of the vault, another app, time away) and [onCleared] drops it when the screen is
 * left. Splitting and combining run PBKDF2 on Dispatchers.Default through [SecretShareRuns], cancellable between rounds,
 * each on a snapshot of the form; a result is shown only for the run started last, so one that ends after a wipe, a
 * cancel, an edit or a second tap is dropped and its bytes zeroed. The SLIP-39 list is read here and the
 * BIP-39 one comes from the process' [WordLists], both off the main thread.
 */
private class SecretShareViewModel(
    private val appContext: Context,
    wordLists: WordLists,
    store: VaultStore,
    lockAfterMs: () -> Long,
    awayInOtherApp: StateFlow<Boolean>,
) : ViewModel() {
    val session: SecretShareSession = SecretShareSession(ComposeCells, SystemClock.elapsedRealtime()) { runs.stop() }

    /** Declared after [session], whose wipe calls it: a wipe only comes once both exist. */
    private val runs: SecretShareRuns = SecretShareRuns(session, viewModelScope)
    var combineMode by mutableStateOf(false)
        private set
    var hexInput by mutableStateOf(false)
        private set
    var slip39 by mutableStateOf<Slip39?>(null)
        private set
    var bip39 by mutableStateOf<Bip39?>(null)
        private set
    var wordsFailed by mutableStateOf(false)
        private set

    /** The field whose paste was refused for being too long (the field keeps what it had). */
    var refused by mutableStateOf<Field?>(null)
        private set

    enum class Field { SECRET, PASSPHRASE, PARTS, NUMBER }

    init {
        watchAway(session.guard, store, lockAfterMs, awayInOtherApp)
        viewModelScope.launch {
            // Never null with the APK's own assets (checkAssets): if it were, the buttons stay off and the screen says why.
            val slip = withContext(Dispatchers.IO) {
                try {
                    Slip39(readList("slip39_english.txt"))
                } catch (e: IOException) {
                    null
                } catch (e: IllegalArgumentException) {
                    null
                }
            }
            // The BIP-39 list of the process (read once, on IO), as the other screens that know a new master.
            val bip = wordLists.bip39English()?.let { runCatching { Bip39(it) }.getOrNull() }
            if (slip == null || bip == null) {
                wordsFailed = true
            } else {
                slip39 = slip
                bip39 = bip
            }
        }
    }

    private fun readList(name: String) = appContext.assets.open(name).bufferedReader().use { it.readLines() }.filter { it.isNotBlank() }

    /** Sets a field from [value] unless it is longer than [max] characters; a refused paste is said, not cut. Typing is use of the screen. */
    fun limited(field: Field, value: String, max: Int, set: (String) -> Unit) {
        session.guard.typed()
        if (value.length <= max) {
            if (refused == field) refused = null
            set(value)
        } else {
            refused = field
        }
    }

    /** Another mode: a run going on stops, so its result never lands under the other form. */
    fun chooseMode(combine: Boolean) {
        stopRun()
        combineMode = combine
        refused = null
        session.error = null
    }

    fun chooseHex(hex: Boolean) {
        hexInput = hex
        splitEdited()
    }

    /** The parts shown were made from what the form held: once it changes they go, and a run going on stops. */
    fun splitEdited() {
        stopRun()
        session.parts = null
        session.error = null
    }

    /** The same for «Recomponer»: the secret shown is dropped (and zeroed) when the parts change. */
    fun combineEdited() {
        stopRun()
        session.recovered = null
        session.error = null
    }

    /** The passphrase is the same field in both modes: what either shows was made with the one before. */
    fun passphraseEdited() {
        splitEdited()
        session.recovered = null
    }

    fun addGroup() {
        if (session.groups.size >= SLIP39_MAX_GROUPS) return
        session.groups = session.groups + GroupForm("2", "3")
        splitEdited()
    }

    fun removeGroup(i: Int) {
        if (session.groups.size <= 1) return
        session.groups = session.groups.filterIndexed { j, _ -> j != i }
        if (session.groups.size == 1) session.groupThreshold = "1"
        splitEdited()
    }

    fun setGroup(i: Int, group: GroupForm) {
        session.groups = session.groups.mapIndexed { j, g -> if (j == i) group else g }
        splitEdited()
    }

    /** «Repartir»: the request is the form's snapshot; a second tap supersedes the run before it ([SecretShareRuns]). */
    fun split() {
        val slip = slip39 ?: return
        val words = bip39 ?: return
        val s = session
        val r = splitRequest(s.secretInput, isPhrase = !hexInput, s.passphrase, s.groupThreshold, s.groups, words)
        val master = r.master
        val pass = r.passphrase
        if (master == null || pass == null) {
            s.parts = null
            s.error = r.error
            return
        }
        runs.split({ check -> slip.splitSecret(master, pass, r.groupThreshold, r.groups, check = check) }) {
            master.fill(0)
            pass.fill('\u0000')
        }
    }

    /** «Recomponer», the same way: the parts and the passphrase are copied once, then the run works on the copy. */
    fun combine() {
        val slip = slip39 ?: return
        val s = session
        val r = combineRequest(s.typedParts, s.passphrase)
        val pass = r.passphrase
        if (pass == null) {
            s.recovered = null
            s.error = r.error
            return
        }
        runs.combine({ check -> slip.combineShares(r.parts, pass, check) }) { pass.fill('\u0000') }
    }

    /** «Cancelar», an edit or a new run: the run going on stops at its next PBKDF2 round and its result is not shown. */
    fun stopRun() = runs.stop()

    /** The over-limit note belongs to the screen being looked at: it goes when the screen is left. */
    fun clearRefused() {
        refused = null
    }

    override fun onCleared() {
        session.wipe() // stops the run too
    }
}

/** For the secret fields, as in «Cifrar texto»: no autocorrection, and the password keyboard, which keyboards do not learn from. */

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SecretShareScreen(store: VaultStore, lockAfterMs: () -> Long, awayInOtherApp: StateFlow<Boolean>, onBack: () -> Unit) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val wordLists = (appContext as SeguridadApp).container.wordLists
    val vm = viewModel { SecretShareViewModel(appContext, wordLists, store, lockAfterMs, awayInOtherApp) }
    val s = vm.session
    GuardEffects(s.guard, lockAfterMs)
    DisposableEffect(vm) { onDispose { vm.clearRefused() } }
    ToolColumn(SHARE_TITLE, onBack, s.guard) {
        Text(SHARE_INTRO, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SectionCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(SHARE_NOT_PROTECTED_TITLE, style = MaterialTheme.typography.titleSmall)
                Text(SHARE_NOT_PROTECTED)
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !vm.combineMode, onClick = { vm.chooseMode(false) }, label = { Text(SHARE_MODE_SPLIT) })
            FilterChip(selected = vm.combineMode, onClick = { vm.chooseMode(true) }, label = { Text(SHARE_MODE_COMBINE) })
        }
        if (vm.wordsFailed) Text(SHARE_WORDS_MISSING, color = NegativeColor)
        if (vm.combineMode) CombineForm(context, vm) else SplitForm(context, vm)
        Text(TOOLS_AWAY_WIPE_NOTE, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SplitForm(context: Context, vm: SecretShareViewModel) {
    val s = vm.session
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = !vm.hexInput, onClick = { vm.chooseHex(false) }, label = { Text(SHARE_INPUT_PHRASE) })
        FilterChip(selected = vm.hexInput, onClick = { vm.chooseHex(true) }, label = { Text(SHARE_INPUT_HEX) })
    }
    OutlinedTextField(
        value = s.secretInput,
        onValueChange = { v -> vm.limited(SecretShareViewModel.Field.SECRET, v, SHARE_MAX_SECRET_CHARS) { s.secretInput = it; vm.splitEdited() } },
        label = { Text(if (vm.hexInput) SHARE_HEX_LABEL else SHARE_PHRASE_LABEL) },
        minLines = 2,
        keyboardOptions = SECRET_TEXT_KEYBOARD,
        modifier = Modifier.fillMaxWidth(),
    )
    TooLong(vm, SecretShareViewModel.Field.SECRET)
    if (!vm.hexInput) Note(SHARE_PHRASE_NOTE)

    if (s.groups.size > 1) {
        NumberField(vm, SHARE_GROUP_THRESHOLD_LABEL, s.groupThreshold, Modifier.fillMaxWidth()) { s.groupThreshold = it; vm.splitEdited() }
    }
    s.groups.forEachIndexed { i, g ->
        Text(shareGroupTitle(i + 1), style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            NumberField(vm, SHARE_MEMBER_THRESHOLD_LABEL, g.threshold, Modifier.weight(1f)) { vm.setGroup(i, g.copy(threshold = it)) }
            NumberField(vm, SHARE_MEMBER_COUNT_LABEL, g.count, Modifier.weight(1f)) { vm.setGroup(i, g.copy(count = it)) }
        }
        if (s.groups.size > 1) TextButton(onClick = { vm.removeGroup(i) }) { Text(SHARE_REMOVE_GROUP) }
    }
    TooLong(vm, SecretShareViewModel.Field.NUMBER)
    OutlinedButton(onClick = vm::addGroup, enabled = s.groups.size < SLIP39_MAX_GROUPS) { Text(SHARE_ADD_GROUP) }
    Note(SHARE_GROUPS_NOTE)

    Passphrase(vm)

    // The warning required before any part is shown, in its own card.
    SectionCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(SHARE_WARNING_TITLE, style = MaterialTheme.typography.titleSmall)
            Text(SHARE_WARNING)
        }
    }
    RunButton(vm, SHARE_SPLIT_BUTTON, enabled = vm.slip39 != null && vm.bip39 != null, onClick = vm::split)
    s.error?.let { Text(it, color = NegativeColor) }

    val parts = s.parts ?: return
    Text(shareGroupsNeeded(s.groupThreshold.trim().toIntOrNull() ?: 1, parts.size))
    parts.forEachIndexed { gi, members ->
        val spec = s.groups.getOrNull(gi)
        SectionCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    shareGroupHeader(gi + 1, spec?.threshold?.trim()?.toIntOrNull() ?: members.size, members.size),
                    style = MaterialTheme.typography.titleSmall,
                )
                members.forEachIndexed { mi, value ->
                    Text(sharePartLabel(mi + 1), style = MaterialTheme.typography.labelMedium)
                    SelectionContainer { Text(value, fontFamily = FontFamily.Monospace) }
                    FilledTonalButton(onClick = { copySensitive(context, value) }) { Text(SHARE_COPY) }
                }
            }
        }
    }
}

@Composable
private fun CombineForm(context: Context, vm: SecretShareViewModel) {
    val s = vm.session
    OutlinedTextField(
        value = s.typedParts,
        onValueChange = { v -> vm.limited(SecretShareViewModel.Field.PARTS, v, SHARE_MAX_PARTS_CHARS) { s.typedParts = it; vm.combineEdited() } },
        label = { Text(SHARE_PARTS_LABEL) },
        minLines = 4,
        keyboardOptions = SECRET_TEXT_KEYBOARD,
        modifier = Modifier.fillMaxWidth(),
    )
    TooLong(vm, SecretShareViewModel.Field.PARTS)
    Note(SHARE_PARTS_NOTE)
    Passphrase(vm)
    RunButton(vm, SHARE_COMBINE_BUTTON, enabled = vm.slip39 != null, onClick = vm::combine)
    s.error?.let { Text(it, color = NegativeColor) }

    val secret = s.recovered ?: return
    val words = vm.bip39
    // Display copies: Strings cannot be zeroed, and they go with the composition once the secret is dropped.
    val hex = remember(secret) { hexLower(secret) }
    val phrase = remember(secret, words) { words?.let { phraseOrNull(it, secret) } }
    SectionCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(SHARE_RESULT_HEX, style = MaterialTheme.typography.labelMedium)
            Output(context, hex)
            if (phrase != null) {
                Text(SHARE_RESULT_PHRASE, style = MaterialTheme.typography.labelMedium)
                Output(context, phrase, note = SHARE_RESULT_PHRASE_NOTE)
            }
            Text(SHARE_RESULT_NOTE, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Output(context: Context, value: String, note: String? = null) {
    SelectionContainer { Text(value, fontFamily = FontFamily.Monospace) }
    // Between the value and «Copiar», so it is read before the copy (review I-1).
    note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    FilledTonalButton(onClick = { copySensitive(context, value) }) { Text(SHARE_COPY) }
}

@Composable
private fun Passphrase(vm: SecretShareViewModel) {
    val s = vm.session
    PasswordField(
        value = s.passphrase,
        onChange = { v -> vm.limited(SecretShareViewModel.Field.PASSPHRASE, v, SLIP39_MAX_PASSPHRASE_CHARS) { s.passphrase = it; vm.passphraseEdited() } },
        label = SHARE_PASSPHRASE_LABEL,
    )
    TooLong(vm, SecretShareViewModel.Field.PASSPHRASE)
    Note(SHARE_PASSPHRASE_NOTE)
}

@Composable
private fun RunButton(vm: SecretShareViewModel, label: String, enabled: Boolean, onClick: () -> Unit) {
    val s = vm.session
    if (s.busy) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Text(SHARE_BUSY)
            TextButton(onClick = vm::stopRun) { Text(SHARE_CANCEL) }
        }
    } else {
        Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(label) }
    }
}

@Composable
private fun NumberField(vm: SecretShareViewModel, label: String, value: String, modifier: Modifier = Modifier, set: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> vm.limited(SecretShareViewModel.Field.NUMBER, v, SHARE_MAX_NUMBER_CHARS, set) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

/** Under a field: a paste over its limit is said, not just ignored. */
@Composable
private fun TooLong(vm: SecretShareViewModel, field: SecretShareViewModel.Field) {
    if (vm.refused == field) Text(SHARE_TOO_LONG, color = NegativeColor)
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
