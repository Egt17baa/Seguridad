# Clavero 2.0, revisión de la rama: parte A «formato y bóvedas»

**Rango:** `a3ac942..dcf28dc`, en un clon propio en `dcf28dc` y sin gradle. **Estado:** terminada.

## Alcance

**Leídos enteros en `dcf28dc`:** `vault/Vault.kt`, `vault/VaultStore.kt` (las 1 738 líneas), `vault/VaultData.kt`, `vault/VaultRegistry.kt`, `vault/VaultLimits.kt`, `vault/PendingUpgrade.kt`, `vault/Trash.kt`, `vault/EntryHistory.kt`, `vault/MasterChanged.kt` y `AppContainer.kt` (las 899 líneas). También la lógica de `ui/screens/VaultUpgrade.kt`.

**Leídos en parte** (el diff y lo que lo rodea):
- `crypto/Crypto.kt`: `KdfParams.checkSane`, `argon2id`, `outOfMemoryAsFailure` y `newPasswordFailureMessage`.
- `crypto/KdfLock.kt`: el KDoc.
- `android/BiometricVault.kt`: el diff entero, con las ramas de alias y preferencias por bóveda.
- `imports/`: el diff entero (`MergePlan.kt`, `ImportSession.kt`, `ImportTexts.kt`, `ImportModel.kt`, `ImportFlow.kt`).
- `vault/{Attachments,History,MasterPolicy,ContactsView}.kt`: el diff.
- `vault/Folders.kt`: los topes.
- `vault/BackupCheck.kt:194-235` (`checkCopyEntries`, `checkCopyStructure`) y `vault/AutoBackup.kt:637-711` (`replaceOldCopies`).
- `ui/screens/VaultPickerScreen.kt:160-330`, `ui/screens/VaultBackup.kt:95-200`, `ui/screens/ImportScreen.kt` (la unión con el almacén), `ui/screens/SettingsScreen.kt:595-670` y `settings/Settings.kt` (`KdfProfile`, `availableOn`, `settingsEditable`).
- `settings/SettingsStore.kt:165-182` y `core/PickerPlaces.kt`.
- Tests: `vault/UnlockErrorTextTest.kt` y la lista de `vault/VaultV4Test.kt`; los usos de `vault-1.4.3.json`, `vault-1.1.json`, `backup-1.1.sgbak` y `export-1.0.2.json`.

**Documentos:** `CLAUDE.md`, la skill `experto-seguridad-cripto/SKILL.md`, el parte (§1), `progress.md` (cada «Ruling» y «Owner decision»), `pendientes.md`, el encargo, y de los informes de las tareas, `task-2-review.md` (sus ⚠️). El diseño y el resto de los `task-*-review.md` solo los busqué por términos, para no repetir lo cerrado.

**No ejecuté nada:** nada de gradle, como pide el encargo.

## Issues

### Critical

Ninguno.

### Important

#### I-1. Cambiar la maestra y cambiar de bóveda a mitad: el diálogo de A6 se queda con la otra bóveda (seguridad y veracidad; cruza las tareas 2 y 8)

- **Dónde:**
  - `AppContainer.kt:806-808`: `onMasterChanged` hace `masterChangedSlot = slot(activeId)`.
  - `AppContainer.kt:798` y `:802`: `replace` y `oldCopies` usan esa bóveda.
  - `ui/screens/VaultBackup.kt:130-145`: el cambio corre en `NonCancellable` y, al acabar, llama a `onMasterChanged` sin decir de qué bóveda es.
  - `AppContainer.kt:551-555`: `switchTo` pone `activeId = id` y cierra `masterChanged`, los dos **antes** de `old.store.awaitIdle()`.
- **Qué pasa, paso a paso:**
  1. Con la bóveda A abierta, el usuario cambia la maestra y pulsa «Cambiar». El diálogo se cierra al instante y `changeMaster` deriva con Argon2id bajo el mutex de A, de 1 a 3 s, o más con «Alto».
  2. Mientras deriva, el usuario va a «Bóvedas» y elige la bóveda B. El botón solo depende de `vaultsBusy` (`VaultPickerScreen.kt:219-226`), que el cambio de maestra no pone.
  3. `switchTo` hace `activeId = B` y `masterChanged.close()`, y espera el mutex de A.
  4. `changeMaster` termina: guarda A con la maestra nueva y la bloquea.
  5. La corrutina de `VaultBackup` apaga bien la huella de A (`store.id`), pero `onMasterChanged` toma `slot(activeId)`, que ya es B.
  6. El diálogo «Contraseña maestra cambiada…» se abre sobre B, con `oldCopies` = las copias apuntadas de **B**. Ya no lo cierra nada, porque el `close()` del cambio de bóveda vino antes.
  7. Si el usuario pulsa «Borrar las copias automáticas anteriores», `replaceOldCopies` corre sobre B (`AutoBackup.kt:655-666`). Con B abierta, hace una copia de B y borra sus copias anteriores. Con B cerrada, deja en sus destinos una petición de A6 que lo hará en el próximo desbloqueo de B (`waitForUnlock`, `:700`).
