# Tarea 12 (1.4.0): revisión — versión 1.4.0 y los documentos

**Revisor:** tarea 12, sobre `ef2300e` (base `57865fc`), rama `ccr-d1e75160-p5f4wc` (ya en `origin`). Solo revisión: nada editado ni confirmado salvo este archivo.
**Resultado:** **Con cambios** — 0 críticos, 5 importantes, 16 menores. Todos son de documentos salvo I-3, que también toca dos textos de la interfaz (van a la ronda final de arreglos).

Leído: `CLAUDE.md` (el de este commit), `experto-seguridad-cripto/SKILL.md`, `desarrollo-movil-senior`, el plan («Global Constraints» y «Task 12»), el diseño (con la nota de corrección del 8-11-2026), el registro entero (decisiones (a) a (f), R1 a R30 y cada línea «phone tests for T12 README»), `task-12-report.md`, `task-10-report.md` («Pendiente de confirmar») y el diff `57865fc..ef2300e`.

## Spec Compliance

| Paso del plan (tarea 12) | Estado |
|---|---|
| `versionName = "1.4.0"` (`app/build.gradle.kts:35`) | ✅ Ningún test fija la versión; Acerca de lo enseña (`ui/screens/SettingsScreen.kt:514`) |
| README: funciones, formatos con la línea de marcas, licencias | ✅ con I-3, I-4, M-8, M-9 |
| README: pruebas en el móvil (paso 1: cada formato real, copia previa en la carpeta y con el diálogo, quitar el archivo de Descargas y de Drive, Comprobar una copia, las tres herramientas, la huella tras la tarea 1, TalkBack al importar) | ✅ P-1, P-2, P-6, P-7, P-10, P-14, P-15 a P-17, P-18, P-13 |
| Cada línea «phone tests for T12 README» del registro (tareas 1 a 11, la que sustituye a la de la tarea 9, la tarea 10, la 11 y lo que añadió su revisor) | ✅ todas tienen su P-n (comprobado línea a línea: T1→P-18, T2→P-15, T3→P-16, T4→P-17, T5→P-14, T6/T7→P-1/P-12, T8→P-6/P-7/P-8/P-9/P-11, T9 y su sustituta→P-2/P-4/P-8/P-9/P-10/P-12/P-13, T10→P-2/P-3/P-4, T11 y revisor→P-19); pero P-4 y P-12 esperan un resultado que el código no da (I-1, I-2), P-11 está en mal orden (M-10) y P-17 pierde «time» (M-11) |
| «Pendiente de confirmar» de `task-10-report.md:79-98` | ✅ P-3 nombra cada punto (Bitwarden 3, Aegis 4, 2FAS 6) |
| `CLAUDE.md` «Estado actual» | ✅ con M-1 a M-7 |
| Ficha sin nombres ajenos | ✅ lo nuevo; la línea de la 1.1 con «Google Authenticator» (`docs/ficha-google-play.md:32`) queda para el Abogado (R30). I-5 |
| Política solo si cambia lo tratado | ✅ cambia solo la frase de A6 (R15), con su pin; la fecha sigue «2 de noviembre de 2026». M-16 |
| Auditoría y traspaso creados | ✅ con M-6, M-7, M-13, M-14 |
| Paso 2: `PublishedTextsTest`, `AppNameTest`, `FinalLegalReviewTest` | ✅ los volví a ejecutar con `--rerun` (más `SourceTextsTest`, `ImportTextsTest` y `ToolTextsTest`): 7 + 8 + 14 + 12 + 13 + 22 = 76 tests, 0 fallos |
| Paso 3: commit y push | ✅ `ef2300e` está en `origin/ccr-d1e75160-p5f4wc` (el informe dice «sin subir»; el controlador lo subió con `2173da7`) |

## Comprobación de veracidad (archivo:línea)

Lo que dicen los documentos, contra el código:

