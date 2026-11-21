# Auditoría completa de Clavero 2.0, parte A: criptografía y formatos (`experto-seguridad-cripto` y `seguridad-criptografia-movil`)

**Código auditado:** el de `69c967b`, el final de la ronda única de arreglos de las revisiones, en la rama `ccr-0aa27f95-u038cd`.
- Al terminar, la rama está en `16d2661`. Desde `69c967b` solo hay documentos: `git diff --stat 69c967b HEAD -- app/src/main app/src/test jvm-check app/build.gradle.kts` sale vacío.
- Cada `archivo:línea` de este informe es de ese árbol. Las rutas cortas son relativas a `app/src/main/java/app/seguridad/`.

**Base de comparación de los formatos:** `main` = `origin/main` = `a3ac942`, el commit de la etiqueta `v1.4.3`.

**Solo lectura:** no cambié código ni ejecuté Gradle. Usé `git` (`diff`, `show`, `log -S`), `grep` y Python con `-I` solo para leer los parámetros de Argon2id de los archivos de prueba. Escribí únicamente este archivo.

**Lecturas previas:**
- `CLAUDE.md`;
- `experto-seguridad-cripto`: `SKILL.md`, `references/cripto-moderna.md` y `references/auditoria.md` (de `criptoanalisis.md`, el triaje; no aplica a esta parte);
- `ingeniero-movil-senior:seguridad-criptografia-movil`, con `references/recetas-cripto.md`;
- el diseño de la 2.0, sobre todo «Modelo de amenaza», «Formato v4», «Varias bóvedas» y sus notas de corrección de RT-23;
- `progress.md`: las últimas 40 líneas y los «Ruling» y «Owner decision» de las tareas 1 y 2, de la fila 45 y de CR-7;
- `pendientes.md`, entera;
- `final-audit-c.md`, por su formato;
- el parte del 20-11;
- el encargo, `advisor-t12-review-fixes.md`, líneas 237 a 293.

**Riesgos con nombre:** las filas 51, 62, 67 y 69 de `pendientes.md` no se reabren. Nada de lo que sigue las toca.

## Alcance y modelo de amenaza

**Qué entra** (la parte A del encargo):
- `crypto/Crypto.kt`, `VaultCrypto` y el formato v4 (`vault/Vault.kt`, `vault/VaultData.kt`):
  - la AAD (el formato, la versión, el coste y la sal);
  - que no se pueda bajar de versión;
  - los parámetros de Argon2id, y que nunca se baje el coste;
- `vault/Backup.kt` (la marca 3), `vault/BackupCheck.kt`, `crypto/KdfLock.kt`, `vaultTag` y `SecureRandom`;
- la puesta a cero de las claves en todos los caminos de `vault/VaultStore.kt`, también `checkMasterToDelete` (CR-7) y su uso en `AppContainer.deleteVault`;
- las comparaciones en tiempo constante;
- `android/BiometricVault.kt` y el Keystore con un alias por bóveda;
- que `SG1:`, `.sgenc`, `seguridad-vault` y `seguridad-backup` se sigan leyendo como en `main` (`git diff main -- …`).

**Atacantes:**
- los de la decisión (f), que interesan aquí:
  - una copia o una exportación hostil importada, con su contraseña;
  - un `SG1:` o un `.sgenc` hostil;
  - el bloqueo y la huella;
  - la memoria y la muerte del proceso;
- el aislamiento entre bóvedas: la clave de una bóveda no abre otra, y la huella de una no se usa en otra;
- la subida desde la 1.4.3: la bóveda v2/v3 y sus copias, y lo que la 1.4.3 hace con lo que escribe la 2.0.

**Fuera del modelo:** quien escribe en el almacenamiento privado de la app (root). Así lo dicen el diseño (nota de corrección de RT-23) y `CLAUDE.md`. Lo apunto donde un razonamiento depende de ello.

**Qué no entra:**
- MASVS completo y el APK (parte B);
- la cadena de suministro, los secretos y el *path traversal* (parte C);
- `AutoBackup.kt`, la migración de `SettingsStore.kt` y las pantallas de bóvedas, línea a línea (parte D);
- el diff de la ronda como tal (parte E).

Cuando toco algo de esas zonas, es solo por su lado criptográfico.

## Resumen

| Severidad | Hallazgo | Ubicación |
|---|---|---|
| Baja | A-1. Borrar una bóveda desactiva su huella **después** de borrar su carpeta, al revés de CRY-A02, y nada barre después lo que deje una muerte del proceso en medio: la clave del Keystore y la clave de datos envuelta se quedan para siempre | `AppContainer.kt:746-751`, `:770-782`; comentarios `:142-143`, `:750`; texto `vault/VaultsTexts.kt:48` |
| Informativa | A-2. El texto de Ajustes sobre el coste de Argon2id dice «Lo ya cifrado guarda su coste», pero el archivo de la bóveda sube al coste elegido en el siguiente desbloqueo con la maestra y al importar una copia | `ui/screens/SettingsScreen.kt:668`; `vault/Vault.kt:601-610` |
| Informativa | A-3. El diseño promete un test contra el lector de la 1.4.3 que rechace una copia con marca 3; ese test no existe. El comportamiento es correcto: lo comprobé leyendo el código de `main` | Diseño, «Formato v4», `docs/superpowers/specs/2026-11-12-clavero-2.0-design.md:70`; `git show main:app/src/main/java/app/seguridad/vault/Backup.kt`, líneas 37 y 271-273 |

No encontré nada Crítico, Alto ni Medio.
- El esquema v4 liga a la AAD el formato, la versión, el coste y la sal (y, en los datos, la revisión y la última comprobación con la maestra). No deja bajar de versión, nunca baja el coste al volver a envolver ni al importar, y deriva todas las claves bajo `KdfLock`.
- Todas las claves derivadas o abiertas se ponen a cero en todos los caminos de salida, también en CR-7.
- `SG1:` y `.sgenc` no cambian ni un byte de comportamiento.
- `seguridad-vault` v2/v3 y `seguridad-backup` con las marcas 1 y 2 se leen igual que en `main`, salvo un tope de profundidad (16) que ningún archivo escrito por la app alcanza (§«Formatos frente a `main`»).

