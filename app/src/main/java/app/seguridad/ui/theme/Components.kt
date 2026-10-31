package app.seguridad.ui.theme

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
 * Secrets typed or shown in [content] can only leave through the app's own «Copiar» (pasting still works), with both
 * of Compose's text menus: the new one is filtered by a modifier on a box around [content] (it applies to the text
 * fields and SelectionContainers inside, in this window only: a dialog needs its own NoCopy inside), the old one by
 * [NoCopyToolbar]. [modifier] goes on that box, which passes its minimum size on to [content]: put a single layout in.
 */
@Composable
fun NoCopy(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val base = LocalTextToolbar.current
    val toolbar = remember(base) { base as? NoCopyToolbar ?: NoCopyToolbar(base) }
    CompositionLocalProvider(LocalTextToolbar provides toolbar) {
        Box(modifier.filterTextContextMenuComponents(NoCopyMenuFilter), propagateMinConstraints = true) { content() }
    }
}

/**
 * Password field with a show/hide switch; never put in saved state. Hidden, Compose offers no Copy; after «Ver» it
 * would, so the field is always inside [NoCopy] (TECLADO-2): the master password and every other one.
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
