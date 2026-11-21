# Clavero 2.0: revisión del director del repaso final de documentos de la tarea 12 (21-11-2026)

**Pregunta:** ¿es verdad y está completo, frente al código de hoy, el repaso final de documentos (`e5a6456` … `7b4d4e8`, informe `sdd-2.0/docs-review-report.md`) y la línea del registro de `66a6617`?

**Cómo:** solo lectura y sin Gradle (hay un clon limpio corriendo), sobre `66a6617` en la rama `ccr-0aa27f95-u038cd`. He leído `CLAUDE.md`, el parte del 20-11 (§3), `progress.md`, el informe del repaso, el `git diff 56d5751..66a6617` entero, la §6, la §7, la §10 y la §11 de la auditoría, `final-audit-b.md` (B-2 y B-1) y `final-audit-d.md` (§1 a §3 y la re-lectura de B-2), y he comprobado cada afirmación nueva contra el código que cita. `git status` limpio antes de escribir este informe; no toco nada más.

**Veredicto:** Aprobado con cambios. El repaso es fiel al código en casi todo, pero dos frases sobre B-2 prometen más de lo que hace `taskAffinity=""`, el traspaso deja que el parte (anterior a B-2) mande sobre la puerta del APK, y `CLAUDE.md`, el registro y `pendientes.md` tienen datos inexactos o incompletos que se arreglan sin tocar el código.

**Motivos:**
- Todos los textos citados en las pruebas nuevas del README (`README.md:416-423`) son las constantes del código, letra a letra: `LIST_NO_MEMORY` (`vault/Vault.kt:301`), `VAULT_NO_MEMORY` (`:291`), `COPY_NO_MEMORY` (`vault/BackupCheckTexts.kt:108`), `UPGRADE_COPYING` (`vault/VaultStore.kt:1757-1758`), `UPGRADE_AUTO_COPY_NOT_DONE` (`ui/screens/VaultUpgrade.kt:58-60`, enseñado en `:132`), `VAULT_NEW_NEEDS_OPEN` (`vault/VaultsTexts.kt:28`, en `ui/screens/VaultPickerScreen.kt:223`) y `ATTACHMENTS_COUNT_WOULD_PASS` (`vault/Attachments.kt:52-54`). Las cifras también: 120 s (`vault/PendingUpgrade.kt:14`), 2 min de `AWAY_BACKSTOP_MS` (`vault/VaultStore.kt:1821`), 1 998 = `BACKUP_MAX_ENTRIES` (2 000, `vault/Backup.kt:62`) − 2 (`vault/Attachments.kt:60-61`).
- **B-2 se promete de más en las notas** (`2026-11-15-clavero-2.0-notas.md:24`): «otra app ya no puede poner una pantalla suya en lugar de la de Clavero» es absoluto, y `taskAffinity=""` solo corta la variante que usa la afinidad. La propia re-lectura de la auditoría dice que la otra variante solo la cierran los parches del sistema (`final-audit-d.md:404`) y que en Android 8 y 9 una app ya podía sacar una pantalla falsa desde segundo plano (`:419`). Lo mismo, en técnico, en `README.md:424`.
- **El traspaso hace mandar al parte sobre B-2** (`2026-11-12-clavero-2.0-traspaso.md:3`: «donde difieran, vale el parte»): el parte es del 20-11, no nombra B-2 y su paso 8 pide «el manifiesto y los permisos iguales a los de la 1.4.3» (`2026-11-20-clavero-2.0-parte.md`, §3, paso 8), mientras la decisión del dueño del 21-11 (`progress.md:285`) y la auditoría (§7, paso 6, `docs/auditorias/2026-11-21-auditoria-2.0.md:395`) aceptan `taskAffinity`. Quien siga el traspaso al pie de la letra suspendería la puerta.
- **`CLAUDE.md` no es fiel en cinco puntos:** la fase dice aún «en construcción» (`CLAUDE.md:39`) bajo un título «casi terminada»; «los cambios esperan» (`:64`) cuando un guardado se rechaza (`vault/VaultStore.kt:315`, `:1771`); «(Ruling, R5)» (`:72`) es una regla de la 1.4.0, y lo de la 2.0 es la fila 61; los riesgos con nombre (`:79`) omiten las filas 54 y 57, que sí están en la §6 de la auditoría (`:340-341`) y en el informe del ataque (`docs/auditorias/2026-11-17-ataque-2.0.md:8`); los rangos de commits de la tabla (`:56`) cortan la ronda en `539d2fd` y los menores en `e68601b`, cuando el registro dice `a94455a` y `56d5751`.
- **Bien:** la fila 80 está bien cerrada (la parte D: «No hay nada Crítico, Alto ni Medio», `final-audit-d.md:45`; D-1 a D-4 en las filas 89, 95, 94 y 96, con D-3 = RC-5 en la 94); las filas 105 (los 12 commits de los menores, `a76d355` … `e68601b`, más el informe `56d5751`) y 106 (cada prueba con su línea del README); la nota de B-4 del diseño (`specs/2026-11-12-clavero-2.0-design.md:127`) dice lo que decidió el Ruling (`advisor-t12-audit-fixes.md:58-73`); RT-25 con `BackupCheck.kt:258` (el `throw CryptoFailure(COPY_DAMAGED)`), `:237` y `:256`, comprobados; el paso 6 de la puerta, con `build-apk.yml:112` y `:324` (y `:315`, `VERSION_CODE: ${{ github.run_number }}`), y el `versionCode` 1 por defecto en la 2.0 y en la 1.4.3 (`app/build.gradle.kts:34`; `git show a3ac942:app/build.gradle.kts`, línea 34).

