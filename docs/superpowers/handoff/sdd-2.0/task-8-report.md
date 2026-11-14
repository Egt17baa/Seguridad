# Tarea 8: copias a varios destinos con calendario y aviso de la última copia comprobada

**Estado:** DONE_WITH_CONCERNS (ver «Dudas»). Commit final «2.0, tarea 8: copias a varios destinos». Rama `ccr-0aa27f95-u038cd`, desde `65f89fa` (el primer intento se perdió sin commits). No se ha hecho `push`.

## Lo hecho

### Modelo (`vault/BackupSchedule.kt`, `settings/Settings.kt`, `settings/SettingsStore.kt`)
- `enum class BackupSchedule { ON_CHANGE, DAILY, WEEKLY, MONTHLY }` y `@Serializable data class BackupTargetSettings(uri, keep, schedule, lastAt, lastError, copies, replacePending, replaceTargets, replaceTold)` con los nombres del brief, **más** `importCopies` (ver Dudas, D-1).
- `fun BackupTargetSettings.due(now, zone = systemDefault())`: días de calendario en la zona del móvil; diaria +1 día, semanal +7, mensual `plusMonths(1)` (31-ene → 28/29-feb, 31-mar → 30-abr); `ON_CHANGE` nunca por calendario; sin copia, siempre.
  - **Reloj atrasado:** `now` antes del día siguiente → no hay copia (`clockBackDoesNotRepeat`).
  - **Reloj adelantado** (copia hecha en 2030 y reloj corregido): si `now` está más de un periodo entero (1, 7 o 31 días) por detrás de `lastAt`, se da por mala la marca: **una** copia, y su `lastAt` reinicia el calendario desde la fecha real. Nunca más de una por destino y llamada; nunca bucles.
- `VaultSettings.targets: List<BackupTargetSettings>` (≤ `VaultLimits.BACKUP_TARGETS_MAX`, nunca una carpeta dos veces), `autoBackupOffForNewVault`, y `pastTargets` (≤ 6): las listas de una carpeta que dejó de ser destino (quitada o cambiada), como la 1.4.3 guardaba su lista al desactivar; la misma carpeta elegida otra vez sigue rotándolas. Una bóveda nueva o importada las olvida (`forgettingAutoBackupCopies`, `turnOffAutoBackupForNewVault`).
- Operaciones: `withTargetAdded`, `withTargetFolderChanged` (misma carpeta: conserva listas y A6, borra fecha y error como la 1.4.3; otra: listas de esa carpeta si las había, sin A6), `withTargetRemoved`, `canAddTarget` (`TOO_MANY`, `ALREADY_A_TARGET`), `withAutoBackupResult` (por `r.folder`), `BackupTargetSettings.withResult`, `withImportCopy`, agregados para el diálogo de A6 (`replacePending`, `firstBackupError`, `oldestBackupAt`, `replaceToldAny`, `listedCopies`, `backupFolders`).
- **Migración:** `LegacyVaultSettings` (congelada, con los nombres de la 1.4.3) → `converted()`: la carpeta elegida pasa a un destino `ON_CHANGE` con su `keep`, fecha, error, listas de esa carpeta y su petición A6 (con `replaceTargets` nulo de la 1.2.x incluido); las listas de otra carpeta (copias desactivadas, otra carpeta elegida) van a `pastTargets`. `decodeSettings` la aplica a los campos de arriba (1.4.3) y a los objetos de `perVault` escritos por las compilaciones de las tareas 2 a 7 (`appJson` ignora claves desconocidas: sin esto se perdían en silencio). `sane` recorta a 3 destinos, quita repetidos y arregla `keep`.
- `SettingsStore`: `turnOffAutoBackupForNewVault` devuelve la lista de carpetas; `removeTarget(id, uri)` sustituye a `turnOffAutoBackup` (dice si quitó una petición A6 en la misma escritura, T11-c); `forgetAutoBackupCopiesForImport` deshace por destino.

### Motor (`vault/AutoBackup.kt`)
- Un `AutoBackup` por bóveda (como en la tarea 2), con `targets: () -> List<BackupTargetSettings>` leído de nuevo antes de cada copia; un solo `Mutex`: una copia cada vez, en el orden de los destinos, en el `scope` de la bóveda (`Dispatchers.IO`), cancelable con él.
- `ON_CHANGE`: igual que la 1.x (debounce tras cada cambio; al desbloquear si se perdió una). Los destinos con calendario solo copian en `runDue(now)`. `request(folder)` (carpeta recién elegida, «Hacer una copia ahora») copia ese destino sea cual sea su calendario.
- `runDue(now)`: lo llama el propio objeto al desbloquear y `AppContainer.appOpened()` desde `MainActivity.onStart` («al abrir la app»). Con la bóveda bloqueada no escribe ni apunta nada: el desbloqueo vuelve a evaluar el calendario, A6 y lo perdido con lo guardado entonces (así un reloj corregido entre medias no da una copia de más). La zona horaria se lee en cada uso (`zone()`), no una vez al construir. Un bloqueo a mitad: la copia ya empezada (instantánea de antes) termina en **su** carpeta, y los destinos siguientes esperan al desbloqueo.
- A6 por destino: `replaceOldCopies(ids)` hace la copia y la rotación en cada destino; cada uno solo borra ids de **sus** listas (`copies`, `importCopies`); bloqueada, cada destino guarda su petición; al desbloquear, `runDue` la hace aunque su calendario no toque. Con un destino, el resultado es exactamente el de antes; con varios, uno combinado (`replaced` solo si todos; texto `AUTO_BACKUP_REPLACED_SEVERAL`).
- Copias de antes de importar y de antes de la 2.0: en el primer destino que la acepte, en orden; un bloqueo o una bóveda cambiada cortan; su id va a la lista de ese destino.
- `scheduledNotice`: «Se hizo la copia con calendario.» / «Se hicieron N copias con calendario.», se borra en cada bloqueo y solo se publica con la bóveda abierta; la enseña la pestaña Bóveda como snackbar, una vez.
- `lastError` solo recibe textos fijos (`messageFor`), nunca rutas, URI ni mensajes de excepción (sin cambios respecto a 1.x).

### «Comprobar una copia» (`vault/BackupCheck.kt`, `vault/BackupCheckSession.kt`, `VaultStore.recordBackupChecked`)
- `BackupCheckReport.vaultTag`: el de los mismos datos comprobados (decisión (b)).
- `BackupCheckRuns(..., record)`: solo un informe **sin problemas**, y solo si la ejecución sigue siendo la actual, se pasa a `record(report, epochDelInicio)`; `session.counted` es `true` (contó), `false` (la copia es de otra bóveda o sin tag) o `null` (no se pudo comparar: bóveda cerrada, paso a v4 pendiente, bloqueo desde el inicio, o informe con problemas). La línea de O-5 solo sale con `false`, nunca con la bóveda cerrada.
- `VaultStore.recordBackupChecked(tag, sinceLock): Boolean?`: compara bajo el mutex con el `vaultTag` de la bóveda abierta (`withBackupChecked`; vacío nunca cuenta), guardado de contabilidad (sin versión, sin subir `revision`, sin copia automática); `null` si no hay nada que comparar (cerrada, bloqueo desde `sinceLock`).
- `backupCheckNotice(last, now)`: `null` antes de 30 días; con 0, «Aún no has comprobado ninguna copia.»; desde 30 días, «Comprobaste una copia por última vez hace N días.» (D-8; antes, el texto del brief); con el reloj por detrás, `null`.
- La pantalla dice, si la copia sin problemas no cuenta, `BACKUP_CHECK_NOT_COUNTED` (O-5).

### Interfaz
- Ajustes, «Copia automática» (`SectionCard` vía `Section`): resumen, lo que borra, **a la vista** «Las copias con calendario se hacen al abrir la app.» (O-4), «Más información», y «Elegir carpeta» / «Añadir otra carpeta» hasta 3. Una tarjeta por carpeta: su calendario (4 opciones), «Conservar las N últimas», última copia y error, «Hacer una copia ahora», «Cambiar de carpeta», «Quitar esta carpeta» (o «Desactivar» si es la única). La misma carpeta dos veces o una cuarta se rechazan antes de pedir el permiso. Los permisos se devuelven con `releaseIfUnused` solo si ninguna bóveda la usa.
- «Copia de la bóveda» en Ajustes y la pestaña Bóveda: una línea con `backupCheckNotice` (en Ajustes con «Más información» que explica O-5).
- `AppNavHost`: el diálogo de A6 lee los agregados de todos los destinos; el texto de éxito dice «en cada carpeta» con varias.

### Maestro
- Ningún flujo de `.maestro/` toca los botones de «Copia automática» (solo comentarios de P-6/P-7, que siguen valiendo). No se cambió nada; los flujos nuevos van a la tarea 11. No se ejecuta nada aquí.

## Tests y TDD
- **RED por aserción (7089212):** `BackupScheduleTest` (`dailyWeeklyMonthly`, `monthlyOnShortMonth`, `clockBackDoesNotRepeat`) y `BackupCheckTest` (`checkRecordsDateOnlyForOwnTag`, `noticeAfter30Days`, y `goodBackupReports` con el tag) contra *stubs* (`due` = false, `backupCheckNotice` = null, `recordBackupChecked` = false, tag vacío): 6 fallos por aserción; después verdes.
- **`AutoBackupTargetsTest`** (`threeTargetsMax`, `eachTargetRotatesOnlyItsOwn`, `a6PerTarget`, `oldSettingsGiveOneOnChangeTarget`, `noCopyOfAnotherVault` y `calendarTargetsCopyOnlyWhenDue`): **desviación**: escritos después del motor (el primer intento se perdió y el motor se rehízo antes). Para que no sean tests vacíos, se comprobaron con mutaciones del motor (copiar destinos con calendario en cada cambio, rotar con las listas de todos los destinos): 4 de 6 en rojo; revertido.
- `BackupCheckSessionTest.onlyAGoodReportIsRecordedWithTheEpochOfItsStart`.
- Tests de la 1.x: los de un solo destino (`AutoBackupTest`, `AutoBackupF3Test`, `AutoBackupImportCopyTest`, `SettingsTest`, `PerVaultSettingsTest`, `AppContainer*Test`) siguen probando el motor real con un destino `ON_CHANGE`, mediante ayudas **solo de test**: `vault/OneTargetBackup.kt` (`oneTargetBackup`, `requestOne`) y `settings/PrincipalSettings.kt` (vista 1.4.3 de un destino por la misma `converted()`). Cambiaron de semántica, con su aserción nueva: `turnOffAutoBackupForNewVault` devuelve lista; sin destino no hay `keep` que conservar.
- `PlatformSourcesTest`: nueva fijación de que «Comprobar una copia» solo escribe con `store.recordBackupChecked(report.vaultTag, since)`; inicio del selector de carpetas.
- Anexo de textos (`docs/superpowers/specs/2026-10-27-seguridad-1.3.0-textos.md`): `AUTO_BACKUP_SHORT` al texto nuevo. Los textos nuevos de la 2.0 van en `vault/BackupTargetTexts.kt` y `BackupCheckTexts.kt` (como las tareas 2 a 7, fuera de `ALL_TOOL_TEXTS`).
- **Suite completa, en el commit final:** `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets` en verde, **app 2196** tests (antes 2184); `./gradlew -p jvm-check test` **2159** (antes 2147), sin fallos; lint 0 errores / 20 avisos (los mismos; uno nuevo de `ModifierParameter` se arregló).

## Archivos
- Nuevos: `app/src/main/java/app/seguridad/vault/BackupSchedule.kt`, `vault/BackupTargetTexts.kt`; tests `vault/BackupScheduleTest.kt`, `vault/AutoBackupTargetsTest.kt`, `vault/OneTargetBackup.kt`.
- Cambiados: `vault/AutoBackup.kt`, `vault/BackupCheck.kt`, `vault/BackupCheckSession.kt`, `vault/BackupCheckTexts.kt`, `vault/VaultStore.kt`, `vault/MasterChanged.kt` (KDoc), `settings/Settings.kt`, `settings/SettingsStore.kt`, `AppContainer.kt`, `MainActivity.kt`, `tools/ToolTexts.kt`, `ui/AppNavHost.kt`, `ui/screens/SettingsScreen.kt`, `ui/screens/VaultBackup.kt`, `ui/screens/VaultScreen.kt`, `ui/screens/BackupCheckScreen.kt`; tests de la 1.x listados arriba, `BackupCheckTest.kt`, `BackupCheckSessionTest.kt`, `PlatformSourcesTest.kt`; el anexo de textos.

