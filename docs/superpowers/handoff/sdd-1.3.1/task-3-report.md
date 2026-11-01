# Informe de la tarea 3: CRY-A02, la huella que guarda la clave de otra bóveda se desactiva y lo dice

**Estado:** DONE (con las observaciones de «Preocupaciones»).
**Commit:** `e435a96` «vault: la huella con la clave de otra bóveda se desactiva y lo dice (CRY-A02)», en `ccr-a1a6eabb-765f9g`, ya subido (`1430a1d..e435a96`). Autor `Claude <noreply@anthropic.com>`; el mensaje acaba solo en `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` (`grep -cE "Claude-Session|claude\.ai"` da 0).

## Qué se ha implementado

Sin cambiar ningún formato ni `BiometricVault` (no se toca el envuelto de la clave).

- `vault/VaultStore.kt:65`: `const val FINGERPRINT_STALE`, junto a `BIOMETRIC_UNLOCK_FAILED`, con el texto de `auditoria/04-cripto-parteA.md` («Textos para el Abogado Digital», punto 3), palabra por palabra.
- `vault/VaultStore.kt:90-110`: `fun CoroutineScope.launchUnlockWithKey(store, key, onWrongKey: () -> Unit = {}, onError: (String) -> Unit): Job` (la firma del encargo). Se mantiene `launch(start = ATOMIC)`. Una rama `catch (e: CryptoFailure)` antes de la genérica llama a `onWrongKey()` una vez y después a `onError(FINGERPRINT_STALE)`. Si `onWrongKey` lanza una excepción inesperada, se ignora y el texto se enseña igual (defensivo; ver «Ronda 1»). `MasterRequired` y cualquier otra excepción siguen por la rama genérica con `biometricUnlockError` (el texto de hoy) y no llegan a `onWrongKey`. La cancelación se relanza como antes.
- `ui/screens/VaultScreen.kt:309-321` (`UnlockVault`): `onWrongKey = { fingerprintStale = true; BiometricVault.disable(context) }`. `bio` pasa a `!fingerprintStale && BiometricVault.enabled(context) && store.biometricAllowed`, así el botón «Usar huella» deja de ofrecerse después del fallo, también si `disable` falla. El aviso automático (`LaunchedEffect(Unit) { if (bio) ... }`) solo corre una vez al entrar, así que no se vuelve a lanzar.

**Desviación respecto al encargo (decisión mía, a revisar):** el `try` que envuelve `disable` no está en la lambda de `UnlockVault` sino dentro de `launchUnlockWithKey`. El efecto pedido es el mismo («si `disable` falla, se enseña el mismo `FINGERPRINT_STALE`»), pero así queda en un solo sitio y se prueba en JVM (`ui/` no se compila en `jvm-check`). Sin ese `try`, una excepción de `onWrongKey` saldría de la rama `catch` hacia el `scope` de la pantalla y la app caería. `UnlockVault` queda con una lambda sencilla.

## Tests y cifras

- `VaultStoreTest`: 58 a 69 (11 nuevos) y uno existente cambiado (ver abajo). Reutilizan los ayudantes que ya había (`store()`, `clockStore`, `clock`, `MASTER_RECHECK_MS`, `mutexOf`).
- Verificaciones globales, una vez antes del commit, con `--no-daemon --max-workers=1 --offline`:
  - `gradle -p jvm-check test`: BUILD SUCCESSFUL, **1250 pasan, 0 fallos** (antes 1239; +11).
  - `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets`: BUILD SUCCESSFUL, **1255 tests, 0 fallos, 0 errores, 0 omitidos** (antes 1244; +11).