## Hallazgos

### [Baja] A-1. Borrar una bóveda deja su clave de la huella si el proceso muere en medio, y la desactiva después y no antes (CRY-A02)

- **Severidad:** Baja.
- **CVSS 3.1:** 1.8, `CVSS:3.1/AV:L/AC:H/PR:H/UI:R/S:U/C:L/I:N/A:N`.
  - Para sacar algo, hay que ejecutar código como la app (en la práctica, root, fuera del modelo) y además la huella del dueño, porque la clave es `setUserAuthenticationRequired(true)` con `AUTH_BIOMETRIC_STRONG`.
  - La muerte del proceso entre dos pasos sí está en el modelo (decisión (f)).
- **MASVS v2:** MASVS-CRYPTO-2 (gestión de claves, incluida su destrucción) y MASVS-STORAGE-2.
- **CWE:** CWE-459 (limpieza incompleta).
- **Dónde:**
  - `AppContainer.kt:746` `registry.remove(id)`: escribe la lista y borra la carpeta (`vault/VaultRegistry.kt:127-134`);
  - `AppContainer.kt:747-751`: **después**, `forgetBiometric(id)`, es decir, `BiometricVault.disable` (`SeguridadApp.kt:41`), que borra el IV, la clave envuelta y el alias `seguridad_vault_biometric_<id>` (`android/BiometricVault.kt:188-195`);
  - `AppContainer.kt:770-782`: `forgetOrphanVaults` barre al arrancar los **ajustes** de una bóveda que ya no está, pero no su huella;
  - nada más en el código borra un alias o unas preferencias huérfanos: `grep -rn "deleteEntry\|aliases()" app/src/main/java` solo da `BiometricVault.kt:129`.
- **Qué pasa:**
  - `CLAUDE.md` (CRY-A02) dice: «Crear la bóveda y borrarla desactivan la huella **antes**».
  - Los otros dos caminos que borran lo cumplen:
    - «He olvidado la contraseña maestra»: `ui/screens/VaultScreen.kt:516`, y después `:518` `store.destroy()`;
    - «Crea tu bóveda»: `VaultScreen.kt:412`, y después `:413` `store.create(pw)`.
  - `deleteVault`, nuevo en la 2.0, lo hace al revés.
  - Si el proceso muere entre `:746` y `:748` (Android lo mata en segundo plano, o se agota la batería), la carpeta ya no está y el registro ya no lista la bóveda. Pero `vault_biometric_<id>.xml` (IV y clave de datos envuelta) y el alias del Keystore se quedan para siempre: el id no se repite (`VaultRegistry.kt:100-103`), así que la app nunca vuelve a pedir esa huella ni a borrarla.
  - Los ajustes sí se recuperan: `settings.update` va después (`:754`), y `forgetOrphanVaults` los quita en el siguiente arranque.
  - Los comentarios `AppContainer.kt:142-143` («a key that wraps the data key of a vault that is gone, which opens nothing») y `:750` («it opens nothing») no son verdad. Esa clave de datos sigue abriendo las copias de esa bóveda, que el borrado deja a propósito (`vaultDeleteWarning`, `vault/VaultsTexts.kt:48-49`). Y si la bóveda vino de «Importar como bóveda nueva», también abre la bóveda de la que salió (RT-23: comparten la clave de datos).
- **Impacto:**
  - Dentro del modelo, nadie la puede usar: hace falta ejecutar código como la app y la huella del dueño.
  - Pero la app incumple una regla vigente (CRY-A02) y una promesa visible: «Se borra «$name» de este móvil: su archivo, sus adjuntos, **su huella** y sus ajustes» (`VaultsTexts.kt:48`). Tras esa muerte del proceso, el texto ya no es verdad.
  - Con el orden de CRY-A02, la peor muerte posible deja una bóveda sin huella: el lado seguro, y se vuelve a activar con la maestra.
- **Corrección exacta** (`AppContainer.kt`):
  1. En `deleteVault`, el bloque `try { forgetBiometric(id) } catch …` (`:747-751`) pasa a ir **antes** de `registry.remove(id)` (`:746`). Por ejemplo, justo después de `forgetClock(id)` (`:745`):
     ```kotlin
     forgetClock(id)
     // CRY-A02: the fingerprint goes before the vault, as «He olvidado» and «Crea tu bóveda» do; a death after this
     // leaves the vault without fingerprint (the safe side). A failure is swept at the next start (forgetOrphanVaults).
     try {
         forgetBiometric(id)
     } catch (e: Exception) {
         // Tried again by forgetOrphanVaults.
     }
     registry.remove(id)
     ```
  2. En `forgetOrphanVaults`, olvidar también la huella de cada id huérfano, antes de quitar sus ajustes (`:774-777`):
     ```kotlin
     if (gone.isEmpty()) return@withLock
     // A-1: a deletion cut between the registry and its fingerprint leaves the key; its settings still name the id.
     gone.forEach { id -> try { forgetBiometric(id) } catch (e: Exception) { /* Next start. */ } }
     ```
  3. Corregir los dos comentarios (`:142-143` y `:750`): la clave que queda envuelve una clave de datos que aún abre las copias de esa bóveda (y, con RT-23, la bóveda de la que se importó). Nunca se pide otra vez y necesita la huella.
