# Dictamen del Abogado Digital: textos de la versión 1.3.0, tareas 1 a 11

- **Fecha de referencia:** 29-10-2026.
- **Objeto:** todos los textos de interfaz nuevos o cambiados entre `3e5afe5` y `HEAD` (`3f8955d`, rama `ccr-a1a6eabb-765f9g`): el anexo `docs/superpowers/specs/2026-10-27-seguridad-1.3.0-textos.md`, los textos fuera del anexo de las tareas 4, 5, 7, 8, 9, 10 y 11, y la atribución de BIP-39 (README, `licenses/bip39.txt`, `NOTICE`, `LicensesScreen.kt`).
- **Líneas:** salvo que se diga otra cosa, son las de `HEAD`. Durante la revisión, otro agente estaba cambiando el árbol de trabajo sin hacer commit (`ToolTexts.kt`, `AutoBackup.kt`, `SettingsScreen.kt`, `VaultBackup.kt` y otros). Por eso los números de línea de `ToolTexts.kt` desde la 437 ya no coinciden con el árbol. Los dos textos nuevos de ese trabajo se revisan aparte, en el apartado 4.
- **Método:** skills `despacho-digital`, `regulacion-digital-ue`, `privacidad-proteccion-datos` y `propiedad-intelectual-ia`, más los criterios del dueño. Cada texto se ha leído en el código y se ha comprobado cuándo y cómo lo muestra la pantalla. No basta con la constante.
- **Carácter del documento:** es un borrador profesional para que el responsable lo revise. No sustituye a la firma de un abogado colegiado.

## 0. Comprobaciones previas

1. **El código usa el anexo tal cual.** Un script propio sacó del anexo las 57 citas «…» de primer nivel, entre «## Limpiar foto» y «## Cambios en los documentos», y las buscó en el código con las concatenaciones de Kotlin unidas. Hay 46 coincidencias literales. Las otras 11 son:
   - 9 textos con parámetros (`photoGpsLine`, `photoDoneText`, `qrAlertsTitle`, `qrPunycode`, `qrUserInfo`, `qrPort`, `qrScheme`, `qrBidi` y `pinText`). Los he comprobado a mano y dan el texto del anexo con su marcador.
   - 2 palabras de regla, no textos de interfaz: «limpio» y «sospechoso».

   El test `ToolTextsTest.everyAnnexTextIsInTheListVerbatim` hace la misma comprobación. **Resultado: no hay ninguna desviación del anexo.**
2. **Lista de palabras en español.** `app/src/main/assets/bip39_spanish.txt` es idéntica, byte a byte, a `bitcoin/bips/bip-0039/spanish.txt` descargado hoy (SHA-256 `46846a5a…ba48c0b`, 2.048 líneas). La cabecera de BIP-39 dice «License: MIT» y su sección «Copyright» dice «This BIP falls under the MIT License.».
3. **Palabras prohibidas.** He buscado en todas las cadenas nuevas `segur|garantiz|limpi|imposib|100 %|cuántic|Signal|anónim|de verdad|validez|sospech`. Sale lo siguiente:
   - «no garantiza que sea seguro» en `QR_NO_ALERTS`. Está permitido.
   - «Chip seguro», «chip seguro» y «Zona segura» en `ToolTexts.kt:413`, `:414` y `:424`. Ver C-5 a C-7.
   - «foto-limpia», el nombre del archivo. Ver C-4.
   - El verbo «limpiar» en «Limpiar foto» y en `PHOTO_DAMAGED`. Ver el riesgo residual R-1.

   El test `noForbiddenWords` no detecta «segura» ni «limpia», y deja pasar «chip seguro» como excepción (`ToolTextsTest.kt:65` y `:73`).

## 1. Resumen

**Cifras:** 210 textos revisados en `HEAD`, más 2 en curso sin commit (apartado 4). 181 están bien y 29 hay que cambiarlos: 9 de forma obligatoria y 20 recomendados.

### 1.1 Cambios obligatorios y recomendados

