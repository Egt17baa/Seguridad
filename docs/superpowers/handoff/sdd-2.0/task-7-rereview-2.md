# Re-revisión de la tarea 7 de la 2.0, ronda 2

Base de los arreglos `e7cd9ae` y cabeza `27e817a`. Diff revisado: `review-e7cd9ae..27e817a.diff`, sin los cambios de `docs/superpowers/handoff`.

La revisión es de solo lectura, sin subagentes y sin repetir la suite ni ningún test: no surgió ninguna duda concreta que lo pidiera.

Lo que se revisa:
- N-1 (de `task-7-rereview-1.md`);
- la comprobación de seguridad «un guardado en cola puede caer en otra bóveda o después del bloqueo» (foco 2 de T2);
- las tres comprobaciones nombradas.

### Finding Verdicts

**N-1 (una pestaña tocada durante un guardado perdía la entrada si el guardado fallaba): ADDRESSED.**
- `vault/EntryForm.kt:491`: con `saving`, `askToLeave(go)` guarda el destino (`pendingLeave = go`) y no lo ejecuta. Una pestaña ya no lleva a `left(false)`, que llamaba a `clear()`, así que el formulario sigue cargado (`loaded = true`) mientras dura el guardado.
- Si el guardado termina bien (`ui/screens/VaultEntryScreen.kt:430-434`), `takeLeave()` da el destino una sola vez, `finish()` y la pestaña se abre. Es el mismo camino que «Guardar» desde el diálogo, ya revisado en la ronda 1.
- Si falla (`:435-447`), `false` o una excepción escriben el error, porque `form.loaded` sigue en `true`, y `finally` descarta el destino con `takeLeave()` (`:446`). El usuario se queda en el formulario, con lo escrito y el error a la vista. Era lo pedido.
- Un bloqueo durante la espera llama a `clear()` y `pendingLeave = null` (`EntryForm.kt:554` y siguientes): no se navega. Un giro llama a `dropLeave()` (`:534-539` y `:601-603`), así que no queda ningún `NavController` viejo en el ViewModel.
- «Atrás» con un guardado en marcha sigue sin hacer nada (`askToLeave(null)`) y no borra una pestaña ya pedida. Dos pestañas seguidas: gana la última, lo razonable.
- Sin cambios (`hasUnsavedChanges` falso), `LeaveGuard.leave` (`EntryForm.kt:623-626`) navega al momento, como antes. No se pierde nada, porque no había nada escrito.
- Test: `EntryEditSessionTest.duringASaveNothingIsAsked` comprueba tres cosas:
  - la pestaña no se abre mientras dura el guardado;
  - el título sigue ahí;
  - `takeLeave()` devuelve el destino una vez y después `null`.

  El test es de la sesión. El camino del ViewModel (`finally` y `form.loaded`) no tiene test propio porque `EntryEditViewModel` es privado, pero se lee directamente en `VaultEntryScreen.kt:422-447`.

**Comprobación de seguridad: un guardado que espera en la cola.**

- **«No puede caer en otra bóveda»: CONFIRMADO.**
  - Cada bóveda tiene su `Slot` con su propio `VaultStore`, su archivo y su `mutex` (`AppContainer.kt:382-387`; `VaultStore.kt:266`). La puerta `active = { activeId == id }` (`AppContainer.kt:413`) impide abrir cualquier bóveda que no sea la activa (`VaultStore.kt:466`).
  - El editor guarda la instancia con la que se creó (`VaultEntryScreen.kt:384` y `:583`), así que `modifyData` solo escribe en el `vault` de esa instancia (`VaultStore.kt:768`).
  - `switchTo` (`AppContainer.kt:496-511`) hace, en este orden:
    - cierra la puerta (`activeId = id`, `:502`);
    - bloquea la bóveda vieja (`:503`), lo que sube `epoch`, borra `dek` y pone `Locked`;
    - espera a su mutex (`awaitIdle`, `:506`).
  - Un guardado que esperaba en ese mutex entra con la vieja ya bloqueada y da `false`. Ocurre sin `sinceLock`, solo por `dek == null` o por no estar `Unlocked` (`VaultStore.kt:773-774`).
  - Además, la navegación entera y sus ViewModels se rehacen con `key(vault)` (`ui/AppNavHost.kt:158-182`) y se sueltan con `scopes.drop` (`:149`). Así se cancela `viewModelScope` en la bóveda vieja.
