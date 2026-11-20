# Auditoría completa de Clavero 2.0, parte E: el diff de la ronda única de arreglos de las revisiones

**Qué se audita:** `git diff 32af38f..69c967b`, la ronda única de arreglos de las revisiones (CR-1 a CR-7, I-1 a I-3), en la rama `ccr-0aa27f95-u038cd`.
- Commits de código: `77e6a51` (CR-1), `9b6bbc4` (CR-7), `c4e8f0f` (CR-2), `f8e1845` (CR-3), `7879cea` (CR-4), `6a568f3` (CR-5 e I-1), `5e62ac2` (CR-6), `d5c167e` (I-2 e I-3) y `69c967b` (el repaso del cierre). Los demás (`ffa442f`, `9e3cfed`, `5b50e60`) solo tocan documentos.
- Al escribir esto, el HEAD es `7a8ec70`. `git diff --stat 69c967b..HEAD -- app jvm-check` sale vacío: el código es el de `69c967b`, y cada `archivo:línea` de este informe es de ese código.
- 31 archivos de `app/` (20 de `src/main`, 11 de tests); `jvm-check` compila las mismas fuentes.

**Encargo:** `sdd-2.0/advisor-t12-review-fixes.md`, «Encargo» (puntos 1 a 9) y «Encargo de la auditoría completa a fondo» (líneas 237–293), parte E: una `security-review` y un `code-review` en nivel alto de ese diff. Informe del implementador: `sdd-2.0/review-fixes-report.md`. Hallazgos de partida: `sdd-2.0/code-review.md`.

**Solo lectura:** no cambié código, no hice commits y no ejecuté Gradle. Usé `git` y `grep`, y solo escribí este archivo. Los métodos de `code-review` y `security-review` los apliqué yo, sin lanzar sus procesos.

**Leído antes:**
- `CLAUDE.md`;
- `experto-seguridad-cripto`: `SKILL.md` y `references/` (auditoria.md, cripto-moderna.md y criptoanalisis.md; la última no aplica al diff);
- `desarrollo-movil-senior`;
- el diseño de la 2.0;
- `progress.md` (las últimas 40 líneas y los «Ruling» y «Owner decision» de la ronda);
- `pendientes.md` (filas 51, 62, 67, 69 y 71 a 80);
- `final-audit-c.md`;
- el parte del 20-11.

**Riesgos con nombre (filas 51, 62, 67 y 69):** no los reabro.
- El diff no toca la lectura de una copia (fila 67) ni el guardado de «Importar de otra app» (fila 69).
- Tampoco toca la densidad de la lista (filas 51 y 62): según el informe del implementador, `RedTeam20ListCostTest` da las mismas cifras.
- El trabajo nuevo de la vista previa va dentro de `previewEstimateOrNoMemory` (`imports/ImportMemory.kt:26-38`).

## Alcance y modelo de amenaza

- **Qué entra:** cada línea del diff de `app/src/main` y de los tests cambiados. Además, el código que el diff llama o del que depende para ser correcto:
  - `VaultStore.unlock`, `opened`, `lock` y `completeUpgradeLocked`;
  - `AutoBackup` (el colector, `copyBeforeUpgrade` y `copyBeforeNow`);
  - `VaultLimits` (`fittedForSave`, `entryLimitProblem` y `jsonBytes`);
  - `VaultCrypto.unlock` y `read`;
  - `Selection.changing`;
  - las pantallas que llaman a lo cambiado.
- **Atacantes:** los de la decisión (f), más el aislamiento entre bóvedas y la subida desde la 1.4.3. Para este diff pesan sobre todo:
  - quien tenga el móvil desbloqueado y la app abierta, frente a borrar una bóveda (CR-7) y las comprobaciones de la maestra;
  - una copia o un archivo importados hostiles, frente a los topes de la vista previa (CR-5) y de una entrada (CR-1);
  - una carpeta compartida lenta u hostil, frente al orden de las copias (CR-2);
  - el bloqueo, un cambio de bóveda y la muerte del proceso, a mitad de la copia de la subida (CR-2) y del borrado (CR-7);
  - la memoria (`OutOfMemoryError`), en CR-5 y CR-7.
- **Lo que no entra:**
  - `AutoBackup.kt` entero y la migración de `SettingsStore` (parte D);
  - la criptografía y los formatos fuera del diff (parte A);
  - MASVS completo y el APK (parte B).

## Resumen

| Severidad | Hallazgo | Ubicación |
|---|---|---|
| Baja (corrección; no es una vulnerabilidad) | E-1. La marca congelada de CR-1 suma unos 34 B a una entrada anterior a la 2.0 en cualquier edición que no cambia la contraseña. Si la entrada está a menos de 34 B del tope de 64 KiB, sale `ENTRY_TOO_BIG` donde antes se guardaba. Es justo una condición de parada del encargo, que el implementador dejó sin comprobar. | `vault/EntryHistory.kt:33-34`, `:48`; `vault/VaultLimits.kt:98`, `:207`, `:226` |
| Informativa | E-2. CR-2 mira la espera una sola vez, antes de pedir `lock`. Una copia que ya esperaba `lock` cuando empieza la copia de una apertura nueva va antes que esa copia. Pasa con un bloqueo y un desbloqueo durante una copia lenta, y es más ancho que la ventana de `launchDue` que apuntó el implementador. | `vault/AutoBackup.kt:485-486`, `:525-526`, `:749` |
| Informativa | E-3. Tres textos de código siguen diciendo algo falso tras la ronda. El KDoc de `deleteVault` dice que la bóveda «se abre» para borrarla. Los KDoc de `ContactsFrame` y `MasterPrompt` dicen que escribir la maestra una vez la pasa a la versión 3. Además, `ContactsGate.Master` y `MasterPrompt` ya no se pueden alcanzar, y `NEEDS_MASTER_NOTICE` sería falso si se viera. | `AppContainer.kt:703-708`; `ui/screens/ContactsScreen.kt:80-81`, `:130-131`; `vault/ContactsView.kt:64`, `:68-69` |

