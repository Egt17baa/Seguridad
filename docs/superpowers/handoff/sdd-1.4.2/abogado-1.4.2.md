# Clavero 1.4.2: revisión final del Abogado Digital

- **Fecha de referencia:** 10-11-2026.
- **Rama:** `ccr-a1a6eabb-765f9g`, en `3323315`, contra `main` en `ba903e7` (`git diff ba903e7..HEAD`).
- **Skills:** `despacho-digital` y `privacidad-proteccion-datos`. `regulacion-digital-ue` y `propiedad-intelectual-ia` no hacen falta: la 1.4.2 no cambia nada de lo revisado en la 1.4.0 y la 1.4.1 (DSA, CRA, accesibilidad, licencias ni marcas). No añade permisos, conexiones, dependencias ni nombres de otras apps a la vista.
- **Hechos que doy por ciertos (los mismos que en la 1.4.1):** el autor es un particular sin ánimo de lucro, Egt17baa. La app es gratuita, no tiene anuncios ni donaciones y no recibe datos de nadie. Se publica en GitHub y en Google Play, fuera de Francia, Rusia y Bielorrusia.
- **Alcance:**
  - el párrafo nuevo de los selectores en `docs/privacidad.html`;
  - los textos cortos de «Texto mínimo (1.4.2)», comparados con lo que queda plegado bajo «Más información»;
  - `SKIP_LOGIN_TOTP_BROKEN`;
  - las líneas nuevas del README;
  - el borrador de las notas, §5 del traspaso.

## 1. Conclusión

**Se puede publicar, con cambios.** Ninguna norma exige cambios en la 1.4.2:
- los lugares de los selectores se guardan solo en el móvil y no salen de él;
- el desarrollador no los recibe, así que no es responsable de ningún tratamiento (arts. 4.7, 13 y 14 RGPD);
- para la Política de datos de usuario de Google Play no hay «recogida», y la sección «Seguridad de los datos» sigue en «No recoge ni comparte datos».

Los cambios obligatorios vienen de dos reglas del proyecto y de la Política de metadatos de Google Play:
- **la regla de veracidad:** lo publicado no afirma nada que el código no haga;
- **la regla de «Texto mínimo»:** «Los avisos que hay que ver no se pliegan» (anexo de textos, «Texto mínimo (1.4.2)»; KDoc de `ui/screens/MoreInfo.kt:28`: «never a warning the user must see»).

Son siete:
- O-1 y O-2: el párrafo de la política y las notas de GitHub sobre los selectores;
- O-3 a O-5: tres textos cortos que pliegan un aviso o prometen más que el texto completo;
- O-6: la fecha de la política;
- O-7: el anexo y el README, para que digan lo mismo que la app.

Todos son cambios de texto o de dónde se enseña un texto que ya existe. No tocan formatos ni el comportamiento.

## 2. Lo que comprobé en el código para la política

| Afirmación del párrafo nuevo (`docs/privacidad.html:38`) | Código | ¿Cierto? |
|---|---|---|
| Se apunta en el almacenamiento privado y nunca el contenido | `android/PrefsPlaceStore.kt:13` (preferencias `picker_places`, `MODE_PRIVATE`); solo la URI del documento (`core/PickerPlaces.kt:76-79`) | Sí |
| No va en ninguna copia de Android | `AndroidManifest.xml:11` (`allowBackup="false"`); `res/xml/backup_rules.xml` y `data_extraction_rules.xml` excluyen `sharedpref` en la copia en la nube y en el paso a otro móvil | Sí |
| «el último archivo **o carpeta**» | Solo se apunta la URI del documento elegido o creado. El único selector de carpetas (la copia automática, `SettingsScreen.kt:168`) tiene `place = null` y se abre en la carpeta ya guardada en los ajustes (`Pickers.kt`, `folderStart`) | **No del todo:** no se apunta ninguna carpeta. Lo que se apunta incluye el **nombre** del archivo (lo dice el KDoc de `PickerPlaces.kt:9-12`) |
| Las opciones de datos privados solo lo recuerdan en memoria | `kept = false`: `IMPORT_OTHER`, `HASH_FILE`, `QR_IMAGE`, `PHOTO_OPEN`, `REDACT_OPEN`, `TEXT_OPEN`, `FILE_OPEN`, `FILE_DECRYPTED_SAVE`, `ENCRYPT_FOR_OPEN`, `SIGN_OPEN`, `RECEIVED_SAVE`, `ATTACHMENT_ADD`, `ATTACHMENT_SAVE` (`PickerPlaces.kt:16-41`) | Sí, salvo lo de la fila siguiente |
| Entre ellas, «el archivo que […] descifras» | En «Cifrar archivos» el archivo de entrada (`FILE_OPEN`) no se apunta. En Contactos → «Abrir lo recibido», el `.sgenc` o `.sgsig` recibido (`RECEIVED_OPEN`, `PkActionsScreen.kt:693`) **sí se apunta** en disco; lo que no se apunta es el archivo descifrado que se guarda (`RECEIVED_SAVE`, `FILE_DECRYPTED_SAVE`) | **No:** el texto dice que no se apunta algo que sí se apunta |
| Se olvidan «hasta que la bóveda se bloquea o se abre» | `AppContainer.kt:323-329`: `forgetSession()` en cada cambio entre bloqueada y abierta | Sí |
| «o hasta que la app se cierra» | Es un mapa en memoria (`PickerPlaces.kt:69`): dura lo que el proceso. Si la bóveda ya estaba bloqueada (las herramientas se usan sin ella), salir de la app con «Atrás» no lo borra mientras Android no cierre el proceso | **Impreciso:** el usuario entiende «cerrar» como salir |
| «Al borrar la bóveda se olvidan todos» | `VaultScreen.kt:411-412`: `forgetAll()` antes de `store.destroy()`, la única llamada a `destroy()` de la app | Sí |
| La carpeta la decide la app que guarda el archivo | `Pickers.kt`, `StartingAt`: `EXTRA_INITIAL_URI`, que el proveedor puede seguir o no | Sí |

