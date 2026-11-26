# Clavero 2.1.1: revisión inicial del Abogado Digital de la barra de abajo

- **Fecha de referencia:** 24-11-2026.
- **Objeto:** el diseño `docs/superpowers/specs/2026-11-24-clavero-2.1.1-barra-design.md`. Es el **visto bueno del principio** (`CLAUDE.md`, «Skills: siempre juntas», punto 4). Todavía no hay código ni capturas. Los textos exactos y el parecido visual se vuelven a mirar en la revisión final.
- **Skills:** `despacho-digital`; criterios de `propiedad-intelectual-ia` (diseño y competencia desleal) y de `regulacion-digital-ue` (accesibilidad).
- **Leídos:** `CLAUDE.md`; el diseño; mis dictámenes de «Cristal» (`sdd-2.0/abogado-2.0-cristal.md`: §3.2, §3.3 con la tabla WCAG, O-C3, O-C4, O-C6, R-C5, R-C6 y R-C7) y del Aspecto de la 2.1 (`sdd-2.1/abogado-2.1-aspecto.md`: O-A1 a O-A6); `app/src/main/java/app/seguridad/ui/screens/AppearanceScreen.kt:95-117`; `docs/privacidad.html:37`; `docs/ficha-google-play.md:3, 50 y 110`; `README.md:71-72, 410 y 449-468`.
- **Hechos que doy por ciertos** (los del diseño): la base es la barra de herramientas flotante de Material 3 Expressive (sistema de diseño público de Google), hecha con código propio sobre Material 3 1.3.2, sin dependencias nuevas; ningún cambio de formato, permiso, manifiesto (B-2) ni red; el difuminado es de lo que dibuja la propia app, dentro del proceso; `FLAG_SECURE` sigue; la app no lee el fondo de pantalla; nada de refracción, lentes ni formas que se funden. Si alguno cambia, esta revisión no vale para lo que cambie.

## 1. Veredicto

**Se puede programar con cambios.** Siete obligaciones (O-1 a O-7) y seis recomendaciones (R-1 a R-6). Ninguna exige rehacer el diseño. La más importante es O-1: el diseño choca con un límite que yo mismo puse en O-A2 (nada de barras «flotantes en cápsula») y aquí lo sustituyo, con condiciones, solo para la barra de abajo.

## 2. Normas y criterios

- **Diseño comunitario:** Reglamento (CE) 6/2002 (RDC), modificado por el Reglamento (UE) 2024/2822: arts. 3 (el dibujo de una interfaz gráfica, también su animación o transición, puede ser un diseño), 6 (carácter singular, impresión general en el usuario informado y libertad del autor), 8.1 (lo que dicta la función), 10 (alcance) y 11 (diseño no registrado: tres años desde su divulgación en la UE). Fecha de aplicación de las modificaciones de 2024/2822: **pendiente de verificar a fecha de hoy**; no cambia la conclusión.
- **Derecho de autor:** las ideas, los estilos y las técnicas no se protegen (art. 10 TRLPI; C-406/10, *SAS Institute*, por analogía).
- **Competencia desleal:** arts. 11 (imitación; libre salvo riesgo de asociación o aprovechamiento indebido), 12 (reputación ajena) y 5 y 6 (engaño y confusión) de la Ley 3/1991 (LCD).
- **Marcas:** uso descriptivo (art. 14.1.b Reglamento (UE) 2017/1001, RMUE; art. 37.1.b Ley 17/2001, LM).
- **EE. UU. (el repositorio es público en todo el mundo):** *trade dress* del §43(a) Lanham Act. Un diseño de producto solo se protege con distintividad adquirida (*Wal-Mart v. Samara*, 2000) y si no es funcional (*TrafFix*, 2001).
- **Accesibilidad:** la Directiva (UE) 2019/882 (EAA) y la Ley 11/2023 **no se aplican** (no hay operador económico ni producto o servicio de su art. 2; razonado en la 2.0). WCAG 2.2 AA (con WCAG2ICT) es guía voluntaria. Lo que obliga es la **regla de veracidad** del proyecto.
- **Datos personales:** RGPD, arts. 4.2 y 25 (como criterio).
- **Reglas del proyecto:** veracidad, palabras prohibidas (`docs/ficha-google-play.md:3`), nombres de otras apps, privacidad del dueño, M-3 (texto mínimo).

