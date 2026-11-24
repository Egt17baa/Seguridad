# Clavero 2.1: revisión inicial del Abogado Digital de los tres estilos nuevos de Aspecto

- **Fecha de referencia:** 23-11-2026.
- **Objeto:** el diseño `docs/superpowers/specs/2026-11-23-clavero-2.1-aspecto-design.md` («Degradado», «Vidrio» y «Transparente» en Ajustes → Aspecto). Es el **visto bueno del principio** (`CLAUDE.md`, «Skills: siempre juntas», punto 4): todavía no hay código. Los textos exactos se vuelven a mirar en la revisión final.
- **Skills:** `despacho-digital` y `propiedad-intelectual-ia`; `privacidad-proteccion-datos` para la §3.2.
- **Leídos:** `CLAUDE.md`; el diseño; mi dictamen de «Cristal» de la 2.0 (`docs/superpowers/handoff/sdd-2.0/abogado-2.0-cristal.md`, sobre todo §3.2, O-C3 a O-C6, R-C7, R-C9 y R-C10); `app/src/main/java/app/seguridad/ui/screens/AppearanceScreen.kt:79-102` (constantes `STYLE_*`, `WALLPAPER_*`, `CUSTOM_*`, `APPEARANCE_NOTICE`); `app/src/main/java/app/seguridad/settings/Appearance.kt:10-16` (`ThemeStyle`); `app/src/main/java/app/seguridad/tools/ToolTexts.kt:588` (cómo dice hoy la app lo de las capturas); `docs/privacidad.html:37`; `docs/ficha-google-play.md:3, 50 y 110`; `README.md:70`.
- **Hechos que doy por ciertos** (los del diseño): la app no lee el fondo de pantalla ni pide permisos; lo dibuja Android detrás de la ventana (`FLAG_SHOW_WALLPAPER`, puesto en tiempo de ejecución); `FLAG_SECURE` sigue; el manifiesto no cambia (B-2); nada de refracción, lentes, formas que se funden ni animaciones; el estilo por defecto sigue siendo «Cristal» y nadie cambia de estilo al actualizar. Si alguno de estos hechos cambia, esta revisión no vale para lo que cambie.

## 1. Veredicto

**Vale con cambios.** Los tres nombres son palabras corrientes del español usadas de forma descriptiva, y el diseño ya trae los límites de O-C3 y O-C4. «Transparente» no crea ningún tratamiento de datos ni pide permisos. Hay **seis cambios obligatorios** (O-A1 a O-A6), casi todos de veracidad de los textos y de límites para el constructor, y **siete recomendados** (R-A1 a R-A7). Ninguno exige rehacer el diseño.

## 2. Normas y criterios que aplico

- **Propiedad industrial e intelectual:** uso descriptivo de un signo (art. 14.1.b Reglamento (UE) 2017/1001, RMUE; art. 37.1.b Ley 17/2001, de Marcas, LM); diseño comunitario (arts. 8.1, 10 y 19 Reglamento (CE) 6/2002, RDC, modificado por el Reglamento (UE) 2024/2822); las ideas, los estilos y las técnicas no se protegen por derecho de autor (art. 10 TRLPI; C-406/10, *SAS Institute*, por analogía).
- **Competencia desleal:** imitación y aprovechamiento de la reputación ajena (arts. 11 y 12 Ley 3/1991, de Competencia Desleal, LCD); actos de engaño (art. 5 LCD) para los textos.
- **Datos personales:** RGPD (Reglamento (UE) 2016/679), arts. 2.1 y 4.2 (si hay tratamiento) y 25 (privacidad desde el diseño, como criterio).
- **Google Play:** políticas de metadatos, de comportamiento engañoso y de datos de usuario (Seguridad de los datos: «No recoge ni comparte datos»).
- **Reglas del proyecto:** veracidad de los textos, palabras prohibidas (`docs/ficha-google-play.md:3`), nombres de otras apps, M-3 (texto mínimo), privacidad del dueño.

## 3. Análisis

### 3.1 Los nombres y el aspecto de «Vidrio» (pregunta 1)

