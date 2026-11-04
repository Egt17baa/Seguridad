package app.seguridad.ui.screens

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.seguridad.core.readAtMost
import app.seguridad.tools.TOOLS_AWAY_WIPE_NOTE
import app.seguridad.ui.theme.NegativeColor
import app.seguridad.ui.theme.PasswordField
import app.seguridad.ui.theme.PositiveColor
import app.seguridad.ui.theme.SectionCard
import app.seguridad.vault.BACKUP_CHECK_BUSY
import app.seguridad.vault.BACKUP_CHECK_CANCEL
import app.seguridad.vault.BACKUP_CHECK_DIR
import app.seguridad.vault.BACKUP_CHECK_HAS_PROBLEMS
import app.seguridad.vault.BACKUP_CHECK_INTRO
import app.seguridad.vault.BACKUP_CHECK_NOT_PROVEN
import app.seguridad.vault.BACKUP_CHECK_NOT_PROVEN_TITLE
import app.seguridad.vault.BACKUP_CHECK_NOT_SGBAK
import app.seguridad.vault.BACKUP_CHECK_OK
import app.seguridad.vault.BACKUP_CHECK_PASSWORD
import app.seguridad.vault.BACKUP_CHECK_PICK
import app.seguridad.vault.BACKUP_CHECK_READ_FAILED
import app.seguridad.vault.BACKUP_CHECK_RESULT_TITLE
import app.seguridad.vault.BACKUP_CHECK_RUN
import app.seguridad.vault.BACKUP_CHECK_STOPS_NOTE
import app.seguridad.vault.BACKUP_CHECK_TITLE
import app.seguridad.vault.BACKUP_CHECK_TOO_LONG
import app.seguridad.vault.BACKUP_HEAD_BYTES
import app.seguridad.vault.BackupCheckReport
import app.seguridad.vault.BackupCheckRuns
import app.seguridad.vault.BackupCheckSession
import app.seguridad.vault.EntryType
import app.seguridad.vault.MIN_MASTER
import app.seguridad.vault.VaultLock
import app.seguridad.vault.VaultState
import app.seguridad.vault.VaultStore
import app.seguridad.vault.backupCheckAttachments
import app.seguridad.vault.backupCheckEntries
import app.seguridad.vault.backupCheckType
import app.seguridad.vault.backupCheckVersion
import app.seguridad.vault.checkBackup
import app.seguridad.vault.isBackup
import app.seguridad.vault.sanitizeReceivedName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * «Comprobar una copia» (Ajustes) for its navigation entry: the password typed, the copy chosen and the report are in
 * [BackupCheckSession], never in saved state (never `rememberSaveable` nor a Bundle). The session's guard empties them by
 * the «PIN y códigos» rule (lock of the vault, another app, time away) and [onCleared] drops them when the screen is
 * left. «Comprobar» takes the password as a CharArray and empties the field; [BackupCheckRuns] zeroes it when the check
 * ends or stops. A check runs [checkBackup] in its own folder of cacheDir/comprobar/ (deleted however it ends); it stops,
 * and its result is dropped, on a new check, «Cancelar», leaving the screen, leaving the app or a lock of the vault.
 * It writes nothing else: no vault, no settings, no copy. Choosing the file is as in «Importar copia»: the picker keeps
 * the vault open ([VaultLock.keepOpenOnce]) and `store.touch()` restarts its idle count, which is all the screen does
 * to the store.
 */