- **«Después de un bloqueo, en la misma bóveda, sí pasaba»: el arreglo es correcto, pero en la app real el caso no se produce.**
  - `EntryEditViewModel.save` corre en `viewModelScope`, que es `Dispatchers.Main.immediate`. `upsert` va derecho a `modifyData`, que empieza con `mutex.withLock` (`VaultStore.kt:764` y `:810`), así que la petición entra en la cola del mutex dentro del mismo toque.
  - El `Mutex` de kotlinx-coroutines es justo, según su documentación («lock is granted in first come, first served order»; versión 1.10.2, `gradle/libs.versions.toml:12`). No he podido abrir las fuentes aquí.
  - Por eso cualquier `unlock` o `unlockWithKey` posterior al bloqueo (`VaultStore.kt:525` y `:618`) se pone en la cola detrás del guardado. El guardado entra antes, con la bóveda `Locked`, y da `false`.
  - El test RED (parte 1 de `aSaveWaitingAcrossALockOrASwitchLandsNowhere`) llama a `upsert` con una época vieja después de volver a abrir. Así prueba la comprobación, pero no que la app pueda producir ese orden (ver Info-2).
  - Aun así, la comprobación es una buena defensa en profundidad: deja de depender de que el mutex sea justo y de que el *dispatcher* sea inmediato. Además, hace cierto para el editor el KDoc de `epoch` (`VaultStore.kt:315-319`). Se acepta.

**Comprobación 3 (valor por defecto de `sinceLock`): SEGURO.**
- `sinceLock: Int? = null` en `modifyData` (`VaultStore.kt:762`) y en `upsert` (`:810`). La comprobación es `if (sinceLock != null && since != sinceLock)` (`:772`), así que con `null` no cambia nada. Todo llamador que no lo pasa se comporta igual que en `e7cd9ae`.
- El editor lee `store.lockEpoch` (`VaultStore.kt:323`), la época de su propia bóveda, y no `AppContainer.lockEpoch`, que es la de toda la app (`AppContainer.kt:482`) y sube con cada cambio de bóveda. `epoch` solo cambia en `lock()` (`VaultStore.kt:689`, el único `epoch++`).
- Por eso el editor solo puede ser rechazado de más si hubo un bloqueo real de esa bóveda entre el «Guardar» y la entrada al mutex. En ese caso, el rechazo es justo lo que se quiere.
- Con la bóveda abierta otra vez y el formulario cargado de nuevo, el rechazo da `SAVE_LOCKED` (`TrashTexts.kt:109-110`), un texto que dice la verdad. Con el formulario borrado por el bloqueo (`loaded = false`), no sale nada.

### Callers of upsert/modifyData (table)

Pregunta de cada fila: entre el toque del usuario y `mutex.withLock` (`VaultStore.kt:764`), ¿hay alguna suspensión?
- **Sin suspensión:** la única protección frente a «se pide antes del bloqueo y se escribe tras volver a abrir» es el orden justo del mutex, y la petición encuentra la bóveda bloqueada.
- **Con suspensión:** la protección es que el ámbito de la composición se cancela cuando la pantalla deja de mostrarse al bloquear.

En la tabla, «Composición» es `rememberCoroutineScope`, que se cancela al salir de la pantalla; `viewModelScope` no se cancela con un bloqueo, solo con un cambio de bóveda.

