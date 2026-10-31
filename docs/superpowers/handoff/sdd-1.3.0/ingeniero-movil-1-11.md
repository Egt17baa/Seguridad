# Revisión Ingeniero Móvil Senior: tareas 1 a 11 de la 1.3.0

Revisión de puesta al día desde el punto de vista de un ingeniero Android senior, con las skills `desarrollo-movil-senior`, `seguridad-criptografia-movil` y `auditoria-seguridad-movil` (OWASP MASVS v2), junto con `experto-seguridad-cripto`. Solo lectura: no se ha tocado el árbol de trabajo ni se ha ejecutado Gradle.

## Alcance

- **Rama** `ccr-a1a6eabb-765f9g`, de `3e5afe5` (commit del plan) a `HEAD` = `126e444`. Incluye la tarea 11 en revisión (`a2b6a36`, `a2e753a` y su revisión 2, `126e444`, confirmada mientras se hacía esta revisión).
- **Archivos:** los de `git diff --stat 3e5afe5..HEAD -- app/src/main app/src/test`, sobre todo:
  - `ui/`: `PhotoCleanScreen`, `RedactScreen`, `QrInspectScreen`, `CodesScreen`, `SettingsScreen`, `IdentityScreen`, `VaultBackup`, `AppNavHost`, `ToolsScreen` y los cambios pequeños de `VaultScreen`, `VaultEntryScreen`, `PkActionsScreen` y `ContactAddScreen`;
  - `android/`: `BiometricVault`, `HeifToJpeg`, `ImageShare`, `QrFromImage` y `RedactRender`;
  - `MainActivity`, `SeguridadApp`, `AppContainer`, `AndroidManifest.xml` y `res/xml/share_paths.xml`;
  - `tools/` donde toca tamaños de imagen: `ImageLimits`, `redact/Redaction` y `qr/QrImageSize`, además de `tools/share/ShareTemp` y `core/Streams`.
- **Líneas citadas:** las de `126e444`, salvo cuando se cita `3f8955d:<ruta>`, que es el estado anterior a la revisión 2 de T11. Cuando `126e444` ya corrige algo, se dice. El árbol de trabajo está limpio.
- **Modelo de amenaza** (el del proyecto):
  - otra app en el móvil;
  - quien tenga el móvil desbloqueado en la mano;
  - un archivo importado hostil.
- **Versiones:** `minSdk 26`, `targetSdk 36` y `compileSdk 36` (`app/build.gradle.kts:23-29`); Kotlin 2.4.21 (compilador de Compose con *strong skipping*), Compose BOM 2025.08.00, biometric 1.1.0 y fragment 1.9.1.
- **No se repite** lo ya aplazado: el orden de imports de T5 y la falta de comprobación de cajas en `onSaveTarget`; los tres puntos de T7 (userinfo, snackbars, traspaso del 2FA); el `upsert` de T8; y los comportamientos de la 1.2.2.

## Resumen

| Severidad | Hallazgo | archivo:línea | Tarea |
|---|---|---|---|
| Important | Lo generado en «PIN y códigos» (PIN, códigos, respuestas) sigue en pantalla al volver a la app, sin límite de tiempo. El inspector de QR sí lo borra. | `ui/screens/CodesScreen.kt:148-159` (no hay `ON_STOP`); comparar con `ui/screens/QrInspectScreen.kt:128-131` | T8 |
| Minor | Un cambio de configuración (girar, modo oscuro del sistema, ventana flotante o pantalla dividida de MagicOS) borra la imagen y las cajas de «Tachar» y la copia de «Limpiar foto». | `ui/screens/RedactScreen.kt:200-207`, `ui/screens/PhotoCleanScreen.kt:134-138`, `AndroidManifest.xml:19-22` | T4, T5 |
| Minor | El gesto «atrás» del sistema compite con el arrastre de «Tachar» en el borde izquierdo: se sale de la pantalla y se pierden las cajas. | `ui/screens/RedactScreen.kt:441-468` | T5 |
| Minor | Si se gira el móvil mientras se cambia la maestra (Argon2id tarda segundos), se pierde el diálogo de A6. | `ui/screens/VaultBackup.kt:62,106-117`, `ui/AppNavHost.kt:118,141` | T11 |
| Minor | Si se gira con el diálogo de la huella abierto en «Activar huella», el alta se cancela sin decir nada. | `android/BiometricVault.kt:226-228,332-341` | T9 |
| Minor | `Cipher.init` de la clave de la huella (StrongBox) se hace en el hilo principal al desbloquear. | `android/BiometricVault.kt:375-377` | T9 |
| Minor | Al convertir HEIC/AVIF a 24 MP la resolución baja sin avisar (una foto de 200 MP queda en unos 24). | `android/HeifToJpeg.kt:69-72`, `tools/ToolTexts.kt:101` | T3, T4 |
| Minor | La lectura de hasta 50 MB llega a doblar la memoria (`ByteArrayOutputStream`), y un `OutOfMemoryError` se muestra como «archivo dañado». | `core/Streams.kt:4-13`, `ui/screens/PhotoCleanScreen.kt:97,195-197`, `ui/screens/RedactScreen.kt:306-308`, `ui/screens/QrInspectScreen.kt:189-191` | T4, T5, T7 |
| Minor | Pico de memoria nativa al decodificar para «Tachar»: el reescalado intermedio de `setTargetSize` y los PNG de 16 bits o HDR en `RGBA_F16` (8 bytes por píxel). A comprobar. | `android/RedactRender.kt:79-92`, `tools/redact/Redaction.kt:101-113` | T5 |
| Minor | Sin `imePadding()`: con edge-to-edge (forzado en Android 15+), el teclado puede tapar los campos del final de «PIN y códigos». A comprobar. | `MainActivity.kt:33-36`, `ui/screens/CodesScreen.kt:249-251,330-361` | T8 |
| Minor | La lista BIP-39 se lee del disco en el hilo principal, durante la composición. | `ui/screens/CodesScreen.kt:146` | T8 |
| Minor | Accesibilidad: el lienzo de «Tachar» no tiene descripción para TalkBack y los emojis de las fichas se leen en voz alta. | `ui/screens/RedactScreen.kt:441-469`, `ui/screens/ToolsScreen.kt:42-45,66` | T4, T5, T7, T8 |
| Minor | `replaceOutcomes` es un `SharedFlow` sin repetición: sin nadie suscrito (actividad recreada o cerrada), el resultado se pierde. **Ya corregido en `126e444`** (`StateFlow` y `replaceOutcomeShown`). | `3f8955d:vault/AutoBackup.kt:174-177,247`, `3f8955d:ui/AppNavHost.kt:121-128` | T11 |
| Informativa | La copia compartida solo se borra al volver a la app pasados 10 minutos; si no se vuelve, queda en la caché privada hasta el próximo arranque. | `tools/share/ShareTemp.kt:57-65`, `MainActivity.kt:50` | T3 |
| Informativa | La hoja para compartir de Android 14+ es del sistema: muestra una miniatura sin `FLAG_SECURE` y ofrece «Copiar», que pone la URI en el portapapeles. | `android/ImageShare.kt:22-30` | T3 |
| Informativa | Los decodificadores del sistema (Skia, libwebp, libheif) procesan imágenes hostiles en el proceso de la app. Lo mitiga que el selector bloquea la bóveda. | `android/RedactRender.kt:79`, `android/QrFromImage.kt:89`, `android/HeifToJpeg.kt:60`, `MainActivity.kt:58-63` | T3, T5, T7 |
| Informativa | Fuera de alcance (anterior a 1.3.0): en las compilaciones *debug*, `androidx.compose.ui.tooling.PreviewActivity` está exportada y sin `FLAG_SECURE`. | `app/build.gradle.kts:100` (`debugImplementation(libs.compose.ui.tooling)`) | — |
| Informativa | El mismo hueco del hallazgo Important existe en «Generador» (1.2.2, fuera de alcance). | `ui/screens/GeneratorScreen.kt` (no tiene `ON_STOP`) | — |

