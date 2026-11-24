# Clavero 2.1: revisión final del Abogado Digital (24-11-2026)

Revisión final antes de publicar (punto 4 de `CLAUDE.md`). Fecha de referencia: 24-11-2026. Rama `ccr-0aa27f95-u038cd`, sobre `3b0de68`. Alcance: todos los textos de la 2.1 frente a `v2.0.0` (`git diff v2.0.0...HEAD`): interfaz, política, ficha, README, notas de la versión y «Novedades», y licencias.

Hechos que doy por ciertos, como en los dictámenes anteriores: desarrollador particular residente en España, sin ánimo de lucro; app gratuita, sin anuncios, compras ni donaciones; la app no envía datos al desarrollador; se publica fuera de Francia (con ultramar), Rusia y Bielorrusia.

Módulos aplicados: `despacho-digital`, `privacidad-proteccion-datos`, `regulacion-digital-ue` y `propiedad-intelectual-ia` (icono y fuente).

## Veredicto

**Se puede publicar, con cambios.** Un cambio obligatorio (O-1, veracidad de la ficha) y cinco recomendados (R-1 a R-5). Ninguna norma exige más. La fecha de la política pasa a 24 de noviembre de 2026.

## Marco

- **Protección de datos (RGPD, LOPDGDD).** La 2.1 no crea ningún tratamiento del desarrollador: lo leído del QR de wifi y el aspecto se quedan en el móvil. La política es transparencia de cortesía y lo que pide Google Play; debe ser exacta (arts. 5.1.a y 12 RGPD, por analogía, y la regla de veracidad del proyecto).
- **Consumo y prácticas comerciales.** La ficha y las «Novedades» no pueden inducir a error (arts. 5 y 7 de la Ley 3/1991, de Competencia Desleal; Directiva 2005/29/CE). Lo que se diga de una versión anterior a la que el usuario tiene debe ser cierto para él.
- **Regulación UE.** La 2.1 no cambia la calificación: sin actividad comercial, fuera del Cyber Resilience Act (Reglamento (UE) 2024/2847, cdos. 18 y 19) y de la European Accessibility Act. No es plataforma (DSA) ni usa IA en la app.
- **Propiedad intelectual.** La fuente del icono (Liberation Serif Bold 2.1.5, SIL OFL 1.1) se usa como contornos en un dibujo, sin distribuir la fuente: la OFL lo permite y no exige atribución. La imagen de referencia del logo la generó el dueño; solo se tomaron ideas y composición (dictamen `abogado-2.1-logo-CL.md`).

## Cambios obligatorios

### O-1. La ficha no puede decir que la 2.1 cambia el formato de la bóveda

La descripción que se pega en Play dice que «al actualizar» la bóveda cambia de formato y que «las versiones anteriores» ya no la abren. Para quien actualiza de la 2.0 a la 2.1 es falso: no hay cambio de formato y la 2.0 abre lo que escribe la 2.1 (`README.md:83`). Solo es cierto desde la 1.x. Es la duda (e) del registro (`sdd-2.1/progress.md:48`); la acepto con una redacción que cubre también la segunda mitad de la frase.

- `docs/ficha-google-play.md:57` (se pega en Play).
  - Actual: «• Al actualizar, la bóveda pasa a un formato nuevo la primera vez que la abres con la contraseña maestra, y las versiones anteriores ya no abren la bóveda ni sus copias nuevas. Exporta antes una copia.»
  - Propuesto: «• Al actualizar desde una versión 1.x, la bóveda pasa a un formato nuevo la primera vez que la abres con la contraseña maestra, y las versiones 1.x ya no abren la bóveda ni sus copias nuevas. Exporta antes una copia.»
  - Suma 15 caracteres: la descripción pasa de 3 891 a unos 3 906 (3 948 contando cada salto de línea dos veces), dentro de 4 000. Que lo confirme `PublishedTextsTest.theFullPlayDescriptionFits`.