## 3. Cambios obligatorios

### O-1. Política de privacidad: el párrafo de los selectores

- **Dónde:** `docs/privacidad.html:38`.
- **Por qué:** por la regla de veracidad y porque la política es el texto que describe el tratamiento (art. 5.1.a) y art. 12.1 RGPD como pauta de transparencia, aunque no haya responsable). Hay tres fallos (§2):
  1. «descifras» incluye «Abrir lo recibido», cuyo archivo recibido sí se apunta;
  2. no se apunta ninguna carpeta, y sí el nombre del archivo, que es lo que le importa al usuario;
  3. «hasta que la app se cierra» se lee como «hasta que salgo de la app».
- **Texto actual:** «Para que cada selector de archivos se abra donde lo usaste la última vez, la app apunta en el móvil, en su almacenamiento privado, el último archivo o carpeta que elegiste en cada opción (nunca su contenido), y no lo incluye en ninguna copia de Android. Las opciones que leen o escriben datos privados (importar de otra app, el archivo que cifras, firmas, descifras o del que calculas un hash, el texto que abres en «Cifrar texto», las fotos que limpias o tachas, la imagen de un QR y los adjuntos de la bóveda) no lo apuntan: solo lo recuerdan en la memoria de la app, hasta que la bóveda se bloquea o se abre, o hasta que la app se cierra. Al borrar la bóveda se olvidan todos. El selector es el de Android: la carpeta en la que se abre la decide la app que guarda el archivo.»
- **Texto propuesto:**

  > Para que cada selector de archivos se abra donde lo usaste la última vez, la app apunta en el móvil, en su almacenamiento privado, la dirección del último archivo que elegiste o guardaste en cada opción (con su nombre, nunca su contenido), y no la incluye en ninguna copia de Android. Las opciones que leen o escriben datos privados no la apuntan: importar de otra app, el archivo que eliges en «Cifrar archivos», el que cifras para un contacto o firmas, el archivo descifrado que guardas, el archivo del que calculas un hash, el texto que abres en «Cifrar texto», las fotos que limpias o tachas, la imagen de un QR y los adjuntos de la bóveda. Esas opciones solo la recuerdan en la memoria de la app, hasta que la bóveda se bloquea o se abre, o hasta que Android cierra la app. Al borrar la bóveda se olvidan todas. El selector es el de Android: la carpeta en la que se abre la decide la app que guarda el archivo.

- **Nota técnica para `experto-seguridad-cripto` y el Ingeniero Móvil Senior:** el texto propuesto describe el código tal como está. Si prefieren que «Abrir lo recibido» tampoco se apunte en disco (`RECEIVED_OPEN(kept = false)`), basta la versión con «descifras». En ese caso hay que cambiar también `PickerPlacesTest` y el KDoc. Es una decisión técnica, no jurídica.

### O-2. Notas de la versión de GitHub: los selectores

