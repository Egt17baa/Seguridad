# Clavero 2.1.1: revisión inicial del Abogado Digital de «las filtradas de Salud se quedan hasta bloquear»

- **Fecha de referencia:** 24-11-2026.
- **Objeto:** el añadido del diseño `docs/superpowers/specs/2026-11-24-clavero-2.1.1-barra-design.md`, sección «Añadido (dueño, 24-11-2026): las filtradas de «Salud» se quedan hasta bloquear» (tarea 5). Es el **visto bueno del principio** (`CLAUDE.md`, «Skills: siempre juntas», punto 4). Todavía no hay código. Los textos exactos se vuelven a mirar en la revisión final.
- **Skills:** `despacho-digital`; criterios de `privacidad-proteccion-datos`.
- **Leídos:** `CLAUDE.md`; el añadido del diseño (líneas 112-140); mi dictamen anterior `sdd-2.1.1/abogado-2.1.1-diseno.md` (formato); `docs/privacidad.html:22, 48, 59-61 y 65`; `docs/ficha-google-play.md:53 y 115`; `README.md:27 y 270`; `app/src/main/java/app/seguridad/ui/screens/HealthScreen.kt:68-71, 86-104 y 192-257`.
- **Hechos que doy por ciertos** (los del diseño): el resultado vive solo en la memoria del proceso, en un objeto de `AppContainer`; guarda el identificador de cada acceso, su número de apariciones y la fecha de su último cambio de contraseña, nunca la contraseña ni su hash; se olvida al cambiar la época del bloqueo (bloqueo por inactividad, al salir de la app o a mano), al cambiar de bóveda y si Android cierra la app; nada va a disco, a un `Bundle` ni a `rememberSaveable`; no hay consultas nuevas a la red ni cambia lo que se envía; las misiones siguen sin usar las filtradas (O-6 de la 2.0). Si alguno cambia, esta revisión no vale para lo que cambie.

## 1. Veredicto

**Se puede programar con cambios.** Tres obligaciones (O-1 a O-3) y tres recomendaciones (R-1 a R-3). Ninguna toca el diseño técnico: son de textos. La importante es O-1: el texto nuevo «Se guarda hasta que bloquees la bóveda.» contradice la línea que ya está en la pantalla («el resultado no se guarda», `HealthScreen.kt:196`) y la política («Nada se guarda», `docs/privacidad.html:60`).

## 2. Normas y criterios

- **RGPD (Reglamento (UE) 2016/679):** arts. 4.2 y 4.7 (tratamiento y responsable), 5.1.c) (minimización), 13 (información) y 25 (protección desde el diseño), como criterio. El desarrollador no recibe ni puede ver el resultado: lo trata la app en el móvil del usuario, para el usuario. No hay tratamiento nuevo por parte del desarrollador ni encargado nuevo.
- **LSSI (Ley 34/2002), art. 22.2, y Directiva 2002/58/CE, art. 5.3:** solo afectan al almacenamiento o al acceso a información en el equipo terminal. Aquí no se escribe nada en el almacenamiento del móvil (solo memoria del propio proceso), y aunque se escribiera sería estrictamente necesario para el servicio que el usuario pide expresamente («Comprobar filtraciones»). No aplican obligaciones nuevas.
- **Normativa de consumo y de servicios digitales (Directiva (UE) 2019/770, TRLGDCU):** no cambia nada (gratuita, sin contraprestación en datos).
- **Regla de veracidad del proyecto:** ningún texto publicado afirma algo falso sobre el código. Es lo que obliga aquí.
- **M-3 (texto mínimo):** una o dos líneas a la vista; lo demás en «Más información».

## 3. Análisis

### 3.1 ¿Cambia algo de la privacidad?

