# Revisión de la rama (1.4.0), parte C: repaso 1 de los arreglos

**Revisor:** el de la parte C (`branch-review-c-build-docs.md`), sobre `b1902ed`. He revisado solo los arreglos de mis hallazgos:
- el código: `2ddc305` y `45c2660`, con `branch-review-fixes-code-report.md`;
- `.maestro/`, en `93b3f71`;
- los documentos: `e6e2b3a`, con `branch-review-fixes-docs-report.md`;
- los commits pequeños del controlador: `2a5ae49`, `970359e` y `b1902ed`.

También he leído las reglas R37 a R40 del registro. Solo es revisión: no he editado nada salvo este archivo y no he ejecutado Gradle.

**Resultado:** **Con cambios.** Los 11 hallazgos están arreglados (4 importantes y 7 menores), y lo que dicen los documentos es verdad contra el código de `b1902ed`. Salen 2 menores nuevos, los dos de documentos y de una línea cada uno.

## Hallazgos de la revisión, uno por uno

| Hallazgo | Estado | Evidencia |
|---|---|---|
| **I-1**, sin `.maestro/` | Arreglado | 25 flujos y `comun/`, `datos/preparar_archivos.py` y `.maestro/README.md`. La tabla de `CLAUDE.md` tiene la fila «Flujos de Maestro … Hechos (`93b3f71`), sin ejecutar aquí». README `:338` y traspaso §1, §2 y §3 (punto 2 del dueño) remiten a `.maestro/README.md`. Las comprobaciones de los flujos, abajo. |
| **I-2**, estado y decisiones atrasados | Arreglado | La tabla de `CLAUDE.md` coincide con el registro: ronda final `c70a0f5`, ataque y `0089b90`, revisión de la rama, repasos A y B hechos y C pendiente. Están (h), R34 a R40 y el punto CRY-A01 con NFKC y *leet* («P4ssw0rd2024!», «ｐａｓｓｗｏｒｄ12345»), que coincide con `vault/MasterPolicy.kt:66,82,114,246`. Traspaso §1 a §4 al día («reglas R1 a R40», «(a) a (h)»). Auditoría §3 con (h), R20, R34 a R40 y §7 reescrito. |
| **I-3**, diseño y plan sin notas | Arreglado | El diseño tiene notas fechadas de los topes, la contraseña, el cerrojo, R18, los duplicados (R34, R36, RT-1, A M-2), R37 y SLIP-0039 (R4, R3, R3a). El plan tiene sus notas en Task 1 y Task 2 (R29), en SLIP-0039 (R4, R3a), en los topes y en `:400` («superseded»). Todas las líneas que citan las he comprobado: `Slip39.kt:5,38,45,46,170,364`, `SecretShareTexts.kt:23-25` (el literal es exacto), `MergePlan.kt:120,250-255,366,369,382-389,400`, `ImportModel.kt:139`, `ImportJson.kt:22,72,215,224`, `BitwardenJson.kt:37-41`, `ImportKdf.kt:56`, `ImportTexts.kt:292`, `VaultStore.kt:687`, `AutoBackup.kt:404` (`importCopyNow`) y `AutoBackupImportCopyTest.kt:323`. |
| **I-4**, informe del ataque | Arreglado | `docs/auditorias/2026-11-08-ataque-1.4.0.md` existe (R39), con el estado de RT-1 a RT-3 «Arreglado (`0089b90`)» y la sección del segundo ataque **[PENDIENTE]**. La tabla de `redteam-report.md:30-32` también dice «Arreglado». `CLAUDE.md`, la auditoría y el traspaso dan la ruta exacta. |
| **M-1**, el borrado del Laboratorio de hashes | Arreglado | `HashLabSession.forget()` pone a cero `fileAlgo`, `readBytes` y `totalBytes` dentro del mismo `synchronized(fileState)`, y `file.clear()` sube el `run` (`CipherSessions.kt:184-185`). Así `sized` y `progress` de la lectura vieja se rechazan (`HashLabSession.kt:77-91`). Después, `970359e` cerró la carrera entre `startFile` y `progress`: primero se abre el `run` nuevo y luego se borran las cifras, todo bajo el mismo cerrojo, con un test que falló antes. El ViewModel ya no guarda estas cifras (`HashLabScreen.kt` las lee de la sesión). La línea de 156 caracteres está partida. |
| **M-2**, pruebas del ataque en el móvil | Arreglado | P-21 a P-26 están en el README (`:360-365`) con la línea de marcas, y también en la tabla de `.maestro/README.md` y en el informe publicado. P-26 coincide con `res/xml/data_extraction_rules.xml` (excluye todo en `cloud-backup` y en `device-transfer`) y con `allowBackup="false"`. P-25 explica bien por qué Inicio no sirve: `BackupCheckScreen.kt:209` para la comprobación en `ON_STOP`. |
| **M-3**, líneas de la auditoría | Arreglado | Fijadas a `970359e`, con la frase falsa quitada. Después de `970359e` no ha cambiado ningún archivo de `app/src/main` (`git diff 970359e..b1902ed -- app/src/main` está vacío). He comprobado: `KeyOpsQueue.kt:27-53`, `BiometricVault.kt:168,183`, `Detect.kt:40`, `ImportJson.kt:22,72,79,215`, `BitwardenJson.kt:37-41`, `ImportKdf.kt:59-98`, `ImportModel.kt:107,139`, `MergePlan.kt:382`, `VaultStore.kt:687`, `AutoBackup.kt:402`, `ImportScreen.kt:299`, `BackupCheck.kt:85`, `Components.kt:179`, `MasterPolicy.kt:31`, `Csv.kt:84`, `VaultEntryScreen.kt:407` y `PwnedPasswords.kt:24`. La fila de la tarea 12 da `ef2300e`, `4929ef6`, `26f30d4` y `e600feb`. |
| **M-4**, prefijos en `AppNameTest` | Arreglado | `AppNameTest` fija `IMPORT_COPY_PREFIX == "seguridad-antes-de-importar-"` y `backupFileName(2026-10-26) == "seguridad-boveda-20261026.sgbak"`. `CLAUDE.md:35` nombra los tres prefijos, y ahora es verdad que `AppNameTest` los comprueba. |
| **M-5**, nombres en la ficha y en las «Novedades» | Arreglado | `ImportTextsTest.thePlayListingAndItsNovedadesNameNoOtherProduct` mira, línea a línea, la ficha desde «## Nombre de la app» y cada bloque «Google Play, «Novedades»» de todos los traspasos. Solo admite la línea exacta de R30, que la ficha sigue teniendo (lo he comprobado), y se comprueba a sí mismo con una línea que nombra dos productos. |
| **M-6**, documentos como entradas de Gradle | Arreglado | `app/build.gradle.kts` (`tasks.withType<Test>().configureEach`) y `jvm-check/build.gradle.kts` declaran `README.md`, `NOTICE`, `SECURITY.md`, la política, la ficha, `PUBLICAR.md`, el anexo de textos de la 1.3.0, el workflow, `app/build.gradle.kts`, el manifiesto, los `*-traspaso.md`, `res/` y `assets/`, más los fuentes en `app`. Con `grep` de las rutas que leen los tests de `app/src/test/java` no sale ninguna fuera de esa lista. |
| **M-7**, lista del Abogado | Arreglado | `task-12-report.md`, «Puesta al día de la lista», tiene R35 con el texto final, `IMPORT_NOT_PROTECTED` en su versión final (`ImportTexts.kt:218-222`), los grupos de 2FAS en claro, la fila del duplicado, lo que cambia sin texto nuevo, la regla de la maestra nueva y los textos nuevos del README, del informe del ataque y de `.maestro/README.md`. |

