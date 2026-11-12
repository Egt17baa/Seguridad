# Revisión de la tarea 3 de Clavero 2.0: papelera, deshacer e historial

Rango `3b32c65..23e3887` (`7bf3121` es el commit del registro del controlador). Revisado sobre el diff; fuera de él, solo comprobaciones puntuales de riesgos con nombre: `VaultStore.kt` (`collectGarbage`, `commit`, `modifyData`, `checkCanHold`, `applyImport`), `Vault.kt` (`write`, `afterMasterUnlock`), `VaultLimits.kt` (`fittedForSave`) y el guardado del editor (`VaultEntryScreen.kt:297`). No he ejecutado tests.

Leídos: `CLAUDE.md`, `experto-seguridad-cripto/SKILL.md` y `references/auditoria.md` (no hay ninguna referencia específica sobre el borrado ni la remanencia; apliqué la regla de honestidad epistémica de la skill), `auditoria-seguridad-movil` y `desarrollo-movil-senior`.

### Spec Compliance

**Cumple:**
- API del brief: `moveToTrash`, `restore`, `deleteForever` y `emptyTrash` (`VaultStore.kt`, hunk `@@ -891`). `deleteForever` y `emptyTrash` solo quitan lo que está en la papelera (`removeFromTrash` filtra `deletedAt != null`). `delete(id)` pasa por `moveToTrash`, salvo las entradas personales (decisión 2, ruling T3 duda 2, aceptada).
- `purgedTrash` (`Trash.kt:35`) siempre apunta `lastUnlockAt`. Lleva un segundo parámetro `clockMark`, una desviación de firma razonable para que la función siga siendo pura.
- Historial: `withVersion`, `restoreVersion`, `clearHistory` y `clearAllHistory` (`EntryHistory.kt:34`, `VaultStore.kt` `@@ -891`). Las versiones van sin adjuntos ni historial (`asVersion`), y `lastUsedAt` y `updatedAt` no cuentan como cambio (`comparable`).
- `UndoTrash` va en `AppContainer`, uno por `Slot` (`AppContainer.kt` `@@ -381`), y el *getter* da el de la bóveda activa. Vale 5 s, con un reloj monotónico y la `lockEpoch`. Cambiar de bóveda lo olvida (test `undoFollowsTheActiveVault`).
- La papelera queda fuera de la lista y de la búsqueda (`Unlocked.entries = data.live`), de «Salud» (`Health.kt:30-45`) y de los duplicados y el presupuesto de etiquetas de la importación (`MergePlan.kt:117`, `liveTags`). Sí va en las copias (`backupKeepsTrash`), y la importación de una copia no pasa `prepare`, así que conserva su papelera.
- El paso de v3 a v4 está probado con una entrada grande: `upgradeOfLargeEntryKeepsNewestAndEveryOldPassword`.
- Pantallas `TrashScreen` y `EntryHistoryScreen`, con confirmaciones, O-2 a la vista y el resto bajo `MoreInfo`.

**⚠️ No cumple o queda a medias:**
- ⚠️ **Ruling T3, duda 1:** la revisión tenía que comprobar que un proceso nuevo no deja la papelera sin vaciar para siempre. **No se cumple** para quien abre la bóveda con más de 2 días entre aperturas (ver I-1).
- ⚠️ **Requisito del controlador «no old password silently disappears from the card»:** solo se cumple para las contraseñas heredadas de la 1.4.3. Las que cambian en la 2.0 desaparecen de la ficha cuando el tope recorta su versión (ver I-2).
- ⚠️ **Diseño, «Todo borrado manda a la papelera»:** la identidad y los contactos se siguen borrando del todo. Lo acepta el ruling T3, duda 2. Su texto «no hay forma de recuperarla» (`DELETE_IDENTITY_CONFIRMATION`) choca con el espíritu de O-2 («nunca irrecuperable»), porque las copias ya hechas sí la conservan. Va al Abogado en la revisión final, no a esta tarea.
- ⚠️ `clearAllHistory` no tiene botón (el brief no lo pide; tarea 10). Deslizar, la selección y las carpetas son de tareas posteriores.

**Extra:** `UndoTrash.offer`/`canUndo`, `trashClockMark` en `AppContainer.appStarted`, y `upsert`/`addAll`, que rechazan o se saltan los ids de la papelera. Todo justificado.

### Strengths

