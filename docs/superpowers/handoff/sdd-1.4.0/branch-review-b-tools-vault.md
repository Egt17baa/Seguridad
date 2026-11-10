# Clavero 1.4.0: revisión de la rama, área B (herramientas y bóveda)

Revisor: área B de la revisión de la rama entera (`7c8dea7..b44aa31`, rama `ccr-d1e75160-p5f4wc`, en el árbol en `7e13934`, que solo añade una línea al registro). Solo se ha leído código; no se ha ejecutado Gradle (instrucción común). Los arreglos de abajo se deben verificar con `./gradlew :app:testDebugUnitTest` y `./gradlew -p jvm-check test`.

Alcance: `BiometricVault` con `KeyOpsQueue` y `KeyPlacement` y sus seis llamadas a `disable`; `tools/hashlab/`, `HashLabScreen.kt` y `crypto/Hashes.kt`; `tools/classic/` y `ClassicLabScreen.kt`; `tools/slip39/`, `SecretShareScreen.kt`, `crypto/Bip39.kt` y los assets `slip39_english.txt` y `licenses/slip39.txt`; `vault/BackupCheck*.kt` y `BackupCheckScreen.kt` (con `Backup.kt` y el reparto con `VaultStore.importReplace`); `vault/MasterPolicy.kt` (CRY-A01 y RT-3, decisión (h)); `settings/`, `CipherSessions.kt` y los cambios pequeños de las demás pantallas y de `vault/`.

## Veredicto

**Necesita arreglos:** 1 Important y 4 Minor. No hay ningún Critical. SLIP-0039, la fila de `BiometricVault`, las comprobaciones de «Comprobar una copia» y la regla del leet están bien. Los hallazgos son de veracidad de los textos, de un teclado que aprende lo que se escribe, de código muerto y de dos derivaciones de Argon2id que pueden correr a la vez.

## Hallazgos

### I-1 (Important): el Laboratorio de hashes y el Laboratorio clásico usan el teclado normal en campos que tratan como secretos, y sus textos dicen que lo escrito no se guarda en el móvil

- **Dónde:**
  - `ui/screens/HashLabScreen.kt:295-301`: el texto que se calcula.
  - `HashLabScreen.kt:345-351`: el mensaje del HMAC.
  - `HashLabScreen.kt:372-378`: la entrada de Base64/hex. Decodificar puede sacar un secreto, y codificar puede pedirlo.
  - `ui/screens/ClassicLabScreen.kt:229-235` (`TextInput`) y los campos de clave (`:172`, `:192`, `:281-297`).

  Ninguno lleva `keyboardOptions`, así que usan el teclado normal, con sugerencias y aprendizaje. En cambio, el texto de «Cifrar texto» (`SecurityToolsScreen.kt:398`) y los campos de «Repartir un secreto» (`SecretShareScreen.kt:277`, `SECRET_KEYBOARD`) usan `KeyboardType.Password` con `autoCorrectEnabled = false`. `HashLabSession.kt:3-6` dice que el texto, el mensaje del HMAC y la entrada del códec «can be a secret», y las dos sesiones los borran con la regla de «PIN y códigos».
- **Texto que deja de ser verdad:**
  - `HASH_LAB_HMAC_INTRO` (`tools/hashlab/HashLabTexts.kt:35-37`) dice: «La clave y el mensaje no se guardan en el móvil: solo están en esta pantalla».
  - `CLASSIC_NOT_SAVED` (`tools/classic/ClassicTexts.kt:9`) dice: «Lo que escribes no se guarda en el móvil».
- **Fallo:**
  1. El usuario escribe en «Mensaje (UTF-8)» un texto secreto, por ejemplo un token o una frase.
  2. Gboard u otro teclado lo aprende en su diccionario personal, en el móvil y a veces sincronizado, y lo sugiere después en otras apps.
  3. La pantalla le había dicho que no se guarda en el móvil, y `FLAG_SECURE` no lo evita.

  Es MASVS-STORAGE-2: datos sensibles en la caché del teclado. Es un hallazgo de seguridad confirmado, y por la regla de la skill vale como mínimo Important.
