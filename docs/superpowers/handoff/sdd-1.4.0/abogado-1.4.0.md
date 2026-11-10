# Dictamen del Abogado Digital: Clavero 1.4.0

- **Fecha de referencia:** 10 de noviembre de 2026.
- **Objeto:** la revisión jurídica única y final antes de publicar la 1.4.0 (`CLAUDE.md`, «Skills: siempre juntas», punto 4).
- **Sobre qué:** rama `ccr-d1e75160-p5f4wc`, cabeza `be08d65` («1.4.0: cierre de la revisión final y comienzo de la del Abogado»), diff `7c8dea7..be08d65`. El código está cerrado en `7b71d3c` (R71); lo posterior son documentos.
- **Método:** `despacho-digital`, con `privacidad-proteccion-datos`, `regulacion-digital-ue`, `propiedad-intelectual-ia` y, para la Directiva (UE) 2019/770, el análisis de `contratos-tecnologicos` del dictamen de la 1.3.1. He leído también `experto-seguridad-cripto` (`SKILL.md`), para que ningún texto afirme algo falso sobre la seguridad.
- **Solo lectura:** no he cambiado ningún archivo del repositorio salvo este dictamen, ni he hecho commit.
- **Criterio de clasificación:** **O** es obligatorio: lo exige una norma o la regla de veracidad del proyecto (un texto que lee el usuario de la app, la ficha o la política afirma algo falso sobre el código). Una inexactitud en la documentación técnica (README de desarrollo, auditoría, informe del ataque) la trato como **R**, igual que la R-4 de la 1.3.1. **I** es informativo.

**Hechos que doy por ciertos (hipótesis expresas):**
1. El desarrollador es un particular residente en España («Egt17baa», seudónimo), sin empresa ni ánimo de lucro. La app es gratuita, sin anuncios, compras ni donaciones (`docs/privacidad.html:25`; `README.md:11`), y él no recibe datos personales (`docs/privacidad.html:29`).
2. La única red es la de `api.pwnedpasswords.com`, con k-anonimato (`net/PwnedPasswords.kt:24`). La 1.4.0 no añade permisos, componentes exportados, hosts ni dependencias del APK (auditoría, §2).
3. La app se publica en Google Play en todos los países menos Francia (con ultramar), Rusia y Bielorrusia, y como APK en GitHub *Releases*.

Si alguno de estos hechos cambia (donaciones, anuncios, una versión de pago, datos que lleguen al desarrollador), hay que volver a analizar el CRA, el TRLGDCU, la Directiva 2024/2853 y la DSA.

---

## 1. Resumen y veredicto

**Veredicto: se puede publicar, con cambios.**

- **Ninguna norma exige cambios.** Con los hechos de arriba, la 1.4.0 no cambia el análisis de la 1.3.1: el RGPD y la LOPDGDD no se aplican al desarrollador (todo el tratamiento es local y doméstico), y tampoco el TRLGDCU ni la Directiva 2019/770, el CRA, la Directiva 2024/2853, la DSA, la LSSI, la EAA ni el AI Act (§4).
- **Los nombres de otras apps** se usan de forma nominativa y descriptiva, para decir qué formato se lee, sin logotipos y con una línea que niega toda relación: cabe en el art. 14.1.c) y 14.2 del Reglamento (UE) 2017/1001 (RMUE) y en el art. 37.1.c) de la Ley 17/2001 de Marcas, y es dudoso que haya siquiera «uso en el tráfico económico» (§3.5).
- **Tres cambios obligatorios por la regla de veracidad** (ninguno por una norma):
  - **O-1:** `IMPORT_REPLACE_NOTE` dice que «Reemplazar» toma del archivo el título y el usuario, y desde R58 y R64 se quedan los de la bóveda; además calla que el 2FA anterior solo queda en la copia de antes de importar (R63).
  - **O-2:** `IMPORT_TOO_MANY_ITEMS` da como motivo que «la bóveda resultante no cabría en una copia», y 20 001 elementos pequeños sí caben.
  - **O-3:** la política dice que fuera de la bóveda «solo quedan» ciertas copias «y las copias temporales que se explican aquí», y no explica la carpeta temporal de «Comprobar una copia» ni la copia de antes de importar cuando va sola a la carpeta de la copia automática.
- **Doce cambios recomendados (R-1 a R-12)**, entre ellos mi decisión sobre R30 (R-1: quitar «Google Authenticator» de la ficha), el nombre exacto del Gestor de contraseñas de Google (R-5), la frase del README sobre dónde están los nombres (R-6), las notas de la versión (R-7) y el estado del informe del ataque (R-8).
- **La fecha de la política:** cambia el texto (A6 ya en esta rama, y O-3), así que el dueño tiene que poner la fecha de `docs/privacidad.html:22`. Recomiendo que sea la del día en que se fusione en `main`, que es cuando GitHub Pages publica el texto nuevo (R-12).

## 2. Cambios

### O-1. `IMPORT_REPLACE_NOTE` (obligatorio por la regla de veracidad)

- **Dónde:** `app/src/main/java/app/seguridad/imports/ImportTexts.kt:280-283`. Se enseña en la vista previa, bajo las opciones de los repetidos (`ui/screens/ImportScreen.kt:609`).
- **Texto actual:** ««Reemplazar» toma del archivo el título, el usuario, la contraseña, la web y el 2FA que no estén vacíos, y suma sus notas y sus etiquetas (hasta 10 por entrada) a las de la bóveda. La contraseña anterior queda en el historial; los adjuntos y el favorito no cambian.»
- **Por qué es falso hoy:**
  - Un acceso repetido lo es porque su título y su usuario tienen la misma clave que los de la bóveda (`imports/MergePlan.kt:427-430`, `compareKey`; el título sin mayúsculas). `replaceWith` se queda entonces con los de la bóveda (`MergePlan.kt:316-317`, R58), y lo mismo con el emisor y la cuenta de un 2FA (`:322-326`, R64). Así, un archivo con «BANCO» frente a «Banco» deja «Banco»: el texto dice lo contrario.
  - El 2FA del archivo sustituye al de la bóveda con su clave secreta, y el anterior solo queda en la copia de antes de importar (`MergePlan.kt:279-282`, R63). El texto dice dónde queda la contraseña anterior y calla dónde queda el 2FA anterior; quien lea la nota puede creer que también se conserva. Perder un 2FA puede dejar al usuario fuera de una cuenta: es justo lo que una nota así tiene que decir. El README ya lo dice (P-9, `README.md:348`), pero el README no lo lee quien importa.
- **Texto propuesto:**

  ```kotlin
  const val IMPORT_REPLACE_NOTE =
      "«Reemplazar» toma del archivo el título, el usuario, la contraseña, la web y el 2FA que no estén vacíos, y suma sus " +
          "notas y sus etiquetas (hasta 10 por entrada) a las de la bóveda. Si el título o el usuario son los mismos " +
          "escritos de otra forma (otras mayúsculas en el título, otros espacios o algo que no se ve), se quedan los de la " +
          "bóveda. La contraseña anterior queda en el historial; el 2FA anterior, con su clave, solo queda en la copia de " +
          "antes de importar. Los adjuntos y el favorito no cambian."
  ```

