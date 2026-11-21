# Repaso de la ronda 6 de la tarea 8 (Clavero 2.0)

- **Alcance:** de `a883dbd` a la cabeza `2e7d82c` (`review-a883dbd..2e7d82c.diff`).
  - Entran `a6Decide` y `a6Snapshot`, el `said` derivado, la marca de la maestra (`masterMark`, de la sal) y `answerRequest`.
  - Entran también `a6DialogView`, `changeTargetFolder`, los KDoc, los tests nuevos y el anexo.
- **Cómo se hizo:** solo leyendo el código. **No ejecuté nada**: ni gradle ni tests, que las suites las corre el controlador en un clon limpio. Las líneas son las de `2e7d82c`.
- **Lentes:**
  - `experto-seguridad-cripto` (`SKILL.md`): un hallazgo confirmado es Important como mínimo; uno teórico, menor;
  - `desarrollo-movil-senior` y `auditoria-seguridad-movil`;
  - las reglas del encargo: un texto de A6 que puede afirmar algo que el código no hizo es **Important**, y también lo es un aviso perdido con un uso normal.

## Veredicto

**No aprobada: 1 Important, 4 Minor y 4 informativos.**

- **Lo que pedía la ronda está bien hecho, para las peticiones pedidas en la 2.0:**
  - I-1 de la re-revisión 5: una carpeta que vuelve a la petición la reabre, y una nueva de verdad no.
  - I-2: una petición de un cambio anterior lo dice.
  - m-1: el diálogo solo enseña su propia petición.
  - m-3 y m-4 de la re-revisión 5.

  Los tests nuevos fallarían si volviera el fallo.
- **I-1 de esta re-revisión:** las peticiones **heredadas de la 1.4.3** (`"1.x"`) no tienen marca y nunca salen `stale`. Además, en ellas cualquier carpeta nueva cuenta como de la petición. Hay dos caminos, y en los dos la app dice «se borraron las copias … hechas antes del cambio de contraseña» después de un cambio en la 2.0 sin «Borrar…», sin que falle ninguna escritura:
  - **Camino a (anterior a esta ronda):** es I-2 sin arreglar para esas peticiones. Contradice la frase del informe «siempre era de su último cambio».
  - **Camino b (nuevo en esta ronda):** en la ronda 5, `closed` lo callaba.
- **Lo que el controlador tiene que decidir con el Ruling:** dice que una regresión de la ronda 6 se deshace con `git revert` y que otro Important va a `pendientes.md`.
  - **Mi recomendación es no revertir.** Un `git revert` de la ronda quita `a6Decide` y vuelve a poner `A6State.closed`, así que devuelve I-1 de la re-revisión 5 (un aviso perdido con un uso normal, para cualquier petición) y deja I-2 sin arreglar. Es peor que lo que se quiere quitar.
  - **Lo nuevo de esta ronda está acotado** a cómo se reconoce quién es de la petición `"1.x"` (`A6Text.kt:33`, `:57`). Se arregla con una condición.
  - **Por tanto:** arreglar hacia delante el camino b, o apuntarlo con el camino a en `pendientes.md` como riesgo con nombre, «peticiones heredadas de la 1.4.3», para la parte «copias y ajustes» de la revisión de la rama.
- **m-1 de esta re-revisión, también nuevo en esta ronda pero Minor:** quitar una carpeta ya hecha de una petición cerrada repite el éxito viejo en el siguiente desbloqueo. Es verdad mientras la marca sea la misma, y por eso es Minor. Con `"1.x"` cae en I-1.

## 1. `a6Decide`

### 1.1 Entradas

`a6Decide(targets, a6, failures, sameVault, masterMark)` (`A6Text.kt:81-87`) recibe:

