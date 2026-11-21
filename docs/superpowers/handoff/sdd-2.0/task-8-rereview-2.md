# Repaso de la ronda 2 de la tarea 8 (Clavero 2.0)

- **Alcance:** de la base `59884ec` a la cabeza `c86db5b`, solo los puntos abiertos de `task-8-rereview-1.md` (la tercera cláusula de I-3, N-1, N-2 y N-3) y el riesgo que nombra el controlador (la cuenta de copias sin apuntar del éxito). No entran los cambios de `docs/superpowers/handoff`.
- **Cómo se hizo:** solo lectura, sin subagentes y sin volver a lanzar la suite. **No ejecuté ningún test**: lo que sigue sale de leer el código. Las líneas son las de `c86db5b`.
- **Lentes:** `experto-seguridad-cripto` (`SKILL.md`: un hallazgo de seguridad confirmado es Important como mínimo; un riesgo teórico se apunta como menor), `ingeniero-movil-senior:desarrollo-movil-senior` y `auditoria-seguridad-movil`.

### Finding Verdicts

| Punto | Veredicto | Evidencia |
|---|---|---|
| **I-3, cláusula 3** | **ADDRESSED** | `vault/AutoBackup.kt:506-516`; tests `AutoBackupTargetsTest.kt:744` y `:782` |
| **N-1** | **ADDRESSED** | `vault/VaultStore.kt:1416`; test `BackupCheckTest.kt:664` |
| **N-2** | **PARTIALLY ADDRESSED** (falta una variante) | `docs/superpowers/specs/2026-10-27-seguridad-1.3.0-textos.md`, sección «Clavero 2.0, tarea 8»; `vault/AutoBackup.kt:170` |
| **N-3** | **ADDRESSED** | `task-8-report.md:30` y `:109` |

**I-3, cláusula 3: ADDRESSED.**
- `tell()` solo publica el éxito si se cumplen dos cosas (`AutoBackup.kt:509`):
  - algún resultado de esta ejecución es `replaced`;
  - ningún destino sigue con `replacePending`, leído después del bucle. `onResult` ya guardó cada resultado dentro de `attempt`, así que esa lectura incluye los de esta ejecución.
- Con un fallo nuevo, se publica el fallo (`:513`). Si no hay ni fallo nuevo ni éxito, no se publica nada: la petición sigue y se dirá cuando se resuelva.
- El caso de un solo resultado (`:512`) también pasa por `done`. Ya no se publica un `replaced` suelto mientras otra carpeta conserva la petición.
- **Los dos caminos de la re-revisión 1 están cubiertos:**
  - **(a) `runChanged`.** El test `noSuccessWhileAFolderThatDidNotTryStillHasTheRequest` (`:744`) tiene «uno» `ON_CHANGE` y «dos» `DAILY`, las dos con la petición:
    - el primer desbloqueo falla en las dos;
    - un cambio guardado reemplaza «uno» y salta «dos»;
    - `assertNull(replaceOutcome)` y las copias viejas de «dos» siguen;
    - el éxito llega en el desbloqueo siguiente, cuando «dos» también reemplaza.
  - **(b) Un bloqueo a mitad de `runDue`.** El test `noSuccessWhenALockLeavesAFolderOut` (`:782`) bloquea en `onWrite` de «uno». Comprueba que no sale nada hasta que «dos» reemplaza.
- **Rojo con la regla anterior:** con `notices.size == 1 → post`, los dos tests fallan en el `assertNull`. Coincide con lo que dice el informe.
- **Lo que dice el texto:** los fallos («…en la carpeta elegida») no afirman ningún borrado. `AUTO_BACKUP_REPLACED_SEVERAL` («…en cada carpeta») ya solo sale con todas las peticiones cerradas. Quedan dos matices que no reabren la cláusula:
  - N-4, abajo: una carrera de microsegundos con una importación;
  - O-5, fuera de alcance: una carpeta quitada y vuelta a añadir.