**Cuentas:** Critical 0 · Important 1 · Minor 12 (1 ya corregido en `126e444`) · Informativa 4.

## Hallazgos

### Critical

Ninguno.

### Important

#### I-1. «PIN y códigos» deja lo generado en pantalla al volver a la app (T8)

- **Dónde:**
  - `ui/screens/CodesScreen.kt:148-159`: PIN, códigos y respuestas en `remember`;
  - `ui/screens/CodesScreen.kt:131-133`: el KDoc dice «lives only in `remember`»;
  - en el archivo no hay `LifecycleEventEffect` ni ningún observador de `ON_STOP`.

  En cambio, `ui/screens/QrInspectScreen.kt:128-131` dice y hace: «A result with a secret in it … does not wait on screen for whoever picks the phone up next: it goes when the app stops».
- **Qué pasa:**
  1. La herramienta no usa la bóveda, así que el bloqueo de `MainActivity.onStop` (`MainActivity.kt:58-63`) no la tapa.
  2. Si el usuario genera una respuesta inventada o unos códigos, los copia a la web del banco y no los guarda en la bóveda, al volver a Seguridad (desde Recientes, o el lanzador con la actividad viva) siguen en pantalla.
  3. Siguen ahí hasta que muere el proceso. `FLAG_SECURE` solo quita la miniatura de Recientes, no la pantalla al abrirla.
- **Impacto:** quien tenga el móvil desbloqueado ve en claro la respuesta a la pregunta de seguridad, o los códigos de recuperación, de una cuenta que el usuario acaba de configurar. Es la amenaza «quien tenga el móvil desbloqueado» del modelo, y contradice la regla de la propia app en el inspector de QR. No hay fuga a otras apps.
- **Corrección exacta:** borrar en `ON_STOP` rompería el flujo «copiar, ir a la web y volver a por la siguiente», así que no se borra al salir. Se redibuja al **volver** solo si se estuvo fuera más que el tiempo de bloqueo de la bóveda. `CodesScreen` recibe `lockAfterMs: () -> Long` desde `AppNavHost` (`{ settings.lockAfter.ms }`; con «Al salir», un mínimo fijo de 60 s):

  ```kotlin
  var stoppedAt by remember { mutableStateOf<Long?>(null) }
  LifecycleEventEffect(Lifecycle.Event.ON_STOP) { stoppedAt = SystemClock.elapsedRealtime() }
  LifecycleEventEffect(Lifecycle.Event.ON_START) {
      val at = stoppedAt ?: return@LifecycleEventEffect
      stoppedAt = null
      // Monotonic clock: changing the phone's time does not keep the secrets on screen.
      if (SystemClock.elapsedRealtime() - at >= maxOf(lockAfterMs(), 60_000L)) {
          pin = generatePin(digits)
          codes = recoveryCodes()
          rows.forEach { it.answer = inventedAnswer(words, plain) } // The sites and questions stay; only the secrets go.
          pending = null
      }
  }
  ```

  Hace falta un test de la decisión (función pura `shouldRedraw(elapsed, lockAfter)`) y una prueba en el móvil (abajo). Si el dueño prefiere borrar siempre al salir, como en el inspector, el texto de la pantalla tiene que decir que se guarde antes de salir.
- **Referencia:** MASVS-STORAGE-2 y MASVS-PLATFORM-3 (datos sensibles expuestos en la UI); MASTG-TEST-0010 («Sensitive data in UI»).

### Minor

#### M-1. Un cambio de configuración borra el trabajo de «Tachar» y «Limpiar foto» (T4, T5)

- **Dónde:**
  - `ui/screens/RedactScreen.kt:200-207`: `picture`, `boxes` y el historial, en `remember`;
  - `ui/screens/RedactScreen.kt:218-225`: el `onDispose` recicla;
  - `ui/screens/PhotoCleanScreen.kt:134-138`;
  - `AndroidManifest.xml:19-22`: `MainActivity` sin `configChanges`.