- **Por qué importa:**
  - Se borran copias de B, cuya maestra no cambió: sus puntos de restauración se pierden sin motivo.
  - Las copias de A, que siguen bajo la contraseña vieja (quizá filtrada), no se ofrecen para borrar, así que el objetivo de seguridad de A6 falla en silencio.
  - Los textos del resultado («se borraron las copias … hechas antes del cambio de contraseña») son falsos para B.
- **Por qué cruza tareas:** `MasterChanged` y `onMasterChanged` son de la 1.3.x, con una sola bóveda. La tarea 2 los hizo por bóveda leyendo la activa, la tarea 8 añadió `oldCopies` por destino, y nadie probó un cambio de bóveda durante el cambio de maestra.
- **Arreglo:**
  - `fun onMasterChanged(vaultId: String, fingerprintFailed: Boolean, hasIdentity: Boolean)`, y `VaultBackup.kt:144` pasa `store.id`.
  - Dentro: `if (vaultId != activeId) return`. Así se cumple la regla escrita en `switchTo`, «un cambio de bóveda cierra el diálogo», y nunca se abre para otra bóveda.
  - Si no, `masterChangedSlot = slot(vaultId)`.
  - `LocalOnMasterChanged` (`AppNavHost.kt:329`) cambia su firma.
  - El diálogo que se pierde equivale a uno cerrado sin pulsar nada: las copias de A siguen bajo la contraseña anterior. Debe decirlo la línea que ya enseña el *snackbar* («Contraseña maestra cambiada…») o la política. Ese texto va al Abogado.
- **Test** (en `MasterChangedSwitchTest`, «Pruebas que pide el revisor» n.º 1): dos bóvedas con un destino cada una; el `changeMaster` de A detenido en `vaultCompute`; `switchTo(B)` en paralelo; se suelta la derivación y se llama a `onMasterChanged(A.id, …)` como hace `VaultBackup`. Se comprueba:
  - `masterChanged.state.value.open == false`;
  - `masterChanged.replace() == false`;
  - los ajustes de B (`a6`, `replacePending` de sus destinos) siguen igual, byte a byte.

  Sin el arreglo, el diálogo se abre con las copias de B.

#### I-2. Fila 45: volver a envolver sin memoria deja la bóveda sin salida (Important; necesita Ruling)

- **Dónde:**
  - `vault/Vault.kt:506-516`: con el archivo más barato que Ajustes (`weaker`), `wrap` con el coste máximo de cada eje.
  - `vault/Vault.kt:354`: `argon2id` dentro de `wrap`, con `data` ya descifrado y decodificado en memoria (`:506-507`).
  - `vault/VaultStore.kt:587-591`: si lanza, se borra la clave y la bóveda no se abre.
  - `settings/Settings.kt:443` (`settingsEditable`) y `ui/screens/SettingsScreen.kt:608`, `:649-650`: el coste solo se puede cambiar con la bóveda abierta (COV2-1).
- **Respuestas a lo que pide el encargo:**
  - **¿Se repite en cada intento?** Sí. `weaker` se calcula en cada desbloqueo con la maestra a partir del archivo, que no ha cambiado (`UnlockErrorTextTest.kt:83-104` lo confirma: el archivo queda igual byte a byte). Mientras Ajustes diga «Alto», cada intento vuelve a pedir 128 MiB. El test de la ronda 2 inyecta un fallo **una sola vez**: no prueba un fallo que se repite.
  - **¿Ajustes deja bajar el coste con la bóveda cerrada?** No: `settingsEditable` es `Unlocked || NoVault`, y `SettingsScreen.kt:649` desactiva las opciones.
  - **¿Se puede elegir «Alto» donde no cabe?** No de forma determinista:
    - `availableOn` (`Settings.kt:423`) pide `heap/2 ≥ 128 MiB`, más estricto que `checkSane` (`Crypto.kt:72`, ¾ del *heap*);
    - `SettingsStore.kt:177` baja «Alto» a «Estándar» al leer si el móvil ya no llega;
    - así que el `cannotFit` de `checkSane` no salta.

    Queda el `OutOfMemoryError` real (`Crypto.kt:99-100`). El pico de memoria durante el `argon2id` de `wrap` es 128 MiB más la bóveda decodificada (hasta 24 MiB de JSON, con sus objetos, `Vault.kt:506-507`), porque `data` está vivo mientras se deriva. En un móvil con 256 MiB de *heap* y una bóveda grande, el fallo puede ser estable.
  - **Salidas reales:**
    - la huella, si está activada y dentro de los 7 días, porque `unlockWithKey` no vuelve a envolver (`VaultStore.kt:706-707`). Abierta así, Ajustes deja volver a «Estándar»;
    - sin huella, ninguna. «He olvidado la contraseña maestra» borra la bóveda, y la pantalla dice `KDF_OUT_OF_MEMORY` («…para el coste «Alto»…»), que es verdad pero no tiene salida.
