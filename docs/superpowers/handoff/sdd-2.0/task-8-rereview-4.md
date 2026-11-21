# Repaso de la ronda 4 de la tarea 8 (Clavero 2.0): el arreglo de estructura de los textos de A6

- **Alcance:** de `6a1ddaa` a la cabeza `b650781`. Entran `vault/A6Text.kt` (nuevo), el resultado por destino y por petición (`replaceRequest`, `replaceDone`, `VaultSettings.a6`), `publish`/`retell`, el diálogo de la maestra, m-2, m-4, m-5, i-3, los tests nuevos y el anexo de textos.
- **Cómo se hizo:** solo leyendo el código. **No ejecuté nada** (ni gradle ni tests; las suites las ejecuta el controlador en un clon limpio). Las líneas son las de `b650781`; el árbol estaba limpio.
- **Lentes:** `experto-seguridad-cripto` (`SKILL.md`: lo confirmado es Important como mínimo; lo teórico, menor); `desarrollo-movil-senior` y `auditoria-seguridad-movil`. Regla del encargo: un texto de A6 que puede afirmar algo que el código no hizo es **Important**.

## Veredicto

**Aprobada, con menores: 0 Critical, 0 Important, 8 Minor y 4 informativos.**

- **La clase de I-1 queda cerrada por construcción.** «…en cada carpeta» depende de un dato guardado por carpeta y por id de petición (`replaceDone.request == a6.id`), no de una condición derivada como «ninguna pendiente». Volví a trazar los caminos A y B de I-1 y el escenario de I-2 sobre el código nuevo: en todos el texto es verdad.
- **Una sola función redacta el éxito y el resumen**, `a6Text` (`A6Text.kt:45-60`). La usan el único `outcome.post` (`AutoBackup.kt:566-574`) y las tres ramas del diálogo de la maestra (`SettingsScreen.kt:419-426`).
  - `replacedText`, `replaceOutcomeText`, `autoBackupUncheckedText` y `answerUnlisted` han desaparecido de `app/src/main`.
  - Fuera de `a6Text` solo quedan dos textos fijos de A6, y los comprobé. `AUTO_BACKUP_REPLACE_DROPPED` (`SettingsScreen.kt:248`, `:353`; `VaultBackup.kt:181`) sale cuando una carpeta o una importación deja caer una petición pendiente: es verdad, esas copias apuntadas siguen ahí. `AUTO_BACKUP_REPLACE_WAITS` (`SettingsScreen.kt:421`; `AutoBackup.kt:665`) solo sale si la petición quedó guardada.
- **No encontré ningún texto de A6 falso al que se llegue con un uso normal.**
- **Decisión para el controlador (m-2):** hay un camino en el que se sumaría una cuenta de una petición anterior y saldría «Se borraron … en 1 de las 2 carpetas» falso. Necesita tres cosas a la vez:
  - un fallo transitorio al guardar el id nuevo (`AppContainer.kt:835-843`, que se traga el fallo);
  - que las escrituras de después sí funcionen;
  - que un destino salga por el `FAILED` que no se apunta (`AutoBackup.kt:631`) con la bóveda **no** bloqueada.

  Leído en `b650781`, ese último caso solo se da si `exportBackup` devuelve `false` (`VaultStore.kt:1338`, `:1341`: sin bóveda, o bloqueada) **y** el estado ya no es `Locked` cuando se mira justo después (`AutoBackup.kt:631`). Eso es: la bóveda borrada (y entonces sus ajustes se descartan, `AppContainer.kt:811`), o un bloqueo seguido de un desbloqueo completo en los microsegundos entre `fill` (`:925-930`) y esa comprobación, cuando el desbloqueo deriva Argon2id durante segundos. `open` no devuelve `null` en producción: lanza (`SeguridadApp.kt:28`; `SafBackupTarget.kt:125-128`).

  Lo califico de **teórico**, y por tanto **Minor** según la regla de la skill. Pero lo que dice es justo lo que el encargo llama Important («puede afirmar algo que el código no hizo»), así que **la elección entre las dos reglas es del controlador**, no mía. El arreglo es pequeño (m-2) y lo recomiendo en cualquier caso. Si el controlador lo toma como Important, el Ruling manda la ronda 5 al plan B; con el arreglo de m-2, la grieta se cierra sin cambiar de estructura.
- **«Otro orden»:** la cadena del implementador se sostiene leyendo el código, y la refuerzo con un eslabón más (ver §3.5).

## 1. La función pura `a6Text`

### 1.1 Entradas

`a6Text(failures, summary)` (`A6Text.kt:45`). `summary` es `a6Summary(targets, a6)` (`A6Text.kt:30-34`):
- `request` = `a6?.id ?: "1.x"` (`:24`);
- `folders` = todos los destinos de ahora;
- `replaced` = los destinos con `replaceDone?.request == request` (`:32`);
- `deleted` y `unlisted` = sumas **solo de esos** (`:33`).

