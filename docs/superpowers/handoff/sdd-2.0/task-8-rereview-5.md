# Repaso de la ronda 5 de la tarea 8 (Clavero 2.0), la última

- **Alcance:** de `66bf6e8` a la cabeza `259048e` (`review-66bf6e8..259048e.diff`). Entran m-1 a m-8 de `task-8-rereview-4.md`, la lista de escrituras tragadas y la tabla de textos de A6 del informe («Ronda 5»).
- **Cómo se hizo:** solo leyendo el código. **No ejecuté nada**: ni gradle ni tests, que las suites las corre el controlador en un clon limpio. Las líneas son las de `259048e`.
- **Lentes:**
  - `experto-seguridad-cripto` (`SKILL.md`): un hallazgo confirmado es Important como mínimo; uno teórico, menor.
  - `desarrollo-movil-senior` y `auditoria-seguridad-movil`.
  - Las reglas del encargo: un texto de A6 que puede afirmar algo que el código no hizo es **Important**, y también lo es un aviso perdido con un uso normal.

## Veredicto

**No aprobada: 2 Important (I-1, regresión de esta ronda; I-2, anterior a ella), 5 Minor y 4 informativos.**

- **I-1 es una regresión de esta ronda**, y su arreglo es pequeño. `A6State.closed` (m-7) silencia para siempre el resumen de una petición cuando una carpeta vuelve a ella después del cierre. Esa carpeta es una que falló, se quitó con la petición pendiente y se volvió a añadir. Su aviso de COPIAS-2 ya no se dice nunca: «quedaba N copia(s) … que la app no tenía apuntada(s) … puede que se abra con una contraseña anterior».
  - No hace falta que falle ninguna escritura. Basta una carpeta que falla, que la re-revisión 3 contó como uso normal (I-2). Por eso no lo califico de teórico.
  - Lo atenúa el snackbar `AUTO_BACKUP_REPLACE_DROPPED`, que sale al quitar la carpeta.
  - En la ronda 4 ese aviso sí salía.
  - Según el Ruling, un Important lleva al dueño, que es quien decide si hay otra ronda. El arreglo de I-1 es una condición, un lector de `pastTargets` y un test.
- **m-2:**
  - el arreglo es correcto en el motor;
  - `replaceNow` no toca ninguna carpeta si el id no quedó guardado;
  - la salida `FAILED` apunta su resultado;
  - los dos tests nuevos fallarían sin el arreglo.

  Queda un resto, teórico y con el mismo origen (que falle esa escritura): el diálogo de la maestra añade a ese fallo el resumen de la petición **anterior** (m-1 de esta re-revisión). Sigo el precedente del Ruling y lo dejo en Minor.
- **I-2 es anterior a esta ronda**: lo vi al seguir m-1, y ya estaba en la ronda 4 y, en su forma de una carpeta, en la 1.x.
  - **Qué pasa:** un cambio de maestra no abre una petición nueva. Si la petición anterior, R0, tenía una carpeta pendiente, se cambia la maestra otra vez sin «Borrar…» y esa carpeta se recupera, se publica «Se hizo una copia nueva en cada carpeta y se borraron las copias … hechas antes del cambio de contraseña…».
  - **Por qué es Important:** el usuario lo lee como dicho del cambio que acaba de hacer, y las copias de la contraseña anterior siguen ahí. No hace falta que falle ninguna escritura.
  - **La decisión es del controlador o del dueño:** por la regla del encargo es Important, pero no la causó esta ronda.
- **El resto está bien hecho:** m-1, m-3, m-4, m-5, m-6 y m-8. No hay regresiones en `a6Text`, en el test exhaustivo (576 combinaciones), en D-3, en N-4 ni en O-5. Los textos son verdaderos y no tienen ninguna palabra prohibida.

## 1. m-2

### 1.1 `onA6` dice si guardó

- `AppContainer.kt:835-852`: `recordA6` devuelve `saved`. Es verdadero solo si la bóveda sigue en la lista y `settings.update` terminó.
- `SettingsStore.write` (`SettingsStore.kt:134-138`) escribe en disco **antes** de cambiar `_settings.value`. Así, un fallo de escritura deja la memoria como estaba, y la segunda comprobación `a6()?.id != request` (`AutoBackup.kt:641`) es coherente con lo que hay en disco.
- Dentro de `update`, `saved = true` se pone en la lambda, antes de escribir. Si la escritura lanza, el `catch` devuelve `false` (`:849-851`). Correcto.
- El `onA6` por defecto, el de memoria, devuelve `true` (`AutoBackup.kt:357`).

### 1.2 `replaceNow` falla antes de tocar ninguna carpeta

