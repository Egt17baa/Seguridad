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

## Estado actual: Clavero 1.3.0, publicada (1-11-2026)

**Nombre:** la app se llama **Clavero** desde la 1.3.0 (antes «Seguridad»). Cambian el nombre visible, los textos, la política, la ficha, el README y los nombres de los APK publicados (`Clavero-<etiqueta>.apk`). **No cambian**, y `AppNameTest` lo comprueba:
- el `applicationId` `io.github.egt17baa.seguridad` y el paquete `app.seguridad`;
- la autoridad del `FileProvider` y el alias de la clave del Keystore;
- la etiqueta propia del portapapeles «Seguridad»;
- los nombres de los formatos (`seguridad-vault`, `seguridad-backup`, `SG1:`…) y los prefijos de las copias (`seguridad-auto-`, `seguridad-boveda-`);
- el nombre del repositorio, que el dueño puede cambiar si quiere.

**Fase:** la 1.3.0 está **publicada**: etiqueta `v1.3.0` en `b6ea21b` (`main`), con `Clavero-v1.3.0.apk` en *Releases*, firmado con la clave de subida. La rama de trabajo fue `ccr-a1a6eabb-765f9g`. `main` está protegida desde el 1-11-2026, así que todo cambio entra por un PR, con **build** y **jvm-check** en verde. El registro de la ejecución está en `docs/superpowers/handoff/sdd-1.3.0/progress.md` y el informe de la auditoría en `docs/auditorias/2026-10-31-auditoria-1.3.0.md`.

| Paso | Estado |
|---|---|
| Tareas 1 a 15, CU-A y CU-B | Terminadas y revisadas |
| Tarea 16: auditoría completa (33 hallazgos, lotes F1 a F7) | Terminada y revisada |
| Tarea 16: `code-review` y `security-review` de la rama (lote CR) | Terminadas y revisadas |
| Tarea 16: nombre nuevo «Clavero» y aviso único del cambio de nombre | Terminado y revisado |
| Tarea 16: revisión final del Abogado Digital (lote AB) | Terminada, con sus cambios aplicados y confirmados |
| Tarea 16: detalles menores aplazados de las tareas 1 a 9 (lote MN) | Terminados |
| Tarea 16: APK de publicación (`aapt2`, `check-manifest`, sin `PreviewActivity`) | Comprobado, también el firmado y el de *Releases* |
| Tarea 16: fusión en `main` (`6e3542d`) y arreglos de la prueba en el móvil por PR (#9 «Importar copia», #10 nombre repetido, #11 zoom en «Tachar») | Hechos |
| Tarea 16: APK firmado, prueba del dueño en el móvil y etiqueta `v1.3.0` | Hechos (1-11-2026) |

**Decisiones del dueño y del controlador que siguen vigentes** (el detalle está en `progress.md`, en las líneas «Ruling» y «Owner decision»):
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
- **Prueba del APK firmado:** se fusiona en `main` sin etiqueta; el dueño lanza Actions → Build APK → Run workflow desde `main`, lo aprueba y descarga `Clavero.apk`. Los arreglos van de la rama a `main`. La etiqueta `v1.3.0` solo cuando la prueba esté bien.
- **Protección de `main`:** el dueño la pone justo después de la fusión y antes de la etiqueta, con la lista «Entorno de firma» del README.
- **Prueba de palabras prohibidas:** `SourceTextsTest` revisa todas las cadenas de `app/src/main/java`, no solo `ToolTexts.kt`.

**Más decisiones del 1-11-2026:**
- **«Importar copia»** deja elegir cualquier archivo y mira su cabecera antes de pedir la contraseña (`mayBeVaultImport`).
- **Un archivo restaurado se guarda con el tipo de su extensión** (`restoredFileMime`): imagen salvo SVG, vídeo, audio, texto plano y PDF; lo demás, `application/octet-stream`.
- **Zoom en «Tachar»:** un segundo dedo antes de que el primero se mueva es zoom, y una cancelación del sistema no deja ningún bloque.
- **Los avisos de R8 sobre los metadatos de Kotlin** se dejan para la próxima versión: son las mismas herramientas que la 1.2.2, y no se cambia la compilación justo antes de publicar.

**Pendientes, del dueño** (`docs/PUBLICAR.md` §3.7 y README, «Entorno de firma»):
1. **Borrar los secretos `SEGURIDAD_*` del repositorio**, si ya están en el entorno `firma`.
2. **La comprobación del paso 7** con la rama `claude/prueba-secretos`, que debe dar `VACIO`.
3. **Buscar las marcas** CLAVERO, CLAVIS y CLAVISTER en la clase 9 (TMview, OEPM, WIPO), y registrarla si quiere.
4. **La fecha de la política, GitHub Pages y las respuestas de Play Console;** subir el AAB `Clavero-google-play-aab` de la ejecución #86 si publica en Google Play.
5. **Las pruebas en el móvil que siguen pendientes en el informe (§4):** `adb`, TalkBack, la ventana flotante de MagicOS, el teclado de Honor y la colaboración con el PC.

## Reglas del proyecto

- **No romper datos existentes:** los formatos `SG1:`, `.sgenc`, `seguridad-vault` y `seguridad-backup` se leen para siempre, y `CP1:`, `.cpenc` y `criptopanel-vault` (CriptoPanel) también. Cualquier cambio de formato lleva tests con datos reales anteriores.
- **Solo primitivas estándar** (`javax.crypto` y Bouncy Castle). Nada de criptografía propia.
- **Solo dependencias gratuitas y de licencia permisiva** (Apache-2.0, MIT, BSD, ISC, y CC BY para datos). Nada propietario ni copyleft (GPL, AGPL ni LGPL): la app se publica con licencia **Apache-2.0** y cualquier dependencia tiene que ser compatible con ella.
- Red: solo `api.pwnedpasswords.com`, con el cliente que bloquea los certificados de eMudhra. Sin logs con datos. `FLAG_SECURE` en toda la app.
- Interfaz en español.

## Compilar y probar

```bash
export ANDROID_HOME=/opt/android-sdk     # en las sesiones en la nube
./gradlew :app:assembleDebug :app:lintDebug --no-daemon --max-workers=1
gradle -p jvm-check test --no-daemon --max-workers=1
```
