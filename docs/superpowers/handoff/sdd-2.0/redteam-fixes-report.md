# Clavero 2.0, arreglos del ataque final (RT-21 a RT-24): informe del implementador

Encargo: `docs/superpowers/handoff/sdd-2.0/advisor-t12-redteam-fixes.md`, «Encargo de la ronda». Base: `5e39b27`. Sin *push*.

## Estado
| Punto | Estado | Commit |
|---|---|---|
| 1. RT-23 | Hecho | f8f3f0d |
| 2. RT-22 (i) | Hecho | b2b8b0d |
| 3. RT-24 | Hecho | 3779726 |
| 4. RT-21 a | Hecho | 4c85961 |
| 5. RT-21 b | Hecho | 47fcec2 |
| 6. RT-21 c | Hecho | 78465ca |
| 7. RT-22 (ii) | Hecho (el dueño dijo que sí) | d7f81d1 |
| 8. Cierre | Hecho | (commit del cierre) |

## 1. RT-23 (f8f3f0d)
- Sin código. `rt23_aVaultImportedAsNewHasADataKeyOfItsOwn` (con `@Ignore`) pasa a `resistsAVaultImportedAsNewSharesTheCopysKeyOnlyUntilAMasterChanges`, sin `@Ignore`. Comprueba:
  - `keyA == keyB` justo después de importar;
  - los `vaultTag`, distintos;
  - tras `changeMaster` en B, la clave de B es otra, la maestra de A ya no abre B, y A abre con la suya y con su entrada `x` y su clave.
- Es un `resists…`: verde desde el principio, sin rojo (opción (a), el código no cambia). `jvm-check --tests RedTeam20VaultsTest`: 4 tests, 0 fallos, 1 omitido (RT-22).
- Diseño `docs/superpowers/specs/2026-11-12-clavero-2.0-design.md`: las dos notas, palabra por palabra.
  - Debajo de la tabla del modelo de amenaza (línea 50).
  - Como sub-viñeta bajo la línea del formato v4 (línea 65).

## 2. RT-22 (i) (b2b8b0d)
- `settings/Settings.kt`:
  - `settingsEditable(state, anyVaultFile)`: verdad con `Unlocked`, o con `NoVault` si ninguna bóveda del móvil tiene archivo;
  - `SETTINGS_NEED_AN_OPEN_VAULT`, con el texto del Ruling;
  - `settingsLockedText(state)`: `NoVault` da el texto nuevo, y lo demás `SETTINGS_NEED_OPEN_VAULT`, que no cambia.
- `AppContainer.kt`: `anyVaultFile()` es privada y la comparten `canAddVault()` y `settingsEditableNow()`, que es `suspend` y lee el disco en IO.
- `ui/screens/SettingsScreen.kt`:
  - `editable` sale de `produceState(false, vaultState) { editableNow() }`, así que empieza cerrado.
  - Cada escritura vuelve a comprobarlo: la vuelta del selector de carpeta, `save`, «Quitar», y el bloqueo, el portapapeles y el coste (`change(guarded = true)`).
  - El tema, el modo sencillo y las misiones no estaban protegidos y siguen sin estarlo.
  - Los pistas y el aviso de la vuelta del selector usan `settingsLockedText`.
- `ui/AppNavHost.kt` pasa `container::settingsEditableNow`.
- **Tests:**
  - `rt22_emptyingAnotherVaultDoesNotUnlockTheSettingsOfTheWholeApp`, sin `@Ignore` y llamando a `c.settingsEditableNow()`.
    - **Rojo** (con `settingsEditableNow` aún con la regla de antes): `AssertionError: With vault A locked and its file there, emptying B must not let the lock and the clipboard of the whole app be changed`.
    - **Verde.**
  - `SettingsEditableTest`, con la regla nueva y los textos.
  - `AppContainerVaultsTest.theGuardedSettingsFollowTheRuleOfCreatingAVault`: primera instalación, editable; A con archivo y B vacía, no; A bloqueada, no; A abierta, sí.
  - App, con `redteam`, `settings`, `AppContainerVaultsTest`, `tools` y `PlatformSourcesTest`: 696 tests, 0 fallos.

