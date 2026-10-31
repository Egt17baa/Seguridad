package app.seguridad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Android-only code that the JVM cannot run, checked in its source (audit 1.3.0, batch F1). The behaviour itself is in
 * the owner's phone test (README).
 */
class PlatformSourcesTest {
    private fun main(path: String): String {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val f = File(dir, "app/src/main/java/app/seguridad/$path")
            if (f.isFile) return f.readText()
            dir = dir.parentFile
        }
        error("$path not found")
    }

    /** Code only: comments say what it does, but they must not make the check pass. */
    private fun code(path: String) = main(path).lines().filterNot { it.trimStart().let { l -> l.startsWith("//") || l.startsWith("*") || l.startsWith("/*") } }.joinToString("\n")

    @Test fun `COV2-4 the window and every dialog are marked accessibility data sensitive on Android 14+`() {
        val src = code("android/SecureWindow.kt")
        val marks = Regex("""setAccessibilityDataSensitive\(View\.ACCESSIBILITY_DATA_SENSITIVE_YES\)""").findAll(src).count()
        assertTrue("secureWindow() and FilterObscuredTouches() must both set it", marks >= 2)
        assertTrue("guarded by API 34", "Build.VERSION_CODES.UPSIDE_DOWN_CAKE" in src)
    }

    @Test fun `COV2-3 MainActivity tells the vault when another app has the focus in multi-window`() {
        val src = code("MainActivity.kt")
        assertTrue("onTopResumedActivityChanged" in src)
        assertTrue("isInMultiWindowMode" in src)
        assertTrue("container.leftForAnotherApp()" in src)
        assertTrue("container.backFromAnotherApp()" in src)
    }

    @Test fun `COV2-3 M3 PIN y codigos hears the multi-window away signal for its whole entry`() {
        val src = code("ui/screens/CodesScreen.kt")
        assertTrue("session.otherAppFocused(" in src)
        assertTrue("session.otherAppLeft(" in src)
        assertTrue("awayInOtherApp" in code("ui/AppNavHost.kt"))
    }

    @Test fun `COV1-1 the text, file and leak tools follow the PIN y codigos rule, in a ViewModel`() {
        val src = code("ui/screens/SecurityToolsScreen.kt")
        // One ViewModel per tool, each watching the time away, the other app and the vault, and the screen's lifecycle.
        for (vm in listOf("TextCryptoViewModel", "FileCryptoViewModel", "BreachViewModel")) assertTrue(vm, "class $vm(" in src)
        assertEquals(3, Regex("""watchAway\(session\.guard, store, lockAfterMs, awayInOtherApp\)""").findAll(src).count())
        assertEquals(3, Regex("""GuardEffects\(s\.guard, lockAfterMs\)""").findAll(src).count())
        // Nothing of theirs in saved state but the decrypted copy's path (not a secret).
        assertEquals(1, Regex("""by rememberSaveable""").findAll(src).count())
        assertTrue("var pendingPath by rememberSaveable" in src)
        val wiring = code("ui/screens/ScreenGuardWiring.kt")
        assertTrue("guard.otherAppFocused(" in wiring && "guard.otherAppLeft(" in wiring && "guard.vaultStateIs(" in wiring)
        assertTrue("Lifecycle.Event.ON_STOP" in wiring && "Lifecycle.Event.ON_START" in wiring && "onDispose" in wiring)
        // The QR inspector hears the multi-window signal too (F1 open point).
        val qr = code("ui/screens/QrInspectScreen.kt")
        assertTrue("away.otherAppFocused(" in qr && "away.otherAppLeft(" in qr)
        assertTrue(Regex("""QrInspectScreen\([^)]*awayInOtherApp""").containsMatchIn(code("ui/AppNavHost.kt")))
    }

    @Test fun `COV1-2 Huella SHA-256 cancels the run before, and its button is off while one runs`() {
        val src = code("ui/screens/SecurityToolsScreen.kt").substringAfter("class FileHashViewModel")
        assertTrue("job?.cancel()" in src)
        assertTrue("session.finish(run, hash)" in src)
        assertTrue("sha256Hex(input.buffered()) { ensureActive() }" in src)
        assertTrue("enabled = !s.busy" in src)
    }

    @Test fun `HERR-1 Limpiar foto passes its cancellation to the readers`() {
        val src = code("ui/screens/PhotoCleanScreen.kt")
        assertTrue("PhotoCleaner.inspect(bytes, check)" in src && "PhotoCleaner.clean(bytes, check)" in src)
    }

    @Test fun `ANDROID-1 a deleted share copy has its URI permission revoked`() {
        assertTrue("revokeUriPermission" in code("android/ImageShare.kt"))
        assertTrue("revokeSharedCopy" in code("SeguridadApp.kt"))
    }

    /**
     * [src] with every comment and the inside of every string literal turned into spaces: same length and same line
     * breaks, so an index found in [src] is the same here, and a parenthesis or brace inside a text is not counted.
     */
    private fun blank(src: String): String {
        val out = StringBuilder(src)
        var i = 0
        fun clear(from: Int, to: Int) { for (k in from until to) if (out[k] != '\n') out[k] = ' ' }
        while (i < src.length) {
            when {
                src.startsWith("//", i) -> { val e = src.indexOf('\n', i).let { if (it < 0) src.length else it }; clear(i, e); i = e }
                src.startsWith("/*", i) -> {
                    var depth = 0
                    var j = i
                    while (j < src.length) {
                        if (src.startsWith("/*", j)) { depth++; j += 2 } else if (src.startsWith("*/", j)) { depth--; j += 2; if (depth == 0) break } else j++
                    }
                    clear(i, j); i = j
                }
                src.startsWith("\"\"\"", i) -> { val e = src.indexOf("\"\"\"", i + 3).let { if (it < 0) src.length else it + 3 }; clear(i + 3, e - 3); i = e }
                src[i] == '"' -> {
                    var j = i + 1
                    while (j < src.length && src[j] != '"') j += if (src[j] == '\\') 2 else 1
                    clear(i + 1, j); i = j + 1
                }
                src[i] == '\'' -> {
                    var j = i + 1
                    while (j < src.length && src[j] != '\'') j += if (src[j] == '\\') 2 else 1
                    clear(i + 1, j); i = j + 1
                }
                else -> i++
            }
        }
        return out.toString()
    }

    /** The index just past the bracket that closes the one at [open] in blanked code [b]. */
    private fun closing(b: String, open: Int): Int {
        val o = b[open]
        val c = if (o == '(') ')' else '}'
        var depth = 0
        for (k in open until b.length) {
            if (b[k] == o) depth++ else if (b[k] == c) { depth--; if (depth == 0) return k + 1 }
        }
        error("Unbalanced ${o} at $open")
    }

    /** The call at [at] (its name) up to the end of its arguments and of a trailing lambda, in blanked code [b]. */
    private fun callRange(b: String, at: Int): IntRange {
        val paren = b.indexOf('(', at)
        var end = closing(b, paren)
        var k = end
        while (k < b.length && b[k].isWhitespace()) k++
        if (k < b.length && b[k] == '{') end = closing(b, k)
        return at until end
    }

    /** Whether [index] of [src] is inside the content lambda of a call to [call]. */
    private fun insideCall(src: String, call: String, index: Int): Boolean {
        val b = blank(src)
        return Regex("""\b$call\b""").findAll(b).any { m ->
            val start = m.range.first
            if (start >= index || b.lastIndexOf("fun ", start).let { it >= 0 && b.substring(it + 4, start).isBlank() }) return@any false
            var k = m.range.last + 1
            while (k < b.length && b[k].isWhitespace()) k++
            if (k < b.length && b[k] == '(') { k = closing(b, k); while (k < b.length && b[k].isWhitespace()) k++ }
            k < b.length && b[k] == '{' && index in k until closing(b, k)
        }
    }

    @Test fun `ANDROID-3 every dialog and menu window of the app filters obscured touches`() {
        val root = mainDir()
        val window = Regex("""\b(AlertDialog|BasicAlertDialog|Dialog|DropdownMenu|ExposedDropdownMenu|ModalBottomSheet|Popup|DatePickerDialog)\(""")
        val missing = mutableListOf<String>()
        var windows = 0
        root.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { f ->
            val src = f.readText()
            val b = blank(src)
            for (m in window.findAll(b)) {
                val before = b.substring(b.lastIndexOf('\n', m.range.first) + 1, m.range.first)
                if (before.trimStart().startsWith("import") || before.trimEnd().endsWith("fun")) continue
                windows++
                val range = callRange(b, m.range.first)
                if ("FilterObscuredTouches()" !in b.substring(range.first, range.last + 1)) {
                    missing += "${f.name}:${src.substring(0, m.range.first).count { it == '\n' } + 1} ${m.groupValues[1]}"
                }
            }
        }
        assertTrue("Too few dialogs found: $windows", windows >= 15)
        assertTrue("Windows without FilterObscuredTouches():\n" + missing.joinToString("\n"), missing.isEmpty())
    }

    private fun mainDir(): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val f = File(dir, "app/src/main/java/app/seguridad")
            if (f.isDirectory) return f
            dir = dir.parentFile
        }
        error("app/src/main/java/app/seguridad not found")
    }

    @Test fun `the source helpers see through texts and comments`() {
        val src = "AlertDialog(text = { Text(\"a ) b\") /* ) */ }) // )\nNoCopy(Modifier) { Field(\"x\") }\nOther { \"y\" }"
        assertEquals(src.length, blank(src).length)
        assertEquals(0 until src.indexOf(" // )"), callRange(blank(src), 0))
        assertTrue(insideCall(src, "NoCopy", src.indexOf("\"x\"")))
        assertFalse(insideCall(src, "NoCopy", src.indexOf("\"y\"")))
    }

    @Test fun `TECLADO-1 NoCopy filters the new text menu and keeps the old toolbar`() {
        val src = main("ui/theme/Components.kt")
        val b = blank(src)
        val noCopy = callRange(b, b.indexOf("fun NoCopy("))
        val body = b.substring(noCopy.first, noCopy.last + 1)
        assertTrue("the new menu (Compose foundation 1.9+)", "filterTextContextMenuComponents(" in body)
        assertTrue("with the tested allow-list", "keptInNoCopyMenu" in code("ui/theme/Components.kt"))
        assertTrue("the old menu", "LocalTextToolbar provides" in body)
        assertTrue("F4 review M1: keyboard copy and cut", "onPreviewKeyEvent" in body && "isCopyOrCutKey(" in body)
    }

    @Test fun `TECLADO-2 the plain texts of Cifrar para and Firmar and every password field are inside NoCopy`() {
        val pk = main("ui/screens/PkActionsScreen.kt")
        for (label in listOf("\"Texto a cifrar\"", "\"Texto a firmar\"")) {
            val at = pk.indexOf(label)
            assertTrue("$label not found", at >= 0)
            assertTrue("$label outside NoCopy", insideCall(pk, "NoCopy", at))
        }
        // The master password (create, unlock, the contacts prompt) and every other PasswordField, also after «Ver».
        val comp = main("ui/theme/Components.kt")
        val field = comp.indexOf("OutlinedTextField(", comp.indexOf("fun PasswordField("))
        assertTrue(field > 0)
        assertTrue("PasswordField outside NoCopy", insideCall(comp, "NoCopy", field))
    }

    @Test fun `TECLADO-3 a copy found on reopening is cleared by its time, also when the app was closed before`() {
        val src = code("android/Clipboard.kt")
        assertTrue("ownCopyClearDelay(" in src)
        // F4 review M3: both timers use the same rule.
        assertEquals(2, Regex("""wipeOwnCopy\(""").findAll(src).count())
        // F4 re-review N1: the clip's text is read only inside the check, which runs only under our label.
        val clip = main("android/Clipboard.kt")
        val read = clip.indexOf("cm.primaryClip?.getItemAt")
        assertTrue(read > 0)
        assertTrue("the text read must be inside wipeOwnCopy's check", insideCall(clip, "wipeOwnCopy", read))
        assertEquals("one read of the clip's content", 1, Regex("""\.primaryClip\b""").findAll(code("android/Clipboard.kt").substringBefore("fun readClipboardText")).count())
        // F4 review M2: the process-wide timer must not hold the Activity.
        val b = blank(main("android/Clipboard.kt"))
        val stale = callRange(b, b.indexOf("fun clearStaleCopy("))
        val body = b.substring(stale.first, stale.last + 1)
        assertTrue("applicationContext.getSystemService(ClipboardManager" in body)
        assertFalse(Regex("""[^.]context\.getSystemService""").containsMatchIn(body))
        assertTrue("clearStaleCopy(this)" in code("MainActivity.kt"))
    }

    @Test fun `COV2-5 the clipboard texts come from ToolTexts and say this phone only`() {
        val tools = code("ui/screens/ToolsScreen.kt")
        assertTrue("clipboardAboutText(settings.clipboard.seconds)" in tools)
        assertFalse("lo que copias se borra del portapapeles a los" in tools)
        val settings = code("ui/screens/SettingsScreen.kt")
        assertTrue("Hint(CLIPBOARD_CLEAR_HINT)" in settings)
        assertFalse("Solo se borra si sigue" in settings)
    }
}