Los 11 tests nuevos:
1. El texto es el del informe y no usa ninguna palabra prohibida (`ForbiddenWords.problems`).
2. Con la DEK de otra bóveda: `onWrongKey` una vez y antes que el texto (`["off", "error: FINGERPRINT_STALE"]`), la bóveda sigue bloqueada, `dataKeyCopy()` es `null` y la clave queda a cero.
3. Lo mismo con una bóveda de versión desconocida (`VERSION_UNKNOWN`), donde es `resolve()` el que lanza `CryptoFailure`.
4. Con la DEK buena: abre y no llama a `onWrongKey` ni a `onError`.
5. Regla de los 7 días: no llama a `onWrongKey`, da `MasterRequired().message`, la clave queda a cero.
6. Reloj atrasado (A11, con `clockStore`): no llama a `onWrongKey`, da `CLOCK_CHANGED_TEXT`.
7. Maestra pedida **y** clave de otra bóveda: gana `MasterRequired` (no llama a `onWrongKey`); deja fijado el orden actual.
8. Otro error (`IOException` desde el proveedor de la marca del reloj): no llama a `onWrongKey` y da `BIOMETRIC_UNLOCK_FAILED`, con la clave a cero.
9. Si `onWrongKey` lanza una excepción inesperada (en la ronda 1 pasó de `KeyStoreException` a `IllegalStateException`): se enseña `FINGERPRINT_STALE` igualmente, no se propaga, bóveda bloqueada, clave a cero.
10. Cancelación (scope cancelado y mutex ocupado): no llama a `onWrongKey`, no informa de nada, la clave queda a cero.
11. Cableado de la pantalla (test de código fuente, como los de `MasterPolicyTest`): `launchUnlockWithKey(store, key, onWrongKey = { ... BiometricVault.disable(context) ... })`, `val bio = !fingerprintStale && ...` y `fingerprintStale = true`.

**Test existente cambiado:** `BOV-3 a refused fingerprint key reports the store's message` (`VaultStoreTest.kt:972`) esperaba «Contraseña incorrecta o datos dañados» para una clave que no abre nada. Es justo el comportamiento que cambia CRY-A02, así que ahora espera `FINGERPRINT_STALE`; conserva el resto (la clave queda a cero, se informa del error). El test `LIBRE-4` de `biometricUnlockError(CryptoFailure())` no cambia: la función sigue siendo total.

## Evidencia TDD

RED: primero los 11 tests, y para que el rojo fuera de comportamiento y no solo de compilación, añadí antes un esqueleto sin lógica (la constante y el parámetro `onWrongKey` sin usar).

```
$ ./gradlew :app:testDebugUnitTest --tests '*VaultStoreTest*' --offline --no-daemon --max-workers=1
VaultStoreTest > CRY-A02 if turning the fingerprint off fails the text is shown all the same FAILED
    java.lang.AssertionError at VaultStoreTest.kt:1096
VaultStoreTest > CRY-A02 another vault's key turns the fingerprint off once, says so, and leaves the vault locked and the key wiped FAILED
    java.lang.AssertionError at VaultStoreTest.kt:999
VaultStoreTest > CRY-A02 the same wrong key from a version-1 vault, whose version is found by the key, is just as stale FAILED
    java.lang.AssertionError at VaultStoreTest.kt:1010
68 tests completed, 3 failed
BUILD FAILED
```

Los tres fallos son los de la clave de otra bóveda (`onWrongKey` no se llamaba y el texto era el de hoy). Los demás pasaban ya con el esqueleto: fijan lo que no debe cambiar (7 días, reloj, otro error, clave buena).

GREEN, tras la rama `catch (e: CryptoFailure)`: apareció un fallo más, el `BOV-3 ... reports the store's message` de arriba (cambio de comportamiento pedido), que se actualizó. Después:

```
$ ./gradlew :app:testDebugUnitTest --tests '*VaultStoreTest*' --offline --no-daemon --max-workers=1
BUILD SUCCESSFUL in 34s        (69 tests, 0 fallos; 11 de ellos «CRY-A02»)
```

Y las dos verificaciones globales, con las cifras de arriba.

## Archivos modificados

