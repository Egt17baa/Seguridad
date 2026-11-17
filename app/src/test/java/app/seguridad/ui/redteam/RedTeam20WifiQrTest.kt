package app.seguridad.ui.redteam

import app.seguridad.imports.plainReportText
import app.seguridad.tools.qr.WIFI_QR_IMAGE
import app.seguridad.tools.qr.WifiConfig
import app.seguridad.tools.qr.WifiQr
import app.seguridad.tools.qr.WifiRead
import app.seguridad.tools.qr.qrView
import app.seguridad.tools.qr.wifiQrLines
import com.google.zxing.BarcodeFormat
import com.google.zxing.Result
import com.google.zxing.client.result.WifiParsedResult
import com.google.zxing.client.result.WifiResultParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/*
 * Final attack of Clavero 2.0 (R32), phase 2, surface 5 (row 24): what Clavero shows of a wifi code against what another
 * reader joins. The oracle is ZXing's own wifi reader (ZXing is in the APK; here only as a test oracle, so this file lives
 * in ui/, which only :app:testDebugUnitTest compiles) and the rules of the format's public description, written here.
 * Report: docs/superpowers/handoff/sdd-2.0/redteam-report.md.
 */
class RedTeam20WifiQrTest {
    private fun zxing(text: String): WifiParsedResult? = WifiResultParser().parse(Result(text, null, null, BarcodeFormat.QR_CODE))

    private fun source(path: String): String =
        listOf("src/main/java/app/seguridad/$path", "app/src/main/java/app/seguridad/$path").map(::File).first { it.exists() }.readText()

    /**
     * RT-24 (row 24, direction (b), the evil twin from the vault). «Enseñar QR» writes the name as it is kept
     * ([WifiQr.encode], tools/qr/WifiQr.kt:167-173), but the screen shows it through `plainReportText`
     * (ui/screens/WifiQrScreen.kt:209), which takes out what draws nothing: a zero-width space, a soft hyphen, a direction
     * mark, a control. A wifi entry named «Casa» followed by U+200B (from the inspector, where the code came with it; a
     * copy; the form) shows «Casa» above the code, and the lines under it say nothing (`wifiQrLines`, :341-345), while
     * every reader of the code joins the network «Casa\u200B»: another network, which whoever made the code can set up.
     * The inspector does show such marks ([app.seguridad.tools.qr.visibleText], with an alert); this screen, where the
     * owner hands the code to someone, does not.
     *
     * The safe behaviour asserted: for a name with characters that draw nothing, what the screen shows tells it from the
     * plain name: the name is not shown through `plainReportText` alone, or the lines under it say so.
     */
    @Test fun rt24_theQrScreenNeverShowsAnotherNetworkThanTheCodeJoins() {
        val screen = source("ui/screens/WifiQrScreen.kt")
        assertTrue("the screen shows the name", screen.contains("shown.ssid"))
        val namePlain = screen.contains("Text(plainReportText(shown.ssid)")
        val hiddenTwins = ArrayList<String>()
        for (twin in listOf("Casa\u200B", "Ca\u00ADsa", "Casa\u202E", "\u2066Casa", "Casa\u0000")) {
            val c = WifiConfig(twin, "clave-larga-1", "WPA", hidden = false)
            val joined = zxing(WifiQr.encode(c))!!.ssid
            assertEquals("Another reader joins the name as kept", twin, joined)
            assertEquals("plainReportText shows «Casa» (a control as a space)", "Casa", plainReportText(twin).trim())
            val linesTell = wifiQrLines(c) != wifiQrLines(c.copy(ssid = "Casa"))
            if (namePlain && !linesTell) hiddenTwins += twin.map { "U+%04X".format(it.code) }.joinToString(" ")
        }
        assertTrue("The screen shows «Casa» and says nothing, while the code joins another network: $hiddenTwins", hiddenTwins.isEmpty())
    }

