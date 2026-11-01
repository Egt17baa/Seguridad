## Veredictos

Rango `adbba00..6bff07a` (el árbol de trabajo está en `f6178ef`, que solo añade 9 líneas de registro a `progress.md`). Los textos del Abogado se compararon con un script (`abogado-1.3.1.md` contra los archivos, carácter a carácter) y cada afirmación contra el código.

**1. Textos del Abogado**
- **O-1, ADDRESSED.** `VaultStore.kt:67-69` es idéntico al texto propuesto y al bloque Kotlin del dictamen; `VaultStoreTest.kt:996-997` fija el mismo literal. Es verdad contra el código: `FINGERPRINT_STALE` solo sale de `StaleBiometricKey` (`VaultStore.kt:490-493`), que se lanza cuando `VaultCrypto.authenticated` da `null`, es decir, clave de otra bóveda o bloque dañado (`Vault.kt:313-321`, el KDoc lo dice). Las dos primeras causas son alcanzables porque cambiar la maestra e importar desactivan la huella después de guardar (`VaultBackup.kt:128-131`, `:174-175`). «Vuelve a activarla» es verdad (`VaultScreen.kt:321-324`). El KDoc `VaultStore.kt:60-66` nombra las tres causas.
- **R-1, ADDRESSED.** `README.md:48` idéntico. Contra `git show 9504ce3:README.md`: la 1.3.0 leía `CP1:` y `.cpenc` (`:48`) e importaba `criptopanel-vault` «se vuelve a cifrar con el formato nuevo» (`:49`). Importar reemplaza la bóveda (`VaultBackup.kt:161`, «Se reemplazará la bóveda actual de este móvil por la de la copia.»). Comprobado en línea con `gh api repos/Egt17baa/Seguridad/releases/tags/v1.3.0`: existen `Clavero-v1.3.0.apk` y `Clavero-v1.3.0.apk.sha256` (lo que el Abogado no pudo ver, dictamen §4.4). «No se instala encima»: `versionCode` por número de ejecución (`app/build.gradle.kts:33-34`, `PUBLICAR.md:86,90`).
- **R-2, ADDRESSED.** `docs/privacidad.html:46` idéntico. `HttpClient.kt:28` envía `Clavero/$appVersion`, que viene de `BuildConfig.VERSION_NAME` (`SeguridadApp.kt:21`, `AppContainer.kt:120`) = «1.3.1» (`app/build.gradle.kts:35`). La fecha de `:22` sigue en «31 de octubre de 2026» (el diff solo toca `:46`).
- **R-3, ADDRESSED.** `LicensesScreen.kt:37` idéntico, en una sola línea. Verdad: la EFF está en `MASTER_WORD_LISTS` (`MasterPolicy.kt:34`); `newMasterProblem` rechaza un núcleo que es una palabra de las listas (`:154`); solo se llama al crear y al cambiar la maestra (`VaultScreen.kt:256`, `VaultBackup.kt:234`; KDoc `MasterPolicy.kt:127-130`); el Generador usa la EFF para las frases (`SecurityToolsScreen.kt:939`). Cumple `PublishedTextsTest` («mismas palabras», «mismo orden», «números de dado», sin «sin cambios»).
- **R-4, ADDRESSED.** La frase de §10 está idéntica (`auditoria-1.3.1.md:386`, aparece una sola vez) y §3.2 CRY-A02 lleva la misma corrección (`:144`). Verdad: crear y borrar desactivan antes (`VaultScreen.kt:281-282`, `:378-379`); cambiar la maestra e importar, después de guardar (`VaultBackup.kt:128-131`, `:174-175`; `VaultStore.kt:885-898`).

**2. Los dos Menores del repaso (`final-rereview.md`, «Lo nuevo que se rompe» 1 y 2)**
- **Menor 1, ADDRESSED.** `VaultStoreTest.kt:1022-1023`, `:1035-1037` y `:1100-1103` describen `VaultCrypto.authenticated`, `StaleBiometricKey` (no `CryptoFailure`) y el orden (regla de acceso primero para versión declarada; clave primero sin versión), todo contra `VaultStore.kt:471-498` y `Vault.kt:313-321`. No queda ningún `resolve()`/`opened()` mal descrito en `app/src/test/java/app/seguridad/vault/` (grep). Detalle menor en «Lo nuevo que se rompe», punto 2.
- **Menor 2, ADDRESSED.** `TrustedChain.kt:24` dice «¿Filtrada?» y «Comprobar filtraciones», los nombres reales (`ToolsScreen.kt:43`, `HealthScreen.kt:180`). No queda «Comprobar contraseña» en `app/`, `docs/`, `README.md` ni `CLAUDE.md`.

