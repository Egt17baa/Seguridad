package app.seguridad.ui.screens

import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import app.seguridad.tools.TwoStepCopy
import app.seguridad.vault.BIG_OPEN
import app.seguridad.vault.passwordForHealth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import app.seguridad.ui.theme.AlertDialog
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
import app.seguridad.crypto.TOTP_UNSUPPORTED_ENTRY
import app.seguridad.crypto.TOTP_UNSUPPORTED_LINK
import app.seguridad.crypto.TotpSecret
import app.seguridad.crypto.generatePassword
import app.seguridad.crypto.OtpAuthRead
import app.seguridad.crypto.isSupported
import app.seguridad.crypto.isUsableTotpKey
import app.seguridad.crypto.parseOtpMigration
import app.seguridad.crypto.readOtpAuth
import app.seguridad.crypto.totpCode
import app.seguridad.imports.plainReportText
import app.seguridad.tools.qr.WIFI_QR_OPEN
import app.seguridad.ui.theme.BackScaffold
import app.seguridad.ui.theme.NegativeColor
import app.seguridad.ui.theme.NoCopy
import app.seguridad.ui.theme.PasswordField
import app.seguridad.ui.theme.SectionCard
import app.seguridad.ui.theme.ScanQrLabel
import app.seguridad.tools.codes.getValue
import app.seguridad.tools.codes.setValue
import app.seguridad.vault.EntryEditSession
import app.seguridad.vault.FormSlot
import app.seguridad.vault.PASTE_MOVES
import app.seguridad.vault.PASTE_NO
import app.seguridad.vault.PASTE_OTPAUTH_QUESTION
import app.seguridad.vault.PASTE_SEED_QUESTION
import app.seguridad.vault.PASTE_YES
import app.seguridad.vault.PastedKind
import app.seguridad.vault.UNSAVED_DISCARD
import app.seguridad.vault.UNSAVED_KEEP
import app.seguridad.vault.UNSAVED_SAVE
import app.seguridad.vault.UNSAVED_TITLE
import app.seguridad.vault.WordLists
import app.seguridad.vault.LeaveGuard
import app.seguridad.vault.guardedBy
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.activity.compose.BackHandler
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
import app.seguridad.vault.MOVE_TO
import app.seguridad.vault.MOVE_TO_TRASH
import app.seguridad.vault.folderCrumb
import app.seguridad.vault.folderPath
import app.seguridad.vault.storeSaveErrorText
import app.seguridad.vault.MOVE_TO_TRASH_TEXT
import app.seguridad.vault.UndoTrash
import app.seguridad.vault.historyOpen
import app.seguridad.vault.saveRefusedText
import app.seguridad.vault.moveToTrashTitle
import app.seguridad.vault.oldPasswords
import app.seguridad.vault.normalizeTags
import app.seguridad.vault.passwordToSave
import app.seguridad.vault.seedWordCount
import app.seguridad.vault.totpImportEntries
import app.seguridad.vault.DUPLICATE
import app.seguridad.vault.DUPLICATE_OPEN
import app.seguridad.vault.FormFields
import app.seguridad.vault.duplicatedText
import app.seguridad.vault.entryLimitProblem
import app.seguridad.vault.entryExpiresAt
import app.seguridad.vault.fieldsToSave
import app.seguridad.vault.rotates
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import com.journeyapps.barcodescanner.ScanContract
import app.seguridad.android.scanQrOptions
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Review I-1 of task 7: the guard the tabs of the app ask before leaving the entry editor (AppNavHost provides it).
 * Null outside the app's navigation (previews, tests), where nothing asks.
 */
val LocalLeaveGuard = staticCompositionLocalOf<LeaveGuard?> { null }

/** Shown when the vault locks while one of its screens is open. */
@Composable
private fun LockedNotice(onBack: () -> Unit) {
    LaunchedEffect(Unit) { onBack() }
}