- **Qué pasa:** al girar, cambiar el modo oscuro del sistema (`uiMode`), el idioma o el tamaño de letra, o al pasar a ventana flotante o pantalla dividida (MagicOS), la actividad se recrea. Se pierden la imagen abierta, todas las cajas dibujadas y la copia limpia. Es intencionado («never in saved state») y falla cerrado (`REDACT_LOST` y `PHOTO_LOST`), pero el usuario pierde todo su trabajo.
- **Impacto:** de uso, no de seguridad. Tras rehacer 20 cajas a mano es fácil olvidarse una.
- **Corrección exacta:** pasar `Picture` y `CleanCopy` a un `ViewModel` de la entrada de navegación (`viewModel()` en el `composable(...)`). Sobrevive a la recreación pero **no** a la muerte del proceso, así que sigue sin ir nunca al `Bundle` ni al disco. En `onCleared()`, `holder.close()` y `previewBitmap.recycle()`. Las corrutinas de renderizado y guardado pasan a `viewModelScope`, que tampoco se cancela al girar. El `DisposableEffect` de la pantalla deja de reciclar.
- **Referencia:** Android Developers, «Save UI states» y «ViewModel overview»: el ViewModel sobrevive a los cambios de configuración y no a la muerte del proceso.

#### M-2. El gesto «atrás» corta los arrastres de «Tachar» que empiezan en el borde (T5)

- **Dónde:** `ui/screens/RedactScreen.kt:441-468`. El `Canvas` ocupa todo el ancho, con solo 16 dp de margen (`RedactScreen.kt:319`). Con `targetSdk 36` el atrás predictivo está activo.
- **Qué pasa:** con navegación por gestos, la zona de atrás del sistema mide unos 24 a 32 dp desde el borde. Un arrastre que empieza en el lado izquierdo de la imagen, para tapar un nombre pegado al borde, lo coge el sistema: se sale de la pantalla y `onDispose` borra la imagen y las cajas.
- **Corrección exacta:** `Modifier.size(w, h).systemGestureExclusion().pointerInput(p) { … }`. Funciona en API 29+ y no hace nada en 26-28. Android limita la exclusión a 200 dp de alto por borde, que basta para la mayoría de las imágenes en vertical.
- **Referencia:** Android Developers, «Handle gesture navigation conflicts» (`Modifier.systemGestureExclusion`).

#### M-3. Girar durante el cambio de la maestra pierde el diálogo de A6 (T11)

- **Dónde:**
  - `ui/screens/VaultBackup.kt:62`: `val onMasterChanged by rememberUpdatedState(LocalOnMasterChanged.current)`;
  - `ui/screens/VaultBackup.kt:106-117`: se llama dentro de `NonCancellable`, después de `changeMaster` (Argon2id, segundos);
  - `ui/AppNavHost.kt:118,141`: `masterChanged` es `rememberSaveable` y la lambda escribe en **ese** estado.
- **Qué pasa:**
  1. Si la actividad se recrea durante la derivación, el `Bundle` ya se ha guardado con `masterChanged = false`.
  2. Cuando `changeMaster` termina, `onMasterChanged()` escribe en el estado del `AppNavHost` antiguo, ya desechado.
  3. La composición nueva nunca muestra el diálogo, y el snackbar también va al `SnackbarHostState` antiguo.

  El arreglo de la ronda 1 («leaving the screen … must not lose the dialog») cubre salir de la pantalla, no recrear la actividad. La revisión 2 (`126e444`) no lo cambia.
- **Impacto:** el usuario no ve el aviso de A6, que dice que las copias anteriores siguen abriéndose con la maestra antigua; es la mitigación que pide el diseño. Requiere girar durante unos segundos concretos.
- **Corrección exacta:** subir la señal a un nivel que no muera con la actividad, como ya hace `replaceOutcome` desde `126e444`: un `MutableStateFlow<Boolean>` en `AppContainer` (o en `VaultStore`), puesto a `true` dentro del `NonCancellable`, leído en `AppNavHost` con `collectAsStateWithLifecycle()` y puesto a `false` en `onClose` y `onRenew`. Así se puede quitar `LocalOnMasterChanged`.
- **Referencia:** Android Developers, «State holders and UI state»: el estado que debe sobrevivir a la UI no vive en la composición.

#### M-4. «Activar huella» se cancela sin aviso si se gira con el diálogo abierto (T9)

- **Dónde:** `android/BiometricVault.kt:226-228`, donde `ON_DESTROY` llama a `end(flow, null, stored = false, notify = false)`, y `android/BiometricVault.kt:332-341`.
- **Qué pasa:** en una recreación, `ON_DESTROY` llega también con `isChangingConfigurations`. El flujo termina, borra las dos copias y la clave, y no llama a `onDone`. El `BiometricFragment` de biometric 1.1.0 se conserva y el diálogo sigue en pantalla; si el usuario pone el dedo, `onOk` ve `flow.done` y no hace nada (`BiometricVault.kt:266`). Falla cerrado, pero sin ningún mensaje.
- **Corrección exacta:** en el observador, si `activity.isChangingConfigurations`, terminar con mensaje en vez de en silencio, y cerrar el diálogo, que ya no sirve:

  ```kotlin
  if (event == Lifecycle.Event.ON_DESTROY) {
      val rotating = flow.activity.isChangingConfigurations
      end(flow, "No se pudo activar la huella", stored = false, notify = rotating)
  }
  ```

  `onDone` del llamante (`VaultScreen.kt:154`) lanza en un `scope` ya cancelado, así que el aviso tiene que salir de un nivel superior. Lo más sencillo es no avisar y añadir a la prueba en el móvil que tras girar el interruptor sigue apagado y se puede reintentar. Como mínimo, documentarlo en el KDoc de `enable`.
