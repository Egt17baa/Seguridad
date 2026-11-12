package app.seguridad.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * 2.0, task 5 (design «Caducidad»): what expires within the next days, or has already, counted from the entry's own date
 * ([VaultEntry.expiresAt]), its date fields marked «caduca» (kept as `AAAA-MM-DD`) and «cambiar la contraseña cada N
 * meses» from the last change of the password. Only inside the app: no notification, no permission (O-4).
 */
class ExpiryTest {
    private val utc = ZoneOffset.UTC
    private fun day(iso: String) = LocalDate.parse(iso).atStartOfDay(utc).toInstant().toEpochMilli()
    private val now = day("2026-11-12") + 10 * 3_600_000L

    private fun entry(
        id: String,
        type: EntryType = EntryType.LOGIN,
        password: String = "",
        expiresAt: Long? = null,
        fields: List<CustomField> = emptyList(),
        rotateMonths: Int? = null,
        createdAt: Long = day("2026-01-01"),
        history: List<EntryVersion> = emptyList(),
        deletedAt: Long? = null,
    ) = VaultEntry(
        id = id, type = type, title = id, password = password, createdAt = createdAt, updatedAt = createdAt, expiresAt = expiresAt,
        fields = fields, rotateMonths = rotateMonths, history = history, deletedAt = deletedAt,
    )

    private fun due(vararg e: VaultEntry, days: Int = 30) = Expiry.dueSoon(e.toList(), now, days, utc).map { it.id }

    @Test fun dueIn30Days() {
        val dateField = { iso: String, expires: Boolean -> listOf(CustomField("expiry", "Caducidad", FieldKind.DATE, iso, expires)) }
        val result = due(
            entry("ya", expiresAt = day("2026-10-01")), // Already expired: it is still shown.
            entry("hoy", expiresAt = day("2026-11-12")),
            entry("dentro", expiresAt = day("2026-12-12")), // The 30th day.
            entry("fuera", expiresAt = day("2026-12-13")),
            entry("campo", type = EntryType.CARD, fields = dateField("2026-11-30", true)),
            entry("campoLejos", type = EntryType.PASSPORT, fields = dateField("2031-11-30", true)),
            // A date that is not marked «caduca» (a purchase date) is no notice.
            entry("compra", type = EntryType.LICENSE, fields = listOf(CustomField("purchased", "Fecha de compra", FieldKind.DATE, "2026-11-20"))),
            entry("nada"),
        )
        assertEquals(listOf("ya", "hoy", "dentro", "campo"), result)
        // Other windows.
        assertEquals(listOf("ya", "hoy"), due(entry("ya", expiresAt = day("2026-10-01")), entry("hoy", expiresAt = day("2026-11-12")), entry("dentro", expiresAt = day("2026-12-12")), days = 0))
        // The earliest date is the one the card shows, with what it is.
        val two = entry("dos", expiresAt = day("2027-03-01"), fields = dateField("2026-12-01", true))
        assertEquals(ExpiryItem("Caducidad", LocalDate.parse("2026-12-01")), Expiry.next(two, utc))
        assertEquals(LocalDate.parse("2027-03-01"), Expiry.items(two, utc).first { it.kind == ExpiryKind.ENTRY }.date)
        // The list's filter «Caducan pronto» reads the same.
        val data = VaultData(entries = listOf(two, entry("fuera", expiresAt = day("2027-12-13"))))
        assertEquals(listOf("dos"), data.entries.query(EntryFilter(expiringSoon = true), data, now, weak = { false }).map { it.id })
    }

    @Test fun rotateMonthsCounts() {
        // Created on 2026-05-01 and never changed: «cada 6 meses» falls on 2026-11-01, already due.
        assertEquals(listOf("seis"), due(entry("seis", password = "clave", rotateMonths = 6, createdAt = day("2026-05-01"))))
        // Changed on 2026-10-01 (the version kept then had another password): due on 2027-04-01, not yet.
        val changed = entry(
            "cambiada", password = "nueva", rotateMonths = 6, createdAt = day("2026-01-01"),
            history = listOf(
                EntryVersion(day("2026-10-01"), entry("cambiada", password = "vieja")),
                EntryVersion(day("2026-02-01"), entry("cambiada", password = "vieja")),
            ),
        )
        assertEquals(emptyList<String>(), due(changed))
        assertEquals(LocalDate.parse("2027-04-01"), Expiry.next(changed, utc)?.date)
        assertEquals(EXPIRY_ROTATE, Expiry.next(changed, utc)?.what)
        // A version that only changed the notes does not restart the count: the password was set at the version before.
        val notesOnly = changed.copy(history = listOf(EntryVersion(day("2026-10-20"), entry("cambiada", password = "nueva"))) + changed.history)
        assertEquals(LocalDate.parse("2027-04-01"), Expiry.next(notesOnly, utc)?.date)
        // The list of 1.4.3 counts too (a change older than the history kept).
        val legacy = entry("legado", password = "nueva", rotateMonths = 1, createdAt = day("2025-01-01")).copy(passwordHistory = listOf(PasswordChange("vieja", day("2026-11-01"))))
        assertEquals(LocalDate.parse("2026-12-01"), Expiry.next(legacy, utc)?.date)
        // The wifi's password is its hidden field.
        val wifi = entry("wifi", type = EntryType.WIFI, rotateMonths = 12, createdAt = day("2025-10-01"), fields = listOf(CustomField("password", "Contraseña", FieldKind.HIDDEN, "clave-wifi")))
        assertEquals(listOf("wifi"), due(wifi))
        // No password, nothing to change; a number of months out of 1 to 120 (a copy made elsewhere) is no reminder.
        assertEquals(emptyList<String>(), due(entry("vacia", rotateMonths = 1, createdAt = day("2020-01-01"))))
        for (m in listOf(0, -3, 121, Int.MAX_VALUE, Int.MIN_VALUE)) {
            assertEquals("$m", emptyList<String>(), due(entry("raro", password = "x", rotateMonths = m, createdAt = day("2020-01-01"))))
        }
    }