### 1.2 Ramas y por qué cada texto es verdad

| Rama | Condición | Texto | Por qué es verdad |
|---|---|---|---|
| a | `failures` no vacía | los fallos, uno por línea, delante (`:47`) | Son los `toldKey` de la pasada (`AutoBackup.kt:556`): cada uno lo dio de verdad `attempt` (`:835-850`) |
| b | `summary == null` o `replaced == 0` | nada más (`:48`) | No afirma nada |
| c | `notReplaced == 0` y `folders == 1` | `AUTO_BACKUP_REPLACED` («…en esta carpeta») más `autoBackupUnlistedText(u, false)` si `u > 0` (`:49-52`) | La única carpeta tiene `replaceDone` de esta petición. `replaceDone` solo lo pone `replaceAfter` con `r.replaced` (`Settings.kt:309-311`). `r.replaced` = `replaceAll && rotation.replaceDone && stillAsked` (`AutoBackup.kt:817-825`): se escribió la copia y se borraron todos sus destinos |
| d | `notReplaced == 0` y `folders > 1` | `AUTO_BACKUP_REPLACED_SEVERAL` («…en cada carpeta») más `autoBackupUnlistedText(u, true)` (`:49-52`) | **Todas** las carpetas de ahora tienen un `replaceDone` de **esta** petición. Que una carpeta tenga ese `replaceDone` y no haya borrado sus copias apuntadas de antes del cambio exigiría el «otro orden» (§3.5), al que no se puede llegar |
| e | `replaced > 0` y `notReplaced > 0` | `a6PartialText(r, n)` y, si `u > 0`, `a6UnlistedInDoneText(u, r)` (`:53-56`) | «en r de las n carpetas» es la cuenta exacta. «En la otra / las otras K no se han borrado» es verdad o peca por prudencia (m-8, i-2). N ≥ 2 por construcción, así que nunca sale «1 de 1» |

### 1.3 Las tres propiedades pedidas

- **«En cada carpeta» solo si todas las carpetas de ahora reemplazaron en ESTA petición:**
  - es la rama d, `notReplaced == 0` con `replaced` filtrado por id (`A6Text.kt:32`, `:49`);
  - el id de la petición es nuevo en cada «Borrar…» (`AutoBackup.kt:619-620`);
  - un destino recibe ese id por `waitForUnlock` (`:654-663`, `Settings.kt:287-290`), por un `attempt` con la bóveda abierta (`:630`, `Settings.kt:311` o `:314`), o por `runDue`/`runChanged` con `t.requestId` (`:475`, `:522`).
- **Las cuentas son la suma guardada para este id:**
  - en el texto, `summary.unlisted` (`A6Text.kt:52`, `:55`);
  - en el resultado, `unlistedLeft = summary?.unlisted` (`AutoBackup.kt:570`).

  Las dos salen de la misma lectura de `targets()` (`:558`), dentro del cerrojo.
- **Una cuenta de otra petición nunca se suma:**
  - el filtro `takeIf { it.request == request }` (`A6Text.kt:32`);
  - una petición nueva borra el `replaceDone` del destino (`Settings.kt:289`; `:314` con la bóveda abierta);
  - la importación y una bóveda nueva lo olvidan todo (`Settings.kt:246-248`, `SettingsStore.kt:88`).

  Única grieta, teórica: m-2.

### 1.4 Los llamantes

- **`publish`** (`AutoBackup.kt:555-576`). Es el único `outcome.post` del archivo. Lo llaman `tell` (`:544-547`, al final de `runChanged`, `:480`, y de `runDue`, `:526`) y `retell` (`:582`). El texto queda fijado en `AutoBackupResult.text` (`:572`), y `AppNavHost.kt:220` lo enseña tal cual.
- **El diálogo de la maestra** (`SettingsScreen.kt:419`, `:420`, `:423`), con `vaultSettings.a6Summary` (`AppNavHost.kt:257`; `Settings.kt:167`).
- **`replaceNow` no redacta:** devuelve el resultado en bruto y el diálogo lee el resumen guardado.
- `grep` de `AUTO_BACKUP_REPLACED`, `a6Text(`, `autoBackupUnlistedText(`, `a6PartialText(` y `a6UnlistedInDoneText(` en `app/src/main` solo da `A6Text.kt`, `SettingsScreen.kt:419-423`, `AutoBackup.kt:572` y las definiciones o la lista de `ToolTexts.kt:776-777`.

## 2. El test exhaustivo (`AutoBackupTargetsTest.kt:1261-1334`)

