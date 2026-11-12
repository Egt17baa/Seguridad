# SDD ledger — plan: docs/superpowers/plans/2026-11-12-clavero-2.0.md

Diseño: `docs/superpowers/specs/2026-11-12-clavero-2.0-design.md` (aprobado por el dueño el 12-11-2026). Plan aprobado el 12-11-2026. Rama `ccr-0aa27f95-u038cd` (la designada; sin *worktree* aparte: el contenedor es propio de la sesión). Base de la tarea 1: `6c25222`.

Owner decision (12-11-2026): el Abogado Digital da su visto bueno al principio (sobre el diseño, antes de la tarea 1) y al final; si no lo da, se adapta lo que diga. La tarea 1 espera a la revisión inicial.
Ruling: el registro vive aquí, en `docs/superpowers/handoff/sdd-2.0/` (convención del proyecto, como `sdd-1.4.0/`); las *briefs* y los paquetes de revisión, en `.superpowers/sdd/` (ignorado por git) — los informes de cada tarea se copian aquí al cerrarla — si se pierde `.superpowers/`, se rehace desde el plan y `git log`.

## Revisión previa (pre-flight)

| Tareas | Comparten | Hallazgo y decisión |
|---|---|---|
| 1 → 2 | `VaultLimitExceeded`, `VaultLimits.VAULTS_MAX`, `TagColor` | Coherente. |
| 1 → 3 | `VaultData.lastUnlockAt`, `deletedAt`, `history` | Coherente. |
| 1 → 4, 5, 8 | `Folder`, `SavedView`, `EntryFilter`, `CustomField`, `vaultTag`, `lastCheckedBackupAt` | Coherente. |
| 1 ↔ 2 | `AutoBackup.copyBeforeUpgrade` (1) y `AutoBackup` por bóveda (2) | Ruling: la tarea 1 lo añade a la instancia única de hoy; la 2 la multiplica por bóveda sin cambiar la firma. |
| 2 ↔ 8 | `VaultSettings` (2) y `BackupTargetSettings` (8) | Ruling: la 2 mueve los campos de hoy tal cual a `VaultSettings`; la 8 los convierte en `targets: List<BackupTargetSettings>` con su migración (un destino `ON_CHANGE`). |
| 3 ↔ 5 | `health/Health.kt` | La 3 quita la papelera; la 5 añade los secretos de `WIFI`. Sin choque. |
| 3 → 6 | `UndoTrash`, `moveToTrash` | Coherente. |
| 4 → 6, 10 | `markUsed`, `FuzzySearch` | Coherente. |
| 5 → 9 | `Templates` (campos del wifi) | Coherente. |
| 1 | Sí misma | Ruling: «una v3 de la 1.4.3» del test `v3FixtureOpensAsVaultData` no existe como archivo de prueba; se genera con el código de `main` (`a3ac942`) **antes** de cambiar `Vault.kt` y se guarda en `app/src/test/resources/seguridad/vault-1.4.3.json` (con su maestra en el test). |
| 1 | Sí misma | Hoy `VaultFile.VERSION` significa 3 en varios sitios (contactos, `write`); al subirlo a 4, cada uso se revisa (`VERSION_3` donde quiere decir 3). |
| 3 | Sí misma | Ruling: la regla del reloj de la papelera se lee así: no se vacía nada en un desbloqueo cuyo `now` supera en más de 2 días la marca del reloj (A11) que había antes de ese desbloqueo; si no, se quita lo de más de 30 días y se apunta `lastUnlockAt` — el diseño pide que el reloj adelantado no vacíe la papelera; esto lo cumple con la marca que ya existe — si es demasiado estricto, la papelera se vacía en el siguiente desbloqueo normal. |
| 5 | Sí misma | «Los secretos cuentan como contraseñas en Salud solo en `LOGIN` y `WIFI`» es del plan, no del diseño: se acepta (una clave de licencia o un CVV no son contraseñas reutilizables). |
| 10 | Sí misma | Ruling: `noPressureWords` busca palabras enteras («ya» como palabra, no dentro de «ayuda») y el signo «!». |
| 11 | Sí misma | Ruling: si `python3 -c "import yaml"` no está, la sintaxis se comprueba con el analizador de YAML que haya en el SDK o en Gradle; se apunta cuál. |
| 12 | Sí misma | `auditoria-seguridad` no está en la sesión (decisión del dueño, 11-11-2026): la auditoría usa `experto-seguridad-cripto` y `auditoria-seguridad-movil`. |

