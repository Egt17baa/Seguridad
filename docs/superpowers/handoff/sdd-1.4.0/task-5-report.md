# Tarea 5: Comprobar una copia, informe del implementador

**Estado:** DONE_WITH_CONCERNS (las dudas están en «Dudas»; ninguna bloquea)
**Commit:** `d27b5e2 1.4.0: Comprobar una copia`, subido a `origin/ccr-a1a6eabb-765f9g`.

## Qué se hizo

- `app/src/main/java/app/seguridad/vault/BackupCheck.kt` (nuevo):
  - `BackupCheckReport(version, entries, attachments, attachmentsOk, problems)`.
  - `suspend fun checkBackup(input, master, tempDir, io = Dispatchers.IO, compute = Dispatchers.Default)`.
  - Las comprobaciones que comparte con la importación: `checkCopyEntries` (antes `checkRestorable` más la regla de una sola identidad, en `VaultStore`) y `attachmentInCopy` (antes `checkInBackup`), más el mensaje de la importación `attachmentNotInCopy(name)`.
  - Una carpeta por ejecución, creada de forma atómica (`Files.createTempDirectory`) y marcada como en uso, y `removeBackupCheckLeftovers` para las que dejó una ejecución que murió con el proceso.
- `vault/VaultStore.kt`: `replace()` llama a `checkCopyEntries(incomingEntries)` (`VaultStore.kt:845`) y a `attachmentInCopy(...)` (`VaultStore.kt:852`). Las dos comprobaciones salen de `VaultStore`: el orden, los mensajes y los dispatchers son los de antes.
- `vault/Backup.kt`:
  - `BackupReader.read(input, tempDir, check = {})` lee como mucho `BACKUP_MAX_READ_BYTES` = 110 MiB del archivo (`Backup.kt:50`, `BoundedInput` en `Backup.kt:187`) y llama a `check` antes de cada lectura.
  - `BackupContents.version` dice la versión de la copia (1 o 2), con valor por defecto.
  - El formato no cambia.
- `vault/BackupCheckSession.kt` (nuevo):
  - `BackupCheckSession` es un `GuardedSession` con la contraseña, el archivo, el nombre, el informe, el error y si está en curso.
  - `BackupCheckRuns`: una ejecución cada vez. Tiene número de ejecución, mira `lockEpoch`, se queda con el `CharArray` y lo pone a cero.
- `vault/BackupCheckTexts.kt` (nuevo): todos los textos.
- `ui/screens/BackupCheckScreen.kt` (nuevo): el ViewModel y la pantalla.
- `SettingsScreen.kt:469`: el botón «Comprobar una copia», en «Copia de la bóveda», siempre activo, porque no necesita la bóveda.
- `AppNavHost.kt:279`: la ruta `settings/check-backup`.
- `SeguridadApp.kt:63`: borra al arrancar lo que dejaron las ejecuciones interrumpidas.
- Tests:
  - nuevos: `BackupCheckTest` (15) y `BackupCheckSessionTest` (8);
  - un test de fuentes nuevo en `PlatformSourcesTest.kt:221`;
  - dos tests puestos al día: `DocumentUriTest`, con el número de selectores protegidos (25 → 26), y `MasterPolicyTest.kt:525`, que admite `BackupCheckScreen.kt` como segunda pantalla que solo mira la longitud, porque pide la contraseña de una copia que ya existe, igual que la importación (CRY-A01).

### Decisión 1 (importReplace sin cambios de comportamiento)

No hace falta NEEDS_CONTEXT. `importReplace` no puede llamar a `checkBackup` tal cual sin cambiar su comportamiento: haría falta un segundo Argon2id, o sacar la derivación del mutex, y `checkBackup` borra su carpeta al terminar. Por eso las dos llaman a **las mismas funciones de comprobación**, en el mismo orden:

1. `BackupReader.read`;
2. `decodeFile`;
3. `unlock`;
4. `read`;
5. `checkCopyEntries`;
6. `attachmentInCopy` para cada adjunto.

`importReplace` lanza el mensaje de siempre (con el nombre del adjunto) en el primer adjunto que falla, como antes. `checkBackup` cuenta todos los que fallan.

Todos los tests de importación siguen en verde sin tocarlos (`BackupTest` 22, `VaultStoreTest`, `VaultV3Test`, `LegacyVaultTest`, `AutoBackupTest`, `AutoBackupF3Test`: 529 tests del paquete `vault` en verde).

