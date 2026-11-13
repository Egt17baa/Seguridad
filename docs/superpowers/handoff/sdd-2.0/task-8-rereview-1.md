# Repaso de la ronda 1 de la tarea 8 (Clavero 2.0)

- **Alcance:** de la base `7a389da` a la cabeza `59884ec`, solo los puntos de `task-8-review.md` (I-1 a I-3 y M-1 a M-9) y las decisiones del controlador (D-8, D-12 y la letra de I-1 a I-3). No entran los cambios de `docs/superpowers/handoff`.
- **Cómo se hizo:** solo lectura, sin subagentes y sin volver a lanzar la suite. No ejecuté ningún test: lo que sigue sale de leer el código, con las líneas de `59884ec`.
- **Lentes:** `experto-seguridad-cripto` (`SKILL.md`: un hallazgo de seguridad confirmado es Important como mínimo), `ingeniero-movil-senior:desarrollo-movil-senior` y `auditoria-seguridad-movil`.

### Finding Verdicts

| Punto | Veredicto | Evidencia |
|---|---|---|
| **I-1** | **ADDRESSED** | `settings/Settings.kt:176-184`, `:198`, `:217-221`; test `AutoBackupTargetsTest.kt:425` |
| **I-2** | **ADDRESSED** | `vault/AutoBackup.kt:496`, `:504-517`, `:770-775`; tests `:476` y `:520` |
| **I-3** | **NOT ADDRESSED** (abierta solo la tercera cláusula del controlador) | `vault/AutoBackup.kt:428-445`, `:461-487`, `:504-509`, `:551-556`, `:740-743`; `ui/AppNavHost.kt:220` |
| **M-1** | **ADDRESSED** | `AutoBackup.kt:373-385`, `:395-399`; `VaultScreen.kt:299`; test `:652` |
| **M-2** | **ADDRESSED** | `AutoBackup.kt:448-450`; `AppContainer.kt:472` |
| **M-3** | **ADDRESSED** | `AutoBackup.kt:467`, `:479`, `:490`, `:776`; test `:672` |
| **M-4** | **ADDRESSED** | `AutoBackup.kt:439`, `:716-718`; test `:694` |
| **M-5** | **ADDRESSED** | `Settings.kt:147-157`; test `:711` |
| **M-6** | **ADDRESSED** | `BackupCheck.kt:142`; `VaultBackup.kt:271`; `BackupCheckTest.noticeAfter30Days` |
| **M-7** | **ADDRESSED** | `SettingsStore.kt:150-158`; `threeTargetsMax` |
| **M-8** | **ADDRESSED** | de (a) a (h), con un test cada uno (lista abajo) |
| **M-9** | **ADDRESSED** | `BackupCheckTexts.kt`, los KDoc de `VaultBackup.kt` y de `BackupCheckScreen.kt` |
| **D-8** | **ADDRESSED** | `BackupCheckTexts.kt` (`backupCheckDaysAgo`); test; anexo |
| **D-12** | **ADDRESSED** (con N-1) | `AppContainer.kt:556`; `VaultStore.kt:1415`; `Vault.kt` (`VaultCrypto.newTag`); test `BackupCheckTest.importAsNewVaultGetsItsOwnTag` |

**I-1: ADDRESSED.**
- `withTargetAdded` recupera de `pastTargets` el número de copias y el calendario (`Settings.kt:180`). `withTargetFolderChanged` rota con el mayor de los dos números (`:198`). `keptPast` guarda el calendario (`:219`).
- El test `aFolderChosenAgainKeepsItsNumberOfCopies` no usa la ayuda que pasaba el número a mano. Llama a `withTargetAdded(uri, ON_CHANGE)` como Ajustes (`SettingsScreen.kt:236`), en dos casos: quitar la carpeta y volver a añadirla en la 2.0, y venir de la 1.4.3 con la copia desactivada y «Conservar las 10». En los dos se conservan las 10 copias.
- Ningún cambio de ajustes borra copias que el usuario quería conservar:
  - volver a añadir una carpeta recupera su número;
  - cambiar a una carpeta pasada se queda con el mayor;
  - `saneVault` solo corrige valores que no son una opción (por ejemplo, 7 pasa a 5).
