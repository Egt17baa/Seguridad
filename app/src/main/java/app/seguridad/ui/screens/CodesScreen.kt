package app.seguridad.ui.screens

import android.content.res.AssetManager
import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.seguridad.android.copySensitive
import app.seguridad.tools.ANSWERS_ADD_QUESTION
import app.seguridad.tools.ANSWERS_BLANK_QUESTION
import app.seguridad.tools.ANSWERS_EXPLANATION
import app.seguridad.tools.ANSWERS_LIMIT
import app.seguridad.tools.ANSWERS_NO_ACCENTS
import app.seguridad.tools.ANSWERS_NO_ACCENTS_NOTE
import app.seguridad.tools.ANSWERS_OTHER
import app.seguridad.tools.ANSWERS_REMOVE_QUESTION
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
import app.seguridad.tools.codes.Cell
import app.seguridad.tools.codes.CellFactory
import app.seguridad.tools.codes.CodesSession
import app.seguridad.tools.codes.LockSeen
import app.seguridad.tools.codes.PendingSave
import app.seguridad.tools.codes.SaveKind
import app.seguridad.tools.codes.pinBits
import app.seguridad.tools.codes.strongPinCount
import app.seguridad.tools.pinCountText
import app.seguridad.ui.theme.NoCopy
import app.seguridad.ui.theme.SectionCard
import app.seguridad.vault.VaultState
import app.seguridad.vault.VaultStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * How many PINs are left per length: a constant of the rules (not a secret). One shared computation per length for the
 * whole run of the app: a second request (another visit, a quick change of length) waits for the first and reuses it.
 */
private val pinCountScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
private val pinCounts = ConcurrentHashMap<Int, Deferred<Long>>()

private fun pinCount(digits: Int): Deferred<Long> = pinCounts.computeIfAbsent(digits) { pinCountScope.async { strongPinCount(digits) } }

/** Compose state for a [CodesSession]: what the ViewModel holds redraws the screen when it changes. */
private object ComposeCells : CellFactory {
    override fun <T> cell(initial: T): Cell<T> = object : Cell<T> {
        private val state = mutableStateOf(initial)
        override var value: T
            get() = state.value
            set(v) {
                state.value = v
            }
    }

    override fun <T> list(): MutableList<T> = mutableStateListOf()
}

/**
 * Holds the screen's [CodesSession] for its navigation entry: a rotation, a theme change or a split screen keeps what
 * was drawn, the death of the process does not (never in saved state: a Bundle can go to disk). The BIP-39 list is read
 * here, off the main thread (M-10). A save runs in [viewModelScope], so a rotation does not cut it. When the vault locks
 * the session decides what goes ([CodesSession.vaultStateIs]); when the entry is gone for good, every secret goes.
 */