private class BackupCheckViewModel(
    private val appContext: Context,
    store: VaultStore,
    lockAfterMs: () -> Long,
    awayInOtherApp: StateFlow<Boolean>,
) : ViewModel() {
    val session: BackupCheckSession = BackupCheckSession(ComposeCells, SystemClock.elapsedRealtime()) { stopAll() }

    /** Declared after [session], whose wipe calls it: a wipe only comes once both exist. */
    private val runs: BackupCheckRuns = BackupCheckRuns(session, viewModelScope) { store.lockEpoch }

    /** Reading the chosen file's name and first bytes. */
    private var pickJob: Job? = null

    init {
        watchAway(session.guard, store, lockAfterMs, awayInOtherApp)
        // Any lock of the vault stops a check going on (the guard may keep the screen, by its rule; never a late result).
        viewModelScope.launch {
            var wasOpen = store.state.value is VaultState.Unlocked
            store.state.collect {
                val open = it is VaultState.Unlocked
                if (wasOpen && !open) runs.stop()
                wasOpen = open
            }
        }
    }

    private fun stopAll() {
        pickJob?.cancel()
        pickJob = null
        refused = false
        runs.stop()
    }

    /** A paste over [MAX_PASSWORD_CHARS] was refused (the field keeps what it had), and the screen says so. */
    var refused by mutableStateOf(false)
        private set

    fun typed(value: String) {
        session.guard.typed()
        refused = value.length > MAX_PASSWORD_CHARS
        if (!refused) session.password = value
    }

    /** A new copy: what was shown for the one before goes; its name and its first bytes are read on IO. */
    fun pick(uri: Uri) {
        stopAll()
        session.report = null
        session.error = null
        session.source = null
        session.fileName = null
        val token = session.token()
        pickJob = viewModelScope.launch {
            val name = withContext(Dispatchers.IO) { runCatching { displayName(appContext, uri) }.getOrDefault("archivo") }
            val head = withContext(Dispatchers.IO) {
                try {
                    appContext.contentResolver.openInputStream(uri)?.use { it.readAtMost(BACKUP_HEAD_BYTES) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
            }
            if (!session.isCurrent(token)) return@launch
            when {
                head == null -> session.error = BACKUP_CHECK_READ_FAILED
                // Said now, before the password: a photo or a note is not a copy (the check itself reads it all again).
                !isBackup(head) -> session.error = BACKUP_CHECK_NOT_SGBAK
                else -> {
                    session.source = uri
                    session.fileName = sanitizeReceivedName(name)
                }
            }
        }
    }

    val canCheck get() = session.source != null && session.password.length >= MIN_MASTER && !session.busy

    fun check() {
        val uri = session.source as? Uri ?: return
        if (!canCheck) return
        val master = session.password.toCharArray()
        session.password = "" // The field empties now; the run owns the CharArray and zeroes it.
        val dir = File(appContext.cacheDir, BACKUP_CHECK_DIR)
        runs.start(master) { pw ->
            withContext(Dispatchers.IO) {
                val input = appContext.contentResolver.openInputStream(uri) ?: throw IOException("no stream")
                input.use { checkBackup(it.buffered(), pw, dir) }
            }
        }
    }

    fun cancel() = runs.stop()

    /** The app went to the background (not a rotation): a check going on stops, as VaultStore.lockEpoch would drop it anyway. */
    fun leftApp() = runs.stop()

    override fun onCleared() {
        stopAll()
        session.wipe()
    }
}

/** Far longer than any master password; keeps a huge paste out of the field. */
private const val MAX_PASSWORD_CHARS = 1024

@Composable
fun BackupCheckScreen(store: VaultStore, lockAfterMs: () -> Long, awayInOtherApp: StateFlow<Boolean>, onBack: () -> Unit) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val activity = context.fragmentActivity()
    val vm = viewModel { BackupCheckViewModel(appContext, store, lockAfterMs, awayInOtherApp) }
    val s = vm.session
    GuardEffects(s.guard, lockAfterMs)
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { if (activity?.isChangingConfigurations != true) vm.leftApp() }
    var pickerError by remember { mutableStateOf<String?>(null) }
    val pick = rememberDocumentPicker(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.pick(uri)
    }
    ToolColumn(BACKUP_CHECK_TITLE, onBack, s.guard) {
        Text(BACKUP_CHECK_INTRO, color = MaterialTheme.colorScheme.onSurfaceVariant)
        // Every file: cloud providers label a .sgbak as they like; the first bytes are checked right after picking.
        OutlinedButton(
            enabled = !s.busy,
            onClick = {
                // As «Importar copia» (VaultBackup.kt): the picker keeps the vault open while it is on top, and the time
                // away counts from here (AWAY_BACKSTOP_MS with «Al salir»); MainActivity.onStart takes the flag back.
                VaultLock.keepOpenOnce = true
                store.touch()
                pickerError = pick.launchOrError(arrayOf("*/*"))
                if (pickerError != null) VaultLock.keepOpenOnce = false // The app never left: leaving later must lock.
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(s.fileName ?: BACKUP_CHECK_PICK) }
        pickerError?.let { Text(it, color = NegativeColor) }
        PasswordField(s.password, vm::typed, label = BACKUP_CHECK_PASSWORD)
        if (vm.refused) Text(BACKUP_CHECK_TOO_LONG, color = NegativeColor)
        FilledTonalButton(enabled = vm.canCheck, onClick = vm::check, modifier = Modifier.fillMaxWidth()) { Text(BACKUP_CHECK_RUN) }
        if (s.busy) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Text(BACKUP_CHECK_BUSY, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                TextButton(onClick = vm::cancel) { Text(BACKUP_CHECK_CANCEL) }
            }
        }
        s.error?.let { Text(it, color = NegativeColor, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
        s.report?.let { Report(it) }
        SectionCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(BACKUP_CHECK_NOT_PROVEN_TITLE, style = MaterialTheme.typography.labelMedium)
                Text(BACKUP_CHECK_NOT_PROVEN, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(BACKUP_CHECK_STOPS_NOTE, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(TOOLS_AWAY_WIPE_NOTE, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The report: counts and problems only, never an entry's content, title or attachment name. */
@Composable
private fun Report(r: BackupCheckReport) {
    SectionCard(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(BACKUP_CHECK_RESULT_TITLE, style = MaterialTheme.typography.labelMedium)
            if (r.problems.isEmpty()) {
                Text(BACKUP_CHECK_OK, color = PositiveColor)
            } else {
                Text(BACKUP_CHECK_HAS_PROBLEMS, color = NegativeColor)
                r.problems.forEach { Text(it, color = NegativeColor) }
            }
            Text(backupCheckVersion(r.version))
            Text(backupCheckEntries(r.entries.values.sum()))
            EntryType.entries.forEach { t -> r.entries[t]?.let { Text(backupCheckType(t, it), color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            Text(backupCheckAttachments(r.attachments, r.attachmentsOk))
        }
    }
}
