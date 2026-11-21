# Revisión de la tarea 2 de Clavero 2.0: varias bóvedas

Rango `643512a..4056f34` (`b9af0cf` es el commit del registro del controlador). Revisión hecha sobre el paquete del diff, con comprobaciones puntuales fuera de él (citadas abajo). No se ha ejecutado la suite ni se ha tocado el árbol.

Leído antes: `CLAUDE.md`, `.claude/skills/experto-seguridad-cripto/SKILL.md` (y la lista de `references/auditoria.md` y `cripto-moderna.md` sobre separación de claves y Keystore), y las skills `auditoria-seguridad-movil`, `seguridad-criptografia-movil` y `desarrollo-movil-senior`. Requisitos: el *brief* de la tarea 2, `docs/superpowers/handoff/sdd-2.0/progress.md` (opción A, «2 ↔ 8», M-8 → tarea 2, las líneas «Ruling (T2, …)»), los cuatro puntos del controlador y el diseño («Varias bóvedas», «Revisión inicial del Abogado Digital», O-1 enmendado, O-2, R-1, R-9).

### Spec Compliance

✅ **Cumple el *brief* y las enmiendas**, con las dos desviaciones ya aceptadas por el controlador (las tres funciones de StrongBox sin `vaultId`; el hueco de «He olvidado»).

- ✅ Registro (`vault/VaultRegistry.kt`): `PRIMARY_VAULT_ID`, `VaultInfo(id, color, order)` sin nombre (opción A), `dirOf` (`principal` → `files/vault/`, los demás → `files/vaults/<32 hex>/`, cualquier otra cadena da `IllegalArgumentException`, `VaultRegistry.kt:90`), `create` con 16 bytes del CSPRNG y `VaultLimitExceeded` en la 11.ª (`:97`), `setColor`, `reorder`, `remove` (nunca la última, `:130`), `setLastOpened`, `writeTextAtomically` bajo `Mutex`, reconstrucción desde las carpetas con `principal` primero (`:163`).
- ✅ Nombre cifrado: `VaultData.name` (`VaultData.kt`), tope de 100 puntos de código en `checkStructure` (`VaultLimits.kt`), `VaultCrypto.create(..., name)` (`Vault.kt:290`), `VaultStore.rename` (`VaultStore.kt:894`), `displayName` con `plainReportText` y «Bóveda N» (`VaultRegistry.kt:49`).
- ✅ `AppContainer`: `activeVault`, `vault` como *getter*, `switchTo` (`AppContainer.kt:404`), `createVault`, `importAsNewVault`, `deleteVault` (`:511`) con maestra y nombre comparado con `compareKey`; borra carpeta, clave de la huella (`forgetBiometric`), ajustes y devuelve el permiso de la carpeta si nadie más la usa; no toca las copias; la última no se borra.
- ✅ Ajustes: `Settings.perVault: Map<String, VaultSettings>` con el coste, la copia automática, A6 y las copias de antes de importar, tal cual (ruling 2 ↔ 8); globales: bloqueo, portapapeles, tema, aviso, marca del reloj, A14, aviso del nombre. Migración de un `settings.json` de la 1.4.3 a `perVault["principal"]` (`SettingsStore.kt:151`), que no resucita una bóveda borrada (comprobado en `oldSettingsMoveToPrincipal`).
- ✅ Huella: alias `seguridad_vault_biometric` y preferencias `vault_biometric` para `principal`, `<base>_<id>` para las demás (`perVaultName`, `BiometricVault.kt:115-121`); una sola `KeyOpsQueue`. `AppNameTest` sin tocar.
- ✅ Punto (1) del controlador: `key(vault)` con `VaultScoped` (`AppNavHost.kt:113-169`) rehace la navegación y vacía los `ViewModel` de la bóveda anterior; `SeguridadApp`, el portapapeles A14, los lugares de los selectores y `QrHandoff` siguen `vaultState` (el `flatMapLatest` de la activa). `MainActivity` no cambia y solo usa el *getter* `container.vault` (comprobado: `MainActivity.kt:57`, `:113`), así que sigue a la activa.
- ✅ Punto (2): alias y preferencias por bóveda, la primera intacta, una cola.
- ✅ Punto (3): borrar pide abrirla (o `confirmMaster` si ya lo está) y escribir su nombre; la confirmación dice a la vista que las copias ya hechas no se borran (`vaultDeleteWarning`, O-2, R-9).
- ✅ Punto (4): una bóveda nueva nace v4 con `vaultTag` (`VaultCrypto.create` → `wrap` con `newVaultTag`).
- ✅ M-8: el paso pendiente sale a `vault/PendingUpgrade.kt`; las propiedades públicas de `VaultStore` no cambian.
- ✅ Tests del *brief*: los 15 nombrados existen, más 5 de regresión (fuentes, textos, nombres por bóveda, orden y color, `newVaultNeedsAnOpenVault`).
- ✅ Textos: M-3 (`VAULTS_SHORT` a la vista, `VAULTS_MORE` bajo «Más información»), O-1 (lo que el móvil guarda sin cifrar), R-1 (cada color con su nombre), R-9.
- ⚠️ La interfaz (`VaultPickerScreen`, `VaultSwitcher`, `key(vault)`) no tiene test de pantalla: lo que digo de ella sale de leer el código (ver I-2), no de ejecutarlo. Queda para el flujo de Maestro de la tarea 11.
- ⚠️ El diseño pone la marca del reloj (A11) entre lo global, y la tarea lo cumple; pero con varias bóvedas esa regla abre el fallo I-1, que el diseño no previó. Hace falta un *ruling* del controlador.
- ⚠️ Los lugares de los selectores (`PickerPlaces`, 1.4.2) siguen siendo de toda la app: la carpeta que usó una bóveda (sus copias, el texto cifrado guardado) se ofrece en otra, y «He olvidado» en una bóveda olvida los lugares de todas. Ni el *brief* ni el diseño dicen si son por bóveda; lo apunto para que el controlador decida (y para la política, si la bóveda B es de otra persona del mismo móvil).
- ⚠️ `VaultRegistry` lee `vaults.json` dentro del constructor de `AppContainer`, que `SeguridadApp` crea `by lazy` en el primer acceso (probablemente en el hilo principal). Son unos cientos de bytes y está documentado; no lo cuento como hallazgo.