**Riesgos o dudas:**
- No he ejecutado nada. Los 342 tests de textos del informe del repaso no los he visto; los cambios que pido no tocan ninguna frase fijada por `PublishedTextsTest.theTwoStepCopyIsSaidWhereItHappens` (`app/src/test/java/app/seguridad/tools/PublishedTextsTest.kt:138-157`), y los textos nuevos que propongo no llevan palabras de `ForbiddenWords` ni nombres de otras apps.
- Los documentos son entradas de los tests de las dos suites (`app/build.gradle.kts:235-253`, `jvm-check/build.gradle.kts:56-75`). El repaso solo pasó el filtro de textos de la app; el último clon limpio de la puerta tiene que ser de un commit con los documentos finales.
- La app de prueba de afinidad de B-2 no está en el repositorio (`final-audit-b.md:88` la proponía en `scripts/test-apps/afinidad/`); la prueba del README depende de que el dueño o la tarea 11 la hagan.
- Las dos líneas de atribución de cada commit nombran el modelo de la sesión. La parte C las dejó fuera de la regla a propósito (`final-audit-c.md:113`, `:213`), y C-2 solo trata los asuntos. No pido nada.

## 1. Veredicto por documento

| Documento | Veredicto | Cambios |
|---|---|---|
| `README.md` (`:28`, `:397`, `:407`, `:415-424`) | Aprobado con cambios | O-1, O-7; menores M-1 a M-4 |
| `docs/superpowers/handoff/2026-11-15-clavero-2.0-notas.md` | Aprobado con cambios | O-2 |
| `docs/superpowers/handoff/2026-11-12-clavero-2.0-traspaso.md` | Aprobado con cambios | O-3; menores M-5 y M-6 |
| `docs/superpowers/handoff/sdd-2.0/pendientes.md` | Aprobado con cambios | O-7 (filas 56 y 69). La fila 80, bien cerrada; la 105 y la 106, bien |
| `CLAUDE.md` (estado, tabla y «Decisiones de la 2.0») | Aprobado con cambios | O-4, O-5; menor M-7 |
| `docs/superpowers/specs/2026-11-12-clavero-2.0-design.md:127` (nota de B-4) | Aprobado | — |
| `docs/auditorias/2026-11-17-ataque-2.0.md:125`, `:268` (RT-25) | Aprobado | — |
| `docs/auditorias/2026-11-21-auditoria-2.0.md` (§7, pasos 3 y 6; citas de `README.md:424`) | Aprobado con cambios | O-8 (una cita que se movió con el repaso); recomendación R-2 |
| `docs/superpowers/handoff/sdd-2.0/progress.md` (líneas de `66a6617`) | Aprobado con cambios | O-6 |
| `docs/superpowers/handoff/sdd-2.0/docs-review-report.md` | Aprobado | Recomendación R-3 |
| Nombres de modelo y datos del dueño en lo que cambió | Aprobado | Ninguno: la búsqueda con los nombres de la tabla de la skill `advisor` sobre el `git diff 56d5751..66a6617` y sobre los diez archivos da 0; ni correos, ni móviles, ni rutas de usuario |

