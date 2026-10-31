package app.seguridad.ui.screens

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewModelScope
import app.seguridad.tools.codes.Cell
import app.seguridad.tools.codes.CellFactory
import app.seguridad.tools.codes.LockSeen
import app.seguridad.tools.codes.ScreenGuard
import app.seguridad.vault.VaultState
import app.seguridad.vault.VaultStore
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Compose state for the sessions of tools/codes and tools/cipher: what a ViewModel holds redraws the screen when it changes. */
internal object ComposeCells : CellFactory {
    override fun <T> cell(initial: T): Cell<T> = object : Cell<T> {
        private val state = mutableStateOf(initial)
        override var value: T
            get() = state.value
            set(v) {
                state.value = v
            }
    }

    override fun <T> list(): MutableList<T> = mutableStateListOf()
}

/**
 * Tells [guard], for the whole life of this ViewModel (its navigation entry, also while another tab is shown), when
 * another app takes the focus in split screen or a floating window ([awayInOtherApp], no ON_STOP comes; COV2-3) and
 * when the vault locks, with why and whether the app was in use then, as the store recorded it at the lock itself. The
 * same as «PIN y códigos» (CodesViewModel). The first value of each flow does nothing.
 */
internal fun ViewModel.watchAway(guard: ScreenGuard, store: VaultStore, lockAfterMs: () -> Long, awayInOtherApp: StateFlow<Boolean>) {
    viewModelScope.launch {
        awayInOtherApp.collect { away ->
            if (away) guard.otherAppFocused(SystemClock.elapsedRealtime()) else guard.otherAppLeft(SystemClock.elapsedRealtime(), lockAfterMs())
        }
    }
    viewModelScope.launch {
        store.state.collect {
            guard.vaultStateIs(it is VaultState.Unlocked) {
                LockSeen(store.lastLockReason, store.lastLockInForeground, SystemClock.elapsedRealtime(), lockAfterMs())
            }
        }
    }
}

/**
 * The screen's side of [guard]: ON_STOP and ON_START (the time away, measured on the monotonic clock), and the screen
 * leaving the composition (another tab, whose time away counts from there). ON_START is also replayed when the screen
 * is composed again (a rotation, back from another tab): a stop of the same trip is used once, and a first visit has
 * none. As in CodesScreen.
 */
@Composable
internal fun GuardEffects(guard: ScreenGuard, lockAfterMs: () -> Long) {
    val currentLockAfter by rememberUpdatedState(lockAfterMs)
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { guard.stopped(SystemClock.elapsedRealtime()) }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { guard.started(SystemClock.elapsedRealtime(), currentLockAfter()) }
    DisposableEffect(guard) { onDispose { guard.stopped(SystemClock.elapsedRealtime()) } }
}

/**
 * M1 of the F6 review: a change of a text field is use of the screen too. The soft keyboard sends no key event and no tap
 * lands on the app window ([usedFor] sees neither), so every `onValueChange` calls this (as CodesScreen does).
 */
internal fun ScreenGuard.typed() = used(SystemClock.elapsedRealtime())

/** Any tap or key on the screen is use of it, for the idle lock (it never touches the vault: no store.touch()). */
internal fun Modifier.usedFor(guard: ScreenGuard): Modifier = this
    .pointerInput(guard) {
        awaitPointerEventScope {
            while (true) {
                awaitPointerEvent(PointerEventPass.Initial)
                guard.used(SystemClock.elapsedRealtime())
            }
        }
    }
    .onPreviewKeyEvent {
        guard.used(SystemClock.elapsedRealtime())
        false
    }
