# Task 12: Teclado y portapapeles (A7 y A14). Informe

**Estado:** DONE_WITH_CONCERNS (preocupaciones menores, abajo).
**Commit:** `3324a8b 1.3.0: el teclado no aprende de ningún campo, aviso del portapapeles en Android 8 a 12` (autor Claude <noreply@anthropic.com>, Co-Authored-By; sin enlaces). No se ha hecho push.

## Qué se ha hecho

### A7: NoLearningInput
- `app/src/main/java/app/seguridad/ui/NoLearningInput.kt`: `@Composable fun NoLearningInput(content)`. Llama a `InterceptPlatformTextInput` con un interceptor (`:23-31`). El interceptor envuelve la petición en un `PlatformTextInputMethodRequest` cuyo `createInputConnection(outAttributes)` llama primero a la original y después hace `imeOptions = imeOptions or IME_FLAG_NO_PERSONALIZED_LEARNING` (`:28`). El indicador se añade con OR después de que el campo rellene sus opciones: el tipo de teclado, la acción y `autoCorrectEnabled` se conservan.
- La API es `@ExperimentalComposeUiApi` en Compose UI 1.9.0 (BOM 2025.08.00, `gradle/libs.versions.toml:8`). Se usa `@OptIn(ExperimentalComposeUiApi::class)`, como hace el código con `ExperimentalMaterial3Api`. No hay dependencias nuevas ni cambios de versión.
- `MainActivity.kt:43`: `NoLearningInput { AppNavHost(container); ClipboardHistoryNotice(container) }` dentro de `SeguridadTheme`.
- Se corrigen los comentarios equivocados en `VaultScreen.kt:380-381` y `ContactsScreen.kt:231-232`. Se corrige también el de `VaultEntryScreen.kt:298-299`, que decía «Compose cannot force it».

### Cobertura de los Dialog: evidencia (javap sobre `ui-android-1.9.0`, el jar transformado de la caché de Gradle)
1. El interceptor viaja en un CompositionLocal. `PlatformTextInputModifierNodeKt` tiene `private static final ProvidableCompositionLocal<ChainedPlatformTextInputInterceptor> LocalChainedPlatformTextInputInterceptor`, creado con `staticCompositionLocalOf`. `InterceptPlatformTextInput` lo aplica con `CompositionLocalProvider`. `establishTextInputSession` lo lee de `LayoutNode.getCompositionLocalMap()` del nodo del campo, es decir, de la composición donde vive el campo.
2. `androidx.compose.ui.window.Dialog` (`AndroidDialog_androidKt`) llama a `rememberCompositionContext()` y después a `DialogWrapper.setContent(CompositionContext, content)`. Este pasa a `DialogLayout.setContent`, que llama a `setParentCompositionContext(...)`. La composición del diálogo es hija de la composición que lo declara y hereda sus CompositionLocals. Con eso, los campos de un `Dialog` o `AlertDialog` declarados dentro de `NoLearningInput` ven el interceptor, aunque estén en otra ventana con su propio Owner.
3. Los popups (`DropdownMenu`, `ModalBottomSheet`) usan el mismo mecanismo de composición padre.

**Conclusión:** la envoltura en la raíz cubre los diálogos y no hace falta un helper aparte. La comprobación final es la del móvil: la lista manual del README incluye ahora un campo de un diálogo.

### A14: aviso del portapapeles
- `tools/ClipboardNotice.kt:11`: `needsClipboardHistoryNotice(sdk, accepted) = sdk < 33 && !accepted`.
- `tools/ClipboardNotice.kt`: `ClipboardNoticeRequest` guarda la copia pendiente solo en memoria, en un campo privado.
  - La interfaz solo ve `asking: StateFlow<Boolean>`; el texto nunca entra en el estado de Compose, en `rememberSaveable` ni en un Bundle.
  - `accept()` entrega el texto una sola vez (`:40`). `cancel()` y `drop()` lo borran (`:52`).
  - `acceptedNow` (`:30`) evita repetir el aviso en la misma ejecución si el guardado falla o todavía no ha terminado.