**Qué cubre:**
- 2 y 3 carpetas con 6 estados cada una: 36 + 216 = 252 combinaciones (`:1333`).
- Los estados que pedía el encargo:
  - reemplazó: `REPLACED`;
  - falló: `FAILED`;
  - sin petición: `ADDED_AFTER`;
  - añadida después: `ADDED_AFTER`;
  - quitada y vuelta a añadir: `READDED_WAITING` y `READDED_DONE`;
  - quitada antes con un resultado de otra petición: `REMOVED_BEFORE`, con `R0` y `unlistedLeft = 5`, que no debe sumarse.
- Las transiciones son las reales de los ajustes: `withTargetAdded`, `withTargetRemoved` y `withAutoBackupResult`, con resultados de la forma que dan `attempt` y `waitForUnlock`.

**¿Fallaría si saliera «en cada carpeta» con una carpeta que no reemplazó?** Sí.
- `assertEquals(label, all2, "cada carpeta" in text)` (`:1325`) se comprueba en todas las combinaciones con al menos una hecha y alguna que no.
- `:1327` exige el texto vacío cuando ninguna reemplazó.
- `:1323` y `:1329` fijan que `R0` no se suma (el `5` de `REMOVED_BEFORE`).
- Ma, Md y Mf del informe lo confirman con salidas de ejemplo que encajan con estas aserciones.

**Límites:** ver i-4.
- Es un test de ajustes y texto con resultados sintéticos: no prueba que `attempt` solo dé `replaced` tras borrar (eso lo fijan los tests de CR-1, que siguen).
- No tiene el estado «falló en un "Borrar…" con la bóveda abierta», sin petición guardada (la rama `joined`, `Settings.kt:314`). Lo cubre el test del camino B (`:1195-1222`).
- No tiene «Cambiar de carpeta» a una carpeta de `pastTargets` (m-1).

## 3. Caminos y puntos abiertos

### 3.1 Camino A de I-1 (quitada antes del cambio y vuelta a añadir), sobre el código nuevo

1. Se quita «uno»: `keptPast` lo guarda sin petición (`Settings.kt:232-240`).
2. «Borrar…» con la bóveda cerrada: `onA6(A6State(R))` (`AutoBackup.kt:620`). Solo «dos» recibe `replaceRequest = R` (`:633`, `:654-663`; `Settings.kt:287-290`).
3. «dos» falla. `publish` dice el fallo y nada más: `replaced == 0`, así que `summary` es null (`:558`).
4. Se vuelve a añadir «uno»: `replaceRequest = null`, `replaceDone = null` (`Settings.kt:194-196`), y cuenta como `"1.x"`, distinto de R.
5. «dos» reemplaza: `replaceDone(R)`. El resumen es 1 de 2, así que sale el parcial: «…en 1 de las 2 carpetas. En la otra carpeta no se han borrado…». **Es verdad.**

Test: `:1160-1189`.

### 3.2 Camino B de I-1 (un fallo y un bloqueo dentro de `replaceNow`)

1. «uno», con la bóveda abierta: `attempt` lanza `WRITE_FAILED` con `newRequest = true` y `request = R` (`AutoBackup.kt:630`, `:837-838`).
2. `replaceAfter`: `joined` pone `replaceRequest = R` sin `replaceDone`. Como no tiene `replacePending`, se devuelve tal cual (`Settings.kt:314-315`).
3. «dos», ya bloqueada: `waitForUnlock(R)`.
4. Al desbloquear, «dos» reemplaza con R: 1 de 2, el parcial. **Es verdad.**
5. Si el diálogo de la maestra siguiera abierto, mostraría `Failed(WRITE_FAILED)` (`replaceStatus`, `:1072-1075`) y `a6Text([fallo], 1 de 2)`, y sería verdad. Según §3.5, no sigue abierto durante el desbloqueo. Además, el camino B entero (un «Borrar…» con la bóveda abierta) solo se da si la bóveda se abrió antes de que el diálogo llegara a abrirse. Lo cubre el test igualmente.
6. La variante «a comprobar» de la re-revisión 3 (`:631`, un `FAILED` que no pasa por `onResult`) **ya no suma una cuenta vieja**: el destino conserva un `replaceDone` de otro id y el filtro lo descarta. Salvo m-2.

Test: `:1195-1222`.

### 3.3 I-2 (una carpeta que falla siempre)

