package app.seguridad.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import app.seguridad.ui.theme.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import app.seguridad.settings.KdfProfile
import app.seguridad.settings.Settings
import app.seguridad.tools.IMAGE_TOOLS_LIMIT
import app.seguridad.tools.clipboardAboutText
import app.seguridad.tools.SIMPLE_MODE_MORE_TOOLS
import app.seguridad.tools.TOOLS_ABOUT_SHORT
import app.seguridad.tools.visibleTools
import app.seguridad.settings.simpleOn
import app.seguridad.ui.theme.SectionCard

/**
 * The Herramientas tab: the tools, two per row; with «Modo sencillo» only those of [visibleTools], and a line that says
 * there are more and where to show them. The card about how the data is protected is always there (O-7: a notice).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(settings: Settings, kdf: KdfProfile, onTool: (String) -> Unit) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { TopAppBar(title = { Text("Herramientas") }) },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val simple = settings.simpleOn
            items(visibleTools(simple), key = { it.route }) { t ->
                SectionCard(Modifier.fillMaxWidth().heightIn(min = 132.dp), onClick = { onTool(t.route) }) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        // Decorative (M-11): TalkBack reads the title, not «esponja» or «cuadrado negro grande».
                        Text(t.icon, Modifier.clearAndSetSemantics { }, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                        Text(t.title, style = MaterialTheme.typography.titleSmall)
                        Text(t.subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (simple) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "simple") {
                    Text(
                        SIMPLE_MODE_MORE_TOOLS,
                        Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }, key = "about") {
                SectionCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Cómo protegemos tus datos", style = MaterialTheme.typography.titleSmall)
                        Text(TOOLS_ABOUT_SHORT, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        MoreInfo {
                        listOf(
                            "El cifrado y el análisis de las imágenes se hacen en tu móvil. Solo «¿Filtrada?» y «Comprobar filtraciones» usan internet, cuando las pulsas, y solo envían 5 caracteres de la huella de cada contraseña.",
                            "Cifrado autenticado (AES-256-GCM, ChaCha20-Poly1305 y otros): si lo cifrado se cambia, se detecta al descifrarlo.",
                            "Tu contraseña se convierte en clave con Argon2id (${kdf.label}), que hace muy lento cada intento de adivinarla por fuerza bruta. Una contraseña corta sigue siendo débil.",
                            IMAGE_TOOLS_LIMIT,
                            clipboardAboutText(settings.clipboard.seconds),
                            "Si olvidas la contraseña de algo cifrado, no hay forma de recuperarlo.",
                            "Estas herramientas no han pasado una auditoría independiente. Para fondos importantes usa una cartera física de criptomonedas.",
                        ).forEach { Text("• $it", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }
        }
    }
}
