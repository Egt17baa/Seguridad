# Ficha de Google Play (borrador)

Textos para *Play Console → Presencia en la tienda → Ficha principal*. Reglas: descripción exacta, sin superlativos de seguridad («segura», «auditada», «inviolable», «grado militar») ni promesas absolutas («imposible de descifrar», «a prueba de cuánticos», «100 %», «como Signal», «con validez legal», «anónimo», «garantiza» salvo «no garantiza», «limpio», «sospechoso»), sin las palabras «wallet», «cartera» ni «monedero» (O-11 de la 2.0), sin decir que la app o su aspecto son «accesibles», que «cumplen WCAG» o que el contraste está garantizado (O-C6), sin nombrar ni describir el aspecto «Cristal» con el nombre del de ningún sistema o app (O-C4), sin marcas de tarjetas, bancos ni otras apps (O-9), y con los avisos de «sin recuperación» y «sin auditoría independiente». La app se llamaba «Seguridad» hasta la 1.2.2; desde la 1.3.0 su nombre visible es «Clavero» (decisión del dueño del 31-10-2026; el identificador `io.github.egt17baa.seguridad` no cambia).

Al publicarla (decisiones del dueño del 31-10-2026, con la revisión final del Abogado Digital; detalle en [`PUBLICAR.md`](PUBLICAR.md), apartados 3.5 a 3.7): en todos los países menos Francia (con sus departamentos y territorios de ultramar, si Play los lista aparte), Rusia y Bielorrusia; Seguridad de los datos, «No recoge ni comparte datos»; desarrollador «Egt17baa», no comerciante. Las capturas de pantalla no muestran «wallet» ni datos reales.

**Recursos gráficos (2.0, O-C2 del Abogado):** el icono de la ficha es [`docs/play/icono-512.png`](play/icono-512.png), el icono nuevo de la 2.0. Las capturas se hacen de nuevo con la 2.0, con el aspecto «Cristal» y solo datos inventados: sin texto de clasificación, precio ni promoción («n.º 1», «gratis», «nuevo», «2.0»), sin marcos de un móvil de una marca concreta y sin la barra de estado con notificaciones o datos de nadie. Del logo no se dice que sea «único», «original», «registrado» o «protegido», ni lleva ® ni ™; las «Novedades» dicen solo «Icono nuevo».

**Longitud (R-8 del Abogado, revisión final de la 2.0):** la descripción completa no puede pasar de 4 000 caracteres (contador de Play Console).

## Nombre de la app (máximo 30 caracteres)

Clavero: bóveda cifrada

(Si se prefiere sin subtítulo, «Clavero».)

## Descripción breve (máximo 80 caracteres)

Bóveda cifrada, 2FA, contactos y cifrado de archivos. Sin cuentas ni anuncios.

## Descripción completa

Clavero (antes «Seguridad») guarda tus contraseñas, códigos 2FA, frases semilla, notas, tarjetas bancarias, redes wifi, licencias y pasaportes en bóvedas cifradas en tu móvil, y cifra textos y archivos. Sin cuentas, nube propia, analítica ni anuncios. Código abierto (Apache-2.0).

Bóveda
• Protegida por tu contraseña maestra (Argon2id) y, si quieres, por tu huella. Hasta 10 bóvedas, cada una con su contraseña maestra; solo hay una abierta a la vez.
• Plantillas de tarjeta, wifi, licencia y pasaporte, campos personalizados y notas con formato sencillo.
• Carpetas, vistas, etiquetas de color, favoritos y una búsqueda que perdona erratas.
• Papelera de 30 días con «Deshacer», e historial de cada entrada.
• Adjuntos cifrados (hasta 5 MB cada uno).
• Salud de la bóveda: contraseñas repetidas, débiles, antiguas o sin 2FA.
• Copias cifradas que exportas tú, y copia automática opcional en hasta 3 carpetas, con cada cambio o con calendario. Comprueba una copia sin importarla.
• QR de una red wifi guardada para que otro móvil se conecte. Quien vea el código ve la contraseña.
• Importa de navegadores y de algunos gestores de contraseñas y apps de 2FA (la lista está en la app), con vista previa y una copia cifrada antes.

2FA, cifrado y contactos
• Códigos TOTP con escáner QR o con la clave a mano.
• Cifra textos y archivos (AES-256-GCM, ChaCha20-Poly1305 y otros algoritmos estándar).
• Cifra textos y archivos para tus contactos de Clavero, o fírmalos, y envíalos por el canal que quieras; compara su huella. El cifrado entre contactos está diseñado para resistir también a ordenadores cuánticos futuros (X-Wing, en proceso de estandarización); las firmas (Ed25519), no.