- `app/src/main/java/app/seguridad/vault/VaultStore.kt` (constante, `launchUnlockWithKey` y su KDoc).
- `app/src/main/java/app/seguridad/ui/screens/VaultScreen.kt` (`UnlockVault`).
- `app/src/test/java/app/seguridad/vault/VaultStoreTest.kt` (11 tests nuevos, 1 actualizado).

Nada de CriptoPanel, `BiometricVault.kt`, `VaultBackup.kt` ni los datos de prueba.

## Autorrevisión

- **Foco de revisión 4 (solo `CryptoFailure` desactiva):** `MasterRequired` (7 días, reloj) y las demás excepciones no llaman a `onWrongKey`; lo fijan los tests 5, 6, 7 y 8. La rama `CryptoFailure` va antes de la genérica y no atrapa nada más (`MasterRequired` y `CryptoFailure` son clases distintas).
- **Clave a cero en cada salida:** no he tocado `unlockWithKey` ni `unlockWithKeyLocked`; siguen poniéndola a cero (`VaultStore.kt:431-439`, `441-457`). El test existente `unlockWithKeyEarlyExitWipesKey` pasa, y los míos 2, 3, 5, 6, 7, 8, 9 y 10 comprueban `all { it == 0 }`.
- **Sin textos fuera de lo pedido:** un solo texto nuevo, constante, copiado del informe; `SourceTextsTest` (todas las cadenas de `app/src/main/java`) y `ForbiddenWords` en el test 1 pasan. No lo he añadido a `ALL_TOOL_TEXTS` porque `ToolTextsTest.everyAppTextIsInTheAnnex` exige que cada texto de esa lista esté en el anexo del dictamen de la 1.3.0; igual que `MASTER_TOO_WEAK`, que tampoco está.
- **Sin logs, sin permisos, sin red, sin dependencias, sin componentes nuevos.**
- **Hilo:** `onWrongKey` corre en el scope de la pantalla (principal). `BiometricVault.disable` es `SharedPreferences.edit{}` (apply, asíncrono) y `KeyStore.deleteEntry`, y la pantalla ya lo llama en el hilo principal en «Crear bóveda» y «Borrar bóveda»; no hay trabajo nuevo pesado.
- **Fuera de alcance, apuntado:** el `README.md` («Pruebas en el móvil», lista de la huella) y el seguimiento de `progress.md` no se tocan aquí. El informe de auditoría (`04-cripto-parteA.md:405`) dice que CRY-A02 entra en esa lista; lo debe hacer la tarea de documentación.

## Preocupaciones

1. **GCM no distingue «clave de otra bóveda» de «datos de la bóveda dañados».** Ambos son `CryptoFailure` en `VaultCrypto.read`. Con una bóveda dañada y la huella buena, la huella también se desactiva y el texto habla de las dos causas que sí son habituales (cambiar la maestra, importar). Coste: ninguno sobre los datos (la bóveda y la clave envuelta por la maestra no se tocan) y la huella se reactiva desde Ajustes; con la bóveda dañada la maestra tampoco abriría. Es lo que dice el diseño («`CryptoFailure` → desactivar»); lo dejo anotado por si el revisor quiere un texto más neutro.
2. **Orden de las comprobaciones (corregido en la ronda 1).** Depende de la versión del archivo. Para la versión 2 y 3 (el caso real), `VaultCrypto.resolve` devuelve el archivo sin probar la clave (`vault/Vault.kt:318`) y `biometricRefusal` (`VaultStore.kt:450`) rechaza con `MasterRequired` antes de que `opened()` pruebe la clave (`VaultStore.kt:342`): una huella vieja con la maestra pendiente enseña la petición de la maestra y no se descubre hasta el primer desbloqueo con huella después de escribirla. Para `VERSION_UNKNOWN` (archivos de la 1.0 y la 1.1), `resolve` prueba la clave primero (`Vault.kt:317-322`) y una clave vieja da `CryptoFailure` aunque la maestra esté pendiente: la huella se desactiva en ese mismo intento. Los dos resultados son coherentes con «`MasterRequired` no cambia» (nunca desactiva); los tests 7 (versión 3) y 7b (`VERSION_UNKNOWN`, ronda 1) fijan los dos órdenes.
3. **`fingerprintStale` está en `remember`, no en `rememberSaveable`.** Es un booleano sin secreto. Si `disable` lanzara de verdad (caso casi inalcanzable: `BiometricVault.disable` borra las preferencias con `edit{}`, que no lanza, y `deleteKey()` va en `runCatching`) y la pantalla se girara, el botón reaparecería y el siguiente intento volvería a dar `FINGERPRINT_STALE`; el aviso automático saldría en cada bloqueo. Sin pérdida de datos.
4. **Prueba en el móvil pendiente** (no hay Android aquí): activar la huella, cambiar la maestra con la app cerrada a mitad (o restaurar un `vault.bin` anterior) y comprobar que el botón desaparece y aparece el texto. Los tests cubren la lógica de `vault/` y el cableado en el código fuente, no el aviso de BiometricPrompt.

