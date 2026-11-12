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
            FieldKind.DATE -> normalizeDateInput(typed) ?: return FormFields.Problem(fieldBadDate(t.label))
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

/** The reminders the editor offers, in months («No» is null). */
val ROTATE_CHOICES = listOf(3, 6, 12)

/** Whether «cambiar la contraseña cada N meses» applies to [type]: the types with a password that «Salud» reads. */
fun rotates(type: EntryType) = type == EntryType.LOGIN || type == EntryType.WIFI

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
        val ids = HashSet<String>()
        val type = e?.type ?: initialType
        for (f in e?.fields.orEmpty()) {
            val t = Templates.templateField(type, f.id)
            val k = templateValueKey(type, f.id)
            if (t != null && k !in values) {
                values[k] = if (t.kind == FieldKind.DATE) displayDate(f.value) else f.value
            } else {
                val id = f.id.takeIf { t == null && ids.add(it) } ?: newFieldId().also { ids.add(it) }
                custom += f.copy(id = id, value = if (f.kind == FieldKind.DATE) displayDate(f.value) else f.value)
            }
        }
        // A new wifi starts as the most common network: WPA, not hidden.
        if (e == null && type == EntryType.WIFI) {
            values[templateValueKey(type, WIFI_SECURITY_KEY)] = "WPA"
            values[templateValueKey(type, WIFI_HIDDEN_KEY)] = "false"
        }
        templateValues.value = values
        customFields.value = custom
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
        return null
    }

    /** The value of the field of the user's at [index]. */
    fun setFieldValue(index: Int, value: String) {
        val list = customFields.value
        if (index in list.indices) customFields.value = list.toMutableList().also { it[index] = it[index].copy(value = value) }
    }

    /** Moves the field at [index] one place up ([delta] -1) or down (+1); nothing past either end. */
    fun moveField(index: Int, delta: Int) {
        val list = customFields.value
        val to = index + delta
        if (index !in list.indices || to !in list.indices) return
        customFields.value = list.toMutableList().also { val f = it.removeAt(index); it.add(to, f) }
    }

    fun removeField(index: Int) {
        val list = customFields.value
        if (index in list.indices) customFields.value = list.toMutableList().also { it.removeAt(index) }
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
        expiresText.value = ""
        rotateMonths.value = null
        error.value = null
        info.value = null
        saving.value = false
        migration.value = null
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
    if (!changingConfigurations) clear()
}

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
