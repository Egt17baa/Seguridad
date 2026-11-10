# Clavero 1.4.1: revisión final del Abogado Digital

- **Fecha de referencia:** 10-11-2026.
- **Rama:** `ccr-d1e75160-p5f4wc`, en `02bff19`, contra `origin/main` (`git diff origin/main...HEAD`).
- **Skills:** `despacho-digital` y `privacidad-proteccion-datos`. `regulacion-digital-ue` y `propiedad-intelectual-ia` no hacen falta: la 1.4.1 no cambia nada de lo que ya se revisó en la 1.4.0 (DSA, CRA, accesibilidad, licencias ni marcas), salvo el uso de «Windows», que se trata abajo.
- **Hechos que doy por ciertos (los mismos que en la 1.4.0):** el autor es un particular sin ánimo de lucro. La app es gratuita, no tiene anuncios ni donaciones y no recibe datos de nadie. Se publica en GitHub y en Google Play, fuera de Francia, Rusia y Bielorrusia. No hay textos nuevos en la interfaz.

## 1. Conclusión

**Se puede publicar, con cambios.** Ninguna norma exige cambios en la 1.4.1. Los cambios obligatorios vienen de las reglas del propio proyecto:
- la regla de veracidad: el texto publicado no afirma nada que el código no haga;
- la regla «Privacidad del dueño» de `CLAUDE.md:169-170`.

Son cuatro:
- O-1: el mensaje de la fusión «Squash and merge»;
- O-2: lo que queda en el árbol sobre las apps del dueño y unos nombres de ejemplo;
- O-3 y O-4: dos frases de las notas de la versión.

Todos son cambios de texto. No tocan formatos, comportamiento ni tests, salvo dos comentarios de código.

La política de privacidad (`docs/privacidad.html`) y la ficha de Google Play (`docs/ficha-google-play.md`) **no necesitan cambios**. La fecha de la política tampoco cambia (§4).

## 2. Cambios obligatorios

### O-1. El mensaje de la fusión «Squash and merge»

- **Dónde:** `docs/superpowers/handoff/2026-11-10-clavero-1.4.1-traspaso.md`, §3, antes del punto 1 (`:32`).
- **Por qué:** en un PR con varios commits, el mensaje que propone GitHub para «Squash and merge» suele juntar los mensajes de todos ellos. El commit `031719f` dice en su cuerpo de qué gestor de contraseñas pidió importar el dueño. Aceptar el mensaje propuesto llevaría ese dato al único commit que llega a `main`, que es lo que la regla de `CLAUDE.md:170` quiere evitar. Lo mismo pasa con la dirección de autor: la cuenta tiene el correo privado, pero el diálogo deja elegir la dirección.
- **Texto propuesto** (un punto nuevo, «0.», en §3):

  > 0. **La fusión:** en el PR, «Squash and merge». Antes de confirmar, borra el mensaje que propone GitHub (junta los de todos los commits de la rama) y escribe este:
  >    título `Clavero 1.4.1 (#<número del PR>)`; cuerpo «Arreglos: borrar una entrada vuelve una sola vez a la lista de la bóveda (F-1); la lista conserva la búsqueda, los filtros y la posición mientras la bóveda está abierta (F-4); los flujos de Maestro valen en Windows y en el móvil (F-2 y F-3); el CSV con las columnas de los navegadores lee también `folder` y `totp` (M-1). versionName 1.4.1.»
  >    En la dirección de autor, elige la `noreply` de GitHub.

### O-2. Lo que queda en el árbol sobre las apps del dueño y unos nombres de ejemplo

- **Por qué:** `7d87224` y `bb37252` quitaron el modelo del móvil, el sistema y las apps del dueño de casi todos los documentos. El diseño de la 1.4.0 (`docs/superpowers/specs/2026-11-03-clavero-1.4.0-design.md:51`) ya habla de «exportaciones con datos inventados de los formatos que se leen». Quedan estos sitios, que siguen diciendo de qué apps iba a exportar el dueño, o que hizo una captura de una de ellas.
- **Riesgo:** el seudónimo se puede unir a una persona por las fusiones antiguas (`CLAUDE.md:170`). Saber qué gestor de contraseñas y qué app de 2FA usa el autor de una app de seguridad ayuda a dirigirle un *phishing*. La norma no lo exige, porque son datos propios publicados por él (art. 2.2.c) RGPD no aplica, pero el dueño es quien decide). Lo exige su regla.

