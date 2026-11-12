# Clavero 2.0: revisión inicial del Abogado Digital del aspecto «Cristal», los temas y el logo

- **Fecha de referencia:** 12-11-2026.
- **Objeto:** solo la parte nueva del diseño, «Aspecto «Cristal», temas y logo (dueño, 12-11-2026)» (`docs/superpowers/specs/2026-11-12-clavero-2.0-design.md:145-178` y las decisiones de Claude de `:249-253`), la «Tarea 5 bis» del plan (`docs/superpowers/plans/2026-11-12-clavero-2.0.md:196-215`) y el logo ya dibujado (commit `61b8d3f`). Lo demás de la 2.0 está en el dictamen inicial (`abogado-2.0-inicial.md`, O-1 a O-11 y R-1 a R-12), que sigue vigente.
- **Revisión al principio** (`CLAUDE.md`, punto 4): todavía no hay código de los temas ni textos de la interfaz. Reviso lo que el diseño permite y lo que obliga a decir. Los textos exactos se revisan al final.
- **Skills:** `despacho-digital`, `propiedad-intelectual-ia` y `regulacion-digital-ue`.
- **Leídos:** `CLAUDE.md`; el diseño y el plan citados; el dictamen inicial de la 2.0 (sobre todo O-8, R-1 y R-8); `app/src/main/res/drawable/ic_launcher_foreground.xml`, `ic_launcher_background.xml` e `ic_launcher_monochrome.xml`; `mipmap-anydpi-v26/ic_launcher.xml` e `ic_launcher_round.xml`; el icono de la 1.x (`git show 61b8d3f~1:app/src/main/res/drawable/ic_launcher_foreground.xml`); las imágenes `docs/play/icono-512.png` y la vista previa del controlador (círculo, redondeado, cuadrado, 40 px y 24 px, en claro y en oscuro); `docs/ficha-google-play.md`; `README.md` (líneas 9 y 58); `docs/privacidad.html`; `docs/PUBLICAR.md:124`.
- **Hechos que doy por ciertos (los de siempre):** autor particular sin ánimo de lucro, con el seudónimo Egt17baa, residente en España; app gratuita, sin anuncios, compras ni donaciones, con licencia Apache-2.0; no recibe datos de nadie; en Google Play, «no comerciante», fuera de Francia (con ultramar), Rusia y Bielorrusia. Y de esta parte: el logo, los temas y «Cristal» **no añaden permisos, componentes, dependencias, fuentes ni red** (diseño, `:153` y `:158`; plan, `:200-203`). El logo lo ha dibujado Claude por encargo del dueño, a mano, con tres trazos vectoriales (un círculo, un arco con una recta y dos dientes), sin partir de ninguna imagen ajena. **No consta ninguna reclamación** del titular de la app cuyo icono se confundía con el de la 1.x.

## 1. Veredicto

**Visto bueno con cambios.** El logo, el aspecto «Cristal», las seis paletas, «Colores del fondo de pantalla» y «Personalizado» se pueden hacer como están diseñados. Ninguna norma obliga a cambiar el dibujo ni a quitar nada.

- **El logo** (una «C» cuyo extremo de abajo es la paleta de una llave con dos dientes, en oro, dentro de un disco translúcido, sobre añil y violeta) evita los motivos más repetidos del sector (escudo, candado, cerradura, llave suelta) y une el dibujo con el nombre («clavero»: quien guarda las llaves). Con lo que puedo ver desde aquí, **no encuentro un parecido que haga probable la confusión** con marcas o iconos conocidos. Pero eso no sustituye una búsqueda de anterioridades, que no puedo hacer desde esta sesión (§7). Por eso la búsqueda del dueño (§6) es una condición para publicar (O-C1), no un trámite opcional.
- **«Cristal»** es una palabra corriente del español usada para describir un aspecto. Los paneles translúcidos y el difuminado son una técnica y un estilo generales: no son de nadie. El riesgo está en copiar los elementos concretos y característicos del diseño de un tercero, o en describirlo con su nombre (O-C3 y O-C4).
- **La Ley de accesibilidad no se aplica**, igual que en el dictamen inicial. La accesibilidad del cristal es voluntaria, pero el diseño la promete en sus propios términos, y eso obliga por veracidad (O-C6).

Son **ocho obligatorios** (O-C1 a O-C8). Siete son sobre textos y sobre lo que no se puede decir. Uno (O-C1) es un paso del dueño antes de publicar. Hay **diez recomendados** (R-C1 a R-C10).

## 2. Mapa normativo de esta parte (12-11-2026)