- `settings/Settings.kt:64`: `val clipboardHistoryNoticeAccepted: Boolean = false`.
- `android/Clipboard.kt:41`: `copySensitive` consulta `needsClipboardHistoryNotice(SDK_INT, ajuste || acceptedNow)`. Si hace falta el aviso, llama a `ask(text)` y devuelve `false` sin copiar. `copySensitiveNow` (`:56`) es la copia de antes, sin cambios.
- `ui/ClipboardHistoryNotice.kt`: un único `AlertDialog` en la raíz (`MainActivity.kt:43`).
  - Usa `FilterObscuredTouches` y muestra `clipboardHistoryNoticeText(settings.clipboard.seconds)`, con los segundos elegidos ahora en Ajustes (15, 45 o 90).
  - «Entendido, copiar» toma el texto, lo copia y guarda el ajuste en `appScope`, así que un giro no cancela el guardado.
  - «Cancelar», tocar fuera y Atrás no copian nada.
- La copia pendiente se descarta en tres casos:
  - al bloquearse la bóveda: un colector en `AppContainer.kt:124` sigue `vault.state` y llama a `drop()` en cada paso de abierta a no abierta;
  - al salir de la app: `MainActivity.kt:78`, con la misma condición que el bloqueo por salir;
  - al aceptar y al cancelar.
- Desviación respecto a «CompositionLocal en la raíz»: `copySensitive(context, text)` no es composable y se llama desde 14 sitios. Para no tocar cada pantalla, la petición vive en `AppContainer.clipboardNotice`, como el precedente de `masterChanged` (M-3). El diálogo se compone una sola vez en la raíz. El efecto que se buscaba (ninguna pantalla cambia y el diálogo está en la raíz) se cumple, y además la petición sobrevive a un giro.
- `QrInspectScreen.kt:291,321`: el snackbar «Copiado tal cual…» solo sale si de verdad se ha copiado en ese momento (ahora `copySensitive` devuelve un Boolean).

### Puntos pequeños añadidos
- (a) `BiometricVault.kt:476`: la corrutina de `unlockOnce` captura ahora también `Throwable` después de `Exception`. Llama a `onEnd()` (= `finish()`, que libera el guardia de SingleRun) y vuelve a lanzar el error. Se hace lo mismo en el `try` síncrono de `unlock` (`:426`, con `finish()`). `Ticket.release()` es idempotente (`SingleRun.kt:16-18`). No cambian el contrato de `CryptoObject` ni la lógica de StrongBox y la zona segura del procesador. `KeyPlacementTest` no se ha tocado y sigue en verde.
- (b) README, lista manual: los puntos de Gboard (con un campo de un diálogo), el aviso A14 en Android 8 a 12 y el giro durante la preparación del desbloqueo con huella.

## TDD
- Rojo: `gradle -p jvm-check test` no compilaba. Daba `Unresolved reference 'clipboardHistoryNoticeAccepted'`, `'needsClipboardHistoryNotice'`, `'ClipboardNoticeRequest'` y `'clipboardHistoryNoticeText'`, 15 errores `e:` en total.
- Verde: `ClipboardNoticeTest` con 5 tests:
  - las API 26, 32, 33 y 34, aceptado o no;
  - aceptar entrega el texto una sola vez;
  - cancelar y bloquear lo descartan sin marcarlo como aceptado;
  - una copia más nueva sustituye a la anterior;
  - el texto con 15, 45 y 90 s.
- Verde: `SettingsTest` con 3 tests nuevos:
  - el valor por defecto es `false`;
  - un `settings.json` con la forma de la 1.2.2, sin el campo, se lee con los valores del usuario y el campo a `false`;
  - el ajuste persiste.

## Verificación completa (`--offline --no-daemon --max-workers=1`, `ANDROID_HOME=/opt/android-sdk`)
- `gradle -p jvm-check test`: BUILD SUCCESSFUL, 931 tests, 0 fallos, 0 errores, 0 omitidos.
- `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets`: BUILD SUCCESSFUL, 931 tests, 0 fallos.
  - Lint da 17 avisos. Ninguno es de los archivos de esta tarea: son de AndroidManifest, DistrustedCertificates, mipmap y libs.versions.toml.
  - No hay avisos `w:` del compilador en los archivos nuevos.
- Sin cambios en el manifiesto, los permisos ni las dependencias. Ningún formato cambia.

## Archivos cambiados
- Nuevos:
  - `app/src/main/java/app/seguridad/ui/NoLearningInput.kt`
  - `app/src/main/java/app/seguridad/ui/ClipboardHistoryNotice.kt`
  - `app/src/main/java/app/seguridad/tools/ClipboardNotice.kt`
  - `app/src/test/java/app/seguridad/tools/ClipboardNoticeTest.kt`