El único cambio que se ve en la importación es el límite nuevo de bytes leídos (110 MiB). Lo pide el punto (a): solo afecta a archivos manipulados, porque una copia real como mucho pesa unos 100,2 MB (`BackupCheckTest.kt:419`).

## TDD

- **RED:** `gradle -p jvm-check test --tests 'app.seguridad.vault.BackupCheck*'` no compila, como se esperaba antes de escribir el código:
  - `Unresolved reference 'BackupCheckReport'`, `'BackupCheckRuns'`, `'BackupCheckSession'`, `'start'`, `'running'`…;
  - y lo mismo en `BackupCheckTest` para `checkBackup`, `BACKUP_MAX_READ_BYTES`, `backupCheckChanged` y `removeBackupCheckLeftovers`.
- **Primer GREEN:** 22 tests y 2 fallos, los dos del propio test y no del código:
  - `missingAttachmentIsReported` daba por hecho un orden de los adjuntos; ahora los elige por nombre;
  - `tempFilesAreDeleted` cortaba 30 bytes, que solo tocan el directorio central y `ZipInputStream` no lee, igual que antes; ahora corta por la mitad.
- **GREEN:**
  - `BackupCheck*`: 23 de 23 (15 más 8). Todo `app.seguridad.vault.*` en verde.
  - Verificación completa: `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets --no-daemon --max-workers=1` da **BUILD SUCCESSFUL**, con 1421 tests y 0 fallos.
  - Lint: **16 avisos**, los mismos que antes (14 en `gradle/libs.versions.toml`, 1 en el manifiesto y 1 en `mipmap-anydpi-v26`). No hay avisos nuevos de lint ni del compilador.
  - `gradle -p jvm-check test --no-daemon --max-workers=1` da **BUILD SUCCESSFUL**, con 1416 tests en verde. `BackupCheck.kt`, `BackupCheckSession.kt` y `BackupCheckTexts.kt` compilan en jvm-check.
  - Después de la verificación solo cambié un `lateinit` del test por dos `val`, para quitar el aviso del compilador, y volví a pasar `BackupCheck*` en verde.

## Punto (a): entradas enormes o maliciosas

| Caso | Límite y dónde | Test |
|---|---|---|
| Archivo de 200 GB (ruido) | Falla en la primera cabecera: `ZipInputStream` no ve LOC y queda `BACKUP_DAMAGED`. Se leen ≤ 64 KiB | `BackupCheckTest.kt:341` |
| Cabecera falsa que dice 4 GB (STORED con 0xFFFFFFFF) | Los tamaños de las cabeceras nunca se usan para reservar memoria. Solo cuentan los bytes leídos de verdad: 6 MB por entrada (`Backup.kt` `copy()`) y 100 MB en total. Error `BACKUP_DAMAGED` al acabarse el archivo | `BackupCheckTest.kt:367` |
| Entrada que no acaba nunca (bloques deflate vacíos: no sale ningún byte y la entrada no tiene fin) | **Límite nuevo:** `BACKUP_MAX_READ_BYTES` = 100 MiB + 10 MiB del archivo, en `BoundedInput` (`Backup.kt:50` y `:187`), con `BACKUP_TOO_BIG`. La contraseña incorrecta ni se prueba | `BackupCheckTest.kt:367` (lee entre MAX y MAX + 64 KiB) |
| Miles de adjuntos | `BACKUP_MAX_ENTRIES` = 2 000 entradas: `BACKUP_TOO_MANY` | `BackupCheckTest.kt:367` |
| Bytes escritos en la carpeta temporal | Como mucho `BACKUP_MAX_TOTAL_BYTES` (100 MB), porque todo lo descomprimido cuenta. Si el disco se llena, sale un `IOException` y se borra todo | `tempFilesAreDeleted` (`:232`) |
| Coste de Argon2id de la copia | `checkSane`, en `VaultCrypto.unlock`: como mucho 128 MiB, 10 pasadas y 4 hilos, y se rechaza antes de reservar memoria. Una falta de memoria se convierte en `CryptoFailure` | lo de antes |
| Trabajo de los hashes | `checkCopyEntries` rechaza más de 5 MB por adjunto y más de 50 MB en total **antes** de calcular los hashes (`BackupCheck.kt:145`) | `theChecksAreTheImportsOwn` (`:210`) |
| Una copia real en el límite (1 998 adjuntos con 100 MB aleatorios) | Sigue leyéndose, y pesa menos que `BACKUP_MAX_READ_BYTES` | `aCopyAtTheLimitsIsStillRead` (`:419`) |
| Cancelación | `check` antes de cada lectura del archivo, también dentro de una sola lectura del ZIP (`BackupCheck.kt:65`, `Backup.kt:196-206`), y `ensureActive` entre etapas y entre adjuntos (`BackupCheck.kt:68`, `:71`, `:77`). Argon2id no se puede cortar | `cancelLeavesNoTemp` (`:260`), `theReaderStopsWhenAskedTo` (`:434`) |
| Sin ANR | La cabecera se lee en IO (`BackupCheckScreen.kt:144-153`); `checkBackup` en IO y Default; nada en el hilo principal | test de fuentes `PlatformSourcesTest.kt:221` |

