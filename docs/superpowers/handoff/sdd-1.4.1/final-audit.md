# Auditoría final de la rama 1.4.1: el código de `app/`

**Rama:** `ccr-d1e75160-p5f4wc`, auditada en `684fc08` (la auditoría empezó en `02bff19`; mientras se hacía entró `684fc08`, que mueve el `reset()` de la lista al ViewModel, y se auditó también).
**Alcance:** `git diff origin/main...HEAD -- app/`:
1. `ui/screens/VaultEntryScreen.kt`: «Borrar» ya no llama a `onBack()` (F-1).
2. `ui/AppNavHost.kt`: `backOnce(nav)` en lugar del `back` compartido.
3. `ui/screens/VaultScreen.kt`: `EntryListViewModel` (búsqueda, filtros y `LazyListState`) y su `reset()` (F-4).
4. `imports/CsvFormats.kt`: el CSV de navegador lee también las columnas `folder` y `totp`.

Los otros cambios de `app/` son solo comentarios o tests: `tools/ImageLimits.kt:14` y `ui/screens/VaultBackup.kt:205` quitan los datos del móvil del dueño de los comentarios; `build.gradle.kts:35` pone `versionName = "1.4.1"`. No hay ningún cambio de formato, permiso, componente exportado, host ni dependencia.

**Modelo de amenaza:**
- Un archivo exportado hostil (decisiones (a) y (c)).
- Quien tiene el móvil después de un bloqueo.
- La memoria del proceso (root o un depurador).
- Las carreras del ciclo de vida: el bloqueo al salir de la app (`MainActivity.kt:113`), las transiciones de Navigation, el giro y las pestañas con `saveState`/`restoreState`.

No entran la red ni la criptografía, porque el diff no las toca.

**Método:**
- Leí el código de la app.
- Comprobé el comportamiento de las bibliotecas en el bytecode de las versiones que usa el proyecto (navigation-compose 2.9.3, foundation y ui 1.9.0).
- Probé el CSV con un test temporal: 17 casos hostiles, ya borrado.
- Pasé `CsvFormatsTest` (30 de 30) y `PlatformSourcesTest` (27 de 27), en verde en `684fc08`.

No hay emulador en el contenedor, así que el hallazgo 1 no se ha reproducido en un móvil.

## Resumen

| # | Severidad | Hallazgo | Ubicación |
|---|---|---|---|
| 1 | Bajo | Con `backOnce`, un bloqueo durante la transición de la ficha a «Editar» o al QR deja la ficha en blanco | `VaultEntryScreen.kt:91-93`, `AppNavHost.kt:317-321` |
| 2 | Bajo | El `LazyListState` del ViewModel retiene el `LayoutNode` de la lista y su última medida, y con ellos la actividad después de un giro | `VaultScreen.kt:423`, `:436`, `:458` |
| 3 | Info | Cerrado en `684fc08`: el `reset()` de F-4 solo corría con la lista en pantalla | `VaultScreen.kt:428-430` |
| 4 | Info | CSV: una fila que solo trae `totp` crea, además del 2FA, un acceso vacío «Acceso» (y lo crea también si el 2FA no vale) | `CsvFormats.kt:135`, `:148-150` |

Crítico: 0. Alto: 0. Medio: 0. Bajo: 2. Info: 2 (uno ya cerrado).

## Hallazgos

### 1. [Bajo] Una ficha en blanco si la app se bloquea durante la transición a «Editar» o al QR

- **Dónde:**
  - `ui/screens/VaultEntryScreen.kt:91-93`: `LockedNotice` es `LaunchedEffect(Unit) { onBack() }` y no dibuja nada.
  - `VaultEntryScreen.kt:135` (ficha) y `:349` (editor) usan `LockedNotice`.
  - `ui/screens/OtpQrScreen.kt:68-71` sigue el mismo patrón.
  - `ui/AppNavHost.kt:317-321`: `backOnce` no hace nada si su pantalla no es la de arriba.
  - `AppNavHost.kt:199`: la ficha navega a «Editar» y al QR.
