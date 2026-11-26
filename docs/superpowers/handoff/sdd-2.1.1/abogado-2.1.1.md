# Clavero 2.1.1: revisión final del Abogado Digital

- **Fecha de referencia:** 26-11-2026.
- **Rama:** `ccr-0aa27f95-u038cd`, en `d35dd91`, frente a la 2.1 publicada (`a29d88c`).
- **Skills:** `despacho-digital` y `privacidad-proteccion-datos` (la política y el resultado de «Salud»). Como criterio, lo ya razonado con `regulacion-digital-ue` (accesibilidad, CRA) y `propiedad-intelectual-ia` (la cápsula y el diseño) en `abogado-2.1.1-diseno.md`; esta versión no añade ningún hecho que obligue a revisarlo.
- **Leídos:** `CLAUDE.md` (punto 4); los dos dictámenes del principio (`abogado-2.1.1-diseno.md`, O-1 a O-7 y R-1 a R-6; `abogado-2.1.1-filtradas.md`, O-1 a O-3 y R-1 a R-3); el modelo `sdd-2.0/abogado-2.0.md`; `final-audit.md` (la auditoría y la «Aprobación del director»); `branch-review.md`; las notas `2026-11-24-clavero-2.1.1-notas.md`; `git diff a29d88c..HEAD` de `docs/privacidad.html`, `docs/ficha-google-play.md`, `README.md` y `app/src/main`.
- **Código mirado:** `health/LeakResults.kt` (entero: `LEAK_RECHECK_NEEDED` en `:23-24`, el KDoc en `:26-46`, `breached` en `:103-113` y `message` en `:212-228`); `ui/screens/HealthScreen.kt:53-54`, `:64-70`, `:177-209`; `ui/screens/AppearanceScreen.kt:95-125`; `ui/theme/Glass.kt:166-167`, `:236`, `:409-420`; `health/Health.kt:33`, `:49`, `:106` (la papelera queda fuera de «Salud»); `FinalLegalReviewTest.kt:45-62` (cómo se lee la fecha de la política).
- **Hechos que da por ciertos:**
  - El autor es un particular sin ánimo de lucro, con el seudónimo Egt17baa, residente en España.
  - La app es gratuita, sin anuncios, compras ni donaciones, de código abierto con licencia Apache-2.0, y no recibe datos de nadie.
  - Su única conexión es la consulta k-anonimizada a `api.pwnedpasswords.com`, solo cuando el usuario la pide.
  - Se publica en GitHub y quizá en Google Play («no comerciante»), fuera de Francia (con ultramar), Rusia y Bielorrusia.
  - Lo propio de la 2.1.1, comprobado por la auditoría y el director y contrastado donde se ha podido: sin dependencias, permisos, componentes exportados ni conexiones nuevas (el manifiesto solo cambia en `versionName` y `versionCode`); ningún formato cambia; el resultado de «Salud» vive solo en la memoria del proceso y guarda el id, el número de apariciones y la marca del último cambio, nunca la contraseña ni su hash (`LeakResults.kt:54`); la cápsula no lee el fondo de pantalla, y `FLAG_SECURE` sigue.

  Si alguno cambia, esta revisión no vale para lo que cambie.

## 1. Conclusión

**Se puede publicar, con cambios.**