**`security-review`:** ninguna vulnerabilidad con una confianza de 0,8 o más, ni introducida ni sin cerrar por el diff. Lo de menos confianza está en §1.3, como «a comprobar».

**`code-review` (alto):** los nueve puntos del encargo están hechos, con los matices de §2.1. Hallazgos: E-1 (Baja), E-2 y E-3 (Informativas). Nada Crítico, Alto ni Medio.

## 1. `security-review` del diff

### 1.1 Método

Para cada cambio del diff seguí el dato desde la entrada hasta el sumidero, línea a línea:
- la maestra y la clave en CR-7;
- el nombre de la bóveda en CR-7;
- las entradas y la papelera en la vista previa (CR-5);
- el mensaje de una excepción hasta la pantalla (CR-6);
- el estado de la subida hasta las copias y el borrado de A6 (CR-2);
- las carpetas en CR-4.

Para cada uno busqué:
- secretos que salgan (registros, textos, `Bundle`, `rememberSaveable`);
- claves sin poner a cero;
- errores que conceden acceso (*fail-open*) o que se tragan;
- comprobaciones separadas de su uso (TOCTOU);
- oráculos nuevos;
- trabajo sin tope sobre entradas hostiles;
- cambios de formato, de AAD, de coste o de permisos.

Solo reporto como vulnerabilidad lo que tiene una confianza de 0,8 o más.

### 1.2 Resultado por zona

- **CR-7, borrar una bóveda bloqueada** (`VaultStore.kt:787-803`, `AppContainer.kt:727-734`):
  - **Las claves.** La KEK se pone a cero dentro de `VaultCrypto.unlock`, en un `finally` (`vault/Vault.kt:440-444`). La DEK se pone a cero en el `finally` del método nuevo (`VaultStore.kt:799-801`), también cuando sale por `!active()`, por `CryptoFailure` o por `OutOfMemoryError`. El JSON descifrado, en el `finally` de `read` (`Vault.kt:477`).
  - **Concurrencia.** Va bajo el `mutex` y `NonCancellable`, con la derivación en `KdfLock` (`VaultStore.kt:424`), en el orden de cerrojos de R38.
  - **Bóveda activa.** Mira `active()` antes y después de Argon2id (`:790`, `:793`).
  - **Sin efectos.** No toca `_state`, `onClockReset`, la papelera, el envoltorio ni `upgrades`, y no hay ningún `commit` ni `save`.
  - **Errores.** El fallo es cerrado:
    - `null` da `LOCKED`;
    - una maestra equivocada deja pasar `CryptoFailure` y no se borra nada;
    - sin memoria da `VAULT_NO_MEMORY`;
    - un nombre que no es el suyo da `WRONG_NAME` en `AppContainer.kt:734`, antes del bloque que borra, que empieza en `:736`.
  - **Ningún oráculo nuevo.** Una maestra correcta con el nombre mal se distinguía igual con el `unlock` de antes, y no había límite de intentos ni antes ni ahora.
  - **Una ventaja de seguridad.** Al no abrir la bóveda, no se escriben copias en carpetas compartidas de una bóveda que se va a borrar. Antes la subida dejaba una copia `seguridad-antes-de-2-0-` fuera de la lista (`code-review.md`, CR-7).
- **CR-2, la copia de la subida va primero** (`PendingUpgrade.kt:35-57`, `:66`, `:81-105`; `AutoBackup.kt:485`, `:525`):
  - **A6.** No se amplía lo que borra: el diff no toca su código, que solo borra los `replaceTargets` congelados al pedirlo. La copia de la subida se apunta con `onImportCopy` (`AutoBackup.kt:773`) y no entra en esa lista.
  - **Lo comprueba un test.** `UpgradeCopyFirstTest.theUpgradeCopyGoesFirstAndTheDueCopiesWaitForIt`: la copia de la subida sigue en la carpeta y no cuenta como «sin apuntar».
  - **La espera termina:**
    - `_copying` solo se escribe con `opening` tomado (`PendingUpgrade.kt:56`, `:66`, `:103`);
    - un bloqueo lo pone en falso con `drop` (`VaultStore.kt:816-821`);
    - el `finally` de `afterUnlock` lo pone en falso en cuanto acaba la apertura a la que pertenece.
  - **E-2** es solo de orden y no expone nada.
- **CR-6, errores en pantalla:**
  - `storeSaveErrorText` (`VaultStore.kt:1757-1765`) solo deja pasar el texto de `VaultLimitExceeded` y de `OrganizeRefused`, que siempre son constantes. Lo comprobé con un `grep` de sus constructores en `app/src/main`: solo `Folders.kt:138-139` pasa parámetros, y son constantes.
  - De una `IllegalStateException` solo pasan cuatro textos fijos. Lo demás da `fallback` y nunca el mensaje de la excepción.
  - No hay registros nuevos en el diff (`grep` de `Log.`, `println`, `printStackTrace` y `.message` en las líneas añadidas de `app/src/main`).
  - «PIN y códigos» sigue sin registrar nada (`CodesScreen.kt:192-194`).