- **Valoración del arreglo propuesto** (si volver a envolver se queda sin memoria, abrir con el coste que el archivo ya tiene y decirlo): **es correcto y no baja nada.**
  - En `afterMasterUnlock`, solo cuando el fallo es `CryptoFailure(KDF_OUT_OF_MEMORY)` **de `wrap` al subir el coste** (el `unlock` anterior ya derivó con el coste del archivo en otro `derive`):
    - un archivo v4 queda como `write(known.copy(masterCheckedAt = now), dek, data, random)`, el mismo camino que sin `weaker`;
    - un archivo v2 o v3 (el paso a v4 obliga a envolver) usa `wrap(dek, data, master, EXISTING, now, known.kdf.params(), …)`: el coste del archivo, que acaba de caber.
  - Se devuelve una marca (por ejemplo, `VaultFile` más `costRaised = false`) para que `VaultStore` enseñe una vez el aviso, que va al Abogado. Por ejemplo: «No se pudo subir el coste a «Alto»: no hay memoria suficiente ahora. La bóveda sigue con el coste de antes. Puedes elegir «Estándar» en Ajustes.»
  - El siguiente desbloqueo lo vuelve a intentar. El fallo por memoria salta al reservar, así que cuesta poco.
  - Un ajuste opcional reduce el pico: derivar la KEK nueva **antes** de `read` y `prepare` en el camino `weaker`.
- **Opciones para el Ruling:**
  - **(A)** Se acepta tal cual, como bloqueo poco probable, con su texto. No lo recomiendo: es una pérdida de acceso sin salida.
  - **(B)** El arreglo de arriba. **Lo recomiendo.**
  - **(C)** Además de (B), reordenar la derivación.
- **Test de (B)** (n.º 2): un `kdf` que **siempre** devuelve un coste mayor y una derivación inyectada que lanza `KDF_OUT_OF_MEMORY` solo al envolver; dos desbloqueos seguidos. Hace falta un punto de inyección en `afterMasterUnlock`, por ejemplo un parámetro `derive: (CharArray, ByteArray, KdfParams) -> ByteArray = ::argon2id…`. Se comprueba:
  - las dos veces abre;
  - `file.kdf` sigue igual;
  - `masterCheckedAt` sube;
  - el aviso sale una vez;
  - con un v3 (`vault-1.4.3.json`), pasa a v4 con el coste del archivo.

#### I-3. Fila 38: `KDF_OUT_OF_MEMORY` dice «Alto» también cuando el coste era «Estándar» (veracidad)

- **Dónde:**
  - `crypto/Crypto.kt:90`: el texto.
  - `:99-100`: `outOfMemoryAsFailure` lo lanza con **cualquier** coste.
  - `:241`: `argon2id` lo usa con `p` cualquiera.
  - `:76`: `cannotFit`, solo con más de 64 MiB.
- **Qué enseña cada sitio y si «Alto» es verdad allí:**

  | Sitio | Cómo llega | ¿«Alto» es verdad? |
  |---|---|---|
  | Pantalla de bloqueo (`VaultScreen.kt:454`, `masterUnlockErrorText`) | `checkSane` del archivo; OOM al derivar con el coste del archivo; OOM al volver a envolver (I-2) | Sí con un archivo «Alto» y al volver a envolver hacia «Alto». **No** con un archivo «Estándar» (64 MiB) en un móvil con poco *heap* o con memoria ocupada: el OOM sale por `:99`. Un coste hecho a mano de 64 a 128 MiB tampoco es «Alto» (fila 38, n-1). |
  | Contactos (`ContactsScreen.kt:145`, `masterFailureMessage`) | `confirmMaster`: solo deriva con el coste del archivo | Igual que la fila anterior, sin el caso de volver a envolver. |
  | Crear la bóveda (`VaultScreen.kt:393`), «Nueva» en Bóvedas (`VaultPickerScreen.kt:310`), cambiar la maestra (`VaultBackup.kt:155`): `newPasswordFailureMessage` | El coste de Ajustes de esa bóveda | Sí con «Alto». **No** con «Estándar» y un OOM. |
  | Importar copia (`VaultBackup.kt:183`), «Importar como bóveda nueva» y borrar una bóveda con su maestra (`AppContainer.kt:763`, `e.message`), Comprobar una copia (`BackupCheckSession.kt:130`) | El coste de la copia, y en el import, el de volver a envolver (A8) | Solo con una copia «Alto». |
  | Cifrar archivo (`SecurityToolsScreen.kt:666`), cifrar y descifrar texto | El perfil al cifrar; la cabecera al descifrar | Igual. |
  | «Importar de otra app» | No llega: `IMPORT_KDF_TOO_BIG` (`ImportKdf.kt`) | No aplica. |

