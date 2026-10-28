package app.seguridad.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.seguridad.android.copySensitive
import app.seguridad.tools.ANSWERS_ADD_QUESTION
import app.seguridad.tools.ANSWERS_BLANK_QUESTION
import app.seguridad.tools.ANSWERS_REMOVE_QUESTION
import app.seguridad.tools.ANSWERS_EXPLANATION
import app.seguridad.tools.ANSWERS_LIMIT
import app.seguridad.tools.ANSWERS_NO_ACCENTS
import app.seguridad.tools.ANSWERS_NO_ACCENTS_NOTE
import app.seguridad.tools.ANSWERS_OTHER
import app.seguridad.tools.ANSWERS_SAVE
import app.seguridad.tools.ANSWERS_TITLE
import app.seguridad.tools.ANSWERS_WARNINGS
import app.seguridad.tools.CODES_ANOTHER_CODES
import app.seguridad.tools.CODES_ANOTHER_PIN
import app.seguridad.tools.CODES_CANCEL_SAVE
import app.seguridad.tools.CODES_COPY
import app.seguridad.tools.CODES_COPY_ALL
import app.seguridad.tools.CODES_PIN_COUNTING
import app.seguridad.tools.CODES_PIN_LENGTH
import app.seguridad.tools.CODES_PIN_TITLE
import app.seguridad.tools.CODES_QUESTION_LABEL
import app.seguridad.tools.CODES_RECOVERY_TITLE
import app.seguridad.tools.CODES_SAVED
import app.seguridad.tools.CODES_SAVE_AS_NOTE
import app.seguridad.tools.CODES_SAVE_FAILED
import app.seguridad.tools.CODES_SITE_LABEL
import app.seguridad.tools.CODES_SITE_NEEDED
import app.seguridad.tools.CODES_TOOL_TITLE
import app.seguridad.tools.CODES_UNLOCK_TO_SAVE
import app.seguridad.tools.RECOVERY_CODES_TEXT
import app.seguridad.tools.codes.InventedQuestion
import app.seguridad.tools.codes.generatePin
import app.seguridad.tools.codes.inventedAnswer
import app.seguridad.tools.codes.inventedAnswersNote
import app.seguridad.tools.codes.pinBits
import app.seguridad.tools.codes.recoveryCodes
import app.seguridad.tools.codes.recoveryCodesNote
import app.seguridad.tools.codes.strongPinCount
import app.seguridad.tools.pinCountText
import app.seguridad.ui.theme.NoCopy
import app.seguridad.ui.theme.SectionCard
import app.seguridad.vault.VaultEntry
import app.seguridad.vault.VaultState
import app.seguridad.vault.VaultStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * How many PINs are left per length: a constant of the rules (not a secret). One shared computation per length for the
 * whole run of the app: a second request (another visit, a quick change of length) waits for the first and reuses it.
 */
private val pinCountScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
private val pinCounts = ConcurrentHashMap<Int, Deferred<Long>>()

private fun pinCount(digits: Int): Deferred<Long> = pinCounts.computeIfAbsent(digits) { pinCountScope.async { strongPinCount(digits) } }

private enum class SaveKind { RECOVERY, ANSWERS }

private class AnswerRow(question: String, answer: String) {
    var question by mutableStateOf(question)
    var answer by mutableStateOf(answer)
}