- **El dato es delicado aunque no salga del móvil:** saber qué cuentas tienen una contraseña filtrada orienta a quien quiera atacarlas. El diseño lo trata bien: solo memoria, hasta el bloqueo, y guarda lo mínimo (identificador, número y fecha del cambio; ni contraseña ni hash). Cumple la minimización (art. 5.1.c) y la protección desde el diseño (art. 25) como criterio.
- **Exposición nueva:** el resultado vive más tiempo (antes, mientras «Salud» estaba en pantalla; ahora, mientras la bóveda esté abierta). Con la bóveda abierta, quien tenga el móvil ya ve las entradas y las contraseñas; ver además qué accesos están filtrados no añade un riesgo relevante. `FLAG_SECURE` sigue.
- **Red:** igual que antes. No hay consultas nuevas, ni automáticas, ni repetidas. El texto de la red de la política (`:59-61`) y la ficha (`:53`, `:115`) siguen siendo ciertos en lo que dicen de la conexión.
- **Conclusión:** no hay obligación legal nueva (RGPD, LSSI ni otras). Lo que cambia es la **veracidad** de tres textos que hoy dicen que el resultado «no se guarda».

### 3.2 La palabra «guarda»

- En la política, «guardar» y «apuntar» se usan para lo que queda **en el móvil** (`:48`: «apunta en el móvil, en su almacenamiento privado»), y «recordar en la memoria» para lo que se olvida al bloquear (`:48`: «Solo la recuerdan en la memoria de la app mientras la bóveda está abierta, y la olvidan cuando la bóveda se bloquea…»). Es un criterio claro y conviene seguirlo.
- El texto propuesto, «Se guarda hasta que bloquees la bóveda.», usa «guarda» para memoria. Al lado de «el resultado no se guarda» (`HealthScreen.kt:196`), el usuario lee dos afirmaciones contrarias, y ninguna dice que nada llega al almacenamiento del móvil, que es justo lo que tranquiliza. O-1.

### 3.3 ¿Es veraz y suficiente «hasta que bloquees la bóveda»?

- **«Bloquees»** sugiere que solo lo hace el usuario. También se olvida con el bloqueo por inactividad y al salir de la app, que no son actos suyos: mejor «hasta que la bóveda se bloquee».
- **Cambiar de bóveda:** con una sola bóveda abierta a la vez, abrir otra cierra la anterior, pero el usuario no tiene por qué saberlo. Conviene decirlo: «o abras otra».
- **Android cierra la app:** al volver, la bóveda está bloqueada; queda dentro de «se bloquee». No hace falta decirlo en la línea corta; sí en la política, que ya usa esa fórmula (`:48`).
- **Lo que no se dice:** que el resultado está «a salvo», «protegido» o «cifrado» en memoria (no lo está de forma especial), ni que se «borra de forma segura» (la JVM no garantiza borrar la memoria).

### 3.4 Política, ficha y README

- **Política (`docs/privacidad.html:60`):** «Nada se guarda.» se puede leer como «nada queda en el móvil», que sigue siendo cierto, pero es la frase de la consulta y la pantalla dirá ahora que la app recuerda el resultado. Para que la política y la pantalla digan lo mismo, se precisa (O-2). Al cambiarla, la fecha de «Última actualización» pasa a la del día del cambio (hoy, 24 de noviembre de 2026; si se cambia otro día, ese).
- **Ficha (`:53`, `:115`):** no dicen nada de guardar el resultado. **No cambian.**
- **README (`:27`):** describe «Salud»; R-1. La prueba de `:270` («al volver, no quedan resultados») sigue siendo cierta, porque ahí la bóveda se bloquea.
- **KDoc de `HealthScreen` (`:68-71`):** dice que los resultados «are gone when it closes». Dejará de ser cierto (O-3).

## 4. Obligaciones (O)

**O-1. Los textos de la pantalla no se contradicen y no dicen «se guarda».**
- Sustituir la línea explicativa (`HealthScreen.kt:195-196`) por:
  > «La comprobación envía solo los 5 primeros caracteres de la huella SHA-1 de cada contraseña a Have I Been Pwned; la comparación se hace en tu móvil. Solo se ejecuta cuando la pulsas.»
- Y en lugar de «Se guarda hasta que bloquees la bóveda.», la línea bajo el resultado:
  > «El resultado no se guarda en el móvil: la app lo recuerda hasta que la bóveda se bloquee o abras otra.»
