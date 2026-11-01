# Auditoría criptográfica (fase 4), parte B: clave pública, generadores, TOTP y red

- **Proyecto:** Clavero 1.3.0, commit `b08d2ec` (publicada).
- **Fecha:** 1-11-2026.
- **Alcance de la parte B:** `crypto/pk/*` (tarjeta, identidad, mensaje, archivo, firmas, cabecera común y «Abrir lo recibido»), `crypto/Generator.kt`, `crypto/Hashes.kt`, `crypto/Totp.kt`, `crypto/Bip39.kt`, `crypto/Pwned.kt`, `crypto/OtpMigration.kt`, `tools/codes/*` (PIN, códigos, respuestas inventadas, sesión), `net/*` (cliente HTTP, HIBP y desconfianza de eMudhra), y como apoyo `vault/PkActions.kt`, `vault/Contacts.kt`, `vault/TotpImport.kt`, `tools/KeyPlacement.kt` y `tools/cipher/CipherSessions.kt`.
- **Fuera de alcance (parte A):** cifrado simétrico (`Crypto.kt`, `FileCrypto.kt`), bóveda, copias y Keystore. Se han leído solo las funciones que la parte B llama (`aead`, `streamNonce`, `randomBytes`, `secureRandom`).
- **Método:** lectura completa de cada fichero y de su flujo de datos, el diseño `docs/superpowers/specs/2026-10-26-seguridad-1.2.0-clave-publica-design.md`, los tests de `app/src/test/java/app/seguridad/{crypto,net,vault}` y los vectores de `app/src/test/resources/`. Desensamblado de Bouncy Castle 1.86 (`javap`) para confirmar qué valida al leer claves X-Wing y ML-KEM. Código fuente de Conscrypt (`google/conscrypt`, rama `master`, `common/src/main/java/org/conscrypt/TrustManagerImpl.java`, consultado el 1-11-2026) para confirmar cómo valida Android las cadenas TLS. No se ha modificado ningún fichero de la app.

## Modelo de amenaza aplicado

| Atacante | Qué puede hacer | Dónde se mira |
|---|---|---|
| El canal (chat, correo) | Leer, cambiar, reenviar, reordenar o truncar tarjetas, mensajes, archivos y firmas | `Card.kt`, `Message.kt`, `FileMessage.kt`, `Signatures.kt`, `Received.kt` |
| Quien manda una tarjeta preparada | Claves de orden pequeño, claves ajenas (unknown key-share), nombres engañosos | `Card.kt`, `Contacts.kt`, `PkActions.kt` |
| Un intermediario en la red (Wi-Fi pública) con una clave de CA comprometida (eMudhra) | Suplantar `api.pwnedpasswords.com` | `net/DistrustedCertificates.kt`, `net/HttpClient.kt`, `net/PwnedPasswords.kt` |
| Quien observa el generador | Adivinar contraseñas, PIN, códigos o respuestas por sesgo o semilla | `Generator.kt`, `Pin.kt`, `RecoveryCodes.kt`, `InventedAnswers.kt` |
| Un QR de 2FA o una exportación de Google Authenticator manipulados | Entrada no fiable | `Totp.kt`, `OtpMigration.kt` |
| Malware con root, o el móvil desbloqueado con la bóveda abierta | Todo | **No se protege** (diseño, «Modelo de amenaza») |

## Resumen

| ID | Severidad | Título | Ubicación |
|---|---|---|---|
| CRY-B01 | Media (CVSS 4.2) | La desconfianza de eMudhra mira la cadena que manda el servidor, no la que valida Android: la caché de intermedios del sistema la salta | `net/DistrustedCertificates.kt:77-96` |
| CRY-B02 | Informativa (CVSS 0.0) | TOTP: un algoritmo, un número de dígitos o un periodo no admitidos se sustituyen en silencio por los de por defecto | `crypto/Totp.kt:65-69`, `:87-89`, `:131-132` |

No hay hallazgos críticos ni altos en la parte B. El núcleo de clave pública (HPKE con X-Wing, Ed25519 y Ed25519ph, STREAM con ChaCha20-Poly1305) está bien compuesto: separación de dominios en todas las firmas, la firma del remitente cubre la cabecera y la identidad entera del destinatario, las claves recibidas se validan, y las firmas inválidas rechazan el dato entero.

## Hallazgos

### CRY-B01. La desconfianza de eMudhra mira la cadena que manda el servidor, no la que valida Android: la caché de intermedios del sistema la salta

- **Dónde:** `app/src/main/java/app/seguridad/net/DistrustedCertificates.kt:77-96`, con `:69-70` y `:110-116`.

  ```kotlin
      private fun refuse(chain: Array<out X509Certificate>) {
          chain.firstOrNull { it.isDistrusted() }?.let {
              throw CertificateException("Distrusted certificate authority (eMudhra key compromise): ${it.subjectX500Principal.name}")
          }
      }

      override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) {
          base.checkServerTrusted(chain, authType)
          refuse(chain)
      }

      override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String, socket: Socket?) {
          (base as? X509ExtendedTrustManager)?.checkServerTrusted(chain, authType, socket) ?: base.checkServerTrusted(chain, authType)
          refuse(chain)
      }
  ```

  ```kotlin
  internal fun X509Certificate.isDistrusted(): Boolean =
      subjectX500Principal.isDistrustedRootName() || issuerX500Principal.isDistrustedRootName() || spkiSha256() in DISTRUSTED_SPKI_SHA256
  ```

- **Estado:** Confirmado por lectura del código de la app y del de Conscrypt. Falta la prueba dinámica descrita abajo (no se ha ejecutado en un móvil).
- **Severidad:** Media, CVSS 3.1 **4.2**, `CVSS:3.1/AV:N/AC:H/PR:N/UI:R/S:U/C:L/I:L/A:N`.
- **Control MASVS:** MASVS-NETWORK-1 (validación de la identidad del servidor TLS). Relacionado con MASVS-NETWORK-2, porque la lista de claves desconfiadas es una política de confianza propia de la app.