## 3. RT-24 (3779726)
- **`tools/qr/WifiQr.kt`:**
  - `wifiQrNameHidden(marks)`, con el texto del Ruling.
  - `wifiQrLines` la mete en segundo lugar, justo después de O-4, cuando `hasHiddenChars(c.ssid)`, con `hiddenMarks(...).joinToString(", ")`.
  - `WIFI_QR_TEXTS` la incluye (es la tabla de textos visibles de la tarea 9).
- **`ui/screens/WifiQrScreen.kt`:** el nombre se enseña con `Text(visibleText(shown.ssid), …)`, y esa línea va en `NegativeColor`; las demás siguen en letra pequeña. El Inspector no cambia.
- **Tests:**
  - `rt24_theQrScreenNeverShowsAnotherNetworkThanTheCodeJoins`, sin `@Ignore`.
    - **Rojo:** `AssertionError: The screen shows «Casa» and says nothing, while the code joins another network: [U+0043 … U+200B, … U+00AD …, … U+202E, U+2066 …, … U+0000]`.
    - **Verde.**
  - `WifiQrTest.aNameWithHiddenCharactersSaysSo`: las cinco gemelas tienen su línea; «Casa ❤️» no la tiene; con red abierta el orden es aviso, nombre y contraseña; se fija el texto exacto; está en `WIFI_QR_TEXTS`.
    - **Rojo:** `IndexOutOfBoundsException: Index 1 out of bounds for length 1`.
    - **Verde.**
  - `PlatformSourcesTest` (R9) espera ahora `Text(visibleText(shown.ssid)`; `visibleText` también neutraliza los controles y las marcas de dirección.
- **Ejecución:** app con `ui.*`, `redteam.*` y `tools.qr.*`, en verde.

## 4. RT-21, parte a (4c85961)
- **`vault/Vault.kt`:**
  - `MasterUnlocked(file, data)` y `afterMasterUnlockWithData(...)`, que decodifica una sola vez y devuelve lo que preparó `prepare`, que es lo que sella.
  - `afterMasterUnlock` se queda como envoltorio, porque lo usan los tests.
  - Costura para tests: `VaultCrypto.decodes`, un contador de `read`.
- **`vault/VaultStore.kt`:**
  - **`unlockLocked`:** `shown = withContext(compute) { data.withPlainVersions().sortedForShow() }`. Lo usan `commit(..., read = shown)` en la v4 y `opened(..., read = shown)` en el paso a v4. Desaparecen las dos relecturas.
  - **`replace`:** dentro de `prepare`, y antes de `write`/`wrap`, corren `checkCopyStructure`, `checkCopySize`, `checkCopyEntries` y el rechazo de un JSON con adjuntos. `attachmentInCopy` se queda después, en `io`. `shown` sale de lo preparado, y la relectura de `:1507` desaparece.
- **Tests** (`vault/SingleDecodeTest`):
  - `anUnlockWithTheMasterDecodesOnce`;
  - `anImportDecodesOnceAndShowsWhatItWrote`, con v4, v4 con `newTag` y v3 (`vault-1.4.3.json`): lo enseñado es igual a `VaultCrypto.read` del archivo guardado, ordenado;
  - `aRefusedCopySpendsNoSecondArgon2id`: una copia con dos identidades, con el coste de Ajustes mayor; `kekDerive` no se llama.
- **Rojo** (contra el `VaultStore.kt` anterior):

  ```
  anUnlockWithTheMasterDecodesOnce :: expected:<1> but was:<2>
  aRefusedCopySpendsNoSecondArgon2id :: expected:<0> but was:<1>
  anImportDecodesOnceAndShowsWhatItWrote :: v4 expected:<1> but was:<2>
  ```

- **Verde:** jvm-check con `vault.*`, `redteam.*`, `AppContainer*` y `crypto.*`, 1252 tests, 0 fallos.

## 5. RT-21, parte b (47fcec2)

