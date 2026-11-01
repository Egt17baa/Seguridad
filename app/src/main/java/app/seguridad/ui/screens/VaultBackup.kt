package app.seguridad.ui.screens

import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.seguridad.android.BiometricVault
import app.seguridad.android.FilterObscuredTouches
import app.seguridad.core.readAtMost
import app.seguridad.crypto.CryptoFailure
import app.seguridad.crypto.newPasswordFailureMessage
import app.seguridad.tools.AUTO_BACKUP_REPLACE_DROPPED
import app.seguridad.tools.PASSWORD_CHANGED_FINGERPRINT_FAILED
import app.seguridad.ui.theme.NegativeColor
import app.seguridad.ui.theme.NoCopy
import app.seguridad.ui.theme.PasswordField
import app.seguridad.vault.BACKUP_DAMAGED
import app.seguridad.vault.BACKUP_HEAD_BYTES
import app.seguridad.vault.MIN_MASTER
import app.seguridad.vault.VaultLock
import app.seguridad.vault.VaultState
import app.seguridad.vault.VaultStore
import app.seguridad.vault.backupFileName
import app.seguridad.vault.identity
import app.seguridad.vault.mayBeVaultImport
import app.seguridad.vault.newMasterProblem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * What to do once the master password was changed (the vault is then locked), with whether the fingerprint could not be
 * turned off and whether the vault had an identity (R-5 of the earlier dictamen): the app root shows the dialog about
 * the old copies and the identity ([MasterChangedDialog]). One place for every screen that can change it; the default
 * does nothing. The app root provides AppContainer.masterChanged, which outlives the activity: a lambda captured before
 * a rotation still reaches the dialog of the new one (M-3).
 */
val LocalOnMasterChanged = staticCompositionLocalOf<(fingerprintFailed: Boolean, hasIdentity: Boolean) -> Unit> { { _, _ -> } }

/** What the vault menu and Ajustes can do with the vault file; each one shows its own dialog or picker. */
class VaultActions(val export: () -> Unit, val import: () -> Unit, val changeMaster: () -> Unit)

/**
 * Export (a .sgbak copy with the attachments), import (a .sgbak copy, or a JSON export of 1.0.x or CriptoPanel)
 * and change of master password, with their pickers and dialogs; results are shown in [snackbar]. Call once per
 * screen, where its dialogs belong.
 */
