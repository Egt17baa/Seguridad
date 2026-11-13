package app.seguridad.vault

import app.seguridad.crypto.MigrationResult
import app.seguridad.crypto.TotpSecret
import app.seguridad.tools.codes.CellFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.util.UUID

// The entry editor keeps a separate field for the LOGIN password and for the SEED's BIP-39 passphrase. With one shared
// field (1.0.0), a password generated under «Acceso» became the passphrase after switching to «Frase semilla», and a
// passphrase creates a different wallet: saved by mistake, it would point the user at an empty one.

/** What the field for [type] starts with: the saved password only when [existing] is of that same type. */
internal fun initialPassword(existing: VaultEntry?, type: EntryType): String =
    if (existing != null && existing.type == type) existing.password else ""

/** The `password` saved for an entry of [type]: the field of that type only, and nothing for NOTE and TOTP. */
internal fun passwordToSave(type: EntryType, loginPassword: String, seedPassphrase: String): String = when (type) {
    EntryType.LOGIN -> loginPassword
    EntryType.SEED -> seedPassphrase
    EntryType.NOTE, EntryType.TOTP, EntryType.IDENTITY, EntryType.CONTACT -> ""
    // 2.0, task 5: their secrets are hidden fields of the template ([fieldsToSave]), never the entry's password.
    EntryType.CARD, EntryType.WIFI, EntryType.LICENSE, EntryType.PASSPORT -> ""
}

// 2.0, task 5: the fields of the templates and the user's own (design «Plantillas», «Campos personalizados»).

/** A custom field with no name. */
const val FIELD_NEEDS_NAME = "Ponle un nombre a cada campo."

/** A date field whose value is not a date. */
fun fieldBadDate(name: String) = "La fecha de «$name» no es válida. Escríbela como DD/MM/AAAA."

/** A number field whose value is not a number. */
fun fieldBadNumber(name: String) = "«$name» solo admite números."

/** The entry's own date of expiry is not a date. */
const val EXPIRY_BAD_DATE = "La fecha de caducidad no es válida. Escríbela como DD/MM/AAAA."

private val NUMBER = Regex("[0-9 .,+-]*")

/**
 * Where the editor keeps what was typed in the template field [key] of [type]: one value per type, so switching type
 * while creating an entry never carries a card's number into a passport's (the same rule as the two passwords above).
 */
fun templateValueKey(type: EntryType, key: String) = "${type.name}:$key"

/** What the editor saves as [VaultEntry.fields]: the fields, or the fixed text of the first problem. */
sealed interface FormFields {
    data class Ok(val fields: List<CustomField>) : FormFields

    data class Problem(val text: String) : FormFields
}

private fun String.cpLength() = codePointCount(0, length)

/**
 * The fields saved for an entry of [type]: first the template's that were filled ([templateValues], by
 * [templateValueKey]; the empty ones are not kept, R-3), with the template's key as their id and [FieldKind.HIDDEN] for
 * its secrets, then the user's own ([custom]) in their order. Dates are kept as `AAAA-MM-DD` ([normalizeDateInput]) and
 * a number holds only digits, spaces and `.,+-`. The limits of one entry ([VaultLimits]) are said here with their
 * texts, before the store refuses them.
 */
fun fieldsToSave(type: EntryType, templateValues: Map<String, String>, custom: List<CustomField>): FormFields {
    val out = ArrayList<CustomField>()
    for (t in Templates.fieldsFor(type)) {
        val raw = templateValues[templateValueKey(type, t.key)].orEmpty()
        val typed = if (t.secret) raw else raw.trim()
        if (typed.isEmpty()) continue
        val value = when (t.kind) {
            FieldKind.DATE -> normalizeDateInput(typed, t.monthYear) ?: return FormFields.Problem(fieldBadDate(t.label))
            FieldKind.NUMBER -> typed.takeIf { NUMBER.matches(it) } ?: return FormFields.Problem(fieldBadNumber(t.label))
            else -> typed
        }
        out += CustomField(t.key, t.label, if (t.secret) FieldKind.HIDDEN else t.kind, value, t.expires)
    }
    for (f in custom) {
        val name = f.name.trim()
        if (name.isEmpty()) return FormFields.Problem(FIELD_NEEDS_NAME)
        if (name.cpLength() > VaultLimits.FIELD_NAME_MAX) return FormFields.Problem(FIELD_NAME_TOO_LONG)
        val value = when (f.kind) {
            FieldKind.DATE -> normalizeDateInput(f.value) ?: return FormFields.Problem(fieldBadDate(name))
            FieldKind.NUMBER -> f.value.trim().takeIf { NUMBER.matches(it) } ?: return FormFields.Problem(fieldBadNumber(name))
            else -> f.value
        }
        out += f.copy(name = name, value = value, expires = f.kind == FieldKind.DATE && f.expires)
    }
    if (out.size > VaultLimits.FIELDS_MAX) return FormFields.Problem(TOO_MANY_FIELDS)
    if (out.any { it.value.cpLength() > VaultLimits.FIELD_VALUE_MAX }) return FormFields.Problem(FIELD_VALUE_TOO_LONG)
    return FormFields.Ok(out)
}

