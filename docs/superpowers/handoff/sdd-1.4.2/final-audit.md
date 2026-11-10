# Auditoría final de la rama 1.4.2: el código de `app/`

**Rama:** `ccr-a1a6eabb-765f9g`, auditada en `3323315` (base `main` en `ba903e7`).

**Alcance:** `git diff ba903e7..HEAD -- app/`:
1. `AndroidManifest.xml`: `configChanges="orientation|screenSize|screenLayout|smallestScreenSize"` en `MainActivity` (F-5).
2. Los sitios de los selectores (M-2): `core/PickerPlaces.kt`, `android/PrefsPlaceStore.kt`, `AppContainer.kt` (el colector que olvida), `SeguridadApp.kt`, `ui/screens/Pickers.kt` (`rememberDocumentPicker`, `StartingAt`, `folderStart`) y sus 28 llamadas (27 con sitio y la de la carpeta de la copia automática, con `place = null`).
3. `VaultScreen.kt`: el aviso de la activación interrumpida, marcado en un `finally`, y `forgetAll()` antes de «Borrar bóveda».
4. Importar (F-6): `imports/ImportModel.kt` (`SkipReason.LOGIN_TOTP_BROKEN`, `ofLogin`), `BitwardenJson.kt` (`addLoginTotp`) y `CsvFormats.kt` (la columna `OTPAuth`).
5. El texto mínimo (M-3): `ui/screens/MoreInfo.kt` (`MoreInfo`, `AwayNote`), los `*_SHORT` de `tools/ToolTexts.kt` y `HashLabTexts.kt`, y las 21 pantallas que pliegan texto.

Fuera de `app/` no se ha mirado nada salvo `docs/privacidad.html:38`, el párrafo de los selectores, porque describe el código de M-2. `build.gradle.kts:35` solo cambia `versionName` a 1.4.2. `MasterPolicy.kt` solo cambia el KDoc (RT-11, opción B).

**Modelo de amenaza:**
- Un archivo hostil importado, o un proveedor de documentos hostil que devuelve URI de su elección.
- Quien tiene el móvil, ya desbloqueado, después de que la bóveda se bloquee.
- La memoria del proceso (root o un depurador) y los archivos privados de la app (root o un análisis forense del móvil).
- El ciclo de vida: un giro que ya no rehace la actividad, una actividad que se rehace por tema o idioma, la muerte del proceso y un bloqueo con un selector abierto.

No entran la red ni la criptografía: el diff no las toca.

**Método:**
- Leí todo el diff de `app/` y el código que lo rodea: `MainActivity.kt`, `VaultStore.lock`, el bloqueo al salir (`VaultLock.keepOpenOnce`), «Abrir lo recibido» y `RedactZoom`.
- Seguí cada uno de los 24 `PickerPlace` hasta su pantalla: si es `kept`, qué nombre propone el selector y si la pantalla mantiene la bóveda abierta mientras está fuera.
- Comparé cada texto plegado con la línea corta que queda a la vista.
- Busqué en el diff `rememberSaveable`, `SavedStateHandle`, `Bundle`, `Log.`, `println`, `exported`, `uses-permission`, hosts y dependencias nuevas.
- Leí `backup_rules.xml` y `data_extraction_rules.xml`.
- Pasé los tests relacionados en `3323315`, todos en verde: `PlatformSourcesTest` (31), `PickerPlacesTest` (6), `BitwardenJsonTest` (30), `CsvFormatsTest` (32), `ImportTextsTest` (17) y `ToolTextsTest` (22).

No escribí ningún test temporal. No hay emulador en el contenedor, así que nada se ha reproducido en un móvil.

**Estado del árbol:** hacia el final de la auditoría, después de pasar los tests (21:48 UTC), aparecieron en el árbol de trabajo cambios sin commit que no son míos (hacia las 21:53). Según sus comentarios, son los cambios de la revisión del Abogado (`sdd-1.4.2/abogado-1.4.2.md`, O-4 y R-9 entre otros) y tocan:
- `ToolTexts.kt`, `ImportTexts.kt`, `HashLabTexts.kt`, `SecretShareTexts.kt` y `BackupCheckTexts.kt`;
- `HashLabScreen.kt`, `SecretShareScreen.kt`, `BackupCheckScreen.kt` y `SettingsScreen.kt`;
- `CsvFormatsTest.kt` y el anexo de textos.