- **«Degradado» y «Transparente»:** palabras corrientes que dicen lo que se ve. Sin riesgo de marca ni de imitación. «Transparente» no sugiere nada sobre los datos (no es una afirmación de «transparencia» en el sentido del RGPD), porque está dentro de Ajustes → Aspecto junto a los demás estilos; no hay que cambiarlo.
- **«Vidrio»:** también es una palabra corriente y descriptiva, y por sí sola no es el nombre de ningún aspecto de otro sistema. Lo que O-C4 prohíbe es el nombre de ese aspecto o su traducción, es decir, **«vidrio líquido»** o «cristal líquido», y cualquier «como el de», «al estilo de» o «inspirado en». Un estilo que se llama «Vidrio», sin «líquido» y sin ninguna referencia a otro sistema, es un uso descriptivo (art. 14.1.b RMUE; art. 37.1.b LM). **No he podido comprobar** si el nombre de ese aspecto está registrado como marca ni en qué clases; con un uso descriptivo y sin la palabra «líquido», no cambia la conclusión.
- **El contexto sí importa.** El dueño pidió «vidrio líquido», y el diseño (`:14`) dice que «Vidrio» «es la respuesta a «vidrio líquido»». En un repositorio público, esa frase documenta una intención de evocar el aspecto de un tercero, que es justo lo que, sumado a un parecido visual, alimentaría una reclamación por aprovechamiento de la reputación ajena (art. 12 LCD) o por imitación (art. 11.2 LCD). Hoy no hay parecido que lo haga probable, porque el diseño excluye lo característico. Por eso es una recomendación (R-A1) y no un cambio obligatorio, pero lo obligatorio es que esa relación **no salga nunca** de los documentos internos (O-A1).
- **El aspecto de «Vidrio».** Paneles más translúcidos, un borde con un degradado de luz, un brillo arriba más ancho, esquinas más redondas y una sombra suave: es el repertorio general del efecto vidrio esmerilado («glassmorphism» en la jerga), que se usa desde hace años en muchas interfaces. No es de nadie (§3.2 del dictamen de «Cristal»). Lo que haría que se pareciera a lo concreto y característico del aspecto de otro sistema no está en las piezas, sino en **cómo se comportan**: lentes y refracción en los bordes, brillos que se mueven con la inclinación del móvil, con el desplazamiento o con el dedo, tintes que cambian según lo que pasa por detrás, formas que se funden o se separan, y la forma concreta de sus barras flotantes en cápsula y de sus botones. O-A2 lo convierte en límites para el constructor.
- **Usabilidad (no es legal, pero afecta a la veracidad):** en español, «Cristal» y «Vidrio» son casi sinónimos, y en buena parte de Latinoamérica «vidrio» es la palabra corriente para lo que en España se llama «cristal». Quien elige no sabe cuál es «más». La línea corta de «Vidrio» debe decir su relación con «Cristal» (texto final en §4). Si el controlador prefiere cambiar el nombre, «Cristal intenso» también vale y no tiene riesgo; no lo exijo.

### 3.2 «Transparente»: privacidad, capturas y batería (pregunta 2)

