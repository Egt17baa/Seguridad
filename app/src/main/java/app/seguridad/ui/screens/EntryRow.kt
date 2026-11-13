package app.seguridad.ui.screens

import androidx.compose.animation.core.animate
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.seguridad.android.FilterObscuredTouches
import app.seguridad.crypto.TOTP_UNSUPPORTED_ROW
import app.seguridad.crypto.isSupported
import app.seguridad.crypto.totpCode
import app.seguridad.imports.plainReportText
import app.seguridad.tools.ROW_NEXT_CODE_BELOW_S
import app.seguridad.tools.TwoStepCopy
import app.seguridad.ui.theme.DropdownMenu
import app.seguridad.ui.theme.NegativeColor
import app.seguridad.ui.theme.RowPanel
import app.seguridad.ui.theme.RowShape
import app.seguridad.ui.theme.rowRevealColors
import app.seguridad.vault.CODE_TAP_TO_COPY
import app.seguridad.vault.COPY_PASSWORD
import app.seguridad.vault.COPY_TOTP
import app.seguridad.vault.COPY_USERNAME
import app.seguridad.vault.DRAG_HANDLE
import app.seguridad.vault.DUPLICATE
import app.seguridad.vault.EDIT
import app.seguridad.vault.EntryType
import app.seguridad.vault.Folder
import app.seguridad.vault.MOVE_TO
import app.seguridad.vault.MOVE_TO_TRASH
import app.seguridad.vault.ROW_SELECT
import app.seguridad.vault.TagColor
import app.seguridad.vault.VaultEntry
import app.seguridad.vault.VaultState
import app.seguridad.vault.VaultStore
import app.seguridad.vault.codeSecondsLeft
import app.seguridad.vault.copyPasswordOffer
import app.seguridad.vault.passwordForHealth
import app.seguridad.vault.rowMenuLabel
import app.seguridad.vault.seedWordCount
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.roundToInt

/** How far a row has to be swiped, as a part of its width, for its action when let go (design «Deslizar»): 40 %. */
internal const val SWIPE_THRESHOLD = 0.4f

/** What a swipe of a row does when let go. */
internal enum class SwipeAction { EDIT, TRASH }

/**
 * The action of a row swiped [dx] pixels (negative to the left) out of [width]: to the left (right to left), «Editar»;
 * to the right (left to right), «Mover a la papelera», which asks first. Nothing under [SWIPE_THRESHOLD] of the width.
 */
internal fun swipeAction(dx: Float, width: Int): SwipeAction? {
    if (width <= 0 || abs(dx) < SWIPE_THRESHOLD * width) return null
    return if (dx < 0) SwipeAction.EDIT else SwipeAction.TRASH
}

/**
 * What a row can do, given by the list. Only the entry (or its id) goes in: copying reads what it copies from the open
 * vault at the tap ([liveEntry]), never from a value kept by the row.
 */
internal class RowActions(
    val open: (VaultEntry) -> Unit,
    val edit: (VaultEntry) -> Unit,
    val askTrash: (VaultEntry) -> Unit,
    val toggleSelect: (VaultEntry) -> Unit,
    val copyUsername: (VaultEntry) -> Unit,
    val copyPassword: (VaultEntry) -> Unit,
    val copyCode: (VaultEntry) -> Unit,
    val duplicate: (VaultEntry) -> Unit,
    val moveTo: (VaultEntry) -> Unit,
    val onDrag: (VaultEntry, Offset?) -> Unit,
    val onDrop: (VaultEntry) -> Unit,
)

/** Entry [id] as the open vault holds it now; null when it is gone or the vault is not open. */
internal fun liveEntry(store: VaultStore, id: String): VaultEntry? =
    (store.state.value as? VaultState.Unlocked)?.entries?.firstOrNull { it.id == id && !it.type.isPersonal }

/** The line under a row's title. */
private fun subline(e: VaultEntry): String = when (e.type) {
    EntryType.LOGIN -> e.username.ifBlank { e.url }
    EntryType.SEED -> "${seedWordCount(e.seedWords).first} palabras"
    EntryType.NOTE -> "Nota"
    EntryType.TOTP -> listOf(e.totp?.issuer, e.totp?.account).filter { !it.isNullOrBlank() }.joinToString(" · ")
    EntryType.IDENTITY, EntryType.CONTACT -> "" // Never listed here.
    EntryType.CARD, EntryType.WIFI, EntryType.LICENSE, EntryType.PASSPORT -> e.type.label
}

