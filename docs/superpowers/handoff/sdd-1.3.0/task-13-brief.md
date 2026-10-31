### Task 13: Clave pública: A15a, A15b, A16a y A16b

**Archivos:**
- Modificar:
  - `vault/PkActions.kt`: `data class PkResult(val inputs: PkInputs, val output: String)`, `data class PkInputs(val contactId: String?, val text: String?, val fileUri: String?)` y `fun resultFor(current: PkInputs, last: PkResult?): String?`;
  - `ui/screens/PkActionsScreen.kt`;
  - `vault/Contacts.kt`: `senderOf` y `fun sameKeyCount(senderEd: ByteArray, entries: List<VaultEntry>): Int`;
  - `crypto/FileCrypto.kt`: `safeFileName`;
  - `vault/PkActions.kt`: `fun anonymousFileName(original: String): String`.
- Test: `PkActionsTest.kt`, `ContactsTest.kt` y `CryptoTest.kt`.

- [ ] **Step 1: Write the failing tests:**
  - `staleResultIsHidden`: con otro contacto u otro texto, `resultFor` devuelve `null`.
  - `senderPrefersVerifiedThenNewest`: con dos contactos con la misma Ed25519, gana el verificado; si los dos son iguales, gana el de `updatedAt` mayor. `sameKeyCount == 2`.
  - `safeFileNameKeepsWholeCodePoints`: 119 «a» más «😀» y «x» da un nombre que acaba en «😀», sin medio par sustituto. 200 caracteres CJK dan 120 puntos de código.
  - `anonymousFileName`: `"factura marzo.pdf"` da `"archivo.pdf"`, `"sin-extension"` da `"archivo"`, y una extensión de más de 10 caracteres o no alfanumérica da `"archivo"`.

- [ ] **Step 2: Run** them. Expected: FAIL.

- [ ] **Step 3: Implement.**
  - **`safeFileName`:** trunca a 120 puntos de código con `codePointCount` y `offsetByCodePoints`.
  - **Límite del formato:** el de bytes de `FileCrypto.kt:63` sigue en `MAX_FILE_NAME_BYTES` (1024). Con 120 puntos de código nunca se pasa de 480 bytes, así que ese corte ya no se alcanza. El diseño decía 255, pero el límite real del formato es 1024, que no cambia; se apunta en el informe.
  - **Pantalla de cifrar un archivo:** el texto de A15b y la casilla «No enviar el nombre».
  - **Pantalla de abrir:** el aviso de A16a cuando `sameKeyCount > 1`.

- [ ] **Step 4: Run** the full task verification. Expected: PASS.

- [ ] **Step 5: Commit** `1.3.0: salida obsoleta, nombre del archivo y remitente con clave repetida`.