- Antes de `:641` solo se hace `_replacing.value = true` y se leen `targets()` y `store.generation` (`AutoBackup.kt:631-639`).
- En `:641` se devuelve `AutoBackupResult("", null, AUTO_BACKUP_FAILED)` sin `attempt`, sin `defer` y sin `onResult`.
- El texto, «No se pudo hacer la copia automática.» (`AutoBackup.kt:133`), es verdad: no se hizo ninguna copia.

### 1.3 La salida `FAILED` que no se apuntaba

- `failedFor` (`AutoBackup.kt:679-683`) apunta un `AutoBackupResult(uri, null, AUTO_BACKUP_FAILED, newRequest = true, request = request)`.
- En `replaceAfter` (`Settings.kt:319`) eso da `replaceRequest = R` y `replaceDone = null`. La carpeta queda en la petición, sin resultado, y nunca se suma una cuenta de R0.
- Lo usan las dos salidas que antes no apuntaban nada (`:652` y `:655`).
- **Sin un «Se volverá a intentar» falso:** `AUTO_BACKUP_FAILED` no promete nada.
  - Si la carpeta tenía una petición anterior pendiente, `replaceAfter` la mantiene (`Settings.kt:320-325`) y sí habrá un reintento, pero el texto no dice lo contrario.
  - La regla R-6 (`:874-878`) solo cambia `AUTO_BACKUP_OLD_NOT_DELETED`, que aquí no sale.
- **`failedAt` no se apunta aquí:** a diferencia de `attempt` (`:885`), así que al abrir la app se reintenta sin esperar. No es un texto y no afecta a A6.

### 1.4 Los tests

- **`aRequestWhoseIdIsNotSavedTouchesNothing`** (`AutoBackupTargetsTest.kt:1422-1439`):
  - deja pasar solo las escrituras con `said` (`a6Writes = { it.said != null }`);
  - compara los archivos de las dos carpetas y **todos** los `VaultSettings` antes y después, y comprueba que no hay resultado publicado;
  - la mutación M5a lo pone en rojo.

  **Límite:** no hay una petición anterior terminada, así que no fija el resto que describo en m-1.
- **`aFolderThatCannotBeOpenedRecordsItsFailure`** (`:1442-1458`):
  - «dos» no se puede abrir;
  - comprueba `replaceRequest == a6.id`, `replaceDone == null`, `lastError == AUTO_BACKUP_FAILED` y el parcial «1 de 2»;
  - la mutación M5b lo pone en rojo.

## 2. Las escrituras de ajustes tragadas en el camino de A6

Busqué todos los `catch` que se tragan una escritura de ajustes en `app/src/main`: `AppContainer.kt:806-818`, `:823-831`, `:835-852` y `:340-360` (el reloj, ajeno a A6), y `VaultStore.kt:1500-1506`. La lista del informe tiene tres huecos:

| Escritura | Qué dice el informe | Lo que veo |
|---|---|---|
| El id de una petición nueva | Arreglado | En el motor, sí. En el diálogo de la maestra queda un resto: m-1 |
| Lo dicho y el cierre al cerrar el diálogo (`AutoBackup.kt:398`) | «Es una repetición» | No siempre. Si después hay **otro cambio de maestra sin «Borrar…»**, el resumen de R0 se vuelve a publicar en el desbloqueo (`:542`). Entonces dice «se borraron las copias … hechas antes del cambio de contraseña» justo después de un cambio más nuevo, y para ese cambio es falso. Con la escritura fallida es teórico (m-1, escenario 2). Sin ningún fallo pasa lo mismo cuando una carpeta pendiente de R0 termina después del cambio nuevo (I-2, anterior a esta ronda) |
| Un reemplazo hecho (`onResult` con `replaced`) | «Se corrige solo» | Se corrige, pero la pasada siguiente cuenta la copia nueva de la pasada perdida como «sin apuntar». No está en `owned`, y `COPY_NAME` coincide (`AutoBackup.kt:1036-1039`). La cuenta sale con una más, y el texto, que va con «puede que», avisa de más. Va en la dirección prudente. Esa copia nunca rota (ya pasaba: `AppContainer.kt:815`). Basta con apuntarlo |
| **Falta:** deshacer el olvido de una importación (`VaultStore.kt:1500-1506`, `prepared.undo()`) | No está. La fila «Olvidar las copias…» habla solo de la escritura de antes, que sí lanza | Una importación que falla y cuyo deshacer también falla deja `a6 = null` y las peticiones y listas olvidadas (`Settings.kt:249-254`). La petición pendiente cae sin `AUTO_BACKUP_REPLACE_DROPPED`, que solo se dice si la importación salió bien (`VaultStore.kt:1473`, `:1496`). Así, «Se hará en cuanto abras la bóveda…», dicho antes, ya no se cumple, y no se avisa. No hay ningún texto nuevo falso. Hacen falta dos fallos seguidos: teórico (m-2) |

