# Clavero 2.1: versión 2.1.0 y documentos (informe)

24-11-2026, sobre `f35faca`, rama `ccr-0aa27f95-u038cd`. Commit «2.1: versionName 2.1.0 y documentos».

## Qué se hizo

1. **Versión.** Primero el test: `AppNameTest` pasa a exigir `versionName = "2.1.0"` (`app/src/test/java/app/seguridad/tools/AppNameTest.kt:122-123`); en rojo con `jvm-check` (1 fallo de 8); después `app/build.gradle.kts:35`, `versionName = "2.1.0"`, y en verde. El `versionCode` no se toca: lo pone la CI (`app/build.gradle.kts:33-34`), como en la 2.0. Ningún otro test ni script fija «2.0.0» (búsqueda en `app/src`, `jvm-check`, `scripts`, `.github`, `.maestro`). El flujo `.maestro/p20-version-y-licencias.yaml:21` sigue esperando «Clavero 1.4.3»: lo pone al día la tarea 11 (Maestro), después del PR y del APK (dueño, 23-11-2026); el README lo dice.
2. **README.**
   - «Funciones»: «**Icono nuevo (2.1).**» bajo «Aspecto (2.1)» (`README.md:73`; R-5 del «1 B» y tabla del §7 del dictamen del logo). Las viñetas «Leer el QR de una red wifi al añadirla (2.1)» y «Aspecto (2.1)» ya estaban.
   - «Formatos»: el apartado «**La 2.1**» (`README.md:83`), en el estilo de «La 1.4.0 no cambió ningún formato…»: qué añade, que no cambia ningún formato (bóveda v4 de `seguridad-vault`, copias con la marca 3 de `seguridad-backup`), que no añade permisos, componentes exportados, conexiones ni dependencias (el manifiesto solo cambia en `versionName` y `versionCode`), y qué sale al actualizar (desde la 2.0, nada; desde la 1.x, lo de la 2.0).
   - «Pruebas de la 2.1» (`README.md:431`): una entrada común (con qué APK, encima de la 2.0, con bóveda de prueba, Maestro después en otro PR) y tres pruebas generales: versión y permisos, instalar encima de la 2.0 (huella, sin guía ni aviso nuevo, ajustes conservados) e icono nuevo (forma del móvil y el monocromo con la «L» separada, R-5 del dictamen del logo). Siguen los dos bloques ya escritos, «QR de wifi» y «Aspecto», sin cambios.
3. **Notas de la versión.** Como en la 2.0 (`2026-11-15-clavero-2.0-notas.md`), en su propio archivo: `docs/superpowers/handoff/2026-11-24-clavero-2.1-notas.md`. Las líneas del QR de wifi y del Aspecto, copiadas tal cual del registro (`progress.md:15` y `:29`); la del icono, «Icono nuevo.» (O-7 del dictamen del logo). GitHub lleva además una línea de qué no cambia. `ImportTextsTest` ya leía todo `*-notas.md` de `handoff/` (sin nombres de otras apps línea a línea) y ahora exige también el de la 2.1 (`ImportTextsTest.kt:166-167`; en rojo antes de crear el archivo, en verde después).
   - **«Novedades» de Play: 365 caracteres** (puntos de código y unidades UTF-16; un solo párrafo, sin saltos de línea), por debajo de 500.
   - Palabras prohibidas de `ForbiddenWords` (comprobado a mano): ninguna.
