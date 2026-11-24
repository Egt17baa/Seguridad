# Repaso del director del arreglo de F-1 (`d581d2e`, 24-11-2026)

Solo lectura, esfuerzo extra. **Veredicto: con arreglos, solo de documentos (M-1 a M-3); el código queda aprobado tal cual.**

- Corte de los toques en `SecureWindow.kt:28` y `:40-44` (API ≥ 34), después de instalar el decor y antes de `setContent`; nada lo pisa (`applyWindowLook`, `enableEdgeToEdge`), sin primer fotograma sin corte. Conviene que `cutWallpaperTouches()` siga siendo la última línea de `secureWindow()`.
- Puerta en `Glass.kt:160-161`; tema, ventana y vista previa pasan el mismo SDK. `shownStyle`/`offeredStyles` sin migrar nada.
- Los tests fallarían sin el arreglo (`transparentNeedsAndroid14`, `theWindowAndTheThemeAgree` en 26–33, F-1 de `PlatformSourcesTest`). 131 tests de las clases tocadas, 0 fallos.
- Textos ciertos para el código; reglas duras intactas.

Menores:
- **M-1:** README `:451` («los cinco estilos») y `:459` (ahorro de batería en «Transparente») deben decir «Android 14 o más».
- **M-2:** `STYLE_MORE` y `CUSTOM_MORE` nombran «Transparente» también en Android 8 a 13. No es falso; para el Abogado.
- **M-3:** referencias desfasadas en `f1-fix-report.md` (`WallpaperWindow.kt:33-35`) y `final-audit.md` (punto 1 del arreglo propuesto, sustituido por `secureWindow()`; líneas de `c44e2a4`).

Riesgos: nada probado en un móvil (casillas con `dumpsys`); R-21-5 aceptado.