- **Pasada 1:** dos fallos distintos. Se dicen y no se guarda `said` (`summary` es null).
- **Pasada 2:** el fallo de «uno» ya se dijo, así que no lleva `toldKey`. «dos» reemplaza. `told` está vacío, pero el resumen es nuevo (`key ≠ said`, `:559`), así que sale el parcial con «En la carpeta en que se borraron quedaba 1 copia…».
- **Pasada 3:** sin cambios, no se dice nada.
- **Quitar «uno»** (`SettingsStore.removeTarget` devuelve `hadRequest`): `retell` (`SettingsScreen.kt:351`) dice `AUTO_BACKUP_REPLACED` para una carpeta, con la cuenta. Es verdad para la única carpeta que queda, y el snackbar dice a la vez `AUTO_BACKUP_REPLACE_DROPPED` sobre la quitada.

Test: `:1228-1259`.

### 3.4 Publicar al cambiar: «1 de 2» seguido de «en cada carpeta»

- **Cada texto es verdad cuando se publica:** el resumen se lee bajo el cerrojo con la misma lectura de `targets()` que decide.
- **`Notice` es una sola casilla** (`core/Notice.kt:20-25`): el «en cada carpeta» sustituye al «1 de 2» si todavía no se había cerrado. Nunca sale un texto viejo después de uno nuevo.
- **Desfase acotado (i-1):** el «1 de 2» puede seguir en pantalla, ya desfasado, desde que la segunda carpeta guarda su `replaceDone` hasta que termina la pasada y se publica. Ese desfase va en la dirección prudente: dice «no se han borrado» de una carpeta que ya borró. **Nunca hay un momento en que «en cada carpeta» esté en pantalla siendo falso.**
- **`said` y la clave:** `said` se guarda solo cuando hay resumen (`:575`), y la clave lleva el id, las carpetas, las reemplazadas, las borradas y las sin apuntar (`A6Text.kt:20`). Así no se repite nada, y un cambio de cualquiera de esos valores sí se publica.

### 3.5 El «otro orden»: no se puede llegar a él (verificado)

La cadena del informe se sostiene:
- **El diálogo es modal.** Es un `AlertDialog` en `VaultNav` mientras `masterState.open` (`AppNavHost.kt:252-268`). Tocar fuera o «Atrás» llama a `close`, que vacía el estado (`SettingsScreen.kt:400`; `MasterChanged.kt:56-58`). Mientras `working`, ni eso.
- **`replace()` exige el diálogo abierto** (`MasterChanged.kt:61-66`), y es el **único** llamante de `replaceOldCopies` (`AppContainer.kt:758`).
- **`changeMaster` bloquea la bóveda** (`VaultStore.kt:1301-1303`).
- **Añadir una carpeta exige la bóveda abierta**, y cambiar de bóveda cierra el diálogo (`AppContainer.kt:514`).

**Eslabón que añado:** la apertura automática con la huella (`VaultScreen.kt:424`, `:438`) tampoco sirve aunque `BiometricVault.disable` haya fallado (`fingerprintFailed`, `VaultBackup.kt:133-138`). `changeMaster` crea una clave de datos nueva (KDoc de `VaultStore.kt:1281-1284`), así que la clave de la huella, que envolvía la vieja, no autentica el archivo nuevo. Da `StaleBiometricKey` (CRY-A02) y no abre nada.

Queda **a comprobar en el móvil** (tarea 11) que TalkBack no puede llegar al campo de contraseña de debajo del diálogo (i-3).

### 3.6 m-2 (el resultado viaja)

- **Viaja con `pastTargets`** (`keptPast`, `Settings.kt:238`) **y vuelve con `withTargetAdded`** (`:196`).
- **Viaja con el deshacer de la importación:** los tres campos por destino más `a6` (`SettingsStore.kt:123-128`). El test `anImportUndoneGivesBackTheA6StateExactly` (`:1337-1350`) compara los `VaultSettings` enteros antes y después de deshacer.
- **No viaja con «Cambiar de carpeta»** hacia una carpeta que estaba en `pastTargets` (`withTargetFolderChanged`, `Settings.kt:211-214`). Es m-1, nuevo.

### 3.7 Cerrar una petición quitando la última carpeta pendiente

- Hecho: `SettingsScreen.kt:350-351` llama a `retell`, que publica (`AutoBackup.kt:582`).
- También al cambiar de carpeta una que tenía la petición (`SettingsScreen.kt:233`, `:246`).
- `retell` se espera en la interfaz detrás del cerrojo del motor (m-6).

### 3.8 i-3: «Hacer una copia ahora» con la bóveda cerrada

- **Ya no pide la copia:** solo `is VaultState.Unlocked -> autoBackup.request(t.uri)` (`SettingsScreen.kt:334-337`).
- **El texto ya no promete nada:** `AUTO_BACKUP_NOW_NEEDS_OPEN` (`BackupTargetTexts.kt:52`), «La copia se hace con la bóveda abierta: ábrela (pestaña Bóveda) y vuelve a pulsar «Hacer una copia ahora».» **Es verdad:** `request` solo se llama con la bóveda abierta.
- **La pista «Aún no se ha hecho ninguna copia: se hará en cuanto abras la bóveda.»** (`:318-322`) también es verdad sin memoria: con `lastAt == null`, `due` da verdadero para un calendario (`BackupSchedule.kt:92`), y `missed` también para `ON_CHANGE` si hubo algún cambio guardado (`AutoBackup.kt:746-749`). En los dos casos, `runDue` al desbloquear copia.
- **Test:** `PlatformSourcesTest.kt:1053-1067`, que es un test de fuentes.

