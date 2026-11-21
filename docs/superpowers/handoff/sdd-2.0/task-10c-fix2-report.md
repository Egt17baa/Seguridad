# Tarea 10, fase C: ronda 2 de arreglos

## Estado

**Hecha.** Base `00206fc`. El HEAD de partida, `f81d4ab`, solo traía documentos. n-4 lo hizo el controlador.

Las dos suites pasan por separado, con `./gradlew --stop` entre ellas; las cifras están abajo. No he hecho push.

No se tocan formatos, permisos, dependencias, red ni registros. Los textos de A6 quedan palabra por palabra.

## Commits

| Commit | Qué |
|---|---|
| `753ec1e` | **n-1**: `checkSane` separa «fuera de límites» de «sin memoria» |
| `ffceacc` | **n-2** (KDoc y tests de «sin memoria» al derivar y al volver a envolver) y **n-5** (Contactos usa `masterUnlockErrorText`; «Contraseña incorrecta» sale de un solo sitio) |
| `36c4523` | **n-3, n-6 y n-7** en el inventario, con el método por los sitios que enseñan errores; filas 38, 42 y 44 |
| `192d7b7` | «2.0, tarea 10 (fase C): ronda 2 de arreglos», con el inventario y este informe copiados a `docs/superpowers/handoff/sdd-2.0/` |

## n-1: las comprobaciones de antes de cambiar

### (a) Sitios que comparan o deciden por `KDF_PARAMS_INVALID` o `KDF_OUT_OF_MEMORY`

**En el código:**
- **`masterUnlockErrorText`** (`vault/Vault.kt:291`): deja pasar los dos tal cual, así que llegue el que llegue, enseña su texto.
- **`newPasswordFailureMessage`** (`crypto/Crypto.kt:201`): enseña `KDF_OUT_OF_MEMORY`; cualquier otro error, también `KDF_PARAMS_INVALID`, da el texto de reserva del que llama.
  - Se usa al crear la bóveda y al cambiar la maestra, siempre con el coste del perfil del móvil.
  - «Alto» solo se ofrece si `availableOn`: la mitad del *heap* tiene que bastar para 128 MiB, y entonces también cabe en 3/4.
  - Por eso ahí `cannotFit` no puede darse, y el cambio no cambia nada.
- **`checkArgon2id`** (`imports/ImportKdf.kt:83-90`): convierte cualquier `CryptoFailure` en `IMPORT_KDF_TOO_BIG`. Sin cambios.

**En los tests:**
- **`SettingsTest.assertRefused`**: esperaba «Parámetros de cifrado no válidos» también para «Alto» con 128 MiB de *heap*. Ese es justo el caso que cambia, y ahora espera `KDF_OUT_OF_MEMORY` (`highIsRefusedWhenTheHeapPlainlyCannotHoldIt`).
- **`KdfLockTest`, `PasswordBytesTest` y `SeedCryptoTest`**: no deciden por cuál de los dos llega.

**Conclusión:** ningún comportamiento depende de cuál llega; solo cambia el texto. No hacía falta parar.

### (b) Cada llamante de `checkSane`, con lo que enseñaba antes y lo que enseña después (caso «no cabe»)

| Llamante | Antes | Después |
|---|---|---|
| Abrir la bóveda: `VaultCrypto.unlock`, `vault/Vault.kt:374`, en la pantalla de bloqueo | «Parámetros de cifrado no válidos» (por `masterUnlockErrorText`) | `KDF_OUT_OF_MEMORY` |
| Importar copia, importar como bóveda nueva, comprobar una copia (la misma `VaultCrypto.unlock`) | Ídem (`e.message`) | `KDF_OUT_OF_MEMORY` |
| `argon2id` (`crypto/Crypto.kt:231`): cualquier derivación (crear, cambiar la maestra, volver a envolver, cifrar) | Ídem | `KDF_OUT_OF_MEMORY`, o el texto de reserva si pasa por `newPasswordFailureMessage` |
| «Cifrar texto» / «Cifrar archivo», encabezado de un dato cifrado (`crypto/Crypto.kt:505`) | «Parámetros de cifrado no válidos» (`e.message`) | `KDF_OUT_OF_MEMORY` |
| Importar de otra app (`imports/ImportKdf.kt:85`) | `IMPORT_KDF_TOO_BIG` | `IMPORT_KDF_TOO_BIG`, sin cambios |

Con unos parámetros fuera de los límites, todos siguen igual: `KDF_PARAMS_INVALID`.