/** The entry's own date of expiry as typed: null for none, or [EXPIRY_BAD_DATE]'s case as [Result.failure]. */
fun expiresAtToSave(text: String): Result<Long?> {
    val iso = normalizeDateInput(text) ?: return Result.failure(IllegalArgumentException(EXPIRY_BAD_DATE))
    return Result.success(if (iso.isEmpty()) null else parseFieldDate(iso)?.let(::expiresAtOf))
}

/**
 * The entry's own date of expiry saved for [type] (re-review N-2): only for a type that has one ([hasOwnExpiry]); for a
 * card, a licence or a passport «Caduca el» is not on screen, so it is never read (no error on a field not shown).
 */
fun entryExpiresAt(type: EntryType, text: String): Result<Long?> = if (hasOwnExpiry(type)) expiresAtToSave(text) else Result.success(null)

/** The reminders the editor offers, in months («No» is null). */
val ROTATE_CHOICES = listOf(3, 6, 12)

/** Whether «cambiar la contraseña cada N meses» applies to [type]: the types with a password that «Salud» reads. */
fun rotates(type: EntryType) = type == EntryType.LOGIN || type == EntryType.WIFI

/** The fields of the form a paste is looked at in. */
enum class FormSlot { TITLE, USERNAME, PASSWORD, URL, NOTES, SEED }

/**
 * A paste waiting for the user's answer: what it looks like ([kind]), the block pasted ([text], a secret), the field it
 * went into and what that field held before and after. Its [toString] never shows any of the texts.
 */
class PasteOffer(val kind: PastedKind, val text: String, val slot: FormSlot, val before: String, val after: String) {
    override fun toString() = "PasteOffer($kind, $slot)"
}

/** What [EntryEditSession.hasUnsavedChanges] compares; its [toString] shows none of the values (they hold secrets). */
private data class FormSnapshot(
    val title: String,
    val username: String,
    val loginPassword: String,
    val seedPassphrase: String,
    val url: String,
    val notes: String,
    val seed: String,
    val totp: TotpSecret,
    val favorite: Boolean,
    val tags: List<String>,
    val notesFormat: NotesFormat,
    val templateValues: Map<String, String>,
    val customFields: List<CustomField>,
    val expiresText: String,
    val rotateMonths: Int?,
) {
    override fun toString() = "FormSnapshot"
}

/** A Google Authenticator export waiting for the user's confirmation: the [entries] it would add and what it leaves out. */
data class PendingMigration(val result: MigrationResult, val entries: List<VaultEntry>, val leftOut: List<String>)

/**
 * The entry editor's state (M6 and concern 3 of the CR review), held by the screen's ViewModel for its navigation entry:
 * a rotation keeps every field (both passwords, the seed phrase and the 2FA secret included), the pending Google
 * Authenticator import, the texts on screen and one id for a new entry ([entryId], so a save cut short and tapped again
 * never makes a second entry). Never in saved state: the death of the process loses it, as ruled.
 *
 * It goes ([clear]) when the vault locks after the form was loaded ([vaultStateIs], from a collector not tied to the
 * lifecycle, so also in the background), when the screen is left without a configuration change and when the ViewModel
 * is cleared; [finish] on save and back, for good. Once cleared, the form starts again from the vault's entry
 * ([load]), never from what was typed. The QR inspector's text is applied once per session ([takeHandOver]).
 */
