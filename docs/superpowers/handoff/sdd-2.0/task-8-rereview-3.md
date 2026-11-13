# Repaso de la ronda 3 de la tarea 8 (Clavero 2.0)

- **Alcance:** de `67c4b2e` a la cabeza `95c8667`. Entran la tabla de textos de A6, `replaceUnlisted`, D-3 (decisión del dueño), N-2, N-4, N-5, O-5, los tests nuevos y los textos.
- **Cómo se hizo:** solo leyendo el código, sin subagentes. **No ejecuté nada**: ni gradle ni tests (las suites las ejecuta el controlador en un clon limpio). Las líneas son las de `95c8667`; el árbol de trabajo estaba limpio.
- **Lentes:**
  - `experto-seguridad-cripto` (`SKILL.md`): un hallazgo de seguridad o de veracidad confirmado es Important como mínimo; lo teórico se apunta como menor.
  - `desarrollo-movil-senior` y `auditoria-seguridad-movil`.
  - Regla del encargo: un texto que puede afirmar algo que el código no hizo es **Important**.

## Veredicto

**Hay que arreglarla antes de seguir: 0 Critical, 2 Important, 5 Minor y 4 informativos.**

- Lo que la re-revisión 2 pidió está hecho:
  - el éxito con varias pasadas y con un `replaceNow` cortado por un bloqueo cuenta todas las carpetas;
  - N-2, N-4, N-5 y O-5 (la petición viaja con `pastTargets`).
- D-3 está bien hecho. No encontré ningún salto indebido: todos los modos de fallo que seguí acaban en una copia de más, nunca en una de menos.
- Quedan dos Important de la misma clase que I-3. Los dos vienen de antes de esta ronda, pero esta ronda tenía que cerrar la clase («entregable que cierra la clase»):
  - **I-1:** `AUTO_BACKUP_REPLACED_SEVERAL` («…se borraron … las que la app tenía apuntadas **en cada carpeta**») sale con una carpeta que nunca tuvo la petición o la perdió sin terminar. Ahí las copias apuntadas de la maestra anterior siguen en la carpeta. Hay un camino que se puede probar de forma determinista, con un solo fallo provocado y sin bloqueos a mitad.
  - **I-2:** si una carpeta falla siempre con el mismo error, la cuenta de las carpetas que terminan después no se enseña nunca. No incumple la regla de no decir algo falso: omite un aviso. Pero incumple una cláusula expresa del Ruling.

### Veredictos por punto

| Punto | Veredicto | Evidencia |
|---|---|---|
| La cuenta de copias sin apuntar (Important de la re-revisión 2) | **ADDRESSED** con varias pasadas y con un bloqueo; **PARTIALLY** con una carpeta que falla siempre (I-2) | `AutoBackup.kt:567-584`, `Settings.kt:296`; tests `AutoBackupTargetsTest.kt:850`, `:884` |
| `replaceUnlisted`: se borra donde debe | **ADDRESSED**, salvo la salida sin `onResult` de `replaceNow` (`AutoBackup.kt:630`, «a comprobar», en I-1) | `Settings.kt:247-248`, `:273-276`, `:297`, `:231-235`; `SettingsStore.kt:84-91`; `AppContainer.kt:550-556` (id nuevo, sin destinos) |
| `replaceUnlisted`: suma y «ninguno pendiente» de la misma lectura (N-4) | **ADDRESSED** | `AutoBackup.kt:569-572`, dentro del cerrojo (`:495`, `:541`); generación leída **después** de la instantánea; `VaultStore.kt:1458-1460` sube la generación antes de `beforeReplace` |
| `replaceUnlisted` viaja con `pastTargets` (O-5) | **PARCIAL** (Minor m-2): viaja la petición, no la cuenta ni `copiedMark` | `Settings.kt:220-227`; `SettingsStore.kt:118-122` |
| N-2 (anexo) | **ADDRESSED** | `docs/superpowers/specs/2026-10-27-seguridad-1.3.0-textos.md:446-449` |
| N-4 | **ADDRESSED** | arriba |
| N-5 (KDoc de `replaceOutcome`) | **ADDRESSED** | `AutoBackup.kt:387-391` |
| O-5 | **ADDRESSED** (la petición vuelve) | `Settings.kt:176-187`, `:220-227`; test `:982` |
| D-3 | **ADDRESSED**, sin saltos indebidos | sección D-3, abajo |

