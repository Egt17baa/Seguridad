# Clavero 2.0: informe del equipo rojo (ataque final, decisiones (d) y (f) del dueño, R32)

## Estado

- **Fase 1 (lo que haría fracasar la 2.0): hecha.** Superficies 1 (copias v4 hostiles, fila 52), 2 (paso a v4 y muerte del proceso), 3 (aislamiento entre bóvedas, H-1 y H-2) y 4 (copias a varios destinos y A6).
- **Fase 2 (textos de fuera, QR y portapapeles): pendiente.** Superficies 5 (fila 24), 6, 7 y 8.
- **Fase 3 (barrido de regresión): pendiente.**
- **Hallazgos hasta ahora:** RT-21 (Media), RT-22 (Baja), RT-23 (Informativa).
- **Último commit:** `8c350a3` (fase 1: tests e informe) y, encima, el que pone al día este bloque y la puerta.
- **Última ejecución de Gradle (17-11-2026, cierre de la fase 1):** la app, `:app:testDebugUnitTest --tests 'app.seguridad.redteam.*'`: **verde**, 81 tests, 3 ignorados (RT-21 a RT-23), 0 fallos; después `./gradlew --stop`. Antes, `jvm-check` con `RedTeam20*`: verde con los `@Ignore` (9 tests, 3 ignorados); sin ellos, fallan exactamente los tres `rtN_`.
- **Siguiente paso si se corta la sesión:** fase 2, superficie 5 (fila 24, `tools/qr/WifiQr.kt` frente a ZXing).

## Alcance y modelo de amenaza

He atacado el código de Clavero 2.0 en el *worktree* `/home/user/Seguridad-rt`, rama `rt-2.0`, desde `569e299` (código igual a `cad3ba5`: nada de `app/src/main` cambió desde entonces). Rutas relativas a `app/src/main/java/app/seguridad/` salvo que se diga otra cosa.

Entradas atacadas (decisión (f)): las copias (`seguridad-backup` con las marcas 2 y 3, y la exportación JSON) en «Importar copia», «Importar como bóveda nueva» y «Comprobar una copia»; el registro `vaults.json`; el paso a v4; las carpetas compartidas de las copias automáticas; el bloqueo y el cambio de bóveda; y, en las fases 2 y 3, los QR, el portapapeles, las búsquedas y el barrido de toda la app.

Atacantes: quien consigue que el usuario importe un archivo manipulado (y conoce su contraseña, porque lo hizo él); otra app sin permisos especiales que puede escribir en una carpeta compartida; quien tiene el móvil desbloqueado un momento, con las bóvedas bloqueadas. Quedan fuera root, el *malware* con accesibilidad y la manipulación física, como en la 1.4.0.

**Método:**
- he leído el código siguiendo cada dato de fuera hasta donde se decodifica, se compara, se pinta o se guarda;
- he escrito las pruebas de concepto como tests en `app/src/test/java/app/seguridad/redteam/RedTeam20*Test.kt` (corren en `jvm-check` y en `:app:testDebugUnitTest`);
- he medido la memoria sin provocar nunca un `OutOfMemoryError`: bytes retenidos (`Runtime` antes y después, con `System.gc()`) y bytes asignados por los hilos (`ThreadMXBean.getThreadAllocatedBytes`), que son una cota superior del pico.

**Cómo leer los tests:** un `rtN_…` comprueba el comportamiento **seguro**, así que falla mientras RT-N siga abierto; lleva `@Ignore("RT-N: abierto, ver redteam-report.md")` solo para que la puerta siga en verde. Un `resists…` documenta un ataque que hoy se resiste y queda como test de regresión. La numeración sigue la de la 1.4.0 (acabó en RT-20).

## Resumen

| RT | Superficie | Hallazgo | Severidad (CVSS 3.1) | MASVS | Estado |
|---|---|---|---|---|---|
| RT-21 | 1. Copias v4 (fila 52) | Una copia se decodifica entera antes de comprobar ningún tope, y las etiquetas de una entrada no tienen ninguno: una copia hecha a mano con 2,6 millones de etiquetas cortas (18 MiB de JSON, dentro de los 24 MiB de `checkCopySize`) da un árbol de unos 150 MiB, se importa y «Comprobar una copia» no ve nada raro. En un móvil con menos memoria la bóveda importada no se abre (y nadie captura el `OutOfMemoryError`) | Media: 4,7 (`AV:L/AC:H/PR:N/UI:R/S:U/C:N/I:N/A:H`) | MASVS-CODE-4, MASVS-RESILIENCE (disponibilidad) | Abierto |
| RT-22 | 3. Aislamiento (H-1) | Con dos bóvedas bloqueadas, quien tiene el móvil un momento pasa a la segunda desde la pantalla de bloqueo, la vacía con «He olvidado la contraseña maestra» (una confirmación) y, con ella en `NoVault`, Ajustes le deja cambiar el bloqueo por inactividad y el tiempo del portapapeles de toda la app, que después protegen a la otra: COV2-1 vuelve con varias bóvedas | Baja: 2,4 (`AV:P/AC:L/PR:N/UI:N/S:U/C:N/I:L/A:N`) | MASVS-AUTH-2, MASVS-STORAGE | Abierto |
| RT-23 | 3. Aislamiento (H-2) | «Importar como bóveda nueva» (y «Importar copia») conserva la clave de datos de la copia: la bóveda nueva tiene la misma clave que la bóveda de la que salió la copia. El diseño dice que cada bóveda tiene la suya (`specs/…-design.md:38`, `:62`). No he encontrado ningún camino sin root que lo aproveche: es veracidad del diseño | Informativa | MASVS-CRYPTO-2 | Abierto |

