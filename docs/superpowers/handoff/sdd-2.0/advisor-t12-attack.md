# Ruling del director: encargo del ataque final y orden hasta el APK (17-11-2026)

Veredicto: El ataque puede empezar ya sobre `cad3ba5`. La re-revisión de D solo toca documentos y tests, así que eso ya es el código final (R32). Va en un worktree propio, con Gradle de uno en uno bajo `flock`, y por fases que se guardan y se suben. Detrás viene una sola ronda de arreglos, que el atacante vuelve a comprobar; después, `code-review` alto y `security-review`, la auditoría, los documentos, el Abogado, la fecha de la política y el PR.

Motivos:
- **El código está congelado.** `git diff e67f668..cad3ba5 -- app/src/main` solo toca `ui/AppNavHost.kt` (1 línea) y `ui/theme/Glass.kt` (2 líneas, los avisos del compilador de `a827d10`), más `versionName` en `app/build.gradle.kts`. Atacar `cad3ba5` es atacar el código final, siempre que nada entre en `app/src/main` hasta que cierre la re-comprobación.
- **Fila 52 (`pendientes.md:60`).** `checkCopyEntries` (`vault/BackupCheck.kt:196-212`) no tiene tope de etiquetas. Pero un tope de 10 (`vault/EntryQuery.kt:12`) puede romper copias reales:
  - `imports/MergePlan.kt:376` conserva más de 10 si la entrada ya las tenía («its own are never dropped»);
  - `MAX_TAGS` existe desde `af29a78` (24-10-2026) y ya está en la v1.3.0 (`1a7f7bd:…/EntryQuery.kt:7`), pero ninguna versión publicada comprobó las etiquetas de una copia importada;
  - el defecto de fondo es la memoria antes de las comprobaciones (i-5, `branch-review-formato.md:176`): `vault/Vault.kt:412-417` solo mira la profundidad y decodifica todo, y `importReplace` (`vault/VaultStore.kt:1493-1530`) no captura `OutOfMemoryError`, mientras que `ui/screens/ImportScreen.kt:308` sí (R11c).
- **Fila 24 (`pendientes.md:32`).** El test diferencial de la tarea 9 solo compara `WifiQr` (`tools/qr/WifiQr.kt:91-320`) con el lector de ZXing. Falta el sentido inverso, «Enseñar QR» desde valores hostiles de la bóveda, y el gemelo malvado.
- **Dos hipótesis nuevas que cruzan tareas, vistas al leer el código** (las tiene que confirmar el atacante):
  - **H-1:** `settingsEditable` es verdad con `NoVault` (`settings/Settings.kt:443`). «He olvidado la contraseña maestra» vacía cualquier bóveda con una sola confirmación (`ui/screens/VaultScreen.kt:479`, `:489-498`), lo que aceptó el Ruling T2, duda 1. Con dos bóvedas, eso reabriría COV2-1.
  - **H-2:** «Importar como bóveda nueva» solo vuelve a envolver la clave (`VaultStore.kt:1494-1504`); solo `changeMaster` crea una clave de datos nueva (`Vault.kt:556-563`). Dos bóvedas pueden compartir la clave de datos, en contra del diseño (`specs/2026-11-12-clavero-2.0-design.md:38`, `:62`).
- **Logística:**
  - las dos suites juntas dan exit 137, y `jvm-check/build.gradle.kts:20-22` compila `redteam/` sin `ui/`: se itera ahí;
  - `.superpowers/` no se versiona (`progress.md:6`);
  - el checkout tiene `user.name=Claude` en su configuración, así que cada commit necesita `-c`.

Encargo del ataque:

---
**Ataque final de Clavero 2.0 (decisiones (d) y (f) del dueño, R32)**

Eres el agente atacante (equipo rojo) de Clavero 2.0, una app Android de bóveda de contraseñas en Kotlin y Compose. La app es del dueño y todo es local: el ataque está autorizado y ocurre dentro del árbol de tests. **No arreglas nada:** encuentras, lo demuestras con un test y lo cuentas. Escribe en español.

