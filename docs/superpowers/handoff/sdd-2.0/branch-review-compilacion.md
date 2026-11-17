# Clavero 2.0, revisión de la rama, parte D: compilación y documentos

**Revisor:** parte D (solo lectura). **HEAD congelado:** `6d3b884` (rama `ccr-0aa27f95-u038cd`). **Rango:** `a3ac942..6d3b884`. Clon propio en el *scratchpad*; ninguna edición, commit ni *push* en el repositorio. El APK de *release* que compilé para comprobar (clave de prueba pública) no se entrega a nadie y se queda en el *scratchpad*.

**Veredicto: Needs fixes (solo Minor).** Ningún Critical ni Important. 0 Critical · 0 Important · 7 Minor · 6 Info. Las ocho comprobaciones del encargo pasan en lo que es código y compilación; lo que queda son documentos, una fila de `pendientes.md` (50) y un hueco de prueba.

## Alcance

- `git diff --stat a3ac942..6d3b884` sobre el alcance de D: cambian `app/build.gradle.kts` (solo `versionName`, `:35`), `app/src/main/res/` (icono: `drawable/ic_launcher_{background,foreground,monochrome}.xml`; `values/colors.xml:3`; `values/themes.xml:3`, comentario), `.maestro/README.md` y `.maestro/borra-datos/p05-ayuda-codigos-qr.yaml`, `.claude/skills/advisor/SKILL.md` (nuevo), `CLAUDE.md`, `README.md`, `docs/privacidad.html`, `docs/ficha-google-play.md`, `docs/PUBLICAR.md`, `docs/play/icono-512.png` (nuevo) y los documentos de `docs/superpowers/`.
- **Sin cambios** (diff vacío): `AndroidManifest.xml`, `build.gradle.kts`, `settings.gradle.kts`, `gradle/` (con `libs.versions.toml` y `verification-metadata.xml`), `jvm-check/` (con su `verification-metadata.xml`), `.github/workflows/`, `scripts/`, `app/proguard-rules.pro`, `config/`, `app/src/main/assets`.
- Desde `a9efdcd` (base de la tabla de afirmaciones) hasta `6d3b884`, `git diff -- app jvm-check gradle .github scripts .maestro` está vacío: solo cambia `docs/` (`6d3b884`, segunda pasada).
- Leídos enteros en `6d3b884`: `app/build.gradle.kts` (hasta `checkReleaseKey`), `app/proguard-rules.pro`, `jvm-check/build.gradle.kts`, `scripts/check-manifest.sh`, `.github/workflows/build-apk.yml` (trabajos `build`, `dependency-metadata`, `jvm-check`, `play-bundle`), `.claude/skills/advisor/SKILL.md`, `sdd-2.0/pendientes.md`, `sdd-2.0/task-12-2-report.md`, el borrador de notas `2026-11-15-clavero-2.0-notas.md`, los partes del 15-11 (§1) y del 16-11, los diffs de la política, la ficha, `PUBLICAR.md`, `CLAUDE.md`, `.maestro/`; la tabla `task-12-1-claims.md` (todas sus referencias comprobadas por script y una muestra de filas contra el código, abajo).

## Compilaciones (en el clon, una a una, `--no-daemon --max-workers=1`, `./gradlew --stop` entre ellas)

| Ejecución | Resultado |
|---|---|
| `:app:assembleRelease -PfirmaDePrueba=true` en `6d3b884` | BUILD SUCCESSFUL (3 min 25 s). 4 avisos `w:` de Kotlin (Info-1) |
| La misma en `a3ac942` (otro *worktree*, ya borrado), para comparar el manifiesto | BUILD SUCCESSFUL |
| `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:lintRelease :app:checkLicense :app:checkFragmentVersion :app:checkAssets` (la de `CLAUDE.md` más el `lintRelease` del trabajo **build** de la CI, `build-apk.yml:81`) | BUILD SUCCESSFUL (7 min 49 s); 2412 tests, 0 fallos, 0 errores, 0 omitidos (contados en los XML) |
| `./gradlew -p jvm-check test` (el *wrapper*, R29) | BUILD SUCCESSFUL (2 min 42 s); 2361 tests, 0 fallos, 0 errores, 0 omitidos |