- **Arreglo:**
  - `outOfMemoryAsFailure(p)` elige el texto por `p.memoryKiB`: con más de `DEFAULT_KDF_MEMORY_KIB`, el texto del coste alto (que no diga «Alto» con comillas si el coste no es exactamente el del perfil); con 64 MiB o menos, una constante nueva, `KDF_NO_MEMORY_NOW`, con qué hacer. Por ejemplo: «Este móvil no tiene ahora memoria suficiente para abrir esto. Cierra otras apps y vuelve a intentarlo.»
  - `cannotFit` (`:76`) siempre está por encima de 64 MiB. La redacción «coste más alto que el estándar» es verdad también para el caso de 64 a 128 MiB de la fila 38.
  - La constante nueva entra en las listas cerradas de `masterUnlockErrorText` (`Vault.kt:295`) y `newPasswordFailureMessage` (`Crypto.kt:204`).
  - La redacción final va al Abogado.
- **Test** (n.º 3): `argon2id` con `KdfParams(64 * 1024, 1, 1)` y un OOM inyectado (una función interna con el generador inyectable) da la constante nueva, y con `KdfParams(128 * 1024, …)` da el texto alto. Además, `masterUnlockErrorText` y `newPasswordFailureMessage` dejan pasar las dos.

### Minor

**m-1. Los lugares de los selectores siguen siendo de toda la app, y un aviso de la tarea 2 se quedó sin destino.**
- **Dónde:** `AppContainer.kt:179` (un solo `PickerPlaces`), `core/PickerPlaces.kt:17-41` y `:97-100`.
- **Qué pasa:**
  - los lugares guardados en disco (las copias de la bóveda, el texto cifrado guardado, las salidas de Limpiar foto y Tachar) se ofrecen en todas las bóvedas;
  - `deleteVault` (`AppContainer.kt:677-716`) no olvida ninguno;
  - «He olvidado la contraseña» de **una** bóveda (`VaultScreen.kt:496`, `forgetAll()`) olvida los de **todas**.
- **Por qué importa:** el revisor de la tarea 2 lo marcó con ⚠️ para que decidiera el controlador (`task-2-review.md:25`), el Abogado lo pidió para la política (O-10.7, `abogado-2.0-inicial.md:239`, `:412`), y no hay ningún Ruling ni ninguna fila de `pendientes.md` sobre ello.
- **No es un secreto:** es el URI de un documento de copia, ya en disco desde la 1.4.2.
- **Arreglo, una de dos:**
  - **(a)** las claves de `kept` por bóveda (`perVaultName(place.name, vaultId)`), con `forget(vaultId)` en `deleteVault` y en «He olvidado»;
  - **(b)** se acepta que son de toda la app, se corrige el texto de la política («Al borrar la bóveda se olvidan todas») y se añade una fila a `pendientes.md` para la parte D y el Abogado.

  Lo decide un Ruling.
- **Test con (a):** dos bóvedas; `remember(EXPORT_COPY)` en A; en B, `place(EXPORT_COPY) == null`; `deleteVault(A)` olvida el lugar de A.

**m-2. Una copia v4 hostil puede traer más de 24 MiB de datos, y después no se puede guardar nada.**
- **Dónde:**
  - el tope por archivo de la copia es de 33 MiB en Base64 (`Backup.kt:51`), unos 24,7 MiB sin codificar;
  - `checkCopyStructure` (`BackupCheck.kt:213-219`) y `checkCopyEntries` no miran el tamaño de los datos;
  - `afterMasterUnlock` no ajusta un v4 a los límites (`Vault.kt:507`).
- **Qué pasa:** con más de 24 MiB y sin historial que quitar, cada guardado lanza `VAULT_TOO_BIG` (`VaultLimits.kt:181`), también «mover a la papelera», que no reduce el tamaño. El texto («Vacía la papelera, borra historial…») no se puede seguir. Abre y exporta, pero no se puede editar.
- **Alcance:** solo una copia hecha a mano. Ninguna bóveda de Clavero pasa de 24 MiB.
- **Arreglo:** en `replace()` y en `checkBackup`, para una copia v4, `if (jsonBytes(incomingData) > VaultLimits.VAULT_MAX_DATA_BYTES) throw CryptoFailure(COPY_DAMAGED)`. Una copia de la 1.4.3 o anterior nunca pasa de 6 MB.
- **Test** (n.º 4): una copia v4 con 24 MiB + 1 de notas repartidas en entradas sin historial, que se rechaza con `COPY_DAMAGED` en «Importar copia» y en «Comprobar una copia».

**m-3. Fila 3: el orden de las entradas sigue en el hilo de quien llama.**
- **Dónde:**
  - `VaultStore.kt:500`: `opened()` ordena con `sortedBy { it.title.lowercase() }`, que vuelve a calcular `lowercase()` en cada comparación.
  - `opened()` corre en el contexto de quien llama, porque `commit` es `withContext(NonCancellable)` sin cambiar de *dispatcher* (`:465`, `:486`). Un «Guardar» del editor viene del hilo principal.
  - En ese mismo hilo corren `collectGarbage` (el `HashSet`, `:391-392`) y los filtros `live` y `trash` de `VaultState.Unlocked` (`:62-63`).