El resto de las filas (petición guardada con la bóveda cerrada, fallo de una carpeta, `failedFor`, copia de antes de importar y `copiedMark`) se sostienen, comprobadas en `AutoBackup.kt:686-698`, `:880-886` y `Settings.kt:288-326`.

## 3. m-7 y `A6State.closed`

### 3.1 Lo dicho se guarda al cerrar

- `publish` ya no escribe (`AutoBackup.kt:571-593`). El resultado lleva su `summary` (`:590`).
- `replaceOutcomeShown` (`:390-401`) guarda `said` y `closed` bajo el cerrojo del motor, y solo si la petición sigue siendo la de ahora (`:398`).
- `AppNavHost.kt:220` le pasa `shown`, el que estaba en pantalla.

### 3.2 Se vuelve a publicar tras un reinicio y un desbloqueo

- `runDue(afterUnlock = true)` llama siempre a `publish`, aunque no haya resultados (`AutoBackup.kt:542`).
- La llamada sale de `init` (`:451-453`), con cada paso a `Unlocked`, también el primero de un proceso nuevo.
- `aSummaryNotClosedIsToldAgainAfterARestart` (`AutoBackupTargetsTest.kt:1461-1479`) lo fija, y la mutación M5c lo pone en rojo.

### 3.3 `closed` puede ocultar un aviso que sigue debiéndose: I-1

`closed` se pone con `said.notReplaced == 0`. Pero ese resumen solo cuenta las carpetas de **ahora**, y una carpeta con la petición pendiente puede estar en `pastTargets` (O-5) y volver después. Ver I-1.

Otros casos que miré:
- **Una carpeta nueva de verdad, añadida después:** queda callada, como fija `aSummaryClosedIsNotToldAgain`. Bien.
- **Una carpeta quitada antes del cambio y vuelta a añadir sin petición**, con sus copias de la maestra anterior apuntadas: tampoco se dice nada. En la ronda 4 no había nada que lo publicara, así que no es una regresión. Lo cubre el aviso general del diálogo de la maestra (`PASSWORD_CHANGE_OLD_BACKUPS`) y va al informativo i-2.

### 3.4 Cerrar el diálogo durante un bloqueo

- **La escritura no toca la bóveda:** solo ajustes, bajo el cerrojo del motor y con la comprobación de la petición (`AutoBackup.kt:395-399`). No lee la bóveda ni enseña nada.
- **`ReplaceOutcomeDialog` puede verse con la bóveda bloqueada** (`AppNavHost.kt:219-221`, sin condición de estado). Ya pasaba antes. Su texto solo tiene números de carpetas y de copias y frases fijas, ningún dato de la bóveda. Informativo i-3.
- **Un desbloqueo con el diálogo abierto** vuelve a publicar el mismo resumen, como un objeto nuevo. `Notice` lo sustituye y se enseña una vez más. Es una repetición, no una afirmación falsa.

### 3.5 m-7 solo cubre el resumen

Un fallo se apunta como dicho al intentarlo, con `toldKey` y `replaceTold` (`AutoBackup.kt:880-886`; `Settings.kt:324`), no al cerrarse el diálogo. Si el proceso muere con un resultado que solo trae fallos, ese fallo ya no se vuelve a decir. Ya pasaba en la ronda 4, y lo atenúa que Ajustes enseña el `lastError` de la carpeta. Informativo i-1.

## 4. m-1, m-3, m-4, m-5, m-6 y m-8

- **m-1, bien:**
  - `restored` (`Settings.kt:199-204`) es el único constructor para añadir una carpeta (`:189`) y para cambiar a una (`:219`);
  - el test exhaustivo añade `MOVED_TO_DONE` y `MOVED_TO_WAITING` (`AutoBackupTargetsTest.kt:1326-1334`), y la mutación M5e lo pone en rojo;
  - el nuevo `dropped = hadRequest && from != chosen` (`SettingsScreen.kt:249`) es verdad sobre la carpeta `from`, porque su petición se queda en `pastTargets` con sus listas (`Settings.kt:222`, `:237-246`). Lo malo es que `hadRequest` sale de una instantánea (m-3).
- **m-3, bien:**
  - `partial` exige `replaced > 0`, y `unknown` es «hecho sin ninguna carpeta hecha» (`SettingsScreen.kt:398-402`, `:423`);
  - `a6Text` solo da `""` con `summary` nulo o `replaced == 0` (`A6Text.kt:48`), que son exactamente los casos de `unknown`, así que `:424` nunca enseña un texto vacío. La rama `Failed` (`:427-431`) siempre lleva el mensaje;
  - el test es débil (m-4).