- **Prueba de concepto propuesta** (tests en `app/src/test/java/app/seguridad/AppContainerVaultsTest.kt`, en rojo hoy):
  1. `deletingAVaultForgetsItsFingerprintBeforeItsFolder`:
     1. Un `AppContainer` con `forgetBiometric = { id -> seen += (c.registry.dirOf(id).exists() to c.registry.vaults.value.any { it.id == id }) }`.
     2. `twoVaults(c)` y `c.deleteVault(idB, masterB.toCharArray(), "Dos")`.
     3. `assertEquals(listOf(true to true), seen)`: la carpeta y la entrada del registro seguían ahí cuando se olvidó la huella.
     4. Hoy da `false to false`.
  2. `anOrphanFingerprintIsForgottenAtStart`:
     1. Un primer contenedor con dos bóvedas, cuyo `forgetBiometric` lanza `IllegalStateException` (simula que el paso no llegó a correr).
     2. Se borra la segunda bóveda: la carpeta no está, y los ajustes quedan con `perVault[idB]`. Para eso, en el test, `settings.updateVault(idB) { it }` tras el borrado, o un `settings.json` escrito a mano con ese id.
     3. Un contenedor nuevo con `forgetBiometric = { forgotten += it }`: `settings.load()` y `forgetOrphanVaults()`.
     4. `assertEquals(listOf(idB), forgotten)`. Hoy la lista sale vacía.

### [Informativa] A-2. «Lo ya cifrado guarda su coste» no es verdad para el archivo de la bóveda

- **Severidad:** Informativa: no da nada a un atacante. Es la regla de veracidad de los textos (`CLAUDE.md`, punto 4).
- **CVSS 3.1:** 0.0, `CVSS:3.1/AV:L/AC:H/PR:N/UI:R/S:U/C:N/I:N/A:N`.
- **MASVS v2:** MASVS-CRYPTO-1 (parámetros de derivación), por lo que se dice de ellos.
- **CWE:** CWE-451 (la interfaz presenta mal una información crítica).
- **Dónde:**
  - el texto: `ui/screens/SettingsScreen.kt:668`, «Más información» del coste de Argon2id: «Es de cada bóveda. Se usa al crear esta bóveda, al cambiar su contraseña maestra y, con ella elegida, al cifrar textos y archivos. Lo ya cifrado guarda su coste.». Viene de la 1.4.3 (`git show main:…/SettingsScreen.kt`, línea 473); la 2.0 añadió «Es de cada bóveda» y «con ella elegida»;
  - el código: `vault/Vault.kt:601-610`. En `afterMasterUnlockWithData`, si el archivo cuesta menos que el perfil en memoria o en pasadas (`weaker`), se vuelve a envolver con el máximo, eje por eje. Pasa en cada desbloqueo con la maestra (`VaultStore.kt:612-613`) y en «Importar copia» e «Importar como bóveda nueva» (`VaultStore.kt:1570-1586`).
- **Qué pasa:**
  - Elegir «Alto» no solo afecta a la bóveda que se cree o a la maestra que se cambie. El archivo de la bóveda que ya existe sube a «Alto» en su siguiente desbloqueo con la maestra.
  - Y con él, sus copias siguientes, que ya solo se abren en móviles con 171 MiB o más (`Crypto.kt:66-72`).
  - El texto `KDF_COST_KEPT` (`vault/VaultsTexts.kt:122-125`, «No se pudo subir el coste de Argon2id al elegido en Ajustes…») ya cuenta esa subida, así que los dos textos se contradicen.
  - El riesgo práctico es de disponibilidad, no de secreto. La frase de al lado sí avisa: «úsalo solo si vas a abrir tus copias en móviles como este» (`SettingsScreen.kt:669-670`).
- **Corrección exacta** (al Abogado, con el visto bueno técnico):

  > Es de cada bóveda. Se usa al crear esta bóveda, al cambiar su contraseña maestra y, con ella elegida, al cifrar textos y archivos. Si el archivo de la bóveda tiene un coste menor, sube a este la próxima vez que la abras con la contraseña maestra; nunca se baja. Los textos y archivos ya cifrados guardan su coste.

  Después, «Lo cifrado con el coste alto…» como está.
- **Prueba de concepto propuesta:**
  1. Un test de fuente nuevo, por ejemplo `app/src/test/java/app/seguridad/tools/KdfCostHintTest.kt`, `theCostHintSaysTheVaultIsRaisedNeverLowered`: lee `SettingsScreen.kt` y comprueba que no tiene «Lo ya cifrado guarda su coste», y que dice «sube» y «nunca se baja».
  2. En el mismo test, `VaultCrypto.afterMasterUnlock` con un archivo `KdfParams(1024, 1, 1)` y el perfil `KdfParams(2048, 2, 1)`: el archivo resultante tiene `kdf == VaultKdf(2048, 2, 1)`.
  3. Y el caso inverso: un archivo `KdfParams(2048, 2, 1)` con el perfil `KdfParams(1024, 1, 1)` conserva `2048/2/1`. El texto queda atado al comportamiento.

### [Informativa] A-3. Falta el test prometido contra el lector de la 1.4.3

- **Severidad:** Informativa: es verificación, no seguridad. El comportamiento es el correcto.
- **CVSS 3.1:** 0.0, `CVSS:3.1/AV:L/AC:H/PR:N/UI:R/S:U/C:N/I:N/A:N`.
- **MASVS v2:** MASVS-CODE-4 (lo que lee cada versión, frente a lo que escribe otra).
- **CWE:** CWE-1068 (lo hecho no coincide con el diseño documentado).
- **Dónde:**
  - el diseño, «Formato v4» (`docs/superpowers/specs/2026-11-12-clavero-2.0-design.md:70`): «La 1.4.3 rechaza una copia con la marca nueva con su texto `BACKUP_NEWER` …, sin tocar nada: **se comprueba con un test contra el lector de la 1.4.3**»;
  - la sección «Pruebas» del diseño (`:234`) lo repite;
  - el informe de la tarea 1 (`sdd-2.0/task-1-report.md:213`) dice que se comprobó leyendo el código, y que `marker4IsNewer` (`app/src/test/java/app/seguridad/vault/BackupV3Test.kt:94-97`) prueba «lo mismo para el lector de la 2.0»;
  - ningún test usa la lógica del lector de la 1.4.3: `grep` sobre `app/src/test` y `jvm-check`, con «1.4.3», `BACKUP_NEWER` y la marca `"3\n"`.