- **Por qué esta redacción es verdad en todos los casos:** en un acceso o una nota repetidos, el título y el usuario siempre son «los mismos escritos de otra forma» (es la regla de duplicados), así que se quedan los de la bóveda; en un 2FA repetido, que se reconoce por su clave, se toman los del archivo si son otros, como dice la primera frase. Desde R71, un 2FA que está dentro de un acceso no ofrece «Reemplazar» (`MergePlan.kt:38-40`, `:119`), así que «el 2FA anterior» solo se refiere a uno que en efecto se cambia. La copia de antes de importar se hace siempre antes de guardar («sin esa copia no se importa nada», `IMPORT_CONFIRM_NOTE`).
- **Para el equipo técnico:** ningún test fija el literal; `ImportTextsTest.theScreenTextsPassTheForbiddenWordRules` lo pasa por `ForbiddenWords` (no lleva ninguna palabra prohibida). Conviene fijarlo con un `assertEquals`. La auditoría (§5.3, «sin textos nuevos de la interfaz») y §11.1 tendrán que decir que este texto cambia.

### O-2. `IMPORT_TOO_MANY_ITEMS` (obligatorio por la regla de veracidad)

- **Dónde:** `app/src/main/java/app/seguridad/imports/ImportTexts.kt:33` (y el comentario de `imports/ImportLimits.kt:11`).
- **Texto actual:** «El archivo tiene más de 20 000 elementos: la bóveda resultante no cabría en una copia.»
- **Por qué es falso en algunos casos:** el tope de 20 000 es una decisión de diseño (decisión (a) y R12), no una consecuencia del tamaño de la copia.
  - El límite de la copia que importa aquí es el del archivo de la bóveda, 6 MiB (`BACKUP_MAX_ENTRY_BYTES`, `vault/Backup.kt:40`); `BACKUP_MAX_ENTRIES` (2 000, `:41`) cuenta archivos de la copia (adjuntos + 2), no entradas.
  - Una entrada importada mínima (título, contraseña, fechas, un id y la etiqueta de origen) ocupa unos 171 bytes de JSON, porque `appJson` no escribe los valores por defecto (`core/Json.kt:6-10`). 20 001 entradas así son unos 3,4 MB, y la estimación de la propia app (`imports/MergePlan.kt:345-354`: ×4/3 + 4 096) da unos 4,6 MB, por debajo de 6 MiB. En una bóveda vacía, esa importación cabría.
  - Además, el tope cuenta también los elementos que no entran (R12), que no llegan a la bóveda.
  - Lo señaló ya la revisión de la tarea 6 («Hay que comprobar que es verdad: 20 001 filas mínimas podrían caber en los 6 MB de la copia», `task-6-review.md:166`) y no consta que se resolviera.
- **Texto propuesto** (con un espacio de no separación en «20 000», como propuso esa revisión, para que no se parta en dos líneas):

  ```kotlin
  const val IMPORT_TOO_MANY_ITEMS = "El archivo tiene más de 20 000 elementos, el máximo que Clavero lee de una vez."
  ```

- **Para el equipo técnico:** los tests usan la constante, no el literal (`DetectTest.kt:42`, `CsvTest.kt:228`, `BitwardenJsonTest.kt:348-351`, `OtpAuthListTest.kt:128`, `RedTeamImportTest.kt:154`). Mi cifra es una estimación hecha a mano; si el equipo quiere confirmarla, un test que fusione 20 000 elementos mínimos en una bóveda vacía y compruebe `estimatedOverBytes == 0` la fija. Si ese test diera lo contrario, el texto propuesto sigue siendo verdad. El comentario de `ImportLimits.kt:11` («since the vault would not fit in a copy») debería decir lo mismo.

### O-3. La política: la copia de antes de importar y la carpeta temporal de «Comprobar una copia» (obligatorio por la regla de veracidad)

- **Dónde:** `docs/privacidad.html:32` dice: «Fuera de la bóveda solo quedan las copias y los archivos que guardas o compartes tú, la copia automática si la activas (en la carpeta que elijas) y las copias temporales que se explican aquí.»
- **Por qué ya no es verdad:**
  - «Comprobar una copia» escribe lo que lee de la copia en `cacheDir/comprobar/` y lo borra al terminar o al arrancar (`vault/BackupCheck.kt:25`, `:76`, `:119-139`; `SeguridadApp.kt:62-63`). Es una copia temporal fuera de la bóveda, y la política no la explica: con el «solo», la frase queda incompleta.
  - La copia de antes de importar, con la copia automática activada, la escribe la app en esa carpeta sin que el usuario elija nada en ese momento (`vault/AutoBackup.kt:388-394`), y no se rota. No es «la copia automática» ni una copia «que guardas tú». Sin carpeta, la elige el usuario con el diálogo y sí está cubierta.
  - Lo que hacía «Importar copia» desde la 1.1 con sus adjuntos (`vault/Backup.kt:217-290`) termina dentro de la bóveda, así que no cambia la frase.
- **Texto propuesto:** dos párrafos nuevos, justo después de `docs/privacidad.html:36` (el de A6, que no cambia y que fija `FinalLegalReviewTest.o1ThePolicySaysOnlyTheListedCopiesAreDeleted`):

  ```html
  <p>Si usas «Importar de otra app», la app lee en el móvil el archivo que eliges con el selector de Android y no lo envía a ningún sitio. Antes de guardar hace una copia cifrada de la bóveda con tu contraseña maestra (un archivo <code>seguridad-antes-de-importar-….sgbak</code>) en la carpeta de la copia automática o, si no la tienes, donde elijas; la rotación de las copias automáticas no la borra. El archivo que exportaste de la otra app no lo crea esta app: sigue donde lo guardaste hasta que lo borres, y al terminar la app te ofrece intentarlo.</p>
  <p>«Comprobar una copia» guarda, mientras trabaja, lo que lee de la copia (sus adjuntos, cifrados como están en ella) en una carpeta temporal de su almacenamiento privado, y la borra al terminar o, si la app se cierra antes, la próxima vez que la abras.</p>
  ```

- **Comprobado:** la lectura es en memoria y por el selector, sin permisos de almacenamiento (`ui/screens/ImportScreen.kt`, sin escrituras salvo la copia de `:370`); la copia es una `seguridad-backup` cifrada con la maestra (`AutoBackup.kt:388-391`; `IMPORT_CONFIRM_NOTE`); A6 la borra si está apuntada (`:36`, ya en la política); `BackupReader` escribe solo los adjuntos, como `<id>.bin` (`vault/Backup.kt:217-224`), y el JSON de la bóveda queda en memoria.
- **Tests:** los dos párrafos pasan `PublishedTextsTest` (ninguna afirmación absoluta de `absoluteClaims`, `:30-33`, ni palabra prohibida) y no tocan las frases que fija `FinalLegalReviewTest`. Si el equipo quiere fijarlos, que añada sus hechos («antes de importar», «carpeta temporal») a `thePolicyNamesTheTemporaryCopiesTheFolderAndTheHashPrefix`.
- **Fecha:** cambia el texto de la política, así que su fecha (`:22`) también (R-12).

### R-1. La línea de la ficha con «Google Authenticator» (R30; recomendado)

- **Dónde:** `docs/ficha-google-play.md:32`. **Actual:** «• Importa las cuentas exportadas desde Google Authenticator.»
- **Decisión:** se cambia. Es verdad y es un uso nominativo lícito (§3.5), así que ninguna norma obliga. Pero:
  - la regla del dueño de la 1.4.0 (R2, R7, R31) dice «nunca en la ficha de Google Play»;
  - la guía de Google Play para la ficha pide no usar en la descripción palabras que puedan confundir sobre la relación con otra app y dejar fuera las referencias a otras apps que no sean necesarias (Play Console, «Metadata» y guía de la ficha; **redacción vigente por confirmar**); una línea sola con una marca de Google es la que más fácilmente lee un revisor de Play como reclamo;
  - el texto genérico describe lo mismo, y es verdad: el escáner de la bóveda lee `otpauth-migration://` (el formato de Google Authenticator y de la opción «Exportar a Google Authenticator» de Aegis) y los `otpauth://` de cualquier app.
