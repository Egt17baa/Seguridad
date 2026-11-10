# Clavero 1.4.0: arreglos de la revisión final (code-review, R66 a R69)

**Rama:** `ccr-d1e75160-p5f4wc`, sobre `d46e978`. **Hallazgos:** los 8 de la línea «Final review: code-review (high)…» de `progress.md:305`. **Decisiones:** R66 a R69 (`progress.md:306-309`). **Commit:** uno, «1.4.0: arreglos de la revisión final (R66 a R68)» (el controlador apunta su SHA). Sin subagentes.

Rutas de Kotlin relativas a `app/src/main/java/app/seguridad/`; las de los tests, a `app/src/test/java/app/seguridad/`. Las líneas son las del árbol de este commit.

## TDD

Escribí primero los tests; para el de R66, `applyImport` recibió antes el parámetro `sinceLock` sin efecto, para que el test compilara y fallara por el comportamiento. Sobre el código de `d46e978` fallaron los cinco:
- `vault/VaultStoreImportTest.aLockSinceSavingStartedMakesTheImportStale` (`:121`): `expected:<Stale> but was:<Done(added=3, replaced=0)>`;
- `imports/MergePlanTest.aCodeInsideALoginIsADuplicate` (`:470`): `expected:<[b]> but was:<[]>`;
- `imports/MergePlanTest.aFullEntryReplacedSpendsNoTagBudget` (`:483`): `expected:<0> but was:<1>`;
- `tools/slip39/SecretShareInputTest.aPhraseDropsWhatDrawsNothing` (`:25`): `ㅤ expected null, but was:<La palabra 1 no es de la lista BIP-39 en inglés.>`;
- `tools/slip39/Slip39Test.caseSpacingAndUnicodeFormsAreAccepted` (`:293`, el bucle nuevo): `Slip39Failure: La palabra 5 de la parte 2 no es de la lista SLIP-39.`

Con los arreglos pasan. Un test de antes cambió: `PlatformSourcesTest` («Importar de otra app holds what it read…», `:515-518`) fijaba el texto `store.applyImport(plan, default, overrides)`; ahora fija la llamada con `sinceLock = since` y que hay dos (`confirm` y `copyTo`), cada una con su `val since = store.lockEpoch`.

`totpKeyOf` con `hexLower` y el CSV de una sola pasada no cambian ningún resultado, así que no tienen un test que falle antes: los guardan los tests que ya había (`vault/TotpImportTest`, los de duplicados de 2FA de `MergePlanTest`, `imports/CsvTest` y los de los lectores de CSV), que siguen en verde.

## Cambios

- **R66 (code-review 1, R37):**
  - `VaultStore.applyImport` (`vault/VaultStore.kt:691`) tiene un parámetro nuevo, `sinceLock: Int? = null`. Dentro de `change`, que corre con el mutex cogido, si se da y `epoch` ya no es él, devuelve `null`, y el resultado es `ApplyResult.Stale` (`:697-698`). KDoc `:676-679`.
  - `ImportScreen.confirm` y `copyTo` (`ui/screens/ImportScreen.kt:331` y `:356`) guardan `store.lockEpoch` justo después de `startSaving` y de `copyPlaceChosen`, y se lo pasan a `applyImport` (`:339`, `:359`).
  - Así, un bloqueo durante la copia de antes de importar cancela la importación aunque la bóveda se abra otra vez antes de guardar. R17 y R37 siguen como dice `CLAUDE.md`, «Pantalla de importar»: durante la copia, la copia se rechaza o, si ya estaba hecha, se queda (`ImportFlow` no la toca), y no se importa nada (`ImportOutcome.NotApplied(Stale)`). Un guardado ya empezado termina, porque la comprobación es la primera del bloque con el mutex. Sin `sinceLock` (los tests de antes), `applyImport` hace lo mismo que antes (`applyAfterALockIsStaleToo` sigue en verde).
