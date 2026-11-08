# Clavero 1.4.0: arreglos de la revisión de la rama (parte de documentos)

Rama `ccr-d1e75160-p5f4wc`. Empecé sobre `ea36df7`; mientras trabajaba entraron en la rama los repasos de A y de B y sus arreglos (`55ddb62`, `2a5ae49`, `cf3e574`, `970359e`), así que las líneas citadas son las del árbol en `970359e`. Hallazgos de documentos de las tres revisiones (`branch-review-a-imports.md`, `branch-review-b-tools-vault.md`, `branch-review-c-build-docs.md`), con las reglas R37 a R39 y las decisiones (g) y (h) del registro. La parte de código está en `branch-review-fixes-code-report.md`. No he tocado código de la app ni tests, ni `progress.md`.

Cada afirmación sobre el código la he comprobado en el código; las líneas que cito abajo son las que he mirado.

## Puerta

Las dos, con el wrapper (R29), en el árbol final de esta ronda. Los documentos son entradas de Gradle desde M-6, así que las pruebas de textos se ejecutaron otra vez:

- `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets --no-daemon --max-workers=1`: **BUILD SUCCESSFUL**, **1 804 tests**, 0 fallos, 0 errores, 0 omitidos (`:app:testDebugUnitTest` se ejecutó, no salió `UP-TO-DATE`); lint 0 errores y 19 avisos (los conocidos); 0 líneas `w:`. Son 1 803 de `45c2660` más 1 de `970359e` (`HashLabSessionTest.anOldReadingNeverWritesItsProgressOnTheNewFile`).
- `./gradlew -p jvm-check test --no-daemon --max-workers=1`: **BUILD SUCCESSFUL**, **1 799 tests**, 0 fallos, 0 errores, 0 omitidos (`:test` se ejecutó); 0 líneas `w:`.
- Después de poner las cuentas en el traspaso (que es entrada de Gradle y lo lee `ImportTextsTest`), volví a ejecutar esa clase en los dos proyectos, con `PublishedTextsTest`, `FinalLegalReviewTest` y `AppNameTest` (los que leen el README y los traspasos): 45 tests en cada uno, 0 fallos.

Palabras prohibidas: he pasado sobre todas las líneas añadidas y sobre el informe nuevo del ataque la misma regla de `ForbiddenWords` (en Python, en el scratchpad), con las demás palabras de la lista del encargo y las afirmaciones absolutas de `PublishedTextsTest`. Salió una sola, en el informe del ataque (el adjetivo de «el comportamiento … de un test `rtN_`»), que cambié por «correcto». Ahora no sale ninguna.

## A M-1 / R37: lo que pasa con un bloqueo después de confirmar

R37: confirmada, salir de la pantalla no corta la importación; un bloqueo antes de empezar a guardar la cancela (durante la copia, `importCopyNow` la rechaza; entre la copia y el guardado, `modify` ve la bóveda bloqueada, `vault/VaultStore.kt:605-608`, y `applyImport` da `Stale`, `:687-720`); no se importa nada y la copia, si se hizo, se queda; un guardado ya empezado termina; el resultado nunca se enseña después del bloqueo (`imports/ImportSession.kt:395-402`).
- `CLAUDE.md:84`: la línea de R17 pasa a «Pantalla de importar (R17, R37)», con lo de arriba.
- Diseño, «Bloqueo» (`docs/superpowers/specs/2026-11-03-clavero-1.4.0-design.md:306`): nota de corrección fechada con R37 y el test que lo fija (`AutoBackupImportCopyTest.aLockBetweenTheCopyAndTheSaveSavesNothing`). La frase original de `:304` se queda, como historia.
- Traspaso, §4 (`docs/superpowers/handoff/2026-11-08-clavero-1.4.0-traspaso.md:86-92`): R37 en el resumen de las decisiones.
- Auditoría: §3, fila «R17, R37» (`docs/auditorias/2026-11-08-auditoria-1.4.0.md:100`); §6, el límite de R17 reescrito con R37 (`:177`); §4, `applyImport` con la bóveda bloqueada da `Stale`.
- README, P-8 (`README.md:347`): decía «o sale «La bóveda se ha bloqueado antes de hacer la copia…», o la importación termina entera». Con R37, tras un bloqueo la pantalla se va (la sesión se borra y `onBack`), así que ese texto no tiene por qué verse. Ahora dice lo que se comprueba al desbloquear: o la bóveda está como antes (y la copia, si se hizo, sigue en la carpeta) o la importación está entera, nunca a medias, y sin resultado después del bloqueo.
- `.maestro/README.md:69`: la fila de P-8 lo dice igual.

