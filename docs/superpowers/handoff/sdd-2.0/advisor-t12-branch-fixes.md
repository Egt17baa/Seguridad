# Director: tarea 12, paso 3. Rulings de las partes A, B y C y encargo de la ronda de arreglos

**Veredicto:** una sola ronda, con un implementador opus, en nueve grupos (fila 17 primero, C-1 después): se arreglan 1 Critical, 10 Important y 11 Minor (uno lo añado yo, D-1); se aceptan con evidencia las filas 2, 20, 21, 31, 32, 33 y 46 y los Info; la fila 47 (R-6) va al dueño sin parar la ronda.

Comprobado en el código (`HEAD` solo difiere de `dcf28dc` en `app/build.gradle.kts`, así que las líneas valen): `VaultStore.kt:803-807` (`modify` da `data.live`), `:1298-1302`, `:1392`, `:1491`; `BackupCheck.kt:194-207`; `AppContainer.kt:551-555`, `:796-809`; `VaultBackup.kt:128-144`; `Vault.kt:495-520`; `VaultStore.kt:586-591`; `Crypto.kt:62-77`, `:90`, `:99-100`; `A6Text.kt:33`, `:44`, `:57`, `:84`; `VaultPickerScreen.kt:169`, `:316`, `:391-392`.

## Rulings

### Parte B (copias y ajustes)

- **B I-1, fila 17: arreglo**, con el diseño ya decidido y la condición del revisor.
  - `requestId` (`A6Text.kt:33`) **no se toca** (lo usan `AutoBackup.kt:494`, `:541` y `Settings.kt:401`).
  - Predicado nuevo `BackupTargetSettings.inRequest(request)`, que solo usa `a6Snapshot` (`A6Text.kt:57`): con un id de la 2.0, `replaceRequest == request`; con `"1.x"`, `(replacePending && replaceRequest == null) || (replaceRequest == "1.x" && replaceDone?.request == "1.x")`.
  - En `a6Summary` (`:44`): `stale = (a6?.masterMark == null && request == LEGACY_A6_REQUEST) || (las dos marcas existen y son distintas)`.
  - Tests que fallan antes: P-17a a P-17f de `branch-review-copias.md`; `aRequestOf143IsTheRequestOfNow` pasa a esperar `a6StaleText(1, 1)`.
- **Duda 1 de B (la repetición única): se acepta la opción a.** Ninguna versión publicada tiene `said` (es de la 2.0). Además, con el arreglo de la fila 18 la línea `uri=sin` que desaparece no cuenta como cambio, así que en el caso normal ni siquiera se repite.
- **B m-1, fila 18: arreglo.** En `a6Decide` (`A6Text.kt:84`): hay cambio si la cabecera (la petición) es otra, si N tiene una línea que S no tenía, o si de S desaparece una línea `=pendiente`. Con paréntesis explícitos. Los tres KDoc (`VaultStore.kt:427-448`, `A6Text.kt:77-78`, `:89-97`). Test: P-18.
- **B C-1: arreglo, y sigue Critical** (duda 2): la app escribe copias que no lee y la rotación se lleva las buenas, sin aviso.
  - Función pura `attachmentsFit(data: VaultData, adding: Long): Boolean` sobre `data.entries` (papelera incluida); `addAttachment` (`VaultStore.kt:1298-1302`) pasa a `modifyData` y la usa.
  - Texto nuevo al añadir (constante nueva, por ejemplo `ATTACHMENTS_TOTAL_WOULD_PASS`): «Con este adjunto, los adjuntos de la bóveda (también los de la papelera) pasarían de 50 MB. Quita algún adjunto o vacía la papelera para añadir otro.»
  - Sin migración: ninguna versión publicada lo permitió; una bóveda de prueba por encima se arregla al vaciar la papelera.
  - Tests: P-C1a, P-C1b y P-C1c.