- **Dónde:**
  - `docs/superpowers/handoff/2026-11-10-clavero-1.4.2-traspaso.md:74` (notas de GitHub);
  - la misma idea en `:22` (§1, M-2), que no se publica, pero de la que salen las notas.
- **Por qué:** por la regla de veracidad. «El de «Importar de otra app» solo lo recuerda mientras Clavero sigue abierta» tiene dos fallos:
  - da a entender que es el único que no se guarda, cuando son trece (`PickerPlaces.kt`);
  - dice que dura mientras la app sigue abierta, cuando se olvida antes, al bloquear la bóveda (`AppContainer.kt:323-329`).
- **Texto actual:** «- Selectores de archivos: cada opción se abre en la carpeta donde la usaste la última vez. El de «Importar de otra app» solo lo recuerda mientras Clavero sigue abierta, y «Borrar bóveda» olvida todos. Algunas nubes abren siempre en su inicio.»
- **Texto propuesto:**

  > - Selectores de archivos: cada opción se abre en la carpeta donde la usaste la última vez, si la app que guarda el archivo lo permite (algunas nubes abren siempre en su inicio). Las opciones que leen o escriben datos privados, como importar de otra app, los archivos que cifras o firmas, las fotos y los adjuntos, no lo apuntan en el móvil: solo lo recuerdan hasta que la bóveda se bloquea o se abre. «Borrar bóveda» los olvida todos.

- **En §1 del traspaso (`:22`):** «el de «Importar de otra app» solo mientras la app sigue abierta» pasa a «los que leen o escriben datos privados (importar de otra app, cifrar, firmar, hashes, fotos, QR y adjuntos), solo en memoria y hasta que la bóveda se bloquea o se abre».

### O-3. «Antes de empezar»: el aviso de que no hay recuperación

- **Dónde:** `app/src/main/java/app/seguridad/tools/ToolTexts.kt:669` (`NOTICE_NO_RECOVERY_SHORT`); el anexo `docs/superpowers/specs/2026-10-27-seguridad-1.3.0-textos.md`, línea «Antes de empezar».
- **Por qué:** por la regla de «Texto mínimo». «Antes de empezar» es la pantalla de avisos que se acepta con «Entendido». El texto completo, ya plegado, dice «la contraseña maestra de la bóveda, **o la de algo que cifres**». La línea corta solo habla de la maestra.
  - Con la 1.4.2, el aviso de que una contraseña olvidada de «Cifrar texto» o «Cifrar archivos» no se recupera queda plegado en los tres sitios donde estaba a la vista:
    - esta pantalla;
    - la tarjeta de Herramientas (`ToolsScreen.kt:97`);
    - «Acerca de» (`SettingsScreen.kt`, la viñeta «Sin recuperación»; `ABOUT_WARNINGS_SHORT` dice solo «sin recuperación de contraseñas»).
  - Es un aviso que hay que ver antes de cifrar algo con contraseña, porque olvidarla hace perder el dato.
- **Texto actual:** «Si olvidas la contraseña maestra, nadie puede recuperar lo guardado.»
- **Texto propuesto:** «Si olvidas la contraseña maestra, o la de algo que cifres, nadie puede recuperar lo guardado.»

### O-4. Las tarjetas «Lo que no protege» y «Lo que no comprueba», plegadas sin una línea a la vista

- **Por qué:** por la regla de «Texto mínimo», y para que la línea corta no prometa más. Antes de la 1.4.2, estas tarjetas se veían siempre. Ahora quedan enteras dentro de `AwayNote { … }`, bajo «Más información», y la línea corta presenta la herramienta sin su límite.
  - «Tachar en imagen» ya tiene su línea corta a la vista, `REDACT_LIMITS_SHORT`. Estas tres no tienen ninguna.
  - El riesgo es que el usuario confíe de más justo cuando va a actuar con el resultado:
    - instalar un archivo porque «la huella coincide»;
    - fiarse de una copia porque «se abre»;
    - guardar juntas las partes de un secreto.

