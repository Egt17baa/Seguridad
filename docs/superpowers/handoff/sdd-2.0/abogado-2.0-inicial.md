# Clavero 2.0: revisión inicial del Abogado Digital (antes de programar)

- **Fecha de referencia:** 12-11-2026.
- **Objeto:** el diseño `docs/superpowers/specs/2026-11-12-clavero-2.0-design.md` (borrador del 12-11-2026, base `main` en `a3ac942`). Todavía no hay código ni textos de la interfaz, así que reviso **lo que el diseño permite y lo que obliga a decir**. Los textos exactos se revisan al final, en la revisión final de siempre (`CLAUDE.md`, punto 4).
- **Skills:** `despacho-digital`, `privacidad-proteccion-datos`, `regulacion-digital-ue` y `propiedad-intelectual-ia`.
- **También leídos:** `CLAUDE.md`, `docs/privacidad.html` (versión del 10-11-2026), `docs/ficha-google-play.md`, `app/src/main/AndroidManifest.xml`, el dictamen de la 1.4.2 (`docs/superpowers/handoff/sdd-1.4.2/abogado-1.4.2.md`) y el mapa normativo del dictamen de la 1.4.0 (`sdd-1.4.0/abogado-1.4.0.md`, §4).
- **Hechos que doy por ciertos (los de siempre):**
  - el autor es un particular sin ánimo de lucro, con el seudónimo Egt17baa, residente en España;
  - la app es gratuita, sin anuncios, compras ni donaciones, con licencia Apache-2.0 y código abierto;
  - no recibe datos de nadie: no hay servidor, cuentas ni analítica; la única conexión es la consulta de Pwned Passwords cuando el usuario la pide;
  - en Google Play: «No recoge ni comparte datos», desarrollador «no comerciante», fuera de Francia (con ultramar), Rusia y Bielorrusia;
  - **lo nuevo de la 2.0 sigue sin red nueva, sin permisos nuevos (salvo el icono, si el dueño lo aprueba), sin dependencias nuevas, sin segundo plano y sin notificaciones** (diseño, «No objetivos»).

Si alguno de estos hechos cambia (donaciones, anuncios, una versión de pago, datos que lleguen al desarrollador, un servidor de sincronización), hay que volver a hacer el análisis entero. Lo más sensible en ese caso es el Cyber Resilience Act: un gestor de contraseñas es un «producto importante» (véase §2 y §7).

## 1. Conclusión

**Visto bueno con cambios.** Todo lo que propone el diseño de la 2.0 se puede hacer cumpliendo la ley española, la europea y la internacional que aplica. **Ninguna norma prohíbe nada del diseño ni obliga a quitar ninguna función.** Con los hechos de arriba, la 2.0 no cambia el análisis de la 1.4.x:

- el RGPD y la LOPDGDD no se aplican al desarrollador, porque no trata datos de nadie (todo queda en el móvil del usuario, que actúa en el ámbito doméstico);
- el CRA, la nueva Directiva de productos defectuosos, la DSA, la LSSI, la Ley de accesibilidad y la Directiva de prácticas comerciales desleales no se aplican, porque no hay actividad comercial ni comerciante;
- las plantillas de tarjeta bancaria y de pasaporte no convierten a Clavero en una app de pagos ni de identidad: son notas estructuradas dentro de la bóveda cifrada.

Los **cambios obligatorios** salen de tres fuentes, como en las versiones anteriores:

- **la regla de veracidad del proyecto:** lo publicado y lo que dice la app no afirman nada que el código no haga;
- **la regla de «Texto mínimo» (M-3):** los avisos que hay que ver no se pliegan;
- **las políticas de Google Play**, que obligan como contrato de distribución: suplantación, comportamiento engañoso, datos de usuario y las declaraciones de Play Console.

Son once (O-1 a O-11, §4). **Ninguno obliga a quitar una función.** Diez son condiciones sobre los textos o sobre cómo se enseña algo. Uno, O-6, limita dos detalles de las «misiones» de «Salud»: que ninguna lance la red por sí sola y que no lleven una lista de servicios de terceros. **El icono cambiable tiene visto bueno** si se cumplen las condiciones de O-8. Si el dueño no lo aprueba, O-8 no hace falta.

## 2. Mapa normativo (12-11-2026)