### Strengths

- **La puerta está en el sitio correcto.** El único sitio que guarda la clave y pone `Unlocked` es `VaultStore.opened` (comprobado: `VaultState.Unlocked(` y `dek = ` solo aparecen en `VaultStore.kt:461` y `:466`), y comprueba `active()` bajo el mismo `synchronized(opening)` que `lock()` (`VaultStore.kt:452`). `switchTo` cierra la puerta (`activeId = id`, `AppContainer.kt:410`) antes de bloquear y espera al mutex (`awaitIdle`). Así se cumple «una abierta a la vez» aunque una pantalla vieja conserve su tienda, y el implementador lo probó con una mutación (quitar `|| !active()` hace fallar `onlyOneUnlockedAtATime`).
- **Separación de claves limpia.** Cada bóveda tiene su DEK, su Argon2id, su archivo y su alias del Keystore; la clave liberada por la huella va a la tienda cuyo `id` se pidió, y AES-GCM rechaza la de otra bóveda (`StaleBiometricKey`).
- **`lockEpoch` de toda la app** monótono entre bóvedas (`AppContainer.kt:390`): una copia pendiente del A14 de una bóveda no se acepta en otra.
- **El registro no puede apuntar fuera** de `files/vault/` ni de `files/vaults/<32 hex>/` (`dirOf` y el filtro de `read`); `../vault` se rechaza con test.
- **La migración de ajustes** es pequeña y correcta: lee por los nombres de los campos y no vuelve a escribirlos arriba.
- **`VaultScoped`** conserva los `ViewModel` en una recreación de la actividad y los cancela al cambiar de bóveda: buena solución para el punto (1) del controlador sin tocar cada pantalla.
- **COV2-1 visto y medio cerrado** por el propio implementador (`canAddVault`), y su resto dicho con franqueza.
- **Secretos de la interfaz** en `remember` del diálogo, nunca en `rememberSaveable` ni en argumentos de navegación; las maestras se borran con `fill('\u0000')`.

### Issues

#### Critical

Ninguno.

#### Important

