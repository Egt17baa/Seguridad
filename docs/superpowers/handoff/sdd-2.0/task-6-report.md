# Tarea 6 de la 2.0: la lista y la ficha — informe del implementador

**Estado:** DONE_WITH_CONCERNS (las dudas de abajo; nada bloquea). Último commit: `0f5be65`.
**Rama:** `ccr-0aa27f95-u038cd`, base `f8d1d54`. Sin `push`.

## Commits

| Commit | Qué |
|---|---|
| `d111ab8` | `Selection`, `TwoStepCopy`, `rowCodeToCopy`, favorita y etiquetar en bloque (`withFavorite`, `withTagAdded`, `VaultStore.setFavorite`/`addTag`), textos (`vault/ListTexts.kt`). Tests en rojo y en verde. |
| `d17ccaa` | `RowPanel` y `rowRevealColors` (`ui/theme/Components.kt`), `EntryRow.kt` (deslizar, «⋮», 2FA, acciones de TalkBack), barra de selección, lista vacía, foco en el buscador, «Copiar contraseña» al volver; el test de N-3 a varias líneas; contraste de las filas. |
| `96fbe2f` | `BigPasswordScreen.kt` y la ficha («Ver en grande», copia en dos pasos, lo que sigue a una copia espera a que se haga). |
| `02ac23d` | `EntryRowTest` (40 %), `NoCopyMenuTest` al día, `VaultTotpTest` lee `EntryRow.kt`, acciones de la fila con `rememberUpdatedState`. |
| `0f5be65` | «2.0, tarea 6: la lista y la ficha»: cada paso a la papelera con sus 5 s de «Deshacer» (`UndoTrash.moves`), la barra de selección no deja pasar toques, textos en plural y «Ya estaban ahí», el test de estado guardado mira también las filas y «Ver en grande», el flujo P-5 de Maestro pulsa «Crear la primera». |

## Qué se ha hecho

**Lógica (sin Compose, en jvm-check):**
- `vault/Selection.kt`: `class Selection { toggle; selectAll(visible); retainVisible(visible); clear; onFilter(key); followVault(state, scope); ids: StateFlow<Set<String>> }`. Solo ids. `selectAll` toma exactamente lo visible y nunca la papelera, la identidad ni un contacto aunque se los pasen (`Selection.kt:41`); tras cada resultado de la lista se poda a lo visible (`VaultList.kt:275`), así la selección es siempre parte de lo que se ve (foco 4). Se vacía al bloquear (`followVault`, `Selection.kt:68`; y `reset()`, `VaultList.kt:312`) y al cambiar la búsqueda o el filtro, no el orden (`selectionFilterKey`, `onFilter`, `VaultList.kt:263`).
- `withFavorite` / `withTagAdded` (`Selection.kt`): solo entradas vivas y listables; cada una guarda su versión como en el editor (`withVersion`, `Selection.kt:102`) y su `updatedAt`. La etiqueta pasa por `normalizeTags`; una entrada con 10 etiquetas no la recibe. `VaultStore.setFavorite`/`addTag` (`VaultStore.kt:1028`, `:1031`): un solo guardado.
- `tools/TwoStepCopy.kt`: `TwoStepCopy(clock, lockEpoch)`: `offerPassword(entryId)`, `pending()`, `leftMs()`, `clear()`. Guarda solo el id, la hora (reloj monotónico, `elapsedRealtime`) y el contador de bloqueos de la app; 30 s (`TWO_STEP_COPY_MS`); un reloj que va hacia atrás o un bloqueo (o cambio de bóveda) desde la oferta la terminan (`TwoStepCopy.kt:44`). Una sola en `AppContainer` (`AppContainer.kt:776`), borrada también al bloquear como `clipboardNotice`.
- `rowCodeToCopy` (`TwoStepCopy.kt`): con menos de 5 s (`ROW_NEXT_CODE_BELOW_S`), el código siguiente y `next = true`.