| Norma | ¿Se aplica? | Motivo | ¿Cambia con la 2.0? |
|---|---|---|---|
| **RGPD** (Reglamento (UE) 2016/679) y **LOPDGDD** (LO 3/2018) | No, al desarrollador. | No hay responsable del tratamiento (art. 4.7 RGPD): el desarrollador no decide fines ni medios de ningún tratamiento ni recibe nada. Lo que el usuario guarda (también los datos de terceros, como el pasaporte de un familiar o la tarjeta de su pareja) es actividad exclusivamente personal o doméstica (art. 2.2.c RGPD; considerando 18; C-101/01, *Lindqvist*, y C-212/13, *Ryneš*, como criterio). | No. Lo nuevo es local. La política sigue dando, por transparencia, la información de los arts. 13 y 14 como pauta (O-10). |
| **Derecho de supresión** (art. 17 RGPD) | No, frente al desarrollador. | No tiene datos que suprimir. La papelera y el historial son herramientas del usuario sobre sus propios datos. Lo que sí rige es la veracidad: si la app dice «Borrar para siempre», tiene que ser cierto en lo que dice (O-2). | — |
| **LSSI** (Ley 34/2002) | No. | Anexo, letra a): un servicio gratuito solo es «servicio de la sociedad de la información» si es actividad económica del prestador, y no lo es. | No. |
| **TRLGDCU** (RDL 1/2007) y **Directiva (UE) 2019/770** | No. | No hay precio ni datos como contraprestación (art. 3.1 de la Directiva); queda fuera el software libre gratuito (art. 3.5.f); el particular no es «empresario» (art. 4 TRLGDCU). | No. |
| **Prácticas comerciales desleales** (Directiva 2005/29/CE; Ley 3/1991 y TRLGDCU) | No. | Solo se aplica a prácticas de un «comerciante» (art. 2.b Directiva 2005/29/CE). Relevante para «Salud» como misiones: no hay norma que la prohíba ni la regule aquí. La recomendación de diseño (R-2) es de buena práctica. | No. |
| **«Digital Fairness Act»** (iniciativa de la Comisión sobre diseño engañoso y adictivo) | — | **No he podido verificar si hay propuesta publicada a fecha de hoy.** Previsiblemente iría dirigida a comerciantes, como la Directiva 2005/29/CE. | — |
| **DSA** (Reglamento (UE) 2022/2065) | No. | No presta servicios intermediarios (art. 3.g). La prohibición de interfaces engañosas (art. 25) es solo para plataformas en línea. | No. |
| **CRA** (Reglamento (UE) 2024/2847) | No, mientras no se monetice. | Solo cubre productos comercializados en el curso de una actividad comercial (art. 3, definiciones de «comercialización» y «puesta a disposición»; considerandos 18 y 19). El software libre que su fabricante no monetiza queda fuera. **Calendario (art. 71.2):** notificación de vulnerabilidades e incidentes (art. 14) desde el 11-9-2026; organismos notificados (capítulo IV) desde el 11-6-2026; aplicación plena el 11-12-2027. **Atención:** los «gestores de contraseñas» figuran entre los productos importantes de la clase I (anexo III; creo que es el punto 3, **numeración por confirmar**). Si algún día hay actividad comercial, la evaluación de la conformidad sería más exigente que la del régimen general. | No. Lo apunto como riesgo si cambian los hechos (§7). |
| **Directiva (UE) 2024/2853** (productos defectuosos) | No. | Su art. 2.2 excluye el software libre y de código abierto desarrollado o suministrado fuera de una actividad comercial. Plazo de trasposición: 9-12-2026 (art. 22); se aplica a los productos introducidos en el mercado desde esa fecha. La 2.0 seguramente se publicará después de esa fecha, y la exclusión sigue valiendo. **No he encontrado texto de trasposición español publicado** (fuentes secundarias: el Ministerio de Justicia la prepara): **pendiente de verificar a fecha de hoy** en el BOE. | Relevante como criterio: la Directiva cuenta como daño indemnizable «la destrucción o corrupción de datos que no se utilicen con fines profesionales» (art. 6.1.c). Es justo el riesgo del paso a v4 (O-3). |
| **Responsabilidad civil general** (arts. 1101, 1102 y 1902 del Código Civil) | Riesgo residual. | Un desarrollador gratuito puede responder por culpa extracontractual (art. 1902 CC) en teoría. La exención de la licencia Apache-2.0 (apartados 7 y 8) no cubre el dolo (art. 1102 CC) y, en mi opinión, tampoco la culpa grave. Probabilidad muy baja. Las medidas del diseño (copia antes del paso a v4, papelera, deshacer) la reducen. | Igual que antes, salvo el paso a v4, que es el momento de más riesgo (O-3, R-6). |
| **AI Act** (Reglamento (UE) 2024/1689) | No. | Nada de lo nuevo es un «sistema de IA» (art. 3.1; considerando 12): la búsqueda que perdona erratas (Damerau-Levenshtein), reconocer lo pegado y las misiones son reglas deterministas escritas por personas. | No. |
| **Accesibilidad**: Directiva (UE) 2019/882 (EAA) y Ley 11/2023, de 8 de mayo | No. | Un gestor de contraseñas no es ninguno de los productos (art. 2.1) ni de los servicios (art. 2.2) que cubre la Directiva, y no hay operador económico. Se aplica desde el 28-6-2025 (art. 31). Las Directivas y el RD 1112/2018 del sector público tampoco. La accesibilidad de la 2.0 es voluntaria y buena práctica (R-1). | No. |
| **Data Act** (Reglamento (UE) 2023/2854) | No. | No es un producto conectado ni un servicio de tratamiento de datos. | No. |
| **PCI DSS** | No. | Es un estándar contractual de las marcas de tarjetas para quien almacena, procesa o transmite datos de titulares como comerciante o proveedor de servicios. El titular que apunta su tarjeta no está obligado, y el desarrollador no toca esos datos. Como criterio, sí sirve para el CVV (R-3). | — |
| **Marcas** (Reglamento (UE) 2017/1001, RMUE, y Ley 17/2001, LM) | Sí, como límite. | Las plantillas, el QR de wifi y el icono no pueden usar marcas ajenas más allá del uso descriptivo o referencial leal (art. 14.1.b y c y art. 14.2 RMUE; art. 37.1.b y c y art. 37.2 LM). Véanse O-8, O-9 y R-4. | Sí: tarjetas, wifi (WPA, WPA2, WPA3 y «Wi-Fi» son marcas de la Wi-Fi Alliance) e icono. |
| **Derecho de autor** (TRLPI; Directiva 2009/24/CE) | Sí, como límite. | El lector de notas con formato, la búsqueda y el QR se escriben desde la idea o la especificación, que no están protegidas (art. 96.4 TRLPI; C-406/10, *SAS Institute*). Cuidado con copiar los ejemplos o tests de especificaciones con licencia «compartir igual» (R-5). Los dibujos de las variantes del icono tienen que ser propios o con licencia permisiva (R-8). | Sí. |
| **Control de exportaciones** (Reglamento (UE) 2021/821) | Sin cambios. | La 2.0 no añade algoritmos ni funciones criptográficas nuevas: usa el mismo AES-256-GCM y el mismo Argon2id. | No. |
| **Políticas de Google Play** | Sí, como contrato. | Suplantación y comportamiento engañoso (O-8, O-9), datos de usuario y la sección «Seguridad de los datos» (O-10, O-11), la declaración de funciones financieras y la de apps de salud (O-11). | Sí: icono, tarjetas, «Salud». |
| **Francia** (Ley 2004-575, art. 30), **Rusia y Bielorrusia** | Fuera de la publicación. | Decisión del dueño. | No. |
| **«Digital Omnibus»** (propuesta de noviembre de 2025 que toca el RGPD, la ePrivacy y la Data Act) | — | No he encontrado su adopción: la parte del RGPD sigue en el Consejo y el Parlamento (el «AI Omnibus», separado, sí se adoptó en 2026). **Pendiente de verificar a fecha de hoy.** La 2.0 no depende de él. | — |

## 3. Análisis de los puntos pedidos

### 3.1 Varias bóvedas con los nombres sin cifrar en el móvil

- **Legal:** el registro `files/vaults.json` guarda el nombre, el color, el orden y la última abierta de cada bóveda; `settings.json` guarda los ajustes de cada una, como las carpetas de sus copias. Todo queda en el almacenamiento privado de la app y fuera de las copias de Android (`AndroidManifest.xml:11`, `allowBackup="false"`). El desarrollador no lo recibe, así que no hay tratamiento suyo. Que el usuario lo acepte es lícito.
- **Lo que importa es lo que se le dice al usuario.** El nombre de una bóveda revela algo («Trabajo», «Médico», el nombre de una persona) a quien tenga el móvil desbloqueado, root o una copia forense. El modelo de amenaza del diseño ya lo reconoce. El usuario solo lo puede evitar si lo sabe **cuando escribe el nombre**: es un aviso que hay que ver, no un texto plegado (O-1).
- **Lo que no se puede decir:** que varias bóvedas sirven para «ocultar» o «esconder» una bóveda, que una es «privada», «secreta» o «invisible», o algo parecido a una negación plausible. Los nombres y el número de bóvedas se ven sin la maestra. El diseño descarta con acierto la bóveda señuelo (decisión 5). Presentarla así sería engañoso (regla de veracidad; política de comportamiento engañoso de Play: el título, el icono y la descripción tienen que reflejar lo que hace la app).
- **Borrar una bóveda:** el diseño ya dice que se avisa de que las copias hechas siguen existiendo. Bien. Hay que añadir las copias de antes de importar y las de antes de la 2.0 (O-2).

