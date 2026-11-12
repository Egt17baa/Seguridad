package app.seguridad.vault

import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.YearMonth
import java.util.Locale

/*
 * 2.0, task 5 (design «Caducidad»): an entry's own date of expiry ([VaultEntry.expiresAt], the start of that day in
 * UTC), its date fields marked «caduca» (kept as `AAAA-MM-DD`) and «cambiar la contraseña cada N meses»
 * ([VaultEntry.rotateMonths], from the last change of the password). Notices only inside the app: a line over the list
 * and one in the card; no notification and no permission (O-4).
 */

/** «Caducan pronto»: within this many days, or already. */
const val EXPIRY_SOON_DAYS = 30

/** The longest reminder a copy may bring, in months; anything else is no reminder. */
const val ROTATE_MONTHS_MAX = 120

/** What [ExpiryItem.what] says for the entry's own date. */
const val EXPIRY_ENTRY = "Caducidad"

/** What [ExpiryItem.what] says for the reminder to change the password. */
const val EXPIRY_ROTATE = "Cambiar la contraseña"

/** Where a date of [ExpiryItem] comes from. */
enum class ExpiryKind { ENTRY, FIELD, ROTATE }

/** One date of an entry the notices look at: [what] it is (the field's name as stored, or a fixed text) and the day. */
data class ExpiryItem(val what: String, val date: LocalDate, val kind: ExpiryKind = ExpiryKind.FIELD)

private val ISO_DATE = Regex("([0-9]{4})-([0-9]{2})-([0-9]{2})")
private val DAY_MONTH_YEAR = Regex("([0-9]{1,2})[/.-]([0-9]{1,2})[/.-]([0-9]{4})")
private val MONTH_YEAR = Regex("([0-9]{1,2})[/.-]([0-9]{2}|[0-9]{4})")

/** The longest text read as a date: anything longer is no date (and is never matched against a pattern). */
private const val DATE_TEXT_MAX = 20

private fun date(year: Int, month: Int, day: Int): LocalDate? = try {
    if (year !in 1900..2199) null else LocalDate.of(year, month, day)
} catch (e: DateTimeException) {
    null
}

