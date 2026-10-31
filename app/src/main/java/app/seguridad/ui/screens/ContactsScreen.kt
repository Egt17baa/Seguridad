package app.seguridad.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.seguridad.ui.theme.CautionColor
import app.seguridad.ui.theme.NegativeColor
import app.seguridad.ui.theme.PasswordField
import app.seguridad.ui.theme.PositiveColor
import app.seguridad.ui.theme.SectionCard
import app.seguridad.vault.EntryType
import app.seguridad.vault.NEEDS_MASTER_NOTICE
import app.seguridad.vault.VAULT_CLOSED
import app.seguridad.vault.VaultEntry
import app.seguridad.vault.VaultState
import app.seguridad.vault.VaultStore
import app.seguridad.vault.contactsList
import app.seguridad.vault.fingerprintRows
import app.seguridad.vault.identity
import app.seguridad.vault.masterFailureMessage
import app.seguridad.vault.verificationMark
import app.seguridad.vault.shownContactTitle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Where the three actions of the Contactos tab lead; their screens are `contacts/…`. */
internal const val ROUTE_ENCRYPT = "contacts/encrypt"
internal const val ROUTE_SIGN = "contacts/sign"
internal const val ROUTE_OPEN = "contacts/open"

/**
 * The frame of every `contacts/…` screen: top bar (with «Bloquear» while open), snackbar, and the vault gate. [content]
 * is drawn only with the vault open and, if it was opened with the fingerprint (version 2), after the master password was
 * typed once ([MasterPrompt]): until then no identity or contact can be saved. When the vault locks with a screen open,
 * [content] leaves the composition at once: the gate shows, never data. Any touch counts as use for the idle lock.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ContactsFrame(
    store: VaultStore,
    title: String,
    onBack: (() -> Unit)?,
    floatingActionButton: (@Composable () -> Unit)? = null,
    content: @Composable (entries: List<VaultEntry>, snackbar: SnackbarHostState) -> Unit,
) {
    VaultIdleLock(store)
    val state by store.state.collectAsStateWithLifecycle()
    val needsMaster by store.needsMasterForContacts.collectAsStateWithLifecycle()
    // The Bóveda tab loads it too; a restored back stack can open this screen first.
    LaunchedEffect(store) { store.load() }
    val snackbar = remember { SnackbarHostState() }
    val actions = vaultActions(store, snackbar)

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás") }
                },
                actions = { if (state is VaultState.Unlocked) TextButton(onClick = { store.lock() }) { Text("Bloquear") } },
            )
        },
        floatingActionButton = { if (floatingActionButton != null && state is VaultState.Unlocked && !needsMaster) floatingActionButton() },
    ) { padding ->
        VaultGate(store, onImport = actions.import, modifier = Modifier.padding(padding)) { entries ->
            if (needsMaster) MasterPrompt(store) else content(entries, snackbar)
        }
    }
}

/**
 * The vault is open but still version 2 (it was opened with the fingerprint): one master password check wraps it as
 * version 3 and the screens show up. A wrong password says what the unlock screen says; the characters are wiped after.
 */
@Composable
private fun MasterPrompt(store: VaultStore) {
    val scope = rememberCoroutineScope()
    var pw by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Contraseña maestra", style = MaterialTheme.typography.headlineSmall)
        Text(NEEDS_MASTER_NOTICE)
        PasswordField(pw, { pw = it; error = null }, label = "Contraseña maestra")
        Button(
            enabled = !busy && pw.isNotEmpty(),
            onClick = {
                busy = true
                val chars = pw.toCharArray()
                scope.launch {
                    try {
                        // False: the vault locked meanwhile (the unlock screen is what shows then).
                        if (store.confirmMaster(chars)) pw = "" else error = VAULT_CLOSED
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        error = masterFailureMessage(e)
                    } finally {
                        chars.fill('\u0000')
                        busy = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Confirmar") }
        if (busy) CircularProgressIndicator(Modifier.size(24.dp))
        error?.let { Text(it, color = NegativeColor) }
    }
}

/** Runs [block] and returns what it gave or threw (leaving the screen still cancels it). */
internal suspend fun <T> attempt(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}

/** A fingerprint in two rows of three groups of five digits, with its [label] above. */
@Composable
internal fun FingerprintBlock(label: String, fingerprint: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        fingerprintRows(fingerprint).forEach { Text(it, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.titleLarge) }
    }
}

/** The Contactos tab: my identity, the three actions and the list of contacts. */
@Composable
fun ContactsScreen(store: VaultStore, onIdentity: () -> Unit, onAdd: () -> Unit, onContact: (String) -> Unit, onAction: (String) -> Unit) {
    ContactsFrame(
        store,
        title = "Contactos",
        onBack = null,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Añadir contacto") },
            )
        },
    ) { entries, _ -> ContactsList(entries, onIdentity, onContact, onAction) }
}

@Composable
private fun ContactsList(entries: List<VaultEntry>, onIdentity: () -> Unit, onContact: (String) -> Unit, onAction: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    val hasIdentity = remember(entries) { entries.identity() != null }
    val total = remember(entries) { entries.count { it.type == EntryType.CONTACT } }
    val shown = remember(entries, query) { contactsList(entries, query) }
    LazyColumn(
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            SectionCard(Modifier.fillMaxWidth(), onClick = onIdentity) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(typeIcon(EntryType.IDENTITY), style = MaterialTheme.typography.titleLarge)
                    Column(Modifier.weight(1f)) {
                        Text("Mi identidad", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (hasIdentity) "Ver mi QR y mi huella, y compartir mi tarjeta" else "Todavía no la has creado: tócala para crearla",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { onAction(ROUTE_ENCRYPT) }, modifier = Modifier.fillMaxWidth()) { Text("Cifrar para…") }
                OutlinedButton(onClick = { onAction(ROUTE_SIGN) }, modifier = Modifier.fillMaxWidth()) { Text("Firmar") }
                OutlinedButton(onClick = { onAction(ROUTE_OPEN) }, modifier = Modifier.fillMaxWidth()) { Text("Abrir lo recibido") }
            }
        }
        if (total > 0) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Buscar contacto") },
                    singleLine = true,
                    // No corrections or suggestions. autoCorrectEnabled = false does not stop the keyboard from learning:
                    // that is NoLearningInput (IME_FLAG_NO_PERSONALIZED_LEARNING, for every field of the app).
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (shown.isEmpty()) {
            item {
                Text(
                    if (total == 0) "Todavía no tienes contactos. Pulsa «Añadir contacto»." else "Sin resultados",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
        items(shown, key = { it.id }) { e ->
            // The saved name is the user's own, and still plain text: Text never reads links or markdown.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().clickable { onContact(e.id) }.padding(vertical = 10.dp),
            ) {
                Text(typeIcon(EntryType.CONTACT), style = MaterialTheme.typography.titleLarge)
                Column(Modifier.weight(1f)) {
                    Text(shownContactTitle(e.title), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    VerificationMark(e)
                }
            }
            HorizontalDivider()
        }
    }
}

/** «sin verificar» (amber: it matters) or «✓ verificado con …» under a contact's name. */
@Composable
internal fun VerificationMark(entry: VaultEntry) {
    if (entry.verified) {
        Text("✓ " + verificationMark(entry), style = MaterialTheme.typography.bodySmall, color = PositiveColor)
    } else {
        Text(verificationMark(entry), style = MaterialTheme.typography.bodySmall, color = CautionColor)
    }
}
