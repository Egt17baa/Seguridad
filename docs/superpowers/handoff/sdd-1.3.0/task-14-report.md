# Tarea 14: CI y publicación (A10, A10d, A10e y A17)

**Estado:** DONE_WITH_CONCERNS. **Commit:** `cfc8d44` (base `5e44bdb`). No se ha hecho push.

## Qué se ha hecho

### A10e: release sin clave de prueba por defecto (`app/build.gradle.kts`)
- `firmaDePrueba = providers.gradleProperty("firmaDePrueba").orNull == "true"` (`app/build.gradle.kts:22`).
- Nueva tarea `checkReleaseKey` (`app/build.gradle.kts:130-142`). Si no hay `releaseStoreFile` y no se pasa la propiedad, lanza en la fase de ejecución `GradleException("Sin clave de subida: usa keystore.properties o -PfirmaDePrueba=true para un APK de prueba firmado con la clave pública de depuración")` (el texto exacto del brief).
- Solo dependen de ella las tareas de empaquetado `packageRelease`, `packageReleaseBundle` y `packageReleaseUniversalApk` (`:143-144`). Por eso `assembleRelease` y `bundleRelease` fallan, mientras que `lintRelease`, `checkLicense`, `checkAssets` y el resto se siguen configurando y ejecutando sin la clave. El `signingConfig` sigue recurriendo a la clave de depuración (`:62-66`, comentario actualizado), así que la variante release se sigue configurando.

### A17: `scripts/check-manifest.sh` (nuevo, ejecutable)
- Uso: `bash scripts/check-manifest.sh [--release] ruta/al.apk`. Ejecuta `aapt2 dump xmltree --file AndroidManifest.xml` y `aapt2 dump permissions`. Busca aapt2 en `$AAPT2`, después en el `$ANDROID_HOME/build-tools/*/aapt2` más reciente (o en `/opt/android-sdk`) y por último en el PATH.
- Falla si:
  - hay un componente exportado (activity, activity-alias, service, receiver o provider) distinto de `app.seguridad.MainActivity` y de `androidx.profileinstaller.ProfileInstallReceiver`, y este último tiene que llevar `android.permission.DUMP`. Un componente con intent-filter y sin `android:exported` cuenta como exportado. Un valor que no sea `false` (por ejemplo una referencia `@0x…`) también cuenta como exportado: el script falla cerrado;
  - `androidx.core.content.FileProvider` no aparece exactamente una vez, está exportado o no tiene `grantUriPermissions="true"`. Esta última comprobación viene de la restricción global de context.md;
  - los permisos no son exactamente los de la 1.2.2 (lista fija `EXPECTED_PERMISSIONS`, `scripts/check-manifest.sh:23-29`), o aapt2 falla (`set -euo pipefail`).
- **PreviewActivity:** solo se admite si el APK es *debuggable* (`android:debuggable=true`), lo que se detecta en el `<application>`. Así, `bash scripts/check-manifest.sh app/build/outputs/apk/debug/app-debug.apk` da OK, como pide el paso 5 del brief. En un APK no *debuggable* siempre falla. La CI pasa `--release`, que además rechaza un APK *debuggable*. Así, una release que se volviera *debuggable* por error no puede colarse con las reglas de debug (`:114-125`, `:152`).

### A10: entorno `firma` (`.github/workflows/build-apk.yml`)
- El antiguo `build` se divide en dos trabajos con los mismos pasos, compartidos con un ancla YAML (`steps: &build-steps` en `:32` y `steps: *build-steps` en `:136`):
  - **build** (`:28-29`): `if: ${{ !(TRUSTED) }}`, es decir, PR, ramas que no son `main` y ejecuciones a mano fuera de `main`. **No declara entorno.** Las expresiones de los secretos dan `''`, igual que antes.
  - **build-firma** (`:132-136`): `if: TRUSTED` y `environment: firma`.
  - TRUSTED es exactamente la condición que ya llevaban las expresiones de los secretos.