/** A date field's value as kept (`AAAA-MM-DD`, a real day), or null. */
fun parseFieldDate(value: String): LocalDate? {
    if (value.length != 10) return null
    val m = ISO_DATE.matchEntire(value) ?: return null
    return date(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
}

/**
 * What the editor keeps of a date typed by the user: `AAAA-MM-DD` for `AAAA-MM-DD`, `D/M/AAAA` (also with `-` or `.`)
 * and, only with [monthYear] (the card's expiry, review M-2), `MM/AA` or `MM/AAAA` (its last day); "" for nothing typed;
 * null for anything else (so «12/11» in a passport is no date, never 31/12/2011).
 */
fun normalizeDateInput(text: String, monthYear: Boolean = false): String? {
    val t = text.trim()
    if (t.isEmpty()) return ""
    if (t.length > DATE_TEXT_MAX) return null
    parseFieldDate(t)?.let { return it.toString() }
    DAY_MONTH_YEAR.matchEntire(t)?.let { m ->
        return date(m.groupValues[3].toInt(), m.groupValues[2].toInt(), m.groupValues[1].toInt())?.toString()
    }
    if (!monthYear) return null
    MONTH_YEAR.matchEntire(t)?.let { m ->
        val month = m.groupValues[1].toInt()
        val y = m.groupValues[2]
        val year = if (y.length == 2) 2000 + y.toInt() else y.toInt()
        if (month !in 1..12) return null
        return date(year, month, 1)?.let { YearMonth.of(year, month).atEndOfMonth().toString() }
    }
    return null
}

/** A kept date (`AAAA-MM-DD`) as the app shows it, `DD/MM/AAAA`; anything else as it is. */
fun displayDate(value: String): String =
    parseFieldDate(value)?.let { "%02d/%02d/%04d".format(Locale.ROOT, it.dayOfMonth, it.monthValue, it.year) } ?: value

/**
 * A kept date as a card shows its expiry (review M-3): `MM/AA` for the last day of a month, as it was asked;
 * [displayDate] for any other.
 */
fun displayMonthYear(value: String): String {
    val d = parseFieldDate(value) ?: return value
    if (d != YearMonth.from(d).atEndOfMonth()) return displayDate(value)
    return "%02d/%02d".format(Locale.ROOT, d.monthValue, d.year % 100)
}

/** Review M-4: whether an entry of [type] has an expiry of its own ([VaultEntry.expiresAt]): not when its template has one. */
fun hasOwnExpiry(type: EntryType): Boolean = !Templates.expiresByTemplate(type)

/** The entry's own date of expiry as a day ([VaultEntry.expiresAt] is that day's start in UTC); null when out of range. */
fun expiryDay(expiresAt: Long): LocalDate? = try {
    Instant.ofEpochMilli(expiresAt).atZone(ZoneOffset.UTC).toLocalDate().takeIf { it.year in 1900..2199 }
} catch (e: DateTimeException) {
    null
}

/** What [VaultEntry.expiresAt] keeps for [day]: its start in UTC. */
fun expiresAtOf(day: LocalDate): Long = day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

object Expiry {
    /** Every date of [e] the notices look at, earliest first; empty for the identity and the contacts. */
    fun items(e: VaultEntry, zone: ZoneId = ZoneId.systemDefault()): List<ExpiryItem> {
        if (e.type.isPersonal) return emptyList()
        val out = ArrayList<ExpiryItem>(2)
        // Review M-4: one expiry per entry; a template's own date wins where it has one. Ruling T5, ronda 1: where that
        // date is missing (or is no date), the entry's own (from a copy) counts, so it is never lost silently.
        val byTemplate = !hasOwnExpiry(e.type) && e.fields.any { f ->
            f.kind == FieldKind.DATE && Templates.templateField(e.type, f.id)?.expires == true && parseFieldDate(f.value) != null
        }
        if (!byTemplate) e.expiresAt?.let(::expiryDay)?.let { out += ExpiryItem(EXPIRY_ENTRY, it, ExpiryKind.ENTRY) }
        for (f in e.fields) {
            // Review M-6: a template's field says its label, not the name a copy gave it.
            val name = Templates.templateField(e.type, f.id)?.label ?: f.name
            if (f.kind == FieldKind.DATE && f.expires) parseFieldDate(f.value)?.let { out += ExpiryItem(name, it) }
        }
        val months = e.rotateMonths
        if (months != null && months in 1..ROTATE_MONTHS_MAX && e.passwordForHealth().isNotEmpty()) {
            try {
                out += ExpiryItem(EXPIRY_ROTATE, Instant.ofEpochMilli(lastPasswordChange(e)).atZone(zone).toLocalDate().plusMonths(months.toLong()), ExpiryKind.ROTATE)
            } catch (x: DateTimeException) {
                // A date out of range (a copy made elsewhere): no reminder.
            }
        }
        return out.sortedBy { it.date }
    }

    /** The earliest date of [e], or null. */
    fun next(e: VaultEntry, zone: ZoneId = ZoneId.systemDefault()): ExpiryItem? = items(e, zone).firstOrNull()

    /** Whether [e] (not in the trash) has a date within [days] of [now]'s day, or past. */
    fun isDue(e: VaultEntry, now: Long, days: Int = EXPIRY_SOON_DAYS, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        if (e.deletedAt != null) return false
        val limit = today(now, zone).plusDays(days.toLong())
        return next(e, zone)?.let { !it.date.isAfter(limit) } == true
    }

    /** The entries of [entries] that [isDue], in their order: never one in the trash, the identity nor a contact. */
    fun dueSoon(entries: List<VaultEntry>, now: Long, days: Int = EXPIRY_SOON_DAYS, zone: ZoneId = ZoneId.systemDefault()): List<VaultEntry> =
        entries.filter { isDue(it, now, days, zone) }

    fun today(now: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
}

/** The card's line for [item] seen on [today]. */
fun expiryLine(item: ExpiryItem, today: LocalDate): String {
    val day = "%02d/%02d/%04d".format(Locale.ROOT, item.date.dayOfMonth, item.date.monthValue, item.date.year)
    return when {
        item.kind == ExpiryKind.ROTATE -> if (item.date.isAfter(today)) "Toca cambiar la contraseña el $day" else "Toca cambiar la contraseña (desde el $day)"
        item.date.isBefore(today) -> "${item.what}: caducó el $day"
        else -> "${item.what}: caduca el $day"
    }
}

/** The line over the list when [n] entries expire within [EXPIRY_SOON_DAYS] days or already have. */
fun expiryNotice(n: Int): String =
    if (n == 1) "1 entrada caduca en los próximos 30 días o ya ha caducado" else "$n entradas caducan en los próximos 30 días o ya han caducado"

/** Under the date fields of the editor (O-4): the notices are only inside the app. */
const val EXPIRY_ONLY_IN_APP = "Los avisos de caducidad solo salen dentro de Clavero, al abrirlo; no hay notificaciones."

/** The button of the line over the list: opens «Caducan pronto». */
const val EXPIRY_NOTICE_OPEN = "Ver"
