# Tarea 1 de Clavero 2.0: informe del implementador

Formato v4, `VaultData`, el paso de v3 a v4 y las copias con marca 3. Rama `ccr-0aa27f95-u038cd`, sin push.

## Commits

| SHA | Asunto |
|---|---|
| `9db2dda` | 2.0, tarea 1: bóveda v3 de la 1.4.3 para los tests (decisión 1: generada con el código de `a3ac942` **antes** de tocar `Vault.kt`) |
| `7d13e5a` | 2.0, tarea 1: formato v4, VaultData y el paso de v3 a v4 |
| `0eec41d` | 2.0, tarea 1: el aviso del paso al formato nuevo (decisión 3) |

Entre el primero y el segundo entró `d61007e` del controlador (solo documentos: `VaultData.name`, tarea 2). No toca nada de esta tarea.

## Qué se ha hecho

**Archivo de prueba (decisión 1).** `app/src/test/resources/seguridad/vault-1.4.3.json`, escrito con `VaultCrypto.create` + `VaultCrypto.write` de la 1.4.3 (un test generador temporal, borrado después). Maestra `maestra-de-prueba-143`, KDF 1024/1/1, `masterCheckedAt` 1800000000000, revisión 1. Entradas inventadas: `e1` LOGIN con `passwordHistory` de dos contraseñas, `e2` NOTE, `e3` TOTP y `e4` SEED con la frase de prueba pública de BIP-39. Ninguna lleva adjuntos. Todo queda documentado en `seguridad/README.txt`.

**Formato (`vault/Vault.kt`, `vault/VaultData.kt`).**
- `VERSION = 4`, `VERSION_3`, `VERSION_2`, `VERSION_UNKNOWN`. Se leen las versiones {0, 2, 3, 4} (`Vault.kt:202`). Un archivo sin versión se prueba como 3 y como 2, nunca como 4 (`Vault.kt:207`).
- `fieldsAad`, `keyAad` y `dataAad` no cambian (`Vault.kt:231`, `:238`): la versión sigue siendo uno de los campos ligados.
- `read` devuelve `VaultData`; una v2 o una v3 da `VaultData(entries = lista)` (`Vault.kt:351`). Antes de decodificar se comprueba la profundidad del JSON (`:354`). Las versiones del historial vuelven sin historial ni adjuntos propios.
- `write` solo escribe la 4; con cualquier otra versión lanza `IllegalStateException` (`Vault.kt:414`).
- `afterMasterUnlock` siempre da una 4 (`:446`): pone cada `passwordHistory` también en `history` (una `EntryVersion` por contraseña, con su `changedAt`), crea un `vaultTag` (16 bytes del CSPRNG en hex, `:286`), aplica `fitted()` y vuelve a envolver la clave con la AAD de la 4. La clave de datos es la misma.
- `changeMaster` también escribe la 4.
- `BiometricRefusal.UPGRADE` va antes del reloj y de los 7 días (`Vault.kt:505`). Su texto es `UPGRADE_NEEDS_MASTER` (`:264`).
- Las clases `VaultData`, `CustomField`, `FieldKind`, `NotesFormat`, `EntryVersion`, `Folder`, `SavedView`, `EntryFilter`, `SortOrder` y `TagColor` (8 valores) llevan exactamente los campos del brief. `VaultEntry` tiene los campos nuevos con valor por defecto.
- `EntryType` gana `CARD`, `WIFI`, `LICENSE` y `PASSPORT`, con `v4Only`.

**Topes (`vault/VaultLimits.kt`).**
- `object VaultLimits` con los valores del brief, más `JSON_MAX_DEPTH = 16` (un tope propio).
- `VaultLimitExceeded` es una `IllegalStateException` con un texto fijo que dice qué hacer.
- `fitted()` quita primero las versiones más viejas, por `savedAt`: dentro de cada entrada (20 versiones y 64 KiB) y después en toda la bóveda (24 MiB).
- `checkLimits()` comprueba los campos, el historial, una entrada sin historial de 64 KiB como máximo, las carpetas (número, profundidad y ciclos), las vistas, los colores y el total.
- Los tamaños se miden contando bytes mientras se codifica (`encodeToStream` sobre un contador, `:76`), sin guardar una copia del JSON.

**Tienda (`vault/VaultStore.kt`).**
- `VaultState.Unlocked(data)`, con `entries` (las que no están en la papelera, en el orden guardado) y `trash`.
- `unlock` de una 2 o una 3:
  - calcula la 4 en memoria y abre la bóveda con ella, sin escribir nada (`:514`);
  - con el mutex ya libre, llama a `beforeUpgrade()` (`:487`); si da true, guarda la 4.
- `pendingUpgrade` y `completeUpgrade()` (`:526`): un guardado de contabilidad, así que no provoca ninguna copia automática.
- Mientras el paso espera:
  - `modifyData` y `changeMaster` lanzan `UPGRADE_PENDING` (`:697`, `:898`);
  - `importReplace` lanza `CryptoFailure(UPGRADE_PENDING)` (`:971`);
  - `exportBackup` exporta el archivo v3 del disco.
- `lock()` descarta el paso pendiente (`:635`).
- La huella rechaza antes de probar la clave (`:583`).
- `confirmMaster` ahora solo comprueba la maestra.
- Cada guardado pasa por `fittedForSave(current)` (`:703`).
- La recolección de adjuntos, `exportBackup` y la cuenta de adjuntos de `applyImport` usan todas las entradas, también las de la papelera (`:342`, `:939`, `:793`).
- `VAULT_IMPORT_MAX_BYTES` es ahora de 40 MiB (`:1172`).
- Una copia importada pasa también `checkCopyStructure` (`:1022`).