- **Invariantes bien puestas en la tienda.** Todo pasa por `modifyData`, con el mutex, una sola lectura y `fittedForSave`. `upsert` no tapa nunca una entrada de la papelera con su mismo id. `addAll` reserva los ids de la papelera. `restore` no crea una segunda identidad desde una copia hostil.
- **Recolección de adjuntos correcta y acotada.** `collectGarbage` (`VaultStore.kt:377-380`) toma los adjuntos de *todas* las entradas, también las de la papelera, de su propio `AttachmentStore`, el de cada bóveda. Así nunca borra el archivo de una entrada viva ni el de otra bóveda, y `deleteForever` y `emptyTrash` se llevan solo los suyos (`deleteForeverRemovesAttachments`).
- **A11 queda exactamente igual.** `appStarted` (`AppContainer.kt` `@@ -242`) es el bucle de `clockSeen` y el `saveClock()` que antes hacía `rememberClock` al arrancar. `clockUsed` es aparte, solo en memoria, y se olvida con la bóveda. Las marcas por bóveda de la tarea 2 no se tocan.
- **La marca se lee antes de que el propio desbloqueo diga la hora** (`unlockLocked` y `unlockWithKeyLocked`: `at` y `mark` antes de `derive`/`opened`), y en la duda no se borra nada.
- **Guardado de contabilidad con la huella.** Va con `mustSave = false` y `saveBookkeeping`: un disco lleno no cierra la bóveda y no provoca copias automáticas. Un `CryptoFailure` sigue conservando la huella, porque quien llama limpia la clave.
- **Textos O-2 correctos y fijados.** Ni «irrecuperable» ni «borrado seguro» (`trashTextsSayWhatStays`). «Las copias ya hechas lo conservan» va a la vista en las tres confirmaciones y en la papelera. Los títulos van con `plainReportText`, fijados en `PlatformSourcesTest`.
- **El historial no enseña secretos sin pedirlos.** Contraseña, frase, notas de una nota, clave 2FA y campos `HIDDEN` están ocultos, y se copian con `copySensitive`. Las versiones están plegadas por defecto. No hay nada en `rememberSaveable` ni en un `Bundle`.
- **TDD honesto,** con mutaciones documentadas para los tests que compilaban sin la API nueva.

### Issues

#### Critical

Ninguno.

#### Important

**I-1. La papelera no se vacía nunca para quien abre la bóveda con más de 2 días entre aperturas** (`Trash.kt:36`, `AppContainer.kt` `trashClockMark`, en el hunk `@@ -242`).
- **Qué pasa.** La regla salta el vaciado cuando `now - lastUnlockAt > 2 días` y `now - clockMark > 2 días`. `clockMark` es `clockUsed`, que solo sube con los usos de este proceso.
  - Si el proceso es nuevo (Android lo mata con frecuencia), `clockMark = 0`.
  - Si el proceso sobrevive, `clockMark` es el último uso, que en este caso también tiene más de 2 días.
  - En los dos casos, una apertura con más de 2 días desde la anterior siempre cuenta como «salto». `purgedTrash` apunta `lastUnlockAt = now` también al saltar, así que la apertura siguiente, otra vez con más de 2 días de hueco, vuelve a saltar. Quien abre la bóveda cada 3 días o más (cada semana, por ejemplo) nunca ve vaciarse la papelera.
  - Ningún test lo cubre: `clockJumpDoesNotEmptyTrash` y `appStartIsNotATrashWitness` solo prueban el salto, no la apertura siguiente con huecos largos.
- **Por qué importa.** Es justo lo que el ruling T3 (duda 1) pedía comprobar. Además vuelve falsos los textos de O-2:
  - `TRASH_SHORT`: «Al abrir la bóveda, lo que lleve más de 30 días se quita solo.»;
  - `TRASH_MORE`: «…se hace en la siguiente apertura.»;
  - `trashLeavesIn`: «Se quita al abrir la bóveda»;
  - y la política que vendrá en la tarea 12.

  Lo borrado se queda en la bóveda y en todas las copias nuevas por tiempo indefinido, contra lo que se dice al usuario. Es un fallo de minimización y de veracidad, no de confidencialidad, porque todo sigue cifrado.
