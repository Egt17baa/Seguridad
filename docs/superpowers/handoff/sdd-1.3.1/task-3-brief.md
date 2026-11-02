### Task 3: CRY-A02, la huella que guarda la clave de otra bóveda se desactiva

**Archivos:**
- Modificar: `app/src/main/java/app/seguridad/vault/VaultStore.kt:58-86` y `app/src/main/java/app/seguridad/ui/screens/VaultScreen.kt:290-300`
- Test: `app/src/test/java/app/seguridad/vault/VaultStoreTest.kt`

**Interfaces:**
- Produce:
  - `const val FINGERPRINT_STALE`, en `VaultStore.kt`, junto a `BIOMETRIC_UNLOCK_FAILED`, con el texto de `04-cripto-parteA.md`, «Textos para el Abogado Digital», punto 3;
  - `fun CoroutineScope.launchUnlockWithKey(store: VaultStore, key: ByteArray, onWrongKey: () -> Unit = {}, onError: (String) -> Unit): Job`.

- [ ] **Paso 1: tests que fallan:**
  - `launchUnlockWithKey` con la DEK de otra bóveda llama a `onWrongKey` una vez, da `FINGERPRINT_STALE`, deja la bóveda bloqueada y pone la clave a cero;
  - con una bóveda que exige la maestra (`MasterRequired`, la regla de los 7 días o el reloj, con los ayudantes de tiempo que ya usa `VaultStoreTest`) **no** llama a `onWrongKey` y da el texto de hoy;
  - con la DEK buena abre la bóveda y no llama a `onWrongKey`.
- [ ] **Paso 2:** ejecutar `./gradlew :app:testDebugUnitTest --tests '*VaultStoreTest*' --offline --no-daemon`. Debe fallar.
- [ ] **Paso 3: implementar.**
  - Una rama `catch (e: CryptoFailure)` antes de la genérica, que llama a `onWrongKey()` y después a `onError(FINGERPRINT_STALE)`.
  - En `UnlockVault`, `onWrongKey = { BiometricVault.disable(context) }`, envuelto en `try` como en `VaultBackup.kt:123-128`: si `disable` falla, se enseña el mismo `FINGERPRINT_STALE`.
  - Después del fallo, la pantalla deja de ofrecer el botón de la huella, porque `bio` se vuelve a leer.
- [ ] **Paso 4:** las dos verificaciones globales, con 0 fallos.
- [ ] **Paso 5: commit** «vault: la huella con la clave de otra bóveda se desactiva y lo dice (CRY-A02)» y push.