Herramientas
• Generador de contraseñas y frases, PIN, códigos de recuperación y respuestas inventadas.
• Limpiar foto (quita la ubicación y otros datos ocultos del archivo; no cambia lo que se ve) y Tachar en imagen.
• Inspector de QR: enseña qué hay en un QR sin abrir nada. No garantiza que un enlace sea seguro.
• Laboratorio de hashes: que la huella coincida no dice quién la publicó.
• Laboratorio clásico (César, Vigenère y Enigma): cifrados rotos, para aprender; no protegen nada.
• Repartir un secreto (SLIP-0039): las partes no son una copia de recuperación SLIP-39 para un dispositivo hardware.

Aspecto
• Estilo «Cristal», con paneles translúcidos (desde Android 12, las barras y los diálogos difuminan el fondo de la app), o «Sólido», y varias paletas. Modo sencillo y guía al empezar.

Privacidad
Las bóvedas se quedan cifradas en tu móvil. Sale lo que tú exportas, guardas, copias o compartes, y las copias cifradas que la app hace donde elijas. La única conexión a Internet es opcional: al pulsar «¿Filtrada?» o «Comprobar filtraciones», consulta Pwned Passwords de Have I Been Pwned con solo los 5 primeros caracteres de la huella SHA-1 de cada contraseña.

Antes de usarla, ten en cuenta
• Sin recuperación: si olvidas la contraseña maestra, nadie puede recuperar lo guardado. Exporta copias.
• Al actualizar, la bóveda pasa a un formato nuevo la primera vez que la abres con la contraseña maestra, y las versiones anteriores ya no abren la bóveda ni sus copias nuevas. Exporta antes una copia.
• La copia con calendario solo se hace con la bóveda abierta: si no abres la app, no hay copia.
• Borrar algo no lo borra de las copias ya hechas, y cambiar la contraseña maestra no las cambia: se siguen abriendo con la anterior.
• Si alguien roba tu identidad (por ejemplo, con una copia y tu contraseña maestra), podrá leer lo que te enviaron antes y firmar como tú. El canal ve los metadatos.
• El archivo exportado de otra app suele estar sin cifrar, salvo si lo proteges con contraseña.
• Limpiar foto y Tachar no cambian el original: comparte la copia.
• Sin auditoría independiente. Para fondos importantes, usa un dispositivo hardware dedicado.

Gratuita y sin compras. Código fuente: https://github.com/Egt17baa/Seguridad

## Versión larga (no cabe en Play)

No se pega en Play: pasa de 4 000 caracteres (6 948 sin Markdown). Es la descripción de antes de la revisión final del Abogado Digital de la 2.0 (O-3), que se conserva como referencia; la de arriba es la que se pega.

Clavero (antes «Seguridad») guarda tus contraseñas, códigos 2FA, frases semilla, notas, tarjetas bancarias, redes wifi, licencias y pasaportes en bóvedas cifradas en tu móvil, y cifra textos y archivos. No tiene cuentas, nube propia, analítica ni anuncios, y es de código abierto (Apache-2.0).

**Bóveda**
• Accesos, frases semilla, notas y códigos 2FA, cifrados con una clave protegida por tu contraseña maestra (Argon2id) y, si quieres, por tu huella.
• Plantillas de tarjeta bancaria, red wifi, licencia y pasaporte, campos personalizados y notas con formato sencillo.
• Hasta 10 bóvedas, cada una con su contraseña maestra; solo hay una abierta a la vez.
• Carpetas, vistas guardadas, etiquetas de color, favoritos y una búsqueda que perdona erratas.
• Papelera durante 30 días, con «Deshacer», e historial de los cambios de cada entrada.
• Adjuntos cifrados (hasta 5 MB cada uno).
• Salud de la bóveda: contraseñas repetidas, débiles, antiguas o sin 2FA, con una sugerencia cada vez.
• Copias cifradas que exportas tú, y copia automática opcional hasta en 3 carpetas que elijas, cada una con su calendario (con cada cambio, diaria, semanal o mensual). Las copias con calendario se hacen con la bóveda abierta, al abrir la app o al desbloquearla, y se saltan si no hubo cambios desde la última de esa carpeta (nunca la primera).
• Enseña el QR de una red wifi guardada para que otro móvil se conecte. Quien vea el código ve la contraseña.
• Comprueba una copia sin importarla: que tu contraseña la abre y que cada adjunto está y coincide con lo que guarda su entrada.
• Importa los archivos CSV de los navegadores más usados y las exportaciones de algunos gestores de contraseñas y apps de 2FA (la lista está en la app y en el código fuente), también las protegidas con contraseña, y añade sus entradas a tu bóveda sin reemplazarla. Antes de guardar te enseña lo que entra, lo que ya tienes y lo que no entra, y hace una copia cifrada de tu bóveda. Al terminar te ofrece quitar el archivo exportado.

**Códigos 2FA**
• TOTP con escáner QR o con la clave a mano.
• Importa las cuentas que algunas apps de 2FA exportan como códigos QR.

**Cifrado**
• Cifra y descifra textos y archivos con AES-256-GCM o ChaCha20-Poly1305 (y otros algoritmos estándar), con contraseña o con una clave aleatoria.

