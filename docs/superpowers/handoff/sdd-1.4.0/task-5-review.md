# Tarea 5: Comprobar una copia, revisión de la tarea (2db90e1..d27b5e2)

### Spec Compliance

- ✅ **Cumple la especificación**, con la excepción documentada de R5.
  - `BackupCheckReport` y `checkBackup(input, master, tempDir)` están en `BackupCheck.kt:34` y `:54`. Los dos dispatchers añadidos tienen valor por defecto.
  - Están los siete tests del brief:
    - `goodBackupReports`, `changedAttachmentIsReported`, `missingAttachmentIsReported`, `wrongPasswordThrows`, `newerVersionThrowsNewerMessage`, `tempFilesAreDeleted` y `vaultIsUntouched`, en `BackupCheckTest.kt`;
    - más ocho de casos de abuso.
  - Están la pantalla con `OPEN_DOCUMENT` (`BackupCheckScreen.kt:209`), `checkBackup` en IO dentro de `cacheDir/comprobar/` (`BackupCheckScreen.kt:176-182`) y la fila de Ajustes (`SettingsScreen.kt:469`).
  - `importReplace` usa `checkCopyEntries` y `attachmentInCopy` (`VaultStore.kt:845` y `:852`), como permite R5.
  - Lo que se añadió de más es lo que exigen los puntos (a) y (b) del dueño, o lo que pide el informe: `BoundedInput`, `BackupCheckSession`/`BackupCheckRuns`, `BackupContents.version` y `removeBackupCheckLeftovers`.
- ⚠️ **No se puede comprobar con el diff:**
  - el tamaño real de una copia escrita con la zlib de Android. El cálculo de abajo da un margen amplio, pero el test `aCopyAtTheLimitsIsStillRead` usa la zlib de la JVM;
  - cómo se comporta en el móvil elegir el archivo con la bóveda abierta. El selector no pone `keepOpenOnce`, así que el bloqueo al salir y el guard pueden borrar el archivo elegido. Es lo mismo que pasa con las demás herramientas, y conviene probarlo una vez en el móvil.

### Comprobaciones hechas (riesgo con nombre → qué miré)

**R5: que las dos rutas no se separen.** Comparé el `checkRestorable` y el `checkInBackup` eliminados con `checkCopyEntries` (`BackupCheck.kt:141-153`) y `attachmentInCopy` (`:163-171`).
- Hacen las mismas reglas en el mismo orden: identidad, `isEntryId`, ids repetidos, `isPersonal` y tamaños.
- Dan los mismos mensajes: `SEVERAL_IDENTITIES`, `COPY_DAMAGED`, `ATTACHMENTS_TOTAL_TOO_BIG` y el texto con «falta o no coincide el adjunto «…»».
- `attachments.totalBytes(refs)` pasa a ser `refs.sumOf { it.size }`, y es lo mismo: `Attachments.kt:161`.
- El orden es igual en las dos: leer, `decodeFile`, `unlock`, `read`, `checkCopyEntries` y los adjuntos.
- Hay una diferencia más que R5 no recoge: `importReplace` también llama a `afterMasterUnlock` (`VaultStore.kt:840`), y `checkBackup` no. Lo miré en `Vault.kt:367-382`:
  - `resolve` y `read` usan el mismo `openAny` que `read`, así que dan el mismo error;
  - `write` no puede lanzar en ese camino;
  - y en `wrap` la contraseña ya pasó `unlock` con `PasswordUse.EXISTING`.

  Lo único que puede fallar solo en la importación es un `KDF_OUT_OF_MEMORY` al volver a envolver con un coste mayor (A8). Es lo que se espera de una pantalla que no escribe nada, pero la KDoc dice algo más (Minor m2).
- `theChecksAreTheImportsOwn`, `wrongPasswordThrows`, `newerVersionThrowsNewerMessage` y `missingAttachmentIsReported` comparan con los mensajes reales de `importReplace`, así que vigilan bien que las dos rutas no se separen.

