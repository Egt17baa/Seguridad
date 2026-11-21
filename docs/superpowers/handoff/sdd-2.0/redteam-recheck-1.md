# Clavero 2.0: re-comprobación 1 del equipo rojo (arreglos de RT-21 a RT-24, decisión (f))

## Estado

- **Hecha.** Base: `4da161e` (la ronda de arreglos registrada, con RT-22 (ii) en `d7f81d1`), en el *worktree* `/home/user/Seguridad-rt`, rama `rt-2.0`.
- **Veredictos:**
  - RT-22, RT-23 y RT-24: **cerrados**;
  - RT-21: **cerrado en lo que pedía** (su test, en verde), pero la re-comprobación encuentra dos caminos alrededor del arreglo: **RT-25** (Media) y **RT-26** (Baja).
- **Un hallazgo nuevo abre otra ronda** (encargo del ataque, «Después», 4).
- **Último commit:** el de «2.0, ataque: re-comprobación 1 de los arreglos (RT-25 y RT-26)».
- **Última ejecución de Gradle (17-11-2026):**
  - `jvm-check`, `--tests 'app.seguridad.redteam.*'`: verde, 102 tests, 2 ignorados (RT-25 y RT-26), 0 fallos;
  - la app, `:app:testDebugUnitTest --tests 'app.seguridad.redteam.*' --tests 'app.seguridad.ui.redteam.*'`: verde, 105 tests, 2 ignorados, 0 fallos; después `./gradlew --stop`.
  - Los `rt21_` a `rt24_` y el `resists…` de RT-23 corren sin `@Ignore` y pasan.

## Resumen

| RT | Arreglo | Veredicto | Severidad |
|---|---|---|---|
| RT-21 | Una sola decodificación con las comprobaciones antes de sellar (`4c85961`); `checkCopyShape` antes de decodificar, con `COPY_LIST_MAX` = 10 000 y `COPY_TREE_MAX_BYTES` = 262 MiB (`47fcec2`); el `OutOfMemoryError` capturado al leer, comprobar e importar una copia y al desbloquear (`78465ca`) | Cerrado: `rt21_` pasa, y los caminos de la lista del director resisten salvo los de RT-25 y RT-26 | — |
| RT-22 | (i) `settingsEditableNow` con la regla de `canAddVault` (`b2b8b0d`); (ii) `mayFillEmptySlot` antes de «Crea tu bóveda» y de «Importar copia» a un hueco (`d7f81d1`) | Cerrado | — |
| RT-23 | Opción (a): el diseño corregido y el test pasado a `resists…` (`f8f3f0d`) | Cerrado (veracidad) | — |
| RT-24 | `visibleText` en la pantalla del QR y la línea `wifiQrNameHidden` (`3779726`) | Cerrado | — |
| **RT-25** (nuevo) | — | Abierto. Una copia que pasa el presupuesto se importa y se abre, pero la lista necesita varias veces más memoria (índice de búsqueda y fichas de etiquetas), sin `catch`: la app se cierra cada vez que se abre la bóveda | **Media**: 4,7 (`AV:L/AC:H/PR:N/UI:R/S:U/C:N/I:N/A:H`), MASVS-CODE-4 |
| **RT-26** (nuevo) | — | Abierto. Con un nombre de miembro escapado (`"tagColors"`), el mapa de colores sale del cálculo de `checkCopyShape`: sus claves no cuentan | **Baja** (sin CVSS: debilita un control, acotado por el tamaño de la copia), MASVS-CODE-4 |

## Hallazgos nuevos

### RT-25 [Media] El presupuesto acota la decodificación de una copia, no lo que pide enseñar la bóveda después

**Cómo lo he atacado.** Una copia v4 hecha a mano: entradas de acceso con 5 000 etiquetas cada una, todas distintas y de 5 letras. Ninguna lista llega a `COPY_LIST_MAX` (10 000), y el árbol estimado queda bajo `COPY_TREE_MAX_BYTES`. La importo con «Importar copia» y abro la bóveda.