@Composable
fun vaultActions(store: VaultStore, snackbar: SnackbarHostState): VaultActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var dialog by remember { mutableStateOf<String?>(null) }
    val onMasterChanged by rememberUpdatedState(LocalOnMasterChanged.current)

    // A copy (.sgbak) is a ZIP, but saved as plain bytes so no app treats it as one to open or rename.
    val exporter = rememberDocumentPicker(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) scope.launch {
            // Written as a stream straight into the chosen document: never a copy in memory or in the cache.
            val result = runCatching {
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri, "wt")!!.use { store.exportBackup(it) } }
            }
            val ok = result.getOrNull() == true
            // Never leave an empty or cut copy that looks like a backup: off the main thread, and not cut by a cancellation.
            if (!ok) discardDocumentNow(context, uri)
            // A CryptoFailure names the attachment that is missing or damaged; false with a vault means it locked
            // meanwhile, and only an open vault's attachments can be checked.
            val failure = (result.exceptionOrNull() as? CryptoFailure)?.message
                ?: "La bóveda se bloqueó: ábrela y vuelve a exportarla".takeIf { result.isSuccess && store.state.value is VaultState.Locked }
            snackbar.showSnackbar(if (ok) "Copia cifrada guardada. Necesitarás tu contraseña maestra para abrirla." else failure ?: "No se pudo guardar la copia")
        }
    }
    // Only the document's address is kept until the password is typed; the file is read as a stream then.
    var importUri by remember { mutableStateOf<Uri?>(null) }
    val importer = rememberDocumentPicker(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            // Its first bytes, to say now (not after the password) if it cannot be read or cannot be a copy: the
            // picker offers every file (owner's phone test, 1-11-2026), so a photo or a note is turned down here.
            val head = runCatching {
                withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)!!.use { it.readAtMost(BACKUP_HEAD_BYTES) } }
            }.getOrNull()
            if (head == null) {
                snackbar.showSnackbar("No se pudo leer el archivo")
            } else if (!mayBeVaultImport(head)) {
                snackbar.showSnackbar(BACKUP_DAMAGED)
            } else {
                importUri = uri
                dialog = "import"
            }
        }
    }

    when (dialog) {
        "master" -> PasswordDialog(
            title = "Nueva contraseña maestra",
            confirmLabel = "Cambiar",
            askTwice = true,
            // CRY-A01: this master is chosen now, so it has to pass newMasterProblem. Importing never does.
            newPassword = true,
            onDismiss = { dialog = null },
        ) { pw ->
            scope.launch {
                // Not cut short by leaving the screen: the fingerprint must be dropped together with the old key.
                val msg = withContext(NonCancellable) {
                    // Read before the change, which locks the vault: whether there is an identity to renew (unknown: yes).
                    val open = store.state.value as? VaultState.Unlocked
                    val hadIdentity = open == null || open.entries.identity() != null
                    try {
                        if (store.changeMaster(pw)) {
                            // From here the password did change: nothing below may say it did not.
                            val fingerprintOff = try {
                                BiometricVault.disable(context) // It wrapped the old data key.
                                true
                            } catch (e: Exception) {
                                false
                            }
                            // Inside this block: leaving the screen or rotating during the key derivation must not
                            // lose the dialog (it is kept outside the activity).
                            onMasterChanged(!fingerprintOff, hadIdentity)
                            // changeMaster has locked the vault: it opens with the new password from now on.
                            if (fingerprintOff) {
                                "Contraseña maestra cambiada. Abre la bóveda con la nueva contraseña. Vuelve a activar la huella si la usabas."
                            } else {
                                PASSWORD_CHANGED_FINGERPRINT_FAILED
                            }
                        } else {
                            "La bóveda se bloqueó: no se ha cambiado nada"
                        }
                    } catch (e: Exception) {
                        newPasswordFailureMessage(e, "No se pudo cambiar la contraseña maestra") // Review F5, M2.
                    } finally {
                        pw.fill('\u0000')
                    }
                }
                snackbar.showSnackbar(msg)
            }
            dialog = null
        }
        "import" -> PasswordDialog(
            title = "Contraseña maestra de la copia",
            confirmLabel = "Importar",
            warning = "Se reemplazará la bóveda actual de este móvil por la de la copia.",
            onDismiss = {
                dialog = null
                importUri = null
            },
        ) { pw ->
            val uri = importUri ?: return@PasswordDialog
            importUri = null
            scope.launch {
                val msg = withContext(NonCancellable) {
                    try {
                        // A .sgbak copy or a JSON export: the store tells them apart by their first bytes.
                        // True when the import dropped a pending request to replace the old automatic copies (COPIAS-3).
                        val dropped = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)!!.use { store.importReplace(it, pw) } }
                        BiometricVault.disable(context)
                        if (dropped) "Copia importada. $AUTO_BACKUP_REPLACE_DROPPED" else "Copia importada"
                    } catch (e: CryptoFailure) {
                        e.message ?: "Contraseña incorrecta"
                    } catch (e: Exception) {
                        "No se pudo importar la copia"
                    } finally {
                        pw.fill('\u0000')
                    }
                }
                snackbar.showSnackbar(msg)
            }
            dialog = null
        }
    }

    return remember(store) {
        VaultActions(
            export = {
                VaultLock.keepOpenOnce = true
                store.touch() // The time away is counted from here (AWAY_BACKSTOP_MS with «Al salir»).
                exporter.launchOrError(backupFileName(LocalDate.now()))?.let { failure ->
                    VaultLock.keepOpenOnce = false // The app never left: leaving later must lock.
                    scope.launch { snackbar.showSnackbar(failure) }
                }
            },
            import = {
                VaultLock.keepOpenOnce = true
                store.touch() // The time away is counted from here (AWAY_BACKSTOP_MS with «Al salir»).
                // Every file: Drive and other cloud providers label a .sgbak as they like (an unknown extension), and
                // a type list left the copies in Drive greyed out (owner's phone test, 1-11-2026). The head is checked
                // right after picking (mayBeVaultImport) and the import checks everything.
                importer.launchOrError(arrayOf("*/*"))?.let { failure ->
                    VaultLock.keepOpenOnce = false // The app never left: leaving later must lock.
                    scope.launch { snackbar.showSnackbar(failure) }
                }
            },
            changeMaster = { dialog = "master" },
        )
    }
}

@Composable
internal fun PasswordDialog(
    title: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    askTwice: Boolean = false,
    newPassword: Boolean = false,
    warning: String? = null,
    onConfirm: (CharArray) -> Unit,
) {
    var a by remember { mutableStateOf("") }
    var b by remember { mutableStateOf("") }
    // CRY-A01: only a password chosen now (the new master) has to be long enough and not easy to guess. The password of
    // a copy to import (newPassword = false) already exists, so it only needs the length it always needed.
    val problem = if (newPassword) newMasterProblem(a) else null
    val ok = (if (newPassword) problem == null else a.length >= MIN_MASTER) && (!askTwice || a == b)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            FilterObscuredTouches()
            NoCopy {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    warning?.let { Text(it, color = NegativeColor) }
                    PasswordField(a, { a = it })
                    // A polite live region, so TalkBack announces it when it changes.
                    if (a.isNotEmpty()) problem?.let { Text(it, color = NegativeColor, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
                    if (askTwice) PasswordField(b, { b = it }, label = "Repítela")
                    if (askTwice && b.isNotEmpty() && a != b) Text("No coinciden", color = NegativeColor)
                }
            }
        },
        confirmButton = { TextButton(enabled = ok, onClick = { onConfirm(a.toCharArray()) }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
