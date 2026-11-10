package app.seguridad.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.seguridad.tools.MORE_INFO
import app.seguridad.tools.MORE_INFO_CLOSED
import app.seguridad.tools.MORE_INFO_LESS
import app.seguridad.tools.MORE_INFO_OPEN
import app.seguridad.tools.TOOLS_AWAY_WIPE_NOTE
import app.seguridad.tools.TOOLS_AWAY_WIPE_SHORT

/**
 * 1.4.2, the owner's request: a screen shows what it is and what it does in a line or two, and the rest of its
 * explanation is here, folded until tapped. Only explanations go here, never a warning the user must see. Whether it is
 * open is not secret, so it may survive in the saved state; what it shows is fixed text.
 */
@Composable
internal fun MoreInfo(content: @Composable ColumnScope.() -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        TextButton(
            onClick = { open = !open },
            modifier = Modifier.semantics { stateDescription = if (open) MORE_INFO_OPEN else MORE_INFO_CLOSED },
        ) { Text(if (open) MORE_INFO_LESS else MORE_INFO) }
        AnimatedVisibility(open) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
        }
    }
}

/**
 * COV1-1: what a tool screen keeps, and for how long, said under it: the short line on view and every case folded, after
 * the screen's own folded explanations ([more]). One place for the tools that wipe what was typed (1.4.2).
 */
@Composable
internal fun AwayNote(more: @Composable ColumnScope.() -> Unit = {}) {
    Text(TOOLS_AWAY_WIPE_SHORT, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    MoreInfo {
        more()
        Text(TOOLS_AWAY_WIPE_NOTE, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