**N-1: ADDRESSED.**
- `prepare` pone `vaultTag = VaultCrypto.newTag()` y `lastCheckedBackupAt = 0L` solo con `newTag` (`VaultStore.kt:1416`). «Importar copia» conserva las dos cosas.
- El test (`BackupCheckTest.kt:664`) primero comprueba una copia de A (7 000) y después hace la copia. Así se ve que la fecha viaja en la copia: la bóveda restaurada `c` la conserva (7 000) y la bóveda nueva `b` queda en 0.
- `BACKUP_CHECK_NOTICE_MORE` («…una copia de esta bóveda…») ya es verdadero para la bóveda importada como nueva.

**N-2: PARTIALLY ADDRESSED.**
- El anexo tiene ya todo lo que pedía la re-revisión 1. Lo comprobé contra el código:
  - `AUTO_BACKUP_SHORT`, `AUTO_BACKUP_MORE` y el párrafo de «Más información» (`SettingsScreen.kt:285-289`);
  - «Añadir otra carpeta», `AUTO_BACKUP_ALREADY_TARGET`, `AUTO_BACKUP_TOO_MANY` y `AUTO_BACKUP_REMOVED`;
  - «Quitar esta carpeta» y «Carpeta N de la copia automática» (`BackupTargetTexts.kt:17-29`, `SettingsScreen.kt:352`);
  - los finales de `scheduleAfter` (`BackupTargetTexts.kt:41-44`) con sus tres comienzos (`SettingsScreen.kt:249-251`);
  - «Aún no se ha hecho ninguna copia: se hará en unos segundos.», variante nueva para los destinos con calendario (`SettingsScreen.kt:314-319`; las otras tres son de la 1.4.0).
- **Falta una:** la variante de varias carpetas de `autoBackupUnlistedText` (`AutoBackup.kt:170`), «**En las carpetas** queda 1 copia…» y «**En las carpetas** quedan N copias…».
  - El anexo solo tiene «En la carpeta…» (líneas 127-129, de la 1.3.x).
  - El informe sí la cita (`task-8-report.md`, «empieza «En las carpetas …»»).
  - Es justo el texto que toca el riesgo con nombre, abajo, y cambiará con su arreglo.
- **Severidad:** Minor, documental.

**N-3: ADDRESSED.** La lista de textos del informe dice «Comprobaste una copia por última vez hace N días.» (`task-8-report.md:30`, `:109`). `grep "Tu última copia comprobada"` sobre el informe y el anexo no da nada.

### Named risk: the count

**Veredicto: CONFIRMADO, Important** (CWE-451, veracidad de lo que el diálogo afirma sobre las copias que quedan con la maestra anterior; regla COPIAS-2).

**Qué pasa.**
- `rotate` cuenta en cada carpeta las copias con nombre de copia de la app que la app no tenía apuntadas (`AutoBackup.kt:925-928`). Son copias de antes de la 1.2.1, de otra bóveda o de alguien más, y «puede que se abran con una contraseña anterior».
- Esa cuenta solo vive en el resultado de esa ejecución (`AutoBackupResult.unlistedLeft`). `BackupTargetSettings` no la guarda (`BackupSchedule.kt:30-41`).
- El éxito suma solo las `notices` de la ejecución que cierra la petición:
  - con varios resultados, `combined` (`:525`);
  - con uno, ese resultado tal cual (`:512`).
- Las carpetas que reemplazaron en una ejecución anterior no entran. Su cuenta tampoco se enseñó entonces, por dos motivos:
  - esa ejecución no publicó nada, porque otra carpeta seguía con la petición (`:509`);
  - o publicó un fallo, y `AppNavHost.kt:220` enseña `shown.error ?: replacedText(…)`, así que con un error la cuenta no se ve.

**Lo peor no es un número menor, es que la frase desaparece.** `replacedText` (`AutoBackup.kt:189-193`) solo añade «En las carpetas quedan N copias…» si N > 0. Si las copias sin apuntar están solo en las carpetas que terminaron antes, el diálogo dice «Se hizo una copia nueva en cada carpeta y se borraron…» y nada más. El usuario no sabe que en una carpeta quedan copias que pueden abrirse con la maestra que acaba de cambiar, quizá porque estaba comprometida.