### 3.2 Plantillas de tarjeta bancaria y de pasaporte

- **No son «categorías especiales»:** ni el número de una tarjeta ni el de un pasaporte son datos del art. 9 RGPD. El número de pasaporte es un «número nacional de identificación» (art. 87 RGPD), pero esa norma habilita a los Estados a regular su tratamiento por responsables, y aquí no hay responsable. **Fuera del RGPD por el art. 2.2.c**, como todo lo demás de la bóveda.
- **PCI DSS** no se aplica (§2). Como criterio de prudencia: el estándar prohíbe a comerciantes y proveedores guardar el código de verificación (CVV) después de autorizar el pago (creo que es el requisito 3.3.1 de la versión 4.0.1, **numeración por confirmar**). No hay que prohibirlo al usuario, que guarda su propia tarjeta, pero conviene que el campo sea opcional y que no se le sugiera rellenarlo (R-3).
- **Google Play:**
  - la plantilla de tarjeta no es una «función financiera»: no hay pagos, préstamos, banca ni cartera de criptoactivos. La **declaración de funciones financieras** de Play Console, que todas las apps tienen que rellenar, sigue en «Mi app no ofrece funciones financieras» (O-11). Los textos no deben llamar «cartera» o «monedero» a la bóveda de tarjetas («wallet» ya está prohibida);
  - «Seguridad de los datos» sigue en «No recoge ni comparte datos»: los datos financieros y los identificadores oficiales no salen del móvil salvo en la copia cifrada que el usuario exporta o manda a su carpeta, como hoy. **Redacción vigente por confirmar** en Play Console antes de enviar (O-11).
- **Marcas y suplantación:**
  - el diseño ya dice «Sin validar marcas de tarjetas ni nombrarlas». Lo hago obligatorio (O-9): sin nombres ni logotipos de marcas de tarjetas ni de bancos, tampoco en los textos de ejemplo de los campos;
  - el pasaporte no puede llevar escudos, banderas ni un diseño que imite el documento oficial. La política de suplantación de Play pone como ejemplo de infracción una app que usa un emblema nacional para sugerir relación con un gobierno. Ningún texto puede decir que lo guardado vale como documento o lo sustituye (O-9).
- **Nombre de la plantilla:** «Tarjeta» ya significa en Clavero «la tarjeta de un contacto» (`docs/privacidad.html`: «no hay servidor que reciba tarjetas»; ficha: «pegando su tarjeta»). Con la plantilla nueva, esa frase de la política se vuelve ambigua (O-10). Recomiendo «Tarjeta bancaria» (R-4).

### 3.3 Historial y papelera: que el usuario pueda borrar del todo

- **No hay obligación legal** frente al desarrollador (§2, art. 17 RGPD). Pero el diseño promete mucho con los nombres «Borrar para siempre», «Vaciar papelera» y «Borrar historial», y hay que mirar qué queda después, sin plegar nada:
  1. **Las copias ya hechas:** las exportadas, las automáticas (en hasta 3 destinos), las de antes de importar y la de antes de la 2.0 llevan la papelera y el historial de ese momento («sí va en las copias», diseño, «Papelera»). Borrar en la bóveda no las toca.
  2. **El vaciado a los 30 días** solo ocurre al abrir la bóveda. Si no se abre, lo de la papelera sigue ahí. El texto no puede decir «se borra a los 30 días» sin más.
  3. **El almacenamiento físico:** cada guardado escribe un archivo nuevo. Los bloques viejos pueden seguir en la memoria flash hasta que se sobrescriban. Como la clave de datos no cambia (diseño, «Paso de v3 a v4»), quien recupere un archivo viejo con la maestra lo abriría. Esto no lo puede arreglar la app, pero sí puede no prometer lo contrario: nada de «irrecuperable», «sin dejar rastro», «borrado seguro» ni «de verdad» (que además está en `ForbiddenWords`).
  4. **Cambiar la maestra:** las copias viejas siguen abriéndose con la maestra anterior (A6), y con ellas su papelera y su historial.
- La regla de veracidad obliga a que los textos de esos botones digan lo esencial a la vista (O-2). Cuando el usuario elige «Borrar para siempre», normalmente es porque quiere que algo desaparezca (una contraseña que alguien vio, un dato de otra persona). Es un aviso que tiene que ver.
- **Una incoherencia del diseño que hay que resolver antes de escribir los textos:** el tope del historial es «20 versiones y 64 KiB por entrada» en «Topes» y «hasta 20 versiones y 256 KiB» en «Historial por entrada». Los textos que lo digan necesitan una cifra (O-2).
- **Lo que está bien:** todo borrado pasa por la papelera, hay «Deshacer», confirmaciones con el nombre, «Borrar historial» por entrada y los adjuntos se borran al vaciar. Es la parte de «que el usuario pueda borrar» que sí está en su mano, y el diseño la cubre.

### 3.4 Notas con formato

- **Sin problema legal.** Que no haya enlaces, HTML, imágenes ni WebView es además lo que mantiene ciertas dos frases de la política: que la app no se conecta salvo a Pwned Passwords y que lo importado nunca se abre (decisión (c) de la 1.4.0).
- **Propiedad intelectual:** la sintaxis (`**`, `*`, `#`, `-`, `1.`) es una idea y un formato, no una obra protegida (art. 96.4 TRLPI; C-406/10). Dos cautelas (R-5):
  - no copiar al repositorio los ejemplos ni los tests de especificaciones publicadas: la de CommonMark se publica, según recuerdo, con licencia CC BY-SA 4.0 (**por confirmar**), que es de «compartir igual» y no está en la lista de licencias permitidas de `CLAUDE.md`. Los tests se escriben desde cero, como los lectores de la 1.4.0;
  - en la interfaz, decir «formato» o «negrita, cursiva, títulos y listas», sin llamarlo «Markdown». No tengo claro si es una marca registrada (**por confirmar**), y no hace falta nombrarlo.

### 3.5 QR de un wifi

- **Sin problema legal.** La contraseña es del usuario, y compartirla es su decisión. El aviso «quien vea el código ve la contraseña» es el que exige la veracidad. Que esté a la vista ya lo dice el diseño (O-4 lo recoge junto con los demás avisos).
- **Marcas:**
  - «WPA», «WPA2» y «WPA3» son marcas de la Wi-Fi Alliance, y nombrarlas para decir qué tipo de seguridad tiene la red es un uso descriptivo leal (art. 14.1.b RMUE; art. 37.1.b LM). Basta con el texto, sin logotipos ni ®;
  - para el nombre del tipo, «wifi» en minúscula, que es la palabra española recogida por la RAE, mejor que «Wi-Fi», que es la marca (R-4);
  - la etiqueta del campo puede ser «Seguridad», que no está prohibida, pero no se puede decir que una red es «segura» ni «no segura» (`ForbiddenWords`). Mejor «Tipo de seguridad: WPA2» o «Abierta (sin contraseña)».
