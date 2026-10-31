### Task 14: CI y publicación (A10, A10d, A10e y A17)

**Archivos:**
- Modificar: `.github/workflows/build-apk.yml`, `app/build.gradle.kts`, `README.md` y `.github/pull_request_template.md` (crearlo si no existe).
- Crear: `scripts/check-manifest.sh`.

- [ ] **Step 1: A10e, en `app/build.gradle.kts`.**
  - **Sin firma real:** si no hay `releaseStoreFile`, `assembleRelease` y `bundleRelease` fallan en la fase de ejecución, con `GradleException("Sin clave de subida: usa keystore.properties o -PfirmaDePrueba=true para un APK de prueba firmado con la clave pública de depuración")`, salvo con `-PfirmaDePrueba=true`.
  - **Comprobar:**
    - `./gradlew :app:assembleRelease` sin la propiedad falla;
    - con `-PfirmaDePrueba=true`, pasa.

- [ ] **Step 2: A17, en `scripts/check-manifest.sh`.**
  - Recibe la ruta del APK. Ejecuta `aapt2 dump xmltree --file AndroidManifest.xml` y `aapt2 dump permissions`.
  - Falla si hay un componente con `exported=true` distinto de `app.seguridad.MainActivity` y de `androidx.profileinstaller.ProfileInstallReceiver`, este con `android.permission.DUMP`.
  - Falla si el proveedor `androidx.core.content.FileProvider` no está o es exportado.
  - Falla si los permisos no son exactamente los de la 1.2.2. El script los lleva en una lista fija, sacada de `aapt2 dump permissions` sobre el APK de la 1.2.2 en esta tarea.
  - Se ejecuta en la CI tras `assembleRelease` y en local sobre el APK debug.

- [ ] **Step 3: A10, en `build-apk.yml`.**
  - Los trabajos que leen los secretos de firma declaran `environment: firma`.
  - Las expresiones de los secretos siguen igual. Si el entorno no existe, GitHub lo crea sin reglas y los secretos del repositorio siguen llegando, así que no se rompe nada hasta que el dueño lo configure. El implementador lo confirma en la documentación de GitHub y lo apunta en el informe.
  - Los trabajos de PR, que nunca firman, no declaran el entorno.
  - La compilación sin secretos pasa `-PfirmaDePrueba=true`.
  - El README trae los pasos para que el dueño cree el entorno, lo limite a `main` y a las etiquetas `v*`, y mueva allí los secretos.

- [ ] **Step 4: A10d, en `build-apk.yml`.**
  - Un paso escribe en `$GITHUB_STEP_SUMMARY` cuántas líneas `<component` cambian en `gradle/verification-metadata.xml` y en `jvm-check/gradle/verification-metadata.xml` respecto a la base (`git diff --numstat`).
  - La plantilla de PR y el README dicen que ese cambio se revisa como cambio de seguridad.

- [ ] **Step 5: Run** `bash scripts/check-manifest.sh app/build/outputs/apk/debug/app-debug.apk` (con `aapt2` de `/opt/android-sdk/build-tools/*`). Expected: OK. Comprueba también el YAML con `python3 -c "import yaml,sys; yaml.safe_load(open('.github/workflows/build-apk.yml'))"`.

- [ ] **Step 6: Commit** `1.3.0: CI con entorno de firma, manifiesto comprobado y release sin clave de prueba por defecto`.

