package app.seguridad.ui.screens

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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.seguridad.imports.plainReportText
import app.seguridad.tools.qr.WIFI_QR_FAILED
import app.seguridad.tools.qr.WIFI_QR_HIDE
import app.seguridad.tools.qr.WIFI_QR_IMAGE
import app.seguridad.tools.qr.WIFI_QR_OPEN_WITH_PASSWORD
import app.seguridad.tools.qr.WIFI_QR_SHOW
import app.seguridad.tools.qr.WIFI_QR_TITLE
import app.seguridad.tools.qr.WIFI_QR_TOO_BIG
import app.seguridad.tools.qr.WIFI_QR_WARNING
import app.seguridad.tools.qr.WIFI_QR_WARNING_OPEN
import app.seguridad.tools.qr.WifiConfig
import app.seguridad.tools.qr.WifiQr
import app.seguridad.tools.qr.wifiConfigOf
import app.seguridad.ui.theme.BackScaffold
import app.seguridad.ui.theme.NegativeColor
import app.seguridad.vault.EntryType
import app.seguridad.vault.VaultState
import app.seguridad.vault.VaultStore
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What drawing a network's code gives: its modules, or the text of why not. */
internal sealed interface WifiQrDrawing {
    class Ok(val matrix: BitMatrix) : WifiQrDrawing
    data class Problem(val text: String) : WifiQrDrawing
}

/**
 * The QR of [c] with ZXing (level M, a margin of 4 modules, UTF-8), as [OtpQrScreen] draws the 2FA one. Never throws: an
 * empty name, a security the code cannot carry and a text over the reader's bound are [WifiQr.problem]'s texts; a text
 * that passes that but is more than a QR code holds makes ZXing throw `WriterException` («Data too big», from the
 * encoder's choice of version), which is [WIFI_QR_TOO_BIG]; anything else, [WIFI_QR_FAILED].
 */
internal fun wifiQrDrawing(c: WifiConfig): WifiQrDrawing {
    WifiQr.problem(c)?.let { return WifiQrDrawing.Problem(it) }
    return try {
        val hints = mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 4, EncodeHintType.CHARACTER_SET to "UTF-8")
        WifiQrDrawing.Ok(QRCodeWriter().encode(WifiQr.encode(c), BarcodeFormat.QR_CODE, 0, 0, hints))
    } catch (e: WriterException) {
        WifiQrDrawing.Problem(WIFI_QR_TOO_BIG)
    } catch (e: Exception) {
        WifiQrDrawing.Problem(WIFI_QR_FAILED)
    }
}

/** One pixel per module (the Image scales it up without smoothing); only in memory, never written anywhere. */
private fun BitMatrix.toImage(): ImageBitmap {
    val pixels = IntArray(width * height) { i -> if (get(i % width, i / width)) android.graphics.Color.BLACK else android.graphics.Color.WHITE }
    return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888).asImageBitmap()
}

/**
 * What the screen says about the network: its name (shown through `plainReportText`), whether it is open, whether a
 * password kept with an open network is left out of the code, and why no code can be drawn ([WifiQr.problem]).
 */
private data class WifiQrShown(val ssid: String, val open: Boolean, val passwordLeftOut: Boolean, val problem: String?) {
    override fun toString() = "WifiQrShown"
}

/**
 * Holds what the wifi QR screen shows for the navigation entry `vault/entry/{id}/wifi-qr` (the id only in the route):
 * the network read from the open vault and, after «Mostrar QR», the image. Never in saved state. It follows the vault
 * for its whole life, not tied to the screen being on view, so a lock in the background drops the image and closes the
 * screen at once; so does the entry going (to the trash, or not a wifi any more). A change of the network drops the
 * image, so a code drawn before never stays for a network that changed.
 */
private class WifiQrViewModel(private val store: VaultStore, private val id: String) : ViewModel() {
    val shown = ComposeCells.cell<WifiQrShown?>(null)
    val drawn = ComposeCells.cell<ImageBitmap?>(null)

    /** Why the code could not be drawn after «Mostrar QR» (only ZXing's own refusal is known that late). */
    val problem = ComposeCells.cell<String?>(null)
    val closed = ComposeCells.cell(false)

    private var config: WifiConfig? = null
    private var drawing: Job? = null