private class CodesViewModel(
    private val store: VaultStore,
    assets: AssetManager,
    private val lockAfterMs: () -> Long,
    awayInOtherApp: StateFlow<Boolean>,
) : ViewModel() {
    val session = CodesSession(ComposeCells, createdAt = SystemClock.elapsedRealtime())

    /** What the last save said, for the snackbar; null once shown. */
    var saveMessage by mutableStateOf<String?>(null)

    init {
        viewModelScope.launch {
            val words = withContext(Dispatchers.IO) {
                try {
                    assets.open("bip39_spanish.txt").bufferedReader(Charsets.UTF_8).use { it.readLines() }.filter { it.isNotBlank() }
                } catch (e: IOException) {
                    null // Never happens with the APK's own asset; the answers just stay off.
                }
            }
            words?.let(session::wordsLoaded)
        }
        // Collected for the entry's whole life (also while another tab is shown): why the vault locked and whether the app
        // was in use then are read from the store, as recorded at the lock itself.
        // COV2-3 (M3 of the F1 review): another app with the focus in split screen or a floating window (no ON_STOP comes)
        // is time away too, for the entry's whole life, like the lock below. The first value (false) does nothing.
        viewModelScope.launch {
            awayInOtherApp.collect { away ->
                if (away) session.otherAppFocused(SystemClock.elapsedRealtime())
                else session.otherAppLeft(SystemClock.elapsedRealtime(), lockAfterMs())
            }
        }
        viewModelScope.launch {
            store.state.collect {
                session.vaultStateIs(it is VaultState.Unlocked) {
                    LockSeen(store.lastLockReason, store.lastLockInForeground, SystemClock.elapsedRealtime(), lockAfterMs())
                }
            }
        }
    }

    fun requestSave(kind: SaveKind) {
        val unlocked = store.state.value is VaultState.Unlocked
        val save = session.requestSave(kind, unlocked, System.currentTimeMillis(), UUID.randomUUID().toString()) ?: return
        write(save)
    }

    /**
     * Writes [save]'s note. Idempotent: one write at a time, and the entry keeps its id, so a repeat replaces it. The gate,
     * if any, goes; whenever the note was written, what it holds leaves the screen ([CodesSession.endWrite]).
     */
    fun write(save: PendingSave) {
        if (!session.beginWrite(save)) return
        viewModelScope.launch {
            val saved = try {
                store.upsert(save.entry)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false // Nothing is logged: the note holds the secrets.
            }
            session.endWrite(save, saved)
            saveMessage = if (saved) CODES_SAVED else CODES_SAVE_FAILED
        }
    }

    override fun onCleared() {
        session.clear()
        saveMessage = null
    }
}