| Entrada | Viene de (`publish`, `AutoBackup.kt:579`) | Para qué |
|---|---|---|
| `targets` | `targets()`, las carpetas de ahora, leídas una vez bajo el cerrojo | El resumen y la foto |
| `a6` | `a6()`: `id`, `said` (la foto cerrada) y `masterMark` | La petición, lo cerrado y la marca |
| `failures` | Los `toldKey` nuevos de la pasada (`:578`) | Los fallos se dicen una vez |
| `sameVault` | `store.generation == since` (N-4) | Tras una importación no hay resumen |
| `masterMark` | `store.masterMark()` (`VaultStore.kt:433-437`) | Para saber si es `stale` |

La foto (`a6Snapshot`, `A6Text.kt:55-67`) es `request` y una línea por cada carpeta de la petición: las que tienen `requestId == request`, ordenadas por URI, en estado `pendiente`, `hecha:borradas:sin_apuntar` o `sin`.

### 1.2 Ramas

1. `summary = a6Summary(...)`, solo si `sameVault && replaced > 0`. Si no, es nulo (`:82`).
2. `changed = summary != null && snapshot != a6?.said` (`:84`).
3. Si no hay fallos y no hay cambio, devuelve nulo y se calla (`:85`).
4. Si no, devuelve `A6Decision(failures, summary, snapshot)`. `publish` lo publica con `said = snapshot` solo si hay resumen (`AutoBackup.kt:594`), y cerrarlo guarda esa foto (`:397-402`).

### 1.3 El invariante y sus excepciones

**El invariante:** sea S la foto cerrada y N la de ahora. Si una carpeta de la petición está pendiente en N y no lo estaba en S, o tiene en N un resultado que no estaba en S, entonces N ≠ S. Por tanto, la app habla siempre que haya alguna carpeta hecha y la misma bóveda.

- Un resultado de la petición implica `replaced ≥ 1` (`A6Text.kt:43`), así que en ese caso la condición `replaced > 0` siempre se cumple.
- Sobrevive a un reinicio: S está en `settings.json` (`BackupSchedule.kt:113-117`) y N se recalcula de los ajustes guardados.
- Un resumen publicado y no cerrado deja S como estaba, así que el desbloqueo siguiente lo vuelve a publicar (`AutoBackup.kt:546`).

**Se calla en tres casos, que juzgo así:**

| Excepción | Cuándo | Juicio |
|---|---|---|
| Ninguna carpeta hecha | `replaced == 0` (`:82`) | Aceptable. No hay nada que afirmar. Los fallos se dicen una vez y quedan en el `lastError` de Ajustes |
| Una importación desde que empezó la pasada | `sameVault == false` | Aceptable. La importación olvidó la petición (`Settings.kt:255-260`). Es N-4 |
| La carpeta ya estaba pendiente en S (el riesgo con nombre) | Una carpeta que sigue pendiente y no cambia | **Aceptable.** S se cerró con ese parcial («En la otra carpeta puede quedar…») y con su fallo, que se dijo una vez. En cuanto la carpeta cambia (reemplaza, se va o vuelve), N ≠ S y habla. No se calla nada que no se haya dicho ya |

**Una cuarta excepción no es aceptable:** con `"1.x"`, la regla de quién es de la petición está mal (I-1).

El KDoc de `a6Decide` (`A6Text.kt:77-78`) dice más de lo que hace el código: «nothing is ever kept silent while a target in the request waits». Va a m-3.

### 1.4 Una carpeta que vuelve reabre; una nueva de verdad no

- **Vuelve:**
  - `keptPast` guarda `replaceRequest` (`Settings.kt:243-252`) y `restored` lo devuelve. La carpeta vuelve a ser de la petición y su línea no está en S, así que habla.
  - Si estaba pendiente y reemplaza, su `hecha:…` (con lo que quedó sin apuntar) es nuevo, así que habla.
  - Lo fijan `aFolderThatComesBackReopensTheRequest` (`AutoBackupTargetsTest.kt:1539-1572`, también tras reiniciar sin cerrar), `aFolderThatComesBackAndFailsIsToldOnce` (`:1574-1600`) y el bucle «vuelve» del exhaustivo (`:1359-1370`).