**Interfaz:**
- `RowPanel` (`Components.kt:125`): la fila como panel de cristal de nivel `CARD` (nunca difumina, `Components.kt:138`), en «Sólido» el color de superficie con su borde (y el de «Alto contraste»); el brillo de `panelRipple(animationsOn)` y la marca quieta `pressSignal` sin animaciones (`:143`, `:157`), igual que `SectionCard`; `combinedClickable` (tocar y mantener). Seleccionada: borde de 2 dp en el acento (3:1 ya medido) y `semantics.selected`.
- `EntryRow.kt`: deslizar con `draggable`; umbral `SWIPE_THRESHOLD = 0.4f` (`:103`), acción al soltar (izquierda: editar; derecha: «¿Mover «<título>» a la papelera?» y después «Deshacer» 5 s con `UndoTrash`), vibración `HapticFeedbackType.LongPress` al pasar el umbral (`:179`) y al entrar en selección (`:209`). Lo que destapa el deslizar se dibuja solo en la franja que deja la fila (`SwipeReveal`, `:254`), nunca bajo la fila translúcida. Acciones de TalkBack: «Editar», «Mover a la papelera» y «Mover a…» (`:217`). «⋮» (`RowMenu`, `:299`): copiar usuario, contraseña (la de un acceso o de un wifi, `passwordForHealth`), 2FA, editar, duplicar, mover a…, papelera. 2FA (`TotpBadge`, `:273`): tocar copia (con `markUsed`), círculo de cuenta atrás (en rojo con menos de 5 s) y «Copiado el código siguiente». `now` llega como `State<Long>` y solo lo lee el 2FA: las demás filas ya no se redibujan cada segundo.
- `VaultList.kt`: las copias releen la entrada de la bóveda abierta al tocar (`liveEntry`) y van por `copySensitive`; lo que sigue (mensaje, `markUsed`, la oferta) va en su `onCopied`, así no pasa nada si el aviso A14 está esperando (`:420`, `:429`, `:439`). Barra de selección abajo (`SelectionBar`, `:727`; con `pointerInput` para que ningún toque llegue a la fila de debajo, `:740`: «N seleccionadas», «Seleccionar todo», «Mover a carpeta», «Etiquetar», «Favorita»/«Quitar favorita», «Papelera» con «¿Mover N entradas a la papelera?», o el título si es una); Atrás termina la selección (`:386`); el «+» se esconde mientras (`VaultScreen.kt:259`). Lista vacía: «Crear la primera» e «Importar» (copia de Clavero o de otra app) (`:700`). Al abrir la bóveda el foco va al buscador sin teclado (`showKeyboardOnFocus = false`, una vez por apertura según `lockEpoch`, desde el propio elemento, `:493`).
- `VaultOrganize.kt`: `OrganizeDialog.MoveSelected(ids)` y `TagSelected(ids)` (`:119`), con el selector de carpetas y el diálogo de nombre de siempre; una etiqueta vacía dice `TAG_NOT_VALID`.
- «Deshacer» quita antes el mensaje que haya en pantalla, para que sus 5 s sean suyos (`VaultScreen.kt:181`), y cada paso a la papelera tiene los suyos aunque dos seguidos den la misma cuenta (dos deslizamientos de una entrada): `UndoTrash.moves` (`Trash.kt:93`) entra en la clave del efecto (`VaultScreen.kt:175-177`).
- Confirmación de varias entradas en plural («Puedes restaurarlas…», `moveToTrashText`); mover lo que ya estaba en esa carpeta dice «Ya estaban ahí».
- Flujo de Maestro P-5 (`.maestro/borra-datos/p05-ayuda-codigos-qr.yaml`) y `.maestro/README.md`: pulsa «Crear la primera» en lugar de «debajo de «Tu bóveda está vacía. Pulsa «Añadir».»» (sin ejecutar aquí; tarea 11).
- «Copiar contraseña» al volver (`PasswordOfferOnReturn`, `EntryRow.kt:363`): en `ON_RESUME`, si `TwoStepCopy` aún la ofrece, un mensaje con acción durante lo que quede de los 30 s (`withTimeoutOrNull(twoStep.leftMs())`); la contraseña se lee de la bóveda al tocar. En la lista y en la ficha.
- `BigPasswordScreen.kt`: letra grande monoespaciada, grupos de 4, una celda por punto de código (un par sustituto no se parte; un control bidi no reordena; un espacio sale «␣» y un invisible «�»). Letras normales; cifras en negrita y subrayadas y en el acento; símbolos en un recuadro y en `tertiary` (el color nunca es la única señal, R-1), con leyenda. El secreto sale de un ViewModel que lee el estado de la bóveda (`BigPasswordViewModel`, `:109`); la ruta solo lleva ids (`vault/entry/{id}/big?field={field}`, `AppNavHost.kt:307`); se cierra al bloquear o si la entrada o el campo desaparece; un id de campo repetido no enseña ninguno. Gira con el móvil (`MainActivity` ya maneja el giro).
- Ficha (`VaultEntryScreen.kt`, `EntryFields.kt`): ya tenía «Copiar» en cada campo; ahora lo que sigue a la copia espera a que se haga (`VaultEntryScreen.kt:142`); «Copiar» del usuario ofrece la contraseña (`:255`); cualquier otra copia termina la oferta; «Ver en grande» en la contraseña o la passphrase (`:259`) y en cada campo oculto (`:274`).
- Arrastres T4 pendientes hechos: «Mover a…» y «Duplicar» en «⋮»; filas como paneles de cristal sin difuminar; marca quieta al pulsar sin animaciones; el test de N-3 lee llamadas de varias líneas.

