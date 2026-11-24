# F-1 de la auditoría final de la 2.1: informe del arreglo (24-11-2026)

Rama `ccr-0aa27f95-u038cd`, sobre `88ddb4e`. Decisión del dueño (24-11-2026), opción (b): «Transparente» solo desde Android 14, y desde Android 14 la ventana corta los toques al fondo de pantalla con cualquier aspecto. Arreglo: puntos 1 a 7 de `sdd-2.1/advisor-f1.md`, con TDD (primero los tests, en rojo por compilación: `TRANSPARENT_MIN_SDK`, `shownStyle`, `offeredStyles` y `TRANSPARENT_FROM_14` no existían; después el código).

## Lo hecho

1. **El corte, en `secureWindow()`.** `app/src/main/java/app/seguridad/android/SecureWindow.kt:28` llama a `cutWallpaperTouches()`, definida en `:40-44`: en API ≥ 34 (`UPSIDE_DOWN_CAKE`), `window.attributes = window.attributes.apply { setWallpaperTouchEventsEnabled(false) }`. Va en `onCreate`, antes de `setContent` (como `FLAG_SECURE`, `MainActivity.kt:46` y `:50`), sin condición de aspecto. El KDoc (`:31-39`) dice que los diálogos, los menús, las hojas, el teclado y la huella son ventanas propias y no se duplican. `applyWindowLook` no lo toca, y nada lo vuelve a encender.
2. **La puerta, en `lookFor`.** `ui/theme/Glass.kt:133`, `const val TRANSPARENT_MIN_SDK = Build.VERSION_CODES.UPSIDE_DOWN_CAKE`; `:160`, `wallpaperBehind` exige también `sdk >= TRANSPARENT_MIN_SDK`. Por debajo, «Transparente» toma los colores de «Cristal» (`:161`, la regla de M-1 que ya existía), sin capa (`layerAlpha` 1) y con las manchas; la ventana no lleva `FLAG_SHOW_WALLPAPER` porque `windowLookFor` lee el mismo `wallpaperBehind`. La puerta está en `lookFor`, no en `applyWindowLook`, así que el tema, la ventana y la vista previa coinciden. Nota en la cabecera de `ui/theme/WallpaperWindow.kt:33-35`.
3. **Ajustes → Aspecto.** `ui/theme/Palettes.kt:162-166`: `shownStyle(style, sdk)` («Transparente» antes de 34 se nombra «Cristal») y `offeredStyles(sdk)` (`STYLE_ORDER` sin «Transparente» antes de 34). `ui/screens/AppearanceScreen.kt:112`, `TRANSPARENT_FROM_14 = "«Transparente», desde Android 14."`; `:154`, `appearanceSummary` con `shownStyle`; `:200-205`, los chips son `offeredStyles(...)`, el marcado es `shownStyle`, la línea y «Más información» usan `shownStyle`, y en sdk < 34 sale la línea nueva. Un «Transparente» guardado no se borra ni se migra (como `shownPalette`): solo lo cambia el usuario al elegir otro estilo en esa pantalla.
4. **Tests** (abajo).
5. **README, «Pruebas de la 2.1, Aspecto»:** la entrada pide un móvil con Android 14 o más y otro con 8 a 13; «Transparente» nítido y con «Colores del fondo de pantalla», en Android 14 o más; la de Android 8 a 11 ya no habla de «Transparente»; dos casillas nuevas (`README.md:466-467`): en 14+, `dumpsys window windows` con `SHOW_WALLPAPER` y `SECURE` y `dumpsys input` sin `DUPLICATE_TOUCH_TO_WALLPAPER` en la ventana de Clavero; en 8 a 13, sin «Transparente», con la línea nueva y sin `SHOW_WALLPAPER` con ningún estilo. La salida se lee en local y no se sube.
6. **Textos:** la política (`docs/privacidad.html:37`), el README (`:72`, `:83`), la ficha (`docs/ficha-google-play.md:50` y `:111`), las notas de GitHub y las «Novedades» (`docs/superpowers/handoff/2026-11-24-clavero-2.1-notas.md:24` y `:33`, con su recuento: 383 de 500, y una línea más para el Abogado). La descripción completa de la ficha mide 3 891 caracteres, 3 933 con los saltos contados dos veces (tope 4 000; `PublishedTextsTest.theFullPlayDescriptionFits` en verde); la versión larga pasa de 7 230 a 7 248 (`:69`, recontado con el mismo método). La fecha de la política **no** cambia. `STYLE_TRANSPARENT_MORE` no cambia (solo se ve desde Android 14).
7. **Auditoría** (`sdd-2.1/final-audit.md`): F-1 «Decidido (b)», con la severidad justificada por ser un canal nuevo hacia otra app (CVSS ≈2,5), y su decisión y arreglo; que los diálogos, los menús, las hojas, el teclado y la huella no se duplican; F-2 resuelto con «desde Android 14»; MASVS PLATFORM y PRIVACY, «Cumple»; R-21-1 y R-21-2, solo en Android 14+; R-21-3 retirado; R-21-5 nuevo (Info): el fondo animado sabe cuándo se ve Clavero (`onVisibilityChanged`), como con el lanzador.