### Salida de las comprobaciones del APK (`6d3b884`, clave de prueba, minificado)

```
$ bash scripts/check-manifest.sh --release app/build/outputs/apk/release/app-release.apk
check-manifest: OK (app/build/outputs/apk/release/app-release.apk)

$ aapt2 dump permissions app-release.apk
package: io.github.egt17baa.seguridad
uses-permission: name='android.permission.INTERNET'
uses-permission: name='android.permission.CAMERA'
uses-permission: name='android.permission.USE_BIOMETRIC'
uses-permission: name='android.permission.USE_FINGERPRINT'
permission: io.github.egt17baa.seguridad.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION
uses-permission: name='io.github.egt17baa.seguridad.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION'

$ aapt2 dump badging app-release.apk | head -1
package: name='io.github.egt17baa.seguridad' versionCode='1' versionName='2.0.0' ... compileSdkVersion='36'
minSdkVersion:'26'  targetSdkVersion:'36'

$ diff <(aapt2 dump permissions 1.4.3.apk) <(aapt2 dump permissions 2.0.apk)    → vacío (exit 0)
$ diff (aapt2 dump xmltree AndroidManifest.xml, sin números de línea ni ids de recurso) 1.4.3 frente a 2.0:
<     A: android:versionName(0x0101021c)="1.4.3"
>     A: android:versionName(0x0101021c)="2.0.0"
```

Manifiesto fusionado igual al de la 1.4.3 salvo `versionName`: `allowBackup=false`, `fullBackupContent` y `dataExtractionRules`; `MainActivity` exportada con `configChanges=0x00000d80` (el de F-5); la otra actividad, no exportada; `FileProvider` no exportado con `grantUriPermissions=true`; `ProfileInstallReceiver` exportado con `android.permission.DUMP`; ningún `service` ni `activity-alias`. Tamaño: 3 318 666 bytes (1.4.3: 3 021 334).

## Las ocho comprobaciones

### 1. Nada nuevo en el APK — **pasa**
Permisos, componentes exportados y `configChanges` idénticos (salida de arriba). Dependencias y sumas: `git diff a3ac942..6d3b884 -- gradle jvm-check/gradle` vacío, así que `libs.versions.toml`, `gradle/verification-metadata.xml` y `jvm-check/gradle/verification-metadata.xml` no cambian; `:app:checkLicense` en verde. `app/build.gradle.kts` solo cambia `:35`. El trabajo **dependency-metadata** del PR (`build-apk.yml:175-227`) dará 0 líneas en las dos tablas.

### 2. R8 — **pasa en el JVM; lo de tiempo de ejecución, al móvil**
- `app/proguard-rules.pro:4-11` es genérico (`app.seguridad.**`): cubre las clases `@Serializable` nuevas sin reglas por clase.
- Clases `@Serializable` en `6d3b884` (34; 22 nuevas o con la anotación nueva frente a `a3ac942`). En `mapping.txt` del APK de *release*: las 21 clases de datos (`A6State`, `BackupTargetSettings`, `CustomField`, `CustomPalette`, `EntryFilter`, `EntryVersion`, `Folder`, `LegacyVaultSettings`, `RegistryFile`, `ReplaceDone`, `SavedView`, `VaultData`, `VaultInfo`, `VaultSettings`, `Settings`, `VaultEntry`, `VaultFile`, `VaultKdf`, `AttachmentRef`, `PasswordChange`, `TotpSecret`) conservan su nombre, su `$$serializer` y su `Companion`; los 13 enum (`BackupSchedule`, `FieldKind`, `NotesFormat`, `SortOrder`, `TagColor`, `PaletteId`, `ThemeStyle`, `GlassLevel`, `ThemeMode`, `LockAfter`, `ClipboardClear`, `KdfProfile`, `EntryType`) se renombran, conservan su `Companion`, y los nombres de sus constantes (los que escribe el JSON: `ON_CHANGE`, `WALLPAPER`, `GRAPHITE`, `LIMITED`…) están en las cadenas del `classes.dex` (41 de 41 nombres de los enum nuevos). Ninguno usa `@SerialName` ni polimorfismo (`core/Json.kt:6-10`).
- `settings/SettingsStore.kt:251`, `:255` leen los nombres de los campos por `serializer().descriptor`, que salen de cadenas del `$$serializer`: no dependen de R8.
- **Móvil (Info-5):** ningún test usa el APK minificado (P-19). La primera prueba de la 2.0 en el móvil con el APK de la CI (minificado) ejerce el JSON v4 y `settings.json` de verdad: abrir una bóveda de la 1.4.3, pasarla a v4, cambiar aspecto, paleta y una carpeta con calendario, cerrar la app del todo y volver: todo sigue. El README («Pruebas de la 2.0», `README.md:397`) pide el APK firmado, que es minificado: lo cubre si se hace esa secuencia.

