package app.seguridad.ui.screens

import app.seguridad.settings.APPEARANCE_TITLE
import app.seguridad.settings.HIGH_CONTRAST
import app.seguridad.settings.HIGH_CONTRAST_SHORT
import app.seguridad.settings.REDUCE_TRANSPARENCY
import app.seguridad.settings.REDUCE_TRANSPARENCY_SHORT
import app.seguridad.settings.SettingItem
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
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
import app.seguridad.settings.STYLE_ORDER
import app.seguridad.settings.Settings
import app.seguridad.settings.SettingsStore
import app.seguridad.settings.ThemeMode
import app.seguridad.settings.ThemeStyle
import app.seguridad.ui.theme.AlertDialog
import app.seguridad.ui.theme.BackScaffold
import app.seguridad.ui.theme.GlassPanel
import app.seguridad.ui.theme.Look
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
import app.seguridad.ui.theme.shownPalette
import app.seguridad.vault.TagColor
import app.seguridad.vault.TagPalette
import kotlinx.coroutines.launch

// 2.0, task 5 bis: the texts of Ajustes → «Aspecto» (minimal text, M-3: a line on view, the rest under «Más información»).
// «Cristal» is only the name of a style here (O-C4); the blur is said to work from Android 12; nothing promises contrast
// as a guarantee (O-C6); the wallpaper's colours as O-C5 words them.
// 2.1, Aspecto (task 4): the five lines and the folded texts are the §4 of the Abogado's opinion, word for word
// (sdd-2.1/abogado-2.1-aspecto.md): the blur only of the three translucent styles (O-A5), «la imagen del fondo de
// pantalla» and «atenuado» for «Transparente», no promise of contrast, no other system's look named (O-A1).
internal const val STYLE_SOLID_SHORT = "Sólido: paneles opacos sobre un fondo liso, sin transparencias."
internal const val STYLE_GRADIENT_SHORT = "Degradado: paneles opacos, sin transparencias, sobre un fondo de color en degradado."
internal const val STYLE_GLASS_SHORT = "Cristal: paneles translúcidos sobre un fondo de color."
internal const val STYLE_GLASS_STRONG_SHORT = "Vidrio: como «Cristal», con paneles más translúcidos y bordes iluminados."
internal const val STYLE_TRANSPARENT_SHORT = "Transparente: deja ver, atenuado, el fondo de pantalla del móvil detrás de la app."
internal const val STYLE_MORE = "En «Cristal», «Vidrio» y «Transparente», desde Android 12, las barras y los diálogos difuminan lo que tienen " +
    "detrás; en Android 8 a 11 son más opacos. «Degradado» y «Sólido» no tienen transparencias. «Reducir transparencias» " +
    "y «Alto contraste» hacen opacos todos los paneles."
internal const val STYLE_TRANSPARENT_MORE = "La app no lee la imagen del fondo de pantalla ni pide permisos: Android la dibuja detrás " +
    "de la app. Desde Android 12, si el móvil lo permite, se ve difuminada. Quien mire la pantalla verá también tu fondo de " +
    "pantalla. Con un fondo de pantalla animado, el móvil puede gastar algo más de batería. Como en toda la app, sin " +
    "capturas de pantalla. Con «Reducir transparencias» o «Alto contraste», el fondo de pantalla deja de verse."
internal const val WALLPAPER_SHORT = "Usa los colores que Android calcula del fondo de pantalla (desde Android 12)."
internal const val WALLPAPER_MORE = "La app no lee la imagen del fondo de pantalla ni pide permisos. Ajusta los tonos para que el texto tenga contraste suficiente."
internal const val WALLPAPER_FROM_12 = "«Colores del fondo de pantalla», desde Android 12."
internal const val CUSTOM_SHORT = "Elige el acento, el fondo y la intensidad del cristal, y pulsa «Aplicar»."
// R-A5: the intensity of the glass sets the panels of the three translucent styles (Palettes.kt, `nominal`).
internal const val CUSTOM_MORE = "La app ajusta los tonos para que el texto tenga contraste suficiente. La vista previa enseña el cambio antes de aplicarlo. " +
    "La intensidad del cristal se aplica a «Cristal», «Vidrio» y «Transparente»."
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

/** R-C8: the one line after updating, with «Entendido» ([app.seguridad.settings.AppearanceNotice]). */
internal const val APPEARANCE_NOTICE = "Aspecto nuevo. Para volver a uno sin transparencias: Ajustes → Aspecto → «Sólido»."
internal const val APPEARANCE_NOTICE_OK = "Entendido"

/** The line on view under the styles: the one of [style]. */
internal fun styleShort(style: ThemeStyle): String = when (style) {
    ThemeStyle.SOLID -> STYLE_SOLID_SHORT
    ThemeStyle.GRADIENT -> STYLE_GRADIENT_SHORT
    ThemeStyle.GLASS -> STYLE_GLASS_SHORT
    ThemeStyle.GLASS_STRONG -> STYLE_GLASS_STRONG_SHORT
    ThemeStyle.TRANSPARENT -> STYLE_TRANSPARENT_SHORT
}