Recuento de la fase 1: **1 hallazgo de severidad media (RT-21), 1 bajo (RT-22) y 1 informativo (RT-23)**; ninguno crítico ni alto. Hay 7 tests de ataques resistidos en la fase 1 (abajo).

## Hallazgos

### RT-21 [Media] Una copia se decodifica entera antes de sus topes, y las etiquetas no tienen tope (fila 52)

**Cómo lo he atacado.** Con la maestra de una copia (la del que la fabrica), he escrito a mano el blob de datos v4 de una bóveda con una sola entrada cuyas `tags` son 2,6 millones de cadenas distintas y cortas (`"0","1",…,"1jz4h"`): 18 MiB de JSON, sellado con AES-256-GCM y la AAD de la versión 4, en una copia con la marca 3. El usuario la importa con «Importar copia» (o «Importar como bóveda nueva»), o la comprueba con «Comprobar una copia».

**Lo que pasa:**
- `VaultCrypto.read` (`vault/Vault.kt:409-427`) solo mira la profundidad antes de decodificar (`jsonNestingWithin`, `:412`; `vault/VaultLimits.kt:254-275`) y después decodifica todo el blob;
- `VaultStore.importReplace` (`vault/VaultStore.kt:1487-1527`) lo decodifica **dos veces** antes de la primera comprobación: dentro de `afterMasterUnlock` (`:1501-1506`, que además lo vuelve a codificar y cifrar) y otra vez en `:1507`; las comprobaciones vienen después (`:1509-1514`). «Comprobar una copia» lo decodifica una vez (`vault/BackupCheck.kt:93`) y comprueba después (`:95-98`);
- `checkCopyEntries` (`vault/BackupCheck.kt:196-212`) no mira las etiquetas de ninguna entrada, ni cuántas ni cómo de largas; `checkCopySize` (`:234-237`) solo mira el tamaño del JSON (24 MiB + 4 KiB). Los topes de una entrada (`entryLimitProblem`, `vault/VaultLimits.kt:194-201`: 64 KiB sin el historial) no se aplican a una copia (`fittedForSave` solo los aplica a lo que cambia, `:170-183`);
- la copia entra entera y la bóveda queda guardada así. Cada desbloqueo la decodifica entera otra vez (`VaultStore.kt:593-596`, `:620`), y la lista junta todas las etiquetas en un `HashSet` en cada composición (`ui/screens/VaultList.kt:302`);
- ninguna de esas llamadas captura `OutOfMemoryError`: ni `importReplace`, ni sus pantallas (`ui/screens/VaultBackup.kt:176-188`, que solo captura `CryptoFailure` y `Exception`), ni el desbloqueo. Solo lo hace «Importar de otra app» (`ui/screens/ImportScreen.kt:308`, R11c).

**Medidas** (JVM de los tests; Android tiene cabeceras de objeto y cadenas comprimidas parecidas):

| Forma del JSON (dentro de los topes de lectura) | Árbol retenido por byte de JSON |
|---|---|
| Etiquetas distintas y cortas en una entrada | **8,1** (y 8,0 en el test de impacto, 2 MiB → 15,7 MiB) |
| `tagColors` (lo rechaza `checkStructure` después de decodificar) | 7,9 |
| Entradas mínimas, historial mínimo | 2,0 a 2,8 |
| Una bóveda real (notas largas en ASCII) | 1,0 |

A 18 MiB, la importación de la copia hostil asignó 1 121 MiB en total frente a 879 MiB la de una bóveda real del mismo tamaño (las dos decodifican dos veces). Proyectado a los 24 MiB que acepta `checkCopySize`, el árbol hostil retenido pasa de 190 MiB, más el texto de la copia, el JSON descifrado y su cadena: no cabe en un *heap* de 192 o 256 MiB, y sí en uno de 512 MiB (la parte A de la revisión importó 8,1 millones de etiquetas vacías con 512 MiB).