## Tests

- Nuevos: `vault/TrashTest.eachMoveHasItsOwnMark` (rojo con `moves` constante, verde después), `vault/SelectionTest` (8: `selectAllTakesOnlyVisible`, `clearedOnLock`, `clearedOnFilterChange`, poda, favorita y etiqueta con versión y topes, título de la confirmación), `tools/TwoStepCopyTest` (7: `offersFor30s`, `neverHoldsPassword` por reflexión, `clearedOnLock`, reloj hacia atrás, código siguiente con menos de 5 s, código no admitido), `OrganizeStoreTest.selectionFavoriteAndTagSaveOnce`, `ui/screens/BigPasswordTest` (3), `ui/screens/EntryRowTest` (40 %), `GlassContrastTest.theRowsOfTheListRead` (todas las paletas, estilos, claro/oscuro y «Alto contraste»: lo que destapa el deslizar a 4,5:1 o 7:1, y `failures` vacío), `PlatformSourcesTest`: las filas son `RowPanel` con la regla de `SectionCard`, el detector de paneles pulsables lee varias líneas (con su propio test de muestras), `.glass(` solo en `ui/theme`; `NoCopyMenuTest`: sin texto seleccionable en las filas ni en «Ver en grande».
- Al día: `PlatformSourcesTest` (pins de R9 movidos a `EntryRow.kt`, `BigPasswordScreen.kt` y las confirmaciones; el resultado de la lista con su poda), `VaultTotpTest` (lee `EntryRow.kt`).
- **Resultados:** `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets` → BUILD SUCCESSFUL, **2146 tests, 0 fallos** (antes 2121). `./gradlew -p jvm-check test` → BUILD SUCCESSFUL, **2109 tests, 0 fallos** (antes 2090). `lintDebug`: 0 errores, 20 avisos, todos en `build.gradle.kts`, `libs.versions.toml`, el manifiesto, `gradle-wrapper.properties` y `mipmap-anydpi-v26`: ninguno en archivos de la tarea (no he comparado el número con la base). El único aviso de Kotlin (`AppNavHost.kt:440`, «Expression is unused») ya estaba en `backOnce`.

### TDD: rojo y verde

- Rojo (stubs que compilan): `SelectionTest` 8 de 8 fallan por aserción (p. ej. `selectAllTakesOnlyVisible`: «expected:<[a1, a2]> but was:<[]>»); `TwoStepCopyTest` 6 de 7 fallan (`offersFor30s`: «expected:<entrada-1> but was:<null>»; `neverHoldsPassword`: «no offer»; `withLessThan5sTheRowCopiesTheNextCode`: «expected:<[732303]> but was:<[]>»). `aClockGoingBackEndsTheOffer` pasaba con el stub (que nunca ofrece).
- Rojo de N-3: con el detector de una línea de antes, `the pressable panel check reads calls on several lines` falló (no ve `Card(\n modifier = Modifier\n .clickable`), y `the rows of the list are panels…` falló (no había `RowPanel`).
- Verde: todos tras implementar (arriba).
- `OrganizeStoreTest.selectionFavoriteAndTagSaveOnce`, `BigPasswordTest` y `EntryRowTest` se escribieron junto con su código (rojo por compilación, no por aserción): lo apunto como desviación menor del orden TDD.

## Archivos

- Nuevos: `app/src/main/java/app/seguridad/vault/Selection.kt`, `vault/ListTexts.kt`, `tools/TwoStepCopy.kt`, `ui/screens/EntryRow.kt`, `ui/screens/BigPasswordScreen.kt`; tests `vault/SelectionTest.kt`, `tools/TwoStepCopyTest.kt`, `ui/screens/BigPasswordTest.kt`, `ui/screens/EntryRowTest.kt`.
- Cambiados: `vault/Trash.kt` (`moves`), `vault/TrashTexts.kt` (`moveToTrashText`), `.maestro/borra-datos/p05-ayuda-codigos-qr.yaml`, `.maestro/README.md`, `ui/theme/Components.kt`, `ui/screens/VaultList.kt` (la fila salió a `EntryRow.kt`), `ui/screens/VaultScreen.kt`, `ui/screens/VaultEntryScreen.kt`, `ui/screens/EntryFields.kt`, `ui/screens/VaultOrganize.kt`, `ui/AppNavHost.kt`, `AppContainer.kt`, `vault/VaultStore.kt`, `vault/OrganizeTexts.kt` (`EMPTY_VAULT` sin «Pulsa «Añadir»», ahora hay botones); tests `PlatformSourcesTest.kt`, `GlassContrastTest.kt`, `NoCopyMenuTest.kt`, `OrganizeStoreTest.kt`, `VaultTotpTest.kt`.

