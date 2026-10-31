# Tarea 15: documentos, versión 1.3.0 y revisión de los textos

**Estado:** DONE_WITH_CONCERNS. Commit `f439d00` «Seguridad 1.3.0: documentación y versión», autor `Claude <noreply@anthropic.com>`, que termina solo con `Co-Authored-By: Claude Opus 5.5`. Sin push. BASE `8df0860`.

La sesión se cortó una vez por el límite de 5 horas mientras se escribía el anexo. Al reanudar, se comprobó que `SourceTextsTest.kt` (324 líneas, termina en el cierre de `KotlinLexer`) y el anexo (183 líneas, termina en «Licencias y atribución») estaban completos. Después se siguió con el resto.

## Qué se ha hecho

### 1. Anexo (`docs/superpowers/specs/2026-10-27-seguridad-1.3.0-textos.md`)

Ahora coincide con el código, que es la fuente. Hay una sección nueva, «Textos añadidos después del dictamen», con 34 textos de CU-A, CU-B y las tareas 9 a 13, que van a la revisión final del Abogado Digital. La cabecera explica que el código manda y que `ToolTextsTest` falla si no coinciden. Las reglas comunes añaden «limpio» y «sospechoso», y citan `ToolTextsTest` y `SourceTextsTest` en lugar de `PkActionsTest`.

| Texto del anexo | Antes → después | Origen |
|---|---|---|
| Limpiar foto, explicación | «…La foto se analiza en tu móvil y no se envía a ningún sitio.» → «…y la app no la envía a ningún sitio: solo sale si tú compartes la copia.» | CU-A C-9 |
| Línea del móvil | «Móvil: marca, modelo y número de serie» → «Móvil o cámara: marca y modelo, y a veces número de serie o nombre del propietario» | CU-A C-11 |
| Autor y derechos | «…si la foto no es tuya, no los quites para ocultar de quién es» → «…la copia no los lleva. Si la foto no es tuya, no uses la copia para ocultar de quién es» | CU-A C-13 |
| HEIC/HEIF/AVIF | añade «y, si la foto pasa de unos 24 megapíxeles, se reduce a ese tamaño.» | CU-A C-3 |
| Nombre del archivo | «foto-limpia» → «copia-de-foto», más el nombre `copia-de-foto.<ext>` | CU-A C-4, ruling del nombre |
| Inspector de QR, explicación | «Solo puedes copiarlo.» → «Puedes copiarlo y, si es una tarjeta o un mensaje de Seguridad o un código 2FA, pasarlo a su pantalla.» | CU-A C-15 |
| `SGK1:` | «con la persona, en persona» → «con esa persona, cara a cara» | CU-A C-17 |
| StrongBox | «Chip seguro (StrongBox)» → «Chip de seguridad (StrongBox)»; «Zona segura del procesador» → «Zona aislada del procesador (TEE)» | CU-A C-5, C-6 |
| R2 | «…chip seguro.…» → «…chip de seguridad (StrongBox).…» | CU-A C-7 |
| A12 | el texto corto → el `RESET_WIPES_DAMAGED` entero («Lo guardado no se puede descifrar… podrás importarlas en una bóveda nueva.») | CU-A C-25 |
| A6, A14, A15b, A16a | «los textos del diseño» → los textos reales, en la sección nueva | tareas 11 a 13 |
| Sección nueva | subtítulo, guardado fallido, sin memoria, TalkBack de Tachar, 3 del QR, `CODES_UNLOCK_TO_SAVE`, 4 de huella, 13 de A6, 4 de A14, 2 de A15b, 1 de A16a y la viñeta de Herramientas | CU-A C-10, C-14, C-16, C-18, C-19 y C-20 a C-24; CU-B; tareas 9 a 13; esta tarea |
| Licencias | bip39.txt «y» → «and»; LicensesScreen → «Las 2.048 palabras en inglés y las 2.048 en español, sin cambios.» | CU-A C-26, C-27 |
| «Cambios en los documentos» | reescrita con los textos que se han aplicado, adaptados a CU-A (C-3, C-4, C-9 y C-22, R-9) | R-9 |

Se ha borrado `SUPERSEDED_ANNEX_TEXTS` y su test. `everyAnnexTextIsInTheListVerbatim` pasa con el anexo como referencia.

`foto-limpia` → `copia-de-foto` también en:
- el diseño, `:104-105` (y las etiquetas de StrongBox de `:207-208`, `:212` y `:217`, la «línea 210»);
- el plan, `:38`, `:170`, `:183` y `:221`;
- `context.md:39`, en las dos copias: la de trabajo (ignorada por git) y `docs/superpowers/handoff/sdd-1.3.0/context.md`.

### 2. Documentos
- `docs/privacidad.html`:
  - fecha (`:22`);
  - fotos en «Qué datos recogemos»;
  - A6/C-22 y las herramientas que generan (`:35-36`);
  - sección nueva «Fotos e imágenes» (`:39-41`);
  - permiso de cámara (`:50`) y «No pide permiso de fotos ni de ubicación.» (`:52`).