Se lee por partes, con búferes de 64 KiB (los de `BackupReader`). Ningún adjunto se carga entero en memoria: se escribe a archivo y su hash se calcula por bloques (`fileMatchesSha256`).

## Punto (b): ventanas entre la comprobación y el uso (TOCTOU)

- **`importReplace` importa exactamente lo que comprueba:**
  - el documento se lee **una sola vez**, a `files/vault/import-<rand>` (`VaultStore.kt:824`);
  - los hashes se comprueban sobre esos archivos (`:852`);
  - y son esos mismos archivos los que `moveIn` pone en su sitio (`:881`).
  
  La carpeta temporal es privada de la app.
- **Segundo toque:** `BackupCheckRuns.start` para la ejecución anterior y espera a que termine (`previous?.join()`, `BackupCheckSession.kt:95`). Así nunca hay dos Argon2id a la vez, y solo cuenta el número de ejecución más reciente (`:97`). Tests: `aSecondTapSupersedesTheFirstRun` y `aNewRunWaitsForTheOneItStopped`.
- **Salir de la pantalla:**
  - `onCleared` llama a `stopAll` y a `session.wipe()`;
  - salir de la app sin girar la pantalla, o cambiar de pestaña, llama a `leftApp()`, que para la ejecución (`BackupCheckScreen.kt:206`);
  - y además `lockEpoch` sube en cada `onStop` (`MainActivity.kt:113`), así que un resultado tardío se descarta (`BackupCheckSession.kt` `current`).
- **Bloqueo de la bóveda durante una ejecución:**
  - el ViewModel observa `store.state` y para la ejecución al pasar de abierta a cerrada (`BackupCheckScreen.kt:113`);
  - y `current(mine, epoch)` descarta cualquier resultado o error si la época cambió;
  - el guard sigue la regla «PIN y códigos» para el informe y la contraseña.
  
  Test: `aLockDuringTheRunDropsItsResult`.
- **Nada temporal que quede atrás:**
  - `checkBackup` borra su carpeta en un `finally` `NonCancellable` en IO (`BackupCheck.kt:97`), y solo cuando el trabajo ha vuelto, porque `withContext` espera a su bloque;
  - `BackupReader` borra lo que escribió si algo falla.
  
  Tests: `tempFilesAreDeleted` (éxito, contraseña incorrecta, versión más nueva, copia cortada, entradas rechazadas, informe con problemas e `IOException` al leer) y `cancelLeavesNoTemp`.
- **Dos ejecuciones nunca chocan:** cada una tiene su subcarpeta en `cacheDir/comprobar/`, creada con `Files.createTempDirectory` (atómica y con nombre aleatorio), registrada en `activeRuns` y borrada en el `finally` (`BackupCheck.kt:61`, `:107`). `removeBackupCheckLeftovers` nunca borra una carpeta en uso. Tests: `twoChecksAtOnceDoNotInterfere` (6 a la vez, 3 rondas, en IO) y `leftoversGoButNotARunGoingOn`.
- **Hashes:** `MessageDigest.isEqual`, en `fileMatchesSha256` (`Backup.kt:170`).

## Decisión 3 (pantalla)

- **La contraseña:** el campo tiene que ser un `String` (Compose no deja otra cosa), en una celda de la sesión del ViewModel y nunca en `rememberSaveable`. Al pulsar «Comprobar»:
  - pasa a un `CharArray` (`BackupCheckScreen.kt:173`) y el campo se vacía (`:174`);
  - `BackupCheckRuns` se queda con el `CharArray`, lo pone a cero al acabar (`BackupCheckSession.kt:107`) y en el acto con `stop()` (`:77`), que es lo que llama `forget()` a través de `onWipe`.
