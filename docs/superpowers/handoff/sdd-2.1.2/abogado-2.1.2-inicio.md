# Clavero 2.1.2: visto bueno del Abogado Digital al principio (sobre el diseño)

- **Fecha de referencia:** 27-11-2026.
- **Objeto:** `docs/superpowers/specs/2026-11-27-clavero-2.1.2-design.md` (en adelante, «el diseño»), antes de programar (punto 4 de «Skills: siempre juntas» de `CLAUDE.md`).
- **Skills:** `despacho-digital`; como criterio, lo ya razonado con `privacidad-proteccion-datos` y `regulacion-digital-ue` en `sdd-2.1.1/abogado-2.1.1.md` (§1), que esta versión no altera.
- **Leídos:** `CLAUDE.md`; el diseño entero; `docs/privacidad.html`; `README.md` (las partes de bóvedas, copias, importar, herramientas, formatos, firma y auditoría); `docs/ficha-google-play.md` (`:63`, `:81`, `:122`, `:126`); `docs/PUBLICAR.md:17`; mi dictamen `sdd-2.1.1/abogado-2.1.1.md`; el informe de la auditoría que se va a copiar al repositorio (`REPORT.md` de la ejecución `seguridad-run-1`, aún fuera del repositorio).
- **Código mirado para comprobar los textos:** `vault/AutoBackup.kt:129-165`, `:723-799` y `:838-880`; `imports/ImportTexts.kt:199-208`; `settings/Settings.kt:227`, `:369-387`; `ui/screens/SettingsScreen.kt:428-438`; `AppContainer.kt:600-683` y `:1006-1013`; `vault/A6Text.kt:150-220`; `vault/ContactsView.kt:181-192`; `tools/qr/Visible.kt:23-69`; `tools/ToolTexts.kt:344-368` y `:533-536`; `tools/ImageLimits.kt`; `imports/ImportJson.kt:349-361`; `imports/ImportLimits.kt:15`.
- **Hechos que doy por ciertos:** los de mi dictamen de la 2.1.1 (`abogado-2.1.1.md:8-15`): particular sin ánimo de lucro (Egt17baa), residente en España; app gratuita, sin anuncios, compras ni donaciones, Apache-2.0; no recibe datos; única conexión, la consulta k-anonimizada a `api.pwnedpasswords.com` a petición del usuario; publicación en GitHub y quizá en Google Play, fuera de Francia (con ultramar), Rusia y Bielorrusia. Y los antiobjetivos del diseño (`:27`): ningún formato, permiso, componente exportado, host ni dependencia cambia. Si alguno cambia, este visto bueno no vale para lo que cambie.

## 1. Veredicto

**Visto bueno con cambios.**

El mapa normativo no cambia respecto a la 2.1.1:
- RGPD y LOPDGDD: el desarrollador no es responsable de ningún tratamiento (art. 4.7 RGPD); lo que el usuario hace en su móvil queda en la excepción doméstica (art. 2.2.c RGPD). Borrar copias o carpetas cifradas del propio usuario en su móvil o en su carpeta no es un tratamiento del desarrollador. Los arts. 5.1.c y 25 RGPD siguen como criterio.
- Art. 22.2 LSSI y art. 5.3 de la Directiva 2002/58/CE: no se aplican; la 2.1.2 no escribe nada nuevo en el equipo que no sea estrictamente necesario para lo que pide el usuario.
- TRLGDCU, Directivas (UE) 2019/770 y 2005/29/CE, DSA, Ley 11/2023, Directiva (UE) 2019/882, Data Act y AI Act: no se aplican, por falta de comerciante, de actividad económica o de objeto.
- CRA (Reglamento (UE) 2024/2847) y Directiva (UE) 2024/2853: no se aplican mientras no haya actividad comercial. Aunque se aplicara el CRA, los hallazgos son de severidad baja y no consta explotación activa, así que no habría nada que notificar. Las fechas de aplicación de sus obligaciones de notificación quedan **pendientes de verificar a fecha de hoy** (no he consultado EUR-Lex en esta sesión). No cambian la conclusión.
- Uso de la marca «Cloudflare»: el uso descriptivo de una marca ajena para decir de dónde sale un método es lícito si no sugiere relación ni aval (art. 37 Ley 17/2001 de Marcas; art. 14 Reglamento (UE) 2017/1001) y no induce a error (arts. 5 y 6 Ley 3/1991 de Competencia Desleal). Por eso O-1.
- Políticas de Google Play: obligan como contrato de distribución (metadatos veraces, sin nombres ajenos, «Novedades» de menos de 500 caracteres).