## Dudas (decididas; el controlador puede cambiarlas)
- **D-1, `importCopies` por destino:** el brief da los campos «verbatim» sin ella, pero A6 «por destino» (R15) necesita saber qué copias de antes de importar y de antes de la 2.0 hay en cada carpeta, como la 1.4.3 lo sabía para la suya. Se añadió con valor por defecto.
- **D-2, `pastTargets`:** sin él, la migración perdía la lista de una 1.4.3 con las copias desactivadas (o con las copias de importar en otra carpeta), y quitar una carpeta y volver a elegirla dejaba sus copias sin rotar. Acotado a 6, se olvida con bóveda nueva o importada.
- **D-3, calendario sin cambios:** se sigue el brief: un destino diario copia cada día aunque la bóveda no haya cambiado, y con «Conservar las 3» puede apartar versiones distintas más antiguas. Alternativa (saltar si no hubo cambio desde su última copia), si el dueño la prefiere.
- **D-4, la primera copia de un destino con calendario:** al elegir la carpeta se hace una enseguida (`request`), y el calendario cuenta desde ahí.
- **D-5, reloj adelantado:** con `lastAt` más de un periodo en el futuro, una copia (no se pierde el calendario durante años); con menos de un periodo, se espera al día que toca (hasta ~2 periodos sin copia).
- **D-6, 30 días:** a los 30 días exactos ya sale el aviso («si pasan 30 días»); `null` hasta entonces.
- **D-7, una copia con problemas no cuenta** como comprobada, aunque sea de esta bóveda.
- **D-8, O-5 y el texto del brief:** decidido por el controlador (ronda 1): «Comprobaste una copia por última vez hace N días.», en lugar del texto del brief, que podía leerse como la fecha de la copia.
- **D-9, una bóveda nueva sin comprobaciones** ve «Aún no has comprobado ninguna copia.» desde el primer día (lo pide el brief para 0).
- **D-12, «Importar como bóveda nueva» conserva el `vaultTag` de la copia** (`Vault.kt:482`): la bóveda nueva y la de origen comparten tag, así que comprobar una copia de una cuenta para las dos. Se acepta: su contenido sale de la misma bóveda; si molesta, la importación como bóveda nueva daría un tag nuevo.
- **D-13, `rememberSaveable` con la URI de la carpeta que se cambia** (`SettingsScreen.kt:198`): no es un secreto (los ajustes la guardan en claro) y evita que, tras recrear la actividad con el selector abierto, «Cambiar de carpeta» se tome por «Añadir».
- **D-10, bloqueo a mitad de la copia de un destino:** la copia ya empezada (instantánea de antes del bloqueo) termina en su propia carpeta, como en 1.x; los destinos siguientes esperan.
- **D-11, `request` sin destino** (de la 1.x) ya no existe: cada botón pide la copia de su carpeta.

## Pruebas en el móvil (pendientes, tarea 11 o el dueño)
- Elegir 3 carpetas (y una cuarta rechazada, la misma dos veces rechazada); cambiar el calendario de cada una; comprobar que una diaria copia al abrir la app al día siguiente y no dos veces el mismo día; mover el reloj hacia atrás y hacia delante.
- Cambiar la maestra con 2 carpetas: diálogo, «Borrar las copias anteriores», el texto «en cada carpeta».
- «Comprobar una copia» de esta bóveda (cuenta), de otra (línea O-5) y de antes de la 2.0; el aviso de 30 días en la pestaña Bóveda y en Ajustes (cambiando la fecha del móvil).
- Snackbar «Se hizo la copia con calendario.» al desbloquear, y que no sale tras bloquear.
- Actualizar desde la 1.4.3 con copia automática activa: un destino «Tras cada cambio» con sus copias, que sigue rotando.

## Revisión experto-seguridad-cripto
Recorridos `SKILL.md` y `references/auditoria.md` (§4: control de acceso, diseño y límites, excepciones *fail-open*, registro, deserialización, *path traversal*) sobre el diff.
- **Modelo de amenaza:** activos, las copias cifradas (con la maestra) y la lista de lo que la app puede borrar. Atacantes: otra app con acceso a la carpeta, quien tiene el móvil un momento, un reloj manipulado, un `settings.json` raro. No se protege lo que otra app haga en la carpeta compartida.
- **Sin criptografía nueva:** las copias siguen siendo `VaultStore.exportBackup` (`vault/AutoBackup.kt`, `copyTo`/`fill`); `vaultTag` solo se compara (`vault/BackupCheck.kt:128`), no autentica nada.
- **Borrado solo por id y de la lista propia:** `vault/AutoBackup.kt:643` (`owned = target(uri)?.copies`) y `:800` (`rotate(uri, …)` con `importCopies` del destino). Test `AutoBackupTargetsTest.eachTargetRotatesOnlyItsOwn` (dos URI sobre la misma carpeta y un archivo ajeno con nombre de copia); mutación comprobada.
- **Nunca la copia de una bóveda en el destino de otra:** un `AutoBackup` por bóveda con sus destinos (`AppContainer.kt:420`); resultados tardíos de una bóveda borrada, descartados (`AppContainer.kt:807`, `:822`); copia de una bóveda reemplazada a mitad, sin apuntar (`generation`, sin cambios). Test `noCopyOfAnotherVault`.
- **Bloqueo:** `runDue` no escribe con la bóveda cerrada (`vault/AutoBackup.kt:426`) y no deja nada pendiente; el aviso del calendario se publica solo con la bóveda abierta (`:434`) y se borra en cada bloqueo (`:373`); la pestaña lo pinta dentro de `VaultGate` (`ui/screens/VaultScreen.kt:298`).
- **Reloj:** `vault/BackupSchedule.kt:71`; atrasado no repite, adelantado da como mucho una copia (`:74`); una evaluación por destino y llamada (sin tormentas).
- **«Comprobar una copia», decisión (b):** el tag sale de los datos comprobados (`vault/BackupCheck.kt:115`); se compara bajo el mutex con la época del inicio (`vault/VaultStore.kt:1078`); tag vacío nunca cuenta; informe con problemas no se apunta (`vault/BackupCheckSession.kt:114`). No es *fail-open*: un fallo al comparar o guardar da `null` y no dice nada.
- **Límites (decisión (a)):** a lo sumo 3 destinos, sin repetidos (`settings/SettingsStore.kt:150`), `pastTargets` ≤ 6; la conversión de ajustes antiguos va por clases tipadas de kotlinx.serialization (`settings/SettingsStore.kt:186`, `:245`), sin tipos del archivo.
- **`lastError`:** solo textos fijos (`vault/AutoBackup.kt:846`); los ajustes no guardan secretos ni nombres (`PerVaultSettingsTest.noSecretInSettings` en verde). Sin registros nuevos.
- **Nombres de archivo:** solo los prefijos de siempre (`seguridad-auto-`, `seguridad-antes-de-importar-`, `seguridad-antes-de-2-0-`) con fecha; ninguna ruta de entrada.
- Sin permisos, componentes exportados, red ni dependencias nuevas; sin `WorkManager` ni servicios.

## Revisión Ingeniero Móvil Senior
- **Concurrencia:** un `Mutex` por bóveda (`vault/AutoBackup.kt:359`) serializa el debounce, `runDue`, `replaceOldCopies` y las copias de antes de importar (`:559`); todo en el `scope` de la bóveda (`Dispatchers.IO` de `appScope`, `AppContainer.kt:106`), nunca en el hilo principal; `appOpened` lanza en `appScope` (`AppContainer.kt:470`).
- **Ciclo de vida:** `runDue` desde `MainActivity.onStart` (`MainActivity.kt:62`) y desde el colector del desbloqueo; sin servicios ni segundo plano. Un `lock()`+`unlock()` sin ceder el hilo (solo en tests) no se ve como desbloqueo: `calendarTargetsCopyOnlyWhenDue` lo documenta.
- **Zona horaria:** leída en cada uso (`vault/AutoBackup.kt:526`).
- **Estado de la UI:** `rememberSaveable` solo con la URI de la carpeta que se cambia (`ui/screens/SettingsScreen.kt:198`, D-13); nada secreto.
- **SAF:** permiso persistente tomado tras comprobar repetidos y tope (`SettingsScreen.kt:214`), devuelto al quitar o cambiar solo si ningún destino de ninguna bóveda lo usa (`SettingsScreen.kt:344`, `AppContainer.kt:740`); al borrar una bóveda, todos los suyos (`AppContainer.kt:669`).
- **Rendimiento:** `targets()` lee un `StateFlow` en memoria; `missed()` lee `lastModified` en IO.
- **Compose:** la línea de la bóveda va encima de la lista con `weight(1f)` (`VaultScreen.kt:286`); `BackupCheckNoticeLine` (`VaultBackup.kt:268`) recalcula solo con su clave; aviso de lint `ModifierParameter` arreglado.

## Textos para el Abogado Digital (revisión final)
Nuevos o cambiados (todos en `vault/BackupTargetTexts.kt`, `vault/BackupCheckTexts.kt`, `vault/AutoBackup.kt`, `tools/ToolTexts.kt` o la pantalla):
- `AUTO_BACKUP_SHORT` (cambia): «Guarda una copia cifrada en las carpetas que elijas, tras cada cambio o con calendario.»
- `AUTO_BACKUP_SCHEDULE_ON_OPEN` (O-4, a la vista): «Las copias con calendario se hacen al abrir la app.»
- `AUTO_BACKUP_MORE`: «Guarda una copia cifrada (.sgbak, con los adjuntos) en hasta 3 carpetas que elijas, cada una con su calendario: unos segundos después de cada cambio en la bóveda, o cada día, cada semana o cada mes. Clavero no funciona en segundo plano: las copias con calendario se hacen cuando abres la app o la bóveda, y si no la abres no hay copia. Elige carpetas que se sincronicen o que copies fuera del móvil.»
- «Más información» de la copia automática (cambia): «… y la de antes de la 2.0 (seguridad-antes-de-2-0-…); nunca otros archivos de la carpeta. Cada carpeta solo borra las copias que la app hizo en ella.»
- `AUTO_BACKUP_ADD_FOLDER`: «Añadir otra carpeta»; `AUTO_BACKUP_ALREADY_TARGET`: «Esa carpeta ya recibe las copias de esta bóveda. Elige otra.»; `AUTO_BACKUP_TOO_MANY`: «Ya hay 3 carpetas. Quita una para añadir otra.»; `AUTO_BACKUP_REMOVED`: «Ya no se harán copias en esa carpeta. Las copias ya hechas siguen en ella.»; botón «Quitar esta carpeta»; título «Carpeta N de la copia automática».
- Calendarios: «Tras cada cambio», «Cada día», «Cada semana», «Cada mes»; final de «Copia automática activada: se hará una ahora y después …»: «tras cada cambio en la bóveda», «cada día, al abrir la app», «cada semana, al abrir la app», «cada mes, al abrir la app»; «Aún no se ha hecho ninguna copia: se hará en unos segundos.»
- `scheduledCopiesText`: «Se hizo la copia con calendario.» / «Se hicieron N copias con calendario.»
- `AUTO_BACKUP_REPLACED_SEVERAL`: «Se hizo una copia nueva en cada carpeta y se borraron las copias automáticas y las de antes de importar hechas antes del cambio de contraseña: las que la app tenía apuntadas en cada carpeta. Las que se hayan copiado a otro sitio (otra carpeta, la nube o su papelera) no se borran.»; `autoBackupUnlistedText(n, severalFolders = true)` empieza «En las carpetas …».
- `BACKUP_CHECK_NEVER`: «Aún no has comprobado ninguna copia.»; `backupCheckDaysAgo` (D-8, rondas 1 y 2): «Comprobaste una copia por última vez hace N días.»
- `BACKUP_CHECK_NOTICE_MORE` (O-5, ronda 1): «Los días se cuentan desde que comprobaste con «Comprobar una copia» una copia de esta bóveda, no desde que se hizo la copia. Las copias de antes de la 2.0 y las de otra bóveda no cuentan.»
- `BACKUP_CHECK_NOT_COUNTED` (O-5, ronda 1): «Esta comprobación no cuenta para el aviso «Comprobaste una copia por última vez»: la copia es de otra bóveda o de antes de la 2.0.»
- `BACKUP_CHECK_DATE_AHEAD` (nuevo, ronda 1, M-6): «La fecha de tu última comprobación de una copia es posterior a hoy. Comprueba una copia otra vez.»
- Con varias carpetas que fallan distinto tras cambiar la maestra, el diálogo enseña sus textos de fallo (los de siempre), uno por línea (ronda 1, I-2).
- **Para la política (tarea 12):** las copias automáticas pueden ir a varias carpetas (hasta 3) con calendario, solo al abrir la app (sin segundo plano); la bóveda guarda cifrada la fecha de la última comprobación de una copia; los ajustes guardan sin cifrar las carpetas, su calendario, cuántas copias se conservan y los identificadores de las copias que la app hizo, también los de carpetas quitadas (como mucho 6) para seguir rotándolas si se vuelven a elegir; A6 borra en cada carpeta solo lo que la app apuntó allí. «Importar como bóveda nueva» da a la bóveda una marca propia (D-12).
- Todos estos textos están también en el anexo (`docs/superpowers/specs/2026-10-27-seguridad-1.3.0-textos.md`, «Clavero 2.0, tarea 8»).


