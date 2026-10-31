package app.seguridad.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.seguridad.SeguridadApp
import app.seguridad.android.copySensitive
import app.seguridad.core.readAtMost
import app.seguridad.crypto.Aead
import app.seguridad.crypto.Bip39
import app.seguridad.crypto.CryptoFailure
import app.seguridad.crypto.ENCRYPTED_EXTENSION
import app.seguridad.crypto.KdfParams
import app.seguridad.crypto.LEGACY_ENCRYPTED_EXTENSION
import app.seguridad.crypto.MAX_TEXT_FILE_BYTES
import app.seguridad.crypto.PassphraseOptions
import app.seguridad.crypto.PasswordOptions
import app.seguridad.crypto.Secret
import app.seguridad.crypto.Strength
import app.seguridad.crypto.TEXT_PREFIX
import app.seguridad.crypto.decryptStream
import app.seguridad.crypto.decryptText
import app.seguridad.crypto.encryptStream
import app.seguridad.crypto.encryptText
import app.seguridad.crypto.extractToken
import app.seguridad.crypto.generatePassphrase
import app.seguridad.crypto.generatePassword
import app.seguridad.crypto.inspectText
import app.seguridad.crypto.isRawKeyText
import app.seguridad.crypto.newPasswordFailureMessage
import app.seguridad.crypto.newRawKeyHex
import app.seguridad.crypto.parseRawKey
import app.seguridad.crypto.passphraseEntropy
import app.seguridad.crypto.passwordEntropy
import app.seguridad.crypto.safeFileName
import app.seguridad.crypto.sameHash
import app.seguridad.crypto.sha256Hex
import app.seguridad.crypto.strength
import app.seguridad.net.PwnedPasswords
import app.seguridad.ui.theme.BackScaffold
import app.seguridad.ui.theme.CautionColor
import app.seguridad.ui.theme.NegativeColor
import app.seguridad.ui.theme.NoCopy
import app.seguridad.ui.theme.PasswordField
import app.seguridad.ui.theme.PositiveColor
import app.seguridad.ui.theme.SectionCard
import app.seguridad.vault.PickedKind
import app.seguridad.vault.encryptedFileName
import app.seguridad.vault.pickedKind
import app.seguridad.vault.sanitizeReceivedName
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.seguridad.tools.TOOLS_AWAY_WIPE_NOTE
import app.seguridad.tools.cipher.BreachSession
import app.seguridad.tools.cipher.FileCryptoSession
import app.seguridad.tools.cipher.FileHashSession
import app.seguridad.tools.cipher.PendingCopy
import app.seguridad.tools.cipher.TextCryptoSession
import app.seguridad.tools.codes.ScreenGuard
import app.seguridad.vault.VaultStore
import java.io.IOException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.StateFlow

