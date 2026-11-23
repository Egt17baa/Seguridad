# Llavion 2.1: visto bueno inicial del Abogado Digital sobre el nombre y el logo

**Fecha de referencia:** 22-11-2026. **Fase:** visto bueno del principio (punto 4 de «Skills: siempre juntas» de `CLAUDE.md`), antes de programar. **Encargo:** el cambio de nombre de «Clavero» a «Llavion» (o «Llavion 256»), el logo nuevo propuesto y el aviso a quienes ya tienen la app. **Base de hechos** (la de los dictámenes anteriores, que doy por cierta): desarrollador particular residente en España, con seudónimo (Egt17baa), sin ánimo de lucro; app gratuita, sin anuncios, compras ni donaciones; no recibe datos; código abierto (Apache-2.0); publicada en todo el mundo salvo Francia (con ultramar), Rusia y Bielorrusia. Lo que cambia en la 2.1 es solo la identidad visible: el `applicationId` `io.github.egt17baa.seguridad`, la clave de subida, el paquete `app.seguridad`, los formatos y la criptografía no cambian.

Skills usadas: `despacho-digital` (método) y `propiedad-intelectual-ia` (marcas, derechos de autor e IA generativa); `privacidad-proteccion-datos` solo para la política.

---

## 1. Veredicto

| Asunto | Veredicto |
|---|---|
| **Nombre «Llavion»** (opción A) | **Vale, con cambios.** No he encontrado ninguna app, marca ni empresa «Llavion» en las fuentes que he podido consultar, y los dominios `.com`, `.app`, `.org` y `.net` parecen libres. Es un nombre inventado y distintivo. Condición: la búsqueda de anterioridades del dueño en las bases oficiales (O-1), que yo no he podido hacer. |
| **Nombre «Llavion 256»** (opción B) | **No la recomiendo** (vale jurídicamente con matices, pero es peor que A). El «256» no añade protección a la marca, es descriptivo y, como nombre, funciona como una afirmación de seguridad sin contexto que la propia app no sostiene entera (§3). |
| **El logo propuesto** | **No vale tal como está.** Contradice la decisión del dueño del 12-11-2026 (cerradura dos veces y una punta de escudo), lleva un «256» que no recomiendo, tiene texto ilegible en tamaño de icono, esquinas redondeadas y halo que Google Play pide no incluir, y su origen (probablemente una IA generativa) deja la imagen sin derecho de autor y sin garantía de que no reproduzca un logo ajeno. **Vale con cambios** si se redibuja (O-3 a O-6). |
| **Aviso a los usuarios** | Hay que avisar una vez en la app, en las notas, en la ficha y en la política, como en la 1.3.0 (O-7 a O-10). El mismo `applicationId` con otro nombre no plantea ningún problema legal. |

**Mi preferencia: opción A**, «Llavion» a secas en el móvil, en la ficha y en la marca, y el «256» fuera del icono; si el dueño lo quiere ver en algún sitio, solo dentro del texto de la ficha y con su contexto («cifra con AES-256-GCM»), nunca en el nombre ni en el icono.

---

## 2. El nombre «Llavion»

### 2.1 Qué he buscado y qué he encontrado (22-11-2026)

| Fuente | Resultado |
|---|---|
| Búsqueda web general de «Llavion», «Llavion 256», «Llavion256», `llavion.com/.app/.es` | Nada con ese nombre. Lo más cercano: «Llamion», una familia de modelos de lenguaje descrita en un artículo de arXiv de mayo de 2026 (otra palabra, otro sector); «Lavion», una solicitud de marca en EE. UU. (serie 99549475, clase 43, hostelería, solicitada en diciembre de 2025); «Lavrion», una ciudad griega. |
| Google Play, búsqueda «llavion» | **Ninguna app con ese nombre.** Play corrige la búsqueda a «avion» y enseña juegos de aviones (entre ellos una app «Avione», de Avione LLC). Ver §2.3. |
| App Store y web, nombres parecidos del sector | «Llavero.io» (Webempresa Europa SL, Madrid, categoría Productividad, con acceso en dos pasos); «TuLlave Smart Lock» (una cerradura). «Llavero» es además el nombre en español del llavero de contraseñas de un sistema operativo conocido. No he encontrado gestores de contraseñas que se llamen «Llavion», «Lavion» o «Avion». |
| Dominios (RDAP del registro, 22-11-2026) | `llavion.app` y `llavion.org`: «objeto no encontrado»; `llavion.com` y `llavion.net`: 404 del registro. Es decir, **parecen libres**. `.es` y `.io` no tienen RDAP público: sin comprobar. |
| TMview, EUIPO eSearch, OEPM, WIPO Global Brand Database | **No he podido consultarlas** desde esta sesión (TMview respondió 503 y las demás necesitan una interfaz que no puedo usar). |

### 2.2 Riesgo de confusión