## Comprobaciones pedidas

- **Los flujos de `.maestro/`, contra el código.** He comprobado con un script todos los `text`/`tapOn`/`assertVisible`/`visible`/`copyTextFrom` de los 25 flujos y de `comun/`: la parte literal de cada expresión está en un literal de `app/src/main/java` (con las cadenas unidas por `+`).
  - Lo que no sale como literal son plantillas de la app, y las he mirado una a una. Todas dan el texto que espera el flujo:
    - `Contraseña maestra (mínimo $MIN_MASTER caracteres)` y `MASTER_TOO_SHORT` (`VaultScreen.kt:270`, `MasterPolicy.kt:37`);
    - `hashLabRecalc`, y la huella de 64 bytes de `HashLabTexts.kt:55,60-61`;
    - `enigmaFinalPositions`, `analysisLetters` y `analysisIc` (`ClassicTexts.kt:60,74-75`);
    - `sharePartLabel`, el texto del grupo y `slip39WrongCount` (`SecretShareTexts.kt:70-71,105`);
    - `previewEntersText` con `typeCount`, y el texto de las entradas que han entrado (`ImportTexts.kt:255-265,364-366`);
    - `importFileText` (`:234`);
    - el resultado de «Comprobar una copia», con `backupCheckEntries`, `backupCheckType` y `EntryType.label` (`BackupCheckTexts.kt:44-51`, `Vault.kt:24,27`);
    - el error por defecto de `CryptoFailure` (`Crypto.kt:198`).
  - Lo demás son datos de prueba (hashes de vectores públicos, César, Vigenère, Enigma y la frase BIP-39 de entropía cero) o textos del selector del sistema («Descargas»/«Downloads», «Guardar»/«Save»).
  - Los dos casos de la maestra nueva de `borra-datos/p19-…` son correctos: «P4ssw0rd2024!» se rechaza por *leet* y «Password1!» por su núcleo («password»), los dos con `MASTER_TOO_WEAK`.
  - No he podido comprobar sin ejecutar el flujo que la copia real de la 1.1.1 tiene exactamente un «Acceso» y un «Código 2FA». Los tests dicen que tiene «Banco» (con el adjunto) y «Correo», y que hay un TOTP, así que es coherente. Los flujos dicen que no se han ejecutado nunca.