- **El formato `WIFI:T:…;S:…;P:…;H:;;`** se usa desde la documentación de ZXing (Apache-2.0), que ya está en el APK y en «Licencias». Sin cambios en las licencias.

### 3.6 Avisos de caducidad sin notificaciones

- **Sin problema legal**, y además ayuda a no prometer de más: sin notificaciones no hay datos en la pantalla de bloqueo ni permiso `POST_NOTIFICATIONS`.
- **Veracidad:** el texto de la opción no puede dar a entender que Clavero avisará cuando algo caduque si la app no se abre. Lo mismo con el calendario de copias, que es aún más importante porque fiarse de él puede costar los datos (O-4).

### 3.7 «Salud» como misiones (prácticas engañosas)

- **Ninguna norma se aplica** (§2: la Directiva 2005/29/CE pide un comerciante; el art. 25 DSA, una plataforma en línea). Como criterio, sirven las Directrices 03/2022 del CEPD sobre patrones de diseño engañosos en las interfaces de las redes sociales (versión 2.0, de 2023). Se escribieron para redes sociales, pero describen los patrones que hay que evitar: *nagging* (insistir), *confirmshaming* (avergonzar al que dice que no), urgencia falsa y opciones desiguales.
- **El diseño ya lo hace bien:** sin rachas, puntos, cuentas atrás ni culpa, con «Ahora no» y calculando lo mismo que hoy. R-2 concreta cómo seguir así.
- **Dos puntos sí son obligatorios** por las reglas del proyecto (O-6):
  1. **La red.** La política dice que la app solo consulta Pwned Passwords «cuando pulsas «¿Filtrada?» o «Comprobar filtraciones»». Una misión como «Comprueba si tus contraseñas están filtradas», con un botón que lance la consulta, haría falsa esa frase. La misión puede llevar a la pantalla de «Salud», donde el usuario pulsa el botón de siempre; o la política y el aviso de la red tienen que nombrar el botón nuevo.
  2. **Los nombres de terceros.** «Activa el 2FA en …» solo puede rellenarse con el título que el usuario dio a su entrada. Si se pensara meter en la app una lista de servicios que admiten 2FA, la app nombraría marcas fuera de `imports/ImportTexts.kt` (regla «Nombres de otras apps»). Además, esa lista tendría su propia licencia y habría que mantenerla. Sería otra decisión, y no la doy por aprobada.

### 3.8 Modo sencillo y guía inicial

- **Sin problema legal.** Ocultar herramientas no es engañoso si se dice dónde se encienden, y el diseño lo dice.
- **Condición (O-7):** saltarse la guía no puede saltarse los avisos de «Antes de empezar» (`ui/screens/NoticeScreen.kt:32`): sin recuperación, lo que sale del móvil y sin auditoría independiente. El modo sencillo oculta herramientas, nunca avisos ni ajustes de protección (bloqueo, portapapeles, huella). Es la misma regla que la O-3 de la 1.4.2.
- **Detalle de coherencia:** una misión como «Haz y comprueba una copia» lleva a «Comprobar una copia», que en el modo sencillo está oculta (diseño, «Modo sencillo»). La misión tiene que llevar a la herramienta igualmente, o no mostrarse. No es un problema legal, pero si la misión lleva a una pantalla que no existe, promete algo que no hace.

### 3.9 Accesibilidad (TalkBack, letra grande, contraste)

- **No es obligatoria** (§2), pero el diseño la quiere, y tiene sentido usar como guía EN 301 549 (la norma armonizada que remite a WCAG 2.1; la versión con WCAG 2.2 está en preparación, **por confirmar**) y WCAG 2.2, nivel AA. Los puntos del diseño que la tocan, con el criterio WCAG de cada uno, están en R-1. Los más importantes:
  - **deslizar y arrastrar** tienen alternativa en el menú («Editar», «Mover a la papelera», «Mover a…»): bien (WCAG 2.5.1 y 2.5.7);
  - **los tiempos cortos** («Deshacer» 5 s, «Copiar contraseña» 30 s, el 2FA que copia el siguiente con menos de 5 s) no llegan a quien usa TalkBack o un control por botones (WCAG 2.2.1). Android tiene el ajuste «Tiempo para actuar», que la app puede leer;
  - **el color no puede ser lo único que distingue algo**: las etiquetas de color y los caracteres coloreados de «Ver en grande» (WCAG 1.4.1);
  - **el color dinámico de Android 12** no garantiza el contraste (WCAG 1.4.3 y 1.4.11); el alto contraste tiene que mandar sobre él.
- **Veracidad:** ningún texto, ni la ficha ni el README, puede decir que la app es «accesible» o que «cumple WCAG» sin una prueba que lo demuestre. Se puede decir lo que hace: «sigue el tamaño de letra del sistema», «modo de alto contraste».

### 3.10 El icono cambiable (pendiente del dueño)

- **Políticas de Play:**
  - la de **suplantación** prohíbe que una app se haga pasar por otro desarrollador, empresa, entidad o app, o sugiera una relación que no existe; uno de sus ejemplos es copiar el logotipo de un negocio para parecer su app oficial;
  - la de **comportamiento engañoso** pide que el título, el icono y la descripción reflejen lo que hace la app;
  - ninguna de las dos habla expresamente de cambiar el icono con `activity-alias`, y **no he encontrado ninguna cláusula que lo prohíba** (consulta del 12-11-2026, §8). En la práctica, Play y Play Protect sí persiguen las apps que esconden su icono o se disfrazan de una app del sistema (es una táctica conocida del *adware*).
- **Conclusión:** variantes **del propio icono de Clavero** (colores, fondo, forma) son lícitas y no rozan esas políticas. Lo que sí sería un problema:
  - un icono que imite una calculadora, una app del sistema o de un tercero;
  - una etiqueta distinta de «Clavero»;
  - una opción que esconda el icono.

  Eso sería suplantación o disfraz, contrario a las políticas citadas y, si usa signos ajenos, al art. 9.2 RMUE y al art. 34.2 LM. El diseño ya lo descarta («Solo variantes del icono de Clavero»). O-8 lo convierte en condiciones que se puedan comprobar.
- **Acuerdo de distribución de Google Play:** según fuentes secundarias de septiembre de 2025, el desarrollador concede a los usuarios el derecho a cambiar el color de su icono (iconos con tema). Encaja con las variantes propias. **Redacción por confirmar.**
- **Marca propia:** «Clavero» sigue sin buscar ni registrar (`CLAUDE.md`, «Pendientes», 7). No impide las variantes, pero cuantas más formas tenga el icono, más conviene registrar la marca, también figurativa (R-8). El README ya dice que la licencia Apache-2.0 no da permiso para usar el nombre ni el icono (`README.md:9`). Esa frase cubre las variantes si se dice «los iconos».