- **Nueva de verdad:** tiene `replaceRequest = null`, así que su `requestId` es `"1.x"` (`A6Text.kt:33`), que no es un UUID y no es de la petición. Bien: lo fija `:1355-1358`. **Salvo que la petición de ahora sea `"1.x"` (I-1).**

## 2. Los tests

- **El escenario de I-1 de la re-revisión 5:** `aFolderThatComesBackReopensTheRequest` sigue el escenario paso a paso. Comprueba el texto exacto (`AUTO_BACKUP_REPLACED_SEVERAL` y una copia sin apuntar) y que vuelve a salir tras un reinicio sin cerrarlo. Con el cierre de la ronda 5 (M6a) fallaría. La variante b fija «una vez» con un `cycle` final.
- **El exhaustivo** (`:1281-1376`, 8² + 8³ = 576 combinaciones). Añade:
  - «sin nada cerrado, habla si y solo si hay alguna hecha» (`:1353`);
  - «cerrado tal cual, calla» (`:1355-1356`);
  - «una añadida después no lo reabre» (`:1357-1358`);
  - «cada carpeta de la petición que se va, se cierra sin ella y vuelve, habla si hay alguna hecha» (`:1359-1370`).

  Fallaría con M6a (cierre permanente) y con M6b (una foto con todas las carpetas).
- **Los límites del exhaustivo:**
  - solo usa la petición `"R"` y la marca nula, así que no puede ver I-1 ni ningún `stale`;
  - no tiene el caso «cerrado y luego se quita una carpeta hecha» (m-1).
- **La aserción nueva de m-4** (`:1351`) compara el texto entero (el fallo, `\n` y el resumen). Ya no se cumple por construcción.
- **Los demás:**
  - `aRequestOfAnEarlierChangeSaysSo` (`:1605-1640`) cubre `stale` con 2 de 2, pero no las ramas de una carpeta ni la del parcial de `a6StaleText` (m-2);
  - `theDialogShowsOnlyItsOwnRequest` (`:1642-1665`) y `theDialogOfTheChangeInEachState` (`:1667-1685`) cubren m-1 y la rama `unknown`;
  - `changingAFolderSaysFromTheWriteWhetherARequestMoved` (`:1687-…`) cubre m-3.

## 3. I-2 y m-1: la marca de la sal

### 3.1 Cuándo cambia la sal

- **Cambio de maestra: siempre.** `VaultCrypto.changeMaster` llama a `wrap` (`Vault.kt:504-509`), y `wrap` saca una sal nueva cada vez (`Vault.kt:330-331`). `VaultStore.changeMaster` pone `vault = next` antes de bloquear (`VaultStore.kt:1297-1316`).

  **«La sal no cambió pero la maestra sí» no puede pasar.**
- **A8** (volver a cifrar con un coste mayor al desbloquear con la maestra): `afterMasterUnlock` también llama a `wrap` (`Vault.kt:492-494`), así que la sal cambia.
  - En el camino normal no pasa entre «Borrar…» y el final de la petición, porque `changeMaster` cifra con el perfil de ahora (`kdf()`).
  - Solo pasa si el perfil se sube entre medias. Entonces sale el texto `stale` en lugar del éxito, y ese texto sigue siendo verdad (§3.2).
- **El paso a v4:** cambia la sal (`Vault.kt:492-494`; `VaultStore.completeUpgradeLocked`, `:612-625`). Pero `changeMaster` exige `upgrade == null` (`VaultStore.kt:1298`), así que **ninguna petición con marca se pide sobre un archivo v3**. Para las peticiones de la 2.0 no se llega a este caso. Importa para I-1, al proponer el arreglo.
- **«Importar copia»:** el archivo cambia de sal, pero la importación olvida `a6` y todas las peticiones (`Settings.kt:255-260`). Si la importación no se guarda y se deshace, el archivo no cambió.