- **m-4, bien:** `PlatformSourcesTest.kt:1060-1069`. `a6Text` en `SettingsScreen.kt` y `AutoBackup.kt`, un solo `outcome.post(` y `ReplaceOutcomeDialog(shown.text ?: shown.error.orEmpty()` (`AppNavHost.kt:220`).
- **m-5, bien:** los KDoc de `replaceOutcome` (`AutoBackup.kt:377-383`) y `newA6RequestId` (`:205`), y el del test (`AutoBackupTargetsTest.kt:926-930`). Las líneas 448 y 449 del anexo ya no contradicen la 450. Quedan otros KDoc desfasados (m-5 de esta re-revisión).
- **m-6, bien:**
  - **`retell`** (`AutoBackup.kt:599-602`) es `scope.launch { lock.withLock { publish(...) } }`, y `publish` ya no escribe ajustes;
  - **el orden de los cerrojos** sigue siendo el del motor y, dentro, el de los ajustes, también en `replaceOutcomeShown` (`:396-398` → `recordA6` → `SettingsStore.update`). No hay ningún camino que tome el de los ajustes y luego el del motor;
  - **las carreras que miré:**
    - `publish` lee `targets()` cuando corre, así que ve la carpeta ya quitada o cambiada (la escritura terminó antes, `SettingsScreen.kt:237` y `:350`);
    - dos `retell` seguidos publican dos veces lo mismo y `Notice` se queda con el último;
    - una importación entre la escritura y el `retell` deja `a6 = null` y ningún `replaceDone`, así que no se dice nada;
    - `releaseIfUnused` ya no espera a una copia en curso. Es lo que pasaba antes de la ronda 4 y siempre sin petición: una copia cortada da un resultado para una carpeta que ya no está, que `withTarget` ignora.
  - **El test I-2** lleva ahora `runCurrent()` (`AutoBackupTargetsTest.kt:1266`).
- **m-8, bien:** «En la otra carpeta / En las otras K carpetas puede quedar alguna copia de antes del cambio de contraseña: míralo a mano.» (`A6Text.kt:69-73`). Es verdad en todos los casos de una carpeta no hecha:
  - añadida después;
  - que falló a medias;
  - que volvió con una petición anterior (i-2 de la ronda 4);
  - cuyo `replaced` no se apuntó.

## 5. Regresiones

- **`a6Text`:** solo cambia el texto de m-8. Las ramas son las de la ronda 4 (`A6Text.kt:45-60`).
- **Test exhaustivo:** 8 estados, 8² + 8³ = 576 combinaciones (`AutoBackupTargetsTest.kt:1357`). Mantiene las aserciones de «cada carpeta», «1 de 1», la suma de `R0` y el texto vacío sin ninguna hecha (`:1341-1353`).
- **D-3:** `restored` trae `copiedMark` también al cambiar de carpeta, pero sin `lastAt`, así que la primera copia nunca se salta (`AutoBackup.kt:536`, `t.lastAt != null`).
- **N-4:** `publish` mantiene `store.generation == since` (`:575`).
  - `replaceOutcomeShown` no mira la generación, pero sí la petición. Tras una importación, `a6 = null` da `"1.x"`, que no es ningún UUID.
  - Solo un resumen de una petición de la 1.4.3 (`"1.x"`) cerrado tras una importación escribiría `A6State("1.x", said, closed)`. Es inofensivo, porque la importación olvidó todos los `replaceDone` (informativo i-4).
- **O-5:** la petición sigue viajando con `pastTargets`, y ahora también al cambiar de carpeta (m-1).

## Issues

### Critical

Ninguno.

### Important

**I-1. `A6State.closed` silencia para siempre el aviso de una carpeta que vuelve a la petición (regresión de la ronda 5).**
- **Dónde:** `AutoBackup.kt:398` (`closed = said.notReplaced == 0`, contando solo las carpetas de ahora) y `AutoBackup.kt:575` (`state?.closed != true`). La carpeta vuelve por `Settings.kt:199-204` (`restored`, con `replacePending`, `replaceRequest` y `replaceTold`), al añadirla (`:189`) o al cambiar de carpeta a ella (`:219`, m-1).
- **Escenario (sin ningún fallo de escritura):**
  1. Hay dos carpetas, «uno» y «dos». Se cambia la maestra y se pulsa «Borrar…»: las dos quedan en R.
  2. Al desbloquear, «uno» reemplaza y «dos» falla (sin permiso, o el proveedor sin conexión). Sale el parcial «1 de 2», que el usuario cierra (`said` = 1/2).
  3. El usuario quita «dos». `removeTarget` da `hadRequest` y `keptPast` guarda su petición con sus listas (`Settings.kt:237-246`). `retell` publica `AUTO_BACKUP_REPLACED` para una carpeta (1/1), con `AUTO_BACKUP_REPLACE_DROPPED` en el snackbar. El usuario lo cierra y queda `closed = true`.
  4. Más tarde vuelve a añadir «dos» (o «Cambiar de carpeta» hacia ella). Vuelve con `replacePending` y R, y `autoBackup.request` la intenta.
  5. **Variante a:** reemplaza y encuentra N copias sin apuntar, de antes de la 1.2.1 o de otra bóveda. `replaceDone(R, …, N)` se guarda, pero `publish` da `summary = null` por `closed` y no hay fallo que decir (`:575-577`). **El aviso de COPIAS-2 de esas N copias no se dice nunca.** En la ronda 4 salía «en cada carpeta» con la cuenta.
  6. **Variante b:** vuelve a fallar con el mismo fallo, ya dicho (`replaceTold` también vuelve). Ahora hay una carpeta con copias de la maestra anterior apuntadas y el parcial «En la otra carpeta puede quedar…» no sale nunca. En la ronda 4 salía. Lo atenúan el snackbar DROPPED del paso 3 y el `lastError` de esa carpeta en Ajustes.