- **Qué pasa:**
  1. El usuario pulsa «Editar» (o «Mostrar QR para otra app») en la ficha. `NavHost` empieza una transición: sin `enterTransition`/`exitTransition` propios en la app, es el fundido por defecto de 700 ms. Durante esa transición, `AnimatedContent` compone las dos entradas, que siguen en STARTED.
  2. Si el usuario sale de la app en ese momento (Inicio, el botón de encendido o una llamada), `onStop` bloquea la bóveda (`MainActivity.kt:113`). Al parar, el `Recomposer` pausa su reloj de fotogramas (en el bytecode de ui 1.9.0, `WindowRecomposer_androidKt$createLifecycleAwareWindowRecomposer$2` llama a `pauseCompositionFrameClock`), así que la transición se queda a medias.
  3. Al volver, las dos pantallas recomponen con `Locked` y lanzan su `LockedNotice`, primero la ficha y luego el editor (el orden de `currentlyVisible`).
  4. El efecto de la ficha llama a `backOnce`. Como `currentBackStackEntry` es el editor, no hace nada.
  5. El del editor quita el editor. `NavHost` vuelve hacia la ficha, que todavía está compuesta: `AnimatedContent` la reutiliza por su `contentKey`, que es el id de la entrada. Por eso su `LaunchedEffect(Unit)` ya se ha ejecutado y no vuelve a correr.
  6. La ficha se queda arriba mostrando `LockedNotice`, que no dibuja nada: una pantalla en blanco con la barra de pestañas. Es el síntoma de F-1, por otro camino.

  Antes, el `back` compartido hacía dos `popBackStack` y terminaba, de casualidad, en la lista. Es una regresión de `backOnce`, en una carrera estrecha.
- **Impacto:** solo de uso. No se ve ningún dato, porque `LockedNotice` no dibuja nada y la bóveda está bloqueada. Se sale con «Atrás» del sistema o con una pestaña.
- **Corrección:** una salida automática tiene que esperar a que su pantalla esté arriba, no descartarse. Los botones pueden seguir con `backOnce` tal cual. Por ejemplo, en `LockedNotice` y en las otras salidas automáticas (`OtpQrScreen.kt:70`, `ContactDetailScreen.kt:71`, `ImportScreen.kt:477` y `VaultEntryScreen.kt:359`):
  ```kotlin
  @Composable
  private fun LockedNotice(onBack: () -> Unit) {
      val owner = LocalLifecycleOwner.current
      // Only on top: an entry still leaving, or covered by the one being entered, waits; one that is popped is
      // disposed first and never calls it.
      LaunchedEffect(owner) { owner.lifecycle.withResumed { }; onBack() }
  }
  ```
  `withResumed` es de `androidx.lifecycle:lifecycle-runtime-ktx`, que ya llega con lifecycle 2.9.2. Navigation solo lleva a RESUMED la entrada de arriba, y solo cuando acaba su transición. Otra forma es esperar con `nav.currentBackStackEntryFlow.first { it === owner }` dentro de un `leaveOnce(nav)` hermano de `backOnce`.
  - **Test:** uno de `PlatformSourcesTest` que exija la espera en cada salida automática.
  - **Prueba en el móvil:** pulsar «Editar» y apagar la pantalla enseguida.
- **Referencia:** MASVS-PLATFORM (comportamiento de la interfaz ante el bloqueo). En Navigation Compose, la entrada que sale sigue compuesta durante la transición.

### 2. [Bajo] El `LazyListState` del ViewModel retiene la interfaz que ya no se ve

- **Dónde:**
  - `ui/screens/VaultScreen.kt:423`: `var listState by mutableStateOf(LazyListState())`, dentro de `EntryListViewModel`.
  - `VaultScreen.kt:458`: `LazyColumn(state = list.listState, …)`.
  - `VaultScreen.kt:436`: `reset()`, el único sitio donde se cambia el `LazyListState`.
- **Qué pasa:** un `LazyListState` es estado de la composición, y el ViewModel vive más que ella: sobrevive al giro, a las pestañas con `saveState` y mientras hay una ficha encima. En foundation 1.9.0 (bytecode):
  - `LazyListState.remeasurement` guarda el `LayoutNode` de la lista. `LazyListState$remeasurementModifier$1.onRemeasurementAvailable` solo lo asigna y nunca lo pone a `null`.
  - `layoutInfoState` guarda la última `LazyListMeasureResult`, con `visibleItemsInfo` → `LazyListMeasuredItem.placeables`, es decir, los nodos de las filas visibles.
  - `LayoutNode.onRelease()` no vacía su cadena de modificadores. En la cadena de la lista está el overscroll de Android, que guarda el `Context` de la actividad (`AndroidEdgeEffectOverscrollEffect.edgeEffectWrapper` → `EdgeEffectWrapper.context`).

  Por eso, si se gira el móvil con una ficha delante o en otra pestaña:
  - el ViewModel de «Bóveda», que es el destino de inicio y no sale nunca de la pila, retiene la actividad anterior y su árbol de vistas;
  - retiene también los textos de las filas que se vieron por última vez (títulos, usuarios y, en «Códigos», los códigos).

  Todo eso dura hasta que la lista se vuelve a mostrar o la bóveda se bloquea. Desde `684fc08`, `reset()` corre con cualquier bloqueo (`:428-430`) y cambia el estado. Salir de la app siempre bloquea, así que la retención no pasa de un bloqueo. No se midió con LeakCanary.

  El `PrefetchHandleProvider`, que lleva la lista de entradas, sí se suelta al salir: `LazyLayoutKt$LazyLayout$2…onDispose` lo pone a `null`. No es parte del hallazgo.
