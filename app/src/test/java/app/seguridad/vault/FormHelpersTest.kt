package app.seguridad.vault

import app.seguridad.crypto.Bip39
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.File
import java.io.FileNotFoundException
import java.text.Normalizer

/**
 * 2.0, task 7: the helpers of the entry form. The phrases are built here from the lists of the app with
 * [Bip39.phraseOf] over made-up entropy, never copied from anywhere.
 */
class FormHelpersTest {
    private fun asset(name: String): List<String> =
        listOf("src/main/assets", "../app/src/main/assets").map { File(it, name) }.first { it.exists() }
            .readLines(Charsets.UTF_8).filter { it.isNotBlank() }

    private val english = asset("bip39_english.txt")
    private val spanish = asset("bip39_spanish.txt")
    private val seeds = SeedLists(english, spanish)

    private fun phrase(list: List<String>, bytes: Int, from: Int = 1): List<String> =
        Bip39(list).phraseOf(ByteArray(bytes) { (it * 7 + from).toByte() })

    private val otpauth = "otpauth://totp/Ejemplo:ana?secret=JBSWY3DPEHPK3PXP&issuer=Ejemplo"

    // titleFromUrl

    @Test fun titleFromHost() {
        assertEquals("Correo.ejemplo.es", titleFromUrl("https://correo.ejemplo.es/entrar?x=1"))
        assertEquals("Banco.example", titleFromUrl("https://www.banco.example"))
        assertEquals("Banco.example", titleFromUrl("  HTTPS://WWW.Banco.Example:443/x  "))
        assertEquals("Tienda.example", titleFromUrl("http://tienda.example."))
        // A bare domain, as people type it, reads as a web address.
        assertEquals("Ejemplo.es", titleFromUrl("ejemplo.es"))
        assertEquals("Ejemplo.es", titleFromUrl("www.ejemplo.es/cuenta"))
        // «\» closes the authority, as in a browser (parseLink).
        assertEquals("Malo.example", titleFromUrl("https://malo.example\\@banco.example"))
        // Only «www.» at the start goes.
        assertEquals("Wwwx.example", titleFromUrl("https://wwwx.example"))
        // The host as the browser connects to it (ASCII): a name in another script is never shown as a look-alike.
        assertEquals("Xn--espaa-rta.example", titleFromUrl("https://españa.example"))
        assertEquals("192.168.1.1", titleFromUrl("http://192.168.1.1/admin"))
    }

    @Test fun titleNullWithoutHost() {
        for (text in listOf("", "   ", "texto sin más", "https://", "mailto:ana@ejemplo.es", otpauth, "ftp://archivos.example", "javascript:alert(1)", "localhost")) {
            assertNull(text, titleFromUrl(text))
        }
    }

    @Test fun titleIgnoresUserinfo() {
        assertEquals("Host.example", titleFromUrl("https://usuario:clave@host.example"))
        assertEquals("Host.example", titleFromUrl("https://usuario@host.example/x"))
        assertEquals("Host.example", titleFromUrl("https://a@b:c@host.example"))
        // Without a scheme, «usuario:clave@host» is not a web address: no title at all, so the key never becomes one.
        assertNull(titleFromUrl("usuario:clave@host.example"))
        assertNull(titleFromUrl("usuario@host.example"))
        for (text in listOf("https://usuario:clave@host.example", "usuario:clave@host.example")) {
            val t = titleFromUrl(text).orEmpty().lowercase()
            assertFalse(t, "usuario" in t || "clave" in t)
        }
    }

    // detectPasted