## Tests

Nuevos (6):
- `WallpaperWindowTest.transparentNeedsAndroid14`: de 26 a 33, «Transparente» es «Cristal» (sin fondo, `layerAlpha` 1, `drawsBlobs`, los mismos colores y el mismo difuminado); 34 y 35, con fondo y capa.
- `WallpaperWindowTest.theStyleShownAndOfferedFollowAndroid14`: `shownStyle` y `offeredStyles` en 26, 33, 34 y 35; el estilo marcado siempre está entre los ofrecidos.
- `AppearanceTextsTest.theLineOfTransparentBeforeAndroid14`: el texto exacto, sin palabras prohibidas ni nombres de otros aspectos.
- `AppearanceTextsTest.theSummaryNamesTheStyleAsItIsDrawn`: con «Transparente» guardado, el resumen empieza por «Cristal · » en 26 y 33 y por «Transparente · » en 34.
- `SettingsTest.aSavedTransparentIsReadAsItIsAndNeverMigrated`: se lee igual y `copy(... style =` solo aparece en `ui/screens/AppearanceScreen.kt`.
- `PlatformSourcesTest.F-1 the window never passes its taps to the wallpaper on Android 14+`: `secureWindow()` llama a `cutWallpaperTouches()`, que hace la llamada con `false` bajo `UPSIDE_DOWN_CAKE`; `setWallpaperTouchEventsEnabled(true)` no aparece en `main`; ningún otro archivo lo nombra; `applyWindowLook` no lo toca; `secureWindow()` va antes de `setContent`.

Cambiados: `WallpaperWindowTest.theWindowAndTheThemeAgree` (SDK 26, 30, 31, 33, 34 y 35; el fondo exige 34+), `theLayerIsAlwaysTheOneForASharpWallpaper` (34 y 35), `GlassContrastTest.eachStyleDrawsWhatItSays` (el fondo, solo en 34+), `GlassContrastTest.theTransparentLayerReadsAtTheOpacityItIsDrawnWith` (34 y 35), `AspectoCapturesTest` (SDK 34), `AppearanceTextsTest.theScreenUsesTheOrderTheLineAndTheFoldedTexts` (`offeredStyles`, `shownStyle` y la línea nueva).

Totales: 2 679 tests de la app y 2 583 de `jvm-check` (ver «Verificación»).

## Verificación

Las dos órdenes enteras, en verde (24-11-2026):
- `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets --no-daemon --max-workers=1`: BUILD SUCCESSFUL; 2 679 tests en 199 clases, 0 fallos, 0 errores, 0 saltados.
- `./gradlew -p jvm-check test --no-daemon --max-workers=1`: BUILD SUCCESSFUL; 2 583 tests en 178 clases, 0 fallos.
- Antes del código, los tests nuevos no compilaban (rojo): `Unresolved reference 'TRANSPARENT_MIN_SDK'`, `'shownStyle'`, `'offeredStyles'`, `'TRANSPARENT_FROM_14'`.

## Revisión experto-seguridad-cripto