- **Por qué cruza tareas:** en la 1.4.3, `opened()` además descifraba en ese hilo y el tope era de 6 MB. La tarea 4 (I-2) sacó el descifrado, pero el orden se quedó, y la 2.0 sube el tope a 24 MiB.
- **Coste:** O(n log n) asignaciones de cadenas por número de entradas. Es irrelevante con cientos de entradas; con decenas de miles de entradas pequeñas cerca del tope, se nota en el hilo principal.
- **Arreglo:** ordenar dentro del `withContext(compute)` donde ya se tienen los datos (`modifyData` en `:840-845`, `unlockWithKeyLocked` en `:706`, `unlockLocked` en `:608-609` y `commit` en `:481`), con las claves precalculadas (`map { it to it.title.lowercase() }.sortedBy { it.second }`), y que `opened()` reciba los datos ya ordenados.
- **Test** (n.º 5): `modifyData` con un `compute` que apunta su hilo, y un comparador espía que falla si corre fuera de él.

**m-4. Los nombres de carpetas y vistas de una copia no tienen tope.**
- **Dónde:** la app limita los nombres a 60 puntos de código (`Folders.kt:24`, `:132`), pero `checkStructure` (`VaultLimits.kt:209-236`) no mira la longitud de `Folder.name` ni de `SavedView.name`. Una copia hostil puede traer un nombre de megas que la lista y las migas pintan (decisión (a) al enseñar). Las vistas se recortan al leer (Ruling T4, M-1), pero las carpetas no.
- **Arreglo:** en `checkCopyStructure`, un nombre de más de `ORGANIZE_NAME_MAX` es `COPY_DAMAGED`, como los campos (Ruling T5, I-2). Las copias de antes de la 2.0 no tienen carpetas, así que no rompe ninguna.
- **Test** (n.º 6).

### Info

- **i-1, fila 31** (se acepta): el detalle va en la tabla de filas.
- **i-2.** `deleteVault` de una bóveda v2 o v3 llama a `store.unlock(master)` (`AppContainer.kt:690`). Eso lanza la copia `seguridad-antes-de-2-0-` en su carpeta y quizá guarda el v4 justo antes de borrar (`VaultStore.kt:573`). Es inofensivo y coherente con «las copias ya hechas no se tocan», pero el borrado espera a que termine la copia. Si se quiere, `confirmMaster` sirve para la bóveda cerrada.
- **i-3.** Al borrar una bóveda, `BiometricVault.clear` vacía `<PREFS>_<id>`, pero el archivo XML vacío se queda. Solo contiene un id aleatorio, no un secreto.
- **i-4.** `changeMaster` y la importación de una copia (`replace`) no miran `active()`: terminan y guardan la bóveda A aunque se haya cambiado a B. Es lo que pidió el usuario sobre A, y `opened()` la deja cerrada. Todas las devoluciones de llamada de `Slot` usan su propio `id` (`AppContainer.kt:405-455`), así que no tocan los ajustes de B. El único escape es el de I-1.
- **i-5, a comprobar.** Al importar una copia hostil, decodificar hasta unos 24,7 MiB de JSON con muchos objetos diminutos (etiquetas vacías, carpetas) puede quedarse sin memoria antes de `checkCopy*`. El `OutOfMemoryError` no se captura en `replace()` y cierra la app. La clase ya existía en la 1.4.3 con las etiquetas. Prueba n.º 7.

## Riesgos que cruzan tareas: respuestas

1. **El paso a v4 frente a todo lo que escribe:** no encontré ningún camino que guarde sin pasar por la guarda.
   - Lo que pasa por `modifyData` lo para `check(upgrade == null)` (`VaultStore.kt:827`): editar, papelera, historial, carpetas, deshacer, importar de otra app, renombrar y «Comprobar una copia». Lo de contabilidad devuelve `false` (`:826`).
   - `changeMaster` (`:1337`) y la importación de una copia (`:1424`) se rechazan.
   - La purga de la papelera y la marca se aplican solo al v4 en memoria, en el `prepare`.
   - «Ahora no» de las misiones vive solo en memoria.
   - La copia con calendario exporta el archivo del disco (v2 o v3).
   - Borrar una bóveda: i-2.
   - Además, `VaultCrypto.write` (`Vault.kt:462`) rechaza un archivo que no es v4.
   - Muerte del proceso: `writeBytesAtomically` (`core/AtomicFile.kt:15-23`) escribe, hace `fsync` y renombra de forma atómica; el v4 solo se escribe en `completeUpgradeLocked` (`:634`).