## La tabla de textos de A6, fila a fila

| # | Texto | ¿La condición es exacta? | ¿El test la fija? |
|---|---|---|---|
| 1 | `AUTO_BACKUP_REPLACED` (una carpeta) | **Sí** en `tell` (`:572`, `:578-582`) y con una respuesta sin error de `replaceOldCopies` (`SettingsScreen.kt:405`). **Le falta una condición:** el diálogo de la maestra también llega a `Done` desde los ajustes, tras una respuesta `WAITS`, cuando nada queda pendiente (`replaceStatus`, `AutoBackup.kt:1052-1053`). Ahí enseña `replacedText(state.answerUnlisted, …)`, con la cuenta de la respuesta directa, no la de las pasadas de después (m-1). | Sí: `AutoBackupF3Test.kt:175` fija el texto; `AutoBackupTest.kt:884`, el camino de `tell`. |
| 2 | `AUTO_BACKUP_REPLACED_SEVERAL` | **No del todo.** `done` exige que ninguna carpeta tenga `replacePending` (`:572`). Pero sale también con una carpeta que no tiene la petición y no reemplazó nada: una quitada antes del cambio y vuelta a añadir, o una que falló en un `replaceNow` con la bóveda abierta antes de un bloqueo. Ahí «se borraron … en cada carpeta» es falso. Es **I-1**. | Los casos que la fila cita sí (`:773`, `:811`, `:941`, `:982`). Falta el de I-1. |
| 3 | «En la carpeta queda(n) N copia(s)…» | Sí. Con una carpeta, la suma es su cuenta: un fallo de esa carpeta deja `null` (`Settings.kt:297-298`). | `AutoBackupF3Test.kt:351`, `:365`. |
| 4 | «Al borrar las copias anteriores, en las carpetas quedaba(n)…» | **Sí tras el éxito**, con la suma guardada (`:570`, `:581`). **Tras un fallo, solo si ese fallo se dice por primera vez** (`:574-577`). Un fallo ya dicho no vuelve a publicar nada. Una carpeta que termina después de ese fallo nunca enseña su cuenta mientras la otra siga fallando. Es **I-2**. | `:850` (fallo nuevo y éxito después) y `:884`. Falta el de I-2. |
| 5 | «En N carpeta(s) no se ha comprobado…» | La condición del código es exacta: `replaceUnlisted == null` con `done` (`:581`). **La razón que da la tabla está incompleta.** No es solo «una añadida después de pedirla». También es una que falló en `replaceNow` con la bóveda abierta (I-1), una que terminó y se quitó y se volvió a añadir, o una tras deshacer una importación (m-2). En el primer caso el texto se queda corto: no dice que las copias **apuntadas** tampoco se borraron. | `:910`: falla sin `unchecked` (M5 del informe). |
| 6 | Fallos (`WRITE_FAILED`, `NO_PERMISSION`, `NO_FOLDER`, `VAULT_PROBLEM`, `FAILED`) | Sí: `toldKey` (`:840-843`) más `toldInRequests` (`:553`), y se publican en `:574-577`. | `:505`, `:549`. |
| 7 | `AUTO_BACKUP_OLD_NOT_DELETED` | Sí: `:801` y `:834-838`, con la petición todavía guardada. | `AutoBackupF3Test.kt:187`. |
| 8 | `AUTO_BACKUP_OLD_NOT_DELETED_NO_RETRY` | Sí: `:809-812` y `:834-838`. | `:578`; `AutoBackupF3Test.kt:204`. |
| 9 | `AUTO_BACKUP_REPLACE_WAITS` | Sí: `:632`, `:653-660`, `:640`. | `:242`, `:884`. |
| 10 | `AUTO_BACKUP_REPLACE_DROPPED` | Sí: `SettingsStore.kt:98-103`. Con O-5 la petición vuelve. | `:982`; `SettingsTest.kt:484`. |

