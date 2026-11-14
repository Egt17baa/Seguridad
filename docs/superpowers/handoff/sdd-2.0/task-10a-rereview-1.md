# Tarea 10, fase A: re-revisión 1 (ronda 1 de arreglos)

**Alcance.**
- De `6336099` a `b67d35b`. `26248be` solo añade el informe.
- Leídos:
  - el «Ruling (advisor, T10 fase A)» (`progress.md:195`);
  - la sección «Ronda 1» de `task-10a-report.md` y el diff completo;
  - el código que lo rodea: `unlockLocked` y `unlockWithKeyLocked`, `commit`, `AppContainer.switchTo`, `newVault`, `deleteVault` y `importAsNewVault`;
  - `SettingsStore.decideRenameNotice` y `SeguridadApp` en `a3ac942`.
- No he ejecutado Gradle: lo que digo de las mutaciones sale de leer el código.

## Veredicto

**Aprobada con dos menores (n-1 y n-2), que son de i-6.**
- m-1, m-3, m-5, m-6 y m-7 están bien resueltos.
- i-6 cierra lo importante: ni la huella ni la maestra tardías escriben ya en una bóveda que no se enseña.
- Quedan dos menores:
  - la ventana que queda abierta en la huella no es de microsegundos, como dice R1-2;
  - la segunda comprobación de la maestra, la de después de Argon2id, no tiene test.
- Por `CLAUDE.md` («Detalles menores: se arreglan todos»), los dos son de una línea y su test, y pueden ir en una ronda 2 corta o al principio de la fase B, según decida el controlador.
- No hay regresiones.

## Issues

### Critical

Ninguno.

### Important

Ninguno.

### Minor

**n-1. En la huella, la ventana de R1-2 dura lo que tarda en cifrarse toda la bóveda, no microsegundos** (`VaultStore.kt:669`, `:685-695`).
- **La maestra:** comprueba `active()` después de Argon2id (`:584`). Entre esa comprobación y `commit` solo quedan `onClockReset` (`:590`, una escritura de `settings.json`, de milisegundos) y la propia escritura.
- **La huella:** comprueba `active()` una sola vez, al principio (`:669`). Después hace todo esto antes de `commit` (`:695`):
  - `VaultCrypto.opens` y `biometricRefusal`;
  - `VaultCrypto.authenticated`, que pasa AES-GCM por todas las entradas;
  - `VaultCrypto.read(...).purgedTrash(...)`;
  - `VaultCrypto.write`, que vuelve a cifrarla entera.

  Con una bóveda cerca de 24 MiB, eso puede durar cientos de milisegundos en un móvil.
- **Escenario:** la huella de A responde, empieza el descifrado y en ese momento el usuario cambia a B (`switchTo`, `AppContainer.kt:543`; `activeId` es `@Volatile`, `:480`, y no toma el mutex del almacén). El cambio no espera a que acabe el descifrado: `commit` guarda el archivo de A (`saveBookkeeping`, `:462`) y después `opened()` lo rechaza (`:497`).
- **Arreglo:** una segunda comprobación justo antes de `commit`, igual que en la maestra:
  ```kotlin
  val next = withContext(compute) { VaultCrypto.write(known, key, data) }
  if (!active()) return key.fill(0)
  commit(next, key, since, mustSave = false, read = data)
  ```
  Así, la ventana queda igual de corta que en la maestra.
- **Texto de R1-2:** decir que la ventana es «el tiempo entre la última comprobación y la escritura: en la maestra, `onClockReset` y el guardado; en la huella, lo mismo tras el arreglo».
- **Importancia:** es inofensivo (ver el juicio de R1-2, abajo), pero el registro debe decir la verdad sobre cuánto dura la ventana.

**n-2. Ningún test prueba la comprobación de después de Argon2id** (`VaultStore.kt:584-587`).
- **Lo que prueba el test de ahora:** `aLateMasterUnlockOfAnotherVaultWritesNothing` (`LastVaultTest.kt:140`) llama a `a.unlock` cuando A ya no es la activa. Entra por la primera comprobación (`:573`) y vuelve antes de Argon2id.
- **La mutación:** quitar la segunda comprobación (`:584-587`) no haría fallar ningún test. Lo deduzco leyendo el código, sin ejecutarlo.
- **Por qué importa:** esa segunda comprobación es la que cubre el caso real del informe, en el que el usuario cambia de bóveda mientras Argon2id corre.
- **Test propuesto:** un `VaultStore` construido con `active = { calls++ == 0 }`, de modo que la primera llamada dé `true` y las siguientes `false`. Se abre con la maestra y se comprueba que:
  - el `vault.bin` queda igual byte a byte;
  - el estado es `Locked`;
  - no se llamó a `onClockReset`.

  Hay que verlo en rojo quitando `:584-587`. El mismo patrón sirve para la comprobación de n-1.