- **Elección del implementador, que también vuelva el calendario: aceptable.** El aviso de «Copia automática activada…» lee el calendario guardado (`SettingsScreen.kt:245-251`), así que dice la verdad, y la tarjeta de la carpeta lo enseña.

**I-2: ADDRESSED.**
- Lo avisado es por petición: es la unión de los `replaceTold` de los destinos que tienen la petición (`AutoBackup.kt:496`), y `attempt` no vuelve a avisar lo que está en esa unión (`:772`).
- Si el mismo fallo sale en dos carpetas en una sola ejecución, las dos lo apuntan, pero `tell()` quita los repetidos (`:510`) y el diálogo lo enseña una vez. Desde entonces, la unión lo contiene.
- `tell()` enseña todos los textos nuevos distintos en un solo resultado (`:510-515`). Todo lo que queda apuntado como avisado sale en el diálogo.
- Tests: `a6FailuresAreToldOnceInAll` (dos fallos distintos, cada uno una vez; el de «dos» en «uno» no se repite; el éxito, una vez) y `a6SameFailureInTwoFoldersIsToldOnce`.
- **Elección del implementador, todos los fallos juntos y uno por línea: aceptable.** Las líneas no dicen de qué carpeta es cada fallo («…en la carpeta elegida»). Cada tarjeta de Ajustes enseña el fallo de su carpeta. Va al Abogado (abajo).

**I-3: NOT ADDRESSED**, pero solo por la tercera cláusula del controlador («el diálogo nunca dice que se borró lo que no se borró»). Las dos primeras están cumplidas:
- **Cláusula 1, cumplida.** Los bucles toman de la instantánea solo los `uri`, y cada destino se vuelve a leer dentro del cerrojo justo antes de usarlo (`:436`, `:470`, `:554`).
- **Cláusula 2, cumplida.** Con un «Importar copia» a mitad, la carpeta siguiente ya no tiene la petición y hace una copia normal. En `replaceNow`, `since` (`:551`) con `stillAsked` (`:740-743`) impide `replaced` y da `AUTO_BACKUP_OLD_NOT_DELETED_NO_RETRY`. Un cambio de maestra a mitad bloquea la bóveda, así que las carpetas que siguen se saltan (`:478`). La petición nueva se guarda con `waitForUnlock`, y nunca queda `replaced` sin borrar. Lo cubre el test `anImportDuringA6NeverSaysTheOldCopiesWereDeleted`.
- **Cláusula 3, abierta.** `tell()` da por hecho el reemplazo con los resultados de las carpetas que **lo intentaron**, no con las que **tienen la petición**:
  - Un solo resultado `replaced` se publica como éxito (`AutoBackup.kt:505-508`), y con varios basta con que todos sean `replaced` (`:512`).
  - `AppNavHost.kt:220` redacta ese éxito con `vaultSettings.targets.size`. Con dos carpetas sale `AUTO_BACKUP_REPLACED_SEVERAL`: «Se hizo una copia nueva en cada carpeta y se borraron las copias … hechas antes del cambio de contraseña: las que la app tenía apuntadas en cada carpeta.».
  - Una carpeta que tiene la petición pero no lo intenta en esa ejecución no entra en `notices`, y el diálogo afirma un borrado que no se hizo, de copias que se abren con la maestra antigua.