| # | Texto (const, archivo:línea) | Veredicto | Texto propuesto (exacto) |
|---|---|---|---|
| C-1 | `PASSWORD_CHANGE_RENEW_HINT`, `tools/ToolTexts.kt:433` | **Cambiar obligatorio** | «Si temes que alguien tenga la contraseña anterior y una copia, con las dos puede ver todo lo que guardabas al hacerla: cambia las contraseñas y los códigos 2FA que más te importen, y renueva tu identidad.» |
| C-2 | `RENEW_IDENTITY_WARNING`, `tools/ToolTexts.kt:440` | **Cambiar obligatorio** | «Tus contactos tendrán que añadir tu tarjeta nueva, y no podrás abrir lo que te enviaron a la anterior. Avísales por otro medio: mientras usen la anterior, quien tenga tu identidad antigua podrá firmar como tú y leer lo que te envíen, si llega a ver esos mensajes.» |
| C-3 | `PHOTO_HEIF_NOTICE`, `tools/ToolTexts.kt:101` (anexo) | **Cambiar obligatorio** | «Las fotos HEIC, HEIF y AVIF se guardan como JPEG: la copia puede perder algo de calidad y, si la foto pasa de unos 24 megapíxeles, se reduce a ese tamaño.» |
| C-4a | `PHOTO_FILE_NAME_NOTE`, `tools/ToolTexts.kt:104` (anexo) | **Cambiar obligatorio** | «El nombre del archivo también puede decir algo de ti (por ejemplo, la fecha en «IMG_20261027_…»). Por eso la copia se llama «copia-de-foto».» |
| C-4b | Nombre del archivo «foto-limpia.{jpg,png,webp}», `ui/screens/PhotoCleanScreen.kt:230` y `:247`, y `tools/share/ShareTemp.kt:13` y `:33` | **Cambiar obligatorio** | «copia-de-foto.jpg», «copia-de-foto.png» y «copia-de-foto.webp». En `SHARE_NAMES` hay que mantener también el nombre antiguo, solo para borrar restos. |
| C-5 | `KEY_LOCATION_STRONGBOX`, `tools/ToolTexts.kt:413` (anexo) | **Cambiar obligatorio** | «Chip de seguridad (StrongBox)» |
| C-6 | `KEY_LOCATION_TEE`, `tools/ToolTexts.kt:414` (anexo) | **Cambiar obligatorio** | «Zona aislada del procesador (TEE)» |
| C-7 | `STRONGBOX_AVAILABLE_NOTICE`, `tools/ToolTexts.kt:424` (anexo R2) | **Cambiar obligatorio** | «Este móvil tiene chip de seguridad (StrongBox). Para usarlo, desactiva la huella y vuelve a activarla.» |
| C-8 | `CODES_UNLOCK_TO_SAVE`, `tools/ToolTexts.kt:378` | **Cambiar obligatorio** (o corregir el código; ver el fundamento) | «Desbloquea la bóveda para guardarlo. Si sales de esta pantalla o giras el móvil, lo que ves aquí se pierde.» |
| C-9 | `PHOTO_EXPLANATION`, `tools/ToolTexts.kt:24` (anexo) | Cambiar | «Quita los datos que la foto guarda dentro del archivo: la ubicación, el modelo del móvil, la fecha, la miniatura y otros. No cambia lo que se ve: una cara, una matrícula, tu portal o un reflejo pueden delatarte igual; para eso, usa «Tachar en imagen». Tampoco quita las marcas invisibles que pueda llevar la propia imagen ni el rastro que deja el sensor de cada cámara. Muchas apps de mensajería quitan parte de estos datos al enviar una foto, pero no siempre todos, y no cuando la envías como documento o archivo. La foto se analiza en tu móvil y la app no la envía a ningún sitio: solo sale si tú compartes la copia.» |
| C-10 | `PHOTO_TOOL_SUBTITLE`, `tools/ToolTexts.kt:21` | Cambiar | «Quita la ubicación y otros datos ocultos del archivo» |
| C-11 | `PHOTO_DEVICE`, `tools/ToolTexts.kt:43` (anexo) | Cambiar | «Móvil o cámara: marca y modelo, y a veces número de serie o nombre del propietario» |
| C-12 | `PHOTO_REMOVED_NAMES[DEVICE]`, `tools/ToolTexts.kt:74` | Cambiar | «datos del móvil o la cámara» |
| C-13 | `PHOTO_AUTHOR_RIGHTS`, `tools/ToolTexts.kt:52` (anexo) | Cambiar | «Datos de autor y de derechos: la copia no los lleva. Si la foto no es tuya, no uses la copia para ocultar de quién es» |
| C-14 | `PHOTO_SAVE_FAILED`, `tools/ToolTexts.kt:114` (también lo usa Tachar) | Cambiar | «No se ha podido guardar la copia. Si ha quedado un archivo a medias en la carpeta, bórralo.» |
| C-15 | `QR_EXPLANATION`, `tools/ToolTexts.kt:210` (anexo) | Cambiar | «Enseña lo que hay en un código QR sin abrir nada: ni enlaces, ni redes Wi-Fi, ni pagos. Puedes copiarlo y, si es una tarjeta o un mensaje de Seguridad o un código 2FA, pasarlo a su pantalla. La imagen se analiza en tu móvil y no se guarda.» |
| C-16 | `QR_HIDDEN_ELSEWHERE`, `tools/ToolTexts.kt:272` | Cambiar | «Hay caracteres invisibles o que cambian el orden del texto escondidos en el enlace, aunque aquí no se vean (por ejemplo, codificados con «%», junto a un emoji del nombre o como saltos de línea). Pueden hacer que el texto parezca otro.» |
| C-17 | `QR_SGK1`, `tools/ToolTexts.kt:260` (anexo) | Cambiar | «Se añadirá como contacto sin verificar. Para verificarlo, compara la huella con esa persona, cara a cara o por llamada.» |
| C-18 | `QR_TEXT_HIDDEN_PASSWORD`, `tools/ToolTexts.kt:281` | Cambiar | «El texto completo lleva la contraseña de la red: toca «Mostrar» para verlo.» |
| C-19 | `QR_TEXT_HIDDEN_OTP`, `tools/ToolTexts.kt:282` | Cambiar | «El texto completo lleva la clave secreta del código 2FA: toca «Mostrar» para verlo.» |
| C-20 | `PASSWORD_CHANGE_OLD_BACKUPS`, `tools/ToolTexts.kt:431` (diseño A6) | Cambiar | «Las copias .sgbak de antes, también las automáticas, se siguen abriendo con la contraseña anterior.» |
| C-21 | `PASSWORD_CHANGE_REPLACE_COPIES`, `tools/ToolTexts.kt:432` (diseño A6) | Cambiar | «Hacer una copia nueva y borrar las automáticas anteriores» |
| C-22 | `AUTO_BACKUP_REPLACED`, `vault/AutoBackup.kt:97` | Cambiar | «Se hizo una copia nueva y se borraron las copias automáticas anteriores de esta carpeta. Las que se hayan copiado a otro sitio (otra carpeta, la nube o su papelera) no se borran.» |
| C-23 | `AUTO_BACKUP_REPLACE_WAITS`, `vault/AutoBackup.kt:98` | Cambiar | «Se hará en cuanto abras la bóveda con la contraseña nueva.» |
| C-24 | `CLOCK_CHANGED_TEXT`, `vault/VaultStore.kt:34` | Cambiar | «La hora del móvil se ha atrasado. Por seguridad, escribe la contraseña maestra.» |
| C-25a | `FORGOT_DIALOG_TEXT`, `ui/screens/VaultScreen.kt:272` | Cambiar | «Lo guardado no se puede descifrar sin la contraseña. Se borrarán la bóveda y también los archivos dañados que la app guardó al intentar repararla. No se puede deshacer. Las copias .sgbak no se borran: si recuerdas la contraseña con la que se hicieron, podrás importarlas en una bóveda nueva.» |
| C-25b | `RESET_WIPES_DAMAGED`, `tools/ToolTexts.kt:444` (anexo A12) | Cambiar | El mismo texto que C-25a, en una sola constante: `FORGOT_DIALOG_TEXT = RESET_WIPES_DAMAGED`. |
| C-26 | Descripción de BIP-39, `ui/screens/LicensesScreen.kt:34` (anexo) | Cambiar | «Las 2.048 palabras en inglés y las 2.048 en español, sin cambios.» |
| C-27 | Primera línea de `app/src/main/assets/licenses/bip39.txt:1` (anexo) | Cambiar | «BIP-39 English and Spanish wordlists (bitcoin/bips, bip-0039/english.txt and bip-0039/spanish.txt). "This BIP falls under the MIT License."» |

### 1.2 Textos correctos (OK)