## Revisión propia

- Selección ⊆ visible por construcción (`selectAll` + `retainVisible` tras cada resultado), y vacía con la bóveda cerrada (no se puede seleccionar con `open = false`).
- Estado guardado: `PlatformSourcesTest` comprueba que no hay `rememberSaveable` ni `SavedStateHandle` en `VaultList.kt`, `VaultOrganize.kt`, `EntryRow.kt` ni `BigPasswordScreen.kt`.
- Ninguna copia guarda el valor: las acciones reciben la entrada, releen por id (`liveEntry`) y copian con `copySensitive`. El orden «copiar, luego mensaje/uso/oferta» respeta el aviso A14 (antes, `Field` llamaba a `onCopied` aunque la copia esperase al aviso; ahora no).
- Acciones de la fila creadas una vez (`remember`) y lo que cambia leído con `rememberUpdatedState` (carpetas, `onOpen`, `onEdit`, `onMessage`).
- Claves de Compose: las filas siguen con `lazyKey('r'|'f'|'a', id)` (T4 C-1); ningún campo usa su id solo como clave (T5 I-3 no cambia).
- `FLAG_SECURE`, permisos, componentes exportados y dependencias: sin cambios (ninguna dependencia ni permiso nuevo; la vibración es `performHapticFeedback`, sin `VIBRATE`).

## Dudas

1. **Copia en dos pasos con «Al salir»:** con el bloqueo «Al salir», salir de Clavero bloquea y el bloqueo termina la oferta (como pide «se borra al bloquear»), así que con ese ajuste nunca aparece «Copiar contraseña» al volver. Con el de por defecto (5 min) sí. No he debilitado el borrado al bloquear. Si el dueño lo quiere también con «Al salir», sería otra decisión (guardar el id más allá de un bloqueo).
2. **Cuándo sale la oferta:** en cada `ON_RESUME` de la lista o de la ficha mientras quede tiempo, también al volver de la ficha a la lista dentro de la app (no solo desde otra app). Me parece útil y es lo mismo que «al volver»; si molesta, se limita a la vuelta desde fuera.
3. **Favorita y etiquetar en bloque guardan una versión** de cada entrada (como el editor, que también versiona un cambio de favorita o de etiquetas), al contrario que «Mover a…» de la tarea 4, que no versiona. Lo he elegido para que la barra haga lo mismo que editar una a una.
4. **«Ver en grande»** se ofrece en la contraseña de un acceso, la passphrase de una semilla y cada campo oculto (también el CVV y el PIN de una tarjeta); no en la clave del 2FA ni en las notas ocultas.
5. **«Importar» de la lista vacía** abre un menú con «Importar copia» (la de Clavero, reemplaza) e «Importar de otra app» (fusiona), porque los dos sirven a una bóveda vacía.
6. **«Seleccionar todo»** reemplaza la selección por lo visible (no la une), coherente con «selección ⊆ visible».
7. **La barra de selección** es un panel de nivel `OVERLAY` (difumina desde Android 12, ya medido en los tests de contraste); queda encima de la lista con margen abajo (176 dp) para que la última fila no quede tapada.
8. **Mensajes que se quitan unos a otros:** «Deshacer» y la oferta de «Copiar contraseña» quitan el mensaje que haya en pantalla; si coinciden, uno corta al otro (caso raro; «Restaurar» de la papelera sigue ahí).

## Comprobar en el móvil