### Informativos

- **i-1. La comparación de `settings.json` byte a byte de los dos tests de `LastVaultTest` no distingue nada.**
  - **Por qué:** sin el arreglo tampoco cambiaba. `onClockSeen` solo se llama dentro de `opened()`, después de la puerta, y `onClockReset` ya iba detrás de `active()` desde la revisión I-1 de la tarea 2.
  - **Lo que sí distingue:** la comparación de `vault.bin`. El implementador vio que pasaba de 389 a 438 bytes, y cuadra con el código: `saveBookkeeping` vuelve a cifrar con un nonce nuevo y apunta el desbloqueo.
  - Se puede dejar, porque no estorba.
- **i-2. m-6, «Al cambiar una entrada se guarda su versión anterior.»** Es verdad para los cambios del editor, «Favorita» y «Etiquetar» en bloque. Pero «Mover a…» no guarda versión (Ruling T6), y `markUsed` tampoco, aunque no es un cambio del usuario.
  - Se acepta como frase general bajo M-3.
  - Va a la revisión final del Abogado junto con los demás textos.
- **i-3. R1-1, `COPIES_KEEP_IT` en los textos de la tarea 3:** estoy de acuerdo. Es la misma imprecisión, y debe ir al Abogado final con la fila 19 de `pendientes.md` y la misma redacción («pueden conservarlo; la copia automática quita las más antiguas»).
- **i-4. El KDoc de `unlockWithKey` no recoge el caso nuevo** (`VaultStore.kt:643-653`). Enumera los rechazos del 1 al 5, pero no dice que una bóveda que no es la activa vuelve sin lanzar nada y sin escribir. Merece una línea.

## Cada punto pedido

**m-1** (`tools/ToolCatalog.kt:49`): «Encendido, Herramientas enseña solo…».
- Con el modo encendido, describe lo que se ve; con el modo apagado, describe lo que haría.
- Es verdad en los dos estados, sigue M-3 y lo fija `theNewTextsWordForWord`.

**m-5** (`vault/TrashTexts.kt:113-117`, `SettingsScreen.kt:729`): `historyAllHint` es un `when` exhaustivo, sin `else`, así que un estado nuevo no compilaría sin su texto.
- **Sin bóveda:** «Aún no tienes bóveda.», que es verdad.
- **Con la bóveda bloqueada:** «Abre la bóveda (pestaña Bóveda) para borrar el historial.», que es verdad porque el botón está desactivado (`SettingsScreen.kt:726`).
- **Abierta, o mientras se lee:** nada, así que no parpadea un aviso falso.
- Lo prueba el test con los cuatro estados.

**m-6** (`vault/TrashTexts.kt:97`): ya no incluye a las identidades ni a los contactos. Tiene un matiz, en i-2.

**m-3** (`SimpleModeFirstVaultTest.kt:70`): el test es real.
- Hay una primera bóveda con archivo y el modo sin decidir. Sin `none { … }` (`AppContainer.kt:463`), `createVault("Dos")` encendería el modo y `assertNull` fallaría.
- El implementador lo vio en rojo con `if (none || true)`.
- Cubre también «Importar como bóveda nueva».

**m-7** (`vault/TrashTexts.kt:106`): el mínimo es 3 (`AUTO_BACKUP_KEEP_CHOICES = listOf(3, 5, 10)`, `Settings.kt:413`; `sane` cambia cualquier otro número por 5, `SettingsStore.kt:181`). «pueden conservarlas; la copia automática quita las más antiguas» es verdad en todos los casos:
- **`ON_CHANGE`:** lo prueba `AutoBackupTest.clearingTheHistoryMayRotateAnOlderCopyOut` (`:275`).
  - Tras 4 ediciones, las 3 copias guardadas tienen historial (la primera, hecha antes de que existiera, ya salió por la rotación).
  - El borrado hace una copia sin historial y quita la más antigua.
  - Las dos que quedan lo conservan, restauradas.
  - La copia nueva no lo tiene.