**Impacto:**
- en un móvil con poca memoria, importar o comprobar esa copia cierra la app (sin pérdida: nada se reemplaza antes de las comprobaciones);
- en uno con mucha memoria, la copia entra y reemplaza la bóveda. Desde entonces cada desbloqueo necesita ese árbol, y la lista es lenta. Si después el usuario lleva sus copias a un móvil con menos memoria, **la bóveda no se abre allí** y la app se cierra en cada intento: es la «bóveda perdida» del encargo;
- hace falta que el usuario importe la copia del atacante con la contraseña que este le da (o una copia suya manipulada por quien conoce su maestra). Por eso AC:H y UI:R.

**Fila 52, la historia de `MAX_TAGS`** (`git log -S MAX_TAGS` y `git grep` en cada versión publicada):
- `MAX_TAGS = 10` nace con las etiquetas (`af29a78`, 24-10-2026) y está en `1a7f7bd` (v1.3.0) y `ca597be` (v1.3.1) en `vault/EntryQuery.kt:7` y `:41` (`parseTags(...).take(MAX_TAGS)`, el formulario), y en `b92286e` (v1.4.0), `ba903e7`, `bd7b51e` y `a3ac942` (v1.4.3) en `EntryQuery.kt:8` y `:45`, más `imports/MergePlan.kt:162`, `:185` y `:328`;
- desde sus pantallas, ninguna versión publicada guardó más de 10: el formulario corta a 10 y «Importar de otra app» nunca pasa de `max(10, las que ya tenía)` (`MergePlan.kt:328`);
- pero ninguna versión publicada comprobó las etiquetas de una copia importada: la 1.3.0 a la 1.4.3 aceptaban una copia hecha a mano con cualquier número, la guardaban así y sus copias las llevaban después, y «Importar de otra app» las conserva («its own are never dropped», `imports/MergePlan.kt:376` hoy);
- **veredicto:** sí, una versión publicada pudo dejar una entrada con más de 10 etiquetas (solo a partir de una copia hecha a mano). Un tope de 10 al leer podría rechazar una copia propia de esa bóveda: **no se debe poner**.

**Arreglo propuesto** (nunca un tope que rechace una copia propia):
1. **Estimar antes de decodificar**, en `VaultCrypto.read` o en las tres entradas de copias (`importReplace`, «Comprobar una copia» e «Importar como bóveda nueva»), con un recorrido lineal como `jsonTreeEstimate` (`imports/ImportJson.kt:84-170`), y rechazar con `COPY_DAMAGED` lo que pase de un presupuesto pensado para el móvil (192 MiB de *heap*): por ejemplo, unas 3 veces el tamaño de la mayor bóveda real (24 MiB) y nunca por debajo de lo que da una bóveda real en el tope. Así cae cualquier forma que multiplica su tamaño (etiquetas, `tagColors`, carpetas, vistas), no solo las etiquetas;
2. **Un tope generoso de etiquetas por entrada en la copia** (por ejemplo, las que caben en los 64 KiB de una entrada), que una copia propia nunca pase; una entrada con más de 10 de una copia de la 1.x se sigue leyendo;
3. **Capturar `OutOfMemoryError` como última defensa** fuera del hilo principal, en `importReplace`, `checkBackup` y el desbloqueo, como `ImportScreen.kt:308` (R11c), con un texto fijo («La copia no cabe en la memoria de este móvil»), sin dejar la bóveda a medias;
4. **Decodificar una sola vez en `importReplace`**: `afterMasterUnlock` ya tiene los datos; la segunda lectura (`:1507`) dobla el trabajo y la basura.

**Test:** `RedTeam20CopyTest.rt21_aCopyWhoseTreeOutgrowsThePhoneIsRefusedBeforeItIsDecoded` (pide que la copia hostil se rechace y que su importación no asigne más que la de una bóveda real del mismo tamaño, o sea, que se rechace antes de decodificar). Sin el `@Ignore`, hoy falla con:

```
java.lang.AssertionError: A copy whose tree is ~8 times its JSON must be refused (it was imported)
RT-21: real 879 MiB allocated, hostile 1121 MiB, refused=null
```

`rt21Impact_aCheckOfTheCopyFindsNothingWrongAndTheTreeIsSeveralTimesItsJson` pasa hoy: «Comprobar una copia» no ve ningún problema y el árbol es 8 veces el JSON (`RT-21 impact: 2052149 bytes of JSON keep 16119 KiB of tree (x8.0)`).

### RT-22 [Baja] Vaciar otra bóveda abre los ajustes de toda la app (H-1, COV2-1 con varias bóvedas)

