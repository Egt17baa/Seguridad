package app.seguridad.ui.screens

import android.content.ClipboardManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.seguridad.android.scanQrOptions
import app.seguridad.crypto.pk.Card
import app.seguridad.crypto.pk.MAX_CARD_BYTES
import app.seguridad.crypto.pk.cardBytesFromAnyText
import app.seguridad.crypto.pk.parseCard
import app.seguridad.ui.theme.CautionColor
import app.seguridad.ui.theme.NegativeColor
import app.seguridad.ui.theme.SectionCard
import app.seguridad.vault.CardCheck
import app.seguridad.vault.ContactName
import app.seguridad.vault.EntryType
import app.seguridad.vault.NoticeLevel
import app.seguridad.vault.TOO_BIG_CARD
import app.seguridad.vault.VaultEntry
import app.seguridad.vault.VaultLock
import app.seguridad.vault.VaultStore
import app.seguridad.vault.cardFingerprint
import app.seguridad.vault.cardReadErrorMessage
import app.seguridad.vault.checkContactName
import app.seguridad.vault.checkIncomingCard
import app.seguridad.vault.incomingCardNotice
import app.seguridad.vault.newContactEntry
import app.seguridad.vault.saveFailureMessage
import app.seguridad.vault.savedFingerprint
import app.seguridad.vault.suggestedContactName
import com.google.zxing.client.android.Intents
import com.journeyapps.barcodescanner.ScanContract
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A card read from text, with its canonical bytes and what to do with it. [otherFingerprint] is the saved contact's, if keys changed. */
private sealed interface Incoming {
    class Failed(val message: String) : Incoming

    class Ready(val card: Card, val bytes: ByteArray, val check: CardCheck, val otherFingerprint: String?) : Incoming
}

/**
 * «Añadir contacto» (`contacts/add`): scan a QR (the contact is saved as verified) or paste a card (unverified). Nothing is
 * saved until the user taps the save button, with the fingerprint in view; the unknown-key-share case has no such button
 * and a changed-keys card is only ever saved as a new contact, never over the old one.
 */
@Composable
fun ContactAddScreen(store: VaultStore, onBack: () -> Unit) {
    // Outside the gate on purpose: a scan that comes back after the vault locked is not lost (it only shows once open).
    // The text is public (a card), and never longer than a card can be, so it may live in saved state.
    var text by rememberSaveable { mutableStateOf<String?>(null) }
    var viaQr by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    fun accept(raw: String, qr: Boolean) {
        if (raw.length > MAX_CARD_BYTES) {
            text = null
            error = TOO_BIG_CARD
        } else {
            text = raw
            viaQr = qr
            error = null
        }
    }

    // The library asks for the CAMERA permission itself; a refusal comes back as a result flag, not as an exception.
    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        VaultLock.keepOpenOnce = false // Back in the app, whatever the outcome: leaving later must lock.
        val raw = result.contents
        when {
            raw != null -> accept(raw, qr = true)
            result.originalIntent?.getBooleanExtra(Intents.Scan.MISSING_CAMERA_PERMISSION, false) == true ->
                error = "Sin permiso de cámara: pega la tarjeta como texto"
            // Otherwise the user cancelled: nothing to say.
        }
    }

    ContactsFrame(store, "Añadir contacto", onBack) { entries, snackbar ->
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Escanea el QR de tu contacto, delante de él, o pega la tarjeta que te haya enviado.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = {
                    VaultLock.keepOpenOnce = true
                    store.touch() // The time away is counted from here (AWAY_BACKSTOP_MS with «Al salir»).
                    try {
                        scanner.launch(scanQrOptions())
                    } catch (e: Exception) {
                        VaultLock.keepOpenOnce = false // The app never left: leaving later must lock.
                        error = "No se pudo abrir el escáner: pega la tarjeta como texto"
                    }
                }) { Text("📷 Escanear QR") }
                OutlinedButton(onClick = {
                    val clip = context.getSystemService(ClipboardManager::class.java)?.primaryClip?.getItemAt(0)?.text?.toString()
                    if (clip.isNullOrBlank()) error = "El portapapeles está vacío" else accept(clip, qr = false)
                }) { Text("Pegar tarjeta") }
            }
            error?.let { Text(it, color = NegativeColor) }
            text?.let { IncomingCardSection(store, entries, it, viaQr, snackbar, onSaved = onBack) }
        }
    }
}

