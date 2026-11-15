# Tarea 10, fase C: ronda 1 de arreglos

## Estado

**Hecha.** Base `b1c9d35`; el HEAD de partida, `45203f4`, solo traía documentos. Las dos suites pasan por separado, con `./gradlew --stop` entre ellas (las cifras, abajo). Sin push.

No cambia nada de formatos, permisos, dependencias, red ni registros. Los textos de A6 quedan palabra por palabra.

## Commits

| Commit | Qué |
|---|---|
| `9edb5d2` | **I-2**, en su propio commit y el primero: `masterUnlockErrorText` y `KDF_PARAMS_INVALID` |
| `f04d321` | **I-1.3**: los fallos de la huella al desbloquear, con «Escribe la contraseña maestra.» |
| `bd29bc3` | **m-2** (`BACKUP_NEWER`), **m-3** y **m-5** en `pendientes.md`, y las filas 41 a 43 |
| `8c2592e`, `62ac400` | El inventario: su búsqueda, las filas que faltaban y los motivos corregidos (I-1, I-2, m-1, m-3, m-4, m-7) |
| `00206fc` | «2.0, tarea 10 (fase C): ronda 1 de arreglos» (cierre; este informe no entra en git) |

## I-2: las cuatro comprobaciones de antes

**(1) Los dos errores salen antes de probar la clave, así que no revelan nada de la contraseña.**
- **`KDF_PARAMS_INVALID`:** sale de `checkSane` (`crypto/Crypto.kt:73`). Se llama en `VaultCrypto.unlock` antes de leer la sal y de derivar (`vault/Vault.kt`, `val kdf = file.kdf.params().also { it.checkSane(heapKiB) }`), y otra vez al principio de `argon2id` (`Crypto.kt:231`). Solo depende del encabezado y del *heap*, nunca de la contraseña.
- **`KDF_OUT_OF_MEMORY`:** sale de `outOfMemoryAsFailure`, alrededor de `Argon2BytesGenerator().init(params).generateBytes` (`Crypto.kt:243`). La memoria que se pide depende solo de los parámetros, no del valor de la contraseña, así que la misma contraseña, buena o mala, da el mismo resultado. Además va antes de abrir la clave envuelta (`openAny`).

**(2) Qué da «Parámetros de cifrado no válidos», y si es verdad en la pantalla de bloqueo.** Hay dos condiciones, las dos en el encabezado del archivo de la bóveda:
- `outOfBounds`: memoria fuera de 8·p..128 MiB, pasadas fuera de 1..10 o carriles fuera de 1..4. Es un archivo editado, dañado o de otra herramienta: los parámetros no son válidos. **Verdad.**
- `cannotFit`: más de 64 MiB y más de 3/4 del *heap*. Es una bóveda «Alto» en un móvil que no puede ejecutarla. Los parámetros valen, pero no en este móvil. **Es verdad leído como «no válidos aquí»**, y sobre todo no empuja a «He olvidado», que es lo que importaba.

Era un literal y ahora es la constante `KDF_PARAMS_INVALID` (`Crypto.kt:84`), con el mismo valor.

**(3) Qué dice `KDF_OUT_OF_MEMORY`.** «Este móvil no tiene memoria suficiente para el coste «Alto» de Argon2id» (`Crypto.kt:91`). No nombra ninguna acción, así que no hay nada que no se pueda hacer desde la pantalla de bloqueo.

Un matiz: si un móvil muy pequeño se quedara sin memoria con el coste «Estándar», el texto nombraría «Alto» sin ser ese el caso. Ya era así antes de esta fase, en todos los sitios donde sale, y queda en la fila 38 de `pendientes.md`.

**(4) Los demás sitios que convierten en texto un `CryptoFailure` de la bóveda, y qué enseña cada uno con esos dos errores:**
- **«Importar copia»** (`ui/screens/VaultBackup.kt:183`): `e.message`, así que enseña los dos tal cual.
- **«Importar como bóveda nueva»** (`AppContainer.kt:763`, el texto del almacén): tal cual.
- **«Comprobar una copia»** (`vault/BackupCheckSession.kt:130`): tal cual.
- **Borrar una bóveda** (`AppContainer.deleteVault` → `confirmMaster`/`unlock`, enseñado por `runVaultsAction`): el texto del almacén, tal cual.
- **Crear o cambiar la maestra** (`newPasswordFailureMessage`, `Crypto.kt:194`): enseña `KDF_OUT_OF_MEMORY` y `PASSWORD_NOT_TEXT`. Un `KDF_PARAMS_INVALID` aquí no puede salir del archivo: el perfil del propio móvil siempre cabe (`availableOn`).
- **La huella** (`biometricUnlockError`): no deriva, así que no puede dar ninguno de los dos.
- **Contactos** (`masterFailureMessage`, `vault/ContactsView.kt:442`): da «Contraseña incorrecta» a todo, también a los dos. Es el mismo fallo que I-2, en una pantalla fuera de alcance: fila 43 de `pendientes.md`.

## La búsqueda del inventario (I-1)