Lo que obliga de verdad es la **regla de veracidad** del proyecto. Aquí, sobre un diseño que aún no tiene código, hay **ocho obligatorios**: uno por el nombre de la auditoría (O-1); cinco porque, tal como está escrito, el diseño volvería falsos textos que hoy son ciertos o reutilizaría textos que no encajan (O-2 a O-6); uno por la política (O-7), y uno por las notas (O-8). Ninguno pide cambiar un formato ni rebajar un arreglo de seguridad: piden que el texto que se enseña sea el cierto o que el comportamiento encaje con el texto que ya existe. Donde dos opciones son válidas, el constructor elige con `experto-seguridad-cripto` y el Ingeniero Móvil Senior, y la elección vuelve a mí en la revisión final.

Lo que me preguntaba el encargo, en corto:
- **¿Hay que tocar la política?** Sí, por R-14 (O-7) y, si se aplica, por R-1. Con ese cambio, también la fecha.
- **¿La ficha de Play?** No. «Sin auditoría independiente» (`docs/ficha-google-play.md:63` y `:126`) y lo que dice de las copias (`:81`, `:122`) siguen siendo ciertos, y la descripción que se pega (`:50`) no cambia.
- **¿El README?** Sí: la línea de R-32 (R-3) y, recomendado, las de C-2, C-3, R-29 y R-30 (R-1, R-4 y R-8).
- **«Borra del móvil su archivo y sus adjuntos»** (`docs/privacidad.html:78`, `README.md:20`): sigue siendo cierta y R-17 la refuerza (lo que quedaba de un borrado cortado se borra al arrancar). No pido añadir ninguna promesa sobre ese caso (R-6).
- **«A6 borra las copias de antes»** (`docs/privacidad.html:46`, `AutoBackup.kt:153-156`): con C-2 **pasa a ser cierta** en el caso de la copia que se estaba haciendo al cambiar la maestra, que hoy queda fuera aunque A6 diga que terminó. Con R-14, A6 borra además en carpetas que el usuario ya no usa, y eso hay que decirlo (O-6 y O-7).

## 2. Cambios obligatorios (O)

**O-1. Ningún texto publicado presenta la revisión como una auditoría de Cloudflare, externa o independiente.**
- **Motivo:** el diseño la llama «auditoría de Cloudflare» (`specs/2026-11-27-clavero-2.1.2-design.md:1`, `:3`, `:7`) y el archivo que se va a copiar se llamaría `docs/auditorias/2026-11-27-auditoria-cloudflare-2.1.1.md` (`:3`). Es la skill pública `security-audit` que publica Cloudflare (MIT), ejecutada por el propio proyecto: Cloudflare no ha participado ni la avala. Leída por un tercero, la expresión sugiere una auditoría hecha por esa empresa, lo que choca con «Sin auditoría independiente» de la ficha (`docs/ficha-google-play.md:63`, `:126`), con la política (`docs/privacidad.html:72`, «La app no ha pasado una auditoría de seguridad independiente») y con el README (`README.md:251`). Las tres frases siguen siendo ciertas y **no se tocan**.
- **Qué se exige:**
  - En las notas de GitHub, en las «Novedades» de Play, en el README, en la ficha, en la política y en la app no se dice «auditoría de Cloudflare», «auditoría externa», «auditoría independiente» ni «auditada». En Play y en sus «Novedades» no aparece «Cloudflare» ni la palabra «auditoría» (la ficha ya dice que no la hay).
  - Las notas de GitHub, si quieren contar el origen, dicen: «una revisión de seguridad hecha por el propio proyecto con un método público (no es una auditoría independiente)». Pueden nombrar el método con su nombre exacto («la skill `security-audit`, publicada por Cloudflare con licencia MIT») solo si añaden «Cloudflare no ha participado en esta revisión».
  - Ni las notas ni el README citan valoraciones del informe como «Clavero resiste bien un ataque serio» (`REPORT.md`, §2): son promesas de seguridad que la ficha prohíbe (`docs/ficha-google-play.md:3`).
- *Base:* regla de veracidad; arts. 5 y 7 LCD como criterio (no inducir a error ni omitir lo que el usuario necesita para valorar); art. 37 LM y art. 14 RMUE (uso descriptivo de marca ajena sin sugerir vínculo). R-10 trata el nombre del archivo.

