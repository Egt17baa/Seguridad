# Tarea 2 de Clavero 2.0: informe del implementador

Varias bóvedas separadas, cada una con su maestra, su archivo, su clave de huella, sus ajustes y su copia automática; una abierta a la vez. Rama `ccr-0aa27f95-u038cd`, sin push. Base `b9af0cf`.

Nota de la reanudación: la sesión anterior se cortó por el límite de la API sin dejar nada escrito ni en commits (árbol limpio en `b9af0cf`). Todo este trabajo se hizo de cero en esta sesión.

## Commit

| SHA | Asunto |
|---|---|
| `4056f34` | 2.0, tarea 2: varias bóvedas |

## Qué se ha hecho

**Registro (`vault/VaultRegistry.kt`, nuevo).**
- `PRIMARY_VAULT_ID = "principal"`, `VaultInfo(id, color, order)` sin nombre (opción A del dueño), `VAULTS_FILE = "vaults.json"`.
- `VaultRegistry(filesDir, io)`: `vaults: StateFlow`, `lastOpened`, `dirOf` (`principal` → `files/vault/`; los demás → `files/vaults/<id>/`; cualquier otra cadena da `IllegalArgumentException`, `VaultRegistry.kt:90`, así ningún id nombra una ruta fuera de esas carpetas), `create(color)` (16 bytes del CSPRNG en hex; la 11.ª lanza `VaultLimitExceeded(TOO_MANY_VAULTS)`, `:97`), `setColor`, `reorder` (exige una permutación exacta), `remove` (borra solo su carpeta; nunca la última, `:130`), `setLastOpened`. Escritura con `writeTextAtomically` bajo un `Mutex`.
- Si `vaults.json` falta, está dañado o no nombra ningún id válido, se reconstruye desde las carpetas: `principal` primero (si existe `files/vault/` o no hay otra) y luego los `files/vaults/<32 hex>/` (`:163`). Se lee una vez, al crearse (unos cientos de bytes, como los ajustes al arrancar).
- `displayName(data, order)`: el nombre con `plainReportText` si la bóveda está abierta y lo tiene; si no, «Bóveda N». `perVaultName(base, id)`: `base` para `principal`, `base_<id>` para las demás.

**Nombre cifrado.** `VaultData.name` (`VaultData.kt`), con tope `VaultLimits.VAULT_NAME_MAX = 100` puntos de código comprobado en `checkStructure` (`VaultLimits.kt:194`, texto `VAULT_NAME_TOO_LONG`). `VaultCrypto.create(..., name)` lo mete en el primer blob; `VaultStore.create(master, name)` y `VaultStore.rename(name)` (`VaultStore.kt:894`, un guardado normal; `false` si no cambia o está bloqueada).

**`VaultStore` por bóveda.** Parámetros nuevos `id` y `active`. La puerta: `opened()` deja la bóveda bloqueada y borra la clave si no es la activa (`VaultStore.kt:452`), en el mismo `synchronized(opening)` que `lock()`; así un desbloqueo, una importación o una creación de una bóveda que ya no es la activa nunca la deja abierta. `awaitIdle()` (`:672`) espera a lo que tenga el mutex.

**M-8.** El paso pendiente a v4 sale a `vault/PendingUpgrade.kt` (nuevo): el archivo v4 que espera, `asking` (`pendingUpgrade`), `autoCopyFailed`, `done` y `afterUnlock(copyBefore, save, stillOpen)` (la copia de antes, el guardado automático o la pregunta). `VaultStore` conserva el mutex, la época y `completeUpgrade`, y delega el estado (`VaultStore.kt:329`, `:519`). Las propiedades públicas (`pendingUpgrade`, `upgradeAutoCopyFailed`, `upgradeDone`, `upgradeDoneShown`) no cambian; los 95 tests de `VaultV4Test` y `VaultStoreTest` siguen en verde.

**`AppContainer`.**
- Una `Slot` por bóveda (`AppContainer.kt:301`): su `VaultStore` (su archivo, su `kdf`, su `beforeCreate`, `beforeReplace` y `beforeUpgrade`, `id`, `active = { activeId == id }`) y su `AutoBackup` (lambdas sobre `settings.vault(id)`) en un ámbito propio hijo de `appScope`, que se cancela al borrarla. La firma de `AutoBackup` no cambia (ruling 1 ↔ 2).
- `activeVault: StateFlow<VaultStore>`, `vault` (getter), `activeVaultId`, `autoBackup` (el de la activa), `vaultState` (`flatMapLatest` del estado de la activa, para las limpiezas de toda la app: portapapeles A14, lugares de los selectores, `QrHandoff` y `wipePkTemp` en `SeguridadApp`).
- `lockEpoch` de toda la app (`:390`): sube con cada bloqueo de la activa y con cada cambio, y nunca repite un valor; lo usa `ClipboardNoticeRequest`, para que una copia pendiente de una bóveda no se acepte en otra con la misma época.
- `switchTo(id)` (`:404`): cierra primero la puerta de la vieja (`activeId = id`, `:410`), la bloquea con `LockReason.MANUAL`, suelta la copia pendiente del A14 y el diálogo de A6, espera a su mutex (`awaitIdle`), carga la nueva y la publica; recuerda la última abierta. Id desconocido: `IllegalArgumentException`.
- `createVault(name, master)` e `importAsNewVault(input, master, name)`: id nuevo, cambian a ella; si algo falla, no queda nada de la nueva y vuelve la anterior (bloqueada). `importAsNewVault` dos veces con la misma copia da dos bóvedas.
- `canAddVault()` (`:462`): solo desde una bóveda abierta, o sin ninguna con archivo (ver «Decisiones», 1).
- `deleteVault(id, master, typedName)` (`:511`): cambia a ella si hace falta, la abre con su maestra (o la comprueba con `confirmMaster` si ya está abierta; una equivocada lanza `CryptoFailure`), compara el nombre escrito con `compareKey` contra `displayName` (`:525`), y borra: la carpeta (registro), la clave de la huella (`forgetBiometric`, Android pasa `BiometricVault.disable(this, id)`), sus ajustes, y devuelve el permiso de su carpeta de copias si ninguna otra bóveda la usa (`releaseIfUnused`, `:552`). No toca las copias hechas. La última no se borra (`false`).
- `onMasterChanged` (`:573`): el diálogo de A6 recuerda de qué bóveda es; su reemplazo de copias va siempre a esa.