- `docs/ficha-google-play.md`:
  - reglas de la cabecera (`:3`);
  - «para estar seguro de que es suya» → «para confirmar que es suya» (`:29`, una palabra prohibida que ya estaba);
  - «Herramientas» (`:34-38`);
  - «Privacidad» (`:41`);
  - dos viñetas nuevas en «Antes de usarla» (`:46-47`);
  - la descripción tiene 3605 caracteres, por debajo del límite de 4000.
- `docs/PUBLICAR.md` §3.5: seguridad de los datos (`:87`), funciones financieras (`:90`), palabras prohibidas (`:92`) y permisos (`:93`).
- `README.md`:
  - StrongBox en «Bóveda» (`:15`);
  - «fechas falsas» → «fechas inventadas» (`:21`), para que el grep de «falsas» quede limpio;
  - herramientas de imagen (`:31-35`);
  - permisos (`:38`);
  - 5 pruebas en el móvil (`:225-229`): StrongBox, Limpiar foto con 200 MP y HEIC, compartir a WhatsApp y Gmail, Tachar e Inspector de QR. Gboard ya estaba.
- **Fecha: 30-10-2026, no 27-10-2026.** La política de la 1.2.0 ya tenía «27 de octubre de 2026», así que esa fecha no sería «nueva». Uso la de hoy, que coincide con la de la 1.3.0 en CLAUDE.md. Si se publica otro día, hay que cambiarla en la tarea 16 (lo dice el anexo).

### 3. ToolsScreen
Constante nueva `IMAGE_TOOLS_LIMIT` (`ToolTexts.kt:217`, en `ALL_TOOL_TEXTS` `:523`), mostrada en «Cómo protegemos tus datos» (`ToolsScreen.kt:83`). Es la frase completa de la ficha: «Las herramientas de imagen no quitan lo que se ve ni cambian el original: comparte la copia.». CU-A no había cambiado ese texto. Se ha hecho constante para que el test del anexo la cubra.

### 4. SourceTextsTest
- Un lexer nuevo (`KotlinLexer`, `:181`) junta las cadenas de literales unidos por `+`, también cuando hay comentarios o saltos de línea entre ellos. Los char literal entran en la cadena, y una cadena sin ningún literal de texto no cuenta como texto.
- Las plantillas `${…}` se leen como código. Así, `'"'` y `'}'` ya no rompen el lexer, y `${'o'}` o `${"o"}` pasan a formar parte del texto.
- Se decodifican los escapes (`\n`, `\uXXXX` y `\$`).
- «garantiza» se juzga sobre el texto entero: «no » + «garantiza…» pasa, y «garan» + «tiza» falla.
- `res/values*/*.xml` y las etiquetas literales del manifiesto también se leen (`xmlTexts`, `:151`).
- El KDoc dice qué se excluye (assets, `res/xml` y drawables) y los límites: no se juntan los trozos alrededor de algo que no sea un literal, ni `$name`, `buildString` o `format`.

### 5. ToolTextsTest
- `everyToolSectionOfTheAnnexIsCheckedAgainstTheApp` (`:70`) comprueba cada sección del anexo, una por tarea (de la 4 a la 13). El número de textos de cada una es exacto (17, 3, 19, 2, 6, 7 y 34), y todos tienen que estar en la app.
- `theAnnexNamesTheCopyAsTheAppDoes` (`:89`) comprueba que el anexo dice `copia-de-foto.<ext>`, que no dice `foto-limpia` y que tiene la viñeta nueva.

### 6. `versionName = "1.3.0"` (`app/build.gradle.kts:35`). versionCode no cambia.

## Tests

- **RED** (nuevos fixtures de `SourceTextsTest` con un `xmlTexts` vacío): `9 tests completed, 5 failed`. Fallaron `escapesAreDecoded`, `aWordSplitAcrossConcatenatedLiteralsIsCaught`, `resourceStringsAreRead`, `aCharLiteralInsideATemplateIsReadAndDoesNotBreakTheLexer` y `garantizaSplitAcrossPiecesIsJudgedOnTheWholeText`. `aCharLiteralAloneIsNotAText` ya pasaba: es una salvaguarda contra falsos positivos.
- **GREEN:** los 9 pasan.
- **Prueba de mutación** sobre el código real, con `--rerun`:
  - `"…no es segu" +` / `"ro recuperarlo."` en `ToolsScreen.kt` y `<string>Una nota segura</string>` en `strings.xml` hacen fallar el test con `ToolsScreen.kt:83 [«seguro/a»]` y `strings.xml:4 [«seguro/a»]`;
  - después se restauraron los dos archivos.
- `ToolTextsTest`: la primera ejecución falló por un recuento («Inspector de QR: only 19»). Se fijaron los números exactos y pasó.
- **Verificación completa**, una vez, antes del commit:
  - `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets --no-daemon --max-workers=1 --offline`: BUILD SUCCESSFUL, 960 tests, 0 fallos y 0 errores.
  - `gradle -p jvm-check test --no-daemon --max-workers=1 --offline`: BUILD SUCCESSFUL, 960 PASSED y 0 FAILED.
  - Después solo cambió la palabra «falsas» del README, que es documentación.