## Ronda 1 de arreglos (revisión `task-8-review.md`)

Commits: «2.0, tarea 8 (en curso): ronda 1, código…», «… ronda 1, tests nuevos», «… anexo de textos…» y el final «2.0, tarea 8: ronda 1 de arreglos».

| Punto | Arreglo | Test |
|---|---|---|
| **I-1** | `withTargetAdded` recupera de `pastTargets` el «Conservar las N» y el calendario de la carpeta; `keptPast` guarda el calendario; `withTargetFolderChanged` a una carpeta con listas usa el mayor de los dos números (`settings/Settings.kt`, `withTargetAdded`, `withTargetFolderChanged`, `keptPast`). | `AutoBackupTargetsTest.aFolderChosenAgainKeepsItsNumberOfCopies` (quitar y volver a añadir con 10 copias como lo hace Ajustes; desde la 1.4.3 con la copia desactivada y «Conservar las 10»; cambio a una carpeta con su número). |
| **I-2** | Lo avisado es por petición: la unión de `replaceTold` de los destinos con petición (`toldInRequests`, `Replace.told`); `tell()` enseña todos los textos nuevos distintos, uno por línea, en un solo resultado, así que todo lo que se apunta como avisado se ha enseñado (`vault/AutoBackup.kt`, `tell`, `attempt`). | `a6FailuresAreToldOnceInAll` (dos carpetas que fallan distinto: los dos textos, una vez; el mismo fallo en la otra carpeta no se repite; el éxito, una vez) y `a6SameFailureInTwoFoldersIsToldOnce`. |
| **I-3** | `runChanged`, `runDue` y `replaceNow` toman de la instantánea solo los `uri` y vuelven a leer cada destino dentro del cerrojo justo antes de su copia. En `attempt`, «replaced» solo si la petición sigue siendo la pedida al terminar (guardada en el destino, o sin importación desde `replaceOldCopies`, `Replace.since`); si no, el error es `AUTO_BACKUP_OLD_NOT_DELETED_NO_RETRY`, nunca «se borraron». | `anImportDuringA6NeverSaysTheOldCopiesWereDeleted` (un import durante la copia de la primera carpeta, al desbloquear y con «Borrar…» con la bóveda abierta y un cambio de bóveda). |
| **M-1** | El aviso del calendario se publica y se borra bajo el mismo cerrojo, con la época del bloqueo de la ejecución; la pestaña lo toma con `takeScheduled`, que solo dice sí si no hubo bloqueo desde entonces. | `theCalendarNoticeNeverOutlivesALock`. |
| **M-2** | `AutoBackup.launchDue` lanza en el `scope` de la bóveda; `AppContainer.appOpened` lo usa. | (código; `PlatformSourcesTest` sigue en verde) |
| **M-3** | `failedAt` en memoria: al abrir la app no se reintenta un destino que falló hace menos de `RETRY_AFTER_FAILURE_MS` (5 min); el desbloqueo, un cambio guardado y A6 sí. | `aFailingFolderIsNotRetriedAtEveryOpening`. |
| **M-4** | `copiedRevision` por destino: el *debounce* salta un destino `ON_CHANGE` que ya copió esa revisión (sin depender de la hora del archivo). | `aChangeIsCopiedOnce`. |
| **M-5** | El diálogo de A6 lee solo los destinos con la petición mientras alguno la tenga (`a6Targets`). | `theA6DialogReadsOnlyTheFoldersWithTheRequest`. |
| **M-6** | `backupCheckNotice`: una fecha a más de un día en el futuro da `BACKUP_CHECK_DATE_AHEAD`; `BackupCheckNoticeLine` recalcula con el día en la clave. | `BackupCheckTest.noticeAfter30Days`. |
| **M-7** | `saneVault` pasa las listas de los destinos que sobran (cuarto, repetido) a `pastTargets`. | `threeTargetsMax`. |
| **M-8** | (a) `a6InASharedFolderDeletesOnlyEachOnesOwn`; (b) `aFolderLeftIsNeverTouched`; (c) I-2; (d) I-1; (e) I-3; (f) `BackupScheduleTest.daysOfTheChangeOfTime` (Madrid, 28-03-2027 y 31-10-2027); (g) `aBadOldVaultLosesNothingElse`; (h) M-1. | — |
| **M-9** | `BACKUP_CHECK_NOT_COUNTED`, `BACKUP_CHECK_NOTICE_MORE` y los KDoc al texto de D-8. | `BackupCheckSessionTest`, `PlatformSourcesTest`. |
| **D-8** | «Comprobaste una copia por última vez hace N días.»; test y anexo al día. | `noticeAfter30Days`. |
| **D-12** | `VaultStore.importReplace(…, newTag = true)` desde `AppContainer.importAsNewVault`: la bóveda nueva recibe `VaultCrypto.newTag()`; «Importar copia» (reemplazar) sigue con el de la copia. | `BackupCheckTest.importAsNewVaultGetsItsOwnTag`. |

**Prueba de los tests nuevos:** con las tres correcciones importantes deshechas a la vez (I-1, I-2 e I-3), `AutoBackupTargetsTest` da 3 en rojo (`aFolderChosenAgainKeepsItsNumberOfCopies`, `a6FailuresAreToldOnceInAll` y `anImportDuringA6NeverSaysTheOldCopiesWereDeleted`); revertido.

**Comandos y salida (en `7a389da`+ronda 1):**
- `./gradlew :app:testDebugUnitTest --tests 'app.seguridad.vault.AutoBackup*' --tests 'app.seguridad.vault.Backup*' --tests 'app.seguridad.settings.*' --tests 'app.seguridad.AppContainer*'`: verde (`AutoBackupTargetsTest` 17, `BackupScheduleTest` 4, `BackupCheckTest` 22).
- `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets --no-daemon --max-workers=1`: BUILD SUCCESSFUL, **2209** tests sin fallos; lint 0 errores, 20 avisos (los mismos).
- `./gradlew -p jvm-check test --no-daemon --max-workers=1`: BUILD SUCCESSFUL, **2172** tests sin fallos.

**Dudas de la ronda:**
- I-3: tras un import a mitad de A6, la carpeta afectada puede decir «La copia automática se hizo, pero no se pudieron borrar las antiguas.»: es verdad (no se borraron) y la pantalla de importar ya avisó de que la petición se quitó.
- M-6: texto nuevo `BACKUP_CHECK_DATE_AHEAD`, al Abogado.
- I-1: volver a elegir una carpeta recupera también su calendario, no solo su número.

## Ronda 2 de arreglos (re-revisión `task-8-rereview-1.md`)

| Punto | Arreglo | Test |
|---|---|---|
| **I-3, tercera cláusula** | `tell()` (`vault/AutoBackup.kt`) publica el éxito de A6 solo si, leídos los destinos después del bucle (con `onResult` ya guardado), **ninguno** sigue con `replacePending`; con un fallo nuevo, el fallo; si no, nada (la petición sigue y se dirá al resolverse). Así el diálogo (`AppNavHost.kt`, «…en cada carpeta») nunca dice que se borró lo que sigue en una carpeta que no lo intentó. | `AutoBackupTargetsTest.noSuccessWhileAFolderThatDidNotTryStillHasTheRequest` (camino `runChanged`, carpeta diaria con la petición) y `noSuccessWhenALockLeavesAFolderOut` (bloqueo tras la primera carpeta). Con la regla de antes (`notices.all { it.replaced }`), los dos en rojo; revertido. |
| **N-1** | «Importar como bóveda nueva» pone también `lastCheckedBackupAt = 0L` (`VaultStore.replace`, `prepare`). «Importar copia» conserva tag y fecha. | `BackupCheckTest.importAsNewVaultGetsItsOwnTag`, ampliado. |
| **N-2** | Anexo (`docs/superpowers/specs/2026-10-27-seguridad-1.3.0-textos.md`, «Clavero 2.0, tarea 8»): todos los textos nuevos o cambiados (resumen, «Más información», carpetas, finales de «Copia automática activada…», la condición del texto de varias carpetas). | `ToolTextsTest` en verde. |
| **N-3** | La lista de textos de este informe dice el texto de D-8. | — |

Nota: con varias carpetas, `unlistedLeft` del éxito suma solo las de la ejecución que lo cierra (las carpetas que terminaron antes ya no lo cuentan): el añadido «En las carpetas quedan N copias…» puede contar de menos, nunca de más.

**Comandos y salida (ronda 2):** focalizados `--tests 'app.seguridad.vault.AutoBackup*' 'app.seguridad.vault.Backup*' 'app.seguridad.settings.*' 'app.seguridad.AppContainer*'` en verde (`AutoBackupTargetsTest` 19); suites completas abajo.

- `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets --no-daemon --max-workers=1`: BUILD SUCCESSFUL, **2211** tests sin fallos; lint 0 errores, 20 avisos (los mismos).
- `./gradlew -p jvm-check test --no-daemon --max-workers=1`: BUILD SUCCESSFUL, **2174** tests sin fallos.

## Ronda 3 de arreglos (re-revisión `task-8-rereview-2.md`; «Ruling (advisor, T8 ronda 3)»; «Owner decision (13-11-2026): D-3»)

Base `67c4b2e`. Commits: 9310aa0 (código), e04e8a6, aa91358 y 2e212f1 (tests), c241ecc (anexo) y el final «2.0, tarea 8: ronda 3 de arreglos». Sin push.

### Lo hecho
1. **La cuenta de copias sin apuntar en el éxito de A6 (opción A).**
   - Hay un campo nuevo por carpeta, `BackupTargetSettings.replaceUnlisted: Int?` (`vault/BackupSchedule.kt:48`).
   - Se guarda cuando esa carpeta reemplaza sus copias, también en un «Borrar…» pedido con la bóveda abierta (`settings/Settings.kt:296`).
   - Se borra en estos casos:
     - con una petición nueva (`Settings.kt:275`, y `replaceAfter` con `newRequest`);
     - con `withoutReplaceRequest` (`Settings.kt:247`);
     - con las dos importaciones (`forgettingAutoBackupCopies`; como bóveda nueva no hay destinos);
     - con una bóveda nueva (`turnOffAutoBackupForNewVault` vacía los destinos) o borrada (`withoutVault`).
   - `tell` (`vault/AutoBackup.kt:567`) suma el campo sobre todas las carpetas. Lo hace con la **misma** lectura de `targets()` con la que decide que ninguna sigue con `replacePending` (`:572`).
   - Un fallo lleva también la cuenta de las carpetas que ya terminaron, así que la advertencia nunca desaparece detrás de un error:
     - en `tell`, `:574`;
     - en el diálogo, con `replaceOutcomeText`, `AutoBackup.kt:215`, que se usa en `ui/AppNavHost.kt:220`;
     - en el diálogo de cambiar la maestra, `ui/screens/SettingsScreen.kt:407-410`.
   - Con varias carpetas, la frase dice que la cuenta es la del momento del reemplazo: «Al borrar las copias anteriores, en las carpetas quedaba(n)…» (`AutoBackup.kt:170`).
   - Una carpeta sin cuenta en el momento del éxito sale como no comprobada (`autoBackupUncheckedText`, `AutoBackup.kt:201`). Es una carpeta en la que no se reemplazó nada para esta petición; hoy, una añadida después de pedirla.
   - Un listado que falla no da nunca un 0. La rotación lanza, el resultado es `AUTO_BACKUP_OLD_NOT_DELETED`, no `replaced`, y la cuenta se queda en `null` (`AutoBackup.kt`, `rotate` dentro de `attempt`). Lo cubre el test `aFailedListingIsNeverAZeroCount`.