- **Texto propuesto:** «• Importa las cuentas que algunas apps de 2FA exportan como códigos QR.»
- **Tests:** `ImportTextsTest.LISTING_ALLOWED` (`app/src/test/java/app/seguridad/imports/ImportTextsTest.kt:136`) tiene que quedar vacío (`emptySet()`), y la aserción de `:168` («The exception is still there») se quita o se queda, que con un conjunto vacío pasa siempre. El resto del test (`:143-170`) sigue fijando que la ficha y las «Novedades» no nombran otra app.

### R-2. La ficha, «Privacidad»: la copia de antes de importar (recomendado)

- **Dónde:** `docs/ficha-google-play.md:54`. **Actual:** «De la app sale lo que tú exportas, guardas, copias al portapapeles o compartes, y la copia automática, si la activas, a la carpeta que elijas.»
- **Propuesto:** «De la app sale lo que tú exportas, guardas, copias al portapapeles o compartes, la copia automática, si la activas, a la carpeta que elijas, y la copia cifrada que hace antes de importar de otra app.»
- **Motivo:** el mismo de O-3. No lo hago obligatorio porque la frase no dice «solo» y la copia va a la misma carpeta que la automática o donde elige el usuario. Conserva «carpeta que elijas» y «copias al portapapeles», que fija `PublishedTextsTest.kt:80`.

### R-3. La política, «Tus derechos» (recomendado)

- **Dónde:** `docs/privacidad.html:63`. **Actual:** «… (las copias <code>.sgbak</code>, también las automáticas, las copias de fotos y las imágenes tachadas, y los archivos cifrados): están donde los guardaste, y los borras tú.»
- **Propuesto:** «… (las copias <code>.sgbak</code>, también las automáticas y las de antes de importar, las copias de fotos y las imágenes tachadas, y los archivos cifrados): están donde los guardaste, y los borras tú. Tampoco borra los archivos que hayas exportado de otras apps para importarlos.»
- **Motivo:** hoy es verdad («.sgbak» las cubre), pero quien desinstala para borrarlo todo tiene que saber que esos dos tipos de archivo siguen ahí, y el exportado suele estar sin cifrar.

### R-4. La política: los nombres de otras apps de antes de la 1.4.0 (mismo trato que R30; recomendado)

- `docs/privacidad.html:51`: «(de 2FA, de exportación de Google Authenticator, de la tarjeta de un contacto, …)» → «(de 2FA, de exportación de otra app de 2FA, de la tarjeta de un contacto, …)».
- `docs/privacidad.html:35`: «Si esa carpeta está sincronizada con un servicio en la nube (por ejemplo, Google Drive), ese servicio…» → «Si esa carpeta está sincronizada con un servicio en la nube, ese servicio…».
- **Motivo:** la regla R7 dice que en lo publicado los nombres solo van en el README y en las notas de GitHub, y la política es un texto publicado. Los dos usos son lícitos y verdaderos; es solo coherencia. Los nombres de Have I Been Pwned, Superlative Enterprises y Cloudflare (`:45-46`) **se quedan**: son quienes reciben la consulta, y nombrarlos es transparencia, no reclamo.

### R-5. «Google Contraseñas» no es el nombre del producto (recomendado)

- **Dónde:** `imports/ImportTexts.kt:212`, `README.md:27` y `:340` (P-1), y la nota de GitHub del traspaso (`2026-11-08-clavero-1.4.0-traspaso.md:68`).
- **Motivo:** en español el producto se llama «Gestor de contraseñas de Google» (así sale en Chrome y en Android: Ajustes → Gestor de contraseñas de Google → Exportar contraseñas). El uso nominativo tiene que identificar bien el producto ajeno; un nombre que no existe no crea confusión, pero no es exacto.
- **Propuesto:**
  - `IMPORT_FORMATS`: «Lee las exportaciones en CSV de Chrome, Edge, Brave, el Gestor de contraseñas de Google, Firefox, Apple y Bitwarden; …» (el resto igual).
  - `README.md:27`: «… Brave y el Gestor de contraseñas de Google (`name,url,username,password,note`) …».
  - P-1 (`README.md:340`): «… de Brave, del Gestor de contraseñas de Google (`Google Passwords.csv`), …».
  - Nota de GitHub: «Lee el CSV de Chrome, Edge, Brave, el Gestor de contraseñas de Google, Firefox, Apple y Bitwarden; …».
- **Tests:** `ImportTextsTest.theFormatsTextSaysWhatIsReadAndWhatIsNot` (`:285-289`) no comprueba esa parte, y `theScreenNamesNoOtherProductOutsideImportTexts` sigue contando al menos 5 nombres.

### R-6. README: dónde están los nombres de otras apps (recomendado, exactitud)

- **Dónde:** `README.md:175`, la frase «Los nombres de otras apps solo están en la pantalla de importar (`imports/ImportTexts.kt`) y en este README, para decir qué formato se lee, sin logotipos y con la línea «Los nombres de otras apps son marcas de sus titulares. Clavero no tiene relación con ellos.».»
- **Por qué no es exacta:** también están las etiquetas de origen que se ven en la bóveda (`importado-bitwarden`, `importado-firefox`…, `ImportTexts.kt:12-18`), los tres textos de Google Authenticator de la 1.1 (`ui/screens/VaultEntryScreen.kt:250`, `:311`, `:537`), «la copia de Google» (`tools/ToolTexts.kt:648`), las notas de la versión de GitHub (R31) y, mientras no se apliquen R-1 y R-4, la ficha y la política.
- **Propuesto:** «En la app, los nombres de las apps cuyos formatos se leen están en los textos de la pantalla de importar (`imports/ImportTexts.kt`), con la línea «Los nombres de otras apps son marcas de sus titulares. Clavero no tiene relación con ellos.», y en las etiquetas de origen que se ponen a lo importado (`importado-bitwarden`…). Fuera de ahí solo quedan los textos de antes de la 1.4.0 que `ImportTextsTest` permite uno a uno (los de Google Authenticator y «la copia de Google») y las direcciones de las licencias. En lo publicado, están en este README y en las notas de la versión de GitHub, para decir qué formato se lee, sin logotipos y con esa misma línea.»

### R-7. Las notas de la versión de GitHub (recomendado)

- **Dónde:** el borrador del traspaso (`docs/superpowers/handoff/2026-11-08-clavero-1.4.0-traspaso.md:63-75`).
- **Cambios:**
  1. `:72` cita el botón a medias. Propuesto: «- Contraseña maestra: al cambiarla, «Hacer una copia nueva y borrar las anteriores (automáticas y de antes de importar)» borra también las copias de antes de importar que la app tiene apuntadas en la carpeta de la copia automática.» (el literal es `PASSWORD_CHANGE_REPLACE_COPIES`, `tools/ToolTexts.kt:507`).
  2. En la viñeta de «Importar de otra app» (`:68`), detrás de «… sin esa copia no importa nada.», añadir: «Con un elemento repetido, «Reemplazar» cambia el 2FA de la bóveda por el del archivo; el anterior solo queda en esa copia.» (R63; es lo que más puede sorprender a quien actualiza).
  3. R-5 en la lista de formatos.
- **Lo demás está bien:** nombra otras apps solo para decir qué formato se lee y con la línea de marcas debajo (R31); dice qué no borra «quitar el archivo»; dice que los cifrados clásicos no protegen nada y que las partes no son una copia de cartera; «No añade permisos ni conexiones» es verdad.
- **«Novedades» de Play** (`:79-81`): bien, sin nombres, sin palabras prohibidas y con 374 caracteres (menos de 500). Sin cambios.

