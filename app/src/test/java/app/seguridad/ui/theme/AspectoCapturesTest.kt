package app.seguridad.ui.theme

import androidx.compose.ui.graphics.Color
import app.seguridad.settings.PaletteId
import app.seguridad.settings.STYLE_ORDER
import app.seguridad.settings.ThemeStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 2.1, Aspecto (task 5): the exact colours and opacities of each style in «Noche», light and dark, on Android 12+ with
 * the blur, as the app works them out. They are what the owner's approximate pictures of each style are drawn from
 * (there is no emulator here, and FLAG_SECURE blocks screenshots on the phone). The values go to the folder named by the
 * environment variable ASPECTO_CAPTURAS, only when it is set: a normal run writes nothing.
 */
class AspectoCapturesTest {
    private fun hex(v: Long) = "\"#%08X\"".format(v and 0xFFFFFFFFL)
    private fun hex(c: Color) = hex(argb(c))
    private fun f(v: Float) = "%.4f".format(java.util.Locale.ROOT, v)

    private fun styleJson(style: ThemeStyle, dark: Boolean): String {
        val look = lookFor(style, PaletteId.NIGHT, null, reduceTransparency = false, highContrast = false, dark = dark, sdk = 31, wallpaperBlur = true)
        val c = look.colors
        val s = c.scheme
        val blobs = c.background.blobs.joinToString(",") {
            """{"color":${hex(it.color)},"alpha":${f(it.alpha)},"x":${f(it.x)},"y":${f(it.y)},"radius":${f(it.radius)}}"""
        }
        val panels = PanelLevel.entries.joinToString(",") {
            """"${it.name}":{"color":${hex(c.panelColor(it))},"alpha":${f(if (look.translucent) look.alpha(it, blurNow = true) else 1f)}}"""
        }
        return """{"style":"${style.name}","label":"${style.label()}","dark":$dark,""" +
            """"drawsBackground":${look.drawsBackground},"blobs":${style != ThemeStyle.GRADIENT && look.drawsBackground && !look.wallpaperBehind},""" +
            """"translucent":${look.translucent},"strong":${look.strong},"litEdge":${litEdge(look)},""" +
            """"wallpaperBehind":${look.wallpaperBehind},"layerAlpha":${f(look.layerAlpha)},""" +
            """"background":{"top":${hex(c.background.top)},"bottom":${hex(c.background.bottom)},"blobs":[$blobs]},""" +
            """"panels":{$panels},"highlightAlpha":${f(c.highlightAlpha)},"border":${hex(c.border)},"borderWidth":${f(c.borderWidth)},""" +
            """"onSurface":${hex(s.onSurface)},"onSurfaceVariant":${hex(s.onSurfaceVariant)},"onBackground":${hex(s.onBackground)},""" +
            """"primary":${hex(s.primary)},"onPrimary":${hex(s.onPrimary)}}"""
    }

    @Test
    fun theValuesOfEveryStyleAreWrittenOnlyWhenAsked() {
        val all = STYLE_ORDER.flatMap { st -> listOf(false, true).map { styleJson(st, it) } }
        assertEquals(10, all.size)
        // What the pictures rely on: only «Transparente» lets the wallpaper show, under a layer that is not opaque.
        for (st in STYLE_ORDER) {
            val look = lookFor(st, PaletteId.NIGHT, null, reduceTransparency = false, highContrast = false, dark = true, sdk = 31, wallpaperBlur = true)
            assertEquals(st.name, st == ThemeStyle.TRANSPARENT, look.wallpaperBehind)
            assertTrue(st.name, look.layerAlpha in 0f..1f && (st == ThemeStyle.TRANSPARENT) == (look.layerAlpha < 1f))
        }
        val dir = System.getenv("ASPECTO_CAPTURAS") ?: return
        File(dir).apply { mkdirs() }.resolve("aspecto-colores.json").writeText(all.joinToString(",\n", "[\n", "\n]\n"))
    }
}
