# Seguridad

App Android de seguridad y criptografía, sin cuentas, sin nube y sin anuncios. La bóveda y los datos cifrados se guardan localmente en el móvil.

**Licencia: [Apache-2.0](LICENSE).** Se permite usar, estudiar, modificar y redistribuir el código conforme a esa licencia. Las dependencias conservan sus propias licencias. Copyright 2026 Egt17baa ([NOTICE](NOTICE)).

**Gratuita y sin monetizar, por compromiso.** La app no tendrá nunca precio, anuncios, compras integradas, versión de pago, donaciones que condicionen funciones ni uso de datos con fines comerciales. No es solo una preferencia: la publicación se plantea como la de un particular sin actividad económica, y cualquier cambio de modelo obligaría a revisar sus obligaciones legales (consumo, Cyber Resilience Act, responsabilidad por productos).

**Privacidad:** la app no recoge datos. [Política de privacidad](https://egt17baa.github.io/Seguridad/privacidad.html) (fuente: [`docs/privacidad.html`](docs/privacidad.html)).

**Identificador:** `io.github.egt17baa.seguridad` desde la 1.1.1 (antes `app.seguridad`). Android lo trata como otra app: para pasar de una versión anterior hay que exportar la bóveda en la antigua e importarla en la nueva (pasos en [`docs/PUBLICAR.md`](docs/PUBLICAR.md#2-descargar-el-apk-instalación-directa)).

## Funciones

- **Bóveda** de accesos, frases semilla, notas y códigos 2FA, cifrada con una clave de datos envuelta con Argon2id y, si se quiere, con la huella (Android Keystore). Se bloquea por inactividad o al salir.
  - **Búsqueda**, **favoritos** y **etiquetas**. La búsqueda mira título, usuario, URL y etiquetas, nunca contraseñas, frases ni notas.
  - **Historial de contraseñas:** las últimas 5 de cada acceso.
  - **Salud de la bóveda:** contraseñas repetidas, débiles, antiguas y sin 2FA. Si se pulsa «Comprobar filtraciones», consulta Have I Been Pwned con k-anonimato, una contraseña a la vez; nunca lo hace por su cuenta.
  - **Adjuntos cifrados** en las entradas (hasta 5 MB cada uno y 50 MB en total), cada uno con su propia clave AES-256-GCM.
  - **Copia `.sgbak`:** exportar e importar la bóveda con sus adjuntos. Se siguen leyendo las exportaciones JSON de la 1.0.x y de CriptoPanel.
  - **Copia automática** cifrada a una carpeta que eliges (almacenamiento del sistema), con rotación de las 3, 5 o 10 últimas. Solo se hace con la bóveda abierta, para comprobar cada adjunto; si estaba bloqueada, se hace al abrirla. Si falla, lo avisa en Ajustes y no toca la bóveda.
- **Códigos 2FA** TOTP (RFC 6238), con escáner QR (ZXing, solo pide la cámara al escanear) o con la clave a mano. **Importar de Google Authenticator** (`otpauth-migration://`) y **exportar un código por QR** para otra app.
- **Cifrar y descifrar texto y archivos** con AES-256-GCM, ChaCha20-Poly1305, Twofish, Serpent, Camellia o la cascada, con contraseña (Argon2id) o con una clave AES-256 aleatoria. Modo frase semilla con BIP-39.
- **Contactos (1.2.0): cifrado y firmas con clave pública**, solo entre usuarios de Seguridad y sin servidor: tú pasas el texto o el archivo por el canal que ya uses (WhatsApp, Telegram, correo).
  - **Mi identidad:** una por persona, guardada en la bóveda (viaja en las copias `.sgbak`). Se comparte como **tarjeta** (QR o texto `SGK1:`) con una huella de 30 cifras para compararla por otro canal.
  - **Contactos:** se añaden escaneando su QR (quedan verificados) o pegando su tarjeta (quedan sin verificar hasta comparar la huella). Si llega una tarjeta con las mismas claves de cifrado que otra persona, o con claves distintas bajo el mismo nombre, la app avisa y no deja guardarla a ciegas.
  - **Cifrar para un contacto** un texto (`SGM1:`) o un archivo (`.sgenc`), con la firma de tu identidad por defecto. El cifrado es híbrido, diseñado para resistir también a ordenadores cuánticos futuros (X-Wing, en proceso de estandarización, con HPKE de la RFC 9180). **Firmar** un texto (`SGS1:`) o un archivo (`.sgsig`) con Ed25519 (RFC 8032): demuestra a tus contactos que es tuyo y que no ha cambiado.
  - **Abrir lo recibido:** una sola entrada que reconoce lo que se pega o se abre (mensaje, texto firmado, tarjeta, archivo cifrado o firma) y dice siempre de quién es: «De Ana (verificado)», «sin verificar», «firmado por una clave que no está en tus contactos» o «sin firmar».
  - **Lo que no protege:** si alguien roba tu identidad (por ejemplo, con una copia `.sgbak` y tu contraseña maestra), puede leer lo que te enviaron antes y firmar como tú. Los metadatos (quién escribe a quién, cuándo y el tamaño) los ve el canal. No hay auditoría independiente.
- **Herramientas:** generador de contraseñas y frases EFF con su entropía, comprobador de contraseñas filtradas (Have I Been Pwned con k-anonimato: solo salen 5 caracteres del hash SHA-1) y huella SHA-256 de archivos.
- **Ajustes:** bloqueo, borrado del portapapeles, coste de Argon2id, tema claro u oscuro, copia de seguridad y la pantalla de licencias.

Protecciones en toda la app: `FLAG_SECURE`, filtro de toques bajo superposiciones, portapapeles marcado como sensible y borrado automático, sin copias de Android (`allowBackup="false"`), sin logs con datos, y red solo hacia `api.pwnedpasswords.com` con un cliente que rechaza los certificados de eMudhra. Permisos: `INTERNET`, `CAMERA` (solo se pide al escanear un QR, de 2FA o de la tarjeta de un contacto) y los de biometría que declara `androidx.biometric` (`USE_BIOMETRIC` y `USE_FINGERPRINT`).

## Compatibilidad con CriptoPanel

Seguridad escribe sus propios formatos (`SG1:`, `.sgenc`, `seguridad-vault` y, desde la 1.1.0, `seguridad-backup` en archivos `.sgbak`) y **lee también los de CriptoPanel**:

- textos `CP1:` y archivos `.cpenc`;
- la copia exportada de la bóveda de CriptoPanel (`criptopanel-vault`, versiones 1 y 2, y las antiguas sin campo `version`). Al importarla se vuelve a cifrar con el formato nuevo.

Copias de la bóveda:

- **`.sgbak` (`seguridad-backup` v1):** lo que escribe la 1.1.0 al exportar y la copia automática. Es un ZIP con la bóveda cifrada y los adjuntos cifrados.
- **JSON de la 1.0.x y de CriptoPanel:** se siguen importando. Una JSON con adjuntos se rechaza y se pide importar el `.sgbak`.
- El diseño describe los dos formatos nuevos (`seguridad-backup` v1 y el de adjunto v1) byte a byte en [docs/superpowers/specs/2026-10-23-seguridad-design.md](docs/superpowers/specs/2026-10-23-seguridad-design.md).

**Formatos nuevos de la 1.2.0** (se leerán siempre; hay vectores guardados en `app/src/test/resources/vectors/`): cabecera `SG` + `0x02`, con tarjeta `SGK1:`, mensaje `SGM1:`, firma de texto `SGS1:`, archivo cifrado para un contacto (`.sgenc`, tipo 3) y firma de archivo (`.sgsig`). Usan solo primitivas estándar de Bouncy Castle: Ed25519 (RFC 8032), HPKE (RFC 9180) con X-Wing (borrador `draft-ietf-hpke-pq`), HKDF-SHA256 y ChaCha20-Poly1305.

**Aviso: después de abrir la bóveda con la 1.2 no se puede volver a la 1.1.** La bóveda pasa a la versión 3 y la copia `.sgbak` a la versión 2. La 1.1 no abre la bóveda nueva (no pierde datos, pero no la abre) y dice que la copia es de una versión más nueva. La 1.2 sigue leyendo las bóvedas y copias de la 1.1 y las reescribe en el formato nuevo. Hacer una copia `.sgbak` antes de actualizar sigue siendo buena idea.

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
- **release-apk** (etiquetas `v*`): adjunta a la release de esa etiqueta `Seguridad-vX.Y.Z.apk` y su `.sha256`, y crea la release si no existe. No ve los secretos. Es la única forma de publicar un APK: los push a `main` solo lo dejan como artefacto de la ejecución.
- **play-bundle** (etiquetas `v*` y a mano): el AAB para Google Play. Sin los secretos falla, porque Play no acepta la clave de prueba.

Todas las acciones están fijadas por SHA y los permisos son de solo lectura salvo en `release-apk`, que puede escribir releases pero no ve los secretos. Los secretos de firma se limitan a ejecuciones de confianza; las compilaciones desde ramas o PR no fiables no deben recibirlos.

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

Una biblioteca puede traer datos con otra licencia que la suya. Por eso `:app:checkAssets` (en `check` y en la CI) falla si en `assets/` del APK hay algún archivo que no venga de `app/src/main/assets`. Así quedó fuera la Public Suffix List (MPL-2.0) que trae OkHttp 5, que la app no usa porque no guarda cookies. Si la tarea falla, revisa la licencia de ese archivo y, si la app no lo necesita, exclúyelo en `androidResources.ignoreAssetsPatterns` (`app/build.gradle.kts`).

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
- [ ] 1.2.0, elegir un `.sgenc` cifrado con contraseña en «Abrir lo recibido»: debe decir que se abre en Herramientas → Cifrar archivos (con su botón), no «firma inválida».
- [ ] 1.2.0, pegar una tarjeta con texto alrededor («Mi tarjeta: SGK1:… gracias») y con el texto partido en varias líneas: se reconoce y se muestra para añadir, sin guardar nada hasta pulsar.
- [ ] 1.2.0, una bóveda de la 1.1 (con huella activada) abierta primero con la huella y después con la maestra: se abre, y las entradas siguen ahí. Crear la identidad pide la maestra si la bóveda sigue en la versión 2. Importar una copia `.sgbak` de la 1.1.
- [ ] 1.2.0, instalar el APK release encima de la 1.1.1 (el mismo identificador `io.github.egt17baa.seguridad`, firmado con la clave de subida): se actualiza sin pedir desinstalar y la bóveda se abre. Si la huella estaba activada, sigue funcionando con la clave de antes; al desactivarla y volver a activarla, la clave nueva solo sirve con el móvil desbloqueado (Android 9 o posterior).
- [ ] 1.2.0, con el **APK release** (R8 activado, no el debug): crear la identidad, cifrar un mensaje y un archivo para un contacto (puede ser una segunda tarjeta propia en otro móvil), abrirlos, y firmar un texto y un archivo y verificarlos. Así se comprueba que R8 no ha quitado nada de X-Wing ni de ML-KEM; si falta algo, se añade la regla `-keep` concreta en `app/proguard-rules.pro`.
