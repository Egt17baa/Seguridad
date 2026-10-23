# Seguridad

App Android de seguridad y criptografía, sin cuentas, sin nube y sin anuncios. Todo se queda en el móvil.

Código privado. Todos los derechos reservados.

## Qué hace (1.0.0)

- **Bóveda** de accesos, frases semilla, notas y códigos 2FA, cifrada con una clave de datos envuelta con Argon2id y, si se quiere, con la huella (Android Keystore). Se bloquea por inactividad o al salir. Exportar e importar.
- **Códigos 2FA** TOTP (RFC 6238), con escáner QR (ZXing, solo pide la cámara al escanear) o con la clave a mano.
- **Cifrar y descifrar texto y archivos** con AES-256-GCM, ChaCha20-Poly1305, Twofish, Serpent, Camellia o la cascada, con contraseña (Argon2id) o con una clave AES-256 aleatoria. Modo frase semilla con BIP-39.
- **Herramientas:** generador de contraseñas y frases EFF con su entropía, comprobador de contraseñas filtradas (Have I Been Pwned con k-anonimato: solo salen 5 caracteres del hash SHA-1) y huella SHA-256 de archivos.
- **Ajustes:** bloqueo, borrado del portapapeles, coste de Argon2id, tema claro u oscuro, copia de seguridad y la pantalla de licencias.

Protecciones en toda la app: `FLAG_SECURE`, filtro de toques bajo superposiciones, portapapeles marcado como sensible y borrado automático, sin copias de Android (`allowBackup="false"`), sin logs con datos, y red solo hacia `api.pwnedpasswords.com` con un cliente que rechaza los certificados de eMudhra. Permisos: `INTERNET`, `CAMERA` (solo se pide al escanear un QR) y los de biometría que declara `androidx.biometric` (`USE_BIOMETRIC` y `USE_FINGERPRINT`).

## Compatibilidad con CriptoPanel

Seguridad escribe sus propios formatos (`SG1:`, `.sgenc`, `seguridad-vault`) y **lee también los de CriptoPanel**:

- textos `CP1:` y archivos `.cpenc`;
- la copia exportada de la bóveda de CriptoPanel (`criptopanel-vault`, versiones 1 y 2, y las antiguas sin campo `version`). Al importarla se vuelve a cifrar con el formato nuevo.

Lo que se cifre de nuevo sale en el formato de Seguridad. Estos formatos se leerán siempre: hay tests con datos reales generados por CriptoPanel 1.10.0.

## Compilar

Hace falta el JDK 17 o posterior y el SDK de Android (compileSdk 36).

```bash
export ANDROID_HOME=/ruta/al/android-sdk
./gradlew :app:testDebugUnitTest :app:lintRelease :app:assembleRelease :app:checkLicense --no-daemon --max-workers=1
```

