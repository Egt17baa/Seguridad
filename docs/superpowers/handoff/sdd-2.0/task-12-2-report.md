# Clavero 2.0, tarea 12, paso 1: segunda pasada de los documentos (informe del implementador)

**Estado: DONE.** Base: `a9efdcd` (rama `ccr-0aa27f95-u038cd`). Solo documentos; ningún código ni test cambiado. Sin *push*.

Encargo del controlador: arreglar la revisión del paso 1 (`sdd-2.0/task-12-1-review.md`: I-1, m-1 a m-8 y su §6), la línea 31 del README (C m-2), las filas de `pendientes.md`, quitar los nombres de modelos de `docs/` y poner al día el borrador de notas con las dos rondas de la revisión de la rama (`branch-fixes-report.md`, `advisor-t12-branch-fixes.md`, `advisor-t12-branch-rereview.md`).

## Commit

| Commit | Qué |
|---|---|
| (este) | «2.0, tarea 12 (paso 1): segunda pasada de los documentos»: README, política, ficha, notas, tabla de afirmaciones, `pendientes.md`, la corrección del informe del paso 1, los nombres de modelos en `docs/` y este informe |

## Tests

Ejecutados por separado sobre el árbol de trabajo, contados con Python sobre los XML:
- app (`:app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets`): BUILD SUCCESSFUL, 2412 tests, 0 omitidos, 0 fallos, 0 errores (los mismos que tras la ronda 2 de la rama)
- `./gradlew --stop`; jvm-check (`-p jvm-check test`): BUILD SUCCESSFUL, 2361 tests, 0 omitidos, 0 fallos, 0 errores

Ningún test ata un documento a algo que cambie aquí. El único atado que toqué de cerca es `FinalLegalReviewTest.o1ThePolicySaysOnlyTheListedCopiesAreDeleted`, que fija palabra por palabra la frase de A6 de la política («…se borren las copias automáticas y las de antes de importar hechas antes del cambio…»): la dejé igual y la completé con una frase después (las de antes de la 2.0, con las palabras del botón), así que el test sigue en verde y el párrafo es cierto.

## Qué cambió, por punto

### 1. La revisión del paso 1

- **I-1 (Important):** la papelera y las copias. La política («Todo eso se borra de la bóveda del móvil. Las copias que ya hiciste pueden seguir teniéndolo: las que exportaste, hasta que las borres; las automáticas, hasta que la rotación las quite.»), el README («Papelera, deshacer e historial») y las notas, con la idea de `COPIES_KEEP_IT` (`vault/TrashTexts.kt:20`, fila 30). La ficha («no lo borra de las copias que ya hiciste») era cierta y se queda.
- **m-1:** F-5 con `VaultV4Test.kt:382-389` y el diseño `:66`.
- **m-2:** T-5 con `vault/TrashTexts.kt:79-82` y `ui/screens/EntryHistoryScreen.kt:79`, `:156`; T-7 con `vault/VaultStore.kt:1162-1169`, `:1186`.
- **m-3:** S-4 con `git diff a3ac942 HEAD -- gradle/libs.versions.toml` (vacío); O-2 con `vault/VaultData.kt:90` (`SortOrder`).
- **m-4:** G-1 dice «Sólido» (paneles opacos sobre un fondo liso, sin transparencias; `ui/screens/AppearanceScreen.kt:80`).
- **m-5:** el difuminado es del fondo de la app (`STYLE_MORE`, `ui/screens/AppearanceScreen.kt:81-82`): G-2, el README («Aspecto» y la prueba de «Cristal»), la ficha («Aspecto») y las notas dicen «difuminan el fondo de la app que tienen detrás».
- **m-6:** §15 nueva en la tabla, M-1 a M-9, una fila por cada una de las nueve afirmaciones sin fila, con su `archivo:línea` en `a9efdcd`.
- **m-7:** la política añade las cuatro cosas: «si queda por enseñar algún aviso de después de actualizar» (`Settings.kt:56`, `:80`), «si la copia automática se apagó al crear otra bóveda» (`:183`), «las copias que quedan por borrar en cada carpeta (sus identificadores, con su nombre de archivo), los fallos que ya te dijo» (`BackupSchedule.kt:38-39`) y «de todo el móvil, si el chip de seguridad StrongBox falló alguna vez al activar la huella y si ya te lo dijo» (`BiometricVault.kt:109-115`). U-2, U-8, U-9 y U-10 de la tabla, al día. La fila 8 dice lo añadido y la redacción va al Abogado.
- **m-8:** la identidad y los contactos no van a la papelera (`vault/VaultStore.kt:1162-1169`): la política (el párrafo de la papelera y «Tus derechos»), el README y las notas («las entradas que borras… tu identidad y tus contactos no van a la papelera: se borran del todo»).
- **m-9:** punto 4, abajo.
- **§6 de la revisión, lo que las rondas dejaron falso:**
  - textos de memoria (fila 38): E-1 y E-2 de la tabla citan ya `KDF_OUT_OF_MEMORY` y `KDF_NO_MEMORY_NOW` (`crypto/Crypto.kt:94-96`, `:111`, `:114`); las notas dicen «falta memoria… el texto de memoria dice qué probar», sin citar el texto viejo con «Alto»;
  - filas 37, 39 y 42: E-3 con `ATTACHMENTS_TOTAL_WOULD_PASS`; K-2, K-3 y K-7 nuevas; las notas, al día;
  - WPA (fila 26): O-4 y Q-2 de la tabla, el README («Plantillas») y las notas dicen «WPA/WPA2» y la línea «Si la red es solo WPA3, puede que el QR no sirva para conectarse.» (`vault/Templates.kt:30`, `:37`);
  - A6 y las copias de antes de la 2.0 (B I-2): la política y el README usan las palabras del botón, «Hacer una copia nueva y borrar las anteriores (automáticas, de antes de importar y de antes de la 2.0)» (`tools/ToolTexts.kt:508`); K-1 en la tabla;
  - la papelera: I-1, arriba;
  - «sus ajustes» al borrar una bóveda: cierto con B m-2 (`AppContainer.kt:748`, `forgetOrphanVaults`); B-6 lo cita;
  - los selectores: Ruling A m-1, opción (b), sin código; la frase de la política sigue siendo cierta (X-1 y fila 46, al Abogado).
