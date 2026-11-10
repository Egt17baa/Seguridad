# Clavero 1.4.0: cambios del dictamen del Abogado Digital (O-1 a O-3, R-1 a R-11)

**Rama:** `ccr-d1e75160-p5f4wc`, sobre `0899221`. **Dictamen:** `docs/superpowers/handoff/sdd-1.4.0/abogado-1.4.0.md` («se puede publicar, con cambios»). **Decisión:** R73 (`progress.md:321`): se aplican O-1 a O-3 y R-1 a R-11, cada uno contrastado por `experto-seguridad-cripto` y el Ingeniero Móvil Senior; R-1 decide R30; R-12, la fecha de la política, se pone en la rama justo antes de fusionar. **Commit:** uno, «1.4.0: cambios del Abogado Digital (O-1 a O-3, R-1 a R-11)». Sin subagentes.

Rutas de Kotlin relativas a `app/src/main/java/app/seguridad/`; las de los tests, a `app/src/test/java/app/seguridad/`. Las líneas son las del árbol de este commit.

## Cambios

Para cada uno, antes de aplicarlo comprobé tres cosas: que no rompe un formato, que no afirma nada falso sobre el código y que no cambia el comportamiento. Ninguno toca un formato ni el comportamiento: son literales de la interfaz, un comentario y documentos.