2. **Aislamiento entre bóvedas:**
   - cada `Slot` tiene su almacén, su `AutoBackup`, su `UndoTrash`, sus ajustes, su alias y sus preferencias de huella y su marca A11 (`AppContainer.kt:401-459`);
   - `switchTo` bloquea, borra el aviso A14 y cierra A6; los ViewModels se rehacen (`VaultScoped`, Ruling T2 M-6);
   - borrar una bóveda quita su carpeta, su registro, sus ajustes (con sus listas) y su clave del Keystore;
   - **fallan:** el diálogo de A6 (I-1) y los lugares de los selectores (m-1).
   - `KdfLock` y `KeyOpsQueue` son de todo el proceso, a propósito.
   - El QR pendiente va a la bóveda activa, donde el usuario guarda a mano (parte C).
3. **Resultados tardíos:**
   - el desbloqueo con la maestra y con la huella tienen `active()` (i-6);
   - «Comprobar una copia» e «Importar de otra app» tienen la época (R66 y `sinceLock`);
   - activar la huella usa `store.id` y la clave de ese almacén (`VaultScreen.kt:225-237`);
   - cambiar la maestra e importar una copia terminan sobre su propia bóveda (i-4), salvo el diálogo de A6 (I-1);
   - exportar toma la instantánea bajo el mutex y escribe donde eligió el usuario.
   - Fila 31: en la tabla.
4. **Límites:**
   - la profundidad, el número de carpetas, vistas, colores y campos, y los ids repetidos se rechazan;
   - faltan el tamaño total de una copia v4 (m-2) y la longitud de los nombres de carpetas (m-4);
   - una bóveda en el tope de 24 MiB, con papelera e historial (todo está en `VaultData`), cabe en una copia con marca 3 de 33 MiB (Ruling T1, duda 1);
   - `applyImport` mide la copia exacta, papelera incluida (`VaultStore.kt:986`, `:1008`).
5. **Reloj:**
   - la purga usa `trashClockMark`, en memoria y sin la subida del arranque, y `lastUnlockAt`, y como mucho se salta una vez (`Trash.kt:42-49`);
   - un reloj adelantado sube A11 (`onClockSeen`), así que al volver la hora atrás la huella pide la maestra (sigue A11, `Vault.kt:565`);
   - la purga desde la huella es un guardado de contabilidad que no mueve `changeMark` (parte B).
   - Coherente con los Rulings T3 (dudas 1 y 3, I-1).
6. **Historial y maestra:**
   - `changeMaster` vuelve a cifrar todo, historial y papelera incluidos, con una clave de datos nueva (`Vault.kt:526-536`): con la clave vieja solo se lee el archivo viejo, y las copias de antes, que A6 reemplaza (I-1);
   - «Borrar historial» quita `history` y `passwordHistory` (`EntryHistory.kt:73-75`), y las copias lo conservan (fila 30, parte B).
7. **Importar sobre v4:**
   - las entradas de la papelera no cuentan como duplicados ni dan etiquetas (`MergePlan.kt` `planMerge`, `liveTags`);
   - «Reemplazar» guarda una versión (`withVersion`);
   - el plan viejo (R16) usa `_revision`, que la contabilidad no sube; `mergedEntries` se calcula otra vez sobre la lista actual, así que no escribe datos viejos;
   - R66 con varias bóvedas: el ViewModel se queda con su almacén (`ImportScreen.kt:184`) y su época, y un cambio de bóveda la sube.
8. **Huella con varias bóvedas:**
   - un alias y unas preferencias por id (`BiometricVault.kt`, `perVaultName`);
   - `masterCheckedAt` (los 7 días) va en el archivo de cada bóveda;
   - A11 es por bóveda;
   - `StaleBiometricKey` apaga solo la huella de `store.id` (`VaultScreen.kt:429-432`);
   - la guarda `unlocking` es global, así que un aviso de la huella de A que llega tarde va al almacén de A, que no está activo, y su clave se borra.

## Filas de `pendientes.md`

