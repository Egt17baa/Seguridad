Archivos de prueba de importar (tareas 6 y 7 de la 1.4.0). Todos están hechos a mano a partir de la documentación
pública de cada formato, con datos inventados. No se ha copiado nada de ningún otro importador (ni código, ni pruebas,
ni archivos de prueba).

- csv/: las exportaciones CSV (ver csv/README.txt).
- bitwarden/bitwarden.json: exportación JSON sin cifrar («encrypted»: false, «folders» e «items»), con los tipos 1 a 5
  (acceso, nota segura, tarjeta, identidad y clave SSH), una passkey, URI de más, campos personalizados y 2FA como
  enlace, como clave sola y con un ajuste que Clavero no admite.
- aegis/aegis-claro.json: copia sin cifrar («db» es un objeto, versión 3 con grupos por uuid), con TOTP, HOTP, Steam,
  mOTP y Yandex.
- 2fas/2fas-claro.2fas: copia sin cifrar («services» y «groups»), con TOTP, HOTP y Steam.
- otpauth/lista.txt: un enlace por línea (CRLF), con líneas en blanco, una línea entre comillas (CSV de una columna),
  un HOTP, un TOTP de 10 cifras y una línea que no es un enlace.

- otpauth/aegis.txt (tarea 10, R21): con la forma de la exportación TXT de Aegis, un enlace otpauth:// por línea, datos
  al azar.

Exportaciones cifradas (tarea 10). El dueño no entrega exportaciones reales (llevan sus secretos; decisión (e)). Las
escribió una sola vez scripts/import-fixtures/gen_encrypted.py, una implementación independiente en Python hecha solo a
partir de la documentación pública que nombra su cabecera; las pruebas leen estos bytes, no ejecutan el script. Cada
archivo cifrado va con su gemelo en claro, y la contraseña es «prueba-clavero-1.4»:
- bitwarden/bitwarden-protegido-pbkdf2.json (PBKDF2-SHA256, 100 000 iteraciones) y
  bitwarden/bitwarden-protegido-argon2id.json (Argon2id, 16 MiB, 3 pasadas, 4 hilos; la sal, SHA-256 de la cadena
  «salt», según la documentación y sin confirmar con una exportación real); su gemelo, bitwarden-protegido-claro.json,
  es exactamente el texto que guarda «data».
- aegis/aegis-cifrado.json (scrypt N = 2^15, r = 8, p = 1, y AES-256-GCM) y su gemelo aegis-cifrado-claro.json.
- 2fas/2fas-cifrado.2fas (PBKDF2-HMAC-SHA256, 10 000 iteraciones, y AES-256-GCM; los grupos en claro) y su gemelo
  2fas-cifrado-claro.2fas.

Cuando lleguen exportaciones reales (1.4.2), se añaden con su propia prueba y se confirma lo que apunta la lista
«Pendiente de confirmar» de docs/superpowers/handoff/sdd-1.4.0/task-10-report.md (el informe de la tarea 10,
reconstruido por el controlador porque el original se perdió).
