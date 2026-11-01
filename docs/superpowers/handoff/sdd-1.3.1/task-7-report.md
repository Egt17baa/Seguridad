# Informe de la tarea 7: versión 1.3.1 y avisos de R8

**Estado:** DONE. Estado final: solo `versionName = "1.3.1"`. El R8 9.1.31 se probó, pasó las cuatro condiciones del paso 4 y se aceptó, pero **el dueño decidió quitarlo** (sección «Decisión del dueño: sin el R8 nuevo»). Los pasos 3 y 4 y las preocupaciones describen ese intento, ya revertido.

**Commits** (rama `ccr-a1a6eabb-765f9g`, con push tras cada uno):

| SHA | Asunto |
|---|---|
| `84c11b7` | build: versión 1.3.1 |
| `ae2c80e` | build: R8 9.1.31 lee los metadatos de Kotlin 2.4 (revertido por `94ddb7f`) |
| `94ddb7f` | build: sin el R8 9.1.31, los avisos de metadatos de Kotlin quedan para la 1.4.0 |

Los tres con autor `Claude <noreply@anthropic.com>`, terminan solo en `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` y `git log -1 --format=%B | grep -cE "Claude-Session|claude\.ai"` da 0 en los tres. Solo se han añadido los archivos propios de la tarea (`app/build.gradle.kts`, `build.gradle.kts`, `gradle/verification-metadata.xml`). `.superpowers/` sigue sin entrar en git.

## Paso 1: versionName

`app/build.gradle.kts:35`: `versionName = "1.3.1"`. `versionCode` no se toca (lo pasa la CI con `-PversionCode`).

Las dos verificaciones globales, con `export ANDROID_HOME=/opt/android-sdk`, `--no-daemon --max-workers=1 --offline`:

| Comando | Resultado |
|---|---|
| `gradle -p jvm-check test` | BUILD SUCCESSFUL, **1269 tests, 0 fallos, 0 errores, 0 omitidos** |
| `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets` | BUILD SUCCESSFUL en 3 min 46 s, **1274 tests, 0 fallos, 0 errores, 0 omitidos** |

(Los recuentos salen de sumar los `TEST-*.xml` de `app/build/test-results/testDebugUnitTest` y `jvm-check/build/test-results/test`.) Los números no han cambiado respecto a lo previsto: 1274 / 1269.

## Paso 2: reproducir los avisos

`./gradlew :app:assembleRelease -PfirmaDePrueba=true --no-daemon --max-workers=1 --offline`.

- Sin los secretos de firma, el build firma con la clave de depuración pública solo si se pasa `-PfirmaDePrueba=true` (`app/build.gradle.kts:20-22`, `:130-144`). No se ha tocado la configuración de firma ni se ha buscado ninguna clave.
- **53 avisos** `WARNING: R8: An error occurred when parsing kotlin metadata. This normally happens when using a newer version of kotlin than the kotlin version released when this version of R8 was created...`, guardados en `.superpowers/sdd/2026-11-01-clavero-1.3.1/r8-antes.txt` (53 líneas; solo líneas `WARNING: R8`).
- Los dos avisos `w:` del compilador de Kotlin (`PhotoCleanScreen.kt:238` y `RedactScreen.kt:365`, «Unnecessary non-null assertion») ya estaban y no son de R8.
- `bash scripts/check-manifest.sh --release app/build/outputs/apk/release/app-release.apk` → `check-manifest: OK`.
- Por qué pasa: `builder-8.13.0.jar` de AGP lleva dentro un R8 8.13.x, y los R8 8.13.19 y 8.13.22 usan `kotlin-metadata-jvm` 2.2.10 (`DependenciesPlugin.kt` de cada etiqueta en r8.googlesource.com), que lee metadatos hasta la 2.3. El proyecto compila con Kotlin 2.4.21.

## Paso 3: R8 más nuevo (intento revertido, ver «Decisión del dueño»)

**Versión elegida: R8 9.1.31.**

