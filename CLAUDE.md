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

## Estado actual: Clavero 1.3.1, terminada en la rama y pendiente del PR (1-11-2026)

**Nombre:** la app se llama **Clavero** desde la 1.3.0 (antes «Seguridad»). Cambian el nombre visible, los textos, la política, la ficha, el README y los nombres de los APK publicados (`Clavero-<etiqueta>.apk`). **No cambian**, y `AppNameTest` lo comprueba:
- el `applicationId` `io.github.egt17baa.seguridad` y el paquete `app.seguridad`;
- la autoridad del `FileProvider` y el alias de la clave del Keystore;
- la etiqueta propia del portapapeles «Seguridad»;
- los nombres de los formatos (`seguridad-vault`, `seguridad-backup`, `SG1:`…) y los prefijos de las copias (`seguridad-auto-`, `seguridad-boveda-`);
- el nombre del repositorio, que el dueño puede cambiar si quiere.

**Fase:**
- **La 1.3.0 está publicada:** etiqueta `v1.3.0` en `b6ea21b` (`main`), con `Clavero-v1.3.0.apk` en *Releases*, firmado con la clave de subida. Su registro está en `docs/superpowers/handoff/sdd-1.3.0/progress.md` y su auditoría en `docs/auditorias/2026-10-31-auditoria-1.3.0.md`.
- **La 1.3.1 es una versión de mantenimiento:** corrige los cinco hallazgos de la revisión criptográfica (`auditoria/04-cripto.md`) y quita CriptoPanel. Se trabaja en la rama `ccr-a1a6eabb-765f9g`, desde `main` en `9504ce3`. El código se cerró en `284d206`.
- **Dónde está:** el registro en `docs/superpowers/handoff/sdd-1.3.1/progress.md`, la auditoría en `docs/auditorias/2026-11-01-auditoria-1.3.1.md` y el traspaso en `docs/superpowers/handoff/2026-11-01-clavero-1.3.1-traspaso.md`.
- `main` está protegida desde el 1-11-2026: todo cambio entra por un PR, con **build** y **jvm-check** en verde.

| Paso de la 1.3.1 | Estado |
|---|---|
| Tareas 1 a 5: CRY-B01, CRY-A01, CRY-A02, CRY-A03 y CRY-B02 | Terminadas y revisadas |
| Tarea 6: fuera CriptoPanel | Terminada y revisada |
| Tarea 7: `versionName` 1.3.1 (el R8 nuevo se probó y se quitó) | Terminada |
| Revisión final: revisión de la rama, auditoría del cambio, `code-review` y `security-review` | Hechas |
| Ronda de arreglos de la revisión final (F1 a F9, de `37d50f2` a `95ddc44`) | Hecha, con su repaso acotado hecho y sus dos detalles menores arreglados (`284d206`) |
| Documentos de la publicación (auditoría, README, `CLAUDE.md`, plantilla del PR, traspaso) | Hechos y repasados, y puestos al día con los cambios del Abogado Digital |
| Revisión final del Abogado Digital | Hecha (1-11-2026): «se puede publicar, con cambios»; ninguna norma exige cambios; O-1 y R-1 a R-4 aplicados (`284d206` y los documentos) |
| Comprobación corta de la última tanda (`284d206` y sus documentos) | Hecha, sin nada abierto |
| PR a `main`, APK firmado, prueba del dueño en el móvil y etiqueta `v1.3.1` | Pendiente |

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
- **Prueba del APK firmado:** se fusiona en `main` sin etiqueta; el dueño lanza Actions → Build APK → Run workflow desde `main`, lo aprueba y descarga `Clavero.apk`. Los arreglos van de la rama a `main`. La etiqueta de la versión (`v1.3.0`, `v1.3.1`) solo cuando la prueba esté bien.
- **Protección de `main`:** el dueño la pone justo después de la fusión y antes de la etiqueta, con la lista «Entorno de firma» del README.
- **Prueba de palabras prohibidas:** `SourceTextsTest` revisa todas las cadenas de `app/src/main/java`, no solo `ToolTexts.kt`.

**Más decisiones del 1-11-2026:**
- **«Importar copia»** deja elegir cualquier archivo y mira su cabecera antes de pedir la contraseña (`mayBeVaultImport`).
- **Un archivo restaurado se guarda con el tipo de su extensión** (`restoredFileMime`): imagen salvo SVG, vídeo, audio, texto plano y PDF; lo demás, `application/octet-stream`.
- **Zoom en «Tachar»:** un segundo dedo antes de que el primero se mueva es zoom, y una cancelación del sistema no deja ningún bloque.
- **Los avisos de R8 sobre los metadatos de Kotlin** quedan para la 1.4.0, con un AGP 9 que traiga ese R8. En la 1.3.1 se probó a fijar el R8 9.1.31 y el dueño decidió quitarlo («Quitarlo», `94ddb7f`): la compilación sigue como la de la 1.3.0, ya probada en el móvil.

**Decisiones de la 1.3.1 (1-11-2026):**
- **CriptoPanel (dueño):** Clavero deja de leer `CP1:`, `.cpenc` y `criptopanel-vault`, y el nombre sale de la interfaz. Lo de CriptoPanel recibe el mismo error que cualquier dato que no es de Clavero. El dueño no tenía ninguna bóveda en CriptoPanel.
- **Maestra nueva (CRY-A01, dueño):** solo al crear la bóveda y al cambiar la maestra. Abrir la bóveda, la huella e importar una copia no cambian (la contraseña de una copia sigue pidiendo solo 10 caracteres). Una maestra nueva necesita:
  - 10 caracteres contados de las dos maneras: unidades UTF-16 y puntos de código en NFC sin caracteres de formato (Cf);
  - una estimación de 50 bits o más (`WEAK_BITS`, la de «Salud»), que se mide de esas dos maneras y se queda con la menor;
  - que no sea una sola palabra conocida: su núcleo (minúsculas, sin acentos y solo letras) no está en la lista común de «Salud» ni en las listas EFF, BIP-39 en inglés y BIP-39 en español.

  Es una estimación: «Barcelona1» y dos palabras juntas pasan. Las listas se leen una vez por proceso, fuera del hilo principal, y `checkAssets` falla si falta alguna en el APK.