- **El patrón de las otras pantallas:** `GuardedSession`, `watchAway` (`BackupCheckScreen.kt:107`), `GuardEffects` (`:205`), `ToolColumn(..., s.guard)` y `TOOLS_AWAY_WIPE_NOTE`, más su propia nota (`BACKUP_CHECK_STOPS_NOTE`).
- **El informe:** solo números y problemas. Los problemas son `backupCheckMissing(n)` y `backupCheckChanged(n)`, sin nombres; `changedAttachmentIsReported` comprueba que no aparecen títulos ni nombres. El tipo de cada entrada se muestra con `EntryType.label`.
- **No escribe nada:** el test de fuentes comprueba que la pantalla solo usa `store.state` y `store.lockEpoch`, y que ni la pantalla ni `BackupCheck.kt` ni `BackupCheckSession.kt` llaman a importar, exportar, editar, ajustes, huella, copias automáticas, `openOutputStream`, `writeText` ni `writeBytes`. Además lo comprueba `vaultIsUntouched`: misma revisión, misma generación y los mismos bytes en `vault.bin` y en `att/`.
- **Lo que no prueba:** `BACKUP_CHECK_NOT_PROVEN`, siempre en pantalla.

## Revisión experto-seguridad-cripto

- **Modelo de amenaza:**
  - quien entrega un `.sgbak` manipulado, sin la contraseña o con ella (una copia hecha a mano);
  - quien tiene el móvil desbloqueado con la bóveda cerrada: la pantalla abre una copia con su contraseña y nada más, igual que «Importar copia» con la bóveda vacía. Cada intento cuesta un Argon2id con los parámetros de la copia, acotados por `checkSane`.
- **Solo primitivas estándar:**
  - AES-256-GCM y Argon2id de `VaultCrypto`, sin cambios;
  - SHA-256 de `MessageDigest` con `isEqual` (`Backup.kt:170`);
  - nada de criptografía propia ni dependencias nuevas.
- **Los formatos no cambian** (`seguridad-backup` v1 y v2, `seguridad-vault` v2 y v3). `formatPinned` y los tests de datos reales anteriores (`LegacyVaultTest`) siguen en verde. La versión que da el informe sale del marcador que ya existía (`Backup.kt:275`).
- **Los límites están antes del trabajo**, y el límite nuevo de bytes leídos está probado con una copia real en el límite. Detalle en la sección (a).
- **Secretos:**
  - la clave de datos se pone a cero en un `finally` (`BackupCheck.kt`, `key.fill(0)`);
  - el JSON descifrado lo pone a cero `VaultCrypto.read`;
  - las entradas descifradas (Strings) quedan en el montón hasta la recogida de basura, como en una importación;
  - no hay ningún log.
- **Mensajes:** son los de la importación (`theChecksAreTheImportsOwn`, `wrongPasswordThrows` y `newerVersionThrowsNewerMessage` los comparan con los de `importReplace`), así que un error no da más información que la importación.
- **Lo que no se protege:**
  - Argon2id no se puede cancelar: si se para, la ejecución termina la derivación (unos segundos) antes de borrar la carpeta, y la siguiente espera;
  - la contraseña pasa por un `String` mientras se escribe.

## Revisión Ingeniero Móvil Senior

- **Ciclo de vida:**
  - el estado vive en el ViewModel de la entrada de navegación y sobrevive al giro, pero no a la muerte del proceso; nada va a un `Bundle`;
  - `ON_STOP` con `isChangingConfigurations` no para la ejecución (`BackupCheckScreen.kt:206`), igual que `MainActivity` no bloquea al girar.
- **Concurrencia:**
  - `viewModelScope` en el hilo principal; IO para el `ContentResolver` y el ZIP, Default para Argon2id y el descifrado;
  - una ejecución cada vez, con número de ejecución y época;
  - el registro `activeRuns` está protegido con `synchronized` y la creación de carpetas es atómica.
- **Selectores:** `rememberDocumentPicker(OpenDocument)` con `*/*` (`DocumentUriTest`, 26 protegidos) y `launchOrError`. No pone `keepOpenOnce`, igual que el resto de herramientas.
- **Accesibilidad:** el informe y los errores en una `liveRegion` educada; los textos en `Text`.
- **Rendimiento:** búferes de 64 KiB y nada entero en memoria. La comprobación completa de una copia en el límite tarda unos segundos (`aCopyAtTheLimitsIsStillRead`).
- **Al arrancar:** la limpieza va en `appScope` en IO (`SeguridadApp.kt:63`), fuera del hilo principal.
- **Sin cambios en permisos, componentes exportados, dependencias ni hosts de red.**