### R-8. El estado del informe del ataque (recomendado, exactitud)

- **Dónde:** `docs/auditorias/2026-11-08-ataque-1.4.0.md:13`. **Actual:** «> La decisión (d) no se cierra hasta que el agente compruebe estos últimos arreglos.»
- **Por qué:** el cuadro de estado se quedó en la quinta comprobación. Después vinieron la sexta (RT-18 a RT-20, `:352-360`) y la corrección final, y el controlador cerró el segundo ataque (R65, `progress.md`). La auditoría dice lo mismo (§5.3). Un documento publicado no debe decir dos cosas.
- **Propuesto** (en lugar de la línea 13):

  ```markdown
  > - **Sexta comprobación y corrección final:** RT-18, RT-19 y RT-20 (Informativos), **arreglados** (decisiones R61 a R64). La corrección final no tuvo otra comprobación del equipo rojo: la revisó el `code-review` de la rama, y el controlador cerró el segundo ataque y sus comprobaciones (R65, 10-11-2026).
  ```

### R-9. La auditoría, §2: «Solo primitivas…» (recomendado, exactitud)

- **Dónde:** `docs/auditorias/2026-11-08-auditoria-1.4.0.md:84`. **Actual:** «Solo primitivas de Bouncy Castle y `javax.crypto`.»
- **Propuesto:** «Solo primitivas de Bouncy Castle y `javax.crypto`, salvo la aritmética de SLIP-0039 (Shamir sobre GF(256), RS1024 y la red Feistel sobre el PBKDF2 de Bouncy Castle), escrita desde su especificación y probada con sus vectores oficiales (§4).»
- **Motivo:** la misma auditoría lo dice así en `:130` y `:315`, y el informe del ataque en `:161`. El resumen no debe decir menos.

### R-10. `BACKUP_CHECK_STOPS_NOTE` (recomendado)

- **Dónde:** `app/src/main/java/app/seguridad/vault/BackupCheckTexts.kt:75-77`. **Actual:** «… mientras se comprueba, la comprobación se para y no se muestra su resultado.»
- **Propuesto:** «… mientras se comprueba, la comprobación se detiene en cuanto puede y no se muestra su resultado.»
- **Motivo:** una derivación Argon2id ya empezada termina por dentro y retiene el cerrojo (`BackupCheck.kt:56-60`, R38). Lo propuso la revisión de la tarea 5. No es falso en lo que ve el usuario (el resultado no sale nunca), por eso no es O. Ningún test fija el literal entero.

### R-11. Dominios reales en las pruebas del navegador (recomendado)

- **Dónde:** P-27 (`README.md:366`) y su fila de `.maestro/README.md`.
- **Motivo:** P-27 pide pegar en la barra de Chrome y del navegador del fabricante URL como `ht<TAB>tp:@banco.es` y «apuntar qué abre cada uno». `banco.es` es un dominio real de un tercero: la prueba haría peticiones a su servidor. Con un dominio reservado (`banco.example`, RFC 2606 y RFC 6761) la barra enseña igual lo que ha leído, sin conectar con nadie. P-21 ya usa `evil.example` y el navegador va a ese host, que no existe.
- **Propuesto:** cambiar `banco.es` por `banco.example` en P-27 y en su fila de `.maestro/README.md`, también en «Clavero compara esas URL como `http://banco.example`» y «su fila de repetido dice «http://banco.example»». Si los tests del equipo rojo usan `banco.es` para lo mismo, pueden seguir: no conectan con nada.

### R-12. La fecha de la política (recomendado)

- `docs/privacidad.html:22` dice «Última actualización: 2 de noviembre de 2026». La 1.4.0 cambia la frase de A6 (`:36`) y, con O-3, añade dos párrafos.
- La propia política promete: «Si esta política cambia, se publicará aquí con su nueva fecha» (`:66`). GitHub Pages la publica al fusionar en `main`, y el traspaso prevé poner la fecha más tarde, el día de publicar la app (§3, paso 3). Entre medias, el texto nuevo saldría con la fecha antigua.
- **Recomendación:** poner en el mismo PR a `main` la fecha del día en que se fusione. Si el dueño prefiere la del día de publicar la app, que el PR de la fecha se fusione ese día y se acepte el desfase. Los dos párrafos de O-3 describen funciones que la 1.3.1 no tiene: no son falsos para ella (no hace nada de eso), así que publicar la política antes que la app no es problema.
- `FinalLegalReviewTest.thePolicyDateIsReadInEveryFormTheOwnerMayWrite` (`:60-64`) lee la fecha en la forma «D de <mes> de AAAA.»: hay que escribirla así.

### I-1 a I-10. Informativos (sin cambio)

- **I-1. Etiquetas de origen con marcas** (`importado-bitwarden`, `importado-firefox`, `importado-apple`, `importado-aegis`, `importado-2fas`; `ImportTexts.kt:12-18`). Se ven en la bóveda, fuera de la pantalla de importar. Jurídicamente es un uso descriptivo del origen de los datos del propio usuario, en su bóveda privada, sin ninguna presentación comercial: cabe sin problema en el art. 14.1.c) RMUE. Encaja con R2 (el literal está en `ImportTexts.kt`). No hace falta cambiarlas; la frase del README queda exacta con R-6.
- **I-2. `IMPORT_GOOGLE_AUTHENTICATOR`** (`ImportTexts.kt:103-107`) afirma que Google Authenticator «en Android no deja hacer captura de esa pantalla». Es una afirmación sobre una app ajena, con sus fuentes en el KDoc (`:88-102`, comprobadas el 8-11-2026). Es verdad hoy; si esa app cambia, el texto dejaría de serlo. Conviene repasarlo en cada versión. No hay riesgo de denigración (art. 9 de la Ley 3/1991): es un hecho neutro y no hay actuación en el mercado.
- **I-3. `IMPORT_NOT_PROTECTED` y los grupos de 2FAS en claro** (`task-12-report.md`, «Puesta al día», punto 3). El texto dice «sus entradas siguen cifradas con esa contraseña» (`ImportTexts.kt:220`), y eso es verdad: lo que queda en claro en una copia cifrada de 2FAS son los nombres de los grupos, no las entradas. No hace falta decirlo. Si el dueño quiere más detalle, el README es el sitio.
- **I-4. La línea de marcas** (`IMPORT_TRADEMARKS`, `ImportTexts.kt:216`, en todos los pasos de la pantalla: `ImportScreen.kt:558`; README `:33` y bajo P-26; informe del ataque `:20`; notas de GitHub). Basta: niega toda relación, que es la condición principal de las «prácticas leales» del art. 14.2 RMUE. «Clavero no tiene relación con ellos» se refiere a los titulares, y gramaticalmente es correcto. La fija `ImportTextsTest.theTrademarkLineIsVerbatimAndOnTheScreen`.
- **I-5. SLIP-0039, comprobado hoy en línea:** `app/src/main/assets/slip39_english.txt` y `app/src/test/resources/vectors/slip39.json` son idénticos a `shamir_mnemonic/wordlist.txt` y `vectors.json` de github.com/trezor/python-shamir-mnemonic (SHA-256 `bcc45553…601eec3` y `13ebeceb…cdabbdc4`), y su `LICENSE` empieza por «Copyright 2019 SatoshiLabs» y el texto MIT, que es el que reproduce `assets/licenses/slip39.txt`. La condición de la MIT (conservar el aviso) se cumple en la app (`LicensesScreen.kt:38`), en `NOTICE:14-16` y en el README (`:173`). «Sin cambios» es verdad. Implementar la especificación SLIP-0039 no necesita licencia: se protege la expresión, no el método (art. 96.4 TRLPI; TJUE C-406/10, *SAS Institute*). La URL `github.com/trezor/…` de Licencias nombra una marca, pero es atribución, como la de Guava (R8a); si se quiere, se añade «trezor» a `names` de `ImportTextsTest` y ese literal a `ALLOWED`.
- **I-6. El ejemplo de User-Agent** «Clavero/1.3.1» (`privacidad.html:46`) sigue siendo un ejemplo verdadero de la forma general que propuso la R-2 de la 1.3.1. Si se toca la política por O-3, puede pasar a «Clavero/1.4.0», sin obligación.
- **I-7. `banco.es` en los documentos publicados** (README, informe del ataque y `.maestro/README.md`): hace siempre de sitio legítimo, nunca de atacante (que es `evil.example`), así que el riesgo jurídico es mínimo. Para lo que se escriba en adelante, mejor dominios reservados (`.example`). Lo que sí conecta con ese dominio lo trata R-11.
- **I-8. README `:175`, «licencias GPL o AGPL, que no son compatibles con la Apache-2.0 de Clavero»:** es correcto en la dirección que importa (no se puede meter código GPL o AGPL en una obra que se distribuye con Apache-2.0 sin pasar la obra entera a esa licencia; al revés, el código Apache-2.0 sí entra en una obra GPL-3.0). Si se quiere precisar: «…con licencias GPL o AGPL, cuyo código no puede entrar en una obra que se distribuye con Apache-2.0, como Clavero».
- **I-9. «Menores de 13 años»** (`privacidad.html:60`): en España la edad del consentimiento es 14 (art. 7 LOPDGDD), pero aquí no hay tratamiento del desarrollador ni consentimiento que recabar. Sin cambio, como en la 1.3.x.
- **I-10. «Cartera física»** (`HASH_LAB_INTRO`, `tools/hashlab/HashLabTexts.kt:7`) frente a «cartera de hardware» (`SHARE_WARNING` y la ficha). Las dos son claras y ninguna dice «wallet». Unificarlas es opcional.