### 3. jvm-check — **pasa** (salvo la fila 50)
- `androidOnly` (`jvm-check/build.gradle.kts:7-12`) y la exclusión de tests `**/ui/**` (`:22`) dejan fuera 13 tests nuevos de `app/src/test/java/app/seguridad/ui/` (`TagPaletteThemeTest`, `BigPasswordTest`, `EntryFieldsTest`, `EntryRowTest`, `HealthScreenTest`, `MarkupTextTest`, `VaultPickerDialogsTest`, `VaultUpgradeTextsTest`, `WifiQrDrawingTest`, `WifiQrReferenceTest`, `GlassContrastTest`, `PalettesTest`, `ThemeSurfaces`). Es lo previsto: corren en `:app:testDebugUnitTest`, que el trabajo **build** de la CI ejecuta (`build-apk.yml:81`) y aquí pasan. Ningún test nuevo fuera de `ui/` queda fuera.
- La tarea que lee los traspasos (`jvm-check/build.gradle.kts:63`, `*-traspaso.md`) y su lector (`ImportTextsTest.kt:150`) pasan con el traspaso de la 2.0 (`2026-11-12-clavero-2.0-traspaso.md`, sin bloque «Novedades» todavía). El borrador de notas (`2026-11-15-clavero-2.0-notas.md`) **no** lo lee nadie: fila 50, Minor-1.

### 4. Versión — **pasa, con un hueco de prueba**
- `app/build.gradle.kts:35` `versionName = "2.0.0"`; el APK dice `versionName='2.0.0'`. `versionCode` 1 en local (`:34`), número de ejecución en la CI (`build-apk.yml:112`, `:315`, `:324`).
- `AppNameTest.theIdentityTheFormatsAndTheClipboardLabelDoNotChange` (`app/src/test/java/app/seguridad/tools/AppNameTest.kt:118-148`): `applicationId`, `namespace`, autoridad del `FileProvider`, alias y preferencias del Keystore, `CLIPBOARD_OWN_LABEL`, `seguridad-vault`, `seguridad-backup`, `seguridad-auto-`, `seguridad-antes-de-importar-`, `seguridad-boveda-`, `SG1:`, `.sgenc`, `.sgsig`, `SGK1:`, `SGM1:`, `SGS1:`; añade `seguridad-antes-de-2-0-` (`:136-137`, `vault/AutoBackup.kt:114`). Ningún nombre ni prefijo de antes cambia.
- **Ningún test fija el `versionName`** (V-3 de la tabla lo dice). La puerta del APK del director pide «`versionName` 2.0 y sus tests» (`advisor-t12-branch-review.md`, «Orden con el APK», 8): Minor-4.
- `.maestro/p20-version-y-licencias.yaml:1`, `:21` esperan «Clavero 1.4.3»: fila 48, tarea 11 (Ruling «T10 cierre y T12 paso 1»). Correcto como destino.

