# Revisión de la tarea 5: CRY-B02, TOTP no inventa ajustes

Rango revisado: `7256c07..5657ec3` (un commit). El diff no estaba cortado. No he vuelto a pasar las suites (el informe del implementador es una afirmación sin verificar). Hice una sola prueba focalizada, para una duda concreta (ver «Revisión experto-seguridad-cripto»). Usé además un `git status` para comprobar que el árbol quedaba limpio; la prueba era un archivo temporal que borré.

### Spec Compliance

**Cumple la tarea.** Todo lo que pide el brief está, con una excepción de redacción que viene del propio plan (Important, plan-mandated, abajo).

- `hmacName` devuelve `String?`; `"SHA1"` y `""` dan `HmacSHA1`, `SHA256`/`SHA512` los suyos, cualquier otro `null`, y se sigue leyendo en cualquier caja (`Totp.kt:76-81`).
- `fun TotpSecret.isSupported()` (`Totp.kt:84`), `fun otpAuthUnsupported(text)` (`:196`), `TOTP_UNSUPPORTED_LINK` (`:87`) y `TOTP_UNSUPPORTED_ENTRY` (`:90`). Los dos textos coinciden palabra por palabra con los puntos 1 y 2 de `auditoria/04-cripto-parteB.md:344-347` y dicen «Clavero».
- `totpCode` da `null` si `!isSupported()` y ya no usa `coerceIn` (`:113-122`).
- `parseOtpAuth` usa el valor por defecto solo si falta el parámetro (`:171-173`); un valor presente y no admitido rechaza el enlace.
- KDoc de `TOTP_MIN_KEY_BYTES` (`:59-63`): 80 bits por debajo de los 128 de RFC 4226 R6 y de los 160 recomendados, aceptado por compatibilidad. Correcto: R6 dice «at least 128 bits» y recomienda 160. El valor (10 bytes) no cambia.
- Pantallas: `applyOtpAuth` usa `otpAuthUnsupported(text)` (`VaultEntryScreen.kt:260`); la ficha pone `TOTP_UNSUPPORTED_ENTRY` en lugar de la tarjeta del código y del campo «Código» (`:149-156`); la lista no cambia (`VaultScreen.kt:455-463` sin tocar).
- Tests del paso 1: todos están.
  - `digits=10/5`, `period=5/300`, `MD5` dan `null` y `otpAuthUnsupported` `true` (`VaultTotpTest.kt:473-485`).
  - Valores por defecto sin parámetros (`:487-501`).
  - `totpCode` da `null` con MD5, `digits=10` y `period=300`, y con `algorithm=""` da el código de RFC 6238 (`:519-548`).
  - Todas las TOTP de `vault-1.1.json`, `export-1.0.2.json` y `backup-1.1.sgbak` son `isSupported()` y dan código (`:565-587`); los 12 casos de Google (`OtpMigrationTest.kt` `everyImportedEntryIsSupportedAndGivesACode`).
  - Los vectores de RFC 6238 siguen sin tocar (`VaultTotpTest.kt:113-118`).
- Evidencia de rojo con cuerpos de pega (fallo de aserción y no de compilación). Es buena práctica de TDD.
- Cuentas: 1266 a 1277 en app y 1261 a 1272 en jvm-check, solo suben (sin verificar). No hay formato, dato de prueba, dependencia, permiso ni componente tocado en el diff.
- Mensaje de commit y `Co-Authored-By`: no verificable sin git. Lo afirma el informe.

### Strengths

- **Una sola decisión, un solo análisis.** `parseOtpAuth` y `otpAuthUnsupported` salen de la misma `readOtpAuth` con tres resultados (`Totp.kt:135-196`). No pueden discrepar, y es la forma correcta de evitar la copia de la lógica en la pantalla.
- **Falla cerrado.** Desaparece la rama «else = SHA-1» y el `coerceIn`. No hay degradación silenciosa, que era el hallazgo.
- **`otpAuthUnsupported` es estrecho.** Solo es `true` con esquema y host correctos, clave válida, etiqueta y consulta decodificables y algún ajuste no admitido. Un escape roto (en la consulta o en la etiqueta), la falta de clave, la clave corta, `hotp`, `https`, `otpauth-migration` y el enlace demasiado largo dan `false`. Está probado (`VaultTotpTest.kt:503-517`). Lo comprobé siguiendo el orden de `readOtpAuth`: la clave se valida antes de leer `digits`/`period` (`:165` frente a `:172`), y la etiqueta se decodifica antes (`:166`).
- **Cuidado con lo guardado.** `""` sigue siendo SHA-1; `sha256`, `Sha512` y `sha1` se siguen leyendo; el rango 6 a 8 incluye el 7; hay un test con ficheros reales de la 1.0.2 y la 1.1 y con los 12 casos de Google.
- **`hotp` valida sus argumentos** (`:97-98`). Antes, 10 dígitos desbordaban el `Int` de `mod` sin avisar.
- **Mensajes de excepción fijos**, sin la clave ni el algoritmo recibido.
- **La ficha conserva la clave secreta y el botón «Mostrar QR para otra app»** (`VaultEntryScreen.kt:191-194`, sin tocar). Con un ajuste no admitido, el usuario puede llevarse la clave a otra app. `toOtpAuthUri` exporta los ajustes tal cual los guardó.
- **El informe señala sus propias dudas** (las cuatro preocupaciones y la quinta, fuera de alcance), con una propuesta de texto concreta.