- **Arreglo:**
  - En los cinco campos del Laboratorio de hashes que pueden llevar un secreto (texto, mensaje y entrada del códec; la clave ya es `PasswordField`), poner `KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password)`, como `SECRET_KEYBOARD`. Conviene sacarlo a un `val` compartido en `ui/screens`.
  - Lo mismo en `TextInput` y en los campos de clave del Laboratorio clásico, o bien cambiar `CLASSIC_NOT_SAVED` a «Clavero no guarda lo que escribes». Lo primero es lo coherente con que su sesión lo borre como secreto.
  - Hay que añadir un test de fuente en `PlatformSourcesTest` (cada `OutlinedTextField` de esas dos pantallas con esas opciones). Si se elige cambiar los textos, los dos van a la lista del Abogado.

### M-1 (Minor): el KDoc de `HashLabSession` dice que el archivo elegido se conserva, pero `forget()` lo borra

- **Dónde:** `tools/hashlab/HashLabSession.kt:14` dice «The tab, the algorithm and the chosen file are not secrets and stay». En cambio, `forget()` (`:39-41`) hace `file.clear()` y `source = null`, y `hasFile` (`HashLabScreen.kt:154`) pasa a falso.
- **Fallo:** quien lea el KDoc espera que «Calcular otra vez» funcione tras un borrado, y no funciona. Lo correcto es el código: el nombre del archivo puede decir algo.
- **Arreglo:** que el KDoc diga «The tab and the algorithm are not secrets and stay; the chosen file and its result go with the rest».

### M-2 (Minor): `sha256Hex(InputStream)` y `sameHash` de `crypto/Hashes.kt` ya no tienen ningún uso en la app

- **Dónde:** `crypto/Hashes.kt:56` (`sha256Hex(input, check)`, que esta rama incluso tocó para usar `hexLower`) y `:69` (`sameHash`). Su único uso era `FileHashScreen`, que la tarea 2 quitó (`SecurityToolsScreen.kt`, diff `-1072..1155`). Ahora solo los usa `crypto/GeneratorAndChecksTest.kt:54-73`. `sha256Hex(edPublic, kemPublic)` es otra sobrecarga y sí se usa.
- **Fallo:** código muerto que sigue con tests. Además, `sameHash` compara con `==` y no en tiempo constante, cuando la decisión (b) pide comparar las huellas en tiempo constante. Si alguien lo vuelve a usar, hereda la comparación vieja en lugar de `sameDigest`/`parseExpected` del laboratorio.
- **Arreglo:** borrar las dos funciones y sus casos de `GeneratorAndChecksTest`, que ya cubren `HashLabTest` (`digest` por partes con cancelación y `parseExpected`).

### M-3 (Minor): «Comprobar una copia» rechaza una maestra de más de 1024 caracteres, que crear la bóveda, cambiar la maestra e «Importar copia» aceptan

- **Dónde:** `ui/screens/BackupCheckScreen.kt:199-200` (`MAX_PASSWORD_CHARS = 1024`, con el KDoc «Far longer than any master password») y `:135-138`. Ni `newMasterProblem` (`vault/MasterPolicy.kt:229-235`) ni el campo de la bóveda nueva ni el diálogo de «Importar copia» (`VaultBackup.kt`) ponen un tope.
- **Fallo:**
  1. Alguien crea la bóveda con una maestra de 1500 caracteres, por ejemplo una frase larga pegada desde un gestor.
  2. Su copia se importa, pero no se puede comprobar: «Ese texto es demasiado largo y no se ha pegado.».
  3. Así deja de ser verdad lo que la pantalla dice de sí misma: que comprueba lo que la importación comprobaría (`BackupCheckReport`, KDoc de `vault/BackupCheck.kt:31-39`).
