# Clavero 2.0: los menores de los tres repasos de la ronda de arreglos de la auditoría (21-11-2026)

**Quién:** el implementador.
**Encargo:** todos los menores de `audit-fixes-recheck-ab.md` (m-1 a m-7), `audit-fixes-recheck-cde.md` (C-1.m1, C-7.m1, D-1.m1, D-1.m2) y `advisor-t12-audit-fixes-recheck.md` (M-1 a M-4; M-4 es el m-1 del repaso AB y se cuenta una vez).
**Base:** `6d2109a`. Rutas: `m/` = `app/src/main/java/app/seguridad/`, `t/` = `app/src/test/java/app/seguridad/`.

## 1. Tabla: menor, commit, rojo y verde

| Menor | Commit | Qué | Rojo | Verde |
|---|---|---|---|---|
| m-1 = M-4 | `a76d355` | `m/AppContainer.kt:776-780` (KDoc de `forgetOrphanVaults`: un fallo de la huella no para lo demás; uno de los ajustes los deja como estaban) y `:792-794` (el `catch`: la clave que queda envuelve la clave de datos, que abre las copias de antes, con la huella) | Comentario: no aplica | Compila; suite entera |
| m-7 | `a76d355` | `m/ui/screens/EntryRow.kt:356-362` y `:368-369` (KDoc de `PasswordOfferOnReturn`: en pantalla dividida o ventana flotante la oferta sale al momento en la parte de Clavero; a pantalla completa, salir bloquea y la acaba); `m/AppContainer.kt:217-222` (`leftForAnotherApp`) | Comentario: no aplica | Suite entera |
| M-1 | `0c5cf3f` | `m/imports/MergePlan.kt:414-422` y `:428-434`: la bóveda entera; el margen es lo que la contabilidad puede cambiar entre la instantánea y el guardado; la garantía en un solo sentido, con la franja del lado seguro | KDoc: no aplica | Suite entera |
| m-2 | `ff503dc` | `t/AppContainerVaultsTest.kt:695`, `aFingerprintThatCannotBeForgottenDoesNotStopTheDeletion`: con `forgetBiometric` que lanza, `deleteVault` da `DELETED`, la carpeta, la fila y los ajustes se van; en un segundo contenedor, el barrido prueba los dos huérfanos y quita los ajustes de los dos aunque el primero lance | (1) los dos `catch` relanzando: `IllegalStateException: Keystore`; (2) solo el barrido parando en el primer fallo (`return@withLock` en el `catch`): `each orphan tried, in its own try expected:<[c…, d…]> but was:<[c…]>` | `AppContainerVaultsTest` 32/0/0 |
| D-1.m2 | `0067da6` | `m/vault/PendingUpgrade.kt:105-113`: el resultado se guarda en `made` dentro del bloque y se usa cuando `waited` es falso; `afterUnlock` toma `waitMs` (por defecto `UPGRADE_COPY_WAIT_MS`, `:99`) para que un test lo acorte. Test nuevo `t/vault/PendingUpgradeTimeoutTest.kt`: la copia se despacha (`yield()`), retiene su hilo hasta que la espera la cancela (sin punto de suspensión, tope de 10 s) y devuelve `Saved` | Con el código anterior (`else null`): `expected:<seguridad-antes-de-2-0-prueba.sgbak> but was:<null>` | 1/0/0 |
| D-1.m1 | `4a481c1` | `t/vault/VaultV4Test.kt:322`, `:331`, `:338`, `:344`: `upgradeAutoCopyNotDone` falso con la copia hecha, con un fallo rápido, con una copia que lanza y sin carpeta | `autoCopyNotDone = true` en lugar de `= waited` (`PendingUpgrade.kt:122`): `a quick failure is not a copy that took too long (recheck D-1.m1)` | `VaultV4Test` 14/0/0 |
| m-6 | `a4d236f` | `t/PlatformSourcesTest.kt:73-75`: el bloque de `edit(commit = true) { … }` de `forget` quita `KEY_IV`, `KEY_BLOB` y `KEY_IN_STRONGBOX` | Quitando `remove(KEY_IN_STRONGBOX)` de `m/android/BiometricVault.kt`: `removes KEY_IN_STRONGBOX: … remove(KEY_IV) remove(KEY_BLOB)` | `PlatformSourcesTest` verde |
| m-4 | `a04ef88` | `t/vault/BackupV3Test.kt:192`: el texto de copia dañada de la 1.4.3 (`a3ac942`, `Backup.kt:58`), no el de la 2.0; la línea en blanco antes del KDoc y el KDoc nombra `:58-59` | Sin rojo propio: ese camino no lo prueba ningún caso; es fidelidad de la copia | `BackupV3Test` 10/0/0 |
| M-2 | `d5d83e4` | `t/vault/UpgradeCopyFirstTest.kt:422`: «the copy ended» excluye el `….tmp`; líneas en blanco antes de los KDoc (`UpgradeCopyFirstTest`, `ContactsViewTest`, `PasswordMarkTest`). Los dos rojos pedidos, abajo | `theWaitEndsWhenTheUnlockCallerIsCancelled`: con las dos defensas quitadas (`copyBeforeUpgrade` sin `scope.async` y `afterUnlock` con `EmptyCoroutineContext` en lugar de `NonCancellable`): `the copy ended: []`. Con solo una quitada sigue verde (se cubren entre sí, como predijo el director). `aCopyWithAnEntryAtTheCapAndItsMarkIsAccepted`: con `checkCopyEntries` midiendo el tope de una entrada con la marca: `CryptoFailure: La copia está dañada: no se puede importar. Elige otra copia.` | `UpgradeCopyFirstTest` 14, `PasswordMarkTest` 9, `ContactsViewTest` 48, sin fallos |
| C-7.m1 | `2f5b9bf` | `t/tools/WorkflowHardeningTest.kt:73-81`: los `if:` de trabajo de `build-firma` y `play-bundle` son exactamente `if: ` + la condición de confianza, y `play-bundle` necesita `build-firma`; tres alteraciones nuevas en `eachCheckFailsOnAnAlteredCopy` (`play-bundle` con `if: always()`, `build-firma` sin `if:`, `play-bundle` sin `build-firma` en `needs`), y `altered()` comprueba que cada alteración cambia el YAML. El workflow no cambia | Sin las comprobaciones nuevas, `eachCheckFailsOnAnAlteredCopy` falla en la alteración `if: always()` (`AssertionError`) | 5/0/0 |
| C-1.m1 | `5444482` | `app/build.gradle.kts:250-254` y `jvm-check/build.gradle.kts:72-76`: entradas `CLAUDE.md`, los `build.gradle.kts` y `settings.gradle.kts` de la raíz y de `jvm-check`, y los árboles `.maestro`, `scripts`, `gradle`, `.github`, `jvm-check/src` (y en jvm-check `../app/src`), sin ningún `build/` | Con el código anterior, una línea añadida a `.maestro/README.md` dejaba `:app:testDebugUnitTest` `UP-TO-DATE` | La misma alteración vuelve a ejecutar la tarea, también con `CLAUDE.md`; sin cambios, `UP-TO-DATE` |
| m-5 | `53809e3` | `README.md:416` («en `versionName`, en el `versionCode` (lo pone la CI) y en `android:taskAffinity=""`») y el comentario de `scripts/check-manifest.sh:13-14` | Texto: no aplica | `bash scripts/check-manifest.sh` sobre el APK de depuración: `OK (… debug)` |
| M-3 (con m-3) | `e68601b` | `docs/auditorias/2026-11-21-auditoria-2.0.md`: las tres líneas (B-2 en la tabla, el repaso de documentos y el inventario para el Abogado) citan ya «Pruebas de la 2.0», `README.md:416` y `:117`, con `versionCode`. `pendientes.md`: fila 12 (la oferta en pantalla dividida o ventana flotante), 51 (con AC-E1), 83 (la ventana de verdad, `VaultStore.kt:646-648`, el `finally` de `PendingUpgrade.kt:126`, y AC-E1), 87 (`539d2fd` y `edit(commit = true)`), y la 106 nueva (móvil, abierta) | Documentos: no aplica | `PublishedFilesNameNoModelTest` verde |
| Fila 105 | este commit | `pendientes.md`: los menores de los repasos, Hecho, con los commits | — | — |

