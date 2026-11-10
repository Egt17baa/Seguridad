package app.seguridad.android

import android.content.Context
import androidx.core.content.edit
import app.seguridad.core.PlaceStore

/**
 * 1.4.2: the kept places of the file pickers ([app.seguridad.core.PickerPlaces]), in the app's private preferences
 * `picker_places`. Never in a backup nor a transfer to another phone (backup_rules.xml and data_extraction_rules.xml
 * exclude every shared preference). Only document URIs of other apps, never a file's content.
 */
class PrefsPlaceStore(context: Context) : PlaceStore {
    private val prefs = context.applicationContext.getSharedPreferences("picker_places", Context.MODE_PRIVATE)

    override fun get(key: String): String? = prefs.getString(key, null)

    override fun put(key: String, value: String) = prefs.edit { putString(key, value) }

    override fun clear() = prefs.edit { clear() }
}