/**
 * One entry of the list (design «La lista y la ficha»): a glass panel ([RowPanel]) that opens the entry; held, it starts
 * the selection ([selecting]: then a tap selects). Swiped past [SWIPE_THRESHOLD] of its width and let go: to the left
 * «Editar», to the right «Mover a la papelera» (asked first, then «Deshacer» for 5 s); a light vibration when it passes
 * the threshold ([HapticFeedbackType.LongPress], without the VIBRATE permission, R-7). Both are also TalkBack's actions
 * of the row, as is «Mover a…». The «⋮» menu copies the user, the password or the 2FA, edits, duplicates, moves or sends
 * to the trash. A 2FA code is copied with a tap ([RowActions.copyCode]), with a circle of the seconds left. With
 * [dragEnabled], a handle at the end drags it to a folder ([RowActions.onDrag], [RowActions.onDrop]). [now] is read only
 * by the 2FA, so the other rows are not drawn again every second.
 */
@Composable
internal fun EntryRow(
    e: VaultEntry,
    now: State<Long>,
    folders: List<Folder>,
    tagColors: Map<String, TagColor>,
    selecting: Boolean,
    selected: Boolean,
    dragEnabled: Boolean,
    canMove: Boolean,
    actions: RowActions,
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var width by remember { mutableIntStateOf(0) }
    var dx by remember { mutableFloatStateOf(0f) }
    var armed by remember { mutableStateOf(false) }
    val drag = rememberDraggableState { delta ->
        dx = (dx + delta).coerceIn(-width.toFloat(), width.toFloat())
        val now = swipeAction(dx, width) != null
        if (now && !armed) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        armed = now
    }
    val title = plainReportText(e.title)
    val rowActions = buildList {
        add(CustomAccessibilityAction(EDIT) { actions.edit(e); true })
        add(CustomAccessibilityAction(MOVE_TO_TRASH) { actions.askTrash(e); true })
        if (canMove) add(CustomAccessibilityAction(MOVE_TO) { actions.moveTo(e); true })
    }
    Box(
        Modifier.fillMaxWidth().draggable(
            drag,
            Orientation.Horizontal,
            enabled = !selecting,
            onDragStopped = {
                val action = swipeAction(dx, width)
                armed = false
                scope.launch { animate(dx, 0f) { v, _ -> dx = v } }
                when (action) {
                    SwipeAction.EDIT -> actions.edit(e)
                    SwipeAction.TRASH -> actions.askTrash(e)
                    null -> Unit
                }
            },
        ),
    ) {
        if (dx != 0f) SwipeReveal(edit = dx < 0, dx = dx, modifier = Modifier.matchParentSize())
        RowPanel(
            onClick = { if (selecting) actions.toggleSelect(e) else actions.open(e) },
            onLongClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                actions.toggleSelect(e)
            },
            selected = if (selecting) selected else null,
            onLongClickLabel = ROW_SELECT,
            modifier = Modifier.fillMaxWidth()
                .onSizeChanged { width = it.width }
                .offset { IntOffset(dx.roundToInt(), 0) }
                .semantics { customActions = rowActions },
        ) {
            if (selecting) {
                // The row says it is selected (RowPanel); the box is only its picture.
                Checkbox(checked = selected, onCheckedChange = null, modifier = Modifier.clearAndSetSemantics { })
            } else {
                Text(typeIcon(e.type), style = MaterialTheme.typography.titleLarge, modifier = Modifier.clearAndSetSemantics { })
            }
            Column(Modifier.weight(1f)) {
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val sub = subline(e)
                if (sub.isNotBlank()) Text(plainReportText(sub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                val folder = e.folderId?.let { id -> folders.firstOrNull { it.id == id } }
                if (folder != null || e.tags.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        folder?.let { Text("📁 " + plainReportText(it.name, 30), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
                        e.tags.take(3).forEach { t -> Text("#${plainReportText(t)}", style = MaterialTheme.typography.labelSmall, color = tagTextColor(tagColors[t]), maxLines = 1) }
                    }
                }
            }
            if (e.favorite) Text("★", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            e.totp?.let { t ->
                if (!t.isSupported()) {
                    // A setting this app does not calculate: why there is no code, as small as the seconds of one (F4).
                    Text(TOTP_UNSUPPORTED_ROW, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                } else {
                    TotpBadge(e, now, enabled = !selecting, onCopy = { actions.copyCode(e) })
                }
            }
            if (!selecting) RowMenu(e, title, canMove, actions)
            if (dragEnabled && !selecting) DragHandle(e, actions)
        }
    }
}

/** What a swipe uncovers, only in the strip the row leaves ([rowRevealColors]): never under the translucent row. */
@Composable
private fun SwipeReveal(edit: Boolean, dx: Float, modifier: Modifier) {
    val (fg, bg) = rowRevealColors(edit)
    Box(
        modifier.clip(RowShape).drawWithContent {
            val left = if (edit) size.width + dx else 0f
            val right = if (edit) size.width else dx
            clipRect(left, 0f, right, size.height) {
                drawRect(bg)
                this@drawWithContent.drawContent()
            }
        }.padding(horizontal = 20.dp),
        contentAlignment = if (edit) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Text(if (edit) EDIT else MOVE_TO_TRASH, color = fg, style = MaterialTheme.typography.labelLarge, modifier = Modifier.clearAndSetSemantics { })
    }
}

/** The 2FA code of a row: a tap copies it (the next one with fewer than 5 s left), with a circle of the seconds left. */
@Composable
private fun TotpBadge(e: VaultEntry, now: State<Long>, enabled: Boolean, onCopy: () -> Unit) {
    val t = e.totp ?: return
    val (code, left) = totpCode(t, now.value) ?: return
    val low = left < ROW_NEXT_CODE_BELOW_S
    val ring = if (low) NegativeColor else MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.outlineVariant
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.clip(RowShape).clickable(enabled = enabled, onClickLabel = COPY_TOTP) { onCopy() }.padding(4.dp)
            .semantics(mergeDescendants = true) { contentDescription = "${code.chunked(3).joinToString(" ")}, ${codeSecondsLeft(left)}. $CODE_TAP_TO_COPY" },
    ) {
        Text(code.chunked(3).joinToString(" "), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.titleMedium)
        val part = left.toFloat() / t.period
        Canvas(Modifier.size(18.dp)) {
            val stroke = 2.5.dp.toPx()
            val inset = stroke / 2
            val arc = Size(size.width - stroke, size.height - stroke)
            drawArc(track, 0f, 360f, useCenter = false, topLeft = Offset(inset, inset), size = arc, style = Stroke(stroke))
            drawArc(ring, -90f, 360f * part, useCenter = false, topLeft = Offset(inset, inset), size = arc, style = Stroke(stroke))
        }
    }
}

/** The «⋮» of a row: copy, edit, duplicate, move and the trash. A menu window filters obscured touches (ANDROID-3). */
@Composable
private fun RowMenu(e: VaultEntry, title: String, canMove: Boolean, actions: RowActions) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Default.MoreVert, contentDescription = rowMenuLabel(title)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            FilterObscuredTouches()
            val items = buildList<Pair<String, () -> Unit>> {
                if (e.username.isNotBlank()) add(COPY_USERNAME to { actions.copyUsername(e) })
                if (e.passwordForHealth().isNotEmpty()) add(COPY_PASSWORD to { actions.copyPassword(e) })
                if (e.totp?.isSupported() == true) add(COPY_TOTP to { actions.copyCode(e) })
                add(EDIT to { actions.edit(e) })
                add(DUPLICATE to { actions.duplicate(e) })
                if (canMove) add(MOVE_TO to { actions.moveTo(e) })
            }
            items.forEach { (text, act) ->
                DropdownMenuItem(text = { Text(text) }, onClick = {
                    open = false
                    act()
                })
            }
            DropdownMenuItem(text = { Text(MOVE_TO_TRASH, color = NegativeColor) }, onClick = {
                open = false
                actions.askTrash(e)
            })
        }
    }
}

