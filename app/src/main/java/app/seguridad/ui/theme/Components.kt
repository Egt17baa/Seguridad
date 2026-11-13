package app.seguridad.ui.theme

import android.view.KeyEvent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ripple
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuComponent
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuKeys
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuSeparator
import androidx.compose.foundation.text.contextmenu.modifier.filterTextContextMenuComponents
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import app.seguridad.android.FilterObscuredTouches
import app.seguridad.tools.QR_SCAN

val SectionShape = RoundedCornerShape(16.dp)

/**
 * A card that groups one result or one block of settings: in «Cristal» a glass panel ([glass], never blurred), in «Sólido»
 * the surface colour with a thin outline, as in 1.4.x. Every screen's cards are these, so they all take the look.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SectionCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val look = LocalLook.current
    val glass = look.glass
    val colors = CardDefaults.cardColors(containerColor = if (glass) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer)
    // «Alto contraste» marks the edge in «Sólido» too (the glass draws its own).
    val border = when {
        glass -> null
        look.highContrast -> BorderStroke(look.colors.borderWidth.dp, look.colors.border)
        else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    }
    val m = if (glass) modifier.glass(PanelLevel.CARD, SectionShape) else modifier
    if (onClick != null) {
        // Review M-8: the brief glow of a pressed panel (Material's ripple), and none when Android removes animations;
        // then a still mark while it is pressed (re-review N-2), in «Cristal» and in «Sólido».
        val animationsOn = LocalAnimationsOn.current
        val interaction = remember { MutableInteractionSource() }
        CompositionLocalProvider(LocalRippleConfiguration provides panelRipple(animationsOn)) {
            Card(
                onClick = onClick,
                modifier = m.pressSignal(interaction, SectionShape, animationsOn),
                shape = SectionShape,
                colors = colors,
                border = border,
                interactionSource = interaction,
                content = content,
            )
        }
    } else {
        Card(modifier = m, shape = SectionShape, colors = colors, border = border, content = content)
    }
}

/** The shape of a row of the vault list ([RowPanel]). */
val RowShape = RoundedCornerShape(12.dp)

/**
 * 2.0, task 6: a row of the vault list. In «Cristal» a glass panel that never blurs ([PanelLevel.CARD]: the list scrolls
 * smoothly), in «Sólido» the surface colour with a thin outline, as [SectionCard]. Pressed, it gives the same signs as
 * [SectionCard]: Material's brief glow while Android has animations on, and the still mark of [pressSignal] when it asks
 * to remove them. [onLongClick] is «mantener pulsado» (the selection). [selected] is null while the list is not selecting;
 * a selected row has an outline in the accent (3:1 on every surface, the same as the still mark) and says so to TalkBack.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun RowPanel(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean? = null,
    onClickLabel: String? = null,
    onLongClickLabel: String? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val look = LocalLook.current
    val animationsOn = LocalAnimationsOn.current
    val interaction = remember { MutableInteractionSource() }
    val surface = when {
        look.glass -> modifier.glass(PanelLevel.CARD, RowShape)
        else -> modifier.background(MaterialTheme.colorScheme.surfaceContainer, RowShape)
            .border(if (look.highContrast) look.colors.borderWidth.dp else 1.dp, if (look.highContrast) look.colors.border else MaterialTheme.colorScheme.outlineVariant, RowShape)
    }
    val marked = if (selected == true) surface.border(2.dp, MaterialTheme.colorScheme.primary, RowShape) else surface
    CompositionLocalProvider(LocalRippleConfiguration provides panelRipple(animationsOn), LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = marked
                .clip(RowShape)
                .combinedClickable(
                    interactionSource = interaction,
                    indication = ripple(),
                    onClickLabel = onClickLabel,
                    onLongClickLabel = onLongClickLabel,
                    onLongClick = onLongClick,
                    onClick = onClick,
                )
                .pressSignal(interaction, RowShape, animationsOn)
                .then(if (selected != null) Modifier.semantics { this.selected = selected } else Modifier)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            content = content,
        )
    }
}

/**
 * The colours of what a swipe of a row uncovers ([content] on [container]): «Editar» on the accent's container, «Mover a
 * la papelera» on the error's. Both pairs are measured by the contrast tests ([edit] true for «Editar»). They are drawn only
 * in the strip the row leaves, never under the translucent row.
 */
@Composable
@ReadOnlyComposable
fun rowRevealColors(edit: Boolean): Pair<Color, Color> = rowRevealColors(MaterialTheme.colorScheme, edit)

/** [rowRevealColors] of [scheme]. */
fun rowRevealColors(scheme: androidx.compose.material3.ColorScheme, edit: Boolean): Pair<Color, Color> =
    if (edit) scheme.onPrimaryContainer to scheme.primaryContainer else scheme.onErrorContainer to scheme.errorContainer