/** Reads [text] and compares it with my identity and the contacts, off the main thread, and shows what comes out. */
@Composable
internal fun IncomingCardSection(
    store: VaultStore,
    entries: List<VaultEntry>,
    text: String,
    viaQr: Boolean,
    snackbar: SnackbarHostState,
    onSaved: () -> Unit,
) {
    val incoming by produceState<Incoming?>(null, text, entries) {
        value = withContext(Dispatchers.Default) {
            try {
                val bytes = cardBytesFromAnyText(text)
                val card = parseCard(bytes)
                val check = checkIncomingCard(card, entries)
                val other = savedFingerprint(check)
                Incoming.Ready(card, bytes, check, other)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Incoming.Failed(cardReadErrorMessage(e))
            }
        }
    }
    when (val i = incoming) {
        null -> Unit
        is Incoming.Failed -> Text(i.message, color = NegativeColor)
        is Incoming.Ready -> ReadyCard(store, i, text, viaQr, snackbar, onSaved)
    }
}

@Composable
private fun ReadyCard(store: VaultStore, ready: Incoming.Ready, text: String, viaQr: Boolean, snackbar: SnackbarHostState, onSaved: () -> Unit) {
    val scope = rememberCoroutineScope()
    val notice = remember(ready.check, viaQr) { incomingCardNotice(ready.check, viaQr) }
    val fingerprint = remember(ready.card) { cardFingerprint(ready.card) }
    // Filled from the card (the name another person chose) and editable: the saved name is the user's own.
    var name by rememberSaveable(text) { mutableStateOf(suggestedContactName(ready.check)) }
    val nameCheck = checkContactName(name)
    var busy by remember { mutableStateOf(false) }
    val noticeColor = when (notice.level) {
        NoticeLevel.INFO -> MaterialTheme.colorScheme.onSurface
        NoticeLevel.WARNING -> CautionColor
        NoticeLevel.DANGER -> NegativeColor
    }
    SectionCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // All of these are plain Text: a name from a card is never read as a link or markdown.
            Text(notice.title, style = MaterialTheme.typography.titleMedium, color = noticeColor)
            if (notice.body.isNotEmpty()) Text(notice.body, color = if (notice.level == NoticeLevel.INFO) Color.Unspecified else noticeColor)
            // The fingerprint is always in view before saving, and with the same encryption key in two cards both are shown
            // (the notice asks to compare them with both people; it still has no save button).
            if (notice.saveLabel != null || ready.check is CardCheck.SharedKem) {
                ready.otherFingerprint?.let { other ->
                    val sharedWithMe = (ready.check as? CardCheck.SharedKem)?.entry?.type == EntryType.IDENTITY
                    FingerprintBlock(if (sharedWithMe) "Huella de tu identidad" else "Huella del contacto que ya tienes", other)
                }
                FingerprintBlock(if (ready.otherFingerprint != null) "Huella de la tarjeta nueva" else "Huella de la tarjeta", fingerprint)
            }
            if (notice.footer.isNotEmpty()) Text(notice.footer, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (notice.saveLabel != null) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre del contacto") },
                    singleLine = true,
                    isError = nameCheck is ContactName.Invalid,
                    supportingText = { (nameCheck as? ContactName.Invalid)?.let { Text(it.message) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    enabled = !busy && nameCheck is ContactName.Valid,
                    onClick = {
                        val saved = (nameCheck as? ContactName.Valid)?.name ?: return@Button
                        busy = true
                        scope.launch {
                            val result = attempt {
                                val entry = withContext(Dispatchers.Default) {
                                    newContactEntry(ready.card, ready.bytes, System.currentTimeMillis(), viaQr).copy(title = saved)
                                }
                                store.upsert(entry)
                            }
                            val failure = saveFailureMessage(result)
                            if (failure == null) onSaved() else snackbar.showSnackbar(failure)
                            busy = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(notice.saveLabel) }
            }
        }
    }
}
