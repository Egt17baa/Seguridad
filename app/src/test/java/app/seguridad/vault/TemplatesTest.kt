package app.seguridad.vault

import app.seguridad.tools.kotlinLiterals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 2.0, task 5 (design «Plantillas»): the fixed fields of each template, its secrets hidden (they are kept as
 * [FieldKind.HIDDEN] fields and copied as passwords), within the limits of format v4, and with no brand of cards, banks
 * or software (Abogado Digital, O-9 and R-4).
 */
class TemplatesTest {
    private val templates = listOf(EntryType.CARD, EntryType.WIFI, EntryType.LICENSE, EntryType.PASSPORT)

    @Test fun cardSecretsAreHidden() {
        val card = Templates.fieldsFor(EntryType.CARD).associateBy { it.key }
        assertEquals(listOf("holder", "number", "expiry", "cvv", "pin"), Templates.fieldsFor(EntryType.CARD).map { it.key })
        for (k in listOf("number", "cvv", "pin")) assertTrue(k, card.getValue(k).secret)
        assertFalse(card.getValue("holder").secret)
        assertEquals(FieldKind.DATE, card.getValue("expiry").kind)
        assertTrue(card.getValue("expiry").expires)
        // The secrets of the other templates too: the wifi's password, the licence's key and the passport's number.
        assertEquals(listOf("password"), Templates.fieldsFor(EntryType.WIFI).filter { it.secret }.map { it.key })
        assertEquals(listOf("key"), Templates.fieldsFor(EntryType.LICENSE).filter { it.secret }.map { it.key })
        assertEquals(listOf("number"), Templates.fieldsFor(EntryType.PASSPORT).filter { it.secret }.map { it.key })
        // Saved as hidden fields, with the template's key as their id; the empty ones (R-3: CVV and PIN optional) not at all.
        val values = mapOf(
            templateValueKey(EntryType.CARD, "holder") to "Ana Pérez",
            templateValueKey(EntryType.CARD, "number") to "4000 0000 0000 0002",
            templateValueKey(EntryType.CARD, "expiry") to "05/29",
            templateValueKey(EntryType.CARD, "cvv") to "123",
        )
        val saved = (fieldsToSave(EntryType.CARD, values, emptyList()) as FormFields.Ok).fields.associateBy { it.id }
        assertEquals(setOf("holder", "number", "expiry", "cvv"), saved.keys)
        for (k in listOf("number", "cvv")) assertEquals(k, FieldKind.HIDDEN, saved.getValue(k).kind)
        assertEquals(FieldKind.TEXT, saved.getValue("holder").kind)
        assertEquals(FieldKind.DATE, saved.getValue("expiry").kind)
        assertEquals("2029-05-31", saved.getValue("expiry").value) // A card's month: its last day.
        assertTrue(saved.getValue("expiry").expires)
        // A value typed under one template never goes to another's field with the same key (the card's number is not the passport's).
        assertTrue((fieldsToSave(EntryType.PASSPORT, values, emptyList()) as FormFields.Ok).fields.isEmpty())
        // Nothing to do for the types without a template.
        for (t in listOf(EntryType.LOGIN, EntryType.NOTE, EntryType.SEED, EntryType.TOTP)) assertTrue(Templates.fieldsFor(t).isEmpty())
    }

    @Test fun everyTemplateFitsLimits() {
        for (t in templates) {
            val fields = Templates.fieldsFor(t)
            assertTrue(t.name, fields.isNotEmpty() && fields.size <= VaultLimits.FIELDS_MAX)
            assertEquals(t.name, fields.size, fields.map { it.key }.toSet().size)
            for (f in fields) {
                assertTrue(f.label, f.label.codePointCount(0, f.label.length) <= VaultLimits.FIELD_NAME_MAX)
                assertTrue(f.key, f.key.isNotEmpty() && f.key.length <= 20)
                // Only a date can expire.
                if (f.expires) assertEquals(f.key, FieldKind.DATE, f.kind)
            }
            // Every field filled with a long value: the entry is within the limits of one entry.
            val entry = VaultEntry(
                id = "e", type = t, title = "Plantilla", createdAt = 1, updatedAt = 1,
                fields = fields.map { CustomField(it.key, it.label, if (it.secret) FieldKind.HIDDEN else it.kind, if (it.kind == FieldKind.DATE) "2030-01-31" else "x".repeat(2_000), it.expires) },
            )
            assertNull(t.name, entry.entryLimitProblem())
            // And it leaves room for the user's own fields.
            assertTrue(Templates.customFieldsRoom(t) >= VaultLimits.FIELDS_MAX - 5)
        }
    }

    @Test fun templatesNameNoBrands() {
        // Brands of cards, payments, banks, networks and software, and the names of other apps of ImportTextsTest.
        val brands = listOf(
            "visa", "mastercard", "master card", "maestro", "american express", "amex", "discover", "jcb", "unionpay", "union pay",
            "diners", "cirrus", "bizum", "paypal", "apple pay", "google pay", "revolut", "santander", "bbva", "caixa", "ing direct",
            "wi-fi", "wifi alliance", "windows", "office", "adobe", "microsoft", "google", "apple", "bitwarden", "aegis", "keepass",
            "wallet", "cartera", "monedero", "markdown",
        )
        val texts = templates.flatMap { t -> Templates.fieldsFor(t).map { it.label } + t.label } +
            kotlinLiterals(source("vault/Templates.kt")).map { it.second }
        assertTrue(texts.size > 20)
        for (text in texts) {
            val t = text.lowercase()
            for (b in brands) assertFalse("«$text» names «$b»", Regex("(?<!\\p{L})${Regex.escape(b)}(?!\\p{L})").containsMatchIn(t))
        }
        // R-4: «Tarjeta bancaria», never only «Tarjeta» (a contact's card); «wifi» in lower case, never «Wi-Fi».
        assertEquals("Tarjeta bancaria", EntryType.CARD.label)
        assertEquals("Red wifi", EntryType.WIFI.label)
        // R-4: the security of a network is said as text, never «segura»; an open network says it has no password.
        assertEquals(listOf("WPA", "WEP", "nopass"), WIFI_SECURITY_VALUES)
        assertEquals("WPA/WPA2/WPA3", wifiSecurityLabel("WPA"))
        assertEquals("Abierta (sin contraseña)", wifiSecurityLabel("nopass"))
    }

    private fun source(path: String): String {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val f = File(dir, "app/src/main/java/app/seguridad/$path")
            if (f.isFile) return f.readText()
            dir = dir.parentFile
        }
        error("$path not found")
    }
}