- **Dos caminos, encontrados leyendo el código (no ejecutados):**
  - **(a) Con `runChanged`:**
    - Dos carpetas con la petición: «uno», `ON_CHANGE`, y «dos», `DAILY`. Al desbloquear fallan las dos y se avisa una vez.
    - «uno» se recupera (por ejemplo, ya hay espacio) y se guarda un cambio. `runChanged` salta «dos» en `:437` (`!wanted && !(change && ON_CHANGE)`), «uno» queda `replaced` y el diálogo dice «en cada carpeta».
    - Si «dos» sigue fallando con un texto ya avisado (por ejemplo, sin permiso), el usuario no vuelve a oír lo contrario. Cree borradas unas copias que no lo están.
  - **(b) Con un bloqueo a mitad de `runDue`:**
    - Primero se escribe y rota «uno». Después la bóveda se bloquea (a mano, o al salir de la app con «Al salir»).
    - «dos» se salta en `:478`, o `copyTo` devuelve `null` cuando `export` no exporta con la bóveda cerrada (`fill`, `defer`). Queda fuera de `notices` y se publica el éxito «en cada carpeta».
    - Este caso se corrige en el siguiente desbloqueo, pero mientras tanto el texto es falso.
- **Origen:** los dos caminos ya estaban en `7a389da`. Son el mismo `tell()` y el mismo `AppNavHost.kt:220`, y la revisión no los vio. Los apunto aquí porque la ronda reescribió `tell()` y porque son justo lo que pide la tercera cláusula. El controlador puede pasarlos a un hallazgo aparte. Por la regla de veracidad y por ser de A6 (copias con una maestra quizá comprometida), son **Important**.
- **Corrección propuesta:**
  - En `tell()`, volver a leer `targets()` después del bucle (`onResult` ya se esperó). El éxito, con uno o con varios resultados, solo si **ningún** destino sigue con `replacePending`.
  - Si alguno sigue con la petición y no hay ningún fallo nuevo que avisar, no se publica nada. La petición sigue y se avisará cuando se resuelva.
  - Test: (a) tal cual, que no salga `replaced` hasta que «dos» termine. Otro con el bloqueo inyectado en `onWrite` de «uno» con dos carpetas, que no salga el éxito.
- **Elección del implementador, el texto tras una importación a mitad de A6: correcto y verdadero.**
  - La copia se hizo: la de la carpeta en curso se queda sin apuntar (`stale`), y la de `replaceNow` se apunta. Las antiguas no se borraron.
  - `AUTO_BACKUP_OLD_NOT_DELETED_NO_RETRY` no promete un reintento (`AutoBackup.kt:143-147`). No hay reintento porque la importación quita la petición a propósito: `forgetAutoBackupCopiesForImport` llama a `forgettingAutoBackupCopies`, que llama a `withoutReplaceRequest` (`SettingsStore.kt:112-114`, `Settings.kt:224-225`). La pantalla de importar ya lo dice («Copia importada. » + `AUTO_BACKUP_REPLACE_DROPPED`, `VaultBackup.kt:181`).
  - Así se cumple la regla de `CLAUDE.md` («La petición sigue hasta borrar todos los destinos»): la petición sigue mientras exista, y aquí deja de existir por la regla de importar (1.4.0, R15 y R17), avisada al usuario.

**M-1: ADDRESSED.**
- El aviso se publica (`postScheduled`, `:380-385`) y se borra (`:398`) bajo `scheduledLock`, con la época de la ejecución.
- `takeScheduled` (`:373-378`) solo dice sí si la época sigue siendo la misma y la bóveda está abierta. Así cubre un bloqueo y un desbloqueo demasiado rápidos para el colector.
- El test `theCalendarNoticeNeverOutlivesALock` cubre los dos casos.

**M-2: ADDRESSED.** `launchDue` usa el `scope` de la bóveda (`AutoBackup.kt:448-450`), y `appOpened` lo usa (`AppContainer.kt:472`). Al borrar la bóveda se cancela con lo demás.

