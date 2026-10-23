# Seguridad: instrucciones para Claude

App Android (Kotlin y Compose) de seguridad y criptografía: bóveda, 2FA, cifrado de texto y archivos, y herramientas. El diseño está en `docs/superpowers/specs/` y los planes en `docs/superpowers/plans/`.

## Skills: siempre juntas

Todo trabajo en este repositorio combina **las skills de proceso de superpowers** con **la skill del proyecto `experto-seguridad-cripto`** (`.claude/skills/experto-seguridad-cripto/`):

1. **superpowers** marca el proceso: brainstorming, plan, desarrollo por subagentes o en línea con TDD, revisión y verificación.
2. **experto-seguridad-cripto** aporta en cada fase el criterio de seguridad. Su sección «Trabajo conjunto con superpowers» dice qué añade en cada fase.
3. Todo implementador y todo revisor, incluidos los subagentes, lee `SKILL.md` de `experto-seguridad-cripto` y las referencias que toquen su tarea, y cierra su informe con una sección **«Revisión experto-seguridad-cripto»** con la evidencia `archivo:línea`.
4. Antes de fusionar en `main`: `code-review` y `security-review` sobre la rama, más la revisión final con esta skill.

## Reglas del proyecto

- **No romper datos existentes:** los formatos `SG1:`, `.sgenc`, `seguridad-vault` y `seguridad-backup` se leen para siempre, y `CP1:`, `.cpenc` y `criptopanel-vault` (CriptoPanel) también. Cualquier cambio de formato lleva tests con datos reales anteriores.
- **Solo primitivas estándar** (`javax.crypto` y Bouncy Castle). Nada de criptografía propia.
- **Solo dependencias gratuitas y de licencia permisiva** (Apache-2.0, MIT, BSD, ISC, y CC BY para datos). Nada propietario ni copyleft (GPL, AGPL ni LGPL): el código de la app es **privado**.
- Red: solo `api.pwnedpasswords.com`, con el cliente que bloquea los certificados de eMudhra. Sin logs con datos. `FLAG_SECURE` en toda la app.
- Interfaz en español.

## Compilar y probar

```bash
export ANDROID_HOME=/opt/android-sdk     # en las sesiones en la nube
./gradlew :app:assembleDebug :app:lintDebug --no-daemon --max-workers=1
gradle -p jvm-check test --no-daemon --max-workers=1
```
