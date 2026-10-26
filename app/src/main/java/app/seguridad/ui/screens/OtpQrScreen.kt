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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.seguridad.crypto.TotpSecret
import app.seguridad.crypto.toOtpAuthUri
import app.seguridad.ui.theme.BackScaffold
import app.seguridad.ui.theme.NegativeColor
import app.seguridad.vault.VaultState
import app.seguridad.vault.VaultStore
import androidx.compose.runtime.LaunchedEffect
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** One pixel per QR module (the Image scales it up without smoothing); only in memory, never written anywhere. */
private fun otpQrBitmap(s: TotpSecret): Bitmap? = try {
    val m = QRCodeWriter().encode(
        s.toOtpAuthUri(),
        BarcodeFormat.QR_CODE,
        0,
        0,
        mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 4, EncodeHintType.CHARACTER_SET to "UTF-8"),
    )
    val pixels = IntArray(m.width * m.height) { i -> if (m.get(i % m.width, i / m.width)) android.graphics.Color.BLACK else android.graphics.Color.WHITE }
    Bitmap.createBitmap(pixels, m.width, m.height, Bitmap.Config.ARGB_8888)
} catch (e: Exception) {
    null
}

/**
 * The otpauth:// QR of a 2FA code, to move it to another app. It holds the secret, so it is drawn only after a tap
 * below the warning, and dropped from the composition on «Ocultar», on leaving and when the vault locks (not
 * recycled by hand: a last frame could still draw it; the garbage collector frees it). The whole
 * window is already FLAG_SECURE, so it never shows in screenshots or the recent apps.
 */
@Composable
fun OtpQrScreen(store: VaultStore, id: String, onBack: () -> Unit) {
    VaultIdleLock(store)
    val state by store.state.collectAsStateWithLifecycle()
    val totp = (state as? VaultState.Unlocked)?.entries?.firstOrNull { it.id == id }?.totp
    if (totp == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    var shown by remember { mutableStateOf(false) }
    BackScaffold("QR para otra app", onBack) { padding ->
        Column(
            Modifier.padding(padding).touchesVault(store).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(listOf(totp.issuer, totp.account).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.titleMedium)
            Text("Este QR contiene el secreto: muéstralo solo para pasarlo a otra app", color = NegativeColor)
            if (shown) {
                val bitmap = remember(totp) { otpQrBitmap(totp) }
                if (bitmap == null) {
                    Text("No se pudo generar el QR", color = NegativeColor)
                } else {
                    // White quiet zone whatever the theme: scanners need dark modules on a light background.
                    Box(Modifier.fillMaxWidth().aspectRatio(1f).background(Color.White)) {
                        Image(bitmap.asImageBitmap(), contentDescription = "Código QR con el secreto", filterQuality = FilterQuality.None, modifier = Modifier.fillMaxWidth().aspectRatio(1f))
                    }
                }
                OutlinedButton(onClick = { shown = false }) { Text("Ocultar QR") }
            } else {
                Button(onClick = { shown = true }) { Text("Mostrar QR") }
            }
        }
    }
}