- **Referencia:** androidx.biometric: «BiometricPrompt should be constructed in onCreate … to receive callbacks after configuration changes».

#### M-5. `Cipher.init` de la clave de la huella en el hilo principal al desbloquear (T9)

- **Dónde:** `android/BiometricVault.kt:375-377`, con `keyStore().load`, `getKey` y `Cipher.init(DECRYPT_MODE…)` en el hilo de la UI.
- **Qué pasa:** con la clave en StrongBox, `init` hace un `begin` en el chip seguro aparte, que en algunos móviles tarda de decenas a cientos de ms. La revisión de T9 sacó la *generación* del hilo principal, pero el *uso* sigue en él. Es la misma pauta de la 1.2.2, solo que ahora con StrongBox.
- **Corrección exacta:** hacer el `init` con `withContext(Dispatchers.Default)` en un `MainScope` local, como en `enable` (`BiometricVault.kt:242-252`), y llamar a `prompt` al volver, comprobando antes `canPrompt`. A comprobar en el Honor antes de cambiarlo: en la prueba en el móvil, medir con `adb shell dumpsys gfxinfo` o Perfetto si el desbloqueo con huella pierde frames.
- **Referencia:** Android Developers, «Keep your app responsive» (ANR y StrictMode).

#### M-6. La conversión de HEIC/AVIF reduce la resolución sin avisar (T3, T4)

- **Dónde:** `android/HeifToJpeg.kt:69-72`, que aplica `setTargetSize` a `MAX_REDACT_PIXELS`, y `tools/ToolTexts.kt:101`: `PHOTO_HEIF_NOTICE` solo dice «puede perder algo de calidad».
- **Qué pasa:** una foto HEIC de 50 MP o de 200 MP (el modo de 200 MP del Honor) sale a unos 24 MP. «Tachar» sí lo avisa (`redactDownscaledText`); «Limpiar foto» no.
- **Corrección exacta:**
  - `heifToJpeg` devuelve también `downscaled: Boolean` (o la clase `CleanCopy` lo lleva);
  - `CopyCard` (`PhotoCleanScreen.kt:279-292`) muestra `redactDownscaledText(redactMegapixels(w, h))` o un texto propio, que va a la lista del Abogado Digital.
- **Referencia:** coherencia con «Tachar» (T5) y con la regla del proyecto de decir lo que hace cada herramienta.

#### M-7. Pico de memoria al leer 50 MB, y el OOM se muestra como «archivo dañado» (T4, T5, T7)

- **Dónde:**
  - `core/Streams.kt:4-13`: un `ByteArrayOutputStream` que crece duplicándose (hasta unos 64 MB de búfer) y `toByteArray()` copia otros 50 MB;
  - lo usan `ui/screens/PhotoCleanScreen.kt:97` y, a través de `readPhoto`, `RedactScreen.kt:286` y `QrInspectScreen.kt:168`;
  - el OOM acaba en `PHOTO_DAMAGED` o `REDACT_DAMAGED` o `QR_IMAGE_DAMAGED`, en `PhotoCleanScreen.kt:195-197`, `RedactScreen.kt:306-308` y `QrInspectScreen.kt:189-191`.
- **Qué pasa:**
  1. Con 50 MB, el pico del montón de Java es de unos 115 MB solo para leer, más los 50 MB de la copia limpia.
  2. En un Android 8 de gama baja (`heapgrowthlimit` de 192 a 256 MB) da `OutOfMemoryError`.
  3. Se captura bien, pero el texto dice «el archivo está dañado», que no es verdad.
- **Corrección exacta:**
  1. En `readPhoto`, si el proveedor da `SIZE` (`PhotoCleanScreen.kt:91-96`), leer en un `ByteArray(size)` exacto, con un byte de más para detectar si miente. Solo hay que recurrir a `readAtMost` cuando no lo da.
  2. Añadir un texto para el OOM, por ejemplo «No hay memoria suficiente para abrir esta imagen en este móvil.», para el Abogado Digital.
  3. No hace falta `android:largeHeap`.
- **Referencia:** Android Developers, «Manage your app's memory».

#### M-8. Pico de memoria nativa al decodificar para «Tachar» (T5, a comprobar)

- **Dónde:** `android/RedactRender.kt:79-92` (`setTargetSize` con un factor no entero, de `tools/redact/Redaction.kt:101-113`) y `RedactRender.kt:155` (la salida en `ARGB_8888`, aparte).
- **Qué pasa:**
  - **Reescalado intermedio:** `ImageDecoder` primero submuestrea a la potencia de dos mayor que el objetivo y después reescala. Para 16320×12240 (200 MP) a 24 MP, el intermedio es de 8160×6120, unos 200 MB nativos, además de los 96 MB del destino.
  - **Profundidad de color:** un PNG de 16 bits o un HEIC/AVIF HDR se decodifica en `RGBA_F16` (8 bytes por píxel, unos 192 MB a 24 MP) si no se pide otra cosa.
  - **Consecuencia:** el límite de 24 MP estaba pensado para 4 bytes por píxel. Lo nativo no da `OutOfMemoryError` de Java: si se acaba la memoria, el *low memory killer* cierra el proceso, y no hay `catch` que lo evite.
- **Corrección exacta**, solo si la prueba en el móvil lo justifica:
  - en el listener, cuando `info.isWideGamut` o el formato es de 16 bits, `decoder.setMemorySizePolicy(ImageDecoder.MEMORY_POLICY_LOW_RAM)` (API 28, evita F16);
  - o elegir el objetivo con `setTargetSampleSize(n)` cuando con eso queda dentro del límite.

  En el Honor (16 GB) no debería fallar; el riesgo es para móviles de 3 o 4 GB.
- **Referencia:** documentación de `ImageDecoder.setTargetSize`, `setMemorySizePolicy` y `MEMORY_POLICY_LOW_RAM`.