El mapa normativo es el de la 2.0 y el de los dos dictámenes del principio:
- El RGPD y la LOPDGDD no se aplican al desarrollador, que no recibe nada ni es responsable de ningún tratamiento (art. 4.7 RGPD). Lo que el usuario hace con la app queda en la excepción doméstica (art. 2.2.c RGPD). Recordar en memoria qué accesos están filtrados es un tratamiento en el móvil del usuario y para él; cumple como criterio la minimización (art. 5.1.c) y la protección desde el diseño (art. 25).
- El art. 22.2 LSSI y el art. 5.3 de la Directiva 2002/58/CE no se aplican: no se escribe nada en el almacenamiento del equipo; aunque se escribiera, sería estrictamente necesario para un servicio que el usuario pide.
- No se aplican, por falta de comerciante, de actividad económica o de objeto: el TRLGDCU, la Directiva (UE) 2019/770, la Directiva 2005/29/CE, la DSA, la Ley 11/2023 y la Directiva (UE) 2019/882 (accesibilidad; WCAG 2.2 es solo guía), el Data Act y el AI Act.
- El CRA (Reglamento (UE) 2024/2847) y la Directiva (UE) 2024/2853 tampoco, mientras no haya actividad comercial. Sus calendarios, incluidas las obligaciones de notificación del CRA, quedan **pendientes de verificar a fecha de hoy** (no se consultó EUR-Lex ni el BOE en esta sesión). No cambian la conclusión.
- Diseño y competencia desleal (RDC, arts. 6, 8.1, 10 y 11; LCD, arts. 5, 6, 11 y 12): lo dicho en el dictamen del principio. Con la opción B (el contenido no pasa por debajo, sin lente ni formas que se funden), el riesgo de imitación es todavía menor.
- Las políticas de Google Play obligan como contrato de distribución: metadatos veraces, sin nombres ajenos, «Novedades» de menos de 500 caracteres. Se cumplen.

Lo que obliga es la **regla de veracidad** del proyecto. Cada texto, comprobado contra el código: todos son ciertos. Hay un obligatorio de calendario (O-1) y una condición ya escrita en las notas (O-2), más una recomendación (R-1) que responde a AD-3 solo con texto. Nada cambia un formato ni el comportamiento.

## 2. Lo comprobado

| Texto | Dónde | Lo que hace el código | ¿Cierto? |
|---|---|---|---|
| `STYLE_MORE` | `AppearanceScreen.kt:104-107` | La cápsula flota en «Cristal», «Vidrio», «Transparente» y, opaca, en «Degradado»; difumina solo `drawAppBackground` desde Android 12 (`Glass.kt:409-420`); opaca con «Reducir transparencias» y «Alto contraste» | Sí. Sustituye bien a su O-6 con la opción B: ya no hace falta «el contenido no se ve por debajo», porque nunca pasa |
| `LEAK_RESULT_KEPT` | `HealthScreen.kt:54`, mostrada en `:206-209` | Nada a disco; se olvida al bloquear, al abrir otra bóveda, al importar una copia y con la muerte del proceso | Sí; dice «hasta», como mucho |
| Línea explicativa de «Salud» | `HealthScreen.kt:178-179` | Ya no dice «el resultado no se guarda» | Sí; cumple su O-1 de las filtradas |
| KDoc de `HealthScreen` | `:64-70` | Memoria, `AppContainer` y los cuatro olvidos | Sí; cumple O-3 |
| KDoc de `LeakResults` | `:26-46` | Sigue la marca, no la contraseña; guardar la misma contraseña no mueve la marca (AD-1) | Sí |
| «Quedan N sin cambiar.» y sus dos variantes | `LeakResults.kt:222-227` | N = `breached.size`: acceso, fuera de la papelera, con contraseña y con la misma marca | Sí (§5, punto 3) |
| `LEAK_RECHECK_NEEDED` | `LeakResults.kt:23-24` | Sale solo si algún acceso hallado ya no cuenta; un cambio no se vuelve a comprobar hasta pulsar otra vez | Sí (§5, punto 6) |
| Política `:60` | `docs/privacidad.html` | Olvidos de «Salud» (con F-2) y de «¿Filtrada?» | Sí |
| Política `:22` | fecha | «24 de noviembre de 2026» | Pendiente: O-1 |
| Política `:61` | «Como en cualquier conexión» | Sin cambios desde antes | Sí; no compara con ninguna app ni sistema |
| README `:27` | «Salud de la bóveda» | «hasta que la bóveda se bloquea o se abre otra (no se guarda en el móvil)» | Sí; cumple su R-1 de las filtradas |
| README `:73` | «Aspecto (2.1.1)» | Como las notas | Sí, con sus límites («desde Android 12», «al menos tanto» con las dos paletas variables) |
| README `:86` | «La 2.1.1» | Formatos, permisos, manifiesto, ajustes y la línea de «Salud» | Sí |
| README `:473-494` | «Pruebas de la 2.1.1» | Cubren O-2 y O-4 del principio y su R-5 (adaptada), además de BR-1 y AD-1 | Sí (§5, punto 1) |
| Ficha `:69` y `:110` | Versión larga, que no se pega | Los cinco estilos, con la barra | Sí; 7 272 caracteres. La descripción que se pega (`:50`) no cambia |
| Notas de GitHub y «Novedades» | `2026-11-24-clavero-2.1.1-notas.md` | 444 caracteres; sin nombres ajenos | Sí; su R-3 de las filtradas, tal cual; su R-4 del principio, rehecha bien para la opción B |
| O-7 (nombres ajenos) | Todo lo publicado | «Material 3 Expressive» solo en el KDoc de `FloatingBar.kt` y en la parte interna de las notas | Se cumple |
| O-3 (la referencia del dueño) | Repositorio | Ni la app ni el móvil de la referencia | Se cumple |

