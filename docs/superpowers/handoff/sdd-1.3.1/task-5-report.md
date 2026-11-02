# Informe de la tarea 5: CRY-B02, TOTP no inventa ajustes

Estado: DONE (con una duda de redacción sobre el texto 1, ver «Preocupaciones»).
Commit: `5657ec3` «totp: rechazar los ajustes no admitidos en vez de cambiarlos (CRY-B02)», subido a `ccr-a1a6eabb-765f9g` (`7256c07..5657ec3`).
El mensaje termina solo en `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` (`grep -cE "Claude-Session|claude\.ai"` da 0).

## Qué se ha implementado

`app/src/main/java/app/seguridad/crypto/Totp.kt`:
- `hmacName(algorithm): String?`: `"SHA1"` y `""` dan `HmacSHA1`; `"SHA256"` y `"SHA512"` los suyos; cualquier otro, `null`. Se sigue leyendo en cualquier caja (`uppercase()`), así que `sha256` guardado sigue valiendo (`Totp.kt:76-81`).
- `fun TotpSecret.isSupported()`: algoritmo conocido, `digits in 6..8` y `period in 10..120` (`Totp.kt:84`).
- `fun otpAuthUnsupported(text)`: `true` solo si `text` es un enlace `otpauth://totp/` con una clave válida y algún ajuste no admitido (`Totp.kt:196`). Sale del mismo análisis que `parseOtpAuth` (una función privada `readOtpAuth` con tres resultados: `NotUsable`, `UnsupportedSettings`, `Usable`), así que las dos funciones no pueden discrepar (`Totp.kt:137-190`).
- `const val TOTP_UNSUPPORTED_LINK` y `TOTP_UNSUPPORTED_ENTRY`, con los textos 1 y 2 de «Textos para el Abogado Digital» de `04-cripto-parteB.md`, palabra por palabra (`Totp.kt:87`, `:90`).
- `totpCode`: `if (!s.isSupported()) return null` antes de decodificar la clave; sin `coerceIn` (`Totp.kt:113-122`).
- `parseOtpAuth`: el valor por defecto (SHA1, 6, 30) solo cuando falta el parámetro. Presente y no admitido (fuera de rango, no numérico, vacío, `algorithm=` vacío o desconocido) rechaza el enlace (`Totp.kt:171-183`).
- `hotp` (pública): lanza `IllegalArgumentException` con un algoritmo o unos dígitos no admitidos, en vez de calcular SHA-1 o desbordar el `Int` de `mod` con 10 dígitos (`Totp.kt:97-98`). Ninguna llamada existente se ve afectada: solo la llama `totpCode`, ya filtrada, y los tests con 8 dígitos.
- KDoc de `TOTP_MIN_KEY_BYTES`: 80 bits está por debajo de los 128 de RFC 4226 (R6) y de los 160 recomendados; se acepta por compatibilidad (`Totp.kt:58-63`). El valor (10 bytes) no cambia.

`app/src/main/java/app/seguridad/ui/screens/VaultEntryScreen.kt`:
- `EntryEditViewModel.applyOtpAuth`: si `parseOtpAuth` da `null` y `otpAuthUnsupported(text)`, el error es `TOTP_UNSUPPORTED_LINK`; si no, el de siempre (`:260`).
- Ficha de la entrada: con `!t.isSupported()`, una tarjeta con «servicio · cuenta» y `TOTP_UNSUPPORTED_ENTRY` en `NegativeColor`, en lugar de la tarjeta del código y del campo «Código» (`:149-156`).
- La lista (`VaultScreen.kt`) no cambia: con un ajuste no admitido `totpCode` da `null` y no enseña nada, como hoy. Un test lo fija.

## Tests y cuentas

Antes: app 1266, jvm-check 1261. Después: **app 1277** (0 fallos, 0 omitidos), **jvm-check 1272** (0 fallos). +11 en cada una.