- **Con lo que he visto, el riesgo es bajo.** «Llavion» es una palabra inventada (llave + la terminación de «avión»). No hay ninguna marca ni app igual en lo que he podido ver.
- **Los parecidos a vigilar** en la búsqueda del dueño (O-1), en las clases 9 y 42:
  - **«LAVION»** y **«AVION»**: comparten 6 y 5 de las 7 letras. Con el yeísmo, «Llavion» suena «yavión», muy cerca de «avión». Una marca «AVION» de software en la clase 9 o 42 sería la anterioridad más peligrosa. «Avión» es una palabra corriente y una marca así sería débil, pero hay que mirarlo.
  - **«LLAVERO»**, «LLAVE…» y «LLAVÍN»: el prefijo «llav-» es descriptivo para un producto de seguridad (una llave), así que nadie puede monopolizarlo y el parecido en ese prefijo pesa poco; lo que distingue es la terminación «-ion». Llavero.io es el caso más cercano por sector (España, accesos); los nombres terminan distinto y no veo confusión probable, pero que conste en la búsqueda.
  - **«CLAVERO»**: es justo el nombre que se deja. No hay riesgo propio, pero el texto «antes «Clavero»» seguirá en la ficha un tiempo (O-9): es un uso descriptivo del nombre anterior de la propia app, admitido (art. 37 de la Ley 17/2001, de Marcas, y art. 14 del Reglamento (UE) 2017/1001, uso con fines de identificación o referencia, en la medida en que se haga conforme a las prácticas leales).
- **Por qué cambiar ahora está bien hecho:** el dueño ha visto «Clavero» en servicios de IA (clavero.ia, clavero.app). Esos servicios suelen estar en las clases 9 y 42, las mismas que una app de seguridad. Seguir con «Clavero» habría sido el riesgo mayor. La fila de la búsqueda de marcas de `CLAUDE.md` (pendientes del dueño, punto 7: CLAVERO, CLAVIS, CLAVISTER) y la O-5 de la 2.0 pasan a ser sobre «LLAVION».

### 2.3 Lingüística

- **La tilde.** En español, la palabra se escribiría «Llavión» (aguda terminada en «n»). Sin tilde, la norma la leería «LLÁ-vion», aunque cualquiera dirá «llavión» por analogía con «avión». **Una marca es un nombre propio inventado y puede escribirse sin tilde**; no es un problema legal. Tiene ventajas prácticas: es igual en todas las lenguas, cabe en un dominio y en un nombre de archivo, y los buscadores suelen ignorar la tilde. Lo único obligatorio es **elegir una forma y usarla igual en todas partes** (app, ficha, política, README, marca, dominio): O-2. Recomiendo «Llavion», sin tilde, también en mayúsculas («LLAVION»).
- **«Avión».** Sí, se lee como «llave» + «avión». No es malo: es memorable. Pero tiene un efecto real: **Google Play corrige hoy la búsqueda «llavion» a «avion»** y enseña juegos de aviones. Cuando la app se llame así, Play la indexará con su título y debería dejar de corregir, pero el primer tiempo costará encontrarla. Por eso conviene que el título de la ficha sea «Llavion: bóveda cifrada» (R-1) y que la ficha diga «antes «Clavero»» (O-9).
- **Otras lenguas de los países donde se publica:**
  - **Catalán:** «llavió» (con tilde) es un pez, la tenca de mar (*Symphodus tinca*), según el diccionario normativo del IEC. Es inofensivo y casi desconocido. No he encontrado ningún sentido malo.
  - **Gallego y portugués:** no existe; la «ll» no es de su ortografía (allí se lee «l» doble o como el español). Sin sentido malo conocido.
  - **Asturiano, euskera, italiano, inglés, alemán y otras lenguas de la UE:** no he encontrado ningún sentido ni malsonante. En inglés el «Ll» inicial es raro y algunos dirán «la-vion»; no es un problema.
  - **Variantes de América Latina:** no he encontrado ningún uso coloquial de «llavión». Es una comprobación limitada (búsqueda web y diccionarios en línea): recomiendo que el dueño pregunte a algún hablante de los países con más usuarios si los conoce (R-2).

### 2.4 ¿Conviene registrarla y dónde?

- **No es obligatorio.** Para usar el nombre no hace falta registrarlo; lo que importa es no chocar con una marca anterior (O-1).
- **Registrarla da un derecho exclusivo** (art. 34 Ley de Marcas; art. 9 RMUE) y evita que un tercero la registre después y le pida al dueño que cambie de nombre otra vez. Para una app gratuita sin ánimo de lucro es **opcional pero recomendable** si el dueño piensa mantenerla años (R-3).
- **Dónde:** marca de la Unión Europea (EUIPO), que cubre todos los Estados miembros, Francia incluida (que una marca la cubra no obliga a publicar la app allí), o, más barata, solo marca española (OEPM). **Clases:** 9 (software descargable, apps) y 42 (diseño y desarrollo de software; software como servicio, si algún día lo hubiera). Las tasas cambian: consultar las vigentes en la web de cada oficina antes de presentar (pendiente de verificar a fecha de hoy).
- **Qué registrar:** primero la **denominativa** «LLAVION» (protege la palabra con cualquier dibujo). La figurativa (el logo) solo cuando el logo sea definitivo y redibujado (§3.4).
- **Importante para la privacidad del dueño:** **una solicitud de marca no se puede presentar con seudónimo.** La OEPM y la EUIPO publican el nombre y la dirección del titular (o de su representante) en sus bases públicas. Si el dueño no quiere que su nombre se asocie a la app, las salidas son no registrar, registrar a través de una persona jurídica o pedir a un agente de la propiedad industrial que actúe como representante (el nombre del titular se publica igual). Es una decisión suya (R-3).
- **Dominio:** registrar `llavion.app` (y, si quiere, `.com` y `.es`) con la privacidad del WHOIS activada, para que nadie lo ocupe con una página que se haga pasar por la app (R-4). No es obligatorio: la app no usa ningún dominio propio.

