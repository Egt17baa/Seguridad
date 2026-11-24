# F-1 de la auditoría final de la 2.1: opinión del director (24-11-2026)

Solo lectura, esfuerzo extra, sobre `86a8bb3`.

**Veredicto:** F-1 es cierto (AOSP de 8.0 a main): con `FLAG_SHOW_WALLPAPER`, los toques sobre la ventana principal se duplican a la ventana del fondo de pantalla, y un fondo animado de otra app con `setTouchEventsEnabled(true)` recibe su posición y su hora. Solo eso: ni contenido (`FLAG_SECURE`) ni teclado; los diálogos, menús, hojas, el teclado y la huella son otras ventanas y no se duplican. CVSS ≈2,5; «Medio» se sostiene por ser un canal nuevo hacia otra app (no porque el arreglo sea una línea). En el proceso cuenta como Important.

- Android 14+ (API 34, `api-versions.xml`): `WindowManager.LayoutParams.setWallpaperTouchEventsEnabled(false)` lo corta (`InputMonitor`: exige `areWallpaperTouchEventsEnabled()`); `copyFrom` lo conserva.
- Android 8 a 13: sin vía pública (solo un flag privado `@hide`, descartado).

## Decisión del dueño (24-11-2026)

**Opción (b): «Transparente» solo desde Android 14.** Además, el corte de los toques desde Android 14 en cualquier caso.

## Arreglo que pide el director

1. `android/SecureWindow.kt`, en `secureWindow()` (antes de `setContent`, sin condición de aspecto): en API ≥ 34, `window.attributes = window.attributes.apply { setWallpaperTouchEventsEnabled(false) }`.
2. `Glass.kt` `lookFor`: `wallpaperBehind` exige también `sdk >= TRANSPARENT_MIN_SDK` (`UPSIDE_DOWN_CAKE`); por debajo, colores de «Cristal» (como en multiventana). La puerta va en `lookFor`, no en `applyWindowLook`, para que tema, ventana y vista previa coincidan.
3. Un «Transparente» guardado en Android ≤ 13 se ve como «Cristal» y no se borra ni se migra (precedente de `shownPalette`): `shownStyle(style, sdk)` y `offeredStyles(sdk)` en `Palettes.kt`; en `AppearanceScreen.kt`, `Choices(offeredStyles(sdk), shownStyle(...))`, `styleShort`/`styleMore`/`appearanceSummary` con `shownStyle`, y en sdk < 34 una línea `TRANSPARENT_FROM_14 = "«Transparente», desde Android 14."`.
4. Tests: `WallpaperWindowTest.theWindowAndTheThemeAgree` con SDK 26, 30, 31, 33, 34, 35; nuevo `transparentNeedsAndroid14` (26–33 igual que «Cristal»: sin fondo, `layerAlpha` 1, `drawsBlobs`; 34–35 con fondo); pasar a 34/35 `theLayerIsAlwaysTheOneForASharpWallpaper`, `GlassContrastTest.theTransparentLayerReadsAtTheOpacityItIsDrawnWith` y `AspectoCapturesTest`; `shownStyle`/`offeredStyles` en 33 y 34; `appearanceSummary` con TRANSPARENT en 33 empieza por «Cristal»; `AppearanceTextsTest` fija el texto nuevo; `SettingsTest`: un TRANSPARENT guardado se lee igual y nada lo migra (`copy(style =` solo en `AppearanceScreen.kt`); `PlatformSourcesTest`: `secureWindow` llama a `setWallpaperTouchEventsEnabled(false)` bajo `UPSIDE_DOWN_CAKE`, `setWallpaperTouchEventsEnabled(true)` no aparece en `main`, `applyWindowLook` no lo toca.
5. README, «Pruebas de la 2.1, Aspecto»: en 14+, `dumpsys window` muestra `SHOW_WALLPAPER` y `SECURE`, y `dumpsys input` no muestra `DUPLICATE_TOUCH_TO_WALLPAPER` en la ventana de Clavero; en ≤ 13, no sale «Transparente», sale la línea y ningún estilo tiene `SHOW_WALLPAPER`. La salida se lee en local y no se sube.
6. Textos que cambian (al Abogado final): `TRANSPARENT_FROM_14`; `docs/privacidad.html:37` («(desde Android 14)»; opcional: «y le pide a Android que no pase tus toques a la app del fondo de pantalla»); README `:72`, `:83`, `:453`, `:460`, `:463`; ficha `:50` y `:111` (dentro de 4 000); notas `2026-11-24-clavero-2.1-notas.md:24` y `:33` y el borrador de «Novedades». `STYLE_TRANSPARENT_MORE` no necesita frase de los toques.
7. Auditoría: F-1 «decidido (b)» con el motivo correcto de la severidad; F-2 «desde Android 14»; se retira R-21-3; R-21-5 nuevo (Info): el fondo animado sabe cuándo se ve (`onVisibilityChanged`), como con el lanzador; R-21-1 y R-21-2 solo en 14+; precisar que diálogos y teclado no se duplican.

Riesgos: nada probado en un móvil; AOSP 9 a 12 no leído (mismo mecanismo); un fabricante puede cambiar `InputMonitor` (lo cubren las casillas con `dumpsys`).