- **play-bundle** declara `environment: firma` (`:230`). Su `if` no cambia.
- **release-apk** pasa a `needs: [build-firma, jvm-check]` (`:202`).
- Las expresiones de los secretos (`KEYSTORE_BASE64` …) no cambian ni de nombre ni de forma.
- Sin secretos, `assembleRelease` recibe `-PfirmaDePrueba=true` (`:82-89`). La salvaguarda de antes, que da error si una ejecución de confianza no tiene clave, se queda como estaba.
- Tras `assembleRelease`, el paso «Check the merged manifest» ejecuta `bash scripts/check-manifest.sh --release …/app-release.apk` (`:94-96`).
- Por qué hay dos trabajos y no `environment: ${{ cond && 'firma' || '' }}`: la documentación de `jobs.<job_id>.environment` no dice qué pasa con un nombre vacío (lo he comprobado). Además, el entorno limitado a `main` y `v*` rechazaría los trabajos de PR y de ramas si lo declararan. Las anclas YAML funcionan en GitHub Actions desde el changelog del 18-09-2025 («Actions: YAML anchors and non-public workflow templates»); las claves de fusión `<<` no.

**Comprobación en la documentación de GitHub (WebFetch, con red):**
- https://docs.github.com/en/actions/managing-workflow-runs-and-deployments/managing-deployments/managing-environments-for-deployment:
  - «Running a workflow that references an environment that does not exist will create an environment with the referenced name.» … «Otherwise, the newly created environment will not have any protection rules or secrets configured.»
  - «Anyone that can edit workflows in the repository can create environments via a workflow file, but only repository admins can configure the environment.»
  - «Users with GitHub Free plans can only configure environments for public repositories.»
- https://docs.github.com/en/actions/reference/security/secrets:
  - «If a secret with the same name exists at multiple levels, the secret at the lowest level takes precedence.»
  - Con organización, repositorio y entorno, gana el del entorno. Por tanto los secretos del repositorio siguen llegando a un trabajo con entorno mientras el entorno no tenga los suyos.
- No he encontrado una frase literal que diga «los secretos del repositorio siguen disponibles en un trabajo con entorno». Se deduce de la regla de precedencia. El dueño lo verá en la primera ejecución de `build-firma`, con la huella del resumen.

### A10d: cambios de `verification-metadata.xml`
- Nuevo trabajo **dependency-metadata** (`:140-175`). No usa secretos ni Gradle, y hace checkout con `fetch-depth: 0`.
- Escribe en `$GITHUB_STEP_SUMMARY` una tabla por archivo (`gradle/…` y `jvm-check/gradle/…`) con las líneas añadidas y quitadas (`git diff --numstat`) y con las líneas `<component` añadidas y quitadas (`git diff -U0 | grep -c`).
- Base: la del PR, si no `github.event.before` y, para una rama o etiqueta nueva (`0000…`), la etiqueta `v*` anterior. Sin base, escribe «Sin base con la que comparar.».
- Los valores del evento entran por `env:`, nunca interpolados en el script.
- El README (`README.md:101`) y la plantilla de PR dicen que ese cambio se revisa como cambio de seguridad.

### README, plantilla de PR y PUBLICAR
- `README.md`:
  - «Compilar»: el fallo sin clave, `-PfirmaDePrueba=true` y cómo ejecutar `check-manifest.sh` en local (`:63-72`);
  - «Integración continua»: los trabajos build, build-firma y dependency-metadata (`:88-95`);
  - «Verificación de dependencias»: aviso de cambio de seguridad (`:101`), y `-PfirmaDePrueba=true` en el comando de regenerar, que lanza `assembleRelease` y `bundleRelease` (`:108-110`);
  - «Firma» (`:134-136`);
  - nueva sección «Entorno de firma» (`:147-161`), en 7 pasos: crear `firma`; limitarlo a la rama `main` y a la etiqueta `v*`; revisores opcionales; mover los secretos; comprobar la huella; borrar los del repositorio; proteger las etiquetas `v*` con un ruleset. También dice qué planes permiten entornos.
- `.github/pull_request_template.md` (nuevo).
- `docs/PUBLICAR.md:5,13,107`: los secretos «del entorno `firma` (o del repositorio…)» y el trabajo **build-firma**. Si no se cambiaban, esas líneas quedaban falsas.