Este informe es sobre `3323315`: todas las líneas que cito son de `HEAD`, comprobadas con `git show HEAD:…`. Esos cambios parecen cubrir:
- el hallazgo 3 (las líneas `*_NOT_PROTECTED_SHORT` a la vista);
- y parte del 4 (`PHOTO_SHORT` y `ENCRYPT_HELP_SHORT`).

No los he auditado. Hay que comprobarlos cuando entren en un commit.

## Resumen

| # | Severidad | Hallazgo | Ubicación |
|---|---|---|---|
| 1 | Bajo | Los sitios «solo en memoria» de las herramientas que no mantienen la bóveda abierta se apuntan después del bloqueo, y sobreviven a cada salida de la app hasta el siguiente desbloqueo | `AppContainer.kt:327`, `Pickers.kt:68`, `MainActivity.kt:113`, `PickerPlaces.kt:22-32` |
| 2 | Bajo | Algunos sitios que se guardan en disco llevan el nombre de un archivo en claro: «Abrir lo recibido» (lo cifrado, la firma, el archivo firmado y el texto) y las salidas que se llaman como el original; la política dice que lo que se descifra no se apunta | `PickerPlaces.kt:31`, `:34`, `:36`, `:37`; `PkActionsScreen.kt:406`, `:525`, `:693-740`; `SecurityToolsScreen.kt:895`; `docs/privacidad.html:38` |
| 3 | Bajo | «Lo que no protege» del Laboratorio de hashes, de Repartir un secreto y de Comprobar una copia queda plegado bajo una línea que solo habla del borrado; «✓ Coincide» sale sin matiz | `HashLabScreen.kt:392-397`, `:462`; `SecretShareScreen.kt:297-301`; `BackupCheckScreen.kt:244-249` |
| 4 | Info | Otros avisos de seguridad plegados sin línea corta que los anuncie, y dos líneas cortas que dicen menos o algo más que el texto completo | `SettingsScreen.kt:448`, `:455`; `PkActionsScreen.kt:331-332`; `CodesScreen.kt:362`; `ImportScreen.kt:613`; `ToolTexts.kt:646` |
| 5 | Info | Con `configChanges`, unos comentarios dicen que el giro rehace la composición, y el zoom de Tachar ya no se pierde al girar ni se ajusta al tamaño nuevo | `PkActionsScreen.kt:268`, `:270`, `:460`; `RedactScreen.kt:572`, `:596`; `RedactZoom.kt:32-41` |

Crítico: 0. Alto: 0. Medio: 0. Bajo: 3. Info: 2.

## Hallazgos

### 1. [Bajo] El último sitio privado de varias herramientas sobrevive al bloqueo

- **Dónde:**
  - `core/PickerPlaces.kt:22-32`: `HASH_FILE`, `QR_IMAGE`, `PHOTO_OPEN`, `REDACT_OPEN`, `TEXT_OPEN`, `FILE_OPEN` y `FILE_DECRYPTED_SAVE` son `kept = false`. El KDoc (`:8-13`) dice que se olvidan «at every lock and unlock of the vault».
  - `AppContainer.kt:327`: el único sitio que los olvida es la transición de la bóveda entre abierta y cerrada (`distinctUntilChanged().drop(1)`).
  - `MainActivity.kt:113`: salir de la app bloquea, salvo si `VaultLock.keepOpenOnce`.
  - Esos siete selectores no ponen `keepOpenOnce = true`:
    - `HashLabScreen.kt:308`;
    - `SecurityToolsScreen.kt:415`, `:816` y `:858`;
    - `PhotoCleanScreen.kt:360`;
    - `RedactScreen.kt:511`;
    - el selector de imagen de `QrInspectScreen.kt:199`.

    Comprobado con `grep -rn "keepOpenOnce = true"`: solo lo ponen las pantallas que necesitan la bóveda. El comentario de `QrInspectScreen.kt:198` lo dice: «the picker does not keep the vault open».
  - `Pickers.kt:68`: el sitio se apunta al volver el resultado.
