# Clavero 2.0, re-revisión 2 de la rama: parte A «formato y bóvedas»

- **Rango:** `e20b3c2..4666fb2` (rama `ccr-0aa27f95-u038cd`), revisado en un clon propio.
- **Cómo:** solo lectura, sin gradle; las líneas son de `4666fb2`.
- **Commits revisados:**
  - `cda4590` (A N-1)
  - `b51162b` (A n-1)
  - `332ed25` (B i-2)
  - `15f19b1` (B i-1)
  - `e59822d` (la guarda de `deleteVault` y `runMarked`)

## Veredicto: aprobado

No hay ningún Critical, Important ni Minor nuevo. Hay dos informativos.

## Comprobación

### A N-1, `cda4590`: arreglado

- `vault/VaultsTexts.kt` `KDF_COST_KEPT` ahora dice «La bóveda se queda con el coste que ya tenía el archivo o la copia».
- Es verdad en los tres caminos:
  - un v4, aunque falle el guardado de contabilidad;
  - el paso a v4 en memoria antes de elegir;
  - importar.
- El test `UnlockErrorTextTest.theCostKeptTextSaysNoSave` fija el texto exacto y comprueba que no aparece «se ha guardado». Falla con el texto anterior.

### A n-1, `b51162b`: arreglado

- El KDoc de `UnlockErrorTextTest.kt:82-87` ya dice dónde se inyecta el fallo (`kdf`, antes de envolver).
- Remite a `noMemoryWhenWrappingAgainOpensWithTheFileCost`.
- Solo cambia un comentario.

### B i-2, `332ed25`: correcto

- `VaultLimits.COPY_DATA_MARGIN_BYTES` es de 4 KiB y solo lo usa `checkCopySize` (`BackupCheck.kt:236`). Los guardados siguen con el tope de 24 MiB en `fittedForSave`.
- **El margen sobra.** Lo único que se escribe sin pasar por `fittedForSave` son los desbloqueos (`afterMasterUnlock` y `unlockWithKeyLocked`), que añaden:
  - `lastUnlockAt` y `trashPurgeSkipped`, unas decenas de bytes;
  - y `vaultTag`, unos 45 bytes, solo cuando falta.

  La purga solo quita. `markUsed` y `recordBackupChecked` pasan por `modifyData`, así que también tienen el tope.
- **Cabe en una copia.** 24 MiB + 4 KiB en Base64 dan unos 32 MiB + 5,5 KiB, que con la cabecera siguen por debajo de los 33 MiB de la marca 3.
- **m-2 sigue cubierto.** Su test va por encima del margen.

### B i-1, `15f19b1`: correcto, sin carreras ni interbloqueos

- **`switchTo`** (`AppContainer.kt:563-570`):
  - dentro de `masterChangedLock` lee `masterChangedSlot` (`@Volatile`) y el estado, cierra el diálogo y devuelve un par;
  - después, fuera del cerrojo, llama a `lostDialogText`, que solo lee `registry.vaults` y `settings` (`StateFlow`), y publica.
- **`onMasterChanged`** (`:871-881`):
  - dentro del cerrojo llama a `slot(vaultId)`, que toma `synchronized(slots)`, y a `masterChanged.changed`, que puede llamar a `slot()` a través de `oldCopies`;
  - el orden de los cerrojos es siempre `masterChangedLock` y después `slots`.
- **Por qué no hay interbloqueo:**
  - ningún sitio toma `slots` y después `masterChangedLock`: `dropVault`, en `:668`, toma `slots` y, ya fuera de él, `store.lock()` toma `opening`;
  - ningún bloque `synchronized` suspende: Kotlin no lo permite, y no hay ninguna llamada `suspend` dentro.
- **Si la bóveda ya no está en el registro,** `lostDialogText` devuelve null y no se dice nada.
- **`hadFolders`** es `targets.isNotEmpty()`. Según el commit, es el mismo predicado con el que el diálogo ofrece «Borrar…»; no lo volví a leer en la interfaz.

### Guarda de `deleteVault`, `e59822d`: correcta