- Deslizar a la derecha (de izquierda a derecha) frente al gesto de Atrás del borde del sistema (en la pestaña raíz, sale de la app): conviene empezar el deslizamiento lejos del borde; ver si se nota.
- El asa de arrastrar a una carpeta frente al deslizar de la fila.
- La vibración al pasar el 40 % y al mantener pulsado; que no vibra de más.
- Con las animaciones quitadas (Opciones de desarrollador): la marca quieta del borde al pulsar una fila, sin brillo; el deslizar vuelve sin animación.
- El foco en el buscador al abrir la bóveda sin abrir el teclado (Gboard y el teclado del fabricante); un toque lo abre.
- «Copiar usuario» → ir a otra app → volver: «¿Copiar también la contraseña…?» durante lo que quede de 30 s; con «Al salir» no sale (duda 1).
- Android 8 a 12: el aviso A14 en la primera copia desde la fila; el mensaje «copiado» solo tras aceptarlo.
- 2FA en la fila: el círculo, el rojo con menos de 5 s y «Copiado el código siguiente».
- «Ver en grande» girando el móvil, con TalkBack (lee los caracteres y «espacio») y con letra al 200 %.
- TalkBack en una fila: «Seleccionar» al mantener, las acciones «Editar», «Mover a la papelera», «Mover a…», y «N seleccionadas» al cambiar.
- Fluidez de la lista con muchas entradas en «Cristal» (las filas translúcidas sin difuminar).

## Revisión experto-seguridad-cripto

Modelo de amenaza: quien tiene el móvil desbloqueado, otra app que lee el portapapeles o el historial del teclado, un archivo importado hostil (títulos con bidi o invisibles), la muerte del proceso y un bloqueo a mitad.
- **Secretos fuera de navegación y estado guardado:** la ruta de «Ver en grande» solo lleva ids codificados (`AppNavHost.kt:307`); el secreto lo lee `BigPasswordViewModel` del estado de la bóveda (`BigPasswordScreen.kt:109`, `:115`) y se cierra al bloquear (`:129`). Ningún `rememberSaveable` nuevo (lo comprueba `PlatformSourcesTest`, «nothing of the list in saved state»).
- **Copia en dos pasos sin secreto:** `TwoStepCopy` solo guarda id, hora y contador de bloqueos (`TwoStepCopy.kt:23`, `:44`; `TwoStepCopyTest.neverHoldsPassword` lo fija por reflexión); se acaba con un bloqueo, un cambio de bóveda o un reloj hacia atrás.
- **Portapapeles:** todas las copias nuevas por `copySensitive` (marca sensible, borrado según Ajustes, aviso A14), y lo que depende de la copia en su `onCopied` (`VaultList.kt:418`, `:427`, `:437`; `VaultEntryScreen.kt:142`). Los campos ocultos copian igual que la contraseña.
- **Leer al tocar:** `liveEntry` relee por id y descarta personales (`EntryRow.kt:136`): una fila vieja no copia un valor anterior ni de una entrada ya en la papelera.
- **Texto plano:** títulos, carpetas, etiquetas y mensajes con `plainReportText` (pins de R9 en `PlatformSourcesTest`); «Ver en grande» enseña cada punto de código en su celda y marca los invisibles (`BigPasswordScreen.kt:66`), así un control bidi no reordena ni esconde caracteres.
- **Sin selección de texto del sistema** en filas ni en «Ver en grande» (`NoCopyMenuTest`).
- **Selección:** solo ids, ⊆ visible, vacía al bloquear (`Selection.kt:41`, `:68`); las acciones en bloque usan el mismo filtro que `trash()` (vivas y no personales, `Selection.kt:97-102`).
- Sin cambios de formato, cripto, red, permisos ni `FLAG_SECURE`.

## Revisión Ingeniero Móvil Senior

- **Rendimiento de la lista:** `now` como `State<Long>` leído solo por `TotpBadge` (`EntryRow.kt:273`): las filas sin 2FA no se recomponen cada segundo (antes toda la lista). Filas de nivel `CARD` sin `RenderEffect` (`Components.kt:138`). Acciones de la fila en un `remember` con `rememberUpdatedState` (`VaultList.kt`).
- **Gestos:** `draggable` horizontal en el contenedor, desactivado al seleccionar; el asa (hija) consume su arrastre antes; el código 2FA tiene su `clickable` dentro del panel; `combinedClickable` del panel con su propio `MutableInteractionSource` para la marca quieta (`Components.kt:143-157`).
- **Ciclo de vida:** la oferta en `LifecycleEventEffect(ON_RESUME)` (`EntryRow.kt:363`); Atrás termina la selección (`VaultList.kt:384`); «Ver en grande» con su ViewModel en el ámbito de la bóveda (`VaultScoped`) y `stateIn(Eagerly)`; el foco del buscador se pide desde el propio elemento (no lanza si la fila no está compuesta) y una vez por apertura (`VaultList.kt:491`).
- **Accesibilidad:** acciones de TalkBack de deslizar y mover (`EntryRow.kt:217`), `onLongClickLabel`, `semantics.selected`, `liveRegion` en el contador, el icono decorativo sin leer, `contentDescription` del «⋮» con el título y del 2FA con los segundos; «Ver en grande» con marcas además del color.
- **Contraste:** reveal medido (`GlassContrastTest.theRowsOfTheListRead`); el resto de colores de la fila ya están en `textColors`/`surfacesOf`.
- **Snackbar:** «Deshacer» descarta el mensaje en pantalla para no perder sus 5 s (`VaultScreen.kt:179`); la oferta, igual (`EntryRow.kt` en `PasswordOfferOnReturn`).

