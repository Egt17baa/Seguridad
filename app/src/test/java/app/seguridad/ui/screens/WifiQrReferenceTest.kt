package app.seguridad.ui.screens

import app.seguridad.tools.qr.WifiConfig
import app.seguridad.tools.qr.WifiQr
import app.seguridad.tools.qr.WifiRead
import com.google.zxing.BarcodeFormat
import com.google.zxing.Result
import com.google.zxing.client.result.WifiParsedResult
import com.google.zxing.client.result.WifiResultParser
import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * 2.0, task 9, ronda 1 (I-2): what Clavero shows of a wifi code against what another reader reads in it. The reference is
 * the wifi reader of ZXing 3.4.1, the library already in the APK (`WifiResultParser`, used here only as a test oracle, on
 * the JVM: jvm-check has no ZXing). Whenever Clavero says «wifi» (usable or only shown), ZXing gives the same name,
 * password, type and «hidden», except for the documented rule of the quotes: ZXing keeps the quotes around a value that
 * Clavero reads as text without them.
 */
class WifiQrReferenceTest {
    private fun zxing(text: String): WifiParsedResult? = WifiResultParser().parse(Result(text, null, null, BarcodeFormat.QR_CODE))

    /** The four values Clavero shows, or null when it does not say «wifi» ([WifiRead.Malformed], or not a wifi code). */
    private fun clavero(text: String): WifiConfig? = when (val r = WifiQr.read(text)) {
        is WifiRead.Usable -> r.config
        is WifiRead.Unsupported -> WifiConfig(r.ssid, r.password, r.security, r.hidden)
        else -> null
    }

    /** ZXing's value against Clavero's: the same, or the same between the quotes Clavero takes off. */
    private fun same(theirs: String?, ours: String): Boolean = theirs.orEmpty() == ours || theirs == "\"$ours\""

    /** The type as ZXing gives it, as Clavero names it: any case, «WPA2» as «WPA» (owner's decision), none as nopass. */
    private fun sameType(theirs: String?, ours: String): Boolean {
        val t = theirs ?: "nopass"
        return t.equals(ours, ignoreCase = true) || (ours == "WPA" && t.equals("WPA2", ignoreCase = true))
    }

    private fun assertAgrees(text: String) {
        val ours = clavero(text) ?: return
        val theirs = zxing(text)
        assertNotNull("ZXing does not read «$text» as wifi, Clavero does", theirs)
        theirs!!
        val ok = same(theirs.ssid, ours.ssid) && sameType(theirs.networkEncryption, ours.security) &&
            theirs.isHidden == ours.hidden && same(theirs.password, ours.password)
        if (!ok) fail("«$text»: Clavero ${ours.ssid}/${ours.password}/${ours.security}/${ours.hidden}, ZXing ${theirs.ssid}/${theirs.password}/${theirs.networkEncryption}/${theirs.isHidden}")
    }

    @Test fun theReviewersTextsAreReadDifferentlyByZxingSoClaveroDoesNotSayWifi() {
        // What ZXing reads in them: a password, another network, another password.
        assertEquals("secreta", zxing("WIFI:T:WPA;S:Casa;XP:secreta;;")!!.password)
        assertEquals("otra", zxing("WIFI:T:WPA;PS:otra;S:Casa;P:clave;;")!!.ssid)
        assertEquals("mala", zxing("WIFI:T:WPA;S:xP:mala;P:buena;;")!!.password)
        // Clavero says it cannot read them safely: nothing shown as a network, nothing offered (and the text is a secret).
        for (t in listOf("WIFI:T:WPA;S:Casa;XP:secreta;;", "WIFI:T:WPA;PS:otra;S:Casa;P:clave;;", "WIFI:T:WPA;S:xP:mala;P:buena;;")) {
            assertEquals(t, WifiRead.Malformed, WifiQr.read(t))
        }
    }