## 2. Lo no hecho, y por qué

- **Nada se ha dejado sin hacer.** Ninguno me pareció incorrecto ni peligroso.
- **m-4** no tiene rojo: la línea cambiada no la recorre ningún caso del test (lo dijo el revisor). No añadí un caso nuevo, porque solo probaría la propia copia del test.
- **D-1.m2** cambia la firma de una función interna: `afterUnlock` toma `waitMs` con el valor por defecto de siempre, y su único llamador (`m/vault/VaultStore.kt:600`) no cambia. Sin eso, el rojo pedía esperar 120 s de verdad. El primer intento del test salía verde con el código anterior: `withTimeoutOrNull` devuelve el valor si el bloque corre sin despacharse hasta el final, aunque el tiempo se haya agotado. Con un `yield()` al empezar la copia, el rojo es seguro. El test no usa `sleep`: espera hasta que la cancelación llega, con un tope de 10 s.
- **M-2 en `audit-fixes-report.md` §1:** el director pedía copiar allí los dos rojos. Están en este informe (tabla, fila M-2), sin tocar el informe de la ronda.
- **Fila 80:** sigue «Abierto». Su cierre es del repaso final de documentos, como dice el director.
- **La puerta del APK** (`docs/auditorias/2026-11-21-auditoria-2.0.md`, paso 6) dice «salvo `versionName` y `android:taskAffinity=""`». Para un APK de depuración es exacto; para el firmado cambia también el `versionCode`. No lo cambié: no está entre las tres líneas de M-3. Lo apunto para el repaso final de documentos.