### Largo máximo que la app escribe en cada lista del formato
| Lista | Máximo que escribe la app | Dónde |
|---|---|---|
| `entries` | Sin tope propio (24 MiB de datos) | `VaultLimits.kt` `VAULT_MAX_DATA_BYTES`; fuera del tope de lista |
| `folders` | 500 | `VaultLimits.FOLDERS_MAX` |
| `views` | 50 | `VaultLimits.VIEWS_MAX` |
| `tagColors` (mapa) | 200 | `VaultLimits.TAG_COLORS_MAX` |
| `entry.tags` | 10 desde las pantallas (`EntryQuery.kt:12`, `MAX_TAGS`); más solo si vienen de una copia hecha a mano (`MergePlan.kt:376` conserva las que hubiera; ninguna versión de la 1.3.0 a la 1.4.3 comprobó las de una copia) | |
| `entry.fields` | 30 | `VaultLimits.FIELDS_MAX` |
| `entry.history` | 20 | `VaultLimits.HISTORY_MAX_VERSIONS` |
| `entry.passwordHistory` | 5 | `History.kt:9`, `MAX_PASSWORD_HISTORY` |
| `entry.attachments` | Menos de 2 000 (adjuntos + 2 ≤ `BACKUP_MAX_ENTRIES`) | `Backup.kt:62` |
| `view.filter.types` | El número de tipos (menos de 20) | `EntryType` |
| `view.filter.tags` | Las fichas que se eligen a mano: más de 10 000 solo eligiendo más de 10 000. **Se acepta**, como dice el Ruling | |

Ninguna pasa de 10 000 de forma legítima, salvo ese caso de las vistas, que el Ruling acepta.

### El modelo y su calibración
- **`vault/VaultLimits.kt`:** `checkCopyShape(json, version)`, `copyTreeEstimate`, `copyLongestList` y `scanCopy`.
  - Es una pasada lineal sobre los bytes: tiene en cuenta las cadenas y sus escapes y no crea ningún `String`.
  - La lista de entradas es la raíz en las versiones 2, 3 y sin versión, y el miembro `entries` de la raíz en la v4.
  - Los nombres de miembro no cuestan, salvo en `tagColors`, que es un mapa.
  - `scanJson` de la importación no se toca: `ImportJsonTest` y `checkJsonShape` no cambian.
- **Constantes:**

  | Constante | Valor |
  |---|---|
  | `COPY_TREE_OBJECT_BYTES` | 160 |
  | `COPY_TREE_COLLECTION_BYTES` | 64 |
  | `COPY_TREE_ELEMENT_BYTES` | 40 |
  | `COPY_TREE_STRING_BYTES` | 48 |
  | `COPY_TREE_CHAR_BYTES` | 2 |
  | `COPY_TREE_LITERAL_BYTES` | 16 |

- **Medido contra el montón vivo después de GC**, con 1 MiB de JSON por forma. Fue con el JDK 21 de las pruebas y el recolector que trae por defecto: no puedo cambiar a SerialGC sin tocar los archivos de Gradle.

  | Forma | Medido | Estimado | Margen |
  |---|---|---|---|
  | Lista | 7 820 976 | 15 383 272 | ×1,97 |
  | Conjunto | 14 470 864 | 15 698 130 | ×1,08 |
  | Mapa | 7 269 536 | 11 381 638 | ×1,57 |
  | Objeto | 1 864 128 | 3 938 278 | ×2,11 |

  El modelo nunca queda por debajo de lo medido; el conjunto es lo más justo.
- **Formas legítimas** a 24 MiB + 4 KiB (estimado y porcentaje del presupuesto):

  | Forma | Estimado | Del presupuesto |
  |---|---|---|
  | (a) notas largas | 50 417 096 | 18,4 % |
  | (b) accesos mínimos | 95 063 260 | 34,6 % |
  | (c) accesos con 10 etiquetas de un punto de código | 219 574 908 | 79,9 % |
  | (d) 30 campos por entrada | 144 595 400 | 52,6 % |
  | (e) 20 versiones de historial | 101 975 944 | 37,1 % |
  | (f) 500 carpetas, 50 vistas y 200 colores, con (c) | 219 465 130 | 79,9 % |
  | (g) 5 contraseñas antiguas por entrada | 133 911 258 | 48,7 % |
  | (h) los cuatro archivos de los recursos | pasan | |

- **`COPY_TREE_MAX_BYTES`** = 1,25 × 219 574 908 = 274 468 635 bytes (261,75 MiB), redondeado hacia arriba a **262 MiB**.
- **`COPY_LIST_MAX`** = 10 000.
- **Condición de parada: no salta, pero por poco.** La copia de `rt21_` (19 843 237 bytes de JSON) se estima en 277 564 024 bytes (264,7 MiB), por encima del presupuesto: 1,25 veces la forma más cara son 261,75 MiB. La distancia es de un 1 %. Además, esa copia ya la rechaza el tope de lista (2,6 millones de etiquetas).
- **Formas hostiles a 24 MiB:**
  - «a» repetida en entradas de 10 000: 605 732 846 bytes;
  - etiquetas distintas de 5 letras repartidas: unos 330 MB.

  Las dos pasan del presupuesto, y las rechaza el presupuesto, no el tope de lista.

