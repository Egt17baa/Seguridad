# Revisión de la tarea 6 de la 2.0: la lista y la ficha

**Base** `f8d1d54` · **Cabeza** `0f5be65` (5 commits) · **Paquete** `review-f8d1d54..0f5be65.diff` (29 archivos, +1883/−174).
**Lentes:** `experto-seguridad-cripto` (SKILL.md, «Trabajo conjunto con superpowers»: revisor de tarea), `ingeniero-movil-senior:desarrollo-movil-senior` y `ingeniero-movil-senior:auditoria-seguridad-movil` (MASVS-STORAGE, -PLATFORM, -PRIVACY).
**Alcance:** solo lectura. No se ha vuelto a ejecutar la suite. Las comprobaciones fuera del diff se limitan a los riesgos con nombre: `VaultState.Unlocked.entries`, `modifyData`, `totpCode`, `copySensitive`, `pressSignal`, `key(vault)`/`VaultScoped` y el informe de lint de `app/build/reports/`.

---

### Spec Compliance

**Interfaces del brief**

- ✅ `Selection { toggle; selectAll(visible); clear; ids: StateFlow<Set<String>> }`, en el ViewModel de la lista: `vault/Selection.kt:19-56` y `VaultList.kt:226` (`val selection = Selection()`). Se vacía al bloquear (`Selection.kt:68-73`, más `reset()` en `VaultList.kt` y `followVault` en el `init`) y al cambiar de filtro (`onFilter`/`selectionFilterKey`, `Selection.kt:60-78`; se llama en `VaultList.kt:263`).
- ✅ `TwoStepCopy(clock)` con `offerPassword`, `pending()` (30 s) y `clear()`. Solo guarda el id, la hora y la época (`tools/TwoStepCopy.kt:17-50`). Hay una sola instancia, en `AppContainer.kt:776`, que se borra al bloquear. Añade el parámetro `lockEpoch`: es más estricto que el brief, así que está bien.
- ✅ Deslizar: el umbral es el 40 % y la acción se hace al soltar (`EntryRow.kt:103-112`, `:187-198`). A la izquierda, editar; a la derecha, «¿Mover «<título>» a la papelera?» (`VaultList.kt` `trashAsk` → `TrashConfirm`) y después «Deshacer» durante 5 s (`VaultScreen.kt:175-195`, `UndoTrash.moves`). Vibra con `HapticFeedbackType.LongPress` al pasar el umbral (`EntryRow.kt:179`).
- ✅ Mantener pulsado entra en selección (`RowPanel.onLongClick`, `EntryRow.kt:206-210`).
- ✅ «⋮»: copiar usuario, contraseña y 2FA, editar, duplicar, mover a… y papelera (`EntryRow.kt:299-330`).
- ✅ `customActions`: «Editar», «Mover a la papelera» y, si se puede mover, «Mover a…» (`EntryRow.kt:182-186`).
- ✅ 2FA en la fila: tocar copia y llama a `markUsed` (`VaultList.kt:436-446`). Hay un círculo de cuenta atrás que se pone rojo con menos de 5 s (`EntryRow.kt:273-296`). Con menos de 5 s se copia el código siguiente y se dice «Copiado el código siguiente» (`TwoStepCopy.kt:59-64`).
- ✅ Barra de selección (`VaultList.kt:727-760`): «Seleccionar todo», «Mover a carpeta», «Etiquetar», «Favorita»/«Quitar favorita» y «Papelera» con «¿Mover N entradas a la papelera?».
- ✅ `BigPasswordScreen`: monoespaciada, en grupos de 4, con color y marca por tipo (`BigPasswordScreen.kt:66-83`, `:160-183`). Se cierra al bloquear o si desaparece la entrada o el campo (`:144`). El secreto sale de `BigPasswordViewModel` y la ruta solo lleva ids (`AppNavHost.kt:307-312`).
- ✅ Ficha: «Copiar» en cada campo; los ocultos copian como la contraseña, con `copySensitive` y `onCopied` tras la copia real (`VaultEntryScreen.kt:137-145`). «Ver en grande» en la contraseña, la passphrase y los campos ocultos.
- ✅ Lista vacía: «Crear la primera» e «Importar» (`VaultList.kt` `EmptyVaultActions`, ~`:695-715`). Al abrir la bóveda, el foco va al buscador con `showKeyboardOnFocus = false`, una vez por apertura (`VaultList.kt:~486-497`).