2. **N-4.**
   - `tell` se llama dentro del cerrojo, al final de `runChanged` y de `runDue` (`AutoBackup.kt:495`, `:541`).
   - Además, el éxito exige que `store.generation` sea la misma que al empezar el bucle (`:572`). Una importación no toma el cerrojo de `AutoBackup` y sube la generación antes de quitar las peticiones (`VaultStore.kt`, `replace`).
3. **O-5.**
   - `keptPast` guarda con las listas de la carpeta su petición pendiente, sus destinos y lo ya avisado (`Settings.kt:225`).
   - `withTargetAdded` lo devuelve (`Settings.kt:184`), aunque la petición de las otras carpetas ya se haya cerrado.
   - El aviso de quitar («… no se borraron: siguen en su carpeta») sigue siendo verdad.
4. **D-3 (decisión del dueño).** Una copia por calendario se salta si la bóveda no cambió desde la última copia de esa carpeta (`AutoBackup.kt:536`).
   - **La marca.** `VaultStore.changeMark()` (`vault/VaultStore.kt:427`, `changeMarkOf` en `:1674`) es el SHA-256 en hexadecimal (opaco) de tres cosas:
     - la hora del último cambio guardado (`lastChangeSavedAt`, la fecha del archivo);
     - el coste Argon2id del archivo;
     - el `vaultTag`.
   - La marca se guarda por carpeta en `BackupTargetSettings.copiedMark` (`BackupSchedule.kt:54`), solo cuando la copia se escribió y se apuntó (`Settings.kt:279`). Sobrevive a la muerte del proceso porque la hora es la del archivo y la marca está en los ajustes.
   - **Por qué esta marca:**
     - `store.generation` y `store.revision` viven solo en memoria.
     - `VaultFile.revision` (`vault/Vault.kt:443`, `:496`) sube en **cada** guardado, también en el de contabilidad de cada desbloqueo (`afterMasterUnlock` vuelve a sellar con `revision + 1`), así que con él no se saltaría nunca.
     - La fecha del archivo sí distingue: los guardados de contabilidad la conservan (`saveBookkeeping`, `VaultStore.kt` `:405-409`). Cambia con las entradas, los adjuntos, los ajustes propios de la bóveda (carpetas, vistas, nombre), la maestra y las importaciones.
     - Un A8 en el desbloqueo conserva la fecha, pero cambia el coste del archivo: por eso el coste entra en la marca.
     - Un `vaultTag` nuevo cambia la marca.
   - **Nunca se salta:**
     - una A6 pendiente;
     - una copia que esperaba al desbloqueo (puede ser una pedida a mano con la bóveda cerrada);
     - la primera copia a una carpeta (`lastAt` o `copiedMark` nulos);
     - una copia a mano, que va por `runChanged`, donde no se salta nada;
     - una carpeta `ON_CHANGE`.
   - **Un salto no hace nada:** no escribe, no rota y no cambia la fecha. La copia sigue pendiente según el calendario y se hace en la siguiente apertura con un cambio.
   - **Un fallo** no cambia `copiedMark`, porque solo se apunta en el resultado de una copia escrita.
   - **Textos:**
     - `AUTO_BACKUP_SCHEDULE_ON_OPEN`: «Las copias con calendario se hacen al abrir la app, si hubo cambios.»;
     - en `AUTO_BACKUP_MORE`, «…si la bóveda cambió desde la última copia en esa carpeta…»;
     - `scheduleAfter`: «cada día, si hubo cambios, al abrir la app» (y lo mismo para la semana y el mes), en `vault/BackupTargetTexts.kt`.
     - La política aún no tiene ninguna línea del calendario (es de la tarea 12; ver Dudas).
5. **N-2:** el anexo lleva la variante «Al borrar las copias anteriores, en las carpetas quedaba(n)…» (que sustituye a «En las carpetas…») y el texto de las carpetas sin comprobar. **N-5:** el KDoc de `replaceOutcome` refleja la regla actual (`AutoBackup.kt`, `replaceOutcome`).

### Tabla: cada texto de resultado de A6
«Petición» es la de reemplazar las copias tras un cambio de maestra. «Pasada» es una ejecución de `runDue` (al desbloquear o al abrir la app) o de `runChanged` (tras un cambio o «Hacer una copia ahora»). Todos los diálogos de después de desbloquear salen de `tell` (`AutoBackup.kt:567`) por `ReplaceOutcomeDialog(replaceOutcomeText(…))` (`AppNavHost.kt:220`). El diálogo de cambiar la maestra (`SettingsScreen.kt:403-411`) enseña la respuesta de `replaceOldCopies`.

| Texto | Condición exacta | `archivo:línea` | Test |
|---|---|---|---|
| `AUTO_BACKUP_REPLACED` (una carpeta) | Diálogo tras desbloquear: la pasada tiene un resultado `replaced`, la misma lectura de destinos dice que ninguno tiene `replacePending` y `generation` no cambió desde el inicio del bucle. Diálogo de la maestra: `replaceOldCopies` sin error. | `AutoBackup.kt:152`, `:572`, `:578`, `:215`; `SettingsScreen.kt:405` | `AutoBackupF3Test` «m1 the replacement's text…», `AutoBackupTest.aReplacementAskedWhileLockedSurvivesARestartAndRunsAtTheNextUnlock` |
| `AUTO_BACKUP_REPLACED_SEVERAL` (varias) | Igual, con más de una carpeta. Nunca con una carpeta con la petición sin terminar: ni una que no lo intentó en la pasada (con calendario, o dejada fuera por un bloqueo), ni una quitada y vuelta a añadir (O-5), ni tras una importación a mitad de la pasada (N-4). | `AutoBackup.kt:161`, `:572` | `AutoBackupTargetsTest.a6PerTarget`, `noSuccessWhileAFolderThatDidNotTryStillHasTheRequest`, `noSuccessWhenALockLeavesAFolderOut`, `aFolderAddedAgainBringsItsRequestBack`, `anImportRightAfterAReplacementMakesNoSuccess` |
| «En la carpeta queda(n) N copia(s)…» (una carpeta) | Detrás del éxito o de un fallo si la suma de `replaceUnlisted` es mayor que 0. Con una carpeta, es su cuenta del reemplazo. | `AutoBackup.kt:170`, `:215`, `:569` | `AutoBackupF3Test` «COPIAS-2 …» |
| «Al borrar las copias anteriores, en las carpetas quedaba(n) N copia(s)…» (varias) | Detrás del éxito, con la suma de `replaceUnlisted` de todas las carpetas, también las que terminaron en pasadas anteriores o en un «Borrar…» cortado por un bloqueo. Detrás de un fallo, con la suma de las que ya terminaron. | `AutoBackup.kt:170`, `:569`, `:574-576`; `Settings.kt:296` | `theSuccessCountsTheUnlistedCopiesOfEveryPass` (pasada 1 con fallo y pasada 2), `aReplacementCutByALockKeepsTheFirstFoldersCount` |
| «En N carpeta(s) no se ha comprobado si quedan copias…» | Detrás del éxito, por cada carpeta sin `replaceUnlisted` (no reemplazó nada para esta petición; hoy, añadida después de pedirla). Un listado fallido nunca llega aquí con 0: no hay éxito hasta que esa carpeta lista y reemplaza. | `AutoBackup.kt:201`, `:580` | `aFailedListingIsNeverAZeroCount` |
| Fallos (`AUTO_BACKUP_WRITE_FAILED`, `_NO_PERMISSION`, `_NO_FOLDER`, `_VAULT_PROBLEM`, `_FAILED`), uno por línea | Algún resultado de la pasada con `toldKey`: un fallo de una carpeta con la petición que no se ha avisado en ninguna carpeta con la petición (`toldInRequests`). | `AutoBackup.kt:574`, `:840` (`told`) | `a6FailuresAreToldOnceInAll`, `a6SameFailureInTwoFoldersIsToldOnce` |
| `AUTO_BACKUP_OLD_NOT_DELETED` («…Se volverá a intentar en la próxima copia.») | La copia se escribió, la petición sigue guardada en esa carpeta y no se pudo borrar algún destino o temporal. | `AutoBackup.kt:140`, `:834` | `AutoBackupF3Test` «R-6 a pending replacement that could not delete a target…» |
| `AUTO_BACKUP_OLD_NOT_DELETED_NO_RETRY` | La copia se escribió pero la petición ya no es la pedida: la quitó una importación (`stillAsked` falso) o no está guardada. Nunca dice «se borraron». | `AutoBackup.kt:147`, `:809-812`, `:834` | `anImportDuringA6NeverSaysTheOldCopiesWereDeleted`, `AutoBackupF3Test` «R-6 a replacement whose request an import dropped…» |
| `AUTO_BACKUP_REPLACE_WAITS` | `replaceOldCopies` con la bóveda bloqueada, o bloqueada a mitad: las carpetas siguientes guardan la petición. | `AutoBackup.kt:233`, `waitForUnlock` | `a6PerTarget`, `aReplacementCutByALockKeepsTheFirstFoldersCount` |
| `AUTO_BACKUP_REPLACE_DROPPED` (al quitar, cambiar de carpeta o importar) | La escritura que quitó la carpeta, o la importación, quitó una petición pendiente. Con O-5 la petición vuelve si se vuelve a añadir la carpeta. | `SettingsScreen.kt:245`, `:346`; `VaultBackup.kt` | `aFolderAddedAgainBringsItsRequestBack`, `SettingsTest.turningTheCopiesOffSaysIfItDroppedAWaitingReplacement` |

**Casos que cubre la tabla:**
- **Carpeta quitada y vuelta a añadir:** la petición vuelve, y no hay éxito hasta que esa carpeta reemplaza (O-5).
- **Importación a mitad:** durante la escritura (`anImportDuringA6NeverSaysTheOldCopiesWereDeleted`) o justo tras un reemplazo (`anImportRightAfterAReplacementMakesNoSuccess`).
- **Bloqueo a mitad de una pasada:** `noSuccessWhenALockLeavesAFolderOut` y `aReplacementCutByALockKeepsTheFirstFoldersCount`.
- **Varias pasadas:** `theSuccessCountsTheUnlistedCopiesOfEveryPass` y `noSuccessWhileAFolderThatDidNotTryStillHasTheRequest`.
- **Una sola carpeta:** los tests de la 1.x (`AutoBackupTest`, `AutoBackupF3Test`), que siguen en verde.

### Tests nuevos (ronda 3) y su rojo sin el arreglo
Tests nuevos en `AutoBackupTargetsTest`:
- `theSuccessCountsTheUnlistedCopiesOfEveryPass`
- `aReplacementCutByALockKeepsTheFirstFoldersCount`
- `aFailedListingIsNeverAZeroCount`
- `anImportRightAfterAReplacementMakesNoSuccess`
- `aFolderAddedAgainBringsItsRequestBack`
- `aCalendarCopyIsSkippedWhileNothingChanged`
- `whatIsNeverSkipped`
- `aFailedCopyDoesNotMarkTheTargetUpToDate`
- `aWrapWithAHigherCostIsAChange`
- `theChangeMarkMovesWithEachPart`

Además, `noCopyOfAnotherVault` y `calendarTargetsCopyOnlyWhenDue` ahora hacen un cambio antes de cada copia por calendario, por D-3.