## A M-5: la nota del diseño para R34 y R36

- Diseño, «Duplicado» (`design.md:259-265`): nota de corrección (ataque RT-1 y RT-2, reglas R34 y R36) que sustituye a la regla del `LOGIN`: `compareKey` (`imports/ImportModel.kt:139-153`), el título sin mayúsculas (`imports/MergePlan.kt:366`) y el usuario con ellas (`:369`); el host de `parseLink` (`tools/qr/LinkSignals.kt:62`, `urlHost` en `MergePlan.kt:382-389`), con `\` que cierra la autoridad y `https://` delante de un texto sin esquema web; la `NOTE` por la clave del título y el texto recortado (`:434`, `:445`); lo guardado no cambia; la fila del duplicado con el usuario y el host (`duplicateSubline`, `:400`); y «Reemplazar» con las notas recortadas (A M-2, `:250-255`).
- El plan, en el mismo sitio (`docs/superpowers/plans/2026-11-03-clavero-1.4.0.md:406`).

## C I-2: `CLAUDE.md`, el traspaso y la auditoría al día

**`CLAUDE.md`:**
- `:35`: los prefijos que fija `AppNameTest`: `seguridad-auto-`, `seguridad-boveda-` y `seguridad-antes-de-importar-` (comprobado en `app/src/test/java/app/seguridad/tools/AppNameTest.kt:130-134`).
- `:42-43`: la rama sigue con la revisión final; el informe del ataque, con su ruta.
- La tabla de pasos (`:53-63`): la tarea 12 terminada con su ronda y su repaso (`4929ef6`, `26f30d4`, `e600feb`); el primer ataque y sus arreglos, hechos (`0089b90`); la ronda final, hecha (`c70a0f5`); los flujos de Maestro, hechos (`93b3f71`); la revisión de la rama en tres partes, hecha; su ronda, hecha (código y documentos); los repasos de A y B, hechos con sus arreglos, y el de C, pendiente; después, el segundo ataque, `code-review`, `security-review`, la auditoría, el Abogado y el PR, pendientes.
- Decisiones de la 1.4.0: (d) con la ruta del informe (`:69`); (g) hecha (`:72`); **(h)** nueva (`:73`); R17 con R37 (`:84`); **R34 y R36** (`:92`), **R35** (`:93`), **R38 y R40** (`:94`) y **R39** (`:95`), cortas como las demás.
- CRY-A01 (`:137`): el núcleo en NFKC y la comprobación *leet* de la decisión (h) (`vault/MasterPolicy.kt:65-66`, `:114`, `:82`, `:242-246`), con el mismo `MASTER_TOO_WEAK`; «P4ssw0rd2024!» y «ｐａｓｓｗｏｒｄ12345» se rechazan (`RedTeamCryptoTest.kt:63`).
- Pendiente 1 del dueño (`:148`): P-4 a P-26, con los flujos de `.maestro/`, `.maestro/README.md` y P-19 con el APK firmado.

**El traspaso:**
- §1 (`:9-18`): lo hecho, con los commits; los tests, 1803 y 1798 después de `45c2660`, y 1804 y 1799 con el repaso de B y esta ronda; lo que falta.
- §2 (`:22-27`): las notas de corrección del diseño y del plan; «(a) a (h), R1 a R40»; el informe del ataque; P-1 a P-26 y `.maestro/README.md`.
- §3, «Para Claude» (`:34-41`): los pasos que quedan, en orden: el repaso de los documentos (C), el segundo ataque con su sección en el informe, `code-review` y `security-review`, la auditoría completa, el Abogado, el informe lleno y el PR. «Para el dueño», punto 2 (`:45`): los flujos ya están, P-4 a P-26 y P-19 con el APK firmado.
- §4 (`:86-92`): (h) y R34 a R40.