**I-1. La maestra de cualquier bóveda baja la marca del reloj (A11) de todas: la regla del reloj y la de los 7 días de la huella de una bóveda se saltan con la maestra de otra.**
- **Dónde:** la marca es una sola para toda la app (`AppContainer.kt:226` `clockMark`, `:240` `resetClock`) y cada bóveda recibe `onClockReset = ::resetClock` (`AppContainer.kt:320`). La bajan `VaultStore.unlockLocked` (`VaultStore.kt:532`, antes incluso de que la puerta de `opened` mire si la bóveda es la activa), `confirmMaster` (`:639`) y las dos importaciones (`:1129`, `:1150`). La regla de la huella de cada bóveda compara con esa marca (`Vault.kt:514`: `now < highWater - CLOCK_SLACK_MS -> CLOCK`).
- **Qué pasa:** en la 1.4.3 solo había una maestra, así que «un desbloqueo con la maestra baja la marca» (A11) era seguro. Con varias bóvedas, la maestra de B baja la marca con la que se protege la huella de A.
- **Impacto:** alguien con el móvil desbloqueado, la maestra de *cualquier* otra bóveda y una huella registrada en el móvil (la del dueño dormido o forzado, o la suya si el móvil es compartido) puede usar la huella de A pasados los 7 días. Basta con atrasar el reloj a menos de 7 días del último `masterCheckedAt` de A, abrir B con su maestra (la marca baja a ese «ahora») y volver a A: ni `CLOCK` ni `MASTER_DUE` la rechazan. La maestra de B no es difícil de tener: puede ser la bóveda de un familiar en el mismo móvil, o una que crea el propio atacante en un hueco vacío (el resto de COV2-1 que el controlador aceptó). Por eso el *ruling* «no abre ninguna bóveda existente» no se sostiene del todo: no abre A, pero debilita la regla que limita su huella.
- **Severidad:** Important, no Critical: hace falta una huella aceptada por el móvil y tocar el reloj del sistema.
- **Arreglo:** que solo la maestra de una bóveda pueda bajar la marca de esa bóveda. Por ejemplo, una marca por bóveda (`VaultSettings.clockHighWaterMs`, con la de la 1.4.3 pasada a `principal`), que `onClockSeen` sube en todas y `onClockReset` baja solo en la suya; o la marca global que solo sube, más un suelo bajado por bóveda. Como el plan pone la marca entre los ajustes globales, decide el controlador. Test que falle hoy: `masterOfOneDoesNotHealTheClockOfAnother` (A con la huella activada y su `masterCheckedAt`; reloj atrasado; desbloquear B con la maestra; `A.biometricRefusal` debe seguir siendo `CLOCK`). De paso: en `unlockLocked`, `onClockReset` debería ir después de la puerta (una tienda que no es la activa no debería bajar nada).

**I-2. La pantalla «Bóvedas» pierde todo lo que dice después de un cambio de bóveda; con una contraseña equivocada en «Importar como bóveda nueva», el usuario acaba, sin ningún mensaje, ante su bóveda bloqueada.**
- **Dónde:** `VaultPickerScreen` es la ruta `vaults` dentro de `key(vault)` (`AppNavHost.kt:169`, `:351`). `run` (`VaultPickerScreen.kt:177`) lanza en `rememberCoroutineScope` y enseña el resultado con `snackbar.showSnackbar(it)` (`:192`), fuera del `NonCancellable`. `createVault`, `importAsNewVault` y `deleteVault` cambian de bóveda (`AppContainer.kt:465-476`, `:511`).
- **Qué pasa:** en cuanto cambia `activeVault`, `key(vault)` desecha la pantalla, su `SnackbarHost` y su `scope`; `showSnackbar` se cancela y no sale nada. Así:
  - «Bóveda borrada de este móvil. Las copias ya hechas siguen donde estaban.» (`VAULT_DELETED`, `:338`) no se ve nunca.
  - «Copia importada como bóveda nueva» (`:357`) tampoco.
  - Peor: con la contraseña de la copia equivocada (el caso más común), `newVault` cambia a la nueva, la importación falla y `dropVault` vuelve a la anterior, **bloqueada**. El usuario sale de «Bóvedas» a la pantalla de desbloqueo de la bóveda que tenía abierta, sin «Contraseña incorrecta» ni nada. Pasa lo mismo con cualquier fallo de `createVault` después del cambio, por ejemplo con el disco lleno. El test `importAsNewTwiceMakesTwoVaults` lo confirma sin decirlo: tras la maestra equivocada, `first` es la activa y ya no está abierta.
