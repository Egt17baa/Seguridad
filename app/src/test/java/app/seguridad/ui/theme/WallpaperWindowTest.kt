package app.seguridad.ui.theme

import app.seguridad.settings.PaletteId
import app.seguridad.settings.ThemeStyle
import org.junit.Assert.assertEquals
import org.junit.Test

/** 2.1, Aspecto (task 3): what the window does in «Transparente», worked out on the JVM. */
class WallpaperWindowTest {
    @Test fun theWallpaperIsBlurredFromAndroid12WhenThePhoneAllowsIt() {
        assertEquals(WindowLook(showWallpaper = true, blurRadiusDp = 24), windowLookFor(showWallpaper = true, sdk = 31, crossWindowBlur = true))
        assertEquals(WindowLook(showWallpaper = true, blurRadiusDp = 24), windowLookFor(showWallpaper = true, sdk = 35, crossWindowBlur = true))
    }

    @Test fun withoutTheBlurOfThePhoneTheWallpaperIsSharp() {
        assertEquals(WindowLook(showWallpaper = true, blurRadiusDp = 0), windowLookFor(showWallpaper = true, sdk = 31, crossWindowBlur = false))
    }

    @Test fun beforeAndroid12ThereIsNoBlur() {
        assertEquals(WindowLook(showWallpaper = true, blurRadiusDp = 0), windowLookFor(showWallpaper = true, sdk = 30, crossWindowBlur = true))
        assertEquals(WindowLook(showWallpaper = true, blurRadiusDp = 0), windowLookFor(showWallpaper = true, sdk = 26, crossWindowBlur = false))
    }

    @Test fun withoutTheWallpaperNothingIsBlurred() {
        assertEquals(WindowLook(showWallpaper = false, blurRadiusDp = 0), windowLookFor(showWallpaper = false, sdk = 34, crossWindowBlur = true))
        assertEquals(WindowLook(showWallpaper = false, blurRadiusDp = 0), windowLookFor(showWallpaper = false, sdk = 26, crossWindowBlur = false))
    }

    /**
     * Review of task 3 (minor 2): the window and the theme say the same. The look's [Look.wallpaperBlur] (a lighter layer)
     * is true exactly when the window blurs the wallpaper, and the window shows it exactly when the look has it behind.
     */
    @Test fun theWindowAndTheThemeAgree() {
        for (style in ThemeStyle.entries) for ((reduce, high) in listOf(false to false, true to false, false to true, true to true)) {
            for (sdk in listOf(26, 30, 31, 35)) for (b in listOf(false, true)) {
                val look = lookFor(style, PaletteId.NIGHT, null, reduceTransparency = reduce, highContrast = high, dark = true, sdk = sdk, wallpaperBlur = b)
                val window = windowLookFor(showsWallpaper(look), sdk, b)
                val case = "$style reduce=$reduce high=$high sdk=$sdk blur=$b"
                assertEquals(case, look.wallpaperBehind, showsWallpaper(look))
                assertEquals(case, look.wallpaperBehind, window.showWallpaper)
                assertEquals(case, look.wallpaperBlur, window.blurRadiusDp > 0)
            }
        }
    }
}
