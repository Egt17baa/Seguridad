package app.seguridad.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.seguridad.android.FilterObscuredTouches
import app.seguridad.imports.plainReportText
import app.seguridad.ui.theme.BackScaffold
import app.seguridad.ui.theme.NegativeColor
import app.seguridad.ui.theme.SectionCard
import app.seguridad.vault.EntryType
import app.seguridad.vault.EntryVersion
import app.seguridad.vault.FieldKind
import app.seguridad.vault.HISTORY_CLEAR
import app.seguridad.vault.HISTORY_CLEARED
import app.seguridad.vault.HISTORY_CLEAR_TEXT
import app.seguridad.vault.HISTORY_EMPTY
import app.seguridad.vault.HISTORY_MORE
import app.seguridad.vault.HISTORY_RESTORE
import app.seguridad.vault.HISTORY_RESTORED
import app.seguridad.vault.HISTORY_SHORT
import app.seguridad.vault.HISTORY_TITLE
import app.seguridad.vault.TRASH_SAVE_FAILED
import app.seguridad.vault.VaultState
import app.seguridad.vault.VaultStore
import app.seguridad.vault.historyClearTitle
import app.seguridad.vault.versionUntil
import kotlinx.coroutines.launch

/**
 * 2.0, task 3: the versions of one entry (route `vault/entry/{id}/history`), newest first, each with the date it was
 * replaced; its fields as on the entry's card, the passwords and hidden fields hidden until shown, copied as sensitive.
 * «Restaurar esta versión» (a new save, which keeps what was there as another version) and «Borrar historial» (confirmed;
 * O-2: on view, that the copies already made keep it). Nothing of it is in saved state; it leaves when the vault locks
 * or the entry is no longer out of the trash.
 */
@Composable
fun EntryHistoryScreen(store: VaultStore, id: String, onBack: () -> Unit) {
    VaultIdleLock(store)
    val state by store.state.collectAsStateWithLifecycle()
    val entry = (state as? VaultState.Unlocked)?.entries?.firstOrNull { it.id == id }
    if (entry == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var clearing by remember { mutableStateOf(false) }
    val hasAny = entry.history.isNotEmpty() || entry.passwordHistory.isNotEmpty()
    BackScaffold("$HISTORY_TITLE · ${plainReportText(entry.title)}", onBack) { padding ->
        LazyColumn(Modifier.padding(padding).touchesVault(store).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(HISTORY_SHORT)
                    MoreInfo { Text(HISTORY_MORE, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    message?.let { (text, bad) -> Text(text, color = if (bad) NegativeColor else MaterialTheme.colorScheme.primary) }
                    if (entry.history.isEmpty()) Text(HISTORY_EMPTY, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 12.dp))
                    if (hasAny) OutlinedButton(onClick = { clearing = true }, modifier = Modifier.fillMaxWidth()) { Text(HISTORY_CLEAR, color = NegativeColor) }
                }
            }
            itemsIndexed(entry.history, key = { i, v -> "${v.savedAt}-$i" }) { _, v ->
                VersionCard(v) {
                    message = null
                    scope.launch {
                        message = try {
                            if (store.restoreVersion(entry.id, v.savedAt)) HISTORY_RESTORED to false else null
                        } catch (e: Exception) {
                            (e.message?.takeIf { e is app.seguridad.vault.VaultLimitExceeded } ?: TRASH_SAVE_FAILED) to true
                        }
                    }
                }
            }
        }
    }
    if (clearing) {
        AlertDialog(
            onDismissRequest = { clearing = false },
            title = { Text(historyClearTitle(plainReportText(entry.title))) },
            text = {
                FilterObscuredTouches()
                Text(HISTORY_CLEAR_TEXT)
            },
            confirmButton = {
                TextButton(onClick = {
                    clearing = false
                    scope.launch {
                        message = try {
                            if (store.clearHistory(entry.id)) HISTORY_CLEARED to false else null
                        } catch (e: Exception) {
                            TRASH_SAVE_FAILED to true
                        }
                    }
                }) { Text(HISTORY_CLEAR, color = NegativeColor) }
            },
            dismissButton = { TextButton(onClick = { clearing = false }) { Text("Cancelar") } },
        )
    }
}

/** One version: its date, folded; open, its fields as on the card (secrets hidden until shown). */
@Composable
private fun VersionCard(v: EntryVersion, onRestore: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    val e = v.entry
    val date = java.time.Instant.ofEpochMilli(v.savedAt).atZone(java.time.ZoneId.systemDefault()).format(historyDate)
    SectionCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(versionUntil(date), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${typeIcon(e.type)} ${plainReportText(e.title)}")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { open = !open }) { Text(if (open) "Ocultar campos" else "Ver campos") }
                FilledTonalButton(onClick = onRestore) { Text(HISTORY_RESTORE) }
            }
            if (open) {
                Field("Usuario", e.username, plain = true)
                Field(if (e.type == EntryType.SEED) "Passphrase BIP-39" else "Contraseña", e.password, secret = true)
                Field("Web", e.url, plain = true)
                Field("Frase semilla", e.seedWords, secret = true)
                Field("Notas", e.notes, secret = e.type == EntryType.NOTE)
                e.totp?.let { Field("Clave secreta 2FA", it.secret, secret = true, mono = true) }
                e.fields.forEach { f -> Field(plainReportText(f.name), f.value, secret = f.kind == FieldKind.HIDDEN, plain = f.kind != FieldKind.HIDDEN) }
                if (e.tags.isNotEmpty()) Text(e.tags.joinToString("  ") { "#${plainReportText(it)}" }, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