- `docs/ficha-google-play.md:124` (versión larga, no se pega, pero se publica en el repositorio).
  - Actual: «• Al actualizar desde una versión anterior, la bóveda pasa a un formato nuevo la primera vez que la abres con la contraseña maestra: después, las versiones anteriores de Clavero no abren la bóveda ni sus copias nuevas. Exporta antes una copia.»
  - Propuesto: «• Al actualizar desde una versión 1.x, la bóveda pasa a un formato nuevo la primera vez que la abres con la contraseña maestra: después, las versiones 1.x de Clavero no abren la bóveda ni sus copias nuevas. Exporta antes una copia.»
- Si `docs/ficha-google-play.md:69` da el recuento de la versión larga, se pone al día.

## Cambios recomendados

### R-1. «Bordes iluminados» y «Alto contraste» (recomendación (a) y 6 del director)

Con «Alto contraste», «Vidrio» no dibuja el borde iluminado (`ui/theme/Glass.kt:300`, `litEdge = strong && !highContrast`); con «Reducir transparencias» sí. `STYLE_MORE` ya dice que los dos ajustes hacen opacos los paneles, pero no dice lo del borde. Decido:
- en la interfaz, una frase más, porque es donde el usuario combina los ajustes;
- en las «Novedades», las notas, el README y la ficha, **sin cambios**: describen el estilo; «Alto contraste» es otro ajuste que el usuario elige y que, por su propia naturaleza, quita los adornos. No induce a error.

`app/src/main/java/app/seguridad/ui/screens/AppearanceScreen.kt:100-102` (`STYLE_MORE`):
- Actual: «… «Reducir transparencias» y «Alto contraste» hacen opacos todos los paneles.»
- Propuesto: «… «Reducir transparencias» y «Alto contraste» hacen opacos todos los paneles, y «Alto contraste» quita también los bordes iluminados de «Vidrio».»
- `AppearanceTextsTest` fija el texto: se pone al día con él.

### R-2. La versión larga de la ficha sin tono de notas de la versión (recomendación 5 del director)

«Tres estilos más en Ajustes → Aspecto» describe un cambio, no la app. En una descripción, los estilos se nombran juntos. `docs/ficha-google-play.md:110-111` (dos viñetas) pasan a una:
- Actual (`:110`): «• Los estilos «Cristal», con paneles translúcidos (desde Android 12, las barras y los diálogos difuminan el fondo de la app que tienen detrás), y «Sólido», sin transparencias.»
- Actual (`:111`): «• Tres estilos más en Ajustes → Aspecto: «Degradado» (paneles opacos sobre un fondo en degradado), «Vidrio» (como «Cristal», más translúcido y con bordes iluminados) y «Transparente» (desde Android 14; deja ver, atenuado, el fondo de pantalla del móvil; la app no lee su imagen ni pide permisos).»
- Propuesto (una sola viñeta): «• Cinco estilos: «Sólido», sin transparencias; «Degradado», con paneles opacos sobre un fondo en degradado; «Cristal», con paneles translúcidos (desde Android 12, las barras y los diálogos difuminan el fondo de la app que tienen detrás); «Vidrio», como «Cristal», más translúcido y con bordes iluminados; y «Transparente» (desde Android 14), que deja ver, atenuado, el fondo de pantalla del móvil: la app no lee su imagen ni pide permisos.»
- El recuento de `:69` se pone al día. La línea «Tres estilos más…» sigue bien en las notas y en las «Novedades», que sí son de la versión. En `README.md:72` puede quedarse: el README cuenta la historia por versiones.

### R-3. Las capturas de la ficha, con la 2.1

`docs/ficha-google-play.md:7`: las capturas enseñan el icono y Ajustes → Aspecto, que cambian en la 2.1. Una captura de la 2.0 con el icono viejo no es engañosa, pero sí desfasada.
- Actual: «Las capturas se hacen de nuevo con la 2.0, con el aspecto «Cristal» y solo datos inventados:»
- Propuesto: «Las capturas se hacen de nuevo con la 2.1, con el aspecto «Cristal» y solo datos inventados:»