### 3.8 bis Un listado fallido nunca da una cuenta de 0 (lo pedía la ronda 3)

- `ReplaceDone.unlisted` es ahora un `Int` que no puede ser null.
- Si `folder.list` lanza dentro de `rotate` (`AutoBackup.kt:971`), la excepción se captura en `attempt` (`:804-808`). Esa captura da `Rotation(AUTO_BACKUP_OLD_NOT_DELETED, …, replaceDone = false)`, así que el resultado no es `replaced` y no se guarda ningún `replaceDone`.
- La cuenta solo se toma tras un listado que funcionó (`:1004-1008`).
- El test `aFailedListingIsNeverAZeroCount` (`AutoBackupTargetsTest.kt:921-949`) lo fija: «dos» queda sin `replaceDone` y con la petición. Al final sale «2 de 3», no «en cada carpeta». Su KDoc (`:918-920`) aún habla del texto «no se ha comprobado» de la ronda 3 (va con m-5).

### 3.9 La muerte del proceso

- **Se guardan** `replaceRequest`, `replaceDone` (`@Serializable`, `BackupSchedule.kt:46`, `:53`, `:99-100`) y `VaultSettings.a6` (`Settings.kt:111-112`; `BackupSchedule.kt:107-108`).
- **Una petición de la 1.4.3** se lee como `"1.x"`. El test `aRequestOf143IsTheRequestOfNow` (`AutoBackupTargetsTest.kt:1353-1360`) convierte unos ajustes de la 1.4.3 y comprueba que el resultado cuenta.
- **Lo que no se guarda** es el resultado ya publicado y aún no visto. Vive en `Notice`, en memoria, y `said` se guarda al publicar, así que tras una muerte del proceso no se vuelve a decir (m-7). Es lo mismo que en la 1.x, y el implementador lo dice en sus «Dudas».

## 4. El resto del diff

- **D-3:** solo cambia que `Replace` lleva `request = t.requestId` (`AutoBackup.kt:475`, `:522`); la regla del salto (`:521`) no cambia. `copiedMark` vuelve ahora con la carpeta, pero sin `lastAt` (`Settings.kt:191-196`), así que la primera copia nunca se salta (`:521`, `t.lastAt != null`). El deshacer devuelve una marca vieja, pero el archivo restaurado tiene otra fecha y sale una copia de más, que es la dirección segura. El test `whatIsAndIsNotAChangeForTheCalendar` (`AutoBackupTargetsTest.kt:1363-1393`, leído) cierra los huecos de la re-revisión 3: llama de verdad a `recordBackupChecked`, vacía la papelera en un desbloqueo y comprueba que «Importar copia» deja `copiedMark = null` y que la apertura siguiente copia.
- **`replaceUnlisted`:** ha desaparecido del todo (`grep` sin resultados en `app/src`). La cuenta vive en `replaceDone.unlisted`.
- **N-4:** `publish` lee el resumen y comprueba la generación dentro del cerrojo (`:558`). `retell` usa la generación del momento, y tras una importación `a6` vale null y ningún destino tiene `replaceDone`, así que no dice nada.
- **O-5:** la petición sigue viajando con `pastTargets`. La excepción es «Cambiar de carpeta», como antes de esta ronda (m-1).
- **Datos en `settings.json` (sin cifrar):**
  - `replaceRequest`: un UUID aleatorio;
  - `replaceDone`: el UUID y dos contadores;
  - `a6`: el UUID y `said` = `"uuid|n|n|n|n"`.

  Son contadores y un id, **sin nombres ni rutas** más allá de los URI de las carpetas que ya se guardaban. No revelan nada de la bóveda.

## Issues

### Critical

Ninguno.

### Important

Ninguno.

### Minor

**m-1. «Cambiar de carpeta» a una carpeta de `pastTargets` no trae su resultado de A6, ni su petición, ni su marca (m-2 incompleto).**
- **Dónde:** `Settings.kt:211-214`. `fresh` solo copia `copies` e `importCopies`; `withTargetAdded` (`:191-196`) trae también `replacePending`, `replaceTargets`, `replaceTold`, `replaceRequest`, `replaceDone` y `copiedMark`.
- **Escenario:**
  1. «uno» reemplaza para R y se quita: su `replaceDone(R)` queda en `pastTargets`.
  2. «dos», todavía pendiente, se cambia a la carpeta «uno».
  3. `retell` dice «…en 0 de…» o, si hay una tercera hecha, «En la otra carpeta no se han borrado…» de una carpeta que **sí** las borró.
