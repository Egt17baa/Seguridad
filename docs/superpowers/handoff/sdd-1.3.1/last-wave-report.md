# Informe de la última tanda de la 1.3.1 (1-11-2026)

Rama `ccr-a1a6eabb-765f9g`. Dos commits, los dos subidos a `origin` sin `amend`, `rebase` ni `force`. Autor `Claude <noreply@anthropic.com>`; el mensaje termina solo en `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` y `git log -1 --format=%B | grep -cE "Claude-Session|claude\.ai"` da 0 en los dos.

| Commit | Asunto | Qué lleva |
|---|---|---|
| `284d206` | textos: aviso de la huella, licencia de la EFF y comentarios de la revisión final | Código y tests (puntos 1 a 4). Es el último commit de código. |
| `6bff07a` | docs: revisión del Abogado Digital de la 1.3.1 aplicada | Documentos (puntos 5 a 11). |

Sin subagentes. No se ha tocado `docs/superpowers/handoff/sdd-1.3.1/progress.md` ni nada de `.superpowers/` salvo este informe.

## Qué cambió, por punto

### Commit 1, `284d206`

1. **O-1, `FINGERPRINT_STALE`.**
   - `app/src/main/java/app/seguridad/vault/VaultStore.kt:67-69`: texto exacto del Abogado: «La huella ya no abre esta bóveda. Puede deberse a un cambio de la contraseña maestra, a una copia importada o a datos dañados. Entra con la contraseña maestra y vuelve a activarla si quieres.»
   - KDoc `VaultStore.kt:60-66`: ahora dice que la clave no autentica las entradas, que puede ser la de otra bóveda **o** un bloque dañado, que AES-GCM no los distingue y que por eso el texto dice «Puede deberse a» y nombra los tres casos.
   - Test que fija el literal: `app/src/test/java/app/seguridad/vault/VaultStoreTest.kt:994-1001` (antes `:996`), con el texto nuevo. Sigue comprobando `ForbiddenWords`.
2. **R-3, nota de la EFF.** `app/src/main/java/app/seguridad/ui/screens/LicensesScreen.kt:37`: texto exacto del Abogado. `PublishedTextsTest.theEffListIsSaidToBeAdaptedEverywhere` (`PublishedTextsTest.kt:101-108`) exige «mismas palabras», «mismo orden» y «números de dado» y que no diga «sin cambios»: el texto nuevo los cumple, la línea sigue siendo una sola, y no hizo falta tocar ningún literal de ningún test. `SourceTextsTest` y `FinalLegalReviewTest` en verde.
3. **Detalle menor 1 del repaso.** `VaultStoreTest.kt:1022-1023`, `:1035-1037` y `:1100-1103`: los comentarios describen `VaultCrypto.authenticated` + `StaleBiometricKey`, la regla de acceso primero para una versión declarada (`refuseIfDue` en `unlockWithKey`) y `VERSION_UNKNOWN` con la clave primero. Comprobado contra `VaultStore.kt:471-498` y `Vault.kt:313-321`. No queda ningún `resolve()` ni `opened()` mal descrito en los tests de `vault/` (grep).
4. **Detalle menor 2 del repaso.** `app/src/main/java/app/seguridad/android/TrustedChain.kt:24`: «check «¿Filtrada?» and «Comprobar filtraciones» with network on a phone at every raise of targetSdk».

### Commit 2, `6bff07a`

