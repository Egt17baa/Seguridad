package app.seguridad.ui.screens

import android.content.ActivityNotFoundException
import androidx.activity.result.ActivityResultLauncher
import app.seguridad.core.pickerLaunchError

/**
 * Launches a system file picker; null when it opened, or the message to show (no picker app, or any other failure)
 * instead of a crash.
 */
internal fun <I> ActivityResultLauncher<I>.launchOrError(input: I): String? =
    pickerLaunchError({ it is ActivityNotFoundException }) { launch(input) }
