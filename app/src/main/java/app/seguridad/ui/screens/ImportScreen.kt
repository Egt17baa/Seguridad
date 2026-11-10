package app.seguridad.ui.screens

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.provider.DocumentsContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.seguridad.core.readAtMost
import app.seguridad.crypto.CryptoFailure
import app.seguridad.crypto.KdfLock
import app.seguridad.imports.Duplicate
import app.seguridad.imports.DuplicateChoice
import app.seguridad.imports.IMPORT_CANCEL
import app.seguridad.imports.IMPORT_CHOICE_KEEP_BOTH
import app.seguridad.imports.IMPORT_CHOICE_REPLACE
import app.seguridad.imports.IMPORT_CHOICE_SKIP
import app.seguridad.imports.IMPORT_CHOOSE_COPY_PLACE
import app.seguridad.imports.IMPORT_CHOOSE_COPY_PLACE_BUTTON
import app.seguridad.imports.IMPORT_CONFIRM
import app.seguridad.imports.IMPORT_CONFIRM_NOTE
import app.seguridad.imports.IMPORT_COPY_DOCUMENT_FAILED
import app.seguridad.imports.IMPORT_COPY_FAILED
import app.seguridad.imports.IMPORT_COPY_LOCKED
import app.seguridad.imports.IMPORT_COPY_VAULT_PROBLEM
import app.seguridad.imports.IMPORT_DONE_TITLE
import app.seguridad.imports.IMPORT_DUPLICATES_ALL
import app.seguridad.imports.IMPORT_ENDED_TITLE
import app.seguridad.imports.IMPORT_ESTIMATING
import app.seguridad.imports.IMPORT_FILE_TOO_BIG
import app.seguridad.imports.IMPORT_FORMATS
import app.seguridad.imports.IMPORT_GOOGLE_AUTHENTICATOR
import app.seguridad.imports.IMPORT_INTRO
import app.seguridad.imports.IMPORT_KDF_BUSY
import app.seguridad.imports.IMPORT_KDF_RUN
import app.seguridad.imports.IMPORT_MAX_BYTES
import app.seguridad.imports.IMPORT_NEW_TAGS_NONE
import app.seguridad.imports.IMPORT_NOTHING_TO_DO
import app.seguridad.imports.IMPORT_NOT_PROTECTED
import app.seguridad.imports.IMPORT_NO_TITLE
import app.seguridad.imports.IMPORT_PASSWORD_LABEL
import app.seguridad.imports.IMPORT_PASSWORD_OPEN
import app.seguridad.imports.IMPORT_PASSWORD_TOO_LONG
import app.seguridad.imports.IMPORT_PICK
import app.seguridad.imports.IMPORT_PICK_OTHER
import app.seguridad.imports.IMPORT_PREVIEW_TITLE
import app.seguridad.imports.IMPORT_READING
import app.seguridad.imports.IMPORT_READ_FAILED
import app.seguridad.imports.IMPORT_REMOVE
import app.seguridad.imports.IMPORT_REMOVED
import app.seguridad.imports.IMPORT_REMOVE_DECLINE
import app.seguridad.imports.IMPORT_REMOVE_DECLINED
import app.seguridad.imports.IMPORT_REMOVE_EXPLAIN
import app.seguridad.imports.IMPORT_REMOVE_FAILED
import app.seguridad.imports.IMPORT_REMOVE_UNSUPPORTED
import app.seguridad.imports.IMPORT_REMOVING
import app.seguridad.imports.IMPORT_REPLACE_NOTE
import app.seguridad.imports.IMPORT_SAVING
import app.seguridad.imports.IMPORT_SKIPPED_DONE_TITLE
import app.seguridad.imports.IMPORT_SKIPPED_TITLE
import app.seguridad.imports.IMPORT_TITLE
import app.seguridad.imports.IMPORT_TRADEMARKS
import app.seguridad.imports.IMPORT_WIPE_NOTE
import app.seguridad.imports.ImportFlow
import app.seguridad.imports.ImportSession
import app.seguridad.imports.ImportStep
import app.seguridad.imports.MergePlan
import app.seguridad.imports.PreviewEstimate
import app.seguridad.imports.RemoveResult
import app.seguridad.imports.RemoveState
import app.seguridad.imports.Skipped
import app.seguridad.imports.duplicateSubline
import app.seguridad.imports.importBecameNotesText
import app.seguridad.imports.importCopyFileName
import app.seguridad.imports.importCopyWhereText
import app.seguridad.imports.importDoneText
import app.seguridad.imports.importDuplicatesText
import app.seguridad.imports.importFileText
import app.seguridad.imports.importFoldersNotTagsText
import app.seguridad.imports.importNewTagsText
import app.seguridad.imports.importPasswordIntro
import app.seguridad.imports.importRepeatedText
import app.seguridad.imports.importReplacedText
import app.seguridad.imports.importSkippedLine
import app.seguridad.imports.importTooBigText
import app.seguridad.imports.plainReportText
import app.seguridad.imports.planMerge
import app.seguridad.imports.previewEntersText
import app.seguridad.imports.previewEstimate
import app.seguridad.imports.readImport
import app.seguridad.tools.SingleRun
import app.seguridad.ui.theme.BackScaffold
import app.seguridad.ui.theme.NegativeColor
import app.seguridad.ui.theme.NoCopy
import app.seguridad.ui.theme.PasswordField
import app.seguridad.ui.theme.PositiveColor
import app.seguridad.ui.theme.SectionCard
import app.seguridad.vault.CopyResult
import app.seguridad.vault.PASSWORD_FIELD_MAX_CHARS
import app.seguridad.vault.VaultLock
import app.seguridad.vault.VaultState
import app.seguridad.vault.VaultStore
import app.seguridad.vault.passwordFieldTakes
import app.seguridad.vault.sanitizeReceivedName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FilterInputStream
import java.io.InputStream
import java.time.LocalDateTime

