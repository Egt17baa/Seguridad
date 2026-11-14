package app.seguridad.ui.screens

import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import app.seguridad.ui.theme.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import app.seguridad.ui.theme.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.seguridad.BuildConfig
import app.seguridad.android.BiometricVault
import app.seguridad.android.FilterObscuredTouches
import app.seguridad.android.SafBackupTarget
import app.seguridad.settings.AUTO_BACKUP_KEEP_CHOICES
import app.seguridad.settings.AUTO_BACKUP_OFF_NEW_VAULT
import app.seguridad.settings.ClipboardClear
import app.seguridad.settings.KdfProfile
import app.seguridad.settings.LockAfter
import app.seguridad.settings.Settings
import app.seguridad.settings.SettingsStore
import app.seguridad.settings.availableOn
import app.seguridad.settings.settingsEditable
import app.seguridad.settings.vault
import app.seguridad.settings.withVault
import app.seguridad.settings.TargetRefusal
import app.seguridad.settings.VaultSettings
import app.seguridad.settings.canAddTarget
import app.seguridad.settings.target
import app.seguridad.settings.withTarget
import app.seguridad.settings.withTargetAdded
import app.seguridad.settings.withTargetFolderChanged
import app.seguridad.tools.ABOUT_SUMMARY
import app.seguridad.tools.ABOUT_WARNINGS_SHORT
import app.seguridad.vault.AUTO_BACKUP_ADD_FOLDER
import app.seguridad.vault.AUTO_BACKUP_ALREADY_TARGET
import app.seguridad.tools.AUTO_BACKUP_DELETES_SHORT
import app.seguridad.vault.AUTO_BACKUP_MORE
import app.seguridad.vault.AUTO_BACKUP_NOW_NEEDS_OPEN
import app.seguridad.vault.AUTO_BACKUP_REMOVED
import app.seguridad.vault.AUTO_BACKUP_SCHEDULE_ON_OPEN
import app.seguridad.vault.AUTO_BACKUP_TOO_MANY
import app.seguridad.vault.autoBackupTargetTitle
import app.seguridad.vault.scheduleAfter
import app.seguridad.vault.scheduleLabel
import app.seguridad.tools.AUTO_BACKUP_OUTCOME_TITLE
import app.seguridad.tools.AUTO_BACKUP_REPLACE_DROPPED
import app.seguridad.tools.AUTO_BACKUP_SHORT
import app.seguridad.tools.CLIPBOARD_CLEAR_HINT
import app.seguridad.tools.CLIPBOARD_CLEAR_SHORT
import app.seguridad.tools.KDF_HIGH_SHORT
import app.seguridad.tools.KDF_HIGH_UNAVAILABLE
import app.seguridad.tools.KEY_LOCATION_TITLE
import app.seguridad.tools.KeyLevel
import app.seguridad.tools.LOCK_ON_EXIT_HINT
import app.seguridad.tools.LOCK_ON_EXIT_SHORT
import app.seguridad.tools.PASSWORD_CHANGED_FINGERPRINT_FAILED
import app.seguridad.tools.PASSWORD_CHANGED_TITLE
import app.seguridad.tools.PASSWORD_CHANGE_CLOSE
import app.seguridad.tools.PASSWORD_CHANGE_COPIES
import app.seguridad.tools.PASSWORD_CHANGE_OLD_BACKUPS
import app.seguridad.tools.PASSWORD_CHANGE_RENEW_HINT
import app.seguridad.tools.PASSWORD_CHANGE_REPLACE_COPIES
import app.seguridad.tools.PASSWORD_CHANGE_SECRETS_HINT
import app.seguridad.tools.PASSWORD_CHANGE_WORKING
import app.seguridad.tools.RENEW_IDENTITY
import app.seguridad.tools.SETTINGS_NEED_OPEN_VAULT
import app.seguridad.tools.STRONGBOX_AVAILABLE_NOTICE
import app.seguridad.tools.keyLevelLabel
import app.seguridad.tools.shouldOfferStrongBoxHint
import app.seguridad.ui.theme.BackScaffold
import app.seguridad.ui.theme.NegativeColor
import app.seguridad.ui.theme.SectionCard
import app.seguridad.vault.AUTO_BACKUP_REPLACE_WAITS
import app.seguridad.vault.AutoBackup
import app.seguridad.vault.BACKUP_CHECK_TITLE
import app.seguridad.vault.BackupSchedule
import app.seguridad.vault.VaultLimits
import app.seguridad.vault.MasterChanged
import app.seguridad.vault.ReplaceStatus
import app.seguridad.vault.VaultLock
import app.seguridad.vault.VaultState
import app.seguridad.vault.VaultStore
import app.seguridad.vault.replaceStatus
import app.seguridad.vault.A6Summary
import app.seguridad.vault.a6DialogView
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Every failed save of a setting, in Ajustes and in «Aspecto» (review M-3 of task 5 bis: the same text). */
internal const val SETTING_SAVE_FAILED = "No se pudo guardar el ajuste"

