package app.seguridad.vault

/*
 * 2.0, task 5 (design «Plantillas»): each type of template has its fixed fields, kept in [VaultEntry.fields] with the
 * template's [TemplateField.key] as their id and [FieldKind.HIDDEN] for the secrets, which are shown masked and copied
 * as passwords. No brand of cards, banks, networks or software is named or checked (Abogado Digital, O-9); the passport
 * has no emblem, and no text says that what is kept stands for the document.
 */

/**
 * One fixed field of a template. [secret]: kept as [FieldKind.HIDDEN] whatever [kind] says; [expires]: a date the
 * notices of expiry look at ([Expiry]); [monthYear]: a date written as on a card, `MM/AA` (review M-2 and M-3 of task 5:
 * only the card's expiry), kept as the last day of that month.
 */
data class TemplateField(
    val key: String,
    val label: String,
    val kind: FieldKind,
    val secret: Boolean,
    val expires: Boolean = false,
    val monthYear: Boolean = false,
)

/** The values the wifi's «Tipo de seguridad» may hold, as the wifi QR writes them (task 9). */
val WIFI_SECURITY_VALUES = listOf("WPA", "WEP", "nopass")

/** What the wifi's [value] of «Tipo de seguridad» is shown as (R-4: as text, never «segura»); unknown values as they are. */
fun wifiSecurityLabel(value: String): String = when (value) {
    "WPA" -> "WPA/WPA2/WPA3"
    "WEP" -> "WEP"
    "nopass" -> "Abierta (sin contraseña)"
    else -> value
}

/** The wifi's «Red oculta» is kept as the text `true` or `false`. */
fun wifiHiddenLabel(value: String): String = if (value == "true") "Sí" else "No"

const val WIFI_SECURITY_KEY = "security"
const val WIFI_HIDDEN_KEY = "hidden"
const val WIFI_PASSWORD_KEY = "password"

object Templates {
    private val card = listOf(
        TemplateField("holder", "Titular", FieldKind.TEXT, secret = false),
        TemplateField("number", "Número", FieldKind.TEXT, secret = true),
        TemplateField("expiry", "Caducidad", FieldKind.DATE, secret = false, expires = true, monthYear = true),
        // R-3: optional and empty unless the user fills them; nothing suggests it.
        TemplateField("cvv", "CVV (opcional)", FieldKind.NUMBER, secret = true),
        TemplateField("pin", "PIN (opcional)", FieldKind.NUMBER, secret = true),
    )

    private val wifi = listOf(
        TemplateField("ssid", "Nombre de la red", FieldKind.TEXT, secret = false),
        TemplateField(WIFI_PASSWORD_KEY, "Contraseña", FieldKind.TEXT, secret = true),
        TemplateField(WIFI_SECURITY_KEY, "Tipo de seguridad", FieldKind.TEXT, secret = false),
        TemplateField(WIFI_HIDDEN_KEY, "Red oculta", FieldKind.TEXT, secret = false),
    )

    private val license = listOf(
        TemplateField("product", "Producto", FieldKind.TEXT, secret = false),
        TemplateField("key", "Clave", FieldKind.TEXT, secret = true),
        TemplateField("email", "Correo", FieldKind.TEXT, secret = false),
        TemplateField("purchased", "Fecha de compra", FieldKind.DATE, secret = false),
        TemplateField("expiry", "Caducidad", FieldKind.DATE, secret = false, expires = true),
    )

    private val passport = listOf(
        TemplateField("number", "Número", FieldKind.TEXT, secret = true),
        TemplateField("country", "País", FieldKind.TEXT, secret = false),
        TemplateField("issued", "Fecha de expedición", FieldKind.DATE, secret = false),
        TemplateField("expiry", "Caducidad", FieldKind.DATE, secret = false, expires = true),
        TemplateField("holder", "Titular", FieldKind.TEXT, secret = false),
    )

    /** The fixed fields of [type], in the order shown; empty for a type without a template. */
    fun fieldsFor(type: EntryType): List<TemplateField> = when (type) {
        EntryType.CARD -> card
        EntryType.WIFI -> wifi
        EntryType.LICENSE -> license
        EntryType.PASSPORT -> passport
        else -> emptyList()
    }

    /** How many fields of their own the user may add to an entry of [type]: the template's count against the 30. */
    fun customFieldsRoom(type: EntryType): Int = VaultLimits.FIELDS_MAX - fieldsFor(type).size

    /**
     * Review M-4 of task 5: whether [type]'s template has its own date that expires (card, licence, passport): then that
     * one is the entry's only expiry, and the entry's own date is neither offered nor counted ([hasOwnExpiry]).
     */
    fun expiresByTemplate(type: EntryType): Boolean = fieldsFor(type).any { it.expires }

    /** The template field of [type] that a stored field with [id] fills, or null when it is one of the user's. */
    fun templateField(type: EntryType, id: String): TemplateField? = fieldsFor(type).firstOrNull { it.key == id }
}

/**
 * Whether the stored [field] of an entry of [type] is a secret: a [FieldKind.HIDDEN] field, or one of the template's
 * secrets whatever kind a copy made elsewhere gave it (the card, «Salud» and the copy follow this).
 */
fun isSecretField(type: EntryType, field: CustomField): Boolean =
    field.kind == FieldKind.HIDDEN || Templates.templateField(type, field.id)?.secret == true

/**
 * The password of this entry as «Salud» and the reminder to change it read it: a LOGIN's own, or the wifi's hidden field.
 * The other secrets of the templates (a card's number, a licence's key…) are not passwords that are reused or changed.
 */
fun VaultEntry.passwordForHealth(): String = when (type) {
    EntryType.LOGIN -> password
    EntryType.WIFI -> fields.firstOrNull { it.id == WIFI_PASSWORD_KEY }?.value.orEmpty()
    else -> ""
}