### 3.11 Textos que no prometen de más (regla de veracidad)

Además de lo de arriba, el diseño tiene frases que, pasadas tal cual a la interfaz, prometerían algo distinto de lo que hace:

- **«Tu última copia comprobada es de hace X»** (diseño, «Copias»). Lo que se apunta es la fecha de **la comprobación**, no la de la copia. Una copia de hace un año comprobada hoy daría «de hace 0 días». Además, una copia sin `vaultTag` (de antes de la 2.0 o de otra bóveda) no cuenta, y el usuario lo tiene que saber para no pensar que el aviso falla (O-5).
- **El calendario de copias** («diaria, semanal o mensual») solo actúa al abrir la app (O-4).
- **El paso a v4:** después, la 1.4.x no abre la bóveda ni sus copias nuevas (O-3).
- **«Deshacer»** dura 5 s, pero la papelera queda. El texto no tiene que decir que se ha perdido algo.
- **«Vibración suave»:** si se hace con `View.performHapticFeedback`, no hace falta ningún permiso. Si se usara la API `Vibrator`, haría falta `android.permission.VIBRATE`, y la sección «Permisos» de la política y la frase «sin permisos nuevos» dejarían de ser ciertas (R-7).

### 3.12 Nombres de otras apps

- El diseño respeta la regla («sin nombres de otras apps fuera de `imports/ImportTexts.kt`»). Los puntos nuevos donde podría colarse alguno son:
  - las plantillas, en los textos de ejemplo de los campos (marcas de tarjetas, bancos, productos de software en «Licencia»);
  - «Salud» (§3.7);
  - el título desde el dominio, si se metiera una tabla que traduzca dominios a nombres comerciales;
  - el icono.

  En todos, ninguno (O-6, O-9, R-11).
- **El título que se rellena con el dominio de la URL** que escribe el usuario no es un uso de marca de la app: es el dato del usuario. Sin tabla de nombres, sin logotipos y sin red, como dice el diseño.
- **«Iconos de webs», descartado:** es la decisión correcta también en lo legal, porque habría enseñado logotipos de terceros dentro de la app y abierto una conexión con terceros.

## 4. Cambios obligatorios al diseño (O-n)

**O-1. Nombres de las bóvedas: aviso a la vista y nada de «ocultar».**
- Al crear y al renombrar una bóveda, debajo del campo del nombre y sin plegar, una línea como: «El nombre se guarda sin cifrar en el móvil: no pongas en él nada que no quieras que se vea».
- Ningún texto (interfaz, ficha, README, notas) presenta varias bóvedas como una forma de ocultar, esconder o negar una bóveda. Tampoco llama «privada», «secreta» u «oculta» a ninguna.
- La política dice qué se guarda sin cifrar: nombre, color, orden, la última abierta y los ajustes de cada bóveda, con las carpetas de sus copias (O-10).
- *Base:* regla de veracidad y de «Texto mínimo» (M-3); arts. 5.1.a) y 12.1 RGPD como pauta de transparencia; política de comportamiento engañoso de Google Play.

**O-2. Borrar, papelera e historial: lo que queda, a la vista.**
- **«Borrar para siempre», «Vaciar papelera», «Borrar historial» y «Borrar bóveda»** dicen en su confirmación, sin plegar, que las copias ya hechas lo siguen teniendo: las exportadas, las automáticas, las de antes de importar y la de antes de la 2.0. Ejemplo: «Se borra de la bóveda. Las copias que ya hiciste lo siguen teniendo».
- **La papelera** dice que lo de más de 30 días se borra **al abrir la bóveda**, y que sus entradas van en las copias.
- **Ningún texto** dice «irrecuperable», «sin rastro», «borrado seguro», «para siempre de todas partes» ni «de verdad».
- **El tope del historial:** antes de escribir los textos, se decide una sola cifra (64 KiB o 256 KiB por entrada; el diseño dice las dos).
- *Base:* regla de veracidad y M-3. El art. 17 RGPD no se aplica, pero el usuario lo leerá como un derecho de supresión. Política de comportamiento engañoso de Google Play.

**O-3. El paso al formato v4: aviso antes y salida sin presión.**
- Antes de guardar como v4, a la vista: «Después, las versiones anteriores de Clavero no abrirán esta bóveda ni las copias nuevas».
- Sin carpeta de copia automática, «Exportar una copia antes» es la opción destacada y la que queda elegida por defecto. «Seguir sin copia» es secundaria y dice lo que supone.
- La línea de la huella («Clavero 2.0 necesita la maestra una vez…») se queda como está. Es correcta.
- Las «Novedades» de Play, las notas de GitHub y el README dicen que una copia hecha con la 2.0 no la abre la 1.4.
- *Base:* regla de veracidad y M-3; la Directiva 2024/2853, art. 6.1.c, como criterio del daño que se quiere evitar (datos destruidos o corruptos); art. 1902 CC como riesgo residual.

**O-4. Copias con calendario, avisos de caducidad y QR de wifi: los límites, a la vista.**
- **Junto al calendario de cada destino**, sin plegar: «La copia se hace al abrir Clavero. Si no la abres, no hay copia».
- **Junto a la opción de caducidad:** los avisos solo salen dentro de la app.
- **En la pantalla del QR de wifi:** «Quien vea el código ve la contraseña».
- *Base:* regla de veracidad y M-3. Fiarse de un calendario que no actúa puede costar los datos.

**O-5. «Última copia comprobada»: decir lo que se mide.**
- El texto describe lo que se guarda. Por ejemplo: «Comprobaste una copia de esta bóveda por última vez hace X». No sirve «Tu última copia comprobada es de hace X», que se lee como la edad de la copia.
- La alternativa es guardar y enseñar también la fecha de la copia, si la copia la tiene.
- El texto explica, al menos bajo «Más información», que una copia de antes de la 2.0 o de otra bóveda no cuenta.
- *Base:* regla de veracidad.

**O-6. «Salud» como misiones: sin red por sí sola y sin nombres de terceros.**
- Ninguna misión lanza la consulta a Pwned Passwords ni otra acción con efectos fuera del móvil. Lleva a la pantalla donde está el botón de siempre. Si se quiere un botón en la misión, se cambian a la vez la política («La única conexión a Internet…») y el aviso de la red.
- «Activa el 2FA en …» solo usa el título que el usuario dio a su entrada. No entra en la app ninguna lista de servicios de terceros.
- *Base:* regla de veracidad (la política nombra los dos únicos botones que conectan); regla «Nombres de otras apps» de `CLAUDE.md`; art. 14 RMUE y art. 37 LM.

**O-7. Guía y modo sencillo: no se saltan los avisos.**
- Saltar la guía no salta «Antes de empezar» ni sus avisos: sin recuperación, lo que sale del móvil y sin auditoría independiente.
- El modo sencillo oculta herramientas, nunca avisos ni ajustes de protección.
- Una misión que lleva a una herramienta oculta la abre igualmente, o no sale.
- *Base:* regla M-3, con el mismo criterio que la O-3 de la 1.4.2.