**Tests del paso 1**

- ✅ `SelectionTest.selectAllTakesOnlyVisible`, `clearedOnLock` y `clearedOnFilterChange`.
- ✅ `TwoStepCopyTest.offersFor30s`, `neverHoldsPassword` (por reflexión) y `clearedOnLock`.
- ✅ `NoCopyMenuTest`, con las filas y «Ver en grande» (`NoCopyMenuTest.kt` +14).

**Arrastres del controlador**

- ✅ «Duplicar» en «⋮» (`EntryRow.kt:313`). ⚠️ «Mover a…» está en «⋮», pero solo con `canMove = !totpOnly && (folders.isNotEmpty() || e.folderId != null)` (`VaultList.kt:479`). En «Códigos» no sale nunca, aunque las entradas 2FA pueden tener carpeta. Sin carpetas no hay adónde mover, así que eso es razonable. Es la misma regla que el asa de arrastrar de la tarea 4 (`showFolders`). Lo dejo para el controlador: el arrastre dice «must be present» y no hace excepción con «Códigos».
- ✅ Filas de cristal que nunca difuminan (`Components.kt:136-140`, `PanelLevel.CARD`). El texto se mide a 4,5:1 (7:1 en «Alto contraste») y lo que no es texto a 3:1, en `GlassContrastTest.theRowsOfTheListRead`. El rojo del círculo es `status.negative = error`, que `failures()` ya mide como texto (`ThemeSurfaces.kt:80`, `Palettes.kt:377`).
- ⚠️ Marca quieta al pulsar sin animaciones: existe (`Components.kt:157`), pero no es exactamente la de `SectionCard` (Minor m-3).
- ✅ Test 5 bis para varias líneas y para las filas nuevas: `pressablePanels` con `closing(...)` y sus muestras de varias líneas (`PlatformSourcesTest.kt`, «the pressable panel check reads calls on several lines»). Además, `RowPanel` se fija con la regla de `SectionCard` y se prohíbe `.combinedClickable` propio en `EntryRow.kt`.

**Restricciones globales**

- ✅ Ningún `rememberSaveable` ni `SavedStateHandle` en la lista, las filas ni «Ver en grande»; lo fija `PlatformSourcesTest`. Los diálogos (`trashAsk`, `OrganizeDialog.*Selected`) solo llevan ids.
- ✅ Texto plano: títulos, línea secundaria, carpeta, etiquetas, menú, oferta y confirmaciones pasan por `plainReportText`; los pins de R9 se movieron a `EntryRow.kt` y `BigPasswordScreen.kt`. Ningún enlace, `Linkify` ni WebView.
- ✅ Sin dependencias, permisos ni componentes nuevos. La vibración usa `performHapticFeedback`, sin `VIBRATE`. `FLAG_SECURE` no cambia y no hay registros con datos.
- ✅ Claves de Compose: `lazyKey('r'|'f'|'a', id)`. Los campos siguen con `key("t", …)` y `key("c", i, f.id)`.
- ✅ `markUsed` sigue siendo una marca por minuto y entrada, en segundo plano (`runCatching`, `scope.launch`). Con la bóveda bloqueada no hace nada: `modifyData` devuelve `false` si falta `dek` (`VaultStore.kt:767-770`). Al cambiar de bóveda va al `VaultStore` viejo, que ya está bloqueado, porque `key(vault)` + `VaultScoped` rehacen la composición (`AppNavHost.kt:178-179`).
- ✅ Portapapeles: todas las copias nuevas pasan por `copySensitive`, con la marca sensible, el borrado y A14. Lo que sigue a una copia va en su `onCopied`, así que no corre si el aviso A14 está esperando o se rechaza.
- ⚠️ El diseño dice «Copiar usuario y, **al volver a la app**, ofrece «Copiar contraseña»». `PasswordOfferOnReturn` cuenta cualquier `ON_RESUME` del `NavBackStackEntry`, incluido el primero al componerse (`EntryRow.kt:365-367`). Así, la oferta sale también al abrir la ficha después de «Copiar usuario» en la lista y al volver de la ficha a la lista, sin haber salido de la app. Con el bloqueo «Al salir» no sale nunca. Son las dudas 1 y 2 del implementador. La 1 es de producto, para el dueño. La 2 la recojo como Minor m-1.

**Riesgos con nombre**