- **Qué pasa:** el comportamiento es el correcto.
  - El lector de `main` tiene `BACKUP_NEWEST = 2` (`git show main:app/src/main/java/app/seguridad/vault/Backup.kt`, línea 37).
  - Rechaza todo marcador que no sea `"2\n"` ni `"1\n"`: un número mayor que 2 da `BACKUP_NEWER` (líneas 271-273).
  - La 2.0 escribe `"3\n"` solo para una bóveda v4 (`vault/Backup.kt:149-153`). La 1.4.3 la rechaza antes de leer `vault.json`.
  - La bóveda v4 en sí, si llegara a la 1.4.3 de otro modo, da «No es una bóveda compatible»: su `supported` solo admite 0, 2 y 3 (`git show main:…/Vault.kt`, `supported`).
  - Falta solo la prueba que el diseño promete. La 1.4.3 está publicada y no cambia, así que el riesgo es nulo; pero el diseño dice algo que no se hizo.
- **Corrección exacta**, una de dos:
  - **(a)** El test: copiar al test, como función privada `reader143Marker(bytes: ByteArray): String?`, el `if` del marcador del lector de `a3ac942` (líneas 262-276 de ese `Backup.kt`; es código propio, no de terceros), con su `BACKUP_NEWEST = 2` y su texto de entonces, «La copia es de una versión más nueva de Clavero».
  - **(b)** Una nota de corrección en el diseño, en `:70` y `:234`: «comprobado leyendo el código de `a3ac942` (`Backup.kt:37`, `:271-273`); no hay test, porque el lector de la 1.4.3 no cambia».
- **Prueba de concepto propuesta (con (a)):** `BackupV3Test.aCopyOfAV4IsRefusedByThe143Reader`:
  1. Una bóveda v4 nueva (`VaultCrypto.create` con `KdfParams(1024, 1, 1)`) y su copia con `writeBackup(out, encodeFile(v4), emptyMap())`.
  2. Leer la primera entrada del ZIP y pasarla por `reader143Marker`: da el `BACKUP_NEWER` de la 1.4.3.
  3. Lo mismo con `vault-1.4.3.json`: su copia lleva la marca 2 y `reader143Marker` da `null` (la acepta).

## Comprobación del encargo, punto por punto

### 1. `Crypto.kt`, `VaultCrypto` y la v4

**La AAD.** `fieldsAad` (`vault/Vault.kt:215-217`) une, con U+0000, la etiqueta, el formato, la versión, `memoryKiB`, `iterations`, `parallelism` y la sal en Base64.
- La clave envuelta lleva la etiqueta `seguridad-vault-key-v2` (`keyAad`, `:249`).
- Los datos llevan `seguridad-vault-data-v2`, más la revisión y `masterCheckedAt` (`dataAad`, `:256`).
- La codificación no es ambigua:
  - el formato tiene que ser exactamente `seguridad-vault` (`supported`, `:220`);
  - la sal es Base64, que no tiene U+0000;
  - los números salen de enteros ya leídos.
- Dos archivos distintos nunca dan la misma AAD.
- Al revés también se cumple: dos escrituras de la misma sal en Base64 (con o sin relleno) dan AAD distintas y fallan cerradas.
- Es lo que pide `references/cripto-moderna.md` §2: ligar el cifrado a su contexto.

**Bajar de versión.**
- `READ_VERSIONS` = {0, 2, 3, 4} (`:222`).
- Un archivo sin versión se prueba como 3 y como 2, nunca como 4 (`candidates`, `:225-226`).
- La versión va en la AAD de los dos blobs: un v4 con `"version": 3`, o sin versión, no abre.
- Con `coerceInputValues = true` (`core/Json.kt`), un `"version": null` se lee como 0, prueba 3 y 2, y falla cerrado.
- `encodeDefaults` está apagado, pero la versión 4 nunca es el valor por defecto (0), así que siempre se escribe (`VaultFile.version`, `:172`; `wrap`, `:413`).
- `write` solo acepta una v4 (`:530-536`), y `wrap` siempre escribe la 4 (`:413`, `:422-423`). La app nunca vuelve a escribir una v2 ni una v3.
- La huella no se ofrece para una v2, una v3 ni un archivo sin versión: `BiometricRefusal.UPGRADE` va antes del reloj y de los 7 días (`:681-687`; `VaultStore.kt:728-733`), y la clave ni se prueba.
- Lo fijan los tests:
  - `VaultV4Test.v4NeverTriedForUnknownVersion` y `v4BindsVersionInAad` (`app/src/test/java/app/seguridad/vault/VaultV4Test.kt:154-171`);
  - `writeIsOnlyForV4` (`:173-180`);
  - `VaultV3Test.deLaVersion4NoSePasaALa3NiALa2`.

**Los parámetros de Argon2id.**
- Argon2id, versión 0x13 (RFC 9106; `crypto/Crypto.kt:249-255`), con sal de 16 bytes (`:228`, `:246`) y 32 bytes de salida (`:223`).
- Los perfiles (`settings/Settings.kt:28-31`):
  - «Estándar»: 64 MiB, 3 pasadas, 1 carril;
  - «Alto»: 128 MiB, 4 pasadas, 1 carril.
- Los dos superan el mínimo de OWASP (19 MiB, t=2). El primero es la segunda recomendación de RFC 9106 con p=1.
- El valor por defecto es 64 MiB desde la 1.0 (`git show origin/claude/seguridad-1.0.0:…/Crypto.kt`, línea 97). Así que ninguna copia que haya escrito Clavero cuesta menos que «Estándar».
- `checkSane` (`:62-77`) acota cualquier cabecera antes de derivar:
  - de 8·p KiB a 128 MiB, de 1 a 10 pasadas y de 1 a 4 carriles;
  - y lo que no cabe en ¾ del *heap*.
