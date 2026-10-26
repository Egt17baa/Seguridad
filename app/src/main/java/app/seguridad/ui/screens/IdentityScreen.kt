package app.seguridad.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.seguridad.android.FilterObscuredTouches
import app.seguridad.ui.theme.NegativeColor
import app.seguridad.vault.ContactName
import app.seguridad.vault.DELETE_IDENTITY_CONFIRMATION
import app.seguridad.vault.DELETE_IDENTITY_WARNING
import app.seguridad.vault.IDENTITY_EXPLANATION
import app.seguridad.vault.IDENTITY_RISK
import app.seguridad.vault.MyCard
import app.seguridad.vault.VaultEntry
import app.seguridad.vault.VaultLock
import app.seguridad.vault.VaultStore
import app.seguridad.vault.checkOwnName
import app.seguridad.vault.identity
import app.seguridad.vault.myCard
import app.seguridad.vault.newIdentityEntry
import app.seguridad.vault.saveFailureMessage
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * One pixel per QR module, like the 2FA one (the Image scales it up without smoothing), with the lowest error correction
 * (L): the card is about 2,000 characters in the alphanumeric mode, so the code stays as coarse as it can be and scans from
 * screen to screen. Only in memory, never written anywhere. The text is public (a card holds no secret).
 */
internal fun cardQrBitmap(text: String): Bitmap? = try {
    val m = QRCodeWriter().encode(
        text,
        BarcodeFormat.QR_CODE,
        0,
        0,
        mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L, EncodeHintType.MARGIN to 4),
    )
    val pixels = IntArray(m.width * m.height) { i -> if (m.get(i % m.width, i / m.width)) android.graphics.Color.BLACK else android.graphics.Color.WHITE }
    Bitmap.createBitmap(pixels, m.width, m.height, Bitmap.Config.ARGB_8888)
} catch (e: Exception) {
    null
}

/** What the screen has of my card: still being worked out, failed, or ready (the QR may fail on its own). */
private sealed interface MyCardState {
    data object Loading : MyCardState

    data object Failed : MyCardState

    class Ready(val card: MyCard, val qr: Bitmap?) : MyCardState
}

/** «Mi identidad» (`contacts/me`): create it, see its QR and fingerprint, share the card, change the name, delete it. */
@Composable
fun IdentityScreen(store: VaultStore, onBack: () -> Unit) {
    ContactsFrame(store, "Mi identidad", onBack) { entries, snackbar -> IdentityBody(store, entries.identity(), snackbar) }
}

