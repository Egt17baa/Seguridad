# Fase 4, criptografía. Parte A: bóveda, huella, cifrado de textos y archivos, y copias

- **Commit revisado:** `b08d2ec` (Clavero 1.3.0, publicada).
- **Fecha:** 1-11-2026.
- **Revisor:** parte A. La parte B (clave pública, herramientas y HIBP) la hace otro revisor y no se repite aquí.

## Alcance y modelo de amenaza

**Ficheros principales:**
- `crypto/Crypto.kt`
- `crypto/FileCrypto.kt`
- `android/BiometricVault.kt`
- `vault/Vault.kt`
- `vault/Backup.kt`
- `vault/Attachments.kt`
- `vault/Contacts.kt`

**Ficheros de apoyo:** `vault/VaultStore.kt`, `AppContainer.kt`, `vault/MasterChanged.kt`, `core/Streams.kt`, `settings/Settings.kt` y `android/Clipboard.kt`.

He seguido el flujo de los datos hasta donde hacía falta: `ui/screens/VaultScreen.kt` y `ui/screens/VaultBackup.kt` (de donde sale la contraseña maestra y dónde se activa o desactiva la huella), `ui/screens/SecurityToolsScreen.kt` (quién llama a `encryptStream` y `decryptStream`), `tools/KeyPlacement.kt`, `core/AtomicFile.kt`, `core/Json.kt`, `health/Health.kt` y el manifiesto con sus reglas de copia. Todas las rutas de Kotlin son relativas a `app/src/main/java/app/seguridad/`.

**Modelo de amenaza:** el del proyecto (`docs/auditorias/2026-10-31-auditoria-1.3.0.md:28-38`). No son de fiar:
- quien manda contenido (`SG1:`, `.sgenc`, copias `.sgbak`);
- otra app del móvil;
- **quien tiene el móvil o una copia `.sgbak`**.

Root y el volcado de memoria quedan como riesgo residual aceptado (`docs/superpowers/specs/2026-10-23-seguridad-design.md:149,223`).

**Método:**
- Lectura completa de los ficheros, de los tests de formato (`VaultTamperTest`, `LegacyVaultTest`) y de los datos de prueba de `app/src/test/resources/`.
- `javap` sobre `bcprov-jdk18on-1.86.jar`, para confirmar qué copias hace Bouncy Castle de la clave (CRY-A03).
- No he ejecutado Gradle, para no escribir nada en el árbol del repositorio.
- Leí la auditoría anterior solo después de tener escritos los hallazgos.

## Resumen

| ID | Severidad | Hallazgo | Dónde | Estado | ¿Nuevo? |
|---|---|---|---|---|---|
| CRY-A01 | Media (4.7) | Una maestra nueva solo necesita 10 caracteres: se aceptan «contraseña» y «1234567890», aunque las copias salen del móvil y el diseño cuenta con «una maestra fuerte» | `ui/screens/VaultBackup.kt:45,222`, `ui/screens/VaultScreen.kt:254` | Confirmado | Nuevo |
| CRY-A02 | Informativa (0.0) | La DEK que envuelve la huella no lleva AAD ni versión. Si queda la de una bóveda anterior, el desbloqueo con huella dice «Contraseña incorrecta o datos dañados» y no se desactiva solo | `android/BiometricVault.kt:307-313`, `vault/VaultStore.kt:65-68`, `ui/screens/VaultScreen.kt:297-299` | Confirmado | Nuevo |
| CRY-A03 | Baja (1.9) | Copias de las claves en objetos de los proveedores (`SecretKeySpec`, `KeyParameter`, `HKDFParameters`) que nadie borra y que siguen ahí después de `lock()` | `crypto/Crypto.kt:258,271,311,320,384` | Confirmado | Conocido en parte (CIFRADO-1 y §5.2 de la auditoría anterior). Las copias de las claves son nuevas |

**Recuento:** 0 críticas, 0 altas, 1 media, 1 baja y 1 informativa. Ninguna obliga a cambiar un formato.

---

## Hallazgos

### CRY-A01: Una maestra nueva solo necesita 10 caracteres, aunque las copias salen del móvil

- **Dónde:**
  - `ui/screens/VaultBackup.kt:45`
    ```kotlin
    const val MIN_MASTER = 10
    ```
  - `ui/screens/VaultBackup.kt:222`, en `PasswordDialog`, que sirve para cambiar la maestra (`askTwice = true`) y para importar
    ```kotlin
        val ok = a.length >= MIN_MASTER && (!askTwice || a == b)
    ```
  - `ui/screens/VaultScreen.kt:254`, en «Crea tu bóveda»
    ```kotlin
                enabled = !busy && a.length >= MIN_MASTER && a == b && understood,
    ```
  - El diseño, `docs/superpowers/specs/2026-10-23-seguridad-design.md:151`:
    > **Descartado por ahora:** atar `vault.bin` a una clave del Keystore además de la maestra. Impediría atacar una copia del archivo fuera del móvil, pero un fallo del Keystore dejaría la bóveda sin abrir; con una maestra fuerte y Argon2id (64 MiB, 3 pasadas) el riesgo no compensa.
  - La propia app ya sabe medir la fuerza de una contraseña, pero solo la usa en «Salud» (`health/Health.kt:17-18,82-83`):
    ```kotlin
    private val COMMON = setOf(
        "123456", "password", "qwerty", "contraseña", "111111", "admin", "iloveyou", "000000", "abc123", "12345678",
    ```
    ```kotlin
    fun estimateBits(password: String): Double {
        if (password.isEmpty() || password.lowercase(Locale.ROOT) in COMMON) return 0.0
    ```
