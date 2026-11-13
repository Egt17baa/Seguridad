package app.seguridad.ui

import android.net.Uri
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import app.seguridad.ui.theme.AlertDialog
import androidx.compose.material3.Icon
import app.seguridad.ui.theme.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.navArgument
import app.seguridad.AppContainer
import app.seguridad.android.FilterObscuredTouches
import app.seguridad.tools.APP_RENAMED_TEXT
import app.seguridad.tools.APP_RENAMED_TITLE
import app.seguridad.tools.PASSWORD_CHANGE_CLOSE
import app.seguridad.ui.screens.AboutScreen
import app.seguridad.ui.screens.AppearanceNoticeDialog
import app.seguridad.ui.screens.AppearanceScreen
import app.seguridad.ui.screens.EntryHistoryScreen
import app.seguridad.ui.screens.TrashScreen
import app.seguridad.ui.screens.BreachScreen
import app.seguridad.ui.screens.ClassicLabScreen
import app.seguridad.ui.screens.CodesScreen
import app.seguridad.ui.screens.ContactAddScreen
import app.seguridad.ui.screens.ContactDetailScreen
import app.seguridad.ui.screens.ContactsScreen
import app.seguridad.ui.screens.EncryptForScreen
import app.seguridad.ui.screens.OpenReceivedScreen
import app.seguridad.ui.screens.SignScreen
import app.seguridad.ui.screens.FileCryptoScreen
import app.seguridad.ui.screens.OtpQrScreen
import app.seguridad.ui.screens.PhotoCleanScreen
import app.seguridad.ui.screens.QrInspectScreen
import app.seguridad.ui.screens.QrTotpImportScreen
import app.seguridad.ui.screens.RedactScreen
import app.seguridad.ui.screens.HashLabScreen
import app.seguridad.ui.screens.BackupCheckScreen
import app.seguridad.ui.screens.SecretShareScreen
import app.seguridad.ui.screens.GeneratorScreen
import app.seguridad.ui.screens.HealthScreen
import app.seguridad.ui.screens.IdentityScreen
import app.seguridad.ui.screens.ImportScreen
import app.seguridad.ui.screens.LicensesScreen
import app.seguridad.ui.screens.LocalOnMasterChanged
import app.seguridad.ui.screens.MasterChangedDialog
import app.seguridad.ui.screens.ReplaceOutcomeDialog
import app.seguridad.ui.screens.NoticeScreen
import app.seguridad.ui.screens.SettingsScreen
import app.seguridad.ui.screens.TextCryptoScreen
import app.seguridad.ui.screens.ToolsScreen
import app.seguridad.ui.screens.VaultEntryDetailScreen
import app.seguridad.ui.screens.VaultEntryEditScreen
import app.seguridad.ui.screens.VaultIdleTicker
import app.seguridad.ui.screens.LocalVaultSwitcher
import app.seguridad.ui.screens.VaultPickerScreen
import app.seguridad.ui.screens.VaultSwitcher
import app.seguridad.settings.vault
import app.seguridad.vault.VaultStore
import androidx.compose.runtime.key
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import app.seguridad.ui.screens.VaultScreen
import app.seguridad.vault.replacedText
import app.seguridad.vault.EntryType
import app.seguridad.vault.VAULT_ENTRY_TYPES
import app.seguridad.vault.VaultState
import kotlinx.coroutines.launch

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("vault", "Bóveda", Icons.Default.Lock),
    Tab("codes", "Códigos", Icons.Default.Refresh),
    Tab("contacts", "Contactos", Icons.Default.Person),
    Tab("tools", "Herramientas", Icons.Default.Build),
    Tab("settings", "Ajustes", Icons.Default.Settings),
)

/**
 * 2.0, task 2: the ViewModels of the active vault's screens, kept across a re-creation of the activity (as the
 * navigation's own were) and cleared as soon as another vault is active: a ViewModel built for one vault never outlives
 * the switch, so it can never write to it again (nor to the other: it only knows its own store).
 */
internal class VaultScopes : ViewModel() {
    private val stores = HashMap<String, ViewModelStore>()

    fun storeFor(id: String): ViewModelStore = stores.getOrPut(id) { ViewModelStore() }

    /** The vault [id] is no longer the active one: its ViewModels go (their scopes are cancelled). */
    fun drop(id: String) {
        stores.remove(id)?.clear()
    }

    override fun onCleared() {
        stores.values.forEach { it.clear() }
        stores.clear()
    }
}