---

## 3. Opción A («Llavion») frente a opción B («Llavion 256»)

### 3.1 Lo que usa la app de verdad (comprobado en el código)

| Parte | Lo que usa | ¿«256»? |
|---|---|---|
| Bóveda y adjuntos | AES-256-GCM (`vault/Vault.kt:262`, `vault/Attachments.kt:133`) con una clave derivada con Argon2id de la contraseña maestra (`crypto/Crypto.kt:57`) | Sí: clave de 256 bits |
| Huella | Clave AES de 256 bits del Keystore de Android (`android/BiometricVault.kt:138`) | Sí |
| «Cifrar texto» y «Cifrar archivo» | AES-256-GCM por defecto; también ChaCha20-Poly1305, Twofish-256, Serpent-256, Camellia-256 y la cascada (`crypto/Crypto.kt:43-48`) | Sí: todas con claves de 256 bits |
| Contactos | X-Wing (ML-KEM-768 + X25519) para cifrar y Ed25519 para firmar (`crypto/pk/Card.kt`) | **No**: Ed25519 da unos 128 bits de seguridad y ML-KEM-768 está en el nivel 3 del NIST (comparable a AES-192) |
| «¿Filtrada?» | SHA-1 de la contraseña, del que solo salen 5 caracteres (`crypto/Pwned.kt:4`) | No (y es correcto que así sea: lo exige el servicio) |
| La contraseña maestra | La regla de la maestra nueva exige una estimación de **50 bits** o más (`health/Health.kt:16`) | **No**: la seguridad real de la bóveda frente a quien roba el archivo depende de la maestra y de Argon2id, no del tamaño de la clave AES |

**Conclusión técnica:** «256» es **literalmente cierto como longitud de la clave** de los cifrados simétricos. **No es cierto como «nivel de seguridad de la app»**, que es como lo lee una persona normal en un nombre o en un icono: la bóveda es tan fuerte como la contraseña maestra (desde unos 50 bits estimados), las firmas dan unos 128 bits y frente a un ordenador cuántico AES-256 da unos 128. El número, solo, es la abreviatura publicitaria habitual de «cifrado de grado militar», que la ficha prohíbe expresamente (`docs/ficha-google-play.md:3`).

### 3.2 ¿Es una afirmación veraz y no engañosa?

- **Normas:** Directiva 2005/29/CE de prácticas comerciales desleales (arts. 6 y 7, acciones y omisiones engañosas) y Ley 3/1991, de Competencia Desleal (arts. 5 y 7). Se aplican a un «comerciante» que actúa con fines profesionales. Con los hechos de arriba (particular, gratis, sin ánimo de lucro) **probablemente no se aplican**, pero la regla de veracidad de este proyecto (`CLAUDE.md`, punto 4 de «Skills»: un texto no puede afirmar algo falso sobre el código) sí, y la política de Google Play sobre metadatos engañosos también (más abajo).
- **En el texto de la ficha, con contexto** («cifra con AES-256-GCM y deriva la clave de tu contraseña con Argon2id»): **veraz y admisible**. Ya está así (`docs/ficha-google-play.md:38` y `:91`).
- **En el nombre o en el icono, sin contexto:** no es falso, pero **induce a pensar en un nivel de seguridad de 256 bits** que la app no da en todas sus partes ni frente a una maestra débil. Es una omisión que puede engañar (art. 7 de la Directiva) y choca con el espíritu de la regla de la ficha. Además, un nombre no se puede matizar: «Llavion 256» repite la afirmación en cada sitio donde aparece (lanzador, notificaciones de Play, capturas, búsquedas) sin la explicación.
- **Y ata la marca a un parámetro técnico.** Si un día la app añade un cifrado con otro tamaño de clave, o cambia el de la bóveda (por ejemplo, por una transición poscuántica), el nombre seguiría diciendo «256».

### 3.3 Google Play

- **El título «Llavion 256» se admite.** La política de metadatos (Play Console, «Metadata», `support.google.com/googleplay/android-developer/answer/9898842`) prohíbe en el título, el icono y el nombre del desarrollador las indicaciones de rendimiento o clasificación en la tienda («n.º 1», «App del año»), el precio y las promociones, los programas de Google Play, los emojis y los caracteres especiales repetidos, y **los símbolos o afirmaciones engañosos**. Un número que forma parte del nombre no está prohibido. Cabe de sobra en los 30 caracteres.
- **El riesgo** está en lo de «engañoso»: un revisor podría ver «256» como una afirmación de función en el título. Es poco probable, pero no es cero, y en el icono el número no aporta nada que no diga ya la descripción.

