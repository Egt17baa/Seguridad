# Revisión de la tarea 8 (Clavero 2.0): copias a varios destinos con calendario

Base `196b155`, cabeza `7a389da` (la rama está en `b0a9fe3`, que solo cambia el registro: `git diff --stat 7a389da HEAD -- app/` vacío). Revisión de la tarea, sin subagentes y sin volver a lanzar la suite. Líneas citadas de `7a389da`.

Lentes: `experto-seguridad-cripto` (`SKILL.md`, «Trabajo conjunto con superpowers»: un hallazgo de seguridad confirmado es Important como mínimo), `ingeniero-movil-senior:desarrollo-movil-senior` y `ingeniero-movil-senior:auditoria-seguridad-movil` (MASVS: STORAGE, PLATFORM, CODE).

Ya decididos por el controlador, se confirman y no se vuelven a abrir: D-8 (texto del aviso: «Comprobaste una copia por última vez hace N días.»), D-12 (`vaultTag` propio al importar como bóveda nueva), D-1/D-2 (campos `importCopies` y `pastTargets`, revisados abajo) y D-3 (el calendario copia aunque no haya cambios).

### Spec Compliance

**Veredicto: cumple**, con las desviaciones ya aceptadas (D-1, D-2) y O-5 pendiente solo del texto de D-8.

