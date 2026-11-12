package app.seguridad.ui.screens

import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.seguridad.AppContainer
import app.seguridad.android.FilterObscuredTouches
import app.seguridad.core.PickerPlace
import app.seguridad.core.readAtMost
import app.seguridad.crypto.CryptoFailure
import app.seguridad.crypto.newPasswordFailureMessage
import app.seguridad.ui.theme.BackScaffold
import app.seguridad.ui.theme.NegativeColor
import app.seguridad.ui.theme.NoCopy
import app.seguridad.ui.theme.PasswordField
import app.seguridad.vault.BACKUP_DAMAGED
import app.seguridad.vault.BACKUP_HEAD_BYTES
import app.seguridad.vault.MIN_MASTER
import app.seguridad.vault.TagColor
import app.seguridad.vault.VAULTS_MORE
import app.seguridad.vault.VAULTS_SHORT
import app.seguridad.vault.VAULTS_TITLE
import app.seguridad.vault.VAULT_ACTIVE
import app.seguridad.vault.VAULT_CHANGE
import app.seguridad.vault.VAULT_CHOOSE
import app.seguridad.vault.VAULT_COLOR
import app.seguridad.vault.VAULT_CREATE
import app.seguridad.vault.VAULT_DELETE
import app.seguridad.vault.VAULT_DELETED
import app.seguridad.vault.VAULT_DELETE_LAST
import app.seguridad.vault.VAULT_DELETE_MASTER
import app.seguridad.vault.VAULT_DELETE_NAME_WRONG
import app.seguridad.vault.VAULT_DELETE_OPEN_FIRST
import app.seguridad.vault.VAULT_DOWN
import app.seguridad.vault.VAULT_FAILED
import app.seguridad.vault.VAULT_IMPORT_NEW
import app.seguridad.vault.VAULT_IMPORT_NEW_DONE
import app.seguridad.vault.VAULT_IMPORT_NEW_SHORT
import app.seguridad.vault.VAULT_NAME_LABEL
import app.seguridad.vault.VAULT_NEW
import app.seguridad.vault.VAULT_NEW_NEEDS_OPEN
import app.seguridad.vault.VAULT_NEW_SHORT
import app.seguridad.vault.VAULT_OPEN_NOW
import app.seguridad.vault.VAULT_RENAME
import app.seguridad.vault.VAULT_UP
import app.seguridad.vault.VaultLimitExceeded
import app.seguridad.vault.VaultLimits
import app.seguridad.vault.VaultLock
import app.seguridad.vault.VaultState
import app.seguridad.vault.displayName
import app.seguridad.vault.label
import app.seguridad.vault.mayBeVaultImport
import app.seguridad.vault.newMasterProblem
import app.seguridad.vault.vaultDeleteTypeName
import app.seguridad.vault.vaultDeleteWarning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 2.0, task 2: what the vault screens show at their top to change vaults (AppNavHost provides it): the active vault's
 * colour and the name the list gives it ([displayName]), and a tap opens «Bóvedas». Empty outside the app's navigation.
 */
val LocalVaultSwitcher = compositionLocalOf<@Composable () -> Unit> { {} }

/** The colour of a vault's dot. Its name goes with it ([label]): the colour is never the only sign. */
internal fun TagColor.swatch(): Color = when (this) {
    TagColor.RED -> Color(0xFFD32F2F)
    TagColor.ORANGE -> Color(0xFFF57C00)
    TagColor.YELLOW -> Color(0xFFFBC02D)
    TagColor.GREEN -> Color(0xFF388E3C)
    TagColor.TEAL -> Color(0xFF00897B)
    TagColor.BLUE -> Color(0xFF1976D2)
    TagColor.PURPLE -> Color(0xFF7B1FA2)
    TagColor.GRAY -> Color(0xFF757575)
}