- Si el implementador prefiere dejar el aviso en un solo sitio, vale también poner esa frase al final de la línea explicativa y no añadir otra; lo que no vale es que convivan «no se guarda» y «se guarda».
- *Base:* regla de veracidad; M-3.

**O-2. La política dice lo mismo que la pantalla.**
- En `docs/privacidad.html:60`, sustituir «Nada se guarda.» por:
  > «Nada de esto se guarda en el móvil: el resultado de «Comprobar filtraciones de los accesos» (qué accesos aparecen en filtraciones y cuántas veces) solo lo recuerda la app en su memoria mientras la bóveda está abierta, y lo olvida cuando la bóveda se bloquea, cuando abres otra o cuando Android cierra la app. El de «¿Filtrada?» se olvida al salir de esa pantalla.»
- La última frase solo si es cierta en el código de la 2.1.1 (hoy «¿Filtrada?» borra el resultado al volver atrás y con la regla de «PIN y códigos»); si no, el implementador la ajusta a lo que haga y la anota para la revisión final.
- Actualizar la fecha de «Última actualización» (`:22`) al día del cambio.
- *Base:* regla de veracidad; art. 13 RGPD y art. 5.1.a) (transparencia) como criterio.

**O-3. El KDoc de `HealthScreen` dice dónde vive el resultado.**
- Cambiar «Leak results live only in this screen's memory: they are never saved or logged, and are gone when it closes.» (`HealthScreen.kt:70`) por una frase que diga que viven solo en memoria, en el objeto de sesión de `AppContainer`, nunca en disco ni en registros, y que se olvidan con el bloqueo, al abrir otra bóveda o con la muerte del proceso.
- *Base:* regla de veracidad (los comentarios también describen el código a quien audita).

## 5. Recomendaciones (R)

**R-1. README.** En `README.md:27`, después de «nunca lo hace por su cuenta», añadir: «Desde la 2.1.1, el resultado se recuerda en memoria hasta que la bóveda se bloquea (nunca en el móvil), para ir arreglando los accesos de uno en uno.» Y en «Pruebas de la 2.1.1», la casilla del diseño, añadiendo: abrir otra bóveda también lo olvida.

**R-2. Lo cancelado.** El diseño guarda también una comprobación cancelada. Que la línea del mensaje siga diciendo «Comprobación cancelada (N de M).» mientras se vea ese resultado, para que nadie crea que un acceso no comprobado está a salvo. Y un acceso añadido después de la comprobación no debe aparecer como «sin filtrar»: el diseño ya dice que «no está comprobado»; que la pantalla no afirme lo contrario.

**R-3. Notas de la versión.** En las «Novedades» de Play y las notas de GitHub de la 2.1.1, si se menciona: «En «Salud», el resultado de «Comprobar filtraciones» se mantiene mientras la bóveda está abierta, para ir arreglando los accesos uno a uno. No se guarda en el móvil.» Sin «seguro», «protegido» ni «borrado seguro».

## 6. Lo que no hay que cambiar

- La ficha de Google Play (`:53`, `:115`) y la sección de datos de Play («No recoge ni comparte datos»).
- Los textos de la red de la política (`:59-61`, `:65`).
- El diseño técnico: memoria del proceso, época del bloqueo, ni contraseña ni hash, misiones sin filtradas, sin consultas nuevas.

## 7. Límites de esta revisión

- No hay código: la redacción final y si «¿Filtrada?» sigue olvidando su resultado como hoy se miran en la revisión final.
- Es un borrador profesional para el proyecto; no sustituye el criterio de un abogado colegiado si llega una reclamación.

## Textos para el Abogado Digital (revisión final)

- La línea explicativa y la línea bajo el resultado de «Salud» (O-1).
- `docs/privacidad.html:60` y su fecha (`:22`) (O-2).
- El KDoc de `HealthScreen` (O-3), solo para confirmar que es cierto.
- `README.md:27` y la casilla de «Pruebas de la 2.1.1» (R-1).
- Los mensajes de comprobación cancelada y de accesos sin comprobar, si cambian (R-2).
- Las «Novedades» de Play y las notas de GitHub de la 2.1.1 (R-3).