**Lee primero, en este orden:**
1. `CLAUDE.md`.
2. `.claude/skills/experto-seguridad-cripto/SKILL.md` y sus `references/auditoria.md` y `references/cripto-moderna.md`.
3. Las skills `auditoria-seguridad-movil`, `seguridad-criptografia-movil` y `desarrollo-movil-senior`.
4. `docs/superpowers/handoff/2026-11-15-clavero-2.0-parte.md` §1 y `2026-11-16-clavero-2.0-parte.md`.
5. El diseño `docs/superpowers/specs/2026-11-12-clavero-2.0-design.md`, sobre todo `:25-30` («La haría fracasar») y `:32-48` («Modelo de amenaza»).
6. `docs/superpowers/handoff/sdd-2.0/progress.md`. Cada «Ruling» y cada «Owner decision» obliga: no reabras lo decidido salvo con un camino nuevo y su test.
7. `docs/superpowers/handoff/sdd-2.0/pendientes.md`.
8. La revisión de la rama, para no repetir lo cerrado: `sdd-2.0/branch-review-*.md`, `branch-rereview-*.md` y `branch-fixes-report.md`.
9. Como modelo, el ataque de la 1.4.0: `docs/auditorias/2026-11-08-ataque-1.4.0.md`, `docs/superpowers/handoff/sdd-1.4.0/redteam-report.md` y `app/src/test/java/app/seguridad/redteam/*.kt`.

**Dónde trabajas**
- En un worktree propio, fuera del repositorio, en la rama nueva `rt-2.0`, desde el commit que te dé el controlador (hoy `cad3ba5`):
  `git -C /home/user/Seguridad worktree add /home/user/Seguridad-rt -b rt-2.0 <commit>`
- Solo escribes en `app/src/test/java/app/seguridad/redteam/`, en archivos nuevos `RedTeam20*Test.kt`. Lo que necesite Compose va en `app/src/test/java/app/seguridad/ui/redteam/`, que `jvm-check` no compila. Además escribes tu informe.
- Nunca tocas `app/src/main`, `jvm-check`, `gradle/`, `.github/` ni los documentos. Nunca haces push, ni PR, ni *force*.
- **Commits:**
  - siempre con `git -c user.name=Egt17baa -c user.email=Egt17baa@users.noreply.github.com commit`, porque la configuración del checkout no es esa;
  - el asunto empieza por «2.0, ataque: …»;
  - al final van las dos líneas de atribución de la sesión, copiadas de `git log -1 --format=%B <commit de partida>`;
  - ningún nombre de modelo en archivos, asuntos ni cuerpos, y ningún dato del dueño.
- **Datos de prueba:** inventados y públicos (decisión (e)). Lo de otras apps, escrito desde su documentación pública y nunca copiado.

**Cuándo compilas (hay poca memoria)**
- Lee y escribe sin Gradle todo lo que puedas, y compila por lotes.
- Cada llamada a Gradle va con el cerrojo común y un solo proceso:
  `flock /home/user/.gradle-run.lock ./gradlew -p jvm-check test --tests 'app.seguridad.redteam.RedTeam20*' --no-daemon --max-workers=1`
- Al cerrar cada fase, una vez en la app:
  1. `export ANDROID_HOME=/home/user/android-sdk` (si no existe, para y avisa; se reinstala según el Ruling del registro);
  2. `flock /home/user/.gradle-run.lock ./gradlew :app:testDebugUnitTest --tests 'app.seguridad.redteam.*' --no-daemon --max-workers=1`;
  3. después, `./gradlew --stop`.
- Nunca corres las suites enteras ni dos Gradle a la vez: las suites las corre el controlador en un clon limpio.
- **Ningún test provoca un `OutOfMemoryError` de verdad:** mataría el proceso de tests, que tiene 512 MiB. Mide en su lugar (objetos o bytes retenidos, `Runtime` antes y después) contra un presupuesto de móvil de 192 MiB, o comprueba que la entrada se rechaza antes de decodificar.
- Cada test dura menos de 10 s, sin `Thread.sleep` (usa *latches* o puntos de sincronización), con semilla fija (`Random(20261117L)`) y sin red.

**Superficies, por prioridad** (rutas relativas a `app/src/main/java/app/seguridad/`)

*Fase 1: lo que haría fracasar la 2.0 (diseño `:25-30`)*

