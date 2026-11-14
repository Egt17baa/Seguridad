package app.seguridad.ui.screens

import app.seguridad.tools.qr.WIFI_QR_NO_NAME
import app.seguridad.tools.qr.WIFI_QR_NO_SECURITY
import app.seguridad.tools.qr.WIFI_QR_TOO_BIG
import app.seguridad.tools.qr.WifiConfig
import app.seguridad.tools.qr.WifiQr
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2.0, task 9: drawing the wifi QR with ZXing (the library already in the APK). An empty name, a security the code
 * cannot carry and a text too large for a QR code each give their Spanish text, never a crash: the too-large one is
 * caught where ZXing throws it (`WriterException`, «Data too big», from the encoder's choice of version), and before it
 * by [WifiQr.problem] for anything over the bound of the reader. Here, on the JVM, because jvm-check has no ZXing.
 */
class WifiQrDrawingTest {
    private fun wpa(ssid: String, password: String) = WifiConfig(ssid, password, "WPA", false)

    @Test fun aNetworkIsDrawnAndReadsBackAsItsText() {
        val c = wpa("Casa;1", "clave\"2")
        val d = wifiQrDrawing(c) as WifiQrDrawing.Ok
        val m = d.matrix
        assertTrue(m.width > 20 && m.width == m.height)
        // Read back with ZXing's own reader: the code carries exactly the encoded text.
        val pixels = IntArray(m.width * m.height) { i -> if (m.get(i % m.width, i / m.width)) 0xFF000000.toInt() else -1 }
        val read = QRCodeReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(m.width, m.height, pixels))))
        assertEquals(BarcodeFormat.QR_CODE, read.barcodeFormat)
        assertEquals(WifiQr.encode(c), read.text)
        assertEquals(c, WifiQr.parse(read.text))
    }

    @Test fun anEmptyNameOrAnUnknownSecurityIsAText() {
        assertEquals(WifiQrDrawing.Problem(WIFI_QR_NO_NAME), wifiQrDrawing(wpa("", "clave")))
        assertEquals(WifiQrDrawing.Problem(WIFI_QR_NO_SECURITY), wifiQrDrawing(WifiConfig("Casa", "clave", "WPA;S:otra", false)))
    }

    @Test fun tooLargeForAQrCodeIsATextWhereZxingThrows() {
        // Over the reader's bound: refused before ZXing (a field of the vault may hold 10 000 code points).
        assertEquals(WifiQrDrawing.Problem(WIFI_QR_TOO_BIG), wifiQrDrawing(wpa("Casa", "😀".repeat(10_000))))
        // Under that bound but over what a QR code holds at level M (2 331 bytes in byte mode, less the UTF-8 marker):
        // ZXing throws WriterException, caught here.
        assertTrue(WifiQr.problem(wpa("Casa", "x".repeat(3_000))) == null)
        assertEquals(WifiQrDrawing.Problem(WIFI_QR_TOO_BIG), wifiQrDrawing(wpa("Casa", "x".repeat(3_000))))
        // The limit, found once: the largest password that still fits with this name, and one more character does not.
        fun drawn(n: Int) = wifiQrDrawing(wpa("Casa", "x".repeat(n))) is WifiQrDrawing.Ok
        var lo = 1 // Drawn.
        var hi = 3_000 // Not drawn.
        assertTrue(drawn(lo) && !drawn(hi))
        while (hi - lo > 1) {
            val mid = (lo + hi) / 2
            if (drawn(mid)) lo = mid else hi = mid
        }
        val fits = lo
        assertEquals(WIFI_QR_LARGEST_TEST_PASSWORD, fits)
        assertEquals(WifiQrDrawing.Problem(WIFI_QR_TOO_BIG), wifiQrDrawing(wpa("Casa", "x".repeat(fits + 1))))
    }

    private companion object {
        /**
         * With the name «Casa» and WPA: `WIFI:T:WPA;S:Casa;P:` (20) + the password + `;H:false;;` (10). ZXing at level M
         * with the UTF-8 marker (ECI) holds 2 329 bytes, so 2 299 characters of «x».
         */
        const val WIFI_QR_LARGEST_TEST_PASSWORD = 2_299
    }
}
