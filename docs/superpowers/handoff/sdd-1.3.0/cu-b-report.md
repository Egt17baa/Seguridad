# CU-B: arreglos del Ingeniero Móvil Senior (tareas 1 a 11), informe

**Rama** `ccr-a1a6eabb-765f9g`, de `08c5d3e` a `365e77c`. Sin push.

**Commits**
- `c808025` 1.3.0: Ingeniero Móvil Senior, diálogo de A6 fuera de la actividad y avisos de las copias (M-3, T11-a a T11-d).
- `365e77c` 1.3.0: Ingeniero Móvil Senior, estado en ViewModel, «PIN y códigos» al volver, memoria y accesibilidad (I-1, M-1, M-2, M-4, M-5, M-7 a M-11).

**Skills usadas:** superpowers (TDD y verificación), `experto-seguridad-cripto`, `ingeniero-movil-senior:desarrollo-movil-senior` y `ingeniero-movil-senior:seguridad-criptografia-movil`.

## Elección del contenedor de estado

`androidx.lifecycle:lifecycle-viewmodel-compose` 2.9.2 ya está en `app/build.gradle.kts:92`. Por eso no hay ninguna dependencia nueva:
- **«Tachar», «Limpiar foto» y «PIN y códigos»** tienen cada una un `ViewModel` de su `NavBackStackEntry`, creado con `viewModel { … }`.
  - Sobrevive a un giro, a un cambio de tema, a la pantalla dividida y a la ventana flotante.
  - Se pierde si muere el proceso, y así falla cerrado.
  - Nunca va a `rememberSaveable` ni al `Bundle`.
- **El diálogo de A6** vive en `AppContainer.masterChanged`, que dura lo que el proceso. Lo cambia cualquier pantalla y lo lee la raíz, así que la lambda de una composición antigua sigue llegando a la nueva.
- **La lógica que se puede probar en la JVM** está en código puro, que compila `jvm-check`: `CodesSession`, `MasterChanged` y `Notice`.

## Tabla de puntos

| Punto | Hecho | Cómo | archivo:línea |
|---|---|---|---|
| I-1 | Sí | Se vuelve a dibujar todo vacío al volver tras `max(lockAfter, 60 s)`, con el tiempo medido con `SystemClock.elapsedRealtime()` desde ON_STOP. Pasa lo mismo si la bóveda se bloquea con la pantalla delante. La espera de guardado también se borra. PIN, códigos y respuestas son `String`: no se pueden poner a cero, así que se sueltan todas sus referencias. | `tools/codes/CodesSession.kt:40,220,233,240,251`; `ui/screens/CodesScreen.kt:160,218-223`; `ui/AppNavHost.kt:231` |
| M-1 | Sí | `RedactViewModel` (`Picture` y cajas) y `PhotoCleanViewModel` (`CleanCopy`). La apertura y la decodificación van en `viewModelScope`. El bitmap de vista previa solo se recicla al cambiar de imagen (`isShown`) o en `onCleared`. | `ui/screens/RedactScreen.kt:206,268,469`; `ui/screens/PhotoCleanScreen.kt:138,173` |
| M-2 | Sí | `Modifier.systemGestureExclusion()` en el lienzo. No hace nada antes de la API 29. | `ui/screens/RedactScreen.kt:482` |
| M-3 | Sí | Clase `MasterChanged` en `AppContainer`. `replace()` corre en `appScope` y guarda la respuesta aunque no haya pantalla esperándola. El aviso de huella no desactivada sale también dentro del diálogo. | `vault/MasterChanged.kt`; `AppContainer.kt:106`; `ui/AppNavHost.kt:121`; `ui/screens/VaultBackup.kt:120`; `ui/screens/SettingsScreen.kt:303` |
| M-4 | Sí | En ON_DESTROY con `isChangingConfigurations`, el flujo termina y deja un `Notice` a nivel de proceso. `VaultScreen` lo muestra una vez en la actividad nueva. | `android/BiometricVault.kt:245`; `ui/screens/VaultScreen.kt:133` |
| M-5 | Sí | `keyStore().load`, `getKey` y `Cipher.init` van en `Dispatchers.Default`. De vuelta en Main se comprueba `canPrompt` y después se llama a `authenticate`, con el `CryptoObject` ya iniciado. El respaldo de StrongBox a TEE no cambia. | `android/BiometricVault.kt:401,423` |
| M-6 | Nada pendiente | `PHOTO_HEIF_NOTICE` (CU-A) ya dice que se reduce a unos 24 MP, y sale siempre que se convierte una foto. No hace falta código. | `tools/ToolTexts.kt:103` |
| M-7 | Sí | `readAtMost(limit, expected)` lee directamente en un solo array cuando el proveedor da el tamaño, sin pasar de `MAX_IMAGE_BYTES + 1`. Los decodificadores lanzan `ImageOutOfMemory`, e `imageOpenFailureText` lo convierte en `IMAGE_NO_MEMORY` en las tres pantallas. | `core/Streams.kt`; `ui/screens/PhotoCleanScreen.kt:105`; `tools/ImageLimits.kt:28,35`; `android/RedactRender.kt:70`; `android/HeifToJpeg.kt:57`; `android/QrFromImage.kt:48,58,70`; `ui/screens/QrInspectScreen.kt:182,185` |
| M-8 | Documentado | KDoc con el riesgo y los dos arreglos de bajo riesgo, que solo se aplican si la prueba en el móvil lo pide. Añadido a la lista del README. | `android/RedactRender.kt:78`; `README.md:179` |
| M-9 | Sí | `imePadding()` en «PIN y códigos» y en las pantallas de la rama con campos dentro de una columna desplazable. | `ui/screens/CodesScreen.kt:270`; `ContactAddScreen.kt:120`; `IdentityScreen.kt:115`; `PkActionsScreen.kt:293,445,666`; `VaultEntryScreen.kt:278` |
| M-10 | Sí | La lista BIP-39 se lee en el `ViewModel` con `Dispatchers.IO`. Mientras no llega, los botones de respuesta están desactivados. | `ui/screens/CodesScreen.kt:151` |
| M-11 | Sí | `semantics { contentDescription = redactCanvasDescription(n) }` en el lienzo, y `clearAndSetSemantics { }` en el emoji de cada ficha. | `ui/screens/RedactScreen.kt:483`; `ui/screens/ToolsScreen.kt:68` |
| T11-a | Sí | `toldOutcome = null` al empezar `replaceNow`, que es la entrada común de los dos caminos. | `vault/AutoBackup.kt:229` |
| T11-b | Sí | En `replaceStatus`, la respuesta «waits» cede ante el registro de Ajustes cuando no queda nada pendiente y hay una copia posterior a la petición. | `vault/AutoBackup.kt:446` |
| T11-c | Sí | `SettingsStore.turnOffAutoBackup()` decide «dropped» con el estado de antes y el de después, bajo el mismo `mutex` que la escritura. | `settings/SettingsStore.kt:55`; `ui/screens/SettingsScreen.kt:251` |
| T11-d | Sí | `replaceOutcomeShown(shown)` usa `Notice.shown`, un compare-and-set por identidad. `post` pasa por `null` para que un resultado igual pero nuevo no se confunda con el anterior. | `core/Notice.kt`; `vault/AutoBackup.kt:189`; `ui/AppNavHost.kt:127` |
| CODES_UNLOCK_TO_SAVE | Reescrito | Ahora dice cuándo se pierde de verdad lo generado. | `tools/ToolTexts.kt:390` |

