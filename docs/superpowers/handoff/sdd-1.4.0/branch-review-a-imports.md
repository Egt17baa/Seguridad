# Revisión de la rama 1.4.0, área A: «Importar de otra app» de principio a fin

Rama `ccr-d1e75160-p5f4wc` en `b44aa31` (HEAD `7e13934`, que solo añade una línea al registro). Base `main` en `7c8dea7`.
Revisor de solo lectura: no se ha tocado código ni se ha ejecutado Gradle (otro revisor lo usa). Los tests que propongo
abajo hay que correrlos en la ronda de arreglos.

Alcance: `imports/` entero, `ui/screens/ImportScreen.kt` y lo que toca la importación en `vault/VaultStore.kt`
(`importSnapshot`, `applyImport`, `modify`), `vault/AutoBackup.kt` (`copyBeforeImport`, la rotación con las copias de
antes de importar), `vault/Backup.kt`, `crypto/OtpMigration.kt`, `crypto/Totp.kt`, `vault/TotpImport.kt`,
`AppContainer.kt` y `ui/AppNavHost.kt`. También sus tests, los archivos de prueba y el generador
(`app/src/test/resources/import/`, `scripts/import-fixtures/gen_encrypted.py`), los arreglos del ataque RT-1 (`urlHost`
con `parseLink`) y RT-2 (`compareKey`), y el cambio de `tools/qr/LinkSignals.kt`.

## Veredicto

**Hacen falta arreglos.** No hay nada crítico ni importante: el flujo es correcto y seguro de principio a fin. Hay **5
menores**. Por la regla del dueño se arreglan todos.

## Hallazgos

### M-1 (menor): la documentación dice que un bloqueo no corta una importación confirmada, y el código la cancela si llega antes del guardado

- **Dónde:**
  - `imports/ImportSession.kt:29`: «Not cut by a lock or by leaving the screen».
  - `ui/screens/ImportScreen.kt:165-166`: «is not cut by a lock or by leaving».
  - `CLAUDE.md:80`, la decisión R17: «confirmada la importación, la copia y el guardado terminan aunque se bloquee la bóveda».
  - `docs/superpowers/handoff/sdd-1.4.0/progress.md:154`, R17: «copy+save finish under NonCancellable even after lock/back».
- **Lo que hace el código.** `confirm()` va bajo `NonCancellable` (`ImportScreen.kt:321`), así que salir de la pantalla no lo corta. Un bloqueo sí cuenta:
  - durante la copia, `importCopyNow` la rechaza (`AutoBackup.kt:408` y `:412`, con `exportBackup` que da `false` si está bloqueada);
  - entre la copia y el guardado, `modify` ve `dek == null` y no guarda (`VaultStore.kt:605-606`), y `applyImport` devuelve `Stale` (`VaultStore.kt:684-718`).

  Solo un guardado que ya tiene el mutex y la copia de la clave termina después del bloqueo (`commit`, `NonCancellable`).
- **Caso.** El usuario confirma y la copia va a una carpeta en la nube que tarda. Mientras tanto la bóveda se bloquea por inactividad. No se importa nada, la copia de antes de importar se queda en la carpeta y en la lista, y la sesión se borra sin decir nada (la pantalla vuelve a la bóveda). Al desbloquear, el usuario cree, por lo que dicen los documentos, que la importación se hizo. El texto de la pantalla (`IMPORT_WIPED_WHILE_SAVING`, «puede haberse hecho») sí dice la verdad.
- **Arreglo.** El comportamiento es el más seguro y se queda como está: sin la bóveda abierta no se guarda nada. Hay que corregir el texto de los cuatro sitios: «Confirmada, salir de la pantalla no la corta; un bloqueo antes de empezar a guardar la cancela (no se importa nada; la copia, si se hizo, se queda); un guardado ya empezado termina», que es lo que dice el diseño en «Bloqueo». Y fijarlo con un test en `VaultStoreImportTest`: plan, `lock()` y `applyImport` deben dar `Stale` y la bóveda en disco sin cambios.

### M-2 (menor): «Reemplazar» repite las notas cuando solo cambian los espacios del principio o del final