- **Por qué es Important:** es un aviso perdido al que se llega sin ningún fallo del sistema, con una carpeta que falla, que es el uso normal de I-2 de la re-revisión 3. Que solo pierda el aviso de las copias sin apuntar en la variante a no lo convierte en teórico.
- **Arreglo (lo que tiene que cumplir):** `closed` no se pone mientras alguna carpeta, de ahora o de `pastTargets`, siga pendiente para esa petición, y se quita cuando una carpeta vuelve a ella.
  - **La forma recomendada:** en `:398`, `closed = said.notReplaced == 0 && past().none { it.replacePending && it.requestId == said.request }`. Necesita un lector **nuevo** de `pastTargets` en el constructor de `AutoBackup` (`AutoBackup.kt:330-353` solo tiene `targets`).
  - **Además:** `restored` (`Settings.kt:199-204`) pone `a6 = a6?.copy(closed = false)` cuando trae una petición pendiente con `requestId == a6.id`.
  - **La forma corta no basta:** con solo `state?.closed != true || notices.isNotEmpty()` en `:575` el resumen se publica, pero `closed` sigue a `true`. Si el proceso muere antes de cerrarlo, el desbloqueo no lo vuelve a decir, porque `:542` no lleva `notices`. Es m-7 otra vez.
  - Con la forma recomendada, `aSummaryClosedIsNotToldAgain` sigue en verde: la carpeta nueva no tiene petición.
- **Test:** el escenario de arriba.
  - Variante a: después del cierre, «dos» vuelve de `pastTargets` con una copia sin apuntar, y sale «en cada carpeta» con la cuenta, también tras reiniciar sin cerrarlo.
  - Variante b: con un fallo ya dicho, sale una vez el parcial.

**I-2. Tras un segundo cambio de maestra sin «Borrar…», el éxito de la petición anterior se dice como si fuera del cambio nuevo (anterior a esta ronda; sin ningún fallo de escritura).**
- **Dónde:**
  - un cambio de maestra no toca `a6` (`AppContainer.kt:766-769`; solo escriben `a6` `AutoBackup.kt:398` y `:641`, `SettingsStore.kt:88`, `:128` y `Settings.kt:253`);
  - la pasada usa la petición de la carpeta (`AutoBackup.kt:537`, `:490`: `request = t.requestId`);
  - `publish` cuenta la petición de `a6` (`:575-577`).
- **Escenario:**
  1. Con «uno» y «dos», se cambia la maestra (el cambio 1) y «Borrar…» da R0. «uno» reemplaza y «dos» falla: queda pendiente con sus copias del cambio 1 (`replaceTargets`).
  2. Pasan días y se hacen copias con la maestra 1.
  3. Se cambia la maestra otra vez (el cambio 2) y el usuario cierra el diálogo **sin** pulsar «Borrar…». `a6` sigue siendo R0.
  4. En un desbloqueo, «dos» se recupera y `runDue` termina R0: borra sus copias de antes del cambio 1.
  5. `publish` ve R0 en 2/2 con una clave nueva y publica, bajo el título «Copias automáticas» (`ToolTexts.kt:518`): «Se hizo una copia nueva en cada carpeta y se borraron las copias automáticas y las de antes de importar hechas antes del cambio de contraseña: las que la app tenía apuntadas en cada carpeta.»
  6. Las copias de la maestra 1 siguen en las dos carpetas y se abren con la contraseña anterior a la de ahora. El usuario acaba de hacer el cambio 2 y lo lee como dicho de ese cambio.
