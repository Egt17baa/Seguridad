# Re-revisión 1 de la tarea 10, fase C (`b1c9d35..00206fc`)

He comprobado sobre el código, sin fiarme del informe (`task-10c-fix1-report.md`):
- el diff, el inventario nuevo y `pendientes.md` (filas 37 a 43);
- el «Ruling (advisor, T10 fase C)» (`progress.md:208`).

`d52051a`, encima de `00206fc`, solo toca documentos.

He corrido en jvm-check `ErrorTextsTest`, `UnlockErrorTextTest` y `ToolTextsTest`: los tres en verde. La suite entera no.

## Veredicto

**Aprobada con cambios.**

I-2 e I-1.3 están bien hechos:
- la lista es cerrada;
- «Contraseña incorrecta» se conserva palabra por palabra;
- los literales de la huella solo se alcanzan desde `unlock()`;
- el de activar no cambia.

Quedan pendientes:
- **un Important nuevo, de veracidad:** «Parámetros de cifrado no válidos» ante una copia «Alto» válida en un móvil pequeño;
- **una afirmación falsa en la respuesta (1)**, repetida en un KDoc;
- **varios menores:** huecos en la búsqueda del inventario, m-6 solo a medias y la fila 43, que yo arreglaría ya.

## Estado de cada hallazgo

| Hallazgo | Estado | Evidencia |
|---|---|---|
| **I-1** | Casi cerrado; quedan huecos (n-3) | Repetí la búsqueda escrita y da lo mismo: 180 coincidencias en 30 archivos. Pero «cada una está en una fila» no es verdad, y la búsqueda no ve algunos textos. Ver n-3 |
| I-1.3, la huella al desbloquear | **Cerrado** | `BiometricVault.unlock` solo se llama desde `ui/screens/VaultScreen.kt:429`. Las constantes salen solo en `unlock` y `unlockSteps` (`android/BiometricVault.kt:548-650`; nada después de ellas en el archivo). El de activar sigue igual (`:472`, comprobado por `ErrorTextsTest.kt:61-62`). Sobre el rojo: solo sé que las constantes no existían, así que el test no compilaba. No he podido confirmar un rojo de comportamiento |
| **I-2** | **Cerrado en el código**, con n-1 y n-2 | `masterUnlockErrorText` (`vault/Vault.kt:290-293`) es una lista cerrada: tres constantes pasan, y todo lo demás da `WRONG_MASTER` («Contraseña incorrecta», `:280`). Nunca un `e.message`. Lo usa `VaultScreen.kt:454`. `KDF_PARAMS_INVALID` tiene el mismo valor que el literal de antes (`crypto/Crypto.kt:84`). Las dos filas del inventario están corregidas |
| m-1 | Cerrado | Motivos corregidos en el inventario, «Filas corregidas» |
| m-2 | Cerrado | «La copia es de una versión más nueva de Clavero. Actualiza Clavero.» (`vault/Backup.kt:82`) es verdad: solo sale cuando la marca pasa de `BACKUP_NEWEST` (`Backup.kt:327`), y actualizar es la única acción posible. Ya no promete nada. Los dos comentarios, al día |
| m-3 | Cerrado | Fila 37 reescrita y marcada de veracidad |
| m-4 | Cerrado | Confirmado: `TagColorsDialog` recibe `tags: List<String>`, las etiquetas que ya hay, y llama a `setTagColor` (`ui/screens/VaultOrganize.kt:430-438`). La etiqueta se elige, no se escribe. El motivo nuevo es verdad |
| m-5 | Cerrado | La fila 40 lleva `README.md:320` y `:329` |
| m-6 | **A medias** (n-4) | Solo se corrigió la copia de trabajo |
| m-7 | Cerrado | El inventario cita R-10; `UPGRADE_EXPORT_FAILED` va al Abogado |
| m-8 | Informativo | Sin cambio |

## Respuestas (1) a (4) de I-2, comprobadas

**(1) «Los dos errores salen antes de probar la clave».** Es **verdad en `VaultCrypto.unlock`**:
- `checkSane` va antes de leer la sal (`vault/Vault.kt:374`);
- el OOM se produce al reservar la memoria de Argon2, que solo depende de los parámetros (`crypto/Crypto.kt:237-243`), y antes de `openAny`.

Pero es **falso en `afterMasterUnlock`** (`vault/Vault.kt:498-516`). Este código vuelve a envolver la clave **después** de que la maestra abriera la bóveda, con un Argon2id de coste `max(archivo, perfil)`, siempre que:
- el archivo sea de un formato viejo, que es **el primer desbloqueo de todo usuario que viene de la 1.4.3**; o
- el perfil cueste más.