- **CR-5 e I-1, la vista previa:**
  - La instantánea sale de una sola sección del `mutex` (`VaultStore.kt:1017-1022`).
  - La papelera (con secretos) solo vive en la sesión del ViewModel (`ImportSession.kt:152-154`). Se borra en `dropAll` (`:463`), al que llega un bloqueo por `vaultStateIs(false)` (`:439-441`). No va a ningún `Bundle` ni a `rememberSaveable`.
  - **Entradas hostiles:**
    - el tope bajo se cuenta sin construir nada (`MergePlan.kt:422`);
    - la medida exacta cuenta sin construir la cadena (`jsonBytes` con `CountingStream`, `VaultLimits.kt:88-90`), mejor que el `encodeToString` de antes (`MergePlan.kt:400`);
    - todo va dentro del envoltorio contra la falta de memoria.
  - **I-1:** «Importar» nunca está activo con `IMPORT_NO_MEMORY` a la vista (`ImportSession.kt:326`, `:338`).
- **CR-1, la marca:**
  - No cambia ningún formato: el campo ya existía (`Vault.kt:121`) y los nulos no se escriben (`core/Json.kt:9`).
  - No escribe nada en una v2 ni en una v3, porque con la subida pendiente se rechaza cualquier guardado (`VaultStore.kt:897`).
  - La marca no crea versiones (`EntryHistory.kt:24-25`).
  - E-1 es de corrección.
- **CR-3 y CR-4:**
  - `contactsGate` es una función pura (`ContactsView.kt:60-65`).
  - `withoutFolder` rechaza antes de cambiar nada (`Folders.kt:209-217`), y el rechazo llega a la pantalla como texto fijo (`VaultOrganize.kt:378`).
- **Lo que no se mueve:** `git diff --stat 32af38f..69c967b` sale vacío para:
  - `crypto/`, `vault/Backup.kt` y `vault/BackupCheck.kt`;
  - `AndroidManifest.xml`, `app/build.gradle.kts`, `gradle/` y `res/`.

  En `vault/Vault.kt` solo cambian 5 líneas de KDoc. No hay ninguna dependencia, permiso, host, AAD, coste ni marca de copia nuevos.

### 1.3 A comprobar (confianza por debajo de 0,8; ninguno es una vulnerabilidad)

- **AC-E1. Una bóveda colgada en la subida tras un `Error`.**
  - Si `saveBookkeeping` se queda sin memoria al codificar una bóveda de unos 24 MiB, el `OutOfMemoryError` no lo captura `completeUpgradeLocked` (`VaultStore.kt:670-674`, que solo captura `Exception`).
  - Sube por `afterUnlock` (el `finally` pone `copying` en falso, `PendingUpgrade.kt:103`) hasta quien llamó a `unlock`.
  - Queda la bóveda abierta con la subida pendiente, sin `asking` y sin `copying`. Los guardados dicen `UPGRADE_COPYING`, que entonces es falso, y no sale el aviso.
  - Lo más probable es que la app se cierre, y al morir el proceso todo queda coherente: el archivo sigue siendo la v3.
  - **A comprobar** en el móvil, con la fila 51. Es la ventana del `Error` que ya apuntó el implementador; no la reabro.
- **AC-E2. Secretos en memoria para leer solo el nombre.**
  - `checkMasterToDelete` descifra y decodifica todos los datos para leer solo `name`.
  - `VaultCrypto.read` crea el JSON entero como un `String` que no se puede borrar (`Vault.kt:465`, CWE-316) y todos los objetos de las entradas.
  - Es igual que el `unlock` al que sustituye, o mejor, porque entonces se quedaban en `_state`.
  - **Endurecimiento opcional:** leer el nombre con un deserializador de un solo campo, con `ignoreUnknownKeys`, para no crear las entradas. No es un hallazgo.

## 2. `code-review` (alto) del diff

### 2.1 ¿Cumple cada arreglo el encargo del director?