- La tabla: cada `archivo:línea` llevado de `ef0f09d` a `a9efdcd` con el diff de cada archivo (un script que traduce los números por los trozos de `git diff -U0`), revisando a mano las cuatro referencias que caían dentro de un trozo cambiado y comprobando una muestra contra el código; además, §16 nueva (K-1 a K-8) con lo que añaden las rondas y dicen los documentos. Las filas X-1 y X-2 dicen ya su desenlace.

### 2. README, línea 31 (C m-2)

«Las copias con calendario se hacen con la bóveda abierta, al abrir Clavero o al desbloquearla, y solo si hubo cambios», las palabras de la guía (`settings/Guide.kt:43`). Lo mismo en la política, en la ficha (las dos líneas) y en las notas; las «Novedades» de Play dicen «(con la app abierta)».

### 3. `pendientes.md`

- **Hechas, con su commit:** 17 (`035752d`), 18 (`4f0d71b`), 26 (`a5d333f`), 30 (`9e97517`), 37 (`e4f6aa0`, `af15de2`), 38 (`2a03ae6`), 39 (`c2da53c`), 42 (`848f118`) y 45 (`3024ba3`, texto en `cda4590`).
- **Aceptadas con su evidencia**, según el Ruling del director (`advisor-t12-branch-fixes.md`): 2, 20, 21, 31, 32 y 33.
- **46:** al Abogado final (Ruling A m-1, opción b). **47:** decidida por el dueño el 16-11-2026, «le vale así».
- **3:** con C i-1 («cada cambio de `breached` relanza el cálculo, y el anterior termina aunque se cancele»).
- **28 (Móvil):** el aviso de «Bóvedas» con dos textos, con la letra al 200 %.
- **51 (Móvil, nueva):** medir la falta de memoria con «Alto» y una bóveda grande.
- **52 (nueva, ataque final):** la prueba n.º 7, sin tope de etiquetas por entrada en `checkCopyEntries` (`BackupCheck.kt:196-212`). `MAX_TAGS` = 10 está en `EntryQuery.kt:12` desde la 1.4.0 (`bbf2200`); antes de rechazarlo en una copia, mirar si alguna versión publicada pudo escribir más.
- **8:** con las cuatro cosas de m-7.

### 4. Nombres de modelos