**Cómo lo he atacado.** El dueño tiene dos bóvedas, A (con sus datos) y B, las dos bloqueadas. Quien tiene el móvil un momento:
1. en la pantalla de bloqueo, toca el selector de bóvedas (`ui/screens/VaultScreen.kt:439`) y elige B (`ui/screens/VaultPickerScreen.kt:244-250`: `switchTo` no pide nada);
2. en la pantalla de bloqueo de B, «He olvidado la contraseña maestra» → «Borrar bóveda» (`VaultScreen.kt:479`, `:489-498`): una confirmación, aceptada para esa bóveda en el Ruling T2, duda 1;
3. B queda en `VaultState.NoVault`, y `settingsEditable` (`settings/Settings.kt:443`) es verdad con `NoVault`;
4. en Ajustes cambia el bloqueo por inactividad y el tiempo del portapapeles (`ui/screens/SettingsScreen.kt:633`, `:641`), que son de toda la app (`Settings.kt:42-43`).

**Lo que pasa:** de vuelta en A, el dueño tiene su bóveda con «Bloqueo: 15 minutos» en lugar de «Al salir» y el portapapeles a 90 s en lugar de 15 s. La regla que cierra COV2-1 para crear bóvedas (`canAddVault`, `AppContainer.kt:631-632`: «ninguna bóveda del móvil tiene archivo») no es la que usa Ajustes.

**Impacto.** Bajo: los valores posibles están acotados (`LockAfter` hasta 15 min, `ClipboardClear` hasta 90 s, `settings/Settings.kt:17-25`), el coste de Argon2id que cambiaría es el de B (por bóveda), y vaciar B deja una huella que el dueño ve. Pero es exactamente lo que COV2-1 quería impedir: «someone holding the phone for a moment with the vault locked must not … weaken its defences» (`Settings.kt:436-442`). Con una sola bóveda (1.4.3) no pasaba nada parecido, porque vaciar la única bóveda no deja nada que proteger. Si ya hay un hueco vacío (otra «He olvidado» anterior), ni siquiera hace falta borrar.

**Arreglo propuesto:** que `settingsEditable` siga la regla de `canAddVault`: editable con la bóveda activa abierta, o cuando **ninguna** bóveda del móvil tiene archivo (por ejemplo, `settingsEditable(state, anyVaultFile)` desde `AppContainer`). Cambia lo que puede hacer el dueño con un hueco: pide un Ruling.

**Test:** `RedTeam20VaultsTest.rt22_emptyingAnotherVaultDoesNotUnlockTheSettingsOfTheWholeApp`. Sin el `@Ignore`, hoy falla con:

```
java.lang.AssertionError: With vault A locked and its file there, emptying B must not let the lock and the clipboard of the whole app be changed
```

### RT-23 [Informativa] La bóveda importada como nueva comparte la clave de datos (H-2, veracidad del diseño)

**Cómo lo he atacado.** Con la bóveda A abierta, «Exportar copia» y después «Importar como bóveda nueva» con esa copia: nace B.

**Lo que pasa:**
- `importAsNewVault` (`AppContainer.kt:612-624`) llama a `VaultStore.importReplace`, que pasa por `VaultCrypto.afterMasterUnlock` (`vault/Vault.kt:510-549`): con una copia v4 de coste igual solo vuelve a sellar los datos con la misma clave (`:532`), y con una más débil vuelve a envolver **la misma** clave (`:534`). Solo `changeMaster` crea una clave de datos nueva (`:555-563`);
- B tiene la clave de datos de A (y también su sal y su `wrappedKey`, si la copia es v4 con el mismo coste). Lo mismo cada vez que se importa la misma copia como bóveda nueva, y con «Importar copia» sobre otra bóveda;
- el diseño dice lo contrario: «cada bóveda tiene su propia clave de datos, así que los trozos de dos bóvedas no se abren juntos» (`docs/superpowers/specs/2026-11-12-clavero-2.0-design.md:38`) y «la clave de un archivo no abre los datos de otro» (`:62`).

**Caminos buscados sin root, y por qué no llegan:**
- **los archivos:** las dos bóvedas viven en el almacenamiento privado (`files/vault/`, `files/vaults/<id>/`); mezclar el blob de una con el archivo de la otra exige escribir ahí (root). Aun así la AAD ata la revisión y `masterCheckedAt`, que divergen en cuanto cualquiera de las dos guarda;
- **la huella:** cada bóveda tiene su alias del Keystore y sus preferencias (`android/BiometricVault.kt:67`, `:92-124`; `perVaultName`, `vault/VaultRegistry.kt:56`), y cada una envuelve solo la clave de su bóveda; nada usa la de una con la otra;
- **los adjuntos:** cada adjunto se cifra con su propia clave, guardada dentro de su entrada (`vault/Vault.kt:123-140`, `AttachmentRef.key`), y cada bóveda los guarda en su carpeta `att/`; la recogida de basura de una solo mira su carpeta;
- **las copias:** una copia de B se abre con la maestra de B; que la clave de datos sea la de A no da nada a quien no tiene una de las dos maestras.