- `grep -rn "lápiz\|falsas" docs app/src/main`:
  - no queda nada en `app/src/main`, `docs/*.html`, `docs/*.md` ni en el README;
  - quedan coincidencias solo en documentos internos de `docs/superpowers/`: la propia línea 648 del plan, que cita el comando; el diseño 1.3.0, `:185` («no “falsas”») y `:186` («“lápiz” no está» en la lista); el diseño 1.2.0, `:390` («tarjetas falsas»); y dos informes del traspaso;
  - no se muestran al usuario, así que no se tocan.
- `grep -rn "foto-limpia"`: solo quedan menciones históricas en `handoff/` y `progress`, y además:
  - `ShareTempTest.kt:96,123`, a propósito, porque el nombre antiguo tiene que rechazarse y borrarse;
  - `ToolTextsTest.kt:92`, la comprobación de que el anexo ya no lo dice;
  - CLAUDE.md `:44` y `:62`;
  - plan `:38` y `context.md:39`, que lo nombran como «antes» o «no».

## Revisión propia y dudas
- **Duda 1.** El test de jvm-check queda «up to date» si solo cambian archivos de `ui/` o `res/`, porque no son entradas de esa tarea. Por eso la prueba de mutación usó `--rerun`. En `:app:testDebugUnitTest` esos archivos sí son entradas, y la CI lo ejecuta. Ya pasaba antes; no lo he cambiado.
- **Duda 2.** La fecha de la política (30-10-2026) está explicada arriba.
- **Duda 3.** Añadí frases a la ficha y a la política que no estaban en la lista literal del anexo, para cumplir R-9 (C-3, C-4, C-9 y C-22) y no prometer más que la app. Todas son ahora el texto del anexo y van a la revisión final.
- **Duda 4.** «Las herramientas de imagen no quitan lo que se ve»: Tachar sí tapa lo que marcas. Es cierto en el sentido de que ninguna herramienta lo quita sola. Que el Abogado lo confirme.
- La ficha dice «No garantiza que un enlace sea seguro». No es la frase exacta que `ForbiddenWords` exime («no garantiza que sea seguro»), pero es el texto aprobado del anexo, y la ficha no la comprueba ningún test.

## Revisión experto-seguridad-cripto
- Sin cambios de formato, criptografía, red, permisos ni dependencias. Solo hay un texto nuevo en la UI (`ToolTexts.kt:217`, `ToolsScreen.kt:83`).
- La política, contrastada con el código:
  - selector `OpenDocument`, sin permisos de medios (`PhotoCleanScreen.kt:340`, `RedactScreen.kt:477`, `QrInspectScreen.kt:161`; el manifiesto solo tiene INTERNET y CAMERA, `AndroidManifest.xml:4,6`);
  - copia temporal única en `cacheDir/compartir`, borrada al compartir otra, al volver pasados 10 minutos o al arrancar (`ShareTemp.kt:10,43-45,64-71`, `SeguridadApp.kt:47`, `MainActivity.kt:61`);
  - nombres fijos (`ShareTemp.kt:16-19`, `Redaction.kt:93`);
  - A6/C-22 (`AutoBackup.kt:96-99`, `ToolTexts.kt` `PASSWORD_CHANGE_OLD_BACKUPS`);
  - la red sigue siendo solo HIBP.
- El test de palabras prohibidas es más estricto: cubre las cadenas partidas, los char en plantillas, los escapes `\u`, los recursos y el manifiesto (`SourceTextsTest.kt:151,179-324`). Ya no puede saltarse con esas formas, y la prueba de mutación lo confirma.

## Revisión Ingeniero Móvil Senior
- `ToolsScreen.kt:83`: solo cambia una cadena de una lista ya existente, sin estado ni recomposiciones nuevas. Lint y assembleDebug pasan.
- `versionName` 1.3.0 (`app/build.gradle.kts:35`). versionCode sigue saliendo de `-PversionCode` (`:34`).
- Las pruebas nuevas del README en el móvil (`README.md:225-229`) cubren StrongBox, HEIC, Gmail con lectura diferida del `content://` y el borrado de `cacheDir/compartir`. Recogen la lista §2.6 del traspaso.

## Textos para el Abogado Digital (revisión final)

**UI**
- `IMAGE_TOOLS_LIMIT`, `app/src/main/java/app/seguridad/tools/ToolTexts.kt:217` (mostrado en `ToolsScreen.kt:83`): «Las herramientas de imagen no quitan lo que se ve ni cambian el original: comparte la copia.»