| Requisito (brief y diseño «### Copias») | Estado | Evidencia |
|---|---|---|
| `BackupSchedule` y `BackupTargetSettings` con los campos del brief | Sí, más `importCopies` (D-1) | `vault/BackupSchedule.kt:13-41` |
| `due(now)`: días de calendario en la zona del móvil; 1, 7 y el mismo día del mes siguiente o el último | Sí | `BackupSchedule.kt:54-77` |
| `runDue(now)` al abrir la app y al desbloquear, una copia cada vez, en orden, sin WorkManager, servicios ni permisos | Sí | `AutoBackup.kt:415-435`, `:366` (colector de desbloqueo), `AppContainer.kt:470-472`, `MainActivity.kt:62` |
| `ON_CHANGE` como hoy | Sí | `AutoBackup.kt:392-407` |
| A6, rotación y copias de antes de importar y de antes de la 2.0, por destino y por bóveda; un destino solo borra lo de su lista | Sí, salvo los avisos de A6 con varios destinos (I-2) y el resultado con una instantánea vieja (I-3) | `AutoBackup.kt:643`, `:800-844` |
| Los ajustes de la 1.4.3 dan un destino `ON_CHANGE` | Sí | `SettingsStore.kt:186-219`, `:245-259` |
| Hasta 3 destinos, sin repetir carpeta | Sí | `Settings.kt` (`canAddTarget`), `SettingsStore.kt:146-153` |
| «Comprobar una copia» apunta la fecha solo con el `vaultTag` de la bóveda abierta | Sí, bajo el mutex y con la época del inicio (T7) | `BackupCheck.kt:128-129`, `VaultStore.kt:1078-1084`, `BackupCheckSession.kt:110-126` |
| `backupCheckNotice`: `null` hasta 30 días, «Aún no has comprobado…» con 0 | Sí; el texto de N días cambia por D-8 | `BackupCheck.kt:139-143` |
| Ajustes: 3 destinos con calendario y número de copias; «Las copias con calendario se hacen al abrir la app.» a la vista (O-4) | Sí | `SettingsScreen.kt:285-299` (tarjetas), la línea O-4 en la sección |
| O-5: la fecha es la de la comprobación; las copias sin `vaultTag` no cuentan, y el texto lo explica | Sí en Ajustes («Más información») y en la pantalla de comprobar; la línea corta queda ambigua hasta D-8 | `BackupCheckTexts.kt` (`BACKUP_CHECK_NOTICE_MORE`, `BACKUP_CHECK_NOT_COUNTED`) |
| Tests con nombre del brief | Están todos; `AutoBackupTargetsTest` se escribió después del motor (ver riesgo 6) | `BackupScheduleTest`, `AutoBackupTargetsTest`, `BackupCheckTest` |

### Strengths

- **El borrado es seguro por construcción.** `rotate` solo borra ids que están a la vez en la lista propia del destino, leída en ese momento (`AutoBackup.kt:643`, `:803-812`), y en el listado de esa carpeta (`:801-802`). Las listas de los destinos pasados (`pastTargets`) no las recorre nunca el motor, y cambiar de carpeta no lleva la lista vieja a la nueva (`Settings.kt:180-195`). Pasar a cada destino la unión de todas las listas (`listedCopies`, `MasterChanged`) no es un problema por esa intersección.
- **Aislamiento por bóveda.** Hay un `AutoBackup` por bóveda, con `targets` de su propio id (`AppContainer.kt:420`). `switchTo` bloquea la bóveda anterior antes de cambiar (`AppContainer.kt:509`), y los resultados tardíos de una bóveda borrada se descartan (`AppContainer.kt:807`, `:822`).
- **Nada cortado con nombre de copia:** se escribe primero como `.tmp` y se renombra al terminar; una cancelación borra el archivo con `NonCancellable` (`AutoBackup.kt` `copyTo`, `fill`, `discard`).
- **Migración:** la 1.4.3 y los objetos de `perVault` de las tareas 2 a 7 se convierten sin perder la lista, A6 con sus destinos y lo avisado, las copias de importar ni las de antes de la 2.0 (`SettingsStore.kt:186-219`, `:245-259`). Es idempotente: tras la primera escritura no quedan nombres viejos (comprobado en `oldSettingsGiveOneOnChangeTarget`). Con un JSON dañado, la conversión de un objeto falla sola y el resto se lee.
- **El calendario aguanta el reloj:** usa `LocalDate` en la zona leída en cada uso. Un reloj atrasado no repite la copia, y uno adelantado da como mucho una copia por destino y llamada, sin bucles (`BackupSchedule.kt:71-77`). Un `lastAt` extremo (`Long.MIN`/`MAX`) no lanza: o sale por `:74` o queda dentro del rango de `LocalDate`.
- **«Comprobar una copia» sigue la decisión (b):** el tag sale de los mismos datos comprobados (`BackupCheck.kt:115`), se compara bajo el mutex con `sinceLock`, un tag vacío no cuenta nunca y un informe con problemas no se apunta.
- **`lastError`** solo guarda textos fijos (`messageFor`, `AutoBackup.kt:846`), y `keep` se valida en la rotación (`:803`) y al leer (`SettingsStore.kt:146-153`).
- Las ayudas de test de la 1.x (`PrincipalSettings.kt`, `OneTargetBackup.kt`) llevan los tests antiguos al motor y a las funciones reales.

### Issues

#### Critical

Ninguno.

#### Important

**I-1. Volver a elegir una carpeta pierde su «Conservar las N», y la primera copia borra copias que el usuario quería conservar.**
- **Dónde:** `settings/Settings.kt:167-170`, `ui/screens/SettingsScreen.kt:235` y `:242`.
- **Qué pasa:** `withTargetAdded` recupera de `pastTargets` las listas de esa carpeta (`copies`, `importCopies`) pero no su `keep`, aunque `keptPast` lo guarda (`Settings.kt:202`). Ajustes llama a `withTargetAdded(chosen, ON_CHANGE)` con el `keep` por defecto, 5, y enseguida a `request(chosen)`, así que la rotación corre con 5 sobre una lista que puede tener 10 copias. En la 1.4.3, `autoBackupKeep` sobrevivía a desactivar la copia y volver a elegir la misma carpeta.
- **A quién afecta:** a quien actualiza desde la 1.4.3 con la copia desactivada y «Conservar las 10», y a quien quita una carpeta en la 2.0 y la vuelve a añadir. En los dos casos se borran hasta 6 copias antiguas antes de que pueda tocar el número.
- **Por qué no lo vio la suite:** la ayuda de test `withFolderChosen` (`PrincipalSettings.kt:112`) pasa el `keep` antiguo a mano.
- **Corrección:**
  - `val added = BackupTargetSettings(uri, past?.keep?.takeIf { it in AUTO_BACKUP_KEEP_CHOICES } ?: keep, schedule, …)`, o que Ajustes pase `past.keep`.
  - Un test: quitar un destino con `keep = 10` y 10 copias, volver a añadirlo con la misma carpeta, y tras la copia siguen 10. También desde los ajustes de la 1.4.3 con la copia desactivada.

**I-2. A6 con varios destinos no cumple «cada fallo distinto se avisa una vez».**
- **Dónde:** `vault/AutoBackup.kt:441-450` y `:684-686`.
- **Qué pasa:**
  - `attempt` decide `toldKey` para cada destino comparándolo solo con el `replaceTold` de ese destino, y lo guarda como avisado (`onResult(recorded)`).
  - `tell()` solo enseña el primer `toldKey` (`:447`, `:450`). Si el destino 1 falla con un error y el destino 2 con otro, el diálogo enseña el primero y el segundo queda apuntado como avisado sin que se haya enseñado nunca.
  - Al revés, el mismo texto puede avisarse una vez por destino, hasta 3 veces por petición.
- **Impacto:** después de cambiar la maestra (A6, de seguridad: copias con la contraseña antigua), el usuario no se entera en el diálogo de un fallo distinto en otra carpeta. Solo lo vería en la tarjeta de esa carpeta en Ajustes. Es una regla vinculante de `CLAUDE.md`.
- **Corrección:**
  - Que lo avisado sea por petición, la unión de los `replaceTold` de los destinos con petición. Un texto ya avisado en cualquier destino no se vuelve a avisar.
  - Que `tell()` enseñe todos los textos nuevos distintos (unidos en un solo resultado, uno por línea), o que solo se apunte como avisado el texto que se enseñó y los demás queden para la próxima vez.
  - Un test con dos destinos que fallan con textos distintos: los dos se enseñan, y cada uno una sola vez.

**I-3. Una instantánea vieja de los destinos puede dar «se borraron las copias anteriores» sin haber borrado nada (A6 y un «Importar copia» a la vez).**
- **Dónde:**
  - `vault/AutoBackup.kt:396/400`, `:419/420` y `:493`: los bucles leen `targets()` una vez y toman de esa instantánea `replacePending` y `replaceTargets`. El KDoc de la clase dice que los destinos se leen «de nuevo antes de cada copia».
  - `:648`, `:839`: con `replace.targets` no nulo, la propia lista vacía y nada que borrar, `done = true` y el resultado es `replaced = true`.
- **Escenario:**
  - Un A6 pendiente en dos destinos se hace al desbloquear (`runDue`), o el usuario pulsa «Borrar las copias…» (`replaceNow`).
  - Mientras se escribe la copia del destino 1 entra «Importar copia». `forgetAutoBackupCopiesForImport` vacía las listas y la petición, y no toma el `lock` de `AutoBackup`.
  - El destino 1 sale como `stale`, que está bien. El destino 2 empieza con la `generation` nueva y con la petición de la instantánea. Su lista propia ya está vacía, así que no borra nada y aun así devuelve `replaced = true`. `tell()` o `replaceNow` dicen «Se hizo una copia nueva… y se borraron las copias… hechas antes del cambio de contraseña».
  - Esas copias, cifradas con la contraseña antigua, siguen en la carpeta, y además la pantalla de importar ya avisó de que la petición se había quitado.
  - En la 1.x, con un solo destino, la ventana era de microsegundos. Ahora dura toda la escritura de la copia del destino anterior.
- **Severidad:** Important por la regla de veracidad y porque es de seguridad: el usuario cree borradas unas copias que se abren con una maestra quizá comprometida. La probabilidad es baja.
- **Corrección:**
  - Los bucles solo toman de la instantánea el `uri` (y el orden). Dentro de `attempt`, ya con el `lock`, se vuelve a leer `target(uri)`.
  - Si ya no tiene `replacePending` (y no es un `replaceNow` nuevo), se hace una copia normal sin `Replace`. Si lo tiene, se usan su `replaceTargets` de ese momento.
  - `replaced` solo cuando la petición sigue guardada al terminar, o cuando se borró al menos un destino de la petición o ya no quedaba ninguno de la lista leída al empezar.
  - El calendario (`due`) también se evalúa con lo leído entonces.
  - Un test: dos destinos con petición, un import (`forgetAutoBackupCopiesForImport` más el cambio de `generation`) durante la escritura del primero, y que no salga «replaced» ni se publique el éxito.

#### Minor (todos se arreglan en esta ronda)

**M-1. El aviso del calendario puede sobrevivir a un bloqueo** (`AutoBackup.kt:434` frente a `:373`).
- Se mira `Unlocked` y después se hace `scheduled.post` fuera de cualquier exclusión con el colector del bloqueo. Si el bloqueo cae entre las dos cosas, y el colector corre antes del `post`, el aviso queda guardado y sale tras el siguiente desbloqueo, o tras volver a esa bóveda.
- Corrección: apuntar `store.lockEpoch` al empezar `runDue` y guardar el aviso junto con esa época. El colector borra todo aviso de una época anterior, y la pestaña solo lo enseña si la época sigue siendo la actual. Otra opción es comprobar la época dentro de `Notice.post` con el mismo cerrojo que `shown`.
- Test: un bloqueo inyectado entre la comprobación y el `post`.

**M-2. `appOpened` lanza `runDue` en `appScope`, no en el `scope` de la bóveda** (`AppContainer.kt:470-472`).
- El KDoc de `runDue` dice «cancelled with this object's scope», y no es así: al borrar la bóveda (`AppContainer.kt:606`, `scope.cancel()`) la ejecución sigue con el `lock` tomado.
- Corrección: un `fun launchDue(now)` en `AutoBackup` que haga `scope.launch { runDue(now) }`.

**M-3. Sin espera entre intentos para un destino que falla.**
- `MainActivity.onStart` (`:62`) llama a `runDue` cada vez que la app vuelve a primer plano: tras compartir, tras el selector, tras otra app.
- Un destino con calendario cuya copia falla (`lastAt` no avanza) o un `ON_CHANGE` con `missed` vuelve a exportar la bóveda entera (hasta 24 MiB más los adjuntos) en cada vuelta. Gasta CPU, E/S y batería y no sirve de nada.
- Corrección: recordar en memoria el fallo de cada destino y no volver a intentarlo hasta el siguiente desbloqueo, el siguiente cambio guardado o pasados unos minutos. A6 sigue igual, porque su reintento ya está prometido en el texto.

**M-4. Copia doble tras un cambio** (`AutoBackup.kt:395`, `:604`).
- Si el usuario guarda y sale y vuelve en menos de `AUTO_BACKUP_DEBOUNCE_MS`, `onStart` hace la copia (`missed`: la fecha del archivo es posterior a `lastAt`) y después el *debounce* hace otra igual (`changed` sigue a `true`). La rotación pierde una versión antigua distinta.
- Corrección: en `runChanged`, saltar un destino `ON_CHANGE` cuyo `lastAt >= lastChangeSavedAt()`.

**M-5. El diálogo tras cambiar la maestra mezcla destinos que no tienen la petición** (`Settings.kt:144-150`, `AppNavHost.kt:260`).
- `firstBackupError` puede ser el error de una copia normal posterior de un destino que ya terminó su A6, y el diálogo lo enseña como fallo del reemplazo (rama `pending && lastError != null` de `replaceStatus`).
- Corrección: calcular `lastError`, `lastBackupAt` y `toldSinceRequest` solo sobre los destinos con `replacePending`, o sobre los que la tenían al pedirla.

**M-6. Un `lastCheckedBackupAt` en el futuro apaga el aviso de los 30 días** (`BackupCheck.kt:139-143`).
- Si se comprobó con el reloj adelantado (por ejemplo, a 2030), el aviso no sale hasta 30 días después de esa fecha: años.
- Corrección: como D-5 del calendario, una fecha a más de un día en el futuro no es creíble y el aviso sale.
- De paso, la `remember(lastCheckedBackupAt)` de `BackupCheckNoticeLine` (`VaultBackup.kt:268-273`) no se recalcula al cambiar el día con la pantalla abierta: hay que incluir el día en la clave.

**M-7. `saneVault` tira las listas de los destinos que sobran** (`SettingsStore.kt:146-153`).
- Con un cuarto destino o una carpeta repetida, `distinctBy().take(3)` descarta sus listas sin pasarlas a `pastTargets`. Es la dirección segura (las copias quedan sin apuntar y nunca se borran), pero contradice «nada se pierde».
- Corrección: pasar sus `copies` e `importCopies` a `pastTargets` con `keptPast`.

**M-8. Huecos de test (riesgo 6).** Con estos cambios, la suite no detectaría regresiones reales:
- (a) **A6 en una carpeta compartida.** `a6PerTarget` usa dos `Folder` separados (`AutoBackupTargetsTest.kt:203-204`), así que un `rotate` que borrara `doomed ∩ listado` sin cruzarlo con la lista propia seguiría en verde. Hace falta A6 con dos destinos sobre la misma carpeta y comprobar que cada uno solo borra lo suyo.
- (b) **Cambiar o quitar una carpeta, en el motor:** tras «Cambiar de carpeta» a otra, la carpeta vieja no se toca (ni la rotación ni A6), y un destino quitado con copias apuntadas no pierde ninguna.
- (c) Un fallo en un destino durante un A6 con varios (I-2).
- (d) Volver a añadir con el `keep` anterior (I-1).
- (e) Un import durante A6 con varios destinos (I-3).
- (f) Un día de cambio de hora en `due`: Europe/Madrid 28-03-2027 y 31-10-2027, a las 02:30 y a las 23:59.
- (g) Un objeto de `perVault` de la 1.4.3 con tipos malos (por ejemplo, `autoBackupCopies: "x"`): no se pierden las otras bóvedas ni el resto de ajustes.
- (h) El aviso del calendario con un bloqueo en medio (M-1).

Sobre la desviación de TDD: las mutaciones del implementador (4 de 6 en rojo) cubren la rotación por lista propia y que el calendario no copie con los cambios. No cubren lo de arriba.

**M-9. Textos atados a D-8** (`BackupCheckTexts.kt`: `BACKUP_CHECK_NOT_COUNTED`, `BACKUP_CHECK_NOTICE_MORE`, y los KDoc de `VaultBackup.kt:262`, `VaultScreen.kt` y `BackupCheckScreen.kt` que citan «Tu última copia comprobada…»).
- Hay que ajustarlos al texto nuevo de D-8 para que no vuelva la expresión «la última copia comprobada». Por ejemplo, `BACKUP_CHECK_NOT_COUNTED`: «Esta comprobación no cuenta para el aviso de «Comprobaste una copia por última vez»: la copia es de otra bóveda o de antes de la 2.0.».
- Pasa por el Abogado (abajo).

#### Informativo (sin acción)

- `recordBackupChecked` (`VaultStore.kt:1078-1084`): `if (saved) true else own` es correcto, porque `fittedForSave` no devuelve `null` y una escritura de contabilidad fallida se queda en memoria como las demás (`commit(mustSave = false)`).
- El mensual se ancla al día en que se hizo la copia: tras un 31 de enero, el 28 de febrero y después el 28 de cada mes. Es lo que pide el brief («mismo día del mes siguiente»).
- `request(uri)` en la `AutoBackup` de otra bóveda (Ajustes abierto durante un cambio de bóveda) deja ese `uri` en `asked` sin efecto. Es inofensivo.
- `settings.json` se lee sin tope de tamaño (`SettingsStore.load`): ya era así en la 1.x, y es un archivo privado de la app.

### Assessment

**Calidad: hay que arreglarla antes de seguir.** El motor es sólido donde más importa: borrar solo lo propio, por id y en la carpeta del destino; aislar cada bóveda; no dejar copias cortadas con nombre de copia; y que el reloj no provoque tormentas de copias. Los tres Important están en los bordes de varios destinos: el `keep` que se pierde al volver a elegir una carpeta (I-1), los avisos de A6 (I-2) y una instantánea vieja frente a una importación (I-3). Los tres tienen una corrección pequeña y concreta, y la suite no los ve (M-8).

**Veredicto por riesgo:**
1. **Migración:** correcta. No se pierde nada de la 1.4.3 ni de las tareas 2 a 7, es idempotente, y un JSON hostil no rompe la app (los objetos que no se leen se ignoran y el archivo ilegible da los valores por defecto, como en la 1.x). Excepciones: I-1, porque el `keep` de una carpeta pasada no vuelve, y M-7.
2. **Bloqueo o cambio de bóveda en `runDue`:** nunca se escribe una copia de A en un destino de B, y un bloqueo para los destinos siguientes. Una cancelación no deja un archivo a medias con nombre válido. Quedan M-1 (aviso tras un bloqueo), M-2 y, frente a una importación, I-3.
3. **Calendario:** correcto (días de calendario en la zona del móvil, el reloj atrasado no repite, el adelantado no causa tormentas y como mucho espera unos dos periodos, D-5). El cambio de hora es correcto por `LocalDate`, pero no tiene test (M-8 f). Fin de mes bien.
4. **Borrado:** correcto. Un destino solo borra ids de su lista leída en el momento y presentes en su carpeta; una URI cambiada nunca usa la lista vieja; `pastTargets` no provoca borrados en una carpeta quitada, y solo vuelve a rotar si el usuario la elige otra vez, con el `keep` mal (I-1). Falta el test en el motor (M-8 a, b).
5. **`lastError` y `keep`:** correcto. Solo textos fijos, y `keep` validado al rotar y al leer.
6. **Tests:** atrapan la rotación cruzada y el calendario en los cambios, pero no A6 en una carpeta compartida, el cambio de carpeta en el motor, ni I-1, I-2 o I-3 (M-8).

**Recuento:** 0 Critical, 3 Important, 9 Minor.

## Revisión experto-seguridad-cripto

Lista de `SKILL.md` y la sección «Trabajo conjunto» aplicadas al diff.

**Modelo de amenaza:**
- **Activos:** las copias cifradas con la maestra de su momento (A6: las de una maestra anterior), la lista de lo que la app puede borrar y la fecha de la última comprobación.
- **Actores:** otra app con acceso a la carpeta, un reloj manipulado, un `settings.json` dañado y la carrera entre un bloqueo, un cambio de bóveda y una importación.
- **Fuera de alcance:** lo que otra app haga en la carpeta compartida.

**Hallazgos:**
- Sin criptografía nueva: las copias siguen siendo `VaultStore.exportBackup`, y `vaultTag` solo se compara (`BackupCheck.kt:128-129`), dentro de datos autenticados por la maestra. No se puede fabricar para dar por comprobada una copia ajena.
- **Borrado (CWE-284/CWE-73):** solo por id de la lista propia y presente en el listado (`AutoBackup.kt:643`, `:800-812`). Nunca por nombre, salvo los `.tmp` con el patrón exacto (`:807`). Correcto.
- **I-3:** un texto falso de borrado de las copias con la maestra antigua cuando una importación cae en medio de un A6 con varios destinos. Es de seguridad: el usuario cree retiradas unas copias que se abren con una contraseña quizá comprometida. **Important.**
- **I-2:** un fallo de A6 en otra carpeta queda apuntado como avisado sin enseñarse (`AutoBackup.kt:441-450`, `:684`). **Important.**
- **I-1:** borrado de copias propias de más contra la elección guardada del usuario (`Settings.kt:167-170`). Afecta a la disponibilidad de las copias. **Important.**
- **Bloqueo y época:** `recordBackupChecked` respeta `sinceLock` (`VaultStore.kt:1078`, `BackupCheckSession.kt:114`), y `runDue` no escribe nada con la bóveda cerrada (`AutoBackup.kt:426`). El aviso puede escaparse del bloqueo (M-1), aunque no tiene datos sensibles.
- **Reloj:** `BackupSchedule.kt:71-77` no da tormentas ni salta para siempre. Una comprobación con el reloj adelantado apaga el recordatorio (M-6).
- **Entrada hostil (CWE-502):** la conversión de ajustes usa clases tipadas de kotlinx.serialization (`SettingsStore.kt:186`, `:245-259`) y captura `IllegalArgumentException` (`:254`), sin tipos polimórficos del archivo. Los límites: 3 destinos (`:146-153`) y 6 `pastTargets`.
- **Registros y datos:** `lastError` solo con textos fijos (`AutoBackup.kt:846`); ningún registro nuevo; los ajustes no guardan secretos.

## Revisión Ingeniero Móvil Senior

- **Ciclo de vida:** `runDue` va en `MainActivity.onStart` (`MainActivity.kt:62`) y en el colector del desbloqueo (`AutoBackup.kt:366`), sin WorkManager ni servicios. Lo cumple. Pero `onStart` es frecuente: hace falta esperar tras un fallo (M-3) y evitar la copia doble del *debounce* (M-4).
- **Concurrencia:**
  - Un `Mutex` por bóveda serializa todas las copias (`AutoBackup.kt:359`). Correcto.
  - Los bucles iteran una instantánea mientras las importaciones escriben los ajustes sin ese `Mutex` (I-3).
  - `appOpened` usa `appScope` y no el `scope` de la bóveda (`AppContainer.kt:470-472`, M-2).
- **SAF:**
  - El permiso persistente se toma después de rechazar una carpeta repetida o una cuarta, y se devuelve con `releaseIfUnused` mirando los destinos de todas las bóvedas (`AppContainer.kt:740`).
  - Volver a añadir una carpeta pierde su `keep` (`SettingsScreen.kt:235`, I-1).
- **Estado de la UI:** `rememberSaveable` solo con la URI de la carpeta que se cambia (`SettingsScreen.kt:198`). No es secreto: se guarda en claro en los ajustes, y se acepta (D-13).
- **Compose:**
  - La línea de la bóveda va encima de la lista con `weight(1f)` (`VaultScreen.kt:286`).
  - El *snackbar* del calendario se marca como visto antes de enseñarse (`VaultScreen.kt:297`).
  - `BackupCheckNoticeLine` no se recalcula al cambiar el día (M-6).
- **Rendimiento:** con 3 destinos, `runDue` hace 3 exportaciones seguidas con el `lock` tomado, y la copia de antes de importar espera a que terminen. Es aceptable, pero con fallos repetidos se multiplica (M-3).
- **Zona horaria:** se lee en cada uso (`AutoBackup.kt` `zone()`), así que un viaje o un cambio de zona da a lo sumo una copia unas horas antes o después.

## Textos para el Abogado Digital (revisión final)

- **D-8 (decidido):** `backupCheckDaysAgo` pasa a «Comprobaste una copia por última vez hace N días.».
- **Textos que dependen de D-8 y hay que alinear (M-9):** `BACKUP_CHECK_NOT_COUNTED` («Esta comprobación no cuenta como la última copia comprobada: la copia es de otra bóveda o de antes de la 2.0.») y `BACKUP_CHECK_NOTICE_MORE` («Cuenta desde el día en que comprobaste con «Comprobar una copia» una copia de esta bóveda, no desde el día en que se hizo la copia. Las copias de antes de la 2.0 y las de otra bóveda no cuentan.»).
- **`BACKUP_CHECK_NEVER`:** «Aún no has comprobado ninguna copia.». Sale desde el primer día en la lista de la bóveda (D-9), sin «Más información» allí: que el Abogado valore si basta.
- **O-4:** `AUTO_BACKUP_SCHEDULE_ON_OPEN`, «Las copias con calendario se hacen al abrir la app.», a la vista; y `AUTO_BACKUP_MORE` («…se hacen cuando abres la app o la bóveda, y si no la abres no hay copia…»).
- **`AUTO_BACKUP_SHORT`:** «Guarda una copia cifrada en las carpetas que elijas, tras cada cambio o con calendario.».
- **«Más información» de la copia automática:** añade «…y la de antes de la 2.0 (seguridad-antes-de-2-0-…)… Cada carpeta solo borra las copias que la app hizo en ella.».
- **Botones y avisos:** `AUTO_BACKUP_ADD_FOLDER`, `AUTO_BACKUP_ALREADY_TARGET`, `AUTO_BACKUP_TOO_MANY`, `AUTO_BACKUP_REMOVED` («Ya no se harán copias en esa carpeta. Las copias ya hechas siguen en ella.») y `autoBackupTargetTitle`.
- **Calendarios:** las etiquetas «Tras cada cambio», «Cada día», «Cada semana» y «Cada mes», y los finales de «Copia automática activada: se hará una ahora y después …».
- **`scheduledCopiesText`:** «Se hizo la copia con calendario.» y «Se hicieron N copias con calendario.».
- **`AUTO_BACKUP_REPLACED_SEVERAL` y `autoBackupUnlistedText(…, severalFolders = true)`:** su veracidad depende de I-3, que hoy puede afirmar un borrado que no se hizo.
- **Para la política (tarea 12):**
  - Hasta 3 carpetas con calendario, solo al abrir la app y sin segundo plano.
  - La bóveda guarda cifrada la fecha de la última comprobación.
  - Los ajustes guardan sin cifrar las carpetas, su calendario, cuántas copias se conservan y los identificadores de las copias que la app hizo, también los de carpetas ya quitadas (`pastTargets`, como mucho 6) para seguir rotándolas si se vuelven a elegir.
  - A6 borra en cada carpeta solo las copias que la app apuntó allí, y no las de una carpeta quitada.