/**
 * «Importar de otra app» (spec «Interfaz») for its navigation entry: what was read, the password typed, the plan and the
 * result live in [ImportSession], never in saved state (never `rememberSaveable` nor a Bundle; owner's rule for secrets).
 * Any lock of the vault and leaving the screen drop all of it ([ImportSession.vaultStateIs], [onCleared]); the session's
 * guard also applies the «PIN y códigos» rule (another app, time away), as in the other tools.
 *
 * The chosen document is opened once, read on IO with at most [IMPORT_MAX_BYTES] + 1 bytes and a stop at the next block
 * if the read is cancelled (owner's decisions (a) and (b)); from then on only those bytes are used, and they are zeroed
 * as soon as the reader is done. [readImport] runs on Default; an OutOfMemoryError is caught around that call only
 * (ruling R11b). A password attempt holds [IMPORT_KDF_RUN] and [KdfLock] until its derivation ends, and its CharArray
 * is zeroed however it ends. The plan comes from one snapshot of the vault (entries and revision together); «Importar» goes
 * through [ImportFlow] (the copy first, then the one save) and is not cut by leaving the screen; a lock before the save
 * starts cancels it and nothing is imported (the copy, if made, stays), while a save already started finishes (ruling
 * R37). What comes from the file is only ever shown as plain text through plainReportText: never a link, an intent or
 * HTML (owner's decision (c)).
 */