### Issues

#### Critical

Ninguno.

#### Important

**I1 (plan-mandated). `TOTP_UNSUPPORTED_LINK` afirma algo falso con `algorithm=MD5`, `algorithm=SHA384` y `algorithm=` vacío.** `Totp.kt:87` dice «un número de dígitos o un periodo», pero `readOtpAuth` también devuelve `UnsupportedSettings` por el algoritmo (`:171`, `:183`) y `VaultEntryScreen.kt:260` lo enseña en esos casos. El enlace `…&algorithm=MD5` tiene dígitos y periodo normales y el aviso culpa a lo que no es. El brief manda las dos cosas a la vez: el texto del punto 1 del informe (sin algoritmo) y `otpAuthUnsupported` `true` con `algorithm=MD5`. El implementador siguió el plan al pie de la letra y lo avisó. Pero el texto sale en pantalla, y el CLAUDE.md pide que ningún texto afirme algo falso sobre el código.

Corrección de dos líneas, que adopto como recomendación al controlador: cambiar la constante a «Este código 2FA usa un algoritmo, un número de dígitos o un periodo que Clavero no admite.» (el texto alternativo del propio informe; no contiene ninguna palabra prohibida) y la igualdad exacta de `VaultTotpTest.kt:590`. `TOTP_UNSUPPORTED_ENTRY` es genérico y es verdadero en todos los casos. El texto 1 de `04-cripto-parteB.md` también hay que corregirlo en «Textos para el Abogado Digital» para que la revisión final parta de la redacción verdadera.

#### Minor

**M1. Inspector de QR (preocupación 3 del implementador).** `tools/qr/QrView.kt:184-188`: `QrContent.kt:72` clasifica por prefijo `otpauth://`, así que el enlace sigue siendo `QrKind.OtpAuth` con su botón «añadir 2FA». Pero `parseOtpAuth` da `null` con un ajuste no admitido y `fields` queda vacío: se pierden servicio y cuenta, que antes se enseñaban (con la mentira de 6 dígitos y 30 s). No es un fallo de seguridad ni un error: el comportamiento coincide con el de los enlaces `hotp` y los rotos, que ya salen sin campos. Al pulsar el botón el editor da el aviso nuevo, que es claro. Mi decisión:
- No es una regresión que bloquee esta tarea ni entra en su alcance. El brief limita los archivos a `Totp.kt` y `VaultEntryScreen.kt`, y arreglarlo exige una decisión de producto, una vía para leer servicio y cuenta de un enlace no admitido (hoy `readOtpAuth` los descarta), un texto nuevo para el Abogado Digital y un test en `QrViewTest`.
- Lo que el inspector debería hacer es enseñar servicio y cuenta con una nota (reutilizando `TOTP_UNSUPPORTED_LINK` mediante `otpAuthUnsupported`), porque su trabajo es inspeccionar y la identificación del QR es lo que ayuda a decidir. Como la regla del proyecto es que los detalles menores no se aplazan, lo apuntaría como tarea pequeña de la 1.3.1 o en `progress.md`, no lo dejaría en el aire.

**M2. Valores vacíos (preocupación 2).** `digits=`, `period=` o `digits= 6` (con espacio) rechazan el enlace (`Totp.kt:172-173`; `toIntOrNull` da `null`). Es lo que pide el diseño («un valor presente que no se admite rechaza el enlace»), falla cerrado y el usuario siempre puede teclear la clave. Un servicio real que emita un parámetro vacío es improbable. Aceptable, y el test lo fija (`VaultTotpTest.kt:474`).

**M3. `hotp` lanza (preocupación 4).** Es un cambio de la API pública, pero el único llamador de producción es `totpCode`, ya filtrado (`Totp.kt:114`); los tests solo la llaman con 6/8 dígitos y SHA1/256/512. Sin impacto. La validación doble (en `totpCode` y en `hotp`) es redundante pero barata y defensiva.