### U+180E (minor añadido a esta tarea): **no lo he quitado** y lo apunto como concern
- Lo que se suponía era que esas aserciones dependen de la versión de Unicode del JDK. **En estos dos tests no es así:**
  - `Card.kt:238` (`isForbiddenNameCodePoint`) lista `0x180E` de forma explícita;
  - `Visible.kt:78` (`isHiddenCodePoint`) también;
  - ninguno de los dos pasa por `Character.getType`.
  
  La dependencia del JDK estaba en el `\p{Cf}` de `FileCrypto.kt:153`, y su test ya se quitó en la ronda 3 de la tarea 13.
- Quitar `0x180E..0x180E` de `CardTest.kt:269` (antes :266) **rompería** `laListaFijaCoincideConLaTablaEnTodosLosPuntosDeCodigo`, que compara los 0x110000 puntos de código con el código de producción. Para que pasara habría que cambiar la producción: dejar de prohibir U+180E en los nombres, en contra de la tabla de diseño (`Card.kt:208`) y del Ruling de `progress.md:83`.
- Quitarlo de `:240` y de `QrContentTest.kt:388` solo haría perder cobertura de un comportamiento que exige el diseño.
- Lo que he hecho: un comentario en los tres sitios (`CardTest.kt:236-237`, `CardTest.kt:259-261` y `QrContentTest.kt:385-387`) que explica por qué no dependen del JDK (U+180E era Zs antes de Unicode 6.3 y desde entonces es Cf).
- JDK local 21.0.12: `Character.getType(0x180E) = 16 (FORMAT)`.
- Si el controlador lo mantiene, quitarlo de `:240` y de `:388` es una línea en cada sitio. `:269` exige cambiar la producción.

## Permisos de la 1.2.2 (A17), salida literal

- Worktree separado en `…/scratchpad/v122`, en el commit `71c9964`. No existe la etiqueta local `v1.2.2`, así que usé el commit con `--detach`.
- En la 1.2.2, la release se firma con la clave de depuración si no hay clave (`signingConfigs.findByName("release") ?: …getByName("debug")`), así que se pudo compilar la release.
- `--offline` falló en `produceReleaseComposeMapping`, porque faltaba `org.jetbrains.kotlin:compose-group-mapping:2.4.21` en la caché. Repetí la compilación con red y pasó.
- El worktree se quitó con `git worktree remove`.

`aapt2 dump permissions` (release 1.2.2, firmada con `c0014df6…1834`):
```
package: io.github.egt17baa.seguridad
uses-permission: name='android.permission.INTERNET'
uses-permission: name='android.permission.CAMERA'
uses-permission: name='android.permission.USE_BIOMETRIC'
uses-permission: name='android.permission.USE_FINGERPRINT'
permission: io.github.egt17baa.seguridad.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION
uses-permission: name='io.github.egt17baa.seguridad.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION'
```
- El APK debug de la 1.2.2 da exactamente lo mismo (`diff`: SAME). En permisos, debug y release no se diferencian.
- La 1.3.0 da exactamente lo mismo en release y en debug.
- Exportados en la release 1.2.2: solo `MainActivity` y `ProfileInstallReceiver`. No lleva `PreviewActivity` ni `FileProvider`, que es nuevo en la 1.3.0.
- Por eso `check-manifest.sh --release` sobre el APK de la 1.2.2 da un solo error, el esperado: «FileProvider aparece 0 veces».

## Tests y resultados