- **Estado:** Confirmado.
- **Severidad:** Media. `CVSS:3.1/AV:L/AC:H/PR:N/UI:R/S:U/C:H/I:N/A:N` = **4.7**.
- **MASVS:** MASVS-CRYPTO-2 (una clave derivada de un secreto débil) y MASVS-AUTH-2 (autenticación local).
- **Por qué es un problema aquí:**
  - La bóveda y todas sus copias dependen de un solo factor: la maestra pasada por Argon2id (`vault/Vault.kt:257`, con 64 MiB, t = 3 y p = 1 por defecto, `settings/Settings.kt:21`). El diseño renunció a atar `vault.bin` al Keystore porque contaba con una maestra fuerte, pero el código no la exige.
  - Las copias `.sgbak` salen del móvil por diseño: la copia automática va a la carpeta SAF que elige el usuario (`AppContainer.kt:247-261`, `settings/Settings.kt:35-36`), que puede ser Drive o el PC. El modelo de amenaza del proyecto cuenta como atacante a quien tiene una copia `.sgbak`.
  - Con esa copia, el ataque es fuera de línea y sin límite de intentos. Ni el Keystore ni la huella participan.
  - Se aceptan como maestra nueva textos que la propia app califica de débiles:
    - «contraseña» (10 caracteres, está en `COMMON`; `estimateBits` da 0);
    - «1234567890» (una sola secuencia de teclado; da unos 3 bits);
    - «aaaaaaaaaa» (unos 5 bits).
  - Argon2id con 64 MiB frena una GPU, pero no frena un diccionario: con unos cientos de pruebas por segundo, las contraseñas habituales caen en minutos u horas. La pantalla aconseja una frase de 5 o 6 palabras (`ui/screens/VaultScreen.kt:244-248`), pero no lo exige ni avisa.
  - No es una vulnerabilidad del cifrado: es la condición de la que depende todo el diseño. Por eso es Media y no Baja.
- **Corrección (en Kotlin), solo para contraseñas NUEVAS** (crear la bóveda y cambiar la maestra). Nunca para desbloquear ni para importar, que son `PasswordUse.EXISTING`.

  ```kotlin
  // vault/MasterPolicy.kt (nuevo, JVM puro, entra en jvm-check). MIN_MASTER se mueve aquí desde VaultBackup.kt.
  package app.seguridad.vault

  import app.seguridad.health.WEAK_BITS
  import app.seguridad.health.estimateBits

  const val MIN_MASTER = 10

  /** Texto que se enseña cuando la maestra nueva es fácil de adivinar (pasa por el Abogado Digital). */
  const val MASTER_TOO_WEAK =
      "Esta contraseña es fácil de adivinar. Usa una frase de 5 o 6 palabras que solo tú conozcas (el Generador te puede dar una)."

  /**
   * Por qué [candidate] no vale como contraseña maestra NUEVA, o null si vale. Usa el mismo umbral que «Salud»
   * (WEAK_BITS = 50). Solo para crear la bóveda o cambiar la maestra: abrir o importar nunca pasa por aquí, así que
   * ninguna bóveda ni copia existente deja de abrirse.
   */
  fun newMasterProblem(candidate: String): String? = when {
      candidate.length < MIN_MASTER -> "Mínimo $MIN_MASTER caracteres"
      estimateBits(candidate) < WEAK_BITS -> MASTER_TOO_WEAK
      else -> null
  }
  ```

  ```kotlin
  // ui/screens/VaultScreen.kt, CreateVault
  val problem = newMasterProblem(a)
  if (a.isNotEmpty()) problem?.let { Text(it, color = NegativeColor) }
  // …
  Button(enabled = !busy && problem == null && a == b && understood, /* … */)

  // ui/screens/VaultBackup.kt, PasswordDialog: un parámetro explícito, para que importar no se endurezca nunca
  internal fun PasswordDialog(
      title: String,
      confirmLabel: String,
      onDismiss: () -> Unit,
      askTwice: Boolean = false,
      newPassword: Boolean = askTwice, // true solo en «Nueva contraseña maestra»
      warning: String? = null,
      onConfirm: (CharArray) -> Unit,
  ) {
      // …
      val ok = (if (newPassword) newMasterProblem(a) == null else a.length >= MIN_MASTER) && (!askTwice || a == b)
  ```
- **Tests:**
  - `newMasterProblem` rechaza «contraseña», «1234567890» y «aaaaaaaaaa», y acepta una frase EFF de 5 palabras;
  - la importación de los datos de prueba reales (`criptopanel/vault-*.json`, `seguridad/backup-1.1.sgbak`, con la maestra «maestra-de-prueba») sigue funcionando;
  - `SourceTextsTest` sobre el texto nuevo.
- **Límite de la corrección:** `estimateBits` es una estimación aproximada y no conoce palabras de diccionario. «Barcelona1» da unos 59 bits y pasaría. La corrección quita los casos más evidentes, pero no garantiza una maestra fuerte. Hay dos opciones para ir más allá:
  - un estimador con diccionario, como `zxcvbn4j` (MIT, compatible con Apache-2.0, pero es una dependencia más);
  - comparar con las listas BIP-39 y EFF que ya van en `assets/`.