## 3. Suites enteras

Sobre `53809e3`, con los cambios de código ya hechos (después solo cambian documentos):
- `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets`: **BUILD SUCCESSFUL**. Los XML dan **2 603 tests, 0 fallos, 0 errores, 0 omitidos** (2 601 de antes más los dos nuevos: `aFingerprintThatCannotBeForgottenDoesNotStopTheDeletion` y `aCopyThatEndsAsTheWaitRunsOutIsStillTheCopy`).
- Lint: 21 avisos, ningún error. Son los 21 de antes; ninguno nuevo.
- `./gradlew --stop`, y luego `./gradlew -p jvm-check test`: **BUILD SUCCESSFUL**, **2 538 tests, 0 fallos, 0 errores, 0 omitidos** (2 536 más los dos nuevos).
- `grep -rnE '^\s*@Ignore' app/src`: vacío.
- `bash scripts/check-manifest.sh app/build/outputs/apk/debug/app-debug.apk`: `OK (…, debug)`.

## Revisión experto-seguridad-cripto

- **Gestión de claves (A-1, CWE-459):** el «lo que se pueda» queda fijado. Un fallo de `forgetBiometric` no para el borrado (`m/AppContainer.kt:754-761`) ni el barrido de los huérfanos (`:788-801`). Cada huérfano está en su propio `try`, y el test lo comprueba con dos. Los comentarios dicen ya la verdad: la clave que queda envuelve la clave de datos, que abre las copias de antes, y para usarla hace falta la huella (`:792-794`).
- **Preferencias de la huella (B-3):** el ancla mira las tres claves que quita `forget` (`t/PlatformSourcesTest.kt:73-75`). Para la primera bóveda, cuyo archivo se queda, eso es lo que olvida la huella.
- **Cadena de firma (C-7, CWE-1357):** con C-3 abierto, los `if:` de `build-firma` y `play-bundle` (`.github/workflows/build-apk.yml:168`, `:283`) y el `needs` de `play-bundle` (`:281`) quedan fijados por test (`t/tools/WorkflowHardeningTest.kt:73-81`), con su alteración roja.
- **Disponibilidad y veracidad (D-1, CWE-451):** `made` (`m/vault/PendingUpgrade.kt:107`, `:113`) evita decir «sin carpeta» de una copia que se hizo. Queda la otra cara de la ventana: si el tiempo vence antes del `.also`, se dice «no ha terminado» de una copia recién hecha. Es un texto que no es falso del todo, y la copia queda apuntada, como aceptó el director. `upgradeAutoCopyNotDone` queda fijado en falso en los cuatro caminos que no son el tiempo agotado (`t/vault/VaultV4Test.kt:322-344`).
- **Formatos y primitivas:** ningún commit toca `m/crypto/`, `seguridad-vault`, `seguridad-backup`, `SG1:`, `.sgenc`, el AAD ni las marcas. Las alteraciones para los rojos se deshicieron todas; `git status` queda limpio salvo este informe.
- **Secretos y registros:** nada nuevo se guarda ni se registra. El test nuevo usa solo nombres y contraseñas de prueba.

## Revisión Ingeniero Móvil Senior

- **Concurrencia:** `withTimeoutOrNull` sigue dentro de `withContext(NonCancellable)` (`m/vault/PendingUpgrade.kt:101-113`). `made` y `waited` son locales de esa corrutina; se escriben en el bloque y se leen después de que vuelve, en el mismo hilo lógico. El test usa `Dispatchers.Default`, no tiempo virtual, porque la carrera necesita que el temporizador corra en otro hilo.
- **Ciclo de vida:** los KDoc de `PasswordOfferOnReturn` (`m/ui/screens/EntryRow.kt:356-369`) y de `leftForAnotherApp` (`m/AppContainer.kt:217-222`) describen lo que pasa: en multiventana la actividad sigue `STARTED`, `collectAsStateWithLifecycle` recoge y la oferta sale al momento. A pantalla completa, `MainActivity.onStop` bloquea.
- **Compilación:** las tareas `test` de las dos suites declaran todo lo que recorre `PublishedFilesNameNoModelTest` (`app/build.gradle.kts:250-254`, `jvm-check/build.gradle.kts:72-76`). Usan `fileTree`, que tolera un directorio que falte y es compatible con la caché de configuración.
- **Manifiesto:** `scripts/check-manifest.sh` sigue pasando sobre el APK de depuración. Solo ha cambiado su comentario.

## Textos para el Abogado Digital (revisión final)

- `README.md:416` (m-5): la frase nombra también el `versionCode` que pone la CI. Es documentación técnica, no un texto para el usuario.
- Ningún texto de la interfaz cambia. Los demás menores son comentarios, tests, la compilación y documentos técnicos (la auditoría y `pendientes.md`).
