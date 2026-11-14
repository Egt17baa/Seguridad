package app.seguridad.settings

import app.seguridad.tools.ABOUT_WARNINGS_SHORT
import app.seguridad.tools.AUTO_BACKUP_SHORT
import app.seguridad.tools.CLIPBOARD_CLEAR_SHORT
import app.seguridad.tools.KDF_HIGH_SHORT
import app.seguridad.tools.KEY_LOCATION_TITLE
import app.seguridad.tools.LOCK_ON_EXIT_SHORT
import app.seguridad.tools.SIMPLE_MODE_SHORT
import app.seguridad.tools.SIMPLE_MODE_TITLE
import app.seguridad.vault.BACKUP_CHECK_TITLE
import app.seguridad.vault.FuzzySearch
import app.seguridad.vault.HISTORY_ALL_CLEAR
import app.seguridad.vault.HISTORY_ALL_SHORT
import app.seguridad.vault.HISTORY_ALL_TITLE

/*
 * 2.0, task 10: «Buscador en Ajustes» (design «Ayuda y personalización»). The texts below are the very ones the screens
 * of Ajustes, «Aspecto» and «Acerca de» draw (they read them from here), so the index can never say what the screen does
 * not. Only fixed texts: never the name of a folder, of a vault or of an entry, nor anything that depends on the data.
 */

const val SETTING_LOCK_TITLE = "Bloquear la bóveda tras no usarla"
const val SETTING_CLIPBOARD_TITLE = "Borrar del portapapeles lo copiado"
const val SETTING_KDF_TITLE = "Coste de Argon2id para lo nuevo"
const val APPEARANCE_TITLE = "Aspecto"
const val APPEARANCE_CHANGE = "Cambiar el aspecto"
const val APPEARANCE_MODE = "Modo"
const val APPEARANCE_STYLE = "Estilo"
const val APPEARANCE_PALETTE = "Paleta"
const val TRANSPARENCY_AND_CONTRAST = "Transparencias y contraste"
const val REDUCE_TRANSPARENCY = "Reducir transparencias"
const val REDUCE_TRANSPARENCY_SHORT = "Paneles opacos y sin difuminar."
const val HIGH_CONTRAST = "Alto contraste"
const val HIGH_CONTRAST_SHORT = "Textos con más contraste y bordes marcados. Enciende «Reducir transparencias»."
const val SETTING_VAULT_COPY_TITLE = "Copia de la bóveda"
const val VAULT_EXPORT = "Exportar bóveda"
const val VAULT_IMPORT = "Importar copia"
const val VAULT_CHANGE_MASTER = "Cambiar contraseña maestra"
const val SETTING_AUTO_BACKUP_TITLE = "Copia automática"
const val SETTING_INFO_TITLE = "Información"
const val SETTING_INFO_OPEN = "Acerca de, licencias y avisos"
const val ABOUT_WARNINGS_TITLE = "Avisos"
const val ABOUT_PRIVACY_TITLE = "Privacidad"
const val ABOUT_PRIVACY_OPEN = "Ver la política de privacidad"
const val ABOUT_LICENSES_TITLE = "Licencias"
const val ABOUT_LICENSES_OPEN = "Ver licencias"

/** Where a setting is: on Ajustes itself (the screen scrolls to it), on «Aspecto» or on «Acerca de» (it opens them). */
enum class SettingPlace { SETTINGS, APPEARANCE, ABOUT }

/**
 * One setting as Ajustes draws it: the [title] of its section and the short [lines] (and buttons) under it, as the
 * screen shows them. [SettingPlace.SETTINGS] ones are the sections of Ajustes, in their order; [FINGERPRINT_KEY] is only
 * there while the fingerprint is on (Ajustes then offers it as a result, never otherwise).
 */