| Texto (const, archivo:línea) | Veredicto | Texto propuesto (exacto) |
|---|---|---|
| **Limpiar foto (`ToolTexts.kt`):** `PHOTO_TOOL_TITLE` :20 (ver R-1), `PHOTO_PICK` :32, `PHOTO_PICK_OTHER` :33, `PHOTO_FOUND_TITLE` :34, `PHOTO_ANALYSING` :35, `photoGpsLine` :128 (con :38-39), `PHOTO_GPS_NO_COORDS` :42, `PHOTO_DATE_SOFTWARE` :44, `PHOTO_THUMBNAIL` :45, `PHOTO_XMP_COMMENTS` :46, `PHOTO_ICC_KEPT` :47, `PHOTO_MOTION_VIDEO` :48, `PHOTO_C2PA` :49, `PHOTO_OTHER` :55, `PHOTO_DONE_CONVERTED` :88, «`$PHOTO_DONE_CONVERTED $PHOTO_ICC_KEPT.`» :155, `PHOTO_DONE_SHORT` :93, `PHOTO_NOTHING_FOUND` :96, `PHOTO_SAVE_COPY` :109, `SHARE` :110, `PHOTO_TOO_BIG` :113, `PHOTO_SHARE_FAILED` :115, `PHOTO_LOST` :116, `PHOTO_TOO_LARGE` :119, `photoDoneText` :124, los otros 8 nombres cortos de `PHOTO_REMOVED_NAMES` :73-81; y en `tools/photo/PhotoReport.kt`, `PHOTO_DAMAGED` :4 y `PHOTO_UNSUPPORTED` :8 | OK | Sin cambios. |
| **Tachar (`ToolTexts.kt`):** `REDACT_TOOL_TITLE` :163, `REDACT_TOOL_SUBTITLE` :164, `REDACT_EXPLANATION` :167, `REDACT_LIMITS` :173, `REDACT_DOWNSCALED` :179, `redactDownscaledText` :186, `REDACT_PICK` :191, `REDACT_PICK_OTHER` :192, `REDACT_OPENING` :193, `REDACT_RENDERING` :194, `REDACT_UNDO` :195, `REDACT_REDO` :196, `REDACT_TOO_BIG` :197, `REDACT_TOO_LARGE` :198, `REDACT_CHANGED` :200, `REDACT_DAMAGED` :202, `REDACT_COPY_FAILED` :204, `REDACT_LOST` :205; nombre del archivo «imagen-tachada» (`Redaction.kt`) | OK | Sin cambios. |
| **QR (`ToolTexts.kt`):** `QR_NO_ALERTS` :215, `qrAlertsTitle` :219, `QR_MIXED_SCRIPTS` :221, `qrPunycode` :226, `QR_IP_HOST` :229, `QR_HTTP` :231, `qrUserInfo` :233, `qrPort` :236, `QR_SHORTENER` :238, `QR_SUBDOMAINS` :239, `qrScheme` :242, `qrBidi` :245, `QR_WIFI_OPEN` :248, `QR_WIFI_WEP` :249, `QR_SEPA` :250, `QR_CRYPTO` :253, `QR_SMS` :256 (solo sale con números cortos, `QrView.kt:165`), `QR_TOO_BIG` :259, `QR_TOOL_TITLE` :264, `QR_TOOL_SUBTITLE` :265, `QR_SCAN` :277 (ver R-6), `QR_PICK_IMAGE` :278, `QR_READING_IMAGE` :279, `QR_FULL_TEXT` :280, `QR_SHOW` :283, `QR_HIDE` :284, `QR_COPY` :285, `QR_COPY_PASSWORD` :286, `QR_ADD_CONTACT` :287, `QR_OPEN_RECEIVED` :288, `QR_ADD_TOTP` :289, `QR_NOT_FOUND` :290, `QR_IMAGE_TOO_BIG` :292, `QR_IMAGE_TOO_LARGE` :293, `QR_IMAGE_DAMAGED` :295, `QR_NO_CAMERA_PERMISSION` :297, `QR_CAMERA_FAILED` :298, `QR_COPIED_WITH_HIDDEN` :301, los 13 `QR_KIND_*` :304-316, `QR_FIELD_WEB` :319, `qrHostReading` :322, los 25 `QR_FIELD_*`, `QR_SCHEME_NONE`, `QR_CIPHER_NONE`, `QR_CIPHER_UNKNOWN` y `QR_YES` :323-349 | OK | Sin cambios. |
| **PIN, códigos y respuestas (`ToolTexts.kt`):** `pinText` :354, `pinCountText` :360, `CODES_TOOL_TITLE`, `CODES_TOOL_SUBTITLE`, `CODES_PIN_TITLE`, `CODES_PIN_LENGTH`, `CODES_PIN_COUNTING`, `CODES_RECOVERY_TITLE`, `CODES_COPY`, `CODES_COPY_ALL`, `CODES_ANOTHER_PIN`, `CODES_ANOTHER_CODES`, `CODES_SAVE_AS_NOTE`, `CODES_SITE_LABEL`, `CODES_SITE_NEEDED` y `CODES_QUESTION_LABEL` :364-377, `CODES_CANCEL_SAVE` :379, `CODES_SAVED` :380, `CODES_SAVE_FAILED` :381, `recoveryNoteTitle` :383, `answersNoteTitle` :384, `RECOVERY_CODES_TEXT` :386, y los 11 `ANSWERS_*` :392-408 | OK | Sin cambios. |
| **StrongBox y A6:** `KEY_LOCATION_TITLE` :412, `KEY_LOCATION_SOFTWARE` :415, `KEY_LOCATION_UNKNOWN` «No se pudo comprobar» :418, `FINGERPRINT_NOT_RESPONDING` :421, `PASSWORD_CHANGE_COPIES` :427, `PASSWORD_CHANGED_TITLE` :430, `PASSWORD_CHANGE_WORKING` :434, `PASSWORD_CHANGE_CLOSE` :435, `AUTO_BACKUP_OUTCOME_TITLE` :436, `RENEW_IDENTITY` :439, `RENEW_IDENTITY_DONE` :441 | OK | Sin cambios. |
| **README:** línea de BIP-39, `README.md:23` («Modo frase semilla con BIP-39 (las listas de palabras BIP-39 en inglés y en español, MIT).»), y línea «Herramientas», `README.md:30` | OK | Sin cambios. |
| **Licencias:** «Copyright (c) the BIP-39 authors (…) and the wordlist contributors», `licenses/bip39.txt:3`, y «the BIP-39 English and Spanish wordlists (MIT);», `NOTICE:11` | OK | Sin cambios. |

**Notas a algunos textos correctos:**
- **`KEY_LOCATION_UNKNOWN`:** es correcto, porque `KeyPlacement.kt:3-7` lo usa sin clave, cuando `KeyInfo` no se puede leer o cuando el nivel es «unknown secure». Va en pretérito indefinido, como el resto de mensajes de la huella («No se pudo activar la huella»).
- **`PHOTO_C2PA`:** es un consejo y no afirma ninguna obligación legal. Ver el fundamento F-13.
- **`REDACT_EXPLANATION`:** «lo de debajo no se puede recuperar» es exacto. El bloque opaco sustituye los píxeles antes de codificar la imagen (`RedactRender.kt:146-158`).

## 2. Fundamento de cada cambio

**Marco aplicable.** Todas las fechas están pendientes de verificar a fecha de hoy. EUR-Lex no se pudo consultar desde este entorno.

- **Directiva (UE) 2024/2853, sobre responsabilidad por los daños causados por productos defectuosos (DRPD).**
  - El software es producto (art. 4.1).
  - El daño incluye la destrucción o corrupción de datos que no se usan con fines profesionales (art. 6.1.c).
  - Para valorar si un producto es defectuoso cuentan su presentación, las instrucciones y las advertencias (art. 7.2.a).
  - El plazo de transposición termina el 9-12-2026. Se aplica a los productos que se introduzcan en el mercado después de esa fecha.
  - Quedan fuera los programas libres y de código abierto que se desarrollan o suministran fuera de una actividad comercial (art. 2.2 y considerando 14). Seguridad es Apache-2.0, gratuita y sin anuncios, así que probablemente queda fuera mientras no se monetice. Aun así, lo prudente es redactar como si se aplicara.
  - La transposición en España está pendiente de verificar.
- **Directiva 2005/29/CE, sobre prácticas comerciales desleales (arts. 6 y 7, acciones y omisiones engañosas), y Ley 3/1991, de Competencia Desleal (arts. 5 y 7).** Solo se aplican a un «comerciante». Si la publicación no es comercial, valen como estándar de diligencia.
- **Reglamento (UE) 2024/2847 (CRA).** Las obligaciones principales se aplican desde el 11-12-2027, y la información al usuario está en el Anexo II. El software libre sin monetizar queda fuera. Las fechas están pendientes de verificar por el paquete Digital Omnibus.
- **Criterios del dueño:** palabras prohibidas, no prometer, decir lo que cada herramienta no protege y exactitud en la privacidad.

**F-1 (C-1). La recomendación tras cambiar la maestra se queda corta: obligatorio.**
- Hechos:
  - El texto ofrece como único remedio «renueva tu identidad».
  - Una copia `.sgbak` antigua, junto con la contraseña anterior, abre todas las entradas de entonces: contraseñas, secretos 2FA, notas y adjuntos.
  - `VaultCrypto.changeMaster` (`Vault.kt:356-369`) crea una clave de datos nueva. Eso protege lo que se guarde después, no lo que ya estaba en la copia.