**M-3: ADDRESSED.**
- `failedAt` vive en memoria (`:776`). Abrir la app no reintenta antes de 5 min (`:479`, `:490`).
- Un reloj atrasado reintenta: `now >= it`.
- El desbloqueo borra la marca (`:467`). La espera no se aplica a A6 ni a lo que esperaba al desbloqueo (`:479`), ni a un cambio guardado (`runChanged` no la mira).

**M-4: ADDRESSED.**
- Antes de cada copia, `attempt` lee `revision` antes de la instantánea y la guarda en `copiedRevision` solo con la misma `generation`. `runChanged` salta una carpeta `ON_CHANGE` que ya copió esa revisión (`:439`).
- Si la revisión se lee antes de un cambio que la instantánea sí recoge, la consecuencia es como mucho una copia de más, nunca una de menos. Es la dirección segura.

**M-5: ADDRESSED.** `a6Targets` (`Settings.kt:147`) limita `replaceToldAny`, `firstBackupError` y `oldestBackupAt` a los destinos que tienen la petición mientras alguno la tenga.

**M-6: ADDRESSED.**
- Una fecha a más de un día en el futuro da `BACKUP_CHECK_DATE_AHEAD` (`BackupCheck.kt:142`).
- La clave de `remember` incluye el día (`VaultBackup.kt:271`). Ese día se cuenta en UTC y solo cuenta cuando la línea se vuelve a dibujar; basta, porque cualquier vuelta a la pantalla la vuelve a dibujar.
- **Elección del implementador, el texto «La fecha de tu última comprobación de una copia es posterior a hoy. Comprueba una copia otra vez.»: aceptable y verdadero.** «Hoy» es el día que marca el reloj del móvil, y al comprobar una copia otra vez la fecha se corrige (`withBackupChecked` apunta `now`). Va al Abogado.

**M-7: ADDRESSED.** Las listas de los destinos que sobran (un cuarto destino o una carpeta repetida) pasan a `pastTargets` con `keptPast` (`SettingsStore.kt:156-157`). El test `threeTargetsMax` comprueba `dup` y `d4`. Hay un detalle informativo en O-2.

**M-8: ADDRESSED.** Cada punto tiene su test:

| Punto | Test |
|---|---|
| (a) A6 con dos destinos en la misma carpeta | `a6InASharedFolderDeletesOnlyEachOnesOwn` (`:584`; «dos» recibe los ids de «uno» y uno ajeno, y solo borra los suyos) |
| (b) La carpeta que se deja | `aFolderLeftIsNeverTouched` (`:616`) |
| (c) Fallos de A6 con varios destinos (I-2) | `a6FailuresAreToldOnceInAll` |
| (d) Volver a añadir con el número anterior (I-1) | `aFolderChosenAgainKeepsItsNumberOfCopies` |
| (e) Una importación durante A6 (I-3) | `anImportDuringA6NeverSaysTheOldCopiesWereDeleted` |
| (f) Los días del cambio de hora | `BackupScheduleTest.daysOfTheChangeOfTime` (Europe/Madrid, 28-03-2027 y 31-10-2027) |
| (g) Ajustes antiguos con tipos malos | `aBadOldVaultLosesNothingElse` |
| (h) El aviso del calendario con un bloqueo (M-1) | `theCalendarNoticeNeverOutlivesALock` |

Ninguno cubre los dos caminos abiertos de I-3.

**M-9 y D-8: ADDRESSED.**
- `backupCheckDaysAgo` dice «Comprobaste una copia por última vez hace N días.».
- `BACKUP_CHECK_NOT_COUNTED` y `BACKUP_CHECK_NOTICE_MORE` están alineados con D-8 y con O-5.
- Los KDoc se han actualizado. `grep "última copia comprobada\|Tu última copia" app/src` no da nada.
- El test y el anexo (`2026-10-27-seguridad-1.3.0-textos.md`, «Clavero 2.0, tarea 8») tienen el texto nuevo.