| Llamador (archivo:línea) | Operación del almacén | Ámbito | ¿Suspensión antes del mutex? | ¿Se escribe tras volver a abrir? | Gravedad |
|---|---|---|---|---|---|
| Editor «Guardar» (`VaultEntryScreen.kt:430`) | `upsert(entry, sinceLock)` | `viewModelScope` | No | No: por el orden del mutex y, ahora, por `sinceLock` (`VaultStore.kt:772`) | — |
| Editor, lote de Google Authenticator (`VaultEntryScreen.kt:466`) | `addAll` | `viewModelScope` (no se cancela con el bloqueo) | No | No, por el orden del mutex. Sin época propia | Info (Info-1) |
| Notas de «PIN y códigos» (`CodesScreen.kt:187`) | `upsert` | `viewModelScope` (no se cancela con el bloqueo) | No | No, por el orden del mutex. Cuando el usuario pide guardar con la bóveda cerrada, la nota queda pendiente hasta que la abra (`CodesSession.kt:192-198`, `CodesScreen.kt:286-288`): es así por diseño, se lo dice la pantalla («Desbloquea para guardar») y puede cancelarlo | — |
| Añadir contacto (`ContactAddScreen.kt:233-236`) | `upsert` | Composición | Sí: un salto a `Dispatchers.Default` para construir la entrada, corto y de CPU | No: el salto dura milisegundos y volver a abrir pide la maestra o la huella. A comprobar en el móvil | — |
| Ficha de contacto (`ContactDetailScreen.kt:100` y `:172`) | `upsert` y `delete` | Composición | No | No | — |
| Identidad (`IdentityScreen.kt:131`, `:147`, `:265` y `:312`) | `upsert` y `delete` | Composición | No | No | — |
| Detalle: duplicar, mover y papelera (`VaultEntryScreen.kt:319`, `:341` y `:365`) | `duplicate`, `moveEntries` y `UndoTrash.moveToTrash` → `trash` | Composición | No | No | — |
| Detalle y lista: marcar como usada (`VaultEntryScreen.kt:219`, `VaultScreen.kt:494` y `VaultList.kt:409`) | `markUsed` (contabilidad: no es un cambio del usuario) | Composición | No | No. Aunque pasara, solo cambiaría `lastUsedAt` | — |
| Lista: mover, duplicar, favorita y papelera (`VaultList.kt:400`, `:451`, `:627`, `:652` y `:672`) | `moveEntries`, `duplicate`, `setFavorite` y `trash` | Composición | No | No | — |
| «Deshacer» de la papelera (`VaultScreen.kt:194`, que llama a `Trash.kt:99-103`) | `restore` | Composición (`LaunchedEffect`) | No: `valid()` comprueba la época (`Trash.kt:77-81`) y `restore` va directo | No: la época propia, el orden del mutex y `forget()` al bloquear (`Trash.kt:74`) | — |
| Organizar: carpetas, etiquetas, vistas y colores (`VaultOrganize.kt:147`, `:151`, `:157`, `:167`, `:175`, `:185`, `:193`, `:374`, `:409` y `:438`) | `createFolder`, `renameFolder`, `moveFolder`, `moveEntries`, `addTag`, `saveView`, `deleteFolder`, `deleteView` y `setTagColor` | Composición | No | No | — |
| Papelera (`TrashScreen.kt:119`, `:138` y `:155`) | `restore`, `deleteForever` y `emptyTrash` | Composición | No | No | — |
| Historial (`EntryHistoryScreen.kt:99` y `:124`) | `restoreVersion` y `clearHistory` | Composición | No | No | — |
| Quitar un adjunto (`VaultAttachments.kt:171`) | `removeAttachment` → `modify` | Composición | No | No | — |
| **Añadir un adjunto** (`VaultAttachments.kt:64-70`, que llama a `VaultStore.kt:1204-1217`) | `addAttachment` → `modify` | Composición | **Sí, sin límite de tiempo:** `openInputStream` y `attachments.read(input)` en IO antes del mutex, y un proveedor lento (un archivo en la nube) puede tardar mucho | En la práctica, no: al bloquear, `AttachmentsSection` deja de mostrarse (`VaultEntryScreen.kt:207` y `:581`), el ámbito se cancela y el `withContext(io)` lanza al volver, antes de `modify`. Si la lectura termina antes de la cancelación, la petición entra en la cola antes de cualquier desbloqueo y encuentra `Locked`. Es una protección implícita, sin época y sin test. A comprobar en el móvil | **Menor (M-A)** |
| Importar de otra app (`ImportScreen.kt:347` y `:367`) | `applyImport(..., sinceLock)` | Pantalla de importar | Sí (la copia de antes) | No: tiene su propio `sinceLock` y la revisión (`VaultStore.kt:921-924`; R66 y R16) | — |
| Renombrar la bóveda (`VaultPickerScreen.kt:319`) y el nombre al crearla (`AppContainer.kt:553`) | `rename` | Composición, o el propio flujo de crear | No | No | — |
| `clearAllHistory` (`VaultStore.kt:1183`) | `modifyData` | — | — | No tiene ningún llamador en `app/src/main` | — |