class EntryEditSession(
    cells: CellFactory,
    val id: String?,
    private val initialType: EntryType,
    private val newId: String,
    private val newFieldId: () -> String = { UUID.randomUUID().toString() },
) {
    val existing = cells.cell<VaultEntry?>(null)
    val type = cells.cell(initialType)
    val title = cells.cell("")
    val username = cells.cell("")
    val loginPassword = cells.cell("")
    val seedPassphrase = cells.cell("")
    val url = cells.cell("")
    val notes = cells.cell("")
    val seed = cells.cell("")
    val totp = cells.cell(TotpSecret(""))
    val favorite = cells.cell(false)
    val tagsText = cells.cell("")
    val error = cells.cell<String?>(null)
    val info = cells.cell<String?>(null)
    val saving = cells.cell(false)
    val migration = cells.cell<PendingMigration?>(null)

    // 2.0, task 5. Secrets too (the hidden fields): only here, in memory, and gone with [clear].
    val notesFormat = cells.cell(NotesFormat.PLAIN)

    /** What was typed in each template field, by [templateValueKey] (dates as the user writes them). */
    val templateValues = cells.cell<Map<String, String>>(emptyMap())

    /** The user's own fields, in their order (dates as the user writes them). */
    val customFields = cells.cell<List<CustomField>>(emptyList())

    /**
     * Review I-3: one key per row of [customFields], in the same order, that follows its field when one is removed or
     * moved and is never used twice in this session (so a «Ver» of one secret never shows another). Not the field's id.
     */
    private val customRows = cells.cell<List<Long>>(emptyList())

    @Volatile private var rowCounter = 0L

    private fun nextRow(): Long = ++rowCounter

    /** The key the editor composes the field at [index] under ([customRows]); "" when there is none. */
    fun rowKey(index: Int): String = customRows.value.getOrNull(index)?.let { "fila-$it" }.orEmpty()

    /** The entry's own date of expiry, as the user writes it. */
    val expiresText = cells.cell("")
    val rotateMonths = cells.cell<Int?>(null)

    /** Saved, or left with «Atrás»: the screen goes back, and nothing is loaded again. */
    val finished = cells.cell(false)

    /** The fields hold the vault's entry (or a new one's defaults) and what was typed since. */
    @Volatile var loaded = false
        private set

    @Volatile private var handedOver = false

    /** The id the entry is saved with: the existing one's, or one fixed for the whole session. */
    val entryId: String get() = existing.value?.id ?: id ?: newId

    /** Fills the fields from [entries] (the open vault) the first time, and again only after [clear]. */
    fun load(entries: List<VaultEntry>) {
        if (loaded || finished.value) return
        val e = id?.let { i -> entries.firstOrNull { it.id == i } }
        existing.value = e
        type.value = e?.type ?: initialType
        title.value = e?.title.orEmpty()
        username.value = e?.username.orEmpty()
        loginPassword.value = initialPassword(e, EntryType.LOGIN)
        seedPassphrase.value = initialPassword(e, EntryType.SEED)
        url.value = e?.url.orEmpty()
        notes.value = e?.notes.orEmpty()
        seed.value = e?.seedWords.orEmpty()
        totp.value = e?.totp ?: TotpSecret("")
        favorite.value = e?.favorite ?: false
        tagsText.value = e?.tags.orEmpty().joinToString(", ")
        notesFormat.value = e?.notesFormat ?: NotesFormat.PLAIN
        loadFields(e)
        expiresText.value = e?.expiresAt?.let(::expiryDay)?.let { displayDate(it.toString()) }.orEmpty()
        rotateMonths.value = e?.rotateMonths?.takeIf { it in 1..ROTATE_MONTHS_MAX }
        autoTitle.value = null
        baseline.value = snapshot()
        loaded = true
    }

    /**
     * The stored fields of [e]: those of its template (by id, the first of each) to [templateValues], the rest to
     * [customFields]. An id seen twice, or one of the template's on a field of the user's (a copy made elsewhere), gets a
     * new one, so each row of the editor is one field. Dates are shown as DD/MM/AAAA.
     */
    private fun loadFields(e: VaultEntry?) {
        val values = HashMap<String, String>()
        val custom = ArrayList<CustomField>()
        val rows = ArrayList<Long>()
        val ids = HashSet<String>()
        val type = e?.type ?: initialType
        for (f in e?.fields.orEmpty()) {
            val t = Templates.templateField(type, f.id)
            val k = templateValueKey(type, f.id)
            if (t != null && k !in values) {
                values[k] = when {
                    t.monthYear -> displayMonthYear(f.value)
                    t.kind == FieldKind.DATE -> displayDate(f.value)
                    else -> f.value
                }
            } else {
                val id = f.id.takeIf { t == null && ids.add(it) } ?: newFieldId().also { ids.add(it) }
                // Review I-1: a field with the id of a template's secret (a repeated one) stays hidden as one of the user's.
                val kind = if (t?.secret == true) FieldKind.HIDDEN else f.kind
                custom += f.copy(id = id, kind = kind, value = if (f.kind == FieldKind.DATE) displayDate(f.value) else f.value)
                rows += nextRow()
            }
        }
        // Ruling T5, ronda 1: a card, licence or passport offers no date of its own (M-4): the one a copy brought fills
        // the template's expiry when it is empty, so the next save keeps it there instead of losing it.
        val expiryField = Templates.fieldsFor(type).firstOrNull { it.expires }
        val own = e?.expiresAt?.let(::expiryDay)?.toString()
        if (expiryField != null && own != null && values[templateValueKey(type, expiryField.key)].isNullOrBlank()) {
            values[templateValueKey(type, expiryField.key)] = if (expiryField.monthYear) displayMonthYear(own) else displayDate(own)
        }
        // A new wifi starts as the most common network: WPA, not hidden.
        if (e == null && type == EntryType.WIFI) {
            values[templateValueKey(type, WIFI_SECURITY_KEY)] = "WPA"
            values[templateValueKey(type, WIFI_HIDDEN_KEY)] = "false"
        }
        templateValues.value = values
        customFields.value = custom
        customRows.value = rows
    }

    /**
     * The type chosen while creating an entry (re-review N-2). To a type whose template has its expiry (card, licence,
     * passport), a date typed in «Caduca el» goes to that field if it is empty, so it is not dropped silently; «Caduca
     * el» keeps it for a type with its own date.
     */
    fun changeType(to: EntryType) {
        type.value = to
        val field = Templates.fieldsFor(to).firstOrNull { it.expires } ?: return
        val typed = expiresText.value.trim()
        if (typed.isNotEmpty() && templateValue(to, field.key).isBlank()) setTemplateValue(to, field.key, typed)
    }

    /** What was typed in the template field [key] of [type]. */
    fun templateValue(type: EntryType, key: String): String = templateValues.value[templateValueKey(type, key)].orEmpty()

    fun setTemplateValue(type: EntryType, key: String, value: String) {
        templateValues.value = templateValues.value + (templateValueKey(type, key) to value)
    }

    /**
     * Adds a field of the user's at the end: null, or the fixed text of why not (no name, a name too long, or no room
     * left: the 30 of an entry, counting the fixed fields of the type's template).
     */
    fun addField(name: String, kind: FieldKind, expires: Boolean): String? {
        val n = name.trim()
        if (n.isEmpty()) return FIELD_NEEDS_NAME
        if (n.codePointCount(0, n.length) > VaultLimits.FIELD_NAME_MAX) return FIELD_NAME_TOO_LONG
        if (customFields.value.size >= Templates.customFieldsRoom(type.value)) return TOO_MANY_FIELDS
        customFields.value = customFields.value + CustomField(newFieldId(), n, kind, "", expires = kind == FieldKind.DATE && expires)
        customRows.value = customRows.value + nextRow()
        return null
    }

    /** The value of the field of the user's at [index]. */
    fun setFieldValue(index: Int, value: String) {
        val list = customFields.value
        if (index in list.indices) customFields.value = list.toMutableList().also { it[index] = it[index].copy(value = value) }
    }

    /** Whether the date field at [index] is one the notices look at; nothing for a field that is not a date. */
    fun setFieldExpires(index: Int, expires: Boolean) {
        val list = customFields.value
        if (index in list.indices && list[index].kind == FieldKind.DATE) {
            customFields.value = list.toMutableList().also { it[index] = it[index].copy(expires = expires) }
        }
    }

    /** Moves the field at [index] one place up ([delta] -1) or down (+1); nothing past either end. */
    fun moveField(index: Int, delta: Int) {
        val list = customFields.value
        val to = index + delta
        if (index !in list.indices || to !in list.indices) return
        customFields.value = list.toMutableList().also { val f = it.removeAt(index); it.add(to, f) }
        customRows.value = customRows.value.toMutableList().also { if (index in it.indices && to in it.indices) it.add(to, it.removeAt(index)) }
    }

    fun removeField(index: Int) {
        val list = customFields.value
        if (index in list.indices) {
            customFields.value = list.toMutableList().also { it.removeAt(index) }
            customRows.value = customRows.value.toMutableList().also { if (index in it.indices) it.removeAt(index) }
        }
    }

    // 2.0, task 7 (design «Formulario»): the title from the URL, the question after a paste, and «¿Guardar los cambios?».

    /**
     * A paste that looks like a 2FA link or a seed phrase, waiting for the user's answer ([acceptPaste], [declinePaste]).
     * It holds what was pasted (a secret): only here, in memory, and gone with [clear].
     */
    val pasteOffer = cells.cell<PasteOffer?>(null)

    /** «Atrás» with changes: the dialog «¿Guardar los cambios?» is up. */
    val askingToLeave = cells.cell(false)

    /** The BIP-39 lists a paste is read with; null until the ViewModel has them (then no phrase is recognised). */
    @Volatile var seedLists: SeedLists? = null

    /** The title [titleFromUrl] filled; while the title is still it, it follows the URL. */
    private val autoTitle = cells.cell<String?>(null)

    /** What the form held right after [load]: [hasUnsavedChanges] compares with it. Null when nothing is loaded. */
    private val baseline = cells.cell<FormSnapshot?>(null)

    /**
     * What the form would save, as compared by [hasUnsavedChanges]: the texts that are trimmed on save, trimmed; the
     * template values that are not empty (an empty one is no value); not the type (a new entry that only changes its
     * type loses nothing) and not what is on screen only (errors, notices, dialogs).
     */
    private fun snapshot() = FormSnapshot(
        title.value.trim(), username.value.trim(), loginPassword.value, seedPassphrase.value, url.value.trim(), notes.value,
        seed.value.trim(), totp.value, favorite.value, normalizeTags(tagsText.value), notesFormat.value,
        templateValues.value.filterValues { it.isNotEmpty() }, customFields.value, expiresText.value.trim(), rotateMonths.value,
    )

    /**
     * Whether something changed since the form was loaded (from the vault, or a new entry's defaults): the dialog of
     * «Atrás» asks only then. Compared with the form as loaded, never with the vault's entry, so what the load tidies (the
     * dates shown as DD/MM/AAAA, a repeated field id, a copy's expiry in the template) is never a change.
     */
    val hasUnsavedChanges: Boolean get() = loaded && baseline.value.let { it != null && it != snapshot() }

    /** The cell of [slot]. */
    private fun slotCell(slot: FormSlot) = when (slot) {
        FormSlot.TITLE -> title
        FormSlot.USERNAME -> username
        FormSlot.PASSWORD -> loginPassword
        FormSlot.URL -> url
        FormSlot.NOTES -> notes
        FormSlot.SEED -> seed
    }

    /**
     * The field [slot] changed to [value] (its own change callback, the only caller). The URL fills an empty title
     * ([titleFromUrl]), and keeps it in step while the title is the one it filled. A block pasted at once ([pastedBlock])
     * that looks like a 2FA link or a seed phrase ([detectPasted]) is offered as that type ([pasteOffer]): only for a new
     * entry (an existing one keeps its type), and only when it is not that type already. The paste stays in the field as
     * pasted until the user answers.
     */
    fun typed(slot: FormSlot, value: String) {
        val cell = slotCell(slot)
        val before = cell.value
        cell.value = value
        if (slot == FormSlot.URL) followUrl(value)
        if (!loaded || existing.value != null) return
        val block = pastedBlock(before, value) ?: return
        val kind = detectPasted(block, seedLists) ?: return
        if ((kind == PastedKind.OTPAUTH && type.value == EntryType.TOTP) || (kind == PastedKind.SEED && type.value == EntryType.SEED)) return
        pasteOffer.value = PasteOffer(kind, block, slot, before, value)
    }

    private fun followUrl(value: String) {
        val current = title.value
        val auto = autoTitle.value
        if (current.isNotBlank() && current != auto) return
        val t = titleFromUrl(value)
        when {
            t != null -> {
                title.value = t
                autoTitle.value = t
            }
            auto != null && current == auto -> {
                title.value = ""
                autoTitle.value = null
            }
        }
    }

    /**
     * «Sí» to [pasteOffer]: the paste goes out of the field it was pasted in (back to what it held before, if the field
     * still holds the paste), and the type changes. A seed phrase goes to the phrase's field; a 2FA link is returned, for
     * the ViewModel to read as a scan (`applyOtpAuth`, where CRY-B02 is). Null, and nothing done, once the form was
     * cleared (a lock while the question was up).
     */
    fun acceptPaste(): String? {
        val offer = pasteOffer.value ?: return null
        pasteOffer.value = null
        if (!loaded) return null
        val cell = slotCell(offer.slot)
        if (cell.value == offer.after) {
            cell.value = offer.before
            if (offer.slot == FormSlot.URL) followUrl(offer.before)
        }
        return when (offer.kind) {
            PastedKind.SEED -> {
                changeType(EntryType.SEED)
                seed.value = offer.text.trim()
                null
            }
            PastedKind.OTPAUTH -> {
                changeType(EntryType.TOTP)
                offer.text.trim()
            }
        }
    }

    /** «No» to [pasteOffer]: everything stays as pasted. */
    fun declinePaste() {
        pasteOffer.value = null
    }

    /**
     * Where the form was going when «¿Guardar los cambios?» opened ([askToLeave]): a tab, or null for «Atrás» (the form
     * ends). Only a call, never text; gone with [clear], «Seguir editando» and a failed save.
     */
    @Volatile private var pendingLeave: (() -> Unit)? = null

    /** «Atrás» (the bar's arrow and the system's back): [askToLeave] with no other place to go. */
    fun back() = askToLeave(null)

    /**
     * Leaving the form towards [go] (another tab, review I-1 of task 7), or with «Atrás» when null. With changes, the
     * dialog opens, without an old error on it (m-1), and [go] waits for its answer; without changes, [go] runs (or the
     * form ends). While a save is under way nothing is asked (m-2): «Atrás» waits, since the screen goes by itself when
     * the save ends, and a tab goes at once (the save goes on in the ViewModel, and what was typed is cleared on leaving).
     */
    fun askToLeave(go: (() -> Unit)?) {
        when {
            saving.value -> go?.invoke()
            hasUnsavedChanges -> {
                error.value = null
                pendingLeave = go
                askingToLeave.value = true
            }
            go != null -> go()
            else -> finish()
        }
    }

    /** «Seguir editando», or the dialog dismissed: back to the form, as it was, and nowhere to go. */
    fun keepEditing() {
        askingToLeave.value = false
        pendingLeave = null
    }

    /**
     * «Descartar»: nothing is saved. With «Atrás» the form ends ([finish]); towards a tab, what was typed goes first
     * ([clear]) and then the tab opens. Nothing while a save is under way (m-2: the save would happen all the same).
     */
    fun discard() {
        if (saving.value) return
        val go = pendingLeave
        pendingLeave = null
        askingToLeave.value = false
        if (go == null) {
            finish()
        } else {
            clear()
            go()
        }
    }

    /** «Guardar» of the dialog passed the checks: the dialog goes, and the place to go waits for the save ([takeLeave]). */
    fun savingToLeave() {
        askingToLeave.value = false
    }

    /** Where to go once the save of the dialog ended (or null); taken once. A failed save drops it the same way. */
    fun takeLeave(): (() -> Unit)? = pendingLeave.also { pendingLeave = null }

    /** A re-created activity: a place to go of the old one is not followed (its dialog goes, the form stays). */
    internal fun dropLeave() {
        if (pendingLeave != null) {
            pendingLeave = null
            askingToLeave.value = false
        }
    }

    /** The vault's state: open loads the form (if it was cleared), any other state after it was loaded clears it. */
    fun vaultStateIs(unlocked: Boolean, entries: List<VaultEntry>) {
        if (unlocked) load(entries) else if (loaded) clear()
    }

    /** The QR inspector's [text] the first time it is offered in this session; null afterwards (a rotation, a new unlock). */
    fun takeHandOver(text: String?): String? {
        if (text == null || handedOver) return null
        handedOver = true
        return text
    }

    /** Everything typed, the pending import and the texts go; [load] fills the form again from the vault. */
    fun clear() {
        existing.value = null
        type.value = initialType
        title.value = ""
        username.value = ""
        loginPassword.value = ""
        seedPassphrase.value = ""
        url.value = ""
        notes.value = ""
        seed.value = ""
        totp.value = TotpSecret("")
        favorite.value = false
        tagsText.value = ""
        notesFormat.value = NotesFormat.PLAIN
        templateValues.value = emptyMap()
        customFields.value = emptyList()
        customRows.value = emptyList()
        expiresText.value = ""
        rotateMonths.value = null
        error.value = null
        info.value = null
        saving.value = false
        migration.value = null
        pasteOffer.value = null
        askingToLeave.value = false
        pendingLeave = null
        autoTitle.value = null
        baseline.value = null
        loaded = false
    }

    /** Saved or left with «Atrás»: cleared for good, and the screen goes back ([finished]). */
    fun finish() {
        clear()
        finished.value = true
    }
}