- **Qué pasa:**
  1. Con la bóveda abierta, el usuario está en «Cifrar archivos» y pulsa «Elegir archivo».
  2. Se abre el selector, se llama a `onStop` y la bóveda se bloquea (`LockReason.LEFT_APP`). El colector olvida los sitios en memoria, pero todavía no hay nada que olvidar.
  3. El usuario elige `…/Salud/informe.pdf`, y `remember(FILE_OPEN, …)` lo apunta en memoria, **ya con la bóveda bloqueada**.
  4. A partir de ahí, salir de la app ya no cambia el estado de la bóveda, que sigue bloqueada, y el colector no vuelve a correr. El sitio se queda hasta el siguiente desbloqueo o hasta que muera el proceso.
  5. Quien coge el móvil (desbloqueado) después abre Clavero, que sigue vivo, entra en «Cifrar archivos» y pulsa «Elegir archivo». El selector del sistema se abre en la carpeta de ese archivo. Con el proveedor del almacenamiento del móvil, también ve su nombre. No hace falta la maestra: las herramientas funcionan con la bóveda bloqueada.

  Lo mismo pasa sin bóveda (quien solo usa las herramientas): ningún sitio en memoria se olvida antes de que muera el proceso. Y, como dice el comentario de `AppContainer.kt:325`, un bloqueo y un desbloqueo más rápidos que el colector cuentan como ninguno.
- **Impacto:** quien tiene el móvil después de un bloqueo puede ver la carpeta, y a veces el nombre, del último archivo que se cifró, descifró o del que se calculó un hash, de la última foto que se limpió o se tachó, del último QR leído de una imagen y del último texto abierto. Es justo lo que `kept = false` quería evitar. El contenido no se ve.

  La severidad es baja porque esa persona ya tiene el móvil desbloqueado y podría buscar en el explorador de archivos, aunque sin saber qué usó el dueño en Clavero. No sale nada del proceso ni del móvil.

  La política (`docs/privacidad.html:38`, «solo lo recuerdan en la memoria de la app, hasta que la bóveda se bloquea o se abre, o hasta que la app se cierra») es cierta al pie de la letra, porque se olvida al abrirse, pero un lector entiende que bloquear la bóveda los olvida, y con estas siete opciones el último sitio llega siempre después del bloqueo.
- **Corrección:** olvidar los sitios en memoria también al salir de verdad de la app, no solo con la bóveda.
  1. Que el selector diga que está abierto, para que el `onStop` de su propio lanzamiento no lo cuente como salir.
  2. Olvidar al salir por cualquier otra causa.

  Por ejemplo:
  ```kotlin
  // PickerPlaces
  @Volatile var pickerOpen = false
  fun leftApp() { if (!pickerOpen) session.clear() }

  // Pickers.kt, StartingAt.createIntent (la única vía de lanzamiento): marca antes de salir
  places?.pickerOpen = true
  // y en el callback de rememberLauncherForActivityResult, primero:
  places?.pickerOpen = false

  // MainActivity.onStop, junto al bloqueo (mismas condiciones que la línea 113, sin keepOpenOnce):
  if (!isChangingConfigurations) container.pickerPlaces.leftApp()
  ```
  - El colector de `AppContainer.kt:327` se queda tal cual.
  - Un selector que se cancela devuelve `null` por el mismo callback, así que la marca se baja igual.
  - Si muere el proceso con el selector abierto, la marca muere con él.
  - **Test:** un `PickerPlacesTest` que apunte un sitio en memoria con la bóveda ya bloqueada, llame a `leftApp()` y compruebe que `initial()` da `null`, y otro que compruebe que `leftApp()` con `pickerOpen` no lo olvida. `PlatformSourcesTest` exigiría la llamada en `onStop`.
  - **Prueba en el móvil:** abrir la bóveda, elegir un archivo en el Laboratorio de hashes, ir a Inicio, volver y abrir otra vez el selector del laboratorio: no debe abrirse en esa carpeta.
- **Referencia:** MASVS-STORAGE-2 (datos sensibles, aquí metadatos, retenidos más de lo necesario); CWE-359.

### 2. [Bajo] Sitios guardados en disco con el nombre de un archivo en claro

