package app.seguridad.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.seguridad.BuildConfig
import app.seguridad.settings.ClipboardClear
import app.seguridad.settings.KdfProfile
import app.seguridad.settings.LockAfter
import app.seguridad.settings.Settings
import app.seguridad.settings.SettingsStore
import app.seguridad.settings.ThemeMode
import app.seguridad.settings.availableOn
import app.seguridad.ui.theme.BackScaffold
import app.seguridad.ui.theme.SectionCard
import app.seguridad.vault.VaultState
import app.seguridad.vault.VaultStore
import kotlinx.coroutines.launch

private fun ThemeMode.label() = when (this) {
    ThemeMode.DARK -> "Oscuro"
    ThemeMode.LIGHT -> "Claro"
    ThemeMode.SYSTEM -> "Como el sistema"
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    SectionCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            content()
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** One option of a single-choice list; the whole row is the touch target. */
@Composable
private fun Choice(label: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick),
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Text(
            label,
            modifier = Modifier.padding(start = 8.dp, top = 10.dp, bottom = 10.dp),
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun <T> Choices(options: List<T>, selected: T, label: (T) -> String, enabled: (T) -> Boolean = { true }, onSelect: (T) -> Unit) {
    Column(Modifier.selectableGroup()) {
        options.forEach { o -> Choice(label(o), o == selected, enabled(o)) { onSelect(o) } }
    }
}

/** The Ajustes tab: lock, clipboard, Argon2id cost and theme; the vault's copies and master password; Acerca de. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(store: SettingsStore, vault: VaultStore, onAbout: () -> Unit) {
    VaultIdleLock(vault)
    val settings by store.settings.collectAsStateWithLifecycle()
    val vaultState by vault.state.collectAsStateWithLifecycle()
    LaunchedEffect(vault) { vault.load() }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val actions = vaultActions(vault, snackbar)
    val highAvailable = remember { KdfProfile.HIGH.availableOn(Runtime.getRuntime().maxMemory() / 1024) }
    fun change(f: (Settings) -> Settings) {
        scope.launch {
            try {
                store.update(f)
            } catch (e: Exception) {
                snackbar.showSnackbar("No se pudo guardar el ajuste")
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { TopAppBar(title = { Text("Ajustes") }) },
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Section("Bloquear la bóveda tras no usarla") {
                Choices(LockAfter.entries, settings.lockAfter, { it.label }) { v -> change { it.copy(lockAfter = v) } }
                Hint("Al salir de la app se bloquea siempre (salvo mientras eliges un archivo o escaneas un QR para ella).")
            }
            Section("Borrar del portapapeles lo copiado") {
                Choices(ClipboardClear.entries, settings.clipboard, { "A los ${it.seconds} s" }) { v -> change { it.copy(clipboard = v) } }
                Hint("Solo se borra si sigue ahí lo que copiaste desde la app.")
            }
            Section("Coste de Argon2id para lo nuevo") {
                Choices(KdfProfile.entries, settings.kdf, { it.label }, enabled = { it != KdfProfile.HIGH || highAvailable }) { v ->
                    change { it.copy(kdf = v) }
                }
                Hint(
                    "Se usa al crear la bóveda, al cambiar la contraseña maestra y al cifrar textos y archivos. Lo ya cifrado guarda su coste " +
                        "y se sigue abriendo igual." + if (highAvailable) "" else " El coste alto necesita más memoria de la que este móvil da a la app.",
                )
            }
            Section("Tema") {
                Choices(ThemeMode.entries, settings.theme, { it.label() }) { v -> change { it.copy(theme = v) } }
            }
            Section("Copia de la bóveda") {
                val unlocked = vaultState is VaultState.Unlocked
                val canImport = unlocked || vaultState is VaultState.NoVault
                OutlinedButton(onClick = actions.export, enabled = unlocked, modifier = Modifier.fillMaxWidth()) { Text("Exportar bóveda") }
                OutlinedButton(onClick = actions.import, enabled = canImport, modifier = Modifier.fillMaxWidth()) { Text("Importar copia") }
                OutlinedButton(onClick = actions.changeMaster, enabled = unlocked, modifier = Modifier.fillMaxWidth()) { Text("Cambiar contraseña maestra") }
                Hint(
                    when (vaultState) {
                        VaultState.Locked -> "Desbloquea la bóveda (pestaña Bóveda) para exportarla, importar una copia o cambiar la contraseña maestra."
                        VaultState.NoVault -> "Aún no tienes bóveda: puedes crearla en la pestaña Bóveda o importar una copia."
                        else -> "La copia va cifrada con tu contraseña maestra: guárdala donde quieras. " +
                            "Al importar se reemplaza la bóveda de este móvil."
                    },
                )
                Hint("«Importar copia» acepta también la exportación criptopanel-vault, que se vuelve a cifrar con el formato nuevo.")
            }
            Section("Información") {
                TextButton(onClick = onAbout) { Text("Acerca de, licencias y avisos") }
            }
        }
    }
}

/** Version, what the app does not promise, and the licenses of what it ships. */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val bouncyCastle = remember {
        runCatching { context.assets.open("licenses/bouncycastle.txt").bufferedReader().use { it.readText() } }.getOrDefault("")
    }
    BackScaffold("Acerca de", onBack) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Seguridad ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.headlineSmall)
            Hint("Contraseñas, códigos 2FA, frases semilla y cifrado, todo en tu móvil y sin cuentas.")
            Section("Avisos") {
                Hint("• Sin auditoría independiente: la app no ha pasado una auditoría de seguridad externa. Para fondos importantes usa una wallet hardware y un gestor de contraseñas auditado.")
                Hint("• Sin recuperación: si olvidas la contraseña maestra o la de algo cifrado, nadie puede recuperar lo guardado.")
                Hint("• Red: solo «¿Filtrada?» se conecta, a api.pwnedpasswords.com, y solo envía los 5 primeros caracteres de la huella SHA-1 de la contraseña.")
            }
            Section("Licencias") {
                Text("Bouncy Castle (licencia MIT)", style = MaterialTheme.typography.labelLarge)
                Hint("Algoritmos criptográficos: Argon2id, ChaCha20-Poly1305, Twofish, Serpent y Camellia.")
                if (bouncyCastle.isNotBlank()) {
                    Text(bouncyCastle.trim(), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                }
                Text("Lista de palabras de la EFF (CC BY 3.0)", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                Hint("EFF Large Wordlist for Passphrases, de la Electronic Frontier Foundation (eff.org/dice), con licencia Creative Commons Atribución 3.0. Se usa sin cambios en el Generador.")
                Text("Lista de palabras BIP-39 (licencia MIT)", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                Hint("Las 2.048 palabras en inglés de las frases semilla BIP-39, sin cambios, para comprobar y completar frases en Cifrar texto.")
            }
        }
    }
}