/**
 * The ViewModel's wiring of this session (m6 of the CR re-review), here so it is tested: follows the vault's [state] in
 * [scope] (the ViewModel's, not tied to the lifecycle), so a lock clears the form at once even with no composition (the
 * app in the background), and the next unlock loads it again from the vault.
 */
fun EntryEditSession.followVault(state: Flow<VaultState>, scope: CoroutineScope): Job = scope.launch {
    state.collect { vaultStateIs(it is VaultState.Unlocked, (it as? VaultState.Unlocked)?.entries.orEmpty()) }
}

/** The screen left the composition: a rotation ([changingConfigurations]) keeps what was typed, any other way out clears it. */
fun EntryEditSession.left(changingConfigurations: Boolean) {
    if (!changingConfigurations) clear() else dropLeave()
}

/**
 * Review I-1 of task 7: the tabs of the app ask the open editor before they leave it. Only a Boolean ([hasChanges]) and
 * a call ([ask], with where to go) pass through it, never anything typed. The editor registers while it is on screen;
 * with none, or none with changes, [leave] goes at once.
 */
class LeaveGuard {
    private class Holder(val hasChanges: () -> Boolean, val ask: (go: () -> Unit) -> Unit)

    @Volatile private var holder: Holder? = null

    /** The editor on screen; the returned call takes it out again (only if it is still the one registered). */
    fun register(hasChanges: () -> Boolean, ask: (go: () -> Unit) -> Unit): () -> Unit {
        val h = Holder(hasChanges, ask)
        holder = h
        return { if (holder === h) holder = null }
    }

    /** Towards [go] (a tab): the editor's dialog first when it has changes. */
    fun leave(go: () -> Unit) {
        val h = holder
        if (h != null && h.hasChanges()) h.ask(go) else go()
    }
}

/** The editor's registration with [guard] ([LeaveGuard]): its changes, and its dialog ([EntryEditSession.askToLeave]). */
fun EntryEditSession.guardedBy(guard: LeaveGuard): () -> Unit = guard.register({ hasUnsavedChanges }) { go -> askToLeave(go) }

/** The ViewModel's onCleared: the navigation entry is gone for good. */
fun EntryEditSession.cleared() = clear()

/** 2.0, task 5: the title of a duplicate ([VaultStore.duplicate]). */
fun duplicateTitle(title: String) = "$title (copia)"

/** The card's «Duplicar». */
const val DUPLICATE = "Duplicar"

/** What the card says after «Duplicar» (design: «y lo dice»); [title] as shown. */
fun duplicatedText(title: String) = "Se ha creado «$title», sin adjuntos ni historial."

/** The button that opens the duplicate. */
const val DUPLICATE_OPEN = "Abrir la copia"