**Copias (`vault/Backup.kt`, `vault/BackupCheck.kt`).**
- `BACKUP_NEWEST = 3` (`Backup.kt:44`).
- La marca es `"3\n"` cuando `vault.json` es una v4 (`isV4`, `:78`) y la de antes en cualquier otro caso. Cada marca tiene su tope por archivo: 32 MiB con la 3 y 6 MB con la 2 (`:129`, `:308`).
- Un número de marca mayor que 3 da `BACKUP_NEWER`.
- «Comprobar una copia» lee la marca 3 e informa de la versión 3 (`BackupCheck.kt:91`).

**AutoBackup.**
- `copyBeforeUpgrade()` (`AutoBackup.kt:424`) con el prefijo `seguridad-antes-de-2-0-` (`:112`). Comparte el código de `copyBeforeImport`.
- Su `.tmp` se borra con los restos de las demás copias (`:670`), y la copia cuenta como «sin apuntar» (`:696`).
- Se apunta en la lista de copias de antes de importar, así que A6 la borra después de un cambio de maestra.

**AppContainer:** `beforeUpgrade = { autoBackup.copyBeforeUpgrade() is CopyResult.Saved }` (`AppContainer.kt:173`).

**Interfaz (decisión 3; `ui/screens/VaultUpgrade.kt` y `VaultScreen.kt:265`).**
- Un diálogo que no se cierra hasta elegir (`onDismissRequest = {}`), con `FilterObscuredTouches`.
- A la vista: «las versiones anteriores no abrirán la bóveda ni sus copias nuevas», y que la copia sí la abren.
- «Exportar una copia antes» es el botón principal: usa el mismo selector y la misma exportación que «Exportar», borra el documento si la copia falla y después llama a `completeUpgrade()`.
- «Seguir sin copia» es el secundario y llama a `completeUpgrade()`.
- El resto va bajo `MoreInfo`.
- La pantalla de desbloqueo enseña `UPGRADE_NEEDS_MASTER` para `BiometricRefusal.UPGRADE` (`VaultScreen.kt:397`).

**Otros cambios.**
- La `EntryFilter` que ya existía (la de la búsqueda de la lista, `EntryQuery.kt`) chocaba con la del brief: se llama ahora `ListQuery`.
- Los tipos `v4Only` no están en `VAULT_ENTRY_TYPES` hasta la tarea 5. Tienen ramas provisionales en los `when` (`VaultScreen`, `VaultEntryScreen`, `EntryForm`).

## Tests y resultados

Tests nuevos, con los nombres del brief:
- `VaultV4Test`, 11 tests: `v3FixtureOpensAsVaultData`, `masterUnlockUpgradesToV4`, `v4NeverTriedForUnknownVersion`, `v4BindsVersionInAad`, `fingerprintRefusedBeforeUpgrade`, `upgradeInterruptedKeepsV3`, `upgradeWaitsForChoiceWithoutBackupFolder` y `upgradeCopiesFirstWithBackupFolder`, más `aNewVaultIsV4WithATag`, `writeIsOnlyForV4` y `aV4VaultOpensWithTheFingerprint`.
- `VaultLimitsTest`, 6 tests: los 5 del brief más `theLimitsAreTheDesignOnes`.
- `BackupV3Test`, 6 tests: los 6 del brief.
- Uno más en `AutoBackupImportCopyTest` (`theUpgradeCopyHasItsOwnNameAndIsListedForA6`) y una aserción más en `AppNameTest` (`UPGRADE_COPY_PREFIX`).

Tests al día: los que leen `read` como lista (con `.entries` o `VaultData(entries = …)`, por un script mecánico) y los que daban por hecho que la 3 era la versión más nueva:
- `VaultV3Test`: ocho casos de la huella sobre una v2 y del paso con `confirmMaster` se rehicieron en siete, con la semántica de la 2.0 y la misma intención (los archivos reales de la 1.1, las reglas de la identidad y de los contactos, la versión ligada en la AAD).
- `VaultStoreTest`: dos casos de CRY-A02 con un archivo sin versión. Ahora no se prueba la clave y la respuesta es `UPGRADE`.
- `VaultTamperTest`, `BackupTest`, `BackupCheckTest` y `PasswordBytesTest`.
- Los recuentos de selectores de `PlatformSourcesTest` y `DocumentUriTest` pasan de 28 a 29.

Resultados, cada suite completa una vez antes de los commits:
- `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets --no-daemon --max-workers=1`: BUILD SUCCESSFUL. **1943 tests, 0 fallos** (eran 1920 en la línea base). Lint: 20 avisos, todos de configuración de la compilación que ya estaban (versiones de dependencias, `OldTargetApi`, `DiscouragedApi` del manifiesto); ninguno en código de esta tarea.
- `./gradlew -p jvm-check test --no-daemon --max-workers=1`: BUILD SUCCESSFUL, **1938 tests, 0 fallos** (eran 1915).
- El estado del primer commit (`7d13e5a`, sin el diálogo) se compiló y pasó la suite completa de la app por separado.
- Sobre HEAD (`0eec41d`), una pasada final de las dos suites: ver «Pasada final» al final.

## Evidencia de TDD

**RED.** Los tests se escribieron antes que el código. La ejecución fue:

```
./gradlew :app:testDebugUnitTest --tests 'app.seguridad.vault.VaultV4Test' --tests 'app.seguridad.vault.VaultLimitsTest' --tests 'app.seguridad.vault.BackupV3Test' --no-daemon --max-workers=1
```

Falló al compilar, porque la API no existía (30 o más errores). Un extracto:

```
e: AppNameTest.kt:13:28 Unresolved reference 'UPGRADE_COPY_PREFIX'.
e: AutoBackupImportCopyTest.kt:166:99 Unresolved reference 'copyBeforeUpgrade' on receiver of type 'AutoBackup'.
e: BackupV3Test.kt:66:25 Cannot access 'val BACKUP_NEWEST: Int': it is private in file.
e: BackupV3Test.kt:100:41 Unresolved reference 'BACKUP_V3_MAX_ENTRY_BYTES'.
e: VaultLimitsTest.kt:22:69 Unresolved reference 'EntryVersion'.
e: VaultLimitsTest.kt:28:29 Unresolved reference 'VaultData'.
e: VaultLimitsTest.kt:32:33 Unresolved reference 'VaultLimits'.
```

