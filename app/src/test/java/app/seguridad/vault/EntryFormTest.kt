package app.seguridad.vault

import app.seguridad.tools.codes.PlainCells
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The editor keeps one password per type: switching type never carries a login password into a seed passphrase. */
class EntryFormTest {
    private fun entry(type: EntryType, password: String) =
        VaultEntry(id = "x", type = type, title = "t", password = password, createdAt = 1, updatedAt = 1)

    @Test
    fun aNewEntryStartsWithBothFieldsEmpty() {
        assertEquals("", initialPassword(null, EntryType.LOGIN))
        assertEquals("", initialPassword(null, EntryType.SEED))
    }

    @Test
    fun switchingFromLoginToSeedDoesNotCarryThePassword() {
        // The user generates a password in «Acceso», then picks «Frase semilla» before saving.
        val loginPassword = "Xk9#generada-en-acceso"
        val seedPassphrase = ""
        assertEquals("", passwordToSave(EntryType.SEED, loginPassword, seedPassphrase))
        assertEquals(loginPassword, passwordToSave(EntryType.LOGIN, loginPassword, seedPassphrase))
    }

    @Test
    fun switchingFromSeedToLoginDoesNotCarryThePassphrase() {
        assertEquals("", passwordToSave(EntryType.LOGIN, "", "mi passphrase"))
        assertEquals("mi passphrase", passwordToSave(EntryType.SEED, "", "mi passphrase"))
    }

    @Test
    fun editingAnExistingSeedKeepsItsPassphrase() {
        val seed = entry(EntryType.SEED, "passphrase-bip39")
        val login = initialPassword(seed, EntryType.LOGIN)
        val passphrase = initialPassword(seed, EntryType.SEED)
        assertEquals("", login)
        assertEquals("passphrase-bip39", passphrase)
        assertEquals("passphrase-bip39", passwordToSave(EntryType.SEED, login, passphrase))
    }

    @Test
    fun editingAnExistingLoginKeepsItsPassword() {
        val existing = entry(EntryType.LOGIN, "secreta")
        assertEquals("secreta", initialPassword(existing, EntryType.LOGIN))
        assertEquals("", initialPassword(existing, EntryType.SEED))
    }

    @Test
    fun noteAndTotpSaveNoPassword() {
        assertEquals("", passwordToSave(EntryType.NOTE, "a", "b"))
        assertEquals("", passwordToSave(EntryType.TOTP, "a", "b"))
        // An old entry of another type never seeds either field.
        assertEquals("", initialPassword(entry(EntryType.NOTE, "resto"), EntryType.LOGIN))
        assertEquals("", initialPassword(entry(EntryType.TOTP, "resto"), EntryType.SEED))
    }

    // 2.0, task 5: the fields of the templates and the user's own.

    private fun session(id: String? = null, type: EntryType = EntryType.LOGIN) = EntryEditSession(PlainCells, id, type, newId = "nuevo-1")