    /** Bumped by everything that drops the image: a drawing that ends after it is not shown. */
    private var turn = 0

    init {
        viewModelScope.launch { store.state.collect { follow(it) } }
    }

    private fun follow(state: VaultState) {
        val entry = (state as? VaultState.Unlocked)?.entries?.firstOrNull { it.id == id && it.type == EntryType.WIFI }
        if (entry == null) {
            close()
            return
        }
        val c = wifiConfigOf(entry)
        if (c == config) return
        hide()
        config = c
        val open = c.security == "nopass"
        shown.value = WifiQrShown(c.ssid, open, open && c.password.isNotEmpty(), WifiQr.problem(c))
    }

    /** «Mostrar QR»: drawn off the main thread, and shown only if nothing dropped it meanwhile. */
    fun show() {
        val c = config ?: return
        if (closed.value || drawn.value != null) return
        drawing?.cancel()
        val mine = ++turn
        drawing = viewModelScope.launch {
            try {
                val image = withContext(Dispatchers.Default) {
                    when (val d = wifiQrDrawing(c)) {
                        is WifiQrDrawing.Ok -> d.matrix.toImage() to null
                        is WifiQrDrawing.Problem -> null to d.text
                    }
                }
                if (mine != turn || closed.value) return@launch
                drawn.value = image.first
                problem.value = image.second
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // Out of memory making the image, at worst: the text, not a crash.
                if (mine == turn && !closed.value) problem.value = WIFI_QR_FAILED
            }
        }
    }

    /** «Ocultar QR», the app stopping, or the network changing: the image goes (not recycled by hand, as the 2FA one). */
    fun hide() {
        turn++
        drawing?.cancel()
        drawing = null
        drawn.value = null
        problem.value = null
    }

    private fun close() {
        hide()
        config = null
        shown.value = null
        closed.value = true
    }

    override fun onCleared() {
        turn++
        drawn.value = null
        shown.value = null
        config = null
    }
}

/**
 * 2.0, task 9 (design «QR de un wifi»): «Enseñar QR» on a wifi entry. The network's name, the warning on view (O-4: who
 * sees the code sees the password) and, after a tap, the code `WIFI:…` to join it from another phone. It holds the
 * password, so it is drawn only after «Mostrar QR», dropped on «Ocultar QR», when the app stops, when the network changes
 * and when the vault locks, which also closes the screen ([WifiQrViewModel]). The whole window is FLAG_SECURE, so it never
 * shows in screenshots or the recent apps. Nothing here joins a network or starts another app.
 */
@Composable
fun WifiQrScreen(store: VaultStore, id: String, onBack: () -> Unit) {
    VaultIdleLock(store)
    val vm = viewModel { WifiQrViewModel(store, id) }
    if (vm.closed.value) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.hide() }
    val shown = vm.shown.value ?: return
    BackScaffold(WIFI_QR_TITLE, onBack) { padding ->
        Column(
            Modifier.padding(padding).touchesVault(store).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(plainReportText(shown.ssid), style = MaterialTheme.typography.titleMedium)
            val cannot = shown.problem
            if (cannot != null) {
                Text(cannot, color = NegativeColor)
                return@Column
            }
            // O-4: on view, never folded; an open network has no password to see.
            Text(if (shown.open) WIFI_QR_WARNING_OPEN else WIFI_QR_WARNING, color = NegativeColor)
            if (shown.passwordLeftOut) {
                Text(WIFI_QR_OPEN_WITH_PASSWORD, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val image = vm.drawn.value
            val failed = vm.problem.value
            when {
                failed != null -> Text(failed, color = NegativeColor)
                image != null -> {
                    // White quiet zone whatever the theme: scanners need dark modules on a light background.
                    Box(Modifier.fillMaxWidth().aspectRatio(1f).background(Color.White)) {
                        Image(image, contentDescription = WIFI_QR_IMAGE, filterQuality = FilterQuality.None, modifier = Modifier.fillMaxWidth().aspectRatio(1f))
                    }
                    OutlinedButton(onClick = vm::hide) { Text(WIFI_QR_HIDE) }
                }
                else -> Button(onClick = vm::show) { Text(WIFI_QR_SHOW) }
            }
        }
    }
}
