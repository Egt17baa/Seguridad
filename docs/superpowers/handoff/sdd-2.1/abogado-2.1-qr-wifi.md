# Abogado Digital: visto bueno inicial de «Leer el QR de la red» en «Añadir → Red WiFi» (2.1)

Fecha de referencia: 23-11-2026. Revisión **del principio** (sobre el diseño, antes de programar), según el punto 4 de «Skills: siempre juntas» de `CLAUDE.md`. Skills: `despacho-digital` y `privacidad-proteccion-datos`.

Documentos leídos: el diseño `docs/superpowers/specs/2026-11-23-clavero-2.1-qr-wifi-design.md`; los textos `QR_*` de `app/src/main/java/app/seguridad/tools/ToolTexts.kt` (líneas 281-422); los `QR_WIFI_*`, `WIFI_QR_*` y `qrWifiOtherSecurity` de `app/src/main/java/app/seguridad/tools/qr/WifiQr.kt` (líneas 349-420); `WIFI_WPA3_NOTE` y las etiquetas de la plantilla en `app/src/main/java/app/seguridad/vault/Templates.kt:37` y `:58-61`; `EntryEditSession.applyWifiQr` (`app/src/main/java/app/seguridad/vault/EntryForm.kt:562-577`); «Modo sencillo» (`app/src/main/java/app/seguridad/tools/ToolCatalog.kt:33-58`); la política `docs/privacidad.html`; la ficha `docs/ficha-google-play.md`, y el README.

## Veredicto

**Vale, con cambios.** El diseño no plantea ningún problema jurídico de fondo: no añade permisos, datos, destinatarios ni tratamientos, y todo pasa en el móvil. Los cambios obligatorios son de **veracidad**: un texto que, con «Modo sencillo» encendido, manda a un sitio donde la herramienta no se ve, y tres enumeraciones cerradas de la política y del README (usos de la cámara y de «Elegir imagen») que dejarían de ser completas.

## 1. Encuadre jurídico (breve)

- **Protección de datos.** El desarrollador no recibe nada: el texto del QR se analiza en el móvil, no sale de él y solo queda en el formulario (diseño, «Restricciones»). No hay tratamiento por parte del responsable de la app, así que no nace ninguna obligación nueva del art. 13 del Reglamento (UE) 2016/679 (RGPD) ni de la LOPDGDD. El uso que haga la persona con sus propias redes entra en la excepción doméstica del art. 2.2.c) RGPD. La contraseña de una wifi, por sí sola, normalmente no es un dato personal.
- **Google Play.** La sección de seguridad de los datos («No recoge ni comparte datos») no cambia: lo que se procesa solo en el dispositivo no cuenta como recogido. El permiso `CAMERA` ya está declarado y se pide igual. Pero la política de Play sobre datos de usuario exige que la política de privacidad sea **exacta y completa**, y la nuestra enumera los usos de la cámara de forma cerrada («solo mientras…»). Por eso hay cambios obligatorios en la política (§3).
- **Consumo y publicidad.** Los textos no hacen promesas ni usan superlativos. Ninguno dice que el código «sea seguro» ni que se haya comprobado (prácticas desleales: Directiva 2005/29/CE y Ley 3/1991, de Competencia Desleal, arts. 5 y 7, por si llegara a verse como práctica comercial). La regla de veracidad del proyecto es más estricta que la ley y es la que manda aquí.
- **Accesibilidad.** La Directiva (UE) 2019/882 no cubre este tipo de app gratuita. Aun así, se mantiene el criterio R-13 de la 1.3.0 (TalkBack lee «Escanear QR», sin «cámara»), que el diseño ya cumple con `ScanQrLabel`.

## 2. Textos nuevos del diseño

Ninguno usa palabras prohibidas (`ForbiddenWords`: «seguro/a», «limpio/a», «garantiza» sin «no», «imposible», «anónim», «100 %», «de verdad», «validez legal», «sospechos»).

