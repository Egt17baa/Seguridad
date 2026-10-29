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