- **B I-4, fila 37: arreglo**, en el mismo grupo que C-1. `BackupCheck.kt:204-205` en dos líneas: un tamaño fuera de `0..MAX_ATTACHMENT_BYTES` da `COPY_DAMAGED`; la suma por encima de 50 MB da `ATTACHMENTS_TOTAL_TOO_BIG`. Test: P-I4, y `VaultStoreTest.kt:166` pasa a `COPY_DAMAGED`.
- **D-1 (Minor, lo añade el director; misma clase que C-1): arreglo.** `upsert` solo mira las identidades vivas (`VaultStore.kt:870-871`), `restore` admite una identidad en la papelera traída por una copia (`:1176-1190`), y `checkCopyEntries` cuenta las de la papelera (`BackupCheck.kt:195`). Con una identidad en la papelera y otra creada después, cada copia propia se rechaza con `SEVERAL_IDENTITIES`. Arreglo: `checkCopyEntries` cuenta solo las identidades con `deletedAt == null`. Test: datos con una identidad en la papelera y otra viva pasan `checkCopyEntries`; dos vivas siguen dando `SEVERAL_IDENTITIES`. El revisor de B lo confirma en su re-revisión.
- **B I-2: arreglo, solo textos.** Los cinco literales nombran también las copias de antes de la 2.0, con las palabras que ya usa Ajustes (`SettingsScreen.kt:396-400`): `PASSWORD_CHANGE_REPLACE_COPIES` (`ToolTexts.kt:508`), `AUTO_BACKUP_REPLACED`, `_SEVERAL` (`AutoBackup.kt:152-164`), `a6PartialText` (`A6Text.kt:159`) y los de sin apuntar (`AutoBackup.kt:175-193`, `A6Text.kt:176-182`). En plural («las de antes de la 2.0»): con tres carpetas hay una por carpeta. Test: P-I2.
- **B I-3, fila 30: arreglo.** `COPIES_KEEP_IT` (`TrashTexts.kt:16`) → «Las copias ya hechas (automáticas o exportadas) pueden conservarlo; la copia automática quita las más antiguas.», las mismas palabras que `HISTORY_ALL_CONFIRM_TEXT` (`:106-107`). Test: P-I3.
- **Fila 39: arreglo.** Dos constantes de qué hacer y un `when` en `VaultBackup.kt:93-95` y en `VaultUpgrade.kt:61`. Exportar: «… Quita adjuntos o entradas grandes, o vacía la papelera, y vuelve a exportarla.» Paso a v4: «… Puedes seguir sin copia: la bóveda pasará al formato nuevo y las versiones anteriores ya no la abrirán.» Test: cada sitio da su texto y el del paso a v4 no dice «quita».
- **Fila 42: arreglo.** Función pura `copyFailureText(e: CryptoFailure)` en `vault/`: el texto por defecto → «Contraseña incorrecta o copia dañada. Vuelve a elegir la copia y escribe su contraseña.»; `KDF_PARAMS_INVALID` → «La copia pide unos parámetros de cifrado que Clavero no admite. Elige otra copia.»; lo demás, igual. La usan `VaultBackup.kt:183-184`, `BackupCheckSession.kt:129-130` e «Importar como bóveda nueva». Sin parámetro nuevo en `runVaultsAction`: cada acción captura su `CryptoFailure` y lanza `VaultsRefused(texto)` (`AppContainer.kt:899`), y borrar una bóveda (`:690`) usa `masterUnlockErrorText`. Test: P-42, el de fuente de los tres sitios, y borrar una bóveda con la maestra mal da `WRONG_MASTER`.
- **B m-2: arreglo, con una condición más.** Al arrancar se quita de `perVault` un id solo si **no está en el registro y además no existe su carpeta `files/vaults/<id>/`**; después se sueltan las carpetas de copias sin uso. Nunca se quita por el registro solo: un `vaults.json` ilegible no puede llevarse los ajustes ni las marcas A11 de bóvedas que siguen en el disco. Tests: P-m2, y el contrario (id fuera del registro con su carpeta en el disco: se queda).
- **Filas 20 y 21: se aceptan.** Fallan hacia el lado seguro (nada se borra, `VaultStore.kt:1552-1557`), ningún texto queda falso, la petición sigue y el `lastError` queda en Ajustes (`SettingsScreen.kt:435`, `AutoBackup.kt:894-900`). La 20 es de la 1.3.x.
- **Info i-1 a i-8 de B: se aceptan** como los deja el revisor.