- **Dónde:**
  - `PickerPlaces.kt:37`: `RECEIVED_OPEN` es `kept` (se guarda en `picker_places`). Su selector, `PkActionsScreen.kt:693`, es el único de «Abrir lo recibido», y elige:
    - el `.sgenc` que se descifra;
    - la firma `.sgsig`;
    - después, «Ahora elige el archivo que firma.» (`:740`), es decir, el archivo en claro que se comprueba;
    - o un archivo de texto con un mensaje (`PickedFile.Text`, `:725`).

    El último que se elige es, muchas veces, el archivo firmado.
  - `PickerPlaces.kt:31`, `:34` y `:36`: `FILE_ENCRYPTED_SAVE`, `ENCRYPT_FOR_SAVE` y `SIGN_SAVE` son `kept`, y sus selectores proponen el nombre del original:
    - `SecurityToolsScreen.kt:895`: `encryptedFileName(name)`, es decir, `<original>.sgenc`;
    - `PkActionsScreen.kt:406`: lo mismo, salvo con «No enviar el nombre»;
    - `PkActionsScreen.kt:525`: `signatureFileName(sourceName)`, es decir, `<original>.sgsig`.

    En cambio, `FILE_OPEN`, `ENCRYPT_FOR_OPEN` y `SIGN_OPEN`, que llevan ese mismo nombre, son `kept = false` (`:30`, `:33`, `:35`) justo por eso (KDoc `:8-13`, `code-review` de la 1.4.2, hallazgo 1).
  - `docs/privacidad.html:38`: «Las opciones que leen o escriben datos privados (… el archivo que cifras, firmas, descifras …) no lo apuntan». El `.sgenc` que se descifra en «Abrir lo recibido», y el archivo cuya firma se comprueba, sí se apuntan.
- **Qué pasa:** el URI de un documento del proveedor del almacenamiento del móvil lleva la ruta, por ejemplo `content://com.android.externalstorage.documents/document/primary%3ADocuments%2FSalud%2Finforme.pdf.sgenc`. Ese texto se queda en `shared_prefs/picker_places.xml`:
  - después de borrar el archivo;
  - después de cada bloqueo;
  - y después de cerrar la app.

  Solo «Borrar bóveda» lo quita. Pasa lo mismo con `informe.pdf.sgsig` y con el archivo firmado que se comprobó. Con Descargas y con las nubes, el identificador suele ser opaco (`msf:1234`) y no lleva el nombre.
- **Impacto:**
  - Ninguna copia de Android lo incluye (`backup_rules.xml` y `data_extraction_rules.xml` excluyen `sharedpref`; `allowBackup="false"`), y otra app no lo puede leer. Hace falta root o un análisis forense. Por eso es bajo.
  - Pero contradice la regla del propio diseño, que no deja fuera de la bóveda el nombre de un archivo en claro, y la política dice algo que no es del todo cierto para «Abrir lo recibido». Por la regla de veracidad del proyecto, eso es lo que importa.
- **Corrección:** una de estas dos.
  - **(a), la más simple y la que recomiendo:**
    - `RECEIVED_OPEN(kept = false)`;
    - `SIGN_SAVE(kept = false)`;
    - `FILE_ENCRYPTED_SAVE(kept = false)`;
    - `ENCRYPT_FOR_SAVE` apuntado solo en memoria si no se ocultó el nombre. Como el sitio es de la llamada y no del lanzamiento, lo más limpio es partirlo en dos: `ENCRYPT_FOR_SAVE` (kept, con «No enviar el nombre») y `ENCRYPT_FOR_SAVE_NAMED(kept = false)`, elegidos en `PkActionsScreen.kt:286` según `hideName`. También vale `kept = false` para los dos casos.

    La política no cambia. Lo que se pierde es que esas opciones vuelvan a su carpeta después de cerrar la app.
  - **(b):** dejar el código y cambiar la política y el KDoc, para que digan que la dirección que se apunta puede llevar el nombre del archivo y que «Abrir lo recibido», la firma y lo cifrado sí se apuntan. Esto no arregla la regla.

  **Test:** `PlatformSourcesTest`, `every file picker opens where it was used last`, fijaría los nuevos `kept = false`.
- **Referencia:** MASVS-STORAGE-2 y MASVS-PRIVACY-1 (minimización); CWE-359.

### 3. [Bajo] «Lo que no protege», plegado bajo la línea del borrado

- **Dónde:**
  - `HashLabScreen.kt:392-397`: `AwayNote { HASH_LAB_INTRO; HASH_LAB_HMAC_INTRO; HASH_LAB_NOT_PROTECTED_TITLE; HASH_LAB_NOT_PROTECTED }`. Antes era una `SectionCard` a la vista.
  - `HashLabScreen.kt:462`: el resultado es solo «✓ Coincide» (`HASH_LAB_MATCH`), en verde.
  - `HashLabTexts.kt:12`: la línea a la vista, `HASH_LAB_SHORT`, es «… compárala con la publicada.».
  - `SecretShareScreen.kt:297-301`: `SHARE_NOT_PROTECTED` («Quien reúna las partes necesarias obtiene el secreto… Guarda cada parte en un sitio distinto. Una contraseña equivocada no da ningún error…»), antes en una `SectionCard`.
  - `BackupCheckScreen.kt:244-249`: `BACKUP_CHECK_NOT_PROVEN` («No dice si es tu copia más reciente… Importar una copia desactiva la huella…»), antes en una `SectionCard`.
  - `MoreInfo.kt:27-30` (KDoc): «Only explanations go here, never a warning the user must see.»