- **R67 (code-review 2):** el `Matcher` de la bóveda (`imports/MergePlan.kt:91`, `Matcher(codesOfEveryType = true)`) saca la clave del 2FA de toda entrada que lleve `totp`, también de un acceso (`:577`), como `totpImportEntries`. El del archivo sigue sacándola solo de las entradas `TOTP`, así que lo que se cuenta como repetido dentro del archivo no cambia. «Reemplazar» sobre ese acceso no cambia nada: `replaceWith` devuelve la entrada de otro tipo tal cual. La fila del duplicado enseña el emisor y la cuenta del 2FA del archivo («Banco»), como en cualquier duplicado de 2FA. KDoc de `planMerge` (`:82-84`) y del `Matcher` (`:546-550`).
- **R68 (code-review 3, 6, 7 y 8):**
  - `TagBudget.tagsFor` (`imports/MergePlan.kt:140`) recibe `onto`, las etiquetas de la entrada de la bóveda en un «Reemplazar» (`mergeImport`, `:225`): solo da y cuenta las que `replaceWith` puede añadir (las que no tiene, hasta `MAX_TAGS`). Una entrada ya con 10 etiquetas no gasta el cupo de 100. `foldersNotTags` y `lost` siguen siendo verdad: `lost` compara lo que traería `importTags` entero con lo que quedó, y las que no caben por `MAX_TAGS` ya no las habría añadido `replaceWith`, así que no cuentan como perdidas;
  - `totpKeyOf` (`vault/TotpImport.kt:11`) usa `hexLower` en lugar de `"%02x".format` por byte. Mismo resultado;
  - `Slip39.normalizedWord` (`tools/slip39/Slip39.kt:323`) y `cleanPhrase` (`tools/slip39/SecretShareInput.kt:51-70`) quitan lo que dice `drawsNothing` (`core/Invisible.kt`), por puntos de código, en lugar de `Character.getType == FORMAT`. Ahora se quitan también U+3164, U+2800, U+FE00 o U+034F. Los signos que preceden a una cifra (U+0600…), que son Cf, ya no se quitan, pero una palabra con uno no es de la lista y da el error de siempre. Los vectores oficiales de SLIP-0039 y de BIP-39 siguen en verde. KDocs en `Slip39.kt:206` y `:307-311`, y en `SecretShareInput.kt:28-30`;
  - `readCsvImport` (`imports/CsvFormats.kt:82`) usa `parseCsvOf` (`imports/Csv.kt:31-52`), una sola pasada: calcula el delimitador una vez, lee la cabecera una vez, decide el formato con ella y, si lo conoce, sigue leyendo con el mismo lector. Si la cabecera no se lee o no es un formato que se conoce, devuelve `null` sin leer más, como antes. `csvHeader` y `parseCsv` se quedan, para los tests.
- **R69 (code-review 4 y 5), no se cambian:** el coste aceptado está en una línea del KDoc de `ImportScreen.decode` (`ui/screens/ImportScreen.kt:291-293`: toda la lectura, no solo la derivación, va con `KdfLock`, acotada a 16 MB y a un árbol de 48 MiB) y de `VaultStore.applyImport` (`vault/VaultStore.kt:688-689`: la bóveda se codifica dos veces con el mutex cogido, acotado por `BACKUP_MAX_ENTRY_BYTES` y solo al importar).

## Puerta

Con el *wrapper*, `ANDROID_HOME=/opt/android-sdk` y `--max-workers=1`:
- `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets --no-daemon --max-workers=1`: **BUILD SUCCESSFUL** con `--rerun-tasks` (tras el reinicio del contenedor), **1 897 tests, 0 fallos y 0 omitidos** (1 893 + 4 nuevos; el de Slip39 es un bucle nuevo dentro de un test que ya estaba); lint con 0 errores y 17 avisos (los mismos); 0 avisos `w:`.
- `./gradlew -p jvm-check test --no-daemon --max-workers=1`: **BUILD SUCCESSFUL** con `--rerun-tasks`, **1 892 tests, 0 fallos y 0 omitidos** (1 888 + 4); 0 avisos `w:`.
- `bash scripts/check-manifest.sh app/build/outputs/apk/debug/app-debug.apk`: **OK**.