**Caminos (leídos en el código, no ejecutados):**
1. **Un fallo y después un éxito.** Dos carpetas con la petición; «uno» tiene 2 copias sin apuntar.
   - Primer desbloqueo: «uno» reemplaza (`unlistedLeft = 2`) y «dos» falla por espacio. El diálogo enseña solo el fallo.
   - Se libera espacio y se guarda un cambio: «dos» reemplaza (`unlistedLeft = 0`), el único resultado de esa ejecución. Sale `AUTO_BACKUP_REPLACED_SEVERAL` sin la frase de las 2 copias.
2. **Los dos escenarios de los tests nuevos** (`:744`, `:782`). En los dos se cierra con un solo resultado, el de «dos». Una copia sin apuntar en «uno» no se diría. Los tests no lo comprueban.
3. **`replaceNow` cortado por un bloqueo** (`:562`, `:564`). Las carpetas que siguen pasan por `waitForUnlock` y se cierran después en `runDue`. Se pierde la cuenta de las primeras.
4. **No afectado:** un `replaceNow` en una sola pasada, con la bóveda abierta (`SettingsScreen.kt:404`). Ahí `results` tiene todos los destinos y la suma los cubre (`:577`).

**Origen.**
- En `59884ec` la rama `notices.size == 1` ya publicaba un único resultado con solo su cuenta.
- La ronda 2 lo convierte en el caso normal: todo cierre en más de una pasada pierde las cuentas anteriores, porque las pasadas anteriores ya no publican nada.

**«Nunca de más» no es exacto.**
- El predicado (`:925-928`) compara con las listas de ese destino solamente.
- Con dos destinos en la misma carpeta (el montaje de `a6InASharedFolderDeletesOnlyEachOnesOwn`), «uno» cuenta como sin apuntar las copias de «dos». Cuenta las viejas, que «dos» borra justo después, y las nuevas, hechas con la maestra nueva.
- La suma puede pasarse. Es la dirección segura y el texto dice «puede que», así que es solo una observación (O-6).

**Arreglo** (contar sobre todos los destinos al cerrar). Hay dos opciones; las dos dan una cifra completa:
- **(A) Guardar la cuenta por destino.** Un campo opcional en `BackupTargetSettings` (por ejemplo, `replaceUnlisted: Int? = null`, compatible con los ajustes guardados):
  - se guarda en `replaceAfter` cuando `r.replaced` (`Settings.kt:280`);
  - se borra cuando se guarda una petición nueva (`r.replaceRequested`, `:262-263`) o cuando `r.newRequest`;
  - en `tell()`, con `done`, la cifra es `targets().sumOf { it.replaceUnlisted ?: 0 }`, leída a la vez que `replacePending`.
  - Sobrevive a la muerte del proceso entre pasadas.
- **(B) Volver a listar cada carpeta en la pasada que cierra.** Dentro de `lock`, antes de soltarlo, con el predicado de `:925-928` sacado a una función. La cifra es la de ese momento.
- **Quitar el número no basta.** Solo vale si la frase deja de depender de N, por ejemplo una advertencia fija con varias carpetas. Si sigue condicionada a N > 0, se sigue perdiendo.
- **Test que hace falta:** una copia sin apuntar en «uno», que reemplaza en la pasada 1, y «dos» que reemplaza en la pasada 2. El éxito debe tener `unlistedLeft == 1`, y `replacedText(…, 2)` debe contener «En las carpetas queda 1 copia». Vale también uno de los dos tests nuevos con esa copia añadida.

### New Issues in the Fix Diff

**N-4 (Minor): `tell()` lee los destinos fuera del cerrojo, y una importación puede quitar la petición justo entonces.**
- **Dónde:** `AutoBackup.kt:509`. `tell` corre después de `lock.withLock` (`:444`, `:485`).
- **Qué pasa:**
  - `forgetAutoBackupCopiesForImport` quita la petición de todos los destinos, a través de `beforeReplace` de la importación (`AppContainer.kt:400`, `Settings.kt:224-225`), y no toma el cerrojo de `AutoBackup`.
  - Supongamos que «uno» reemplaza y «dos» se salta (por su calendario o por un bloqueo).
  - Si la importación cae entre el final del `attempt` de «uno» y la lectura de `targets()` en `tell`, `none { replacePending }` da verdadero y sale «se borraron… en cada carpeta».
  - Pero la importación quitó la petición de «dos», cuyas copias no se borraron.
