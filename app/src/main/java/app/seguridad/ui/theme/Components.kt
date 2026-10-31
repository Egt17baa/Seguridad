package app.seguridad.ui.theme

import android.view.KeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuComponent
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuKeys
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuSeparator
import androidx.compose.foundation.text.contextmenu.modifier.filterTextContextMenuComponents
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.unit.dp

val SectionShape = RoundedCornerShape(16.dp)

/** A plain card on the surface color with a thin outline: groups one result or one block of settings. */
@Composable
fun SectionCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier, shape = SectionShape, colors = colors, border = border, content = content)
    } else {
        Card(modifier = modifier, shape = SectionShape, colors = colors, border = border, content = content)
    }
}

/** Screen with a title and a back arrow. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackScaffold(title: String, onBack: () -> Unit, content: @Composable (PaddingValues) -> Unit) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
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