Ese segundo Argon2id puede dar `KDF_OUT_OF_MEMORY` con la maestra **buena**. Ver n-2.

`KDF_PARAMS_INVALID` no puede salir ahí:
- el perfil `kdf()` es el ajuste ya saneado (`AppContainer.kt:409`), y `SettingsStore.kt:177` baja «Alto» a «Estándar» donde no cabe (`Settings.kt:423`, `availableOn`);
- el archivo ya pasó `checkSane` en `unlock`;
- el máximo de dos costes que pasan, eje por eje, también pasa.

Por tiempo no se filtra nada: la derivación de la clave dura lo mismo con cualquier contraseña, y lo único que cambia de una rama a otra es lo que ya enseña el texto.

**(2) Las condiciones de `KDF_PARAMS_INVALID`** son dos (`crypto/Crypto.kt:65-73`):
- **`outOfBounds`:** memoria fuera de 8·p a 128 MiB, pasadas fuera de 1 a 10 o carriles fuera de 1 a 4. Es un archivo editado o dañado. El texto es verdad.
- **`cannotFit`:** más de 64 MiB y más de 3/4 del *heap*. Los parámetros **son válidos**, pero no caben en este móvil. «Parámetros de cifrado no válidos» es falso ahí, y leerlo «como no válidos aquí» estira las palabras. Ver n-1.

En la pantalla de bloqueo, `cannotFit` casi no se alcanza: `allowBackup="false"` y `device-transfer` lo excluyen todo (`AndroidManifest.xml:11`, `res/xml/data_extraction_rules.xml`). Una bóveda «Alto» solo llega a un móvil pequeño a través de una copia. Pero justo ahí, al importar o comprobar esa copia, el caso es real.

**(3) `KDF_OUT_OF_MEMORY`** no nombra ninguna acción. Es verdad que dice «Alto» aunque falle con «Estándar»: ya era así antes y está en la fila 38. Conforme.

**(4) Los demás sitios** que convierten el fallo en texto: la lista del informe es correcta.
- `VaultBackup.kt:183`, `AppContainer.kt:763` y `BackupCheckSession.kt:130` enseñan los dos errores tal cual.
- `newPasswordFailureMessage` (`Crypto.kt:194`) enseña `KDF_OUT_OF_MEMORY`.
- La huella no deriva.
- Contactos da «Contraseña incorrecta» a todo (n-5).

**Los tests:**
- `whatTheStoreTellsApartIsSaid` estaba en rojo (la función no existía).
- `impossibleParametersAreSaid` pasa por el almacén de verdad, pero **solo con `outOfBounds`**.
- El Ruling pedía «un `kdf` inyectado que falla». No hay ningún test que lleve `KDF_OUT_OF_MEMORY` ni `cannotFit` a través de `store.unlock`. Bastaría con inyectar como `kdf` un perfil fuera de límites, para cubrir el camino de `afterMasterUnlock`. Ver n-2.

## Issues nuevos

### Critical

Ninguno.

### Important

**n-1. Veracidad: «Parámetros de cifrado no válidos» ante unos parámetros válidos (`cannotFit`).** `crypto/Crypto.kt:72-73` da `KDF_PARAMS_INVALID` también cuando el coste es válido y solo no cabe en este móvil.

El caso real: alguien pasa a un móvil con menos memoria y quiere importar su copia «Alto». «Importar copia» (`VaultBackup.kt:183`), «Importar como bóveda nueva» (`AppContainer.kt:763`) y «Comprobar una copia» (`BackupCheckSession.kt:130`) le dicen «Parámetros de cifrado no válidos». Eso se lee como una copia dañada, y no lo está. Es lo mismo que pasaba con I-2.

La fila nueva del inventario («Se deja: es verdad en las dos») y la fila 42 lo dan por bueno.

**Arreglo (una línea de código, la misma clase que I-2):** en `checkSane`, `if (outOfBounds) throw CryptoFailure(KDF_PARAMS_INVALID)` y `if (cannotFit) throw CryptoFailure(KDF_OUT_OF_MEMORY)`. `cannotFit` exige más de 64 MiB, así que «no tiene memoria suficiente para el coste «Alto»» es verdad. Además:
- `KDF_OUT_OF_MEMORY` ya está en la lista cerrada;
- `newPasswordFailureMessage` ya lo enseña;
- `checkArgon2id` de la importación lo convierte todo en `IMPORT_KDF_TOO_BIG` (`imports/ImportKdf.kt:83-88`), así que allí no cambia nada;
- en «Cifrar texto» también sería verdad.