- **Por qué es Minor:**
  - La ventana son microsegundos sin suspensión: los destinos que se saltan no hacen E/S.
  - La importación llega tras un Argon2id de segundos, sin relación con ese instante.
  - La pantalla de importar ya dice «Las copias anteriores que ibas a borrar no se borraron: siguen en su carpeta.».
  - Es teórico, según la regla de proporción de la skill.
- **Arreglo:**
  - Apuntar `store.generation` al empezar el bucle, dentro del cerrojo, y exigir en `tell` que siga igual **después** de leer `targets()`.
  - Basta porque `importReplace` sube la generación (`VaultStore.kt:1442`) antes de `beforeReplace()` (`:1444`). Si `tell` ve las peticiones quitadas, ve también la generación nueva.
  - Cubre de paso un `NO_RETRY` ya avisado que pudiera ir con un `replaced`.

**N-5 (Minor, documental): el KDoc de `replaceOutcome` ya no dice la regla.**
- **Dónde:** `AutoBackup.kt:340-342`, que sigue diciendo ««replaced» only when every target that tried replaced its old copies».
- **Corrección:** «only when no target has the request any more», como el KDoc de `tell` (`:499-505`).

No veo más problemas en el diff:
- `tell` no publica nada con `notices` vacías (`:507`).
- Un fallo ya avisado no se repite.
- El cambio de `VaultStore` solo afecta a `newTag = true`.

### Out-of-Scope Observations

- **O-5 (Minor, misma clase que I-3): una carpeta quitada y vuelta a añadir a mitad de A6.**
  - **El camino:**
    - `withTargetRemoved` quita la petición. `SettingsScreen.kt:345` lo avisa: «…Las copias anteriores que ibas a borrar no se borraron: siguen en su carpeta.».
    - `withTargetAdded` devuelve las listas de esa carpeta (`copies` e `importCopies` de `pastTargets`, `Settings.kt:176-184`, I-1), pero no la petición.
    - Cuando la otra carpeta reemplaza, `none { replacePending }` es verdadero.
    - `AppNavHost.kt:220` redacta con `targets.size` = 2: «…se borraron… las que la app tenía apuntadas en cada carpeta». Eso es falso para la carpeta vuelta a añadir: sus copias apuntadas de antes del cambio siguen ahí.
  - **Por qué es Minor:** hace falta que el usuario quite y vuelva a añadir la misma carpeta durante la petición, después de un aviso explícito.
  - **Arreglo:** puede ir en la misma ronda que el riesgo con nombre. Con la opción (A), «cada carpeta» solo si todos los destinos actuales cerraron la petición (`replaceUnlisted != null`); si no, un texto que no diga «cada carpeta». Otra salida: que `withTargetAdded` no devuelva `copies` que eran objetivos de una petición quitada. Esta es peor, porque esas copias no rotarían.
- **O-6 (Informativo):** la sobrecuenta en una carpeta compartida por dos destinos, descrita arriba. Es la dirección segura.
- **O-1 a O-3 de la re-revisión 1:** sin cambios.

### Verdict

**Hay que arreglarla antes de seguir.**
- I-3 (cláusula 3), N-1 y N-3 están resueltos. Los tests de I-3 cubren los dos caminos pedidos.
- El riesgo con nombre está **confirmado como Important**. Con varias carpetas, el éxito de A6 cuenta solo las copias sin apuntar de la pasada que cierra la petición. La advertencia «En las carpetas quedan N copias… puede que se abran con una contraseña anterior» puede dar menos o desaparecer (`AutoBackup.kt:512`, `:525`; `AppNavHost.kt:220`; `replacedText`, `:189-193`).
- N-2 queda a medias: falta la variante «En las carpetas…» en el anexo (Minor).
- Hay dos menores nuevos, N-4 (la carrera de `tell` con una importación) y N-5 (el KDoc), y un menor fuera de alcance, O-5, de la misma clase. Los dos menores nuevos pueden ir en la misma ronda.

## Revisión experto-seguridad-cripto

**Modelo de amenaza:** el de las revisiones anteriores.
- El activo crítico de A6 son las copias que se abren con una maestra anterior, quizá comprometida.
- El usuario decide si borra a mano según lo que le dice el diálogo.