**Filas que faltan:**
- **`ReplaceStatus.Done` y `Failed` leídos de los ajustes**, en el diálogo de la maestra (`AutoBackup.kt:1052-1062`; `SettingsScreen.kt:405-411`). Es un segundo diálogo con texto de resultado de A6. Usa `answerUnlisted` de la respuesta directa, no la suma guardada, y no tiene la línea de «no comprobado».
- **Los casos del encargo:**
  - **Varias carpetas y varias pasadas:** filas 2 y 4. Bien, salvo I-2.
  - **Una carpeta:** fila 1. Bien.
  - **`replaceNow` cortado por un bloqueo:** bien si la primera carpeta reemplazó (`:884`). **Mal** si falló (I-1, camino B).
  - **Una importación a mitad de la petición:** bien (`:578`, `:941`).
  - **O-5:** bien si la carpeta se quitó **durante** la petición (`:982`). **Mal** si se quitó **antes** del cambio (I-1, camino A).
  - **Una carpeta que falla siempre:** **mal** (I-2).

## D-3, el riesgo con nombre

**Cómo funciona, leído en el código:**
- **La marca.** Es el SHA-256 de `"$changedAt|m,t,p|$tag"` (`VaultStore.kt:1673-1677`), donde:
  - `changedAt` es `file.lastModified()` (`:416`);
  - el coste es el del `VaultFile` en memoria (`:428`);
  - el tag es el de los datos abiertos (`:429`).
- **Por qué los guardados de contabilidad no la mueven.** `saveBookkeeping` lee la fecha, escribe (con `writeTextAtomically`, `:222`) y la devuelve con `setLastModified` (`:405-409`). Hay tres sitios que lo usan, todos con el `mutex` del almacén:
  - `commit(mustSave = false)` (`:446-450`), en el desbloqueo con la maestra (`:544`, `:573`), en el de la huella (`:637`, `:671`) y en `modifyData(bookkeeping = true)` (`:784`, `:815`): `markUsed` (`:1084`) y `recordBackupChecked` (`:1096`);
  - `completeUpgradeLocked` (`:600`, `:605`).

  Así que la lectura, la escritura y la vuelta a poner la fecha nunca se cruzan con un guardado real.

**Los cambios reales mueven la marca:**

| Cambio | Cómo | Evidencia |
|---|---|---|
| Entradas | `modify`/`modifyData` sin `bookkeeping`, `save` y la fecha nueva | `:761-765`, `:815`, `:447` |
| Adjuntos (archivos aparte, por id y SHA-256) | `addAttachment` y `removeAttachment` pasan por `modify`: guardado real | `:1241-1255`, `:1264-1267` |
| Ajustes de la bóveda (vistas, carpetas, nombre) | `modifyData`, guardado real | `:1071` |
| Maestra | `changeMaster` usa `save`; además fuerza A6, que nunca se salta | `:1285-1300` |
| A8 | guardado de contabilidad que conserva la fecha, pero el coste entra en la marca | test `:1111` |
| `vaultTag` | entra en la marca; «Importar copia» guarda con fecha nueva y borra `copiedMark` (`Settings.kt:233`); «como bóveda nueva» es otro id, sin destinos (`AppContainer.kt:550-556`) | `:1131` |

**Ningún salto indebido:**
- **La marca se lee antes de la instantánea** (`AutoBackup.kt:785-786`, y después `copyTo`, que exporta con el `mutex` en `VaultStore.kt:1336`). Un cambio en medio deja una copia más nueva que su marca, y la siguiente vez sale una copia de más.
- **Una lectura a mitad de un `saveBookkeeping`** (la fecha nueva, todavía sin devolver) da una marca distinta, y por tanto una copia de más.
- **Si `setLastModified` falla**, la fecha se mueve y sale una copia de más.
- **La comparación es por igualdad**, no por orden. Un reloj atrasado o adelantado da otra fecha y por tanto copia. Un archivo restaurado con una fecha vieja necesitaría el mismo milisegundo, el mismo coste y el mismo tag; además, `allowBackup="false"` (`AndroidManifest.xml:11`).
- **Lo que no cuenta como cambio** (el desbloqueo, «Recientes», la fecha de la última comprobación y la purga de la papelera al desbloquear, `VaultStore.kt:666-671`) no quita datos de la última copia. Esa copia tiene **más**, no menos (i-2).

**Lo que nunca se salta** (`AutoBackup.kt:536`):
- `!replace`: una A6 pendiente;
- `!waited`: una copia que esperaba al desbloqueo;
- `t.lastAt != null && t.copiedMark != null`: la primera copia, también tras migrar de la 1.4.3 o tras volver a elegir la carpeta (`Settings.kt:197`);
- `calendar`, que excluye `ON_CHANGE`;
- `runChanged`, que no tiene salto (la copia a mano).