| Pantalla | Texto corto actual (a la vista) | Línea nueva propuesta, a la vista, debajo de la corta (la tarjeta completa sigue plegada) |
|---|---|---|
| Laboratorio de hashes (`HashLabScreen.kt:392`; `HashLabTexts.kt:12` y `:17`) | «Calcula la huella de un texto o un archivo y compárala con la publicada.» | `HASH_LAB_NOT_PROTECTED_SHORT` = «Si la huella viene de la misma web que el archivo, quien cambie uno puede cambiar la otra.» |
| Comprobar una copia (`BackupCheckScreen.kt:244`; `BackupCheckTexts.kt:68`) | «Comprueba que una copia .sgbak se abre con su contraseña y que sus adjuntos coinciden, sin importarla.» | `BACKUP_CHECK_NOT_PROVEN_SHORT` = «No dice si es tu copia más reciente ni si tiene lo que cambiaste después.» |
| Repartir un secreto (`SecretShareScreen.kt:297`; `SecretShareTexts.kt:13`) | «Divide un secreto en partes (SLIP-0039): con las necesarias se recompone; con menos, no.» | `SHARE_NOT_PROTECTED_SHORT` = «Quien reúna las partes necesarias obtiene el secreto: guarda cada una en un sitio distinto.» |

- **Repartir un secreto:** el resto de su tarjeta ya está a la vista en otros sitios:
  - la contraseña equivocada, en `SHARE_PASSPHRASE_NOTE` (`SecretShareScreen.kt:424`);
  - la cartera, en `SHARE_WARNING_SHORT`.
- **Los tres textos nuevos:** no llevan palabras prohibidas y no prometen nada.

### O-5. Limpiar foto: «otros datos ocultos»

- **Dónde:** `app/src/main/java/app/seguridad/tools/ToolTexts.kt:646` (`PHOTO_SHORT`) y el anexo («Limpiar foto»).
- **Por qué:** la línea corta no puede prometer más que el texto completo. «Otros datos ocultos» se lee como «todo lo oculto». El texto completo (`PHOTO_EXPLANATION`, `ToolTexts.kt:39-45`, ahora plegado) aclara dos cosas:
  - se quitan solo «los datos que la foto guarda dentro del archivo»;
  - «tampoco quita las marcas invisibles que pueda llevar la propia imagen ni el rastro que deja el sensor».

  Las marcas invisibles también son «datos ocultos».
- **Texto actual:** «Quita de la foto la ubicación, el modelo del móvil y otros datos ocultos. No cambia lo que se ve: una cara o una matrícula pueden delatarte igual.»
- **Texto propuesto:** «Quita los datos que la foto guarda dentro del archivo, como la ubicación o el modelo del móvil. No cambia lo que se ve: una cara o una matrícula pueden delatarte igual.»

### O-6. La fecha de la política

- **Dónde:** `docs/privacidad.html:22` («Última actualización: 10 de noviembre de 2026.»).
- **Por qué:** el texto cambia, y la propia política promete que «se publicará aquí con su nueva fecha» (`:69`). La fecha tiene que ser el día en que el texto nuevo llega a `main`, justo antes de fusionar, como ya prevé el traspaso, §2. Si se fusiona el 10-11-2026, la cadena se queda igual, y es correcta. Si se fusiona otro día, se cambia a ese día. Así se cumple el art. 12.1 RGPD como pauta y la Política de datos de usuario de Google Play, que pide una política accesible y actual.

### O-7. El anexo y el README dicen lo mismo que la app

- **Por qué:** por la regla de veracidad. Son los documentos publicados que citan estos textos.
- **El anexo** (`docs/superpowers/specs/2026-10-27-seguridad-1.3.0-textos.md`, «Texto mínimo (1.4.2)»):
  - recoge los textos de O-3 a O-5 como queden;
  - añade a «Lo que no se pliega nunca» las tres líneas de O-4.
- **El README** (`README.md:375`): la lista de las opciones que no se apuntan cambia como en O-1:
  - **Texto actual:** «Las opciones que leen o escriben datos privados (importar de otra app, el archivo que cifras, firmas, descifras o del que calculas un hash, el texto que abres en «Cifrar texto», las fotos que limpias o tachas, la imagen de un QR y los adjuntos) lo recuerdan solo hasta que la bóveda se bloquea o se abre, o hasta cerrar la app.»
  - **Texto propuesto:** «Las opciones que leen o escriben datos privados (importar de otra app, el archivo que eliges en «Cifrar archivos», el que cifras para un contacto o firmas, el archivo descifrado que guardas, el del que calculas un hash, el texto que abres en «Cifrar texto», las fotos que limpias o tachas, la imagen de un QR y los adjuntos) lo recuerdan solo hasta que la bóveda se bloquea o se abre, o hasta que Android cierra la app; «Abrir lo recibido» sí recuerda el archivo recibido.»

## 4. Cambios recomendados