### 3.2 ¿El texto `stale` es verdad siempre que sale?

`a6StaleText` (`A6Text.kt:124-133`) dice: «Se terminaron de borrar las copias que la app tenía apuntadas de antes de un cambio de contraseña anterior, {en la carpeta | en todas las carpetas | en N de las M carpetas; en las demás puede quedar…}. Las copias hechas después de ese cambio no se han borrado: si cambiaste la contraseña otra vez, puede que alguna se abra con una contraseña anterior. Míralo a mano.».

- **Con otro cambio de maestra:** es verdad.
- **Con A8 y sin otro cambio:**
  - «de antes de un cambio de contraseña anterior» es verdad: el cambio es pasado;
  - «si cambiaste la contraseña otra vez…» va en condicional y es verdad.
- **«Las copias hechas después de ese cambio no se han borrado»:** la rotación normal (Conservar N) puede haber borrado alguna. La frase peca por prudencia, pero no es exacta. Va al Abogado (m-4).
- **«en todas las carpetas»:** solo si `replaced == folders`, contando todas las carpetas de ahora. Una carpeta añadida después baja a «N de las M…; en las demás puede quedar…». Es verdad.

**¿Puede dar un «se terminaron de borrar» falso u omitir el aviso?**
- **Con marca: no.** Que salga `stale` exige marcas distintas (`A6Text.kt:44`), y con la misma marca no hubo ningún cambio de maestra desde la petición.
- **Sin marca (`"1.x"`): sí omite el aviso, y entonces sale el éxito llano.** Es I-1.

### 3.3 ¿Se guarda algo de la sal en `settings.json`?

- Se guarda `A6State.masterMark`, que es el SHA-256 en hexadecimal de `"a6|" + sal` (`VaultStore.kt:435`). La sal ya está sin cifrar en el archivo de la bóveda, en la misma carpeta privada, y la etiqueta `a6|` separa este hash de cualquier otro uso.
- **No revela nada** que no dé ya el archivo: ni la contraseña ni la clave, que exigen Argon2id con la contraseña. Como mucho, quien lea los dos archivos sabe si la bóveda se volvió a cifrar desde la petición.
- La copia del sistema está desactivada (`AndroidManifest.xml:11`).
- La foto (`said`) guarda los URI de las carpetas, que `settings.json` ya guardaba, y números. Es para la política (§«Textos»).

### 3.4 ¿El diálogo de la maestra enseña solo su petición?

**Sí.**
- `answerRequest` se vacía al pedir (`MasterChanged.kt:73`) y se rellena con `r.request` de la respuesta (`:86`).
- Todas las respuestas con petición la nombran (`AutoBackup.kt:673`, `:681`). La del id sin guardar (`:651-653`) y la de una excepción (`MasterChanged.kt:83`) no nombran ninguna.
- El diálogo filtra con `summary?.takeIf { it.request == state.answerRequest }` (`SettingsScreen.kt:402`), y el test de fuentes lo fija (`PlatformSourcesTest.kt:1071`).
- El resumen del diálogo se calcula sin marca (`AppNavHost.kt:257`), así que nunca sale `stale`. Es correcto, porque su propia petición es la del cambio de ahora.

## 4. m-2 a m-5 de la re-revisión 5

- **m-2:** la lista del informe tiene ya la fila del deshacer de una importación (`VaultStore.kt:1513-1519`) y corrige las filas «lo dicho» y «reemplazo hecho».
  - El deshacer no se arregla, porque no da ningún texto falso, y va a «Dudas» para `pendientes.md`. Es lo que pide el Ruling (solo se arreglan las que dan una afirmación falsa).