- **Por qué importa:** M-3 manda que los errores y las confirmaciones estén a la vista. El texto de después de borrar repite O-2 y R-9, y el aviso de antes del borrado sí se ve. Además, equivocarse de contraseña bloquea una bóveda que no tenía nada que ver.
- **Arreglo:**
  1. En «Importar como bóveda nueva», comprobar la contraseña y la copia antes de crear el hueco y cambiar de bóveda (descifrar la copia fuera de la tienda, con el `KdfLock`, y pasar lo leído). Una contraseña equivocada deja la bóveda abierta como estaba y se puede volver a escribir.
  2. Guardar el resultado de crear, importar y borrar en `AppContainer`, igual que `masterChanged` y `renameNotice` (por ejemplo, `vaultsNotice: StateFlow<String?>`), y que `VaultNav` lo enseñe después del cambio.
  - Test: una prueba de `AppContainer` en la que una contraseña equivocada en `importAsNewVault` deja la bóveda de antes abierta; y una de fuentes, o el flujo de Maestro de la tarea 11, para el mensaje.

#### Minor

**M-1. Un hueco vacío no se puede borrar desde la interfaz.** Con la bóveda activa en `NoVault`, la pantalla solo dice «Para borrar una bóveda, ábrela con su contraseña maestra.» (`VaultPickerScreen.kt:283`), aunque no hay ninguna maestra que escribir, y no ofrece el botón. La API sí lo permite (decisión 2 del informe). Estos huecos salen de «He olvidado» en una bóveda que no es la primera y de una creación o importación cortada por la muerte del proceso. Son, además, el resto de COV2-1 y la vía más fácil de I-1, y el dueño no puede quitarlos. Arreglo: para un hueco vacío, «Borrar esta bóveda» con solo el aviso de O-2, sin maestra ni nombre.

**M-2. La excepción «sin archivo» de `deleteVault` mira el estado, no los archivos.** `deleteVault` se salta la maestra y el nombre con `VaultState.NoVault` (`AppContainer.kt:517`). Pero `load()` también da `NoVault` cuando el archivo está dañado y se apartó como `vault.bin.bad-<hora>` («kept for recovery», BOV-2) y cuando la lectura da `IOException` con el archivo todavía ahí (`VaultStore.kt:334-348`). En los dos casos, `registry.remove` borra la carpeta entera, el archivo para la recuperación incluido, sin maestra ni nombre. Arreglo: eximir solo si la carpeta no tiene `vault.bin` ni ningún `.bad-`; si los tiene, negarse (o pedir una confirmación que diga que se borra la bóveda dañada). Test con un `vault.bin.bad-1` en la carpeta.

**M-3. `newVault` y `deleteVault` trabajan sobre la activa y no sobre la bóveda que nombran.** Las dos llaman a `switchTo(id)` y después usan el *getter* `vault`: `make(vault)` (`AppContainer.kt:472`) y `val store = vault` (`:515`). Si otro `switchTo` se cuela entre medias (solo `switching` lo serializa, no `changingVaults`), `deleteVault` comprobaría la maestra y el nombre de otra bóveda y luego borraría `id`, y `importAsNewVault` haría `importReplace` sobre otra bóveda. Hoy el `busy` de la pantalla lo impide, pero el contrato es frágil. Lo mismo con `autoBackup` (`:378`), que sale de `activeId`, escrito antes que `_activeVault`: durante un cambio, `container.vault` y `container.autoBackup` son de bóvedas distintas. Arreglo: `slot(id).store` en las dos, y `autoBackup` a partir de `_activeVault.value.id`.

**M-4. `lateWriteNeverLandsInAnotherVault` no fuerza el entrelazado que dice probar.** Ordena con dos `Thread.sleep(200)`, y sus aserciones pasan en cualquier orden (si el cambio empieza antes, `upsert` da `false` y también pasa). Arreglo: una señal desde dentro del cálculo (un `CountDownLatch` que el hilo de cálculo baja al empezar la derivación del guardado de A) antes de lanzar `switchTo`, y comprobar que `switch` no termina antes de `gate.countDown()`.

**M-5. Un resultado tardío de la copia automática resucita los ajustes de una bóveda borrada.** `recordAutoBackup` y `recordImportCopy` usan `withVault(id)` (`AppContainer.kt:607`, `:622`), que añade una entrada por defecto si no hay ninguna (`Settings.withVault`). Un resultado que llegue después de `withoutVault(id)` (si la cancelación del ámbito de su `Slot` no lo corta antes) deja un `perVault[id]` vacío de una bóveda que ya no existe. No hace daño, pero ensucia `settings.json`. Arreglo: para los resultados, cambiar solo si `id in perVault`.

