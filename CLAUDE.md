# Clavero (antes «Seguridad»): instrucciones para Claude

Clavero es una app Android (Kotlin y Compose) de seguridad y criptografía: bóveda, 2FA, cifrado de texto y archivos, y herramientas. El diseño está en `docs/superpowers/specs/` y los planes en `docs/superpowers/plans/`.

## Skills: siempre juntas

Todo trabajo en este repositorio sigue el traspaso vigente en `docs/superpowers/handoff/` y combina, en cada fase de construcción, **los plugins Superpowers e Ingeniero Móvil Senior**, **la skill del proyecto `experto-seguridad-cripto`** (`.claude/skills/experto-seguridad-cripto/`) y **todo lo que tenga que ver con la auditoría de la app** (`auditoria-seguridad-movil`, `auditoria-seguridad`, `security-review`, `code-review`):

1. **superpowers** marca el proceso: brainstorming, plan, desarrollo por subagentes o en línea con TDD, revisión y verificación.
2. **experto-seguridad-cripto** aporta en cada fase el criterio de seguridad. Su sección «Trabajo conjunto con superpowers» dice qué añade en cada fase.
3. **Ingeniero Móvil Senior** aporta el criterio de Android, Kotlin y Compose:
   - `desarrollo-movil-senior` para la arquitectura, el ciclo de vida, la concurrencia y el rendimiento;
   - `seguridad-criptografia-movil` cuando se tocan Keystore, StrongBox, la biometría o el cifrado;
   - `auditoria-seguridad-movil` (OWASP MASVS) en las revisiones.
4. **Abogado Digital** no interviene en cada tarea: hace **una sola revisión al final**, con todo terminado y antes de publicar.
   - Revisa todos los textos de la interfaz y de la documentación, las licencias y, sobre todo, la **política de privacidad y seguridad** (`docs/privacidad.html`) y la ficha de Google Play.
   - Comprueba que cumplen la ley española, la europea y la internacional que aplique. La app no se publica en Francia en Google Play.
   - Usa `despacho-digital`, más `privacidad-proteccion-datos`, `regulacion-digital-ue` y `propiedad-intelectual-ia` según el caso.
   - Sus cambios se aplican siempre que `experto-seguridad-cripto` y el Ingeniero Móvil Senior no vean un problema técnico o de seguridad (por ejemplo, que rompa un formato, que el texto afirme algo falso sobre el código o que cambie el comportamiento). Si lo ven, se busca un texto que satisfaga a todos y la decisión queda apuntada.
5. Todo implementador y todo revisor, incluidos los subagentes:
   - lee `SKILL.md` de `experto-seguridad-cripto` y las referencias que toquen su tarea, y las skills de Ingeniero Móvil Senior que correspondan;
   - cierra su informe con las secciones **«Revisión experto-seguridad-cripto»** y **«Revisión Ingeniero Móvil Senior»**, con la evidencia `archivo:línea`;
   - si la tarea tiene textos, los apunta en **«Textos para el Abogado Digital (revisión final)»** para que lleguen a esa revisión.
6. Antes de fusionar en `main`, sobre la rama:
   - `code-review` y `security-review`;
   - la auditoría completa con `experto-seguridad-cripto`, `auditoria-seguridad-movil` y `auditoria-seguridad`;
   - la revisión final del Abogado Digital (punto 4), con sus cambios aplicados.

## Estado actual: Clavero 1.4.0, en desarrollo (8-11-2026)

**Nombre:** la app se llama **Clavero** desde la 1.3.0 (antes «Seguridad»). Cambian el nombre visible, los textos, la política, la ficha, el README y los nombres de los APK publicados (`Clavero-<etiqueta>.apk`). **No cambian**, y `AppNameTest` lo comprueba:
- el `applicationId` `io.github.egt17baa.seguridad` y el paquete `app.seguridad`;
- la autoridad del `FileProvider` y el alias de la clave del Keystore;
- la etiqueta propia del portapapeles «Seguridad»;
- los nombres de los formatos (`seguridad-vault`, `seguridad-backup`, `SG1:`…) y los prefijos de las copias (`seguridad-auto-`, `seguridad-boveda-` y, desde la 1.4.0, `seguridad-antes-de-importar-`);
- el nombre del repositorio, que el dueño puede cambiar si quiere.