| Norma | ¿Se aplica? | Por qué |
|---|---|---|
| **Marcas:** Reglamento (UE) 2017/1001 (RMUE) y Ley 17/2001 (LM) | Sí, como límite. | El logo será el signo que identifica a Clavero en Play y en el móvil. No puede crear riesgo de confusión con una marca anterior para productos idénticos o similares (art. 8.1.b y art. 9.2.b RMUE; art. 6.1.b y art. 34.2.b LM), ni aprovecharse de una marca renombrada (art. 9.2.c RMUE; art. 34.2.c LM). Se aprecia de forma global, por la impresión de conjunto y sus elementos dominantes (TJUE C-251/95, *Sabel*; C-342/97, *Lloyd*). Marca notoria no registrada: art. 6 *bis* del Convenio de París y art. 6.2.d LM. |
| **Diseños:** Reglamento (CE) 6/2002, modificado por el Reglamento (UE) 2024/2822 (diseño de la Unión), y Ley 20/2003 | Sí, como límite. | Desde la reforma, la definición de «producto» cubre expresamente los símbolos gráficos, los logotipos y las interfaces gráficas (**aplicación por fases desde el 1-5-2025; fecha por confirmar en EUR-Lex**). Un icono o una interfaz registrados como diseño se infringen si el nuestro produce la misma impresión general en el usuario informado (art. 10 RDC). El diseño no registrado solo protege frente a la copia (art. 19.2 RDC), y la creación independiente es defensa. Lo que viene impuesto solo por la función técnica no se protege (art. 8.1 RDC): eso incluye el efecto genérico de translucidez. |
| **Derecho de autor:** TRLPI; Directiva 2001/29/CE | Sí, como límite, y con poca protección propia. | Se protege la expresión original, nunca una idea, un estilo o una técnica («C + llave», «paneles de vidrio»): C-5/08, *Infopaq*; C-683/17, *Cofemel*; C-833/18, *Brompton*. Frente a terceros, un dibujo sencillo y geométrico hecho por una IA difícilmente es «obra» del dueño (hace falta la creación intelectual propia de un autor humano). Eso no impide usarlo. Solo quiere decir que **su protección real vendrá de la marca**, si se registra (R-C1). |
| **Competencia desleal:** Ley 3/1991 (LCD), arts. 6 y 11 | Riesgo residual. | La confusión y la imitación son desleales si se hacen en el mercado con fines concurrenciales (arts. 2 y 3). Un particular sin actividad económica está en el borde del ámbito subjetivo. El cambio de icono reduce el riesgo, no lo crea. |
| **Políticas de Google Play** (suplantación, comportamiento engañoso, metadatos) | Sí, como contrato. | El icono, el título y las capturas no pueden imitar a otra app ni llevar texto de clasificación, precio o promoción («n.º 1», «gratis», «nuevo», «el mejor»). La política de metadatos lo prohíbe en el título, el icono y el nombre del desarrollador desde el 29-9-2021 (centro de ayuda de Play Console; **redacción vigente por confirmar el día de enviar**). |
| **Reglamento de IA**, Reglamento (UE) 2024/1689, art. 50 | No. | Las obligaciones de marcar el contenido sintético son del proveedor del sistema (art. 50.2) o del responsable del despliegue para ultrasuplantaciones o textos de interés público (art. 50.4). Un logo no es ninguna de esas cosas. No hay que decir que lo diseñó una IA, aunque se puede decir en los documentos técnicos (ya lo dicen). |
| **Accesibilidad:** Directiva (UE) 2019/882 (EAA) y Ley 11/2023, de 8 de mayo | No. | Un gestor de contraseñas no es ninguno de los productos (art. 2.1) ni de los servicios (art. 2.2: comunicaciones electrónicas, acceso a medios audiovisuales, transporte de viajeros, banca para consumidores, libros electrónicos y comercio electrónico) que cubre. No hay «operador económico». Las microempresas que prestan servicios quedan fuera de todos modos (art. 4.5). Se aplica desde el 28-6-2025 (art. 31). Detalle en §3.3. |
| **Igualdad de las personas con discapacidad:** RDLeg 1/2013 (art. 29) y RD 193/2023 | Muy improbable. | Se refieren a bienes y servicios a disposición del público, pensados para quien los ofrece en el mercado. **No he podido confirmar** si alcanzan a un particular que publica gratis una app de código abierto. Con las medidas del diseño (R-1, «Reducir transparencias», «Alto contraste», «Sólido»), el riesgo práctico es nulo. |

## 3. Análisis

### 3.1 El logo

**Qué es.** Un disco translúcido (`ic_launcher_foreground.xml:14`, círculo de radio 30 con un borde blanco al 28 %), una «C» abierta de trazo grueso que termina en una recta horizontal hacia la derecha (`:25`) y dos dientes que bajan de esa recta (`:37`), todo en un degradado de oro (`#FFE3A3` a `#F0A23A`). Se lee como una llave cuyo ojo es la letra C. El fondo es un degradado de añil a violeta (`ic_launcher_background.xml`). La versión monocroma (`ic_launcher_monochrome.xml`) es la misma «C» con sus dientes, sin el disco.

**Lo que evita, y por qué es lo correcto.** El icono de la 1.x era un escudo turquesa con una cerradura sobre azul oscuro. Escudos, candados, cerraduras y llaves sueltas son los motivos que más se repiten en las apps de seguridad: con ellos, dos iconos parecidos se confunden aunque nadie haya copiado a nadie. Un motivo menos repetido, ligado al nombre, es más distintivo y más fácil de defender. La decisión del diseño (`:151`) es la que yo habría recomendado.

**Riesgo de confusión con marcas e iconos ajenos.**
- **Elemento dominante:** la «C» con dientes de llave. Una letra sola en un círculo es un recurso muy común y, por sí sola, poco distintiva: hay muchos logotipos que son una «C» dentro de un círculo o de un cuadrado. Lo que da carácter a este es la paleta con dientes. **En tamaños pequeños** (24 px, en la vista previa), los dientes casi desaparecen y queda «una C dorada en un círculo»: ahí es donde más se parece a otros (R-C3).
- **Elemento conceptual:** «llave» + «C». Es un concepto natural para cerrajerías, inmobiliarias, hoteles o gestores de acceso. En la clase 9 (software) y en las clases 42 y 45 puede haber marcas figurativas con una llave formada por una letra. Esa es justo la búsqueda que hay que hacer (§6), porque la similitud conceptual cuenta en la comparación global (*Sabel*).
- **Colores:** el oro sobre violeta no es de nadie. Un color o una combinación de colores solo se protege como marca si se registra como tal y ha adquirido carácter distintivo (C-104/01, *Libertel*; C-49/02, *Heidelberger Bauchemie*). Los colores pesan poco en la comparación de dos figuras distintas.
- **Productos:** la comparación que importa es con apps (clase 9), sobre todo **gestores de contraseñas, apps de 2FA, carteras de criptomonedas y apps de cifrado**, que son las que un usuario de Play vería al lado de Clavero. Es ahí donde hay que mirar primero (§6, paso 3).
- **Conclusión:** con lo que puedo ver desde aquí, **no encuentro un parecido que haga probable la confusión**. No puedo decir que no exista. Si la búsqueda encuentra algo cercano, se cambian los detalles (la forma de los dientes, el remate de la «C», los colores) antes de publicar.