- **Dónde:** `imports/MergePlan.kt:241`. La comparación de `replaceWith` es exacta (`incoming.notes == existing.notes`, `existing.notes.endsWith("\n" + incoming.notes)`), pero `Matcher` empareja las notas recortadas (`MergePlan.kt:407` y `:418`, `e.notes.trim()`).
- **Caso.** En la bóveda hay una NOTE «Wifi» con «clave 123». El archivo trae «Wifi» con «clave 123\n»: un CSV o un JSON con salto de línea al final, o un espacio de más. Es un duplicado. Si el usuario elige «Reemplazar», las notas pasan a ser «clave 123\n\nImportado el 8-11-2026:\nclave 123\n»: el mismo texto dos veces. Pasa lo mismo con un LOGIN cuyas notas solo cambian en los espacios de los extremos. En una NOTE, encima, «Reemplazar» no cambia nada más que la mayúscula del título y las etiquetas, así que todo lo que hace es estropearla.
- **Arreglo.** En `replaceWith`, tratar como iguales las notas con `trim()`:
  - `incoming.notes.isBlank() || incoming.notes.trim() == existing.notes.trim() || existing.notes.trimEnd().endsWith("\n" + incoming.notes.trim())` deja las de la bóveda;
  - al añadir, usar `incoming.notes.trim()`.

  Tests en `MergePlanTest`: una NOTE con «x» y «x\n» reemplazada no cambia (`replaceWith` devuelve la misma entrada), y un LOGIN con «a» y « a » tampoco.

### M-3 (menor): un archivo cifrado con una cabecera que nunca se podrá usar pide la contraseña antes de rechazarse

- **Dónde:**
  - `imports/BitwardenJson.kt:36`: `NeedsPassword` sin mirar `bitwardenProtected(root)`;
  - `imports/ImportJson.kt:212` (Aegis) y `:221` (2FAS): lo mismo sin `aegisEncrypted(root)` ni `twoFasEncrypted(root)`.
- **Caso.** Una exportación protegida de Bitwarden con `kdfIterations: 2000000000`, con Argon2id de 512 MiB o con `kdfParallelism` 8, que el propio Bitwarden deja poner. O un Aegis con `n` = 2²², o un archivo con la cabecera rota. Clavero enseña el paso de la contraseña. El usuario busca y escribe la contraseña de su otra app, y solo entonces lee «Este archivo pide más memoria o más cálculo del que Clavero puede usar en este móvil…» o «dañado». No se calcula nada de más, porque el tope va antes de la KDF (bien), pero se pide un secreto que la app sabe que no puede usar. Es justo lo que R13a llamó «honest UI» («without asking for a password the app cannot use»).
- **Arreglo.** Antes de devolver `NeedsPassword`, comprobar la cabecera sin derivar nada:
  - Bitwarden: `bitwardenProtected(root)`;
  - Aegis: la validación de `aegisEncrypted` sin decodificar `db`, o pasar el resultado ya leído;
  - 2FAS: `twoFasEncrypted(root)`.

  Todas lanzan ya `ImportFailure` con `IMPORT_KDF_TOO_BIG` o `IMPORT_BROKEN`. Tests: `readImport(bytes, null)` de cada formato con un parámetro fuera de rango debe dar `Failed(IMPORT_KDF_TOO_BIG)`, no `NeedsPassword`. Los tests del ataque (`RedTeamImportTest.kt:175-186`) pasan contraseña y siguen valiendo.

### M-4 (menor): la fila de un duplicado no enseña la web que traerá «Reemplazar»

- **Dónde:** `ui/screens/ImportScreen.kt:663-666`. Si el LOGIN tiene usuario, `sub` es `e.username.ifBlank { e.url }`, así que no sale ni la URL ni su host. Para un duplicado sin URL en un lado se aplica la regla del diseño (`MergePlan.kt:415`, «cuentan el título y el usuario»), y `replaceWith` copia la URL del archivo (`MergePlan.kt:251`).
- **Caso.** El modelo de amenaza dice: «colar entradas que suplanten otras o cambiar las que ya hay». En la bóveda está «Banco» / «ana» sin URL. Un archivo manipulado trae «Banco» / «ana» con `https://banco-acceso.example/` y otra contraseña. Es un duplicado. La fila enseña «Banco» y «ana». El usuario elige «Reemplazar» para todos, con un solo toque, y la entrada se queda con la web y la contraseña del atacante (la buena va al historial). Pasa lo mismo, con el mismo host, si la web pasa de `https:` a `http:`. Lo elige el usuario, pero sin ver lo que cambia. RT-1 cerró el engaño del host en las URL con host; este es el que queda.
- **Arreglo.** En `DuplicateRow`, para un LOGIN, enseñar el usuario y el host de la web del archivo, con `parseLink(...).host` o un `urlHost` público y siempre con `plainReportText`: «ana · banco-acceso.example». No hace falta ningún literal nuevo. Mejor todavía, marcar cuando la entrada de la bóveda no tiene web («(la bóveda no tiene web)»), pero eso es un texto nuevo y va a la lista del Abogado. Test de fuente en `PlatformSourcesTest`, o una función pura `duplicateSubline(entry)` probada en `ImportStateTest`.