/** Screen with a title and a back arrow. */
@Composable
fun BackScaffold(title: String, onBack: () -> Unit, snackbarHost: @Composable () -> Unit = {}, content: @Composable (PaddingValues) -> Unit) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = snackbarHost,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás") }
                },
            )
        },
        content = content,
    )
}

/**
 * 2.0, task 5 bis: the app's top bar. In «Cristal» a glass bar ([PanelLevel.BAR]: blurred from Android 12, more opaque
 * before); in «Sólido» Material's. The content of the screen is laid out below it (the Scaffold's padding), so nothing
 * with the focus is ever under it (R-C7).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopAppBar(title: @Composable () -> Unit, navigationIcon: @Composable () -> Unit = {}, actions: @Composable RowScope.() -> Unit = {}) {
    if (!LocalLook.current.glass) {
        androidx.compose.material3.TopAppBar(title = title, navigationIcon = navigationIcon, actions = actions)
        return
    }
    androidx.compose.material3.TopAppBar(
        title = title,
        modifier = Modifier.glass(PanelLevel.BAR, panelShape(PanelLevel.BAR)),
        navigationIcon = navigationIcon,
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent),
    )
}

/** 2.0, task 5 bis: the app's bottom bar, glass in «Cristal» as [TopAppBar]. */
@Composable
fun NavigationBar(content: @Composable RowScope.() -> Unit) {
    if (!LocalLook.current.glass) {
        androidx.compose.material3.NavigationBar(content = content)
        return
    }
    androidx.compose.material3.NavigationBar(
        modifier = Modifier.glass(PanelLevel.BAR, panelShape(PanelLevel.BAR)),
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        content = content,
    )
}

/**
 * 2.0, task 5 bis: every dialog of the app. In «Cristal» a glass panel ([PanelLevel.OVERLAY]: blurred from Android 12);
 * in «Sólido» Material's. The same name and the same parameters as Material's, so a screen only imports this one; it
 * filters obscured touches in its window like every dialog (ANDROID-3), and the caller still does so in its own text.
 */
@Composable
fun AlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    properties: DialogProperties = DialogProperties(),
) {
    val glass = LocalLook.current.glass
    val shape = panelShape(PanelLevel.OVERLAY)
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            FilterObscuredTouches()
            confirmButton()
        },
        modifier = if (glass) modifier.glass(PanelLevel.OVERLAY, shape) else modifier,
        dismissButton = dismissButton,
        icon = icon,
        title = title,
        text = text,
        shape = shape,
        containerColor = if (glass) Color.Transparent else AlertDialogDefaults.containerColor,
        tonalElevation = if (glass) 0.dp else AlertDialogDefaults.TonalElevation,
        properties = properties,
    )
}

/**
 * 2.0, task 5 bis: every menu of the app. In «Cristal» translucent with the glass edge, never blurred, and opaque enough
 * to read over anything under it ([AppColors.floorAlpha]); in «Sólido» Material's. As [AlertDialog], the same name and
 * parameters as Material's.
 */
@Composable
fun DropdownMenu(expanded: Boolean, onDismissRequest: () -> Unit, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val look = LocalLook.current
    if (!look.glass) {
        androidx.compose.material3.DropdownMenu(expanded = expanded, onDismissRequest = onDismissRequest, modifier = modifier) {
            FilterObscuredTouches()
            content()
        }
        return
    }
    val colors = look.colors
    androidx.compose.material3.DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        containerColor = colors.panelColor(PanelLevel.OVERLAY).copy(alpha = look.alpha(PanelLevel.OVERLAY, blurNow = false)),
        tonalElevation = 0.dp,
        border = BorderStroke(colors.borderWidth.dp, colors.border),
    ) {
        FilterObscuredTouches()
        content()
    }
}

/**
 * The old system text menu ([LocalTextToolbar], used by Compose when its new menu is off) without Copy, Cut and
 * Autofill: a copy would skip the sensitive flag and the timed clearing, and a manual autofill request would hand the
 * field to the autofill service (M1).
 */
internal class NoCopyToolbar(private val base: TextToolbar) : TextToolbar {
    override val status: TextToolbarStatus get() = base.status

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
    ) = base.showMenu(rect, null, onPasteRequested, null, onSelectAllRequested)

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
        onAutofillRequested: (() -> Unit)?,
    ) = base.showMenu(rect, null, onPasteRequested, null, onSelectAllRequested, null)

    override fun hide() = base.hide()
}

/**
 * TECLADO-1 (audit 1.3.0): what stays in the new text menu of Compose foundation 1.9+ (on by default, and it never
 * asks [LocalTextToolbar]): «Pegar», «Seleccionar todo» and the separators. An allow-list, so Copy, Cut, Autofill, the
 * other apps' PROCESS_TEXT actions («Traducir»…) and the text classification items go, and so does anything a later
 * Compose adds.
 */
internal fun keptInNoCopyMenu(component: TextContextMenuComponent): Boolean =
    component is TextContextMenuSeparator ||
        component.key == TextContextMenuKeys.PasteKey ||
        component.key == TextContextMenuKeys.SelectAllKey

