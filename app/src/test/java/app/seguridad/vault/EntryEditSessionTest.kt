package app.seguridad.vault

import app.seguridad.crypto.TotpSecret
import app.seguridad.tools.codes.PlainCells
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
