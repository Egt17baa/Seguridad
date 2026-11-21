package app.seguridad.ui.screens

import app.seguridad.vault.CustomField
import app.seguridad.vault.EntryType
import app.seguridad.vault.FieldKind
import app.seguridad.vault.VaultEntry
import app.seguridad.vault.WIFI_PASSWORD_KEY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 2.0, task 6: «Ver en grande» (design «Ficha»): groups of 4, one cell per character, the kind of each, what it shows. */
class BigPasswordTest {
    @Test fun groupsOf4WithTheKindOfEachCharacter() {
        val g = bigPasswordCells("Ab3\$xY9!z")
        assertEquals(listOf(4, 4, 1), g.map { it.size })
        assertEquals(listOf("A", "b", "3", "$"), g[0].map { it.shown })
        assertEquals(
            listOf(CharKind.LETTER, CharKind.LETTER, CharKind.DIGIT, CharKind.SYMBOL, CharKind.LETTER, CharKind.LETTER, CharKind.DIGIT, CharKind.SYMBOL, CharKind.LETTER),
            g.flatten().map { it.kind },
        )
        assertEquals(4, BIG_GROUP)
    }

    /** A pair of surrogates is one cell; a space, a bidi control or a zero width shows a mark, never nothing. */
    @Test fun nothingHidesAndNothingSplits() {
        val cells = bigPasswordCells("a😀 ‮b​c").flatten()
        assertEquals(listOf("a", "😀", "␣", "�", "b", "�", "c"), cells.map { it.shown })
        assertEquals(CharKind.SYMBOL, cells[1].kind)
        assertEquals(CharKind.SPACE, cells[2].kind)
        assertEquals(emptyList<List<BigCell>>(), bigPasswordCells(""))
    }

    private fun entry(type: EntryType, password: String = "", fields: List<CustomField> = emptyList()) =
        VaultEntry("e", type, "T", password = password, createdAt = 0, updatedAt = 0, fields = fields)

    /** Only a secret comes up big: a login's password, a seed's passphrase, or a hidden field the entry has once. */
    @Test fun onlyASecretOfTheEntry() {
        assertEquals("clave", bigPasswordValue(entry(EntryType.LOGIN, "clave"), null))
        assertEquals("frase", bigPasswordValue(entry(EntryType.SEED, "frase"), null))
        assertNull(bigPasswordValue(entry(EntryType.LOGIN, ""), null))
        assertNull(bigPasswordValue(entry(EntryType.NOTE, "x"), null))
        assertNull(bigPasswordValue(null, null))
        val wifi = entry(EntryType.WIFI, fields = listOf(CustomField(WIFI_PASSWORD_KEY, "Contraseña", FieldKind.HIDDEN, "wifi-clave"), CustomField("ssid", "Red", FieldKind.TEXT, "Casa")))
        assertEquals("wifi-clave", bigPasswordValue(wifi, WIFI_PASSWORD_KEY))
        assertNull("not a secret", bigPasswordValue(wifi, "ssid"))
        assertNull("not there", bigPasswordValue(wifi, "otro"))
        val twice = entry(EntryType.LOGIN, "x", listOf(CustomField("h", "A", FieldKind.HIDDEN, "uno"), CustomField("h", "B", FieldKind.HIDDEN, "dos")))
        assertNull("an id that is there twice is none of them", bigPasswordValue(twice, "h"))
        assertNull(bigPasswordValue(entry(EntryType.IDENTITY, "x"), null))
    }
}