private class ImportViewModel(
    private val appContext: Context,
    private val store: VaultStore,
    private val copyBeforeImport: suspend () -> CopyResult?,
    lockAfterMs: () -> Long,
    awayInOtherApp: StateFlow<Boolean>,
) : ViewModel() {
    val session: ImportSession = ImportSession(ComposeCells, SystemClock.elapsedRealtime()) { stopAll() }

    /** Reading the file, trying its password, building the plan: one at a time, stopped by «Cancelar» and by any wipe. */
    private var job: Job? = null

    /** A paste over [PASSWORD_FIELD_MAX_CHARS] was refused (the field keeps what it had). */
    var refused by mutableStateOf(false)
        private set

    /** Another password was still being worked out when «Abrir» was tapped. */
    var kdfBusy by mutableStateOf(false)
        private set

    init {
        watchAway(session.guard, store, lockAfterMs, awayInOtherApp)
        viewModelScope.launch { store.state.collect { session.vaultStateIs(it is VaultState.Unlocked) } }
        // The preview's estimate, for the choices shown: a change of choice drops the one being made.
        viewModelScope.launch {
            snapshotFlow { EstimateInput(session.plan, session.default, session.overrides) }.collectLatest { input ->
                val plan = input.plan ?: return@collectLatest
                val default = input.default
                val overrides = input.overrides
                val existing = session.existing
                val estimate = try {
                    withContext(Dispatchers.Default) { previewEstimate(plan, default, overrides, existing, System.currentTimeMillis()) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // The save's exact check decides; the tags are unknown, so the preview claims nothing about them.
                    PreviewEstimate(0L, null, 0)
                }
                session.estimated(plan, default, overrides, estimate)
            }
        }
    }

    private fun stopAll() {
        job?.cancel()
        job = null
        refused = false
        kdfBusy = false
    }

    fun typed(value: String) {
        session.guard.typed()
        kdfBusy = false
        refused = !passwordFieldTakes(value)
        if (!refused) session.password = value
    }

    /** A document was chosen: read it once, then hand the bytes to the readers. */
    fun pick(uri: Uri) {
        stopAll()
        val run = session.begin(uri)
        job = viewModelScope.launch {
            val name = withContext(Dispatchers.IO) { runCatching { displayName(appContext, uri) }.getOrDefault("archivo") }
            if (!session.isRun(run)) return@launch
            session.fileName = sanitizeReceivedName(name)
            // Kept outside the IO block so that bytes read just before a cancellation are still zeroed.
            val box = arrayOfNulls<ByteArray>(1)
            val readOk = try {
                withContext(Dispatchers.IO) {
                    val reading = coroutineContext.job
                    box[0] = appContext.contentResolver.openInputStream(uri)?.use { raw ->
                        StopOnCancel(raw) { reading.ensureActive() }.readAtMost(IMPORT_MAX_BYTES + 1)
                    }
                }
                true
            } catch (e: CancellationException) {
                box[0]?.fill(0)
                throw e
            } catch (e: Exception) {
                false
            }
            val bytes = box[0]
            when {
                !readOk || bytes == null -> session.readFailed(run, IMPORT_READ_FAILED)
                bytes.size > IMPORT_MAX_BYTES -> {
                    bytes.fill(0)
                    session.readFailed(run, IMPORT_FILE_TOO_BIG)
                }
                session.hold(run, bytes) -> decode(run, null, null)
            }
        }
    }

    /** «Abrir» with the password typed: one attempt per tap (ruling R13), one derivation at a time in the app. */
    fun submitPassword() {
        val ticket = IMPORT_KDF_RUN.start()
        if (ticket == null) {
            kdfBusy = true
            return
        }
        val taken = session.takePassword()
        if (taken == null) {
            ticket.release()
            return
        }
        kdfBusy = false
        refused = false
        val (run, pw) = taken
        job = viewModelScope.launch { decode(run, pw, ticket) }
    }

    /**
     * The readers over the bytes held for [run], with [pw] if given; then the plan from one snapshot of the vault. [pw]
     * is zeroed and [ticket] released when the reader ends: a derivation cannot be cut, so a cancelled run still waits
     * for it here before it frees the next one. With [pw] the read may derive a key (Argon2id, scrypt or PBKDF2), so it
     * runs under [KdfLock] (ruling R38), one large derivation at a time in the app; without it nothing is derived and it
     * does not wait for an unlock. A result that comes after a cancel, a wipe or another file has an old [run] and is
     * dropped by [ImportSession.onRead]. Accepted cost (ruling R69, code-review 4): the whole read, not only the
     * derivation, holds [KdfLock]; it is bounded (16 MB, a 48 MiB tree), so an unlock that overlaps waits for one parse.
     */
    private suspend fun decode(run: Long, pw: CharArray?, ticket: SingleRun.Ticket?) {
        val result = try {
            val bytes = session.bytesFor(run) ?: return
            val read = {
                try {
                    readImport(bytes, pw)
                } catch (e: OutOfMemoryError) {
                    null
                }
            }
            if (pw == null) withContext(Dispatchers.Default) { read() } else KdfLock.derive(Dispatchers.Default, read)
        } finally {
            pw?.fill('\u0000')
            ticket?.release()
        }
        val read = session.onRead(run, result) ?: return
        val snap = store.importSnapshot()
        val plan = snap?.let { withContext(Dispatchers.Default) { planMerge(read, it.entries, it.revision) } }
        session.onPlan(run, plan, snap?.entries.orEmpty())
    }

    fun cancel() {
        stopAll()
        session.cancel()
    }

    fun chooseAll(choice: DuplicateChoice) = session.chooseAll(choice)

    fun choose(index: Int, choice: DuplicateChoice) = session.choose(index, choice)

    /** «Importar»: the copy into the automatic copies' folder (or, without one, the place is asked), then the save. */
    fun confirm() {
        val plan = session.plan ?: return
        val default = session.default
        val overrides = session.overrides
        val run = session.startSaving() ?: return
        // Ruling R66 (R37): a lock from here on cancels the import, even if the vault is opened again before the save.
        val since = store.lockEpoch
        viewModelScope.launch {
            val outcome = withContext(NonCancellable) {
                val auto = try {
                    copyBeforeImport()
                } catch (e: Exception) {
                    CopyResult.Failed(IMPORT_COPY_FAILED)
                }
                auto?.let { copied -> ImportFlow(copy = { copied }, apply = { store.applyImport(plan, default, overrides, sinceLock = since) }).confirm() }
            }
            if (outcome == null) session.needsCopyPlace(run) else session.onOutcome(run, outcome, inAutoFolder = true)
        }
    }

    /** The save dialog gave [uri] for the copy: written there, then the save; a document not asked for is deleted. */
    fun copyTo(uri: Uri) {
        val plan = session.plan
        val default = session.default
        val overrides = session.overrides
        val run = if (plan != null) session.copyPlaceChosen() else null
        if (plan == null || run == null) {
            viewModelScope.launch { discardDocumentNow(appContext, uri) }
            return
        }
        // Ruling R66: as in confirm, a lock from here on cancels the import.
        val since = store.lockEpoch
        viewModelScope.launch {
            val outcome = withContext(NonCancellable) {
                ImportFlow(copy = { copyToDocument(uri) }, apply = { store.applyImport(plan, default, overrides, sinceLock = since) }).confirm()
            }
            session.onOutcome(run, outcome, inAutoFolder = false)
        }
    }

    fun copyPlaceCancelled() = session.copyPlaceCancelled()

    /** The copy before importing into the document [uri] (as «Exportar copia cifrada»); deleted unless complete. */
    private suspend fun copyToDocument(uri: Uri): CopyResult {
        val failure = try {
            val written = withContext(Dispatchers.IO) { appContext.contentResolver.openOutputStream(uri, "wt")?.use { store.exportBackup(it) } }
            when (written) {
                true -> null
                false -> IMPORT_COPY_LOCKED
                null -> IMPORT_COPY_DOCUMENT_FAILED
            }
        } catch (e: CryptoFailure) {
            IMPORT_COPY_VAULT_PROBLEM
        } catch (e: Exception) {
            IMPORT_COPY_DOCUMENT_FAILED
        }
        if (failure != null) {
            discardDocumentNow(appContext, uri)
            return CopyResult.Failed(failure)
        }
        val name = withContext(Dispatchers.IO) { runCatching { displayName(appContext, uri) }.getOrDefault("") }
        return CopyResult.Saved(sanitizeReceivedName(name.ifBlank { importCopyFileName(LocalDateTime.now()) }))
    }

    /** «Quitar el archivo exportado»: the document picked in this run, once, after an import. */
    fun removeExported() {
        val uri = session.takeRemovable() as? Uri ?: return
        viewModelScope.launch {
            val result = withContext(NonCancellable + Dispatchers.IO) { deleteExported(appContext, uri) }
            session.onRemoved(result)
        }
    }

    fun declineRemoval() = session.declineRemoval()

    override fun onCleared() {
        session.wipe()
    }
}

/**
 * What the estimate depends on. The plan is compared by identity, as [ImportSession.estimated] does: a new plan equal
 * to the one before (the same file picked again) still gets its own estimate.
 */
private class EstimateInput(val plan: MergePlan?, val default: DuplicateChoice, val overrides: Map<Int, DuplicateChoice>) {
    override fun equals(other: Any?): Boolean =
        other is EstimateInput && other.plan === plan && other.default == default && other.overrides == overrides

    override fun hashCode(): Int = System.identityHashCode(plan) * 31 + default.hashCode() * 17 + overrides.hashCode()
}

/** The most read in one call, so a cancellation stops the read at the next block. */
private const val READ_BLOCK = 64 * 1024

/** [input], checking [check] (a cancellation) before each read; never more than [READ_BLOCK] bytes at once. */
private class StopOnCancel(input: InputStream, private val check: () -> Unit) : FilterInputStream(input) {
    override fun read(): Int {
        check()
        return super.read()
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        check()
        return super.read(b, off, minOf(len, READ_BLOCK))
    }
}

/**
 * Deletes the exported file [uri] (spec «Quitar el archivo exportado»): only a document whose provider says it can
 * delete it (FLAG_SUPPORTS_DELETE, when the flags can be read); never throws.
 */
private fun deleteExported(context: Context, uri: Uri): RemoveResult {
    val resolver = context.contentResolver
    val isDocument = runCatching { DocumentsContract.isDocumentUri(context, uri) }.getOrDefault(false)
    if (!isDocument) return RemoveResult.UNSUPPORTED
    val flags = runCatching {
        resolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_FLAGS), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getInt(0) else null
        }
    }.getOrNull()
    if (flags != null && flags and DocumentsContract.Document.FLAG_SUPPORTS_DELETE == 0) return RemoveResult.UNSUPPORTED
    return try {
        if (DocumentsContract.deleteDocument(resolver, uri)) RemoveResult.REMOVED else RemoveResult.FAILED
    } catch (e: Exception) {
        RemoveResult.FAILED
    }
}