## 3. Análisis

### 3.1 Propiedad intelectual e imitación (pregunta 1)

- **La idea es libre.** Una barra flotante, redondeada, con una pastilla en la pestaña elegida y el contenido translúcido por debajo es un patrón general. Es una idea y una técnica: el derecho de autor no la protege.
- **Hay anterioridad pública.** La barra de herramientas flotante de Material 3 Expressive la publica Google para que los desarrolladores de Android la usen, y antes ya había barras de navegación flotantes en muchas apps. Eso reduce la libertad útil de un tercero para reclamar la forma general: un diseño (registrado o no) solo cubre lo que da su impresión general singular (arts. 6 y 10 RDC), y la forma de cápsula con cinco pestañas está en gran parte dictada por la función (art. 8.1 RDC).
- **El riesgo está en los detalles y en el comportamiento.** Hay un lenguaje de diseño de un tercero, presentado en 2025, cuya barra de abajo es también una cápsula flotante sobre contenido difuminado. Si existe un diseño comunitario no registrado sobre él, todavía estaría vivo (art. 11 RDC). Lo que lo hace característico no es la cápsula, sino cómo se comporta: la lente o la refracción del contenido en los bordes, la pastilla que se estira o se funde al pasar de una pestaña a otra, la barra que se encoge o se esconde al desplazar, un botón redondo separado que flota junto a ella, los brillos que se mueven con el móvil o con el dedo, y el tinte que cambia en tiempo real según lo que pasa por debajo. El diseño ya excluye parte de esto. O-2 lo cierra.
- **La referencia del dueño.** El diseño dice bien que se toma «la idea general» y que la base es Material 3 Expressive. No nombra la app ni el móvil de la referencia. Así debe seguir: por la imitación (art. 11.2 LCD, la intención de evocar a otro pesa) y por la regla de privacidad del dueño (las apps que usa no salen nunca). O-3.
- **EE. UU.:** sin distintividad adquirida de una cápsula genérica y con su carácter funcional, una reclamación de *trade dress* es poco probable.
- **Conclusión:** riesgo **bajo** si se cumplen O-1 a O-3. **No he buscado** registros concretos de diseños (clase de Locarno 14-04) ni de marcas: lo dicho sale de la anterioridad pública y de los límites, no de una búsqueda.

### 3.2 La contradicción con O-A2

- O-A2 (dictamen del Aspecto de la 2.1), tercer punto: «no se cambia la forma de las barras ni de los botones de la app para hacerlos flotantes en cápsula». Se pensó para «Vidrio», para que no sumara forma y material del lenguaje de diseño de un tercero.
- Este diseño hace justo eso en cuatro estilos. Cambia el contexto: la base es ahora un patrón público de Android (Material 3 Expressive), no el aspecto de otro sistema, y la cápsula es la misma en «Degradado», que es opaco. Con los límites de O-2, el conjunto no da la impresión general de ese tercero.
- Por eso sustituyo ese punto de O-A2, solo para la barra de abajo y con las condiciones de O-1 y O-2. Lo demás de O-A2 sigue: la barra de arriba, los botones flotantes y los diálogos no pasan a cápsula flotante por este diseño.

### 3.3 Accesibilidad (pregunta 2)

