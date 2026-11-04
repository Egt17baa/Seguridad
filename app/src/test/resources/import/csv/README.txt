Archivos CSV hechos a mano para las pruebas de importar (tarea 6 de la 1.4.0), a partir de la documentación pública
de cada formato: las cabeceras de exportación de los navegadores de Chromium, de Firefox y de Contraseñas de Apple.
Todos los datos son inventados. No se ha copiado nada de ningún otro importador.

- chromium.csv: name,url,username,password,note (LF, comillas solo donde hacen falta).
- firefox.csv: url,username,password,httpRealm,formActionOrigin,guid,timeCreated,timeLastUsed,timePasswordChanged (CRLF, todo entre comillas).
- apple.csv: Title,URL,Username,Password,Notes,OTPAuth (un 2FA admitido, uno de 10 dígitos y uno HOTP).

Cuando lleguen las exportaciones reales del dueño (paso 5 de la tarea 6), se añaden aquí con su propia prueba.