**D-12: ADDRESSED.**
- `importAsNewVault` llama a `importReplace(…, newTag = true)` (`AppContainer.kt:556`), que pone un `VaultCrypto.newTag()` de 16 bytes de `SecureRandom` en `prepare` (`VaultStore.kt:1415`). «Importar copia» conserva el tag de la copia.
- El test comprueba que comprobar una copia de A no apunta nada en B, y que una copia de B sí cuenta para B.
- Queda N-1 (abajo): la fecha que hereda la bóveda nueva.

### New Issues in the Fix Diff

**N-1 (Minor): «Importar como bóveda nueva» hereda la fecha de comprobación de la bóveda de origen.**
- **Dónde:** `vault/VaultStore.kt:1415`. `prepare` solo cambia `vaultTag` y conserva el `lastCheckedBackupAt` de la copia. Nada lo pone a 0: `grep lastCheckedBackupAt app/src/main/java/app/seguridad/vault/` solo lo escribe en `withBackupChecked`.
- **Qué pasa:**
  - Una copia de A hecha después de una comprobación lleva esa fecha. La bóveda nueva B la hereda y su aviso calla hasta 30 días.
  - En Ajustes, `BACKUP_CHECK_NOTICE_MORE` dice que los días cuentan «desde que comprobaste … una copia de esta bóveda». Es falso para B: con su tag nuevo, nunca se ha comprobado ninguna copia suya.
  - Si se comprobó con el reloj adelantado, B hereda además `BACKUP_CHECK_DATE_AHEAD`.
- **Por qué no lo ve el test:** `importAsNewVaultGetsItsOwnTag` usa una copia de A con la fecha a 0.
- **Corrección:** `prepare = { if (newTag) it.copy(vaultTag = VaultCrypto.newTag(), lastCheckedBackupAt = 0L) else it }`. Al test se le añade comprobar A antes de hacer su copia y `assertEquals(0L, b.data().lastCheckedBackupAt)` después de importar.

**N-2 (Minor, documental): el informe dice que todos los textos están en el anexo, y no lo están.**
- **Qué dice el informe:** en «Textos para el Abogado Digital», `task-8-report.md` afirma «Todos estos textos están también en el anexo».
- **Qué hay en el anexo:** la sección nueva de `docs/superpowers/specs/2026-10-27-seguridad-1.3.0-textos.md` solo tiene dos viñetas.
- **Qué falta:** `grep` sobre `docs/` no encuentra:
  - `AUTO_BACKUP_ADD_FOLDER` («Añadir otra carpeta»), `AUTO_BACKUP_ALREADY_TARGET`, `AUTO_BACKUP_TOO_MANY` y `AUTO_BACKUP_REMOVED`;
  - el botón «Quitar esta carpeta» y el título «Carpeta N de la copia automática»;
  - `AUTO_BACKUP_SHORT` y `AUTO_BACKUP_MORE`, y lo que se añade a «Más información»;
  - los finales de «Copia automática activada…» y «Aún no se ha hecho ninguna copia…».
- **Por qué importa:** son la lista de la revisión final del Abogado Digital.
- **Corrección:** completar el anexo, o quitar esa afirmación del informe.

**N-3 (Minor, documental): el informe sigue citando el texto anterior a D-8.**
- La lista principal de textos de `task-8-report.md` dice todavía: `backupCheckDaysAgo`: «Tu última copia comprobada es de hace N días.» (D-8: posible choque con O-5).
- **Corrección:** cambiarlo por el texto de D-8, para que no llegue a la revisión final del Abogado.

No veo más problemas en el diff:
- `runDue` lee la época dentro del cerrojo.
- `replaceNow` devuelve `AUTO_BACKUP_FAILED` si se quitan todas las carpetas a mitad.
- `copiedRevision` y `failedAt` son `ConcurrentHashMap`, y `scheduledEpoch` solo se toca bajo `scheduledLock`.
- `saneVault` sigue siendo idempotente.
- La conversión de los ajustes sigue con clases tipadas.