Son detalles menores. Según las decisiones de la 1.3.x que siguen vigentes («Detalles menores: se arreglan todos, no se aplazan»), conviene aplicarlos también. Ninguno cambia el comportamiento.

| N.º | Dónde | Texto actual | Texto propuesto | Motivo |
|---|---|---|---|---|
| R-1 | Traspaso `:81`, «Novedades» de Google Play | «Cada selector de archivos se abre donde lo usaste la última vez.» | «Cada selector de archivos se abre donde lo usaste la última vez, si la app de archivos lo permite.» | Política de metadatos de Google Play (no inducir a error): el proveedor puede no seguir la indicación (`Pickers.kt`, `StartingAt`). El texto completo queda en unos 320 caracteres, por debajo de 500 |
| R-2 | `imports/ImportTexts.kt:25` (`SKIP_LOGIN_TOTP_BROKEN`) | «2FA dañado o no válido (el acceso sí entra)» | «2FA dañado o no válido (el acceso se lee sin él)» | La fila sale también en «No han entrado», después de importar. Si el acceso era un duplicado y el usuario eligió «Saltar», o si la importación no llegó a guardarse, «sí entra» ya no es cierto. «Se lee sin él» es cierto en la vista previa y después. Si se cambia, hay que actualizar la lista de fallos, F-6 |
| R-3 | `ToolTexts.kt:663` (`IMPORT_REMOVE_SHORT`) | «El archivo importado sigue en tu móvil, sin cifrar si lo exportaste sin contraseña.» | «El archivo importado sigue en tu móvil, sin cifrar si lo exportaste sin contraseña. Clavero puede intentar borrarlo, pero no las copias de la nube, de la papelera ni de otras apps.» | La línea va justo encima del botón «Quitar el archivo exportado», y el límite de ese borrado solo está plegado (`IMPORT_REMOVE_EXPLAIN`). Lo mitiga que `IMPORT_REMOVED` lo diga después, pero conviene que se vea antes de pulsar |
| R-4 | `ToolTexts.kt:679` (`IMPORT_REPLACE_SHORT`) | ««Reemplazar» cambia también el 2FA de la bóveda por el del archivo: el anterior solo queda en la copia de antes de importar.» | ««Reemplazar» cambia también el 2FA de la bóveda por el del archivo, si este trae uno: el anterior solo queda en la copia de antes de importar.» | El texto completo dice «que no estén vacíos» (`IMPORT_REPLACE_NOTE`): sin 2FA en el archivo, el de la bóveda no cambia |
| R-5 | `ToolTexts.kt:655` (`BREACH_SHORT`) | «Tu contraseña no sale del móvil: solo se envían 5 caracteres de su huella.» | «Tu contraseña no sale del móvil: solo se envían a Have I Been Pwned 5 caracteres de su huella.» | Antes de pulsar, el usuario debe saber a quién se envía. El texto plegado y «Acerca de» lo dicen; la línea a la vista no. Es transparencia, con el art. 13.1.e) RGPD como pauta. Have I Been Pwned es el servicio al que se conecta, no una app que se importe, y ya se nombra en la pantalla |
| R-6 | `ToolTexts.kt:670` (`NOTICE_NO_AUDIT_SHORT`) | «La app no ha pasado una auditoría de seguridad independiente.» | «La app no ha pasado una auditoría de seguridad independiente. Para fondos importantes, usa una cartera física.» | El consejo es lo que el usuario puede hacer con el aviso, y la app guarda frases semilla. «Cartera» es la palabra de R3a |
| R-7 | `ToolTexts.kt:639` (`RECOVERY_CODES_SHORT`) | «Diez códigos al azar para guardar en la bóveda o impresos.» | «Diez códigos al azar, para los servicios que te dejan elegir los tuyos. Guárdalos en la bóveda o impresos.» | El texto plegado dice «si un servicio te da los suyos, guarda esos». Sin eso, alguien puede creer que estos sustituyen a los del servicio |
| R-8 | `ToolTexts.kt:648` (`ENCRYPT_HELP_SHORT`) | «Solo podrá leerlo quien tenga la identidad del contacto.» | «Solo podrá leerlo quien tenga la identidad del contacto. Quien vea el mensaje sabe que existe y su tamaño.» | Como «Lo que no protege la identidad (dicho siempre, sin exagerar)» (`ContactsView.kt:33`): el límite de los metadatos queda solo plegado (`ENCRYPT_HELP`) |
| R-9 | `SettingsScreen.kt:291`, copia automática | (Plegado entero, sin línea a la vista) «Cada copia va cifrada con la contraseña maestra que tenía la bóveda al hacerla […] Solo se borran las copias automáticas más antiguas […]; nunca otros archivos de la carpeta.» | Una línea a la vista: «Borra solo sus copias automáticas más antiguas, nunca otros archivos de la carpeta.» | Quien elige una carpeta debe saber, antes de activarla, que la app borra archivos en ella y cuáles. La política lo dice; la pantalla, ahora, solo plegado |
| R-10 | `ToolTexts.kt:637` (`TOOLS_AWAY_WIPE_SHORT`) y `:642` (`CODES_UNLOCK_TO_SAVE_SHORT`) | «Se borra al volver atrás, al cerrar la app, al bloquear la bóveda o si estás fuera más que el bloqueo.» / «Desbloquea la bóveda para guardarlo. Si vuelves atrás o la bloqueas, se pierde.» | «Lo escrito y el resultado se borran al volver atrás, al cerrar la app, al bloquear la bóveda o si estás fuera más tiempo que el bloqueo.» / «Desbloquea la bóveda para guardarlo. Se pierde al volver atrás, al cerrar la app, al bloquear la bóveda o si estás fuera más tiempo que el bloqueo.» | Claridad. La primera no dice qué se borra, y «más que el bloqueo» es oscuro. La segunda omite casos del texto completo (`CODES_UNLOCK_TO_SAVE`), y así no contradice a la regla de «PIN y códigos» de `CLAUDE.md` |
| R-11 | `ToolTexts.kt:633` (`TOOLS_ABOUT_SHORT`) | «Todo se hace en tu móvil. Solo «¿Filtrada?» y «Comprobar filtraciones» usan internet, y envían 5 caracteres de la huella.» | «Todo se hace en tu móvil salvo «¿Filtrada?» y «Comprobar filtraciones», que usan internet cuando las pulsas y envían 5 caracteres de la huella de cada contraseña.» | Quita la contradicción aparente de «Todo» y luego «Solo», y recupera «cuando las pulsas» del texto completo |

