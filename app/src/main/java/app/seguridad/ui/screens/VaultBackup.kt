package app.seguridad.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.seguridad.android.BiometricVault
import app.seguridad.android.FilterObscuredTouches
import app.seguridad.core.readAtMost
import app.seguridad.crypto.CryptoFailure
import app.seguridad.tools.PASSWORD_CHANGED_FINGERPRINT_FAILED
import app.seguridad.ui.theme.NegativeColor
import app.seguridad.ui.theme.NoCopy
import app.seguridad.ui.theme.PasswordField
import app.seguridad.vault.BACKUP_HEAD_BYTES
import app.seguridad.vault.VaultLock
import app.seguridad.vault.VaultState
import app.seguridad.vault.VaultStore
import app.seguridad.vault.backupFileName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

const val MIN_MASTER = 10

/**
 * What to do once the master password was changed (the vault is then locked), with whether the fingerprint could not be
 * turned off: the app root shows the dialog about the old copies and the identity ([MasterChangedDialog]). One place for
 * every screen that can change it; the default does nothing. The app root provides AppContainer.masterChanged, which
 * outlives the activity: a lambda captured before a rotation still reaches the dialog of the new one (M-3).
 */
val LocalOnMasterChanged = staticCompositionLocalOf<(fingerprintFailed: Boolean) -> Unit> { {} }

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
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) scope.launch {
            // Written as a stream straight into the chosen document: never a copy in memory or in the cache.
            val result = runCatching {
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri, "wt")!!.use { store.exportBackup(it) } }
            }
            val ok = result.getOrNull() == true
            // Never leave an empty or cut copy that looks like a backup: off the main thread, and not cut by a cancellation.
            if (!ok) withContext(NonCancellable + Dispatchers.IO) { discardDocument(context, uri) }
            // A CryptoFailure names the attachment that is missing or damaged; false with a vault means it locked
            // meanwhile, and only an open vault's attachments can be checked.
            val failure = (result.exceptionOrNull() as? CryptoFailure)?.message
                ?: "La bóveda se bloqueó: ábrela y vuelve a exportarla".takeIf { result.isSuccess && store.state.value is VaultState.Locked }
            snackbar.showSnackbar(if (ok) "Copia cifrada guardada. Necesitarás tu contraseña maestra para abrirla." else failure ?: "No se pudo guardar la copia")
        }
    }
    // Only the document's address is kept until the password is typed; the file is read as a stream then.
    var importUri by remember { mutableStateOf<Uri?>(null) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            // Its first bytes, to say now (not after the password) if it cannot be read.
            val readable = runCatching {
                withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)!!.use { it.readAtMost(BACKUP_HEAD_BYTES) } }
            }.isSuccess
            if (!readable) {
                snackbar.showSnackbar("No se pudo leer el archivo")
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
            onDismiss = { dialog = null },
        ) { pw ->
            scope.launch {
                // Not cut short by leaving the screen: the fingerprint must be dropped together with the old key.
                val msg = withContext(NonCancellable) {
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
                            onMasterChanged(!fingerprintOff)
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
                        "No se pudo cambiar la contraseña maestra"
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
                        withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)!!.use { store.importReplace(it, pw) } }
                        BiometricVault.disable(context)
                        "Copia importada"
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
                // Some cloud providers label a ZIP uploaded from a computer x-zip-compressed; the content is sniffed anyway.
                importer.launchOrError(arrayOf("application/octet-stream", "application/zip", "application/x-zip-compressed", "application/json", "text/*"))?.let { failure ->
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
    warning: String? = null,
    onConfirm: (CharArray) -> Unit,
) {
    var a by remember { mutableStateOf("") }
    var b by remember { mutableStateOf("") }
    val ok = a.length >= MIN_MASTER && (!askTwice || a == b)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            FilterObscuredTouches()
            NoCopy {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    warning?.let { Text(it, color = NegativeColor) }
                    PasswordField(a, { a = it })
                    if (askTwice) PasswordField(b, { b = it }, label = "Repítela")
                    if (askTwice && b.isNotEmpty() && a != b) Text("No coinciden", color = NegativeColor)
                }
            }
        },
        confirmButton = { TextButton(enabled = ok, onClick = { onConfirm(a.toCharArray()) }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
