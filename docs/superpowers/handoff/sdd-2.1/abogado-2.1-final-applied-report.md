# Clavero 2.1: cambios del Abogado final y menores del director, aplicados (24-11-2026)

Rama `ccr-0aa27f95-u038cd`, sobre `f52e783`. Fuentes: dictamen `sdd-2.1/abogado-2.1-final.md` (O-1, R-1 a R-5 y la fecha de la política) y repaso del director `sdd-2.1/advisor-f1-recheck.md` (M-1 y M-3; M-2 no cambia por el Ruling del controlador en `progress.md`). Todos los textos se copiaron exactos del dictamen. Ninguno choca con el código.

## Cambios

**O-1 (obligatorio), la ficha y el formato:**
- `docs/ficha-google-play.md:57` (se pega en Play): «• Al actualizar desde una versión 1.x, la bóveda pasa a un formato nuevo la primera vez que la abres con la contraseña maestra, y las versiones 1.x ya no abren la bóveda ni sus copias nuevas. Exporta antes una copia.»
- `docs/ficha-google-play.md:123` (versión larga; en el dictamen, `:124`, que tras R-2 sube una línea): «• Al actualizar desde una versión 1.x, la bóveda pasa a un formato nuevo la primera vez que la abres con la contraseña maestra: después, las versiones 1.x de Clavero no abren la bóveda ni sus copias nuevas. Exporta antes una copia.»
- Cierto para el código: el formato v4 llegó en la 2.0 y la 2.1 no cambia formatos (`README.md:83`); la 1.4.3 no abre la v4 (`BackupV3Test.aCopyOfAV4IsRefusedByThe143Reader`).

**R-1, `STYLE_MORE`:**
- `app/src/main/java/app/seguridad/ui/screens/AppearanceScreen.kt:100-102`: termina en «… «Reducir transparencias» y «Alto contraste» hacen opacos todos los paneles, y «Alto contraste» quita también los bordes iluminados de «Vidrio».»
- Cierto para el código: `ui/theme/Glass.kt:300`, `litEdge(look) = look.strong && !look.highContrast`; con «Reducir transparencias» sí se dibuja.
- `app/src/test/java/app/seguridad/ui/screens/AppearanceTextsTest.kt:33-37` (`theFoldedTextsAreTheLawyersOnes`), al día con el texto nuevo.

**R-2, la versión larga de la ficha:** `docs/ficha-google-play.md:110`, las dos viñetas en una: «• Cinco estilos: «Sólido», sin transparencias; «Degradado», con paneles opacos sobre un fondo en degradado; «Cristal», con paneles translúcidos (desde Android 12, las barras y los diálogos difuminan el fondo de la app que tienen detrás); «Vidrio», como «Cristal», más translúcido y con bordes iluminados; y «Transparente» (desde Android 14), que deja ver, atenuado, el fondo de pantalla del móvil: la app no lee su imagen ni pide permisos.»

**R-3, capturas:** `docs/ficha-google-play.md:7`, «Las capturas se hacen de nuevo con la 2.1, con el aspecto «Cristal» y solo datos inventados:».

**R-4:** `README.md:71` termina en «(Ajustes → Aspecto → «Sólido»). Icono nuevo, que la 2.1 sustituye (abajo).»

**R-5:** `README.md:9`, añadida tras la frase de los archivos del icono: «Las letras «C» y «L» del icono son contornos de Liberation Serif Bold (SIL Open Font License 1.1); la app no incluye la fuente.» Cierto: la fuente no está en `app/src/main` (solo contornos en los XML del icono). La línea opcional en «Licencias» no se añade. `app/src/test/java/app/seguridad/tools/FinalLegalReviewTest.kt:178-186` (`r8ToR10TheLicencesAndTheName`), que fija el párrafo «Nombre» del README, al día con la frase (la primera ejecución falló ahí, como debía).

**Recuentos de la ficha** (medidos con el método de `PublishedTextsTest.playFullDescription` y, para la versión larga, el mismo que dio 7 248 en `f52e783`):
- Descripción completa: 3 906 caracteres, 3 948 con cada salto contado dos veces (antes 3 891 y 3 933); tope 4 000.
- Versión larga: 7 203 sin Markdown (antes 7 248; R-2 quita «Tres estilos más en Ajustes → Aspecto» y la viñeta duplicada). `docs/ficha-google-play.md:69` al día.
- `CLAUDE.md:83`: «3 948 en la 2.1» (antes 3 933). Es el único cambio en `CLAUDE.md`.

