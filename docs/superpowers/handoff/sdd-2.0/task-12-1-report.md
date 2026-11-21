# Clavero 2.0, tarea 12, paso 1 (versión y documentos): informe del implementador

**Estado: DONE_WITH_CONCERNS** (todo lo del encargo hecho y las dos suites en verde; las «concerns» son dos huecos del código frente al diseño y dos pendientes de proceso, que van a `pendientes.md`, filas 46 a 50, y a «Dudas»).

Encargo: «Ruling (advisor, T10 cierre y T12 paso 1)» de `progress.md`. Base: `ef0f09d`. Rama `ccr-0aa27f95-u038cd`.

## Commits

| Commit | Qué |
|---|---|
| `c0fe71f` | La tabla de afirmaciones `sdd-2.0/task-12-1-claims.md`, en su propio commit, antes de editar ningún documento |
| `e91316d` | README, `docs/privacidad.html`, `docs/ficha-google-play.md`, `docs/PUBLICAR.md`, el borrador de notas `2026-11-15-clavero-2.0-notas.md` y `versionName` 2.0.0 |
| `f2b583c` | (Del controlador, por el límite de uso) `pendientes.md` (filas 6, 7, 8, 22, 36 y 40 cerradas; 46 a 50 nuevas) y una copia a medias de este informe |
| (cierre) | «2.0, tarea 12 (paso 1): versión y documentos»: una frase de la política corregida (abajo) y este informe terminado |

## Tests

Ejecutados por separado en el árbol de trabajo (`ANDROID_HOME=/home/user/android-sdk`), contados con Python sobre los XML de resultados:

- `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets`: 2370 tests, 0 omitidos, 0 fallos, 0 errores; BUILD SUCCESSFUL
- `./gradlew --stop` y `./gradlew -p jvm-check test`: 2326 tests, 0 omitidos, 0 fallos, 0 errores; BUILD SUCCESSFUL

Los mismos números que en `dcf28dc` (app 2370, jvm-check 2326): el paso 1 no añade ni quita tests; los que leen los documentos (`PublishedTextsTest`, `FinalLegalReviewTest`, `AppNameTest`, `ImportTextsTest`, `SourceTextsTest`) pasan con los textos nuevos. La primera ejecución dio 1 fallo: `FinalLegalReviewTest.o1ThePolicySaysOnlyTheListedCopiesAreDeleted` (`:78`) prohíbe «que la app hizo en esa carpeta» en la política, y la frase nueva de la copia de antes de la 2.0 lo decía; pasa a «que la app tiene apuntada en esa carpeta», que además es lo exacto (A6 borra por la lista, no por quién la hizo). Con eso, las dos suites en verde.

## Qué cambió

1. **Tabla de afirmaciones** (`sdd-2.0/task-12-1-claims.md`, `c0fe71f`): 89 filas, en 14 apartados (versión, formato v4, varias bóvedas, papelera e historial, organización, lista, copias, QR de wifi, ayuda, aspecto, errores, lo que no cambia, lo guardado sin cifrar), cada una con su `archivo:línea`, si depende de Android y en qué documento va; y §14 con lo que el código contradice. Lo que depende de la versión de Android (el difuminado y los colores del fondo de pantalla, desde Android 12; StrongBox, desde Android 9) va así en los textos. Tras el commit se corrigieron tres referencias de línea (`Backup.kt:58`, `:60-62`, `EntryHistory.kt:31`) antes de editar documentos (mismo commit `c0fe71f`).
2. **Versión:** `versionName` 2.0.0 (`app/build.gradle.kts:35`). El `versionCode` es el número de ejecución del workflow (`-PversionCode="$GITHUB_RUN_NUMBER"`, `build-apk.yml:111-112` y `:315-324`; `PUBLICAR.md:86`): no se toca. Ningún test fija `versionName`; el único test que lee `.maestro/` es `CsvFormatsTest.kt:75` (un CSV). Por eso, según el punto 3 del Ruling, **`.maestro/` no se toca**: `p20-version-y-licencias.yaml:1` y `:21` siguen con «Clavero 1.4.3» y van a la tarea 11 (fila 48).
3. **README:**
   - «Funciones», la bóveda: tipos nuevos; varias bóvedas; organizar; plantillas, campos, notas con formato, duplicar y caducidad; papelera, deshacer e historial; la lista y la ficha; el formulario; las 5 últimas contraseñas siguen; «Salud» con sugerencias; copias en varias carpetas con calendario y el aviso de la comprobación.
   - El Inspector de QR con el QR de wifi, y «QR de una red wifi»; Ajustes; «Ayuda» (modo sencillo, guía, avisos del arranque y errores); «Aspecto» (con «desde Android 12» y O-C5).
   - «Formatos»: el aviso de la 2.0 (O-3): el paso a v4, la copia `seguridad-antes-de-2-0-`, «Exportar una copia antes» y «Seguir sin copia», la marca 3 de las copias y lo que dice la 1.4.
   - Fila 40: las líneas 320 y 329 citan ahora `COPY_DAMAGED` y `BACKUP_DAMAGED` con «… Elige otra copia.».
   - «Pruebas de la 2.0»: 16 pruebas, desde las filas «Móvil» de `pendientes.md` (3, 10, 11, 12, 13, 13 bis, 13 ter, 23, 28, 29, 34 y 44) más el paso desde la 1.4.3 (O-3), la copia de la 2.0 en la 1.4.3, una instalación nueva, varias bóvedas y la versión con los permisos. Cada una dice de qué fila sale, para la tarea 11.