### M-5 (menor): KDoc y diseño que ya no dicen la verdad tras R16, R17, R34, R36 y RT-1

- **`imports/MergePlan.kt:69-73` (KDoc de `planMerge`).** Dice que el título y el usuario se comparan «trimmed» y el host «lowercase, without `www.`». Desde RT-1 y RT-2 (R34, R36), los dos pasan por `compareKey` (texto mostrado, NFC, espacios juntos; el título sin mayúsculas) y el host por `parseLink` (`MergePlan.kt:356-379`).
- **`imports/MergePlan.kt:69` y `:99-104`, y `vault/VaultStore.kt:662`.** Dicen que `planImport` es «the only way the import screen builds a plan». La pantalla no lo usa: llama a `importSnapshot()` y `planMerge` por su cuenta (`ImportScreen.kt:300-301`, aceptado en R17, porque necesita `snap.entries` para la estimación). En `main`, `planImport` solo lo usan los tests. Además, `planImport` correría `planMerge` en el dispatcher de quien lo llama, no en `Default`.
- **El diseño, «Duplicado» (`docs/superpowers/specs/2026-11-03-clavero-1.4.0-design.md:239-243`).** Sigue diciendo «el usuario (sin espacios al principio ni al final)». R36 (`progress.md:231`) promete una nota de corrección fechada «in the branch review's fix round», y todavía no está.
- **Caso.** Quien mantenga el código y se fíe del KDoc puede quitar `compareKey` o `parseLink` creyendo que sobran, y volverían RT-1 y RT-2. Sus tests lo detectarían, pero la documentación engaña.
- **Arreglo.**
  - Reescribir el KDoc de `planMerge` con las reglas de R34 y RT-1.
  - Para `planImport`, una de dos: que la pantalla lo use (por ejemplo, que devuelva también la instantánea) o decir en el KDoc que la pantalla hace lo mismo con `importSnapshot()` y `planMerge` en `Default`, y que `planImport` es para los tests.
  - Añadir al diseño la nota del 8-11-2026 para R34 y R36: claves `compareKey`, host de `parseLink`, notas solo por la clave del título y el texto recortado.

## Lo que he comprobado y está bien

- **El flujo, paso a paso.**
  - Se elige el archivo (`ImportScreen.kt:458-460`, registrado antes de la salida por bloqueo, M-3 de la tarea 9).
  - Se lee una sola vez en IO, con `readAtMost(IMPORT_MAX_BYTES + 1)` y bloques de 64 KiB que paran al cancelar (`:236-258`, `:409-419`).
  - Se detecta: primero `.zip` y HTML, luego el tope y luego UTF-8 o UTF-16 estrictos (`Detect.kt:42-54`, `TextDecode.kt:102-140`).
  - La contraseña, con `IMPORT_KDF_RUN`: un solo ticket, que se suelta cuando acaba la derivación aunque se cancele (`:263-298`). `CharArray` se pone a cero en el `finally`.
  - `readImport` corre en `Default`, con el `OutOfMemoryError` capturado solo ahí (R11b).
  - El plan sale de una sola instantánea tomada bajo el mutex (`VaultStore.kt:664-667`).
  - Vista previa y estimación en `Default`, con `collectLatest` y una identidad por plan.
  - La copia: `copyBeforeImport` en el ámbito y con el lock de `AutoBackup`. Sin carpeta, `CREATE_DOCUMENT`; un documento que no se pidió se borra.
  - `applyImport`: la revisión, los tamaños y `checkCanHold` en la misma sección crítica que la escritura (`VaultStore.kt:684-718`), con un `accept` que mide el archivo cifrado exacto.
  - Al terminar, `dropAll` pone los bytes a cero y suelta las referencias.