## Evidencia de TDD

1. **Tests primero.** Se escribieron `CodesSessionTest`, `StreamsTest`, `NoticeTest`, `ImageFailureTest`, `MasterChangedTest` y los tests nuevos de `AutoBackupTest` y `SettingsTest`. Con `jvm-check` fallaron al compilar (rojo): «Unresolved reference 'Notice'», «No parameter with name 'expected'», «Unresolved reference 'turnOffAutoBackup'», «'imageOpenFailureText'» y «'CodesSession'».
2. **Rojo de comportamiento.** Ya con el código hecho, se quitó a propósito el reinicio de T11-a y la regla de T11-b. `AutoBackupTest` dio «42 tests completed, 3 failed»:
   - `aNewRequestWhileOpenStartsTheOnceRuleAgain`: NullPointerException;
   - `closingTheDialogAcknowledgesOnlyTheOutcomeItShowed`: NullPointerException;
   - `replaceStatusNeverClaimsDoneWithoutARecordedReplacement`: «expected Done but was Waits».

   Después se restauró el código.
3. **T11-d.** La firma nueva, `replaceOutcomeShown(shown)`, hizo fallar al compilar el test anterior. El test de identidad cubre el resultado que llega entre mostrar el aviso y cerrarlo.
4. **Verde.** Todos los tests pasan (lo de abajo).

## Verificación completa

Con `ANDROID_HOME=/opt/android-sdk` y `--no-daemon --max-workers=1 --offline`:

| Comprobación | Resultado |
|---|---|
| `gradle -p jvm-check test` | 915 tests, 0 fallos (antes de cada commit: 895 en el estado del commit 1 y 915 en el final) |
| `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets` | BUILD SUCCESSFUL in 5m 1s, con 915 tests de unidad y 0 fallos |
| `lintDebug` | 0 errores y 16 avisos, todos anteriores y fuera de los archivos tocados: versiones en `libs.versions.toml`, `DiscouragedApi` en el manifiesto, `CustomX509TrustManager` en `DistrustedCertificates.kt` y `ObsoleteSdkInt` en mipmap |
| `aapt2 dump permissions` del APK debug | Solo `INTERNET`, `CAMERA`, `USE_BIOMETRIC` y `USE_FINGERPRINT`, más el `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` de AndroidX. Ningún permiso nuevo. |
| `grep` de `Log.`, `println` y `printStackTrace` en `app/src/main/java` | 0 resultados |

## Archivos cambiados

35 archivos, con 1460 líneas añadidas y 393 quitadas.

- **Nuevos:**
  - `core/Notice.kt`, `tools/codes/CodesSession.kt` y `vault/MasterChanged.kt`;
  - los tests `core/NoticeTest.kt`, `core/StreamsTest.kt`, `tools/ImageFailureTest.kt`, `tools/codes/CodesSessionTest.kt` y `vault/MasterChangedTest.kt`.