**El rojo sin el arreglo.** El «equivalente» de un `git stash` es deshacer el arreglo en el código, con `scratchpad/mut.py` y `mut2.py`: los tests nuevos usan campos nuevos, así que un `stash` entero no compilaría. Comando de cada ronda: `./gradlew :app:testDebugUnitTest --tests 'app.seguridad.vault.AutoBackupTargetsTest'`. Después de cada ronda se vuelve con `git checkout -- app/src/main`. La salida:

| Mutación | Salida | Tests en rojo |
|---|---|---|
| M1, M3, M4 y M5: cuenta de la pasada en vez de la guardada, sin comprobar la generación, sin devolver la petición en `pastTargets` y sin carpetas no comprobadas | 29 tests, 5 en rojo | `theSuccessCountsTheUnlistedCopiesOfEveryPass` (esperaba 1, era 0), `aReplacementCutByALockKeepsTheFirstFoldersCount` (1/0), `anImportRightAfterAReplacementMakesNoSuccess` («no se borraron… en cada carpeta»: había éxito), `aFolderAddedAgainBringsItsRequestBack`, `aFailedListingIsNeverAZeroCount` (1/0 de las no comprobadas) |
| M6, M7 y M10: un listado fallido como reemplazo con 0, sin el salto de D-3, y un fallo que apunta la marca | 29 tests, 4 en rojo | `aFailedListingIsNeverAZeroCount` («expected null, but was 0»), `aCalendarCopyIsSkippedWhileNothingChanged`, `aFailedCopyDoesNotMarkTheTargetUpToDate`, `theChangeMarkMovesWithEachPart` (este, por M11 en esa misma ronda) |
| M11: marca sin el coste | 2 en rojo | `theChangeMarkMovesWithEachPart` y `aWrapWithAHigherCostIsAChange` (esperaba 2, era 1) |
| M12: marca sin el tag | 1 en rojo | `theChangeMarkMovesWithEachPart` |
| M8a: la primera copia se salta | 8 en rojo | entre ellos `whatIsNeverSkipped` (`:1050`, la primera copia) |
| M8b: A6 se salta | 1 en rojo | `whatIsNeverSkipped` (`:1078`, `replacePending` sigue) |
| M8c: la copia que esperaba se salta | 1 en rojo | `whatIsNeverSkipped` (esperaba 3, era 2) |
| M9: la copia a mano se salta | 1 en rojo | `whatIsNeverSkipped` (esperaba 2, era 1) |

Al principio, `aWrapWithAHigherCostIsAChange` pasaba en la ronda 2 porque M7 quitaba el salto entero. Se repitió M11 sola y cayó. M8b y M8c no fallaban, porque la A6 pedida bloqueada y la copia a mano con la bóveda cerrada pasaban por `waited`. El test se rehízo (una A6 guardada como tras reiniciar y una copia a mano bloqueada en un día que toca), y ahora fallan.

### Verificación
- `export ANDROID_HOME=/home/user/android-sdk; ./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets --no-daemon --max-workers=1`: BUILD SUCCESSFUL, **2221** tests sin fallos (`AutoBackupTargetsTest`: 29). Lint: 0 errores y los mismos 20 avisos.
- `./gradlew -p jvm-check test --no-daemon --max-workers=1`: BUILD SUCCESSFUL, **2184** tests sin fallos.

### Revisión experto-seguridad-cripto (ronda 3)
- **Veracidad de A6 (CWE-451).**
  - El éxito solo sale con todas las peticiones cerradas, leídas a la vez que la cuenta, dentro del cerrojo y con la misma generación (`AutoBackup.kt:567-582`).
  - La advertencia de copias sin apuntar no se pierde con un fallo ni con varias pasadas (`:574-576`, `Settings.kt:296`).
  - Una carpeta sin cuenta se dice no comprobada (`AutoBackup.kt:201`).
- **Borrado:** sin cambios. Solo ids de la lista propia (`rotate`). La petición devuelta por O-5 solo afecta a ids de la lista de esa carpeta, que viajan con ella.
- **D-3:**
  - La marca es un SHA-256 opaco (`VaultStore.kt:1674`): en `settings.json` no queda el `vaultTag` en claro ni nada de la bóveda.
  - Un salto no escribe, no rota y no cambia la fecha (`AutoBackup.kt:536`).
  - A6 y la primera copia nunca se saltan.
  - Un fallo no marca la carpeta como al día. Es la dirección segura: en la duda, se hace la copia.
  - Los guardados de contabilidad no cuentan como cambio. No cambian nada que una copia proteja, salvo «Recientes» y la fecha de la última comprobación.
- Sin red, permisos, dependencias ni registros nuevos. SHA-256 es de `java.security` (`MessageDigest`), una primitiva estándar.

### Revisión Ingeniero Móvil Senior (ronda 3)
- **Concurrencia:** `tell` corre dentro del `Mutex` (`AutoBackup.kt:495`, `:541`) y no suspende. Solo lee un `StateFlow` y publica en `Notice`.
- **Persistencia:**
  - `replaceUnlisted` y `copiedMark` son campos opcionales con valor por defecto (`BackupSchedule.kt:48`, `:54`), así que los ajustes de las tareas 2 a 8 se siguen leyendo.
  - La fecha del archivo (`File.lastModified`) sobrevive a la muerte del proceso.
- **Coste:**
  - `changeMark()` hace un `lastModified` y un SHA-256 de unos 100 bytes por carpeta con calendario y pasada, en el `scope` de E/S.
  - El salto evita reexportar la bóveda entera (hasta 24 MiB más los adjuntos) cada día sin cambios.
- **UI:** la cuenta se enseña también con un fallo en los dos diálogos (`AppNavHost.kt:220`, `SettingsScreen.kt:407-410`). Sin estado nuevo en `rememberSaveable`.

### Textos para el Abogado Digital (ronda 3)
- **Cambian, por D-3:**
  - «Las copias con calendario se hacen al abrir la app, si hubo cambios.»;
  - en «Más información»: «…o cada día, cada semana o cada mes si la bóveda cambió desde la última copia en esa carpeta…»;
  - los finales «cada día, si hubo cambios, al abrir la app», «cada semana, si hubo cambios, al abrir la app» y «cada mes, si hubo cambios, al abrir la app».
- **Cambia, con varias carpetas:** «Al borrar las copias anteriores, en las carpetas quedaba 1 copia … no se ha borrado y puede que se abra con una contraseña anterior. Si no la necesitas, bórrala a mano.» y la forma plural «…quedaban N copias…». Con una carpeta no cambia.
- **Nuevo:** «En 1 carpeta no se ha comprobado si quedan copias con nombre de copia de la app que la app no tenía apuntadas: puede que alguna se abra con una contraseña anterior. Míralo a mano.» y «En N carpetas…».
- Todos están en el anexo, en la sección «Clavero 2.0, tarea 8».
- **Para la política (tarea 12):** la copia con calendario solo se hace si la bóveda cambió desde la última copia en esa carpeta. Los ajustes guardan, por carpeta, una marca opaca (un resumen SHA-256) para saberlo, y cuántas copias sin apuntar quedaron al reemplazar.

### Dudas (ronda 3)
- **La resolución de la fecha del archivo.** Si un cambio se guarda en el mismo milisegundo que la copia anterior, se vería como «sin cambios» hasta el siguiente cambio. En la práctica no pasa: la copia tarda bastante más. `VaultFile.revision` no sirve como marca porque sube en cada desbloqueo.
- **Lo que no cuenta como cambio.** Los guardados de contabilidad no cuentan: el desbloqueo, «Recientes» (`lastUsedAt`) y la fecha de la última comprobación. Una copia por calendario no se hace solo por ellos. Si el dueño quiere que cuenten, basta con sacarlos de `saveBookkeeping`, pero entonces no se saltaría casi nunca.
- **«Ajustes de la bóveda»:** se lee como los ajustes que viven dentro de la bóveda (carpetas, vistas, nombre y colores). Un cambio del coste de Argon2id en Ajustes cuenta en cuanto se aplica (A8, en el siguiente desbloqueo). Los ajustes de las copias (carpetas, calendario) no son contenido de la copia y no cuentan.
- **«Un listado que falla no se guarda como 0».** Se cumple por construcción: sin listado no hay reemplazo ni cuenta. El texto «no se ha comprobado» sale solo para las carpetas que no reemplazaron para la petición (hoy, una añadida después de pedirla). Elegí «no se ha comprobado» y no «no se pudo comprobar», porque esa carpeta no falló.
- **La política (`docs/privacidad.html`) aún no dice nada del calendario.** La línea con «si hubo cambios» se escribe en la tarea 12, que hace la política de la 2.0.
- **O-6 sigue:** en una carpeta compartida por dos destinos, la cuenta puede pasarse. Es la dirección segura.

## Ronda 4 de arreglos (re-revisión `task-8-rereview-3.md`; «Ruling (advisor, T8 ronda 4)»)

Base `6a1ddaa`. Commits sin push:
- c8689e1: estructura;
- 5b67dfe: tests de antes;
- 50533fc: tests nuevos;
- 0a2fba9: m-5;
- 3b8fc4d: anexo;
- el de cierre, «2.0, tarea 8: ronda 4 de arreglos».

El contenedor se reinició después de 3b8fc4d. Las suites se volvieron a ejecutar después del reinicio, con el árbol limpio.

### 1. Arreglo de estructura
- **Resultado guardado por destino, con el id de la petición.**
  - Cada destino guarda la petición en la que está, en `BackupTargetSettings.replaceRequest` (`vault/BackupSchedule.kt:46`).
  - Si reemplazó, guarda también `replaceDone = ReplaceDone(request, deleted, unlisted)` (`BackupSchedule.kt:53`, `:100`).
  - La bóveda guarda la petición de ahora y lo último que se dijo de ella: `VaultSettings.a6 = A6State(id, said)` (`BackupSchedule.kt:108`). Este campo sustituye a `replaceUnlisted` y a `unchecked` de la ronda 3, que ya no existen.
  - `replaceOldCopies` crea un id nuevo en cada petición (`vault/AutoBackup.kt:619-620`, `newA6RequestId` en `:206`). Todo resultado lleva el id de su petición (`AutoBackupResult.request`).
  - Una petición guardada por la 1.4.3, sin id, cuenta como `LEGACY_A6_REQUEST` en un solo sitio: `currentA6Request` y `requestId` (`vault/A6Text.kt:24`, `:27`).
- **Cómo se apunta** (`settings/Settings.kt`):
  - una petición pedida con la bóveda cerrada guarda su id y no tiene resultado (`:289`);
  - un reemplazo guarda su resultado con el id (`:309-311`);
  - un fallo de un «Borrar…» pedido con la bóveda abierta deja la carpeta en la petición, sin resultado (`:314`).
- **Una cuenta de otra petición nunca se suma.** `a6Summary` (`A6Text.kt:30`) solo cuenta los `replaceDone` cuyo id es el de la petición de ahora. Por eso ya no hace falta poner las cuentas a cero, que era el punto 3 del revisor.
- **Una sola función pura, sin E/S, redacta el texto:** `a6Text(failures, summary)` (`A6Text.kt:45`). La usan tres sitios:
  - `publish`, para lo que se dice al desbloquear (`AutoBackup.kt:555-575`). El texto queda fijado en `AutoBackupResult.text`, y `AppNavHost.kt:220` lo enseña tal cual.
  - El diálogo de la maestra (`ui/screens/SettingsScreen.kt:397`, `:419-423`, con `VaultSettings.a6Summary`, `Settings.kt:167`). Ya no redacta su propio éxito (m-1), y `MasterChanged.answerUnlisted` desaparece.
  - Nada más: `replacedText`, `replaceOutcomeText` y `autoBackupUncheckedText` se han quitado. `PlatformSourcesTest` («the A6 texts come from one function…») fija que `AUTO_BACKUP_REPLACED` y `AUTO_BACKUP_REPLACED_SEVERAL` solo aparecen en `A6Text.kt`, `AutoBackup.kt` (donde se definen) y `ToolTexts.kt` (la lista de textos).