## Textos para el Abogado Digital (revisión final)

Nuevos (`vault/ListTexts.kt` salvo indicación):
- «Más opciones de «<título>»», «Seleccionar»
- «Copiar usuario», «Copiar contraseña», «Copiar 2FA», «Editar»
- «Usuario copiado», «Contraseña copiada», «Código copiado», «Copiado el código siguiente», «Toca para copiar», «Quedan N s»
- «¿Copiar también la contraseña de «<título>»?»
- «1 seleccionada» / «N seleccionadas», «Seleccionar todo», «Terminar la selección», «Mover a carpeta», «Etiquetar», «Favorita», «Quitar favorita», «Papelera», «Etiqueta», «Etiquetar 1 entrada» / «Etiquetar N entradas»
- «1 entrada movida» / «N entradas movidas» + « a «<carpeta>»» o « fuera de las carpetas»
- «Ya la tenían (o no caben más de 10 etiquetas).», ««<etiqueta>» puesta en 1 entrada» / «… en N entradas»
- «Sin cambios», «1 entrada marcada como favorita» / «N entradas marcadas como favoritas», «1 entrada ya no es favorita» / «N entradas ya no son favoritas»
- «Crear la primera», «Importar» (y su menú con los textos que ya había: «Importar copia», «Importar de otra app»)
- «Ver en grande», «En grande · <título>», «Letras», «Cifras», «Símbolos», «espacio» (TalkBack)
- Cambiado (`vault/OrganizeTexts.kt`): «Tu bóveda está vacía. Pulsa «Añadir».» → «Tu bóveda está vacía.»
- «Puedes restaurarlas desde la papelera durante 30 días.» (`vault/TrashTexts.kt`, varias entradas), «Ya estaban ahí» (mover a la carpeta en la que ya estaban)
- Reutilizados: «¿Mover «<título>» a la papelera?», «¿Mover N entradas a la papelera?», «Puedes restaurarla desde la papelera durante 30 días.», «Mover a la papelera», «Deshacer», «Duplicar», «Mover a…».


---

## Ronda 1 de arreglos (revisión `task-6-review.md`)

**Commit:** `0f6051d` «2.0, tarea 6: ronda 1 de arreglos» (Egt17baa, sin `push`).

| Hallazgo | Arreglo | Evidencia |
|---|---|---|
| **I-1** (Important, seguridad): mensajes con título, etiqueta o carpeta sobre «Bóveda bloqueada» | Al bloquear se quita el mensaje en pantalla; los mensajes de la lista se lanzan en un ámbito creado dentro del contenido de `VaultGate`, que el bloqueo cancela con los que esperan en cola (también los de la tarea 4: carpetas, `duplicatedText`, `taggedSelected`, `movedSelected`, todo lo que pasa por `onMessage`). | `VaultScreen.kt:178`, `:273-275`; test `PlatformSourcesTest.the list's messages go at the lock` |
| **⚠️ 1**: «Mover a…» en «Códigos» | `canMove` ya no excluye «Códigos» en «⋮», en las acciones de TalkBack ni en la barra de selección (con carpetas). | `VaultList.kt:481`, `:619` |
| **⚠️ 2 / m-1**: la oferta salía al navegar dentro de la app | `TwoStepCopy.markLeft()` desde `MainActivity.onStop` (no en un giro ni con el selector o el escáner abiertos); `takeReturnOffer()` da el id una sola vez por vuelta y solo si se salió dentro de los 30 s; la pantalla solo mira los cambios que llegan mientras existe (`changes == atStart` no ofrece), así que nunca al componerse ni al navegar. Con «Al salir» sigue sin salir (el bloqueo la borra; aceptado). | `TwoStepCopy.kt:44`, `:54`; `MainActivity.kt:115`; `EntryRow.kt:365-370`; tests `TwoStepCopyTest.offeredOnlyOnReturnFromOutside`, `PlatformSourcesTest.the copy in two steps offers on the return from outside the app` |
| **m-2**: la oferta seguía a la vista tras terminar | `TwoStepCopy.changes` (`StateFlow<Long>`) sube al ofrecer, al salir y al terminar; el efecto se reinicia y quita el mensaje. | `TwoStepCopy.kt:34`; test `TwoStepCopyTest.changesTellTheScreen` |
| **m-3**: marca quieta de la fila | `pressSignal` antes de `clip`, como en `SectionCard`; la fila seleccionada ya no lleva el borde del acento: se rellena con `secondaryContainer` (opaco, donde ya se miden todos los colores de texto, las etiquetas y el acento a 3:1). | `Components.kt:144`, `:151`; pins en `PlatformSourcesTest.the rows of the list are panels…` |
| **m-4**: código 2FA caducado tras el aviso A14 | `copyOrAskNoticeFor` / `ClipboardNoticeRequest.askFor` / `copySensitiveFor`: el texto se calcula al escribirlo (al instante o al aceptar el aviso), desde la entrada tal como esté entonces; si ya no hay nada, no se copia ni se avisa. `ask(text)` de siempre pasa por el mismo camino. | `ClipboardNotice.kt:40`, `:85`, `:109`; `Clipboard.kt:64`; `VaultList.kt:442`; test `ClipboardNoticeTest.aCopyWorkedOutAtWriteTime` |
| **m-5**: focus 4 sin fijar en el cableado | Pins: `onSelectAll = { list.selection.selectAll(result.shown) }` (y una sola llamada a `selectAll`), y la regex del resultado llega hasta `selection.retainVisible(r.shown)`. | `PlatformSourcesTest` (test de la lista) |
| **m-6**: `ROW_MENU` sin usar | Quitado (y de la lista del Abogado). | `vault/ListTexts.kt` |
| **m-7**: `.maestro/README.md:136` | Punto 3: «P-5 pulsa «Crear la primera» de la bóveda vacía (2.0): comprobar que Maestro la encuentra por su texto.» | `.maestro/README.md:136` |