**GREEN.** La misma orden, más `AutoBackupImportCopyTest` y `AppNameTest`, dio BUILD SUCCESSFUL:

```
VaultV4Test tests="11" failures="0" errors="0"
VaultLimitsTest tests="6" failures="0" errors="0"
BackupV3Test tests="6" failures="0" errors="0"
AutoBackupImportCopyTest tests="16" failures="0" errors="0"
AppNameTest tests="8" failures="0" errors="0"
```

Después, la suite completa dio 40 fallos en los tests antiguos que daban por hecho la v3. Se pusieron al día uno a uno (la lista está arriba): 1943 en verde.

## Archivos cambiados

**Código nuevo:**
- `app/src/main/java/app/seguridad/vault/VaultData.kt`
- `app/src/main/java/app/seguridad/vault/VaultLimits.kt`
- `app/src/main/java/app/seguridad/ui/screens/VaultUpgrade.kt`

**Código modificado:** en `app/src/main/java/app/seguridad/`:
- `vault/Vault.kt`, `vault/VaultStore.kt`, `vault/Backup.kt`, `vault/BackupCheck.kt`, `vault/AutoBackup.kt`
- `vault/EntryQuery.kt`, `vault/ContactsView.kt`, `vault/EntryForm.kt`
- `ui/screens/VaultScreen.kt`, `ui/screens/VaultEntryScreen.kt`
- `AppContainer.kt`

**Tests nuevos:**
- `app/src/test/java/app/seguridad/vault/VaultV4Test.kt`
- `app/src/test/java/app/seguridad/vault/VaultLimitsTest.kt`
- `app/src/test/java/app/seguridad/vault/BackupV3Test.kt`
- `app/src/test/resources/seguridad/vault-1.4.3.json`, más su entrada en `README.txt`

**Tests modificados:** en `app/src/test/java/app/seguridad/`:
- `vault/`: `VaultV3Test`, `VaultStoreTest`, `VaultTamperTest`, `BackupTest`, `BackupCheckTest`, `VaultTotpTest`, `VaultStoreImportTest`, `LegacyVaultTest`, `ContactsTest`, `ContactsViewTest`, `EntryEditSessionTest`, `EntryQueryTest`, `AutoBackupImportCopyTest`
- `crypto/PasswordBytesTest`, `imports/MergePlanTest`, `redteam/RedTeamRecheckTest`, `settings/SettingsEditableTest`, `tools/AppNameTest`, `PlatformSourcesTest`, `core/DocumentUriTest`

## Lo que encontré al revisar mi propio diff, ya arreglado

- La línea del diálogo «Las copias que ya tienes no cambian» se quitó. Si la copia automática falla y sale el diálogo, la rotación puede borrar más tarde las copias v3 antiguas, así que la frase podía dejar de ser cierta.
- `upgrade` es ahora `@Volatile`, porque `lock()` lo pone a null desde el hilo principal sin el mutex.
- `beforeUpgrade()` va dentro de un `try`: si lanzara, se trata como «sin copia» y no rompe el desbloqueo.
- Tres decisiones de diseño durante la implementación:
  - **Los topes de una entrada solo se aplican a lo que cambia** (`fittedForSave`). Las notas de la 1.4.3 no tenían tope, así que una entrada antigua de más de 64 KiB habría impedido el paso y el desbloqueo. Ahora el paso y `changeMaster` no rechazan nada, y la estructura y el total se comprueban siempre.
  - **`passwordHistory` se conserva** además de copiarse a `history`. La ficha (`VaultEntryScreen.kt:181`) y «Salud» (`Health.kt:47`) lo siguen leyendo, y quitarlo ya habría dejado de enseñar el historial. La tarea 3 lo sustituye.
  - **La copia de antes del paso exporta el archivo del disco**, con la bóveda abierta y el mutex libre. Si se exportara con el mutex tomado, la exportación se quedaría esperando al mismo mutex.

## Dudas para el controlador

1. **El borde de 24 MiB frente a 32 MiB.** Una bóveda con un JSON de datos a menos de unos 400 bytes de 24 MiB se guarda, pero no cabe en una copia con marca 3: el Base64 del blob cifrado más la envoltura del `VaultFile` pasan de 32 MiB (unos 33 554 472 bytes más unos 400). La exportación diría `BACKUP_DOES_NOT_FIT_V3`. Hay dos arreglos posibles: bajar un poco el tope de datos o subir con margen el tope por archivo de la copia. Decide el controlador.
2. **«Importar de otra app» sigue con 6 MB** (`BACKUP_MAX_ENTRY_BYTES` en `applyImport` y `MergePlan.estimatedOverBytes`). Es más estricto, nunca peor, pero se queda corto para una bóveda v4. Pasar a `BACKUP_V3_MAX_ENTRY_BYTES` obliga a reescalar sus tests (VaultStoreImportTest, MergePlanTest). Lo dejo para una tarea posterior.
3. **Un elemento importado de más de 64 KiB** hace que `applyImport` lance `VaultLimitExceeded` (una `IllegalStateException`). La pantalla de importar enseña entonces su texto fijo de fallo al guardar, no `TooBig`. Convendría tratarlo en el plan de importación.
4. **La copia `seguridad-antes-de-2-0-`** se apunta con las copias de antes de importar, así que A6 la borra después de un cambio de maestra (está bajo la contraseña de antes). La política o el README deberían decirlo.
5. **`importReplace` con el paso pendiente** lanza `CryptoFailure(UPGRADE_PENDING)`, no `IllegalStateException`, para que el diálogo de importar enseñe el texto. Además, el diálogo del paso está delante de todo.
6. **`confirmMaster` ya no migra.** Ese camino ya no se alcanza: la huella no abre una 2 y la maestra deja el paso pendiente.
7. **`checkStructure`** trata como carpeta de arriba una cuya carpeta madre no existe. Con ids repetidos, gana el primero. La tarea 4 puede endurecerlo.
8. Los tipos `v4Only` (`Tarjeta`, `Wifi`, `Licencia`, `Pasaporte`) tienen ramas provisionales y no se ofrecen todavía.

