package app.seguridad.ui.screens

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.seguridad.android.BiometricVault
import app.seguridad.android.FilterObscuredTouches
import app.seguridad.crypto.CryptoFailure
import app.seguridad.crypto.newPasswordFailureMessage
import app.seguridad.crypto.totpCode
import app.seguridad.tools.RESET_WIPES_DAMAGED
import app.seguridad.tools.VAULT_CREATE_INTRO
import app.seguridad.ui.theme.NegativeColor
import app.seguridad.ui.theme.PasswordField
import app.seguridad.vault.BiometricRefusal
import app.seguridad.vault.CLOCK_CHANGED_TEXT
import app.seguridad.vault.EntryFilter
import app.seguridad.vault.EntryType
import app.seguridad.vault.VAULT_ENTRY_TYPES
import app.seguridad.vault.VaultEntry
import app.seguridad.vault.VaultState
import app.seguridad.vault.VaultStore
import app.seguridad.vault.launchUnlockWithKey
import app.seguridad.vault.query
import app.seguridad.vault.seedWordCount
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal tailrec fun Context.fragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.fragmentActivity()
    else -> null
}

/** Any touch on the vault counts as use for the idle lock. */
internal fun Modifier.touchesVault(store: VaultStore) = pointerInput(store) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent(PointerEventPass.Initial)
            store.touch()
        }
    }
}

/**
 * Checked as a vault screen opens, before its first frame: time spent elsewhere in the app counts as idle.
 * The periodic check runs app-wide ([VaultIdleTicker]), so the vault also locks while the user is on other tabs.
 */
@Composable
fun VaultIdleLock(store: VaultStore) {
    remember(store) { store.lockIfIdle() }
}

/** Locks the vault after the time chosen in Ajustes without use, wherever the user is in the app (every 15 s). */
@Composable
fun VaultIdleTicker(store: VaultStore) {
    LaunchedEffect(store) {
        while (true) {
            store.lockIfIdle()
            delay(15_000)
        }
    }
}

/** The vault (or only its 2FA codes when [totpOnly]): the Bóveda and Códigos tabs. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(store: VaultStore, totpOnly: Boolean, onOpen: (String) -> Unit, onNew: (EntryType) -> Unit, onHealth: () -> Unit) {
    VaultIdleLock(store)
    val state by store.state.collectAsStateWithLifecycle()
    LaunchedEffect(store) { store.load() }
    val snackbar = remember { SnackbarHostState() }
    var menu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val actions = vaultActions(store, snackbar)
    // M-4: «Activar huella» cut short by a re-creation of the activity is said here, by the new one, once.
    val interrupted by BiometricVault.interrupted.collectAsStateWithLifecycle()
    interrupted?.let { message ->
        LaunchedEffect(message) {
            BiometricVault.interruptionShown(message)
            snackbar.showSnackbar(message)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(if (totpOnly) "Códigos 2FA" else "Bóveda") },
                actions = {
                    if (state is VaultState.Unlocked) {
                        TextButton(onClick = { store.lock() }) { Text("Bloquear") }
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Más opciones") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            FilterObscuredTouches() // The menu is its own window: no taps through an overlay on top of it.
                            val bio = BiometricVault.enabled(context)
                            if (BiometricVault.available(context)) {
                                DropdownMenuItem(text = { Text(if (bio) "Desactivar huella" else "Activar huella") }, onClick = {
                                    menu = false
                                    if (bio) {
                                        BiometricVault.disable(context)
                                        scope.launch { snackbar.showSnackbar("Huella desactivada") }
                                    } else {
                                        val activity = context.fragmentActivity() ?: return@DropdownMenuItem
                                        val key = store.dataKeyCopy() ?: return@DropdownMenuItem
                                        BiometricVault.enable(activity, key) { err -> scope.launch { snackbar.showSnackbar(err ?: "Huella activada") } }
                                    }
                                })
                            }
                            DropdownMenuItem(text = { Text("🩺 Salud de la bóveda") }, onClick = {
                                menu = false
                                onHealth()
                            })
                            DropdownMenuItem(text = { Text("Exportar copia cifrada") }, onClick = {
                                menu = false
                                actions.export()
                            })
                            DropdownMenuItem(text = { Text("Importar copia") }, onClick = {
                                menu = false
                                actions.import()
                            })
                            DropdownMenuItem(text = { Text("Cambiar contraseña maestra") }, onClick = {
                                menu = false
                                actions.changeMaster()
                            })
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (state is VaultState.Unlocked) {
                ExtendedFloatingActionButton(
                    onClick = { onNew(if (totpOnly) EntryType.TOTP else EntryType.LOGIN) },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(if (totpOnly) "Código" else "Añadir") },
                )
            }
        },
    ) { padding ->
        VaultGate(store, onImport = actions.import, modifier = Modifier.padding(padding)) { entries -> EntryList(entries, totpOnly, onOpen) }
    }
}

/**
 * What every screen that needs the open vault shows until it is: a spinner while loading, «Crea tu bóveda» with no vault
 * and «Bóveda bloqueada» when locked. Only with the vault open does it draw [content], with the entries; when the vault
 * locks, [content] leaves the composition at once, so no data stays on screen. Any touch counts as use for the idle lock.
 * [onImport] is the «Importar una copia exportada» button of the creation screen (see [vaultActions]).
 */