**Por qué es un problema en esta app.**

1. `refuse` comprueba `chain`, que es la cadena **que presenta el servidor**, después de que el sistema (`base`) la haya aceptado. No comprueba la cadena **que el sistema ha construido y validado**.
2. En Android, `base` es el `RootTrustManager` de Network Security Config, que delega en `com.android.org.conscrypt.TrustManagerImpl`. Esa clase guarda en una caché de proceso los intermedios de toda cadena que valida y los usa después para completar cadenas a las que les falta el intermedio. En Conscrypt (`TrustManagerImpl.java`, rama `master`):
   - `:738-743`: «Add intermediate CAs to the index to tolerate sites that assume that the browser will have cached these» → `intermediateIndex.index(untrustedChain.get(i))`, justo después de `validator.validate(...)`;
   - `:643-660`: «5. Finally try the cached intermediates to handle server that failed to send them» → `intermediateIndex.findAllByIssuerAndSignature(current)`.
3. El intermedio comprometido de los tests (`app/src/test/resources/emudhra/em-dv-tls-ca-g3b-cross.pem`) tiene como sujeto `C=NL, O=eMudhra BV, CN=EM DV TLS CA - G3B`, como emisor `AAA Certificate Services` (Comodo) y `pathlen:0`. Una hoja falsa que emita con su clave tiene como emisor `CN=EM DV TLS CA - G3B, O=eMudhra BV`, que **no** está en `DISTRUSTED_ROOT_CNS` (`:49`, solo raíces y solo con `O=eMudhra Technologies Limited`), y una SPKI del atacante, que **no** está en `DISTRUSTED_SPKI_SHA256`.

Ataque con las premisas del propio fichero (`:21-24`: «the private keys of eight intermediate CAs were recoverable», y están firmadas por «AAA Certificate Services, a root many Android versions still trust»):

1. Un intermediario en la red de la víctima responde a la primera conexión a `api.pwnedpasswords.com` con `[hoja falsa, EM DV TLS CA - G3B firmado por AAA]`. El sistema la valida contra AAA y **guarda el intermedio en su caché**. Después `refuse` encuentra la SPKI y lanza: la víctima ve «No se pudo comprobar. Revisa la conexión.» (`ui/screens/SecurityToolsScreen.kt`, `BreachViewModel.check`).
2. La víctima vuelve a pulsar «Comprobar» (o lanza la comprobación de «Salud», `ui/screens/HealthScreen.kt:135-175`). El intermediario manda ahora solo `[hoja falsa]`. El sistema completa la cadena con el intermedio de su caché y la acepta; `refuse([hoja falsa])` no encuentra nada y **la conexión sigue**.

Qué consigue: los 5 caracteres de SHA-1 de cada contraseña que se comprueba (en «Salud», de todas las de la bóveda, una tras otra) junto con la IP de la víctima, y el control de la respuesta: puede decir «✓ No aparece en filtraciones conocidas» de una contraseña filtrada, o lo contrario. No ve nada más: la petición solo lleva el prefijo (`net/PwnedPasswords.kt:31-32`). Por eso la confidencialidad y la integridad son bajas y la complejidad alta (posición de intermediario, una clave de eMudhra y un segundo intento). Los tests no lo detectan porque `AcceptAll` (`app/src/test/java/app/seguridad/net/DistrustedCertificatesTest.kt:21-25`) no imita la caché del sistema.

**Corrección en Kotlin.** No cambia ningún formato, así que no hace falta migración. Dos capas:

1. **Mínima (mover una línea en cada una de las tres sobrecargas):** comprobar la cadena presentada **antes** de dársela al sistema. Así el sistema nunca valida (ni guarda en su caché) una cadena con una clave comprometida a través de este cliente.
2. **Completa:** comprobar también, **después**, la cadena que el sistema ha validado, con `android.net.http.X509TrustManagerExtensions` (existe desde la API 17 y admite el `RootTrustManager` de Network Security Config desde la API 24; `minSdk` es 26). Como esa clase no existe en la JVM de `jvm-check`, se inyecta desde Android.

```kotlin
// net/DistrustedCertificates.kt

/** La cadena que el sistema validó y aceptó; [host] es el nombre del servidor, o null si no se conoce. */
typealias TrustedChain = (chain: Array<out X509Certificate>, authType: String, host: String?) -> List<X509Certificate>

class DistrustingTrustManager(
    private val base: X509TrustManager,
    // En la JVM de los tests, la presentada (la JVM no tiene caché de intermedios). En Android, la de
    // X509TrustManagerExtensions, que incluye los intermedios que Conscrypt saca de su caché.
    private val trustedChain: TrustedChain = { chain, _, _ -> chain.toList() },
) : X509ExtendedTrustManager() {
    private fun refuse(chain: List<X509Certificate>) {
        chain.firstOrNull { it.isDistrusted() }?.let {
            throw CertificateException("Distrusted certificate authority (eMudhra key compromise): ${it.subjectX500Principal.name}")
        }
    }

    // 1. Antes que el sistema: nunca valida (ni guarda en su caché) una cadena con una clave comprometida.
    // 2. Después, sobre la cadena que construyó el sistema, que puede llevar intermedios que el servidor no envió.
    private inline fun checked(chain: Array<out X509Certificate>, authType: String, host: String?, system: () -> Unit) {
        refuse(chain.asList())
        system()
        refuse(trustedChain(chain, authType, host))
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) =
        checked(chain, authType, null) { base.checkServerTrusted(chain, authType) }

    override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String, socket: Socket?) =
        checked(chain, authType, (socket as? SSLSocket)?.handshakeSession?.peerHost) {
            (base as? X509ExtendedTrustManager)?.checkServerTrusted(chain, authType, socket) ?: base.checkServerTrusted(chain, authType)
        }

    override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String, engine: SSLEngine?) =
        checked(chain, authType, engine?.handshakeSession?.peerHost) {
            (base as? X509ExtendedTrustManager)?.checkServerTrusted(chain, authType, engine) ?: base.checkServerTrusted(chain, authType)
        }

    // checkClientTrusted y getAcceptedIssuers, como ahora.
}

fun OkHttpClient.Builder.distrustCompromisedCas(trustedChainOf: ((X509TrustManager) -> TrustedChain)? = null): OkHttpClient.Builder {
    val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(null as KeyStore?) }
    val system = factory.trustManagers.filterIsInstance<X509TrustManager>().single()
    val trust = trustedChainOf?.let { DistrustingTrustManager(system, it(system)) } ?: DistrustingTrustManager(system)
    val tls = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), null) }
    return sslSocketFactory(tls.socketFactory, trust)
}
```

