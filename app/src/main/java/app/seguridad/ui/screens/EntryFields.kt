package app.seguridad.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import app.seguridad.android.FilterObscuredTouches
import app.seguridad.android.copySensitive
import app.seguridad.crypto.PasswordOptions
import app.seguridad.crypto.generatePassword
import app.seguridad.imports.plainReportText
import app.seguridad.ui.theme.NegativeColor
import app.seguridad.ui.theme.NoCopy
import app.seguridad.ui.theme.PasswordField
import app.seguridad.ui.theme.SectionCard
import app.seguridad.tools.codes.getValue
import app.seguridad.tools.codes.setValue
import app.seguridad.vault.CustomField
import app.seguridad.vault.EXPIRY_ONLY_IN_APP
import app.seguridad.vault.EntryEditSession
import app.seguridad.vault.EntryType
import app.seguridad.vault.Expiry
import app.seguridad.vault.FieldKind
import app.seguridad.vault.MarkupEdit
import app.seguridad.vault.NotesFormat
import app.seguridad.vault.ROTATE_CHOICES
import app.seguridad.vault.Templates
import app.seguridad.vault.VaultEntry
import app.seguridad.vault.WIFI_HIDDEN_KEY
import app.seguridad.vault.WIFI_PASSWORD_KEY
import app.seguridad.vault.WIFI_SECURITY_KEY
import app.seguridad.vault.WIFI_SECURITY_VALUES
import app.seguridad.vault.displayDate
import app.seguridad.vault.displayMonthYear
import app.seguridad.vault.hasOwnExpiry
import app.seguridad.vault.expiryLine
import app.seguridad.vault.isSecretField
import app.seguridad.vault.rotates
import app.seguridad.vault.wifiHiddenLabel
import app.seguridad.vault.wifiSecurityLabel

/*
 * 2.0, task 5: the fields of the templates and the user's own, the notes with format and the dates of expiry, in the
 * card and in the editor. Everything typed lives in the editor's session ([EntryEditSession], its ViewModel), never in
 * saved state; the hidden fields are shown masked and copied as passwords ([copySensitive]); what comes from the vault
 * is shown as plain text ([plainReportText]).
 */

// Texts of this part (also in «Textos para el Abogado Digital» of the task's report).
internal const val FIELD_ADD = "Añadir campo"
internal const val FIELD_ADD_TITLE = "Campo nuevo"
internal const val FIELD_NAME = "Nombre del campo"
internal const val FIELD_EXPIRES = "Avisar cuando caduque"
internal const val FIELD_UP = "Subir"
internal const val FIELD_DOWN = "Bajar"
internal const val FIELD_REMOVE = "Quitar"
internal const val FIELDS_TITLE = "Campos"
internal const val DATE_HINT = "DD/MM/AAAA"
internal const val CARD_DATE_HINT = "MM/AA"
internal const val NOTES_WITH_FORMAT = "Notas con formato: negrita, cursiva, títulos y listas"
internal const val FORMAT_BOLD = "Negrita"
internal const val FORMAT_ITALIC = "Cursiva"
internal const val FORMAT_TITLE = "Título"
internal const val FORMAT_LIST = "Lista"
internal const val FORMAT_HELP =
    "Escribe **así** para negrita y *así* para cursiva. Al principio de una línea: «# » para un título («## » y «### », más " +
        "pequeños), «- » para una lista y «1. » para una lista numerada. Lo demás se ve tal cual: los enlaces son texto y no se abren."
internal const val ENTRY_EXPIRES = "Caduca el (opcional, $DATE_HINT)"
internal const val ROTATE_TITLE = "Recordar cambiar la contraseña"
internal const val ROTATE_NONE = "No"
internal fun rotateEvery(months: Int) = "Cada $months meses"

private fun kindLabel(kind: FieldKind) = when (kind) {
    FieldKind.TEXT -> "Texto"
    FieldKind.HIDDEN -> "Oculto"
    FieldKind.NUMBER -> "Número"
    FieldKind.DATE -> "Fecha"
}

// The card.

/**
 * How the card shows the stored [field] of an entry of [type]: a date as DD/MM/AAAA (the card's expiry as MM/AA, as it is
 * asked, review M-3), the wifi's choices in words.
 */