    /**
     * Row 24, direction (a), the evil twin from a code: the cases the attack names (keys twice, a `\` at the end, `;` and
     * `:` escaped, quotes, `wifi:` in small letters, a byte order mark and a NUL before it, the last field without `;`,
     * invisibles and direction marks in the name, `S:` hidden in another field). Each one is either not read at all
     * ([WifiRead.Malformed]: nothing shown as a network, nothing offered) or read exactly as ZXing reads it (its quotes
     * aside), and then the inspector shows the name with its marks and an alert (tools/qr/QrView.kt:114-117, :138).
     */
    @Test fun resistsTheEvilTwinCodes() {
        val cases = listOf(
            "WIFI:S:Casa;S:Otra;T:WPA;P:clave;;", "WIFI:S:Casa;T:WPA;P:clave;P:otra;;", "WIFI:S:Casa\\;T:WPA;P:clave;;",
            "WIFI:S:Casa;T:WPA;P:clave\\", "WIFI:S:Ca\\:sa;T:WPA;P:cl\\;ave;;", "WIFI:S:\"Casa\";T:WPA;P:\"clave\";;",
            "WIFI:S:\\\"Casa\\\";T:WPA;P:clave;;", "wifi:S:Casa;T:WPA;P:clave;;", "\uFEFFWIFI:S:Casa;T:WPA;P:clave;;",
            "\u0000WIFI:S:Casa;T:WPA;P:clave;;", "WIFI:S:Casa;T:WPA;P:clave", "WIFI:S:Casa\u200B;T:WPA;P:clave;;",
            "WIFI:S:\u202ECasa;T:WPA;P:clave;;", "WIFI:S:Casa;T:WPA;X:S\\:Otra;P:clave;;", "WIFI:S:Casa;T:WPA;XS:Otra;P:clave;;",
            "WIFI:T:WPA;S:Casa;P:clave;H:true;;S:Otra;;", "WIFI:S:Casa\u0000Otra;T:WPA;P:clave;;",
        )
        var read = 0
        for (t in cases) {
            val ours = WifiQr.read(t) ?: continue
            if (ours == WifiRead.Malformed) continue
            read++
            val (ssid, password) = when (ours) {
                is WifiRead.Usable -> ours.config.ssid to ours.config.password
                is WifiRead.Unsupported -> ours.ssid to ours.password
                else -> error("unreachable")
            }
            val theirs = assertNotNullAndGet(zxing(t), t)
            assertTrue("«$t»: name ${theirs.ssid} vs $ssid", theirs.ssid == ssid || theirs.ssid == "\"$ssid\"")
            assertTrue("«$t»: password", theirs.password.orEmpty() == password || theirs.password == "\"$password\"")
            // What draws nothing in the name is shown with its marks, and said.
            if (plainReportText(ssid) != ssid) {
                val v = qrView(t)
                assertFalse("«$t»: an alert", v.alerts.isEmpty())
                assertTrue("«$t»: the name with its marks", v.fields.first().value != plainReportText(ssid))
            }
        }
        assertTrue("some are read", read >= 3)
        // The image's description never carries the password.
        assertFalse(WIFI_QR_IMAGE.contains("clave"))
    }

    private fun assertNotNullAndGet(r: WifiParsedResult?, t: String): WifiParsedResult {
        assertNotNull("ZXing does not read «$t»", r)
        return r!!
    }

    /**
     * Row 24, direction (b), the rest of a hostile entry (a copy can bring any text in the wifi's fields): names and
     * passwords with NUL, line breaks, a byte order mark, quotes and the format's own characters, and every one of the
     * three types: what Clavero writes, ZXing reads back the same (its quotes aside), so no other field or network can be
     * slipped in; a type that is not one of the three is never written (`problem`, tools/qr/WifiQr.kt:151-156).
     */
    @Test fun resistsHostileVaultValuesInTheCodeItWrites() {
        val values = listOf("a\u0000b", "a\nS:Otra", "\uFEFFCasa", "\"Casa\"", "Casa;;", "x\\", "S:Otra;P:mala", "CAFE", "0".repeat(64), "é\u202Eabc")
        for (ssid in values) for (pw in values) for (sec in listOf("WPA", "WEP", "nopass")) {
            val c = WifiConfig(ssid, pw, sec, hidden = ssid.length % 2 == 0)
            if (WifiQr.problem(c) != null) continue
            val t = WifiQr.encode(c)
            val z = assertNotNullAndGet(zxing(t), t)
            assertTrue(t, z.ssid == ssid || z.ssid == "\"$ssid\"")
            if (sec != "nopass") assertTrue(t, z.password == pw || z.password == "\"$pw\"")
            assertEquals(t, c.hidden, z.isHidden)
        }
        for (bad in listOf("WPA;P:x", "WPA3", "", "wpa")) {
            assertTrue(bad, WifiQr.problem(WifiConfig("Casa", "clave", bad, false)) != null)
        }
    }
}