**Derecho de autor y diseño de lo hecho con IA.**
- El logo está hecho de figuras geométricas básicas, escritas a mano en el XML (`:14`, `:25`, `:37`). No parte de ninguna imagen, plantilla ni banco de iconos. Cumple R-8 del dictamen inicial («dibujos propios»).
- Si alguien reclamase que es una copia, la defensa es la **creación independiente**, y la prueba es el commit `61b8d3f` (12-11-2026), con los tres vectores. Conviene conservar ese historial (R-C2).
- Que lo dibuje Claude no le quita al dueño el derecho a usarlo. Las condiciones de Anthropic para consumidores ceden al usuario los derechos que Anthropic pueda tener sobre lo generado (**redacción vigente por confirmar**). Pero el dueño seguramente **no tendrá un derecho de autor fuerte** sobre un dibujo tan sencillo hecho por una IA. Para impedir que otro lo use, lo eficaz es registrarlo como marca (R-C1).

**El README.** `README.md:9` dice que la licencia Apache-2.0 no da permiso para usar «el nombre «Clavero» ni el icono». Es correcto: el apartado 6 de la licencia excluye las marcas, y el icono funciona como marca (no registrada) de Clavero, aunque los archivos XML del icono, como el resto del código, se distribuyen con la Apache-2.0. Con dos iconos en la historia del proyecto, conviene decir «los iconos» y aclarar ese doble plano (O-C7 y R-C4).

### 3.2 «Cristal», el aspecto de la 2.0

- **El nombre.** «Cristal» es una palabra corriente del español que describe lo que se ve: paneles que dejan ver lo de detrás. Usarlo como nombre de un estilo dentro de Ajustes es un uso descriptivo (art. 14.1.b RMUE; art. 37.1.b LM), no un uso como marca de producto. Puede haber marcas «Cristal» registradas en otras clases, alguna con renombre, pero un ajuste de aspecto dentro de una app no crea ningún vínculo con ellas. **No debe convertirse en un producto o en una marca**: nada de «Clavero Cristal», ni ™, ni un logotipo propio para «Cristal», ni ponerlo en el título de Play (O-C4).
- **El estilo no es de nadie.** El vidrio esmerilado, la translucidez y el difuminado del fondo los usan los sistemas operativos y muchas apps desde hace casi veinte años. Una técnica y un estilo no se protegen por derecho de autor (es una idea; C-406/10, *SAS Institute*, por analogía). El efecto genérico tampoco se protege como diseño (art. 8.1 RDC) ni como forma de una marca (art. 7.1.e RMUE). En EE. UU., la «apariencia» (*trade dress*) de un producto solo se protege si no es funcional y ha adquirido significado secundario (*Wal-Mart v. Samara*, 2000; *TrafFix*, 2001). Un efecto de vidrio general no cumple ninguna de las dos cosas.
- **Lo que sí sería un riesgo:**
  - copiar los **elementos concretos y característicos** del lenguaje de diseño de un fabricante: la refracción o la lente en los bordes que deforma lo de detrás, las formas «líquidas» que se funden al moverse, la forma y el comportamiento exactos de sus barras, sus botones flotantes o sus iconos. Los grandes fabricantes suelen registrar como diseño sus interfaces y sus iconos (clase de Locarno 14-04; **no he podido comprobar** registros concretos);
  - usar **sus iconos o sus fuentes** (por ejemplo, conjuntos de símbolos con licencias que solo permiten usarlos en sus propias plataformas);
  - **describirlo con su nombre** o con una traducción («cristal líquido», «vidrio líquido»), o decir que es «como el de» un sistema o una app (comparación con una marca ajena y regla del proyecto sobre nombres de otras apps).

  El diseño de «Cristal» (degradado con tres manchas, panel translúcido con un borde de 1 dp y un brillo arriba, difuminado con `RenderEffect` en barras, diálogos y hojas) es la técnica general, no la de nadie en concreto. **Está bien así.** O-C3 lo convierte en un límite para el implementador.
- **Un apunte del historial del proyecto:** el primer diseño (`docs/superpowers/specs/2026-10-23-seguridad-design.md:63`) habla del «vidrio líquido de CriptoPanel». No es un problema (era una app del propio dueño), pero confirma que «vidrio líquido» no debe volver a usarse en nada publicado, por la cercanía con el nombre del lenguaje de diseño de un fabricante presentado en 2025.
- **Cómo describirlo (orientativo, con la regla M-3):**
  - App (Ajustes → Aspecto): «Cristal: paneles translúcidos sobre un fondo de color.» Plegado: «Desde Android 12, las barras, los diálogos y las hojas difuminan lo que tienen detrás. En Android 8 a 11 son más opacos. «Reducir transparencias» los hace opacos.»
  - README, ficha y notas: «Aspecto nuevo, «Cristal»: paneles translúcidos (difuminados desde Android 12), seis paletas de color, los colores del fondo de pantalla (desde Android 12) y una personalizada. «Sólido» conserva el aspecto de antes.»
  - Sin «propio», «único», «exclusivo», «original», «inspirado en…», «al estilo de…», «glassmorphism» ni nombres de lenguajes de diseño de terceros.

### 3.3 Accesibilidad de una interfaz translúcida