## Textos para el Abogado Digital (revisión final)

Todos están en `app/src/main/java/app/seguridad/vault/BackupCheckTexts.kt`:

- `BACKUP_CHECK_TITLE`: «Comprobar una copia» (el botón de Ajustes y el título).
- `BACKUP_CHECK_INTRO`: «Comprueba una copia .sgbak sin importarla: que la contraseña maestra que escribas la abre, que sus entradas se leen y que cada adjunto está en la copia y coincide con la huella SHA-256 que guarda su entrada. No cambia nada: no toca tu bóveda, tus ajustes ni tus copias. Mientras la comprueba, guarda lo que lee en una carpeta temporal de la app y lo borra al terminar.»
- Botones y estados: «Elegir copia», «Contraseña maestra de la copia», «Comprobar», «Cancelar», «Comprobando la copia…».
- «Ese texto es demasiado largo y no se ha pegado.»
- «Este archivo no es una copia .sgbak de Clavero. Esta pantalla solo comprueba esas copias.»
- «No se pudo leer el archivo», «No se pudo comprobar la copia».
- «Resultado».
- «No se ha encontrado ningún problema: la contraseña abre la copia, sus entradas se leen y todos sus adjuntos coinciden.»
- «La copia tiene problemas y no se puede importar así:»
- Los recuentos:
  - «Formato de la copia: versión N»;
  - «N entradas» o «1 entrada», y «<tipo>: N»;
  - «Sin adjuntos», «1 adjunto; coincide: N» y «N adjuntos; coinciden: M».
- Los problemas:
  - «Falta 1 adjunto en la copia.» o «Faltan N adjuntos en la copia.»;
  - «1 adjunto no coincide con lo que guarda su entrada: está dañado o se ha cambiado.» o el plural.
- `BACKUP_CHECK_NOT_PROVEN_TITLE`: «Lo que no comprueba».
- `BACKUP_CHECK_NOT_PROVEN`: «No dice si es tu copia más reciente ni si tiene lo que cambiaste después de hacerla. No comprueba que quede espacio en este móvil para importarla. Importar una copia desactiva la huella: después tendrás que volver a activarla.» Que la huella se desactiva al importar es verdad: lo hace `VaultBackup.kt:175`.
- `BACKUP_CHECK_STOPS_NOTE`: «La contraseña se borra del campo al pulsar «Comprobar». Si sales de la app, vuelves atrás o se bloquea la bóveda mientras se comprueba, la comprobación se para y no se muestra su resultado.» Sobre «se para»: si en ese momento corre Argon2id, termina esa derivación por dentro y su resultado se descarta. Si hace falta, se puede decir «se detiene en cuanto puede».
- Se usan también `TOOLS_AWAY_WIPE_NOTE` y los mensajes de la importación que ya existían.

Ninguna palabra prohibida: `SourceTextsTest` está en verde.

## Dudas

1. **El límite nuevo de 110 MiB** también se aplica a `importReplace`. Solo cambia algo con archivos manipulados (una entrada que nunca acaba ahora da «La copia pasa de 100 MB» en lugar de leerse sin fin), y una copia real en el límite pesa unos 100,2 MB. El revisor debería confirmar que es el cambio mínimo que pide el punto (a).
2. **`importReplace` no llama a `checkBackup`** como función entera: las dos comparten `checkCopyEntries` y `attachmentInCopy` (ver «Decisión 1»), porque llamarla supondría un segundo Argon2id o cambiar el mutex.
3. **«Comprobar una copia» funciona también con la bóveda cerrada o sin bóveda**, para revisar una copia antes de importarla en un móvil nuevo. Es la misma posibilidad que ya da «Importar copia» sin bóveda.
4. **Una contraseña pegada de más de 1 024 caracteres se rechaza**, y la pantalla lo dice.

---

# Ronda de arreglos 1 (revisión `task-5-review.md`)

**Commit:** `22cee96 1.4.0: Comprobar una copia, arreglos de la revisión de la tarea 5`, subido a `origin/ccr-a1a6eabb-765f9g`.

## Qué cambió

