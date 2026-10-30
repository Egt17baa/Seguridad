# Seguridad: instrucciones para Claude

App Android (Kotlin y Compose) de seguridad y criptografía: bóveda, 2FA, cifrado de texto y archivos, y herramientas. El diseño está en `docs/superpowers/specs/` y los planes en `docs/superpowers/plans/`.

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

## Estado actual: Seguridad 1.3.0 (30-10-2026)

**Fase:** desarrollo de la 1.3.0 por subagentes, en la rama `ccr-a1a6eabb-765f9g`; todavía no hay nada en `main`. Hay que seguir el traspaso `docs/superpowers/handoff/2026-10-28-seguridad-1.3.0-traspaso.md`. El registro de la ejecución está en `docs/superpowers/handoff/sdd-1.3.0/progress.md`, con su copia de trabajo en `.superpowers/sdd/2026-10-27-seguridad-1.3.0-herramientas/`.

| Paso | Estado |
|---|---|
| Tareas 1 a 12 | Terminadas y revisadas |
| Puesta al día CU-A (textos, Abogado Digital) y CU-B (Android, Ingeniero Móvil Senior) | Terminadas |
| Tarea 13: clave pública (A15a, A15b, A16a y A16b) | Terminada y revisada (`7268a24..b6e0d4a`) |
| Tarea 14: CI y publicación | Terminada y revisada |
| Tarea 15: documentos, versión 1.3.0 y anexo de textos | Terminada y revisada (`f439d00..01f44fc`) |
| Tarea 16: auditoría completa, revisión final del Abogado Digital, prueba en el móvil, fusión en `main` y APK | Pendiente |

**Decisiones del dueño y del controlador que siguen vigentes** (el detalle está en `progress.md`, en las líneas «Ruling»):
- **Detalles menores:** se arreglan todos, no se aplazan.
- **Nombre de salida de Limpiar foto:** `copia-de-foto.<ext>`, y no `foto-limpia`, porque «limpio» es una palabra prohibida. Tachar sigue con `imagen-tachada.<ext>`.
- **Estado que sobrevive al giro:** la imagen y los recuadros de Tachar, la copia de Limpiar foto, el PIN, los códigos, las respuestas y el diálogo de A6 viven en un ViewModel o en `AppContainer`, **nunca** en `rememberSaveable` ni en un `Bundle` si son secretos o imágenes. Se pierden si Android cierra la app, y eso se acepta.
- **«PIN y códigos»:** el PIN, los códigos y las respuestas se borran:
  - al volver atrás;
  - al bloquear la bóveda con la app en primer plano;
  - con el bloqueo por inactividad, solo si la pantalla no se ha usado durante `max(bloqueo, 60 s)`;
  - al volver después de ese tiempo fuera.

  Los sitios y las preguntas se conservan.
- **Huella (A11):** se guarda la hora más alta vista. La huella pide la maestra si el reloj va más de 5 min por detrás. Un desbloqueo con la maestra baja esa marca, pero una importación solo la baja después de guardar la bóveda.
- **Importar (A8):** si la copia tiene un Argon2id más débil, se vuelve a envolver con el máximo, eje por eje, entre el de la copia y el del perfil. Nunca se baja el coste.
- **Copias tras cambiar la maestra (A6):** la petición de reemplazo se guarda en `Settings.autoBackupReplacePending`. Primero se hace la copia nueva y después se borran solo las de la lista propia.
- **Portapapeles en Android 8 a 12 (A14):** el aviso se acepta una vez, y la copia pendiente se rechaza si la bóveda se bloquea entre el aviso y la aceptación.
- **Nombres de archivo (A16b):** se cortan por puntos de código, con un máximo de 255 bytes al escribir y guardar. Al leer se siguen aceptando hasta 1024 bytes (`MAX_FILE_NAME_BYTES`).
- **Prueba de palabras prohibidas:** `SourceTextsTest` revisa todas las cadenas de `app/src/main/java`, no solo `ToolTexts.kt`.

**Pendientes que ya tienen tarea:**
- **Tarea 16, Abogado Digital (una sola revisión final):**
  - todos los textos, incluidos los apuntados en «Textos para el Abogado Digital (revisión final)» en los informes de la tarea 12 en adelante;
  - las licencias y la atribución de BIP-39;
  - la política de privacidad y seguridad `docs/privacidad.html` y la ficha de Google Play, según la ley española, la europea y la internacional. Francia queda fuera de los países de publicación;
  - comprobar las fechas de la Directiva (UE) 2024/2853, del CRA y del AI Act, que quedaron sin verificar.
- **Tarea 16, nombre de la app (petición del dueño, 30-10-2026):** «Seguridad» es demasiado genérico. Al final, antes de la revisión final de textos, el Abogado Digital (`propiedad-intelectual-ia`) ayuda al dueño a elegir un nombre nuevo:
  - antes de proponerlo, busca marcas parecidas en OEPM, EUIPO y WIPO, en la clase 9. Por ejemplo, «FortisVault» se parece a la familia «Forti-» de Fortinet;
  - con el nombre elegido, se cambian el nombre visible, los textos, la política, la ficha y el README;
  - **no** cambian el `applicationId` (`io.github.egt17baa.seguridad`) ni los nombres internos de los formatos (`seguridad-vault`, `seguridad-backup`, `SG1:`…);
  - después, la revisión final del Abogado ya revisa los textos con el nombre nuevo.
- **Tarea 16, auditoría y APK:**
  - auditoría completa con el método de la auditoría externa;
  - revisar con `aapt2` los permisos del APK de publicación;
  - comprobar que el `PreviewActivity` exportado solo existe en debug.
- **Tarea 16, prueba del dueño en el Honor Magic 8 Pro:** la lista del traspaso (§2.6), más las pruebas nuevas de los informes `ingeniero-movil-1-11.md`, `cu-b-report.md` y `task-12-report.md`:
  - girar el móvil durante «Activar huella» y durante el desbloqueo;
  - Gboard, en un campo normal y en un campo dentro de un diálogo;
  - fotos de 200 MP;
  - un móvil de 3 a 4 GB de RAM para la memoria nativa de Tachar.
- **Detalles menores aplazados** de las tareas 1 a 9: el traspaso, §2.5. Se cierran en la revisión final.

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