### 3.4 ¿Un número debilita la marca o la hace más difícil de registrar?

- Los números pueden ser marca, pero **un número que describe una característica del producto es descriptivo** y no se puede registrar solo (art. 7.1.b) y c) RMUE; art. 5.1.b) y c) Ley de Marcas). Es el criterio del TJUE en el asunto C-51/10 P, *Agencja Wydawnicza Technopol* («1000» para publicaciones), y de las Directrices de la EUIPO sobre cifras. Para software de cifrado, «256» es justo la longitud de clave: descriptivo.
- **«Llavion 256» se registraría igual que «Llavion»**, porque lo distintivo es «Llavion». Pero el «256» **no añade ninguna protección**: un tercero podría usar «256» en su nombre sin infringir nada, y en una comparación de marcas el «256» se trata como un elemento débil que casi no cuenta. Es decir: no lo hace más difícil, pero tampoco más fuerte, y alarga el nombre.
- **La búsqueda de anterioridades de «Llavion 256»** da lo mismo que la de «Llavion» (nada en las fuentes consultadas, §2.1): no hay ningún resultado con «Llavion 256» ni «Llavion256».

### 3.5 Recomendación

**Opción A.** «Llavion» a secas en el lanzador, en la ficha, en la marca y en el icono. El «256» fuera del icono y del nombre. Si el dueño quiere que se vea, en el texto de la ficha (ya está, con su contexto) o, como mucho, en un gráfico de la ficha (el «gráfico de funciones») con el texto completo («AES-256-GCM»), nunca solo.

Si el dueño elige igualmente la **opción B**, es legal con estas condiciones (obligatorias en ese caso): la primera línea de la descripción corta y de la larga explica el número («256: la longitud en bits de la clave AES con la que se cifra la bóveda; la seguridad real depende de tu contraseña maestra»); la ficha mantiene el aviso de «sin auditoría independiente»; ninguna parte dice ni insinúa que la app da «256 bits de seguridad»; y los contactos no se presentan como «256».

---

## 4. El logo

### 4.1 Lo que se ve

Un hexágono de cinta plegada en dos colores (oro a la izquierda, formando casi una «L», y azul a la derecha), con **una cerradura** brillante en el centro; un círculo azul alrededor; debajo, «LLAVION» con la «O» como **otra cerradura** dorada; «256» en azul entre dos líneas; una línea azul que acaba **en punta hacia abajo, como la parte baja de un escudo**; todo dentro de un cuadrado de **esquinas redondeadas con borde brillante** sobre fondo casi negro. Parece hecho con una herramienta de IA generativa (el estilo de brillos, el acabado metálico y el texto lo sugieren), aunque no se sabe seguro.

### 4.2 Choca con una decisión del dueño

El 12-11-2026 el dueño decidió un logo «que no se parece a ninguna marca ni app (**sin escudo, candado ni cerradura**)» (`CLAUDE.md`, «Decisiones de la 2.0», y `sdd-2.0/abogado-2.0-cristal.md`, §3.1). Este logo lleva **cerradura dos veces** y una **punta de escudo**. No es ilegal, pero:

- Esa decisión tenía una razón jurídica, no solo estética: **la cerradura, el candado y el escudo son los motivos más repetidos de las apps de seguridad, de los bancos y de las VPN**. Son descriptivos para esos productos (una cerradura «dice» seguridad) y por eso dan poca distintividad a una marca figurativa (art. 7.1.b) y c) RMUE) y aumentan el riesgo de que el icono se parezca a otro en la tienda.
- Si el dueño quiere cerraduras, **tiene que revocar expresamente su decisión del 12-11-2026** y dejarlo apuntado en el registro (O-3). Mi recomendación es no hacerlo.

### 4.3 Parecido con marcas conocidas

- Sin una búsqueda por imagen (que no he podido hacer) no puedo afirmar ni negar un parecido concreto. Lo que sí veo:
  - El **hexágono de cinta plegada en dos colores** es una forma muy común en logos de plantilla y en marcas de tecnología y de criptomonedas. No es una forma distintiva por sí sola.
  - La **cerradura en el centro** sobre fondo oscuro es casi el icono genérico de «gestor de contraseñas» o «caja fuerte» en las tiendas.
  - El **oro y el azul** son los colores de muchas marcas financieras.
- **Riesgo propio de la IA generativa:** estas herramientas pueden reproducir, casi igual, logos o plantillas que vieron al entrenarse, sin decirlo. Por eso **la búsqueda por imagen es obligatoria** antes de usar este logo o uno derivado (O-5).

### 4.4 Derechos sobre una imagen hecha con IA