**Cuando hay salto:**
- **No escribe, no rota y no toca la fecha:** el `continue` va antes de `attempt`, así que no cambian `pending`, `failedAt`, `copiedRevision` ni `byCalendar`.
- **Un fallo no marca la carpeta como al día:** solo la rama `writtenAt != null && copies != null` guarda `copiedMark` (`Settings.kt:278-279`). Un resultado de una bóveda reemplazada pierde `copies` (`:264-266`) y no guarda marca.
- **La muerte del proceso:** la fecha está en el archivo y `copiedMark` en los ajustes, así que la marca sobrevive.
- **Lo que revela:** nada. Es un SHA-256 con un tag aleatorio de 128 bits; en `settings.json` solo se puede comparar si dos marcas son iguales. La fecha y el coste ya se ven en el disco.

## Issues

### Critical

Ninguno.

### Important

**I-1. «…se borraron … las que la app tenía apuntadas en cada carpeta» con una carpeta en la que no se borró nada (veracidad, CWE-451; la misma clase que I-3).**
- **Dónde:**
  - `AutoBackup.kt:572` (`done` solo mira `replacePending`);
  - `:578-582` y `:216` (la base es `AUTO_BACKUP_REPLACED_SEVERAL`, `:161-164`, aunque `unchecked > 0`);
  - `autoBackupUncheckedText`, `:201-207`, solo habla de las copias **sin apuntar**;
  - el diálogo de la maestra, `SettingsScreen.kt:405` con `replaceStatus`, `AutoBackup.kt:1052-1053`.
- **Camino A (O-5 al revés: la carpeta se quita antes del cambio).** Añadir una carpeta exige la bóveda abierta (`SettingsScreen.kt:297`, `editable`), así que hace falta que «dos» siga pendiente después de un desbloqueo:
  1. Dos carpetas, «uno» y «dos», con copias apuntadas.
  2. Se quita «uno». `withTargetRemoved` guarda sus listas en `pastTargets` sin petición, porque aún no había ninguna (`Settings.kt:211-227`).
  3. Se cambia la maestra (la bóveda se bloquea, `MasterChanged.kt:53`) y se pide «Borrar…». Solo «dos» recibe la petición (`replaceNow`, `:621`, `:632`; `listedCopies`, `Settings.kt:138`).
  4. Al desbloquear, «dos» falla (por ejemplo, sin espacio) y conserva la petición.
  5. Con la bóveda abierta se vuelve a añadir «uno». `withTargetAdded` le devuelve sus copias apuntadas, todas de antes del cambio, sin petición (`Settings.kt:179-185`). «uno» hace su primera copia normal, sin borrar nada de lo anterior.
  6. «dos» reemplaza en una pasada posterior. `done` es verdadero (`:572`), `unchecked` vale 1 y sale «Se hizo una copia nueva en cada carpeta y se borraron las copias … hechas antes del cambio de contraseña: las que la app tenía apuntadas en cada carpeta.»
  7. En «uno» siguen las copias apuntadas con la maestra anterior. Solo se irán al rotar, según «Conservar las N».
  8. La línea añadida, «En 1 carpeta no se ha comprobado si quedan copias … que la app no tenía apuntadas», no lo corrige: dice algo de otras copias.
  9. Para un test es determinista: un solo fallo provocado (`two.failCreate`) y ningún bloqueo a mitad.
  10. **Si el diálogo de la maestra sigue abierto** (sobrevive al bloqueo, `MasterChanged.kt:53-60`), pasa a `Done` cuando nada queda pendiente y todas las carpetas tienen una copia posterior (`replaceStatus`, `AutoBackup.kt:1052-1053`, con `oldestBackupAt` de todos los destinos), y enseña `replacedText(…, 2)`: la misma frase, sin la línea.