### Parte A (formato y bóvedas)

- **A I-1: arreglo, y la duda se decide así: no se guarda el diálogo, pero no se pierde en silencio.**
  - `onMasterChanged(vaultId, fingerprintFailed, hasIdentity)`; `VaultBackup.kt:144` pasa `store.id`; cambia `LocalOnMasterChanged` (`VaultBackup.kt:64`, `AppNavHost.kt:329`).
  - Un cerrojo propio hace atómicos «comprobar la activa y abrir» y «cerrar por cambio de bóveda»: `onMasterChanged` comprueba `vaultId == activeId` y abre dentro de él; `switchTo` (`AppContainer.kt:551-554`) cierra dentro de él, después de poner `activeId`.
  - Si la bóveda ya no es la activa, o si `switchTo` cierra un diálogo abierto en el que no se pidió nada (`requestedAt == 0`), se publica un aviso con `vaultsNotices.post` (`AppContainer.kt:728`): «La contraseña maestra de la otra bóveda se cambió. Al cambiar de bóveda, el aviso que ofrece borrar sus copias anteriores no se llegó a mostrar o se cerró: esas copias siguen abriéndose con la contraseña anterior. Para borrarlas, vuelve a esa bóveda y cambia otra vez la contraseña maestra, o bórralas a mano.» Con `fingerprintFailed`, una línea más: «En esa bóveda la huella ya no la abre, pero no se pudo desactivar: ábrela con la contraseña nueva, desactiva la huella y vuelve a activarla.»
  - Motivo: el *snackbar* de `VaultBackup` muere con la pantalla, y sin aviso el objetivo de A6 falla callado.
  - Test que falla antes: el n.º 1 de `branch-review-formato.md`, más: el aviso está en `vaultsNotice`, y los ajustes de B no cambian tras `masterChanged.replace()`.
- **A I-2, fila 45: Ruling, opción (B).** No se reordena la derivación (C): toca `wrap` sin necesidad.
  - En `afterMasterUnlock` (`Vault.kt:516-520`), solo cuando `weaker` y el `wrap` con el coste subido lanza un `CryptoFailure` de memoria (los dos textos de la fila 38): un v4 del formato propio se escribe con `write(known.copy(masterCheckedAt = now), …)`; un v2 o v3 se envuelve con el coste exacto del archivo. Nunca se baja nada (CR-2, A8). Un fallo que no es de memoria, o sin `weaker`, sale como hoy.
  - Punto de inyección para el test: un parámetro `derive` (o equivalente) con el valor por defecto de hoy; y un aviso `onCostKept: () -> Unit = {}`.
  - Aviso, por `vaultsNotices`, solo si la bóveda llegó a abrirse y **una vez por proceso y bóveda**; también desde `replace()` (`VaultStore.kt:1483`): «No se pudo subir el coste de Argon2id al elegido en Ajustes: ahora no hay memoria suficiente. La bóveda se ha guardado con el coste que ya tenía el archivo o la copia. Se volverá a intentar cada vez que la abras con la contraseña maestra. Si en Ajustes tienes el coste «Alto», puedes elegir «Estándar».»
  - Test que falla antes: el n.º 2 (fallo **persistente** solo al envolver; dos desbloqueos abren; `kdf` del archivo igual; `masterCheckedAt` sube; el aviso, una vez; `vault-1.4.3.json` pasa a v4 con su coste).
- **A I-3, fila 38: arreglo.** `outOfMemoryAsFailure` recibe el coste (`Crypto.kt:99-100`, `:241`).
  - Con más de `DEFAULT_KDF_MEMORY_KIB` (y en `cannotFit`, `:76`): `KDF_OUT_OF_MEMORY` → «No hay memoria suficiente para este coste de Argon2id, más alto que «Estándar». Cierra Clavero del todo, vuelve a abrirlo e inténtalo otra vez; si sigue igual, este móvil no puede con ese coste.»
  - Con 64 MiB o menos, constante nueva `KDF_NO_MEMORY_NOW`: «Ahora no hay memoria suficiente para esta operación. Cierra Clavero del todo, vuelve a abrirlo e inténtalo otra vez.»
  - No se dice «Cierra otras apps»: Argon2id reserva en el *heap* de Clavero (`Crypto.kt:66-71`), y las demás apps no lo cambian.
  - La constante nueva entra en `masterUnlockErrorText` (`Vault.kt:295`), `newPasswordFailureMessage` (`Crypto.kt:204`) y `copyFailureText`. No se añade un qué hacer por sitio: el texto ya es verdad en todos los de la tabla del revisor.
  - Test: el n.º 3.