**¿Se aplica la EAA?** No (§2). El dictamen inicial (`abogado-2.0-inicial.md:47`) ya lo razonó para toda la 2.0, y «Cristal» no cambia nada: sigue sin haber un producto o un servicio de los del art. 2 ni un operador económico. La accesibilidad es **voluntaria**. Lo que sí obliga es la **regla de veracidad**: el diseño promete 4,5:1 «en cada texto» y que «lo comprueba un test» (`:163`), y si los textos publicados lo cuentan, tiene que ser cierto.

**WCAG 2.2 AA como guía (R-1 del dictamen inicial).** Para una app nativa, la nota WCAG2ICT del W3C explica cómo aplicarla. Lo que toca el cristal:

| Criterio | Qué pide | Cómo está en el diseño | Qué falta |
|---|---|---|---|
| **1.4.3 Contraste mínimo** | Texto a 4,5:1 (3:1 si es grande). Sobre un fondo que varía, cuenta el punto peor. | Bien planteado: el panel mezclado con el punto más claro y con el más oscuro del fondo (`:163`). | Las **barras y los diálogos** no tienen detrás solo el fondo: tienen las filas de la lista, sus textos y sus colores (`:162`). El test tiene que medir también ese caso (R-C5). Con «Colores del fondo de pantalla», el contraste no se puede probar de antemano: hay que corregirlo al aplicarlo (R-C6). |
| **1.4.11 Contraste no textual** | Iconos, bordes de campos, interruptores e indicador de foco a 3:1. | El diseño solo mide los textos. | Medir también esos elementos sobre el cristal (R-C5). El borde de 1 dp del panel no hace falta que llegue a 3:1 si no es lo único que dice dónde está un control. |
| **1.4.1 Uso del color** | El color no es la única señal. | R-1 ya lo pide para las etiquetas. | Nada nuevo. |
| **2.4.11 Foco no oculto (mínimo)**, nuevo en 2.2 | Lo que tiene el foco no queda tapado del todo por barras fijas. | Con barras translúcidas encima de la lista, el contenido pasa por detrás. | El elemento con el foco de TalkBack o del teclado se desplaza para que no quede debajo de una barra (R-C7). |
| **2.2.2 Pausar, detener, ocultar** y **2.3.3 Animación por interacción** (AAA) | Lo que se mueve solo se puede parar; se respeta la petición de quitar animaciones. | Las manchas del fondo parecen fijas; el brillo al pulsar se quita si Android lo pide (`:165`). | Que las manchas sigan siendo fijas. Si algún día se animan, hace falta un ajuste para pararlas (R-C7). |
| **1.4.4, 1.4.10, 1.4.12** Letra al 200 %, reflujo y espaciado | Nada se corta ni se solapa. | R-1. | Comprobar los paneles con la letra al 200 % (Maestro y a mano). |
| **1.4.6 Contraste mejorado** (AAA) | 7:1. | — | Buena meta para «Alto contraste» (R-C6). |

**«Reducir transparencias», «Alto contraste» y «Sólido».** Son tres salidas para quien lee mal sobre un fondo translúcido. Están bien, y que «Alto contraste» encienda «Reducir transparencias» (`:164`) también. El nombre «Reducir transparencias» es descriptivo y lo usan varios sistemas. No hay problema en usarlo.

**El cambio al actualizar.** «Cristal» sale por defecto también al actualizar (`:170`, `:251`). Es una decisión válida, pero quien venga de la 1.4.x verá otra app de un día para otro. Una línea la primera vez, con el camino de vuelta a «Sólido», evita confusiones y ayuda a quien tenga baja visión (R-C8).

### 3.4 Temas, paletas, «Colores del fondo de pantalla» y «Personalizado»

- **Nombres de las paletas** («Noche», «Océano», «Bosque», «Atardecer», «Grafito», «Arena»): son palabras corrientes, descriptivas. No hay problema. Lo que no se puede hacer es usar como nombre (o copiar los valores exactos de) **temas conocidos que tienen nombre y licencia propios** (los de los editores de código, por ejemplo). Los valores de las seis paletas tienen que ser propios (R-C9).
- **«Colores del fondo de pantalla»:** es la descripción llana del color dinámico de Android 12. Bien. No debe llamarse con los nombres comerciales de ese sistema de color. Además, hay que corregir una frase del diseño: dice que «no se lee nada del sistema» (`:158`), pero con esta opción **la app sí recibe de Android los colores que Android calcula del fondo de pantalla** (`:172`). No lee la imagen ni necesita permisos, pero sí lee algo. Ningún texto publicado puede decir que la app «no lee nada del sistema» (O-C5).
- **«Personalizado»:** el usuario elige el acento, el fondo y la intensidad del cristal. La app corrige la luminosidad para llegar a 4,5:1 (`:173-176`). Bien. Los textos no pueden decir que la app «garantiza» que se lea (palabra prohibida en la ficha, `docs/ficha-google-play.md:3`). Se puede decir lo que hace: «Clavero ajusta los tonos para que el texto se lea con contraste suficiente».
- **Privacidad:** el tema, el estilo, la paleta y los tonos personalizados son ajustes de la app, sin secretos, en su almacenamiento privado (plan, `:203-204`). No son datos personales de nadie que salgan del móvil. La política no está obligada a contarlo, pero el dictamen inicial (O-10) ya pide que diga lo que el móvil guarda sin cifrar. Conviene añadir una línea (R-C10).
- **Las paletas en la pantalla de desbloqueo:** el color de la paleta se ve sin la maestra. No revela nada de la bóveda. No es un problema.

### 3.5 Textos y documentos que cambian, y el icono antiguo