Nuevos en `VaultTotpTest.kt` (10) y `OtpMigrationTest.kt` (1):
- `a link with a setting this app cannot calculate is refused, not changed` (:180): `digits=10/5/9/0/-6/abc/vacío`, `period=5/9/121/300/0/abc/vacío`, `algorithm=MD5/vacío/SHA384`: `parseOtpAuth` da `null` y `otpAuthUnsupported` da `true`; también con el ajuste antes de la clave y con esquema en mayúsculas.
- `without the parameters the defaults stay 6 digits, 30 seconds and SHA1` (:194): sin parámetros 6, 30, SHA1; con solo uno, los otros dos por defecto; los bordes (6, 7, 8; 10, 30, 60, 120) y el algoritmo en cualquier caja.
- `only a valid TOTP link ... is reported as unsupported` (:210): `false` para enlaces buenos, texto vacío, `https`, `hotp`, `otpauth-migration`, sin clave, clave corta, clave no Base32, escape roto (en la consulta y en la etiqueta) y enlace demasiado largo.
- `a code with a setting this app cannot calculate gives no code instead of a wrong one` (:226): `totpCode` da `null` con `MD5`, `SHA384`, `digits` 10 y 5, `period` 300, 5 y 0; `isSupported()` es `false`.
- `an empty algorithm is SHA1, and the name is read in any case, as before` (:242): con `algorithm = ""` da el código SHA1 de RFC 6238 (94287082 a 59 s; 07081804 a 1111111109 s); `sha256`/`Sha256` y `sha512`/`Sha512` dan los vectores de RFC 6238.
- `every setting in range still gives a code, with the time left of its own period` (:257): 6 a 8 dígitos por 10, 30, 60 y 120 s.
- `hotp itself refuses what it cannot calculate` (:265).
- `every TOTP in the real files of Clavero is still supported and gives a code` (:272): abre con la maestra `maestra-de-prueba` `seguridad/vault-1.1.json`, `seguridad/export-1.0.2.json` y el `vault.json` de dentro de `seguridad/backup-1.1.sgbak`; exige al menos una TOTP en cada uno y que todas sean `isSupported()` y den código. Valores guardados reales: las tres tienen `algorithm=SHA1`, `digits=6`, `period=30`, clave de 16 caracteres. No usa nada de `criptopanel/`.
- `the two new texts name Clavero, pass the forbidden words and say what happens` (:296): igualdad exacta con los textos del informe y `ForbiddenWords.problems` vacío.
- `the entry screens use the decision from Totp, not a copy of it` (:303): comprobación del texto fuente de la pantalla (que `ui/` no se compila en la JVM): `applyOtpAuth` usa `otpAuthUnsupported(text)` y `TOTP_UNSUPPORTED_LINK`, la ficha usa `isSupported()` y `TOTP_UNSUPPORTED_ENTRY`, y `VaultScreen.kt` no tiene ninguno de los dos textos.
- `OtpMigrationTest.everyImportedEntryIsSupportedAndGivesACode` (:101): los 12 casos de algoritmo (0 a 3) por dígitos (0 a 2) que escribe Google son `isSupported()` y dan código.

Además, `assertTrue(isSupported())` añadido a `parsesSingleTotp`, `issuerFromNameWhenFieldEmptyAndDefaults`, `parsesKnownGoogleExample`, el lote de 100 y `toOtpAuthRoundTrip`. Los vectores de RFC 6238 (`totp matches RFC 6238`) siguen pasando sin cambios.

## Evidencia TDD

RED. Antes de implementar se pusieron las firmas con cuerpos de pega (`isSupported = true`, `otpAuthUnsupported = false`, los textos), para que el rojo fuera de aserciones y no de compilación:

```
$ export ANDROID_HOME=/opt/android-sdk
$ gradle -p jvm-check test --tests '*Totp*' --tests '*OtpMigration*' --offline --no-daemon --max-workers=1
VaultTotpTest > hotp itself refuses what it cannot calculate FAILED
    java.lang.AssertionError: MD5
VaultTotpTest > the entry screens use the decision from Totp, not a copy of it FAILED
VaultTotpTest > a link with a setting this app cannot calculate is refused, not changed FAILED
    java.lang.AssertionError: digits=10 expected null, but was:<TotpSecret(secret=JBSWY3DPEHPK3PXP, issuer=Example, account=ana, algorithm=SHA1, digits=6, period=30)>
VaultTotpTest > a code with a setting this app cannot calculate gives no code instead of a wrong one FAILED
    java.lang.AssertionError: expected null, but was:<(94287082, 1)>
35 tests completed, 4 failed
BUILD FAILED in 25s
```

Los demás pasaron ya con las pegas: son las guardas de no regresión (valores por defecto, `algorithm = ""`, datos reales, que `otpAuthUnsupported` sea `false` donde toca).