4. **Política** (`docs/privacidad.html:22`, «Última actualización: 23 de noviembre de 2026.»). Su último cambio de contenido es `13491f7` (23-11-2026, la frase de «Transparente», R-A6), antes `5d4e41b` (23-11-2026, cámara e imagen del formulario de wifi). Desde entonces no ha cambiado nada: **la fecha no se toca**. Sigue siendo cierta tras la decisión del dueño del 24-11-2026 (sin difuminado del fondo de pantalla): la política no hablaba de ese difuminado, solo de que Android dibuja el fondo de pantalla y la app no lee su imagen (`WallpaperWindow.kt:51-54`, `FLAG_SHOW_WALLPAPER` en tiempo de ejecución, sin permiso). El icono no es un tratamiento de datos (dictamen del logo, §7). Si antes de fusionar cambia algo del contenido, la fecha pasa al día del cambio (O-6 del QR de wifi; fila 49 de la 2.0).
5. **Ficha de Play** (`docs/ficha-google-play.md`). No dice ningún número de versión. Lo que dice sigue siendo cierto: los cinco estilos (`:50`), el QR de wifi guardado (`:33`), el icono de la 2.1 (`:7`). La descripción completa: 3 872 caracteres (3 914 contando cada salto dos veces), dentro de 4 000 (`PublishedTextsTest.theFullPlayDescriptionFits`). **Un cambio:** el recuento de la «Versión larga» decía «6 948 sin Markdown», que era el de la 2.0; con las líneas del Aspecto de la 2.1 son **7 230** (`:69`, medido igual que el 6 948 de `v2.0.0`). Para el Abogado, dos notas sin cambio: (i) `:57` «Al actualizar, la bóveda pasa a un formato nuevo…» solo es cierto al actualizar desde la 1.x; desde la 2.0 no pasa nada. Propuesta, si lo quiere: «Al actualizar desde la 1.x, …» (+13 caracteres, cabe). (ii) `:7` «Las capturas se hacen de nuevo con la 2.0, con el aspecto «Cristal»» sigue valiendo, pero puede querer decir la 2.1.
6. **Guía de novedades y aviso del aspecto.** No hace falta entrada para la 2.1, sin texto nuevo:
   - la guía corta es la «de la 2.0» (`settings/Guide.kt:82`, «Ver las novedades de la 2.0»; `Guide.kt:17`, «nothing outside 2.0»), y `GUIDE_CURRENT = 2` (`settings/Settings.kt:110`) solo sube «when a version brings a guide of its own». El dictamen del QR de wifi (§3) dice «Sin cambios»: meter ahí la 2.1 la haría falsa. El del logo (§7) deja «Icono nuevo» en la guía como opcional.
   - `APPEARANCE_NOTICE` (`ui/screens/AppearanceScreen.kt:126`) sigue cierto con «Degradado» (dictamen del Aspecto, línea 48) y solo sale una vez desde la 1.x.
   - Si el dueño quiere una guía de la 2.1, sería texto de interfaz nuevo y una decisión de diseño: no se ha hecho.
7. **Lo que no va en las notas:** el arreglo del test inestable `RedTeam20CopyTest.rt21` (N-4) no cambia nada de la app.

## Tests

- Primero en rojo y después en verde: `AppNameTest` (versión) y `ImportTextsTest.thePlayListingAndItsNovedadesNameNoOtherProduct` (borrador de la 2.1).
- `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets --no-daemon --max-workers=1` (con `flock`): BUILD SUCCESSFUL; **2 672 tests, 0 fallos**, 0 omitidos; *lint*, licencias, fragment y assets en verde.
- `./gradlew -p jvm-check test --no-daemon --max-workers=1` (con `flock`): BUILD SUCCESSFUL; **2 580 tests, 0 fallos**.
- Una primera tanda se cortó por el tope de tiempo de la tarea en segundo plano (no por un fallo); se repitió entera.

## Archivos

- `app/build.gradle.kts`
- `app/src/test/java/app/seguridad/tools/AppNameTest.kt`
- `app/src/test/java/app/seguridad/imports/ImportTextsTest.kt`
- `README.md`
- `docs/ficha-google-play.md`
- `docs/superpowers/handoff/2026-11-24-clavero-2.1-notas.md` (nuevo)
- `docs/superpowers/handoff/sdd-2.1/progress.md` (una línea)
- `docs/superpowers/handoff/sdd-2.1/version-2.1-report.md` (este informe)