- **Formatos** (README:26-31; notas de GitHub, traspaso:57): `readImport` prueba CSV, JSON que empieza por `{` y listas `otpauth://`, por el contenido (`imports/Detect.kt:18-22,40-54`); `.zip` y HTML antes de leer (`:43-44`). CSV de Chromium `name,url,…,note`, Firefox por `httprealm/formactionorigin/guid`, Apple por `title`, Bitwarden por nombres de columna con `folder` o `collections` (`imports/CsvFormats.kt:8-17,32-33,60-79`); `,` o `;` (`imports/Csv.kt:5,48-49`). JSON: Bitwarden por `encrypted`/`items`, Aegis por `db` objeto o cadena, 2FAS por `services`/`servicesEncrypted` (`imports/ImportJson.kt:193-228`); PBKDF2 y Argon2id de Bitwarden (`imports/BitwardenCrypto.kt:39-40`), scrypt de Aegis (`imports/ImportKdf.kt:43-46`). ✅
- **Topes** (README:35, auditoría §4): 16 MiB (`imports/ImportLimits.kt:9`), 20 000 (`:12`), campos 1024/1024/4096/4096/64 KiB (`:15-19`), 64 columnas y 500 000 celdas (`:25,31`), 64 niveles y 48 MiB de árbol (`imports/ImportJson.kt:22,72,79-81`), 8 MiB cifrados (`imports/ImportKdf.kt:55`), 100 etiquetas nuevas (`imports/MergePlan.kt:110`). ✅
- **KDF** (auditoría §4): PBKDF2 5 000 a 2 000 000 (`imports/ImportKdf.kt:39-40,57-60`), scrypt N 2¹⁴ a 2²⁰, r ≤ 16, p ≤ 4, N < 2^(16·r) y memoria con `checkSane` (`:43-46,69-79`), Argon2id de Bitwarden por `checkSane` (`:82-97`). HMAC de Bitwarden en tiempo constante (`imports/BitwardenCrypto.kt:151`). ✅
- **Duplicados**: «Saltar» por defecto, «Conservar los dos», «Reemplazar» con la contraseña anterior al historial (`imports/MergePlan.kt:23-24,194-205,230`; textos `imports/ImportTexts.kt:274-280`). Etiquetas de origen (`imports/ImportTexts.kt:12-18`). Motivos de «no entran» (`imports/ImportModel.kt:32-40`). ✅
- **Copia previa**: `seguridad-antes-de-importar-AAAAMMDD-HHMMSS.sgbak` en la carpeta automática, cifrada con la maestra, nunca rotada, apuntada para A6; sin carpeta, nula y se pide dónde (`vault/AutoBackup.kt:99-105,388-433`). Sin copia no se importa (`IMPORT_COPY_CANCELLED`, `imports/ImportTexts.kt:334`). ✅
- **A6** (política:36, FinalLegalReviewTest:67-79, notas de GitHub): `PASSWORD_CHANGE_REPLACE_COPIES` (`tools/ToolTexts.kt:507`) y la copia hecha con el diálogo fuera de la lista (`vault/AutoBackup.kt:394`; `importCopyWhereText`). La frase de la política dice lo mismo que `AUTO_BACKUP_REPLACED`. ✅
- **Comprobar una copia** (README:38): `cacheDir/comprobar/` (`vault/BackupCheck.kt:26`, `ui/screens/BackupCheckScreen.kt:179`), una carpeta por ejecución borrada en un `finally` no cancelable (`vault/BackupCheck.kt:79-118,125-139`) y al arrancar (`SeguridadApp.kt:63`); tamaño y SHA-256 de cada adjunto (`vault/BackupCheck.kt:186-192`). ✅ salvo «dice si se podría importar» (I-4).
- **Sin red al importar**: ningún archivo de `imports/`, `vault/BackupCheck*.kt`, `tools/hashlab|classic|slip39/` importa OkHttp, `HttpURLConnection` ni sockets; solo `java.net.URI` y `URLDecoder` para analizar texto (`imports/CsvFormats.kt:4`, `imports/OtpAuthList.kt:12-13`). La única red sigue en `net/PwnedPasswords.kt:24`. ✅
- **SLIP-0039**: README:50 y :175, `LicensesScreen.kt:38`, `NOTICE:14-16`, `assets/licenses/slip39.txt:1-3`: MIT, Copyright 2019 SatoshiLabs, solo datos. Ancla `#licencias` existe (README:163). ✅ salvo M-9.
- **Bouncy Castle** (`ui/screens/LicensesScreen.kt:35`): scrypt (`imports/ImportKdf.kt`, `SCrypt`), SHA-3, BLAKE2b, BLAKE3 (`tools/hashlab/HashLab.kt`, `SHA3Digest`, `Blake2bDigest`, `Blake3Digest`) son de BC. La lista nunca fue exhaustiva (PBKDF2 y HKDF ya venían de BC en la 1.3.1, `crypto/Crypto.kt`); no es un hallazgo. ✅
- **Huella** (README:19): `KeyOpsQueue` en un dispatcher de un solo hilo (`android/BiometricVault.kt:183`), `disable` `suspend` (`:166`), `cancelEnable` (`tools/KeyOpsQueue.kt:49`). ✅
- **Auditoría §8**: 50 commits de `Claude <noreply@anthropic.com>` en `7c8dea7..d4b8c9d`, ninguno con `Claude-Session` ni claude.ai (también `57865fc..2173da7`); `git diff 7c8dea7..d4b8c9d -- app/src/main/AndroidManifest.xml app/src/main/res` vacío; `FLAG_SECURE` en `android/SecureWindow.kt:24`. Las líneas citadas de `KeyOpsQueue`, `BiometricVault`, `VaultStore.kt:684`, `ImportScreen.kt:291`, `ImportModel.kt:106` y `HashLab.kt:98` son las que dice. ✅
- **Palabras prohibidas** (`app/src/test/java/app/seguridad/tools/ForbiddenWords.kt:5-7`) en todas las líneas añadidas del diff: ninguna; los cuatro «garantiza» van tras «no». Tampoco «a prueba de cuánticos» ni «como Signal». ✅
- **Correos y secretos**: solo `seguridad.egt17baa@gmail.com` (política), `noreply@anthropic.com` (auditoría, traspaso) y el ejemplo `clave@ejemplo.es` (README:289). Ningún correo personal, token ni clave. ✅

