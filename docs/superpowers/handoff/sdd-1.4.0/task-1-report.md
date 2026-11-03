# Tarea 1 (1.4.0): `BiometricVault` en una sola fila de operaciones: informe

**Estado:** DONE_WITH_CONCERNS (las inquietudes están en «Inquietudes»; ninguna bloquea).
**Commit:** `d59c743` «1.4.0: BiometricVault en una sola fila de operaciones», subido a `origin/ccr-a1a6eabb-765f9g`.

## Qué se ha hecho

### `tools/KeyOpsQueue.kt` (nuevo, Kotlin puro y con corrutinas, compila en `jvm-check`)
- `enum class KeyOp { ENABLE, DISABLE, UNLOCK }`, `class KeyOpsQueue(dispatcher)`, `suspend fun <T> run(kind, block): T` y `fun cancelEnable()`.
- Un `Mutex` cubre cada operación entera. Cada bloque corre en `withContext(dispatcher)`. `withContext` solo vuelve cuando el bloque ha terminado, también si se cancela, así que la fila sigue ocupada hasta que el bloque acaba, con sus `finally` incluidos (`KeyOpsQueue.kt:41`).
- Cada `run` abre su propio `coroutineScope`. `cancelEnable` cancela ese `Job` y no la corrutina que llama: quien llama recibe la `CancellationException` y sigue activo para limpiar e informar (`KeyOpsQueue.kt:36-45`).
- Los ENABLE se apuntan **antes** de pedir el lock, en un conjunto protegido con `synchronized`. Así, un DISABLE (`cancelEnable()` antes de pedir el lock, `:34`) cancela tanto el ENABLE en curso como uno que aún espera su turno: gana la última intención del usuario. UNLOCK no cancela nada y espera su turno.
- La KDoc avisa de que la fila no es reentrante: nunca se llama a `run` desde dentro de un bloque.

### `android/BiometricVault.kt`
- Una sola fila: `KeyOpsQueue(Executors.newSingleThreadExecutor().asCoroutineDispatcher())` (`:180`), y `MainScope()` para las corrutinas de activar y desbloquear (`:183`).
- **DISABLE:** `suspend fun disable(context)` (`:163-166`) hace `withContext(NonCancellable) { queue.run(DISABLE) { clear(app) } }`. No se salta nunca, aunque cancelen a quien llama. Usa `applicationContext` para no retener la actividad. `clear()` (`:169`) es el cuerpo de antes y lo usan las operaciones que ya tienen la fila. Se sustituye el `disable(activity)` interno de `enable` (`:369`) y los dos de `unlock` (`:578` y `:592`); llamar a `disable` desde dentro de la fila la bloquearía para siempre.
- **ENABLE:** todo el flujo es **una** operación: el `clear` inicial, la generación de la clave y `Cipher.init` (en el hilo de la fila), el prompt (`withContext(Main)` y `suspendCancellableCoroutine`, `askFingerprint`, `:238-262`), `doFinal` con la escritura de las preferencias (en el hilo de la fila, `:448`) y el segundo intento en el TEE si falla StrongBox, con la espera a que se vaya el fragmento (`promptGone`). Lo mantienen igual que antes:
  - `EnableFlow` sigue, con `decide()` como única salida, igual que `end()`. El guard `enabling` se libera en `ended()` (`:498`), después de que la operación salga de la fila: «espera a una generación de clave en curso» se cumple solo.
  - El segundo intento usa las mismas funciones puras `firstAttempt`, `nextAfterFailure`, `note`/`shouldMarkStrongBoxFailed` con `PENDING` (`:402`) y luego `STORED`/`FAILED`. M-4 publica `FINGERPRINT_ENABLE_INTERRUPTED` y cierra el prompt viejo.
  - Las claves se borran en el `finally` del bloque, dentro de la fila, y la clave sin usar se borra antes de que corra otra operación (`:373-374`). `ended()` las vuelve a poner a cero, por si la operación se canceló antes de su turno (`:497`).
  - **Activar cancelado** (por un DISABLE o porque se va la actividad): `decide()` no llega a llamarse, así que acaba como un fallo, sin mensaje y con `note(FAILED)`, que nunca marca StrongBox por `AUTH_OR_FINAL`. Si la actividad sigue viva, el prompt se cierra con `cancelAuthentication`.
  - El lanzamiento es `CoroutineStart.UNDISPATCHED` (`:360`): la operación ya está en la fila cuando `enable()` vuelve, y un `disable` pedido justo después la cancela.