## 2. Cambios obligatorios (veracidad y completitud)

**O-1. `README.md:424`, el final de la línea «versión y permisos» (B-2).** `taskAffinity=""` no impide toda pantalla ajena en la tarea, solo la que entra con la misma afinidad (`final-audit-b.md:63-65`, `:80`; `final-audit-d.md:404`).
- Donde dice: «…(auditoría de la 2.0, B-2, decisión del dueño del 21-11-2026), que impide que otra app meta una pantalla suya en la tarea de Clavero.»
- Que diga: «…(auditoría de la 2.0, B-2, decisión del dueño del 21-11-2026), que impide que otra app meta una pantalla suya en la tarea de Clavero declarando su misma afinidad (la otra variante conocida de ese ataque solo la cierran los parches de seguridad de Android).»

**O-2. `2026-11-15-clavero-2.0-notas.md:24` y `:55` (B-2, texto publicado).** Es la frase que más importa: va a las notas de GitHub.
- `:24`, donde dice: «Seguridad: al abrir Clavero desde su icono, otra app ya no puede poner una pantalla suya en lugar de la de Clavero (sobre todo en Android 8 a 10). [auditoría B-2]»
- Que diga: «Seguridad: se cierra una forma conocida en que otra app instalada podía hacer que, al tocar el icono de Clavero, saliera una pantalla suya (en Android 8 a 10). [auditoría B-2]»
- `:55`, donde dice: ««Seguridad: … otra app ya no puede poner una pantalla suya…» (B-2; no debe prometer más que eso ni dar versiones de Android como garantía)»
- Que diga: ««Seguridad: se cierra una forma conocida…» (B-2; tiene que seguir diciendo «una forma», sin prometer que ninguna app pueda ya enseñar una pantalla suya, y sin decir nada de Android 11 o posterior)»
- El Abogado puede redactarla de otra manera con esas dos condiciones. «En Android 8 a 10» es lo confirmado (`final-audit-d.md:409`); de Android 11 o posterior la línea no dice nada.

**O-3. `2026-11-12-clavero-2.0-traspaso.md:3`, la precedencia del parte.**
- Donde dice: «**Parte al día (20-11-2026): `2026-11-20-clavero-2.0-parte.md`, que sustituye a los anteriores; donde difieran, vale el parte. Este traspaso se puso al día el 21-11-2026, en el repaso final de documentos de la tarea 12.**»
- Que diga: «**Parte al día (20-11-2026): `2026-11-20-clavero-2.0-parte.md`, que sustituye a los anteriores; donde difieran, vale el parte, salvo lo decidido después en el registro (`sdd-2.0/progress.md`): sobre todo B-2 (`android:taskAffinity=""`, decisión del dueño del 21-11-2026) y la puerta del APK de la §7, paso 6, de `docs/auditorias/2026-11-21-auditoria-2.0.md`. Este traspaso se puso al día el 21-11-2026, en el repaso final de documentos de la tarea 12.**»