1. **Copias v4 hostiles en «Importar copia», «Importar como bóveda nueva» y «Comprobar una copia».**
   - Dónde mirar: `vault/VaultStore.kt:1493-1530` (`importReplace`, `afterMasterUnlock` antes de las comprobaciones); `vault/Vault.kt:409-417` (solo la profundidad, después decodifica todo); `vault/BackupCheck.kt:196-212`, `:218`, `:234`; `vault/Backup.kt:44-58` (marca 3, 33 MiB); `vault/VaultLimits.kt:19-54`.
   - **Fila 52 (prueba n.º 7):**
     - Escribe primero la historia. Usa `git log -S MAX_TAGS` y `git grep` sobre las versiones publicadas: `1a7f7bd` (v1.3.0), `ca597be` (v1.3.1), `b92286e` (v1.4.0), `ba903e7`, `bd7b51e` y `a3ac942` (v1.4.3). Pregunta: ¿pudo alguna versión dejar una entrada con más de 10 etiquetas? Por ejemplo, con una copia hecha a mano e importada antes; `imports/MergePlan.kt:376` las conserva. Si pudo, sus copias propias también las llevan.
     - Mide dónde se va la memoria: al decodificar, en `afterMasterUnlock` o después.
     - Comprueba qué pasa con un `OutOfMemoryError`: hoy no se captura (compáralo con `ui/screens/ImportScreen.kt:308` y R11c).
     - Comprueba si una copia que entra en un móvil con mucho *heap* deja la bóveda sin abrir en otro con poco: eso sería una bóveda perdida.
     - Propón el arreglo: estimar antes de decodificar (como `JSON_MAX_TREE_BYTES`), un tope generoso o capturar la excepción como última defensa. Nunca un tope que rechace una copia propia.
   - **Cada campo de v4 con valores hostiles:**
     - `folders`, `views`, `tagColors`, `fields`;
     - `history`, con versiones de tipo personal, con adjuntos, con su propio historial o con `savedAt` repetidos;
     - `deletedAt` y `lastUnlockAt` en el futuro o negativos, y `trashPurgeSkipped`;
     - `expiresAt` y `lastUsedAt` en `Long.MAX_VALUE`;
     - `template` y `notesFormat` desconocidos, un `name` largo, un `vaultTag` que no es hex o es enorme, y `lastCheckedBackupAt` en el futuro;
     - los tipos `CARD`, `WIFI`, `LICENSE` y `PASSPORT` dentro de una bóveda v3;
     - la marca de la copia frente a la versión de dentro (marca 2 con v4, marca 3 con v3; `Backup.kt:58`);
     - un archivo sin versión nunca se prueba como 4 (`Vault.kt:221-225`).
   - **Una copia que cierra la app al pintarse** (la clase de T4 C-1): revisa cada clave de Compose hecha con datos de la bóveda en `ui/screens/VaultList.kt:543`, `:556` y `:571`, `VaultOrganize.kt:402` y `:455`, `EntryHistoryScreen.kt:94`, `TrashScreen.kt:111` y `EntryFields.kt`. Una clave repetida, o que choca con una fija, da una bóveda que se cierra cada vez que se abre.

2. **El paso a v4 y la muerte del proceso.**
   - Dónde mirar: `vault/PendingUpgrade.kt:41-87`; `vault/Vault.kt:522-557` (`afterMasterUnlock`, `upgraded`, `fitted`) y `:593` (la huella se rechaza antes del paso); `vault/AutoBackup.kt:114-120` y `:738` (`seguridad-antes-de-2-0-`).
   - En cada punto (la copia `.tmp`, el renombrado, la escritura v4, los ajustes), el disco queda con el archivo anterior o con el nuevo, nunca a medias.
   - Comprueba también que:
     - una v4 vuelta a v3 a mano no se abre como 4;
     - `fitted()` no pierde nada sin decirlo;
     - la clave de la huella no abre un archivo que no es suyo (CRY-A02).

