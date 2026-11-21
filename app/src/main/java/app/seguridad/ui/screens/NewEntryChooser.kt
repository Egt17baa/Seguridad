package app.seguridad.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.seguridad.android.FilterObscuredTouches
import app.seguridad.ui.theme.AlertDialog
import app.seguridad.vault.CANCEL
import app.seguridad.vault.EntryType
import app.seguridad.vault.NEW_ENTRY_CHOICES
import app.seguridad.vault.NEW_ENTRY_CHOOSE
import app.seguridad.vault.NEW_ENTRY_OTHER_LINE
import app.seguridad.vault.NewEntryChoice

/**
 * 2.0, task 7 (design «Entradas»): the «+» of the vault, and «Crear la primera» of the empty vault, ask what to keep:
 * [NEW_ENTRY_CHOICES], in their order, each with the icon of its type. «Otro» is an empty «Acceso», where the user adds
 * the fields. Nothing secret is here: only the choice, which goes to the form as its type.
 */
@Composable
fun NewEntryChooser(onDismiss: () -> Unit, onPick: (EntryType) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(NEW_ENTRY_CHOOSE) },
        text = {
            FilterObscuredTouches() // A dialog is its own window: no taps through an overlay on top of it.
            Column(Modifier.verticalScroll(rememberScrollState())) {
                NEW_ENTRY_CHOICES.forEach { choice ->
                    TextButton(onClick = { onPick(choice.type) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(2.dp), horizontalAlignment = Alignment.Start) {
                            // The icon is decoration (review M-8): TalkBack reads the name only.
                            DecoratedLabel(if (choice == NewEntryChoice.OTHER) "➕" else typeIcon(choice.type), choice.label)
                            if (choice == NewEntryChoice.OTHER) {
                                Text(NEW_ENTRY_OTHER_LINE, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(CANCEL) } },
    )
}