**O-4. `CLAUDE.md:39`, la línea de la fase.** Tres cambios en la misma línea:
- «- **2.0 en construcción** (rama» → «- **2.0 casi terminada** (rama»;
- «parte vigente `docs/superpowers/handoff/2026-11-20-clavero-2.0-parte.md` (sustituye a los anteriores).» → «parte vigente `docs/superpowers/handoff/2026-11-20-clavero-2.0-parte.md` (sustituye a los anteriores; lo decidido después en `progress.md`, como B-2 y la puerta del APK de la §7, paso 6, de la auditoría, vale sobre él).»;
- «Estado y decisiones, abajo («Pasos de la 2.0»).» → «Estado y decisiones, abajo («Paso de la 2.0» y «Decisiones de la 2.0»).»

**O-5. `CLAUDE.md`, tabla y «Decisiones de la 2.0».**
- `:54`: «(la 3.ª sin hallazgos)» → «(la 3.ª sin hallazgos nuevos)». La re-comprobación 3 dio un informativo, I-1 (`progress.md:276`).
- `:56`: «ronda de arreglos (`7505281` … `539d2fd`), sus repasos y sus menores (`a76d355` … `e68601b`)» → «ronda de arreglos (`7505281` … `a94455a`), sus repasos y sus menores (`a76d355` … `56d5751`)». Así lo dice el registro (`progress.md:287`, `:290`).
- `:64`, donde dice: «Mientras se hace esa copia, los cambios esperan (`UPGRADE_COPYING`, CR-2), como mucho 120 s (D-1, `UPGRADE_AUTO_COPY_NOT_DONE`).»
  Que diga: «Mientras se hace esa copia, un guardado se rechaza con `UPGRADE_COPYING` y las copias automáticas esperan a que acabe (CR-2). La subida espera esa copia como mucho 120 s y después pregunta, con `UPGRADE_AUTO_COPY_NOT_DONE` (D-1); la copia sigue y, si acaba tarde, no se anuncia como la de la subida.»
  Evidencia: `vault/VaultStore.kt:315` y `:1771` (el guardado lanza el texto), y `vault/PendingUpgrade.kt:109` y `:122` (la espera y la bandera).
- `:72`: «(Ruling, R5)» → «(R5 de la 1.4.0, ampliada por el Ruling del director en el cierre de la ronda 2 del ataque, fila 61)».
- `:79`: «y las filas 51, 58, 62, 67, 69, 83 y 104 de `pendientes.md`.» → «y las filas 51, 54, 57, 58, 62, 67, 69, 83 y 104 de `pendientes.md` (la tabla de la §6 de la auditoría de la 2.0).»

**O-6. `progress.md:287` (la línea de la ronda en `66a6617`).** `git rev-list --count 7505281^..a94455a` da 23, no 21, y no solo el punto 3 va en varios commits.
- Donde dice: «hecha en 21 commits, de `7505281` a `a94455a` (un commit por punto, con su rojo antes; el punto 3 en tres: `d9e341a`, `18adc17`, `539d2fd`).»
- Que diga: «hecha en 23 commits, de `7505281` a `a94455a`: un commit por hallazgo, con su rojo antes (el punto 3 en tres, `d9e341a`, `18adc17` y `539d2fd`, y los puntos 12, 13 y 15 en dos cada uno), y el último, el informe.»

**O-7. Las filas 56 y 69 sin su línea del README.** El informe del repaso dice que cada fila con algo para el móvil apunta a su línea del README, pero estas dos no la tienen.
- **`README.md:416`:**
  - «(filas 55, 58 y 62, con la 51)» → «(filas 55, 58, 62 y 69, con la 51)».
  - Al final de la línea, después de «(AC-E1, fila 83).», añadir: « Con «Importar de otra app» y un archivo grande en ese móvil: si falta memoria al guardar, la app puede cerrarse; anotarlo, y comprobar que la bóveda sigue como estaba y que la copia de antes de importar se queda donde se guardó (fila 69).»
  - Evidencia: fila 69 y §6 de la auditoría (`:345`).
- **`README.md:415`:**
  - Al final de la línea, añadir: « Abrir la ficha de una entrada con una nota con formato muy larga (cerca de los 64 KB de una entrada) tampoco hace esperar (fila 56).»
  - Evidencia: fila 56, «una nota antigua enorme en `MarkupText` (al móvil, con la fila 3)»; `redteam-report.md:241`, `:314`.
- **`pendientes.md:64`** (fila 56), su estado: «Aceptado / móvil» → «Aceptado / móvil: la nota enorme, en el móvil (README, «Pruebas de la 2.0», «rendimiento (fila 3)»)».
- **`pendientes.md:77`** (fila 69), su estado: «Aceptado, con las 55 y 58» → «Aceptado, con las 55 y 58: en el móvil (README, «Pruebas de la 2.0», «memoria con una bóveda grande»)».

**O-8. `docs/auditorias/2026-11-21-auditoria-2.0.md:493`.** El repaso puso dos líneas en las notas (`:23-24`) y la frase de B-4 pasó de `:28` a `:30`. El repaso movió las citas de `README.md:416` a `:424`, pero no esta.
- Cambio: «Las notas (`2026-11-15-clavero-2.0-notas.md:28`)» → «Las notas (`2026-11-15-clavero-2.0-notas.md:30`)».

## 3. Menores (se arreglan, regla del proyecto)

**M-1. `README.md:418` (B-2).** La parte B pedía comprobar también «Al salir» (`final-audit-b.md:82`).
- Donde dice: «Después, el icono, la lista de recientes, los selectores, el escáner, «Compartir» y la pantalla dividida siguen igual que antes.»
- Que diga: «Después, el icono, la lista de recientes, los selectores, el escáner, «Compartir», la pantalla dividida y el bloqueo «Al salir» siguen igual que antes.»

**M-2. `README.md:420`.** «(auditoría, A-1 y B-3)» → «(auditoría, A-1 y B-3, fila 87)», como las demás pruebas nuevas.

**M-3. `README.md:423` (fila 84).** «No tiene otro tope de número en la pantalla» no dice qué hacer, y la bóveda de prueba no se puede montar a mano adjunto a adjunto. El borde exacto es el de `AttachmentsTest.kt:174` (con 1 997 cabe uno más; con 1 998, no).
- Donde dice: «con una bóveda de prueba cerca de 1 998 adjuntos (contando los de la papelera), «Añadir adjunto» no tiene otro tope de número en la pantalla, y el adjunto que pasaría de 1 998 se rechaza con»
- Que diga: «con una bóveda de prueba de 1 997 adjuntos pequeños (contando los de la papelera; se monta importando una copia de prueba hecha a mano, con menos de 50 MB entre todos), «Añadir adjunto» añade uno más sin otro tope en la pantalla, y el siguiente, que pasaría de 1 998, se rechaza con»

**M-4. `README.md:28`.** «Como mucho 1 998 por bóveda» no es verdad para una bóveda que llegó de la 1.4.3 con más (riesgo con nombre de la fila 104). El tope está al añadir (`vault/VaultStore.kt:1381`).
- Donde dice: «desde la 2.0, como mucho 1 998 por bóveda, los que caben en una copia, contando los de la papelera)»
- Que diga: «desde la 2.0, «Añadir adjunto» no deja pasar de 1 998 por bóveda, los que caben en una copia, contando los de la papelera)»