- **Arreglo:** una sola constante, por ejemplo `MAX_MASTER_CHARS` en `MasterPolicy.kt`, que use «Comprobar una copia» y que `newMasterProblem` aplique solo a una maestra nueva. Así ninguna bóveda nueva supera lo que la comprobación acepta, y las que ya existen siguen abriéndose. Otra opción es subir el tope de la comprobación a uno que cubra cualquier maestra que se haya podido crear (64 KiB, por ejemplo) y corregir el KDoc. Hay que añadir el test del límite.

### M-4 (Minor): el Argon2id de una comprobación cancelada puede correr a la vez que el desbloqueo con la maestra, y este falla con un texto que no es verdad

- **Dónde:**
  - `vault/BackupCheck.kt:52` y `:89-91`: la derivación de la copia va bajo su propio `derivations` y en `NonCancellable`, así que una cancelación no la corta.
  - `vault/VaultStore.kt:319` (`derive`), dentro del `mutex` de la tienda, por ejemplo en `:909`, y lo mismo `unlock`: no comparte cerrojo con la comprobación.
  - `crypto/Crypto.kt:72`: `checkSane` acepta 128 MiB si caben en las 3/4 partes del heap, pensando en **una** derivación.
- **Fallo:**
  1. Con la bóveda bloqueada, que la pantalla permite (`SettingsScreen.kt`: «open, locked or with no vault»), el usuario comprueba una copia con el coste «Alto», que son 128 MiB.
  2. Pulsa «Cancelar» o vuelve atrás. La derivación sigue unos segundos.
  3. Va a «Bóveda» y desbloquea con la maestra, también con «Alto».
  4. Corren dos derivaciones de 128 MiB en un móvil de 192 a 256 MiB de heap. `outOfMemoryAsFailure` devuelve `KDF_OUT_OF_MEMORY` («Este móvil no tiene memoria suficiente para el coste «Alto» de Argon2id»), que es falso: el móvil sí tiene memoria para una, y un reintento funciona.

  Es poco probable, pero la 1.3.1 no podía llegar aquí: todo Argon2id de la bóveda iba bajo el mismo mutex.
- **Arreglo:** un único cerrojo de derivación en todo el proceso, un `Mutex` compartido en `crypto/` o en `vault/`, que tomen `checkBackup` y `VaultStore.derive`, y también `store.unlock`. El orden es siempre el `mutex` de la tienda y después el de derivación, y la comprobación solo toma el segundo, así que no hay interbloqueo. Test: con un `unlock` falso que espera un `CompletableDeferred`, una comprobación cancelada a mitad de su derivación hace esperar a la derivación de la tienda, como ya prueba `BackupCheckTest.afterCancelTheNextDerivationWaits` entre dos comprobaciones. Las derivaciones de «Importar de otra app» (`imports/ImportKdf.kt`, `IMPORT_KDF_RUN`) son del área A, pero deberían usar el mismo cerrojo; se lo paso al controlador.

## Comprobado y correcto