    @Test fun detectsOtpauth() {
        assertEquals(PastedKind.OTPAUTH, detectPasted(otpauth, seeds))
        assertEquals(PastedKind.OTPAUTH, detectPasted("\n  $otpauth \n", seeds))
        assertEquals(PastedKind.OTPAUTH, detectPasted(otpauth.replace("otpauth://totp", "OTPAUTH://TOTP"), seeds))
        // The lists are not needed for a link.
        assertEquals(PastedKind.OTPAUTH, detectPasted(otpauth, null))
        // A link that does not read: no key, a broken key, settings out of range (CRY-B02: refused, never changed).
        assertNull(detectPasted("otpauth://totp/Ejemplo:ana?issuer=Ejemplo", seeds))
        assertNull(detectPasted("otpauth://totp/Ejemplo:ana?secret=1", seeds))
        assertNull(detectPasted("$otpauth&digits=9", seeds))
        assertNull(detectPasted("otpauth://hotp/Ejemplo:ana?secret=JBSWY3DPEHPK3PXP&counter=1", seeds))
        assertNull(detectPasted("otpauth-migration://offline?data=AAAA", seeds))
        assertNull(detectPasted("ver $otpauth", seeds))
    }

    @Test fun detectsSeedOnlyWithChecksum() {
        for (bytes in listOf(16, 20, 24, 28, 32)) {
            val words = phrase(english, bytes)
            assertEquals(bytes / 4 * 3, words.size)
            assertEquals("$bytes", PastedKind.SEED, detectPasted(words.joinToString(" "), seeds))
        }
        val twelve = phrase(english, 16)
        // Capitals, line breaks, tabs and other spaces between the words.
        assertEquals(PastedKind.SEED, detectPasted(twelve.joinToString("\n").uppercase(), seeds))
        assertEquals(PastedKind.SEED, detectPasted("  " + twelve.joinToString("\t ") + "\n", seeds))
        assertEquals(PastedKind.SEED, detectPasted(twelve.joinToString(" "), seeds))

        // The same words with a wrong checksum: the last word changed for others of the list until the sum fails.
        val bad = english.asSequence().map { twelve.dropLast(1) + it }.first { Bip39(english).entropyOf(it) == null }
        assertNull(detectPasted(bad.joinToString(" "), seeds))
        // A wrong number of words: 11, 13 and 25, all from the list.
        assertNull(detectPasted(twelve.dropLast(1).joinToString(" "), seeds))
        assertNull(detectPasted((twelve + "abandon").joinToString(" "), seeds))
        assertNull(detectPasted((phrase(english, 32) + "abandon").joinToString(" "), seeds))
        // A word out of the list.
        assertNull(detectPasted((twelve.dropLast(1) + "zzzz").joinToString(" "), seeds))
        // Without the lists, no phrase is recognised.
        assertNull(detectPasted(twelve.joinToString(" "), null))
    }

    @Test fun detectsSpanishSeedComposedDecomposedAndWithoutAccents() {
        // The Spanish list of the app is written decomposed (a letter and its accent); find a phrase with accents.
        val words = (1..200).asSequence().map { phrase(spanish, 16, from = it) }
            .first { p -> p.any { w -> Normalizer.normalize(w, Normalizer.Form.NFC) != w } }
        val nfd = words.joinToString(" ") { Normalizer.normalize(it, Normalizer.Form.NFD) }
        val nfc = Normalizer.normalize(nfd, Normalizer.Form.NFC)
        val bare = Normalizer.normalize(nfd, Normalizer.Form.NFD).filterNot { Character.getType(it) == Character.NON_SPACING_MARK.toInt() }
        assertTrue(nfc != nfd && bare != nfc)
        assertEquals(PastedKind.SEED, detectPasted(nfd, seeds))
        assertEquals(PastedKind.SEED, detectPasted(nfc, seeds))
        assertEquals(PastedKind.SEED, detectPasted(bare, seeds))
        assertEquals(PastedKind.SEED, detectPasted(nfc.uppercase(), seeds))
        // Words of both lists in one phrase never add up.
        val mixed = words.take(6).map { Normalizer.normalize(it, Normalizer.Form.NFC) } + phrase(english, 16).drop(6)
        assertNull(detectPasted(mixed.joinToString(" "), seeds))
    }