@Composable
internal fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    SectionCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            content()
        }
    }
}

@Composable
internal fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** One option of a single-choice list; the whole row is the touch target. */
@Composable
private fun Choice(label: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick),
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Text(
            label,
            modifier = Modifier.padding(start = 8.dp, top = 10.dp, bottom = 10.dp),
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun <T> Choices(options: List<T>, selected: T, label: (T) -> String, enabled: (T) -> Boolean = { true }, onSelect: (T) -> Unit) {
    Column(Modifier.selectableGroup()) {
        options.forEach { o -> Choice(label(o), o == selected, enabled(o)) { onSelect(o) } }
    }
}

private val backupDate: DateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(Locale.forLanguageTag("es-ES"))

/**
 * «Copia automática» (2.0, task 8): up to [VaultLimits.BACKUP_TARGETS_MAX] folders, each chosen with OpenDocumentTree (its
 * access kept with a persistable permission, given back when removed or replaced unless another target uses it), with its
 * calendar, how many copies it keeps and how its last copy went; and the line that the copies with a calendar are only
 * made when the app is opened (Abogado Digital, O-4: in plain view).
 */
@Composable
private fun AutoBackupSection(
    store: SettingsStore,
    vault: VaultStore,
    autoBackup: AutoBackup,
    releaseIfUnused: (String) -> Unit,
    snackbar: SnackbarHostState,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val all by store.settings.collectAsStateWithLifecycle()
    // 2.0, task 2: the copies of the vault shown, by its local id.
    val id = vault.id
    val settings = all.vault(id)
    val vaultState by vault.state.collectAsStateWithLifecycle()
    // Copies are only made from the open vault (each attachment is checked): while locked, one waits for it.
    val locked = vaultState is VaultState.Locked
    val noVault = vaultState is VaultState.NoVault
    // COV2-1: where the copies go and how many are kept change only with the vault open, or with none.
    val editable = settingsEditable(vaultState)
    // The target whose folder the picker changes; null when it adds one. Only a folder's URI (saved in plain text in the
    // settings anyway), never a secret: kept across a re-creation of the activity while the picker is open.
    var changing by rememberSaveable { mutableStateOf<String?>(null) }

    val picker = rememberDocumentPicker(ActivityResultContracts.OpenDocumentTree(), place = null) { uri ->
        VaultLock.keepOpenOnce = false // Back in the app, whatever the outcome: leaving later must lock.
        val from = changing
        changing = null
        if (uri != null) scope.launch {
            // Checked again on return: the vault may have locked while the picker was open (COV2-1). No permission taken.
            if (!settingsEditable(vault.state.value)) {
                snackbar.showSnackbar(SETTINGS_NEED_OPEN_VAULT)
                return@launch
            }
            val chosen = uri.toString()
            val now = store.settings.value.vault(id)
            // Refused before taking a permission: the same folder twice, or a fourth one.
            if (from == null || from != chosen) {
                when (now.canAddTarget(chosen)) {
                    TargetRefusal.ALREADY_A_TARGET -> {
                        snackbar.showSnackbar(AUTO_BACKUP_ALREADY_TARGET)
                        return@launch
                    }
                    TargetRefusal.TOO_MANY -> if (from == null) {
                        snackbar.showSnackbar(AUTO_BACKUP_TOO_MANY)
                        return@launch
                    }
                    null -> Unit
                }
            }
            val resolver = context.contentResolver
            if (!withContext(Dispatchers.IO) { SafBackupTarget.takeFolder(resolver, uri) }) {
                snackbar.showSnackbar("No se pudo obtener permiso para usar esa carpeta. Elige otra.")
                return@launch
            }
            val hadRequest = try {
                // The lists of a folder go with it ([VaultSettings.pastTargets]): the same one chosen again goes on rotating
                // them (and keeps a request to replace them), another one starts with what that folder had. Round 6 (m-3):
                // whether a waiting request moved away is decided by the write itself.
                if (from == null) {
                    store.updateVault(id) { it.withTargetAdded(chosen, BackupSchedule.ON_CHANGE) }
                    false
                } else {
                    store.changeTargetFolder(id, from, chosen)
                }
            } catch (e: Exception) {
                if (chosen != from) releaseIfUnused(chosen)
                snackbar.showSnackbar(SETTING_SAVE_FAILED)
                return@launch
            }
            if (from != null && from != chosen) releaseIfUnused(from)
            autoBackup.request(chosen)
            // Round 4: a folder changed while its A6 request waited: the summary of the request is told if it changed.
            if (hadRequest) autoBackup.retell()
            // A request to replace the old copies that the new folder dropped is said, not lost in silence.
            // Round 5: another folder takes the place of one whose request waited (that request stays with its folder's lists).
            val dropped = if (hadRequest) " $AUTO_BACKUP_REPLACE_DROPPED" else ""
            val schedule = store.settings.value.vault(id).target(chosen)?.schedule ?: BackupSchedule.ON_CHANGE
            snackbar.showSnackbar(
                when (vault.state.value) {
                    // Locked only if it locked in the instant since the check above: the copy still waits for it.
                    VaultState.Locked -> "Copia automática activada: se hará una en cuanto abras la bóveda y después ${scheduleAfter(schedule)}."
                    VaultState.NoVault -> "Copia automática activada: se hará una cuando crees la bóveda y después ${scheduleAfter(schedule)}."
                    else -> "Copia automática activada: se hará una ahora y después ${scheduleAfter(schedule)}."
                } + dropped,
            )
        }
    }
    val choose: (String?) -> Unit = choose@{ from ->
        if (!settingsEditable(vault.state.value)) return@choose
        changing = from
        VaultLock.keepOpenOnce = true
        vault.touch() // The time away is counted from here (AWAY_BACKSTOP_MS with «Al salir»).
        picker.launchOrError(folderStart(from ?: settings.targets.lastOrNull()?.uri))?.let { failure ->
            VaultLock.keepOpenOnce = false // The app never left: leaving later must lock.
            changing = null
            scope.launch { snackbar.showSnackbar(failure) }
        }
    }
    val save: ((VaultSettings) -> VaultSettings) -> Unit = { change ->
        if (settingsEditable(vault.state.value)) scope.launch {
            try {
                store.updateVault(id, change)
            } catch (e: Exception) {
                snackbar.showSnackbar(SETTING_SAVE_FAILED)
            }
        }
    }

    Section("Copia automática") {
        Hint(AUTO_BACKUP_SHORT)
        Hint(AUTO_BACKUP_DELETES_SHORT)
        // O-4 of the Abogado Digital: in plain view, not under «Más información».
        Hint(AUTO_BACKUP_SCHEDULE_ON_OPEN)
        MoreInfo {
            Hint(AUTO_BACKUP_MORE)
            Hint(
                "Cada copia va cifrada con la contraseña maestra que tenía la bóveda al hacerla: si la cambias, las copias anteriores " +
                    "se siguen abriendo con la antigua. Solo se borran las copias automáticas más antiguas (seguridad-auto-…) y, si " +
                    "lo pides tras cambiar la contraseña maestra, las de antes de importar (seguridad-antes-de-importar-…) y la de " +
                    "antes de la 2.0 (seguridad-antes-de-2-0-…); nunca otros archivos de la carpeta. Cada carpeta solo borra las " +
                    "copias que la app hizo en ella.",
            )
        }
        if (settings.autoBackupOffForNewVault && settings.targets.isEmpty()) {
            Text(AUTO_BACKUP_OFF_NEW_VAULT, style = MaterialTheme.typography.bodySmall, color = NegativeColor)
        }
        if (settings.targets.size < VaultLimits.BACKUP_TARGETS_MAX) {
            OutlinedButton(onClick = { choose(null) }, enabled = editable, modifier = Modifier.fillMaxWidth()) {
                Text(if (settings.targets.isEmpty()) "Elegir carpeta" else AUTO_BACKUP_ADD_FOLDER)
            }
        }
        if (!editable) Hint(SETTINGS_NEED_OPEN_VAULT)
    }
    settings.targets.forEachIndexed { index, t ->
        Section(autoBackupTargetTitle(index + 1)) {
            Text("Carpeta: ${SafBackupTarget.label(t.uri)}", style = MaterialTheme.typography.bodyMedium)
            Choices(BackupSchedule.entries, t.schedule, ::scheduleLabel, enabled = { editable }) { chosen ->
                save { it.withTarget(t.uri) { target -> target.copy(schedule = chosen) } }
            }
            Choices(AUTO_BACKUP_KEEP_CHOICES, t.keep, { "Conservar las $it últimas" }, enabled = { editable }) { k ->
                save { it.withTarget(t.uri) { target -> target.copy(keep = k) } }
            }
            val last = t.lastAt
            Hint(
                if (last == null) {
                    "Aún no se ha hecho ninguna copia: se hará " + when {
                        noVault -> "cuando crees la bóveda."
                        locked -> "en cuanto abras la bóveda."
                        t.schedule == BackupSchedule.ON_CHANGE -> "en unos segundos y tras cada cambio."
                        else -> "en unos segundos."
                    }
                } else {
                    "Última copia: " + backupDate.format(Instant.ofEpochMilli(last).atZone(ZoneId.systemDefault()))
                },
            )
            t.lastError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = NegativeColor) }
            OutlinedButton(
                onClick = {
                    // Round 4 (i-3): only with the vault open. A request made while locked would live in memory only, so
                    // nothing promises it: the text asks to open the vault and press again.
                    when (vault.state.value) {
                        is VaultState.Unlocked -> autoBackup.request(t.uri)
                        VaultState.Locked -> scope.launch { snackbar.showSnackbar(AUTO_BACKUP_NOW_NEEDS_OPEN) }
                        VaultState.NoVault -> scope.launch { snackbar.showSnackbar("Aún no tienes bóveda: no hay nada que copiar.") }
                        else -> Unit
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Hacer una copia ahora") }
            OutlinedButton(onClick = { choose(t.uri) }, enabled = editable, modifier = Modifier.fillMaxWidth()) { Text("Cambiar de carpeta") }
            OutlinedButton(
                enabled = editable,
                onClick = {
                    if (settingsEditable(vault.state.value)) scope.launch {
                        try {
                            // From the write itself (T11-c): a replacement that finished just before is not «dropped».
                            val hadRequest = store.removeTarget(id, t.uri)
                            // Round 4: removing a folder whose A6 request was waiting may end the request: its summary is told.
                            if (hadRequest) autoBackup.retell()
                            releaseIfUnused(t.uri)
                            snackbar.showSnackbar(AUTO_BACKUP_REMOVED + if (hadRequest) " $AUTO_BACKUP_REPLACE_DROPPED" else "")
                        } catch (e: Exception) {
                            snackbar.showSnackbar(SETTING_SAVE_FAILED)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (settings.targets.size == 1) "Desactivar" else "Quitar esta carpeta") }
        }
    }
}

/**
 * What follows a change of master password (the vault is locked by then): the copies that already exist do not change,
 * the automatic ones can be replaced ([onReplace], only while [autoBackupOn], which is read again at each recomposition),
 * and the identity can be renewed ([onRenew] leads to «Mi identidad»). Nothing of it lives in the dialog, which a
 * rotation recreates: [state] is AppContainer.masterChanged (whether it was asked, and what the call answered, recorded
 * even if the activity was re-created meanwhile), [replacing] is AutoBackup working in its own scope, and [pending],
 * [lastError] and [lastBackupAt] are the saved settings. It is offered again only after a failure. The dialog cannot be
 * dismissed while it works.
 */
@Composable
fun MasterChangedDialog(
    state: MasterChanged.State,
    autoBackupOn: Boolean,
    /**
     * Task 8, round 4: the A6 request of now as the targets saved it ([a6Summary]); its text is [a6Text]'s, the same as the
     * dialog after an unlock, never one written here.
     */
    summary: A6Summary? = null,
    replacing: Boolean,
    pending: Boolean,
    lastError: String?,
    lastBackupAt: Long?,
    /** m3: a failure of this request was already told (the targets' replaceTold is not empty). */
    toldSinceRequest: Boolean,
    onReplace: () -> Unit,
    onRenew: () -> Unit,
    onClose: () -> Unit,
) {
    val working = state.busy || replacing
    val status = replaceStatus(state.requestedAt, state.answered, state.answerError, pending, lastError, lastBackupAt, toldSinceRequest)
    // Rounds 4 to 6: what it shows of its own request (m-1: never another's) is decided in one pure function (a6DialogView).
    val view = a6DialogView(status, working, summary?.takeIf { it.request == state.answerRequest })
    val canReplace = view.canReplace
    AlertDialog(
        onDismissRequest = { if (!working) onClose() },
        title = { Text(PASSWORD_CHANGED_TITLE) },
        text = {
            FilterObscuredTouches()
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Also said in a snackbar, which a rotation can lose: here it stays until the dialog is closed.
                if (state.fingerprintFailed) Text(PASSWORD_CHANGED_FINGERPRINT_FAILED, color = NegativeColor)
                Text(PASSWORD_CHANGE_COPIES)
                Text(PASSWORD_CHANGE_OLD_BACKUPS)
                if (autoBackupOn) {
                    OutlinedButton(
                        enabled = canReplace,
                        onClick = { if (canReplace) onReplace() },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(PASSWORD_CHANGE_REPLACE_COPIES) }
                    when {
                        working -> Hint(PASSWORD_CHANGE_WORKING)
                        view.text == null -> Unit
                        view.negative -> Text(view.text, style = MaterialTheme.typography.bodySmall, color = NegativeColor)
                        else -> Hint(view.text)
                    }
                }
                // R-5 of the earlier dictamen: «Renovar identidad» only when the vault had one; the advice on passwords stays.
                if (state.hasIdentity) {
                    Text(PASSWORD_CHANGE_RENEW_HINT)
                    OutlinedButton(enabled = !working, onClick = onRenew, modifier = Modifier.fillMaxWidth()) { Text(RENEW_IDENTITY) }
                } else {
                    Text(PASSWORD_CHANGE_SECRETS_HINT)
                }
            }
        },
        confirmButton = { TextButton(enabled = !working, onClick = onClose) { Text(PASSWORD_CHANGE_CLOSE) } },
    )
}

/** How a replacement that ran after an unlock went (it was asked for while the vault was locked), told once. */
@Composable
fun ReplaceOutcomeDialog(text: String, failed: Boolean, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(AUTO_BACKUP_OUTCOME_TITLE) },
        text = {
            FilterObscuredTouches()
            Text(text, color = if (failed) NegativeColor else MaterialTheme.colorScheme.onSurface)
        },
        confirmButton = { TextButton(onClick = onClose) { Text(PASSWORD_CHANGE_CLOSE) } },
    )
}

/**
 * «Dónde está la clave de la huella», only while the fingerprint is on. Read from the Keystore off the main thread, on
 * each visit and whenever the vault changes state (a new master password or an import turns the fingerprint off).
 */
@Composable
private fun FingerprintKeySection(vaultId: String, vaultState: VaultState) {
    val context = LocalContext.current
    val level by produceState<KeyLevel?>(null, vaultId, vaultState) {
        value = withContext(Dispatchers.IO) { if (BiometricVault.enabled(context, vaultId)) BiometricVault.keyLevel(context, vaultId) else null }
    }
    // R2: decided once per visit to Ajustes (a lock or unlock does not take it away), from the key as it was then.
    val offerHint by produceState(false, vaultId) {
        value = withContext(Dispatchers.IO) {
            BiometricVault.enabled(context, vaultId) && shouldOfferStrongBoxHint(
                hasStrongBox = BiometricVault.hasStrongBox(context),
                current = BiometricVault.keyLevel(context, vaultId),
                strongBoxFailedBefore = BiometricVault.strongBoxFailed(context),
                hintShown = BiometricVault.strongBoxHintShown(context),
            )
        }
    }
    val current = level ?: return
    Section(KEY_LOCATION_TITLE) {
        Text(keyLevelLabel(current), style = MaterialTheme.typography.bodyMedium)
        if (offerHint) {
            Hint(STRONGBOX_AVAILABLE_NOTICE)
            // Saved only once it has been put on screen, so it is not lost to a visit that never showed it.
            LaunchedEffect(Unit) { withContext(Dispatchers.IO) { BiometricVault.markStrongBoxHintShown(context) } }
        }
    }
}

/** The Ajustes tab: lock, clipboard, Argon2id cost and theme; the vault's copies and master password; Acerca de. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    store: SettingsStore,
    vault: VaultStore,
    autoBackup: AutoBackup,
    releaseIfUnused: (String) -> Unit,
    onAbout: () -> Unit,
    onCheckBackup: () -> Unit,
    onAppearance: () -> Unit,
) {
    VaultIdleLock(vault)
    val settings by store.settings.collectAsStateWithLifecycle()
    val vaultState by vault.state.collectAsStateWithLifecycle()
    LaunchedEffect(vault) { vault.load() }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val actions = vaultActions(vault, snackbar)
    val highAvailable = remember { KdfProfile.HIGH.availableOn(Runtime.getRuntime().maxMemory() / 1024) }
    // COV2-1: the lock, the clipboard and the Argon2id cost change only with the vault open, or with none (the theme always).
    val editable = settingsEditable(vaultState)
    fun change(f: (Settings) -> Settings) {
        scope.launch {
            try {
                store.update(f)
            } catch (e: Exception) {
                snackbar.showSnackbar(SETTING_SAVE_FAILED)
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { TopAppBar(title = { Text("Ajustes") }) },
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Section("Bloquear la bóveda tras no usarla") {
                Choices(LockAfter.entries, settings.lockAfter, { it.label }, enabled = { editable }) { v ->
                    if (settingsEditable(vault.state.value)) change { it.copy(lockAfter = v) }
                }
                if (!editable) Hint(SETTINGS_NEED_OPEN_VAULT)
                Hint(LOCK_ON_EXIT_SHORT)
                MoreInfo { Hint(LOCK_ON_EXIT_HINT) }
            }
            Section("Borrar del portapapeles lo copiado") {
                Choices(ClipboardClear.entries, settings.clipboard, { "A los ${it.seconds} s" }, enabled = { editable }) { v ->
                    if (settingsEditable(vault.state.value)) change { it.copy(clipboard = v) }
                }
                if (!editable) Hint(SETTINGS_NEED_OPEN_VAULT)
                Hint(CLIPBOARD_CLEAR_SHORT)
                MoreInfo { Hint(CLIPBOARD_CLEAR_HINT) }
            }
            Section("Coste de Argon2id para lo nuevo") {
                // 2.0, task 2: the cost is each vault's (the one shown).
                Choices(KdfProfile.entries, settings.vault(vault.id).kdf, { it.label }, enabled = { editable && (it != KdfProfile.HIGH || highAvailable) }) { v ->
                    if (settingsEditable(vault.state.value)) change { s -> s.withVault(vault.id) { it.copy(kdf = v) } }
                }
                if (!editable) Hint(SETTINGS_NEED_OPEN_VAULT)
                // On view: what the high cost means, and why its chip is off (code review of 1.4.2).
                Hint(KDF_HIGH_SHORT)
                if (!highAvailable) Hint(KDF_HIGH_UNAVAILABLE)
                MoreInfo {
                        Hint(
                            "Es de cada bóveda. Se usa al crear esta bóveda, al cambiar su contraseña maestra y, con ella elegida, al cifrar textos y archivos. Lo ya cifrado guarda su coste. " +
                                "Lo cifrado con el coste alto solo se abre en móviles que den a la app al menos 171 MiB de memoria: muchos móviles " +
                                "económicos no llegan, así que úsalo solo si vas a abrir tus copias en móviles como este.",
                        )
                }
            }
            // 2.0, task 5 bis: the mode, the style and the palette have their own screen, with a preview.
            Section(APPEARANCE_TITLE) {
                Hint(appearanceSummary(settings))
                OutlinedButton(onClick = onAppearance, modifier = Modifier.fillMaxWidth()) { Text(APPEARANCE_CHANGE) }
            }
            Section("Copia de la bóveda") {
                val unlocked = vaultState is VaultState.Unlocked
                val canImport = unlocked || vaultState is VaultState.NoVault
                OutlinedButton(onClick = actions.export, enabled = unlocked, modifier = Modifier.fillMaxWidth()) { Text("Exportar bóveda") }
                OutlinedButton(onClick = actions.import, enabled = canImport, modifier = Modifier.fillMaxWidth()) { Text("Importar copia") }
                OutlinedButton(onClick = actions.changeMaster, enabled = unlocked, modifier = Modifier.fillMaxWidth()) { Text("Cambiar contraseña maestra") }
                // Reads a copy and changes neither the vault nor a copy: open, locked or with no vault.
                OutlinedButton(onClick = onCheckBackup, modifier = Modifier.fillMaxWidth()) { Text(BACKUP_CHECK_TITLE) }
                // 2.0, task 8: one line once 30 days have passed since a copy of this vault was checked (O-5: more below).
                (vaultState as? VaultState.Unlocked)?.data?.let { data -> BackupCheckNoticeLine(data.lastCheckedBackupAt) }
                Hint(
                    when (vaultState) {
                        VaultState.Locked -> "Desbloquea la bóveda (pestaña Bóveda) para exportarla, importar una copia o cambiar la contraseña maestra."
                        VaultState.NoVault -> "Aún no tienes bóveda: puedes crearla en la pestaña Bóveda o importar una copia."
                        else -> "La copia va cifrada con tu contraseña maestra: guárdala donde quieras. " +
                            "Al importar se reemplaza la bóveda de este móvil."
                    },
                )
                MoreInfo { Hint("«Exportar bóveda» guarda una copia .sgbak con los adjuntos. «Importar copia» acepta esa copia y la exportación .json de la 1.0.") }
            }
            AutoBackupSection(store, vault, autoBackup, releaseIfUnused, snackbar)
            FingerprintKeySection(vault.id, vaultState)
            Section("Información") {
                TextButton(onClick = onAbout) { Text("Acerca de, licencias y avisos") }
            }
        }
    }
}

/** Published from docs/privacidad.html with GitHub Pages; Google Play requires the same link in the store listing. */
const val PRIVACY_POLICY_URL = "https://egt17baa.github.io/Seguridad/privacidad.html"
const val SOURCE_CODE_URL = "https://github.com/Egt17baa/Seguridad"

/** Opens [url] in the browser (the app itself makes no request); without a browser the address is shown to copy. */
@Composable
private fun OpenLinkButton(label: String, url: String) {
    val uriHandler = LocalUriHandler.current
    var failed by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { failed = runCatching { uriHandler.openUri(url) }.isFailure }, modifier = Modifier.fillMaxWidth()) { Text(label) }
    if (failed) {
        Hint("No hay un navegador para abrirlo. Puedes copiar la dirección:")
        SelectionContainer { Text(url, style = MaterialTheme.typography.bodySmall) }
    }
}