- **Arreglo propuesto: saltar como mucho una vez seguida.**
  - Un campo en `VaultData`, por ejemplo `trashPurgeDeferredAt: Long = 0`, cifrado y autenticado. La v4 aún no se ha publicado y el campo lleva valor por defecto.
  - `purgedTrash` lo pone a `now` cuando salta. Si la apertura anterior saltó (`trashPurgeDeferredAt == lastUnlockAt`), vacía sin mirar la regla del salto.
  - Así se cumplen la letra del diseño (un salto no vacía en *ese* desbloqueo) y la del texto («se hace en la siguiente apertura»). Un reloj adelantado y luego corregido vacía con la hora buena, y el límite de la duda 3 no empeora.
  - Test: tres desbloqueos separados por 3 días, cada uno con `trashClockMark = 0` (proceso nuevo); el segundo vacía.
  - Alternativa más fina: comparar el delta del reloj de pared con el de `elapsedRealtime` dentro del mismo arranque, que sería la señal de verdad. Si se quiere, se añade encima del «una vez como mucho».

**I-2. Las contraseñas que cambian en la 2.0 desaparecen de la ficha cuando el tope recorta su versión** (`EntryHistory.kt:45` `oldPasswords`, `History.kt`; `withNewPassword` ya no existe).
- **Qué pasa.** En la 2.0, una contraseña antigua solo vive en la versión que la guardó. `fittedHistory` quita las versiones más viejas al pasar de 20 versiones o de 64 KiB. Con eso, la contraseña sale de «Contraseñas anteriores» sin aviso, aunque el cambio que la recortó fuera de otro campo.
  - Ejemplo: cambiar la contraseña una vez y editar las notas 20 veces.
  - Con una entrada grande pasa enseguida. `capAt64KiB`, con notas de 20 KiB, ya deja solo de 1 a 3 versiones, así que p0 y p1 se pierden de la ficha. Con notas de 40 KiB (el caso del test de M-4), la contraseña anterior se va en la siguiente edición de las notas.
  - En la 1.4.3, `passwordHistory` guardaba las 5 últimas pasara lo que pasara con los demás campos. Es una regresión para las entradas grandes y para las que se editan mucho.
  - La conciliación solo protege la lista heredada. El test de M-4 solo cubre el paso de v3 a v4, no un cambio hecho después.
- **Por qué importa.** El *dispatch* exige que ninguna contraseña antigua desaparezca de la ficha en silencio (M-4 de la tarea 1, que pide conciliar las dos listas). Perder una contraseña antigua es perder un dato del usuario, por ejemplo para entrar en un sitio que no aceptó el cambio.
- **Arreglo propuesto, cualquiera de los dos:**
  - (a) Seguir escribiendo `passwordHistory`, como hacía `withNewPassword` (LOGIN, contraseña no vacía y distinta, `take(MAX_PASSWORD_HISTORY)`), en `upsert` y en `replaceWith`. `oldPasswords` ya quita los repetidos y «Borrar historial» ya vacía las dos listas.
  - (b) Que `fittedHistory`/`withVersion`, al quitar una versión cuya contraseña desaparecería de `oldPasswords()`, la pase a `passwordHistory`, con el mismo tope de 5.

  La (a) es más simple. El KDoc de `passwordHistory` («never written again») y el de `History.kt` cambiarían con ella.
  - Test: una entrada con notas de 40 KiB, cambiar la contraseña A→B y después editar las notas: `oldPasswords()` sigue teniendo A. Y 21 ediciones de las notas después de un cambio de contraseña: sigue estando.

#### Minor

- **M-1. Aviso de «Deshacer» viejo después de cambiar de bóveda** (`Trash.kt:47-84`, `VaultScreen.kt` `@@ -168`).
  - Qué pasa: si se cambia de bóveda en los 5 s siguientes a mover algo a la papelera, el `LaunchedEffect` de la lista se cancela sin llamar a `forget()`. `offer` se queda en N. Al volver a esa bóveda y abrirla, la lista enseña «Movida a la papelera · Deshacer» durante 5 s, y pulsar no hace nada, porque `valid()` falla por la época.
  - Arreglo: que `offer` se ponga a 0 cuando `valid()` falla por la época, o un colector de `store.state` en `UndoTrash` que haga `forget()` en `Locked`. Test: mover, cambiar de bóveda y volver: `offer.value == 0`.