| Punto | Evidencia | Valoración |
|---|---|---|
| Éxito de A6 sin peticiones pendientes | `AutoBackup.kt:509`, `:512-514` | Correcto: el texto «en cada carpeta» ya no sale con una carpeta pendiente. |
| Copias sin apuntar en el éxito (COPIAS-2) | `:525`, `:512`, `:189-193`, `AppNavHost.kt:220`, `rotate` `:925-928` | La cuenta de las pasadas anteriores se pierde, y la advertencia puede desaparecer. **Important** (riesgo con nombre). |
| Carrera con una importación | `:444`, `:485`, `:509`; `VaultStore.kt:1442-1444` | Ventana de microsegundos, sin suspensión. Se cierra comprobando la generación. Minor (N-4). |
| `vaultTag` y fecha en «Importar como bóveda nueva» | `VaultStore.kt:1416` | Tag de 128 bits de `SecureRandom` y fecha a 0, dentro de los datos autenticados con la maestra. Correcto. |
| Borrado | `rotate`, sin cambios | Sigue borrando solo por id de la lista propia. |
| Registros, red, permisos y dependencias | diff | Nada nuevo. Los textos siguen siendo fijos, sin rutas ni URI. |

## Revisión Ingeniero Móvil Senior

| Área | Evidencia | Valoración |
|---|---|---|
| Concurrencia | `AutoBackup.kt:430-444`, `:465-485`, `:506-516` | `tell` lee `targets()` (un `StateFlow`) fuera del `Mutex`. Es aceptable para leer, pero abre N-4. El arreglo con la generación no añade cerrojos. |
| Persistencia entre pasadas | `BackupSchedule.kt:30-41`, `Settings.kt:262-263`, `:280` | Lo que el éxito final necesita de pasadas anteriores (la cuenta) no está en los ajustes. La opción (A) del arreglo lo pone en un campo opcional que deserializa los ajustes antiguos. La (B) lista las carpetas por SAF dentro del cerrojo, con E/S solo en la pasada que cierra. |
| Ciclo de vida | `AppNavHost.kt:218-220` | El diálogo sigue en `Notice` (sobrevive a la recreación de la actividad). Redacta con `targets.size` en el momento de enseñarlo: ver O-5. |
| Tests | `AutoBackupTargetsTest.kt:744`, `:782`; `BackupCheckTest.kt:664` | Deterministas (`runTest`, `runCurrent`, `settle`), sin esperas reales. Les falta comprobar `unlistedLeft`. |
| Estado | — | Nada nuevo en `rememberSaveable` ni en un `Bundle`. |

## Textos para el Abogado Digital (revisión final)

- **`AUTO_BACKUP_REPLACED_SEVERAL`:** «Se hizo una copia nueva en cada carpeta y se borraron las copias automáticas y las de antes de importar hechas antes del cambio de contraseña: las que la app tenía apuntadas en cada carpeta. Las que se hayan copiado a otro sitio (otra carpeta, la nube o su papelera) no se borran.»
  - Ya solo sale cuando ninguna carpeta tiene la petición pendiente.
  - Su veracidad depende aún de N-4 y O-5.
- **`autoBackupUnlistedText(n, severalFolders = true)`:** «En las carpetas queda 1 copia…» / «En las carpetas quedan N copias con nombre de copia de la app (automática o de antes de importar) que la app no tenía apuntadas (por ejemplo, de antes de la versión 1.2.1 o de otra bóveda): no se han borrado y puede que se abran con una contraseña anterior. Si no las necesitas, bórralas a mano.»
  - **Falta en el anexo** (N-2).
  - **No es fiable** hasta que se arregle el riesgo con nombre: la cifra puede quedarse corta o la frase no salir. Si el arreglo cambia el texto (por ejemplo, una advertencia sin número), el texto nuevo va al Abogado.
- **Sin cambios desde la re-revisión 1:**
  - los de D-8, O-5 y M-6 (`BACKUP_CHECK_*`);
  - la nota de I-2 (los fallos de varias carpetas, uno por línea, sin decir la carpeta);
  - `AUTO_BACKUP_OLD_NOT_DELETED_NO_RETRY`.
- `BACKUP_CHECK_NOTICE_MORE` («…una copia de esta bóveda…») ya es verdadero también para una bóveda importada como nueva (N-1).