- **I1. La clave de datos se pone a cero siempre.**
  - La derivación corre dentro de `withContext(NonCancellable) { withContext(compute) { key = unlock(...) } }` (`BackupCheck.kt:89-91`), el mismo anidado que `derive` en `VaultStore`.
  - La clave se asigna **dentro** del bloque, a una `var` que está fuera del `try`. El `finally` hace `key?.fill(0)` (`BackupCheck.kt:116`) aunque la cancelación descarte el resultado del `withContext`.
  - La cancelación se aplica justo después, con `ensureActive`.
  - El parámetro `unlock` (`BackupCheck.kt:74`, por defecto `VaultCrypto.unlock`) existe solo para que los tests vean la clave.
  - El `CharArray` de la contraseña ya se ponía a cero en todos los caminos: en el acto con `BackupCheckRuns.stop()` y siempre en el `finally` de la ejecución. Ahora lo comprueban también los tests del camino real.
- **I2. Una sola derivación cada vez, en cualquier camino.**
  - Hay un `Mutex` de proceso, `derivations` (`BackupCheck.kt:52`), alrededor de la derivación no cancelable (`:89`).
  - Esperar el `Mutex` sí es cancelable, así que una ejecución cancelada antes de derivar no deriva. Una que ya tiene el `Mutex` lo suelta cuando Argon2id termina.
  - Ya no depende de `job` ni de `join`. El `previous?.join()` de `BackupCheckRuns` se quitó, y con él el test `aNewRunWaitsForTheOneItStopped`, que probaba un camino que la interfaz no permite.
- **m1. No queda ninguna carpeta vacía.** La carpeta se crea con `withContext(NonCancellable) { withContext(io) { dir = newRunDir(tempDir) } }` (`BackupCheck.kt:80`), dentro del `try`. El `finally` la borra y la quita de `activeRuns` en cuanto existe (`:117`).
- **m2. La KDoc dice lo que de verdad se comprueba.**
  - La KDoc de `BackupCheckReport` dice exactamente qué se comprueba y qué no:
    - la nueva envoltura de A8 (un segundo Argon2id que puede quedarse sin memoria con «Alto»);
    - que la bóveda tenga que estar abierta o no haber bóveda;
    - guardar los ajustes;
    - el espacio para guardar.
  - El texto de la pantalla `BACKUP_CHECK_NOT_PROVEN` añade la frase de A8 (abajo).
  - R5 queda así: «cuenta y no nombra» y «no vuelve a envolver (A8)» son las dos diferencias con la importación.
- **m3. El selector, igual que el de «Importar copia».**
  - Se copia exactamente el patrón de `VaultBackup.kt:201-210`: `VaultLock.keepOpenOnce = true`, `store.touch()`, `launchOrError(arrayOf("*/*"))` y, si el selector no se abre, otra vez `keepOpenOnce = false` (`BackupCheckScreen.kt:222-225`).
  - `MainActivity.onStart` quita la marca al volver (`MainActivity.kt:60`).
  - Antes, elegir el archivo con la bóveda abierta la bloqueaba (`MainActivity.kt:113`, `LEFT_APP`). La pantalla no se borraba, porque `lockWipes` no borra con `LEFT_APP`, pero la bóveda sí quedaba bloqueada. Ahora sigue abierta mientras el selector está encima, con el límite de seguridad `AWAY_BACKSTOP_MS`.
  - `store.touch()` solo reinicia la cuenta de inactividad y sube la marca de reloj de A11 (`VaultStore.kt:555-558`), lo mismo que hace cualquier uso de una pantalla de la bóveda y lo mismo que «Importar copia». No cambia ningún ajuste del usuario.
  - El test de fuentes ahora deja usar `store.state`, `store.lockEpoch` y `store.touch`, cada uno una vez donde toca, y fija el bloque del selector, el de «Importar copia» y el reinicio en `onStart` (`PlatformSourcesTest.kt:245`). `BackupCheck.kt` y `BackupCheckSession.kt` siguen sin `keepOpenOnce` ni `touch()`.

## Tests nuevos (`BackupCheckTest.kt`)

- `cancelDuringTheDerivationZeroesTheKey` (`:311`): cancela mientras corre un `unlock` lento. La derivación termina y la clave que devolvió queda a cero, y la carpeta temporal, vacía. Después de una comprobación que acaba bien, la clave también queda a cero.
- `afterCancelTheNextDerivationWaits` (`:332`): el camino real de la interfaz, con `BackupCheckRuns` y `checkBackup` de verdad:
  1. `start`;
  2. `stop` («Cancelar») durante Argon2id, y la primera contraseña queda a cero en el acto;
  3. `start` otra vez: después de 300 ms solo ha empezado la primera derivación;
  4. al soltarla, el orden es `empieza 1, acaba 1, empieza 2, acaba 2`, el informe de la segunda aparece, las dos claves quedan a cero, la segunda contraseña también y `temp` queda vacía.