- Corre antes de toda derivación: `Vault.kt:433`; `Crypto.kt:248`; `Crypto.kt:522` (`SG1:`/`.sgenc`); `imports/ImportKdf.kt:85`.
- El vector de RFC 9106 está fijado en `CryptoTest` (`argon2id matches RFC 9106`).

**Que nunca se baje el coste.**
- `afterMasterUnlockWithData` (`Vault.kt:601-608`) vuelve a envolver con el máximo, eje por eje: memoria, pasadas y paralelismo.
- La reserva de la fila 45 (`:611-624`), cuando subir el coste se queda sin memoria, guarda el coste del propio archivo, nunca uno menor.
- La importación usa la misma función (`VaultStore.kt:1570-1586`), así que A8 se cumple.
- `changeMaster` (`Vault.kt:645-655`, `VaultStore.kt:1412`) usa el perfil de Ajustes, como en la 1.4.3 (`git show main:…/Vault.kt:388-397`). Es la elección del usuario para una contraseña nueva, y Ajustes lo dice («Se usa … al cambiar su contraseña maestra»).
- Los nuevos caminos de la 2.0 no bajan nada:
  - una bóveda nueva empieza en «Estándar» (Ruling T2, duda 2);
  - «Importar como bóveda nueva» envuelve con el máximo entre la copia y ese perfil.

**`PasswordUse`.**
- `EXISTING` para abrir, para volver a envolver tras la maestra y al importar: `Vault.kt:330`, `:439`, `:610`, `:620`.
- `NEW` para crear y para cambiar la maestra: `:392`, `:650`.
- Una maestra de la 1.0 o la 1.1 sigue abriendo tras la subida a v4 (`PasswordBytesTest`, el cambio de `:177-185`: ahora comprueba la 4).

**Nonces y aleatoriedad.**
- Nonce de 96 bits nuevo en cada sellado (`Vault.kt:260`).
- La clave de datos, de 32 bytes aleatorios (`:390`, `:648`).
- La sal es nueva en cada envoltura (`:408`).
- Las escrituras de la 2.0 suben: cada uso (`markUsed`) y cada desbloqueo guardan. Aun así quedan órdenes de magnitud por debajo del límite de unos 2³² mensajes por clave con nonces aleatorios (NIST SP 800-38D §8.3).
- Con RT-23 (dos bóvedas con la misma clave de datos), la cuenta es la suma de las dos, igual de lejos.

### 2. `Backup` (marca 3), `BackupCheck`, `KdfLock`, `vaultTag` y `SecureRandom`

**`Backup.kt`.**
- La marca 3 (`:41`, `:44`) se escribe solo si la versión del archivo del disco es 4 (`:149-153`; `VaultStore.kt:1454-1478`, `version = v.version` bajo el mutex).
- `exportBeforeUpgrade` solo exporta mientras espera la subida, y entonces el archivo es la v2/v3 con la marca 2 (`VaultStore.kt:1452`, `:1459`).
- El lector:
  - acepta las marcas 1, 2 y 3 (`:321-333`);
  - da 33 MiB solo a `vault.json` con la marca 3 (`:330-333`, `:341`); los adjuntos, siempre 6 MB (`:297`, `:352`);
  - con un número mayor que 3, `BACKUP_NEWER` (`:325-327`).
- La marca no está autenticada, pero nada de seguridad depende de ella:
  - `BackupContents.version` solo se enseña (`BackupCheck.kt:124`);
  - los topes de lo descifrado los aplican `checkCopyShape` y `checkCopySize` sobre los datos, sea cual sea la marca (`Vault.kt:462`; `BackupCheck.kt:300-303`).
  - Cambiar la marca a mano solo cambia el tope de bytes de `vault.json` (un rechazo antes o después) y lo que dice el informe.

**`BackupCheck.kt`.**
- `checkBackup` (`:78-139`) deriva bajo `KdfLock` (`:101`) y pone la clave a cero en `finally` (`:136`), también si lo cancelan.
- Comprueba los mismos datos que sellaría la importación (`readAsImported` + `checkCopyData`, `:108`), en el mismo orden (`VaultStore.kt:1575-1583`; R5).
- Los adjuntos se comparan en tiempo constante (`:313-319`, `Backup.kt:199-219`).
- `withBackupChecked` (`:146-147`) compara etiquetas que no son secretas.

**`KdfLock`.**
- `derive` (`crypto/KdfLock.kt:52-53`): el cerrojo, después `NonCancellable`, después el contexto. Una clave derivada siempre vuelve a quien la pone a cero (BOV-3).
- Todas las Argon2id de la bóveda pasan por `VaultStore.derive` (`VaultStore.kt:425`), en las líneas `:561`, `:607`, `:612`, `:773`, `:791`, `:1412`, `:1558` y `:1570`. Más `BackupCheck.kt:101`.
- El orden de los cerrojos se respeta: el mutex de la bóveda y después `KdfLock`. CR-7 sigue ese orden.

**`vaultTag`.**
- 16 bytes de `SecureRandom`, en hexadecimal escrito a mano, sin depender de la configuración regional (`Vault.kt:339-348`, `:368`; decisión del dueño del 19-11).
- Se comprueba en las copias (`isVaultTag`, `:374`; `BackupCheck.kt:292`).
- «Importar como bóveda nueva» da una etiqueta propia después de las comprobaciones (`VaultStore.kt:1578-1580`).
- No es una clave ni un secreto. Va dentro del blob cifrado, y lo único que sale a `settings.json` es su SHA-256, mezclado con la fecha y el coste (`changeMarkOf`, `VaultStore.kt:1846`).

**`SecureRandom`.**
- Un solo `SecureRandom()` del sistema (`Crypto.kt:232-234`).
- `grep` en `app/src/main/java` no encuentra `kotlin.random`, `java.util.Random` ni `Math.random`.
- Los ids de las bóvedas son `randomBytes(16)` (`VaultRegistry.kt:102`). Los de entradas, carpetas y vistas son `UUID.randomUUID()`, que también usa `SecureRandom`, y no son secretos.
- El `"%02x"` de los ids de bóveda (`VaultRegistry.kt:102`) da ASCII con el `Formatter` del JDK y el de Android desde la 7. La versión mínima es Android 8, así que el riesgo que llevó a escribir a mano el `vaultTag` no aplica aquí.