Está escrita en `task-10c-inventory.md`, sección «Ronda 1», y copiada a `docs/superpowers/handoff/sdd-2.0/`.

- **Qué busca:** un patrón de literales con «No se », «no se ha», «no se puede», «no es», «dañad», «no válid», «incorrect», «bloqueó», «pasa(n) de», «no cabe», «demasiad», «Ya hay», «ya no», «no responde», «no está», «No hay» y «Falta».
- **Dónde:** `vault/`, `imports/`, `crypto/Crypto.kt`, `android/BiometricVault.kt`, `AppContainer.kt` y doce pantallas.
- **A mano:** cada `throw CryptoFailure(`, cada `throw AttachmentLimit(` y cada `catch (e: CryptoFailure)`.
- **Resultado:** 180 coincidencias en 30 archivos. Cada una tiene su fila, o queda fuera de alcance por ser de Contactos.

## Hallazgo → arreglo → `archivo:línea` → test

| Hallazgo | Arreglo | `archivo:línea` | Test (y su rojo) |
|---|---|---|---|
| **I-2** | `masterUnlockErrorText`: una lista cerrada. `NOT_A_VAULT`, `KDF_OUT_OF_MEMORY` y `KDF_PARAMS_INVALID` pasan tal cual; todo lo demás es `WRONG_MASTER` («Contraseña incorrecta», palabra por palabra), nunca un `e.message` cualquiera. La pantalla de bloqueo la usa | `vault/Vault.kt:290`; `ui/screens/VaultScreen.kt:454`; `crypto/Crypto.kt:84` | `UnlockErrorTextTest`: `whatTheStoreTellsApartIsSaid`, `impossibleParametersAreSaid` (un encabezado con `memoryKiB` imposible en un almacén de verdad) y `aWrongPasswordIsStillAWrongPassword`. Los dos primeros, rojos con el código de antes. El test de fuente F2 de `VaultStoreTest`, al día |
| **I-1.3** | Los literales de `BiometricVault.unlock` y `unlockSteps` (solo desde `VaultScreen.kt:429`) pasan a `FINGERPRINT_UNLOCK_UNUSABLE`, `_FAILED` y `_NOT_ENABLED`, con «. Escribe la contraseña maestra.». El `:472` (activar) no cambia | `android/BiometricVault.kt:560`, `:574`, `:600`, `:602-603`, `:632`, `:643`, `:648`; `vault/VaultStore.kt:78-80` | `ErrorTextsTest.theFingerprintsFailuresAtTheGate` (rojo con los valores de antes), que además comprueba que el de activar sigue igual |
| **I-1.1, I-1.2** | Se dejan. La constante por defecto de `CryptoFailure` y `KDF_PARAMS_INVALID` son las mismas de «Cifrar texto». El texto propio de la copia va a la fila 42; el de después de la huella, a la fila 41, citando que la 1.3.1 «da el error de siempre» (CRY-A02) | — | — |
| **I-1.4 a I-1.6** | Una fila por texto en el inventario, juzgada con las mismas reglas. Ninguno se puede arreglar solo con cadenas y siendo verdad en todos sus sitios: o ya dicen qué hacer, o no tienen una causa conocida, o están fijados (`ImportTextsTest`, R7) | Inventario, «Filas nuevas (I-1)» | — |
| **m-1** | Motivos corregidos: las cinco «comparadas con la misma constante, se dejan por el Ruling al pie de la letra»; los `IMPORT_COPY_*` no son datos y se juzgaron fila a fila; en `ImportTexts.kt`, solo seis fijados palabra por palabra, y el resto se sostiene por «Elegir otro archivo» | Inventario | — |
| **m-2 (b)** | `BACKUP_NEWER`: «La copia es de una versión más nueva de Clavero. Actualiza Clavero.», sin «para abrirla». Corregidos los comentarios | `vault/Backup.kt:80-82`; `ErrorTextsTest.kt:18` | `ErrorTextsTest.eachSaysWhatHappenedThenWhatToDo`: rojo con «para abrirla» |
| **m-3** | Fila 37 de `pendientes.md` corregida: los dos sitios por separado y el caso de un solo adjunto con un tamaño fuera de 0..5 MB o negativo. Marcada de veracidad, para la revisión de la rama | `pendientes.md`, fila 37 | — |
| **m-4** | Se deja con el motivo corregido. En los colores, la etiqueta se **elige** de una lista (`VaultOrganize.kt:438`); en «Etiquetar» solo falla con el nombre vacío. «Escribe un solo nombre, sin comas» no sería verdad en ninguno de los dos | Inventario | — |
| **m-5** | Fila 40 de `pendientes.md`: `README.md:320` (`COPY_DAMAGED`), junto a la 329 | `pendientes.md`, fila 40 | — |
| **m-6** | Corregida la duda 1 de `task-10c-report.md`: un `\n` rompería `ToolTextsTest`, que lee el anexo línea a línea. Ahora es un Ruling | `task-10c-report.md` | — |
| **m-7** | El inventario cita R-10 del Abogado inicial. `UPGRADE_EXPORT_FAILED` va para el Abogado | Inventario | — |