- Riesgo: el usuario puede creer razonablemente que renovar la identidad basta. Es una advertencia incompleta (art. 7.2.a DRPD) y una posible omisión engañosa (art. 7 de la Directiva 2005/29/CE).
- La redacción propuesta dice lo que la copia expone y qué hacer, sin prometer nada.

**F-2 (C-2). «Renovar identidad» no revoca la tarjeta anterior: obligatorio.**
- Hechos:
  - No hay mecanismo de revocación.
  - Los contactos que conservan la tarjeta antigua siguen cifrando para esa clave.
  - Esos contactos siguen viendo como «De [nombre] (verificado)» lo que se firme con la identidad antigua.
- Por qué importa: la función se ofrece justo para el caso en que alguien tiene la identidad antigua. Callar que el riesgo continúa hasta que cada contacto cambie la tarjeta es la omisión de algo que no protege (criterio del dueño; art. 7.2.a DRPD).
- «si llega a ver esos mensajes» evita exagerar: el atacante necesita además el mensaje, que circula por el canal.

**F-3 (C-3). Reducción silenciosa de las fotos HEIC: obligatorio.**
- Hechos: `heifToJpeg` (`android/HeifToJpeg.kt:38-41`) reduce a `MAX_REDACT_PIXELS` = 24 MP (`ImageLimits.kt:7`) cualquier HEIC, HEIF o AVIF más grande. Una foto de 50 o de 200 MP pierde entre la mitad y casi nueve de cada diez píxeles.
- Problema: el aviso solo dice «algo de calidad», y en Tachar la misma reducción sí se avisa (`REDACT_DOWNSCALED`). La afirmación es inexacta sobre una característica del producto (art. 6.1.b de la Directiva 2005/29/CE) y daña los datos del usuario si borra el original creyendo que la copia es equivalente (art. 6.1.c DRPD).
- El texto propuesto es cierto en todas las versiones con conversión (Android 9 y posteriores, `HeifToJpeg.kt:27`).

**F-4 (C-4a y C-4b). «foto-limpia»: obligatorio.**
- El dueño prohíbe «limpio», y «limpia» es la misma palabra en femenino.
- Aquí es un adjetivo que califica el resultado y viaja con el archivo hasta quien lo recibe. Es justo la afirmación de un estado («está limpia») que la regla quiere evitar: la copia conserva lo que se ve, las marcas invisibles, el rastro del sensor y el perfil de color.
- «copia-de-foto» es neutro, no dice nada de la persona y mantiene la finalidad del anexo, que el nombre no revele la fecha.
- Cambios en el código:
  - `PhotoCleanScreen.kt:230` y `:247`.
  - `ShareTemp.kt:13` y `:33`. Hay que aceptar también «foto-limpia», solo para borrar restos de pruebas.
  - Actualizar las pruebas.
  - Añadir «limpia», «limpias» y «limpios» a `noForbiddenWords`.
- No toca ningún formato de los que se leen siempre.

**F-5 a F-7 (C-5, C-6 y C-7). «Chip seguro» y «Zona segura»: obligatorio por regla del dueño.**
- El dueño prohíbe «seguro» sin «no garantiza que sea seguro», y «segura» es la misma palabra.
- El anexo permitió «chip seguro» como nombre del hardware, y el test lo excluye (`ToolTextsTest.kt:73`). Pero «seguro» o «segura» al lado de la clave de la huella es una afirmación de cualidad, que el art. 7.2.a DRPD tiene en cuenta al valorar qué seguridad cabe esperar.
- El riesgo jurídico es bajo, porque es un término técnico, y la sustitución no cuesta nada: «Chip de seguridad (StrongBox)», que es el término habitual de StrongBox (elemento de seguridad), y «Zona aislada del procesador (TEE)», que describe el TEE sin calificarlo.
- Cambios en el test:
  - quitar la excepción de «chip seguro»;
  - prohibir como palabras completas `seguro`, `segura`, `seguros` y `seguras`, salvo «no garantiza que sea seguro»;
  - no usar `segur` como raíz, porque saltaría con «Seguridad» y con «Por seguridad».
- Hay que actualizar el anexo y la línea 210 del diseño, que también dice «zona segura».

**F-8 (C-8). «Lo que ves aquí se conserva mientras tanto» es falso al girar el móvil: obligatorio, o corregir el código.**
- Hechos:
  - En `CodesScreen.kt:148-162`, todo lo sorteado y lo que espera a guardarse va en `remember`, no en estado guardado. Está hecho así a propósito, para no persistir secretos.
  - El manifiesto no declara `configChanges` y usa `screenOrientation="fullSensor"` (`AndroidManifest.xml:32`). Al girar el móvil se recrea la actividad y se pierden los códigos y las respuestas.
- Riesgo: si el usuario ya ha puesto esos códigos o respuestas en un servicio, puede perder el acceso a su cuenta. Es un dato perdido y una afirmación falsa sobre el producto (art. 6.1.c DRPD; art. 6 de la Directiva 2005/29/CE).
- Alternativa preferible: guardar el estado en un `ViewModel`, que sobrevive a la rotación sin escribir en el estado guardado. Entonces el texto puede quedar «Desbloquea la bóveda para guardarlo. Lo que ves aquí se conserva mientras no salgas de esta pantalla.»

**F-9 (C-9). Privacidad: «no se envía a ningún sitio».** Es exacto para el análisis, pero la misma pantalla tiene «Compartir», que entrega la copia a otra app (`PhotoCleanScreen.kt:240-262`). La política de privacidad del anexo lo dice así, y la pantalla debe ser coherente (transparencia, art. 5.1.a y art. 12 RGPD, como buena práctica: el desarrollador no recibe los datos). Solo cambia la última frase.

**F-10 (C-10).** Es la primera impresión en «Herramientas». «del archivo» marca el límite, que no se quita lo que se ve, igual que en la ficha de Play aprobada («otros datos ocultos del archivo»).

**F-11 (C-11 y C-12). Exactitud de los metadatos del dispositivo.**
- `Found.DEVICE` se marca con cualquiera de estas etiquetas (`Jpeg.kt:321` y `:326`; `Png.kt:152`): Make, Model, MakerNote, CameraOwnerName, BodySerialNumber, LensMake, LensModel, LensSerialNumber o la clave PNG «Source».
- En una foto de móvil normal solo hay marca y modelo, así que el texto actual dice que hay número de serie cuando casi nunca lo hay.
- En cambio, calla el nombre del propietario (CameraOwnerName), que es un dato personal directo (art. 4.1 RGPD).
- Los textos de metadatos tienen que ser exactos (criterio del dueño), y «Móvil o cámara» cubre las fotos de cámara. El nombre corto de la lista «Copia lista…» se ajusta para que encaje.

**F-12 (C-13). Datos de autor y de derechos.**
- El texto dice «no los quites», pero la herramienta los quita siempre: no hay ninguna opción para conservarlos (`PhotoCleanScreen.kt`). La instrucción no corresponde a lo que el usuario puede hacer.
- Lo que la ley prohíbe es suprimir a sabiendas la información para la gestión de derechos con el fin de facilitar o encubrir una infracción (art. 7 de la Directiva 2001/29/CE; art. 162 TRLPI). Por eso la advertencia útil se refiere al uso de la copia.
- Se evita «limpies».

**F-13 (`PHOTO_C2PA`, OK).** «dilo tú» es un consejo. El art. 50.4 del Reglamento (UE) 2024/1689 (AI Act) obliga a quien despliega ultrafalsificaciones, pero excluye a las personas físicas que actúan en una actividad puramente personal (art. 2.10). El texto no afirma ninguna obligación, así que puede quedar como está. Fechas pendientes de verificar por el paquete Digital Omnibus.

