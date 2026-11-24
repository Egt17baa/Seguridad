# Aspecto de la 2.1: revisión de toda la rama por el director (24-11-2026)

Rama `ccr-0aa27f95-u038cd`, commits `42458bd..94b5026`. Esfuerzo extra, solo lectura.

**Veredicto:** lista con arreglos: 2 importantes y 7 menores, ninguno crítico. «Cristal» y «Sólido» de la 2.0 no cambian (`kindOf`, `nominal` y `drawAppBackground` dan lo mismo que en `42458bd`); los textos del §4 están literales; O-A1, O-A2, O-A3 y B-2 se cumplen; `FLAG_SECURE` va antes de `setContent`.

## Importantes

- **I-1. «Transparente» en ventana flotante o de escritorio deja ver otras apps (O-A4).** `Glass.kt:150` (`wallpaperBehind` solo mira el estilo y `opaque`), `WallpaperWindow.kt:52-59`, `MainActivity.kt:63-67`. El fondo de pantalla está debajo de todas las tareas: en una ventana flotante, detrás está otra app, que se vería al 14-20 % a través de Clavero. Incumple O-A4 y hace falso «deja ver el fondo de pantalla» en un modo que la app documenta (`README.md:24`, `:333`, `:409`). Arreglo: `wallpaperBehind` también exige no estar en multiventana (`isInMultiWindowMode`, seguido con `addOnMultiWindowModeChangedListener`), en `lookFor` y `windowLookFor`, con test de que coinciden.
- **I-2. La capa medida no es la dibujada.** `Palettes.kt:360-366` mide la capa con `bgDarkest`/`bgLightest` de `bounds(bg)`, manchas incluidas; `Glass.kt:214` y `:228-231` la dibujan sin manchas (`drawsBlobs` es falso con `wallpaperBehind`). En «Noche» oscuro, `onSurfaceVariant` sube de `#B4B6C4` (L 0,471) en «Cristal» a `#E2E3E8` (L 0,769), casi igual que `onSurface`, y `primary` pasa de oro `#F3A824` a crema `#FBE0B0`. Con lo que se dibuja bastaría L ≥ 0,545. Arreglo: medir la capa con la caja canal a canal del degradado dibujado (`bounds(BackgroundSpec(top, bottom, emptyList()))`) en `Palettes.kt:366` y en `PalettesTest.transparentLayerReadsOverAnyWallpaper`, `GlassContrastTest.layerPoints` y `tagsReadOnACardOverTheTransparentLayer`; lo opaco con manchas sigue con `against`. Corregir los comentarios y rehacer las aproximaciones.

## Menores

- **M-1.** «Transparente» con «Reducir transparencias» usa los colores corregidos para una capa que no se dibuja (`Glass.kt:148`): usar los de «Cristal» cuando es opaco, con test.
- **M-2.** (a) `PalettesTest.kt:182` salta «Transparente» en `failures()`: medir sus colores con paneles opacos, o recorrer todos los estilos en `GlassContrastTest.reduceTransparencyMakesPanelsOpaque` (`:33`). (b) `everyPaletteMeetsItsTargetsItself` (`PalettesTest.kt:86`) solo cubre «Cristal»: añadir «Vidrio» y «Transparente» con `ALL_CUSTOM` y una submuestra fija de `WALLPAPER_SWEEP`.
- **M-3.** La prueba de O-A3 solo busca `WallpaperManager` (`PlatformSourcesTest.kt:1346`, `:1431`): añadir `WALLPAPER_SERVICE` y `"wallpaper"`.
- **M-4.** Comentarios desfasados: `Glass.kt:383-387` (detrás de un diálogo está el contenido de la pantalla, no solo el fondo de pantalla) y `Palettes.kt:84` (olvida «Degradado»).
- **M-5.** Los rulings de la rama solo están en el registro de trabajo, que git ignora: copiarlos a `sdd-2.1/progress.md`; `CLAUDE.md` y el parte al día al cerrar la ronda.
- **M-6.** Casillas que faltan en «Pruebas de la 2.1, Aspecto»: ventana flotante; cambiar claro/oscuro del sistema con «Transparente»; «Transparente» con «Colores del fondo de pantalla»; la vista previa en «Transparente» no deja ver nada de detrás.
- **M-7.** Las aproximaciones de «Transparente» solo enseñan el fondo difuminado (`scripts/aspecto-capturas/dibujar.py:68`): añadir variantes con el fondo nítido.

## Opinión sobre la capa de «Transparente» (decide el dueño)

Que el fondo «apenas se vea» viene sobre todo de I-2. Con I-2 arreglado, a 0,80 los textos de «Noche» oscuro vuelven casi a los tonos de «Cristal». Recomendación: mantener el mínimo en 0,80. En claro el límite real es la capa sobre negro (a 0,70 el texto secundario sería casi tan oscuro como el principal), y una imagen nítida detrás molesta más de lo que mide el contraste. Si se quiere que se vea más, lo menos dañino es 0,75 solo en oscuro.

## Riesgos o dudas

1. **El difuminado de «Transparente» casi seguro no se verá.** La documentación de `Window.setBackgroundBlurRadius` pide una ventana translúcida (`windowIsTranslucent`), y en AOSP `DecorView` pone el radio a 0 si la ventana no lo es; O-A4 la prohíbe. Si se confirma: el texto «Desde Android 12, si el móvil lo permite, se ve difuminada» es falso, y la capa quedaría a 0,80 sobre una imagen nítida en lugar del 0,86 previsto sin difuminado.
2. **Transiciones:** con la superficie translúcida, la ventana anterior o el lanzador pueden verse una fracción de segundo al abrir, con «Atrás» predictivo o al ir a recientes. Conviene decidir antes qué se acepta en la prueba del README.
3. La región viva va en una `Box`, no en el `Text` (`AppearanceScreen.kt:193`); lo cubre la prueba con TalkBack.
4. El director no probó nada en un móvil; lo de las ventanas flotantes y el difuminado sale del funcionamiento de AOSP.

## Textos para el Abogado Digital (revisión final)

Interfaz: los nombres «Degradado», «Vidrio» y «Transparente»; `STYLE_GRADIENT_SHORT`, `STYLE_GLASS_STRONG_SHORT`, `STYLE_TRANSPARENT_SHORT`, `STYLE_MORE`, `STYLE_TRANSPARENT_MORE` y `CUSTOM_MORE` (`AppearanceScreen.kt:91-110`). Publicados: `README.md:71-72` y «Pruebas de la 2.1, Aspecto»; ficha `:3`, `:7`, `:50` y `:110-112`; `docs/privacidad.html:37` (fecha al día al fusionar); el borrador de «Novedades» en `sdd-2.1/progress.md`.

Recomendaciones del director:
1. `STYLE_MORE`, «difuminan lo que tienen detrás», no es exacto: mejor el texto de la 2.0, «difuminan el fondo de la app que tienen detrás».
2. «Desde Android 12, si el móvil lo permite, se ve difuminada»: probablemente falso (riesgo 1).
3. Si se arregla I-1: añadir que en pantalla dividida o en una ventana flotante el fondo de pantalla no se ve.
4. La batería: si el difuminado funcionara, también gasta con un fondo quieto.
5. Ficha `:111`: «Tres estilos más en Ajustes → Aspecto» suena a notas de versión dentro de una descripción.
6. Ya apuntada: «bordes iluminados» frente a «Alto contraste».