### Out-of-Scope Observations

- **O-1 (Informativo, viene de la 1.x).** Un cambio de maestra mientras una copia está en vuelo no sube `generation`, porque `nextGeneration` solo se llama al crear, importar o borrar (`VaultStore.kt:504`, `:1441`, `:1546`).
  - Si la instantánea de esa copia es de antes del cambio, la copia queda cifrada con la maestra anterior.
  - `MasterChanged.changed` lee `oldCopies` en cuanto se guarda la maestra, quizá antes de que esa copia se apunte, y A6 no la incluiría.
  - Con una sola carpeta ya pasaba en la 1.x. Con varias, la ventana no crece, porque las carpetas que siguen se saltan al bloquearse la bóveda.
  - Se podría cerrar haciendo que `changeMaster` suba una época de copias, o que `replaceNow` añada las copias apuntadas cuya hora sea anterior al cambio.
- **O-2 (Informativo).** Con un `settings.json` dañado, `saneVault` (M-7) pasa a `pastTargets` el duplicado de una carpeta que sigue siendo destino, con el mismo `uri`. Es inofensivo, porque el motor nunca lee `pastTargets`:
  - al quitar o cambiar esa carpeta, `filter { it.uri != … }` lo descarta y sus listas se pierden, que es la dirección segura;
  - mientras tanto, `withTargetAdded` no puede añadirla, porque ya es destino.
- **O-3 (Informativo, viene de antes).** `Notice.post` sustituye un resultado de A6 que aún no se ha enseñado. Si llegan dos antes de cerrar el diálogo, los fallos apuntados como avisados del primero pueden no llegar a verse. Es raro, porque las dos ejecuciones van en serie bajo el cerrojo y el diálogo es modal.

### Verdict

**Hay que arreglarla antes de seguir.**
- I-1, I-2, M-1 a M-9, D-8 y D-12 están resueltos y bien probados.
- I-3 queda abierta por su tercera cláusula: el diálogo de A6 puede decir que se borraron las copias antiguas «en cada carpeta» cuando una carpeta con la petición no lo intentó (**Important**).
- Hay tres menores nuevos: N-1 (la fecha de comprobación heredada en «Importar como bóveda nueva»), y N-2 y N-3 (documentales).

## Revisión experto-seguridad-cripto

**Modelo de amenaza:** el de la revisión. Las copias cifradas con una maestra anterior son el activo crítico de A6. El usuario actúa según lo que le dice el diálogo.

| Punto | Evidencia | Valoración |
|---|---|---|
| A6, veracidad (CWE-451, información engañosa en la interfaz sobre una acción de seguridad) | `AutoBackup.kt:504-515`, `ui/AppNavHost.kt:220`, `AutoBackup.kt:437`, `:478` | El éxito se deduce de las carpetas que lo intentaron, no de las que tienen la petición. El usuario puede creer retiradas unas copias que se abren con una maestra quizá comprometida. **Important** (I-3, cláusula 3). |
| Borrado | `rotate`, `AutoBackup.kt:891-934` | Solo por id de la lista propia leída en el momento y presente en el listado. El test de la carpeta compartida (`:584`) lo fija. Correcto. |
| Lo avisado por petición | `:496`, `:772` | No se pierde ningún fallo: todo lo apuntado se enseña (`:510-515`). Correcto. |
| Carreras con una importación | `:551`, `:740-743`, la relectura en `:436`, `:470` y `:554` | Ningún `replaced` sin borrar. El texto sin promesa de reintento es verdadero. Correcto. |
| Bloqueo | `:373-385` (época y cerrojo comunes), `:478` | El aviso del calendario no sobrevive a un bloqueo. Correcto. |
| `vaultTag` (D-12) | `Vault.kt` `newTag`, `VaultStore.kt:1415` | 128 bits de `SecureRandom` (`newVaultTag`), dentro de datos autenticados con la maestra. No se puede fabricar para dar por comprobada una copia ajena. N-1 es de veracidad, no de cripto. |
| Reloj | `BackupCheck.kt:142`, `AutoBackup.kt:490` | Una fecha adelantada ya no apaga el aviso. El reloj atrasado no bloquea el reintento. Correcto. |
| Registros, red, permisos y dependencias | — | Nada nuevo. `lastError` y los avisos siguen siendo textos fijos. |