| Id | Dónde | Aplicado | Contraste con el código |
|---|---|---|---|
| **O-1** | `imports/ImportTexts.kt:280-285` | Tal cual | R58: con un acceso o una nota repetidos, el título y el usuario tienen la misma clave (`compareKey`; el título sin mayúsculas), y `replaceWith` conserva los de la bóveda (`imports/MergePlan.kt`). En un 2FA, que se reconoce por su clave, se toman los del archivo si son otros: lo dice la primera frase. R64: el emisor y la cuenta, igual. R63: el 2FA del archivo sustituye al de la bóveda con su clave, y el anterior solo queda en la copia de antes de importar, que se hace siempre antes de guardar. R71: un 2FA dentro de un acceso no ofrece «Reemplazar», así que «el 2FA anterior» es siempre uno que se cambia. Los ejemplos del paréntesis (mayúsculas, espacios, lo que no se ve) son casos de «escritos de otra forma», que es la regla exacta; las formas de compatibilidad de NFKC (R44) entran ahí también |
| **O-2** | `imports/ImportTexts.kt:33`; el comentario de `imports/ImportLimits.kt:11` | Tal cual, con U+00A0 en «20 000» (` ` en el código) | `IMPORT_MAX_ITEMS = 20_000` (`imports/ImportLimits.kt:12`), decisión (a) y R12: cuenta también los elementos que no entran, y es el máximo que se lee, no un tamaño de la copia (`BACKUP_MAX_ENTRY_BYTES` y `BACKUP_MAX_ENTRIES`, R14, son otra comprobación, `applyImport`). El texto nuevo es verdad en todos los casos |
| **O-3** | `docs/privacidad.html:37-38` | Tal cual | La lectura es en memoria y con el selector, sin enviar nada; la copia previa es una `seguridad-backup` cifrada con la maestra, con el prefijo `seguridad-antes-de-importar-` (`vault/AutoBackup.kt:99`, `:388-412`) en la carpeta de la copia automática, o donde elija el usuario con el diálogo, que propone ese mismo nombre (`importCopyFileName`); la rotación no la borra; al terminar se ofrece quitar el archivo exportado. «Comprobar una copia» escribe los adjuntos, cifrados, en `cacheDir/comprobar/` y la borra al terminar, al cancelar y al arrancar (`vault/BackupCheck.kt`, `SeguridadApp.kt:62-63`). Va detrás del párrafo de A6, que no cambia |
| R-1 | `docs/ficha-google-play.md:32`; `imports/ImportTextsTest.kt:137` | Tal cual; `LISTING_ALLOWED` pasa a `emptySet()` | El escáner lee `otpauth-migration://` y los `otpauth://` de cualquier app. La aserción «The exception is still there» se queda: con un conjunto vacío pasa siempre, y el resto del test sigue fijando que la ficha y las «Novedades» no nombran otra app. Decide R30 |
| R-2 | `docs/ficha-google-play.md:54` | Tal cual | La copia previa sale de la app a la carpeta automática o a donde elija el usuario. Siguen «carpeta que elijas» y «copias al portapapeles», que fija `PublishedTextsTest` |
| R-3 | `docs/privacidad.html:65` | Tal cual | Desinstalar no borra lo que está fuera del almacenamiento privado: las `.sgbak` de antes de importar y el archivo exportado de la otra app |
| R-4 | `docs/privacidad.html:35` y `:53` | Tal cual | Las dos frases siguen siendo verdad sin el nombre |
| R-5 | `imports/ImportTexts.kt:212-213`, `README.md:27` y `:340`, notas de GitHub del traspaso | Tal cual | `ImportTextsTest` sigue encontrando al menos 5 nombres en `IMPORT_FORMATS` («Google» sigue en la lista) y ninguno fuera de `ImportTexts.kt`. Partí la línea de `IMPORT_FORMATS` para que no pase de 120 columnas |
| R-6 | `README.md:175` | Tal cual | Comprobado contra `ImportTextsTest.ALLOWED` (`:45-53`): los tres textos de Google Authenticator de la 1.1, «la copia de Google» y las direcciones de las licencias (Guava, JetBrains, JSpecify), y contra las etiquetas de origen de `ImportTexts.kt:12-18` |
| R-7 | Traspaso, notas de GitHub (`:68`, `:72`) | Tal cual | El botón es `PASSWORD_CHANGE_REPLACE_COPIES` (`tools/ToolTexts.kt:507`); R63 como en O-1; R-5. Las «Novedades» no cambian |
| R-8 | `docs/auditorias/2026-11-08-ataque-1.4.0.md:12` | Tal cual; además, la fecha del cuadro de estado pasa a 10-11-2026 y se quita una línea en blanco del cuadro | R65 cerró el segundo ataque; la auditoría (§5.3) dice lo mismo |
| R-9 | `docs/auditorias/2026-11-08-auditoria-1.4.0.md:84` | Tal cual | La auditoría ya lo dice así en §4 y en su revisión de experto-seguridad-cripto |
| R-10 | `vault/BackupCheckTexts.kt:77` | Tal cual | Una derivación Argon2id empezada termina por dentro con el cerrojo cogido (R38, `vault/BackupCheck.kt`); el resultado nunca se muestra |
| R-11 | README P-27 (`:366`) y `.maestro/README.md:88` | Tal cual, en toda la línea de P-27 y su fila | Ningún flujo ni dato de `.maestro/` usa `banco.es` (`grep -rn "banco\.es" .maestro/` solo encontraba las filas de P-9 y P-27 del README de Maestro; P-9 no se pega en un navegador y se queda). Los tests unitarios no se tocan |
| R-12 | `docs/privacidad.html:22` | **No se aplica ahora** (R73) | La pone el controlador en la rama justo antes de fusionar, con la forma «D de <mes> de AAAA.» que lee `FinalLegalReviewTest` |

**Ajustes del dictamen:** ninguno de fondo. Solo, en R-8, la fecha del cuadro de estado del informe del ataque y quitar una línea en blanco que separaba el último punto del cuadro, para que se lea como una sola lista.

**Tests nuevos o cambiados:**
- `imports/ImportTextsTest.theReplaceNoteAndTheItemCapSayWhatTheCodeDoes` (`:291`), nuevo: fija O-1 y O-2 palabra por palabra, y que `IMPORT_MAX_ITEMS` es 20 000. Es lo que el dictamen recomendaba («conviene fijarlo»);
- `imports/ImportTextsTest.LISTING_ALLOWED` (`:137`), vacío (R-1).

`ImportTextsTest`, `FinalLegalReviewTest`, `PublishedTextsTest`, `SourceTextsTest` y `AppNameTest` siguen en verde sin otro cambio: ninguno fijaba los literales que cambian. Los textos nuevos no tienen palabras prohibidas (`ForbiddenWords`, y la búsqueda de las de `CLAUDE.md` en todas las líneas añadidas).