Buscados en todo `docs/` los nombres de los modelos (cuatro palabras y los identificadores que empiezan por el nombre del asistente seguido de un modelo), sin distinguir mayúsculas: 194 apariciones en 46 archivos, de la 1.1.0 a la 2.0. Se sustituyeron todas:
- en los pies de commit citados en planes, contextos e informes, el nombre del asistente se queda y el del modelo se quita;
- la llamada a la skill `advisor` se escribe sin modelo («`/advisor`»);
- el nombre del modelo del director pasa a «el director» o, donde decía cuál eligió el dueño, a «el modelo que eligió el dueño»;
- el modelo de cada implementador, revisor o re-revisión se quita («implementer», «reviewer», «revisor», «un implementador nuevo»…).

Después, la misma búsqueda solo encuentra rutas del *scratchpad* (`/tmp/claude-0/…`), que no son nombres de modelos. Los pies de los commits ya hechos están en git, no en archivos, y no se tocan. Fuera de `docs/` queda la skill del director (`.claude/skills/advisor/SKILL.md`, configuración del proyecto: no la toco sin que el controlador lo diga) y una palabra de la lista de la EFF que coincide con uno de esos nombres y no es un modelo.

### 5. Notas

`docs/superpowers/handoff/2026-11-15-clavero-2.0-notas.md`, al día con las dos rondas: la papelera (I-1, m-8), el calendario (C m-2), el wifi (fila 26), A6 con las copias de antes de la 2.0 (B I-2), los errores (filas 37 a 39, 42 y 38), el aviso del coste que no subió (fila 45), el aviso del cambio de bóveda (A I-1 y B i-1) y los diálogos de «Bóvedas» que se cierran al bloquear (C I-1). «Novedades» de Play: 416 caracteres. Sin nombres de otras apps.

## Dudas

1. **`.claude/skills/advisor/SKILL.md`** nombra modelos (es la skill que se llama con el modelo). Si también debe quedar sin nombres, es configuración del proyecto: decide el controlador.
2. **La frase de A6 de la política** está atada por `FinalLegalReviewTest` con las palabras de la 1.4.0. La completé sin tocarla; si el Abogado quiere una sola frase con las de antes de la 2.0, hay que cambiar el test a la vez.
3. **Fila 50:** sigue como estaba; el borrador de notas recuerda que el bloque de «Novedades» se copie al traspaso de la 2.0.

## Revisión experto-seguridad-cripto

- **Nada promete de más sobre el borrado:** la política, el README y las notas dicen ahora que las copias **pueden** conservar lo borrado y que la rotación quita las automáticas, como el código (`vault/TrashTexts.kt:20`; la rotación, `vault/BackupSchedule.kt:16-27`); la identidad y los contactos se borran del todo (`vault/VaultStore.kt:1162-1169`).
- **Lo guardado sin cifrar, completo:** la lista de la política cubre ya todos los campos serializados (`settings/Settings.kt:42-100`, `:172-194`; `vault/BackupSchedule.kt:30-59`, `:100-118`; `vault/VaultRegistry.kt:39`, `:42`) y las preferencias de la huella (`android/BiometricVault.kt:109-121`, `:478-480`); ninguno es un secreto ni un nombre de bóveda.
- **A6:** los documentos usan las palabras del botón (`tools/ToolTexts.kt:508`) y del resultado (`vault/AutoBackup.kt:152-164`): nombran las copias de antes de la 2.0 que de verdad se borran.
- **Memoria:** los documentos no citan ya el texto viejo con «Alto»; la tabla cita los dos de `crypto/Crypto.kt:94-96`, `:114`.
- Sin cambios de código, de formatos, de permisos ni de red.

## Revisión Ingeniero Móvil Senior

- **Lo que depende de Android:** el difuminado, solo desde Android 12 y del fondo de la app (`ui/theme/Glass.kt:105`, `:252`; `ui/screens/AppearanceScreen.kt:81-82`); los colores del fondo de pantalla, desde Android 12 (`ui/theme/Theme.kt:80`). Los documentos lo dicen así.
- **Sin segundo plano:** el calendario de copias corre con la bóveda abierta, al abrir o al desbloquear (`vault/AutoBackup.kt:457`, `:505`; `settings/Guide.kt:43`), y los documentos lo dicen con esas palabras.
- **Ciclo de vida:** los diálogos de «Bóvedas» se cierran con el estado de la bóveda (`ui/screens/VaultPickerScreen.kt:168`); el aviso de «Bóvedas» se desplaza (`ui/AppNavHost.kt`, `79da15f`), con su prueba al 200 % en la fila 28.
- **Pruebas en el móvil:** tres nuevas en el README (la letra al 200 % del aviso, la memoria con «Alto» y una bóveda grande, borrar y la rotación de las copias con un contacto que no va a la papelera), para la tarea 11.