Hay que comprobar `SeedCryptoTest.kt:161-168` (solo espera una `CryptoFailure`) y añadir un test de `cannotFit` con un `heapKiB` pequeño.

**Esto reabre un Ruling:** `progress.md:208` dejó el texto de `checkSane` en las copias como «se deja», con la fila 42. Así que el hallazgo tiene dos partes:
- **Lo que hay que corregir ya:** la frase falsa «es verdad en las dos» del inventario («Filas nuevas (I-1)», fila de `KDF_PARAMS_INVALID»`), y la fila 42. Las dos tienen que decir que el texto es falso en `cannotFit` y marcarse de veracidad.
- **Lo que necesita un Ruling nuevo:** el cambio de una línea en `checkSane`. Yo lo haría en esta fase, porque es la misma clase que I-2.

### Minor

**n-2. La respuesta (1) y el KDoc de `masterUnlockErrorText` afirman algo falso.** `vault/Vault.kt:283-284` dice «What the store tells apart before the key is tried, so nothing of the password is in it». No es verdad en el nuevo envoltorio de `afterMasterUnlock` (`Vault.kt:512`), que deriva **después** de comprobar la maestra (ver (1)).

- **El efecto:** un `KDF_OUT_OF_MEMORY` en ese punto delata que la maestra era buena. `KDF_PARAMS_INVALID` no puede salir ahí (ver (1)).
- **Lo que se filtra es despreciable:** quien escribe la maestra buena lo sabría igual al abrirse la bóveda. Solo el caso de un fallo que se repite siempre deja saber que es la buena sin abrir.
- **Sale mejor que antes:** antes decía «Contraseña incorrecta» con la maestra buena.

**Arreglo:**
- corregir el KDoc, la respuesta (1) y la frase del inventario («Filas corregidas», I-2: «El almacén distingue, antes de probar la clave, tres casos»): «antes de probar la clave en `unlock`; en el nuevo envoltorio de `afterMasterUnlock`, después, con la maestra ya comprobada, y solo `KDF_OUT_OF_MEMORY`»;
- añadir el test con un `kdf` inyectado, como pedía el Ruling.

Si se quiere que un fallo del nuevo envoltorio no impida abrir (abrir sin volver a envolver), es una decisión de diseño: a `pendientes.md`.

**n-3. La búsqueda del inventario no lo encuentra todo, y «cada una está en una fila» es falso.**
- **Coincidencias sin fila:**
  - los errores de **activar** la huella, `android/BiometricVault.kt:406`, `:431`, `:449`, `:464`, `:472` y `:490` en `00206fc` («No se pudo activar la huella», «No se pudo usar la huella»). Son de los 180 y solo se nombra el `:472`.
  - el resultado de **borrar una bóveda** con la maestra equivocada. Lo que dice la búsqueda a mano no lo cubre. `AppContainer.deleteVault` llama a `confirmMaster` o `unlock` (`AppContainer.kt:688-690`), y `runMarked` enseña `e.message` (`:762-763`). Con una maestra equivocada sale «Contraseña incorrecta o datos dañados», no el `WRONG_MASTER` de la pantalla de bloqueo. Con los dos errores de Argon2id sale su texto. Le falta una fila (con su motivo: el texto por defecto, compartido, como la fila 42).
- **Textos que el patrón no ve:**
  - `android/BiometricVault.kt:629`, «Has cambiado las huellas del móvil: entra con la contraseña maestra…», que es del desbloqueo y ya dice qué hacer;
  - `:457`, «Este móvil no permite guardar la clave con huella», al activar;
  - `core/PickerLaunch.kt:4` y `:7` (`NO_FILE_PICKER`, `PICKER_FAILED`): salen al elegir una copia, una carpeta o un adjunto, y la carpeta `core/` no se busca;
  - «No coinciden» (`ui/screens/VaultScreen.kt:368`, `VaultBackup.kt:253`, `VaultPickerScreen.kt:426`);
  - el motivo de salto de `crypto/OtpMigration.kt:192`, al importar 2FA en el formulario.

**Arreglo:**
- añadir al patrón `más de |como mucho|no coincide|No coinciden|Has cambiado|no tiene|no permite`;
- añadir `core/PickerLaunch.kt` a las carpetas;
- dar una fila a cada uno. Casi todos se dejan, porque ya dicen qué hacer, son de activar (Ajustes) o son validación, pero con su motivo.

**n-4. m-6 solo está en la copia de trabajo.** `.superpowers/sdd/…/task-10c-report.md:122` está corregido. La copia que entra en git, `docs/superpowers/handoff/sdd-2.0/task-10c-report.md:122`, sigue diciendo «basta un `\n` en los seis», y la `:118` sigue con «Actualiza Clavero para abrirla.». Hay que copiar la versión corregida a `docs/`.