### Dónde se aplica
- **`vault/Vault.kt`:** `read(file, dek, copy = false)`. Con `copy`, `checkCopyShape` corre después de `jsonNestingWithin` y antes de `toString`. `afterMasterUnlockWithData(..., copy)` se lo pasa a `read`. El contador `decodes` cuenta ahora lo que de verdad se decodifica, es decir, lo que pasa los análisis.
- **`vault/VaultStore.kt`:** en `replace` va `copy = true` (importar la copia, importarla como bóveda nueva y el JSON de la 1.0).
- **`vault/BackupCheck.kt`:** `checkBackup` llama a `VaultCrypto.read(..., copy = true)`.

### Tests
- **`redteam/RedTeam20CopyShapeTest`:**
  - el modelo frente a lo medido, con lista, conjunto, mapa y objeto;
  - las formas legítimas (a) a (g), cada una por debajo del 80 % del presupuesto;
  - (h) los cuatro archivos reales;
  - el rechazo antes de decodificar, que se comprueba con `decodes == 0` a través de «Importar copia»:
    - 24 MiB de «a» en entradas de 10 000;
    - 24 MiB de etiquetas distintas de 5 letras;
    - `tagColors`, carpetas, vistas y las etiquetas de una entrada, con más de 10 000;
    - las etiquetas de 2 000 vistas;
    - lo mismo en una v3 sin versión;
  - la lista de entradas, sin tope;
  - una bóveda propia con 20 000 etiquetas en una entrada se abre con la maestra y con la huella.
- **`RedTeam20CopyTest`:**
  - `rt21_aCopyWhoseTreeOutgrowsThePhoneIsRefusedBeforeItIsDecoded`, sin `@Ignore`: real 758 MiB asignados, hostil 239 MiB, `COPY_DAMAGED`;
  - `rt21Impact_…` pasa a `resistsACheckOfACopyOverTheListCapRefusesIt`: comprobar da `COPY_DAMAGED`, y se conserva la medida (×8,1) con `VaultCrypto.read` sin los topes de copia.
- **Rojo** (con `checkCopyShape` vacía un momento):

  ```
  listsOverTheCapAreRefused :: tagColors: not refused
  theSameInAVersion3WithoutItsVersion :: v3, 24 MiB of «a»: not refused
  theTagsOfTwoThousandViewsAreRefused :: 2 000 views with tags: not refused
  repeatedAndSpreadTagsUnderTheListCapAreRefused :: 24 MiB of «a», 10 000 an entry: not refused
  rt21_aCopyWhoseTreeOutgrowsThePhoneIsRefusedBeforeItIsDecoded :: A copy whose tree is ~8 times its JSON must be refused (it was imported)
  resistsACheckOfACopyOverTheListCapRefusesIt :: The check found nothing wrong
  ```

- **Verde:** jvm-check con `redteam.*`, `vault.*`, `imports.*` y `AppContainer*`: 1330 tests, 0 fallos, y ninguno tarda más de 8 s.

## 6. RT-21, parte c (78465ca)
- **Textos:**
  - `vault/BackupCheckTexts.kt`: `COPY_NO_MEMORY`, palabra por palabra. `copyFailureText` lo pasa tal cual.
  - `vault/Vault.kt`: `VAULT_NO_MEMORY`, junto a `WRONG_MASTER`. `masterUnlockErrorText` lo añade a su lista cerrada.
- **Dónde se captura**, siempre alrededor de lo que corre en `compute`:
  - `replace`: alrededor de todo lo anterior a `withContext(NonCancellable)`, que no escribe nada. Da `COPY_NO_MEMORY` y borra la clave.
  - `checkBackup`: alrededor de la lectura y las comprobaciones. Da `COPY_NO_MEMORY` y la carpeta se borra en el `finally`.
  - `unlockLocked` (lo preparado y su orden), `commit` (su lectura) y `unlockWithKeyLocked`: dan `CryptoFailure(VAULT_NO_MEMORY)`. No es `StaleBiometricKey`, así que la huella no se apaga. La pantalla lo enseña a través de `biometricFailureText` (`VaultStore.kt:99`).