## Revisión experto-seguridad-cripto

- **Modelo de amenaza:** nadie nuevo. La huella guardada envuelve una DEK que, tras un cambio de maestra o una importación, ya no es la de la bóveda; eso no es un ataque sino un blob rancio (CRY-A02, informativo). El arreglo hace que el blob inservible se borre en lugar de seguir en el Keystore y en las preferencias, y que el usuario lo sepa. No hay clave nueva, ni AAD, ni versión: el formato queda igual y se descarta cambiarlo, como en el diseño.
- **Solo primitivas estándar, sin criptografía propia:** no se añade ninguna. La decisión de «clave que no abre» es la autenticación GCM de siempre (`CryptoFailure` desde `VaultCrypto.read`, `vault/VaultStore.kt:342-347`), no una comparación nuestra, así que no hay un canal lateral nuevo ni una comparación en tiempo no constante.
- **Solo `CryptoFailure` desactiva (foco 4):** `vault/VaultStore.kt:101-107` (única rama que llama a `onWrongKey`); `MasterRequired` sale por la genérica, `:108-109`. Tests: `VaultStoreTest.kt:1039-1100` (7 días, reloj, maestra pedida más clave ajena, otro error).
- **Secretos en memoria:** la clave entregada se pone a cero en cada salida, también en la rama nueva (`vault/VaultStore.kt:431-439`, `441-457`; no modificados). Tests con `all { it == 0 }` en 2, 3, 5-10. `FINGERPRINT_STALE` no contiene datos del usuario. Ningún secreto va a `rememberSaveable` ni a un `Bundle`: `fingerprintStale` es un booleano (`ui/screens/VaultScreen.kt:309`).
- **Sin información de más:** el texto no distingue «clave ajena» de «datos dañados» hacia fuera más de lo que ya hacía «Contraseña incorrecta o datos dañados»; no hay un oráculo nuevo (el atacante tendría que pasar antes el prompt biométrico del sistema).
- **Fallo cerrado:** si `disable` falla, no se abre nada y se enseña el mismo texto (`vault/VaultStore.kt:102-107`, test 9); la bóveda queda bloqueada.
- **Sin logs, sin red, sin permisos nuevos.**

## Revisión Ingeniero Móvil Senior