## 3. Lo que he revisado y está bien

### 3.1 Textos de la interfaz

- **`imports/ImportTexts.kt`, leído entero.** Salvo O-1 y O-2, todo es exacto:
  - los topes que se citan coinciden con el código: 16 MB (`IMPORT_MAX_BYTES`), 64 columnas (`CSV_MAX_COLUMNS`), 64 niveles (`JSON_MAX_DEPTH`, `ImportJson.kt:22`), 8 MB de parte cifrada (`IMPORT_MAX_ENCRYPTED_CHARS`, `ImportKdf.kt:56`), 100 etiquetas nuevas (`IMPORT_MAX_NEW_TAGS`, `MergePlan.kt:146`) y 10 por entrada (`MAX_TAGS`, `vault/EntryQuery.kt:8`);
  - **R33:** «sin cifrar» va solo para lo exportado sin contraseña (`IMPORT_NOT_PROTECTED`, `IMPORT_REMOVE_EXPLAIN`, fijados en `ImportTextsTest.kt:244-256`); «no garantiza que el almacenamiento lo borre físicamente» es la única forma permitida de «garantiza»;
  - «puede intentar borrar… algunas apps no lo dejan» es verdad (`ImportScreen.kt:438-447`, `FLAG_SUPPORTS_DELETE`);
  - los errores son textos fijos, nunca un valor del archivo ni el texto de una excepción;
  - `IMPORT_CONFIRM_NOTE`, `IMPORT_COPY_*`, `IMPORT_SAVE_FAILED` e `IMPORT_WIPED_WHILE_SAVING` dicen solo lo que es verdad en todos los caminos (R17, R37, R66);
  - `importCopyWhereText`: la copia hecha con el diálogo no la borra A6, y lo dice.
- **`vault/BackupCheckTexts.kt`:** R6 se cumple: `BACKUP_CHECK_INTRO` dice «No cambia tu bóveda ni tus copias» y ya no dice «tus ajustes» (`store.touch()` solo mueve la marca del reloj, `vault/VaultStore.kt:564-567`). `BACKUP_CHECK_NOT_PROVEN` dice lo que no se prueba. Solo R-10.
- **`tools/hashlab/HashLabTexts.kt`:** «Lo que no protege» dice el límite real (la huella no autentica el origen). Ya no dice «el archivo es el original» ni «no uses este archivo», como el texto de la 1.3.x. «No se guardan en el móvil» es verdad con el teclado sin sugerencias (revisión de la rama, B I-1).
- **`tools/classic/ClassicTexts.kt`:** «rotos desde hace décadas: no protegen nada» es exacto y es la advertencia que hace falta. Los nombres alemanes de las piezas son términos técnicos, no marcas.
- **`tools/slip39/SecretShareTexts.kt`:** `SHARE_WARNING` dice «cartera», no nombra marcas y es verdad (las partes reparten la entropía; una cartera SLIP-39 tomaría ese valor como semilla). «Recomponlas» está bien escrito (palabra llana acabada en «s», sin tilde). `SHARE_NOT_PROTECTED` y `SHARE_RESULT_NOTE` avisan de que una contraseña equivocada da otro secreto sin error. Mezclar «SLIP-39» y «SLIP-0039» es aceptable (R3a).
- **`tools/ToolTexts.kt` y `vault/AutoBackup.kt` (R15):** `PASSWORD_CHANGE_REPLACE_COPIES`, `AUTO_BACKUP_REPLACED` (fijado en `FinalLegalReviewTest.kt:82-90`), `AUTO_BACKUP_REPLACE_DROPPED` y `autoBackupUnlistedText` dicen exactamente lo que borra A6, y la pista de Ajustes nombra los dos prefijos.
- **Literales de `ui/screens/*` y `vault/*`:** las pantallas nuevas no tienen literales propios (todo sale de los archivos de textos); el formulario dice «Nombre (p. ej. «Correo» o «Cartera»)» (R35, `VaultEntryScreen.kt:407`). La nota de Bouncy Castle de Licencias (`LicensesScreen.kt:35`) nombra lo que la app usa de esa biblioteca (lo comprobé por los `import` de `imports/` y `tools/hashlab/`).
- **Palabras prohibidas:** pasé el criterio de `ForbiddenWords` (más «como Signal», «a prueba de cuánticos» y «wallet») por los archivos de textos, `ui/screens/`, `vault/`, el README, la política, la ficha, `NOTICE`, el traspaso, la auditoría, el informe del ataque y `.maestro/README.md`: solo aparecen en comentarios de código y en la cabecera de reglas de la ficha (`docs/ficha-google-play.md:3-5`), que no se publica.

### 3.2 Política de privacidad (`docs/privacidad.html`, leída entera)

- «Qué datos recogemos: Ninguno» (`:29`) sigue siendo verdad: importar, comprobar una copia y las tres herramientas trabajan en el móvil, sin red (los únicos archivos con red siguen en `net/`).
- La frase de A6 (`:36`, R15) es exacta y la fija `FinalLegalReviewTest.kt:66-80`.
- Permisos (`:49-53`): sin cambios en el manifiesto (auditoría, §2; `check-manifest`).
- «No ha pasado una auditoría de seguridad independiente» (`:57`) sigue siendo verdad: las revisiones y el ataque de la 1.4.0 son internos.
- Lo que falta es O-3, y R-3, R-4 y R-12.

### 3.3 Ficha de Google Play (`docs/ficha-google-play.md`)