**M-6. `VaultScoped` vacía la tienda de la bóveda anterior durante la composición.** `remember(vault) { scopes.storeFor(vault.id) }` (`AppNavHost.kt:134-135`) llama a `ViewModelStore.clear()` mientras se compone el árbol nuevo, antes de los `onDispose` del viejo. Hoy funciona, pero es un efecto secundario en la composición. Arreglo: vaciarla en un `DisposableEffect(vault)` (en su `onDispose`), o desde `switchTo`.

**M-7 (aplazado por el controlador, solo lo confirmo):** `VaultStore.kt` sigue en 1 313 líneas y `AppContainer.kt` sube a 629. La importación es la siguiente pieza para sacar de `VaultStore`, y los huecos de `AppContainer` (`Slot`, crear, borrar, cambiar) podrían ir a un `VaultSwitchboard` propio. Para la revisión final.

### Assessment

**Task quality:** Needs fixes

**Reasoning:** Lo importante está bien hecho: la puerta de una sola bóveda abierta, la separación de claves y de alias, el registro sin nombres, la migración de los ajustes y la recreación de las pantallas por bóveda. Ninguno de los riesgos nombrados por el controlador (dos bóvedas abiertas, una escritura tardía en la otra, la huella de A abriendo B, el registro inventando o perdiendo bóvedas, borrar la carpeta o la clave de otra) aparece en el código. Pero hay dos cosas que arreglar antes de cerrar la tarea:
- I-1, una regresión de seguridad que la 1.4.3 no tenía: con varias bóvedas, la maestra de una debilita la regla del reloj y de los 7 días de la huella de otra.
- I-2, un fallo de la pantalla nueva: los resultados no se ven y una contraseña equivocada bloquea la bóveda abierta sin decir nada.

Los menores entran en la ronda (regla del `CLAUDE.md`, «Detalles menores: se arreglan todos»), salvo M-7, ya aplazado.

### Revisión experto-seguridad-cripto

Modelo de amenaza aplicado: otra persona con el móvil desbloqueado y la app bloqueada; una segunda persona con su propia bóveda en el mismo móvil; una pantalla o un `ViewModel` que sobrevive a un cambio de bóveda; la muerte del proceso a mitad; un `vaults.json` dañado o manipulado en el almacenamiento privado (solo con *root*).

- **Separación de claves (CWE-323/320):** bien. Una DEK y un Argon2id por archivo (`VaultCrypto.create`, `Vault.kt:290`); un alias del Keystore por bóveda con la misma especificación de siempre (`setUserAuthenticationRequired`, `setInvalidatedByBiometricEnrollment`, `AUTH_BIOMETRIC_STRONG`, `BiometricVault.kt` `createKey`); las preferencias, por bóveda. La huella de A no abre B: la clave liberada va a la tienda de su `id`, y AES-GCM rechaza una DEK que no es la suya (`masterOfOneDoesNotOpenAnother`, `StaleBiometricKey`).
- **Una abierta a la vez:** bien. La puerta está en `VaultStore.kt:452`, el único sitio que guarda `dek` y pone `Unlocked` (`:461`, `:466`); `switchTo` la cierra primero (`AppContainer.kt:410`).
- **Carreras (decisión (b)):** bien en lo esencial. Un guardado ya empezado termina en su propio archivo; `awaitIdle` espera antes de enseñar la otra bóveda; el `lockEpoch` de la app es monótono. El test del foco 2 no fuerza el entrelazado (M-4), y `newVault`/`deleteVault` dependen de que nadie cambie de bóveda entre medias (M-3).
- **A11 entre bóvedas:** **fallo, I-1.** `resetClock` es global (`AppContainer.kt:240`) y lo llama la maestra de cualquier bóveda (`VaultStore.kt:532`, `:639`, `:1129`, `:1150`); la regla `CLOCK` de cada bóveda lo lee (`Vault.kt:514`). CWE-287 (la autenticación se debilita con una credencial de otro ámbito). Important.
- **Datos en claro (MASVS-STORAGE):** bien. `vaults.json` solo tiene ids aleatorios, colores, orden y la última abierta (`registryHasNoNames`); `settings.json` no tiene ningún secreto ni nombre (`noSecretInSettings`); el nombre va en el blob cifrado con AAD y no está en claro en `vault.bin`. Los dos archivos, en el almacenamiento privado, fuera de las copias del sistema.
- **Rutas (CWE-22):** bien. `dirOf` solo acepta `principal` o 32 hex (`VaultRegistry.kt:90`) y `read` filtra los ids (`:156`).
- **Aleatoriedad:** el id sale de `randomBytes(16)` (el CSPRNG del proyecto) y se comprueba contra la lista y las carpetas.
- **Borrado:** pide la maestra y el nombre (`compareKey`), borra la clave del Keystore y sus preferencias, no promete borrar las copias y no dice «irrecuperable». Pero la excepción «sin archivo» mira el estado y puede borrar sin maestra un archivo dañado apartado para la recuperación (M-2).
- **COV2-1:** `canAddVault` cierra el caso principal. El resto que aceptó el controlador también es la vía más fácil de I-1, y el dueño no puede borrar ese hueco desde la interfaz (M-1).
- **Memoria:** las maestras se borran (`VaultPickerScreen.kt`, los `finally` de `NewVaultDialog`, `DeleteVaultDialog` e `ImportNew`); `lock()` borra la DEK en cada cambio; los `ViewModel` de la bóveda anterior se cancelan. Igual que en el resto de la app, el texto de los campos es `String` (`PasswordField`), y eso no se puede borrar.
- Nada nuevo de red, permisos, componentes exportados, dependencias ni registros con datos. Primitivas solo de `javax.crypto` y Bouncy Castle.

