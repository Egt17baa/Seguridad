package app.seguridad.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.seguridad.android.FilterObscuredTouches
import app.seguridad.android.copySensitive
import app.seguridad.android.readClipboardText
import app.seguridad.crypto.PasswordOptions
import app.seguridad.crypto.TotpSecret
import app.seguridad.crypto.generatePassword
import app.seguridad.crypto.parseOtpAuth
import app.seguridad.crypto.parseOtpMigration
import app.seguridad.crypto.totpCode
import app.seguridad.crypto.totpKey
import app.seguridad.ui.theme.BackScaffold
import app.seguridad.ui.theme.NegativeColor
import app.seguridad.ui.theme.NoCopy
import app.seguridad.ui.theme.PasswordField
import app.seguridad.ui.theme.SectionCard
import app.seguridad.ui.theme.ScanQrLabel
import app.seguridad.tools.codes.getValue
import app.seguridad.tools.codes.setValue
import app.seguridad.vault.EntryEditSession
import app.seguridad.vault.cleared
import app.seguridad.vault.followVault
import app.seguridad.vault.left
import app.seguridad.vault.EntryType
import app.seguridad.vault.PendingMigration
import app.seguridad.vault.PasswordChange
import app.seguridad.vault.VAULT_ENTRY_TYPES
import app.seguridad.vault.VaultEntry
import app.seguridad.vault.VaultLock
import app.seguridad.vault.VaultState
import app.seguridad.vault.VaultStore
import app.seguridad.vault.normalizeTags
import app.seguridad.vault.passwordToSave
import app.seguridad.vault.seedWordCount
import app.seguridad.vault.totpImportEntries
import com.journeyapps.barcodescanner.ScanContract
import app.seguridad.android.scanQrOptions
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.UUID

/** Shown when the vault locks while one of its screens is open. */
@Composable
private fun LockedNotice(onBack: () -> Unit) {
    LaunchedEffect(Unit) { onBack() }
}

@Composable
private fun Field(label: String, value: String, secret: Boolean = false, mono: Boolean = false) {
    if (value.isBlank()) return
    val context = LocalContext.current
    var shown by remember { mutableStateOf(!secret) }
    SectionCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            // No text selection: the system "Copy" would skip the sensitive flag and the timed clearing.
            Text(if (shown) value else "•".repeat(minOf(value.length, 16)), fontFamily = if (mono || secret) FontFamily.Monospace else FontFamily.Default)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { copySensitive(context, value) }) { Text("Copiar") }
                if (secret) OutlinedButton(onClick = { shown = !shown }) { Text(if (shown) "Ocultar" else "Mostrar") }
            }
        }
    }
}

private val historyDate: java.time.format.DateTimeFormatter =
    java.time.format.DateTimeFormatter.ofLocalizedDateTime(java.time.format.FormatStyle.MEDIUM).withLocale(java.util.Locale.forLanguageTag("es-ES"))

/** Old passwords, folded away by default; each one is hidden until shown and copies as sensitive, like the current one. */
@Composable
private fun PasswordHistorySection(history: List<PasswordChange>) {
    if (history.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = !open }) { Text("${if (open) "▾" else "▸"} Contraseñas anteriores (${history.size})") }
    if (open) {
        history.forEach { h ->
            val date = java.time.Instant.ofEpochMilli(h.changedAt).atZone(java.time.ZoneId.systemDefault()).format(historyDate)
            Field("Cambiada el $date", h.password, secret = true)
        }
    }
}

