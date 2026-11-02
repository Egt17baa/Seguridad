# Re-revisión de la tarea 3, ronda 1 (`e435a96..0389a1f`)

Alcance: el diff de la ronda (2 archivos: `vault/VaultStore.kt` y `VaultStoreTest.kt`), el apartado «Ronda 1» del informe y comprobaciones puntuales en el código actual. Sin comandos git, sin volver a ejecutar la suite. Evidencia de pruebas: el XML que dejó la ronda, `app/build/test-results/testDebugUnitTest/TEST-app.seguridad.vault.VaultStoreTest.xml` (`tests="70" failures="0" errors="0" skipped="0"`, 16:28 UTC del 1-11-2026), que contiene los tests renombrados y el nuevo. El árbol de trabajo está limpio (sin diferencias), así que la mutación del informe se restauró.

### Finding Verdicts

- **M1 (orden con `VERSION_UNKNOWN`): ADDRESSED.**
  - Informe corregido: preocupación 2 (`task-3-report.md:85`) distingue v2/v3 (`MasterRequired` primero) de `VERSION_UNKNOWN` (la clave se prueba primero y da `CryptoFailure`), con las referencias `Vault.kt:318` y `Vault.kt:317-322`, que corresponden a `resolve` (`Vault.kt:317-321`). La autorrevisión de la ronda (`:159`) lo repite.
  - Test nuevo `VaultStoreTest.kt:1030` «with the master password due, a vault of unknown version tries the key first, so a stale key still turns the fingerprint off»: archivo `VERSION_UNKNOWN` (ayudante `unknownVersionVault()`, `:1012`), reloj a `MASTER_RECHECK_MS`, clave ajena: `off == 1`, `[FINGERPRINT_STALE]`, clave a cero; después la clave buena de la misma bóveda: `off == 0`, `[MasterRequired().message]`, clave a cero. Fija el orden en las dos direcciones, y se rompería si `resolve` dejara de probar la clave antes del reloj. Cubierto por el XML (el test aparece, 0 fallos).
  - El test de v3 se renombró (`:1094`) y su comentario (`:1095-1097`) dice que vale para v2 y v3 y que `VERSION_UNKNOWN` va al revés.
- **M2 (motivo defensivo, sin afirmar nada del Keystore): ADDRESSED en los sitios citados; queda un resto menor fuera del diff (ver abajo).**
  - Comentario `VaultStore.kt:109`: «Defensive: an unexpected exception from onWrongKey must not crash the screen's scope nor hide the text below.» Sin Keystore.
  - KDoc `VaultStore.kt:85-87`: «`onWrongKey` is not expected to throw, but if it does the text is shown all the same…».
  - Test `VaultStoreTest.kt:1124`: renombrado «if onWrongKey throws for whatever reason…», lanza `IllegalStateException("unexpected")` (ya no `KeyStoreException`), comentario «Defensive…» (`:1128-1129`).
  - Resto: `ui/screens/VaultScreen.kt:308` (fuera del diff, de la versión original) sigue diciendo «…even if the Keystore refused to turn it off». Es la misma afirmación que M2 pide quitar, y el informe de la ronda dice «Ya no se nombra el Keystore» (`task-3-report.md:124`), lo cual no es del todo cierto. No es un defecto de comportamiento. Arreglo de una línea: «…even if turning it off ever threw».
- **M3 (test de cableado): ADDRESSED.** `VaultStoreTest.kt:1147-1157`. La regex `launchUnlockWithKey\(store, key, onWrongKey = \{\s*fingerprintStale = true\s*BiometricVault\.disable\(context\)\s*\}\)` solo admite el orden `fingerprintStale = true` y después `disable`; coincide con el código real (`VaultScreen.kt:318-321`, tras filtrar comentarios), y `VaultScreen.kt:324` y `:350` para `bio`. Conserva `val bio = !fingerprintStale && …` y añade `if (bio) OutlinedButton(onClick = ::withFingerprint` (`VaultScreen.kt:350`) y `LaunchedEffect(Unit) { if (bio) withFingerprint() }` (`:324`), ambos presentes. El informe aporta la mutación (intercambiar las dos líneas): `70 tests completed, 1 failed`, solo ese test; el árbol está limpio, así que se restauró.
- **M4 (nombres): ADDRESSED.**
  - `VaultStoreTest.kt:972`: «BOV-3 a refused fingerprint key reports FINGERPRINT_STALE and wipes the key» (aparece en el XML).
  - `:1018`: «another vault's key on a vault of unknown version (VERSION_UNKNOWN) is just as stale».
  - No quedan en el repositorio fuera de `build/`, `.git/` y `.superpowers/` las cadenas «version-1 vault, whose version» ni «reports the store's message». El informe conserva los nombres viejos solo en los registros históricos (líneas 36 y 48), que describen el estado de entonces; la ronda indica los renombres (`:127-128`).
- **M5 (giro, límite aceptado, una frase de KDoc + informe): ADDRESSED.** `VaultStore.kt:90-91`: «Limit, as for any state of the screen (BOV-3): if the screen is rotated while the answer is on its way, onWrongKey still turns the fingerprint off, but the new screen does not repeat FINGERPRINT_STALE (the old composition got it).» Una frase. Es exacta: con el scope cancelado y el mutex libre, la rama llega a `CryptoFailure`, `disable` borra preferencias (la pantalla nueva ve `enabled() == false`, sin botón) y el texto va al estado de la composición vieja. Informe: punto 5 de la ronda (`:129`), con «a mirar en la prueba en el móvil». No se construyó nada, como pidió el controlador.
- **M6 (`disable` que lanza de verdad, límite aceptado, solo informe): ADDRESSED.** Punto 6 de la ronda (`task-3-report.md:130`) y preocupación 3 reescrita (`:86`): `fingerprintStale` no sobrevive a la pantalla siguiente, el aviso automático podría volver a salir en cada bloqueo, caso casi inalcanzable. Sin código nuevo.
- **M7:** preexistente, fuera de alcance, no se toca. Correcto.

### New Breakage in the Fix Diff

Ninguna Critical ni Important. La lógica no cambia: el diff de `VaultStore.kt` solo toca KDoc y un comentario (las líneas ejecutables del `launch` son idénticas).

Minor (cosmético, sin efecto):
- `VaultStoreTest.kt:1031`: el comentario del test nuevo dice «(see the next test)», pero el test de versión 3 está en `:1094`, a seis tests de distancia. Apuntar al nombre («the version-3 test below») o quitar la referencia.

### Out-of-Scope Observations

- `ui/screens/VaultScreen.kt:308` conserva la frase «the Keystore refused to turn it off» (ver M2). Es la única mención que queda de esa causa.
- El informe conserva las cifras y los nombres de antes de la ronda en las secciones «Tests y cifras» y «Evidencia TDD» (69 tests, nombres viejos), y la lista de los 11 tests no nombra el «7b»; la sección «Ronda 1» lo aclara (69 a 70; app 1256; jvm-check 1251). No hace falta tocarlo.
- Cifras globales de la ronda (jvm-check 1251, app 1256, lint y compuertas): afirmaciones del informe, no verificadas aquí. Lo único comprobado es el XML de `VaultStoreTest` (70/70).

### Verdict

**Fix round:** All findings addressed, no new Critical/Important breakage.

Dos restos Minor, a criterio del controlador, sin bloquear: el comentario de `VaultScreen.kt:308` (Keystore) y el puntero «next test» en `VaultStoreTest.kt:1031`. Ambos son cambios de una línea de comentario.
