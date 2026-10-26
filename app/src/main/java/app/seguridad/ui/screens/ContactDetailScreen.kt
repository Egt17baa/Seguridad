package app.seguridad.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.seguridad.android.FilterObscuredTouches
import app.seguridad.ui.theme.CautionColor
import app.seguridad.ui.theme.NegativeColor
import app.seguridad.vault.ContactName
import app.seguridad.vault.EntryType
import app.seguridad.vault.FINGERPRINT_HELP
import app.seguridad.vault.SIMILAR_NAME_WARNING
import app.seguridad.vault.UNVERIFIED_NOTICE
import app.seguridad.vault.VERIFY_QUESTION
import app.seguridad.vault.VaultEntry
import app.seguridad.vault.VaultStore
import app.seguridad.vault.cardFingerprint
import app.seguridad.vault.checkContactName
import app.seguridad.vault.contactCard
import app.seguridad.vault.markVerified
import app.seguridad.vault.renamedContact
import app.seguridad.vault.saveFailureMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The fingerprint of a saved card: being worked out, or the stored card no longer reads. */
private sealed interface SavedFingerprint {
    data object Loading : SavedFingerprint

    data object Unreadable : SavedFingerprint

    class Ready(val value: String) : SavedFingerprint
}

/**
 * A contact (`contacts/{id}`): its saved name, the fingerprint to compare, «Marcar como verificado», rename and delete.
 * The name is the user's own and drawn as plain text. If the contact no longer exists, it goes back.
 */
@Composable
fun ContactDetailScreen(store: VaultStore, id: String, onBack: () -> Unit) {
    ContactsFrame(store, "Contacto", onBack) { entries, snackbar ->
        val entry = entries.firstOrNull { it.id == id && it.type == EntryType.CONTACT }
        if (entry == null) {
            LaunchedEffect(Unit) { onBack() }
        } else {
            ContactDetail(store, entry, snackbar)
        }
    }
}

@Composable
private fun ContactDetail(store: VaultStore, entry: VaultEntry, snackbar: SnackbarHostState) {
    val scope = rememberCoroutineScope()
    var dialog by remember { mutableIntStateOf(NONE) }
    var busy by remember { mutableStateOf(false) }
    // The card is read again (signature included) off the main thread; a card that no longer reads is not shown.
    val fingerprint by produceState<SavedFingerprint>(SavedFingerprint.Loading, entry.card) {
        value = withContext(Dispatchers.Default) {
            try {
                SavedFingerprint.Ready(cardFingerprint(entry.contactCard()))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                SavedFingerprint.Unreadable
            }
        }
    }

    /** Saves [updated] in place of the contact and says why not if it did not work. */
    fun save(updated: VaultEntry) {
        busy = true
        scope.launch {
            saveFailureMessage(attempt { store.upsert(updated) })?.let { snackbar.showSnackbar(it) }
            busy = false
        }
    }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Plain Text: whatever the name holds is shown as it is, never as a link or markdown.
        Text(entry.title, style = MaterialTheme.typography.headlineSmall)
        VerificationMark(entry)
        when (val f = fingerprint) {
            SavedFingerprint.Loading -> Unit
            SavedFingerprint.Unreadable -> Text(
                "La tarjeta guardada de este contacto ya no se puede leer. Puedes borrarlo y volver a añadirlo.",
                color = NegativeColor,
            )
            is SavedFingerprint.Ready -> {
                FingerprintBlock("Su huella", f.value, Modifier.fillMaxWidth())
                Text(FINGERPRINT_HELP, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (!entry.verified) {
            Text(UNVERIFIED_NOTICE, color = CautionColor)
            Text(SIMILAR_NAME_WARNING, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (fingerprint is SavedFingerprint.Ready) {
                Button(onClick = { dialog = VERIFY }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Marcar como verificado") }
            }
        }
        OutlinedButton(onClick = { dialog = RENAME }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Cambiar el nombre") }
        OutlinedButton(onClick = { dialog = DELETE }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text("Borrar contacto", color = NegativeColor)
        }
    }

    when (dialog) {
        VERIFY -> AlertDialog(
            onDismissRequest = { dialog = NONE },
            title = { Text("¿Coincide la huella?") },
            text = {
                FilterObscuredTouches()
                Text(VERIFY_QUESTION)
            },
            confirmButton = {
                TextButton(onClick = {
                    dialog = NONE
                    save(markVerified(entry, System.currentTimeMillis()))
                }) { Text("Sí, coincide") }
            },
            dismissButton = { TextButton(onClick = { dialog = NONE }) { Text("Cancelar") } },
        )
        RENAME -> RenameDialog(
            current = entry.title,
            onDismiss = { dialog = NONE },
            onConfirm = { name ->
                dialog = NONE
                save(renamedContact(entry, name, System.currentTimeMillis()))
            },
        )
        DELETE -> AlertDialog(
            onDismissRequest = { dialog = NONE },
            title = { Text("Borrar contacto") },
            text = {
                FilterObscuredTouches()
                Text("Se borrará de esta bóveda. No podrás cifrarle mensajes ni reconocer sus firmas hasta que lo vuelvas a añadir.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        dialog = NONE
                        busy = true
                        scope.launch {
                            // Deleted: the contact is gone from the list and the screen goes back by itself (once).
                            saveFailureMessage(attempt { store.delete(entry.id) })?.let { snackbar.showSnackbar(it) }
                            busy = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NegativeColor, contentColor = MaterialTheme.colorScheme.surface),
                ) { Text("Borrar") }
            },
            dismissButton = { TextButton(onClick = { dialog = NONE }) { Text("Cancelar") } },
        )
    }
}

private const val NONE = 0
private const val VERIFY = 1
private const val RENAME = 2
private const val DELETE = 3

/** Asks for the new name and says at once why one does not fit (control or invisible characters, over 64 bytes). */
@Composable
private fun RenameDialog(current: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(current) }
    val check = checkContactName(name)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cambiar el nombre") },
        text = {
            FilterObscuredTouches()
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nombre del contacto") },
                singleLine = true,
                isError = check is ContactName.Invalid,
                supportingText = { (check as? ContactName.Invalid)?.let { Text(it.message) } },
            )
        },
        confirmButton = {
            TextButton(enabled = check is ContactName.Valid, onClick = { onConfirm(name) }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