**TDD:** rojo con stubs que compilan: `ClipboardNoticeTest.aCopyWorkedOutAtWriteTime`, `TwoStepCopyTest.offeredOnlyOnReturnFromOutside` y `TwoStepCopyTest.changesTellTheScreen` FAILED; verdes tras implementar. Los pins nuevos de `PlatformSourcesTest` fallan sobre `0f5be65` por construcción (buscan el código nuevo); no los ejecuté contra la cabeza anterior.

**Tests ejecutados:**
- Enfocados: `./gradlew -p jvm-check test --tests 'app.seguridad.tools.*'` → BUILD SUCCESSFUL; `./gradlew :app:testDebugUnitTest --tests 'app.seguridad.PlatformSourcesTest' --tests 'app.seguridad.tools.*' --tests 'app.seguridad.vault.SelectionTest' --tests 'app.seguridad.ui.*'` → BUILD SUCCESSFUL.
- Completos:
  - `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets --no-daemon --max-workers=1` → `BUILD SUCCESSFUL in 4m 38s`, **2151 tests, 0 fallos**; lint 0 errores y 20 avisos, todos en `build.gradle.kts`, `libs.versions.toml`, el manifiesto, `gradle-wrapper.properties` y `mipmap-anydpi-v26` (ninguno en archivos de la tarea).
  - `./gradlew -p jvm-check test --no-daemon --max-workers=1` → `BUILD SUCCESSFUL in 1m 39s`, **2114 tests, 0 fallos**.

**Dudas de la ronda:**
- `markLeft` también cuenta abrir otra actividad que no marca `VaultLock.keepOpenOnce` (por ejemplo, el diálogo del sistema para compartir, si alguna vez lo hubiera en la lista). El selector y el escáner sí lo marcan y no cuentan.
- La fila seleccionada ahora es un relleno opaco (`secondaryContainer`) también en «Cristal»: se ve distinta del resto de filas translúcidas; a comprobar en el móvil junto con la marca quieta sin animaciones.

**Comprobar en el móvil (además de lo de arriba):** «Copiar usuario» → otra app → volver: la oferta sale una vez; abrir la ficha o cambiar de pestaña no la vuelve a sacar; «Copiar» de la contraseña en la ficha la quita de la pantalla. Bloquear con un mensaje de «Duplicar» o «Etiquetar» en pantalla o en cola: no queda nada sobre «Bóveda bloqueada». Android 8 a 12: tocar un 2FA, esperar en el aviso A14 y aceptar: se copia el código vigente.

### Revisión experto-seguridad-cripto (ronda 1)
- I-1 cerrado: nada de la bóveda queda en pantalla ni sale después del bloqueo desde la lista (`VaultScreen.kt:178`, `:273-275`). La ficha y «Ver en grande» ya salían de la composición con su `SnackbarHost`.
- m-4: el aviso A14 sigue rechazando la copia si hubo un bloqueo entre el aviso y la aceptación (la época se comprueba antes de calcular el texto, `ClipboardNotice.kt:109`), y el texto calculado sale de la bóveda abierta en ese momento.
- `TwoStepCopy` sigue sin guardar nada secreto: un `Boolean` y un contador `Long` más (`TwoStepCopyTest.neverHoldsPassword` lo comprueba por reflexión).