| Criterio (WCAG 2.2) | El diseño | Qué falta |
|---|---|---|
| **1.4.3 y 1.4.6** Contraste del texto (4,5:1; 7:1 en «Alto contraste») | Objetivo 5: se mide sobre lo que la app puede poner debajo, no sobre blanco y negro. Es una decisión técnica válida: el difuminado mezcla colores, y la mezcla queda entre el color más claro y el más oscuro que la app dibuja. | Que la lista de «lo que la app puede poner debajo» esté **completa** (O-5): también los colores con acento (botones, etiquetas, chips, marcas de color de las bóvedas y carpetas, avatares, errores). Y que valga para «Personalizado» y para «Colores del fondo de pantalla», que no se conocen de antemano. |
| **1.4.11** Iconos y pastilla a 3:1 | Sí. | El indicador de foco del teclado en las pestañas, también a 3:1 (O-5). |
| **1.4.1** Uso del color | La pastilla es una forma, no solo un color. Bien. | Nada. |
| **2.4.11 / 2.4.12** Foco no oculto | Pide verlo **entero**: es el nivel AAA (2.4.12). Bien. | Que un toque sobre la cápsula nunca active lo que hay debajo, y que TalkBack no ofrezca como visible lo que está tapado sin desplazarlo antes (O-4). |
| **2.5.8** Tamaño del objetivo | Cinco pestañas en 328 dp: unos 65 dp cada una. Bien. | Nada. |
| **1.4.4 y 1.4.10** Letra grande | «Herramientas» puede cortarse con «…» con letra grande. | Cortar el texto visible es aceptable si el nombre entero sigue para TalkBack y la pestaña funciona igual (O-4). |
| **2.3.3** Animación por interacción y **2.2.2** | La pastilla se mueve con la animación normal, que se quita si Android lo pide. | La cápsula no se anima al desplazar (O-2). |
| «Reducir transparencias» y «Alto contraste» | Cápsula opaca, sin difuminado. Bien. | Nada. |

- El desplazamiento del contenido por debajo de la cápsula lo provoca el usuario. No es movimiento automático.
- Nada de esto es obligatorio por ley (la EAA no se aplica). Pasa a ser obligatorio cuando un texto publicado lo afirma. Por eso O-5 va ligado a los textos que ya dicen «contraste suficiente».

### 3.4 Privacidad y seguridad

- Grabar el contenido en una capa (`GraphicsLayer`) y difuminarlo es dibujo dentro del proceso. No se guarda, no sale del móvil y no lo ve otra app: no hay tratamiento nuevo (art. 4.2 RGPD) ni permiso nuevo.
- `FLAG_SECURE` sigue en la ventana: la cápsula no cambia lo que se puede capturar.
- Que las imágenes del usuario nunca pasen por debajo (objetivo 5) es bueno también para la privacidad: una foto que se está tachando no se ve, ni difuminada, fuera de su sitio.

### 3.5 Textos (pregunta 3)

- **`STYLE_MORE`** (`AppearanceScreen.kt:100-102`) dice hoy que las barras difuminan «el fondo de la app que tienen detrás». Con la 2.1.1, la barra de abajo difumina **el contenido** que pasa por debajo. El texto quedaría incompleto y engañoso sobre lo que se ve (O-6).
- **`STYLE_TRANSPARENT_MORE`:** «Se ve nítida, atenuada» sigue siendo cierto: la cápsula difumina el contenido de la app, no el fondo de pantalla. Ningún texto puede decir que la barra difumina el fondo de pantalla (O-6).
- **Las cinco líneas cortas:** siguen siendo ciertas. «Degradado: paneles opacos, sin transparencias» vale porque su cápsula es opaca. No hace falta que hablen de la barra (M-3).
- **`CUSTOM_MORE` y `WALLPAPER_MORE`** («ajusta los tonos para que el texto tenga contraste suficiente»): solo son ciertos si la regla de contraste de la cápsula se aplica también a esas paletas (O-5).
- **«Desde Android 12»:** es cierto para el difuminado de la barra (el diseño usa `BlurEffect`, que es de la API 31). En Android 8 a 11 el contenido no pasa por debajo: los textos no pueden decir que pasa difuminado en todos los móviles.
- **Lo que no se dice:** ni «como en» ninguna app o sistema, ni «Material 3 Expressive», ni «de Google», ni «como en Android 16», ni «nuevo diseño», «exclusivo» o «único», ni que la barra es «accesible» o que el contraste está garantizado (O-C6). Ni «líquido» (O-A1).