- **Impacto:** memoria. Hay una actividad destruida retenida (acotada a una) y textos de la bóveda retenidos mientras está abierta, sin que se vean. Sin fuga fuera del proceso.
- **Corrección:** guardar en el ViewModel solo la posición (dos `Int`) y crear el `LazyListState` en la composición, sin `rememberSaveable`:
  ```kotlin
  // EntryListViewModel
  var firstIndex = 0
  var firstOffset = 0
  var generation by mutableIntStateOf(0)   // reset(): firstIndex = 0; firstOffset = 0; generation++
  // EntryList
  val listState = remember(list.generation) { LazyListState(list.firstIndex, list.firstOffset) }
  DisposableEffect(listState) {
      onDispose { list.firstIndex = listState.firstVisibleItemIndex; list.firstOffset = listState.firstVisibleItemScrollOffset }
  }
  ```
  - **Lo que se pierde:** al volver, la posición se recupera por índice y no por clave. En el caso de F-4, borrar «Beta» de «Alfa, Beta, Gamma», el resultado es el mismo.
  - **Test:** `PlatformSourcesTest` exigiría que no haya ningún `LazyListState(` en una clase `ViewModel`.
- **Referencia:** guía de Android «State holders and UI state» (el estado de los elementos de la interfaz se queda en la composición) y MASVS-STORAGE (datos sensibles en memoria más tiempo del necesario).

### 3. [Info, cerrado en `684fc08`] El `reset()` de F-4 solo corría con la lista en pantalla

- **Dónde:** `031719f` y `02bff19`, `VaultScreen.kt`: `LaunchedEffect(state) { if (state !is VaultState.Unlocked) list.reset() }`.
- **Qué pasaba:**
  1. Con una búsqueda en «Códigos», el usuario pasa a «Bóveda». La entrada de «Códigos» sale con `saveState`, que conserva su ViewModel.
  2. La bóveda se bloquea y se vuelve a abrir en «Bóveda».
  3. Al volver a «Códigos», la búsqueda y la posición de antes del bloqueo seguían ahí: el efecto nunca vio `Locked`.

  Lo mismo pasaba con un bloqueo en otra pestaña y un desbloqueo en «Contactos».
- **Estado:** arreglado en `684fc08`. `VaultScreen.kt:428-430` sigue el estado de la bóveda en `viewModelScope`, como `EntryEditViewModel` (`VaultEntryScreen.kt:240-243`), y `PlatformSourcesTest` lo fija. Comprobado: el colector va en `Dispatchers.Main.immediate` y `reset()` solo escribe estado de Compose en el hilo principal. Con la bóveda cerrada, `VaultGate` ya ha quitado la lista, así que el `LazyListState` nuevo no la mueve en pantalla.

### 4. [Info] CSV: una fila que solo trae el 2FA crea también un acceso vacío

- **Dónde:** `imports/CsvFormats.kt:135`: `totp` cuenta para que la fila no esté vacía. Después, `:148` añade siempre el acceso y `:150` añade el 2FA.
- **Qué pasa:** lo comprobé con el test temporal.
  - La fila `,,,,,,JBSWY3DPEHPK3PXP` da un acceso «Acceso» sin URL, usuario, contraseña ni notas, y un 2FA «Acceso».
  - La fila `,,,,,,nope!` da el acceso vacío solo y `BROKEN` para el 2FA.

  Bitwarden ya hace lo mismo (`readBitwardenRow`, `CsvFormats.kt:178` y `:199`), así que es coherente con lo que hay.
- **Impacto:** ninguno de seguridad. Es una entrada vacía de más, que la vista previa enseña.
- **Corrección (opcional):** si la fila no trae URL, usuario, contraseña ni notas, solo el 2FA, no añadir el acceso. Habría que hacerlo igual en los dos lectores, para que no se separen. Si se deja como está, conviene una línea en el KDoc de `readRow`.

## Lo que está bien