| Punto | ¿Cumple? | Evidencia y matices |
|---|---|---|
| CR-1 | Sí, con E-1 | `frozenPasswordMark` con la fórmula exacta (`EntryHistory.kt:33-34`). Se usa en `withVersion` (`:48`), `withoutHistory` (`:91`), los adjuntos (`VaultStore.kt:1375`, `:1388`) y `duplicate` (`:991`). `comparable()` lo pone a nulo (`EntryHistory.kt:24-25`). KDoc al día (`Vault.kt:114-120`). El `grep` está en el informe y los tests en `PasswordMarkTest`. Verifiqué: el fixture `e1` es «Favorita», así que `setFavorite(…, false)` de `aRealVaultOf143KeepsItsDate` sí guarda. E-1 es la duda que el implementador dejó abierta y que el encargo lista como parada. |
| CR-7 | Sí, con E-3 | El método hace todo lo que pide el encargo (§1.2). `deleteVault` lo usa para toda bóveda bloqueada (`AppContainer.kt:727-732`). El nombre se compara igual que antes (`:734`): para una v2 o v3, `read` da `name = ""` y de ahí «Bóveda N», como daba la subida (`Vault.kt:356-366`, `VaultRegistry.kt:49-50`). El KDoc de `deleteVault` no se puso al día (E-3). |
| CR-2 | Sí, con E-2 | `copying` sigue la regla del encargo, y en falso va en el `finally` (`PendingUpgrade.kt:100-104`), que cubre `asking`, una copia que falla y un guardado que falla. La espera está en los dos sitios, antes de `lock` (`AutoBackup.kt:485`, `:525`), y `copyBeforeUpgrade` no espera (`:749`). El texto es palabra por palabra el del encargo (`VaultStore.kt:1748-1749`). Un solo ayudante en los cuatro sitios (`:897`, `:1408`, `:1495`; `importReplace` pasa por `checkCanReplace`). Está en la lista de `storeSaveErrorText` (`:1762`). La espera de `runChanged` no tiene rojo propio (ya apuntado). |
| CR-3 | Sí en el código; no del todo en el KDoc | `contactsGate` (`ContactsView.kt:60-65`) y la pantalla (`ContactsScreen.kt:96-99`, `:120-124`) están bien. El KDoc de `MasterPrompt` (`:130-131`) y el de `ContactsFrame` (`:80-81`) siguen con la idea que CR-3 desmontó (E-3). |
| CR-4 | Sí | La regla del padre efectivo es la de `checkFreeName` (`Folders.kt:143-149` frente a `:212-213`), con `organizeKey`. La carpeta que se borra y sus hijas quedan fuera del conjunto de comparación. Rechaza antes de cambiar nada (`:214-216`). El texto es palabra por palabra el del encargo (`:39-40`). |
| CR-5 e I-1 | Sí, con la desviación declarada | `VaultSnapshot.rest` sale de la misma sección (`VaultStore.kt:1017-1022`). Se mide con los dos topes de `applyImport` (`MergePlan.kt:421-426`, frente a `VaultStore.kt:1065-1083`). `PREVIEW_UNSEEN_BYTES` (`MergePlan.kt:438`) cubre los campos de `VaultData` que quedan fuera: la etiqueta, de 32 hex por la regla de la fila 63, dos `Long`, un booleano y un `Int`, unos 180 B en el peor caso (`VaultData.kt:18-32`). I-1 son dos líneas (`ImportSession.kt:326`, `:338`). Desviación: el diferencial de 200 casos va con bóvedas pequeñas, más 4 casos en los topes reales; vale para la desigualdad «la vista previa ≥ el guardado». Los adjuntos siguen sin modelarse (conocido, fuera del encargo). |
| CR-6 | Sí | Los 9 sitios y `saveFailureMessage` (`ContactsView.kt:470-475`) están. Repetí el barrido a mano, con otra heurística: cada llamada de guardado del almacén en `ui/` y sus 14 líneas siguientes. Los sitios sin `storeSaveErrorText` cerca son la subida (`VaultUpgrade.kt:94`, con su texto), «Contactos» (`IdentityScreen.kt:131`, `:147`, por `saveFailureMessage`), las lambdas de «Organizar» (`VaultOrganize.kt:147-193`, por su ayudante) y los bloques de la papelera (`TrashScreen.kt:119-155`, por `:89`). Ninguno esconde `UPGRADE_*`. `changeMaster` está arreglado (`VaultBackup.kt:166-168`), pero el test de fuente no lo fija (su `try` está anidado), como dice el informe. |
| I-2 e I-3 | Sí | Son solo tests (`RedTeam20Recheck2Test.kt:251-252`, `:600-604`). |
| Tests cambiados | Sí | Los autorizados, más dos con su motivo. `deleteNeedsMasterAndName` (`AppContainerVaultsTest.kt:617-621`) afirmaba el defecto de CR-7 y está fuera de `vault/`, `imports/` y `redteam/`, así que no es una parada. El ancla de R16 (`RedTeam20Recheck3Test.kt:245`) cambia por la firma, que es lo que permite el encargo. |

### 2.2 Concurrencia y ciclo de vida, comprobados

- **Siempre se llega a `afterUnlock`.**
  - `unlock` vuelve de `mutex.withLock { withContext(NonCancellable) { … } }` (`VaultStore.kt:586`).
  - `withContext(NonCancellable)` no cambia de *dispatcher*, así que al volver no hay cancelación inmediata, y `withLock` no comprueba la cancelación al salir.
  - Una apertura que publicó `Unlocked` con la subida pendiente siempre llega a `afterUnlock`, aunque quien llamó se haya cancelado. `copying` nunca se queda en verdadero por eso.
- **Ningún bloqueo mutuo.**
  - Nadie tiene `AutoBackup.lock` mientras espera `upgradeCopying`.
  - `afterUnlock` corre con el `mutex` del almacén libre (`VaultStore.kt:584-595`).
  - `export` toma el `mutex` solo para la instantánea.
- **Un ámbito cancelado al borrar la bóveda.** `copyBeforeUpgrade` (`scope.async`, `AutoBackup.kt:749`) lanza `CancellationException`, que es una `Exception`, y `PendingUpgrade.kt:84-88` la convierte en `Failed`. El `finally` cierra la espera. Lo cubre `theWaitEndsWithTheScopeCancelled`.
- **Una bóveda a la vez.** Cada bóveda tiene su `AutoBackup` y su `PendingUpgrade` (`AppContainer.kt:407-460`): una espera de una bóveda nunca frena las copias de otra.
- **Ningún estado nuevo se guarda.** El diff no añade nada a `rememberSaveable` ni a un `Bundle`. `failed` de la ficha es `remember` (`VaultEntryScreen.kt:214`). Desde la 1.4.2, `MainActivity` declara `configChanges` para el giro.

## Hallazgos

### [Baja] E-1. La marca congelada de CR-1 hace saltar el tope de 64 KiB en entradas de la 1.4.3 que estaban por debajo