### R-4. El «Icono nuevo» de la 2.0 en el README

`README.md:71` termina en «Icono nuevo.», que era el de la 2.0. Ahora lo sustituye el de la 2.1 (`README.md:73`). Leído solo, parece el icono actual.
- Actual (final de `:71`): «… (Ajustes → Aspecto → «Sólido»). Icono nuevo.»
- Propuesto: «… (Ajustes → Aspecto → «Sólido»). Icono nuevo, que la 2.1 sustituye (abajo).»

### R-5. La fuente del icono, visible (R-1 del dictamen del logo, sigue sin hacer)

No es obligatorio: la OFL no pide atribución por usar los contornos en un dibujo, y la fuente no va en el APK. La ficha (`:7`) y los XML del icono ya lo dicen. Recomiendo una frase en el README, al lado de la del icono, para que quien reutilice los archivos lo sepa.
- `README.md:9`, después de «Los archivos del icono se publican con la Apache-2.0, pero su dibujo identifica a Clavero.», añadir: «Las letras «C» y «L» del icono son contornos de Liberation Serif Bold (SIL Open Font License 1.1); la app no incluye la fuente.»
- La línea en la pantalla de «Licencias» (con el texto de la OFL como recurso) sigue siendo opcional, como dije en el dictamen del logo. Si se hace, nunca dice que la app incluya la fuente.

## La política de privacidad y su fecha

**La fecha pasa a «24 de noviembre de 2026»** (`docs/privacidad.html:22`).

- La política dice hoy «23 de noviembre de 2026». El 23-11 entraron el formulario de wifi (cámara e «Elegir imagen») y la frase de «Transparente» (`5d4e41b`, `13491f7`).
- El 24-11 (`d581d2e`, F-1) cambió el contenido de `docs/privacidad.html:37`: «(desde Android 14)» y «le pide a Android que no pase tus toques a la app del fondo de pantalla». Es una afirmación nueva sobre lo que hace la app con lo que toca el usuario.
- **No es un cambio material** en el sentido de que haga falta avisar a los usuarios: no hay tratamiento nuevo ni datos que salgan; al contrario, se corta un canal hacia otra app. Pero la fecha de «Última actualización» debe ser la del último cambio de contenido: si no, el texto afirma algo falso sobre sí mismo. Lo dijo ya la R-A6 del dictamen del Aspecto: «Si se cambia la política, se cambia su fecha».
- Si antes de fusionar cambia algo más de la política, la fecha es la de ese día.

El contenido de la política (`:37`, `:56` y `:66`) es correcto para el código:
- «Transparente» solo desde Android 14 (`TRANSPARENT_MIN_SDK`) y el corte de los toques desde Android 14 con cualquier aspecto (`android/SecureWindow.kt`, `cutWallpaperTouches`). «Le pide» no promete lo que haga un fabricante: bien dicho.
- Que un fondo animado sepa cuándo se ve (R-21-5 de la auditoría) no necesita frase: no recibe nada de Clavero y pasa igual con el lanzador.
- El formulario de una red wifi nueva con «Elegir imagen» y la cámara: correcto, y solo al añadir (no al editar).

## Las notas de la versión y las «Novedades»

`docs/superpowers/handoff/2026-11-24-clavero-2.1-notas.md`. **Sin cambios**, con las condiciones que el propio borrador pone a cada línea (las pruebas en el móvil y, para «Icono nuevo», la búsqueda por imagen del dueño).