- **La copia coincide con lo que se importa.** `exportBackup` toma su instantánea bajo el mutex (`VaultStore.kt:832-837`) y `applyImport` exige la misma revisión. Si la importación se guarda, la copia es exactamente la bóveda a la que se aplicó.
- **Carreras, decisión (b).** Todo resultado lleva su `run` (`onRead`, `onPlan`, `onOutcome`, `estimated`), así que uno tardío tras un bloqueo, un borrado, otra elección o «Cancelar» se tira. Que se pongan a cero los bytes mientras un lector los recorre en `Default` solo puede dar un resultado basura, y también se tira.
- **Bloqueo, giro y muerte del proceso.**
  - Cualquier bloqueo borra todo (`vaultStateIs`), igual que la regla de «PIN y códigos» (`watchAway`, `GuardEffects`).
  - El estado vive en el ViewModel con `ComposeCells`, sin `rememberSaveable` ni `Bundle`.
  - Tras la muerte del proceso, un archivo elegido no se lee (la bóveda está bloqueada) y un documento creado se borra (`copyTo` no encuentra el plan).
- **Topes de las KDF, decisión (a)**, siempre antes de calcular (`ImportKdf.kt:58-97`):
  - PBKDF2 de 5 000 a 2 000 000 en Bitwarden y en 2FAS (con el número fijo comprobado igual);
  - Argon2id con `checkSane` (hasta 128 MiB, de 1 a 10 pasadas y de 1 a 4 hilos, ¾ del montón por encima de 64 MiB) y la memoria en MiB acotada antes de multiplicar;
  - scrypt con N potencia de 2 de 2¹⁴ a 2²⁰, r ≤ 16, p ≤ 4, N < 2^(16·r) y 128·N·r con la regla de `checkSane`;
  - en Aegis se comprueban todas las ranuras antes de la primera derivación y solo se prueba una (R24);
  - lo cifrado se acota a 8 MiB de Base64 antes de decodificarlo.
- **Bitwarden.**
  - El HMAC de `encKeyValidation_DO_NOT_EDIT` y luego el de `data`, con `MessageDigest.isEqual`, antes de descifrar en CBC (`BitwardenCrypto.kt:135-155`).
  - Un relleno roto tras un MAC bueno es `IMPORT_BROKEN`.
  - El búfer de CBC se pone a cero y se suelta (`:162-182`).
  - Lo descifrado pasa por el mismo `checkJsonShape` y no se descifra nada dos veces (`:189-198`).
- **GCM.** Va por `blockGcm`, que borra la salida si la etiqueta falla; una etiqueta que falla es «contraseña incorrecta» (`ImportKdf.kt:217-221`). En Aegis se comprueba también la etiqueta de la clave envuelta, y `db` no se descifra con una contraseña mala.
- **Puesta a cero.** Todo lo secreto pasa por `Secrets` y se borra en un `finally`: los bytes de la contraseña, la sal, la clave, las dos mitades de HKDF, los MAC calculados y el texto descifrado (`openBitwarden`, `openAegis`, `openTwoFas`). Bouncy Castle limpia la memoria de Argon2 y de scrypt. Lo que no se puede borrar (las pads de HMac, los `String`) es el riesgo aceptado del diseño.
- **Duplicados y «Reemplazar» frente al diseño.**
  - Se mantienen el id, `createdAt`, el favorito y los adjuntos.
  - Un campo vacío del archivo nunca borra uno de la bóveda.
  - Las notas se añaden con «Importado el D-M-AAAA:», salvo el caso de M-2.
  - Las etiquetas se suman hasta 10 sin quitar las propias.
  - La contraseña anterior va al historial con `withNewPassword`.
  - `IDENTITY`, `CONTACT` y `SEED` nunca se emparejan ni se tocan (`MergePlan.kt:237-263`, `:395-420`).
  - Un reemplazo que no cambia nada no cuenta ni se guarda (`VaultStore.kt:707-710`).
  - Los TOTP se comparan con `totpKeyOf`, como `totpImportEntries`.