**O-8. Icono cambiable (solo si el dueño lo aprueba): condiciones.**
1. Todas las variantes llevan el mismo dibujo de Clavero. Cambian el color, el fondo o el contraste, nunca el motivo.
2. La etiqueta del lanzador de cada `activity-alias` es siempre «Clavero».
3. Ninguna variante imita una app del sistema (calculadora, ajustes, notas…) ni de un tercero, ni usa marcas, logotipos o emblemas ajenos.
4. No hay ninguna opción para esconder el icono ni para que la app parezca otra cosa.
5. La variante por defecto es la de la ficha de Play.
6. Antes de cambiarlo, la app dice a la vista que se reiniciará y que el acceso directo puede desaparecer de la pantalla de inicio.
7. La política dice que el icono se puede cambiar y que eso no añade permisos (O-10). El README dice que la licencia no cubre «el nombre ni los iconos».
- *Base:* políticas de suplantación y de comportamiento engañoso de Google Play; art. 9.2 RMUE y art. 34.2 LM; regla de veracidad.
- La seguridad del componente exportado nuevo (cada alias con el filtro del lanzador) es del experto y del Ingeniero Móvil Senior.

**O-9. Plantillas de tarjeta, pasaporte y licencia: sin marcas ni apariencia oficial.**
- Sin nombres ni logotipos de marcas de tarjetas, de bancos ni de productos de software, tampoco en los textos de ejemplo de los campos ni en las capturas de Play.
- El pasaporte, sin escudos, banderas ni un diseño que imite el documento.
- Ningún texto dice que lo guardado vale como documento, lo sustituye o sirve para identificarse. «Validez legal» ya está prohibida.
- *Base:* política de suplantación de Google Play (el ejemplo del emblema nacional); art. 14 RMUE y art. 37 LM; regla de veracidad.

**O-10. Política de privacidad: lo que tiene que decir la de la 2.0.** La política es el texto que describe lo que hace la app con los datos, y la Política de datos de usuario de Google Play pide que sea exacta y esté al día. Tiene que añadir o cambiar:
1. **Varias bóvedas:** cada una con su maestra y su archivo, y el registro sin cifrar (nombre, color, orden, última abierta y ajustes por bóveda) en el almacenamiento privado, fuera de las copias de Android.
2. **Lo nuevo de la bóveda cifrada:** tarjetas bancarias, pasaportes, redes wifi, licencias, campos personalizados, carpetas, vistas, el historial de cada entrada, la papelera, las fechas de caducidad y la fecha de último uso de cada entrada. Todo dentro de la bóveda cifrada, y la papelera y el historial también dentro de las copias.
3. **«Dónde se guardan tus datos»:** la lista de la bóveda («contraseñas, códigos 2FA, frases semilla…») y «Qué datos recogemos» («No recibimos… tus contraseñas…») incluyen los tipos nuevos.
4. **«no hay servidor que reciba tarjetas»:** pasa a «tarjetas de contacto», para no confundirlas con las bancarias.
5. **Las copias:** hasta tres destinos por bóveda, el calendario que solo actúa al abrir la app, y que no hay notificaciones ni nada en segundo plano.
6. **Borrar:** qué hace la papelera (vaciado al abrir la bóveda pasados 30 días), qué borra «Borrar historial» y qué deja «Borrar bóveda»: sus copias ya hechas, las de antes de importar y la de antes de la 2.0.
7. **Los selectores:** «Al borrar la bóveda se olvidan todas» se ajusta a lo que haga el código con varias bóvedas (si borrar una olvida los lugares de todas o solo los suyos).
8. **Reconocer lo pegado:** la app solo lee el portapapeles cuando el usuario pega.
9. **«Tus derechos»:** cómo borrar una bóveda, la papelera y el historial, y que las copias ya hechas las borra el usuario.
10. **«Permisos»:** igual si no se añade ninguno (R-7). Si hay icono cambiable, una línea que diga que no añade permisos.
11. **La fecha**, la del día de la fusión en `main` (como la R-12 de la 1.4.0).
- *Base:* arts. 12 a 14 RGPD como pauta; Política de datos de usuario de Google Play; regla de veracidad.

**O-11. Google Play: ficha, «Novedades» y declaraciones.**
- **La ficha** (`docs/ficha-google-play.md`) se pone al día:
  - «historial de las últimas contraseñas» pasa a ser el historial por entrada;
  - varias bóvedas, plantillas y papelera;
  - ninguna marca de tarjetas, de bancos ni de otras apps;
  - sin «cartera», «monedero» ni «wallet»;
  - sin «accesible» sin una prueba que lo respalde;
  - con los avisos de siempre (sin recuperación, sin auditoría independiente).
- **Las «Novedades»** dicen que la bóveda pasa a un formato nuevo y que sus copias nuevas no las abre la 1.4 (O-3).
- **En Play Console**, antes de enviar, se repasan cuatro declaraciones:
  - «Seguridad de los datos», que sigue en «No recoge ni comparte datos», con la redacción vigente;
  - la declaración de funciones financieras: «Mi app no ofrece funciones financieras»;
  - la declaración de apps de salud: «Mi app no ofrece funciones de salud», aunque una pantalla se llame «Salud»;
  - el público objetivo, sin cambios.
- *Base:* políticas de metadatos, suplantación y datos de usuario de Google Play; las declaraciones obligatorias de Play Console (centro de ayuda de Play Console, §8).

## 5. Cambios recomendados (R-n)

**R-1. Accesibilidad: WCAG 2.2 AA como guía voluntaria.**
- **Tiempos:** «Deshacer», «Copiar contraseña» y el cambio del 2FA se alargan según el ajuste «Tiempo para actuar» de Android (`AccessibilityManager.getRecommendedTimeoutMillis`; WCAG 2.2.1).
- **Color:** las etiquetas de color llevan siempre su nombre, y los caracteres de «Ver en grande» se distinguen por algo más que el color (WCAG 1.4.1).
- **Contraste:** se comprueba el contraste con el color dinámico, y el alto contraste manda sobre él (WCAG 1.4.3 y 1.4.11).
- **Letra:** con la letra al 200 % no se corta nada (WCAG 1.4.4 y 1.4.10).
- **TalkBack:**
  - el botón «⋮» tiene una etiqueta con el nombre de la entrada;
  - la cuenta atrás del 2FA no se anuncia cada segundo;
  - el deslizamiento y el arrastre tienen su alternativa en el menú (WCAG 2.5.1 y 2.5.7), como ya dice el diseño.
- **Vibración:** respeta el ajuste del sistema.

**R-2. Misiones sin presión, con estas reglas** (Directrices 03/2022 del CEPD como criterio):
- «Ahora no» tiene el mismo peso visual que la acción, y su texto es neutro, sin culpa («No, prefiero arriesgarme»);
- una misión descartada no vuelve en la misma sesión ni durante un tiempo razonable;
- sin colores de alarma ni palabras de urgencia que exageren el riesgo;
- se pueden apagar todas en Ajustes.