**Documentos de cierre:**
- la auditoría, §11.1 y su línea en §1.3, el cuadro de estado, §5.3 (`IMPORT_REPLACE_NOTE` cambia), §7 y §12: ya no queda ningún «[POR LLENAR]»; la fecha de la política sigue pendiente, como dice R73;
- `CLAUDE.md`: la fila del Abogado, «Hecha», y la regla de los nombres de otras apps (R30, decidida por R-1);
- el traspaso: R30 decidida por R-1, el estado de la lista de Claude y el paso de la fecha de la política (R73).

## Puerta

Con el *wrapper*, `export ANDROID_HOME=/opt/android-sdk` y `--max-workers=1`:
- `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets --no-daemon --max-workers=1`, repetida con `--rerun-tasks` después de todos los documentos: **BUILD SUCCESSFUL**, con **1 900 tests, 0 fallos y 0 omitidos** (1 899 + 1); lint con 0 errores y 19 avisos, todos de las clases de siempre (`GradleDependency`, `NewerVersionAvailable`, `AndroidGradlePluginVersion`, `OldTargetApi`, `ObsoleteSdkInt`, `DiscouragedApi`), en archivos que este commit no toca;
- `./gradlew -p jvm-check test --no-daemon --max-workers=1`: **BUILD SUCCESSFUL**, con **1 895 tests, 0 fallos y 0 omitidos** (1 894 + 1);
- ningún aviso `w:` del compilador;
- `bash scripts/check-manifest.sh app/build/outputs/apk/debug/app-debug.apk`: **OK**.

## Revisión experto-seguridad-cripto

- **Ninguna afirmación falsa sobre la seguridad.** O-1 dice dónde queda el 2FA anterior (solo en la copia de antes de importar, R63), lo que reduce el riesgo de que el usuario se quede fuera de una cuenta y no da a un atacante nada que no sepa. O-3 describe una copia cifrada con la maestra y una carpeta temporal con adjuntos cifrados, sin prometer un borrado físico ni nada absoluto (`PublishedTextsTest`). R-10 deja de dar a entender que una derivación se corta (R38).
- **Ningún formato cambia:** no se toca `seguridad-backup` ni el prefijo `seguridad-antes-de-importar-` (que fija `AppNameTest`), ni ninguna cabecera.
- **R-11** evita que una prueba conecte con un dominio real de un tercero; `banco.example` es un dominio reservado (RFC 2606, RFC 6761).
- **Sin datos personales:** ningún correo ni enlace de sesión en lo cambiado.

## Revisión Ingeniero Móvil Senior

- **Interfaz:** O-1 tiene unas 25 palabras más, en `bodySmall` dentro de un `LazyColumn` (`ui/screens/ImportScreen.kt`), y se parte en líneas sin problema; O-2 sale en el `ErrorText` del primer paso, y con U+00A0 la cifra no se parte. R-10 cambia una línea de «Comprobar una copia». Ningún estado ni comportamiento nuevo.
- **Kotlin:** `IMPORT_TOO_MANY_ITEMS` usa ` ` en el código, para que el espacio de no separación se vea al leerlo. `IMPORT_FORMATS` sigue siendo una constante en tiempo de compilación.
- **Documentos:** la política, la ficha y el README no van en el APK; GitHub Pages publica la política al fusionar en `main`.

## Textos para el Abogado Digital (revisión final)

- **Aplicados tal como los escribió el dictamen:** O-1 (`IMPORT_REPLACE_NOTE`), O-2 (`IMPORT_TOO_MANY_ITEMS`), O-3 (`docs/privacidad.html:37-38`), R-1 y R-2 (`docs/ficha-google-play.md:32` y `:54`), R-3 y R-4 (`docs/privacidad.html:65`, `:35` y `:53`), R-5 (`IMPORT_FORMATS`, README `:27` y `:340`, notas de GitHub), R-6 (README `:175`), R-7 (notas de GitHub del traspaso), R-8 (informe del ataque `:12`), R-9 (auditoría `:84`), R-10 (`BACKUP_CHECK_STOPS_NOTE`) y R-11 (README P-27 y `.maestro/README.md:88`).
- **Pendiente:** R-12, la fecha de la política, en la rama justo antes de fusionar (R73).
- **Ningún texto se ha cambiado respecto a lo que propuso el dictamen.**