**Fase:**
- **Publicadas:** la 1.3.0 (etiqueta `v1.3.0` en `b6ea21b`) y la **1.3.1**, la última: etiqueta `v1.3.1` en `889d595`, con `Clavero-v1.3.1.apk` y su `.sha256` en *Releases* (2-11-2026), firmada con la clave de subida (huella `746398ea…9df9e`), `versionCode` 94 y los mismos permisos que la 1.2.2. El dueño la probó en el móvil sin problemas (3-11-2026). Su historia: registro `docs/superpowers/handoff/sdd-1.3.1/progress.md`, auditoría `docs/auditorias/2026-11-01-auditoria-1.3.1.md` y traspaso `docs/superpowers/handoff/2026-11-01-clavero-1.3.1-traspaso.md` (la 1.3.0, en `sdd-1.3.0/` y `docs/auditorias/2026-10-31-auditoria-1.3.0.md`).
- **La 1.4.0** añade «Importar de otra app» (fusiona en la bóveda, con vista previa y una copia antes), tres herramientas («Laboratorio de hashes», que sustituye a la huella SHA-256 de archivos, «Laboratorio clásico» y «Repartir un secreto», SLIP-0039) y «Comprobar una copia»; pone todas las operaciones de `BiometricVault` en una sola fila y pasa a AGP 9.5.0 (R8 9.5.28) y Gradle 9.6.0. No cambia ningún formato de Clavero ni añade permisos, componentes exportados, hosts ni dependencias del APK.
  - Diseño `docs/superpowers/specs/2026-11-03-clavero-1.4.0-design.md` (aprobado el 3-11-2026) y plan `docs/superpowers/plans/2026-11-03-clavero-1.4.0.md`.
  - Base: `main` en `7c8dea7` (PR #16). Las tareas 1 a 10 se hicieron en la rama `ccr-a1a6eabb-765f9g` (hasta `a1f7771`); el trabajo se reanudó el 8-11-2026 en la rama **`ccr-d1e75160-p5f4wc`**, desde `a1f7771`, con la revisión de la tarea 10, las tareas 11 y 12 y la revisión final.
  - Registro `docs/superpowers/handoff/sdd-1.4.0/progress.md` (en la misma carpeta, los informes y las revisiones de cada tarea), auditoría `docs/auditorias/2026-11-08-auditoria-1.4.0.md` (sus secciones de la revisión final están por llenar), informe del ataque `docs/auditorias/2026-11-08-ataque-1.4.0.md` (R39) y traspaso `docs/superpowers/handoff/2026-11-08-clavero-1.4.0-traspaso.md`.
- `main` está protegida desde el 1-11-2026: todo cambio entra por un PR, con **build** y **jvm-check** en verde.

| Paso de la 1.4.0 | Estado |
|---|---|
| Tarea 1: `BiometricVault` en una sola fila (`KeyOpsQueue`) | Terminada y revisada |
| Tareas 2 a 4: Laboratorio de hashes, Laboratorio clásico y Repartir un secreto | Terminadas y revisadas |
| Tarea 5: Comprobar una copia | Terminada y revisada |
| Tareas 6 a 10: los lectores (CSV, JSON, cifrados), el plan de fusión con la copia previa y la pantalla de importar | Terminadas y revisadas |
| Tarea 11: AGP 9.5.0 y el R8 nuevo | Terminada y revisada |
| Tarea 12: `versionName` 1.4.0 y los documentos | Terminada y revisada: su ronda de arreglos (`4929ef6`) y su repaso (`26f30d4`, `e600feb`) |
| Primer ataque sobre toda la app (decisiones (d) y (f), R32) y sus arreglos | Hecho: 3 hallazgos de severidad baja (RT-1 a RT-3), arreglados en `0089b90` con sus tests en verde; informe `docs/auditorias/2026-11-08-ataque-1.4.0.md` (R39) |
| Ronda final de arreglos (las líneas «Final fix wave» del registro) | Hecha (`c70a0f5`) |
| Flujos de Maestro para las pruebas en el móvil (decisión (g)) | Hechos (`93b3f71`), sin ejecutar aquí: `.maestro/README.md` |
| Revisión de la rama entera, en tres partes (A importar, B herramientas y bóveda, C compilación y documentos) | Hecha (`6e43a08`, `96c1d6e`, `e33a4d3`) |
| Su ronda de arreglos | Hecha: el código (`2ddc305`, `45c2660`; R37 a R40, `branch-review-fixes-code-report.md`) y los documentos (`branch-review-fixes-docs-report.md`) |
| Los repasos de esa ronda | Hechos: A (`55ddb62`, `2a5ae49`), B (`cf3e574`, `970359e`) y C (`41a585a`, con sus dos menores arreglados) |
| Segundo ataque, sobre el código final, después de esa ronda (R32), con sus arreglos y su repaso | Ataque hecho (`59725c8`, unido en `865d353`): RT-4 a RT-7 (3 bajos y 1 informativo), decisiones R41 a R44; arreglados en `bf04f45`; falta su revisión y el repaso del equipo rojo. Por dónde seguir: `docs/superpowers/handoff/2026-11-09-clavero-1.4.0-continuar.md` |
| `code-review`, `security-review` y la auditoría completa | Pendientes |
| Revisión final del Abogado Digital, con sus cambios | Pendiente |
| PR a `main`, APK firmado, prueba del dueño en el móvil y etiqueta `v1.4.0` | Pendientes |

**Decisiones de la 1.4.0** (el detalle, en `docs/superpowers/handoff/sdd-1.4.0/progress.md`, en las líneas «Owner decision» y «Ruling»):
- **(a) Entradas enormes (dueño, 3-11-2026):** en toda entrada de datos, topes antes de leer o reservar, lectura por partes con búferes acotados, nada de leer un archivo entero sin tope ni de analizar sin límite de profundidad o de número, y el trabajo largo fuera del hilo principal y cancelable.
- **(b) Carreras entre comprobar y usar (dueño):** se usa lo que se comprobó (una sola lectura), un bloqueo o un borrado a mitad no deja un resultado tardío, y los secretos se comparan en tiempo constante.
- **(c) Lo importado es dato hostil (dueño, 4-11-2026):** solo se muestra como texto plano y se guarda tal cual; nunca se ejecuta, se interpreta, se abre ni se convierte en enlace o en `Intent`; sin deserializar tipos del archivo.
- **(d) Ataque (dueño):** un agente atacante sobre **toda** la app (cuándo corre: R32, abajo), con pruebas de concepto como tests que fallan en el repositorio; cada hallazgo se arregla y su test pasa a verde y se queda en la suite. Informe en `docs/auditorias/2026-11-08-ataque-1.4.0.md` (R39).
- **(f) Agente atacante dedicado (dueño, 8-11-2026):** amplía (d): ataca cada entrada (archivos y copias importados, `SG1:` y `.sgenc`, QR, *intents* y componentes exportados, portapapeles, otras apps y carpetas compartidas, la copia del sistema, la red y TLS, el bloqueo y la huella, la memoria y la muerte del proceso) con pruebas de concepto como tests; corre en la revisión final, antes del PR, y el mismo agente vuelve a comprobar cada arreglo.
- **Cuándo corre el ataque (R32):** empezó el 8-11-2026 en paralelo con la revisión de la tarea 12, en su propio *worktree*, para aprovechar la espera; vuelve a atacar después de la ronda de la revisión de la rama, así que lo último que ataca es el código final, y después vienen `code-review`, `security-review`, la auditoría completa y el Abogado.
- **(g) Pruebas en el móvil (dueño, 8-11-2026):** el dueño usa su plugin «Android qa libre» (Mobile MCP y Maestro) en su ordenador, con el emulador de Android Studio o el móvil por USB, no en el contenedor de la nube (sin `/dev/kvm` ni emulador). Claude prepara como flujos de Maestro en `.maestro/` las pruebas del README que se puedan automatizar: hecho (`93b3f71`), 25 flujos sin ejecutar aquí; `.maestro/README.md` dice cuáles borran datos, cómo se ejecutan y qué queda a mano.
- **(h) Maestra nueva en *leet* o en ancho completo (dueño, 8-11-2026, RT-3):** amplía CRY-A01. Una maestra nueva que es una sola palabra conocida en *leet* (0→o, 3→e, 4/@→a, 5/$→s, 7/+→t, 8→b, 6/9→g, 1/!/| como i o l; cifras y símbolos solo alrededor) o en letras de ancho completo (NFKC) se rechaza con `MASTER_TOO_WEAK`, sin cambiar el texto. Solo una maestra nueva.
- **(e) Sin exportaciones reales (dueño, 4-11-2026):** los archivos de prueba son sintéticos, escritos desde la documentación pública; los cifrados, con un generador independiente en Python (`scripts/import-fixtures/gen_encrypted.py`). El dueño prueba con sus apps después de publicar; lo que falle va a la 1.4.1. Lo que falta confirmar está en «Pendiente de confirmar» de `sdd-1.4.0/task-10-report.md`.
- **Nombres de otras apps (R2, R7, R8):** solo en literales de `imports/ImportTexts.kt` y en el README, con la línea «Los nombres de otras apps son marcas de sus titulares. Clavero no tiene relación con ellos.»; nunca en la ficha de Play ni en sus «Novedades». Las notas de la versión de GitHub siguen la regla del README: nombres solo para decir qué formato se lee y con la línea de marcas (R31). La línea de la ficha de la 1.1 con «Google Authenticator» la decide el Abogado (R30). Los documentos técnicos (registro, auditoría, traspaso, este archivo) pueden nombrarlas. `ImportTextsTest` lo comprueba, con una lista exacta de literales anteriores permitidos (los «Google Authenticator» de la 1.1, «la copia de Google» y la atribución de Guava). El aviso de SLIP-0039 no nombra marcas y dice «cartera» (R3, R3a).
- **SLIP-0039 (R4):** la lista desde `assets`, PBKDF2 de Bouncy Castle, exponente de iteraciones de 0 a 8, y al recomponer secretos de hasta 64 bytes.
- **Comprobar una copia (R5, R6):** comparte con «Importar copia» sus comprobaciones (`checkCopyEntries`, `attachmentInCopy`), en el mismo orden; llama a `store.touch()` como «Importar copia», que puede subir la marca del reloj (A11), y sus textos no dicen que no toque nada.
- **Mostrar sin suplantar (R9, R10):** los títulos, usuarios y URL se guardan tal cual; al mostrarlos en listas, cabeceras y el informe de importar se quitan los controles, las marcas de dirección y los invisibles (`plainReportText`), en cualquier entrada.
- **JSON (R11a, R11c, R11b):** antes de analizarlo se estima el tamaño del árbol (con el crecimiento de las listas y los mapas) y se rechaza por encima de 48 MiB (`JSON_MAX_TREE_BYTES`); `readImport` va fuera del hilo principal y un `OutOfMemoryError` solo se captura ahí, como última defensa.
- **Contraseña de una exportación (fin de R13a):** se pide; una equivocada se puede volver a escribir y nada se reintenta solo.
- **Lo que cabe (R12, R14):** como mucho 20 000 elementos por archivo, contando los que no entran; la bóveda resultante tiene que caber en una copia (adjuntos + 2 ≤ `BACKUP_MAX_ENTRIES`, y el tamaño codificado exacto).
- **A6 y las copias de antes de importar (R15):** A6 también borra las copias de antes de importar que la app tiene apuntadas en la carpeta; sus textos y la política lo dicen. La copia guardada con el diálogo no está en la lista y no se borra.
- **Un plan de importar viejo no se aplica (R16):** el plan se hace sobre una instantánea (entradas y revisión) tomada bajo el mutex, y se rechaza si la bóveda cambió; un fallo al guardar da un texto fijo.
- **Pantalla de importar (R17, R37):** confirmada la importación, salir de la pantalla no la corta. Un bloqueo antes de empezar a guardar la cancela (durante la copia, la copia se rechaza; entre la copia y el guardado, `applyImport` da `Stale`): no se importa nada y la copia, si se hizo, se queda. Un guardado ya empezado termina. El resultado no se enseña después del bloqueo; con la carpeta de la copia automática sin permiso sale su error y no se pasa al diálogo (sin copia no se importa).
- **Etiquetas (R18):** como mucho 100 etiquetas nuevas por importación (`IMPORT_MAX_NEW_TAGS`); la vista previa enseña 20 y «y N más».
- **Formatos (R19, R21, R22):** Bitwarden: su CSV, su JSON en claro y «Protegida con contraseña»; «Cuenta restringida» y el `.zip` tienen su texto. Una página HTML se reconoce antes de leerla y nunca se muestra; el TXT de Aegis entra como lista de `otpauth://`; Google Authenticator tiene un texto de ayuda; el CSV de Edge se lee con y sin la columna de notas.
- **Argon2id de Bitwarden (R23):** su propia llamada a Bouncy Castle, porque la sal es un SHA-256 de 32 bytes (`Crypto.argon2id` pide 16), con el mismo `checkSane` antes.
- **«Pendiente de confirmar» (R25):** la lista de lo que solo confirma una exportación real vive en `sdd-1.4.0/task-10-report.md`; el código y `import/README.txt` apuntan ahí.
- **Aegis (R24):** solo se prueba la primera ranura de contraseña (una derivación por intento).
- **URI del CSV de Bitwarden (R26, R27):** la celda `login_uri` se parte solo por una coma seguida de un esquema con `://`; la primera es la URL y las demás van a las notas.
- **AGP 9.5.0 (R28):** se queda en 9.5.0 aunque haya salido la 9.5.1. EPL-2.0 (JUnit Platform) y las partes GPL-CE/LGPL de R8 son solo de compilación y no van en el APK.
- **Duplicados (R34, R36; RT-1 y RT-2):** el título y el usuario se comparan con `compareKey` (lo que enseña `plainReportText`, en NFC y con los espacios seguidos como uno): el título sin distinguir mayúsculas y el usuario distinguiéndolas. El host de la URL lo lee `parseLink`, como un navegador (`\` cierra la autoridad en un enlace web). Lo guardado no cambia. Sustituye a «sin espacios al principio ni al final» del diseño, que tiene su nota de corrección. La fila de un duplicado enseña el usuario y el host que traería «Reemplazar» (`duplicateSubline`).
- **Etiqueta del nombre (R35):** «Nombre (p. ej. «Correo» o «Cartera»)», sin nombres de productos.
- **Un solo cerrojo de derivación (R38, R40):** `crypto/KdfLock` hace que nunca corran dos derivaciones a la vez en todo el proceso: las Argon2id de `VaultStore`, «Comprobar una copia», «Importar de otra app» con contraseña, «Cifrar texto» y la clave de «Cifrar archivo» (no el cifrado del flujo). Una derivación cancelada suelta el cerrojo cuando termina por dentro.
- **Informe del ataque (R39):** `docs/auditorias/2026-11-08-ataque-1.4.0.md`, escrito desde `sdd-1.4.0/redteam-report.md` con el estado de cada hallazgo; el segundo ataque (R32) se añade ahí. Los `redteam-*.md` siguen como archivos de trabajo.
- **«Sin cifrar» (R33):** la pantalla de importar y la oferta de quitar el archivo dicen que el archivo exportado está sin cifrar solo si se exportó sin contraseña (`IMPORT_NOT_PROTECTED`, `IMPORT_REMOVE_EXPLAIN`, fijados en `ImportTextsTest`); el README, la ficha y las notas dicen lo mismo.
- **jvm-check (R29):** siempre con el wrapper, `./gradlew -p jvm-check test` (Gradle 9.6); un Gradle 8 instalado falla la verificación de dependencias, y las sumas `-gradle813` no se vuelven a añadir.
- **`BiometricVault` (tarea 1):** todas sus operaciones van en `KeyOpsQueue`, sobre un dispatcher de un solo hilo, cada una entera (activar: de crear la clave a `doFinal` y las preferencias, con el diálogo dentro); `disable` es `suspend` y sus seis llamadas lo esperan; un `disable` cancela la activación en curso. La fila no es reentrante.

**Decisiones del dueño y del controlador de la 1.3.x que siguen vigentes** (el detalle está en `docs/superpowers/handoff/sdd-1.3.0/progress.md` y `sdd-1.3.1/progress.md`, en las líneas «Ruling» y «Owner decision»):
- **Detalles menores:** se arreglan todos, no se aplazan.
- **Nombre de salida de Limpiar foto:** `copia-de-foto.<ext>`, y no `foto-limpia`, porque «limpio» es una palabra prohibida. Tachar sigue con `imagen-tachada.<ext>`.
- **Estado que sobrevive al giro:** la imagen y los recuadros de Tachar, la copia de Limpiar foto, el PIN, los códigos, las respuestas, el diálogo de A6, el texto pendiente del QR y el formulario de una entrada viven en un ViewModel o en `AppContainer`, **nunca** en `rememberSaveable` ni en un `Bundle` si son secretos o imágenes. Se pierden si Android cierra la app, y eso se acepta.
- **«PIN y códigos»:** el PIN, los códigos y las respuestas se borran:
  - al volver atrás;
  - con cualquier bloqueo de la bóveda mientras la app está en otra ventana o en otra app;
  - al bloquear la bóveda con la app en primer plano;
  - con el bloqueo por inactividad, solo si la pantalla no se ha usado durante `max(bloqueo, 60 s)`;
  - al volver después de ese tiempo fuera.

  Los sitios y las preguntas se conservan.
- **Huella (A11):** se guarda la hora más alta vista. La huella pide la maestra si el reloj va más de 5 min por detrás. Un desbloqueo con la maestra baja esa marca, pero una importación solo la baja después de guardar la bóveda.
- **Importar (A8):** si la copia tiene un Argon2id más débil, se vuelve a envolver con el máximo, eje por eje, entre el de la copia y el del perfil (también el paralelismo). Nunca se baja el coste. Volver a envolver en un desbloqueo normal cuando el perfil cuesta más es lo previsto.
- **Copias tras cambiar la maestra (A6):** la petición de reemplazo se guarda en `Settings.autoBackupReplacePending`, con la lista de destinos de ese momento (`autoBackupReplaceTargets`). Primero se hace la copia nueva y después se borran solo las de la lista propia. La petición sigue hasta borrar todos los destinos; cada fallo distinto se avisa una vez. «Se volverá a intentar en la próxima copia.» solo aparece donde hay un reintento real. Una bóveda nueva siempre olvida la lista anterior.
- **Portapapeles en Android 8 a 12 (A14):** el aviso se acepta una vez, y la copia pendiente se rechaza si la bóveda se bloquea entre el aviso y la aceptación.
- **Nombres de archivo (A16b):** se cortan por puntos de código, con un máximo de 255 bytes al escribir y guardar. Al leer se siguen aceptando hasta 1024 bytes (`MAX_FILE_NAME_BYTES`). Un nombre recibido se corta a 120 puntos de código.
- **Contraseñas (F5):** `PasswordUse.EXISTING` acepta lo que aceptaba Bouncy Castle 1.80 para abrir lo de antes; `PasswordUse.NEW` rechaza el texto que no es Unicode válido (`PASSWORD_NOT_TEXT`).
- **Enlaces en el QR (lote MN):** un enlace con datos de usuario (`usuario@` o `usuario:clave@`) se trata como secreto: se borra al salir de la app y al bloquear.
- **Firma en CI (opción A):** `build-firma` y `play-bundle` solo corren con una etiqueta `v*` o a mano desde `main`, en el entorno `firma`. Un push a `main` solo compila con la clave de prueba. La huella del certificado de subida se comprueba siempre (`746398ea…9df9e`).
- **Publicación:** «No recoge ni comparte datos» en Google Play; desarrollador «Egt17baa»; nunca donaciones; fuera de Francia (con ultramar), Rusia y Bielorrusia.
- **Prueba del APK firmado:** se fusiona en `main` sin etiqueta; el dueño lanza Actions → Build APK → Run workflow desde `main`, lo aprueba y descarga `Clavero.apk`. Los arreglos van de la rama a `main`. La etiqueta de la versión (`v1.3.0`, `v1.3.1`) solo cuando la prueba esté bien.
- **Protección de `main`:** el dueño la pone justo después de la fusión y antes de la etiqueta, con la lista «Entorno de firma» del README.
- **Prueba de palabras prohibidas:** `SourceTextsTest` revisa todas las cadenas de `app/src/main/java`, no solo `ToolTexts.kt`.
- **Fusiones con el botón de GitHub:** firman con el correo de la cuenta del dueño. Las de los PR #9 a #14 llevan su correo personal; desde el 3-11-2026 la cuenta lo tiene privado, así que las siguientes usan la dirección `noreply` de GitHub. Las seis que ya existen no se reescriben: haría falta un force-push a `main`, que rompe las etiquetas y las copias descargadas.

**Más decisiones del 1-11-2026 (1.3.1), siguen vigentes:**
- **«Importar copia»** deja elegir cualquier archivo y mira su cabecera antes de pedir la contraseña (`mayBeVaultImport`).
- **Un archivo restaurado se guarda con el tipo de su extensión** (`restoredFileMime`): imagen salvo SVG, vídeo, audio, texto plano y PDF; lo demás, `application/octet-stream`.
- **Zoom en «Tachar»:** un segundo dedo antes de que el primero se mueva es zoom, y una cancelación del sistema no deja ningún bloque.
- **Los avisos de R8 sobre los metadatos de Kotlin:** resueltos en la 1.4.0 con AGP 9.5.0 y su R8 9.5.28 (tarea 11: de 78 avisos a 0). En la 1.3.1 se había probado a fijar el R8 9.1.31 y el dueño lo quitó (`94ddb7f`).

**Decisiones de la 1.3.1 (1-11-2026), siguen vigentes:**
- **CriptoPanel (dueño):** Clavero deja de leer `CP1:`, `.cpenc` y `criptopanel-vault`, y el nombre sale de la interfaz. Lo de CriptoPanel recibe el mismo error que cualquier dato que no es de Clavero. El dueño no tenía ninguna bóveda en CriptoPanel.
- **Maestra nueva (CRY-A01, dueño):** solo al crear la bóveda y al cambiar la maestra. Abrir la bóveda, la huella e importar una copia no cambian (la contraseña de una copia sigue pidiendo solo 10 caracteres). Una maestra nueva necesita:
  - 10 caracteres contados de las dos maneras: unidades UTF-16 y puntos de código en NFC sin caracteres de formato (Cf);
  - una estimación de 50 bits o más (`WEAK_BITS`, la de «Salud»), que se mide de esas dos maneras y se queda con la menor;
  - que no sea una sola palabra conocida: su núcleo (en NFKC, en minúsculas, sin acentos y solo letras) no está en la lista común de «Salud» ni en las listas EFF, BIP-39 en inglés y BIP-39 en español, y tampoco lo está la palabra escrita en *leet* con cifras y símbolos alrededor (decisión (h) de la 1.4.0, `isKnownWordInLeet`, con un tope de 128 puntos de código). Así, «P4ssw0rd2024!» y «ｐａｓｓｗｏｒｄ12345» se rechazan, con el mismo `MASTER_TOO_WEAK`.

  Es una estimación: «Barcelona1» y dos palabras juntas pasan. Las listas se leen una vez por proceso, fuera del hilo principal, y `checkAssets` falla si falta alguna en el APK.
- **Huella (CRY-A02):** se desactiva solo cuando su clave no autentica la bóveda (`StaleBiometricKey`, con el texto `FINGERPRINT_STALE`, que dice «Puede deberse a» un cambio de la maestra, una copia importada o datos dañados, porque AES-GCM no distingue esos casos). Crear la bóveda y borrarla desactivan la huella antes; cambiar la maestra e importar una copia, justo después de guardar. Una clave que autentica pero cuyas entradas no se decodifican da el error de siempre y conserva la huella. Con un archivo que dice su versión (2 o 3), primero van la regla de los 7 días y la del reloj (A11), antes de usar la clave, como en la 1.3.0. Sin versión, primero la clave, que es la que dice qué versión es.
- **Un archivo que esta versión no abre** (otro formato, o una versión 1, 4…): la huella no se ofrece ni prueba la clave (`BiometricRefusal.UNSUPPORTED`, sin aviso), y la maestra dice «No es una bóveda compatible» en lugar de «Contraseña incorrecta».
- **TOTP (CRY-B02):** fuera de 6 a 8 dígitos, de 10 a 120 s o de SHA1, SHA256 y SHA512, el código se rechaza, no se cambia. El editor y el Inspector de QR lo dicen, y el Inspector no ofrece «Añadir a la bóveda». La lista dice «2FA no admitido» y la ficha explica por qué. Solo un parámetro que falta toma el valor por defecto.
- **Revisión del Abogado Digital (1-11-2026):** «se puede publicar, con cambios». Con los hechos que da por ciertos (particular sin ánimo de lucro, gratuita, sin anuncios ni donaciones, sin recibir datos), ninguna norma exige cambios. Se aplicaron O-1 (`FINGERPRINT_STALE`, obligatorio por la regla de veracidad de este proyecto), R-1 (la nota de CriptoPanel del README, con la salida por la 1.3.0), R-2 (el ejemplo de User-Agent de la política), R-3 (la nota de la EFF en Licencias) y R-4 (una frase de la auditoría). Las notas de la versión de GitHub y de Play no están en el repositorio: deben decir que la 1.3.1 deja de abrir los formatos de CriptoPanel, no presentar la regla de la maestra como algo que hace fuerte la contraseña y no usar palabras prohibidas (borrador en el traspaso de la 1.3.1).
- **`BiometricVault` en un solo hilo:** hecho en la 1.4.0 (tarea 1, arriba).
- **Cada subida de `targetSdk`:** se repite en el móvil «¿Filtrada?» y «Comprobar filtraciones» con red. La segunda validación TLS de CRY-B01 no recibe el OCSP grapado ni las SCT de la extensión TLS, y podría rechazar de más si Android llega a exigir Certificate Transparency (N-04).

**Pendientes, del dueño** (`docs/PUBLICAR.md` §3.7 y README, «Entorno de firma»):
1. **1.4.0, después de fusionar el PR en `main`:** *Actions → Build APK → Run workflow* desde `main`, aprobar **build-firma** y descargar `Clavero.apk`; hacer las pruebas P-4 a P-26 del README («Pruebas de la 1.4.0») en su ordenador, con «Android qa libre» y los flujos de `.maestro/` (decisión (g); `.maestro/README.md` dice cómo se ejecutan, cuáles borran datos y cuáles quedan a mano; P-19 va con el APK firmado); poner la fecha de `docs/privacidad.html` el día que se publique (GitHub Pages la publica al fusionar en `main`); poner la etiqueta `v1.4.0` desde Termux cuando la prueba esté bien; pegar las notas de la versión en GitHub (y en Play), con el borrador del traspaso de la 1.4.0, y subir el AAB de esa ejecución si publica en Google Play. Los pasos, en el traspaso de la 1.4.0, §3.
2. **1.4.0, exportaciones reales (decisión (e)):** después de publicar, importar una exportación de cada app que use (P-1 a P-3); lo que no entre bien va a la 1.4.1.
3. **1.3.1, las notas de la versión:** la release `v1.3.1` de GitHub se publicó sin notas. Hay que pegarlas a mano (*Releases → v1.3.1 → Edit*), porque las herramientas de Claude no pueden editar una release. El texto, revisado con la guía del Abogado Digital, está en el traspaso de la 1.3.1, §3, punto 5. Lo mismo en Play, si publica allí.
4. **CriptoPanel:** retirar su app o su repositorio, si quiere; es decisión suya.
5. **Borrar los secretos `SEGURIDAD_*` del repositorio**, si ya están en el entorno `firma`.
6. **La comprobación del paso 7** con la rama `claude/prueba-secretos`, que debe dar `VACIO`.
7. **Buscar las marcas** CLAVERO, CLAVIS y CLAVISTER en la clase 9 (TMview, OEPM, WIPO), y registrarla si quiere.
8. **Las respuestas de Play Console** y subir el AAB `Clavero-google-play-aab` de la ejecución de la etiqueta (de la 1.3.1, la #94; de la 1.4.0, la de `v1.4.0`), si publica en Google Play.
9. **Una copia propia de la clave de subida y de sus contraseñas** (el `.jks`), fuera del móvil y en un gestor de contraseñas, si no la tiene: la CI firma con los secretos de GitHub, que no se pueden leer. Si se perdiera, `docs/PUBLICAR.md` §4 explica cómo cambiarla.
10. **Las pruebas en el móvil que siguen pendientes en el informe de la 1.3.0 (§4):** `adb`, TalkBack, la ventana flotante de MagicOS, el teclado de Honor y la colaboración con el PC.

## Reglas del proyecto

- **No romper datos existentes:** los formatos `SG1:`, `.sgenc`, `seguridad-vault` y `seguridad-backup` se leen para siempre. Desde la 1.3.1, por decisión del dueño (1-11-2026), los de CriptoPanel (`CP1:`, `.cpenc` y `criptopanel-vault`) no se leen y reciben el error genérico. Cualquier cambio de formato lleva tests con datos reales anteriores.
- **Solo primitivas estándar** (`javax.crypto` y Bouncy Castle). Nada de criptografía propia.
- **Solo dependencias gratuitas y de licencia permisiva** (Apache-2.0, MIT, BSD, ISC, y CC BY para datos). Nada propietario ni copyleft (GPL, AGPL ni LGPL): la app se publica con licencia **Apache-2.0** y cualquier dependencia tiene que ser compatible con ella.
- Red: solo `api.pwnedpasswords.com`, con el cliente que bloquea los certificados de eMudhra. Sin logs con datos. `FLAG_SECURE` en toda la app.
- Interfaz en español.
- **Nombres de otras apps** (1.4.0): en el texto visible de la app, solo en la pantalla de importar (`imports/ImportTexts.kt`); en lo publicado, solo en el README y en las notas de la versión de GitHub (R31), para decir qué formato se lee, sin logotipos y con la línea de marcas; nunca en la ficha de Google Play ni en sus «Novedades». **Nunca se copia código**, tests ni archivos de prueba de otras apps ni de otros importadores: los lectores se escriben desde la documentación pública.

## Compilar y probar

```bash
export ANDROID_HOME=/opt/android-sdk     # en las sesiones en la nube
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets --no-daemon --max-workers=1
./gradlew -p jvm-check test --no-daemon --max-workers=1   # el wrapper (Gradle 9.6), nunca un gradle instalado (R29): un Gradle 8 falla la verificación de dependencias
```
