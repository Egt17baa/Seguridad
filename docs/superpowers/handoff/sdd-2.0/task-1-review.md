# Revisión de la tarea 1 de Clavero 2.0 (formato v4, `VaultData`, paso de v3 a v4, copias con marca 3)

Rango: `7744994..9804b28` (sin contar `d61007e` ni `553692a`, que son documentos del controlador). Revisado en dos pasadas sobre el paquete `review-7744994..9804b28.diff`: primero el código de producción, después los tests. Comprobaciones fuera del diff, una por riesgo con nombre:
- todos los caminos de escritura de `VaultStore.kt` en `9804b28`: `save`, `commit`, `saveBookkeeping`, `modify` y `modifyData`, `changeMaster`, `replace`, `destroy`;
- `AutoBackup.copyTo` y `fill`: de dónde sale el archivo de la copia;
- quién llama a `store.unlock(chars)` (`VaultScreen.kt:374-382`): cuándo se borra la maestra;
- quién usa `VaultGate` y qué hacen con las excepciones los que llaman a `upsert` y `changeMaster`;
- la exportación de `VaultBackup.kt:77-91`, para comparar.

Leídos antes de revisar: `CLAUDE.md`, `experto-seguridad-cripto/SKILL.md` y `references/cripto-moderna.md` (AEAD, AAD, aleatoriedad). Skills cargadas: `ingeniero-movil-senior:auditoria-seguridad-movil` y `:seguridad-criptografia-movil`. No he vuelto a ejecutar la suite. No he ejecutado ningún test aislado: ninguna duda lo pedía, porque el hallazgo importante sale de leer el orden del código, no de un resultado.

### Spec Compliance

- ❌ **Un desvío:** con carpeta de copia automática, `pendingUpgrade` vale `true` mientras corre `beforeUpgrade()`.
  - El brief dice que `pendingUpgrade` es `true` solo si no hay carpeta o si `beforeUpgrade` da `false`.
  - Aquí se pone a `true` en `opened()` (`VaultStore.kt` del diff, línea 2466: `_pendingUpgrade.value = upgraded != null`) antes de llamar a `beforeUpgrade()` (líneas 2493-2501).
  - Así, el diálogo del paso sale también con carpeta durante toda la copia. Detalle en I-1.
- ✅ Todo lo demás de las «Interfaces» del brief está, con los nombres pedidos:
  - `VERSION` = 4, 3, 2 y 0, y sin versión se prueba como 3 y 2, nunca como 4 (diff 1561-1567);
  - `VaultData` y sus tipos (1897-1985), los campos nuevos de `VaultEntry` (1458-1471) y `EntryType` con `v4Only` (1403-1436);
  - `VaultLimits`, `fitted()`, `checkLimits()` y `VaultLimitExceeded` (1991-2232);
  - `read`, que da `VaultData` y lee la v2 o v3 como `VaultData(entries)` (1703-1722);
  - `write`, que solo escribe la v4 (1758-1768);
  - `afterMasterUnlock`, que siempre da una v4, con `history` y `vaultTag` (1798-1819);
  - `BiometricRefusal.UPGRADE` antes del reloj y de los 7 días (1869-1875);
  - `VaultState.Unlocked(data)` con `entries` y `trash` (2253-2256);
  - `pendingUpgrade` y `completeUpgrade` (2344-2359, 2539-2559);
  - el bloqueo de escrituras (2733, 2879, 2926) y el descarte al bloquear (2684-2686);
  - en las copias: la marca 3, `BACKUP_NEWEST = 3`, 33 MiB (decisión T1-1) y 40 MiB (964-1007);
  - `copyBeforeUpgrade` con `seguridad-antes-de-2-0-` (812-857), sobre la única instancia de hoy, como dijo el controlador;
  - «Comprobar una copia» lee la marca 3 y mira la estructura (1243-1279).
- ✅ Las decisiones T1-1 a T1-3 están aplicadas:
  - 33 MiB, con `aVaultAtTheDataLimitFitsInACopy` (3806-3832);
  - `backupMaxEntryBytes` en `applyImport` y en la estimación (982, 2784-2828, 468);
  - `ENTRY_TOO_BIG` en el plan (384-386) y `ApplyResult.EntryTooBig` (338, 2811-2814, 225-229).