**`docs/privacidad.html`**
- `:22`: «Última actualización: 30 de octubre de 2026.»
- `:29`: «No recibimos, vemos ni guardamos tus contraseñas, códigos 2FA, frases semilla, notas, adjuntos, archivos, tus fotos e imágenes, tu identidad ni tus contactos.»
- `:35`: «Al cambiar la contraseña maestra, las copias que ya existen se siguen abriendo con la contraseña anterior. Puedes pedir que se haga una copia nueva y se borren las copias automáticas anteriores que la app hizo en esa carpeta; las que se hayan copiado a otro sitio (otra carpeta, la nube o su papelera) no se borran.»
- `:36`: «Los PIN, los códigos de recuperación y las respuestas inventadas se generan en el móvil; si los guardas, van a la bóveda cifrada.»
- `:39`, título: «Fotos e imágenes».
- `:40`: «Limpiar foto, Tachar en imagen y el Inspector de QR (con «Elegir imagen») abren solo la imagen que eliges tú con el selector de Android: la app no pide permiso para ver tu galería. La imagen se analiza en el móvil y la app no la envía a ningún sitio: solo sale del móvil la copia que tú compartas. Si guardas una copia, se escribe donde tú elijas; si esa carpeta está sincronizada con un servicio en la nube, ese servicio la recibe según su propia política. Si pulsas «Compartir», la app prepara una copia temporal en su almacenamiento interno y se la entrega a la app que elijas (por ejemplo, una de mensajería), que la trata según su propia política. Solo hay una copia temporal a la vez: se borra cuando compartes otra, al volver a Seguridad pasados unos minutos o, como muy tarde, la próxima vez que abras la app.»
- `:41`: «La copia de Limpiar foto se propone con el nombre «copia-de-foto» y la de Tachar en imagen con «imagen-tachada», para que el nombre no diga la fecha ni quién sale. Limpiar foto quita los datos ocultos del archivo (la ubicación, el modelo del móvil, la fecha y otros), pero no lo que se ve en la imagen, y el original no se toca: sigue teniéndolos. Las fotos HEIC, HEIF y AVIF se guardan como JPEG y, si pasan de unos 24 megapíxeles, se reducen a ese tamaño.»
- `:50`: «Cámara: solo mientras escaneas un código QR (de 2FA, de exportación de Google Authenticator, de la tarjeta de un contacto, o cualquier QR en el Inspector de QR). Las imágenes no se guardan ni se envían.»
- `:52`: «No pide permiso de fotos ni de ubicación.»

**`docs/ficha-google-play.md`**
- `:3`, en las reglas: «…«con validez legal», «anónimo», «garantiza» salvo «no garantiza», «limpio», «sospechoso»)…»
- `:29`: «…comprueba su huella para confirmar que es suya.»
- `:34`: «Generador de contraseñas y frases, PIN al azar sin patrones habituales, códigos de recuperación y respuestas inventadas para preguntas de seguridad.»
- `:35`: «Limpiar foto: quita la ubicación y otros datos ocultos del archivo antes de enviarla. No cambia lo que se ve en ella. Las fotos HEIC, HEIF y AVIF se guardan como JPEG, reducidas si pasan de unos 24 megapíxeles.»
- `:36`: «Tachar en imagen: tapa con bloques negros lo que no quieras enseñar en una captura.»
- `:37`: «Inspector de QR: enseña qué hay en un QR, con avisos de señales típicas de engaño, sin abrir nada. No garantiza que un enlace sea seguro.»
- `:38`: «Huella SHA-256 de archivos y comprobación de contraseñas filtradas.»
- `:41`, al final: «Las fotos se procesan en el móvil y la app no las envía a ningún sitio: solo sale la copia que tú compartas.»
- `:46`: «Las herramientas de imagen no quitan lo que se ve ni cambian el original: comparte la copia.»
- `:47`: «Cambiar la contraseña maestra no cambia las copias que ya existen: se siguen abriendo con la contraseña anterior.»

**`docs/PUBLICAR.md`**
- `:87`, al final: «Las herramientas de imagen de la 1.3.0 procesan en el móvil; compartir lo inicia el usuario; la sección no cambia (no se declara «Fotos y vídeos»).»
- `:90`, al final: «El Inspector de QR enseña los datos de un QR de pago (SEPA o criptomonedas), pero no inicia pagos: sigue siendo «ninguna».»
- `:92`: añade «anónimo», «garantiza» salvo «no garantiza».
- `:93`: «…cámara (solo al escanear un QR de 2FA, de Google Authenticator, de la tarjeta de un contacto o el Inspector de QR). Sin permiso de fotos ni de ubicación de medios (`ACCESS_MEDIA_LOCATION`): las imágenes se eligen con el selector del sistema. El `FileProvider` no está exportado y no es un permiso.»

**`README.md`**
- `:15`: «…con la huella (Android Keystore; desde la 1.3.0, en el chip de seguridad StrongBox si el móvil lo tiene, y si no o si falla, en la zona aislada del procesador).»
- `:21`: «…dejando archivos con fechas inventadas.»
- `:31-35`: el bloque «Herramientas de imagen (1.3.0)» completo:
  - Limpiar foto con `copia-de-foto.<ext>` y HEIC;
  - Tachar con `imagen-tachada.<ext>`;
  - Inspector de QR: «No garantiza que un enlace sea seguro.»;
  - la copia temporal, «se borra al compartir otra, al volver a la app pasados 10 minutos o al arrancarla».
- `:38`: permisos «…o en el Inspector de QR…» y «Sin permiso de fotos ni de ubicación (`READ_MEDIA_IMAGES`, `ACCESS_MEDIA_LOCATION`): las imágenes se eligen con el selector del sistema.»
- `:225-229`: cinco pruebas en el móvil. Citan textos de la app tal cual: «Dónde está la clave de la huella», «Chip de seguridad (StrongBox)», «Zona aislada del procesador (TEE)», «Activar huella», «Guardar copia», «Compartir» y «Mostrar».

**Anexo:** todo el archivo `docs/superpowers/specs/2026-10-27-seguridad-1.3.0-textos.md`. En particular:
- la sección «Textos añadidos después del dictamen», con los 34 textos ya en la app de CU-A, CU-B y las tareas 9 a 13, que se revisan aquí por primera vez en conjunto;
- «Cambios en los documentos», reescrita.