- **UNLOCK:** `Cipher.init`, el prompt y `doFinal` son una sola operación (`unlockSteps`, `:559`). Las preferencias se leen dentro de la operación. `onKey` y `onError` se llaman en el hilo principal **después** de salir de la fila (`:540-546`), así que pueden llamar a `disable` sin bloquearla. La clave liberada se entrega siempre, con `handOverKey`, o se pone a cero: nunca se pierde (BOV-3). `ON_DESTROY` cancela la operación (`:549`); termina cuando ha vuelto el paso de Keystore en curso, y entonces se suelta `SingleRun`. Un DISABLE espera al UNLOCK y no lo cancela.
- `enabled()` y `keyLevel()` siguen siendo lecturas simples, sin cambios.

### Llamadas a `disable` (las seis, todas esperan ahora)
- `VaultBackup.kt:131` (cambiar la maestra, **después** de guardar) y `:175` (importar, **después** de guardar): ya estaban en `scope.launch { withContext(NonCancellable) {…} }`. La llamada `suspend` compila tal cual; el archivo **no cambia**.
- `VaultScreen.kt:171` (menú «Desactivar huella», en un callback): pasa a `scope.launch { disable; showSnackbar("Huella desactivada") }`.
- `VaultScreen.kt:283` (crear la bóveda, desactivar **antes** de `store.create`): ya estaba en `scope.launch`.
- `VaultScreen.kt:325` (`onWrongKey`): no es un callback de `unlock` sino la lambda de `launchUnlockWithKey`, que ya corre en una corrutina (`launch(ATOMIC)`). He cambiado su tipo a `onWrongKey: suspend () -> Unit` (`VaultStore.kt:105`). Así `disable` se espera dentro de esa misma corrutina, antes de `onError(FINGERPRINT_STALE)`. El texto de la pantalla no cambia, y el test de fuente de CRY-A02 sigue pasando sin tocarlo. Es la regla R1 aplicada con la forma más pequeña posible.
- `VaultScreen.kt:382` («Borrar bóveda», en un callback): pasa a `scope.launch { disable; store.destroy() }`, con la huella desactivada **antes** de borrar (CRY-A02).

## Tests y resultados

- `KeyOpsQueueTest` (nuevo, 9 tests, con `StandardTestDispatcher` sobre el planificador de `runTest`):
  - los cuatro del brief: `operationsNeverOverlap`, `disableDuringEnableCancelsTheEnable` (además comprueba que quien llama sigue activo), `unlockWaitsForDisable` y `cancelledEnableReleasesTheQueue` (con `currentTime == 0`);
  - cinco más:
    - `disableAlsoDropsAnEnableStillWaitingItsTurn`;
    - `aCancelledEnableFinishesItsCleanupBeforeTheDisableRuns`: no queda una clave sin preferencias;
    - `disableWaitsForAnUnlockAndDoesNotCancelIt`;
    - `aFailingOperationReleasesTheQueue`;
    - `blocksRunOnTheDispatcherGiven`.
- `VaultStoreTest`: un test nuevo, «1_4_0 the stale-key text waits until turning the fingerprint off has ended». Con `onWrongKey` suspendido (`delay`), el orden es `[offStart, offEnd, FINGERPRINT_STALE]`.
- **Ningún test existente ha cambiado.** Las pruebas del flujo de activar que hay en JVM son las de las funciones puras (`KeyPlacementTest` y `SingleRunTest`) y siguen igual. `EnableFlow` está en `android/` y no tiene tests JVM.
- Verificación completa (con `--offline`):
  - `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets --no-daemon --max-workers=1 --offline`: **BUILD SUCCESSFUL**, 1303 tests y 0 fallos. Lint da 4 avisos, todos de antes y en archivos que no he tocado: GradleDependency ×2 en `libs.versions.toml:6`, DiscouragedApi en `AndroidManifest.xml:32` y ObsoleteSdkInt en `mipmap-anydpi-v26`. No hay ningún aviso nuevo.
  - `gradle -p jvm-check test --no-daemon --max-workers=1 --offline`: **BUILD SUCCESSFUL**, 1298 PASSED y ningún FAILED.
  - Kotlin no avisa de nada en los archivos tocados. Los dos avisos `!!` que salen son de `PhotoCleanScreen` y `RedactScreen`, que ya estaban.