- `AppContainer.kt:717-719` cierra el diálogo de A6 de la bóveda que se borra, bajo `masterChangedLock` y antes del `switchTo` de `:721`.
- Así ya no se publica nunca «vuelve a esa bóveda» de una bóveda borrada.
- Un diálogo abierto siempre es de la bóveda activa (lo abre `onMasterChanged` solo para ella, y cada cambio de bóveda lo cierra). Al llegar aquí la activa es la que se borra, así que la comparación `masterChangedSlot?.id == id` basta.
- `vaultKekDerive` es un parámetro de test y por defecto vale `EXISTING_DERIVE`.

## Informativos

**i-1. `runMarked` junta el resultado con el aviso pendiente, pero dos avisos publicados dentro de la misma acción siguen pisándose.**
- `AppContainer.kt:819-826`.
- En «Importar como bóveda nueva», `switchTo` publica el aviso de A6 perdido y, después, `made` → `store.unlock` puede publicar `KDF_COST_KEPT`. `Notice` guarda solo el último, así que el primero se pierde.
- Solo pasa si se juntan dos cosas raras: un diálogo de A6 abierto sin pedir nada, y una copia más barata que «Estándar» en un móvil sin memoria.
- Se acepta. Si se quiere cerrar del todo, `Notice` podría guardar una lista.

**i-2. El aviso nombra la bóveda por su color, y dos bóvedas pueden tener el mismo.**
- `nextColor` vuelve a empezar a partir de 8 bóvedas, y el usuario puede elegir el color.
- Entonces el texto es ambiguo, pero no falso.
- Es lo que decidió el Ruling: el nombre de la bóveda va cifrado y no se puede enseñar fuera de ella.

## Revisión experto-seguridad-cripto

- `AppContainer.kt:563-570`, `:717-719` y `:871-881`: el diálogo de A6 solo puede pedir el borrado de las copias de la bóveda cuya maestra cambió. Un cambio de bóveda o un borrado lo cierran de forma atómica frente a `onMasterChanged`.
- `vault/BackupCheck.kt:236` y `vault/VaultLimits.kt` (`COPY_DATA_MARGIN_BYTES`): una copia hostil con más de 24 MiB + 4 KiB sigue siendo `COPY_DAMAGED`. El margen no deja que ningún guardado pase de 24 MiB.
- Los textos de esta ronda (`KDF_COST_KEPT` y `masterChangedInOtherVaultText`) solo usan el color, que el registro guarda sin cifrar, y nunca el nombre cifrado de la bóveda. No afirman nada que el código no haga.

## Revisión Ingeniero Móvil Senior

- `AppContainer.kt`: el orden de los cerrojos es `masterChangedLock` y después `slots`, sin suspensión dentro. `switchTo` sigue con `NonCancellable` y `awaitIdle` fuera de los monitores.
- `AppContainer.kt:819-826`: `runMarked` corre en `NonCancellable` y publica una sola vez.
- No hay estado nuevo en la interfaz, ni `rememberSaveable`, ni trabajo nuevo en el hilo principal.

## Textos para el Abogado Digital (revisión final)

- **`KDF_COST_KEPT`**, la versión nueva: «No se pudo subir el coste de Argon2id al elegido en Ajustes: ahora no hay memoria suficiente. La bóveda se queda con el coste que ya tenía el archivo o la copia. Se volverá a intentar cada vez que la abras con la contraseña maestra. Si en Ajustes tienes el coste «Alto», puedes elegir «Estándar».»
- **`masterChangedInOtherVaultText`**, con sus dos variantes:
  - con carpetas: «La contraseña maestra de la bóveda de color X se cambió. Al cambiar de bóveda, el aviso que ofrece borrar sus copias anteriores no se llegó a mostrar o se cerró: esas copias siguen abriéndose con la contraseña anterior. Para borrarlas, vuelve a esa bóveda y cambia otra vez la contraseña maestra, o bórralas a mano.»
  - sin carpetas: «La contraseña maestra de la bóveda de color X se cambió. Las copias .sgbak que ya tenías de esa bóveda se siguen abriendo con la contraseña anterior.»
  - con la línea de la huella cuando no se pudo desactivar.
  - Ojo: dos bóvedas pueden tener el mismo color (i-2).
- **El resultado de una acción de «Bóvedas»:** cuando hay un aviso pendiente, se enseñan juntos, separados por una línea en blanco (`runMarked`).