- **RT-1 y RT-2.**
  - `urlHost` lee el host con `parseLink`: `\` cierra la autoridad en los esquemas web y un texto sin esquema web se lee con `https://` delante (`MergePlan.kt:372-379`).
  - `SPECIAL_SCHEMES` se comparte con `LinkSignals.kt`, que solo cambia su visibilidad.
  - `compareKey` (`ImportModel.kt:139-153`) cumple R34: el título sin mayúsculas, el usuario con ellas y lo guardado tal cual.
- **Lo importado es dato hostil, decisión (c).** Se muestra solo como `Text` con `plainReportText`: el nombre del archivo, los títulos, el usuario o la web de un duplicado, las etiquetas, «no entran» y el nombre de la copia. No se convierte en enlace, en `Intent` ni en HTML. El JSON se lee solo como árbol, con nombres fijos. En `imports/` y `ImportScreen.kt` no hay `Log` ni `rememberSaveable`. Hay `NoCopy` y `FLAG_SECURE` en toda la app.
- **A6 y las copias de antes de importar (R15).** Las copias propias van en una lista aparte. La rotación no las toca, solo las borra el reemplazo de A6 (`AutoBackup.kt:636-680`). Los temporales cortados se recogen y las que no están apuntadas se cuentan por su nombre. `AppContainer` añade a `oldCopies` las copias de antes de importar.
- **Archivos de prueba y generador, decisión (e).** Son sintéticos. El generador en Python usa la biblioteca `cryptography`, cita la documentación pública y no copia código. Se escribió una vez y los tests leen los bytes guardados.

## Revisión experto-seguridad-cripto

He aplicado los criterios de `.claude/skills/experto-seguridad-cripto/SKILL.md`: primitivas estándar, comprobar el MAC antes de descifrar, comparar en tiempo constante, topes de las KDF antes de calcular, puesta a cero y ninguna fuga en errores.

- **Primitivas.** Solo Bouncy Castle: PBKDF2-HMAC-SHA256, scrypt, Argon2id v1.3, HKDF-Expand, AES-CBC con PKCS#7 y AES-GCM (`ImportKdf.kt:144-221`, `BitwardenCrypto.kt:142-182`). Nada de criptografía propia.
- **Encrypt-then-MAC bien verificado.** Primero el MAC de la validación y el de los datos, y solo después el CBC (`BitwardenCrypto.kt:136-138`). La comparación usa `MessageDigest.isEqual` (`:151`). Un relleno roto después de un MAC bueno da un error distinto, `IMPORT_BROKEN`, pero solo llega quien ya tiene la clave buena, así que no sirve de oráculo de relleno.
- **GCM.** Una etiqueta que falla borra la salida (`ImportKdf.kt:217-221`, con `blockGcm`). La sal de Aegis debe tener de 16 a 64 bytes, y los nonces y las etiquetas, su longitud exacta (`AegisCrypto.kt:66-84`).
- **KDF con tope antes de trabajar**, decisión (a):
  - `checkPbkdf2Iterations` (`ImportKdf.kt:58-60`);
  - `checkScrypt` con RFC 7914 §2 y la memoria con `checkSane` (`:69-79`);
  - `bitwardenArgon2Params` (`:94-97`);
  - todas las ranuras de Aegis antes de derivar (`AegisCrypto.kt:59-77`);
  - un `OutOfMemoryError` dentro de la KDF se convierte en `IMPORT_KDF_TOO_BIG` (`ImportKdf.kt:137-141`).

  Lo que queda es M-3: la contraseña se pide antes de comprobar la cabecera.
- **Secretos.** `Secrets.wipe()` en el `finally` de cada `open*` (`BitwardenCrypto.kt:213-214`, `AegisCrypto.kt:130-131`, `TwoFasCrypto.kt:212-213`). El `CharArray` de la contraseña se pone a cero tras la derivación (`ImportScreen.kt:295-298`). Los bytes leídos se ponen a cero en `dropAll` y en `onRead` (`ImportSession.kt:199`, `:421-424`). El búfer de validación de `decodeText` se borra (`TextDecode.kt:136-139`).
- **Ningún mensaje lleva datos del archivo.** `readImport` cambia cualquier excepción por un texto fijo (`Detect.kt:49-53`), `ImportFlow` también (`ImportFlow.kt:43`, `:54`) y `WrongType` no tiene mensaje ni traza (`ImportJson.kt:233-236`).
- **Integridad de la bóveda.**
  - La revisión, los límites de la copia y `checkCanHold` van en la misma sección crítica que la escritura (`VaultStore.kt:684-718`).
  - La escritura es atómica, con `commit` bajo `NonCancellable`.
  - La huella no se toca, porque la clave de datos no cambia.
  - Con M-1 corregido en la documentación, lo que se dice coincide con lo que se hace.