1. **Resultado tardío tras bloquear o cambiar de bóveda.** El resultado de una copia no actúa: `liveEntry` lee de la bóveda abierta al tocar (`EntryRow.kt:136`) y A14 rechaza la copia si la bóveda se bloquea entre medias. El código siguiente se calcula al tocar. «Deshacer» es un efecto con la clave `unlocked` y `UndoTrash` comprueba la época. Las acciones en bloque usan `modifyData`, que con la bóveda bloqueada no guarda; un cambio de bóveda deja el `VaultStore` viejo bloqueado. La oferta en dos pasos termina con `lockEpoch`, que cuenta cada bloqueo y cada cambio. «Ver en grande» se cierra. **Pero sí se enseñan datos después del bloqueo:** los mensajes de la lista con un título o una etiqueta pueden verse, o seguir en cola, sobre «Bóveda bloqueada» (Important I-1).
2. **Papelera al deslizar.** ✅ La confirmación guarda el id (`trashAsk = e.id`) y vuelve a buscar la entrada en `result.data.live`. Si ya no está, se cierra (`LaunchedEffect(id) { trashAsk = null }`). «Deshacer» devuelve los ids que se movieron de verdad (`UndoTrash.last.ids`). En bloque, la confirmación vuelve a filtrar por `result.shown`. No se usa ningún índice.
3. **«Copiado el código siguiente».** ✅ `totpCode` da `left = period − (⌊now/1000⌋ mod period)` ∈ [1, period] (`Totp.kt:140-142`). Por tanto, `now + left·1000` cae en `⌊now/1000⌋ + left`, que es justo el primer segundo del paso siguiente, sea cual sea la fracción de segundo de `now`. `TwoStepCopyTest.withLessThan5sTheRowCopiesTheNextCode` lo prueba de 1 a 4 s y en el límite de 5 s. El único caso abierto es el retraso del aviso A14 (Minor m-4).
4. **Lint «sin avisos nuevos».** ✅, con un límite. `app/build/reports/lint-results-debug.xml` (04:57, dos minutos antes del commit `0f5be65` de las 04:59:50) tiene 20 avisos: 7 `GradleDependency`, 8 `NewerVersionAvailable`, 2 `AndroidGradlePluginVersion`, 1 `OldTargetApi`, 1 `DiscouragedApi` del manifiesto y 1 `ObsoleteSdkInt` de `mipmap-anydpi-v26`. Ninguno está en código. La base anterior, la ronda de la 5 bis (`task-5bis-report.md:5`), tenía 21 avisos de esas mismas categorías; los de versiones dependen de la red y del día. No aparece ningún ID nuevo. El límite: no tengo el XML de la base, solo su descripción, y el informe de lint es de antes de la hora del commit, aunque cuadra con «ejecutar y después hacer el commit».

---

### Strengths

- **Selección ⊆ visible por construcción.** `selectAll` filtra la papelera y lo personal aunque se lo pasen, y `retainVisible` poda tras cada resultado. `selectionFilterKey` no cuenta el orden, así que cambiar el orden no vacía la selección. Está bien pensado y bien probado (`SelectionTest`, 8 casos).
- **`TwoStepCopy` no puede guardar un secreto.** Un único punto de entrada que solo acepta un `String` id, más un test por reflexión que lo fija. Usa el reloj monotónico, termina si el reloj va hacia atrás y comprueba la época del bloqueo.
- **Copias que releen al tocar** (`liveEntry`), con el orden «copiar → mensaje/uso/oferta» dentro de `onCopied`. Arregla de paso el `Field` antiguo, que llamaba a `onCopied` aunque la copia esperara al aviso A14 (`VaultEntryScreen.kt:142`, `EntryFields.kt` `NotesCard`).
- **`BigPasswordScreen` honesto con caracteres hostiles:** una celda por punto de código, los controles bidi, los anchos cero y las marcas combinantes salen como «�», y un id de campo repetido no enseña ninguno. El color nunca es la única señal (R-1).
- **Rendimiento:** `now` es un `State<Long>` que solo lee `TotpBadge`, así que la lista deja de recomponerse entera cada segundo. Las acciones de la fila se crean una vez y leen lo que cambia con `rememberUpdatedState`.
- **`UndoTrash.moves`** resuelve bien el caso de dos deslizamientos seguidos con la misma cuenta, y tiene su test rojo y verde.
- **El detector 5 bis** lee llamadas de varias líneas e incluye `GlassPanel`, con sus muestras positivas y negativas. Además impide `.glass(` fuera de `ui/theme`.