- Ningún nombre de otra app salvo la línea de R30 (R-1). Las líneas nuevas son verdad:
  - «Comprueba una copia sin importarla…» no dice que no toque nada (R6);
  - «Importa los archivos CSV de los navegadores más usados y las exportaciones de algunos gestores…» es una descripción razonable (Chrome, Edge, Brave, Firefox y el de Apple); «también las protegidas con contraseña» se refiere a «algunos», y la exportación «Cuenta restringida» de Bitwarden tiene su texto;
  - las tres herramientas dicen lo que no hacen;
  - el aviso nuevo del archivo exportado dice «suele estar sin cifrar, salvo si lo proteges con contraseña» (R33).
- **«No recoge ni comparte datos»** sigue valiendo: la 1.4.0 no envía nada fuera del móvil. La copia de antes de importar va, como la automática desde la 1.1, a una carpeta que elige el usuario con el selector del sistema, cifrada con su maestra; no es una recogida del desarrollador. El desarrollador «Egt17baa», «no comerciante» y los países no cambian.

### 3.4 README, `NOTICE` y Licencias

- La sección de la 1.4.0 (`README.md:19-38`, `:47-54`, `:66`, `:173-177`) y P-1 a P-27 (`:338-368`) dicen la verdad sobre el código, salvo R-5, R-6 y R-11. P-9 ya dice R58 y R63. La línea de marcas está en `:33` y bajo P-26 (`:368`), y P-21 a P-27 nombran productos solo para decir con qué se prueba.
- «Los lectores están escritos desde la documentación pública … no hay código, tests ni archivos de prueba copiados» (`:37`, `:175`): es la afirmación que sostiene la compatibilidad con Apache-2.0. No la he podido comprobar línea a línea contra el código de Bitwarden, Aegis y 2FAS (no está en el repositorio), pero los archivos de prueba son sintéticos (`app/src/test/resources/import/README.txt`) y el generador independiente nombra su documentación.
- **Herramientas de compilación** (`:177`): EPL-2.0 y las partes GPL-CE/LGPL de R8 solo se usan para compilar; la regla del proyecto («ninguna dependencia copyleft») se refiere a lo que se distribuye, y `:app:checkLicense` mira `releaseRuntimeClasspath`. Conforme.
- `NOTICE` cumple el §4.d de la Apache-2.0 y conserva los avisos MIT y CC BY. La EFF y BIP-39 no cambian.

### 3.5 Marcas, nombres de otras apps e interoperabilidad

1. **¿Hay uso «en el tráfico económico»?** El art. 9.2 RMUE y el art. 34.2 LM solo protegen frente al uso en el tráfico económico, que el TJUE entiende como el que se hace en una actividad comercial con ánimo de obtener una ventaja económica, no en la esfera privada (C-206/01, *Arsenal*, apartado 40, cita por confirmar). Un particular que publica gratis, sin anuncios ni donaciones, queda probablemente fuera. No me apoyo solo en esto: Google Play es un mercado, y un tribunal podría verlo de otro modo.
2. **Aunque lo hubiera, el uso está permitido.** El art. 14.1.c) RMUE (en su redacción del Reglamento (UE) 2015/2424) y el art. 37.1.c) LM (redacción del RDL 23/2018) permiten usar la marca ajena para identificar o referirse a los productos de su titular, en particular cuando hace falta para indicar el destino de un producto o servicio, siempre que sea conforme a las prácticas leales (art. 14.2 RMUE; art. 37.2 LM). La jurisprudencia (C-63/97, *BMW*; C-228/03, *Gillette*) pide que no dé a entender una relación comercial, que no se aproveche indebidamente del renombre, que no desacredite la marca y que no presente el producto como imitación. Aquí:
   - los nombres se usan para decir qué archivo se lee, que es el «destino» del lector;
   - sin logotipos, en texto plano y con la línea que niega toda relación;
   - no se describen esas apps de forma negativa (los textos sobre lo que no se lee son instrucciones neutras);
   - la ficha de Play, donde el uso se parecería más a un reclamo, no los nombra (con R-1, ninguno).
3. **Etiquetas de la otra app citadas** («Protegida con contraseña», «Cuenta restringida», «.zip (with attachments)», «Exportar a Google Authenticator»): son rótulos funcionales de pocas palabras, sin la originalidad que exige la protección (C-5/08, *Infopaq*), citados para que el usuario encuentre la opción.
4. **Formatos e interoperabilidad:** los formatos de archivo y la funcionalidad no son objeto del derecho de autor sobre el programa (Directiva 2009/24/CE, art. 1.2 y considerando 11; art. 96.4 TRLPI; C-406/10, *SAS Institute*). Leer un formato documentado no necesita licencia.
5. **Fuera de la UE:** en EE. UU., la doctrina del *nominative fair use* (*New Kids on the Block v. News America Publishing*, 9.º Circuito, 1992) lleva a la misma conclusión con los mismos requisitos; en el Reino Unido, el art. 11(2)(c) de la Trade Marks Act 1994. Si se quiere certeza en esos países, que la confirme un abogado local.
6. **La marca propia** «Clavero» sigue sin buscar ni registrar (pendiente del dueño, `CLAUDE.md`, «Pendientes», 7).

### 3.6 Auditoría e informe del ataque (documentos publicados)

- **Auditoría** (`docs/auditorias/2026-11-08-auditoria-1.4.0.md`): dice desde el principio que no es una auditoría independiente (`:76`), no presenta nada como invulnerable, separa lo arreglado de lo aceptado (§6) y lo que falta probar en el móvil (§10). Solo R-9, y que §11.1 recoja este dictamen.
- **Informe del ataque** (`docs/auditorias/2026-11-08-ataque-1.4.0.md`): describe ataques contra la propia app, con entradas dentro del árbol de tests y sin nada contra sistemas de terceros (`:37-39`). Las severidades vienen con su CVSS y su MASVS, sin exagerar ni rebajar. Lleva la línea de marcas (`:20`). Solo R-8 (y R-11 para la prueba que conecta).
- Ninguno de los dos afirma que la app sea segura, ni usa «garantiza» salvo en «no garantiza».

## 4. Ley aplicable

**a) Mapa normativo** (fecha de referencia 10-11-2026; las fuentes están al final):