## 5. Lo revisado sin cambios

- **Los demás textos cortos.** Están en el anexo y en `ToolTexts.kt:621-681`. No contradicen su texto plegado ni prometen más:
  - `ENIGMA_SHORT` (igual a la primera frase de `ENIGMA_INTRO`);
  - `QR_SHORT`;
  - `REDACT_SHORT` y `REDACT_LIMITS_SHORT`;
  - `SHARE_WARNING_SHORT`: más estrecho que el completo («o con una herramienta SLIP-0039»), pero no falso;
  - `GENERATOR_EFF_SHORT`: la atribución CC BY 3.0 US completa sigue plegada y en Licencias, lo que basta para la cláusula 4.b) de la licencia («reasonable to the medium»);
  - `IMPORT_SHORT`, `IMPORT_NOT_PROTECTED_SHORT` (con «suele», R33), `IMPORT_WIPE_SHORT` e `IMPORT_CONFIRM_SHORT`;
  - `VAULT_CREATE_SHORT`: la casilla «Entiendo que si olvido…» sigue a la vista (`VaultScreen.kt:299`);
  - `IDENTITY_SHORT`: `IDENTITY_RISK` sigue a la vista (`IdentityScreen.kt:124` y `:153`);
  - `AUTO_BACKUP_SHORT`;
  - `KDF_HIGH_SHORT` y `KDF_HIGH_UNAVAILABLE`, ya a la vista tras la revisión de código;
  - `ABOUT_WARNINGS_SHORT`;
  - `ANSWERS_SHORT`: la regla de los datos de identidad, que tienen que ser verdaderos, sigue a la vista en lo esencial («nunca en tus datos de identidad»), y es lo jurídicamente relevante;
  - `HASH_LAB_HMAC_SHORT`.
- **Lo que no se pliega** (comprobado en el código):
  - los errores y resultados;
  - los diálogos de borrar;
  - el aviso de un contacto sin verificar (`ContactDetailScreen.kt`, «never folded»);
  - el aviso de Laboratorio clásico;
  - la línea de marcas de la pantalla de importar (`ImportScreen.kt:574`), que sigue a la vista aunque los nombres de los formatos (`IMPORT_FORMATS`) queden plegados. Es correcto: los nombres de otras apps siguen solo en `ImportTexts.kt`.