| Prueba | Comando | Resultado |
|---|---|---|
| Release sin clave | `./gradlew :app:assembleRelease --offline …` | exit 1, `Execution failed for task ':app:checkReleaseKey'. > Sin clave de subida: usa keystore.properties o -PfirmaDePrueba=true para un APK de prueba firmado con la clave pública de depuración` |
| Bundle sin clave | `./gradlew :app:bundleRelease --offline …` | exit 1, el mismo mensaje |
| Release con la propiedad | `./gradlew :app:assembleRelease -PfirmaDePrueba=true --offline …` | exit 0, BUILD SUCCESSFUL, APK firmado con `c0014df6…1834` |
| `-PfirmaDePrueba=false` | `:app:checkReleaseKey -PfirmaDePrueba=false` | BUILD FAILED, el mismo mensaje |
| Con clave | `SEGURIDAD_KEYSTORE_FILE=app/debug.keystore … :app:checkReleaseKey` | BUILD SUCCESSFUL |
| Manifiesto de la release 1.3.0 | `bash scripts/check-manifest.sh --release app/build/outputs/apk/release/app-release.apk` | `check-manifest: OK` |
| Manifiesto del debug 1.3.0 (paso 5) | `bash scripts/check-manifest.sh app/build/outputs/apk/debug/app-debug.apk` | `…PreviewActivity exportado, permitido solo porque el APK es debuggable` y después `check-manifest: OK (…, debug)` |
| Casos negativos (aapt2 falso que devuelve el volcado real modificado) | `--release` sobre debug; permiso extra `READ_MEDIA_IMAGES`; falta CAMERA; PreviewActivity en un APK no debuggable; FileProvider exportado, sin grant y ausente; ProfileInstallReceiver sin DUMP; intent-filter sin `exported`; servicio exportado | los 10 dan exit 1 con su mensaje |
| Entrada inválida | APK basura; sin argumentos | exit 1 (aapt2 falla); exit 2 (uso) |
| Resumen A10d | el script del paso, con bases `71c9964`, vacía, `0000…`, `HEAD~1` y `83e06e8~1` | tablas correctas: `83e06e8` da `8 | 0 | 1 | 0` en los dos archivos (un `<component` añadido), las demás `0`, y «Sin base…» con base vacía o de ceros |
| YAML | `python3 -c "import yaml,sys; yaml.safe_load(open('.github/workflows/build-apk.yml'))"` | OK; los trabajos son `build`, `build-firma`, `dependency-metadata`, `jvm-check`, `release-apk` y `play-bundle`; los pasos de `build-firma` son iguales a los de `build` (8) |
| Verificación completa | `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets --no-daemon --max-workers=1 --offline` | BUILD SUCCESSFUL in 3m 32s (62 tareas); 0 fallos en las 62 clases de test |
| jvm-check | `gradle -p jvm-check test --no-daemon --max-workers=1 --offline` | BUILD SUCCESSFUL in 1m 31s; 953 tests, 0 fallos |

No se ha podido ejecutar el workflow en GitHub desde aquí.

## Archivos
- `.github/workflows/build-apk.yml` (modificado)
- `.github/pull_request_template.md` (nuevo)
- `scripts/check-manifest.sh` (nuevo, 100755)
- `app/build.gradle.kts`
- `README.md`
- `docs/PUBLICAR.md`
- `app/src/test/java/app/seguridad/crypto/pk/CardTest.kt` y `app/src/test/java/app/seguridad/tools/qr/QrContentTest.kt` (solo comentarios)

## Autorrevisión
- No cambian las comprobaciones existentes: `checkLicense`, `checkFragmentVersion`, `checkAssets`, la suma del wrapper, las acciones fijadas por SHA y `verification-metadata` se quedan como estaban. La salvaguarda «sin clave en una ejecución de confianza → error» sigue en los pasos compartidos.
- No cambian los nombres ni las expresiones de los secretos. No hay dependencias, permisos ni componentes nuevos.
- `"${test_key[@]}"` con el array vacío funciona en bash ≥ 4.4 también con `-u`. Los runners usan bash 5.
- Para `checkReleaseKey`, el valor de `missingKey` se calcula al configurar y solo se lanza en `doLast`. No hay configuration cache activa en `gradle.properties`.

