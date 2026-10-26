package app.seguridad.ui

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import app.seguridad.ui.screens.FileCryptoScreen
import app.seguridad.ui.screens.OtpQrScreen
import app.seguridad.ui.screens.FileHashScreen
import app.seguridad.ui.screens.GeneratorScreen
import app.seguridad.ui.screens.HealthScreen
import app.seguridad.ui.screens.LicensesScreen
import app.seguridad.ui.screens.NoticeScreen
import app.seguridad.ui.screens.SettingsScreen
import app.seguridad.ui.screens.TextCryptoScreen
import app.seguridad.ui.screens.ToolsScreen
import app.seguridad.ui.screens.VaultEntryDetailScreen
import app.seguridad.ui.screens.VaultEntryEditScreen
import app.seguridad.ui.screens.VaultIdleTicker
import app.seguridad.ui.screens.VaultScreen
import app.seguridad.vault.EntryType
import kotlinx.coroutines.launch

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("vault", "Bóveda", Icons.Default.Lock),
    Tab("codes", "Códigos", Icons.Default.Refresh),
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
    val onOpen: (String) -> Unit = { id -> nav.navigate("vault/entry/$id") }
    val onHealth: () -> Unit = { nav.navigate("vault/health") }
    val onNew: (EntryType) -> Unit = { type -> nav.navigate("vault/edit?type=${type.name}") }

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
                VaultEntryDetailScreen(vault, id, onEdit = { nav.navigate("vault/edit?id=$id") }, onShowQr = { nav.navigate("vault/entry/$id/qr") }, onBack = back)
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
                    initialType = EntryType.entries.firstOrNull { it.name == entry.arguments?.getString("type") } ?: EntryType.LOGIN,
                    onDone = back,
                )
            }
            composable("vault/health") {
                HealthScreen(vault, container.pwned, onEdit = { id -> nav.navigate("vault/edit?id=$id") }, onBack = back)
            }
            composable("tools") { ToolsScreen(settings, onTool = { route -> nav.navigate(route) }) }
            composable("tools/text") { TextCryptoScreen(settings.kdf.params, onBack = back) }
            composable("tools/file") { FileCryptoScreen(settings.kdf.params, onBack = back) }
            composable("tools/generator") { GeneratorScreen(onBack = back) }
            composable("tools/breach") { BreachScreen(container.pwned, onBack = back) }
            composable("tools/hash") { FileHashScreen(onBack = back) }
            composable("settings") { SettingsScreen(settingsStore, vault, container.autoBackup, onAbout = { nav.navigate("settings/about") }) }
            composable("settings/about") { AboutScreen(onBack = back, onLicenses = { nav.navigate("settings/licenses") }) }
            composable("settings/licenses") { LicensesScreen(onBack = back) }
        }
    }
}