| # | Texto propuesto | Dictamen | Redacción final |
|---|---|---|---|
| 1 | «Elegir imagen» (`QR_PICK_IMAGE`) | Bien. | Sin cambios. Botón de la cámara: `ScanQrLabel` («Escanear QR»), sin cambios. En el formulario de una wifi no hay otro botón «Escanear QR» (el del 2FA solo sale con el tipo Código 2FA, `VaultEntryScreen.kt:696-707`), así que no hay confusión. |
| 2 | «Este código no es de una red wifi. Para ver qué contiene, usa el Inspector de QR de Herramientas.» | **Obligatorio (O-1):** con «Modo sencillo» encendido, Herramientas no enseña el Inspector de QR (`SIMPLE_TOOL_ROUTES`, `ToolCatalog.kt:34`), y el texto mandaría a un sitio donde no se ve. Hacen falta dos variantes. | Apagado: **«Este código no es de una red wifi. Para ver qué contiene, usa el Inspector de QR de Herramientas.»** Encendido: **«Este código no es de una red wifi. Para ver qué contiene, apaga «Modo sencillo» en Ajustes y usa el Inspector de QR de Herramientas.»** |
| 3 | Los textos de rechazo de siempre (`qrWifiOtherSecurity`, `QR_WIFI_NOT_ADDABLE`, `QR_WIFI_PASSWORD_WITHOUT_TYPE`, `QR_WIFI_NAME_SPACES`, `QR_WIFI_UNREADABLE`) y los de la imagen (`QR_NOT_FOUND`, `QR_IMAGE_TOO_BIG`, `QR_IMAGE_TOO_LARGE`, `QR_IMAGE_DAMAGED`, `QR_NO_CAMERA_PERMISSION`, `QR_CAMERA_FAILED`) | Bien. Dicen «no lo añade a la bóveda» o «no se añade», que es cierto en el formulario (no rellena nada y no se guarda nada). Reutilizarlos sin cambios mantiene una sola redacción ya revisada en la 2.0. | Sin cambios. **Recomendado (R-1):** el tipo que enseña `qrWifiOtherSecurity` viene del código, que es dato hostil (decisión (c) de la 1.4.0). Tiene que pasar por `plainReportText` (sin controles, marcas de dirección ni invisibles), igual que en el Inspector, y conviene que un test lo compruebe en este formulario. |
| 4 | Diálogo: «¿Sustituir lo escrito?» / «El nombre de la red, la contraseña, el tipo de seguridad y si está oculta se cambiarán por los del código.» / «Sustituir» / «Cancelar» | Bien en el fondo. **Obligatorio (O-2), de veracidad:** el título dice «lo escrito», así que el diálogo solo puede salir si la persona escribió o eligió algo. Un valor que el formulario nuevo pone solo (por ejemplo «Red oculta» en «No», o un tipo de seguridad elegido de antemano) no cuenta como escrito. Si el constructor no puede distinguirlo, se usa el título alternativo. **Recomendado (R-2):** nombrar los campos con sus etiquetas (`Templates.kt:58-61`), que es lo que la persona ve. | Título: **«¿Sustituir lo escrito?»** (o, si no se puede distinguir lo escrito de lo que viene puesto, **«¿Sustituir los datos de la red?»**). Texto: **««Nombre de la red», «Contraseña», «Tipo de seguridad» y «Red oculta» pasarán a ser los del código.»** Botones: **«Sustituir»** y **«Cancelar»**. |
| 5 | Ningún aviso nuevo al rellenar; `WIFI_WPA3_NOTE` igual | Bien: los campos se ven rellenados, y el criterio de texto mínimo (M-3 de la 1.4.2) lo apoya. | Sin cambios. |

Notas sobre el texto 4:
- Un código de red abierta deja la contraseña vacía (`WifiQr.kt:136`). «Pasarán a ser los del código» ya lo cubre: no hace falta otra frase.
- El título de la entrada no aparece en el diálogo porque solo se rellena si está vacío (`EntryForm.kt:575`). Eso es correcto y no debe cambiar: si alguna vez se sustituye un título ya escrito, el diálogo tendría que decirlo.

## 3. Política, ficha, README, guía y notas de la versión