- **«No se ha cambiado nada» y «La bóveda sigue guardada» son ciertos en cada sitio:**
  - `replace`: antes de `NonCancellable` no hay escritura. `nextGeneration`, `beforeReplace` y el guardado van después.
  - `checkBackup`: solo escribe su carpeta temporal, que se borra.
  - «Importar como bóveda nueva»: `newVault` la deshace (`dropVault`) y la bóveda de antes se queda como estaba.
  - Desbloqueo: el archivo no cambia antes de la lectura; el test lo comprueba byte a byte.
- **Costura para los tests:** `VaultCrypto.beforeDecode`, que corre justo antes de decodificar. Un `OutOfMemoryError()` hecho a mano; nada agota la memoria de verdad.
- **Tests:**
  - `vault/NoMemoryTest`:
    - importar: el archivo de la bóveda y `settings.json` quedan iguales byte a byte, no queda carpeta `import-` y la bóveda sigue abierta;
    - comprobar: no queda carpeta;
    - importar como bóveda nueva: el registro, la bóveda activa y su archivo quedan como estaban;
    - maestra: sale `VAULT_NO_MEMORY` y no `WRONG_MASTER`, la bóveda queda bloqueada con el archivo igual, y el siguiente intento abre;
    - huella: `CryptoFailure(VAULT_NO_MEMORY)` y no `StaleBiometricKey`, y la siguiente abre.
  - `ErrorTextsTest.theTextsOfRunningOutOfMemory` fija los dos textos.
- **Rojo** (los tests nuevos contra `VaultStore.kt` y `BackupCheck.kt` de `47fcec2`): los 5 de `NoMemoryTest` con `java.lang.OutOfMemoryError` sin capturar.
- **Verde:** jvm-check con `vault.*`, `redteam.*`, `AppContainer*` e `imports.*`: 1336 tests, 0 fallos.
- **Inventario:** `docs/superpowers/handoff/sdd-2.0/task-10c-inventory.md`, dos filas nuevas en «1. La bóveda».

## 7. RT-22 (ii) (d7f81d1), con el sí del dueño
- **Guardián en el contenedor:** `AppContainer.mayFillEmptySlot()`, que es `canAddVault()` leída en IO. Las pantallas lo comprueban justo antes de llamar al almacén, a través de `LocalEmptySlotGuard` (`ui/screens/VaultBackup.kt`), que da `ui/AppNavHost.kt` (`container::mayFillEmptySlot`).
  - «Crea tu bóveda» (`ui/screens/VaultScreen.kt`) lo vuelve a pedir antes de `store.create(pw)`. Si no deja, da `VAULT_NEW_NEEDS_OPEN`.
  - «Importar copia» (`VaultBackup.kt`), con `NoVault`, lo pide antes de `importReplace`. Si no deja, da `VAULT_NEW_NEEDS_OPEN`.
- **Lo que se ve en ese estado** (otra bóveda con archivo y la bóveda vacía a la vista):
  - «Crea tu bóveda» enseña el selector de bóvedas y `VAULT_NEW_NEEDS_OPEN`, sin el formulario ni «Importar». Mientras no se sabe, un indicador de espera; no ofrece nada.
  - Ajustes: «Importar copia» se apaga, con la pista `VAULT_NEW_NEEDS_OPEN`. Usa el mismo `editable` de RT-22 (i), que con `NoVault` es justo esa regla.
  - Ajustes, «Hacer una copia ahora»: «Esta bóveda está vacía: no hay nada que copiar.» (`VAULT_EMPTY_NOTHING_TO_COPY`). Este cambio va en todos los casos con `NoVault`, como dice el Ruling.
  - «Salud»: `HEALTH_EMPTY_VAULT`. Con una sola bóveda, o sin archivos, sigue `HEALTH_NO_VAULT`.
- **No cambian:** `createVault` e `importAsNewVault` desde una bóveda abierta no pasan por el guardián, y «He olvidado» tampoco.
- **Tests** (`AppContainerVaultsTest`):
  - `anEmptyVaultIsFilledOnlyByTheRuleOfCreatingAVault`: primera instalación, sí; A con archivo y B vacía, no; desde A abierta, `createVault` funciona;
  - `theScreensAskTheGuardBeforeFillingAnEmptyVault`: de fuente, más los tres textos exactos.
