package app.seguridad.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import app.seguridad.SeguridadApp
import app.seguridad.vault.WordLists

/**
 * The known words for the rule of a NEW master (CRY-A01, owner's amendment): the lists of `assets/`, read off the main
 * thread once per process ([wordLists], F6), not again at each opening or turn of the screen. They are not a secret and
 * not worth saving, so the state is a plain `remember` (never `rememberSaveable`).
 *
 * It is null while they load, and the button of creating or changing the master waits for it to stop being null (one
 * frame once they are loaded). If they cannot be read it is an empty set (see `loadMasterWords`), not null, so the
 * button never waits for ever and the rule stays at the length and the estimate; the next screen tries to read them
 * again.
 */
@Composable
internal fun rememberMasterWords(): Set<String>? {
    val lists = wordLists()
    var words by remember { mutableStateOf<Set<String>?>(null) }
    LaunchedEffect(Unit) { words = lists.master() }
    return words
}

/** The process' word lists (AppContainer): the master rule's and the Generator's, read once, off the main thread. */
@Composable
internal fun wordLists(): WordLists = (LocalContext.current.applicationContext as SeguridadApp).container.wordLists