---

### Issues

#### Critical

Ninguno.

#### Important

**I-1. Los mensajes de la lista con un título o una etiqueta sobreviven al bloqueo (riesgo con nombre 1).**
- **Dónde:** `VaultScreen.kt:155` (el `scope` de `VaultScreen`, fuera de `VaultGate`), `:198` (el `SnackbarHost` del `Scaffold`, que sigue en pantalla con la bóveda bloqueada) y `:269` (`message` = `scope.launch { snackbar.showSnackbar(text) }`). Mensajes nuevos de esta tarea que nombran datos: `VaultList.kt:449` (`duplicatedText(plainReportText(it.title))`, «Se ha creado «<título>»…») y `VaultOrganize.kt:185` (`taggedSelected(…, plainReportText(tag))`, «<etiqueta>» puesta en N entradas»). Viene de la tarea 4, que ya mostraba así los nombres de carpeta (`movedTo`, `movedSelected`), y esta tarea lo amplía a títulos y etiquetas.
- **Qué pasa:** al bloquear, `VaultGate` quita la lista de la composición, pero el `SnackbarHostState` y el ámbito que lanzó el mensaje siguen vivos. Un mensaje en pantalla se queda hasta 4 s sobre «Bóveda bloqueada». Uno en cola, porque `SnackbarHostState` los encadena (por ejemplo, «Usuario copiado» y después «Se ha creado «Banco…»»), aparece **después** del bloqueo. Nada descarta el mensaje al bloquear: el efecto de «Deshacer» sale con `return@LaunchedEffect undo.forget()` sin llamar a `dismiss()`.
- **Por qué importa:** `VaultGate` promete que, al bloquear, «no data stays on screen». El título de una entrada es dato cifrado de la bóveda. Quien coja el móvil justo tras pulsar «Bloquear» lo lee. El impacto es bajo (unos segundos y lo hizo el propio usuario), pero es un hallazgo de seguridad confirmado sobre un riesgo con nombre, y la skill pone Important como mínimo.
- **Corrección:** en `VaultScreen`, al pasar a bloqueada, descartar el mensaje y anular los pendientes. Por ejemplo, `LaunchedEffect(unlocked) { if (!unlocked) snackbar.currentSnackbarData?.dismiss() }`, y lanzar `message` en un ámbito que muera con `VaultGate`: un `rememberCoroutineScope()` dentro del contenido de `VaultGate`, que el bloqueo cancela, y con él los `showSnackbar` en cola. Un test de fuente en `PlatformSourcesTest` que fije las dos cosas, como los demás pins de bloqueo.

#### Minor

**m-1. La oferta «¿Copiar también la contraseña…?» sale sin haber vuelto a la app.**
- **Dónde:** `EntryRow.kt:363-375`; usada en `VaultScreen.kt:275` y `VaultEntryScreen.kt:215`.
- **Qué pasa:** `LifecycleEventEffect(ON_RESUME)` usa el `LifecycleOwner` del `NavBackStackEntry`. Recibe un `ON_RESUME` al componerse (el `LifecycleRegistry` reenvía los eventos perdidos al añadir el observador; por eso `returns == 0` en `:367` no lo evita) y otro con cada vuelta dentro de la app. Así, «Copiar usuario» en la lista → abrir la ficha enseña la oferta enseguida, sin haber pegado nada fuera.
- **Por qué:** el diseño dice «al volver a la app».
- **Corrección:** apuntar en `TwoStepCopy` que se salió de la app desde la oferta. Por ejemplo, `markLeft()` desde `MainActivity.onStop` (ya hace algo parecido con `clipboardNotice`) y que `pending()` solo dé el id si se salió. O escuchar el ciclo de vida de la `Activity` y no el de la entrada de navegación. Un test en `TwoStepCopyTest` de que sin salir no hay oferta.

**m-2. La oferta sigue en pantalla después de terminar.**
- **Dónde:** `EntryRow.kt:371-374`.
- **Qué pasa:** el mensaje es `Indefinite` con `withTimeoutOrNull(leftMs)`. Si otra copia termina la oferta mientras está a la vista (por ejemplo, «Copiar» de la contraseña en la ficha llama a `twoStep.clear()`), el mensaje sigue ofreciendo «Copiar contraseña» hasta el final de los 30 s, y pulsarlo no hace nada (`twoStep.pending() == id` es falso) y no lo dice.
- **Corrección:** que `TwoStepCopy` exponga su estado como `StateFlow`, o que el efecto lo compruebe periódicamente, y cancelar el mensaje cuando la oferta termine.