- **Severidad:** Baja. Es de corrección, no una vulnerabilidad: no expone ni pierde datos, y la pantalla dice un texto fijo y verdadero según la medida. Es exactamente la condición de parada «CR-1 … hace fallar un tope de una entrada que ya existía» del encargo. El implementador la dejó abierta en «Riesgos o dudas» («no miré si `entryLimitProblem` cuenta la marca congelada»), y el parte la pasa al director.
- **CVSS 3.1:** no es explotable por un tercero. Para cumplir el formato, como pérdida de disponibilidad de una edición: `AV:L/AC:H/PR:N/UI:R/S:U/C:N/I:N/A:L`, 2.5.
- **MASVS v2:** no corresponde a ningún control; el más cercano es MASVS-CODE-4 (validación coherente de los límites).
- **CWE:** CWE-682 (cálculo incorrecto: el tope cuenta un campo que pone el propio almacén).
- **Dónde:**
  - `vault/EntryHistory.kt:33-34` (`frozenPasswordMark`) y `:48` (`withVersion` congela la marca en toda edición que no cambia la contraseña);
  - `VaultStore.kt:1375` y `:1388` (los adjuntos);
  - `vault/VaultLimits.kt:98`: `bytesWithoutHistory` mide la entrada con `passwordChangedAt`. Con `explicitNulls = false` (`core/Json.kt:9`), un nulo no se escribe, y una marca son `,"passwordChangedAt":1700000000000`, 34 B;
  - `VaultLimits.kt:207`: `fittedForSave` comprueba los topes de toda entrada que no es el mismo objeto ni cambió solo de sitio;
  - `VaultLimits.kt:226`: `> ENTRY_MAX_JSON_BYTES`, con `ENTRY_TOO_BIG`.
- **Qué pasa:**
  - La 1.4.3 no tenía tope por entrada (`VaultLimits.kt` no existe en `main`; KDoc de `fittedForSave`, `:190-195`).
  - Antes de la ronda, una edición que no cambiaba la contraseña de una entrada anterior a la 2.0 copiaba la marca nula. Ahora escribe la fecha congelada, y la entrada crece 34 B.
  - Una entrada de la 1.4.3 cuyo JSON sin historial está entre 65 503 y 65 536 B se guardaba antes y ahora da `VaultLimitExceeded(ENTRY_TOO_BIG)`. Pasa con una edición del mismo tamaño en el editor (`VaultEntryScreen.kt:541` siempre pone `updatedAt` y pasa por `withVersion`), al quitar «Favorita» (-16 B + 34 B), al quitar un adjunto pequeño y con «Reemplazar» de «Importar de otra app», que da `EntryTooBig` (`VaultStore.kt:1074`).
  - «Borrar todo el historial» (`VaultStore.kt:1336-1350`) es todo o nada. Una entrada con historia, sin lista de 1.4.3 y sin marca, en esa franja, hace que no se borre nada. Las de la 1.4.3 tienen siempre la lista cuando tienen historia (`Vault.kt:356-364`), así que solo afecta a copias hechas a mano.
- **Impacto:** el usuario no puede marcar, etiquetar ni editar sin cambiar la contraseña una entrada antigua en esa franja de 34 B hasta que acorte sus notas, aunque no la haya hecho más grande. No se pierde nada.
- **Corrección exacta:** que la marca del propio almacén, acotada a 34 B, no cuente en el tope de una entrada. Sigue contando en los 24 MiB de la bóveda, que mide `jsonBytes(data)`.
  ```kotlin
  // vault/VaultLimits.kt:98
  private fun VaultEntry.bytesWithoutHistory(): Long =
      jsonBytes(VaultEntry.serializer(), if (history.isEmpty() && passwordChangedAt == null) this else copy(history = emptyList(), passwordChangedAt = null))
  ```
  - `entryLimitProblem` es la única función del tope de una entrada, y la usan el guardado, `applyImport` y la comprobación de copias. La app sigue aceptando todas sus propias copias.
  - KDoc de `ENTRY_MAX_JSON_BYTES`: «sin el historial ni la marca de la última contraseña».
  - Alternativa, si el director prefiere no tocar la medida: aceptar la franja como coste conocido y apuntarlo en `pendientes.md` y en el KDoc de `frozenPasswordMark`.
- **Prueba de concepto (test, en rojo hoy):**
  1. `PasswordMarkTest.anEntryOf143JustUnderTheEntryCapStillTakesAnEditThatKeepsItsPassword`:
     1. un `login()` sin marca, con notas tales que `jsonBytes(VaultEntry.serializer(), e) == VaultLimits.ENTRY_MAX_JSON_BYTES - 10`;
     2. `withEntries(e)`;
     3. `s.upsert(s.entry("e").copy(notes = notes.dropLast(1) + "y"))`, del mismo tamaño.
     - Hoy: `VaultLimitExceeded(ENTRY_TOO_BIG)`. Tras el arreglo: `true`, y `lastPasswordChange` igual que antes.
  2. La misma entrada con `favorite = true` y `setFavorite(setOf("e"), false)`: hoy, `ENTRY_TOO_BIG`.
  3. `aCopyWithAnEntryAtTheCapAndItsMarkIsAccepted`: la app exporta una bóveda con esa entrada ya marcada, y «Comprobar una copia» e «Importar copia» la aceptan. Es la regla de que la app nunca rechaza sus propias copias.

### [Informativa] E-2. La espera de CR-2 se mira una vez antes de pedir `lock`, y no después de tomarlo

- **Severidad:** Informativa. Solo es de orden: no expone datos, no pierde ninguna copia y A6 no puede borrar la copia de la subida, porque cuando corre todavía no existe.
- **CVSS 3.1:** `AV:L/AC:H/PR:N/UI:R/S:U/C:N/I:N/A:N`, 0.0.
- **MASVS v2:** no aplica (es el orden de las copias, sin datos expuestos).
- **CWE:** CWE-362 (condición de carrera, aquí sin efecto de seguridad).
- **Dónde:** `vault/AutoBackup.kt:485-486` (`runChanged`) y `:525-526` (`runDue`), frente a `:749` (`copyBeforeUpgrade`, que pide el mismo `lock`, un `Mutex` justo, por orden de llegada, `:448`).
- **Qué pasa:**
  1. Con una carpeta lenta, la copia de la apertura 1 (A) tiene `lock`.
  2. El usuario bloquea: `drop` pone `copying` en falso (`VaultStore.kt:821`).
  3. El `runDue(afterUnlock = true)` de la apertura 1, que esperaba en `first { !it }`, pasa y se pone en la cola de `lock`.
  4. El usuario vuelve a abrir con la maestra: la apertura 2 pone `copying` en verdadero (`PendingUpgrade.kt:56`) y su `copyBeforeUpgrade` se pone en la cola detrás.
  5. Cuando A termina, el `runDue` viejo toma `lock` con la bóveda abierta otra vez (la comprobación de estado de `:540` pasa). Escribe la copia automática del archivo v3 y hace A6 antes que la copia `seguridad-antes-de-2-0-` de la apertura 2.
  6. Mientras tanto, los guardados dicen `UPGRADE_COPYING`, que sigue siendo verdadero en lo esencial: hay copias en marcha.