GREEN, mismo comando, con la implementación: `35 tests completed`, 35 `PASSED`, `BUILD SUCCESSFUL in 23s`.

Verificaciones globales (una vez, antes de confirmar):
- `gradle -p jvm-check test --offline --no-daemon --max-workers=1`: `BUILD SUCCESSFUL in 1m 12s`, 1272 `PASSED`, 0 fallos.
- `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets --no-daemon --max-workers=1 --offline`: `BUILD SUCCESSFUL in 3m 27s`; suma de los XML de `testDebugUnitTest`: 1277 tests, 0 omitidos, 0 fallos, 0 errores.

## Archivos cambiados (commit `5657ec3`)

- `app/src/main/java/app/seguridad/crypto/Totp.kt`
- `app/src/main/java/app/seguridad/ui/screens/VaultEntryScreen.kt`
- `app/src/test/java/app/seguridad/vault/VaultTotpTest.kt`
- `app/src/test/java/app/seguridad/crypto/OtpMigrationTest.kt`

Ningún formato, dato de prueba, `criptopanel/`, dependencia, permiso, conexión ni componente tocado.

## Autorrevisión

- Ninguna entrada TOTP que hoy da código deja de darlo: solo cambia el resultado para algoritmo desconocido (antes, SHA-1 en silencio), `digits` fuera de 6 a 8 (antes, recortado) y `period` fuera de 10 a 120 (antes, recortado). El editor no puede producirlos (solo ofrece SHA1/SHA256/SHA512 y 6 u 8), ni `parseOtpMigration` (solo SHA1/SHA256/SHA512, 6 u 8, 30 s). `algorithm = ""` y la caja baja siguen valiendo. Los datos reales de Clavero (tres ficheros) y los 12 casos de Google lo prueban.
- `otpAuthUnsupported` no mira el algoritmo, dígitos o periodo si la clave no vale: un enlace sin clave sigue dando el aviso de siempre.
- Duplicados en el enlace (`digits=6&digits=10`): `toMap()` se queda con el último, como antes; no se ha tocado.
- Los textos 1 y 2 son constantes en español, sin las palabras prohibidas (test con `ForbiddenWords`; `SourceTextsTest` pasa en la ejecución completa).
- Sin logs, sin cadenas con datos: los mensajes de `IllegalArgumentException` de `hotp` son fijos y no llevan ni la clave ni el algoritmo recibido.

## Preocupaciones

1. **Texto 1 y el algoritmo.** El texto del punto 1 dice «un número de dígitos o un periodo», pero el resumen de la tarea exige que `otpAuthUnsupported` sea `true` también con `algorithm=MD5`; en ese caso el aviso nombra algo que no es la causa (antes de la tarea, MD5 daba el aviso genérico «no es un código 2FA»). Lo implementado es lo del informe, palabra por palabra. Propuesta para la revisión final, sin tocar nada más que la constante y la igualdad exacta del test: «Este código 2FA usa un algoritmo, un número de dígitos o un periodo que Clavero no admite.»
2. **Valores vacíos en el enlace.** `digits=` o `period=` presentes y vacíos (antes: valor por defecto) ahora rechazan el enlace, como pide el diseño («un valor presente que no se admite rechaza el enlace»). Un QR de un servicio que escriba así un parámetro vacío daría el aviso nuevo en vez de un código con 6 dígitos y 30 s.
3. **Inspector de QR.** `tools/qr/QrView.kt:184` usa `parseOtpAuth` solo para enseñar servicio y cuenta; con un enlace de ajustes no admitidos ya no enseña esos dos campos (el botón «añadir 2FA» sigue, y el editor da `TOTP_UNSUPPORTED_LINK`). No está en el resumen de la tarea y no se ha tocado.
4. **Lista.** El informe de la auditoría dice «en la lista y en la ficha»; el diseño y el resumen dicen que la lista no cambia. Se ha seguido el diseño.
5. **Observación fuera de alcance.** `hotp` crea `SecretKeySpec(key, "RAW")`, que copia la clave y no se puede poner a cero; el `fill(0)` de `totpCode` solo borra la copia propia. Ya era así; no es parte de CRY-B02.

## Revisión experto-seguridad-cripto