- **Cambiados:**
  - `AppContainer.kt`;
  - en `android/`: `BiometricVault.kt`, `HeifToJpeg.kt`, `QrFromImage.kt` y `RedactRender.kt`;
  - `core/Streams.kt` y `settings/SettingsStore.kt`;
  - en `tools/`: `ImageLimits.kt` y `ToolTexts.kt`;
  - `ui/AppNavHost.kt`;
  - en `ui/screens/`: `CodesScreen`, `ContactAddScreen`, `IdentityScreen`, `PhotoCleanScreen`, `PkActionsScreen`, `QrInspectScreen`, `RedactScreen`, `SettingsScreen`, `ToolsScreen`, `VaultBackup`, `VaultEntryScreen` y `VaultScreen`;
  - `vault/AutoBackup.kt`;
  - los tests `AutoBackupTest`, `SettingsTest` y `ToolTextsTest`;
  - `README.md`.

## Pruebas en el móvil para la lista del dueño

Están añadidas en `README.md:174-180`:
1. **I-1:** en «PIN y códigos», volver a los 10 s conserva lo generado. Volver tras el tiempo de bloqueo, o 1 minuto con «Al salir», deja la pantalla vacía. Si la bóveda se bloquea por inactividad con la pantalla delante, también se vacía. Al girar, todo sigue. Con 4 preguntas, la última queda por encima del teclado (M-9).
2. **M-1 y M-2:** al girar, al cambiar el modo oscuro y en ventana flotante o pantalla dividida, «Tachar» conserva sus bloques y «Limpiar foto» su copia. Un arrastre desde el borde izquierdo dibuja un bloque y no sale de la pantalla.
3. **M-3:** al girar durante la derivación de la maestra («Alto»), sale el diálogo. Al girar durante la sustitución de las copias, se ve el resultado.
4. **M-4:** al girar con el diálogo de la huella abierto, sale el aviso, y al volver a tocar se activa.
5. **M-5:** al desbloquear con la huella en StrongBox, no hay tirón (`dumpsys gfxinfo`).
6. **M-7 y M-8:** abrir un JPEG de 200 MP y un PNG de 16 bits y mirar `dumpsys meminfo`, «Native Heap». En un móvil de 3 o 4 GB, anotar si el sistema la mata.
7. **M-11:** con TalkBack, las fichas no leen el emoji y el lienzo dice cuántos bloques tiene.

## Textos para el Abogado Digital

Todos pasan `ToolTextsTest` (están en `ALL_TOOL_TEXTS`) y `SourceTextsTest`.

1. `CODES_UNLOCK_TO_SAVE` (`tools/ToolTexts.kt:390`), **cambiado**. Antes decía que se perdía al girar, y eso deja de ser verdad. Ahora:
   > «Desbloquea la bóveda para guardarlo. Lo que ves aquí se pierde si vuelves atrás, si cierras la app o si pasas fuera de esta pantalla más tiempo que el de bloqueo de la bóveda (como mínimo, 1 minuto).»

   Encaja con el código:
   - «vuelves atrás»: el `ViewModel` se limpia al salir de la pila;
   - «cierras la app»: muere el proceso;
   - el tiempo fuera se mide desde ON_STOP de la pantalla, también al ir a otra pestaña.

   No menciona el borrado cuando la bóveda se bloquea con la pantalla delante, porque mientras se ve este texto la bóveda ya está bloqueada. El test `textsOfTheLegalReviewAreTheProposedOnes` se actualizó a este texto.
2. `IMAGE_NO_MEMORY` (`tools/ToolTexts.kt:216`), **nuevo**:
   > «No hay memoria suficiente para abrir esta imagen en este móvil.»
3. `FINGERPRINT_ENABLE_INTERRUPTED` (`tools/ToolTexts.kt:473`), **nuevo**, con el texto que propuso el controlador:
   > «Se interrumpió al girar el móvil. Vuelve a tocar «Activar huella».»

   También salta con otros cambios de configuración, como el tema del sistema o la ventana flotante. Si se quiere más general: «Se interrumpió al girar el móvil o cambiar la pantalla…».
4. `redactCanvasDescription(n)` (`tools/ToolTexts.kt:212`), **nuevo**. Solo lo lee TalkBack:
   > «Imagen para tachar. Arrastra el dedo sobre lo que quieras tapar. Bloques puestos: N.»
5. **Lugar nuevo para un texto existente:** `PASSWORD_CHANGED_FINGERPRINT_FAILED` sale ahora también dentro del diálogo «Contraseña maestra cambiada» (`ui/screens/SettingsScreen.kt:303`). El texto no cambia.
6. **Documentación:** siete líneas nuevas en «Comprobaciones manuales en el móvil» (`README.md:174-180`). Son instrucciones de prueba y citan textos de la app tal cual.

## Revisión experto-seguridad-cripto

- **Secretos y estado guardado.**
  - PIN, códigos y respuestas viven en un `ViewModel` (`ui/screens/CodesScreen.kt:141`), nunca en `rememberSaveable` ni en el `Bundle`. Lo mismo pasa con los bitmaps y los bytes de imagen (`RedactScreen.kt:206`, `PhotoCleanScreen.kt:138`).
  - Los únicos `rememberSaveable` que quedan son datos no secretos de `AppNavHost` (pestaña y aviso). Los tres de A6 se han quitado de `SettingsScreen`.