- **Por qué es Important:**
  - El texto es verdad para el cambio 1 y falso para el cambio 2, que es el único que el usuario tiene en la cabeza. Puede dejar de borrar a mano unas copias que lo necesitan.
  - No hace falta que falle ninguna escritura: una carpeta que falla es uso normal (I-2 de la re-revisión 3), y no pulsar «Borrar…» es una opción del diálogo.
- **No lo causó esta ronda:**
  - en la ronda 4, `tell` publicaba lo mismo al final de esa pasada;
  - en la 1.x, con una carpeta, salía `AUTO_BACKUP_REPLACED` igual.

  Lo marco Important por la regla del encargo, y la decisión de arreglarlo ahora o apuntarlo es del controlador o del dueño.
- **Arreglo (lo que tiene que cumplir):** un resumen de una petición anterior al último cambio de maestra no se dice como éxito, pero sus copias sin apuntar sí se siguen diciendo. Por ejemplo, «Al terminar de borrar las copias de un cambio de contraseña anterior, quedaba…». Si no, sería un aviso perdido de la misma clase que I-1.
  - **Una forma:** guardar la época del cambio de maestra en `A6State`. `onMasterChanged` la escribiría **bajo el cerrojo del motor**, como una petición nueva sin carpetas, antes de cualquier `replaceNow`. Hoy `onMasterChanged` no es `suspend` (`AppContainer.kt:766`): si escribe fuera del cerrojo puede llegar después del `A6State(R)` de `:641`, y entonces las carpetas guardan R y la bóveda otro id, y el éxito no se dice nunca.
  - **La alternativa:** que `a6Text` nombre de qué cambio habla.
- **Test:** el escenario de arriba. Tras el cambio 2 sin «Borrar…», terminar R0 no publica «en cada carpeta», y una copia sin apuntar de R0 se sigue diciendo.

### Minor

**m-1. Un resumen de la petición anterior puede salir como si fuera de un cambio de maestra más nuevo (teórico; resto de m-2).**
- **Dónde:** `SettingsScreen.kt:427-431` (`a6Text(listOf(status.message), summary)`) con `summary = vaultSettings.a6Summary` (`AppNavHost.kt:257`), que es el de `a6`. También la nueva publicación en el desbloqueo (`AutoBackup.kt:542`, `:575-577`).
- **Escenario 1:**
  1. Una petición R0 terminó en todas las carpetas: cada una conserva `replaceDone(R0)`, y un cambio de maestra no lo borra.
  2. La maestra cambia otra vez y se pulsa «Borrar…».
  3. La escritura de `A6State(R)` falla y `replaceNow` devuelve `AUTO_BACKUP_FAILED` (`:641`), así que `a6` sigue siendo R0.
  4. El diálogo enseña «No se pudo hacer la copia automática.» y debajo «Se hizo una copia nueva en cada carpeta y se borraron las copias … hechas antes del cambio de contraseña…». Es **falso** para este cambio.
- **Escenario 2:** el resumen de R0 ya terminado no quedó cerrado, porque falló la escritura al cerrarlo (`:398`). Hay un cambio de maestra nuevo sin «Borrar…». El desbloqueo siguiente (`:542`) vuelve a publicar R0 con el mismo texto. Es la forma de I-2 que sale con una escritura fallida, sin ninguna carpeta pendiente.
- **Por qué es menor:** los dos necesitan que falle una escritura de ajustes. Es el mismo origen que m-2, que el Ruling dejó en Minor. Pero por la regla del encargo es una afirmación falsa, así que el arreglo se recomienda igual.
- **Arreglo:**
  - El de I-2 cubre el escenario 2.
  - Para el 1, que el diálogo use el resumen solo si es de la petición que respondió: `MasterChanged.State` guardaría el `request` del resultado de `replaceOldCopies`, y `:428` usaría `summary.takeIf { it.request == state.request }`. No depende de ninguna escritura.
- **Test:** R0 terminada, otro cambio de maestra y la escritura del id que falla: el texto del diálogo es solo `AUTO_BACKUP_FAILED`.

**m-2. A la lista de escrituras tragadas le falta una y dos filas se quedan cortas.**
- **Falta:** `VaultStore.kt:1500-1506` (deshacer el olvido de una importación que no se guardó). Si falla, la petición pendiente cae sin `AUTO_BACKUP_REPLACE_DROPPED` (§2). Ningún texto nuevo es falso, pero el «Se hará en cuanto abras la bóveda…» de antes deja de cumplirse sin aviso. Es teórico, con dos fallos seguidos.
- **Filas cortas:**
  - «Lo dicho y el cierre…» no siempre es una repetición: ver m-1, escenario 2;
  - «Un reemplazo hecho… se corrige solo» cuenta la copia nueva de esa pasada como «sin apuntar» (`AutoBackup.kt:1036-1039`), y eso avisa de más.