3. **Aislamiento entre bóvedas.**
   - Dónde mirar: `AppContainer.kt:554`, `:611-623`, `:631-668`, `:695` y `:748`; `android/BiometricVault.kt:67` y `:92-124`; `vault/VaultRegistry.kt:31`, `:90` y `:150-176`; `settings/Settings.kt:443`.
   - **H-1 (COV2-1 con varias bóvedas):** `settingsEditable` es verdad con `NoVault`, y «He olvidado la contraseña maestra» (`ui/screens/VaultScreen.kt:479`, `:489-498`) vacía cualquier bóveda con una sola confirmación (aceptado para esa bóveda en el Ruling T2, duda 1). Con A y B cerradas: ¿puede quien tiene el móvil un momento vaciar B y cambiar el bloqueo, el portapapeles o el coste de Argon2id de toda la app, que después protegen a A?
   - **H-2 (la misma clave de datos en dos bóvedas):** «Importar como bóveda nueva» solo vuelve a envolver la clave (`VaultStore.kt:1494-1504`), y solo `changeMaster` crea una clave de datos nueva (`Vault.kt:556-563`). Busca un camino sin root (los adjuntos `att/`, la clave de la huella o las copias) por el que los datos de una bóveda se abran o se mezclen con la otra. Si no lo hay, es una afirmación del diseño (`:38`, `:62`) o de un texto que no es verdad: dilo como veracidad.
   - **Lo de cada bóveda se borra al bloquear, al cambiar de bóveda y al borrarla:**
     - el QR pendiente;
     - la oferta de copiar la contraseña (`tools/TwoStepCopy.kt:10`, `:37`) y el aviso A14;
     - los títulos de las misiones, los filtros, la selección, el deshacer y el formulario;
     - `PickerPlaces`, «Ver en grande» y el QR del wifi.
   - **Ningún resultado tardío escribe ni enseña nada en otra bóveda.** Revisa: cambiar la maestra, importar, comprobar una copia, exportar, activar la huella, la copia con calendario, `markUsed` y la purga de la papelera.
   - A11 y la regla de los 7 días van por bóveda.
   - Prueba un `vaults.json` dañado o con identificadores raros: `../`, mayúsculas, 33 cifras u 11 bóvedas.

4. **Copias a varios destinos y A6** (`vault/AutoBackup.kt`, `BackupSchedule.kt`, `A6Text.kt`).
   - Otra app con acceso a la misma carpeta compartida (decisión (f)) planta, cambia o renombra archivos con los nombres de Clavero (`seguridad-auto-`, `-antes-de-importar-`, `-antes-de-2-0-`). Busca si así la rotación o A6 borran lo que no es suyo, o si «Comprobar una copia» apunta una fecha falsa.
   - El reloj adelantado o atrasado frente al calendario y frente a la marca de D-3.
   - La copia de B nunca acaba en la carpeta de A.

*Fase 2: textos de fuera, QR y portapapeles*