**O-2. C-2 en la copia de antes de importar: sin copia no se importa, no se enseña el nombre de una copia borrada, y el texto de error tiene que ser cierto.**
- **Motivo:** el diseño aplica C-2 también a la copia de antes de importar (`design.md:91`: «Lo mismo en la copia de antes de importar (`recordImportCopy`)»). Hoy `copyBeforeNow` apunta la copia (`AutoBackup.kt:773`) y devuelve `CopyResult.Saved(written.name)` (`:779`); la pantalla del final dice su nombre (README `:367`, P-6). Si el borrado se hiciera dentro de `recordImportCopy` (`AppContainer.kt:1006-1013`), `copyBeforeNow` seguiría devolviendo `Saved` y la importación seguiría **sin copia**. Eso haría falsas tres afirmaciones públicas: «Antes de guardar hace una copia cifrada de la bóveda» (`docs/privacidad.html:47`), «sin esa copia no importa nada» (`README.md:41`) y el nombre que enseña la pantalla.
- **Qué se exige:**
  1. Si la copia de antes de importar se borra por C-2, la importación **no se aplica**, igual que con `IMPORT_COPY_VAULT_CHANGED` hoy (decisión R17 de la 1.4.0).
  2. No se reutiliza `IMPORT_COPY_VAULT_CHANGED` (`ImportTexts.kt:206-208`): dice «La bóveda se ha sustituido o borrado» (no es eso: cambió la maestra) y «La copia que se hizo sigue en la carpeta» (sería falso si se borró). Tampoco `IMPORT_COPY_FAILED` (`:200`), porque si el borrado falla la copia sí está en la carpeta.
  3. Texto nuevo, cierto en los dos casos (borrada o no): «La contraseña maestra ha cambiado mientras se hacía la copia, así que no se ha importado nada. Vuelve a intentarlo.» Sin palabras prohibidas (`ForbiddenWords.kt:5-7`). Si el equipo técnico prefiere no añadir texto, la alternativa válida es **no borrar** la copia de antes de importar y abortar con este mismo texto, apuntando la copia en las listas de A6 (punto 4).
  4. Si no se puede borrar, la copia va a la lista de copias de antes de importar de su carpeta (`importCopies`, que A6 borra) y **no** a la de rotación (`copies`). Si fuera a `copies`, la rotación la borraría y dejaría de ser cierto «la rotación de las copias automáticas no la borra» (`docs/privacidad.html:47`).
  5. Lo mismo, si el plan aplica C-2 en `copyBeforeNow` y así alcanza a la copia de antes de la 2.0 (`AutoBackup.kt:749`): si se borra, el flujo pasa a «sin copia» y pregunta («Exportar una copia antes» o «Seguir sin copia»), como dice `docs/privacidad.html:45`. Nunca se anuncia como hecha.
- *Base:* regla de veracidad; decisión R17 de la 1.4.0 (sin copia no se importa).

**O-3. C-2 en la copia automática: «Última copia» no puede enseñar la hora de una copia que se ha borrado.**
- **Motivo:** el diseño da a la copia borrada «el resultado de una copia rancia (`stale`)» (`design.md:90`). Hoy `stale` construye `AutoBackupResult(uri, at, …)` con `writtenAt = at` (`AutoBackup.kt:916-917`), y `recording` guarda `lastAt = r.writtenAt` (`Settings.kt:385`). Ajustes enseña entonces «Última copia: <fecha>» (`SettingsScreen.kt:428-438`). Hoy es cierto, porque la copia rancia se queda en la carpeta. Con C-2 sería la hora de una copia que ya no existe. Además, si `lastAt` avanza, el calendario puede dar por hecha la copia del día (`missed`, `AutoBackup.kt:797-801`).
- **Qué se exige:** cuando C-2 **borra** la copia, el resultado no mueve `lastAt` (por ejemplo, `writtenAt = null`) ni la marca de cambios, y no hay aviso nuevo. Cuando **no** puede borrarla y la apunta en `copies`, puede moverlo, porque la copia existe. Lo fija un test.
- *Base:* regla de veracidad (texto de la interfaz).