- **Camino B (`replaceNow` cortado por un bloqueo tras un fallo):**
  1. Con la bóveda abierta, «Borrar…». Se puede llegar desde la app: el diálogo de la maestra sobrevive al bloqueo que sigue al cambio, y después del desbloqueo «Borrar…» sigue activo (`canReplace` con `NotAsked`, `Unknown` o `Failed`, `SettingsScreen.kt:385`). Entonces `replaceNow` va por la rama `Unlocked` (`AutoBackup.kt:624-630`).
  2. En «uno» la copia falla (`:628-629`; `messageFor`, `:830`). Pedida con la bóveda abierta, la petición no se guarda (`Settings.kt:297-298`): `replacePending = false` y `replaceUnlisted = null`. Las copias apuntadas de «uno» no se tocan.
  3. La bóveda se bloquea antes de «dos» (un proveedor lento en «uno» y el usuario sale de la app). «dos» guarda la petición (`:632`, `:653-660`).
  4. Al desbloquear, «dos» reemplaza y `done` es verdadero: la misma frase falsa sobre «uno».
- **Otro orden, «a comprobar en el móvil»:** desbloquear, volver a añadir «uno» y pulsar «Borrar…» con el diálogo todavía abierto. Ahí `asked` es la lista del momento del cambio, sin las copias de «uno» (`MasterChanged.kt:55`, `:75`). En `replaceNow`, «uno» acabaría `replaced` sin borrar nada suyo y **con** una cuenta, así que el punto 1 del arreglo no lo taparía. No creo que se pueda llegar: el diálogo es modal y cerrarlo lo borra (`close`, `:58-60`). Si en el móvil se puede, el punto 4 deja de ser opcional.
- **Variante «a comprobar», no la cuento:** la salida `?: AutoBackupResult(uri, null, AUTO_BACKUP_FAILED)` (`:630`) no pasa por `onResult`, así que una carpeta que sale por ahí conserva el `replaceUnlisted` de la petición **anterior**. La suma tendría una cuenta vieja y la carpeta no saldría como no comprobada. En producción, `SafBackupTarget.open` lanza en lugar de devolver `null` (`SafBackupTarget.kt:125-128`), así que solo se llega ahí si `copyTo` da `null` con la bóveda abierta.
- **Origen:** la regla «ninguna con `replacePending`» es de la ronda 2. La ronda 3 añadió la línea de las no comprobadas, pero dejó la base «en cada carpeta».
- **Arreglo propuesto:**
  1. En `tell`, `AUTO_BACKUP_REPLACED_SEVERAL` solo con `unchecked == 0`. Si no, una base que no diga «cada carpeta» y una línea que diga lo que de verdad pasa en las demás, sin el número de carpetas en la base. Por ejemplo: «Se hizo una copia nueva y se borraron las copias … hechas antes del cambio de contraseña en las carpetas que tenían la petición: las que la app tenía apuntadas en cada una.» más «En N carpeta(s) no se borraron las copias anteriores al cambio de contraseña ni se ha comprobado si quedan otras: puede que alguna se abra con una contraseña anterior. Míralo a mano.» El texto, al Abogado.
  2. Que el diálogo de la maestra no redacte su propio éxito con datos incompletos. O lee la suma y las no comprobadas de los ajustes y usa `replaceOutcomeText`, o en `Done` tras `WAITS` remite al diálogo del resultado.
  3. En `replaceNow`, al empezar, poner `replaceUnlisted = null` en todos los destinos, con una escritura. Así una carpeta que no pasa por `onResult` nunca suma una cuenta vieja.
  4. Opcional para el camino A: que `withTargetAdded`, si otra carpeta tiene la petición pendiente, devuelva también la petición para las copias guardadas en `pastTargets` **antes** del cambio. No es necesario si se aplica el punto 1, salvo que se pueda llegar al «otro orden» de arriba.
- **Tests:**
  - `aFolderRemovedBeforeTheChangeAndAddedBackIsNotSaidDeleted` (camino A): el texto no contiene «en cada carpeta» y las copias viejas de «uno» siguen en la carpeta;
  - `aFailureThenALockInReplaceNowIsNotSaidDeleted` (camino B). `failCreate` lanza antes de escribir y `onWrite` no llega a correr, así que hace falta un gancho que bloquee y después lance durante la escritura, por ejemplo `one.onWrite = { s.lock(); throw IOException("lleno") }`, comprobando que el fallo se convierte en `WRITE_FAILED`.