| Norma | ¿Se aplica? | Motivo | ¿Cambia con la 1.4.0? |
|---|---|---|---|
| **RGPD** (Reglamento (UE) 2016/679) y **LOPDGDD** (LO 3/2018) | No, al desarrollador. | No recibe datos (`privacidad.html:29`). Lo que el usuario hace en su móvil, también importar los datos de sus cuentas (y los de terceros que tenga, como una identidad de Bitwarden), es actividad doméstica (art. 2.2.c RGPD; C-101/01, *Lindqvist*; C-212/13, *Ryneš*). Riesgo residual bajo por analogía con *Fashion ID* (C-40/17), como en la 1.3.x. | No. Importar y comprobar una copia son locales y no envían nada. La política sigue dando, por transparencia, la información de los arts. 13 y 14 (O-3). |
| **LSSI** (Ley 34/2002) | No. | Anexo, letra a): un servicio no remunerado solo cuenta si es actividad económica del prestador, y no lo es. | No. |
| **TRLGDCU** (RDL 1/2007) y **Directiva (UE) 2019/770** | No. | Sin precio ni datos como contraprestación (art. 3.1 de la Directiva); excluido el software libre gratuito (art. 3.5.f); el particular no es «empresario» (art. 4 TRLGDCU). Análisis completo en el dictamen de la 1.3.1, §3.7.b. | No. |
| **CRA** (Reglamento (UE) 2024/2847) | No, mientras no se monetice. | Solo cubre lo comercializado en el curso de una actividad comercial; el considerando 18 deja fuera el software libre que su fabricante no monetiza, y «nunca donaciones» despeja la duda de la guía de la Comisión de julio de 2026 (referencia exacta por confirmar). Calendario (art. 71.2): notificación de vulnerabilidades e incidentes (art. 14) desde el 11-9-2026, ya en vigor; aplicación plena el 11-12-2027. | No. |
| **Directiva (UE) 2024/2853** (productos defectuosos) | No. | Su art. 2.2 excluye el software libre desarrollado o suministrado fuera de una actividad comercial. Plazo de trasposición: 9-12-2026; se aplica a los productos introducidos en el mercado desde esa fecha. **No he encontrado un texto de trasposición español publicado** (solo noticias de que el Ministerio de Justicia la prepara para 2026): **pendiente de verificar a fecha de hoy**, en el BOE y en la audiencia pública del Ministerio. Hasta entonces rige el Libro III del TRLGDCU, que tampoco alcanza a un particular sin actividad. | No. Las versiones posteriores al 9-12-2026 se seguirían sin aplicar mientras no haya actividad comercial. |
| **AI Act** (Reglamento (UE) 2024/1689) | No. | Nada de lo nuevo es un sistema de IA (art. 3.1; considerando 12): los lectores, la regla de duplicados y el análisis de frecuencias son reglas deterministas escritas por personas. | No. |
| **DSA** (Reglamento (UE) 2022/2065) | No, como prestador. | No presta servicios intermediarios. La declaración «no comerciante» en Play sigue igual. | No. |
| **Accesibilidad** (Directiva (UE) 2019/882 y Ley 11/2023) | No. | La app no es ninguno de los productos ni servicios cubiertos. Se sigue la prueba con TalkBack de forma voluntaria (P-13, P-17). | No. |
| **Data Act** (Reglamento (UE) 2023/2854) | No. | No es un producto conectado ni un servicio de tratamiento de datos. | No. |
| **Marcas** (RMUE y LM) | Sí, como límite que se respeta. | §3.5: uso nominativo con prácticas leales (art. 14.1.c y 14.2 RMUE; art. 37.1.c y 37.2 LM). | Sí: es lo nuevo de la 1.4.0. Conforme, con R-1 y R-4 para la coherencia con R7. |
| **Derecho de autor** (TRLPI; Directiva 2009/24/CE) | Sí, como límite que se respeta. | Lectores escritos desde la documentación (§3.5.4); listas de palabras con su licencia (I-5). | Sí: SLIP-0039. Conforme. |
| **Control de exportaciones** (Reglamento (UE) 2021/821; EAR §734.3(b)(3)) | Sin cambios respecto a la 1.3.x. | Añade funciones hash, scrypt y reparto de secretos, no una categoría nueva de cifrado; el código sigue siendo público. Los «pendiente de verificar» de la 1.3.0 siguen en `docs/PUBLICAR.md`, §3.7. | No. |
| **Francia** (Ley 2004-575, art. 30), **Rusia y Bielorrusia** | Fuera de la publicación. | Decisión del dueño. | No. |
| **«Digital Omnibus»** (propuesta de la Comisión, noviembre de 2025) | — | No he encontrado su adopción: el Consejo fijó su posición y quitó varios de los cambios del RGPD; el CEPD y el SEPD emitieron un dictamen conjunto el 11-2-2026. **Pendiente de verificar a fecha de hoy.** La 1.4.0 no depende de él. | — |

**b) Las políticas de Google Play** no son normas, pero obligan como contrato de distribución. La de metadatos pide no dar a entender una relación con otra app que no existe y no meter referencias a otras apps que no sean necesarias; la de suplantación prohíbe hacerse pasar por otra app o marca. La 1.4.0 las cumple con R-1 (ninguna marca en la ficha) y sin logotipos ni nombres en el icono o en el título. **Redacción vigente por confirmar** en Play Console antes de enviar.

## 5. Lo que no se ha podido valorar, y por qué

1. **Fuentes oficiales:** no he consultado EUR-Lex ni el BOE directamente en esta sesión; me apoyo en el análisis de la 1.3.1 (que sí usó el BOE consolidado del TRLGDCU) y en fuentes secundarias para lo nuevo. Siguen **pendientes de verificar**: la trasposición española de la Directiva 2024/2853, el estado del «Digital Omnibus», la guía de la Comisión sobre el CRA y la redacción vigente de las políticas de Play.
2. **Las citas de jurisprudencia** de §3.5 (*Arsenal*, *BMW*, *Gillette*, *Infopaq*, *SAS Institute*, *New Kids*) son de memoria: el criterio es el consolidado, pero los apartados concretos están **por confirmar** antes de citarlos en un escrito.
3. **Que no se ha copiado código** de Bitwarden, Aegis ni 2FAS: me baso en lo que dicen el README, los informes y la cabecera del generador; no he comparado con el código de esas apps.
4. **Las exportaciones reales** (decisión (e)): los textos sobre lo que lee cada formato se apoyan en archivos sintéticos. Si una exportación real no entra, el texto de `IMPORT_FORMATS` podría prometer de más; lo confirma el dueño después de publicar (P-1 a P-3).
5. **Cómo se ven los textos** (largo de O-1 en un móvil, TalkBack): es de la prueba del dueño (P-13).

## 6. Tabla de cambios

| Id | Dónde | Cambio | Test que lo fija |
|---|---|---|---|
| **O-1** | `imports/ImportTexts.kt:280-283` | `IMPORT_REPLACE_NOTE`: el título y el usuario se quedan los de la bóveda si son los mismos escritos de otra forma; el 2FA anterior solo queda en la copia de antes de importar | Ninguno literal (`ImportTextsTest` pasa `ForbiddenWords`); conviene fijarlo |
| **O-2** | `imports/ImportTexts.kt:33` (y `ImportLimits.kt:11`) | `IMPORT_TOO_MANY_ITEMS`: «…más de 20 000 elementos, el máximo que Clavero lee de una vez.» | Los tests usan la constante |
| **O-3** | `docs/privacidad.html`, tras `:36` | Dos párrafos: importar de otra app y su copia previa; la carpeta temporal de «Comprobar una copia» | `PublishedTextsTest`, `FinalLegalReviewTest` (no tocan esas frases) |
| R-1 | `docs/ficha-google-play.md:32` | «• Importa las cuentas que algunas apps de 2FA exportan como códigos QR.» (decisión de R30) | `ImportTextsTest.LISTING_ALLOWED` pasa a vacío |
| R-2 | `docs/ficha-google-play.md:54` | Añadir «y la copia cifrada que hace antes de importar de otra app» | `PublishedTextsTest.kt:80` sigue pasando |
| R-3 | `docs/privacidad.html:63` | «Tus derechos»: las de antes de importar y los archivos exportados de otras apps | — |
| R-4 | `docs/privacidad.html:35`, `:51` | Sin «Google Drive» ni «Google Authenticator» | — |
| R-5 | `ImportTexts.kt:212`, `README.md:27`, `:340`, nota de GitHub | «el Gestor de contraseñas de Google» en lugar de «Google Contraseñas» | — |
| R-6 | `README.md:175` | Dónde están en realidad los nombres de otras apps | — |
| R-7 | Traspaso, `:68` y `:72` | Notas de GitHub: el botón entero de A6, R63 y R-5 | `ImportTextsTest` (las «Novedades» no cambian) |
| R-8 | `docs/auditorias/2026-11-08-ataque-1.4.0.md:13` | El estado, al día: sexta comprobación y cierre por R65 | — |
| R-9 | `docs/auditorias/2026-11-08-auditoria-1.4.0.md:84` | «Solo primitivas…, salvo la aritmética de SLIP-0039…» | — |
| R-10 | `vault/BackupCheckTexts.kt:77` | «se detiene en cuanto puede» | — |
| R-11 | `README.md:366` (P-27), `.maestro/README.md` | `banco.example` en lugar de `banco.es` en las pruebas que se pegan en el navegador | — |
| R-12 | `docs/privacidad.html:22` | La fecha: la del día de la fusión en `main`, en el mismo PR | `FinalLegalReviewTest.thePolicyDateIsReadInEveryFormTheOwnerMayWrite` (la forma) |
| I-1 a I-10 | §2 | Etiquetas de origen; Google Authenticator; grupos de 2FAS; línea de marcas; SLIP-0039 comprobado; User-Agent; `banco.es`; GPL; menores; «cartera física» | — |