**M-5. `2026-11-12-clavero-2.0-traspaso.md`, fila de la tarea 12.** La lista de la ronda omite A-2, D-3, E-2 y E-3, que también se arreglaron (`0b01405`, `e629906`, `0ae9409`, `3399f10`).
- Donde dice: «(B-2, B-1, A-1 con B-3, D-1, D-2, D-4, E-1, la fila 84, B-4, RC-1 a RC-6, C-1, C-5, C-7 y A-3)»
- Que diga: «(B-2, B-1, A-1 con B-3, A-2, D-1, D-2, D-3 (con RC-5), D-4, E-1, E-2 (con RC-2), E-3 (con RC-6), la fila 84, B-4, RC-1 a RC-6, C-1, C-5, C-7 y A-3)»

**M-6. `2026-11-12-clavero-2.0-traspaso.md`, «Siguiente paso», punto 4.** Le falta la condición de la CI del parte (§3, paso 9), y la frase de la copia se lee al revés.
- Que diga: «4. El OK del dueño para el PR a `main`, con **build** y **jvm-check** en verde; él lo fusiona sin etiqueta y lanza *Actions → Build APK* desde `main` (con un `versionCode` de 125 o más). Antes de instalar, guarda fuera del móvil una copia de su bóveda hecha con la 1.4.3; después prueba en el móvil (README, «Pruebas de la 2.0»).»

**M-7. `CLAUDE.md`, «Decisiones de la 2.0».**
- `:63`: «(dueño, 13-11, 17-11 y 19-11-2026)» → «(dueño, 13-11, 15-11, 17-11 y 19-11-2026)». La del 15-11 está en `progress.md:216`.
- Después de la línea del nombre cifrado (`:65`), añadir la decisión del logo (`progress.md:90`) y lo que el dueño tiene pendiente de ella (filas 15 y 16):
  «- **Logo y aspecto (dueño, 12-11-2026):** logo nuevo que no se parece a ninguna marca ni app (sin escudo, candado ni cerradura), «Cristal», seis paletas y «Personalizado» (tarea 5 bis, con el visto bueno del Abogado en `sdd-2.0/abogado-2.0-cristal.md`). El icono cambiable (`activity-alias`) sigue fuera hasta que lo apruebe el dueño, y antes de publicar el icono el dueño busca anterioridades del logo y de la marca (O-C1, fila 15).»

## 4. Recomendaciones (no bloquean)