- **Qué pasa:** `AwayNote` enseña «Se borra al volver atrás…» y pliega debajo, bajo un único «Más información», tanto los casos del borrado como las explicaciones de la pantalla. Quien lee la línea corta espera encontrar debajo más detalle del borrado, no lo que la herramienta no garantiza.
  - El caso que más pesa es el del Laboratorio de hashes. Su texto completo (`HASH_LAB_INTRO`) propone comprobar «el firmware de una cartera física». Ahora la pantalla enseña «compárala con la publicada» y un «✓ Coincide» verde, y en ningún sitio a la vista dice que una huella sacada de la misma web que el archivo no prueba nada.
  - En Repartir un secreto, lo de la contraseña equivocada sigue a la vista junto al campo (`SHARE_PASSPHRASE_NOTE`, `SecretShareScreen.kt:424`) y el umbral está en `SHARE_SHORT`. Lo que queda plegado es «quien reúna las partes obtiene el secreto» y «guarda cada parte en un sitio distinto».
  - En Comprobar una copia, una copia que se abre bien puede hacer creer que es la última. Que importarla desactiva la huella solo se lee plegado.
- **Impacto:** de uso, con consecuencias de seguridad. Un usuario puede tomar «✓ Coincide» como prueba de autenticidad e instalar un firmware o un APK cambiado junto con su huella. No hay una vía técnica de ataque nueva: el atacante tiene que controlar la descarga y la huella, que es el caso que el aviso describía.
- **Corrección:**
  - Volver a poner a la vista una línea de «Lo que no protege» en las tres pantallas. Por ejemplo, para el Abogado:
    - `HASH_LAB_NOT_PROTECTED_SHORT = "Que coincida no prueba quién la publicó: tómala de otra fuente que la descarga."`;
    - `SHARE_NOT_PROTECTED_SHORT = "Quien reúna las partes necesarias obtiene el secreto: guarda cada una en un sitio distinto."`;
    - `BACKUP_CHECK_NOT_PROVEN_SHORT = "No dice si es tu copia más reciente."`.
  - Mejor todavía, separar `AwayNote` de las explicaciones: el `MoreInfo` propio de la pantalla bajo su línea corta (como ya hacen `PkActionsScreen.kt:331-332` o `PhotoCleanScreen`) y `AwayNote()` sin `more`.
  - **Test:** que `ToolTextsTest` fije las líneas nuevas, y que `PlatformSourcesTest` compruebe que `HASH_LAB_NOT_PROTECTED_SHORT` está fuera de `MoreInfo`/`AwayNote`.
- **Referencia:** MASVS-PLATFORM (la interfaz no induce a error en decisiones de seguridad); NIST SP 800-107 y la práctica de verificar las huellas por un canal independiente.

### 4. [Info] Otros avisos plegados, y dos líneas cortas que hay que afinar

Ninguno abre una vía de ataque. Los apunto para el dueño y para el Abogado, porque M-3 dice que no se pliega ningún aviso:
- `SettingsScreen.kt:455`: `CLIPBOARD_CLEAR_HINT` queda plegado sin línea corta. Incluye dos cosas: «si compartes el portapapeles con otros dispositivos, allí puede quedarse», y que en segundo plano la app vacía el portapapeles aunque hayas copiado otra cosa. Las dos cambian lo que espera el usuario.
- `SettingsScreen.kt:448`: `LOCK_ON_EXIT_HINT` queda plegado sin línea corta. Dice cuándo **no** se bloquea al salir: con un selector o el escáner abiertos, y con la pantalla dividida o una ventana flotante. Es la excepción a «se bloquea al salir».
- `PkActionsScreen.kt:331-332`: `ENCRYPT_HELP` queda plegado. Dice que no hay secreto hacia adelante y que quien vea el mensaje sabe que existe, su tamaño y cuándo se envió. La línea corta (`ENCRYPT_HELP_SHORT`) es cierta, y `IDENTITY_RISK` sigue a la vista en la pantalla de la identidad (`IdentityScreen.kt:124`, `:153`), pero no en «Cifrar para…».
- `CodesScreen.kt:362`: queda plegado `ANSWERS_WARNINGS`: «Si pierdes la bóveda y sus copias, pierdes estas respuestas.»
- `ImportScreen.kt:613`: `IMPORT_CONFIRM_SHORT` no dice «Sin esa copia no se importa nada» ni que se pedirá dónde guardarla. Es lo que confundió al dueño en la prueba de P-7 («al principio parecía que volvía atrás»). Propuesta: «Antes de guardar, Clavero hace una copia cifrada de la bóveda; sin ella no se importa nada.»
- `ToolTexts.kt:646`: `PHOTO_SHORT` dice «Quita… la ubicación, el modelo del móvil y otros datos ocultos». El texto completo aclara que no quita «las marcas invisibles que pueda llevar la propia imagen ni el rastro que deja el sensor». «Otros datos ocultos» puede leerse como si lo quitara todo. Propuesta: «y otros datos que guarda el archivo».

