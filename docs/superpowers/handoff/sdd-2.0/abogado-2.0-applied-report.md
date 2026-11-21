# Clavero 2.0: cambios del dictamen final del Abogado Digital, aplicados

- **Fecha:** 21-11-2026. **Rama:** `ccr-0aa27f95-u038cd`, desde `d321bba`.
- **Fuentes:** el dictamen `sdd-2.0/abogado-2.0.md` (`35954f4`) y la última «Owner decision» de `progress.md` (21-11-2026): la ficha del anexo A se da por buena, R-7 se hace y R-1 a R-6 y R-8 se aplican todos.
- **Skills:** `experto-seguridad-cripto` (SKILL.md) y, del Ingeniero Móvil Senior, `desarrollo-movil-senior` y `seguridad-criptografia-movil` (R-7 toca el camino de la huella).

## 1. Cambio → commit → archivo:línea

| Cambio | Commit | Dónde |
|---|---|---|
| R-7: `FINGERPRINT_DATA_UNREADABLE`, solo tras la huella cuando la clave autentica y los datos no se decodifican; la huella se conserva | `3f65f92` | `app/src/main/java/app/seguridad/vault/VaultStore.kt:93-100` (constante), `:713` (KDoc), `:760-768` (el `catch` alrededor de `VaultCrypto.read`); tests `app/src/test/java/app/seguridad/vault/VaultStoreTest.kt:1002-1007` y `:1247-1265` |
| O-1: `DELETE_IDENTITY_CONFIRMATION` | `1ccd965` | `app/src/main/java/app/seguridad/vault/ContactsView.kt:43-46`; test `ContactsViewTest.kt` (`losTextosFijosDicenLoQueDebenDecir`) |
| O-2: `a6StaleText` | `1ccd965`, `e6195bf` | `app/src/main/java/app/seguridad/vault/A6Text.kt:152-167`; test `AutoBackupTargetsTest.kt` (`theStaleTextSaysWhere`); anexo `docs/superpowers/specs/2026-10-27-seguridad-1.3.0-textos.md:452` |
| R-1: el botón de «Salud» | `1ccd965`, `e6195bf` | `app/src/main/java/app/seguridad/ui/screens/HealthScreen.kt:255`; `README.md:27` |
| R-2: `UPGRADE_COPYING` | `1ccd965`, `e6195bf` | `app/src/main/java/app/seguridad/vault/VaultStore.kt:1771-1779`; test `ErrorTextsTest.kt` (`theTextOfASaveWhileTheUpgradeCopyRuns`); `README.md:421` |
| R-3: `KDF_COST_MORE` | `1ccd965` | `app/src/main/java/app/seguridad/vault/VaultsTexts.kt:136-141`; test `KdfCostHintTest.kt:18-25` |
| R-4: `NEEDS_MASTER_NOTICE` | `1ccd965` | `app/src/main/java/app/seguridad/vault/ContactsView.kt:68-74`; test `ContactsViewTest.kt` |
| R-5: `HEALTH_EMPTY_VAULT` y `VAULT_EMPTY_NOTHING_TO_COPY` | `1ccd965` | `app/src/main/java/app/seguridad/tools/ToolCatalog.kt:49`; `vault/VaultsTexts.kt:128`; test `AppContainerVaultsTest.kt:337-343` |
| R-6: las copias de antes de importar y de antes del formato nuevo | `e6195bf` | `docs/privacidad.html:52`; `README.md:23`; notas `2026-11-15-clavero-2.0-notas.md:31` |
| O-3: la descripción completa del anexo A | `13e7028` | `docs/ficha-google-play.md:21-65`; la larga, en `:67` («Versión larga (no cabe en Play)»); tests `PublishedTextsTest.kt:78-98` y `FinalLegalReviewTest.kt:158-164` |
| R-8: la línea del máximo | `13e7028` | `docs/ficha-google-play.md:9` |
| O-4: las condiciones de B-2 y B-4, fuera del bloque que se pega | `d41f73b` | `docs/superpowers/handoff/2026-11-15-clavero-2.0-notas.md:12-13` (el bloque empieza en `:19`) |
| O-5: la fila 15 lo dice ahora explícitamente | `ee36398` | `pendientes.md`, fila 15 |
| `pendientes.md` (filas 4, 5, 14, 15, 19, 41, 46, 53), §11.1 de la auditoría y `CLAUDE.md` | `ee36398` | `docs/superpowers/handoff/sdd-2.0/pendientes.md`; `docs/auditorias/2026-11-21-auditoria-2.0.md`, §11.1; `CLAUDE.md` (fila de la tabla «Paso de la 2.0» y la línea «Abogado final» de «Decisiones de la 2.0») |

La fecha de `docs/privacidad.html:22` (fila 49) **no se ha tocado**.

## 2. Lo adaptado y por qué

