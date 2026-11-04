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

Cuando lleguen las exportaciones reales del dueño (paso 5 de la tarea 6, aplicado a bitwarden.json, aegis-claro.json
y 2fas-claro.2fas en la tarea 7), se añaden con su propia prueba.
