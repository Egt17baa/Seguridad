# Tarea 5: Comprobar una copia, re-revisión de la ronda de arreglos 1 (d27b5e2..22cee96)

### Finding Verdicts

- **I1. La DEK no se pone a cero si se cancela durante Argon2id** — ADDRESSED.
  - `BackupCheck.kt` (diff): `key` es una `var` externa; el `unlock` se asigna dentro de `withContext(NonCancellable) { withContext(compute) { key = unlock(...) } }`. Una cancelación en el borde no pierde la referencia, y el `finally` hace `key?.fill(0)` en cualquier salida (éxito, excepción o cancelación).
  - Cancelación durante Argon2id: el bloque no es cancelable, termina, asigna `key`; `ensureActive()` lanza después y el `finally` la pone a cero. Si `unlock` lanza, `key` queda `null` y no hay nada que poner a cero. Si se cancela antes o mientras se espera el lock, aún no hay clave.
  - Contraseña: la copia que pasa `unlock` la pone a cero `BackupCheckRuns.stop()`/`finally` (`BackupCheckSession.kt`). Que `stop()` la ponga a cero mientras Argon2id corre solo puede dar un `CryptoFailure` de una ejecución ya descartada (`current(mine, epoch)` es falso).
  - El test `cancelDuringTheDerivationZeroesTheKey` cancela de verdad en mitad de la derivación: `unlock.started.await()`, luego `job.cancel()`, y solo después `release.countDown()`. Comprueba `["empieza 1","acaba 1"]` y que la clave devuelta (`unlock.keys.single()`) está a cero. Con el código viejo la clave devuelta no se habría puesto a cero, así que el test falla sin el arreglo.
- **I2. «Nunca dos Argon2id a la vez» no se cumple tras `stop()`** — ADDRESSED.
  - Un `Mutex` de proceso (`derivations`) rodea la derivación, así que no depende de que `BackupCheckRuns` conserve el `Job` anterior. Se quitó el `previous?.join()` que `stop()` dejaba inútil.
  - Camino real (comprobar, Cancelar, comprobar): el primero sigue dentro del bloque `NonCancellable` con el lock; el segundo se suspende en `lock()` (no bloquea ningún hilo) hasta que acabe. `afterCancelTheNextDerivationWaits` hace exactamente ese recorrido con `BackupCheckRuns`, comprueba tras 300 ms que solo consta `empieza 1` y después el orden completo `1,1,2,2`.
  - Bloqueo mutuo: el titular del lock solo espera a un hilo de `Dispatchers.Default` (no al principal), y nadie que espere el lock tiene un hilo ocupado. `withLock` libera en `finally`, también si `unlock` lanza o si la cancelación llega con el lock ya tomado; cancelar mientras se espera el lock lo abandona sin tomarlo. No hay un segundo lock que se anide, así que no hay ciclo.
  - Efecto secundario aceptado: «Comprobar» tras «Cancelar» puede tardar unos segundos (la espera es acotada por un Argon2id de ≤128 MiB).
- **m1. Puede quedar una carpeta vacía tras cancelar** — ADDRESSED.
  - `dir` se asigna dentro de `withContext(NonCancellable) { withContext(io) { dir = newRunDir(tempDir) } }`, y el `finally` hace `dir?.let { ... endRun(d) }` en `NonCancellable`. `endRun` quita la entrada de `activeRuns` en su propio `finally`.
  - `cancelWhileTheFolderIsMadeLeavesNothing` cancela justo después de que el bloque de `io` crea la carpeta y antes de que la continuación siga (un solo hilo), y comprueba que no queda nada. Reproduce el hueco del hallazgo.
  - Observación menor: la segunda parte del test (`run-1` + `removeBackupCheckLeftovers`) prueba poco sobre `activeRuns`, porque el nombre no coincide con el de la ejecución. No resta al arreglo.