- **Por qué va aparte:** el implementador apuntó la variante de `launchDue` justo antes de un desbloqueo («Para el director», `review-fixes-report.md:376`). Esa ventana es de microsegundos: con la bóveda bloqueada, `runDue` toma y suelta `lock` enseguida (`AutoBackup.kt:540`). Esta otra dura lo que tarde la copia lenta, y nace de la misma causa.
- **Corrección exacta:** volver a mirar con `lock` tomado y soltarlo si una apertura nueva empezó su copia, porque `copyBeforeUpgrade` lo necesita.
  ```kotlin
  /** CR-2: [lock], tomado solo cuando no corre ninguna copia de antes de la subida (mirado otra vez ya con él). */
  private suspend fun lockAfterUpgradeCopy() {
      while (true) {
          store.upgradeCopying.first { !it }
          lock.lock()
          if (!store.upgradeCopying.value) return
          lock.unlock()
      }
  }
  ```
  En `runChanged` y `runDue`: `lockAfterUpgradeCopy(); try { … } finally { lock.unlock() }`, en lugar de `first {}` más `lock.withLock`. El riesgo que queda es que `copying` pase a verdadero justo después de esa comprobación. Se cierra mirando `store.upgradeCopying.value` antes de cada `attempt` del bucle de destinos y dejando el resto para la pasada siguiente. Lo decide el director; mientras tanto basta con apuntarlo en `pendientes.md` como ventana conocida.
- **Prueba de concepto (test, en rojo hoy):** `UpgradeCopyFirstTest.aCopyQueuedOnTheLockBeforeAReopenWaitsForTheNewUpgradeCopy`.
  1. `setup(t, a6 = listOf(old))`, con `t.gate` cerrado.
  2. `unlock` 1 y `runCurrent()`: la copia A queda parada en `write`.
  3. `s.lock()` y `runCurrent()`: el `runDue` viejo se pone en la cola de `lock`.
  4. `unlock` 2 con la maestra y `runCurrent()`: `copying` es verdadero.
  5. Se abre la puerta y `runCurrent()`.
  6. Se afirma que `t.created[1]` empieza por `seguridad-antes-de-2-0-`. Hoy es `seguridad-auto-…`.
  - Con `withTimeout` y la regla de 30 s, como los demás de la clase.

### [Informativa] E-3. Textos de código que siguen diciendo algo falso tras la ronda

- **Severidad:** Informativa (la regla de veracidad del proyecto, aplicada al código).
- **CVSS 3.1:** `AV:L/AC:H/PR:N/UI:R/S:U/C:N/I:N/A:N`, 0.0.
- **MASVS v2:** no aplica.
- **CWE:** CWE-1116 (comentarios inexactos).
- **Dónde y qué:**
  1. **`AppContainer.kt:703-708`, KDoc de `deleteVault`:**
     - «It is opened with [master] … and so shows its name». Desde CR-7, una bóveda bloqueada no se abre: solo se comprueba su maestra (`:731`) y su nombre no se enseña.
     - «nothing is deleted … not when … the vault locked meanwhile». Una bóveda que se bloqueó entre el diálogo y la confirmación se borra si la maestra y el nombre son los suyos (`:731-734`). Ya pasaba antes, al abrirla.
     - Lo que sí da `LOCKED` es la carrera de la rama abierta (`:728-729`) o una bóveda que ya no está activa (`VaultStore.kt:790`, `:793`).
  2. **`ContactsScreen.kt:130-131`, KDoc de `MasterPrompt`:** «one master password check wraps it as version 3 and the screens show up». `confirmMaster` solo comprueba la maestra y nunca envuelve la bóveda como una 3 (`VaultStore.kt:770-777`). Es la premisa que desmontó CR-3, y el encargo pedía poner al día este KDoc.
  3. **`ContactsScreen.kt:80-81`, KDoc de `ContactsFrame`:** «if it is still version 2, after the master password was typed once ([MasterPrompt])». Escribirla no cambia nada.
  4. **`ContactsGate.Master` (`ContactsView.kt:64`) y `MasterPrompt` ya no se pueden alcanzar en la 2.0.**
     - `needsMaster` exige que el almacén tenga una v2 (`VaultStore.kt:1115-1116`, `:336`).
     - Con la bóveda abierta, eso solo pasa con la subida pendiente: `opened(v, …, upgraded = checked)` en `:640` deja `copying` o `asking`.
     - `saved()` pasa a la v4 en el mismo bloque (`:675-679`).
     - `drop()` solo ocurre al bloquear (`:821`) o en una apertura cortada (`:526`).
     - La huella no abre una v2 (`BiometricRefusal.UPGRADE`, `Vault.kt:683`).
     - Así, `NEEDS_MASTER_NOTICE` (`ContactsView.kt:68-69`: «hace falta tu contraseña maestra, una sola vez. La bóveda se abrió con la huella.») sería falso si alguna vez se viera.