- `cancelWhileTheFolderIsMadeLeavesNothing` (`:360`): la comprobación y su IO comparten un solo hilo. Se cancela justo después de que corra el bloque que crea la carpeta y antes de que la comprobación siga, y no queda ninguna `run-…`.

## TDD

- **RED:** con el código anterior (sin `NonCancellable`, sin `Mutex` y con la carpeta creada fuera), `gradle -p jvm-check test --tests 'app.seguridad.vault.BackupCheckTest'` da:
  - `cancelDuringTheDerivationZeroesTheKey FAILED java.lang.AssertionError`;
  - `afterCancelTheNextDerivationWaits FAILED … La segunda espera a que acabe la primera expected:<[empieza 1]> but was:<[empieza 1, empieza 2, acaba 2]>`;
  - y, por separado, con la carpeta creada como antes: `cancelWhileTheFolderIsMadeLeavesNothing FAILED java.lang.AssertionError: Ninguna carpeta run-… queda`.
- **GREEN:** con los arreglos, `BackupCheckTest` queda en 18 de 18 y `BackupCheckSessionTest` en 7 de 7. `gradle -p jvm-check test --tests 'app.seguridad.vault.BackupCheck*' --tests 'app.seguridad.PlatformSourcesTest'` da BUILD SUCCESSFUL.

## Verificación, después del último cambio

- `export ANDROID_HOME=/opt/android-sdk; ./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --no-daemon --max-workers=1`: **BUILD SUCCESSFUL in 3m 34s**, con 1423 tests y 0 fallos. Entran todos los de importación (`BackupTest`, `VaultStoreTest`, `VaultV3Test`, `LegacyVaultTest`, `AutoBackupTest` y `AutoBackupF3Test`) y `PlatformSourcesTest`. Lint: 16 avisos, los mismos de siempre.
- `gradle -p jvm-check test --no-daemon --max-workers=1`: **BUILD SUCCESSFUL in 47s**, con 1418 tests y 0 fallos.

## Texto cambiado (para el Abogado Digital)

`BACKUP_CHECK_NOT_PROVEN` queda así: «No dice si es tu copia más reciente ni si tiene lo que cambiaste después de hacerla. No comprueba que quede espacio en este móvil para importarla. Tampoco prueba lo que solo hace la importación: si el coste de Argon2id elegido en este móvil es mayor que el de la copia, al importarla se vuelve a cifrar su clave con ese coste, y eso puede necesitar más memoria. Importar una copia desactiva la huella: después tendrás que volver a activarla.»

Es verdad por A8 (`Vault.kt:367-382`, el máximo eje por eje) y por `KDF_OUT_OF_MEMORY`. `SourceTextsTest` está en verde.

## Revisión experto-seguridad-cripto

- **La clave de datos de la copia se pone a cero en todos los caminos**, también al cancelar durante Argon2id (`BackupCheck.kt:89-91` y `:116`). Lo comprueba `cancelDuringTheDerivationZeroesTheKey`.
- **Nunca hay dos Argon2id de comprobación a la vez en el proceso** (`BackupCheck.kt:52` y `:89`): la memoria queda acotada a una sola derivación de ≤ 128 MiB. Lo comprueba `afterCancelTheNextDerivationWaits`.
- **No queda nada temporal**, ni siquiera al cancelar mientras se crea la carpeta (`BackupCheck.kt:80` y `:117`).
- **Sin primitivas nuevas ni cambios de formato.**

## Revisión Ingeniero Móvil Senior

- **Cancelación:** los bordes no cancelables siguen el patrón de `VaultStore.derive` (`VaultStore.kt:312`), y la espera del `Mutex` sí se puede cancelar.
- **El selector:** igual que el de «Importar copia» (`BackupCheckScreen.kt:222-225`, `VaultBackup.kt:201-210`), y `MainActivity.onStart` quita la marca (`MainActivity.kt:60`). Así, elegir la copia ya no bloquea la bóveda abierta.

---

# Ronda de arreglos 2 (revisión `task-5-rereview-1.md`)