### Revisión Ingeniero Móvil Senior (ronda 1)
- La salida de la app se marca en `MainActivity.onStop` con las mismas condiciones que el bloqueo al salir (`MainActivity.kt:115`); la pantalla recoge `changes` con `collectAsStateWithLifecycle`, así que la marca hecha fuera llega al volver.
- El ámbito de los mensajes vive con el contenido de `VaultGate`: un bloqueo lo cancela y `showSnackbar` cancelado quita su mensaje.
- `pressSignal` fuera del recorte en `RowPanel` (`Components.kt:151`), como en `SectionCard`.

### Textos para el Abogado Digital (ronda 1)
- Quitado: «Más opciones» (`ROW_MENU`, no se usaba).
- `.maestro/README.md`, punto 3: «P-5 pulsa «Crear la primera» de la bóveda vacía (2.0): comprobar que Maestro la encuentra por su texto.» (documento técnico).
- Para la veracidad: la oferta de «Copiar contraseña» sale solo al volver desde fuera de la app y no con el bloqueo «Al salir».

---

## Ronda 2 de arreglos (re-revisión `task-6-rereview-1.md`)

**Commit:** `04b9865` «2.0, tarea 6: ronda 2 de arreglos» (Egt17baa, sin `push`). N-2 aceptado por el controlador, sin tocar.

| Hallazgo | Arreglo | Evidencia |
|---|---|---|
| **N-1** (Minor): al aceptar el aviso A14 con `produce()` en null (la entrada ya no está) se devolvía `NOTHING_WAITING` sin apuntar `acceptedNow`, y el aviso volvía a salir | Nuevo resultado `AcceptOutcome.NOTHING_TO_COPY`: no copia nada ni llama a `onCopied`, pero apunta `acceptedNow`; `ClipboardHistoryNotice` lo trata como `COPIED` y guarda la aceptación en Ajustes. Siguen igual `LOCKED` (un bloqueo desde el aviso no copia ni apunta nada), `NOTHING_WAITING` y `WRITE_FAILED`. | `tools/ClipboardNotice.kt:110`, `:136`; `ui/ClipboardHistoryNotice.kt:61`; test `ClipboardNoticeTest.acceptedWithNothingToCopyStillCountsAsAccepted` (y `aCopyWorkedOutAtWriteTime` al día) |

**TDD:** rojo con el valor nuevo del `enum` ya compilando y sin cambiar `acceptOutcome`: `ClipboardNoticeTest.acceptedWithNothingToCopyStillCountsAsAccepted` y `aCopyWorkedOutAtWriteTime` FAILED; verdes tras el arreglo.

**Tests ejecutados:**
- Enfocados: `./gradlew -p jvm-check test --tests 'app.seguridad.tools.ClipboardNoticeTest' --tests 'app.seguridad.tools.TwoStepCopyTest' --no-daemon --max-workers=1` → `BUILD SUCCESSFUL in 11s`.
- Completos:
  - `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets --no-daemon --max-workers=1` → `BUILD SUCCESSFUL in 4m 32s`, **2152 tests, 0 fallos**; lint 0 errores y 20 avisos, todos en `build.gradle.kts`, `libs.versions.toml`, el manifiesto, `gradle-wrapper.properties` y `mipmap-anydpi-v26` (ninguno en archivos de la tarea).
  - `./gradlew -p jvm-check test --no-daemon --max-workers=1` → `BUILD SUCCESSFUL in 1m 31s`, **2115 tests, 0 fallos**.

### Revisión experto-seguridad-cripto (ronda 2)
- La época del bloqueo se sigue comprobando antes de calcular el texto (`ClipboardNotice.kt`, `acceptOutcome`): tras un bloqueo, `LOCKED`, sin copiar ni apuntar la aceptación. Con `NOTHING_TO_COPY` no se escribe nada en el portapapeles; solo se recuerda que el usuario ya leyó y aceptó el aviso, que es lo que pasó.

### Revisión Ingeniero Móvil Senior (ronda 2)
- El `when` de `ClipboardHistoryNotice.kt:61` cubre el valor nuevo; guardar la aceptación en Ajustes sigue en `appScope` como antes.

### Textos para el Abogado Digital (ronda 2)
- Ninguno nuevo.