**m-3. La marca quieta de la fila no es la de `SectionCard` y se confunde con la selección.**
- **Dónde:** `Components.kt:142` (selección: borde de 2 dp en `primary`, antes del `clip`), `:148` (`.clip(RowShape)`) y `:157` (`.pressSignal(...)` **después** del `clip`).
- **Qué pasa:** en `SectionCard`, `pressSignal` está en el modificador de `Card`, fuera de su recorte. En `RowPanel` va después de `.clip(RowShape)`, así que el trazo de 2 dp centrado en el borde pierde la mitad de fuera. Además, la marca (un contorno de 2 dp en `primary`, `Glass.kt:203-212`) es igual al borde de una fila seleccionada. Sin animaciones, pulsar una fila seleccionada no da ninguna señal visible, y pulsar una sin seleccionar se parece a «seleccionada».
- **Corrección:** poner `.pressSignal` antes de `.clip`, igual que en `SectionCard`, y distinguir la marca de la selección (por ejemplo, un relleno translúcido en el acento al pulsar, o un borde de selección de otro grosor o en otro color medido a 3:1). El efecto visual queda para comprobarlo en el móvil, en «Comprobar en el móvil».

**m-4. El código 2FA puede copiarse caducado si hay aviso A14.**
- **Dónde:** `VaultList.kt:436-446` (`rowCodeToCopy(it, System.currentTimeMillis())` antes de `copySensitive`).
- **Qué pasa:** en Android 8 a 12, la primera copia enseña el aviso A14 y escribe el texto **al aceptarlo**. El código se calculó al tocar, así que si el usuario tarda en aceptar se copia uno ya caducado, con el mensaje «Código copiado» o «Copiado el código siguiente». Pasa una sola vez por instalación, pero es justo la promesa del riesgo 3.
- **Corrección:** si `copySensitive` devuelve `false` (el aviso está esperando), no copiar un código 2FA en diferido: que tras aceptar haya que volver a tocar. O que el camino del aviso acepte un proveedor de texto que se evalúe al escribir.

**m-5. Focus 4 no está fijado donde se conecta.**
- **Dónde:** `VaultList.kt:618` (`onSelectAll = { list.selection.selectAll(result.shown) }`), `:275` (`selection.retainVisible(r.shown)`) y `PlatformSourcesTest.kt:139` (la regex termina en `_result.value = r`).
- **Qué pasa:** `selectAllTakesOnlyVisible` prueba que `Selection` toma lo que le dan menos la papelera y lo personal, pero ningún test fija que la interfaz le pase `result.shown`, ni que la poda tras cada resultado siga ahí. Un cambio que pasara `data.live` o quitara `retainVisible` dejaría todos los tests en verde.
- **Corrección:** añadir a los pins de `PlatformSourcesTest` (en el test de la lista) `selection.selectAll(result.shown)` y extender la regex hasta `selection.retainVisible(r.shown)`.

**m-6. Hay un texto sin usar en la lista del Abogado.**
- **Dónde:** `vault/ListTexts.kt:10`, `const val ROW_MENU = "Más opciones"`. Solo se usa `rowMenuLabel(title)`.
- **Corrección:** quitarlo, o usarlo, y quitarlo también de «Textos para el Abogado Digital».

**m-7. `.maestro/README.md` sigue diciendo que P-5 pulsa «Añadir».**
- **Dónde:** `.maestro/README.md:136`, punto 3 de «Comprobaciones que faltan»: «`borra-datos/p05-ayuda-codigos-qr` toca «Añadir» por debajo del texto «Tu bóveda está vacía…»». El flujo ahora pulsa «Crear la primera», y la línea 145 ya lo dice.
- **Corrección:** cambiar el punto 3 por «P-5 pulsa «Crear la primera» de la bóveda vacía: comprobar que Maestro la encuentra por su texto».

**Observaciones sin hallazgo** (para el registro):
- `OrganizeDialog.MoveSelected` y `TagSelected` copian los ids al abrirse y no vuelven a filtrar por `shown` al confirmar, como sí hace la papelera en bloque. Mientras el diálogo está abierto, la lista no cambia desde fuera, así que no lo cuento como fallo.
- La duda 3 (favorita y etiquetar en bloque guardan una versión, a diferencia de «Mover a…») es coherente con el editor. Queda para el controlador.