- **Migración:** ninguna. No cambia ningún formato. Las bóvedas que ya tienen una maestra débil se siguen abriendo. Si se quiere, al desbloquear con la maestra se puede avisar una vez de que conviene cambiarla. Se calcularía con el `String` que ya existe en la pantalla (`pw`), sin crear otra copia.
- **A comprobar, sin hallazgo:** el diálogo de importar también exige 10 caracteres (`VaultBackup.kt:222`). La app siempre los exigió (`git log -S "MIN_MASTER = "`: solo `f141966`), pero no he podido comprobar si CriptoPanel admitía maestras más cortas. Si las admitía, una `criptopanel-vault` así no se podría importar. La corrección de arriba no cambia eso.
- **¿Nuevo?:** nuevo. No aparece en `docs/auditorias/2026-10-31-auditoria-1.3.0.md`, cuyo modelo de amenaza (§1.2) sí incluye a quien tiene una copia `.sgbak`.

### CRY-A02: La DEK envuelta por la huella no va atada a la bóveda ni lleva versión

- **Dónde:**
  - `android/BiometricVault.kt:307-313`: se envuelve sin AAD y se guarda sin ninguna marca de esquema.
    ```kotlin
                    val stored = try {
                        val wrapped = c.doFinal(key)
                        prefs(activity).edit {
                            putString(KEY_IV, Base64.getEncoder().encodeToString(c.iv))
                            putString(KEY_BLOB, Base64.getEncoder().encodeToString(wrapped))
                            if (inStrongBox) putBoolean(KEY_IN_STRONGBOX, true) else remove(KEY_IN_STRONGBOX)
                        }
    ```
  - `android/BiometricVault.kt:505` y `:541-543`: se desenvuelve también sin AAD.
    ```kotlin
                            Result.success(Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, k, GCMParameterSpec(128, ivBytes)) })
    ```
    ```kotlin
                val dataKey = try {
                    c.doFinal(wrapped)
                } catch (e: Exception) {
    ```
  - La huella se desactiva desde la pantalla, en un paso aparte, después de guardar la bóveda nueva (`ui/screens/VaultBackup.kt:121-127` y `:168-169`):
    ```kotlin
                        try {
                            if (store.changeMaster(pw)) {
                                // From here the password did change: nothing below may say it did not.
                                val fingerprintOff = try {
                                    BiometricVault.disable(context) // It wrapped the old data key.
                                    true
                                } catch (e: Exception) {
    ```
    ```kotlin
                            val dropped = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)!!.use { store.importReplace(it, pw) } }
                            BiometricVault.disable(context)
    ```
  - Si la DEK no abre la bóveda, el desbloqueo enseña el texto genérico y la huella sigue activada (`vault/VaultStore.kt:65-68`, `ui/screens/VaultScreen.kt:297-299`):
    ```kotlin
    fun biometricUnlockError(e: Exception): String = when (e) {
        is CryptoFailure, is MasterRequired -> e.message ?: BIOMETRIC_UNLOCK_FAILED
        else -> BIOMETRIC_UNLOCK_FAILED
    }
    ```
    ```kotlin
            BiometricVault.unlock(activity, onKey = { key ->
                scope.launchUnlockWithKey(store, key) { error = it }
            }, onError = { error = it })
    ```
- **Estado:** Confirmado.
- **Severidad:** Informativa. `CVSS:3.1/AV:L/AC:H/PR:N/UI:R/S:U/C:N/I:N/A:N` = **0.0**.
- **MASVS:** MASVS-CRYPTO-2 (gestión de claves: ligar cada clave a su contexto y a su versión).
- **Por qué es un problema aquí:**
  - La envoltura es correcta: AES-256-GCM en el Keystore, con un IV que genera el Keystore, autenticación por uso con `BIOMETRIC_STRONG` y la clave invalidada al añadir una huella.
  - Lo que falta es atarla a la bóveda. Hay tres casos en los que la huella guarda la DEK de una bóveda que ya no está:
    - la muerte del proceso entre `changeMaster` (que ya guardó la DEK nueva) y `BiometricVault.disable`;
    - un `disable` que falla (caso `PASSWORD_CHANGED_FINGERPRINT_FAILED`, `tools/ToolTexts.kt:547-549`);
    - una excepción en `importReplace` después de guardar la copia.
  - En esos casos, la pantalla bloqueada lanza la huella sola (`ui/screens/VaultScreen.kt:301`, `LaunchedEffect`). El usuario pone el dedo, `VaultCrypto.read` falla y lee «Contraseña incorrecta o datos dañados» (`crypto/Crypto.kt:196`), un texto que hace pensar en una bóveda dañada.
  - La huella no se desactiva sola y vuelve a fallar en cada intento. No hay impacto de seguridad: la DEK antigua no abre lo guardado después del cambio, porque `changeMaster` crea una DEK nueva (`vault/Vault.kt:381-390`). Ninguna ruta del código usa esa DEK para abrir otra cosa.
  - Lo marco porque la fase 4 pide revisar el «texto cifrado sin versión de esquema». El blob `vault_biometric/wrapped` es el único dato cifrado del alcance sin versión y sin AAD. Los adjuntos no llevan versión en el archivo, pero sí en la AAD (`seguridad-attachment-v1`, `vault/Attachments.kt:34`), y `AttachmentRef` podría llevar un campo de versión con valor por defecto.