- **«…en cada carpeta» solo si todas las carpetas de ahora tienen un reemplazo de esta petición** (`A6Text.kt:49`). Si no, el texto dice en cuántas sí y en cuántas no, con la advertencia (`a6PartialText`, `:63`), y la cuenta de copias sin apuntar «en las carpetas en que se borraron» (`a6UnlistedInDoneText`, `:76`). Nunca dice «1 de 1».
- **I-2: se publica cuando el resumen guardado cambia respecto a lo último dicho en esta petición** (`AutoBackup.kt:558-560`), con al menos una carpeta reemplazada, y se guarda lo dicho (`A6State.said`).
  - Los fallos nuevos (`toldKey`) se publican siempre, una vez en total.
  - Quitar una carpeta, o cambiarla de sitio, mientras su petición esperaba vuelve a publicar el resumen (`retell`, `AutoBackup.kt:582`; `SettingsScreen.kt:246`, `:351`). Así se dice el resumen final cuando se quita la última carpeta pendiente.
- **N-4:** se decide dentro del cerrojo y con la misma generación (`AutoBackup.kt:558`).
- **m-2: el resultado viaja.**
  - Con `pastTargets` (`keptPast`, `Settings.kt:238`) y con `withTargetAdded` (`:196`), que devuelven `replaceRequest`, `replaceDone` y `copiedMark`.
  - Con el deshacer de la importación (`SettingsStore.kt:128`, también `a6`).
  - La importación y una bóveda nueva lo olvidan (`Settings.kt:248`, `SettingsStore.kt:88`).
- **m-3:** el fallo va en su propia línea, y después el resumen, que dice en cuántas carpetas sí se borraron. Así, el fallo y la cuenta no se contradicen.

### 2. Punto 4 del revisor
Queda fuera. Una carpeta añadida después del cambio no entra en la petición, así que cuenta como no reemplazada. El texto dice que en ella «no se han borrado: puede que alguna se abra con una contraseña anterior. Míralo a mano.» Lo cubre el test exhaustivo, con el estado `ADDED_AFTER`.

### 3. El «otro orden»: no se puede llegar a él
El «otro orden» es desbloquear, volver a añadir «uno» y pulsar «Borrar…» con el diálogo de la maestra todavía abierto. Esta es la cadena que lo impide:
- El diálogo se compone en la raíz de la app siempre que `masterState.open` es verdadero (`ui/AppNavHost.kt:252`). Es un `AlertDialog` modal (`SettingsScreen.kt:400`), así que mientras está abierto no se puede tocar ninguna pantalla de debajo: ni la de la bóveda para desbloquear, ni Ajustes para añadir una carpeta.
- Si se toca fuera o se pulsa «Atrás», se llama a `onClose` (`SettingsScreen.kt:400`), que es `MasterChanged.close` y vacía `oldCopies` y la petición (`vault/MasterChanged.kt:56-57`). «Renovar identidad» y «Cerrar» también lo cierran (`AppNavHost.kt`, `onRenew` y `onClose`). Un cambio de bóveda lo cierra (`AppContainer.kt:514`).
- `replace()` solo funciona con el diálogo abierto (`MasterChanged.kt:61-64`).
- Añadir una carpeta exige la bóveda abierta (`SettingsScreen.kt:197`, `:300`).
- Nada desbloquea la bóveda sin tocar la pantalla de debajo:
  - al cambiar la maestra se desactiva la huella (`ui/screens/VaultBackup.kt:136`, porque envolvía la clave de datos vieja);
  - la apertura automática con la huella (`VaultScreen.kt:424`, `:438`) exige `BiometricVault.enabled`, que ya es falso.
- Si el proceso muere, el estado del diálogo, que solo vive en memoria, se pierde.

Por eso no hace falta la regla de reserva («reemplazada solo si borró sus propias copias apuntadas»).

### 4. Tests nuevos y su rojo sin el arreglo
**Tests nuevos.** En `AutoBackupTargetsTest`:
- `aFolderRemovedBeforeTheChangeAndAddedBackIsNotSaidDeleted` (camino A);
- `aFailureThenALockInReplaceNowIsNotSaidDeleted` (camino B: `one.onWrite = { s.lock(); throw IOException("lleno") }`, que da `WRITE_FAILED`);
- `aFolderThatKeepsFailingNeverHidesTheCountOfTheOthers` (I-2, y el resumen final al quitar la última carpeta pendiente);
- `everyCombinationOfFolders` (exhaustivo, ver abajo);
- `anImportUndoneGivesBackTheA6StateExactly`;
- `aRequestOf143IsTheRequestOfNow`;
- `whatIsAndIsNotAChangeForTheCalendar` (m-4: la comprobación de una copia y la purga de la papelera en el desbloqueo no cuentan como cambio; «Importar copia» sí).

En `PlatformSourcesTest`, «the A6 texts come from one function, and a copy by hand needs the vault open» (una sola función e i-3).

**`everyCombinationOfFolders`:**
- **Qué combina:** de 2 a 3 carpetas, cada una en uno de seis estados: `REPLACED`, `FAILED`, `ADDED_AFTER`, `READDED_WAITING`, `READDED_DONE` y `REMOVED_BEFORE` (este con un resultado de una petición anterior). Son 252 combinaciones.
- **Cómo llega a cada estado:** por las transiciones reales: `withTargetAdded`, `withTargetRemoved` y `withAutoBackupResult`, con los resultados que dan `attempt` y `waitForUnlock`.
- **Qué comprueba:**
  - «cada carpeta» solo sale si todas reemplazaron para esta petición;
  - la cuenta es la suma guardada;
  - las reemplazadas más las no reemplazadas son todas las carpetas;
  - nunca dice «1 de 1»;
  - no dice nada si ninguna reemplazó.

**Los tests de antes, adaptados:**
- `noSuccessWhileAFolderThatDidNotTryStillHasTheRequest` y `noSuccessWhenALockLeavesAFolderOut` ahora esperan el parcial («…en 1 de las 2 carpetas…»), no `null`.
- Los de la cuenta de la ronda 3 esperan el texto de `a6Text`.
- `OneTargetBackup` conserva los campos nuevos de cada resultado (consejo del advisor). Sin eso, los tests de la 1.x no verían el éxito.

**El rojo sin el arreglo.** Cada mutación se aplicó sola (`scratchpad/mut4.py`) y se ejecutó con `./gradlew :app:testDebugUnitTest --tests 'app.seguridad.vault.AutoBackupTargetsTest' --tests 'app.seguridad.PlatformSourcesTest'`. Después de cada una se volvió con `git checkout -- app/src/main`.

| Mutación | Salida | Tests en rojo |
|---|---|---|
| Ma: vuelve la regla «ninguna carpeta pendiente» (una carpeta sin petición cuenta como reemplazada) | 78 tests, 5 en rojo | `everyCombinationOfFolders` («[REPLACED, ADDED_AFTER] expected 1 but was 2»), `aFolderRemovedBeforeTheChangeAndAddedBackIsNotSaidDeleted`, `aFailureThenALockInReplaceNowIsNotSaidDeleted`, `anImportDuringA6NeverSaysTheOldCopiesWereDeleted`, `aFailedListingIsNeverAZeroCount` |
| Mb: solo se publica un fallo nuevo o el éxito completo (I-2 sin arreglar) | 6 en rojo | `aFolderThatKeepsFailingNeverHidesTheCountOfTheOthers`, `aFolderRemovedBeforeTheChangeAndAddedBackIsNotSaidDeleted`, `aFailureThenALockInReplaceNowIsNotSaidDeleted`, `noSuccessWhileAFolderThatDidNotTry…`, `noSuccessWhenALockLeavesAFolderOut`, `aFailedListingIsNeverAZeroCount` |
| Mc: `retell` no hace nada | 1 en rojo | `aFolderThatKeepsFailingNeverHidesTheCountOfTheOthers` (el resumen final) |
| Md: `keptPast` no lleva el resultado (m-2) | 2 en rojo | `everyCombinationOfFolders` («[REPLACED, READDED_DONE] expected 2 but was 1»), `aFolderAddedAgainBringsItsRequestBack` |
| Me: el deshacer de la importación no devuelve `a6` | 1 en rojo | `anImportUndoneGivesBackTheA6StateExactly` |
| Mf: sin el id de la petición (cuenta el resultado de cualquier petición) | 1 en rojo | `everyCombinationOfFolders` («[REPLACED, REMOVED_BEFORE] expected 1 but was 2») |
| Mg: «Hacer una copia ahora» pide la copia también con la bóveda cerrada (i-3) | 1 en rojo | `PlatformSourcesTest` «the A6 texts come from one function…» |
| Mh: la marca de D-3 incluye `VaultFile.revision` | 2 en rojo | `whatIsAndIsNotAChangeForTheCalendar` («expected 1 but was 2»), `aCalendarCopyIsSkippedWhileNothingChanged` |

### 5. Menores
- **m-4:** cubierto por `whatIsAndIsNotAChangeForTheCalendar`. Una comprobación de una copia y la purga de la papelera en el desbloqueo no hacen copia. «Importar copia» deja `copiedMark = null` y la apertura siguiente copia.
- **m-5:** el KDoc de la clase `AutoBackup` describe el salto de D-3, lo que nunca se salta y el resultado por petición.
- **i-3:**
  - «Hacer una copia ahora» con la bóveda bloqueada ya no pide la copia: viviría solo en memoria (`SettingsScreen.kt:334`).
  - El texto ya no la promete: «La copia se hace con la bóveda abierta: ábrela (pestaña Bóveda) y vuelve a pulsar «Hacer una copia ahora».» (`AUTO_BACKUP_NOW_NEEDS_OPEN`, `vault/BackupTargetTexts.kt:52`).
  - La primera copia de una carpeta recién elegida no depende de la memoria: `lastAt` nulo nunca se salta.
- **i-1:** comprobar la precisión de `lastModified` en el móvil va a `pendientes.md`. El controlador lo apunta.

### 6. Tabla de los textos de A6, sobre la función pura
Todo texto de resultado de A6 que se enseña sale de `a6Text(failures, a6Summary(targets, a6))` (`A6Text.kt:45`). Lo usan `publish` (`AutoBackup.kt:555-575`), que lo deja en `AutoBackupResult.text` y se enseña en `AppNavHost.kt:220`, y el diálogo de la maestra (`SettingsScreen.kt:419-423`). En la tabla, R es la petición de ahora (`currentA6Request`, `A6Text.kt:24`) y «hecha» quiere decir `replaceDone.request == R`.