**Dónde está todavía el icono de la 1.x:**
- **Recursos de la app:** sustituido. Solo había los tres vectores y los dos `mipmap-anydpi-v26`, sin PNG antiguos (`git ls-files`). Bien.
- **Historial de git, APK publicados y releases de GitHub (1.0 a 1.4.3):** **no hace falta quitarlo.** No consta ninguna reclamación, y la publicación pasada de un icono genérico con un escudo y una cerradura no se convierte en infracción por haber cambiado de icono. Reescribir el historial o retirar las releases antiguas sería desproporcionado. Si algún día llegara una reclamación, se valora entonces.
- **Play Console:** si hay ficha publicada, el icono de 512 px y el gráfico destacado (si lo hay) se cambian **con la publicación de la 2.0**, para que el icono de la ficha coincida con el de la app instalada.
- **GitHub:** la imagen de vista previa del repositorio (*Settings → Social preview*), si se puso una con el icono antiguo.
- **El icono cambiable (O-8 del dictamen inicial), si el dueño lo aprueba:** las variantes son del **logo nuevo**. El escudo de la 1.x no vuelve como «icono clásico» (O-C8).

**Lo que hay que escribir o cambiar** (va a la revisión final):
1. **`README.md`:**
   - la línea 9: «el nombre ni los iconos» (O-C7);
   - la línea 58 («tema claro u oscuro»): el aspecto, las paletas, «Personalizado», «Reducir transparencias» y «Alto contraste»;
   - en «Funciones», el aspecto nuevo, descrito como en §3.2;
   - las pruebas en el móvil: el icono en las formas de los lanzadores (círculo, redondeado y cuadrado) y como icono temático (monocromo); el cristal en Android 8 a 11 y desde Android 12; «Reducir transparencias»; «Alto contraste»; letra al 200 %; TalkBack sobre una barra con la lista detrás.
2. **`docs/ficha-google-play.md`:** una línea del aspecto nuevo, sin otras marcas (O-C3 y O-C4); un apartado de **recursos gráficos**: el icono es `docs/play/icono-512.png`, las capturas son nuevas, con «Cristal» y datos inventados (O-C2).
3. **«Novedades» de Play y notas de GitHub:** «Icono nuevo» y «Aspecto nuevo», sin decir por qué cambió el icono ni nombrar la app con la que se confundía (O-C2).
4. **`docs/privacidad.html`:** solo la línea de R-C10 y lo de O-C5, si se habla del fondo de pantalla.
5. **`docs/PUBLICAR.md:124`:** añadir a la lista de la ficha el icono nuevo, las capturas nuevas y lo de O-C2.
6. **Textos de la interfaz:** Ajustes → Aspecto (estilo, paleta, «Colores del fondo de pantalla», «Personalizado», «Reducir transparencias», «Alto contraste», la vista previa) y la línea de R-C8. Las descripciones de TalkBack de la vista previa y de los tonos (que nombren el color, no solo lo enseñen).
7. **«Licencias» en la app:** nada, porque el logo es propio. Si entrara alguna fuente o algún icono de terceros, sí (R-C9).

## 4. Cambios obligatorios (O-C)

**O-C1. Búsqueda de anterioridades antes de publicar la 2.0.**
- Antes de publicar el icono nuevo en Play o en una release, el dueño hace la búsqueda de §6 (figurativa y por imagen, en las clases 9, 42 y 45, y en las tiendas de apps).
- Si aparece algo cercano (una llave formada por una «C» o una letra parecida, para software o seguridad, o un icono de app del sector con la misma idea), se cambian los detalles del logo antes de publicar y se vuelve a revisar.
- Se apunta en `docs/superpowers/handoff/sdd-2.0/progress.md` que se hizo, cuándo y con qué resultado, sin datos del dueño.
- *Base:* arts. 8.1.b y 9.2 RMUE; arts. 6.1.b y 34.2 LM; política de suplantación de Google Play. Esta revisión no es una búsqueda (§7).

**O-C2. Lo que se dice del logo, en todo lo publicado.**
- No se dice que el logo es «único», «original», «exclusivo», «registrado» o «protegido», ni que «no se parece a ningún otro» o que «se ha comprobado que no infringe». Nada de ® (no hay registro). Tampoco ™.
- No se dice por qué cambió el icono, más allá de «Icono nuevo». No se nombra ni se describe la app con la que se confundía el de la 1.x, ni se dice que alguien copió a alguien.
- El icono de Play y las capturas no llevan texto de clasificación, precio ni promoción («n.º 1», «gratis», «nuevo», «2.0»), ni marcos de un móvil de una marca concreta, ni la barra de estado con notificaciones o datos de nadie (y nunca nada del dueño: regla «Privacidad del dueño»).
- *Base:* regla de veracidad; política de metadatos y de suplantación de Google Play; regla de los nombres de otras apps de `CLAUDE.md`.

**O-C3. «Cristal» no imita el diseño concreto de nadie.**
- Se usa la técnica general (translucidez, difuminado, un borde y un brillo). No se reproducen los elementos característicos del lenguaje de diseño de un fabricante: refracción o lente en los bordes, formas que se funden al moverse, la forma exacta de sus barras, botones o iconos.
- No se usan iconos, símbolos ni fuentes de terceros cuya licencia no esté en la lista de `CLAUDE.md` (Apache-2.0, MIT, BSD, ISC, CC BY). Los iconos de Material que ya usa la app (Apache-2.0) sirven.
- *Base:* arts. 10 y 19 RDC (con el Reglamento (UE) 2024/2822); TRLPI; regla de dependencias de `CLAUDE.md`.