/** What «Más información» under the styles holds with [style] chosen (M-3): the blur, and with «Transparente» its own text. */
internal fun styleMore(style: ThemeStyle): List<String> =
    if (style == ThemeStyle.TRANSPARENT) listOf(STYLE_MORE, STYLE_TRANSPARENT_MORE) else listOf(STYLE_MORE)

private fun ThemeMode.modeLabel() = when (this) {
    ThemeMode.DARK -> "Oscuro"
    ThemeMode.LIGHT -> "Claro"
    ThemeMode.SYSTEM -> "Como el sistema"
}

/** The line of Ajustes that sums up the look: «Cristal · Noche · Oscuro». */
internal fun appearanceSummary(s: Settings, sdk: Int = Build.VERSION.SDK_INT): String =
    "${s.style.label()} · ${shownPalette(s.palette, s.custom, sdk).label()} · ${s.theme.modeLabel()}"

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
    // Review M-3: a failed save is told as in Ajustes (a snackbar, which shows wherever the user is in the screen).
    val snackbar = remember { SnackbarHostState() }
    fun change(onSaved: () -> Unit = {}, f: (Settings) -> Settings) {
        scope.launch {
            try {
                store.update(f)
            } catch (e: Exception) {
                snackbar.showSnackbar(SETTING_SAVE_FAILED)
                return@launch
            }
            onSaved()
        }
    }
    val saved = settings.custom ?: CustomPalette(accentHue = 1, backgroundHue = 8, glass = GlassLevel.MEDIUM)
    var editing by rememberSaveable { mutableStateOf(false) }
    var accent by rememberSaveable { mutableIntStateOf(saved.accentHue) }
    var background by rememberSaveable { mutableIntStateOf(saved.backgroundHue) }
    var glass by rememberSaveable { mutableStateOf(saved.glass) }
    val draft = remember(accent, background, glass) { fixContrast(CustomPalette(accent, background, glass)) }
    val customOpen = editing || (settings.palette == PaletteId.CUSTOM && settings.custom != null)
    val wallpaperOffered = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    BackScaffold(APPEARANCE_TITLE, onBack, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Preview(settings, if (customOpen) draft else null)
            Section(SettingItem.MODE.title) {
                Choices(ThemeMode.entries, settings.theme, { it.modeLabel() }) { v -> change { it.copy(theme = v) } }
            }
            Section(SettingItem.STYLE.title) {
                Choices(STYLE_ORDER, settings.style, { it.label() }) { v -> change { it.copy(style = v) } }
                Hint(styleShort(settings.style))
                MoreInfo { styleMore(settings.style).forEach { Hint(it) } }
            }
            Section(SettingItem.PALETTE.title) {
                val offered = PaletteId.entries.filter { it != PaletteId.WALLPAPER || wallpaperOffered }
                // Review M-5: what is drawn is what is marked (the wallpaper's colours before Android 12, or a
                // «Personalizado» without tones, are drawn as «Noche»).
                val shown = if (customOpen) PaletteId.CUSTOM else shownPalette(settings.palette, settings.custom, Build.VERSION.SDK_INT)
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
                        // Review M-3: the editor closes only once the palette is saved; a failure keeps the draft on screen.
                        onClick = { change(onSaved = { editing = false }) { it.copy(palette = PaletteId.CUSTOM, custom = draft) } },
                        enabled = !applied,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(CUSTOM_APPLY) }
                }
            }
            // Review M-4: a title of its own for both switches.
            Section(SettingItem.TRANSPARENCY.title) {
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
        else lookFor(
            settings.style, PaletteId.CUSTOM, custom, settings.reduceTransparency, settings.highContrast, dark,
            sdk = Build.VERSION.SDK_INT, wallpaperBlur = current.wallpaperBlur,
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(PREVIEW_TITLE, style = MaterialTheme.typography.titleSmall)
        // «Transparente»: the preview cannot show the wallpaper, so its layer is drawn over the app's own gradient, opaque
        // underneath; nothing behind the window shows through the preview.
        Box(
            Modifier.fillMaxWidth().height(196.dp).clip(SectionShape)
                .drawBehind { if (look.wallpaperBehind) drawPreviewUnderlay(look, size) }
                .clearAndSetSemantics { contentDescription = PREVIEW_DESCRIPTION },
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
                                color = Color(if (look.dark) TagPalette.dark(TagColor.BLUE) else TagPalette.light(TagColor.BLUE)),
                            )
                        }
                    }
                    Button(onClick = {}, modifier = Modifier.padding(horizontal = 12.dp)) { Text(PREVIEW_BUTTON) }
                }
            }
        }
    }
}

/** Under the preview of «Transparente»: the gradient of the app, opaque, where the wallpaper would be. */
private fun DrawScope.drawPreviewUnderlay(look: Look, area: Size) {
    val bg = look.colors.background
    drawRect(Brush.verticalGradient(listOf(Color(bg.top), Color(bg.bottom)), startY = 0f, endY = area.height), size = area)
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