- ✅ Lo que pidió el controlador en el encargo: el diálogo mínimo, con O-3 («Exportar una copia antes» como botón principal y «Seguir sin copia» como secundario), en `VaultGate` (`VaultUpgrade.kt`, diff 708-795; 569-573).
- ✅ Los tests del brief están todos, con su nombre (`marker3EntryLimitIs32MiB` pasa a `…33MiB` por T1-1). El archivo real de la 1.4.3 está hecho como dice la decisión de la revisión previa (`vault-1.4.3.json` y su `README.txt`).

⚠️ No se puede comprobar con el diff:
- **Lo que hace la 1.4.3 con una copia de marca 3.** Lo deduzco del código que el diff quita (líneas 1178-1180: `number > BACKUP_NEWEST(2)` da `BACKUP_NEWER`), pero no hay ninguna ejecución con el lector de la 1.4.3. Solo lo confirma una prueba con el APK de la 1.4.3 o un test que compile su lector.
- **O-3 con carpeta.** Con la copia automática hecha, el paso es callado: el usuario nunca ve que las versiones anteriores no abrirán la bóveda. El diseño (línea 66) solo pone el aviso cuando no hay carpeta, y O-3 (línea 152) dice «aviso a la vista». Es una cuestión de diseño para el controlador o el Abogado, no un defecto del código.
- **Cómo se ve el diálogo en el móvil** (TalkBack, el giro con `configChanges`, el selector), y el rendimiento de guardar con 24 MiB en un móvil real.

### Strengths

- **La versión ligada a la AAD, sin tocar.** `fieldsAad`, `keyAad` y `dataAad` son los de antes (diff 1551-1599). `read` elige el decodificador por la versión que la AAD autenticó (`openAny` → `version`, 1704-1713), no por el campo en claro, así que un blob v3 nunca se lee como `VaultData` ni al revés. Un archivo sin versión nunca se prueba como 4 (1565-1567).
  - Los tests lo cubren en los dos sentidos: `v4BindsVersionInAad` cambia 4 por 3 y 3 por 4 (5618-5627), y `v4NeverTriedForUnknownVersion` quita la versión (5610-5616).
- **Nada se escribe antes de elegir.** El camino del paso abre con `opened(v, key, since, upgraded = checked)` sin guardar (2525-2529). Todas las escrituras están cerradas:
  - `modifyData`, `changeMaster` y `checkCanReplace` lanzan `UPGRADE_PENDING`;
  - las demás (`upsert`, `addAll`, `delete`, `addAttachment`, `removeAttachment` y `applyImport`) pasan por `modify` y `modifyData`, que es lo que comprobé en `VaultStore.kt` de `9804b28`, líneas 679-912;
  - `commit` solo se llama desde caminos de la v4.
  - `lock()` descarta la v4 calculada (2684-2686), y `completeUpgradeLocked(since)` no guarda si hubo un bloqueo (2545). `destroy()` pasa por `lock()`, así que también la descarta.
- **La muerte del proceso** se prueba con un escritor que deja el `.tmp` a medias (`upgradeInterruptedKeepsV3`, 5669-5694). El archivo es el de la 1.4.3 escrito por su propio código: es un test con datos reales anteriores, como pide el `CLAUDE.md`.
- **La clave de la huella sigue valiendo** después del paso: la DEK es la misma (`masterUnlockUpgradesToV4`, 5572; `aV4VaultOpensWithTheFingerprint`). La huella rechaza antes de usar la clave, y la clave se borra (5658-5666).
- **La profundidad del JSON se limita antes de decodificar** (`jsonNestingWithin`, 2200-2221, con las comillas y los escapes bien), y las versiones del historial vuelven planas (`withPlainVersions`). Así se corta la recursión `EntryVersion → VaultEntry → history` de una copia hecha a mano.
- **`fittedForSave` compara por identidad** (2147-2157): una bóveda antigua con notas de más de 64 KiB nunca deja de abrir ni de guardar, pero todo lo que cambia cumple los topes. Es una buena solución a un riesgo real de compatibilidad.
- **Los fallos de tamaño de la importación tienen ya su propio resultado** (`TooBig` y `EntryTooBig`), dentro del mutex y antes de escribir, con tests que comparan el archivo byte a byte. Las copias en ciclo o por encima de los topes de estructura se rechazan como dañadas antes de cambiar nada.

### Issues

#### Critical (Must Fix)

Ninguno.