- **Corrección sin cambiar el formato** (recomendada): si la huella entrega una clave que no abre la bóveda, se desactiva y se dice por qué.
  ```kotlin
  // vault/VaultStore.kt
  fun CoroutineScope.launchUnlockWithKey(
      store: VaultStore,
      key: ByteArray,
      onWrongKey: () -> Unit = {},
      onError: (String) -> Unit,
  ): Job = launch(start = CoroutineStart.ATOMIC) {
      try {
          store.unlockWithKey(key)
      } catch (e: CancellationException) {
          throw e
      } catch (e: CryptoFailure) {
          // La clave que guarda la huella no abre esta bóveda (otra DEK): no se vuelve a ofrecer.
          onWrongKey()
          onError(FINGERPRINT_STALE)
      } catch (e: Exception) {
          onError(biometricUnlockError(e))
      }
  }

  // ui/screens/VaultScreen.kt, UnlockVault
  BiometricVault.unlock(activity, onKey = { key ->
      scope.launchUnlockWithKey(store, key, onWrongKey = { BiometricVault.disable(context) }) { error = it }
  }, onError = { error = it })
  ```
  Una bóveda que esté dañada también la desactivaría, y no se pierde nada: en ese caso la maestra tampoco la abre. `MasterRequired` no es una `CryptoFailure`, así que la regla de los 7 días y la del reloj (A11) no cambian. Test: en `VaultStoreTest`, `unlockWithKey` con una DEK de otra bóveda lanza `CryptoFailure` y deja la bóveda bloqueada. El borrado de la huella se prueba en el móvil, con la lista de `README.md`.
- **Si algún día se quiere AAD** (no lo recomiendo ahora: cada cambio de `BiometricVault` necesita su propia prueba en el móvil):
  - guardar `putInt("scheme", 2)` y usar como AAD `"seguridad-biometric-v2"`;
  - al leer, sin `scheme` se descifra sin AAD, como en 1.1–1.3, y se sigue leyendo para siempre;
  - no hace falta migrar: el blob se rehace la próxima vez que se active la huella.
- **¿Nuevo?:** nuevo. Toca LIBRE-4, que fijó los textos del fallo de la huella, y el texto `PASSWORD_CHANGED_FINGERPRINT_FAILED` de la tarea 11, pero ninguno de los dos trata la DEK antigua.

### CRY-A03: Copias de las claves en objetos de los proveedores que no se borran

- **Dónde:**
  - `crypto/Crypto.kt:311`: AES-GCM, el camino de la bóveda (DEK y KEK), de los adjuntos y del cifrado por defecto.
    ```kotlin
                init(if (encrypt) Cipher.ENCRYPT_MODE else Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BYTES * 8, nonce))
    ```
  - `crypto/Crypto.kt:258` (Twofish, Serpent y Camellia) y `:320` (ChaCha20-Poly1305):
    ```kotlin
                init(encrypt, AEADParameters(KeyParameter(key), TAG_BYTES * 8, nonce, aad))
    ```
  - `crypto/Crypto.kt:271`: la cascada; igual en `:384`, la clave aleatoria con HKDF.
    ```kotlin
        HKDFBytesGenerator(SHA256Digest()).apply { init(HKDFParameters(key, null, "${dialect.labelPrefix}-cascade-$layer".toByteArray())) }.generateBytes(out, 0, out.size)
    ```
  - Lo que sí se borra: `vault/VaultStore.kt:479-482`
    ```kotlin
        fun lock(reason: LockReason = LockReason.MANUAL) = synchronized(opening) {
            epoch++
            dek?.fill(0)
            dek = null
    ```
- **Estado:** Confirmado. Con `javap` sobre `bcprov-jdk18on-1.86.jar`:
  - `KeyParameter(byte[], int, int)` hace `System.arraycopy` a su campo `key`, y `getKey()` devuelve ese campo;
  - `HKDFParameters` hace `Arrays.clone` de la IKM;
  - `GCMBlockCipher` guarda la clave en `lastKey`.

  `SecretKeySpec` copia la clave en su constructor (`key.clone()`).
- **Severidad:** Baja. `CVSS:3.1/AV:L/AC:H/PR:H/UI:N/S:U/C:L/I:N/A:N` = **1.9**.
- **MASVS:** MASVS-STORAGE-2 (fugas de datos sensibles, aquí en la memoria) y MASVS-CRYPTO-2.
- **Por qué es un problema aquí:**
  - La higiene del código propio es buena: `fill(0)` en cada salida (`crypto/Crypto.kt:238,519-520,567,570`, `vault/Vault.kt:222,245,274,291`, `vault/VaultStore.kt:481`).
  - Pero cada `aead(...)` deja en el heap otra copia de la clave dentro de un objeto del proveedor, que nadie puede poner a cero: la DEK en cada lectura y cada guardado de la bóveda, la KEK en cada desbloqueo, la clave de cada adjunto, y la IKM de HKDF.
  - Esas copias sobreviven a `lock()` hasta que pasa el recolector. Conscrypt guarda además su propia copia de la clave en el `Cipher`.
  - Para leerlas hay que volcar la memoria del proceso (root o depurador). Con esa capacidad, el atacante también puede esperar al siguiente desbloqueo (`docs/superpowers/specs/2026-10-23-seguridad-design.md:149`). Por eso es Baja y no más.
  - Es el mismo tipo de riesgo que ya se aceptó para el `String` de Compose y el estado SHA-1 de Conscrypt (auditoría anterior, §5.2). Las claves no figuraban en esa lista.