**La auditoría:**
- La entrada (`:3`) y §1.3, punto 2 (`:63-70`): lo hecho y lo que falta, con las marcas **[POR LLENAR]** solo donde falta.
- §3: «(a) a (h)» (`:88`), «R17, R37» (`:100`), **R20** (`:102`), **R34 y R36**, **R35**, **R38 y R40** y **R39** (`:114-117`).
- §5.1 a §5.3 (`:131-157`): la ronda final, la revisión de la rama y el ataque, resumidos; el segundo ataque, **[POR LLENAR]**.
- §6: R37, el usuario que distingue mayúsculas (R34), el tope de 65 536 de la contraseña pegada (B M-3, `vault/MasterPolicy.kt:31`) y la derivación cancelada que se espera (R38, R40).
- §7 (`:184-192`): solo lo que falta (el repaso C, el segundo ataque, `code-review`, `security-review`, la auditoría completa, el Abogado y el PR). Ya no da por pendientes las cinco líneas de la ronda final.
- §8 a §12: el diff del manifiesto y de los recursos sobre `970359e` (sin cambios), los 79 commits de `7c8dea7..970359e` (todos de `Claude <noreply@anthropic.com>`, con `Co-Authored-By` y sin `Claude-Session`), los recuentos de tests de cada paso, P-21 a P-26 en §10 y la lista del Abogado puesta al día en §11.

## C I-3: notas de corrección en el diseño y en el plan

Sin reescribir la historia: el texto de partida se queda y debajo va una nota fechada que dice qué cambió y con qué regla. Los números salen del código.
- **Duplicados (R34, R36):** arriba, en A M-5.
- **SLIP-0039:** diseño, `:368-370`; plan, `:255` y `:265`.
  - PBKDF2 de Bouncy Castle (`PKCS5S2ParametersGenerator`, `tools/slip39/Slip39.kt:5` y `:364`), la lista desde `assets`, el exponente de 0 a 8 (`:46`, rechazo en `:170`), los repartos con el 1 (`:45`) y hasta 64 bytes al recomponer (59 palabras, `:38`) (R4);
  - el aviso que se ve, copiado de `SHARE_WARNING` (`tools/slip39/SecretShareTexts.kt:23-25`) (R3, R3a).
- **Topes:** diseño, `:138-143`; plan, `:320`. 64 niveles y 48 MiB de árbol estimado (`imports/ImportJson.kt:22`, `:72`; R11a, R11c, R11b); 64 columnas y 500 000 celdas (`imports/ImportLimits.kt:25`, `:31`); los 20 000 elementos cuentan los que no entran (R12, `ImportLimits.kt:12`, `imports/ImportModel.kt:194`); 8 MiB de parte cifrada (`imports/ImportKdf.kt:56`); y R18 con su propia nota en «Etiquetas» (`design.md:244`: `IMPORT_MAX_NEW_TAGS`, `imports/MergePlan.kt:120`; 20 en la vista previa, `imports/ImportTexts.kt:292`).
- **La KDF:** diseño, `:160-162`. La cabecera se comprueba antes de pedir la contraseña (A M-3, `imports/BitwardenJson.kt:37-41`, `imports/ImportJson.kt:215`, `:224`) y el cerrojo de todo el proceso (R38, R40, `crypto/KdfLock.kt`).
- **`gradle -p jvm-check` → `./gradlew -p jvm-check`** (R29): plan, `:119` y `:168`, como nota bajo cada paso (los pasos se hicieron antes de R29).
- El plan está escrito en inglés, así que sus notas también.

## C I-4 / R39: el informe publicado del ataque