| Texto | Condición exacta (en `a6Text` / `a6Summary`) | `archivo:línea` | Test |
|---|---|---|---|
| Fallos (`AUTO_BACKUP_WRITE_FAILED`, `_NO_PERMISSION`, `_NO_FOLDER`, `_VAULT_PROBLEM`, `_FAILED`), uno por línea, delante | `failures` no vacía: un fallo con `toldKey` de esta pasada, no dicho antes en ninguna carpeta con la petición (`AutoBackup.kt:848`). En el diálogo de la maestra, el mensaje de `ReplaceStatus.Failed`. | `A6Text.kt:47`; `AutoBackup.kt:557`; `SettingsScreen.kt:423` | `a6FailuresAreToldOnceInAll`, `a6SameFailureInTwoFoldersIsToldOnce` |
| `AUTO_BACKUP_REPLACED` | Al menos una hecha, y `notReplaced == 0` con una sola carpeta | `A6Text.kt:48-51` | `AutoBackupF3Test` «COPIAS-2…», `aRequestOf143IsTheRequestOfNow`, `aFolderThatKeepsFailingNeverHidesTheCountOfTheOthers` (el final) |
| `AUTO_BACKUP_REPLACED_SEVERAL` («…en cada carpeta») | Al menos una hecha, `notReplaced == 0` y más de una carpeta: **todas** las carpetas de ahora tienen un reemplazo de R | `A6Text.kt:48-51` | `everyCombinationOfFolders` (aserción `all2`), `a6PerTarget`, `theSuccessCountsTheUnlistedCopiesOfEveryPass` |
| «En la carpeta queda(n) N copia(s)…» (una carpeta) | Todas hechas, una sola carpeta y `unlisted > 0` (la suma guardada de R) | `A6Text.kt:52`; `AutoBackup.kt:170` | `AutoBackupF3Test` «COPIAS-2 …unlisted…», `aFolderThatKeepsFailing…` (el final) |
| «Al borrar las copias anteriores, en las carpetas quedaba(n)…» | Todas hechas, más de una carpeta y `unlisted > 0` | `A6Text.kt:52`; `AutoBackup.kt:170` | `theSuccessCountsTheUnlistedCopiesOfEveryPass`, `aReplacementCutByALockKeepsTheFirstFoldersCount`, `everyCombinationOfFolders` |
| «Se borraron … en R de las N carpetas. En la otra carpeta / En las otras K carpetas no se han borrado: puede que alguna se abra con una contraseña anterior. Míralo a mano.» | Al menos una hecha y alguna no hecha (fallida, pendiente, añadida después, quitada antes, o con un resultado de otra petición). Por construcción, N es al menos 2. | `A6Text.kt:55`, `:63` | `aFolderRemovedBeforeTheChangeAndAddedBackIsNotSaidDeleted` (A), `aFailureThenALockInReplaceNowIsNotSaidDeleted` (B), `noSuccessWhileAFolderThatDidNotTry…`, `noSuccessWhenALockLeavesAFolderOut`, `aFailedListingIsNeverAZeroCount`, `everyCombinationOfFolders` |
| «En la carpeta / En las carpetas en que se borraron quedaba(n) N copia(s)…» | El parcial con `unlisted > 0` | `A6Text.kt:56`, `:76` | `aFolderThatKeepsFailingNeverHidesTheCountOfTheOthers`, `theSuccessCountsTheUnlistedCopiesOfEveryPass` (tras el fallo) |
| (nada) | `told` vacío y el resumen igual al dicho (`A6State.said`), o sin ninguna hecha, o con otra generación | `AutoBackup.kt:558-560` | `aFolderThatKeepsFailing…` (sin cambios no se repite), `anImportRightAfterAReplacementMakesNoSuccess` |
| `AUTO_BACKUP_OLD_NOT_DELETED` / `_NO_RETRY` | Son fallos de una carpeta (fila 1): la copia se hizo pero no se borraron sus destinos; `_NO_RETRY` cuando la petición ya no está guardada (una importación) | `AutoBackup.kt:140`, `:147`, `:817`, `:842` | `AutoBackupF3Test` «R-6…», `anImportDuringA6NeverSaysTheOldCopiesWereDeleted` |
| `AUTO_BACKUP_REPLACE_WAITS` | Respuesta de «Borrar…» con la bóveda bloqueada: diálogo de la maestra, `ReplaceStatus.Waits` | `AutoBackup.kt:197`; `SettingsScreen.kt:422` | `a6PerTarget`, `aReplacementCutByALockKeepsTheFirstFoldersCount` |
| Diálogo de la maestra con «Done» pero parcial | `ReplaceStatus.Done` y `notReplaced > 0`: el texto parcial de `a6Text`, en negativo, y «Borrar…» se vuelve a ofrecer | `SettingsScreen.kt:397`, `:420` | `aFailureThenALockInReplaceNowIsNotSaidDeleted` (el resumen que lee el diálogo) |

**Casos que cubre la tabla:**
- **Carpeta quitada y vuelta a añadir:** con la petición pendiente, `READDED_WAITING`, cuenta como no hecha; ya hecha, `READDED_DONE`, cuenta como hecha. Tests: `aFolderAddedAgainBringsItsRequestBack` y el exhaustivo.
- **Quitada antes del cambio:** caminos A y `REMOVED_BEFORE`.
- **Importación a mitad:** `anImportDuringA6…` y `anImportRightAfter…`.
- **Bloqueo a mitad de una pasada:** camino B y `noSuccessWhenALock…`.
- **Varias pasadas:** I-2 y `theSuccessCounts…`.
- **Una sola carpeta:** los tests de la 1.x.

### Verificación (después del reinicio, en 3b8fc4d)
- `export ANDROID_HOME=/home/user/android-sdk; ./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets --no-daemon --max-workers=1`: salida 0, **2228** tests sin fallos. Lint: 0 errores y los mismos 20 avisos.
- `./gradlew -p jvm-check test --no-daemon --max-workers=1`: salida 0, **2191** tests sin fallos (antes 2184).

Las dos se ejecutan por separado. Juntas, en un solo comando, el contenedor mató el proceso por memoria (salida 137).

### Revisión experto-seguridad-cripto (ronda 4)
- **Veracidad (CWE-451): la clase de fallo se cierra por construcción.**
  - Solo una función pura redacta (`A6Text.kt:45`), y la fija un test de fuentes.
  - «En cada carpeta» depende de un dato guardado por carpeta y por petición (`replaceDone.request == R`, `A6Text.kt:32`, `:49`), no de una condición derivada.
  - Cada estado que llevaba a un texto falso es una fila del test exhaustivo: carpeta añadida después, quitada antes, vuelta a añadir, fallida, o con un resultado de otra petición.
- **Borrado:** sin cambios. Solo ids de la lista propia (`rotate`). El id de la petición no cambia lo que se borra, solo lo que se dice.
- **Datos guardados:** `replaceRequest` es un UUID aleatorio y `replaceDone` son dos contadores. En `settings.json` no queda ningún secreto ni nada de la bóveda.
- **i-3:** ya no se promete una copia que solo vivía en memoria.
- Sin red, permisos, dependencias ni registros nuevos.

### Revisión Ingeniero Móvil Senior (ronda 4)
- **Concurrencia:** `publish` corre bajo el `Mutex` del motor (`AutoBackup.kt:555`). `retell` también (`:582`, con `scope.async`). `onA6` escribe los ajustes de forma síncrona en `AppContainer.recordA6` (`AppContainer.kt:835`), que comprueba que la bóveda sigue en la lista.
- **Persistencia:** los campos nuevos son opcionales (`replaceRequest`, `replaceDone`, `a6`), así que se siguen leyendo los ajustes de la 1.4.3 y de las tareas 2 a 8. La petición de la 1.4.3 se lee como `LEGACY_A6_REQUEST` (test `aRequestOf143IsTheRequestOfNow`).
- **UI:**
  - el texto del diálogo de después de desbloquear queda fijado en el resultado cuando se publica, y no depende de cuántas carpetas haya cuando se enseña;
  - el diálogo de la maestra lee el mismo resumen de los ajustes;
  - sin estado nuevo en `rememberSaveable`.
- **Tests:** deterministas (`runTest`, `runCurrent`, `settle`). El exhaustivo es puro: 252 combinaciones, sin E/S.

### Textos para el Abogado Digital (ronda 4; también en el anexo)
- **Nuevos:**
  - «Se borraron las copias automáticas y las de antes de importar hechas antes del cambio de contraseña (las que la app tenía apuntadas) en N de las M carpetas.»;
  - «En la otra carpeta no se han borrado: puede que alguna se abra con una contraseña anterior. Míralo a mano.» y «En las otras K carpetas no se han borrado: …»;
  - «En la carpeta en que se borraron quedaba 1 copia con nombre de copia de la app … Si no la necesitas, bórrala a mano.» y «En las carpetas en que se borraron quedaban N copias…»;
  - «La copia se hace con la bóveda abierta: ábrela (pestaña Bóveda) y vuelve a pulsar «Hacer una copia ahora».» (sustituye a «…se hará en cuanto la abras.»).
- **Se quitan:** «En N carpetas no se ha comprobado…», de la ronda 3.
- **Sin cambios:** los demás textos de A6, con su condición en la tabla de arriba.

### Dudas (ronda 4)
- **El momento en que se guarda lo dicho.** `A6State.said` se guarda al publicar, no al enseñarse. Si el proceso muere antes de que se vea el diálogo, ese resumen no se repite, igual que la 1.x perdía su éxito. La petición y los resultados siguen guardados, y el diálogo de la maestra los lee.
- **Una carpeta vuelta a añadir con una petición anterior.** Si una carpeta tiene una petición R0 y entretanto se pidió otra, R, cuenta como no hecha para R aunque termine la suya de R0. El texto dice entonces «no se han borrado…» con «puede que», que es la dirección prudente.
- **El avance parcial se publica.** Como pide el ruling, se publica cada vez que cambia el resumen: puede haber más de un diálogo en una misma petición, por ejemplo «1 de 2» y después «en cada carpeta».
- **Los fallos se siguen apuntando por carpeta** (`replaceTold`, unidos en `toldInRequests`), como desde la ronda 1. El id de la petición no los sustituye, porque un «Volver a intentarlo» (`newRequest`) los reinicia.

## Ronda 5 de arreglos, la última (re-revisión `task-8-rereview-4.md`; «Ruling (advisor, T8 ronda 5, la última)»)

Base `66bf6e8`. Commits sin push:
- código: el commit de m-1, m-2, m-3, m-5, m-6, m-7 y m-8;
- tests: d6073de;
- anexo y KDoc: 9cc32cb;
- cierre: «2.0, tarea 8: ronda 5 de arreglos».

### Lo hecho
- **m-2:**
  - `onA6` devuelve si guardó, y `AppContainer.recordA6` (`AppContainer.kt:835`) devuelve `false` si falla la escritura o si la bóveda ya no está.
  - `replaceNow` devuelve `AUTO_BACKUP_FAILED` **antes de tocar ninguna carpeta** si el id no quedó guardado o no se lee igual (`vault/AutoBackup.kt:641`).
  - La salida `FAILED` que no se apuntaba ahora pasa por `failedFor` (`:652`, `:679`), que apunta el resultado con `onResult`: la carpeta queda en la petición, sin resultado.
  - Su texto es «No se pudo hacer la copia automática.», sin «Se volverá a intentar».
- **m-7:** «lo último dicho» (`A6State.said`) se guarda cuando el usuario cierra el diálogo, no al publicar.
  - Lo hace `replaceOutcomeShown` (`AutoBackup.kt:390-398`), bajo el cerrojo del motor y solo si la petición sigue siendo la de ahora. Un resumen de todas las carpetas hechas cierra la petición (`A6State.closed`, `vault/BackupSchedule.kt:113`), y de ella ya no se dice nada más (`AutoBackup.kt:575`).
  - **Vuelve a publicar tras un reinicio, aunque no quede nada pendiente:** cada desbloqueo pasa por `publish` (`AutoBackup.kt:542`). Si el resumen no se cerró, se vuelve a decir.
- **m-1:** «Cambiar de carpeta» recupera de `pastTargets` con el mismo código que añadir (`restored`, `settings/Settings.kt:199`, `:219`): la petición, el resultado y la marca.
  - Con esto, el aviso «…no se borraron: siguen en su carpeta» al cambiar de carpeta depende solo de que la carpeta anterior tuviera la petición. Su petición se queda con sus listas en `pastTargets` (`SettingsScreen.kt`, `dropped`).
- **m-3:** el diálogo de la maestra trata `Done` sin ninguna carpeta hecha como desconocido: sin texto de resultado y con «Borrar…» activo (`ui/screens/SettingsScreen.kt:398-400`, `:423`). En el test exhaustivo, `a6Text` con un fallo nunca da un texto vacío, y con una carpeta hecha tampoco.
- **m-4:** el test de fuentes fija ahora:
  - `a6PartialText` y `a6UnlistedInDoneText` solo se usan en `A6Text.kt`;
  - `autoBackupUnlistedText` solo en `A6Text.kt`, `AutoBackup.kt` y `ToolTexts.kt`;
  - `a6Text` solo en `AutoBackup.kt` y `SettingsScreen.kt`;
  - `outcome.post(` aparece una sola vez;
  - `ReplaceOutcomeDialog` recibe `shown.text`.
- **m-5:** los KDoc de `replaceOutcome`, `newA6RequestId` (un UUID en texto) y `aFailedListingIsNeverAZeroCount`, y las líneas 448 y 449 del anexo, ya dicen la regla de ahora.
- **m-6:** `retell` ya no se espera. Lanza en el `scope` del motor, bajo su cerrojo (`AutoBackup.kt:599`). El orden de los cerrojos no cambia: siempre el del motor primero y el de los ajustes dentro, también en `replaceOutcomeShown`. Ajustes no espera a una copia en curso.
- **m-8:** «En la otra carpeta puede quedar alguna copia de antes del cambio de contraseña: míralo a mano.» y «En las otras K carpetas puede quedar alguna…» (`vault/A6Text.kt:70`, `:72`), también en el anexo.

