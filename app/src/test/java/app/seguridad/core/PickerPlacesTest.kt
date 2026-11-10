package app.seguridad.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1.4.2, the owner's request: each file picker of the app opens where it was used last, not where any other picker of
 * the app (or another app) left the system's. Kept on the phone only; «Importar de otra app» only while the app runs
 * (an exported file is usually unencrypted, so where it was is not written down); all forgotten with the vault.
 */
class PickerPlacesTest {
    private class Memory : PlaceStore {
        val map = HashMap<String, String>()
        override fun get(key: String) = map[key]
        override fun put(key: String, value: String) {
            map[key] = value
        }
        override fun clear() = map.clear()
    }

    private val doc = "content://com.android.providers.downloads.documents/document/raw%3A%2Fstorage%2Fx.sgbak"

    @Test fun eachPlaceKeepsItsOwn() {
        val kept = Memory()
        val p = PickerPlaces(kept)
        p.remember(PickerPlace.EXPORT_COPY, doc)
        p.remember(PickerPlace.HASH_FILE, "content://otra.app/document/1")
        assertEquals(doc, p.initial(PickerPlace.EXPORT_COPY))
        assertEquals("content://otra.app/document/1", p.initial(PickerPlace.HASH_FILE))
        assertNull(p.initial(PickerPlace.IMPORT_COPY))
        // Kept across a restart of the app: a new PickerPlaces over the same store.
        assertEquals(doc, PickerPlaces(kept).initial(PickerPlace.EXPORT_COPY))
    }

    @Test fun importFromAnotherAppIsOnlyRememberedWhileTheAppRuns() {
        assertFalse(PickerPlace.IMPORT_OTHER.kept)
        val kept = Memory()
        val p = PickerPlaces(kept)
        p.remember(PickerPlace.IMPORT_OTHER, doc)
        assertEquals(doc, p.initial(PickerPlace.IMPORT_OTHER))
        assertTrue(kept.map.isEmpty())
        assertNull(PickerPlaces(kept).initial(PickerPlace.IMPORT_OTHER))
    }

    @Test fun forgetAllDropsEveryPlace() {
        val kept = Memory()
        val p = PickerPlaces(kept)
        p.remember(PickerPlace.EXPORT_COPY, doc)
        p.remember(PickerPlace.IMPORT_OTHER, doc)
        p.forgetAll()
        assertNull(p.initial(PickerPlace.EXPORT_COPY))
        assertNull(p.initial(PickerPlace.IMPORT_OTHER))
        assertTrue(kept.map.isEmpty())
    }

    /** Only another app's content:// document, at most PLACE_MAX_CHARS: anything else is neither kept nor offered. */
    @Test fun onlyAnotherAppsDocumentIsKept() {
        val kept = Memory()
        val p = PickerPlaces(kept, packageName = "io.github.egt17baa.seguridad")
        for (bad in listOf("file:///data/data/x/vault.bin", "content://io.github.egt17baa.seguridad.compartir/x", "", "content://a/" + "x".repeat(PLACE_MAX_CHARS))) {
            p.remember(PickerPlace.EXPORT_COPY, bad)
            assertNull(bad, p.initial(PickerPlace.EXPORT_COPY))
        }
        // A value written by something else (or an older version) is checked again when read.
        kept.map[PickerPlace.EXPORT_COPY.name] = "file:///sdcard/x"
        assertNull(p.initial(PickerPlace.EXPORT_COPY))
        assertTrue(kept.map.keys.all { it in PickerPlace.entries.map { e -> e.name } })
    }

    @Test fun aFailingStoreIsNoPlace() {
        val broken = object : PlaceStore {
            override fun get(key: String): String = error("disk")
            override fun put(key: String, value: String) = error("disk")
            override fun clear() = error("disk")
        }
        val p = PickerPlaces(broken)
        p.remember(PickerPlace.EXPORT_COPY, doc)
        assertNull(p.initial(PickerPlace.EXPORT_COPY))
        p.forgetAll()
    }
}