## Evidencia de TDD

- **RED:** `gradle -p jvm-check test --tests '*KeyOpsQueueTest*' --offline` dio `:compileTestKotlin FAILED` con `e: …KeyOpsQueueTest.kt:29:37 Unresolved reference 'KeyOpsQueue'.` y `Unresolved reference 'KeyOp'`. Es lo esperado: la clase aún no existía. Una segunda pasada de compilación arregló dos errores del propio test: faltaba importar `currentTime`, y `CoroutineDispatcher.Key` es experimental, así que se usa `ContinuationInterceptor`.
- **GREEN:** el mismo comando dio `KeyOpsQueueTest > … PASSED` ×9, `BUILD SUCCESSFUL`.

## Archivos

- Nuevos: `app/src/main/java/app/seguridad/tools/KeyOpsQueue.kt` y `app/src/test/java/app/seguridad/tools/KeyOpsQueueTest.kt`.
- Cambiados: `app/src/main/java/app/seguridad/android/BiometricVault.kt`, `app/src/main/java/app/seguridad/ui/screens/VaultScreen.kt`, `app/src/main/java/app/seguridad/vault/VaultStore.kt` (solo la firma de `onWrongKey` y su KDoc) y `app/src/test/java/app/seguridad/vault/VaultStoreTest.kt` (el test nuevo y el import de `delay`).
- `VaultBackup.kt` no cambia: sus dos llamadas ya estaban en corrutinas.

## Autorrevisión

- **Fuera del brief:** he tocado `VaultStore.kt` (`onWrongKey` pasa a `suspend`). Era lo mínimo para esperar a `disable` en `VaultScreen.kt:325` sin romper el test de fuente de CRY-A02.
- **`doFinal` fuera del hilo principal:** al activar y al desbloquear, `doFinal` va ahora en el hilo de la fila, como pide el diseño («Las llamadas al Keystore van en un dispatcher de un solo hilo»). Antes iba en el principal. La clave de datos solo la toca la propia operación: `ended()` vuelve a poner a cero en el hilo principal cuando el bloque ya ha terminado, así que no hay carrera entre `doFinal` y el borrado.
- **Excepciones inesperadas** dentro de las operaciones (por ejemplo, `authenticate` lanzando una excepción): antes tumbaban `MainScope` al activar. Ahora acaban con «No se pudo activar la huella» y limpian. Al desbloquear siguen dando «No se pudo usar la huella», como antes.
- **YAGNI:** no hay API nueva fuera de lo pedido. El conjunto de ENABLE, en vez de un solo `Job`, es lo mínimo para cancelar también uno que espera su turno.

## Inquietudes

1. **Cambio de comportamiento en desbloquear con la actividad recreada:** antes, `ON_DESTROY` con el prompt en pantalla solo soltaba el guard. Si luego llegaba una respuesta tardía al callback viejo, todavía desbloqueaba. Ahora `ON_DESTROY` cancela la operación y la respuesta tardía se ignora (no hay `doFinal`, así que no se libera ninguna clave). El motivo: si la operación siguiera esperando un prompt que quizá no responde nunca, la fila quedaría bloqueada para siempre. Hacer el `doFinal` tardío fuera de la fila rompería el orden, y un fallo podría culpar a StrongBox sin motivo. En la práctica, `androidx.biometric` 1.1.0 cambia el callback del ViewModel en cuanto la actividad nueva crea su propio `BiometricPrompt` (el arranque automático), así que la respuesta tardía casi nunca llegaba. **Hay que probarlo en el móvil:** girar el móvil con el prompt de desbloqueo en pantalla.
2. **Borrar la bóveda («He olvidado»):** `store.destroy()` va ahora después de esperar a `disable`. Si la pantalla se destruye justo en esos milisegundos, la huella queda desactivada pero la bóveda no se borra. Es el lado seguro, y antes pasaba lo mismo si se cancelaba `destroy`.
3. **Activar cancelado por un DISABLE:** no muestra ningún mensaje (`notify = false`). Quien desactiva muestra el suyo.
4. **Lo que dice el diseño que se pruebe en el móvil:** activar y desactivar seguidos, desactivar durante la generación y durante el prompt, girar el móvil en cada paso (M-4) y desbloquear mientras se desactiva. En JVM solo se puede probar la fila; `BiometricVault` necesita Keystore y BiometricPrompt reales.