- **Accesibilidad:** «Más información» y «Menos información» dicen su estado a TalkBack («Desplegado» y «Plegado», `MoreInfo.kt`). La Ley 11/2023 y la Directiva (UE) 2019/882 no se aplican a esta app, porque no es un servicio de los que enumera. Es una buena práctica.
- **Palabras prohibidas:** ninguna en los textos nuevos de la app, del README ni de las notas (comprobado con las reglas de `ForbiddenWords.kt`: «seguro/a», «limpio/a», «garantiza» sin «no», «anónim», «imposible», «100 %», «de verdad», «validez legal», «sospechos»). Los textos propuestos aquí tampoco las llevan.
- **Nombres de otras apps:** las notas de GitHub y las «Novedades» no nombran ninguna app. Las líneas nuevas del README (`:370-377`) quedan encima de su línea de marcas (`:379`) y solo dicen «una carpeta de la nube» y «Descargas».
- **Notas de la versión, el resto:** son ciertas según el código y la lista de fallos:
  - «No cambia ningún formato de Clavero ni añade permisos ni conexiones» (traspaso §1; `AndroidManifest.xml` solo añade `configChanges`);
  - la línea de la huella;
  - la del 2FA (F-6);
  - la del texto mínimo.

  **Condición:** la línea de la huella en horizontal («ya enseña el diálogo y la activa», y en Play «ya funciona») solo debe publicarse si la prueba del dueño en el móvil (README, F-5) sale bien. El traspaso ya pone la prueba antes de la etiqueta y de las notas. Si no saliera, esa línea se quita.
- **Ficha de Google Play** (`docs/ficha-google-play.md`): la 1.4.2 no la cambia, y sigue siendo cierta.
- **Datos personales:** el archivo de prueba `.maestro/datos/csv-carpeta-2fa.csv` usa dominios `example` (RFC 2606) y datos inventados. Las líneas nuevas del README, de `CLAUDE.md` y del traspaso no nombran el móvil, el ordenador, las apps ni los servicios del dueño: solo «una carpeta de la nube». Cumplen la regla «Privacidad del dueño».

## 6. Cómo se aplican

Según `CLAUDE.md`, punto 4, `experto-seguridad-cripto` y el Ingeniero Móvil Senior comprueban antes que ningún cambio rompa un formato, cambie el comportamiento o afirme algo falso del código.
- O-3 a O-5 y R-2 a R-11 cambian constantes de texto o añaden una línea a la vista. Si algún test fija esos literales (`ToolTextsTest`, `ImportTextsTest`, `SourceTextsTest`), se actualiza con ellos.
- Las tres constantes nuevas de O-4 van en `ALL_TOOL_TEXTS`, o en la lista equivalente de su archivo, para que pasen la prueba de palabras prohibidas.
- O-1, O-2, O-6 y O-7 son documentos.
- La nota técnica de O-1 (`RECEIVED_OPEN`) la deciden ellos.

*Borrador profesional para la revisión del responsable. No sustituye el asesoramiento de un abogado colegiado en una jurisdicción concreta.*

## 7. Repaso tras la auditoría (`git diff 3323315..83057e1`, 10-11-2026)

### 7.1. Lo comprobado

**Política de privacidad, el párrafo de los selectores (`docs/privacidad.html:38`): dice la verdad.**
- En disco solo se apuntan siete lugares. Son los que tienen `kept` en `core/PickerPlaces.kt:17-41`: `EXPORT_COPY`, `IMPORT_COPY`, `IMPORT_COPY_BEFORE`, `BACKUP_CHECK`, `PHOTO_SAVE`, `REDACT_SAVE` y `TEXT_SAVE`. El párrafo los nombra todos y ninguno más.
- Los demás solo se recuerdan con la bóveda abierta:
  - `initial` y `remember` miran `sessionOpen` (`PickerPlaces.kt:77-86`);
  - `AppContainer.kt:130` lo une a `VaultState.Unlocked`.
- Se olvidan en cada bloqueo y en cada desbloqueo (`AppContainer.kt:327`), y con el proceso.
- «Borrar bóveda» los olvida todos (`forgetAll`).
- Mi O-1 queda resuelto. El detalle que queda es R-12.

**`IMPORT_CONFIRM_SHORT`: correcto.** Repite las dos ideas del texto completo (`IMPORT_CONFIRM_NOTE`): la copia cifrada antes de guardar y que sin ella no se importa nada. Lo demás del texto completo (dónde se guarda la copia) lo dice `importCopyWhereText` al terminar.

**`ANSWERS_WARNINGS`, ahora siempre a la vista: correcto.** Es un aviso («si pierdes la bóveda y sus copias, pierdes estas respuestas») y no lleva palabras prohibidas.