**R-3. Tarjeta y pasaporte: lo justo.**
- El CVV y el PIN son campos opcionales, sin marca de «recomendado» y vacíos por defecto (PCI DSS como criterio, §3.2).
- La plantilla no pide más campos que los del diseño.
- Una línea plegada puede recordar que guardar los datos de otra persona es decisión de quien los guarda, sin entrar en derecho.

**R-4. Nombres.**
- «Tarjeta bancaria», para no confundirla con la tarjeta de un contacto.
- «Wifi» en minúscula (RAE), sin «Wi-Fi».
- WPA, WPA2, WPA3 y WEP como texto, sin ® ni logotipos (uso descriptivo, art. 14.1.b RMUE).
- La seguridad de la red no se califica como «segura».

**R-5. Notas con formato.**
- Sin copiar al repositorio ejemplos ni tests de especificaciones publicadas con licencias de «compartir igual» (CommonMark, CC BY-SA 4.0, **por confirmar**). Los tests se escriben desde cero.
- Sin llamarlo «Markdown» en la interfaz.

**R-6. El paso a v4, con un recordatorio.** Si el usuario eligió «Seguir sin copia», una línea en Ajustes ofrece hacer una copia ahora, hasta que haga una. Reduce el único momento de riesgo real de pérdida de datos de la 2.0 (§2, arts. 1902 CC y 6.1.c de la Directiva 2024/2853).

**R-7. Vibración sin permiso.** Usar `View.performHapticFeedback` (o su equivalente en Compose), que no necesita `android.permission.VIBRATE`. Así siguen siendo ciertas la sección «Permisos» de la política y la frase «sin permisos nuevos» del diseño. Si se añade el permiso, se cambia la política (O-10).

**R-8. Iconos y marca.**
- Los dibujos de las variantes son propios o tienen una licencia de la lista de `CLAUDE.md` (por ejemplo, Apache-2.0).
- El README dice «el nombre ni los iconos».
- Buscar y registrar la marca «Clavero», también figurativa, en la clase 9, antes o con la 2.0 (`CLAUDE.md`, «Pendientes», 7).

**R-9. «Importar como bóveda nueva» y «Borrar bóveda».**
- La primera dice que crea otra bóveda con el mismo contenido y la misma maestra que la copia.
- La segunda enumera lo que no borra: las copias, las de antes de importar y los archivos exportados de otras apps.

**R-10. Errores que dicen qué hacer.**
- Ningún error propone como solución desinstalar, borrar los datos de la app, «Seguir sin copia» o borrar una bóveda sin decir que se pierden datos.
- Ninguno culpa al usuario.

**R-11. Título desde el dominio y reconocer lo pegado.**
- Sin tabla de dominios a nombres comerciales: el título es el dominio tal como lo escribió el usuario.
- Reconocer lo pegado no enseña ni guarda nada que el usuario no haya pegado (lo dice el diseño: se pone como test).

**R-12. CRA, por si cambian los hechos.**
- Se mantiene el aviso privado de vulnerabilidades de `SECURITY.md`, que ya se parece a lo que pide el anexo I, parte II, del CRA.
- Se deja escrito en `docs/PUBLICAR.md` que cualquier monetización, donaciones incluidas, obliga a revisar el CRA como gestor de contraseñas (producto importante de la clase I, anexo III, **numeración por confirmar**) antes del 11-12-2027.

## 6. Qué textos y documentos habrá que revisar al final

En la revisión final, antes de publicar, con todo terminado:

1. **`docs/privacidad.html`:** todo lo de O-10, y la fecha.
2. **`docs/ficha-google-play.md`** y las **«Novedades»** de Play: O-11 y O-3.
3. **`README.md`:**
   - lo nuevo;
   - la línea de marcas, si se nombra alguna app;
   - «el nombre ni los iconos» (R-8);
   - las pruebas en el móvil de la 2.0;
   - que una copia de la 2.0 no la abre la 1.4.
4. **Las notas de la versión de GitHub** (regla R31 de la 1.4.0).
5. **Los textos de la interfaz** nuevos, con su línea corta y su texto plegado (M-3), comparados uno a uno. Sobre todo:
   - las confirmaciones de borrar (entrada, varias, carpeta, papelera, historial, bóveda);
   - el nombre de la bóveda (O-1);
   - el paso a v4 (O-3);
   - el calendario de copias y la última copia comprobada (O-4, O-5);
   - el QR de wifi;
   - las misiones (O-6, R-2);
   - la guía y el modo sencillo (O-7);
   - el icono (O-8);
   - las plantillas y sus textos de ejemplo (O-9);
   - los errores con qué hacer (R-10);
   - los textos de accesibilidad de TalkBack.
6. **El anexo de textos** (`docs/superpowers/specs/2026-10-27-seguridad-1.3.0-textos.md`), al día con lo anterior.
7. **`docs/PUBLICAR.md`:** las declaraciones de Play Console (O-11) y la nota del CRA (R-12).
8. **«Licencias» dentro de la app:** solo si entra algún recurso nuevo (dibujos del icono). El diseño no añade dependencias.
9. **Los documentos de auditoría y del ataque** de la 2.0: que no digan que la app es segura ni que la auditoría es independiente.

## 7. Riesgos que no puedo valorar sin más hechos

1. **Cómo trata Play los iconos con `activity-alias`:** no hay cláusula expresa (§3.10). Si el revisor de Play lo marcara, la respuesta es O-8, pero no puedo asegurar cómo lo verá. Si se quiere certeza, se puede preguntar al soporte de políticas de Play antes de publicar.
2. **Las variantes del icono:** no las he visto. Las condiciones de O-8 se comprueban sobre los dibujos reales, en la revisión final.
3. **La redacción vigente de las políticas y declaraciones de Play** («Seguridad de los datos», funciones financieras, salud, acuerdo de distribución sobre iconos con tema): consultada en fuentes secundarias y en el centro de ayuda. **Por confirmar** en Play Console el día de enviar.
4. **La trasposición española de la Directiva 2024/2853** (plazo, 9-12-2026), el estado del «Digital Omnibus» y si hay propuesta de una «Digital Fairness Act»: **pendientes de verificar a fecha de hoy** en el BOE y en EUR-Lex. Con los hechos actuales no cambian la conclusión.
5. **Si cambian los hechos** (donaciones, publicidad, una versión de pago, un servidor de sincronización, datos que lleguen al desarrollador):
   - el CRA (gestor de contraseñas, clase I) y la Directiva 2024/2853 pasarían a aplicarse;
   - con un servidor, también el RGPD con todas sus obligaciones;
   - para las tarjetas, posiblemente PCI DSS.