Los llamadores salen de buscar `.<nombre>(` y `::<nombre>` para cada operación de `VaultStore` que escribe a través de `modifyData` o `modify`, y de revisar `vaultActions` (`VaultBackup.kt`), que no escribe con `modifyData`. `changeMaster`, `importReplace` y `exportBackup` toman el mutex por su cuenta, no pasan por `modifyData` y quedan fuera de esta pregunta.

**M-A (Menor, teórico y anterior a esta ronda). `addAttachment` puede esperar mucho antes del mutex y no lleva época.**
- **Dónde:** `VaultStore.kt:1204-1207` (la lectura en IO antes de `modify`) y `VaultAttachments.kt:64-70`.
- **Qué pasa:** es el único llamador con una espera larga, sin límite, entre el toque y el mutex. Su única protección contra escribir en la bóveda después de volver a abrirla es que la composición se cancele al bloquear. No hay ninguna comprobación de la época ni ningún test que lo fije. El editor, que no puede caer en ese caso, ahora sí la tiene.
- **Impacto:** si la composición no se cancelara a tiempo (por un cambio futuro de la pantalla, o si se mueve a un ViewModel), el adjunto que eligió el usuario se cifraría y se guardaría en su entrada después de un bloqueo y de volver a abrir. Sería una escritura tardía, en contra de la decisión (b) de la 1.4.0, aunque en la misma bóveda y en la entrada que el usuario eligió. No hay fuga ni cruce de bóvedas.
- **Corrección propuesta**, del mismo tipo que la de esta ronda:
  - en `addAttachment`, `val sinceLock = lockEpoch` antes de leer;
  - pasarlo a `modify` y de ahí a `modifyData(sinceLock = …)`;
  - un test: bloquear y volver a abrir mientras dura la lectura, y comprobar que da `false` y que no queda ninguna referencia.

  Alternativa: dejarlo escrito como decisión del controlador, porque hoy no se produce.

### New Issues in the Fix Diff

No hay ninguno de gravedad Menor o mayor. Hay tres notas informativas:

- **Info-1 (opcional).** El KDoc de `epoch` (`VaultStore.kt:315-319`) dice: «Each one reads it when called, before waiting for the mutex: a lock while it waits counts too». Para `modifyData` sin `sinceLock` no es literal: lee `since = epoch` ya dentro del mutex (`:769`). En la práctica da igual, porque un bloqueo durante la espera deja `dek == null` y la petición da `false`.
  - Si se quiere cerrar el caso para todos los llamadores, basta con que el valor por defecto de `modifyData` sea la época leída al llamar, antes del mutex: `sinceLock: Int? = epoch`, evaluado antes de `mutex.withLock`.
  - No rechazaría de más: solo rechaza si hubo un bloqueo real, y en ese caso la bóveda estaría cerrada o se habría vuelto a abrir después.
  - Cubriría también `addAll` y las notas de «PIN y códigos», pero no M-A, cuya espera es anterior a la llamada.
- **Info-2 (calidad de los tests).**
  - La parte 1 de `aSaveWaitingAcrossALockOrASwitchLandsNowhere` (`AppContainerVaultsTest.kt:188` y siguientes) llama a `upsert` con una época vieja después de volver a abrir. Prueba la comprobación, pero no un orden que la app produzca.
  - La parte 2, el guardado en cola durante el cambio a B, pasaría igual sin `sinceLock`: A queda `Locked` y su puerta impide volver a abrirla mientras B es la activa. Es un buen test de regresión de «no cae en otra bóveda», no del arreglo.

  Ninguna de las dos cosas cambia los veredictos.
- **Info-3 (interfaz).** Mientras dura un guardado, tocar una pestaña no da ninguna señal: la pestaña se abre al terminar o no se abre si falla. El editor ya muestra que está guardando, y la espera normal es de milisegundos. Prueba en el móvil (tarea 11): tocar una pestaña durante un guardado.

### Out-of-Scope Observations

- La observación de la ronda 1 sobre «otra bóveda» queda cerrada. `switchTo` cierra la puerta, bloquea y espera al mutex de la bóveda vieja (`AppContainer.kt:502-506`). La navegación y los ViewModels se rehacen para la bóveda nueva (`AppNavHost.kt:158-182`).
- El guardado pendiente de «PIN y códigos» con la bóveda cerrada (`CodesSession.kt:192-198`) se escribe cuando se abre la bóveda. Es intencionado y lo dice la pantalla. Como el ViewModel es de la bóveda (`AppNavHost.kt:140-151`), un cambio de bóveda lo suelta y no se escribe en la otra.