- **Rojo** (con `mayFillEmptySlot` = `true`): `anEmptyVaultIsFilledOnlyByTheRuleOfCreatingAVault :: A has its file: B, empty, cannot be filled` y `theScreensAskTheGuardBeforeFillingAnEmptyVault :: AssertionError`.
- **Al día en este commit:**
  - el test de fuente de `PlatformSourcesTest` sobre `checkCopyEntries`, que movió la parte a;
  - `changeOfMasterThenSwitch…` compara los ajustes de B sin la marca A11, que sube con cualquier uso por diseño. Fallaba según el reloj.
- **Verde:** toda la app (`:app:testDebugUnitTest`), 2454 tests, 0 fallos.

## 8. Cierre
- **Ejecutadas por separado, en jvm-check:** `BackupCheckTest`, `BackupV3Test`, `BackupTest`, `VaultV4Test`, `VaultStoreTest`, `AppContainerVaultsTest`, `ErrorTextsTest` y `redteam.*`: 280 tests, 0 fallos.
  - `ui/redteam` va en la app, en la ejecución de `d7f81d1`.
  - `grep -rn "@Ignore"` en `redteam/` y `ui/redteam/` no deja ningún `@Ignore` activo.
  - Las suites enteras no las corrí, como pide el encargo: son del clon limpio del controlador.
- **`pendientes.md`:**
  - filas 24 y 52, «Hecho»;
  - fila 23, con la prueba de RT-24 en el móvil;
  - filas nuevas: 54, el riesgo con nombre de RT-21; 55, RT-21 en el móvil, con la fila 51; 56, los informativos del ataque y su destino.
  - RT-22 (ii) está hecho, así que no lleva fila.
- **Copia de este informe:** `docs/superpowers/handoff/sdd-2.0/redteam-fixes-report.md`.

| RT | Commit | Rojo | Verde | Tests |
|---|---|---|---|---|
| RT-23 | f8f3f0d | — (`resists…`, sin código) | Sí | `resistsAVaultImportedAsNewSharesTheCopysKeyOnlyUntilAMasterChanges` |
| RT-22 (i) | b2b8b0d | Sí | Sí | `rt22_…`, `SettingsEditableTest`, `theGuardedSettingsFollowTheRuleOfCreatingAVault` |
| RT-24 | 3779726 | Sí | Sí | `rt24_…`, `WifiQrTest.aNameWithHiddenCharactersSaysSo`, `PlatformSourcesTest` (R9) |
| RT-21 a | 4c85961 | Sí | Sí | `SingleDecodeTest` (3) |
| RT-21 b | 47fcec2 | Sí | Sí | `rt21_…`, `resistsACheckOfACopyOverTheListCapRefusesIt`, `RedTeam20CopyShapeTest` (10) |
| RT-21 c | 78465ca | Sí | Sí | `NoMemoryTest` (5), `ErrorTextsTest.theTextsOfRunningOutOfMemory` |
| RT-22 (ii) | d7f81d1 | Sí | Sí | `anEmptyVaultIsFilledOnlyByTheRuleOfCreatingAVault`, `theScreensAskTheGuardBeforeFillingAnEmptyVault` |

## Revisión experto-seguridad-cripto
- **Decisión (a), CWE-400 y CWE-770:** un tope antes de reservar.
  - `vault/Vault.kt` (`read`, con `copy`) hace `checkCopyShape` antes de `toString` y antes de decodificar.
  - `vault/VaultLimits.kt` (`scanCopy`) es lineal y no crea ningún `String`.
  - Lo propio (desbloqueo, huella, `confirmMaster`, `changeMaster`, exportar) no lleva esos topes, así que no se pierde ninguna bóveda (Ruling T1, dudas 4 a 6).
- **Decisión (b):** una sola decodificación.
  - `Vault.kt` (`afterMasterUnlockWithData`) da lo preparado, que es lo que se sella y lo que se enseña (`VaultStore.kt`: `unlockLocked`, `replace`).
  - Las comprobaciones de la copia van en `prepare`, antes de `write`/`wrap`, así que una copia rechazada no gasta el segundo Argon2id (`SingleDecodeTest.aRefusedCopySpendsNoSecondArgon2id`).