- **No hay tratamiento de datos.** La app no lee la imagen del fondo de pantalla: pide a Android que la dibuje detrás de su ventana, y Android la compone sin entregarle los píxeles. Sin acceso a la imagen no hay tratamiento (art. 4.2 RGPD) de lo que pueda aparecer en ella (una foto personal, rostros de terceros). La Seguridad de los datos de Play («No recoge ni comparte datos») no cambia. Esto solo es cierto si la app no usa ninguna API que devuelva la imagen del fondo (por ejemplo, las de `WallpaperManager` que dan su `Drawable` o su archivo): O-A3.
- **Lo que verá quien mire la pantalla.** El fondo de pantalla de inicio ya lo ve cualquiera que mire el móvil al desbloquearlo o en el escritorio; con «Transparente» se ve, además, atenuado, detrás de la bóveda abierta. No es una exposición nueva y la elige el usuario a sabiendas. No hace falta un aviso legal. Sí es buena práctica (art. 25 RGPD como criterio; privacidad desde el diseño) que el texto plegado lo diga con una frase llana (R-A3), sin alarmar.
- **Solo el fondo de pantalla, nunca otras apps.** El texto promete «el fondo de pantalla del móvil». Si la implementación hiciera translúcida la ventana entera (por ejemplo, con un tema translúcido en lugar de `FLAG_SHOW_WALLPAPER`), detrás se verían la pantalla de la app anterior o de otra app, con sus datos. Eso sería falso respecto del texto y un riesgo de privacidad para terceros: O-A4.
- **Capturas y recientes.** `FLAG_SECURE` sigue en la ventana de la app: Android no deja capturarla y su miniatura en recientes no enseña el contenido. La propuesta dice «Las capturas de pantalla siguen bloqueadas»; es cierto, pero conviene usar la fórmula que ya usa la app (`ToolTexts.kt:588`: «Sin capturas de pantalla en toda la app»), para no prometer algo distinto en cada sitio. No se debe decir que el fondo de pantalla queda protegido de capturas: es otra ventana, del sistema, y no depende de Clavero.
- **Batería y movimiento.** Un fondo de pantalla animado sigue dibujándose mientras se ve detrás de la app, y el difuminado de la ventana (desde Android 12) se recalcula con cada fotograma del fondo. «Puede gastar algo más de batería» es cierto y prudente; no hay que dar cifras. Además, un fondo animado es movimiento detrás del texto que la app no puede parar; si el sistema pide quitar las animaciones, Android suele pararlo, pero no siempre. No hay obligación legal (la Ley de accesibilidad no se aplica, como dije en la 2.0), pero por coherencia con R-C7 lo menciono en R-A4.
- **«Colores del fondo de pantalla» a la vez.** Si el usuario elige «Transparente» y esa paleta, la app sí recibe de Android los colores calculados del fondo (O-C5). Por eso el texto de «Transparente» no puede decir «la app no lee el fondo de pantalla» a secas: tiene que decir «la imagen del fondo de pantalla», como ya dice `WALLPAPER_MORE` (O-A5).

### 3.3 Los textos de la interfaz (pregunta 3)

- **«Degradado: paneles opacos sobre un fondo de color en degradado.»** Cierta. Añado «sin transparencias», para que se vea su diferencia con «Cristal» y en paralelo con «Sólido».
- **«Vidrio: paneles translúcidos con bordes de luz, sobre un fondo de color.»** No es falsa, pero no dice en qué se distingue de «Cristal» (§3.1). Propongo decirlo.
- **«Transparente: deja ver el fondo de pantalla del móvil detrás de la app.»** Le falta que se ve atenuado (hay una capa de color bastante opaca). Sin eso, quien lo elige espera ver su fondo tal cual.
- **Más información de «Transparente».** «La app no lee el fondo de pantalla» debe ser «la imagen del fondo de pantalla» (O-A5). «Lo muestra Android» es correcto; «lo dibuja Android detrás de la app» es más preciso. «Si el móvil lo permite» es la cautela necesaria (hay móviles y modos de ahorro que no difuminan entre ventanas). «Las capturas de pantalla siguen bloqueadas», con la fórmula de la app (§3.2). Falta decir que «Reducir transparencias» y «Alto contraste» lo tapan (el diseño lo hace; el usuario debe saber cómo volver).
- **`STYLE_MORE` actual** (`AppearanceScreen.kt:81-82`): «Desde Android 12, las barras y los diálogos difuminan el fondo de la app que tienen detrás. En Android 8 a 11 son más opacos.» Hoy se refiere a «Cristal». Con cinco estilos, dicho así sería falso para «Degradado» y «Sólido», que no difuminan nada: tiene que decir a qué estilos se aplica (O-A5).
- **`CUSTOM_GLASS` («Intensidad del cristal»)** en «Personalizado»: el constructor decide si afecta a «Vidrio» y a «Transparente». Lo que el texto plegado diga tiene que coincidir con lo que haga (R-A5).
- **`APPEARANCE_NOTICE`** («Para volver a uno sin transparencias: … «Sólido»»): sigue siendo cierto con «Degradado», que tampoco tiene transparencias. No hay que tocarlo.
- **Palabras prohibidas:** ninguno de los textos propuestos ni de los finales usa las de `docs/ficha-google-play.md:3` ni promete contraste («se lee bien», «accesible»): cumplen O-C6. La frase del diseño «bastante opaca, para que el texto se lea» es una meta interna; no pasa a la interfaz como promesa.