### 5. Documentos frente al código — **pasa, con un Minor**
- **Tabla de afirmaciones** (`task-12-1-claims.md`): 179 referencias `archivo:línea`; todas existen y caen dentro de su archivo en `6d3b884` (script; el código no cambió desde `a9efdcd`). Muestra comprobada línea a línea: F-2 (`vault/Vault.kt:308`), E-1/E-2 (`crypto/Crypto.kt:87`, `:94-96`, `:111`, `:114`), E-4 (`vault/BackupCheck.kt:182`, `vault/Backup.kt:79`), K-1 (`tools/ToolTexts.kt:508`), K-2 (`vault/BackupCheckTexts.kt:105-108`), K-3 (`AppContainer.kt:991-995`), K-4 (`vault/VaultsTexts.kt:122-125`), C-1 (`vault/VaultLimits.kt:46`), M-4 (`vault/History.kt:9`), M-9 (`vault/BackupTargetTexts.kt:7`), U-9 (`settings/Settings.kt:183`), S-1 (manifiesto y `check-manifest.sh`, arriba). Todas ciertas.
- **Lo guardado sin cifrar en la política** (`docs/privacidad.html`, párrafo «Fuera de las bóvedas…» y su lista): cubre cada campo serializado de `settings/Settings.kt:37-53` (`lockAfter`, `clipboard`, `theme`, `noticeAccepted`, `clipboardHistoryNoticeAccepted`, `renameNoticePending`, `perVault`, `style`, `palette`, `custom`, `reduceTransparency`, `highContrast`, `appearanceNoticePending`, `simpleMode`, `guideSeen`, `missionsOff`), de `VaultSettings` (`:172-198`: `kdf`, `clockHighWaterMs`, `targets`, `autoBackupOffForNewVault`, `pastTargets`, `a6`), de `vault/VaultRegistry.kt:39`, `:42` (`id`, `color`, `order`, `lastOpened`), las preferencias de `android/BiometricVault.kt:118`, `:121` y las de `android/PrefsPlaceStore.kt:13`. Las postergaciones de misiones solo viven en memoria (`health/Missions.kt:91-97`). Coincide con la lista de la parte B.
- **Textos citados en el README frente a sus constantes:** script que busca cada «…» de más de 25 caracteres del README en `app/src/main/java` (uniendo las concatenaciones). Los textos de error de la 2.0 coinciden (`BACKUP_DAMAGED`, `COPY_DAMAGED`, `UPGRADE_NEEDS_MASTER`, «La copia es de una versión más nueva de Clavero»…). No coinciden 11, todos de antes de la 2.0 (de la 1.2 a la 1.4.3: textos de Gradle, de Android o con `$n`, que sí existen con su plantilla), salvo dos que sí importan: Minor-2.
- **Filas 6, 7, 8, 22, 36 y 40:** cerradas de verdad. 6 y 8: la lista de la política (arriba). 7: `README.md:80`, la ficha («Antes de usarla», «Al actualizar…») y las dos notas. 22: la línea «QR de wifi» de las notas y `README.md:414`. 36: «Errores que dicen qué hacer» y «Los avisos del arranque salen de uno en uno» en las notas, con las constantes de la ronda (E-1 a E-3, K-2, K-4, K-7). 40: `README.md` cita «… Elige otra copia.» como `vault/BackupCheck.kt:182` y `vault/Backup.kt:79`.

