package app.seguridad.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import app.seguridad.ui.theme.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import app.seguridad.ui.theme.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import app.seguridad.ui.theme.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.seguridad.android.BiometricVault
import app.seguridad.android.FilterObscuredTouches
import app.seguridad.crypto.CryptoFailure
import app.seguridad.crypto.newPasswordFailureMessage
import app.seguridad.imports.IMPORT_TITLE
import app.seguridad.tools.RESET_WIPES_DAMAGED
import app.seguridad.tools.VAULT_CREATE_INTRO
import app.seguridad.tools.VAULT_CREATE_SHORT
import app.seguridad.ui.theme.NegativeColor
import app.seguridad.ui.theme.PasswordField
import app.seguridad.vault.BiometricRefusal
import app.seguridad.vault.CLOCK_CHANGED_TEXT
import app.seguridad.vault.EntryType
import app.seguridad.vault.MASTER_TOO_WEAK
import app.seguridad.vault.MIN_MASTER
import app.seguridad.vault.NOT_A_VAULT
import app.seguridad.vault.UPGRADE_NEEDS_MASTER
import app.seguridad.vault.VaultEntry
import app.seguridad.vault.VaultState
import app.seguridad.vault.VaultStore
import app.seguridad.vault.launchUnlockWithKey
import app.seguridad.vault.newMasterProblem
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import app.seguridad.vault.TRASH_OPEN
import app.seguridad.vault.UNDO
import app.seguridad.vault.UNDO_TRASH_MS
import app.seguridad.vault.UndoTrash
import app.seguridad.vault.movedToTrash
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
fun VaultScreen(
    store: VaultStore,
    undo: UndoTrash,
    totpOnly: Boolean,
    onOpen: (String) -> Unit,
    onNew: (EntryType) -> Unit,
    onHealth: () -> Unit,
    onImportOther: () -> Unit,
    onTrash: () -> Unit,
) {
    VaultIdleLock(store)
    val state by store.state.collectAsStateWithLifecycle()
    LaunchedEffect(store) { store.load() }
    val list = viewModel { EntryListViewModel(store, totpOnly) }
    val snackbar = remember { SnackbarHostState() }
    var menu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val actions = vaultActions(store, snackbar)
    // M-4: «Activar huella» cut short by a re-creation of the activity is said here, by the new one, once.
    val interrupted by BiometricVault.interrupted.collectAsStateWithLifecycle()
    interrupted?.let { message ->
        LaunchedEffect(message) {
            // Seen once shown (F-5): another re-creation while it is on screen cancels this, and the next one shows it.
            // Leaving the list or locking while it is on screen also cancels it: that counts as seen (code review of 1.4.2).
            try {
                snackbar.showSnackbar(message)
            } finally {
                val activity = generateSequence(context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()
                if (activity?.isChangingConfigurations != true) BiometricVault.interruptionShown(message)
            }
        }
    }

    // 2.0, task 3: «Deshacer» for 5 s after a move to the trash; a lock (or leaving the list) ends it, and a lock forgets it.
    val undoCount by undo.offer.collectAsStateWithLifecycle()
    val unlocked = state is VaultState.Unlocked
    LaunchedEffect(undoCount, unlocked) {
        if (undoCount == 0) return@LaunchedEffect
        if (!unlocked) return@LaunchedEffect undo.forget()
        val answer = withTimeoutOrNull(UNDO_TRASH_MS) {
            snackbar.showSnackbar(movedToTrash(undoCount), actionLabel = UNDO, duration = SnackbarDuration.Indefinite)
        }
        if (answer == SnackbarResult.ActionPerformed) {
            try {
                undo.undo()
            } catch (e: Exception) {
                undo.forget() // A full disk: the entries stay in the trash, where «Restaurar» still brings them back.
            }
        } else {
            undo.forget()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                // 2.0, task 2: with the vault open, its name (and the way to another vault) is the title of the tab.
                title = { if (!totpOnly && state is VaultState.Unlocked) LocalVaultSwitcher.current() else Text(if (totpOnly) "Códigos 2FA" else "Bóveda") },
                actions = {
                    if (state is VaultState.Unlocked) {
                        TextButton(onClick = { store.lock() }) { Text("Bloquear") }
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Más opciones") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            FilterObscuredTouches() // The menu is its own window: no taps through an overlay on top of it.
                            val bio = BiometricVault.enabled(context, store.id)
                            if (BiometricVault.available(context)) {
                                DropdownMenuItem(text = { Text(if (bio) "Desactivar huella" else "Activar huella") }, onClick = {
                                    menu = false
                                    if (bio) {
                                        scope.launch {
                                            BiometricVault.disable(context, store.id)
                                            snackbar.showSnackbar("Huella desactivada")
                                        }
                                    } else {
                                        val activity = context.fragmentActivity() ?: return@DropdownMenuItem
                                        val key = store.dataKeyCopy() ?: return@DropdownMenuItem
                                        BiometricVault.enable(activity, store.id, key) { err -> scope.launch { snackbar.showSnackbar(err ?: "Huella activada") } }
                                    }
                                })
                            }
                            DropdownMenuItem(text = { Text("🩺 Salud de la bóveda") }, onClick = {
                                menu = false
                                onHealth()
                            })
                            // 2.0, task 3: the trash, with how many entries it holds.
                            val trashed = (state as? VaultState.Unlocked)?.trash?.size ?: 0
                            DropdownMenuItem(text = { Text("🗑 $TRASH_OPEN ($trashed)") }, onClick = {
                                menu = false
                                onTrash()
                            })
                            DropdownMenuItem(text = { Text("Exportar copia cifrada") }, onClick = {
                                menu = false
                                actions.export()
                            })
                            DropdownMenuItem(text = { Text("Importar copia") }, onClick = {
                                menu = false
                                actions.import()
                            })
                            // 1.4.0: entries from another app's export, added to this vault (it does not replace it).
                            DropdownMenuItem(text = { Text(IMPORT_TITLE) }, onClick = {
                                menu = false
                                onImportOther()
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
        VaultGate(store, onImport = actions.import, modifier = Modifier.padding(padding)) {
            EntryList(store, totpOnly, list, onOpen, onMessage = { text -> scope.launch { snackbar.showSnackbar(text) } })
        }
    }
}

/**
 * What every screen that needs the open vault shows until it is: a spinner while loading, «Crea tu bóveda» with no vault
 * and «Bóveda bloqueada» when locked. Only with the vault open does it draw [content], with the entries (and, 2.0, the
 * dialog of the upgrade to format v4 while [VaultStore.pendingUpgrade]); when the vault
 * locks, [content] leaves the composition at once, so no data stays on screen. Any touch counts as use for the idle lock.
 * [onImport] is the «Importar una copia exportada» button of the creation screen (see [vaultActions]).
 */
@Composable
fun VaultGate(store: VaultStore, onImport: () -> Unit, modifier: Modifier = Modifier, content: @Composable (List<VaultEntry>) -> Unit) {
    val state by store.state.collectAsStateWithLifecycle()
    val upgradePending by store.pendingUpgrade.collectAsStateWithLifecycle()
    val upgradeDone by store.upgradeDone.collectAsStateWithLifecycle()
    Box(modifier.fillMaxSize().touchesVault(store)) {
        when (val s = state) {
            VaultState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            VaultState.NoVault -> CreateVault(store, onImport = onImport)
            VaultState.Locked -> UnlockVault(store)
            is VaultState.Unlocked -> {
                content(s.entries)
                // 2.0: the choice before passing to format v4, over whatever vault screen is open.
                if (upgradePending) {
                    VaultUpgradeDialog(store)
                } else {
                    // O-3 with a folder: once, after the upgrade saved with its automatic copy.
                    upgradeDone?.let { VaultUpgradeDoneNotice(store, it) }
                }
            }
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
    // A single known word is not a master: the lists load off the main thread (null until they are in), once for this
    // screen, and the button of creating waits for them.
    val words = rememberMasterWords()
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LocalVaultSwitcher.current()
        Text("Crea tu bóveda", style = MaterialTheme.typography.headlineSmall)
        failed?.let { Text(it, color = NegativeColor) }
        Text(VAULT_CREATE_SHORT)
        MoreInfo { Text(VAULT_CREATE_INTRO, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        PasswordField(a, { a = it }, label = "Contraseña maestra (mínimo $MIN_MASTER caracteres)")
        // CRY-A01: as soon as there is text, what is missing (length first, then strength, then a single known word); the
        // button waits for null. A polite live region, so TalkBack announces it when it changes.
        val problem = newMasterProblem(a, words ?: emptySet())
        if (a.isNotEmpty()) problem?.let { Text(it, color = NegativeColor, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
        PasswordField(b, { b = it }, label = "Repítela")
        if (b.isNotEmpty() && a != b) Text("No coinciden", color = NegativeColor)
        // MASTER_TOO_WEAK already gives this advice: the fixed hint waits until that text is gone.
        if (problem != MASTER_TOO_WEAK) {
            Text(
                "Consejo: usa una frase de 5 o 6 palabras que solo tú conozcas (el Generador te puede dar una).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { understood = !understood }) {
            Checkbox(checked = understood, onCheckedChange = { understood = it })
            Text("Entiendo que si olvido la contraseña maestra nadie puede recuperar lo que guarde.")
        }
        Button(
            enabled = !busy && words != null && problem == null && a == b && understood,
            onClick = {
                busy = true
                failed = null
                val pw = a.toCharArray()
                scope.launch {
                    try {
                        // A fingerprint left from a previous (deleted or damaged) vault wraps the wrong key.
                        BiometricVault.disable(context, store.id)
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
            "Sirve una copia .sgbak de esta app (con los adjuntos) o una exportación .json de la 1.0.",
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
    // A key that opened nothing (CRY-A02): the button is gone for this screen even if turning it off ever threw.
    var fingerprintStale by remember { mutableStateOf(false) }
    val bio = !fingerprintStale && BiometricVault.enabled(context, store.id) && store.biometricAllowed
    fun withFingerprint() {
        val activity = context.fragmentActivity() ?: return
        // The key is handed over even if this screen's scope is gone (a rotation): opened or wiped, never dropped (BOV-3);
        // only the store's own texts are shown, never a raw exception (LIBRE-4). A key of another vault (a fingerprint
        // that outlived a change of master password or an import) turns the fingerprint off and says so (CRY-A02);
        // launchUnlockWithKey shows the text even if turning it off throws, as VaultBackup does after a new master.
        BiometricVault.unlock(activity, store.id, onKey = { key ->
            scope.launchUnlockWithKey(store, key, onWrongKey = {
                fingerprintStale = true
                BiometricVault.disable(context, store.id)
            }) { error = it }
        }, onError = { error = it })
    }
    LaunchedEffect(Unit) { if (bio) withFingerprint() }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // 2.0, task 2: which vault this is («Bóveda N» and its colour, before unlocking) and the way to another.
        LocalVaultSwitcher.current()
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
                        // A file this version cannot open is not a forgotten password («He olvidado» deletes it).
                        error = if (e.message == NOT_A_VAULT) NOT_A_VAULT else "Contraseña incorrecta"
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
        if (BiometricVault.enabled(context, store.id)) {
            when (store.biometricRefusal) {
                BiometricRefusal.CLOCK -> Text(CLOCK_CHANGED_TEXT, style = MaterialTheme.typography.bodySmall)
                BiometricRefusal.MASTER_DUE ->
                    Text("Por seguridad, cada 7 días hay que escribir la contraseña maestra.", style = MaterialTheme.typography.bodySmall)
                // 2.0: a vault of version 2 or 3 passes to v4 with the master password, once.
                BiometricRefusal.UPGRADE -> Text(UPGRADE_NEEDS_MASTER, style = MaterialTheme.typography.bodySmall)
                // A file this version cannot open: no notice (the master password says it is not compatible).
                BiometricRefusal.UNSUPPORTED, null -> Unit
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
                    scope.launch {
                        // CRY-A02: the fingerprint is off before the vault is deleted.
                        BiometricVault.disable(context, store.id)
                        // 1.4.2: where the file pickers opened goes with the vault.
                        pickerPlaces(context)?.forgetAll()
                        store.destroy()
                    }
                }) { Text("Borrar bóveda", color = NegativeColor) }
            },
            dismissButton = { TextButton(onClick = { confirmDestroy = false }) { Text("Cancelar") } },
        )
    }
}

fun typeIcon(t: EntryType) = when (t) {
    EntryType.LOGIN -> "🔑"
    EntryType.SEED -> "🌱"
    EntryType.NOTE -> "📝"
    EntryType.TOTP -> "⏱"
    EntryType.IDENTITY -> "🪪"
    EntryType.CONTACT -> "👤"
    EntryType.CARD -> "💳"
    EntryType.WIFI -> "📶"
    EntryType.LICENSE -> "🧾"
    EntryType.PASSPORT -> "🛂"
}
