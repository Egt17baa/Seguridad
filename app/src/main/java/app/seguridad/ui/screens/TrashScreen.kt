package app.seguridad.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import app.seguridad.ui.theme.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.seguridad.android.FilterObscuredTouches
import app.seguridad.imports.plainReportText
import app.seguridad.ui.theme.BackScaffold
import app.seguridad.ui.theme.NegativeColor
import app.seguridad.ui.theme.SectionCard
import app.seguridad.vault.COPIES_KEEP_IT
import app.seguridad.vault.TRASH_DELETE_FOREVER
import app.seguridad.vault.TRASH_DELETE_FOREVER_TEXT
import app.seguridad.vault.TRASH_EMPTY
import app.seguridad.vault.TRASH_EMPTY_LIST
import app.seguridad.vault.TRASH_EMPTY_TITLE
import app.seguridad.vault.TRASH_MORE
import app.seguridad.vault.TRASH_RESTORE
import app.seguridad.vault.TRASH_RESTORE_NONE
import app.seguridad.vault.TRASH_SAVE_FAILED
import app.seguridad.vault.TRASH_SHORT
import app.seguridad.vault.TRASH_TITLE
import app.seguridad.vault.VaultEntry
import app.seguridad.vault.VaultState
import app.seguridad.vault.VaultStore
import app.seguridad.vault.trashDeleteForeverTitle
import app.seguridad.vault.trashEmptyText
import app.seguridad.vault.trashLeavesIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 2.0, task 3: the trash of the open vault (route `vault/trash`): the entries deleted, each with «Restaurar» and «Borrar
 * para siempre» (confirmed), and «Vaciar papelera» (confirmed). Abogado Digital, O-2: on view, that it empties itself on
 * opening the vault after 30 days and that the copies already made keep what they hold. Only titles and types are shown
 * (as plain text), never a secret. It leaves when the vault locks.
 */
@Composable
fun TrashScreen(store: VaultStore, onBack: () -> Unit) {
    VaultIdleLock(store)
    val state by store.state.collectAsStateWithLifecycle()
    val open = state as? VaultState.Unlocked
    if (open == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val trash = remember(open) { open.trash.sortedByDescending { it.deletedAt ?: 0L } }
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var forever by remember { mutableStateOf<VaultEntry?>(null) }
    var emptying by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            now = System.currentTimeMillis()
        }
    }
    fun run(block: suspend () -> Int, none: String? = null) {
        message = null
        scope.launch {
            try {
                if (block() == 0 && none != null) message = none
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                message = TRASH_SAVE_FAILED
            }
        }
    }
    BackScaffold("🗑 $TRASH_TITLE", onBack) { padding ->
        LazyColumn(
            Modifier.padding(padding).touchesVault(store).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(TRASH_SHORT)
                    Text(COPIES_KEEP_IT, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    MoreInfo { Text(TRASH_MORE, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    message?.let { Text(it, color = NegativeColor) }
                    if (trash.isEmpty()) {
                        Text(TRASH_EMPTY_LIST, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 16.dp))
                    } else {
                        OutlinedButton(onClick = { emptying = true }, modifier = Modifier.fillMaxWidth()) { Text(TRASH_EMPTY, color = NegativeColor) }
                    }
                }
            }
            items(trash, key = { it.id }) { e ->
                SectionCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${typeIcon(e.type)} ${plainReportText(e.title)}", maxLines = 2, overflow = TextOverflow.Ellipsis)
                        e.deletedAt?.let {
                            Text(trashLeavesIn(it, now), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(onClick = { run({ store.restore(setOf(e.id)) }, none = TRASH_RESTORE_NONE) }) { Text(TRASH_RESTORE) }
                            TextButton(onClick = { forever = e }) { Text(TRASH_DELETE_FOREVER, color = NegativeColor) }
                        }
                    }
                }
            }
        }
    }
    forever?.let { e ->
        AlertDialog(
            onDismissRequest = { forever = null },
            title = { Text(trashDeleteForeverTitle(plainReportText(e.title))) },
            text = {
                FilterObscuredTouches()
                Text(TRASH_DELETE_FOREVER_TEXT)
            },
            confirmButton = {
                TextButton(onClick = {
                    forever = null
                    run({ store.deleteForever(setOf(e.id)) })
                }) { Text(TRASH_DELETE_FOREVER, color = NegativeColor) }
            },
            dismissButton = { TextButton(onClick = { forever = null }) { Text("Cancelar") } },
        )
    }
    if (emptying) {
        AlertDialog(
            onDismissRequest = { emptying = false },
            title = { Text(TRASH_EMPTY_TITLE) },
            text = {
                FilterObscuredTouches()
                Text(trashEmptyText(trash.size))
            },
            confirmButton = {
                TextButton(onClick = {
                    emptying = false
                    run({ store.emptyTrash() })
                }) { Text(TRASH_EMPTY, color = NegativeColor) }
            },
            dismissButton = { TextButton(onClick = { emptying = false }) { Text("Cancelar") } },
        )
    }
}