- **m2. KDoc y texto `BACKUP_CHECK_NOT_PROVEN`** — ADDRESSED.
  - KDoc de `BackupCheckReport`: lo que dice es verdad por el código. A8 y `afterMasterUnlock` vuelven a envolver con el máximo, eje por eje (`Vault.kt:367-378`); `importReplace` exige la bóveda abierta o inexistente (`checkCanReplace`); `KDF_OUT_OF_MEMORY` habla de «Alto» (`Crypto.kt:81`). La KDoc de `checkBackup` también es verdad: `unlock` solo lo cambian los tests, una derivación a la vez y la clave siempre a cero.
  - Texto `BACKUP_CHECK_NOT_PROVEN`: es cierto (el nuevo envoltorio lo hace `afterMasterUnlock` en la importación) y no contiene ninguna palabra de `ForbiddenWords` (`anónim`, `imposible`, `100 %`, `de verdad`, `validez legal`, `sospechos`, limpio/a, seguro/a, `garantiza`). Imprecisión menor: «si el coste... es mayor» es en realidad «mayor en memoria o en pasadas» (`Vault.kt:370`); no es falso.
- **m3. El selector sigue a «Importar copia» y añade `store.touch()`** — ADDRESSED.
  - `BackupCheckScreen.kt:222-226` repite línea a línea el patrón de `VaultBackup.kt:201-208`: `keepOpenOnce = true`, `touch()`, `launchOrError`, y vuelve a `false` si el selector no abrió. `MainActivity.onStart` (línea 60) lo devuelve a `false` al volver. Como en «Importar copia», el callback del selector no lo reinicia.
  - Qué guarda `touch()` (`VaultStore.kt:555-558`): `lastUse = elapsed()` en memoria (el contador de inactividad) y `onClockSeen(now())`. Este último es `AppContainer.rememberClock`, que sí puede escribir en disco `Settings.clockHighWaterMs` (la marca A11), pero solo la **sube** (`seeingClock`, `AppContainer.kt:206-209`) y solo cuando la subida supera `CLOCK_WRITE_STEP_MS` (`clockMarkWorthWriting`, `saveClock`). Nunca la baja: eso solo lo hace `resetClock` tras comprobar la maestra. Es lo mismo que hace cualquier uso de la app y que ya hace «Importar copia»; no debilita A11 ni toca la bóveda, la huella o las copias.
  - `vaultIsUntouched` sigue valiendo: llama a `checkBackup` directamente, no pasa por la pantalla, y comprueba la revisión, la generación y los bytes de la bóveda, que `touch()` no cambia.
  - Sin bóveda o con la bóveda bloqueada: `touch()` solo fija `lastUse` y llama a `onClockSeen`, sin lanzar. `lockIfIdle` exige `dek != null` (`VaultStore.kt:579`), así que no desbloquea nada ni se puede usar para eso.

### New Breakage in the Fix Diff

- **Minor, precisión de la frase «no escribe nada».** `touch()` puede escribir la marca A11 en los ajustes. El brief, la KDoc de `BackupCheckViewModel` («which is all the screen does to the store») y `BACKUP_CHECK_INTRO` («no toca ... tus ajustes») son, en sentido estricto, un poco más fuertes que el código. La marca es un contador interno que solo sube y que la app escribe en cualquier uso; no es un ajuste del usuario ni un cambio de datos. Sugerencia sin urgencia: en la KDoc, «restarts its idle count (and may raise the clock mark, as every use does)». El texto de la interfaz puede quedarse como está; que lo vea el Abogado Digital.
- **Minor, test del selector.** El test de fuentes (`PlatformSourcesTest`) fija el espaciado exacto de cuatro líneas; si se reformatea, falla sin que haya un cambio de comportamiento. Es frágil pero no es un error.
- Nada Critical ni Important. Sin TOCTOU nuevo: la carpeta es de la ejecución y atómica, y se borra en `finally` no cancelable. Sin entradas enormes nuevas: no se tocó ningún límite de lectura.

### Out-of-Scope Observations

- El `OutOfMemoryError` fuera de `outOfMemoryAsFailure` sigue sin capturarse en `BackupCheckSession` (ya lo apuntó la revisión). Con el `Mutex` es menos probable, pero queda.

### Verdict

