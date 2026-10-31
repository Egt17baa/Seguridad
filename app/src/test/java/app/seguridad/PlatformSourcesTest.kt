package app.seguridad

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Android-only code that the JVM cannot run, checked in its source (audit 1.3.0, batch F1). The behaviour itself is in
 * the owner's phone test (README).
 */
class PlatformSourcesTest {
    private fun main(path: String): String {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val f = File(dir, "app/src/main/java/app/seguridad/$path")
            if (f.isFile) return f.readText()
            dir = dir.parentFile
        }
        error("$path not found")
    }

    /** Code only: comments say what it does, but they must not make the check pass. */
    private fun code(path: String) = main(path).lines().filterNot { it.trimStart().let { l -> l.startsWith("//") || l.startsWith("*") || l.startsWith("/*") } }.joinToString("\n")

    @Test fun `COV2-4 the window and every dialog are marked accessibility data sensitive on Android 14+`() {
        val src = code("android/SecureWindow.kt")
        val marks = Regex("""setAccessibilityDataSensitive\(View\.ACCESSIBILITY_DATA_SENSITIVE_YES\)""").findAll(src).count()
        assertTrue("secureWindow() and FilterObscuredTouches() must both set it", marks >= 2)
        assertTrue("guarded by API 34", "Build.VERSION_CODES.UPSIDE_DOWN_CAKE" in src)
    }

    @Test fun `COV2-3 MainActivity tells the vault when another app has the focus in multi-window`() {
        val src = code("MainActivity.kt")
        assertTrue("onTopResumedActivityChanged" in src)
        assertTrue("isInMultiWindowMode" in src)
        assertTrue("container.leftForAnotherApp()" in src)
        assertTrue("container.backFromAnotherApp()" in src)
    }

    @Test fun `COV2-3 M3 PIN y codigos hears the multi-window away signal for its whole entry`() {
        val src = code("ui/screens/CodesScreen.kt")
        assertTrue("session.otherAppFocused(" in src)
        assertTrue("session.otherAppLeft(" in src)
        assertTrue("awayInOtherApp" in code("ui/AppNavHost.kt"))
    }

    @Test fun `ANDROID-1 a deleted share copy has its URI permission revoked`() {
        assertTrue("revokeUriPermission" in code("android/ImageShare.kt"))
        assertTrue("revokeSharedCopy" in code("SeguridadApp.kt"))
    }
}