---

# Fix round 1

**Estado:** DONE. Commit `d1b08cd`, «Seguridad 1.3.0: documentación y versión, revisión 1», sobre `c8a4d46` y `1dc9e2c`, que son commits del controlador y no se han tocado. Autor `Claude <noreply@anthropic.com>`. El mensaje termina solo con `Co-Authored-By`. No se ha hecho push.

## Cambio por hallazgo

- **I-1.** Se sustituye por «Limpiar foto no cambia lo que se ve, y Tachar solo tapa lo que marcas tú. Ninguna cambia el original: comparte la copia.» en:
  - `ToolTexts.kt:227` (`IMAGE_TOOLS_LIMIT`, que se muestra en `ToolsScreen.kt:83`);
  - el anexo, en «Textos añadidos» y en «Cambios en los documentos»;
  - la ficha (`:46`);
  - el README (`:31`).

  El plan (`:643`) dice ahora la frase nueva y por qué cambió.
- **M-1.** `ForbiddenWords.kt`: «no» solo cuenta si es una palabra entera (`(?<!\p{L})no (?:se |lo )?$`). Fixture: «El cifrado moderno garantiza que sea seguro» falla.
- **M-2.**
  - Normalización (`ForbiddenWords.normalise`):
    - toda serie de `\s` o `\p{Zs}` (NBSP, U+202F, saltos de línea) pasa a ser un espacio;
    - se quitan los caracteres que no se ven y podrían partir una palabra (U+00AD, U+200B, U+200C, U+200D, U+2060 y U+FEFF). Esto es un añadido mío, por el mismo motivo.
  - Formas aceptadas: «no garantiza que [0 a 3 palabras] sea seguro/a/os/as», y «garantiza» detrás de «no», «no se» o «no lo».
  - XML: `<(string|item)(?=[\s/>])`, así que `<string-array>` no cuenta como `<string>` y sus `<item>` se leen uno a uno.
  - Fixtures (`SourceTextsTest.kt:90`, `:106` y `:170`):
    - NBSP y U+202F en «100 %»;
    - «de\nverdad»;
    - «no\ngarantiza…» y «no  garantiza…» se aceptan;
    - las tres formas legítimas, y «No garantiza que el enlace web sea seguro»;
    - con 4 palabras, ya no es una negación;
    - «No garantiza que sea seguro. Es seguro.» falla;
    - una palabra partida con un carácter invisible («garanti­za», «se​gura», «im⁠posible») falla;
    - `<string-array>`.
  - Sobre «no\ngaranti za»: lo he leído como «no» + salto de línea + «garantiza». «garanti za», con un espacio de verdad, son dos palabras en pantalla y no la palabra prohibida, así que no se marca: marcarlo daría falsos positivos. Sí se marca cuando la separan caracteres invisibles.
- **M-3.**
  - Código:
    - `PHOTO_UNSUPPORTED_BEFORE_ANDROID_9` (`ToolTexts.kt:132`) y la función pura `photoUnsupportedText(sdk)` (`:136`, con 28 o más devuelve `PHOTO_UNSUPPORTED`);
    - el texto se elige en `PhotoCleanScreen.kt:167` y `RedactScreen.kt:248`, con `Build.VERSION.SDK_INT`;
    - el código puro sigue lanzando `PHOTO_UNSUPPORTED` (`HeifToJpeg.kt:29-30`, `RedactRender.kt:49-51`), y solo cambia el texto que se muestra. El texto general no nombra AVIF, así que también es cierto de Android 9 a 11.
  - Test: `theUnsupportedFormatTextNamesHeicOnlyWhereItCanBeConverted` (`ToolTextsTest.kt:141`).
  - En los documentos se dice desde qué versión: HEIC y HEIF desde Android 9, AVIF desde Android 12, en `privacidad.html:41`, la ficha (`:35`) y el README (`:32`).
- **M-4.**
  - `privacidad.html:41`, la ficha (`:35`) y el README (`:32`) ya no dicen que se quita todo. Dicen que «la copia solo conserva la imagen, sus datos de color (como el perfil de color) y, en las JPEG, la orientación», y que no quita las marcas invisibles.
  - La regla pedía decir «solo el perfil de color y la orientación», pero lo he cambiado a «datos de color». El código conserva en PNG `gAMA`, `cHRM`, `sRGB`, `sBIT`, `PLTE` y `tRNS` (`Png.kt:209`), y en WebP `ICCP` (`Webp.kt:61`). La orientación solo se reescribe en JPEG (`Jpeg.kt:19-26`, y el ruling de la tarea 2 sobre PNG y WebP). «Solo el perfil de color» habría sido falso para PNG.
- **M-5.**
  - Sección nueva del anexo, «Textos de la app que no estaban en el dictamen» (`:132`), con 123 textos agrupados por herramienta. Salieron del fallo del test nuevo.
  - La cabecera del anexo ahora distingue lo revisado de lo pendiente, y el título ya no dice «aprobados».
  - Test nuevo `everyAppTextIsInTheAnnex` (`ToolTextsTest.kt:71`): todo `ALL_TOOL_TEXTS` tiene que estar en el anexo.
  - Los recuentos exactos por sección pasan a 35 y 123.