## Tareas
Ruling: este contenedor no trae `/opt/android-sdk`; el SDK (plataforma 36, build-tools 36.0.0, platform-tools) se instaló en `/home/user/android-sdk` con `sdkmanager` desde dl.google.com — todos los comandos usan `export ANDROID_HOME=/home/user/android-sdk` — fuera del repositorio, se pierde con el contenedor y se vuelve a instalar igual.
Línea base: `jvm-check` 1915 tests en verde en `25aa637`.
Línea base: `:app:testDebugUnitTest :app:assembleDebug` en verde en `25aa637` (1920 tests).
Abogado inicial (12-11-2026): «visto bueno con cambios» (`abogado-2.0-inicial.md`); O-1 a O-11 y R-1 a R-12 aplicados al diseño (sección «Revisión inicial del Abogado Digital») y al plan (restricciones globales). Sin choque técnico ni de seguridad. Corregido el tope del historial a 64 KiB en todo el diseño (O-2).
Task 1: dispatched (base 7744994, model opus).
Owner decision (12-11-2026, opción A): el nombre de cada bóveda va cifrado dentro de ella (`VaultData.name`, tarea 2); antes de abrir se ve «Bóveda N» y su color; el registro no guarda nombres. O-1 del Abogado deja de aplicar en su parte del aviso; la política dirá que el móvil guarda sin cifrar cuántas bóvedas hay y su color (revisión final).
Task 1: implementer DONE_WITH_CONCERNS (9db2dda, 7d13e5a, 0eec41d; app 1943 y jvm-check 1938 en verde).
Ruling (T1, duda 1): una bóveda en el tope de 24 MiB de datos no cabe en una copia de 32 MiB (Base64 y envoltura) — se sube el tope por archivo de la copia con marca 3 a 33 MiB (`BACKUP_V3_MAX_ENTRY_BYTES = 33L * 1024 * 1024`, texto «Un archivo de la copia pasa de 33 MB») y se fija con un test que una bóveda en el tope exacto cabe en una copia — el diseño promete 24 MiB de bóveda que siempre quepa en una copia (R12, R14); si es caro, solo cambia un número.
Ruling (T1, duda 2): el cálculo de si cabe de «Importar de otra app» usa el tope de la copia que de verdad se escribiría (marca 3: 33 MiB; marca 2: 6 MB) — con 6 MB rechazaría importaciones que caben — se reescalan sus tests.
Ruling (T1, duda 3): un elemento importado que pasa de 64 KiB sale en el plan como «no cabe» con su texto propio (como los demás límites de R12), nunca como fallo genérico al guardar — decisión (a) y R12.
Ruling (T1, dudas 4 a 6): se aceptan — `passwordHistory` se queda hasta la tarea 3; topes solo a lo que cambia, para que una bóveda antigua siempre abra; la copia `seguridad-antes-de-2-0-` entra en la lista que borra A6, como las de antes de importar (R15), y va a «Textos para el Abogado» (la política debe decirlo); `ListQuery` y `CryptoFailure(UPGRADE_PENDING)` bien.
Task 1 review (9804b28): spec ❌, quality Needs fixes; 0 Critical, 1 Important (I-1: el diálogo del paso sale durante la copia automática), 8 Minor. Revisión en `.superpowers/sdd/…/task-1-review.md` (se copia aquí al cerrar).
Ruling (T1, ⚠️ O-3 con carpeta): con la copia automática bien hecha, el paso era callado y O-3 pide el aviso a la vista — tras el paso con carpeta se enseña una vez un aviso informativo (las versiones anteriores ya no abren la bóveda ni sus copias nuevas; la copia de antes está en la carpeta, con su nombre) — entra en el bucle de arreglos.
Ruling (T1, menores): `CLAUDE.md` («Detalles menores: se arreglan todos») manda sobre la regla de la skill de aplazarlos — M-1, M-2, M-3, M-5 y M-7 entran en la ronda 1; M-4 va a la tarea 3 (no quitar `passwordHistory` sin conciliar las dos listas y probar el paso con una entrada grande); M-6 a la prueba de rendimiento (tarea 4 y revisión final: medir un `upsert` en el tope de 24 MiB); M-8 a la tarea 2 (sacar el paso pendiente de `VaultStore.kt` a su propia pieza al partirlo por bóveda).
Task 1: fix round 1/5 dispatched and done (fe9e944): I-1, O-3 con carpeta, M-1, M-2, M-3, M-5, M-7; app 1956 y jvm-check 1949 en verde. Re-review en marcha.
Ruling (T1, ronda 1): el aviso de después del paso vive solo en memoria y se pierde si Android cierra el proceso antes de enseñarlo — se acepta: el paso ya está hecho y la copia de antes en su carpeta; O-3 también lo dicen las «Novedades», las notas y el README (tarea 12) — si molesta, se guarda una marca en los ajustes.
Task 1: fix round 1/5 (8 addressed, 0 open; commits 9804b28..fe9e944).
Task 1: complete (commits 7744994..fe9e944, review clean). Informes: `task-1-report.md`, `task-1-review.md`, `task-1-rereview-1.md`.
Task 1: minor (deferred): M-4 → tarea 3; M-6 → rendimiento (tarea 4 y final); M-8 → tarea 2.
Task 2: dispatched (base 643512a, model opus).
Task 2: implementer DONE_WITH_CONCERNS (4056f34; app 1976 y jvm-check 1969 en verde). Se paró una vez por el límite de uso y se reanudó.
Ruling (T2, duda 1): crear o importar otra bóveda pide una bóveda abierta (`canAddVault`, cierra COV2-1); el hueco que deja «He olvidado la contraseña maestra» se puede convertir en una bóveda nueva desde su pantalla — se acepta: es lo mismo que hoy hace la 1.4.3 con su única bóveda (quien olvida la maestra crea otra), y no abre ninguna bóveda existente — si el dueño lo quiere cerrar, es una decisión de producto aparte.
Ruling (T2, duda 2): se aceptan — los avisos de StrongBox son del teléfono, no de la bóveda; «Importar como bóveda nueva» conserva el nombre de la copia (va cifrado); una bóveda nueva empieza con el coste STANDARD de Argon2id; una bóveda sin archivo se borra sin maestra ni nombre (no hay nada que proteger).
Task 2: minor (deferred): `VaultStore.kt` ~1 310 líneas; la próxima pieza para sacar es la importación — a la revisión final.
Task 2 review (4056f34): spec ✅ (4 ⚠️), quality Needs fixes; 0 Critical, 2 Important, 7 Minor (`task-2-review.md`).
Ruling (T2, I-1, seguridad): la marca del reloj de A11 pasa a ser **por bóveda**: toda hora vista sube la marca de todas las bóvedas (así sigue siendo la más alta vista); un desbloqueo con la maestra de una bóveda solo baja la suya; las demás conservan la suya — con una marca única, la maestra de otra bóveda (de un familiar o de un hueco creado por el atacante) curaba el reloj de la huella de A — los ajustes de la 1.4.3 dan esa marca a `principal` y a toda bóveda nueva la marca de ese momento. Con esto, el ruling sobre `canAddVault` se sostiene: un hueco nuevo no abre ni ayuda a abrir ninguna bóveda existente.
Ruling (T2, I-2): «Importar como bóveda nueva» comprueba la contraseña de la copia antes de cambiar de bóveda; los resultados de la pantalla «Bóvedas» (borrada, importada, errores) se guardan en `AppContainer` y se enseñan después del cambio.
Ruling (T2, menores): todos a la ronda 1 (`CLAUDE.md`): M-1 borrar un hueco vacío desde la interfaz; M-2 `deleteVault` solo se salta la maestra y el nombre si la carpeta no tiene ningún archivo de bóveda (ni `vault.bin` ni un `.bad-` apartado ni un fallo de lectura); M-3 `newVault`/`deleteVault` y el *getter* `autoBackup` usan la bóveda de su `id`; M-4 el test del foco 2 controla el entrelazado con puntos de sincronización (sin `Thread.sleep`) y falla si se quita la protección; M-5 un resultado tardío de la copia de una bóveda borrada se descarta; M-6 `VaultScoped` no vacía la tienda de `ViewModel` durante la composición (en un efecto). M-7 sigue aplazado.
Task 2: fix round 1 done (f6b98b7; app 1980, jvm-check 1973). Ruling: la doble derivación de «Importar como bóveda nueva» (comprobar antes de cambiar) se acepta — es el precio de no dejar al usuario ante una bóveda cerrada — un Argon2id más, solo en esa acción.
Task 2: fix round 1/5 (9 addressed, 0 open; 4056f34..f6b98b7). Fix round 2/5 (2 menores de la re-review addressed, 0 open; f6b98b7..6c05936; la re-review 2 no pudo escribir su archivo y dio su informe en el mensaje: «All findings addressed, no new Critical/Important breakage»).
Task 2: complete (commits 643512a..6c05936, review clean). Informes: `task-2-report.md`, `task-2-review.md`, `task-2-rereview-1.md`. App 1982, jvm-check 1975.