- **I-1** usa el reloj monótono, así que cambiar la hora del móvil no deja los secretos en pantalla (`CodesScreen.kt:218-221`, `CodesSession.kt:40`).
  - La regla «la bóveda se bloquea» cuenta solo con la pantalla visible (`CodesSession.kt:233`). Salir de la app siempre bloquea la bóveda (`MainActivity.kt:62`); si ese bloqueo borrara, se perdería el uso de copiar e ir a la web, que I-1 quiere conservar. Ese caso lo cubre la regla del tiempo fuera.
  - `onCleared` suelta todas las referencias (`CodesSession.kt:251`). Son `String` y no se pueden poner a cero, como ya pasaba antes.
- **Biometría.** La regla del `CryptoObject` se mantiene: el cifrador se inicia antes de `authenticate`, y `doFinal` se hace en el callback (`BiometricVault.kt:401-423`). No se toca `KeyPlacement` ni `nextAfterFailure`/`unlockBlamesStrongBox` (pasa `KeyPlacementTest`). No se añade `setUnlockedDeviceRequired`. En el alta interrumpida, `end` sigue borrando las dos copias de la clave y la clave del Keystore (`BiometricVault.kt:245`).
- **Entrada no fiable.**
  - `readAtMost` nunca pasa del límite aunque el proveedor mienta, y está probado con tamaños de menos, de más, negativos y por encima del límite (`StreamsTest`).
  - `MAX_IMAGE_BYTES` sigue igual.
  - La falta de memoria tiene su propio texto fijo, sin detalles internos (`ImageLimits.kt:35`).
  - La memoria nativa queda documentada (M-8, `RedactRender.kt:78`).
- **Formatos, red y logs.** No se toca ningún formato (`SG1:`, `.sgenc`, `seguridad-vault`, `seguridad-backup`, `CP1:`, `.cpenc` ni `criptopanel-vault`). No hay conexiones ni dependencias nuevas, y no hay logs (`grep`: 0). Los permisos del APK no cambian (`aapt2`). No hay componentes exportados nuevos. `FLAG_SECURE` no se toca.
- **Copias automáticas.** `Notice` evita perder un resultado entre que se muestra y se cierra (`core/Notice.kt`). «Dropped» se decide de forma atómica (`SettingsStore.kt:55`). «Done» sigue saliendo solo de un registro de sustitución (`AutoBackup.kt:446`), con tests.

## Revisión Ingeniero Móvil Senior

- **Ciclo de vida.**
  - Los `ViewModel` son de la `NavBackStackEntry`: sobreviven a la recreación y a cambiar de pestaña con `saveState`, y se limpian al salir de la pila.
  - El trabajo que debe sobrevivir a un giro corre en `viewModelScope`: abrir y decodificar imágenes, y escribir la nota. La sustitución de A6 corre en `appScope`.
  - Guardar y compartir siguen en el `scope` de la pantalla, porque necesitan la actividad. Si un giro los corta, descartan el documento, como antes.
- **Bitmaps.** La vista previa ya no se recicla al recomponer por un giro, que ahora la sigue dibujando. Se recicla al cambiar de imagen (`RedactScreen.kt:469`) y en `onCleared` (`RedactScreen.kt:268`). Navigation desecha el contenido antes de destruir la entrada, así que no se dibuja un bitmap ya reciclado.
- **ON_START y ON_STOP en Compose.** `LifecycleEventEffect` repite ON_START al componer. La parada vale una sola vez y se usa la primera: el `onDispose` de otra pestaña o de un giro no la mueve (`CodesScreen.kt:218-223`; test `theFirstStopOfATripCounts`).
- **Hilos.**
  - El `init` del cifrador va en `Default` y el aviso de huella en Main (M-5).
  - La lista BIP-39 se lee en `IO` (M-10).
  - El estado de Compose solo se escribe en Main, desde `viewModelScope`.
  - `MasterChanged` escribe en un `StateFlow` desde `appScope`, con IO, y eso es seguro entre hilos.
- **Edge-to-edge e IME.** `imePadding()` está antes de `verticalScroll`. La barra inferior ya se consume con `consumeWindowInsets` (`AppNavHost.kt`), así que no hay doble margen (M-9).
- **minSdk 26.** `systemGestureExclusion` no hace nada antes de la API 29. No se usa ninguna API nueva sin comprobar la versión.
- **Accesibilidad.** El lienzo tiene descripción y el número de bloques. Los emojis de las fichas no se leen.

## Dudas que quedan

- **Bloqueo de la bóveda e I-1.** «El bloqueo de la bóveda borra» se ha interpretado como «con la pantalla visible». Un bloqueo con la app en segundo plano, o desde otra pestaña, lo decide la regla del tiempo fuera. Borrar en cualquier bloqueo rompería el uso de copiar y volver. Se puede ajustar si el controlador quiere otra cosa.
- **Bloqueo por inactividad con la pantalla delante.** Con la bóveda abierta, si el bloqueo por inactividad salta mientras el usuario escribe preguntas durante más del tiempo de bloqueo, la pantalla se vacía. Es lo que pide la regla, pero puede sorprender.
- **Diálogo de huella retenido (M-4).** No se cierra el diálogo de huella que conserva biometric 1.1.0, porque cerrarlo en `onDestroy` es arriesgado sin un móvil para probarlo. Si el usuario pone el dedo en él, no pasa nada (el flujo ya terminó); el aviso le dice que vuelva a tocar «Activar huella».
- **M-8.** Solo está documentado. Hace falta la prueba en un móvil de 3 o 4 GB.