- **R-1. `README.md:397`:** el enlace «[§10](docs/auditorias/2026-11-21-auditoria-2.0.md)» puede llevar su ancla, `…auditoria-2.0.md#10-por-validar-en-el-móvil`.
- **R-2. Auditoría, §11 (`:478`):** las líneas de Kotlin de la §11 son las de `69c967b`, como dice la cabecera (`:14`), y varias ya no son las de hoy: `UPGRADE_COPYING` está en `:1757`, no en `:1748`; `FOLDER_DELETE_TO_NONE` en `:63`, no en `:60`. Al Abogado le sirve una frase al principio de la §11: «Las líneas de Kotlin de esta sección son las de `69c967b`; las de los textos nuevos o cambiados en la ronda de arreglos de la auditoría están en `sdd-2.0/audit-fixes-report.md`, §6.»
- **R-3. `docs-review-report.md:13`:** «B-4 ya estaba (`:28`)» → «(`:30`)». Es un archivo de trabajo; se puede dejar.
- **R-4. `CLAUDE.md`, «Pendientes, del dueño»:** un punto 12 para la 2.0 que junte C-3 (no aprobar una firma que no haya lanzado él), O-C1, el icono cambiable, las pruebas del README y la copia con la 1.4.3 antes de instalar. Hoy eso solo está repartido entre las decisiones y el traspaso.

## 5. Ruling

1. **El repaso se aprueba con cambios.** Lo que dice es verdad frente al código salvo lo de los puntos O-1 a O-8, y la fila 80 queda bien cerrada.
2. **Se aplican O-1 a O-8 y M-1 a M-7 en un solo lote de documentos, antes de lanzar al Abogado Digital.** Puede ser uno o varios commits, del controlador o del implementador del repaso. El Abogado revisa las notas y el README, y no debe recibir la frase de B-2 que promete de más. R-1 a R-4 quedan a juicio del controlador.
3. **Antes del commit**, los tests de textos de la app con el cerrojo (`PublishedTextsTest`, `ImportTextsTest`, `PublishedFilesNameNoModelTest`, `AppNameTest`, `FinalLegalReviewTest`), y la misma búsqueda de nombres de modelo sobre el diff.
4. **No pido otra revisión mía de este lote:** lo compruebo en la puerta del APK, con el resto.
5. **El clon limpio que corre ahora** vale como prueba del estado de hoy, no como el de la puerta. Los documentos son entradas de los tests de las dos suites, y el Abogado cambiará más: el último clon limpio tiene que ser del commit final, con sus cambios aplicados.
6. **Sigue el plan del parte, con B-2 por encima del paso 8:** la revisión final del Abogado (§11 de la auditoría, más las líneas de B-2 de O-1 y O-2), la fecha de la política, el último clon limpio y la puerta del APK.
7. **La ficha de Play (fila 53):** bien no acortarla en el repaso. Es redacción del Abogado con el visto bueno técnico, y tiene que bajar de 4 000 caracteres antes de pegarla en la consola. No bloquea el PR, que no la lleva.

## Revisión experto-seguridad-cripto

- **Veracidad de lo que se promete (CWE-451 en sentido débil):** `android:taskAffinity=""` está solo en `MainActivity` (`app/src/main/AndroidManifest.xml:28`; `PlatformSourcesTest.kt:53-59`). Corta la entrada en la tarea por la afinidad, la técnica de B-2 (`final-audit-b.md:63-65`, `:80`). No corta la variante sin afinidad, que solo cierran los parches del sistema (`final-audit-d.md:404`), ni la pantalla falsa desde segundo plano de Android 8 y 9 (`:419`). De ahí O-1 y O-2: un texto de seguridad no puede decir que «ya no puede» pasar lo que sigue pudiendo pasar por otro camino.
- **A-1 y B-3:** la prueba del README es fiel al código. `BiometricVault.forget` escribe con `edit(commit = true)` y borra el archivo de preferencias salvo el de la primera bóveda (`android/BiometricVault.kt:193-203`, `PREFS` en `:69`; `vault/VaultRegistry.kt:56`). El archivo que la prueba busca, `vault_biometric_<id>.xml`, es el de `perVaultName`.
- **Fila 84:** el tope se mira antes de escribir el adjunto (`vault/VaultStore.kt:1381`, antes de `attachments.add`, `:1383`). Por eso es verdad que el rechazo no deja «nada a medias». El texto nombra 1 998, que es `BACKUP_MAX_ENTRIES` − 2 (`vault/Backup.kt:62`, `vault/Attachments.kt:60-61`).
- **D-1:** la espera es `withTimeoutOrNull(waitMs)` con 120 s (`vault/PendingUpgrade.kt:14`, `:109`). La bandera `autoCopyNotDone` solo se pone si la espera se agotó (`:122`), y la copia tardía nunca se anuncia como la de la subida (`:114-117`). El README y las notas dicen exactamente eso; `CLAUDE.md:64` no (O-5).
- **RT-25:** `copyExtraTags` (`vault/BackupCheck.kt:237`) y `checkCopyEntries` (`:256`), con el rechazo en `:258`; las citas del informe del ataque, al día.
- **C-3:** `CLAUDE.md:79` y el traspaso dicen lo que decidió el dueño (`progress.md:280`): los secretos no se borran hasta que recupere la contraseña del almacén, y mientras tanto él no aprueba ninguna firma que no haya lanzado. El *workflow* sigue sin dar los secretos al trabajo `build` (`.github/workflows/build-apk.yml:44`, `:88-91`).
- **Secretos y datos del dueño:** ninguno en el diff. Los commits son de Egt17baa con la dirección `noreply`. Los nombres de modelo, con la tabla de la skill `advisor`: 0 en lo que cambió.

