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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import app.seguridad.settings.Settings
import app.seguridad.tools.CODES_TOOL_SUBTITLE
import app.seguridad.tools.CODES_TOOL_TITLE
import app.seguridad.tools.IMAGE_TOOLS_LIMIT
import app.seguridad.tools.clipboardAboutText
import app.seguridad.tools.PHOTO_TOOL_SUBTITLE
import app.seguridad.tools.PHOTO_TOOL_TITLE
import app.seguridad.tools.QR_TOOL_SUBTITLE
import app.seguridad.tools.QR_TOOL_TITLE
import app.seguridad.tools.REDACT_TOOL_SUBTITLE
import app.seguridad.tools.REDACT_TOOL_TITLE
import app.seguridad.ui.theme.SectionCard

data class Tool(val route: String, val icon: String, val title: String, val subtitle: String)

val TOOLS = listOf(
    Tool("tools/text", "🔤", "Cifrar texto", "Para enviar por chat o correo, o guardar una frase semilla"),
    Tool("tools/file", "📁", "Cifrar archivo", "Cualquier archivo del móvil, de cualquier tamaño"),
    Tool("tools/generator", "🎲", "Generador", "Contraseñas y frases con su fortaleza"),
    Tool("tools/breach", "🕵", "¿Filtrada?", "Have I Been Pwned, sin enviar tu contraseña"),
    Tool("tools/hash", "#", "Huella de archivo", "SHA-256 para comprobar una descarga"),
    Tool("tools/photo", "🧽", PHOTO_TOOL_TITLE, PHOTO_TOOL_SUBTITLE),
    Tool("tools/redact", "⬛", REDACT_TOOL_TITLE, REDACT_TOOL_SUBTITLE),
    Tool("tools/qr", "🔍", QR_TOOL_TITLE, QR_TOOL_SUBTITLE),
    Tool("tools/codes", "🔢", CODES_TOOL_TITLE, CODES_TOOL_SUBTITLE),
)

/** The Herramientas tab: every tool that is not the vault, two per row. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(settings: Settings, onTool: (String) -> Unit) {
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
            items(TOOLS, key = { it.route }) { t ->
                SectionCard(Modifier.fillMaxWidth().heightIn(min = 132.dp), onClick = { onTool(t.route) }) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        // Decorative (M-11): TalkBack reads the title, not «esponja» or «cuadrado negro grande».
                        Text(t.icon, Modifier.clearAndSetSemantics { }, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                        Text(t.title, style = MaterialTheme.typography.titleSmall)
                        Text(t.subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }, key = "about") {
                SectionCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Cómo protegemos tus datos", style = MaterialTheme.typography.titleSmall)
                        listOf(
                            "El cifrado y el análisis de las imágenes se hacen en tu móvil. Solo «¿Filtrada?» y «Comprobar filtraciones» usan internet, cuando las pulsas, y solo envían 5 caracteres de la huella de cada contraseña.",
                            "Cifrado autenticado (AES-256-GCM, ChaCha20-Poly1305 y otros): si lo cifrado se cambia, se detecta al descifrarlo.",
                            "Tu contraseña se convierte en clave con Argon2id (${settings.kdf.label}), que hace muy lento cada intento de adivinarla por fuerza bruta. Una contraseña corta sigue siendo débil.",
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