**El «Alto» del texto.** Las exportaciones de otras apps nunca llegan a `KDF_OUT_OF_MEMORY`: `checkArgon2id` y el `kdf` de la importación lo convierten en `IMPORT_KDF_TOO_BIG`. En cambio, una copia o un dato cifrado con un coste hecho a mano entre 64 y 128 MiB (Clavero solo escribe 64 o 128) dirían «coste «Alto»» sin serlo. Queda en la fila 38, sin texto nuevo.

## n-2: `KDF_OUT_OF_MEMORY` también puede salir tras una maestra buena

- **KDoc de `masterUnlockErrorText`** (`vault/Vault.kt`) al día. `KDF_OUT_OF_MEMORY` puede salir:
  - en la derivación;
  - después de una maestra buena, al volver a envolver la clave con el coste de Ajustes (`afterMasterUnlock`, `vault/Vault.kt:512`).

  En ese segundo caso solo sale cuando la contraseña era buena.
- **La respuesta (1) del informe de la ronda 1 y la frase del inventario**, corregidas.
- **Tests** (`UnlockErrorTextTest`):
  - `aDerivationWithoutMemoryIsSaid`: 96 MiB con 96 MiB de *heap* inyectado da `KDF_OUT_OF_MEMORY`. En rojo con el `checkSane` de antes.
  - `noMemoryWhenWrappingAgainLeavesTheFileAsItWas`: el coste inyectado falla una vez al volver a envolver. Da `KDF_OUT_OF_MEMORY`, el `vault.bin` queda igual byte a byte, la bóveda sigue bloqueada y el siguiente intento abre.

  El almacén hace lo que se esperaba, así que no hubo que parar.

## n-5: Contactos

- **`masterFailureMessage`** (`vault/ContactsView.kt:445`) usa `masterUnlockErrorText`, con el KDoc corregido. `ContactsViewTest` cubre `KDF_OUT_OF_MEMORY`, `KDF_PARAMS_INVALID` y `NOT_A_VAULT`; se vio en rojo.
- **Fila 43 de `pendientes.md`**: cerrada.
- **«Contraseña incorrecta» sale de un solo sitio**, `WRONG_MASTER` (`vault/Vault.kt:280`). El literal sin uso de `ui/screens/VaultBackup.kt:184` pasa a ser `WRONG_MASTER`.
  - Test de fuente: `UnlockErrorTextTest.theWordsComeFromOnePlace`, en rojo con el literal de antes.

## n-3: el método de enumeración

Está escrito en el inventario, en «Ronda 2 del inventario», para repetirlo. No busca por palabras, sino por los sitios que enseñan errores:
1. cada `ErrorText(`;
2. cada estado de error que se pone;
3. cada `showSnackbar(`, `onError(` y `onMessage(`;
4. cada `throw` y cada resultado de fallo del alcance: bóveda, copias, importar, huella y `core/`.

Da 281 sitios. Cada uno se sigue hasta su cadena. Como cruce, un script lista lo que no está en el inventario, y además sigue el patrón de frases ampliado con «más de», «como mucho», «no coincide», «Has cambiado», «no tiene» y «no permite».

**Cada elemento de la lista del revisor tiene su fila, juzgada sola:**
- **«Activar huella»** (`BiometricVault.kt:406`, `:431`, `:449`, `:457`, `:464`, `:490`): se deja.
- **`:629`**: ya dice qué hacer.
- **`core/PickerLaunch.kt`**: se comparte con las herramientas.
- **«No coinciden»**: lo busca un flujo de Maestro, palabra por palabra.
- **`OtpMigration.kt:190-192`**: es la lista de lo que no entra.
- **Borrar una bóveda con la maestra equivocada** (`AppContainer.kt:688-690`, `:763`): dice «Contraseña incorrecta o datos dañados», que es verdad. Separarlo pide código (fila 42).

Ninguno se podía arreglar solo con cadenas y siendo verdad en todos sus sitios.

## n-6 y n-7

- **n-6:** la línea de error de `UnlockVault` no es una región viva (`ui/screens/VaultScreen.kt:478`). Lo corregí en el informe de la ronda 1 y lo apunté en la fila 44 (TalkBack en el móvil). No toqué el código.
- **n-7:** fila en el inventario para el texto de error del sistema en el diálogo de la huella (`BiometricVault.kt:641`). Se deja: lo escribe Android, no la app.

## Tests

Las dos suites se corrieron sobre `36c4523`:

- `:app`: **BUILD SUCCESSFUL**, 2370 tests y 0 fallos; assemble, lint, licencias, Fragment y assets, en verde.
- jvm-check: **BUILD SUCCESSFUL**, 2326 tests y 0 fallos, después de `./gradlew --stop`.