**3. Documentos**
- **Plan, ADDRESSED.** `plans/2026-11-01-clavero-1.3.1.md:162` lleva la nota «Corrección (1-11-2026, revisión final)»; cierta salvo el matiz del punto 3 de «Fuera del alcance».
- **«Desactivan la huella antes», ADDRESSED.** Grep en `README.md`, `CLAUDE.md`, la auditoría, el traspaso, el diseño, el plan, la plantilla del PR, `PUBLICAR.md`, la ficha y la política: las únicas afirmaciones sobre el orden son `CLAUDE.md:95`, `auditoria-1.3.1.md:144`, `:386`, `:435`, `:437` y `traspaso.md:61`, todas con la distinción correcta. El diseño `:78` no habla de orden (ver «Fuera del alcance»).
- **Referencias al cierre del código, ADDRESSED.** `CLAUDE.md:40`, traspaso `:9-10`, auditoría `:6`, `:8`, `:13`, `:347`, `:360`. Comprobado: `git diff --name-only 9504ce3..284d206 -- app jvm-check` da 50 archivos (56 y 49 hasta `95ddc44`, más `LicensesScreen.kt`); `git log 9504ce3..284d206` son 45 commits, todos `Claude <noreply@anthropic.com>`, 0 con `Claude-Session`/`claude.ai`; 1 358 líneas de más de 120 caracteres en `284d206` y 1 356 en `95ddc44` (mismo `awk length`). Los restos de `95ddc44` (`CLAUDE.md:50`, auditoría `:59`, `:67`, `:94`, `:240`, `:356`) son históricos y exactos.
- **Muestra de `archivo:línea` en `284d206`, ADDRESSED (más de 25 comprobadas con `git show 284d206:<ruta>`).** `VaultStore.kt:471-498`, `:474-477`, `:487-488`, `:494`, `:128`, `:100-122`, `:461-469`, `:97-98`, `:93-95`, `:112-119`, `:139` (`NOT_A_VAULT`), `:67-69`, `:60-66`, `:896-897`, `:841-842`, `:303-305`; `VaultStoreTest.kt:975`, `:994-1001`, `:1099`, `:1115`, `:1173`, `:1188`, `:1234`, `:1246`, `:1257`; `LicensesScreen.kt:36-37`; `TrustedChain.kt:21-24`, `:29-33`; `README.md:24`, `:48`, `:308`, `:312`; `privacidad.html:46`. Todas apuntan a lo que dicen.
- **Sección del Abogado (§11.1) y regla de la ruta de importación, ADDRESSED.** §11.1 resume fielmente el dictamen (veredicto, hechos, tabla O-1 a R-4, premisa corregida, lo no valorado, observación de `VaultBackup.kt:174-180`). La fila nueva de §6 es exacta: `opened()` relee y solo lanza `CryptoFailure` (`VaultStore.kt:363-385`), `checked` se decodificó antes (`:841-842`), `collectGarbage` se traga sus errores (`:303-305`), y la línea «Ruling» existe en `progress.md` (`f6178ef`). El §8.2 sobre la URL es cierto: la única línea añadida en `app/src/main` con URL es la de la EFF (`git diff -U0 9504ce3 284d206`) y la URL de la licencia ya estaba en `9504ce3`.
- **`CLAUDE.md`, ADDRESSED.** `:52` marca la revisión del Abogado como hecha (O-1 y R-1 a R-4); el paso del dueño de la fecha y GitHub Pages está en «Pendientes» `:3` (con la línea «Última actualización») y `:9`; la decisión del Abogado, en «Decisiones de la 1.3.1». `PUBLICAR.md:94` (apartado 3.5) existe.
- **Borrador de la nota de la versión, ADDRESSED con un Menor.** Cada frase es cierta contra el código: 10 caracteres y una sola palabra conocida (`MasterPolicy.kt:150-156`); solo al crear o cambiar (`:127-130`); huella (`VaultStore.kt:100-122`, `VaultScreen.kt:321-324`); TOTP 6 a 8 dígitos, 10 a 120 s, SHA1/SHA256/SHA512 y «2FA no admitido» (`Totp.kt:88-91,97-102,105,114`); eMudhra aplicado a la cadena que valida Android (`DistrustedCertificates.kt:109-113`); la única conexión sigue siendo HIBP. Sin palabras prohibidas (ver 5). Sigue la guía del Abogado. Menor: punto 1 de «Lo nuevo que se rompe».

**4. Tests, ADDRESSED.** El informe (`last-wave-report.md`, tabla de «Verificaciones») da app 1293 y jvm-check 1288, 0 fallos, desde `cleanTest`, con los comandos completos, antes de cada commit. No se re-ejecutó nada. Corroboración solo de lectura: la suma de los XML que quedan en `app/build/test-results/testDebugUnitTest` (81 archivos) es 1293 tests, 0 omitidos, 0 fallos, 0 errores, y la de `jvm-check/build/test-results/test` (80) es 1288 con 0/0/0, ambas escritas a las 22:20-22:22 del 1-11-2026, antes de `6bff07a` (22:22:15). Ninguna duda que justifique un test aislado.