@Composable
private fun IdentityBody(store: VaultStore, identity: VaultEntry?, snackbar: SnackbarHostState) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    // The name on my card: optional, and it is the only thing of mine other people read. Public, so it may be saved state.
    var name by rememberSaveable(identity?.id) { mutableStateOf(identity?.username.orEmpty()) }
    val nameCheck = checkOwnName(name)

    fun report(result: Result<Boolean>) {
        saveFailureMessage(result)?.let { scope.launch { snackbar.showSnackbar(it) } }
    }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(IDENTITY_EXPLANATION, modifier = Modifier.fillMaxWidth())
        if (identity == null) {
            NameField(name, { name = it }, nameCheck)
            Text(IDENTITY_RISK, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth())
            Button(
                enabled = !busy && nameCheck is ContactName.Valid,
                onClick = {
                    val own = (nameCheck as? ContactName.Valid)?.name ?: return@Button
                    busy = true
                    scope.launch {
                        report(attempt { store.upsert(newIdentityEntry(System.currentTimeMillis()).copy(username = own)) })
                        busy = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Crear mi identidad") }
            return@Column
        }
        MyCardSection(store, identity, snackbar)
        NameField(name, { name = it }, nameCheck)
        Button(
            enabled = !busy && nameCheck is ContactName.Valid && nameCheck.name != identity.username,
            onClick = {
                val own = (nameCheck as? ContactName.Valid)?.name ?: return@Button
                busy = true
                scope.launch {
                    report(attempt { store.upsert(identity.copy(username = own, updatedAt = System.currentTimeMillis())) })
                    busy = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Guardar nombre") }
        Text(IDENTITY_RISK, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth())
        DeleteIdentity(store, identity, snackbar, enabled = !busy)
    }
}

@Composable
private fun NameField(name: String, onChange: (String) -> Unit, check: ContactName) {
    OutlinedTextField(
        value = name,
        onValueChange = onChange,
        label = { Text("Tu nombre en la tarjeta (opcional)") },
        singleLine = true,
        isError = check is ContactName.Invalid,
        supportingText = { Text((check as? ContactName.Invalid)?.message ?: "Lo verán tus contactos. Puedes dejarlo vacío.") },
        modifier = Modifier.fillMaxWidth(),
    )
}

/** The QR, the fingerprint and «Compartir mi tarjeta». The card is worked out off the main thread once per identity and name. */
@Composable
private fun MyCardSection(store: VaultStore, identity: VaultEntry, snackbar: SnackbarHostState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // The identity itself is opened inside `myCard()` and closed there: only the public card is kept.
    val state by produceState<MyCardState>(MyCardState.Loading, identity.id, identity.username) {
        value = withContext(Dispatchers.Default) {
            try {
                val card = identity.myCard()
                MyCardState.Ready(card, cardQrBitmap(card.qrText))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                MyCardState.Failed
            }
        }
    }
    when (val s = state) {
        MyCardState.Loading -> Text("Preparando tu tarjeta…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        MyCardState.Failed -> Text("No se pudo preparar tu tarjeta. Revisa el nombre o inténtalo de nuevo.", color = NegativeColor)
        is MyCardState.Ready -> {
            if (s.qr == null) {
                Text("No se pudo generar el QR. Comparte la tarjeta como texto.", color = NegativeColor)
            } else {
                // White quiet zone whatever the theme: scanners need dark modules on a light background.
                Box(Modifier.fillMaxWidth().aspectRatio(1f).background(Color.White)) {
                    Image(
                        s.qr.asImageBitmap(),
                        contentDescription = "Código QR de tu tarjeta",
                        filterQuality = FilterQuality.None,
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                    )
                }
                Text(
                    "Para añadirte, tu contacto pulsa «Escanear QR» en Contactos → Añadir contacto.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            FingerprintBlock("Tu huella", s.card.fingerprint, Modifier.fillMaxWidth())
            Text(
                "Tu contacto debe ver estas mismas cifras en su móvil. Compáralas en persona o por llamada.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    // The share sheet is another screen: leaving for it must not lock the vault (the picker rule).
                    VaultLock.keepOpenOnce = true
                    store.touch()
                    try {
                        // The card text is public (name and public keys): no read permission is granted to the receiver.
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, s.card.text)
                        context.startActivity(Intent.createChooser(send, "Compartir mi tarjeta"))
                    } catch (e: Exception) {
                        VaultLock.keepOpenOnce = false // The app never left: leaving later must lock.
                        scope.launch { snackbar.showSnackbar("No se pudo abrir el menú de compartir") }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Compartir mi tarjeta") }
            Text(
                "Se comparte como texto: tu nombre y tus claves públicas, nada secreto.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** «Borrar mi identidad»: the warning of the design, then a second confirmation with a red button. */
@Composable
private fun DeleteIdentity(store: VaultStore, identity: VaultEntry, snackbar: SnackbarHostState, enabled: Boolean) {
    val scope = rememberCoroutineScope()
    var step by remember { mutableIntStateOf(0) }
    OutlinedButton(onClick = { step = 1 }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Text("Borrar mi identidad", color = NegativeColor)
    }
    if (step == 1) {
        AlertDialog(
            onDismissRequest = { step = 0 },
            title = { Text("Borrar mi identidad") },
            text = {
                FilterObscuredTouches()
                Text(DELETE_IDENTITY_WARNING)
            },
            confirmButton = { TextButton(onClick = { step = 2 }) { Text("Continuar", color = NegativeColor) } },
            dismissButton = { TextButton(onClick = { step = 0 }) { Text("Cancelar") } },
        )
    }
    if (step == 2) {
        AlertDialog(
            onDismissRequest = { step = 0 },
            title = { Text("¿Borrarla de verdad?") },
            text = {
                FilterObscuredTouches()
                Text(DELETE_IDENTITY_CONFIRMATION)
            },
            confirmButton = {
                Button(
                    onClick = {
                        step = 0
                        scope.launch {
                            saveFailureMessage(attempt { store.delete(identity.id) })?.let { snackbar.showSnackbar(it) }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NegativeColor, contentColor = MaterialTheme.colorScheme.surface),
                ) { Text("Borrar mi identidad") }
            },
            dismissButton = { TextButton(onClick = { step = 0 }) { Text("Cancelar") } },
        )
    }
}