- **Corrección exacta:**
  1. `deleteVault`: «A locked vault is not opened: its master password is only checked ([VaultStore.checkMasterToDelete]), and [typedName] is compared with the name its data hold; an open one is checked with [VaultStore.confirmMaster]. LOCKED when the open vault locked during that check, or it is no longer the active one.»
  2. `MasterPrompt` y `ContactsFrame`: decir que en la 2.0 una v2 abierta siempre tiene la subida pendiente, así que la puerta enseña el texto de la subida. `MasterPrompt` queda solo como defensa.
  3. Hay dos opciones, y la elige el director:
     - quitar `ContactsGate.Master` y `MasterPrompt`, con `contactsGate` dando `Notice(UPGRADE_PENDING)` para `needsMaster` sin subida pendiente;
     - dejarlos y pasar `NEEDS_MASTER_NOTICE` al Abogado, con una nota de que hoy no se ve.
- **Prueba de concepto (test):**
  - `ContactsViewTest.theMasterGateIsNeverReachedWithAnOpenVault`:
    1. un almacén con `vault-1.1.json` (v2) y una carpeta lenta;
    2. se recoge cada valor de `state`, `needsMasterForContacts`, `pendingUpgrade` y `upgradeCopying` desde `unlock` con la maestra hasta guardar la v4, más un guardado de la subida que falla (`writeFile` que lanza);
    3. se afirma que, con `Unlocked`, `contactsGate(…)` nunca da `Master`.
  - Los KDoc no tienen un test útil; se arreglan en la revisión.

## Lo que está bien

- **CR-7 hace más que lo pedido:** una bóveda bloqueada se borra sin publicar `Unlocked`, sin la subida, sin copias, sin vaciar la papelera, sin volver a envolver la clave y sin bajar la marca del reloj de A11. La clave se pone a cero en un `finally` (`VaultStore.kt:787-803`).
- **CR-2 no amplía A6:** la copia de la subida va apuntada y fuera de la lista congelada. La espera aplaza las copias y no se las salta, porque la subida se guarda como «bookkeeping» y no sube `revision` (`VaultStore.kt:671`). Siempre termina (`PendingUpgrade.kt:100-104`).
- **`UPGRADE_COPYING` deja verdadero lo que dice la pantalla** mientras dura la copia (`VaultStore.kt:315`), también en «Contactos» (`ContactsView.kt:63`) y en «Importar copia» (`copyFailureText` deja pasar el texto, `BackupCheckTexts.kt:142-146`).
- **CR-1 no crea versiones falsas** (`EntryHistory.kt:24-25`). Conserva «Antigua» y el aviso de cambiarla (`Health.kt:72`, `Expiry.kt:125-127`, que solo cuentan entradas con contraseña). Restaurar una versión que solo difiere en la marca ya no guarda nada (`VaultStore.kt:1314`).
- **CR-5 mide igual que el guardado:** el mismo `fitted()` sobre `entradas fusionadas + papelera` con el resto de la bóveda (`MergePlan.kt:429`, frente a `VaultStore.kt:1079`). El tope bajo se cuenta con la papelera, más que el guardado, y nunca menos. Ninguna cadena del JSON entero se construye ya.
- **Las llamadas quedan ancladas:** el ancla de R16 fija que la pantalla pasa `snap.rest` (`RedTeam20Recheck3Test.kt:245`), y el valor por defecto de `onPlan` (`ImportSession.kt:247`) no puede abrir CR-5 sin que falle ese test.
- **CR-6:** un único ayudante con `fallback` (`VaultStore.kt:1757`) y un test de fuente que recorre `ui/` (`ErrorTextsTest.theScreensSayTheStoreTextsOfAFailedSave`).
- **Los tests nuevos son deterministas:** sin `sleep`, con `withTimeout` y una regla de 30 s (`UpgradeCopyFirstTest.kt:110`), y con `Random(20261120L)` (`VaultStoreImportTest.kt:232`). Los datos reales de la 1.4.3 (`vault-1.4.3.json`) están en CR-1, CR-2 y CR-7.
- **La privacidad del dueño en el rango:**
  - los 13 commits van como `Egt17baa <Egt17baa@users.noreply.github.com>`;
  - ningún nombre de modelo en las líneas añadidas ni en los mensajes (`grep -niw` con la tabla de la skill `advisor`);
  - ningún dato del dueño.

## Revisión experto-seguridad-cripto

- **Primitivas, formatos y coste:**
  - nada cambia: el diff no toca `crypto/`, `Backup.kt`, `BackupCheck.kt` ni el AAD de `Vault.kt`, del que solo cambia el KDoc de `passwordChangedAt` (`Vault.kt:114-120`);
  - Argon2id sigue con `checkSane` antes de derivar, también en el borrado (`Vault.kt:431-439`);
  - `SG1:`, `.sgenc`, `seguridad-vault` y `seguridad-backup` siguen igual.
- **La gestión de claves en CR-7:**
  - KEK a cero (`Vault.kt:440-444`), DEK a cero (`VaultStore.kt:799-801`) y JSON descifrado a cero (`Vault.kt:477`);
  - una sola derivación a la vez, con `KdfLock` (`VaultStore.kt:424`), sin cortarse si el llamador se cancela (BOV-3);
  - sin publicar la clave ni los datos.
  - Queda AC-E2 (el `String` del JSON entero en memoria), igual que antes o mejor.
- **Fallo cerrado:** el borrado solo sigue con la maestra y el nombre bien (`AppContainer.kt:727-736`). La vista previa nunca permite «Importar» con `IMPORT_NO_MEMORY` (`ImportSession.kt:338`). `storeSaveErrorText` nunca enseña lo que dijo una excepción (`VaultStore.kt:1757-1765`).
- **Integridad de una señal de seguridad:** CR-1 arregla que una edición sin importancia callara «Antigua» (CWE-1287 en `code-review.md`). E-1 es su efecto en el tope, de corrección.
- **Carreras:**
  - la espera de CR-2 es correcta en lo que dice el encargo;
  - E-2 es una ventana de orden, sin efecto de seguridad;
  - la instantánea de CR-5 sale de una sola sección del `mutex`, y el plan viejo se sigue rechazando por `revision` (R16, `VaultStore.kt:1053`).