- **Por qué es menor:** el error va en la dirección prudente. Además, una petición pendiente guardada en `pastTargets` (O-5) se pierde por este camino sin aviso: sus copias siguen apuntadas pero ya no se reemplazan, y el texto lo dice («no se han borrado»).
- **Arreglo:** construir `fresh` como `withTargetAdded`, con los seis campos de `past`, y añadir al test exhaustivo un estado `MOVED_TO` (y otro con una petición pendiente en `pastTargets`).

**m-2. Si no se guarda el id de la petición nueva, se sigue adelante (teórico; la única grieta de «nunca se suma una cuenta anterior»).**
- **Dónde:** `AppContainer.kt:835-843` (`recordA6` se traga el fallo) y `AutoBackup.kt:619-620` (`replaceNow` sigue igual).
- **Escenario 1 (omisión):**
  1. Petición R0 terminada.
  2. Nuevo cambio de maestra: el disco está lleno al guardar `A6State(R)` y deja de estarlo justo después.
  3. Los destinos guardan R, pero `a6.id` sigue siendo R0.
  4. Al terminar, el resumen de R0 da 0 reemplazadas: **ni el éxito ni el aviso de COPIAS-2 se dicen nunca**.

  El efecto es el mismo que el de la I-2 de la re-revisión 3, que fue Important. La diferencia es cómo se llega. La I-2 salía con un uso normal (una carpeta sin espacio o sin permiso, que falla siempre). Este escenario solo sale con un fallo de escritura de `settings.json` en el almacenamiento interno justo en esa escritura y no en las siguientes. Por eso lo cuento como teórico.
- **Escenario 2 (texto falso, hacen falta dos cosas improbables a la vez):** además, un destino sale por `?: AutoBackupResult(uri, null, AUTO_BACKUP_FAILED)` (`:631`), sin `onResult`, con la bóveda no bloqueada. Conserva su `replaceDone(R0)`, se suma y sale «Se borraron … en 1 de las 2 carpetas» sobre un cambio de contraseña en el que no borró nada. Cuándo se llega a `:631` con la bóveda no bloqueada está en el Veredicto: solo con la bóveda borrada o con una carrera de microsegundos.
- **Arreglo:** que `onA6` diga si guardó (que `recordA6` devuelva `Boolean` o relance). Si el id no quedó guardado (o `a6()?.id != request` al leerlo otra vez), `replaceNow` devuelve `AUTO_BACKUP_FAILED` **antes** de tocar ninguna carpeta. Test con un `onA6` que falla.

**m-3. El diálogo de la maestra puede enseñar un texto rojo vacío.**
- **Dónde:** `SettingsScreen.kt:397`, `:420`. `partial` es verdadero con `replaced == 0` (`notReplaced = folders > 0`), pero `a6Text` devuelve `""` cuando `replaced == 0` (`A6Text.kt:48`).
- **Cuándo pasa:** con `Done` (`replaceStatus`, `AutoBackup.kt:1064-1065` o `:1079`) cuando ningún destino tiene un `replaceDone` del id actual. Se ve un `Text` vacío y «Borrar…» vuelve a estar activo, sin ninguna explicación.
- **Si se llega o no:** según §3.5, el diálogo no sigue abierto durante un desbloqueo ni durante una importación. Así que el camino «tras una importación» no se alcanza con el diálogo abierto. Con la bóveda abierta, `Done` por la respuesta exige que todos los resultados vengan sin error, es decir, reemplazados. Solo queda m-2. Es un arreglo **defensivo**.
- **Arreglo:** `partial` también exige `summary.replaced > 0`. Con `Done` y `replaced == 0`, se trata como `Unknown`: «Borrar…» activo y sin texto de resultado.

**m-4. El test de fuentes solo fija dos constantes.**
- **Dónde:** `PlatformSourcesTest.kt:1053-1058`. Solo comprueba `AUTO_BACKUP_REPLACED` y `AUTO_BACKUP_REPLACED_SEVERAL`.
- **Qué no fija:** que `a6PartialText`, `a6UnlistedInDoneText` y `autoBackupUnlistedText` solo se usen en `A6Text.kt`; que `outcome.post(` aparezca una sola vez en `AutoBackup.kt`; ni que `ReplaceOutcomeDialog` reciba `shown.text`.
- **Por qué importa:** una vuelta a redactar fuera de la función con esas otras frases no haría fallar nada.
- **Arreglo:** añadir esas tres comprobaciones.