**(1) La importación no cambia para ninguna copia real.**
- El A8, la huella después de guardar, `autoBackupReplace`, los mensajes de versión más nueva y la limpieza de `temp` no cambian. El diff de `replace()` solo toca las dos llamadas: `VaultStore.kt:836-857`.
- **El límite de 110 MiB.** El escritor es el mismo, byte a byte, en v1.1.0, v1.1.1, v1.2.0, v1.2.2 y HEAD (lo comparé con `git show <tag>:…/Backup.kt`). La 1.0.x solo escribía JSON, que no pasa por `BackupReader`. Ese escritor:
  - pone como mucho 100 MiB de contenido, con el marcador, el JSON y los adjuntos sumados en `total` (`Backup.kt:109-133`);
  - escribe como mucho 2 000 entradas;
  - guarda los adjuntos en bloques *stored* y el JSON comprimido con el nivel por defecto.

  El peor caso suma:

  | Parte | Bytes |
  |---|---|
  | Contenido | 104 857 600 |
  | Cabeceras ZIP (LOC 30 + 40, descriptor de datos 16, CEN 46 + 40), por 2 000 entradas | 344 000 |
  | EOCD | 22 |
  | Bloques *stored* de zlib: 5 B cada ≤ 32 KiB, unos 3 300 bloques | ≈ 16 000 |
  | El bloque final de cada entrada | ≈ 10 000 |
  | La expansión del JSON en el peor caso (≤ 6 MiB) | ≈ 2 000 |
  | **Total** | **≈ 105,23 MB** |

  El límite es de 115 343 360 B, así que el margen es de unos 10,1 MB, unas 27 veces el exceso real. Aun suponiendo bloques de 512 B (un 1 %), se quedaría en unos 106,3 MB. Los tamaños nunca llegan a ZIP64. **El margen se mantiene.**
- **El límite cuenta los bytes de entrada.** `BoundedInput` va debajo de `ZipInputStream` (`Backup.kt:227`) y cuenta `read`, `read(byte[])` y `skip`. Lo que `PushbackInputStream` devuelve no se vuelve a pedir a `BoundedInput`. El test de bloques vacíos (`aForgedSizeIsRefusedBeforeTheWork`) termina entre MAX y MAX + 64 KiB con `BACKUP_TOO_BIG`. Lo ejecuté: 15 de 15 en verde.

**(2) Entradas enormes o maliciosas.**
- Un archivo de ruido no pasa de la primera LOC.
- Los tamaños de una cabecera falsa nunca se usan para reservar memoria: solo cuentan los bytes leídos (6 MB por entrada, 100 MB en total y 110 MiB del archivo).
- Hay 2 000 entradas como mucho.
- El trabajo de los hashes está acotado antes de empezar por `checkCopyEntries`: 50 MB declarados, incluso con referencias repetidas. Además, la longitud se compara antes de calcular el hash (`BackupCheck.kt:169`).
- Se lee con búferes de 64 KiB y ningún adjunto entra entero en memoria.
- `check()` va antes de cada lectura, y nada corre en el hilo principal.
- En el disco temporal hay como mucho 100 MiB por ejecución, pero ver I2: puede haber dos ejecuciones a la vez.

**(3) La ventana entre comprobar y usar (TOCTOU).**
- `importReplace` lee una sola vez, en una carpeta privada (`VaultStore.kt:821-824`), y mueve esos mismos archivos (`moveIn`).
- Cada ejecución tiene su carpeta, creada con `Files.createTempDirectory`, y la borra en un `finally` `NonCancellable` (`BackupCheck.kt:97`).
- Los hashes se comparan con `MessageDigest.isEqual` (`Backup.kt:170`).
- `BackupCheckRuns` no tiene ningún lock, así que no puede bloquearse. Pero la serialización no hace lo que dice (I2), y una cancelación en el borde de un `withContext` deja la clave sin poner a cero (I1) y, a veces, una carpeta vacía (m1).