- **España y UE:** solo es obra protegida la creación intelectual propia de una **persona física** (art. 5.1 TRLPI; TJUE C-5/08 *Infopaq* y C-145/10 *Painer*). Una imagen generada a partir de unas instrucciones escritas, sin una aportación creativa humana suficiente sobre su forma, **probablemente no tiene derecho de autor**. Hay un precedente checo en ese sentido (Tribunal Municipal de Praga, 2023, asunto 10 C 13/2023; referencia por confirmar).
- **EE. UU.:** la Oficina de Derechos de Autor (informe «Copyright and Artificial Intelligence, Part 2: Copyrightability», enero de 2025) no registra lo generado solo con instrucciones; sí la selección, la disposición o la modificación humana suficientes. El tribunal de apelación del Distrito de Columbia confirmó en *Thaler v. Perlmutter* (marzo de 2025) que la autoría exige una persona.
- **Consecuencias para el dueño:**
  1. No podrá impedir por derecho de autor que otro copie la imagen tal cual (sí por marca, si la registra, o por competencia desleal en algunos casos).
  2. La licencia Apache-2.0 del repositorio no cambia nada: el README ya dice que la licencia no da derecho a usar el nombre ni los iconos (`README.md:9`), y eso sigue valiendo por la marca y por el art. 6 de la Apache-2.0 (marcas).
  3. **Si la redibuja una persona** (el dueño o un diseñador) en vector, con cambios creativos propios, **esa versión sí tiene autor** y derecho de autor sobre lo que aporte. Si la hace un tercero, hace falta una **cesión por escrito** de los derechos de explotación (arts. 43 y 45 TRLPI), con permiso para publicarla con la Apache-2.0 en el repositorio.
- **Términos de la herramienta:** hay que leer los de la herramienta concreta y el plan con el que se generó: (a) si da al usuario la titularidad o una licencia sobre lo generado y si permite el uso comercial y como marca; (b) si los planes gratuitos tienen otra licencia (algunas herramientas dan a los usuarios gratuitos una licencia no comercial o se reservan derechos); (c) si la herramienta da alguna garantía o indemnidad frente a reclamaciones (casi nunca para usuarios particulares); (d) que **no hay exclusividad**: otro usuario puede obtener una imagen parecida. Apuntar en el registro la herramienta, el plan, la fecha y que se revisaron sus términos (O-4), sin datos personales.
- **¿Se puede registrar como marca figurativa?** Sí. Una marca no necesita derecho de autor: basta que sea distintiva y que no choque con derechos anteriores. Pero (a) una figurativa cuyos elementos son descriptivos (cerradura, escudo, «256») protege poco más allá de la palabra; (b) si la imagen reprodujera una obra o un logo ajenos, el titular podría oponerse o pedir la nulidad (art. 9.1.c) Ley de Marcas; art. 60.2.c) RMUE). Por eso, primero la denominativa, y la figurativa solo con el logo redibujado y buscado.
- **Reglamento de IA (Reglamento (UE) 2024/1689):** usar una imagen generada como logo no obliga al dueño a etiquetarla. Las obligaciones de transparencia del art. 50 recaen en el proveedor de la herramienta (marcado legible por máquina) o en quien publica ultrafalsificaciones o textos de interés público; un logo no es ninguna de las dos cosas.

### 4.5 Requisitos de Google Play y de Android para el icono

- **Icono de la ficha** (especificaciones de diseño de iconos de Google Play): 512 × 512 px, PNG de 32 bits, sRGB, hasta 1 MB, **cuadrado completo, sin esquinas redondeadas ni sombra exterior** (Play aplica la máscara con un radio del 30 % y la sombra). El logo propuesto trae sus propias esquinas redondeadas y un halo: Play lo redondearía dos veces. **No se puede subir así** (O-6).
- **Texto en el icono:** la política no prohíbe el nombre de la marca, pero sí cualquier texto que indique clasificación, precio, promociones o programas de Play, y los símbolos engañosos. «LLAVION» es el nombre (admitido); «256», ver §3. A 48 dp en el lanzador, el texto y el «256» **no se leen**: son ruido.
- **Icono del lanzador (adaptativo, Android 8+):** la app ya tiene `ic_launcher_foreground.xml`, `ic_launcher_background.xml` y `ic_launcher_monochrome.xml` (`app/src/main/res/drawable/`). El dibujo nuevo tiene que caber en la zona segura (unos 66 de 108 dp) y tener versión monocroma para los iconos temáticos. Una imagen rasterizada con brillos no sirve: hay que pasarla a vector (lo que, de paso, crea la aportación humana del §4.4).

### 4.6 Lo que propongo

Mantener lo que sí es distintivo y quitar lo genérico:

1. **El hexágono de cinta en oro y azul**, sin la cerradura dentro. La cinta dorada ya dibuja una «L»: convertirla en un monograma «L» claro es lo más propio del nombre.
2. En el centro, si hace falta algo, **la paleta de una llave** (los dientes), integrada en la cinta, no una cerradura ni un candado; o nada.
3. **Sin texto** en el icono del lanzador ni en el de Play. El logotipo con la palabra («Llavion», en una tipografía propia, sin la «O» como cerradura) va aparte: README, gráfico de la ficha, pantalla «Acerca de».
4. **Sin «256»**, sin la punta de escudo, sin el círculo exterior ni el marco redondeado; fondo liso oscuro a sangre (Play redondea).
5. Redibujado en vector por una persona, con la imagen generada solo como referencia.