### 3.4 Política, ficha, README y notas (pregunta 4)

- **Política (`docs/privacidad.html:37`):** lo que dice sigue siendo cierto (la app no lee la imagen del fondo de pantalla ni pide permisos). Para que la política explique todo lo que la app hace con algo del móvil, conviene añadir una frase sobre «Transparente» (R-A6). No es obligatorio: no hay tratamiento de datos nuevo y lo que ya dice no queda falso.
- **Ficha (`docs/ficha-google-play.md:50` y `:110`):** hoy presenta el aspecto como «Cristal» … «o «Sólido»». Con la 2.1 publicada, esa enumeración cerrada sería incompleta y daría una idea equivocada de lo que hay: hay que ponerla al día al publicar la 2.1 (O-A6), respetando el tope de 4 000 caracteres (`PublishedTextsTest.theFullPlayDescriptionFits`). Sin «vidrio líquido» ni comparaciones (O-A1). La regla de la cabecera (`:3`) debe nombrar los estilos nuevos.
- **README (`README.md:70`):** lo mismo; y la prueba en el móvil de la 2.1 («Pruebas de la 2.1») con los puntos de §3.2 (que detrás solo se ve el fondo de pantalla, nunca otra app; recientes y capturas en «Transparente»).
- **Notas de GitHub y «Novedades» de Play:** una línea descriptiva (texto en §4). Sin «nuevo look», sin superlativos, sin nombres de otros sistemas ni de su aspecto.
- **Licencias:** nada, salvo que entre algún recurso de terceros (R-C9 sigue: los degradados, bordes y sombras se hacen con código propio; nada de imágenes, texturas ni máscaras copiadas).

## 4. Textos finales (mi redacción)

En la interfaz (Ajustes → Aspecto), una línea a la vista por estilo (M-3):

- **«Sólido»** (sin cambios): «Sólido: paneles opacos sobre un fondo liso, sin transparencias.»
- **«Degradado»:** «Degradado: paneles opacos, sin transparencias, sobre un fondo de color en degradado.»
- **«Cristal»** (sin cambios): «Cristal: paneles translúcidos sobre un fondo de color.»
- **«Vidrio»:** «Vidrio: como «Cristal», con paneles más translúcidos y bordes iluminados.»
- **«Transparente»:** «Transparente: deja ver, atenuado, el fondo de pantalla del móvil detrás de la app.»

Bajo «Más información»:

- **Del difuminado, para todos los estilos** (sustituye a `STYLE_MORE`): «En «Cristal», «Vidrio» y «Transparente», desde Android 12, las barras y los diálogos difuminan lo que tienen detrás; en Android 8 a 11 son más opacos. «Degradado» y «Sólido» no tienen transparencias. «Reducir transparencias» y «Alto contraste» hacen opacos todos los paneles.»
- **De «Transparente»:** «La app no lee la imagen del fondo de pantalla ni pide permisos: Android la dibuja detrás de la app. Desde Android 12, si el móvil lo permite, se ve difuminada. Quien mire la pantalla verá también tu fondo de pantalla. Con un fondo de pantalla animado, el móvil puede gastar algo más de batería. Como en toda la app, sin capturas de pantalla. Con «Reducir transparencias» o «Alto contraste», el fondo de pantalla deja de verse.»

Fuera de la app (para la 2.1, al publicar):