**M4. `toOtpAuthUri` con `algorithm = ""`** (`Totp.kt:216`, sin tocar) escribe `algorithm=`, que `parseOtpAuth` rechaza (`:183`), mientras `isSupported()` lo acepta como SHA-1. Ninguna ruta de escritura de Clavero produce `""`, y el comportamiento de `parseOtpAuth` con `algorithm=` es el mismo de antes (rechazo). El comentario de `:182` lo explica. Solo lo anoto por coherencia; no hay que tocarlo.

**M5. Test de pantalla por texto fuente** (`VaultTotpTest.kt:596-611`). Acepta que `isSupported()` y `TOTP_UNSUPPORTED_ENTRY` aparezcan en cualquier parte entre `VaultEntryDetailScreen(` y `private class EntryEditViewModel`, no solo dentro de la rama de la ficha. Es un test débil por naturaleza (`ui/` no se compila en la JVM) y sigue el patrón del proyecto. Suficiente.

**M6. Estilo.** El KDoc de `Totp.kt:68` está pegado solo a `TOTP_DIGITS` y no a `TOTP_PERIODS`. La tarjeta no admitida repite el encabezado «servicio · cuenta» de la tarjeta del código (`VaultEntryScreen.kt:152` y `:161`). Detalles sin importancia.

**M7. Observación previa, sin cambio de esta tarea.** `readOtpAuth` decodifica la clave solo para validarla (`totpKey(it) != null`, `Totp.kt:165`) y no pone a cero esa copia. Con el cambio, un enlace rechazado se analiza dos veces (`parseOtpAuth` y `otpAuthUnsupported`), así que se hace una copia más en la rama de error. El `String` del enlace sigue vivo de todos modos y no se puede borrar, así que el valor de `fill(0)` aquí es casi nulo. No es de esta tarea.

### Assessment

**Task quality:** Approved (con I1, plan-mandated, que el controlador debe resolver; ver abajo).

**Reasoning:** el código hace lo que el brief y el diseño piden, falla cerrado y no pierde ninguna TOTP que hoy dé código. Revisé las rutas de escritura de `TotpSecret`: `parseOtpAuth` (solo SHA1/SHA256/SHA512 antes de la tarea), `parseOtpMigration` (`OtpMigration.kt:141-159`: SHA1/SHA256/SHA512, 6 u 8 dígitos, 30 s, y MD5/HOTP/desconocido ya se omiten con motivo), los chips del editor (`VaultEntryScreen.kt:454-455`: 6/8 y SHA1/256/512), el valor por defecto de la clase y la importación JSON/`.sgbak`, que deserializa tal cual. Ninguna produce algo que el nuevo `isSupported()` rechace. La única diferencia observable en datos guardados es para valores que ningún camino de Clavero escribe (nombres de algoritmo como `MD5` o `SHA-256`, `digits` fuera de 6..8 o `period` fuera de 10..120), y esos daban un código inventado y erróneo, que es justo el hallazgo. Los tests cubren la compatibilidad de verdad (ficheros reales, 12 casos de Google, caja de los nombres).

La única cosa que arreglaría ya es I1: es un texto de pantalla falso en un caso real (MD5), nace de una contradicción dentro del plan, y cuesta dos líneas. No lo cuento como «Needs fixes» porque el implementador siguió el brief tal cual, avisó, y dejó la alternativa escrita; el controlador solo tiene que decidir el texto y aplicarlo (o pedírselo al implementador) antes de la revisión del Abogado. M1 va a un seguimiento pequeño, no a esta tarea.

### Revisión experto-seguridad-cripto

