### Task 7: compilación: versión 1.3.1 y los avisos de R8

**Archivos:**
- Modificar: `app/build.gradle.kts:35`, `versionName`
- Si sale bien: `build.gradle.kts` (bloque `buildscript`) y `gradle/verification-metadata.xml`

- [ ] **Paso 1:** `versionName = "1.3.1"`. Las dos verificaciones globales.
- [ ] **Paso 2: reproducir los avisos.**
  - Ejecutar `./gradlew :app:assembleRelease --no-daemon --max-workers=1 --offline` y guardar las líneas de R8 con «Kotlin metadata» o «metadata» en `.superpowers/sdd/2026-11-01-clavero-1.3.1/r8-antes.txt`.
  - Ejecutar `bash scripts/check-manifest.sh app/build/outputs/apk/release/app-release.apk`.
- [ ] **Paso 3: probar un R8 más nuevo.**
  - Buscar en `https://dl.google.com/dl/android/maven2/com/android/tools/r8/maven-metadata.xml` la versión estable más baja cuyas notas digan que lee los metadatos de Kotlin 2.4.
  - Fijarla en `build.gradle.kts` con `buildscript { dependencies { classpath("com.android.tools:r8:<versión>") } }`.
  - Generar sus sumas con `./gradlew --write-verification-metadata sha256 help` (sin `--offline`) y revisar el diff de `verification-metadata.xml`: solo entradas de `com.android.tools:r8` y lo que arrastre.
- [ ] **Paso 4: se acepta solo si se cumple todo esto:**
  - los avisos desaparecen de `assembleRelease`;
  - las dos verificaciones globales dan 0 fallos;
  - `check-manifest.sh` da OK sobre el APK de publicación;
  - `aapt2 dump badging` da la misma etiqueta y los mismos 6 permisos que la 1.3.0.

  Si algo falla, se deshace el paso 3 y se apunta el motivo en el registro: queda para la 1.4.0.
- [ ] **Paso 5: commit** «build: versión 1.3.1» y, si se aceptó, «build: R8 <versión> lee los metadatos de Kotlin 2.4». Push.

---

## Después de las tareas: revisión final, documentos y PR

Esta parte la hace el controlador y no tiene una tarea por subagente:

1. **Revisión de la rama, contra `9504ce3`:**
   - `code-review` y `security-review`;
   - la auditoría del cambio con `experto-seguridad-cripto`, `auditoria-seguridad-movil` y `auditoria-seguridad`;
   - la revisión del Abogado Digital de los textos nuevos y del README.

   Se arreglan todos sus hallazgos, con re-revisión.
2. **Documentos:**
   - `docs/auditorias/2026-11-01-auditoria-1.3.1.md`, que enlaza `auditoria/04-cripto.md` y apunta el riesgo aceptado de CRY-A03;
   - en el README, las líneas de la prueba en el móvil de la 1.3.1:
     - comprobar una contraseña y lanzar «Salud» con red;
     - crear una bóveda y cambiar la maestra con «contraseña1» (rechazada) y con una frase (aceptada);
     - importar una copia;
     - un QR `otpauth` con `digits=10`;
   - `CLAUDE.md`, «Estado actual»;
   - el traspaso y el registro.
3. **PR a `main`** desde `ccr-a1a6eabb-765f9g`, con la plantilla `.github/pull_request_template.md`, y **build** y **jvm-check** en verde.
4. **Después del PR, el dueño:**
   - lanza el APK firmado (Actions → Build APK → Run workflow desde `main`) y lo prueba en el móvil;
   - pone la etiqueta `v1.3.1` desde Termux cuando la prueba está bien.
