package app.seguridad.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.seguridad.android.FilterObscuredTouches
import app.seguridad.crypto.CryptoFailure
import app.seguridad.crypto.safeFileName
import app.seguridad.ui.theme.NegativeColor
import app.seguridad.ui.theme.SectionCard
import app.seguridad.vault.AttachmentLimit
import app.seguridad.vault.AttachmentRef
import app.seguridad.vault.MAX_ATTACHMENTS_TOTAL_BYTES
import app.seguridad.vault.VaultLock
import app.seguridad.vault.VaultStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/** «1,5 MB», «820 KB» or «12 B». */
private fun sizeText(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> String.format(Locale.forLanguageTag("es-ES"), "%.1f MB", bytes / (1024.0 * 1024))
    bytes >= 1024 -> "${bytes / 1024} KB"
    else -> "$bytes B"
}

/**
 * «Adjuntos» of a saved entry: add (encrypted with its own key), save decrypted where the user picks (written
 * straight from memory to that document, never through the cache) and remove. Each change saves the vault at once.
 */
@Composable
internal fun AttachmentsSection(store: VaultStore, entryId: String, attachments: List<AttachmentRef>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<AttachmentRef?>(null) }
    // Only the random id of the attachment being saved, never its content.
    var pendingSave by rememberSaveable { mutableStateOf<String?>(null) }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            message = try {
                val added = withContext(Dispatchers.IO) {
                    val name = displayName(context, uri)
                    context.contentResolver.openInputStream(uri)!!.use { store.addAttachment(entryId, it, name) }
                }
                if (added) true to "Adjunto cifrado y guardado en la bóveda." else false to "La bóveda se bloqueó: no se ha añadido."
            } catch (e: AttachmentLimit) {
                false to (e.message ?: "El adjunto es demasiado grande")
            } catch (e: Exception) {
                false to "No se pudo añadir el adjunto."
            } finally {
                busy = false
            }
        }
    }

    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { dest ->
        val id = pendingSave
        pendingSave = null
        if (dest == null || id == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            message = try {
                val plain = store.openAttachment(entryId, id)
                if (plain == null) {
                    discardDocumentNow(context, dest)
                    false to "La bóveda se bloqueó o el adjunto ya no está: no se ha guardado."
                } else {
                    try {
                        withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(dest, "wt")!!.use { it.write(plain) } }
                    } finally {
                        plain.fill(0)
                    }
                    true to "Adjunto descifrado y guardado."
                }
            } catch (e: CryptoFailure) {
                discardDocumentNow(context, dest) // Never leave an empty file that looks like the attachment.
                false to (e.message ?: "El adjunto está dañado")
            } catch (e: Exception) {
                discardDocumentNow(context, dest)
                false to "No se pudo guardar el adjunto."
            } finally {
                busy = false
            }
        }
    }

    SectionCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Adjuntos", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (attachments.isEmpty()) {
                Text("Sin adjuntos. Cada uno se cifra con su propia clave, hasta 5 MB por archivo.", style = MaterialTheme.typography.bodySmall)
            }
            attachments.forEach { ref ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("📎 ${safeFileName(ref.name)} · ${sizeText(ref.size)}")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(enabled = !busy, onClick = {
                            pendingSave = ref.id
                            VaultLock.keepOpenOnce = true
                            store.touch() // The time away is counted from here (AWAY_BACKSTOP_MS with «Al salir»).
                            save.launchOrError(safeFileName(ref.name))?.let { failure ->
                                VaultLock.keepOpenOnce = false // The app never left: leaving later must lock.
                                pendingSave = null
                                message = false to failure
                            }
                        }) { Text("Guardar descifrado") }
                        TextButton(enabled = !busy, onClick = { removing = ref }) { Text("Quitar", color = NegativeColor) }
                    }
                }
            }
            val total = attachments.sumOf { it.size }
            if (total > 0) {
                Text(
                    "${sizeText(total)} en esta entrada. La bóveda admite ${MAX_ATTACHMENTS_TOTAL_BYTES / (1024 * 1024)} MB en total.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(enabled = !busy, onClick = {
                VaultLock.keepOpenOnce = true
                store.touch() // The time away is counted from here (AWAY_BACKSTOP_MS with «Al salir»).
                pick.launchOrError(arrayOf("*/*"))?.let { failure ->
                    VaultLock.keepOpenOnce = false // The app never left: leaving later must lock.
                    message = false to failure
                }
            }) { Text(if (busy) "Un momento…" else "Añadir adjunto") }
            message?.let { (ok, text) -> Text(text, color = if (ok) MaterialTheme.colorScheme.primary else NegativeColor) }
        }
    }

    removing?.let { ref ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("¿Quitar «${safeFileName(ref.name)}»?") },
            text = {
                FilterObscuredTouches()
                Text("Se borrará de la bóveda. No se puede deshacer.")
            },
            confirmButton = {
                TextButton(onClick = {
                    removing = null
                    busy = true
                    scope.launch {
                        message = try {
                            if (store.removeAttachment(entryId, ref.id)) true to "Adjunto quitado." else false to "La bóveda se bloqueó: no se ha quitado."
                        } catch (e: Exception) {
                            false to "No se pudo quitar (¿almacenamiento lleno?)"
                        } finally {
                            busy = false
                        }
                    }
                }) { Text("Quitar", color = NegativeColor) }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancelar") } },
        )
    }
}