- **Riesgos con nombre:** no se tocan (filas 51, 62, 67 y 69).

## Revisión Ingeniero Móvil Senior

- **Concurrencia:**
  - la espera es un `StateFlow.first {}` que se puede cancelar, fuera del `Mutex` (`AutoBackup.kt:485`, `:525`);
  - el `Mutex` es justo, lo que explica E-2;
  - `_copying` se escribe siempre con `opening` tomado (`PendingUpgrade.kt:56`, `:66`, `:103`);
  - no hay bloqueo mutuo (§2.2).
- **Cancelación:** `unlock` siempre llega a `afterUnlock` (`withContext(NonCancellable)` no cambia de *dispatcher*, `VaultStore.kt:586-594`). Un ámbito cancelado al borrar la bóveda termina la espera (`PendingUpgrade.kt:84-88`, `:100-104`).
- **Ciclo de vida:**
  - `ContactsFrame` lee los tres flujos con `collectAsStateWithLifecycle` (`ContactsScreen.kt:96-99`);
  - la decisión es pura y se prueba en jvm-check (`ContactsView.kt:60-65`);
  - la papelera de la vista previa vive en la sesión del ViewModel y se borra al bloquear (`ImportSession.kt:439-441`, `:453-463`);
  - no hay nada nuevo en `rememberSaveable`.
- **Rendimiento:**
  - por cada cambio de opciones, la vista previa hace un `fitted()` y una codificación que solo cuenta de toda la bóveda (`MergePlan.kt:421-429`), en `Dispatchers.Default` (`ImportScreen.kt:216`);
  - ese trabajo de CPU no se puede cortar: `collectLatest` descarta el resultado, pero no el trabajo;
  - con cambios rápidos pueden coincidir varias estimaciones; cada una retiene una copia ajustada de la lista, ya sin la cadena JSON de antes (`MergePlan.kt:400`);
  - se mide en el móvil con las filas 3 y 51; no es un hallazgo.
- **Interfaz:**
  - `failed` de la ficha pasa a ser texto, con `remember` (`VaultEntryScreen.kt:214`, `:244`);
  - «Renombrar» deja pasar la cancelación (`VaultPickerScreen.kt:349-350`);
  - los `import` nuevos van al principio del bloque (`CodesScreen.kt:3`, `TrashScreen.kt:3`, `VaultAttachments.kt:3`, `VaultBackup.kt:3`, `VaultPickerScreen.kt:3`), un detalle de estilo sin efecto.
- **Dos detalles menores, sin hallazgo:**
  - `onPlan(…, rest: VaultData = VaultData())` (`ImportSession.kt:247`): quitar el valor por defecto obligaría a todo llamador a pasar la papelera;
  - el KDoc de `passwordChangedAt` tiene una línea de más de 120 columnas (`Vault.kt:119`).

## Textos para el Abogado Digital (revisión final)

- **`UPGRADE_COPYING`** (`vault/VaultStore.kt:1748-1749`), nuevo: «Clavero está haciendo la copia automática de antes del cambio al formato nuevo. Espera a que termine e inténtalo otra vez.»
  - Sale en cualquier guardado, en «Contactos» (`ContactsView.kt:63`) y en «Importar copia» (`VaultStore.kt:1495`).
  - Matices de veracidad:
    - sin carpeta, puede salir un instante, antes de que se vea el aviso, sin que haya ninguna copia;
    - en la ventana de E-2, antes de la de la subida van otras copias automáticas.
- **`FOLDER_DELETE_NAME_TAKEN`** (`vault/Folders.kt:39-40`), nuevo: «Una de sus subcarpetas se llama igual que otra carpeta del sitio al que pasaría. Cambia antes uno de los dos nombres.» El diálogo también ofrece la opción de la papelera.
- **Textos que ya existían y que CR-6 lleva a más pantallas:**
  - `UPGRADE_PENDING`;
  - los topes de `VaultLimits.kt:60-73`, como `ENTRY_TOO_BIG` y `VAULT_NAME_TOO_LONG` en «Renombrar»;
  - los de «Organizar»;
  - en «Contactos», `NEEDS_MASTER_FOR_CONTACTS` y `ONLY_ONE_IDENTITY` (`VaultStore.kt:1767`, `:1779`), donde antes salía `NOT_SAVED_STATE`.
- **`ENTRY_TOO_BIG`** («La entrada pasa de 64 KB. Acorta sus notas o sus campos.», `VaultLimits.kt:61`): en la franja de E-1 sale aunque el usuario no haya hecho más grande la entrada.
- **`NEEDS_MASTER_NOTICE`** (`vault/ContactsView.kt:68-69`): sería falso en la 2.0 («una sola vez», «La bóveda se abrió con la huella») si alguna vez se viera. Hoy no se ve (E-3).
- **`VAULT_DELETE_LOCKED`** (`VaultsTexts.kt:61`): sigue siendo verdadero tras CR-7. Solo sale en la carrera de la bóveda abierta o con otra bóveda activa.
- **`FOLDER_DELETE_TO_NONE`** (`vault/OrganizeTexts.kt:60`, «Sus entradas y sus subcarpetas se quedan sin carpeta»): viene de la duda del implementador y no es de este diff. Las subcarpetas pasan a la raíz (`Folders.kt:219`), así que conviene un texto que lo diga.

Modelo: el director (esfuerzo extra).