## Revisión Ingeniero Móvil Senior

| Área | Evidencia | Valoración |
|---|---|---|
| Ciclo de vida | `AppContainer.kt:472`, `AutoBackup.kt:448-450` | `runDue` ya va en el `scope` de la bóveda (M-2). `onStart` ya no hace tormentas de reintentos (M-3) ni copias dobles tras un cambio (M-4). |
| Concurrencia | `:428-445`, `:461-487`, `:551-556` | Todo va serializado por el `Mutex` de cada bóveda, y cada destino se vuelve a leer dentro de él. Los mapas en memoria son concurrentes. El aviso usa un `synchronized` propio y corto, sin suspender dentro. |
| Compose | `VaultScreen.kt:299`, `VaultBackup.kt:271` | `takeScheduled` en `LaunchedEffect`, con la clave del día en `remember`. El día es UTC: a lo sumo cambia unas horas antes o después del día local, y es aceptable. |
| SAF y Ajustes | `SettingsScreen.kt:236`, `:245-251` | Volver a añadir una carpeta recupera su número y su calendario, y el aviso lee lo guardado. |
| Estado | — | Nada nuevo en `rememberSaveable`. |

Pendiente para el móvil: I-3, cláusula 3.

## Textos para el Abogado Digital (revisión final)

- **D-8:** «Comprobaste una copia por última vez hace N días.»
- **O-5:**
  - `BACKUP_CHECK_NOTICE_MORE`: «Los días se cuentan desde que comprobaste con «Comprobar una copia» una copia de esta bóveda, no desde que se hizo la copia. Las copias de antes de la 2.0 y las de otra bóveda no cuentan.». Su veracidad para las bóvedas importadas como nuevas depende de N-1.
  - `BACKUP_CHECK_NOT_COUNTED`: «Esta comprobación no cuenta para el aviso «Comprobaste una copia por última vez»: la copia es de otra bóveda o de antes de la 2.0.»
- **M-6, nuevo:** `BACKUP_CHECK_DATE_AHEAD`: «La fecha de tu última comprobación de una copia es posterior a hoy. Comprueba una copia otra vez.»
- **I-2:** con varias carpetas que fallan de forma distinta después de cambiar la maestra, el diálogo enseña sus textos de siempre, uno por línea. No dicen de qué carpeta es cada uno («…en la carpeta elegida»). El Abogado valora si basta, o si cada línea debería nombrar la carpeta («Carpeta N»).
- **I-3:** `AUTO_BACKUP_OLD_NOT_DELETED_NO_RETRY` («La copia automática se hizo, pero no se pudieron borrar las antiguas.») aparece ahora también tras importar en mitad de A6. Es verdadero y no promete un reintento.
  - `AUTO_BACKUP_REPLACED_SEVERAL` («…en cada carpeta…») **no es verdadero en todos los casos** hasta que se cierre la cláusula 3 de I-3.
- **N-2:** antes de la revisión final, los textos de la tarea 8 que faltan en el anexo:
  - `AUTO_BACKUP_ADD_FOLDER`, `AUTO_BACKUP_ALREADY_TARGET`, `AUTO_BACKUP_TOO_MANY` y `AUTO_BACKUP_REMOVED`;
  - «Quitar esta carpeta» y «Carpeta N de la copia automática»;
  - `AUTO_BACKUP_SHORT` y `AUTO_BACKUP_MORE`;
  - los finales de «Copia automática activada…».