---

# Fix round 1

**Commit:** `0425a88` 1.3.0: Ingeniero Móvil Senior, revisión 1 de CU-B. Sin push.

## Cambios

| Punto | Cómo | archivo:línea |
|---|---|---|
| Important 1 | `AppContainer.foreground` se pone a false en `onStop` antes de `lock(LockReason.LEFT_APP)`, y a true en `onStart`, después de `lockIfIdle`. Así un bloqueo por el tiempo pasado fuera sigue la regla del tiempo fuera. `VaultStore.lock(reason)` guarda en el momento del bloqueo `lastLockReason` y `lastLockInForeground`, leído con `inForeground`. Lo hace en el bloqueo y no cuando llega el colector, para no competir con `onStart`. Cualquier bloqueo con la app en uso vacía los secretos, sea cual sea la pestaña. | `vault/VaultStore.kt:30,364,368,377,397`; `MainActivity.kt:49,66`; `AppContainer.kt:55,65`; `tools/codes/CodesSession.kt:56,253`; `ui/screens/CodesScreen.kt:167` |
| Important 2 | La sesión guarda el último uso de la pantalla con el reloj monótono (`used`): cualquier toque (`PointerEventPass.Initial`), tecla (`onPreviewKeyEvent`) o cambio en un campo. El bloqueo por inactividad (`IDLE`) solo vacía si la pantalla lleva `max(lockAfter, 60 s)` sin usarse. `MANUAL` vacía siempre. No se llama a `store.touch()`. Llamadas revisadas: «Bloquear» en Bóveda y en Contactos (`MANUAL` por defecto); cambio de maestra, importar y borrar la bóveda (`MANUAL` por defecto); `lockIfIdle` (`IDLE`); `MainActivity.onStop` (`LEFT_APP`). | `CodesSession.kt:132,253`; `CodesScreen.kt:259,264` |
| Minor 1 | `wipe()` vuelve a sacar el PIN, los códigos y las respuestas, y quita el guardado pendiente. Las webs, las preguntas, la longitud y la forma se quedan. | `CodesSession.kt:267`; `README.md:174` |
| Minor 2 | `SingleRun` (`tools/SingleRun.kt`, código puro) en `BiometricVault.unlock`. Cada turno se libera una sola vez, y liberarlo tarde no suelta un turno más nuevo. Se libera en `onKey`, en `onError`, al cancelar, si `canPrompt` da false, ante cualquier excepción o cancelación y en el ON_DESTROY de la actividad (un aviso que nunca responde). | `android/BiometricVault.kt:387,395,501,516` |
| Minor 3 | `windowInsetsPadding(WindowInsets.systemGestures.only(Horizontal))` alrededor del lienzo, además de `systemGestureExclusion`. El límite de 200 dp está documentado en el KDoc. | `ui/screens/RedactScreen.kt:572` |
| Minor 4 | `left()` desde `onDispose` cuando no es `isChangingConfigurations`: suelta la imagen o la copia. Si se está guardando o compartiendo, la suelta al terminar, salvo que la pantalla vuelva antes (`shown()`). La vista previa solo se recicla cuando la composición ya no está. | `RedactScreen.kt:397,454`; `PhotoCleanScreen.kt:274,317` |
| Minor 5 | `save()` y `share()` en `viewModelScope`, con el `contentResolver` del contexto de la aplicación (`writeDocument`). El documento a medias se borra, también al cancelar (`NonCancellable + IO`). La hoja de compartir la abre la pantalla con `PreparedShare`, porque necesita la actividad. Si no se entrega, el archivo se borra. | `RedactScreen.kt:291,334`; `PhotoCleanScreen.kt:191,223` |
| Minor 6 | Textos del Abogado (abajo) y `README.md:177`. | `tools/ToolTexts.kt:390,473` |
| Minor 7 | El `BiometricPrompt` del alta se guarda en el flujo. Con `isChangingConfigurations`, `end()` y después `cancelAuthentication()` dentro de un try/catch. | `BiometricVault.kt:257` |
| Minor 8 | El KDoc de `Notice` explica que el mismo objeto publicado dos veces (una constante `String` internada) se junta en uno, y que no importa. | `core/Notice.kt` |
| Minor 9 | `readAtMost` pone a cero `exact` tras copiarlo, el búfer de lectura, cada array superado al crecer (`GrowingBuffer`) y el suyo propio al final. | `core/Streams.kt` |
| Minor 10 | M-8 se queda solo documentado, como se aceptó. | — |

## Tests