/** Version and what the app does not promise; the licenses of what it ships are one tap away. */
@Composable
fun AboutScreen(onBack: () -> Unit, onLicenses: () -> Unit) {
    BackScaffold("Acerca de", onBack) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Clavero ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.headlineSmall)
            Hint(ABOUT_SUMMARY)
            Section("Avisos") {
                Hint(ABOUT_WARNINGS_SHORT)
                MoreInfo {
                    Hint("• Sin auditoría independiente: la app no ha pasado una auditoría de seguridad externa. Para fondos importantes usa una cartera física de criptomonedas y un gestor de contraseñas auditado.")
                    Hint("• Sin recuperación: si olvidas la contraseña maestra o la de algo cifrado, nadie puede recuperar lo guardado.")
                    Hint("• Red: solo se conectan «¿Filtrada?» y «Comprobar filtraciones» de la salud de la bóveda, cuando las pulsas, a api.pwnedpasswords.com, y solo envían los 5 primeros caracteres de la huella SHA-1 de cada contraseña.")
                }
            }
            Section("Privacidad") {
                Hint("La app no recoge datos: no hay cuentas, analítica ni anuncios. La política completa está en la web.")
                OpenLinkButton("Ver la política de privacidad", PRIVACY_POLICY_URL)
            }
            Section("Licencias") {
                Hint("Solo componentes de código abierto con licencia libre y gratuita. La app es gratuita, sin anuncios ni compras, y su código es abierto: © 2026 Egt17baa, licencia Apache-2.0.")
                OutlinedButton(onClick = onLicenses, modifier = Modifier.fillMaxWidth()) { Text("Ver licencias") }
                OpenLinkButton("Ver el código fuente", SOURCE_CODE_URL)
            }
        }
    }
}