**Ajustes (`settings/Settings.kt`, `SettingsStore.kt`).** `Settings` se queda con lo global (bloqueo, portapapeles, tema, aviso, marca del reloj A11, aviso A14, aviso del nombre) más `perVault: Map<String, VaultSettings>`. `VaultSettings` lleva, tal cual (ruling 2 ↔ 8), el coste de Argon2id, la copia automática, A6 y las copias de antes de importar; todas las funciones (`withAutoBackupResult`, `withFolderChosen`, `forgettingAutoBackupCopies`…) pasan a `VaultSettings`. `decodeSettings` (`SettingsStore.kt:151`) lee un `settings.json` de la 1.4.3 con esos campos arriba como los de `principal`, salvo que ya haya `perVault["principal"]`; al escribir, ya no van arriba, así que una bóveda borrada no vuelve. `updateVault`, `turnOffAutoBackupForNewVault(id)`, `turnOffAutoBackup(id)`, `forgetAutoBackupCopiesForImport(id)`. «Modo sencillo» no existe todavía (tarea 10).

**Huella (`android/BiometricVault.kt`).** `enabled`, `keyLevel`, `disable`, `enable` y `unlock` reciben `vaultId`; alias `perVaultName(ALIAS, id)` y preferencias `perVaultName(PREFS, id)` (`BiometricVault.kt:115`, `:121`). `ALIAS` y `PREFS` siguen tal cual y `AppNameTest` no se ha tocado. Lo que se sabe de StrongBox en el móvil (`strongbox_failed`, `strongbox_hint_shown`) sigue en `PREFS` para todas (`phonePrefs`, `:118`). Una sola `KeyOpsQueue`.

**Interfaz.**
- `AppNavHost`: dentro de `key(vault)`, `VaultScoped` da a la navegación un `ViewModelStore` propio de la bóveda activa, guardado en un `ViewModel` de la actividad (`VaultScopes`, `AppNavHost.kt:113`): sobrevive a una recreación de la actividad y se vacía (todos sus `ViewModel` cancelados) en cuanto hay otra bóveda activa. Ninguna pantalla conserva la tienda de la bóveda anterior. El coste de Argon2id de «Cifrar texto», «Cifrar archivo» y del resumen de Herramientas es el de la bóveda activa.
- `ui/screens/VaultPickerScreen.kt` (nuevo), ruta `vaults`: la lista («Bóveda N» y su color, con el nombre del color para TalkBack; la activa marcada), elegir, subir y bajar, color, cambiar el nombre y borrar la abierta (maestra y nombre, con el aviso O-2 y R-9 a la vista), «Nueva bóveda» (nombre, maestra con `newMasterProblem`, repítela, «Entiendo…») e «Importar como bóveda nueva» (selector guardado con `PickerPlace.IMPORT_COPY`, la cabecera con `mayBeVaultImport`, la contraseña en el `PasswordDialog` de «Importar copia»).
- `VaultSwitcher` (por `LocalVaultSwitcher`): arriba en «Crea tu bóveda», en «Bóveda bloqueada» y como título de la pestaña Bóveda abierta.
- `SettingsScreen` muestra y cambia la copia automática y el coste de la bóveda activa, y devuelve los permisos de carpeta con `releaseIfUnused`.

## Tests

Nuevos (20): `VaultRegistryTest` (10), `PerVaultSettingsTest` (3), `AppContainerVaultsTest` (7).
- `VaultRegistryTest`: `primaryStaysInOldFolder`, `elevenIsRefused`, `rebuiltFromFoldersWhenRegistryDamaged`, `removeDeletesOnlyItsFolder` (también: la última no se borra; `../vault` no es un id), `reorderAndColorAndLastOpened`, `registryHasNoNames` (ni `vaults.json` ni el `vault.bin` en claro contienen el nombre; se lee al abrirla; un nombre de 101 se rechaza), `displayNameBeforeAndAfterUnlock`, `perVaultNamesKeepThePrimaryOnes`, `biometricKeysArePerVault` (fuentes), `vaultTextsSayWhatStays` (O-1, O-2, R-9).
- `PerVaultSettingsTest`: `oldSettingsMoveToPrincipal`, `newVaultStartsWithoutBackup`, `noSecretInSettings` (ni `settings.json` ni `vaults.json` contienen nombres, títulos, usuarios, contraseñas, notas ni maestras de prueba).
- `AppContainerVaultsTest`: `switchLocksTheOpenOne`, `onlyOneUnlockedAtATime`, `masterOfOneDoesNotOpenAnother`, `lateWriteNeverLandsInAnotherVault` (foco 2: el guardado de A se para en el hilo de cálculo con el mutex de A cogido mientras empieza `switchTo(B)`; el archivo de B queda byte a byte igual, B abierta no lo tiene, A lo tiene si `upsert` dijo `true`, y una escritura posterior de una pantalla vieja de A con B abierta no guarda nada en ningún sitio), `deleteNeedsMasterAndName`, `importAsNewTwiceMakesTwoVaults`, `newVaultNeedsAnOpenVault`.