### 5. [Info] El giro ya no rehace la composición: comentarios y el zoom de Tachar

- **Dónde:**
  - Comentarios que dicen que el giro pierde el estado de `remember`:
    - `PkActionsScreen.kt:268`: «it is lost when the phone rotates»;
    - `PkActionsScreen.kt:270` y `:460`: «a rotation loses it»;
    - `RedactScreen.kt:572`: «a rotation re-creates the composition»;
    - `RedactScreen.kt:596`: «lost on rotation (the composition is re-created)».
  - `RedactZoom.kt:32-41`: `offsetX`/`offsetY` están en píxeles de la vista y solo se ajustan en `transformed`, con cada gesto de dos dedos.
- **Qué pasa:** con `configChanges`, un giro ya no rehace la actividad ni la composición.
  - El texto en claro de «Cifrar para…» y «Firmar» se queda al girar. No vive más que antes: se va con la pantalla y con el bloqueo, como siempre, y nunca en un `Bundle`. Pero los comentarios, que justifican usar `remember`, ya no son ciertos.
  - En Tachar, un zoom hecho en vertical se queda al girar. Su desplazamiento puede salirse del rango de la vista nueva (`viewW * (1 - s)..0`), y la imagen se enseña corrida, con un margen vacío, hasta el siguiente gesto.

  He comprobado que no afecta a lo que se tapa: el dibujo (`RedactScreen.kt:695-696`) y el toque (`toPreviewX`/`toPreviewY`, con `boxFromDrag`) usan el mismo `zoom` y el tamaño de ahora. Un bloque cubre lo que hay bajo el dedo y nunca partes que no se ven.
- **Corrección:**
  - Actualizar los cinco comentarios: «se pierde si la actividad se rehace (tema, idioma), no al girar».
  - En `RedactCanvas`, quitar el zoom cuando cambia el tamaño, con `var zoom by remember(p, w, h) { … }`, o volver a encajarlo en el rango con un `transformed(1f, 0f, 0f, 0f, 0f, viewW, viewH)` al medir.

## Lo que está bien

- **`configChanges` (F-5):**
  - `FLAG_SECURE` se pone una sola vez, en `onCreate` (`MainActivity.kt:33`), sobre la ventana, que ya no se rehace con un giro. Las que se rehacen (tema, idioma) lo vuelven a poner.
  - El bloqueo de `onStop` (`:113`) y el de la ventana múltiple (`:85-91`) siguen con `isChangingConfigurations`. Con un giro, ahora ni siquiera hay `onStop`.
  - `SecureCaptureActivity` no cambia.
  - No hay ningún componente, `intent-filter`, permiso ni extra nuevo.
  - `PlatformSourcesTest` fija el valor exacto.
- **El aviso de la activación interrumpida** (`VaultScreen.kt:163-174`): se marca como visto en el `finally`, salvo si la actividad se está rehaciendo. Un cambio de tema mientras está en pantalla deja que la actividad nueva lo enseñe.
- **Los sitios de los selectores:**
  - Solo se apunta un documento que ha pasado `isAcceptableDocument`, la comprobación con el `PackageManager` (`Pickers.kt:66-68`).
  - `acceptable()` lo vuelve a comprobar al leerlo, con un tope de 2048 caracteres (`PickerPlaces.kt:93-98`).
  - El URI solo va como `EXTRA_INITIAL_URI` a un selector del sistema. Clavero no lo abre, no pide permisos sobre él y nunca lo pone en otro `Intent`.
  - Un proveedor hostil solo consigue que el selector intente empezar en uno de sus documentos, que el usuario ya eligió antes.
  - Unas preferencias que fallan no rompen nada (`runCatching`).
  - `picker_places` es privado, con `MODE_PRIVATE`, y está fuera de todas las copias: `allowBackup="false"` y `sharedpref` excluido en `backup_rules.xml` y en `data_extraction_rules.xml`, para la nube y para el paso a otro móvil.
  - «Borrar bóveda» los olvida antes de `destroy()` (`VaultScreen.kt:411`), y es el único `destroy()` de la app.
  - `folderStart` no lanza excepciones y solo acepta `content`.
