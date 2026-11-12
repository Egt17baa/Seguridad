package app.seguridad.vault

import app.seguridad.core.appJson
import app.seguridad.crypto.TotpSecret
import app.seguridad.health.estimateBits
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
        assertEquals(listOf("1"), all.query(ListQuery(text = "binance")).map { it.id })
        assertEquals(listOf("2"), all.query(ListQuery(text = "canon")).map { it.id })
        assertEquals(listOf("2"), all.query(ListQuery(text = "CAÑÓN")).map { it.id })
        assertEquals(listOf("3"), all.query(ListQuery(text = "pepe@")).map { it.id })
        assertEquals(listOf("4"), all.query(ListQuery(text = "ejemplo")).map { it.id })
        assertEquals(listOf("5"), all.query(ListQuery(text = "TRAB")).map { it.id })
    }

    @Test
    fun searchNeverMatchesSecrets() {
        val all = listOf(
            e("p", "a", password = "gato123"), e("s", "b", type = EntryType.SEED, seedWords = "gato123 perro"),
            e("n", "c", notes = "gato123"), e("t", "d", type = EntryType.TOTP, totp = TotpSecret(secret = "GATO123ABC")),
        )
        assertTrue(all.query(ListQuery(text = "gato123")).isEmpty())
        assertTrue(all.query(ListQuery(text = "perro")).isEmpty())
    }

    @Test
    fun favoritesFirstThenTitle() {
        val all = listOf(e("1", "zeta"), e("2", "Álamo"), e("3", "beta", favorite = true), e("4", "alfa"), e("5", "Zulu", favorite = true))
        assertEquals(listOf("3", "5", "2", "4", "1"), all.query(ListQuery()).map { it.id })
    }

    @Test
    fun tagFilter() {
        val all = listOf(e("1", "a", tags = listOf("banco", "trabajo")), e("2", "b", tags = listOf("trabajo")), e("3", "c"))
        assertEquals(listOf("1", "2"), all.query(ListQuery(tag = "trabajo")).map { it.id })
        assertEquals(listOf("1"), all.query(ListQuery(tag = "banco")).map { it.id })
        assertEquals(listOf("1"), all.query(ListQuery(tag = "trabajo", text = "banc")).map { it.id })
        val mixed = listOf(e("1", "a"), e("2", "b", type = EntryType.NOTE))
        assertEquals(listOf("2"), mixed.query(ListQuery(type = EntryType.NOTE)).map { it.id })
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

    /** 1.4.0, Task 8: a tag is cut by code points, so an emoji at the edge is never split into a lone surrogate. */
    @Test
    fun normalizeTagsCutsByCodePoints() {
        val edge = "x".repeat(23) + "😀"
        assertEquals(listOf(edge), normalizeTags(edge + "zz"))
        val cut = normalizeTags("x".repeat(24) + "😀").single()
        assertEquals("x".repeat(24), cut)
        for (t in normalizeTags("${"a".repeat(23)}😀😀, b${"😀".repeat(30)}")) {
            assertTrue(t, t.codePoints().noneMatch { it in 0xD800..0xDFFF }) // no half of a pair left alone
            assertTrue(t, t.codePointCount(0, t.length) <= MAX_TAG_LENGTH)
        }
    }

    @Test
    fun oldJsonWithoutNewFieldsDecodes() {
        val json = """{"id":"a","type":"LOGIN","title":"Vieja","username":"u","createdAt":1,"updatedAt":2}"""
        val entry = appJson.decodeFromString(VaultEntry.serializer(), json)
        assertFalse(entry.favorite)
        assertEquals(emptyList<String>(), entry.tags)
    }

    // 2.0, tarea 4: la consulta de la lista con [EntryFilter], carpetas, vistas, orden y la búsqueda con erratas.

    private fun v4(
        id: String, title: String, type: EntryType = EntryType.LOGIN, folderId: String? = null, lastUsedAt: Long? = null,
        createdAt: Long = 1, updatedAt: Long = 1, deletedAt: Long? = null, password: String = "", notes: String = "",
        fields: List<CustomField> = emptyList(), username: String = "", url: String = "", tags: List<String> = emptyList(),
        favorite: Boolean = false, totp: TotpSecret? = null, expiresAt: Long? = null,
    ) = VaultEntry(
        id = id, type = type, title = title, username = username, password = password, url = url, notes = notes, totp = totp,
        createdAt = createdAt, updatedAt = updatedAt, favorite = favorite, tags = tags, folderId = folderId, fields = fields,
        deletedAt = deletedAt, lastUsedAt = lastUsedAt, expiresAt = expiresAt,
    )

    private fun List<VaultEntry>.q(filter: EntryFilter, data: VaultData = VaultData(entries = this), weak: (VaultEntry) -> Boolean = { false }) =
        query(filter, data, now = 1_000, weak = weak).map { it.id }

    @Test fun neverSearchesPasswordsHiddenFieldsOrNotes() {
        val all = listOf(
            v4("p", "a", password = "gatito"),
            v4("n", "b", type = EntryType.NOTE, notes = "gatito"),
            v4("h", "c", fields = listOf(CustomField("1", "PIN del gatito", FieldKind.HIDDEN, "gatito"))),
            v4("v", "d", fields = listOf(CustomField("2", "Nombre", FieldKind.TEXT, "gatito"))),
            v4("t", "e", type = EntryType.TOTP, totp = TotpSecret(secret = "GATITOGATITOGATI")),
            v4("u", "f", url = "https://ana:gatito@banco.es/gatito?gatito=1"),
            v4("s", "g", type = EntryType.SEED).copy(seedWords = "gatito perro"),
        )
        // Neither exactly nor with a typo.
        assertEquals(emptyList<String>(), all.q(EntryFilter(text = "gatito")))
        assertEquals(emptyList<String>(), all.q(EntryFilter(text = "gatitp")))
        // The name of a field that is not hidden is searched; its value never.
        assertEquals(listOf("v"), all.q(EntryFilter(text = "nombre")))
        assertEquals(emptyList<String>(), all.q(EntryFilter(text = "pin del")))
        // The host of the URL is, the rest of the link is not.
        assertEquals(listOf("u"), all.q(EntryFilter(text = "banco.es")))
        assertEquals(listOf("u"), all.q(EntryFilter(text = "banco")))
    }

    @Test fun searchFieldsAndHostAndFolder() {
        val folders = listOf(Folder("w", "Trabajo"), Folder("p", "Proyectos", parentId = "w"))
        val all = listOf(
            v4("1", "Correo", username = "ana@ejemplo.es"), v4("2", "x", url = "www.Ejemplo.org/entrar"),
            v4("3", "y", tags = listOf("hogar")), v4("4", "z", folderId = "p"),
            v4("5", "Código", type = EntryType.TOTP, totp = TotpSecret(secret = "A".repeat(16), issuer = "Banquito", account = "luis")),
            v4("6", "España", url = "https://españa.es"),
        )
        val data = VaultData(entries = all, folders = folders)
        assertEquals(listOf("2"), all.q(EntryFilter(text = "ejemplo.org"), data))
        assertEquals(listOf("3"), all.q(EntryFilter(text = "hogr"), data)) // A typo in a tag.
        // The folder's name and those above it.
        assertEquals(listOf("4"), all.q(EntryFilter(text = "proyectos"), data))
        assertEquals(listOf("4"), all.q(EntryFilter(text = "trabajo"), data))
        assertEquals(listOf("5"), all.q(EntryFilter(text = "banquito"), data))
        assertEquals(listOf("5"), all.q(EntryFilter(text = "luis"), data))
        assertEquals(listOf("6"), all.q(EntryFilter(text = "españa.es"), data))
        // Exact first, then typos, whatever the order chosen.
        val ranked = listOf(v4("a", "Banca"), v4("b", "Banco"), v4("c", "Mi banco"))
        assertEquals(listOf("b", "c", "a"), ranked.q(EntryFilter(text = "banco")))
    }

    @Test fun filterBySubfolders() {
        val folders = listOf(Folder("a", "A"), Folder("b", "B", parentId = "a"), Folder("c", "C", parentId = "b"), Folder("d", "D"))
        val all = listOf(v4("1", "uno", folderId = "a"), v4("2", "dos", folderId = "b"), v4("3", "tres", folderId = "c"), v4("4", "cuatro", folderId = "d"), v4("5", "cinco"))
        val data = VaultData(entries = all, folders = folders)
        assertEquals(listOf("2", "3", "1"), all.q(EntryFilter(folderId = "a"), data)) // dos, tres, uno
        assertEquals(listOf("1"), all.q(EntryFilter(folderId = "a", includeSubfolders = false), data))
        assertEquals(listOf("2", "3"), all.q(EntryFilter(folderId = "b"), data))
        // A folder that is not there shows nothing (never everything).
        assertEquals(emptyList<String>(), all.q(EntryFilter(folderId = "falta"), data))
    }

    @Test fun sortByUsed() {
        val all = listOf(
            v4("1", "Alfa", lastUsedAt = 10, createdAt = 3, updatedAt = 1), v4("2", "Beta", lastUsedAt = 30, createdAt = 1, updatedAt = 3),
            v4("3", "Gamma", createdAt = 2, updatedAt = 2), v4("4", "Delta", lastUsedAt = 20),
        )
        assertEquals(listOf("2", "4", "1", "3"), all.q(EntryFilter(sort = SortOrder.USED)))
        assertEquals(listOf("1", "2", "4", "3"), all.q(EntryFilter(sort = SortOrder.NAME)))
        assertEquals(listOf("1", "3", "2", "4"), all.q(EntryFilter(sort = SortOrder.CREATED)))
        assertEquals(listOf("2", "3", "1", "4"), all.q(EntryFilter(sort = SortOrder.UPDATED)))
        // Names in Spanish order, without case or accents: «Álamo» next to «alfa», «ñu» after «nube».
        val names = listOf(v4("z", "zeta"), v4("al", "Álamo"), v4("a", "alfa"), v4("nu", "ñu"), v4("n", "nube"))
        assertEquals(listOf("al", "a", "n", "nu", "z"), names.q(EntryFilter()))
        // The five «Recientes».
        val many = (1..8).map { v4("r$it", "R$it", lastUsedAt = it.toLong()) } + v4("never", "Nunca")
        assertEquals(listOf("r8", "r7", "r6", "r5", "r4"), recentEntries(many).map { it.id })
    }

    @Test fun viewsApplyTheirFilter() {
        val folders = listOf(Folder("w", "Trabajo"))
        val strong = "z8#Vq2!zRw9@Lm4pXt7&"
        val all = listOf(
            v4("1", "Correo", password = "123456", folderId = "w", tags = listOf("diario")),
            v4("2", "Banco", password = strong, folderId = "w", favorite = true, tags = listOf("diario", "dinero")),
            v4("3", "Nota", type = EntryType.NOTE, folderId = "w", tags = listOf("diario")),
            v4("4", "Foro", password = "123456"),
            v4("5", "Seguro", password = strong, expiresAt = 1_000 + 10 * 86_400_000L),
            v4("6", "Con 2FA", password = strong, totp = TotpSecret(secret = "A".repeat(16))),
        )
        val data = VaultData(entries = all, folders = folders)
        val weak: (VaultEntry) -> Boolean = { it.type == EntryType.LOGIN && it.password.isNotEmpty() && estimateBits(it.password) < 50 }
        val view = SavedView("v1", "Trabajo débil", EntryFilter(folderId = "w", weak = true, types = setOf(EntryType.LOGIN)))
        assertEquals(listOf("1"), all.q(view.filter, data, weak))
        assertEquals(listOf("2"), all.q(EntryFilter(favorites = true), data, weak))
        assertEquals(listOf("2"), all.q(EntryFilter(tags = setOf("diario", "dinero")), data, weak)) // Every tag of the view.
        assertEquals(listOf("2", "1", "3"), all.q(EntryFilter(tags = setOf("diario")), data, weak))
        assertEquals(listOf("5"), all.q(EntryFilter(expiringSoon = true), data, weak))
        // «Sin 2FA»: the logins with no code of their own nor one for them in the vault.
        assertEquals(listOf("2", "1", "4", "5"), all.q(EntryFilter(noTotp = true), data, weak))
        assertEquals(listOf("3"), all.q(EntryFilter(types = setOf(EntryType.NOTE)), data, weak))
        // A view's text is searched like the box's.
        assertEquals(listOf("2"), all.q(EntryFilter(text = "bnaco", folderId = "w"), data, weak))
    }

    @Test fun trashNeverShown() {
        val all = listOf(
            v4("1", "Banco", deletedAt = 5), v4("2", "Banco viejo", deletedAt = 7, favorite = true, lastUsedAt = 99), v4("3", "Correo"),
            v4("id", "Yo", type = EntryType.IDENTITY),
        )
        assertEquals(listOf("3"), all.q(EntryFilter()))
        assertEquals(emptyList<String>(), all.q(EntryFilter(text = "banco")))
        assertEquals(emptyList<String>(), all.q(EntryFilter(favorites = true)))
        assertEquals(emptyList<String>(), recentEntries(all).map { it.id })
        assertEquals(emptyList<String>(), favoriteEntries(all).map { it.id })
    }
}