- **M-6.**
  - El plan (`:677`) y el traspaso (`:137`) dicen «Chip de seguridad (StrongBox)».
  - Hay dos pruebas nuevas en el README: un QR de enlace (`:230`), y el PIN con los códigos y las respuestas guardados (`:231`).
  - La app no guarda el PIN: solo lo copia (`CodesScreen.kt`). Lo que se guarda como nota son los códigos de recuperación y las respuestas, y la prueba lo dice así.
- **M-7.** README `:15`: la clave va a StrongBox al activar la huella. Quien la tenía activada en la 1.2.2 sigue en la TEE hasta que la desactive y la vuelva a activar.
- **M-8.** `privacidad.html:62`: desinstalar no borra lo que se guardó fuera de la app (las copias `.sgbak`, también las automáticas, las copias de fotos, las tachadas y los archivos cifrados). Eso lo borra el usuario.
- **M-9.** Comprobado en el código: `copia-de-foto` solo admite `jpg`, `png` o `webp`, y `imagen-tachada` solo `jpg` o `png` (`Redaction.kt:93`, `ShareTemp.kt:19`). Lo dicen ya `context.md:39` (en las dos copias), el plan (`:38`) y el README (`:32-33`).
- **M-10.**
  - README `:38` y PUBLICAR `:93`: la biblioteca `androidx.core` declara `io.github.egt17baa.seguridad.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. Es un permiso de firma que solo tiene la propia app, protege sus receptores internos para que ninguna otra app les envíe avisos y no da acceso a nada del móvil.
  - `scripts/check-manifest.sh:28-29` ya lo esperaba.
  - El código propio no registra receptores (`grep registerReceiver`: 0). Por eso el texto no dice que la app «lo use».

## Tests (RED/GREEN)

- **M-1 y M-2 en `SourceTextsTest`.**
  - RED: «11 tests completed, 2 failed» (`theAllowedDenialsAreWholeWordsAndOnlyThose` y `spacingAndInvisibleCharactersDoNotHideAWord`).
  - El caso de `<string-array>` ya pasaba: la etiqueta de cierre `</string-array>` no casaba con `</string\s*>`. Aun así, la expresión se ha hecho exacta.
  - GREEN: todo pasa.
- **M-3 en `ToolTextsTest`.**
  - RED: error de compilación, «Unresolved reference 'photoUnsupportedText'» y «'PHOTO_UNSUPPORTED_BEFORE_ANDROID_9'».
  - GREEN después de implementarlo.
- **M-5, `everyAppTextIsInTheAnnex`.**
  - RED: «20 tests completed, 1 failed», con 124 textos que faltaban (123 de la lista y el texto M-3).
  - GREEN después de añadir la sección.
- **Verificación completa** (`--offline --no-daemon --max-workers=1`), después del último cambio:
  - `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets`: BUILD SUCCESSFUL, 964 tests, 0 fallos y 0 errores.
  - `gradle -p jvm-check test`: BUILD SUCCESSFUL, 964 PASSED y 0 FAILED.
- **Palabras prohibidas en los documentos públicos.** Pasé un script con las mismas reglas por `privacidad.html`, la ficha (desde la descripción), el README y PUBLICAR §3.5. Solo marca la línea de PUBLICAR `:92`, que es la propia lista de palabras prohibidas.

## Dudas
- En M-4, «datos de color» en lugar de «solo el perfil de color» (explicado arriba).
- En M-2, la lectura de «no\ngaranti za» (explicada arriba).
- En M-6, el PIN se copia y no se guarda, y la prueba lo dice así.

## Revisión experto-seguridad-cripto
- **Sin cambios de comportamiento** salvo el texto de error. El código puro sigue fallando de forma cerrada con `PHOTO_UNSUPPORTED` (`HeifToJpeg.kt:29-30`, `RedactRender.kt:49-51`). La pantalla solo cambia lo que enseña (`PhotoCleanScreen.kt:167`, `RedactScreen.kt:248`).
- **Sin cambios** en formatos, criptografía, red, permisos ni dependencias.
- **El control de palabras prohibidas es más estricto.** «No» tiene que ser una palabra entera. Los espacios Unicode y los caracteres invisibles ya no sirven para saltarse la regla. La negación tiene una forma acotada, con 3 palabras como máximo, para que no exima un «seguro» que va en otra frase (`ForbiddenWords.kt`; fixtures en `SourceTextsTest.kt:90` y `:106`).
- **Documentos, comprobados contra el código:**
  - lo que se conserva en la copia: `Jpeg.kt:19-26`, `Png.kt:209` y `Webp.kt:61`;
  - las versiones de Android: `HeifToJpeg.kt:29-30`;
  - los nombres y extensiones: `Redaction.kt:93`, `ShareTemp.kt:19`;
  - el permiso de firma: `scripts/check-manifest.sh:28-29`;
  - lo que no se borra al desinstalar: las copias se escriben con SAF donde elige el usuario.

## Revisión Ingeniero Móvil Senior
- `Build.VERSION.SDK_INT` se lee solo en `ui/`. La función es pura y se prueba en jvm-check (`ToolTexts.kt:136`). Los imports nuevos son `android.os.Build` y `photoUnsupportedText`, en `PhotoCleanScreen.kt:5,58` y `RedactScreen.kt:6,89`. Lint pasa.
- minSdk 26: los textos ya no mencionan HEIC en Android 8. El README explica qué pasa con StrongBox para quien actualiza desde la 1.2.2 (`:15`).
- Pruebas en el móvil nuevas: README `:230-231`.

## Textos para el Abogado Digital (revisión final), lista completa

Siguen valiendo los textos de la primera entrega (arriba), con estos cambios.

**UI**
- `IMAGE_TOOLS_LIMIT`, `ToolTexts.kt:227` (se muestra en `ToolsScreen.kt:83`): «Limpiar foto no cambia lo que se ve, y Tachar solo tapa lo que marcas tú. Ninguna cambia el original: comparte la copia.» Sustituye al texto anterior.
- `PHOTO_UNSUPPORTED_BEFORE_ANDROID_9`, `ToolTexts.kt:132` (se muestra en Android 8, en `PhotoCleanScreen.kt:167` y `RedactScreen.kt:248`): «Este formato no se admite en este móvil. Usa una foto JPEG, PNG o WebP: las HEIC necesitan Android 9 o posterior.»
- Los 123 textos de la sección «Textos de la app que no estaban en el dictamen» del anexo (`docs/superpowers/specs/2026-10-27-seguridad-1.3.0-textos.md:132`). Ya estaban en la app y no cambian, pero llegan a la revisión por primera vez.

**`docs/privacidad.html`**
- `:41`: «La copia de Limpiar foto se propone con el nombre «copia-de-foto» y la de Tachar en imagen con «imagen-tachada», para que el nombre no diga la fecha ni quién sale. Limpiar foto quita la ubicación, el modelo del móvil, la fecha y otros datos ocultos del archivo: la copia solo conserva la imagen, sus datos de color (como el perfil de color) y, en las JPEG, la orientación (el giro) de la foto. No cambia lo que se ve en la imagen ni quita las marcas invisibles que pueda llevar la propia imagen, y el original no se toca: sigue teniendo esos datos. Las fotos HEIC y HEIF se pueden usar desde Android 9, y las AVIF desde Android 12; las tres se guardan como JPEG y, si pasan de unos 24 megapíxeles, se reducen a ese tamaño.»
- `:62`, en «Tus derechos»: «Para borrar todo lo que guarda la app, desinstálala o borra sus datos desde los Ajustes de Android. Eso no borra los archivos que hayas guardado fuera de la app (las copias .sgbak, también las automáticas, las copias de fotos y las imágenes tachadas, y los archivos cifrados): están donde los guardaste, y los borras tú.»
- El resto de pasajes de la primera entrega no cambia. El correo de contacto de `:26` es del controlador (`c8a4d46`) y también va a la revisión.

**`docs/ficha-google-play.md`**
- `:35`: «Limpiar foto: quita la ubicación y otros datos ocultos del archivo antes de enviarla; la copia solo conserva la imagen, sus datos de color y, en las JPEG, la orientación. No cambia lo que se ve en ella. Las fotos HEIC y HEIF (desde Android 9) y AVIF (desde Android 12) se guardan como JPEG, reducidas si pasan de unos 24 megapíxeles.»
- `:46`: «Limpiar foto no cambia lo que se ve, y Tachar solo tapa lo que marcas tú. Ninguna cambia el original: comparte la copia.»
- La descripción completa tiene 3756 caracteres, por debajo del límite de 4000.

**`docs/PUBLICAR.md`**
- `:93`, al final de «Permisos»: «El APK lleva además `io.github.egt17baa.seguridad.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`, que declara la biblioteca `androidx.core`: es un permiso de firma que solo tiene la propia app y protege sus receptores internos, para que ninguna otra app les envíe avisos. No da acceso a nada del móvil.»

**`README.md`**
- `:15`: «…y, si se quiere, con la huella (Android Keystore). Desde la 1.3.0, al activar la huella la clave se crea en el chip de seguridad StrongBox si el móvil lo tiene, y si no o si falla, en la zona aislada del procesador (TEE). Quien ya tenía la huella activada en la 1.2.2 sigue con la clave en la TEE hasta que desactive la huella y vuelva a activarla (Ajustes dice dónde está).»
- `:31`: «…solo sale la copia que compartes. Limpiar foto no cambia lo que se ve, y Tachar solo tapa lo que marcas tú. Ninguna cambia el original: comparte la copia.»
- `:32`: «Limpiar foto: quita la ubicación y otros datos ocultos del archivo, y antes enseña lo que trae. La copia solo conserva la imagen, sus datos de color (como el perfil de color) y, en las JPEG, la orientación (el giro) de la foto; no quita las marcas invisibles que pueda llevar la propia imagen. Guarda o comparte una copia llamada `copia-de-foto.<ext>` (`jpg`, `png` o `webp`). Admite JPEG, PNG y WebP; HEIC y HEIF desde Android 9, y AVIF desde Android 12. Esas tres se guardan como JPEG y, si pasan de unos 24 megapíxeles, se reducen a ese tamaño.»
- `:33`: «…la copia (`imagen-tachada.jpg` o `imagen-tachada.png`) se crea de nuevo…»
- `:38`: «Además, la biblioteca `androidx.core` declara `io.github.egt17baa.seguridad.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`, un permiso de firma que solo tiene la propia app: protege los receptores internos que registren la app o sus bibliotecas, para que ninguna otra app les envíe avisos. No da acceso a nada del móvil.»
- `:230`: «1.3.0, Inspector de QR con un QR de enlace (por ejemplo, uno con `http://` y otro con un acortador): enseña la web en grande y sus avisos, no abre nada al tocarlo, y «Copiar» copia el enlace tal cual.»
- `:231`: «1.3.0, «PIN y códigos» con la bóveda abierta: generar un PIN de 6 cifras y copiarlo; escribir la web y guardar los códigos de recuperación con «Guardar en la bóveda como nota»; escribir dos preguntas, generar sus respuestas y pulsar «Guardar en la bóveda». En la bóveda aparecen las dos notas («Códigos de recuperación · …» y «Respuestas de seguridad · …»), con lo que se veía. Con la bóveda bloqueada, sale «Desbloquea la bóveda para guardarlo…».»

