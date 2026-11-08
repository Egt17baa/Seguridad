# Clavero 1.4.0: arreglos del equipo rojo (RT-1 a RT-3), 8-11-2026

**Rama:** `ccr-d1e75160-p5f4wc`, sobre `ee30fee`, más el *commit* del equipo rojo (`5fae975`, traído con `cherry-pick`) y el de estos arreglos.

**Método (TDD):**
1. Quité el `@Ignore` de los tres `rtN_` y los vi fallar con `jvm-check`: 20 tests, 3 fallos, los tres `rtN_`.
2. Escribí los tests nuevos de cada arreglo.
3. Hice los arreglos y vi en verde los `rtN_` y los tests nuevos.
4. Pasé la puerta completa.

Los tres `rtN_` se quedan en la suite, sin `@Ignore`.

## RT-1 (decisión R34): `urlHost` lee el host como el navegador

**Antes:** `urlHost` cortaba la autoridad solo en `/`, `?` y `#`, así que `https://evil.example\@banco.es/` daba `banco.es`.

**Ahora** (`app/src/main/java/app/seguridad/imports/MergePlan.kt:372-379`), `urlHost` usa el lector de enlaces del Inspector de QR, `parseLink` (`tools/qr/LinkSignals.kt:62`), que sigue el URL Standard de WHATWG:
- en un enlace web (`http`, `https`, `ftp`, `ws`, `wss`), `\` cierra la autoridad como `/`, y la información de usuario llega hasta la última `@` antes de ese cierre;
- se quitan los tabuladores y los saltos de línea;
- el host se decodifica (`%xx`) y se mapea como en UTS #46 (ancho completo a ASCII, sin invisibles, `xn--`), y un número se lee como la IPv4 que el navegador usaría.

Así el plan de fusión y el Inspector leen igual cada enlace. `SPECIAL_SCHEMES` pasa de `private` a `internal` (`LinkSignals.kt:25`) para que los dos usen la misma lista.

**Sin esquema web:** un texto sin `://` que no empieza por un esquema web («banco.es/login», «banco.es:8443», «localhost:8080») se lee con `https://` delante, como lo haría la barra de direcciones. Antes se tomaba igual, por la parte anterior a `:` y a `/`.

**Lo que no cambia:**
- un esquema no web con `//` (`androidapp://com.app`) se lee por su autoridad, como antes;
- un host vacío se compara con el texto entero, recortado y en minúsculas, como antes;
- se quitan `www.`, el puerto, el usuario, las mayúsculas, la ruta, la consulta y el fragmento.