- **Duda (d):** «No cambia el formato de la bóveda ni el de las copias: la 2.1 abre lo mismo que la 2.0. No añade permisos, conexiones ni dependencias.» **Vale.** Lo comprobé: frente a `v2.0.0`, el manifiesto no cambia (`INTERNET` y `CAMERA`, los de la 2.0), en Gradle solo cambia `versionName` y no cambian `gradle/` ni los formatos. El permiso de cámara que usa el formulario de wifi ya existía. Es útil para el usuario y es cierto.
- «Icono nuevo.»: vale, sin decir por qué ni la fuente (O-7 del dictamen del logo).
- La línea del Aspecto: sin «líquido» ni otro sistema (O-A1), con «desde Android 14» (F-1) y sin prometer contraste. No hace falta decir que «Transparente» se ve como «Cristal» en pantalla dividida: es un detalle de uso que la app explica en `STYLE_TRANSPARENT_MORE`, y su ausencia no lleva a error sobre lo que se compra o instala.
- «Bordes iluminados» con «Alto contraste»: sin cambios aquí (R-1).
- Las «Novedades» tienen 383 caracteres (máximo 500) y no nombran otras apps. Las notas de GitHub tampoco: esta versión no cambia qué se importa, así que no hace falta la línea de marcas (R31).
- Los corchetes con el origen de cada línea se quitan al publicar.

## Revisado sin cambios

**Interfaz, Aspecto** (`ui/screens/AppearanceScreen.kt:91-116`):
- Los nombres «Degradado», «Vidrio» y «Transparente»: palabras comunes, descriptivas, sin evocar el aspecto de otro sistema (O-A1, O-A2).
- `STYLE_SOLID_SHORT`, `STYLE_GRADIENT_SHORT`, `STYLE_GLASS_SHORT`, `STYLE_GLASS_STRONG_SHORT` y `STYLE_TRANSPARENT_SHORT`: dicen lo que hace cada estilo (O-A5).
- `STYLE_TRANSPARENT_MORE`: «Se ve nítida, atenuada por una capa de color.» y «En pantalla dividida o en una ventana flotante, el fondo de pantalla no se ve.» son ciertas para el código (decisión del dueño del 24-11-2026 e I-1). La frase de quien mira la pantalla (R-A3) y la de la batería con fondo animado, bien. No necesita la frase de los toques: está en la política, que es su sitio.
- `TRANSPARENT_FROM_14` («Transparente», desde Android 14.): clara y cierta.
- `CUSTOM_MORE`: «contraste suficiente» viene de la 2.0 (O-C6, aceptado entonces: describe lo que hace la app, no garantiza accesibilidad); la frase de la intensidad cumple la R-A5.
- `STYLE_MORE`: vuelve al texto de la 2.0 («el fondo de la app que tienen detrás»), que es el exacto. Solo la frase de R-1.

**Interfaz, QR de wifi** (`tools/qr/WifiQr.kt:447-466`, `tools/ToolTexts.kt`, `vault/EntryForm.kt`, `ui/screens/VaultEntryScreen.kt`, `ui/screens/QrImageRead.kt`):
- `WIFI_SCAN_NOT_WIFI` y su variante de «Modo sencillo»: no enseñan el contenido y mandan al Inspector (O-1 del dictamen del QR de wifi).
- `WIFI_SCAN_REPLACE_TITLE_TYPED`, `WIFI_SCAN_REPLACE_TITLE_FILLED`, `WIFI_SCAN_REPLACE_TEXT`, `WIFI_SCAN_REPLACE` y `CANCEL`: claros; nombran los cuatro campos que cambian.
- Los textos del Inspector reutilizados en el formulario (`QR_NO_CAMERA_PERMISSION`, `QR_CAMERA_FAILED`, `QR_NOT_FOUND`, `QR_IMAGE_TOO_BIG`, `QR_IMAGE_TOO_LARGE`, `QR_IMAGE_DAMAGED`, `QR_READING_IMAGE`, `QR_WIFI_*`, `qrWifiOtherSecurity`, `qrBidi`): encajan en el formulario. «Puedes crear la entrada a mano» de `QR_WIFI_NOT_ADDABLE` se entiende allí como «escribir los datos a mano», y es cierto.
- El tipo de seguridad del QR, limpio y cortado a 32 caracteres con «…» (R-1 del dictamen del QR de wifi), también en el campo «Cifrado» del Inspector: bien.
- Las comillas anidadas («…«Modo sencillo»…»): en la app no hay anidamiento real (un solo nivel dentro de una frase sin comillas). Solo aparece en los documentos al citar textos de la app; la RAE recomienda « “ ” » en el segundo nivel, pero es ortotipografía, no derecho, y el proyecto usa «» de forma coherente. Sin cambios.