**m-5. Documentación desfasada.**
- **`AutoBackup.kt:372-377`** (KDoc de `replaceOutcome`): dice «"replaced" only when no target has the request any more ([tell])… a failure carries the count of the targets that did replace». Ya no es así: «replaced» es ahora «todas las carpetas de ahora reemplazaron en esta petición», y el texto es el de `a6Text`.
- **`AutoBackup.kt:205`** (KDoc de `newA6RequestId`): dice «16 random bytes in hex», pero es un UUID v4 en texto, con guiones.
- **KDoc de `aFailedListingIsNeverAZeroCount`** (`AutoBackupTargetsTest.kt:918-920`): describe todavía el texto de la ronda 3.
- **Anexo de textos, líneas 448 y 449:** siguen diciendo «solo cuando ninguna carpeta tiene ya la petición pendiente (ronda 2)» y «esta frase va también detrás de un fallo cuando otra carpeta ya terminó». Las dos las sustituye la línea 450, pero se contradicen con ella: el Abogado leería dos reglas.
- **Arreglo:** actualizarlo todo.

**m-6. `retell` se espera en la interfaz detrás del cerrojo del motor.**
- **Dónde:** `SettingsScreen.kt:246`, `:351`; `AutoBackup.kt:582` (`scope.async { lock.withLock … }.await()`).
- **Qué pasa:** con una copia en curso hacia un proveedor lento, el snackbar «Ya no se harán copias en esa carpeta…», `releaseIfUnused` y el «Copia automática activada…» esperan a que termine, que pueden ser segundos o minutos.
- **Arreglo:** `fun retell() { scope.launch { lock.withLock { publish(emptyList(), store.generation) } } }`, sin esperar. El resultado sale por `replaceOutcome` de todos modos.

**m-7. Si el proceso muere entre la publicación y la lectura, el resumen no se vuelve a decir.**
- **Dónde:** `said` se guarda al publicar (`AutoBackup.kt:575`). `Notice` vive en memoria, y el diálogo de la maestra también.
- **Qué se pierde:** el aviso de COPIAS-2 («puede que se abra con una contraseña anterior»). Pasaba igual en la 1.x y no es una afirmación falsa, pero el Ruling pide «sin repetición», no «sin pérdida».
- **Arreglo, el más pequeño:** guardar `said` en `replaceOutcomeShown` (al cerrar) en lugar de al publicar, y llamar a `retell` después de cada desbloqueo. La otra opción es enseñar el resumen de la petición en Ajustes, el plan B que el Ruling ya prevé para la ronda 5.

**m-8. «En la otra carpeta no se han borrado» da por hecho que había copias que borrar (para el Abogado).**
- **Dónde:** `A6Text.kt:67-71`.
- **Casos en que no encaja del todo:**
  - una carpeta añadida después, sin copias de antes: no había nada que borrar;
  - una con `AUTO_BACKUP_OLD_NOT_DELETED`, que borró algunas: no es verdad que no se borrara ninguna.

  Va en la dirección prudente y lleva «puede que».
- **Propuesta, verdadera en todos los casos:** «En la otra carpeta puede quedar alguna copia de antes del cambio de contraseña: míralo a mano.» y «En las otras K carpetas puede quedar alguna…». Al Abogado.

### Informativos

- **i-1.** El «1 de 2» ya en pantalla puede quedar desfasado mientras dura la pasada en que termina la otra carpeta (§3.4). Va en la dirección prudente.
- **i-2.** Una carpeta que vuelve con una petición anterior pendiente (R0, por O-5) y la termina cuenta como no hecha para R («Dudas» 2 del informe). Va en la dirección prudente.
- **i-3.** El «otro orden» queda descartado por el código (§3.5). Falta comprobar en el móvil, en la tarea 11, que TalkBack o un servicio de accesibilidad no pueden escribir en el campo de la contraseña de debajo del diálogo. Si pudieran, haría falta la regla de reserva del Ruling.
- **i-4.** El test exhaustivo es de ajustes y texto, con resultados sintéticos (§2). Es lo que debe probar. Que `replaced` implique «borrado» lo fijan los tests de la rotación de CR-1 y los caminos A y B.

## Textos (veracidad y palabras prohibidas)

- **Palabras prohibidas:** ninguno de los textos nuevos tiene ninguna de `ForbiddenWords.kt:5-7`. Los textos nuevos son:
  - `a6PartialText` (`A6Text.kt:64-71`);
  - `a6UnlistedInDoneText` (`:77-85`);
  - `AUTO_BACKUP_NOW_NEEDS_OPEN` (`BackupTargetTexts.kt:52`).