- **Corrección:**
  - En los caminos de Bouncy Castle se pueden borrar la copia de `KeyParameter` y `lastKey`, porque son el mismo array:
    ```kotlin
    private fun blockGcm(engine: BlockCipher, encrypt: Boolean, key: ByteArray, nonce: ByteArray, aad: ByteArray, input: ByteArray): ByteArray {
        val kp = KeyParameter(key)
        try {
            return GCMBlockCipher.newInstance(engine).run {
                init(encrypt, AEADParameters(kp, TAG_BYTES * 8, nonce, aad))
                val out = ByteArray(getOutputSize(input.size))
                val n = processBytes(input, 0, input.size, out, 0)
                val total = n + doFinal(out, n)
                if (total == out.size) out else out.copyOf(total).also { out.fill(0) }
            }
        } catch (e: InvalidCipherTextException) {
            throw CryptoFailure()
        } finally {
            kp.key.fill(0) // KeyParameter copia la clave y getKey() devuelve esa copia (bcprov 1.86); GCMBlockCipher.lastKey es el mismo array.
        }
    }
    ```
    Lo mismo para ChaCha20-Poly1305 (`:319-325`). Los subkeys que expanden los motores siguen sin borrarse.
  - Para AES vía Conscrypt y para `HKDFParameters` no hay una API que borre la copia. Lo proporcionado es **aceptar el riesgo y apuntarlo** en la tabla de riesgos residuales del próximo informe: «las claves pasan por `SecretKeySpec` y por los parámetros de Bouncy Castle, que no se pueden poner a cero; quedan en el heap hasta el recolector, también tras bloquear».
  - Cambiar AES a la implementación de tablas de Bouncy Castle para poder borrar sería peor: se pierde la aceleración por hardware y la resistencia a los canales laterales de tiempo.
- **Migración:** ninguna. Las claves y los formatos quedan iguales byte a byte; los tests de vectores (`CryptoTest`, `DialectTest`) lo comprueban.
- **¿Nuevo?:** conocido en parte. CIFRADO-1 (§3.2, F5) y §5.2 de `docs/auditorias/2026-10-31-auditoria-1.3.0.md` tratan la contraseña: los búferes de Bouncy Castle, que se arreglaron, y el `String` de Compose y el estado de Conscrypt, que se aceptaron. Las copias de las **claves** en `SecretKeySpec`, `KeyParameter` y `HKDFParameters` son nuevas.

---

## Observaciones que no son hallazgo

Están revisadas, y la razón por la que no las marco es la que se da en cada una.

- **Metadatos en claro en `vault.json` y, por tanto, en las copias:**
  - qué son: `masterCheckedAt`, `revision`, el coste de Argon2id y el tamaño de los blobs (`vault/Vault.kt:127-137`);
  - por qué no es un hallazgo: van todos como AAD (`vault/Vault.kt:163-165,206-214`), así que no se pueden cambiar sin que falle el descifrado, y no revelan ninguna entrada. Solo dicen cuándo se escribió la maestra por última vez y cuántas veces se ha guardado. Quitarlos exigiría una versión 4 del formato, y no compensa.
- **Volver a una versión anterior de `vault.bin`, o importar una copia vieja, sin contador externo:**
  - qué pasa: para cambiar `vault.bin` hace falta root, y para importar hace falta la maestra de esa copia;
  - por qué no es un hallazgo: `revision` y `masterCheckedAt` en la AAD impiden mezclar trozos de dos versiones, y no ampliar los 7 días (`VaultTamperTest`), aunque no impiden cambiar el archivo entero por uno anterior. Una versión anterior no alarga la huella: lleva una `masterCheckedAt` antigua y `biometricRefusal` la rechaza (`vault/Vault.kt:412-417`).
- **`KDF_RAW_KEY` (CP1 de CriptoPanel 1.9.0, la clave aleatoria usada tal cual):** solo se lee (`crypto/Crypto.kt:377,417-418`). `newHeader` siempre escribe `KDF_RAW_KEY_HKDF`, con una clave distinta para cada mensaje sacada de una sal de 16 bytes (`crypto/Crypto.kt:376,499-500`). No hay degradación al escribir.
- **GCM y ChaCha20-Poly1305 de Bouncy Castle** escriben el texto descifrado en `out` antes de comprobar la etiqueta. Si la etiqueta falla, ese búfer local no se devuelve nunca (`crypto/Crypto.kt:259-266,321-328`). Archivos: cada registro se verifica antes de escribirse, el descifrado va a la caché privada y se borra en cualquier fallo (`ui/screens/SecurityToolsScreen.kt:684-716`), y al arrancar se borran los restos (`SeguridadApp.kt:34`).
- **Los adjuntos no cambian de clave al cambiar la maestra.** Eso no da acceso a nada más:
  - una copia `.sgbak` anterior ya contiene esos mismos archivos;
  - un adjunto añadido después tiene una clave nueva;
  - la exportación JSON (`VaultStore.exportText`) no se ofrece en la interfaz.

  La identidad sí se ofrece renovar (`vault/MasterChanged.kt:30-33`).
- **`argon2idRaw` no comprueba sus parámetros** (`crypto/Crypto.kt:243-252`). Solo la usa el vector de RFC 9106: ningún código de `app/src/main/java` la llama.
- **`appJson` con `coerceInputValues = true`** (`core/Json.kt:6-10`). Un `"format": null` pasa a `criptopanel-vault`, y un `"version": null` pasa a `VERSION_UNKNOWN`. Ninguno de los dos degrada nada, porque la etiqueta y la versión van en la AAD (`vault/Vault.kt:206-214`).
- **Clave de la huella sin `setUnlockedDeviceRequired`** (`android/BiometricVault.kt:117-119`). Está justificado en `docs/superpowers/specs/2026-10-27-seguridad-1.3.0-herramientas-design.md:205`, y la autenticación en cada uso ya exige la huella cada vez.

---

## Checklist de la fase 4