Sin tocar: `CLAUDE.md`, el parte, `docs/privacidad.html`, `docs/PUBLICAR.md`, el código de la app.

## Revisión experto-seguridad-cripto

- No cambia ninguna primitiva, formato, clave ni red: el diff solo toca `versionName`, dos tests y documentos. Los formatos que el README dice que no cambian lo confirman `git diff v2.0.0 HEAD -- app/src/main/AndroidManifest.xml gradle/` (vacío) y que la 2.1 no toca `vault/Backup.kt` ni `VaultFile`.
- Veracidad: «No añade permisos, conexiones ni dependencias» (notas, `README.md:83`) se apoya en ese diff vacío y en `WallpaperWindow.kt:51-54` (la bandera de la ventana se pone en tiempo de ejecución; el manifiesto no cambia, B-2). «La 2.0 abre lo que escribe la 2.1»: el ajuste de estilo nuevo, que no es un formato de datos, lo lee la 2.0 como «Cristal» (`core/Json.kt:8`, `coerceInputValues`, en `v2.0.0` también).
- Las notas no dicen que nada proteja más de lo que hace; «la app no lee su imagen ni pide permisos» es la del dictamen del Aspecto y es cierta (sin `WallpaperManager`, fijado en `PlatformSourcesTest`).
- Privacidad del dueño: ningún dato suyo en los textos nuevos; las pruebas usan bóvedas y QR de prueba.

## Revisión Ingeniero Móvil Senior

- `versionName` en `app/build.gradle.kts:35`; `versionCode` por propiedad de la CI (`:33-34`), como siempre: cada subida a Play necesita uno mayor y lo da el número de ejecución.
- Actualizar desde la 2.0: `guideDue` da `NONE` con `guideSeen = GUIDE_CURRENT = 2` (`Settings.kt:110` y siguientes), y `appearanceNoticePending` ya se decidió en el primer arranque de la 2.0 (`Settings.kt:146-149`): por eso el README dice que no sale ninguna guía ni aviso nuevo. Lo comprueba en el móvil la prueba «2.1, encima de la 2.0».
- `check-manifest.sh` no fija `versionName` (`scripts/check-manifest.sh:11-14`); la prueba «versión y permisos» del README lo pasa sobre el APK firmado.
- Maestro: P-20 sigue con «1.4.3» (`.maestro/p20-version-y-licencias.yaml:21`), apuntado para la tarea 11.

## Textos para el Abogado Digital (revisión final)

- Todo `docs/superpowers/handoff/2026-11-24-clavero-2.1-notas.md`: las notas de GitHub y las «Novedades» de Play (365 caracteres). Nuevas frente al registro: «No cambia el formato de la bóveda ni el de las copias: la 2.1 abre lo mismo que la 2.0. No añade permisos, conexiones ni dependencias.» y «Icono nuevo.»
- En la línea del Aspecto, lo que queda abierto: «con bordes iluminados» con «Alto contraste» (registro, línea 30, (a)); que «Transparente» en pantalla dividida o ventana flotante se ve como «Cristal» (registro, línea 45) y la línea no lo dice.
- `README.md:73`, «**Icono nuevo (2.1).**».
- `README.md:83`, el apartado «**La 2.1** …» entero.
- `README.md:431-435`, la entrada de «Pruebas de la 2.1» y sus tres pruebas generales.
- `docs/ficha-google-play.md:69`, «(7 230 sin Markdown, con las líneas del Aspecto de la 2.1)».
- Propuestas sin aplicar para la ficha: `:57` «Al actualizar desde la 1.x, …» y `:7` «capturas… con la 2.0» → «con la 2.1».
- Recordatorio: «Ver las novedades de la 2.0» (`Guide.kt:82`) no cambia; una guía de la 2.1 no se hace (dictamen del QR de wifi, §3).