#### M-9. Sin `imePadding()`: el teclado puede tapar los campos del final (T8, a comprobar)

- **Dónde:**
  - `MainActivity.kt:33-36` llama a `enableEdgeToEdge`, y con `targetSdk 36` el edge-to-edge es obligatorio;
  - `grep -rn "imePadding\|WindowInsets.ime" app/src/main` no da nada;
  - `ui/screens/CodesScreen.kt:249-251` es una `Column` con `verticalScroll` sin margen para el teclado, y `CodesScreen.kt:330-361` tiene el campo de la web y las preguntas al final de una página larga.
- **Qué pasa:** con edge-to-edge, `adjustResize` ya no encoge la vista. Sin `imePadding()` el área desplazable no se acorta, y el campo enfocado de la última pregunta puede quedar debajo del teclado sin poder subirlo. Es una pauta de toda la app (1.2.2), pero T8 añade la pantalla con más campos al final.
- **Corrección exacta:** `Modifier.padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp)` en `CodesScreen.kt:250`. Lo mismo, como tarea aparte, en las pantallas de la 1.2.2 con campos.
- **Referencia:** Android Developers, «Edge-to-edge in Compose: IME insets» (`Modifier.imePadding`).

#### M-10. La lista BIP-39 se lee en el hilo principal durante la composición (T8)

- **Dónde:** `ui/screens/CodesScreen.kt:146`: `remember { context.assets.open("bip39_spanish.txt")…readLines() }`.
- **Qué pasa:** son unos 20 KB de un asset, leídos en la primera composición. Con StrictMode da `DiskReadViolation`. El coste es pequeño, pero bloquea el primer frame de la pantalla.
- **Corrección exacta:** cargarla una vez por proceso fuera del hilo principal: `val words by produceState<List<String>?>(null) { value = withContext(Dispatchers.IO) { … } }`, con los botones de respuesta desactivados mientras es `null`. O un `lazy` en un objeto, iniciado desde `appScope`.
- **Referencia:** StrictMode `detectDiskReads`.

#### M-11. Accesibilidad de las herramientas nuevas (T4, T5, T7, T8)

- **Dónde:** `ui/screens/RedactScreen.kt:441-469` y `ui/screens/ToolsScreen.kt:42-45,66`.
- **Qué pasa:**
  - El `Canvas` de «Tachar» no tiene `semantics`: TalkBack no dice qué es ni cuántas cajas hay.
  - En las fichas, el `Text(t.icon)` (🧽, ⬛, 🔍, 🔢) se lee antes del título («esponja, Limpiar foto…», «cuadrado negro grande…»). Pasa lo mismo con los iconos de la 1.2.2.
- **Corrección exacta:**
  - `Canvas(Modifier.size(w, h).semantics { contentDescription = "Vista previa de la imagen. Arrastra el dedo para tapar una zona. ${boxes.size} zonas tapadas." }…)`, texto que va al Abogado Digital;
  - `Text(t.icon, Modifier.clearAndSetSemantics { }, …)` en `ToolsScreen.kt:66`.
- **Referencia:** Android Developers, «Accessibility in Compose: semantics»; WCAG 2.2, 1.1.1.

#### M-12. `replaceOutcomes` se pierde si no hay nadie suscrito (T11, ya corregido en `126e444`)

- **Dónde:** `3f8955d:vault/AutoBackup.kt:174-177,247`: `MutableSharedFlow` con `replay = 0` y `tryEmit`; se recogía en `3f8955d:ui/AppNavHost.kt:121-128`.
- **Qué pasa:** con `replay = 0`, lo que se emite sin suscriptor se descarta: actividad recreada, o cerrada con «atrás» mientras el proceso sigue vivo. Al terminar bien se borra la petición, y el usuario no se entera.
- **Estado:** `126e444` lo cambia por un `StateFlow<AutoBackupResult?>` con `replaceOutcomeShown()` (`ui/AppNavHost.kt:121-123`). Es la corrección adecuada; queda cerrado si la nueva revisión de T11 lo confirma.

### Informativa

- **N-1. Vida de la copia compartida** (`tools/share/ShareTemp.kt:57-65`, `MainActivity.kt:50`).
  - Solo se borra en `onStart` tras 10 minutos, o al arrancar el proceso. Si el usuario comparte y no vuelve a la app, queda en `cacheDir/compartir/` hasta el próximo arranque.
  - Es privada, no entra en copias de seguridad y solo la lee la app elegida, con un permiso por URI. No hace falta cambiarla. Si se quiere acotar, sirve un `appScope.launch { delay(SHARE_KEEP_MS); shareTemp.onReturn() }` tras `prepare`.
- **N-2. Hoja para compartir de Android 14+** (`android/ImageShare.kt:22-30`).
  - `ChooserActivity` es del sistema y no hereda `FLAG_SECURE`: su miniatura de la imagen se puede capturar.
  - La acción «Copiar» pone la URI en el portapapeles (el sistema recibe el permiso), legible mientras exista el archivo.
  - Es la imagen que el usuario quiere compartir, ya limpia o tachada, así que se acepta. Se puede mencionar en el anexo de textos si el Abogado lo ve útil.
- **N-3. Decodificadores del sistema con archivos hostiles.**
  - «Tachar» y el QR desde imagen decodifican píxeles con Skia, libpng, libwebp, libheif y libjpeg-turbo *dentro del proceso*. CVE-2023-4863 (libwebp) es el precedente.
  - «Limpiar foto» no decodifica píxeles salvo los HEIC (`PhotoCleanScreen.kt:112-121`); usa solo el analizador estructural propio. Buena decisión.
  - Lo mitiga que abrir el selector bloquea la bóveda (`MainActivity.kt:58-63`, sin `keepOpenOnce` en estas pantallas), así que la clave de datos no está en memoria cuando se decodifica.
  - Endurecer más exigiría decodificar en un `Service` con `android:isolatedProcess="true"`, lo que no compensa ahora. Que el móvil tenga los parches de seguridad mensuales al día es el control real.