**O-C4. Cómo se nombra y se describe «Cristal».**
- Es el nombre de un estilo dentro de Ajustes, y así se presenta: «el aspecto «Cristal»». Nunca «Clavero Cristal», ni con ™ o un logotipo propio, ni en el título de Play.
- No se describe con el nombre del lenguaje de diseño de ningún sistema o app, ni con su traducción («cristal líquido», «vidrio líquido»), ni como «al estilo de», «como el de» o «inspirado en» nadie. Tampoco los nombres comerciales del color dinámico de Android.
- Los textos publicados dicen que el difuminado es **desde Android 12**.
- *Base:* art. 14.1.b RMUE y art. 37.1.b LM (uso descriptivo); regla de los nombres de otras apps; regla de veracidad.

**O-C5. «Colores del fondo de pantalla» y lo que la app lee.**
- Ningún texto publicado (Ajustes, README, ficha, política) dice que la app «no lee nada del sistema» ni que «no usa el fondo de pantalla».
- Lo cierto, y lo que se puede decir: «La app no lee la imagen del fondo de pantalla ni pide permisos. Con «Colores del fondo de pantalla», usa los colores que Android calcula de él (desde Android 12).»
- La frase del diseño (`:158`) se corrige en ese sentido.
- *Base:* regla de veracidad; política de datos de usuario de Google Play.

**O-C6. Lo que se dice de la accesibilidad y del contraste.**
- Ningún texto dice que la app o «Cristal» son «accesibles», que «cumplen WCAG» o que el contraste está «garantizado» (como ya pedía el dictamen inicial, §3.9).
- Se puede decir lo que hace: «Reducir transparencias», «Alto contraste», «Sólido», «sigue el tamaño de letra del sistema», «ajusta los tonos personalizados para que el texto tenga contraste suficiente».
- Si algún documento publicado cuenta la cifra de 4,5:1, tiene que ser cierta en lo que dice: el test mide lo que el texto afirma, también en las barras con la lista detrás (R-C5).
- *Base:* regla de veracidad; palabras prohibidas de la ficha (`docs/ficha-google-play.md:3`).

**O-C7. El README dice «los iconos».**
- `README.md:9`: «La licencia Apache-2.0 no da permiso para usar el nombre «Clavero» ni sus iconos (el actual y los anteriores) (§6).» Y la frase siguiente, igual: «usa otro nombre y otro icono».
- *Base:* apartado 6 de la Apache-2.0; R-8 del dictamen inicial.

**O-C8. Si se aprueba el icono cambiable, sus variantes son del logo nuevo.**
- Las condiciones de O-8 del dictamen inicial se aplican sobre este logo. Las variantes cambian el color, el fondo o el contraste de la «C» con la llave, nunca el motivo.
- El escudo de la 1.x no vuelve como variante.
- La de por defecto es la de `docs/play/icono-512.png`.
- *Base:* O-8 del dictamen inicial; políticas de suplantación y de comportamiento engañoso de Google Play.

## 5. Cambios recomendados (R-C)

**R-C1. Registrar la marca, también la figurativa.** Con este logo, lo que de verdad protege a Clavero frente a imitaciones es una marca registrada, más que el derecho de autor (§3.1). Si el dueño quiere, la opción más eficiente es una **marca de la Unión Europea en la EUIPO**, en la **clase 9** («software descargable para gestionar contraseñas, cifrar datos y generar códigos de verificación»), con una descripción ajustada a lo que hace, sin pedir de más (TJUE C-371/18, *Sky v. SkyKick*). Puede ser una marca mixta (la palabra con el logo) o, mejor, dos: la denominativa «Clavero» y la figurativa. Las tasas son las vigentes de la EUIPO (**por confirmar el día de presentar**). Se tiene que usar en los cinco años siguientes al registro. Registrar no garantiza que no haya conflictos: el titular de un derecho anterior puede oponerse o pedir la nulidad. Es el pendiente 7 de `CLAUDE.md`, ampliado al logo.

**R-C2. Conservar la prueba de la creación.** No reescribir ni borrar el commit `61b8d3f` y los siguientes que toquen el logo. Si algún día se modifica, que quede en un commit propio y con fecha. Esa es la prueba de la creación independiente y de la fecha, si alguien reclama.

**R-C3. Que se reconozca en pequeño.** En 24 px los dientes casi desaparecen y queda «una C en un círculo», el recurso más común (§3.1). Si el Ingeniero Móvil Senior no ve inconveniente, dientes algo más largos o más separados, o una recta algo más larga, mantienen la llave a la vista en los tamaños de las notificaciones y de Ajustes. Así es más distintivo, que es lo que protege. Es una recomendación de distintividad, no un problema legal.

**R-C4. La licencia de los archivos del icono, clara.** Los XML del icono están en el repositorio con la Apache-2.0, como el resto del código. Eso permite copiar los archivos, pero no usar el dibujo como signo de otra app (apartado 6). Se puede añadir, junto a la línea de O-C7: «Los archivos del icono están en el repositorio con el resto del código, pero el dibujo identifica a Clavero: una versión modificada tiene que usar otro.» Evita malentendidos sin cambiar la licencia.

**R-C5. El test de contraste, con el peor caso real.**
- Las barras, los diálogos y las hojas se miden también con lo que puede pasar por detrás: las filas de la lista, sus textos y sus colores, no solo el fondo. Lo más sencillo y robusto: su opacidad mínima es la que da 4,5:1 sobre blanco puro y sobre negro puro.
- Los iconos, los bordes de los campos, los interruptores y el indicador de foco, a 3:1 (WCAG 1.4.11).

**R-C6. Contraste del color dinámico y del alto contraste.**
- Con «Colores del fondo de pantalla», los colores que da Android pasan por la misma corrección que «Personalizado» antes de usarse (R-1 del dictamen inicial: el contraste se comprueba también con el color dinámico).
- En «Alto contraste», la meta es 7:1 (WCAG 1.4.6, AAA).
- Desde Android 14, si el sistema pide más contraste (ajuste de contraste del sistema), la app puede subir el suyo o encender «Reducir transparencias». El Ingeniero Móvil Senior decide cómo.