Cambiados por el traslado de campos (sin cambiar lo que comprueban): `SettingsTest`, `AutoBackupTest`, `AutoBackupF3Test`, `AutoBackupImportCopyTest`, `AppContainerTest` (los campos de la copia van a la primera bóveda, con el ayudante de test `settings/PrincipalSettings.kt`: `Settings.principal` y `SettingsStore.updatePrincipal`); `VaultStoreTest` (texto de fuente: `BiometricVault.disable(context, store.id)`); `PlatformSourcesTest` y `DocumentUriTest` (29 → 30 selectores: el de «Importar como bóveda nueva»); `FinalLegalReviewTest` (`${settings.kdf.label}` → `${kdf.label}`: el mismo texto, con el coste de la bóveda activa).

Resultados finales (después del commit, sobre `4056f34`):
- `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --no-daemon --max-workers=1`: BUILD SUCCESSFUL, **1976** tests, 0 fallos (base 1956).
- `./gradlew -p jvm-check test --no-daemon --max-workers=1`: BUILD SUCCESSFUL, **1969** tests, 0 fallos (base 1949).

## Pruebas TDD (RED y GREEN)

- `VaultRegistryTest` RED: `./gradlew :app:testDebugUnitTest --tests 'app.seguridad.vault.VaultRegistryTest'` → `e: VaultRegistryTest.kt:22:30 Unresolved reference 'VaultRegistry'.`, `Unresolved reference 'PRIMARY_VAULT_ID'`, `'VAULTS_FILE'`… GREEN: `tests="8" failures="0"` (luego 10 con los dos de fuentes y textos).
- `AppContainerVaultsTest` y `PerVaultSettingsTest` RED: `e: AppContainerVaultsTest.kt:51:19 Unresolved reference 'createVault'`, `'activeVaultId'`, `'switchTo'`, `'registry'`, `No parameter with name 'vaultCompute'`, `'forgetBiometric'`, `Unresolved reference 'updateVault' on receiver of type 'SettingsStore'`. GREEN: `AppContainerVaultsTest tests="6" failures="0"`, `PerVaultSettingsTest tests="3" failures="0"`.
- Mutación de la puerta: quitando `|| !active()` de `VaultStore.opened`, `AppContainerVaultsTest > onlyOneUnlockedAtATime FAILED (AssertionError at AppContainerVaultsTest.kt:91)`; vuelto a poner, verde.
- `newVaultNeedsAnOpenVault` RED: `e: AppContainerVaultsTest.kt:227:68 Unresolved reference 'canAddVault'`. GREEN: `BUILD SUCCESSFUL` (7 de 7).
- Sin TDD (dicho con franqueza): `biometricKeysArePerVault` y `vaultTextsSayWhatStays` se escribieron después del código, como guardas de regresión; la interfaz (`VaultPickerScreen`, `AppNavHost`) no tiene test de pantalla (no hay emulador; queda para el flujo de Maestro de la tarea 11). El traslado de `PendingUpgrade` (M-8) es una refactorización cubierta por los tests existentes del paso a v4.

## Decisiones tomadas aquí (para el controlador)

1. **COV2-1 con varias bóvedas (hallazgo propio, arreglado).** Sin más, cualquiera con el móvil desbloqueado y la app bloqueada podía crear una bóveda nueva (abierta) y con ella cambiar los ajustes de toda la app (bloqueo, portapapeles), que `settingsEditable` deja tocar con la bóveda abierta. Ahora `createVault` e `importAsNewVault` exigen `canAddVault()`: una bóveda abierta, o ninguna con archivo en el móvil; la pantalla desactiva esos botones (y el orden y los colores) y dice `VAULT_NEW_NEEDS_OPEN`. Queda un resto (ver «Dudas», 1).
2. **Borrar una bóveda sin archivo** (un hueco que dejó «He olvidado la contraseña maestra»): no pide maestra ni nombre, porque no hay nada que proteger ni nombre que comprobar. Con archivo, siempre las dos cosas.
3. **El coste de Argon2id de una bóveda nueva** empieza en «Estándar», no se hereda de la abierta: es un ajuste por bóveda que Ajustes enseña. Si se prefiere heredar, es una línea en `newVault`.
4. **«Importar como bóveda nueva»** no pide nombre en la pantalla: usa el `PasswordDialog` de «Importar copia» (así sigue habiendo un solo sitio que mira solo la longitud de una contraseña existente, `MasterPolicyTest`) y la bóveda conserva el nombre de la copia; se cambia después con «Cambiar el nombre». La API (`importAsNewVault(..., name)`) sí lo acepta.
5. **StrongBox** se sigue apuntando una vez por móvil en `PREFS`, no por bóveda: es un dato del móvil, no de la clave. Por eso `strongBoxFailed`, `strongBoxHintShown` y `markStrongBoxHintShown` no reciben `vaultId` (el brief pedía el id en cada función pública; estas tres no tocan ninguna clave).
6. **Dos bóvedas en la misma carpeta de copias** funcionan: cada una solo rota y borra los ids de su propia lista; las de la otra cuentan como «no apuntadas» en el aviso de A6, que ya dice «o de otra bóveda». La tarea 8 lo revisará con los destinos.

## Autorrevisión