- **F-6:**
  - `LOGIN_TOTP_BROKEN` solo cambia el motivo que se enseña. Los topes (`OTPAUTH_MAX_CHARS`, `otpNameFits`) van antes de construir nada, y el 2FA dañado no entra.
  - HOTP, Steam y los ajustes no admitidos conservan sus motivos.
  - `SkipReason` no se guarda por su ordinal en ningún sitio.
- **El texto mínimo:**
  - `MoreInfo` solo guarda en `rememberSaveable` un booleano, sin secretos.
  - Ningún error, resultado ni diálogo de confirmación ha quedado plegado: busqué `NegativeColor`, `error` y `failed` dentro de los `MoreInfo`.
  - Siguen a la vista el aviso de un contacto sin verificar, `SIMILAR_NAME_WARNING`, `IDENTITY_RISK`, la línea de marcas, `IMPORT_NOT_PROTECTED_SHORT` en cada paso y `IMPORT_REPLACE_SHORT`.
  - `PASSWORD_CHANGE_OLD_BACKUPS` sigue en el diálogo de cambiar la maestra (`SettingsScreen.kt:335`), así que plegar el párrafo de las copias de Ajustes no esconde que las copias anteriores siguen abriéndose con la maestra anterior.
- **Lo que no hay:** nada nuevo en `rememberSaveable` con secretos, `SavedStateHandle` o `Bundle`. Ningún `Log.` ni `println`, conexión, host, permiso, componente exportado ni dependencia nuevos (`app/build.gradle.kts` solo cambia `versionName`).

## Siguientes pasos

1. Hallazgo 2: decidir entre (a) y (b). (a) es un cambio de cuatro líneas en `PickerPlaces.kt` y deja la política como está.
2. Hallazgo 1: olvidar los sitios en memoria también al salir de la app, con la marca de selector abierto.
3. Hallazgo 3: volver a poner a la vista una línea de «Lo que no protege» en las tres pantallas. Los textos, para el Abogado.
4. Hallazgos 4 y 5: textos y comentarios, en la misma ronda.

## Revisión experto-seguridad-cripto

- **Criptografía:** el diff no cambia ningún código criptográfico, ningún formato (`SG1:`, `.sgenc`, `seguridad-vault`, `seguridad-backup`) ni ningún parámetro de KDF. `MasterPolicy.kt` solo cambia el KDoc (RT-11, opción B).
- **Entradas hostiles (decisión (c)):**
  - El URI que devuelve un proveedor se trata como dato: se comprueba al apuntarlo (`Pickers.kt:66-68`) y al leerlo (`PickerPlaces.kt:93-98`, con un tope de 2048), y solo vuelve al selector del sistema como indicación (`Pickers.kt:88-91`).
  - En F-6, un 2FA hostil de un acceso sigue pasando por `readOtpAuth` con sus topes antes de nada (`ImportModel.kt:263-283`, `:308-313`). Solo cambia el texto del motivo.
- **Metadatos de archivos privados:** los hallazgos 1 y 2 son de minimización. El nombre o la carpeta de un archivo en claro se queda en memoria después del bloqueo (`AppContainer.kt:327` frente a `MainActivity.kt:113`) o en `picker_places` (`PickerPlaces.kt:31`, `:34`, `:36`, `:37`). Ninguno expone contenido ni sale del móvil.
- **Veracidad:** `docs/privacidad.html:38` no es exacta para «Abrir lo recibido» (hallazgo 2). La línea a la vista del Laboratorio de hashes deja de decir lo que una huella no prueba (hallazgo 3).
- **Copias:** `backup_rules.xml` y `data_extraction_rules.xml` excluyen `sharedpref`, y por tanto `picker_places`, para la copia en la nube y para el paso a otro móvil; `allowBackup="false"` (`AndroidManifest.xml:11`).

## Revisión Ingeniero Móvil Senior