private val NoCopyMenuFilter: (TextContextMenuComponent) -> Boolean = ::keptInNoCopyMenu

/**
 * F4 review M1: Copy and Cut that do not go through a menu. Compose 1.9.0 (`KeyMappingKt.commonKeyMapping`) copies on
 * Ctrl+C, Ctrl+Insert and KEYCODE_COPY, and cuts on Ctrl+X and KEYCODE_CUT, also in a hidden password field (that path
 * has no password check). A keyboard's editing panel («Copiar»/«Cortar» in Gboard's toolbar) calls
 * `InputConnection.performContextMenuAction`, which Compose's `RecordingInputConnection` turns into KEYCODE_COPY or
 * KEYCODE_CUT sent to the view. Shift+Delete (KEYCODE_FORWARD_DEL) is not Cut in 1.9.0, but is in other toolkits:
 * caught too. Pasting (Ctrl+V, Shift+Insert, KEYCODE_PASTE) is not.
 */
internal fun isCopyOrCutKey(keyCode: Int, ctrl: Boolean, shift: Boolean): Boolean = when (keyCode) {
    KeyEvent.KEYCODE_COPY, KeyEvent.KEYCODE_CUT -> true
    KeyEvent.KEYCODE_C, KeyEvent.KEYCODE_X, KeyEvent.KEYCODE_INSERT -> ctrl
    KeyEvent.KEYCODE_FORWARD_DEL -> shift && !ctrl
    else -> false
}

/**
 * Secrets typed or shown in [content] do not leave through the system's Copy or Cut, so they only go out with the
 * sensitive flag and the timed clearing of the app's own «Copiar» (pasting still works):
 * - the new text menu of Compose 1.9+ is filtered by a modifier on a box around [content] ([keptInNoCopyMenu]); it
 *   applies to the text fields and SelectionContainers inside, in this window only: a dialog needs its own NoCopy;
 * - the old menu, by [NoCopyToolbar];
 * - the keyboard shortcuts and the keyboard's editing panel, by a preview key handler on the same box
 *   ([isCopyOrCutKey]): an ancestor's preview runs before the focused field (Compose `FocusOwnerImpl`), and the
 *   panel's copy and cut arrive as key events.
 *
 * Not covered, and no parent can cover them: an accessibility service's «Copiar» action (TalkBack's editing actions)
 * on any field that is not a hidden password (a [PasswordField] after «Ver» included), since Compose gives every such
 * field copyText and cutText semantics (F4 re-review N3); and
 * a keyboard that reads the field itself through its InputConnection (it already sees what is typed) and copies it
 * on its own. [modifier] goes on the box, which passes its minimum size on to [content]: put a single layout in.
 */
@Composable
fun NoCopy(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val base = LocalTextToolbar.current
    val toolbar = remember(base) { base as? NoCopyToolbar ?: NoCopyToolbar(base) }
    CompositionLocalProvider(LocalTextToolbar provides toolbar) {
        Box(
            modifier
                .filterTextContextMenuComponents(NoCopyMenuFilter)
                // Both the key down and the key up are consumed: the field never sees either.
                .onPreviewKeyEvent { isCopyOrCutKey(it.nativeKeyEvent.keyCode, it.isCtrlPressed, it.isShiftPressed) },
            propagateMinConstraints = true,
        ) { content() }
    }
}

/**
 * The keyboard of a field whose text can be a secret and is shown as typed (a text to encrypt or hash, an HMAC message,
 * a key of a classic cipher): the password keyboard type with autocorrection off, so the keyboard offers no suggestions
 * and does not learn the words into its dictionary (MASVS-STORAGE-2). It does not mask the text (no visual
 * transformation): that is [PasswordField]'s, for the fields that are hidden.
 */
val SECRET_TEXT_KEYBOARD = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password)

/**
 * Password field with a show/hide switch; never put in saved state. Hidden, Compose's menus offer no Copy, but after
 * «Ver» they would, and a keyboard shortcut copies it even hidden: so the field is always inside [NoCopy] (TECLADO-2,
 * F4 review M1), the master password and every other one.
 */
@Composable
fun PasswordField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, label: String = "Contraseña") {
    var visible by remember { mutableStateOf(false) }
    NoCopy(modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(label) },
            singleLine = true,
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
            trailingIcon = { TextButton(onClick = { visible = !visible }) { Text(if (visible) "Ocultar" else "Ver", style = MaterialTheme.typography.labelMedium) } },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * The label of every «Escanear QR» button. R-13 (final review of the Abogado Digital): the camera is decoration, so
 * TalkBack reads «Escanear QR», not «cámara, Escanear QR» (as the tools' icons, M-11).
 */
@Composable
fun ScanQrLabel() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("📷", Modifier.clearAndSetSemantics { })
        Spacer(Modifier.width(6.dp))
        Text(QR_SCAN)
    }
}