---

## 5. El cambio de nombre para quienes ya tienen Clavero

### 5.1 Cómo se hizo en la 1.3.0, y qué reutilizar

En la 1.3.0, «Seguridad» pasó a «Clavero» con: un aviso único al actualizar (`settings/RenameNotice.kt`, diálogo «Nuevo nombre» con `APP_RENAMED_TEXT` en `tools/ToolTexts.kt:736`, marcado al pulsar «Entendido», decidido solo para quien actualiza en `SettingsStore.decideRenameNotice`); «antes «Seguridad»» en la ficha, el README y la política; y `AppNameTest`, que comprueba que ningún texto nombra la app con el nombre viejo salvo el aviso. Es un buen modelo: hay que repetirlo.

### 5.2 Obligatorio avisar, y dónde

- **No hay una norma que obligue a avisar de un cambio de nombre** de una app gratuita. Pero **sí hay un riesgo real para el usuario**: un icono y un nombre distintos de repente se parecen mucho a lo que hace una app maliciosa que se hace pasar por otra. Sin aviso, un usuario prudente podría desinstalarla (y perder la bóveda si no tiene copia) o desconfiar. Por la regla de veracidad del proyecto y por la política de Play sobre metadatos claros, lo trato como obligatorio.
- **El mismo `applicationId` con otro nombre no plantea nada legal:** Google Play permite cambiar el título y el icono de una app; la actualización sigue firmada con la misma clave, que es lo que prueba que es la misma app. La ficha nueva pasará una revisión normal. Conviene que la primera línea de la descripción diga «antes «Clavero»» para que la revisión y los usuarios lo entiendan a la primera.
- **El nombre de la app sale fuera:** el User-Agent de «¿Filtrada?» y «Comprobar filtraciones» pasa de «Clavero/<versión>» a «Llavion/<versión>» (`net/HttpClient.kt:28`), y la política lo cita con un ejemplo (`docs/privacidad.html:61`). Hay que cambiar los dos a la vez (O-10).

### 5.3 Los textos que se refieren a archivos o a otras personas

Los archivos que ya tienen los usuarios los hizo «Clavero» (copias `.sgbak`, textos `SG1:`, tarjetas `SGK1:`, mensajes `SGM1:`), y los contactos de un usuario pueden seguir con la 2.0 («Clavero»). Los textos que hablan de eso **no pueden decir solo «de Llavion»**, porque el usuario buscaría algo que no tiene. Ejemplos (`vault/VaultStore.kt:1810`, `vault/PkActions.kt:81`, ficha `docs/ficha-google-play.md:39` y `:118`): «Elige una copia .sgbak de Llavion (o de Clavero)», «No es una tarjeta, un mensaje cifrado ni un texto firmado de Llavion o de Clavero», «Solo funciona entre usuarios de Llavion (también con la 2.0, que se llamaba Clavero)» (R-5). Comprobado: los formatos de contactos no llevan el nombre de la app dentro (`crypto/pk/`; la etiqueta de dominio es `seguridad-card-v1`), así que una 2.0 y una 2.1 se entienden.

---

## 6. Cambios obligatorios (O)

**O-1. Búsqueda de anterioridades del dueño antes de publicar la 2.1.** En TMview (todas las oficinas de la UE), EUIPO eSearch, OEPM (Localizador de marcas) y WIPO Global Brand Database, clases 9 y 42 (y 38 y 45 por si acaso), estas palabras: LLAVION, LAVION, AVION, LLAVIÓN, YAVION, LLAVERO, CLAVION. Y en Google Play y en la App Store: «Llavion», «Lavion», «Avion». Si aparece una marca igual o muy parecida en la clase 9 o 42, se para y se consulta. Apuntar en `progress.md` la fecha, las bases y el resultado, sin datos personales. Sustituye a la búsqueda de CLAVERO (pendientes del dueño, punto 7, y O-5 de la 2.0).

**O-2. Una sola grafía.** «Llavion», sin tilde, en todas partes (`strings.xml`, textos de la app, ficha, README, política, `PUBLICAR.md`, nombres de los APK publicados `Llavion-<etiqueta>.apk`, User-Agent, marca y dominio). Que `AppNameTest` exija esa grafía y prohíba «Clavero» fuera de las excepciones de O-7 y R-5.

**O-3. El logo no puede llevar cerradura ni escudo mientras siga vigente la decisión del dueño del 12-11-2026.** O se redibuja sin ellos (mi recomendación, §4.6), o el dueño revoca expresamente esa decisión y queda apuntado en el registro.

**O-4. Origen de la imagen y términos de la herramienta.** Antes de usar la imagen o un derivado, el dueño confirma con qué herramienta y plan se hizo, lee sus términos (titularidad o licencia de lo generado, uso comercial y como marca) y lo apunta en el registro (sin datos personales). Si los términos no permiten ese uso, no se usa. Si redibuja un tercero, cesión por escrito (arts. 43 y 45 TRLPI) compatible con la Apache-2.0.