## Revisión Ingeniero Móvil Senior

- **B-4 (README `:407`, nota del diseño `:127`):** en multiventana, perder el primer plano llama a `leftForAnotherApp()` (`MainActivity.kt:84-86` desde Android 10; `:89-92` antes), y esta llama a `twoStepCopy.markLeft()` (`AppContainer.kt:223-227`). La bóveda sigue abierta y la oferta sale en la ventana de Clavero, también en una ventana flotante (`isInMultiWindowMode`, `MainActivity.kt:102`). A pantalla completa, `onStop` bloquea (`:129`). La prueba de la ventana flotante es correcta.
- **B-1 (README `:419`):** el *intent* se sustituye en `onCreate`, después de `super.onCreate` y antes de `setContent` (`MainActivity.kt:39`), y no hay `onNewIntent`. La orden `adb` usa el `applicationId` sin sufijo de depuración (`app/build.gradle.kts:30`). Vale también con el APK firmado, porque `MainActivity` está exportada; con el de depuración basta.
- **D-2 (README `:422`):** con «Al salir», `lockIfIdle()` corre en `onStart`, antes de que llegue el resultado del selector (`MainActivity.kt:65`; `vault/VaultStore.kt:863-869`), con el tope `AWAY_BACKSTOP_MS` (`:1821`). Por eso los 2 minutos y medio bloquean, y el diálogo no se abre (`ui/screens/VaultPickerScreen.kt:222-223`). El aviso es condicional, como debe.
- **B-2 en el móvil (README `:418`):** con la afinidad vacía, el escáner y los selectores siguen en la tarea de Clavero, porque se abren sin `FLAG_ACTIVITY_NEW_TASK` (`final-audit-d.md:397-400`). Falta comprobar «Al salir» (M-1). La app de prueba no está en el repositorio (Riesgos).
- **Puerta del APK (auditoría §7, paso 6):** en el clon, los dos APK de depuración llevan `versionCode` 1 (`app/build.gradle.kts:34`, igual en `a3ac942`). El firmado lleva el número de la ejecución (`.github/workflows/build-apk.yml:112`, `:315`, `:324`). La comparación con esa excepción es correcta.
- **Rendimiento y memoria (README `:415-417`):** las medidas por `EntryListViewModel` (dos a la vez, la lista y «Códigos») están en las filas 58 y 62. Con O-7, la fila 69 (el guardado de «Importar de otra app» sin captura de `OutOfMemoryError`) y la nota larga de `MarkupText` (fila 56) también tienen su prueba.

## Textos para el Abogado Digital (revisión final)

- **Las notas, `:24` (B-2), con el texto de O-2:** «Seguridad: se cierra una forma conocida en que otra app instalada podía hacer que, al tocar el icono de Clavero, saliera una pantalla suya (en Android 8 a 10).» Condiciones técnicas: «una forma», nada absoluto y nada de Android 11 o posterior.
- **`README.md:28`** (M-4), **`:415-416`** (O-7), **`:418`** (M-1), **`:423`** (M-3) y **`:424`** (O-1).
- Lo demás, como lo dejó el repaso (`docs-review-report.md`, «Textos para el Abogado Digital»), y la §11 de la auditoría.

Modelo: el director (esfuerzo extra).