**(4) Secretos.**
- El `CharArray` se pone a cero en `stop()` y en el `finally` (`BackupCheckSession.kt:77` y `:107`).
- La sesión vive en un ViewModel, sin `rememberSaveable` (lo comprueba el test de fuentes).
- El informe solo da números, con `EntryType.label`.
- Los errores son constantes, sin rutas ni nombres, y no hay logs.
- Pregunta: ¿se puede usar como oráculo de la maestra con la bóveda bloqueada? La bóveda no limita los intentos (no hay retardo ni contador en `VaultStore` ni en `VaultScreen`), así que no se salta nada. Y quien tenga el `.sgbak` puede atacarlo sin conexión mucho más rápido.

**(5) La pantalla no escribe nada.**
- `BackupCheck.kt` y `BackupCheckSession.kt` no hacen referencia a `VaultStore`.
- La pantalla solo usa `store.state` y `store.lockEpoch`, con un test de fuentes (`PlatformSourcesTest.kt:221`).
- `vaultIsUntouched` comprueba la revisión, la generación y los bytes.
- Sin bóveda, o con la bóveda bloqueada, funciona sin peligro, y es coherente con «Importar copia», que también deja leer el archivo antes de pedir la contraseña. Igual que `mayBeVaultImport`, mira la cabecera antes de pedir la contraseña (`BackupCheckScreen.kt:148-160`). Es más estricta (solo `.sgbak`) y lo dice (`BACKUP_CHECK_NOT_SGBAK`).

**(6) Los tests de guarda que se cambiaron.**
- **`DocumentUriTest` pasa de 25 a 26.** Hay exactamente un selector nuevo, y pasa por `rememberDocumentPicker` y `launchOrError`. El escaneo de selectores sin guarda no cambia.
- **`MasterPolicyTest`.** La lista sigue siendo exacta (`["BackupCheckScreen.kt", "VaultBackup.kt"]`), con una sola coincidencia en la pantalla y con la expresión exacta. Pedir la contraseña de una copia que ya existe no es una maestra nueva, como dice CRY-A01.
- Ninguno de los dos cambios debilita su guarda.

**(7) Textos.**
- No hay palabras prohibidas, ni «wallet» ni «seguro».
- «huella SHA-256» sigue el uso de la app («huella SHA-1» en `SettingsScreen.kt:518`).
- `BACKUP_CHECK_NOT_PROVEN` dice lo que no prueba, y que la importación desactiva la huella, que es verdad por CRY-A02.

### Strengths

- Las reglas de entrada y de adjuntos se extraen de verdad a una sola implementación. Los tests comparan los mensajes con `importReplace` real, no con constantes copiadas.
- El límite de bytes de entrada va en el sitio correcto, por debajo del ZIP, cubre `skip` y no cambia nada para las copias reales.
- Los tests de abuso son de verdad: un flujo de ruido sin fin, una LOC falsa de 4 GB, bloques vacíos sin fin, 2 000 entradas y una copia real en el límite.
- `BackupCheckRuns` combina el número de ejecución y la época. Cancelar, salir, bloquear o borrar la sesión nunca deja ver un resultado tardío, y los tests lo comprueban con el planificador.
- La carpeta temporal es atómica y propia de cada ejecución. El registro de las que están en uso impide que la limpieza del arranque borre una ejecución en marcha.

### Issues

#### Critical (Must Fix)

Ninguno.

#### Important (Should Fix)

**I1. La clave de datos (DEK) no se pone a cero si se cancela durante Argon2id.** `BackupCheck.kt:69`.

`val key = withContext(compute) { VaultCrypto.unlock(incoming, master) }` es cancelable. `withContext` tiene garantía de cancelación inmediata: si el trabajo se cancela mientras corre Argon2id, que no se puede cortar, el bloque devuelve la DEK y `withContext` la descarta y lanza `CancellationException`. `key` no llega a asignarse, y el `finally { key.fill(0) }` de `:93-95` no la cubre.