- **Falla cerrado, no sustituye** (MASVS-CRYPTO-1, MASVS-CODE-4). Sin `else -> SHA-1` (`Totp.kt:76-81`), sin `coerceIn` (`:113-122`), sin valor por defecto cuando el parámetro está presente (`:171-173`). Un código que no se sabe calcular no se convierte en uno plausible y falso.
- **HOTP/TOTP intacto**: el truncamiento dinámico de RFC 4226 §5.3 y el HMAC de RFC 6238 (`:96-107`) no cambian. Los vectores de RFC 6238 (SHA1/256/512, 59 s y 1111111109 s) pasan según el informe y siguen en `VaultTotpTest.kt:113-118`; el nuevo test de `algorithm=""` repite el de SHA1 (`:535-548`). 6 a 8 dígitos es el mínimo de RFC 4226 §5.3 («6 dígitos como mínimo, y posiblemente 7 y 8»); 10 a 120 s es política de la app.
- **SHA-1 sigue** solo donde lo exige el protocolo, sin debilitar nada.
- **Comentario de RFC 4226 corregido y exacto**: R6 dice «at least 128 bits» y recomienda 160 (`Totp.kt:59-63`). Aceptar 80 bits por compatibilidad es una decisión razonable y está explicada.
- **Memoria**: la clave decodificada se pone a cero en el `finally` (`:119-121`); la salida por `!isSupported()` ocurre antes de decodificarla (`:114`). `hotp` crea un `SecretKeySpec(key, "RAW")` que copia la clave y no se puede borrar (ya era así; el informe lo declara fuera de alcance, de acuerdo).
- **Entrada no fiable**: el enlace sigue limitado a 4096 caracteres, los escapes rotos son «no usable» (`:149-188`) y `digits`/`period` no numéricos no lanzan. Un valor duplicado (`digits=6&digits=10`) lo resuelve `toMap()` quedándose con el último, como antes; no es regresión.
- **Compatibilidad con lo guardado, comprobada con una prueba focalizada** (no estaba en el informe): abrí con `maestra-de-prueba` los tres `criptopanel/vault-v1.json`, `vault-noversion.json` y `vault-v2.json` (los que `BackupTest.jsonExportsStillImport` importa) y las TOTP de los tres son `algorithm=SHA1, digits=6, period=30`, `isSupported()` y dan código. Eso cierra el hueco de que el test del implementador no mirase `criptopanel/` (los usuarios de la 1.3.0 pudieron importar esas copias). Borré el archivo de la prueba; el árbol quedó limpio.
- **Efecto residual a dejar por escrito** (informativo): las entradas que ya se guardaron antes de esta tarea desde un QR con `digits=10` o `period=300` quedaron con 6 dígitos y 30 s (la información se perdió al guardar), y seguirán dando un código que el servicio no acepta. Esta tarea evita los nuevos casos pero no puede arreglar los antiguos. Lo cita el propio informe de la auditoría («el único efecto real es un código erróneo con un QR raro, que el usuario detecta al dar de alta el 2FA»).
- **Sin cambio de formato ni migración**; sin logs; sin secretos en mensajes de excepción.

### Revisión Ingeniero Móvil Senior (file:line)

- **Lógica en el módulo probado por JVM.** La decisión vive en `crypto/Totp.kt` y las pantallas solo la llaman (`VaultEntryScreen.kt:149`, `:158`, `:260`). Es la separación correcta para poder probarla sin Android; el test de texto fuente impide que se copie en la pantalla.
- **Llamadores revisados** (los cuatro y ninguno más): `VaultEntryScreen.applyOtpAuth` (`:243-265`, único punto por el que pasan el escáner con cámara, el portapapeles y la entrega del inspector, `:379-388`, `:447`); `VaultEntryScreen` ficha (`:149-158`); `VaultScreen.kt:456` (lista, sin cambio: con ajuste no admitido no enseña nada, como hoy); `QrView.kt:184` (M1).
- **Compose**: `isSupported()` es una comparación barata sin asignaciones relevantes (`uppercase()` es invariante de región, no afecta el «i» turco), y se evalúa cada segundo por el efecto de `now`. No cambia la estructura de efectos ni la clave de recomposición. El aviso es texto normal dentro de `SectionCard`; no depende solo del color, TalkBack lo lee.
- **Estado**: el error sigue en `form.error` (ViewModel), nunca en `rememberSaveable`; ningún secreto nuevo en `Bundle`.
- **Textos**: constantes en español (convención del proyecto) sin palabras prohibidas; `SourceTextsTest` las recorre (según el informe). Ver I1 para la verdad del texto del enlace.
- **Sin permisos, dependencias, conexiones ni componentes nuevos.**

### Textos para el Abogado Digital

1. `TOTP_UNSUPPORTED_LINK` (`crypto/Totp.kt:87`), al leer un QR o enlace `otpauth://totp/` válido con un ajuste que Clavero no calcula. Texto actual (falso con un algoritmo no admitido): «Este código 2FA usa un número de dígitos o un periodo que Clavero no admite.» **Recomendado, en el sitio de I1:** «Este código 2FA usa un algoritmo, un número de dígitos o un periodo que Clavero no admite.»
2. `TOTP_UNSUPPORTED_ENTRY` (`crypto/Totp.kt:90`), en la ficha de una entrada guardada con un ajuste no admitido, en lugar del código: «Clavero no puede calcular este código: usa un ajuste que no admite.» (Verdadero en todos los casos.) Tener en cuenta que la lista no enseña este texto ni ningún otro: con un ajuste no admitido no aparece código en ella, como hoy, y el punto 2 de la auditoría decía «en la lista y en la ficha».
3. Si se hace M1 (inspector de QR), un texto de nota en el inspector (puede ser el mismo del punto 1).