private fun shownValue(type: EntryType, field: CustomField): String = when {
    type == EntryType.WIFI && field.id == WIFI_SECURITY_KEY -> wifiSecurityLabel(field.value)
    type == EntryType.WIFI && field.id == WIFI_HIDDEN_KEY -> wifiHiddenLabel(field.value)
    field.kind == FieldKind.DATE && Templates.templateField(type, field.id)?.monthYear == true -> displayMonthYear(field.value)
    field.kind == FieldKind.DATE -> displayDate(field.value)
    else -> field.value
}

/**
 * The fields of [entry] in the card: its template's first, in the template's order and with its labels, then the
 * user's own, in their order and with their names (as plain text). Hidden ones are masked until shown and copied as a
 * password, as the password itself.
 */
@Composable
internal fun EntryFieldsCard(entry: VaultEntry, onCopied: () -> Unit) {
    val used = HashSet<Int>()
    for (t in Templates.fieldsFor(entry.type)) {
        val i = entry.fields.indexOfFirst { it.id == t.key }
        if (i < 0) continue
        used += i
        val f = entry.fields[i]
        // The wifi's «Red oculta» only when it is.
        if (entry.type == EntryType.WIFI && t.key == WIFI_HIDDEN_KEY && f.value != "true") continue
        // Review I-3: each row under its own key, so a «Mostrar» stays with its field when the entry changes.
        key("t", t.key) {
            Field(t.label, shownValue(entry.type, f), secret = isSecretField(entry.type, f), mono = t.secret, plain = true, onCopied = onCopied)
        }
    }
    entry.fields.forEachIndexed { i, f ->
        if (i in used) return@forEachIndexed
        key("c", i, f.id) {
            Field(plainReportText(f.name, 100), shownValue(entry.type, f), secret = isSecretField(entry.type, f), mono = f.kind == FieldKind.NUMBER, plain = true, onCopied = onCopied)
        }
    }
}

/** The notes in the card: as today, or with their format when [VaultEntry.notesFormat] says so; a note's are hidden. */
@Composable
internal fun NotesCard(entry: VaultEntry, onCopied: () -> Unit) {
    val secret = entry.type == EntryType.NOTE
    if (entry.notesFormat != NotesFormat.LIMITED) return Field("Notas", entry.notes, secret = secret, onCopied = onCopied)
    if (entry.notes.isBlank()) return
    val context = LocalContext.current
    var shown by remember { mutableStateOf(!secret) }
    SectionCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Notas", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            // No text selection, as every field of the card: the system «Copiar» would skip the sensitive flag.
            if (shown) MarkupText(entry.notes) else Text("•".repeat(minOf(entry.notes.length, 16)), fontFamily = FontFamily.Monospace)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = {
                    copySensitive(context, entry.notes)
                    onCopied()
                }) { Text("Copiar") }
                if (secret) OutlinedButton(onClick = { shown = !shown }) { Text(if (shown) "Ocultar" else "Mostrar") }
            }
        }
    }
}