**R-C7. Foco y movimiento.**
- El elemento que tiene el foco de TalkBack o del teclado no queda tapado por una barra translúcida (WCAG 2.4.11).
- Las manchas del fondo siguen siendo fijas. Si algún día se animan, un ajuste las para, y se respeta la petición de Android de quitar las animaciones (WCAG 2.2.2 y 2.3.3).

**R-C8. Una línea tras actualizar.** La primera vez que se abre la 2.0 encima de la 1.4.x: «Aspecto nuevo. Para volver al de antes: Ajustes → Aspecto → Sólido.» Con «Entendido». Se puede juntar con el aviso del paso a v4 (O-3 del dictamen inicial), siempre que no lo tape.

**R-C9. Paletas y fuentes propias.** Los valores de las seis paletas se eligen para Clavero. No se toman los de temas conocidos con nombre y licencia propios, y no se les ponen esos nombres. Si se añade alguna fuente o algún icono, con una licencia de la lista de `CLAUDE.md` y en la pantalla de licencias. Ojo: muchas fuentes libres usan la SIL OFL, que no está en esa lista.

**R-C10. Una línea en la política.** En la parte de lo que el móvil guarda sin cifrar (O-10 del dictamen inicial): «Los ajustes de aspecto (modo, estilo, paleta y tonos personalizados) se guardan en el móvil, en el almacenamiento privado de la app, sin cifrar: no son secretos ni salen del móvil. El logo y los temas no añaden permisos.» Más la frase de O-C5 si se habla del fondo de pantalla.

## 6. Lo que tiene que hacer el dueño

Es la búsqueda de O-C1. No hace falta un abogado para hacerla. Si aparece algo cercano, se para y se consulta antes de publicar.

1. **Marcas figurativas en TMview** (tmdn.org/tmview, que incluye la OEPM, la EUIPO y las oficinas nacionales de la UE) y en la **WIPO Global Brand Database** (branddb.wipo.int, que incluye las marcas internacionales y muchas oficinas de fuera de la UE, entre ellas la de EE. UU.). Las dos permiten buscar **por imagen**: subir `docs/play/icono-512.png` (el repositorio ya es público) y, aparte, la versión monocroma.
   - **Clases de Niza:** 9 (software descargable), 42 (software como servicio y diseño de software) y 45 (servicios de seguridad).
   - **Territorios:** la UE y España primero; después las demás oficinas que ofrezca la búsqueda, porque Play publica casi en todo el mundo.
2. **Por códigos de la Clasificación de Viena** (los elementos figurativos de las marcas). Los de abajo son de la 5.ª edición, comprobados en el texto de la OMPI. **La vigente es la 10.ª, desde el 1-1-2026: confirmar cada código en ella** antes de buscar (nivilo.wipo.int). Combinarlos, siempre con las clases 9, 42 o 45:
   - **14.5.1** «Llaves, partes de llaves», y sus auxiliares: **A 14.5.2** (una llave), **A 14.5.9** (partes de llaves), **A 14.5.12** (llaves con el ojo circular u oval, que es lo que hace la «C») y **A 14.5.15** (llaves de otra forma);
   - **27.5.1** «Letras con una forma especial de escritura», con **A 27.5.21** (una letra), **A 27.5.8** (letras unidas a un elemento figurativo) y **A 27.5.24** (letras claras sobre fondo oscuro);
   - **26.1.1** (círculos), con **A 26.1.18** (círculos que contienen letras), y **26.1.4** (dos círculos, uno dentro del otro);
   - **29.1.2** (amarillo, oro) y **29.1.5** (violeta), o **29.1.12** (dos colores predominantes), para afinar.

   La búsqueda que más importa es **14.5.1 + 27.5.1 (o A 27.5.8) en la clase 9**. Por si alguna vez sirve para la 1.x, los escudos son la división **24.1** y las cerraduras, **14.5.21**.
3. **Las tiendas de apps.** En Google Play y en la App Store, buscar «gestor de contraseñas», «2FA», «autenticador», «bóveda», «cifrado», «cartera», «llave» y «key», y mirar los iconos de las primeras páginas. Es donde un usuario vería a Clavero al lado de otra app. Se pueden añadir búsquedas inversas de imagen generales con `icono-512.png`.
4. **Diseños registrados en DesignView** (tmdn.org/designview): clases de Locarno **14-04** (pantallas e iconos) y **32-00** (símbolos gráficos y logotipos) (**confirmar en la edición vigente**).
5. **La palabra «Clavero»**, junto con CLAVIS y CLAVISTER, en la clase 9: el pendiente 7 de `CLAUDE.md`, que conviene hacer a la vez.
6. **Apuntar** en `progress.md` la fecha, las bases consultadas y el resultado («sin anterioridades cercanas» o lo que haya). Nada de datos personales.
7. **Si quiere,** registrar la marca (R-C1).
8. **Al publicar en Play,** subir el icono nuevo y las capturas nuevas con la 2.0 (O-C2), y cambiar la vista previa del repositorio en GitHub si tenía el icono antiguo.

## 7. Límites de este dictamen