---

### Assessment

**Spec:** cumple, con dos ⚠️ para el controlador o el dueño: «Mover a…» no sale en «Códigos», y la oferta sale con la navegación interna (m-1) y nunca con «Al salir».
**Task quality: Needs fixes.** Hay que arreglar I-1 y las siete Minor (m-1 a m-7). Todas son locales y no tocan formatos, criptografía ni permisos.

---

## Revisión experto-seguridad-cripto

**Modelo de amenaza:** quien tiene el móvil desbloqueado (también justo después de «Bloquear»), otra app que lee el portapapeles, un archivo importado hostil (títulos con controles bidi o invisibles), la muerte del proceso y el bloqueo o el cambio de bóveda a mitad de una acción.

- **Secretos fuera de la navegación y del estado guardado:** la ruta `vault/entry/{id}/big?field={field}` solo lleva ids codificados (`AppNavHost.kt:307-312`, `:298-299`). `BigPasswordViewModel` lee el valor de `store.state` y lo vuelve `null` al bloquear (`BigPasswordScreen.kt:131-140`, `:144`). No hay `rememberSaveable` ni `SavedStateHandle` (lo fija `PlatformSourcesTest`). ✅ (MASVS-STORAGE-2.)
- **`TwoStepCopy`:** guarda el id, `elapsedRealtime` y la época, nada más (`TwoStepCopy.kt:18`; `TwoStepCopyTest.neverHoldsPassword`). La contraseña se lee de la bóveda al tocar (`VaultScreen.kt:474-481`). ✅
- **Portapapeles (A14 y marca sensible):** todas las copias nuevas pasan por `copySensitive` (`VaultList.kt:418-446`, `VaultScreen.kt:476`, `VaultEntryScreen.kt:142`). El aviso A14 rechaza una copia pendiente si la bóveda se bloquea (`Clipboard.kt:45-57` → `copyOrAskNotice`). Queda el código 2FA en diferido (m-4). ✅/m-4
- **Leer al tocar:** `liveEntry` usa `Unlocked.entries`, que es `data.live` (`VaultStore.kt:61-62`), así que nunca toma una entrada de la papelera; también descarta lo personal (`EntryRow.kt:135-136`). ✅
- **Datos en pantalla tras bloquear:** **I-1** (`VaultScreen.kt:155`, `:198`, `:269`; `VaultList.kt:449`; `VaultOrganize.kt:185`). Revelación de información de bajo impacto, CWE-200 / MASVS-PRIVACY.
- **Texto plano frente a suplantación (R9 y R10):** `plainReportText` en título, línea secundaria, carpeta, etiquetas, menú, oferta, confirmaciones y «En grande · <título>». Las celdas de «Ver en grande» enseñan cada punto de código por separado, así que un control bidi no reordena (`BigPasswordScreen.kt:66-90`). ✅
- **Sin selección de texto del sistema** en las filas ni en «Ver en grande» (`NoCopyMenuTest`, +14). ✅
- **Acciones en bloque:** `changing(...)` excluye la papelera y lo personal y guarda una versión de cada entrada (`Selection.kt:97-106`); se guardan en un solo `modifyData` (`VaultStore.kt:1028-1049`). ✅
- **Cambio de bóveda:** `key(vault)` + `VaultScoped` (`AppNavHost.kt:178-179`) rehacen la navegación y sus ViewModels, `lockEpoch` es de toda la app (`AppContainer.kt:482`), y una escritura tardía va al `VaultStore` viejo, que está bloqueado. ✅
- Sin cambios de formato, de criptografía, de red, de permisos, de componentes exportados ni de `FLAG_SECURE`. Ninguna dependencia nueva.

## Revisión Ingeniero Móvil Senior

