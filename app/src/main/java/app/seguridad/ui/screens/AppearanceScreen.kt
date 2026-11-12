package app.seguridad.ui.screens

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.seguridad.android.FilterObscuredTouches
import app.seguridad.settings.CUSTOM_HUES
import app.seguridad.settings.CustomPalette
import app.seguridad.settings.GlassLevel
import app.seguridad.settings.PaletteId
import app.seguridad.settings.Settings
import app.seguridad.settings.SettingsStore
import app.seguridad.settings.ThemeMode
import app.seguridad.settings.ThemeStyle
import app.seguridad.ui.theme.AlertDialog
import app.seguridad.ui.theme.BackScaffold
import app.seguridad.ui.theme.GlassPanel
import app.seguridad.ui.theme.currentLook
import app.seguridad.ui.theme.PanelLevel
import app.seguridad.ui.theme.ProvideLook
import app.seguridad.ui.theme.SectionShape
import app.seguridad.ui.theme.fixContrast
import app.seguridad.ui.theme.hueName
import app.seguridad.ui.theme.hueSwatch
import app.seguridad.ui.theme.isDarkTheme
import app.seguridad.ui.theme.label
import app.seguridad.ui.theme.lookFor
import app.seguridad.vault.TagColor
import app.seguridad.vault.TagPalette
import kotlinx.coroutines.launch

// 2.0, task 5 bis: the texts of Ajustes → «Aspecto» (minimal text, M-3: a line on view, the rest under «Más información»).
// «Cristal» is only the name of a style here (O-C4); the blur is said to work from Android 12; nothing promises contrast
// as a guarantee (O-C6); the wallpaper's colours as O-C5 words them.
internal const val APPEARANCE_TITLE = "Aspecto"
internal const val APPEARANCE_CHANGE = "Cambiar el aspecto"
internal const val APPEARANCE_MODE = "Modo"
internal const val APPEARANCE_STYLE = "Estilo"
internal const val APPEARANCE_PALETTE = "Paleta"
internal const val STYLE_GLASS_SHORT = "Cristal: paneles translúcidos sobre un fondo de color."
internal const val STYLE_SOLID_SHORT = "Sólido: paneles opacos sobre un fondo liso, sin transparencias."
internal const val STYLE_MORE = "Desde Android 12, las barras y los diálogos difuminan lo que tienen detrás. En Android 8 a 11 son más opacos. " +
    "«Reducir transparencias» los hace opacos."
internal const val WALLPAPER_SHORT = "Usa los colores que Android calcula del fondo de pantalla (desde Android 12)."
internal const val WALLPAPER_MORE = "La app no lee la imagen del fondo de pantalla ni pide permisos. Ajusta los tonos para que el texto tenga contraste suficiente."
internal const val WALLPAPER_FROM_12 = "«Colores del fondo de pantalla», desde Android 12."
internal const val CUSTOM_SHORT = "Elige el acento, el fondo y la intensidad del cristal, y pulsa «Aplicar»."
internal const val CUSTOM_MORE = "La app ajusta los tonos para que el texto tenga contraste suficiente. La vista previa enseña el cambio antes de aplicarlo."
internal const val CUSTOM_ACCENT = "Acento"
internal const val CUSTOM_BACKGROUND = "Fondo"
internal const val CUSTOM_GLASS = "Intensidad del cristal"
internal const val CUSTOM_APPLY = "Aplicar"
internal const val PREVIEW_TITLE = "Vista previa"
internal const val PREVIEW_DESCRIPTION = "Vista previa del aspecto elegido, con datos de ejemplo"
internal const val PREVIEW_BAR = "Bóveda"
internal const val PREVIEW_ENTRY = "Ejemplo"
internal const val PREVIEW_LINE = "usuario@ejemplo.com"
internal const val PREVIEW_TAG = "#trabajo"
internal const val PREVIEW_BUTTON = "Botón"
internal const val REDUCE_TRANSPARENCY = "Reducir transparencias"
internal const val REDUCE_TRANSPARENCY_SHORT = "Paneles opacos y sin difuminar."
internal const val HIGH_CONTRAST = "Alto contraste"
internal const val HIGH_CONTRAST_SHORT = "Textos con más contraste y bordes marcados. Enciende «Reducir transparencias»."
internal const val APPEARANCE_SAVE_FAILED = "No se pudo guardar el ajuste."