| Fila | Veredicto |
|---|---|
| **31** | **Se acepta.** Después de la segunda comprobación (`VaultStore.kt:593`; con la huella, `:710`), queda la ventana hasta que `switchTo` cambia `activeId` (`AppContainer.kt:551`), porque `switchTo` no toma el mutex antes de eso. En ella solo se escribe el archivo de **A** con **su** clave de datos y la KEK de su maestra, que el usuario acaba de escribir bien: `masterCheckedAt`, `lastUnlockAt`, la papelera vaciada y, quizá, la clave vuelta a envolver con el coste de Ajustes de A. También se baja la marca A11 de A (`:599`). `opened()` la rechaza (`:506`), la clave se borra y `collectGarbage` no corre, porque el estado es `Locked`. Con un v2 o v3 no se escribe nada (`:605-616`). Todo es lo que haría un desbloqueo normal de A. Cerrarla del todo pide que `switchTo` tome el mutex de A **antes** de cambiar `activeId`, y entonces el cambio de bóveda esperaría a un Argon2id entero, sin cambiar lo que se escribe. No compensa. |
| **45** | **Necesita Ruling** (Important, I-2). Opciones (A), (B) y (C); recomiendo **(B)**. |
| **2** | **Se acepta.** El tamaño no esconde un defecto dentro de `VaultStore`: las guardas (`upgrade`, época, `active()`) están juntas en `modifyData`, y las operaciones aparte (`changeMaster`, `replace`, los dos desbloqueos) las tienen o no las necesitan (i-4). El defecto que encontré (I-1) está en `AppContainer` y `VaultBackup`. Sacar la importación no es de esta versión. |
| **3** | **Se acepta lo pesado** y m-3 se arregla. El coste está en `fittedForSave` (una codificación que solo cuenta bytes), `VaultCrypto.write` (codificar, cifrar y Base64), `save` (`encodeFile`, con un segundo JSON de unos 32 MiB) y, en `applyImport`, una codificación más. Todo corre en `compute` o en `io` (`VaultStore.kt:840-845`, `:408`). Quedan en el hilo de quien llama el orden de `opened()` y los filtros (m-3). Medir en el móvil sigue pendiente: la fila se queda «Abierto» como fila del móvil. |
| **38** | **Se arregla** (Important, I-3), con la tabla por sitio de arriba. La redacción va al Abogado. |

## Pruebas que pide el revisor

1. **`MasterChangedSwitchTest.changeOfMasterThenSwitchNeverOpensA6ForTheOtherVault`** (`app/src/test/.../AppContainerVaultsTest` o uno nuevo):
   - Un `AppContainer` con un `vaultCompute` que se puede detener, dos bóvedas (A y B) con un destino cada una y copias apuntadas.
   - Se lanza `A.store.changeMaster(nueva)`, se espera a que pare en `vaultCompute` y se lanza `switchTo(B)`.
   - Se suelta, y después de que `changeMaster` devuelve `true`, se llama a `container.onMasterChanged(false, true)` (hoy) o a `onMasterChanged(A.id, false, true)` (con el arreglo).
   - Se comprueba que `masterChanged.state.value.open == false` y que los ajustes de B no cambian después de `masterChanged.replace()`. Hoy falla: está abierto y `oldCopies` son las de B.
2. **`UnlockErrorTextTest.noMemoryWhenWrappingAgainOpensWithTheFileCost`** (con el arreglo (B)): un fallo **persistente** solo al volver a envolver. Abre dos veces, `kdf` del archivo igual, aviso una vez; y lo mismo desde `vault-1.4.3.json`, que pasa a v4 con su coste. Hoy, el mismo cuerpo con el fallo persistente demuestra el bloqueo: cada `unlock` lanza `KDF_OUT_OF_MEMORY`.
3. **`CryptoTest.outOfMemoryTextFollowsTheCost`**: con 64 MiB y un OOM inyectado, `KDF_NO_MEMORY_NOW`; con 128 MiB, el texto del coste alto. Además, las listas de `masterUnlockErrorText` y `newPasswordFailureMessage`.
4. **`BackupCheckTest.v4CopyOverTheDataLimitIsDamaged`**: unos 24 MiB + 1 de datos, sin historial, dentro de una copia con marca 3 que cabe en 33 MiB. Con `importReplace` y con `checkBackup`, `COPY_DAMAGED` y nada cambiado.
5. **`VaultStoreTest.sortRunsOnCompute`**: el orden de `opened()` y `live`/`trash`, fuera del hilo de quien llama (m-3).
6. **`BackupCheckTest.longFolderNameIsDamaged`**: una carpeta de 61 puntos de código da `COPY_DAMAGED` (m-4).
7. **A comprobar (i-5): `VaultStoreTest.hostileTinyObjectsCopy`**: una copia v4 con unos 20 MiB de `"tags":["","",…]` en una entrada. Hay que mirar si `importReplace` acaba en `CryptoFailure` o en `OutOfMemoryError` con el *heap* de los tests (`-Xmx` del módulo). Es solo informativo.

## Dudas

- **I-1:** ¿se descarta el diálogo si la bóveda ya no está activa (lo que propongo), o se guarda para cuando se vuelva a A? Guardarlo pide estado por bóveda en `MasterChanged`.
- **m-1:** ¿los lugares de las copias son por bóveda? Lo decide el controlador (O-10.7).
- **I-2 (B):** el texto del aviso y si basta con una vez por proceso. Va al Abogado.

## Lo que no pude comprobar

- Nada ejecutado (sin gradle).
- No leí enteros:
  - `crypto/Crypto.kt` (las partes de `SG1:` y `.sgenc` quedan fuera de esta rama, salvo `checkSane`);
  - `android/BiometricVault.kt` (solo el diff y su contexto);
  - `imports/*.kt` (solo el diff; los lectores no cambian en la rama);
  - `vault/Folders.kt`, `MasterPolicy.kt` y `ContactsView.kt` (el diff y los topes);
  - `ui/screens/VaultPickerScreen.kt` (la lógica, de la línea 160 a la 330).