**F-14 (C-14). «No se ha guardado nada» puede ser falso tras un fallo de escritura.**
- `discardDocument` (`SecurityToolsScreen.kt:469-471`) borra el archivo sin garantía (`runCatching`), así que puede quedar un archivo a medias.
- Ese archivo a medias no filtra nada: es parte de la copia, sin metadatos, o ya tachada. Aun así, la afirmación absoluta no es cierta, y el texto propuesto dice qué hacer.
- Los demás «No se ha guardado nada.» de Limpiar foto (`PHOTO_TOO_BIG`, `PHOTO_TOO_LARGE` y `PHOTO_DAMAGED`) salen antes de crear ningún archivo. Son ciertos y siguen el patrón del anexo, así que quedan OK.

**F-15 (C-15). «Solo puedes copiarlo» no es exacto.** La pantalla ofrece también «Añadir contacto», «Abrir lo recibido» y «Añadir a la bóveda» (`QrView.kt:178-190`). Ninguna abre nada fuera de la app, pero el texto tiene que describir lo que hay.

**F-16 (C-16).** El paréntesis actual enumera tres casos como si se dieran todos, cuando solo se da uno. «por ejemplo» y «aunque aquí no se vean» lo hacen exacto y explican por qué no aparece la marca ⟨U+…⟩, que es la razón de ser del texto (`QrView.kt:219`).

**F-17 (C-17). Estilo.** «con la persona, en persona» repite la palabra y suena mal. «cara a cara» mantiene el sentido de la verificación fuera del canal.

**F-18 (C-18 y C-19). Concordancia.** «Lleva la contraseña…: toca «Mostrar» para verlo» no concuerda: «verlo» se refiere al texto completo, que no se nombra. Al poner el sujeto, la frase se entiende sola.

**F-19 (C-20). Exactitud.** Las copias automáticas también son `.sgbak` (`seguridad-auto-…sgbak`) y también se abren con la contraseña anterior. «que hiciste antes» puede entenderse como solo las manuales.

**F-20 (C-21). El orden del botón.** El código hace primero la copia nueva y después borra (`AutoBackup.kt`, informe de la tarea 11), justo para que nunca haya cero copias. El botón anuncia el orden contrario y puede asustar a quien teme quedarse sin copia. Este cambio toca un texto del diseño.

**F-21 (C-22). Lo que no protege el borrado.**
- El borrado solo afecta a la carpeta elegida, a través del almacenamiento de Android.
- Si esa carpeta se sincroniza con un servicio en la nube, o el usuario movió copias a otro sitio, allí siguen. El caso típico es una carpeta de Drive elegida con el selector, que guarda versiones y papelera.
- Es justo el riesgo que motiva el botón: alguien con la contraseña anterior y una copia. Hay que decirlo (criterio del dueño; art. 7 de la Directiva 2005/29/CE).

**F-22 (C-23). Claridad.** «con la contraseña nueva» deja claro que el paso siguiente es abrir con la nueva, que es lo que el usuario tiene que hacer tras el cambio.

**F-23 (C-24). Exactitud de la condición.** La huella solo se rechaza si el reloj está por detrás de la marca más alta, con 5 minutos de margen, o por detrás de la última comprobación de la maestra (`Vault.kt:391-396`). Adelantar el reloj no lo provoca. «se ha atrasado» describe la condición sin culpar al usuario, que puede no haber tocado nada (por ejemplo, si una sincronización automática corrigió la hora).

**F-24 (C-25a y C-25b). El diálogo de «He olvidado la contraseña maestra».**
- **Pérdida de información.**
  - Antes el diálogo decía «Lo guardado no se puede descifrar…».
  - Ahora `FORGOT_DIALOG_TEXT` (`VaultScreen.kt:272` y `:340`) sustituye ese texto por el de A12, y el diseño solo pedía añadir lo de los archivos dañados.
  - La frase se recupera.
- **Omisión relevante.** El diálogo no dice que las copias `.sgbak`, manuales o automáticas, no se borran (`VaultStore.kt:750-766` solo borra la bóveda, los archivos dañados y los adjuntos). Esto importa por dos razones:
  - Quien quiere borrarlo todo tiene que saber que las copias siguen ahí. Es un riesgo para la privacidad.
  - Quien recuerda una contraseña antigua puede recuperar sus datos importando una copia, ya que la pantalla de bóveda nueva ofrece «importar» (`VaultScreen.kt`, `CreateVault`). Callarlo puede llevar a perder datos (art. 6.1.c DRPD).
- **Una sola fuente de verdad.** Hoy el mismo texto está en dos constantes (`ToolTexts.kt:444` y `VaultScreen.kt:272`), y pueden acabar siendo distintas.

**F-25 (C-26).** «Las 2.048 palabras en inglés y en español» se entiende como 2.048 en total. Son 2.048 de cada lista.

**F-26 (C-27).** La línea está en inglés y tiene una «y» española. Es una errata menor heredada del anexo, y conviene corregir también el anexo.

**F-27 (Licencias, OK).**
- La licencia MIT de BIP-39 es permisiva y compatible con Apache-2.0. Se cumple su única condición, incluir el aviso de copyright y el de permiso: el texto MIT completo está en `licenses/bip39.txt`, se ve en la pantalla de licencias y figura en `NOTICE`.
- La lista se distribuye sin cambios, como comprueba el hash. Quitar las tildes para mostrarlas es un uso de la lista, no una modificación del archivo.
- La fórmula «the BIP-39 authors (…) and the wordlist contributors» es correcta: BIP-39 no nombra un titular único de la lista en español, y así se atribuye a todos sin inventar nombres.
- La atribución en español del README («las listas de palabras BIP-39 en inglés y en español, MIT») es fiel a la frase del anexo.

## 3. Riesgos residuales

- **R-1. El verbo «Limpiar» en el nombre de la herramienta y en `PHOTO_DAMAGED` (veredicto OK).**
  - Lo considero aceptable: nombra una acción, no un resultado; es el término habitual («limpiar metadatos»); siempre lo acompaña el subtítulo que lo limita; y el anexo, la ficha de Play y el propio dueño lo aprobaron.
  - Si el dueño entiende que la prohibición de «limpio» también cubre el verbo, la alternativa es «Quitar datos de una foto». Este cambio afecta a la ficha y a `privacidad.html` (tarea 15).
- **R-2. La ubicación que oculta Android.**
  - Si la foto trae otros datos pero no aparece la ubicación, la tarjeta no repite el aviso de `PHOTO_NOTHING_FOUND`, que dice que Android puede ocultarla.
  - El usuario podría pensar que el original no tiene ubicación. Lo mitiga «comparte la copia, no el original» al terminar.
  - Recomiendo mostrar esa frase siempre que no salga la línea de GPS (es un cambio de lógica, no de texto).
- **R-3. HEIC no enseña qué traía el original.** Con la conversión, «Lo que trae la foto» describe el JPEG nuevo (`PhotoCleanScreen.kt:271-293`). Es cierto, pero el usuario no ve qué tenía el original, ubicación incluida. Es aceptable con C-3 y `PHOTO_DONE_CONVERTED`.
- **R-4. El perfil de color conservado.** Puede llevar una descripción o un copyright del fabricante, que es una huella menor del dispositivo. El texto lo nombra y lo justifica. Es aceptable.
- **R-5. El aviso de renovar identidad sale aunque no haya identidad.**
  - `MasterChangedDialog` (`SettingsScreen.kt:328-329`) muestra el aviso y el botón aunque no exista identidad, y el botón lleva a crearla.
  - Recomiendo mostrarlo solo si existe una identidad (es lógica, no texto).