- **m-3:** `changeTargetFolder` (`SettingsStore.kt:111-116`) decide dentro del `mutex.withLock`, con la misma escritura, y Ajustes lo usa (`SettingsScreen.kt:233-246`, `:254`).
  - Devuelve `false` si es la misma carpeta, si `to` ya es una carpeta (`after == before`) y si el reemplazo terminó justo antes.
- **m-4:** la aserción ya no se cumple por construcción (`AutoBackupTargetsTest.kt:1351`). `a6DialogView` (`A6Text.kt:142-155`) tiene su test, con `unknown` incluido.
- **m-5:** están al día los KDoc de `pastTargets`, `withTargetRemoved`, `publish`, `replaceOldCopies` y `A6State`. Queda uno nuevo, de esta ronda (m-3 de esta re-revisión).

## 5. Regresiones

- **Los hallazgos anteriores:**
  - I-1 de la re-revisión 5: cerrado para las peticiones de la 2.0;
  - I-2: cerrado para las de la 2.0, y abierto para las de `"1.x"` (I-1);
  - m-1 a m-5: cerrados;
  - i-1: va a `pendientes.md` (informe, «Dudas»).
- **D-3:** sin cambios en `copiedMark` ni en la regla del salto (`AutoBackup.kt:540`).
- **N-4:** `sameVault` (`AutoBackup.kt:579`). `replaceOutcomeShown` sigue mirando la petición (`:402`).
- **O-5:** sin cambios en `keptPast` ni en `restored`.
- **El arreglo de m-2 de la ronda 5:** sigue (`AutoBackup.kt:651-653`). La marca va en la misma escritura que el id, así que si no se guarda, no hay petición.
- **Los ajustes de la ronda 5 con `closed`:** se leen igual, porque `appJson` ignora las claves desconocidas (`core/Json.kt:7`).

## Issues

### Critical

Ninguno.

### Important

**I-1. Una petición heredada de la 1.4.3 (`"1.x"`) se da por hecha tras un cambio de maestra en la 2.0 sin «Borrar…».**
- **Dónde:**
  - **sin marca, nunca `stale`:** `a6Summary` exige `a6?.masterMark != null` (`A6Text.kt:44`). Una petición de la 1.4.3 llega sin `a6` (`SettingsStore.kt:208-216`: el destino lleva `replacePending` y `replaceRequest = null`) y, al cerrarla, se guarda `A6State("1.x")` sin marca (`AutoBackup.kt:402`);
  - **quién es de la petición:** `requestId` vale `replaceRequest ?: "1.x"` (`A6Text.kt:33`), y `a6Snapshot` filtra con él (`:57`). Así, con la petición `"1.x"`, toda carpeta nueva (`replaceRequest = null`) cuenta como de la petición.

  Verificado: `aRequestOf143IsTheRequestOfNow` (`AutoBackupTargetsTest.kt:1396-1403`), y el resultado guarda `replaceDone("1.x")` (`Settings.kt:320-322`).
- **Camino a (anterior a esta ronda: es I-2 sin arreglar para estas peticiones):**
  1. Con la 1.4.3, el usuario cambió la maestra y pulsó «Borrar…». La petición quedó pendiente (la carpeta falla, o la app se actualizó antes de desbloquear).
  2. Ya en la 2.0, cambia la maestra otra vez y cierra el diálogo **sin** «Borrar…».
  3. La carpeta se recupera y la petición `"1.x"` termina. Sale «Se hizo una copia nueva y se borraron las copias … hechas antes del cambio de contraseña: las que la app tenía apuntadas en esta carpeta.».
  4. Es falso para el cambio que el usuario acaba de hacer: las copias de la contraseña anterior siguen ahí.

  El informe dice «La de la 1.4.3 siempre era de su último cambio». Es verdad al actualizar, no después de un cambio hecho en la 2.0.