### 3.6 Política y ficha (pregunta 4)

- **Política (`docs/privacidad.html:37`):** **no cambia.** No hay datos, permisos ni ajustes nuevos. Su fecha tampoco.
- **Ficha, descripción que se pega (`:50`):** **no cambia.** No describe la barra, y los cinco estilos siguen siendo los mismos.
- **Ficha, versión larga (`:110`):** dice que «las barras y los diálogos difuminan el fondo de la app que tienen detrás». No se pega en Play, pero está publicada en el repositorio: conviene ponerla al día (R-3).
- **Cabecera de la ficha (`:3`):** ya prohíbe describir los estilos con el nombre de otro sistema o app. Basta.
- **Capturas:** si alguna enseña la cápsula, mismas reglas de siempre (R-A7, O-C2).

## 4. Obligaciones (O)

**O-1. Sustituyo el tercer punto de O-A2, solo para la barra de abajo.**
- Se permite que la barra de abajo sea una cápsula flotante en «Degradado», «Cristal», «Vidrio» y «Transparente», con las condiciones de O-2.
- Lo demás de O-A2 sigue: el borde de luz y el brillo de «Vidrio» son fijos; el tinte no cambia con lo que pasa por detrás; la barra de arriba, los botones flotantes y los diálogos no pasan a cápsula flotante.
- Dónde: apuntarlo en `docs/superpowers/handoff/sdd-2.1.1/progress.md` como decisión del Abogado, y en el diseño, en «No objetivos» (junto a «O-A1 del dictamen del Aspecto»): «O-A2 se sustituye para la barra de abajo por la O-1 de `sdd-2.1.1/abogado-2.1.1-diseno.md`».
- *Base:* arts. 6, 8.1 y 10 RDC; art. 11 LCD.

**O-2. La cápsula es un panel quieto que no imita el comportamiento de otro sistema.**
- No se encoge, no se esconde, no cambia de forma ni de tamaño al desplazar, al tocar ni al cambiar de pestaña.
- La pastilla no se estira, no se deforma ni se funde al pasar de una pestaña a otra: aparece o se desplaza con la animación normal de Material, sin efecto elástico, y sin animación si Android pide quitarlas.
- Nada de lente, aumento ni refracción del contenido bajo la cápsula o bajo la pastilla, tampoco al arrastrar el dedo.
- Ningún botón redondo separado flotando junto a la cápsula. Los botones flotantes que ya existen (`VaultScreen`, `ContactsScreen`) siguen siendo los de Material, en su sitio, por encima de la cápsula.
- La opacidad y el tinte de la cápsula dependen solo del estilo, la paleta, el modo y los ajustes de accesibilidad. Se calculan al elegir el aspecto, no fotograma a fotograma según el contenido.
- Iconos de Material (Apache-2.0), como hasta ahora.
- Dónde: en el plan, como límites del constructor; en la revisión final, comprobado con las capturas o con el móvil.
- *Base:* arts. 10 y 11 RDC; art. 11.2 LCD; O-C3 y O-A2.

**O-3. La referencia del dueño no entra en el repositorio.**
- Ni el nombre de la app ni el del móvil de la referencia, ni una captura suya, en el código, los documentos, los commits, los PR, los nombres de las capturas ni las notas.
- El diseño ya cumple («una barra flotante»). Que el plan y los informes de las tareas lo sigan haciendo.
- Si un documento tiene que decir de dónde salen las medidas, dice «la barra de herramientas flotante de Material 3 Expressive».
- *Base:* regla «Privacidad del dueño» (`CLAUDE.md`: las apps que usa); art. 11.2 LCD (la intención de imitar pesa).