**I-2. Una carpeta que falla siempre con el mismo error esconde la cuenta de las que terminan después (aviso omitido, COPIAS-2).**
- **No es una afirmación falsa:** no se dice nada. Pero incumple la cláusula del Ruling «si una carpeta falla siempre, la cuenta de las terminadas se enseña también con el error», y la advertencia «puede que se abran con una contraseña anterior» desaparece. La re-revisión 2 calificó de Important el mismo efecto.
- **Dónde:**
  - `tell` solo publica con un fallo dicho por primera vez o con `done` (`AutoBackup.kt:573-583`);
  - un fallo ya dicho no lleva `toldKey` (`:840-843`, con `toldInRequests`, `:553`).
- **Escenario**, con dos carpetas y la petición guardada con la bóveda cerrada:
  1. **Pasada 1:** «uno» falla con `WRITE_FAILED` (se dice, con una cuenta de 0) y «dos» falla con otro error, o un bloqueo la deja fuera (`:531`).
  2. **Pasada 2:** «uno» vuelve a fallar con `WRITE_FAILED`, que ya se dijo, así que no lleva `toldKey`. «dos» reemplaza y deja 2 copias sin apuntar: `told` está vacío y `done` es falso, así que **no se publica nada**.
  3. Mientras «uno» falle, las 2 copias de «dos», que pueden abrirse con la maestra que se acaba de cambiar, no se dicen nunca.
  4. Si el usuario quita «uno» (`removeTarget`), su petición se va, ya no queda ninguna pasada con avisos y la cuenta no se dice **nunca**.
- **Arreglo propuesto:**
  - En `tell`, con `!done`, si la pasada tiene un `replaced` con `unlistedLeft > 0` (o si la suma creció desde la última que se dijo para esta petición), publicar un resultado con la cuenta. Puede llevar el fallo ya dicho de la carpeta pendiente, repetido como contexto, o un texto propio que no dé la petición por terminada, por ejemplo «En N carpeta(s) ya se borraron las copias anteriores; en las demás, todavía no.» seguido de `autoBackupUnlistedText`.
  - Guardar por petición la cuenta ya dicha, para no repetirla.
  - Al quitar la última carpeta pendiente, enseñar la suma de las demás junto a `AUTO_BACKUP_REPLACE_DROPPED`, o enseñarla en Ajustes.
- **Test:** pasada 1 con «uno» `WRITE_FAILED` y «dos» `NO_PERMISSION` (por `SecurityException`); pasada 2 con «uno» `WRITE_FAILED` otra vez y «dos» que reemplaza con 1 copia sin apuntar. Se espera un resultado publicado con `unlistedLeft == 1`.

### Minor

- **m-1. El diálogo de la maestra solo cuenta la respuesta directa** (`SettingsScreen.kt:405`, `:410`; `MasterChanged.kt:83`).
  - En `Done` tras `WAITS` (el camino normal: cambiar la maestra bloquea la bóveda), enseña `replacedText(0, n)`, sin la cuenta de las pasadas de después ni la línea de las no comprobadas.
  - El diálogo del resultado (`AppNavHost.kt:220`) sí la enseña a la vez, así que el aviso no se pierde. Por eso es menor.
  - Viene de la 1.x («0 unknown»). Falta en la tabla. Se arregla con el punto 2 de I-1.
- **m-2. `keptPast` y `ReplacePrepared.undo` no llevan `replaceUnlisted` ni `copiedMark`** (`Settings.kt:220-227`; `SettingsStore.kt:118-122`).
  - Una carpeta que terminó, se quitó y se volvió a añadir, o una importación deshecha, sale como «no se ha comprobado» y pierde su cuenta.
  - `copiedMark` perdido da una copia de más.
  - Es la dirección segura. **Arreglo:** copiar los dos campos.
- **m-3. Un fallo seguido de la cuenta se lee mal** (`replaceOutcomeText`, `AutoBackup.kt:215-221`).
  - «No se pudo escribir la copia automática en la carpeta elegida. ¿Queda espacio? Al borrar las copias anteriores, en las carpetas quedaba 1 copia…» no dice que otras carpetas sí terminaron, y se puede entender que no se borró nada.
  - **Propuesta:** «En las carpetas que ya terminaron, al borrar las copias anteriores quedaba…». Al Abogado.