- La tabla oficial de Android (<https://developer.android.com/build/kotlin-support>) dice, para **Kotlin 2.4: R8 9.1.29** como mínimo. Para Kotlin 2.3 pone 8.13.19.
- `https://dl.google.com/dl/android/maven2/com/android/tools/r8/maven-metadata.xml` no publica 9.1.29 ni 9.1.30 (aunque existen las etiquetas en el repositorio de R8): la versión publicada más baja desde 9.1.29 es **9.1.31**, sin sufijo alpha/beta.
- Comprobación propia en el código fuente: la etiqueta `9.1.31` de R8 (<https://r8.googlesource.com/r8/+/refs/tags/9.1.31>) usa `kotlinMetadataVersion = "2.3.10"` (commit «Update Kotlin-metadata-jvm to 2.3.10», que sube desde 2.2.10). `kotlin-metadata-jvm` 2.3.x lee metadatos de Kotlin 2.4.
- No he encontrado unas «notas de versión» de R8 con la frase «lee Kotlin 2.4»; mi base es la tabla oficial, más el valor de `kotlinMetadataVersion` en la etiqueta. Lo digo para que el revisor lo sepa.
- Las notas de AGP 9.1.0 (<https://developer.android.com/build/releases/agp-9-1-0-release-notes>) listan dos cambios de R8 de la serie 9.1: repaquetado por defecto al paquete sin nombre (`-dontrepackage` lo desactiva) y nombres para `-maximumremovedandroidloglevel`.

Cambio en `build.gradle.kts:1-12` (bloque `buildscript` antes de `plugins`, con repositorio `google()` y `classpath("com.android.tools:r8:9.1.31")`, y un comentario en inglés como el resto de los scripts). No se ha tocado ninguna regla de R8 (`app/proguard-rules.pro` queda igual).

`./gradlew --write-verification-metadata sha256 help --no-daemon --max-workers=1` (sin `--offline`):

- Diff de `gradle/verification-metadata.xml`: **+8 líneas, 0 quitadas**, líneas 1605-1612, solo `<component group="com.android.tools" name="r8" version="9.1.31">` con `r8-9.1.31.jar` y `r8-9.1.31.pom`. No ha tocado ni reescrito ninguna otra entrada.
- Las sumas coinciden con las que publica Google en `r8-9.1.31.jar.sha256` (`3b4de305…8286f`) y `r8-9.1.31.pom.sha256` (`4bbdb08e…bcd`); el `.pom` también lo he comprobado con `sha256sum` de lo descargado.
- No existe `r8-9.1.31.module` en Google Maven (404), el POM no tiene dependencias ni padre: no arrastra nada más.
- Licencia: el POM de 9.1.31 declara BSD-3-Clause, y además, por el código que lleva empaquetado dentro, la misma lista que ya declara el R8 8.13.19 (con «CDDL 1.1 and GPL 2.0 with Class-path Exception» y «LGPL 2.1»). Es la misma mezcla que ya tiene el R8 que viene dentro de `builder-8.13.0.jar` de AGP. Es una herramienta de compilación: no está en `releaseRuntimeClasspath`, no entra en el APK y `checkLicense` no la ve. Lo apunto como concepto para el Abogado Digital si quiere verlo (ver abajo).
- Prueba de que la caché no engaña (el README avisa de que con la caché llena Gradle puede no apuntar algún POM): con `GRADLE_USER_HOME` vacío, `./gradlew help --no-daemon --max-workers=1` con la verificación estricta del archivo → BUILD SUCCESSFUL (descargó todo y lo verificó contra las sumas guardadas). Con JDK 21 (en la sesión solo hay 21; la CI usa 17).

## Paso 4: condiciones de aceptación (del intento revertido)

| Condición | Resultado |
|---|---|
| Los avisos desaparecen de `assembleRelease` | **Sí**: 53 → 0 con «kotlin metadata» (`r8-despues.txt` tiene 2 líneas de otro tipo, ver preocupación 1). Reconstruido con `:app:minifyReleaseWithR8 --rerun` para que R8 se ejecute de verdad. |
| Las dos verificaciones globales, 0 fallos | **Sí**: jvm-check 1269 / 0 fallos; app 1274 / 0 fallos (el test se ejecutó de nuevo, no `UP-TO-DATE`). `lintRelease` y `bundleRelease` también verdes (el AAB de 6,4 MB se genera). |
| `check-manifest.sh` OK sobre el APK de publicación | **Sí**: `check-manifest: OK (app/build/outputs/apk/release/app-release.apk)` con `--release`. |
| `aapt2 dump badging`: misma etiqueta y mismos 6 permisos | **Sí**, ver abajo. |

**Comparación de `aapt2`** (no hay APK 1.3.0 en la sesión: la base es el APK de release 1.3.1 con el R8 de AGP, paso 2). `diff badging-antes.txt badging-despues.txt` → **idénticos**:

- `package: name='io.github.egt17baa.seguridad' versionCode='1' versionName='1.3.1'` (solo `versionName` cambia respecto a la 1.3.0, como se esperaba).
- `application-label:'Clavero'`, y 0 etiquetas localizadas distintas de «Clavero».
- 6 permisos de `aapt2 dump permissions`: `uses-permission` INTERNET, CAMERA, USE_BIOMETRIC, USE_FINGERPRINT; `permission` y `uses-permission` de `io.github.egt17baa.seguridad.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. Es la lista `EXPECTED_PERMISSIONS` de `scripts/check-manifest.sh`.

**Contenido del APK, R8 no está dentro:**

- Entradas del zip: 493 en los dos; la única diferencia son dos nombres de `META-INF/services/…` ofuscados (`b8.t`, `c8.a` → `d9`, `v90`). Comprobado que cada uno apunta a la clase correcta en su `mapping.txt` (`AndroidDispatcherFactory`/`AndroidExceptionPreHandler`, con la interfaz fusionada).
- 0 entradas `com/android/tools/…` en el zip; 0 cadenas `com/android/tools/r8` en `classes.dex`; 0 líneas `com.android.tools.r8` en `mapping.txt`.
- Un único `classes.dex` (4 285 812 → 4 166 820 bytes sin comprimir; APK 2 931 593 → 2 947 878 bytes, +16 KB).
- `configuration.txt` (reglas de conservación fusionadas): idéntico salvo las rutas de la caché de transformaciones y el salto de línea final. Las 197 semillas de `app.seguridad.*` en `seeds.txt` son idénticas; los 7 `$$serializer` de la app siguen en `mapping.txt`. `seeds.txt` pierde 65 semillas de miembros de librerías (Compose `ViewLayer`, `VectorDrawableCompat`, dos proveedores de Bouncy Castle que ya se eliminaban en las dos versiones, etc.).

## Archivos (del intento, antes de revertir)

| Archivo | Cambio |
|---|---|
| `app/build.gradle.kts:35` | `versionName = "1.3.1"` |
| `build.gradle.kts:1-12` | bloque `buildscript` con `com.android.tools:r8:9.1.31` |
| `gradle/verification-metadata.xml:1605-1612` | entrada `com.android.tools:r8:9.1.31` (jar y pom) |

En `.superpowers/sdd/2026-11-01-clavero-1.3.1/` (no versionado): `r8-antes.txt` (53 líneas), `r8-despues.txt` (2 líneas), `badging-antes.txt` y `badging-despues.txt` (idénticos), y este informe.

## Autorrevisión del diff (del intento, antes de revertir)

- 20 líneas añadidas, 0 quitadas. Sin código de la app ni reglas de R8 tocadas. El comentario del `build.gradle.kts` dice cuándo quitar el cambio.
- `buildscript` va antes de `plugins` (obligatorio en Kotlin DSL). `repositories { google() }` solo en el `buildscript`: `dependencyResolutionManagement` con `FAIL_ON_PROJECT_REPOS` no afecta al classpath del script, y R8 solo está en Google Maven.
- La verificación de firmas sigue en `false` (como el resto del proyecto): lo único que protege es la suma, que coincide con la publicada por Google.

## Preocupaciones (del intento, antes de revertir)

1. **Dos avisos nuevos de R8**, de otro tipo: `WARNING: R8: Class file resource provider does not support async parsing: com.android.builder.dexing.r8.ClassFileProviderFactory$OrderedClassFileResourceProvider`. Salen porque AGP 8.13.0 está hecho para R8 8.13, no para 9.1. Son avisos de rendimiento (R8 analiza en serie), no cambian el resultado. La condición del paso 4 se refiere a los avisos de metadatos y se cumple; quien revise puede decidir que 53 → 2 de otro tipo no compensa.
2. **R8 9.1.x produce un dex distinto al de la 8.13 que AGP probó.** El cambio visible: repaquetado por defecto a un paquete plano (`app.seguridad.crypto.Aead -> j5.a` pasa a `b5`), 257 clases menos en el dex (4 626 → 4 369), 13 clases más de `app.seguridad` en el mapa y el perfil base (`baseline.prof`) reescrito (6 232 → 9 803 bytes). Las reglas y las semillas de la app son las mismas, pero **los 1274 tests corren sobre el build de depuración sin minificar**: ningún test cubre el APK de release. Lo cubre solo la prueba del dueño en el móvil con el APK firmado, que ya está prevista. Convendría que esa prueba pase por una bóveda nueva, abrir una copia importada (serializadores `kotlinx.serialization`), el escáner de QR y la huella, que son los caminos con reflexión o con componentes cargados por nombre.
3. **No tengo notas de versión de R8 que digan «Kotlin 2.4»** como tal; la base es la tabla oficial de Android y `kotlinMetadataVersion = 2.3.10` en la etiqueta 9.1.31.
4. Jar de 9.1.31 declara en su POM licencias GPL con excepción de classpath y LGPL por código empaquetado, igual que el R8 8.13.19 que ya traía AGP. No entra en el APK. La regla del proyecto es «sin copyleft»; si el controlador prefiere que lo mire el Abogado Digital, ya está apuntado.
5. La CI usa JDK 17; yo he regenerado con JDK 21. Para este cambio (un solo artefacto, jar y pom, sin padre ni `.module`) no hay diferencia posible, y la prueba de la caché vacía lo respalda; si el controlador quiere repetirlo con el comando completo del README (§ «Verificación de dependencias»), es solo comprobar que el diff de `verification-metadata.xml` no crece.
6. Para volver atrás: revertir `ae2c80e` (borra el bloque `buildscript` y las 8 líneas de sumas). Los avisos pasarían a la 1.4.0, con un AGP que traiga R8 ≥ 9.1.29.

## Decisión del dueño: sin el R8 nuevo

El dueño decidió quitar el R8 fijado («Quitarlo»): el dex de publicación cambia mucho (repaquetado plano, 257 clases menos en el dex, otro `baseline.prof`), ningún test cubre el APK minificado, y la ganancia es solo estética (los avisos). Los 53 avisos quedan como en la 1.3.0 y se quitarán con un AGP 9 que ya traiga un R8 ≥ 9.1.29 (1.4.0).

**Qué se ha revertido.** `git revert --no-commit ae2c80e` y un commit propio, `94ddb7f` («build: sin el R8 9.1.31, los avisos de metadatos de Kotlin quedan para la 1.4.0», cuerpo con el motivo, termina solo en `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`, autor `Claude <noreply@anthropic.com>`, `grep -cE "Claude-Session|claude\.ai"` = 0). Sin reset, sin amend, sin rebase, sin `--force`. Push hecho: `ae2c80e..94ddb7f`.

**Estado final comprobado:**

- `git diff e947908 -- build.gradle.kts gradle/verification-metadata.xml` → vacío (0 líneas): los dos archivos son idénticos a `e947908`.
- `git diff e947908 --stat` → un único archivo: `app/build.gradle.kts | 2 +-`, y el cambio es `-versionName = "1.3.0"` / `+versionName = "1.3.1"` (`app/build.gradle.kts:35`). Es lo único de la tarea 7 que queda.

**Verificación sobre la cabeza revertida (`94ddb7f`)**, con `ANDROID_HOME=/opt/android-sdk`, `--no-daemon --max-workers=1 --offline`:

| Comando | Resultado |
|---|---|
| `gradle -p jvm-check test` | BUILD SUCCESSFUL, ejecutado de nuevo con `cleanTest test` (la primera vez salió `UP-TO-DATE`): **1269 tests, 0 fallos, 0 errores, 0 omitidos** |
| `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets` | BUILD SUCCESSFUL en 5 min, los seis ejecutados (ninguno `UP-TO-DATE`): **1274 tests, 0 fallos, 0 errores, 0 omitidos** |
| `./gradlew :app:assembleRelease -PfirmaDePrueba=true` | BUILD SUCCESSFUL en 3 min 32 s, `minifyReleaseWithR8` ejecutado; **53 avisos** `WARNING: R8: An error occurred when parsing kotlin metadata` y 0 avisos de «async parsing» (el estado de la 1.3.0) |
| `bash scripts/check-manifest.sh --release app/build/outputs/apk/release/app-release.apk` | `check-manifest: OK (app/build/outputs/apk/release/app-release.apk)` |
| `aapt2 dump badging` (+ `dump permissions`) | **idéntico a `badging-antes.txt`**: `versionName='1.3.1'`, `application-label:'Clavero'`, 0 etiquetas localizadas distintas, los mismos 6 permisos |

El APK de la cabeza revertida es el mismo que el de antes del intento (el del paso 2): mismas 493 entradas, `classes.dex` byte a byte igual (SHA-256 `a1ba0afa…2d9d`), `mapping.txt` igual, y la única entrada con otro CRC es `META-INF/version-control-info.textproto` (lleva el SHA del commit de la compilación). Los archivos `r8-despues.txt` y `badging-despues.txt` de esta carpeta son del intento revertido y no describen el estado final.

## Textos para el Abogado Digital (revisión final)

Esta tarea no añade ningún texto de la interfaz, de la política ni de la ficha. Con el R8 fijado revertido tampoco queda ningún dato de licencias que revisar: no se añade ninguna dependencia ni herramienta (el punto sobre la licencia de `com.android.tools:r8:9.1.31` ya no aplica).

## Revisión experto-seguridad-cripto

- **Superficie de ataque y cadena de suministro.** El estado final solo cambia `app/build.gradle.kts:35` (`versionName = "1.3.1"`). `build.gradle.kts` y `gradle/verification-metadata.xml` son idénticos a `e947908` (`git diff` vacío): ninguna herramienta, dependencia ni suma nueva. `verify-metadata` sigue en `true` (`gradle/verification-metadata.xml:4`).
- **Permisos y componentes.** Sin permisos, componentes ni conexiones nuevos: `check-manifest.sh --release` OK y los 6 permisos idénticos a los de `scripts/check-manifest.sh` (`EXPECTED_PERMISSIONS`); la única red sigue siendo `api.pwnedpasswords.com`.
- **Minificación y código enviado.** Reglas de R8 sin tocar (`app/proguard-rules.pro` intacto); el dex (`classes.dex`) y el `mapping.txt` del release son byte a byte los de la compilación anterior al intento, es decir, el R8 de AGP 8.13 de siempre. No hay cambio de criptografía, formatos, secretos en memoria, logs ni `rememberSaveable`.
- **Riesgo aceptado.** Los 53 avisos de metadatos de Kotlin siguen. Son avisos de R8 sobre metadatos de Kotlin que no sabe leer: solo importan a quien use la reflexión de Kotlin (`kotlin-reflect`), y la app no depende de ella (no está en `app/build.gradle.kts` ni en `gradle/libs.versions.toml`; `kotlin.reflect.KProperty` en `CodesSession.kt:5` es la interfaz de la biblioteca estándar para propiedades delegadas). No afectan a las reglas de conservación. Es el mismo comportamiento de la 1.3.0; se resuelve en la 1.4.0 con AGP 9.

## Revisión Ingeniero Móvil Senior

- **Build.** `versionName` en `app/build.gradle.kts:35`; `versionCode` sigue viniendo de `-PversionCode` (`app/build.gradle.kts:34`). El `build.gradle.kts` de la raíz queda sin `buildscript`, como estaba.
- **Release.** `assembleRelease -PfirmaDePrueba=true` y `check-manifest.sh --release` verdes; el APK es el mismo que el del paso 2 salvo el SHA de commit de `version-control-info.textproto`. Los tests (1274 en app, 1269 en jvm-check) corren sobre el build de depuración; el release minificado no tiene tests automáticos y por eso se prefirió no cambiar de R8. Sigue cubriéndolo la prueba del dueño con el APK firmado.
- **Pendiente para la 1.4.0.** Subir a un AGP 9 que traiga R8 ≥ 9.1.29 (tabla oficial de Android para Kotlin 2.4, <https://developer.android.com/build/kotlin-support>); entonces cambiará el dex (repaquetado plano por defecto) y habrá que probar el APK minificado a conciencia (bóveda nueva, importar copia, escáner QR, huella, giro de pantalla).