## Concerns
1. **U+180E:** no se ha quitado (ver arriba). El motivo del ruling no se aplica a estos tests, y en `CardTest.kt:269` no se puede quitar sin cambiar la producción. Lo decide el controlador.
2. **Nombre del trabajo:** los push a `main` y las etiquetas ya no ejecutan `build` sino `build-firma`. Si el dueño tiene `build` como comprobación obligatoria en una regla de rama, en los PR sigue existiendo, porque `build` corre en todos los PR.
3. **Anclas YAML:** GitHub las admite desde 09-2025. Si un linter antiguo (actionlint < 1.7.8, por ejemplo) se quejara, no afecta a GitHub.
4. **Plan de GitHub:** si el repositorio es privado y la cuenta es Free, el entorno `firma` se crea pero no se puede configurar (no admite reglas ni secretos de entorno). Los secretos del repositorio siguen funcionando. Lo dice el README.
5. **Herencia del secreto:** la documentación no dice con una frase literal que los secretos del repositorio sigan llegando a un trabajo con entorno. Se deduce de la precedencia documentada («lowest level takes precedence»). El dueño lo confirma con la huella en la primera ejecución de `build-firma` (README, paso 5).
6. **Compilación offline:** `assembleRelease` de la 1.2.2 no compilaba offline por `compose-group-mapping:2.4.21`; se compiló con red. La release de la 1.3.0 sí compiló offline después, porque el artefacto quedó en la caché.

## Revisión experto-seguridad-cripto
- **Modelo de amenaza:**
  - el atacante puede abrir un PR o hacer push a una rama, pero no a `main` ni a etiquetas `v*` protegidas: no recibe la clave. `build` no declara entorno y sus expresiones de secretos dan `''` (`build-apk.yml:28-29`, `:49-52`);
  - quien empuja una etiqueta `v*`, si no está protegida, sí la recibe. El README, paso 7 (`README.md:157`), pide un ruleset de etiquetas; es una acción del dueño en GitHub.
- **Cadena de suministro (OWASP Top 10:2025, Software Supply Chain Failures; CWE-494):**
  - `dependency-metadata` hace visible en el resumen cada cambio de `<component` (`build-apk.yml:140-175`);
  - el README marca ese cambio como de seguridad e indica `verify-signatures=false` (`README.md:101`, `gradle/verification-metadata.xml:5`);
  - no hay inyección de expresiones en el script: los valores del evento entran por `env:` (`build-apk.yml:146-148`).
- **Firma con clave pública (CWE-321, clave en el código):** con `checkReleaseKey`, un APK firmado con la clave pública (`app/build.gradle.kts:130-144`) solo sale si se pide de forma explícita. La CI de confianza sigue fallando sin clave.
- **Superficie IPC (MASVS-PLATFORM-1):** `scripts/check-manifest.sh` impone en la CI lo siguiente:
  - solo dos componentes exportados, y el receptor con `DUMP` (`:137-152`);
  - un `FileProvider` no exportado y con `grantUriPermissions` (`:139-145`, `:159`);
  - exactamente los permisos de la 1.2.2 (`:23-29`, `:161-166`);
  - ningún APK *debuggable* con `--release` (`:114-125`; MASVS-CODE, `debuggable`).
  
  Si algo es dudoso, falla cerrado: un `exported` que no sea `false`, un intent-filter sin `exported` o un fallo de aapt2 hacen fallar el script.
- **Evidencia ejecutada:** `aapt2 dump permissions` de la 1.2.2 y de la 1.3.0 (arriba), los 10 casos negativos y las releases sin clave y con la propiedad.
- No hay criptografía nueva ni formatos tocados.

## Revisión Ingeniero Móvil Senior
- **Gradle:** el guard va en las tareas de empaquetado de AGP (`packageRelease`, `packageReleaseBundle` y `packageReleaseUniversalApk`, en `app/build.gradle.kts:143-144`), no en el `signingConfig`. Así la variante release se sigue configurando, y `lintRelease` y `checkAssets` (que lee `SingleArtifact.ASSETS` de release) funcionan sin clave. Comprobado: `checkAssets` pasó en la verificación sin la propiedad.
- `providers.gradleProperty` es lazy y compatible con la configuration cache.
- **Manifiesto fusionado:** se revisa el `AndroidManifest.xml` binario del APK, no el de `src/`. Ahí entran los componentes de las bibliotecas (profileinstaller, startup, ui-tooling y zxing `CaptureActivity`).
- `PreviewActivity` viene de `debugImplementation(libs.compose.ui.tooling)` (`app/build.gradle.kts:104`). Comprobado que no está en la release (`check-manifest --release` OK).
- **targetSdk 36:** `exported` explícito es obligatorio con intent-filter. Aun así, el script trata la ausencia como exportado (`scripts/check-manifest.sh:130-137`) por si se compilara para otro targetSdk.
- **CI:** los trabajos firmados y los no firmados ejecutan los mismos pasos (ancla en `build-apk.yml:32` y `:136`), así que no pueden divergir. El artefacto `Seguridad-apk` tiene el mismo nombre en los dos, y en cada ejecución solo corre uno.