- Nuevo: `docs/auditorias/2026-11-08-ataque-1.4.0.md`, escrito desde `redteam-report.md` con el formato de informe de `experto-seguridad-cripto`:
  - el alcance, el modelo de amenaza y el método;
  - el resumen, con los tres hallazgos «Arreglado (`0089b90`)»;
  - cada hallazgo con dónde estaba, cómo se reproduce, el impacto, el arreglo con las líneas de hoy (`MergePlan.kt:382-389`, `ImportModel.kt:139-153`, `MasterPolicy.kt:65-66`, `:114`, `:242-246`…), los tests (con los nombres de hoy, como `rt1Impact_replaceCannotTakeTheAttackersUrlAndPassword`) y la referencia (CWE-436, CWE-1007, CWE-521);
  - los ataques resistidos, con sus tests;
  - «Necesita prueba en el móvil», con la P-n de cada una;
  - la puerta de cada paso;
  - las dos revisiones;
  - **«Segundo ataque (R32)», marcado [PENDIENTE]**.

  Dice también que la fila del duplicado con el host, que el agente propuso para RT-1 y no se aplicó en sus arreglos, llegó con la revisión de la rama (A M-4). Nombra productos, así que lleva la línea de marcas.
- `redteam-report.md:30-32`: «Estado» pasa de «Explotado (test)» a «Arreglado (`0089b90`)», y el recuento (`:34`) apunta al informe publicado.
- Lo apuntan `CLAUDE.md:43`, `:54`, `:69` y `:95`, la auditoría (`:65`, `:117`, `:152`) y el traspaso (`:12`, `:26`, `:37`).

## C M-2: las pruebas del ataque en el móvil

- `README.md:360-365`: **P-21** (R36: Chrome y Firefox van a `evil.example` con `https://evil.example\@banco.es/login`), **P-22** (capturas y Recientes en negro en importar), **P-23** (toques ocultos sobre «Importar» y «Reemplazar»), **P-24** (el portapapeles en Android 13 o posterior), **P-25** (sin `run-*` ni `dec*.tmp` tras cerrar el proceso a mitad) y **P-26** (`adb backup` y la transferencia a otro móvil). La línea de marcas va debajo (`:367`), porque nombran productos.
- P-25 usa `am force-stop` con la pantalla delante, no `am kill` tras pulsar Inicio, como decía el agente: al salir de la app, «Comprobar una copia» se para y borra su carpeta (`ui/screens/BackupCheckScreen.kt:209`, `leftApp`, `:193`), así que con Inicio no queda nada que comprobar. Los nombres los saqué del código: `DECRYPT_TMP_PREFIX = "dec"` y `File.createTempFile` en `cacheDir` (`ui/screens/SecurityToolsScreen.kt:583`, `:690`), y `comprobar` con `run-` (`vault/BackupCheck.kt:25`, `:27`). Los borrados al arrancar están en `SeguridadApp.kt:42` y `:63`.
- La entrada de «Pruebas de la 1.4.0» (`README.md:338`) dice que los flujos están en `.maestro/` y apunta a su `README.md`.
- `.maestro/README.md`: la entrada dice P-1 a P-26 (`:3`); en la tabla, las filas P-21 a P-26 son manuales, cada una con su motivo (`:82-87`), y llevan la línea de marcas (`:89`). Cambian también las filas de P-8, P-9, P-17 y P-19 (abajo).
- La auditoría, §10, tiene sus filas (`:232-236`).

## C M-3: las líneas de la auditoría

- `:13`: fuera la frase falsa («el código no cambia en la tarea 12 salvo una nota…»). Ahora dice que las líneas son de `970359e` y por qué cambiaron.
- La fila de la tarea 12 (§1.3, `:61`) tiene ahora los hashes `ef2300e`, `4929ef6`, `26f30d4` y `e600feb`, con el N-1 del repaso.
- Las líneas, comprobadas una a una en `970359e`:

| Antes | Ahora |
|---|---|
| `tools/KeyOpsQueue.kt:27-49` | `:27-53` |
| `android/BiometricVault.kt:166` | `:168` (el `NonCancellable` de `disable`) |
| `imports/ImportKdf.kt:58-84` | `:59-98` (`checkPbkdf2Iterations` a `bitwardenArgon2Params`) |
| `vault/VaultStore.kt:684` | `:687` (`applyImport`) |
| `ui/screens/ImportScreen.kt:291` | `:299` (el `catch` de `OutOfMemoryError`) |
| `imports/ImportModel.kt:106` | `:107` (`plainReportText`) |

  Siguen valiendo: `Detect.kt:40`, `ImportJson.kt:22,72,79`, `BitwardenCrypto.kt:151`, `AutoBackup.kt:402`, `HashLab.kt:98`, `PwnedPasswords.kt:24`, `SecureWindow.kt:24` y `BiometricVault.kt:183`. Las nuevas, también comprobadas: `BitwardenJson.kt:37-41`, `ImportJson.kt:215`, `MergePlan.kt:382`, `:400`, `BackupCheck.kt:85`, `Components.kt:179`, `MasterPolicy.kt:31`, `Csv.kt:84` y `VaultEntryScreen.kt:407`.
- En los dos apartados de revisión (`:252-266`), las mismas líneas al día.

## C M-7: la lista consolidada del Abogado

- `task-12-report.md:131`: la etiqueta de R35 con el texto final, «Nombre (p. ej. «Correo» o «Cartera»)» (`ui/screens/VaultEntryScreen.kt:407`).
- `task-12-report.md`, sección nueva «Puesta al día de la lista, revisión de la rama» (al final). Contiene:
  - R35;
  - el texto final de `IMPORT_NOT_PROTECTED` (N-1), que ya estaba bien en el punto B y se repite aquí, copiado de `imports/ImportTexts.kt:218-222`;
  - la nota de los grupos de 2FAS en claro (`task-12-rereview-1.md:73`);
  - la fila del duplicado con el host (A M-4);
  - `IMPORT_REPLACE_NOTE` con A M-2;
  - lo que cambia sin texto nuevo (A M-3, B I-1 con `HASH_LAB_HMAC_INTRO` y `CLASSIC_NOT_SAVED`, B M-3 con los dos textos de «demasiado largo», que no dicen el número);
  - `KDF_OUT_OF_MEMORY` (`crypto/Crypto.kt:81`) con R38 y R40;
  - la regla de la maestra (h): ni la política ni la ficha ni «Funciones» del README la describen, solo dos pruebas del README (la de la 1.3.1 y P-19);
  - los textos nuevos del README;
  - el informe publicado del ataque, con productos y dominios de ejemplo;
  - los documentos técnicos.
- Una línea en blanco doble que ya estaba (`task-12-report.md:137`), quitada.

## Lo de los flujos de Maestro

- **P-17** (`README.md:356`): «Repartir con exponente 8» no se puede hacer desde la app. `SecretShareScreen.kt:244` llama a `splitSecret` sin exponente, así que se usa el de por defecto, el 1 (`Slip39.kt:45`, KDoc en `:41`). He mirado los 45 vectores de `app/src/test/resources/vectors/slip39.json`, leyendo la cabecera de cada parte (los 4 bits bajos de la segunda palabra): el exponente más alto es el **3**, en los n.º 42 y 44, que son válidos, y no hay ninguno con 8. P-17 pide ahora:
  - recomponer el n.º 4 (`b43ceb7e57a0ea8766221624d01b0864`, como el flujo `semiautomaticas/p17-recomponer-vector-oficial.yaml`);
  - recomponer el n.º 42 (una parte de 20 palabras, exponente 3, `1679b4516e0ee5954351d288a838f45e`), anotar cuánto tarda y «Cancelar» si da tiempo;
  - y dice que el tope es 8, que uno mayor se rechaza antes de calcular y que un exponente de 4 a 8 solo llega con partes de otra herramienta.

  La contraseña de los vectores se cita por su constante (`VECTOR_PASSPHRASE` en `Slip39Test.kt`), sin escribirla, porque es un nombre de producto. Ningún test recompone una parte con exponente 8: el rechazo del 15 lo prueba `RedTeamCryptoTest.resistsForeignTamperedAndCostlySlip39Parts`, y los repartos de los tests usan el 0. Lo apunto abajo.