- Falla cerrado, no sustituye: un ajuste que no se sabe calcular no se convierte en otro que da un valor plausible pero falso (MASVS-CRYPTO-1, MASVS-CODE-4). `hmacName` ya no tiene rama «else = SHA-1» (`Totp.kt:76-81`), `totpCode` no recorta (`:113-122`), `parseOtpAuth` no sustituye lo escrito (`:171-183`).
- HOTP/TOTP: HMAC-SHA1, SHA256 y SHA512 de RFC 6238 y truncamiento dinámico de RFC 4226 §5.3 intactos (`:96-110`); los vectores del apéndice B de RFC 6238 pasan (`VaultTotpTest.kt` «totp matches RFC 6238», «an empty algorithm is SHA1…»). SHA-1 sigue solo donde lo exige el protocolo. 6 a 8 dígitos es el rango de RFC 4226; 10 a 120 s es política de la app, no de la RFC (la RFC recomienda 30 s).
- Clave: el mínimo de 80 bits se queda (`:63`) y el comentario deja de atribuir a RFC 4226 un mínimo que no dice: R6 pide 128 bits y recomienda 160.
- Memoria: la clave decodificada se pone a cero en `finally` (`:120`); la salida temprana por `!isSupported()` ocurre antes de decodificarla, así que no deja nada que borrar. Observación previa fuera de alcance: la copia de `SecretKeySpec` (preocupación 5).
- Entrada no fiable: el enlace sigue limitado a `OTPAUTH_MAX_CHARS` (`:150`), los escapes rotos siguen siendo «no usable» (`:184`), y `digits`/`period` no numéricos no lanzan (`toIntOrNull`, `:172-173`). `hotp` valida sus argumentos (`:97-98`) en vez de desbordar `mod` con 10 dígitos.
- Sin cambio de formato ni migración: lo guardado se lee igual (`algorithm = ""` = SHA1; mayúsculas o minúsculas).

## Revisión Ingeniero Móvil Senior

- La decisión vive en `crypto/Totp.kt`, que `jvm-check` compila y prueba; la pantalla solo la llama (`VaultEntryScreen.kt:149`, `:260`), y un test de texto fuente impide que se copie la lógica en la pantalla.
- Compose: `isSupported()` es una comparación sin asignaciones; el efecto de `now` cada segundo y la estructura de la ficha no cambian (`VaultEntryScreen.kt:135-159`). El aviso es un `Text` normal dentro de `SectionCard`: TalkBack lo lee, y no depende solo del color (el mensaje lo dice con palabras).
- El estado del formulario no cambia: el error sigue en `form.error` (ViewModel), nunca en `rememberSaveable`. Ningún secreto nuevo en `Bundle`.
- Los textos son constantes en español (convención del proyecto), no en `strings.xml`; `SourceTextsTest` las recorre.
- Sin permisos, dependencias, conexiones ni componentes nuevos.

## Textos para el Abogado Digital (revisión final)

1. `TOTP_UNSUPPORTED_LINK` (`crypto/Totp.kt`), al leer un QR o enlace `otpauth://` válido con un ajuste que Clavero no calcula, en el editor y, desde la ronda 1, también como nota en el inspector de QR: «Este código 2FA usa un algoritmo, un número de dígitos o un periodo que Clavero no admite.» (Texto corregido en la ronda 1: la primera versión, la del informe de la auditoría, no nombraba el algoritmo y era falsa con `algorithm=MD5`.)
2. `TOTP_UNSUPPORTED_ENTRY` (`crypto/Totp.kt:90`), en la ficha de una entrada guardada con un ajuste no admitido, en lugar del código: «Clavero no puede calcular este código: usa un ajuste que no admite.»

---

# Ronda 1 (correcciones de la revisión)

Commit: `1f88721` «totp: ronda 1 de CRY-B02, texto verdadero, inspector de QR con nombres y nota, algoritmo vacío en el enlace». Rama `ccr-a1a6eabb-765f9g`, un push después del commit.

## Cambios