- **R-7, dónde se pone el texto.** El encargo apuntaba a `biometricUnlockError` (`VaultStore.kt:98-101` antes del cambio). El texto se pone en el almacén, justo alrededor de `VaultCrypto.read` en `unlockWithKeyLocked`, después de que `VaultCrypto.authenticated` haya dicho que la clave autentica: así solo ese camino lo dice. Hacerlo en `biometricUnlockError` habría obligado a comparar el mensaje con el literal por defecto de `CryptoFailure`, que es frágil. `biometricUnlockError` no cambia: sigue pasando el texto propio del almacén a la pantalla, y `VAULT_NO_MEMORY` (el `OutOfMemoryError`, que va por otro `catch`) sigue igual. Sigue siendo un `CryptoFailure`, así que `launchUnlockWithKey` no llama a `onWrongKey` y la huella no se apaga: CRY-A02 no cambia en comportamiento. El desbloqueo con la maestra no pasa por ahí y conserva «Contraseña incorrecta o datos dañados».
- **TDD de R-7.** Primero se cambió la esperanza del test `F1 …` al texto literal y se ejecutó `VaultStoreTest` en `jvm-check`: 79 tests, 1 fallo (`expected:<[La huella funcionó, …]> but was:<[Contraseña incorrecta o datos dañados]>`). Después de la implementación, 79 tests y 0 fallos.
- **O-3, la versión larga.** El dictamen propone conservar la larga «si se quiere» en una sección propia. Se conserva debajo, como «Versión larga (no cabe en Play)», con una línea que dice que no se pega. Los tests que miraban hechos de la ficha en todo el archivo se limitan ahora a lo que se pega, la sección «Descripción completa» (si no, los habría cumplido la larga):
  - `PublishedTextsTest.thePolicyNamesTheTemporaryCopiesTheFolderAndTheHashPrefix` buscaba «carpeta que elijas» y «copias al portapapeles», que no están en el anexo A. Ahora busca lo que dice el anexo: «donde elijas», «5 primeros caracteres» y «exportas, guardas, copias o compartes». El anexo no es falso: «copias» es el verbo de la misma lista, y lo que se copia al portapapeles es lo que sale. Lo aceptó el dueño.
  - `FinalLegalReviewTest.r7TheListingNamesNoMessagingBrand` buscaba «envíalo por el canal que quieras (una app de mensajería o el correo)»; el anexo dice «envíalos por el canal que quieras», sin nombrar ninguna app. La comprobación de que no aparece ninguna marca de mensajería sigue sobre toda la ficha.
  - Test nuevo `PublishedTextsTest.theFullPlayDescriptionFits` (R-8): la sección que se pega mide como mucho 4 000, también si cada salto contara dos, lleva los dos avisos («Sin recuperación», «Sin auditoría independiente») y no tiene negritas de Markdown.
  - Recuento del archivo final: 3 927 caracteres (3 927 unidades UTF-16), 42 saltos de línea y 3 969 si cada salto contara dos.
- **R-1 en la política.** `docs/privacidad.html:60` sigue diciendo «Comprobar filtraciones»: es el principio del nombre del botón, y lo que dice (qué se envía) sigue siendo cierto. El dictamen no pedía cambiarlo, y no se ha tocado para no mover la política fuera de lo pedido. Los pasos de prueba antiguos del README (1.1.0 y 1.3.1, `:262` y `:346`) tampoco cambian.
- **Pendientes 46 y 53.** Se añade el estado nuevo y se conserva el anterior detrás de «Antes:».
- **Tests añadidos para fijar O-1, O-2 y R-4.** Antes no los fijaba ningún test. Se añaden asserts mínimos en los tests de textos que ya había, para que el texto corregido no vuelva atrás.

Ningún cambio rompe un formato ni cambia el comportamiento. R-7 solo cambia el texto.

## 3. Resultados de las suites

De una en una, con el cerrojo (`flock /home/user/.gradle-run.lock`), sobre `ee36398`:
- `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets --no-daemon --max-workers=1`: **BUILD SUCCESSFUL** (8 min 49 s). Según los XML de `app/build/test-results/testDebugUnitTest/`: **2 604 tests, 0 fallos, 0 errores y 0 omitidos** (antes 2 603; el nuevo es `theFullPlayDescriptionFits`). Lint: **0 errores** y 22 avisos, todos de versiones y configuración (`GradleDependency` 7, `NewerVersionAvailable` 8, `AndroidGradlePluginVersion` 2, `DiscouragedApi`, `ModifierFactoryExtensionFunction`, `ObsoleteSdkInt` y `OldTargetApi`, 1 cada uno). Ninguno está en un archivo tocado aquí.
- `./gradlew --stop`.
- `./gradlew -p jvm-check test --no-daemon --max-workers=1`: **BUILD SUCCESSFUL** (3 min 46 s). Según los XML: **2 539 tests, 0 fallos, 0 errores y 0 omitidos** (antes 2 538).
- `grep -rnE '^\s*@Ignore' app/src`: no da nada.

## Revisión experto-seguridad-cripto