- La probabilidad real del OOM de I-2 y I-3 en un móvil: depende del *heap* y de la bóveda. Va a una fila «Móvil»: con «Alto» y una bóveda grande de prueba, en un móvil con 256 MiB por app.

## Revisión experto-seguridad-cripto

- `AppContainer.kt:806-808`, `:798`, `:802`, `:551-555` y `ui/screens/VaultBackup.kt:144`: el diálogo de A6 se liga a la bóveda activa y no a la que cambió la maestra. Se borran copias de B y no se ofrecen las de A, que siguen bajo la contraseña vieja (I-1, Important).
- `vault/Vault.kt:508-516`: al volver a envolver, el coste no baja en ningún eje (CR-2). Correcto. El arreglo (B) de I-2 conserva eso, porque usa el coste del archivo.
- `vault/Vault.kt:213-215`, `:247` y `:254`: la AAD une el formato, la versión, el coste y la sal, y en los datos además la revisión y `masterCheckedAt`. Un v4 no pasa por un v3 ni al revés (`VaultV4Test.v4BindsVersionInAad`). Desde la huella, ningún v2 o v3 llega a `write` (`Vault.kt:462`, `:564`).
- `vault/Vault.kt:526-536`: cambiar la maestra crea una clave de datos nueva y lo vuelve a cifrar todo, historial y papelera incluidos.
- `crypto/Crypto.kt:62-77` y `:230-247`: `checkSane` comprueba primero los límites y después lo que cabe (fila 38, n-1); Argon2id sigue la RFC 9106 v0x13 con una sal de 16 bytes; la salida se borra.
- `crypto/Crypto.kt:90` y `:99-100`: el texto de «Alto» sale también con 64 MiB (I-3, veracidad).
- `vault/VaultStore.kt:582`, `:593`, `:682` y `:710`: la doble comprobación de i-6. La ventana que queda solo escribe con la propia clave (fila 31).
- `vault/BackupCheck.kt:194-219` y `vault/VaultLimits.kt:209-236`: falta el tope de tamaño de una copia v4 y la longitud de los nombres de carpetas (m-2, m-4). Las copias hostiles son las de la decisión (c).
- Solo `javax.crypto` y Bouncy Castle (Argon2), sin primitivas nuevas en el alcance. La comparación del nombre al borrar usa `compareKey`, que no es un secreto.

## Revisión Ingeniero Móvil Senior

- `vault/VaultStore.kt:465`, `:486` y `:500`: `commit`/`opened` corren en el *dispatcher* de quien llama; el orden de las entradas se hace en el hilo principal (m-3).
- `vault/VaultStore.kt:840-845` y `:408`: codificar, cifrar y escribir van en `compute` e `io`; `KdfLock` va primero el mutex y después el cerrojo (sin interbloqueo entre bóvedas: cada `VaultStore` tiene su mutex y nadie toma dos).
- `AppContainer.kt:545-568`: `switchTo` con `NonCancellable`, `awaitIdle`, la época de la app y `setLastOpened` a prueba de fallos.
- `AppContainer.kt:621-641` y `:677-716`: crear y borrar se deshacen bien. Una muerte del proceso deja como mucho un hueco vacío o unos ajustes huérfanos, que nunca se leen.
- `core/AtomicFile.kt:15-23`: escritura atómica con `fsync`.
- `settings/Settings.kt:423`, `:443` y `ui/screens/SettingsScreen.kt:649`: «Alto» solo con `heap/2 ≥ 128 MiB` y con la bóveda abierta. Esto último es lo que hace sin salida a I-2.
- `android/BiometricVault.kt`: un alias y unas preferencias por bóveda, y la información de StrongBox, común. Un archivo de preferencias vacío se queda después de borrar (i-3).
- Una fila «Móvil» nueva: medir el OOM de I-2 y I-3 con «Alto» y una bóveda grande (en «Lo que no pude comprobar»).

## Textos para el Abogado Digital (revisión final)

- **I-1:** con el arreglo, el diálogo de A6 no sale si se cambió de bóveda durante el cambio de maestra. ¿Lo dice el *snackbar* («Contraseña maestra cambiada…») o la política? Las copias de antes siguen bajo la contraseña anterior.
- **I-2 (B):** el aviso nuevo, por ejemplo: «No se pudo subir el coste a «Alto»: no hay memoria suficiente ahora. La bóveda sigue con el coste de antes. Puedes elegir «Estándar» en Ajustes.»
- **I-3:** `KDF_OUT_OF_MEMORY` sin «Alto» cuando el coste no es ese, y la constante nueva `KDF_NO_MEMORY_NOW`, por ejemplo: «Este móvil no tiene ahora memoria suficiente para abrir esto. Cierra otras apps y vuelve a intentarlo.»
- **m-1:** si los lugares de los selectores son de toda la app o de cada bóveda, el texto de la política («Al borrar la bóveda se olvidan todas», O-10.7).