### 6. Reglas de lo publicado — **pasa, con dos Minor y una recomendación**
- **Nombres de otras apps:** ninguno en la ficha (solo «Google» en el título «Ficha de Google Play», `docs/ficha-google-play.md:1`, de antes), la política, ni en los dos bloques de notas; `PUBLICAR.md:101` nombra «Google Authenticator» desde antes de la 2.0 (no es lo publicado en la tienda). El README sigue con su línea de marcas.
- **Palabras prohibidas:** puerto a Python de `ForbiddenWords` (`app/src/test/java/app/seguridad/tools/ForbiddenWords.kt`) sobre las líneas añadidas del README, la ficha y `PUBLICAR.md` y sobre las notas: solo saltan las líneas que **enumeran** las palabras prohibidas (la regla de la ficha, `docs/ficha-google-play.md:3`, y la guía de las notas, `2026-11-15-clavero-2.0-notas.md:7`). Los dos bloques que se publican están limpios.
- **Privacidad del dueño:** en las líneas añadidas del rango, los únicos correos son `Egt17baa@users.noreply.github.com`, `noreply@anthropic.com` y direcciones de ejemplo inventadas (`ana@example.com`…); las únicas rutas son `/home/user` (el contenedor de la nube) y el *scratchpad* `/tmp/claude-0/…`; ningún modelo de móvil ni de ordenador. `.claude/skills/advisor/SKILL.md` no tiene datos del dueño.
- **Identificadores de modelo** (la regla: «Nunca un identificador de modelo en lo que se sube», `2026-11-15-clavero-2.0-parte.md:20`):
  - identificadores técnicos (`claude-…`): **ninguno** en el árbol de `6d3b884`;
  - nombres de modelo que quedan: `.claude/skills/advisor/SKILL.md:3` y `:16` (la skill los necesita: son los valores de `model` que pasa al subagente); `docs/superpowers/handoff/sdd-2.0/task-12-2-report.md:62-66` (el propio informe de la limpieza los cita, y `:68` dice que en `docs/` ya no queda ninguno: Minor-3); los pies `Co-Authored-By` de 282 de los 283 commits del rango y el asunto de un commit (que nombra el modelo del director), en el historial, no en archivos (Info-3);
  - **recomendación:** (a) en `task-12-2-report.md:63-66`, cambiar los nombres citados por «los nombres de los modelos» (sin listar cuáles); (b) la skill `advisor` se queda como está: es configuración que solo funciona con esos nombres, no un documento, y la regla habla de identificadores; si el dueño quiere cero nombres en lo subido, la alternativa es sacar la skill del repositorio a su configuración de usuario (`~/.claude/skills/`), decisión suya; (c) el historial no se reescribe (nunca *force push*, parte §1); los pies son la atribución que la propia regla manda poner.

### 7. `pendientes.md` — **pasa, con un Minor**
- Ninguna fila sin destino.
- Todo «Hecho» con su commit, salvo la **fila 43** («Hecho en la ronda 2 de la fase C», sin hash; es `ffceacc`): Minor-5. Los demás commits citados existen en la rama y su asunto corresponde (`7e868de`, `b67d35b`, `e91316d`, `c0fe71f`, `95c8667`, `035752d`, `4f0d71b`, `2093c50`, `a5d333f`, `9e97517`, `e4f6aa0`, `af15de2`, `2a03ae6`, `c2da53c`, `848f118`, `3024ba3`, `cda4590`, `79da15f`, `4d5917a`).
- «Abierto» que no son del móvil ni del dueño: 4, 5, 14, 19 (Abogado final), 41 (Abogado y dueño), 24 y 52 (ataque), 49 (controlador, antes del PR), 50 (esta parte) y 3 (la mitad «revisión final»). Todas tienen un paso posterior del orden del director (5 a 7) y no son de esta parte salvo la 50; no bloquean D, pero sí la puerta del APK (punto 8) hasta que se cierren (Info-4).

### 8. El flujo de trabajo — **pasa**
- **build:** sus pasos (`build-apk.yml:81`, `:112`, `:122`) son los que corrí: `:app:testDebugUnitTest :app:lintRelease :app:checkLicense :app:checkFragmentVersion :app:checkAssets` en verde, `assembleRelease` con `-PfirmaDePrueba=true` en verde y `check-manifest.sh --release` OK. **jvm-check:** `-p jvm-check test` en verde, 2361 tests (el trabajo de la CI añade solo la *toolchain* 21 de `JAVA_HOME_21_X64`, `build-apk.yml:248`).
- **versionCode:** la última ejecución de *Build APK* es la n.º 124 (la de la etiqueta `v1.4.3`, `a3ac942`, 12-11-2026; la 123, el *push* a `main`), así que el APK de la 1.4.3 publicado tiene `versionCode` 124 como mucho. El contador es por archivo de workflow (`build-apk.yml:28`, que no cambia): la ejecución del PR será la 125 o más, y la firmada de `main` después, mayor aún. Actualiza la 1.4.3 instalada.

## Issues

### Critical
Ninguno.

### Important
Ninguno.

### Minor

