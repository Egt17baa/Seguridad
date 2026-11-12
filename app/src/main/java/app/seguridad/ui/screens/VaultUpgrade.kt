package app.seguridad.ui.screens

import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.seguridad.android.FilterObscuredTouches
import app.seguridad.core.PickerPlace
import app.seguridad.ui.theme.NegativeColor
import app.seguridad.vault.VaultLock
import app.seguridad.vault.VaultStore
import app.seguridad.vault.backupFileName
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// 2.0, task 1: the choice before the vault passes to format v4 (design «Formato v4»; Abogado Digital, O-3).
internal const val UPGRADE_TITLE = "Formato nuevo de la bóveda"
internal const val UPGRADE_NOT_BACK = "Después del cambio, las versiones anteriores de Clavero no abrirán esta bóveda ni sus copias nuevas."
internal const val UPGRADE_COPY_FIRST = "Antes, exporta una copia: esa sí la abren las versiones anteriores."
internal const val UPGRADE_MORE =
    "La copia guarda la bóveda tal como está ahora, cifrada con tu contraseña maestra. " +
        "Hasta que elijas, la bóveda se puede ver pero no se guarda ningún cambio."
internal const val UPGRADE_EXPORT = "Exportar una copia antes"
internal const val UPGRADE_SKIP = "Seguir sin copia"
internal const val UPGRADE_EXPORT_FAILED = "No se pudo guardar la copia. Vuelve a intentarlo o sigue sin copia."
internal const val UPGRADE_SAVE_FAILED = "No se pudo guardar la bóveda en el formato nuevo. ¿Queda espacio en el móvil? Vuelve a intentarlo."

/**
 * Shown over every vault screen while [VaultStore.pendingUpgrade]: a vault of version 2 or 3 was opened with the master
 * password and there is no automatic copy to make first. «Exportar una copia antes» (the highlighted choice) saves the
 * same copy as «Exportar», of the file still on disk, and then the version 4; «Seguir sin copia» saves the version 4 at
 * once. It cannot be dismissed: the vault saves nothing until one is chosen. Its texts are fixed; nothing secret is
 * kept here.
 */
@Composable
internal fun VaultUpgradeDialog(store: VaultStore) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun complete() {
        if (!store.completeUpgrade() && store.pendingUpgrade.value) error = UPGRADE_SAVE_FAILED
    }

    val exporter = rememberDocumentPicker(ActivityResultContracts.CreateDocument("application/octet-stream"), place = PickerPlace.EXPORT_COPY) { uri ->
        if (uri == null) {
            busy = false
        } else {
            scope.launch {
                val ok = runCatching {
                    withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri, "wt")!!.use { store.exportBackup(it) } }
                }.getOrNull() == true
                if (ok) {
                    complete()
                } else {
                    // Never leave an empty or cut copy that looks like a backup.
                    discardDocumentNow(context, uri)
                    error = UPGRADE_EXPORT_FAILED
                }
                busy = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = {},
        title = { Text(UPGRADE_TITLE) },
        text = {
            FilterObscuredTouches()
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(UPGRADE_NOT_BACK)
                Text(UPGRADE_COPY_FIRST)
                MoreInfo { Text(UPGRADE_MORE, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (busy) CircularProgressIndicator()
                error?.let { Text(it, color = NegativeColor, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
            }
        },
        confirmButton = {
            Button(enabled = !busy, onClick = {
                error = null
                busy = true
                VaultLock.keepOpenOnce = true
                store.touch() // The time away is counted from here (AWAY_BACKSTOP_MS with «Al salir»).
                exporter.launchOrError(backupFileName(LocalDate.now()))?.let { failure ->
                    VaultLock.keepOpenOnce = false // The app never left: leaving later must lock.
                    error = failure
                    busy = false
                }
            }) { Text(UPGRADE_EXPORT) }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = {
                error = null
                busy = true
                scope.launch {
                    complete()
                    busy = false
                }
            }) { Text(UPGRADE_SKIP) }
        },
    )
}