- **R-6. Accesibilidad.**
  - «📷 Escanear QR» hace que TalkBack lea «cámara». Los iconos de las herramientas son emojis.
  - La Directiva (UE) 2019/882 y la Ley 11/2023 no se aplican a esta app, porque no es uno de los servicios regulados (fecha pendiente de verificar). Aun así, recomiendo dar un `contentDescription` o quitar el emoji del texto del botón.
- **R-7. Tono.** Conviven el pretérito perfecto, en las herramientas («No se ha podido…»), y el indefinido, en la bóveda, las copias y la huella («No se pudo…», «Se hizo…»). Dentro de cada familia hay coherencia, así que no exijo cambios. Para la 1.4 recomiendo unificar en pretérito perfecto.
- **R-8. Los tests no cubren todas las palabras prohibidas.** `noForbiddenWords` no incluye «a prueba de cuánticos», «como Signal», «segura», «limpia» ni «limpios». Hay que añadirlas (ver F-4 y F-5).
- **R-9. Documentos de la tarea 15.** `privacidad.html`, la ficha de Play y el nombre de archivo que cite la política tienen que reflejar C-3, C-4, C-9 y C-22. El anexo también tiene que reflejar los cambios a sus textos (C-3, C-4a, C-5, C-6, C-7, C-9, C-11, C-13, C-15, C-17, C-25b, C-26 y C-27).
- **R-10. Vigencia.** No he podido consultar EUR-Lex desde este entorno. Antes de publicar hay que confirmar:
  - el estado de la transposición de la DRPD en España;
  - el calendario del CRA;
  - el calendario del art. 50 del AI Act, a la vista del paquete Digital Omnibus.

## 4. Textos en curso, sin commit, en el árbol de trabajo

No son de `HEAD`. Se revisan para que no vuelvan a esta revisión.

| Texto (const, archivo:línea en el árbol) | Veredicto | Texto propuesto (exacto) |
|---|---|---|
| `AUTO_BACKUP_REPLACE_DROPPED`, `tools/ToolTexts.kt:437` | Cambiar | «Las copias automáticas anteriores que ibas a borrar no se borraron: siguen en su carpeta.» |
| `PASSWORD_CHANGED_FINGERPRINT_FAILED`, `tools/ToolTexts.kt:438` | Cambiar | «Contraseña maestra cambiada. La huella ya no abre la bóveda, pero no se pudo desactivar: abre la bóveda con la contraseña nueva, desactiva la huella y vuelve a activarla.» |

**Fundamento:**
- **«siguen en la carpeta anterior».** Es falso cuando el texto se añade tras «Desactivar» (`SettingsScreen.kt:259` en el árbol), porque la carpeta es la misma, no «la anterior».
- **«No se pudo apagar la huella: … vuelve a activarla».**
  - Si `disable()` falla, la huella sigue marcada como activada. No funciona, porque `changeMaster` crea una clave de datos nueva (`Vault.kt:356-364`), pero el menú sigue ofreciendo «Desactivar huella» (`VaultScreen.kt:146`), no «Activar».
  - Hay que decir los pasos reales.
  - «desactivar» es la palabra que usa la app, no «apagar».

## Verificación de CU-A (commit 3a625e5, 29-10-2026)

**Método:**
- **Literalidad.** Un script extrae cada texto propuesto en §1.1 y §4 y lo busca en todo `app/src/main` y en `licenses/bip39.txt`, con las concatenaciones de Kotlin unidas y `$PHOTO_FILE_BASE` sustituido por su valor. Resultado: 29 de 29 textos literales están presentes. C-4b y C-25b no son textos y los he revisado a mano.
- **Textos antiguos.** He buscado «foto-limpia», «Chip seguro», «Zona segura» y `FORGOT_DIALOG_TEXT` en `app/src/main`: no queda ninguno.
- **Palabras prohibidas.** He hecho el grep pedido sobre `app/src/main` y `README.md`.

### Tabla