@Composable
private fun VaultScoped(container: AppContainer, vault: VaultStore, content: @Composable () -> Unit) {
    val scopes = viewModel<VaultScopes>()
    val owner = remember(vault) {
        val store = scopes.storeFor(vault.id)
        object : ViewModelStoreOwner {
            override val viewModelStore: ViewModelStore = store
        }
    }
    // Review M-6: cleared in an effect, once this composition leaves, never while one is being composed; and only when
    // the vault is no longer the active one (a re-created activity keeps them, as the navigation's own were kept).
    DisposableEffect(vault) {
        onDispose { if (container.activeVault.value !== vault) scopes.drop(vault.id) }
    }
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner) { content() }
}

@Composable
fun AppNavHost(container: AppContainer) {
    val settingsStore = container.settings
    val settings by settingsStore.settings.collectAsStateWithLifecycle()
    // 2.0, task 2: the vault the app shows. Everything below is built again for another one (a switch): its screens,
    // their ViewModels and the navigation, so no screen keeps the store of a vault that is no longer active.
    val vault by container.activeVault.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    // The vault locks after the chosen time unused, on any screen (and before the notice is accepted too).
    VaultIdleTicker(vault)

    // If saving the acceptance fails (full storage), the notice still lets the user in and comes back next time.
    var noticeAcceptedNow by rememberSaveable { mutableStateOf(false) }
    if (!settings.noticeAccepted && !noticeAcceptedNow) {
        NoticeScreen(onAccept = {
            scope.launch {
                try {
                    settingsStore.update { it.copy(noticeAccepted = true) }
                } catch (e: Exception) {
                    noticeAcceptedNow = true
                }
            }
        })
        return
    }

    key(vault) {
        VaultScoped(container, vault) { VaultNav(container, vault) }
    }
}