### 3. Las claves a cero, también CR-7

Recorrí cada camino que crea, copia o recibe una clave:

| Camino | Dónde se pone a cero |
|---|---|
| `VaultCrypto.create` | La clave de datos, si algo falla (`Vault.kt:393-396`) |
| `wrap` | La clave derivada de la maestra, siempre (`:425-427`) |
| `unlock` | La clave derivada de la maestra, siempre (`:442-444`) |
| `read` y `readJson` | El JSON descifrado, siempre (`:477`, `:493`) |
| `seal` | El texto en claro (`:264`), salvo la clave de datos, que sigue en uso (`:422`) |
| `changeMaster` | La clave de datos nueva, si falla (`:651-653`) |
| `argon2id` | La contraseña en UTF-8 (`Crypto.kt:262-264`) |
| `runAead` | El búfer, si falla (`:299`) |
| `blockGcm` | La copia de la clave (`:326-328`) |
| `chaChaPoly` | La clave y el estado (`:405-412`) |
| `commit` | En cada fallo (`VaultStore.kt:492-505`) |
| `opened` | La clave anterior, al sustituirla, o la nueva, si la bóveda se bloqueó antes (`:525-534`) |
| `createLocked` | `:564-567` y el `commit` |
| `unlockLocked` | `:616-626` y `:619-621` |
| `unlockWithKey` | En cada salida (`:709`, `:715-760`) |
| `confirmMasterLocked` | `:775` |
| CR-7, `checkMasterToDelete` | `try`/`finally` alrededor de todo lo que sigue a la derivación, también si la bóveda deja de estar activa o se acaba la memoria (`:787-804`) |
| `lock` | `:818` |
| `modifyData` | `:903-921` |
| `addAttachment` | El adjunto en claro (`:1377-1378`) |
| `changeMaster` (la tienda) | La copia de la clave y la clave nueva (`:1413-1414`, `:1421-1422`) |
| `replace` | `:1592-1599`, `:1606-1610`, `:1623-1627`, `:1632-1635`, más el `commit` y `opened` |
| `checkBackup` | `BackupCheck.kt:136` |
| `BiometricVault.enable` | Las dos copias, en todos los finales (`:353`, `:397-400`, `:420`, `:527`) |
| `BiometricVault.unlock` | La entrega con `handOverKey` (`:574`) o la puesta a cero en un `Error` (`:564`) |

**CR-7** (`VaultStore.kt:787-804`; `AppContainer.kt:724-732`):
- Solo comprueba. No publica estado, no llama a `onClockReset`, no purga la papelera, no vuelve a envolver ni sube a v4.
- Va bajo el mutex, en `NonCancellable`, y deriva bajo `KdfLock`.
- `active()` se mira antes y después de la derivación.
- Lo fijan `AppContainerVaultsTest.deletingALockedV3VaultNeverOpensItNorCopiesIt` y `deletingALockedV4VaultNeverOpensItNorCopiesIt` (`:578-603`). El archivo queda igual byte a byte, sin ningún documento en la carpeta y sin `Unlocked`.
- Que la clave quede a cero se ve leyendo el código; ningún test lo mira (`VaultCrypto.unlock` no se puede inyectar ahí). No lo pongo como hallazgo: el `finally` cubre todas las salidas.

**Lo que queda, aceptado o sin efecto:**
- Los `String` de `VaultData` (las contraseñas descifradas) no se pueden poner a cero. Es así desde la 1.x.
- Tampoco la copia de la clave que guarda `SecretKeySpec` en la JCA (`Crypto.kt:374`), ni las subclaves de los motores de Bouncy Castle (CRY-A03, documentado en `Crypto.kt:306-311`).
- `Vault.kt:441` no pone a cero una clave de datos desenvuelta de tamaño distinto de 32. Viene de la 1.3.1, sin cambios. Solo llega ahí un archivo cuya clave envuelta autentica con la clave derivada de la maestra, es decir, uno sellado por quien sabe la contraseña: su contenido no es un secreto del usuario.
- `replace` llama a `keepAside()` (`VaultStore.kt:1616`) sin poner la clave a cero si lanza. `File.renameTo` no lanza `IOException`, así que no hay camino práctico.

### 4. Comparaciones en tiempo constante

- Los SHA-256 de los adjuntos usan `MessageDigest.isEqual`: `Backup.kt:187`, `:215`; `Attachments.kt:138`.
- Las etiquetas de AES-GCM las comparan la JCA y Bouncy Castle por dentro.
- `isEqual` también en las herramientas: `BitwardenCrypto.kt:151` (MAC), `Shamir.kt:76`, `Slip39.kt:72` y `HashLab.kt:98`.
- `contentEquals` solo aparece sobre datos públicos: el *magic*, los marcadores de la copia y las claves públicas de `pk/` y `Contacts.kt`.
- Lo nuevo de la 2.0 que compara contraseñas no es un oráculo de tiempo para nadie, porque es una comparación local al guardar: `withVersion`, `vault/History.kt` (diff `main..HEAD`, línea `passwordForHealth() != previous.passwordForHealth()`), y `comparable()`.
- `withBackupChecked` compara `vaultTag`, que no es secreto.

### 5. `BiometricVault` y un alias por bóveda

**La clave del Keystore** (`BiometricVault.kt:133-149`):
- AES-256-GCM, `setUserAuthenticationRequired(true)` y `setInvalidatedByBiometricEnrollment(true)`;
- `AUTH_BIOMETRIC_STRONG` con tiempo 0 desde Android 11 (`:141`); antes, autenticación en cada uso con `CryptoObject`;
- StrongBox cuando lo hay, y el TEE como alternativa.

No cambia frente a `main`: el diff solo añade `vaultId`.