```kotlin
// android/TrustedChain.kt (solo Android) y AppContainer: secureClient(appVersion) { androidTrustedChain(it) }
fun androidTrustedChain(system: X509TrustManager): TrustedChain {
    val ext = X509TrustManagerExtensions(system)
    return { chain, authType, host ->
        // Sin nombre de servidor no se puede pedir la cadena validada: se rechaza (falla cerrado).
        val name = host ?: throw CertificateException("Sin nombre de servidor")
        @Suppress("UNCHECKED_CAST")
        ext.checkServerTrusted(chain as Array<X509Certificate>, authType, name)
    }
}
```

Capa opcional, barata y comprobable en la JVM: añadir a la comparación por nombre los **sujetos de los ocho intermedios** como emisor (`CN=EM DV TLS CA - G3B, O=eMudhra BV`, etc.). Con `pathlen:0`, toda hoja que firme un intermedio comprometido lo lleva como emisor aunque el servidor no lo mande.

Tests nuevos (JVM, en `DistrustedCertificatesTest`):

```kotlin
@Test fun `a chain completed from the system's intermediate cache is refused`() {
    // Como Conscrypt con su caché: el servidor manda algo sin eMudhra y el sistema completa con el intermedio.
    val tm = DistrustingTrustManager(AcceptAll) { chain, _, _ -> chain.toList() + compromised + aaaRoot }
    assertThrows(CertificateException::class.java) { tm.checkServerTrusted(arrayOf(aaaRoot), "ECDHE_ECDSA") }
}