## Revisión experto-seguridad-cripto

Leídos: `SKILL.md` y `references/cripto-moderna.md` y `auditoria.md`, usados como lista de comprobación del diff.

**Cifrado y AAD.**
- Mismo AES-256-GCM con nonce aleatorio de 96 bits por sellado (`seal`, sin cambios) y la misma Argon2id con `PasswordUse.EXISTING` al volver a envolver.
- La versión 4 va en la AAD de la clave envuelta y de los datos (`Vault.kt:231`, `:238`), así que una 4 no pasa por una 3 ni al revés. Lo prueban `v4BindsVersionInAad`, `cambiarLaVersionDeUnArchivoRompeLaApertura` y `deLaVersion4NoSePasaALa3NiALa2`.
- Un archivo sin versión nunca se prueba como 4 (`Vault.kt:207`, `v4NeverTriedForUnknownVersion`): quitar el campo no cambia de AAD.
- La clave envuelta de la 2 con los datos de la 4 se rechaza (`unaClaveEnvueltaDeLaVersion2JuntoAUnosDatosDeLaVersion4SeRechaza`).

**Claves.**
- La clave de datos no cambia al pasar a la 4 (`masterUnlockUpgradesToV4`, `trasLaMaestraSeGuardaLaIdentidadYLaHuellaSigueValiendo`).
- La KEK se borra en `finally` (`Vault.kt:324`, `:341`) y el texto en claro de los datos también (`:367`).
- `completeUpgrade` no copia la clave de datos.
- `lock()` borra la DEK y el paso pendiente.
- No se reduce el coste de ningún eje (`conversionOfAStrongerOldFileKeepsItsCost`, A8 y CR-2 se mantienen).

**Aleatoriedad:** el `vaultTag` sale de `randomBytes(16, SecureRandom)` (`Vault.kt:286`). No es una clave ni un secreto; solo identifica la bóveda.

**Huella.**
- `UPGRADE` rechaza antes del reloj, de los 7 días y de usar la clave (`VaultStore.kt:583`). No se descifra nada y una clave caducada no se confunde con otra cosa.
- La clave se borra (`fingerprintRefusedBeforeUpgrade`, y los dos casos rehechos de CRY-A02 en `VaultStoreTest`).

**Atomicidad (foco 1).**
- Nada se escribe hasta que la copia se hace o el usuario la declina.
- La escritura es atómica: `.tmp`, `fsync` y renombrado.
- `upgradeInterruptedKeepsV3` simula la muerte del proceso a mitad del temporal: el archivo sigue siendo la v3 y se abre con la maestra.
- Un bloqueo descarta el paso (`upgradeWaitsForChoiceWithoutBackupFolder`).

**Entradas hostiles (decisión (a)).**
- Antes de decodificar, un recorrido lineal rechaza el JSON anidado a más de 16 niveles (`VaultLimits.kt:205`, llamado en `Vault.kt:354`). Sin eso, `EntryVersion → VaultEntry → history` es recursivo y una copia hecha a mano (su autor conoce su maestra) podría agotar la pila del decodificador.
- Las versiones del historial pierden historial y adjuntos al leer.
- Una copia con carpetas en ciclo, o por encima de los topes de estructura, se rechaza como dañada antes de cambiar nada (`BackupCheck.kt:178`, `VaultStore.kt:1022`).
- Los topes de lectura: 32 MiB por archivo de la copia con marca 3 y 40 MiB por exportación JSON. Siguen el diseño.

**Las copias.**
- La copia de antes del paso sale del archivo del disco, con la marca 2: la abren las versiones anteriores (`upgradeWaitsForChoiceWithoutBackupFolder` comprueba el byte exacto).
- La marca 3 la rechaza la 1.4.3 con su `BACKUP_NEWER` (su lector solo acepta "1\n" y "2\n" y da `BACKUP_NEWER` para un número mayor). El nuevo `marker4IsNewer` comprueba lo mismo para el lector de la 2.0.

**Registros y secretos:** no se ha añadido ningún log. Los textos son fijos, sin rutas ni excepciones crudas. No hay secretos en ajustes ni en un `Bundle`: el diálogo solo guarda `busy` y `error`, textos fijos.

No he encontrado nada en el diff con severidad Media o mayor. Las dudas 1 a 3 son de alcance y de experiencia de uso, no vulnerabilidades.

## Revisión Ingeniero Móvil Senior

Skills cargadas: `seguridad-criptografia-movil` y `desarrollo-movil-senior`.

**Concurrencia.**
- La copia de antes del paso corre con el mutex libre (`VaultStore.kt:483-495`), porque `exportBackup` toma el mismo mutex. `upgradeCopiesFirstWithBackupFolder` exporta desde dentro de `beforeUpgrade` y prueba que no se bloquea.
- Todo el camino es `NonCancellable`, como `unlock` y `commit` (BOV-3).
- La copia corre en el `scope` de `AutoBackup` y bajo su lock, así que no se mezcla con una copia automática.
- `completeUpgradeLocked(since)` no guarda si hubo un bloqueo desde el desbloqueo.

**Estado.**
- `pendingUpgrade` es un `StateFlow` en la tienda, no en la pantalla: sobrevive a recrear la actividad y se vuelve a calcular en el siguiente desbloqueo si Android cerró la app.
- El diálogo sale en `VaultGate` (`VaultScreen.kt:265`), así que cubre cualquier pantalla de la bóveda.
- `VaultLock.keepOpenOnce` evita que el selector bloquee la bóveda (`VaultUpgrade.kt:102`).
- Un documento a medias se borra (`:77`).
- `FilterObscuredTouches` va en el diálogo, contra el *tapjacking*.