- **El F-1 de «Borrar»** (`VaultEntryScreen.kt:211-220`): no llama a `onBack()`. La ficha sale una vez por `LockedNotice` cuando desaparece la entrada, y un fallo deja `failed`.
- **`backOnce`** (`AppNavHost.kt:317-321`) es correcto:
  - lee el `LocalLifecycleOwner` de `androidx.lifecycle.compose`, el mismo que da `NavBackStackEntry.LocalOwnersProvider` en navigation-compose 2.9.3 (comprobado en el bytecode de `NavBackStackEntryProviderKt`);
  - lo recuerda con la clave `(nav, owner)`, así que una entrada restaurada con `restoreState` (un objeto nuevo) recibe su propio `backOnce`;
  - la misma ruta dos veces en la pila (`tools/file` ↔ `contacts/open`) son entradas distintas y cada una solo se quita a sí misma;
  - los diálogos son `AlertDialog` de la misma composición, no destinos.
  - Arregla además dos carreras de antes: un toque en la flecha de una pantalla que ya se va (700 ms) y un bloqueo durante un «Atrás». Las dos quitaban la pantalla de debajo, hasta dejar el `NavHost` vacío. `PlatformSourcesTest` comprueba que el único `popBackStack` es el protegido.
- **`EntryListViewModel`:**
  - no usa `SavedStateHandle`, `rememberSaveable` ni `Bundle` (`VaultScreen.kt:419-438`); lo que guarda se pierde si Android cierra la app, que es lo que se busca;
  - el único dato que el `LazyListState` guarda de la bóveda es la clave de la primera fila, el id de la entrada, que no es secreto;
  - cada pestaña («Bóveda» y «Códigos») tiene su entrada y su ViewModel.
- **El CSV:**
  - El NUL en `folder` o en `totp` rompe la fila (`:138`).
  - Una sustitución suplente sola no puede llegar: `decodeText` es estricto (`TextDecode.kt:33-36`).
  - El `totp` pasa por `addLoginTotp`, la misma lectura estricta de Bitwarden (`BitwardenJson.kt:190-198`). En el test temporal:
    - un parámetro inyectado en la clave (`…&digits=8&algorithm=MD5`) da `BROKEN`, porque va codificado en su parámetro;
    - `digits=10` da `TOTP_UNSUPPORTED`;
    - HOTP y `steam://` dan `NOT_TOTP`;
    - más de `OTPAUTH_MAX_CHARS` da `FIELD_TOO_LONG`, solo para el 2FA;
    - una clave corta (`234567`) da `BROKEN`.
  - La carpeta pasa a etiqueta solo por `importTags`: `compareKey`, como mucho 24 puntos de código y el tope de 100 etiquetas nuevas. No se guarda en ninguna entrada. Una carpeta de 70 000 caracteres queda en 65 537 (`CSV_MAX_FIELD_CHARS`) y solo vive en la memoria de la lectura, igual que en Bitwarden.
  - Una carpeta solo con texto de control o invisible no crea etiqueta. Una fila con solo `name` y `folder` es `EMPTY`.
  - **Sin regresiones**, comprobado:
    - el CSV de Chromium y el de Edge, con notas y sin ellas, siguen igual;
    - Apple y Firefox no leen `folder` ni `totp` aunque traigan esas columnas;
    - Bitwarden se detecta antes;
    - las cabeceras en mayúsculas y las filas cortas funcionan.
- **Sin logs** en el diff: ningún `Log.`, `println` ni `printStackTrace` nuevos.

## Revisión experto-seguridad-cripto

Lista de `references/auditoria.md` sobre el diff:

- **Criptografía:** el diff no toca ninguna primitiva, clave ni formato. El 2FA del CSV entra por `readOtpAuth`/`isSupported` (CRY-B02), sin cambiar ningún ajuste (`ImportModel.kt:247-262`, `BitwardenJson.kt:190-198`).
- **Entrada hostil** (decisiones (a) y (c)):
  - topes antes de guardar: `CSV_MAX_FIELD_CHARS` (`ImportLimits.kt`), `OTPAUTH_MAX_CHARS` e `IMPORT_MAX_ITEMS`, que cuenta también el 2FA de cada fila (`ImportModel.kt:229-232`);
  - nada se interpreta: la carpeta va a `compareKey` y `normalizeTags`, y el `totp` a un parámetro codificado;
  - el texto de los saltos pasa por `plainReportText` (`ImportModel.kt:225-227`).