- **Arreglo:** añadirlas a la lista del informe. Para el deshacer, devolver `droppedRequest` también en el camino del fallo, para que la pantalla de importar diga `AUTO_BACKUP_REPLACE_DROPPED`, o apuntarlo en `pendientes.md`.

**m-3. Al cambiar de carpeta, `hadRequest` sale de una instantánea, no de la escritura (anterior a esta ronda; la línea se tocó en ella).**
- **Dónde:** `SettingsScreen.kt:213` (`now`), `:233` y `:249`. Quitar una carpeta lo decide en la escritura (`SettingsStore.kt:98-103`, T11-c); cambiarla, no.
- **Escenario:**
  1. Una pasada de reemplazo de `from`, que ya estaba en curso hacia un proveedor lento mientras el selector estaba abierto, termina y apunta `replaced` entre la lectura de `now` y `updateVault` (`:237`), es decir, durante `takeFolder` (`:229`).
  2. El snackbar dice «Las copias anteriores que ibas a borrar no se borraron: siguen en su carpeta.».
  3. Es falso: se acaban de borrar.
- **Por qué es menor:** la ventana es de milisegundos, y el mismo fallo estaba en la ronda 4 con la condición de antes.
- **Arreglo:** que el almacén decida `hadRequest` bajo su cerrojo, como `removeTarget`. Por ejemplo, `changeTargetFolder(id, from, to): Boolean`.

**m-4. El test de m-3 apenas prueba nada.**
- **Dónde:** `AutoBackupTargetsTest.kt:1349-1350`.
  - `a6Text(listOf(AUTO_BACKUP_WRITE_FAILED), summary).isNotEmpty()` es verdad por construcción (`A6Text.kt:47`).
  - `text.isNotBlank()` con una carpeta hecha repite `!anyDone == text.isEmpty()` (`:1348`).
- **Qué falta:** la rama `unknown` del diálogo (`SettingsScreen.kt:400`, `:423`) no tiene ningún test. La mutación M5f falla por la línea `:1348`, no por la nueva.
- **Arreglo:** sacar `partial`, `unknown` y `canReplace` a una función pura y probarla con `Done` y `replaced == 0`.

**m-5. Documentación que sigue desfasada.**
- **`Settings.kt:227`:** el KDoc de `withTargetRemoved` dice «its A6 request goes», pero desde O-5 se queda en `pastTargets`.
- **`Settings.kt:104-109`:** el KDoc de `pastTargets` dice «Only [uri] and those two lists are kept», pero se guardan también `keep`, `schedule`, la petición, `replaceDone` y `copiedMark`.
- **`AutoBackup.kt:565-570`:** el KDoc de `publish` dice «since it was last told ([A6State.said])» y no nombra `closed` ni que `said` es lo cerrado.
- **`AutoBackup.kt:614-627`:** el KDoc de `replaceOldCopies` no nombra la salida del id sin guardar.
- **Arreglo:** ponerlos al día.

### Informativos

- **i-1.** m-7 solo cubre el resumen. Un resultado que solo trae fallos y se pierde con la muerte del proceso no se vuelve a decir, porque los fallos se apuntan al intentarlo (`AutoBackup.kt:880-886`). Ya pasaba antes. Lo atenúa el `lastError` de la carpeta en Ajustes. A `pendientes.md`.
- **i-2.** Una carpeta quitada **antes** del cambio, con copias de la maestra anterior, y vuelta a añadir sin petición no da ningún aviso, ni antes ni ahora. Lo cubre `PASSWORD_CHANGE_OLD_BACKUPS`.
- **i-3.** `ReplaceOutcomeDialog` puede verse sobre la pantalla de la bóveda bloqueada (`AppNavHost.kt:219-221`). Ya pasaba. Solo tiene números y frases fijas, ningún dato de la bóveda.
- **i-4.** Si se cierra un resumen de una petición de la 1.4.3 justo después de una importación, se escribe `A6State("1.x", …)`, y es inofensivo (§5).

## Textos (veracidad y palabras prohibidas)

- **Textos nuevos:** solo los de m-8 (`A6Text.kt:70`, `:72`). Son verdaderos en todos los casos (§4), en español llano y sin ninguna palabra de `ForbiddenWords.kt:5-7`.
- **`AUTO_BACKUP_FAILED`** (`AutoBackup.kt:133`), en las salidas nuevas (`:641`, `:679-683`), es verdad: no se hizo ninguna copia. Lo único falso es lo que se le añade en m-1.
- **El anexo** (`docs/superpowers/specs/2026-10-27-seguridad-1.3.0-textos.md:448-450`) es coherente con el código.

## Revisión experto-seguridad-cripto