Comprobación ejecutada para I-1 e I-2 (jshell con las clases de `jvm-check/build`, `readImport(bytes, null)`):
```
arr.json (P-12 tal cual)            -> Failed(Clavero no reconoce este archivo.)
obj.json ({"a":[[1],…×700000]})     -> Failed(El archivo tiene demasiados datos. Clavero no lo lee.)
foto.jpg (cabecera JPEG)            -> Failed(El archivo está dañado o no es texto.)
doc.pdf  (%PDF-1.7 + línea binaria) -> Failed(El archivo está dañado o no es texto.)
```

## Strengths

- Las 20 pruebas P-n cubren todas las líneas del registro y la lista «Pendiente de confirmar», con números para citarlas, y separan lo que el dueño prueba antes (P-4 a P-20) y después de publicar (P-1 a P-3) en el traspaso y la auditoría.
- La lista consolidada de textos para el Abogado (`task-12-report.md`) recoge cada tarea en su versión final, con las preguntas abiertas (R30, la copia previa en la política, el User-Agent) y la base jurídica del uso descriptivo de marcas.
- `CLAUDE.md` mantiene todas las reglas vigentes de la 1.3.x (nada vivo se perdió: comprobé cada línea quitada), marca como hechas las dos que la 1.4.0 cierra (R8 de Kotlin y `BiometricVault`) y usa `./gradlew -p jvm-check` en «Compilar y probar» (R29, `CLAUDE.md:160`).
- La auditoría en borrador es honesta sobre lo que falta (**[POR LLENAR]** en §2, §5, §8, §9, §11.1) y sus citas `archivo:línea` son correctas.
- Las notas de la versión no dicen que nada sea fuerte, dicen lo que no protege cada pieza, llevan la línea de marcas y caben en Play (372 caracteres).

## Issues

### Critical

Ninguno.

### Important

**I-1. P-4 espera un texto que una foto o un PDF no dan** (`README.md:345`). Dice «una foto o un PDF dicen «Clavero no reconoce este archivo.»». Una JPEG, una PNG o un PDF con su línea binaria no son UTF-8 válido, así que `decodeText` devuelve null y sale `IMPORT_NOT_TEXT` «El archivo está dañado o no es texto.» (`imports/Detect.kt:47`, `imports/TextDecode.kt:19-27`, `imports/ImportTexts.kt:27`; comprobado arriba). El dueño anotaría un fallo que no lo es.
*Arreglo:* «una foto o un PDF dicen «El archivo está dañado o no es texto.», y un `.txt` con una frase cualquiera, «Clavero no reconoce este archivo.»».

