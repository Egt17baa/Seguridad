package app.seguridad.ui.screens

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
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.seguridad.android.BiometricVault
import app.seguridad.android.FilterObscuredTouches
import app.seguridad.core.readAtMost
import app.seguridad.crypto.CryptoFailure
import app.seguridad.ui.theme.NegativeColor
import app.seguridad.ui.theme.NoCopy
import app.seguridad.ui.theme.PasswordField
import app.seguridad.vault.VaultLock
import app.seguridad.vault.VaultStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val MIN_MASTER = 10

/** Name offered when exporting the vault. */
const val VAULT_EXPORT_FILE_NAME = "seguridad-boveda.json"

private const val VAULT_IMPORT_MAX_BYTES = 20 * 1024 * 1024

/** What the vault menu and Ajustes can do with the vault file; each one shows its own dialog or picker. */
class VaultActions(val export: () -> Unit, val import: () -> Unit, val changeMaster: () -> Unit)

/**
 * Export, import (also an export of the older app) and change of master password, with their pickers and
 * dialogs; results are shown in [snackbar]. Call once per screen, where its dialogs belong.
 */
@Composable
fun vaultActions(store: VaultStore, snackbar: SnackbarHostState): VaultActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var dialog by remember { mutableStateOf<String?>(null) }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val text = store.exportText() ?: run {
                discardDocument(context, uri)
                return@launch
            }
            val ok = runCatching {
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray()) } }
            }.isSuccess
            if (!ok) discardDocument(context, uri) // Never leave an empty or cut copy that looks like a backup.
            snackbar.showSnackbar(if (ok) "Copia cifrada guardada. Necesitarás tu contraseña maestra para abrirla." else "No se pudo guardar la copia")
        }
    }
    var importText by remember { mutableStateOf<String?>(null) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            importText = runCatching {
                withContext(Dispatchers.IO) {
                    // A real export is a few hundred KB; a huge file must not exhaust memory.
                    context.contentResolver.openInputStream(uri)!!.use { it.readAtMost(VAULT_IMPORT_MAX_BYTES + 1) }
                        .also { require(it.size <= VAULT_IMPORT_MAX_BYTES) }.decodeToString()
                }
            }.getOrNull()
            if (importText == null) snackbar.showSnackbar("No se pudo leer el archivo") else dialog = "import"
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
                            BiometricVault.disable(context) // It wrapped the old data key.
                            "Contraseña maestra cambiada. Vuelve a activar la huella si la usabas."
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
                importText = null
            },
        ) { pw ->
            val text = importText ?: return@PasswordDialog
            importText = null
            scope.launch {
                val msg = withContext(NonCancellable) {
                    try {
                        store.importReplace(text, pw)
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
                exporter.launch(VAULT_EXPORT_FILE_NAME)
            },
            import = {
                VaultLock.keepOpenOnce = true
                importer.launch(arrayOf("application/json", "application/octet-stream", "text/*"))
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