## Hallazgo → arreglo → `archivo:línea` → test

| Hallazgo | Arreglo | `archivo:línea` | Test (y su rojo) |
|---|---|---|---|
| n-1 | `outOfBounds` primero (`KDF_PARAMS_INVALID`); `cannotFit` da `KDF_OUT_OF_MEMORY` | `crypto/Crypto.kt:73-76` | `SettingsTest.checkSaneTellsBoundsFromMemory` (alto con *heap* pequeño, fuera de límites, las dos a la vez) y `highIsRefusedWhenTheHeapPlainlyCannotHoldIt`, los dos en rojo |
| n-2 | KDoc; respuesta (1) e inventario corregidos | `vault/Vault.kt` (KDoc de `masterUnlockErrorText`) | `UnlockErrorTextTest.aDerivationWithoutMemoryIsSaid` (rojo) y `noMemoryWhenWrappingAgainLeavesTheFileAsItWas` |
| n-5 | `masterFailureMessage` → `masterUnlockErrorText`; el literal de `VaultBackup` → `WRONG_MASTER` | `vault/ContactsView.kt:445`; `ui/screens/VaultBackup.kt:184` | `ContactsViewTest` (rojo) y `UnlockErrorTextTest.theWordsComeFromOnePlace` (rojo) |
| n-3 | El método y las filas nuevas | Inventario, «Ronda 2» | — |
| n-6, n-7 | El informe corregido, la fila 44 y una fila de inventario | — | — |

## Textos nuevos o cambiados

No hay cadenas nuevas. Lo que cambia es qué texto llega en un caso:
- Una cabecera dentro de los límites que no cabe en el *heap*, en la bóveda, las copias o «Cifrar texto» y «Cifrar archivo»: dice `KDF_OUT_OF_MEMORY` («Este móvil no tiene memoria suficiente para el coste «Alto» de Argon2id») en lugar de «Parámetros de cifrado no válidos».
- En Contactos, al comprobar la maestra, `NOT_A_VAULT`, `KDF_OUT_OF_MEMORY` y `KDF_PARAMS_INVALID` se dicen tal cual en lugar de «Contraseña incorrecta».

## Filas de `pendientes.md`

- **38:** ampliada (n-1). «Alto» no es verdad para un coste hecho a mano entre 64 y 128 MiB. Las exportaciones de otras apps no llegan aquí.
- **42:** al día con n-1.
- **43:** cerrada (n-5).
- **44:** nueva (n-6), TalkBack y la línea de error de la pantalla de bloqueo.

## Revisión experto-seguridad-cripto

- **n-1.** `checkSane` sigue rechazando lo mismo, antes de derivar y sin tocar la contraseña (`crypto/Crypto.kt:73-76`); solo cambia el mensaje. El orden importa: un encabezado fuera de los límites que además no cabe se lee como «no válido», que es la verdad más fuerte. Ningún límite cambia.
- **n-2.** Que `KDF_OUT_OF_MEMORY` salga después de una maestra buena (`afterMasterUnlock`) dice que la contraseña era buena. No es un oráculo que alguien pueda usar: solo pasa cuando el coste de Ajustes no cabe en el móvil, y ese coste lo elige quien tiene la bóveda abierta. Además, el archivo no cambia y la bóveda queda bloqueada (el test lo comprueba byte a byte).
- **n-5.** Contactos ya no empuja a creer que la maestra es mala cuando no lo es. Ningún texto nuevo lleva datos (`vault/ContactsView.kt:445`).

## Revisión Ingeniero Móvil Senior

- **El cambio es una función pura, `checkSane`,** con un `if` más. No cambia hilos, estado ni pantallas.
- **Los tests no dependen del *heap* real:** inyectan el *heap* (`checkSane(heapKiB = …)`, `VaultCrypto.unlock(…, heapKiB = …)`) o el coste (`kdf = { … }`).
- **TalkBack** (n-6) queda para el móvil, en la fila 44, sin tocar `VaultScreen.kt`.

## Textos para el Abogado Digital (revisión final)

1. **n-1:** una bóveda, copia o dato cifrado con un coste que este móvil no puede ejecutar dice ahora `KDF_OUT_OF_MEMORY`, que nombra «Alto». La fila 38 explica cuándo no es «Alto».
2. **n-5:** Contactos dice lo mismo que la pantalla de bloqueo.
3. **Sin cambiar** (el inventario explica por qué): «No coinciden», los textos de activar la huella, el texto del sistema del diálogo de la huella y `NO_FILE_PICKER` y `PICKER_FAILED`.

## Dudas

Ninguna.