- `VaultStore.kt` sigue en unas 1 310 líneas: M-8 pedía sacar el paso pendiente y eso está hecho (`PendingUpgrade`), pero la tarea añade el nombre, la puerta y `awaitIdle`. La importación (`importReplace`, `replace`, `moveIn`, `putBack`) sería la siguiente pieza para sacar; no lo he hecho para no ampliar el alcance.
- Un test inicial se colgaba: `assertThrows { runBlocking { … } }` dentro de `runTest` bloquea el hilo del test mientras una escritura de la marca del reloj (`appScope` = `backgroundScope`) tiene el mutex de los ajustes. Era del test, no del código: los tests nuevos usan un `assertFails` suspendido.
- El aviso del compilador «Expression is unused» en `AppNavHost.kt` es de `backOnce`, que no he tocado.

## Dudas y riesgos

1. **Resto de COV2-1:** si existe un hueco vacío (una bóveda borrada con «He olvidado la contraseña»), quien tenga el móvil puede cambiar a ella y crear allí una bóveda con su pantalla de siempre, y con ella abierta tocar el bloqueo y el portapapeles de toda la app. Antes de la 2.0 ya podía hacerlo borrando la bóveda (destructivo y visible); ahora basta con un hueco que ya exista. Severidad baja (el bloqueo máximo es 15 min y el portapapeles 90 s). Si se quiere cerrar del todo, los ajustes globales se podrían limitar a «la bóveda abierta tiene archivo desde antes de esta sesión» o pedir la maestra para cambiarlos; es una decisión de producto.
2. Un `importAsNewVault` de dos copias iguales da dos bóvedas con el mismo `vaultTag`: para la tarea 8 («Última copia comprobada»), una copia comprobada de una cuenta para las dos.
3. Las pantallas no tienen test automático; hace falta su flujo de Maestro (tarea 11): crear una segunda bóveda, cambiar, comprobar que la primera queda bloqueada, borrar con maestra y nombre, e importar como bóveda nueva.

## Revisión experto-seguridad-cripto

Repaso con `references/cripto-moderna.md` y `references/auditoria.md` sobre el diff:
- **Separación de claves:** cada bóveda tiene su propia clave de datos (AES-256-GCM + Argon2id, sin cambios en `Vault.kt` salvo el nombre en el blob, `Vault.kt` `create`) y su propia clave de huella (`BiometricVault.kt:121`, alias por id). La de una no abre otra (`masterOfOneDoesNotOpenAnother`; AES-GCM autentica con la clave de cada una).
- **Una abierta a la vez:** la puerta está en el único sitio donde se guarda la clave (`VaultStore.kt:452`, bajo `opening`), no solo en la interfaz; `switchTo` la cierra antes de bloquear (`AppContainer.kt:410`). Evidencia: `onlyOneUnlockedAtATime` y su mutación.
- **Carreras (decisión (b)):** un guardado ya empezado termina con su copia de la clave en su propio archivo; `switchTo` espera al mutex (`awaitIdle`, `VaultStore.kt:672`) antes de enseñar la otra; la época de toda la app no repite valores (`AppContainer.kt:390`). Evidencia: `lateWriteNeverLandsInAnotherVault`.
- **Datos en claro:** el registro solo guarda ids aleatorios, colores, orden y la última abierta (`VaultRegistry.kt`); el nombre va en el blob cifrado (`registryHasNoNames`); `settings.json` no recibe ningún secreto (`noSecretInSettings`). Ambos en el almacenamiento privado, fuera de las copias del sistema (`allowBackup` sigue apagado).
- **Rutas:** `dirOf` solo acepta `principal` o 32 hex (`VaultRegistry.kt:90`); un registro dañado no puede apuntar a otra carpeta (`read` filtra los ids, `:156`).
- **Aleatoriedad:** el id sale de `randomBytes` (CSPRNG del proyecto), 128 bits.
- **Borrado:** pide la maestra y el nombre; borra la clave del Keystore y los ajustes; no promete borrar copias ni dice «irrecuperable» (`vaultTextsSayWhatStays`).
- **COV2-1:** ver «Decisiones», 1, y «Dudas», 1.
- Nada nuevo de red, permisos, componentes exportados, dependencias ni registros.

## Revisión Ingeniero Móvil Senior

- **Ciclo de vida:** `VaultScopes` (`AppNavHost.kt:113`) guarda el `ViewModelStore` de la bóveda en un `ViewModel` de la actividad: un cambio de tema o de configuración que recrea la actividad conserva los `ViewModel` (como antes, cuando colgaban del `NavControllerViewModel`), y un cambio de bóveda los vacía y cancela sus `viewModelScope`. `key(vault)` reconstruye `rememberNavController` y su estado guardado va por bóveda.
- **Concurrencia:** dos mutex distintos (`switching` y `changingVaults`) para que `createVault`/`deleteVault` puedan llamar a `switchTo` sin reentrada; todo lo que cambia bóvedas va en `NonCancellable` a partir del punto sin vuelta (`AppContainer.kt:404`, `:511`). `canAddVault` lee disco: la pantalla lo pide en `Dispatchers.IO` (`VaultPickerScreen.kt:174`).
- **Recursos:** cada `Slot` tiene su ámbito, que se cancela al borrar la bóveda (los colectores de su `AutoBackup` se van con él); como mucho 10.
- **Estado:** las maestras y el nombre escrito viven en `remember` del diálogo (nunca `rememberSaveable` ni `Bundle`) y se borran (`fill('\u0000')`) tras usarse (`VaultPickerScreen.kt`, `NewVaultDialog` `:398`, `DeleteVaultDialog` `:436`).
- **Biometría:** la cola única de `KeyOpsQueue` sigue serializando todas las claves; una clave liberada por la huella va a la tienda de su bóveda (`store.id`), cuya puerta la rechaza si ya no es la activa.
- **Accesibilidad (R-1):** el color de cada bóveda lleva su nombre (`TagColor.label()`), los puntos de color son de 32 dp como objetivo táctil, y el seleccionado se marca con `selected`.