| Test | Qué cubre |
|---|---|
| `CodesSessionTest` (15) | `aLockByHandWipesItOnThisTabOrAnother` (bloqueo en otra pestaña con la app en uso: se vacía). `theIdleLockKeepsAScreenInUse`. `theIdleLockWipesAScreenLeftUnused`. `theLockFromLeavingTheAppIsLeftToTheTimeAwayRule`. `onlyAChangeFromOpenToLockedIsALock`. `backAfterTheLockTime…`, que ahora comprueba que se quedan las webs y las preguntas. |
| `VaultStoreTest` | `each lock says why it happened` y `a lock records whether the app was in use at that moment`, este último con la carrera de `onStart`. |
| `SingleRunTest` (2) | Un segundo inicio se rechaza. Liberar dos veces, o tarde, nunca suelta un turno más nuevo. |
| `ToolTextsTest` | Los dos textos nuevos, tal cual. |
| `SourceTextsTest`, `StreamsTest` y `NoticeTest` | Siguen en verde. |
| `KeyPlacementTest` | Sin tocar (`git diff` vacío), 8/8 en verde. |

**Evidencia de rojo:**
- Primero rojo de compilación: no existían `LockSeen`, `vaultStateIs` con `lock`, `SingleRun`, `lastLockReason` ni `inForeground`.
- Después, una mutación: con la condición antigua `stoppedAt == null` en `vaultStateIs`, `CodesSessionTest` dio «15 tests completed, 4 failed». Fallaron `aLockByHandWipesItOnThisTabOrAnother`, `theIdleLockKeepsAScreenInUse`, `theIdleLockWipesAScreenLeftUnused` y `theLockFromLeavingTheAppIsLeftToTheTimeAwayRule`. Luego se restauró el código.

## Comandos y salida

Con `ANDROID_HOME=/opt/android-sdk` y `--no-daemon --max-workers=1 --offline`, sobre el árbol del commit:
- `gradle -p jvm-check test`: 922 tests, 0 fallos.
- `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets`: BUILD SUCCESSFUL, 922 tests de unidad, 0 fallos.
- **Lint:** 0 errores y 16 avisos, los mismos de antes. Dos avisos `Recycle` nuevos en el guardado movido se quitaron con `writeDocument`, que cierra el flujo de forma explícita.
- `aapt2 dump permissions`: igual que antes (`INTERNET`, `CAMERA`, `USE_BIOMETRIC`, `USE_FINGERPRINT` y el permiso de AndroidX).
- `grep` de logs: 0.

## Pruebas en el móvil, actualizadas (`README.md:174-180`)

- **«PIN y códigos»:**
  - al volver tras el tiempo de bloqueo, PIN, códigos y respuestas son nuevos, y las webs y las preguntas siguen;
  - «Bloquear» en Bóveda o Contactos con la pantalla en Herramientas: al volver, son nuevos;
  - escribiendo preguntas más tiempo que el bloqueo por inactividad no se pierde nada; con la pantalla sin tocar ese tiempo, sí.
- **«Tachar»:**
  - un arrastre desde el borde izquierdo, también en la parte de abajo de una imagen alta, dibuja un bloque;
  - guardar o compartir y girar mientras se prepara: se guarda o se comparte igual;
  - ir a otra pestaña con una imagen abierta y volver: hay que elegirla de nuevo.
- **«Activar huella»:** al girar durante el alta, el diálogo se cierra y sale «La activación de la huella se interrumpió…». Si se abre la bóveda y se toca en seguida «Usar huella», sale un solo diálogo.

## Textos para el Abogado Digital

1. `CODES_UNLOCK_TO_SAVE` (`tools/ToolTexts.kt:390`). Es el texto del Abogado con un solo añadido, «si bloqueas la bóveda»:
   > «Desbloquea la bóveda para guardarlo. Lo que ves aquí se pierde si vuelves atrás, si se cierra la app, si bloqueas la bóveda o si estás fuera de esta pantalla más tiempo que el bloqueo de la bóveda (1 minuto como mínimo).»

   Sin el añadido no era del todo cierto tras Important 1. Mientras se ve este texto la bóveda está bloqueada; pero si el usuario la abre en otra pestaña y la bloquea con «Bloquear» antes de volver, el guardado pendiente y lo generado se pierden. El bloqueo por inactividad ya queda dentro de «más tiempo que el bloqueo».

   Hay un matiz: «lo que ves aquí» ahora se refiere al PIN, los códigos y las respuestas; las webs y las preguntas se quedan (Minor 1). Si se quiere exactitud total: «Lo generado aquí (PIN, códigos y respuestas) se pierde…».
2. `FINGERPRINT_ENABLE_INTERRUPTED` (`tools/ToolTexts.kt:473`), tal cual:
   > «La activación de la huella se interrumpió porque cambió la pantalla (por ejemplo, al girar el móvil). Vuelve a tocar «Activar huella».»
3. **README (documentación):** se actualizan las líneas 174, 175 y 177, citando los textos de arriba.

## Revisión experto-seguridad-cripto (revisión 1)