- **Veracidad:**
  - el parcial dice la cuenta exacta (§1.2, rama e);
  - «En la carpeta / En las carpetas en que se borraron quedaba(n) N…» usa la suma del id actual, con el singular o el plural según `replaced`;
  - `AUTO_BACKUP_NOW_NEEDS_OPEN` es verdad (§3.8).

  Solo m-8 es un matiz de precisión.
- **Español llano:** correcto. «(las que la app tenía apuntadas)» va entre paréntesis y delante de «en N de las M carpetas», así que no se lee como dicho de «contraseña» (O-2 de la 1.x).

## Revisión experto-seguridad-cripto

**Modelo de amenaza:** el de las re-revisiones anteriores.
- El activo de A6 son las copias que se abren con una maestra anterior, quizá comprometida.
- El usuario decide si borra a mano según lo que le dice la app.
- Un «se borraron» falso deja copias abribles con la clave vieja sin que nadie las mire (CWE-451).

| Punto | Evidencia | Valoración |
|---|---|---|
| «En cada carpeta» | `A6Text.kt:32`, `:49-51`; `Settings.kt:309-311`; `AutoBackup.kt:817-825` | Cerrado por construcción: un dato por carpeta y por id |
| Una cuenta de otra petición | `A6Text.kt:32`; `Settings.kt:289`, `:314` | Filtrada por id; grieta teórica en m-2 |
| Caminos A y B e I-2 | §3.1-3.3; tests `:1160`, `:1195`, `:1228` | Verdad en todos |
| «Otro orden» | `MasterChanged.kt:56-66`; `VaultStore.kt:1301-1303`; `AppContainer.kt:514`, `:758`; CRY-A02 | Inalcanzable. TalkBack, a comprobar (i-3) |
| Borrado | `rotate` sin cambios; el id solo cambia lo que se dice | Solo ids de la lista propia |
| Id de la petición | `UUID.randomUUID()` (`AutoBackup.kt:206`) | Solo tiene que ser único; no es un secreto. `SecureRandom` por debajo |
| `settings.json` | `BackupSchedule.kt:46`, `:53`, `:100`, `:108` | Contadores y un UUID; sin nombres, rutas ni datos de la bóveda |
| Red, permisos, registros y dependencias | diff | Nada nuevo |

## Revisión Ingeniero Móvil Senior

| Área | Evidencia | Valoración |
|---|---|---|
| Concurrencia | `AutoBackup.kt:464`, `:502`, `:582`, `:610` | `publish`, `retell` y `replaceNow` van bajo el mismo `Mutex`. `onA6` y `onResult` escriben de forma síncrona (`AppContainer.kt:808-816`, `:835-843`), así que el resumen ve los resultados de la pasada |
| Persistencia | campos opcionales con valor por defecto; `"1.x"` | Se siguen leyendo los ajustes de la 1.4.3 y de las tareas 2 a 8. «Cambiar de carpeta» no lleva los campos nuevos (m-1) |
| UI | `AppNavHost.kt:220`, `:257`; `SettingsScreen.kt:397-426` | El texto queda fijado al publicar. El diálogo lee el mismo resumen. Texto vacío en rojo (m-3). `retell` bloquea el snackbar (m-6) |
| Ciclo de vida | `Notice` en memoria, `said` guardado al publicar | Se pierde con la muerte del proceso (m-7). Nada en `rememberSaveable` ni en un `Bundle` |
| Tests | `AutoBackupTargetsTest.kt:1160-1334`; `PlatformSourcesTest.kt:1053` | Deterministas (`runTest`, `runCurrent`, `settle`); el exhaustivo es puro. El de fuentes es estrecho (m-4) |
| i-3 | `SettingsScreen.kt:334-337` | Sin estado en memoria que pueda perderse |

## Textos para el Abogado Digital (revisión final)

- **Nuevos (ronda 4):**
  - «Se borraron las copias automáticas y las de antes de importar hechas antes del cambio de contraseña (las que la app tenía apuntadas) en N de las M carpetas.»;
  - «En la otra carpeta no se han borrado: puede que alguna se abra con una contraseña anterior. Míralo a mano.» y «En las otras K carpetas no se han borrado: …». La propuesta de m-8 es «…puede quedar alguna copia de antes del cambio de contraseña: míralo a mano.»;
  - «En la carpeta en que se borraron quedaba 1 copia…» y «En las carpetas en que se borraron quedaban N copias…»;
  - «La copia se hace con la bóveda abierta: ábrela (pestaña Bóveda) y vuelve a pulsar «Hacer una copia ahora».»
- **Anexo:** las líneas 448 y 449 están desfasadas frente a la 450 (m-5).
- **Para la política (tarea 12):** los ajustes guardan, por carpeta, un id aleatorio de la petición de borrar las copias anteriores y cuántas copias borró y cuántas sin apuntar quedaron. No guardan nombres de archivo nuevos.