| # | Punto | Estado | Evidencia |
|---|---|---|---|
| **Claves** ||||
| 1 | Claves en el Keystore, no en el código, los recursos, `BuildConfig`, las prefs ni los ficheros | OK | Clave de la huella en `AndroidKeyStore` (`android/BiometricVault.kt:107-122`). DEK aleatoria (`vault/Vault.kt:241`), guardada solo envuelta por Argon2id (`vault/Vault.kt:270`) o por el Keystore (`android/BiometricVault.kt:308-311`). `vault.bin` no va atado al Keystore por decisión de diseño (`docs/superpowers/specs/2026-10-23-seguridad-design.md:151`): ver CRY-A01 |
| 2 | StrongBox con respaldo controlado | OK | `android/BiometricVault.kt:116,244,333-354`; solo se apunta con pruebas (`tools/KeyPlacement.kt:16-17,23-24,52-55`); el nivel se enseña en Ajustes (`android/BiometricVault.kt:126-141`) |
| 3 | `setUserAuthenticationRequired(true)` y `setInvalidatedByBiometricEnrollment(true)` | OK | `android/BiometricVault.kt:112-115` (autenticación por uso, `BIOMETRIC_STRONG` desde Android 11); `KeyPermanentlyInvalidatedException` desactiva la huella (`:503,514-517`) |
| 4 | Propósito mínimo y cifrado aleatorizado | OK | `PURPOSE_ENCRYPT or PURPOSE_DECRYPT`, GCM y `NoPadding` (`android/BiometricVault.kt:108-110`); aleatorización por defecto y IV leído de `c.iv` (`:310`) |
| 5 | Claves de Bouncy Castle fuera del Keystore envueltas antes de guardarse | OK | Semilla de la identidad dentro de las entradas cifradas con la DEK (`vault/Vault.kt:64-69`, `vault/Contacts.kt:59-73`); clave de cada adjunto dentro de `AttachmentRef` (`vault/Vault.kt:93-94`, `vault/Attachments.kt:91-103`) |
| 6 | Rotación y versión de la clave junto al texto cifrado | Hallazgo (CRY-A02) | OK en textos y archivos (magic, `VERSION`, algoritmo y KDF, `crypto/Crypto.kt:404-412,415`), en la bóveda (`format`, `version` y coste, `vault/Vault.kt:117,126`, DEK nueva en `changeMaster`, `:381-390`) y en la nueva envoltura con el coste máximo (`:360-375`). Sin versión ni AAD: el blob de la huella |
| **Algoritmos y modos** ||||
| 7 | Nada de ECB, DES, 3DES, RC4, Blowfish, MD5, SHA-1 para integridad ni RSA PKCS#1 | OK | Los únicos `Cipher.getInstance` de la parte A son `"AES/GCM/NoPadding"` (`crypto/Crypto.kt:310`, `android/BiometricVault.kt:285,505`); la integridad de los adjuntos es SHA-256 (`vault/Attachments.kt:101,122`) |
| 8 | Algoritmos preferidos (AES-256-GCM, ChaCha20-Poly1305) | OK | `crypto/Crypto.kt:40-46,309-328`; Twofish, Serpent y Camellia en GCM con la API ligera (`:255-266,293-295`) |
| 9 | CBC sin MAC | No aplica | No hay CBC en la parte A |
| **AES-GCM** ||||
| 10 | IV de 12 bytes, único con la misma clave | OK | Textos: aleatorio de 12 bytes (`crypto/Crypto.kt:204,501`). Archivos: STREAM con prefijo de 7 bytes, contador sin desbordamiento y marca del último (`crypto/FileCrypto.kt:21-22,83,126`), con una clave por archivo, sacada de la sal (`crypto/Crypto.kt:500`). Bóveda: aleatorio en cada guardado (`vault/Vault.kt:218`). Adjuntos: clave y nonce nuevos (`vault/Attachments.kt:91,94`). Ningún contador se reinicia al restaurar o reinstalar |
| 11 | IV generado por el Keystore y leído de `cipher.iv` | OK | `android/BiometricVault.kt:285,310` |
| 12 | Etiqueta de 128 bits y AAD con contexto | OK (excepción: CRY-A02) | `TAG_BYTES * 8` (`crypto/Crypto.kt:258,311,320`). AAD: la cabecera entera en textos y archivos (`crypto/Crypto.kt:512,558`, `crypto/FileCrypto.kt:60,102`); etiqueta, formato, versión, coste y sal, y además la revisión y la fecha en los datos, en la bóveda (`vault/Vault.kt:163-165,206-214`); `seguridad-attachment-v1` más el id en los adjuntos (`vault/Attachments.kt:34,49`) |
| 13 | `AEADBadTagException` tratada como fallo de integridad | OK | `crypto/Crypto.kt:264-266,315-317,326-328` (un único `CryptoFailure`); `vault/Vault.kt:188-197`; `vault/Attachments.kt:131-134`; `android/BiometricVault.kt:541-545` |
| **Aleatoriedad** ||||
| 14 | Solo `SecureRandom()` sin semilla | OK | `crypto/Crypto.kt:207-209`; `UUID.randomUUID()` (`vault/Contacts.kt:63,101`). `grep` de `kotlin.random`, `java.util.Random`, `Math.random`, `setSeed` y `SHA1PRNG` en `app/src/main/java`: sin resultados |
| **Derivación de claves** ||||
| 15 | Argon2id con parámetros adecuados | OK | Versión 0x13, 64 MiB, t = 3, p = 1 por defecto; «Alto»: 128 MiB, t = 4 (`crypto/Crypto.kt:55,200,224-230`, `settings/Settings.kt:21-22`). Por encima del mínimo de OWASP (19 MiB, t = 2) |
| 16 | Sal aleatoria de 16 bytes o más, única | OK | `crypto/Crypto.kt:203,221,500`; `vault/Vault.kt:256` (sal nueva en cada envoltura) |
| 17 | Límites del coste leído de un archivo (mínimo, máximo y memoria) | OK | `checkSane`: 8·p ≤ m ≤ 128 MiB, 1 ≤ t ≤ 10, 1 ≤ p ≤ 4, y no más de ¾ del heap (`crypto/Crypto.kt:60-72`); cabecera (`:439`); bóveda (`vault/Vault.kt:281`); `OutOfMemoryError` pasa a `CryptoFailure` (`crypto/Crypto.kt:86-90`); A8 nunca baja el coste (`vault/Vault.kt:363-371`) |
| 18 | Una contraseña débil no se protege solo con el KDF | Hallazgo (CRY-A01) | `ui/screens/VaultBackup.kt:45,222`, `ui/screens/VaultScreen.kt:254` |
| **Firmas, MAC y acuerdos de clave** ||||
| 19 | Se comprueba el resultado de `verify()` | No aplica | Es de la parte B; `vault/Contacts.kt:99,125` solo llama a `parseCard` |
| 20 | ECDH validado y con KDF | No aplica | Es de la parte B |
| 21 | MAC, hashes y PIN comparados en tiempo constante | OK | `MessageDigest.isEqual` en `vault/Backup.kt:132,160` y `vault/Attachments.kt:122`; las claves públicas se comparan con `contentEquals` (`vault/Contacts.kt:156,170,216-219`), y es correcto porque son públicas |
| **Bouncy Castle en Android** ||||
| 22 | No se pide el proveedor `"BC"` por nombre | OK | API ligera (`crypto/Crypto.kt:3-16,224-235,257,319`); `grep '"BC"'`: sin resultados |
| 23 | `bcprov` al día | No aplica (revisor de dependencias) | `gradle/libs.versions.toml:14` (`1.86`) |
| 24 | No mezclar claves del Keystore con Bouncy Castle | OK | La clave del Keystore solo pasa por `javax.crypto.Cipher` (`android/BiometricVault.kt:285,505`) |
| **Higiene de memoria** ||||
| 25 | Claves, contraseñas y texto en claro a cero tras usarse | Hallazgo (CRY-A03) | OK en el código propio: `crypto/Crypto.kt:238,519-520,567,570`, `crypto/FileCrypto.kt:80-81,89,124,133`, `vault/Vault.kt:222,245,274,291,302`, `vault/VaultStore.kt:481`, `android/BiometricVault.kt:241,334,373`, `core/Streams.kt:35,59,78`. `PBEKeySpec`: no aplica |
| **Errores de composición** ||||
| 26 | IV o contador que se reinicia tras reinstalar o restaurar una copia | OK | Todos los nonces son aleatorios o van con una clave por archivo (fila 10) |
| 27 | La misma clave para dos usos (cifrar y MAC, envolver y datos) | OK | KEK solo para envolver y DEK solo para los datos (`vault/Vault.kt:257,270-271`); HKDF con una etiqueta por uso (`crypto/Crypto.kt:271,384`); una clave por adjunto (`vault/Attachments.kt:91`) |
| 28 | Texto cifrado sin versión de esquema | Hallazgo (CRY-A02) | Solo el blob de la huella |
| 29 | Caída silenciosa a guardar en claro si falla el Keystore | OK | Si falla, no se guarda nada (`android/BiometricVault.kt:297-298,315-322`); la maestra sigue siendo la vía (`:513-519`) |
| 30 | Degradación de versión, algoritmo o KDF | OK | `vault/Vault.kt:184-197,206-214`; la huella se niega para v1 y para formatos antiguos (`:412-413`); el dialecto ha de coincidir con su prefijo (`crypto/Crypto.kt:556-557`); la versión de cabecera, en `:432` |
| 31 | Copia o archivo manipulado | OK | `.sgbak`: patrones exactos y límites sobre lo leído (`vault/Backup.kt:192-254`); el SHA-256 de cada adjunto se autentica con la DEK de la copia antes de tocar nada (`vault/VaultStore.kt:771-790,864-872`); STREAM detecta truncado, reordenado y cola añadida (`crypto/FileCrypto.kt:115-127`) |
| 32 | Biometría invalidada, StrongBox o Keystore que falla | OK | `android/BiometricVault.kt:503,513-519,455-463`; el «StrongBox falló» solo se apunta con pruebas (`tools/KeyPlacement.kt:52-55,106`) |
| 33 | Copias de la plataforma | OK | `allowBackup="false"` y reglas que excluyen todo (`app/src/main/AndroidManifest.xml:11-13`, `app/src/main/res/xml/backup_rules.xml`, `app/src/main/res/xml/data_extraction_rules.xml`) |