**Lo que pasa:**
- `checkCopyShape` (`vault/VaultLimits.kt:308-311`) la acepta, la importación decodifica y comprueba dentro del bloque protegido (`vault/VaultStore.kt:1537-1541`), y el desbloqueo también (`:602-605`, `:487-495`, `:733-736`): la bóveda «se abre»;
- en cuanto la lista se pinta, `EntryListViewModel` construye en `Dispatchers.Default` el índice de búsqueda (`SearchIndex.build`, `vault/EntryQuery.kt:106-140`; llamado en `ui/screens/VaultList.kt:290`) y las fichas de etiquetas (`flatMapTo(HashSet()).sorted()`, `VaultList.kt:302`), en un `viewModelScope.launch` sin ningún `catch` (`:263-283`);
- medido en la JVM con 60 entradas de 5 000 etiquetas (2,4 MB de JSON):
  - estimación: 31,4 MB;
  - árbol: 15,9 MB;
  - **índice: 72,8 MB**;
  - fichas: 14,8 MB.

  Enseñarla pide **3,3 veces la estimación**. El índice guarda, por cada texto distinto, la cadena normalizada y un `IntArray` por palabra (`EntryQuery.kt:112-114`), y con etiquetas distintas nada se comparte;
- escalado al presupuesto, una copia que lo llena con esta forma (unos 262 MiB de estimación) necesita más de 800 MiB para la lista. Basta una copia de un tercio del presupuesto para pasar de los 256 a 512 MiB de *heap* de un móvil.

**Impacto:** el `OutOfMemoryError` sale fuera de los bloques protegidos y la app se cierra **cada vez que se abre esa bóveda**, después de que la importación dijera «Copia importada». La bóveda sigue guardada, pero no se puede usar en el móvil. Saldría con «He olvidado», que la vacía, o importando otra copia desde Ajustes. Es lo que RT-21 quería impedir, por otro camino: el arreglo protege la lectura, no lo que viene después.

Hacen falta las mismas condiciones que en RT-21 (importar una copia de quien conoce su maestra), así que AC:H y UI:R. Una bóveda legítima nunca tiene miles de etiquetas distintas: las pantallas guardan 10 como mucho (`MAX_TAGS`), y la forma (c) de la calibración usa etiquetas de un punto de código, que el índice comparte (`memo.texts`, `EntryQuery.kt:112`).

**Arreglo propuesto** (para el Ruling; cualquiera, o más de uno):
1. un tope de **etiquetas distintas en toda la copia** (por ejemplo, unas pocas miles: la app no escribe más de 10 por entrada, y la lista de etiquetas de la pantalla es una sola), comprobado en el mismo análisis antes de decodificar o justo después, junto a `checkCopyEntries`;
2. o que el modelo de `checkCopyShape` cuente lo que cuesta cada cadena distinta en el índice y en las fichas, no solo en el árbol;
3. y, en cualquier caso, un `catch (e: OutOfMemoryError)` alrededor de `compute` en `EntryListViewModel` (`VaultList.kt:272`) que enseñe un texto fijo en lugar de cerrar la app (como `ImportScreen.kt:308`).

La 3 sola deja la bóveda sin lista, pero ya no cierra la app.

**Test:** `RedTeam20RecheckTest.rt25_aCopyThatPassesTheBudgetCanAlsoBeListed`. Pide que una copia que `checkCopyShape` acepta no necesite para enseñarse más de lo que dice su estimación, o que se rechace. Sin el `@Ignore`, hoy falla con:

```
RT-25: 2404623 bytes of JSON, estimate 31395252, tree 15944488, index 72784264, chips 14809920 (x3.30 the estimate)
java.lang.AssertionError: Accepted, but showing it needs 3.3 times its estimate (tree 15944488, index 72784264, chips 14809920; estimate 31395252)
```

### RT-26 [Baja] Un nombre de miembro escapado saca el mapa de colores del cálculo

**Cómo lo he atacado.** Un blob v4 cuyo miembro raíz se escribe `"tagColors"`, con 2 000 claves de 1 KB.