4. **Política** (`docs/privacidad.html`), los 11 puntos de O-10 y R-C10 con O-C5:
   - los tipos nuevos en «Qué datos recogemos» y en «Dónde se guardan»; hasta 10 bóvedas; lo que va cifrado dentro de cada una (nombre, carpetas, vistas, colores, campos, historial, papelera, caducidades, último uso, última comprobación);
   - una lista de lo que el móvil guarda sin cifrar, sacada de los campos serializados (abajo);
   - «tarjetas de contacto»; las copias en hasta 3 carpetas con calendario, solo al abrir la app, sin segundo plano ni notificaciones; la copia de antes del formato nuevo; A6 por carpeta, con la copia de antes de la 2.0;
   - los selectores: «Al borrar la bóveda se olvidan todas» pasa a decir lo que hace el código (X-1);
   - reconocer lo pegado (no lee el portapapeles); papelera, historial y lo que queda en las copias; «Permisos» (sin notificaciones; vibración sin permiso); «Tus derechos» (borrar entradas, papelera, historial y una bóveda, y lo que no borra).
   - **La fecha no cambia** (10-11-2026): se pone justo antes de fusionar, como en la 1.4.0 (fila 49). Las frases que fijan `FinalLegalReviewTest` y `PublishedTextsTest` quedan palabra por palabra.
5. **Ficha:** las reglas de la cabecera con O-11 («cartera», «monedero»), O-C6, O-C4 y O-9; un apartado «Recursos gráficos» (O-C2); la descripción con los tipos nuevos, varias bóvedas, carpetas, papelera e historial, copias en 3 carpetas con calendario, el QR de wifi con su aviso y un apartado «Aspecto»; «cartera de hardware» de Repartir un secreto pasa a «dispositivo hardware» (O-11); en «Antes de usarla», el calendario solo al abrir, el paso al formato nuevo (O-3) y que borrar no borra las copias (O-2). La descripción breve no cambia (cabe en 80).
6. **`docs/PUBLICAR.md`:** la fecha de la política al publicar la 2.0; las tarjetas bancarias y las funciones financieras; la nota del CRA (R-12); «Salud» y las funciones de salud (O-11); el icono y las capturas nuevas (O-C2); O-C1 en las acciones del dueño.
7. **Notas** (`docs/superpowers/handoff/2026-11-15-clavero-2.0-notas.md`): GitHub y Play («Novedades», 413 caracteres), cada línea con su fila de la tabla entre corchetes (se quitan al publicar), con la fila 7 (O-3), la 22 (el QR pendiente) y la 36 (los errores). Sin nombres de otras apps.
8. **`pendientes.md`:** filas 6, 7, 8, 22, 36 y 40 cerradas con `e91316d`; filas 46 a 50 nuevas.
9. NOTICE y licencias: sin cambios (ninguna dependencia ni recurso de terceros nuevo; el logo es propio).

## Lo que el móvil guarda sin cifrar (para la política)

Sacado de los `@Serializable` de `settings.json` (`settings/Settings.kt:40-101`, `:170-195`; `vault/BackupSchedule.kt:29-60`, `:98-119`) y de `vaults.json` (`vault/VaultRegistry.kt:39-42`), más las preferencias (`android/BiometricVault.kt:116-121`, `:478-480`; `android/PrefsPlaceStore.kt:13`). Frente a la fila 8, lo que **no estaba** en ella:
- los ajustes de aspecto (`Settings.kt:44`, `:67-80`; fila 6, R-C10);
- por bóveda, el coste de Argon2id y la marca del reloj (de la 1.x, ahora por bóveda);
- por carpeta, la dirección, el calendario, la fecha y el error de la última copia y los identificadores de las copias (de la 1.x para una carpeta, ahora hasta 3);
- `pastTargets`: lo mismo de hasta 6 carpetas que ya no se usan (`Settings.kt:184-198`);
- el resumen de A6 ya enseñado (`A6State.said`, `A6Text.kt:55-67`), que lleva las direcciones de las carpetas;
- `autoBackupOffForNewVault`;
- la huella por bóveda (la clave envuelta, el IV y si está en StrongBox).