**Commit:** `5958932 1.4.0: Comprobar una copia, segunda ronda de arreglos de la tarea 5`, subido a `origin/ccr-a1a6eabb-765f9g`.

## 1. Los textos que decían «no escribe nada» (R6: `touch()` se queda)

`touch()` puede guardar la marca de reloj de A11 (`Settings.clockHighWaterMs`). Por eso he cambiado los textos que afirmaban que no se escribe nada:

- **`BACKUP_CHECK_INTRO`** (`BackupCheckTexts.kt`). Antes: «… No cambia nada: no toca tu bóveda, tus ajustes ni tus copias. Mientras la comprueba…». Ahora: «Comprueba una copia .sgbak sin importarla: que la contraseña maestra que escribas la abre, que sus entradas se leen y que cada adjunto está en la copia y coincide con la huella SHA-256 que guarda su entrada. No cambia tu bóveda ni tus copias. Mientras la comprueba, guarda lo que lee en una carpeta temporal de la app y lo borra al terminar.»
- **La KDoc de `BackupCheckViewModel`** (`BackupCheckScreen.kt:84-95`) dice ahora exactamente lo que guarda `touch()`: reinicia en memoria la cuenta de inactividad y puede subir la marca de A11 (`Settings.clockHighWaterMs`), que solo se guarda en disco cuando sube lo bastante y nunca baja, como en cualquier uso de la app.
- **El comentario de `SettingsScreen.kt:468`** y el nombre y el comentario del test de fuentes ya no dicen «writes nothing»: ahora dicen «changes neither the vault nor a copy».
- La KDoc de `checkBackup` («Writes nothing but that folder») no cambia: es cierta, porque `checkBackup` no llama a `touch()`.
- Ningún test fijaba el texto anterior. `SourceTextsTest` y `FinalLegalReviewTest` están en verde.

## 2. El test del selector ya no depende del espaciado

En `PlatformSourcesTest` (en el test «Comprobar una copia …»), los espacios se reducen a uno antes de buscar los cuatro pasos en orden:

1. `VaultLock.keepOpenOnce = true`;
2. `store.touch()`;
3. `pickerError = pick.launchOrError(arrayOf("*/*"))`;
4. `if (pickerError != null) VaultLock.keepOpenOnce = false`.

Se siguen comprobando que hay una sola llamada a `store.touch()` y un solo `keepOpenOnce = true`, que el patrón de «Importar copia» es el mismo y que `MainActivity.onStart` vuelve a poner la marca a `false`.

**Mutaciones** (cada una con `gradle -p jvm-check test --tests 'app.seguridad.PlatformSourcesTest'`, sobre `BackupCheckScreen.kt`, y luego restaurado):

| Cambio | Resultado |
|---|---|
| Partir `pickerError =` en dos líneas (reformateo) | PASSED |
| Quitar `VaultLock.keepOpenOnce = true` | FAILED |
| Quitar la línea `if (pickerError != null) VaultLock.keepOpenOnce = false` | FAILED |
| Quitar `store.touch()` | FAILED |
| El original | PASSED |

## Verificación, después del último cambio

- `gradle -p jvm-check test --tests 'app.seguridad.PlatformSourcesTest' --tests 'app.seguridad.vault.BackupCheck*' --tests 'app.seguridad.tools.SourceTextsTest' --tests 'app.seguridad.tools.FinalLegalReviewTest'`: BUILD SUCCESSFUL, 72 tests en verde.
- `export ANDROID_HOME=/opt/android-sdk; ./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --no-daemon --max-workers=1`: **BUILD SUCCESSFUL in 2m 31s**, con 1423 tests y 0 fallos. Lint: los 16 avisos de siempre.
- `gradle -p jvm-check test --no-daemon --max-workers=1`: **BUILD SUCCESSFUL in 46s**, con 1418 tests y 0 fallos.

## Texto cambiado, para el Abogado Digital

`BACKUP_CHECK_INTRO` ahora dice «No cambia tu bóveda ni tus copias.» (antes: «No cambia nada: no toca tu bóveda, tus ajustes ni tus copias.»). No hay palabras prohibidas.

## Revisión experto-seguridad-cripto

Sin cambios de código con efecto en la seguridad. El texto ya no dice más de lo que hace el código, y A11 no se debilita, porque `touch()` solo sube la marca.

## Revisión Ingeniero Móvil Senior

El test de fuentes ya no falla por un cambio de formato, y sí falla si se quita cualquiera de los cuatro pasos del selector. Lo muestran las mutaciones de arriba.