**O-4. C-1 y «Bóveda nueva»: lo que encuentra el usuario al volver tiene que ser lo que dice el diseño, y lo que digan las notas.**
- **Motivo:** el diseño dice que, si `leaves` cambió, `newVault` no llama a `made` y que «la bóveda nueva queda creada, registrada y activa, pero bloqueada» (`design.md:65-69`). Eso vale para `importAsNewVault`, donde la bóveda se escribe en `before` (`AppContainer.kt:621-626`). En `createVault`, en cambio, `made` **es** la creación (`AppContainer.kt:609`: `made = { it.create(master, name.trim()) }`). Si no se llama, no se escribe ningún archivo: queda una ranura vacía, activa, y la regla RT-22 solo deja borrarla mientras haya otra bóveda con archivo. Además, en `importAsNewVault` el nombre elegido se pone en `made` (`:628-629`, `store.rename(name)`): si no se llama, la bóveda queda con el nombre de la copia, no con el que escribió el usuario.
- **Qué se exige:**
  - El plan fija qué pasa en cada vía y lo prueba. En `createVault` vale que se cree y después se bloquee, o que se deshaga entera, como `dropVault`, sin dejar ranura vacía. En `importAsNewVault`, el nombre elegido se conserva o se dice. Lo decide el equipo técnico.
  - Si al volver hay algo que el usuario no espera (una bóveda que no se creó, otro nombre), se le dice con un texto existente que sea cierto o con uno nuevo que pasará por mí.
  - Las notas de la versión describen solo el resultado probado: «la bóveda nueva queda bloqueada» solo para la vía en que es cierto.
- **Efecto positivo:** con C-1, `LOCK_ON_EXIT_HINT` y `LOCK_ON_EXIT_SHORT` («Al salir de la app se bloquea siempre, salvo mientras eliges un archivo o escaneas un QR para ella», `ToolTexts.kt:533-536` y `:689`), hoy falsos en este caso, pasan a ser ciertos.
- *Base:* regla de veracidad; antiobjetivo de no dejar la bóveda bloqueada en el uso normal (`design.md:29`).

**O-5. R-24: una tarjeta con el nombre de tu identidad no puede enseñar el aviso de «tu contacto».**
- **Motivo:** el diseño trata esa tarjeta como `KeysChanged` (`design.md:164`). El aviso de `KeysChanged` dice «Las claves de este contacto han cambiado», «se parece a tu contacto «…»», «Puede que haya cambiado de móvil o de identidad» y «Compara las dos huellas con él» (`ContactsView.kt:181-191`). Con la identidad propia, las cuatro frases son falsas: no es un contacto, no es otra persona que cambió de móvil y no hay con quién comparar la huella guardada. Además, `savedFingerprint` llama a `contactCard()` sobre la entrada (`:204`), que no es de contacto.
- **Qué se exige:** el mismo nivel de aviso y el mismo botón «Guardar como contacto nuevo», con un título y un cuerpo propios. Propuesta:
  - Título: «Esta tarjeta lleva tu nombre».
  - Cuerpo: «Esta tarjeta lleva el mismo nombre que tu identidad, pero trae otras claves: no es tu tarjeta. Puede ser de otra persona que se llama igual o de alguien que intenta hacerse pasar por ti. Si la guardas, queda como un contacto nuevo. Compara su huella con esa persona.»
  - El pie de página (`:187-191`) sigue igual: no menciona «tu contacto».
- Dice «puede ser»: no acusa (hay homónimos) y no usa «sospechoso» (`ForbiddenWords.kt:5`). Si el equipo técnico prefiere otra salida (por ejemplo, `KeysChanged` con el campo del nombre vacío y otro texto), la redacción vuelve a mí.
- *Base:* regla de veracidad; M-3 (avisos a la vista).

**O-6. R-14: los textos de A6 tienen que seguir siendo ciertos con las carpetas que ya no se usan.**
- **Motivo:** con R-14, la petición de A6 incluye las copias apuntadas en `pastTargets` (`design.md:150`; hoy `listedCopies` solo mira `targets`, `Settings.kt:227`). Entonces:
  - `AUTO_BACKUP_REPLACED` dice «las que la app tenía apuntadas **en esta carpeta**» (`AutoBackup.kt:153-156`). Se quedaría corto si también se borraron copias en otra carpeta.
  - `AUTO_BACKUP_REPLACED_SEVERAL` dice «Se hizo una copia nueva **en cada carpeta**» (`:162-165`). Sería falso si cuenta una carpeta que ya no se usa, donde no se hace copia.
  - `a6PartialText` y `a6StaleText` cuentan «de las N carpetas» (`A6Text.kt:151-165`, `:190-202`). El usuario solo ve en Ajustes las carpetas en uso, así que N no le cuadraría.
  - El aviso que el diseño quiere reutilizar para un destino quitado sin permiso (`design.md:150`, «con el aviso que ya existe») es `AUTO_BACKUP_NO_PERMISSION`: «La app ya no tiene permiso para usar la carpeta de la copia automática. Vuelve a elegirla.» (`AutoBackup.kt:129`). Para una carpeta que el usuario quitó a propósito, las dos frases son falsas o un mal consejo.