**Ni lanza ni resuelve:** `parseLink` es trabajo de cadenas. No usa `java.net.URI` y captura las excepciones de `IDN`, que solo convierte una etiqueta a su forma `xn--`; no hay DNS ni red. El test de 20 000 enlaces al azar con `\`, `@`, `[`, `%40`, sustitutos sueltos e invisibles lo comprueba.

**Tests:**
- `RedTeamImportTest.rt1_aUrlThatGoesToAnotherSiteIsNotADuplicate`: en verde.
- `RedTeamImportTest.rt1Impact_replaceCannotTakeTheAttackersUrlAndPassword`: antes documentaba el daño. Ahora comprueba que «Reemplazar» deja intacta la entrada de la bóveda y que la fila entra como nueva, guardada tal cual (R9).
- `MergePlanTest.theHostIsTheOneABrowserGoesTo` (`:84`):
  - `\`, `\@`, `:\\`, `:/\`, mayúsculas, `www.` y puerto, usuario delante, sin esquema, un tabulador dentro y `wss`;
  - la última `@` antes del cierre;
  - todo lo que ya se comparaba igual;
  - `localhost:8080`, IPv6, `androidapp://` y el host vacío.
- `MergePlanTest.theHostAgreesWithTheQrReaderForEveryWebLink` (`:118`) y `aUrlOfAnotherSiteIsNotADuplicateAndReplaceCannotTakeIt` (`:131`).
- `loginDuplicatesByTitleUserAndHost` sigue en verde.

**No aplicado:** la sugerencia del informe de enseñar el host en la fila del duplicado (`ui/screens/ImportScreen.kt:664`). El encargo pedía no cambiar textos de la interfaz. Con RT-1 cerrado, una URL de otro sitio ya no llega a esa fila. Queda como mejora posible, que tendría que pasar por el Abogado Digital.

## RT-2 (decisión R34): lo que se ve igual es un duplicado

**Ahora** hay una función compartida, `compareKey` (`app/src/main/java/app/seguridad/imports/ImportModel.kt:139-153`), junto a `plainReportText`:
- toma `plainReportText(text, Int.MAX_VALUE)`, es decir, lo mismo que se enseña, sin el corte de `MAX_TITLE`, que no vale para comparar;
- lo pasa a NFC;
- deja cada tramo de espacios (también el espacio de no separación y los controles que `plainReportText` enseña como espacio) en un solo espacio, sin ninguno en los extremos.

**Coste:** una pasada por punto de código más NFC, cuya comprobación rápida hace barato el texto normal. 40 000 claves tardan muy poco en el test (`ImportModelTest.compareKeyIsNotCutNeverThrowsAndIsCheap`, con un margen de 5 s).

**No lanza:** `plainReportText` ya quita los sustitutos sueltos antes de NFC.

**Uso en el plan** (`imports/MergePlan.kt`):
- `titleKey` (`:356`) es `compareKey(...).lowercase(Locale.ROOT)`: el título sigue sin distinguir mayúsculas;
- `userKey` (`:359`) es `compareKey(...)`, sin `lowercase`: el usuario las sigue distinguiendo, por decisión del controlador, porque una mayúscula distinta se ve;
- se usa en `:398` y `:413` (inicios de sesión) y en `:407` y `:418` (notas, donde solo el título pasa por `titleKey` y el texto sigue con `trim()`).

Lo guardado no cambia (R9, R10): solo cambia la comparación.

**Un detalle añadido:** juntar los espacios seguidos y tratar igual el espacio de no separación. «Mi banco» con dos espacios, o con `U+00A0`, se ve igual que con uno, y sin esto quedaría una entrada sombra del mismo tipo. Lo que sí se ve distinto sigue siendo distinto:
- un espacio dentro de una palabra («Ban co»);
- otra letra;
- las letras de ancho completo (sin NFKC);
- una mayúscula en el usuario.

Hay tests de todos estos casos.

**Tests:**
- `RedTeamImportTest.rt2_aTitleOrUserThatLooksTheSameIsADuplicate`: en verde.
- `MergePlanTest.aTitleOrUserThatLooksTheSameIsADuplicate` (`:142`): ZWSP, NFD, RLO, NBSP, NUL, guion suave, BOM, U+2060 y U+034F son duplicados; lo visible (mayúscula en el usuario, «Cafe», «Ban co», ancho completo, «a na») no lo es.
- `aNoteWhoseTitleLooksTheSameIsADuplicateAndItsTextIsComparedAsBefore` (`:159`) y `aLookAlikeIsStoredAsItCameWhenTheUserKeepsBoth` (`:166`).
- `ImportModelTest.compareKeyIsWhatIsShownInNfcWithTheSpacesFolded` (`:69`) y `compareKeyIsNotCutNeverThrowsAndIsCheap` (`:83`).
- El comentario de `loginDuplicatesByTitleUserAndHost` que decía «the user is only trimmed, never folded» ahora dice que el usuario conserva las mayúsculas (R34).

**Para el controlador:** el diseño dice que el usuario se compara «sin espacios al principio ni al final». Con R34 se compara además como se ve. Si el diseño o el plan deben recogerlo, es cosa del registro, que no toco.

## RT-3 (decisión (h) del dueño, «Rechazarlas»): la maestra nueva no puede ser una palabra conocida en *leet* ni en ancho completo

**Alcance:** solo `newMasterProblem`, que solo llaman crear la bóveda (`ui/screens/VaultScreen.kt:273`) y el diálogo de maestra nueva (`ui/screens/VaultBackup.kt:234`, con `newPassword`). Abrir, la huella e importar una copia no pasan por aquí. El texto sigue siendo `MASTER_TOO_WEAK`.

**NFKC en `masterCore`** (`vault/MasterPolicy.kt:52-63`): NFKC antes de las minúsculas y del plegado. «ｐａｓｓｗｏｒｄ12345» da «password» y «𝐩𝐚𝐬𝐬», «pass». Las listas pasan también por `masterCore`: NFKC no cambia nada en ellas (letras normales; la española, en NFD, se vuelve a descomponer). Siguen siendo 10 937 palabras, con 10 letras como máximo.

**La comprobación *leet*,** `isKnownWordInLeet` (`:101-122`), con la regla en `:233`. La contraseña es una palabra conocida si se puede partir en `inicio + palabra + final`, donde:
- el inicio y el final no tienen letras (cifras, símbolos, espacios o nada);
- la palabra, con las sustituciones devueltas a letras y lo demás como lo deja `masterCore`, está en las listas.

Las sustituciones (`leetLetter`, `:76-86`): 0→o, 3→e, 4→a, @→a, 5→s, $→s, 7→t, +→t, 8→b, 6→g, 9→g. El 1, el ! y el | se prueban una vez todos como «i» y otra todos como «l». El 2 nunca es «z», porque estropearía los años.

**Mejoras sobre el diseño propuesto:**
- Como el inicio y el final no tienen letras, la palabra contiene todas las letras de la contraseña: solo se prueban los cortes que empiezan antes de la primera letra y acaban después de la última. Son (primera + 1) × (n − última) núcleos por cada lectura del «1». Cada uno se construye añadiendo un trozo al anterior, y los trozos de cada punto de código se calculan una vez. Así es O(n²) con n ≤ 128, sin la normalización de cada subcadena.
- Una contraseña sin letras nunca se mira. Si no, una de solo cifras («7357…») tendría casi siempre un trozo que en *leet* es una palabra corta, y el Generador de cifras se rechazaría mucho más. Las cifras siguen a cargo de la estimación, como antes.
- Por encima de 128 puntos de código en NFKC (`LEET_MAX_CODE_POINTS`, `:69`) no se prueba. La regla de la palabra sin *leet* se sigue aplicando a cualquier longitud.
- Sin expresiones regulares en el bucle, así que no hay riesgo de *backtracking*. Peor caso medido (128 puntos de código, una letra en medio): 20 comprobaciones muy por debajo del margen de 2 s del test, porque `newMasterProblem` corre en cada pulsación.

**KDoc:** los de `masterCore`, `isKnownWordInLeet` y `newMasterProblem` dicen qué hace y qué no hace (otras grafías pasan si la estimación llega), y que es una estimación: «does not guarantee that a master is strong».

**Tests:**
- `RedTeamCryptoTest.rt3_aKnownWordInLeetIsRefusedAsANewMaster`: en verde.
- `RedTeamCryptoTest.resistsAKnownWordWithDigitsAndSymbolsAround`: sigue en verde.
- `MasterPolicyTest` (`:237-296`):
  - NFKC (ancho completo, negrita matemática, la ligadura «ﬁ», cifras de ancho completo);
  - los 7 ejemplos del informe y 6 más («1l0v3y0u2024», «4dm1n-2024!!», «!!P4SSW0RD!!», «2024-dr4g0n!», «p4ss-w0rd 99», «Ｐ４ｓｓｗ０ｒｄ２０２４»), cada uno por encima de la estimación y rechazado por la palabra;
  - cada sustitución, que el 2 no es «z» y que el «1» no se mezcla;
  - que no se deja fuera ninguna letra y que sin letras o sin listas no hay palabra;
  - el tope y el peor caso;
  - lo que el dueño aceptó: «Barcelona1», «B4rc3l0n4!!», dos palabras juntas (también en *leet*), una frase y contraseñas al azar al estilo del Generador.
- Siguen en verde todos los tests de antes, también los del Generador con la semilla fija.

### Tasa de rechazo del Generador, medida de nuevo

Medida con un test desechable, que no está en el *commit*. Uso el algoritmo real del Generador: 4 posiciones forzadas (una minúscula, una mayúscula, una cifra y un símbolo) y las demás al azar entre los 89 caracteres. Estratifico por el número `j` de letras de las posiciones libres: un millón de muestras exactas por estrato, ponderadas con la binomial (52/89). Por encima de 10 letras no puede haber rechazo por palabra, porque ninguna tiene más de 10 y el núcleo *leet* nunca tiene menos letras que la contraseña. Lo comprobé además con 200 000 contraseñas del Generador por longitud: 0 rechazos con más de 10 letras.

Se mide `newMasterProblem` entero (estimación y palabra). «Antes» es la regla sin *leet*.

| Generador (4 clases) | Antes de RT-3 | Después de RT-3 | Cifra del KDoc de antes (cálculo exacto de la 1.3.1) |
|---|---|---|---|
| 12 caracteres | 1 de cada 3 791 | 1 de cada 3 136 | 1 de cada 3 900 |
| 16 caracteres | 1 de cada 60 440 | 1 de cada 54 293 | 1 de cada 61 000 |
| **20 caracteres (por defecto)** | 1 de cada 1 144 381 | **1 de cada 1 075 259** | 1 de cada 1,1 millones |

Lo de «antes» coincide con el cálculo exacto de la 1.3.1, dentro del ruido, y eso valida el método. A 20 caracteres, la tasa queda en torno a 1 de cada 1,1 millones, muy lejos del límite de 1 de cada 100 000. El KDoc de `newMasterProblem` (`vault/MasterPolicy.kt:222-227`) y el de `fixedRandom` (`MasterPolicyTest.kt:434-440`) dan ahora 1 de cada 3 100, 54 000 y 1,1 millones. Con 16 caracteres, una tanda de 200 contraseñas al azar fallaría 1 vez de cada 270 ejecuciones (antes, 308).

Lo demás del Generador, antes y después de RT-3:

| Generador | Antes | Después |
|---|---|---|
| Frases de 5 palabras: EFF, BIP-39 en español y en inglés; los 4 separadores, con y sin mayúsculas y número (160 000 por lista) | 0 | 0 |
| 16 caracteres sin símbolos (500 000) | 0 | 0 |
| 20 caracteres sin símbolos (500 000) | 0 | 0 |
| 16 letras, sin cifras ni símbolos (500 000) | 1 | 1 |
| 16 caracteres, 4 clases (500 000, muestreo directo) | 10 | 10 |
| Solo cifras, 10 / 12 / 16 / 20 / 24 / 28 caracteres (200 000 de cada) | todas / todas / 32,7 % / 1,8 % / 0,03 % / ninguna | igual: la comprobación *leet* no mira una contraseña sin letras |

**Una observación que ya estaba en la 1.3.1:** con 16 letras solas, la estimación de «Salud» rechaza muy de vez en cuando una contraseña al azar con un tramo del teclado o letras repetidas (`XGIyuIoptrqnqWXT`, 45,6 bits; `bBbRStzzUdZXyYxw`). Son 2 de cada 3 millones en una prueba aparte. No es de RT-3 y no la cambio, porque es la estimación que el dueño eligió. El KDoc de `newMasterProblem` lo dice ahora («fewer than 1 in 500,000 with 16 letters only»), y «↻ Otra» lo resuelve.

## Puerta

En el *worktree*, el 8-11-2026, con los arreglos:

- `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets --no-daemon --max-workers=1`: **BUILD SUCCESSFUL** (6 min 11 s); `:app:testDebugUnitTest`: 1 785 tests en 111 clases, 0 fallos y 0 ignorados (antes de esta ronda, los 3 `rtN_` ignorados).
- `./gradlew -p jvm-check test --no-daemon --max-workers=1` (el *wrapper*, Gradle 9.6; R29): **BUILD SUCCESSFUL**; 1 780 tests en 110 clases, 0 fallos y 0 ignorados.

Las suites tocadas, en `jvm-check`, sin ningún test ignorado:

| Suite | Tests | Ignorados | Fallos |
|---|---|---|---|
| `RedTeamImportTest` | 12 | 0 | 0 |
| `RedTeamCryptoTest` | 8 | 0 | 0 |
| `MergePlanTest` | 34 (28 + 6) | 0 | 0 |
| `ImportModelTest` | 11 (9 + 2) | 0 | 0 |
| `MasterPolicyTest` | 42 (36 + 6) | 0 | 0 |

## Revisión experto-seguridad-cripto

He leído `SKILL.md` y he recorrido `references/auditoria.md` y `references/cripto-moderna.md` (contraseñas y validación de entradas) como lista de comprobación de este diff.

- **Sin criptografía nueva ni cambios de formato:**
  - RT-1 y RT-2 solo cambian cómo compara el plan de fusión (`imports/MergePlan.kt:356-379`). Nada de lo que se guarda cambia (R9, R10; `MergePlanTest.aLookAlikeIsStoredAsItCameWhenTheUserKeepsBoth`, `:166`).
  - RT-3 solo afecta a una maestra nueva (`vault/MasterPolicy.kt:233`). Ninguna bóveda ni copia que exista deja de abrirse, porque abrir, la huella e importar no pasan por `newMasterProblem` (`MasterPolicyTest`, «creating the vault and changing the master call newMasterProblem»).
- **Modelo de amenaza de RT-1:** el atacante controla el archivo importado. Ahora el plan y el navegador están de acuerdo en el host (`MergePlan.kt:376-378`, con `parseLink`), así que una URL de otro sitio entra como entrada nueva, visible en la vista previa, y «Reemplazar» nunca la aplica a la entrada buena (`MergePlanTest.kt:131`).
  - Lo que queda: dos lectores distintos podrían seguir sin estar de acuerdo en casos que `parseLink` tampoco ve (por ejemplo, reglas futuras de WHATWG). Ahora es un solo lector, el que ya revisaron el lote MN y el Inspector de QR.
  - Que Chrome y Firefox de Android vayan a `evil.example` sigue en «Necesita prueba en el móvil», punto 1.
- **Modelo de amenaza de RT-2:** la entrada sombra necesitaba que dos textos se vieran igual y se compararan distinto. Ahora se comparan tal como se enseñan (`ImportModel.kt:139-153`, sobre `plainReportText`, `:107`).
  - Lo que queda, ya documentado en `plainReportText`: las letras parecidas de otros alfabetos (una «а» cirílica). No se pliegan, igual que en la pantalla. Es un riesgo aceptado, y la vista previa enseña la entrada como nueva.
- **Entradas hostiles:** `compareKey` y `urlHost` no lanzan con ninguna entrada (`MergePlanTest.kt:118`, 20 000 enlaces al azar con sustitutos sueltos; `ImportModelTest.kt:83`). Son lineales, o casi, en el tamaño del campo, que ya está acotado por `BACKUP_MAX_ENTRY_BYTES` y por los 20 000 elementos (R12). No hay expresiones regulares nuevas.
- **RT-3, ataque sin conexión a una copia robada:** las variantes *leet* y de ancho completo de una palabra común son lo primero que prueban las reglas de hashcat y de John the Ripper. Ya no se aceptan como maestra nueva (`MasterPolicyTest.kt:245`). Argon2id sigue siendo la defensa principal.
  - La regla sigue siendo una estimación y lo dice (`MasterPolicy.kt:212-215`). Otras grafías, los nombres propios y los datos personales pasan si la estimación llega.
  - Coste de la regla para el usuario: la tasa de rechazo del Generador a 20 caracteres sigue en torno a 1 de cada 1,1 millones (tabla de arriba).
- **Sin DoS en la pantalla:** la comprobación *leet* tiene un tope de 128 puntos de código y es O(n²) (`MasterPolicy.kt:69` y `:101-122`). El peor caso tiene test (`MasterPolicyTest.kt:277`).
- **Secretos en memoria:** `masterCore` y la comprobación *leet* hacen copias de la contraseña en `String` y `StringBuilder` que no se pueden poner a cero. Es lo mismo que ya hacían `masterCore` y `estimateBits`, y la contraseña ya es un `String` en el campo de texto (KDoc de `masterCore`, `MasterPolicy.kt:47-49`). Nada se registra ni se guarda.
- **Búsquedas hechas en el diff:** no hay `Log.`, `println` ni `printStackTrace` en `app/src/main/java`. No hay dependencias, permisos, hosts ni componentes nuevos (solo cambian cuatro archivos `.kt` de `main`).

## Revisión Ingeniero Móvil Senior

He leído las skills `desarrollo-movil-senior` y `auditoria-seguridad-movil`.

- **Rendimiento en la interfaz:** `newMasterProblem` se llama al recomponer, en cada pulsación (`ui/screens/VaultScreen.kt:273` y `VaultBackup.kt:234`).
  - La comprobación *leet* solo corre si la estimación y la palabra sin *leet* no han rechazado ya (`MasterPolicy.kt:232-233`: el orden de `when` y el `||`).
  - Tiene un tope de 128 puntos de código, calcula los trozos una vez por lectura del «1» y construye los núcleos añadiendo; no hay expresiones regulares en el bucle (`:101-122`).
  - Con una maestra normal (10 a 30 caracteres) son unas decenas de búsquedas en un `HashSet`.
- **Plan de fusión:**
  - `compareKey` y `urlHost` corren dentro de `planMerge`, fuera del hilo principal como antes (`ui/screens/ImportScreen.kt:301`, en `Dispatchers.Default`, sin cambios).
  - Una clave por entrada, para la bóveda y para el archivo (`MergePlan.kt:398` y `:413`). Con 20 000 entradas, la medida del test cabe de sobra (`ImportModelTest.kt:83`).
  - `parseLink` usa `IDN` solo para las etiquetas que no son ASCII.
- **Sin cambios de Compose ni de estado:** ningún archivo de `ui/` cambia. `SPECIAL_SCHEMES` pasa a `internal` (`LinkSignals.kt:25`), sin cambio de comportamiento en el Inspector.
- **JVM y Android:** todo es Kotlin puro de `imports/`, `vault/` y `tools/qr/`, así que corre en `jvm-check` y en `:app:testDebugUnitTest`. `java.text.Normalizer` (NFC y NFKC) está en Android desde la API 9.
- **MASVS:**
  - MASVS-CODE-4 (validación de entradas): RT-1 y RT-2;
  - MASVS-PLATFORM-3 (la interfaz no engaña): RT-2, porque la vista previa ya no enseña como «nueva» algo que se ve igual que una entrada de la bóveda;
  - MASVS-AUTH-1 y MASVS-CRYPTO-2 (calidad de la maestra que protege la clave): RT-3.

## Textos para el Abogado Digital (revisión final)

Ninguno nuevo. No cambia ningún texto de la interfaz: `MASTER_TOO_WEAK` es el mismo (`MasterPolicy.kt:27-28`), y la sugerencia de enseñar el host en la fila del duplicado (RT-1) no se aplicó. Los KDoc nuevos están en inglés y son comentarios de código.

Una nota de veracidad para la revisión final: si la política, el README o la ficha describen la regla de la maestra nueva, ahora también rechaza una palabra conocida escrita con cifras o símbolos en lugar de letras, o en letras de ancho completo. Sigue siendo una estimación que no garantiza que la maestra sea fuerte. No he buscado ni cambiado esos textos, porque el README, la ficha y el traspaso son de la ronda de la tarea 12.