- **I1.** `TOTP_UNSUPPORTED_LINK` es ahora «Este código 2FA usa un algoritmo, un número de dígitos o un periodo que Clavero no admite.» (`crypto/Totp.kt`, constante). La igualdad exacta de `VaultTotpTest` («the two new texts name Clavero…») se ha actualizado y sigue pasando `ForbiddenWords`. La nota para el Abogado Digital de arriba está corregida.
- **M1.** El inspector de QR sigue enseñando «Servicio» y «Cuenta» de un enlace `otpauth://totp/` con clave válida y ajustes no admitidos, más la nota `TOTP_UNSUPPORTED_LINK` (la misma constante, sin texto nuevo; `QrInspectScreen` ya pinta `notes`):
  - `crypto/Totp.kt`: el resultado `UnsupportedSettings` de `readOtpAuth` lleva ahora `issuer` y `account` (ya pasados por `otpName`, sin caracteres de control ni de giro), y una función pequeña `otpAuthNames(text): Pair<String, String>?` los expone, también para un enlace usable; `null` si no es un enlace TOTP con clave válida. La decisión sigue en `crypto/`. Nunca expone la clave.
  - `tools/qr/QrView.kt`: usa `otpAuthNames` para los campos y `otpAuthUnsupported(kind.text)` para la nota (`notes = listOfNotNull(TOTP_UNSUPPORTED_LINK.takeIf { … })`). Con un enlace sin clave, con clave corta o `hotp` no hay nombres ni nota, como antes. El botón de añadir 2FA y el texto oculto (`textHidden`) no cambian.
- **M4.** `toOtpAuthUri` escribe `algorithm=SHA1` cuando el algoritmo guardado está vacío (que `isSupported()` ya cuenta como SHA1); el resto se escribe como antes, en mayúsculas. Un código guardado con `algorithm = ""` ya se puede enseñar como QR a otra app y se lee de vuelta.
- **M6.** Cada constante de rango tiene su propio KDoc: `TOTP_DIGITS` (RFC 4226 §5.3, 6 como mínimo y posiblemente 7 y 8) y `TOTP_PERIODS` (rango propio de la app; RFC 6238 sugiere 30 s). La cabecera repetida «servicio · cuenta» se deja como está (decisión del controlador).
- **M7.** `readOtpAuth` valida la clave con `isUsableTotpKey`, que decodifica, comprueba y pone a cero esa copia antes de devolver. No hay test de la puesta a cero (la copia es local y privada); los tests de comportamiento de los enlaces siguen pasando.
- **M2, M3, M5.** Sin cambios, según las decisiones del controlador.

## Tests nuevos (+3 en cada suite)

- `VaultTotpTest`: `the service and the account of a link are read also when its settings are not supported`: `otpAuthNames` da (servicio, cuenta) con `digits=10`, `period=300`, `algorithm=MD5`, `digits=abc`, `period=`, `algorithm=` y con un enlace bueno; los nombres salen limpios de caracteres de giro; da `null` con texto vacío, `https`, `hotp`, sin clave, clave corta o no Base32, escape roto en la consulta y en la etiqueta, y `otpauth-migration`.
- `QrViewTest.aCodeWithSettingsTheAppCannotCalculateStillShowsItsNamesAndSaysSo`: con `digits=10`, `period=300`, `algorithm=MD5`, `digits=5` y `period=5` la vista tiene `ADD_TOTP`, `textHidden`, «Servicio» = Banco, «Cuenta» = ana, `notes == [TOTP_UNSUPPORTED_LINK]` y la clave no sale en ningún campo; con ajustes admitidos no hay nota; sin clave, con clave corta o `hotp` no hay campos ni nota.
- `OtpMigrationTest.toOtpAuthWritesSha1ForAnEmptyAlgorithm`: vuelta completa de una entrada con `algorithm = ""` (y 8 dígitos): el enlace lleva `algorithm=SHA1`, `parseOtpAuth` lo lee como la misma entrada con `SHA1`, y el código de ambas a 59 s es el de RFC 6238 (94287082).
- Se actualizó la igualdad exacta del texto (I1).

## Evidencia TDD

RED. Con el código de producción de la ronda guardado aparte (`git stash push -- app/src/main`) y solo una pega `otpAuthNames = null` para que compile:

```
$ gradle -p jvm-check test --tests '*Totp*' --tests '*OtpMigration*' --tests '*QrViewTest*' --offline --no-daemon --max-workers=1
OtpMigrationTest > toOtpAuthWritesSha1ForAnEmptyAlgorithm FAILED
    java.lang.AssertionError: otpauth://totp/Banco:ana?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ&issuer=Banco&algorithm=&digits=8&period=30
QrViewTest > aCodeWithSettingsTheAppCannotCalculateStillShowsItsNamesAndSaysSo FAILED
    java.lang.AssertionError: digits=10 expected:<Banco> but was:<null>
VaultTotpTest > the service and the account of a link are read also when its settings are not supported FAILED
    java.lang.AssertionError: expected:<(Example, ana)> but was:<null>
VaultTotpTest > the two new texts name Clavero, pass the forbidden words and say what happens FAILED
    org.junit.ComparisonFailure: expected:<...e código 2FA usa un [algoritmo, un ]número de dígitos o ...>
58 tests completed, 4 failed
BUILD FAILED in 23s
```

(La pega y el stash se deshicieron: `git checkout -- app/src/main` y `git stash pop`; sin restos de `STUB` en `Totp.kt`.)

GREEN, con la implementación y además `SourceTextsTest` y `ToolTextsTest`:

```
$ gradle -p jvm-check test --tests '*Totp*' --tests '*OtpMigration*' --tests '*QrView*' --tests '*SourceTexts*' --tests '*ToolTexts*' --offline --no-daemon --max-workers=1
BUILD SUCCESSFUL in 24s      (93 PASSED; QrViewTest 21, SourceTextsTest 12, ToolTextsTest 22)
```

Verificaciones globales (una vez, tras los cambios):
- `gradle -p jvm-check test --offline --no-daemon --max-workers=1`: `BUILD SUCCESSFUL in 1m 1s`, **1275** `PASSED` (antes de la ronda 1272), 0 fallos.
- `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets --no-daemon --max-workers=1 --offline`: `BUILD SUCCESSFUL in 3m 7s`; suma de los XML de `testDebugUnitTest`: **1280** tests (antes 1277), 0 omitidos, 0 fallos, 0 errores.

## Archivos cambiados en la ronda

- `app/src/main/java/app/seguridad/crypto/Totp.kt`
- `app/src/main/java/app/seguridad/tools/qr/QrView.kt`
- `app/src/test/java/app/seguridad/vault/VaultTotpTest.kt`
- `app/src/test/java/app/seguridad/crypto/OtpMigrationTest.kt`
- `app/src/test/java/app/seguridad/tools/qr/QrViewTest.kt`

## Revisión experto-seguridad-cripto (ronda 1)

- La clave decodificada solo para validarla se pone a cero antes de salir (`Totp.kt`, `isUsableTotpKey`, llamada en `readOtpAuth`); la rama de error (enlace rechazado, analizado dos veces por `parseOtpAuth` y `otpAuthUnsupported`) ya no deja esa copia. El `String` del enlace sigue vivo y no se puede borrar.
- `otpAuthNames` devuelve solo servicio y cuenta, ya saneados por `otpName` (sin control ni giro de texto, 256 como máximo); nunca la clave. El inspector sigue ocultando el texto completo (`textHidden = true`) y los tests comprueban que la clave no sale en ningún campo.
- Un código guardado con algoritmo vacío (SHA-1 por compatibilidad) ya se exporta como `algorithm=SHA1`, que es lo que hace: el enlace y el código guardado dan el mismo valor (vector de RFC 6238).
- Sigue sin haber `coerceIn` ni sustitución de ajustes; los textos de las dos pantallas dicen ahora la causa real (algoritmo, dígitos o periodo).

## Revisión Ingeniero Móvil Senior (ronda 1)

- La decisión sigue en `crypto/Totp.kt` (JVM): el inspector (`tools/qr/QrView.kt`, también JVM) llama a `otpAuthNames` y `otpAuthUnsupported`; ninguna pantalla copia la lógica. La pantalla del inspector no cambia: ya pinta `v.notes` con `CautionColor` (`ui/screens/QrInspectScreen.kt:344`).
- Sin permisos, dependencias, conexiones ni componentes nuevos; sin textos nuevos (la nota reutiliza la constante). Sin estado nuevo en `rememberSaveable` ni `Bundle`.

## Textos para el Abogado Digital (ronda 1)

Los mismos dos de arriba; el 1 con la redacción corregida. Nuevo uso, sin texto nuevo: `TOTP_UNSUPPORTED_LINK` también sale como nota (en color de aviso) en el inspector de QR de un enlace `otpauth://totp/` con ajustes no admitidos.