**Lo que pasa:**
- `scanCopy` reconoce los miembros `entries` y `tagColors` de la raíz comparando sus bytes tal cual (`bytesEqual`, `vault/VaultLimits.kt:321-325`, usado en `:424-428`);
- el decodificador lee los nombres ya sin escapes, así que para él es `tagColors`, un `LinkedHashMap` con 2 000 cadenas de clave;
- para el análisis es un objeto de una clase, y los nombres de sus miembros no cuestan (`bytes -= lastStringCost`, `:422`). El resultado: estimación de 108 320 bytes y árbol de 2 189 520 bytes, 20 veces más;
- después, `checkStructure` rechaza más de 200 colores, pero ya decodificado.

**Impacto:** bajo. Lo que se escapa del cálculo son las claves del mapa, como mucho el tamaño de la propia copia (hasta 33 MiB en bytes, o el doble en UTF-16). Se suma a lo que ya entra bajo el presupuesto, y lo frena el último `catch` de la lectura. Pero el modelo sobre el que descansa el presupuesto dice que nunca queda por debajo de lo medido (`VaultLimits.kt:251-266`, «never below it»), y aquí sí queda.

La variante al revés, `"entries"`, es más estricta: la lista queda sujeta a `COPY_LIST_MAX`.

**Arreglo propuesto:** comparar el nombre del miembro ya sin escapes (basta con reconocer `\uXXXX` y `\x` en los nombres de la raíz), o rechazar como `COPY_DAMAGED` una copia con un escape en un nombre de miembro de la raíz, que Clavero nunca escribe.

**Test:** `RedTeam20RecheckTest.rt26_anEscapedMemberNameIsEstimatedAsTheDecoderReadsIt`. Sin el `@Ignore`, hoy falla con:

```
java.lang.AssertionError: Estimate 108320, tree kept 2189520
```

## Re-comprobación de cada arreglo

### RT-21

- **Su test:** `rt21_aCopyWhoseTreeOutgrowsThePhoneIsRefusedBeforeItIsDecoded` pasa sin `@Ignore`. Lo rechaza ya el tope de lista: 2,6 millones de etiquetas en una entrada. El presupuesto solo lo pasa por un 1 % (264,7 frente a 261,75 MiB; `redteam-fixes-report.md`), pero no es lo que lo rechaza.
- **Mapas en lugar de listas:** el único mapa del formato es `tagColors`, y el análisis lo cuenta con su tabla y sus claves (`VaultLimits.kt:417-420`), salvo con un nombre escapado (RT-26). Un objeto donde se espera una lista hace fallar al decodificador en su primer *token*, sin haber decodificado nada antes.
- **Conjuntos en vistas:** los `tags` y `types` de una vista se cuentan como `LinkedHashSet`, el elemento más caro. Lo cubren el `theTagsOfTwoThousandViewsAreRefused` y el `theModelIsNeverBelowWhatIsMeasured` del implementador (margen ×1,08, el más justo).
- **v3 sin versión:** `read` pasa al análisis la versión que de verdad abrió el blob (3 o 2), y su raíz es la lista de entradas (`VaultLimits.kt:381`). Lo cubre `theSameInAVersion3WithoutItsVersion`.
- **Un miembro `entries` repetido** (`resistsEscapesMultiByteAndRepeatedMembers`): el decodificador se queda con el último y el análisis cuenta los dos, así que la estimación queda por encima.
- **Escapes y UTF-8 de varios bytes** (el mismo test): `é`, `ñ`, `€`, `😀` y `\"`. Cada byte entre comillas cuesta 2 (`COPY_TREE_CHAR_BYTES`), y eso cubre de sobra lo que guarda la cadena (Latin-1 o UTF-16). La estimación queda por encima.
- **Formas repartidas justo bajo el presupuesto:** con los márgenes medidos (lista ×1,97, conjunto ×1,08, mapa ×1,57, objeto ×2,11), ninguna forma del árbol pasa de su estimación. Lo que sí pasa de ella es lo que la app construye después, que es RT-25.
  - La calibración se midió con el recolector por defecto (G1), no con SerialGC. Que el montón vivo después de GC dependa del recolector solo cambia el redondeo de cabeceras y alineación, que el modelo ya cubre con márgenes de ×1,08 o más. No he encontrado ninguna forma que caiga por debajo por eso.
  - El presupuesto (262 MiB) es mayor que el *heap* de un móvil de 192 o 256 MiB. Eso lo aceptó el Ruling («protege igual que la peor bóveda legítima»); con él, la última defensa es `COPY_NO_MEMORY`/`VAULT_NO_MEMORY`, y su prueba va a la fila 51.