- **README, ficha (`:110`), «Novedades» y notas:** «Tres estilos más en Ajustes → Aspecto: «Degradado» (paneles opacos sobre un fondo en degradado), «Vidrio» (como «Cristal», más translúcido y con bordes iluminados) y «Transparente» (deja ver, atenuado, el fondo de pantalla del móvil; la app no lee su imagen ni pide permisos).» En la ficha corta (`:50`), si no cabe: «Cinco estilos («Sólido», «Degradado», «Cristal», «Vidrio» y «Transparente») y varias paletas.»
- **Política (R-A6),** añadir a `:37`: «Con el estilo «Transparente», Android dibuja el fondo de pantalla detrás de la app; la app tampoco lee su imagen.»

Si los nombres internos o la técnica cambian al programar, estos textos se vuelven a revisar en la revisión final.

## 5. Cambios obligatorios (O-A)

**O-A1. «Vidrio líquido» no sale de los documentos internos.**
- No aparece en la interfaz, el README, la ficha, las «Novedades», las notas de GitHub, la política, los nombres de las capturas, los asuntos de los commits ni los títulos de los PR. Tampoco «cristal líquido», el nombre original de ese aspecto en otro idioma, ni «como el de», «al estilo de» o «inspirado en» ningún sistema o app.
- Nunca «Clavero Vidrio», ni ™, ni logotipo propio para ningún estilo; ningún estilo va en el título de Play (O-C4 sigue).
- *Base:* arts. 12 y 5 LCD; art. 14.1.b RMUE; regla de los nombres de otras apps; O-C4.

**O-A2. «Vidrio» no imita el comportamiento característico de otro aspecto.**
- Límites para el constructor, además de los del diseño (sin refracción, lentes, formas que se funden ni animaciones de líquido):
  - el borde de luz y el brillo son **fijos**: no se mueven con la inclinación del móvil (ningún sensor), con el desplazamiento ni con el dedo;
  - el tinte de los paneles no cambia según lo que pasa por detrás (solo la paleta y el modo claro u oscuro);
  - no se cambia la forma de las barras ni de los botones de la app para hacerlos flotantes en cápsula, ni se aumenta lo que hay detrás de un panel;
  - iconos de Material (Apache-2.0) como hasta ahora; ni iconos, ni símbolos, ni fuentes de terceros fuera de la lista de `CLAUDE.md`.
- *Base:* arts. 10 y 19 RDC; art. 11.2 LCD; O-C3.

**O-A3. La app no obtiene la imagen del fondo de pantalla.**
- Ni en «Transparente» ni en ningún otro sitio se usan las API que devuelven la imagen o el archivo del fondo de pantalla (las de `WallpaperManager` que dan su `Drawable`, su `Bitmap` o su descriptor de archivo). Solo la marca de la ventana y el difuminado de la ventana que compone el sistema.
- Ningún permiso nuevo; el manifiesto no cambia (B-2).
- *Base:* veracidad de `WALLPAPER_MORE`, de la política (`:37`) y de la Seguridad de los datos de Play; art. 25 RGPD como criterio.

**O-A4. Detrás de la app solo se ve el fondo de pantalla.**
- «Transparente» no se hace con una ventana o un tema translúcidos que dejen ver la app anterior u otras apps. Si un móvil no dibuja el fondo de pantalla detrás, la capa queda opaca, no transparente a otra cosa.
- Lo comprueba la prueba en el móvil de la 2.1 (abrir Clavero desde otra app con datos en pantalla y ver que no se ve detrás).
- *Base:* veracidad del texto; privacidad de terceros (lo que enseñaría otra app).

**O-A5. Los textos dicen lo que hace cada estilo.**
- `STYLE_MORE` se cambia para que el difuminado se diga solo de «Cristal», «Vidrio» y «Transparente» (texto en §4).
- El texto de «Transparente» dice «la imagen del fondo de pantalla», no «el fondo de pantalla» a secas (O-C5), y dice que se ve atenuado.
- Lo de las capturas, con la fórmula de la app («sin capturas de pantalla»), sin decir que el fondo de pantalla queda protegido.
- Ningún texto promete que se lee bien con cualquier fondo, ni contraste (O-C6).
- *Base:* regla de veracidad; art. 5 LCD; O-C5 y O-C6.