### Revisión Ingeniero Móvil Senior

- **Ciclo de vida y estado (`desarrollo-movil-senior`):** `key(vault)` y `VaultScoped` (`AppNavHost.kt:113-169`) son una buena forma de que ningún `ViewModel` sobreviva a un cambio de bóveda sin tocar cada pantalla, y `VaultScopes`, un `ViewModel` de la actividad, aguanta la recreación. Dos peros:
  - vaciar la tienda en `remember` es un efecto en la composición (M-6);
  - la pantalla «Bóvedas» vive dentro del árbol que su propia acción desecha, así que pierde sus resultados (I-2). El estado que tiene que sobrevivir al cambio va en `AppContainer`, como `masterChanged`.
- **Concurrencia:** dos mutex (`switching`, `changingVaults`) sin orden inverso, sin interbloqueo; `NonCancellable` desde el punto sin vuelta. Usar `slot(id).store` en lugar del *getter* `vault` dentro de `newVault` y `deleteVault` haría el contrato independiente de la interfaz (M-3). `canAddVault` lee el disco; la pantalla lo pide en `Dispatchers.IO` (`VaultPickerScreen.kt:174`), pero `newVault` lo vuelve a llamar en el hilo de quien lo llama (Main desde la pantalla): es un `exists()` por bóveda, aceptable.
- **Recursos:** un `Slot` por bóveda, con un ámbito hijo de `appScope` que se cancela al borrarla; como mucho 10. Los resultados tardíos de la copia pueden dejar un `perVault` vacío (M-5).
- **Keystore y biometría (`seguridad-criptografia-movil`):** la especificación de la clave no cambia; el alias de la primera bóveda se queda (`AppNameTest`); StrongBox se apunta una vez por móvil en `PREFS` (`BiometricVault.kt:118`), y es correcto porque es un dato del móvil; una sola `KeyOpsQueue` para todos los alias. Que la marca A11 sea global es lo que falla con varias bóvedas (I-1).
- **Datos guardados (MASVS-STORAGE):** almacenamiento privado, escritura atómica, sin secretos en `SharedPreferences`; los nombres de las preferencias por bóveda solo dicen el id.
- **Plataforma (MASVS-PLATFORM):** ninguna `Activity`, `intent-filter` ni componente exportado nuevo; el selector nuevo pasa por `rememberDocumentPicker` con `PickerPlace.IMPORT_COPY` (`PlatformSourcesTest`, `DocumentUriTest`: 30).
- **Accesibilidad (R-1):** cada color con su nombre en `contentDescription`, objetivos de 32 dp, `selected` en la activa y en el color elegido. El aviso de la confirmación de borrado está a la vista.
- **Tamaño de los archivos:** `VaultStore.kt` 1 313 líneas y `AppContainer.kt` 629 (M-7, aplazado).
