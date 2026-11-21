# Clavero 2.0, tarea 12 (paso 1): revisión de «versión y documentos»

Revisor de solo lectura, en un clon propio en `e0ed4e3` (sin gradle). Encargo: «Ruling (advisor, T10 cierre y T12 paso 1)» de `progress.md`. Leídos: `CLAUDE.md`, `SKILL.md` de `experto-seguridad-cripto`, el registro, `pendientes.md`, el informe `task-12-1-report.md`, la tabla `task-12-1-claims.md`, el diff `dce664b..e0ed4e3` (sin `progress.md`), las revisiones de la rama A, B y C y las listas del Abogado. El código de `dcf28dc` y `e0ed4e3` es el mismo salvo `versionName` (`git diff dcf28dc e0ed4e3 --stat -- app`: solo `app/build.gradle.kts`). Rutas de código relativas a `app/src/main/java/app/seguridad/`.

**Veredicto: Necesita arreglos** (0 Critical, 1 Important, 9 Minor). El Important es de veracidad en los documentos y se arregla con texto; ninguno toca código.

## 1. Cada fila de la tabla frente al código

Comprobadas las 89 filas contra el código en `e0ed4e3`. Casi todas son ciertas y su `archivo:línea` es el bueno; lo que depende de Android (G-2, G-3, G-4; B-5 con StrongBox) va en condicional. Lo comprobado, por grupos:

- **V-1 a V-3:** `app/build.gradle.kts:35` (`2.0.0`), `:33-34`; `build-apk.yml:111-112`, `:315-324`; `PUBLICAR.md:86`. Ningún test lee `versionName` ni el `build.gradle.kts`; el único que lee `.maestro/` es `CsvFormatsTest.kt:70-76` (un CSV). Ciertas.
- **F-1 a F-9:** `vault/Vault.kt:192`, `:201`, `:300`; `vault/VaultStore.kt:221-226`; `vault/AutoBackup.kt:114`, `:120`, `:736-740`; `ui/screens/VaultUpgrade.kt:38-45`, `:53-58`, `:63-68`; `vault/Backup.kt:35`, `:41`, `:44`, `:51`, `:58`, `:60-62`, `:82`; `VaultLimits.kt:29`. Ciertas.
- **B-1 a B-9:** `vault/VaultLimits.kt:34`; `vault/VaultsTexts.kt:10`, `:34-37`, `:44`, `:47-49`, `:76`; `vault/VaultRegistry.kt:38-42`, `:44-51`, `:89-94`, `:122-134`, `:163-172`; `AppContainer.kt:488`, `:563`, `:677-716`; `ui/screens/VaultScreen.kt:494-497`; `core/PickerPlaces.kt:97-100`. Ciertas.
- **T-1 a T-6:** `vault/Trash.kt:9-12`, `:17-27`, `:36-46`; `VaultLimits.kt:23`, `:26`; `EntryHistory.kt:27-39`; `BackupSchedule.kt:16-27`. Ciertas como afirmaciones del código; el texto que T-6 lleva a los documentos no (I-1, abajo).
- **O-1 a O-11, L-1 a L-5, C-1 a C-5, Q-1 a Q-4, A-1 a A-5:** `VaultLimits.kt:20-22`, `:30-33`, `:38`; `VaultData.kt:90-94`; `EntryQuery.kt:60-71`; `Templates.kt:1-73`; `Vault.kt:20-47`; `ui/screens/EntryRow.kt:148-157`, `:178`, `:208`; `tools/TwoStepCopy.kt:10-13`; `vault/BackupSchedule.kt:5-14`, `:80-96`; `vault/AutoBackup.kt:290-296`, `:452-457`, `:517-541`; `settings/Settings.kt:415-416`; `tools/qr/WifiQr.kt:40-44`, `:118-124`, `:350`, `:356`, `:378-397`; `MainActivity.kt:118-119`; `AppContainer.kt:222-227`; `tools/qr/QrHandoff.kt:83-91`; `settings/Guide.kt:37-63`. Ciertas.
- **G-1 a G-8:** `ui/theme/Glass.kt:103-105`, `:250-252`, `:275-277`; `settings/Appearance.kt:18-33`; `ui/theme/Theme.kt:78-84`; `ui/theme/Palettes.kt:126-128`; `ui/screens/AppearanceScreen.kt:78-82`, `:101`, `:142-144`. Ciertas, con m-4 y m-5.
- **E-1 a E-4:** `vault/Vault.kt:277`, `:280`, `:294-297`; `crypto/Crypto.kt:87`, `:90`; `vault/VaultStore.kt:71`, `:1674`; `vault/Attachments.kt:29`; `vault/Backup.kt:79`, `:82`; `vault/BackupCheck.kt:180`. Ciertas hoy; E-1 cambiará (§6).
- **S-1 a S-4:** `AndroidManifest.xml:4`, `:6`, `:11-13`; `git diff a3ac942 e0ed4e3 -- gradle/libs.versions.toml app/src/main/AndroidManifest.xml` vacío. Ciertas; la evidencia de S-4 es pobre (m-3).
- **U-1 a U-11:** `settings/Settings.kt:42-100`, `:172-194`; `vault/BackupSchedule.kt:30-59`, `:100-118`; `vault/VaultRegistry.kt:39`, `:42`; `android/BiometricVault.kt:117-121`, `:477-480`; `android/PrefsPlaceStore.kt:13`. Las líneas son las buenas.