- **`seguridad-criptografia-movil`:** la huella sigue ligada a una operación criptográfica (`CryptoObject`), no es un booleano; no se cambia su envuelto ni la política del Keystore (`BiometricVault.kt` intacto). Desactivar es `BiometricVault.disable` (borra preferencias y la clave del Keystore), el mismo método que ya usan el cambio de maestra, la importación, «Crear bóveda» y «Borrar bóveda» (`ui/screens/VaultBackup.kt:131,175`, `ui/screens/VaultScreen.kt`).
- **`desarrollo-movil-senior`, concurrencia:** `launch(start = CoroutineStart.ATOMIC)` se conserva (BOV-3), así la clave se entrega aunque el scope de la pantalla esté cancelado por un giro; la cancelación se relanza antes que cualquier otra rama (`vault/VaultStore.kt:99-100`); `onWrongKey` en una pantalla ya destruida solo escribe en un estado de Compose desechado, sin efecto.
- **Compose:** el estado es local y de interfaz (`remember`, `ui/screens/VaultScreen.kt:309`); `bio` se vuelve a leer en cada recomposición (`:310`) y `error = it` la provoca; la marca `fingerprintStale` garantiza que el botón desaparezca también si `disable` falla. `LaunchedEffect(Unit)` no se relanza, así que no hay un bucle de avisos.
- **Compatibilidad de API:** la firma pedida `launchUnlockWithKey(store, key, onWrongKey = {}, onError)` mantiene el lambda final como `onError`, así que los usos de antes (`launchUnlockWithKey(s, key) { errors += it }`) compilan igual. El valor por defecto `{}` evita tocar a quien no lo necesite.
- **Pruebas:** la lógica está en `vault/` (JVM, sin Robolectric); la parte de `ui/` se fija con un test de código fuente, el mismo estilo que `MasterPolicyTest`. Falta la prueba en el móvil (preocupación 4).

## Textos para el Abogado Digital (revisión final)

- **`FINGERPRINT_STALE`** (`vault/VaultStore.kt:65`; informe `auditoria/04-cripto-parteA.md`, punto 3), que se enseña en la pantalla «Bóveda bloqueada» cuando la huella ha entregado una clave que no abre la bóveda, a la vez que se desactiva la huella:
  «La huella ya no abre esta bóveda (se cambió la contraseña maestra o se importó una copia). Entra con la contraseña maestra y vuelve a activarla si quieres.»
  - Comprobado en código: es verdad que se desactiva (`BiometricVault.disable`), y que «vuelve a activarla» se hace en Ajustes con la bóveda abierta. Las dos causas que nombra son las que dejan una huella de otra clave (`ui/screens/VaultBackup.kt:131,175`), pero también aparece si la bóveda está dañada (preocupación 1); el texto no afirma que sea la única causa.
  - No usa «seguro», «garantiza», «anónimo», «imposible», «100 %», «de verdad», «validez legal», «limpio», «sospechoso» ni «como Signal».
- **Sin cambios** en `BIOMETRIC_UNLOCK_FAILED`, `CLOCK_CHANGED_TEXT` ni el texto de `MasterRequired`.

---

# Ronda 1 (revisión de la tarea 3: «Minor» corregidos)

Revisión: `.superpowers/sdd/2026-11-01-clavero-1.3.1/task-3-review.md` (aprobada, solo Minor; regla del proyecto: se arreglan). Los puntos 5 y 6 son límites aceptados por el controlador: no se construye nada, se anotan. El 7 es anterior a la tarea.

## Cambios