- **A m-1 y fila 46: se acepta la opción (b).** Los lugares de los selectores son de todo el móvil (`AppContainer.kt:179`, `core/PickerPlaces.kt:17-41`), no son un secreto (el URI de un documento, en disco desde la 1.4.2) y la política ya dice lo que hace el código (fila 46, O-10.7). Sin código. La fila 46 queda para el Abogado final.
- **A m-2: arreglo.** En `replace()` y en `checkBackup`, datos de la copia por encima de `VaultLimits.VAULT_MAX_DATA_BYTES` dan `COPY_DAMAGED`, después de `afterMasterUnlock` (una copia anterior a la 2.0 ya llega ajustada). Test: el n.º 4.
- **A m-3 (fila 3): arreglo.** Una función `sortedForShow` con las claves calculadas una vez; se ordena dentro de `compute` en los cuatro caminos (`VaultStore.kt:481`, `:608-609`, `:706`, `:840-845`) y `opened()` (`:500`) deja de ordenar. Tests: el n.º 5 y uno de que la lista sale ordenada tras cada camino (maestra, huella, `upsert`, importar, paso a v4). La medida en el móvil sigue en la fila 3.
- **A m-4: arreglo.** En `checkCopyStructure`, un nombre de carpeta o de vista de más de `ORGANIZE_NAME_MAX` puntos de código da `COPY_DAMAGED`. Test: el n.º 6.
- **Filas 31 y 2: se aceptan**, con la evidencia del revisor (`VaultStore.kt:593`, `:710`, `:506`; `AppContainer.kt:551`).
- **Info de A: se aceptan.** Para i-5, el implementador ejecuta la prueba n.º 7 y apunta el resultado; si la app se cierra con un `OutOfMemoryError`, pasa al ataque con la fila 24, no a esta ronda.

### Parte C (interfaz)

- **C I-1: arreglo, y la duda: sí, se cierran también «Bóveda nueva» e «Importar como bóveda nueva».** Guardan una contraseña escrita (la de una copia suele ser una maestra real); es la regla de «PIN y códigos».
  - Función pura `pickerDialogKept(dialog, state)`: con la bóveda bloqueada solo se queda `DeleteEmpty`; abierta, todos; sin bóveda, `New`, `ImportNew` y `DeleteEmpty`.
  - La señal es el estado de la bóveda activa (`activeState`), síncrona; no `canAdd`, que se calcula en IO (`VaultPickerScreen.kt:176`).
  - Tests: el n.º 1 de `branch-review-interfaz.md`, ampliado a los cinco diálogos y los tres estados, y el de fuente.
- **C I-2: arreglo**, como propone el revisor (`onBig` nulo por defecto, `bigActionFor`). Test: el n.º 2.
- **C I-3, fila 26: arreglo.** La etiqueta «WPA/WPA2» (`Templates.kt:29`) y, bajo el selector, «Si la red es solo WPA3, puede que el QR no sirva para conectarse.» Con «puede»: algunos Android prueban WPA3 con un QR `T:WPA`; se mide en el móvil (fila 23). Lo guardado no cambia. Test: el n.º 3.
- **C m-1: arreglo.** Un solo cálculo en `Dispatchers.Default` con clave `(data, breached, day)`. Mientras no hay resultado, «Salud» no enseña el texto de «todo bien» ni ninguna cuenta. Test: el n.º 5. Con esto la fila 33 recibe la clave `day`.
- **C m-2: arreglo, texto.** `settings/Guide.kt:42` y `:61` → «Las copias con calendario se hacen con la bóveda abierta, al abrir Clavero o al desbloquearla, y solo si hubo cambios.» (`AutoBackup.kt:457`, `:533-535`). Test: el que fije el texto de la guía.
- **C m-3: arreglo**: `onCopied = twoStep::clear` en el historial (`EntryHistoryScreen.kt:160`). Test: de fuente.
- **Filas 32 y 33: se aceptan** (`Missions.kt:87-103`, `VaultRegistry.kt:102`, `VaultStore.kt:1613-1615`; `HealthScreen.kt:125`).
- **C i-1 e i-2: se aceptan.** i-2 es de antes de la 2.0, con tope de 16 y `FLAG_SECURE`; queda apuntado para la auditoría final.

