package app.seguridad.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import app.seguridad.vault.loadMasterWords
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The known words for the rule of a NEW master (CRY-A01, owner's amendment): the lists of `assets/` read off the main
 * thread, once for as long as the screen that asks for them is open. They are not a secret and not worth saving, so the
 * state is a plain `remember` (never `rememberSaveable`): after a turn of the screen they are read again.
 *
 * It is null while they load, and the button of creating or changing the master waits for it to stop being null. If
 * they cannot be read it is an empty set (see `loadMasterWords`), not null, so the button never waits for ever and the
 * rule stays at the length and the estimate.
 */
@Composable
internal fun rememberMasterWords(): Set<String>? {
    val assets = LocalContext.current.assets
    var words by remember { mutableStateOf<Set<String>?>(null) }
    LaunchedEffect(Unit) { words = withContext(Dispatchers.IO) { loadMasterWords { name -> assets.open(name) } } }
    return words
}