- Modificados:
  - `MainActivity.kt`, `AppContainer.kt`
  - `android/Clipboard.kt`, `android/BiometricVault.kt`
  - `settings/Settings.kt`, `tools/ToolTexts.kt`
  - `ui/screens/VaultScreen.kt`, `ContactsScreen.kt`, `VaultEntryScreen.kt` (comentarios) y `QrInspectScreen.kt`
  - `app/src/test/.../settings/SettingsTest.kt`
  - `README.md`

## Autorrevisión y preocupaciones
1. **Exactitud del texto A14.** «Seguridad lo borra del portapapeles a los N s» es cierto mientras el proceso sigue vivo y el portapapeles conserva nuestra copia. Si el sistema mata la app, se borra al volver a abrirla (`clearStaleCopy`). Si otra app copia algo antes, no se borra lo de esa otra app (es lo correcto). El texto del diseño se ha usado tal cual; el Abogado Digital puede valorar si quiere matizarlo.
2. **«N»:** se usa el valor actual de Ajustes cuando se muestra el aviso. Si el usuario cambia el ajuste con el diálogo abierto (no puede, porque es modal), el texto se recompone.
3. El snackbar «Copiado tal cual…» del QR no sale tras aceptar el aviso, solo cuando la copia es directa. Pasa como mucho una vez por instalación y solo en Android 8 a 12.
4. **Carrera mínima:** el `drop()` por bloqueo llega por un colector en `appScope` (Dispatchers.IO). Hay una ventana de milisegundos entre el bloqueo y el `drop` en la que «Entendido, copiar» aún copiaría. Al salir de la app, el `drop()` es síncrono en `onStop`.
5. **A7 depende del teclado:** `IME_FLAG_NO_PERSONALIZED_LEARNING` es una petición, y el código lo dice así. La prueba con Gboard está en la lista manual.
6. Desviación del CompositionLocal, explicada arriba.

## Textos para el Abogado Digital
- `clipboardHistoryNoticeText(seconds)`, `tools/ToolTexts.kt:480-482`. Con 45 dice: «En este Android, el teclado puede guardar lo copiado en su historial. Seguridad lo borra del portapapeles a los 45 s, pero no del historial del teclado.» Las variantes de 15 y 90 s están en `ALL_TOOL_TEXTS` (`:531`).
- `CLIPBOARD_HISTORY_ACCEPT`, `tools/ToolTexts.kt:485`: «Entendido, copiar»
- `CLIPBOARD_HISTORY_CANCEL`, `tools/ToolTexts.kt:488`: «Cancelar»
- README, 3 puntos nuevos de la lista manual, al final de la lista «Lo que no se puede probar en la CI»: Gboard, el aviso A14 en Android 8 a 12 y el giro durante el desbloqueo con huella. El último es el texto (b), tal cual.
- Los textos pasan `ToolTextsTest` (15/15) y `SourceTextsTest` (3/3).

## Revisión experto-seguridad-cripto
- **Modelo de amenaza:**
  - A7: un teclado de terceros que aprende palabras (nombres, URL, búsquedas) y las sincroniza o las sugiere después.
  - A14: el historial del portapapeles del teclado en Android 8 a 12, que guarda los secretos copiados más allá del borrado de la app.
  - Ninguna de las dos medidas protege de un teclado malicioso, que puede ignorar el indicador y registrarlo todo. Así queda dicho en el KDoc de `NoLearningInput.kt:10-16`.
- **A7:**
  - El indicador se aplica a todos los campos, también en los diálogos (evidencia de bytecode arriba).
  - Es OR después de la petición original (`NoLearningInput.kt:26-29`): no se pierden `KeyboardType.Password` ni `autoCorrectEnabled=false`.
- **A14:**
  - El secreto pendiente no sale de la memoria: ni Bundle, ni `rememberSaveable`, ni disco, ni log (`ClipboardNotice.kt:21`, `ClipboardHistoryNotice.kt:41`).
  - Se entrega una sola vez y se borra al cancelar, al bloquearse la bóveda o al salir (`AppContainer.kt:124-128`, `MainActivity.kt:78`).
  - Cancelar no copia nada.
  - El diálogo filtra los toques tapados (`ClipboardHistoryNotice.kt:35`), contra el tapjacking del botón de aceptar.
  - En Android 13 y posteriores, el aviso nunca sale y se mantiene `EXTRA_IS_SENSITIVE` (`Clipboard.kt:56-61`).