@Composable
fun VaultEntryDetailScreen(store: VaultStore, id: String, onEdit: () -> Unit, onShowQr: () -> Unit, onBack: () -> Unit) {
    VaultIdleLock(store)
    val state by store.state.collectAsStateWithLifecycle()
    val entry = (state as? VaultState.Unlocked)?.entries?.firstOrNull { it.id == id }
    if (entry == null) return LockedNotice(onBack)
    val scope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    BackScaffold("${typeIcon(entry.type)} ${entry.title}", onBack) { padding ->
        Column(Modifier.padding(padding).touchesVault(store).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (failed) Text("No se pudo borrar (¿almacenamiento lleno?)", color = NegativeColor)
            entry.totp?.let { t ->
                totpCode(t, now)?.let { (code, left) ->
                    SectionCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(listOf(t.issuer, t.account).filter { it.isNotBlank() }.joinToString(" · "), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(code.chunked(3).joinToString(" "), style = MaterialTheme.typography.displaySmall, fontFamily = FontFamily.Monospace)
                            Text("Caduca en $left s", color = if (left <= 5) NegativeColor else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Field("Código", code, mono = true)
                }
            }
            if (entry.favorite || entry.tags.isNotEmpty()) {
                Text(
                    (listOf("★ Favorito").filter { entry.favorite } + entry.tags.map { "#$it" }).joinToString("  "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Field("Usuario", entry.username)
            Field(if (entry.type == EntryType.SEED) "Passphrase BIP-39" else "Contraseña", entry.password, secret = true)
            Field("Web", entry.url)
            PasswordHistorySection(entry.passwordHistory)
            if (entry.seedWords.isNotBlank()) {
                val (n, valid) = seedWordCount(entry.seedWords)
                Field("Frase semilla ($n palabras${if (valid) "" else ", número poco habitual"})", entry.seedWords, secret = true)
                Text(
                    "Nunca escribas esta frase en una web ni se la des a nadie: quien la tenga controla tus fondos.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NegativeColor,
                )
            }
            Field("Notas", entry.notes, secret = entry.type == EntryType.NOTE)
            entry.totp?.let {
                Field("Clave secreta 2FA", it.secret, secret = true, mono = true)
                OutlinedButton(onClick = onShowQr) { Text("Mostrar QR para otra app") }
            }
            AttachmentsSection(store, entry.id, entry.attachments)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onEdit) { Text("Editar") }
                TextButton(onClick = { confirm = true }) { Text("Borrar", color = NegativeColor) }
            }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("¿Borrar «${entry.title}»?") },
            text = {
                FilterObscuredTouches()
                Text("Se borrará de la bóveda. No se puede deshacer.")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    scope.launch {
                        try {
                            if (store.delete(entry.id)) onBack()
                        } catch (e: Exception) {
                            failed = true
                        }
                    }
                }) { Text("Borrar", color = NegativeColor) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancelar") } },
        )
    }
}

/**
 * Holds the entry editor's [EntryEditSession] for its navigation entry (M6, concern 3 of the CR review): a rotation keeps
 * what was typed and the pending Google Authenticator import; the death of the process does not (never in saved state).
 * Save and import run in [viewModelScope], so a rotation cuts neither them nor what follows them. The vault's state is
 * collected for the entry's whole life (not tied to the lifecycle): a lock clears the form at once, also in the
 * background. When the entry is gone for good ([onCleared]), everything goes.
 */
private class EntryEditViewModel(private val store: VaultStore, id: String?, initialType: EntryType) : ViewModel() {
    val form = EntryEditSession(ComposeCells, id, initialType, newId = UUID.randomUUID().toString())

    init {
        (store.state.value as? VaultState.Unlocked)?.let { form.load(it.entries) }
        form.followVault(store.state, viewModelScope)
    }

    /** A 2FA text scanned, pasted or handed over by the QR inspector: applied exactly as a scan here. */
    fun applyOtpAuth(text: String) {
        val entries = (store.state.value as? VaultState.Unlocked)?.entries ?: return
        form.info.value = null
        if (text.trimStart().startsWith("otpauth-migration://", ignoreCase = true)) {
            val result = parseOtpMigration(text)
            if (result == null) {
                form.error.value = "El QR de Google Authenticator no es válido o está dañado"
            } else {
                val (added, dupes) = totpImportEntries(result.imported, entries, System.currentTimeMillis()) { UUID.randomUUID().toString() }
                form.migration.value = PendingMigration(result, added, result.skipped + dupes)
                form.error.value = null
            }
            return
        }
        val parsed = parseOtpAuth(text)
        if (parsed == null) {
            form.error.value = "El QR o enlace no es un código 2FA (otpauth://totp/…)"
        } else {
            form.totp.value = parsed
            if (form.title.value.isBlank()) form.title.value = parsed.issuer.ifBlank { parsed.account }
            form.error.value = null
        }
    }

    fun save(entry: VaultEntry) {
        if (form.saving.value) return
        form.saving.value = true
        viewModelScope.launch {
            try {
                if (store.upsert(entry)) {
                    form.finish()
                } else if (form.loaded) {
                    form.error.value = "La bóveda se bloqueó: no se ha guardado"
                }
            } catch (x: CancellationException) {
                throw x
            } catch (x: Exception) {
                if (form.loaded) form.error.value = "No se pudo guardar"
            } finally {
                form.saving.value = false
            }
        }
    }

    /** «Importar» in the Google Authenticator dialog: [pending]'s entries, then what the batch says comes next. */
    fun importMigration(pending: PendingMigration) {
        if (form.saving.value) return
        val result = pending.result
        val batch = result.batchIndex + 1
        val more = result.batchSize > 1 && batch < result.batchSize
        if (pending.entries.isEmpty()) {
            form.migration.value = null
            if (more) form.info.value = "Lote $batch de ${result.batchSize}: escanea el siguiente"
            return
        }
        form.saving.value = true
        viewModelScope.launch {
            try {
                when {
                    !store.addAll(pending.entries) -> if (form.loaded) form.error.value = "La bóveda se bloqueó: no se ha importado nada"
                    more -> form.info.value =
                        "Lote $batch de ${result.batchSize} importado (${pending.entries.size}). Lote ${batch + 1} de ${result.batchSize}: escanea el siguiente"
                    // Editing an entry: leaving would lose its unsaved changes, so it stays here.
                    form.existing.value != null -> form.info.value =
                        "Importados ${pending.entries.size} códigos de Google Authenticator. Esta entrada sigue abierta: " +
                            "guárdala si la has cambiado."
                    else -> form.finish()
                }
            } catch (x: CancellationException) {
                throw x
            } catch (x: Exception) {
                if (form.loaded) form.error.value = "No se pudo importar"
            } finally {
                form.migration.value = null
                form.saving.value = false
            }
        }
    }

    override fun onCleared() {
        form.cleared()
    }
}

/**
 * A new entry ([id] null) or an existing one. Its state lives in [EntryEditViewModel] (a rotation keeps it). [otpAuthText]
 * is a 2FA text the QR inspector read (`otpauth://` or a Google Authenticator export): applied once per session, exactly
 * as a scan here would be; [onOtpAuthTaken] then lets the caller drop its own copy.
 */
@Composable
fun VaultEntryEditScreen(
    store: VaultStore,
    id: String?,
    initialType: EntryType,
    onDone: () -> Unit,
    otpAuthText: String? = null,
    onOtpAuthTaken: () -> Unit = {},
) {
    VaultIdleLock(store)
    val state by store.state.collectAsStateWithLifecycle()
    val unlocked = state as? VaultState.Unlocked ?: return LockedNotice(onDone)
    val vm = viewModel { EntryEditViewModel(store, id, initialType) }
    val form = vm.form
    val context = LocalContext.current
    val activity = context.fragmentActivity()
    // Only a rotation (or another configuration change) keeps what was typed: leaving otherwise lets it go now.
    DisposableEffect(vm) {
        onDispose { form.left(changingConfigurations = activity?.isChangingConfigurations == true) }
    }
    if (form.finished.value) {
        LaunchedEffect(Unit) { onDone() }
        return
    }
    // Back after leaving (another tab): from the vault again, never from what was typed. A no-op once loaded.
    form.load(unlocked.entries)
    val existing = form.existing.value
    var type by form.type
    var title by form.title
    var username by form.username
    // One field per type: a password generated under «Acceso» must never become a seed's passphrase.
    var loginPassword by form.loginPassword
    var seedPassphrase by form.seedPassphrase
    var url by form.url
    var notes by form.notes
    var seed by form.seed
    var totp by form.totp
    var favorite by form.favorite
    var tagsText by form.tagsText
    var error by form.error
    val info = form.info.value
    val saving = form.saving.value
    // Typing counts as use too, so a long note is not lost to the idle lock.
    LaunchedEffect(type, title, username, loginPassword, seedPassphrase, url, notes, seed, totp, tagsText) { store.touch() }

    LaunchedEffect(Unit) {
        form.takeHandOver(otpAuthText)?.let(vm::applyOtpAuth)
        onOtpAuthTaken()
    }

    // The library asks for the CAMERA permission itself; a refusal comes back as a result flag, not as an exception.
    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        VaultLock.keepOpenOnce = false // Back in the app, whatever the outcome: leaving later must lock.
        val raw = result.contents
        when {
            raw != null -> vm.applyOtpAuth(raw)
            result.originalIntent?.getBooleanExtra(com.google.zxing.client.android.Intents.Scan.MISSING_CAMERA_PERMISSION, false) == true ->
                error = "Sin permiso de cámara: pega el enlace otpauth:// o escribe la clave"
            // Otherwise the user cancelled: nothing to say.
        }
    }

    BackScaffold(if (existing == null) "Nueva entrada" else "Editar", form::finish) { padding ->
        NoCopy {
            // M-9: imePadding, so the notes and the last fields stay above the keyboard with edge-to-edge.
            Column(Modifier.padding(padding).imePadding().touchesVault(store).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (existing == null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        VAULT_ENTRY_TYPES.forEach { t -> FilterChip(selected = type == t, onClick = { type = t }, label = { Text("${typeIcon(t)} ${t.label}") }) }
                    }
                }
                OutlinedTextField(title, { title = it }, label = { Text("Nombre (p. ej. «Binance» o «Ledger»)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                when (type) {
                    EntryType.LOGIN -> {
                        OutlinedTextField(username, { username = it }, label = { Text("Usuario o correo") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        PasswordField(loginPassword, { loginPassword = it })
                        TextButton(onClick = { loginPassword = generatePassword(PasswordOptions(length = 20)) ?: loginPassword }) { Text("🎲 Generar una al azar") }
                        OutlinedTextField(url, { url = it }, label = { Text("Web") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                    EntryType.SEED -> {
                        OutlinedTextField(
                            seed,
                            { seed = it },
                            label = { Text("Palabras de recuperación, separadas por espacios") },
                            minLines = 3,
                            // A password field: well-behaved keyboards (Gboard among them) neither suggest from it nor sync it. Not
                            // learning is also asked of every field by NoLearningInput; both are up to the keyboard to honour.
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        val (n, valid) = seedWordCount(seed)
                        if (n > 0) Text("$n palabras" + if (valid) " ✓" else " (lo habitual son 12, 15, 18, 21 o 24)", style = MaterialTheme.typography.bodySmall)
                        PasswordField(seedPassphrase, { seedPassphrase = it }, label = "Passphrase BIP-39 (opcional)")
                        TextButton(onClick = { seedPassphrase = generatePassword(PasswordOptions(length = 20)) ?: seedPassphrase }) { Text("🎲 Generar una al azar") }
                        Text(
                            "Una passphrase crea una cartera distinta. Guárdala en un sitio distinto de la frase: quien tenga solo la frase no accede a esos fondos. Si la pierdes, tú tampoco.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    EntryType.NOTE, EntryType.IDENTITY, EntryType.CONTACT -> Unit // The last two have their own tab.
                    EntryType.TOTP -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(onClick = {
                                VaultLock.keepOpenOnce = true
                                store.touch() // The time away is counted from here (AWAY_BACKSTOP_MS with «Al salir»).
                                try {
                                    scanner.launch(scanQrOptions())
                                } catch (e: Exception) {
                                    VaultLock.keepOpenOnce = false // The app never left: leaving later must lock.
                                    error = "No se pudo abrir el escáner: pega el enlace otpauth:// o escribe la clave"
                                }
                            }) { ScanQrLabel() }
                            OutlinedButton(onClick = {
                                // M3 (audit 1.3.0, teclado): never throws, like every other «Pegar».
                                readClipboardText(context)?.let(vm::applyOtpAuth) ?: run { error = "El portapapeles está vacío" }
                            }) { Text("Pegar enlace") }
                        }
                        OutlinedTextField(totp.issuer, { totp = totp.copy(issuer = it) }, label = { Text("Servicio") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(totp.account, { totp = totp.copy(account = it) }, label = { Text("Cuenta") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        PasswordField(totp.secret, { totp = totp.copy(secret = it) }, label = "Clave secreta (Base32)")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                            listOf(6, 8).forEach { d -> FilterChip(selected = totp.digits == d, onClick = { totp = totp.copy(digits = d) }, label = { Text("$d dígitos") }) }
                            listOf("SHA1", "SHA256", "SHA512").forEach { a -> FilterChip(selected = totp.algorithm == a, onClick = { totp = totp.copy(algorithm = a) }, label = { Text(a) }) }
                        }
                    }
                }
                OutlinedTextField(
                    notes,
                    { notes = it },
                    label = { Text(if (type == EntryType.NOTE) "Nota" else "Notas") },
                    minLines = if (type == EntryType.NOTE) 5 else 2,
                    // Secure notes: a password field, as the seed words (no learning or suggestions from well-behaved keyboards).
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    tagsText,
                    { tagsText = it },
                    label = { Text("Etiquetas (separadas por comas)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Switch(checked = favorite, onCheckedChange = { favorite = it })
                    Text("Favorito")
                }
                if (existing == null) {
                    Text("Guarda la entrada para poder añadirle adjuntos.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    // The live list: adding or removing saves at once, apart from the rest of the form.
                    AttachmentsSection(store, existing.id, unlocked.entries.firstOrNull { it.id == existing.id }?.attachments.orEmpty())
                }
                error?.let { Text(it, color = NegativeColor) }
                info?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                Button(
                    onClick = {
                        if (saving) return@Button // A second tap before the button redraws as disabled.
                        val now = System.currentTimeMillis()
                        error = when {
                            title.isBlank() -> "Ponle un nombre"
                            type == EntryType.TOTP && totpKey(totp.secret) == null -> "La clave secreta no es válida (Base32: letras A-Z y números 2-7, al menos 16)"
                            type == EntryType.SEED && seedWordCount(seed).first == 0 -> "Escribe las palabras"
                            else -> null
                        }
                        if (error != null) return@Button
                        val e = VaultEntry(
                            id = form.entryId,
                            type = type,
                            title = title.trim(),
                            username = if (type == EntryType.LOGIN) username.trim() else "",
                            password = passwordToSave(type, loginPassword, seedPassphrase),
                            url = if (type == EntryType.LOGIN) url.trim() else "",
                            notes = notes,
                            seedWords = if (type == EntryType.SEED) seed.trim().split(Regex("\\s+")).joinToString(" ") { it.lowercase() } else "",
                            totp = if (type == EntryType.TOTP) totp.copy(secret = totp.secret.uppercase().filter { it.isLetterOrDigit() }) else null,
                            createdAt = existing?.createdAt ?: now,
                            updatedAt = now,
                            favorite = favorite,
                            tags = normalizeTags(tagsText),
                        )
                        vm.save(e)
                    },
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Guardar") }
            }
        }
    }
    form.migration.value?.let { pending ->
        val result = pending.result
        val entries = pending.entries
        val batch = result.batchIndex + 1
        val more = result.batchSize > 1 && batch < result.batchSize
        val batchNote = when {
            result.batchSize <= 1 -> null
            more -> "Lote $batch de ${result.batchSize}: escanea el siguiente"
            else -> "Lote $batch de ${result.batchSize}: es el último"
        }
        AlertDialog(
            onDismissRequest = { if (!saving) form.migration.value = null },
            title = { Text("Importar de Google Authenticator") },
            text = {
                FilterObscuredTouches()
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        when (entries.size) {
                            0 -> "No hay códigos nuevos que importar."
                            1 -> "Se añadirá 1 código nuevo a la bóveda: ${entries.single().title}."
                            else -> "Se añadirán ${entries.size} códigos nuevos a la bóveda: ${entries.joinToString(", ") { it.title }}."
                        },
                    )
                    if (pending.leftOut.isNotEmpty()) {
                        Text("No se importan (${pending.leftOut.size}):", style = MaterialTheme.typography.labelLarge)
                        pending.leftOut.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                    }
                    batchNote?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) }
                }
            },
            confirmButton = {
                TextButton(enabled = !saving, onClick = { vm.importMigration(pending) }) {
                    Text(if (entries.isEmpty()) "Aceptar" else "Importar ${entries.size}")
                }
            },
            dismissButton = { if (entries.isNotEmpty()) TextButton(enabled = !saving, onClick = { form.migration.value = null }) { Text("Cancelar") } },
        )
    }
}