## 3. Cambios obligatorios (O)

**O-1. La fecha de la política es la del día de la fusión.**
- `docs/privacidad.html:22`. Texto actual: «Última actualización: 24 de noviembre de 2026.»
- Texto nuevo, justo antes de la fusión, con el día en que se fusione: «Última actualización: D de noviembre de 2026.» (si se fusiona el 26, «26 de noviembre de 2026»; si es en diciembre, «D de diciembre de 2026»).
- El formato es el que lee `FinalLegalReviewTest.policyDate`: día sin cero a la izquierda y mes en minúsculas. Las notas (`:13`) ya lo dicen.
- *Base:* regla de veracidad; art. 12.1 RGPD como criterio de transparencia.

**O-2. La línea de la barra solo se publica si salen bien las pruebas en el móvil.**
- No es un cambio de texto: confirma la condición de las notas (`:12`).
- La línea «La barra de abajo flota…» (README `:73`, el párrafo de `:86`, las notas de GitHub y las «Novedades») se publica solo si salen bien en el móvil estas casillas de «Pruebas de la 2.1.1»: «nada debajo de la cápsula»; «toques en el borde»; «la cápsula no cambia de forma»; «TalkBack»; «Sólido igual que la 2.1»; «Transparente»; «Reducir transparencias» y «Alto contraste»; «otras paletas».
- Si alguna no sale, se arregla o se quita lo que deje de ser cierto antes de publicar.
- *Base:* regla de veracidad (D-1: aquí ningún test compone la cápsula, F-3 de la auditoría); O-2, O-4 y O-5 del dictamen del principio.

## 4. Cambios recomendados (R)