- **Suplantación.** RT-1 y RT-2 están arreglados con tests (`MergePlan.kt:356-379`, `ImportModel.kt:139-153`). M-4 cierra el último caso visible: el usuario elige sin ver la web que entra.

## Revisión Ingeniero Móvil Senior

He aplicado `desarrollo-movil-senior` (ciclo de vida, concurrencia, rendimiento), `seguridad-criptografia-movil` y `auditoria-seguridad-movil` (MASVS-STORAGE, MASVS-CRYPTO y MASVS-PLATFORM).

- **Ciclo de vida.**
  - El estado vive en el ViewModel (`ImportScreen.kt:169-389`), con `ComposeCells`, no en estado guardado.
  - `onCleared` borra la sesión (`:386-388`).
  - Los *launchers* se registran antes de salir por bloqueo, así que el resultado de un selector que llega después de la muerte del proceso se trata (`:456-463`).
  - Con la bóveda bloqueada, `LaunchedEffect(Unit) { onBack() }` (`:465-468`).
  - `VaultLock.keepOpenOnce` y `store.touch()` alrededor de los dos selectores, como en «Importar copia» (`:472-483`).
- **Concurrencia.**
  - `viewModelScope` usa `Main.immediate`, así que `decode` entra en su `try` antes de suspender y el `finally` siempre pone a cero la contraseña y suelta el ticket (`:277`, `:285-298`).
  - `withContext(Default)` espera al bloque aunque se cancele, así que el ticket no se suelta mientras la KDF sigue en marcha.
  - `confirm` y `copyTo` van bajo `NonCancellable` (`:321`, `:344`).
  - `copyBeforeImport` corre en el ámbito de la app y con el lock de `AutoBackup` (`AutoBackup.kt:402`), así que salir de la pantalla no corta la copia entre el renombrado y la lista.
  - La estimación usa `collectLatest`, que une el trabajo anterior y no deja que se acumulen cálculos (`ImportScreen.kt:193-209`).
- **Hilo principal.** La lectura va en IO; `readImport`, `planMerge` y la estimación, en `Default`; el borrado del archivo exportado, en IO bajo `NonCancellable` (`:379`). En el hilo principal solo quedan pasos de estado.
- **Rendimiento y memoria.**
  - Se usa `LazyColumn` con claves estables (`"d${index}"`, `"s${index}"`), así que miles de duplicados no bloquean la pantalla (R17).
  - El JSON se estima antes de analizarlo (R11a, R11c).
  - Hay como mucho 20 000 elementos.
  - La estimación de tamaño se hace sin construir nada cuando se sabe que no cabe (`MergePlan.kt:274-278`).
- **Plataforma.**
  - `DocumentsContract.deleteDocument` solo si el proveedor da `FLAG_SUPPORTS_DELETE` (o no da las banderas), y nunca lanza (`ImportScreen.kt:425-440`).
  - Un documento a medias se borra (`discardDocumentNow`).
  - No hay componentes exportados, permisos ni hosts nuevos.
  - `FLAG_SECURE` y `NoCopy` están presentes.
- **Lo pendiente es de UX y de documentación:** M-1, M-4 y M-5. No hay fallos de ciclo de vida.

## Textos para el Abogado Digital (revisión final)

- Ningún texto nuevo de la interfaz. M-1 y M-5 solo cambian KDoc, `CLAUDE.md`, el registro y el diseño. M-2 y M-3 no cambian textos. M-4 se puede hacer sin literales nuevos.
- Si en M-4 se elige la marca «(la bóveda no tiene web)» o algo parecido, ese literal va a la lista, en `imports/ImportTexts.kt`.
- Los textos actuales de importar que tocan este hallazgo ya están en la lista consolidada de `task-12-report.md`: `IMPORT_WIPED_WHILE_SAVING`, `IMPORT_REPLACE_NOTE` y `IMPORT_KDF_TOO_BIG`. Conviene que el Abogado lea `IMPORT_REPLACE_NOTE` («suma sus notas») junto con el arreglo de M-2.