### Filas sin revisor

- **Fila 47 (R-6): Owner.** El diseño la aceptó (`2026-11-12-clavero-2.0-design.md:222`) y no tiene línea propia; la cubren en parte `BACKUP_CHECK_NEVER` (`BackupCheckTexts.kt:80`) y la misión. No entra en esta ronda.
- **Fila 50:** de la parte D.

## Encargo de la ronda

> Eres el implementador de la ronda de arreglos de la revisión de la rama de Clavero 2.0 (Android, Kotlin y Compose), en `/home/user/Seguridad`, rama `ccr-0aa27f95-u038cd`. Modelo: opus. Trabajas solo: nadie más toca `app/` mientras dure la ronda.
>
> **Lee antes:** `CLAUDE.md`; `.claude/skills/experto-seguridad-cripto/SKILL.md` y sus `references/`; las skills `desarrollo-movil-senior` y `seguridad-criptografia-movil`; `superpowers:test-driven-development`; `.superpowers/sdd/2026-11-12-clavero-2.0/advisor-t12-branch-fixes.md` (los **Rulings: obligan, palabra por palabra en los textos**); las tres revisiones `docs/superpowers/handoff/sdd-2.0/branch-review-{copias,formato,interfaz}.md` (los Issues y «Pruebas que pide el revisor»); `docs/superpowers/handoff/sdd-2.0/progress.md` (cada Ruling) y `pendientes.md`. Las líneas citadas son de `dcf28dc` y siguen valiendo.
>
> **No choques con el paso 1 (documentos), que otro agente termina en este mismo checkout:**
> - Solo tocas `app/src/main` y `app/src/test`. Nunca `docs/`, `README.md`, `CLAUDE.md`, `.maestro/`, `app/build.gradle.kts` ni `gradle/`.
> - `git add` solo de tus archivos, por su ruta; nunca `git add -A`, `stash`, `checkout` de archivos ajenos ni `reset`. Hay cambios ajenos sin confirmar (`docs/privacidad.html`): no los toques.
> - Si un test ata un documento a una constante que cambias (README, notas, traspaso), **para y avisa al controlador**; no edites el documento.
> - Avisa al controlador antes de cada ejecución de gradle: una sola suite a la vez en la máquina.
>
> **Cómo:** TDD en cada punto: primero el test, verlo fallar por el motivo esperado (pega la salida en el informe), después el arreglo. Sin criptografía nueva, sin logs, sin cambiar ningún formato. Cada commit compila. No hagas *push*.
>
> **Orden y commits** (un commit por punto salvo donde se dice):
> 1. **Fila 17** (B I-1), en sus propios commits: tests P-17a a P-17f y el arreglo.
> 2. **Fila 18** (B m-1 y los tres KDoc), P-18.
> 3. **C-1**, con **fila 37** (B I-4) y **D-1**: tres commits. Después, las dos suites enteras.
> 4. **A I-1** (el diálogo de A6 y el cambio de bóveda).
> 5. **Fila 38** (A I-3) y después **fila 45** (A I-2): dos commits, en ese orden (la 45 usa los dos textos de memoria).
> 6. **A m-2 y m-4** (un commit) y **A m-3** (otro). Ejecuta la prueba n.º 7 de la parte A y apunta el resultado. Después, las dos suites enteras.
> 7. **Textos de las copias:** B I-2, fila 30, fila 39 y fila 42 (cuatro commits).
> 8. **B m-2** (ajustes huérfanos, con la doble condición).
> 9. **Parte C:** I-1, I-2 con m-3, fila 26, m-1, m-2 (cinco commits). Al final, las dos suites enteras.
>
> **Suites, siempre por separado y nunca a la vez:**
> ```bash
> export ANDROID_HOME=/home/user/android-sdk
> ./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets --no-daemon --max-workers=1
> ./gradlew --stop
> ./gradlew -p jvm-check test --no-daemon --max-workers=1
> ```
> Entre medias, solo los tests del punto (`--tests`). Un test nuevo que usa Android va a la lista `androidOnly` de `jvm-check` solo si ya hay otros de su clase ahí; si no, avisa.
>
> **Commits:** `git -c user.name=Egt17baa -c user.email=Egt17baa@users.noreply.github.com commit`, mensaje «2.0, revisión de la rama: <punto>», con las líneas de atribución de la sesión al final. Nunca un nombre, un correo ni un dato del dueño.
>
> **Si algo no encaja** (un Ruling contradice el código, un texto resultaría falso, un arreglo pide tocar un formato): no improvises; apúntalo en el informe y sigue con el punto siguiente.
>
> **Informe:** `/home/user/Seguridad/.superpowers/sdd/2026-11-12-clavero-2.0/branch-fixes-report.md`, escrito por bloques tras cada grupo: por punto, el commit, el test y su fallo de antes, los archivos y líneas; la salida final de las dos suites (número de tests); lo no hecho y por qué; cada texto que cambia, con su constante y el antes y el después (para la segunda pasada de documentos); y las secciones «Revisión experto-seguridad-cripto», «Revisión Ingeniero Móvil Senior» (con `archivo:línea`) y «Textos para el Abogado Digital (revisión final)».
>
> **Hecho** quiere decir: los nueve grupos confirmados, cada test nuevo visto en rojo y en verde, las dos suites en verde sobre el último commit, y el informe en su sitio.