/** R-C8: the one line after updating, with «Entendido» ([app.seguridad.settings.AppearanceNotice]). */
internal const val APPEARANCE_NOTICE = "Aspecto nuevo. Para volver a uno sin transparencias: Ajustes → Aspecto → «Sólido»."
internal const val APPEARANCE_NOTICE_OK = "Entendido"

private fun ThemeMode.modeLabel() = when (this) {
    ThemeMode.DARK -> "Oscuro"
    ThemeMode.LIGHT -> "Claro"
    ThemeMode.SYSTEM -> "Como el sistema"
}

/** The line of Ajustes that sums up the look: «Cristal · Noche · Oscuro». */
internal fun appearanceSummary(s: Settings): String = "${s.style.label()} · ${s.palette.label()} · ${s.theme.modeLabel()}"

/**
 * Ajustes → «Aspecto»: the mode, the style, the palette («Personalizado» with its preview before it is applied),
 * «Reducir transparencias» and «Alto contraste». Everything here is the look only, saved in Settings (never a secret);
 * the draft of «Personalizado» is three small numbers, so it may survive a rotation in the saved state.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppearanceScreen(store: SettingsStore, onBack: () -> Unit) {
    val settings by store.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var failed by remember { mutableStateOf(false) }
    fun change(f: (Settings) -> Settings) {
        scope.launch {
            failed = try {
                store.update(f)
                false
            } catch (e: Exception) {
                true
            }
        }
    }
    val saved = settings.custom ?: CustomPalette(accentHue = 1, backgroundHue = 8, glass = GlassLevel.MEDIUM)
    var editing by rememberSaveable { mutableStateOf(false) }
    var accent by rememberSaveable { mutableIntStateOf(saved.accentHue) }
    var background by rememberSaveable { mutableIntStateOf(saved.backgroundHue) }
    var glass by rememberSaveable { mutableStateOf(saved.glass) }
    val draft = fixContrast(CustomPalette(accent, background, glass))
    val customOpen = editing || settings.palette == PaletteId.CUSTOM
    val wallpaperOffered = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    BackScaffold(APPEARANCE_TITLE, onBack) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (failed) Hint(APPEARANCE_SAVE_FAILED)
            Preview(settings, if (customOpen) draft else null)
            Section(APPEARANCE_MODE) {
                Choices(ThemeMode.entries, settings.theme, { it.modeLabel() }) { v -> change { it.copy(theme = v) } }
            }
            Section(APPEARANCE_STYLE) {
                Choices(ThemeStyle.entries, settings.style, { it.label() }) { v -> change { it.copy(style = v) } }
                Hint(if (settings.style == ThemeStyle.GLASS) STYLE_GLASS_SHORT else STYLE_SOLID_SHORT)
                MoreInfo { Hint(STYLE_MORE) }
            }
            Section(APPEARANCE_PALETTE) {
                val offered = PaletteId.entries.filter { it != PaletteId.WALLPAPER || wallpaperOffered }
                val shown = if (customOpen) PaletteId.CUSTOM else settings.palette
                Choices(offered, shown, { it.label() }) { v ->
                    if (v == PaletteId.CUSTOM) {
                        editing = true
                    } else {
                        editing = false
                        change { it.copy(palette = v) }
                    }
                }
                if (shown == PaletteId.WALLPAPER) {
                    Hint(WALLPAPER_SHORT)
                    MoreInfo { Hint(WALLPAPER_MORE) }
                }
                if (!wallpaperOffered) Hint(WALLPAPER_FROM_12)
            }
            if (customOpen) {
                Section(PaletteId.CUSTOM.label()) {
                    Hint(CUSTOM_SHORT)
                    MoreInfo { Hint(CUSTOM_MORE) }
                    Text(CUSTOM_ACCENT, style = MaterialTheme.typography.labelLarge)
                    Tones(accent) { accent = it }
                    Text(CUSTOM_BACKGROUND, style = MaterialTheme.typography.labelLarge)
                    Tones(background) { background = it }
                    Text(CUSTOM_GLASS, style = MaterialTheme.typography.labelLarge)
                    Choices(GlassLevel.entries, glass, { it.label() }) { glass = it }
                    val applied = settings.palette == PaletteId.CUSTOM && settings.custom == draft
                    Button(
                        onClick = {
                            editing = false
                            change { it.copy(palette = PaletteId.CUSTOM, custom = draft) }
                        },
                        enabled = !applied,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(CUSTOM_APPLY) }
                }
            }
            Section(REDUCE_TRANSPARENCY) {
                // «Alto contraste» turns it on: the switch shows it on, and it cannot be turned off while that is on.
                SwitchRow(REDUCE_TRANSPARENCY, settings.reduceTransparency || settings.highContrast, enabled = !settings.highContrast) { v ->
                    change { it.copy(reduceTransparency = v) }
                }
                Hint(REDUCE_TRANSPARENCY_SHORT)
                SwitchRow(HIGH_CONTRAST, settings.highContrast) { v -> change { it.copy(highContrast = v) } }
                Hint(HIGH_CONTRAST_SHORT)
            }
        }
    }
}

/** The twelve tones, each with its name (the colour is never the only sign) and a swatch TalkBack does not read. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Tones(selected: Int, onSelect: (Int) -> Unit) {
    val dark = isDarkTheme
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (i in 0 until CUSTOM_HUES) {
            FilterChip(
                selected = selected == i,
                onClick = { onSelect(i) },
                label = { Text(hueName(i)) },
                leadingIcon = { Box(Modifier.size(14.dp).background(hueSwatch(i, dark), CircleShape).clearAndSetSemantics { }) },
            )
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 6.dp),
    ) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/**
 * The preview: a bar, a card with an entry made up for it, a tag and a button, in the look chosen ([custom], while
 * «Personalizado» is being edited, or the one on screen). TalkBack reads it as one preview, not as an entry.
 */