5. **R-1.** `README.md:48`: texto exacto del Abogado. Comprobado contra el README de `9504ce3` (la 1.3.0 leía `CP1:` y `.cpenc` e importaba `criptopanel-vault`, «se vuelve a cifrar con el formato nuevo»).
6. **R-2.** `docs/privacidad.html:46`: texto exacto del Abogado. **La fecha de `:22` (31 de octubre de 2026) no se ha tocado.** `HttpClient.kt:28` envía `Clavero/$appVersion` y `app/build.gradle.kts:35` pone 1.3.1.
7. **R-4 y todo lo que decía «antes».**
   - `docs/auditorias/2026-11-01-auditoria-1.3.1.md` §10, «Sin prueba en el móvil, porque la interfaz no lo permite» (antes `:385`): texto exacto del Abogado.
   - Mismo documento, §3.2 CRY-A02, «Sin prueba en el móvil» (`:144`): crear la bóveda y borrarla antes (`VaultScreen.kt:281-282,378-379`); importar y cambiar la maestra, justo después de guardar (`VaultBackup.kt:128-131,174-175`, `VaultStore.kt:885-898`). Lo comprobé leyendo los cuatro sitios.
   - `grep` de «caminos», «desactivan» y «desactivar la huella» en `README.md`, `CLAUDE.md`, el traspaso, el diseño, el plan y la plantilla del PR: **no hay ningún otro sitio** que diga que todos los caminos desactivan «antes». En `CLAUDE.md` y en el traspaso añadí en la decisión de la huella la distinción (antes: crear y borrar; justo después de guardar: cambiar la maestra e importar).
8. **Plan.** `docs/superpowers/plans/2026-11-01-clavero-1.3.1.md:162`: «Corrección (1-11-2026, revisión final)»: desde F6 las listas se leen una vez por proceso y fuera del hilo principal (`vault/MasterPolicy.kt`, `WordLists`).
9. **Cierre del código en `284d206`.**
   - `CLAUDE.md:40`; traspaso `:9-10`; auditoría `:6,8,13` y los sitios que citan los tests y el APK.
   - Auditoría: «Alcance» pasa a `git diff 9504ce3..284d206 -- app jvm-check`, **50 archivos** de código (los 49 de `95ddc44` más `LicensesScreen.kt`); «Comprobado sobre `9504ce3..284d206`» (§8.2); **43 commits** del código, todos de Claude y sin enlaces de sesión (`git log 9504ce3..284d206`: 45 commits, 45 de `Claude <noreply@anthropic.com>`, 0 enlaces); «Tests en `284d206`» (§9) con los comandos de esta tanda; «1 358 líneas» de más de 120 (antes 1 356: dos líneas del KDoc nuevo, medidas con `awk length` como antes).
   - §8.2 aclara dos cosas verdaderas: la única línea añadida en `app/src/main` con una URL es la nota de la EFF (la URL de atribución ya estaba en la 1.3.0), y la versión de publicación no se recompiló con esta tanda (solo cambian textos y comentarios).
   - **Líneas de la auditoría puestas al día** (`VaultStore.kt` +2 desde la línea 64; `VaultStoreTest.kt` +1/+2/+3 tras `:1022`, `:1035` y `:1100`): `VaultStore.kt:471-498` (antes `469-496`), `:474-477`, `:487-488`, `:494`, `:128` (`StaleBiometricKey`), `:100-122` (`launchUnlockWithKey`), `:461-469`, `:97-98` y `:93-95` (KDoc), `:112-119`, `:139` (`NOT_A_VAULT`), `:67-69` (§11, punto 3), `:461-498`; `VaultStoreTest.kt:975-1257`, `:1099`, `:1115`, `:1173`, `:1188`, `:1234,1246`. Las abrí una a una en `284d206`. `LicensesScreen.kt:36-37`, `TrustedChain.kt:21-24,29-33`, `README.md:24,48,308-312` y `privacidad.html:46` no se mueven (edición dentro de una línea).
10. **Sección del Abogado en la auditoría.**
    - Nuevo §11.1 «Revisión final del Abogado Digital (1-11-2026)»: veredicto «se puede publicar, con cambios»; ninguna norma exige cambios con los hechos que da por ciertos (y qué hechos); tabla con O-1 y R-1 a R-4 y dónde está cada cambio; la premisa corregida; lo que no pudo valorar; y su observación de `VaultBackup.kt:174-180`.
    - §6 (límites aceptados): nueva fila con la ruta de importación: si `opened()` fallara justo después de guardar (`VaultStore.kt:896-897`), la pantalla diría «No se pudo importar la copia» y no desactivaría la huella (`VaultBackup.kt:174-180`). Sin cambio: casi inalcanzable (`checked` se decodificó antes, `VaultStore.kt:841-842`; `collectGarbage` se traga sus errores, `:303-305`) y con la 1.3.1 una huella vieja acaba en `FINGERPRINT_STALE` y se desactiva.
    - §11 (la lista que se le entregó) puesta al día en los puntos 3, 14, 15 y 17; §12 «Siguientes pasos» marca como hechos el repaso acotado y la revisión del Abogado.