    @Test fun theCaseIsReadAsZxingReadsIt() {
        // ZXing reads only «WIFI:» and the keys in capitals: another case is not read safely by Clavero either.
        assertEquals(null, zxing("wifi:T:WPA;S:Casa;P:clave;;"))
        assertEquals(WifiRead.Malformed, WifiQr.read("wifi:T:WPA;S:Casa;P:clave;;"))
        assertEquals(null, zxing("WIFI:s:Casa;;"))
        assertEquals(WifiRead.Malformed, WifiQr.read("WIFI:s:Casa;;"))
        // The value of «T:» and «H:» in any case: ZXing keeps the type as written and reads «H:» as a boolean in any case.
        assertAgrees("WIFI:T:wpa;S:Casa;P:clave;H:TRUE;;")
        assertEquals(WifiConfig("Casa", "clave", "WPA", true), WifiQr.parse("WIFI:T:wpa;S:Casa;P:clave;H:TRUE;;"))
    }

    /**
     * Every body of length 0 to 5 over `\ ; , : " a`, a space, `S`, `P`, `T` and `H` after «WIFI:» (177 156 texts), and
     * 200 000 longer ones at random from the same alphabet and from whole pieces («S:», «T:WPA», «;;»…).
     */
    @Test fun wheneverClaveroSaysWifiZxingReadsTheSame() {
        val alphabet = listOf('\\', ';', ',', ':', '"', 'a', ' ', 'S', 'P', 'T', 'H')
        var level = listOf("")
        var checked = 0
        var wifi = 0
        repeat(6) { n ->
            for (body in level) {
                val t = "WIFI:$body"
                assertAgrees(t)
                if (clavero(t) != null) wifi++
                checked++
            }
            if (n < 5) level = level.flatMap { s -> alphabet.map { s + it } }
        }
        assertEquals(177_156, checked)
        val pieces = listOf("S:", "P:", "T:", "H:", "T:WPA", "T:WEP", "T:nopass", "T:WPA2", "T:SAE", "H:true", "H:false", "E:TTLS", "PH2:x", ";", ";;", ":", "\\", "\"", ",", "a", " ", "S", "P", "T", "H", "X")
        val rnd = Random(20261114L)
        repeat(200_000) {
            val sb = StringBuilder("WIFI:")
            val whole = rnd.nextBoolean()
            repeat(6 + rnd.nextInt(11)) { if (whole) sb.append(pieces[rnd.nextInt(pieces.size)]) else sb.append(alphabet[rnd.nextInt(alphabet.size)]) }
            val t = sb.toString()
            assertAgrees(t)
            if (clavero(t) != null) wifi++
        }
        // Not empty by accident: many of them are read as wifi.
        assertTrue("only $wifi read as wifi", wifi > 5_000)
    }

    /** Everything Clavero writes, ZXing reads the same (quotes aside). Over the alphabet of the round trip of [WifiQr]. */
    @Test fun whatClaveroWritesZxingReadsTheSame() {
        val alphabet = listOf("\\", ";", ",", ":", "\"", "a", " ", "P", "S", "T", "H")
        var level = listOf("")
        val all = ArrayList<String>(level)
        repeat(2) {
            level = level.flatMap { s -> alphabet.map { s + it } }
            all += level
        }
        val raw = "0123456789abcdef".repeat(4)
        val extra = listOf("Casa", "CAFE", "12345678", raw, raw.take(26), "\"foo;bar\\baz\"", "a b", "x;S:y", "x\\")
        var written = 0
        for (ssid in all + extra) {
            if (ssid.isEmpty()) continue
            for (password in all + extra) {
                for (security in listOf("WPA", "WEP", "nopass")) {
                    val c = WifiConfig(ssid, password, security, hidden = (written % 3 == 0))
                    if (WifiQr.problem(c) != null) continue
                    val t = WifiQr.encode(c)
                    val theirs = zxing(t)
                    assertNotNull(t, theirs)
                    theirs!!
                    assertTrue(t, same(theirs.ssid, c.ssid))
                    assertTrue(t, sameType(theirs.networkEncryption, c.security))
                    assertEquals(t, c.hidden, theirs.isHidden)
                    if (security == "nopass") assertTrue(t, theirs.password == null) else assertTrue(t, same(theirs.password, c.password))
                    written++
                }
            }
        }
        assertTrue(written > 40_000)
    }
}