**O-5. Búsqueda por imagen del logo definitivo.** En la búsqueda por imagen de TMview y de la WIPO Global Brand Database (clases de Viena 26.4 «hexágonos» y 14.3 «cerraduras y llaves», si las usa el sistema) y en un buscador de imágenes inverso general, antes de publicarlo. Es especialmente importante si el dibujo sale de una IA generativa (§4.3).

**O-6. El icono, conforme a Play y a Android.** Sin «256», sin esquinas redondeadas ni halo exterior propios, 512 × 512 a sangre para Play; adaptativo en vector, dentro de la zona segura y con versión monocroma, para el lanzador. Sin «256» en el icono también si el dueño eligiera la opción B (el número no se lee a ese tamaño y en el icono no lleva contexto).

**O-7. Aviso único al actualizar.** Un diálogo como el de la 1.3.0 para quien actualiza (no para una instalación nueva): «Nuevo nombre» / «Clavero ahora se llama Llavion. Es la misma app: tus datos, copias y archivos cifrados siguen igual.» Si a ese usuario aún le quedaba pendiente el aviso de «Seguridad» (actualiza desde la 1.2.x), un solo diálogo: «Esta app se llamaba Seguridad y después Clavero. Ahora se llama Llavion. Es la misma app: tus datos, copias y archivos cifrados siguen igual.» Con una marca nueva en los ajustes, como `renameNoticePending`, y la misma excepción con nombre en `AppNameTest`. Si hay guía de novedades, una línea igual.

**O-8. Notas de la versión (GitHub y «Novedades» de Play).** Primera línea: «Clavero ahora se llama Llavion, con un icono nuevo. Es la misma app, del mismo desarrollador y firmada con la misma clave: tus datos, copias y archivos cifrados siguen igual.» Sin decir por qué se cambia en términos de otras empresas (no nombrar clavero.ia ni clavero.app: no aporta nada y podría leerse como una afirmación sobre terceros).

**O-9. Ficha de Play.** Título «Llavion: bóveda cifrada» (o «Llavion»). La primera frase de la descripción corta y de la larga: «Llavion (antes «Clavero»)…». Mantener «antes «Clavero»» al menos seis meses o dos versiones; la mención de «Seguridad» puede salir de la ficha ya (lleva tres versiones) y quedarse solo en la política y el README. La regla de la cabecera de `docs/ficha-google-play.md` se actualiza con la nueva historia del nombre. El gráfico de funciones y las capturas, con el icono y el nombre nuevos.

**O-10. Política de privacidad (`docs/privacidad.html`).** Título «Política de privacidad de Llavion»; línea de cabecera «App Android «Llavion», antes «Clavero» y «Seguridad» (`io.github.egt17baa.seguridad`)»; el User-Agent «Llavion/» con el ejemplo «Llavion/2.1.0», cambiado a la vez en `net/HttpClient.kt`; todas las demás menciones de «Clavero» (incluida la de la guía, `:36`); la fecha de la última actualización. No cambia ningún tratamiento, así que no hace falta pedir nada al usuario: basta el aviso de O-7 y O-8.

## 7. Cambios recomendados (R)

**R-1.** Título de la ficha con descriptor («Llavion: bóveda cifrada»), para compensar que Play corrige hoy «llavion» a «avion».

**R-2.** Preguntar a algún hablante de los países de América Latina con más usuarios si «llavión» tiene algún uso coloquial allí.

**R-3.** Registrar la marca denominativa LLAVION en las clases 9 y 42 (EUIPO, o OEPM si se quiere solo España), sabiendo que el nombre del titular se publica. La figurativa, solo con el logo redibujado.

**R-4.** Registrar `llavion.app` (y, si se quiere, `.com` y `.es`) con privacidad del WHOIS.

**R-5.** Los textos que se refieren a archivos hechos antes o a contactos con la 2.0 nombran los dos nombres (§5.3). Revisar las 101 menciones de «Clavero» en `app/src/main` (39 archivos) y las del README (52), `PUBLICAR.md` (11), la ficha (10) y la política (8), y decidir en cada una si es «Llavion» o «Llavion (o Clavero)».

**R-6.** En el README, la línea de «Nombre» (`README.md:9`) con «Llavion» y «sus iconos, el actual y los anteriores», y la historia: «Llavion, antes «Clavero» (2.0 y anteriores desde la 1.3.0) y «Seguridad» (hasta la 1.2.2)».

**R-7.** En la pantalla «Acerca de» o «Licencias», una línea «Antes se llamaba Clavero» para quien llega a la app por una copia antigua o por un contacto.

## 8. Opcional

**OP-1.** Una tipografía propia (o libre, con licencia OFL compatible) para el logotipo con la palabra, en lugar de la del dibujo generado.

**OP-2.** Una página corta en el repositorio (`docs/`) que diga cuál es el icono y el nombre oficiales y la huella del certificado de firma, para que los usuarios puedan distinguir la app de una imitación.

---

## 9. Límites de este dictamen