@Composable
internal fun Field(
    label: String,
    value: String,
    secret: Boolean = false,
    mono: Boolean = false,
    plain: Boolean = false,
    onCopied: () -> Unit = {},
    onBig: (() -> Unit)? = null,
) {
    if (value.isBlank()) return
    val context = LocalContext.current
    var shown by remember { mutableStateOf(!secret) }
    SectionCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            // No text selection: the system "Copy" would skip the sensitive flag and the timed clearing.
            Text(if (shown) (if (plain) plainReportText(value, value.length) else value) else "•".repeat(minOf(value.length, 16)), fontFamily = if (mono || secret) FontFamily.Monospace else FontFamily.Default)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // A hidden field copies as the password does (sensitive, cleared after the time of Ajustes, the A14
                // notice on Android 8 to 12); what follows the copy waits until it is really in the clipboard.
                FilledTonalButton(onClick = { copySensitive(context, value) { onCopied() } }) { Text("Copiar") }
                if (secret) OutlinedButton(onClick = { shown = !shown }) { Text(if (shown) "Ocultar" else "Mostrar") }
                // 2.0, task 6: «Ver en grande» (BigPasswordScreen reads the secret from the vault, never from here).
                onBig?.let { TextButton(onClick = it) { Text(BIG_OPEN) } }
            }
        }
    }
}

internal val historyDate: java.time.format.DateTimeFormatter =
    java.time.format.DateTimeFormatter.ofLocalizedDateTime(java.time.format.FormatStyle.MEDIUM).withLocale(java.util.Locale.forLanguageTag("es-ES"))