**Un alias y unas preferencias por bóveda:**
- La primera bóveda conserva `seguridad_vault_biometric` y `vault_biometric`: `perVaultName` (`VaultRegistry.kt:56`), fijado en `AppNameTest:126-127` y `VaultRegistryTest.perVaultNamesKeepThePrimaryOnes`.
- Las demás llevan `_<32 hex>`. No hay colisión posible: `dirOf` y `read` solo aceptan `principal` o 32 hex (`VaultRegistry.kt:31`, `:90-94`, `:156`).
- Lo del teléfono (StrongBox) está en `PREFS` y sobrevive a `clear` (`:120-121`, `:188-195`).

**El aislamiento:**
- `VaultScreen.kt:238-239` y `:451-456` usan siempre el `store.id` y el `store` de la misma bóveda.
- `unlockWithKeyLocked` rechaza y pone a cero una clave si la bóveda ya no es la activa (`VaultStore.kt:719`, `:753`), o si no autentica sus datos (`StaleBiometricKey`, `:735-738`).
- La clave de una bóveda no abre otra: los datos de cada una están ligados a su sal (`dataAad`).
- Excepción, RT-23: dos bóvedas importadas de la misma copia comparten la clave de datos (nota del diseño). Mezclar sus archivos exige root.

**El orden de CRY-A02:**
- Se cumple al crear (`VaultScreen.kt:412`), con «He olvidado» (`:516`), al cambiar la maestra (`VaultBackup.kt:149`, después de guardar) y al importar (`:195`, después de guardar).
- **No** se cumple al borrar desde «Bóvedas» (A-1).

### 6. Formatos frente a `main`

**`SG1:` y `.sgenc`: sin cambios.**
- `git diff main HEAD --quiet -- crypto/FileCrypto.kt crypto/pk` sale limpio.
- En `Crypto.kt`, el diff solo:
  - separa en dos textos el error de `checkSane` (`:75-76`: antes, un solo `throw` para «fuera de límites» o «no cabe»; ahora, el mismo `throw` con dos mensajes);
  - cambia los textos de falta de memoria (`:94-114`);
  - y amplía `newPasswordFailureMessage`.
- `Header`, `encryptText`, `decryptText` (`:475-645`) y los límites no cambian: se aceptan exactamente las mismas cabeceras.

**`seguridad-vault` v2 y v3.**
- `unlock`, `authenticated`, `resolve`, `keyAad` y `dataAad` no cambian.
- Los candidatos para un archivo sin versión son los de antes: antes `[VERSION(=3), 2]`, ahora `[VERSION_3, VERSION_2]`.
- `read` descodifica la misma `List<VaultEntry>`, con el mismo `appJson` (`core/Json.kt` sin cambios).
- Solo añade dos cosas:
  - `jsonNestingWithin` (16 niveles; `Vault.kt:461`, `VaultLimits.kt:498-519`): un archivo escrito por la app llega como mucho a unos 4 niveles en v2/v3 y a 8 en v4;
  - `withPlainVersions`, que no hace nada sin `history`.
- Lo cubren las pruebas con datos reales de antes: `vault-1.1.json` (v2), `vault-1.4.3.json` (v3), `export-1.0.2.json` y `backup-1.1.sgbak`, en `VaultV4Test`, `VaultV3Test`, `PasswordBytesTest`, `BackupTest`, `VaultTotpTest` e `ImportHeadTest`. Los archivos no cambian: `git diff --stat main HEAD -- app/src/test/resources/` solo añade `vault-1.4.3.json`, `settings-1.4.3.json` y el `README.txt`.

**`seguridad-backup`.**
- Las marcas 1 y 2 se leen como en `main` (`git show main:…/Backup.kt:271-275` frente a `:321-333`), con los mismos 6 MB.
- El escritor da la marca 2 a toda bóveda que no sea v4, byte a byte como antes (`BackupV3Test.v3VaultWritesMarker2`).
- La marca 3 es nueva, y la 1.4.3 la rechaza (A-3).

## Lo que está bien

- **v4 sin primitivas nuevas:** el mismo AES-256-GCM y la misma Argon2id. La versión, el formato, el coste y la sal van en la AAD de la clave envuelta y de los datos, y la revisión y la última maestra, en la de los datos (`Vault.kt:215-256`).
- **Ningún camino para bajar de versión:** ni por la lectura, ni por la huella, ni por la escritura (`:220-241`, `:530-536`, `:681-687`). Los tests de manipulación están en `VaultV4Test:154-180` y `VaultV3Test`.
- **El coste nunca baja** al volver a envolver ni al importar: el máximo eje por eje (`:601-608`), y la reserva de la fila 45 guarda exactamente el coste del archivo (`:611-624`).
- **Todas las derivaciones pasan por `KdfLock`**, sin carreras que pierdan una clave (BOV-3).
- **La puesta a cero es completa** en cada salida, incluidos CR-7 y la huella.
- **«Comprobar una copia» e «Importar» comprueban exactamente los mismos datos**, en el mismo orden (R5).
- **`vaultTag`** se escribe sin depender de la configuración regional, y una etiqueta mal formada en una copia es `COPY_DAMAGED`.
- **Un alias del Keystore por bóveda**, con la primera intacta, y aislamiento por `active()` y por la autenticación de los datos.
- **`SG1:` y `.sgenc` intactos.** `seguridad-vault` v2/v3 y `seguridad-backup` 1/2 se leen como en la 1.4.3, con pruebas sobre archivos reales.

## Revisión experto-seguridad-cripto

- **Modelo de amenaza primero:** lo nuevo de la 2.0 en cripto es la v4, la subida, la marca 3, las varias bóvedas (con su huella) y CR-7. Contra la copia hostil, la AAD y el tope antes de descodificar (`Vault.kt:461-462`) cierran la manipulación y el agotamiento. Contra la muerte del proceso, CR-7 y la subida no dejan nada a medias. El único resto es A-1, en el borrado.
- **AEAD bien usado** (`references/cripto-moderna.md` §2; NIST SP 800-38D):
  - etiqueta completa de 128 bits (`Crypto.kt:230`);
  - nonce aleatorio de 96 bits en cada sellado (`Vault.kt:260`);
  - AAD que liga el contexto (`:215-256`);
  - nunca se devuelve texto en claro sin verificar (`runAead`, `Crypto.kt:287-301`; la JCA lanza antes de devolver).