- **Qué se exige:**
  - Cada resumen de A6 es cierto con carpetas que ya no se usan: o no las cuenta entre «las N carpetas» ni en «en cada carpeta» y las nombra aparte, o el texto se adapta.
  - El fallo de una carpeta que ya no se usa tiene su propio texto. Propuesta: «No se pudieron borrar las copias anteriores de una carpeta que ya no usas para la copia automática: la app ya no tiene permiso para usarla. Puede que alguna se abra con la contraseña anterior. Míralo a mano.» No dice «Vuelve a elegirla» ni «Se volverá a intentar», salvo que haya un reintento real (decisión A6 de la 1.3.x).
  - Si el plan prefiere no añadir textos, la alternativa válida es dejar R-14 fuera de esta versión (es un refuerzo, no un hallazgo).
- *Base:* regla de veracidad; decisión A6 de la 1.3.x («Se volverá a intentar» solo donde hay un reintento real).

**O-7. La política dice que A6 alcanza también las carpetas que ya no usas (R-14) y lleva la fecha del día de la fusión.**
- **Motivo:** `docs/privacidad.html:46` acota lo que borra A6 a «las que la app tiene apuntadas **en esa carpeta**», y `:38` dice que lo de las 6 carpetas que ya no usas se guarda «para seguir rotando sus copias si vuelves a elegirlas». Con R-14 la app borra, si se le pide, archivos del usuario en carpetas que ya no usa. Es lo que el usuario querrá casi siempre, pero la política describe exactamente qué borra la app y dónde. Callarlo la volvería engañosa por omisión.
- **Texto nuevo** (si R-14 entra):
  - `:46`, la frase «Puedes pedir que se haga una copia nueva y se borren las copias automáticas y las de antes de importar hechas antes del cambio: las que la app tiene apuntadas en esa carpeta.» pasa a: «Puedes pedir que se haga una copia nueva y se borren las copias automáticas y las de antes de importar hechas antes del cambio: las que la app tiene apuntadas en las carpetas de la copia automática y en las que usaste antes para ella y aún recuerda. Si ya no tiene permiso para una de estas últimas, no borra nada en ella y te lo dice.»
  - `:38`, el final «…, para seguir rotando sus copias si vuelves a elegirlas» pasa a: «…, para seguir rotando sus copias si vuelves a elegirlas y para borrarlas si pides reemplazar las copias tras cambiar la contraseña maestra».
  - Lo demás de `:46` («No se borran las que no tenga apuntadas…», «Con varias carpetas, cada una borra solo las que tiene apuntadas…») sigue siendo cierto y se queda.
- **Fecha:** con cualquier cambio de la política (este o el de R-1), `docs/privacidad.html:22` pasa a «Última actualización: D de M de 2026.», con el día de la fusión, en el formato que lee `FinalLegalReviewTest.policyDate` (día sin cero, mes en minúsculas). Si la política no cambia, la fecha no se toca.
- *Base:* regla de veracidad; arts. 12.1 y 13 RGPD como criterio de transparencia.

**O-8. Las notas de la versión 2.1.2 (GitHub y Play) dicen solo lo probado, sin prometer de más.**
- **Qué no pueden decir:**
  - nada de O-1 («auditoría de Cloudflare», «auditada», «independiente», «externa»), ni «Cloudflare» en Play;
  - «más segura», «segura», «sin vulnerabilidades», «corrige todos los fallos» o «todos los hallazgos»: V-2 se queda por decisión del dueño y la §6 descarta refuerzos (`design.md:22`, `:179-196`); tampoco palabras prohibidas (`ForbiddenWords.kt:5-7`);
  - de V-1, que la copia «solo escribe dentro de la carpeta elegida»: el diseño acepta cualquier documento del **mismo proveedor** (misma autoridad) y descarta exigir que cuelgue del árbol (`design.md:51`); lo cierto es «del mismo proveedor de almacenamiento que la carpeta elegida»;
  - de C-2, «con la maestra nueva se hace otra copia en la siguiente ocasión», salvo que un test demuestre que, con calendario y sin cambios en la bóveda, la siguiente copia se hace de verdad (la regla de saltarse copias de D-3 podría saltarla);
  - de R-33, que las fotos «se borran de la memoria»: Android y la JVM no lo garantizan. Lo cierto es «pone a cero su copia de los bytes del archivo elegido en cuanto ya no hace falta»;
  - nada del Generador (V-2) como arreglado;
  - nombres de otras apps en Play ni en sus «Novedades» (R2, R7, R8 y R31 de la 1.4.0). En GitHub, solo para decir qué formato se lee y con la línea de marcas: aquí C-3 afecta a exportaciones de varios gestores, y basta con decir «exportaciones en JSON con carpetas o grupos».