## Textos para el Abogado Digital (revisión final)

- La política: la frase de la papelera y las copias (I-1); la excepción de la identidad y los contactos (m-8), en el párrafo de la papelera y en «Tus derechos»; las cuatro líneas nuevas de lo guardado sin cifrar (m-7); la frase del calendario; la de A6 con las de antes de la 2.0.
- El README: «Papelera, deshacer e historial», «Copias en varias carpetas», «Plantillas» (WPA), «Aspecto» (el fondo de la app), «Formatos» (el botón de A6) y las tres pruebas nuevas.
- La ficha: «Aspecto» y las dos líneas del calendario.
- Las notas de GitHub y de Play, enteras.
- Además, lo que ya pedían las rondas: la fila 46 (los selectores) y la fila 19 con la 30 (la redacción de `COPIES_KEEP_IT`).

# Ronda de la parte D

Encargo del controlador: los Minor-1 a Minor-7 y el Info-1 de la parte D (`sdd-2.0/branch-review-compilacion.md`) y n-1 a n-3 de la re-revisión de la segunda pasada (`sdd-2.0/task-12-2-rereview.md`). Sin *push* mío (el controlador subió `a827d10`).

## Commits

| Commit | Qué |
|---|---|
| `a827d10` | Código y tests: Minor-1 (`ImportTextsTest` y las entradas de las dos tareas `test`), Minor-4 (`AppNameTest` fija `versionName` 2.0.0) e Info-1 (los avisos del compilador) |
| (este) | «2.0, revisión de la rama, parte D: documentos»: Minor-2, -3, -5, -6, -7, n-1, n-2, n-3 y este informe |

## Por punto

- **Minor-1 (fila 50):** `ImportTextsTest.kt:150` lee también los `*-notas.md`, y `thePlayListingAndItsNovedadesNameNoOtherProduct` comprueba que lee `2026-11-15-clavero-2.0-notas.md`. Las entradas de la tarea `test` en `jvm-check/build.gradle.kts:63` y `app/build.gradle.kts:242` incluyen `*-notas.md`. **Rojo** (con la aserción y sin ampliar el lector): `thePlayListingAndItsNovedadesNameNoOtherProduct :: AssertionError: the 2.0 «Novedades» draft not read`. **Verde** con el lector ampliado; el bloque de las notas no nombra ninguna app. La fila 50 pasa a hecha.
- **Minor-2:** `README.md:283` y `:364` citan el botón de la 2.0, «Hacer una copia nueva y borrar las anteriores (automáticas, de antes de importar y de antes de la 2.0)» (`tools/ToolTexts.kt:508`). La de la 1.3.0 recuerda el texto de la 1.4.x, y P-11 añade «y las de antes de la 2.0». `grep -n "automáticas y de antes de importar)" README.md` solo encuentra ya esa mención entre paréntesis.
- **Minor-3 y n-3:** la sección 4 de este informe (en las dos copias) ya no nombra ningún modelo: dice que se sustituyeron y cómo, sin citarlos. También se quitaron los dos nombres que citaba la revisión de la parte D (`branch-review-compilacion.md:83` y `:117`). Una búsqueda de los nombres de los modelos y de sus identificadores en `docs/`, `CLAUDE.md` y el README, quitando las rutas `/tmp/claude-0/`, sale vacío.
- **Minor-4:** `AppNameTest.theIdentityTheFormatsAndTheClipboardLabelDoNotChange` comprueba `versionName = "2.0.0"` en `app/build.gradle.kts` (opción a). **Rojo** con `versionName` puesto a mano en 1.4.3 durante la prueba: `theIdentityTheFormatsAndTheClipboardLabelDoNotChange :: java.lang.AssertionError` (8 tests, 1 fallo). **Verde** con 2.0.0, que se restauró antes del commit.
- **Minor-5:** fila 43 con `ffceacc`.
- **Minor-6:** `CLAUDE.md:35`: «… `seguridad-boveda-`, desde la 1.4.0, `seguridad-antes-de-importar-` y, desde la 2.0, `seguridad-antes-de-2-0-`».
- **Minor-7:** `README.md:78`, en pasado: «La 1.4.0 no cambió ningún formato de Clavero ni añadió uno nuevo.»
- **Info-1:**
  - `ui/theme/Glass.kt:250` y `:252` quedan sin los `!!` innecesarios (`backdrop` y `at` ya se sabían no nulos).
  - `ui/AppNavHost.kt:535`: `{ nav.popBackStack(); Unit }` pasa a `{ nav.popBackStack() }`. Era trivial: la lambda ya es `() -> Unit`, así que el `Boolean` se descarta igual. El comportamiento no cambia y `backOnce` sigue cubierto por sus tests.
  - La compilación ya no da ningún aviso `w:` de Kotlin.