- **N-4. Fuera de alcance, anterior a 1.3.0.**
  - `debugImplementation(libs.compose.ui.tooling)` (`app/build.gradle.kts:100`) mete en el APK *debug* `androidx.compose.ui.tooling.PreviewActivity` con `exported=true` y sin `secureWindow()`. Comprobado con `aapt2 dump xmltree` del APK de depuración.
  - Si el dueño usa compilaciones *debug* en su móvil, otra app puede abrir esa actividad. Solo puede componer funciones `@Composable` sin parámetros obligatorios, y no hay ninguna `@Preview`, así que el riesgo es bajo.
  - Recomendación: en el móvil personal, la compilación *release*; o quitar `ui-tooling` si no se usan las vistas previas.

## Pruebas en el móvil a añadir

En el Honor Magic 8 Pro (Android 17) y, si se puede, en un Android 8 o 9 y en un Android 12 o 13. Las pruebas que ya están en la lista de la tarea 16 no se repiten. Cada una dice qué tiene que pasar.

1. **I-1:** en «PIN y códigos», generar una respuesta, pulsar Inicio y volver a los 10 s: sigue la misma, para poder copiarla. Salir más tiempo que el bloqueo de la bóveda (o 60 s con «Al salir») y volver: el PIN, los códigos y las respuestas son nuevos, y la web y las preguntas siguen ahí.
2. **M-1 y M-2:**
   - En «Tachar», dibujar 3 cajas y girar el móvil: o se conservan (si se aplica M-1), o sale `REDACT_LOST` sin cierre de la app.
   - Con la navegación por gestos, empezar a arrastrar en el borde izquierdo de la imagen: se dibuja una caja y no se sale de la pantalla.
3. **M-1, con el sistema:** en «Limpiar foto», con la copia lista, cambiar el modo oscuro del sistema desde Ajustes rápidos y volver. Lo mismo con la ventana flotante o la pantalla dividida de MagicOS.
4. **M-3:** cambiar la maestra con Argon2id en «Alto» y girar el móvil mientras deriva: al terminar sale el diálogo «Contraseña cambiada» (tras la corrección), o se anota que no.
5. **M-4:** «Activar huella», girar con el diálogo abierto y poner el dedo: no se activa, la app no se cierra y al reintentar se activa. Anotar si hay algún mensaje.
6. **M-5:** desbloquear con huella con la clave en StrongBox: anotar si se nota un tirón. Si hay `adb`, usar `adb shell dumpsys gfxinfo io.github.egt17baa.seguridad`.
7. **M-6:** limpiar un HEIC de 50 MP o más: comprobar la resolución del resultado (Propiedades en la Galería) y que la pantalla lo dice.
8. **M-7 y M-8:**
   - Abrir en «Tachar» un JPEG de 200 MP y un PNG de 16 bits de unos 24 MP. Mirar `adb shell dumpsys meminfo io.github.egt17baa.seguridad`, sección «Native Heap». La app no se cierra.
   - En un móvil de 3 o 4 GB, anotar si el sistema la mata.
9. **M-9:** en «PIN y códigos», añadir 4 preguntas y tocar la última: el campo queda por encima del teclado.
10. **HDR:** limpiar un HEIC HDR (10 bits) y una foto «Ultra HDR» JPEG del Honor:
    - sale una copia, y no «dañada»;
    - en «Tachar», la caja negra está en la copia compartida;
    - la copia no conserva el mapa de ganancia, porque se dibuja en un bitmap nuevo (`RedactRender.kt:155`).
11. **Selector:** elegir la misma foto con GPS desde la raíz «Imágenes» y desde «Almacenamiento interno > DCIM» del selector:
    - anotar si el informe muestra «Ubicación GPS» con coordenadas, sin ellas o nada (Android oculta la ubicación a las apps sin `ACCESS_MEDIA_LOCATION`);
    - el texto `PHOTO_NOTHING_FOUND` ya lo prevé (`tools/ToolTexts.kt:96-98`);
    - en las dos, la copia no lleva GPS.
12. **Guardar en otros proveedores:** «Guardar copia» en Google Drive y en el gestor de archivos de Honor. Si alguno no admite el modo `"wt"`, sale `PHOTO_SAVE_FAILED` y no queda un documento vacío.
13. **Compartir:**
    - compartir a Gmail, volver antes de 10 minutos y enviar: el adjunto llega;
    - volver tras más de 10 minutos: `cacheDir/compartir` está vacío (en *debug*, `adb shell run-as … ls cache/compartir`).
14. **TalkBack:** recorrer las 4 fichas nuevas y «Tachar»: el lector dice nombres en español, sin leer emojis.

## Lo que está bien

- **FileProvider:**
  - no exportado y con `grantUriPermissions="true"` (`AndroidManifest.xml:38-46`);
  - una sola ruta, `cache-path compartir/` (`res/xml/share_paths.xml:4`);
  - autoridad coherente (`android/ImageShare.kt:11`);
  - `FLAG_GRANT_READ_URI_PERMISSION` más `ClipData` para que el permiso pase por el selector (`ImageShare.kt:27-28`);
  - nombres fijos con lista cerrada (`ShareTemp.kt:13,38`) y nunca más de un archivo (`ShareTemp.kt:94-100`).

  Confirmado en el APK: los permisos son solo `INTERNET`, `CAMERA`, `USE_BIOMETRIC` y `USE_FINGERPRINT`, más el `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` de AndroidX; ni `READ_MEDIA_IMAGES` ni `ACCESS_MEDIA_LOCATION`.