#### Important (Should Fix)

**I-1. Con carpeta de copia automática, el diálogo del paso sale mientras se hace la copia, y lo que se elige en ese rato puede dejar una copia «de antes» que la 1.4.x no abre, o un documento vacío.**
- **Dónde:** `VaultStore.kt` del diff, líneas 2465-2466 (en `opened`, `_pendingUpgrade.value = upgraded != null`), 2490-2501 (`unlock`: `beforeUpgrade()` corre después, con el mutex libre) y 2896-2906 (`exportBackup` exporta `vault`, sea cual sea en ese momento). `VaultScreen.kt:569-573` enseña `VaultUpgradeDialog` en cuanto `pendingUpgrade` es `true`. `AutoBackup.copyTo` y `fill` exportan con `store.exportBackup(out)` (comprobado en `9804b28`, `AutoBackup.kt:588-616`).
- **Qué pasa:** `pendingUpgrade` vale `true` desde que abre la bóveda hasta que termina `beforeUpgrade()`, que escribe en el SAF la bóveda y todos sus adjuntos (hasta 100 MB, varios segundos). Durante ese rato el diálogo está en pantalla, con sus dos botones:
  - **«Seguir sin copia»:** `completeUpgrade()` puede tomar el mutex antes que el `exportBackup` de la copia automática. Entonces guarda la v4 y pone `vault = v4`, y la copia `seguridad-antes-de-2-0-…` sale de la v4 con la marca 3. Es justo la copia que, por su nombre, debería abrir la 1.4.x.
  - **«Exportar una copia antes»:**
    - Se abre el selector. Mientras tanto la copia automática termina, `completeUpgradeLocked(since)` guarda la v4 y `pendingUpgrade` pasa a `false`.
    - El diálogo sale de la composición y su `rememberDocumentPicker` se desregistra. El resultado del selector se pierde y `CreateDocument` deja un `.sgbak` vacío en el sitio elegido. Es justo lo que el propio código evita en otro sitio («Never leave an empty or cut copy», `VaultUpgrade.kt`, línea 749 del diff).
    - Si el selector vuelve antes, la exportación escribe la v4 con la marca 3, aunque el diálogo acaba de decir «esa sí la abren las versiones anteriores» (`UPGRADE_COPY_FIRST`). Eso es un texto que afirma algo falso sobre el código, que la regla de veracidad del `CLAUDE.md` no admite.
  - Además, es el desvío del brief que se describe en «Spec Compliance».
- **Por qué importa:** la copia de antes del paso es la red de seguridad de la migración de formato (O-3 y foco 1). Una copia con ese nombre que la versión anterior no abre, o un aviso que lo promete en falso, quita esa red justo a quien vuelve atrás. No es explotable por un atacante. Es una pérdida de la garantía de recuperación y un texto falso, y por eso la tarea no se puede dar por buena tal como está. Ningún test lo cubre: `upgradeCopiesFirstWithBackupFolder` (5742-5772) no mira `pendingUpgrade` dentro de `beforeUpgrade`.
- **Cómo arreglarlo:**
  1. Separar el estado interno del que se enseña. `upgrade` (que bloquea las escrituras) se pone en `opened`, pero `_pendingUpgrade.value = true` solo cuando hay que preguntar al usuario: en `unlock()`, después de que `beforeUpgrade()` dé `false` o lance, y si `completeUpgradeLocked` falla.
  2. Que la exportación «de antes del paso» no pueda exportar la v4. Por ejemplo, `exportBackup(out, beforeUpgradeOnly = true)` (o un `exportBeforeUpgrade(out)`) que, con el mutex tomado, devuelve `false` si `upgrade == null`. La usarían `AutoBackup.copyBeforeUpgrade` y el botón del diálogo.
  3. Tests: dentro de `beforeUpgrade`, `assertFalse(s.pendingUpgrade.value)` y que un `upsert` siga lanzando `UPGRADE_PENDING`. Y que la exportación «de antes del paso» después de `completeUpgrade()` dé `false` sin escribir nada.

#### Minor (Nice to Have)