- **No he podido hacer una búsqueda de anterioridades.** Desde esta sesión no tengo acceso fiable a las búsquedas por imagen de TMview, de la EUIPO, de la OMPI ni de las tiendas de apps, y no he comparado el logo con ninguna base de datos. Lo que digo del riesgo de confusión sale de mirar el dibujo y de lo que conozco de los iconos del sector. **No garantiza que no exista una marca, un diseño o un icono anterior parecido.** Por eso la búsqueda del dueño es obligatoria (O-C1).
- **No conozco la app con la que se confundía el icono de la 1.x** ni sus derechos. No valoro si el icono antiguo infringía algo. Cambiarlo es prudente, sin más.
- **No he podido comprobar** los registros de diseño concretos de los lenguajes de diseño de otros fabricantes, la redacción vigente de las políticas de metadatos de Play, la de las condiciones de Anthropic sobre lo generado, la fecha de aplicación exacta del Reglamento (UE) 2024/2822, el alcance del RD 193/2023 para un particular ni los códigos de la 10.ª edición de Viena. Cada uno está marcado **por confirmar** donde aparece.
- **La accesibilidad** la reviso en lo jurídico y en lo que se promete. Medir el contraste real, TalkBack y la letra al 200 % es de las pruebas (tests y Maestro) y del Ingeniero Móvil Senior.
- Es un borrador profesional para el dueño. No sustituye el asesoramiento de un abogado colegiado, ni una búsqueda de anterioridades profesional si el dueño decide registrar la marca.

## 8. Fuentes consultadas (12-11-2026)

- Clasificación de Viena, 5.ª edición (OMPI), texto en PDF: https://www.wipo.int/classifications/data/vienna/ITSupport_and_download_area/20030101/pdf/vcl_5_en_20030101.pdf (divisiones 14.5, 24.1, 26.1, 27.5 y 29.1).
- Clasificación de Viena, ediciones y vigencia (la 9.ª del 1-1-2023 al 31-12-2025; la 10.ª desde el 1-1-2026): https://nivilo.wipo.int/viennalegacy.htm y https://www.wipo.int/en/web/classification-vienna; resumen del INEGI: https://inegi.org.mx/contenidos/app/scian/clasifiviena.pdf
- Google Play, política de metadatos (título, icono y nombre del desarrollador sin clasificación, precio ni promoción): https://support.google.com/googleplay/android-developer/answer/9898842 y la cobertura de su entrada en vigor el 29-9-2021: https://www.apptamin.com/blog/new-aso-rules-coming-to-the-play-store
- Condiciones de Anthropic para consumidores (lo generado): https://www.anthropic.com/legal/consumer-terms (**redacción vigente por confirmar**).
- Las demás normas y sentencias citadas, de memoria profesional y de los dictámenes anteriores del proyecto. No he consultado EUR-Lex ni el BOE directamente en esta sesión.

## Revisión experto-seguridad-cripto

(Notas del Abogado para el experto; él las confirma.)
- **Nada de este dictamen toca la seguridad:** ningún cambio pide red, permisos, componentes, datos fuera de la bóveda ni secretos en los ajustes. `FLAG_SECURE` y la lista de recientes no cambian (diseño, `:166`).
- **O-C5 es de veracidad, no un riesgo:** el color dinámico lo da Android; la app no lee la imagen del fondo de pantalla (`:158` frente a `:172`).
- **R-C10:** los ajustes de aspecto no llevan secretos (plan, `:203-204`, `PlatformSourcesTest`: «nada secreto en el estado del tema»).

## Revisión Ingeniero Móvil Senior

(Notas del Abogado para el Ingeniero; él las confirma.)
- **Logo:** dentro de la zona segura (`ic_launcher_foreground.xml:12`, escala 0,9), con versión monocroma (`ic_launcher_monochrome.xml`) y los dos `mipmap-anydpi-v26` apuntando a los tres vectores. El oro sobre el fondo tiene un contraste de 6,6:1 a 12,5:1 (medido con los colores de `:25` y de `ic_launcher_background.xml`), bien para un icono.
- **`docs/play/icono-512.png`** es PNG RGB de 8 bits por canal (24 bits, sin alfa) y 142 KB. La guía de Play pide un PNG de 32 bits, cuadrado y sin sombras, de hasta 1024 KB: **comprobar al subirlo** que Play Console lo acepta y, si no, exportarlo con canal alfa.
- **R-C3** (dientes más visibles en pequeño), **R-C5** (contraste con la lista detrás de las barras y 3:1 de los elementos no textuales), **R-C6** (corrección del color dinámico, 7:1 en «Alto contraste», el contraste del sistema desde Android 14) y **R-C7** (foco no tapado, manchas fijas): son técnicos, y el Ingeniero decide cómo y si alguno tiene un coste que no compense. Si alguno no se hace, se apunta la razón.
- **O-C3:** el difuminado con `RenderEffect` recortado al panel es la técnica general y no imita la refracción de ningún sistema. Mantenerlo así.

## Textos para el Abogado Digital (revisión final)

- Ajustes → Aspecto: los nombres y las líneas de «Cristal», «Sólido», las seis paletas, «Colores del fondo de pantalla», «Personalizado» (acento, fondo, intensidad, vista previa), «Reducir transparencias» y «Alto contraste», con sus textos plegados y sus descripciones de TalkBack.
- La línea tras actualizar (R-C8).
- `README.md:9` (O-C7 y R-C4), `README.md:58` y la descripción del aspecto en «Funciones».
- La línea del aspecto en `docs/ficha-google-play.md`, su apartado de recursos gráficos y las capturas nuevas (O-C2).
- Las «Novedades» de Play y las notas de GitHub: «Icono nuevo» y «Aspecto nuevo» (O-C2, O-C4).
- `docs/privacidad.html` (R-C10, O-C5) y `docs/PUBLICAR.md:124`.
- La corrección del diseño en `:158` (O-C5).
- El apunte de la búsqueda de O-C1 en `progress.md`.

*Borrador profesional para revisión del dueño. No sustituye el asesoramiento de un abogado colegiado en cada jurisdicción de publicación ni una búsqueda de anterioridades profesional.*