- **Huella (CRY-A02):** se desactiva solo cuando su clave no autentica la bóveda (`StaleBiometricKey`, con el texto `FINGERPRINT_STALE`, que dice «Puede deberse a» un cambio de la maestra, una copia importada o datos dañados, porque AES-GCM no distingue esos casos). Crear la bóveda y borrarla desactivan la huella antes; cambiar la maestra e importar una copia, justo después de guardar. Una clave que autentica pero cuyas entradas no se decodifican da el error de siempre y conserva la huella. Con un archivo que dice su versión (2 o 3), primero van la regla de los 7 días y la del reloj (A11), antes de usar la clave, como en la 1.3.0. Sin versión, primero la clave, que es la que dice qué versión es.
- **Un archivo que esta versión no abre** (otro formato, o una versión 1, 4…): la huella no se ofrece ni prueba la clave (`BiometricRefusal.UNSUPPORTED`, sin aviso), y la maestra dice «No es una bóveda compatible» en lugar de «Contraseña incorrecta».
- **TOTP (CRY-B02):** fuera de 6 a 8 dígitos, de 10 a 120 s o de SHA1, SHA256 y SHA512, el código se rechaza, no se cambia. El editor y el Inspector de QR lo dicen, y el Inspector no ofrece «Añadir a la bóveda». La lista dice «2FA no admitido» y la ficha explica por qué. Solo un parámetro que falta toma el valor por defecto.
- **Revisión del Abogado Digital (1-11-2026):** «se puede publicar, con cambios». Con los hechos que da por ciertos (particular sin ánimo de lucro, gratuita, sin anuncios ni donaciones, sin recibir datos), ninguna norma exige cambios. Se aplicaron O-1 (`FINGERPRINT_STALE`, obligatorio por la regla de veracidad de este proyecto), R-1 (la nota de CriptoPanel del README, con la salida por la 1.3.0), R-2 (el ejemplo de User-Agent de la política), R-3 (la nota de la EFF en Licencias) y R-4 (una frase de la auditoría). Las notas de la versión de GitHub y de Play no están en el repositorio: deben decir que la 1.3.1 deja de abrir los formatos de CriptoPanel, no presentar la regla de la maestra como algo que hace fuerte la contraseña y no usar palabras prohibidas (borrador en el traspaso de la 1.3.1).
- **`BiometricVault.disable` en el hilo principal:** sigue ahí en la 1.3.1, en sus seis llamadas (es una llamada al Keystore). En la 1.4.0, todas las operaciones de `BiometricVault` pasan a un dispatcher de un solo hilo: mover solo `disable`, sin ponerlas en fila, podría borrar la clave que `enable` acaba de crear con el mismo alias, o saltarse `disable` al cancelar.
- **Cada subida de `targetSdk`:** se repite en el móvil «¿Filtrada?» y «Comprobar filtraciones» con red. La segunda validación TLS de CRY-B01 no recibe el OCSP grapado ni las SCT de la extensión TLS, y podría rechazar de más si Android llega a exigir Certificate Transparency (N-04).

**Pendientes, del dueño** (`docs/PUBLICAR.md` §3.7 y README, «Entorno de firma»):
1. **1.3.1:** cuando el PR esté fusionado, Actions → Build APK → Run workflow desde `main`, aprobar **build-firma**, descargar `Clavero.apk` y hacer las pruebas de la 1.3.1 del README («Comprobaciones manuales en el móvil»).
2. **1.3.1:** si salen bien, la etiqueta `v1.3.1` desde su terminal.
3. **1.3.1, la política:** `docs/privacidad.html` cambió (el ejemplo de User-Agent, R-2). El día que publique, el dueño pone su fecha (la línea «Última actualización») y vuelve a publicarla en GitHub Pages. La rama no toca la fecha.
4. **1.3.1, las notas de la versión** de GitHub (y de Play, si publica allí): según la guía del Abogado Digital y con el borrador del traspaso de la 1.3.1, §3.
5. **CriptoPanel:** retirar su app o su repositorio, si quiere; es decisión suya.
6. **Borrar los secretos `SEGURIDAD_*` del repositorio**, si ya están en el entorno `firma`.
7. **La comprobación del paso 7** con la rama `claude/prueba-secretos`, que debe dar `VACIO`.
8. **Buscar las marcas** CLAVERO, CLAVIS y CLAVISTER en la clase 9 (TMview, OEPM, WIPO), y registrarla si quiere.
9. **La fecha de la política, GitHub Pages y las respuestas de Play Console** (la fecha y Pages, también para la 1.3.1: paso 3); subir el AAB `Clavero-google-play-aab` de la ejecución #86 si publica en Google Play.
10. **Las pruebas en el móvil que siguen pendientes en el informe de la 1.3.0 (§4):** `adb`, TalkBack, la ventana flotante del sistema, el teclado del fabricante y la colaboración con el PC.

## Reglas del proyecto

- **No romper datos existentes:** los formatos `SG1:`, `.sgenc`, `seguridad-vault` y `seguridad-backup` se leen para siempre. Desde la 1.3.1, por decisión del dueño (1-11-2026), los de CriptoPanel (`CP1:`, `.cpenc` y `criptopanel-vault`) no se leen y reciben el error genérico. Cualquier cambio de formato lleva tests con datos reales anteriores.
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