**Impacto:** ninguno explotable que haya encontrado. Es una afirmación del diseño que no es verdad (ningún texto publicado la hace: el README dice «cada una con su contraseña maestra, su archivo, su huella», `README.md:20`). Si un día una bóveda importada como nueva pierde su maestra, no se recupera con la clave de la otra sin root.

**Arreglo propuesto** (para el Ruling): o (a) corregir el diseño (`:38`, `:62`) con una nota de corrección, como las demás del documento, diciendo que «Importar como bóveda nueva» conserva la clave de datos de la copia; o (b) dar a la bóveda nueva una clave de datos nueva al importarla (descifrar y volver a cifrar los datos con una clave aleatoria y envolverla con la maestra, como `changeMaster`; los adjuntos no cambian, porque tienen sus claves). (b) cambia lo que se escribe al final del ciclo; lo prudente es (a), con el Abogado mirando la frase.

**Test:** `RedTeam20VaultsTest.rt23_aVaultImportedAsNewHasADataKeyOfItsOwn` (la afirmación del diseño). Sin el `@Ignore`, hoy falla con:

```
java.lang.AssertionError: The vault made from A's copy has A's data key
```

Si el Ruling elige (a), el test pasa a ser un `resists…` que fije lo que es verdad (las dos claves iguales y ningún camino entre las bóvedas).

## Ataques intentados y resistidos

### Fase 1, superficie 1: copias v4 hostiles

- **Fechas en los extremos de `Long`, enums desconocidos, `vaultTag` que no es hex y nombres largos** (`RedTeam20CopyTest.resistsHostileDatesEnumsAndNamesInAV4Copy`): `deletedAt`, `expiresAt`, `lastUsedAt`, `lastUnlockAt` y `lastCheckedBackupAt` en `Long.MAX_VALUE` o negativos, `rotateMonths` en `Int.MAX_VALUE`, `notesFormat` «HTML», `sort` «RANDOM», `templatesVersion` negativo, `vaultTag` «<script>». La copia entra, lo que calculan las pantallas no lanza nada (`trashLeavesIn`, `vault/TrashTexts.kt:51-58`, se queda en 0 días; `Expiry.dueSoon`, que acota los años a 1900–2199, `vault/Expiry.kt:100`; `backupCheckNotice`; `displayName`) y al volver a abrir la papelera pone la fecha futura en «ahora» (`vault/Trash.kt:42`). Un nombre de 5 000 puntos de código se rechaza como dañado (`checkStructure`, `vault/VaultLimits.kt:228`). Un enum desconocido sin valor por defecto (el tipo de un campo, un tipo en una vista) hace la copia dañada, sin otro efecto.
- **Un v4 sin su versión, o que dice ser un 3** (`resistsAV4FileWithoutItsVersionIsNeverOpened`): no se abre; los candidatos son 3 y 2 (`vault/Vault.kt:224-225`) y la versión está en la AAD de la clave (`:248`).
- **La marca frente a la versión de dentro** (`resistsTheMarkerAgainstTheVersionInside`): un v4 en una copia con la marca 2 se importa como v4; la marca solo fija el tope por archivo (`vault/Backup.kt:58`).
- **Las claves de Compose hechas con datos de la bóveda** (leído, sin test: `jvm-check` no compila Compose): `ui/screens/VaultList.kt:543` (vistas, `distinctBy { it.id }` en `:539`), `:556` (etiquetas: salen de un `HashSet`, `:302`), `:571` y `:591` (entradas, con prefijo por sección y `checkCopyEntries` rechaza ids repetidos, `vault/BackupCheck.kt:203`), `VaultOrganize.kt:218`, `:319` (carpetas: ids únicos por `checkStructure`), `:402` (`distinctBy`), `:455` (las mismas etiquetas del `HashSet`), `EntryHistoryScreen.kt:94` (`"${savedAt}-$i"`: el índice la hace única aunque se repita `savedAt`), `TrashScreen.kt:111` (el id; el primer `item` no tiene clave, así que su clave por defecto es un índice y nunca choca con una cadena) y `EntryFields.kt:153`, `:159`, `:269`, `:307` (`key(...)` dentro de un bucle, no de una lista perezosa: Compose admite claves repetidas ahí). Las claves fijas (`"search"`, `"tools"`, `"h-a"`…) no tienen `:` y las de datos sí (`lazyKey`, `VaultList.kt:767`): no chocan. No he encontrado ninguna copia que cierre la app al pintarse.
- **`history` hostil** (leído): versiones con su propio historial o adjuntos se leen sin ellos (`withPlainVersions`, `vault/VaultLimits.kt:278-286`); una versión de tipo personal no cambia el tipo al restaurar (Ruling T3, M-2); `savedAt` repetidos no rompen la pantalla (arriba). Los topes de historial no se comprueban en una copia v4 (20 versiones, 64 KiB): se recortan en el primer guardado que toque esa entrada; es parte de RT-21 (memoria), no un fallo aparte.
- **Los tipos `CARD`, `WIFI`, `LICENSE` y `PASSPORT` en una bóveda v3** (leído): se leen (la v3 se decodifica como `List<VaultEntry>` con el enum de la 2.0, `vault/Vault.kt:417`) y pasan a v4 al desbloquear. Ninguna versión anterior los escribe; no rompe nada.