**Rendimiento.**
- Los tamaños se cuentan sin guardar el JSON (`encodeToStream` sobre un contador), una vez por guardado.
- `fitted()` no copia nada si ya cabe.
- Los topes de una entrada solo se miran en las que cambian.
- La lectura de una bóveda de 24 MiB y las copias de 32 MiB caben en el heap; quedan para las pruebas de rendimiento del diseño, con 2 000 entradas.

**Compatibilidad.**
- Se leen siempre: las v2 y v3 (`vault-1.1.json`, `vault-1.4.3.json`, `backup-1.1.sgbak`) y las copias con marca 1 y 2.
- `AppNameTest` sigue igual y ahora fija también el prefijo nuevo.
- No hay dependencias ni permisos nuevos.
- Lint no da avisos nuevos.

## Textos para el Abogado Digital (revisión final)

Textos nuevos o cambiados visibles para el usuario:

**Paso al formato nuevo** (`ui/screens/VaultUpgrade.kt`, `vault/Vault.kt`, `vault/VaultStore.kt`):
- `UPGRADE_NEEDS_MASTER`: «Clavero 2.0 necesita la maestra una vez para pasar la bóveda al formato nuevo.» (el texto del brief, en la pantalla de desbloqueo)
- `UPGRADE_TITLE`: «Formato nuevo de la bóveda»
- `UPGRADE_NOT_BACK` (a la vista, O-3): «Después del cambio, las versiones anteriores de Clavero no abrirán esta bóveda ni sus copias nuevas.»
- `UPGRADE_COPY_FIRST` (a la vista): «Antes, exporta una copia: esa sí la abren las versiones anteriores.»
- `UPGRADE_MORE` (bajo «Más información»): «La copia guarda la bóveda tal como está ahora, cifrada con tu contraseña maestra. Hasta que elijas, la bóveda se puede ver pero no se guarda ningún cambio.»
- `UPGRADE_EXPORT` (botón principal): «Exportar una copia antes»
- `UPGRADE_SKIP` (botón secundario): «Seguir sin copia»
- `UPGRADE_EXPORT_FAILED`: «No se pudo guardar la copia. Vuelve a intentarlo o sigue sin copia.»
- `UPGRADE_SAVE_FAILED`: «No se pudo guardar la bóveda en el formato nuevo. ¿Queda espacio en el móvil? Vuelve a intentarlo.»
- `UPGRADE_PENDING`: «Antes de guardar nada, elige en el aviso si exportas una copia de la bóveda.»

**Topes** (`vault/VaultLimits.kt`):
- `VAULT_TOO_BIG`: «La bóveda no cabe: pasa de 24 MB. Vacía la papelera, borra historial o quita entradas grandes.»
- `ENTRY_TOO_BIG`: «La entrada pasa de 64 KB. Acorta sus notas o sus campos.»
- `TOO_MANY_FIELDS`: «Una entrada puede tener como mucho 30 campos. Quita alguno.»
- `FIELD_NAME_TOO_LONG`: «El nombre de un campo puede tener como mucho 100 caracteres. Acórtalo.»
- `FIELD_VALUE_TOO_LONG`: «El valor de un campo puede tener como mucho 10 000 caracteres. Acórtalo.»
- `HISTORY_TOO_BIG`: «El historial de una entrada pasa de 20 versiones o de 64 KB. Borra su historial.»
- `TOO_MANY_FOLDERS`: «Puede haber como mucho 500 carpetas. Borra alguna.»
- `FOLDERS_TOO_DEEP`: «Las carpetas pueden anidarse como mucho 5 niveles. Mueve alguna más arriba.»
- `FOLDER_CYCLE`: «Una carpeta no puede estar dentro de sí misma. Muévela a otro sitio.»
- `TOO_MANY_VIEWS`: «Puede haber como mucho 50 vistas guardadas. Borra alguna.»
- `TOO_MANY_TAG_COLORS`: «Puede haber como mucho 200 etiquetas con color. Quita el color de alguna.»

**Copias** (`vault/Backup.kt`, `vault/VaultStore.kt`):
- `BACKUP_ENTRY_TOO_BIG_V3`: «Un archivo de la copia pasa de 33 MB» (33 desde la ronda de arreglos, resolución T1-1)
- `BACKUP_DOES_NOT_FIT_V3`: «La bóveda no cabe en una copia (100 MB en total, 33 MB por archivo y 2 000 archivos)»
- `SKIP_ENTRY_TOO_BIG` (`imports/ImportTexts.kt`; una fila de lo que no entra en la vista previa de importar): «Pasa de 64 KB, el máximo de una entrada»
- `IMPORT_REPLACE_TOO_BIG` (`imports/ImportTexts.kt`): «Al reemplazar, una entrada pasaría de 64 KB. Elige «Conservar los dos» o «Saltar» para ella. No se ha importado nada.»
- `IMPORT_TOO_BIG` (cambiado de 20 a 40 MB): «El archivo pasa de 40 MB: no es una copia de la bóveda»

**Nombres de tipos** (`EntryType`, el brief): «Tarjeta», «Wifi», «Licencia», «Pasaporte». Hay que revisarlos con R-4 («Tarjeta bancaria»; «wifi» en minúscula en el texto corrido). Aún no se ofrecen en ninguna pantalla.

**Nombre de archivo:** `seguridad-antes-de-2-0-AAAAMMDD-HHMMSS.sgbak`, en la carpeta de la copia automática.
- **A6 (resolución T1, dudas 4 a 6):** esta copia entra en la lista que borra A6 después de un cambio de maestra, como las copias de antes de importar (R15), porque está bajo la contraseña de antes. La política de privacidad y el README tienen que decirlo, junto a lo que ya dicen de las copias de antes de importar.

**Para las notas y la ficha (O-3):** hay que decir que la 2.0 pasa la bóveda a un formato que las versiones anteriores no abren, que la primera vez pide la maestra (la huella no se ofrece esa vez) y que una copia hecha con la 2.0 no la abre la 1.4.

## Pasada final sobre HEAD (`0eec41d`)