- **Camino b (nuevo en esta ronda):**
  1. La petición `"1.x"` termina en la 2.0 y el usuario cierra el resumen: `said = {uno=hecha}`.
  2. Cambia la maestra sin «Borrar…».
  3. Añade una carpeta nueva, «dos». Con `requestId` igual a `"1.x"` cuenta como de la petición, con la línea `dos=sin`, y la foto cambia.
  4. En el desbloqueo siguiente sale `a6PartialText(1, 2)`: «Se borraron las copias … hechas antes del cambio de contraseña (las que la app tenía apuntadas) en 1 de las 2 carpetas.».
  5. Es falso para el cambio nuevo en «uno».

  En la ronda 5, `closed` lo callaba. Sin un cambio de maestra entre medias es un parcial de más, con un texto verdadero.
- **Por qué es Important:** la regla del encargo, y no hace falta que falle ninguna escritura. Lo atenúa que solo afecta a las peticiones que vienen de la 1.4.3, que son las de un usuario con una petición pendiente al actualizar.
- **¿Regresión o pendiente?:** el camino b es regresión de esta ronda y el a, no. Sobre el `git revert`, ver el Veredicto: **no** lo recomiendo.
- **Arreglo (lo que tiene que cumplir):**
  1. **Quién es de la petición `"1.x"`:** solo las carpetas que estaban en ella, es decir, `replaceRequest == "1.x"` explícito, o `replacePending` o `replaceDone("1.x")`. Nunca una carpeta con `replaceRequest == null` sin petición. Eso cierra el camino b.
  2. **Para el camino a,** una petición `"1.x"` tiene que salir `stale` si hubo un cambio de maestra en la 2.0 desde que se heredó.
     - **Cuidado con el orden** si se apunta una marca «la primera vez que la 2.0 ve la petición». El primer desbloqueo de un v3 deja `vault` en v3 hasta `completeUpgrade` (`VaultStore.kt:612-625`), y el archivo v4 lleva una sal nueva (`Vault.kt:492-494`). Una marca tomada en ese desbloqueo saldría `stale` en cuanto se guarde el v4. El texto seguiría siendo verdad, pero se perdería el éxito.
     - **Lo más directo** es hacerlo en el cambio de maestra: si hay una petición sin marca, apuntarle la marca de antes del cambio, bajo el cerrojo del motor y antes de cualquier `replaceNow`.
- **Test:** el exhaustivo con la petición `"1.x"` (los estados heredados), y los dos caminos de arriba con un cambio de maestra en medio.

### Minor

**m-1. Quitar una carpeta ya hecha de una petición cerrada repite el éxito viejo en el siguiente desbloqueo (nuevo en esta ronda).**
- **Dónde:** `A6Text.kt:84`. Cualquier diferencia entre N y S cuenta, también una línea `hecha` que desaparece. `runDue(afterUnlock)` publica siempre (`AutoBackup.kt:546`).
- **Escenario:**
  1. «uno» y «dos» reemplazan y el usuario cierra «…en cada carpeta».
  2. Meses después quita «dos». No estaba pendiente, así que no hay `retell` (`SettingsScreen.kt:355-357`).
  3. En el desbloqueo siguiente sale otra vez «Se hizo una copia nueva y se borraron … en esta carpeta.» bajo «Copias automáticas».
- **Por qué es menor:** con la misma marca no hubo ningún cambio de maestra desde la petición, así que el texto sigue siendo verdad. Es una repetición, contra la regla de no repetir de las rondas 1 y 4. Con `"1.x"` no hay marca y puede ser falso: eso es I-1.
- **Arreglo (lo que tiene que cumplir):** una línea que desaparece solo es noticia si estaba `pendiente` en S. Así siguen hablando los dos tests que necesitan que hable una carpeta quitada, porque en los dos estaba pendiente: `aFolderThatKeepsFailingNeverHidesTheCountOfTheOthers` (`AutoBackupTargetsTest.kt:1239-1270`) y `aFolderThatComesBackReopensTheRequest` (`:1539-1572`).
- **Test:** S con todas hechas, quitar una hecha y desbloquear: nada.