1. **Orden de las comprobaciones (Minor 1).** La preocupación 2 de arriba se ha corregido (v2/v3: `MasterRequired` primero; `VERSION_UNKNOWN`: la clave se prueba primero y una clave vieja da `CryptoFailure`). Test nuevo en `VaultStoreTest`, «with the master password due, a vault of unknown version tries the key first, so a stale key still turns the fingerprint off»: archivo `VERSION_UNKNOWN`, `clock += MASTER_RECHECK_MS`, clave de otra bóveda, y se comprueba que `onWrongKey` se llama una vez y que sale `FINGERPRINT_STALE`; después, con la clave buena de la misma bóveda y el mismo reloj, que no se llama a `onWrongKey` y que sale el texto de `MasterRequired`; en las dos la clave queda a cero. El test del orden en versión 3 se ha renombrado y su comentario ya dice que es solo para versión 2 y 3. Se ha extraído el ayudante `unknownVersionVault()`.
2. **Comentario y test de `onWrongKey` que lanza (Minor 2).** `VaultStore.kt:105` ahora dice «Defensive: an unexpected exception from onWrongKey must not crash the screen's scope nor hide the text below.», y el KDoc de `launchUnlockWithKey` dice que no se espera que `onWrongKey` lance. Ya no se nombra el Keystore. El test se llama «if onWrongKey throws for whatever reason the text is shown all the same», lanza `IllegalStateException("unexpected")` y su comentario dice que es defensivo.
3. **Test de cableado (Minor 3).** Ahora fija que `fingerprintStale = true` va **antes** de `BiometricVault.disable(context)` dentro de `onWrongKey = { ... }` (la regex admite solo ese orden), que `bio` es `!fingerprintStale && BiometricVault.enabled(context) && store.biometricAllowed`, que el botón sigue regido por `if (bio) OutlinedButton(onClick = ::withFingerprint` y el aviso automático por `LaunchedEffect(Unit) { if (bio) withFingerprint() }`. Comprobado con una mutación: al intercambiar las dos líneas en `VaultScreen.kt`, solo ese test falla (`70 tests completed, 1 failed`); la pantalla se restauró y no queda diferencia en ese archivo.
4. **Nombres (Minor 4).**
   - `BOV-3 a refused fingerprint key reports the store's message` pasa a `BOV-3 a refused fingerprint key reports FINGERPRINT_STALE and wipes the key`.
   - `CRY-A02 the same wrong key from a version-1 vault...` pasa a `CRY-A02 another vault's key on a vault of unknown version (VERSION_UNKNOWN) is just as stale`.
5. **Giro durante el aviso (Minor 5, límite aceptado).** Una frase nueva en el KDoc de `launchUnlockWithKey` (`vault/VaultStore.kt`, «Limit, as for any state of the screen (BOV-3)...»): si la pantalla se gira mientras llega la respuesta, `onWrongKey` sí desactiva la huella, pero la pantalla nueva no repite `FINGERPRINT_STALE` (lo recibió la composición vieja); el botón simplemente no aparece. Es el mismo límite que el estado de error bajo BOV-3 y la regla de no usar `rememberSaveable`. A mirar en la prueba en el móvil.
6. **`disable` que lanza de verdad (Minor 6, límite aceptado).** No se construye nada: `fingerprintStale` no sobrevive a la pantalla siguiente y el aviso automático podría volver a salir en cada bloqueo. Caso casi inalcanzable (preocupación 3 de arriba, reescrita). Queda anotado aquí.

## Comandos y salida

```
$ ./gradlew :app:testDebugUnitTest --tests '*VaultStoreTest*' --offline --no-daemon --max-workers=1
BUILD SUCCESSFUL in 49s        (VaultStoreTest: 70 tests, 0 fallos, 0 errores, 0 omitidos)

# Mutación del punto 3 (intercambio de las dos líneas de onWrongKey en VaultScreen.kt; ya restaurado):
VaultStoreTest > CRY-A02 the unlock screen turns the fingerprint off with the wrong key and stops offering it FAILED
70 tests completed, 1 failed

$ gradle -p jvm-check test --no-daemon --max-workers=1 --offline
BUILD SUCCESSFUL in 1m 33s     (1251 pasan, 0 fallos; antes 1250)

$ ./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets --no-daemon --max-workers=1 --offline
BUILD SUCCESSFUL in 4m 1s      (1256 tests, 0 fallos, 0 errores, 0 omitidos; antes 1255)
```

Cifras: `VaultStoreTest` 69 a 70 (+1: el del orden con `VERSION_UNKNOWN`); app 1255 a 1256; jvm-check 1250 a 1251.

## Archivos modificados en la ronda

- `app/src/main/java/app/seguridad/vault/VaultStore.kt` (KDoc de `launchUnlockWithKey` y un comentario; sin cambio de lógica).
- `app/src/test/java/app/seguridad/vault/VaultStoreTest.kt` (1 test nuevo, 4 renombres o retoques, el test de cableado más estricto, el ayudante `unknownVersionVault()`).

