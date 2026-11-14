package app.seguridad.tools

import app.seguridad.tools.classic.CLASSIC_SUBTITLE
import app.seguridad.tools.classic.CLASSIC_TITLE
import app.seguridad.tools.hashlab.HASH_LAB_SUBTITLE
import app.seguridad.tools.hashlab.HASH_LAB_TITLE
import app.seguridad.tools.slip39.SHARE_SUBTITLE
import app.seguridad.tools.slip39.SHARE_TITLE

/** One card of Herramientas: the route it opens (a destination of the navigation), its icon (decorative) and texts. */
data class Tool(val route: String, val icon: String, val title: String, val subtitle: String)

/**
 * Every tool of Herramientas, in their order. 2.0, task 10: «Salud de la bóveda» is here too (it was only reached from the
 * open vault), as «Modo sencillo» shows it (design «Ayuda y personalización»); with the vault locked its screen says to
 * unlock it and shows nothing of it.
 */
val TOOLS = listOf(
    Tool("tools/text", "🔤", "Cifrar texto", "Para enviar por chat o correo, o guardar una frase semilla"),
    Tool("tools/file", "📁", "Cifrar archivo", "Cualquier archivo del móvil, de cualquier tamaño"),
    Tool("tools/generator", "🎲", "Generador", "Contraseñas y frases con su fortaleza"),
    Tool("vault/health", "🩺", HEALTH_TOOL_TITLE, HEALTH_TOOL_SUBTITLE),
    Tool("tools/breach", "🕵", "¿Filtrada?", "Have I Been Pwned, sin enviar tu contraseña"),
    Tool("tools/hash", "#", HASH_LAB_TITLE, HASH_LAB_SUBTITLE),
    Tool("tools/classic", "🏛", CLASSIC_TITLE, CLASSIC_SUBTITLE),
    Tool("tools/share-secret", "🧩", SHARE_TITLE, SHARE_SUBTITLE),
    Tool("tools/photo", "🧽", PHOTO_TOOL_TITLE, PHOTO_TOOL_SUBTITLE),
    Tool("tools/redact", "⬛", REDACT_TOOL_TITLE, REDACT_TOOL_SUBTITLE),
    Tool("tools/qr", "🔍", QR_TOOL_TITLE, QR_TOOL_SUBTITLE),
    Tool("tools/codes", "🔢", CODES_TOOL_TITLE, CODES_TOOL_SUBTITLE),
)

/** What «Modo sencillo» shows (design: the generator, encrypt text and file, and «Salud»). */
val SIMPLE_TOOL_ROUTES = listOf("tools/text", "tools/file", "tools/generator", "vault/health")

/**
 * The cards Herramientas shows: every tool, or with [simple] only [SIMPLE_TOOL_ROUTES], in the same order. It only
 * filters this list: every route stays a destination of the navigation, and no notice nor setting depends on it (O-7).
 */
fun visibleTools(simple: Boolean): List<Tool> = if (simple) TOOLS.filter { it.route in SIMPLE_TOOL_ROUTES } else TOOLS

const val HEALTH_TOOL_TITLE = "Salud de la bóveda"
const val HEALTH_TOOL_SUBTITLE = "Contraseñas repetidas, débiles o antiguas, y accesos sin 2FA"

/** The name of the switch in Ajustes. */
const val SIMPLE_MODE_TITLE = "Modo sencillo"

/** On view under the switch in Ajustes, whether on or off (ronda 1, m-1: «Encendido»; O-7: it says what it does not hide). */
const val SIMPLE_MODE_SHORT = "Encendido, Herramientas enseña solo cifrar texto, cifrar archivo, el generador y la salud de la bóveda. No oculta avisos ni ajustes."

/** On view at the end of Herramientas while it is on: there are more, and where to show them. */
const val SIMPLE_MODE_MORE_TOOLS = "Hay más herramientas. Para verlas, apaga «$SIMPLE_MODE_TITLE» en Ajustes."