- **Formatos:** `Settings` solo gana un campo con valor por defecto. `appJson` tiene `ignoreUnknownKeys`, así que una 1.2.2 que lea un archivo nuevo ignora el campo. El test con un JSON de la 1.2.2 está en verde. No se toca ningún formato cifrado.
- **(a)** Un `Error` ya no deja bloqueado el guardia de la huella (`BiometricVault.kt:426,476`) y se sigue propagando. No se oculta.
- Sin red, permisos ni dependencias nuevas. Solo APIs de Compose y Android.

## Revisión Ingeniero Móvil Senior
- **Ciclo de vida:** la petición vive en `AppContainer` (una por proceso) y sobrevive a un giro. El texto no se pierde a medias ni se duplica: `accept()` es `@Synchronized` y entrega una sola vez. El guardado del ajuste va en `appScope`, que no se cancela con la composición (`ClipboardHistoryNotice.kt:44`).
- **Muerte del proceso:** la copia pendiente se pierde, como debe ser, porque no está en un Bundle. El aviso sigue sin aceptar y vuelve a salir en la siguiente copia.
- **Concurrencia:** `asking` es un `MutableStateFlow` y el texto está protegido por `@Synchronized`. Los avisos `collectAsStateWithLifecycle` paran en segundo plano y el `drop` por bloqueo no depende de ellos (`AppContainer.kt:125-127`).
- **Compose:**
  - El diálogo solo se compone mientras `asking` es true (`ClipboardHistoryNotice.kt:30`).
  - El interceptor es un `private val` de nivel superior, sin estado, así que no provoca recomposiciones nuevas.
  - `@OptIn(ExperimentalComposeUiApi::class)` sigue el patrón del código (`NoLearningInput.kt:17,23`). La API puede cambiar al subir Compose; tiene tests del compilador, pero no un test de UI.
- **Compatibilidad:** `copySensitive` ahora devuelve un Boolean. Los 12 sitios que lo ignoran compilan sin cambios, porque en una lambda Unit el valor se descarta. Los 2 del QR lo usan.
- **(a):** el patrón `catch Throwable → liberar → relanzar` aparece una vez en cada ruta (`BiometricVault.kt:426,476`). `finish()` y `release()` son idempotentes.

## Fix round 1

**Commit:** `b78ccfa 1.3.0: el teclado no aprende de ningún campo, aviso del portapapeles en Android 8 a 12, revisión 1`. No se ha hecho push.

1. **Bloqueo entre la petición y la aceptación.** Se reutiliza el `epoch` de VaultStore, expuesto como `val lockEpoch` (`VaultStore.kt:137`), que sube con cada `lock()`.
   - `ClipboardNoticeRequest(lockEpoch)` lo apunta en `ask()` (`ClipboardNotice.kt:60`).
   - `accept()` devuelve false y no copia si el valor ha cambiado (`:73`). Cubre también un bloqueo seguido de un desbloqueo, que el colector puede saltarse porque StateFlow fusiona los estados.
   - El colector que descarta la copia sigue en `AppContainer.kt:126`.
   - Tests: `aLockBetweenAskAndAcceptRefusesTheCopy` y `aLockAndUnlockBetweenAskAndAcceptStillRefusesTheCopy`.
2. **onCopied.** `copySensitive(context, text, onCopied = null)` (`Clipboard.kt:41`). La petición guarda el callback junto al texto y lo llama justo después de escribir (`ClipboardNotice.kt:78`). `drop` y `cancel` lo olvidan.
   - El QR (`QrInspectScreen.kt:291,321`) pasa el snackbar como `onCopied`, así que sale también tras aceptar el aviso.
   - Tests: `theGateWritesNothingUntilAccepted` (se llama una vez, y solo tras aceptar), `aDroppedCopyAndItsCallbackAreForgotten` y `aNewerCopyReplacesTheOneWaiting`.
   - La lambda del QR puede quedar retenida unos instantes si se gira el móvil con el aviso abierto. Mostraría el snackbar en un scope ya cancelado, que no hace nada. No se queda en memoria más allá de aceptar, cancelar o descartar.