- **Modelo de amenaza:** el de siempre.
  - El activo son las copias que se abren con una maestra anterior, quizá comprometida.
  - El usuario decide si borra a mano según lo que le dice la app.
  - Un texto que dice «se borraron» sin haberse borrado, o un aviso que no llega, deja esas copias accesibles a quien tenga la contraseña anterior.
- **El borrado no cambia:** solo ids de la lista propia (`AutoBackup.kt:1007-1023`), nunca por nombre. Lo sin apuntar se cuenta y no se borra (`:1036-1039`).
- **m-2 está cerrado en el motor:** sin el id guardado, no hay ninguna petición (`AutoBackup.kt:641`), y toda carpeta intentada apunta su resultado con el id (`:679-683`). Así se cierra la grieta «se suma una cuenta de R0» del escenario 2 de la re-revisión 4.
- **Lo que queda:**
  - **I-1:** aviso de COPIAS-2 perdido con un uso normal; **Important**, por la regla de la skill para lo confirmado;
  - **I-2:** el éxito de una petición anterior dicho tras un cambio de maestra más nuevo; **Important** por la regla del encargo, aunque anterior a esta ronda (ya pasaba en la 1.x);
  - **m-1:** afirmación falsa con una escritura fallida, teórica; Minor, como el Ruling hizo con m-2.
- **Datos en `settings.json` (sin cifrar):** se añade `A6State.closed`, un booleano (`BackupSchedule.kt:113`). No hay nombres ni rutas nuevos, y no se revela nada de la bóveda. Lo que ya decía la re-revisión 4 sobre la política (tarea 12) sigue igual, con este campo de más.
- **Sin cambios en:** la red, los permisos, los registros, las dependencias, la criptografía y los formatos (`A6State` tiene un valor por defecto, así que se siguen leyendo los ajustes de la ronda 4).

## Revisión Ingeniero Móvil Senior

- **Concurrencia:**
  - `retell` y `replaceOutcomeShown` lanzan en el `scope` del motor, bajo su `Mutex` (`AutoBackup.kt:395-400`, `:599-602`);
  - el orden de los cerrojos sigue siendo el del motor y, dentro, el de los ajustes;
  - `Mutex` es justo (FIFO), así que un cierre del diálogo encolado detrás de una copia lenta se escribe antes de un «Borrar…» pedido después, y la comprobación de `:398` cubre el orden contrario;
  - Ajustes ya no espera a una copia en curso.
- **Ciclo de vida:**
  - el resultado vive en `AutoBackup` (`Notice`), por encima de la actividad;
  - lo dicho se guarda al cerrar, y un desbloqueo después de una muerte del proceso lo vuelve a publicar (`:451-453`, `:542`).
  - Ver I-1 para `closed`, i-1 para los fallos y m-3 para la instantánea de `hadRequest` al volver del selector, cuando `MainActivity.onStart` lanza `launchDue` (`AppContainer.kt:473-476`).
- **Persistencia:**
  - `A6State.closed = false` por defecto;
  - `recordA6` no cambia la memoria si falla el disco (`SettingsStore.kt:134-138`);
  - el deshacer de una importación sigue tragándose su fallo (m-2).
- **UI:** el diálogo de la maestra ya no enseña nunca un texto rojo vacío (`SettingsScreen.kt:398-402`, `:423-431`). Su decisión no tiene test (m-4).
- **Tests:** son deterministas (`runTest`, `runCurrent`, `cycle`). Los de m-2 y m-7 fallarían sin el arreglo. Al exhaustivo le faltan la dimensión `closed` y las carpetas que vuelven después del cierre (I-1).

## Textos para el Abogado Digital (revisión final)

- **Cambian (m-8):**
  - «En la otra carpeta puede quedar alguna copia de antes del cambio de contraseña: míralo a mano.»;
  - «En las otras K carpetas puede quedar alguna copia de antes del cambio de contraseña: míralo a mano.»

  Sustituyen a «…no se han borrado: puede que alguna se abra con una contraseña anterior. Míralo a mano.». La parte «puede que se abra con una contraseña anterior» ya no está en esta frase, y el Abogado puede valorar si la quiere. El encabezado del parcial («Se borraron … en N de las M carpetas») y `PASSWORD_CHANGE_OLD_BACKUPS` lo siguen diciendo.
- **Salen en caminos nuevos:** `AUTO_BACKUP_FAILED` («No se pudo hacer la copia automática.»), tras un «Borrar…» cuyo id no se pudo guardar y para una carpeta que no se pudo abrir. El texto no es nuevo.
- **Para la política (tarea 12):** los ajustes guardan también si el usuario cerró el resumen de la petición de borrar las copias anteriores (`closed`), además del id, los contadores y lo último dicho.
- **El anexo** (`…-textos.md:448-450`) está al día con estos textos.