### Verdict

**APROBADA.**
- N-1 está resuelto.
- La comprobación de seguridad está verificada: no puede caer en otra bóveda, y el arreglo para «después del bloqueo» es correcto y seguro con el valor por defecto `null`.
- Queda abierto **M-A (Menor, teórico y anterior a esta ronda)** en `addAttachment`. Se arregla con el mismo `sinceLock` o se deja como decisión escrita.
- Info-1 a Info-3 son opcionales.
- No hay ningún hallazgo Crítico ni Importante.

## Revisión experto-seguridad-cripto

- **Modelo de amenaza:** que un resultado tardío caiga en otra bóveda o después de un bloqueo (decisión (b) de la 1.4.0 y foco 2 de T2). Es una carrera interna de la app, no un ataque externo.
- **Otra bóveda:** no puede pasar, porque cada bóveda tiene su instancia, su mutex y una puerta `active()`:
  - `AppContainer.kt:382-387` y `:413`;
  - `VaultStore.kt:466` y `:764-774`;
  - `switchTo` en `AppContainer.kt:502-506`.
- **Después del bloqueo:** `VaultStore.kt:772` rechaza con la época del «Guardar», leída sin el mutex y `@Volatile` (`:320-323`). No hay ningún camino en el que el rechazo deje la clave sin borrar: el `return` está antes de `dek?.copyOf()` (`:773`). El valor por defecto `null` mantiene el comportamiento anterior.
- **Escrituras tardías:** ningún llamador escribe en la práctica tras volver a abrir. La garantía general descansa en que el `Mutex` es justo, según su documentación (1.10.2).
- **M-A:** es el único llamador con una espera sin límite antes del mutex (`VaultStore.kt:1205`) y sin época. Es Menor y teórico: la protección de hoy es la cancelación de la composición.
- **N-1:** es de disponibilidad, porque se perdían datos del usuario sin aviso. Queda resuelto sin exponer nada: `pendingLeave` es una llamada sin texto, y `clear()`, `dropLeave()` y `takeLeave()` la borran (`EntryForm.kt:531-539` y `:554`).
- En el diff no hay registros, `Bundle`, `rememberSaveable` ni secretos nuevos.

## Revisión Ingeniero Móvil Senior

- **Concurrencia:** `viewModelScope` (`Main.immediate`) hace que `upsert` entre en la cola del mutex dentro del mismo toque (`VaultEntryScreen.kt:428-430` y `VaultStore.kt:764`). `lockEpoch` se lee en el hilo principal antes de `launch` (`:427`), que es la época que vio el usuario.
- **Ciclo de vida:**
  - un giro durante el guardado descarta el destino (`EntryForm.kt:601-603` y `:534-539`), así que no queda ningún `NavController` viejo en el ViewModel;
  - salir de la pantalla de otra manera llama a `clear()`;
  - el ViewModel se suelta con el cambio de bóveda (`AppNavHost.kt:149`).
- **El orden «`takeLeave`, `finish()` y `go()»`** (`VaultEntryScreen.kt:432-434`) es el mismo que ya se revisó para el diálogo. `backOnce` impide quitar el editor dos veces.
- **M-A:** depende de cuándo se recompone la pantalla al bloquear. Con la app en segundo plano no hay fotogramas, pero se recompone antes de que el usuario pueda volver a abrir. A comprobar en el móvil si no se arregla: elegir un adjunto grande o de un proveedor lento, bloquear mientras se lee, volver a abrir y comprobar que no aparece.
- **Pruebas en el móvil (tarea 11):**
  - tocar una pestaña durante un guardado: se abre al terminar;
  - provocar un guardado que falle (por ejemplo, la bóveda llena): el formulario se queda, con el error.

## Textos para el Abogado Digital (revisión final)

Ninguno nuevo en esta ronda: el diff no añade ni cambia ningún texto de la interfaz. `SAVE_LOCKED` y `ENTRY_IN_TRASH` ya estaban y se siguen usando como antes.