- **No he podido consultar TMview, EUIPO eSearch, OEPM ni WIPO**, ni hacer una búsqueda por imagen. Lo dicho sobre el riesgo de confusión sale de búsquedas web, de Google Play, de la App Store y del RDAP de los dominios, el 22-11-2026. **No garantiza que no exista una marca anterior**; por eso O-1 y O-5 son obligatorios.
- No sé con qué herramienta se generó la imagen ni sus términos; lo dicho en §4.4 es general.
- Las tasas de las oficinas de marcas, los plazos y las políticas de Google Play cambian: comprobarlas al presentar o al publicar (pendiente de verificar a fecha de hoy).
- Es un borrador profesional para el dueño. No sustituye el asesoramiento de un abogado colegiado en cada jurisdicción ni una búsqueda de anterioridades profesional si decide registrar la marca.

## 10. Fuentes consultadas (22-11-2026)

- Google Play Console, política de metadatos: `support.google.com/googleplay/android-developer/answer/9898842`.
- Android Developers, especificaciones de diseño de iconos de Google Play: `developer.android.com/distribute/google-play/resources/icon-design-specifications`.
- Diccionario de la lengua catalana del IEC, «llavió» (copia en `latel.upf.edu/morgana/altres/diec/VERD/DD049852.HTM`).
- Google Play, búsqueda «llavion» (`play.google.com/store/search?q=llavion&c=apps`).
- App Store, ficha de Llavero.io (`apps.apple.com/nl/app/llavero-io/id1345700110`).
- Markinton, solicitud estadounidense «LAVION» 99549475 (`markinton.com/trademark/lavion-99549475`).
- arXiv 2605.25676 (artículo sobre «Llamion»).
- RDAP de los registros de `.com`, `.net`, `.org` y `.app` vía `rdap.org`.
- Normas: Ley 17/2001, de Marcas (arts. 5, 9, 34 y 37); Reglamento (UE) 2017/1001 (arts. 7, 9, 14 y 60); TRLPI (arts. 5, 43 y 45); Directiva 2005/29/CE (arts. 6 y 7); Ley 3/1991, de Competencia Desleal (arts. 5 y 7); Reglamento (UE) 2024/1689 (art. 50). Jurisprudencia: TJUE C-5/08 *Infopaq*, C-145/10 *Painer*, C-51/10 P *Agencja Wydawnicza Technopol*; *Thaler v. Perlmutter* (D.C. Cir., 2025); Tribunal Municipal de Praga, 10 C 13/2023 (referencia por confirmar). U.S. Copyright Office, *Copyright and Artificial Intelligence, Part 2* (enero de 2025).

---

## Revisión experto-seguridad-cripto

- El «256» es cierto como longitud de clave de los cifrados simétricos: AES-256-GCM en la bóveda (`app/src/main/java/app/seguridad/vault/Vault.kt:262`) y los adjuntos (`vault/Attachments.kt:133`), clave AES de 256 bits del Keystore (`android/BiometricVault.kt:138`) y los seis cifrados de `crypto/Crypto.kt:43-48`.
- No lo es como nivel de seguridad: Ed25519 y X-Wing en los contactos (`crypto/pk/Card.kt:17-19`), SHA-1 de «¿Filtrada?» (`crypto/Pwned.kt:4`) y una maestra nueva desde 50 bits estimados (`health/Health.kt:16`). De ahí la recomendación de la opción A y O-6.
- El cambio de nombre no toca formatos ni etiquetas criptográficas: `Dialect.SEGURIDAD` y su `labelPrefix` «seguridad» (`crypto/Crypto.kt:33`), los prefijos `SGK1:`, `SGM1:`, `SGS1:` (`crypto/pk/Card.kt:30`, `Message.kt:36`, `Signatures.kt:33`) y la etiqueta `seguridad-card-v1` deben quedarse igual; `AppNameTest` ya lo exige para los formatos y debería seguir haciéndolo.

## Revisión Ingeniero Móvil Senior

- El icono del lanzador es adaptativo (`app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`, con `drawable/ic_launcher_foreground.xml`, `_background.xml` y `_monochrome.xml`): el dibujo nuevo tiene que ser vector, dentro de la zona segura y con monocromo (O-6).
- El icono de Play (`docs/play/icono-512.png`) se sustituye por un 512 × 512 a sangre sin esquinas ni sombra.
- El aviso único se hace con el mismo `OnceNotice` de `settings/RenameNotice.kt` y una marca nueva decidida en `SettingsStore.decideRenameNotice` (`settings/SettingsStore.kt:59`), y el User-Agent cambia en `net/HttpClient.kt:28` a la vez que la política (O-10).
- El `applicationId`, la firma y el `FileProvider` no cambian; `AppNameTest` lo sigue comprobando.

## Textos para el Abogado Digital (revisión final)

- El texto del aviso único (O-7), sus dos variantes y la línea de la guía de novedades.
- La primera línea de las notas de la versión y de las «Novedades» de Play (O-8).
- El título y la primera frase de la ficha, y la regla de su cabecera (O-9).
- La política entera (O-10), con la fecha.
- Los textos con los dos nombres (R-5) y la línea de «Nombre» del README (R-6).
- Si se eligiera la opción B, la frase que explica el «256» en la ficha (§3.5).
