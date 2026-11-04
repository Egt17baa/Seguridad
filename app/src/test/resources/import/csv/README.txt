Archivos CSV hechos a mano para las pruebas de importar (tarea 6 de la 1.4.0), a partir de la documentación pública
de cada formato: las cabeceras de exportación de los navegadores de Chromium, de Firefox y de Contraseñas de Apple.
Todos los datos son inventados. No se ha copiado nada de ningún otro importador.

- chromium.csv: name,url,username,password,note (LF, comillas solo donde hacen falta).
- firefox.csv: url,username,password,httpRealm,formActionOrigin,guid,timeCreated,timeLastUsed,timePasswordChanged (CRLF, todo entre comillas).
- apple.csv: Title,URL,Username,Password,Notes,OTPAuth (un 2FA admitido, uno de 10 dígitos y uno HOTP).

- Google Passwords.csv (tarea 10, R21): el único archivo que exporta Chrome, con las columnas de chromium.csv y datos
  al azar.
- Microsoft Edge Passwords.csv (R22): name,url,username,password, sin columna de notas (CRLF); edge-con-notas.csv, con
  name,url,username,password,note.
- bitwarden.csv (R19): la cabecera de la caja fuerte personal de Bitwarden, folder,favorite,type,name,notes,fields,
  reprompt,login_uri,login_username,login_password,login_totp (https://bitwarden.com/help/condition-bitwarden-import/),
  con 2FA como clave sola, como enlace y de Steam, una nota segura y una fila vacía; bitwarden-organizacion.csv, la de
  una organización (collections en lugar de folder y favorite). Datos al azar.

Cuando lleguen las exportaciones reales del dueño (paso 5 de la tarea 6), se añaden aquí con su propia prueba.