**M-1. La maestra vive en memoria mientras dura la copia automática.**
- **Dónde:** `VaultScreen.kt:374-382` (comprobado en `9804b28`). `chars.fill('\u0000')` va en el `finally`, después de `store.unlock(chars)`. Ahora `unlock` no vuelve hasta que terminan `beforeUpgrade()` y el guardado (diff 2493-2501).
- **Qué pasa:** el `CharArray` de la maestra sigue en memoria todos los segundos que tarda la copia en el SAF, aunque `unlock` ya no lo necesita después de `afterMasterUnlock`.
- **Cómo arreglarlo:** lanzar la copia y el guardado en un ámbito de la app (el de `AutoBackup`, o un `appScope`), y devolver de `unlock` al abrir. Encaja con el arreglo de I-1.

**M-2. Cada copia vuelve a decodificar el archivo de la bóveda entero solo para saber su versión.**
- **Dónde:** `Backup.kt`, diff 1010-1014 (`isV4` con `VaultCrypto.decodeFile(vaultJson)`) y 1048.
- **Qué pasa:** en el tope de 33 MiB es otro análisis completo y otra cadena de unos 33 MB (unos 66 MB en UTF-16) en el heap, encima de `json` y de los bytes que ya tiene `exportBackup`.
- **Cómo arreglarlo:** pasar la versión. `exportBackup` ya tiene `v`: `writeBackup(out, json, files, expected, vaultVersion = v.version)`, y dejar `isV4` solo para los tests.

**M-3. El diálogo del paso tapa el motivo de un fallo de la copia.**
- **Dónde:** `VaultUpgrade.kt`, diff 743-751, frente a `VaultBackup.kt:88` en `9804b28`.
- **Qué pasa:** la exportación normal enseña el `CryptoFailure` («falta o está dañado el adjunto X»). El diálogo dice siempre «Vuelve a intentarlo», y con un adjunto dañado volver a intentarlo falla siempre igual.
- **Cómo arreglarlo:** el mismo `failure ?: UPGRADE_EXPORT_FAILED` que en `VaultBackup`.

**M-4. Pasar `passwordHistory` a `history` no siempre deja todas las contraseñas antiguas, y las dos listas se separan.** Es un aviso para la tarea 3.
- **Dónde:** `Vault.kt`, diff 1622-1632 y 1803; `VaultLimits.kt`, 2083-2095.
- **Qué pasa:**
  - Cada `EntryVersion` es la entrada entera, así que una entrada con notas largas (por ejemplo, 40 KiB) y tres contraseñas antiguas pasa de 64 KiB de historial. `fitted()` quita entonces versiones, y no todas las contraseñas de `passwordHistory` quedan en `history`.
  - Además, los cambios de contraseña después del paso (en el editor o con `withNewPassword` al importar) solo van a `passwordHistory`.
  - Hoy no se pierde nada, porque `passwordHistory` se conserva (resolución T1, dudas 4 a 6).
- **Cómo arreglarlo:** apuntar en el traspaso de la tarea 3 que no se quite `passwordHistory` sin conciliar las dos listas. Y probar el paso con una entrada grande, no solo con el archivo pequeño.

**M-5. Si la copia automática falla, el usuario no lo sabe.**
- **Dónde:** `AppContainer.kt`, diff 121 (`… is CopyResult.Saved`), y `VaultStore.kt` 2495-2499.
- **Qué pasa:** el diálogo sale con «Antes, exporta una copia» y no dice que la carpeta falló, así que el usuario no sabe que su copia automática no funciona.
- **Cómo arreglarlo:** una línea fija, a la vista, cuando había carpeta y la copia no se hizo.

**M-6. Guardar codifica la bóveda entera varias veces.**
- **Dónde:** `VaultLimits.kt`, diff 2098-2122 y 2147-2157; `VaultStore.kt`, 2741 y 2816.
- **Qué pasa:** cada guardado codifica el tamaño de cada historial, después el total y después el JSON que se cifra (al importar, también `fitted` y `jsonBytes`). Con 24 MiB son tres o cuatro codificaciones completas bajo el mutex.
- **Cómo arreglarlo:** en las pruebas de rendimiento del diseño (2 000 entradas), medir el tiempo de un `upsert` en el tope. Si pasa de lo aceptable, guardar el tamaño de cada entrada por identidad.