- Modelo de amenaza: otra app instalada que pone un fondo animado (`WallpaperService` con `setTouchEventsEnabled(true)`). Antes del arreglo, en «Transparente» recibía la posición y la hora de cada toque sobre la ventana principal de Clavero.
- Android 14+: `setWallpaperTouchEventsEnabled(false)` en `SecureWindow.kt:40-44`, siempre, antes de cualquier contenido; `copyFrom` conserva el valor cuando Android rehace los atributos, y nada en `main` lo pone a `true` (`PlatformSourcesTest`). Android 8 a 13: nunca `FLAG_SHOW_WALLPAPER` (`Glass.kt:160`, `WallpaperWindowTest.transparentNeedsAndroid14`), así que la ventana no es el objetivo del fondo y no hay duplicado. No se usa el flag privado `@hide` ni `WallpaperManager` (O-A3; la prueba «sin `WallpaperManager`», `WALLPAPER_SERVICE` ni `"wallpaper"` sigue en verde).
- `FLAG_SECURE` sigue en `secureWindow()` antes de `setContent` y `applyWindowLook` no lo toca. Sin logs. El manifiesto no cambia (B-2): todo es en tiempo de ejecución. Sin ventanas translúcidas (O-A4).
- Lo que queda: R-21-5 (Info), el fondo animado sabe cuándo está a la vista; R-21-1 y R-21-2, solo en 14+. Un fabricante que cambie `InputMonitor` lo cubren las casillas de `dumpsys` del README. Nada probado en un móvil todavía.
- Ninguna primitiva, clave ni formato cambia. Un ajuste guardado no se reescribe: no hay migración que pueda fallar a medias.

## Revisión Ingeniero Móvil Senior

- `setWallpaperTouchEventsEnabled` es API 34; la llamada está bajo `Build.VERSION.SDK_INT >= UPSIDE_DOWN_CAKE` (`SecureWindow.kt:41`), así que `lintDebug` no da `NewApi`. `window.attributes = ...apply { }` vuelve a mandar los atributos al gestor de ventanas, que es como se aplica un cambio en `LayoutParams`.
- `TRANSPARENT_MIN_SDK` es `const` (la constante de `VERSION_CODES` se inlinea al compilar), y los tests en la JVM la usan con el `sdk` que pasan, sin depender del `SDK_INT` del entorno.
- Una sola fuente de verdad: `lookFor` decide el fondo para el tema, la ventana (`windowLookFor`) y la vista previa de Ajustes; `shownStyle` y `offeredStyles` solo deciden qué se nombra y qué se ofrece. El estado guardado no cambia (como `shownPalette`), así que un cambio de versión de Android, una copia restaurada o una vuelta atrás no pierde la elección.
- Compose: `val shownStyle = shownStyle(...)` es barato y se recalcula con `settings`; la región viva `Polite` lee la línea del estilo que se ve. La línea de Android 14 es un `Hint` fijo, como la de Android 12 de la paleta.
- Ciclo de vida: `secureWindow()` corre una vez en `onCreate`; con `configChanges` la actividad no se rehace al girar o cambiar de tamaño, y si Android la rehace vuelve a pasar por `onCreate`.

## Textos para el Abogado Digital (revisión final)

- **Nuevo en la interfaz:** `TRANSPARENT_FROM_14` = ««Transparente», desde Android 14.», en Ajustes → Aspecto antes de Android 14 (`ui/screens/AppearanceScreen.kt:112`).
- **Política** (`docs/privacidad.html:37`): «Con el estilo «Transparente» (desde Android 14), Android dibuja el fondo de pantalla detrás de la app; la app tampoco lee su imagen y le pide a Android que no pase tus toques a la app del fondo de pantalla;». La frase opcional del director («y le pide a Android que no pase tus toques a la app del fondo de pantalla») se ha añadido porque es cierta para el código: desde Android 14 la app lo pide siempre (`SecureWindow.kt:40-44`), y «Transparente» solo existe desde Android 14. Dice «le pide»: no promete lo que haga un fabricante. La fecha de la política no cambia aquí; va al final, con el Abogado.
- **README** `:72` y la ficha `:111` (versión larga): ««Transparente» (desde Android 14; deja ver, atenuado, …)»; README `:83`: «(«Degradado», «Vidrio» y, desde Android 14, «Transparente»)»; ficha `:50` (la que se pega en Play): «Cinco estilos («Sólido», «Degradado», «Cristal», «Vidrio» y, desde Android 14, «Transparente») y varias paletas.»; casillas del README `:453`, `:460`, `:463`, `:466-467` y la entrada de la sección.
- **Notas de GitHub y «Novedades»** (`2026-11-24-clavero-2.1-notas.md:24` y `:33`): ««Transparente» (desde Android 14; deja ver, atenuado, …)»; «Novedades», 383 de 500. Sin nombres de otras apps.
- `STYLE_TRANSPARENT_MORE` no cambia (el director: no necesita frase de los toques).