    @Test fun trashedNotDue() {
        val trashed = entry("papelera", expiresAt = day("2026-11-01"), deletedAt = day("2026-11-10"))
        assertEquals(emptyList<String>(), due(trashed))
        // Nor the identity or a contact, which have their own tab.
        assertEquals(emptyList<String>(), due(entry("yo", type = EntryType.IDENTITY, expiresAt = day("2026-11-01"))))
        assertEquals(listOf("vivo"), due(trashed, entry("vivo", expiresAt = day("2026-11-01"))))
    }

    @Test fun badDateIgnored() {
        val bad = listOf("2026-13-01", "2026-02-30", "mañana", "", "26-11-01", "2026-1-1", "２０２６-１１-０１", "2026-11-01T00:00", "x".repeat(10_000))
        for (v in bad) {
            val e = entry("mala", type = EntryType.CARD, fields = listOf(CustomField("expiry", "Caducidad", FieldKind.DATE, v, expires = true)))
            assertEquals(v.take(20), emptyList<String>(), due(e))
            assertNull(v.take(20), parseFieldDate(v))
        }
        // A «caduca» on a field that is not a date is no notice either.
        assertEquals(emptyList<String>(), due(entry("texto", fields = listOf(CustomField("x", "X", FieldKind.TEXT, "2026-11-01", expires = true)))))
        // An entry date far out of range (a copy made elsewhere) neither throws nor counts.
        assertEquals(emptyList<String>(), due(entry("lejos", expiresAt = Long.MAX_VALUE), entry("antes", expiresAt = Long.MIN_VALUE)))
        // What the editor accepts and keeps as AAAA-MM-DD; anything else it refuses.
        assertEquals("2026-11-12", normalizeDateInput("2026-11-12"))
        assertEquals("2026-11-02", normalizeDateInput("2/11/2026"))
        assertEquals("2026-11-02", normalizeDateInput(" 02-11-2026 "))
        // Review M-2: a month and a year only where a card asks for them; elsewhere «12/11» is no date (not 31/12/2011).
        assertEquals("2029-05-31", normalizeDateInput("05/29", monthYear = true))
        assertEquals("2028-02-29", normalizeDateInput("2/2028", monthYear = true))
        assertEquals("2026-11-02", normalizeDateInput("2/11/2026", monthYear = true))
        for (v in listOf("05/29", "2/2028", "12/11")) assertNull(v, normalizeDateInput(v))
        assertEquals(
            FormFields.Problem(fieldBadDate("Fecha de expedición")),
            fieldsToSave(EntryType.PASSPORT, mapOf(templateValueKey(EntryType.PASSPORT, "issued") to "12/11"), emptyList()),
        )
        // Review M-3: the card's expiry shown as it is asked; any other day of a month as a full date.
        assertEquals("05/29", displayMonthYear("2029-05-31"))
        assertEquals("15/05/2029", displayMonthYear("2029-05-15"))
        assertEquals("raro", displayMonthYear("raro"))
        assertEquals("", normalizeDateInput("  "))
        for (v in bad.filter { it.isNotBlank() } + listOf("31/02/2026", "13/29", "00/30")) assertNull(v.take(20), normalizeDateInput(v, monthYear = true))
        assertEquals("12/11/2026", displayDate("2026-11-12"))
        assertEquals("mañana", displayDate("mañana"))
        assertTrue(Expiry.items(entry("nada"), utc).isEmpty())
    }

    /**
     * Review M-4: one expiry per entry: a template with a date that expires (card, licence, passport) has that one only,
     * and the entry's own date (a copy made elsewhere may bring it) does not count. Review M-6: its line says the
     * template's label, not the name a copy gave the field.
     */
    @Test fun aTemplateHasOneExpiry() {
        val card = entry(
            "tarjeta", type = EntryType.CARD, expiresAt = day("2026-11-01"),
            fields = listOf(CustomField("expiry", "Otro nombre", FieldKind.DATE, "2027-05-31", expires = true)),
        )
        assertEquals(listOf(ExpiryItem("Caducidad", LocalDate.parse("2027-05-31"))), Expiry.items(card, utc))
        assertEquals(emptyList<String>(), due(card))
        for (t in listOf(EntryType.CARD, EntryType.LICENSE, EntryType.PASSPORT)) assertTrue(t.name, !hasOwnExpiry(t))
        for (t in listOf(EntryType.LOGIN, EntryType.NOTE, EntryType.SEED, EntryType.TOTP, EntryType.WIFI)) assertTrue(t.name, hasOwnExpiry(t))
        // A wifi has no date of its template: its own counts.
        assertEquals(listOf("wifi"), due(entry("wifi", type = EntryType.WIFI, expiresAt = day("2026-11-01"))))
    }
}
