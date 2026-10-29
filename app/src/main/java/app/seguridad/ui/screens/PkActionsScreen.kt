package app.seguridad.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.seguridad.SeguridadApp
import app.seguridad.android.copySensitive
import app.seguridad.android.readClipboardText
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.seguridad.core.readAtMost
import app.seguridad.crypto.pk.cardText
import app.seguridad.crypto.MAX_TEXT_FILE_BYTES
import app.seguridad.crypto.safeFileName
import app.seguridad.tools.DONT_SEND_FILE_NAME
import app.seguridad.tools.fileNameInsideText
import app.seguridad.tools.qr.QrHandoff
import app.seguridad.tools.qr.QrTarget
import app.seguridad.ui.theme.CautionColor
import app.seguridad.ui.theme.NegativeColor
import app.seguridad.ui.theme.PositiveColor
import app.seguridad.ui.theme.SectionCard
import app.seguridad.vault.ENCRYPT_HELP
import app.seguridad.vault.FileSelection
import app.seguridad.vault.NO_IDENTITY_TO_SIGN
import app.seguridad.vault.PK_OPEN_TEMP_DIR
import app.seguridad.vault.PickStep
import app.seguridad.vault.PickedFile
import app.seguridad.vault.PickedKind
import app.seguridad.vault.PkInputs
import app.seguridad.vault.PkMessage
import app.seguridad.vault.PkResult
import app.seguridad.vault.ReceivedFile
import app.seguridad.vault.ReceivedOutcome
import app.seguridad.vault.SHARE_HINT
import app.seguridad.vault.SIGN_HELP
import app.seguridad.vault.SYMMETRIC_FILE_NOTICE
import app.seguridad.vault.SenderLine
import app.seguridad.vault.TrustLevel
import app.seguridad.vault.VaultEntry
import app.seguridad.vault.VaultLock
import app.seguridad.vault.VaultState
import app.seguridad.vault.VaultStore
import app.seguridad.vault.actionErrorMessage
import app.seguridad.vault.afterPicking
import app.seguridad.vault.anonymousFileName
import app.seguridad.vault.clearTempDir
import app.seguridad.vault.contactsList
import app.seguridad.vault.decryptReceivedFileToTemp
import app.seguridad.vault.encryptFileForContact
import app.seguridad.vault.encryptTextForContact
import app.seguridad.vault.encryptWarning
import app.seguridad.vault.encryptedFileName
import app.seguridad.vault.identity
import app.seguridad.vault.nextPickStep
import app.seguridad.vault.openErrorMessage
import app.seguridad.vault.openReceivedText
import app.seguridad.vault.resultFor
import app.seguridad.vault.messageFor
import app.seguridad.vault.classifyPicked
import app.seguridad.vault.sanitizeReceivedName
import app.seguridad.vault.saveReceivedFile
import app.seguridad.vault.senderLine
import app.seguridad.vault.signFileAs
import app.seguridad.vault.signTextAs
import app.seguridad.vault.signatureFileName
import app.seguridad.vault.verificationMark
import app.seguridad.vault.verifyFileAgainst
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/*
 * «Cifrar para…», «Firmar» y «Abrir lo recibido» (diseño: «Pantallas»). Aquí solo hay pantalla: toda la lógica con peso
 * (textos del remitente, saneado de nombres, errores, descifrado a una carpeta temporal) está en `vault/PkActions.kt`,
 * que sí se prueba en `jvm-check`.
 *
 * Reglas que cumplen las tres:
 *  - Cada pantalla pasa por `ContactsFrame` (y por tanto por `VaultGate`): si la bóveda se bloquea, el contenido sale de la
 *    composición, se cancelan las corrutinas de `rememberCoroutineScope` y se borra la carpeta temporal.
 *  - Todo el trabajo va en `Dispatchers.Default`, se puede cancelar, y la identidad se abre y se cierra dentro de la
 *    función pura que lo hace (nunca en un `remember` ni en un ViewModel).
 *  - Girar el móvil recrea la actividad: lo que se escribe (texto en claro, texto descifrado) vive solo en `remember` y se
 *    pierde; solo se guardan en el estado de la pantalla datos que no son secretos (modo, contacto, URI de un archivo).
 *  - El texto descifrado se copia solo con `copySensitive`; lo cifrado o firmado (público) se copia o se comparte con
 *    `ACTION_SEND`, que **nunca** pone `VaultLock.keepOpenOnce`: no hay callback que lo devuelva a `false`.
 *  - `keepOpenOnce` solo se pone junto a un selector (OpenDocument, CreateDocument), que lo quita en su callback.
 *  - A15a: un resultado lleva las entradas de las que salió (`PkResult`, `PkInputs`) y solo se enseña si son las de
 *    ahora (`resultFor`): un trabajo que termina después de cambiar el contacto, el texto o el archivo no enseña nada.
 *  - Un documento que no se llegó a escribir se borra fuera del hilo principal y sin que lo corte una cancelación:
 *    `discardDocumentLater` (desde un callback, en el ámbito de la app) o `discardDocumentNow` (desde una corrutina,
 *    con `NonCancellable + Dispatchers.IO`, como en Tachar y Limpiar foto).
 */