Todo eso va en la política, en una lista (corrección de la segunda pasada: le faltaban cuatro cosas, m-7 de la revisión del paso 1, que se añadieron; ver `task-12-2-report.md`). Ninguno es un secreto ni el contenido de una entrada (`Settings.kt:36-39`: «Never a secret, nor a vault's name»).

## Contradicciones entre el código y lo que se afirmaría (se avisa; no se arregla)

1. **X-1 (fila 46):** la política de la 1.4.2 decía «Al borrar la bóveda se olvidan todas» (los lugares de los selectores). Con varias bóvedas, «Borrar esta bóveda» (`AppContainer.kt:677-716`) no llama a `forgetAll`; solo lo hace «He olvidado la contraseña maestra» (`ui/screens/VaultScreen.kt:494-497`). Los lugares son de todo el móvil (`core/PickerPlaces.kt:97-100`). La política dice ahora lo que hace el código (O-10.7). Si se quiere otra cosa, es código: revisión de la rama.
2. **X-2 (fila 47):** R-6 del Abogado inicial (tras «Seguir sin copia», una línea en Ajustes que ofrezca hacer una copia hasta que se haga) no tiene una línea propia; lo más cercano es `BACKUP_CHECK_NEVER` («Aún no has comprobado ninguna copia.», `vault/BackupCheckTexts.kt:80`) y la misión «Comprueba una copia» (`health/Missions.kt:44`). Ningún documento afirma que exista.
3. Ninguna otra fila de la tabla choca con el código (corrección de la segunda pasada: la tabla estaba bien, pero el texto que llevaban los documentos sobre las copias y la papelera, I-1, y sobre el difuminado, m-5, decía más que el código; ver `task-12-2-report.md`). Un matiz que se corrigió al escribir: «Sólido» no es «el aspecto de la 1.x» (conserva la paleta, Ruling T5 bis); el README dice «paneles opacos sobre un fondo liso, sin transparencias», como `STYLE_SOLID_SHORT` (`ui/screens/AppearanceScreen.kt:80`).

## Dudas

1. **El borrador de notas y `ImportTextsTest`:** el encargo pide el borrador en `2026-11-15-clavero-2.0-notas.md`, pero `ImportTextsTest.thePlayListingAndItsNovedadesNameNoOtherProduct` solo lee las «Novedades» de los `*-traspaso.md`. Se comprobó a mano que no nombra ninguna app. Propuesta: el controlador pasa el bloque al traspaso de la 2.0 cuando lo escriba, o el test lee también `*-notas.md` (fila 50).
2. **`.maestro/p20`:** se deja en «Clavero 1.4.3» porque ningún test lo ata (punto 3 del Ruling); fallará con el APK 2.0.0 hasta la tarea 11 (fila 48; el README lo dice en «Pruebas de la 2.0»).
3. **La fecha de la política** queda en el 10-11-2026 con texto nuevo. Pages sirve desde `main`, así que no se publica nada antes de la fusión; el controlador la pone justo antes (fila 49).
4. **El plan** (tarea 12, «Archivos») pide también `docs/auditorias/2026-11-XX-auditoria-2.0.md` y el estado de `CLAUDE.md`: son de pasos posteriores (auditoría) y del controlador (Ruling, punto 8), así que no se tocan aquí.
5. **El README** sigue diciendo en «Pruebas de la 1.4.1» que P-20 espera «Clavero 1.4.3»: es historia de esa versión; la línea nueva de «Pruebas de la 2.0» dice lo de la 2.0.

## Revisión experto-seguridad-cripto

Lista de `references/` sobre el diff (solo documentos y una constante de versión):
- **Nada promete de más:** ningún texto dice «irrecuperable», «borrado seguro» ni que borrar una bóveda o vaciar la papelera borre las copias (O-2): `docs/privacidad.html` (párrafo de la papelera y «Tus derechos»), ficha («Borrar algo de la bóveda… no lo borra de las copias»). Coherente con que la clave de datos no cambia (dictamen inicial, «Revisión experto-seguridad-cripto»).
- **La política no esconde nada que esté sin cifrar:** la lista sale de los campos serializados (`Settings.kt:40-101`, `:170-195`; `BackupSchedule.kt:29-60`, `:98-119`; `VaultRegistry.kt:39-42`), no solo de la fila 8; las dos huellas SHA-256 (`VaultStore.kt:442-446`, `:1734-1737`) se describen como opacas y la sal como no secreta, que es cierto (está en el archivo).
- **Una sola conexión:** la política y la ficha siguen diciendo que solo «¿Filtrada?» y «Comprobar filtraciones» conectan; las misiones no (`health/Missions.kt:50`, O-6).
- **Portapapeles:** la frase de «reconocer lo pegado» es cierta: `vault/FormHelpers.kt:18`, `:58-62` (solo el bloque que recibe el campo).
- **QR de wifi:** el aviso citado es la constante (`tools/qr/WifiQr.kt:356`); el borrado del QR pendiente al salir (`MainActivity.kt:118-119`, `tools/qr/QrHandoff.kt:85-91`).
- **Formato:** la marca 3 y el rechazo de la 1.4 (`vault/Backup.kt:41`, `:82`); la copia de antes de la 2.0 que abre la 1.4 es la del archivo de antes del cambio (`ui/screens/VaultUpgrade.kt:63-68`).
- Sin hallazgos de seguridad en el diff: no cambia código salvo `versionName`.

## Revisión Ingeniero Móvil Senior

- **Versión:** `versionName` 2.0.0 en `app/build.gradle.kts:35`; el `versionCode` sigue saliendo de `-PversionCode` (`:33-34`), sin cambiar el nombre del workflow (`AppNameTest`). El User-Agent toma `BuildConfig.VERSION_NAME` (`SeguridadApp.kt:26`), así que será «Clavero/2.0.0»; la política da un ejemplo de la 1.3.1 que sigue siendo válido como ejemplo.
- **Lo que depende de Android, en condicional:** el difuminado desde Android 12 (`ui/theme/Glass.kt:105`, `:252`); «Colores del fondo de pantalla» desde Android 12 (`ui/theme/Theme.kt:80`; `ui/theme/Palettes.kt:128`; `ui/screens/AppearanceScreen.kt:144`); Android 8 a 11 sin difuminado (`minSdk = 26`, `app/build.gradle.kts:31`).
- **Sin trabajo en segundo plano ni permisos nuevos:** el manifiesto solo pide Internet y cámara (`AndroidManifest.xml:4`, `:6`); el calendario de copias corre al abrir (`vault/AutoBackup.kt:457`, `:505`); la vibración es `performHapticFeedback` (`ui/screens/EntryRow.kt:178`, `:208`).
- **«Pruebas de la 2.0»** cubren cada fila «Móvil» de `pendientes.md` para que la tarea 11 las convierta en flujos; las que necesitan dos móviles, TalkBack o el reloj quedan a mano.
- **Riesgo de proceso:** `p20-version-y-licencias.yaml` fallará con el APK 2.0.0 hasta la tarea 11 (fila 48).

## Textos para el Abogado Digital (revisión final)

Todos nuevos o cambiados en este paso; ninguno es de la interfaz:
- `README.md`: «Funciones» (bóveda, varias bóvedas, organizar, plantillas, papelera e historial, lista, formulario, «Salud», copias en varias carpetas, Inspector de QR, QR de wifi, Ajustes, Ayuda, Aspecto), «Formatos» (aviso de la 2.0, O-3), las líneas 320 y 329 de antes y «Pruebas de la 2.0».
- `docs/privacidad.html`: los 11 puntos de O-10, la lista de lo guardado sin cifrar (con la línea de R-C10 y la de O-C5), los selectores (O-10.7), «Permisos» y «Tus derechos». La fecha, al fusionar.
- `docs/ficha-google-play.md`: la cabecera de reglas, «Recursos gráficos» (O-C2), la descripción completa (bóveda, aspecto, privacidad y «Antes de usarla») y el cambio de «cartera de hardware» (O-11).
- `docs/PUBLICAR.md`: la fecha de la política en la 2.0, tarjetas bancarias y funciones financieras, el CRA (R-12), «Salud» y las funciones de salud, el icono y las capturas (O-C2), O-C1.
- `docs/superpowers/handoff/2026-11-15-clavero-2.0-notas.md`: las notas de GitHub y las «Novedades» de Play.
- Para su juicio: X-1 (los lugares de los selectores al borrar una bóveda) y X-2 (R-6 sin línea propia).
