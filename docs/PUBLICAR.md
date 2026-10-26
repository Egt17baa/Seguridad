# Firmar y publicar Seguridad

## 1. La clave de subida y su huella

El APK de *Releases* y el AAB de Google Play se firman con la **clave de subida**, guardada en los secretos del repositorio `SEGURIDAD_KEYSTORE_BASE64`, `SEGURIDAD_KEYSTORE_PASSWORD`, `SEGURIDAD_KEY_ALIAS` y `SEGURIDAD_KEY_PASSWORD` (cómo crearlos en el [README](../README.md#firma)).

Huella SHA-256 esperada del certificado de la clave de subida (es pública, no es un secreto):

```
74:63:98:EA:37:06:5C:F0:F3:B2:AC:A2:23:FB:8C:DB:79:16:7C:B4:C5:3B:8D:21:EF:E3:C0:71:17:93:DF:9E
```

El trabajo **build** de la CI escribe la huella del APK en el resumen de cada ejecución (*Actions → la ejecución → Summary*). Tiene que ser esta. Si sale `C0:01:4D:F6:…:18:34`, el APK está firmado con la clave de prueba pública (`app/debug.keystore`), la CI lo avisa con un `warning` y ese APK no se debe distribuir: cualquiera puede firmar una «actualización» con esa clave.

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

**De la 1.1.0 a la 1.1.1 cambia el identificador** (`app.seguridad` → `io.github.egt17baa.seguridad`). Para Android es otra app: no se actualiza encima de la anterior y las dos pueden convivir. Exporta la bóveda en la versión antigua (Ajustes → Exportar bóveda, `.sgbak`), impórtala en la nueva, comprueba que abre y después desinstala la antigua.

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
- **Seguridad de los datos:** la app no tiene cuentas, analítica ni anuncios, y todo se guarda cifrado en el móvil. La única transmisión es la consulta a Have I Been Pwned (5 caracteres del hash SHA-1 de cada contraseña comprobada, más la IP que ve cualquier servidor), solo cuando el usuario la pide. Declárala como **tratamiento efímero, iniciado por el usuario y cifrado en tránsito**: Google cuenta como recogida cualquier transmisión fuera del móvil, y esta es la respuesta más coherente con su definición.
- **Cifrado / exportación:** sí usa cifrado, solo con algoritmos estándar y publicados (AES, ChaCha20, Twofish, Serpent, Camellia, Argon2id) para proteger datos del propio usuario. El fundamento es que el código es abierto y está públicamente disponible: así queda fuera del control de exportaciones en la UE (Nota General de Software del Reglamento (UE) 2021/821) y de las EAR de EE. UU. Opcional y sin coste: enviar a la BIS y a la NSA el correo de notificación con la URL del repositorio.
- **Funciones financieras:** ninguna. No es un exchange ni una wallet: guarda frases semilla, pero no firma ni envía transacciones. No uses la palabra «wallet» en la ficha.
- **Público objetivo:** mayores de 13 años, sin dirigirse a menores (fuera de la política de Familias). Completa el cuestionario de clasificación de contenido.
- **Descripción:** exacta y sin superlativos de seguridad («segura», «auditada», «inviolable», «grado militar»), con los avisos de «sin recuperación» y «sin auditoría independiente», y mencionando la conexión opcional con HIBP. Borrador en [`docs/ficha-google-play.md`](ficha-google-play.md).
- **Permisos:** Internet (comprobación de filtraciones) y cámara (solo al escanear un QR de 2FA o de Google Authenticator). La huella usa la API biométrica del sistema; la app nunca la ve.
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
3. Cambia los cuatro secretos `SEGURIDAD_*` del repositorio por los de la clave nueva y actualiza la huella esperada del apartado 1.

Si la clave se ha **filtrado** (no solo perdido), haz lo mismo cuanto antes, y cambia también los secretos: la clave vieja deja de valer en Play cuando Google aplica el cambio. Ojo: el APK de *Releases* lo firma esa misma clave, así que los que lo instalaron desde *Releases* tendrán que desinstalar (exportando antes la bóveda) e instalar el nuevo.

Guarda el `.jks` y sus contraseñas en un gestor de contraseñas y en una copia fuera de línea. **Nunca** en Git: `.gitignore` ya ignora `*.jks`, `*.keystore` (salvo la clave de prueba) y `keystore.properties`.

## 5. Comprobación antes de cada subida

```bash
./gradlew :app:testDebugUnitTest :app:lintRelease :app:assembleRelease :app:checkLicense --no-daemon --max-workers=1
./gradlew -p jvm-check test --no-daemon --max-workers=1
```

Y la lista de comprobaciones manuales del [README](../README.md#comprobaciones-manuales-en-el-móvil).