**README:** «La 2.1» (`:83`), «Leer el QR de una red wifi al añadirla (2.1)» (`:65`), «Aspecto (2.1)» (`:72`), «Icono nuevo (2.1)» (`:73`) y «Pruebas de la 2.1» (`:431-468`): ciertos y sin palabras prohibidas. Las casillas de `dumpsys` dicen que la salida se lee en el ordenador del dueño y no se sube (regla «Privacidad del dueño»). Las de «Transparente» piden un fondo de pantalla que no sea del dueño. Solo R-4 y R-5.

**Ficha:** `:3` (reglas, con O-A6), `:7` (salvo R-3; R-A7 de las capturas de «Transparente», bien), `:50` (cinco estilos, «desde Android 14»), `:112-113`. Solo O-1, R-2 y R-3.

**Licencias y marcas:**
- Liberation Serif: uso permitido por la OFL; el logo no queda sujeto a ella. Nombrar «Liberation Serif» para atribuir es uso descriptivo de la marca de Red Hat. Los XML del icono llevan la atribución (O-7 del dictamen del logo).
- Del icono no se dice «único», «original», «registrado» ni «protegido», ni lleva ® ni ™ (`ficha:7`).
- Ningún nombre de otra app en la ficha ni en las «Novedades».

**Regulación:** nada cambia en DSA, CRA, accesibilidad ni IA. La app sigue sin publicarse en Francia: la 2.1 no toca los países.

## Para el dueño

1. **Antes de publicar el icono:** la búsqueda por imagen en TMview y en la WIPO Global Brand Database, con los códigos de Viena del dictamen del logo (O-4), y apuntar el resultado en `sdd-2.1/progress.md`. Hasta entonces, «Icono nuevo» no sale en las notas ni en las «Novedades».
2. **Antes de Play:** mirar la marca CLAVERR del Reino Unido (clases 9, 35 y 42).
3. **Las pruebas en el móvil** de «Pruebas de la 2.1» (QR de wifi y Aspecto) antes de publicar sus líneas, en especial: «Transparente» nítido, detrás solo el fondo de pantalla también en la animación de abrir (O-A4), y pantalla dividida.
4. **Riesgo con nombre, a conocer:** commits anteriores de la rama nombran la herramienta con la que generaste la imagen de referencia del logo (`sdd-2.1/progress.md:49`). Quitarlo exige reescribir el historial con *force push*; es decisión tuya, como la C-2 de la 2.0.
5. **Si publicas en Play:** pegar la descripción con O-1 aplicada y las «Novedades» del borrador; capturas nuevas con la 2.1 (R-3) y solo datos inventados.
6. Lo de siempre: tu copia del `.jks` y de sus contraseñas fuera del móvil (C-3) y los secretos `SEGURIDAD_*`.

## Para quien aplique los cambios

- O-1 es obligatorio. R-1 a R-5 se aplican salvo que `experto-seguridad-cripto` o el Ingeniero Móvil Senior vean un problema técnico; si lo ven, se busca un texto que valga para todos y se apunta.
- R-1 cambia una constante que fija `AppearanceTextsTest`.
- Tras O-1 y R-2, `PublishedTextsTest` (4 000 caracteres) y el recuento de `ficha:69`.
- La fecha de la política, al final, el día en que se aplique el último cambio de su contenido.