3. **Prueba de Gboard al subir Compose.** Nota en la lista del README (`README.md:182`) y comentario junto al BOM (`gradle/libs.versions.toml:8`), con el texto «Al subir Compose, repetir la prueba de Gboard (campo normal y campo en un diálogo)».
4. **Puerta comprobable en la JVM.** Función pura `copyOrAskNotice(text, notice, accepted, sdk, write, onCopied)` (`ClipboardNotice.kt:18`). `copySensitive` la llama con `Build.VERSION.SDK_INT` y `write = copySensitiveNow(applicationContext, …)` (`Clipboard.kt:45`). Los valores reales los pone `copySensitive`, porque `Build` no existe en jvm-check. El diálogo llama solo a `notice.accept()` (`ClipboardHistoryNotice.kt:39`); la escritura la hace la propia petición.
   - Tests: `theGateWritesNothingUntilAccepted` (nada escrito hasta aceptar y, después, `acceptedNow` deja pasar la siguiente copia), `theGateCancelledWritesNothing` y `theGateCopiesAtOnceWhenNoNoticeIsNeeded` (API 33 y aviso ya aceptado).
5. **Texto A14 decidido** (`ToolTexts.kt:480-482`), aplicado tal cual con los segundos reales. `ToolTextsTest.clipboardHistoryNoticeIsTheReviewedTextWithTheRealSeconds` comprueba la frase con 45 s, las variantes de 15, 45 y 90 s en `ALL_TOOL_TEXTS` y los dos botones.
   - **Comprobación de «al volver a abrirla»:** `MainActivity.kt:64-66` llama a `clearStaleCopy(this)` en `onWindowFocusChanged(hasFocus = true)`. Esto pasa también la primera vez que la ventana gana el foco tras un arranque en frío.
   - `Clipboard.kt:89-92` borra la copia si la etiqueta es `CLIP_LABEL` («Seguridad») y su `ClipDescription.timestamp` es de hace más que el plazo elegido.
   - `getTimestamp` existe desde la API 26, que es `minSdk` (`app/build.gradle.kts:28`).
   - Matiz: el borrado se hace al volver a abrir la app y no antes. El texto dice exactamente eso.

**Verificación completa** (offline, `--no-daemon --max-workers=1`):
- `gradle -p jvm-check test`: BUILD SUCCESSFUL, 935 tests, 0 fallos.
- `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets`: BUILD SUCCESSFUL, 935 tests, 0 fallos.
- Resultados por clase: ClipboardNoticeTest 8/8, ToolTextsTest 16/16, SourceTextsTest 3/3, SettingsTest 27/27 y KeyPlacementTest 8/8 (sin tocar).
- Lint: 17 avisos, los mismos de antes; ninguno en los archivos de la tarea. No hay avisos `w:` del compilador.

### Textos para el Abogado Digital (revisión final)
- `clipboardHistoryNoticeText(seconds)`, `tools/ToolTexts.kt:480-482`. Con 45 dice: «En este Android, el teclado puede guardar lo copiado en su historial. Seguridad lo borra del portapapeles a los 45 s (si Android cierra la app antes, al volver a abrirla), pero no del historial del teclado.» También existen las variantes de 15 y 90 s.
- `CLIPBOARD_HISTORY_ACCEPT`, `tools/ToolTexts.kt:485`: «Entendido, copiar»
- `CLIPBOARD_HISTORY_CANCEL`, `tools/ToolTexts.kt:488`: «Cancelar»
- `README.md:182`: «Al subir Compose, repetir la prueba de Gboard (campo normal y campo en un diálogo): `NoLearningInput` usa una API experimental (`InterceptPlatformTextInput`).»
- `gradle/libs.versions.toml:8`, comentario: «Al subir Compose, repetir la prueba de Gboard (campo normal y campo en un diálogo): NoLearningInput usa una API experimental.»

### Revisión experto-seguridad-cripto (ronda 1)
- Ya no hay ventana entre un bloqueo y una aceptación: la comparación del contador se hace dentro de la sección `@Synchronized` (`ClipboardNotice.kt:68-76`). `lock()` sube `epoch` de forma síncrona antes de cambiar el estado (`VaultStore.kt`, `lock`).
- El texto pendiente sigue solo en memoria, dentro de la petición. La función `write` captura el `applicationContext`, nunca una Activity.
- No cambian los formatos, los permisos ni las dependencias.

### Revisión Ingeniero Móvil Senior (ronda 1)
- `write` y `onCopied` se llaman fuera del bloqueo del monitor (`ClipboardNotice.kt:77-78`), así que un callback de la interfaz no puede provocar un interbloqueo.
- Un `lock()` sobre una bóveda ya bloqueada también sube el contador. Por ejemplo, al salir de la app con un selector abierto. En ese caso la aceptación se rechaza sin copiar nada; es la dirección prudente.
- La API experimental queda vigilada por la nota del README y el comentario del BOM.
