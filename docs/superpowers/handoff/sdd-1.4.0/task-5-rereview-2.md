# Tarea 5: Comprobar una copia, re-revisión de la ronda de arreglos 2 (22cee96..5958932)

### Finding Verdicts

- **Minor 1. La frase sobre `touch()` dice más de lo que hace** — ADDRESSED.
  - `BACKUP_CHECK_INTRO` (`BackupCheckTexts.kt`) dice ahora «No cambia tu bóveda ni tus copias.» Es verdad: la comprobación solo lee la copia y escribe en su carpeta temporal (`BackupCheck.kt`); `touch()` no cambia la bóveda ni las copias. Sin palabras de `ForbiddenWords` (anónim, imposible, 100 %, de verdad, validez legal, sospechos, limpio/a, seguro/a, garantiza).
  - KDoc de `BackupCheckViewModel` (`BackupCheckScreen.kt`): «restarts its idle count in memory and may raise the A11 clock mark (`Settings.clockHighWaterMs`, saved to disk only when it rises far enough, never lowered), as every use of the app does». Coincide con `VaultStore.touch` (`lastUse` más `onClockSeen(now())`) y con `AppContainer.rememberClock`/`saveClock` (solo sube, escritura por pasos de `CLOCK_WRITE_STEP_MS`). «Any setting the user chose» es exacto: la marca no la elige el usuario.
  - El comentario de `SettingsScreen.kt` («changes neither the vault nor a copy») también es verdad.
  - El texto quitó «ajustes», que era lo que sobrepasaba el código.
- **Minor 2. La aserción del selector depende del espaciado exacto** — ADDRESSED.
  - `PlatformSourcesTest.kt`: `flat = src.replace(Regex("\\s+"), " ")` y se busca la secuencia de los cuatro pasos (`keepOpenOnce = true`, `store.touch()`, `pickerError = pick.launchOrError(...)`, `if (pickerError != null) ... = false`) en ese orden en `flat`. Un reformateo ya no la rompe.
  - Sigue fallando si se quita `keepOpenOnce = true`, `store.touch()` o la línea de vuelta a `false`: en cualquiera de los tres casos la cadena contigua deja de existir en `flat`. Además, las aserciones de `count == 1` de `store.touch()` y de `keepOpenOnce = true` fallan por separado. El informe lo confirma con mutaciones reales (quitar uno de los pasos: FAILED; partir una línea: PASSED). No se re-ejecutó.
  - La lista de escritura (`"settings"`, `"touch()"`) no se rompe por el KDoc nuevo: el KDoc dice `Settings.` y «setting the user», no `settings` en minúscula; el informe da 72 tests en verde.

### New Breakage in the Fix Diff

None.

### Out-of-Scope Observations

None.

### Verdict

**Fix round:** All findings addressed, no new Critical/Important breakage. No test run; verificado con el diff y el código de apoyo.

## Revisión experto-seguridad-cripto

- Sin cambios de código con efecto en seguridad (solo texto, comentarios y un test). A11 no se debilita: `touch()` solo sube la marca (`AppContainer.kt:206-209`, `VaultStore.kt:555-558`).
- El texto de la interfaz ya no afirma nada que el código no cumpla (`BackupCheckTexts.kt`, `BACKUP_CHECK_INTRO`).

## Revisión Ingeniero Móvil Senior

- El test de fuentes tolera el formato y detecta la falta de cualquiera de los cuatro pasos del selector (`PlatformSourcesTest.kt`, bloque `flat`), igual que el patrón de `VaultBackup.kt`.
- Sin cambios de comportamiento en `BackupCheckScreen.kt` ni en `SettingsScreen.kt` (solo KDoc y comentario).

## Textos para el Abogado Digital (revisión final)

- `BACKUP_CHECK_INTRO` (`BackupCheckTexts.kt`): «Comprueba una copia .sgbak sin importarla: ... No cambia tu bóveda ni tus copias. Mientras la comprueba, guarda lo que lee en una carpeta temporal de la app y lo borra al terminar.» Cierto; ya no menciona los ajustes.
