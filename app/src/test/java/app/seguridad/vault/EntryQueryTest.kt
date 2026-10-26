package app.seguridad.vault

import app.seguridad.core.appJson
import app.seguridad.crypto.TotpSecret
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EntryQueryTest {
    private fun e(
        id: String, title: String, type: EntryType = EntryType.LOGIN, username: String = "", url: String = "",
        tags: List<String> = emptyList(), favorite: Boolean = false, password: String = "", notes: String = "",
        seedWords: String = "", totp: TotpSecret? = null,
    ) = VaultEntry(
        id = id, type = type, title = title, username = username, password = password, url = url, notes = notes,
        seedWords = seedWords, totp = totp, createdAt = 1, updatedAt = 1, favorite = favorite, tags = tags,
    )

    @Test
    fun searchMatchesTitleUserUrlTagsIgnoringCaseAndAccents() {
        val all = listOf(
            e("1", "Binance"), e("2", "Cañón"), e("3", "x", username = "Pepe@Correo.es"),
            e("4", "y", url = "https://Ejemplo.com"), e("5", "z", tags = listOf("trabajo")), e("6", "otro"),
        )
        assertEquals(listOf("1"), all.query(EntryFilter(text = "binance")).map { it.id })
        assertEquals(listOf("2"), all.query(EntryFilter(text = "canon")).map { it.id })
        assertEquals(listOf("2"), all.query(EntryFilter(text = "CAÑÓN")).map { it.id })
        assertEquals(listOf("3"), all.query(EntryFilter(text = "pepe@")).map { it.id })
        assertEquals(listOf("4"), all.query(EntryFilter(text = "ejemplo")).map { it.id })
        assertEquals(listOf("5"), all.query(EntryFilter(text = "TRAB")).map { it.id })
    }

    @Test
    fun searchNeverMatchesSecrets() {
        val all = listOf(
            e("p", "a", password = "gato123"), e("s", "b", type = EntryType.SEED, seedWords = "gato123 perro"),
            e("n", "c", notes = "gato123"), e("t", "d", type = EntryType.TOTP, totp = TotpSecret(secret = "GATO123ABC")),
        )
        assertTrue(all.query(EntryFilter(text = "gato123")).isEmpty())
        assertTrue(all.query(EntryFilter(text = "perro")).isEmpty())
    }

    @Test
    fun favoritesFirstThenTitle() {
        val all = listOf(e("1", "zeta"), e("2", "Álamo"), e("3", "beta", favorite = true), e("4", "alfa"), e("5", "Zulu", favorite = true))
        assertEquals(listOf("3", "5", "2", "4", "1"), all.query(EntryFilter()).map { it.id })
    }

    @Test
    fun tagFilter() {
        val all = listOf(e("1", "a", tags = listOf("banco", "trabajo")), e("2", "b", tags = listOf("trabajo")), e("3", "c"))
        assertEquals(listOf("1", "2"), all.query(EntryFilter(tag = "trabajo")).map { it.id })
        assertEquals(listOf("1"), all.query(EntryFilter(tag = "banco")).map { it.id })
        assertEquals(listOf("1"), all.query(EntryFilter(tag = "trabajo", text = "banc")).map { it.id })
        val mixed = listOf(e("1", "a"), e("2", "b", type = EntryType.NOTE))
        assertEquals(listOf("2"), mixed.query(EntryFilter(type = EntryType.NOTE)).map { it.id })
    }

    @Test
    fun normalizeTagsRules() {
        assertEquals(listOf("a", "b", "c"), normalizeTags(" A, b ,A,, c ,B"))
        assertEquals(emptyList<String>(), normalizeTags(" , ,"))
        val long = "x".repeat(40)
        assertEquals(listOf("x".repeat(24)), normalizeTags(long))
        val many = (1..15).joinToString(",") { "t$it" }
        assertEquals((1..10).map { "t$it" }, normalizeTags(many))
        assertFalse(normalizeTags("ÁB").contains("ÁB"))
    }

    @Test
    fun oldJsonWithoutNewFieldsDecodes() {
        val json = """{"id":"a","type":"LOGIN","title":"Vieja","username":"u","createdAt":1,"updatedAt":2}"""
        val entry = appJson.decodeFromString(VaultEntry.serializer(), json)
        assertFalse(entry.favorite)
        assertEquals(emptyList<String>(), entry.tags)
    }
}