@Composable
fun VaultGate(store: VaultStore, onImport: () -> Unit, modifier: Modifier = Modifier, content: @Composable (List<VaultEntry>) -> Unit) {
    val state by store.state.collectAsStateWithLifecycle()
    Box(modifier.fillMaxSize().touchesVault(store)) {
        when (val s = state) {
            VaultState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            VaultState.NoVault -> CreateVault(store, onImport = onImport)
            VaultState.Locked -> UnlockVault(store)
            is VaultState.Unlocked -> content(s.entries)
        }
    }
}

@Composable
private fun CreateVault(store: VaultStore, onImport: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var a by remember { mutableStateOf("") }
    var b by remember { mutableStateOf("") }
    var understood by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    // Review F5, M2: a password that is not valid text, or no memory for Argon2id, says so; anything else, «Inténtalo de nuevo».
    var failed by remember { mutableStateOf<String?>(null) }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Crea tu bóveda", style = MaterialTheme.typography.headlineSmall)
        failed?.let { Text(it, color = NegativeColor) }
        Text(
            VAULT_CREATE_INTRO,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PasswordField(a, { a = it }, label = "Contraseña maestra (mínimo $MIN_MASTER caracteres)")
        PasswordField(b, { b = it }, label = "Repítela")
        if (b.isNotEmpty() && a != b) Text("No coinciden", color = NegativeColor)
        Text(
            "Consejo: usa una frase de 5 o 6 palabras que solo tú conozcas (el Generador te puede dar una).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { understood = !understood }) {
            Checkbox(checked = understood, onCheckedChange = { understood = it })
            Text("Entiendo que si olvido la contraseña maestra nadie puede recuperar lo que guarde.")
        }
        Button(
            enabled = !busy && a.length >= MIN_MASTER && a == b && understood,
            onClick = {
                busy = true
                failed = null
                val pw = a.toCharArray()
                scope.launch {
                    try {
                        // A fingerprint left from a previous (deleted or damaged) vault wraps the wrong key.
                        BiometricVault.disable(context)
                        store.create(pw)
                    } catch (e: Exception) {
                        failed = newPasswordFailureMessage(e, "No se pudo crear la bóveda. Inténtalo de nuevo.")
                    } finally {
                        pw.fill('\u0000')
                        busy = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Crear bóveda") }
        if (busy) CircularProgressIndicator(Modifier.size(24.dp))
        OutlinedButton(onClick = onImport, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Importar una copia exportada") }
        Text(
            "Sirve una copia .sgbak de esta app (con los adjuntos), una exportación .json de la 1.0 o una criptopanel-vault (se vuelve a cifrar con el formato nuevo).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun UnlockVault(store: VaultStore) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pw by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var confirmDestroy by remember { mutableStateOf(false) }
    val bio = BiometricVault.enabled(context) && store.biometricAllowed
    fun withFingerprint() {
        val activity = context.fragmentActivity() ?: return
        // The key is handed over even if this screen's scope is gone (a rotation): opened or wiped, never dropped (BOV-3);
        // only the store's own texts are shown, never a raw exception (LIBRE-4).
        BiometricVault.unlock(activity, onKey = { key ->
            scope.launchUnlockWithKey(store, key) { error = it }
        }, onError = { error = it })
    }
    LaunchedEffect(Unit) { if (bio) withFingerprint() }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("🔒 Bóveda bloqueada", style = MaterialTheme.typography.headlineSmall)
        PasswordField(pw, { pw = it; error = null }, label = "Contraseña maestra")
        Button(
            enabled = !busy && pw.isNotEmpty(),
            onClick = {
                busy = true
                val chars = pw.toCharArray()
                scope.launch {
                    try {
                        store.unlock(chars)
                        pw = ""
                    } catch (e: CryptoFailure) {
                        error = "Contraseña incorrecta"
                    } catch (e: Exception) {
                        error = "No se pudo abrir la bóveda"
                    } finally {
                        chars.fill('\u0000')
                        busy = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Desbloquear") }
        if (busy) CircularProgressIndicator(Modifier.size(24.dp))
        if (bio) OutlinedButton(onClick = ::withFingerprint, modifier = Modifier.fillMaxWidth()) { Text("Usar huella") }
        if (BiometricVault.enabled(context)) {
            when (store.biometricRefusal) {
                BiometricRefusal.CLOCK -> Text(CLOCK_CHANGED_TEXT, style = MaterialTheme.typography.bodySmall)
                BiometricRefusal.MASTER_DUE ->
                    Text("Por seguridad, cada 7 días hay que escribir la contraseña maestra.", style = MaterialTheme.typography.bodySmall)
                null -> Unit
            }
        }
        error?.let { Text(it, color = NegativeColor) }
        TextButton(onClick = { confirmDestroy = true }) { Text("He olvidado la contraseña maestra") }
    }
    if (confirmDestroy) {
        AlertDialog(
            onDismissRequest = { confirmDestroy = false },
            title = { Text("Sin la contraseña no hay recuperación") },
            text = {
                FilterObscuredTouches()
                Text(RESET_WIPES_DAMAGED)
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDestroy = false
                    BiometricVault.disable(context)
                    scope.launch { store.destroy() }
                }) { Text("Borrar bóveda", color = NegativeColor) }
            },
            dismissButton = { TextButton(onClick = { confirmDestroy = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun EntryList(allEntries: List<VaultEntry>, totpOnly: Boolean, onOpen: (String) -> Unit) {
    // Identity and contacts have their own tab (and no chip or row here): the list only shows what the vault tab always did.
    val entries = remember(allEntries) { allEntries.filterNot { it.type.isPersonal } }
    var query by remember { mutableStateOf("") }
    var type by remember { mutableStateOf<EntryType?>(if (totpOnly) EntryType.TOTP else null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    var tag by remember { mutableStateOf<String?>(null) }
    val allTags = remember(entries) { entries.flatMap { it.tags }.distinct().sorted() }
    // A tag no entry has any more (deleted or renamed) must not keep filtering the list.
    val activeTag = tag?.takeIf { it in allTags }
    val shown = remember(entries, query, type, activeTag) { entries.query(EntryFilter(query, activeTag, type)) }
    LazyColumn(contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item {
            OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Buscar") }, singleLine = true,
                // No corrections or suggestions. autoCorrectEnabled = false does not stop the keyboard from learning: that
                // is NoLearningInput (IME_FLAG_NO_PERSONALIZED_LEARNING, for every field of the app).
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth())
        }
        if (!totpOnly) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    FilterChip(selected = type == null, onClick = { type = null }, label = { Text("Todo (${entries.size})") })
                    VAULT_ENTRY_TYPES.forEach { t ->
                        FilterChip(selected = type == t, onClick = { type = t }, label = { Text("${t.label} (${entries.count { it.type == t }})") })
                    }
                }
            }
        }
        if (allTags.isNotEmpty()) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    allTags.forEach { t ->
                        FilterChip(selected = activeTag == t, onClick = { tag = if (activeTag == t) null else t }, label = { Text("#$t") })
                    }
                }
            }
        }
        if (shown.isEmpty()) {
            item {
                Text(
                    if (entries.isEmpty()) "Tu bóveda está vacía. Pulsa «Añadir»." else "Sin resultados",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
        items(shown, key = { it.id }) { e ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().clickable { onOpen(e.id) }.padding(vertical = 10.dp),
            ) {
                Text(typeIcon(e.type), style = MaterialTheme.typography.titleLarge)
                Column(Modifier.weight(1f)) {
                    Text(e.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val sub = when (e.type) {
                        EntryType.LOGIN -> e.username.ifBlank { e.url }
                        EntryType.SEED -> "${seedWordCount(e.seedWords).first} palabras"
                        EntryType.NOTE -> "Nota"
                        EntryType.TOTP -> listOf(e.totp?.issuer, e.totp?.account).filter { !it.isNullOrBlank() }.joinToString(" · ")
                        EntryType.IDENTITY, EntryType.CONTACT -> "" // Never listed here.
                    }
                    if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                if (e.favorite) Text("★", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                e.totp?.let { t ->
                    totpCode(t, now)?.let { (code, left) ->
                        Column(horizontalAlignment = Alignment.End) {
                            Text(code.chunked(3).joinToString(" "), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.titleMedium)
                            Text("${left}s", style = MaterialTheme.typography.labelSmall, color = if (left <= 5) NegativeColor else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            HorizontalDivider()
        }
    }
}

fun typeIcon(t: EntryType) = when (t) {
    EntryType.LOGIN -> "🔑"
    EntryType.SEED -> "🌱"
    EntryType.NOTE -> "📝"
    EntryType.TOTP -> "⏱"
    EntryType.IDENTITY -> "🪪"
    EntryType.CONTACT -> "👤"
}