| Documento | Dictamen |
|---|---|
| `docs/privacidad.html`, línea 66 (Cámara) | **Obligatorio (O-3).** La lista de usos de la cámara está cerrada («solo mientras escaneas un código QR (de 2FA, …, o cualquier QR en el Inspector de QR)»). Hay que añadir el nuevo: «Cámara: solo mientras escaneas un código QR (de 2FA, de exportación de otra app de 2FA, de la tarjeta de un contacto, **de una red wifi al añadirla a la bóveda** o cualquier QR en el Inspector de QR). Las imágenes no se guardan ni se envían.» |
| `docs/privacidad.html`, línea 56 (imágenes elegidas) | **Obligatorio (O-4).** «Limpiar foto, Tachar en imagen y el Inspector de QR (con «Elegir imagen») abren solo la imagen que eliges tú…» pasa a «Limpiar foto, Tachar en imagen, el Inspector de QR y el formulario de una red wifi nueva (con «Elegir imagen») abren solo la imagen que eliges tú…». El resto del párrafo (compartir, copias) no aplica al formulario y sigue cierto. |
| `docs/privacidad.html`, línea 48 (selectores) | Sin cambios, **siempre que** el selector nuevo use el lugar en memoria del Inspector (`PickerPlace.QR_IMAGE`, `QrInspectScreen.kt:199`) y nunca uno que se apunte en disco. La política dice que «la imagen de un QR» no se apunta. **Obligatorio para el constructor (O-5):** usar ese lugar, o no recordar ninguno. |
| `docs/privacidad.html`, fecha | **Obligatorio (O-6):** con O-3 y O-4, «Última actualización» pasa a la fecha del cambio, justo antes de fusionar (como la fila 49 de la 2.0). |
| `README.md`, línea 72 (permisos) | **Obligatorio (O-7).** La misma enumeración cerrada: «`CAMERA` (solo se pide al escanear un QR, de 2FA, de la tarjeta de un contacto, **de una red wifi al añadirla a la bóveda** o en el Inspector de QR)». |
| `README.md`, líneas 62-63 | **Recomendado (R-3):** una frase en «QR de una red wifi»: «Desde la 2.1, al añadir una entrada «Red wifi» se puede leer el QR de la red con la cámara o desde una imagen: rellena el formulario con las mismas reglas que el Inspector de QR (WPA2 se guarda como WPA; WPA3 y SAE no se añaden), y un código que no es de wifi no se enseña.» Más la sección «Pruebas de la 2.1» que ya prevé el diseño. |
| `docs/ficha-google-play.md` | **Opcional (Op-1).** Ninguna norma lo exige y la descripción completa está casi en el tope (3 927 de 4 000; `PublishedTextsTest`). Si se quiere, la línea 33 podría decir: «• QR de una red wifi: léelo al añadirla o enséñalo para que otro móvil se conecte. Quien vea el código ve la contraseña.», siempre que la prueba del tope pase. Ni la ficha ni sus «Novedades» nombran otras apps. |
| Guía de novedades (`settings/Guide.kt`) | **Sin cambios.** Es la guía «de la 2.0» (`GUIDE_OPEN_NEW`, línea 82) y dice «nada fuera de la 2.0» (línea 17). Meter ahí la 2.1 la haría falsa. Una guía de la 2.1 sería otro cambio y no hace falta para una sola función. |
| Notas de la versión (GitHub y «Novedades» de Play) | **Recomendado (R-4):** una línea como «Al añadir una red wifi, puedes leer su QR con la cámara o desde una imagen.», sin palabras prohibidas, sin decir que comprueba la red y sin nombres de otras apps. Pasa por la revisión final. |

## 4. Otras cosas antes de programar

- **Recomendado (R-5):** el diseño dice que un QR que no es de wifi «no se enseña». Que tampoco se copie ni se conserve para pasarlo al Inspector. El texto O-1 solo indica dónde está el Inspector; no debe llevar ningún botón que abra el código en él, porque eso sería el enfoque B que se descartó (el texto viajaría entre pantallas).
- **Recomendado (R-6):** con el permiso de cámara denegado, `QR_NO_CAMERA_PERMISSION` dice «elige una imagen con el código QR». En el formulario también se puede escribir a mano, pero el texto no lo niega, así que sigue siendo cierto. Se reutiliza sin cambios.
- **Opcional (Op-2):** si en las pruebas en el móvil el botón «Escanear QR» encima de los campos no se entiende, se puede poner una línea corta delante, como «Rellenar con el QR de la red:». No la exijo, por el criterio de texto mínimo.
- Todos los textos nuevos o reutilizados en este formulario se apuntan en «Textos para el Abogado Digital (revisión final)» del informe de la tarea. Queda pendiente el **segundo visto bueno, al final**, con el código hecho, la política y el README cambiados, y antes de publicar.

## Resumen de cambios

- **Obligatorios:** O-1 (texto 2 con su variante para «Modo sencillo»); O-2 (el diálogo solo dice «lo escrito» si la persona escribió algo, o usa «¿Sustituir los datos de la red?»); O-3 y O-4 (la política: cámara y «Elegir imagen»); O-5 (el selector usa `PickerPlace.QR_IMAGE` en memoria o ninguno); O-6 (la fecha de la política); O-7 (los permisos del README).
- **Recomendados:** R-1 (`plainReportText` en el tipo de `qrWifiOtherSecurity`, con su test); R-2 (las etiquetas de los campos en el diálogo); R-3 (el README); R-4 (las notas de la versión); R-5 (nada del QR que no es de wifi se copia ni pasa al Inspector); R-6 (reutilizar `QR_NO_CAMERA_PERMISSION`).
- **Opcionales:** Op-1 (la ficha, si cabe); Op-2 (una línea delante de los botones).