**m-2. Tests que faltan.**
- Las ramas de `a6StaleText` con una carpeta y con el parcial (`A6Text.kt:126`, `:128`) no tienen test.
- El exhaustivo no tiene `stale`, `"1.x"` ni el caso «cerrado y quitar una hecha».
- **Arreglo:** añadirlos. Lo de `"1.x"` va con I-1.

**m-3. Documentación.**
- **`VaultStore.kt:418-426`:** el KDoc de `changeMark` queda huérfano. El de `masterMark` (`:427-432`) se metió entre el KDoc y su función (`:439`), así que `changeMark` se queda sin documentación y queda un bloque suelto.
- **`A6Text.kt:77-78`:** el KDoc de `a6Decide` dice «nothing is ever kept silent while a target in the request waits». No es así: calla con `replaced == 0`, tras una importación y con una carpeta que ya estaba pendiente en S (§1.3).
- **`A6Text.kt:89-97`:** el KDoc de `a6Text` no nombra la rama `stale`.
- **Arreglo:** mover el KDoc de `masterMark` encima de su función y ajustar los otros dos.

**m-4. «Las copias hechas después de ese cambio no se han borrado» (`A6Text.kt:131`) es demasiado absoluta.**
- **Qué pasa:** la rotación (Conservar N, `AutoBackup.kt:1025`) puede haber borrado algunas. Peca por prudencia, pero no es exacta.
- **Propuesta para el Abogado:** «Esta petición no borró las copias hechas después de ese cambio: si cambiaste la contraseña otra vez, puede que alguna se abra con una contraseña anterior. Míralo a mano.».

### Informativos

- **i-1.** El texto `stale` sale en `ReplaceOutcomeDialog` con el color normal, aunque pide «Míralo a mano». El diálogo solo colorea los fallos (`AppNavHost.kt:220`). En el diálogo de la maestra sí sería rojo (`A6Text.kt:150`), pero ahí no sale nunca `stale`.
- **i-2.** Con A8 (el perfil subido entre «Borrar…» y el final), el éxito sale como `stale`. Es verdad, pero confunde. No pasa en el camino normal (§3.1).
- **i-3.** El deshacer de una importación que falla (m-2 de la re-revisión 5) y los resultados que solo traen fallos y se pierden con la muerte del proceso (i-1 de la re-revisión 5) van a `pendientes.md`, como dice el informe.
- **i-4.** `masterMark()` lee `vault`, que no es `@Volatile`, igual que `changeMark()`. Es el patrón de siempre del archivo, y la lectura va después de tomar el cerrojo del motor. No veo ninguna carrera que se pueda alcanzar.

## Textos (veracidad y palabras prohibidas)

- **Texto nuevo:** `a6StaleText` y sus tres variantes (`A6Text.kt:124-133`).
  - Es verdad en todos los casos con marca (§3.2), salvo el matiz de m-4.
  - No tiene ninguna palabra de `ForbiddenWords.kt:5-7`.
  - Está en español llano. Lleva el condicional «si cambiaste la contraseña otra vez» antes de «Míralo a mano», que se lee bien.
- **Los demás textos de A6 no cambian.** El anexo (`docs/superpowers/specs/2026-10-27-seguridad-1.3.0-textos.md:452`) recoge el texto nuevo.

## Revisión experto-seguridad-cripto

- **Modelo de amenaza:** las copias que se abren con una maestra anterior, quizá comprometida, y lo que la app le dice al usuario para que las borre a mano. Una afirmación de borrado que no es verdad para el último cambio deja esas copias al alcance de quien tenga la contraseña anterior.
- **Bien, con evidencia:**
  - **cuándo hablar** se decide en una función pura con un invariante comprobable (§1.3);
  - **ningún dato calla una petición para siempre;**
  - **el diálogo nunca mezcla peticiones** (§3.4);
  - **la marca** cambia con cada cambio de maestra, porque `wrap` siempre pone una sal nueva (`Vault.kt:330-331`, `:504-509`);
  - **el borrado no cambia:** solo ids de la lista propia (`AutoBackup.kt:1021-1037`).