/** Un comprobador de cancelación para el código que no es `suspend`: lanza [CancellationException] si el trabajo ya no está activo. */
private suspend fun cancellationCheck(): () -> Unit {
    val job = currentCoroutineContext()[Job]
    return { job?.ensureActive() }
}

/**
 * Borra la subcarpeta de [received] fuera del hilo principal (`Dispatchers.IO`) y en el ámbito de la app, que vive lo que
 * el proceso: el borrado termina aunque la pantalla se vaya o la corrutina que lo pide esté cancelada.
 */
private fun discardInBackground(context: Context, received: ReceivedFile?) {
    if (received == null) return
    val scope = (context.applicationContext as? SeguridadApp)?.container?.appScope ?: CoroutineScope(Dispatchers.IO)
    scope.launch(Dispatchers.IO) { received.discard() }
}

/** Pone [VaultLock.keepOpenOnce] y cuenta el tiempo fuera desde aquí; lo quita si el selector no se abre. Devuelve el error, o `null`. */
private fun launchPicker(store: VaultStore, launch: () -> String?): String? {
    VaultLock.keepOpenOnce = true
    store.touch() // The time away is counted from here (AWAY_BACKSTOP_MS with «Al salir»).
    val failure = launch()
    if (failure != null) VaultLock.keepOpenOnce = false // The app never left: leaving later must lock.
    return failure
}

/** Launches [block] with [setBusy] true while it runs (also if it is cancelled or fails). */
private fun CoroutineScope.launchBusy(setBusy: (Boolean) -> Unit, block: suspend () -> Unit): Job = launch {
    setBusy(true)
    try {
        block()
    } finally {
        setBusy(false)
    }
}

@Composable
private fun Busy(text: String, onCancel: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        TextButton(onClick = onCancel) { Text("Cancelar") }
    }
}

@Composable
private fun ResultMessage(message: Pair<Boolean, String>?) {
    message?.let { (ok, text) -> Text(text, color = if (ok) PositiveColor else NegativeColor) }
}

@Composable
private fun SenderText(line: SenderLine) {
    val color = when (line.level) {
        TrustLevel.GOOD -> PositiveColor
        TrustLevel.CAUTION -> CautionColor
        TrustLevel.WARNING -> NegativeColor
    }
    // Plain Text: a name is never read as a link or markdown.
    Text(line.text, color = color, style = MaterialTheme.typography.titleMedium)
    line.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    // A16a: the same signing key is kept in several contacts (the one shown is the verified, or else the newest).
    line.sameKey?.let { Text(it, color = CautionColor) }
}

