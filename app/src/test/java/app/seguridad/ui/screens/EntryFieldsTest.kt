package app.seguridad.ui.screens

import app.seguridad.vault.CustomField
import app.seguridad.vault.EntryType
import app.seguridad.vault.FieldKind
import app.seguridad.vault.VaultEntry
import app.seguridad.vault.WIFI_PASSWORD_KEY
import app.seguridad.vault.WIFI_SSID_KEY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Branch review, part C: the card of fields (I-2) and the history's copies (m-3). */
class EntryFieldsTest {
    private fun src(name: String) = generateSequence(File(".").absoluteFile) { it.parentFile }
        .map { File(it, "app/src/main/java/app/seguridad/ui/screens/$name") }.first { it.isFile }.readText()

    /** C I-2 (test n.º 2): no «Ver en grande» where nobody receives it (the versions of the history); it is there on the card. */
    @Test fun noBigButtonWithoutHandler() {
        val wifi = VaultEntry(
            "e", EntryType.WIFI, "T", createdAt = 0, updatedAt = 0,
            fields = listOf(CustomField(WIFI_PASSWORD_KEY, "Contraseña", FieldKind.HIDDEN, "wifi-clave")),
        )
        assertNull(bigActionFor(wifi, WIFI_PASSWORD_KEY, null))
        var asked: String? = null
        val action = bigActionFor(wifi, WIFI_PASSWORD_KEY) { asked = it }
        assertNotNull(action)
        action!!()
        assertEquals(WIFI_PASSWORD_KEY, asked)
        assertNull("not a secret", bigActionFor(wifi, "ssid") { })
        // The history passes no handler; the card of the entry does.
        val history = src("EntryHistoryScreen.kt")
        assertFalse(history, "onBig" in history)
        assertTrue("onBig = " in src("VaultEntryScreen.kt"))
    }

    /**
     * C m-3: any copy ends the offer of «Copiar contraseña», also in the history: each field there, its card and its old
     * passwords, ends it, and none copies with nothing after it.
     */
    @Test fun theHistorysCopiesEndTheOffer() {
        val history = src("EntryHistoryScreen.kt")
        assertFalse(history, "onCopied = {}" in history)
        assertTrue("twoStep: TwoStepCopy" in history)
        assertTrue("onCopied = twoStep::clear" in history)
        val fields = Regex("""\bField\(""").findAll(history).count()
        assertEquals(fields, Regex("""\bField\([^\n]*onCopied = onCopied\)""").findAll(history).count() + Regex("""\bField\([^\n]*onCopied = twoStep::clear\)""").findAll(history).count())
        assertTrue("EntryFieldsCard(e, onCopied = onCopied)" in history)
    }

    /**
     * Round 2 of the fixes of the attack, informative 2: on the card, the name of a wifi network «Casa» + U+200B shows
     * its mark, as the screen of its QR does, and «Copiar» copies it as it is, with the invisible character; every other
     * field and the titles stay with plainReportText (R9 and R10).
     */
    @Test fun theNameOfAWifiShowsItsMarksAndCopiesAsItIs() {
        val name = "Casa\u200B"
        val ssid = CustomField(WIFI_SSID_KEY, "Red", FieldKind.TEXT, name)
        // What the card hands to Field, which is what «Copiar» copies: the value as it is.
        assertEquals(name, shownValue(EntryType.WIFI, ssid))
        assertTrue(isWifiName(EntryType.WIFI, WIFI_SSID_KEY))
        assertFalse(isWifiName(EntryType.WIFI, WIFI_PASSWORD_KEY))
        assertFalse(isWifiName(EntryType.LOGIN, WIFI_SSID_KEY))
        assertEquals("Casa\u27E8U+200B\u27E9", fieldShownText(name, plain = true, marked = true))
        assertEquals("Casa", fieldShownText(name, plain = true, marked = false))
        assertEquals(name, fieldShownText(name, plain = false, marked = false))
        // The anchors: the row shows fieldShownText, «Copiar» copies the value, and the card marks only the network's name.
        val field = src("VaultEntryScreen.kt").let { it.substring(it.indexOf("internal fun Field("), it.indexOf("internal val historyDate")) }
        assertTrue(field, "Text(if (shown) fieldShownText(value, plain, marked) else" in field)
        assertTrue(field, "copySensitive(context, value) { onCopied() }" in field)
        assertEquals(1, Regex("""copySensitive\(""").findAll(field).count())
        val card = src("EntryFields.kt")
        assertEquals(1, Regex("""marked = isWifiName\(entry\.type, t\.key\)""").findAll(card).count())
        assertEquals("only the template's field of the network", 1, Regex("""marked = """).findAll(card).count())
    }
}