### Fase 1, superficie 2: el paso a v4 y la muerte del proceso

- **Cada punto del paso** (leído y con los tests que ya hay): la copia `seguridad-antes-de-2-0-` se escribe en un `.tmp` y se renombra (`vault/AutoBackup.kt:946-960`), y nada se guarda hasta que se hace o se rechaza (`vault/VaultStore.kt:612-623`, `vault/PendingUpgrade.kt:68-86`); el archivo v4 se escribe con `writeTextAtomically` (`.tmp`, `fsync` y renombrado atómico, `core/AtomicFile.kt:15-23`), así que el disco tiene el 3 o el 4, nunca medio. Lo cubren `VaultV4Test.upgradeInterruptedKeepsV3` (`app/src/test/java/app/seguridad/vault/VaultV4Test.kt:213`) y los de muerte del proceso de A6 (`AutoBackupF3Test.kt:442`, `:479`). Nota menor: el renombrado no hace `fsync` de la carpeta; con ext4/f2fs y un corte de luz justo después, podría volver el archivo anterior, que es uno válido.
- **Un v4 vuelto a v3 a mano** no se abre como 4: `resistsAV4FileWithoutItsVersionIsNeverOpened` (arriba).
- **`fitted()`** (`vault/VaultLimits.kt:121-154`) solo quita historial, nunca entradas ni campos: una bóveda que no cabe sin historial se sigue abriendo y se guarda igual en el paso (sin `checkLimits` en `afterMasterUnlock`). El recorte de historial es silencioso, como dice el diseño.
- **La clave de la huella de otro archivo** (CRY-A02): con un archivo de versión 2 o 3 la huella no se prueba (`BiometricRefusal.UPGRADE`, `vault/Vault.kt:593`); con un v4, una clave que no autentica es `StaleBiometricKey` (`VaultStore.kt:716-720`). Los tests de `VaultStoreTest.kt:1209` y `:1285` lo cubren. Con RT-23, la huella de A sí autenticaría los datos de una B importada de A, pero cada bóveda solo lee sus propias preferencias de la huella (`perVaultName`).

### Fase 1, superficie 3: aislamiento entre bóvedas

- **El registro con ids hostiles** (`RedTeam20VaultsTest.resistsAHostileRegistry`): `../vault`, `/etc`, 32 mayúsculas, 33 cifras, `principal/../../x`, repetidos y 12 bóvedas: solo quedan los 10 primeros ids válidos (`vault/VaultRegistry.kt:150-160`), `lastOpened` hostil cae a la primera, y `dirOf` (`:89-93`) rechaza todo lo demás; cada carpeta queda dentro de `files/vaults/`.
- **El registro dañado** (`resistsADamagedRegistry`): texto que no es JSON, vacío, o sin ningún id válido: se reconstruye desde las carpetas y vuelve la primera bóveda (`:163-174`).
- **Lo de cada bóveda se borra al bloquear y al cambiar** (leído): el QR pendiente (`tools/qr/QrPendingText.kt:39-58`, más `MainActivity.kt:105-119`); la oferta de «Copiar contraseña» (`tools/TwoStepCopy.kt`, solo el id de la entrada, con el contador de bloqueos de toda la app, `AppContainer.kt:910-914`); el aviso A14 (`AppContainer.kt:900-904`); `PickerPlaces` (`AppContainer.kt:917-921`). `lockEpoch` de toda la app sube con cada bloqueo y cada cambio de bóveda y nunca repite valor (`AppContainer.kt:533-540`), así que nada guardado para una bóveda se acepta en otra. Los títulos de las misiones, los filtros, la selección, el deshacer y el formulario están en los ViewModel y en `UndoTrash` con la época de su bóveda (`vault/Trash.kt:55-70`). «Ver en grande» y el QR del wifi, en la fase 2.
- **Resultados tardíos en otra bóveda** (leído y con los tests que ya hay): un guardado que empieza en A y acaba después del cambio cae en A o en ninguna (`AppContainerVaultsTest.lateWriteNeverLandsInAnotherVault`, `aSaveWaitingAcrossALockOrASwitchLandsNowhere`); cambiar la maestra y cambiar de bóveda nunca abre A6 en la otra (`changeOfMasterThenSwitchNeverOpensA6ForTheOtherVault`); «Comprobar una copia» solo apunta la fecha si el `vaultTag` es el de la bóveda (`vault/BackupCheck.kt:130-131`); `markUsed` y la purga de la papelera pasan por `modifyData` con la época y `active()`.
- **A11 y los 7 días por bóveda:** la marca del reloj es por bóveda (Ruling T2, I-1) y `masterCheckedAt` va en cada archivo; `AppContainerVaultsTest.masterOfOneDoesNotHealTheClockOfAnother`.
- **La maestra de una no abre otra:** `AppContainerVaultsTest.masterOfOneDoesNotOpenAnother` (y RT-23 no lo cambia: B importada de A tiene la maestra de A hasta que se cambie, porque es la de su copia).