- **Última defensa:** `OutOfMemoryError` solo alrededor del trabajo de `compute`, en `VaultStore.kt` (`unlockLocked`, `commit`, `unlockWithKeyLocked`, `replace`) y `BackupCheck.kt` (`checkBackup`).
  - No es `StaleBiometricKey`, así que la huella sigue.
  - `masterUnlockErrorText` nunca dice «Contraseña incorrecta» ante una falta de memoria, y así nadie llega a «He olvidado».
- **RT-22, CWE-284:** `settingsEditable(state, anyVaultFile)` y `mayFillEmptySlot` comparten el predicado de `canAddVault` (`AppContainer.kt`, `anyVaultFile`). Cada escritura lo vuelve a comprobar.
- **RT-24, CWE-451:** `WifiQrScreen.kt` enseña `visibleText(shown.ssid)`, lo mismo que lleva el código, con las marcas del Inspector (`tools/qr/Visible.kt`).
- **RT-23:** las primitivas no cambian. AES-256-GCM, con *nonce* aleatorio de 12 bytes por sellado, sigue lejos de 2^32 sellados con la misma clave. Las notas del diseño corrigen la afirmación falsa.
- No hay primitivas, permisos, hosts ni dependencias nuevas, y no cambia ningún formato (`SG1:`, `.sgenc`, `seguridad-vault`, `seguridad-backup`).

## Revisión Ingeniero Móvil Senior
- **Memoria:**
  - el desbloqueo deja de decodificar dos veces (`VaultStore.kt`, `unlockLocked`, y `commit` con `read`);
  - la importación, una sola vez;
  - las capturas de `OutOfMemoryError` van dentro de los bloques de `compute`, nunca en el hilo principal.
- **Ciclo de vida y E/S:** los `File.exists` de las reglas nuevas van en IO: `AppContainer.settingsEditableNow` y `mayFillEmptySlot`, que las pantallas leen con `produceState`, empezando cerradas.
- **Compose:**
  - `SettingsScreen.kt`: `change(guarded = true)` solo en el bloqueo, el portapapeles y el coste; el tema, el modo sencillo y las misiones siguen sin guarda.
  - `WifiQrScreen.kt`: el aviso del nombre va en `NegativeColor`, después de O-4.
  - `VaultScreen.kt` (`CreateVault`): sin formulario mientras no se sabe.
- **Tests:**
  - los de la UI solo los compila la app (`jvm-check/build.gradle.kts` excluye `ui/`);
  - Gradle siempre de uno en uno, con `flock`, y `./gradlew --stop` después de cada ejecución de la app;
  - ningún test agota la memoria de verdad: usa la costura `VaultCrypto.beforeDecode`.

## Textos para el Abogado Digital (revisión final)
- `COPY_NO_MEMORY`: «Este móvil no tiene memoria suficiente para leer esta copia. No se ha cambiado nada. Cierra Clavero del todo, vuelve a abrirlo e inténtalo otra vez; si sigue igual, este móvil no puede leerla.»
- `VAULT_NO_MEMORY`: «Este móvil no tiene memoria suficiente para abrir esta bóveda. La bóveda sigue guardada: cierra Clavero del todo, vuelve a abrirlo e inténtalo otra vez; si sigue igual, este móvil no puede abrirla.»
- `SETTINGS_NEED_AN_OPEN_VAULT`: «Abre una de tus bóvedas (pestaña Bóveda) para cambiar este ajuste.»
- `wifiQrNameHidden`: «El nombre de esta red lleva caracteres que no se ven, marcados como $marks, y el código los lleva tal cual. Si no lo escribiste así, vuelve a escribir el nombre en «Editar».»
- Las dos notas de RT-23 en el diseño.
- RT-22 (ii):
  - `VAULT_NEW_NEEDS_OPEN`, ya existía, en «Crea tu bóveda» y en Ajustes;
  - `VAULT_EMPTY_NOTHING_TO_COPY` «Esta bóveda está vacía: no hay nada que copiar.»;
  - `HEALTH_EMPTY_VAULT` «Esta bóveda está vacía. Para crear o importar otra bóveda, abre antes una de las que tienes.»
- `COPY_DAMAGED` para una copia de la 1.x con una lista de más de 10 000 hecha a mano: es discutible como verdad. Es el riesgo con nombre, fila 54.