## Textos para el Abogado Digital (revisión final)

Nuevos, en `app/src/main/java/app/seguridad/vault/VaultsTexts.kt`:
- `VAULTS_TITLE` «Bóvedas».
- `VAULTS_SHORT` «Cada bóveda tiene su contraseña maestra, su archivo, su huella y su copia automática. Solo hay una abierta a la vez.»
- `VAULTS_MORE` (bajo «Más información») «Antes de abrirla, la lista dice «Bóveda 1», «Bóveda 2»… y su color; su nombre va cifrado dentro de ella y se ve al abrirla. Cambiar de bóveda bloquea la que está abierta. Este móvil guarda sin cifrar cuántas bóvedas hay, su color y su orden.» (O-1: lo que el móvil guarda sin cifrar; la política debe decir lo mismo.)
- Botones y etiquetas: «Abierta», «Elegida», «Elegir», «Cambiar el nombre», «Nombre de la bóveda», «Nueva bóveda», «Crear», «Color», «Subir», «Bajar», «Cambiar de bóveda», «Importar como bóveda nueva», «Borrar esta bóveda», «Contraseña maestra de esta bóveda», «Borrar».
- `VAULT_NEW_SHORT` «La bóveda abierta se bloquea. La nueva empieza vacía y sin copia automática: elige su carpeta en Ajustes.»
- `VAULT_NEW_NEEDS_OPEN` «Para crear o importar otra bóveda, abre antes una de las que tienes.»
- `VAULT_IMPORT_NEW_SHORT` (R-9) «Crea otra bóveda con lo que tiene la copia. No cambia ni borra las bóvedas que ya tienes.» y `VAULT_IMPORT_NEW_DONE` «Copia importada como bóveda nueva».
- `vaultDeleteWarning` (O-2, R-9, a la vista) «Se borra «<nombre>» de este móvil: su archivo, sus adjuntos, su huella y sus ajustes. Las copias ya hechas (automáticas o exportadas) no se borran y se siguen abriendo con su contraseña maestra.»
- `vaultDeleteTypeName` «Para confirmar, escribe su nombre: «<nombre>»».
- `VAULT_DELETED` «Bóveda borrada de este móvil. Las copias ya hechas siguen donde estaban.», `VAULT_DELETE_NAME_WRONG` «El nombre no coincide: no se ha borrado nada.», `VAULT_DELETE_OPEN_FIRST` «Para borrar una bóveda, ábrela con su contraseña maestra.», `VAULT_DELETE_LAST` «Es la única bóveda: para borrarla, usa «He olvidado la contraseña maestra» en la pantalla de bloqueo.», `VAULT_FAILED` «No se pudo hacer. Inténtalo de nuevo.»
- `TOO_MANY_VAULTS` (`VaultRegistry.kt`) «Puede haber como mucho 10 bóvedas. Borra alguna para crear otra.» y `VAULT_NAME_TOO_LONG` (`VaultLimits.kt`) «El nombre de la bóveda puede tener como mucho 100 caracteres. Acórtalo.»
- Los nombres de los colores: rojo, naranja, amarillo, verde, turquesa, azul, morado, gris.
- Cambiado en Ajustes («Coste de Argon2id para lo nuevo», bajo «Más información»): «Es de cada bóveda. Se usa al crear esta bóveda, al cambiar su contraseña maestra y, con ella elegida, al cifrar textos y archivos. Lo ya cifrado guarda su coste. …» (antes: «Se usa al crear la bóveda, al cambiar la contraseña maestra y al cifrar textos y archivos.»).
- Para la política (tarea 12): el móvil guarda sin cifrar cuántas bóvedas hay, su color y su orden (en `vaults.json`, almacenamiento privado, fuera de las copias del sistema); el nombre va cifrado; cada bóveda tiene sus ajustes y su clave de huella; borrar una bóveda no borra sus copias.
- Ningún texto presenta varias bóvedas como forma de ocultar una (O-1); ninguno dice «irrecuperable» ni «borrado seguro» (O-2). Comprobado en `vaultTextsSayWhatStays` y por `SourceTextsTest` (sin palabras prohibidas).

---

# Ronda 1 de arreglos (revisión `task-2-review.md`)

Commit `f6b98b7` «2.0, tarea 2: ronda 1 de arreglos de la revisión», sobre `4056f34`. La ronda se cortó una vez por el límite de la API; los cambios a medias del árbol se volvieron a revisar antes de seguir (la mutación temporal de `switchTo` estaba deshecha).

## Cambios

**I-1 (ruling «T2, I-1, seguridad»): la marca del reloj de A11 es de cada bóveda.**
- `VaultSettings.clockHighWaterMs` (`settings/Settings.kt:72`); `Settings` ya no lo tiene. `seeingClock` y `resettingClock` pasan a `VaultSettings`. La migración de `decodeSettings` lo lleva solo a `principal`, porque el campo tiene el mismo nombre que en la 1.4.3.
- `AppContainer`: `clockSeen` y `clockLowered` por id. `clockMark(id)` (`AppContainer.kt:242`). `rememberClock` sube la marca de todas las bóvedas de la lista (`:249`). `resetClock(id, now)` solo baja la suya (`:261`). `startClock` (`:271`) da a una bóveda nueva la marca más alta de ese momento (`:531`). `forgetClock` actúa al borrar una bóveda (`:552`, `:598`). `saveClock` escribe todas las marcas en una sola escritura y solo las de la lista, así que una bóveda borrada no vuelve. Cada tienda recibe `clockHighWater = { clockMark(id) }` y `onClockReset = { resetClock(id, it) }` (`:361`, `:363`).
- `VaultStore.unlockLocked` solo baja la marca si es la bóveda activa (`VaultStore.kt:534`).