    @Test fun ordinaryTextIsNothing() {
        val texts = listOf(
            "",
            "hola, esto es una nota normal",
            "contraseña-muy-larga-123",
            "https://ejemplo.es/entrar",
            "otpauth",
            "uno dos tres cuatro cinco seis siete ocho nueve diez once doce",
            // A phrase inside a longer text is not a phrase.
            "mi frase: " + phrase(english, 16).joinToString(" "),
            // Too long to look at (decision (a)): read up to a bound only.
            phrase(english, 16).let { p -> p.first() + " ".repeat(5_000) + p.drop(1).joinToString(" ") },
            "x".repeat(100_000),
        )
        for (t in texts) assertNull(t.take(40), detectPasted(t, seeds))
    }

    @Test fun seedListsThatCannotBeReadRecogniseNoPhrase() {
        val broken = SeedLists(emptyList(), listOf("solo", "dos"))
        assertNull(detectPasted(phrase(english, 16).joinToString(" "), broken))
        assertEquals(PastedKind.OTPAUTH, detectPasted(otpauth, broken))
    }

    // pastedBlock: only a block inserted at once reaches detectPasted; typing one character at a time never does.

    @Test fun typingNeverReachesDetection() {
        var text = ""
        for (c in otpauth) {
            val next = text + c
            assertNull(pastedBlock(text, next))
            text = next
        }
        // Deleting, and the keyboard changing a word for a short one, either.
        assertNull(pastedBlock(text, text.dropLast(5)))
        assertNull(pastedBlock("contrasena", "contraseña"))
    }

    @Test fun aPasteIsTheInsertedBlock() {
        assertEquals(otpauth, pastedBlock("", otpauth))
        assertEquals(otpauth, pastedBlock("ab", "a${otpauth}b"))
        // Pasting over a selection: the block that came in.
        assertEquals(otpauth, pastedBlock("antes SELECCION despues", "antes ${otpauth} despues"))
        // Pasting the same text that was selected changes nothing.
        assertNull(pastedBlock(otpauth, otpauth))
    }

    // The «+» chooser.

    @Test fun theChooserOffersTheTypesInOrder() {
        assertEquals(
            listOf("Acceso", "Código 2FA", "Nota", "Frase semilla", "Tarjeta bancaria", "Red wifi", "Licencia", "Pasaporte", "Otro"),
            NEW_ENTRY_CHOICES.map { it.label },
        )
        assertEquals(
            listOf(EntryType.LOGIN, EntryType.TOTP, EntryType.NOTE, EntryType.SEED, EntryType.CARD, EntryType.WIFI, EntryType.LICENSE, EntryType.PASSPORT, EntryType.LOGIN),
            NEW_ENTRY_CHOICES.map { it.type },
        )
        // R-4: the labels are those of the types (the form's chips), «Otro» is an empty «Acceso».
        NEW_ENTRY_CHOICES.dropLast(1).forEach { assertEquals(it.type.label, it.label) }
        assertTrue(NEW_ENTRY_CHOICES.none { it.type.isPersonal })
    }

    // The generator and the strength.

    @Test fun strengthWhileTyping() {
        assertNull(strengthText(""))
        assertEquals("Fuerza estimada: Débil", strengthText("1234"))
        assertEquals("Fuerza estimada: Débil", strengthText("password"))
        assertEquals("Fuerza estimada: Muy fuerte", strengthText("k#9Lw!q2Zr@7Vt\$4Pm&x8"))
        assertNotNull(strengthText("x".repeat(20_000)))
    }

    @Test fun onlyPasswordsTheUserChoosesGetTheGenerator() {
        assertTrue(templateSecretIsPassword(EntryType.WIFI, WIFI_PASSWORD_KEY))
        // Numbers someone else gives (a card's, a passport's, a licence's key) are not generated.
        assertFalse(templateSecretIsPassword(EntryType.CARD, "number"))
        assertFalse(templateSecretIsPassword(EntryType.CARD, "cvv"))
        assertFalse(templateSecretIsPassword(EntryType.CARD, "pin"))
        assertFalse(templateSecretIsPassword(EntryType.LICENSE, "key"))
        assertFalse(templateSecretIsPassword(EntryType.PASSPORT, "number"))
        assertFalse(templateSecretIsPassword(EntryType.WIFI, "ssid"))
    }

    // The lists, once per process and off the main thread, as the other lists of the app (F6).

