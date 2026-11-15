# Re-revisión 2 de la tarea 10, fase C (`00206fc..dcf28dc`)

He comprobado el diff, el informe (`task-10c-fix2-report.md`), el inventario («Ronda 2»), `pendientes.md` (filas 38 y 42 a 44) y el «Ruling (advisor, T10 fase C, ronda 2)» (`progress.md:211`). `b5b9a77`, encima de `dcf28dc`, solo toca `progress.md`.

He corrido en jvm-check `UnlockErrorTextTest` y `SettingsTest`, y los dos están en verde. La suite entera no.

## Veredicto

**Aprobada con cambios, solo de documentos.** El código de n-1, n-2 y n-5 está bien y sus tests prueban lo que dicen.

Lo que queda está en los documentos:
- dos correcciones que el informe da por hechas no están en el informe de la ronda 1;
- una frase superada del inventario;
- falta una fila de fuera de alcance.

Ninguna toca el código. Bastan los arreglos y que el controlador lea el diff; no hace falta otra re-revisión.

## Estado de n-1 a n-7

| Hallazgo | Estado | Evidencia |
|---|---|---|
| **n-1** | **Cerrado** | Ver «n-1, comprobado» |
| **n-2** | **Cerrado en el código y los tests; los documentos, a medias** (r-1) | Ver «n-2, comprobado» |
| **n-3** | **Cerrado**, con un detalle (r-3) | He repetido la enumeración (abajo) |
| **n-4** | Cerrado | `docs/…/task-10c-report.md` ya no dice «basta un `\n`», y `:28` y `:118` dicen «Actualiza Clavero.» |
| **n-5** | **Cerrado** | Ver «n-5, comprobado» |
| **n-6** | **A medias** (r-1) | La fila 44 está bien (`pendientes.md:52`), y la fila del inventario también. Pero el informe de la ronda 1 sigue diciendo «con `liveRegion` como antes» (`docs/…/task-10c-fix1-report.md:104`, y la copia de `.superpowers/`). El informe de la ronda 2 dice «Lo corregí en el informe de la ronda 1», y no es así |
| **n-7** | Cerrado | La fila del inventario («Filas nuevas de la ronda 2», `BiometricVault.kt:641`) tiene un motivo verdadero: el texto lo escribe Android |

### n-1, comprobado

- **El orden en `checkSane`:** `outOfBounds` se mira primero y da `KDF_PARAMS_INVALID`; `cannotFit` solo da `KDF_OUT_OF_MEMORY` (`crypto/Crypto.kt:73-76`).
- **(a), nada decide por cuál de los dos llega.** He buscado las dos constantes en `app/src/main`, y solo deciden por ellas:
  - `masterUnlockErrorText` (`vault/Vault.kt:295`), que deja pasar las dos tal cual;
  - `newPasswordFailureMessage` (`crypto/Crypto.kt:204`), que solo deja pasar `KDF_OUT_OF_MEMORY`. Ahí `cannotFit` no puede darse: crear y cambiar la maestra usan el perfil saneado, que solo es «Alto» si `heap/2 ≥ 128 MiB` (`settings/Settings.kt:423`, `SettingsStore.kt:177`). Y si se diera, el texto nuevo sería más verdadero que el de reserva.
  - `checkArgon2id` (`imports/ImportKdf.kt:83-89`) traga los dos y da `IMPORT_KDF_TOO_BIG`.

  No cambia ningún comportamiento.
- **(b), la tabla de llamantes**, comprobada contra los cuatro `checkSane(` de `main` (`Crypto.kt:231`, `:505`; `Vault.kt:374`; `ImportKdf.kt:85`). Correcta.
- **Los tests:**
  - `SettingsTest.checkSaneTellsBoundsFromMemory` prueba «Alto» con un *heap* pequeño, fuera de límites, las dos condiciones a la vez y demasiadas pasadas.
  - `highIsRefusedWhenTheHeapPlainlyCannotHoldIt` cubre `cannotFit` y `decryptText`.
  - El primero de cada uno fallaba con el código de antes, que daba `KDF_PARAMS_INVALID` en `cannotFit`, así que el rojo es de comportamiento y no de compilación.
- **Las filas 38 y 42** describen bien el cambio. La 38 también apunta el «Alto» falso para un coste a mano entre 64 y 128 MiB.

### n-2, comprobado