**M-7. Con la marca 3, el tope de 33 MiB por archivo también vale para los adjuntos.**
- **Dónde:** `Backup.kt`, diff 1080 y 1190-1192.
- **Qué pasa:** un adjunto tiene un tope propio (`MAX_ATTACHMENT_BYTES`). Una copia hecha a mano puede hacer que el lector escriba hasta 33 MiB por adjunto en la caché antes de que `checkCopyEntries` la rechace. El total sigue acotado a 100 MB, así que la exposición total no cambia.
- **Cómo arreglarlo:** usar `BACKUP_MAX_ENTRY_BYTES` (o el tope de un adjunto) para `att/*` también con la marca 3.

**M-8. `VaultStore.kt` crece hasta unas 1 190 líneas.**
- **Dónde:** `+282/−83` en esta tarea.
- **Qué pasa:** es informativo. La tarea 2 la parte por bóveda, y entonces conviene sacar el paso pendiente (`upgrade`, `completeUpgrade` y la copia de antes) a una pieza propia.
- **Además:** `aVaultAtTheDataLimitFitsInACopy` tarda 7,1 s según el informe. Es aceptable, pero es el test más lento añadido.

### Assessment

**Task quality:** Needs fixes

**Reasoning:** El formato, la versión ligada a la AAD, el bloqueo de escrituras, el descarte al bloquear y la compatibilidad con datos reales de la 1.1 y la 1.4.3 están bien hechos y probados. Falla I-1: con carpeta de copia automática, el estado pendiente se enseña mientras se hace la copia. En ese rato, la copia «de antes de la 2.0», o la que el usuario exporta desde el aviso, puede salir con la marca 3, que la 1.4.x no abre, o como un documento vacío. Eso deja en falso el texto del aviso y quita la red de recuperación de la migración.

### Revisión experto-seguridad-cripto

Usé `references/cripto-moderna.md` §2 (AEAD, AAD) y §6 (aleatoriedad) como lista de comprobación del diff.

- **AEAD y nonces:** sin cambios. `seal` y `open` usan AES-256-GCM con un nonce aleatorio de 96 bits por sellado y la etiqueta completa (diff 1605). `write` vuelve a sellar con un nonce nuevo y `revision + 1` (1765-1767). ✔
- **AAD y versión:**
  - La versión es uno de los campos ligados de `fieldsAad` (1551-1553). Una v4 marcada como 3, una v3 marcada como 4 o una v4 sin versión no abren: `v4BindsVersionInAad` (5618-5627) y `v4NeverTriedForUnknownVersion` (5610-5616).
  - El decodificador sale de la versión autenticada (1704-1713), no del campo en claro.
  - Una clave envuelta de la v2 junto a datos de la v4 se rechaza (`VaultV3Test`, renombrado).
  - Sin hallazgos.
- **Gestión de claves:**
  - La DEK es la misma al pasar a v4, y la KEK se borra en `finally` (1675-1677).
  - El texto en claro de los datos se borra en `read` (1720), y la clave se borra en todos los errores de `unlockLocked` y `opened` (2513-2515, 2445-2447).
  - `upgrade` solo guarda un `VaultFile` cifrado (2359), y `lock()` lo descarta (2685).
  - Pendiente, M-1: la maestra en `CharArray` vive en memoria durante la copia automática (`VaultScreen.kt:374-382`). Severidad baja: está en memoria del propio proceso, con `FLAG_SECURE`, y sin escribirse en ningún sitio.
- **Aleatoriedad:** `vaultTag` sale de `randomBytes(16, SecureRandom)` (1634) y no es una clave. ✔
- **Coste de Argon2id:** el paso usa `PasswordUse.EXISTING` y el máximo eje por eje (1806-1814), así que A8 y CR-2 se mantienen. ✔
- **Huella:** `UPGRADE` va antes del reloj, de los 7 días y de usar la clave (1869-1875 y 2605-2610), así que la clave de la huella no descifra nada cuando rechaza, y se borra (5664). CRY-A02 cambia con conocimiento de causa en el caso sin versión (4442-4482): ahora no se prueba la clave y la huella se conserva. Es coherente con el brief.
- **Entradas hostiles (decisión (a)):**
  - Se acota la profundidad antes de decodificar (2200-2221) y se aplanan las versiones (2224-2232).
  - Las copias en ciclo o por encima de los topes de estructura se rechazan antes de cambiar nada (1273-1279 y 2951).
  - Los topes de lectura: 33 MiB por archivo con la marca 3, 100 MB en total y 40 MiB de JSON. Una copia hecha a mano con historiales enormes queda acotada por esos 33 MiB y se recorta al guardar.
  - Pendiente, M-7: el tope por adjunto con la marca 3.