- **SAF sin permisos:** `OpenDocument` y `CreateDocument`. El original nunca se escribe (`PhotoCleanScreen.kt:86-100`). Un documento a medias se borra (`PhotoCleanScreen.kt:157-164`, `RedactScreen.kt:251-266`). Que el proveedor diga un tamaño falso no hace leer más (`PhotoCleanScreen.kt:96-98`).
- **Imágenes hostiles:**
  - el tamaño declarado se comprueba en el listener de cabecera, antes de inflar nada (`RedactRender.kt:87`, `QrFromImage.kt:98`, `HeifToJpeg.kt:68`);
  - `ALLOCATOR_SOFTWARE` y `setOnPartialImageListener { false }`;
  - se comprueba que el decodificador respetó el tamaño (`RedactRender.kt:57`, `QrFromImage.kt:54`);
  - `inSampleSize` con `inJustDecodeBounds` en Android 8 y la orientación EXIF aplicada a mano (`RedactRender.kt:96-119`);
  - HEIC refusado antes de API 28 y AVIF antes de API 31 (`RedactRender.kt:49-50`, `HeifToJpeg.kt:57-58`);
  - `recycle()` en todas las salidas (`RedactRender.kt:61-73,176`, `QrFromImage.kt:58-60`, `HeifToJpeg.kt:79-81`).
- **Tachar:**
  - la copia sale del mismo bitmap que la vista previa;
  - cajas opacas, sin antialiasing, y comprobación píxel a píxel antes de codificar (`RedactRender.kt:159-169,192-202`);
  - se redibuja en un bitmap nuevo, así que no hay mapa de ganancia ni metadatos del original;
  - `SourceHolder` impide reciclar mientras se renderiza (`RedactScreen.kt:114-137`);
  - la entrada se bloquea durante el trabajo y se compara la instantánea de cajas (`RedactScreen.kt:229,244,382`);
  - el arrastre solo invalida el dibujado, no recompone (`RedactScreen.kt:432-433,476-482`).
- **Hilos:** decodificar, limpiar, renderizar y leer QR en `Dispatchers.Default`; la entrada y salida en `IO`. La generación de claves de StrongBox, fuera del hilo principal (`BiometricVault.kt:242-258`). `ShareTemp` está sincronizado y `onReturn` corre en `IO` (`MainActivity.kt:50`).
- **Trabajo que debe terminar:**
  - `VaultStore.commit` y `changeMaster` en `NonCancellable` (`vault/VaultStore.kt:201,519`);
  - `replaceOldCopies` en el `scope` de `AutoBackup` (`vault/AutoBackup.kt:216`);
  - `discardPrepared` en `NonCancellable + IO` (`PhotoCleanScreen.kt:243`, `RedactScreen.kt:378`).
- **Biometría:**
  - `CryptoObject` siempre (`BiometricVault.kt:187`);
  - `BIOMETRIC_STRONG` con `setUserAuthenticationParameters(0, AUTH_BIOMETRIC_STRONG)` en API 30+;
  - `setInvalidatedByBiometricEnrollment(true)`;
  - StrongBox con respaldo a la zona segura del procesador, sin `setUnlockedDeviceRequired` (`BiometricVault.kt:101-117`);
  - todas las API nuevas comprobadas por versión (`BiometricVault.kt:80,109-110,122`);
  - la copia de la clave de datos se borra en todas las salidas (`BiometricVault.kt:336,396`);
  - el flujo no retiene la actividad tras terminar (`BiometricVault.kt:339`).
- **PLATFORM:**
  - `FLAG_SECURE`, `filterTouchesWhenObscured` y la exclusión de autorrelleno en la ventana (`android/SecureWindow.kt:17-21`);
  - los diálogos nuevos llaman a `FilterObscuredTouches()` (`SettingsScreen.kt:309,360`, `IdentityScreen.kt:252,289,301`);
  - los diálogos de Compose heredan `FLAG_SECURE` (`SecureFlagPolicy.Inherit` por defecto);
  - ningún componente exportado nuevo.
- **STORAGE:**
  - sin logs (`grep` de `Log.`, `println` y `printStackTrace`: 0 resultados);
  - `allowBackup="false"` con `dataExtractionRules` y `fullBackupContent` que excluyen todo (`AndroidManifest.xml:11-13`, `res/xml/*`);
  - `QrHandoff` solo en memoria, nunca en la ruta, y se vacía al bloquear (`SeguridadApp.kt:36-43`);
  - el QR de imagen se pone a cero tras leerlo (`QrInspectScreen.kt:173-175`, `QrFromImage.kt:65`).
- **Resultados de actividad:** todos los lanzadores se registran sin condiciones en su pantalla, así que el resultado llega tras recrear la actividad o tras la muerte del proceso. Cuando la copia ya no existe, se falla cerrado (`PhotoCleanScreen.kt:142-146`, `RedactScreen.kt:233-237`).
- **Atrás y compatibilidad:** el atrás predictivo de Android 16 y `targetSdk 36` no rompen nada: `NavHost` gestiona el atrás y los trabajos sobreviven a la cancelación. La ocultación de la ubicación de Android 10+ está prevista en el texto (`ToolTexts.kt:96-98`).

## Revisión Ingeniero Móvil Senior

- [x] **Ciclo de vida, rotación y muerte del proceso**
  - Lo secreto está en `remember`, intencionadamente (`QrInspectScreen.kt:111-112`, `CodesScreen.kt:131-133`).
  - Los cambios de configuración pierden el trabajo de las herramientas (M-1) y el diálogo de A6 (M-3); la huella se cancela en silencio (M-4).
  - `rememberSaveable` solo guarda datos no secretos (`AppNavHost.kt:118`; `SettingsScreen.kt:298`, un `Long` con la hora de la petición).