- **R-7 no filtra nada que antes no se supiera.** El texto nuevo solo sale después de que `VaultCrypto.authenticated` haya autenticado los datos con la clave que soltó la huella (`VaultStore.kt:750-753`). Eso ya lo distinguía el comportamiento (la huella se apagaba o no, CRY-A02), y quien llega a ese punto ya tiene la clave. El oráculo «contraseña incorrecta frente a datos dañados» del desbloqueo con la maestra sigue igual: un solo mensaje (`Crypto.kt:221`, sin cambios).
- **La clave se sigue borrando en todos los fallos:** el `throw CryptoFailure(FINGERPRINT_DATA_UNREADABLE)` (`VaultStore.kt:766`) sale por el `catch (e: Throwable)` de `unlockWithKey` (`:719-727`), que borra la clave si no se quedó en la bóveda abierta. El `OutOfMemoryError` sigue con su `key.fill(0)` y `VAULT_NO_MEMORY` (`:771-774`).
- **Los textos son verdad frente al código:** O-1 (la identidad no va a la papelera; `DELETE_IDENTITY_WARNING` no cambia), O-2 (dice lo que hizo la petición), R-2 (es verdad también sin carpeta), R-3 (concuerda con `KDF_COST_KEPT`, `VaultsTexts.kt:122-125`), R-1 (`HealthScreen.kt:253`: `enabled` solo con accesos `LOGIN`) y R-5. No se envía ninguna contraseña más a `api.pwnedpasswords.com` (minimización; fila 5).
- **Sin cambios** de formato, primitivas, red, permisos, dependencias ni logs. `grep -rnE '^\s*@Ignore' app/src` no da nada.

## Revisión Ingeniero Móvil Senior

- **Concurrencia de R-7:** el `try`/`catch` va dentro del mismo `withContext(compute)` y bajo el mutex del almacén (`VaultStore.kt:759-770`). No añade ningún punto de suspensión ni cambia el orden de `active()`, `commit` ni la purga de la papelera (`purgedTrash` se aplica a lo leído, como antes).
- **Pantalla:** `launchUnlockWithKey` (`VaultStore.kt:133-154`) no cambia. Un `CryptoFailure` va a `onError(biometricUnlockError(e))`, que devuelve el mensaje propio, así que el texto nuevo llega tal cual a la pantalla de bloqueo, sin estado nuevo ni recomposición distinta.
- **Compose:** R-1 cambia solo el literal de `Text` del botón (`HealthScreen.kt:255`); su longitud sigue cabiendo en un `Button` de ancho completo, que parte la línea.
- **Lint y compilación:** `assembleDebug` y `lintDebug` en verde, sin errores de lint ni avisos en los archivos tocados (§3).

## Textos para el Abogado Digital (revisión final)

Los textos finales, tal como quedan:
- `DELETE_IDENTITY_CONFIRMATION`: «Se borrará tu identidad de esta bóveda: no pasa por la papelera ni se puede deshacer. Las copias ya hechas (automáticas o exportadas) pueden conservar la identidad que tenían.»
- `a6StaleText` (segunda frase): «Esta petición no borró las copias hechas después de ese cambio: si cambiaste la contraseña otra vez, puede que alguna se abra con una contraseña anterior. Míralo a mano.»
- Botón de «Salud»: «Comprobar filtraciones de los accesos (envía solo 5 caracteres del hash de cada contraseña)».
- `UPGRADE_COPYING`: «Clavero está preparando el paso de la bóveda al formato nuevo, con su copia automática si la hay. Espera a que termine e inténtalo otra vez.»
- `KDF_COST_MORE` (el trozo cambiado): «… sube a este la próxima vez que la abras con la contraseña maestra, si el móvil tiene memoria para ello; nunca se baja. …»
- `NEEDS_MASTER_NOTICE`: «Para guardar tu identidad y tus contactos hace falta tu contraseña maestra, una sola vez.»
- `HEALTH_EMPTY_VAULT`: «Esta bóveda está vacía: aún no tiene archivo. Para crear o importar otra bóveda, abre antes una de las que tienes.»
- `VAULT_EMPTY_NOTHING_TO_COPY`: «Esta bóveda está vacía: aún no tiene archivo, así que no hay nada que copiar.»
- `FINGERPRINT_DATA_UNREADABLE` (R-7): «La huella funcionó, pero los datos de la bóveda no se pudieron leer: pueden estar dañados. Prueba con la contraseña maestra.»
- La política, el README y las notas (R-6): «… las que exportaste y las de antes de importar o de antes del formato nuevo, hasta que las borres; las automáticas, hasta que la rotación las quite.» (en el README y las notas, «las exportadas y las de antes de importar…»).
- README `:27` (R-1): «Si se pulsa «Comprobar filtraciones de los accesos», consulta Have I Been Pwned con k-anonimato las contraseñas de los accesos, una a la vez; …».
- README `:421` (R-2): «Mientras espera, un cambio dice «Clavero está preparando el paso de la bóveda al formato nuevo, con su copia automática si la hay…»».
- Ficha: la descripción completa es el anexo A, sin cambios; la línea de R-8: «**Longitud (R-8 del Abogado, revisión final de la 2.0):** la descripción completa no puede pasar de 4 000 caracteres (contador de Play Console).»
- Notas, condiciones de O-4: las dos líneas del dictamen, con «(O-4 del Abogado Digital, revisión final de la 2.0)» al final.

Para ver: la política, `:60`, sigue diciendo «Comprobar filtraciones» (§2).