- **Ciclo de vida:** la oferta usa el ciclo de vida de la entrada de navegación y no el de la app (m-1, `EntryRow.kt:365`). El mensaje vive en un ámbito que sobrevive a `VaultGate` (I-1, `VaultScreen.kt:155`). Atrás termina la selección (`BackHandler(enabled = selecting)`). El foco del buscador se pide una vez por apertura (`focusedOpening` frente a `store.lockEpoch`). La duda «Al salir» es de producto.
- **Gestos:** `draggable` horizontal en el `Box`, apagado al seleccionar. El asa consume su arrastre en el pase principal antes que el padre. El 2FA tiene su `clickable` dentro del panel. La barra de selección para los toques con un `pointerInput` vacío: como el nodo existe, se lleva el toque y no lo comparte con lo de debajo. El conflicto con el gesto de Atrás del borde queda para el móvil.
- **Compose y rendimiento:** `now` como `State<Long>`, leído solo en `TotpBadge` (`EntryRow.kt:275`). Las filas de nivel `CARD` no usan `RenderEffect`. `RowActions` se recuerda con `remember(store, twoStep, list)` y los valores que cambian van por `rememberUpdatedState` (`VaultList.kt:~386-392`). Los parámetros `List`/`Map` no son estables y recomponen las filas con su padre, como antes, así que no es una regresión.
- **Pulsar y animaciones:** m-3 (`Components.kt:142`, `:148`, `:157`). El brillo sigue `panelRipple(animationsOn)` y el `LocalRippleConfiguration` cubre también los botones de dentro.
- **Accesibilidad:** `customActions`, `onLongClickLabel = «Seleccionar»`, `semantics.selected`, `liveRegion` en el contador, el «⋮» con el título, el 2FA con los segundos y «Toca para copiar», y la casilla y los iconos decorativos callados. Correcto.
- **Contraste:** las filas y lo que destapa el deslizar se miden en todas las paletas y estilos (`GlassContrastTest.theRowsOfTheListRead`). El rojo del círculo es `error`, medido como texto (`ThemeSurfaces.kt:80`).
- **Pruebas:** `SelectionTest`, `TwoStepCopyTest`, `BigPasswordTest`, `EntryRowTest`, `TrashTest.eachMoveHasItsOwnMark` y `OrganizeStoreTest.selectionFavoriteAndTagSaveOnce` están bien enfocados. Falta fijar el cableado de focus 4 (m-5). TDD: tres clases se escribieron junto con su código; está declarado en el informe.
- **Lint:** sin avisos nuevos en código (riesgo 4), con el límite dicho.

## Textos para el Abogado Digital (revisión final)

Nuevos, en `vault/ListTexts.kt` salvo que se diga otra cosa:
- «Más opciones de «<título>»» y «Seleccionar». («Más opciones», `ROW_MENU`, no se usa: m-6, quitar.)
- «Copiar usuario», «Copiar contraseña», «Copiar 2FA», «Editar».
- «Usuario copiado», «Contraseña copiada», «Código copiado», «Copiado el código siguiente», «Toca para copiar», «Quedan N s».
- «¿Copiar también la contraseña de «<título>»?»
- «1 seleccionada» / «N seleccionadas», «Seleccionar todo», «Terminar la selección», «Mover a carpeta», «Etiquetar», «Favorita», «Quitar favorita», «Papelera», «Etiqueta», «Etiquetar 1 entrada» / «Etiquetar N entradas».
- «1 entrada movida» / «N entradas movidas» + « a «<carpeta>»» o « fuera de las carpetas»; «Ya estaban ahí».
- «Ya la tenían (o no caben más de 10 etiquetas).», ««<etiqueta>» puesta en 1 entrada» / «… en N entradas».
- «Sin cambios», «1 entrada marcada como favorita» / «N entradas marcadas como favoritas», «1 entrada ya no es favorita» / «N entradas ya no son favoritas».
- «Crear la primera», «Importar» (su menú: «Importar copia», que es un literal en `VaultList.kt:708`, y «Importar de otra app»).
- «Ver en grande», «En grande · <título>», «Letras», «Cifras», «Símbolos», «espacio» (TalkBack).

Cambiados:
- `vault/OrganizeTexts.kt`: «Tu bóveda está vacía. Pulsa «Añadir».» → «Tu bóveda está vacía.»
- `vault/TrashTexts.kt`: «Puedes restaurarlas desde la papelera durante 30 días.» (varias entradas).

Reutilizados: «¿Mover «<título>» a la papelera?», «¿Mover N entradas a la papelera?», «Puedes restaurarla desde la papelera durante 30 días.», «Mover a la papelera», «Deshacer», «Duplicar», «Mover a…», «Se ha creado «<título>», sin adjuntos ni historial.»

Para la veracidad: la oferta en dos pasos no sale con el bloqueo «Al salir» (duda 1). Si algún texto de ayuda o de la ficha de Play describe «Copiar usuario y luego contraseña», tiene que decirlo o no prometerla en ese caso.