## Textos para el Abogado Digital (revisión final)
- **GradleException** (`app/build.gradle.kts:137-138`): «Sin clave de subida: usa keystore.properties o -PfirmaDePrueba=true para un APK de prueba firmado con la clave pública de depuración».
- **Descripción de la tarea** (`app/build.gradle.kts:132`): «Falla si se empaqueta una release sin clave de subida y sin -PfirmaDePrueba=true.»
- **README.md**:
  - `:63`, párrafo «El APK queda en … firmado con la clave de subida de `keystore.properties`. Sin ella, `assembleRelease` y `bundleRelease` fallan con «Sin clave de subida…», para que no se reparta por error un APK firmado con la clave de prueba pública…»;
  - `:65-72`, la comprobación del manifiesto;
  - `:88-95`, los trabajos **build**, **build-firma**, **dependency-metadata** y **play-bundle**, y el párrafo sobre los secretos;
  - `:101`, «**Todo cambio de estos dos archivos se revisa como un cambio de seguridad**…»;
  - `:134-136`, «Firma»;
  - `:147-161`, la sección «Entorno de firma» entera, con los 7 pasos y la nota sobre los planes de GitHub.
- **`.github/pull_request_template.md`**: el archivo entero, con las secciones «Qué cambia», «Comprobaciones» y «Cambios de seguridad».
- **`docs/PUBLICAR.md`** `:5`, `:13` y `:107`: los secretos «del entorno `firma` (o, mientras no se muevan allí, del repositorio)» y el trabajo **build-firma**.
- **`scripts/check-manifest.sh`**: los mensajes de error en español (`check-manifest: ERROR: …`, `:47`, `:58-59`, `:119-124`, `:133-135`, `:151-159`, `:163`, `:168-172`). Son herramienta de desarrollo y no salen en la app.
- **Resumen de la CI** (`build-apk.yml:160-174`): «Sin base con la que comparar.», «Todo cambio aquí se revisa como cambio de seguridad.» y las cabeceras de la tabla.

---

# Fix round 1

**Commit:** `5454356` («1.3.0: CI con entorno de firma, manifiesto comprobado y release sin clave de prueba por defecto, revisión 1»). No se ha hecho push. U+180E no cambia: el revisor está de acuerdo.

## Arreglos

### M1. Sumas que cambian sin tocar `<component`
- Arreglado en `build-apk.yml:177-191`. La tabla añade dos columnas, «Sumas añadidas o cambiadas» y «Sumas quitadas», que cuentan las líneas `^+` y `^-[[:space:]]*<sha256` de `git diff -U0`. Lo hace para los dos `verification-metadata.xml`. El diff se calcula una vez por archivo.
- La plantilla de PR (`pull_request_template.md:14`) habla ahora de entradas «añadidas o cambiadas»: un `<component` nuevo, o una suma `<sha256` nueva o distinta en un componente que ya estaba.
- El README (`:93`, `:104`) se ha ajustado igual.

### M2. Base de un pull request
- Arreglado en `build-apk.yml:152-168`. Con `GITHUB_EVENT_NAME=pull_request` (la variable que GitHub pone por defecto):
  - si `HEAD^2` existe, HEAD es el commit de fusión y la base es `HEAD^1`;
  - si no, la base es `PR_BASE` (base.sha).
- En un push se sigue usando `github.event.before`, y después la etiqueta `v*` anterior.
- Las entradas siguen llegando por `env:` y no hay ninguna `${{ }}` dentro del script.