**I-2. P-12 no prueba el tope del árbol JSON** (`README.md:353`). El comando escribe una lista (`[[1],[1],…]`), y `readJsonImport` solo mira un texto que empieza por `{` (`imports/ImportJson.kt:196`): el archivo sale como «Clavero no reconoce este archivo.» sin pasar por `checkJsonShape`. La línea de la tarea 7 del registro pedía justo eso («a crafted ~3 MB JSON of tiny arrays gives «demasiados datos»»), y el «por ejemplo» lo esconde.
*Arreglo:* `python3 -c 'print("{\"a\":[" + ",".join(["[1]"] * 700000) + "]}")' > listas.json` y «dice «El archivo tiene demasiados datos. Clavero no lo lee.» y la app no se cierra» (comprobado arriba), sin «por ejemplo» (puntos 5 de las inquietudes del informe).

**I-3. «Está sin cifrar» es falso para las exportaciones protegidas con contraseña, que la 1.4.0 ya lee** (regla de veracidad, como O-1 de la 1.3.1).
- Documentos de esta tarea: `README.md:35` («quitar el archivo exportado, que está sin cifrar»), `docs/ficha-google-play.md:61` («El archivo que exportas de otra app para importarlo está sin cifrar»), las notas de GitHub (traspaso:57, «que está sin cifrar») y la auditoría (§1.2:36 y §6, «está en claro»).
- Su origen está en la interfaz: `IMPORT_NOT_PROTECTED` (`imports/ImportTexts.kt:217-219`, en todos los pasos, `ui/screens/ImportScreen.kt:545`) e `IMPORT_REMOVE_EXPLAIN` (`imports/ImportTexts.kt:391-394`, `ui/screens/ImportScreen.kt:621`). Se escribieron en la tarea 9, cuando una exportación cifrada no se leía (R13a). Desde la tarea 10 salen igual tras importar un «.json (Encrypted)» de Bitwarden, un Aegis cifrado o un 2FAS con contraseña, cuyo contenido sí está cifrado (de 2FAS solo `groups` va en claro, `task-10-report.md:97`).
*Arreglo:* en los documentos, «que puede estar sin cifrar (lo está si no lo exportaste con contraseña)»; en la ficha, «El archivo que exportas de otra app suele estar sin cifrar, salvo si lo proteges con contraseña.». En la ronda final de arreglos, los dos textos de la interfaz dicen lo mismo (o eligen la frase según `session` haya pedido contraseña), con su test, y van a la lista del Abogado.

**I-4. README dice que «Comprobar una copia» «dice si se podría importar»** (`README.md:38`), y la pantalla dice lo contrario: «Lo que no comprueba… No comprueba que quede espacio en este móvil para importarla. Tampoco prueba lo que solo hace la importación…» (`BACKUP_CHECK_NOT_PROVEN`; el KDoc de `vault/BackupCheck.kt:30-41` también lo excluye). Es lo que R6 prohibió: que los textos digan más de lo que hace.
*Arreglo:* «Abre una copia `.sgbak` con su contraseña, sin importarla, y dice si la contraseña la abre y sus entradas se leen: la versión, cuántas entradas tiene de cada tipo y si cada adjunto está y coincide con el tamaño y el SHA-256 que guarda su entrada. No prueba lo que solo hace la importación (el espacio libre ni volver a cifrar su clave con el coste de Argon2id de este móvil).»

**I-5. La ficha y las «Novedades» prometen las exportaciones de cualquier gestor** (`docs/ficha-google-play.md:28`, traspaso:69: «las exportaciones de otros gestores de contraseñas y de otras apps de 2FA»). Sin nombres (R2), la frase se lee como «de cualquiera», y Clavero solo lee Bitwarden, los CSV de navegadores y Apple, Aegis y 2FAS: quien use otro gestor instalaría la app por una función que no tiene. Es el texto de la tienda, donde una función anunciada que no existe es la queja más fácil.
*Arreglo:* «Importa los archivos CSV de los navegadores más usados y las exportaciones de algunos gestores de contraseñas y apps de 2FA (la lista está en la app y en el código fuente), …»; en «Novedades», «de algunos gestores de contraseñas y apps de 2FA». Al Abogado.

### Minor

**M-1. `CLAUDE.md`, orden y forma.** «Decisiones de la 1.4.0» (`CLAUDE.md:89-110`) queda en medio de las de la 1.3.x: entre «Decisiones del dueño… de la 1.3.x» (`:62`) y «Más decisiones del 1-11-2026» (`:112`), que son de la 1.3.x. Por eso `:131` dice «(tarea 1, abajo)» de algo que está arriba. Además hay líneas en blanco dobles en `:37-38`, `:87-88` y `:117-118`.
*Arreglo:* mover el bloque de la 1.4.0 delante de `:62` (o detrás de `:132`), corregir «abajo» y dejar una sola línea en blanco.