Ese es justo el caso del punto (b): «Cancelar», salir de la app, bloquear o volver atrás mientras se deriva. La DEK de una copia hecha con `exportBackup` es la DEK de la bóveda: `afterMasterUnlock` la conserva y solo `changeMaster` la cambia. Así que la DEK se queda en el montón, sin poner a cero, después del `dek.fill(0)` que hace `VaultStore.lock` (`VaultStore.kt:546`).

`importReplace` evita esto con `derive { … }` (`VaultStore.kt:312` y `:837`, `NonCancellable`). El informe dice que «la clave de datos se pone a cero en un finally», y no es así en este camino. Por la regla de `experto-seguridad-cripto`, un hallazgo de seguridad confirmado es como mínimo Important.

**Arreglo:** `val key = withContext(NonCancellable + compute) { VaultCrypto.unlock(incoming, master) }`. El `ensureActive()` de `:71`, ya dentro del `try`, cancela después. Se puede añadir un test: cancelar mientras dura un `unlock` lento (con un `compute` que espere una señal) y comprobar que la clave devuelta queda a cero. Para hacerlo hay que poder observar la clave, por ejemplo con un parámetro `unlock` inyectable.

**I2. «Nunca dos Argon2id a la vez» no se cumple en el uso real.** `BackupCheckSession.kt:73-80` y `:84-95`.

`start()` toma `previous = job`, pero `stop()` pone `job = null` (`:75`). En la pantalla, el botón «Comprobar» está desactivado mientras `busy` (`BackupCheckScreen.kt:168`). Por eso **solo** se puede empezar una segunda ejecución después de un `stop()`: «Cancelar», `pick()` (que llama a `stopAll`), `leftApp` o un bloqueo. Y entonces `previous` siempre es `null`.

Pasos para reproducirlo:
1. Comprobar.
2. Pulsar «Cancelar» mientras corre Argon2id.
3. Pegar la contraseña.
4. Comprobar.

La segunda derivación empieza mientras la primera sigue. Además, si una ejecución que espera en `join()` se cancela, sale en seguida, porque `join` es cancelable, y la cadena se rompe.

`aNewRunWaitsForTheOneItStopped` solo prueba un `start()` llamado dentro de otro, sin un `stop()` entre los dos. Ese camino no se puede alcanzar desde la interfaz.

Consecuencias:
- dos Argon2id de hasta 128 MiB a la vez (más un posible desbloqueo de la bóveda) con un montón de 192 a 256 MiB. Puede dar `KDF_OUT_OF_MEMORY`, o un `OutOfMemoryError` en otra parte de la app, donde no se convierte y la cierra;
- hasta 200 MiB temporales a la vez;
- y no se cumple lo que el informe promete para el punto (b).

**Arreglo:**
- guardar una `tail: Job?` que `stop()` no borre, asignarla en cada `launch` y esperar con `withContext(NonCancellable) { previous?.join() }`, para que la cadena se mantenga aunque se cancele la que espera;
- o un `Mutex` de proceso alrededor de la derivación de `checkBackup`.

Test: `start` → `stop` → `start`, con la primera en `NonCancellable`, y comprobar el orden.

#### Minor (Nice to Have)

**m1. Puede quedar una carpeta vacía de una ejecución.** `BackupCheck.kt:61`.

`val dir = withContext(io) { newRunDir(tempDir) }` va antes del `try`. Si se cancela mientras corre `newRunDir`, la carpeta se crea y se apunta en `activeRuns`, pero la garantía de cancelación inmediata descarta `dir` y el `finally` nunca la borra. Queda una carpeta vacía `run-…`, que `removeBackupCheckLeftovers` no toca mientras viva el proceso, porque está «en uso», y una entrada en `activeRuns` que no se va. El punto (b) pide no dejar nada temporal.

**Arreglo:** `withContext(NonCancellable + io) { newRunDir(tempDir) }`, que es el mismo patrón de I1.

**m2. La KDoc de `BackupCheckReport` dice más de lo que hace.** `BackupCheck.kt:31-32`.