### Punto 2: escrituras de ajustes del camino de A6 cuyo fallo se traga

| Escritura | Dónde | Si falla solo esa escritura, el texto… | ¿Afirma algo falso? |
|---|---|---|---|
| El id de una petición nueva (`onA6(A6State(R))`) | `AppContainer.kt:835`, `AutoBackup.kt:641` | Antes: las carpetas quedaban en R y la bóveda en R0, y el resumen no contaba nada o contaba un R0 viejo. **Ahora:** `replaceNow` devuelve `AUTO_BACKUP_FAILED` sin tocar nada | Antes podía (m-2); **arreglado**. Test `aRequestWhoseIdIsNotSavedTouchesNothing` |
| Lo dicho y el cierre (`onA6(... said, closed)`) al cerrar el diálogo | `AutoBackup.kt:398` | El mismo resumen se vuelve a decir en el siguiente desbloqueo | No: es una repetición |
| La petición guardada con la bóveda cerrada (`onResult`, `replaceRequested`) | `AppContainer.kt:808`; `waitForUnlock` | `waitForUnlock` vuelve a leer la carpeta y responde `AUTO_BACKUP_FAILED`, no «Se hará…». La carpeta no entra en la petición y el resumen la cuenta como no hecha («puede quedar…») | No: queda corto |
| Un reemplazo hecho (`onResult`, `replaced`) | `AppContainer.kt:808` | La carpeta sigue pendiente y sin resultado, y el resumen la cuenta como no hecha («puede quedar…»). La pasada siguiente encuentra sus destinos ya borrados, termina y lo apunta | No: queda corto, y se corrige solo |
| Un fallo de una carpeta (`onResult`, `toldKey`) | `AppContainer.kt:808` | Ese fallo se puede volver a decir | No: es una repetición |
| El fallo de un «Borrar…» con la bóveda abierta (`failedFor`, `newRequest`) | `AutoBackup.kt:679`; `AppContainer.kt:808` | La carpeta conserva el id de una petición anterior, y su `replaceDone` (si lo tiene) es de otro id, que el filtro no cuenta | No: queda corto |
| Una copia de antes de importar (`onImportCopy`) | `AppContainer.kt:824` | No queda apuntada. A6 no la borra, pero `rotate` la cuenta como copia sin apuntar por su nombre y lo avisa | No |
| La marca de D-3 (`copiedMark`, en el `onResult` de una copia) | `AppContainer.kt:808` | Se queda la marca anterior, y puede salir una copia de más | No: no hay texto de A6 |
| Olvidar las copias para una importación | `SettingsStore.forgetAutoBackupCopiesForImport` | No se traga: lanza y la importación se rechaza | — |
| Quitar o cambiar una carpeta | `SettingsStore.removeTarget`, `updateVault` | No se traga: Ajustes dice «No se pudo guardar el ajuste» | — |

Después del arreglo de m-2, ninguna escritura tragada lleva a afirmar algo falso. Todas, como mucho, dejan el texto corto o lo repiten.

### Tabla de los textos de A6, al día
Todo sale de `a6Text(failures, a6Summary(targets, a6))` (`vault/A6Text.kt:45`). Solo lo publica `publish` (`AutoBackup.kt:571`, el único `outcome.post`, `:583`), y el diálogo de la maestra lo lee (`SettingsScreen.kt:398-427`). «Hecha» quiere decir `replaceDone.request == R`, la petición de ahora.

| Texto | Condición exacta | `archivo:línea` | Test |
|---|---|---|---|
| Fallos, uno por línea | Un `toldKey` nuevo de la pasada; en el diálogo de la maestra, el mensaje de `Failed` | `A6Text.kt:47`; `AutoBackup.kt:572` | `a6FailuresAreToldOnceInAll`, `a6SameFailureInTwoFoldersIsToldOnce` |
| `AUTO_BACKUP_FAILED` (sin reintento) | El id de una petición nueva no se guardó, o una carpeta no se pudo abrir en «Borrar…» | `AutoBackup.kt:641`, `:679` | `aRequestWhoseIdIsNotSavedTouchesNothing`, `aFolderThatCannotBeOpenedRecordsItsFailure` |
| `AUTO_BACKUP_REPLACED` | Al menos una hecha y todas las carpetas hechas, con una sola carpeta | `A6Text.kt:48-51` | `AutoBackupF3Test` «COPIAS-2…», `aRequestOf143IsTheRequestOfNow` |
| `AUTO_BACKUP_REPLACED_SEVERAL` | Igual, con más de una carpeta | `A6Text.kt:48-51` | `everyCombinationOfFolders` (512 + 64 combinaciones, también `MOVED_TO_*`), `a6PerTarget` |
| «En la carpeta queda(n)…» o «Al borrar las copias anteriores, en las carpetas quedaba(n)…» | Todas hechas y `unlisted > 0` (la suma guardada de R) | `A6Text.kt:52`; `AutoBackup.kt:170` | `theSuccessCountsTheUnlistedCopiesOfEveryPass`, `everyCombinationOfFolders` |
| «Se borraron … en R de las N carpetas. En la otra carpeta / En las otras K carpetas puede quedar alguna copia de antes del cambio de contraseña: míralo a mano.» | Al menos una hecha y alguna no | `A6Text.kt:55`, `:63-73` | Caminos A y B, `aFolderThatKeepsFailing…`, `everyCombinationOfFolders` |
| «En la carpeta / En las carpetas en que se borraron quedaba(n)…» | Parcial con `unlisted > 0` | `A6Text.kt:56`, `:78` | `aFolderThatKeepsFailing…`, `theSuccessCounts…` |
| (nada) | Sin fallo nuevo y con el resumen igual a lo último cerrado (`said`), o sin ninguna hecha, o la petición cerrada (`closed`), o con otra generación | `AutoBackup.kt:575-577` | `aSummaryClosedIsNotToldAgain`, `anImportRightAfterAReplacementMakesNoSuccess` |
| (otra vez, tras reiniciar) | Un resumen que no se cerró se vuelve a decir en el siguiente desbloqueo | `AutoBackup.kt:542` | `aSummaryNotClosedIsToldAgainAfterARestart` |
| `AUTO_BACKUP_OLD_NOT_DELETED` / `_NO_RETRY` | Fallos de una carpeta: la copia se hizo y no se borraron sus destinos; `_NO_RETRY` sin la petición guardada | `AutoBackup.kt:140`, `:147` | `AutoBackupF3Test` «R-6…», `anImportDuringA6…` |
| `AUTO_BACKUP_REPLACE_WAITS` | «Borrar…» con la bóveda bloqueada | `AutoBackup.kt:197` | `a6PerTarget` |
| Diálogo de la maestra: parcial / desconocido | `Done` con `notReplaced > 0` y `replaced > 0`: el parcial. `Done` con `replaced == 0`: nada y «Borrar…» activo | `SettingsScreen.kt:398-400`, `:421-423` | (el resumen que lee: camino B) |

### Tests nuevos y su rojo sin el arreglo
Cada mutación se aplicó sola (`scratchpad/mut5.py`), con `./gradlew :app:testDebugUnitTest --tests 'app.seguridad.vault.AutoBackupTargetsTest' --tests 'app.seguridad.PlatformSourcesTest'`. Después de cada una se volvió con `git checkout -- app/src/main`.

| Mutación | Salida | Test en rojo |
|---|---|---|
| M5a: se sigue aunque no se guarde el id | 82 tests, 1 en rojo | `aRequestWhoseIdIsNotSavedTouchesNothing` («expected No se pudo hacer la copia automática. but was null») |
| M5b: la salida `FAILED` sin apuntar | 1 en rojo | `aFolderThatCannotBeOpenedRecordsItsFailure` (`replaceRequest` nulo) |
| M5c: lo dicho se guarda al publicar | 1 en rojo | `aSummaryNotClosedIsToldAgainAfterARestart` |
| M5d: sin `closed` | 1 en rojo | `aSummaryClosedIsNotToldAgain` (una carpeta añadida meses después hacía publicar) |
| M5e: «Cambiar de carpeta» solo con las listas (m-1) | 1 en rojo | `everyCombinationOfFolders` («[REPLACED, MOVED_TO_DONE] expected 2 but was 1») |
| M5f: `a6Text` sin los fallos (m-3) | 2 en rojo | `everyCombinationOfFolders` («[FAILED, FAILED]»: texto vacío), `theSuccessCountsTheUnlistedCopiesOfEveryPass` |
| M5g: un segundo `outcome.post` (m-4) | 1 en rojo | `PlatformSourcesTest` «the A6 texts…» (esperaba 1, era 2) |

m-6 y m-8 no tienen test propio. El I-2 de la ronda 4 se adaptó con un `runCurrent()` después de `retell`, y los textos de m-8 los cubren los tests que usan `a6PartialText`.

### Verificación (por separado)
- `export ANDROID_HOME=/home/user/android-sdk; ./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets --no-daemon --max-workers=1`: salida 0, **2232** tests sin fallos. Lint: 0 errores y los mismos 20 avisos.
- `./gradlew -p jvm-check test --no-daemon --max-workers=1`: salida 0, **2195** tests sin fallos.

### Revisión experto-seguridad-cripto (ronda 5)
- **La última grieta de «nunca se suma una cuenta anterior» (m-2) está cerrada.** Sin el id guardado no hay petición (`AutoBackup.kt:641`), y toda carpeta que lo intenta apunta su resultado (`:679`).
- **Escrituras tragadas (punto 2):** ninguna lleva ya a una afirmación falsa. Las que fallan dejan el texto corto o lo repiten.
- **`said` al cerrar (m-7):** la advertencia de COPIAS-2 («puede que se abra con una contraseña anterior») ya no se pierde con la muerte del proceso.
- **m-8:** el texto es verdadero tenga lo que tenga la carpeta.
- **Sin cambios en:** el borrado (solo ids de la lista propia), la red, los permisos, los registros y las dependencias.

### Revisión Ingeniero Móvil Senior (ronda 5)
- **Concurrencia:** `retell` y `replaceOutcomeShown` lanzan en el `scope` del motor, bajo su `Mutex`, sin esperar (`AutoBackup.kt:390-398`, `:599`). El orden de los cerrojos sigue siendo motor, después ajustes. Ajustes ya no espera a una copia lenta.
- **Persistencia:** `A6State.closed` tiene valor por defecto, así que se siguen leyendo los ajustes de la ronda 4.
- **Ciclo de vida:**
  - lo dicho se guarda al cerrar el diálogo;
  - cada desbloqueo vuelve a publicar un resumen que no se cerró;
  - una petición cerrada no vuelve a hablar, aunque se añadan carpetas.
- **UI:** el diálogo de la maestra nunca enseña un texto rojo vacío (`SettingsScreen.kt:400`, `:423`).

### Textos para el Abogado Digital (ronda 5; también en el anexo)
- **Cambia (m-8):** «En la otra carpeta puede quedar alguna copia de antes del cambio de contraseña: míralo a mano.» y «En las otras K carpetas puede quedar alguna copia de antes del cambio de contraseña: míralo a mano.». Sustituyen a «…no se han borrado: puede que alguna se abra con una contraseña anterior. Míralo a mano.».
- **Anexo:** las líneas 448 y 449 ya dicen la regla de las rondas 4 y 5, sin contradecir la 450.
- **Sin textos nuevos más:** `AUTO_BACKUP_FAILED` ya existía.

### Dudas (ronda 5)
- **Un resumen no cerrado se vuelve a decir en cada desbloqueo hasta que se cierre.** Es lo que pide m-7 («sin pérdida»). Si el diálogo se cierra sin leerlo, cuenta como dicho.
- **`closed` solo se pone con un resumen de todas las carpetas hechas.** Un parcial cerrado deja la petición abierta: si después cambia el resumen (otra carpeta termina, o se quita la que faltaba), se dice otra vez.
- **i-3 (TalkBack bajo el diálogo de la maestra) e i-1 (la precisión de `lastModified`)** van a `pendientes.md`. Los apunta el controlador.