**O-A6. La ficha y el README, al día al publicar la 2.1.**
- `docs/ficha-google-play.md:50` y `:110`, y `README.md:70`, dejan de presentar «Cristal» o «Sólido» como las únicas opciones, con los textos de §4, dentro del tope de 4 000 caracteres.
- La regla de la cabecera de la ficha (`:3`) dice «sin nombrar ni describir los estilos del aspecto con el nombre del de ningún sistema o app», no solo «Cristal».
- *Base:* política de metadatos de Google Play; regla de veracidad.

## 6. Cambios recomendados (R-A)

**R-A1. Quitar del diseño la frase que une «Vidrio» con «vidrio líquido».** En `docs/superpowers/specs/2026-11-23-clavero-2.1-aspecto-design.md:14`, cambiar «Es la respuesta a «vidrio líquido»: ese nombre **no** se usa…» por algo como «Es la respuesta a la petición de un cristal más vistoso; el nombre del aspecto de otro sistema, ni traducido, no se usa en ningún sitio (O-C4 de la 2.0).» La cita de la petición del dueño en `:3` puede quedarse, porque es un registro de lo que pidió.

**R-A2. Un test de textos.** Que las pruebas de palabras (`SourceTextsTest` o `PublishedTextsTest`) rechacen «líquido» o «liquid» junto a «vidrio», «cristal» o «glass» en las cadenas de la app y en lo publicado (README, ficha, política), como ya se hace con otras reglas.

**R-A3. La frase de quien mira la pantalla.** «Quien mire la pantalla verá también tu fondo de pantalla.» (ya en el texto de §4). No es obligatoria, pero es la información honesta que corresponde a una app de bóveda.

**R-A4. Movimiento detrás del texto.** Si el sistema pide quitar las animaciones o reducir las transparencias, la app ya hace opaca la capa con su propio ajuste; el constructor mira si conviene que el ajuste del sistema también la haga opaca (decisión del Ingeniero Móvil Senior; coherente con R-C6 y R-C7 de la 2.0).

**R-A5. «Intensidad del cristal».** Si en «Personalizado» afecta también a «Vidrio» y a «Transparente», el texto plegado (`CUSTOM_MORE`) lo dice; si solo afecta a «Cristal», lo dice también. Que no haya un mando que no hace nada sin explicarlo.

**R-A6. Una frase en la política.** La de §4, junto a la de los colores del fondo de pantalla (`docs/privacidad.html:37`). Si se cambia la política, se cambia su fecha.

**R-A7. Las capturas de Play.** Si alguna captura de la ficha enseña «Transparente», el fondo de pantalla de la captura es inventado o liso (nunca una foto con personas, ni nada del dueño: regla «Privacidad del dueño»), y la captura no lleva texto promocional (O-C2).

## 7. Lo que no tengo que cambiar

- El enfoque A y el descarte de B y C del diseño: C (refracción con sombreadores) es justo lo que O-C3 desaconseja; bien descartado.
- Que el estilo por defecto no cambie al actualizar: evita una sorpresa sobre lo que el usuario ve.
- Que un valor desconocido caiga en «Cristal»: es una cuestión técnica; no tiene efecto legal.

## 8. Límites de esta revisión

- No he hecho búsquedas de marcas ni de diseños registrados del aspecto de otros sistemas; lo que digo de «Vidrio» sale del uso descriptivo y de los límites de O-A2, no de una búsqueda.
- No hay código ni capturas todavía: el parecido visual se mira en la revisión final, con las capturas que el constructor prepara para el dueño.
- Este análisis es un borrador profesional para el proyecto; no sustituye el criterio de un abogado colegiado si llega una reclamación.

## Textos para el Abogado Digital (revisión final)

- Las cinco líneas cortas de los estilos, el `STYLE_MORE` nuevo y el texto plegado de «Transparente» (§4).
- `CUSTOM_MORE`, si cambia (R-A5).
- `docs/ficha-google-play.md:3, 50 y 110`, `README.md:70` y su apartado «Pruebas de la 2.1», `docs/privacidad.html:37` y su fecha (O-A6, R-A6).
- Las «Novedades» de Play y las notas de GitHub de la 2.1.
- Las capturas de la ficha, si cambian (R-A7).