El APK queda en `app/build/outputs/apk/release/app-release.apk`. Sin clave de subida se firma con la clave de prueba pública (`app/debug.keystore`); ver [Firma](#firma).

### jvm-check

`jvm-check/` es otro proyecto Gradle que compila todo lo que no depende de Android (cripto, bóveda, red, ajustes) y ejecuta sus tests en una JVM normal, sin el SDK de Android y solo con Maven Central. Usa la toolchain de Java 21.

```bash
./gradlew -p jvm-check test --no-daemon --max-workers=1
# o, con un Gradle 8.14.x instalado:
gradle -p jvm-check test --no-daemon --max-workers=1
```

## Integración continua

`.github/workflows/build-apk.yml` se ejecuta en cada push a `main` y a `claude/**`, en cada pull request, en las etiquetas `v*` y a mano:

- **build:** tests, lint, comprobación de licencias y APK de release `Seguridad.apk`, firmado con los secretos `SEGURIDAD_*`. Escribe en el resumen del trabajo la huella SHA-256 del certificado y avisa si es la de la clave de prueba.
- **jvm-check:** los tests en la JVM.
- **publish** (solo en `main`): publica el APK en la versión `apk-latest` de *Releases*. No ve los secretos.
- **play-bundle** (etiquetas `v*` y a mano): el AAB para Google Play. Sin los secretos falla, porque Play no acepta la clave de prueba.

Todas las acciones están fijadas por SHA, los permisos son de solo lectura salvo en `publish`, y los secretos solo existen en el paso de Gradle que firma.

### Verificación de dependencias

`gradle/verification-metadata.xml` (y `jvm-check/gradle/verification-metadata.xml`) guardan el SHA-256 de cada dependencia y plugin. Gradle rechaza cualquier archivo que no coincida, venga del repositorio que venga. Al cambiar o añadir una dependencia hay que regenerarlos.

Hazlo **como la CI**: con el JDK 17 y con una caché de Gradle vacía para cada proyecto. Con una caché ya llena, o con otro JDK, Gradle resuelve algunos POM y BOM por otro camino y no los apunta, y luego la CI falla con «Dependency verification failed … Checksums are missing».

```bash
export JAVA_HOME=/ruta/al/jdk-17
rm -rf build app/build jvm-check/build
GRADLE_USER_HOME=$(mktemp -d) ./gradlew --write-verification-metadata sha256 help :app:assembleRelease :app:lintRelease \
  :app:testDebugUnitTest :app:checkLicense :app:bundleRelease :app:assembleDebug :app:lintDebug --no-daemon --max-workers=1
GRADLE_USER_HOME=$(mktemp -d) ./gradlew -p jvm-check --write-verification-metadata sha256 help test --no-daemon --max-workers=1
```

Después, comprueba con otra caché vacía y **sin** `--write-verification-metadata` que pasan los comandos de la CI.

Revisa el diff antes de hacer commit: cada suma nueva tiene que corresponder a una dependencia que has añadido tú.

### Licencias

Solo se aceptan dependencias gratuitas y de licencia permisiva. `:app:checkLicense` (plugin `com.github.jk1.dependency-license-report`) falla si alguna biblioteca de `releaseRuntimeClasspath`, es decir, de las que van dentro del APK, no declara una licencia de `config/allowed-licenses.json`:

Apache-2.0, MIT, BSD-2-Clause, BSD-3-Clause, ISC y CC BY. No se permiten GPL, AGPL, LGPL, SSPL ni licencias propietarias.

Para actualizar la lista:

1. Ejecuta `./gradlew :app:checkLicense`. Si falla, el error nombra la biblioteca y su licencia, y `app/build/reports/dependency-license/dependencies-without-allowed-license.json` trae la regla que habría que añadir.
2. Comprueba la licencia en el repositorio de la biblioteca. Si es permisiva y gratuita, añade su nombre exacto (tal como sale en el informe, después de normalizar) como una entrada `{ "moduleLicense": "…" }` en `config/allowed-licenses.json`. Si es copyleft o propietaria, no la añadas: cambia de biblioteca.
3. Añade su texto a la pantalla de licencias de la app.

## Firma

El APK de release y el AAB se firman con la clave de subida si existe, en `keystore.properties` (en local, nunca en Git) o en variables de entorno (en la CI). Si no, el APK se firma con la clave de prueba pública, que sirve para probar pero que cualquiera puede usar: no distribuyas un APK así.

Secretos del repositorio (*Settings → Secrets and variables → Actions*):

| Secreto | Valor |
|---|---|
| `SEGURIDAD_KEYSTORE_BASE64` | el almacén en base64: `base64 -w0 tu.jks` |
| `SEGURIDAD_KEYSTORE_PASSWORD` | contraseña del almacén |
| `SEGURIDAD_KEY_ALIAS` | alias de la clave |
| `SEGURIDAD_KEY_PASSWORD` | contraseña de la clave |

La CI decodifica el almacén en un archivo temporal del runner, exporta `SEGURIDAD_KEYSTORE_FILE`, compila y lo borra. Huella esperada del certificado y pasos de Google Play: [docs/PUBLICAR.md](docs/PUBLICAR.md).

## Comprobaciones manuales en el móvil

Lo que no se puede probar en la CI (no hay móvil ni emulador con huella). Antes de cada versión:

- [ ] Tema claro, y el marco oscuro que se ve al arrancar antes de que cargue la interfaz.
- [ ] Huella dactilar: activarla, abrir la bóveda con ella, y qué pasa al añadir o borrar una huella en el sistema.
- [ ] Escáner QR con la cámara real, y con el permiso de cámara denegado (debe quedar la entrada a mano).
- [ ] Bordes de la pantalla: barra de estado, barra de navegación y teclado, en vertical y en horizontal.
- [ ] Importar una copia real de la bóveda de CriptoPanel y abrir un texto `CP1:` y un archivo `.cpenc` reales.
- [ ] Menús desplegables bajo una superposición (filtro de toques) y exportar la bóveda sin tener bóveda (debe avisar).
- [ ] Pantalla de licencias: todos los textos se abren.