- **Minor-1 (fila 50, asignada a D): el borrador de «Novedades» de la 2.0 no lo comprueba ningún test.** `ImportTextsTest.kt:150` solo lista `*-traspaso.md`, y el bloque está en `2026-11-15-clavero-2.0-notas.md` («## Google Play, «Novedades»»). Hoy no nombra ninguna app (comprobado a mano, y con `ForbiddenWords`), pero nada lo protege si cambia antes de publicar. Cruza tareas: la regla de R2/R7/R8 (1.4.0) y la decisión del paso 1 de dejar el borrador fuera del traspaso. **Arreglo** (se arregla): en `ImportTextsTest.kt:150` aceptar también `-notas.md` (`f.name.endsWith("-traspaso.md") || f.name.endsWith("-notas.md")`), y en las entradas de la tarea `test` de `jvm-check/build.gradle.kts:63` y de `app/build.gradle.kts:242` `include("*-traspaso.md", "*-notas.md")`, para que un cambio en las notas vuelva a correr el test. Test: el mismo `thePlayListingAndItsNovedadesNameNoOtherProduct`, más una aserción de que `parts` contiene `2026-11-15-clavero-2.0-notas.md`. La alternativa (copiar el bloque al traspaso de la 2.0) depende de que alguien se acuerde; prefiero la del test.
- **Minor-2: dos pruebas pendientes del README citan el botón de A6 de antes.** `README.md:283` (1.3.0) y `README.md:364` (1.4.0, P-11) dicen pulsar «Hacer una copia nueva y borrar las anteriores (automáticas y de antes de importar)»; en la 2.0 el botón es «… (automáticas, de antes de importar y de antes de la 2.0)» (`tools/ToolTexts.kt:508`). Son casillas sin marcar que el dueño hará con la 2.0 (y la tarea 11 puede convertirlas en flujos que busquen el texto). **Arreglo:** el texto de la 2.0 en las dos líneas, o «Hacer una copia nueva y borrar las anteriores (…)», y en P-11 añadir «y las de antes de la 2.0». Comprobación: el script de citas de este informe (o `grep -n "automáticas y de antes de importar)" README.md` vacío).
- **Minor-3: el informe de la segunda pasada nombra los modelos que dice haber quitado.** `docs/superpowers/handoff/sdd-2.0/task-12-2-report.md:63-66` cita «Claude Opus 5.5», «Fable», «Fable 5.2», «opus», «sonnet»; `:68` afirma que en `docs/` solo quedan rutas del *scratchpad*. **Arreglo:** «los nombres de los modelos de los pies de commit, del director y de los agentes» sin listarlos; comprobación: `grep -rniE "\b(fable|opus|sonnet|haiku)\b" docs/` vacío (salvo las rutas `/tmp/claude-0/`).
- **Minor-4: ningún test fija `versionName` 2.0.0**, y la puerta del APK pide «`versionName` 2.0 y sus tests». No ha habido uno en ninguna versión (V-3). **Arreglo** (uno de dos, lo decide el controlador): (a) en `AppNameTest.theIdentityTheFormatsAndTheClipboardLabelDoNotChange`, `assertTrue("versionName = \"2.0.0\"" in gradle)` (con `app/build.gradle.kts` ya entre las entradas de jvm-check, `jvm-check/build.gradle.kts:61`); o (b) cambiar la condición de la puerta a «`versionName` 2.0.0 (`app/build.gradle.kts:35`) y el `aapt2 dump badging` de D». Prefiero (a): es una línea y evita una release con el número viejo.
- **Minor-5: fila 43 de `pendientes.md` sin commit.** «Hecho en la ronda 2 de la fase C» → añadir `ffceacc` («n-2 … y n-5 (Contactos dice lo mismo que la puerta)»).
- **Minor-6: `CLAUDE.md:35` no lista el prefijo nuevo.** La lista de lo que no cambia y comprueba `AppNameTest` dice `seguridad-auto-`, `seguridad-boveda-` y `seguridad-antes-de-importar-`; desde la 2.0, `AppNameTest.kt:136-137` fija también `seguridad-antes-de-2-0-`. **Arreglo:** «y, desde la 2.0, `seguridad-antes-de-2-0-`». Puede ir con la puesta al día de `CLAUDE.md` del punto 7 del orden.
- **Minor-7: `README.md:78`, en «Formatos», dice en presente «La 1.4.0 no cambia ningún formato de Clavero ni añade uno nuevo.»** justo encima del aviso de la 2.0 (`:80`), que sí cambia el formato. Leído seguido, parece que Clavero no cambia formatos. **Arreglo:** «La 1.4.0 no cambió ningún formato…» o moverla debajo del aviso como nota histórica.

### Info

- **Info-1:** avisos del compilador en `assembleRelease`: `ui/theme/Glass.kt:250:52`, `:252:118`, `:252:124` («Unnecessary non-null assertion», de la 2.0) y `ui/AppNavHost.kt:535:115` («Expression is unused», de la 1.4.1, `backOnce`). Inofensivos; los tres de `Glass.kt` se pueden quitar en cualquier ronda de código (detalles menores).
- **Info-2:** la skill `advisor` (`.claude/skills/advisor/SKILL.md:3`, `:16`) nombra modelos: ver la recomendación del punto 6.
- **Info-3:** 282 de 283 commits del rango llevan en su pie `Co-Authored-By` el nombre del modelo, y uno nombra en el asunto el modelo del director. Es el historial, no se reescribe (parte §1).
- **Info-4:** filas «Abierto» que deben cerrarse antes de la puerta del APK: 4, 5, 14, 19, 41 (Abogado final), 24 y 52 (ataque), 49 (fecha de la política), 50 (Minor-1).
- **Info-5 (Móvil):** R8 en tiempo de ejecución, ver el punto 2; propongo añadirlo como prueba en «Pruebas de la 2.0» si no se da por cubierto.
- **Info-6:** `check-manifest.sh` compara con los permisos «de la 1.2.2» (`scripts/check-manifest.sh:22-29`), que son también los de la 1.4.3: el texto del error sigue siendo cierto.

## Filas de `pendientes.md`

| Fila | Veredicto |
|---|---|
| 50 | **Se arregla** (Minor-1): `ImportTextsTest` lee también `*-notas.md`, y las entradas de las dos tareas `test` lo incluyen |
| 43 | **Se arregla** (Minor-5): añadir `ffceacc` |
| 48 | Se acepta su destino (tarea 11); recordatorio: falla con 2.0.0 hasta entonces |
| 49 | Se acepta su destino (controlador, antes del PR); `docs/privacidad.html:22` |
| 6, 7, 8, 22, 36, 40 | Cerradas de verdad (punto 5) |

## Pruebas que pide el revisor

1. `ImportTextsTest.thePlayListingAndItsNovedadesNameNoOtherProduct` con la lectura de `*-notas.md` (Minor-1) y la aserción `assertTrue(parts.any { it.first == "2026-11-15-clavero-2.0-notas.md" })`.
2. `AppNameTest`: `assertTrue("versionName = \"2.0.0\"" in gradle)` (Minor-4, opción a).
3. Antes del PR, repetir en un clon limpio del commit final: `assembleRelease -PfirmaDePrueba=true`, `check-manifest.sh --release`, `aapt2 dump permissions` (si algo cambió en `app/`, `gradle/` o el manifiesto).

## Dudas

1. ¿Prefiere el controlador la opción (a) o (b) de Minor-4?
2. La skill `advisor`: ¿se queda en el repositorio (mi recomendación) o el dueño la pasa a su configuración de usuario?

## Lo que no pude comprobar

- R8 en un dispositivo (no hay emulador en la nube): Info-5.
- Que la ejecución del PR tenga de verdad el número 125 o más: deducido de la lista de ejecuciones (la última, 124) y de que el contador solo sube.
- El resultado de los trabajos **build**, **dependency-metadata** y **jvm-check** en GitHub: solo los equivalentes en local.

## Revisión experto-seguridad-cripto

- `app/src/main/AndroidManifest.xml` sin cambios en el rango, y el manifiesto fusionado del APK minificado igual al de la 1.4.3 salvo `versionName` (diff de `aapt2 dump xmltree`): mismos permisos (`scripts/check-manifest.sh:22-29`), `allowBackup=false`, ningún componente exportado nuevo, `FileProvider` no exportado. MASVS-PLATFORM y MASVS-STORAGE no cambian por la compilación.
- `gradle/verification-metadata.xml` y `jvm-check/gradle/verification-metadata.xml` sin cambios: ninguna dependencia ni suma nueva; solo `javax.crypto` y Bouncy Castle (`app/build.gradle.kts:109`, `libs.bouncycastle`, sin cambio de versión).
- `.github/workflows/build-apk.yml:44`, `:88-91`, `:168`, `:283`: los secretos de firma siguen solo con una etiqueta o a mano desde `main`; el PR firma con la clave de prueba pública (`:108`) y ese APK no debe instalarse en el móvil. Nada del rango toca esto.
- `app/proguard-rules.pro:4-11`: las reglas de kotlinx.serialization cubren las clases v4 y de ajustes; los `$$serializer` sobreviven (`mapping.txt`) y los nombres de los enum están en el dex, así que el formato `seguridad-vault` v4 y `settings.json` se leen y se escriben igual minificados. Los formatos (`AppNameTest.kt:118-148`) no cambian.
- `docs/privacidad.html`: la lista de lo guardado sin cifrar coincide con los campos serializados (`settings/Settings.kt:37-53`, `:172-198`; `vault/VaultRegistry.kt:39`, `:42`; `android/BiometricVault.kt:118`, `:121`; `android/PrefsPlaceStore.kt:13`); ninguno es un secreto ni un nombre de bóveda. La frase de la papelera y las copias dice «pueden seguir teniéndolo», como `vault/TrashTexts.kt:20`.
- Regla de veracidad: Minor-2 (un botón citado con su texto viejo) y Minor-7 (una frase en presente que contradice la de debajo) son textos que no dicen lo que hace la 2.0; ninguno promete protección.

## Revisión Ingeniero Móvil Senior

- `app/build.gradle.kts:34-35`: `versionName` 2.0.0; `versionCode` 1 en local y el número de ejecución en la CI (`build-apk.yml:112`); la última ejecución es la 124 (etiqueta `v1.4.3`), así que el APK del PR y el firmado actualizan la 1.4.3 instalada.
- `MainActivity` sigue con `configChanges=0x00000d80` (giro y tamaño, F-5): el estado de las pantallas nuevas no depende de rehacer la actividad.
- `jvm-check/build.gradle.kts:7-12`, `:22`: los 13 tests nuevos de `ui/` corren solo en `:app:testDebugUnitTest` (CI, `build-apk.yml:81`); en local, 2412 tests en verde con `lintDebug` y `lintRelease`.
- `.maestro/borra-datos/p05-ayuda-codigos-qr.yaml:73-86`: pulsa «Crear la primera» y elige «Código 2FA» en «¿Qué quieres guardar?»; cambiado sin ejecutar (`.maestro/README.md:136`), a confirmar en la tarea 11 con las filas 27, 35 y 48.
- `app/src/main/res/values/themes.xml:3-5` y `colors.xml:3`: la ventana previa a Compose usa el fondo de «Noche» (`#FF141733`); `PalettesTest` lo comprueba. Icono adaptativo con `ic_launcher_monochrome.xml` (iconos temáticos de Android 13).
- R8 en tiempo de ejecución: solo en el móvil (Info-5).

## Textos para el Abogado Digital (revisión final)

- `README.md:283` y `:364`: el texto del botón de A6 en las dos pruebas (Minor-2).
- `README.md:78`: la frase de la 1.4.0 en «Formatos» (Minor-7).
- El borrador de notas `2026-11-15-clavero-2.0-notas.md`, entero (GitHub y «Novedades» de Play, 416 caracteres); con Minor-1, su bloque de «Novedades» queda bajo `ImportTextsTest`.
- `docs/privacidad.html`: la lista nueva de lo guardado sin cifrar, la papelera y las copias, el calendario, A6 con las de antes de la 2.0, los selectores (fila 46) y la fecha (fila 49, `:22`).
- `docs/ficha-google-play.md`: «Aspecto», las dos líneas del calendario, «Al actualizar…» y «Borrar algo de la bóveda o de su papelera no lo borra de las copias que ya hiciste.».
- `docs/PUBLICAR.md`: funciones financieras con las tarjetas, «Salud de la bóveda» frente a apps de salud, el logo (O-C1) y las capturas (O-C2).
- `.claude/skills/advisor/SKILL.md`: si los nombres de modelos pueden quedarse en lo publicado del repositorio (punto 6).