/** The handle that drags a row to a folder (task 4); TalkBack has «Mover a…» as an action of the row instead. */
@Composable
private fun DragHandle(e: VaultEntry, actions: RowActions) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    var at by remember { mutableStateOf(Offset.Zero) }
    Text(
        "⠿",
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(32.dp)
            .onGloballyPositioned { origin = it.positionInRoot() }
            .semantics { contentDescription = DRAG_HANDLE }
            .pointerInput(e.id) {
                detectDragGestures(
                    onDragStart = { p ->
                        at = origin + p
                        actions.onDrag(e, at)
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        at += amount
                        actions.onDrag(e, at)
                    },
                    onDragEnd = { actions.onDrop(e) },
                    onDragCancel = { actions.onDrag(e, null) },
                )
            },
    )
}

/**
 * The copy in two steps (design «Copiar usuario y luego contraseña»): back in the app ([Lifecycle.Event.ON_RESUME]) after
 * «Copiar usuario», while [TwoStepCopy] still offers it, a message offers «Copiar contraseña» of that entry for what is
 * left of its 30 s. Only the id comes from [twoStep]: the password is read from the open vault at the tap ([liveEntry]).
 */
@Composable
internal fun PasswordOfferOnReturn(store: VaultStore, twoStep: TwoStepCopy, snackbar: SnackbarHostState, copyPassword: (VaultEntry) -> Unit) {
    var returns by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { returns++ }
    LaunchedEffect(returns) {
        if (returns == 0) return@LaunchedEffect
        val id = twoStep.pending() ?: return@LaunchedEffect
        val entry = liveEntry(store, id)?.takeIf { it.passwordForHealth().isNotEmpty() } ?: return@LaunchedEffect twoStep.clear()
        snackbar.currentSnackbarData?.dismiss()
        val answer = withTimeoutOrNull(twoStep.leftMs()) {
            snackbar.showSnackbar(copyPasswordOffer(plainReportText(entry.title)), actionLabel = COPY_PASSWORD, duration = SnackbarDuration.Indefinite)
        }
        if (answer == SnackbarResult.ActionPerformed && twoStep.pending() == id) liveEntry(store, id)?.let(copyPassword)
    }
}
