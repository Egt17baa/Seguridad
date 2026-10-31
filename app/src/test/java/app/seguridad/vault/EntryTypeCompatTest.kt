package app.seguridad.vault

import app.seguridad.core.appJson
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [EntryType.label] is display-only: the enum is stored by its name, so renaming a label (1.3.0: «Nota segura» to
 * «Nota») never touches a vault file, a .sgbak (its vault.json is this same JSON) or what an older version wrote.
 */
class EntryTypeCompatTest {
    /** An entry as 1.2.x wrote it (in the encrypted body of a vault file and in a .sgbak's vault.json). */
    private val oldNote = """{"id":"n1","type":"NOTE","title":"Mi nota","notes":"texto","createdAt":1,"updatedAt":2}"""

    @Test fun anOldNoteStillReadsAsNote() {
        val e = appJson.decodeFromString<VaultEntry>(oldNote)
        assertEquals(EntryType.NOTE, e.type)
        assertEquals("Mi nota", e.title)
    }

    @Test fun everyTypeIsWrittenByNameAndNeverByLabel() {
        for (t in EntryType.entries) {
            val json = appJson.encodeToString(VaultEntry("i", t, "t", createdAt = 1, updatedAt = 2))
            assertTrue(json, "\"type\":\"${t.name}\"" in json)
            assertFalse(json, "\"${t.label}\"" in json)
            assertEquals(t, appJson.decodeFromString<VaultEntry>(json).type)
        }
    }

    @Test fun theOldLabelIsNotAcceptedNorNeeded() {
        // Nothing matches on the label: the name is the only key.
        val byLabel = oldNote.replace("\"NOTE\"", "\"Nota segura\"")
        try {
            appJson.decodeFromString<VaultEntry>(byLabel)
            org.junit.Assert.fail("a label was read as a type")
        } catch (e: kotlinx.serialization.SerializationException) {
            // expected
        }
    }

    @Test fun aBackupJsonWithNoteSurvivesTheListRoundTrip() {
        val list = appJson.decodeFromString<List<VaultEntry>>("[$oldNote]")
        assertEquals(listOf(EntryType.NOTE), list.map { it.type })
        assertTrue("\"type\":\"NOTE\"" in appJson.encodeToString(list))
    }
}