**R-1 (AD-3). Que la línea bajo el resultado diga que lo añadido o cambiado después no está comprobado. Solo texto.**
- **Motivo:** con el resultado a la vista toda la sesión, «Comprobación terminada: 0 accesos filtrados.» puede leerse como el estado de ahora, y no lo es para un acceso añadido después ni para uno no filtrado cuya contraseña cambió. Ningún texto es falso hoy y su R-2 de las filtradas se cumple, pero en una app de seguridad conviene que nadie deduzca que una contraseña nueva está a salvo. Lo prefiere al cambio de código del director: no toca el comportamiento ni reabre la auditoría, y la frase es cierta siempre que sale.
- `app/src/main/java/app/seguridad/ui/screens/HealthScreen.kt:54`. Texto actual: «El resultado no se guarda en el móvil: la app lo recuerda hasta que la bóveda se bloquee o abras otra.» Texto nuevo: «El resultado no se guarda en el móvil: la app lo recuerda hasta que la bóveda se bloquee o abras otra. Las contraseñas añadidas o cambiadas después no están comprobadas.»
- **Por qué es cierto:** un acceso nuevo no entra en `found`; una contraseña cambiada mueve la marca y sale de `breached` sin volver a comprobarse (`LeakResults.kt:103-113`); guardar la misma contraseña no la «cambia» (AD-1). Con `LEAK_RECHECK_NEEDED` se repite un poco, pero esa solo sale a veces y añade qué hacer; las dos se quedan.
- Con el cambio, `README.md:494`: «bajo el resultado se lee «El resultado no se guarda en el móvil: la app lo recuerda hasta que la bóveda se bloquee o abras otra. Las contraseñas añadidas o cambiadas después no están comprobadas.».» Y el test que fije `LEAK_RESULT_KEPT` y la prueba de palabras prohibidas. La política, la ficha, las notas y las «Novedades» no cambian.
- Si hubiera un problema técnico (por ejemplo, de espacio a 360 dp con letra grande), la frase puede ir al final de la línea explicativa (`:178-179`). Ninguna otra redacción vale sin pasar por él.
- *Base:* regla de veracidad; art. 25 RGPD como criterio; M-3 (línea corta y a la vista, porque es una advertencia).

## 5. Respuestas a los puntos abiertos

1. **R-5 con la opción B.** Acepta la casilla de «Transparente» (`README.md:489`) tal como está y retira el último punto de su R-5, que suponía que el contenido pasaba por debajo. Ningún texto promete ver el fondo de pantalla a través de la barra: `STYLE_TRANSPARENT_MORE` habla del fondo detrás de la app; `STYLE_MORE` dice que se difumina «el fondo de la app», que en «Transparente» es la capa (`Glass.kt:409-420`); la casilla dice «apenas se ve» y «en ningún sitio sale difuminado».
2. **D-2 frente a O-2.** No va contra O-2, que prohíbe cambiar de forma o de tamaño **mientras se usa** (al desplazar, al tocar o al cambiar de pestaña). Crecer con el tamaño de letra que elige el usuario es una adaptación estática de accesibilidad (WCAG 1.4.4 como guía). Vale la casilla «unos 80 dp». Sobre D-3, «una pastilla detrás de la pestaña elegida» describe bien lo que se ve; sin cambio.
3. **«Quedan N».** Lo acepta como está. Que un acceso en la papelera cuente como «cambiado» es coherente con todo «Salud», que deja fuera la papelera (`health/Health.kt:33`, `:49`, `:106`). La frase dice «sin cambiar», no «arreglados» ni «a salvo»; `LEAK_RECHECK_NEEDED` y R-1 evitan leer que lo cambiado está comprobado.
4. **F-2 aplicado** (`docs/privacidad.html:60`, «…, cuando importas una copia que la reemplaza o cuando Android cierra la app.»). Confirmado y cierto. `LEAK_RESULT_KEPT`, README `:27`, `:86` y las «Novedades» no tienen que nombrar la importación: dicen hasta cuándo, como mucho.
5. **F-1.** La frase de «¿Filtrada?» se queda. No pide el texto largo del director; si alguien lo quiere, solo vale ese, tal cual: «El de «¿Filtrada?» se olvida al volver atrás desde esa pantalla, al pulsar «Bloquear», al abrir otra bóveda o cuando Android cierra la app.». Nunca «al bloquear la bóveda» ni «al salir de la app» (AD-2, `ScreenAway.kt:79-83`).
6. **`LEAK_RECHECK_NEEDED`.** Aprobado tal cual: cierto siempre que sale, nombra el botón con su texto exacto, no dice que la contraseña nueva esté a salvo y no usa palabras prohibidas (`LeakResultsTest.kt:279`).
7. **AD-3.** No pide el cambio de código; lo cubre R-1. Si el dueño prefiriera el aviso condicional (solo ids y números en memoria), no hay objeción jurídica, pero reabre la auditoría de esa clase y R-1 basta.
8. **La fecha de la política.** O-1.
9. **«Como en cualquier conexión»** (`privacidad.html:61`). No choca con O-7, que prohíbe comparar la app o su aspecto con otra app o sistema. Es un hecho técnico general, de antes de la 2.1.1, e informa mejor (art. 13 RGPD como criterio). Se queda.
10. **«deja ver más el fondo de la app» y «Reducir transparencias» o «Alto contraste».** No hace falta nombrarlos: la línea describe cada estilo por defecto, desde Android 12; los dos ajustes son una elección expresa que hace opacos todos los paneles, y ya lo dicen `STYLE_MORE`, el README `:71` y la casilla de `:487`. Añadirlo llevaría las «Novedades» al borde de los 500 caracteres sin ganar claridad.
11. **Lo demás:** la ficha `:110` y su recuento (`:69`, 7 272) están bien; la descripción que se pega (`:50`) no cambia. El KDoc de `FloatingBar.kt` (BR-5) y la parte interna de las notas (`:9`) pueden nombrar la guía de Material 3 Expressive como orientación (O-7 lo permite en lo interno). Al publicar se quitan los corchetes de las notas. El asunto de `537871c` es interno; no pide reescribir el historial.

