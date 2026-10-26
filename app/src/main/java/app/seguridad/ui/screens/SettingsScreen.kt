package app.seguridad.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import app.seguridad.android.SafBackupTarget
import app.seguridad.settings.AUTO_BACKUP_KEEP_CHOICES
import app.seguridad.settings.ClipboardClear
import app.seguridad.settings.KdfProfile
import app.seguridad.settings.LockAfter
import app.seguridad.settings.Settings
import app.seguridad.settings.SettingsStore
import app.seguridad.settings.ThemeMode
import app.seguridad.settings.availableOn
import app.seguridad.ui.theme.BackScaffold
import app.seguridad.ui.theme.NegativeColor
import app.seguridad.ui.theme.SectionCard
import app.seguridad.vault.AutoBackup
import app.seguridad.vault.VaultLock
import app.seguridad.vault.VaultState
import app.seguridad.vault.VaultStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

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

private val backupDate: DateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(Locale.forLanguageTag("es-ES"))

/**
 * «Copia automática»: the folder (chosen with OpenDocumentTree, its access kept with a persistable permission and
 * given back when turned off or replaced), how many copies to keep, and how the last one went.
 */
@Composable
private fun AutoBackupSection(store: SettingsStore, vault: VaultStore, autoBackup: AutoBackup, snackbar: SnackbarHostState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by store.settings.collectAsStateWithLifecycle()
    val vaultState by vault.state.collectAsStateWithLifecycle()
    val folder = settings.autoBackupUri
    // Copies are only made from the open vault (each attachment is checked): while locked, one waits for it.
    val locked = vaultState is VaultState.Locked

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        VaultLock.keepOpenOnce = false // Back in the app, whatever the outcome: leaving later must lock.
        if (uri != null) scope.launch {
            val resolver = context.contentResolver
            if (!withContext(Dispatchers.IO) { SafBackupTarget.takeFolder(resolver, uri) }) {
                snackbar.showSnackbar("No se pudo obtener permiso para usar esa carpeta. Elige otra.")
                return@launch
            }
            val chosen = uri.toString()
            val previous = store.settings.value.autoBackupUri
            try {
                store.update { it.copy(autoBackupUri = chosen, lastAutoBackupError = null, lastAutoBackupAt = null) }
            } catch (e: Exception) {
                if (chosen != previous) SafBackupTarget.releaseFolder(resolver, chosen)
                snackbar.showSnackbar("No se pudo guardar el ajuste")
                return@launch
            }
            if (previous != null && previous != chosen) SafBackupTarget.releaseFolder(resolver, previous)
            autoBackup.request()
            snackbar.showSnackbar(
                if (vault.state.value is VaultState.Locked) {
                    "Copia automática activada: se hará una en cuanto abras la bóveda y después tras cada cambio."
                } else {
                    "Copia automática activada: se hará una ahora y tras cada cambio en la bóveda."
                },
            )
        }
    }
    val choose = {
        VaultLock.keepOpenOnce = true
        vault.touch() // The time away is counted from here (AWAY_BACKSTOP_MS with «Al salir»).
        picker.launchOrError(null)?.let { failure ->
            VaultLock.keepOpenOnce = false // The app never left: leaving later must lock.
            scope.launch { snackbar.showSnackbar(failure) }
        }
        Unit
    }

    Section("Copia automática") {
        if (folder == null) {
            Hint(
                "Guarda una copia cifrada (.sgbak, con los adjuntos) en una carpeta que elijas, unos segundos después de cada " +
                    "cambio en la bóveda. Elige una carpeta que se sincronice o que copies fuera del móvil.",
            )
            OutlinedButton(onClick = choose, modifier = Modifier.fillMaxWidth()) { Text("Elegir carpeta") }
            return@Section
        }
        Text("Carpeta: ${SafBackupTarget.label(folder)}", style = MaterialTheme.typography.bodyMedium)
        Choices(AUTO_BACKUP_KEEP_CHOICES, settings.autoBackupKeep, { "Conservar las $it últimas" }) { k ->
            scope.launch {
                try {
                    store.update { it.copy(autoBackupKeep = k) }
                } catch (e: Exception) {
                    snackbar.showSnackbar("No se pudo guardar el ajuste")
                }
            }
        }
        val last = settings.lastAutoBackupAt
        Hint(
            if (last == null) {
                "Aún no se ha hecho ninguna copia: se hará " + if (locked) "en cuanto abras la bóveda." else "en unos segundos y tras cada cambio."
            } else {
                "Última copia: " + backupDate.format(Instant.ofEpochMilli(last).atZone(ZoneId.systemDefault()))
            },
        )
        settings.lastAutoBackupError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = NegativeColor) }
        OutlinedButton(
            onClick = {
                autoBackup.request() // While locked it waits, and is made as soon as the vault is opened.
                if (vault.state.value is VaultState.Locked) {
                    scope.launch { snackbar.showSnackbar("Abre la bóveda (pestaña Bóveda) para hacer la copia: se hará en cuanto la abras.") }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Hacer una copia ahora") }
        OutlinedButton(onClick = choose, modifier = Modifier.fillMaxWidth()) { Text("Cambiar de carpeta") }
        OutlinedButton(
            onClick = {
                scope.launch {
                    try {
                        store.update { it.copy(autoBackupUri = null, lastAutoBackupError = null, lastAutoBackupAt = null) }
                        SafBackupTarget.releaseFolder(context.contentResolver, folder)
                        snackbar.showSnackbar("Copia automática desactivada. Las copias ya hechas siguen en la carpeta.")
                    } catch (e: Exception) {
                        snackbar.showSnackbar("No se pudo guardar el ajuste")
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Desactivar") }
        Hint(
            "Cada copia va cifrada con la contraseña maestra que tenía la bóveda al hacerla: si la cambias, las copias anteriores " +
                "se siguen abriendo con la antigua. Solo se borran las copias automáticas más antiguas (seguridad-auto-…), nunca " +
                "otros archivos de la carpeta.",
        )
    }
}

/** The Ajustes tab: lock, clipboard, Argon2id cost and theme; the vault's copies and master password; Acerca de. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(store: SettingsStore, vault: VaultStore, autoBackup: AutoBackup, onAbout: () -> Unit) {
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
                Hint("Al salir de la app se bloquea siempre (salvo mientras eliges un archivo o escaneas un QR para ella; con «Al salir», como mucho 2 minutos).")
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
                    "Se usa al crear la bóveda, al cambiar la contraseña maestra y al cifrar textos y archivos. Lo ya cifrado guarda su coste. " +
                        "Lo cifrado con el coste alto solo se abre en móviles que den a la app al menos 171 MiB de memoria: muchos móviles " +
                        "económicos no llegan, así que úsalo solo si vas a abrir tus copias en móviles como este." +
                        if (highAvailable) "" else " Este móvil no da a la app memoria suficiente para cifrar con el coste alto.",
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
                Hint("«Exportar bóveda» guarda una copia .sgbak con los adjuntos. «Importar copia» acepta esa copia, la exportación .json de la 1.0 y la criptopanel-vault, que se vuelve a cifrar con el formato nuevo.")
            }
            AutoBackupSection(store, vault, autoBackup, snackbar)
            Section("Información") {
                TextButton(onClick = onAbout) { Text("Acerca de, licencias y avisos") }
            }
        }
    }
}

/** Version and what the app does not promise; the licenses of what it ships are one tap away. */
@Composable
fun AboutScreen(onBack: () -> Unit, onLicenses: () -> Unit) {
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
                Hint("Solo componentes de código abierto con licencia libre y gratuita.")
                OutlinedButton(onClick = onLicenses, modifier = Modifier.fillMaxWidth()) { Text("Ver licencias") }
            }
        }
    }
}