**Contactos**
• Tu identidad y tus contactos viven en la bóveda cifrada de tu móvil. Añade a un contacto escaneando su QR o pegando su tarjeta y comparando la huella.
• Cifra un texto o un archivo para un contacto y envíalo por el canal que quieras (una app de mensajería o el correo). Solo quien tenga la identidad de ese contacto podrá abrirlo; comprueba su huella para confirmar que es suya.
• Firma un texto o un archivo: demuestra a tus contactos que es tuyo y que no ha cambiado.
• El cifrado entre contactos está diseñado para resistir también a ordenadores cuánticos futuros (cifrado híbrido X-Wing, en proceso de estandarización). Las firmas no: usan Ed25519.

**Herramientas**
• Generador de contraseñas y frases, PIN al azar sin patrones habituales, códigos de recuperación y respuestas inventadas para preguntas de seguridad.
• Limpiar foto: quita la ubicación y otros datos ocultos del archivo antes de enviarla; la copia solo conserva la imagen, sus datos de color y, en las JPEG, la orientación. No cambia lo que se ve en ella. Las fotos HEIC y HEIF (desde Android 9) y AVIF (desde Android 12) se guardan como JPEG, reducidas si pasan de unos 24 megapíxeles.
• Tachar en imagen: tapa con bloques negros lo que no quieras enseñar en una captura.
• Inspector de QR: enseña qué hay en un QR, con avisos de señales típicas de engaño, sin abrir nada. No garantiza que un enlace sea seguro.
• Laboratorio de hashes: SHA-2, SHA-3, BLAKE2b, BLAKE3 y HMAC de textos y archivos, para compararlos con la huella que publica su autor. Que coincida no dice que la huella venga de quien dice venir.
• Laboratorio clásico: César, Vigenère y la máquina Enigma, con análisis de frecuencias. Son cifrados históricos, rotos desde hace décadas: no protegen nada y sirven para aprender.
• Repartir un secreto: divide una frase o un secreto en partes (SLIP-0039) y lo recompone con las necesarias en Clavero. Las partes no son una copia de recuperación SLIP-39 para un dispositivo hardware.
• Comprobación de contraseñas filtradas.

**Aspecto**
• El aspecto «Cristal», con paneles translúcidos (desde Android 12, las barras y los diálogos difuminan el fondo de la app que tienen detrás), o «Sólido», sin transparencias. Seis paletas, una personalizada y, desde Android 12, los colores del fondo de pantalla. «Reducir transparencias» y «Alto contraste» en Ajustes.
• Modo sencillo y una guía al empezar.

**Privacidad**
Las bóvedas se quedan cifradas en tu móvil. De la app sale lo que tú exportas, guardas, copias al portapapeles o compartes, la copia automática, si la activas, a la carpeta que elijas, y las copias cifradas que hace antes de pasar la bóveda al formato nuevo y antes de importar de otra app. La única conexión a Internet es opcional: cuando pulsas «¿Filtrada?» o «Comprobar filtraciones», la app consulta el servicio Pwned Passwords de Have I Been Pwned enviando solo los 5 primeros caracteres de la huella SHA-1 de cada contraseña, nunca la contraseña. Las fotos se procesan en el móvil y la app no las envía a ningún sitio: solo sale la copia que tú compartas.

**Antes de usarla, ten en cuenta**
• Sin recuperación: si olvidas la contraseña maestra, nadie puede recuperar lo guardado. Exporta copias.
• Lo que los contactos no protegen: si alguien roba tu identidad (por ejemplo, con una copia de la bóveda y tu contraseña maestra), podrá leer lo que te enviaron antes y firmar como tú. Los metadatos son visibles para el canal que uses: quién escribe a quién, cuándo y el tamaño. Solo funciona entre usuarios de Clavero.
• Limpiar foto no cambia lo que se ve, y Tachar solo tapa lo que marcas tú. Ninguna cambia el original: comparte la copia.
• Cambiar la contraseña maestra no cambia las copias que ya existen: se siguen abriendo con la contraseña anterior.
• La copia automática con calendario solo se hace con la bóveda abierta, al abrir la app o al desbloquearla: si no la abres, no hay copia.
• Al actualizar desde una versión anterior, la bóveda pasa a un formato nuevo la primera vez que la abres con la contraseña maestra: después, las versiones anteriores de Clavero no abren la bóveda ni sus copias nuevas. Exporta antes una copia.
• Borrar algo de la bóveda o de su papelera no lo borra de las copias que ya hiciste.
• El archivo que exportas de otra app para importarlo suele estar sin cifrar, salvo si lo proteges con contraseña. Quitarlo con Clavero no borra sus copias en la nube, en la papelera ni en otras apps.
• Sin auditoría independiente: la app no ha pasado una auditoría de seguridad externa. Para fondos importantes, usa un dispositivo hardware dedicado.

Gratuita, sin anuncios ni compras. Código fuente: https://github.com/Egt17baa/Seguridad