- **La marca:** un SHA-256 con separación de dominio sobre un dato público (la sal). No es un MAC ni hace falta que lo sea, porque solo indica si la bóveda se volvió a cifrar. No revela la contraseña ni la clave, y la copia del sistema está desactivada.
- **Queda I-1:** las peticiones `"1.x"` siguen expuestas a la clase de I-2, y el camino b es nuevo. Es Important por la regla del encargo y la de la skill (confirmado, sin ningún fallo del sistema). Tiene arreglo pequeño o va a `pendientes.md`.
- **Sin cambios en:** la red, los permisos, los registros, las dependencias (SHA-256 de `java.security`) y los formatos. `A6State` solo añade un campo opcional, y `closed` se ignora al leer.

## Revisión Ingeniero Móvil Senior

- **Concurrencia:**
  - `publish` (`AutoBackup.kt:577-597`) y el cierre del resultado (`:399-403`) siguen bajo el `Mutex` del motor, con una sola lectura de `targets()` y `a6()`;
  - `changeTargetFolder` decide bajo el cerrojo de los ajustes (`SettingsStore.kt:111-116`);
  - el orden de los cerrojos no cambia (el del motor y, dentro, el de los ajustes);
  - `answerRequest` se escribe con `_state.update` y la misma comprobación de `requestedAt` (`MasterChanged.kt:86`).
- **Ciclo de vida y persistencia:**
  - S está en `settings.json` y N se recalcula. La marca sale del archivo. El invariante sobrevive a un reinicio, y un resumen no cerrado vuelve en el desbloqueo siguiente;
  - `MasterChanged.State.answerRequest` vive en memoria, como el resto del diálogo, por encima de la actividad, en `AppContainer`.
- **UI:**
  - `MasterChangedDialog` pinta lo que da `a6DialogView` (`SettingsScreen.kt:402-426`), una función pura con su test. No hay estado nuevo en Compose;
  - el texto `stale` en `ReplaceOutcomeDialog` no se colorea (i-1).
- **Rendimiento:** `a6Snapshot` hace un orden y una concatenación sobre 3 carpetas como mucho (`BACKUP_TARGETS_MAX`). Es despreciable, y va fuera del hilo principal, en el `scope` del motor.
- **Tests:** deterministas (`runTest`, `runCurrent`, `cycle`). El exhaustivo es puro. Faltan `"1.x"`, `stale` en el exhaustivo y las ramas de `a6StaleText` (m-2).

## Textos para el Abogado Digital (revisión final)

- **Nuevo (I-2):** «Se terminaron de borrar las copias que la app tenía apuntadas de antes de un cambio de contraseña anterior, en todas las carpetas. Las copias hechas después de ese cambio no se han borrado: si cambiaste la contraseña otra vez, puede que alguna se abra con una contraseña anterior. Míralo a mano.»
  - Con una carpeta: «…, en la carpeta.».
  - Si no terminaron todas: «…, en N de las M carpetas; en las demás puede quedar alguna copia de antes de ese cambio.».
  - **Propuesta de m-4:** «Esta petición no borró las copias hechas después de ese cambio: …».
- **Para la política (tarea 12):** `settings.json`, sin cifrar en la carpeta privada de la app, guarda ahora por cada bóveda:
  - el id aleatorio de la petición;
  - una marca opaca de la sal del archivo, un SHA-256, que no permite saber nada de la contraseña;
  - la foto del último resumen cerrado: los URI de las carpetas, que ya se guardaban, su estado y unos contadores.

  No guarda nombres de archivo nuevos ni datos de la bóveda.