| Archivo:línea | Texto actual (resumido) | Texto propuesto |
|---|---|---|
| `docs/superpowers/plans/2026-11-03-clavero-1.4.0.md:60` | `## Files the owner exports (for Tasks 6, 7 and 10)` | `## Test exports (for Tasks 6, 7 and 10)` |
| `docs/superpowers/plans/2026-11-03-clavero-1.4.0.md:62` | «The owner makes these files with the **same invented data** in every app and gives them to the controller.» | «These files would be made with the **same invented data** in every format; owner decision (e) replaced them with synthetic files.» (el resto de la línea y la tabla `:84-94` quedan como están) |
| `docs/superpowers/handoff/sdd-1.4.0/progress.md:112` | «Step 3 pending (owner's real bitwarden.json, aegis-claro.json, 2fas-claro.2fas; fixtures …)» | «Step 3 pending (real exports of the JSON formats; fixtures hand-made from public docs with invented data).» |
| `docs/superpowers/handoff/sdd-1.4.0/progress.md:131` | «Task 7: step 3 pending — owner's real bitwarden.json, aegis-claro.json, 2fas-claro.2fas.» | «Task 7: step 3 pending — real exports of the JSON formats.» |
| `docs/superpowers/handoff/sdd-1.4.0/task-7-report.md:29` | «**Step 3 (the owner's files) is still pending.** When `bitwarden.json`, `aegis-claro.json` and `2fas-claro.2fas` arrive, …» | «**Step 3 (real exports) is still pending.** When real exports of these formats arrive, …» (el resto, igual) |
| `docs/superpowers/handoff/sdd-1.4.0/task-10-review.md:281` | «… quote Bitwarden's UI labels, from the owner's screenshot of 4-11-2026.» | «… quote Bitwarden's UI labels, from a screenshot of 4-11-2026.» |
| `app/src/main/java/app/seguridad/ui/screens/VaultScreen.kt:415` | El ejemplo de F-4 con tres nombres de pila | `(deleting «Beta» from «Alfa, Beta, Gamma» leaves «Alfa, Gamma» on screen, not the top).` |
| `app/src/test/java/app/seguridad/PlatformSourcesTest.kt:55-56` | El mismo ejemplo, atribuido al dueño | `1.4.1, F-4 (the owner): after deleting «Beta» from «Alfa, Beta, Gamma», the vault list is back where it was, at` / `«Alfa, Gamma», not at the top. …` |

**Sobre los dos comentarios de código:** no consta si esos nombres de pila son entradas de la bóveda del dueño. `CLAUDE.md:169` dice «nada de su bóveda» y la lista de fallos (`2026-11-10-clavero-1.4.1-fallos.md:81`) ya usa «Alfa, Beta, Gamma», así que se cambian por precaución. Son comentarios: no cambian el código ni el test.

### O-3. Notas de GitHub: «Pruebas automáticas en el móvil»

- **Dónde:** `docs/superpowers/handoff/2026-11-10-clavero-1.4.1-traspaso.md:51`.
- **Por qué:** dice «funcionan en Windows y en un móvil real». Solo se han ejecutado en el móvil los siete flujos que no borran datos, y con la 1.4.0 (`2026-11-10-clavero-1.4.1-fallos.md:75-76`; README, «Pruebas de la 1.4.1, para la 1.4.2»). Las tres semiautomáticas se reescribieron sin ejecutarlas, y `borra-datos/` no se ha pasado. La frase afirma de todos los flujos lo que solo se ha visto en siete.
- **Texto propuesto:**

  > - Pruebas automáticas en el móvil (.maestro, para quien compila Clavero): los nombres de los flujos ya valen en Windows, y los siete flujos que no borran datos pasan en un móvil. Los demás todavía no se han ejecutado.

- **Marca:** citar «Windows» para decir con qué sistema funciona un producto es un uso descriptivo permitido (art. 14.1.c) del Reglamento (UE) 2017/1001 y art. 37.1.c) de la Ley 17/2001, de Marcas). No es el nombre de otra app ni un formato que se lea, así que no necesita la línea de marcas, aunque se puede añadir.

### O-4. «Novedades» de Google Play: el CSV

- **Dónde:** `docs/superpowers/handoff/2026-11-10-clavero-1.4.1-traspaso.md:57`.
- **Por qué:** dice «Al importar un CSV con carpeta y código 2FA, se conservan los dos». Es más amplio que el código (`imports/CsvFormats.kt`, `COL_FOLDER` y `COL_TOTP`), que hace esto:
  - solo lee `folder` y `totp` en el CSV con las columnas de los navegadores;
  - la carpeta pasa a etiqueta;
  - un 2FA que no lee va a «no entran» (`CsvFormatsTest.browserCsvBadTotpOrBrokenFolder`).

  Un CSV de otro formato con columnas de carpeta o de 2FA no las conserva. La ficha de Play es el texto más visible, y la Política de metadatos de Google Play pide que no induzca a error.