- **Qué pueden decir** (borradores; el texto final pasa por mí en la revisión final):
  - **GitHub:** «Clavero 2.1.2 arregla cuatro fallos de severidad baja y añade refuerzos, salidos de una revisión de seguridad hecha por el propio proyecto (no es una auditoría independiente). Si sales de la app mientras importas una copia como bóveda nueva, al volver esa bóveda está bloqueada [y lo que resulte de «Bóveda nueva», O-4]. Una copia automática que se estaba haciendo justo cuando cambias la contraseña maestra se hizo con la anterior: la app la borra al terminar o, si no puede, la apunta para que la quiten la rotación o el reemplazo de las copias. «Importar de otra app»: un nombre de carpeta o grupo de más de 1 024 caracteres en una exportación JSON ya no hace lenta la vista previa (ese elemento entra sin carpeta), y «Cancelar» la corta. Un dominio con los caracteres invisibles de unión (U+200C o U+200D) ya no se lee como el mismo dominio sin ellos: al importar no sale como repetido de tu entrada, y el Inspector de QR avisa. La copia automática solo escribe en documentos del mismo proveedor de almacenamiento que la carpeta elegida. Además, refuerzos en el bloqueo, las copias y la lectura de archivos. No cambia ningún formato, permiso, componente, conexión ni dependencia: la 2.1.2 abre lo mismo que la 2.1.1, y la 2.1.1 abre lo que escribe la 2.1.2.» Si entran R-14 y la política, una línea: «Al reemplazar las copias tras cambiar la contraseña maestra, también se borran las que la app tenía apuntadas en carpetas que ya no usas.»
  - **Play («Novedades», menos de 500 caracteres):** «• Si sales de la app mientras importas una bóveda nueva, al volver está bloqueada. • Una copia automática hecha justo al cambiar la contraseña maestra se borra al terminar o, si no puede, la quita la rotación. • Importar de otra app: un nombre de carpeta enorme ya no la hace lenta. • Un dominio con caracteres invisibles ya no se confunde con otro. • Otros arreglos. No cambia ningún formato ni permiso.» (unos 390 caracteres; se cuentan al cerrar.)
  - Cada frase se publica solo si su test o su prueba en el móvil sale bien (como O-2 de la 2.1.1). Lo que no salga se quita.
- *Base:* regla de veracidad; políticas de metadatos de Google Play; reglas de `CLAUDE.md` sobre nombres de otras apps y palabras prohibidas.

## 3. Cambios recomendados (R)

**R-1. Decir en la política y en el README que la copia automática que se estaba haciendo al cambiar la maestra se borra.**
- **Motivo:** ningún texto se vuelve falso: la política describe A6 como algo que se pide (`docs/privacidad.html:46`) y el README dice que la rotación solo borra lo que la app creó (`README.md:30`). Pero C-2 es un borrado de un archivo del usuario sin que lo pida, y conviene que la política, que enumera lo que la app borra, lo diga.
- **Texto:** al final de `docs/privacidad.html:46` y, con la misma redacción, en `README.md:30`: «Si una copia automática se estaba haciendo justo cuando cambias la contraseña maestra, la app la borra al terminar, porque se hizo con la anterior; si no puede, la deja apuntada para que la quiten la rotación o, si lo pides, el reemplazo de las copias.» Solo si el comportamiento final es ese (O-2, O-3).
- *Base:* arts. 12 y 13 RGPD como criterio de transparencia.