- **m-4. Huecos en los tests de D-3.**
  - `aCalendarCopyIsSkippedWhileNothingChanged` dice «a check of a copy» (`AutoBackupTargetsTest.kt:1020`), pero no llama a `recordBackupChecked`.
  - Ningún test cubre la purga de la papelera al desbloquear como algo que no es un cambio.
  - El reinicio por un tag nuevo solo lo fija el test del hash (`:1131`). Falta uno de integración: «Importar copia» deja `copiedMark = null` (`Settings.kt:233`) y hace la copia siguiente.
- **m-5. El KDoc de la clase no habla del salto** (`AutoBackup.kt:319-320`): «a target with a calendar gets one only from [runDue], when [due] says so». Documental.

### Informativos

- **i-1. A comprobar en el móvil (tarea 11):** la precisión de `File.lastModified()` y si `setLastModified` funciona en el almacenamiento interno de cada fabricante.
  - Con una precisión de 1 s, un cambio, una copia y otro cambio en el mismo segundo no movería la marca. Las copias van tras el *debounce* o al desbloquear, así que no veo cómo podría pasar.
  - Un `setLastModified` que falla solo da copias de más.
- **i-2. La purga de la papelera al desbloquear es un guardado de contabilidad** (`VaultStore.kt:666-671`). Una copia saltada conserva entradas que la papelera ya vació. Hay más datos, no menos; las copias anteriores también los tienen.
- **i-3. Una copia pedida a mano con la bóveda cerrada vive solo en memoria** (`pending`). Tras la muerte del proceso y sin cambios, D-3 la salta, y «se hará en cuanto la abras» deja de cumplirse. Con `ON_CHANGE` ya pasaba antes (`missed`).
- **i-4.** O-6 sigue: la cuenta puede pasarse en una carpeta compartida por dos destinos, que es la dirección segura.

## Tests: comprobación por muestreo del rojo

- **`theSuccessCountsTheUnlistedCopiesOfEveryPass` (`:850`).**
  - Comprueba lo que dice: «uno» deja 1 copia sin apuntar en la pasada 1, con «dos» fallando.
  - El fallo lleva la cuenta (`:864-869`) y el éxito de la pasada 2, que solo hace «dos», también (`:876-879`).
  - Con M1 (la cuenta de la pasada en lugar de la guardada), `done.unlistedLeft` sería 0 y el test fallaría, como dice el informe. Es un test real.
- **`aFailedCopyDoesNotMarkTheTargetUpToDate` (`:1092`).**
  - Si un fallo guardara la marca, fallaría el primer `assertEquals(mark, …)`.
  - Además, la copia siguiente se saltaría y la carpeta tendría 1 copia, no 2. Es un test real.
- **También miré la parte de A6 de `whatIsNeverSkipped` (`:1074-1082`).**
  - Fija la petición como tras un reinicio, en un día que toca y sin cambios.
  - Sin `!replace`, se saltaría y `replacePending` seguiría verdadero. Es un test real.

## Textos (veracidad y palabras prohibidas)

- **Palabras prohibidas:** ninguno de los textos nuevos o cambiados (`BackupTargetTexts.kt:7-15` y `:43-45`; `AutoBackup.kt:170-207`) tiene ninguna de `ForbiddenWords.kt:5-7`.
- **«Las copias con calendario se hacen al abrir la app, si hubo cambios.»:** es verdad, con dos excepciones que juegan a favor: la primera copia y A6 se hacen igual. En la purga de la papelera y en «Recientes» no hay copia; es razonable que el usuario no los vea como «cambios».
- **`AUTO_BACKUP_MORE`** («…si la bóveda cambió desde la última copia en esa carpeta…»): es verdad. La marca es por carpeta y la mueven también la copia a mano y la petición.
- **`scheduleAfter`:** correcto.
- **No queda ninguna promesa de una copia con calendario sin condiciones:** `grep` de «con calendario», «cada día» y «al abrir la app» en `app/src/main`, el README, `docs/privacidad.html`, `.maestro` y `docs/PUBLICAR.md` solo da `AUTO_BACKUP_SHORT` («…tras cada cambio o con calendario.», sin promesa) y un comentario de `MainActivity.kt:61`. La política no dice nada del calendario; es de la tarea 12.
- **Falsos:** `AUTO_BACKUP_REPLACED_SEVERAL` en los caminos de I-1, y el de m-3, que confunde.

## Revisión experto-seguridad-cripto