- **Un `OutOfMemoryError` fuera de los bloques protegidos:**
  - **la lista** es RT-25;
  - **la lectura del ZIP y `decodeFile`**: en «Importar copia» van antes de `replace` (`VaultStore.kt:1482-1485`) y en «Comprobar una copia», antes del bloque protegido (`BackupCheck.kt:84-88`). Leen hasta 33 MiB de `vault.json` como texto y lo vuelven a copiar en `VaultFile.data`, sin `catch`. Si falta memoria ahí, la app se cierra antes de cambiar nada, sin perder datos. Informativo, sin número: lo tapa el mismo arreglo 3 de RT-25 si se pone en las pantallas, o ampliando el bloque protegido a esas dos líneas;
  - **los guardados** (`modifyData`, que codifica y sella la bóveda entera en `compute`), la exportación y la copia automática después de importar tampoco tienen `catch`. Una bóveda que cabía al abrirse cabe al guardarse (mismo tamaño), así que no lo cuento como hallazgo.

### RT-22

- (i) **resiste** (`RedTeam20VaultsTest.rt22_…`, ya sin `@Ignore`, y `RedTeam20RecheckTest.resistsTheEmptySlotAndTheSettingsAfterForgettingAnotherVault`): con A con archivo y bloqueada y B vaciada, `settingsEditableNow()` da falso.
  - Cada escritura de `SettingsScreen` lo vuelve a comprobar: el bloqueo, el portapapeles y el coste con `change(guarded = true)` (`ui/screens/SettingsScreen.kt:643`, `:651`, `:660`), y las carpetas en la vuelta del selector y en `save` (`:343`, `:386`).
  - **El buscador de Ajustes** (`settings/SettingsSearch.kt`) solo lleva a un ajuste de la misma pantalla, no tiene otra forma de escribir.
  - Fuera de Ajustes, nadie escribe el bloqueo, el portapapeles, el coste ni las carpetas (`grep` de `copy(lockAfter`, `copy(clipboard`, `copy(kdf`, `withTargetAdded` en `ui/` y `AppContainer.kt`). La única escritura de toda la app fuera de Ajustes es `clipboardHistoryNoticeAccepted` (`ui/ClipboardHistoryNotice.kt:68`), que exige copiar algo y, por tanto, una bóveda abierta.
- (ii) **resiste:** con A con archivo, `mayFillEmptySlot()` y `canAddVault()` dan falso. «Crea tu bóveda» (`ui/screens/VaultScreen.kt:406-413`) e «Importar copia» a un hueco (`ui/screens/VaultBackup.kt:188`) lo comprueban justo antes de llamar al almacén. «Importar de otra app» y «Contactos» necesitan una bóveda abierta, y «Importar como bóveda nueva» pasa por `newVault`, que ya tenía `canAddVault`. **Informativo:** el guardián vive en las pantallas, no en `VaultStore.create` ni en `importReplace`; una pantalla nueva que los llame sin él lo saltaría. Recomiendo un test de fuente que fije que solo esas dos pantallas llaman a `store.create(` y a `store.importReplace(` con `NoVault`.
- **Vaciar todas las bóvedas** con «He olvidado» deja la app como recién instalada. Es el caso aceptado en el Ruling T2, duda 1: borra justo lo que se quería proteger.

### RT-23

- `resistsAVaultImportedAsNewSharesTheCopysKeyOnlyUntilAMasterChanges` pasa. Las dos notas de corrección están en el diseño (`docs/superpowers/specs/2026-11-12-clavero-2.0-design.md`, bajo la tabla del modelo de amenaza y bajo la línea del formato v4). Ningún camino nuevo: cerrado.

### RT-24