**R-2. C-4: reutilizar la marca visible del Inspector, sin texto nuevo.**
- **Motivo:** el Inspector ya enseña cada carácter invisible como `⟨U+200C⟩` (`Visible.kt:23-29`, `:69`), y ese es el «distintivo» que pide el diseño (`design.md:120`). Hoy `duplicateSubline` pasa el host por `plainReportText` (`MergePlan.kt:666-672`), que quita los invisibles: sin la marca, la fila enseñaría `banco.es` para un host que no lo es.
- **Recomendación:** en la fila de duplicado, el host con la marca de `visibleText`, por ejemplo `ban⟨U+200D⟩co.es`. Es cierto, no acusa a nadie y no añade texto. Si hiciera falta una palabra, solo valdría algo descriptivo como «(con caracteres invisibles)», nunca «falso», «engaño» ni «sospechoso» (`ForbiddenWords.kt:5`), y vuelve a mí.

**R-3. R-32: la línea del README, alineada con el texto de la app.**
- **Texto propuesto** (en Funciones, junto a Ajustes, `README.md:66`): «Bloqueo: al salir de la app se bloquea siempre, salvo mientras eliges un archivo o escaneas un QR para ella. En ese caso, y mientras usas otra app en pantalla dividida o en ventana flotante, cuenta el tiempo elegido; con «Al salir», unos 2 minutos. Al volver del selector, la siguiente salida ya bloquea.»
- Es la misma regla que `LOCK_ON_EXIT_HINT` (`ToolTexts.kt:533-536`), más R-5. Si la «observación del dueño» que cita el diseño (`design.md:177`) dice algo más, esa parte vuelve a mí. No cuenta nada del móvil del dueño (regla «Privacidad del dueño»).

**R-4. R-29 y R-30: documentarlos donde ya se describe lo que la CI se niega a firmar.**
- `docs/PUBLICAR.md:17` enumera cuándo fallan **build-firma** y **play-bundle**. Hay que añadir: «Desde la 2.1.2, en la ejecución de una etiqueta `v*`, se niegan a firmar si su versión no es mayor que la de la mayor etiqueta `v*` anterior, y **play-bundle** pasa `check-manifest.sh` también sobre el AAB.» Con esto el documento sigue siendo cierto. Si el plan compara también la etiqueta en mayúscula `V2.1.0`, que el dueño tiene pendiente de borrar, que lo diga.

**R-5. R-21: el error del tope de piezas tiene que ser cierto para una imagen real.**
- Se puede reutilizar «el archivo está dañado o tiene un formato que no conocemos» (como `QR_IMAGE_DAMAGED`, `ToolTexts.kt:367-368`), pero solo con un tope tan alto que ningún archivo de cámara real lo alcance, y con un test con un archivo realista. Nunca los textos de tamaño («50 MB», «260 megapíxeles», `:364-366`) ni `IMAGE_NO_MEMORY` (`:276`), que dirían otra cosa.

**R-6. R-17: ninguna promesa nueva, y cuidado con los datos.**
- `docs/privacidad.html:78` y `README.md:20` no cambian. No se promete en ningún sitio que «lo que quede de un borrado cortado se borra siempre», porque solo pasa si `vaults.json` se leyó de su archivo (`design.md:153`).
- El riesgo jurídico de R-17 es el contrario: borrar datos de una bóveda viva (antiobjetivo «no romper datos existentes», `design.md:28`; responsabilidad civil general del art. 1902 CC, limitada por la licencia Apache-2.0 y por la gratuidad). Pido que el plan pruebe al menos esto: con `vaults.json` reconstruido o ilegible no se borra nada; una carpeta listada nunca se borra; una bóveda a medio crear o importar (el caso de `dropVault`) solo contiene lo que el usuario aún tiene en su origen. El criterio técnico es de `experto-seguridad-cripto` y del Ingeniero Móvil Senior.

**R-7. R-33: si se cuenta, con su límite.** En README o notas: «pone a cero su copia de los bytes del archivo elegido en cuanto ya no hacen falta». Nunca «borra la foto de la memoria» (O-8).

**R-8. C-3: una línea en el README.** En `README.md:41`, tras «Las carpetas pasan a etiquetas (como mucho 100 nuevas por importación)»: «; una carpeta o un grupo con un nombre de más de 1 024 caracteres no pasa a etiqueta, y el elemento entra sin ella». El valor es el de `MAX_TITLE` (`ImportLimits.kt:15`). Hoy «lo que no entra, con su motivo» no cubre este caso, porque el elemento sí entra.

**R-9. R-23: el nombre del campo de Bitwarden no se corta sin decirlo.** Si pasa del tope, se sigue la regla de las notas (`NotesBuilder`, `ImportJson.kt:363-367`: nunca se importa cortado; el elemento va a «no entra» con su motivo) o se documenta lo que se haga. «Una sola línea» solo cambia cómo se escribe en las notas y no pide texto.