## Revisión experto-seguridad-cripto

- **Modelo de amenaza:** las operaciones solapadas sobre el único alias `seguridad_vault_biometric` podían dejar una clave del Keystore sin preferencias, unas preferencias sin clave, una huella que sobrevive a un cambio de maestra (CRY-A02) o una marca falsa de «StrongBox falló». Ahora no hay solapes: `KeyOpsQueue.kt:41` (el `Mutex` cubre la operación entera) y `BiometricVault.kt:364-384` (activar es una sola operación).
- **Gana la última intención:** un DISABLE cancela el ENABLE en curso o en espera (`KeyOpsQueue.kt:34` y `:38`). La limpieza del ENABLE (poner a cero y borrar la clave sin usar) termina dentro de la fila, antes de que corra el DISABLE (`BiometricVault.kt:373-374`; test `aCancelledEnableFinishesItsCleanupBeforeTheDisableRuns`).
- **No queda una clave sin preferencias ni unas preferencias sin clave:** las preferencias se escriben después de `doFinal`, en la misma operación (`:448`), y `stored` solo vale `true` cuando se han escrito. Si no se guardó, se borra la clave dentro de la fila (`:374`). Si se canceló después de guardar, el DISABLE que viene detrás borra las dos cosas (`clear`, `:169-176`).
- **CRY-A02:** `disable` no se salta nunca (`NonCancellable`, `:165`) y se espera antes de seguir. Antes de crear la bóveda (`VaultScreen.kt:283`) y de borrarla (`VaultScreen.kt:382`); después de guardar al cambiar la maestra (`VaultBackup.kt:131`) y al importar (`VaultBackup.kt:175`). Con una clave obsoleta, se espera antes de mostrar el texto (`VaultStore.kt:105`, `VaultScreen.kt:325`; test «1_4_0 the stale-key text waits…»).
- **El material de clave se pone a cero:** la clave de datos y su copia de reserva, en el `finally` de la operación y otra vez en `ended()` (`:373` y `:497`). La clave que se libera al desbloquear se entrega con `handOverKey` o se pone a cero si hay un `Error` (`:533` y `:543`).
- **StrongBox:** las reglas de marcado no cambian (`note` con `PENDING`/`STORED`/`FAILED`, `:402` y `:496`). Activar cancelado nunca marca por `AUTH_OR_FINAL`. Al desbloquear, `failedAt` marca y desactiva dentro de la misma operación (`:577-578`).
- **El contrato de CryptoObject sigue igual:** `Cipher.init` antes de `authenticate` y `doFinal` sobre el `Cipher` autorizado que devuelve el prompt (`:429`, `:448`, `:590` y `:610`). No hay criptografía nueva, ni dependencias, ni permisos, y no hay logs (comprobado con `grep` sobre el diff: ningún `Log.`, `println` ni `printStackTrace`). El alias y el nombre de las preferencias no cambian (`AppNameTest` en verde).

## Revisión Ingeniero Móvil Senior

- **Concurrencia:** todo el Keystore va en un solo hilo (`:180`) y la UI (`canPrompt`, el prompt, `onDone`, `onKey` y `onError`) en `Dispatchers.Main` (`:422`, `:433`, `:598`, `:540-546` y `:494-503`). No hay nada que bloquee el hilo principal: `disable` hace `withContext` hacia la fila, y la fila espera con `suspend` (`Mutex`), sin bloquear.
- **Sin bloqueos mutuos:** dentro de la fila nunca se vuelve a llamar a la fila (`clear` en vez de `disable`, `:369`, `:578` y `:592`). Los callbacks hacia la UI se ejecutan después de salir de la fila, así que `onWrongKey` (que llama a `disable`) no se queda esperando al UNLOCK en curso (`:540-546`).
- **Ciclo de vida:** `ON_DESTROY` cancela la operación de activar (`:344`) o la de desbloquear (`:549`), y un prompt que no responde nunca no deja la fila bloqueada. M-4 no cambia (`:336-355`). `UNDISPATCHED` (`:360` y `:521`) evita la carrera entre `launch` y `ON_DESTROY` o un `disable` inmediato. `suspendCancellableCoroutine` ignora las respuestas tardías y cierra el prompt solo si su actividad sigue viva (`:243-262`).
- **Compose:** las dos llamadas que estaban en callbacks van ahora en el `scope` de la composición, y lo que depende de ellas va dentro de la misma corrutina, después del `await` (`VaultScreen.kt:170-173` y `:380-384`). No se usa `rememberSaveable` ni se toca el estado.
- **Rendimiento:** la generación de la clave StrongBox y el `doFinal` salen del hilo principal. El retraso extra es un salto entre hilos por operación.
- **Lint y compilación:** sin avisos nuevos, y todos los `check*` en verde.