## Revisión experto-seguridad-cripto

- **R66** cierra una carrera entre comprobar y usar (decisión (b), CWE-367): la época se lee en el hilo principal cuando empieza el guardado, y se compara con el mutex cogido, en la misma sección crítica que la escritura (`vault/VaultStore.kt:697-698`), así que un bloqueo y un desbloqueo en medio no dejan pasar un plan hecho antes del bloqueo. No cambia nada de lo guardado ni de la copia: la copia cifrada de antes de importar se queda, y no se importa nada.
- **R67** iguala la regla de duplicados de «Importar de otra app» con la de `totpImportEntries` (una sola regla para el 2FA, `vault/TotpImport.kt:7-11`). No hay una entrada nueva con el mismo secreto que ya tiene un acceso, y «Reemplazar» no escribe sobre el acceso.
- **R68:** `drawsNothing` es la lista fija que ya usan la bóveda, los nombres y el Inspector (R49), así que una parte de SLIP-0039 o una frase BIP-39 pegadas con un invisible que no es Cf se leen como se ven. Solo se lee mejor lo que el usuario escribe: nada cambia en la derivación (PBKDF2 de Bouncy Castle, R4), en las sumas de comprobación ni en el secreto. `totpKeyOf` sigue poniendo a cero los bytes decodificados (`vault/TotpImport.kt:11`).
- **Sin primitivas, formatos, dependencias, permisos ni hosts nuevos.** Sin logs. Ningún secreto nuevo en memoria: `parseCsvOf` borra el lector en un `finally` (`imports/Csv.kt:49-50`), como `parseCsv`.

## Revisión Ingeniero Móvil Senior

- **R66, en la pantalla:** `store.lockEpoch` se lee en el hilo principal antes de `viewModelScope.launch` (`ui/screens/ImportScreen.kt:331`, `:356`), y el guardado sigue en `withContext(NonCancellable)`: salir de la pantalla no lo corta (R17). No hay estado nuevo en la pantalla, ni en `rememberSaveable` ni en un `Bundle`.
- **Coste:** `parseCsvOf` lee la cabecera una vez y calcula el delimitador una vez (antes, dos veces cada cosa), lo que ahorra una pasada por un archivo de hasta 16 MB. `hexLower` evita un `String.format` por byte. `tagsFor` con `onto` hace un `filter` sobre como mucho 10 etiquetas. `drawsNothing` es una búsqueda binaria corta por punto de código.
- **Concurrencia:** `planMerge` y la lectura siguen donde estaban (`Dispatchers.Default`). R69 deja la lectura entera con `KdfLock` y la doble codificación con el mutex, apuntadas como coste para la 1.4.1.
- **Kotlin:** `Matcher(codesOfEveryType)` es `private`; `parseCsvOf` es `internal` y genérico, sin reflexión. `jvm-check` lo compila todo y no da ningún aviso `w:`.

## Textos para el Abogado Digital (revisión final)

- **Ningún texto de la interfaz ni del README cambia.**
- **Cambia, sin texto, lo que se ve:**
  - un 2FA del archivo cuyo secreto ya está en un acceso de la bóveda sale como repetido de ese acceso, no como entrada nueva; «Reemplazar» no lo cambia;
  - una parte de «Repartir un secreto» o una frase BIP-39 pegada con un invisible que no es de formato (U+3164, U+2800…) se lee;
  - un bloqueo durante la copia de antes de importar cancela la importación también si la bóveda se abre otra vez antes de guardar: no se importa nada y la copia se queda, como dice el README («Importar de otra app» y P-8).
