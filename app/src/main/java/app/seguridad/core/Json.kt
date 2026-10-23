package app.seguridad.core

import kotlinx.serialization.json.Json

/** The app's JSON settings: unknown keys are ignored, and nulls and default values are left out when writing. */
val appJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
}