- **Ciclo de vida:**
  - `configChanges` (`AndroidManifest.xml:23`) cubre el giro, el plegado y el cambio de tamaño. El tema, el idioma, la fuente y la densidad siguen rehaciendo la actividad, con M-4 para la huella.
  - `onStop` y la ventana múltiple siguen con `isChangingConfigurations` (`MainActivity.kt:85-91`, `:110-113`).
  - El estado secreto sigue en ViewModels o en `AppContainer`. Ahora un giro ya no lo pone a prueba: solo lo hacen el tema y la muerte del proceso. Conviene que la prueba en el móvil de «sobrevive al giro» se haga también cambiando el tema.
- **Compose:**
  - `StartingAt` es un contrato nuevo en cada recomposición, como ya lo eran los `ActivityResultContracts.*()` de cada llamada. No es un cambio de comportamiento del registro de resultados.
  - El zoom de Tachar se queda al girar sin volver a encajarse en el rango (hallazgo 5, `RedactZoom.kt:32-41`).
- **Concurrencia:** `PickerPlaces` usa un `ConcurrentHashMap`, y el colector va en `appScope` (`Dispatchers.IO`, `AppContainer.kt:76`). Una carrera solo puede hacer que se olvide de más, salvo el caso documentado de un bloqueo y un desbloqueo más rápidos que el colector (`AppContainer.kt:325`).
- **MASVS:**
  - STORAGE-1/2: preferencias privadas, fuera de las copias, con los hallazgos 1 y 2.
  - PLATFORM-1: ningún componente exportado, `intent-filter` ni permiso nuevos.
  - PLATFORM-3: `FLAG_SECURE` intacto.
  - CODE-4: ningún registro con datos.
  - PRIVACY-1: hallazgo 2.
- **Rendimiento:** `PrefsPlaceStore.get` lee las preferencias en el hilo principal al lanzar un selector. Las carga `getSharedPreferences` al crear `AppContainer`, así que en la práctica ya están en memoria. No es un hallazgo.

## Textos para el Abogado Digital (revisión final)

- `docs/privacidad.html:38`, el párrafo de los selectores:
  - si se elige (b) en el hallazgo 2, decir que «Abrir lo recibido», lo cifrado y la firma sí se apuntan, y que la dirección puede llevar el nombre del archivo;
  - con (a), el texto ya es cierto.
  - Con el arreglo del hallazgo 1, «hasta que la bóveda se bloquea o se abre, o hasta que la app se cierra» puede pasar a «hasta que sales de la app, la bóveda se bloquea o se abre».
- Textos que propongo, a la vista (hallazgo 3):
  - `HASH_LAB_NOT_PROTECTED_SHORT`: «Que coincida no prueba quién la publicó: tómala de otra fuente que la descarga.»;
  - `SHARE_NOT_PROTECTED_SHORT`: «Quien reúna las partes necesarias obtiene el secreto: guarda cada una en un sitio distinto.»;
  - `BACKUP_CHECK_NOT_PROVEN_SHORT`: «No dice si es tu copia más reciente.»
- Líneas cortas que hay que afinar (hallazgo 4):
  - `IMPORT_CONFIRM_SHORT`: «… sin ella no se importa nada.»;
  - `PHOTO_SHORT`: «otros datos que guarda el archivo» en lugar de «otros datos ocultos».
- Avisos plegados sin línea corta que el Abogado debe valorar (hallazgo 4): `CLIPBOARD_CLEAR_HINT` (el portapapeles compartido con otros dispositivos), `LOCK_ON_EXIT_HINT` (cuándo no se bloquea al salir), `ENCRYPT_HELP` (sin secreto hacia adelante, metadatos) y `ANSWERS_WARNINGS`. Los textos de «Para fondos importantes usa una cartera física…» (`NoticeScreen`, `AboutScreen`, Herramientas) también quedan plegados; las líneas cortas `NOTICE_NO_AUDIT_SHORT` y `ABOUT_WARNINGS_SHORT` dicen que no hay auditoría independiente.
- El texto nuevo de F-6: `SKIP_LOGIN_TOTP_BROKEN`, «2FA dañado o no válido (el acceso sí entra)».
- Todos los `*_SHORT` nuevos de `tools/ToolTexts.kt` (sección «Texto mínimo») y `HASH_LAB_SHORT`/`HASH_LAB_HMAC_SHORT`, ya en el anexo «Texto mínimo (1.4.2)». `TOOLS_ABOUT_SHORT` dice «Todo se hace en tu móvil»: es cierto para las herramientas, pero no para la copia automática, que va a la carpeta que elijas (y esa carpeta puede estar en la nube). Está bien en su contexto, la tarjeta de Herramientas.
