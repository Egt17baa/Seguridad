### Task 5: CRY-B02, TOTP no inventa ajustes

**Archivos:**
- Modificar:
  - `app/src/main/java/app/seguridad/crypto/Totp.kt:59-69`, `:85-95` y `:125-135`;
  - `app/src/main/java/app/seguridad/ui/screens/VaultEntryScreen.kt:144-156` y `:243-246`.
- Tests: `app/src/test/java/app/seguridad/vault/VaultTotpTest.kt` y `app/src/test/java/app/seguridad/crypto/OtpMigrationTest.kt`

**Interfaces:**
- Produce, en `Totp.kt`:
  - `fun TotpSecret.isSupported(): Boolean`;
  - `fun otpAuthUnsupported(text: String): Boolean`, que es `true` si `text` es un enlace `otpauth://totp/` con una clave válida y algún ajuste no admitido;
  - `const val TOTP_UNSUPPORTED_LINK`, el texto del punto 1 de «Textos para el Abogado Digital» de `04-cripto-parteB.md`, que dice «Clavero»;
  - `const val TOTP_UNSUPPORTED_ENTRY`, el del punto 2.
- Produce, en `totpCode`: devuelve `null` si `!isSupported()`.

- [ ] **Paso 1: tests que fallan:**
  - `parseOtpAuth` da `null` con `digits=10`, `digits=5`, `period=5`, `period=300` y `algorithm=MD5`, y `otpAuthUnsupported` da `true` con esos casos;
  - sin parámetros, sigue dando 6, 30 y SHA1;
  - `totpCode` da `null` con `algorithm = "MD5"`, `digits = 10` o `period = 300`, y con `algorithm = ""` da el código SHA1 de RFC 6238;
  - todas las entradas TOTP de los datos de prueba importados y todas las de `OtpMigrationTest` cumplen `isSupported()`;
  - los vectores de RFC 6238 siguen pasando.
- [ ] **Paso 2:** ejecutar `gradle -p jvm-check test --tests '*Totp*' --tests '*OtpMigration*' --offline --no-daemon`. Debe fallar.
- [ ] **Paso 3: implementar.**
  - `hmacName` devuelve `String?`: `"SHA1"` y `""` dan `HmacSHA1`, `"SHA256"` y `"SHA512"` los suyos, y cualquier otro `null`.
  - En `totpCode` ya no se usa `coerceIn`.
  - `parseOtpAuth` usa el valor por defecto solo cuando falta el parámetro.
  - El KDoc de `TOTP_MIN_KEY_BYTES` dice que 80 bits está por debajo de los 128 de RFC 4226 (R6) y que se aceptan por compatibilidad.
  - Pantallas:
    - en `EntryEditViewModel.applyOtpAuth` (`VaultEntryScreen.kt:229`), si `otpAuthUnsupported(text)`, el error es `TOTP_UNSUPPORTED_LINK`;
    - en la ficha de una entrada con `!isSupported()`, `TOTP_UNSUPPORTED_ENTRY` en lugar de la tarjeta del código;
    - la lista no cambia.
- [ ] **Paso 4:** las dos verificaciones globales, con 0 fallos.
- [ ] **Paso 5: commit** «totp: rechazar los ajustes no admitidos en vez de cambiarlos (CRY-B02)» y push.

