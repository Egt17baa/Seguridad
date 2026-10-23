package app.seguridad.android

import android.os.Bundle
import com.journeyapps.barcodescanner.CaptureActivity
import com.journeyapps.barcodescanner.ScanOptions

/**
 * The library's scanner screen, with the same protection as the rest of the app: a 2FA QR holds a secret, so no
 * screenshots and no preview in Recents. Must run before super.onCreate, which inflates the content.
 */
class SecureCaptureActivity : CaptureActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        secureWindow()
        super.onCreate(savedInstanceState)
        window.decorView.filterTouchesWhenObscured = true
        findViewById<android.view.View>(android.R.id.content)?.filterTouchesWhenObscured = true
    }
}

fun scanQrOptions(): ScanOptions = ScanOptions()
    .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
    .setBeepEnabled(false)
    .setOrientationLocked(true)
    .setCaptureActivity(SecureCaptureActivity::class.java)
    .setPrompt("Apunta al código QR")