- **P-19** (`README.md:358`): dice que va con el APK firmado y que pasar del de depuración al firmado, o al revés, obliga a desinstalar y borra la bóveda (el `applicationId` es el mismo y las firmas son distintas: `app/build.gradle.kts` no tiene `applicationIdSuffix`). Añade la regla de (h) («P4ssw0rd2024!» y «ｐａｓｓｗｏｒｄ12345»), que el flujo `borra-datos/p19-crear-boveda-maestra-nueva.yaml` prueba en parte: Maestro no escribe ancho completo, y la fila de P-19 en `.maestro/README.md` lo dice.
- **`CLAUDE.md`, pendiente 1** (`:148`), y el README (`:338`) apuntan a `.maestro/` y a `.maestro/README.md`.
- **`.maestro/README.md`**:
  - P-8: R37;
  - P-9: el host de la fila del duplicado, que el flujo no comprueba;
  - P-17: el vector n.º 42 en lugar del exponente 8, que no se puede repartir;
  - P-19: el ancho completo.

## Lo demás que se pedía comprobar

- **«Funciones» del README frente a esta ronda:**
  - `README.md:35`: la fila de un acceso repetido enseña el usuario y la web, solo su dominio, que traería «Reemplazar» (`duplicateSubline` da `usuario · host`, `MergePlan.kt:400-406`); los parámetros de la clave se comprueban también antes de pedir la contraseña (A M-3).
  - `README.md:52`: los campos de las tres herramientas nuevas usan el teclado sin sugerencias ni aprendizaje. Lo comprobé en el código: `SECRET_TEXT_KEYBOARD` en los cuatro campos del Laboratorio de hashes y los seis del clásico (`PlatformSourcesTest`), el secreto y las partes de «Repartir un secreto» (`SecretShareScreen.kt`), y la clave del HMAC y la contraseña de SLIP-0039 son `PasswordField`, que usa `KeyboardType.Password` sin autocorrección (`ui/theme/Components.kt:196`).
  - P-9 (`README.md:348`): la fila del duplicado.
  - El tope de 65 536 caracteres de la contraseña pegada no lo nombra el README.
- **Pasos P-n con 1 024 caracteres:** ninguno. Solo lo nombran el diseño y el plan, para el tamaño de un campo importado, que no cambia.

## No hecho, o para el controlador

- **Ningún test recompone una parte de SLIP-0039 con exponente 8** (de 4 a 8, en realidad). El tope y el rechazo de uno mayor están en el código (`Slip39.kt:46`, `:170`) y el rechazo del 15 tiene test, pero el camino que acepta 8 solo se recorre con los vectores hasta el 3. Es un test que falta, no un fallo conocido. No lo he añadido porque esta tarea no cambia tests.
- **Las líneas citadas se mueven** si entra más código antes del cierre: la auditoría lo dice (`:13`) y se vuelven a fijar al cerrar.

## Revisión experto-seguridad-cripto