## Re-revisión

- **Revisor de B (opus, el mismo encargo, solo lectura, clon propio):** grupos 1, 2, 3, 7 y 8, y D-1. Primero la fila 17 y C-1.
- **Revisor de A:** grupos 4, 5 y 6; en la fila 45, que el coste no baja en ningún camino y que el aviso no sale tras un bloqueo.
- **Revisor de C:** grupo 9, y los textos nuevos que se ven en pantalla de los grupos 4 y 5 (el aviso de `vaultsNotices`).
- Cada uno comprueba además que ningún arreglo de otro grupo rompe lo suyo, y cierra con sus dos secciones.
- **Controlador:** las dos suites en un clon limpio del último commit; las filas de `pendientes.md` (17, 18, 26, 30, 37, 38, 39, 42 y 45 hechas; 2, 20, 21, 31, 32, 33 aceptadas; 3 sigue «Móvil»; 46 al Abogado; 47 al dueño); una fila «Móvil» nueva para medir la falta de memoria con «Alto» y una bóveda grande; después, la segunda pasada de documentos (los textos cambiados) y la parte D.

## Preguntas al dueño

1. **Fila 47 (R-6), no bloquea:** el recordatorio en Ajustes tras «Seguir sin copia» no tiene línea propia; hoy lo cubren «Aún no has comprobado ninguna copia.» y la misión «Comprueba una copia». ¿Vale así (y lo ve el Abogado final), o quieres la línea propia? La línea propia pide guardar un dato nuevo.

## Riesgos o dudas

- **No ejecuté nada:** todo sale de leer el código. D-1 lo leí en tres sitios pero no lo probé; lo confirma su test.
- **El grupo 5 toca el desbloqueo.** Un error en la fila 45 deja bóvedas sin abrir o baja un coste; por eso su test usa un fallo persistente y el revisor de A mira cada eje.
- **El grupo 6 (m-3) cambia dónde se ordena** en cuatro caminos de `VaultStore`: un camino olvidado da una lista sin ordenar; lo cubre el test de orden por camino.
- **B m-2 borra ajustes al arrancar:** solo con la doble condición. Si el implementador no puede comprobar la carpeta de la bóveda desde ahí, no lo hace y lo dice.
- **Los textos cambian constantes que citan el README y las notas** (`KDF_OUT_OF_MEMORY`, `COPIES_KEEP_IT`, los de A6, la guía): si un test los ata, la ronda se para en ese punto hasta que el controlador coordine con el paso 1.
- **Memoria de la máquina:** las dos suites juntas dan exit 137; por eso van separadas y con aviso.
- **A I-1:** el aviso manda a «cambiar otra vez la contraseña» para recuperar el diálogo. Es verdad (`AppContainer.kt:802`, lee las copias apuntadas de ese momento), pero es incómodo; el Abogado puede preferir otra redacción.
- **C-1 en bóvedas de prueba ya por encima de 50 MB:** sus copias siguen sin poder importarse hasta vaciar la papelera. Solo afecta a versiones de prueba.