## Textos para el Abogado Digital (revisión final)

Ninguno. No he añadido ni cambiado textos de la interfaz: «Huella desactivada», «No se pudo activar la huella» y los demás son los mismos de antes, solo cambiados de sitio.

---

# Ronda de arreglos 1 (revisión `task-1-review.md`: I1, M1 y M2)

**Commit:** `516c544` «1.4.0: BiometricVault, arreglos de la revisión de la tarea 1 (I1, M1, M2)», subido a `origin/ccr-a1a6eabb-765f9g`.

## Qué cambia

- **I1 (Important): el aviso M-4 ya no dice que no se guardó nada cuando sí se guardó.**
  - El observador de `ON_DESTROY` solo apunta `flow.recreated = !flow.done && activity.isChangingConfigurations` (`BiometricVault.kt:349-350`, con el campo en `:301`). Sigue cancelando la operación y cerrando el prompt viejo en ese mismo caso.
  - El aviso sale ahora de `ended()`, en el hilo principal, cuando la operación ya ha salido de la fila y `stored` tiene su valor final: `if (shouldSayEnableInterrupted(flow.recreated, flow.stored)) interruption.post(FINGERPRINT_ENABLE_INTERRUPTED)` (`:509`).
  - Hay un `currentCoroutineContext().ensureActive()` antes de `doFinal` (`:458`): si la cancelación ya ha llegado, no se guarda nada, el `finally` de la operación borra la clave y se avisa.
  - La decisión está en una función pura, `shouldSayEnableInterrupted(recreated, stored) = recreated && !stored`, en `tools/KeyPlacement.kt:62`, igual que `shouldMarkStrongBoxFailed`.
  - Están al día la KDoc de `interrupted` (`:270-277`, «Posted only once that enable's operation has ended with nothing stored»), la de `enable` («if nothing was stored») y la de `ended`.
- **M1 (Minor):** el observador de `unlock` llama a `ticket.release()` y después a `job.cancel()` en `ON_DESTROY` (`:566-571`). `release` se puede llamar dos veces sin efecto, y el del `finally` (`:551`) no suelta un turno más nuevo (`SingleRun.kt:16-18`). Así, el arranque automático de la actividad nueva ya no se rechaza. Espera en la fila detrás del desbloqueo cancelado, y nunca hay dos `Cipher.init` a la vez. La KDoc de `unlock` está al día.
- **M2 (Minor):** nuevo test `KeyOpsQueueTest.anEnableAskedAfterADisableRunsAfterIt` (`KeyOpsQueueTest.kt:82`): el orden es `[disableStart, disableEnd, enable]` y el ENABLE no se cancela.

No cambia ningún texto de la interfaz; `FINGERPRINT_ENABLE_INTERRUPTED` es la misma constante.

## Tests que lo cubren y TDD

- **RED:** `gradle -p jvm-check test --tests '*KeyOpsQueueTest*' --tests '*KeyPlacementTest*' --offline` dio `e: …KeyPlacementTest.kt:149:61 Unresolved reference 'shouldSayEnableInterrupted'.` (×4) y `:compileTestKotlin FAILED`. Es lo esperado: la función aún no existía. El test de M2 pasa con el código de la ronda anterior; es una regresión que protege una propiedad que ya se cumplía, como pedía la revisión.
- **GREEN:** `gradle -p jvm-check test --tests '*KeyOpsQueueTest*' --tests '*KeyPlacementTest*' --tests '*VaultStoreTest*' --tests '*SingleRunTest*' --offline` dio `KeyOpsQueueTest > anEnableAskedAfterADisableRunsAfterIt PASSED` y `KeyPlacementTest > theInterruptedNoticeOnlyWhenARecreationLeftNothingStored PASSED` (con los tres casos que pide la revisión y el cuarto), sin ningún FAILED y con `BUILD SUCCESSFUL`.
- `BiometricVault` y `EnableFlow` no tienen tests JVM (están en `android/`). Lo que en ellos se puede probar sin el móvil es la función pura de arriba.

## Verificación completa

- `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets --no-daemon --max-workers=1 --offline` dio `BUILD SUCCESSFUL in 3m 17s`, con 1305 tests y 0 fallos. Lint da los mismos 4 avisos de antes (GradleDependency ×2 en `libs.versions.toml:6`, DiscouragedApi en `AndroidManifest.xml:32` y ObsoleteSdkInt en `mipmap-anydpi-v26`); no hay ninguno nuevo, y Kotlin no avisa de nada en los archivos tocados.
- `gradle -p jvm-check test --no-daemon --max-workers=1 --offline` dio `BUILD SUCCESSFUL in 1m 6s`, con 1300 PASSED y 0 FAILED.

## Inquietudes que siguen abiertas

Siguen las inquietudes 2 a 5 de la primera ronda. La 1 cambia un poco: el ticket ya se suelta enseguida, pero una respuesta tardía del prompt viejo sigue sin tenerse en cuenta. Hay que probarlo en el móvil igual que antes, y también girar el móvil justo después de aceptar la huella al activarla (I1): no debe salir el aviso, y el menú debe decir «Desactivar huella».

## Revisión experto-seguridad-cripto (ronda 1)

- **Veracidad (I1):** el aviso «no se guardó nada» se decide con el estado final. La operación escribe `stored` en `decide()` (`BiometricVault.kt:308-314`) antes de salir de la fila, y `ended()` lo lee en el hilo principal (`:509`) después de que el bloque haya terminado (`KeyOpsQueue.kt:41`). La función `shouldSayEnableInterrupted` (`KeyPlacement.kt:62`) tiene test con todos los casos.
- **Estado coherente:** con `ensureActive()` antes de `doFinal` (`:458`), una cancelación que ya ha llegado no envuelve ni escribe nada. Si la cancelación llega durante `doFinal` o durante la escritura, se guardan juntas la clave y las preferencias, y no se avisa; el DISABLE que haya cancelado borra después las dos. Nunca queda una clave sin preferencias (`:373-374`).
- **M1:** soltar el turno antes de tiempo no abre una carrera en el Keystore. El desbloqueo nuevo espera en el `Mutex` (`KeyOpsQueue.kt:41`) a que termine el bloque cancelado, y el `finally` viejo no suelta el turno nuevo (`SingleRun.kt:16-18`).
- No hay criptografía, dependencias, permisos ni logs nuevos.

## Revisión Ingeniero Móvil Senior (ronda 1)

- **Hilos:** `recreated`, `gone`, `observer` y `prompt` solo se usan en el hilo principal: el observador (`:345-365`) y `ended()` (`:503-515`). `stored` y `done` siguen siendo `@Volatile`.
- **Ciclo de vida:**
  - M-4 sigue cerrando el prompt viejo cuando se recrea la actividad. El aviso llega a la actividad nueva por el `StateFlow` `interrupted`, después de que la operación termine, como antes, y `VaultScreen` lo muestra cuando lo recibe.
  - Desbloquear suelta el turno enseguida en `ON_DESTROY` (`:568`), y el `LaunchedEffect` de la pantalla nueva ya no se queda sin respuesta.
- **Fila:** el test M2 fija que un ENABLE pedido después de un DISABLE no se cancela y espera su turno (`KeyOpsQueueTest.kt:82-91`).
- Lint y compilación sin avisos nuevos.

## Textos para el Abogado Digital (revisión final), ronda 1

Ninguno: no se ha añadido ni cambiado ningún texto. Solo cambia cuándo sale `FINGERPRINT_ENABLE_INTERRUPTED`: ahora solo cuando lo que dice es cierto.
