# Ficha de Google Play (borrador)

Textos para *Play Console → Presencia en la tienda → Ficha principal*. Reglas: descripción exacta, sin superlativos de seguridad («segura», «auditada», «inviolable», «grado militar») ni promesas absolutas («imposible de descifrar», «a prueba de cuánticos», «100 %», «como Signal», «con validez legal»), sin la palabra «wallet», y con los avisos de «sin recuperación» y «sin auditoría independiente». El nombre «Seguridad» es provisional (ver `docs/PUBLICAR.md`, apartado 3.1).

## Descripción breve (máximo 80 caracteres)

Bóveda cifrada, 2FA, contactos y cifrado de archivos. Sin cuentas ni nube.

## Descripción completa

Seguridad guarda tus contraseñas, códigos 2FA, frases semilla y notas en una bóveda cifrada en tu móvil, y cifra textos y archivos. No tiene cuentas, nube, analítica ni anuncios, y es de código abierto (Apache-2.0).

**Bóveda**
• Accesos, frases semilla, notas y códigos 2FA, cifrados con una clave protegida por tu contraseña maestra (Argon2id) y, si quieres, por tu huella.
• Búsqueda, favoritos, etiquetas e historial de las últimas contraseñas.
• Adjuntos cifrados (hasta 5 MB cada uno).
• Salud de la bóveda: contraseñas repetidas, débiles, antiguas o sin 2FA.
• Copias cifradas que exportas tú, y copia automática opcional a una carpeta que elijas.

**Códigos 2FA**
• TOTP con escáner QR o con la clave a mano.
• Importa las cuentas exportadas desde Google Authenticator.

**Cifrado**
• Cifra y descifra textos y archivos con AES-256-GCM o ChaCha20-Poly1305 (y otros algoritmos estándar), con contraseña o con una clave aleatoria.

**Contactos**
• Tu identidad y tus contactos viven en la bóveda cifrada de tu móvil. Añade a un contacto escaneando su QR o pegando su tarjeta y comparando la huella.
• Cifra un texto o un archivo para un contacto y envíalo por el canal que quieras (WhatsApp, Telegram, correo). Solo tu contacto, con Seguridad, podrá abrirlo.
• Firma un texto o un archivo: demuestra a tus contactos que es tuyo y que no ha cambiado.
• Diseñado para resistir también a ordenadores cuánticos futuros (cifrado híbrido X-Wing, en proceso de estandarización).

**Herramientas**
• Generador de contraseñas y frases, huella SHA-256 de archivos y comprobación de contraseñas filtradas.

**Privacidad**
Todo se queda en tu móvil. La única conexión a Internet es opcional: cuando pulsas «¿Filtrada?» o «Comprobar filtraciones», la app consulta el servicio Pwned Passwords de Have I Been Pwned enviando solo los 5 primeros caracteres de la huella SHA-1 de cada contraseña, nunca la contraseña.

**Antes de usarla, ten en cuenta**
• Sin recuperación: si olvidas la contraseña maestra, nadie puede recuperar lo guardado. Exporta copias.
• Lo que los contactos no protegen: si alguien roba tu identidad (por ejemplo, con una copia de la bóveda y tu contraseña maestra), podrá leer lo que te enviaron antes y firmar como tú. Los metadatos son visibles para el canal que uses: quién escribe a quién, cuándo y el tamaño. Solo funciona entre usuarios de Seguridad.
• Sin auditoría independiente: la app no ha pasado una auditoría de seguridad externa. Para fondos importantes, usa un dispositivo hardware dedicado.

Gratuita, sin anuncios ni compras. Código fuente: https://github.com/Egt17baa/Seguridad