- **El hueco de Important 1 está cerrado.** El motivo y el estado de la app se guardan dentro de `lock()`, bajo el mismo `synchronized`, antes de que cambie el estado (`VaultStore.kt:377`). Así, el colector del `ViewModel` lee los datos de ese bloqueo y no un `foreground` posterior. Esto incluye la carrera de `onStart` → `lockIfIdle` → `foreground = true`, cubierta por un test. Un bloqueo con la app en uso vacía la pantalla desde cualquier pestaña (`CodesSession.kt:253`).
- **Important 2 no debilita nada.** «Bloquear» vacía siempre. El bloqueo por inactividad respeta el mismo umbral que el tiempo fuera (`awayTooLong`, al menos 60 s), con el reloj monótono. La pantalla no toca la bóveda, así que el bloqueo por inactividad de la bóveda no cambia.
- **Minor 1.** Las webs y las preguntas no son secretos según el modelo de amenaza. Lo secreto (PIN, códigos, respuestas y la nota pendiente con ellos) se vacía igual que antes.
- **Biometría.**
  - `SingleRun` impide dos `Cipher.init` a la vez sobre StrongBox, y con eso un falso `markStrongBoxFailed`.
  - El contrato del `CryptoObject` y el respaldo a TEE no cambian (`KeyPlacementTest` sin tocar y en verde).
  - `cancelAuthentication` va dentro de un try/catch y después de `end()`, que ya borró las dos copias de la clave y la clave del Keystore. Los callbacks ven `flow.done`.
- **Memoria y archivos.**
  - `readAtMost` pone a cero todo array intermedio (`Streams.kt`).
  - Un documento a medias se borra también al cancelar.
  - Una copia preparada para compartir que no se llega a entregar se borra.
  - `MAX_IMAGE_BYTES` sigue igual.
- **Sin cambios en el resto.** Ningún formato, permiso, dependencia ni componente exportado nuevo; ningún log. `FLAG_SECURE` no se toca.

## Revisión Ingeniero Móvil Senior (revisión 1)

- **Ciclo de vida.**
  - `foreground` sigue `onStart` y `onStop` de la única actividad. Con `isChangingConfigurations` pasa a false y vuelve a true sin bloqueo en medio.
  - `left()` solo se llama cuando la composición se va sin cambio de configuración. Al recrear la actividad, `isChangingConfigurations` ya es true cuando se desecha la composición.
  - Un guardado o una preparación de compartir en curso terminan antes de soltar nada, salvo que la pantalla vuelva (`shown()`). No se recicla ningún bitmap que siga en pantalla: el contenido de Navigation se desecha después de la animación de salida.
- **Concurrencia.**
  - Todo el estado de Compose se escribe en Main (`viewModelScope`).
  - `lastLockReason` y `lastLockInForeground` son `@Volatile` y se escriben bajo `synchronized(opening)`.
  - `SingleRun` usa CAS.
  - El observador de ON_DESTROY de `unlock` se quita en `finish`.
- **Compartir.** La hoja del sistema se abre desde la actividad. El `ViewModel` solo prepara el archivo y lo entrega una vez (`PreparedShare`, comparado por identidad).
- **Insets.** El margen de los gestos horizontales solo afecta a la caja del lienzo. Las columnas desplazables mantienen `imePadding` (M-9).
- **Por comprobar en el móvil.**
  - `cancelAuthentication` de biometric 1.1.0 durante ON_DESTROY: si el sistema restaura el fragmento del aviso en la actividad nueva, debe cerrarse solo. Si no, el aviso sigue sin efecto, porque el flujo ya terminó.
  - Que la recreación con «No conservar actividades» suelte la imagen. Es lo esperado: falla cerrado.

## Dudas que quedan (revisión 1)

- **CODES_UNLOCK_TO_SAVE:** el añadido «si bloqueas la bóveda» y el matiz de «lo que ves aquí» necesitan el visto bueno del Abogado.
- **cancelAuthentication** está sin probar en un móvil.
- **Volver a la pestaña de Herramientas** obliga a elegir de nuevo la imagen de «Tachar» o «Limpiar foto». Es lo que pide Minor 4, pero es un cambio de comportamiento visible.

---

# Fix round 2

**Commit:** `134a5c6` 1.3.0: Ingeniero Móvil Senior, revisión 2 de CU-B. Sin push.

## Cambios

| Punto | Cómo | archivo:línea |
|---|---|---|
| 1. Compartir tras salir | Si se ha salido de la pantalla (`leftWhileBusy`) cuando termina `prepare`, no se pone `shareReady`. El `finally` borra el archivo preparado, quita `busy`/`working` y suelta la imagen (`releaseIfLeft`). Si la pantalla vuelve antes (`shown()`), se comparte, porque el usuario lo pidió. No hay test de JVM: estos ViewModel están en `ui/` y usan `Uri`, `Context` y `Bitmap`. | `ui/screens/RedactScreen.kt:347`; `ui/screens/PhotoCleanScreen.kt:232` |
| 2. Giro durante `Cipher.init` | El ON_DESTROY solo libera el guard si el aviso ya se mostró (`promptShown`). Antes de eso, el init en curso termina por `onEnd`, porque `canPrompt` da false con la actividad destruida. Así, el arranque automático de la actividad nueva no puede lanzar un segundo `Cipher.init` en StrongBox. `SingleRun` no cambia, y sus 2 tests ya cubren que un segundo inicio se rechaza mientras el turno no se libera; no hacía falta otro. | `android/BiometricVault.kt:400,406,421` |
| 3. Giro y primer plano | `if (!isChangingConfigurations) container.foreground = false`, con comentario para el caso del giro. El test del bloqueo por inactividad en `onStart` (`VaultStoreTest`, «a lock records whether the app was in use at that moment») sigue en verde: el orden de `onStart` no cambia. | `MainActivity.kt:68` |
| 4. KDoc | `RedactViewModel` ya dice que guardar y preparar el compartir van en el `viewModelScope`. Partida la línea larga de `CodesSession`. | `RedactScreen.kt:208`; `tools/codes/CodesSession.kt:66` |
| 5. `exact` a cero si falla | try/catch(`Throwable`) alrededor de la lectura al array exacto: lo pone a cero y vuelve a lanzar. Test nuevo, `aReadThatFailsPartwayThrowsAndReturnsNothing`. | `core/Streams.kt:24` |
| 6. `discardDocument` fuera del hilo principal | Todas las ramas de guardar (`BoxesChanged`, `PhotoFailure`, `Exception`) y la de «imagen perdida» usan IO con `NonCancellable`. | `RedactScreen.kt:294,316-324`; `PhotoCleanScreen.kt:193,212` |
| 7. Textos | `CODES_UNLOCK_TO_SAVE` tal cual, con `ToolTextsTest` al día. En `README.md:177`, «sin «La huella no responde…»». | `tools/ToolTexts.kt:390` |