- **Con calendario** (diario, semanal o mensual): el borrado no copia en el momento. Las copias de antes siguen ahí hasta la siguiente copia programada.
  - D-3 no la salta, porque el borrado es un cambio: sube `revision` en `modifyData`, que no es de contabilidad.
  - Al copiar, la rotación quita la más antigua.
  - El texto sigue siendo verdad.
- **Varios destinos:** cada uno rota por su cuenta y con su propio mínimo de 3, así que cada uno cumple lo anterior.
- **Una copia exportada a mano, y las de antes de importar o de antes de la 2.0:** la rotación no las toca. Conservan el historial mientras el usuario no las borre, así que «pueden» es verdad, aunque se queda corto.
- **Sin copia automática:** «la copia automática quita las más antiguas» es una frase general, y sigue siendo verdad donde se aplica.
- **El femenino:** «conservarlas» concuerda con «las versiones y las contraseñas anteriores».

**i-6:**
- **Antes de cualquier efecto, bajo el mutex:**
  - **Huella:** la comprobación está en `:669`, dentro de `mutex.withLock { withContext(NonCancellable) { … } }` (`:657`). Va antes de `now()`, de `trashClockMark()`, de `biometricRefusal`, de la purga y del guardado.
  - **Maestra:** comprueba en `:573`, antes de Argon2id, y en `:584`, después de `afterMasterUnlock`.
  - **Entre las dos comprobaciones de la maestra no se escribe nada.** `derive { VaultCrypto.unlock }` y `derive { afterMasterUnlock(..., prepare = purgedTrash) }` solo trabajan en memoria: la purga y la clave vuelta a envolver de A8 solo se escriben en `commit` (`:593`) o, en el paso a v4, en `completeUpgrade`.
  - **`onClockReset`** queda detrás de la segunda comprobación (`:590`). Es correcto.
- **Tests:** son reales.
  - Sin el arreglo, la huella llegaba hasta `saveBookkeeping` y la maestra a `commit(mustSave = false)`, y las dos reescribían `vault.bin` con un nonce nuevo. La comparación byte a byte fallaba en las dos.
  - Los dos tests comprueban además que, enseñada otra vez, A se abre (`LastVaultTest.kt:131-133`, `:157-159`).
  - El test de la huella usa `keyOfA!!.copyOf()` para no gastar la clave, que se borra al volver.
  - La comprobación de después de Argon2id no tiene test (n-2).
- **Regresiones:** ninguna.
  - Con la bóveda activa, `active()` da `true` y el camino no cambia: la regla de los 7 días, A11 (`biometricRefusal` y `onClockReset`) y el paso a v4 (`opened(... upgraded = checked)` y después `afterUnlock`) siguen igual.
  - **Los otros llamadores de `unlock`:** `importAsNewVault` llama a `unlock` en `made`, después de `switchTo` (`AppContainer.kt:593-595`, `:626-627`), así que la bóveda ya es la activa. `deleteVault` hace `switchTo(id)` antes de `unlock` (`:673`, `:682`).
  - **Si aun así le llegara una bóveda no activa,** `unlock` volvería sin lanzar y `deleteVault` daría `LOCKED` (`:684`): se niega por el lado seguro y no borra nada sin la maestra.
  - **Para la pantalla:** una pantalla con un almacén que ya no es el activo ya está desmontada por `key(vault)`. Que `unlock` vuelva sin decir nada no deja ningún aviso falso a la vista.
- **R1-2, juicio: inofensivo.** Lo que puede escribirse en esa ventana es:
  - **el archivo de A con su propia clave,** nunca el de otra bóveda (foco 2 de la tarea 2);
  - **lo que hace cualquier desbloqueo** (la última comprobación de la maestra, la purga de más de 30 días y la clave vuelta a envolver), y solo tras una autenticación que la persona hizo para A;
  - **la marca del reloj de A,** que solo baja con su maestra correcta.

  El resultado es el mismo que si el desbloqueo hubiera terminado y después se hubiera cambiado de bóveda. Además, `switchTo` bloquea A y sube su época, y `opened()` la rechaza (`:497`), así que A nunca queda abierta. No hay fuga, no se mezclan bóvedas y no se apaga la huella.

  Se acepta como riesgo con nombre para la revisión de la rama, parte «formato y bóvedas», con dos condiciones: el arreglo de n-1, para que la ventana de la huella sea tan corta como la de la maestra, y el texto de R1-2 corregido.