**O-4. Nada queda tapado ni se activa a ciegas bajo la cápsula.**
- Un toque sobre la cápsula, también en su borde y en su margen transparente si lo hubiera, va a la cápsula o a nada, nunca a lo que hay debajo.
- TalkBack: al llegar a un elemento que está bajo la cápsula, primero se desplaza (objetivo 3) y después se lee. La cápsula se recorre como una sola barra de navegación con sus cinco pestañas, y la elegida se anuncia como elegida.
- Con letra grande, si el nombre visible se corta con «…», TalkBack sigue leyendo el nombre entero («Herramientas»).
- Lo comprueba el README, «Pruebas de la 2.1.1».
- *Base:* WCAG 2.4.11/2.4.12, 4.1.2 y 1.4.4 como guía; regla de veracidad si algún texto habla de TalkBack.

**O-5. El contraste, con todo lo que la app pone debajo, en todas las paletas.**
- La lista de colores de fondo del objetivo 5 incluye todo lo que puede pasar bajo la cápsula en las pantallas del `NavHost`: el fondo y sus extremos, los paneles, y también los elementos con color de acento o propio (botones, chips y etiquetas, marcas de color de bóvedas y carpetas, avatares de Contactos, avisos y errores). Lo que no entre en la lista no pasa por debajo: esa pantalla deja el espacio de abajo como en la 2.1.
- Se mide contra el color más claro y el más oscuro de esa lista (la mezcla del difuminado queda entre ellos), con y sin la opacidad del panel.
- Vale también para «Personalizado» y para «Colores del fondo de pantalla»: se calcula al aplicar la paleta, con la misma regla, porque no se conocen de antemano (R-C6). Si no, `CUSTOM_MORE` y `WALLPAPER_MORE` dejarían de ser ciertos.
- El indicador de foco del teclado en las pestañas, a 3:1.
- Ningún texto publicado da cifras de contraste ni dice «accesible» (O-C6).
- *Base:* regla de veracidad (`AppearanceScreen.kt:110` y `:115`); WCAG 1.4.3, 1.4.6 y 1.4.11 como guía.

**O-6. `STYLE_MORE` dice lo que hace la barra de abajo.**
- Sustituir `STYLE_MORE` (`AppearanceScreen.kt:100-102`) por este texto (o uno equivalente que diga lo mismo):

  «En «Cristal», «Vidrio» y «Transparente», desde Android 12, la barra de abajo flota sobre el contenido y difumina lo que pasa por debajo; las demás barras y los diálogos difuminan el fondo de la app que tienen detrás. En Android 8 a 11 son más opacos y el contenido no se ve por debajo de la barra. «Degradado» tiene la misma barra flotante, opaca. «Degradado» y «Sólido» no tienen transparencias. «Reducir transparencias» y «Alto contraste» hacen opacos todos los paneles, y «Alto contraste» quita también los bordes iluminados de «Vidrio».»
- Ningún texto dice que la barra difumina el fondo de pantalla (en «Transparente» se ve nítido).
- *Base:* regla de veracidad; art. 5 LCD; O-A5.

**O-7. Nada de nombres ajenos en lo publicado.**
- La interfaz, el README, la ficha, las «Novedades», las notas de GitHub y los nombres de las capturas no dicen «Material 3 Expressive», «de Google», «como en Android 16» ni «como en» ninguna app o sistema, ni «líquido» (O-A1).
- Los documentos técnicos internos (diseño, plan, informes, KDoc) pueden decir que las medidas siguen la barra flotante de Material 3 Expressive, sin decir que Google la aprueba o que la app «cumple» esa guía.
- *Base:* arts. 5, 6 y 12 LCD; arts. 9 y 14 RMUE; regla de nombres de otras apps.

## 5. Recomendaciones (R)

**R-1. Guardar la prueba de la creación independiente.** En el plan o en el informe de la tarea, apuntar de dónde salen las medidas (la página pública de la guía de Material 3 Expressive sobre la barra flotante, con la fecha de consulta) y que no se ha medido ni copiado nada de capturas de otras apps. Un commit propio para la cápsula.