**n-5. La fila 43 (Contactos) es la misma clase que I-2. La arreglaría ya.** `masterFailureMessage` (`vault/ContactsView.kt:442`) da «Contraseña incorrecta» a toda `CryptoFailure`, y su KDoc (`:441`) dice «Lo mismo que dice la pantalla de desbloqueo». Desde I-2, eso es falso.

El caso es real: con la bóveda abierta con la huella, `confirmMaster` (`ui/screens/ContactsScreen.kt:141`) deriva con Argon2id. En un móvil justo de memoria, eso da `KDF_OUT_OF_MEMORY`, y el usuario lee «Contraseña incorrecta» con la maestra buena. Ahí no hay botón «He olvidado», así que no se borra nada, pero el texto es falso.

**Arreglo de una línea:** `if (e is CryptoFailure) masterUnlockErrorText(e) else NOT_OPENED`. Sin `NOT_A_VAULT` posible con la bóveda abierta. Más un test. Que esté «fuera de alcance» porque es de Contactos no se sostiene: la función es de `vault/`, el arreglo es la misma función que I-2, y el KDoc ya miente.

**n-6. El informe dice algo falso sobre la pantalla.** `task-10c-fix1-report.md`, «Revisión Ingeniero Móvil Senior»: «con `liveRegion` como antes». La línea de error de `UnlockVault` es `error?.let { Text(it, color = NegativeColor) }` (`ui/screens/VaultScreen.kt:478`), sin región viva (ver mi revisión, fila 34). Hay que corregir la frase.

**n-7 (informativo).** Un `PromptAnswer.Error` pasa el texto del sistema sin línea de qué hacer (`android/BiometricVault.kt:641`). Es texto de Android, y se acepta. Merece una fila en el inventario.

## Las filas nuevas del inventario y las filas 41 y 42

Repetí también la parte a mano de la búsqueda: cada `catch (e: CryptoFailure)`, cada `as? CryptoFailure` y cada `throw CryptoFailure(…)` de `vault/`. Lo único que falta es el borrado de una bóveda (n-3).

| Fila | Motivo | ¿Verdad? |
|---|---|---|
| Texto por defecto de `CryptoFailure` | Constante compartida con «Cifrar texto»; filas 41 y 42 | Sí. La fila 41 cita bien CRY-A02 («da el error de siempre»), y la fila 42 cita los tres sitios correctos |
| `KDF_PARAMS_INVALID` | «Es verdad en las dos» | **No**: es falso en `cannotFit` (n-1) |
| La huella al desbloquear | Cambian | Sí (arriba) |
| Los tres «La bóveda se bloqueó…» de los adjuntos (`VaultAttachments.kt:71`, `:92`, `:171`) | Lo que pasó ya dice qué hacer | Sí. Van juntos en una fila, pero con la misma condición y sus tres líneas: no es «en bloque». Apoyarse en «la revisión los acepta» sobra |
| «El adjunto es demasiado grande» y «El adjunto está dañado» | No salen nunca | Sí. `Attachments.open` envuelve toda excepción en `CryptoFailure(ATTACHMENT_DAMAGED)` (`vault/Attachments.kt:130-133`), y `AttachmentLimit` siempre lleva mensaje |
| `VaultBackup.kt:93` y `:151` | Ya dice qué hacer; el bloqueo, aceptado | Sí |
| `VaultEntryScreen.kt:242` | Como `TRASH_SAVE_FAILED` | Sí |
| «El QR de Google Authenticator no es válido o está dañado» | Fijado en `ImportTextsTest` (R7) | Sí: está en la lista exacta (`ImportTextsTest.kt:46`) |
| `VaultEntryScreen.kt:422`, `:470`, `:482`, `:509`, `:701`, `:768` y `:774` | Ya dicen qué hacer, sin causa o informativos | Sí |
| `SETTING_SAVE_FAILED` y `SettingsScreen.kt:331` | Sin causa / ya dice qué hacer | Sí |
| `SAVE_LOCKED` y `HISTORY_ALL_LOCKED` | Bloqueo | Sí. Comparten una fila con la misma condición y cada uno con su línea |
| `ENTRY_IN_TRASH` y `EXPIRY_BAD_DATE` | Ya dicen qué hacer | Sí |
| `HISTORY_RESTORE_REFUSED` | «Tres casos, un solo texto» | Sí. `restoreVersion` da null también cuando la entrada ya no está (`VaultStore.kt:1230-1243`): son más de tres casos, y el motivo sigue valiendo |
| `TRASH_RESTORE_NONE` | De Contactos, y ya dice por qué | Sí |
| `BACKUP_ENTRY_TOO_BIG_V3` y «Id de adjunto no válido» | Topes / error del programa | Sí |
| El adjunto que falta al exportar y `attachmentNotInCopy` | Ya dice qué hacer / no lo distingue | Sí |
| Los problemas de «Comprobar una copia» y «Ya la tenían…» | Partes del informe / un resultado | Sí |
| `IMPORT_COPY_*` | Fila a fila | Sí. `ImportSession.kt:344-347`: `CopyFailed` vuelve a la vista previa con el error |
| `IMPORT_KDF_BUSY` | Ya dice qué hacer | Sí |
| `importTooBigText` | Vuelve a la vista previa, con «Importar» apagado | Sí (`ImportSession.kt:348-353`) |
| `SkipReason` | Las líneas de «No entran» | Sí |
| `UPGRADE_SAVE_FAILED` | O-3, ya dice qué hacer | Sí |
| Contactos | Fuera de alcance, con la fila 43 | El motivo es verdad, pero yo lo arreglaría ya (n-5) |