- **n-1, el calendario:** donde decía «y solo si hubo cambios», ahora dice que las copias con calendario «se saltan si no hubo cambios desde la última de esa carpeta (nunca la primera)», adaptado a cada frase:
  - el README (línea 31, que ya no se contradice);
  - la política (párrafo de las copias);
  - la ficha («Copias cifradas…»);
  - las notas.
  
  La guía de la app (`settings/Guide.kt:43`) sigue con «y solo si hubo cambios», más corta (M-3): va al Abogado para que decida si quiere las mismas palabras.
- **n-2:**
  - La prueba de la fila 30 del README dice ahora: «exportar una copia, borrar una entrada para siempre («Borrar para siempre» en la papelera, o «Vaciar papelera») y hacer dos cambios más, esperando cada vez a que se haga su copia: ninguna copia automática de la carpeta tiene ya la entrada; la exportada antes, sí». Explica que una entrada solo movida a la papelera sigue en todas las copias.
  - La prueba de la memoria dice «(fila 51)».

## Tests

Por separado, sobre el árbol con todo esto:
- app (`:app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets`): BUILD SUCCESSFUL, **2412 tests, 0 fallos**, sin avisos `w:`.
- `./gradlew --stop`.
- jvm-check: BUILD SUCCESSFUL, **2361 tests, 0 fallos**.

Los dos tests cambiados añaden aserciones, no tests, así que el número no cambia.

## Revisión experto-seguridad-cripto

- `app/src/test/java/app/seguridad/imports/ImportTextsTest.kt:150`, `:164-165`: la regla de no nombrar otras apps en las «Novedades» de Play (R2, R7, R8) protege ya también el borrador de la 2.0. Las entradas de las tareas (`jvm-check/build.gradle.kts:63`, `app/build.gradle.kts:242`) hacen que un cambio en las notas vuelva a ejecutar el test.
- `app/src/test/java/app/seguridad/tools/AppNameTest.kt`: `versionName` fijado, para que no salga una release con el número viejo. El User-Agent de Pwned Passwords lo toma de ahí.
- `ui/theme/Glass.kt:250`, `:252` y `ui/AppNavHost.kt:535`: solo quitan avisos, sin cambio de comportamiento, de red, de formato ni de permisos.
- Los documentos dicen ahora lo que hace el código con las copias con calendario (`vault/AutoBackup.kt`, `runDue`, la condición de D-3) y con la papelera en las copias (`vault/Trash.kt:9-12`).

## Revisión Ingeniero Móvil Senior

- `ui/AppNavHost.kt:535`: la lambda de `backOnce` sin fragmento suelto; `remember(nav)` la sigue guardando igual.
- `ui/theme/Glass.kt:250-252`: el *smart cast* de `backdrop` y `at` ya los da por no nulos dentro de `ready`. El difuminado sigue solo desde Android 12 (`SDK_INT >= S`).
- Pruebas en el móvil del README: la de la fila 30 es ahora reproducible (borrar para siempre y esperar cada copia), y la de la memoria apunta a la fila 51.

## Textos para el Abogado Digital (revisión final)

- La frase del calendario, en el README (línea 31), la política, la ficha y las notas: «se saltan si no hubo cambios desde la última de esa carpeta (nunca la primera)». Frente a ella, la guía de la app dice «y solo si hubo cambios».
- `README.md:78` («no cambió») y las dos pruebas con el botón de A6 de la 2.0 (`:283`, `:364`).
- La prueba de la fila 30 del README.