- **Integridad de la migración (foco 1):** la escritura atómica se mantiene (`writeFile` solo se cambia en los tests, 2309). El archivo v3 sigue en disco hasta la elección, y la muerte del proceso a mitad está probada (5669-5694).
  - **Hallazgo I-1 (Important):** la copia «de antes del paso» puede salir de la v4 con la marca 3, por la carrera entre `beforeUpgrade()` y `completeUpgrade()` desde el diálogo, que se enseña antes de tiempo. No es explotable por un tercero. Rompe la garantía de recuperación y la veracidad del texto `UPGRADE_COPY_FIRST`.
- **Registros y secretos:** no se añade ningún log. Los textos son fijos, sin rutas ni excepciones. El diálogo solo guarda `busy` y `error` en `remember` (731-732), sin secretos.
- **La 1.4.3 y la marca 3:** por el código que el diff quita (1178-1180), la 1.4.3 da `BACKUP_NEWER` sin tocar nada. Es ⚠️ hasta probarlo con su APK.

### Revisión Ingeniero Móvil Senior

Skills aplicadas: `seguridad-criptografia-movil` y `auditoria-seguridad-movil` (MASVS: STORAGE, CRYPTO, PLATFORM y CODE).

- **Concurrencia:**
  - El mutex es el único escritor. `upgrade` es `@Volatile`: se escribe bajo `opening` y el mutex, y `lock()` lo borra sin mutex. Los lectores (`modifyData` y `completeUpgradeLocked`) lo leen bajo el mutex (2359, 2733 y 2544).
  - La copia corre con el mutex libre para que `exportBackup` no se bloquee (2491-2501). Esa misma ventana es la que abre I-1.
  - Todo el camino es `NonCancellable`, como BOV-3.
- **Estado y ciclo de vida:**
  - `pendingUpgrade` vive en la tienda (un `StateFlow`), no en la pantalla, así que sobrevive a rehacer la actividad. Si Android cierra el proceso, se vuelve a calcular en el siguiente desbloqueo.
  - El diálogo vive en `VaultGate`, que cubre `VaultScreen`, `CodesScreen`, `ContactsScreen` y `QrInspectScreen` (comprobado en `9804b28`).
  - Las pantallas sin `VaultGate` que guardan capturan `Exception` (`ContactAddScreen.kt:232-238` con `attempt`, `VaultBackup.kt:128-134`), así que `UPGRADE_PENDING` no tumba la app.
  - El selector del diálogo se desregistra si el diálogo sale de la composición. Ese es el documento vacío de I-1.
- **Plataforma:** `FilterObscuredTouches` en el diálogo (762), `VaultLock.keepOpenOnce` alrededor del selector (775-778) y `discardDocumentNow` al fallar (750). No hay permisos, componentes exportados ni dependencias nuevas. Los recuentos de selectores pasan a 29, y `PlatformSourcesTest` y `DocumentUriTest` comprueban que van con su guarda.
- **Rendimiento y memoria:**
  - Los tamaños se cuentan sin guardar el JSON (`CountingStream`, 2053-2067).
  - Pendiente, M-2: se decodifica dos veces el archivo en cada copia.
  - Pendiente, M-6: varias codificaciones completas por guardado.
  - Hay que medir las dos en el móvil con el tope de 24 MiB.
- **Compatibilidad:**
  - Se leen la v2 y la v3 reales (`vault-1.1.json`, `vault-1.4.3.json` y `backup-1.1.sgbak`) y las marcas 1 y 2.
  - `AppNameTest` fija el prefijo nuevo.
  - `ListQuery` evita el choque de nombres con el `EntryFilter` del brief.
  - Los tipos `v4Only` no se ofrecen todavía (1436) y tienen ramas provisionales en los `when`.
- **Interfaz (M-3):** dos líneas a la vista (`UPGRADE_NOT_BACK` y `UPGRADE_COPY_FIRST`), el resto bajo `MoreInfo` (766) y el error con `liveRegion` (768). Por I-1, `UPGRADE_COPY_FIRST` tiene que seguir siendo cierto en cualquier orden de los hechos.

Ninguna sección de esta revisión añade textos para el Abogado Digital. Los de la tarea están en el informe del implementador. Si M-5 se arregla, su línea nueva va a «Textos para el Abogado Digital (revisión final)».