- `./gradlew :app:testDebugUnitTest --no-daemon --max-workers=1`: BUILD SUCCESSFUL, 1943 tests, 0 fallos.
- `./gradlew -p jvm-check test --no-daemon --max-workers=1`: BUILD SUCCESSFUL. Salió UP-TO-DATE, así que el árbol de los commits es idéntico al que dio 1938 tests y 0 fallos.


## Ronda de arreglos: resoluciones T1-1 a T1-3

Commit `9804b28` (2.0, tarea 1: los topes de la copia v4 y de lo importado), sobre `553692a` (las resoluciones del controlador). Las dudas 4 a 6 se aceptan tal como estaban; el punto de A6 sobre `seguridad-antes-de-2-0-` está en «Textos para el Abogado Digital».

### Qué cambió

**T1-1.**
- `BACKUP_V3_MAX_ENTRY_BYTES = 33L * 1024 * 1024`, con los textos «Un archivo de la copia pasa de 33 MB» y «… 33 MB por archivo …» (`vault/Backup.kt`). `VAULT_MAX_DATA_BYTES` sigue en 24 MiB.
- Test nuevo, `BackupV3Test.aVaultAtTheDataLimitFitsInACopy`:
  - 400 notas, ajustadas para que el JSON de datos (con un `vaultTag` de 32) mida exactamente 24 MiB;
  - `addAll` las guarda (el tope admite justo el límite), `exportBackup` escribe la copia, cuyo `vault.json` pasa de 32 MiB y no de 33 (prueba de que con 32 no cabía), e `importReplace` la vuelve a leer igual.

**T1-2.**
- `backupMaxEntryBytes(vaultVersion)` (`vault/Backup.kt`) da 33 MiB para la v4 (marca 3) y 6 MB para las demás (marca 2).
- `VaultStore.applyImport` lo usa en la comprobación previa y en la exacta (`accept`).
- La estimación de la vista previa (`estimatedVaultFileBytes` y `estimatedOverBytes`, `imports/MergePlan.kt`) toma por defecto el de la v4, la única que guarda una importación.
- Hallado al hacerlo: con 33 MiB, unos datos de entre 24 y 24,7 MiB pasaban esas dos comprobaciones y después `fittedForSave` lanzaba `VAULT_TOO_BIG`, que se veía como fallo genérico. Ahora `applyImport` mide los datos ya recortados (`fitted`) y da `ApplyResult.TooBig` con lo que sobra.
- Tests reescalados:
  - `VaultStoreImportTest`: `tooBigIsRefused` (ahora es el tope de datos el que lo rechaza), `aHeavilyEscapedImportIsRefusedBeforeEncoding` (500 × 10 000 caracteres de control, cada entrada por debajo de 64 KiB) y `aFileClearlyTooBigIsRefusedBeforeEncrypting` (600 × 60 000);
  - `MergePlanTest`: `anEstimateOverTheLimitSaysByHowMuch`.
- Test nuevo, `theFitIsTheCapOfTheCopyThatWouldBeWritten`: una importación que pasa de 6 MB entra, su copia se exporta y se vuelve a importar, y `backupMaxEntryBytes` da 33 MiB, 6 MB y 6 MB para las versiones 4, 3 y 2.

**T1-3.**
- `planMerge` deja fuera, antes de buscar duplicados, todo elemento cuya entrada guardada pasaría de los topes de una entrada (`entryFitsImported`). Se mide con un id de la longitud de un UUID, las fechas más anchas y todas las etiquetas de sus carpetas. Primero se cuentan las longitudes de sus textos en JSON (`escapedTextBytes`), así que lo que pasa claramente del tope nunca se codifica.
- Ese elemento sale en `MergePlan.skipped` con `SkipReason.ENTRY_TOO_BIG` («Pasa de 64 KB, el máximo de una entrada», `SKIP_ENTRY_TOO_BIG`), ordenado por índice con lo que deja fuera el lector.
- Mismo caso de fallo genérico, en «Reemplazar»: puede juntar las notas de la bóveda y las del archivo por encima de 64 KiB. Da `ApplyResult.EntryTooBig`. `ImportSession` vuelve a la vista previa con `IMPORT_REPLACE_TOO_BIG`, y ya no es un fallo genérico.
- `VaultLimits.entryLimitProblem()` es la única regla, compartida por `checkLimits`, el plan y `applyImport`.
- Tests nuevos:
  - `MergePlanTest.anItemOverTheEntryLimitIsSkippedInThePlan`: 40 000 comillas, que son 80 KB en JSON; un duplicado grande que no cuenta como duplicado; uno pequeño que entra;
  - `VaultStoreImportTest.anItemOverTheEntryLimitNeverFailsTheSave`;
  - `VaultStoreImportTest.aReplaceThatWouldPassTheEntryLimitIsRefusedWithItsOwnResult`: no se guarda nada, y «Conservar los dos» sí entra;
  - `ImportStateTest.aReplacePastTheEntryLimitSaysSoAndKeepsThePreview`.

### Evidencia (TDD y suites)

- RED: `./gradlew :app:compileDebugUnitTestKotlin --no-daemon --max-workers=1` falla con `Unresolved reference 'ENTRY_TOO_BIG'`, `'SKIP_ENTRY_TOO_BIG'`, `'entryFitsImported'`, `'backupMaxEntryBytes'` y `'EntryTooBig'` (MergePlanTest y VaultStoreImportTest). Los tests de 33 MiB y del tope exacto se escribieron antes de cambiar la constante.
- GREEN, con `./gradlew :app:testDebugUnitTest --tests 'app.seguridad.vault.BackupV3Test' --tests 'app.seguridad.vault.VaultStoreImportTest' --tests 'app.seguridad.imports.MergePlanTest' --tests 'app.seguridad.imports.ImportStateTest' --tests 'app.seguridad.vault.VaultLimitsTest' --no-daemon --max-workers=1`, BUILD SUCCESSFUL:

  | Clase | Tests | Fallos |
  |---|---|---|
  | BackupV3Test | 7 | 0 |
  | VaultStoreImportTest | 16 | 0 |
  | MergePlanTest | 59 | 0 |
  | ImportStateTest | 44 | 0 |
  | VaultLimitsTest | 6 | 0 |

  `aVaultAtTheDataLimitFitsInACopy` tarda 7,1 s.