    private fun source(path: String): String {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val f = File(dir, "app/src/main/java/app/seguridad/$path")
            if (f.isFile) return f.readText()
            dir = dir.parentFile
        }
        error("$path not found")
    }

    /**
     * A hidden field is a secret (CLAUDE.md): what was typed in one, or loaded from the vault, lives only in the
     * session the editor's ViewModel holds ([EntryEditSession], in memory), goes with every [EntryEditSession.clear]
     * (a lock, leaving, saving), and nothing of the editor or of the card is ever in saved state (`rememberSaveable`,
     * a `Bundle` or a `SavedStateHandle`), so the death of the process never writes one out.
     */
    @Test fun hiddenFieldsNeverInSaveable() {
        val card = VaultEntry(
            id = "c1", type = EntryType.CARD, title = "Tarjeta", createdAt = 1, updatedAt = 1,
            fields = listOf(
                CustomField("number", "Número", FieldKind.HIDDEN, "4000 0000 0000 0002"),
                CustomField("cvv", "CVV", FieldKind.HIDDEN, "123"),
                CustomField("f1", "Clave del buzón", FieldKind.HIDDEN, "buzon-secreto"),
                CustomField("expiry", "Caducidad", FieldKind.DATE, "2029-05-31", expires = true),
            ),
        )
        val s = session(id = "c1", type = EntryType.CARD)
        s.load(listOf(card))
        assertEquals("4000 0000 0000 0002", s.templateValue(EntryType.CARD, "number"))
        // Review M-3: the card's expiry as it is asked, MM/AA.
        assertEquals("05/29", s.templateValue(EntryType.CARD, "expiry"))
        assertEquals(listOf("buzon-secreto"), s.customFields.value.map { it.value })
        s.setTemplateValue(EntryType.CARD, "pin", "9876")
        s.vaultStateIs(unlocked = false, emptyList())
        assertTrue(s.templateValues.value.isEmpty())
        assertTrue(s.customFields.value.isEmpty())
        assertEquals(NotesFormat.PLAIN, s.notesFormat.value)
        assertEquals("", s.expiresText.value)
        assertNull(s.rotateMonths.value)
        // Loaded again from the vault, never from what was typed.
        s.vaultStateIs(unlocked = true, listOf(card))
        assertEquals("", s.templateValue(EntryType.CARD, "pin"))
        // Nothing of the editor, the card, its fields or the notes in saved state.
        val screens = listOf("ui/screens/VaultEntryScreen.kt", "ui/screens/EntryFields.kt", "ui/screens/MarkupText.kt").joinToString("\n") { source(it) }
        for (saved in listOf("rememberSaveable", "SavedStateHandle", "Bundle", "onSaveInstanceState")) assertFalse(saved, saved in screens)
        // The session's cells are plain state in the ViewModel (ComposeCells), never saved.
        val cells = source("ui/screens/ScreenGuardWiring.kt")
        assertTrue("private val state = mutableStateOf(initial)" in cells)
        assertFalse("rememberSaveable" in cells)
    }

    @Test fun thirtyOneFieldsRefused() {
        val s = session()
        s.load(emptyList())
        repeat(VaultLimits.FIELDS_MAX) { i -> assertNull("$i", s.addField("Campo $i", FieldKind.TEXT, expires = false)) }
        assertEquals(TOO_MANY_FIELDS, s.addField("El 31", FieldKind.TEXT, expires = false))
        assertEquals(VaultLimits.FIELDS_MAX, s.customFields.value.size)
        // A template's own fields count: a card leaves room for 25 of the user's.
        val c = session(type = EntryType.CARD)
        c.load(emptyList())
        repeat(VaultLimits.FIELDS_MAX - 5) { i -> assertNull("$i", c.addField("Campo $i", FieldKind.TEXT, expires = false)) }
        assertEquals(TOO_MANY_FIELDS, c.addField("Uno más", FieldKind.TEXT, expires = false))
        // What is saved is checked too, whatever the session let in (a copy made elsewhere, a type changed after adding).
        val many = (0..VaultLimits.FIELDS_MAX).map { CustomField("f$it", "Campo $it", FieldKind.TEXT, "v") }
        assertEquals(FormFields.Problem(TOO_MANY_FIELDS), fieldsToSave(EntryType.LOGIN, emptyMap(), many))
        assertTrue(fieldsToSave(EntryType.LOGIN, emptyMap(), many.take(VaultLimits.FIELDS_MAX)) is FormFields.Ok)
        // And the other limits of a field, with their fixed texts.
        assertEquals(FormFields.Problem(FIELD_NAME_TOO_LONG), fieldsToSave(EntryType.LOGIN, emptyMap(), listOf(CustomField("f", "n".repeat(101), FieldKind.TEXT, ""))))
        assertEquals(FormFields.Problem(FIELD_VALUE_TOO_LONG), fieldsToSave(EntryType.LOGIN, emptyMap(), listOf(CustomField("f", "n", FieldKind.TEXT, "v".repeat(10_001)))))
        assertEquals(FIELD_NAME_TOO_LONG, s.addField("n".repeat(101), FieldKind.TEXT, expires = false).also { s.removeField(0) })
        assertEquals(FIELD_NEEDS_NAME, session().apply { load(emptyList()) }.addField("  ", FieldKind.TEXT, expires = false))
        assertEquals(FormFields.Problem(FIELD_NEEDS_NAME), fieldsToSave(EntryType.LOGIN, emptyMap(), listOf(CustomField("f", " ", FieldKind.TEXT, "v"))))
        // A date must be one, and a number a number; both say which field.
        assertEquals(FormFields.Problem(fieldBadDate("Revisión")), fieldsToSave(EntryType.LOGIN, emptyMap(), listOf(CustomField("f", "Revisión", FieldKind.DATE, "mañana"))))
        assertEquals(FormFields.Problem(fieldBadNumber("Planta")), fieldsToSave(EntryType.LOGIN, emptyMap(), listOf(CustomField("f", "Planta", FieldKind.NUMBER, "tres"))))
        val ok = (fieldsToSave(EntryType.LOGIN, emptyMap(), listOf(CustomField("f", "Revisión", FieldKind.DATE, "1/2/2027", expires = true), CustomField("g", "Planta", FieldKind.NUMBER, "-3,5"))) as FormFields.Ok).fields
        assertEquals(listOf("2027-02-01", "-3,5"), ok.map { it.value })
        // Order: moved up and down, and removed.
        val o = session()
        o.load(emptyList())
        for (n in listOf("A", "B", "C")) o.addField(n, FieldKind.TEXT, expires = false)
        o.moveField(2, -1)
        assertEquals(listOf("A", "C", "B"), o.customFields.value.map { it.name })
        o.moveField(0, -1) // Already first: nothing.
        o.moveField(2, 1) // Already last: nothing.
        o.removeField(0)
        assertEquals(listOf("C", "B"), o.customFields.value.map { it.name })
        assertEquals(2, o.customFields.value.map { it.id }.toSet().size)
    }

    /**
     * A vault saved before the copies were checked may still repeat an id (review I-1 of task 5): each row is one field,
     * and a field that carries the id of a template's secret never becomes a visible one: it stays hidden.
     */
    @Test fun fieldsFromACopyLoadOneRowEach() {
        val e = VaultEntry(
            id = "w", type = EntryType.WIFI, title = "Casa", createdAt = 1, updatedAt = 1, rotateMonths = 9_999,
            notesFormat = NotesFormat.LIMITED, expiresAt = expiresAtOf(java.time.LocalDate.parse("2027-01-31")),
            fields = listOf(
                CustomField("password", "Contraseña", FieldKind.TEXT, "primera"),
                CustomField("password", "Contraseña", FieldKind.TEXT, "segunda"),
                CustomField("x", "Uno", FieldKind.TEXT, "1"),
                CustomField("x", "Dos", FieldKind.TEXT, "2"),
            ),
        )
        var n = 0
        val s = EntryEditSession(PlainCells, "w", EntryType.WIFI, newId = "nuevo-1", newFieldId = { "id-${n++}" })
        s.load(listOf(e))
        assertEquals("primera", s.templateValue(EntryType.WIFI, "password"))
        assertEquals(listOf("id-0", "x", "id-1"), s.customFields.value.map { it.id })
        assertEquals(FieldKind.HIDDEN, s.customFields.value[0].kind) // It carried the id of the wifi's password.
        assertEquals(listOf(FieldKind.TEXT, FieldKind.TEXT), s.customFields.value.drop(1).map { it.kind })
        assertEquals(NotesFormat.LIMITED, s.notesFormat.value)
        assertEquals("31/01/2027", s.expiresText.value)
        assertNull(s.rotateMonths.value) // Out of 1 to 120: no reminder.
        // Saved: the template's password is hidden whatever the copy said, and so is the second one, now one of the user's.
        val saved = (fieldsToSave(EntryType.WIFI, s.templateValues.value, s.customFields.value) as FormFields.Ok).fields
        assertEquals(listOf("password", "id-0", "x", "id-1"), saved.map { it.id })
        assertEquals(listOf(FieldKind.HIDDEN, FieldKind.HIDDEN, FieldKind.TEXT, FieldKind.TEXT), saved.map { it.kind })
        assertEquals(Result.success(e.expiresAt), expiresAtToSave(s.expiresText.value))
        assertTrue(expiresAtToSave("mañana").isFailure)
        assertEquals(Result.success(null), expiresAtToSave(""))
        // A new wifi starts as WPA and not hidden.
        val w = session(type = EntryType.WIFI).apply { load(emptyList()) }
        assertEquals("WPA", w.templateValue(EntryType.WIFI, "security"))
        assertEquals("false", w.templateValue(EntryType.WIFI, "hidden"))
    }

    /**
     * Review I-3 of task 5: each row of the editor has its own key, which follows its field when one is removed or moved
     * and is never the field's id alone nor used twice in a session, so a «Ver» never passes from one secret to another.
     */
    @Test fun rowKeysFollowTheirFields() {
        val s = session()
        s.load(emptyList())
        for (n in listOf("A", "B", "C")) assertNull(s.addField(n, FieldKind.HIDDEN, expires = false))
        val k = (0..2).map { s.rowKey(it) }
        assertEquals(3, k.toSet().size)
        assertTrue(k.none { key -> s.customFields.value.any { it.id == key } })
        s.removeField(0)
        assertEquals(k.drop(1), (0..1).map { s.rowKey(it) })
        s.moveField(1, -1)
        assertEquals(listOf(k[2], k[1]), (0..1).map { s.rowKey(it) })
        s.setFieldValue(0, "x")
        s.setFieldExpires(0, true) // Not a date: nothing.
        assertEquals(listOf(k[2], k[1]), (0..1).map { s.rowKey(it) })
        assertNull(s.addField("D", FieldKind.DATE, expires = false))
        assertTrue(s.rowKey(2) !in k)
        s.setFieldExpires(2, true)
        assertTrue(s.customFields.value[2].expires)
        val before = (0..2).map { s.rowKey(it) }
        // A lock and a new load: new keys, never one of before (nothing composed keeps a «Ver» of another field).
        s.vaultStateIs(unlocked = false, emptyList())
        s.vaultStateIs(unlocked = true, listOf(VaultEntry("e", EntryType.LOGIN, "t", createdAt = 1, updatedAt = 1, fields = listOf(CustomField("f", "F", FieldKind.HIDDEN, "1")))))
        assertTrue(s.rowKey(0) !in before + k)
        // The editor composes each row under its key: the template's by type and key, the user's by this one.
        val ui = source("ui/screens/EntryFields.kt")
        assertTrue("key(type, t.key) {" in ui)
        assertTrue("key(form.rowKey(i)) {" in ui)
        // And the card under the field's place and id, never the id alone.
        assertTrue("key(\"c\", i, f.id) {" in ui && "key(\"t\", t.key) {" in ui)
        assertFalse(Regex("""key\(f\.id\)""").containsMatchIn(ui))
    }

    /**
     * Ruling T5, ronda 1: the editor of a card, licence or passport offers no date of its own (M-4), so an `expiresAt` a
     * copy brought goes to the template's empty expiry when the form loads, and is saved there: never lost on a save.
     */
    @Test fun aCopysExpiryGoesToTheTemplatesField() {
        val day = expiresAtOf(java.time.LocalDate.parse("2027-05-31"))
        val card = VaultEntry("c", EntryType.CARD, "Tarjeta", createdAt = 1, updatedAt = 1, expiresAt = day)
        val s = session(id = "c", type = EntryType.CARD)
        s.load(listOf(card))
        assertEquals("05/27", s.templateValue(EntryType.CARD, "expiry"))
        val saved = (fieldsToSave(EntryType.CARD, s.templateValues.value, s.customFields.value) as FormFields.Ok).fields
        assertEquals(listOf(CustomField("expiry", "Caducidad", FieldKind.DATE, "2027-05-31", expires = true)), saved)
        // Another day of a month, in a passport: as a full date.
        val p = session(id = "p", type = EntryType.PASSPORT)
        p.load(listOf(VaultEntry("p", EntryType.PASSPORT, "Pasaporte", createdAt = 1, updatedAt = 1, expiresAt = expiresAtOf(java.time.LocalDate.parse("2030-03-15")))))
        assertEquals("15/03/2030", p.templateValue(EntryType.PASSPORT, "expiry"))
        // With both, the template's wins and the entry's goes (one expiry per entry).
        val both = VaultEntry(
            "b", EntryType.LICENSE, "Licencia", createdAt = 1, updatedAt = 1, expiresAt = day,
            fields = listOf(CustomField("expiry", "Caducidad", FieldKind.DATE, "2028-01-10", expires = true)),
        )
        val b = session(id = "b", type = EntryType.LICENSE)
        b.load(listOf(both))
        assertEquals("10/01/2028", b.templateValue(EntryType.LICENSE, "expiry"))
        // A login keeps its own date in its own field.
        val l = session(id = "l")
        l.load(listOf(VaultEntry("l", EntryType.LOGIN, "Banco", createdAt = 1, updatedAt = 1, expiresAt = day)))
        assertEquals("31/05/2027", l.expiresText.value)
        assertEquals("", l.templateValue(EntryType.LOGIN, "expiry"))
    }

    /**
     * Re-review N-2: «Caduca el» typed on a new login, then the type changed to one whose template has its expiry: the
     * date goes to the template's field if it is empty (nothing lost silently), and the hidden «Caduca el» is never
     * read when saving (no error on a field not on screen).
     */
    @Test fun aTypedExpiryFollowsTheTypeChange() {
        val s = session()
        s.load(emptyList())
        s.expiresText.value = "15/03/2030"
        s.changeType(EntryType.PASSPORT)
        assertEquals(EntryType.PASSPORT, s.type.value)
        assertEquals("15/03/2030", s.templateValue(EntryType.PASSPORT, "expiry"))
        // A template's own date already typed wins.
        s.setTemplateValue(EntryType.CARD, "expiry", "05/29")
        s.changeType(EntryType.CARD)
        assertEquals("05/29", s.templateValue(EntryType.CARD, "expiry"))
        // Back to a type with its own date: «Caduca el» is still what was typed.
        s.changeType(EntryType.LOGIN)
        assertEquals("15/03/2030", s.expiresText.value)
        // When saving, only a type with its own date reads «Caduca el».
        assertEquals(Result.success(null), entryExpiresAt(EntryType.CARD, "mañana"))
        assertEquals(Result.success(null), entryExpiresAt(EntryType.LICENSE, "15/03/2030"))
        assertTrue(entryExpiresAt(EntryType.LOGIN, "mañana").isFailure)
        assertEquals(Result.success(expiresAtOf(java.time.LocalDate.parse("2030-03-15"))), entryExpiresAt(EntryType.WIFI, "15/03/2030"))
        // The screen saves through it, and changes the type through the session.
        val ui = source("ui/screens/VaultEntryScreen.kt")
        assertTrue("val expiresAt = entryExpiresAt(type, form.expiresText.value).getOrElse {" in ui)
        assertTrue("onClick = { form.changeType(t) }" in ui)
    }
}