**R-10. El informe que se copia al repositorio: nombre y cabecera.**
- **Nombre del archivo:** `docs/auditorias/2026-11-27-auditoria-2.1.1.md` (o `…-revision-2.1.1.md`), sin «cloudflare» (O-1). Si se cambia, se cambia también en el diseño (`design.md:3`).
- **Cabecera:** su línea de método (`REPORT.md:7`) ya dice «la skill pública `security-audit` de Cloudflare (licencia MIT)». Añadir: «La ejecutó el propio proyecto; Cloudflare no ha participado en ella ni la avala. No es una auditoría independiente.»
- **Antes de copiarlo:** comprobar que no lleva nombres de modelos (`PublishedFilesNameNoModelTest`) ni datos del dueño. En mi lectura no los hay: «lo que viste en el móvil» no dice qué móvil.
- **Licencia:** si se copia al repositorio algún archivo de la propia skill (los validadores `.cjs`, plantillas), va con su aviso MIT (compatible con Apache-2.0). Los tests de la auditoría, escritos en la ejecución, no lo necesitan.
- El título del diseño (`design.md:1`, `:7`) puede quedarse como documento interno, pero mejor «arreglos de la revisión de seguridad de la 2.1.1».

## 4. Lo que no hay que cambiar

- La ficha de Play: la descripción que se pega (`docs/ficha-google-play.md:50`), «Sin auditoría independiente» (`:63`, `:126`) y la sección de datos («No recoge ni comparte datos»).
- `docs/privacidad.html:72` («La app no ha pasado una auditoría de seguridad independiente») y `:78`; `README.md:251`.
- La política en todo lo demás. V-1, C-1, C-3, C-4 y los refuerzos R-1 a R-13, R-15, R-16, R-18 a R-28 y R-31 no tocan nada de lo que describe: ni datos nuevos, ni lugares de almacenamiento, ni conexiones, ni permisos. R-16 deja cierto «la rotación solo borra las copias que la propia app creó» (`README.md:30`), y R-25 y R-26 no tienen texto.
- `LOCK_ON_EXIT_HINT` y `LOCK_ON_EXIT_SHORT`: con C-1 (y R-5) pasan a ser ciertos sin tocarlos.
- `AUTO_BACKUP_REPLACED` en el caso de una sola carpeta sin carpetas antiguas: con C-2 pasa a ser cierto también para la copia que se estaba haciendo.

## 5. Límites de esta revisión

- Revisa un diseño sin código: cada «es cierto» depende de que el comportamiento final sea el descrito. Lo comprobaré contra el código en la revisión final.
- No he visto la app en un móvil.
- Los calendarios del CRA y de la Directiva (UE) 2024/2853 quedan pendientes de verificar a fecha de hoy. No cambian la conclusión mientras no haya actividad comercial.
- Es un borrador profesional para el proyecto; no sustituye el criterio de un abogado colegiado si llega una reclamación.

## Textos para el Abogado Digital (revisión final)

Llegan a la revisión final, contra el código:
1. El texto del error de la copia de antes de importar con la maestra cambiada (O-2), o la alternativa elegida.
2. Lo que se le dice al usuario, si se le dice algo, cuando vuelve tras salir durante «Bóveda nueva» o «Importar como bóveda nueva» (O-4).
3. El título y el cuerpo del aviso de una tarjeta con el nombre de tu identidad (O-5).
4. Los resúmenes y el fallo de A6 con carpetas que ya no se usan (O-6), o la constancia de que R-14 quedó fuera.
5. `docs/privacidad.html:38`, `:46` y la fecha de `:22` (O-7 y R-1).
6. Las notas de GitHub y las «Novedades» de Play de la 2.1.2, con su recuento (O-8).
7. Las líneas del README de R-1, R-3, R-8 y, si se cuenta, R-7; y la de `docs/PUBLICAR.md` (R-4).
8. La marca o el texto del host con caracteres invisibles en la fila de duplicado (R-2), si no es la marca `⟨U+…⟩` tal cual.
9. El texto del tope de piezas de las imágenes (R-5).
10. El nombre y la cabecera del informe en `docs/auditorias/` (R-10).
11. Cualquier otro texto nuevo o cambiado que salga del plan (`design.md:198-205`).

— el Abogado Digital, 27-11-2026