- **M-2. `restoreVersion` puede convertir una entrada en IDENTITY o CONTACT con una versión hostil** (`VaultStore.kt`, `restoreVersion`).
  - Qué pasa: una copia de otro sitio puede traer, en el historial de un LOGIN, una versión de tipo personal. `checkCanHold` solo impide una segunda identidad. Además, el error (`ONLY_ONE_IDENTITY`, `UPGRADE_PENDING`) sale en la pantalla como `TRASH_SAVE_FAILED` («¿Queda espacio en el móvil?»), un texto que no dice la verdad sobre lo que pasó.
  - Arreglo: devolver `false` si `version.type.isPersonal != current.type.isPersonal` y distinguir el `IllegalStateException` en `EntryHistoryScreen`. Decisión (c), lo importado es dato hostil: el riesgo es bajo, porque pide importar una copia con su contraseña, pero es una invariante.
- **M-3. Editor sobre una entrada que ya está en la papelera** (`VaultEntryScreen.kt:297-300`).
  - Qué pasa: `upsert` devuelve `false` y el editor dice «La bóveda se bloqueó: no se ha guardado», que es falso.
  - Arreglo: un resultado distinto (o un texto «Está en la papelera: restáurala para editarla») y salir del editor. Hoy cuesta llegar a este caso; la tarea 6 (selección y deslizar) lo hará más fácil.
- **M-4. Pantalla de versiones con solo la lista heredada** (`VaultEntryScreen.kt` `@@ -171` y `EntryHistoryScreen.kt`).
  - Qué pasa: con `history` vacío y `passwordHistory` no vacío, la ficha enseña «Versiones (0)». La pantalla dice «No hay versiones anteriores.» y aun así ofrece «Borrar historial», que borra unas contraseñas que esa pantalla no enseña.
  - Arreglo: contar `oldPasswords()` en el botón, o enseñar las contraseñas heredadas en la pantalla de versiones.
- **M-5. Coste del desbloqueo con la huella en el tope** (`VaultStore.kt` `unlockWithKeyLocked`).
  - Qué pasa: ahora descifra y analiza dos veces (`VaultCrypto.read` aquí y otra vez en `opened`), y cifra y escribe el archivo entero en cada desbloqueo, aunque no haya nada que vaciar. Con 24 MiB, son segundos y escritura en la flash en cada apertura.
  - Arreglo: pasar los datos ya leídos a `opened` y, como mínimo, medirlo en M-6 (tareas 4 y final). Es aceptable si se mide.
- **M-6. Una entrada borrada con el reloj adelantado tarda en irse lo que duró el adelanto** (`Trash.kt:37`).
  - Qué pasa: queda con `deletedAt` en el futuro cuando el reloj se corrige. No se va hasta que la hora real alcanza esa fecha más 30 días (con un salto de 400 días, «Se quita en 430 días»). No es para siempre, pero el texto «30 días» no se cumple.
  - Arreglo: en `purgedTrash`, una entrada con `deletedAt > now` pasa a `deletedAt = now`, y cuenta 30 días desde la hora buena.
- **M-7. `restoreVersion` busca por `savedAt`** (`VaultStore.kt` `restoreVersion`).
  - Qué pasa: dos versiones del mismo milisegundo (una copia hostil, o el reloj atrás) restauran la primera, que puede no ser la que se pulsó.
  - Arreglo: pasar el índice además de `savedAt`, o comprobar que la versión es igual a la que enseñaba la pantalla.

### Assessment

**Task quality:** Needs fixes

**Reasoning:** la estructura está bien:
- todas las operaciones van bajo el mutex;
- la recolección de adjuntos va por bóveda y cuenta la papelera;
- A11 no cambia;
- la importación y «Salud» filtran la papelera;
- los textos O-2 son veraces, salvo en el punto de I-1;
- los secretos del historial están ocultos.

Quedan dos promesas explícitas del encargo sin cumplir:
- I-1: la papelera se queda sin vaciar para siempre con aperturas espaciadas, que es lo que el ruling T3 mandaba comprobar, y eso vuelve falsos los textos a la vista;
- I-2: las contraseñas antiguas de la 2.0 se pierden de la ficha cuando el tope recorta su versión, que es el requisito de M-4.

Los dos tienen un arreglo pequeño y un test claro. Los menores entran en la misma ronda (`CLAUDE.md`, «Detalles menores: se arreglan todos»).

### Revisión experto-seguridad-cripto