@Test fun `the system never validates a chain with a compromised key`() {
    var systemCalled = false
    val spy = object : X509TrustManager by AcceptAll {
        override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) { systemCalled = true }
    }
    assertThrows(CertificateException::class.java) { DistrustingTrustManager(spy).checkServerTrusted(arrayOf(compromised, aaaRoot), "ECDHE_ECDSA") }
    assertFalse(systemCalled)
}
```

**Prueba dinámica (solo con tu build de depuración, en un laboratorio):** una CA raíz de laboratorio en `debug-overrides` de Network Security Config, un intermedio de laboratorio cuya SPKI se añade a la lista solo en el *source set* de depuración, y un proxy que en la primera conexión sirve `[hoja, intermedio]` y en la segunda `[hoja]`. Antes del arreglo la segunda conexión se acepta; después, las dos se rechazan.

**Respecto a la auditoría anterior:** **nuevo**. La auditoría de la 1.3.0 da la red por buena «con el bloqueo de eMudhra encima de la confianza del sistema» (`docs/auditorias/2026-10-31-auditoria-1.3.0.md:570`), igual que la externa de la 1.2.0 (`docs/auditorias/2026-10-27-auditoria-externa-1.2.0.md:109`); ninguna menciona la caché de intermedios de Conscrypt.

---

### CRY-B02. TOTP: un algoritmo, un número de dígitos o un periodo no admitidos se sustituyen en silencio por los de por defecto

- **Dónde:** `app/src/main/java/app/seguridad/crypto/Totp.kt:65-69`, `:87-89` y `:131-132` (y el comentario de `:59`).

  ```kotlin
  private fun hmacName(algorithm: String) = when (algorithm.uppercase()) {
      "SHA256" -> "HmacSHA256"
      "SHA512" -> "HmacSHA512"
      else -> "HmacSHA1"
  }
  ```

  ```kotlin
      val step = s.period.coerceIn(10, 120)
      try {
          return hotp(key, seconds / step, s.digits.coerceIn(6, 8), s.algorithm) to (step - (seconds % step).toInt())
  ```

  ```kotlin
              digits = query["digits"]?.toIntOrNull()?.takeIf { it in 6..8 } ?: 6,
              period = query["period"]?.toIntOrNull()?.takeIf { it in 10..120 } ?: 30,
  ```

  ```kotlin
  /** Shortest TOTP key accepted (80 bits, what RFC 4226 allows as a minimum); shorter ones break the HMAC. */
  ```

- **Estado:** Confirmado.
- **Severidad:** Informativa, CVSS 3.1 **0.0**, `CVSS:3.1/AV:N/AC:H/PR:N/UI:R/S:U/C:N/I:N/A:N`.
- **Control MASVS:** MASVS-CRYPTO-1 (uso correcto de primitivas); MASVS-CODE-4 (validación de entrada no fiable).

**Por qué es un problema en esta app.** Un QR `otpauth://` con `digits=10` o `period=300` se acepta con 6 dígitos y 30 s, y la app enseña códigos que el servicio nunca aceptará, sin decir por qué. `hmacName` convierte cualquier nombre desconocido en HMAC-SHA1, y `totpCode` recorta en silencio lo guardado. Hoy ningún camino de escritura produce esos valores (el editor ofrece solo `SHA1`/`SHA256`/`SHA512` y 6 u 8 dígitos, `ui/screens/VaultEntryScreen.kt:439-440`; `parseOtpAuth` y `parseOtpMigration` filtran el algoritmo), así que el único efecto real es un código erróneo con un QR raro, que el usuario detecta al dar de alta el 2FA. No es una degradación de seguridad: HMAC-SHA1 sigue siendo adecuado para HOTP y TOTP (RFC 6238). El comentario de `:59` atribuye a RFC 4226 un mínimo de 80 bits, pero RFC 4226 §4, R6, dice «at least 128 bits» y recomienda 160; aceptar 80 bits está bien por compatibilidad (muchos servicios dan claves de 16 caracteres Base32), y HMAC no «se rompe» con claves más cortas, solo se debilita.

**Corrección en Kotlin.** No cambia el formato de la bóveda; lo guardado se sigue leyendo igual.

```kotlin
private fun hmacName(algorithm: String): String? = when (algorithm.uppercase()) {
    "SHA1" -> "HmacSHA1"
    "SHA256" -> "HmacSHA256"
    "SHA512" -> "HmacSHA512"
    else -> null
}

/** Si el código usa un algoritmo, unos dígitos o un periodo que esta app sabe calcular. */
fun TotpSecret.isSupported(): Boolean = hmacName(algorithm) != null && digits in 6..8 && period in 10..120

fun totpCode(s: TotpSecret, nowMillis: Long): Pair<String, Int>? {
    if (!s.isSupported()) return null // La pantalla dice por qué, en vez de enseñar un código que no vale.
    val key = totpKey(s.secret) ?: return null
    ...
}

// parseOtpAuth: el valor por defecto solo cuando el parámetro falta; un valor presente y no admitido rechaza el enlace.
digits = query["digits"]?.let { it.toIntOrNull()?.takeIf { d -> d in 6..8 } ?: return null } ?: 6,
period = query["period"]?.let { it.toIntOrNull()?.takeIf { p -> p in 10..120 } ?: return null } ?: 30,

/** Shortest TOTP key accepted: 80 bits, below the 128 of RFC 4226 (R6) but what many services issue (16 Base32 characters). */
const val TOTP_MIN_KEY_BYTES = 10
```

Tests: `parseOtpAuth` con `digits=10`, `period=5` y `period=300` da `null`; `totpCode` con `algorithm = "MD5"` da `null`; los vectores de RFC 6238 siguen pasando.

**Plan de migración:** ninguno de formato. Una entrada guardada con valores fuera de rango, si existiera, deja de enseñar un código falso y enseña el aviso (texto en «Textos para el Abogado Digital»).

**Respecto a la auditoría anterior:** **nuevo**. Ni la auditoría de la 1.3.0 ni la externa de la 1.2.0 tratan los parámetros de TOTP; el único cambio previo en `Totp.kt` fue no lanzar con escapes `%` mal formados (`docs/superpowers/specs/2026-10-23-seguridad-design.md:244`).

---

## Flujo de datos revisado

| Dato | Nace | Se guarda | Se protege | Se usa | Si falla |
|---|---|---|---|---|---|
| Semilla de identidad (32 B) | `SecureRandom` (`crypto/pk/Identity.kt:96`, `vault/Contacts.kt:59-73`) | Base64 en `VaultEntry.identitySeed` (`vault/Vault.kt:63-69`), dentro de la bóveda cifrada (parte A) | Nunca en un campo de clase; el array se pone a cero (`Contacts.kt:48-52`, `:70-72`) | HKDF-SHA256 con `info` distinto para Ed25519 y X-Wing (`Identity.kt:55`, `:58`, `:163-174`); `Identity` se abre y se cierra en cada operación (`vault/PkActions.kt:369-373`) | `CryptoFailure` con semilla mal formada (`Contacts.kt:43-47`, `Identity.kt:54`) |
| Tarjeta | `cardBytes` firma `labeled("seguridad-card-v1") ‖ cuerpo` (`Card.kt:78-89`) | Bytes canónicos en el contacto, releídos con `parseCard` (`Contacts.kt:98-126`) | Firma, NFC, lista fija de caracteres, módulo de ML-KEM (BC, comprobado con `javap`), X25519 de orden pequeño (`Card.kt:97-135`) | Huella SHA-512 con etiqueta, 6×5 cifras, unos 100 bits (`Identity.kt:137-154`) | Se rechaza entera; `SharedKem` sin botón de guardar, `KeysChanged` solo como contacto nuevo (`Contacts.kt:208-226`) |
| Mensaje `SGM1:` | `encryptMessage`: semilla efímera de 64 B (`Message.kt:100-107`) | No se guarda | HPKE base X-Wing/HKDF-SHA256/ChaCha20-Poly1305, AAD = cabecera con `enc` (`Message.kt:126-143`); firma sobre cabecera ‖ Ed25519 ‖ X-Wing del destinatario ‖ mensaje (`:138-140`) | `decryptMessage`, como mucho 8 aperturas (`:188-212`) | Firma inválida rechaza todo (`:230`); remitente solo por la Ed25519 guardada (`Contacts.kt:154-164`) |
| Archivo tipo 3 | Clave = `export("seguridad-file-key-v1", 32)` (`FileMessage.kt:150`) | No se guarda; se pone a cero (`:193`, `:305`) | STREAM ChaCha20-Poly1305, nonce prefijo ‖ contador ‖ último, AAD = cabecera (`:171-190`, `:326-342`); contador que no da la vuelta (`:187`, `:276`) | Ed25519ph con contexto propio (`:154-169`, `:258-266`, `:294`) | Se escribe en una carpeta propia de la caché y se borra en cualquier fallo (`vault/PkActions.kt:559-574`) |
| Firmas `SGS1:` y `.sgsig` | `signText`, `signFile` (`Signatures.kt:74-88`, `:166-181`) | Públicas | Etiqueta o contexto propios; texto normalizado; nada detrás de la firma (`:145-159`); 101 bytes exactos (`:190-192`) | `verifySignedText`, `verifyFileSignature` (`:116-138`, `:190-209`) | `CryptoFailure`; el booleano se usa (`:133`, `:206-207`) |
| Contraseñas, frases, PIN, códigos, respuestas | `secureRandom` con `nextInt(bound)` | En una nota de la bóveda si el usuario la guarda (`RecoveryCodes.kt:30-31`, `InventedAnswers.kt:34-42`) | Sin sesgo de módulo; rechazo de PIN débiles con muestreo por rechazo (`Pin.kt:140-152`) | Pantalla y portapapeles sensible | `CodesSession.wipe/clear` sueltan las referencias (`CodesSession.kt:277-295`) |
| Secreto TOTP | QR, exportación de Google o teclado | `TotpSecret.secret` (Base32) en la bóveda | Clave decodificada y puesta a cero en cada código (`Totp.kt:85-92`) | HOTP de RFC 4226 (`:72-81`) | Ver CRY-B02 |
| Contraseña a comprobar en HIBP | Pantalla o «Salud» | No | SHA-1 en un `CharArray` que se borra; solo salen 5 caracteres (`Hashes.kt:16-34`, `PwnedPasswords.kt:28-44`) | TLS con desconfianza de eMudhra, sin caché, cookies ni redirecciones (`HttpClient.kt:12-27`) | Ver CRY-B01 |

## Observaciones sin hallazgo

- `passwordEntropy` sobrestima un poco (`Generator.kt:41-46`, ya lo dice su comentario). Con 8 caracteres y los cuatro tipos, la entropía de Shannon de `generatePassword` es de unos 50,7 bits frente a los 51,8 que se enseñan; la etiqueta («Aceptable») no cambia.
- La lista de la EFF tiene 4 palabras con guion (`drop-down`, `felt-tip`, `t-shirt`, `yo-yo`). Con el separador «-» la frase se parte de forma ambigua; la pérdida de entropía es despreciable (haría falta que las dos mitades fueran también palabras de la lista).
- Las listas de palabras están completas y sin repeticiones: 2.048 en inglés, 2.048 en español (también sin tildes, comprobado) y 7.776 de la EFF.
- `TotpSecret` y `VaultEntry` son `data class` sin `toString` propio, así que lo imprimirían todo. No se ha encontrado ningún registro, `println` ni plantilla que los imprima. Por defensa en profundidad, se podría redactar `toString`, como ya hacen `OpenedMessage`, `SignedText` o `PkInputs`.
- Los objetos de clave de Bouncy Castle (`Ed25519PrivateKeyParameters`, `XWingPrivateKeyParameters`, `HPKEContext`, el HMAC de HKDF) no se pueden poner a cero. Está documentado en `Identity.kt:33-38` y `:156-162`.
- La firma del tipo 3 no delimita el registro 0 de los datos; no hay frescura en lo firmado; queda un temporal en claro en la caché mientras está abierto el selector. Los tres están aceptados como «Informativa» en el diseño (`2026-10-26-seguridad-1.2.0-clave-publica-design.md`, «Revisión final de la 1.2.0»): no se repiten como hallazgos.

## Checklist de la fase 4 (parte B)

| Punto | Estado | Evidencia |
|---|---|---|
| Claves en Android Keystore, no en código, recursos, prefs ni ficheros | OK (por diseño, fuera del Keystore) | La semilla de identidad vive solo dentro de la bóveda cifrada: `vault/Vault.kt:63-69`, `vault/Contacts.kt:59-73`. Nada en `BuildConfig`, recursos ni prefs. El Keystore es de la parte A. |
| `setIsStrongBoxBacked(true)` con caída controlada | OK (lógica) | `tools/KeyPlacement.kt:16-24` (StrongBox solo con API 28+ y sin fallo previo; una sola caída al TEE), `:52-55`. Los parámetros de la clave, en la parte A. |
| `setUserAuthenticationRequired` / `setInvalidatedByBiometricEnrollment` | No aplica (parte A) | La parte B no crea claves del Keystore. |
| Propósito mínimo y `setRandomizedEncryptionRequired` | No aplica (parte A) | Ídem. |
| Claves de BC fuera del Keystore envueltas antes de guardarse | OK | La semilla se guarda dentro de la bóveda cifrada con la DEK (`vault/Vault.kt:63-69`, `:105-108`); las claves privadas se derivan en cada uso y no se guardan (`vault/PkActions.kt:369-373`, `Contacts.kt:21`). |
| Rotación y versión de clave junto al cifrado | OK | Cabecera `SG ‖ 0x02 ‖ tipo ‖ suite` en todo dato (`crypto/pk/PkFormat.kt:13-16`, `:44`, `:51-57`); «Renovar identidad» (`Contacts.kt:82-90`); suite propia por si cambia el borrador de X-Wing (diseño, «Enfoques»). |
| Prohibidos: ECB, DES, 3DES, RC4, Blowfish, MD5, SHA-1 para integridad o firmas, RSA PKCS#1 v1.5 | OK | SHA-1 solo donde lo exige el protocolo: HIBP (`crypto/Hashes.kt:19`) y HMAC-SHA1 de TOTP (`crypto/Totp.kt:65-69`, RFC 6238). Nada de RSA. MD5 de Google se rechaza (`crypto/OtpMigration.kt:145`). |
| Preferidos (AEAD, Ed25519, acuerdo moderno) | OK | HPKE X-Wing + HKDF-SHA256 + ChaCha20-Poly1305 (`PkFormat.kt:63`); Ed25519 y Ed25519ph (`Identity.kt:67-80`). |
| CBC sin MAC | No aplica | No hay CBC en la parte B. |
| Nonce único por clave | OK | HPKE gestiona su nonce (un solo `seal` por contexto, `Message.kt:142`); el archivo usa una clave nueva por archivo (`FileMessage.kt:150`) con nonce STREAM prefijo ‖ contador ‖ último (`crypto/FileCrypto.kt:21-22`) y el contador no da la vuelta (`FileMessage.kt:187`, `:276`). |
| IV generado por el Keystore | No aplica | La parte B no cifra con el Keystore. |
| Tag de 128 bits y AAD con contexto | OK | Tag de 16 B (`crypto/Crypto.kt:205`, `:318-326`); AAD = cabecera completa con tipo, suite, `enc` y prefijo (`Message.kt:142`, `FileMessage.kt:328`, `:339`); `info` distinto para mensaje y archivo (`Message.kt:46`, `FileMessage.kt:54`). |
| Fallo de autenticación tratado como manipulación | OK | `Message.kt:204-213` (nunca un texto sin comprobar), `FileMessage.kt:245`, `:288`, `:337-342`; el archivo a medias se borra (`vault/PkActions.kt:570-573`). |
| Solo `SecureRandom()` sin semilla | OK | `crypto/Crypto.kt:207-209`; ningún `java.util.Random`, `kotlin.random`, `Math.random`, `setSeed` ni `SHA1PRNG` en `app/src/main/java` (búsqueda). |
| Sin sesgo de módulo en los generadores | OK | `nextInt(bound)` en `Generator.kt:34`, `:37`, `:52`, `:56`; `shuffled(random)` de `java.util.Random` en `:36`; `Pin.kt:145` con rechazo de débiles; `RecoveryCodes.kt:17`; `InventedAnswers.kt:23`. |
| Derivación desde contraseña o PIN (Argon2id, PBKDF2) | No aplica (parte A) | La parte B no deriva de contraseñas. HKDF-SHA256 desde una semilla de 256 bits es lo correcto (`Identity.kt:163-174`, RFC 5869). |
| Sal aleatoria de 16 B o más | No aplica | HKDF con sal vacía sobre material uniforme de 32 B (`Identity.kt:167`); lo permite RFC 5869 §3.1. |
| PIN corto con Keystore y límite de intentos | No aplica | El PIN de «PIN y códigos» es para otros servicios; no desbloquea nada en la app. |
| Booleano de `verify()` comprobado | OK | `Card.kt:109-111`, `Message.kt:230`, `Signatures.kt:133`, `:206-207`, `FileMessage.kt:294`. |
| Clave que firma ligada a la identidad mostrada | OK | El remitente sale de la Ed25519 guardada del contacto, nunca del nombre de una tarjeta (`vault/Contacts.kt:154-164`, `vault/PkActions.kt:118-138`, `:362-365`); aviso de varias tarjetas con la misma clave (`PkActions.kt:122`). |
| No aceptar firmas sobre otra cosa | OK | Etiquetas con `0x00` en Ed25519 puro y contextos en Ed25519ph, cada una en un solo formato (`PkFormat.kt:70-80`, `Card.kt:38`, `Message.kt:47`, `Signatures.kt:38-39`, `FileMessage.kt:56`); la firma del mensaje y del archivo cubre la cabecera y las dos claves del destinatario (`Message.kt:138`, `FileMessage.kt:162-169`). |
| Acuerdo de claves: curva y clave pública validadas, KDF sobre el secreto | OK | X25519 de orden pequeño rechazado (`Card.kt:131-133`); ML-KEM con comprobación de módulo de FIPS 203 §7.2 en Bouncy Castle 1.86 (`Card.kt:123-127`; `MLKEMPublicKeyParameters` lanza «Modulus check failed», comprobado con `javap`); Ed25519 decodificado y de orden pequeño rechazado (`Identity.kt:122-129`, test `IdentityTest.claveDeOrdenPequenoRechazada`); KDF de HPKE (HKDF-SHA256). |
| Comparaciones en tiempo constante | OK / No aplica | La parte B no compara secretos. Las claves públicas se comparan con `contentEquals` (`Contacts.kt:152-156`). `sameHash` (`Hashes.kt:54-56`) compara una huella publicada, y `pwnedCount` (`Pwned.kt:11`) un sufijo en local, sin oráculo. TOTP solo genera códigos, no los verifica. |
| Proveedor BC no pedido por nombre `"BC"` | OK | Solo la API ligera de BC (imports de `Identity.kt:7-15`, `PkFormat.kt:63`); ningún `getInstance(..., "BC")` en `app/src/main/java`. |
| `bcprov` actual | OK (lo verifica `revisor-dependencias`) | `gradle/libs.versions.toml:14` (`1.86`). |
| No mezclar claves del Keystore con BC | OK | La parte B no usa el Keystore. |
| Higiene de memoria | OK, con los límites documentados | `fill(0)` en semillas, claves derivadas, textos en claro y firmas: `Identity.kt:172`, `Contacts.kt:51`, `:71`, `Message.kt:105`, `:145-147`, `:240-242`, `FileMessage.kt:106`, `:193-198`, `:297`, `:305-306`, `:361`, `Hashes.kt:21`, `:32`, `PwnedPasswords.kt:44`, `OtpMigration.kt:161`, `:213`. Los `String` de Compose y los objetos de BC no se pueden borrar (documentado en `CodesSession.kt:71`, `CipherSessions.kt:18-19`, `Identity.kt:33-38`). |
| Red: solo lo previsto, TLS sin debilitar, sin `TrustManager` ni `HostnameVerifier` permisivos | **Hallazgo (CRY-B01)** | Solo sale el prefijo de 5 caracteres con `Add-Padding` (`PwnedPasswords.kt:31-32`); sin caché, cookies ni redirecciones (`HttpClient.kt:19-23`); verificador de nombres por defecto de OkHttp; el `TrustManager` nunca es más débil que el del sistema (`DistrustedCertificates.kt:83-96`), pero la desconfianza de eMudhra se salta (CRY-B01). |
| TOTP: manejo del secreto y parámetros | **Hallazgo (CRY-B02)** | El secreto se decodifica, se usa y se borra (`Totp.kt:85-92`); clave mínima de 80 bits (`:60`, `:63`); los parámetros no admitidos se sustituyen en silencio (CRY-B02). |
| Errores de composición: misma clave para cifrar y firmar | OK | Ed25519 y X-Wing salen de la semilla con `info` distinto (`Identity.kt:18-19`, `:55`, `:58`). |
| Errores de composición: dato cifrado sin versión | OK | `PkFormat.kt:44`, `:51-57`; tests de vectores que se leen para siempre (`app/src/test/resources/vectors/seguridad-pk.json`). |
| Errores de composición: caída silenciosa a algo más débil | OK | Una firma inválida nunca se enseña como «sin firmar» (`Message.kt:230`, `FileMessage.kt:294`); sin identidad, el interruptor «Firmar» se apaga y lo dice (`ui/screens/PkActionsScreen.kt:386-392`). La caída de TOTP a SHA-1 no debilita nada (CRY-B02). |
| Errores de composición: IV que se reinicia tras reinstalar | No aplica | Ni contadores persistentes ni IV guardados en la parte B: clave HPKE nueva por mensaje y por archivo. |
| Errores de composición: almacenamiento en claro si el Keystore falla | No aplica (parte A) | `KeyPlacement.kt:139-147` borra la clave si falla la entrega. |
| Errores de composición: claves BC guardadas sin envolver | OK | Ver «Claves de BC fuera del Keystore». |

## Comparación con la auditoría de la 1.3.0

Leída después de escribir los hallazgos de arriba (`docs/auditorias/2026-10-31-auditoria-1.3.0.md`, y también la externa de la 1.2.0).

| Punto de este informe | Estado | Referencia |
|---|---|---|
| CRY-B01, desconfianza de eMudhra saltada por la caché de intermedios | **Nuevo** | La 1.3.0 lo da por bueno: §6, «Red» (`:570`); la externa de la 1.2.0, `:109`. |
| CRY-B02, parámetros de TOTP sustituidos en silencio | **Nuevo** | Sin mención. |
| Observación: objetos de Bouncy Castle y `String` de Compose que no se pueden borrar | Ya conocido | CIFRADO-1 (§3.2, lote F5) y §5.2, «Las contraseñas como `String` en el estado de Compose». |
| Observación: registro 0 sin delimitar en la firma del tipo 3, sin frescura, temporal en claro con el selector abierto | Ya conocido | Diseño de la 1.2.0, «Revisión final» (Informativas); el temporal también en la externa de la 1.2.0, punto 9 (`:58`). |
| Observación: lista de la EFF (palabras con guion) | Nuevo, sin hallazgo | LIBRE-3 trata solo la licencia y la columna de los dados. |
| Observación: `toString` de `TotpSecret` y `VaultEntry` | Nuevo, sin hallazgo | Sin mención. |
| Clave pública: lectura estricta, separación de dominios, «verificado» solo por QR o confirmación | Coincide (OK) | §6, «Clave pública». |
| Aleatoriedad sin sesgo y sin logs | Coincide (OK) | §6, «Sin logs». |

## Revisión experto-seguridad-cripto

Recorrido con `references/cripto-moderna.md` como lista de comprobación, y con el modelo de amenaza de arriba.

- **AEAD y nonces.** HPKE (RFC 9180) en modo base con X-Wing, HKDF-SHA256 y ChaCha20-Poly1305, un `seal` por contexto (`crypto/pk/PkFormat.kt:63`, `crypto/pk/Message.kt:126-143`). En el archivo, una clave nueva exportada por archivo (`crypto/pk/FileMessage.kt:150`) y el nonce STREAM con contador que no da la vuelta (`FileMessage.kt:187`, `:276`; `crypto/FileCrypto.kt:21-22`). La cabecera entera es la AAD (`Message.kt:142`, `FileMessage.kt:328`). Correcto.
- **Firmas.** Ed25519 puro con etiqueta y `0x00`, Ed25519ph con contexto (RFC 8032 §5.1), cada etiqueta en un solo formato (`PkFormat.kt:70-80`). La misma clave en Ed25519 y Ed25519ph no permite confusión gracias a `dom2` de RFC 8032. Firma dentro del cifrado sobre la cabecera y las dos claves del destinatario: sin reenvío subrepticio ni unknown key-share (`Message.kt:138`, `FileMessage.kt:162-169`; Davis, USENIX 2001). El booleano se usa siempre (`Card.kt:109-111`, `Message.kt:230`, `Signatures.kt:133`, `:206-207`, `FileMessage.kt:294`).
- **Claves recibidas.** X25519 de orden pequeño rechazado con RFC 7748 §6.1 (`Card.kt:131-133`); ML-KEM con la comprobación de módulo de FIPS 203 §7.2, comprobada en el *bytecode* de Bouncy Castle 1.86 (`MLKEMPublicKeyParameters`: «Modulus check failed for ML-KEM public key»); Ed25519 decodificado y de orden pequeño rechazado (`Identity.kt:122-129`). Una codificación no canónica de la parte X25519 no da nada a un atacante: el combinador de X-Wing usa los bytes tal cual, así que ni él ni la dueña de la clave abren el mensaje.
- **Derivación.** HKDF-SHA256 con `info` por clave sobre una semilla de 256 bits del CSPRNG (`Identity.kt:55`, `:58`, `:163-174`; RFC 5869). Es el uso correcto de HKDF (material de alta entropía, no una contraseña).
- **Huella.** SHA-512 con etiqueta, 6 grupos de 5 cifras, unos 99,7 bits; sesgo del módulo de 10⁻⁷ (`Identity.kt:137-154`). Una segunda preimagen cuesta unas 2⁹⁹ operaciones.
- **Aleatoriedad.** Solo `SecureRandom()` (`crypto/Crypto.kt:207-209`) y `nextInt(bound)`, que rechaza internamente (`Generator.kt:34-37`, `Pin.kt:145`, `RecoveryCodes.kt:17`, `InventedAnswers.kt:23`). Entropías comprobadas: código de recuperación 50 bits, respuesta 44 bits (las 2.048 palabras en español siguen siendo distintas sin tildes), frase EFF 12,9 bits por palabra.
- **Comparaciones.** No hay secretos comparados en la parte B; lo que se compara son claves públicas o huellas publicadas (`vault/Contacts.kt:152-156`, `crypto/Hashes.kt:54-56`).
- **TLS.** Hallazgo CRY-B01: la política extra se aplica a la cadena presentada y no a la validada (`net/DistrustedCertificates.kt:83-96`). Lo demás está bien: solo el prefijo de 5 caracteres con `Add-Padding` (`net/PwnedPasswords.kt:31-32`), sin redirecciones, caché ni cookies (`net/HttpClient.kt:19-23`), y el verificador de nombres por defecto de OkHttp.
- **Severidad.** CRY-B01 queda en Media (4.2) porque hace falta ser intermediario, tener una clave de eMudhra y que la víctima reintente, y solo se pierde el prefijo de HIBP y la veracidad de su respuesta. CRY-B02 es Informativa: no hay ataque, solo códigos erróneos con un QR raro.

## Revisión Ingeniero Móvil Senior

- **Plataforma TLS (CRY-B01).** En Android, `TrustManagerFactory.init(null)` da el `RootTrustManager` de Network Security Config, que delega en `com.android.org.conscrypt.TrustManagerImpl`, y esa clase guarda en una caché de proceso los intermedios de las cadenas que valida (Conscrypt, `TrustManagerImpl.java:643-660`, `:738-743`). La JVM de `jvm-check` no la tiene, y por eso los tests con `AcceptAll` (`app/src/test/java/app/seguridad/net/DistrustedCertificatesTest.kt:21-25`) no podían verlo. La corrección usa `X509TrustManagerExtensions` sobre el gestor **del sistema** (no sobre el envoltorio), que admite el `RootTrustManager` desde la API 24 (`minSdk = 26`, `app/build.gradle.kts:31`). Se inyecta desde Android para que `net/` siga compilando en la JVM. El cliente es único y perezoso (`AppContainer.kt:99`, `:245`), así que la caché vive lo que vive el proceso.
- **Network Security Config.** No hay `networkSecurityConfig` en el manifiesto (`app/src/main/AndroidManifest.xml:9-17`). Con `targetSdk = 36` (`app/build.gradle.kts:32`), el tráfico en claro está prohibido y no se confía en las CA del usuario. En Android 8.0 y 8.1 el tráfico en claro está permitido por defecto, pero la URL es fija en `https` y no se siguen redirecciones (`net/HttpClient.kt:22-23`, `net/PwnedPasswords.kt:24`).
- **Ciclo de vida y estado.** Los secretos de «PIN y códigos» viven en un ViewModel (`ui/screens/CodesScreen.kt:129-134`) y se sueltan con `wipe` y `clear` (`tools/codes/CodesSession.kt:277-295`); los de «Cifrar texto», «Cifrar archivos» y «¿Filtrada?», en `GuardedSession` (`tools/cipher/CipherSessions.kt:21-40`). El texto en claro de «Cifrar para…» está en `remember`, no en `rememberSaveable` (`ui/screens/PkActionsScreen.kt:265-266`).
- **Concurrencia.** HPKE, Ed25519 y el descifrado de archivos corren en `Dispatchers.Default` (`ui/screens/PkActionsScreen.kt:299`, `:421`, `:478`, `:549`) y el borrado de temporales en `Dispatchers.IO` (`:578`). `Identity` es `@Volatile` y se cierra en un `finally` (`crypto/pk/Identity.kt:40-42`, `vault/PkActions.kt:369-373`). `hpkeSuite()` crea una instancia por llamada (`PkFormat.kt:63`).
- **Archivos.** El archivo descifrado va a una subcarpeta propia de `cacheDir/pk-open` y se borra en cualquier fallo o cancelación (`vault/PkActions.kt:559-574`); la carpeta entera, al arrancar y al bloquear (`:595-603`).
- **Componentes.** El escáner de QR es una actividad propia no exportada con `FLAG_SECURE` (`app/src/main/AndroidManifest.xml:28-35`), y solo el escaneo con la cámara marca un contacto como verificado. Lo que llega desde el inspector de QR, no (`ui/screens/ContactAddScreen.kt:104-106`).
- **Memoria.** Es higiene, con los límites de Compose y de Bouncy Castle ya documentados (`Identity.kt:33-38`, `:156-162`): `fill(0)` en semillas, claves y textos en claro (ver la fila «Higiene de memoria» del checklist).

## Textos para el Abogado Digital (revisión final)

Solo si se aplica la corrección de CRY-B02 (CRY-B01 no añade ningún texto: el fallo sigue siendo «No se pudo comprobar. Revisa la conexión.»):

1. Al leer un QR o un enlace `otpauth://` con un número de dígitos o un periodo que la app no admite (hoy sale «El QR o enlace no es un código 2FA (otpauth://totp/…)», `ui/screens/VaultEntryScreen.kt:245`): «Este código 2FA usa un número de dígitos o un periodo que Clavero no admite.»
2. En la lista y en la ficha de una entrada guardada con un ajuste no admitido, en lugar del código: «Clavero no puede calcular este código: usa un ajuste que no admite.»