## Textos nuevos o cambiados

| Texto | Cuándo se ve | `archivo:línea` | Test |
|---|---|---|---|
| `NOT_A_VAULT`, `KDF_OUT_OF_MEMORY` y «Parámetros de cifrado no válidos» en la pantalla de bloqueo (antes salía «Contraseña incorrecta») | Desbloquear con la maestra, en esos tres casos | `vault/Vault.kt:290`; `ui/screens/VaultScreen.kt:454` | `UnlockErrorTextTest` |
| «No se pudo usar la huella. Escribe la contraseña maestra.» | Huella al desbloquear: Keystore, Cipher.init o el diálogo sin `CryptoObject` | `vault/VaultStore.kt:78` | `ErrorTextsTest.theFingerprintsFailuresAtTheGate` |
| «No se pudo desbloquear con la huella. Escribe la contraseña maestra.» | Huella al desbloquear: `doFinal`, o la entrega de la clave | `vault/VaultStore.kt:79` | Ídem |
| «La huella no está activada. Escribe la contraseña maestra.» | Huella al desbloquear sin los datos guardados | `vault/VaultStore.kt:80` | Ídem |
| «La copia es de una versión más nueva de Clavero. Actualiza Clavero.» (antes, «… para abrirla.») | Una copia con una marca más nueva | `vault/Backup.kt:82` | `ErrorTextsTest` |

Ninguno está en `.maestro/`, en el README ni en el anexo de la 1.3.0. `ToolTextsTest` sigue en verde.

## Filas nuevas o cambiadas de `pendientes.md`

- **37:** reescrita (m-3), de veracidad.
- **40:** con `README.md:320` (m-5).
- **41:** el texto por defecto de `CryptoFailure` tras la huella, para la revisión final (CRY-A02, «da el error de siempre»).
- **42:** el texto por defecto de `CryptoFailure` y `KDF_PARAMS_INVALID` en las copias; un texto propio necesita código en tres sitios.
- **43:** Contactos (`masterFailureMessage`) tapa `KDF_OUT_OF_MEMORY` y `KDF_PARAMS_INVALID`, como I-2.

## Tests

- `:app`: **BUILD SUCCESSFUL**: 2366 tests, 0 fallos; assemble, lint, licencias, Fragment y assets en verde (sobre `62ac400`).
- jvm-check: **BUILD SUCCESSFUL**: 2322 tests, 0 fallos (después de `./gradlew --stop`).

## Revisión experto-seguridad-cripto

- **I-2, el riesgo que se quita.** Con la maestra buena, una bóveda «Alto» en un móvil sin memoria decía «Contraseña incorrecta», y el usuario podía pulsar «He olvidado la contraseña maestra» y borrarla. Ahora dice lo que pasa (`ui/screens/VaultScreen.kt:454`).
- **Los textos nuevos no filtran nada.** Los dos salen antes de probar la clave y no dependen de la contraseña (respuesta 1). «Contraseña incorrecta» sigue tapando, deliberadamente, la diferencia entre una contraseña equivocada y una clave envuelta dañada (`Crypto.kt:197-198`).
- **Lista cerrada.** `masterUnlockErrorText` nunca enseña el mensaje de una excepción cualquiera (LIBRE-4), cosa que prueba `whatTheStoreTellsApartIsSaid` con un mensaje inventado (`vault/Vault.kt:290`).
- **La huella.** Los textos dicen qué hacer y no cambian cuándo se apaga la huella: eso sigue siendo solo `StaleBiometricKey` (CRY-A02). Activar la huella no cambia (`android/BiometricVault.kt:472`).
- **Nada más se toca:** ni formatos, ni la AAD, ni los parámetros, ni la red.

## Revisión Ingeniero Móvil Senior

- **La pantalla de bloqueo** sigue pintando el error en la misma línea (`error`), con `liveRegion` como antes. Solo cambia la función que elige el texto.
- **`BiometricVault`.** Las constantes viven en `vault/` (Kotlin puro), así que se prueban en las dos suites. El archivo Android solo las importa.
- **Limpieza.** La constante privada repetida `WRONG_MASTER` de `ContactsView.kt` se quita, y se usa la pública (`vault/Vault.kt:280`), con el mismo valor.

## Textos para el Abogado Digital (revisión final)

1. **I-2.** La pantalla de bloqueo ya no dice «Contraseña incorrecta» cuando el archivo no es compatible, cuando el móvil no tiene memoria para el coste «Alto» o cuando los parámetros de cifrado no son válidos. Dice esos textos, que ya existían.
2. **Los tres fallos de la huella al desbloquear,** con «Escribe la contraseña maestra.».
3. **`BACKUP_NEWER`:** «… Actualiza Clavero.».
4. **`UPGRADE_EXPORT_FAILED`** («… Vuelve a intentarlo o sigue sin copia.»), a la luz de R-10: lo que cuesta seguir sin copia está a la vista en el mismo diálogo.
5. **Filas 41 y 43**, de veracidad.

## Dudas

Ninguna nueva. La duda 1 de la fase C es ya un Ruling.