Ningún cambio toca un formato, una clave, un permiso ni el comportamiento del código: son textos y documentos.

## 7. Lo que queda para el dueño

1. **La fecha de la política** (`docs/privacidad.html:22`), con la forma «D de <mes> de AAAA.» (R-12); GitHub Pages la publica al fusionar en `main`, y hay que hacerlo antes de enviar la app a revisión en Play (`docs/PUBLICAR.md`).
2. **Decidir R-1 a R-12.** Mi recomendación es aplicarlos todos; R-1 es mi decisión sobre R30.
3. **Las notas de la versión:** pegarlas a mano en *Releases → v1.4.0 → Edit* con R-7, y las «Novedades» en Play tal como están.
4. **En Play Console:** «No recoge ni comparte datos», «no comerciante» y los países siguen igual; repasar antes de enviar la redacción vigente de las políticas de metadatos y de suplantación (§4.b).
5. **Buscar y, si quiere, registrar la marca** «Clavero» (y CLAVIS, CLAVISTER) en la clase 9: TMview, OEPM y WIPO (`CLAUDE.md`, «Pendientes», 7).
6. **Después de publicar:** probar las exportaciones reales (P-1 a P-3); si algún formato no entra, corregir `IMPORT_FORMATS`, el README y las notas en la 1.4.1.
7. **En cada versión:** volver a mirar `IMPORT_GOOGLE_AUTHENTICATOR` (I-2), que depende de cómo se comporte una app ajena.
8. **Si cambia algún hecho supuesto** (donaciones, publicidad, una versión de pago, datos que lleguen al desarrollador): nueva revisión del CRA, el TRLGDCU, la Directiva 2024/2853 y la DSA.

---

## Revisión experto-seguridad-cripto

- **O-1** describe el comportamiento real de `replaceWith` (`imports/MergePlan.kt:296-334`): conserva el título y el usuario cuya clave coincide (R58), el emisor y la cuenta del 2FA (R64), y cambia la clave secreta del 2FA, que solo queda en la copia previa (R63, `:279-282`). Avisar de esa pérdida reduce un riesgo real de bloqueo de cuentas, y no da a un atacante nada que no sepa.
- **O-2** no cambia el tope (`IMPORT_MAX_ITEMS`, decisión (a)); solo deja de dar un motivo falso. La cuenta de tamaños sale de `vault/Backup.kt:40-41`, `core/Json.kt:6-10` y `MergePlan.kt:345-354`.
- **O-3** describe lo que hacen `vault/AutoBackup.kt:388-394`, `vault/BackupCheck.kt:48-60`, `:119-139` y `SeguridadApp.kt:62-63`: la copia previa cifrada con la maestra, nunca rotada; la carpeta temporal con adjuntos cifrados, borrada al terminar y al arrancar. No afirma nada absoluto ni promete un borrado físico.
- **R-1, R-2, R-4 y R-5** no cambian ninguna promesa de seguridad. **R-9** hace que el resumen de la auditoría diga lo mismo que su §4 sobre la única aritmética propia (SLIP-0039). **R-10** corrige un texto que daba a entender que una derivación Argon2id se corta, y no se corta (`BackupCheck.kt:56-60`, R38). **R-11** evita que una prueba conecte con un dominio real de un tercero.
- **Ningún texto propuesto** usa «seguro», «garantiza» (salvo «no garantiza»), «imposible», «100 %», «anónimo», «limpio», «sospechoso», «de verdad», «validez legal», «como Signal», «a prueba de cuánticos» ni «wallet».
- **Ningún cambio toca un formato, una clave ni un parámetro criptográfico.**

## Revisión Ingeniero Móvil Senior

- **O-1** se pinta en `bodySmall` bajo las opciones de los repetidos (`ui/screens/ImportScreen.kt:609`), dentro de un `LazyColumn`: unas 25 palabras más, que se parten en líneas sin problema. **O-2** sale en el `ErrorText` del primer paso (`:512`); con U+00A0 la cifra no se parte.
- **O-3, R-2, R-3, R-4 y R-12** son HTML y Markdown: no tocan el APK. La política se sirve desde `docs/` por GitHub Pages; el aviso de las tiendas apunta a la misma dirección.
- **R-1** obliga a cambiar `ImportTextsTest.LISTING_ALLOWED` (`:136`), que lee la ficha desde el disco; el resto del test sigue fijando la regla de Play.
- **R-5 y R-10** cambian literales de `ImportTexts.kt` y `BackupCheckTexts.kt`, que ningún test fija enteros; `ImportTextsTest` sigue encontrando al menos 5 nombres en `IMPORT_FORMATS` y ninguno fuera de `ImportTexts.kt`.
- **Sin cambios de permisos, componentes, hilos ni ciclo de vida:** ningún cambio propuesto toca `AndroidManifest.xml`, `res/` ni el código que corre.

## Fuentes consultadas (10-11-2026)

- python-shamir-mnemonic, `shamir_mnemonic/wordlist.txt`, `vectors.json` y `LICENSE` (comprobados por SHA-256 contra el repositorio) — https://github.com/trezor/python-shamir-mnemonic
- Google, ayuda de Play Console sobre la ficha y la política de metadatos — https://support.google.com/googleplay/android-developer/answer/9898842 y https://support.google.com/googleplay/android-developer/answer/9887877
- Gestor de contraseñas de Google, nombre en español y exportación (fuentes secundarias) — https://www.xataka.com/basics/como-exportar-tus-contrasenas-de-google-chrome y https://proton.me/support/es-419/pass-import-chrome
- Directiva (UE) 2024/2853, plazo de trasposición y estado en España (fuentes secundarias; pendiente de verificar en BOE y EUR-Lex) — https://www.garrigues.com/sites/default/files/noticias/files/informe-garrigues-espana-novedades-legales-2026.pdf y https://www.fieldfisher.com/es-es/locations/espana/actualidad/la-nueva-regulacion-de-los-productos-defectuosos-l
- «Digital Omnibus», estado de la tramitación (fuentes secundarias) — https://www.addleshawgoddard.com/en/insights/insights-briefings/2026/data-protection/eu-digital-omnibus-european-council-adopts-its-position/ y https://www.addleshawgoddard.com/en/insights/insights-briefings/2026/data-protection/eu-digital-omnibus-edpb-edps-joint-opinion/
- Dictamen del Abogado Digital de la 1.3.1, con sus fuentes del TRLGDCU, la Directiva 2019/770, el CRA y el AI Act — `docs/superpowers/handoff/sdd-1.3.1/abogado-1.3.1.md`

*Borrador profesional para revisión del dueño. No sustituye el asesoramiento de un abogado colegiado en cada jurisdicción de publicación.*