**M-2. `CLAUDE.md:135`, pendiente 1:** «hacer las pruebas P-1 a P-20» contradice el pendiente 2 (`:136`), el traspaso §3, paso 2, y la auditoría §10, donde P-1 a P-3 se hacen después de publicar (decisión (e)).
*Arreglo:* «hacer las pruebas P-4 a P-20».

**M-3. `CLAUDE.md:142`, pendiente 8:** al reescribirlo se perdió el verbo: «(la 1.3.1: el AAB … #94)» ya no dice que hay que subirlo, y no nombra el AAB de la 1.4.0.
*Arreglo:* «**Las respuestas de Play Console** y subir el AAB `Clavero-google-play-aab` de la ejecución de la etiqueta (de la 1.3.1, la #94; de la 1.4.0, la de `v1.4.0`), si publica en Google Play.»

**M-4. Faltan reglas vivas en los resúmenes.** R30 no está en `CLAUDE.md`, ni en la auditoría §3 (`:81-107`, acaba en R29), ni en el traspaso §2 (`:18`, «reglas R1 a R29»). `CLAUDE.md:96` dice «nunca en la ficha de Play» sin la excepción de R30. `CLAUDE.md` tampoco resume R4 (SLIP-0039: exponente de 0 a 8, recomponer hasta 64 bytes), R5/R6 («Comprobar» comparte las comprobaciones de «Importar copia» y llama a `touch()`, que puede subir la marca de A11), R23 (la Argon2id de Bitwarden con su propia llamada a BC, sal de 32 bytes) ni R25 (dónde vive «Pendiente de confirmar»), y las cuatro siguen decidiendo el código.
*Arreglo:* una línea por cada una en `CLAUDE.md` (el texto puede copiar el de la auditoría §3), R30 en la auditoría §3 y «R1 a R30» en el traspaso.

**M-5. Las notas de GitHub nombran otras apps, y la regla no lo permite.** `CLAUDE.md:96,153` y las «Global Constraints» del plan solo dejan los nombres en la pantalla de importar y en el README; el borrador de GitHub (traspaso:57) los nombra. Los documentos técnicos (auditoría, traspaso, `CLAUDE.md`) también, sin que la regla diga si pueden.
*Arreglo:* que el controlador decida y lo apunte (regla nueva). O la regla añade «y en las notas de la versión de GitHub, con la línea de marcas» y aclara que vale para el texto visible de la app y los textos publicados (ficha, «Novedades»), no para los documentos técnicos; o la nota de GitHub quita los nombres y remite al README.

**M-6. El orden del ataque no es el que dicen los documentos.** El registro (última línea) lanza el agente atacante en paralelo con esta revisión, antes de la ronda final y de la revisión de la rama. En cambio, la tabla de `CLAUDE.md:55-57`, la decisión (d) (`CLAUDE.md:93`), el traspaso §3 (1 a 3) y la auditoría §1.3, punto 2, lo ponen después de la revisión de la rama. El traspaso §4 (`:76`) dice además «antes de las revisiones finales», que contradice su propio §3.
*Arreglo:* una línea «Ruling» que diga que (f) adelanta el ataque (en paralelo desde el 8-11-2026), y los cuatro sitios alineados con ella. Si no, el traspaso §4 dice «después de la revisión de la rama y su ronda, y antes de `code-review`, `security-review`, la auditoría completa y el Abogado».

**M-7. «Se revisa con la revisión final»** (`CLAUDE.md:54`, auditoría §1.3, fila 12 (`:61`), traspaso §1 (`:10`)): la tarea 12 tiene su propia revisión (este archivo).
*Arreglo:* «Terminada y revisada (`task-12-review.md`)» cuando se cierre su ronda.

**M-8. README, cuándo se borra el estado** (`README.md:35`, importar; `:52`, las tres herramientas): «al estar fuera más tiempo que el bloqueo». El código y la pantalla dicen «si estás fuera de esta pantalla más tiempo que el bloqueo de la bóveda (1 minuto como mínimo)» (`imports/ImportTexts.kt:221-224`, `tools/ToolTexts.kt:628-630`). Con «Al salir» (`settings/Settings.kt:10`, 0 ms) y sin bóveda, el README promete que se borra al momento, y en realidad tarda 1 minuto.
*Arreglo:* «… o al estar fuera de la pantalla más tiempo que el bloqueo de la bóveda (1 minuto como mínimo)» en los dos sitios.

**M-9. README, SLIP-0039** (`README.md:50`): «un secreto en hex (de 16 a 32 bytes)», pero el código pide un número par de bytes (`tools/slip39/SecretShareInput.kt:35`; `SHARE_HEX_LABEL`, `tools/slip39/SecretShareTexts.kt:33`).
*Arreglo:* «(de 16 a 32 bytes, en número par)».

**M-10. P-11, en mal orden** (`README.md:352`). Primero se cambia la maestra y luego «La huella sigue funcionando después de importar». Pero cambiar la maestra desactiva la huella (`ui/screens/VaultBackup.kt:128-131`, CRY-A02), así que en ese orden la prueba falla siempre.
*Arreglo:* «Antes de cambiar la maestra: la huella sigue funcionando después de importar (importar de otra app no la desactiva). … Después del cambio, la huella queda desactivada, como siempre al cambiar la maestra.»

**M-11. P-17** (`README.md:358`): la línea de la tarea 4 del registro dice «time with exponent 8 and «Cancelar»», y P-17 solo pide pulsar «Cancelar».
*Arreglo:* «Repartir con exponente 8: anota cuánto tarda; repetirlo y pulsar «Cancelar».»

**M-12. P-6** (`README.md:347`): «Abrirla con «Comprobar una copia» …: no tiene lo importado». «Comprobar» solo enseña recuentos (`vault/BackupCheck.kt:100-110`), no las entradas.
*Arreglo:* «… sus recuentos de entradas son los de antes de importar».

**M-13. Traspaso §1** (`:9`): «La sesión siguiente se cortó antes del informe de la tarea 10». Según el registro, se cortó la sesión que hizo la tarea 10, no la siguiente.
*Arreglo:* «Esa sesión se cortó antes del informe de la tarea 10».

**M-14. Auditoría §1.2** (`:36`): «Lo que no se protege, y la pantalla lo dice: …; las cadenas de Kotlin no se pueden poner a cero». La pantalla no dice lo de las cadenas.
*Arreglo:* «… y no garantiza el borrado físico (la pantalla lo dice: `IMPORT_NOT_PROTECTED`, `IMPORT_REMOVE_EXPLAIN`). Además, las cadenas de Kotlin no se pueden poner a cero …».

**M-15. `README.md:111-112`** ofrece `gradle -p jvm-check …` «con un Gradle 9.6.x instalado», y `CLAUDE.md:109,160` dice «siempre con el wrapper… nunca un gradle instalado» (R29). Viene de la tarea 11 y no está en este diff, pero ahora los dos se contradicen.
*Arreglo:* quitar las dos líneas del README, o dejar R29 en «nunca un Gradle 8 instalado» en `CLAUDE.md` y en la auditoría §3.

**M-16. La política y la copia de antes de importar** (pregunta de R30). La política es veraz sin la frase propuesta: la copia está cifrada y queda donde la app guarda sus copias. Pero no está completa: «las de antes de importar» solo sale en la frase de A6 (`docs/privacidad.html:36`), sin que nada antes diga qué son. Además, «Fuera de la bóveda solo quedan…» (`:30`) y «Tus derechos» («las copias .sgbak, también las automáticas», `:63`) no las nombran.
*Arreglo (propuesta para el Abogado):* detrás de `:35`: «Antes de importar de otra app, la app hace una copia cifrada de la bóveda con tu contraseña maestra, en la carpeta de la copia automática o donde elijas; la rotación de las copias automáticas no la borra.» (verdad según `vault/AutoBackup.kt:388-394`). En `:63`: «(las copias .sgbak, también las automáticas y las de antes de importar, …)». La copia temporal de «Comprobar una copia» está cifrada y en el almacenamiento privado, igual que la de «Importar copia» desde la 1.1, que la política nunca nombró: no hace falta añadirla, pero va en la lista como pregunta.

## Revisión experto-seguridad-cripto

- Nada en los documentos promete más protección que el código. Las únicas afirmaciones falsas exageran el riesgo (I-3) o la cobertura (I-4, I-5), no la protección. Las notas no dicen «fuerte» ni «seguro», y cada herramienta dice lo que no protege (`README.md:48-50`; ficha `:46-48`; `SHARE_WARNING` y `CLASSIC_NOTICE`).
- Los topes de las KDF de los archivos importados son los que dicen la auditoría y el README (`imports/ImportKdf.kt:39-46,57-97`), y el HMAC de Bitwarden se compara en tiempo constante (`imports/BitwardenCrypto.kt:151`). La comparación de huellas usa `MessageDigest.isEqual` (`tools/hashlab/HashLab.kt:98`).
- I-2: tal como está, la prueba en el móvil de la decisión (a) para JSON no llega al estimador (`imports/ImportJson.kt:196` corta antes). El archivo corregido sí lo ejercita.
- La política dice exactamente lo que borra A6 (`docs/privacidad.html:36`, fijado en `FinalLegalReviewTest.kt:67-79`, con el `assertFalse` de la frase anterior).
- La nota de Bouncy Castle (`ui/screens/LicensesScreen.kt:35`) nombra primitivas que sí vienen de BC. Sin criptografía propia nueva en esta tarea.
- Ningún formato de Clavero cambia. La copia previa es una `seguridad-backup` normal (`vault/AutoBackup.kt:388-392`).

## Revisión Ingeniero Móvil Senior

- `versionName = "1.4.0"` (`app/build.gradle.kts:35`). `versionCode` sigue saliendo de `-PversionCode` (`:34`), y Acerca de lo enseña (`ui/screens/SettingsScreen.kt:514`).
- Las pruebas en el móvil cubren lo que la CI no ve (R8 nuevo en P-19, huella con giros en P-18, proveedores en P-7 y P-10, TalkBack en P-13 y P-17, muerte del proceso en P-14). Pero P-4, P-11 y P-12 darían falsos fallos (I-1, M-10, I-2) y conviene arreglarlas antes de dárselas al dueño.
- El estado secreto vive en ViewModels: lo dicen el README y la auditoría, y lo confirma `ui/screens/ImportScreen.kt` (sin `rememberSaveable` de lo leído). El borrado por tiempo fuera es `max(bloqueo, 60 s)` (M-8).
- Los comandos de `CLAUDE.md:159-160` usan el wrapper en los dos proyectos (R29). `README.md:112` se aparta (M-15).
- Sin cambios de manifiesto, recursos ni dependencias en `ef2300e`. Solo cambian un literal de Licencias y un pin de test.

## Textos para el Abogado Digital (revisión final)

Además de la lista consolidada de `task-12-report.md` («Textos para el Abogado Digital»), y en la forma que tengan tras esta ronda:
1. Los textos de I-3: `IMPORT_NOT_PROTECTED`, `IMPORT_REMOVE_EXPLAIN`, `README.md:35`, `docs/ficha-google-play.md:61` y la nota de GitHub.
2. Los de I-5: la ficha (`docs/ficha-google-play.md:28`) y las «Novedades» de Play.
3. La frase de M-16 para la política y la línea de «Tus derechos». También, si se quiere, «Al importar de otra app, el archivo que eliges se lee solo en el móvil y no se envía a ningún sitio.».
4. Nombres ajenos que ya estaban en textos publicados, con el mismo trato que R30: `docs/privacidad.html:51` («de exportación de Google Authenticator», Permisos) y `:35` («por ejemplo, Google Drive»), además de `docs/ficha-google-play.md:32`.
5. La decisión de M-5: si las notas de GitHub pueden nombrar otras apps.
6. Para saber: la política se publica al fusionar en `main` (GitHub Pages) con la frase de A6 ya cambiada y la fecha «2 de noviembre de 2026» hasta que el dueño la ponga al publicar. Es lo mismo que en la 1.3.1, pero «Cambios» dice «se publicará aquí con su nueva fecha».

## Assessment

**Con cambios.** La versión, la estructura de los documentos y la cobertura de las pruebas están bien, y casi todo lo que afirman se sostiene en el código. Antes de la revisión final hay que arreglar los cinco importantes: dos pruebas del móvil esperan un resultado que el código no da (I-1, I-2), «sin cifrar» es falso para las exportaciones con contraseña, también en la interfaz (I-3), «Comprobar una copia» se describe como más de lo que hace (I-4), y la ficha promete cualquier gestor (I-5). Los 16 menores van en la misma ronda (regla del dueño). La parte de la interfaz de I-3 se arregla en la ronda final de arreglos, con test, y llega a la lista del Abogado.