## Nada roto

- **A6:** `AutoBackup.kt` y `A6Text.kt`, sin cambios en el diff. `ErrorTextsTest.theFrozenOnesKeepTheirWords`, en verde.
- **Anexo y `ToolTextsTest`:** el anexo no cambia, y `BIOMETRIC_UNLOCK_FAILED` sigue igual. `ToolTextsTest`, en verde.
- **Formatos, permisos, dependencias, red y registros:** sin cambios. Solo constantes, `masterUnlockErrorText` y sus usos.
- **`ContactsView`:** la constante privada `WRONG_MASTER` pasa a la pública de `Vault.kt:280`, con el mismo valor.

## Revisión experto-seguridad-cripto

- **Lista cerrada (LIBRE-4).** `masterUnlockErrorText` nunca enseña un mensaje cualquiera (`vault/Vault.kt:290-293`; test con «lo que sea», `UnlockErrorTextTest.kt:32-34`).
- **Oráculo de la contraseña.** En `unlock`, los tres textos dependen solo del archivo, de sus parámetros y del móvil. En el nuevo envoltorio, después de comprobar la maestra, delatan que la maestra es buena (n-2). Riesgo despreciable: es lo mismo que sabe quien la abre. Pero el KDoc lo niega.
- **Veracidad.** `cannotFit` (n-1) es la misma clase que I-2: un texto que hace creer que algo válido está dañado.
- **Huella.** Ningún cambio en cuándo se apaga (solo `StaleBiometricKey`, CRY-A02). Los textos nuevos no llevan datos.
- **Formatos y primitivas.** Sin cambios.

## Revisión Ingeniero Móvil Senior

- **Desde Android.** `BiometricVault.kt` importa constantes de `vault/` (Kotlin puro), y se prueban en las dos suites (`ErrorTextsTest`).
- **Una prueba frágil.** El test de fuente cuenta apariciones desde `fun unlock(activity` hasta el final del archivo (`ErrorTextsTest.kt:53-59`). Hoy vale, porque después de `unlockSteps` no hay nada más, pero una función nueva al final lo rompería sin motivo. Sugiero cortar en el final de `unlockSteps` (es menor y no bloquea).
- **La pantalla de bloqueo.** El texto sale en la misma línea (`VaultScreen.kt:478`), a la vista y sin `maxLines`. Sin región viva (n-6; la fila 34 del móvil).

## Textos para el Abogado Digital (revisión final)

1. En la pantalla de bloqueo, según el caso: «No es una bóveda compatible», «Este móvil no tiene memoria suficiente para el coste «Alto» de Argon2id» y «Parámetros de cifrado no válidos». Antes, «Contraseña incorrecta».
2. «No se pudo usar la huella. Escribe la contraseña maestra.», «No se pudo desbloquear con la huella. Escribe la contraseña maestra.» y «La huella no está activada. Escribe la contraseña maestra.».
3. «La copia es de una versión más nueva de Clavero. Actualiza Clavero.».
4. n-1: «Parámetros de cifrado no válidos» ante una copia «Alto» válida en un móvil pequeño. Si se arregla, sale `KDF_OUT_OF_MEMORY`.
5. n-5: Contactos, «Contraseña incorrecta» con la maestra buena si falta memoria (fila 43).
6. Filas 41 a 43 de `pendientes.md`, y `UPGRADE_EXPORT_FAILED` (R-10).