**5. Reglas, ADDRESSED**
- Líneas añadidas (`git diff -U0 adbba00 6bff07a`, 131 líneas) contra «imposible», «100 %», «cuántic», «como signal», «validez legal», «seguro/a», «anónim», «garantiz», «de verdad», «limpio/a», «sospech»: una sola coincidencia, la nota de la versión del traspaso `:47` («no garantiza que la contraseña sea fuerte»). **Es aceptable:** `ForbiddenWords` (`app/src/test/java/app/seguridad/tools/ForbiddenWords.kt`) solo admite «garantiza» justo después de un «no» entero (`denial`), y la frase no contiene «seguro». Reproduje sus expresiones regulares en Python sobre el borrador completo, O-1, R-1, R-2 y R-3: ninguna da problemas. El texto de la nota queda dentro de la regla, aunque no la pasa ningún test (está en un `.md` de `docs/superpowers/handoff/`).
- Correos añadidos: solo `noreply@anthropic.com` (línea de autoría de los commits en la auditoría). Ningún correo personal.
- `docs/privacidad.html:22` sin cambios («31 de octubre de 2026»).
- Nada bajo `app/src/test/resources/seguridad/` ni `vectors/` en el rango (`git diff --name-only adbba00 6bff07a`); tampoco entre `9504ce3` y `284d206` (solo desaparecen los tres archivos de `criptopanel/` de la tarea 6). Manifiesto, `res/`, `gradle/`, `build.gradle.kts`, `settings.gradle.kts`, `jvm-check/gradle/` y los workflows, sin diferencias entre `9504ce3` y `284d206`.

## Lo nuevo que se rompe

Nada Crítico ni Importante. Tres detalles:

1. **Menor, texto del borrador de la nota de la versión: `docs/superpowers/handoff/2026-11-01-clavero-1.3.1-traspaso.md:47`.** Dice «la app pide al menos 10 caracteres y no acepta una contraseña fácil de adivinar, por ejemplo una sola palabra conocida». Leída sola, la frase afirma más de lo que hace el código: «Barcelona1» y dos palabras juntas pasan (`MasterPolicy.kt:132-141`; auditoría §6). La frase siguiente («Es una ayuda: quita los casos más evidentes y no garantiza que la contraseña sea fuerte») la corrige, así que no es una falsedad con el contexto, y es un borrador que el dueño todavía puede cambiar. Si se quiere cerrar del todo por la regla de veracidad, basta cambiar «no acepta una contraseña fácil de adivinar» por «rechaza las contraseñas que estima fáciles de adivinar». Es solo documentación y no necesita otra pasada de código.
2. **Informativa, `VaultStoreTest.kt:1101`.** El comentario dice «refuseIfDue in VaultStore.unlockWithKey»; `refuseIfDue` es una función local de `unlockWithKeyLocked` (`VaultStore.kt:478`), que `unlockWithKey` llama bajo el mutex (`:464`). Lo que describe (la regla se decide desde el archivo antes de usar la clave) es exacto.
3. **Informativa, `auditoria-1.3.1.md:355`.** «Los 43 del código (hasta `284d206`)»: son 42 hasta `95ddc44` más `284d206`, y dejan fuera `adbba00` y `18c25ce`, que son de documentos, aunque en los 42 ya había commits de documentos. La afirmación es verdadera (los 45 son de Claude y sin enlaces); la etiqueta «del código» es floja. Con «los 45 commits de `9504ce3..284d206`» sería exacta.

## Fuera del alcance

1. `auditoria/04-cripto.md:114` y `auditoria/04-cripto-parteA.md:413` siguen citando el texto antiguo de `FINGERPRINT_STALE`. Son el informe de origen sobre la 1.3.0 (históricos; el diseño dice que no cambian).
2. `docs/superpowers/specs/2026-11-01-clavero-1.3.1-design.md:78` dice que `CryptoFailure` desactiva la huella; desde la F1 solo lo hace `StaleBiometricKey`. Es el diseño original y el registro y la auditoría ya recogen el cambio.
3. La nota del plan (`:162`) y la auditoría (`:123`, `:482`) dicen que las listas se leen «una vez por proceso». El KDoc de `WordLists` (`MasterPolicy.kt:84-89`) matiza que una lectura fallida no se guarda y que la EFF se lee como mucho dos veces por proceso. Simplificación ya existente en la auditoría y sin consecuencias.
4. `abogado-1.3.1.md` §4.3 (si CriptoPanel se publicó para terceros) sigue sin comprobar, y §11.1 de la auditoría no lo repite. R-1 no depende de ello.
5. No se revisó el resto de `progress.md` más allá de la línea «Ruling» de la ruta de importación.