Dice: «No problems means the copy passes every check the import makes before replacing the vault». Pero la importación también:
- vuelve a envolver con `afterMasterUnlock`, con un segundo Argon2id que puede dar `KDF_OUT_OF_MEMORY` cuando el perfil cuesta más (A8);
- exige la bóveda abierta o que no haya bóveda (`checkCanReplace`);
- y guarda los ajustes (`IMPORT_SETTINGS_FAILED`).

Los textos de la interfaz no dicen esto, así que solo hay que arreglar la documentación: «every check of the copy's contents the import makes» y nombrar la nueva envoltura de A8 como lo que no se comprueba. Además, R5 debería recoger esta segunda diferencia junto a la de «cuenta y no nombra».

### Assessment

**Task quality:** Needs fixes

**Reasoning:** La extracción compartida, los límites y la vigilancia de los mensajes son correctos, y el límite de 110 MiB es seguro para todas las copias reales. Pero el caso concreto del punto (b) del dueño, la cancelación durante Argon2id, deja la DEK sin poner a cero (I1). Y la serialización «una derivación cada vez» no se usa en el único camino que la interfaz permite (I2). Los dos arreglos son pequeños.

## Revisión experto-seguridad-cripto

- **Primitivas:**
  - solo `VaultCrypto` (AES-256-GCM y Argon2id de BC) y SHA-256 de `MessageDigest`, sin cambios;
  - la comparación es en tiempo constante (`Backup.kt:170`);
  - no hay criptografía propia ni dependencias nuevas;
  - no hay código copiado de terceros.
- **Formatos:** `seguridad-backup` v1 y v2 y `seguridad-vault` v2 y v3 no cambian. Solo se lee el marcador para dar la versión (`Backup.kt:275`). v1 se comprueba en `goodBackupReports`.
- **Límites (a):**
  - 110 MiB de entrada (`Backup.kt:50` y `:187-211`), demostrados con el cálculo del escritor desde v1.1.0;
  - 6 MB por entrada, 100 MB en total y 2 000 entradas (`Backup.kt` `copy()` y `:265`);
  - `checkCopyEntries`, con 5 MB por adjunto y 50 MB en total, antes de los hashes (`BackupCheck.kt:149-152`);
  - `checkSane`, con Argon2id ≤ 128 MiB, 10 pasadas y 4 hilos (`Crypto.kt:62-74`).
- **Secretos:**
  - el `CharArray` se pone a cero (`BackupCheckSession.kt:77` y `:107`);
  - **la DEK no se pone a cero si se cancela durante la derivación (I1, `BackupCheck.kt:69`)**;
  - el JSON descifrado se pone a cero en `Vault.kt:283-291`;
  - las entradas descifradas son Strings, en el montón hasta la recogida de basura, como en la importación;
  - no hay logs, y los mensajes son constantes.
- **TOCTOU (b):**
  - la importación usa una sola lectura (`VaultStore.kt:824`, `:852` y `moveIn`);
  - el resultado tardío se descarta (`BackupCheckSession.kt:117`);
  - **la serialización de Argon2id falla después de un `stop()` (I2)**.
- **Oráculo de la maestra con la bóveda bloqueada:** no se salta ningún límite, porque la bóveda no limita los intentos. Quien tenga el `.sgbak` puede atacarlo sin conexión y más rápido. Se acepta.
- **Coherencia con la importación:** las reglas y los mensajes son los mismos (`VaultStore.kt:845` y `:852`; `BackupCheck.kt:141-171`). La diferencia de A8, la nueva envoltura, se apunta en m2.

## Revisión Ingeniero Móvil Senior

- **Ciclo de vida:**
  - el ViewModel está atado a la entrada de navegación;
  - `onCleared` llama a `stopAll` y a `wipe` (`BackupCheckScreen.kt:190-193`);
  - `ON_STOP` no actúa al girar la pantalla (`:206`);
  - el guard es el de las otras herramientas: `watchAway` y `GuardEffects` (`ScreenGuardWiring.kt:47` y `:69`).