### Fase 1, superficie 4: copias a varios destinos y A6

- **Otra app que planta, cambia o renombra archivos con los nombres de Clavero** (leído y con los tests que ya hay): la rotación y A6 solo borran por id lo que la propia carpeta apuntó (`vault/AutoBackup.kt:1016-1061`; `owned`, `importCopies` en `:1028`), nunca por el nombre; `AutoBackupTest.filesLeftByAnotherAppWithLaterDatesNeverDisplaceTheRealCopies` (`AutoBackupTest.kt:325`). Las copias `-antes-de-importar-` y `-antes-de-2-0-` nunca se rotan (`:1027-1031`). Un archivo plantado con esos nombres solo sube la cuenta de «copias sin apuntar» del texto de A6 (`:1050-1053`), que ya dice que puede haberlas de otra bóveda o de antes de la 1.2.1 (`vault/A6Text.kt:210-214`): sin efecto.
- **Las sobras `.tmp` se borran por el nombre** (`:1026`): con dos bóvedas que usan la misma carpeta, la rotación de B puede borrar el `.tmp` de una copia de A que sigue escribiéndose después de cambiar de bóveda; la de A entonces falla al renombrar y se aplaza al próximo desbloqueo (`:946-960`, `:969-981`). No se pierde ninguna copia hecha. Informativo, no lo numero.
- **«Comprobar una copia» con una fecha falsa:** la fecha solo se apunta si el `vaultTag` de la copia (cifrado dentro de ella) es el de la bóveda abierta (`vault/BackupCheck.kt:130-131`); otra app no puede fabricar una copia con ese `vaultTag` sin la maestra. Dice la fecha de la comprobación, no la de la copia (O-5).
- **La copia de B nunca acaba en la carpeta de A:** los destinos son por bóveda (`Settings.vault(id).targets`, `AppContainer.kt:453-464`), cada `AutoBackup` exporta su propio `VaultStore`, y crear otra bóveda apaga su copia (fila 8). Lo cubren `AppContainerVaultsTest` y `PerVaultSettingsTest`.
- **El reloj frente al calendario y D-3:** la marca de cambio de D-3 es un SHA-256 de fecha, coste y `vaultTag` (`vault/VaultStore.kt:1760`), y una copia con calendario se salta solo si esa marca no cambió; con el reloj atrasado, `freeName` busca el siguiente segundo libre (`AutoBackup.kt:994-1001`) y la rotación ordena por cuándo se hicieron, no por el nombre (`:1003-1016`).

## Filas 24 y 52

- **Fila 52 (prueba n.º 7):** confirmada y ampliada: es RT-21. La memoria se va al decodificar, antes de `afterMasterUnlock` y otra vez después (`VaultStore.kt:1501-1507`); el `OutOfMemoryError` no se captura en ningún sitio de las copias; una copia que entra en un móvil con mucha memoria deja una bóveda que no se abre en uno con poca. Una versión publicada pudo dejar más de 10 etiquetas en una entrada (desde una copia hecha a mano), así que **no** un tope de 10: estimar antes de decodificar, un tope generoso y la excepción capturada como última defensa (arriba).
- **Fila 24:** pendiente (fase 2).

## H-1 y H-2

- **H-1: confirmada.** Es RT-22 (Baja).
- **H-2: confirmada como hecho, descartada como camino de ataque.** Las dos bóvedas comparten la clave de datos (RT-23, Informativa); no he encontrado ningún camino sin root por el que los datos de una se abran o se mezclen con la otra. Es la afirmación del diseño (`:38`, `:62`) la que no es verdad.