11. **Estado y pasos del dueño.**
    - `CLAUDE.md`, tabla «Estado actual»: revisión del Abogado Digital «Hecha (1-11-2026)… O-1 y R-1 a R-4 aplicados»; el repaso acotado, hecho; fila nueva «Comprobación corta de la última tanda», pendiente; el PR sigue pendiente.
    - `CLAUDE.md`, «Pendientes, del dueño»: pasos 3 (política: fecha y GitHub Pages porque `privacidad.html` cambió) y 4 (notas de la versión); el 9 lo repite para la 1.3.1. Una decisión nueva: «Revisión del Abogado Digital».
    - Traspaso §3, pasos del dueño (5): fecha de la política y Pages; guía del Abogado para las notas de GitHub (decir que la 1.3.1 deja de abrir los formatos de CriptoPanel, no presentar la regla de la maestra como algo que hace fuerte la contraseña, ninguna palabra prohibida) y un borrador. El borrador es el único texto nuevo mío (ver más abajo).

## Verificaciones

Con `export ANDROID_HOME=/opt/android-sdk`, desde cero, sin red, un worker:

| Cuándo | Comando | Resultado |
|---|---|---|
| Antes del commit 1 | `gradle -p jvm-check cleanTest test --no-daemon --max-workers=1 --offline` | BUILD SUCCESSFUL, **1288 tests, 0 fallos, 0 errores, 0 omitidos** |
| Antes del commit 1 | `./gradlew :app:cleanTestDebugUnitTest :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets --no-daemon --max-workers=1 --offline` | BUILD SUCCESSFUL, **1293 tests, 0 fallos, 0 errores, 0 omitidos**; lint 0 errores y 10 avisos (6 `NewerVersionAvailable`, 2 `GradleDependency`, `DiscouragedApi`, `ObsoleteSdkInt`), los de siempre |
| Antes del commit 2 (los tests leen `README.md` y `docs/`: `FinalLegalReviewTest`, `PublishedTextsTest`, `AppNameTest`, `ToolTextsTest`) | `gradle -p jvm-check cleanTest test …` | BUILD SUCCESSFUL, **1288 tests, 0 fallos** |
| Antes del commit 2 | `./gradlew :app:cleanTestDebugUnitTest :app:testDebugUnitTest …` | BUILD SUCCESSFUL, **1293 tests, 0 fallos** |

Los recuentos salen de los XML de `build/test-results` (suma de `tests`, `failures`, `errors` y `skipped`). No se añadió ningún test, por eso los totales no cambian.

## Palabras prohibidas y correos

`grep -niE "imposible|100 ?%|cuántic|como signal|validez legal|\bseguro|\bsegura\b|anónim|garantiza|de verdad|limpio|sospechoso"` sobre las líneas añadidas (`git diff -U0 | grep '^+'`):

- **Commit 1:** sin coincidencias (`grep exit: 1`).
- **Commit 2:** una sola coincidencia, el borrador de la nota de la versión del traspaso: «…y no garantiza que la contraseña sea fuerte», que es la forma permitida («no garantiza»).
- Correos en las líneas añadidas: solo `noreply@anthropic.com` (la línea de los commits de la auditoría, ya existente). Ningún correo personal.
- Erratas en los textos del Abogado: ninguna; los copié tal cual.

## Revisión experto-seguridad-cripto