He usado `SKILL.md` (el formato de informe de auditoría y «Trabajo conjunto con superpowers», en la fila de *finishing-a-development-branch*) para el informe publicado del ataque, y su principio 2, «evidencia antes que opinión», para cada cifra y cada línea.
- **El informe del ataque** sigue el formato de la skill: alcance y modelo de amenaza, resumen por severidad, cada hallazgo con dónde, qué pasa, impacto, corrección y referencia (CWE-436, CWE-1007, CWE-521, WHATWG URL Standard, UAX #15, NIST SP 800-63B), lo que está bien y los siguientes pasos (el segundo ataque). La severidad es la del agente (CVSS 3.1) y no la he cambiado.
- **Ningún documento dice más de lo que hace el código:**
  - R37: la importación se cancela con un bloqueo antes del guardado (`VaultStore.kt:605-608`, `:687-720`); P-8 ya no promete un texto que la pantalla no llega a enseñar;
  - R34: el usuario distingue mayúsculas (`MergePlan.kt:369`), y está como límite aceptado en la auditoría;
  - la regla (h) solo vale para una maestra nueva (`MasterPolicy.kt:242-246`, en `newMasterProblem`).
- **SLIP-0039:** la nota corrige que PBKDF2 no es de `javax.crypto` (`Slip39.kt:5`, `:364`). P-17 ya no pide algo que la app no hace, y queda apuntado el test que falta del exponente 8.
- **Nada secreto en los documentos:** la contraseña de los vectores se cita por su constante. La maestra de los flujos (`nube-ladera-pino-ancla-trigo-prueba`) es sintética y ya estaba en `.maestro/`. Los dominios son de ejemplo (`evil.example`, `banco.es`, `banco-acceso.example`). No hay correos.
- **MASVS:** P-22 (MASVS-PLATFORM-3, capturas), P-23 (MASVS-PLATFORM-3, *tapjacking*), P-24 (MASVS-STORAGE-2, portapapeles), P-25 (MASVS-STORAGE-2, temporales) y P-26 (MASVS-STORAGE-2, copias) cubren las comprobaciones que solo se ven en un móvil.

## Revisión Ingeniero Móvil Senior

He usado `desarrollo-movil-senior` (ciclo de vida y procesos) y `auditoria-seguridad-movil` (MASVS) para las pruebas del móvil nuevas.
- **P-25:** `am kill` solo mata un proceso en segundo plano, y salir de la app para «Comprobar una copia», que borra su carpeta (`BackupCheckScreen.kt:209`, `ON_STOP` sin cambio de configuración). Por eso la prueba usa `am force-stop` con la pantalla delante, que deja lo que se comprueba al arrancar (`SeguridadApp.kt:42`, `:63`). `run-as` solo funciona con un APK depurable, y la prueba lo dice.
- **P-19:** cambiar de firma con el mismo `applicationId` obliga a desinstalar, y eso borra los datos de la app. La prueba y `.maestro/README.md` lo dicen.
- **P-26:** `allowBackup="false"`, `dataExtractionRules` y `fullBackupContent` (`app/src/main/AndroidManifest.xml:11-13`) son lo que la prueba comprueba en un móvil.
- **Maestro:** las filas nuevas de `.maestro/README.md` son manuales y dicen por qué. El ancho completo de P-19 queda a mano porque Maestro solo escribe ASCII en Android, como ya decía su README.
- **Sin cambios de código, manifiesto, recursos ni dependencias** en esta ronda.

## Textos para el Abogado Digital (revisión final)

Todos están apuntados en la sección nueva del final de `task-12-report.md` («Puesta al día de la lista, revisión de la rama»). En resumen:
- **README.md:**
  - `:35`: la fila del acceso repetido con el dominio, y «antes de calcular nada y antes de pedir su contraseña»;
  - `:52`: el teclado sin sugerencias de las tres herramientas;
  - `:338`: la entrada de las pruebas con `.maestro/`;
  - P-8 (`:347`), P-9 (`:348`), P-17 (`:356`) y P-19 (`:358`);
  - las nuevas **P-21 a P-26** (`:360-365`), que nombran Chrome, Firefox, Gboard y la copia de Google, con la línea de marcas en `:367`.
- **`docs/auditorias/2026-11-08-ataque-1.4.0.md`:** documento público nuevo. Nombra productos (Bitwarden, Chrome, Firefox) y dominios de ejemplo, y lleva la línea de marcas.
- **`.maestro/README.md`:** las filas P-21 a P-26 y la línea de marcas.
- **De las revisiones, sin texto nuevo en la interfaz:**
  - la etiqueta final de R35;
  - `IMPORT_NOT_PROTECTED` y la nota de los grupos de 2FAS;
  - la fila del duplicado con el host;
  - `IMPORT_REPLACE_NOTE` con A M-2;
  - `HASH_LAB_HMAC_INTRO` y `CLASSIC_NOT_SAVED`, que vuelven a ser verdad;
  - `BACKUP_CHECK_TOO_LONG` e `IMPORT_PASSWORD_TOO_LONG`, con el tope nuevo;
  - `KDF_OUT_OF_MEMORY`;
  - `MASTER_TOO_WEAK` con (h).
- **Documentos técnicos** (`CLAUDE.md`, el traspaso, el diseño, el plan, la auditoría, `redteam-report.md`): no cambian ningún texto de la interfaz ni de la tienda. El bloque de «Novedades» del traspaso no cambia (`ImportTextsTest.thePlayListingAndItsNovedadesNameNoOtherProduct`).