---

## Revisión experto-seguridad-cripto

He usado `references/cripto-moderna.md` como lista de comprobación.

- **AEAD en todo** (§2): AES-256-GCM, ChaCha20-Poly1305 y GCM sobre Twofish, Serpent y Camellia. Etiqueta completa y AAD con contexto en todos los formatos (`crypto/Crypto.kt:255-329,512`, `vault/Vault.kt:163-214`, `vault/Attachments.kt:49`). Ningún cifrado sin autenticar.
- **Nonces** (§2): aleatorios de 96 bits, o STREAM con una clave por archivo. Con nonces aleatorios, el límite de unas 2³² operaciones por clave queda muy lejos: una DEK cifra un blob por guardado (`vault/Vault.kt:218`). El contador de STREAM se para antes de repetir (`crypto/FileCrypto.kt:83,126`).
- **KDF** (§3): Argon2id con 64 MiB y t = 3 por defecto, por encima del mínimo de RFC 9106 y de OWASP. HKDF solo sobre material de alta entropía: la clave aleatoria y la clave de cada capa de la cascada (`crypto/Crypto.kt:271,384`), nunca sobre una contraseña. El coste leído tiene topes de memoria y de tiempo (`crypto/Crypto.kt:60-72`).
- **Gestión de claves** (§11.4: dónde vive, cómo rota y qué pasa si se filtra):
  - la DEK vive en memoria mientras la bóveda está abierta y se borra al bloquear (`vault/VaultStore.kt:479-482`);
  - rota al cambiar la maestra (`vault/Vault.kt:381-390`), y la KEK, en cada envoltura;
  - el punto débil real es la maestra, porque las copias salen del móvil: CRY-A01.