    @Test fun theListsOfThePhrasesAreReadFromTheAssets() = runBlocking {
        val dir = listOf("src/main/assets", "../app/src/main/assets").map(::File).first { it.isDirectory }
        val opened = ArrayList<String>()
        val lists = WordLists({ name -> opened += name; File(dir, name).inputStream() }, Dispatchers.Unconfined)
        val read = lists.seedLists()
        assertNotNull(read)
        assertEquals(PastedKind.SEED, detectPasted(phrase(spanish, 32).joinToString(" "), read))
        // Kept: a second form reads nothing again.
        assertTrue(read === lists.seedLists())
        assertEquals(listOf(BIP39_ENGLISH_LIST, BIP39_SPANISH_LIST), opened)
        // A list that cannot be read is not kept: no phrase is recognised, and the next form tries again.
        var fail = true
        val broken = WordLists({ name -> if (fail && name == BIP39_SPANISH_LIST) throw FileNotFoundException(name) else File(dir, name).inputStream() }, Dispatchers.Unconfined)
        assertNull(broken.seedLists())
        fail = false
        assertNotNull(broken.seedLists())
    }

    // The wiring of the screens (read from their sources, as EntryFormTest does).

    private fun source(path: String): String {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val f = File(dir, "app/src/main/java/app/seguridad/$path")
            if (f.isFile) return f.readText()
            dir = dir.parentFile
        }
        error("$path not found")
    }

    @Test fun aPasteIsOnlyLookedAtInTheFieldsOwnChange() {
        val main = generateSequence(File(".").absoluteFile) { it.parentFile }.map { File(it, "app/src/main/java") }.first { it.isDirectory }
        val callers = main.walkTopDown().filter { it.isFile && it.extension == "kt" }
            .filter { f -> f.readText().contains("detectPasted(") }.map { it.name }.toSet()
        // Only the session's change of a field (EntryEditSession.typed) asks; the helpers define it.
        assertEquals(setOf("FormHelpers.kt", "EntryForm.kt"), callers)
        val form = source("vault/EntryForm.kt")
        assertEquals(1, Regex("""detectPasted\(""").findAll(form).count())
        assertTrue("val block = pastedBlock(before, value) ?: return" in form)
        // No new reading of the clipboard: the editor's only one is the «Pegar enlace» of the 2FA, as before.
        val screen = source("ui/screens/VaultEntryScreen.kt")
        assertEquals(1, Regex("""readClipboardText\(""").findAll(screen).count())
        assertFalse("readClipboardText" in source("ui/screens/EntryFields.kt") || "readClipboardText" in form)
        // The fields go through the session; the generator never does.
        for (slot in listOf("TITLE", "USERNAME", "PASSWORD", "URL", "SEED")) assertTrue(slot, "form.typed(FormSlot.$slot, it)" in screen)
        assertTrue("form.typed(FormSlot.NOTES, it.text)" in source("ui/screens/EntryFields.kt"))
        assertTrue("onGenerated = { loginPassword = it }" in screen)
    }

    @Test fun backAsksWithChangesAndThePlusOpensTheChooser() {
        val screen = source("ui/screens/VaultEntryScreen.kt")
        assertTrue("BackHandler(enabled = form.hasUnsavedChanges) { form.back() }" in screen)
        assertTrue("BackScaffold(if (existing == null) \"Nueva entrada\" else \"Editar\", form::back)" in screen)
        // «Guardar» of the dialog is the button's.
        assertEquals(2, Regex("""vm(::|\.)trySave""").findAll(screen).count())
        val vault = source("ui/screens/VaultScreen.kt")
        assertTrue("onClick = newEntry," in vault)
        assertTrue("NewEntryChooser(onDismiss = { choosing = false })" in vault)
        assertTrue("val newEntry: () -> Unit = { if (totpOnly) onNew(EntryType.TOTP) else choosing = true }" in vault)
        // «Crear la primera» of the empty vault does what the «+» does.
        assertTrue("Button(onClick = onNew) { Text(EMPTY_CREATE_FIRST) }" in source("ui/screens/VaultList.kt"))
    }
}