@Composable
private fun Dot(color: TagColor, modifier: Modifier = Modifier) {
    Box(modifier.size(16.dp).background(color.swatch(), CircleShape).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
}

/** The line at the top of the vault screens: the active vault and «Cambiar de bóveda». */
@Composable
fun VaultSwitcher(container: AppContainer, onOpen: () -> Unit) {
    val store by container.activeVault.collectAsStateWithLifecycle()
    val vaults by container.registry.vaults.collectAsStateWithLifecycle()
    val state by store.state.collectAsStateWithLifecycle()
    val order = vaults.indexOfFirst { it.id == store.id }.coerceAtLeast(0)
    val info = vaults.getOrNull(order) ?: return
    val name = displayName((state as? VaultState.Unlocked)?.data, order)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.clickable(role = Role.Button, onClick = onOpen).padding(vertical = 4.dp)
            .semantics { contentDescription = "$name, ${info.color.label()}. $VAULT_CHANGE" },
    ) {
        Dot(info.color)
        Text(name, style = MaterialTheme.typography.titleMedium)
        Text("· $VAULT_CHANGE", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
    }
}

private sealed interface PickerDialog {
    data object New : PickerDialog
    data object Rename : PickerDialog
    data object Delete : PickerDialog
    data class ImportNew(val uri: Uri) : PickerDialog
}

/**
 * «Bóvedas»: the list of vaults before and after unlocking («Bóveda N» and its colour; the open one by its name), with
 * the active one marked; choose, create (name and a new master, MasterPolicy), rename and delete the open one (its
 * master and its name), change the order and the colour, and «Importar como bóveda nueva». Every secret typed here
 * (masters, the typed name) lives in this composition only (never in saved state) and goes with it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VaultPickerScreen(container: AppContainer, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val vaults by container.registry.vaults.collectAsStateWithLifecycle()
    val active by container.activeVault.collectAsStateWithLifecycle()
    val activeState by active.state.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<PickerDialog?>(null) }
    var busy by remember { mutableStateOf(false) }
    val openData = (activeState as? VaultState.Unlocked)?.data
    // COV2-1: another vault, the order and the colours only from an open one (or with none on the phone), so someone
    // holding the phone with every vault locked changes nothing here but which one is shown; read off the main thread.
    val canAdd by produceState(false, activeState, vaults) { value = withContext(Dispatchers.IO) { container.canAddVault() } }
    VaultIdleLock(active)

    fun run(block: suspend () -> String?) {
        if (busy) return
        busy = true
        scope.launch {
            val message = try {
                withContext(NonCancellable) { block() }
            } catch (e: VaultLimitExceeded) {
                e.message
            } catch (e: CryptoFailure) {
                e.message ?: "Contraseña incorrecta"
            } catch (e: Exception) {
                VAULT_FAILED
            } finally {
                busy = false
            }
            message?.let { snackbar.showSnackbar(it) }
        }
    }

    val importer = rememberDocumentPicker(ActivityResultContracts.OpenDocument(), place = PickerPlace.IMPORT_COPY) { uri ->
        VaultLock.keepOpenOnce = false
        if (uri != null) scope.launch {
            val head = runCatching {
                withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)!!.use { it.readAtMost(BACKUP_HEAD_BYTES) } }
            }.getOrNull()
            when {
                head == null -> snackbar.showSnackbar("No se pudo leer el archivo")
                !mayBeVaultImport(head) -> snackbar.showSnackbar(BACKUP_DAMAGED)
                else -> dialog = PickerDialog.ImportNew(uri)
            }
        }
    }

    BackScaffold(VAULTS_TITLE, onBack) { padding ->
        Box(Modifier.padding(padding)) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(VAULTS_SHORT, style = MaterialTheme.typography.bodyMedium)
                MoreInfo { Text(VAULTS_MORE, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                vaults.forEachIndexed { i, info ->
                    val isActive = info.id == active.id
                    val name = displayName(if (isActive) openData else null, i)
                    Column(
                        Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium).padding(12.dp)
                            .semantics { selected = isActive },
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Dot(info.color)
                            Text(name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            if (isActive) Text(if (openData != null) VAULT_OPEN_NOW else VAULT_ACTIVE, style = MaterialTheme.typography.labelMedium)
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!isActive) {
                                OutlinedButton(enabled = !busy, onClick = {
                                    run {
                                        container.switchTo(info.id)
                                        onBack() // To its unlock screen.
                                        null
                                    }
                                }) { Text(VAULT_CHOOSE) }
                            }
                            if (i > 0) {
                                TextButton(enabled = !busy && canAdd, onClick = {
                                    run {
                                        container.registry.reorder(vaults.map { it.id }.toMutableList().apply { add(i - 1, removeAt(i)) })
                                        null
                                    }
                                }) { Text(VAULT_UP) }
                            }
                            if (i < vaults.lastIndex) {
                                TextButton(enabled = !busy && canAdd, onClick = {
                                    run {
                                        container.registry.reorder(vaults.map { it.id }.toMutableList().apply { add(i + 1, removeAt(i)) })
                                        null
                                    }
                                }) { Text(VAULT_DOWN) }
                            }
                        }
                        Text(VAULT_COLOR, style = MaterialTheme.typography.labelMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            TagColor.entries.forEach { c ->
                                Box(
                                    Modifier.size(32.dp).clickable(role = Role.RadioButton, enabled = !busy && canAdd) {
                                        run {
                                            container.registry.setColor(info.id, c)
                                            null
                                        }
                                    }.semantics {
                                        contentDescription = c.label()
                                        selected = c == info.color
                                    },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Dot(c, if (c == info.color) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                                }
                            }
                        }
                        if (isActive && openData != null) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(enabled = !busy, onClick = { dialog = PickerDialog.Rename }) { Text(VAULT_RENAME) }
                                if (vaults.size > 1) {
                                    TextButton(enabled = !busy, onClick = { dialog = PickerDialog.Delete }) { Text(VAULT_DELETE, color = NegativeColor) }
                                }
                            }
                            if (vaults.size <= 1) Text(VAULT_DELETE_LAST, style = MaterialTheme.typography.bodySmall)
                        } else if (isActive && vaults.size > 1) {
                            Text(VAULT_DELETE_OPEN_FIRST, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (vaults.size < VaultLimits.VAULTS_MAX) {
                    if (!canAdd) Text(VAULT_NEW_NEEDS_OPEN, style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(enabled = !busy && canAdd, onClick = { dialog = PickerDialog.New }, modifier = Modifier.fillMaxWidth()) { Text(VAULT_NEW) }
                    OutlinedButton(enabled = !busy && canAdd, onClick = {
                        VaultLock.keepOpenOnce = true
                        active.touch()
                        importer.launchOrError(arrayOf("*/*"))?.let { failure ->
                            VaultLock.keepOpenOnce = false
                            scope.launch { snackbar.showSnackbar(failure) }
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text(VAULT_IMPORT_NEW) }
                    Text(VAULT_IMPORT_NEW_SHORT, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (busy) CircularProgressIndicator(Modifier.size(24.dp))
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
    }

    when (val d = dialog) {
        PickerDialog.New -> NewVaultDialog(onDismiss = { dialog = null }) { name, master ->
            dialog = null
            run {
                try {
                    container.createVault(name, master)
                    onBack() // To the new vault, open.
                    null
                } catch (e: VaultLimitExceeded) {
                    e.message
                } catch (e: Exception) {
                    newPasswordFailureMessage(e, VAULT_FAILED)
                } finally {
                    master.fill('\u0000')
                }
            }
        }
        PickerDialog.Rename -> NameDialog(VAULT_RENAME, openData?.name.orEmpty(), onDismiss = { dialog = null }) { name ->
            dialog = null
            run {
                active.rename(name)
                null
            }
        }
        PickerDialog.Delete -> {
            val order = vaults.indexOfFirst { it.id == active.id }
            val shown = displayName(openData, order)
            DeleteVaultDialog(shown, onDismiss = { dialog = null }) { master, typed ->
                dialog = null
                val id = active.id
                run {
                    try {
                        if (container.deleteVault(id, master, typed)) VAULT_DELETED else VAULT_DELETE_NAME_WRONG
                    } finally {
                        master.fill('\u0000')
                    }
                }
            }
        }
        // The copy's master password, in the dialog of «Importar copia» (it already exists: only its length, CRY-A01). The
        // new vault keeps the copy's name; it can be changed here once it is open.
        is PickerDialog.ImportNew -> PasswordDialog(
            title = "Contraseña maestra de la copia",
            confirmLabel = "Importar",
            warning = VAULT_IMPORT_NEW_SHORT,
            onDismiss = { dialog = null },
        ) { master ->
            dialog = null
            run {
                try {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(d.uri)!!.use { container.importAsNewVault(it, master, name = "") }
                    }
                    VAULT_IMPORT_NEW_DONE
                } finally {
                    master.fill('\u0000')
                }
            }
        }
        null -> Unit
    }
}