@Composable
private fun VaultNav(container: AppContainer, vault: VaultStore) {
    val settingsStore = container.settings
    val settings by settingsStore.settings.collectAsStateWithLifecycle()
    val vaultSettings = settings.vault(vault.id)
    val nav = rememberNavController()
    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    val onTab = tabs.firstOrNull { it.route == route }?.route
    // On a detail screen, the tab it was opened from stays selected (an entry opens from Bóveda and from Códigos alike).
    var lastTab by rememberSaveable { mutableStateOf(tabs.first().route) }
    LaunchedEffect(onTab) { if (onTab != null) lastTab = onTab }
    val currentTab = onTab ?: lastTab
    // Ids are encoded into the routes: an imported copy (or a vault that imported one with 1.0 to 1.2.x) may hold any id,
    // and encoded it can neither match another route nor break this one (LIBRE-1).
    val onOpen: (String) -> Unit = { id -> nav.navigate("vault/entry/${Uri.encode(id)}") }
    val onHealth: () -> Unit = { nav.navigate("vault/health") }
    val onNew: (EntryType) -> Unit = { type -> nav.navigate("vault/edit?type=${type.name}") }
    val onEdit: (String) -> Unit = { id -> nav.navigate("vault/edit?id=${Uri.encode(id)}") }
    val onImportOther: () -> Unit = { nav.navigate("vault/import") }
    val onTrash: () -> Unit = { nav.navigate("vault/trash") }

    // After the master password changes (from any screen), the dialog about the old copies and the identity. It lives in
    // AppContainer, not here: a rotation while the new key is derived re-creates this composition, and the screen that
    // changed the password writes to the holder, which the new composition reads (M-3).
    val masterChanged = container.masterChanged
    val masterState by masterChanged.state.collectAsStateWithLifecycle()
    // How a replacement asked for while locked went, told once after the unlock that ran it.
    // It lives in AutoBackup until shown, so an activity re-created meanwhile still shows it; closing it acknowledges only
    // the outcome that was on screen (T11-d): one that arrived meanwhile is shown next.
    val replaceOutcome by container.autoBackup.replaceOutcome.collectAsStateWithLifecycle()
    replaceOutcome?.let { shown ->
        ReplaceOutcomeDialog(shown.error ?: replacedText(shown.unlistedLeft), failed = shown.error != null, onClose = { container.autoBackup.replaceOutcomeShown(shown) })
    }
    // The notice of the new name, once, to those who updated (owner's decision, 31-10-2026). Never at the vault's gate,
    // where it would cover the password field or sit under the fingerprint prompt: only while the vault is open, or when
    // there is none (m4: a lock while it is up hides it until the next unlock). It lives in AppContainer, so a rotation
    // keeps it up until «Entendido».
    val vaultState by vault.state.collectAsStateWithLifecycle()
    val renameReady = vaultState is VaultState.Unlocked || vaultState is VaultState.NoVault
    LaunchedEffect(renameReady) { if (renameReady) container.renameNotice.showIfDue() }
    val renameVisible by container.renameNotice.visible.collectAsStateWithLifecycle()
    if (renameVisible && renameReady) RenameNoticeDialog(onClose = container.renameNotice::closed)
    // 2.0, task 5 bis (R-C8): the line on how to go back to a look without transparencies, once, to those who updated. As
    // the notice of the name, only with the vault open or with none; never over that notice nor over the one of the
    // upgrade of the vault (O-3): it waits until they are closed.
    val upgradeDone by vault.upgradeDone.collectAsStateWithLifecycle()
    val appearanceReady = renameReady && !renameVisible && upgradeDone == null
    LaunchedEffect(appearanceReady) { if (appearanceReady) container.appearanceNotice.showIfDue() }
    val appearanceVisible by container.appearanceNotice.visible.collectAsStateWithLifecycle()
    if (appearanceVisible && appearanceReady) AppearanceNoticeDialog(onClose = container.appearanceNotice::closed)
    // Review I-2: what the last action of «Bóvedas» said, also after the switch it made (it rebuilt every screen).
    val vaultsNotice by container.vaultsNotice.collectAsStateWithLifecycle()
    vaultsNotice?.let { shown ->
        AlertDialog(
            onDismissRequest = { container.vaultsNoticeShown(shown) },
            text = {
                FilterObscuredTouches()
                Text(shown)
            },
            confirmButton = { TextButton(onClick = { container.vaultsNoticeShown(shown) }) { Text(PASSWORD_CHANGE_CLOSE) } },
        )
    }
    val replacing by container.autoBackup.replacing.collectAsStateWithLifecycle()
    if (masterState.open) {
        MasterChangedDialog(
            state = masterState,
            autoBackupOn = vaultSettings.autoBackupUri != null,
            replacing = replacing,
            pending = vaultSettings.autoBackupReplacePending,
            lastError = vaultSettings.lastAutoBackupError,
            lastBackupAt = vaultSettings.lastAutoBackupAt,
            toldSinceRequest = vaultSettings.autoBackupReplaceTold.isNotEmpty(),
            onReplace = { masterChanged.replace() },
            onRenew = {
                masterChanged.close()
                nav.navigate("contacts/me")
            },
            onClose = masterChanged::close,
        )
    }
    val switcher: @Composable () -> Unit = { VaultSwitcher(container, onOpen = { nav.navigate("vaults") }) }
    CompositionLocalProvider(LocalOnMasterChanged provides container::onMasterChanged, LocalVaultSwitcher provides switcher) {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentTab == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            },
        ) { padding ->
            NavHost(nav, startDestination = "vault", modifier = Modifier.padding(padding).consumeWindowInsets(padding)) {
                composable("vault") { VaultScreen(vault, container.undoTrash, container.twoStepCopy, totpOnly = false, onOpen = onOpen, onEdit = onEdit, onNew = onNew, onHealth = onHealth, onImportOther = onImportOther, onTrash = onTrash) }
                composable("codes") { VaultScreen(vault, container.undoTrash, container.twoStepCopy, totpOnly = true, onOpen = onOpen, onEdit = onEdit, onNew = onNew, onHealth = onHealth, onImportOther = onImportOther, onTrash = onTrash) }
                // 2.0, task 3: the trash and the versions of an entry (the id encoded, LIBRE-1).
                composable("vault/trash") { TrashScreen(vault, onBack = backOnce(nav)) }
                composable("vault/entry/{id}/history") { entry ->
                    EntryHistoryScreen(vault, entry.arguments?.getString("id").orEmpty(), onBack = backOnce(nav))
                }
                composable("vault/entry/{id}") { entry ->
                    val id = entry.arguments?.getString("id").orEmpty()
                    VaultEntryDetailScreen(
                        vault, container.undoTrash, id,
                        onEdit = { nav.navigate("vault/edit?id=${Uri.encode(id)}") },
                        onShowQr = { nav.navigate("vault/entry/${Uri.encode(id)}/qr") },
                        onHistory = { nav.navigate("vault/entry/${Uri.encode(id)}/history") },
                        onBack = backOnce(nav),
                        onOpenEntry = { copy -> nav.navigate("vault/entry/${Uri.encode(copy)}") },
                    )
                }
                composable("vault/entry/{id}/qr") { entry ->
                    OtpQrScreen(vault, entry.arguments?.getString("id").orEmpty(), onBack = backOnce(nav))
                }
                composable(
                    "vault/edit?id={id}&type={type}",
                    arguments = listOf(
                        navArgument("id") { type = NavType.StringType; nullable = true; defaultValue = null },
                        navArgument("type") { type = NavType.StringType; nullable = true; defaultValue = null },
                    ),
                ) { entry ->
                    VaultEntryEditScreen(
                        vault,
                        id = entry.arguments?.getString("id"),
                        initialType = VAULT_ENTRY_TYPES.firstOrNull { it.name == entry.arguments?.getString("type") } ?: EntryType.LOGIN,
                        onDone = backOnce(nav),
                    )
                }
                composable("vault/health") {
                    HealthScreen(vault, container.pwned, onEdit = { id -> nav.navigate("vault/edit?id=${Uri.encode(id)}") }, onBack = backOnce(nav))
                }
                // Every contacts/… screen goes through VaultGate (inside ContactsFrame): with the vault locked it shows the gate, never data.
                // A contact's own screen is under contacts/c/, so no id (an imported copy brings any) can ever be taken for one
                // of the fixed routes (me, add, encrypt, sign, open); the id is encoded, so it stays one path segment (LIBRE-1).
                composable("contacts") {
                    ContactsScreen(
                        vault,
                        onIdentity = { nav.navigate("contacts/me") },
                        onAdd = { nav.navigate("contacts/add") },
                        onContact = { id -> nav.navigate("contacts/c/${Uri.encode(id)}") },
                        onAction = { route -> nav.navigate(route) },
                    )
                }
                composable("contacts/me") { IdentityScreen(vault, onBack = backOnce(nav)) }
                composable("contacts/add") { ContactAddScreen(vault, onBack = backOnce(nav)) }
                composable("contacts/encrypt") { EncryptForScreen(vault, onBack = backOnce(nav)) }
                composable("contacts/sign") { SignScreen(vault, onBack = backOnce(nav)) }
                composable("contacts/open") { OpenReceivedScreen(vault, onBack = backOnce(nav), onOpenSymmetric = { nav.navigate("tools/file") }) }
                composable("contacts/c/{id}") { entry ->
                    ContactDetailScreen(vault, entry.arguments?.getString("id").orEmpty(), onBack = backOnce(nav))
                }
                composable("tools") { ToolsScreen(settings, vaultSettings.kdf, onTool = { route -> nav.navigate(route) }) }
                // COV1-1: what is typed there and the result go by the «PIN y códigos» rule (lock time, multi-window, locks).
                val lockAfterMs = { settingsStore.settings.value.lockAfter.ms }
                // «Importar de otra app»: what it reads is dropped on any lock, on back and by the «PIN y códigos» rule.
                composable("vault/import") {
                    ImportScreen(vault, container.autoBackup::copyBeforeImport, lockAfterMs = lockAfterMs, awayInOtherApp = container.awayInOtherApp, onBack = backOnce(nav))
                }
                composable("tools/text") {
                    TextCryptoScreen(vaultSettings.kdf.params, vault, lockAfterMs = lockAfterMs, awayInOtherApp = container.awayInOtherApp, onBack = backOnce(nav))
                }
                composable("tools/file") {
                    FileCryptoScreen(
                        vaultSettings.kdf.params, vault, lockAfterMs = lockAfterMs, awayInOtherApp = container.awayInOtherApp,
                        onBack = backOnce(nav), onOpenReceived = { nav.navigate("contacts/open") },
                    )
                }
                composable("tools/generator") { GeneratorScreen(onBack = backOnce(nav)) }
                composable("tools/breach") {
                    BreachScreen(container.pwned, vault, lockAfterMs = lockAfterMs, awayInOtherApp = container.awayInOtherApp, onBack = backOnce(nav))
                }
                composable("tools/hash") { HashLabScreen(vault, lockAfterMs = lockAfterMs, awayInOtherApp = container.awayInOtherApp, onBack = backOnce(nav)) }
                composable("tools/classic") { ClassicLabScreen(vault, lockAfterMs = lockAfterMs, awayInOtherApp = container.awayInOtherApp, onBack = backOnce(nav)) }
                composable("tools/share-secret") { SecretShareScreen(vault, lockAfterMs = lockAfterMs, awayInOtherApp = container.awayInOtherApp, onBack = backOnce(nav)) }
                composable("tools/photo") { PhotoCleanScreen(container.shareTemp, onBack = backOnce(nav)) }
                composable("tools/redact") { RedactScreen(container.shareTemp, onBack = backOnce(nav)) }
                // The QR's text never goes in a route: it is handed over through QrHandoff.
                composable("tools/qr") {
                    QrInspectScreen(
                        vault,
                        lockAfterMs = lockAfterMs,
                        awayInOtherApp = container.awayInOtherApp,
                        onAddContact = { nav.navigate("contacts/add") },
                        onOpenReceived = { nav.navigate("contacts/open") },
                        onAddTotp = { nav.navigate("tools/qr/totp") },
                        onBack = backOnce(nav),
                    )
                }
                composable("tools/codes") { CodesScreen(vault, lockAfterMs = lockAfterMs, awayInOtherApp = container.awayInOtherApp, onBack = backOnce(nav)) }
                composable("tools/qr/totp") { QrTotpImportScreen(vault, onDone = backOnce(nav)) }
                composable("settings") {
                    SettingsScreen(
                        settingsStore, vault, container.autoBackup, container::releaseIfUnused,
                        onAbout = { nav.navigate("settings/about") },
                        onCheckBackup = { nav.navigate("settings/check-backup") },
                        onAppearance = { nav.navigate("settings/appearance") },
                    )
                }
                // 2.0, task 5 bis: the look (mode, style, palette, «Personalizado», transparencies, contrast).
                composable("settings/appearance") { AppearanceScreen(settingsStore, onBack = backOnce(nav)) }
                composable("settings/check-backup") {
                    BackupCheckScreen(vault, lockAfterMs = lockAfterMs, awayInOtherApp = container.awayInOtherApp, onBack = backOnce(nav))
                }
                // 2.0, task 2: the vaults of the phone (choose, create, rename, order, delete, import as a new one).
                composable("vaults") { VaultPickerScreen(container, onBack = backOnce(nav)) }
                composable("settings/about") { AboutScreen(onBack = backOnce(nav), onLicenses = { nav.navigate("settings/licenses") }) }
                composable("settings/licenses") { LicensesScreen(onBack = backOnce(nav)) }
            }
        }
    }
}

