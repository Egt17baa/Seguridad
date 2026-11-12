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