6. **Las citas de memoria:**
   - la numeración del anexo III del CRA;
   - el requisito de PCI DSS;
   - la licencia de la especificación de CommonMark;
   - si «Markdown» es marca registrada;
   - la versión de EN 301 549;
   - *Lindqvist*, *Ryneš* y *SAS Institute*.

   El criterio es el consolidado, pero las referencias exactas están **por confirmar** antes de citarlas fuera de este dictamen.
7. **Lo que el usuario guarde de otras personas** (pasaportes, tarjetas o wifi ajenos): queda en su ámbito doméstico. Si alguien usara Clavero para una actividad profesional (los datos de los clientes de una gestoría, por ejemplo), sería él el responsable del tratamiento. No afecta al desarrollador, pero los textos no deberían presentar la app como herramienta para guardar datos de clientes.
8. **Fuera de la UE:**
   - EE. UU.: el uso referencial de marcas sigue el *nominative fair use*;
   - Reino Unido: art. 11(2) de la Trade Marks Act 1994, numeración por confirmar.

   No encuentro ninguna norma que afecte a lo nuevo. Si se quiere certeza en un país concreto, que la confirme un abogado local.

## 8. Fuentes consultadas (12-11-2026)

- Google Play, política de suplantación — https://support.google.com/googleplay/android-developer/answer/9888374
- Google Play, comportamiento engañoso (búsqueda; no hay cláusula expresa sobre cambiar o esconder el icono) — https://support.google.com/googleplay/android-developer/answer/9888077 y la política de software no deseado — https://developers.google.com/android/play-protect/mobile-unwanted-software
- Google Play, declaración de funciones financieras — https://support.google.com/googleplay/android-developer/answer/13849271
- Google Play, declaración de apps de salud — https://support.google.com/googleplay/android-developer/answer/14738291
- Acuerdo de distribución y los iconos con tema (fuente secundaria) — https://www.androidauthority.com/google-play-icon-theming-agreement-3597899
- Directiva (UE) 2024/2853, plazo y trasposición en España (fuentes secundarias; pendiente de verificar en el BOE) — https://www.garrigues.com/sites/default/files/noticias/files/informe-garrigues-espana-novedades-legales-2026.pdf, https://www.fieldfisher.com/es-es/locations/espana/actualidad/la-nueva-regulacion-de-los-productos-defectuosos-l y https://cuatrecasas.com/en/spain/litigation/art/new-directive-liability-defective-products
- «Digital Omnibus», estado (fuentes secundarias) — https://www.addleshawgoddard.com/en/insights/insights-briefings/2026/data-protection/eu-digital-omnibus-european-council-adopts-its-position/, https://edri.org/our-work/the-digital-omnibus-is-going-on-summer-break-your-rights-are-not/ y https://www.iubenda.com/en/blog/ai-omnibus-parliament-adoption-june-2026
- Dictámenes anteriores: `docs/superpowers/handoff/sdd-1.4.0/abogado-1.4.0.md` (§4, mapa normativo, y sus fuentes del CRA, el TRLGDCU y la Directiva 2019/770) y `docs/superpowers/handoff/sdd-1.4.2/abogado-1.4.2.md`.

No he consultado EUR-Lex ni el BOE directamente en esta sesión. Las fechas del CRA (art. 71.2) y de la EAA (art. 31) son las del texto publicado en el DOUE, tal como las recogen los dictámenes anteriores.

---

## Revisión experto-seguridad-cripto

Revisión del dictamen, no del código, que aún no existe.

- **O-2 tiene un fundamento técnico, no solo de texto.** La clave de datos no cambia al pasar a v4 (diseño, «Paso de v3 a v4») ni al borrar. Así que un archivo `vault.bin` viejo que quede en la flash o en una copia se abre con la maestra de ese momento, papelera e historial incluidos. Prometer un borrado irrecuperable sería falso con este diseño. Rotar la clave de datos con «Borrar para siempre» no lo arreglaría tampoco, por las copias.
- **O-1** describe un riesgo que el modelo de amenaza del diseño ya acepta (tabla, fila «Registro de bóvedas»). El aviso no da a un atacante nada que no vea ya en `files/vaults.json`.
- **O-6.1 protege la frase de la política sobre la única conexión** (`docs/privacidad.html`, «La única conexión a Internet»). Sin ella, una misión podría mandar los prefijos SHA-1 de todas las contraseñas sin que el usuario pulse el botón que conoce.
- **O-8 no da visto bueno a la superficie técnica:** cada `activity-alias` es un componente exportado con `MAIN` y `LAUNCHER`, y al cambiarlo se reinicia la app. Eso se revisa en la fase de diseño técnico: que no acepte *extras*, que no cambie el estado de la bóveda y que su activación no la pueda pedir otra app.
- **R-3 (CVV opcional) y R-7 (vibración sin permiso)** no cambian ningún formato ni parámetro criptográfico.
- **Ningún texto propuesto usa palabras prohibidas** (`ForbiddenWords`: «segur[oa]», «garantiza» sin «no», «imposible», «100 %», «anónim», «de verdad», «validez legal», «limpi[oa]», «sospechos»). Los ejemplos de O-1 a O-5 están escritos para pasarlo.

## Revisión Ingeniero Móvil Senior

- **R-1:** `AccessibilityManager.getRecommendedTimeoutMillis(…)` existe desde Android 10 (API 29). En Android 8 y 9 se usan los tiempos del diseño. En Compose, el `SnackbarDuration` se sustituye por un tiempo propio calculado con esa llamada.
- **R-7:** en Compose, `LocalHapticFeedback.current.performHapticFeedback(...)` no necesita `VIBRATE`. La API `Vibrator` sí lo necesita (permiso «normal», pero cambia el manifiesto y `aapt2 dump permissions`).
- **O-8:**
  - cada `activity-alias` necesita `android:label="@string/app_name"` (punto 2);
  - el alias activo se cambia con `PackageManager.setComponentEnabledSetting`, que puede quitar los accesos directos y reiniciar el proceso (punto 6);
  - la variante por defecto debe ser el alias habilitado en el manifiesto, para que una instalación nueva enseñe el icono de la ficha (punto 5).
- **O-4:** el calendario «al abrir la app» se engancha a `onStart` del proceso o de la bóveda abierta, sin `WorkManager`. El texto de O-4 describe exactamente eso.
- **O-10.7:** hoy `PickerPlaces` tiene un solo almacén (`android/PrefsPlaceStore.kt`, preferencias `picker_places`). Con varias bóvedas, el implementador decide si los lugares de las copias son por bóveda, y la política dirá lo que haga.
- **Sin cambios de permisos, componentes ni ciclo de vida** por este dictamen: todo lo que pide son textos, el orden de unos botones (O-3) y dos límites de las misiones (O-6).

## Textos para el Abogado Digital (revisión final)

Los de §6. Los ejemplos de texto de este dictamen son orientativos: el implementador los escribe con la regla M-3, y se revisan todos al final.

*Borrador profesional para revisión del dueño. No sustituye el asesoramiento de un abogado colegiado en cada jurisdicción de publicación.*