- **Concurrencia:**
  - `viewModelScope` en el hilo principal, IO para el SAF y el ZIP, y Default para la KDF;
  - el registro `activeRuns` está `synchronized` (`BackupCheck.kt:103-127`);
  - hay una garantía de cancelación inmediata en dos bordes de `withContext` (I1 y m1), y `join` es cancelable y `previous` es nulo después de `stop()` (I2).
- **Memoria:** dos Argon2id a la vez son posibles después de «Cancelar» (I2). Un `OutOfMemoryError` fuera de `outOfMemoryAsFailure` no se captura: `BackupCheckSession.kt:103` solo captura `Exception`.
- **ANR:** el nombre y la cabecera se leen en IO (`BackupCheckScreen.kt:144-153`), y la comprobación en IO y Default. En el hilo principal solo está el acceso a `cacheDir`, como en el resto de pantallas.
- **Arranque:** la limpieza va en `appScope` IO (`SeguridadApp.kt:63`).
- **Selector:** `rememberDocumentPicker(OpenDocument)` con `*/*` y `launchOrError` (`BackupCheckScreen.kt:209` y `:216`). Falta probar en el móvil cómo se comporta con la bóveda abierta, porque no hay `keepOpenOnce` (⚠️).
- **Accesibilidad:** el informe y los errores están en una `liveRegion` educada (`BackupCheckScreen.kt:234` y `:246`).
- **Plataforma:** no hay permisos, componentes exportados, dependencias ni hosts nuevos.
- **Tests ejecutados:**
  - `gradle -p jvm-check test --tests 'app.seguridad.vault.BackupCheckTest' --tests 'app.seguridad.vault.BackupCheckSessionTest'`;
  - resultado: 15/15 y 8/8 en verde, con 0 fallos (`jvm-check/build/test-results/test/TEST-app.seguridad.vault.BackupCheck*.xml`), después del cambio del `lateinit`.

## Textos para el Abogado Digital (revisión final)

Todos están en `app/src/main/java/app/seguridad/vault/BackupCheckTexts.kt`:

- `BACKUP_CHECK_TITLE` «Comprobar una copia»: la fila de Ajustes y el título de la pantalla.
- `BACKUP_CHECK_INTRO`: «Comprueba una copia .sgbak sin importarla: …No cambia nada: no toca tu bóveda, tus ajustes ni tus copias. Mientras la comprueba, guarda lo que lee en una carpeta temporal de la app y lo borra al terminar.»
  - **Nota técnica:** es verdad por `endRun` y `removeBackupCheckLeftovers`, pero ver m1, que hay que arreglar para que siga siéndolo en todos los casos.
- `BACKUP_CHECK_PICK`, `BACKUP_CHECK_PASSWORD`, `BACKUP_CHECK_RUN`, `BACKUP_CHECK_CANCEL` y `BACKUP_CHECK_BUSY`.
- `BACKUP_CHECK_TOO_LONG`, `BACKUP_CHECK_NOT_SGBAK`, `BACKUP_CHECK_READ_FAILED` y `BACKUP_CHECK_FAILED`.
- `BACKUP_CHECK_RESULT_TITLE`, `BACKUP_CHECK_OK` y `BACKUP_CHECK_HAS_PROBLEMS`.
- `backupCheckVersion`, `backupCheckEntries`, `backupCheckType`, `backupCheckAttachments`, `backupCheckMissing` y `backupCheckChanged`.
- `BACKUP_CHECK_NOT_PROVEN_TITLE` y `BACKUP_CHECK_NOT_PROVEN`: «No dice si es tu copia más reciente… No comprueba que quede espacio… Importar una copia desactiva la huella…».
  - **Propuesta técnica:** decir también que la importación vuelve a comprobarlo todo, y que puede necesitar más memoria si el coste de Argon2id del móvil es mayor (m2).
- `BACKUP_CHECK_STOPS_NOTE`: «…la comprobación se para y no se muestra su resultado.»
  - **Nota técnica:** si en ese momento corre Argon2id, termina por dentro, unos segundos, y su resultado se descarta. Con I2 arreglado, la siguiente comprobación espera. «Se detiene en cuanto puede» sería más exacto, si se prefiere.