@Composable
private fun Preview(settings: Settings, custom: CustomPalette?) {
    val current = currentLook
    val dark = current.dark
    val look = remember(current, custom) {
        if (custom == null) current
        else lookFor(settings.style, PaletteId.CUSTOM, custom, settings.reduceTransparency, settings.highContrast, dark, sdk = Build.VERSION.SDK_INT)
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(PREVIEW_TITLE, style = MaterialTheme.typography.titleSmall)
        Box(
            Modifier.fillMaxWidth().height(196.dp).clip(SectionShape).clearAndSetSemantics { contentDescription = PREVIEW_DESCRIPTION },
        ) {
            ProvideLook(look) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassPanel(Modifier.fillMaxWidth().height(48.dp), PanelLevel.BAR) {
                        Text(PREVIEW_BAR, Modifier.align(Alignment.CenterStart).padding(horizontal = 16.dp), style = MaterialTheme.typography.titleMedium)
                    }
                    GlassPanel(Modifier.fillMaxWidth().padding(horizontal = 12.dp), PanelLevel.CARD) {
                        Column(Modifier.padding(12.dp)) {
                            Text(PREVIEW_ENTRY)
                            Text(PREVIEW_LINE, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                PREVIEW_TAG,
                                style = MaterialTheme.typography.labelSmall,
                                color = androidx.compose.ui.graphics.Color(if (look.dark) TagPalette.dark(TagColor.BLUE) else TagPalette.light(TagColor.BLUE)),
                            )
                        }
                    }
                    Button(onClick = {}, modifier = Modifier.padding(horizontal = 12.dp)) { Text(PREVIEW_BUTTON) }
                }
            }
        }
    }
}

/** R-C8: «Aspecto nuevo…», once after updating, closed with «Entendido». */
@Composable
internal fun AppearanceNoticeDialog(onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        text = {
            FilterObscuredTouches()
            Text(APPEARANCE_NOTICE)
        },
        confirmButton = { TextButton(onClick = onClose) { Text(APPEARANCE_NOTICE_OK) } },
    )
}