**Fecha de la política:** `docs/privacidad.html:22`, «Última actualización: 24 de noviembre de 2026.». Ningún test fija el día: `FinalLegalReviewTest` solo lee la forma de la fecha. El contenido de la política no cambia en esta ronda.

**M-1 del director:**
- `README.md:451`: «- [ ] 2.1, los cinco estilos: en Android 14 o más, Ajustes → Aspecto, …».
- `README.md:459`: «… y, en «Transparente» (Android 14 o más), el fondo de pantalla sigue igual de nítido y atenuado; …».

**M-3 del director (referencias):**
- `docs/superpowers/handoff/sdd-2.1/f1-fix-report.md:8`: la nota de la cabecera es `ui/theme/WallpaperWindow.kt:33-35` (decía `:31-33`).
- `docs/superpowers/handoff/sdd-2.1/final-audit.md`:
  - `:3`, aviso de que las líneas de código están al día en `f52e783`;
  - `:45`, el punto 1 del arreglo propuesto queda marcado como sustituido por el corte en `secureWindow()` (`android/SecureWindow.kt:28` y `:40-44`);
  - líneas de `c44e2a4` puestas al día: `WallpaperWindow.kt:50-58` a `:54-62` (`:30`, `:39`, `:83`), `:53` a `:57` (`:87`), `:64-71` a `:68-75` (`:87`, `:118`), `:77-88` a `:81-92` (`:76`, `:88`, `:119`), `AppearanceScreen.kt:101-105` a `:103-107` (`:31`, `:63`), `:300-304` y `:331-334` a `:308-312` y `:339-342` (`:86`), `Glass.kt:151` a `:160` (`:76`) y `Glass.kt:228-230` a `:237-239` (`:89`, `:121`). `MainActivity.kt` y `Theme.kt` no cambiaron desde `c44e2a4`.

**M-2:** sin cambios (Ruling del controlador, `progress.md`).

## Verificación

- `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets --no-daemon --max-workers=1`: BUILD SUCCESSFUL, 2 679 tests, 0 fallos.
- `./gradlew -p jvm-check test --no-daemon --max-workers=1`: BUILD SUCCESSFUL, 2 583 tests, 0 fallos.
- Incluyen `PublishedTextsTest.theFullPlayDescriptionFits`, `AppearanceTextsTest`, `FinalLegalReviewTest`, `ImportTextsTest` y `PublishedFilesNameNoModelTest`.
- Búsqueda en el diff: ninguna «líquido», ningún nombre de modelo, ningún nombre de otra app en la ficha, ningún dato del dueño.

## Revisión experto-seguridad-cripto

- Solo textos, una constante de interfaz y su test: no cambia ningún formato, KDF, clave, permiso ni red. `FLAG_SECURE` (`android/SecureWindow.kt:24`) y el manifiesto, sin tocar; `git diff --stat` no toca `AndroidManifest.xml`, `crypto/` ni `vault/`.
- Veracidad: O-1 es cierto frente al formato v4 (la 2.1 no cambia formatos, `README.md:83`); R-1 frente a `Glass.kt:300`; R-5 frente al APK (la fuente no se incluye); M-1 frente a `TRANSPARENT_MIN_SDK` (`Glass.kt:133`, `:160`).
- La política solo cambia la fecha (`docs/privacidad.html:22`), que ahora es la del último cambio de contenido (`d581d2e`, 24-11-2026).

## Revisión Ingeniero Móvil Senior

- `STYLE_MORE` (`AppearanceScreen.kt:100-102`) sigue siendo un `const val` concatenado, sin recursos ni formato nuevo; se muestra bajo «Más información» (`ui/screens/MoreInfo.kt`), donde un texto más largo no cambia el diseño.
- La frase de R-1 sirve en cualquier versión de Android: el borde de «Vidrio» no depende del SDK.
- Compilación, lint y tests en verde (arriba); sin cambios de ciclo de vida, concurrencia ni rendimiento.

## Textos para el Abogado Digital (revisión final)

Todos son los suyos, copiados exactos (O-1, R-1 a R-5 y la fecha). Además, del director, sin texto nuevo para la interfaz: «en Android 14 o más» y «(Android 14 o más)» en dos casillas de pruebas del README (`:451`, `:459`), y el recuento «7 203 sin Markdown» de `ficha:69`.