## 6. Lo que no hay que cambiar

- La descripción que se pega en Play (`docs/ficha-google-play.md:50`) y la sección de datos de Play («No recoge ni comparte datos»).
- `docs/privacidad.html:60`, salvo la fecha de `:22` (O-1), y `:61`.
- `STYLE_MORE`, `STYLE_TRANSPARENT_MORE`, `CUSTOM_MORE`, `WALLPAPER_MORE` y las cinco líneas cortas (`CUSTOM_MORE` y `WALLPAPER_MORE` siguen ciertos por O-5 del principio, según `CapsuleColorsTest`).
- `LEAK_RECHECK_NEEDED`, «Quedan N sin cambiar.» y sus variantes, y los KDoc de `HealthScreen` y `LeakResults`.
- Las notas de GitHub y las «Novedades» (444 caracteres).
- La frase de «¿Filtrada?» de la política.

## 7. Límites de esta revisión

- No ha visto la app en un móvil ni capturas: el parecido visual y lo que dicen las casillas se confirman con O-2.
- No ha buscado diseños ni marcas registrados.
- Los calendarios del CRA y de la Directiva (UE) 2024/2853, y la fecha de aplicación del Reglamento (UE) 2024/2822, quedan pendientes de verificar a fecha de hoy; no cambian la conclusión mientras no haya actividad comercial.
- Es un borrador profesional para el proyecto; no sustituye el criterio de un abogado colegiado si llega una reclamación.

## Textos para el Abogado Digital (revisión final)

Ninguno pendiente, salvo la fecha que se ponga en `docs/privacidad.html:22` (O-1), que el coordinador puede poner sin otra revisión si sigue el formato dado, y, si R-1 se aplicara con una redacción distinta de la exacta de §4, esa redacción.

— el Abogado Digital, 26-11-2026

## Aplicación (26-11-2026)

- **R-1:** aplicado con el texto exacto de §4, en `HealthScreen.kt:54` y `README.md:494`; `PublishedTextsTest.theLineUnderTheLeakResultSaysWhatIsNotChecked` fija la constante y la casilla. Ni `experto-seguridad-cripto` ni el Ingeniero Móvil Senior ven un problema: es cierto (`LeakResults.kt:103-113`; un acceso nuevo no entra en `found`) y la línea es `bodySmall` con varias líneas, sin altura fija.
- **O-1:** la fecha de `docs/privacidad.html:22`, justo antes del PR, con el día de la fusión.
- **O-2:** condición del dueño en el móvil antes de publicar la línea de la barra (README, «Pruebas de la 2.1.1»).