/** Old passwords, folded away by default; each one is hidden until shown and copies as sensitive, like the current one. */
@Composable
private fun PasswordHistorySection(history: List<PasswordChange>, onCopied: () -> Unit) {
    if (history.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = !open }) { Text("${if (open) "▾" else "▸"} Contraseñas anteriores (${history.size})") }
    if (open) {
        history.forEach { h ->
            val date = java.time.Instant.ofEpochMilli(h.changedAt).atZone(java.time.ZoneId.systemDefault()).format(historyDate)
            Field("Cambiada el $date", h.password, secret = true, onCopied = onCopied)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VaultEntryDetailScreen(
    store: VaultStore,
    undo: UndoTrash,
    twoStep: TwoStepCopy,
    id: String,
    onEdit: () -> Unit,
    onBig: (fieldId: String?) -> Unit,
    onShowQr: () -> Unit,
    onHistory: () -> Unit,
    onBack: () -> Unit,
    onOpenEntry: (String) -> Unit = {},
    onShowWifiQr: () -> Unit = {},
) {
    VaultIdleLock(store)
    val state by store.state.collectAsStateWithLifecycle()
    val entry = (state as? VaultState.Unlocked)?.entries?.firstOrNull { it.id == id }
    if (entry == null) return LockedNotice(onBack)
    val scope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    // 2.0, task 4: «Mover a…», and copying a field counts as a use («Recientes», the order «por uso»): a bookkeeping
    // save that makes no version and no automatic copy ([VaultStore.markUsed]); a failure only loses the mark.
    var moving by remember { mutableStateOf(false) }
    var moveError by remember { mutableStateOf<String?>(null) }
    // 2.0, task 5: «Duplicar» says what it made and offers to open it (the id and the title shown, no secret).
    var duplicated by remember { mutableStateOf<Pair<String, String>?>(null) }
    // Review M-5: one copy per tap, however fast the second comes.
    var duplicating by remember { mutableStateOf(false) }
    val used: () -> Unit = { scope.launch { runCatching { store.markUsed(entry.id) } } }
    // 2.0, task 6: any copy ends the offer of «Copiar contraseña»; «Copiar» of the user starts it (design «Copiar usuario
    // y luego contraseña»), for 30 s, with only the entry's id.
    val copied: () -> Unit = {
        twoStep.clear()
        used()
    }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val message: (String) -> Unit = { text -> scope.launch { snackbar.showSnackbar(text) } }
    val folders = (state as? VaultState.Unlocked)?.data?.folders.orEmpty()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    PasswordOfferOnReturn(store, twoStep, snackbar) { e -> copyOfferedPassword(context, scope, store, twoStep, e, message) }
    BackScaffold("${typeIcon(entry.type)} ${plainReportText(entry.title)}", onBack, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(Modifier.padding(padding).touchesVault(store).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (failed) Text("No se pudo mover a la papelera (¿almacenamiento lleno?)", color = NegativeColor)
            entry.totp?.let { t ->
                if (!t.isSupported()) {
                    // A saved code with an algorithm, digits or period this app does not calculate: the reason, not a wrong code.
                    SectionCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(plainReportText(listOf(t.issuer, t.account).filter { it.isNotBlank() }.joinToString(" · ")), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(TOTP_UNSUPPORTED_ENTRY, color = NegativeColor)
                        }
                    }
                } else {
                    totpCode(t, now)?.let { (code, left) ->
                        SectionCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(plainReportText(listOf(t.issuer, t.account).filter { it.isNotBlank() }.joinToString(" · ")), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(code.chunked(3).joinToString(" "), style = MaterialTheme.typography.displaySmall, fontFamily = FontFamily.Monospace)
                                Text("Caduca en $left s", color = if (left <= 5) NegativeColor else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Field("Código", code, mono = true, onCopied = copied)
                    }
                }
            }
            if (entry.favorite || entry.tags.isNotEmpty()) {
                Text(
                    (listOf("★ Favorito").filter { entry.favorite } + entry.tags.map { "#${plainReportText(it)}" }).joinToString("  "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            entry.folderId?.let { id -> folderPath(folders, id).takeIf { it.isNotEmpty() } }?.let { path ->
                Text(folderCrumb(path.map { plainReportText(it.name) }), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // 2.0, task 5: the earliest date of expiry, only inside the app (O-4).
            ExpiryCardLine(entry, now)
            Field("Usuario", entry.username, plain = true, onCopied = {
                used()
                if (entry.passwordForHealth().isNotEmpty()) twoStep.offerPassword(entry.id)
            })
            Field(
                if (entry.type == EntryType.SEED) "Passphrase BIP-39" else "Contraseña", entry.password, secret = true, onCopied = copied,
                onBig = { onBig(null) }.takeIf { bigPasswordValue(entry, null) != null },
            )
            Field("Web", entry.url, plain = true, onCopied = copied)
            // 2.0, task 3 (M-4): from the history and from the list of 1.4.3, so none disappears when the history is trimmed.
            PasswordHistorySection(entry.oldPasswords(), onCopied = copied)
            if (entry.seedWords.isNotBlank()) {
                val (n, valid) = seedWordCount(entry.seedWords)
                Field("Frase semilla ($n palabras${if (valid) "" else ", número poco habitual"})", entry.seedWords, secret = true, onCopied = copied)
                Text(
                    "Nunca escribas esta frase en una web ni se la des a nadie: quien la tenga controla tus fondos.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NegativeColor,
                )
            }
            // 2.0, task 5: the template's fields and the user's own, then the notes (with their format, if they have it).
            EntryFieldsCard(entry, onCopied = copied, onBig = { fieldId -> onBig(fieldId) })
            // 2.0, task 9: the code to join this network from another phone, on its own screen (only the id goes there).
            if (entry.type == EntryType.WIFI) OutlinedButton(onClick = onShowWifiQr) { Text(WIFI_QR_OPEN) }
            NotesCard(entry, onCopied = copied)
            entry.totp?.let {
                Field("Clave secreta 2FA", it.secret, secret = true, mono = true, onCopied = copied)
                OutlinedButton(onClick = onShowQr) { Text("Mostrar QR para otra app") }
            }
            AttachmentsSection(store, entry.id, entry.attachments)
            if (entry.history.isNotEmpty() || entry.passwordHistory.isNotEmpty()) {
                OutlinedButton(onClick = onHistory) { Text(historyOpen(entry.history.size, entry.oldPasswords().size)) }
            }
            moveError?.let { Text(it, color = NegativeColor) }
            duplicated?.let { (copyId, copyTitle) ->
                Text(duplicatedText(copyTitle), color = MaterialTheme.colorScheme.primary)
                TextButton(onClick = { onOpenEntry(copyId) }) { Text(DUPLICATE_OPEN) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onEdit) { Text("Editar") }
                if (!entry.type.isPersonal) {
                    OutlinedButton(enabled = !duplicating, onClick = {
                        if (duplicating) return@OutlinedButton
                        duplicating = true
                        scope.launch {
                            try {
                                duplicated = store.duplicate(entry.id)?.let { it.id to plainReportText(it.title) }
                                moveError = null
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                moveError = storeSaveErrorText(e)
                            } finally {
                                duplicating = false
                            }
                        }
                    }) { Text(DUPLICATE) }
                }
                if (!entry.type.isPersonal && (folders.isNotEmpty() || entry.folderId != null)) OutlinedButton(onClick = { moving = true }) { Text(MOVE_TO) }
                TextButton(onClick = { confirm = true }) { Text(MOVE_TO_TRASH, color = NegativeColor) }
            }
        }
    }
    if (moving) {
        EntryFolderPicker(entry.title, folders, onDismiss = { moving = false }) { target ->
            moving = false
            scope.launch {
                try {
                    store.moveEntries(setOf(entry.id), target)
                    moveError = null
                } catch (e: Exception) {
                    moveError = storeSaveErrorText(e)
                }
            }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(moveToTrashTitle(plainReportText(entry.title))) },
            text = {
                FilterObscuredTouches()
                Text(MOVE_TO_TRASH_TEXT)
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    scope.launch {
                        try {
                            // No onBack here: once the entry is gone this screen leaves by itself (LockedNotice, as
                            // after a lock), and a second popBackStack would take the vault list too (1.4.1). 2.0, task
                            // 3: to the trash, with «Deshacer» on the list for 5 s ([UndoTrash]).
                            undo.moveToTrash(setOf(entry.id))
                        } catch (e: Exception) {
                            failed = true
                        }
                    }
                }) { Text(MOVE_TO_TRASH, color = NegativeColor) }
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
private class EntryEditViewModel(private val store: VaultStore, wordLists: WordLists, id: String?, initialType: EntryType) : ViewModel() {
    val form = EntryEditSession(ComposeCells, id, initialType, newId = UUID.randomUUID().toString())

    init {
        (store.state.value as? VaultState.Unlocked)?.let { form.load(it.entries) }
        form.followVault(store.state, viewModelScope)
        // 2.0, task 7: the BIP-39 lists a paste is read with, once per process and off the main thread (not secret).
        viewModelScope.launch { form.seedLists = wordLists.seedLists() }
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
        when (val read = readOtpAuth(text)) {
            is OtpAuthRead.Usable -> {
                val parsed = read.secret
                form.totp.value = parsed
                if (form.title.value.isBlank()) form.title.value = parsed.issuer.ifBlank { parsed.account }
                form.error.value = null
            }
            // A valid 2FA code whose settings this app does not calculate is said so, instead of "not a 2FA code".
            is OtpAuthRead.UnsupportedSettings -> form.error.value = TOTP_UNSUPPORTED_LINK
            OtpAuthRead.NotUsable -> form.error.value = "El QR o enlace no es un código 2FA (otpauth://totp/…)"
        }
    }

    fun save(entry: VaultEntry) {
        if (form.saving.value) return
        form.saving.value = true
        // Re-review 1: the lock epoch of this «Guardar»: a lock before the store takes the save refuses it, also if the
        // vault was opened again meanwhile (never a late write after a lock).
        val sinceLock = store.lockEpoch
        viewModelScope.launch {
            try {
                if (store.upsert(entry, sinceLock = sinceLock)) {
                    // Review I-1: saved from the dialog of a tab, the tab opens once the entry is saved.
                    val go = form.takeLeave()
                    form.finish()
                    go?.invoke()
                } else if (form.loaded) {
                    // Review M-3: an entry that went to the trash meanwhile is said so, not «se bloqueó».
                    form.error.value = saveRefusedText(store.state.value, entry.id)
                }
            } catch (x: CancellationException) {
                throw x
            } catch (x: Exception) {
                // 2.0, task 5: a limit says which, with its own text ([storeSaveErrorText]); anything else, the store's fixed text.
                if (form.loaded) form.error.value = storeSaveErrorText(x)
            } finally {
                form.saving.value = false
                form.takeLeave() // A save that failed goes nowhere: the form stays, with its error.
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

    /** «Sí» to the question after a paste: a 2FA link is read exactly as a scan here ([applyOtpAuth]). */
    fun acceptPaste() {
        form.acceptPaste()?.let(::applyOtpAuth)
    }

    /**
     * «Guardar» (the button, and the one of «¿Guardar los cambios?»): what is on the form, checked, as the entry to save;
     * the first problem goes to [EntryEditSession.error] instead, and the form stays.
     */
    fun trySave() {
        if (form.saving.value) return // A second tap before the button redraws as disabled.
        val existing = form.existing.value
        val type = form.type.value
        val title = form.title.value
        val totp = form.totp.value
        val seed = form.seed.value
        val now = System.currentTimeMillis()
        form.error.value = when {
            title.isBlank() -> "Ponle un nombre"
            type == EntryType.TOTP && !isUsableTotpKey(totp.secret) -> "La clave secreta no es válida (Base32: letras A-Z y números 2-7, al menos 16)"
            type == EntryType.SEED && seedWordCount(seed).first == 0 -> "Escribe las palabras"
            else -> null
        }
        if (form.error.value != null) return
        val fields = when (val f = fieldsToSave(type, form.templateValues.value, form.customFields.value)) {
            is FormFields.Ok -> f.fields
            is FormFields.Problem -> {
                form.error.value = f.text
                return
            }
        }
        // Re-review N-2: «Caduca el» only where it is on screen.
        val expiresAt = entryExpiresAt(type, form.expiresText.value).getOrElse {
            form.error.value = it.message
            return
        }
        val e = VaultEntry(
            id = form.entryId,
            type = type,
            title = title.trim(),
            username = if (type == EntryType.LOGIN) form.username.value.trim() else "",
            password = passwordToSave(type, form.loginPassword.value, form.seedPassphrase.value),
            url = if (type == EntryType.LOGIN) form.url.value.trim() else "",
            notes = form.notes.value,
            seedWords = if (type == EntryType.SEED) seed.trim().split(Regex("\\s+")).joinToString(" ") { it.lowercase() } else "",
            totp = if (type == EntryType.TOTP) totp.copy(secret = totp.secret.uppercase().filter { it.isLetterOrDigit() }) else null,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            favorite = form.favorite.value,
            tags = normalizeTags(form.tagsText.value),
            fields = fields,
            notesFormat = form.notesFormat.value,
            // Review M-4: a template with its own date of expiry has that one only.
            expiresAt = expiresAt,
            rotateMonths = if (rotates(type)) form.rotateMonths.value else null,
        )
        // The limits of one entry, with their own text, before the store refuses it (an old entry that only moves keeps
        // its size; one that is edited must fit).
        e.entryLimitProblem()?.let {
            form.error.value = it
            return
        }
        save(e)
    }

    /**
     * «Guardar» of «¿Guardar los cambios?»: [trySave]; if the form does not pass its checks, the dialog stays and says why;
     * otherwise it goes, and where it was going ([EntryEditSession.takeLeave]) follows the save.
     */
    fun saveFromDialog() {
        trySave()
        if (form.error.value == null) form.savingToLeave()
    }

    override fun onCleared() {
        form.cleared()
    }
}

/**
 * A new entry ([id] null) or an existing one. Its state lives in [EntryEditViewModel] (a rotation keeps it). [otpAuthText]
 * is a 2FA text the QR inspector read (`otpauth://` or a Google Authenticator export): applied once per session, exactly
 * as a scan here would be; [onOtpAuthTaken] then lets the caller drop its own copy. [wifiQrText] (2.0, task 9) is a wifi
 * code the inspector read: it fills a new wifi's fields once per session ([EntryEditSession.applyWifiQr]), never saves;
 * [onWifiQrTaken] the same. Only one of the two is ever given.
 */
@Composable
fun VaultEntryEditScreen(
    store: VaultStore,
    id: String?,
    initialType: EntryType,
    onDone: () -> Unit,
    otpAuthText: String? = null,
    onOtpAuthTaken: () -> Unit = {},
    wifiQrText: String? = null,
    onWifiQrTaken: () -> Unit = {},
) {
    VaultIdleLock(store)
    val state by store.state.collectAsStateWithLifecycle()
    val unlocked = state as? VaultState.Unlocked ?: return LockedNotice(onDone)
    val lists = wordLists()
    val vm = viewModel { EntryEditViewModel(store, lists, id, initialType) }
    val form = vm.form
    val context = LocalContext.current
    val activity = context.fragmentActivity()
    // Only a rotation (or another configuration change) keeps what was typed: leaving otherwise lets it go now.
    DisposableEffect(vm) {
        onDispose { form.left(changingConfigurations = activity?.isChangingConfigurations == true) }
    }
    // Review I-1 of task 7: the tabs ask this form before they leave it (only whether it has changes, and its dialog).
    val leaveGuard = LocalLeaveGuard.current
    DisposableEffect(form, leaveGuard) {
        val off = leaveGuard?.let(form::guardedBy)
        onDispose { off?.invoke() }
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
    LaunchedEffect(type, title, username, loginPassword, seedPassphrase, url, notes, seed, totp, tagsText, form.templateValues.value, form.customFields.value, form.expiresText.value) { store.touch() }

    LaunchedEffect(Unit) {
        form.takeHandOver(otpAuthText)?.let(vm::applyOtpAuth)
        onOtpAuthTaken()
        form.takeHandOver(wifiQrText)?.let(form::applyWifiQr)
        onWifiQrTaken()
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

    // 2.0, task 7: «Atrás» with changes asks first (the bar's arrow, the system's back and its predictive gesture); with
    // none, the system's back goes as always (and leaving clears the form, [left]).
    BackHandler(enabled = form.hasUnsavedChanges) { form.back() }
    BackScaffold(if (existing == null) "Nueva entrada" else "Editar", form::back) { padding ->
        NoCopy {
            // M-9: imePadding, so the notes and the last fields stay above the keyboard with edge-to-edge.
            Column(Modifier.padding(padding).imePadding().touchesVault(store).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (existing == null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        VAULT_ENTRY_TYPES.forEach { t -> FilterChip(selected = type == t, onClick = { form.changeType(t) }, label = { Text("${typeIcon(t)} ${t.label}") }) }
                    }
                }
                OutlinedTextField(title, { form.typed(FormSlot.TITLE, it) }, label = { Text("Nombre (p. ej. «Correo» o «Cartera»)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                when (type) {
                    EntryType.LOGIN -> {
                        OutlinedTextField(username, { form.typed(FormSlot.USERNAME, it) }, label = { Text("Usuario o correo") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        // 2.0, task 7: the strength while it is typed, and the generator next to it.
                        PasswordWithTools(loginPassword, { form.typed(FormSlot.PASSWORD, it) }, label = "Contraseña", onGenerated = { loginPassword = it })
                        // The title, if it is empty, takes the domain of the web (EntryEditSession.typed, titleFromUrl).
                        OutlinedTextField(url, { form.typed(FormSlot.URL, it) }, label = { Text("Web") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                    EntryType.SEED -> {
                        OutlinedTextField(
                            seed,
                            { form.typed(FormSlot.SEED, it) },
                            label = { Text("Palabras de recuperación, separadas por espacios") },
                            minLines = 3,
                            // A password field: well-behaved keyboards (Gboard among them) neither suggest from it nor sync it. Not
                            // learning is also asked of every field by NoLearningInput; both are up to the keyboard to honour.
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        val (n, valid) = seedWordCount(seed)
                        if (n > 0) Text("$n palabras" + if (valid) " ✓" else " (lo habitual son 12, 15, 18, 21 o 24)", style = MaterialTheme.typography.bodySmall)
                        PasswordWithTools(seedPassphrase, { seedPassphrase = it }, label = "Passphrase BIP-39 (opcional)", onGenerated = { seedPassphrase = it })
                        Text(
                            "Una passphrase crea una cartera distinta. Guárdala en un sitio distinto de la frase: quien tenga solo la frase no accede a esos fondos. Si la pierdes, tú tampoco.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    EntryType.NOTE, EntryType.IDENTITY, EntryType.CONTACT -> Unit // The last two have their own tab.
                    // 2.0, task 5: the fixed fields of their template.
                    EntryType.CARD, EntryType.WIFI, EntryType.LICENSE, EntryType.PASSPORT -> TemplateFieldsEditor(form, type)
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
                // 2.0, task 5: the user's own fields, the notes (with their format if chosen) and the dates of expiry.
                CustomFieldsEditor(form)
                NotesEditor(form, type)
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
                ExpiryEditor(form, type)
                if (existing == null) {
                    Text("Guarda la entrada para poder añadirle adjuntos.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    // The live list: adding or removing saves at once, apart from the rest of the form.
                    AttachmentsSection(store, existing.id, unlocked.entries.firstOrNull { it.id == existing.id }?.attachments.orEmpty())
                }
                error?.let { Text(it, color = NegativeColor) }
                info?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                Button(
                    onClick = vm::trySave,
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
                            1 -> "Se añadirá 1 código nuevo a la bóveda: ${plainReportText(entries.single().title)}."
                            else -> "Se añadirán ${entries.size} códigos nuevos a la bóveda: ${entries.joinToString(", ") { plainReportText(it.title) }}."
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
    // 2.0, task 7: a paste that looks like a 2FA link or a seed phrase: the question, never the pasted text.
    form.pasteOffer.value?.let { offer ->
        AlertDialog(
            onDismissRequest = form::declinePaste,
            text = {
                FilterObscuredTouches()
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (offer.kind == PastedKind.OTPAUTH) PASTE_OTPAUTH_QUESTION else PASTE_SEED_QUESTION)
                    Text(PASTE_MOVES, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = { TextButton(onClick = vm::acceptPaste) { Text(PASTE_YES) } },
            dismissButton = { TextButton(onClick = form::declinePaste) { Text(PASTE_NO) } },
        )
    }
    // 2.0, task 7: «Atrás» with changes. Closing the dialog any other way is «Seguir editando».
    if (form.askingToLeave.value) {
        AlertDialog(
            onDismissRequest = form::keepEditing,
            title = {
                FilterObscuredTouches()
                Text(UNSAVED_TITLE)
            },
            // What stops the save (a missing name…) is said here, where the user is looking, not at the end of the form.
            text = error?.let { e -> { Text(e, color = NegativeColor) } },
            // Review m-3: one button per line, so none is cut with the letter at 200 % (R-1).
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(enabled = !saving, onClick = vm::saveFromDialog) { Text(UNSAVED_SAVE) }
                    // Review m-2: not while a save is under way (it would save all the same).
                    TextButton(enabled = !saving, onClick = form::discard) { Text(UNSAVED_DISCARD, color = NegativeColor) }
                    TextButton(onClick = form::keepEditing) { Text(UNSAVED_KEEP) }
                }
            },
        )
    }
}