- **Ningún cambio toca un formato, una clave, un parámetro ni un camino de ejecución.** El commit 1 cambia el valor de una constante de texto (`VaultStore.kt:67-69`), un literal de `LicensesScreen.kt:37`, y comentarios (`VaultStoreTest.kt:1022-1103`, `TrustedChain.kt:24`). Las dos pantallas y `StaleBiometricKey` (`VaultStore.kt:128`) siguen usando la constante, así que no cambia qué desactiva la huella (solo `StaleBiometricKey`, `VaultStore.kt:112-119`; orden de las comprobaciones, `:471-498`).
- **O-1 es verdad contra el código:** `VaultCrypto.authenticated` (`Vault.kt:313-321`) devuelve `null` si AES-GCM no autentica con ninguna versión candidata, sea porque la clave es de otra bóveda o porque el bloque está dañado; el texto nombra los tres casos como posibles, no como un hecho. «Vuelve a activarla» sigue siendo cierto (`onWrongKey` desactiva la huella).
- **R-3 es verdad:** `MASTER_WORD_LISTS` incluye la lista de la EFF (`MasterPolicy.kt:34`) y `newMasterProblem` rechaza un núcleo que sea una palabra de ellas (`MasterPolicy.kt:150-156`), solo al crear la bóveda y al cambiar la maestra (KDoc `:127-130`). El Generador usa la EFF para las frases.
- **R-1, R-2 y R-4 no debilitan nada:** R-1 manda a la 1.3.0 publicada y avisa de que importar reemplaza la bóveda; R-2 describe el User-Agent real; R-4 corrige un riesgo residual sin rebajarlo (la huella con clave vieja solo queda tras una muerte del proceso o un error justo después de guardar).
- **Límite aceptado nuevo y explícito:** `VaultBackup.kt:174-180` (§6 de la auditoría). No se rebaja ninguna severidad.
- **Sin logs ni secretos nuevos:** ninguna línea añadida en `app/src/main` con `Log.`, `println`, `printStackTrace` ni URL de red (la única URL es la atribución de la licencia en `LicensesScreen.kt:37`; la única mención de `rememberSaveable` es un comentario que dice que no se usa).

## Revisión Ingeniero Móvil Senior

- **Interfaz:** `FINGERPRINT_STALE` se enseña en `UnlockVault` por `onError` y `Text(it, color = NegativeColor)` (`VaultScreen.kt:321-324,364`); unos 30 caracteres más, que se parten en líneas sin problema. La nota de la EFF se pinta en `bodySmall` y se parte en líneas (`LicensesScreen.kt:59`); `Component(...)` sigue siendo una sola línea, como pide `PublishedTextsTest`.
- **Sin cambios de ciclo de vida, hilos, permisos ni componentes:** `launchUnlockWithKey` sigue `ATOMIC` y el límite BOV-3 del giro sigue documentado (`VaultStore.kt:97-98`). `AndroidManifest.xml`, `res/` y las dependencias no cambian (`git diff 9504ce3..284d206` vacío en esos caminos).
- **Compilación:** `assembleDebug`, `lintDebug`, `checkLicense`, `checkFragmentVersion` y `checkAssets` en verde. La versión de publicación (`assembleRelease`) **no** se recompiló con esta tanda: solo cambian textos y comentarios; el APK de publicación se comprobó en `94ddb7f` y en `95ddc44`, y lo vuelve a comprobar la prueba del dueño con el APK firmado. Se dice así en la auditoría, §8.2.
- **TalkBack:** el texto de la huella y la nota de Licencias son texto plano en un `Text`; no hay semántica nueva.

## Textos para el Abogado Digital (revisión final)

Son sus propios textos, aplicados tal como los escribió, más el borrador de las notas de la versión que sigue su guía:

1. **O-1**, `FINGERPRINT_STALE`, `app/src/main/java/app/seguridad/vault/VaultStore.kt:67-69`: «La huella ya no abre esta bóveda. Puede deberse a un cambio de la contraseña maestra, a una copia importada o a datos dañados. Entra con la contraseña maestra y vuelve a activarla si quieres.»
2. **R-3**, `app/src/main/java/app/seguridad/ui/screens/LicensesScreen.kt:37`: «De la Electronic Frontier Foundation, con las mismas palabras y en el mismo orden; solo se ha quitado la columna de los números de dado. El Generador la usa para las frases y, al crear la bóveda o cambiar la contraseña maestra, la app no acepta como maestra una sola de sus palabras. Licencia: https://creativecommons.org/licenses/by/3.0/us/»
3. **R-1**, `README.md:48`: «La 1.3.1 deja de abrir los formatos de CriptoPanel (`CP1:`, `.cpenc` y su bóveda). La 1.3.0 (`Clavero-v1.3.0.apk`, en *Releases*) los sigue abriendo: con ella se pueden descifrar esos textos y archivos para cifrarlos de nuevo, e importar la copia de la bóveda de CriptoPanel, que se vuelve a cifrar en el formato de Clavero (importar reemplaza la bóveda del móvil: exporta antes la tuya). La 1.3.0 no se instala encima de la 1.3.1: úsala antes de actualizar o en otro móvil.»
4. **R-2**, `docs/privacidad.html:46`: «…y el servicio ve además el nombre y la versión de la app («Clavero/» seguido del número de la versión instalada, por ejemplo «Clavero/1.3.1»).» La fecha de la política (`:22`) no se ha tocado.
5. **R-4**, `docs/auditorias/2026-11-01-auditoria-1.3.1.md` §10: «…todos los caminos que cambian la clave de datos desactivan la huella: crear la bóveda y borrarla, antes; importar una copia y cambiar la maestra, justo después de guardar, así que solo una muerte del proceso o un error en ese momento la dejaría activa (§3.2, CRY-A02).» Y la misma corrección en el §3.2, «Sin prueba en el móvil».
6. **Borrador de la nota de la versión de GitHub** (mío, `docs/superpowers/handoff/2026-11-01-clavero-1.3.1-traspaso.md`, §3, paso 5 del dueño). Sigue su guía: dice que la 1.3.1 deja de abrir los formatos de CriptoPanel, no presenta la regla de la maestra como algo que hace fuerte la contraseña («una ayuda… no garantiza que la contraseña sea fuerte») y no lleva ninguna palabra prohibida:

   > Clavero 1.3.1
   >
   > Versión de mantenimiento. No cambia ningún formato de Clavero: las bóvedas, las copias, los textos y los archivos de la 1.3.0 se siguen abriendo.
   >
   > - Dejan de abrirse los formatos de CriptoPanel (CP1:, .cpenc y su bóveda). Si los necesitas, usa antes la 1.3.0 (Clavero-v1.3.0.apk, en Releases): con ella se pueden descifrar esos textos y archivos para cifrarlos de nuevo, e importar la copia de la bóveda de CriptoPanel (importar reemplaza la bóveda del móvil: exporta antes la tuya). La 1.3.0 no se instala encima de la 1.3.1.
   > - Contraseña maestra nueva: al crear la bóveda o al cambiar la maestra, la app pide al menos 10 caracteres y no acepta una contraseña fácil de adivinar, por ejemplo una sola palabra conocida. Es una ayuda: quita los casos más evidentes y no garantiza que la contraseña sea fuerte. Abrir la bóveda e importar una copia no cambian.
   > - Huella: si la huella ya no abre la bóveda, la app lo dice y la desactiva. Se vuelve a activar entrando con la contraseña maestra.
   > - Códigos 2FA: un código con ajustes que Clavero no admite (fuera de 6 a 8 dígitos, de 10 a 120 segundos o de SHA1, SHA256 y SHA512) se rechaza en lugar de cambiarse, y la lista dice «2FA no admitido».
   > - Red: la única conexión sigue siendo la comprobación de contraseñas filtradas, y el bloqueo de los certificados de eMudhra se aplica también a la cadena que valida Android.

   Cada frase contra el código: 10 caracteres y rechazo de una palabra conocida (`MasterPolicy.kt:150-156`); solo al crear o cambiar (KDoc `:127-130`); huella (`VaultStore.kt:100-122`, `FINGERPRINT_STALE`); TOTP (`crypto/Totp.kt:87-102,108-114`); eMudhra en la cadena del sistema (auditoría, CRY-B01); formatos de Clavero sin cambios (auditoría §3.3 y §9).

## Para el controlador

- Queda, como dijo el controlador, la comprobación corta de esta tanda. En los documentos aparece como **[PENDIENTE]**.
- Fuera de lo pedido, sin tocar: `docs-report.md` y `.superpowers/sdd/…/context.md` (internos, ignorados por git) y los documentos históricos de la 1.0 a la 1.3.0.
- `docs/superpowers/handoff/sdd-1.3.1/progress.md` no se tocó, como se pidió: su cabecera seguirá diciendo lo de `95ddc44` hasta que el controlador copie su registro.