- **SLIP-0039 contra la especificación** (`slip-0039.md` y la implementación de referencia, comparando número a número):
  - RS1024: `GEN` (`tools/slip39/Rs1024.kt:12-14`), `polymod` (`:16-27`), la suma de control `polymod(cs‖data‖000) ^ 1` en tres palabras de mayor a menor (`:30-33`) y la verificación `== 1`; las cadenas «shamir» y «shamir_extendable» van según el bit `ext` (`Slip39.kt:219-221`).
  - GF(256) de Rijndael (0x11B, generador 3) y `mul` sin tablas ni ramas (`Gf256.kt:35-46`).
  - La interpolación de Lagrange con los logaritmos solo de los índices (`Shamir.kt:18-33`).
  - `shamirSplit`: T−2 partes aleatorias, el resumen `HMAC-SHA256(R, S)[:4] ‖ R` en x = 254 y S en x = 255 (`Shamir.kt:46-63`).
  - `shamirRecover` compara el resumen en tiempo constante (`Shamir.kt:69-86`).
  - Feistel de 4 rondas con `PBKDF2-HMAC-SHA256(i‖P, salt‖R, 2500·2^e, n/2)`, la sal vacía con `ext` o «shamir» más el identificador en dos bytes, la salida `R‖L` y las rondas al revés al descifrar (`Slip39.kt:336-372`).
  - La cabecera de 40 bits en las dos direcciones (`:241-250`, `:268-271`) y el relleno de como mucho 8 bits a cero (`:223-240`).
  - Al recomponer: mismo identificador, `ext`, `e`, umbral y número de grupos, y misma longitud; Gt ≤ G; número exacto de grupos y de miembros; umbral de miembros por grupo; índices distintos (`:162-179`). Una repetición exacta cuenta una vez, en tiempo lineal (`:149-159`).
  - Los 45 vectores oficiales, cada inválido por su motivo exacto (`Slip39Test.officialVectors`).
  - Los topes, siempre antes de cualquier trabajo:
    - partes: 256 como mucho, de hasta 1000 caracteres cada una y 135 936 en total (`SecretShareInput.kt:17`, `:127-129`);
    - palabras: de 20 a 59;
    - contraseña: ASCII 32-126 y hasta 256 (`Slip39.kt:325-329`);
    - exponente: de 0 a 8 al recomponer (`:170`), y siempre 1 al repartir (R4).
  - `Bip39.entropyOf` y `phraseOf`: CS = n/3 y ENT/32, y el byte alto del SHA-256 (`crypto/Bip39.kt:58-109`). La lista de 1024 palabras se comprueba con `require` y está en `checkAssets` (`app/build.gradle.kts:198`). La licencia MIT y su atribución están en `licenses/slip39.txt` y en `LicensesScreen.kt:38`.
- **Ejecuciones de SLIP-0039** (`SecretShareRuns.kt`): una sola a la vez, sobre una instantánea del formulario. `withContext(work)` espera a que termine el bloque aunque se cancele, así que la limpieza (poner a cero la maestra y la contraseña) nunca corre mientras PBKDF2 las usa. Un secreto recompuesto que llega tarde se pone a cero (`produced`, `:202-216`), y el anterior se pone a cero al reemplazarlo (`SecretShareSession.kt:267-273`).
- **Laboratorios y bóveda:** ninguno lee la bóveda; `store` solo se usa para `watchAway`. El estado vive en el ViewModel (`ComposeCells`), sin `rememberSaveable` ni `Bundle`. Las copias usan `copySensitive`. El archivo se lee por partes de 64 KiB, cancelable, y solo enseña el resultado la última ejecución (`FileHashSession.clear` y `cancelled`). La huella esperada se compara con `MessageDigest.isEqual` y tiene topes (`HashLab.kt:175-180`).
- **Enigma:** el cableado de los rotores I a V y de los reflectores B y C es el histórico, y el doble paso es correcto (`Enigma.kt:169-178`).
- **Lo que comparten «Comprobar una copia» e «Importar copia» (R5, sin desvío):**
  - `checkCopyEntries` y `attachmentInCopy` se aplican en el mismo orden en `checkBackup` (`BackupCheck.kt:95-101`) y en `importReplace` (`VaultStore.kt:917`, `:924`).
  - El `checkRestorable` que se quitó usaba `attachments.totalBytes`, que es `refs.sumOf { it.size }` (`Attachments.kt:161`), así que suma lo mismo.
  - El resultado de `read(incoming)` coincide con el de `afterMasterUnlock`: `openAny` prueba las versiones.
- **Límites y lectura de una copia:**
  - Sin carreras entre comprobar y usar: hay una sola lectura en una carpeta propia, borrada pase lo que pase.
  - La clave se pone a cero también al cancelar (`:115-118`).
  - `checkSane` limita el Argon2id de la copia (`Vault.kt:269`).
  - `BACKUP_MAX_READ_BYTES` deja 10 MB sobre los 100 MB de contenido, frente a unos 0,2 MB de sobrecarga del ZIP del propio escritor, y lee las copias v1 y v2.
