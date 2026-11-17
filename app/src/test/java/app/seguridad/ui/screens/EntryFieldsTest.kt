package app.seguridad.ui.screens

import app.seguridad.vault.CustomField
import app.seguridad.vault.EntryType
import app.seguridad.vault.FieldKind
import app.seguridad.vault.VaultEntry
import app.seguridad.vault.WIFI_PASSWORD_KEY
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
}
