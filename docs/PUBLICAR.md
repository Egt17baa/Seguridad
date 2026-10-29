# Firmar y publicar Seguridad

## 1. La clave de subida y su huella

El APK de *Releases* y el AAB de Google Play se firman con la **clave de subida**, guardada en los secretos del entorno `firma` (o, mientras no se muevan allí, del repositorio) `SEGURIDAD_KEYSTORE_BASE64`, `SEGURIDAD_KEYSTORE_PASSWORD`, `SEGURIDAD_KEY_ALIAS` y `SEGURIDAD_KEY_PASSWORD` (cómo crearlos en el [README](../README.md#firma)).

Huella SHA-256 esperada del certificado de la clave de subida (es pública, no es un secreto):

```
74:63:98:EA:37:06:5C:F0:F3:B2:AC:A2:23:FB:8C:DB:79:16:7C:B4:C5:3B:8D:21:EF:E3:C0:71:17:93:DF:9E
```

El trabajo **build-firma** de la CI (o **build**, en un PR o una rama) escribe la huella del APK en el resumen de cada ejecución (*Actions → la ejecución → Summary*). Tiene que ser esta. Si sale `C0:01:4D:F6:…:18:34`, el APK está firmado con la clave de prueba pública (`app/debug.keystore`), la CI lo avisa con un `warning` y ese APK no se debe distribuir: cualquiera puede firmar una «actualización» con esa clave.

Para comprobar un APK descargado:

```bash
$ANDROID_HOME/build-tools/36.0.0/apksigner verify --print-certs Seguridad.apk
```

La línea `Signer #1 certificate SHA-256 digest` debe dar la huella de arriba (en minúsculas y sin los dos puntos).

Para ver la huella de tu almacén:

```bash
keytool -list -v -keystore tu.jks -alias TU_ALIAS | grep SHA256
```

## 2. Descargar el APK (instalación directa)

Solo las versiones etiquetadas publican un APK. Cada etiqueta `v*` (por ejemplo `v1.1.1`) adjunta a su release `Seguridad-v1.1.1.apk` y `Seguridad-v1.1.1.apk.sha256`; si la release no existe aún, se crea con el nombre de la etiqueta. Los push a `main` ya no publican nada (antes iba a `apk-latest`): así no llegan a nadie versiones sin estabilizar.

1. En GitHub, abre el repositorio → *Releases* → la última versión.
2. Descarga `Seguridad-vX.Y.Z.apk` y comprueba su huella (apartado 1) y su SHA-256.
3. En el móvil, permite instalar apps de esa fuente e instálalo. Después puedes volver a quitar ese permiso.

**Verificación de desarrolladores Android.** Desde el 30 de septiembre de 2026, los móviles certificados de algunos países solo instalan apps de desarrolladores verificados, y en 2027 se extiende al resto. Registra también este APK (el de GitHub) en Play Console, en la misma cuenta, para que siga instalándose.

**En la 1.1.1 cambia el identificador** (`app.seguridad` → `io.github.egt17baa.seguridad`). Para Android es otra app: no se actualiza encima de la anterior. Para pasar:

1. En la app antigua, si usas la copia automática, **desactívala** (Ajustes → Copia automática). Si las dos apps escriben copias en la misma carpeta, la rotación de cada una borra las copias de la otra.
2. Exporta la bóveda: en la 1.1.0, Ajustes → Exportar bóveda (archivo `.sgbak`, con los adjuntos); en la 1.0.x, la exportación es un `.json`. La nueva importa los dos.
3. Instala la nueva, importa la copia con la contraseña maestra y comprueba que se abre.
4. En la nueva, vuelve a configurar lo que no viaja en la copia: los ajustes (bloqueo, portapapeles, coste de Argon2id), la huella y la carpeta de la copia automática.
5. Desinstala la antigua.

**La 1.2.0 se instala encima de la 1.1.1** (mismo identificador y misma firma) y conserva la bóveda, pero después de abrirla con la 1.2 no se puede volver a la 1.1: la bóveda pasa a la versión 3 y la 1.1 no puede usarla (con la maestra no la abre y con la huella no puede guardar cambios; no se pierden datos). Conviene exportar una copia `.sgbak` antes de actualizar.

Las ramas `claude/**` y los pull requests no publican nada: su APK queda como artefacto `Seguridad-apk` de la ejecución (*Actions → la ejecución → Artifacts*), solo para probar.

Un APK firmado con la clave de subida y otro firmado con la de prueba no se actualizan entre sí: Android pide desinstalar, y al desinstalar se borra la bóveda. Exporta antes una copia.

## 3. Publicar en Google Play (app nueva)

### 3.1 Antes de la primera subida

- El `applicationId` es `io.github.egt17baa.seguridad`, bajo el espacio de GitHub del propietario (desde la 1.1.1). **No se puede cambiar una vez publicado.**
- El nombre «Seguridad» es genérico: no se puede registrar como marca y puede confundirse con los ajustes «Seguridad» del sistema. Antes de la primera subida, decide un nombre distintivo (comprobado en TMview y en Google Play); el nombre visible sí se puede cambiar después, el `applicationId` no.
- Cuenta de desarrollador en <https://play.google.com/console> (pago único y verificación de identidad). Las cuentas **personales** nuevas tienen que hacer una prueba cerrada con al menos 12 testers durante 14 días antes de pasar a producción.

### 3.2 Crear la app

1. *Play Console → Crear app*: nombre **Seguridad**, idioma predeterminado español, tipo **app**, **gratuita**. Una app gratuita no puede pasar a ser de pago.
2. Acepta las declaraciones y crea la app.

### 3.3 Play App Signing

Google Play firma lo que instala la gente con su propia **clave de firma de apps**; tu clave solo es la **clave de subida**, que demuestra que la subida es tuya.

1. En la primera versión, Play Console ofrece *Play App Signing*: elige **que Google genere y guarde la clave de firma de apps** (la opción recomendada). No hace falta subir ninguna clave privada a Google.
2. Al subir el primer AAB, Play registra el certificado con el que está firmado como **clave de subida**.
3. En *Prueba y publicación → Configuración → Integridad de la app → Firma de apps*, comprueba que la huella SHA-256 del **certificado de la clave de subida** es `74:63:98:…:DF:9E` (apartado 1). La del certificado de la clave de firma de apps será otra: es la de Google, y es la que tendrán las apps instaladas desde Play.

Las instalaciones desde Play y las del APK de *Releases* tienen firmas distintas, así que no se actualizan entre sí.

### 3.4 Generar y subir el AAB

1. Crea una etiqueta y súbela: `git tag v1.0.0 && git push origin v1.0.0`, o lanza el workflow *Build APK* a mano (*Actions → Build APK → Run workflow*).
2. El trabajo **play-bundle** compila `:app:bundleRelease` firmado con la clave de subida y deja el artefacto **`Seguridad-google-play-aab`**. Si faltan los secretos, falla a propósito: Play no acepta la clave de prueba.
3. El `versionCode` es el número de ejecución del workflow, así que cada subida tiene uno mayor que la anterior.
4. Descarga el artefacto (*Actions → la ejecución → Artifacts*), descomprime el zip y queda `app-release.aab`.
5. *Prueba y publicación → Prueba cerrada* (o *Producción* si tu cuenta lo permite) → *Crear versión* → sube `app-release.aab`, escribe las notas de la versión y envía a revisión.

### 3.5 Formularios de la ficha

- **Política de privacidad:** <https://egt17baa.github.io/Seguridad/privacidad.html> (se publica desde `docs/privacidad.html` con GitHub Pages: *Settings → Pages → Deploy from a branch → `main` / `docs`*). Es la misma que enlaza la app en *Ajustes → Acerca de*. Cuando la cambies, actualiza su fecha.
- **Seguridad de los datos:** la app no tiene cuentas, analítica ni anuncios, y todo se guarda cifrado en el móvil (la identidad y los contactos de la 1.2.0 también: solo en la bóveda y en las copias del usuario, sin enviarse a ningún sitio, así que esta sección no cambia). La única transmisión es la consulta a Have I Been Pwned (5 caracteres del hash SHA-1 de cada contraseña comprobada, más la IP que ve cualquier servidor), solo cuando el usuario la pide. Declárala como **tratamiento efímero, iniciado por el usuario y cifrado en tránsito**: Google cuenta como recogida cualquier transmisión fuera del móvil, y esta es la respuesta más coherente con su definición.
- **Cifrado / exportación:** sí usa cifrado, solo con algoritmos estándar y publicados (AES, ChaCha20-Poly1305, Twofish, Serpent, Camellia, Argon2id y, desde la 1.2.0, Ed25519, HPKE, HKDF y el KEM híbrido X-Wing, un borrador del IETF ya publicado) para proteger datos del propio usuario. En el formulario de exportación de Play, responde «cifrado estándar, código públicamente disponible», e incluye X-Wing entre los algoritmos estándar. El fundamento es que el código es abierto y está públicamente disponible: así queda fuera del control de exportaciones en la UE (Nota General de Software del Reglamento (UE) 2021/821) y de las EAR de EE. UU. (§734.3(b)(3) y §742.15(b)). **No se envía ningún correo a la BIS ni a la NSA:** solo se exige para la criptografía «no estándar», y esta app usa únicamente primitivas estándar publicadas (decisión del propietario, con el visto bueno de la revisión jurídica del 26-10-2026). Si algún día el código dejara de ser público o se añadiera un algoritmo propio, hay que volver a analizarlo.
- **Países y regiones:** **quita Francia** (*Prueba y publicación → Países y regiones*). La importación de un medio de cifrado en Francia exige una declaración previa a la ANSSI (art. 30 de la Ley 2004-575) y el propietario ha decidido no publicar allí ni presentarla. Si algún día se quiere Francia, antes hay que presentar esa declaración (gratuita; se resuelve en un mes). Quien descargue el APK de GitHub desde Francia para su propio uso está en el supuesto exento de la categoría 12.a del anexo 1 del decreto 2007-663.
- **Funciones financieras:** ninguna. No es un exchange ni una wallet: guarda frases semilla, pero no firma ni envía transacciones. No uses la palabra «wallet» en la ficha.
- **Público objetivo:** mayores de 13 años, sin dirigirse a menores (fuera de la política de Familias). Completa el cuestionario de clasificación de contenido.
- **Descripción:** exacta y sin superlativos de seguridad («segura», «auditada», «inviolable», «grado militar»), ni promesas absolutas («imposible de descifrar», «a prueba de cuánticos», «100 %», «como Signal», «con validez legal»), con los avisos de «sin recuperación» y «sin auditoría independiente», y mencionando la conexión opcional con HIBP. Borrador en [`docs/ficha-google-play.md`](ficha-google-play.md).
- **Permisos:** Internet (comprobación de filtraciones) y cámara (solo al escanear un QR de 2FA, de Google Authenticator o de la tarjeta de un contacto). La huella usa la API biométrica del sistema; la app nunca la ve.
- **Anuncios:** no tiene. Tampoco compras ni donaciones: la app es gratuita y no se monetizará (ver el README).

## 4. Si se pierde la clave de subida

Con Play App Signing, perder la clave de subida (o sus contraseñas) **no** hace perder la app:

1. Crea una clave de subida nueva:
   ```bash
   keytool -genkeypair -v -keystore seguridad-upload-2.jks -alias seguridad \
     -keyalg RSA -keysize 4096 -validity 10000
   keytool -export -rfc -keystore seguridad-upload-2.jks -alias seguridad -file upload_certificate.pem
   ```
2. En *Play Console → Integridad de la app → Firma de apps*, pide **restablecer la clave de subida** (*Request upload key reset*) y adjunta `upload_certificate.pem`. Solo lo puede hacer el propietario de la cuenta. Google tarda unos días en aplicarlo.
3. Cambia los cuatro secretos `SEGURIDAD_*` del entorno `firma` (o del repositorio, si siguen allí) por los de la clave nueva y actualiza la huella esperada del apartado 1.

Si la clave se ha **filtrado** (no solo perdido), haz lo mismo cuanto antes, y cambia también los secretos: la clave vieja deja de valer en Play cuando Google aplica el cambio. Ojo: el APK de *Releases* lo firma esa misma clave, así que los que lo instalaron desde *Releases* tendrán que desinstalar (exportando antes la bóveda) e instalar el nuevo.

Guarda el `.jks` y sus contraseñas en un gestor de contraseñas y en una copia fuera de línea. **Nunca** en Git: `.gitignore` ya ignora `*.jks`, `*.keystore` (salvo la clave de prueba) y `keystore.properties`.

## 5. Comprobación antes de cada subida

```bash
./gradlew :app:testDebugUnitTest :app:lintRelease :app:assembleRelease :app:checkLicense --no-daemon --max-workers=1
./gradlew -p jvm-check test --no-daemon --max-workers=1
```

Y la lista de comprobaciones manuales del [README](../README.md#comprobaciones-manuales-en-el-móvil), que incluye probar el **APK release** (con R8) en un móvil: crear la identidad, cifrar y abrir un mensaje y un archivo, y firmar y verificar.

## 6. Si no se publica en Google Play

Si la app solo se reparte a amigos y familia, no hace falta Play Console, ni las secciones 3.1 a 3.5, ni quitar Francia de ninguna lista: basta con el APK firmado con la clave de subida que adjunta la CI a la release de GitHub (apartado 2). Siguen valiendo la huella del apartado 1 y las comprobaciones del apartado 5. Quien instala desde GitHub y quien instalaría desde Play tienen firmas distintas, así que no se actualizan entre sí.