- `rt24_theQrScreenNeverShowsAnotherNetworkThanTheCodeJoins` pasa. `WifiQrScreen.kt:209` enseña el nombre con `visibleText`, y la segunda línea (`wifiQrNameHidden`) aparece con las cinco gemelas (`WifiQrTest.aNameWithHiddenCharactersSaysSo`).
- **Otras pantallas que enseñan el nombre de la red a otra persona:**
  - solo la del QR une a una red con lo que enseña;
  - el Inspector ya marcaba los caracteres invisibles, con aviso;
  - «Ver en grande» solo es para secretos (la contraseña del wifi), y pinta cada carácter que no se ve como «�» (`ui/screens/BigPasswordScreen.kt:65-90`).
- **Informativo, sin número:** la ficha enseña el nombre de la red con `plainReportText` (`ui/screens/VaultEntryScreen.kt:161`, `plain = true`, desde `EntryFields.kt:160`), sin las marcas. Quien lea la ficha por encima del hombro y elija a mano la red de la lista de su móvil ve dos «Casa» iguales. No hay ningún código de por medio, y «Copiar» copia el nombre tal cual. Si se quiere coherencia con la pantalla del QR, `visibleText` en los campos de nombre de red; es texto, va al Abogado.

## Necesita prueba en el móvil

- **RT-25:** importar una copia de prueba con miles de etiquetas distintas que pase el análisis, en un móvil de 256 MiB y en otro de 512 MiB, y ver si la lista cierra la app. Va con la fila 51.

## Revisión experto-seguridad-cripto

- **RT-25:** CWE-400 y CWE-770. El control acota una fase (la decodificación) y deja sin acotar la siguiente (el índice). La decisión (a) pide topes «antes de leer o reservar» en toda entrada de datos, y la memoria del índice también sale de la copia.
- **RT-26:** CWE-436 (*Interpretation Conflict*). El análisis y el decodificador leen distinto el mismo nombre; es la misma clase que RT-1 de la 1.4.0 y que la fila 24.
- **Lo que está bien:**
  - el análisis tiene en cuenta las cadenas y sus escapes;
  - ningún `String` se crea antes de decidir;
  - el tope de lista no rechaza ninguna copia propia (10 000 frente a 500);
  - el `OutOfMemoryError` de la lectura nunca dice «Contraseña incorrecta» (`masterUnlockErrorText`, `vault/Vault.kt:305`);
  - el guardado sigue a «lo comprobado es lo que se escribe» (decisión (b)).

## Revisión Ingeniero Móvil Senior

- **RT-25:** un `viewModelScope.launch` sin `CoroutineExceptionHandler` propaga el `OutOfMemoryError` al hilo y cierra el proceso. El `catch` en `compute` es barato y no cambia nada en el caso normal. El índice se rehace en cada cambio de datos (con `previous` solo se ahorra lo que no cambió), así que el pico también llega al guardar.
- **RT-22:** `produceState(false, …)` empieza cerrado; cada escritura vuelve a mirar el disco en IO. Correcto en el ciclo de vida.
- **RT-24:** `visibleText` en un `Text` de la pantalla del QR, en el color de los avisos. Sin estado guardado nuevo.

## Textos para el Abogado Digital (revisión final)

- RT-25, si se arregla con un `catch` en la lista: un texto fijo nuevo («Este móvil no tiene memoria suficiente para enseñar esta bóveda…», regla M-3).
- RT-26: ninguno (usaría `COPY_DAMAGED`, que ya existe).
- RT-24, el informativo de la ficha, si se cambia.

## Puerta

| Ejecución | Tests | Ignorados | Fallos | Resultado |
|---|---|---|---|---|
| `jvm-check`, `--tests 'app.seguridad.redteam.*'` | 102 | 2 (RT-25, RT-26) | 0 | verde |
| `:app:testDebugUnitTest --tests 'app.seguridad.redteam.*' --tests 'app.seguridad.ui.redteam.*'`, después `--stop` | 105 | 2 (RT-25, RT-26) | 0 | verde |

- Sin los `@Ignore`, fallan exactamente `rt25_` y `rt26_`, con las salidas copiadas arriba.
- RT-21 a RT-24 ya no tienen ningún `@Ignore`.
- El archivo nuevo es `app/src/test/java/app/seguridad/redteam/RedTeam20RecheckTest.kt`.
- No se tocó `app/src/main`.