**I-2 (ruling «T2, I-2»).**
- `importAsNewVault` comprueba la copia y su contraseña antes del cambio. `newVault(name, before, made)` (`AppContainer.kt:534`) ejecuta `before` (`load` + `importReplace`) en la tienda nueva mientras la de antes sigue activa. La puerta mantiene bloqueada la nueva, y la de antes sigue abierta. Solo después vienen `switchTo` y `made` (`unlock(master)`, que es una segunda derivación, y el nombre). Si falla, no se cambia de bóveda y no queda nada de la nueva.
- Los resultados de «Bóvedas» viven en `AppContainer`:
  - `vaultsNotice`, un `Notice<String>`, y `vaultsBusy`;
  - `runVaultsAction` (`:646`) traduce la excepción a un texto: el del tope, el de `VaultsRefused`, el de la tienda o `VAULT_FAILED`;
  - `launchVaultsAction` (`:666`) lo lanza en `appScope`.

  `VaultNav` enseña el aviso en un diálogo («Entendido»), también después del cambio (`AppNavHost.kt:219`). La pantalla ya no lleva su propio `busy` ni enseña sus resultados en un `snackbar`. Tampoco llama a `onBack` tras un cambio: la navegación se rehace sola.
- `canAddVault` lanza ahora `VaultsRefused(VAULT_NEW_NEEDS_OPEN)`, una subclase de `IllegalStateException`, para que el aviso diga por qué.

**M-1.** Un hueco vacío enseña «Borrar esta bóveda» con solo el aviso `VAULT_DELETE_EMPTY_WARNING` (`VaultPickerScreen.kt:181`, `:339`). Un hueco con un archivo apartado dice `VAULT_DELETE_DAMAGED`.

**M-2.** `isEmptySlot(id)` (`AppContainer.kt:621`) es verdadero solo si la carpeta no tiene `vault.bin` (tampoco uno que no se leyó), ni `vault.bin.bad-…` ni `att.bad-…`. En cualquier otro caso, `deleteVault` en `NoVault` devuelve `false` y no borra nada (`:583`).

**M-3.** `newVault` y `deleteVault` usan `slot(id).store` (`:534`, `:580`). El *getter* `autoBackup` sale de `_activeVault.value.id` (`:421`).

**M-4.** `lateWriteNeverLandsInAnotherVault` funciona así:
- un `CoroutineDispatcher` que avisa cuando el guardado de A llega al hilo de cálculo, que está retenido;
- `a.state.first { Locked }` confirma que el cambio empezó;
- `withTimeoutOrNull(1 000) { switch.await() }` tiene que dar `null` (el cambio espera), y A sigue siendo la activa;
- luego se suelta el hilo, y el guardado acaba en A (`true`) y no en B.

No hay `Thread.sleep`. La puerta se suelta en un `finally`, así que un fallo termina el test en vez de colgarlo.

**M-5.** `recordAutoBackup` y `recordImportCopy` (ahora `internal`) no cambian nada si el id ya no está en la lista (`AppContainer.kt:731`, `:746`).

**M-6.** `VaultScopes` guarda un `ViewModelStore` por id. `VaultScoped` lo vacía en el `onDispose` de un `DisposableEffect(vault)`, y solo si la bóveda ya no es la activa (`AppNavHost.kt:120`, `:141`). Una recreación de la actividad lo conserva.

**Otros.** Un comentario de `BackupCheckScreen.kt:98` decía `Settings.clockHighWaterMs`; ahora dice `VaultSettings.clockHighWaterMs`, de cada bóveda. Se adaptaron las pruebas del reloj: `AppContainerTest` usa `clockMark(PRIMARY_VAULT_ID)` y `principal.clockHighWaterMs`, `ClockMarkTest` usa `VaultSettings(...)` y `PerVaultSettingsTest` lee la marca de la 1.4.3 en `principal`. Lo que comprueban no cambia.

## Tests que lo cubren

- I-1: `masterOfOneDoesNotHealTheClockOfAnother`. Comprueba que:
  - con el reloj atrasado, la maestra de B cura solo la marca de B, y la huella de A sigue en `CLOCK`;
  - una bóveda nueva empieza con la marca más alta;
  - siguen en verde los tests BOV-1 del reloj (`AppContainerTest`) y `ClockMarkTest`.
- I-2: `importAsNewWithAWrongPasswordStaysWhereItWas`. Con una contraseña equivocada:
  - el aviso es el de la tienda y no se cambia de bóveda;
  - A sigue abierta y el registro sigue con una bóveda.

  Una importación buena deja el aviso después del cambio. `importAsNewTwiceMakesTwoVaults` sigue en verde.
- M-1 y M-2: `anEmptySlotGoesWithoutMasterButASetAsideFileStays`. Un hueco que vació «He olvidado» se borra sin maestra. Con `vault.bin.bad-1`, `deleteVault` da `false` y el archivo y la bóveda se quedan.
- M-4: el test reescrito, con la mutación de abajo.
- M-5: `aLateCopyResultOfADeletedVaultIsDropped`.
- M-3 y M-6 no tienen test propio. M-3 es un contrato sin carrera que se pueda reproducir de forma determinista. M-6 es de la interfaz y queda para el flujo de Maestro de la tarea 11.

## Comandos y salida