- **El KDoc de `masterUnlockErrorText`** (`vault/Vault.kt:283-292`) es verdad. Distingue «antes de probar la clave» (`NOT_A_VAULT` y `KDF_PARAMS_INVALID`) de `KDF_OUT_OF_MEMORY`, que también puede salir tras una maestra buena. No atribuye `KDF_PARAMS_INVALID` al nuevo envoltorio.
- **`aDerivationWithoutMemoryIsSaid`:** 96 MiB con 96 MiB de *heap* inyectado, a través de `VaultCrypto.unlock`. Fallaba con el `checkSane` de antes.
- **`noMemoryWhenWrappingAgainLeavesTheFileAsItWas`:** el archivo queda igual byte a byte (`assertArrayEquals`), la bóveda bloqueada y el siguiente intento abre.

  Un matiz, sin cambio: el fallo se inyecta al evaluar `kdf()`, que es el argumento de `afterMasterUnlock` dentro del `derive` (`vault/VaultStore.kt:588`). Así que el cuerpo de `afterMasterUnlock` no llega a correr. Prueba igual lo que importa: un fallo dentro de ese `derive`, después de comprobar la maestra, no guarda nada. `afterMasterUnlock` es puro, y el guardado viene después (`:589-592`).
- **Los documentos** están a medias (r-1). La frase del inventario de la ronda 1 (`task-10c-inventory.md:142`, «antes de probar la clave, tres casos») sigue igual; la corrige la sección de la ronda 2 (`:238`), que vale como fe de erratas. Pero la respuesta (1) del informe de la ronda 1 (`task-10c-fix1-report.md:21` y `:97`) sigue diciendo que los dos salen siempre antes de probar la clave y que «no revelan nada». El informe de la ronda 2 la da por corregida.

### n-5, comprobado

- **El código:** `masterFailureMessage` usa `masterUnlockErrorText` (`vault/ContactsView.kt:445`), y su KDoc ya es verdad.
- **El test:** `ContactsViewTest.kt:578-585` cubre las tres constantes, un mensaje cualquiera y una `IOException`.
- **«Contraseña incorrecta» sale de un solo sitio:**
  - `WRONG_MASTER` (`vault/Vault.kt:280`);
  - el literal de `VaultBackup.kt:184` pasa a ser `WRONG_MASTER`;
  - el test de fuente `UnlockErrorTextTest.theWordsComeFromOnePlace` busca `"Contraseña incorrecta"` con sus comillas en todo `main`, así que no cuenta el texto por defecto de `CryptoFailure`.
- **La fila 43:** cerrada.

## n-3: la enumeración, repetida

Desde `app/src/main/java/app/seguridad` he lanzado las cuatro búsquedas del inventario: `ErrorText(`, los estados de error, `showSnackbar`/`onError`/`onMessage` y los `throw` o resultados de fallo del alcance. Salen **279 líneas únicas**; el inventario cuenta 281, y la diferencia es de líneas repetidas.

Después crucé con el inventario cada literal de esas líneas, de 6 caracteres o más, y cada constante en mayúsculas.

**Lo que no tiene fila:**
- **Constantes de límites o de formato**, que no son textos: `BACKUP_MAX_*`, `KEY_BYTES`, `VERSION`…
- **Contactos:** `BAD_IDENTITY`, `BAD_STORED_CARD`, `CARD_MISMATCH` y `NO_IDENTITY_TO_OPEN`, fuera de alcance.
- **«Cifrar texto»** (`crypto/Crypto.kt`): «Algoritmo desconocido», «Versión de formato no compatible», «Derivación de clave desconocida», «Esto se cifró con una clave…» y «El texto cifrado debe empezar por…». Fuera de alcance. Ninguno llega a la bóveda ni a las copias: el archivo de la bóveda no usa `Header` ni `Aead.of` (`vault/Vault.kt:258`, `:271`).
- **Constantes que solo aparecen por su grupo:** `FOLDER_NAME_TAKEN`, `VIEW_NAME_TAKEN`, `IMPORT_CSV_*` e `IMPORT_JSON_*` están en «`FOLDER_NAME_*`», «`VIEW_NAME_*`», «`IMPORT_CSV_*`» e «`IMPORT_JSON_*`» de la versión 1, y los `FINGERPRINT_UNLOCK_*`, en la ronda 1.
- **«Salud»** (`ui/screens/HealthScreen.kt:229`, «No se pudo completar la comprobación (…). Revisa la conexión.», y los de «Comprobación cancelada/terminada»). `HealthScreen.kt` está en la lista de pantallas de la búsqueda, pero no tiene fila ni aparece en la lista de fuera de alcance (r-3).

**La lista del revisor, una por una:**

| Elemento | Fila | ¿Motivo verdadero? |
|---|---|---|
| Activar la huella: `BiometricVault.kt:406`, `:431`, `:449`, `:464` y `:490` | Sí | Sí. Antes ya se ha reintentado sin StrongBox, y la bóveda sigue abriéndose con la maestra |
| `:457` | Sí | Sí |
| `:629`, «Has cambiado las huellas…» | Sí | Sí, ya dice qué hacer |
| `core/PickerLaunch.kt:4` y `:7` | Sí | Sí. `pickerLaunchError` solo lo usa `ui/screens/Pickers.kt:41` (`rememberDocumentPicker`), que comparten las pantallas de la bóveda y las herramientas (`PhotoClean`, `Redact`, `SecurityTools`, `PkActions`, `HashLab`, `QrInspect`…) |
| «No coinciden» | Sí | Sí. `.maestro/borra-datos/p19-crear-boveda-maestra-nueva.yaml:110` lo busca palabra por palabra, y `:131` comprueba que no está |
| `crypto/OtpMigration.kt:190-192` | Sí | Sí: es la lista de lo que no entra |
| Borrar una bóveda con la maestra equivocada | Sí | Sí. Pasa por `AppContainer.kt:688-690` y `:763`, y la fila 42 está al día |
| n-7, el texto del sistema | Sí | Sí |