**La prueba de la actualización:** lo que dice el implementador es verdad.
- En `a3ac942`, `SeguridadApp` llama a `decideRenameNotice` en cada arranque (`SeguridadApp.kt:72`). Con `renameNoticePending == null`, `decideRenameNotice` escribe siempre `settings.json` (`SettingsStore.kt:49-57` de esa etiqueta), aunque no se haya cambiado ningún ajuste.
- Solo quedan sin `settings.json` dos casos: una escritura que falló, o un móvil que viene de la 1.2.x o anterior sin haber arrancado nunca una 1.3.0 o posterior.
- La 2.0 cubre los dos por la carpeta de la bóveda (`AppContainer.kt:199`).
- `SimpleModeFirstVaultTest.aVaultOf1xWithoutSettingsIsAnUpdate` (`:85`) usa la bóveda real de la 1.4.3, sin `settings.json`. Comprueba `guideSeen = 1`, la guía de novedades y el modo apagado, también en el arranque siguiente.

**Otras regresiones:** no veo ninguna.
- `SETTINGS_NEED_OPEN_VAULT` sigue en uso en las demás secciones.
- `COPIES_KEEP_IT` sigue intacto para los textos de la tarea 3, y `copiesMadeBeforeKeepTheHistory` fija que este botón ya no lo usa.

## Revisión experto-seguridad-cripto

- **Decisión (b) de la 1.4.0, ningún resultado tardío:**
  - la huella y la maestra de una bóveda que ya no se enseña no dejan nada escrito en el caso probado (`VaultStore.kt:573`, `:584`, `:669`), bajo el mutex del almacén;
  - la puerta de `opened()` con `!active()` (`:497`) sigue debajo como segunda defensa;
  - quedan la ventana de la huella (n-1) y el test de la segunda comprobación (n-2), los dos menores.
  - Ninguno permite abrir una bóveda, mezclar bóvedas ni filtrar un secreto, así que, por la regla de la skill, no son un hallazgo de seguridad confirmado de nivel Important.
- **CRY-A02:** la huella no se apaga. La vuelta temprana no lanza, así que `launchUnlockWithKey` no llama a `onWrongKey` (`VaultStore.kt:126-132`).
- **La clave:** se borra en las tres salidas nuevas (`:584-586`, `:669`). La maestra la borra quien llama, como antes. Con la bóveda activa, la regla de los 7 días, A11 y A8 no cambian.
- **O-2 y la veracidad:** m-7 dice «pueden», y está probado con la rotación real con el mínimo de 3. m-1, m-5 y m-6 son verdad en cada estado. Ningún texto pasa por las palabras prohibidas (`ForbiddenWords`) ni nombra otra app.
- **Nada nuevo** en formatos, red, permisos, dependencias ni registros.

## Revisión Ingeniero Móvil Senior

- **Concurrencia:**
  - las comprobaciones leen `activeId`, que es `@Volatile` (`AppContainer.kt:480`), dentro del mutex del almacén;
  - `switchTo` no toma ese mutex (`:543`), y eso es lo que deja la ventana de R1-2;
  - cerrarla del todo pediría cambiar de bóveda activa bajo el mutex, que es código de la tarea 2 y bien aplazado.
  - La comprobación de antes de Argon2id ahorra una derivación con mucha memoria; `KdfLock` no cambia.
- **Interfaz:**
  - `historyAllHint` es una función pura y exhaustiva del estado, que la pantalla solo pinta;
  - con `Loading` no se pinta nada;
  - la sección se sigue pintando sin bóveda, así que el buscador tiene adónde llevar.
- **Tests:**
  - los de contenedor esperan a `advanceUntilIdle` antes de comparar bytes, que es correcto;
  - el de la rotación usa el mínimo real de Ajustes y restaura las copias de verdad;
  - falta el de la segunda comprobación (n-2).
- **m-4 y m-9:** en las filas del móvil, con el arreglo escrito, como dice el Ruling.