- RED de I-1, comportamiento: una prueba temporal con la API de `4056f34` (`ClockRedProbeTest`, que se borró después) dio `ClockRedProbeTest > masterOfOneDoesNotHealTheClockOfAnother FAILED — java.lang.AssertionError: expected:<CLOCK> but was:<null>`.
- RED de compilación de los tests nuevos: `e: AppContainerVaultsTest.kt:271:31 Expression 'clockMark' of type 'Long' cannot be invoked as a function`, `Unresolved reference 'runVaultsAction'`, `'vaultsNotice'`…
- GREEN enfocado:
  - `./gradlew :app:testDebugUnitTest --tests 'app.seguridad.AppContainerVaultsTest' --tests 'app.seguridad.AppContainerTest' --tests 'app.seguridad.settings.*'` → BUILD SUCCESSFUL;
  - `AppContainerVaultsTest tests="11" failures="0"`, `AppContainerTest tests="8" failures="0"`, `ClockMarkTest tests="3" failures="0"`.
- Mutación de M-4: con `old.store.awaitIdle()` comentado en `switchTo`, el resultado fue `AppContainerVaultsTest > lateWriteNeverLandsInAnotherVault FAILED — the switch waits for the save of A expected:<null> but was:<kotlin.Unit>`. Se volvió a poner. Un primer intento colgó el test, porque la puerta no se soltaba al fallar; se arregló con el `finally`.
- Final, sobre el árbol que se ha guardado:
  - `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --no-daemon --max-workers=1` → BUILD SUCCESSFUL, **1980** tests, 0 fallos;
  - `./gradlew -p jvm-check test --no-daemon --max-workers=1` → BUILD SUCCESSFUL, **1973** tests, 0 fallos;
  - después, solo cambió un comentario (`BackupCheckScreen.kt:98`), y `:app:compileDebugKotlin` sigue sin errores.

## Revisión experto-seguridad-cripto (I-1)

- **Amenaza:** alguien con el móvil desbloqueado, una huella aceptada por el móvil y la maestra de otra bóveda B atrasa el reloj y abre B. Antes, eso bajaba la marca única y la huella de A volvía a valer pasados los 7 días. Es CWE-287: una credencial de otro ámbito relajaba la regla.
- **Defensa:** cada bóveda tiene su marca. Solo `resetClock(id, …)` la baja, y solo la llaman la tienda de esa bóveda, después de comprobar su propia maestra: `unlockLocked` solo si es la activa (`VaultStore.kt:534`), `confirmMaster` y las dos importaciones. Las horas vistas siguen subiendo todas las marcas (`AppContainer.kt:249`), así que la regla «la marca solo sube» vale para cada bóveda frente a las demás. Una bóveda nueva o importada empieza en la más alta (`:271`, `:531`). Un hueco nuevo no puede bajar la marca de otra, y el ruling sobre `canAddVault` se sostiene.
- **Persistencia:** cada marca va en su `VaultSettings`. La de la 1.4.3 pasa a `principal`, que es la única bóveda que existía. Si la escritura falla, las marcas en memoria siguen contando (BOV-1). Una escritura que empezó antes de un reinicio de la marca no borra ese reinicio (`clockResets`). Nunca se escribe la marca de una bóveda que ya no está en la lista.
- **Lo que no cambia:** la holgura de 5 minutos, la regla de los 7 días, `biometricRefusal` (`Vault.kt`), la especificación de las claves del Keystore y el orden de las comprobaciones de `unlockWithKey`.
- **Resto:** la marca sigue siendo un dato local sin firmar en el almacenamiento privado. Con *root* se puede cambiar, igual que en la 1.4.3. Ese caso está fuera del modelo de amenaza de A11.

## Revisión Ingeniero Móvil Senior

- **Ciclo de vida:**
  - el aviso y el estado de ocupado de «Bóvedas» viven en `AppContainer`, como `masterChanged`, y sobreviven al cambio de bóveda que rehace la navegación;
  - las acciones corren en `appScope` con `NonCancellable` (`AppContainer.kt:646`), así que salir de la pantalla no las corta;
  - `VaultScoped` ya no tiene efectos secundarios en la composición (`AppNavHost.kt:141`).
- **Concurrencia:**
  - `clockLock` protege los mapas de marcas, y `clockIds()` lee el registro dentro de él, sin llamadas que bloqueen;
  - `newVault` y `deleteVault` ya no dependen de qué bóveda está activa (`slot(id).store`);
  - el test del foco 2 es determinista y termina aunque falle.
- **Coste:** «Importar como bóveda nueva» deriva la clave dos veces (al importar y al abrir). Es el precio de comprobar la contraseña sin bloquear la bóveda abierta. `KdfLock` sigue serializando las derivaciones.
- **Interfaz:** `isEmptySlot` se lee en `Dispatchers.IO` (`VaultPickerScreen.kt:181`). El borrado de un hueco tiene su propio diálogo con el aviso de O-2 a la vista.

## Textos para el Abogado Digital (revisión final), nuevos en esta ronda

- `VAULT_DELETE_EMPTY_WARNING`: «Se quita esta bóveda vacía de este móvil: no tiene ningún archivo de bóveda. Las copias ya hechas, si las hay, no se borran.» (O-2)
- `VAULT_DELETE_DAMAGED`: «Esta bóveda tiene un archivo que no se pudo leer y que se guarda para recuperarlo: no se puede borrar desde aquí.»
- Los avisos de «Bóvedas» (`VAULT_DELETED`, `VAULT_IMPORT_NEW_DONE`, `VAULT_NEW_NEEDS_OPEN`, `VAULT_DELETE_NAME_WRONG`, `VAULT_FAILED` y los textos de la tienda, como «Contraseña incorrecta o datos dañados») salen ahora en un diálogo con «Entendido» después del cambio. Los textos no cambian.
- Para la política (tarea 12): cada bóveda tiene su propia marca del reloj. Es un dato técnico, no personal, y está en `settings.json`, en el almacenamiento privado.