5. **Fila 24: lo que enseña Clavero frente a lo que lee otro lector.**
   - Dónde mirar: `tools/qr/WifiQr.kt:30`, `:91-142`, `:209-320` y `:321`; `tools/qr/QrPendingText.kt:39-58`; `MainActivity.kt:105-119`; `ui/screens/WifiQrScreen.kt` y `QrInspectScreen.kt`.
   - **Sentido (a), el gemelo malvado:** un QR hostil que el Inspector enseña como «Casa» y añade a la bóveda, pero que otro lector une a otra red. Prueba:
     - claves repetidas, una `\` al final, `;` y `:` escapados y comillas;
     - minúsculas (`wifi:`), BOM y NUL;
     - marcas de dirección e invisibles en el nombre (lo que se guarda tal cual frente a lo que se enseña con `visibleText`);
     - el último campo sin `;`.
   - **Sentido (b):** una entrada de la bóveda con valores hostiles cuyo «Enseñar QR» da a otro lector una red o una contraseña distintas de las de la ficha.
   - **El oráculo:** ZXing (ya está en el APK) y las reglas de la descripción pública del formato, escritas por ti.
   - Comprueba también que la descripción de la imagen nunca lleva la contraseña y que el texto pendiente se borra al salir y al bloquear.

6. **Notas con formato** (`vault/LimitedMarkup.kt:36-45`, `:86-160`; `ui/screens/MarkupText.kt`).
   - Ningún enlace: ni `LinkAnnotation`, ni `UrlAnnotation`, ni `Linkify`, ni `autoLink`.
   - Entradas patológicas con tiempo y memoria acotados: 64 KiB de `*` y `**` alternos, 10 000 líneas de lista, numeración de 9 cifras.

7. **Portapapeles y lo pegado.**
   - El 2FA de un toque: si quedan menos de 5 s, copia el siguiente.
   - «Copiar contraseña» en dos pasos: solo después de salir y volver, durante 30 s, y se borra al bloquear.
   - El aviso A14 en Android 8 a 12 y la marca de sensible.
   - «Reconocer lo pegado» nunca lee el portapapeles si el usuario no pega.

8. **Búsquedas** (`vault/FuzzySearch.kt`, `settings/SettingsSearch.kt`).
   - Nunca miran contraseñas, campos ocultos ni notas: ningún resultado sirve de oráculo de un secreto.
   - Tope de 200 puntos de código, y el trabajo fuera del hilo principal.

*Fase 3: barrido de regresión sobre toda la app, como en la 1.4.0*

- «Importar de otra app» sobre una bóveda v4: la papelera, los tipos nuevos frente a R67, y «Reemplazar» con el historial.
- `SG1:` y `.sgenc`, SLIP-0039, las bombas de KDF y los nombres de archivo.
- Cada `Dialog`, `AlertDialog`, `DropdownMenu` y `Popup` nuevo llama a `FilterObscuredTouches()` (`android/SecureWindow.kt:36-52`). Mira si `PlatformSourcesTest.kt:548-567` los ve todos.
- Las pantallas nuevas no guardan secretos ni imágenes en `rememberSaveable` ni en un `Bundle`.
- Ningún log con datos.
- `settings.json` y `vaults.json` guardan sin cifrar exactamente lo de la fila 8.
- La red va solo a `api.pwnedpasswords.com`, y ninguna misión la abre (O-6).
- Para el manifiesto y los permisos, usa la evidencia de la parte D (`branch-review-compilacion.md`). No compiles un APK salvo que tengas una duda concreta.

**Fuera de alcance**
- Root, el *malware* con accesibilidad y la manipulación física, como en la 1.4.0.
- Lo que solo se ve en un móvil va a «Necesita prueba en el móvil», con la fila de `pendientes.md` o de «Pruebas de la 2.0» del README que lo cubre.
- Lo aceptado por un Ruling (filas 2, 20, 21, 31, 32, 33, 46 y 47, y el Ruling T2, duda 1) solo se reabre con un camino nuevo y su test.

**Qué cuenta como hallazgo** (siempre con `archivo:línea` y un test que lo reproduce)
- Algo que hace fracasar la 2.0 (diseño `:25-30`):
  - perder o estropear una bóveda;
  - un secreto fuera de la bóveda cifrada: en los ajustes, un aviso, un log, un `Bundle`, la lista de recientes, o el portapapeles sin la marca de sensible o sin borrarse;
  - una bóveda que se abre con la maestra o la clave de otra, o que sigue abierta cuando se bloquea otra;
  - un borrado sin papelera ni deshacer.
- Romper las decisiones (a), (b) o (c), una regla del proyecto (formatos para siempre, solo primitivas estándar, la red, `FLAG_SECURE`, los logs, `rememberSaveable`) o un Ruling.
- Que la app se cierre, o que una bóveda no se pueda abrir, por un dato que llega de fuera: una copia, un QR, una importación o una carpeta compartida.
- Un texto visible, o de la política, que afirma una protección que el código no da (veracidad).

La severidad usa la escala de `experto-seguridad-cripto` (de Crítica a Informativa), justificada por el impacto y la probabilidad en esta app. Añade el CVSS 3.1 cuando aplique y MASVS v2. Para el proceso, un hallazgo de seguridad confirmado cuenta al menos como Important.

**Pruebas de concepto como tests** (como en la 1.4.0)
- Un test `rtN_…` comprueba el comportamiento **seguro**, así que falla mientras RT-N siga abierto. Lleva `@Ignore("RT-N: abierto, ver redteam-report.md")` solo para que la puerta siga en verde; el arreglo lo quita. La numeración sigue la de la 1.4.0, que acabó en RT-20: la 2.0 empieza en **RT-21**.
- Un test `resists…` documenta un ataque que hoy se resiste y se queda como test de regresión.
- El KDoc de cada test dice el ataque, la superficie y el RT. Usa la API pública o `internal`, sin reflexión salvo que no haya otra forma.
- Un test que lee el código fuente solo se usa si no hay otra forma, y con un mínimo de coincidencias para que nunca pase vacío.
- Para cada `rtN_`, pega en el informe la salida del fallo con el código de hoy.

**Informe y guardado** (los límites de uso son de 5 horas)
- **Informe de trabajo:** `/home/user/Seguridad/.superpowers/sdd/2026-11-12-clavero-2.0/redteam-report.md`, escrito por bloques. Usa la estructura de `sdd-1.4.0/redteam-report.md`, con estas secciones:
  - Alcance y modelo de amenaza;
  - Resumen: tabla con RT, superficie, hallazgo, severidad con CVSS, MASVS y estado;
  - Hallazgos: dónde, cómo se reproduce, qué pasa, impacto, arreglo propuesto y test;
  - Ataques intentados y resistidos;
  - Filas 24 y 52, con su veredicto;
  - H-1 y H-2, confirmadas o descartadas;
  - Necesita prueba en el móvil;
  - Revisión experto-seguridad-cripto;
  - Revisión Ingeniero Móvil Senior;
  - Textos para el Abogado Digital (revisión final);
  - Puerta.
- **Arriba del todo, un bloque «Estado»,** al día cada vez que cierras una superficie: las hechas, las pendientes, el último commit y la última ejecución de Gradle con su resultado. Si el límite de uso te corta, la sesión siguiente empieza leyéndolo.
- **Al cerrar cada superficie:** un commit con sus tests (aunque estén con `@Ignore`) y, en el mismo commit, una copia del informe en `docs/superpowers/handoff/sdd-2.0/redteam-report.md`, porque `.superpowers/` no se versiona. El controlador une `rt-2.0` a la rama y sube al cerrar cada fase.
- **Has terminado cuando:**
  - las superficies 1 a 8 y el barrido tienen su respuesta con `archivo:línea`;
  - las filas 24 y 52, y H-1 y H-2, tienen su veredicto;
  - cada RT tiene su test, con el rojo enseñado;
  - el subconjunto `redteam` está en verde con los `@Ignore`, en `jvm-check` y en la app;
  - el informe está en sus dos sitios.

**Después** (no es tu trabajo, pero escríbelo para quien venga)
1. El controlador lleva los hallazgos al director, que da un Ruling por RT.
2. Una sola ronda de arreglos, con otro implementador y TDD: quita el `@Ignore`, enseña el rojo, arregla y deja el test en verde. Nunca rompe `SG1:`, `.sgenc`, `seguridad-vault` ni `seguridad-backup`. Su informe: `redteam-fixes-report.md`.
3. Después, tú vuelves a comprobar cada arreglo y buscas cómo saltártelo (decisión (f)), en `redteam-recheck-N.md`. Si no se te puede retomar, lo hace un agente nuevo con este mismo encargo y tu informe, y se apunta.
4. Un hallazgo nuevo abre otra ronda. Con 5 rondas, decide el dueño.
5. Al cerrar el ataque:
   - el controlador corre las dos suites, por separado, en un clon limpio;
   - el PR no puede llevar ningún `@Ignore` en `redteam/`;
   - se escribe el informe publicado, `docs/auditorias/<fecha de inicio>-ataque-2.0.md`, desde el de trabajo y con el estado de cada RT (R39), sin nombres de modelos ni datos del dueño, y con la línea de marcas si nombra otras apps;
   - los informes de trabajo se copian a `sdd-2.0/`.
---

¿Algo en paralelo?:
- **Sí, mientras el atacante lee (sin Gradle):**
  1. La re-revisión corta de D, que ya está en marcha.
  2. El clon limpio de línea base del commit de partida (la app, después `--stop`, después `jvm-check`), con el mismo `flock /home/user/.gradle-run.lock`. Sirve para separar los fallos que ya había de los del ataque.
  3. El paquete del Abogado, solo lectura: los «Textos para el Abogado» de todos los informes (fila 14) y las filas 4, 5, 19, 41, 46 y 47, por constante y `archivo:línea`, en `.superpowers/…/abogado-final-paquete.md`. Antes del Abogado se pone al día con lo que hayan cambiado los arreglos.
  4. Opcional: `security-review` en solo lectura sobre el mismo commit; sus hallazgos entran en la ronda de arreglos del ataque.
- **No en paralelo:**
  - dos Gradle a la vez (lo impide el `flock`);
  - nadie edita `app/src/main` mientras dura el ataque, o R32 deja de cumplirse;
  - ni `code-review` alto, que debe ver los arreglos del ataque y es el más caro, ni el repaso de documentos, ni el Abogado, ni la fecha de la política.
- **El límite de uso es común a todos los agentes:** correr más a la vez no ahorra uso, solo tiempo de reloj.

Orden hasta el APK:
1. Cerrar la re-revisión de D. Comprobar que `git diff cad3ba5..<cierre de D> -- app/src/main jvm-check app/build.gradle.kts` sale vacío; si no, el ataque parte del cierre de D.
2. Controlador: el worktree `rt-2.0`, el cerrojo y el clon limpio de línea base. Desde aquí, el código queda congelado.
3. Ataque, fases 1 a 3, uniendo y subiendo `rt-2.0` al cerrar cada fase. En paralelo, lo de arriba.
4. El director da un Ruling por RT, por las filas 24 y 52, y por H-1 y H-2 si se confirman.
5. Ronda de arreglos (con los de `security-review` si corrió) y re-comprobación del atacante, hasta cerrar. Con 5 rondas, decide el dueño.
6. Clon limpio, sin `@Ignore` en `redteam/`, y el informe publicado `docs/auditorias/<fecha>-ataque-2.0.md`.
7. `code-review` alto (y `security-review` si no corrió), con una sola ronda de arreglos, el repaso de quien encontró cada cosa y un clon limpio. Si un arreglo toca la criptografía, los formatos, la importación, el bloqueo, el portapapeles, el QR o las bóvedas, el atacante repasa ese diff, para que R32 se cumpla al pie de la letra.
8. Auditoría completa (`experto-seguridad-cripto`, `auditoria-seguridad-movil`, `auditoria-seguridad`) en `docs/auditorias/<fecha>-auditoria-2.0.md`, con el apartado del Abogado vacío; después sus arreglos, el repaso y un clon limpio. Desde aquí solo cambian documentos: un cambio de código vuelve al paso 6.
9. Repaso final de documentos, con su revisión:
   - el README («Pruebas de la 2.0»), las notas de GitHub y de Play y el traspaso;
   - `pendientes.md`: cada fila con su destino, y abiertas solo las del móvil y las del dueño;
   - el estado de la 2.0 y sus decisiones en `CLAUDE.md`;
   - ningún nombre de modelo ni dato del dueño.
10. Revisión final del Abogado (`despacho-digital` más `privacidad-proteccion-datos`, `regulacion-digital-ue` y `propiedad-intelectual-ia`) sobre las filas 4, 5, 14, 19, 41, 46 y 47, todos los «Textos para el Abogado», la política, la ficha, el README, las notas, la guía («solo si hubo cambios»), `KDF_COST_KEPT`, los colores repetidos y la frase de `FinalLegalReviewTest`. Sus cambios se aplican con el visto bueno técnico (`CLAUDE.md`, punto 4), junto con los tests que fijan los textos, y se cierra con un clon limpio.
11. La fecha de la política (`docs/privacidad.html:22`, fila 49), justo antes de fusionar; después, el commit final y la subida.
12. La puerta del APK:
    - el clon limpio en verde en el commit final;
    - `versionName` 2.0.0;
    - el manifiesto y los permisos iguales a los de la 1.4.3;
    - ningún `@Ignore` nuevo;
    - ningún nombre de modelo ni dato del dueño en los archivos;
    - `pendientes.md` al día.
    Con eso, se pide al dueño el OK para el PR.
13. El dueño hace el PR a `main`, con build y jvm-check en verde (el artefacto con la clave de prueba es solo para el emulador). Fusiona sin etiqueta y lanza «Build APK» desde `main`. Antes de instalar, una copia de su bóveda con la 1.4.3, fuera del móvil. Aquí termina la tarea 12.
14. La tarea 11 (Maestro: filas 27, 35 y 48, entre otras) va en un segundo PR. La etiqueta `v2.0.0`, solo cuando el dueño dé por buena la prueba en el móvil.

Riesgos o dudas:
- **R32 frente a los cambios posteriores:** si `code-review`, `security-review` o la auditoría cambian código sensible, el ataque ya no ha sido «lo último». Lo cubre el repaso del atacante sobre ese diff (paso 7).
- **Fila 52:** un tope de 10 al leer puede rechazar una copia propia (`MergePlan.kt:376`; ninguna versión comprobó las etiquetas). Y aunque se pusiera, el `OutOfMemoryError` ocurre antes de `checkCopyEntries`. Lo probable es un arreglo de memoria antes de decodificar, con la excepción capturada fuera del hilo principal, más que un simple tope.
- **H-1, si se confirma:** el arreglo natural es que `settingsEditable` siga la regla de `canAddVault` (`AppContainer.kt:631-632`): «ninguna bóveda del móvil tiene archivo» en lugar de «la activa no tiene». Cambia lo que puede hacer el dueño con un hueco, así que pide un Ruling, no al dueño, salvo que toque «He olvidado».
- **H-2:** dar una clave de datos nueva al importar como bóveda nueva toca el formato al final del ciclo. Salvo que el ataque encuentre un camino real, lo prudente es corregir la afirmación del diseño y de los textos, y que el Abogado la mire.
- **Memoria:** un test que llegue de verdad al `OutOfMemoryError` mata el Gradle de los tests. Por eso el encargo pide medir y no provocar el fallo.
- **Pérdida del trabajo:** `.superpowers/` y el worktree se pierden si se cambia el contenedor. Por eso la copia del informe va en `rt-2.0`, y el controlador une y sube en cada fase.
- **Identidad:** el checkout tiene `user.name=Claude`. Un commit sin `-c` saldría con otra identidad, y arreglarlo pediría un *force push*, que está prohibido.
- **No ejecuté nada.** H-1, H-2 y el coste de la fila 52 salen de leer el código. Los confirma o descarta el atacante.

Revisión experto-seguridad-cripto:
- **Copias:** `vault/Vault.kt:412-417` acota la profundidad pero no el tamaño del árbol antes de decodificar. Las importaciones de otras apps sí lo estiman (R11a), y el único freno de una copia son sus 33 MiB por archivo (`vault/Backup.kt:51`). La decisión (a) pide el tope antes de reservar.
- **Clave de datos:** `vault/VaultStore.kt:1494-1504` vuelve a envolver la clave sin crear una nueva; solo `vault/Vault.kt:556-563` lo hace. Las garantías de AES-GCM con la AAD por versión (`Vault.kt:221-225`) siguen intactas, pero «cada bóveda tiene su propia clave» (diseño `:38`, `:62`) no es cierto en ese caso.
- **Ajustes de toda la app:** `settings/Settings.kt:443` junto con `ui/screens/VaultScreen.kt:489-498` es una posible regresión de COV2-1 con varias bóvedas (`AppContainer.kt:631-632` sí la cierra para crear bóvedas).
- **QR del wifi:** `tools/qr/WifiQr.kt:209-320` es un analizador propio frente al de ZXing. Es la clase CWE-436 (*Interpretation Conflict*), la misma que RT-1 de la 1.4.0.
- **Sin cambios de base:** ninguna primitiva, permiso ni host nuevo (parte D, `branch-review-compilacion.md`). El ataque sigue la regla de solo `javax.crypto` y Bouncy Castle, y no deja nada fuera del árbol de tests.

Revisión Ingeniero Móvil Senior:
- **Memoria en el móvil:** `ui/screens/ImportScreen.kt:308` captura `OutOfMemoryError` fuera del hilo principal; `importReplace` (`VaultStore.kt:1493-1530`) no lo hace. Con 128 a 256 MiB de *heap*, una copia hostil cierra la app, y una bóveda que entró en un móvil grande puede no abrir en uno pequeño.
- **Claves de Compose:** las hechas con datos de la bóveda (`ui/screens/VaultList.kt:543`, `:556`, `:571`; `VaultOrganize.kt:402`, que usa `distinctBy`, frente a `VaultList.kt:543`, que no; `EntryHistoryScreen.kt:94`) son la superficie de «cerrarse en cada apertura» de T4 C-1.
- **Ventanas:** cada `Dialog`, menú o `Popup` necesita `FilterObscuredTouches()` (`android/SecureWindow.kt:36-52`), y su test de fuente pide al menos 15 ventanas (`PlatformSourcesTest.kt:566`). Los diálogos de «Cristal» y los menús «⋮» nuevos tienen que estar dentro.
- **Ciclo de vida:** `MainActivity.kt:105-119` (`onStop` → `leftApp`, sin contar los cambios de configuración) y `tools/qr/QrPendingText.kt:39-58` son las únicas puertas del borrado al salir. Las pantallas nuevas con secretos tienen que colgar de ahí o del bloqueo.
- **Compilación:** `jvm-check/build.gradle.kts:20-22` compila `redteam/` y excluye `ui/`, así que se itera ahí, con menos memoria. Con `--max-workers=1`, `--no-daemon` y `flock` se evita el exit 137.

Modelo: el director (esfuerzo extra).