**Anexo**
- La cabecera nueva y el título «Seguridad 1.3.0: textos de las herramientas».
- La regla común sobre la negación, ampliada.
- Las dos secciones pendientes de revisión.
- «Cambios en los documentos», actualizada con los pasajes de arriba.

---

# Fix round 2

**Estado:** DONE. Commit `01f44fc`, «Seguridad 1.3.0: documentación y versión, revisión 2». Autor `Claude <noreply@anthropic.com>`. El mensaje termina solo con `Co-Authored-By`. No se ha hecho push.

## Cambios
1. **Anexo, `:136`.** «líneas cortas de de Copia lista» pasa a «líneas cortas de Copia lista».
2. **`deniedSafe`** (`ForbiddenWords.kt:13`). La negación tiene ahora un grupo para el «sea segur[oa]s?» final. `withoutDeniedSafe` (`:46`) borra solo ese grupo, así que un «seguro» entre las palabras del medio se sigue marcando. Fixtures nuevos (`SourceTextsTest.kt:102-104`) que tienen que fallar:
   - «No garantiza que el seguro sea seguro»;
   - «No garantiza que lo seguro sea seguro»;
   - «No garantiza que el seguro de hogar sea seguro».

   «No garantiza que un enlace sea seguro» se sigue aceptando.