---

# Ronda 2 de arreglos (dos menores de `task-2-rereview-1.md`)

Commit `6c05936` «2.0, tarea 2: ronda 2 de arreglos (dos menores de la segunda revisión)», sobre `7e44cd1`.

## Cambios

1. **Dos toques, una sola acción.** `launchVaultsAction` (`AppContainer.kt:676`) marca `_vaultsBusy` con `compareAndSet(false, true)` antes de lanzar nada, en el mismo hilo que el toque. Si ya estaba marcado, no hace nada. Luego lanza `runMarked` (`:657`), que hace el trabajo y desmarca en su `finally`. `runVaultsAction` (`:651`), la llamada suspendida de los tests, usa el mismo `compareAndSet`: si hay otra acción en marcha, devuelve `null` sin ejecutar nada.
2. **Cada borrado tiene su resultado y su texto.**
   - `deleteVault` (`:579`) devuelve `VaultDeletion`, con cinco casos:
     - `DELETED`;
     - `WRONG_NAME`;
     - `LOCKED`: la bóveda se bloqueó entre medias, o `confirmMaster` no pudo comprobar la maestra;
     - `KEPT_DAMAGED`;
     - `LAST_VAULT`: la única bóveda (`:582`), que antes salía como `false` y la pantalla del hueco vacío lo tomaba por un archivo dañado.
   - `deletionText(outcome)` (`VaultsTexts.kt:67`) da un texto distinto para cada caso. Texto nuevo: `VAULT_DELETE_LOCKED` (`:61`).
   - Los dos diálogos de «Bóvedas» (con maestra y nombre, y el del hueco vacío) enseñan `deletionText(...)` (`VaultPickerScreen.kt`).
   - Una contraseña equivocada sigue lanzando `CryptoFailure`, que `runVaultsAction` convierte en el texto de la tienda.

## Tests

- `twoTapsRunTheActionOnce`: dos `launchVaultsAction` seguidos, con el primero parado en un `CompletableDeferred`. La acción se ejecuta una vez, sale su aviso, la marca de ocupado se quita y la siguiente acción, después, sí se ejecuta.
- `eachDeletionOutcomeSaysWhatHappened`: con una sola bóveda (vacía), `deleteVault` da `LAST_VAULT`, no un archivo dañado. Cada resultado tiene su texto y los cinco textos son distintos.
- Los asserts de `deleteVault` de `deleteNeedsMasterAndName`, `anEmptySlotGoesWithoutMasterButASetAsideFileStays` y `aLateCopyResultOfADeletedVaultIsDropped` comparan ahora el resultado exacto: `WRONG_NAME`, `DELETED`, `LAST_VAULT` y `KEPT_DAMAGED`.

## Comandos y salida

- RED del menor 1, con el código de `7e44cd1`: una prueba temporal (`BusyRedProbeTest`, borrada después) dio `BusyRedProbeTest > twoTapsRunTheActionOnce FAILED — java.lang.AssertionError: expected:<1> but was:<2>`.
- RED del menor 2: no compila (`VaultDeletion` y `deletionText` no existían).
- La primera versión del test esperaba con `advanceUntilIdle` y leía el aviso sin que estuviera puesto (`expected:<uno> but was:<null>`). Ahora espera con `vaultsNotice.first { it != null }` y `vaultsBusy.first { !it }`.
- GREEN enfocado: `./gradlew :app:testDebugUnitTest --tests 'app.seguridad.AppContainerVaultsTest' --tests 'app.seguridad.vault.VaultRegistryTest'` → BUILD SUCCESSFUL, sin fallos.
- Final:
  - `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --no-daemon --max-workers=1` → BUILD SUCCESSFUL, **1982** tests, 0 fallos;
  - `./gradlew -p jvm-check test --no-daemon --max-workers=1` → BUILD SUCCESSFUL, **1975** tests, 0 fallos.

## Revisión experto-seguridad-cripto

- El menor 1 evita que dos acciones de «Bóvedas» se ejecuten a la vez. Por ejemplo, dos «Nueva bóveda» que derivarían a la vez, o un borrado junto a una importación. El `changingVaults` del contenedor ya las serializaba; ahora la segunda ni siquiera llega a empezar. No cambia ninguna regla de claves ni de borrado.
- Con el menor 2 no se borra nada que antes no se borrara: el único caso de borrado sigue siendo `DELETED`, con las mismas comprobaciones (maestra, nombre, hueco sin archivos). Solo cambia lo que se dice, que ahora es cierto en cada caso. La regla de este proyecto es que ningún texto afirme algo falso.

## Revisión Ingeniero Móvil Senior

- **Concurrencia:** el `compareAndSet` sobre el `MutableStateFlow` es atómico y se hace antes de `appScope.launch` (`AppContainer.kt:676`), así que dos toques en el mismo fotograma no pueden lanzar dos acciones. El `finally` de `runMarked` deja la marca libre también si la acción falla.
- **API:** `VaultDeletion` cambia el tipo que devuelve `deleteVault`. Sus llamadas son la pantalla y los tests, y las dos están al día.

## Textos para el Abogado Digital (revisión final), nuevos en esta ronda

- `VAULT_DELETE_LOCKED`: «La bóveda se bloqueó: no se ha borrado nada. Ábrela y vuelve a intentarlo.»
- Con una sola bóveda, el resultado del borrado vuelve a ser `VAULT_DELETE_LAST` (texto ya apuntado), en vez del texto del archivo dañado.
