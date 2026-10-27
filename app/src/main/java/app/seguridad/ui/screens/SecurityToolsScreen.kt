package app.seguridad.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
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
import app.seguridad.crypto.newRawKeyHex
import app.seguridad.crypto.parseRawKey
import app.seguridad.crypto.passphraseEntropy
import app.seguridad.crypto.passwordEntropy
import app.seguridad.crypto.safeFileName
import app.seguridad.crypto.sameHash
import app.seguridad.crypto.sha256Hex
import app.seguridad.crypto.strength
import app.seguridad.net.PwnedPasswords
import app.seguridad.vault.PickedKind
import app.seguridad.vault.pickedKind
import app.seguridad.ui.theme.BackScaffold
import app.seguridad.ui.theme.CautionColor
import app.seguridad.ui.theme.NegativeColor
import app.seguridad.ui.theme.NoCopy
import app.seguridad.ui.theme.PasswordField
import app.seguridad.ui.theme.PositiveColor
import app.seguridad.ui.theme.SectionCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.TextButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.AssistChip
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.clickable
import android.provider.DocumentsContract

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
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        SegmentedButton(selected = !useKey, onClick = { onUseKey(false) }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Contraseña") }
        SegmentedButton(selected = useKey, onClick = { onUseKey(true) }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Clave AES-256") }
    }
    if (!useKey) {
        PasswordField(password, onPassword)
        Text(
            "Se convierte en clave con Argon2id. Usa una larga: una frase de 6 palabras del Generador es ideal.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        OutlinedTextField(
            value = keyHex,
            onValueChange = onKeyHex,
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
            "Una clave aleatoria de 256 bits: imposible de adivinar. Apúntala en papel y guárdala SEPARADA del texto cifrado: " +
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

@Composable
private fun ToolColumn(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    BackScaffold(title, onBack) { padding ->
        NoCopy {
            Column(
                Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) { content() }
        }
    }
}

/** Encrypts with [kdf] (Ajustes: coste de Argon2id); decrypting reads the cost from the ciphertext itself. */
@Composable
fun TextCryptoScreen(kdf: KdfParams, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bip39 = remember { Bip39(context.assets.open("bip39_english.txt").bufferedReader().readLines().filter { it.isNotBlank() }) }
    var encrypt by remember { mutableStateOf(true) }
    var seedMode by remember { mutableStateOf(true) }
    var alg by remember { mutableStateOf(Aead.AES_256_GCM) }
    var input by remember { mutableStateOf("") }
    var useKey by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var keyHex by remember { mutableStateOf("") }
    var output by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    fun reset() {
        output = null
        error = null
    }
    var fileMessage by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    val saveTxt = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { dest ->
        if (dest == null) return@rememberLauncherForActivityResult
        val text = output ?: run {
            // The screen was rebuilt while the dialog was open (the result is never kept in saved state).
            discardDocument(context, dest)
            fileMessage = false to "No se ha guardado: vuelve a cifrar el texto."
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            fileMessage = try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(dest, "wt")!!.use { it.write((text + "\n").toByteArray(Charsets.UTF_8)) }
                }
                true to "Guardado. El archivo solo contiene el texto cifrado: sin tu ${if (useKey) "clave" else "contraseña"} no sirve de nada."
            } catch (e: Exception) {
                discardDocument(context, dest)
                false to "No se pudo guardar el archivo."
            }
        }
    }
    val openTxt = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)!!.use { it.readAtMost(MAX_TEXT_FILE_BYTES + 1) }
                        .takeIf { it.size <= MAX_TEXT_FILE_BYTES }?.toString(Charsets.UTF_8)
                }
            } catch (e: Exception) {
                null
            }
            val token = text?.let(::extractToken)
            if (token != null) {
                input = token
                reset()
                fileMessage = true to "Texto cifrado importado de ${displayName(context, uri)}."
            } else {
                fileMessage = false to if (text == null) "No se pudo leer el archivo." else "Ese archivo no contiene un texto cifrado ($TEXT_PREFIX… o $LEGACY_TEXT_PREFIX…)."
            }
        }
    }

    ToolColumn("Cifrar texto", onBack) {
        ModePicker(encrypt) {
            encrypt = it
            input = ""
            fileMessage = null
            reset()
        }
        if (encrypt) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = seedMode, onClick = { seedMode = true; reset() }, label = { Text("🌱 Frase semilla") })
                FilterChip(selected = !seedMode, onClick = { seedMode = false; reset() }, label = { Text("Texto libre") })
            }
            OutlinedTextField(
                value = input,
                onValueChange = { input = if (seedMode) it.lowercase() else it; reset() },
                label = { Text(if (seedMode) "Las 12, 18 o 24 palabras de tu wallet" else "Texto a cifrar") },
                minLines = 4,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            if (seedMode) SeedHelper(bip39, input) { input = it }
        } else {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it; reset() },
                label = { Text("Texto cifrado (empieza por $TEXT_PREFIX o $LEGACY_TEXT_PREFIX)") },
                minLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(onClick = {
                fileMessage = openTxt.launchOrError(arrayOf("text/plain", "application/octet-stream", "*/*"))?.let { false to it }
            }, modifier = Modifier.fillMaxWidth()) {
                Text("📂 Importar archivo .txt")
            }
            val detected = remember(input) { inspectText(input) }
            if (detected != null) {
                val (a, raw) = detected
                LaunchedEffect(a, raw) { useKey = raw }
                Text("✓ Cifrado con ${a.label} y ${if (raw) "una clave AES-256" else "una contraseña"}.", color = PositiveColor)
            } else {
                Text(
                    "No hace falta elegir el algoritmo: se detecta solo (${Aead.entries.joinToString(", ") { it.label }}).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        SecretInput(useKey, { useKey = it; reset() }, password, { password = it; reset() }, keyHex, { keyHex = it; reset() }, allowGenerate = encrypt)
        if (encrypt) {
            Text("Algoritmo", style = MaterialTheme.typography.titleSmall)
            AlgorithmPicker(alg) { alg = it }
        }
        Button(
            enabled = !busy && input.isNotBlank() && hasSecret(useKey, password, keyHex),
            onClick = {
                val secret = secretOf(useKey, password, keyHex) ?: return@Button
                // Snapshot the request before launching asynchronous work; controls remain interactive while busy.
                val encryptNow = encrypt
                val useKeyNow = useKey
                val algNow = alg
                val inputNow = input
                val kdfNow = kdf
                val plainNow = if (seedMode) bip39.normalize(inputNow).joinToString(" ") else inputNow
                busy = true
                reset()
                scope.launch {
                    try {
                        val result = withContext(Dispatchers.Default) {
                            if (encryptNow) encryptText(plainNow, secret, algNow, kdfNow) else decryptText(inputNow, secret)
                        }
                        // The form stays editable while busy: a result for a mode or text the user has since changed
                        // is dropped, and never wipes what they typed after starting.
                        if (encrypt == encryptNow && input == inputNow) {
                            output = result
                            // The plaintext (often a seed phrase) is no longer needed once it is encrypted. Only on
                            // success: a failed encryption must not lose what the user typed.
                            if (encryptNow) input = ""
                        }
                    } catch (e: CryptoFailure) {
                        error = if (useKeyNow && e.message == CryptoFailure().message) "Clave incorrecta o texto dañado" else e.message
                    } finally {
                        secret.wipe()
                        // The operation has consumed the entered password; never leave it in the form after use.
                        if (!useKeyNow) password = ""
                        busy = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (encrypt) "Cifrar" else "Descifrar") }
        if (busy) Busy(if (useKey) "Procesando…" else "Derivando la clave con Argon2id…")
        error?.let { Text(it, color = NegativeColor) }
        fileMessage?.let { (ok, text) -> Text(text, color = if (ok) PositiveColor else NegativeColor) }
        output?.let { out ->
            val seed = !encrypt && bip39.check(out).let { it.validCount && it.unknown.isEmpty() }
            SectionCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        when {
                            encrypt -> "Texto cifrado"
                            seed -> "Frase semilla (${bip39.normalize(out).size} palabras)"
                            else -> "Texto original"
                        },
                        style = MaterialTheme.typography.labelMedium,
                    )
                    when {
                        seed -> SeedGrid(bip39.normalize(out))
                        // The ciphertext can be selected; the decrypted text only leaves through "Copiar" (sensitive, cleared).
                        encrypt -> SelectionContainer { Text(out, fontFamily = FontFamily.Monospace) }
                        else -> Text(out)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { copySensitive(context, out) }) { Text("Copiar") }
                        if (encrypt) {
                            OutlinedButton(onClick = {
                                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, out), "Compartir"))
                            }) { Text("Compartir") }
                        }
                    }
                    if (encrypt) {
                        OutlinedButton(
                            onClick = {
                                fileMessage = saveTxt.launchOrError(if (seedMode) "frase-cifrada.txt" else "texto-cifrado.txt")?.let { false to it }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("💾 Guardar como archivo .txt") }
                    }
                    if (encrypt) {
                        Text(
                            "Guarda este texto donde quieras (nube, correo, papel). Sin tu ${if (useKey) "clave" else "contraseña"} es inútil. " +
                                "Antes de borrar tu copia original, prueba a descifrarlo.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
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

internal fun displayName(context: Context, uri: Uri): String =
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    } ?: "archivo"

/** Decrypted files wait here, in the app's private cache, until saved; leftovers are wiped at start. */
const val DECRYPT_TMP_PREFIX = "dec"

/** Prefix of the legacy texts, still decrypted (new ones always start with [TEXT_PREFIX]). */
private const val LEGACY_TEXT_PREFIX = "CP1:"

/** Saves `<name>.sgenc`, encrypted with [kdf]; decrypts both its own files and the legacy ones. */
@Composable
fun FileCryptoScreen(kdf: KdfParams, onBack: () -> Unit, onOpenReceived: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var encrypt by remember { mutableStateOf(true) }
    var alg by remember { mutableStateOf(Aead.AES_256_GCM) }
    var source by remember { mutableStateOf<Uri?>(null) }
    var sourceName by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var useKey by remember { mutableStateOf(false) }
    var keyHex by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var busy by remember { mutableStateOf(false) }
    // Decrypted data waits here (app-private cache) until the user picks where to save it; deleted right after.
    var pendingPath by rememberSaveable { mutableStateOf<String?>(null) }
    // The chosen file is a `.sgenc` for a contact or a `.sgsig`: it opens in Contactos → Abrir lo recibido, not here.
    var forContacts by remember { mutableStateOf(false) }

    val saveEncrypted = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { dest ->
        if (dest == null) return@rememberLauncherForActivityResult
        val src = source
        val clearPasswordAfterUse = !useKey
        val secret = secretOf(useKey, password, keyHex)
        if (src == null || secret == null) {
            // The screen was rebuilt while the dialog was open: the file and secret are never kept in saved state.
            secret?.wipe()
            discardDocument(context, dest)
            message = false to "No se ha guardado: vuelve a elegir el archivo y la contraseña o clave."
            return@rememberLauncherForActivityResult
        }
        busy = true
        scope.launch {
            message = try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(src)!!.use { input ->
                        context.contentResolver.openOutputStream(dest, "wt")!!.use { out -> encryptStream(input.buffered(), out.buffered(), sourceName, secret, alg, kdf) }
                    }
                }
                true to "Archivo cifrado y guardado."
            } catch (e: Exception) {
                discardDocument(context, dest) // Never leave a truncated file behind.
                false to "No se pudo cifrar el archivo."
            } finally {
                secret.wipe()
                if (clearPasswordAfterUse) password = ""
                busy = false
            }
        }
    }
    val saveDecrypted = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { dest ->
        val tmp = pendingPath?.let(::File)?.takeIf { it.exists() } ?: run {
            // The decrypted copy is gone (the app was restarted meanwhile): nothing to write.
            pendingPath = null
            if (dest != null) {
                discardDocument(context, dest)
                message = false to "No se ha guardado: vuelve a descifrar el archivo."
            }
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            message = try {
                if (dest != null) {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(dest, "wt")!!.use { out -> tmp.inputStream().use { it.copyTo(out) } }
                    }
                    true to "Archivo descifrado y guardado."
                } else {
                    false to "No se ha guardado nada."
                }
            } catch (e: Exception) {
                dest?.let { discardDocument(context, it) }
                false to "No se pudo guardar el archivo."
            } finally {
                tmp.delete()
                pendingPath = null
            }
        }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            source = uri
            sourceName = displayName(context, uri)
            message = null
            forContacts = false
            if (!encrypt) {
                scope.launch {
                    forContacts = try {
                        withContext(Dispatchers.IO) {
                            context.contentResolver.openInputStream(uri)!!.use { it.readAtMost(8) }
                        }.let { pickedKind(it) != PickedKind.OTHER }
                    } catch (e: Exception) {
                        false // Unreadable: decrypting says so with its own message.
                    }
                }
            }
        }
    }

    ToolColumn("Cifrar archivos", onBack) {
        ModePicker(encrypt) {
            encrypt = it
            source = null
            forContacts = false
            message = null
        }
        OutlinedButton(onClick = { pick.launchOrError(arrayOf("*/*"))?.let { message = false to it } }, modifier = Modifier.fillMaxWidth()) {
            Text(if (source == null) (if (encrypt) "Elegir archivo" else "Elegir archivo $ENCRYPTED_EXTENSION o $LEGACY_ENCRYPTED_EXTENSION") else sourceName)
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
        SecretInput(useKey, { useKey = it; message = null }, password, { password = it; message = null }, keyHex, { keyHex = it; message = null }, allowGenerate = encrypt)
        if (encrypt) {
            Text("Algoritmo", style = MaterialTheme.typography.titleSmall)
            AlgorithmPicker(alg) { alg = it }
        }
        Button(
            enabled = !busy && source != null && !(forContacts && !encrypt) && hasSecret(useKey, password, keyHex),
            onClick = {
                if (encrypt) {
                    saveEncrypted.launchOrError(sourceName + ENCRYPTED_EXTENSION)?.let { message = false to it }
                } else {
                    val src = source ?: return@Button
                    val clearPasswordAfterUse = !useKey
                    val secret = secretOf(useKey, password, keyHex) ?: return@Button
                    busy = true
                    scope.launch {
                        val tmp = File.createTempFile(DECRYPT_TMP_PREFIX, null, context.cacheDir)
                        try {
                            val name = withContext(Dispatchers.IO) {
                                context.contentResolver.openInputStream(src)!!.use { input ->
                                    tmp.outputStream().buffered().use { out -> decryptStream(input.buffered(), out, secret) }
                                }
                            }
                            pendingPath = tmp.path
                            saveDecrypted.launchOrError(safeFileName(name))?.let { failure ->
                                pendingPath = null
                                tmp.delete() // Never leave decrypted data behind in the cache.
                                message = false to failure
                            }
                        } catch (e: CryptoFailure) {
                            tmp.delete()
                            message = false to (e.message ?: "Contraseña incorrecta o archivo dañado")
                        } catch (e: Exception) {
                            tmp.delete()
                            message = false to "No se pudo leer el archivo."
                        } finally {
                            secret.wipe()
                            if (clearPasswordAfterUse) password = ""
                            busy = false
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (encrypt) "Cifrar y guardar" else "Descifrar y guardar") }
        if (busy) Busy(if (encrypt) "Cifrando…" else "Descifrando y comprobando…")
        message?.let { (ok, text) -> Text(text, color = if (ok) PositiveColor else NegativeColor) }
        Text(
            "Se cifra por bloques de 64 KiB: sirve para archivos de cualquier tamaño. Al descifrar se comprueba todo el archivo " +
                "antes de guardarlo; si alguien lo ha modificado o la contraseña no es la correcta, no se guarda nada.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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

@Composable
fun BreachScreen(pwned: PwnedPasswords, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var busy by remember { mutableStateOf(false) }
    ToolColumn("¿Contraseña filtrada?", onBack) {
        Text(
            "Comprobamos tu contraseña contra más de 900 millones de contraseñas filtradas (Have I Been Pwned). Tu contraseña nunca sale " +
                "del móvil: calculamos su huella SHA-1 aquí y solo enviamos sus 5 primeros caracteres; la comparación final se hace en tu móvil.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PasswordField(password, { password = it; result = null })
        Button(
            enabled = !busy && password.isNotEmpty(),
            onClick = {
                busy = true
                val pw = password.toCharArray()
                scope.launch {
                    result = try {
                        val n = pwned.times(pw)
                        if (n > 0) {
                            false to "⚠ Aparece $n ${if (n == 1) "vez" else "veces"} en filtraciones. No la uses: cámbiala donde la tengas."
                        } else {
                            true to "✓ No aparece en filtraciones conocidas. Aun así, usa una distinta para cada sitio."
                        }
                    } catch (e: Exception) {
                        false to "No se pudo comprobar. Revisa la conexión."
                    } finally {
                        pw.fill('\u0000')
                        password = ""
                        busy = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Comprobar") }
        if (busy) Busy("Comprobando…")
        result?.let { (ok, text) -> Text(text, color = if (ok) PositiveColor else NegativeColor) }
    }
}

@Composable
fun FileHashScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf<String?>(null) }
    var hash by remember { mutableStateOf<String?>(null) }
    var expected by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var pickerError by remember { mutableStateOf<String?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        name = displayName(context, uri)
        hash = null
        busy = true
        scope.launch {
            hash = try {
                withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)!!.use { sha256Hex(it.buffered()) } }
            } catch (e: Exception) {
                null
            } finally {
                busy = false
            }
        }
    }
    ToolColumn("Huella SHA-256", onBack) {
        Text(
            "Calcula la huella SHA-256 de un archivo y compárala con la que publica la web oficial (por ejemplo, de una app o del firmware " +
                "de tu wallet). Si no coinciden, el archivo se ha alterado o está dañado.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = { pickerError = pick.launchOrError(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) { Text(name ?: "Elegir archivo") }
        pickerError?.let { Text(it, color = NegativeColor) }
        if (busy) Busy("Calculando…")
        hash?.let { h ->
            SectionCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("SHA-256", style = MaterialTheme.typography.labelMedium)
                    SelectionContainer { Text(h, fontFamily = FontFamily.Monospace) }
                    FilledTonalButton(onClick = { copySensitive(context, h) }) { Text("Copiar") }
                }
            }
            OutlinedTextField(
                value = expected,
                onValueChange = { expected = it },
                label = { Text("Pega la huella publicada") },
                modifier = Modifier.fillMaxWidth(),
            )
            if (expected.isNotBlank()) {
                if (sameHash(h, expected)) {
                    Text("✓ Coincide: el archivo es el original.", color = PositiveColor)
                } else {
                    Text("✗ No coincide: no uses este archivo.", color = NegativeColor)
                }
            }
        }
    }
}