/**
 * «PIN y códigos» (`tools/codes`): a random PIN without the usual patterns, ten recovery codes and invented answers for
 * security questions. All three are drawn here from the app's secure random source and nothing leaves the phone.
 *
 * What is drawn is a secret: it lives in this entry's [CodesViewModel] (never in saved state, never in a log), and the
 * system text menu has no Copy ([NoCopy]); it leaves through «Copiar» ([copySensitive]) or into the vault as a note.
 * Saving goes through the existing [VaultGate] when the vault is locked, and the draw waits on this screen meanwhile.
 * I-1: back after more than the vault's lock time away ([lockAfterMs], at least a minute, measured on the monotonic
 * clock from ON_STOP, or from another app taking the focus in multi-window: [awayInOtherApp]), or once the vault locks
 * with the app in use (by hand; idle, if this screen was not used for that time either; any lock while in the other
 * app), the PIN, the codes and the answers are drawn again.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CodesScreen(store: VaultStore, lockAfterMs: () -> Long, awayInOtherApp: StateFlow<Boolean>, onBack: () -> Unit) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val actions = vaultActions(store, snackbar)
    // The Bóveda tab loads it too; this screen can be reached first.
    LaunchedEffect(store) { store.load() }

    val appAssets = context.applicationContext.assets
    val vm = viewModel { CodesViewModel(store, appAssets, lockAfterMs, awayInOtherApp) }
    val session = vm.session
    val currentLockAfter by rememberUpdatedState(lockAfterMs)

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { session.stopped(SystemClock.elapsedRealtime()) }
    // Also replayed when the screen is composed again (a rotation, back from another tab): a stop of the same trip is
    // used once, and a first visit has none.
    LifecycleEventEffect(Lifecycle.Event.ON_START) { session.started(SystemClock.elapsedRealtime(), currentLockAfter()) }
    // Another tab chosen: this composition goes without a stop of its own; the time away counts from here.
    DisposableEffect(session) { onDispose { session.stopped(SystemClock.elapsedRealtime()) } }

    var count by remember { mutableStateOf<Long?>(null) }
    // Off the main thread, once per length: 10^8 values at 8 digits take a few seconds.
    LaunchedEffect(session.digits) { count = pinCount(session.digits).await() }

    vm.saveMessage?.let { message ->
        LaunchedEffect(message) {
            vm.saveMessage = null
            snackbar.showSnackbar(message)
        }
    }

    val saving = session.saving
    val pending = session.pending
    val rows = session.rows
    val busy = saving || pending != null
    val blankQuestion = rows.any { it.question.isBlank() }

    Scaffold(
        // Any tap or key here is use of this screen, for the idle lock (it never touches the vault: no store.touch()).
        modifier = Modifier
            .pointerInput(session) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial)
                        session.used(SystemClock.elapsedRealtime())
                    }
                }
            }
            .onPreviewKeyEvent {
                session.used(SystemClock.elapsedRealtime())
                false
            },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(CODES_TOOL_TITLE) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás") } },
            )
        },
    ) { padding ->
        val waiting = pending
        if (waiting != null) {
            Column(Modifier.padding(padding).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(CODES_UNLOCK_TO_SAVE, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(enabled = !saving, onClick = session::cancelSave) { Text(CODES_CANCEL_SAVE) }
                }
                VaultGate(store, onImport = actions.import, modifier = Modifier.weight(1f)) {
                    // Open now: write the note queued, then back to the screen.
                    LaunchedEffect(waiting) { vm.write(waiting) }
                    CircularProgressIndicator()
                }
            }
        } else {
            NoCopy {
                Column(
                    // imePadding (M-9): with edge-to-edge the keyboard does not shrink the window, so the last questions
                    // would stay under it.
                    Modifier.padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
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
                            Text(session.pin, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.headlineMedium)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                FilledTonalButton(onClick = session::newPin) { Text(CODES_ANOTHER_PIN) }
                                OutlinedButton(onClick = { copySensitive(context, session.pin) }) { Text(CODES_COPY) }
                            }
                        }
                    }
                    Text(CODES_PIN_LENGTH, style = MaterialTheme.typography.labelMedium)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        (4..8).forEachIndexed { i, n ->
                            SegmentedButton(
                                selected = session.digits == n,
                                onClick = {
                                    // The effect keyed on the length computes the count; tapping the selected length only draws again.
                                    if (session.chooseDigits(n)) count = null
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
                            session.codes.forEach { Text(it, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.titleMedium) }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                FilledTonalButton(onClick = session::newCodes) { Text(CODES_ANOTHER_CODES) }
                                OutlinedButton(onClick = { copySensitive(context, session.codes.joinToString("\n")) }) { Text(CODES_COPY_ALL) }
                            }
                        }
                    }
                    SiteField(session.recoverySite) {
                        session.used(SystemClock.elapsedRealtime())
                        session.recoverySite = it
                    }
                    Button(
                        enabled = !busy && session.recoverySite.isNotBlank(),
                        onClick = { vm.requestSave(SaveKind.RECOVERY) },
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
                            checked = session.plain,
                            enabled = session.canDrawAnswers,
                            onCheckedChange = session::setPlainAnswers,
                        )
                    }
                    Text(
                        ANSWERS_NO_ACCENTS_NOTE,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SiteField(session.answersSite) {
                        session.used(SystemClock.elapsedRealtime())
                        session.answersSite = it
                    }
                    rows.forEachIndexed { i, row ->
                        SectionCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(
                                    value = row.question,
                                    onValueChange = {
                                        session.used(SystemClock.elapsedRealtime()) // The soft keyboard sends no key events.
                                        row.question = it
                                    },
                                    label = { Text(CODES_QUESTION_LABEL) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Text(row.answer, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.titleMedium)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    FilledTonalButton(enabled = session.canDrawAnswers, onClick = { session.newAnswer(i) }) { Text(ANSWERS_OTHER) }
                                    OutlinedButton(enabled = row.answer.isNotEmpty(), onClick = { copySensitive(context, row.answer) }) { Text(CODES_COPY) }
                                    if (rows.size > 1) TextButton(onClick = { session.removeQuestion(i) }) { Text(ANSWERS_REMOVE_QUESTION) }
                                }
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = session::addQuestion,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(ANSWERS_ADD_QUESTION) }
                    if (blankQuestion) {
                        Text(ANSWERS_BLANK_QUESTION, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(
                        enabled = !busy && session.answersSite.isNotBlank() && !blankQuestion && session.canDrawAnswers,
                        onClick = { vm.requestSave(SaveKind.ANSWERS) },
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