## Revisión experto-seguridad-cripto (ronda 1)

- La lógica no cambia, solo comentarios y tests. `onWrongKey` sigue llamándose únicamente desde la rama `CryptoFailure` (`vault/VaultStore.kt:101-107`).
- El orden real queda descrito y probado en los dos casos: versión 2/3, `MasterRequired` antes de probar la clave; `VERSION_UNKNOWN`, `CryptoFailure` antes del reloj o los 7 días. En ninguno `MasterRequired` desactiva la huella (`VaultStoreTest`, tests de 7 días, reloj y los dos del orden). La clave se comprueba a cero en las ramas nuevas del test.
- Sin textos nuevos, sin permisos, red ni dependencias.

## Revisión Ingeniero Móvil Senior (ronda 1)

- El comentario ya no atribuye al Keystore un fallo que `BiometricVault.disable` no produce: borra preferencias con `edit{}` (`android/BiometricVault.kt:151-156`) y llama a `deleteKey()` (`:157`), que va en `runCatching` (`:102-104`). El `try` de `launchUnlockWithKey` queda como defensa documentada como tal.
- El test de cableado fija el orden `fingerprintStale = true` y luego `disable` (`ui/screens/VaultScreen.kt:318-321`), y que el botón y el aviso automático siguen regidos por `bio` (`:324`, `:350`).
- Límite de giro anotado en el KDoc (punto 5), sin `rememberSaveable`.

## Textos para el Abogado Digital (revisión final)

Sin cambios: sigue siendo solo `FINGERPRINT_STALE` (`vault/VaultStore.kt:65`), con el texto de antes. Anotación de la revisión: el texto afirma como hecho las dos causas entre paréntesis y GCM no distingue una clave ajena de una bóveda dañada (preocupación 1); que la revisión final decida si «puede deberse a» encaja mejor.

---

# Ronda 2 (segunda revisión de la tarea 3: dos restos en comentarios)

Revisión: `.superpowers/sdd/2026-11-01-clavero-1.3.1/task-3-rereview-1.md`. Todos los puntos de la ronda 1 estaban resueltos; quedaban dos restos de una línea, solo en comentarios (sin cambio de lógica).

## Cambios

1. `ui/screens/VaultScreen.kt:308`: el comentario decía «…even if the Keystore refused to turn it off». Ahora dice «…even if turning it off ever threw.», como el resto (defensivo). Con esto es verdad lo que decía la ronda 1: ya no se nombra el Keystore en `VaultStore.kt`, `VaultScreen.kt` ni `VaultStoreTest.kt` (`grep "Keystore refused"` no da nada).
2. `VaultStoreTest.kt:1031`: «(see the next test)» apuntaba a un test que está seis más abajo. Ahora remite al otro por su nombre: «(see «a version-3 vault asks for it before trying the key», below)».

## Qué se ha ejecutado

Solo comentarios, así que no se repiten las suites completas:

```
$ ./gradlew :app:testDebugUnitTest --tests '*VaultStoreTest*' :app:assembleDebug --offline --no-daemon --max-workers=1
BUILD SUCCESSFUL in 47s        (VaultStoreTest: 70 tests, 0 fallos, 0 errores, 0 omitidos; assembleDebug compila)
```

Las cifras globales no cambian respecto a la ronda 1 (app 1256, jvm-check 1251).

## Revisión experto-seguridad-cripto (ronda 2)

Solo comentarios; ninguna línea de código ejecutable cambia. `onWrongKey` sigue llamándose solo desde la rama `CryptoFailure`.

## Revisión Ingeniero Móvil Senior (ronda 2)

Sin cambios de comportamiento, de estado de Compose ni de concurrencia. El comentario de `ui/screens/VaultScreen.kt:308` ya describe `fingerprintStale` como defensa, no como respuesta a una negativa del Keystore que `BiometricVault.disable` no produce.

## Textos para el Abogado Digital (revisión final)

Sin cambios.