3. **Escapes antes de normalizar.** El lexer ya decodificaba `\uXXXX`, `\t`, `\n`, `\r`, `\b` y `\$` antes de pasar el texto a `ForbiddenWords.normalise`. Lo hace `KotlinLexer.escape`, desde la entrega inicial. Lo dejan fijado unos fixtures nuevos (`SourceTextsTest.kt:164`, `escapedInvisibleOrLetterCharactersInASourceLiteralAreCaught`):
   - el texto fuente `"es segu­ro"` se lee como «es segu­ro», con un guion blando de verdad, y se marca;
   - `"es seguro"` se marca;
   - `"es de\tverdad"` y `"es de\nverdad"` se marcan;
   - una cadena raw con el guion blando literal se marca.
4. **KDoc de `problems`** (`ForbiddenWords.kt:31`). Dice que «garantiza» se busca como subcadena a propósito: «garantizar», «garantizado» o «garantizamos» también se marcan, porque es más estricto y falla de forma cerrada.

## Tests
- **RED:** «12 tests completed, 1 failed». Falla `theAllowedDenialsAreWholeWordsAndOnlyThose`, con «No garantiza que el seguro sea seguro» aceptado. Los fixtures de escapes ya pasaban antes del cambio, lo que confirma que el punto 3 ya estaba cubierto.
- **GREEN:** todo pasa.
- **Verificación completa** (`--offline --no-daemon --max-workers=1`):
  - `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets`: BUILD SUCCESSFUL, 965 tests, 0 fallos y 0 errores.
  - `gradle -p jvm-check test --rerun`: BUILD SUCCESSFUL, 965 PASSED y 0 FAILED.

## Revisión experto-seguridad-cripto
- Solo cambian código de test y documentación. No se toca la app, ni formatos, criptografía, red, permisos o dependencias.
- El control de palabras prohibidas es más estricto:
  - la excepción de la negación queda acotada a su «sea seguro» final (`ForbiddenWords.kt:13`, `:46-53`);
  - los escapes y los caracteres invisibles no sirven para saltarse la regla (`SourceTextsTest.kt:164`).

## Revisión Ingeniero Móvil Senior
- No hay cambios en Android, Kotlin de producción ni Compose. Lint y assembleDebug pasan sin avisos nuevos.

## Textos para el Abogado Digital (revisión final)
- No hay textos nuevos ni cambiados en la UI ni en los documentos públicos.
- El único cambio de texto es una errata en un encabezado interno del anexo (`docs/superpowers/specs/2026-10-27-seguridad-1.3.0-textos.md:136`): «Limpiar foto (títulos, botones, líneas cortas de Copia lista, errores)». No se muestra al usuario.
- La lista completa de textos para la revisión sigue siendo la de la entrega inicial y la de «Fix round 1».