- **SLIP-0039 en P-17.** He descodificado la cabecera de los 45 vectores con la lista de palabras:
  - el n.º 4 tiene el exponente 2 y da `b43ceb7e57a0ea8766221624d01b0864`;
  - el exponente más alto es el 3 (los n.º 42 y n.º 44), y el n.º 42 da `1679b4516e0ee5954351d288a838f45e`;
  - ningún vector pasa de 3.

  Así que P-17 y la nota de `.maestro/README.md` son verdad. `b1902ed` añade `Slip39Test.aHighIterationExponentRoundTrips` con e = 4 y e = 8: lo que la app no puede hacer desde su pantalla queda probado en la suite.
- **El informe del ataque, contra el código.**
  - Las líneas que cita son correctas: `MergePlan.kt:366,369,382-389,400,434,445`, `ImportScreen.kt:672`, `ImportModel.kt:139`, `MasterPolicy.kt:66,82,89,114,242-246`, `RedTeamImportTest.kt:76,85,105`, `RedTeamCryptoTest.kt:62,69`, `MergePlanTest.kt:84,118,131,142,380`, `MasterPolicyTest.kt:237,245,277`, `SeguridadApp.kt:42,54,63`, `ImportKdf.kt:59-98`, `Crypto.kt:62-74` y `Shamir.kt:76`.
  - Los recuentos, también: 16 tests `resists…` (9 + 7) y 12 + 8 tests en total.
- **Palabras prohibidas** (la regla de `ForbiddenWords`, en Python): nada en lo que esta ronda añade al README, en el informe del ataque, en el traspaso, en `.maestro/README.md`, en los YAML ni en `preparar_archivos.py`. La ficha y la política no cambian en esta ronda.
- **Nombres de otras apps:**
  - El README (bajo P-26), el informe del ataque y `.maestro/README.md` llevan la línea de marcas.
  - Los YAML no nombran ningún producto, y la contraseña de los vectores (un nombre de producto) llega por `-e CONTRASENA_VECTOR`.
  - La ficha solo tiene la línea de R30, y ahora un test lo vigila (M-5).
- **Correos:** en `.maestro/`, `docs/`, `CLAUDE.md` y `README.md` solo hay `noreply@anthropic.com`, `seguridad.egt17baa@gmail.com` y datos de ejemplo (`example.com`, `ejemplo.es`, `evil.com`, y `alice@google.com`, que ya estaba). Los 11 commits de `e33a4d3..b1902ed` son de `Claude <noreply@anthropic.com>` y acaban solo en `Co-Authored-By`.
- **Archivos sueltos:** el `.orig` de `2ddc305` se quitó en `2a5ae49`, y `.gitignore` ignora ahora `*.orig` y `*.rej`. No hay otro archivo de extensión rara en `7c8dea7..b1902ed`. `.maestro/datos/generados/` lo ignora `.maestro/datos/.gitignore`.
- **`preparar_archivos.py`:** solo usa la biblioteca estándar, llama a `subprocess.run(["adb", …], check=True)` con una lista (sin `shell=True`) y copia archivos sintéticos del repositorio.

## Menores nuevos