**README (`:375`): correcto, salvo «las fotos» (R-12).** Coincide con el código, incluido «también después de cerrar la app» para los lugares que se apuntan.

**Notas de Google Play: correctas.** R-1 está aplicado.

### 7.2. Cambios obligatorios

**O-8. Traspaso §5, notas de GitHub: la condición está dentro del bloque que se pega.**
- **Dónde:** `docs/superpowers/handoff/2026-11-10-clavero-1.4.2-traspaso.md`, la última línea antes del cierre del bloque de GitHub.
- **Texto actual:** «La línea de la huella en horizontal solo se publica si la prueba F-5 en el móvil sale bien (condición del Abogado).»
- **Cambio:** la misma frase, fuera del bloque, después del cierre.
- **Por qué:** es una instrucción interna y se publicaría tal cual en la *Release*. La regla de veracidad pide que lo publicado no diga nada que no sea para el lector.

**O-9. Traspaso §5, notas de GitHub, la línea «Menos texto»: promete más de lo que hay.**
- **Texto actual:** «Los avisos, los errores y las confirmaciones se ven siempre, y cada herramienta dice a la vista lo que no protege.»
- **Texto propuesto:** «Los avisos, los errores y las confirmaciones se ven siempre, y las herramientas que explican lo que no protegen lo resumen a la vista.»
- **Por qué:** no todas las herramientas tienen esa explicación. El Generador, el Inspector de QR, «Cifrar texto» y «Cifrar archivos» no la tienen.
  - Las que la tienen la enseñan a la vista: Laboratorio de hashes, Repartir un secreto, Comprobar una copia, Tachar, Limpiar foto, Laboratorio clásico e Identidad.
  - «Cada herramienta dice lo que no protege» afirma algo que el código no hace (regla de veracidad).

### 7.3. Cambios recomendados

| N.º | Dónde | Texto actual | Texto propuesto | Motivo |
|---|---|---|---|---|
| R-12 | `docs/privacidad.html:38` y `README.md:375`, la lista entre paréntesis | Política: «(importar de otra app, cifrar, firmar, descifrar o abrir lo recibido, calcular un hash, las fotos, la imagen de un QR, los adjuntos)». README: lo mismo, terminado en «y los adjuntos» | Política: «(importar de otra app, cifrar, firmar, descifrar o abrir lo recibido, calcular un hash, el texto que abres en «Cifrar texto», las fotos que abres para limpiarlas o tacharlas, la imagen de un QR, los adjuntos)». README: lo mismo, terminado en «y los adjuntos» | «Las fotos» se puede confundir con las copias de Limpiar foto y Tachar, que sí se apuntan (`PHOTO_SAVE` y `REDACT_SAVE`). El texto que se abre en «Cifrar texto» (`TEXT_OPEN`) solo cabe en «cifrar», mientras que el texto cifrado que se guarda sí se apunta |
| R-13 | `tools/ToolTexts.kt`, `LOCK_ON_EXIT_SHORT` | «Al salir de la app se bloquea, salvo mientras eliges un archivo o escaneas un QR para ella.» | «Al salir de la app se bloquea, salvo mientras eliges un archivo o escaneas un QR para ella. Con otra app en pantalla dividida o en ventana flotante, cuenta el tiempo elegido.» | El texto completo, plegado (`LOCK_ON_EXIT_HINT`), tiene una segunda excepción que importa: con otra app en pantalla dividida o flotante, la bóveda sigue abierta hasta el tiempo elegido. Sin ella, la línea corta promete un bloqueo que en ese caso no llega |
| R-14 | `tools/ToolTexts.kt`, `CLIPBOARD_CLEAR_SHORT` | «Desde Android 10, si no estás usando la app, vacía el portapapeles aunque hayas copiado otra cosa después.» | «Desde Android 10, si no estás usando la app, vacía el portapapeles aunque hayas copiado otra cosa después. Solo lo vacía en este móvil, no en los dispositivos con los que lo compartas.» | El límite de protección («allí puede quedarse») solo está plegado (`CLIPBOARD_CLEAR_HINT`). Es el mismo criterio que O-4. Es recomendado y no obligatorio porque el momento de actuar es copiar, no esta pantalla de Ajustes |

Ninguno de los textos propuestos lleva palabras prohibidas (`ForbiddenWords.kt`) ni nombres de otras apps. Si se aplican, el anexo «Texto mínimo (1.4.2)» recoge R-13 y R-14.
