package app.seguridad.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.seguridad.android.FilterObscuredTouches
import app.seguridad.android.copySensitive
import app.seguridad.crypto.PasswordOptions
import app.seguridad.crypto.TotpSecret
import app.seguridad.crypto.generatePassword
import app.seguridad.crypto.MigrationResult
import app.seguridad.crypto.parseOtpAuth
import app.seguridad.crypto.parseOtpMigration
import app.seguridad.crypto.totpCode
import app.seguridad.crypto.totpKey
import app.seguridad.ui.theme.BackScaffold
import app.seguridad.ui.theme.NegativeColor
import app.seguridad.ui.theme.NoCopy
import app.seguridad.ui.theme.PasswordField
import app.seguridad.ui.theme.SectionCard
import app.seguridad.vault.EntryType
import app.seguridad.vault.PasswordChange
import app.seguridad.vault.VaultEntry
import app.seguridad.vault.VaultLock
import app.seguridad.vault.VaultState
import app.seguridad.vault.VaultStore
import app.seguridad.vault.initialPassword
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

@Composable
fun VaultEntryEditScreen(store: VaultStore, id: String?, initialType: EntryType, onDone: () -> Unit) {
    VaultIdleLock(store)
    val state by store.state.collectAsStateWithLifecycle()
    val unlocked = state as? VaultState.Unlocked ?: return LockedNotice(onDone)
    val existing = remember(id) { id?.let { i -> unlocked.entries.firstOrNull { it.id == i } } }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var type by remember { mutableStateOf(existing?.type ?: initialType) }
    var title by remember { mutableStateOf(existing?.title.orEmpty()) }
    var username by remember { mutableStateOf(existing?.username.orEmpty()) }
    // One field per type: a password generated under «Acceso» must never become a seed's passphrase.
    var loginPassword by remember { mutableStateOf(initialPassword(existing, EntryType.LOGIN)) }
    var seedPassphrase by remember { mutableStateOf(initialPassword(existing, EntryType.SEED)) }
    var url by remember { mutableStateOf(existing?.url.orEmpty()) }
    var notes by remember { mutableStateOf(existing?.notes.orEmpty()) }
    var seed by remember { mutableStateOf(existing?.seedWords.orEmpty()) }
    var totp by remember { mutableStateOf(existing?.totp ?: TotpSecret("")) }
    var favorite by remember { mutableStateOf(existing?.favorite ?: false) }
    var tagsText by remember { mutableStateOf(existing?.tags.orEmpty().joinToString(", ")) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    // One id for a new entry, however many times Guardar is tapped.
    val newId = remember { UUID.randomUUID().toString() }
    // Typing counts as use too, so a long note is not lost to the idle lock.
    LaunchedEffect(type, title, username, loginPassword, seedPassphrase, url, notes, seed, totp, tagsText) { store.touch() }

    // A Google Authenticator export waiting for the user's confirmation, with the entries it would add.
    var migration by remember { mutableStateOf<Pair<MigrationResult, List<VaultEntry>>?>(null) }
    var migrationLeftOut by remember { mutableStateOf(emptyList<String>()) }
    var info by remember { mutableStateOf<String?>(null) }

    fun applyOtpAuth(text: String) {
        info = null
        if (text.trimStart().startsWith("otpauth-migration://", ignoreCase = true)) {
            val result = parseOtpMigration(text)
            if (result == null) {
                error = "El QR de Google Authenticator no es válido o está dañado"
            } else {
                val (entries, dupes) = totpImportEntries(result.imported, unlocked.entries, System.currentTimeMillis()) { UUID.randomUUID().toString() }
                migration = result to entries
                migrationLeftOut = result.skipped + dupes
                error = null
            }
            return
        }
        val parsed = parseOtpAuth(text)
        if (parsed == null) {
            error = "El QR o enlace no es un código 2FA (otpauth://totp/…)"
        } else {
            totp = parsed
            if (title.isBlank()) title = parsed.issuer.ifBlank { parsed.account }
            error = null
        }
    }

    // The library asks for the CAMERA permission itself; a refusal comes back as a result flag, not as an exception.
    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        VaultLock.keepOpenOnce = false // Back in the app, whatever the outcome: leaving later must lock.
        val raw = result.contents
        when {
            raw != null -> applyOtpAuth(raw)
            result.originalIntent?.getBooleanExtra(com.google.zxing.client.android.Intents.Scan.MISSING_CAMERA_PERMISSION, false) == true ->
                error = "Sin permiso de cámara: pega el enlace otpauth:// o escribe la clave"
            // Otherwise the user cancelled: nothing to say.
        }
    }

    BackScaffold(if (existing == null) "Nueva entrada" else "Editar", onDone) { padding ->
        NoCopy {
            Column(Modifier.padding(padding).touchesVault(store).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (existing == null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        EntryType.entries.forEach { t -> FilterChip(selected = type == t, onClick = { type = t }, label = { Text("${typeIcon(t)} ${t.label}") }) }
                    }
                }
                OutlinedTextField(title, { title = it }, label = { Text("Nombre (p. ej. «Binance» o «Ledger»)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                when (type) {
                    EntryType.LOGIN -> {
                        OutlinedTextField(username, { username = it }, label = { Text("Usuario o correo") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        PasswordField(loginPassword, { loginPassword = it })
                        TextButton(onClick = { loginPassword = generatePassword(PasswordOptions(length = 20)) ?: loginPassword }) { Text("🎲 Generar una segura") }
                        OutlinedTextField(url, { url = it }, label = { Text("Web") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                    EntryType.SEED -> {
                        OutlinedTextField(
                            seed,
                            { seed = it },
                            label = { Text("Palabras de recuperación, separadas por espacios") },
                            minLines = 3,
                            // A password keyboard neither learns nor suggests the words, nor syncs them to the cloud.
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        val (n, valid) = seedWordCount(seed)
                        if (n > 0) Text("$n palabras" + if (valid) " ✓" else " (lo habitual son 12, 15, 18, 21 o 24)", style = MaterialTheme.typography.bodySmall)
                        PasswordField(seedPassphrase, { seedPassphrase = it }, label = "Passphrase BIP-39 (opcional)")
                        TextButton(onClick = { seedPassphrase = generatePassword(PasswordOptions(length = 20)) ?: seedPassphrase }) { Text("🎲 Generar una segura") }
                        Text(
                            "Una passphrase crea una wallet distinta. Guárdala en un sitio distinto de la frase: quien tenga solo la frase no accede a esos fondos. Si la pierdes, tú tampoco.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    EntryType.NOTE -> Unit
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
                            }) { Text("📷 Escanear QR") }
                            OutlinedButton(onClick = {
                                val cm = context.getSystemService(android.content.ClipboardManager::class.java)
                                cm?.primaryClip?.getItemAt(0)?.text?.toString()?.let(::applyOtpAuth) ?: run { error = "El portapapeles está vacío" }
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
                    // Secure notes: no keyboard learning or suggestions (same as the seed words).
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
                            id = existing?.id ?: newId,
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
                        saving = true
                        scope.launch {
                            try {
                                if (store.upsert(e)) onDone() else error = "La bóveda se bloqueó: no se ha guardado"
                            } catch (x: Exception) {
                                error = "No se pudo guardar"
                            } finally {
                                saving = false
                            }
                        }
                    },
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Guardar") }
            }
        }
    }
    migration?.let { (result, entries) ->
        val batch = result.batchIndex + 1
        val more = result.batchSize > 1 && batch < result.batchSize
        val batchNote = when {
            result.batchSize <= 1 -> null
            more -> "Lote $batch de ${result.batchSize}: escanea el siguiente"
            else -> "Lote $batch de ${result.batchSize}: es el último"
        }
        AlertDialog(
            onDismissRequest = { if (!saving) migration = null },
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
                    if (migrationLeftOut.isNotEmpty()) {
                        Text("No se importan (${migrationLeftOut.size}):", style = MaterialTheme.typography.labelLarge)
                        migrationLeftOut.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                    }
                    batchNote?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) }
                }
            },
            confirmButton = {
                TextButton(enabled = !saving, onClick = {
                    if (entries.isEmpty()) {
                        migration = null
                        if (more) info = "Lote $batch de ${result.batchSize}: escanea el siguiente"
                        return@TextButton
                    }
                    saving = true
                    scope.launch {
                        try {
                            if (!store.addAll(entries)) {
                                error = "La bóveda se bloqueó: no se ha importado nada"
                            } else if (more) {
                                info = "Lote $batch de ${result.batchSize} importado (${entries.size}). Lote ${batch + 1} de ${result.batchSize}: escanea el siguiente"
                            } else {
                                onDone()
                            }
                        } catch (x: Exception) {
                            error = "No se pudo importar"
                        } finally {
                            migration = null
                            saving = false
                        }
                    }
                }) { Text(if (entries.isEmpty()) "Aceptar" else "Importar ${entries.size}") }
            },
            dismissButton = { if (entries.isNotEmpty()) TextButton(enabled = !saving, onClick = { migration = null }) { Text("Cancelar") } },
        )
    }
}