- **Modelo de amenaza:**
  - un reloj de pared que no es de fiar, adelantado sin querer o a propósito (pérdida de datos o retención de más);
  - una copia o un archivo importado con una papelera o un historial hostiles (decisión (c));
  - la remanencia después de «borrar».

  La confidencialidad no cambia: la papelera, el historial y `lastUnlockAt` van dentro del blob v4, cifrado y autenticado (`Vault.kt` `write`, `seal` con `dataAad`). Nadie puede poner `lastUnlockAt` «reciente» sin la clave de datos.
- **Primitivas:** ninguna nueva. Ni `Log`, ni red, ni permisos, ni dependencias en el diff.
- **Remanencia y textos (O-2):**
  - Los textos dicen «se quita de la bóveda» y que las copias lo conservan (`TrashTexts.kt`), sin prometer un borrado físico. Correcto: `writeTextAtomically` renombra, la flash puede conservar bloques viejos, y las `String` de Kotlin no se pueden borrar de la memoria.
  - La excepción es I-1: «se quita solo al abrir la bóveda pasados 30 días» es falso para quien abre la bóveda con aperturas espaciadas, y la regla de veracidad del proyecto lo convierte en un hallazgo de cumplimiento, no solo de producto.
- **Comprobar y usar a la vez (decisión (b)):**
  - `trash`, `restore`, `removeFromTrash`, `restoreVersion` y `clearHistory` deciden y escriben dentro de un solo `modifyData`;
  - `UndoTrash` valida la época antes de restaurar (`Trash.kt:57`);
  - `upsert` rechaza un id de la papelera (`VaultStore.kt` `@@ -741`).
- **Lo importado es dato hostil (decisión (c)):**
  - `restore` no admite una segunda identidad (`restoreNeverMakesASecondIdentity`);
  - `restoreVersion` sí admite una versión de tipo personal (M-2, bajo).
  - Los topes del historial se mantienen (`fittedHistory`, y `checkLimits` en lo que cambia).
- **Regla del reloj:** falla del lado conservador (nunca borra de más), pero sin un límite, y eso es I-1. La fuente fiable que falta es `elapsedRealtime` dentro del mismo arranque. Lo recomendado es el «salta una vez como mucho», más simple y verificable.
- **Hallazgos de seguridad confirmados:** ninguno explotable. I-1 es de minimización y veracidad (Important); M-2 es una invariante con datos hostiles (Minor, porque pide importar una copia con su contraseña).

### Revisión Ingeniero Móvil Senior

- **Concurrencia:**
  - todo cambio va en `modifyData`, con el mutex y `compute`, y `commit` es `NonCancellable`;
  - los dos desbloqueos leen la hora y la marca antes de `derive` y de `opened` (`VaultStore.kt` `@@ -515` y `@@ -593`);
  - `clockUsed` va bajo `clockLock`;
  - `UndoTrash` usa `@Volatile` y un `StateFlow`.
- **Ciclo de vida y varias bóvedas:**
  - un `UndoTrash` por `Slot`, y las pantallas nuevas dentro de `VaultNav(container, vault)`;
  - las dos pantallas salen con `LaunchedEffect { onBack() }` cuando la bóveda se bloquea o la entrada deja de estar viva;
  - queda el aviso viejo de M-1.
- **Compose:**
  - `LazyColumn` con claves (`it.id`; `"${savedAt}-$i"`);
  - la cuenta atrás de la papelera, una vez por minuto;
  - nada secreto en `rememberSaveable`;
  - `FilterObscuredTouches` en los diálogos.
  - La ficha recompone cada segundo (el *ticker* del TOTP) y llama a `oldPasswords()` en cada pasada: O(20), aceptable.
- **Navegación:** `backOnce(nav)` en las dos rutas nuevas y el id codificado (`Uri.encode`). La ficha no hace un segundo `onBack` después de mover (`PlatformSourcesTest`).
- **Rendimiento:**
  - `withVersion` hace dos `copy` y compara, y `fittedHistory` mide como mucho 64 KiB;
  - el desbloqueo con la huella ahora lee dos veces y escribe siempre (M-5, para la medición de M-6 en el tope de 24 MiB).
- **Snackbar:** `withTimeoutOrNull(UNDO_TRASH_MS)` cuenta también la espera en la cola de `SnackbarHostState` (otro aviso delante acorta el tiempo útil). `UndoTrash` manda de todos modos: aceptable.
- **Accesibilidad:** la alternativa a los 5 s es «Restaurar» en la papelera, y la confirmación lo dice; los botones son de texto.
