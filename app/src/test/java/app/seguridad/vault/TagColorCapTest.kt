package app.seguridad.vault

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Random

/**
 * Round 2 of the fixes of the attack, point 9 (the director's closure, duda 2): [withTagColor] keeps no set of every tag
 * of the vault any more, only of those that already have a colour (at most [VaultLimits.TAG_COLORS_MAX]), and gives the
 * same result as before for any vault: the entries of the trash count, the versions do not. The code of before is
 * copied here as the reference ([before]); the test passes with both (a change with no other effect: no red).
 */
class TagColorCapTest {
    private val rnd = Random(20261118L)
    private val colorsOf = TagColor.entries

    /** withTagColor as it was before point 9 (Folders.kt), the reference. */
    private fun VaultData.before(tag: String, color: TagColor?): Result<VaultData?> = runCatching {
        val t = normalizeTags(tag).singleOrNull() ?: throw OrganizeRefused(TAG_NOT_VALID)
        if (tagColors[t] == color) return@runCatching null
        if (color == null) return@runCatching copy(tagColors = tagColors - t)
        var colors = tagColors
        if (t !in colors && colors.size >= VaultLimits.TAG_COLORS_MAX) {
            val used = entries.flatMapTo(HashSet()) { it.tags }
            colors = colors.filterKeys { it in used }
            if (colors.size >= VaultLimits.TAG_COLORS_MAX) throw VaultLimitExceeded(TOO_MANY_TAG_COLORS)
        }
        copy(tagColors = colors + (t to color))
    }

    private fun same(name: String, data: VaultData, tag: String, color: TagColor?) {
        val expected = data.before(tag, color)
        val got = runCatching { data.withTagColor(tag, color) }
        assertEquals("$name: result", expected.getOrNull(), got.getOrNull())
        assertEquals("$name: failure", expected.exceptionOrNull()?.let { it::class to it.message }, got.exceptionOrNull()?.let { it::class to it.message })
    }

    private fun entry(i: Int, tags: List<String>, trash: Boolean = false, versions: List<String> = emptyList()) = VaultEntry(
        "e$i", EntryType.LOGIN, "t$i", createdAt = 1, updatedAt = 1, tags = tags, deletedAt = if (trash) 1L else null,
        history = if (versions.isEmpty()) emptyList() else listOf(EntryVersion(1, VaultEntry("e$i", EntryType.LOGIN, "t$i", createdAt = 1, updatedAt = 1, tags = versions))),
    )

    @Test fun theSameResultAsBeforeWithTheColoursFull() {
        repeat(40) { round ->
            // 200 colours taken; some of their tags in live entries, some only in the trash, some only in versions, some nowhere.
            val coloured = List(VaultLimits.TAG_COLORS_MAX) { "c$round-$it" }
            val tagColors = coloured.associateWith { colorsOf[rnd.nextInt(colorsOf.size)] }
            val entries = List(60) { i ->
                val tags = List(rnd.nextInt(11)) { if (rnd.nextBoolean()) coloured[rnd.nextInt(coloured.size)] else "x${rnd.nextInt(500)}" }.distinct()
                val versions = List(rnd.nextInt(4)) { coloured[rnd.nextInt(coloured.size)] }
                entry(i, tags, trash = rnd.nextInt(4) == 0, versions = versions)
            }
            val data = VaultData(entries = entries, tagColors = tagColors)
            same("round $round, a new tag", data, "nueva$round", colorsOf[round % colorsOf.size])
            same("round $round, a coloured one again", data, coloured[rnd.nextInt(coloured.size)], colorsOf[(round + 1) % colorsOf.size])
            same("round $round, without colour", data, coloured[rnd.nextInt(coloured.size)], null)
        }
    }

    /** Every coloured tag in use (live or in the trash, not only in a version): TOO_MANY_TAG_COLORS, as before. */
    @Test fun tooManyTagColoursAsBefore() {
        val coloured = List(VaultLimits.TAG_COLORS_MAX) { "c$it" }
        val tagColors = coloured.associateWith { TagColor.BLUE }
        val inUse = VaultData(entries = coloured.chunked(10).mapIndexed { i, c -> entry(i, c, trash = i % 2 == 0) }, tagColors = tagColors)
        same("all in use", inUse, "nueva", TagColor.RED)
        try {
            inUse.withTagColor("nueva", TagColor.RED)
            throw AssertionError("not refused")
        } catch (e: VaultLimitExceeded) {
            assertEquals(TOO_MANY_TAG_COLORS, e.message)
        }
        // Only in versions: they do not count, so the colours go and the new one fits.
        val onlyInVersions = VaultData(entries = coloured.chunked(10).mapIndexed { i, c -> entry(i, emptyList(), versions = c) }, tagColors = tagColors)
        same("only in versions", onlyInVersions, "nueva", TagColor.RED)
        assertEquals(1, onlyInVersions.withTagColor("nueva", TagColor.RED)!!.tagColors.size)
    }
}