| Elemento | Aplicado | archivo:línea | Observación |
|---|---|---|---|
| C-1 `PASSWORD_CHANGE_RENEW_HINT` | Sí | `tools/ToolTexts.kt:438` | Literal. Se muestra en `MasterChangedDialog` (`SettingsScreen.kt`, en el diálogo, «Text(PASSWORD_CHANGE_RENEW_HINT)»). |
| C-2 `RENEW_IDENTITY_WARNING` | Sí | `tools/ToolTexts.kt:451` | Literal. |
| C-3 `PHOTO_HEIF_NOTICE` | Sí | `tools/ToolTexts.kt:103` | Literal. El 24 fijo es correcto: `heifToJpeg` siempre usa `MAX_REDACT_PIXELS`. |
| C-4a `PHOTO_FILE_NAME_NOTE` | Sí | `tools/ToolTexts.kt:108` | Literal, interpolado con `PHOTO_FILE_BASE`. |
| C-4b nombre «copia-de-foto» | Sí | `tools/share/ShareTemp.kt:13` y `:16`; `ui/screens/PhotoCleanScreen.kt:231` y `:248` | Sirve para guardar y para compartir. Ver D-1. |
| C-5 `KEY_LOCATION_STRONGBOX` | Sí | `tools/ToolTexts.kt:418` | Literal. |
| C-6 `KEY_LOCATION_TEE` | Sí | `tools/ToolTexts.kt:419` | Literal. |
| C-7 `STRONGBOX_AVAILABLE_NOTICE` | Sí | `tools/ToolTexts.kt:429` | Literal. |
| C-8 `CODES_UNLOCK_TO_SAVE` | Sí | `tools/ToolTexts.kt:383` | Literal. Ahora es cierto. El riesgo de pérdida de datos sigue hasta que CU-B conserve el estado. Cuando lo haga, el texto tiene que pasar a «Desbloquea la bóveda para guardarlo. Lo que ves aquí se conserva mientras no salgas de esta pantalla.» |
| C-9 `PHOTO_EXPLANATION` | Sí | `tools/ToolTexts.kt:26` | Literal. |
| C-10 `PHOTO_TOOL_SUBTITLE` | Sí | `tools/ToolTexts.kt:23` | Literal. |
| C-11 `PHOTO_DEVICE` | Sí | `tools/ToolTexts.kt:45` | Literal. |
| C-12 nombre corto DEVICE | Sí | `tools/ToolTexts.kt:76` | Literal. |
| C-13 `PHOTO_AUTHOR_RIGHTS` | Sí | `tools/ToolTexts.kt:54` | Literal. |
| C-14 `PHOTO_SAVE_FAILED` | Sí | `tools/ToolTexts.kt:118` | Literal. Se usa también en Tachar. |
| C-15 `QR_EXPLANATION` | Sí | `tools/ToolTexts.kt:214` | Literal. |
| C-16 `QR_HIDDEN_ELSEWHERE` | Sí | `tools/ToolTexts.kt:277` | Literal. |
| C-17 `QR_SGK1` | Sí | `tools/ToolTexts.kt:265` | Literal. |
| C-18 `QR_TEXT_HIDDEN_PASSWORD` | Sí | `tools/ToolTexts.kt:286` | Literal. |
| C-19 `QR_TEXT_HIDDEN_OTP` | Sí | `tools/ToolTexts.kt:287` | Literal. |
| C-20 `PASSWORD_CHANGE_OLD_BACKUPS` | Sí | `tools/ToolTexts.kt:436` | Literal. |
| C-21 `PASSWORD_CHANGE_REPLACE_COPIES` | Sí | `tools/ToolTexts.kt:437` | Literal. |
| C-22 `AUTO_BACKUP_REPLACED` | Sí | `vault/AutoBackup.kt:95` | Literal. |
| C-23 `AUTO_BACKUP_REPLACE_WAITS` | Sí | `vault/AutoBackup.kt:98` | Literal. Se muestra en `SettingsScreen.kt:340`. |
| C-24 `CLOCK_CHANGED_TEXT` | Sí | `vault/VaultStore.kt:34` | Literal. Ahora también está en `ALL_TOOL_TEXTS`. |
| C-25a y C-25b, una sola constante | Sí | `tools/ToolTexts.kt:458`; `ui/screens/VaultScreen.kt:68` y `:337` | Literal. Ver D-2. |
| C-26 pantalla de licencias | Sí | `ui/screens/LicensesScreen.kt:34` | Literal. |
| C-27 `bip39.txt` | Sí | `assets/licenses/bip39.txt:1` | Literal. |
| §4 `AUTO_BACKUP_REPLACE_DROPPED` | Sí | `tools/ToolTexts.kt:444`; usos en `SettingsScreen.kt:183` (otra carpeta) y `:259` (desactivar) | Literal, y cierto en los dos casos. Tras «Desactivar» queda algo repetitivo («Las copias ya hechas siguen en la carpeta. Las copias automáticas anteriores… siguen en su carpeta.»), pero es exacto. Aceptable. |
| §4 `PASSWORD_CHANGED_FINGERPRINT_FAILED` | Sí | `tools/ToolTexts.kt:445`; uso en `ui/screens/VaultBackup.kt:122` | Literal. Es cierto porque `changeMaster` crea una clave de datos nueva (`Vault.kt:356-364`). |
| Tarea 11: «Copias automáticas» (`AUTO_BACKUP_OUTCOME_TITLE`) | Sí, OK | `tools/ToolTexts.kt:443`; `SettingsScreen.kt:358` | Correcto. |
| Tarea 11: textos de A6 en su versión final (`PASSWORD_CHANGED_TITLE`, `PASSWORD_CHANGE_COPIES`, `PASSWORD_CHANGE_WORKING`, `PASSWORD_CHANGE_CLOSE`, `RENEW_IDENTITY`, `RENEW_IDENTITY_DONE`) | Sí, OK | `tools/ToolTexts.kt:432-455` | Textos y orden del diálogo comprobados (`SettingsScreen.kt`, `MasterChangedDialog`). |
| D-1: `SHARE_NAMES` sin «foto-limpia» | Aceptable | `tools/share/ShareTemp.kt:16` y `:97-103` | `clear()` borra cualquier entrada de `compartir/`, se llame como se llame, así que el resto antiguo desaparece sin volver a escribir la palabra prohibida. Es mejor que lo que yo propuse. |
| D-2: se conserva `RESET_WIPES_DAMAGED` y se borra `FORGOT_DIALOG_TEXT` | Aceptable | `tools/ToolTexts.kt:458` | El resultado es el mismo: una sola fuente de verdad. |
| D-3: constante `PHOTO_FILE_BASE` | Aceptable | `tools/share/ShareTemp.kt:13` | Evita que el nombre del archivo y el texto que lo anuncia lleguen a ser distintos. |
| D-4: `SUPERSEDED_ANNEX_TEXTS` en el test, con el anexo intacto | Aceptable, con condición | `app/src/test/.../ToolTextsTest.kt` | La condición es que la tarea 15 actualice el anexo (y la línea 210 del diseño), la política de privacidad y la ficha de Play (R-9), y que después borre esa lista. Hasta entonces, el anexo publicado no refleja los textos de la app. |
| Palabras prohibidas en los textos de la 1.3.0 | Sin palabras prohibidas en `ToolTexts.kt`, salvo lo permitido | — | Solo quedan «no garantiza que sea seguro» (`QR_NO_ALERTS`, permitido) y el verbo «Limpiar» en «Limpiar foto» y en `PHOTO_DAMAGED` (R-1, que el dueño tiene que confirmar). «limpio» y «asegurar» aparecen en la lista BIP-39 en español (`bip39_spanish.txt:1024` y `:161`), que son datos y no textos de interfaz, aunque pueden salir como respuesta inventada. Es aceptable. |
| Palabras prohibidas en textos anteriores a la 1.3.0 | **No** | ver la tabla siguiente | Hay 6 textos de versiones anteriores que el grep encuentra. No son de las tareas 1 a 11, pero incumplen la regla en `app/src/main`. |
| `README.md` | OK | `README.md:27` | «diseñado para resistir también a ordenadores cuánticos futuros» no es «a prueba de cuánticos» y dice lo que pretende, no lo que garantiza. Está permitido. No hay más coincidencias. |

### Cambios pendientes (textos anteriores a la 1.3.0 con palabras prohibidas)

| Texto (archivo:línea) | Veredicto | Texto propuesto (exacto) |
|---|---|---|
| `ui/screens/NoticeScreen.kt:39`, «…Apúntala en un lugar seguro y exporta de vez en cuando una copia cifrada.» | Cambiar obligatorio | «Apúntala y guárdala donde solo tú puedas verla, y exporta de vez en cuando una copia cifrada.» |
| `ui/screens/VaultEntryScreen.kt:287`, «🎲 Generar una segura» | Cambiar obligatorio | «🎲 Generar una al azar» |
| `ui/screens/VaultEntryScreen.kt:304`, «🎲 Generar una segura» | Cambiar obligatorio | «🎲 Generar una al azar» |
| `ui/screens/IdentityScreen.kt:299`, «¿Borrarla de verdad?» | Cambiar obligatorio | «¿Borrar tu identidad?» |
| `ui/screens/SecurityToolsScreen.kt:162`, «Una clave aleatoria de 256 bits: imposible de adivinar. …» | Cambiar obligatorio | «Una clave aleatoria de 256 bits: en la práctica, nadie puede adivinarla. Apúntala en papel y guárdala SEPARADA del texto cifrado: sin ella nadie, ni tú, puede descifrarlo.» |
| `vault/Vault.kt:25`, `NOTE("Nota segura")`, la etiqueta del tipo | Cambiar obligatorio | «Nota» |

**Fundamento:**
- Son las palabras prohibidas por el dueño: «seguro» y «segura» sin «no garantiza que sea seguro», «de verdad» e «imposible».
- «imposible de adivinar» y «Nota segura» son afirmaciones absolutas de cualidad sobre el producto, que pesan en la valoración de la seguridad esperada (art. 7.2.a de la Directiva (UE) 2024/2853) y como posible acción engañosa (art. 6.1.b de la Directiva 2005/29/CE). Las demás son de tono, pero entran en la regla.
- `EntryType` se serializa por el nombre del enum (`NOTE`), no por la etiqueta, así que cambiar la etiqueta no toca ningún formato. Aun así, el implementador debe confirmar que `NOTE.label` no se escribe en la bóveda ni en exportaciones (el grep solo encuentra usos en la interfaz).
- El test `noForbiddenWords` solo cubre `ToolTexts.kt`. Recomiendo extenderlo a todas las cadenas de `app/src/main/java` (por ejemplo, con un test que lea el código fuente), para que estos casos no vuelvan.