**N-1. Los recuentos de tests no coinciden entre los documentos.**
- El informe del ataque, en «Puerta» (`docs/auditorias/2026-11-08-ataque-1.4.0.md`), dice «Hoy … 1 803 y 1 798».
- El traspaso (§1, «Tests») dice 1804 y 1799, con el arreglo de `970359e` y la parte de documentos.
- Después, `b1902ed` añade un test más (`Slip39Test.aHighIterationExponentRoundTrips`), así que la suite tiene ahora uno más que lo que dice el traspaso (no lo he ejecutado).
- Fallo: tres cifras distintas para «hoy» en documentos que se publican juntos.
- Arreglo: en el informe del ataque, poner la cifra con su commit («en `45c2660`: 1 803 y 1 798»), y no «hoy». Al cerrar el código, dar en el traspaso, en la auditoría §9 y en el informe la cifra de la puerta final, con su commit.

**N-2. El punto 5 de «Necesita prueba en el móvil» del informe del ataque dice `am kill`, y la prueba buena (P-25) usa `force-stop`.**
- `docs/auditorias/2026-11-08-ataque-1.4.0.md`, «Necesita prueba en el móvil», 5: «tras `adb shell am kill`». `am kill` solo mata un proceso en segundo plano, y para que la app pase a segundo plano «Comprobar una copia» se para y borra su carpeta (`BackupCheckScreen.kt:209`). Con ese comando no se prueba la muerte a mitad.
- El README (P-25) y `.maestro/README.md` dicen `adb shell am force-stop`, y el README explica por qué.
- Arreglo: «tras `adb shell am force-stop io.github.egt17baa.seguridad` con la comprobación en pantalla (P-25)». El mismo punto de `redteam-report.md:282` es un archivo de trabajo y puede quedarse como está.

## Revisión experto-seguridad-cripto

- **El borrado del Laboratorio de hashes (M-1, N-2 de B):** la comprobación del `run` y la escritura de las cifras van bajo un mismo cerrojo (`HashLabSession.kt:67-110`). Una lectura vieja ya no deja el tamaño ni el progreso de otro archivo, tampoco después de un borrado. `onWipe` va fuera del cerrojo, así que el borrado nunca espera a la lectura.
- **La regla de la maestra nueva:** `CLAUDE.md` la describe ahora igual que el código (`MasterPolicy.kt:66`, NFKC; `:114`, `isKnownWordInLeet`; `:82`, el tope de 128; `:246`). El flujo `p19` prueba los dos caminos de `MASTER_TOO_WEAK`.
- **SLIP-0039:** el exponente se rechaza por encima de 8 antes de PBKDF2 (`Slip39.kt:107,170`). Desde `b1902ed` hay un test de ida y vuelta con e = 8. Los vectores oficiales no pasan de e = 3 (descodificado aquí).
- **Las notas de corrección** describen los duplicados tal como los compara el código (`compareKey`, `parseLink`), y así ya no invitan a volver a la comparación que RT-1 y RT-2 explotaron.
- **Los textos fijados:** los prefijos de las copias (`AppNameTest`) y la ficha sin nombres de otras apps (`ImportTextsTest`).

## Revisión Ingeniero Móvil Senior

- **Gradle:** las entradas de los tests (`app/build.gradle.kts`, `tasks.withType<Test>().configureEach`; `jvm-check/build.gradle.kts`) son archivos simples con `PathSensitivity.RELATIVE`, resueltos al configurar, y no rompen la caché de configuración. Un cambio solo de documentos vuelve a ejecutar las pruebas que los leen.
- **Compose y estado:** las cifras del archivo pasan del ViewModel a las celdas de la sesión (`ComposeCells`). Se escriben desde el hilo de IO bajo `synchronized`, y la composición lee el estado de *snapshot* sin cambios de API.
- **`.maestro/`:**
  - los flujos de la raíz no hacen `clearState`, y los de `borra-datos/` lo dicen en su primera línea y en la tabla;
  - no hay capturas, por `FLAG_SECURE`;
  - Maestro solo escribe ASCII, y por eso el ancho completo queda a mano en P-19;
  - P-19 va con el APK firmado, avisando de que cambiar de firma obliga a desinstalar.
- **Ciclo de vida en P-25:** la prueba usa `force-stop` porque `ON_STOP` ya para la comprobación (`BackupCheckScreen.kt:209`). N-2 es el único sitio donde el informe dice otra cosa.

## Textos para el Abogado Digital (revisión final)

Nada nuevo fuera de la lista puesta al día de `task-12-report.md` (puntos 1 a 11). Si se aplica N-2, la frase del informe del ataque cambia, pero es un documento técnico y no un texto de la interfaz.