/** «Nuevo nombre»: the app used to be called «Seguridad» ([APP_RENAMED_TEXT]); shown once, closed with «Entendido». */
@Composable
private fun RenameNoticeDialog(onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(APP_RENAMED_TITLE) },
        text = {
            FilterObscuredTouches()
            Text(APP_RENAMED_TEXT)
        },
        confirmButton = { TextButton(onClick = onClose) { Text(PASSWORD_CHANGE_CLOSE) } },
    )
}

/**
 * «Back» for the screen that composes it: pops that screen, and only while it is the one on top. A screen that leaves by
 * itself (LockedNotice, when its entry is gone or the vault locks) and also goes back from a button can never pop the
 * screen under it, the vault list or the start destination: a second call does nothing (F-1 of 1.4.1, where deleting an
 * entry left a blank screen or another tab).
 * - A call while another screen is on top (a lock during the transition to the editor or the QR) waits: the screen is
 *   popped as soon as it is on top again, so it never stays blank (the 1.4.1 audit, finding 1). A call while the
 *   screen is on its way out (already popped) is dropped with it: the wait lives as long as its composition.
 * - Inside a destination, LocalLifecycleOwner is its NavBackStackEntry. Anywhere else (a wrapper with its own owner),
 *   it pops as before, never silently doing nothing.
 */
@Composable
private fun backOnce(nav: NavHostController): () -> Unit {
    val entry = LocalLifecycleOwner.current as? NavBackStackEntry ?: return remember(nav) { { nav.popBackStack(); Unit } }
    var pending by remember(entry) { mutableStateOf(false) }
    LaunchedEffect(nav, entry) {
        nav.currentBackStackEntryFlow.collect { top ->
            if (pending && top === entry) {
                pending = false
                nav.popBackStack()
            }
        }
    }
    return remember(nav, entry) {
        {
            if (nav.currentBackStackEntry === entry) {
                pending = false
                nav.popBackStack()
            } else {
                pending = true
            }
        }
    }
}