/** A name for a vault, at most [VaultLimits.VAULT_NAME_MAX] code points (the store refuses more). */
@Composable
private fun NameField(value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.codePointCount(0, it.length) <= VaultLimits.VAULT_NAME_MAX) onChange(it) },
        label = { Text(VAULT_NAME_LABEL) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            FilterObscuredTouches()
            NameField(name) { name = it }
        },
        confirmButton = { TextButton(onClick = { onConfirm(name) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** A new vault: its name and a new master (MasterPolicy: [newMasterProblem]), typed twice, and the notice of no recovery. */
@Composable
private fun NewVaultDialog(onDismiss: () -> Unit, onConfirm: (String, CharArray) -> Unit) {
    var name by remember { mutableStateOf("") }
    var a by remember { mutableStateOf("") }
    var b by remember { mutableStateOf("") }
    var understood by remember { mutableStateOf(false) }
    val words = rememberMasterWords()
    val problem = newMasterProblem(a, words ?: emptySet())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(VAULT_NEW) },
        text = {
            FilterObscuredTouches()
            NoCopy {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(VAULT_NEW_SHORT, style = MaterialTheme.typography.bodySmall)
                    NameField(name) { name = it }
                    PasswordField(a, { a = it }, label = "Contraseña maestra (mínimo $MIN_MASTER caracteres)")
                    if (a.isNotEmpty()) problem?.let { Text(it, color = NegativeColor) }
                    PasswordField(b, { b = it }, label = "Repítela")
                    if (b.isNotEmpty() && a != b) Text("No coinciden", color = NegativeColor)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { understood = !understood }) {
                        Checkbox(checked = understood, onCheckedChange = { understood = it })
                        Text("Entiendo que si olvido la contraseña maestra nadie puede recuperar lo que guarde.")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = words != null && problem == null && a == b && understood, onClick = { onConfirm(name, a.toCharArray()) }) {
                Text(VAULT_CREATE)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** Ruling 3: the open vault's master and its name typed exactly; what goes and what stays on view (O-2, R-9). */
@Composable
private fun DeleteVaultDialog(name: String, onDismiss: () -> Unit, onConfirm: (CharArray, String) -> Unit) {
    var pw by remember { mutableStateOf("") }
    var typed by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(VAULT_DELETE) },
        text = {
            FilterObscuredTouches()
            NoCopy {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(vaultDeleteWarning(name), color = NegativeColor)
                    PasswordField(pw, { pw = it }, label = VAULT_DELETE_MASTER)
                    Text(vaultDeleteTypeName(name), style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value = typed, onValueChange = { typed = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            TextButton(enabled = pw.isNotEmpty() && typed.isNotBlank(), onClick = { onConfirm(pw.toCharArray(), typed) }) {
                Text("Borrar", color = NegativeColor)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