- [x] **Ámbitos de corrutinas:** `NonCancellable` o el `scope` de la app donde el trabajo debe acabar (lo dicho en «Lo que está bien»). `rememberCoroutineScope` solo para trabajo de UI cuya cancelación falla cerrada (`PhotoCleanScreen.kt:157-160`, `RedactScreen.kt:251-254`).
- [x] **Claves de `LaunchedEffect`:**
  - `LaunchedEffect(digits)` (`CodesScreen.kt:152`), `LaunchedEffect(store)` (`QrInspectScreen.kt:132`) y `LaunchedEffect(waiting)` (`CodesScreen.kt:243`) son correctas;
  - `DisposableEffect(p)` recicla la vista previa por imagen (`RedactScreen.kt:431`);
  - `LocalOnMasterChanged` no recompone de más: el compilador de Kotlin 2.4 recuerda la lambda.
- [x] **Contratos de resultado:** registrados sin condiciones y recibidos tras recrear; `launchOrError` captura `ActivityNotFoundException` (`ui/screens/Pickers.kt:13-14`).
- [x] **Hilo principal:** todo el trabajo pesado está fuera, salvo `Cipher.init` al desbloquear (M-5) y la lista BIP-39 (M-10).
- [x] **Memoria e imágenes:**
  - `recycle` en todas las salidas;
  - límites de cabecera de 260 MP y 32768 px;
  - escalado a 24 MP o 2048 px;
  - OOM capturado y con un texto que no es el adecuado (M-7);
  - picos nativos a comprobar (M-8);
  - HEIC con `ImageDecoder` solo en API 28+.
- [x] **FileProvider y compartir:** no exportado, ruta estrecha, permiso por URI con `ClipData`, archivos temporales limpiados (N-1 informativa).
- [x] **SAF y selector:** `OpenDocument` y `CreateDocument`, sin permisos de medios (confirmado con `aapt2 dump permissions`). El selector de fotos (`PickVisualMedia`) no se usa: es correcto, porque `OpenDocument` da acceso a los HEIC de cualquier proveedor.
- [x] **MASVS-STORAGE:** sin logs ni secretos en la caché; copias de seguridad desactivadas. El hueco de I-1 es de UI, no de almacenamiento.
- [x] **MASVS-CRYPTO y AUTH:** `CryptoObject`, `BIOMETRIC_STRONG`, StrongBox con respaldo, invalidación al añadir huellas y sin `setUnlockedDeviceRequired` (regla del proyecto).
- [x] **MASVS-PLATFORM:** `FLAG_SECURE` en la ventana y heredado por los diálogos; `filterTouchesWhenObscured` en la ventana y en cada diálogo nuevo; sin componentes exportados nuevos; ningún intent con el texto del QR (`QrInspectScreen.kt:105-110`).
- [x] **MASVS-CODE:** R8 activo (`app/build.gradle.kts:57-59`); el código nuevo no necesita reglas (sin reflexión; zxing y `FileProvider` se conservan por uso o por el manifiesto). Lint, según los informes de cada tarea.
- [x] **RESILIENCE:** no aplica.
- [x] **Compose:**
  - recomposición acotada: el arrastre solo invalida el dibujado y la vista previa es de 2048 px como máximo;
  - accesibilidad (M-11);
  - teclado con edge-to-edge (M-9).
- [x] **minSdk 26:** todas las API nuevas están comprobadas por versión:
  - `ImageDecoder` (`QrFromImage.kt:41,88`, `RedactRender.kt:54,76`, `HeifToJpeg.kt:57`);
  - AVIF en API 31;
  - `setIsStrongBoxBacked` y `FEATURE_STRONGBOX_KEYSTORE` en API 28;
  - `setUserAuthenticationParameters` en API 30;
  - `KeyInfo.securityLevel` en API 31;
  - `EXTRA_IS_SENSITIVE` en API 33.

  `readAtMost` sustituye a `readNBytes` (API 33) (`core/Streams.kt:3`).
- [ ] **Compatibilidad con Android 8 a 16 y con el Honor con Android 17:** el código no tiene ramas sin comprobar. Las diferencias reales quedan en las «Pruebas en el móvil»:
  - HDR y Ultra HDR;
  - memoria nativa con 200 MP;
  - StrongBox del Honor;
  - selector y gestor de archivos de MagicOS;
  - modo `"wt"` de los proveedores.

  Los cambios de comportamiento propios de Android 17 (API 37) que afecten a apps con `targetSdk 36` no se han podido verificar aquí: están a comprobar en el móvil.

## Revisión experto-seguridad-cripto

- **Primitivas:** sin cambios en esta revisión. AES-256-GCM del Keystore con nonce del propio Keystore (`c.iv`, `BiometricVault.kt:273`) y etiqueta de 128 bits (`BiometricVault.kt:377`).
- **Aleatoriedad:** sin cambios. `generatePin`, `recoveryCodes` e `inventedAnswer` usan el `secureRandom` común, según la revisión de T8.
- **Hallazgo de seguridad confirmado:** I-1. Exposición en la UI frente a quien tenga el móvil desbloqueado (`CodesScreen.kt:148-159`, sin `ON_STOP`), contra la regla de la propia app (`QrInspectScreen.kt:128-131`). Es **Important**.
- **Entrada hostil:**
  - el tamaño se comprueba antes de leer (`PhotoCleanScreen.kt:96-98`) y antes de decodificar (listeners de cabecera);
  - los decodificadores del sistema, con la bóveda bloqueada (N-3).

  No se ha encontrado ninguna ruta en la que un archivo hostil cierre la app por una excepción no capturada: todas acaban en `Exception` u `OutOfMemoryError` (`RedactRender.kt:61-73`, `QrFromImage.kt:42-50`, `HeifToJpeg.kt:82-88`). Solo queda el *low memory killer* nativo (M-8), que no se puede capturar.
- **Formatos:** esta revisión no toca ninguno (`SG1:`, `.sgenc`, `seguridad-vault`, `seguridad-backup`, `CP1:`, `.cpenc` y `criptopanel-vault` siguen igual).
- **Red y permisos:** sin conexiones ni permisos nuevos (`aapt2 dump permissions` del APK de depuración, compilado a las 12:21 de hoy, antes de `a2e753a` y `126e444`, que no tocan el manifiesto).