@Composable
private fun TextOrFile(isFile: Boolean, onChange: (Boolean) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        SegmentedButton(selected = !isFile, onClick = { onChange(false) }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Texto") }
        SegmentedButton(selected = isFile, onClick = { onChange(true) }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Archivo") }
    }
}

/** The text just produced (public: encrypted or signed), with copy and share. */
@Composable
private fun PublicOutput(context: Context, output: String, shareTitle: String, onError: (String) -> Unit) {
    SectionCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(output, maxLines = 6, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { copySensitive(context, output) }) { Text("Copiar") }
                OutlinedButton(onClick = { if (!shareText(context, output, shareTitle)) onError("No se pudo abrir el menú de compartir") }) { Text("Compartir") }
            }
            Text(SHARE_HINT, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "Al compartir, la bóveda se bloquea cuando sales de la app.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SourceButton(source: Uri?, name: String, onPick: () -> Unit) {
    OutlinedButton(onClick = onPick, modifier = Modifier.fillMaxWidth()) { Text(if (source == null) "Elegir archivo" else sanitizeReceivedName(name)) }
}

// ---- Cifrar para… ----

/** «Cifrar para…» (`contacts/encrypt`): un texto o un archivo, el contacto y «Firmar con mi identidad». */
@Composable
fun EncryptForScreen(store: VaultStore, onBack: () -> Unit) {
    ContactsFrame(store, "Cifrar para…", onBack) { entries, _ -> EncryptForContent(store, entries) }
}

@Composable
private fun EncryptForContent(store: VaultStore, entries: List<VaultEntry>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val contacts = remember(entries) { contactsList(entries, "") }
    val hasIdentity = remember(entries) { entries.identity() != null }
    var isFile by rememberSaveable { mutableStateOf(false) }
    var contactId by rememberSaveable { mutableStateOf<String?>(null) }
    var sign by rememberSaveable { mutableStateOf(true) }
    var source by rememberSaveable { mutableStateOf<Uri?>(null) }
    var sourceName by rememberSaveable { mutableStateOf("") }
    // The plain text is a secret: never in saved state (it is lost when the phone rotates, which is acceptable).
    var text by remember { mutableStateOf("") }
    // A15a: with the inputs it came from; a rotation loses it, like the text.
    var result by remember { mutableStateOf<PkResult?>(null) }
    // A15a: a message also carries the inputs it is about, and shows only next to them.
    var tagged by remember { mutableStateOf<PkMessage?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    var busy by remember { mutableStateOf(false) }
    // A15b: not a secret, so it may be saved; off by default (the real name goes inside, as before).
    var hideName by rememberSaveable { mutableStateOf(false) }
    val contact = contacts.firstOrNull { it.id == contactId }
    val signNow = sign && hasIdentity
    val warning = contact?.let(::encryptWarning)
    val inputsNow = PkInputs(contact?.id, text.takeIf { !isFile }, source?.toString()?.takeIf { isFile })
    val output = resultFor(inputsNow, result)
    // The name that goes inside the file (encryptFileFor passes it through safeFileName too).
    val nameInside = if (hideName) anonymousFileName(sourceName) else safeFileName(sourceName)

    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { dest ->
        VaultLock.keepOpenOnce = false // Back in the app, whatever the outcome: leaving later must lock.
        if (dest == null) return@rememberLauncherForActivityResult
        val src = source
        val to = contact
        if (src == null || to == null) {
            discardDocumentLater(context, dest)
            tagged = PkMessage(inputsNow, false, "No se ha guardado: vuelve a elegir el archivo y el contacto.")
            return@rememberLauncherForActivityResult
        }
        val sender = signNow
        val name = if (hideName) anonymousFileName(sourceName) else sourceName
        val jobInputs = PkInputs(to.id, null, src.toString())
        tagged = null
        job = scope.launchBusy({ busy = it }) {
            try {
                withContext(Dispatchers.Default) {
                    val check = cancellationCheck()
                    context.contentResolver.openInputStream(src)!!.use { input ->
                        context.contentResolver.openOutputStream(dest, "wt")!!.use { out ->
                            encryptFileForContact(input.buffered(), out.buffered(), name, to, sender, entries, check)
                        }
                    }
                }
                tagged = PkMessage(jobInputs, true, "Archivo cifrado y guardado.")
            } catch (e: CancellationException) {
                discardDocumentNow(context, dest) // Never leave a truncated file behind.
                throw e
            } catch (e: Exception) {
                discardDocumentNow(context, dest)
                tagged = PkMessage(jobInputs, false, actionErrorMessage(e, "No se ha podido cifrar el archivo."))
            }
        }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        VaultLock.keepOpenOnce = false
        if (uri != null) {
            source = uri
            sourceName = displayName(context, uri)
            tagged = null
        }
    }

    // M-9: imePadding, so the field stays above the keyboard with edge-to-edge.
    Column(Modifier.imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(ENCRYPT_HELP, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextOrFile(isFile) { isFile = it; result = null; tagged = null }
        if (isFile) {
            SourceButton(source, sourceName) {
                launchPicker(store) { pick.launchOrError(arrayOf("*/*")) }?.let { tagged = PkMessage(inputsNow, false, it) }
            }
            if (source != null) {
                // A15b: the recipient sees the name inside; «No enviar el nombre» sends archivo.<ext> instead.
                Text(fileNameInsideText(nameInside), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                // The whole row toggles it, read by TalkBack as one checkbox.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().toggleable(value = hideName, enabled = !busy, role = Role.Checkbox) { hideName = it },
                ) {
                    Checkbox(checked = hideName, enabled = !busy, onCheckedChange = null)
                    Text(DONT_SEND_FILE_NAME)
                }
            }
        } else {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it; result = null; tagged = null },
                label = { Text("Texto a cifrar") },
                minLines = 4,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Text("Para", style = MaterialTheme.typography.titleSmall)
        if (contacts.isEmpty()) {
            Text("Todavía no tienes contactos: añade uno en Contactos.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        contacts.forEach { c ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable { contactId = c.id; result = null }.padding(vertical = 2.dp),
            ) {
                RadioButton(selected = c.id == contactId, onClick = { contactId = c.id; result = null })
                Column {
                    Text(c.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        verificationMark(c),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (c.verified) PositiveColor else CautionColor,
                    )
                }
            }
        }
        warning?.let { Text(it, color = CautionColor) }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Firmar con mi identidad")
                Text(
                    if (hasIdentity) "Así sabrá que es tuyo y que no ha cambiado." else NO_IDENTITY_TO_SIGN,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Not while working: «Firmar» is not among the inputs a result carries, so it cannot change under a job.
            Switch(checked = signNow, enabled = hasIdentity && !busy, onCheckedChange = { sign = it; result = null })
        }

        if (isFile) {
            Button(
                enabled = !busy && source != null && contact != null,
                onClick = {
                    VaultLock.keepOpenOnce = true
                    store.touch()
                    // With «No enviar el nombre» the proposed outer name does not carry it either.
                    save.launchOrError(encryptedFileName(if (hideName) anonymousFileName(sourceName) else sourceName))?.let { failure ->
                        VaultLock.keepOpenOnce = false
                        tagged = PkMessage(inputsNow, false, failure)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Cifrar y guardar") }
        } else {
            Button(
                enabled = !busy && text.isNotEmpty() && contact != null,
                onClick = {
                    val to = contact ?: return@Button
                    val plain = text
                    val sender = signNow
                    val inputs = PkInputs(to.id, plain, null)
                    tagged = null
                    result = null
                    job = scope.launchBusy({ busy = it }) {
                        try {
                            val token = withContext(Dispatchers.Default) { encryptTextForContact(plain, to, sender, entries) }
                            result = PkResult(inputs, token)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            tagged = PkMessage(inputs, false, actionErrorMessage(e, "No se ha podido cifrar el mensaje."))
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Cifrar") }
        }
        if (busy) Busy("Cifrando…") { job?.cancel(); tagged = PkMessage(inputsNow, false, "Cancelado.") }
        ResultMessage(messageFor(inputsNow, tagged))
        output?.let { PublicOutput(context, it, "Compartir mensaje cifrado") { err -> tagged = PkMessage(inputsNow, false, err) } }
    }
}

// ---- Firmar ----

/** «Firmar» (`contacts/sign`): un texto o un archivo. */
@Composable
fun SignScreen(store: VaultStore, onBack: () -> Unit) {
    ContactsFrame(store, "Firmar", onBack) { entries, _ -> SignContent(store, entries) }
}

@Composable
private fun SignContent(store: VaultStore, entries: List<VaultEntry>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val hasIdentity = remember(entries) { entries.identity() != null }
    var isFile by rememberSaveable { mutableStateOf(false) }
    var source by rememberSaveable { mutableStateOf<Uri?>(null) }
    var sourceName by rememberSaveable { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    // A15a: with the inputs it came from; a rotation loses it, like the text.
    var result by remember { mutableStateOf<PkResult?>(null) }
    // A15a: a message also carries the inputs it is about, and shows only next to them.
    var tagged by remember { mutableStateOf<PkMessage?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    var busy by remember { mutableStateOf(false) }
    val inputsNow = PkInputs(null, text.takeIf { !isFile }, source?.toString()?.takeIf { isFile })
    val output = resultFor(inputsNow, result)

    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { dest ->
        VaultLock.keepOpenOnce = false
        if (dest == null) return@rememberLauncherForActivityResult
        val src = source
        if (src == null) {
            discardDocumentLater(context, dest)
            tagged = PkMessage(inputsNow, false, "No se ha guardado: vuelve a elegir el archivo.")
            return@rememberLauncherForActivityResult
        }
        val jobInputs = PkInputs(null, null, src.toString())
        tagged = null
        job = scope.launchBusy({ busy = it }) {
            try {
                withContext(Dispatchers.Default) {
                    val check = cancellationCheck()
                    val signature = context.contentResolver.openInputStream(src)!!.use { signFileAs(it.buffered(), entries, check) }
                    context.contentResolver.openOutputStream(dest, "wt")!!.use { it.write(signature) }
                }
                tagged = PkMessage(jobInputs, true, "Firma guardada. Envía el archivo y su .sgsig juntos.")
            } catch (e: CancellationException) {
                discardDocumentNow(context, dest)
                throw e
            } catch (e: Exception) {
                discardDocumentNow(context, dest)
                tagged = PkMessage(jobInputs, false, actionErrorMessage(e, "No se ha podido firmar el archivo."))
            }
        }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        VaultLock.keepOpenOnce = false
        if (uri != null) {
            source = uri
            sourceName = displayName(context, uri)
            tagged = null
        }
    }

    // M-9: imePadding, so the field stays above the keyboard with edge-to-edge.
    Column(Modifier.imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(SIGN_HELP, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!hasIdentity) Text(NO_IDENTITY_TO_SIGN, color = CautionColor)
        TextOrFile(isFile) { isFile = it; result = null; tagged = null }
        if (isFile) {
            SourceButton(source, sourceName) {
                launchPicker(store) { pick.launchOrError(arrayOf("*/*")) }?.let { tagged = PkMessage(inputsNow, false, it) }
            }
            Text(
                "La firma va en un archivo aparte (.sgsig). No cubre el nombre del archivo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                enabled = !busy && hasIdentity && source != null,
                onClick = {
                    VaultLock.keepOpenOnce = true
                    store.touch()
                    save.launchOrError(signatureFileName(sourceName))?.let { failure ->
                        VaultLock.keepOpenOnce = false
                        tagged = PkMessage(inputsNow, false, failure)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Firmar y guardar") }
        } else {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it; result = null; tagged = null },
                label = { Text("Texto a firmar") },
                minLines = 4,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                enabled = !busy && hasIdentity && text.isNotBlank(),
                onClick = {
                    val plain = text
                    val inputs = PkInputs(null, plain, null)
                    tagged = null
                    result = null
                    job = scope.launchBusy({ busy = it }) {
                        try {
                            val signed = withContext(Dispatchers.Default) { signTextAs(plain, entries) }
                            result = PkResult(inputs, signed)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            tagged = PkMessage(inputs, false, actionErrorMessage(e, "No se ha podido firmar el texto."))
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Firmar") }
        }
        if (busy) Busy("Firmando…") { job?.cancel(); tagged = PkMessage(inputsNow, false, "Cancelado.") }
        ResultMessage(messageFor(inputsNow, tagged))
        output?.let { PublicOutput(context, it, "Compartir texto firmado") { err -> tagged = PkMessage(inputsNow, false, err) } }
    }
}

// ---- Abrir lo recibido ----

/** «Abrir lo recibido» (`contacts/open`): pegar un texto, o elegir un `.sgenc`, o un `.sgsig` con su archivo. */
@Composable
fun OpenReceivedScreen(store: VaultStore, onBack: () -> Unit, onOpenSymmetric: () -> Unit) {
    val context = LocalContext.current
    val state by store.state.collectAsStateWithLifecycle()
    val locked = state !is VaultState.Unlocked
    // With the vault locked nothing decrypted may stay in the private cache: the whole folder goes (each operation also
    // deletes its own subfolder when it ends, fails or is cancelled; the app wipes it at start too).
    LaunchedEffect(locked) {
        if (locked) withContext(Dispatchers.IO) { clearTempDir(File(context.cacheDir, PK_OPEN_TEMP_DIR)) }
    }
    // A text from the QR inspector that waited at the locked gate and was never taken goes with the screen, like the
    // 2FA one (QrTotpImportScreen). Once taken, there is nothing left to clear.
    DisposableEffect(Unit) { onDispose { QrHandoff.clear() } }
    ContactsFrame(store, "Abrir lo recibido", onBack) { entries, snackbar ->
        OpenReceivedContent(store, entries, snackbar, onOpenSymmetric)
    }
}

@Composable
private fun OpenReceivedContent(store: VaultStore, entries: List<VaultEntry>, snackbar: androidx.compose.material3.SnackbarHostState, onOpenSymmetric: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tempRoot = remember { File(context.cacheDir, PK_OPEN_TEMP_DIR) }
    // From the QR inspector, read once the vault is open: as pasted text, which «Abrir» then opens.
    var pasted by remember { mutableStateOf(QrHandoff.take(QrTarget.RECEIVED)?.takeIf { it.length <= MAX_TEXT_FILE_BYTES }.orEmpty()) }
    var textResult by remember { mutableStateOf<ReceivedOutcome?>(null) }
    // A15a: the text [textResult] came from; it is shown only while the field still holds that text.
    var textResultFrom by remember { mutableStateOf<PkInputs?>(null) }

    // Files: an encrypted one, or a signature plus the file it signs (in either order). Only non-secret data is saved.
    var encUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var encName by rememberSaveable { mutableStateOf("") }
    var sigBytes by rememberSaveable { mutableStateOf<ByteArray?>(null) }
    var signedUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var signedName by rememberSaveable { mutableStateOf("") }
    // The last file read as text: it may also be the file a `.sgsig` picked afterwards signs (`afterPicking`).
    var textUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var textName by rememberSaveable { mutableStateOf("") }
    var pending by remember { mutableStateOf<ReceivedFile?>(null) }
    var fileLine by remember { mutableStateOf<SenderLine?>(null) }
    var message by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var prompt by rememberSaveable { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    var busy by remember { mutableStateOf(false) }
    var symmetric by remember { mutableStateOf(false) }

    // The decrypted file waits in its own subfolder of the private cache only until saved: it goes when the screen goes
    // away (also when the vault locks), and the whole folder is wiped at app start and when the vault locks.
    DisposableEffect(Unit) {
        onDispose {
            discardInBackground(context, pending)
            pending = null
        }
    }

    // A15a: a text result is kept only if the field still holds the text it came from; a late one is dropped, not
    // hidden (a card's bytes are zeroed; a decrypted String cannot be, but no reference to it is kept).
    fun keepIfStillCurrent(outcome: ReceivedOutcome, from: PkInputs) {
        if (from == PkInputs(null, pasted, null)) {
            textResult = outcome
            textResultFrom = from
        } else if (outcome is ReceivedOutcome.CardFound) {
            outcome.bytes.fill(0)
        }
    }

    fun resetFiles() {
        encUri = null
        encName = ""
        sigBytes = null
        signedUri = null
        signedName = ""
        textUri = null
        textName = ""
        prompt = null
        symmetric = false
    }

    val saveDecrypted = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { dest ->
        VaultLock.keepOpenOnce = false
        val received = pending
        if (received == null || !received.file.exists()) {
            // The screen was rebuilt meanwhile (the decrypted copy is never kept in saved state): nothing to write.
            pending = null
            if (dest != null) {
                discardDocumentLater(context, dest)
                message = false to "No se ha guardado: vuelve a descifrar el archivo."
            }
            return@rememberLauncherForActivityResult
        }
        if (dest == null) {
            discardInBackground(context, received)
            pending = null
            message = false to "No se ha guardado nada."
            return@rememberLauncherForActivityResult
        }
        job = scope.launchBusy({ busy = it }) {
            try {
                withContext(Dispatchers.Default) {
                    val check = cancellationCheck()
                    context.contentResolver.openOutputStream(dest, "wt")!!.use { out -> saveReceivedFile(received, out, check) }
                }
                // Only now, with everything done, the sender is shown (the signature is at the end of the file).
                fileLine = senderLine(received.sender, addressedToMe = true)
                message = true to "Archivo descifrado y guardado."
            } catch (e: CancellationException) {
                discardDocumentNow(context, dest)
                throw e
            } catch (e: Exception) {
                discardDocumentNow(context, dest)
                message = false to actionErrorMessage(e, "No se ha podido guardar el archivo.")
            } finally {
                discardInBackground(context, received)
                pending = null
            }
        }
    }
    val pickReceived = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        VaultLock.keepOpenOnce = false
        if (uri == null) return@rememberLauncherForActivityResult
        message = null
        fileLine = null
        job = scope.launchBusy({ busy = it }) {
            try {
                // At most MAX_TEXT_FILE_BYTES + 1 bytes are read: a text file is read like a pasted text, anything else
                // is told apart by its first bytes (a signature is 101 bytes).
                val picked = withContext(Dispatchers.Default) {
                    val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readAtMost(MAX_TEXT_FILE_BYTES + 1) }
                    classifyPicked(bytes, haveSignature = sigBytes != null)
                }
                val name = displayName(context, uri)
                val chosen = FileSelection(encUri, encName, sigBytes, signedUri, signedName, textUri, textName).afterPicking(picked, uri, name)
                encUri = chosen.encrypted
                encName = chosen.encryptedName
                sigBytes = chosen.signature
                signedUri = chosen.signed
                signedName = chosen.signedName
                textUri = chosen.textCandidate
                textName = chosen.textCandidateName
                symmetric = picked == PickedFile.Symmetric
                // A signature file is checked against the file, not the text: the earlier text result (with its own
                // sender line) would sit next to the one for the signature.
                if (picked is PickedFile.Signature) textResult = null
                val kind = when (picked) {
                    PickedFile.Encrypted -> PickedKind.ENCRYPTED
                    is PickedFile.Signature -> PickedKind.SIGNATURE
                    else -> PickedKind.OTHER
                }
                if (picked is PickedFile.Text) {
                    pasted = picked.text
                    fileLine = null
                    textResult = null
                    val from = PkInputs(null, picked.text, null)
                    val outcome = withContext(Dispatchers.Default) { openReceivedText(picked.text, entries) }
                    keepIfStillCurrent(outcome, from)
                }
                prompt = if (picked is PickedFile.Symmetric || picked is PickedFile.Text) {
                    null
                } else {
                    when (nextPickStep(kind, haveSignature = sigBytes != null, haveFile = signedUri != null)) {
                        PickStep.READY_TO_DECRYPT -> null
                        PickStep.READY_TO_VERIFY -> null
                        PickStep.NEED_SIGNED_FILE -> "Ahora elige el archivo que firma."
                        PickStep.NEED_SIGNATURE -> "Ahora elige su firma (.sgsig)."
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = false to openErrorMessage(e)
            }
        }
    }

    // M-9: imePadding, so the field stays above the keyboard with edge-to-edge.
    Column(Modifier.imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Pega lo que te hayan enviado (una tarjeta, un mensaje cifrado o un texto firmado), o elige un archivo cifrado (.sgenc) " +
                "o una firma (.sgsig) con su archivo.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // ---- Pasted text ----
        OutlinedTextField(
            value = pasted,
            onValueChange = {
                if (it.length <= MAX_TEXT_FILE_BYTES) {
                    pasted = it
                    textResult = null
                } else {
                    message = false to "El texto es demasiado grande"
                }
            },
            label = { Text("Texto recibido") },
            minLines = 4,
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                val clip = readClipboardText(context)
                if (clip.isNullOrBlank()) message = false to "El portapapeles está vacío"
                else if (clip.length > MAX_TEXT_FILE_BYTES) message = false to "El texto es demasiado grande"
                else {
                    pasted = clip
                    textResult = null
                    message = null
                }
            }) { Text("Pegar") }
            Button(
                enabled = !busy && pasted.isNotBlank(),
                onClick = {
                    val text = pasted
                    message = null
                    fileLine = null
                    textResult = null
                    job = scope.launchBusy({ busy = it }) {
                        val outcome = try {
                            withContext(Dispatchers.Default) { openReceivedText(text, entries) }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            ReceivedOutcome.Failed(openErrorMessage(e))
                        }
                        keepIfStillCurrent(outcome, PkInputs(null, text, null))
                    }
                },
            ) { Text("Abrir") }
        }
        when (val r = textResult?.takeIf { textResultFrom == PkInputs(null, pasted, null) }) {
            null -> Unit
            is ReceivedOutcome.Failed -> Text(r.message, color = NegativeColor)
            is ReceivedOutcome.CardFound -> IncomingCardSection(
                store, entries, cardText(r.bytes), viaQr = false, snackbar,
                onSaved = { textResult = null; pasted = "" },
            )
            is ReceivedOutcome.Opened -> SectionCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SenderText(r.line)
                    // Plain text, not selectable: the only way out is «Copiar», which marks it as sensitive.
                    Text(r.text)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { copySensitive(context, r.text) }) { Text("Copiar") }
                        TextButton(onClick = { textResult = null; pasted = "" }) { Text("Borrar") }
                    }
                }
            }
        }

        // ---- Files ----
        Text("Archivos", style = MaterialTheme.typography.titleSmall)
        OutlinedButton(
            enabled = !busy,
            onClick = { launchPicker(store) { pickReceived.launchOrError(arrayOf("*/*")) }?.let { message = false to it } },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Elegir archivo (.sgenc, .sgsig o el archivo firmado)") }
        if (symmetric) {
            Text(SYMMETRIC_FILE_NOTICE, color = CautionColor, style = MaterialTheme.typography.titleSmall)
            Button(onClick = onOpenSymmetric, modifier = Modifier.fillMaxWidth()) { Text("Ir a Cifrar archivos") }
        }
        // The provider's name was chosen by whoever sent the file: shown through the same sanitiser (the Uri is untouched).
        if (encUri != null) Text("Cifrado: ${sanitizeReceivedName(encName)}")
        if (sigBytes != null) Text("Firma (.sgsig) elegida")
        if (signedUri != null) Text("Archivo firmado: ${sanitizeReceivedName(signedName)}")
        prompt?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }

        if (encUri != null) {
            Button(
                enabled = !busy,
                onClick = {
                    val src = encUri ?: return@Button
                    message = null
                    fileLine = null
                    job = scope.launchBusy({ busy = it }) {
                        try {
                            // `pending` is set inside, so a cancellation right after it still finds the subfolder to delete.
                            val received = withContext(Dispatchers.Default) {
                                val check = cancellationCheck()
                                context.contentResolver.openInputStream(src)!!.use { decryptReceivedFileToTemp(tempRoot, it.buffered(), entries, check) }
                                    .also { pending = it }
                            }
                            // It is whole and checked: now the user picks where it goes (the sender is shown after saving).
                            VaultLock.keepOpenOnce = true
                            store.touch()
                            saveDecrypted.launchOrError(received.name)?.let { failure ->
                                VaultLock.keepOpenOnce = false
                                discardInBackground(context, received)
                                pending = null
                                message = false to failure
                            }
                        } catch (e: CancellationException) {
                            discardInBackground(context, pending)
                            pending = null
                            throw e
                        } catch (e: Exception) {
                            discardInBackground(context, pending)
                            pending = null
                            message = false to openErrorMessage(e)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Descifrar y guardar") }
        }
        val sig = sigBytes
        val signed = signedUri
        if (sig != null && signed != null && encUri == null) {
            Button(
                enabled = !busy,
                onClick = {
                    message = null
                    fileLine = null
                    textResult = null
                    job = scope.launchBusy({ busy = it }) {
                        try {
                            val sender = withContext(Dispatchers.Default) {
                                val check = cancellationCheck()
                                context.contentResolver.openInputStream(signed)!!.use { verifyFileAgainst(it.buffered(), sig, entries, check) }
                            }
                            fileLine = senderLine(sender, addressedToMe = false)
                            message = true to "La firma es válida: el archivo no ha cambiado."
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            message = false to openErrorMessage(e)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Comprobar firma") }
        }
        if (encUri != null || sigBytes != null || signedUri != null) {
            TextButton(enabled = !busy, onClick = { resetFiles(); message = null; fileLine = null }) { Text("Quitar los archivos elegidos") }
        }
        if (busy) Busy("Trabajando…") { job?.cancel(); message = false to "Cancelado." }
        ResultMessage(message)
        fileLine?.let { SenderText(it) }
    }
}