@Composable
fun ImportScreen(
    store: VaultStore,
    copyBeforeImport: suspend () -> CopyResult?,
    lockAfterMs: () -> Long,
    awayInOtherApp: StateFlow<Boolean>,
    onBack: () -> Unit,
) {
    VaultIdleLock(store)
    val state by store.state.collectAsStateWithLifecycle()
    val appContext = LocalContext.current.applicationContext
    val vm = viewModel { ImportViewModel(appContext, store, copyBeforeImport, lockAfterMs, awayInOtherApp) }
    val s = vm.session
    GuardEffects(s.guard, lockAfterMs)
    // M-3: registered before the locked return, so a result delivered after the process died or while locked still
    // reaches them: a file picked then is not read, and a document created then is deleted (copyTo finds no plan).
    val pick = rememberDocumentPicker(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && store.state.value is VaultState.Unlocked) vm.pick(uri)
    }
    val createCopy = rememberDocumentPicker(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) vm.copyTo(uri) else vm.copyPlaceCancelled()
    }
    // Locked: the session has already dropped everything; the screen goes back to the vault, which asks for the master.
    if (state !is VaultState.Unlocked) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    var pickerError by remember { mutableStateOf<String?>(null) }
    // As «Importar copia» (VaultBackup.kt): the picker keeps the vault open while it is on top, and the time away counts
    // from here; MainActivity.onStart takes the flag back. Every file: the content says what it is (spec «Detección»).
    val launchPick: () -> Unit = {
        VaultLock.keepOpenOnce = true
        store.touch()
        pickerError = pick.launchOrError(arrayOf("*/*"))
        if (pickerError != null) VaultLock.keepOpenOnce = false
    }
    val launchCreate: () -> Unit = {
        VaultLock.keepOpenOnce = true
        store.touch()
        pickerError = createCopy.launchOrError(importCopyFileName(LocalDateTime.now()))
        if (pickerError != null) VaultLock.keepOpenOnce = false
    }
    BackScaffold(IMPORT_TITLE, onBack) { padding ->
        NoCopy {
            LazyColumn(
                Modifier.padding(padding).fillMaxSize().usedFor(s.guard).touchesVault(store),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val step = s.step
                s.fileName?.let { name ->
                    if (step != ImportStep.Pick) item { Text(importFileText(plainReportText(name)), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                when (step) {
                    ImportStep.Pick -> item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(IMPORT_INTRO)
                            Text(IMPORT_FORMATS, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(IMPORT_GOOGLE_AUTHENTICATOR, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            ErrorText(s.error)
                            Button(onClick = launchPick, modifier = Modifier.fillMaxWidth()) { Text(IMPORT_PICK) }
                        }
                    }
                    ImportStep.Reading -> item { Busy(IMPORT_READING, onCancel = vm::cancel) }
                    is ImportStep.Password -> item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(importPasswordIntro(step.format), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                            PasswordField(s.password, vm::typed, label = IMPORT_PASSWORD_LABEL)
                            // A wrong password (the only failure that keeps this step) is said here, and can be typed again.
                            ErrorText(s.error)
                            if (vm.refused) Text(IMPORT_PASSWORD_TOO_LONG, color = NegativeColor)
                            if (vm.kdfBusy) Text(IMPORT_KDF_BUSY, color = NegativeColor)
                            Button(enabled = s.password.isNotEmpty(), onClick = vm::submitPassword, modifier = Modifier.fillMaxWidth()) {
                                Text(IMPORT_PASSWORD_OPEN)
                            }
                            OutlinedButton(onClick = {
                                vm.cancel()
                                launchPick()
                            }, modifier = Modifier.fillMaxWidth()) { Text(IMPORT_PICK_OTHER) }
                        }
                    }
                    ImportStep.Preview -> preview(vm, launchPick)
                    ImportStep.Saving -> item { Busy(IMPORT_SAVING, onCancel = null) }
                    ImportStep.ChooseCopyPlace -> item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(IMPORT_CHOOSE_COPY_PLACE, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                            Button(onClick = launchCreate, modifier = Modifier.fillMaxWidth()) { Text(IMPORT_CHOOSE_COPY_PLACE_BUTTON) }
                            OutlinedButton(onClick = vm::copyPlaceCancelled, modifier = Modifier.fillMaxWidth()) { Text(IMPORT_CANCEL) }
                        }
                    }
                    is ImportStep.Done -> done(vm, step, launchPick)
                    is ImportStep.Ended -> item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Heading(IMPORT_ENDED_TITLE)
                            Text(step.message, color = NegativeColor)
                            OutlinedButton(onClick = launchPick, modifier = Modifier.fillMaxWidth()) { Text(IMPORT_PICK_OTHER) }
                        }
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        pickerError?.let { Text(it, color = NegativeColor) }
                        HorizontalDivider()
                        Text(IMPORT_NOT_PROTECTED, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(IMPORT_WIPE_NOTE, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(IMPORT_TRADEMARKS, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** Step 3: what enters, the confirmation, then the duplicates (each with its choice) and what does not enter. */
private fun LazyListScope.preview(vm: ImportViewModel, launchPick: () -> Unit) {
    val s = vm.session
    val plan = s.plan ?: return
    val counts = s.counts ?: return
    item {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Heading(IMPORT_PREVIEW_TITLE)
            Text(previewEntersText(counts.added))
            if (counts.replaced > 0) Text(importReplacedText(counts.replaced))
            if (plan.repeatedInFile > 0) Text(importRepeatedText(plan.repeatedInFile), color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (plan.becameNotes > 0) Text(importBecameNotesText(plan.becameNotes), color = MaterialTheme.colorScheme.onSurfaceVariant)
            // From the background estimate (M-1), at most IMPORT_TAGS_SHOWN by name (R18); null while it is made.
            s.newTags?.let { tags ->
                Text(if (tags.isEmpty()) IMPORT_NEW_TAGS_NONE else importNewTagsText(tags.map { plainReportText(it) }), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (s.foldersNotTags > 0) Text(importFoldersNotTagsText(s.foldersNotTags), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    item {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val over = s.overBytes
            when {
                over == null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(IMPORT_ESTIMATING, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                over > 0 -> Text(importTooBigText(over, 0), color = NegativeColor)
                counts.addedTotal == 0 && counts.replaced == 0 -> Text(IMPORT_NOTHING_TO_DO, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ErrorText(s.error)
            Text(IMPORT_CONFIRM_NOTE, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(enabled = s.canConfirm, onClick = vm::confirm, modifier = Modifier.fillMaxWidth()) { Text(IMPORT_CONFIRM) }
            OutlinedButton(onClick = launchPick, modifier = Modifier.fillMaxWidth()) { Text(IMPORT_PICK_OTHER) }
        }
    }
    if (plan.duplicates.isNotEmpty()) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HorizontalDivider()
                Heading(importDuplicatesText(plan.duplicates.size))
                Text(IMPORT_DUPLICATES_ALL)
                Choices(s.default, vm::chooseAll)
                Text(IMPORT_REPLACE_NOTE, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(plan.duplicates, key = { "d${it.item.index}" }) { d -> DuplicateRow(d, s.choiceOf(d.item.index)) { vm.choose(d.item.index, it) } }
    }
    skippedList(plan.skipped, IMPORT_SKIPPED_TITLE)
}

/** Step 5: what entered, where the copy is, what did not enter, and the offer to remove the exported file. */
private fun LazyListScope.done(vm: ImportViewModel, step: ImportStep.Done, launchPick: () -> Unit) {
    val s = vm.session
    item {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Heading(IMPORT_DONE_TITLE)
            Text(importDoneText(step.added, step.replaced))
            Text(importCopyWhereText(plainReportText(step.where), step.inAutoFolder), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    item {
        SectionCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp).semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (s.removal) {
                    RemoveState.OFFERED -> {
                        Text(IMPORT_REMOVE_EXPLAIN)
                        Button(onClick = vm::removeExported, modifier = Modifier.fillMaxWidth()) { Text(IMPORT_REMOVE) }
                        TextButton(onClick = vm::declineRemoval) { Text(IMPORT_REMOVE_DECLINE) }
                    }
                    RemoveState.REMOVING -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(IMPORT_REMOVING)
                    }
                    RemoveState.REMOVED -> Text(IMPORT_REMOVED, color = PositiveColor)
                    RemoveState.FAILED -> Text(IMPORT_REMOVE_FAILED, color = NegativeColor)
                    RemoveState.UNSUPPORTED -> Text(IMPORT_REMOVE_UNSUPPORTED, color = NegativeColor)
                    RemoveState.DECLINED -> Text(IMPORT_REMOVE_DECLINED)
                    null -> Unit
                }
            }
        }
    }
    skippedList(s.skipped, IMPORT_SKIPPED_DONE_TITLE)
    item { OutlinedButton(onClick = launchPick, modifier = Modifier.fillMaxWidth()) { Text(IMPORT_PICK_OTHER) } }
}

/** «No entran»: each element's plain title (never a password or a note) and its reason. */
private fun LazyListScope.skippedList(skipped: List<Skipped>, title: String) {
    if (skipped.isEmpty()) return
    item {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HorizontalDivider()
            Heading("$title (${skipped.size})")
        }
    }
    items(skipped, key = { "s${it.index}" }) { k ->
        Text(importSkippedLine(plainReportText(k.title).ifBlank { IMPORT_NO_TITLE }, k.reason.text), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * One duplicate: its title, then its user and the host of its web (with the scheme when it is not https, never the user
 * info), or its service and account ([duplicateSubline], plain text: the web is the one «Reemplazar» brings in), and its
 * own choice.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DuplicateRow(d: Duplicate, choice: DuplicateChoice, onChoose: (DuplicateChoice) -> Unit) {
    val e = d.item.entry
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("${typeIcon(e.type)} ${plainReportText(e.title).ifBlank { IMPORT_NO_TITLE }}")
        val sub = duplicateSubline(e)
        if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        // Ruling R71: a duplicate that «Reemplazar» cannot change is offered «Saltar» and «Conservar los dos» only.
        Choices(choice, onChoose, replace = d.replaceable)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Choices(selected: DuplicateChoice, onChoose: (DuplicateChoice) -> Unit, replace: Boolean = true) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((choice, label) in listOfNotNull(
            DuplicateChoice.SKIP to IMPORT_CHOICE_SKIP,
            DuplicateChoice.KEEP_BOTH to IMPORT_CHOICE_KEEP_BOTH,
            (DuplicateChoice.REPLACE to IMPORT_CHOICE_REPLACE).takeIf { replace },
        )) {
            FilterChip(selected = selected == choice, onClick = { onChoose(choice) }, label = { Text(label) })
        }
    }
}

/** A step's title, read by TalkBack when the step changes. */
@Composable
private fun Heading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.semantics {
            heading()
            liveRegion = LiveRegionMode.Polite
        },
    )
}

@Composable
private fun ErrorText(error: String?) {
    error?.let { Text(it, color = NegativeColor, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
}

@Composable
private fun Busy(text: String, onCancel: (() -> Unit)?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        onCancel?.let { TextButton(onClick = it) { Text(IMPORT_CANCEL) } }
    }
}