## Tests y verificación

Con `ANDROID_HOME=/opt/android-sdk` y `--no-daemon --max-workers=1 --offline`:
- `gradle -p jvm-check test`: 923 tests, 0 fallos.
- `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets`: BUILD SUCCESSFUL in 4m 15s, con 923 tests de unidad y 0 fallos.
- **Lint:** 0 errores y 16 avisos, los mismos de antes.
- **Tests que cubren esta ronda:**

  | Test | Resultado |
  |---|---|
  | `CodesSessionTest` | 15/15 |
  | `VaultStoreTest` | 35/35 |
  | `SingleRunTest` | 2/2 |
  | `ToolTextsTest` | 15/15 |
  | `SourceTextsTest` | 3/3 |
  | `StreamsTest` | 6/6 |
  | `KeyPlacementTest` | 8/8, sin tocar desde `08c5d3e` (`git diff` vacío) |

- `grep` de logs: 0.

## Pruebas en el móvil (añadidas)

- **«Tachar» o «Limpiar foto»:** tocar «Compartir» e ir en seguida a otra pestaña. Al volver no se abre ninguna hoja de compartir y hay que elegir la imagen de nuevo.
- **Huella con StrongBox:** con la bóveda bloqueada, girar el móvil justo al abrirse la pantalla de desbloqueo. Debe salir un solo diálogo de huella, sin «La huella no responde…», y el desbloqueo debe funcionar.

## Textos para el Abogado Digital

1. `CODES_UNLOCK_TO_SAVE` (`tools/ToolTexts.kt:390`), con el texto que propusieron el Abogado y el revisor, tal cual:
   > «Desbloquea la bóveda para guardarlo. El PIN, los códigos y las respuestas se pierden si vuelves atrás, si se cierra la app, si bloqueas la bóveda o si estás fuera de esta pantalla más tiempo que el bloqueo de la bóveda (1 minuto como mínimo).»

   Es fiel al código: solo se borran el PIN, los códigos y las respuestas (`CodesSession.kt:267`), y las webs y las preguntas se quedan.
2. **README.md:177:** «sin «La huella no responde…»», que cita el principio de `FINGERPRINT_NOT_RESPONDING`.

## Revisión experto-seguridad-cripto (revisión 2)

- **Huella.** Nunca hay dos `Cipher.init` a la vez sobre StrongBox, tampoco al girar: el guard solo lo libera el final real de ese desbloqueo (`BiometricVault.kt:406,421`). Con eso se evita un `markStrongBoxFailed` falso. El `CryptoObject` y el respaldo a TEE no cambian (`KeyPlacementTest` sin tocar).
- **Primer plano.** Un giro ya no pone `foreground` a false (`MainActivity.kt:68`). Antes, un bloqueo en esa ventana no podía ocurrir, porque al girar no se bloquea, así que no se pierde nada. Salir de la app sigue poniéndolo a false antes del bloqueo `LEFT_APP`.
- **Datos que se quedan.**
  - Una copia preparada después de salir de la pantalla se borra, y no se abre después ninguna hoja de compartir sin que se haya pedido (`RedactScreen.kt:347`, `PhotoCleanScreen.kt:232`).
  - Un documento a medias se borra en IO y sin poder cancelarse.
  - `readAtMost` no deja bytes leídos en un array perdido si la lectura falla (`Streams.kt:24`).
- **Sin cambios en el resto.** Ningún formato, permiso, dependencia ni componente exportado nuevo; ningún log. `FLAG_SECURE` no se toca.

## Revisión Ingeniero Móvil Senior (revisión 2)

- **Hilo principal.** No queda ninguna llamada al proveedor de documentos en el hilo principal en los caminos de guardar.
- **Ciclo de vida.**
  - El caso «compartir terminado con la pantalla fuera» suelta la imagen y no deja `shareReady` colgado.
  - `onShown` se marca justo antes de `authenticate`, en Main, que es el mismo hilo que el observador. No hay carrera.
- **Comentarios.** Los KDoc de `RedactViewModel` y `CodesSession` cuentan lo que hace el código.
- **Por comprobar en el móvil.** Las dos pruebas añadidas de arriba.