- **KDF de contraseñas** (§3; RFC 9106; OWASP Password Storage Cheat Sheet): Argon2id 64 MiB/t=3/p=1 y 128 MiB/t=4/p=1, con sal de 16 bytes, y límites duros antes de derivar (`Crypto.kt:62-77`).
- **Una propiedad conocida, que dejo escrita:**
  - Volver a envolver con un coste mayor (A8 y la fila 45) conserva la clave de datos (`Vault.kt:610`, el `dek` de siempre). Así que quien tenga una copia antigua envuelta con un coste menor puede atacar ese coste y, si da con la maestra, leer también los datos posteriores sellados con esa clave.
  - Solo un cambio de maestra da una clave de datos nueva (`:648`).
  - Ninguna copia que escribe Clavero cuesta menos que «Estándar», así que el margen real es el de «Estándar». Lo mismo vale para la reserva de la fila 45 en una importación: guardar el coste de la copia no añade nada que la copia no tenga ya.
  - No es hallazgo: es el diseño de siempre, coherente con la nota de RT-23. Lo apunto para que ningún texto diga que subir el coste protege las copias anteriores.
- **Aleatoriedad** (§6): solo `SecureRandom` del sistema (`Crypto.kt:232`).
- **Comparaciones** (§3, último punto): `MessageDigest.isEqual` para todo lo secreto o autenticado (`Backup.kt:187`, `:215`; `Attachments.kt:138`).
- **Riesgos con nombre:** las filas 51, 62, 67 y 69 no se tocan.

## Revisión Ingeniero Móvil Senior

- **Keystore** (`seguridad-criptografia-movil`, «Almacenamiento seguro» y «Autenticación»):
  - clave no exportable en StrongBox o en el TEE (`BiometricVault.kt:133-149`);
  - la huella va atada a una operación criptográfica (`BiometricPrompt.CryptoObject`, `:237`), no a un booleano;
  - `setInvalidatedByBiometricEnrollment(true)`;
  - sin `setUnlockedDeviceRequired`, por la decisión documentada de la 1.2 (`:143-145`).
  - Con un alias por bóveda, las preferencias de cada una quedan en su propio archivo privado (`allowBackup="false"`, `AndroidManifest.xml:11`).
- **Ciclo de vida de la clave:** crear, activar, desactivar, cambiar la maestra, importar y «He olvidado» desactivan la huella en el orden de CRY-A02. El borrado desde «Bóvedas» no, y no hay barrido al arrancar (A-1, `AppContainer.kt:746-751`, `:770-782`).
- **Concurrencia:**
  - `KeyOpsQueue` es una sola fila para todas las bóvedas, y un `disable` de cualquiera cancela una activación en curso de cualquiera (`tools/KeyOpsQueue.kt:34`).
  - Lo miré por si una bóveda podía cortar la activación de otra: no es alcanzable. Mientras se activa la huella de A, su diálogo tapa la pantalla. Y una importación o un cambio de maestra de B que estuviera corriendo termina antes de que `switchTo` deje ver A (`awaitIdle` bajo el mutex de B), con su `disable(B)` milisegundos después, mucho antes de que A se pueda desbloquear y pulsar «Activar huella».
  - Si alguna vez pasara, el resultado es el lado seguro: no se guarda nada.
- **Corrutinas:**
  - toda derivación corre en `Dispatchers.Default` bajo `KdfLock`, nunca en el hilo principal (`KdfLock.kt:52-53`; `deriveBlocking` lo rechaza en el principal, `:65-68`);
  - los pasos que no deben cortarse van en `NonCancellable`: `VaultStore.kt:484`, `:767`, `:788`, `:1601`; `BackupCheck.kt:90`, `:137`;
  - `launchUnlockWithKey` arranca en `ATOMIC` para no perder la clave (`VaultStore.kt:124-147`).
- **Memoria:**
  - `OutOfMemoryError` se convierte en texto en el desbloqueo (`VaultStore.kt:615-618`), en la huella (`:747-750`), en CR-7 (`:796-798`) y en las copias (`:1594-1596`; `BackupCheck.kt:109-111`).
  - Argon2id reserva su memoria en el *heap* de la app; `checkSane` deja libre un cuarto (`Crypto.kt:66-72`).
  - Las medidas en el móvil siguen en las filas 51 y 55.

## Textos para el Abogado Digital (revisión final)

- **A-2:** `ui/screens/SettingsScreen.kt:668`, «Más información» del coste de Argon2id. Propuesta: «Es de cada bóveda. Se usa al crear esta bóveda, al cambiar su contraseña maestra y, con ella elegida, al cifrar textos y archivos. Si el archivo de la bóveda tiene un coste menor, sube a este la próxima vez que la abras con la contraseña maestra; nunca se baja. Los textos y archivos ya cifrados guardan su coste.» Tiene que concordar con `KDF_COST_KEPT` (`vault/VaultsTexts.kt:122-125`).
- **A-1:** `vaultDeleteWarning` (`vault/VaultsTexts.kt:48-49`) dice «su huella» entre lo que se borra. Con el arreglo de A-1 será verdad siempre; sin él, deja de serlo si el proceso muere en medio del borrado. No propongo cambiar el texto, sino el código.
- **Sin cambios de texto, solo para que lo sepa:** ningún texto publicado debe decir que subir el coste de Argon2id protege las copias hechas antes. Volver a envolver conserva la clave de datos (§«Revisión experto-seguridad-cripto»). Hoy ninguno lo dice: lo comprobé en el README, la política y las constantes de `vault/VaultsTexts.kt`.

Modelo: el director (esfuerzo extra).