- **Datos en memoria y en el estado guardado:**
  - ni la búsqueda ni la posición van a un `Bundle` (`VaultScreen.kt:419-438`);
  - desde `684fc08` se borran con cualquier bloqueo (`:428-430`);
  - queda la retención del hallazgo 2, de severidad baja, acotada al tiempo con la bóveda abierta.
- **Bloqueo:**
  - ninguna pantalla enseña datos después de un bloqueo: `LockedNotice` no dibuja nada y `VaultGate` quita su contenido;
  - el hallazgo 1 es solo de uso.
- **Privacidad del dueño:** el diff quita su modelo de móvil de un comentario (`ImageLimits.kt:14`) y de un test. En el código auditado no queda ningún dato suyo. El historial antiguo se resuelve con «Squash and merge», según la regla del 10-11-2026.

## Revisión Ingeniero Móvil Senior

Criterio de `desarrollo-movil-senior` y `auditoria-seguridad-movil` (MASVS-PLATFORM, STORAGE y CODE):

- **Navigation Compose y el ciclo de vida:**
  - la entrada como `LifecycleOwner` es la forma correcta de saber qué pantalla pide «Atrás» (`AppNavHost.kt:317-321`);
  - el defecto está en las salidas automáticas, que se descartan en lugar de esperar (hallazgo 1; `VaultEntryScreen.kt:91-93`, `OtpQrScreen.kt:70`). La espera a RESUMED, o a `currentBackStackEntryFlow`, es lo idiomático.
- **El estado de la interfaz en un ViewModel:**
  - la búsqueda y los filtros en un ViewModel están bien;
  - el `LazyListState` no (hallazgo 2; `VaultScreen.kt:423`): conviene guardar solo el índice y el desplazamiento.
- **Concurrencia:**
  - `viewModelScope` y `Main.immediate` para el colector (`VaultScreen.kt:429`);
  - el borrado sigue en `rememberCoroutineScope` (`VaultEntryScreen.kt:214`) y su fallo se enseña.
  - Sin carreras nuevas, aparte de la del hallazgo 1.
- **Pruebas:**
  - los tests nuevos de `PlatformSourcesTest` (`:33-68`) fijan el código fuente, no el comportamiento;
  - sin Robolectric ni pruebas de interfaz, los hallazgos 1 y 2 solo se pueden cubrir así o en el móvil (un flujo de Maestro: «Editar» y apagar la pantalla, y volver);
  - `CsvFormatsTest` (`:44-76`) cubre el caso feliz, un 2FA malo y una carpeta rota.

## Textos para el Abogado Digital (revisión final)

El diff de `app/` no añade ni cambia ningún texto de la interfaz. Los KDoc de `CsvFormats.kt:9-10` y `:111-112` son comentarios. El README describe el CSV con `folder` y `totp` (línea 27), y está fuera de este alcance.

## Estado de los hallazgos (controlador, 10-11-2026)

| Hallazgo | Estado |
|---|---|
| 1 (Bajo), una salida automática ignorada en la transición | Arreglado: `backOnce` deja la petición en espera y quita la pantalla en cuanto vuelve a estar arriba (`nav.currentBackStackEntryFlow`). Fuera de un destino, hace `popBackStack` como antes. `PlatformSourcesTest` lo comprueba. |
| 2 (Bajo), el `LazyListState` en el ViewModel | Arreglado: el ViewModel guarda solo `firstIndex` y `firstOffset`; la lista crea su `LazyListState` y los devuelve al salir. |
| 3 (Info), el `reset()` de F-4 | Arreglado antes en `684fc08`. |
| 4 (Info), una fila con solo `totp` crea también un acceso vacío | Se queda como está, igual que el CSV de Bitwarden: el acceso lleva el título y la carpeta, y el 2FA es una entrada aparte. |

**`code-review` (alto) de la misma rama:**
- Los dos primeros puntos coinciden con los hallazgos 2 y 1, ya arreglados.
- El filtro de una etiqueta que desaparece ahora se borra, no solo se oculta.
- Las referencias a «1.4.1» que quedaban en el README y en `.maestro/README.md` pasan a «1.4.2».
- El CSV con `folder` o `totp` tiene ahora su propio formato, `PASSWORDS_CSV`, y su etiqueta de origen, `importado-csv`. Antes llevaba la de los navegadores, y la pantalla de importar dice que se lee.
- `abrir-app.yaml` ya no pulsa «Atrás» en el diálogo de la huella: pulsa «Usar contraseña maestra» o «Cancelar», con `optional`.
- `backOnce` ya no se queda sin hacer nada fuera de un destino.

**`security-review`:** ninguna vulnerabilidad. Su nota sobre el `reset()` es el hallazgo 3.