## Veredicto

**Todo ADDRESSED.** O-1, R-1, R-2, R-3 y R-4 aplicados con los textos exactos del Abogado y verdaderos contra el código; los dos Menores del repaso, arreglados; plan, referencias de cierre, sección del Abogado y `CLAUDE.md`, correctos; tests 1293 / 1288 sin fallos; reglas cumplidas (la frase «no garantiza que la contraseña sea fuerte» es aceptable). Sin puntos abiertos que bloqueen. Un Menor opcional de redacción en el borrador de la nota de la versión (punto 1) y dos informativas; por la regla de «los detalles menores se arreglan todos», conviene retocar el punto 1 (una línea de documentación) antes del PR.

## Marcadores `[PENDIENTE]` que se refieren a esta comprobación

Para que el controlador los marque como hechos:

- `CLAUDE.md:53`: fila de la tabla «Comprobación corta de la última tanda (`284d206` y sus documentos) | Pendiente».
- `docs/auditorias/2026-11-01-auditoria-1.3.1.md:11`: en la lista «Quedan pendientes (marcados **[PENDIENTE]**)», «la comprobación corta de la última tanda».
- `docs/auditorias/2026-11-01-auditoria-1.3.1.md:60`: §1.3, punto 4, «Falta una comprobación corta de esa tanda **[PENDIENTE]**».
- `docs/auditorias/2026-11-01-auditoria-1.3.1.md:452`: §12, «Queda», punto 1, «La comprobación corta de la última tanda … **[PENDIENTE]**».
- `docs/superpowers/handoff/2026-11-01-clavero-1.3.1-traspaso.md:13`: «**Falta:** la comprobación corta de la última tanda».
- `docs/superpowers/handoff/2026-11-01-clavero-1.3.1-traspaso.md:28`: «Para Claude», punto 1, «La comprobación corta de la última tanda».
- Aparte (del controlador): `docs/superpowers/handoff/sdd-1.3.1/progress.md:129`, «Short scoped check dispatched (sonnet, adbba00..6bff07a)», pide su línea de cierre.

No se refieren a esta comprobación y siguen pendientes: `CLAUDE.md:54`, la auditoría `:379-383` (pruebas en el móvil) y `:453-456` (PR, APK firmado, fecha de la política y etiqueta), y los pasos del dueño del traspaso.

## Revisión experto-seguridad-cripto

- Ningún cambio del rango toca un formato, una clave, un parámetro, la red ni un camino de ejecución: `284d206` cambia el valor de una constante (`VaultStore.kt:67-69`), un literal (`LicensesScreen.kt:37`) y comentarios (`VaultStoreTest.kt:1022-1103`, `TrustedChain.kt:24`). El único camino que desactiva la huella sigue siendo `StaleBiometricKey` (`VaultStore.kt:490-493`, `:112-119`) y el orden de las comprobaciones no cambia (`:471-498`).
- O-1 no da al atacante ninguna información nueva (AES-GCM no distingue las causas, `Vault.kt:313-321`) ni invita a una acción peligrosa: pide la maestra y reactivar la huella.
- R-3 y la nota de la versión no presentan la regla de la maestra como garantía de fuerza (`MasterPolicy.kt:132-141`).
- Sin `Log.`, `println`, `printStackTrace` ni `rememberSaveable` en las líneas añadidas de `app/src/main`; la única URL es la atribución de la licencia.

## Revisión Ingeniero Móvil Senior

- `FINGERPRINT_STALE` se enseña en `UnlockVault` por `onError` y `Text(it, color = NegativeColor)` (`VaultScreen.kt:321-324`, `:364`): unos 30 caracteres más, se parte en líneas. La nota de la EFF va en `bodySmall` (`LicensesScreen.kt:59`), una sola `Component(...)`.
- Sin cambios de ciclo de vida, hilos, permisos ni componentes: `launchUnlockWithKey` sigue `ATOMIC` (`VaultStore.kt:107`) y el manifiesto, `res/` y las dependencias son iguales a `9504ce3`.
- La versión de publicación no se recompiló con esta tanda y la auditoría lo dice (§8.2); solo cambian textos y comentarios, y `assembleDebug`, `lintDebug`, `checkLicense`, `checkFragmentVersion` y `checkAssets` están en verde según el informe.

## Textos para el Abogado Digital (revisión final)

Ninguno nuevo. Los textos del rango son los suyos, aplicados tal cual. El borrador de la nota de la versión (`traspaso.md:39-51`) lo escribió el implementador siguiendo su guía; si el dueño lo cambia, que lo mire otra vez (el Menor del punto 1).