- **Texto propuesto** (unos 270 caracteres, por debajo de 500, sin nombres de otras apps ni palabras prohibidas):

  > Arreglos: al borrar una entrada vuelves a la lista de la bóveda, en el mismo sitio y con tu búsqueda. Al importar un CSV con las columnas de los navegadores que trae también carpeta y 2FA, la carpeta pasa a etiqueta y el 2FA, si Clavero lo lee, entra como código. Ningún formato de Clavero cambia.

## 3. Cambios recomendados

### R-1. Notas de GitHub y README: la carpeta es una etiqueta

- **Dónde:**
  - `docs/superpowers/handoff/2026-11-10-clavero-1.4.1-traspaso.md:50`;
  - `README.md:27`, el inciso «desde la 1.4.1, …».
- **Por qué:** «conserva la carpeta y el código 2FA» es cierto, pero en Clavero la carpeta pasa a etiqueta y el 2FA es una entrada aparte. Decirlo evita que alguien busque carpetas. El README ya tiene su línea de marcas (`README.md:33`).
- **Texto propuesto (notas):**

  > - Importar de otra app: un CSV con las columnas de los navegadores que trae también las columnas folder y totp conserva la carpeta de cada acceso, como etiqueta, y su 2FA, que entra como un código aparte. Si Clavero no lee ese 2FA, lo apunta en lo que no entra, y el acceso entra igual.

- **Texto propuesto (README, el inciso):** «desde la 1.4.1, un CSV con esas columnas que trae también `folder` y `totp` conserva la carpeta, como etiqueta, y el 2FA».

### R-2. Borrar la rama no quita sus commits de GitHub

- **Dónde:**
  - `CLAUDE.md:170`;
  - `docs/superpowers/handoff/2026-11-10-clavero-1.4.1-traspaso.md:7`;
  - `docs/superpowers/handoff/2026-11-10-clavero-1.4.1-fallos.md:3`.
- **Por qué:** los tres textos dan a entender que, con la fusión «Squash and merge» y la rama borrada, los primeros commits de la rama dejan de estar a la vista. Pero GitHub guarda las referencias de cada PR (`refs/pull/<n>/head`), y la pestaña «Commits» del PR sigue enseñándolos después de borrar la rama. Esos commits nombran el móvil del dueño, lo que importó y el gestor de O-1.
  - Es lo que dice la guía de GitHub «Removing sensitive data from a repository»: lo que queda en los PR o en vistas en caché solo lo quita su soporte. Conviene confirmarlo en la versión vigente de esa guía.
  - El dueño ya decidió no reescribir el historial. El texto solo tiene que describir con exactitud lo que se consigue, para que decida sabiéndolo.
- **Texto propuesto** (al final de `CLAUDE.md:170`, y en una línea igual en el traspaso):

  > Borrar la rama no quita esos commits de GitHub: siguen en la pestaña «Commits» del PR, porque GitHub guarda sus referencias. Quitarlos del todo requiere pedírselo al soporte de GitHub; es decisión del dueño.

### R-3. Lo que queda sobre la cámara del móvil del dueño

- **Por qué:** `ImageLimits.kt` ya dice «as some phones shoot». Estos sitios siguen diciendo que el móvil del dueño hace fotos de 200 MP, y eso reduce mucho los modelos posibles.

| Archivo:línea | Texto propuesto |
|---|---|
| `app/src/test/java/app/seguridad/tools/redact/RedactionTest.kt:196` | `// Some phones shoot about 200 MP at full resolution: allowed, as are smaller sensors.` |
| `docs/superpowers/handoff/2026-10-28-seguridad-1.3.0-traspaso.md:143` | «**Limpiar foto:** una JPEG y una HEIC de 200 MP, si hay un móvil que las haga; capturas de pantalla; …» (el resto, igual) |
| `docs/superpowers/handoff/sdd-1.3.0/task-5-report.md:331` | «Add to the phone checklist: a full-resolution 200 MP JPEG and HEIC, if a phone at hand takes them, in Tachar and in Limpiar foto (HEIC).» |

### R-4. El servicio en la nube del dueño (opcional)

- **Dónde:**
  - `app/src/main/java/app/seguridad/ui/screens/VaultBackup.kt:205`;
  - `docs/auditorias/2026-10-31-auditoria-1.3.0.md:468`;
  - `docs/superpowers/handoff/sdd-1.3.0/progress.md:356`.