Hallazgos:

- **m-1 (Minor), F-5:** la evidencia (`BiometricVault.kt:118`, `:124`) solo dice que la primera bóveda conserva el alias, no que la clave siga abriendo la bóveda tras el paso a v4. Lo prueba `app/src/test/java/app/seguridad/vault/VaultV4Test.kt:382-389` (`aV4VaultOpensWithTheFingerprint`: la clave de datos de `vault-1.4.3.json` abre la v4) y lo dice el diseño (`2026-11-12-clavero-2.0-design.md:66`). **Arreglo:** añadir las dos a la evidencia.
- **m-2 (Minor), T-5 y T-7:** «los campos ocultos y las contraseñas siguen ocultos en él» no tiene evidencia (`EntryHistory.kt:31` es el recorte); está en `vault/TrashTexts.kt:75-78` (`HISTORY_MORE`) y `ui/screens/EntryHistoryScreen.kt:55`, `:138`. T-7 cita solo el Ruling: el código es `vault/VaultStore.kt:1144-1150`. **Arreglo:** poner esas líneas.
- **m-3 (Minor), S-4 y O-2:** S-4 dice «`gradle/libs.versions.toml` sin cambios de la tarea 12», que no prueba «ninguna dependencia nueva» en la 2.0. Lo prueba `git diff a3ac942 e0ed4e3 -- gradle/libs.versions.toml` (vacío). O-2 cita solo `RECENT_MAX`; el orden es `vault/VaultData.kt:90` (`SortOrder`). **Arreglo:** cambiar la evidencia.
- **m-4 (Minor), G-1:** dice «Sólido», el de la 1.x», que el propio informe corrigió («no es el aspecto de la 1.x», Ruling T5 bis); la fila sigue con la frase vieja. **Arreglo:** «Sólido» (paneles opacos sobre un fondo liso, `AppearanceScreen.kt:80`).
- **m-5 (Minor), G-2:** el código difumina **el fondo que dibuja la app** detrás del panel (`Glass.kt:275`, «The app's background as it is behind the panel»; `AppearanceScreen.kt:81`, `STYLE_MORE`: «difuminan el fondo de la app que tienen detrás»), no el contenido que pasa por debajo. El README («difuminan lo que tienen detrás»), la ficha y las notas («difuminan lo de detrás») dicen más que el código. **Arreglo:** «difuminan el fondo de la app que tienen detrás», como `STYLE_MORE`, en la fila y en los tres documentos.

## 2. Afirmaciones de los documentos sin fila

Recorridos línea a línea el diff de `README.md`, `docs/privacidad.html`, `docs/ficha-google-play.md`, `docs/PUBLICAR.md` y `2026-11-15-clavero-2.0-notas.md`. La mayoría tiene su fila. Las que no la tienen son todas ciertas en el código; les falta la fila:

- **m-6 (Minor):** sin fila, todas comprobadas:
  - «Ver en grande» enseña una contraseña con letra grande (README, notas:23): `ui/screens/VaultEntryScreen.kt:167`, `ui/screens/BigPasswordScreen.kt:52-55`. Ojo, I-2 de la parte C: en el historial no hace nada (§6).
  - el «+» pregunta qué guardar (README, notas:24): `vault/FormHelpers.kt:182` (`NEW_ENTRY_CHOOSE`).
  - «Cambiar de bóveda», arriba en la pestaña Bóveda y en la pantalla de bloqueo, y lo que deja hacer «Bóvedas» (crear, ordenar, color, nombre con la bóveda abierta) (README): `ui/AppNavHost.kt:326`, `ui/screens/VaultScreen.kt:218`, `:357`, `:439`; `vault/VaultsTexts.kt:18-32`, `:43`.
  - cada acceso sigue guardando sus 5 últimas contraseñas (README, «Historial de contraseñas»): `vault/History.kt:4-19`.
  - la pantalla del QR de wifi se cierra al bloquear la bóveda (README): `ui/screens/WifiQrScreen.kt:187-200`.
  - «Borrar todo el historial» en Ajustes (README, política): `ui/screens/SettingsScreen.kt:731`; `vault/TrashTexts.kt` (`HISTORY_ALL_*`).
  - el contenido de la guía (la maestra, la copia, la huella y las bóvedas) (README): `settings/Guide.kt:38-51`.
  - el texto «Aspecto nuevo. Para volver…» que cita «Pruebas de la 2.0»: `ui/screens/AppearanceScreen.kt:101`.
  - «Las copias con calendario se hacen al abrir la app» (ficha) y «… si hubo cambios»: `vault/BackupTargetTexts.kt:7`.

  **Arreglo:** una fila por cada una (o añadirlas a L-1, O-10, B-1, T-5, Q-1, A-2 y G-6), para que la segunda pasada las vuelva a mirar.
- No encontré afirmaciones falsas sin fila, salvo las de §3 y §6.

## 3. La política: lo guardado sin cifrar

Comparada la lista nueva (`docs/privacidad.html`, el `<ul>` tras «Fuera de las bóvedas…») con los campos `@Serializable` de `Settings` (`settings/Settings.kt:42-100`), `VaultSettings` (`:172-194`), `BackupTargetSettings` (`vault/BackupSchedule.kt:30-59`), `ReplaceDone` y `A6State` (`:100-118`), `RegistryFile` y `VaultInfo` (`vault/VaultRegistry.kt:39`, `:42`) y las preferencias (`android/BiometricVault.kt:101-121`, `:477-480`; `android/PrefsPlaceStore.kt:13`), y con la tabla de la parte B (`branch-review-copias.md:233-305`).

Lo que está: el registro (cuántas, color, orden, última abierta); `lockAfter`, `clipboard`, avisos aceptados, `simpleMode`, `guideSeen` (con lo que revela), `missionsOff`; `theme`, `style`, `palette`, `custom`, `reduceTransparency`, `highContrast`; por bóveda `kdf`, `clockHighWaterMs`, la huella (blob, IV, StrongBox); por carpeta `uri`, `keep`, `schedule`, `lastAt`, `lastError`, `copies`/`importCopies` (con el nombre de archivo), `copiedMark`; `pastTargets`; A6 (`a6.id`, `masterMark`, `replaceDone`, `said` con las direcciones). Los selectores, en su párrafo propio. Nada de la lista es falso: no promete nada que el código no haga.

- **m-7 (Minor), faltan cuatro cosas pequeñas** (ningún secreto, pero la lista se presenta como completa y la fila 8 se cierra con ella):
  - `autoBackupOffForNewVault` (`Settings.kt:183`; U-9 de la tabla), si la copia automática se apagó al crear una bóveda nueva;
  - `replaceTargets` y `replaceTold` (`BackupSchedule.kt:38-39`): los identificadores de las copias que una petición de A6 aún tiene que borrar (con su nombre de archivo) y los fallos ya dichos;
  - `appearanceNoticePending` y `renameNoticePending` (`Settings.kt:56`, `:80`): si queda por enseñar una línea de después de actualizar (U-3 la nombra; la política no);
  - de todo el móvil, en las preferencias de la huella: si StrongBox falló alguna vez y si ya se dijo (`BiometricVault.kt:109-115`, `KEY_STRONGBOX_FAILED`, `KEY_STRONGBOX_HINT_SHOWN`; de la 1.3.0).

  **Arreglo:** añadir a la lista «si la copia automática se apagó al crear otra bóveda», en el punto de A6 «las copias que quedan por borrar», en el de los ajustes «si queda por enseñar algún aviso de después de actualizar» y en el de la huella «si el chip StrongBox falló en este móvil». Corregir U-9 en la tabla (dice que va y no va) y el informe («Todo eso va en la política»).
- **X-1 (fila 46):** la frase nueva de los selectores dice lo que hace el código (`AppContainer.kt:677-716` sin `forgetAll`; `VaultScreen.kt:494-497`). Cierta. Si la ronda decide que «Borrar esta bóveda» los olvide (m-1 de la parte A), la frase cambia.
- **X-2 (fila 47):** ningún documento afirma el recordatorio de R-6. Bien.
- **I-1 (Important), la frase de la papelera en la política** (párrafo «Al borrar una entrada…»): «Todo eso se borra de la bóveda del móvil, pero las copias que ya hiciste lo siguen teniendo.» No es cierto con copia automática: cada borrado es un guardado que lanza una copia `ON_CHANGE` y su rotación quita la más antigua (`vault/AutoBackup.kt:452-457`, `:1025`; «Conservar» es 3, 5 o 10, `settings/Settings.kt:415`), así que tras `N - 1` cambios ninguna copia automática lo tiene. Es el mismo error que I-3 de la parte B (`COPIES_KEEP_IT`, fila 30) y está también en:
  - `README.md`, «Papelera, deshacer e historial»: «Las copias ya hechas conservan lo que se borra después.»;
  - las notas, `2026-11-15-clavero-2.0-notas.md:22`: «Las copias ya hechas conservan lo que borras después.»;
  - la fila T-6 lo dice bien («la rotación puede quitar la más antigua»), pero los documentos no lo llevan.

  **Arreglo** (redacción final al Abogado, con la de la parte B): «Todo eso se borra de la bóveda del móvil. Las copias que ya hiciste pueden seguir teniéndolo: las que exportaste, hasta que las borres; las automáticas, hasta que la rotación las quite.» Lo mismo, más corto, en el README y en las notas. La ficha («no lo borra de las copias que ya hiciste») es cierta tal cual: el borrado no las toca.
- **m-8 (Minor), la identidad y los contactos no van a la papelera** (`vault/VaultStore.kt:1144-1150`; T-7). La política («Al borrar una entrada, va a la papelera de su bóveda»; «Tus derechos»: «puedes borrar una entrada (va a la papelera)»), el README («todo borrado de una entrada la manda a la papelera») y las notas:22 («lo que borras pasa 30 días en ella») no dicen la excepción. **Arreglo:** en la política, «(salvo tu identidad y tus contactos, que se borran del todo)»; en el README igual; en las notas, «las entradas que borras».

## 4. Reglas: nombres de otras apps, palabras prohibidas, O-C2, O-C4, O-C6, O-3, datos del dueño

Buscadas en las 133 líneas añadidas de los cinco documentos las palabras de las reglas de la ficha, de `PublishedTextsTest`, `FinalLegalReviewTest` y `SourceTextsTest`, y las de O-C2, O-C4 y O-C6 («Liquid Glass», «vidrio líquido», «accesible», «WCAG», «garantizado», «único», «registrado», ®, ™), más «cartera», «monedero», «wallet», «fuerte», «irrecuperable», «borrado seguro» y nombres de apps de contraseñas o de 2FA.

- **Nombres de otras apps:** ninguno nuevo en la ficha, sus «Novedades» ni las notas. Las notas no los necesitan (no cambia qué se importa) y lo dicen (`notas.md:3`). Bien.
- **Palabras prohibidas:** solo aparecen dentro de las listas de reglas (`ficha-google-play.md:3`, `:7`; `PUBLICAR.md:98`, `:124`) y en frases de antes («No garantiza que un enlace sea seguro», README; «Limpiar foto», el nombre de la herramienta). Ninguna afirmación nueva las usa. «cartera de hardware» pasa a «dispositivo hardware» (O-11). Bien.
- **O-C2:** «Icono nuevo», sin «único», «original», «registrado», ®, ™ (README; notas:28; ficha «Recursos gráficos»). **O-C4:** «Cristal» solo como nombre del estilo, con el difuminado «desde Android 12», sin el nombre de ningún sistema. **O-C6:** «la app ajusta los tonos para que el texto tenga contraste suficiente», sin «accesible», «WCAG» ni «garantizado». **O-C5:** «usa los colores que Android calcula de él; no lee la imagen ni pide permisos», en el README y la política. Bien.
- **O-3:** el aviso en el README («Formatos»), la ficha («Antes de usarla») y las dos notas, con la línea «Importante» primero. Bien.
- **O-1:** ningún texto presenta varias bóvedas como forma de ocultar una. Bien.
- **Datos del dueño:** ninguno en el diff (solo su seudónimo y el correo de contacto del proyecto, que ya estaba). Bien.
- **m-9 (Minor, para la parte D), nombres de modelos en lo que entra en el diff:** los documentos publicados no nombran ninguno, pero sí los de traspaso y registro que entran en este diff: `docs/superpowers/handoff/2026-11-16-clavero-2.0-parte.md:7`, `:13`, `:15`; `sdd-2.0/advisor-t12-branch-review.md:33`, `:143`; `sdd-2.0/progress.md` (las líneas nuevas `:216`, `:219` y muchas de antes). El Ruling «T12 revisión de la rama y APK» ya lo manda a la parte D («se cambian por «el director»»); se apunta aquí para que incluya también el parte y el texto del director, no solo el registro.

## 5. Versión y filas de `pendientes.md`

- **`versionName`:** `2.0.0` en `app/build.gradle.kts:35`; `versionCode` sale de `-PversionCode` (`:33-34`) con el número de ejecución (`build-apk.yml:111-112`, `:315-324`), sin cambiar el nombre del workflow. Ningún test fija la versión. El User-Agent toma `BuildConfig.VERSION_NAME`. Bien.
- **`.maestro/p20-version-y-licencias.yaml:1`, `:21`** siguen con «Clavero 1.4.3»: correcto por el punto 3 del Ruling (ningún test lo ata); fila 48 y la última prueba de «Pruebas de la 2.0» lo dicen.
- **Filas 6, 7, 22, 36 y 40:** cerradas con lo que piden (R-C10 y O-C5 en la política; O-3 en README, ficha y notas; el QR pendiente en notas:26 y README; los errores en notas:29; README `:334` y `:343` con «… Elige otra copia.», iguales a `vault/BackupCheck.kt:180` y `vault/Backup.kt:79`). Bien.
- **Fila 8:** cerrada como «Hecho», pero la lista tiene los huecos de m-7. Cerrarla cuando se arregle m-7.
- **Filas 46 a 50:** sensatas y con destino. Dos notas: la 46 depende de la decisión de la parte A (m-1); la 50 es mejor resolverla moviendo el bloque de «Novedades» al traspaso de la 2.0 cuando se escriba (así el test que ya existe lo lee), y no tocando el test.
- **Nota del informe:** dice «Ninguna otra afirmación de la tabla choca con el código»; con I-1 y m-5 no es así del todo (la tabla está bien; el texto que llevan los documentos, no).

## 6. Lo que la ronda de arreglos A–C hará falso (para la segunda pasada)

- **E-1 y la línea de errores de las notas (`notas.md:29`), filas 36 y 38:** la parte A, I-3, cambia `KDF_OUT_OF_MEMORY` («… para el coste «Alto» de Argon2id», `crypto/Crypto.kt:90`) para que no diga «Alto» con «Estándar», y propone `KDF_NO_MEMORY_NOW`. E-1 cita el texto exacto: hay que volver a copiarlo de la constante. La parte B, fila 42, cambia el texto por defecto de las copias y `KDF_PARAMS_INVALID` en «Importar» y «Comprobar» (`copyFailureText`).
- **E-3 «adjunto de más de 5 MB» y fila 37:** la parte B, C-1 e I-4, añade el texto «… pasarían de 50 MB … vacía la papelera» al añadir, y `COPY_DAMAGED` para un adjunto fuera de 0..5 MB en una copia. Si las notas o el README nombran esos errores, al día.
- **Fila 39:** `BACKUP_DOES_NOT_FIT` ganará una línea en la exportación y en «Exportar una copia antes»; F-4 y el README («Formatos») no la citan, pero la segunda pasada debe mirarlo.
- **O-4 y Q-2, fila 26 (parte C, I-3):** la etiqueta «WPA/WPA2/WPA3» (`vault/Templates.kt:29`) cambia. La fila O-4 la cita; el README y las notas no, así que solo cambia la tabla.
- **«Ver en grande» (parte C, I-2):** si se arregla quitándolo del historial, el README y las notas:23 siguen siendo ciertos (es de la ficha); si se arregla haciéndolo funcionar, también.
- **A6 y la copia de antes de la 2.0 (parte B, I-2):** los textos de la app pasarán a nombrar «la de antes de la 2.0». La política y el README ya lo dicen; comprobar que usan las mismas palabras que el botón nuevo (`ToolTexts.kt:508`).
- **La papelera en las copias (parte B, I-3, fila 30):** el texto de la app cambiará a «pueden conservarlo…»; I-1 de esta revisión debe usar la misma redacción.
- **Cambiar de bóveda durante el cambio de maestra (parte A, I-1)** y **la fila 45 (parte A, I-2):** si añaden un aviso nuevo, va a «Errores que dicen qué hacer» de las notas y a la tabla.
- **Borrar una bóveda deja sus ajustes con un fallo (parte B, m-2):** el README, la política y las notas dicen que borra «sus ajustes»; cierto cuando se aplique m-2.
- **«Al borrar la bóveda» y los selectores (parte A, m-1):** si se decide que «Borrar esta bóveda» los olvide, cambia la frase nueva de la política y B-9.

## Resumen de arreglos

| # | Severidad | Dónde | Arreglo |
|---|---|---|---|
| I-1 | Important | `docs/privacidad.html` (párrafo de la papelera); `README.md` («Papelera, deshacer e historial»); `notas.md:22` | «Las copias … pueden seguir teniéndolo», con la rotación; misma redacción que la fila 30 |
| m-1 | Minor | tabla, F-5 | evidencia `VaultV4Test.kt:382-389` y diseño `:66` |
| m-2 | Minor | tabla, T-5 y T-7 | `TrashTexts.kt:75-78`, `EntryHistoryScreen.kt:55`; `VaultStore.kt:1144-1150` |
| m-3 | Minor | tabla, S-4 y O-2 | `git diff a3ac942 e0ed4e3 -- gradle/libs.versions.toml`; `VaultData.kt:90` |
| m-4 | Minor | tabla, G-1 | «Sólido» sin «el de la 1.x» |
| m-5 | Minor | tabla G-2; README «Aspecto»; ficha «Aspecto»; `notas.md:28` | «difuminan el fondo de la app que tienen detrás» (`STYLE_MORE`) |
| m-6 | Minor | tabla | filas para las nueve afirmaciones de §2 |
| m-7 | Minor | política (lista sin cifrar); tabla U-9; informe | las cuatro cosas que faltan; fila 8 se cierra después |
| m-8 | Minor | política, README, `notas.md:22` | la identidad y los contactos no van a la papelera |
| m-9 | Minor | parte D | nombres de modelos en el parte y en el texto del director, no solo en el registro |

Lo de §6 no es un hallazgo de este paso: es la lista para la segunda pasada, después de la ronda A–C.

## Revisión experto-seguridad-cripto

- **La política no esconde nada sin cifrar que importe:** la lista sale de los campos serializados (`settings/Settings.kt:42-100`, `:172-194`; `vault/BackupSchedule.kt:30-59`, `:100-118`; `vault/VaultRegistry.kt:39`, `:42`); lo que falta (m-7) son marcas sin datos del usuario, salvo `replaceTargets`, que lleva nombres de archivo con fecha y hora (como `copies`, que ya está). Ningún campo guarda un nombre de bóveda, de entrada o un secreto (`Settings.kt:36-39`).
- **Las huellas descritas como opacas lo son:** `copiedMark` es SHA-256 de `mtime`, coste y `vaultTag`, y `vaultTag` va cifrado dentro de la bóveda (`VaultData.kt:31`); `masterMark` es SHA-256 de la sal, que ya está en claro en `vault.bin`. Correcto decir que no se lee nada de ellas.
- **Nada promete borrar de más:** ningún texto dice «irrecuperable» ni «borrado seguro»; el error es el contrario (I-1: promete que las copias conservan, cuando la rotación las quita). La ficha dice bien que borrar no toca las copias.
- **Huella tras el paso a v4:** la clave de datos no cambia (diseño `:66`; `VaultV4Test.kt:382-389`), así que «la clave de la huella sigue valiendo» es cierto.
- **Formato:** marca 3 de `seguridad-backup` (`vault/Backup.kt:41`, `:44`) y el texto de la 1.4 (`:80-82`); la copia de antes de la 2.0 es el archivo v2/v3 con marca 2 (`vault/AutoBackup.kt:736-740`). Los documentos lo dicen bien.
- **Una sola conexión:** sigue siendo Pwned Passwords al pulsar; ninguna misión la lanza (`health/Missions.kt:50`). Sin dependencias ni permisos nuevos frente a `v1.4.3` (`git diff a3ac942 e0ed4e3 -- gradle/libs.versions.toml app/src/main/AndroidManifest.xml` vacío).
- **Portapapeles:** «no lee el portapapeles» es cierto (`vault/FormHelpers.kt:18`, `:58-62`).

## Revisión Ingeniero Móvil Senior

- **Versión:** `app/build.gradle.kts:35` (`2.0.0`), `versionCode` por `-PversionCode` (`:33-34`, `build-apk.yml:111-112`, `:315-324`). `.maestro/p20-version-y-licencias.yaml:1`, `:21` fallará con el APK 2.0.0 hasta la tarea 11 (fila 48), y el README lo avisa.
- **Lo que depende de Android, en condicional:** difuminado solo con `SDK_INT >= S` (`ui/theme/Glass.kt:105`, `:252`, `:276`); «Colores del fondo de pantalla» solo desde Android 12 (`ui/theme/Theme.kt:80`; `ui/theme/Palettes.kt:128`; `ui/screens/AppearanceScreen.kt:144`); StrongBox desde Android 9 (`android/BiometricVault.kt:104-106`). Los documentos lo dicen así; m-5 corrige qué se difumina.
- **Sin segundo plano:** las copias con calendario corren al desbloquear y al abrir la app (`vault/AutoBackup.kt:452-457`, `:517-541`), sin `WorkManager` ni servicio; el manifiesto solo pide Internet y cámara (`AndroidManifest.xml:4`, `:6`); `allowBackup="false"` (`:11`).
- **Vibración sin permiso:** `HapticFeedbackType.LongPress` (`ui/screens/EntryRow.kt:178`, `:208`).
- **Ciclo de vida:** el QR pendiente se borra al salir (`MainActivity.kt:118-119`, `tools/qr/QrHandoff.kt:85-91`), y la pantalla del QR de wifi se cierra al bloquear (`ui/screens/WifiQrScreen.kt:187-200`): los documentos lo dicen bien.
- **«Pruebas de la 2.0»:** cubren las filas «Móvil» de `pendientes.md`; añadir una para m-8 (borrar un contacto no va a la papelera) y otra para I-1 (con «Conservar 3», tras tres cambios la copia más antigua con lo borrado desaparece), útiles para la tarea 11.

## Textos para el Abogado Digital (revisión final)

- I-1: la frase de la papelera y las copias en la política, el README y las notas, con la redacción de la fila 30 (`COPIES_KEEP_IT`).
- m-5: «difuminan el fondo de la app que tienen detrás» en el README, la ficha y las notas (O-C4).
- m-7: las cuatro líneas nuevas de la lista de lo guardado sin cifrar en la política.
- m-8: «(salvo tu identidad y tus contactos, que se borran del todo)» en la política y el README; «las entradas que borras» en las notas.
- Los textos de §6 que cambie la ronda A–C (errores, A6, papelera, WPA), en la segunda pasada.