enum class SettingItem(val title: String, val lines: List<String>, val place: SettingPlace) {
    LOCK(SETTING_LOCK_TITLE, listOf(LOCK_ON_EXIT_SHORT), SettingPlace.SETTINGS),
    CLIPBOARD(SETTING_CLIPBOARD_TITLE, listOf(CLIPBOARD_CLEAR_SHORT), SettingPlace.SETTINGS),
    KDF(SETTING_KDF_TITLE, listOf(KDF_HIGH_SHORT), SettingPlace.SETTINGS),
    APPEARANCE(APPEARANCE_TITLE, listOf(APPEARANCE_CHANGE), SettingPlace.SETTINGS),
    SIMPLE_MODE(SIMPLE_MODE_TITLE, listOf(SIMPLE_MODE_SHORT), SettingPlace.SETTINGS),
    VAULT_COPY(SETTING_VAULT_COPY_TITLE, listOf(VAULT_EXPORT, VAULT_IMPORT, VAULT_CHANGE_MASTER, BACKUP_CHECK_TITLE), SettingPlace.SETTINGS),
    AUTO_BACKUP(SETTING_AUTO_BACKUP_TITLE, listOf(AUTO_BACKUP_SHORT), SettingPlace.SETTINGS),
    FINGERPRINT_KEY(KEY_LOCATION_TITLE, emptyList(), SettingPlace.SETTINGS),
    HISTORY(HISTORY_ALL_TITLE, listOf(HISTORY_ALL_SHORT, HISTORY_ALL_CLEAR), SettingPlace.SETTINGS),
    INFO(SETTING_INFO_TITLE, listOf(SETTING_INFO_OPEN), SettingPlace.SETTINGS),
    MODE(APPEARANCE_MODE, emptyList(), SettingPlace.APPEARANCE),
    STYLE(APPEARANCE_STYLE, emptyList(), SettingPlace.APPEARANCE),
    PALETTE(APPEARANCE_PALETTE, emptyList(), SettingPlace.APPEARANCE),
    TRANSPARENCY(TRANSPARENCY_AND_CONTRAST, listOf(REDUCE_TRANSPARENCY, REDUCE_TRANSPARENCY_SHORT, HIGH_CONTRAST, HIGH_CONTRAST_SHORT), SettingPlace.APPEARANCE),
    WARNINGS(ABOUT_WARNINGS_TITLE, listOf(ABOUT_WARNINGS_SHORT), SettingPlace.ABOUT),
    PRIVACY(ABOUT_PRIVACY_TITLE, listOf(ABOUT_PRIVACY_OPEN), SettingPlace.ABOUT),
    LICENSES(ABOUT_LICENSES_TITLE, listOf(ABOUT_LICENSES_OPEN), SettingPlace.ABOUT),
}

/** The search of Ajustes over [SettingItem]: the titles and lines, with [FuzzySearch] (it forgives typos). */
object SettingsSearch {
    /** The query is cut to this many code points before anything compares it (a paste of a whole text, say). */
    const val QUERY_MAX = 60

    const val HINT = "Buscar en Ajustes"
    const val NOTHING = "Ningún ajuste coincide."

    private val index: List<Pair<SettingItem, List<String>>> = SettingItem.entries.map { item ->
        item to (listOf(item.title) + item.lines).map(FuzzySearch::normalize)
    }

    /** [query] as it is searched: its first [QUERY_MAX] code points. */
    fun cut(query: String): String {
        if (query.length <= QUERY_MAX) return query
        val end = query.offsetByCodePoints(0, minOf(QUERY_MAX, query.codePointCount(0, query.length)))
        return query.substring(0, end)
    }

    /** The settings that match [query], best first (the title wins a tie, then the order of the screen); none for a blank one. */
    fun find(query: String): List<SettingItem> {
        val q = FuzzySearch.prepare(cut(query))
        if (q.isEmpty) return emptyList()
        return index.mapNotNull { (item, texts) -> q.score(texts)?.let { item to it } }
            .sortedWith(compareBy({ it.second }, { it.first.ordinal }))
            .map { it.first }
    }
}
