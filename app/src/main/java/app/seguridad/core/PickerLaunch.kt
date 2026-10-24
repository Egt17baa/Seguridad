package app.seguridad.core

/** Shown when the phone has no app to pick or save files (some stripped-down ROMs ship without one). */
internal const val NO_FILE_PICKER = "Este móvil no tiene ninguna app para elegir o guardar archivos."

/** Shown when opening the picker failed for any other reason. */
internal const val PICKER_FAILED = "No se pudo abrir el selector de archivos."

/**
 * Runs [launch] (an `ActivityResultLauncher.launch`); null when it ran, or the message to show instead of closing
 * the app. [noApp] tells "no app can handle it" (ActivityNotFoundException, Android-only) from any other failure,
 * such as the "Can only use lower 16 bits for requestCode" that androidx.fragment 1.2.5 threw in Seguridad 1.0.0.
 * Errors (out of memory and the like) are not caught.
 */
internal inline fun pickerLaunchError(noApp: (RuntimeException) -> Boolean, launch: () -> Unit): String? = try {
    launch()
    null
} catch (e: RuntimeException) {
    if (noApp(e)) NO_FILE_PICKER else PICKER_FAILED
}