- Completo: `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --no-daemon --max-workers=1`, BUILD SUCCESSFUL, **1949 tests y 0 fallos**. Lint, 20 avisos: los mismos de configuración de la compilación de antes, ninguno nuevo.
- `./gradlew -p jvm-check test --no-daemon --max-workers=1`: BUILD SUCCESSFUL, **1944 tests y 0 fallos**.

### Revisión experto-seguridad-cripto (ronda de arreglos)

- Ninguna primitiva, AAD ni formato cambia: solo el tope de lectura por archivo con la marca 3, que sube de 32 a 33 MiB. Sigue acotado (decisión (a)) y lo cubre `BACKUP_MAX_READ_BYTES`.
- Lo importado sigue siendo dato hostil: el tamaño de cada elemento se cuenta primero sin codificar (`escapedTextBytes`), así que un archivo con texto que JSON escapa seis veces no se convierte en una cadena mayor (`MergePlan.kt`, `entryFitsImported`).
- Un fallo de tamaño no deja nada escrito: `TooBig` y `EntryTooBig` salen dentro del mutex y antes de `write` (`VaultStore.applyImport`). Los tests comprueban que el archivo queda igual, byte a byte.

### Revisión Ingeniero Móvil Senior (ronda de arreglos)

- El plan corre fuera del hilo principal (`planMerge`, en `Dispatchers.Default` en la pantalla).
- La medida exacta solo se hace con lo que no pasa claramente del tope, contando sobre un flujo, sin guardar el JSON.
- El segundo `fitted` de `applyImport` es un coste aceptado, como R69: solo con «Importar» y acotado por el tope de los datos.
- La vista previa vuelve con su texto y deja elegir de nuevo («Conservar los dos» o «Saltar»), como con `TooBig`.

### Textos nuevos de esta ronda (también en la sección del Abogado)

`SKIP_ENTRY_TOO_BIG`, `IMPORT_REPLACE_TOO_BIG` y los dos textos de 33 MB.

## Ronda 1 de arreglos de la revisión (`task-1-review.md`): I-1, O-3 con carpeta y M-1, M-2, M-3, M-5 y M-7

Commit `fe9e944` (2.0, tarea 1: ronda 1 de arreglos de la revisión), sobre `fd61015`. M-4, M-6 y M-8 se aplazan según la resolución «menores».

### Qué cambió

**I-1** (`vault/VaultStore.kt`).
- `opened()` pone `upgrade`, que cierra las escrituras, pero ya no `pendingUpgrade`. `unlock()` lo pone a true, bajo `opening` y solo si no hubo un bloqueo, cuando hay que preguntar: sin carpeta, cuando la copia automática falla o lanza, o cuando el guardado de después falla. Mientras corre la copia automática nadie lo ve en true, y `UPGRADE_PENDING` cierra las escrituras todo el tiempo.
- `exportBeforeUpgrade(out)` devuelve false sin escribir nada si ya no hay paso pendiente (se guardó, se bloqueó la bóveda o es una v4 de siempre), comprobado con el mutex tomado al hacer la instantánea. Así lo exportado es siempre la v2 o v3 del disco, con la marca 2.
- La usan `AutoBackup.copyBeforeUpgrade` (`copyTo` y `fill` reciben la función de exportar; si da false, `fill` borra el documento) y el botón del diálogo, que borra su documento si da false.
- Cambio de firma: `beforeUpgrade` es `suspend () -> CopyResult?` (null sin carpeta, `Saved(nombre)` o `Failed`) en lugar de `Boolean`. El aviso de O-3 necesita el nombre y M-5 necesita saber si había carpeta. `AppContainer` pasa `autoBackup.copyBeforeUpgrade()` tal cual.

**O-3 con carpeta.**
- `VaultStore.upgradeDone: StateFlow<String?>` guarda el nombre de la copia de antes después de un paso guardado sin preguntar, hasta `upgradeDoneShown()`. `destroy()` lo borra.
- `VaultGate` enseña `VaultUpgradeDoneNotice` (`ui/screens/VaultUpgrade.kt`): un título, dos líneas a la vista y «Entendido». Al cerrarlo de cualquier modo cuenta como visto.
- Vive solo en memoria: si Android cierra el proceso antes de enseñarlo, el aviso se pierde. Lo apunto como límite.

**M-1.** En el camino del paso, `unlock()` borra el `CharArray` de la maestra en cuanto termina `unlockLocked` (después de `afterMasterUnlock`), antes de la copia y del guardado. Quien llama la sigue borrando también.

**M-2.** `writeBackup(…, vaultVersion: Int? = null)`. `exportBackup` le pasa `v.version`, tomada en la instantánea. Solo los tests, que no la pasan, decodifican el archivo (`isV4`).

**M-3.** El diálogo enseña `upgradeExportFailure(e)`: el mensaje del `CryptoFailure` si lo hay, o `UPGRADE_EXPORT_FAILED`, como hace `VaultBackup.kt`.

**M-5.** `VaultStore.upgradeAutoCopyFailed` es true cuando había carpeta y la copia no se hizo, pero no cuando la copia se hizo y falló el guardado. El diálogo pone entonces arriba, a la vista, `UPGRADE_AUTO_COPY_FAILED`.

**M-7.** Con la marca 3, solo `vault.json` admite 33 MiB. Los adjuntos y los nombres que se ignoran se quedan en `BACKUP_MAX_ENTRY_BYTES` (6 MB, por encima de los 5 MB de un adjunto), al leer (`BackupReader`) y al escribir (`writeBackup`).

### Tests que lo cubren