**R-2. Código propio.** La cápsula se escribe desde cero, como dice el diseño. Si algún trozo se adapta del código de AndroidX (Apache-2.0), se conserva su cabecera de copyright y se dice en el commit; la licencia ya está en la pantalla de licencias.

**R-3. La ficha larga y el README.** Al publicar la 2.1.1:
- `docs/ficha-google-play.md:110` (versión larga): cambiar «(desde Android 12, las barras y los diálogos difuminan el fondo de la app que tienen detrás)» por «(desde Android 12, la barra de abajo flota y difumina lo que pasa por debajo, y las demás barras y los diálogos, el fondo de la app)». La descripción que se pega (`:50`) no cambia.
- `README.md`: una línea «Aspecto (2.1.1)» junto a `:72` con el texto de R-4.

**R-4. «Novedades», notas de GitHub y README.** Una línea:

«La barra de abajo flota sobre el contenido, redondeada, en «Degradado», «Cristal», «Vidrio» y «Transparente»; en los tres últimos, desde Android 12, lo que pasa por debajo se ve difuminado. «Sólido» no cambia.»

Sin «nuevo diseño», sin superlativos, sin nombres ajenos (O-7).

**R-5. Pruebas en el móvil.** Añadir a «Pruebas de la 2.1.1» del README:
- la cápsula no se encoge, no se esconde ni cambia de forma al desplazar, y la pastilla no se estira (O-2);
- un toque en el borde de la cápsula no abre lo que hay debajo (O-4);
- con TalkBack, la pestaña elegida se anuncia como elegida y, con letra grande, «Herramientas» se lee entero;
- con un teclado, el foco de las pestañas se ve;
- en «Transparente», el fondo de pantalla se ve nítido a través de la cápsula y solo se difumina el contenido de la app.

**R-6. Movimiento del sistema.** Si Android pide quitar las animaciones, además de la pastilla, que no haya ninguna otra transición de la cápsula (R-A4, R-C7). Decisión del Ingeniero Móvil Senior.

## 6. Lo que no hay que cambiar

- «Sólido» idéntico a la 2.1: bien.
- Descartar la subida a Material 3 1.4 (opción A): es técnico, sin efecto legal; además evita dependencias y metadatos nuevos.
- Que las imágenes del usuario nunca pasen por debajo: bien, por contraste y por privacidad.
- Pedir el foco **entero** (2.4.12): más de lo que pedía R-C7.
- Que, si choca, gane el contraste: bien.
- La política de privacidad y la descripción que se pega en Play.

## 7. Límites de esta revisión

- No he buscado diseños ni marcas registrados. Lo dicho sale de la anterioridad pública y de los límites de O-2.
- No hay código ni capturas: el parecido visual se mira en la revisión final.
- La fecha de aplicación del Reglamento (UE) 2024/2822 está pendiente de verificar a fecha de hoy; no cambia la conclusión.
- Es un borrador profesional para el proyecto; no sustituye el criterio de un abogado colegiado si llega una reclamación.

## Textos para el Abogado Digital (revisión final)

- `STYLE_MORE` nuevo (O-6) y, si cambian, `STYLE_TRANSPARENT_MORE`, `CUSTOM_MORE`, `WALLPAPER_MORE` y las cinco líneas cortas.
- Las descripciones de TalkBack de la cápsula y de sus pestañas, si son nuevas (O-4).
- `README.md`: «La 2.1.1», la línea del Aspecto y «Pruebas de la 2.1.1» (R-3, R-4, R-5).
- `docs/ficha-google-play.md:110` (R-3); `:50` y `docs/privacidad.html:37` solo para confirmar que no cambian.
- Las «Novedades» de Play y las notas de GitHub de la 2.1.1 (R-4).
- Las capturas, si cambian (R-A7, O-3).
- Los KDoc y comentarios que hablen de la base de la cápsula (O-7).
