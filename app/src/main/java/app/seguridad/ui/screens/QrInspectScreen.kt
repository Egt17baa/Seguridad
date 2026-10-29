package app.seguridad.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.seguridad.android.QrImageUnreadable
import app.seguridad.android.copySensitive
import app.seguridad.android.decodeQrFromImage
import app.seguridad.android.scanQrOptions
import app.seguridad.tools.imageOpenFailureText
import app.seguridad.tools.QR_ADD_CONTACT
import app.seguridad.tools.QR_ADD_TOTP
import app.seguridad.tools.QR_CAMERA_FAILED
import app.seguridad.tools.QR_COPY
import app.seguridad.tools.QR_COPY_PASSWORD
import app.seguridad.tools.QR_EXPLANATION
import app.seguridad.tools.QR_FIELD_PASSWORD
import app.seguridad.tools.QR_FIELD_WEB
import app.seguridad.tools.QR_FULL_TEXT
import app.seguridad.tools.QR_HIDE
import app.seguridad.tools.QR_IMAGE_DAMAGED
import app.seguridad.tools.QR_IMAGE_TOO_BIG
import app.seguridad.tools.QR_IMAGE_TOO_LARGE
import app.seguridad.tools.QR_NOT_FOUND
import app.seguridad.tools.QR_NO_ALERTS
import app.seguridad.tools.QR_NO_CAMERA_PERMISSION
import app.seguridad.tools.QR_OPEN_RECEIVED
import app.seguridad.tools.QR_PICK_IMAGE
import app.seguridad.tools.QR_READING_IMAGE
import app.seguridad.tools.QR_SCAN
import app.seguridad.tools.QR_SHOW
import app.seguridad.tools.QR_TEXT_HIDDEN_OTP
import app.seguridad.tools.QR_TEXT_HIDDEN_PASSWORD
import app.seguridad.tools.QR_TOOL_TITLE
import app.seguridad.tools.QR_TOO_BIG
import app.seguridad.tools.qr.QrAction
import app.seguridad.tools.qr.QrHandoff
import app.seguridad.tools.qr.QrTarget
import app.seguridad.tools.qr.QrView
import app.seguridad.tools.qr.qrView
import app.seguridad.tools.qr.visibleText
import app.seguridad.tools.qrAlertsTitle
import app.seguridad.tools.qrHostReading
import app.seguridad.ui.theme.CautionColor
import app.seguridad.ui.theme.NegativeColor
import app.seguridad.ui.theme.NoCopy
import app.seguridad.ui.theme.SectionCard
import app.seguridad.vault.EntryType
import app.seguridad.vault.VaultLock
import app.seguridad.vault.VaultState
import app.seguridad.vault.VaultStore
import com.google.zxing.client.android.Intents
import com.journeyapps.barcodescanner.ScanContract
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * «Inspector de QR» (`tools/qr`): reads a QR with the camera or from a picked image and shows what it holds, with the
 * signals of the usual tricks. It only shows and copies: there is no way here to open a link, join a network, pay or
 * call, and no intent is ever started with the QR's text. The only ways on are the app's own screens for its own texts
 * (a contact card, something received, a 2FA code), which get the text through [QrHandoff], never in the route.
 *
 * What is read lives only in `remember`, never in saved state (a Wi-Fi password or a 2FA key may be in it): turning the
 * phone or coming back from another screen empties it. Nothing is saved and nothing is logged.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrInspectScreen(store: VaultStore, onAddContact: () -> Unit, onOpenReceived: () -> Unit, onAddTotp: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var view by remember { mutableStateOf<QrView?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    // Back here, a text handed to a screen that never took it (its vault gate was left locked) does not stay around.
    LaunchedEffect(Unit) { QrHandoff.clear() }

    // A result with a secret in it (a Wi-Fi password, a 2FA key) does not wait on screen for whoever picks the phone up
    // next: it goes when the app stops (Home, screen off, another app; the scanner and the picker too, which is harmless:
    // the result they bring is shown after) and when the vault locks, as the vault's own screens do.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { if (view?.carriesSecret == true) view = null }
    LaunchedEffect(store) {
        store.state.map { it is VaultState.Unlocked }.distinctUntilChanged().drop(1).collect { open ->
            if (!open && view?.carriesSecret == true) view = null
        }
    }

    fun show(raw: String) {
        error = null
        view = try {
            // A picked image decoded while the app was away: a secret is not left there for when it comes back.
            qrView(raw).takeUnless { it.carriesSecret && !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) }
        } catch (e: IllegalArgumentException) {
            error = QR_TOO_BIG
            null
        }
    }

    // The library asks for the CAMERA permission itself; a refusal comes back as a result flag, not as an exception.
    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        VaultLock.keepOpenOnce = false // Back in the app, whatever the outcome: leaving later must lock.
        val raw = result.contents
        when {
            raw != null -> show(raw)
            result.originalIntent?.getBooleanExtra(Intents.Scan.MISSING_CAMERA_PERMISSION, false) == true -> error = QR_NO_CAMERA_PERMISSION
            // Otherwise the user cancelled: nothing to say.
        }
    }

    // Like «Limpiar foto»: the picker does not keep the vault open (this screen does not need it).
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        view = null // The previous result goes before the next image is read.
        error = null
        busy = true
        scope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) { readPhoto(context, uri) }
                ensureActive()
                val text = withContext(Dispatchers.Default) {
                    try {
                        decodeQrFromImage(bytes)
                    } finally {
                        bytes.fill(0) // The picture of a 2FA QR is that key too: it goes as soon as it is read.
                    }
                }
                ensureActive()
                if (text == null) error = QR_NOT_FOUND else show(text)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = imageOpenFailureText(e, QR_IMAGE_TOO_BIG, QR_IMAGE_TOO_LARGE) { QR_IMAGE_DAMAGED }
            } catch (e: OutOfMemoryError) {
                // Up to 50 MB read: on a small heap this is the error to show (M-7), not a crash.
                error = imageOpenFailureText(e, QR_IMAGE_TOO_BIG, QR_IMAGE_TOO_LARGE) { QR_IMAGE_DAMAGED }
            } finally {
                busy = false
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(QR_TOOL_TITLE) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás") } },
            )
        },
    ) { padding ->
        NoCopy {
            Column(
                Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(QR_EXPLANATION, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FilledTonalButton(
                    enabled = !busy,
                    onClick = {
                        VaultLock.keepOpenOnce = true
                        store.touch() // The time away is counted from here (AWAY_BACKSTOP_MS with «Al salir»).
                        try {
                            scanner.launch(scanQrOptions())
                        } catch (e: Exception) {
                            VaultLock.keepOpenOnce = false // The app never left: leaving later must lock.
                            error = QR_CAMERA_FAILED
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(QR_SCAN) }
                OutlinedButton(
                    enabled = !busy,
                    onClick = { pick.launchOrError(arrayOf("image/*"))?.let { error = it } },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(QR_PICK_IMAGE) }
                if (busy) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(QR_READING_IMAGE, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                error?.let { Text(it, color = NegativeColor) }
                view?.let { v ->
                    QrResult(v, onCopied = { notice -> scope.launch { snackbar.showSnackbar(notice) } }, onAction = {
                        when (v.action) {
                            QrAction.ADD_CONTACT -> { QrHandoff.put(QrTarget.CARD, v.text); onAddContact() }
                            QrAction.OPEN_RECEIVED -> { QrHandoff.put(QrTarget.RECEIVED, v.text); onOpenReceived() }
                            QrAction.ADD_TOTP -> { QrHandoff.put(QrTarget.OTP, v.text); onAddTotp() }
                            QrAction.NONE -> Unit
                        }
                    })
                }
            }
        }
    }
}

/** One QR's summary, signals, full text and actions. All of it plain [Text]: nothing is a link, nothing is selectable. */
@Composable
private fun QrResult(v: QrView, onCopied: (String) -> Unit, onAction: () -> Unit) {
    val context = LocalContext.current
    // A new QR starts hidden again.
    var revealed by remember(v) { mutableStateOf(false) }

    SectionCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(v.title, style = MaterialTheme.typography.titleMedium)
            // The name the browser connects to (ASCII), split into its labels: what it ends in is what counts. Its Unicode
            // reading, the one a look-alike name imitates with, goes below and smaller, never as the highlighted one.
            if (v.hostLabels.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(QR_FIELD_WEB, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        v.hostLabels.joinToString("  ·  "),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    v.hostReading?.let {
                        Text(qrHostReading(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            v.fields.forEach { f ->
                Column {
                    Text(f.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(f.value)
                }
            }
            v.secret?.let { secret ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(QR_FIELD_PASSWORD, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    // Hidden, always the same eight dots: not even its length is shown.
                    Text(if (revealed) visibleText(secret) else "••••••••", fontFamily = FontFamily.Monospace)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { revealed = !revealed }) { Text(if (revealed) QR_HIDE else QR_SHOW) }
                        FilledTonalButton(onClick = {
                            // Waiting for the A14 notice: nothing copied yet, so no snackbar about the copy.
                            if (copySensitive(context, secret)) v.secretCopyNotice?.let(onCopied)
                        }) { Text(QR_COPY_PASSWORD) }
                    }
                }
            }
        }
    }

    // Never a ✓, a green or a «limpio»: no signal is not «safe», and the text says so.
    if (v.alerts.isEmpty()) {
        Text(QR_NO_ALERTS, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(qrAlertsTitle(v.alerts.size.toString()), style = MaterialTheme.typography.titleSmall, color = CautionColor)
            v.alerts.forEach { Text("• $it") }
        }
    }
    v.notes.forEach { Text(it, color = CautionColor) }

    SectionCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(QR_FULL_TEXT, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (v.textHidden && !revealed) {
                Text(if (v.secret != null) QR_TEXT_HIDDEN_PASSWORD else QR_TEXT_HIDDEN_OTP, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(v.shownText, fontFamily = FontFamily.Monospace)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Copied as it is, hidden characters included (never stripped): when there are any, the snackbar says so.
                FilledTonalButton(onClick = {
                    if (copySensitive(context, v.text)) v.copyNotice?.let(onCopied)
                }) { Text(QR_COPY) }
                if (v.textHidden) OutlinedButton(onClick = { revealed = !revealed }) { Text(if (revealed) QR_HIDE else QR_SHOW) }
            }
        }
    }

    when (v.action) {
        QrAction.ADD_CONTACT -> Button(onClick = onAction, modifier = Modifier.fillMaxWidth()) { Text(QR_ADD_CONTACT) }
        QrAction.OPEN_RECEIVED -> Button(onClick = onAction, modifier = Modifier.fillMaxWidth()) { Text(QR_OPEN_RECEIVED) }
        QrAction.ADD_TOTP -> Button(onClick = onAction, modifier = Modifier.fillMaxWidth()) { Text(QR_ADD_TOTP) }
        QrAction.NONE -> Unit
    }
}

/**
 * «Añadir a la bóveda» from the inspector (`tools/qr/totp`): the vault's own 2FA form and Google Authenticator import
 * ([VaultEntryEditScreen] with the scanned text), behind the vault gate. The text is taken from [QrHandoff] only once
 * the vault is open; if it locks afterwards, back to the inspector, as the form does on its own.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrTotpImportScreen(store: VaultStore, onDone: () -> Unit) {
    val state by store.state.collectAsStateWithLifecycle()
    // The Bóveda tab loads it too; this screen can be reached first.
    LaunchedEffect(store) { store.load() }
    // Left at the gate without opening the vault (back, another tab): the 2FA key it was handed goes with the screen,
    // instead of waiting in memory for the next lock. Once taken, there is nothing left to clear.
    DisposableEffect(Unit) { onDispose { QrHandoff.clear() } }
    var wasOpen by remember { mutableStateOf(false) }
    if (state is VaultState.Unlocked) {
        SideEffect { wasOpen = true }
        val text = remember { QrHandoff.take(QrTarget.OTP) }
        VaultEntryEditScreen(store, id = null, initialType = EntryType.TOTP, onDone = onDone, otpAuthText = text)
        return
    }
    if (wasOpen) {
        LaunchedEffect(Unit) { onDone() }
        return
    }
    val snackbar = remember { SnackbarHostState() }
    val actions = vaultActions(store, snackbar)
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(QR_ADD_TOTP) },
                navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás") } },
            )
        },
    ) { padding ->
        VaultGate(store, onImport = actions.import, modifier = Modifier.padding(padding)) { }
    }
}
