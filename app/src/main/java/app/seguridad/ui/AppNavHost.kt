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
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.navArgument
import app.seguridad.AppContainer
import app.seguridad.ui.screens.AboutScreen
import app.seguridad.ui.screens.BreachScreen
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
import app.seguridad.ui.screens.FileHashScreen
import app.seguridad.ui.screens.GeneratorScreen
import app.seguridad.ui.screens.HealthScreen
import app.seguridad.ui.screens.IdentityScreen
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
import app.seguridad.ui.screens.VaultScreen
import app.seguridad.vault.AUTO_BACKUP_REPLACED
import app.seguridad.vault.EntryType
import app.seguridad.vault.VAULT_ENTRY_TYPES
import kotlinx.coroutines.launch

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("vault", "Bóveda", Icons.Default.Lock),
    Tab("codes", "Códigos", Icons.Default.Refresh),
    Tab("contacts", "Contactos", Icons.Default.Person),
    Tab("tools", "Herramientas", Icons.Default.Build),
    Tab("settings", "Ajustes", Icons.Default.Settings),
)

@Composable
fun AppNavHost(container: AppContainer) {
    val settingsStore = container.settings
    val settings by settingsStore.settings.collectAsStateWithLifecycle()
    val vault = container.vault
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

    val nav = rememberNavController()
    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    val onTab = tabs.firstOrNull { it.route == route }?.route
    // On a detail screen, the tab it was opened from stays selected (an entry opens from Bóveda and from Códigos alike).
    var lastTab by rememberSaveable { mutableStateOf(tabs.first().route) }
    LaunchedEffect(onTab) { if (onTab != null) lastTab = onTab }
    val currentTab = onTab ?: lastTab
    val back: () -> Unit = { nav.popBackStack() }
    // Ids are encoded into the routes: an imported copy (or a vault that imported one with 1.0 to 1.2.x) may hold any id,
    // and encoded it can neither match another route nor break this one (LIBRE-1).
    val onOpen: (String) -> Unit = { id -> nav.navigate("vault/entry/${Uri.encode(id)}") }
    val onHealth: () -> Unit = { nav.navigate("vault/health") }
    val onNew: (EntryType) -> Unit = { type -> nav.navigate("vault/edit?type=${type.name}") }

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
        ReplaceOutcomeDialog(shown.error ?: AUTO_BACKUP_REPLACED, failed = shown.error != null, onClose = { container.autoBackup.replaceOutcomeShown(shown) })
    }
    val replacing by container.autoBackup.replacing.collectAsStateWithLifecycle()
    if (masterState.open) {
        MasterChangedDialog(
            state = masterState,
            autoBackupOn = settings.autoBackupUri != null,
            replacing = replacing,
            pending = settings.autoBackupReplacePending,
            lastError = settings.lastAutoBackupError,
            lastBackupAt = settings.lastAutoBackupAt,
            onReplace = { masterChanged.replace() },
            onRenew = {
                masterChanged.close()
                nav.navigate("contacts/me")
            },
            onClose = masterChanged::close,
        )
    }
    CompositionLocalProvider(LocalOnMasterChanged provides masterChanged::changed) {
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
                composable("vault") { VaultScreen(vault, totpOnly = false, onOpen = onOpen, onNew = onNew, onHealth = onHealth) }
                composable("codes") { VaultScreen(vault, totpOnly = true, onOpen = onOpen, onNew = onNew, onHealth = onHealth) }
                composable("vault/entry/{id}") { entry ->
                    val id = entry.arguments?.getString("id").orEmpty()
                    VaultEntryDetailScreen(vault, id, onEdit = { nav.navigate("vault/edit?id=${Uri.encode(id)}") }, onShowQr = { nav.navigate("vault/entry/${Uri.encode(id)}/qr") }, onBack = back)
                }
                composable("vault/entry/{id}/qr") { entry ->
                    OtpQrScreen(vault, entry.arguments?.getString("id").orEmpty(), onBack = back)
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
                        onDone = back,
                    )
                }
                composable("vault/health") {
                    HealthScreen(vault, container.pwned, onEdit = { id -> nav.navigate("vault/edit?id=${Uri.encode(id)}") }, onBack = back)
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
                composable("contacts/me") { IdentityScreen(vault, onBack = back) }
                composable("contacts/add") { ContactAddScreen(vault, onBack = back) }
                composable("contacts/encrypt") { EncryptForScreen(vault, onBack = back) }
                composable("contacts/sign") { SignScreen(vault, onBack = back) }
                composable("contacts/open") { OpenReceivedScreen(vault, onBack = back, onOpenSymmetric = { nav.navigate("tools/file") }) }
                composable("contacts/c/{id}") { entry ->
                    ContactDetailScreen(vault, entry.arguments?.getString("id").orEmpty(), onBack = back)
                }
                composable("tools") { ToolsScreen(settings, onTool = { route -> nav.navigate(route) }) }
                composable("tools/text") { TextCryptoScreen(settings.kdf.params, onBack = back) }
                composable("tools/file") { FileCryptoScreen(settings.kdf.params, onBack = back, onOpenReceived = { nav.navigate("contacts/open") }) }
                composable("tools/generator") { GeneratorScreen(onBack = back) }
                composable("tools/breach") { BreachScreen(container.pwned, onBack = back) }
                composable("tools/hash") { FileHashScreen(onBack = back) }
                composable("tools/photo") { PhotoCleanScreen(container.shareTemp, onBack = back) }
                composable("tools/redact") { RedactScreen(container.shareTemp, onBack = back) }
                // The QR's text never goes in a route: it is handed over through QrHandoff.
                composable("tools/qr") {
                    QrInspectScreen(
                        vault,
                        onAddContact = { nav.navigate("contacts/add") },
                        onOpenReceived = { nav.navigate("contacts/open") },
                        onAddTotp = { nav.navigate("tools/qr/totp") },
                        onBack = back,
                    )
                }
                composable("tools/codes") { CodesScreen(vault, lockAfterMs = { settingsStore.settings.value.lockAfter.ms }, onBack = back) }
                composable("tools/qr/totp") { QrTotpImportScreen(vault, onDone = back) }
                composable("settings") { SettingsScreen(settingsStore, vault, container.autoBackup, onAbout = { nav.navigate("settings/about") }) }
                composable("settings/about") { AboutScreen(onBack = back, onLicenses = { nav.navigate("settings/licenses") }) }
                composable("settings/licenses") { LicensesScreen(onBack = back) }
            }
        }
    }
}