/**
 * «PIN y códigos» (`tools/codes`): a random PIN without the usual patterns, ten recovery codes and invented answers for
 * security questions. All three are drawn here from the app's secure random source and nothing leaves the phone.
 *
 * What is drawn is a secret: it lives only in `remember` (never in saved state, never in a log), and the system text
 * menu has no Copy ([NoCopy]); it leaves through «Copiar» ([copySensitive]) or into the vault as a note. Saving goes
 * through the existing [VaultGate] when the vault is locked, and the draw waits on this screen meanwhile.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodesScreen(store: VaultStore, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val actions = vaultActions(store, snackbar)
    val vaultState by store.state.collectAsStateWithLifecycle()
    // The Bóveda tab loads it too; this screen can be reached first.
    LaunchedEffect(store) { store.load() }

    val words = remember { context.assets.open("bip39_spanish.txt").bufferedReader(Charsets.UTF_8).use { it.readLines() }.filter { it.isNotBlank() } }

    var digits by remember { mutableIntStateOf(6) }
    var pin by remember { mutableStateOf(generatePin(6)) }
    var count by remember { mutableStateOf<Long?>(null) }
    // Off the main thread, once per length: 10^8 values at 8 digits take a few seconds.
    LaunchedEffect(digits) { count = pinCount(digits).await() }

    var codes by remember { mutableStateOf(recoveryCodes()) }
    var recoverySite by remember { mutableStateOf("") }

    var plain by remember { mutableStateOf(true) }
    var answersSite by remember { mutableStateOf("") }
    val rows = remember { mutableStateListOf(AnswerRow("", inventedAnswer(words, plain = true))) }

    var pending by remember { mutableStateOf<SaveKind?>(null) }
    var saving by remember { mutableStateOf(false) }

    /** Writes the note. On success the saved values leave the screen (drawn again or blank), so they do not stay in view. */
    suspend fun save(kind: SaveKind) {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        val entry: VaultEntry = when (kind) {
            SaveKind.RECOVERY -> recoveryCodesNote(recoverySite.trim(), codes, now, id)
            SaveKind.ANSWERS -> inventedAnswersNote(
                answersSite.trim(),
                rows.map { InventedQuestion(it.question.trim(), it.answer) },
                now,
                id,
            )
        }
        val saved = try {
            store.upsert(entry)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false // Nothing is logged: the note holds the secrets.
        }
        if (saved) {
            when (kind) {
                SaveKind.RECOVERY -> {
                    codes = recoveryCodes()
                    recoverySite = ""
                }
                SaveKind.ANSWERS -> {
                    answersSite = ""
                    rows.clear()
                    rows.add(AnswerRow("", inventedAnswer(words, plain)))
                }
            }
        }
        // Its own coroutine: showSnackbar waits until the message goes, and nothing here waits for that.
        scope.launch { snackbar.showSnackbar(if (saved) CODES_SAVED else CODES_SAVE_FAILED) }
    }

    fun requestSave(kind: SaveKind) {
        if (saving || pending != null) return // One save at a time: a second tap would add a second note.
        if (vaultState is VaultState.Unlocked) {
            saving = true
            scope.launch {
                try {
                    save(kind)
                } finally {
                    saving = false
                }
            }
        } else {
            pending = kind
        }
    }

    val busy = saving || pending != null
    val blankQuestion = rows.any { it.question.isBlank() }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(CODES_TOOL_TITLE) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás") } },
            )
        },
    ) { padding ->
        val kind = pending
        if (kind != null) {
            Column(Modifier.padding(padding).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(CODES_UNLOCK_TO_SAVE, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { pending = null }) { Text(CODES_CANCEL_SAVE) }
                }
                VaultGate(store, onImport = actions.import, modifier = Modifier.weight(1f)) {
                    // Open now: write the note, then back to the screen with what was drawn still on it.
                    LaunchedEffect(Unit) {
                        saving = true
                        try {
                            save(kind)
                            pending = null
                        } finally {
                            saving = false
                        }
                    }
                    CircularProgressIndicator()
                }
            }
        } else {
            NoCopy {
                Column(
                    Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // ---- PIN ----
                    Text(CODES_PIN_TITLE, style = MaterialTheme.typography.titleMedium)
                    Text(
                        count?.let { pinCountText(it, pinBits(it)) } ?: CODES_PIN_COUNTING,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SectionCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(pin, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.headlineMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton(onClick = { pin = generatePin(digits) }) { Text(CODES_ANOTHER_PIN) }
                                OutlinedButton(onClick = { copySensitive(context, pin) }) { Text(CODES_COPY) }
                            }
                        }
                    }
                    Text(CODES_PIN_LENGTH, style = MaterialTheme.typography.labelMedium)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        (4..8).forEachIndexed { i, n ->
                            SegmentedButton(
                                selected = digits == n,
                                onClick = {
                                    digits = n
                                    count = null
                                    pin = generatePin(n)
                                },
                                shape = SegmentedButtonDefaults.itemShape(i, 5),
                            ) { Text("$n") }
                        }
                    }

                    HorizontalDivider()

                    // ---- Códigos de recuperación ----
                    Text(CODES_RECOVERY_TITLE, style = MaterialTheme.typography.titleMedium)
                    Text(RECOVERY_CODES_TEXT, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SectionCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            codes.forEach { Text(it, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.titleMedium) }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton(onClick = { codes = recoveryCodes() }) { Text(CODES_ANOTHER_CODES) }
                                OutlinedButton(onClick = { copySensitive(context, codes.joinToString("\n")) }) { Text(CODES_COPY_ALL) }
                            }
                        }
                    }
                    SiteField(recoverySite) { recoverySite = it }
                    Button(
                        enabled = !busy && recoverySite.isNotBlank(),
                        onClick = { requestSave(SaveKind.RECOVERY) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(CODES_SAVE_AS_NOTE) }

                    HorizontalDivider()

                    // ---- Respuestas inventadas ----
                    Text(ANSWERS_TITLE, style = MaterialTheme.typography.titleMedium)
                    Text(ANSWERS_EXPLANATION, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(ANSWERS_LIMIT, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(ANSWERS_WARNINGS, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(ANSWERS_NO_ACCENTS, modifier = Modifier.weight(1f))
                        Switch(
                            checked = plain,
                            onCheckedChange = { on ->
                                plain = on
                                // Accents cannot be put back on a plain answer: every answer is drawn again in the chosen form.
                                rows.forEach { it.answer = inventedAnswer(words, plain = on) }
                            },
                        )
                    }
                    Text(
                        ANSWERS_NO_ACCENTS_NOTE,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SiteField(answersSite) { answersSite = it }
                    rows.forEachIndexed { i, row ->
                        SectionCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(
                                    value = row.question,
                                    onValueChange = { row.question = it },
                                    label = { Text(CODES_QUESTION_LABEL) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Text(row.answer, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.titleMedium)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilledTonalButton(onClick = { rows[i].answer = inventedAnswer(words, plain) }) { Text(ANSWERS_OTHER) }
                                    OutlinedButton(onClick = { copySensitive(context, row.answer) }) { Text(CODES_COPY) }
                                    if (rows.size > 1) TextButton(onClick = { rows.removeAt(i) }) { Text(ANSWERS_REMOVE_QUESTION) }
                                }
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = { rows.add(AnswerRow("", inventedAnswer(words, plain))) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(ANSWERS_ADD_QUESTION) }
                    if (blankQuestion) {
                        Text(ANSWERS_BLANK_QUESTION, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(
                        enabled = !busy && answersSite.isNotBlank() && !blankQuestion,
                        onClick = { requestSave(SaveKind.ANSWERS) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(ANSWERS_SAVE) }
                }
            }
        }
    }
}

@Composable
private fun SiteField(value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(CODES_SITE_LABEL) },
        supportingText = if (value.isBlank()) ({ Text(CODES_SITE_NEEDED) }) else null,
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}