## Necesita prueba en el móvil

- **RT-21:** importar en un móvil con 512 MiB de *heap* una copia con muchas etiquetas que pase los topes, y abrir después su copia en uno de 192 o 256 MiB: hoy la app se cierra en cada desbloqueo. Junto con la fila 51 (una bóveda real cerca de 24 MiB con «Alto»).

## Revisión experto-seguridad-cripto

- **Entradas enormes (decisión (a)):** RT-21 la incumple: el tope de una copia es su tamaño en bytes (`vault/Backup.kt:51`, `vault/BackupCheck.kt:234-237`), no lo que cuesta decodificarla; «Importar de otra app» sí estima el árbol antes (`imports/ImportJson.kt:72-82`, R11a). CWE-400 y CWE-770.
- **Primitivas:** sin cambios; AES-256-GCM con la AAD de la versión (`vault/Vault.kt:213-255`) y Argon2id. RT-23 no debilita ninguna: con una misma clave de datos, cada sellado usa un *nonce* aleatorio de 12 bytes, y el número de sellados sigue muy lejos del límite de 2^32 por clave (NIST SP 800-38D §8.3).
- **Aislamiento:** RT-22 es un fallo de control de acceso (CWE-284) en un ajuste de seguridad; RT-23, una afirmación del diseño sin respaldo en el código.
- **Lo que está bien:** el registro (`dirOf` con patrón exacto), la versión atada en la AAD, la escritura atómica, la papelera con la fecha futura llevada a «ahora», la rotación por id.

## Revisión Ingeniero Móvil Senior

- **Memoria:** en Android el *heap* de una app va de 192 a 512 MiB según el móvil (`ActivityManager.getMemoryClass`); `importReplace` decodifica dos veces y cifra en medio, y el desbloqueo decodifica, codifica y vuelve a decodificar (`VaultStore.kt:593-603`). Con RT-21 eso pasa de cientos de MiB. Ningún `OutOfMemoryError` está capturado en esas rutas (solo en `ImportScreen.kt:308`, `QrInspectScreen.kt:221` y las de imágenes).
- **Compose:** las claves perezosas con datos de la bóveda son únicas por construcción (arriba); la lista de etiquetas se rehace con un `HashSet` en cada cambio de datos (`VaultList.kt:302`), lo que con RT-21 es otro coste por composición.
- **Ciclo de vida:** el borrado al bloquear cuelga del flujo `vaultState` de `AppContainer` y del contador `lockEpoch` de toda la app; ninguna pantalla de la fase 1 guarda un secreto en `rememberSaveable`.

## Textos para el Abogado Digital (revisión final)

- RT-21, si se arregla con la excepción capturada: un texto nuevo del tipo «La copia no cabe en la memoria de este móvil. …» (con su qué hacer, regla M-3).
- RT-22, si cambia la regla: el texto que explica en Ajustes por qué no se puede cambiar algo con la bóveda bloqueada podría tener que decir «con alguna bóveda bloqueada».
- RT-23: la frase del diseño (`:38`, `:62`) si el Ruling elige corregirla; ningún texto publicado la repite.

## Puerta

- Fase 1, `jvm-check` (`--tests 'app.seguridad.redteam.RedTeam20*'`): 9 tests, 3 ignorados (RT-21, RT-22, RT-23), verde.
- Fase 1, la app (`:app:testDebugUnitTest --tests 'app.seguridad.redteam.*'`, con `flock` y después `--stop`): 81 tests (los de la 1.4.0 y los nuevos), 3 ignorados, 0 fallos, verde.
- Sin los `@Ignore`, fallan solo los tres `rtN_`, con las salidas copiadas en cada hallazgo.

## Después (para quien venga)

1. El controlador lleva RT-21 a RT-23, la fila 52, H-1 y H-2 al director, que da un Ruling por RT.
2. Una sola ronda de arreglos, con otro implementador y TDD: quita el `@Ignore`, enseña el rojo, arregla y deja el test en verde. Nunca rompe `SG1:`, `.sgenc`, `seguridad-vault` ni `seguridad-backup`. Su informe: `redteam-fixes-report.md`.
3. El atacante vuelve a comprobar cada arreglo y busca cómo saltárselo (decisión (f)), en `redteam-recheck-N.md`; si no se le puede retomar, lo hace un agente nuevo con el encargo y este informe.
4. Un hallazgo nuevo abre otra ronda. Con 5 rondas, decide el dueño.
5. Al cerrar: las dos suites por separado en un clon limpio, ningún `@Ignore` en `redteam/`, el informe publicado `docs/auditorias/2026-11-17-ataque-2.0.md` (R39) y los informes de trabajo en `sdd-2.0/`.