/** Every algorithm with what it is; AES-256 first as the sensible default. */
@Composable
private fun AlgorithmPicker(alg: Aead, onChange: (Aead) -> Unit) {
    Column {
        Aead.entries.forEach { a ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable { onChange(a) }.padding(vertical = 4.dp),
            ) {
                RadioButton(selected = a == alg, onClick = { onChange(a) })
                Column {
                    Text(a.label, style = MaterialTheme.typography.titleSmall)
                    Text(a.detail.substringAfter(": "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Password (stretched with Argon2id) or a random AES-256 key the user keeps elsewhere and never the fingerprint. */
@Composable
private fun SecretInput(
    useKey: Boolean,
    onUseKey: (Boolean) -> Unit,
    password: String,
    onPassword: (String) -> Unit,
    keyHex: String,
    onKeyHex: (String) -> Unit,
    allowGenerate: Boolean,
    guard: ScreenGuard,
) {
    val context = LocalContext.current
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        SegmentedButton(selected = !useKey, onClick = { onUseKey(false) }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Contraseña") }
        SegmentedButton(selected = useKey, onClick = { onUseKey(true) }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Clave AES-256") }
    }
    if (!useKey) {
        PasswordField(password, { guard.typed(); onPassword(it) })
        Text(
            "Se convierte en clave con Argon2id. Usa una larga: una frase de 6 palabras del Generador es ideal.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        OutlinedTextField(
            value = keyHex,
            onValueChange = { guard.typed(); onKeyHex(it) },
            label = { Text("Clave (64 caracteres hexadecimales)") },
            minLines = 2,
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            isError = keyHex.isNotBlank() && !isRawKeyText(keyHex),
            supportingText = { if (keyHex.isNotBlank() && !isRawKeyText(keyHex)) Text("No es una clave de 256 bits válida") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (allowGenerate) FilledTonalButton(onClick = { onKeyHex(newRawKeyHex()) }) { Text("🎲 Generar clave") }
            if (isRawKeyText(keyHex)) OutlinedButton(onClick = { copySensitive(context, keyHex) }) { Text("Copiar clave") }
        }
        Text(
            "Una clave aleatoria de 256 bits: en la práctica, nadie puede adivinarla. Apúntala en papel y guárdala SEPARADA del texto cifrado: " +
                "sin ella nadie, ni tú, puede descifrarlo.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Whether a secret has been entered; unlike [secretOf] it builds no copy of it (called on every recomposition). */
private fun hasSecret(useKey: Boolean, password: String, keyHex: String): Boolean =
    if (useKey) isRawKeyText(keyHex) else password.isNotEmpty()

/** The secret to use now; the caller wipes it when done. */
private fun secretOf(useKey: Boolean, password: String, keyHex: String): Secret? =
    if (useKey) parseRawKey(keyHex)?.let { Secret.RawKey(it) } else password.takeIf { it.isNotEmpty() }?.let { Secret.Password(it.toCharArray()) }

private fun Secret.wipe() = when (this) {
    is Secret.Password -> chars.fill('\u0000')
    is Secret.RawKey -> bytes.fill(0)
}

@Composable
private fun ModePicker(encrypt: Boolean, onChange: (Boolean) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        SegmentedButton(selected = encrypt, onClick = { onChange(true) }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Cifrar") }
        SegmentedButton(selected = !encrypt, onClick = { onChange(false) }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Descifrar") }
    }
}

@Composable
private fun Busy(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A tool's scrolling column; with a [guard] (COV1-1), any tap or key on it is use of the screen, for the idle lock. */
@Composable
private fun ToolColumn(title: String, onBack: () -> Unit, guard: ScreenGuard? = null, content: @Composable () -> Unit) {
    BackScaffold(title, onBack) { padding ->
        NoCopy {
            Column(
                Modifier.padding(padding).then(if (guard != null) Modifier.usedFor(guard) else Modifier)
                    .verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) { content() }
        }
    }
}

/**
 * «Cifrar texto»'s form and result for its navigation entry (COV1-1, audit 1.3.0): a rotation, a theme change or another
 * tab keeps them, the death of the process does not (never in saved state: the text is often a seed phrase). The
 * session's guard empties it by the «PIN y códigos» rule. Encrypting, decrypting, importing and saving run in
 * [viewModelScope], so a rotation cuts none of them. The BIP-39 list is read here, off the main thread.
 */
private class TextCryptoViewModel(
    private val appContext: Context,
    store: VaultStore,
    lockAfterMs: () -> Long,
    awayInOtherApp: StateFlow<Boolean>,
) : ViewModel() {
    val session = TextCryptoSession(ComposeCells, SystemClock.elapsedRealtime())
    var bip39 by mutableStateOf<Bip39?>(null)
        private set

    init {
        watchAway(session.guard, store, lockAfterMs, awayInOtherApp)
        viewModelScope.launch {
            bip39 = withContext(Dispatchers.IO) {
                try {
                    Bip39(appContext.assets.open("bip39_english.txt").bufferedReader().use { it.readLines() }.filter { it.isNotBlank() })
                } catch (e: IOException) {
                    null // Never happens with the APK's own asset; the screen then stays without the seed helper.
                }
            }
        }
    }

    fun reset() {
        session.output = null
        session.error = null
    }

    /** Encrypts or decrypts what the form holds now. */
    fun run(kdf: KdfParams) {
        val s = session
        val words = bip39 ?: return
        val secret = secretOf(s.useKey, s.password, s.keyHex) ?: return
        // Snapshot the request before launching asynchronous work; controls remain interactive while busy.
        val encryptNow = s.encrypt
        val useKeyNow = s.useKey
        val algNow = s.alg
        val inputNow = s.input
        val plainNow = if (s.seedMode) words.normalize(inputNow).joinToString(" ") else inputNow
        val run = s.token()
        s.busy = true
        reset()
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.Default) {
                    if (encryptNow) encryptText(plainNow, secret, algNow, kdf) else decryptText(inputNow, secret)
                }
                // The form stays editable while busy: a result for a mode or text the user has since changed is dropped,
                // and never wipes what they typed after starting. Nor one for a screen emptied meanwhile (COV1-1).
                if (s.isCurrent(run) && s.encrypt == encryptNow && s.input == inputNow) {
                    s.output = result
                    // The plaintext (often a seed phrase) is no longer needed once it is encrypted. Only on success: a
                    // failed encryption must not lose what the user typed.
                    if (encryptNow) s.input = ""
                }
            } catch (e: CryptoFailure) {
                if (s.isCurrent(run)) {
                    s.error = if (useKeyNow && e.message == CryptoFailure().message) "Clave incorrecta o texto dañado" else e.message
                }
            } finally {
                secret.wipe()
                // The operation has consumed the entered password; never leave it in the form after use.
                if (!useKeyNow) s.password = ""
                s.busy = false
            }
        }
    }

    /** Saves the ciphertext to [dest], from the save dialog; a document not written is deleted. */
    fun saveTo(dest: Uri) {
        val text = session.output ?: run {
            // The screen was emptied while the dialog was open (or the app restarted): nothing to write.
            discardDocumentLater(appContext, dest)
            session.fileMessage = false to "No se ha guardado: vuelve a cifrar el texto."
            return
        }
        val useKey = session.useKey
        viewModelScope.launch {
            session.fileMessage = try {
                withContext(Dispatchers.IO) {
                    appContext.contentResolver.openOutputStream(dest, "wt")!!.use { it.write((text + "\n").toByteArray(Charsets.UTF_8)) }
                }
                true to "Guardado. El archivo solo contiene el texto cifrado: sin tu ${if (useKey) "clave" else "contraseña"} no sirve de nada."
            } catch (e: CancellationException) {
                discardDocumentNow(appContext, dest)
                throw e
            } catch (e: Exception) {
                discardDocumentNow(appContext, dest)
                false to "No se pudo guardar el archivo."
            }
        }
    }

    /** Reads a ciphertext from [uri] into the form. The provider's name is read off the main thread and sanitised. */
    fun importFrom(uri: Uri) {
        val run = session.token()
        viewModelScope.launch {
            val read = try {
                withContext(Dispatchers.IO) {
                    val text = appContext.contentResolver.openInputStream(uri)!!.use { it.readAtMost(MAX_TEXT_FILE_BYTES + 1) }
                        .takeIf { it.size <= MAX_TEXT_FILE_BYTES }?.toString(Charsets.UTF_8)
                    text to sanitizeReceivedName(displayName(appContext, uri))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            if (!session.isCurrent(run)) return@launch
            val text = read?.first
            val token = text?.let(::extractToken)
            if (token != null) {
                session.input = token
                reset()
                session.fileMessage = true to "Texto cifrado importado de ${read.second}."
            } else {
                session.fileMessage = false to if (text == null) "No se pudo leer el archivo." else "Ese archivo no contiene un texto cifrado ($TEXT_PREFIX… o $LEGACY_TEXT_PREFIX…)."
            }
        }
    }

    override fun onCleared() {
        session.wipe()
    }
}

/**
 * Encrypts with [kdf] (Ajustes: coste de Argon2id); decrypting reads the cost from the ciphertext itself. What is typed
 * and the result live in [TextCryptoViewModel] and go by the «PIN y códigos» rule (COV1-1): back after
 * `max(lock time, 60 s)` away ([lockAfterMs], [awayInOtherApp]) or once the vault locks with the app in use.
 */
@Composable
fun TextCryptoScreen(kdf: KdfParams, store: VaultStore, lockAfterMs: () -> Long, awayInOtherApp: StateFlow<Boolean>, onBack: () -> Unit) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val vm = viewModel { TextCryptoViewModel(appContext, store, lockAfterMs, awayInOtherApp) }
    val s = vm.session
    GuardEffects(s.guard, lockAfterMs)
    val bip39 = vm.bip39
    val saveTxt = rememberDocumentPicker(ActivityResultContracts.CreateDocument("text/plain")) { dest ->
        if (dest != null) vm.saveTo(dest)
    }
    val openTxt = rememberDocumentPicker(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importFrom(uri)
    }

    ToolColumn("Cifrar texto", onBack, s.guard) {
        ModePicker(s.encrypt) {
            s.encrypt = it
            s.input = ""
            s.fileMessage = null
            vm.reset()
        }
        if (s.encrypt) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = s.seedMode, onClick = { s.seedMode = true; vm.reset() }, label = { Text("🌱 Frase semilla") })
                FilterChip(selected = !s.seedMode, onClick = { s.seedMode = false; vm.reset() }, label = { Text("Texto libre") })
            }
            OutlinedTextField(
                value = s.input,
                onValueChange = { s.guard.typed(); s.input = if (s.seedMode) it.lowercase() else it; vm.reset() },
                label = { Text(if (s.seedMode) "Las 12, 18 o 24 palabras de tu cartera" else "Texto a cifrar") },
                minLines = 4,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            if (s.seedMode && bip39 != null) SeedHelper(bip39, s.input) { s.guard.typed(); s.input = it }
        } else {
            OutlinedTextField(
                value = s.input,
                onValueChange = { s.guard.typed(); s.input = it; vm.reset() },
                label = { Text("Texto cifrado (empieza por $TEXT_PREFIX o $LEGACY_TEXT_PREFIX)") },
                minLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(onClick = {
                s.fileMessage = openTxt.launchOrError(arrayOf("text/plain", "application/octet-stream", "*/*"))?.let { false to it }
            }, modifier = Modifier.fillMaxWidth()) {
                Text("📂 Importar archivo .txt")
            }
            val detected = remember(s.input) { inspectText(s.input) }
            if (detected != null) {
                val (a, raw) = detected
                LaunchedEffect(a, raw) { s.useKey = raw }
                Text("✓ Cifrado con ${a.label} y ${if (raw) "una clave AES-256" else "una contraseña"}.", color = PositiveColor)
            } else {
                Text(
                    "No hace falta elegir el algoritmo: se detecta solo (${Aead.entries.joinToString(", ") { it.label }}).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        SecretInput(
            s.useKey, { s.useKey = it; vm.reset() }, s.password, { s.password = it; vm.reset() }, s.keyHex, { s.keyHex = it; vm.reset() },
            allowGenerate = s.encrypt,
            guard = s.guard,
        )
        if (s.encrypt) {
            Text("Algoritmo", style = MaterialTheme.typography.titleSmall)
            AlgorithmPicker(s.alg) { s.alg = it }
        }
        Button(
            enabled = !s.busy && bip39 != null && s.input.isNotBlank() && hasSecret(s.useKey, s.password, s.keyHex),
            onClick = { vm.run(kdf) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (s.encrypt) "Cifrar" else "Descifrar") }
        if (s.busy) Busy(if (s.useKey) "Procesando…" else "Derivando la clave con Argon2id…")
        s.error?.let { Text(it, color = NegativeColor) }
        s.fileMessage?.let { (ok, text) -> Text(text, color = if (ok) PositiveColor else NegativeColor) }
        s.output?.let { out ->
            val encrypt = s.encrypt
            val seedWords = if (encrypt) null else bip39?.takeIf { b -> b.check(out).let { it.validCount && it.unknown.isEmpty() } }?.normalize(out)
            SectionCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        when {
                            encrypt -> "Texto cifrado"
                            seedWords != null -> "Frase semilla (${seedWords.size} palabras)"
                            else -> "Texto original"
                        },
                        style = MaterialTheme.typography.labelMedium,
                    )
                    when {
                        seedWords != null -> SeedGrid(seedWords)
                        // The ciphertext can be selected to read it; inside NoCopy the menu has no Copy (TECLADO-1), so both
                        // the ciphertext and the decrypted text leave through «Copiar» (sensitive, cleared) or «Compartir».
                        encrypt -> SelectionContainer { Text(out, fontFamily = FontFamily.Monospace) }
                        else -> Text(out)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { copySensitive(context, out) }) { Text("Copiar") }
                        if (encrypt) {
                            OutlinedButton(onClick = {
                                if (!shareText(context, out, "Compartir")) s.fileMessage = false to "No se pudo abrir el menú de compartir"
                            }) { Text("Compartir") }
                        }
                    }
                    if (encrypt) {
                        OutlinedButton(
                            onClick = {
                                s.fileMessage = saveTxt.launchOrError(if (s.seedMode) "frase-cifrada.txt" else "texto-cifrado.txt")?.let { false to it }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("💾 Guardar como archivo .txt") }
                    }
                    if (encrypt) {
                        Text(
                            "Guarda este texto donde quieras (nube, correo, papel). Sin tu ${if (s.useKey) "clave" else "contraseña"} es inútil. " +
                                "Antes de borrar tu copia original, prueba a descifrarlo.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        AwayNote()
    }
}

/** COV1-1: what this screen keeps, and for how long, said under it. */
@Composable
private fun AwayNote() {
    Text(TOOLS_AWAY_WIPE_NOTE, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Word count, BIP-39 checksum, misspelled words with a one-tap fix, and completions for the word being typed. */
@Composable
private fun SeedHelper(bip39: Bip39, input: String, onChange: (String) -> Unit) {
    if (input.isBlank()) return
    val check = bip39.check(input)
    val words = bip39.normalize(input)
    val typing = if (input.endsWith(" ")) "" else words.lastOrNull().orEmpty()
    val completions = if (typing.isNotEmpty() && typing !in bip39.words) bip39.complete(typing) else emptyList()
    if (completions.isNotEmpty()) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            completions.forEach { w ->
                AssistChip(onClick = { onChange((words.dropLast(1) + w).joinToString(" ") + " ") }, label = { Text(w) })
            }
        }
    }
    check.unknown.filter { it.word != typing }.forEach { u ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Palabra ${u.position}: «${u.word}» no es BIP-39.", color = NegativeColor, modifier = Modifier.weight(1f))
            u.suggestion?.let { s ->
                TextButton(onClick = { onChange(words.mapIndexed { i, w -> if (i == u.position - 1) s else w }.joinToString(" ")) }) { Text("¿$s?") }
            }
        }
    }
    val (text, color) = when {
        check.checksumOk == true -> "✓ ${check.count} palabras · checksum BIP-39 correcto" to PositiveColor
        check.checksumOk == false -> "⚠ ${check.count} palabras pero el checksum no cuadra: revisa el orden o alguna palabra" to NegativeColor
        !check.validCount -> "${check.count} palabras (lo habitual son 12, 15, 18, 21 o 24)" to MaterialTheme.colorScheme.onSurfaceVariant
        else -> "${check.count} palabras" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(text, color = color, style = MaterialTheme.typography.bodySmall)
}

/** The recovered words numbered, as wallets show them. */
@Composable
private fun SeedGrid(words: List<String>) {
    val columns = 3
    val rows = (words.size + columns - 1) / columns
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (r in 0 until rows) {
            Row {
                for (c in 0 until columns) {
                    val i = c * rows + r
                    Text(
                        words.getOrNull(i)?.let { "${(i + 1).toString().padStart(2)}. $it" }.orEmpty(),
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** Deletes a document the save dialog created when nothing could be written to it, instead of leaving it empty. */
internal fun discardDocument(context: Context, uri: Uri) {
    runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
}

/**
 * [discardDocument] from a callback (not a coroutine): in `Dispatchers.IO` on the app's scope (AppContainer.appScope), so
 * it finishes even if the screen goes away.
 */
internal fun discardDocumentLater(context: Context, uri: Uri) {
    val app = context.applicationContext
    val scope = (app as? SeguridadApp)?.container?.appScope ?: CoroutineScope(Dispatchers.IO)
    scope.launch(Dispatchers.IO) { discardDocument(app, uri) }
}

/** [discardDocument] from a coroutine: off the main thread, and not cut short by a cancellation. */
internal suspend fun discardDocumentNow(context: Context, uri: Uri) {
    withContext(NonCancellable + Dispatchers.IO) { discardDocument(context, uri) }
}

internal fun displayName(context: Context, uri: Uri): String =
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    } ?: "archivo"

/** Decrypted files wait here, in the app's private cache, until saved; leftovers are wiped at start. */
const val DECRYPT_TMP_PREFIX = "dec"

/** Prefix of the legacy texts, still decrypted (new ones always start with [TEXT_PREFIX]). */
private const val LEGACY_TEXT_PREFIX = "CP1:"

/** A decryption that ended: the name to propose in the save dialog, for the run [run] (its copy is the session's pending one). */
private class DecryptedReady(val name: String, val run: Long)

/** Deletes [path] off the main thread on the app's scope, so it finishes even if the screen goes away (as [discardDocumentLater]). */
private fun deleteLater(context: Context, path: String) {
    val app = context.applicationContext
    val scope = (app as? SeguridadApp)?.container?.appScope ?: CoroutineScope(Dispatchers.IO)
    scope.launch(NonCancellable + Dispatchers.IO) { File(path).delete() }
}

/**
 * «Cifrar archivos» for its navigation entry (COV1-1, audit 1.3.0): the mode, the algorithm, the chosen file, the
 * password or key and the last message survive a rotation or another tab, never the death of the process (never in
 * saved state). The session's guard empties the password, the key and the message by the «PIN y códigos» rule.
 * Encrypting, decrypting and writing the decrypted copy run in [viewModelScope] with the application's resolver, so a
 * rotation cuts none of them; only the save dialogs are the screen's.
 */
private class FileCryptoViewModel(
    private val appContext: Context,
    store: VaultStore,
    lockAfterMs: () -> Long,
    awayInOtherApp: StateFlow<Boolean>,
) : ViewModel() {
    /** M2 of the F6 review: a wipe gives the decrypted copy waiting for the save dialog up, and deletes it. */
    val session = FileCryptoSession(ComposeCells, SystemClock.elapsedRealtime(), onDiscard = { deleteLater(appContext, it) })
    var encrypt by mutableStateOf(true)
    var alg by mutableStateOf(Aead.AES_256_GCM)
    var source by mutableStateOf<Uri?>(null)

    /** The chosen file's name from its provider; null until it is read (M7: off the main thread). */
    var sourceName by mutableStateOf<String?>(null)
        private set
    private var naming = 0L

    /** Set when a decryption ends; the screen opens the save dialog for it and clears it. */
    var decrypted by mutableStateOf<DecryptedReady?>(null)

    init {
        watchAway(session.guard, store, lockAfterMs, awayInOtherApp)
    }

    /**
     * Another file (or none): its name is read from the provider on IO (a slow cloud provider must not block the main
     * thread), for this pick only; until then «Cifrar y guardar» stays off.
     */
    fun choose(uri: Uri?) {
        source = uri
        sourceName = null
        val pick = ++naming
        if (uri == null) return
        viewModelScope.launch {
            val name = withContext(Dispatchers.IO) { runCatching { displayName(appContext, uri) }.getOrDefault("archivo") }
            if (pick == naming) sourceName = name
        }
    }

    /** Encrypts the chosen file into [dest], from the save dialog; a document not fully written is deleted. */
    fun encryptTo(dest: Uri, kdf: KdfParams) {
        val s = session
        val src = source
        val name = sourceName
        val clearPasswordAfterUse = !s.useKey
        val secret = secretOf(s.useKey, s.password, s.keyHex)
        if (src == null || name == null || secret == null) {
            // The screen was emptied or rebuilt while the dialog was open: the file and secret are never kept in saved state.
            secret?.wipe()
            discardDocumentLater(appContext, dest)
            s.message = false to "No se ha guardado: vuelve a elegir el archivo y la contraseña o clave."
            return
        }
        val algNow = alg
        s.busy = true
        viewModelScope.launch {
            s.message = try {
                withContext(Dispatchers.IO) {
                    appContext.contentResolver.openInputStream(src)!!.use { input ->
                        appContext.contentResolver.openOutputStream(dest, "wt")!!.use { out -> encryptStream(input.buffered(), out.buffered(), name, secret, algNow, kdf) }
                    }
                }
                true to "Archivo cifrado y guardado."
            } catch (e: CancellationException) {
                discardDocumentNow(appContext, dest) // Never leave a truncated file behind.
                throw e
            } catch (e: Exception) {
                discardDocumentNow(appContext, dest)
                false to newPasswordFailureMessage(e, "No se pudo cifrar el archivo.") // Review F5, M2.
            } finally {
                secret.wipe()
                if (clearPasswordAfterUse) s.password = ""
                s.busy = false
            }
        }
    }

    /** Decrypts the chosen file into the app's private cache; [decrypted] then asks the screen for a place to save it. */
    fun decrypt() {
        val s = session
        val src = source ?: return
        val clearPasswordAfterUse = !s.useKey
        val secret = secretOf(s.useKey, s.password, s.keyHex) ?: return
        val run = s.token()
        s.busy = true
        viewModelScope.launch {
            var tmp: File? = null
            try {
                val file = withContext(Dispatchers.IO) { File.createTempFile(DECRYPT_TMP_PREFIX, null, appContext.cacheDir) }
                tmp = file
                val name = withContext(Dispatchers.IO) {
                    appContext.contentResolver.openInputStream(src)!!.use { input ->
                        file.outputStream().buffered().use { out -> decryptStream(input.buffered(), out, secret) }
                    }
                }
                // Emptied meanwhile (COV1-1): the decrypted data is not offered for saving.
                if (s.isCurrent(run)) {
                    s.pending?.let { deleteLater(appContext, it.path) } // An earlier copy never saved.
                    s.pending = PendingCopy(file.path, run)
                    decrypted = DecryptedReady(name, run)
                } else {
                    deleteNow(file)
                }
            } catch (e: CryptoFailure) {
                deleteNow(tmp)
                if (s.isCurrent(run)) s.message = false to (e.message ?: "Contraseña incorrecta o archivo dañado")
            } catch (e: CancellationException) {
                deleteNow(tmp) // Never leave decrypted data behind in the cache.
                throw e
            } catch (e: Exception) {
                deleteNow(tmp)
                if (s.isCurrent(run)) s.message = false to "No se pudo leer el archivo."
            } finally {
                secret.wipe()
                if (clearPasswordAfterUse) s.password = ""
                s.busy = false
            }
        }
    }

    /**
     * N5 of the F6 re-review: deleting a decrypted copy (it may be several GB) is disk work: on IO, and not cut short by
     * a cancellation.
     */
    private suspend fun deleteNow(file: File?) {
        if (file != null) withContext(NonCancellable + Dispatchers.IO) { file.delete() }
    }

    /**
     * Copies the decrypted [tmp] into [dest] (null: the dialog was cancelled), and deletes [tmp] whatever happens. N7 of
     * the F6 re-review: if the screen was emptied during a long copy, a success is not shown (the file is saved where
     * the user chose, in time, and the screen's results go by the rule); a failure still is, because then nothing was
     * kept and the user must know.
     */
    fun writeDecrypted(tmp: File, dest: Uri?) {
        val run = session.token()
        viewModelScope.launch {
            val message = try {
                if (dest != null) {
                    withContext(Dispatchers.IO) {
                        appContext.contentResolver.openOutputStream(dest, "wt")!!.use { out -> tmp.inputStream().use { it.copyTo(out) } }
                    }
                    true to "Archivo descifrado y guardado."
                } else {
                    false to "No se ha guardado nada."
                }
            } catch (e: CancellationException) {
                dest?.let { discardDocumentNow(appContext, it) }
                throw e
            } catch (e: Exception) {
                dest?.let { discardDocumentNow(appContext, it) }
                false to "No se pudo guardar el archivo."
            } finally {
                deleteNow(tmp)
            }
            if (session.isCurrent(run) || !message.first) session.message = message
        }
    }

    override fun onCleared() {
        decrypted = null
        session.wipe() // Deletes a decrypted copy never saved.
    }
}

/**
 * Saves `<name>.sgenc`, encrypted with [kdf]; decrypts both its own files and the legacy ones. The password, the key and
 * the last message live in [FileCryptoViewModel] and go by the «PIN y códigos» rule (COV1-1).
 */
@Composable
fun FileCryptoScreen(
    kdf: KdfParams,
    store: VaultStore,
    lockAfterMs: () -> Long,
    awayInOtherApp: StateFlow<Boolean>,
    onBack: () -> Unit,
    onOpenReceived: () -> Unit,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val vm = viewModel { FileCryptoViewModel(appContext, store, lockAfterMs, awayInOtherApp) }
    val s = vm.session
    GuardEffects(s.guard, lockAfterMs)
    val encrypt = vm.encrypt
    val source = vm.source
    // The chosen file is a `.sgenc` for a contact or a `.sgsig`: it opens in Contactos → Abrir lo recibido, not here.
    var forContacts by remember { mutableStateOf(false) }
    // The file and mode the check below finished for. Until it matches the current ones the check is still running
    // («comprobando») and «Descifrar» stays off, so a contact's file can never be tried with a password meanwhile.
    var checkedFor by remember { mutableStateOf<Pair<Uri, Boolean>?>(null) }
    val checking = !encrypt && source != null && checkedFor != (source to encrypt)

    val saveEncrypted = rememberDocumentPicker(ActivityResultContracts.CreateDocument("application/octet-stream")) { dest ->
        if (dest != null) vm.encryptTo(dest, kdf)
    }
    // The decrypted copy waits in the app-private cache, as the session's pending copy (in the ViewModel, never in saved
    // state), until the user picks where to save it; deleted right after. M2 of the F6 review: the screen emptied while
    // the dialog was open (the time away, ON_START comes before the dialog's answer), or the app restarted meanwhile:
    // the copy was already given up, nothing is written and the document the dialog made goes.
    val saveDecrypted = rememberDocumentPicker(ActivityResultContracts.CreateDocument("application/octet-stream")) { dest ->
        val copy = s.takePending()
        val tmp = copy?.let { File(it.path) }?.takeIf { it.exists() && s.isCurrent(copy.run) }
        if (tmp == null) {
            copy?.let { deleteLater(context, it.path) }
            if (dest != null) {
                discardDocumentLater(context, dest)
                s.message = false to "No se ha guardado: vuelve a descifrar el archivo."
            }
            return@rememberDocumentPicker
        }
        vm.writeDecrypted(tmp, dest)
    }
    // A decryption that ended: its save dialog, unless the screen was emptied meanwhile (then the copy is already gone).
    val ready = vm.decrypted
    LaunchedEffect(ready) {
        val r = ready ?: return@LaunchedEffect
        vm.decrypted = null
        if (!s.isCurrent(r.run) || s.pending?.run != r.run) return@LaunchedEffect
        saveDecrypted.launchOrError(safeFileName(r.name))?.let { failure ->
            s.takePending()?.let { deleteLater(context, it.path) } // Never leave decrypted data behind in the cache.
            s.message = false to failure
        }
    }
    val pick = rememberDocumentPicker(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            // Another file: «Descifrar» is off from this very frame until its check ends. The same file again keeps its
            // check (the effect below is keyed on it and does not run again).
            if (uri != vm.source) checkedFor = null
            vm.choose(uri)
            s.message = null
        }
    }
    // Keyed on the chosen file and the mode: a slow check for an earlier file is cancelled when another is picked or the mode
    // changes, so a stale result can never overwrite a newer one.
    LaunchedEffect(source, encrypt) {
        forContacts = false
        checkedFor = null
        val uri = source
        if (uri != null && !encrypt) {
            forContacts = try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)!!.use { it.readAtMost(8) }
                }.let { pickedKind(it) != PickedKind.OTHER }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false // Unreadable: decrypting says so with its own message.
            }
            // Only a check that ran to the end says it is done: a cancelled one (another file, another mode) never does.
            checkedFor = uri to false
        }
    }

    ToolColumn("Cifrar archivos", onBack, s.guard) {
        ModePicker(encrypt) {
            vm.encrypt = it
            vm.choose(null)
            forContacts = false
            s.message = null
        }
        OutlinedButton(onClick = { pick.launchOrError(arrayOf("*/*"))?.let { s.message = false to it } }, modifier = Modifier.fillMaxWidth()) {
            val name = vm.sourceName
            Text(
                if (source == null || name == null) {
                    if (encrypt) "Elegir archivo" else "Elegir archivo $ENCRYPTED_EXTENSION o $LEGACY_ENCRYPTED_EXTENSION"
                } else {
                    sanitizeReceivedName(name)
                },
            )
        }
        if (forContacts) {
            Text("Esto es para Contactos → Abrir lo recibido", color = CautionColor, style = MaterialTheme.typography.titleSmall)
            Text(
                "Es un archivo cifrado para un contacto o una firma (.sgsig): no se abre con una contraseña.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onOpenReceived, modifier = Modifier.fillMaxWidth()) { Text("Abrir lo recibido") }
        }
        // N6 of the F6 re-review: also while the chosen file's name is read from its provider (M7), in either mode.
        if (checking || (source != null && vm.sourceName == null)) {
            Text("Comprobando el archivo…", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        SecretInput(
            s.useKey, { s.useKey = it; s.message = null }, s.password, { s.password = it; s.message = null }, s.keyHex, { s.keyHex = it; s.message = null },
            allowGenerate = encrypt,
            guard = s.guard,
        )
        if (encrypt) {
            Text("Algoritmo", style = MaterialTheme.typography.titleSmall)
            AlgorithmPicker(vm.alg) { vm.alg = it }
        }
        Button(
            enabled = !s.busy && ready == null && source != null && vm.sourceName != null && !checking && !(forContacts && !encrypt) && hasSecret(s.useKey, s.password, s.keyHex),
            onClick = {
                if (encrypt) {
                    val name = vm.sourceName ?: return@Button
                    saveEncrypted.launchOrError(encryptedFileName(name))?.let { s.message = false to it } // At most 255 bytes with «.sgenc».
                } else {
                    vm.decrypt()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (encrypt) "Cifrar y guardar" else "Descifrar y guardar") }
        if (s.busy) Busy(if (encrypt) "Cifrando…" else "Descifrando y comprobando…")
        s.message?.let { (ok, text) -> Text(text, color = if (ok) PositiveColor else NegativeColor) }
        Text(
            "Se cifra por bloques de 64 KiB: sirve para archivos de cualquier tamaño. Al descifrar se comprueba todo el archivo " +
                "antes de guardarlo; si alguien lo ha modificado o la contraseña no es la correcta, no se guarda nada.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AwayNote()
    }
}

@Composable
private fun StrengthBar(bits: Double) {
    val s = strength(bits)
    val color = when (s) {
        Strength.WEAK -> NegativeColor
        Strength.FAIR -> CautionColor
        else -> PositiveColor
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LinearProgressIndicator(progress = { (bits / 128).toFloat().coerceIn(0.05f, 1f) }, color = color, modifier = Modifier.fillMaxWidth())
        Text("${s.label} · ${bits.roundToInt()} bits de entropía", color = color, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun GeneratorScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val words = remember { context.assets.open("eff_large_wordlist.txt").bufferedReader().readLines().filter { it.isNotBlank() } }
    var phrase by remember { mutableStateOf(false) }
    var p by remember { mutableStateOf(PasswordOptions()) }
    var w by remember { mutableStateOf(PassphraseOptions()) }
    var seed by remember { mutableIntStateOf(0) }
    val result = remember(phrase, p, w, seed) { if (phrase) generatePassphrase(words, w) else generatePassword(p) }
    val bits = if (phrase) passphraseEntropy(words.size, w) else passwordEntropy(p)

    ToolColumn("Generador", onBack) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(selected = !phrase, onClick = { phrase = false }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Contraseña") }
            SegmentedButton(selected = phrase, onClick = { phrase = true }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Frase") }
        }
        SectionCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(result ?: "Elige al menos un tipo de carácter", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.titleMedium)
                StrengthBar(bits)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { seed++ }) { Text("↻ Otra") }
                    OutlinedButton(enabled = result != null, onClick = { result?.let { copySensitive(context, it) } }) { Text("Copiar") }
                }
            }
        }
        if (!phrase) {
            Text("Longitud: ${p.length}", style = MaterialTheme.typography.labelMedium)
            Slider(value = p.length.toFloat(), onValueChange = { p = p.copy(length = it.roundToInt()) }, valueRange = 8f..64f)
            Option("Minúsculas (a-z)", p.lower) { p = p.copy(lower = it) }
            Option("Mayúsculas (A-Z)", p.upper) { p = p.copy(upper = it) }
            Option("Números (0-9)", p.digits) { p = p.copy(digits = it) }
            Option("Símbolos (!@#…)", p.symbols) { p = p.copy(symbols = it) }
            Option("Evitar caracteres confusos (0 O 1 l I)", p.excludeAmbiguous) { p = p.copy(excludeAmbiguous = it) }
        } else {
            Text("Palabras: ${w.words}", style = MaterialTheme.typography.labelMedium)
            Slider(value = w.words.toFloat(), onValueChange = { w = w.copy(words = it.roundToInt()) }, valueRange = 4f..10f, steps = 5)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                listOf("-" to "guion", " " to "espacio", "." to "punto", "_" to "barra baja").forEach { (sep, label) ->
                    FilterChip(selected = w.separator == sep, onClick = { w = w.copy(separator = sep) }, label = { Text(label) })
                }
            }
            Option("Primera letra en mayúscula", w.capitalize) { w = w.copy(capitalize = it) }
            Option("Añadir un número", w.addNumber) { w = w.copy(addNumber = it) }
            Text(
                "Palabras de la lista de la EFF (7.776 palabras en inglés, CC BY 3.0 US). Una frase de 6 palabras es fácil de recordar y muy difícil de adivinar.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Option(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/**
 * «¿Contraseña filtrada?» for its navigation entry (COV1-1, audit 1.3.0): the password being typed and the answer, never
 * in saved state, emptied by the «PIN y códigos» rule. The check runs in [viewModelScope], so a rotation does not cut it.
 */
private class BreachViewModel(
    private val pwned: PwnedPasswords,
    store: VaultStore,
    lockAfterMs: () -> Long,
    awayInOtherApp: StateFlow<Boolean>,
) : ViewModel() {
    val session = BreachSession(ComposeCells, SystemClock.elapsedRealtime())

    init {
        watchAway(session.guard, store, lockAfterMs, awayInOtherApp)
    }

    fun check() {
        val s = session
        val pw = s.password.toCharArray()
        val run = s.token()
        s.busy = true
        viewModelScope.launch {
            val result = try {
                val n = pwned.times(pw)
                if (n > 0) {
                    false to "⚠ Aparece $n ${if (n == 1) "vez" else "veces"} en filtraciones. No la uses: cámbiala donde la tengas."
                } else {
                    true to "✓ No aparece en filtraciones conocidas. Aun así, usa una distinta para cada sitio."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false to "No se pudo comprobar. Revisa la conexión."
            } finally {
                pw.fill('\u0000')
                s.password = ""
                s.busy = false
            }
            if (s.isCurrent(run)) s.result = result
        }
    }

    override fun onCleared() {
        session.wipe()
    }
}

@Composable
fun BreachScreen(pwned: PwnedPasswords, store: VaultStore, lockAfterMs: () -> Long, awayInOtherApp: StateFlow<Boolean>, onBack: () -> Unit) {
    val vm = viewModel { BreachViewModel(pwned, store, lockAfterMs, awayInOtherApp) }
    val s = vm.session
    GuardEffects(s.guard, lockAfterMs)
    ToolColumn("¿Contraseña filtrada?", onBack, s.guard) {
        Text(
            "Comprobamos tu contraseña contra más de 900 millones de contraseñas filtradas (Have I Been Pwned). Tu contraseña nunca sale " +
                "del móvil: calculamos su huella SHA-1 aquí y solo enviamos sus 5 primeros caracteres; la comparación final se hace en tu móvil.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PasswordField(s.password, { s.guard.typed(); s.password = it; s.result = null })
        Button(
            enabled = !s.busy && s.password.isNotEmpty(),
            onClick = vm::check,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Comprobar") }
        if (s.busy) Busy("Comprobando…")
        s.result?.let { (ok, text) -> Text(text, color = if (ok) PositiveColor else NegativeColor) }
        AwayNote()
    }
}

/**
 * «Huella SHA-256» for its navigation entry. COV1-2 (audit 1.3.0): a new pick cancels the hash going on (the reading
 * stops at its next 64 KiB), the result is shown only for the file picked last ([FileHashSession]) and the button is off
 * while it runs. The provider's name is read off the main thread and shown sanitised, as in «Cifrar archivos». Not a
 * secret: no away rule.
 */
private class FileHashViewModel(private val appContext: Context) : ViewModel() {
    val session = FileHashSession(ComposeCells)
    var expected by mutableStateOf("")
    private var job: Job? = null

    fun pick(uri: Uri) {
        job?.cancel()
        val run = session.start()
        job = viewModelScope.launch {
            val name = withContext(Dispatchers.IO) { runCatching { displayName(appContext, uri) }.getOrDefault("archivo") }
            session.named(run, sanitizeReceivedName(name))
            val hash = try {
                withContext(Dispatchers.IO) {
                    appContext.contentResolver.openInputStream(uri)!!.use { input -> sha256Hex(input.buffered()) { ensureActive() } }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            session.finish(run, hash)
        }
    }
}

@Composable
fun FileHashScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val vm = viewModel { FileHashViewModel(appContext) }
    val s = vm.session
    var pickerError by remember { mutableStateOf<String?>(null) }
    val pick = rememberDocumentPicker(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.pick(uri)
    }
    ToolColumn("Huella SHA-256", onBack) {
        Text(
            "Calcula la huella SHA-256 de un archivo y compárala con la que publica la web oficial (por ejemplo, de una app o del firmware " +
                "de tu cartera física). Si no coinciden, el archivo se ha alterado o está dañado.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            enabled = !s.busy,
            onClick = { pickerError = pick.launchOrError(arrayOf("*/*")) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(s.name ?: "Elegir archivo") }
        pickerError?.let { Text(it, color = NegativeColor) }
        if (s.busy) Busy("Calculando…")
        // M8 of the F6 review: an unreadable file is said, not left as a silent nothing.
        if (s.failed) Text("No se pudo leer el archivo.", color = NegativeColor)
        s.hash?.let { h ->
            SectionCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("SHA-256", style = MaterialTheme.typography.labelMedium)
                    SelectionContainer { Text(h, fontFamily = FontFamily.Monospace) }
                    FilledTonalButton(onClick = { copySensitive(context, h) }) { Text("Copiar") }
                }
            }
            OutlinedTextField(
                value = vm.expected,
                onValueChange = { vm.expected = it },
                label = { Text("Pega la huella publicada") },
                modifier = Modifier.fillMaxWidth(),
            )
            if (vm.expected.isNotBlank()) {
                if (sameHash(h, vm.expected)) {
                    Text("✓ Coincide: el archivo es el original.", color = PositiveColor)
                } else {
                    Text("✗ No coincide: no uses este archivo.", color = NegativeColor)
                }
            }
        }
    }
}