**Veredicto de la verificación:** **Cambios.** Todo C-1 a C-27 y §4 está aplicado literalmente en todos sus usos, y las desviaciones D-1 a D-4 son aceptables (D-4 con la condición de la tarea 15). Quedan los 6 textos anteriores de la tabla, y extender `noForbiddenWords` a toda la app.

## CU-B (commits c808025 y 365e77c, 29-10-2026)

He comparado cada texto con el código que lo muestra:
- `CodesSession.kt:33-40` y `:207-236`, y `CodesScreen.kt:210-222`;
- `BiometricVault.kt:240-246` y `VaultScreen.kt:132-139`;
- `ImageLimits.kt:32-38`;
- `RedactScreen.kt:483`;
- `SettingsScreen.kt:303`;
- `README.md:174-180`.

| Texto | Veredicto | Texto propuesto exacto |
|---|---|---|
| `CODES_UNLOCK_TO_SAVE`, `tools/ToolTexts.kt:390` | Cambiar | «Desbloquea la bóveda para guardarlo. Lo que ves aquí se pierde si vuelves atrás, si se cierra la app o si estás fuera de esta pantalla más tiempo que el bloqueo de la bóveda (1 minuto como mínimo).» |
| `IMAGE_NO_MEMORY`, `tools/ToolTexts.kt:216` | OK | Sin cambios. |
| `FINGERPRINT_ENABLE_INTERRUPTED`, `tools/ToolTexts.kt:473` | Cambiar | «La activación de la huella se interrumpió porque cambió la pantalla (por ejemplo, al girar el móvil). Vuelve a tocar «Activar huella».» |
| `redactCanvasDescription(n)`, `tools/ToolTexts.kt:212` | OK | Sin cambios. |
| `PASSWORD_CHANGED_FINGERPRINT_FAILED`, ahora también en el diálogo, `ui/screens/SettingsScreen.kt:303` | OK | Sin cambios. Repite «Contraseña maestra cambiada.», que ya está en el título del diálogo. Es redundante, pero no es incorrecto. |
| Comprobación manual de la huella, `README.md:178` («sale «Se interrumpió al girar el móvil…»») | Cambiar, solo si se acepta el cambio de `FINGERPRINT_ENABLE_INTERRUPTED` | «sale «La activación de la huella se interrumpió…»» |
| Resto de líneas nuevas de `README.md:174-180` | OK | Sin cambios. Son instrucciones de prueba, citan textos que existen y no tienen palabras prohibidas. |

**Fundamento:**

- **`CODES_UNLOCK_TO_SAVE`.** La lógica que describe es correcta: se pierde al volver atrás, al morir el proceso y cuando el tiempo fuera llega al bloqueo de la bóveda, con un mínimo de 1 minuto (`awayTooLong`, `CodesSession.kt:40`). Cambio dos cosas:
  - «si cierras la app» pasa a «si se cierra la app». Android también puede cerrar la app en segundo plano sin que el usuario lo haga, y el texto no debe prometer que lo generado sigue ahí mientras el usuario no la cierre (art. 6.1.c de la Directiva (UE) 2024/2853, pérdida de datos).
  - «pasas fuera de esta pantalla» no es un español natural. Lo cambio por «estás fuera de esta pantalla».
  - Además, «el bloqueo de la bóveda» enlaza con el ajuste «Bloquear la bóveda tras no usarla» (`SettingsScreen.kt:405`).
- **`FINGERPRINT_ENABLE_INTERRUPTED`.**
  - El aviso salta con cualquier recreación de la actividad (`activity.isChangingConfigurations`, `BiometricVault.kt:245`): también al cambiar el tema del sistema, la ventana flotante o la pantalla dividida, como reconoce el propio informe. «al girar el móvil» solo es exacto en un caso.
  - El texto actual tampoco dice qué se interrumpió. Como sale en un snackbar de la pantalla Bóveda, tiene que poder entenderse solo.
  - Uso el pretérito indefinido («se interrumpió»), como el resto de mensajes de la huella.
- **`IMAGE_NO_MEMORY`.** Es exacto, no culpa al archivo y sustituye con razón al antiguo «archivo dañado». Hay coherencia de tono con los demás errores de imagen.
- **`redactCanvasDescription`.** Es claro para TalkBack, no promete nada y da información útil (cuántos bloques hay). Sigue lo recomendado en R-6.

**Veredicto CU-B: Cambios.** Hay que cambiar 2 textos, `CODES_UNLOCK_TO_SAVE` y `FINGERPRINT_ENABLE_INTERRUPTED`, y su cita en `README.md:178`.

## CU-B ronda 1 (commit 0425a88, 29-10-2026)

He comparado los textos con `tools/codes/CodesSession.kt`:
- `wipe()` vuelve a sortear el PIN, los códigos y las respuestas, y descarta el guardado pendiente. Conserva las webs, las preguntas, la longitud elegida y el formulario.
- `clear()`, al salir de la pantalla, lo quita todo.
- `vaultStateIs`: un bloqueo a mano con la app en uso borra siempre; el bloqueo por inactividad solo borra si la pantalla tampoco se usó durante ese tiempo; el bloqueo al salir de la app no borra.

| Texto | Veredicto | Texto propuesto exacto |
|---|---|---|
| `CODES_UNLOCK_TO_SAVE`, `tools/ToolTexts.kt:390` | Cambiar | «Desbloquea la bóveda para guardarlo. El PIN, los códigos y las respuestas se pierden si vuelves atrás, si se cierra la app, si bloqueas la bóveda o si estás fuera de esta pantalla más tiempo que el bloqueo de la bóveda (1 minuto como mínimo).» |
| `README.md`, prueba de «PIN y códigos» (en la línea que empieza «- [ ] 1.3.0, «PIN y códigos»») | OK | Sin cambios. Coincide con `wipe()` y `vaultStateIs`, incluido el caso de seguir escribiendo con el bloqueo por inactividad. |
| `README.md`, prueba de rotación en «Tachar» y «Limpiar foto» | OK | Sin cambios. |
| `README.md`, prueba de «Activar huella» | Cambiar | Sustituir «sin «No responde»» por «sin «La huella no responde…»» |

**Fundamento:**
- **`CODES_UNLOCK_TO_SAVE`.** «Lo que ves aquí se pierde» ya no es exacto en tres de los cuatro casos (bloquear, estar fuera más tiempo que el bloqueo y que se cierre la app no siempre lo vacía todo): las webs y las preguntas se conservan, y solo cambian el PIN, los códigos y las respuestas. El texto mínimo exacto nombra lo que se pierde. En el cuarto caso, «vuelves atrás», se pierde todo, y el texto propuesto no dice lo contrario: solo afirma lo que se pierde siempre.
- **«si bloqueas la bóveda».** Es correcto: el bloqueo a mano borra siempre, y el verbo en segunda persona excluye el bloqueo automático al salir de la app, que no borra. En el momento en que se lee este texto la bóveda ya está bloqueada, así que esta parte informa para después y no confunde.
- **Línea de la huella en el README.** Hay que citar el texto real de la app (`FINGERPRINT_NOT_RESPONDING`, «La huella no responde: …»), igual que las demás líneas citan los textos tal cual.
- **Palabras prohibidas.** No hay ninguna en las líneas nuevas.

**Veredicto CU-B ronda 1: Cambios.** Hay 2 cambios: `CODES_UNLOCK_TO_SAVE` y la cita del README.
