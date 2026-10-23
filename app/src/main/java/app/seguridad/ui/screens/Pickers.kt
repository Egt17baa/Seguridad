package app.seguridad.ui.screens

import android.content.ActivityNotFoundException
import androidx.activity.result.ActivityResultLauncher

/** Shown when the phone has no app to pick or save files (some stripped-down ROMs ship without one). */
internal const val NO_FILE_PICKER = "Este móvil no tiene ninguna app para elegir o guardar archivos."

/** Launches a system file picker; false instead of a crash when the phone has none. */
internal fun <I> ActivityResultLauncher<I>.launchOrFalse(input: I): Boolean = try {
    launch(input)
    true
} catch (e: ActivityNotFoundException) {
    false
}