- **Por qué:** dicen en qué servicio en la nube guardaba el dueño sus copias en la prueba del 1-11-2026. Es un servicio muy común y el riesgo es pequeño, pero la regla habla de «las apps que usa». Si el dueño quiere aplicarla al pie de la letra:
  - se escribe «una carpeta en la nube» en lugar del nombre del servicio, en los dos documentos;
  - en el comentario de código, `(owner's phone test, 1-11-2026)` pasa a `(phone test, 1-11-2026)`.

### R-5. Una versión que se quedó atrás

- **Dónde:** `app/src/test/resources/import/README.txt:30`.
- **Cambio:** «Cuando lleguen exportaciones reales (1.4.1)» pasa a «Cuando lleguen exportaciones reales (1.4.2)». Así coincide con `CLAUDE.md:77` y con el traspaso, §1.

## 4. Lo revisado sin cambios

- **Política de privacidad** (`docs/privacidad.html`). La 1.4.1 lee dos columnas más de un archivo que el usuario elige, todo en el móvil. Las cubre `:37`, que dice que la app lee el archivo en el móvil y no lo envía a ningún sitio. No hay datos nuevos, destinatarios, permisos, conexiones ni plazos. No hay tratamiento por un responsable (la app no recibe datos), así que los arts. 13 y 14 RGPD no piden nada más. La Política de datos de usuario de Google Play tampoco: la sección «Seguridad de los datos» sigue en «No recoge ni comparte datos».
  - La fecha (`:22`, «10 de noviembre de 2026») no cambia, porque el texto no cambia (`:68`). El traspaso lo dice bien (`:14`).
- **Ficha de Google Play** (`docs/ficha-google-play.md`). `:28` («los archivos CSV de los navegadores más usados y las exportaciones de algunos gestores…») y `:61` siguen siendo ciertos y no nombran otras apps. No cambia.
- **Palabras prohibidas:** ninguna en los textos nuevos o cambiados: README, traspaso, lista de fallos, `.maestro/README.md` y `CLAUDE.md`. «Limpiar foto» es el nombre de la herramienta, como en las versiones anteriores, y «copia limpia» (`sdd-1.3.0/ingeniero-movil-1-11.md`) es un texto interno anterior. «Cartera»: sin cambios.
- **Nombres de otras apps:**
  - la línea nueva del README (`:27`) está debajo de su línea de marcas (`:33`);
  - las notas de GitHub y las «Novedades» no nombran ninguna app;
  - «Chrome» y «el navegador del fabricante» de P-27 (`README.md:366`, `.maestro/README.md:90`) quedan bajo la línea de marcas (`README.md:376`);
  - la lista de fallos nombra `login_totp` de Bitwarden como referencia técnica, lo que permiten los documentos técnicos.
- **`.maestro/README.md`:** la nota de Windows y «Lo que enseñó la primera ejecución» son instrucciones técnicas exactas. Dicen que `maestro hierarchy` y `adb` leen el texto de la pantalla con la depuración USB, lo mismo que la lista de fallos (`:41`), y es un aviso útil y verdadero.
- **Datos personales en el árbol** (`git grep` en `HEAD`):
  - **Correo personal del dueño:** no aparece en ningún archivo. Solo queda como autor de las seis fusiones antiguas, como ya está decidido.
  - **Nombre real y usuario del ordenador:** no aparecen. Las rutas `/home/user/…` son del contenedor de la nube, y `C:\Users\ana` es un dato de prueba.
  - **Modelo del móvil y del ordenador:** no aparecen. Las búsquedas solo coinciden con palabras de las listas BIP-39 y SLIP-0039.
  - **El correo `seguridad.egt17baa@gmail.com`:** es el del proyecto, publicado a propósito (política, `SECURITY.md`, `docs/PUBLICAR.md`).
  - **WhatsApp, Telegram y Gmail:** salen en las pruebas como destinos para compartir, no como apps del dueño.
  - **Lo que queda:** lo de O-2, R-3 y R-4.
- **Lo que mandó el dueño para depurar:** no hay en el repositorio estructuras de pantalla de Maestro, registros ni capturas (`git ls-files`), como pide `CLAUDE.md:169`.

## 5. Cómo se aplican

Según `CLAUDE.md`, punto 4, `experto-seguridad-cripto` y el Ingeniero Móvil Senior comprueban antes que ningún cambio rompa un formato, cambie el comportamiento o afirme algo falso del código. En O-2, R-3 y R-4 solo cambian comentarios. O-1 es un paso del dueño en la fusión. Hay que aplicarlos antes de abrir o fusionar el PR, porque lo que entre en la rama llega a `main` con la fusión «Squash and merge».

*Borrador profesional para la revisión del responsable. No sustituye el asesoramiento de un abogado colegiado en una jurisdicción concreta.*