/** The card's line of expiry: the earliest date of [entry] ([Expiry.next]), in red when it is within the 30 days. */
@Composable
internal fun ExpiryCardLine(entry: VaultEntry, now: Long) {
    val item = Expiry.next(entry) ?: return
    val today = Expiry.today(now)
    val due = Expiry.isDue(entry, now)
    Row(verticalAlignment = Alignment.CenterVertically) {
        // Review M-8 (R-1): the clock is decoration; TalkBack reads the line only.
        Text("⏰ ", Modifier.clearAndSetSemantics { }, style = MaterialTheme.typography.bodyMedium)
        Text(
            expiryLine(item.copy(what = plainReportText(item.what, 100)), today),
            style = MaterialTheme.typography.bodyMedium,
            color = if (due) NegativeColor else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A label with an emoji in front that TalkBack does not read (review M-8, as `ScanQrLabel`). */
@Composable
internal fun DecoratedLabel(emoji: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$emoji ", Modifier.clearAndSetSemantics { })
        Text(text)
    }
}

/**
 * A secret of a template that is a number (the card's CVV and PIN, review M-9): as [PasswordField], masked until «Ver»,
 * with no copying, but with the keyboard of digits.
 */
@Composable
private fun SecretNumberField(value: String, onChange: (String) -> Unit, label: String) {
    var visible by remember { mutableStateOf(false) }
    NoCopy(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(label) },
            singleLine = true,
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, autoCorrectEnabled = false),
            trailingIcon = { TextButton(onClick = { visible = !visible }) { Text(if (visible) "Ocultar" else "Ver", style = MaterialTheme.typography.labelMedium) } },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// The editor.

/** A date typed as the user likes ([app.seguridad.vault.normalizeDateInput] reads it on save). */
@Composable
private fun DateField(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value, onChange, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, autoCorrectEnabled = false),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** The fixed fields of [type]'s template, from [form]'s values for that type. */
@Composable
internal fun TemplateFieldsEditor(form: EntryEditSession, type: EntryType) {
    for (t in Templates.fieldsFor(type)) key(type, t.key) {
        val value = form.templateValue(type, t.key)
        val set: (String) -> Unit = { form.setTemplateValue(type, t.key, it) }
        when {
            type == EntryType.WIFI && t.key == WIFI_SECURITY_KEY -> {
                Text(t.label, style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    WIFI_SECURITY_VALUES.forEach { v -> FilterChip(selected = value == v, onClick = { set(v) }, label = { Text(wifiSecurityLabel(v)) }) }
                }
            }
            type == EntryType.WIFI && t.key == WIFI_HIDDEN_KEY -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Switch(checked = value == "true", onCheckedChange = { set(if (it) "true" else "false") })
                Text(t.label)
            }
            t.secret && t.kind == FieldKind.NUMBER -> SecretNumberField(value, set, label = t.label)
            t.secret -> {
                PasswordField(value, set, label = t.label)
                if (type == EntryType.WIFI && t.key == WIFI_PASSWORD_KEY) {
                    TextButton(onClick = { set(generatePassword(PasswordOptions(length = 20)) ?: value) }) { DecoratedLabel("🎲", "Generar una al azar") }
                }
            }
            t.kind == FieldKind.DATE -> DateField(value, set, "${t.label} (${if (t.monthYear) CARD_DATE_HINT else DATE_HINT})")
            else -> OutlinedTextField(
                value, set, label = { Text(t.label) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = if (t.key == "email") KeyboardType.Email else KeyboardType.Text, autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** The user's own fields: each with its value, «Subir», «Bajar» and «Quitar»; and «Añadir campo». */
@Composable
internal fun CustomFieldsEditor(form: EntryEditSession) {
    val fields = form.customFields.value
    var adding by remember { mutableStateOf(false) }
    if (fields.isNotEmpty()) Text(FIELDS_TITLE, style = MaterialTheme.typography.labelLarge)
    fields.forEachIndexed { i, f ->
        // Review I-3: the row's own key (never the field's id alone): a «Ver» goes with its field, or with nothing.
        key(form.rowKey(i)) {
            val name = plainReportText(f.name, 100)
            val set: (String) -> Unit = { form.setFieldValue(i, it) }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                when (f.kind) {
                    FieldKind.HIDDEN -> PasswordField(f.value, set, label = name)
                    FieldKind.DATE -> DateField(f.value, set, "$name ($DATE_HINT)")
                    FieldKind.NUMBER -> OutlinedTextField(
                        f.value, set, label = { Text(name) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, autoCorrectEnabled = false),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    FieldKind.TEXT -> OutlinedTextField(
                        f.value, set, label = { Text(name) },
                        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    if (f.kind == FieldKind.DATE) {
                        Checkbox(checked = f.expires, onCheckedChange = { on -> form.setFieldExpires(i, on) })
                        Text(FIELD_EXPIRES, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(enabled = i > 0, onClick = { form.moveField(i, -1) }, modifier = Modifier.semantics { contentDescription = "$FIELD_UP «$name»" }) { Text(FIELD_UP) }
                    TextButton(enabled = i < fields.size - 1, onClick = { form.moveField(i, 1) }, modifier = Modifier.semantics { contentDescription = "$FIELD_DOWN «$name»" }) { Text(FIELD_DOWN) }
                    TextButton(onClick = { form.removeField(i) }, modifier = Modifier.semantics { contentDescription = "$FIELD_REMOVE «$name»" }) { Text(FIELD_REMOVE, color = NegativeColor) }
                }
            }
        }
    }
    OutlinedButton(onClick = { adding = true }) { DecoratedLabel("➕", FIELD_ADD) }
    if (adding) AddFieldDialog(onDismiss = { adding = false }) { name, kind, expires -> form.addField(name, kind, expires).also { if (it == null) adding = false } }
}

/** The name and kind of a new field; [onAdd] gives null when it was added, or why not (shown in the dialog). */
@Composable
private fun AddFieldDialog(onDismiss: () -> Unit, onAdd: (String, FieldKind, Boolean) -> String?) {
    // Not saved state: only the name of a field and its kind (no value), and lost on rotation as the whole dialog.
    var name by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(FieldKind.TEXT) }
    var expires by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(FIELD_ADD_TITLE) },
        text = {
            FilterObscuredTouches()
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(FIELD_NAME) }, singleLine = true, keyboardOptions = KeyboardOptions(autoCorrectEnabled = false), modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    FieldKind.entries.forEach { k -> FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(kindLabel(k)) }) }
                }
                if (kind == FieldKind.HIDDEN) Text("Se ve oculto y se copia como una contraseña.", style = MaterialTheme.typography.bodySmall)
                if (kind == FieldKind.DATE) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = expires, onCheckedChange = { expires = it })
                        Text(FIELD_EXPIRES)
                    }
                }
                problem?.let { Text(it, color = NegativeColor) }
            }
        },
        confirmButton = { TextButton(onClick = { problem = onAdd(name, kind, expires) }) { Text("Añadir") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/**
 * The notes: a switch for the format and, with it on, the buttons that write its marks around the selection or at the
 * start of the line, and what the marks are, folded. The text is kept as typed. The selection is kept here only while
 * the screen is composed; the text is the session's.
 */
@Composable
internal fun NotesEditor(form: EntryEditSession, type: EntryType) {
    var notes by form.notes
    var format by form.notesFormat
    var field by remember { mutableStateOf(TextFieldValue(notes)) }
    // The session's text wins (a load, a clear): the selection goes to its end.
    val value = if (field.text == notes) field else TextFieldValue(notes, TextRange(notes.length))
    fun apply(change: Triple<String, Int, Int>) {
        field = TextFieldValue(change.first, TextRange(change.second, change.third))
        notes = change.first
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Switch(checked = format == NotesFormat.LIMITED, onCheckedChange = { format = if (it) NotesFormat.LIMITED else NotesFormat.PLAIN })
        Text(NOTES_WITH_FORMAT, style = MaterialTheme.typography.bodyMedium)
    }
    if (format == NotesFormat.LIMITED) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            OutlinedButton(onClick = { apply(MarkupEdit.wrap(value.text, value.selection.min, value.selection.max, "**")) }) { Text(FORMAT_BOLD) }
            OutlinedButton(onClick = { apply(MarkupEdit.wrap(value.text, value.selection.min, value.selection.max, "*")) }) { Text(FORMAT_ITALIC) }
            OutlinedButton(onClick = { apply(MarkupEdit.toggleLinePrefix(value.text, value.selection.min, "# ")) }) { Text(FORMAT_TITLE) }
            OutlinedButton(onClick = { apply(MarkupEdit.toggleLinePrefix(value.text, value.selection.min, "- ")) }) { Text(FORMAT_LIST) }
        }
        MoreInfo { Text(FORMAT_HELP, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    OutlinedTextField(
        value,
        {
            field = it
            notes = it.text
        },
        label = { Text(if (type == EntryType.NOTE) "Nota" else "Notas") },
        minLines = if (type == EntryType.NOTE) 5 else 2,
        // Secure notes: a password field, as the seed words (no learning or suggestions from well-behaved keyboards).
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** The entry's own date of expiry and, for a password, the reminder to change it; the notices are only in the app (O-4). */
@Composable
internal fun ExpiryEditor(form: EntryEditSession, type: EntryType) {
    var expires by form.expiresText
    var months by form.rotateMonths
    // Review M-4: one expiry per entry; a card, a licence and a passport have their template's.
    if (hasOwnExpiry(type)) DateField(expires, { expires = it }, ENTRY_EXPIRES)
    if (rotates(type)) {
        Text(ROTATE_TITLE, style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            FilterChip(selected = months == null, onClick = { months = null }, label = { Text(ROTATE_NONE) })
            // A number a copy brought, kept as it is unless another is chosen.
            val choices = (ROTATE_CHOICES + listOfNotNull(months)).distinct().sorted()
            choices.forEach { m -> FilterChip(selected = months == m, onClick = { months = m }, label = { Text(rotateEvery(m)) }) }
        }
    }
    Text(EXPIRY_ONLY_IN_APP, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