- **Veracidad:** los textos de «Comprobar una copia» dicen la verdad con R6. «Importar una copia desactiva la huella» lo hace `VaultBackup.kt:175`. Un resultado no se enseña después de un bloqueo ni al salir (`lockEpoch` y `ON_STOP`).
- **`KeyOpsQueue` y `BiometricVault` (tarea 1):**
  - Sin reentrada: `clear()` se llama dentro de la fila, y `onKey`, `onError` y `onDone` después de salir de ella (`BiometricVault.kt:540-561`, `:506-517`).
  - Un `disable` cancela la activación en curso o en espera. El registro en `enables` es síncrono en el hilo principal (`UNDISPATCHED`, `:370`), así que no se cuela una activación entre `cancelEnable` y `withLock`.
  - Una activación cancelada borra su clave y pone a cero las copias dentro de la fila (`:381-385`).
  - Si llega a escribir las preferencias justo antes de la cancelación, la desactivación que va detrás lo borra.
  - `withContext(dispatcher)` mantiene el cerrojo hasta que el bloque vuelve (`KeyOpsQueue.kt:41`).
  - Las seis llamadas esperan a `disable` antes de seguir: `VaultBackup.kt:131` y `:175`, y `VaultScreen.kt:181`, `:298`, `:340` (dentro de `onWrongKey`, ahora `suspend`) y `:397`.
  - `enabled()` y `keyLevel()` se leen fuera de la fila, pero cada transición escribe las preferencias como último paso al activar y como primero al borrar, así que nunca enseñan un estado a medias (diseño, «las lecturas que deciden algo»).
- **`MasterPolicy` y RT-3 (decisión (h)):**
  - Las sustituciones son las de la decisión: 0, 3, 4/@, 5/$, 7/+, 8, 6/9, y 1/!/| como «i» o como «l».
  - `lead` y `tail` van sin letras, y `middle` lleva siempre todas.
  - El NFKC se aplica antes, y en `masterCore` también.
  - El tope es de 128 puntos de código, con unas 4 160 subcadenas por lectura como mucho; en el peor caso son alrededor de un millón de caracteres copiados, unos milisegundos por pulsación.
  - Una frase del Generador nunca da un núcleo de 10 letras o menos, porque el leet solo añade letras.
  - Solo se aplica a una maestra nueva.
- **Ajustes, `importCopies` y A6:** las copias de antes de importar están en `oldCopies` (`AppContainer.kt:296-297`) y en los destinos de la petición (`Settings.kt`, `recording`). Se olvidan con una bóveda nueva (`forgettingAutoBackupCopies`). `SettingsStore` las conserva al restablecer. El nombre `seguridad-antes-de-importar-` del texto de `SettingsScreen.kt:284-286` coincide con `AutoBackup.kt:99`.
- **Visualización (R9):** `plainReportText` se aplica en `ContactsView`, `PkActions`, `HealthScreen`, `OtpQrScreen`, `PkActionsScreen`, `VaultScreen`, `VaultEntryScreen` y en las cabeceras de contactos. `normalizeTags` corta por puntos de código.
- **Nombres de otras apps:** «Correo»/«Cartera» (R35) y la atribución de SLIP-0039 (SatoshiLabs, `trezor` en la URL), que es atribución de licencia como R8a y ya está en la lista del Abogado.

## Revisión experto-seguridad-cripto

- **Primitivas estándar:** HMAC-SHA256 de JCA (`Shamir.kt:35-40`); PBKDF2 y los resúmenes de Bouncy Castle (`Slip39.kt:364-367`, `HashLab.kt:33-39`); `java.util.Base64`. La única aritmética propia es la que define SLIP-0039, GF(256) y Lagrange, y la validan los 45 vectores oficiales.
- **Tiempo constante (decisión (b)):**
  - en el resumen de Shamir (`Shamir.kt:76`), en `Share.sameAs` (`Slip39.kt:71`) y en la huella esperada (`HashLab.kt:98`);
  - `Gf256.mul` no usa tablas con bytes secretos (`Gf256.kt:35-46`);
  - excepción: `sameHash`, que ya no se usa (M-2).