**Modelo de amenaza:** el de las re-revisiones anteriores.
- El activo de A6 son las copias que se abren con una maestra anterior, quizá comprometida.
- El usuario decide si borra a mano según lo que le dice el diálogo.
- Para D-3, el riesgo es quedarse sin una copia de datos reales.

| Punto | Evidencia | Valoración |
|---|---|---|
| El éxito de A6 «en cada carpeta» | `AutoBackup.kt:572`, `:578-582`, `:161-164`; `Settings.kt:179-185`, `:297-298` | **Important (I-1):** puede afirmar un borrado que no se hizo |
| La cuenta COPIAS-2 con una carpeta que falla siempre | `:573-583`, `:840-843` | **Important (I-2):** el aviso se omite |
| N-4 | `:569-572`, `:495`, `:541`; `VaultStore.kt:1458-1460` | Cerrado: la generación se lee después de la instantánea, con el orden de la importación |
| La marca de D-3 | `VaultStore.kt:427-432`, `:1673-1677` | SHA-256 de `java.security`, opaco (tag de 128 bits). Sin secretos en `settings.json` |
| Un salto indebido | `AutoBackup.kt:536`, `:785-786`; `VaultStore.kt:405-409` | No encontré ninguno: los modos de fallo dan copias de más |
| Borrado | `rotate`, sin cambios | Solo ids de la lista propia; la petición devuelta (O-5) solo afecta a ids de esa carpeta |
| Red, permisos, registros y dependencias | diff | Nada nuevo |

## Revisión Ingeniero Móvil Senior

| Área | Evidencia | Valoración |
|---|---|---|
| Concurrencia | `AutoBackup.kt:477-497`, `:513-544`, `:567` | `tell` dentro del `Mutex`, sin suspender; `onResult` escribe los ajustes de forma síncrona (`AppContainer.kt:805-808`; `SettingsStore.kt:48`), así que la instantánea de `tell` incluye los resultados de la pasada |
| Persistencia | `BackupSchedule.kt:41-54`; `Settings.kt:220-227`; `SettingsStore.kt:118-122` | Los campos opcionales leen los ajustes viejos. `pastTargets` y el deshacer no llevan los campos nuevos (m-2) |
| Ciclo de vida | `MasterChanged.kt:83`; `SettingsScreen.kt:403-411`; `AppNavHost.kt:218-221` | El diálogo de la maestra sobrevive al bloqueo y redacta con la respuesta directa (m-1 e I-1, punto 2) |
| Coste de D-3 | `AutoBackup.kt:536` | Un `stat` y un SHA-256 de unos 100 bytes por carpeta con calendario y pasada, frente a reexportar hasta 24 MiB y los adjuntos |
| Sistema de archivos | `VaultStore.kt:222`, `:405-409` | Escritura atómica. `setLastModified` en el almacenamiento interno, a comprobar en el móvil (i-1) |
| Tests | `AutoBackupTargetsTest.kt:850-1140` | Deterministas (`runTest`, `runCurrent`, `settle`), sin esperas reales; faltan los de I-1, I-2 y m-4 |
| Estado | — | Nada nuevo en `rememberSaveable` ni en un `Bundle` |

## Textos para el Abogado Digital (revisión final)

- **D-3:**
  - «Las copias con calendario se hacen al abrir la app, si hubo cambios.»;
  - en «Más información»: «…o cada día, cada semana o cada mes si la bóveda cambió desde la última copia en esa carpeta…»;
  - «cada día / cada semana / cada mes, si hubo cambios, al abrir la app».
- **Con varias carpetas:** «Al borrar las copias anteriores, en las carpetas quedaba 1 copia…» y «…quedaban N copias…». Tras un fallo se lee mal (m-3); propuesta: «En las carpetas que ya terminaron, al borrar…».
- **«En 1 carpeta / En N carpetas no se ha comprobado si quedan copias…»:** si se arregla I-1, cambia a un texto que diga también que las copias anteriores **no se borraron** en esas carpetas.
- **`AUTO_BACKUP_REPLACED_SEVERAL`:** no es verdad en los caminos de I-1. Su texto nuevo para cuando no todas terminaron vuelve al Abogado.
- **Para la política (tarea 12):** el calendario solo copia si la bóveda cambió desde la última copia en esa carpeta; los ajustes guardan por carpeta una marca opaca (SHA-256) y cuántas copias sin apuntar quedaron al reemplazar.
