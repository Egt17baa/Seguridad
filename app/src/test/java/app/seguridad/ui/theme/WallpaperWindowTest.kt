package app.seguridad.ui.theme

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
}
