package app.seguridad.vault

import app.seguridad.crypto.TotpSecret
import app.seguridad.tools.codes.PlainCells
import app.seguridad.tools.qr.QR_WIFI_UNREADABLE
import app.seguridad.tools.qr.WIFI_SCAN_NOT_WIFI
import app.seguridad.tools.qr.WIFI_SCAN_NOT_WIFI_SIMPLE
import app.seguridad.tools.qr.qrWifiOtherSecurity
import app.seguridad.tools.qrBidi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M6 and concern 3 of the CR review: the entry editor's state lives in its ViewModel (this session), so a rotation keeps
 * what was typed, and it goes on a lock, on save, on back, on leaving and when the ViewModel is cleared. In memory only.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EntryEditSessionTest {
    private val saved = VaultEntry(
        id = "e1", type = EntryType.LOGIN, title = "Banco", username = "ana", password = "clave-guardada",
        url = "https://banco.example", notes = "nota", createdAt = 1, updatedAt = 1, favorite = true, tags = listOf("a", "b"),
    )

    private fun session(id: String? = null, type: EntryType = EntryType.LOGIN) = EntryEditSession(PlainCells, id, type, newId = "nuevo-1")

    /** Types something secret in every field. */
    private fun EntryEditSession.typeEverything() {
        title.value = "Wallet"
        username.value = "yo"
        loginPassword.value = "contraseña-escrita"
        seedPassphrase.value = "passphrase-escrita"
        url.value = "https://x.example"
        notes.value = "nota secreta"
        seed.value = "abandon ability able"
        totp.value = TotpSecret("JBSWY3DPEHPK3PXP", issuer = "X", account = "y")
        favorite.value = true
        tagsText.value = "t1, t2"
        error.value = "un error"
        info.value = "un aviso"
    }

    private fun EntryEditSession.assertEmpty() {
        assertEquals("", title.value)
        assertEquals("", username.value)
        assertEquals("", loginPassword.value)
        assertEquals("", seedPassphrase.value)
        assertEquals("", url.value)
        assertEquals("", notes.value)
        assertEquals("", seed.value)
        assertEquals(TotpSecret(""), totp.value)
        assertFalse(favorite.value)
        assertEquals("", tagsText.value)
        assertNull(error.value)
        assertNull(info.value)
        assertNull(migration.value)
        assertNull(existing.value)
        assertFalse(saving.value)
        // 2.0, task 7: a pending question about a paste (it holds what was pasted) and the dialog of «Atrás».
        assertNull(pasteOffer.value)
        assertFalse(askingToLeave.value)
    }

    @Test fun anExistingEntryIsLoadedOnceAndWhatIsTypedSurvives() {
        val s = session(id = "e1")
        s.vaultStateIs(unlocked = true, listOf(saved))
        assertEquals(saved, s.existing.value)
        assertEquals("Banco", s.title.value)
        assertEquals("clave-guardada", s.loginPassword.value)
        assertEquals("", s.seedPassphrase.value) // One field per type.
        assertEquals("a, b", s.tagsText.value)
        assertTrue(s.favorite.value)
        s.title.value = "Banco nuevo"
        s.loginPassword.value = "otra"
        // A rotation composes the screen again over the same session: loading again changes nothing.
        s.vaultStateIs(unlocked = true, listOf(saved))
        s.load(listOf(saved))
        assertEquals("Banco nuevo", s.title.value)
        assertEquals("otra", s.loginPassword.value)
    }

    @Test fun aNewEntryKeepsOneIdForTheWholeSession() {
        val s = session()
        s.load(emptyList())
        assertEquals("nuevo-1", s.entryId)
        s.typeEverything()
        assertEquals("nuevo-1", s.entryId) // However many times «Guardar» is tapped, or the screen rotated.
        assertEquals("e1", session(id = "e1").apply { load(listOf(saved)) }.entryId)
    }

    @Test fun aLockClearsEverythingAndTheNextUnlockStartsFromTheVault() {
        val s = session(id = "e1")
        s.load(listOf(saved))
        s.typeEverything()
        s.migration.value = PendingMigration(app.seguridad.crypto.MigrationResult(listOf(TotpSecret("JBSWY3DPEHPK3PXP")), emptyList(), 0, 1), emptyList(), emptyList())
        s.saving.value = true
        s.vaultStateIs(unlocked = false, emptyList())
        s.assertEmpty()
        assertFalse(s.loaded)
        // Opened again (the screen still there): it starts from the saved entry, never from what was typed.
        s.vaultStateIs(unlocked = true, listOf(saved))
        assertEquals("Banco", s.title.value)
        assertEquals("clave-guardada", s.loginPassword.value)
    }

    @Test fun aLockBeforeAnythingWasLoadedDoesNothing() {
        val s = session()
        s.vaultStateIs(unlocked = false, emptyList())
        assertFalse(s.loaded)
        assertFalse(s.finished.value)
    }

    @Test fun saveOrBackFinishesAndClears() {
        val s = session()
        s.load(emptyList())
        s.typeEverything()
        s.finish()
        s.assertEmpty()
        assertTrue(s.finished.value)
        // Finished for good: a later unlock does not load it again.
        s.vaultStateIs(unlocked = true, listOf(saved))
        assertTrue(s.finished.value)
        assertEquals("", s.title.value)
    }

    @Test fun leavingOrBeingClearedLetsItGoAndComingBackReloads() {
        val s = session(id = "e1")
        s.load(listOf(saved))
        s.typeEverything()
        s.clear() // Another tab, or the ViewModel cleared.
        s.assertEmpty()
        s.load(listOf(saved)) // Back on the tab: from the vault again.
        assertEquals("Banco", s.title.value)
    }

    @Test fun theQrTextIsHandedOverOnce() {
        val s = session(type = EntryType.TOTP)
        s.load(emptyList())
        assertEquals("otpauth://totp/x?secret=JBSWY3DPEHPK3PXP", s.takeHandOver("otpauth://totp/x?secret=JBSWY3DPEHPK3PXP"))
        // A rotation composes the form again: the text is not applied a second time (a batch is never offered twice).
        assertNull(s.takeHandOver("otpauth://totp/x?secret=JBSWY3DPEHPK3PXP"))
        assertNull(session().takeHandOver(null))
        // Not even after a lock and a new unlock.
        s.vaultStateIs(unlocked = false, emptyList())
        s.vaultStateIs(unlocked = true, emptyList())
        assertNull(s.takeHandOver("otpauth://totp/x?secret=JBSWY3DPEHPK3PXP"))
    }

    // m6 (CR re-review): the ViewModel's wiring, as a function the ViewModel calls: the vault's state is followed with no
    // composition at all (a lock in the background clears at once), and leaving or being cleared lets it go.
    @Test fun followingTheVaultClearsOnALockWithNoScreenAndReloadsOnTheNextUnlock() = runTest {
        val state = MutableStateFlow<VaultState>(VaultState.Unlocked(VaultData(entries = listOf(saved))))
        val s = session(id = "e1")
        s.followVault(state, backgroundScope)
        runCurrent()
        assertEquals("Banco", s.title.value)
        s.typeEverything()
        state.value = VaultState.Locked // In the background: no composition exists.
        runCurrent()
        s.assertEmpty()
        state.value = VaultState.Unlocked(VaultData(entries = listOf(saved)))
        runCurrent()
        assertEquals("Banco", s.title.value)
        assertEquals("clave-guardada", s.loginPassword.value)
    }

    @Test fun leavingWithoutAConfigurationChangeOrBeingClearedLetsItGo() {
        val s = session(id = "e1")
        s.load(listOf(saved))
        s.typeEverything()
        s.left(changingConfigurations = true) // A rotation: kept.
        assertEquals("contraseña-escrita", s.loginPassword.value)
        s.left(changingConfigurations = false) // Another tab, or the activity finishing: gone.
        s.assertEmpty()
        s.load(listOf(saved))
        s.typeEverything()
        s.cleared() // The ViewModel's onCleared.
        s.assertEmpty()
    }

    // 2.0, task 7: «¿Guardar los cambios?» only when something changed since the form was loaded.

    private fun seeds(): SeedLists {
        fun asset(name: String) = listOf("src/main/assets", "../app/src/main/assets").map { java.io.File(it, name) }.first { it.exists() }
            .readLines(Charsets.UTF_8).filter { it.isNotBlank() }
        return SeedLists(asset("bip39_english.txt"), asset("bip39_spanish.txt"))
    }

    private fun seedPhrase(): String {
        fun asset(name: String) = listOf("src/main/assets", "../app/src/main/assets").map { java.io.File(it, name) }.first { it.exists() }
            .readLines(Charsets.UTF_8).filter { it.isNotBlank() }
        return app.seguridad.crypto.Bip39(asset("bip39_english.txt")).phraseOf(ByteArray(16) { (it * 5 + 3).toByte() }).joinToString(" ")
    }

    private val otpauth = "otpauth://totp/Ejemplo:ana?secret=JBSWY3DPEHPK3PXP&issuer=Ejemplo"

    @Test fun unsavedChangesDetected() {
        val s = session(id = "e1")
        s.load(listOf(saved))
        s.title.value = "Banco nuevo"
        assertTrue(s.hasUnsavedChanges)
        s.back()
        assertTrue(s.askingToLeave.value)
        assertFalse(s.finished.value)
        // «Seguir editando»: the dialog goes, the typed text stays.
        s.keepEditing()
        assertFalse(s.askingToLeave.value)
        assertEquals("Banco nuevo", s.title.value)
        // Back to what was loaded: nothing to save.
        s.title.value = "Banco"
        assertFalse(s.hasUnsavedChanges)

        val changes: List<Pair<String, EntryEditSession.() -> Unit>> = listOf(
            "usuario" to { username.value = "otra" },
            "contraseña" to { loginPassword.value = "otra-clave" },
            "web" to { url.value = "https://otro.example" },
            "notas" to { notes.value = "otra nota" },
            "favorito" to { favorite.value = false },
            "etiquetas" to { tagsText.value = "a, b, c" },
            "formato" to { notesFormat.value = NotesFormat.LIMITED },
            "caducidad" to { expiresText.value = "01/01/2030" },
            "recordatorio" to { rotateMonths.value = 6 },
            "campo" to { assertNull(addField("Pregunta", FieldKind.HIDDEN, false)) },
        )
        for ((what, change) in changes) {
            val t = session(id = "e1").apply { load(listOf(saved)) }
            t.change()
            assertTrue(what, t.hasUnsavedChanges)
        }
        // A new entry: anything typed counts.
        val n = session()
        n.load(emptyList())
        n.typed(FormSlot.TITLE, "Correo")
        assertTrue(n.hasUnsavedChanges)
        val c = session(type = EntryType.CARD)
        c.load(emptyList())
        c.setTemplateValue(EntryType.CARD, "number", "4000 0000 0000 0002")
        assertTrue(c.hasUnsavedChanges)
        // A field of the user's: its value, and its place.
        val withFields = saved.copy(fields = listOf(CustomField("f1", "Uno", FieldKind.TEXT, "1"), CustomField("f2", "Dos", FieldKind.TEXT, "2")))
        val f = session(id = "e1").apply { load(listOf(withFields)) }
        f.moveField(0, 1)
        assertTrue(f.hasUnsavedChanges)
        f.moveField(1, -1)
        assertFalse(f.hasUnsavedChanges)
        f.setFieldValue(1, "dos")
        assertTrue(f.hasUnsavedChanges)
        f.removeField(1)
        assertTrue(f.hasUnsavedChanges)
    }

    @Test fun noChangesNoDialog() {
        // Opening never counts as a change, whatever the load had to tidy up.
        val day = expiresAtOf(java.time.LocalDate.parse("2027-05-31"))
        val loads: List<Pair<String, EntryEditSession>> = listOf(
            "acceso" to session(id = "e1").apply { load(listOf(saved)) },
            "nueva" to session().apply { load(emptyList()) },
            // A new wifi starts as WPA, not hidden.
            "wifi nueva" to session(type = EntryType.WIFI).apply { load(emptyList()) },
            // A card with the expiresAt of a copy, moved into the template's expiry.
            "tarjeta de una copia" to session(id = "c").apply { load(listOf(VaultEntry("c", EntryType.CARD, "Tarjeta", createdAt = 1, updatedAt = 1, expiresAt = day))) },
            // A field id seen twice (a copy made elsewhere) gets a new one, and a date is shown as DD/MM/AAAA.
            "campos repetidos" to session(id = "e1").apply {
                load(listOf(saved.copy(fields = listOf(CustomField("x", "Uno", FieldKind.TEXT, "1"), CustomField("x", "Dos", FieldKind.DATE, "2027-01-31", expires = true)))))
            },
            "nota con formato" to session(id = "n").apply { load(listOf(VaultEntry("n", EntryType.NOTE, "Nota", notes = "**a**", notesFormat = NotesFormat.LIMITED, createdAt = 1, updatedAt = 1))) },
            "2FA" to session(id = "t").apply { load(listOf(VaultEntry("t", EntryType.TOTP, "X", totp = TotpSecret("JBSWY3DPEHPK3PXP", "X", "y"), createdAt = 1, updatedAt = 1))) },
        )
        for ((what, s) in loads) {
            assertTrue(what, s.loaded)
            assertFalse(what, s.hasUnsavedChanges)
            // A rotation composes the form again over the same session.
            s.load(listOf(saved))
            assertFalse(what, s.hasUnsavedChanges)
            s.back()
            assertFalse(what, s.askingToLeave.value)
            assertTrue(what, s.finished.value)
        }
        // Changing the type of a new entry, with nothing typed, loses nothing.
        val n = session()
        n.load(emptyList())
        n.changeType(EntryType.CARD)
        n.changeType(EntryType.NOTE)
        assertFalse(n.hasUnsavedChanges)
        // Typed and taken back: an empty template value is no value; spaces around the title are not saved.
        n.changeType(EntryType.CARD)
        n.setTemplateValue(EntryType.CARD, "holder", "Ana")
        n.setTemplateValue(EntryType.CARD, "holder", "")
        n.typed(FormSlot.TITLE, "  ")
        assertFalse(n.hasUnsavedChanges)
        // An error or a notice on screen is not a change.
        n.error.value = "un error"
        n.info.value = "un aviso"
        assertFalse(n.hasUnsavedChanges)
        // A lock clears the form: nothing loaded, nothing to ask about.
        val e = session(id = "e1").apply { load(listOf(saved)) }
        e.title.value = "otro"
        e.vaultStateIs(unlocked = false, emptyList())
        assertFalse(e.hasUnsavedChanges)
        e.vaultStateIs(unlocked = true, listOf(saved))
        assertFalse(e.hasUnsavedChanges)
    }

    // 2.0, task 7: the title from the URL fills only an empty title.

    @Test fun theUrlFillsAnEmptyTitleOnly() {
        val n = session()
        n.load(emptyList())
        for (part in listOf("h", "ht", "https://c", "https://co", "https://usuario:clave@correo.ejemplo.es")) n.typed(FormSlot.URL, part)
        assertEquals("https://usuario:clave@correo.ejemplo.es", n.url.value)
        // While the title is the one it filled, it follows the URL; never with the user info.
        assertEquals("Correo.ejemplo.es", n.title.value)
        n.typed(FormSlot.URL, "texto")
        assertEquals("", n.title.value)
        n.typed(FormSlot.URL, "https://banco.example")
        assertEquals("Banco.example", n.title.value)
        // Once the user writes a title, it is theirs.
        n.typed(FormSlot.TITLE, "Mi banco")
        n.typed(FormSlot.URL, "https://otro.example")
        assertEquals("Mi banco", n.title.value)
        // A loaded title is never touched.
        val e = session(id = "e1").apply { load(listOf(saved)) }
        e.typed(FormSlot.URL, "https://otro.example")
        assertEquals("Banco", e.title.value)
        // After a clear, the next load starts again.
        n.clear()
        n.load(emptyList())
        n.typed(FormSlot.TITLE, "Correo")
        n.typed(FormSlot.URL, "https://otro.example")
        assertEquals("Correo", n.title.value)
    }

    // 2.0, task 7: a paste that looks like a 2FA link or a seed phrase asks; it never changes the type on its own.

    @Test fun aPastedSeedIsOfferedAndTakenOutOfTheField() {
        val s = session()
        s.load(emptyList())
        s.seedLists = seeds()
        val phrase = seedPhrase()
        s.typed(FormSlot.TITLE, "Car")
        s.typed(FormSlot.TITLE, "Car$phrase")
        // The paste is in the field as pasted until the user answers; the type has not changed.
        assertEquals("Car$phrase", s.title.value)
        assertEquals(EntryType.LOGIN, s.type.value)
        val offer = s.pasteOffer.value!!
        assertEquals(PastedKind.SEED, offer.kind)
        assertFalse("never in its text", phrase in offer.toString())
        assertNull(s.acceptPaste())
        assertNull(s.pasteOffer.value)
        assertEquals(EntryType.SEED, s.type.value)
        assertEquals(phrase, s.seed.value)
        // Out of the title (titles are shown in lists): what was there before the paste.
        assertEquals("Car", s.title.value)
    }

    @Test fun aPastedOtpauthIsHandedToTheReaderOfTheLinks() {
        val s = session()
        s.load(emptyList())
        s.typed(FormSlot.PASSWORD, otpauth)
        assertEquals(PastedKind.OTPAUTH, s.pasteOffer.value?.kind)
        assertEquals(otpauth, s.acceptPaste())
        assertEquals(EntryType.TOTP, s.type.value)
        assertEquals("", s.loginPassword.value)
        // «No»: everything stays as pasted.
        val t = session()
        t.load(emptyList())
        t.typed(FormSlot.NOTES, "nota $otpauth".removePrefix("nota "))
        assertTrue(t.pasteOffer.value != null)
        t.declinePaste()
        assertNull(t.pasteOffer.value)
        assertEquals(otpauth, t.notes.value)
        assertEquals(EntryType.LOGIN, t.type.value)
    }

    @Test fun aPasteIsOnlyOfferedWhereItChangesSomething() {
        val phrase = seedPhrase()
        // Typing, one character at a time, is never a paste.
        val typing = session().apply { load(emptyList()); seedLists = seeds() }
        var text = ""
        for (c in otpauth) {
            text += c
            typing.typed(FormSlot.NOTES, text)
        }
        assertNull(typing.pasteOffer.value)
        // An existing entry does not change its type.
        val e = session(id = "e1").apply { load(listOf(saved)); seedLists = seeds() }
        e.typed(FormSlot.NOTES, otpauth)
        e.typed(FormSlot.PASSWORD, phrase)
        assertNull(e.pasteOffer.value)
        // Already of that type.
        val seed = session(type = EntryType.SEED).apply { load(emptyList()); seedLists = seeds() }
        seed.typed(FormSlot.SEED, phrase)
        assertNull(seed.pasteOffer.value)
        // Ordinary text.
        val n = session().apply { load(emptyList()); seedLists = seeds() }
        n.typed(FormSlot.NOTES, "una nota pegada de muchas palabras normales")
        assertNull(n.pasteOffer.value)
        // Not loaded (locked): nothing.
        val locked = session().apply { seedLists = seeds() }
        locked.typed(FormSlot.NOTES, otpauth)
        assertNull(locked.pasteOffer.value)
    }

    @Test fun aLockWhileAskingDropsThePasteAndALateAnswerDoesNothing() {
        val s = session()
        s.load(emptyList())
        s.seedLists = seeds()
        s.typed(FormSlot.PASSWORD, seedPhrase())
        assertTrue(s.pasteOffer.value != null)
        s.vaultStateIs(unlocked = false, emptyList())
        s.assertEmpty()
        // The «Sí» of a dialog that was on screen comes too late.
        assertNull(s.acceptPaste())
        s.vaultStateIs(unlocked = true, emptyList())
        assertEquals(EntryType.LOGIN, s.type.value)
        assertEquals("", s.seed.value)
        assertNull(s.pasteOffer.value)
    }

    // Review round 1 of task 7: I-1 (a tab asks too), m-1 (no old error in the dialog), m-2 (nothing during a save).

    @Test fun aTabWithChangesAsksAndDiscardGoesWithNothingTyped() {
        val s = session(id = "e1").apply { load(listOf(saved)) }
        var went = 0
        s.loginPassword.value = "contraseña-nueva-recien-generada"
        s.askToLeave { went++ }
        assertTrue(s.askingToLeave.value)
        assertEquals(0, went)
        // «Descartar»: what was typed goes first, then the tab opens; the form is not «finished» (the tab keeps it).
        s.discard()
        assertEquals(1, went)
        s.assertEmpty()
        assertFalse(s.finished.value)
        // Back on the tab: from the vault again.
        s.load(listOf(saved))
        assertEquals("clave-guardada", s.loginPassword.value)
        // Without changes a tab goes at once.
        s.askToLeave { went++ }
        assertEquals(2, went)
        assertFalse(s.askingToLeave.value)
    }

    @Test fun keepEditingStaysAndForgetsTheTab() {
        val s = session(id = "e1").apply { load(listOf(saved)) }
        var went = 0
        s.title.value = "Banco nuevo"
        s.askToLeave { went++ }
        s.keepEditing()
        assertFalse(s.askingToLeave.value)
        assertEquals("Banco nuevo", s.title.value)
        // The next «Atrás» and «Descartar» end the form; the tab of before is never opened.
        s.back()
        s.discard()
        assertEquals(0, went)
        assertTrue(s.finished.value)
    }

    @Test fun saveFromATabsDialogGoesOnceSaved() {
        val s = session(id = "e1").apply { load(listOf(saved)) }
        var went = 0
        s.title.value = "Banco nuevo"
        s.askToLeave { went++ }
        // «Guardar» passed the checks: the dialog goes, and the tab waits for the save.
        s.savingToLeave()
        assertFalse(s.askingToLeave.value)
        val go = s.takeLeave()
        assertTrue(go != null)
        assertNull(s.takeLeave()) // Taken once: a failed save, or a second one, goes nowhere.
        go!!.invoke()
        assertEquals(1, went)
    }

    @Test fun aLockWhileAskingForATabGoesNowhere() {
        val s = session(id = "e1").apply { load(listOf(saved)) }
        var went = 0
        s.title.value = "Banco nuevo"
        s.askToLeave { went++ }
        s.vaultStateIs(unlocked = false, emptyList())
        s.assertEmpty()
        assertNull(s.takeLeave())
        s.discard() // A late tap of the dialog that was on screen.
        assertEquals(0, went)
        // A re-created activity: the place to go of the old one is dropped, with its dialog; the form stays.
        val r = session(id = "e1").apply { load(listOf(saved)) }
        r.title.value = "Banco nuevo"
        r.askToLeave { went++ }
        r.left(changingConfigurations = true)
        assertFalse(r.askingToLeave.value)
        assertNull(r.takeLeave())
        assertEquals("Banco nuevo", r.title.value)
    }

    @Test fun theDialogOpensWithoutAnOldError() {
        val s = session(id = "e1").apply { load(listOf(saved)) }
        s.error.value = "Ponle un nombre"
        s.title.value = "Banco nuevo"
        s.back()
        assertTrue(s.askingToLeave.value)
        assertNull(s.error.value)
    }

    @Test fun duringASaveNothingIsAsked() {
        val s = session(id = "e1").apply { load(listOf(saved)) }
        var went = 0
        s.title.value = "Banco nuevo"
        s.saving.value = true
        // «Atrás» waits: the screen goes by itself when the save ends.
        s.back()
        assertFalse(s.askingToLeave.value)
        assertFalse(s.finished.value)
        // Re-review N-1: a tab waits for the save, with no dialog: it opens once the entry is saved, and a failed save
        // keeps the form with its error (the ViewModel takes it with takeLeave, as for «Guardar» of the dialog).
        s.askToLeave { went++ }
        assertEquals(0, went)
        assertFalse(s.askingToLeave.value)
        assertEquals("Banco nuevo", s.title.value)
        s.takeLeave()!!.invoke()
        assertEquals(1, went)
        assertNull(s.takeLeave())
        // «Descartar» of a dialog opened before the save does nothing while it runs.
        s.saving.value = false
        s.back()
        s.saving.value = true
        s.discard()
        assertTrue(s.askingToLeave.value)
        assertFalse(s.finished.value)
        assertEquals("Banco nuevo", s.title.value)
    }

    @Test fun theGuardPassesOnlyAQuestionAndACall() {
        val guard = LeaveGuard()
        var went = 0
        guard.leave { went++ } // No editor: at once.
        assertEquals(1, went)
        val s = session(id = "e1").apply { load(listOf(saved)) }
        val off = s.guardedBy(guard)
        guard.leave { went++ } // An editor without changes: at once.
        assertEquals(2, went)
        s.title.value = "Banco nuevo"
        guard.leave { went++ }
        assertEquals(2, went)
        assertTrue(s.askingToLeave.value)
        s.discard()
        assertEquals(3, went)
        // A second editor registered; the first one's removal leaves it in place.
        val t = session(id = "e1").apply { load(listOf(saved)) }
        val offT = t.guardedBy(guard)
        off()
        t.title.value = "otro"
        guard.leave { went++ }
        assertTrue(t.askingToLeave.value)
        offT()
        guard.leave { went++ }
        assertEquals(4, went)
    }

    // 2.1, QR de wifi (task 1): «Añadir → Red WiFi» reads the network's code and decides in the session.

    private fun newWifi() = session(type = EntryType.WIFI).also { it.load(emptyList()) }

    private fun EntryEditSession.wifi(key: String) = templateValue(EntryType.WIFI, key)

    private fun EntryEditSession.wifiFields() =
        listOf(wifi(WIFI_SSID_KEY), wifi(WIFI_PASSWORD_KEY), wifi(WIFI_SECURITY_KEY), wifi(WIFI_HIDDEN_KEY), title.value)

    private val untouched = listOf("", "", "WPA", "false", "")

    @Test fun scanWifiFillsAnEmptyNewWifi() {
        val s = newWifi()
        s.scanWifi("WIFI:T:WPA2;S:Casa;P:clave1234;H:false;;", simpleMode = false)
        assertEquals("Casa", s.wifi(WIFI_SSID_KEY))
        assertEquals("clave1234", s.wifi(WIFI_PASSWORD_KEY))
        assertEquals("WPA", s.wifi(WIFI_SECURITY_KEY))
        assertEquals("false", s.wifi(WIFI_HIDDEN_KEY))
        assertEquals("Casa", s.title.value)
        assertNull(s.wifiReplace.value)
        assertNull(s.error.value)
        // Nothing is saved: only «Guardar» does, and «Atrás» asks before dropping it.
        assertTrue(s.hasUnsavedChanges)
        assertFalse(s.finished.value)
    }

    @Test fun scanWifiAcceptsWepAndOpen() {
        val wep = newWifi()
        wep.scanWifi("WIFI:T:WEP;S:Viejo;P:12345;H:true;;", simpleMode = false)
        assertEquals("WEP", wep.wifi(WIFI_SECURITY_KEY))
        assertEquals("12345", wep.wifi(WIFI_PASSWORD_KEY))
        assertEquals("true", wep.wifi(WIFI_HIDDEN_KEY))
        val open = newWifi()
        open.scanWifi("WIFI:T:nopass;S:Bar;;", simpleMode = false)
        assertEquals("nopass", open.wifi(WIFI_SECURITY_KEY))
        assertEquals("", open.wifi(WIFI_PASSWORD_KEY))
        assertEquals("Bar", open.wifi(WIFI_SSID_KEY))
        assertNull(open.error.value)
    }

    @Test fun scanWifiRefusesWpa3AndSae() {
        for (t in listOf("WPA3", "SAE")) {
            val s = newWifi()
            s.scanWifi("WIFI:T:$t;S:Casa;P:clave1234;;", simpleMode = false)
            assertEquals(t, qrWifiOtherSecurity(t), s.wifiReadMessage.value)
            assertEquals(t, untouched, s.wifiFields())
            assertNull(t, s.wifiReplace.value)
        }
    }

    @Test fun scanWifiRefusesWhatIsNotWifi() {
        for (simple in listOf(false, true)) {
            val s = newWifi()
            s.scanWifi("https://ejemplo.com", simpleMode = simple)
            assertEquals(if (simple) WIFI_SCAN_NOT_WIFI_SIMPLE else WIFI_SCAN_NOT_WIFI, s.wifiReadMessage.value)
            assertEquals(untouched, s.wifiFields())
            assertNull(s.wifiReplace.value)
        }
    }

    @Test fun scanWifiMalformed() {
        val s = newWifi()
        // The last field has no «;» after it: WifiQr.read gives Malformed (WifiQrTest).
        s.scanWifi("WIFI:S:a;P:b", simpleMode = false)
        assertEquals(QR_WIFI_UNREADABLE, s.wifiReadMessage.value)
        assertEquals(untouched, s.wifiFields())
        assertNull(s.wifiReplace.value)
    }

    @Test fun scanWifiTheOtherReasonsSayWhy() {
        val s = newWifi()
        s.scanWifi("WIFI:T:nopass;S:Casa;P:clave;;", simpleMode = false)
        assertEquals(app.seguridad.tools.qr.QR_WIFI_PASSWORD_WITHOUT_TYPE, s.wifiReadMessage.value)
        assertEquals(untouched, s.wifiFields())
    }

    @Test fun scanWifiAsksBeforeReplacingTyped() {
        val code = "WIFI:T:WEP;S:Casa;P:clave1234;H:true;;"
        val s = newWifi()
        s.setTemplateValue(EntryType.WIFI, WIFI_SSID_KEY, "Mía")
        val before = s.wifiFields()
        s.scanWifi(code, simpleMode = false)
        assertEquals(before, s.wifiFields())
        assertTrue(s.wifiReplace.value!!.typed)
        s.confirmWifiReplace()
        assertEquals(listOf("Casa", "clave1234", "WEP", "true", "Casa"), s.wifiFields())
        assertNull(s.wifiReplace.value)
        assertNull(s.error.value)

        val c = newWifi()
        c.setTemplateValue(EntryType.WIFI, WIFI_PASSWORD_KEY, "escrita")
        val typed = c.wifiFields()
        c.scanWifi(code, simpleMode = false)
        assertNotNull(c.wifiReplace.value)
        c.cancelWifiReplace()
        assertEquals(typed, c.wifiFields())
        assertNull(c.wifiReplace.value)
        // A confirm with nothing pending does nothing.
        c.confirmWifiReplace()
        assertEquals(typed, c.wifiFields())
    }

    @Test fun scanWifiTwiceAsksAsFilled() {
        val s = newWifi()
        s.scanWifi("WIFI:T:WPA;S:Casa;P:clave1234;;", simpleMode = false)
        s.scanWifi("WIFI:T:WPA;S:Otra;P:otra-clave;;", simpleMode = false)
        assertEquals("Casa", s.wifi(WIFI_SSID_KEY))
        assertFalse(s.wifiReplace.value!!.typed)
        // A field changed after the first reading: what is there now was typed.
        s.cancelWifiReplace()
        s.setTemplateValue(EntryType.WIFI, WIFI_HIDDEN_KEY, "true")
        s.scanWifi("WIFI:T:WPA;S:Otra;P:otra-clave;;", simpleMode = false)
        assertTrue(s.wifiReplace.value!!.typed)
        // Confirmed, the title already there stays.
        s.confirmWifiReplace()
        assertEquals("Otra", s.wifi(WIFI_SSID_KEY))
        assertEquals("Casa", s.title.value)
    }

    @Test fun scanWifiAfterClearDoesNothing() {
        val s = newWifi()
        s.clear()
        s.scanWifi("WIFI:T:WPA;S:Casa;P:clave1234;;", simpleMode = false)
        assertTrue(s.templateValues.value.isEmpty())
        assertEquals("", s.title.value)
        assertNull(s.wifiReplace.value)
        assertNull(s.error.value)
        // The next unlock loads an empty new wifi: the last reading is forgotten, so a code fills it without asking.
        s.load(emptyList())
        s.scanWifi("WIFI:T:WPA;S:Casa;P:clave1234;;", simpleMode = false)
        assertEquals("Casa", s.wifi(WIFI_SSID_KEY))
        assertNull(s.wifiReplace.value)
    }

    @Test fun clearDropsAPendingReplace() {
        val s = newWifi()
        s.setTemplateValue(EntryType.WIFI, WIFI_SSID_KEY, "Mía")
        s.scanWifi("WIFI:T:WPA;S:Casa;P:clave1234;;", simpleMode = false)
        assertNotNull(s.wifiReplace.value)
        s.vaultStateIs(unlocked = false, emptyList())
        assertNull(s.wifiReplace.value)
        // A confirm that comes late (the dialog after the lock) fills nothing.
        s.confirmWifiReplace()
        assertTrue(s.templateValues.value.isEmpty())
        // Nor after the next unlock: the pending code went with the lock.
        s.load(emptyList())
        s.confirmWifiReplace()
        assertEquals(untouched, s.wifiFields())
    }

    @Test fun scanWifiOnlyOnANewWifi() {
        val login = session().also { it.load(emptyList()) }
        login.scanWifi("WIFI:T:WPA;S:Casa;P:clave1234;;", simpleMode = false)
        assertEquals(EntryType.LOGIN, login.type.value)
        assertTrue(login.templateValues.value.isEmpty())
        assertNull(login.wifiReplace.value)
        val stored = VaultEntry(id = "w1", type = EntryType.WIFI, title = "Casa", createdAt = 1, updatedAt = 1)
        val edit = session(id = "w1", type = EntryType.WIFI).also { it.load(listOf(stored)) }
        edit.scanWifi("WIFI:T:WPA;S:Otra;P:clave1234;;", simpleMode = false)
        assertEquals("", edit.wifi(WIFI_SSID_KEY))
        assertNull(edit.wifiReplace.value)
    }

    @Test fun wifiReplaceToStringHidesValues() {
        val s = newWifi()
        s.setTemplateValue(EntryType.WIFI, WIFI_SSID_KEY, "Mía")
        s.scanWifi("WIFI:T:WPA;S:RedSecretaNombre;P:clave-muy-secreta;;", simpleMode = false)
        val shown = s.wifiReplace.value.toString()
        assertFalse(shown, "clave-muy-secreta" in shown)
        assertFalse(shown, "RedSecretaNombre" in shown)
    }

    @Test fun aPendingReplaceGoesWhenTheTypeLeavesWifi() {
        val s = newWifi()
        s.setTemplateValue(EntryType.WIFI, WIFI_SSID_KEY, "Mía")
        s.scanWifi("WIFI:T:WPA;S:Casa;P:clave1234;;", simpleMode = false)
        assertNotNull(s.wifiReplace.value)
        s.changeType(EntryType.LOGIN)
        assertNull(s.wifiReplace.value)
        s.confirmWifiReplace()
        assertEquals(EntryType.LOGIN, s.type.value)
        assertEquals("Mía", s.wifi(WIFI_SSID_KEY))
        assertEquals("", s.wifi(WIFI_PASSWORD_KEY))
        assertEquals("", s.title.value)
        assertNull(s.wifiReplace.value)
    }

    @Test fun confirmFillsNothingOnAnotherType() {
        // Even if a replace were pending with the type no longer wifi (the cell set by hand), «Sustituir» fills nothing.
        val s = newWifi()
        s.setTemplateValue(EntryType.WIFI, WIFI_SSID_KEY, "Mía")
        s.scanWifi("WIFI:T:WPA;S:Casa;P:clave1234;;", simpleMode = false)
        val pending = s.wifiReplace.value
        s.type.value = EntryType.LOGIN
        s.wifiReplace.value = pending
        s.confirmWifiReplace()
        assertEquals(EntryType.LOGIN, s.type.value)
        assertEquals("Mía", s.wifi(WIFI_SSID_KEY))
        assertNull(s.wifiReplace.value)
    }

    // 2.1, task 2: a reading counts only for the form it started in (the carried requirement of the review of task 1).
    // Review of the branch, M-3: the start is kept in the session when the camera or the picker opens
    // ([EntryEditSession.beginWifiRead]); the result only takes it back ([EntryEditSession.finishWifiRead]), so it can
    // never take a new one when it arrives.

    @Test fun aReadingStartsOnlyOnALoadedForm() {
        val s = session(type = EntryType.WIFI)
        assertFalse(s.beginWifiRead(WifiReadKind.CAMERA))
        assertNull(s.finishWifiRead(WifiReadKind.CAMERA))
        s.load(emptyList())
        assertTrue(s.beginWifiRead(WifiReadKind.CAMERA))
        assertNotNull(s.finishWifiRead(WifiReadKind.CAMERA))
        s.clear()
        assertFalse(s.beginWifiRead(WifiReadKind.IMAGE))
    }

    @Test fun aResultTakesTheStartOnceAndOnlyItsOwn() {
        val s = newWifi()
        // No reading opened: a result that comes has no start, and takes none.
        assertNull(s.finishWifiRead(WifiReadKind.CAMERA))
        s.beginWifiRead(WifiReadKind.CAMERA)
        assertNull(s.finishWifiRead(WifiReadKind.IMAGE))
        assertNotNull(s.finishWifiRead(WifiReadKind.CAMERA))
        assertNull(s.finishWifiRead(WifiReadKind.CAMERA))
        // A clear drops a reading still open.
        s.beginWifiRead(WifiReadKind.IMAGE)
        s.clear()
        s.load(emptyList())
        assertNull(s.finishWifiRead(WifiReadKind.IMAGE))
        // One that could not open drops its start, and says why under the buttons.
        s.beginWifiRead(WifiReadKind.CAMERA)
        s.wifiReadNotOpened(WifiReadKind.CAMERA, "no se abrió")
        assertNull(s.finishWifiRead(WifiReadKind.CAMERA))
        assertEquals("no se abrió", s.wifiReadMessage.value)
        assertNull(s.error.value)
    }

    @Test fun aReadingStartedBeforeALockIsNotTakenAsANewOne() {
        // The start is the one kept when the camera opened: a lock (and a quick unlock) before the result makes it late,
        // whatever the form is when it arrives.
        var epoch = 0
        val s = EntryEditSession(PlainCells, null, EntryType.WIFI, newId = "nuevo-1", lockEpoch = { epoch }).also { it.load(emptyList()) }
        s.beginWifiRead(WifiReadKind.CAMERA)
        epoch++
        val since = s.finishWifiRead(WifiReadKind.CAMERA)!!
        s.scanWifi("WIFI:T:WPA;S:Casa;P:clave1234;;", simpleMode = false, since = since)
        assertEquals(untouched, s.wifiFields())
    }

    /**
     * 2.1.2, R-8: the 2FA scanner of the form, as a wifi reading: its result counts only in the form it started in. A
     * lock (also one quickly opened again) or a clear before it comes makes it late, and the form takes nothing.
     */
    @Test fun scanResultAfterALockIsDropped() {
        var epoch = 0
        val s = EntryEditSession(PlainCells, null, EntryType.TOTP, newId = "nuevo-1", lockEpoch = { epoch })
        // Not loaded: the scanner does not open, and a result that comes has no start.
        assertFalse(s.beginOtpRead())
        assertNull(s.finishOtpRead())
        s.load(emptyList())
        // In the form it started in: current, once.
        assertTrue(s.beginOtpRead())
        val ok = s.finishOtpRead()!!
        assertTrue(s.otpReadIsCurrent(ok))
        assertNull(s.finishOtpRead())
        // A lock and a quick unlock before the result: late.
        assertTrue(s.beginOtpRead())
        epoch++
        val afterLock = s.finishOtpRead()!!
        assertFalse(s.otpReadIsCurrent(afterLock))
        // A clear (the lock clears the form) drops a reading still open, and one taken before it is late.
        assertTrue(s.beginOtpRead())
        val beforeClear = s.finishOtpRead()!!
        assertTrue(s.beginOtpRead())
        s.clear()
        assertFalse(s.otpReadIsCurrent(beforeClear))
        s.load(emptyList())
        assertNull(s.finishOtpRead())
        assertFalse(s.otpReadIsCurrent(beforeClear))
        // One that could not open drops its start.
        assertTrue(s.beginOtpRead())
        s.otpReadNotOpened()
        assertNull(s.finishOtpRead())
    }

    private fun EntryEditSession.read(kind: WifiReadKind = WifiReadKind.CAMERA): WifiReadStart {
        assertTrue(beginWifiRead(kind))
        return finishWifiRead(kind)!!
    }

    @Test fun aReadingFillsTheFormItStartedIn() {
        val s = newWifi()
        val since = s.read()
        s.scanWifi("WIFI:T:WPA;S:Casa;P:clave1234;;", simpleMode = false, since = since)
        assertEquals("Casa", s.wifi(WIFI_SSID_KEY))
        val failed = s.read(WifiReadKind.IMAGE)
        s.wifiReadFailed("no se pudo", failed)
        assertEquals("no se pudo", s.wifiReadMessage.value)
        // Never mixed with «Guardar»'s error.
        assertNull(s.error.value)
    }

    @Test fun aLateReadingAfterALockAndAQuickUnlockFillsNothing() {
        var epoch = 3
        val s = EntryEditSession(PlainCells, null, EntryType.WIFI, newId = "nuevo-1", lockEpoch = { epoch }).also { it.load(emptyList()) }
        val since = s.read()
        // The lock clears the form; the next unlock loads a new, empty one before the result lands.
        epoch++
        s.vaultStateIs(unlocked = false, emptyList())
        s.vaultStateIs(unlocked = true, emptyList())
        assertTrue(s.loaded)
        s.scanWifi("WIFI:T:WPA;S:Casa;P:clave1234;;", simpleMode = false, since = since)
        s.wifiReadFailed("tarde", since)
        assertEquals(untouched, s.wifiFields())
        assertNull(s.wifiReplace.value)
        assertNull(s.wifiReadMessage.value)
        assertNull(s.error.value)
    }

    @Test fun aLateReadingFillsNothingEvenIfTheLockWasNotSeen() {
        // The collector may see only the last state (a lock and an unlock conflated): the vault's lock epoch still moved.
        var epoch = 0
        val s = EntryEditSession(PlainCells, null, EntryType.WIFI, newId = "nuevo-1", lockEpoch = { epoch }).also { it.load(emptyList()) }
        val since = s.read()
        epoch++
        s.scanWifi("WIFI:T:WPA;S:Casa;P:clave1234;;", simpleMode = false, since = since)
        s.wifiReadFailed("tarde", since)
        assertEquals(untouched, s.wifiFields())
        assertNull(s.wifiReadMessage.value)
    }

    @Test fun aLateReadingAfterLeavingTheFormFillsNothing() {
        // Left (not a rotation) and back: a new form, the same session and the same lock epoch.
        val s = newWifi()
        val since = s.read()
        s.left(changingConfigurations = false)
        s.load(emptyList())
        s.scanWifi("WIFI:T:WPA;S:Casa;P:clave1234;;", simpleMode = false, since = since)
        assertEquals(untouched, s.wifiFields())
        // A rotation keeps the form and the reading opened before it.
        assertTrue(s.beginWifiRead(WifiReadKind.CAMERA))
        s.left(changingConfigurations = true)
        val kept = s.finishWifiRead(WifiReadKind.CAMERA)!!
        s.scanWifi("WIFI:T:WPA;S:Casa;P:clave1234;;", simpleMode = false, since = kept)
        assertEquals("Casa", s.wifi(WIFI_SSID_KEY))
    }

    @Test fun aReadingStartToStringShowsNothing() {
        assertEquals("WifiReadStart", newWifi().read().toString())
    }

    // Review of task 2 (minors 1 and 2): the message clears and is shown only for the form the reading started in.

    @Test fun aStaleImageReadLeavesAFreshFormsMessageAlone() {
        val s = newWifi()
        val since = s.read(WifiReadKind.IMAGE)
        s.clear()
        s.load(emptyList())
        s.wifiReadMessage.value = "mensaje del formulario nuevo"
        // M-2: a stale start says so, and the picker's answer goes without reading the image.
        assertFalse(s.wifiReadBegan(since))
        assertEquals("mensaje del formulario nuevo", s.wifiReadMessage.value)
        // The current reading does clear it (already when it opens), and «Guardar»'s error stays.
        s.error.value = "error de Guardar"
        val now = s.read(WifiReadKind.IMAGE)
        assertNull(s.wifiReadMessage.value)
        s.wifiReadMessage.value = "otra"
        assertTrue(s.wifiReadBegan(now))
        assertNull(s.wifiReadMessage.value)
        assertEquals("error de Guardar", s.error.value)
    }

    @Test fun aFailedReadSaysNothingOnceTheTypeLeftWifi() {
        val s = newWifi()
        val since = s.read(WifiReadKind.IMAGE)
        s.changeType(EntryType.LOGIN)
        s.wifiReadFailed("no se pudo", since)
        assertFalse(s.wifiReadBegan(since))
        assertNull(s.wifiReadMessage.value)
    }

    // Review of the branch, I-2: the reading's message has its own cell, shown under «Escanear QR» and «Elegir imagen».

    @Test fun theReadingsMessageGoesWithAFillAReadAndAClear() {
        val s = newWifi()
        s.scanWifi("https://ejemplo.com", simpleMode = false)
        assertEquals(WIFI_SCAN_NOT_WIFI, s.wifiReadMessage.value)
        assertNull(s.error.value)
        s.scanWifi("WIFI:T:WPA;S:Casa;P:clave1234;;", simpleMode = false)
        assertNull(s.wifiReadMessage.value)
        s.scanWifi("https://ejemplo.com", simpleMode = false)
        s.beginWifiRead(WifiReadKind.CAMERA)
        assertNull(s.wifiReadMessage.value)
        s.wifiReadMessage.value = "algo"
        s.clear()
        assertNull(s.wifiReadMessage.value)
        // Leaving wifi drops it too, so it is not there if the type comes back.
        s.load(emptyList())
        s.scanWifi("https://ejemplo.com", simpleMode = false)
        s.changeType(EntryType.LOGIN)
        assertNull(s.wifiReadMessage.value)
    }

    // Review of the branch, I-1: a form reached as wifi through the row of types, untouched, is filled without asking.

    @Test fun anUntouchedWifiReachedFromAnotherTypeIsFilledWithoutAsking() {
        for (from in listOf(EntryType.LOGIN, EntryType.TOTP)) {
            val s = session(type = from).also { it.load(emptyList()) }
            s.changeType(EntryType.WIFI)
            s.scanWifi("WIFI:T:WEP;S:Casa;P:clave1234;H:true;;", simpleMode = false)
            assertNull("$from", s.wifiReplace.value)
            assertEquals("$from", listOf("Casa", "clave1234", "WEP", "true", "Casa"), s.wifiFields())
        }
    }

    @Test fun aWifiReachedFromAnotherTypeWithSomethingTypedStillAsks() {
        val s = session(type = EntryType.LOGIN).also { it.load(emptyList()) }
        s.changeType(EntryType.WIFI)
        s.setTemplateValue(EntryType.WIFI, WIFI_PASSWORD_KEY, "escrita")
        s.scanWifi("WIFI:T:WPA;S:Casa;P:clave1234;;", simpleMode = false)
        assertTrue(s.wifiReplace.value!!.typed)
        assertEquals("escrita", s.wifi(WIFI_PASSWORD_KEY))
        // A security other than WPA, or hidden on, counts as typed too.
        for ((key, value) in listOf(WIFI_SECURITY_KEY to "WEP", WIFI_HIDDEN_KEY to "true")) {
            val t = session(type = EntryType.LOGIN).also { it.load(emptyList()) }
            t.changeType(EntryType.WIFI)
            t.setTemplateValue(EntryType.WIFI, key, value)
            t.scanWifi("WIFI:T:WPA;S:Casa;P:clave1234;;", simpleMode = false)
            assertNotNull(key, t.wifiReplace.value)
        }
    }

    // Review of the branch, M-4: R-1 of the Abogado Digital in the form. The bidi mark is past the 31 characters kept
    // before «…», so the cut leaves it out whole.

    @Test fun aLongTypeOfSecurityWithABidiMarkIsCutInTheForm() {
        val s = newWifi()
        s.scanWifi("WIFI:T:${"A".repeat(100)}‮;S:Casa;P:clave1234;;", simpleMode = false)
        assertEquals(qrWifiOtherSecurity("A".repeat(31) + "…"), s.wifiReadMessage.value)
        assertEquals(untouched, s.wifiFields())
    }

    // Review of the branch, M-5: a name of a network with characters that are not seen gets the inspector's alert.

    @Test fun aNameWithHiddenCharactersGetsTheInspectorsAlert() {
        val s = newWifi()
        assertNull(s.wifiNameAlert())
        s.scanWifi("WIFI:T:WPA;S:Ca​sa‮;P:clave1234;;", simpleMode = false)
        // N-I1 of the director's recheck: the alert says «marcados como …», so the name comes with its marks to show.
        assertEquals("Ca\u27E8U+200B\u27E9sa\u27E8U+202E\u27E9" to qrBidi("\u27E8U+200B\u27E9, \u27E8U+202E\u27E9"), s.wifiNameAlert())
        // It follows the field: written again without them, it goes.
        s.setTemplateValue(EntryType.WIFI, WIFI_SSID_KEY, "Casa")
        assertNull(s.wifiNameAlert())
        // An emoji with its presentation selector is not hidden (as in the inspector).
        s.setTemplateValue(EntryType.WIFI, WIFI_SSID_KEY, "Casa ❤️")
        assertNull(s.wifiNameAlert())
        // Also when editing a saved wifi (RT-24).
        val stored = VaultEntry(id = "w1", type = EntryType.WIFI, title = "Casa", createdAt = 1, updatedAt = 1)
        val edit = session(id = "w1", type = EntryType.WIFI).also { it.load(listOf(stored)) }
        edit.setTemplateValue(EntryType.WIFI, WIFI_SSID_KEY, "Ca\u200Bsa")
        assertEquals("Ca\u27E8U+200B\u27E9sa" to qrBidi("\u27E8U+200B\u27E9"), edit.wifiNameAlert())
        // Only on a wifi.
        s.setTemplateValue(EntryType.WIFI, WIFI_SSID_KEY, "Ca​sa")
        s.changeType(EntryType.LOGIN)
        assertNull(s.wifiNameAlert())
    }
}
