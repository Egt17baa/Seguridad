package app.seguridad.ui

import androidx.compose.foundation.text.contextmenu.data.ProcessTextKey
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuItem
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuKeys
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuSeparator
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import app.seguridad.ui.theme.NoCopyToolbar
import app.seguridad.ui.theme.keptInNoCopyMenu
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TECLADO-1 (audit 1.3.0): what [app.seguridad.ui.theme.NoCopy] leaves in the system text menu, on both of Compose's
 * paths. Compose types only, so this test runs in `:app:testDebugUnitTest` and not in jvm-check (which skips `ui/`).
 */
class NoCopyMenuTest {
    private fun item(key: Any) = TextContextMenuItem(key, "x", 0) { }

    /** The key Compose gives to each PROCESS_TEXT action (its constructor is internal to Compose). */
    private fun processText(id: Int): Any = ProcessTextKey::class.java.getConstructor(Int::class.javaPrimitiveType).newInstance(id)

    @Test fun `the new menu keeps only Pegar, Seleccionar todo and the separators`() {
        assertTrue(keptInNoCopyMenu(item(TextContextMenuKeys.PasteKey)))
        assertTrue(keptInNoCopyMenu(item(TextContextMenuKeys.SelectAllKey)))
        assertTrue(keptInNoCopyMenu(TextContextMenuSeparator))
    }

    @Test fun `the new menu drops Copiar, Cortar, Autocompletar, PROCESS_TEXT and anything unknown`() {
        assertFalse(keptInNoCopyMenu(item(TextContextMenuKeys.CopyKey)))
        assertFalse(keptInNoCopyMenu(item(TextContextMenuKeys.CutKey)))
        assertFalse(keptInNoCopyMenu(item(TextContextMenuKeys.AutofillKey)))
        // «Traducir» and the other apps' PROCESS_TEXT actions send the selected text to another app.
        assertFalse(keptInNoCopyMenu(item(processText(0))))
        assertFalse(keptInNoCopyMenu(item(processText(7))))
        // An allow-list: a key Compose adds in a later version (or a text classification item) is dropped too.
        assertFalse(keptInNoCopyMenu(item(Any())))
        assertFalse(keptInNoCopyMenu(item("Copy")))
    }

    /** A system TextToolbar that records what it is asked to show. */
    private class Recorder : TextToolbar {
        var copy: (() -> Unit)? = {}
        var paste: (() -> Unit)? = null
        var cut: (() -> Unit)? = {}
        var selectAll: (() -> Unit)? = null
        var autofill: (() -> Unit)? = {}
        var hidden = false
        override val status = TextToolbarStatus.Hidden
        override fun showMenu(rect: Rect, onCopyRequested: (() -> Unit)?, onPasteRequested: (() -> Unit)?, onCutRequested: (() -> Unit)?, onSelectAllRequested: (() -> Unit)?) {
            copy = onCopyRequested; paste = onPasteRequested; cut = onCutRequested; selectAll = onSelectAllRequested; autofill = null
        }
        override fun showMenu(rect: Rect, onCopyRequested: (() -> Unit)?, onPasteRequested: (() -> Unit)?, onCutRequested: (() -> Unit)?, onSelectAllRequested: (() -> Unit)?, onAutofillRequested: (() -> Unit)?) {
            copy = onCopyRequested; paste = onPasteRequested; cut = onCutRequested; selectAll = onSelectAllRequested; autofill = onAutofillRequested
        }
        override fun hide() { hidden = true }
    }

    @Test fun `the old menu (LocalTextToolbar) shows Pegar and Seleccionar todo only`() {
        val base = Recorder()
        val toolbar = NoCopyToolbar(base)
        val paste = {}
        val selectAll = {}
        toolbar.showMenu(Rect.Zero, {}, paste, {}, selectAll, {})
        assertNull(base.copy)
        assertNull(base.cut)
        assertNull("M1: no manual autofill on a protected field", base.autofill)
        assertNotNull(base.paste)
        assertNotNull(base.selectAll)

        val other = Recorder()
        NoCopyToolbar(other).showMenu(Rect.Zero, {}, paste, {}, selectAll)
        assertNull(other.copy)
        assertNull(other.cut)
        assertNotNull(other.paste)

        toolbar.hide()
        assertTrue(base.hidden)
        assertEquals(TextToolbarStatus.Hidden, toolbar.status)
    }
}