**VaultV4Test**
- `upgradeCopiesFirstWithBackupFolder`, reescrito:
  - dentro de `beforeUpgrade`, `pendingUpgrade` es false, `upsert` lanza `UPGRADE_PENDING` y `exportBeforeUpgrade` da la marca 2 con el v3 byte a byte;
  - después, `upgradeDone` es el nombre de la copia y vuelve a null con `upgradeDoneShown()`;
  - con la copia fallida o una que lanza, `pendingUpgrade` y `upgradeAutoCopyFailed` son true;
  - sin carpeta, `pendingUpgrade` es true y `upgradeAutoCopyFailed` es false.
- Nuevo, `theCopyBeforeTheUpgradeIsNeverTheV4`: después de `completeUpgrade`, en una v4 de siempre y con la bóveda cerrada, `exportBeforeUpgrade` da false con 0 bytes escritos.
- Nuevo, `theMasterIsWipedBeforeTheCopy`.
- `upgradeInterruptedKeepsV3`: si la copia se hizo y falló el guardado, `upgradeDone` es null y `upgradeAutoCopyFailed` es false.

**BackupV3Test**
- Nuevo, `writeBackupTakesTheVaultVersion` (M-2).
- Nuevo, `marker3KeepsTheAttachmentCap` (M-7, el lector y el escritor).

**AutoBackupImportCopyTest**
- `theUpgradeCopyHasItsOwnNameAndIsListedForA6`, ahora sobre la bóveda real de la 1.4.3 con el paso pendiente: la copia sale con la marca 2, y después de `completeUpgrade` da `Failed` sin dejar ningún archivo.
- Nuevo, `aCutUpgradeCopyGoesWithTheNextRotation`.

**VaultUpgradeTextsTest** (nuevo, `app/src/test/java/app/seguridad/ui/screens/`)
- `theExportFailureSaysWhy` (M-3).
- `theNewTextsAreFixedAndClean` (M-5 y O-3, también con `ForbiddenWords`).

**VaultV3Test**, al día con `CopyResult`.

### Órdenes y salida

- RED: `./gradlew :app:compileDebugUnitTestKotlin --no-daemon --max-workers=1` dio `Unresolved reference` para `upgradeExportFailure`, `UPGRADE_AUTO_COPY_FAILED`, `UPGRADE_DONE_*`, `upgradeDoneCopy`, `exportBeforeUpgrade`, `upgradeDone`, `upgradeDoneShown` y `upgradeAutoCopyFailed`; `No parameter with name 'vaultVersion'`; y `Argument type mismatch … 'suspend () -> CopyResult?'` contra `'suspend () -> Boolean'`.
- GREEN: `./gradlew :app:testDebugUnitTest --tests '…VaultV4Test' --tests '…VaultV3Test' --tests '…BackupV3Test' --tests '…AutoBackupImportCopyTest' --tests '…VaultUpgradeTextsTest' --tests '…VaultStoreTest' --tests '…BackupTest' --tests '…SourceTextsTest'` dio BUILD SUCCESSFUL, sin ningún fallo:

  | Clase | Tests |
  |---|---|
  | VaultV4Test | 13 |
  | VaultV3Test | 33 |
  | BackupV3Test | 9 |
  | AutoBackupImportCopyTest | 17 |
  | VaultUpgradeTextsTest | 2 |
  | VaultStoreTest | 76 |
  | BackupTest | 22 |
  | SourceTextsTest | 12 |

- `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --no-daemon --max-workers=1`: BUILD SUCCESSFUL, **1956 tests y 0 fallos**. Lint, 20 avisos, ninguno nuevo.
- `./gradlew -p jvm-check test --no-daemon --max-workers=1`: BUILD SUCCESSFUL, **1949 tests y 0 fallos**.

### Revisión experto-seguridad-cripto (ronda 1)

- **La copia de antes del paso.** `exportBeforeUpgrade` mira `upgrade` con el mutex tomado, donde se hace la instantánea (`VaultStore.export`). Si el paso se guarda después, lo que se escribe no cambia, porque el JSON ya es el del v3. Ninguna copia con la marca 3 puede salir con el nombre `seguridad-antes-de-2-0-` (`theUpgradeCopyHasItsOwnNameAndIsListedForA6`). Así `UPGRADE_COPY_FIRST` sigue siendo cierto.
- **M-1.** La maestra se borra antes de la copia, que puede tardar segundos en el SAF.
- **M-7.** El tope de 6 MB por archivo de la copia vuelve para todo lo que no es `vault.json`.
- **Sin cambios** en el cifrado, la AAD ni el formato.

### Revisión Ingeniero Móvil Senior (ronda 1)

- El diálogo ya no sale mientras se hace la copia automática, así que no se puede elegir a medias ni perder el resultado del selector. Si la exportación da false, el documento se borra (`discardDocumentNow`).
- `upgradeAutoCopyFailed` se escribe antes de `pendingUpgrade`, los dos bajo `opening`. Es `@Volatile` y se lee al componer el diálogo.
- El aviso de después del paso vive en la tienda (un `StateFlow`) y no en la pantalla, así que sobrevive a rehacer la actividad. Con `FilterObscuredTouches`.

### Textos nuevos (para el Abogado Digital, revisión final)

- `UPGRADE_AUTO_COPY_FAILED` (a la vista en el diálogo del paso): «No se pudo hacer la copia automática de antes del cambio.»
- `UPGRADE_DONE_TITLE`: «Bóveda en el formato nuevo»
- `UPGRADE_DONE_NOT_BACK` (a la vista, O-3): «Las versiones anteriores de Clavero ya no abren esta bóveda ni sus copias nuevas.»
- `upgradeDoneCopy(nombre)` (a la vista): «La copia de antes del cambio está en la carpeta de la copia automática: «<nombre>».»
- `UPGRADE_DONE_OK`: «Entendido»
- El diálogo enseña también el motivo de un fallo de la exportación (M-3), con los textos de `CryptoFailure` que ya había, como el del adjunto que falta o está dañado.