### M3. Planes de GitHub
- `README.md:156`, paso 3: en un repositorio privado, *Required reviewers* solo existe con Enterprise.
- `README.md:160`, paso 7: en un repositorio privado, los rulesets necesitan Pro, Team o Enterprise.
- `README.md:162-166`, nueva nota «Planes de GitHub»:
  - en un repositorio público, todo está disponible;
  - en uno privado con Free, ni entornos ni sus secretos;
  - con Pro o Team: el entorno, sus secretos y las ramas y etiquetas permitidas, pero no los revisores;
  - los rulesets de etiquetas necesitan Pro, Team o Enterprise.
- Fuente (WebFetch, https://docs.github.com/en/actions/reference/workflows-and-actions/deployments-and-environments):
  - «If you are on a GitHub Free, GitHub Pro, or GitHub Team plan, required reviewers are only available for public repositories.»
  - «For users on GitHub Pro or GitHub Team plans, deployment branches and tags are also available for private repositories.»
  - «If you are using GitHub Free, environment secrets are only available in public repositories.» … «you must use GitHub Pro, GitHub Team, or GitHub Enterprise».
- Para los rulesets, WebFetch no devolvió el recuadro de disponibilidad de la página «About rulesets» (solo «for customers on GitHub Team and GitHub Enterprise plans», que se refiere a los rulesets de organización). La frase del README sigue lo que dijo el revisor y la nota habitual de GitHub («public repositories with GitHub Free…, and in public and private repositories with GitHub Pro, GitHub Team, GitHub Enterprise…»). **Queda por verificar** en la revisión final.

### M4. Dos variantes del comando
- `README.md:59-63`: dos variantes del comando, «Con clave (keystore.properties)» y «De prueba, sin clave» con `-PfirmaDePrueba=true`.
- `docs/PUBLICAR.md:115`, §5: dice que hace falta `keystore.properties` y qué hace `-PfirmaDePrueba=true`, que Play no acepta.

### M5. No afirmar como hecho que los secretos solo llegan al entorno
- `README.md:98` y `build-apk.yml:19-23`:
  - hoy, los PR y las ramas no reciben los secretos por las expresiones del flujo;
  - solo los trabajos del entorno `firma` los leerán cuando los secretos estén allí y se hayan borrado del repositorio (enlace a «Entorno de firma»);
  - hasta entonces son secretos del repositorio.
- La plantilla de PR (`:15`) se ha ajustado igual.

## Pruebas
- **YAML:** `python3 -c "import yaml; …safe_load(…)"` da OK. Trabajos: `build`, `build-firma`, `dependency-metadata`, `jvm-check`, `release-apk` y `play-bundle`.
- **El paso del resumen en local:** saqué el script del YAML y lo ejecuté en un clon `--shared` de usar y tirar. La rama base avanza un commit (README), y la rama del PR solo cambia el valor de una `<sha256` en `gradle/verification-metadata.xml`. Después hice el commit de fusión.

  | Caso | Base usada | `gradle/verification-metadata.xml` | `jvm-check/…` |
  |---|---|---|---|
  | `pull_request` con commit de fusión | `HEAD^1`, que es la punta de la rama base | `1 \| 1 \| 0 \| 0 \| 1 \| 1` | todo a 0 |
  | `push` | `before` | lo mismo | todo a 0 |
  | `pull_request` sin commit de fusión | `PR_BASE` | lo mismo | todo a 0 |
  | `PR_BASE='$(touch /tmp/pwned); x'` | ninguna: «Sin base con la que comparar.» | — | — |

  - Con el commit de fusión, el cambio del README de la rama base no se cuenta.
  - Antes, este caso daba `0` en `<component`, que es el fallo de M1. Ahora cuenta 1 suma añadida o cambiada y 1 quitada.
  - En el último caso no se ejecuta nada: `/tmp/pwned` no existe.
- **Verificación completa con `--offline`:**
  - `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets` da BUILD SUCCESSFUL (62 tareas; no cambió código Gradle ni Kotlin);
  - `gradle -p jvm-check test` da BUILD SUCCESSFUL;
  - `bash scripts/check-manifest.sh app/build/outputs/apk/debug/app-debug.apk` da OK.

## Revisión experto-seguridad-cripto (fix round 1)
- **M1 cierra un hueco real de cadena de suministro (CWE-494):** sustituir el artefacto de una dependencia que ya existe cambia solo su `<sha256`, y antes el resumen daba 0. Ahora sale en «Sumas añadidas o cambiadas» (`build-apk.yml:186-189`, cabecera en `:181`).
- **M2:** la base de un PR es `HEAD^1` del commit de fusión, así que no se cuentan los cambios que solo están en la rama base (`build-apk.yml:156-166`). No hay inyección de expresiones: `PR_BASE` y `PUSH_BEFORE` llegan por `env:` (`:150-151`), y un valor hostil se trata como dato (probado).
- **M5:** la documentación ya no promete un aislamiento que depende de una acción del dueño (`README.md:98`, `build-apk.yml:19-23`). Lo que protege hoy, las expresiones de confianza, está descrito como tal.

## Revisión Ingeniero Móvil Senior (fix round 1)
- **M4:** los comandos del README (`:59-63`) y de PUBLICAR §5 (`docs/PUBLICAR.md:115`) cuadran con el comportamiento de `checkReleaseKey` (`app/build.gradle.kts:130-144`). La variante con clave falla sin `keystore.properties`, y la de prueba lleva la clave pública.
- No cambia nada de Gradle, Kotlin, el manifiesto ni el script `check-manifest.sh`.

## Textos para el Abogado Digital (revisión final), fix round 1
- **`README.md`:**
  - `:59-63`, comentarios del bloque de comandos: «# Con clave (keystore.properties): el APK para distribuir.» y «# De prueba, sin clave: firmado con la clave de prueba pública, solo para probar.»;
  - `:93`, el apartado **dependency-metadata**: «…cuántas líneas, cuántas entradas `<component` y cuántas sumas `<sha256` se añaden, cambian o quitan… respecto a la base del PR (la rama en la que se fusionaría)…»;
  - `:98`, «Las compilaciones desde ramas o PR no reciben los secretos de firma: las expresiones del flujo solo los leen en ejecuciones desde `main` o desde una etiqueta `v*`. Cuando los secretos estén en el entorno `firma` y se hayan borrado del repositorio (ver Entorno de firma), solo los podrán leer los trabajos de ese entorno (**build-firma** y **play-bundle**), con las reglas que le pongas en GitHub. Hasta entonces son secretos del repositorio.»;
  - `:104`, «…cuántas entradas `<component` y cuántas sumas `<sha256` se añaden, cambian o quitan (una suma cambiada en un componente que ya estaba no toca su línea `<component`)…»;
  - `:156`, paso 3, última frase: «En un repositorio privado esta opción solo existe con GitHub Enterprise (ver la nota de abajo).»;
  - `:160`, paso 7, última frase: «En un repositorio privado, los rulesets necesitan GitHub Pro, Team o Enterprise.»;
  - `:162-166`, la nota entera «**Planes de GitHub.** …», con sus tres puntos.
- **`docs/PUBLICAR.md:115`:** «Hace falta `keystore.properties` con la clave de subida: sin ella, `assembleRelease` falla con «Sin clave de subida…». Con `-PfirmaDePrueba=true` se compila igual, pero firmado con la clave de prueba pública, que Google Play no acepta y que no se debe distribuir.»
- **`.github/pull_request_template.md`:**
  - `:14`, la frase sobre las entradas «añadidas o cambiadas»;
  - `:15`, «…los secretos de firma solo se leen en los trabajos del entorno `firma` (y, mientras sigan en el repositorio, solo en ejecuciones desde `main` o desde una etiqueta `v*`)…».
- **Resumen de la CI** (`build-apk.yml:181`): las cabeceras nuevas de la tabla, «Sumas añadidas o cambiadas» y «Sumas quitadas».
- **Por verificar en la revisión final:** que los rulesets en repositorios privados necesitan Pro, Team o Enterprise (la página de GitHub no se pudo leer entera).
