# Seguridad

App Android de seguridad y criptografía, sin cuentas, sin nube y sin anuncios. La bóveda y los datos cifrados se guardan localmente en el móvil.

**Licencia: [Apache-2.0](LICENSE).** Se permite usar, estudiar, modificar y redistribuir el código conforme a esa licencia. Las dependencias conservan sus propias licencias. Copyright 2026 Egt17baa ([NOTICE](NOTICE)).

**Gratuita y sin monetizar, por compromiso.** La app no tendrá nunca precio, anuncios, compras integradas, versión de pago, donaciones que condicionen funciones ni uso de datos con fines comerciales. No es solo una preferencia: la publicación se plantea como la de un particular sin actividad económica, y cualquier cambio de modelo obligaría a revisar sus obligaciones legales (consumo, Cyber Resilience Act, responsabilidad por productos).

**Privacidad:** la app no recoge datos. [Política de privacidad](https://egt17baa.github.io/Seguridad/privacidad.html) (fuente: [`docs/privacidad.html`](docs/privacidad.html)).

**Identificador:** `io.github.egt17baa.seguridad` desde la 1.1.1 (antes `app.seguridad`). Android lo trata como otra app: para pasar de una versión anterior hay que exportar la bóveda en la antigua e importarla en la nueva (pasos en [`docs/PUBLICAR.md`](docs/PUBLICAR.md#2-descargar-el-apk-instalación-directa)).

## Funciones

- **Bóveda** de accesos, frases semilla, notas y códigos 2FA, cifrada con una clave de datos envuelta con Argon2id y, si se quiere, con la huella (Android Keystore). Desde la 1.3.0, al activar la huella la clave se crea en el chip de seguridad StrongBox si el móvil lo tiene, y si no o si falla, en la zona aislada del procesador (TEE). Quien ya tenía la huella activada en la 1.2.2 sigue con la clave en la TEE hasta que desactive la huella y vuelva a activarla (Ajustes dice dónde está). Se bloquea por inactividad o al salir.
  - **Búsqueda**, **favoritos** y **etiquetas**. La búsqueda mira título, usuario, URL y etiquetas, nunca contraseñas, frases ni notas.
  - **Historial de contraseñas:** las últimas 5 de cada acceso.
  - **Salud de la bóveda:** contraseñas repetidas, débiles, antiguas y sin 2FA. Si se pulsa «Comprobar filtraciones», consulta Have I Been Pwned con k-anonimato, una contraseña a la vez; nunca lo hace por su cuenta.
  - **Adjuntos cifrados** en las entradas (hasta 5 MB cada uno y 50 MB en total), cada uno con su propia clave AES-256-GCM.
  - **Copia `.sgbak`:** exportar e importar la bóveda con sus adjuntos. Se siguen leyendo las exportaciones JSON de la 1.0.x y de CriptoPanel.
  - **Copia automática** cifrada a una carpeta que eliges (almacenamiento del sistema), con rotación de las 3, 5 o 10 últimas. Solo se hace con la bóveda abierta, para comprobar cada adjunto; si estaba bloqueada, se hace al abrirla. Si falla, lo avisa en Ajustes y no toca la bóveda. Desde la 1.2.1 la rotación solo borra las copias que la propia app creó (las recuerda por su identificador, no por el nombre), así que otra app no puede hacer que se borren las buenas dejando archivos con fechas inventadas. Las copias hechas con la 1.2.0 o antes ya no se borran solas: puedes borrarlas a mano. Al importar otra bóveda, las copias que ya había dejan de rotarse y se quedan en la carpeta.
- **Códigos 2FA** TOTP (RFC 6238), con escáner QR (ZXing, solo pide la cámara al escanear) o con la clave a mano. **Importar de Google Authenticator** (`otpauth-migration://`) y **exportar un código por QR** para otra app.
- **Cifrar y descifrar texto y archivos** con AES-256-GCM, ChaCha20-Poly1305, Twofish, Serpent, Camellia o la cascada, con contraseña (Argon2id) o con una clave AES-256 aleatoria. Modo frase semilla con BIP-39 (las listas de palabras BIP-39 en inglés y en español, MIT).
- **Contactos (1.2.0): cifrado y firmas con clave pública**, solo entre usuarios de Seguridad y sin servidor: tú pasas el texto o el archivo por el canal que ya uses (WhatsApp, Telegram, correo).
  - **Mi identidad:** una por persona, guardada en la bóveda (viaja en las copias `.sgbak`). Se comparte como **tarjeta** (QR o texto `SGK1:`) con una huella de 30 cifras para compararla por otro canal.
  - **Contactos:** se añaden escaneando su QR (quedan verificados) o pegando su tarjeta (quedan sin verificar hasta comparar la huella). Si llega una tarjeta con las mismas claves de cifrado que otra persona, o con claves distintas bajo el mismo nombre, la app avisa y no deja guardarla a ciegas.
  - **Cifrar para un contacto** un texto (`SGM1:`) o un archivo (`.sgenc`), con la firma de tu identidad por defecto. El cifrado es híbrido, diseñado para resistir también a ordenadores cuánticos futuros (X-Wing, en proceso de estandarización, con HPKE de la RFC 9180). **Firmar** un texto (`SGS1:`) o un archivo (`.sgsig`) con Ed25519 (RFC 8032): demuestra a tus contactos que es tuyo y que no ha cambiado.
  - **Abrir lo recibido:** una sola entrada que reconoce lo que se pega o se abre (mensaje, texto firmado, tarjeta, archivo cifrado o firma) y dice siempre de quién es: «De Ana (verificado)», «sin verificar», «firmado por una clave que no está en tus contactos» o «sin firmar».
  - **Lo que no protege:** si alguien roba tu identidad (por ejemplo, con una copia `.sgbak` y tu contraseña maestra), puede leer lo que te enviaron antes y firmar como tú. Los metadatos (quién escribe a quién, cuándo y el tamaño) los ve el canal. No hay auditoría independiente.
- **Herramientas:** generador de contraseñas y frases EFF con su entropía, PIN al azar sin los patrones más usados, códigos de recuperación y respuestas inventadas para preguntas de seguridad (con las palabras de la lista BIP-39 en español), comprobador de contraseñas filtradas (Have I Been Pwned con k-anonimato: solo salen 5 caracteres del hash SHA-1) y huella SHA-256 de archivos.
- **Herramientas de imagen (1.3.0),** con la imagen que eliges en el selector del sistema (sin permiso de fotos), analizada en el móvil y sin enviarla a ningún sitio; solo sale la copia que compartes. Limpiar foto no cambia lo que se ve, y Tachar solo tapa lo que marcas tú. Ninguna cambia el original: comparte la copia.
  - **Limpiar foto:** quita la ubicación y otros datos ocultos del archivo, y antes enseña lo que trae. La copia solo conserva la imagen, sus datos de color (como el perfil de color) y, en las JPEG, la orientación (el giro) de la foto; no quita las marcas invisibles que pueda llevar la propia imagen. Guarda o comparte una copia llamada `copia-de-foto.<ext>` (`jpg`, `png` o `webp`). Admite JPEG, PNG y WebP; HEIC y HEIF desde Android 9, y AVIF desde Android 12. Esas tres se guardan como JPEG y, si pasan de unos 24 megapíxeles, se reducen a ese tamaño.
  - **Tachar en imagen:** tapa con bloques negros lo que no quieras enseñar; la copia (`imagen-tachada.jpg` o `imagen-tachada.png`) se crea de nuevo, sin los datos ocultos del original.
  - **Inspector de QR:** enseña qué hay en un QR (de la cámara o de una imagen) sin abrir nada, con avisos de señales típicas de engaño. No garantiza que un enlace sea seguro.
  - Al compartir, la copia temporal está en `cacheDir/compartir/` (un `FileProvider` no exportado) y se borra al compartir otra, al volver a la app pasados 10 minutos o al arrancarla.
- **Ajustes:** bloqueo, borrado del portapapeles, coste de Argon2id, tema claro u oscuro, copia de seguridad y la pantalla de licencias.

Protecciones en toda la app: `FLAG_SECURE`, filtro de toques bajo superposiciones, portapapeles marcado como sensible y borrado automático, sin copias de Android (`allowBackup="false"`), sin logs con datos, y red solo hacia `api.pwnedpasswords.com` con un cliente que rechaza los certificados de eMudhra. Permisos: `INTERNET`, `CAMERA` (solo se pide al escanear un QR, de 2FA, de la tarjeta de un contacto o en el Inspector de QR) y los de biometría que declara `androidx.biometric` (`USE_BIOMETRIC` y `USE_FINGERPRINT`). Además, la biblioteca `androidx.core` declara `io.github.egt17baa.seguridad.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`, un permiso de firma que solo tiene la propia app: protege los receptores internos que registren la app o sus bibliotecas, para que ninguna otra app les envíe avisos. No da acceso a nada del móvil. Sin permiso de fotos ni de ubicación (`READ_MEDIA_IMAGES`, `ACCESS_MEDIA_LOCATION`): las imágenes se eligen con el selector del sistema.

## Compatibilidad con CriptoPanel

Seguridad escribe sus propios formatos (`SG1:`, `.sgenc`, `seguridad-vault` y, desde la 1.1.0, `seguridad-backup` en archivos `.sgbak`) y **lee también los de CriptoPanel**:

- textos `CP1:` y archivos `.cpenc`;
- la copia exportada de la bóveda de CriptoPanel (`criptopanel-vault`, versiones 1 y 2, y las antiguas sin campo `version`). Al importarla se vuelve a cifrar con el formato nuevo.

Copias de la bóveda:

- **`.sgbak` (`seguridad-backup` v1):** lo que escribe la 1.1.0 al exportar y la copia automática. Es un ZIP con la bóveda cifrada y los adjuntos cifrados.
- **JSON de la 1.0.x y de CriptoPanel:** se siguen importando. Una JSON con adjuntos se rechaza y se pide importar el `.sgbak`.
- El diseño describe los dos formatos nuevos (`seguridad-backup` v1 y el de adjunto v1) byte a byte en [docs/superpowers/specs/2026-10-23-seguridad-design.md](docs/superpowers/specs/2026-10-23-seguridad-design.md).

**Formatos nuevos de la 1.2.0** (se leerán siempre; hay vectores guardados en `app/src/test/resources/vectors/`): cabecera `SG` + `0x02`, con tarjeta `SGK1:`, mensaje `SGM1:`, firma de texto `SGS1:`, archivo cifrado para un contacto (`.sgenc`, tipo 3) y firma de archivo (`.sgsig`). Usan solo primitivas estándar de Bouncy Castle: Ed25519 (RFC 8032), HPKE (RFC 9180) con X-Wing (borrador `draft-ietf-hpke-pq`), HKDF-SHA256 y ChaCha20-Poly1305.

**Aviso: después de abrir la bóveda con la 1.2 no se puede volver a la 1.1.** La bóveda pasa a la versión 3 y la copia `.sgbak` a la versión 2. La 1.1 no puede usar la bóveda nueva: con la contraseña maestra no la abre, y con la huella puede enseñar las entradas pero no guardar cambios. No se pierden datos. Con la copia nueva dice que es de una versión más nueva. La 1.2 sigue leyendo las bóvedas y copias de la 1.1 y las reescribe en el formato nuevo. Hacer una copia `.sgbak` antes de actualizar sigue siendo buena idea.

Lo que se cifre de nuevo sale en el formato de Seguridad. Estos formatos se leerán siempre: hay tests con datos reales generados por CriptoPanel 1.10.0.

## Compilar

Hace falta el JDK 17 o posterior y el SDK de Android (compileSdk 36).

```bash
export ANDROID_HOME=/ruta/al/android-sdk
# Con clave (keystore.properties): el APK para distribuir.
./gradlew :app:testDebugUnitTest :app:lintRelease :app:assembleRelease :app:checkLicense --no-daemon --max-workers=1
# De prueba, sin clave: firmado con la clave de prueba pública, solo para probar.
./gradlew :app:testDebugUnitTest :app:lintRelease :app:assembleRelease :app:checkLicense -PfirmaDePrueba=true --no-daemon --max-workers=1
```

El APK queda en `app/build/outputs/apk/release/app-release.apk`, firmado con la clave de subida de `keystore.properties`. Sin ella, `assembleRelease` y `bundleRelease` fallan con «Sin clave de subida…», para que no se reparta por error un APK firmado con la clave de prueba pública (`app/debug.keystore`). Para un APK de prueba firmado con esa clave, añade `-PfirmaDePrueba=true`; ver [Firma](#firma).

Para comprobar el manifiesto final (componentes exportados, `FileProvider` y permisos), con `aapt2` del SDK:

```bash
./gradlew :app:assembleDebug --no-daemon --max-workers=1
bash scripts/check-manifest.sh app/build/outputs/apk/debug/app-debug.apk
```

`scripts/check-manifest.sh` falla si hay un componente exportado que no sea `MainActivity` o el `ProfileInstallReceiver` protegido por `android.permission.DUMP`, si el `FileProvider` falta o está exportado, o si los permisos no son exactamente los de la 1.2.2 (la lista está en el script). En un APK *debuggable* admite además el `PreviewActivity` de las vistas previas de Compose, que solo trae el APK debug. La CI lo ejecuta con `--release` sobre el APK de release, y así un APK *debuggable* falla.

### jvm-check

`jvm-check/` es otro proyecto Gradle que compila todo lo que no depende de Android (cripto, bóveda, red, ajustes) y ejecuta sus tests en una JVM normal, sin el SDK de Android y solo con Maven Central. Usa la toolchain de Java 21.

```bash
./gradlew -p jvm-check test --no-daemon --max-workers=1
# o, con un Gradle 8.14.x instalado:
gradle -p jvm-check test --no-daemon --max-workers=1
```

## Integración continua

`.github/workflows/build-apk.yml` se ejecuta en cada push a `main` y a `claude/**`, en cada pull request, en las etiquetas `v*` y a mano:

- **build** (pull requests, ramas que no son `main` y ejecuciones a mano fuera de `main`): tests, lint, comprobación de licencias y APK de release `Seguridad.apk`, firmado con la clave de prueba pública (`-PfirmaDePrueba=true`). No usa ningún entorno ni recibe los secretos. Comprueba el manifiesto con `scripts/check-manifest.sh --release`, escribe en el resumen del trabajo la huella SHA-256 del certificado y avisa de que es la de la clave de prueba.
- **build-firma** (push a `main`, etiquetas `v*` y ejecuciones a mano desde `main`): los mismos pasos, en el entorno `firma` y con la clave de subida. Sin los secretos falla en vez de dar un APK con la clave de prueba.
- **dependency-metadata:** escribe en el resumen del trabajo cuántas líneas, cuántas entradas `<component` y cuántas sumas `<sha256` se añaden, cambian o quitan en `gradle/verification-metadata.xml` y `jvm-check/gradle/verification-metadata.xml` respecto a la base del PR (la rama en la que se fusionaría), al commit anterior del push o, en una rama o etiqueta nueva, a la etiqueta `v*` anterior.
- **jvm-check:** los tests en la JVM.
- **release-apk** (etiquetas `v*`): adjunta a la release de esa etiqueta `Seguridad-vX.Y.Z.apk` y su `.sha256`, y crea la release si no existe. No ve los secretos. Es la única forma de publicar un APK: los push a `main` solo lo dejan como artefacto de la ejecución.
- **play-bundle** (etiquetas `v*` y a mano desde `main`): el AAB para Google Play, en el entorno `firma`. Sin los secretos falla, porque Play no acepta la clave de prueba.

Todas las acciones están fijadas por SHA y los permisos son de solo lectura salvo en `release-apk`, que puede escribir releases pero no ve los secretos. Las compilaciones desde ramas o PR no reciben los secretos de firma: en **build** las expresiones del flujo solo los leen en ejecuciones desde `main` o desde una etiqueta `v*`, y **build-firma** y **play-bundle** solo se ejecutan en esos casos (su condición `if`). Cuando los secretos estén en el entorno `firma` y se hayan borrado del repositorio (ver [Entorno de firma](#entorno-de-firma)), solo los podrán leer los trabajos de ese entorno (**build-firma** y **play-bundle**), con las reglas que le pongas en GitHub. Hasta entonces son secretos del repositorio.

### Verificación de dependencias

`gradle/verification-metadata.xml` (y `jvm-check/gradle/verification-metadata.xml`) guardan el SHA-256 de cada dependencia y plugin. Gradle rechaza cualquier archivo que no coincida, venga del repositorio que venga. Al cambiar o añadir una dependencia hay que regenerarlos.

**Todo cambio de estos dos archivos se revisa como un cambio de seguridad**, igual que el código que cifra: una suma nueva o cambiada decide qué código entra en el APK, y las firmas de los artefactos no se comprueban (`verify-signatures` es `false`). El trabajo **dependency-metadata** de la CI escribe en el resumen cuántas entradas `<component` y cuántas sumas `<sha256` se añaden, cambian o quitan (una suma cambiada en un componente que ya estaba no toca su línea `<component`), y la plantilla de los pull requests lo pide revisar.

Hazlo **como la CI**: con el JDK 17 y con una caché de Gradle vacía para cada proyecto. Con una caché ya llena, o con otro JDK, Gradle resuelve algunos POM y BOM por otro camino y no los apunta, y luego la CI falla con «Dependency verification failed … Checksums are missing».

```bash
export JAVA_HOME=/ruta/al/jdk-17
rm -rf build app/build jvm-check/build
GRADLE_USER_HOME=$(mktemp -d) ./gradlew --write-verification-metadata sha256 help :app:assembleRelease :app:lintRelease \
  :app:testDebugUnitTest :app:checkLicense :app:bundleRelease :app:assembleDebug :app:lintDebug -PfirmaDePrueba=true \
  --no-daemon --max-workers=1
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

Una biblioteca puede traer datos con otra licencia que la suya. Por eso `:app:checkAssets` (en `check` y en la CI) falla si en `assets/` del APK hay algún archivo que no venga de `app/src/main/assets`. Así quedó fuera la Public Suffix List (MPL-2.0) que trae OkHttp 5, que la app no usa porque no guarda cookies. Si la tarea falla, revisa la licencia de ese archivo y, si la app no lo necesita, exclúyelo en `androidResources.ignoreAssetsPatterns` (`app/build.gradle.kts`).

## Firma

El APK de release y el AAB se firman con la clave de subida, en `keystore.properties` (en local, nunca en Git) o en variables de entorno (en la CI). Sin ella, `assembleRelease` y `bundleRelease` fallan, salvo con `-PfirmaDePrueba=true`: entonces se firman con la clave de prueba pública, que sirve para probar pero que cualquiera puede usar. No distribuyas un APK así.

Secretos de firma (en el entorno `firma` o, mientras no los muevas allí, en *Settings → Secrets and variables → Actions*):

| Secreto | Valor |
|---|---|
| `SEGURIDAD_KEYSTORE_BASE64` | el almacén en base64: `base64 -w0 tu.jks` |
| `SEGURIDAD_KEYSTORE_PASSWORD` | contraseña del almacén |
| `SEGURIDAD_KEY_ALIAS` | alias de la clave |
| `SEGURIDAD_KEY_PASSWORD` | contraseña de la clave |

La CI decodifica el almacén en un archivo temporal del runner, exporta `SEGURIDAD_KEYSTORE_FILE`, compila y lo borra. Huella esperada del certificado y pasos de Google Play: [docs/PUBLICAR.md](docs/PUBLICAR.md).

### Entorno de firma

Los trabajos que firman (**build-firma** y **play-bundle**) declaran el entorno `firma` de GitHub. Si el entorno no existe, GitHub lo crea en la primera ejecución sin reglas ni secretos, y los trabajos siguen leyendo los secretos del repositorio: no se rompe nada. Si el mismo secreto está en el entorno y en el repositorio, vale el del entorno. Para que solo `main` y las etiquetas de versión puedan usar la clave:

1. En *Settings → Environments*, abre `firma` (o créalo con *New environment* y ese nombre exacto).
2. En *Deployment branches and tags*, elige *Selected branches and tags* y añade dos reglas: una de tipo *Branch* con el patrón `main` y otra de tipo *Tag* con el patrón `v*`.
3. Si quieres aprobar cada firma a mano, marca *Required reviewers* y añádete. Con *Prevent self-review* no podrías aprobar tus propias ejecuciones. En un repositorio privado esta opción solo existe con GitHub Enterprise (ver la nota de abajo).
4. En *Environment secrets*, añade los cuatro secretos de la tabla de arriba con los mismos nombres y valores. Necesitas el `.jks` y sus contraseñas.
5. Lanza una ejecución desde `main` (*Actions → Build APK → Run workflow*) y comprueba en el resumen de **build-firma** que la huella es la de [docs/PUBLICAR.md](docs/PUBLICAR.md), no la de la clave de prueba.
6. Después, borra los cuatro secretos de *Settings → Secrets and variables → Actions* del repositorio: si se quedan allí, cualquier trabajo que no use el entorno podría volver a leerlos.
7. Protege también las etiquetas `v*` (*Settings → Rules → Rulesets*, una regla de etiquetas que limite crearlas, moverlas y borrarlas a ti), porque quien pueda crear una etiqueta `v*` obtiene un APK firmado. En un repositorio privado, los rulesets necesitan GitHub Pro, Team o Enterprise.

**Planes de GitHub.** En un repositorio público, todo lo anterior está disponible con cualquier plan. En uno privado:

- con GitHub Free no se pueden configurar entornos ni sus secretos: `firma` se crea, pero sin reglas, y los secretos tienen que seguir en el repositorio;
- con GitHub Pro o Team se pueden configurar el entorno, sus secretos y las ramas y etiquetas permitidas (pasos 1, 2 y 4), pero no *Required reviewers* (paso 3), que en un repositorio privado necesita GitHub Enterprise;
- los rulesets de etiquetas (paso 7) necesitan GitHub Pro, Team o Enterprise.

## Seguridad y alcance de la auditoría

La app contiene controles defensivos, pero este proyecto **no debe considerarse auditado de forma independiente**. La revisión automatizada y estática no sustituye las pruebas en dispositivos reales ni una auditoría criptográfica externa. Consulta el [informe de revisión de seguridad](docs/AUDITORIA_SEGURIDAD_2026-10.md) y la [política de reporte de vulnerabilidades](SECURITY.md). No guardes fondos o secretos críticos basándote únicamente en esta revisión.

## Comprobaciones manuales en el móvil

Lo que no se puede probar en la CI (no hay móvil ni emulador con huella). Antes de cada versión:

- [ ] Tema claro, y el marco oscuro que se ve al arrancar antes de que cargue la interfaz.
- [ ] Huella dactilar: activarla, abrir la bóveda con ella, y qué pasa al añadir o borrar una huella en el sistema.
- [ ] Escáner QR con la cámara real, y con el permiso de cámara denegado (debe quedar la entrada a mano).
- [ ] Escanear QR, exportar e importar abren su pantalla.
- [ ] Bordes de la pantalla: barra de estado, barra de navegación y teclado, en vertical y en horizontal.
- [ ] Importar una copia real de la bóveda de CriptoPanel y abrir un texto `CP1:` y un archivo `.cpenc` reales.
- [ ] Menús desplegables bajo una superposición (filtro de toques) y exportar la bóveda sin tener bóveda (debe avisar).
- [ ] Pantalla de licencias: todos los textos se abren.
- [ ] 1.1.0, QR de Google Authenticator: importar uno con varias cuentas (y uno con HOTP, que debe saltarse y decirlo), y uno que venga en varios lotes (debe pedir el siguiente). Comparar los códigos con la app de origen. Exportar una entrada por QR y escanearla con otra app.
- [ ] 1.1.0, adjuntos: añadir uno de varios MB, verlo en el detalle, guardarlo descifrado y comparar con el original; uno de más de 5 MB debe rechazarse; quitarlo con confirmación; añadir adjuntos desde un proveedor lento (Drive sin conexión) sin que la bóveda se quede bloqueada.
- [ ] 1.1.0, copia `.sgbak`: exportar con adjuntos e importar en otro móvil (o tras borrar los datos de la app) con la contraseña de la copia. En Android 14 o posterior, una copia con nombres de ZIP raros (`../`, `/` inicial) debe rechazarse o ignorarse sin escribir nada fuera.
- [ ] 1.1.0, copia automática: elegir una carpeta, hacer un cambio y comprobar que aparece `seguridad-auto-….sgbak` y que no queda ningún `.tmp`. Con una opción de conservar 3, comprobar la rotación y que no se borra nada ajeno. **Revocar el permiso** de la carpeta en Ajustes del sistema (o borrar la carpeta): la app no se cierra, Ajustes avisa y la bóveda sigue igual. Hacer un cambio y salir de la app antes de 3 s: no aparece copia hasta abrir la bóveda de nuevo.
- [ ] 1.1.0, copia automática en un proveedor que no sepa renombrar (sin `FLAG_SUPPORTS_RENAME`, o que falle `renameDocument`): la copia debe aparecer directamente como `seguridad-auto-….sgbak`, sin `.tmp`, importarse bien, y Ajustes no debe mostrar ningún error. Llenar el almacenamiento de ese proveedor a mitad de copia: no debe quedar ningún archivo y el aviso es el de escritura. Repetir en Google Drive, que permite nombres repetidos: no debe quedar ningún `.tmp` ni dos copias con el mismo nombre.
- [ ] 1.1.0, crear una bóveda nueva con la copia automática activada (por ejemplo, tras borrar la bóveda por haber olvidado la contraseña maestra): Ajustes dice que se ha desactivado, la carpeta ya no aparece en los permisos de la app y las copias anteriores siguen en la carpeta. Cerrar la app a la fuerza en los 3 s tras un cambio y abrir la bóveda: la copia se hace al desbloquear.
- [ ] 1.1.0, salud con filtraciones: con una contraseña conocida como filtrada (`password`) y una repetida, ver los avisos; «Comprobar filtraciones» con red, con la red cortada (debe fallar sin cerrar la app) y cancelándola a mitad; empezarla y pulsar Inicio: al bloquearse la bóveda no debe salir ninguna consulta más y, al volver, no quedan resultados.
- [ ] 1.2.0, tarjeta por QR: con dos móviles, de pantalla a pantalla, en los dos sentidos. El QR mide unos 2.000 caracteres con corrección L: probarlo con el brillo alto y bajo y a distintas distancias; si no se lee bien, se baja la densidad antes de tocar el formato. Un contacto añadido por QR queda «verificado»; pegado, «sin verificar».
- [ ] 1.2.0, compartir por WhatsApp y Telegram: un mensaje cifrado (`SGM1:`), un texto firmado (`SGS1:`) y un archivo (`.sgenc`), y abrirlos al otro lado (pegando el texto o eligiendo el archivo en «Abrir lo recibido»). Comprobar que el chat parte o recorta el texto y aun así se abre.
- [ ] 1.2.0, el menú de compartir: al pulsar «Compartir» y volver a Seguridad, la bóveda se bloquea (es lo esperado: compartir no deja la bóveda abierta). Con «Compartir mi tarjeta» también.
- [ ] 1.2.0, un archivo de 100 MB cifrado para un contacto y descifrado, girando el móvil a mitad: la operación se cancela sin dejar nada a medias (ni archivo temporal ni documento de destino a medio escribir). Y cancelarla a mano con un archivo grande.
- [ ] 1.2.0, girar el móvil con una tarjeta pendiente de guardar (pegada o escaneada): el texto sigue ahí, con su huella a la vista.
- [ ] 1.2.0, bloqueo por inactividad con el selector `CreateDocument` abierto (al guardar un archivo descifrado): la bóveda se bloquea, la operación se cancela y no queda el archivo en claro ni el temporal.
- [ ] 1.2.0, matar la app desde Recientes a mitad de un descifrado de «Abrir lo recibido» y volver a abrirla: `cacheDir/pk-open` queda vacía (se borra al arrancar). Se puede mirar con `adb shell run-as io.github.egt17baa.seguridad ls cache` en un APK debug.
- [ ] 1.2.0, abrir un `.txt` que contenga un mensaje `SGM1:` (o una tarjeta o un texto firmado) con «Abrir lo recibido»: se lee como texto. Un `.txt` firmado que se elige antes que su `.sgsig` debe seguir verificándose como archivo firmado.
- [ ] 1.2.0, elegir un `.txt` firmado y luego su `.sgsig` en «Abrir lo recibido»: solo queda la línea del archivo firmado («Firmado por …» con la nota «Una firma dice quién lo firmó, no a quién iba dirigido.»), sin el resultado de texto anterior.
- [ ] 1.2.0, elegir un `.sgenc` cifrado con contraseña en «Abrir lo recibido»: debe decir que se abre en Herramientas → Cifrar archivos (con su botón), no «firma inválida».
- [ ] 1.2.0, pegar una tarjeta con texto alrededor («Mi tarjeta: SGK1:… gracias») y con el texto partido en varias líneas: se reconoce y se muestra para añadir, sin guardar nada hasta pulsar.
- [ ] 1.2.0, una bóveda de la 1.1 (con huella activada) abierta primero con la huella y después con la maestra: se abre, y las entradas siguen ahí. Crear la identidad pide la maestra si la bóveda sigue en la versión 2. Importar una copia `.sgbak` de la 1.1.
- [ ] 1.2.0, instalar el APK release encima de la 1.1.1 (el mismo identificador `io.github.egt17baa.seguridad`, firmado con la clave de subida): se actualiza sin pedir desinstalar y la bóveda se abre. Si la huella estaba activada, sigue funcionando con la clave de antes.
- [ ] 1.2.2, huella: desactivarla y volver a activarla en ⋮ → «Activar huella». Debe pedir la huella y quedar activada (en la 1.2.0 y la 1.2.1 algunos móviles decían «Este móvil no permite guardar la clave con huella»). Bloquear y desbloquear con la huella.
- [ ] 1.2.1, autorrelleno: con un servicio de autorrelleno activo (el de Google o un gestor de contraseñas) y una bóveda de prueba con una contraseña inventada, desbloquear, crear una entrada y salir de la app: no debe aparecer «¿Guardar contraseña?» ni sugerencias de autorrelleno en ningún campo, tampoco en los diálogos (contraseña de una copia, cambiar la maestra). Con `adb shell dumpsys autofill` no debe haber datos de la app.
- [ ] 1.2.1, copias automáticas: con 3 copias hechas por la 1.2.1, dejar en la carpeta (desde el gestor de archivos) un `seguridad-auto-99991231-235959.sgbak` y hacer un cambio: se borra la copia propia más antigua y el archivo ajeno sigue ahí. Las copias de la 1.2.0 que ya hubiera tampoco se borran.
- [ ] 1.2.1, bloqueo: pulsar Inicio justo al tocar «Usar huella» o «Desbloquear» (mientras se abre): al volver, la bóveda está bloqueada.
- [ ] 1.2.0, con el **APK release** (R8 activado, no el debug): crear la identidad, cifrar un mensaje y un archivo para un contacto (puede ser una segunda tarjeta propia en otro móvil), abrirlos, y firmar un texto y un archivo y verificarlos. Así se comprueba que R8 no ha quitado nada de X-Wing ni de ML-KEM; si falta algo, se añade la regla `-keep` concreta en `app/proguard-rules.pro`.
- [ ] 1.3.0, «PIN y códigos»: generar una respuesta, pulsar Inicio y volver a los 10 s: sigue la misma. Salir más tiempo que el bloqueo de la bóveda (o 1 minuto con «Al salir») y volver: PIN, códigos y respuestas son nuevos, y las webs y las preguntas siguen. Con la bóveda abierta, «Bloquear» en Bóveda o Contactos (con esta pantalla en Herramientas): al volver, también son nuevos. Escribiendo preguntas aquí más de lo que tarda el bloqueo por inactividad: no se pierde nada; dejando la pantalla sin tocar ese tiempo, sí. Girar el móvil: todo sigue. Añadir 4 preguntas y tocar la última: el campo queda por encima del teclado.
- [ ] 1.3.0, girar el móvil, cambiar el modo oscuro del sistema y pasar a ventana flotante o pantalla dividida (MagicOS): en «Tachar», con 3 bloques dibujados, la imagen y los bloques siguen; en «Limpiar foto», la copia sigue. Con la navegación por gestos, arrastrar desde el borde izquierdo de la imagen de «Tachar» (también en una imagen alta, en su parte de abajo): se dibuja un bloque y no se sale de la pantalla. Guardar o compartir y girar mientras se prepara la copia: la copia se guarda o se comparte igual. Con una imagen abierta, ir a otra pestaña y volver: hay que elegirla de nuevo (no se guarda en memoria fuera de la pantalla).
- [ ] 1.3.0, cambiar la maestra con Argon2id «Alto» y girar el móvil mientras deriva: al terminar sale el diálogo «Contraseña maestra cambiada». Pulsar «Hacer una copia nueva y borrar las automáticas anteriores» y girar: el resultado se ve igual.
- [ ] 1.3.0, «Activar huella» y girar el móvil con el diálogo de la huella abierto: no se activa, la app no se cierra, el diálogo de la huella se cierra, sale «La activación de la huella se interrumpió…» y al volver a tocar «Activar huella» se activa. Con la huella activada, abrir la bóveda bloqueada y tocar en seguida «Usar huella»: un solo diálogo, sin «La huella no responde…».
- [ ] 1.3.0, desbloquear con huella con la clave en StrongBox: no se nota un tirón (si hay `adb`, `adb shell dumpsys gfxinfo io.github.egt17baa.seguridad`).
- [ ] 1.3.0, memoria: abrir en «Tachar» un JPEG de 200 MP y un PNG de 16 bits de unos 24 MP, y mirar `adb shell dumpsys meminfo io.github.egt17baa.seguridad` («Native Heap»). La app no se cierra; en un móvil de 3 o 4 GB, anotar si el sistema la mata (la memoria nativa no se puede capturar: si pasa, se aplica `MEMORY_POLICY_LOW_RAM`, ver `RedactRender.kt`). Si falta memoria Java, el aviso es «No hay memoria suficiente…», nunca «archivo dañado».
- [ ] 1.3.0, TalkBack: recorrer las fichas de Herramientas y «Tachar»: dice los nombres en español, sin leer los emojis, y la imagen de «Tachar» dice cuántos bloques tiene.
- [ ] 1.3.0, Gboard: en un campo de la bóveda, la barra de sugerencias no aprende (Ajustes de Gboard → Diccionario personal no gana la palabra). Probarlo también en un campo de un diálogo (por ejemplo, el de la contraseña maestra).
- [ ] Al subir Compose, repetir la prueba de Gboard (campo normal y campo en un diálogo): `NoLearningInput` usa una API experimental (`InterceptPlatformTextInput`).
- [ ] 1.3.0, en un móvil o emulador con Android 8 a 12: la primera vez que se copia un secreto (por ejemplo, «Copiar» en una entrada de la bóveda) sale el aviso «En este Android, el teclado puede guardar lo copiado en su historial…» con los segundos elegidos en Ajustes. «Cancelar» no copia nada (pegar en otra app no pega el secreto). «Entendido, copiar» lo copia, y las copias siguientes, también tras cerrar y abrir la app, ya no preguntan. En Android 13 o más el aviso no sale nunca.
- [ ] 1.3.0, girar el móvil mientras se prepara el desbloqueo con huella: la petición automática no se repite; tocar «Usar huella» una vez funciona.
- [ ] 1.3.0, StrongBox: activar la huella; en Ajustes, «Dónde está la clave de la huella» dice «Chip de seguridad (StrongBox)» (en un móvil sin StrongBox o en un emulador, «Zona aislada del procesador (TEE)»). Bloquear y desbloquear 10 veces, reiniciar el móvil y desbloquear, y añadir una huella nueva en el sistema: pide la contraseña maestra. Pulsar Inicio durante la primera petición de huella y volver a activarla: sigue en StrongBox. Tocar «Activar huella» dos veces rápido: una sola petición.
- [ ] 1.3.0, «Limpiar foto» con una JPEG y una HEIC de 200 MP de la cámara, una captura de pantalla y fotos llegadas por WhatsApp y Telegram: enseña lo que trae; la HEIC sale como JPEG y, si pasa de unos 24 megapíxeles, reducida, con su aviso. «Guardar copia» propone `copia-de-foto.<ext>` y la copia no lleva la ubicación (mirarlo con un visor de EXIF).
- [ ] 1.3.0, «Compartir» desde «Limpiar foto» y desde «Tachar» a WhatsApp y a Gmail (en Gmail, enviar el correo después de volver a Seguridad): la imagen llega entera, y en la de «Tachar» los bloques están puestos. Al volver a Seguridad pasados 10 minutos, `cacheDir/compartir` queda vacía (`adb shell run-as io.github.egt17baa.seguridad ls cache/compartir` en un APK debug).
- [ ] 1.3.0, «Tachar» con un PNG, una JPEG con orientación 6, una HEIC y una imagen de más de 24 MP (sale el aviso de que se ha reducido); tocar mientras se prepara la copia.
- [ ] 1.3.0, Inspector de QR: escanear con la cámara un QR de Wi-Fi; el resultado sigue al volver del escáner y la contraseña está oculta hasta tocar «Mostrar».
- [ ] 1.3.0, Inspector de QR con un QR de enlace (por ejemplo, uno con `http://` y otro con un acortador): enseña la web en grande y sus avisos, no abre nada al tocarlo, y «Copiar» copia el enlace tal cual.
- [ ] 1.3.0, «PIN y códigos» con la bóveda abierta: generar un PIN de 6 cifras y copiarlo; escribir la web y guardar los códigos de recuperación con «Guardar en la bóveda como nota»; escribir dos preguntas, generar sus respuestas y pulsar «Guardar en la bóveda». En la bóveda aparecen las dos notas («Códigos de recuperación · …» y «Respuestas de seguridad · …»), con lo que se veía. Con la bóveda bloqueada, sale «Desbloquea la bóveda para guardarlo…».
- [ ] 1.3.0, actualizar desde la 1.2.2 con la copia automática activada en una carpeta: sin volver a elegirla, un cambio en la bóveda hace la copia y Ajustes no muestra «La app ya no tiene permiso…». Con la bóveda bloqueada, los botones de la copia automática, «Conservar», el bloqueo, el portapapeles y el coste de Argon2id aparecen desactivados con «Abre la bóveda (pestaña Bóveda) para cambiar este ajuste».
- [ ] 1.3.0, pantalla dividida con «Bloqueo: Al salir» (auditoría, F1): en Ajustes, sin tocar la bóveda desde hace más de 2 minutos, tocar «Activar huella». La activación termina (el diálogo de la huella no le quita el foco a la app). Repetir bajando la cortina de notificaciones unos segundos: la bóveda no se bloquea en ninguno de los dos casos. Después, usar la otra app más de 2 minutos y 15 segundos: al volver, la bóveda está bloqueada; volver antes de 2 minutos: sigue abierta.
- [ ] 1.3.0, pantalla dividida y «PIN y códigos» (auditoría, F1): generar un PIN, códigos y respuestas, usar el navegador de la otra mitad más de 1 minuto (o más que el bloqueo elegido) y volver: el PIN, los códigos y las respuestas son nuevos, y las webs y las preguntas siguen. Volver antes de 1 minuto: siguen los mismos. Con la bóveda abierta y «Al salir», quedarse más de 2 minutos en la otra app: al bloquearse, también son nuevos aunque se mire sin volver.
- [ ] 1.3.0, accesibilidad en Android 14 o posterior (auditoría, F1): con «Seleccionar para pronunciar» (Accessibility Suite), los textos de la app se siguen leyendo en voz alta; con TalkBack, la bóveda, los diálogos y los menús se leen y se manejan.
- [ ] 1.3.0, compartir y cierre de la app (auditoría, F1): compartir una copia de «Limpiar foto» a Gmail, y, sin salir de Gmail, `adb shell am kill io.github.egt17baa.seguridad`; enviar el correo: el adjunto debe llegar. Si no llega, anotarlo: el borrado de `compartir/` al arrancar tendría que respetar las copias de menos de 10 minutos.