**Fix round:** All findings addressed, no new Critical/Important breakage. Quedan dos Minor (precisión de la frase «no escribe nada» y fragilidad del test de fuentes).

Tests: no se ejecutó nada; leí el diff y el código de apoyo (`VaultStore.touch`, `AppContainer.saveClock`, `Vault.afterMasterUnlock`, `MainActivity.onStart`, `VaultBackup.kt`). El informe de arreglos nombra `BackupCheckTest` y `BackupCheckSessionTest`.

## Revisión experto-seguridad-cripto

- **Secretos:** la clave de datos de la copia se pone a cero en toda salida (`BackupCheck.kt`, `finally`: `key?.fill(0)`), también con una cancelación en mitad de Argon2id; `BackupCheckTest.cancelDuringTheDerivationZeroesTheKey`. La contraseña se pone a cero en `BackupCheckRuns.stop()` y al acabar (`BackupCheckSession.kt`).
- **Primitivas:** no hay cambios; sigue `VaultCrypto.unlock` (Argon2id de BC y AES-GCM). El parámetro `unlock` inyectable solo lo usan los tests; el valor por defecto es el real.
- **Una derivación a la vez:** `derivations` (Mutex) en `BackupCheck.kt`; sin bloqueo mutuo (el titular solo espera a `Default`, nunca al hilo principal; `withLock` libera en `finally`).
- **A11 (marca de reloj):** `touch()` solo sube la marca (`AppContainer.kt:206-209`, `resetClock` aparte), igual que «Importar copia»; no la baja ni desbloquea.
- **TOCTOU:** una carpeta por ejecución, creada con `Files.createTempDirectory`, borrada en un `finally` no cancelable (`BackupCheck.kt`, `endRun`), sin carpetas huérfanas tras una cancelación.
- **Textos:** `BACKUP_CHECK_NOT_PROVEN` es verdad por `Vault.kt:367-378`; sin palabras prohibidas.

## Revisión Ingeniero Móvil Senior

- **Concurrencia:** `NonCancellable` solo envuelve la creación de la carpeta y la derivación (trabajo que no se puede cortar), y los resultados se asignan a variables locales, así que la garantía de cancelación inmediata de `withContext` no los pierde. `ensureActive()` tras cada bloque da la cancelación (`BackupCheck.kt`). `Mutex.lock` es cancelable y no ocupa un hilo.
- **Ciclo de vida del selector:** `BackupCheckScreen.kt:222-226` copia `VaultBackup.kt:201-208`; `MainActivity.kt:60` devuelve la bandera. Como en el resto de pantallas, el callback no la reinicia.
- **Hilo principal:** `touch()` se llama en el hilo principal y solo hace una asignación y un lanzamiento en `appScope` (`AppContainer.saveClock`); la escritura a ajustes va fuera del hilo principal.
- **Tests:** `cancelDuringTheDerivationZeroesTheKey`, `afterCancelTheNextDerivationWaits` y `cancelWhileTheFolderIsMadeLeavesNothing` (`BackupCheckTest.kt`) usan bloqueos y un planificador de un solo hilo reales, no esperas ciegas, salvo un `delay(300)` que solo confirma que no pasa nada; se quitó `aNewRunWaitsForTheOneItStopped` (camino inalcanzable desde la interfaz).

## Textos para el Abogado Digital (revisión final)

- `BACKUP_CHECK_NOT_PROVEN` (`BackupCheckTexts.kt`): «Tampoco prueba lo que solo hace la importación: si el coste de Argon2id elegido en este móvil es mayor que el de la copia, al importarla se vuelve a cifrar su clave con ese coste, y eso puede necesitar más memoria.» Cierto por `afterMasterUnlock`; sin palabras prohibidas.
- `BACKUP_CHECK_INTRO`: «No cambia nada: no toca tu bóveda, tus ajustes ni tus copias.» Cierto para los ajustes del usuario; la marca interna del reloj (A11) puede subir al elegir el archivo, como en cualquier uso de la app. Decisión del Abogado Digital si basta o si conviene «ni tus ajustes» sin más.
