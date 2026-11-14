package app.seguridad.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.seguridad.android.FilterObscuredTouches
import app.seguridad.settings.GUIDE_DONE
import app.seguridad.settings.GUIDE_NEXT
import app.seguridad.settings.GUIDE_SKIP
import app.seguridad.settings.GuideKind
import app.seguridad.settings.guidePages
import app.seguridad.settings.guideStep
import app.seguridad.ui.theme.AlertDialog

/**
 * 2.0, task 10 (phase B): one of the guides ([guidePages]), a screen at a time, with «Saltar» (also the back gesture) on
 * every one and «Entendido» on the last; either calls [onDone] (saved as seen by [app.seguridad.settings.GuideHolder]).
 * Only fixed texts: nothing of the vault. Which page is shown is a number, kept across a re-creation of the activity.
 * The text scrolls, so at 200 % of the system's font scale nothing is cut.
 */
@Composable
fun GuideDialog(kind: GuideKind, onDone: () -> Unit) {
    val pages = guidePages(kind)
    var index by rememberSaveable(kind) { mutableIntStateOf(0) }
    val page = pages[index.coerceIn(0, pages.lastIndex)]
    val last = index >= pages.lastIndex
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(page.title) },
        text = {
            FilterObscuredTouches()
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(page.text, Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                Text(guideStep(index, pages.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(onClick = { if (last) onDone() else index++ }) { Text(if (last) GUIDE_DONE else GUIDE_NEXT) }
        },
        dismissButton = if (last) null else ({ TextButton(onClick = onDone) { Text(GUIDE_SKIP) } }),
    )
}