## Textos para el Abogado Digital (revisión final)

Todos los de los Rulings: el de añadir un adjunto (C-1); los cinco de A6 con «las de antes de la 2.0» (B I-2); `COPIES_KEEP_IT` (fila 30, con la 19); los dos de la fila 39; los dos de la fila 42; los dos avisos nuevos de `vaultsNotices` (A I-1 y fila 45); `KDF_OUT_OF_MEMORY` y `KDF_NO_MEMORY_NOW` (fila 38); la etiqueta y la línea del wifi (fila 26); la línea de la guía (C m-2). Además, la fila 46 (los lugares de los selectores en la política) y la fila 47 según responda el dueño.

## Revisión experto-seguridad-cripto

- `vault/VaultStore.kt:803-807`, `:1298-1302`, `:1392`, `:1491` y `vault/BackupCheck.kt:204-207`: C-1 confirmado; el límite al añadir no ve la papelera y la copia sí. Rompe «una copia propia siempre se lee».
- `vault/BackupCheck.kt:195` con `vault/VaultStore.kt:870-871`, `:1176-1190`: D-1, la misma clase con la identidad.
- `AppContainer.kt:551-555`, `:796-809` y `ui/screens/VaultBackup.kt:128-144`: A I-1 confirmado; `onMasterChanged` lee `activeId` después de que `switchTo` lo cambió y cerró el diálogo. Se borrarían copias de otra bóveda.
- `vault/Vault.kt:508-520` y `vault/VaultStore.kt:586-591`: fila 45; el coste no baja en ningún eje y la opción (B) lo conserva (usa el coste del archivo).
- `crypto/Crypto.kt:62-77`, `:90`, `:99-100`: fila 38; un solo texto para cualquier coste. Argon2id sigue siendo el de Bouncy Castle, sin cambios.
- `vault/A6Text.kt:33`, `:44`, `:57`, `:84`: filas 17 y 18; veracidad sobre qué copias siguen abriéndose con una contraseña anterior.
- `ui/screens/VaultPickerScreen.kt:169`, `:316`, `:391-392`: C I-1; el nombre cifrado de la bóveda y contraseñas escritas quedan a la vista tras un bloqueo.
- Ningún arreglo añade primitivas, permisos, red ni registros, ni cambia `seguridad-vault`, `seguridad-backup`, `SG1:` o `.sgenc`.

## Revisión Ingeniero Móvil Senior

- `AppContainer.kt:728-736` y `ui/AppNavHost.kt:296-305`: `vaultsNotices` vive en `AppContainer` y lo pinta la navegación; sirve para los dos avisos nuevos sin estado en la composición y sobrevive al giro.
- `ui/screens/VaultPickerScreen.kt:176`: `canAdd` se calcula en IO; por eso el cierre de los diálogos usa `activeState`, que es síncrono.
- `vault/VaultStore.kt:465-486`, `:500`: `commit` no cambia de *dispatcher*; el orden corre en el hilo de quien llama (m-3).
- `ui/screens/HealthScreen.kt:125-136`: «Salud» calcula en la composición (C m-1); al sacarlo, el estado vacío no debe decir «todo bien».
- `AppContainer.kt:701-713`: el registro se escribe antes que los ajustes; el huérfano posible es el de B m-2.
- `app/build.gradle.kts`: es lo único que cambió desde `dcf28dc` en `app` y `jvm-check`; la ronda no lo toca.

**Modelo:** el director (`advisor`).