- **Tiempo constante** (§3): `MessageDigest.isEqual` en las dos comparaciones de hashes con secreto (`vault/Backup.kt:132,160`, `vault/Attachments.kt:122`).
- **Nada de criptografía propia:** `javax.crypto` y la API ligera de Bouncy Castle 1.86. La codificación UTF-8 propia de la contraseña (`crypto/Crypto.kt:132-179`) no es criptografía. Reproduce byte a byte lo que hacía Bouncy Castle 1.80 (decisión F5) y se comprueba con datos reales en `PasswordBytesTest`.
- **Proporción:** no hay ningún hallazgo crítico ni alto. El único medio es de política (CRY-A01), no del cifrado. Los otros dos son de robustez (CRY-A02) y de higiene (CRY-A03). Ningún hallazgo obliga a cambiar un formato que se lee para siempre.

## Revisión Ingeniero Móvil Senior

He usado `seguridad-criptografia-movil` como lista de comprobación.

- **Biometría ligada a una operación criptográfica**, no a un booleano: `CryptoObject` con un `Cipher` iniciado antes de `authenticate` (`android/BiometricVault.kt:193,505,524`), `BIOMETRIC_STRONG` (`:176`) y autenticación en cada uso (`:115`).
- **Keystore y StrongBox:** respaldo controlado al TEE (`android/BiometricVault.kt:333-354`). `Cipher.init` y la creación de la clave van fuera del hilo principal (`:283-289,501-509`), y Ajustes dice dónde quedó la clave (`ui/screens/SettingsScreen.kt:372-396`). No usa `EncryptedSharedPreferences`, que está obsoleto: el blob va en unas prefs `MODE_PRIVATE` y cifrado por el Keystore (`android/BiometricVault.kt:98`).
- **Ciclo de vida y concurrencia:**
  - las copias de la DEK se borran en cada salida de `enable`, también en `ON_DESTROY` (`android/BiometricVault.kt:248-265,369-378`);
  - la entrega de la clave es `CoroutineStart.ATOMIC` y `NonCancellable` (`vault/VaultStore.kt:76-86,264,295`);
  - `lock()` y `opened()` van bajo `synchronized(opening)` con `epoch` (`:314-336,479-488`).
- **Almacenamiento:** `vault.bin` y los adjuntos solo se escriben cifrados y de forma atómica (`core/AtomicFile.kt:15-23`). La copia de la plataforma está desactivada (`app/src/main/AndroidManifest.xml:11-13`).
- **Estado de la interfaz:** la maestra vive en `remember` y no en `rememberSaveable`, así que no acaba en un `Bundle` (`ui/screens/VaultScreen.kt:228-229`, `ui/screens/VaultBackup.kt:220-221`). Los usos de `rememberSaveable` de la app no guardan secretos: comprobado con `grep`.
- **Portapapeles:** marca `EXTRA_IS_SENSITIVE` desde Android 13, borra al terminar el tiempo y al reabrir la app, y solo lee el texto cuando la etiqueta es la suya (`android/Clipboard.kt:60-81,102-119`). Comparar el texto con `==` en `:77` no es un problema de tiempo: nadie puede medirlo desde fuera.
- **Pruebas en el móvil que piden los arreglos:** CRY-A02 (que una huella que ya no corresponde se desactive) entra en la lista de la huella del `README.md`. CRY-A01 y CRY-A03 se prueban en JVM.

## Textos para el Abogado Digital (revisión final)

Son textos nuevos que proponen las correcciones. Ninguno se ha aplicado.

1. **CRY-A01, `MASTER_TOO_WEAK`:** «Esta contraseña es fácil de adivinar. Usa una frase de 5 o 6 palabras que solo tú conozcas (el Generador te puede dar una).» Aparece al crear la bóveda y al cambiar la maestra, nunca al abrir ni al importar.
2. **CRY-A01, `newMasterProblem`:** «Mínimo 10 caracteres». Hoy esa información está en la etiqueta del campo: «Contraseña maestra (mínimo $MIN_MASTER caracteres)», `ui/screens/VaultScreen.kt:241`.
3. **CRY-A02, `FINGERPRINT_STALE`:** «La huella ya no abre esta bóveda (se cambió la contraseña maestra o se importó una copia). Entra con la contraseña maestra y vuelve a activarla si quieres.»
4. **CRY-A03:** ningún texto en la interfaz. Solo una línea en la tabla de riesgos residuales del próximo informe: «Las claves pasan por `SecretKeySpec` y por los parámetros de Bouncy Castle, que no se pueden poner a cero; quedan en la memoria hasta el recolector, también tras bloquear».