- **Secretos a cero:**
  - el secreto y las partes intermedias (`Shamir.kt:57-62`, `Slip39.kt:110-134`, `:197-200`);
  - el secreto recompuesto al reemplazarlo (`SecretShareSession.kt:267-273`) o cuando llega tarde (`SecretShareRuns.kt:214`);
  - la clave de la copia en «Comprobar» (`BackupCheck.kt:116`);
  - la maestra de la comprobación (`BackupCheckSession.kt:78`, `:105`);
  - las claves de la activación de la huella (`BiometricVault.kt:383`, `:511`).
- **Entradas hostiles (decisión (a)):**
  - Todo campo tiene un tope antes de leerse: `SHARE_MAX_*`, `MAX_TEXT_CHARS`, `MAX_CODEC_CHARS` y `MAX_CLASSIC_CHARS`.
  - En las copias, `BoundedInput` (`Backup.kt`) cuenta los bytes reales del archivo y comprueba la cancelación en cada lectura, también con bloques deflate vacíos.
  - El exponente de SLIP-0039 se rechaza antes de PBKDF2.
  - El leet tiene tope (`MasterPolicy.kt:104`).
- **Mensajes de error:** no llevan contenido. Los de SLIP-0039 dicen el número de la parte y nunca una palabra (`SecretShareTexts.kt:90-114`). El informe de la copia da recuentos, sin títulos ni nombres de adjuntos (`BackupCheck.kt:105-114`).
- **Pendiente:** I-1, el teclado que aprende (MASVS-STORAGE-2), y M-4, las derivaciones simultáneas.

## Revisión Ingeniero Móvil Senior

- **Ciclo de vida y estado:**
  - Los tres laboratorios y «Comprobar una copia» guardan su estado en un ViewModel con `ComposeCells`, sobreviven al giro y nunca usan `rememberSaveable` para secretos (`HashLabScreen.kt:125-243`, `SecretShareScreen.kt:114-274`, `BackupCheckScreen.kt:97-197`).
  - `onCleared` borra y para.
  - `watchAway` y `GuardEffects` aplican la regla de «PIN y códigos» (`ScreenGuardWiring.kt:47-72`).
  - «Comprobar» para al salir de la app, salvo en un giro (`BackupCheckScreen.kt:210`).
- **Concurrencia:**
  - `KeyOpsQueue` va en un hilo propio y el prompt en el principal (`BiometricVault.kt:183`, `:443-446`). No hay reentrada ni espera del hilo principal a la fila.
  - Para PBKDF2 se usa `Dispatchers.Default` y para los archivos, `IO`. `withContext` espera al bloque cancelado antes de la limpieza.
  - En «Comprobar una copia», `NonCancellable` va solo donde una cancelación perdería la carpeta o la clave (`BackupCheck.kt:80`, `:90`, `:117`).
- **Hilo principal:** en el hilo principal solo corren el hash de un texto de hasta 100 000 caracteres (`HashLabScreen.kt:303`), los cifrados clásicos de hasta 20 000 y `newMasterProblem` con el leet acotado; todo dura milisegundos. Las listas de palabras se leen en `IO` (`SecretShareScreen.kt:146`, `MasterPolicy.kt:162-174`).
- **Pendiente:** I-1, que pide las `KeyboardOptions` de los campos secretos, como en «Cifrar texto», y M-4, un cerrojo de derivación para todo el proceso.

## Textos para el Abogado Digital (revisión final)

Ya están en la lista consolidada de `task-12-report.md` (`:117`, `:120`, `:123`, `:126-127`) todos los textos de estas pantallas. Para la revisión final hay que añadir solo:

- Si I-1 se arregla cambiando los textos y no el teclado: los nuevos `HASH_LAB_HMAC_INTRO` y `CLASSIC_NOT_SAVED`. Si se arregla con el teclado, no cambia ningún texto.
- Si M-3 se arregla con un tope para una maestra nueva: el mensaje nuevo que lo diga, si lo hay.
- El texto `KDF_OUT_OF_MEMORY` (`crypto/Crypto.kt:81`) no es nuevo, pero M-4 muestra un caso en que es falso. Con M-4 arreglado vuelve a ser verdad y no hace falta cambiarlo.