## Issues nuevos

### Critical / Important

Ninguno.

### Minor

**r-1. El informe de la ronda 2 da por corregidas dos frases del informe de la ronda 1 que siguen igual** (n-2 y n-6). Las tres están igual en la copia de `docs/` y en la de `.superpowers/`:
- `task-10c-fix1-report.md:21` dice «Los dos errores salen antes de probar la clave, así que no revelan nada de la contraseña»;
- `:97` dice «Los dos salen antes de probar la clave»;
- `:104` dice «con `liveRegion` como antes».

**Arreglo:** corregirlas en las dos copias, con una nota «(corregido en la ronda 2, n-2 / n-6)», o quitar del informe de la ronda 2 «La respuesta (1) del informe de la ronda 1 … corregidas» y «Lo corregí en el informe de la ronda 1».

**r-2. Dos frases superadas en el inventario de la ronda 1.**
- `task-10c-inventory.md:138` sigue diciendo «Cada una está en una fila», que era falso (lo dijo la re-revisión 1, n-3).
- `:142` sigue con «antes de probar la clave, tres casos».

La sección de la ronda 2 lo corrige, pero quien lea de arriba abajo se encuentra la frase falsa primero. **Arreglo:** una nota al final de cada una, «(ronda 2: ver abajo)».

**r-3. «Salud» no tiene fila.** `HealthScreen.kt` está en la lista de pantallas del método (`task-10c-inventory.md`, «Ronda 2»), pero sus errores, de red (`:229`), no tienen fila ni salen como fuera de alcance. **Arreglo:** una línea en la sección 4: «Salud (comprobar filtraciones, red): fuera de alcance».

## Nada roto

- **A6:** sin cambios en `vault/AutoBackup.kt` ni `vault/A6Text.kt` en `00206fc..dcf28dc`.
- **`ToolTextsTest` y el anexo de la 1.3.0:** el anexo no cambia, y ninguna constante de `ALL_TOOL_TEXTS` cambia de valor.
- **Formatos, permisos, dependencias, red y registros:** sin cambios. Solo `checkSane`, `masterFailureMessage`, un literal pasado a constante, KDoc y tests. Los límites de `checkSane` son los mismos: solo cambia el mensaje de `cannotFit`.

## Revisión experto-seguridad-cripto

- **`checkSane` rechaza lo mismo** (`crypto/Crypto.kt:65-76`), antes de derivar y sin tocar la contraseña.
- **El orden es el seguro:** un encabezado fuera de los límites que además no cabe sigue siendo «no válido», que es lo que es.
- **Lo que delata el nuevo envoltorio.** Un `KDF_OUT_OF_MEMORY` al volver a envolver (`vault/Vault.kt:512`) solo sale con la maestra buena. Lo dice el KDoc (`Vault.kt:287-290`), y la fuga es despreciable: es lo que sabe quien abre la bóveda. El archivo no cambia y la bóveda queda bloqueada, como prueba `noMemoryWhenWrappingAgainLeavesTheFileAsItWas`.
- **La lista cerrada** sigue sin enseñar ningún `e.message` cualquiera, también en Contactos (`vault/ContactsView.kt:445`).

## Revisión Ingeniero Móvil Senior

- **Sin cambios de hilos, estado ni composición.** `checkSane` es puro, y `masterFailureMessage` es una función pura que usa `ContactsScreen.kt:145`.
- **Los tests no dependen del *heap* real:** lo inyectan (`heapKiB = …`) o inyectan el coste (`kdf = { … }`).
- **TalkBack** de la línea de error de la pantalla de bloqueo, en la fila 44 del móvil (`VaultScreen.kt:478`).

## Textos para el Abogado Digital (revisión final)

1. Una bóveda, una copia o un dato cifrado con un coste que este móvil no puede ejecutar dice ahora «Este móvil no tiene memoria suficiente para el coste «Alto» de Argon2id». Antes decía «Parámetros de cifrado no válidos». Ver la fila 38: no es «Alto» para un coste hecho a mano entre 64 y 128 MiB.
2. Contactos, al comprobar la maestra, dice lo mismo que la pantalla de bloqueo.
3. Se dejan sin cambiar, cada uno con su motivo en el inventario: «No coinciden», los textos de activar la huella, el texto del sistema del diálogo de la huella, `NO_FILE_PICKER` y `PICKER_FAILED`.
