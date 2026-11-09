# Clavero 1.4.0: nueva comprobación de los arreglos del segundo ataque (RT-4 a RT-7)

**Rama:** `ccr-d1e75160-p5f4wc`, sobre `eec1ad5` (arreglos en `bf04f45`). **Decisión del dueño:** (f), después de cada arreglo, el equipo rojo vuelve a atacarlo. **Decisiones que comprueba:** R41 a R44 (`progress.md`), con (c) y (h).

Lo hace un agente nuevo del equipo rojo, que no hizo el segundo ataque. Trabaja solo con tests unitarios, en local y sobre la app del dueño. No ha cambiado código de producción ni ha hecho ningún commit.

Rutas de Kotlin relativas a `app/src/main/java/app/seguridad/`. Los tests están en `app/src/test/java/app/seguridad/redteam/RedTeamRecheckTest.kt`, con el mismo criterio que antes:
- un `rtN_…` comprueba lo seguro, falla mientras el hallazgo sigue abierto y lleva `@Ignore` solo para la puerta;
- un `resists…` pasa.

## Resumen

| RT | Veredicto | Lo que queda |
|---|---|---|
| RT-4 (R41) | **Se sostiene** cuando las dos entradas tienen URL. Resiste 25 variantes que deben ser duplicado y 29 que no deben serlo. | Al lado del arreglo: **RT-8** (Baja). Cuando la entrada de la bóveda no tiene URL, una URL de cualquier esquema es duplicado y la fila solo enseña el host. También **RT-12** (Informativa). |
| RT-5 (R42) | **Se sostiene.** «¡», «€», «ø» y «Ø» se reconocen en mayúsculas, al principio, en medio y al final, con ancho completo y mezclados con el *leet* ASCII, y no hay rechazos de más. | Otras sustituciones y letras parecidas: **RT-11** (Informativa). |
| RT-6 (R43) | **No se sostiene del todo.** Los puntos de código del informe ya no se enseñan ni separan claves, y los emoji se ven. Pero la lista no es una sola: los nombres de archivo quitan U+2065 y U+FFF0–U+FFF8, y lo que se enseña los conserva. | **RT-9** (Baja), **RT-13** (Informativa) y, en el Inspector de QR, **RT-10** (Baja). |
| RT-7 (R44) | **Se sostiene.** NFKC pliega las formas de compatibilidad. No vuelve a meter lo que no se dibuja ni deja espacios de más, y lo guardado no cambia. | Nada nuevo. Los parecidos que NFKC no pliega (otras escrituras, variantes de guion y de apóstrofo) siguen como los dejó el segundo ataque, aceptados por R9. |

Recuento: **3 hallazgos nuevos de severidad baja (RT-8, RT-9 y RT-10) y 3 informativos (RT-11, RT-12 y RT-13)**. No hay ninguno crítico, alto ni medio.

Los arreglos de RT-1 a RT-3 y todo lo resistido del segundo ataque siguen en verde: `RedTeamImportTest` (12 tests), `RedTeamCryptoTest` (8) y `RedTeamSecondAttackTest` (13), sin omitidos ni fallos.

**Clase nueva:** `RedTeamRecheckTest`, con 14 tests: 8 `resists…` en verde y 6 `rtN_` con `@Ignore`. Se ejecutó con `./gradlew -p jvm-check test --tests 'app.seguridad.redteam.*' --no-daemon --max-workers=1`, que compila los tests de `app/src/test/java`. Resultado: **BUILD SUCCESSFUL**, 0 fallos y ningún aviso `w:` del compilador. Sin el `@Ignore`, los seis `rtN_` fallan con los mensajes que se citan abajo; después volví a poner los `@Ignore`.

## RT-4 (R41): se sostiene

**Evidencia:** `resistsSchemeAndAuthorityVariantsAroundTheRt4Fix` (`RedTeamRecheckTest.kt:73`) y `resistsReplacingWithoutAUrl` (`:101`). Código: `urlKey` (`imports/MergePlan.kt:401-407`), `comparedLink` (`:416-423`) y `Matcher.find` (`:474-478`).

Contra una entrada `https://banco.es/` de la bóveda:
- **Son duplicado,** porque el navegador va a https y a banco.es:
  - el esquema escrito de otras formas: `HTTPS://BANCO.ES`, `hTtPs://www.banco.es/login`, `https:/banco.es`, `https:banco.es`, `https:\\banco.es`, `https:\/banco.es`, `https:///banco.es`;
  - sin esquema: `//banco.es`, `\\banco.es`, `banco.es`, `banco.es/login`, `banco.es:8443/x`, con espacios alrededor;
  - con información de usuario: `https://ana@banco.es/`, `https://ana:x@banco.es/` y `https://evil.example%2F@banco.es`. Un «/» codificado no termina la autoridad, así que el host es banco.es, como lo lee el navegador;
  - el host escrito de otras formas: el punto final, `%62anco`, el ancho completo, el punto ideográfico;
  - con puertos, `#` o `?`.
- **No son duplicado** (son entradas nuevas, y «Reemplazar» no toca la de la bóveda):
  - otro esquema web: `http://`, `HTTP://`, `http:banco.es`, `http:\\banco.es`, `ftp://`, `ws://`, `wss://`;
  - un esquema que no es web: `file://banco.es/x`, `file:///sdcard/banco.es`, `javascript://banco.es/%0Aalert(1)`, `JavaScript://`, `intent://…;end`, `content://`, `https+x://`, `mailto:ana@banco.es`, `javascript:x@banco.es/`, `data:text/html,banco.es`;
  - un esquema que lleva otra URL dentro: `javascript:https://banco.es`, `blob:https://…`, `view-source:https://…`;
  - el host es otro: `https://banco.es@evil.example`, `https://evil.example\@banco.es`, `https://banco.es.evil.example`, IDN con «а» cirílica, `banco.es..`, `banco.es:8443@evil.example`;
  - **del lado seguro:** `java\tscript://banco.es/`, `ht\ttps://banco.es` y `\u0001https://banco.es`. Un navegador quita el tabulador y el control del principio, pero la clave no, y estas URL entran como nuevas. Se ven en la vista previa y no cambian nada de la bóveda.
- Un elemento del archivo sin URL es duplicado por título y usuario, y «Reemplazar» conserva la URL de la bóveda (`replaceWith`, `MergePlan.kt:263`).

### RT-8 [Baja] Una entrada de la bóveda sin URL recibe una URL de cualquier esquema, y la fila solo dice el host

Es el punto abierto (1) que dejó quien hizo los arreglos (`progress.md`, línea «Open points»). Lo confirmo con un test.

**Cómo lo he atacado.** En la bóveda hay una entrada «Banco» / «ana» **sin URL**. En el archivo, la misma fila trae `javascript://banco.es/%0Aalert(1)`, `intent://banco.es/#Intent;scheme=https;package=com.example.evil;end`, `file://banco.es/sdcard/x.html` o `http://banco.es/login`. Probé también una segunda variante: la bóveda tiene la entrada `https://banco.es/` **y además** otra «Banco» / «ana» sin URL.

**Lo que pasa:**
- `Matcher.find` (`imports/MergePlan.kt:477`) usa `g.any` cuando el elemento no tiene URL, y `g.byHost[host] ?: g.hostless` cuando la tiene. Por eso cualquier URL es duplicado de una entrada sin URL que tenga el mismo título y el mismo usuario;
- `duplicateSubline` (`:435-441`, R41) enseña «ana · banco.es» para las cuatro URL;
- con «Reemplazar», `replaceWith` (`:263`) le pone a la entrada de la bóveda la URL del archivo;
- en la segunda variante, `http://banco.es/` no coincide con la entrada `https` y va a parar a la que no tiene URL: el mismo resultado.

**Evidencia:** sin el `@Ignore`, `rt8_aUrlOfAnySchemeDoesNotEnterSilentlyAnEntryWithoutUrl` (`RedTeamRecheckTest.kt:119`) falla con: `«javascript://banco.es/%0Aalert(1)» against [VaultEntry(… url= …)]: a duplicate whose row «ana · banco.es» hides the scheme; after «Reemplazar»: [javascript://banco.es/%0Aalert(1)]`.

**Impacto.** Bajo, como RT-4. CVSS 3.1: 2,5 (`AV:L/AC:H/PR:N/UI:R/S:U/C:N/I:L/A:N`).
- Hace falta que el usuario importe el archivo del atacante, que tenga una entrada sin web con el mismo título y usuario, y que elija «Reemplazar».
- Clavero no abre ningún enlace (decisión (c)). El daño es que la entrada guarde una dirección `http`, `intent:` o `javascript:` mientras la fila decía solo «banco.es»; quien la copie y la abra puede acabar en otro sitio.
- La contraseña de antes queda en el historial.

**Arreglo** (cualquiera de los dos):
1. **Sin cambiar textos (recomendado):** en `Matcher.find`, cuando la coincidencia viene de una entrada sin URL, aceptarla solo si la URL que llega es `https` (o no tiene esquema, que se lee como `https`):
   ```kotlin
   if (host == null) g.any else g.byHost[host] ?: g.hostless?.takeIf { host.startsWith("hhttps://") }
   ```
   Así, `http`, `javascript:`, `intent:` o `file:` contra una entrada sin web entran como entradas nuevas, que se ven en la vista previa. Es lo que R41 dice de `http` frente a `https`.
2. **En la fila:** que `duplicateSubline` enseñe el esquema cuando no es `https` («ana · javascript://banco.es»). Es texto de la interfaz, así que pasa por el Abogado. R41 decidió no hacerlo.

**Test que lo dará por arreglado:** `rt8_…`, sin el `@Ignore`. Acepta cualquiera de los dos arreglos.

### RT-12 [Informativa] Una URL sin host se compara y se enseña en minúsculas

**Cómo lo he atacado.** En la bóveda está `data:text/plain;base64,SGVsbG8=`. En el archivo, `data:text/plain;base64,sGVSbg8=`: otro contenido, porque Base64 distingue mayúsculas de minúsculas.

**Lo que pasa:** `urlKey` compara la URL sin host entera y en minúsculas («t» + `t.lowercase`, `imports/MergePlan.kt:406`), así que las dos son duplicado. La fila enseña la URL que llega en minúsculas (`urlHost`, `:390`): «ana · data:text/plain;base64,sgvsbg8=». No es el texto que entraría.

**Evidencia:** sin el `@Ignore`, `rt12_aUrlWithoutAHostKeepsItsCase` (`:145`) falla con `a duplicate, row «ana · data:text/plain;base64,sgvsbg8=»`.

**Impacto.** Informativo. Las dos URL tienen el mismo esquema, Clavero no abre ningún enlace y es raro guardar una URL `data:` o `javascript:` en una entrada.

**Arreglo:** en las claves «t» y en lo que da `urlHost` para ellas, pasar a minúsculas solo el esquema y dejar el resto como está. Por ejemplo: `schemeOf(t)?.let { it + t.substring(it.length) } ?: t`, con «t» delante en `urlKey`.

## RT-5 (R42): se sostiene

**Evidencia:** `resistsLeetVariantsAroundTheRt5Fix` (`RedTeamRecheckTest.kt:158`). Código: `leetLetter` (`vault/MasterPolicy.kt:91-102`) e `isKnownWordInLeet` (`:117`).

Se rechazan, todas por encima de la estimación, solo por la palabra:
- en mayúsculas o con el «¡» al principio y al final: «PR¡NC3SS2024», «¡Pr¡nc3ss!2024», «¡¡Dr4gøn!!2024»;
- con ancho completo: «Ｐr¡nc€ss2024»;
- con «Ø»: «Dr4gØn!2024», «DR4GØN!2024», «P4$$wØrd!2024»;
- con «ç» y acentos: «Prinçess2024!», «Pàsswörd2024!»;
- mezcladas con el *leet* ASCII: «Sh4døw€2024!», «C0ntra$€ña2024», «M¡ll¡on2024!», «H€ll0!2024!!».

No hay rechazos de más: «¡Q7x€zø#Kp2m», «ø€¡ vTq9#Lm2!Rz», «R7#q¡Xv2€Lm9$Tzø» y una frase pasan. El Generador solo usa ASCII, así que su tasa de rechazo no cambia, como dice el informe de los arreglos.

### RT-11 [Informativa] Otras sustituciones y letras parecidas que la regla no lee

**Cómo lo he atacado.** He propuesto como maestra nueva once contraseñas. Todas superan la estimación, entre 73 y 99 bits, y todas se aceptan:
- **Otros símbolos de las tablas de *leet* habituales:** «Pr1n(3ss2024!», «Pr1n<3ss2024!», «Pr1n©3ss2024» («(», «<» y «©» por c), «P4§§w0rd2024» («§» por s), «P4ssw°rd2024!» («°» por o), «#3ll0!2024!!» («#» por h).
- **Letras que NFKC no pliega:** «Prıncess2024!» (la «ı» sin punto), «ᴘʀɪɴᴄᴇss2024!» (versalitas), «Pаssword2024!» (la «а» cirílica) y «🅿🅰🆂🆂🆆🅾🆁🅳2024». Estas últimas son símbolos, no letras, así que el núcleo se queda sin letras y la comprobación no mira nada.
- **«1» leído como «i» y como «l» en la misma palabra:** «M1|l1on2024!». Es un límite que el KDoc ya dice (`vault/MasterPolicy.kt:230-231`).

**Evidencia:** sin el `@Ignore`, `rt11_otherLeetAndLookAlikeLettersAreRefused` (`:181`) falla con `accepted: «Pr1n(3ss2024!» (85 bits), «Pr1n<3ss2024!» (85 bits), «Pr1n©3ss2024» (88 bits), «P4§§w0rd2024» (88 bits), «P4ssw°rd2024!» (99 bits), «#3ll0!2024!!» (73 bits), «Prıncess2024!» (99 bits), «ᴘʀɪɴᴄᴇss2024!» (96 bits), «Pаssword2024!» (99 bits), «🅿🅰🆂🆂🆆🅾🆁🅳2024» (81 bits), «M1|l1on2024!» (79 bits)`.

**Impacto.** El mismo que RT-3 y RT-5, aunque más pequeño. Los símbolos de la primera lista están en reglas de diccionario conocidas. Las letras de otras escrituras y las versalitas son menos habituales, y quien las escribe ya se aparta de lo que prueba un diccionario. La regla es una estimación (CRY-A01), y su KDoc ya dice que solo conoce las sustituciones habituales.

**Arreglo** (lo decide el dueño, como (h)):
- **Opción A:** añadir a `leetLetter` «(», «<», «©» y «¢» → c; «§» → s; «°» → o; «#» → h; «ı» → i; «ł» → l; y las versalitas U+1D00–U+1D22 a su letra, con una tabla fija. **Ojo:** «(», «<» y «#» están en `SYMBOLS` del Generador (`crypto/Generator.kt:9`), así que hay que volver a medir su tasa de rechazo, con el método de `redteam-second-fixes-report.md`.
- **Opción B:** dejarlo como límite documentado. El KDoc de `newMasterProblem` ya lo dice, y bastaría con añadir los ejemplos.

Las letras de otras escrituras siguen fuera en cualquier caso: R9 ya las acepta como parecidas.

**Después (R50, 9-11-2026):** RT-11 pasa a la lista del dueño para la 1.4.1, y su prueba `rt11_otherLeetAndLookAlikeLettersAreRefused` sale de `RedTeamRecheckTest` para que la puerta siga sin omitidos; las once contraseñas de arriba son su registro.

## RT-6 (R43): no se sostiene del todo

**Lo que sí se sostiene**, con evidencia en `resistsInvisiblesAroundTheRt6Fix` (`RedTeamRecheckTest.kt:194`) y `resistsEmojiThatMustStillShow` (`:215`):
- **No se enseñan ni separan claves, ni solos ni repetidos:**
  - los rellenos de Hangul U+115F, U+1160, U+3164 y U+FFA0;
  - U+2061–U+2064 y U+206A–U+206F;
  - U+FFF9–U+FFFB, U+1D173–U+1D17A, U+034F, U+1BCA0–U+1BCA3, U+2800, U+17B4, U+180B y U+180F;
  - U+FE00 y U+FE0E;
  - todo el plano 14 (U+E0001, U+E0080, U+E0100, U+E01EF, U+E01F0, U+E0FFF);
  - U+200B, U+2060, U+FEFF y U+00AD.
- **Los sustitutos sueltos** (alto, bajo, al revés, al final) se quitan, y lo que se enseña y lo que se compara coinciden.
- **U+FE0F detrás de ASCII** se quita, también al principio y junto a un ZWJ.
- **Una torre de marcas** se corta igual en los dos lados.
- **Los emoji se siguen viendo:** «❤️», «1️⃣», «#️⃣», «*️⃣», «1⃣», «🇪🇸», «👍🏽», «©️», «‼️», «☎️», «↔️» y «🏳️» salen igual. U+FE0F se queda en lo que se enseña exactamente donde se queda en un nombre de archivo, y «❤️ Banco» y «Café» + U+FE0F comparan igual que sin el selector.
- **Aceptado (R9 y R43):** una secuencia con ZWJ se enseña como sus emoji sueltos («👨👩👧»), y una bandera de etiquetas, como la de Inglaterra, como la bandera negra «🏴». Por eso las banderas de Inglaterra y de Escocia comparan igual: un duplicado de más, y elige el usuario.

### RT-9 [Baja] U+2065 y U+FFF0–U+FFF8 no se dibujan, pero se enseñan y separan claves; los nombres de archivo sí los quitan

**Cómo lo he atacado.** En el archivo, «Banco» con U+2065 o con uno de U+FFF0–U+FFF8 dentro o al final, con el usuario y la URL de la bóveda.

**Lo que pasa:**
- `drawsNothing` (`core/Invisible.kt:32-39`) es la categoría Cf más unos rangos fijos. U+2065 y U+FFF0–U+FFF8 no tienen asignado ningún carácter (Cn), así que no son Cf, y no están en los rangos;
- son Default_Ignorable_Code_Point de Unicode, y HarfBuzz, que usa Android para pintar el texto, los oculta (`is_default_ignorable` cubre U+2060–U+206F y U+FFF0–U+FFF8). Hay que comprobarlo en el móvil (abajo);
- `plainReportText` (`imports/ImportModel.kt:117-144`) los conserva, y `compareKey` (`:173`) también. «Banco» + U+2065 se ve como «Banco» y no es duplicado de «Banco»: la entrada sombra de RT-2 vuelve, con otro carácter;
- la promesa de R43, «display, comparison and file names agree», no se cumple. Los nombres de archivo los quitan (`isHiddenInName`, `crypto/FileCrypto.kt:175-179`), a través de la lista de las tarjetas, que tiene `in 0x2060..0x206F` y `in 0xFFF0..0xFFFB` (`crypto/pk/Card.kt:243-244`). Lo que se enseña no los quita;
- `InvisibleTest.whatIsShownWhatIsComparedAndTheNamesOfFilesAgree` solo comprueba un sentido: lo que está en `drawsNothing` se quita de los nombres. No comprueba que lo invisible que quitan los nombres esté en `drawsNothing`.

**Evidencia:** sin el `@Ignore`, `rt9_unassignedDefaultIgnorablesAreNotShownNorKeptApart` (`:236`) falla con `shown or kept apart: U+2065, U+FFF0, U+FFF1, U+FFF2, U+FFF3, U+FFF4, U+FFF5, U+FFF6, U+FFF7, U+FFF8`. Su segunda mitad, que recorre todo Unicode, falla por sí sola con `dropped from names, shown in lists: U+2065, U+FFF0, …, U+FFF8`: son exactamente esos once, sin contar los controles, el uso privado, los no caracteres, U+2028/U+2029, el ZWJ y U+FE0F, que tienen reglas propias.

**Impacto.** El mismo que RT-2 y RT-6. CVSS 3.1: 3,3 (`AV:L/AC:L/PR:N/UI:R/S:U/C:N/I:L/A:N`). Que no se vea depende de cómo pinte el móvil.

**Arreglo** (en `core/Invisible.kt:32-39`): añadir `cp == 0x2065 || cp in 0xFFF0..0xFFF8`. Mejor aún, basar la lista en Default_Ignorable_Code_Point como lista fija, la misma base que `isForbiddenNameCodePoint`. En `InvisibleTest`, añadir el otro sentido: todo punto de código que `isHiddenInName` quita y que no es control, uso privado ni no carácter tiene que estar en `drawsNothing`. El Inspector de QR (RT-10) lo gana con el mismo cambio.

### RT-13 [Informativa] Caracteres de formato que sí se dibujan se quitan de lo que se enseña

**Cómo lo he atacado.** Un título «Banco ۝1» o «Banco ؀1», con U+06DD (fin de aleya) o U+0600 (signo de número árabe).

**Lo que pasa:** `drawsNothing` incluye toda la categoría Cf (`core/Invisible.kt:33`). Algunos Cf sí se dibujan: los Prepended_Concatenation_Mark (U+0600–U+0605, U+06DD, U+070F, U+0890–U+0891, U+08E2, U+110BD y U+110CD), que Unicode deja fuera de Default_Ignorable_Code_Point porque deben verse. `plainReportText` los quita, así que:
- la lista enseña menos de lo que tiene el título;
- dos títulos que se ven distintos comparan igual: un duplicado de más, y elige el usuario.

**Evidencia:** sin el `@Ignore`, `rt13_formatCharactersThatDrawAreShown` (`:259`) falla con `dropped: U+0600, U+0601, U+0605, U+06DD, U+070F, U+08E2, U+110BD`.

**Impacto.** Informativo. No sirve para hacerse pasar por otra entrada (va en el sentido de enseñar menos), y estos signos solo aparecen en textos árabes, siríacos y kaithi con números.

**Arreglo:** que `drawsNothing` no cuente esos ocho rangos. Lo resuelve también la lista fija de Default_Ignorable_Code_Point que se propone en RT-9. U+FFF9–U+FFFB tampoco son Default_Ignorable, pero conviene seguir quitándolos, porque pueden esconder el texto de una anotación. Con este cambio, hay que repasar `InvisibleTest.whatIsSeenIsNotInTheList`.

### RT-10 [Baja] El Inspector de QR no marca U+2800, U+034F, U+17B4/5, U+180B–U+180F ni otros invisibles

Es el punto abierto (2) de los arreglos. Lo confirmo con un test.

**Cómo lo he atacado.** Un QR de Wi-Fi o de texto con «Casa» seguido de un punto de código que no se dibuja.

**Lo que pasa:** `isHiddenCodePoint` (`tools/qr/Visible.kt:85-97`) tiene su propia lista y no usa `drawsNothing`. Le faltan:
- U+034F, U+17B4, U+17B5, U+180B–U+180D, U+180F y U+2800;
- U+2065, U+206A–U+206F y U+FFF0–U+FFF8;
- U+FFF9–U+FFFB, U+1BCA0–U+1BCA3 y U+1D173–U+1D17A;
- el plano 14 desde U+E0080, salvo los selectores (por ejemplo, U+E0080 y U+E01F0).

`visibleText` los deja sin marca, `hasHiddenChars` da `false` y no salen ni la alerta de caracteres ocultos ni el aviso al copiar (`tools/qr/QrView.kt:115`, `:203` y `:206`). «Casa» + U+2800 se hace pasar por la red «Casa». Al Inspector se va justo para saber si un QR intenta parecer otra cosa.

**Evidencia:** sin el `@Ignore`, `rt10_theQrInspectorMarksEverythingThatDrawsNothing` (`:317`) falla con `unmarked: U+034F, U+17B4, U+17B5, U+180B, U+180C, U+180D, U+180F, U+2800, U+2065, U+206A, U+206F, U+FFF0, U+FFF9, U+FFFA, U+FFFB, U+1BCA0, U+1D173, U+E0080, U+E01F0`. Lo que ya marcaba sigue marcado, y los emoji no se marcan (`resistsTheQrInspectorsOwnList`, `:325`).

**Impacto.** Bajo. CVSS 3.1: 3,3 (`AV:L/AC:L/PR:N/UI:R/S:U/C:N/I:L/A:N`). El Inspector no conecta a ninguna red ni abre nada: el usuario lee el resultado y decide. Una herramienta que dice «no hay caracteres ocultos» cuando sí los hay le lleva a confiar de más.

**Arreglo** (en `tools/qr/Visible.kt:85-97`):
1. después de las dos excepciones de los emoji (U+FE0E y U+FE0F detrás de un emoji, y el *keycap*), añadir `drawsNothing(cp) -> true`;
2. dejar las reglas propias del Inspector: marca el ZWJ y U+2028/U+2029, y no marca `'\n'`;
3. ajustar el KDoc de `visibleText`.

Con RT-9 arreglado, el Inspector gana también U+2065 y U+FFF0–U+FFF8. No cambia ningún texto: solo aparecen más marcas «⟨U+XXXX⟩».

## RT-7 (R44): se sostiene

**Evidencia:** `resistsCompatibilityFormsAroundTheRt7Fix` (`RedTeamRecheckTest.kt:271`) y `resistsNfkcExpansionAndOddInput` (`:297`). Código: `compareKey` (`imports/ImportModel.kt:173-194`).
- **Comparan igual:** «Oﬃce» y «Office», «Ⅻ» y «XII», «Ｂａｎｃｏ», «𝔅anco» y «Banco», «Banco․es» (U+2024) y «Banco.es», «ｶﾞｽ» y «ガス», «ǲenana» y «Dzenana», «ſeguro» y «seguro», «µ» y «μ», el espacio ideográfico y el NBSP como un espacio, y «é» + U+FE0F + acento como «é» + acento.
- **Solo al comparar:** lo que se enseña sigue siendo «Oﬃce», y con «Saltar» la entrada de la bóveda no cambia. Un título y un usuario en ancho completo («Ｂａｎｃｏ» / «ａｎａ») son duplicado, el coste que acepta R44.
- **Sin efectos de más:** el NFKC nunca deja en la clave nada de `drawsNothing`, ni espacios al principio, al final o dos seguidos (U+FDFA, U+2000/U+2001, U+3000 con U+FF0E, U+2028).
- **Coste:** con U+FDFA repetido 1 024 veces (la expansión más larga, 18 puntos de código), la clave queda por debajo de 20 480 caracteres. No lanza nada con U+10FFFF, U+FFFF, U+FDD0, el uso privado, una bandera ni sustitutos sueltos.
- **Lo que NFKC no pliega,** como ya anotó el segundo ataque: la «а» cirílica y otras letras de otras escrituras (aceptadas por R9), U+2010 y U+2212 frente a «-», U+2019 y U+02BC frente a «'», U+01C3 frente a «!». No abro hallazgo: no es un hueco nuevo, y la decisión ya está tomada.

## Barrido de regresión de RT-1 a RT-3 y de lo resistido

- `RedTeamImportTest` (12), `RedTeamCryptoTest` (8) y `RedTeamSecondAttackTest` (13): todos en verde y sin omitidos, también los `rt1_` a `rt7_`.
- Las confusiones de la autoridad de RT-1 se repiten en `resistsSchemeAndAuthorityVariantsAroundTheRt4Fix`: `\`, `%5C`, `#@`, `?@`, `/@`, la información de usuario e IDN, ahora con el esquema en la clave. `urlHost` sigue dando el host al que va el navegador, y la fila lo enseña así.
- La fila del duplicado sigue sin bidi ni invisibles (`resistsDuplicateRowSpoofing`).

## Ataques intentados y resistidos (esta comprobación)

En `RedTeamRecheckTest`:
1. `resistsSchemeAndAuthorityVariantsAroundTheRt4Fix`: 25 URL que son duplicado y 29 que no lo son, y en estas «Reemplazar» no cambia la entrada de la bóveda.
2. `resistsReplacingWithoutAUrl`: un elemento sin URL, o con espacios o un tabulador, conserva la URL de la bóveda.
3. `resistsLeetVariantsAroundTheRt5Fix`: 13 maestras con «¡», «€», «ø» y «Ø» rechazadas, y 4 contraseñas que no son palabras y pasan.
4. `resistsInvisiblesAroundTheRt6Fix`: 34 puntos de código que no se dibujan, los sustitutos sueltos, U+FE0F detrás de ASCII y la torre de marcas.
5. `resistsEmojiThatMustStillShow`: 13 textos con emoji que se enseñan tal cual, con la misma regla del selector que un nombre de archivo, y lo aceptado de ZWJ y de las banderas de etiquetas.
6. `resistsCompatibilityFormsAroundTheRt7Fix`: 13 pares que NFKC pliega, lo guardado no cambia, y las claves no tienen invisibles ni espacios de más.
7. `resistsNfkcExpansionAndOddInput`: la expansión más larga y las entradas raras no lanzan nada y no crecen sin límite.
8. `resistsTheQrInspectorsOwnList`: lo que el Inspector ya marca sigue marcado, y los emoji no se marcan.

## Necesita prueba en el móvil

1. **RT-9:** en la lista de la bóveda, importar «Banco» + U+2065 y «Banco» + U+FFF0 (un CSV de prueba) y mirar si se ven como «Banco». Si el móvil enseña un recuadro, RT-9 baja a informativo, pero las listas siguen sin coincidir.
2. **RT-10:** en el Inspector de QR, un QR de Wi-Fi con la red «Casa» + U+2800, para ver que hoy sale sin marca ni alerta, y después con el arreglo.
3. **RT-13:** un título «Banco ۝1»: el móvil dibuja el signo, y la lista hoy lo quita.
4. **U+FFF9–U+FFFB:** mirar cómo los pinta el móvil. HarfBuzz no los oculta, y `drawsNothing` los quita. Si salen como un recuadro, la lista enseña menos que el título (sin riesgo; solo para saberlo).
5. **RT-8:** con una entrada «Banco» / «ana» sin web, importar un CSV con `http://banco.es/` y ver la fila «ana · banco.es» y el resultado de «Reemplazar».
6. Siguen pendientes las dos de los arreglos del segundo ataque: `http://` frente a `https://` sale como entrada nueva, y «Banco» + U+2800 sale como duplicado.

## Revisión experto-seguridad-cripto

- **Modelo de amenaza:** un archivo importado hostil (decisión (c)), un QR hostil y una persona que elige una maestra nueva débil (decisión (h)). No he tocado ninguna primitiva ni ningún formato: los seis hallazgos son de comparación (`imports/MergePlan.kt:406` y `:477`, `imports/ImportModel.kt:117-194`), de presentación (`core/Invisible.kt:32-39`, `tools/qr/Visible.kt:85-97`) o de política (`vault/MasterPolicy.kt:91-102`). Ningún arreglo propuesto cambia lo que se guarda en `seguridad-vault`, `seguridad-backup`, `SG1:` o `.sgenc`.
- **RT-8** es del mismo tipo que RT-4 y se cierra con una línea en `Matcher.find` (`MergePlan.kt:477`), sin cambiar la fila de R41.
- **RT-9 y RT-13:** la base adecuada es la lista fija de Default_Ignorable_Code_Point (Unicode TR #44, DerivedCoreProperties; UTS #39 la usa para lo que no se ve). La categoría Cf sola se queda corta (Cn) y se pasa (Prepended_Concatenation_Mark). `isForbiddenNameCodePoint` (`crypto/pk/Card.kt:237-250`) ya usa esa base. Ponerla en `drawsNothing` cumple R43 en los dos sentidos.
- **RT-11:** sigue siendo una estimación (CRY-A01; NIST SP 800-63B, en los verificadores de contraseñas, pide comparar con listas de contraseñas conocidas, no adivinar reglas). Ampliar el *leet* con símbolos que están en el Generador obliga a medir otra vez su tasa de rechazo.
- **Sin logs ni secretos:** los tests usan contraseñas inventadas y no escriben nada fuera del árbol de tests.

## Revisión Ingeniero Móvil Senior

- **MASVS:** RT-8 y RT-12 son MASVS-CODE-4 (validación de lo importado). RT-9, RT-10 y RT-13 son MASVS-CODE-4 y MASVS-PLATFORM-3 (lo que la interfaz enseña). RT-11 es MASVS-AUTH-1.
- **Android:** que U+2065 y U+FFF0–U+FFF8 no se vean depende de HarfBuzz, dentro de Minikin. Los oculta como *default ignorables* aunque la fuente no los tenga. Por eso RT-9 y RT-10 llevan prueba en el móvil, y la severidad se queda en Baja.
- **Kotlin puro:** todos los arreglos propuestos son Kotlin sin Android (`core/`, `imports/`, `tools/qr/`, `vault/`) y se prueban en `jvm-check`. No cambia ninguna pantalla de Compose. RT-10 solo hace que el Inspector enseñe más marcas «⟨U+XXXX⟩», que ya se generan en el código.
- **Rendimiento:** `drawsNothing` con rangos fijos sigue costando O(1) por punto de código. El test de todo Unicode de RT-9 tarda menos de un segundo en `jvm-check`.
- **El test nuevo:** `internal` se usa solo desde el mismo módulo (`drawsNothing` e `isHiddenInName`). No hay avisos `w:` y el archivo no usa ninguna API obsoleta.

## Textos para el Abogado Digital (revisión final)

- **Ninguno si RT-8 se arregla con la opción 1** (en `Matcher.find`). Si se elige la opción 2, cambia la línea del duplicado («ana · javascript://banco.es», «ana · http://banco.es»), y ese texto va a su lista.
- **RT-10** no cambia textos: el Inspector enseña más marcas con el formato de siempre, «⟨U+XXXX⟩», y la alerta y el aviso al copiar son los mismos.
- **RT-11:** si el dueño amplía la regla de la maestra y la política, el README o la ficha la describen (R36), que digan lo mismo que el código o no entren en ese detalle. `MASTER_TOO_WEAK` no cambia.
- **Comportamiento que puede describirse:** si el README («Importar de otra app») explica cuándo un elemento es duplicado, con RT-8 arreglado una URL `http` o de otro esquema ya no es duplicado de una entrada sin web.

## Tercera comprobación (db42d3e, ce6b0f9)

**Qué es.** La decisión (f) otra vez: el equipo rojo vuelve a atacar los arreglos de RT-8, RT-9, RT-10, RT-12 y RT-13, con las decisiones R45 a R49, R51 y R52 (`progress.md`). RT-11 pasó a la lista del dueño para la 1.4.1 (R50), así que aquí no se mira. Sigo sin cambiar código de producción y sin hacer commits.

Los tests nuevos están en `RedTeamRecheckTest.kt`, a partir de `:318`. Rutas de Kotlin relativas a `app/src/main/java/app/seguridad/`.

### Resumen de la tercera comprobación

| RT | Decisión | Veredicto |
|---|---|---|
| RT-8 | R48 (con R45) | **No se sostiene.** Con las formas normales de escribir https sí aguanta. Pero un tabulador o un salto de línea dentro del esquema, o un control C0 delante, seguidos de una «@», saltan R48 y también R41 y R45: es **RT-14** (Baja). |
| RT-9 | R49 | **Se sostiene.** La lista coincide con los datos de Unicode 15.0, 16.0 y 17.0, y con todo lo que el JDK llama Cf. Los sustitutos sueltos se quitan. |
| RT-10 | R46 | **Se sostiene.** El Inspector marca cada punto de `drawsNothing`, solo o detrás de un emoji, y sus excepciones de emoji no dejan pasar nada más. |
| RT-12 | R51 | **Se sostiene.** Sin host, solo se pliega el esquema; lo de detrás se compara exacto y la fila lo enseña tal como está escrito. |
| RT-13 | R49 y R52 | **Se sostiene.** Los trece signos que preceden a una cifra se ven en las listas, separan claves y el Inspector no los marca. Los nombres de archivo los siguen quitando. |
| R45 (fila) | — | **Se sostiene** con todas las formas de información de usuario, salvo la vía de RT-14. |

Recuento: **1 hallazgo nuevo, RT-14, de severidad baja**. No hay ninguno crítico, alto, medio ni informativo nuevo.

`RedTeamRecheckTest` tiene ahora 20 tests: 19 pasan y uno, `rt14_`, lleva `@Ignore`. Lo comprobé sin el `@Ignore` y falla. Las otras clases del equipo rojo siguen en verde, sin omitidos: `RedTeamImportTest` 12, `RedTeamCryptoTest` 8 y `RedTeamSecondAttackTest` 13.

Lo ejecuté con `./gradlew -p jvm-check test --tests 'app.seguridad.redteam.*' --no-daemon --max-workers=1`: **BUILD SUCCESSFUL**, 0 fallos y ningún aviso `w:`.

### RT-14 [Baja] Un tabulador o un control en el esquema esconde `http:` o `javascript:`: duplicado de banco.es

**Cómo lo he atacado.** En la bóveda está «Banco» / «ana», una vez con `https://banco.es/` y otra sin URL. En un CSV de navegador, la misma fila trae en la columna `url` uno de estos valores:
- `ht<TAB>tp:@banco.es`;
- `<U+0001>http:@banco.es`;
- `HT<LF>TP:x@banco.es/login`;
- `http<TAB>:@banco.es`;
- `java<TAB>script:alert(1)<!--@banco.es`;
- `<U+0001>javascript:alert(1)<!--@banco.es`.

El lector de CSV los guarda tal cual: el test lo comprueba con `readImport`.

**Lo que pasa:**
- `urlHost`, `urlKey` y `shownUrl` (`imports/MergePlan.kt:389`, `:406` y `:445`) dan a `comparedLink` (`:429`) el texto sin limpiar;
- su `schemeOf` (`tools/qr/LinkSignals.kt:119`) no ve un esquema con un tabulador o un salto de línea dentro, ni con un control delante, así que pone `https://` delante;
- `parseLink` sí quita los tabuladores y los saltos de línea y recorta los controles, como hace el navegador (`LinkSignals.kt:63`). Pero entonces ya va detrás de `https://`, y el esquema que leería el navegador queda como el principio de la autoridad;
- la «@» manda el host a banco.es. La clave es «hhttps://banco.es» y la fila, «ana · banco.es»;
- un navegador hace al revés: primero quita los tabuladores y los controles, y después lee `http://banco.es/`, o un `javascript:` cuyo «<!--» abre un comentario (ECMAScript, anexo B, en un script clásico).

Así se saltan tres decisiones a la vez:
- **R41 (RT-4):** es duplicado de la entrada `https`;
- **R48 (RT-8):** es duplicado de la entrada sin URL;
- **R45:** la fila no enseña el esquema.

Con «Reemplazar», la entrada guarda ese texto. Además, `ht<TAB>tps://banco.es`, que es https, sale en la fila como «ana · https», con un host que no es el de verdad.

Corrige lo que dije en el primer repaso («del lado seguro: `java\tscript://…`, `ht\ttps://…`, `\u0001https://…`»): es verdad solo mientras no haya una «@» detrás.

**Evidencia:** `rt14_aTabOrALeadingControlDoesNotHideTheScheme` (`RedTeamRecheckTest.kt:336`). Sin el `@Ignore` falla con: `duplicates whose row hides what the browser opens: «ht\ttp:@banco.es» vault «https://banco.es/»: row «ana · banco.es»; «ht\ttp:@banco.es» vault «»: row «ana · banco.es»; «\u0001http:@banco.es» … ; «java\tscript:alert(1)<!--@banco.es» vault «https://banco.es/»: row «ana · banco.es»; … ; «ht\ttps://banco.es» vault «»: row «ana · https»`.

El test comprueba antes, con `parseLink`, que el navegador lee esas URL como `http`, `javascript` o `https`.

**Impacto.** Bajo, como RT-4 y RT-8. CVSS 3.1: 2,5 (`AV:L/AC:H/PR:N/UI:R/S:U/C:N/I:L/A:N`).
- Hace falta que el usuario importe el archivo del atacante y elija «Reemplazar».
- Clavero no abre ningún enlace (decisión (c)).
- Para llegar a `http://` o a `javascript:`, el usuario tiene que copiar la URL y abrirla en un navegador que quite el tabulador o el control al pegar. No sé si la barra de Chrome en Android lo hace o si busca el texto: queda para el móvil.
- La contraseña de antes queda en el historial.

**Arreglo:** que la comparación y la fila lean el texto como lo lee el navegador antes de buscar el esquema:
1. sacar la primera línea de `parseLink` a una función compartida en `tools/qr/LinkSignals.kt`, por ejemplo:
   ```kotlin
   internal fun asBrowserReadsIt(s: String) = s.filterNot { it == '\t' || it == '\n' || it == '\r' }.trim { it <= ' ' || it.isWhitespace() }
   ```
2. usarla en `urlHost`, `urlKey` y `shownUrl` en lugar de `url.trim()`, y en la clave «t» de R51.

Entonces `ht<TAB>tp:@banco.es` es `http:@banco.es`: la clave es «hhttp://banco.es», no es duplicado y la fila dice «ana · http://banco.es». `java<TAB>script:…` es una clave «t» y no es duplicado de nada web.

**Ojo:** con el arreglo, dos URL de `resistsSchemeAndAuthorityVariantsAroundTheRt4Fix` que yo puse entre las que no son duplicado, `ht\ttps://banco.es` y `\u0001https://banco.es`, pasan a serlo. Es correcto, porque el navegador abre https en banco.es: hay que moverlas a la lista de duplicados.

**Test que lo dará por arreglado:** `rt14_…`, sin el `@Ignore`. Acepta que no haya duplicado o que la fila diga lo que abre el navegador.

### Ataques de la tercera comprobación intentados y resistidos

Todos en `RedTeamRecheckTest`:
1. **R48, con todas las formas de https frente a una entrada sin URL** (`resistsTheRt8FixVariants`, `:361`):
   - son duplicado, con la fila «ana · banco.es», y «Reemplazar» trae su URL: `HTTPS:banco.es`, `https:/banco.es`, `https:\\banco.es`, `https://ana:clave@banco.es/`, `banco.es`, `//banco.es/x`, con `www.`, puerto, consulta y fragmento, y con espacios alrededor;
   - entran como entradas nuevas y la entrada sin URL no cambia: `http`, `HTTP:`, `http:\\`, `ftp`, `ws`, `wss`, `javascript:x@`, `javascript://`, `mailto:`, `https://`, `intent://`, `ana:clave@banco.es`, `data:` y `file:///`;
   - con la entrada `https` y otra sin URL, `http` no va a ninguna de las dos.
2. **R45, la información de usuario en la fila** (`resistsTheRowShowingUserInfo`, `:388`). Ninguna de 21 formas enseña «clave»:
   - con host: `a@clave@host` (cuenta la última «@»), `%40`, `:clave@`, `clave:@`, todo en mayúsculas con puerto, `https:\\`, `//`, `http`, `ftp`, `sftp` con «\» y `javascript://`;
   - sin host: `ana:`, `ana:1234@`, `mailto:`, `MAILTO:a@clave@…?cc=x`, `https://clave@`, `https://ana:clave@/x`, y un U+200B o un U+202E dentro de la información de usuario;
   - con una sola letra y dos puntos (`x:clave@banco.es`): no es un esquema (sería la letra de una unidad), se lee como https y la fila dice «ana · banco.es».
3. **R51** (`resistsTheRt12FixVariants`, `:408`):
   - son duplicado de lo mismo: `DATA:`, `Data:` y espacios alrededor;
   - no lo son: otro Base64, `TEXT/plain` en mayúsculas, un carácter de menos o un control delante;
   - la fila enseña el Base64 tal como está escrito.
4. **R49, frente a los datos de Unicode** (`resistsTheRt9FixAgainstTheUnicodeData`, `:425`):
   - descargué `DerivedCoreProperties.txt` y `extracted/DerivedGeneralCategory.txt` de 15.0.0, 16.0.0 y **17.0.0**: los rangos de Default_Ignorable_Code_Point y de Cf son idénticos en las tres;
   - el test fija esos rangos: todo Default_Ignorable está en `drawsNothing`, y todo Cf de Unicode 17.0 está en `drawsNothing` o es uno de los trece signos (y no los dos a la vez);
   - recorre todo el Unicode del JDK 21 (15.0): ningún Cf queda fuera de las dos listas, y lo que `drawsNothing` añade a Default_Ignorable es exactamente U+2800, U+FFF9–U+FFFB y U+13430–U+1343F;
   - los sustitutos U+D800–U+DFFF no están en la lista, y lo que se enseña y lo que se compara los quitan sueltos. Un nombre de archivo escribe «?», como siempre ha escrito UTF-8.

   Con la lista fija, un punto de código que añada una versión futura de Unicode y no se dibuje necesita que alguien lo añada a los datos; en 17.0 no hay ninguno.
5. **Los trece signos, en los nombres y en lo que se enseña** (`resistsTheThirteenMarksInNamesAndDisplay`, `:449`): con cada uno de ellos, «Banco ؀12» se enseña tal cual y no compara igual que «Banco 12»; «factura ؀12.pdf» se guarda como «factura 12.pdf» (R52); el Inspector no los marca.
6. **R46, el Inspector** (`resistsTheRt10FixWithEveryPointAndTheEmojiExceptions`, `:463`):
   - en todo Unicode, cada punto de `drawsNothing` detrás de «a» sale como una sola marca «⟨U+XXXX⟩», y detrás de «❤» también se marca (salvo U+FE0E y U+FE0F);
   - no se marcan solo las excepciones: «❤️», «❤︎», «©️», «😀️» y los *keycaps* «1️⃣» y «#️⃣»;
   - sí se marcan U+FE0F detrás de ASCII o de una letra CJK, «1» + U+FE0E + U+20E3, «1» + U+FE0F suelto, dos U+FE0F seguidos, U+FE00 o U+E0100 detrás de un emoji, U+FE0F al principio, y U+2800 + U+FE0F;
   - en un host cuenta cualquier selector.
7. **Sin regresión:** los 13 tests del primer repaso de esta clase y las otras tres clases del equipo rojo siguen en verde.

### Necesita prueba en el móvil (tercera comprobación)

1. **RT-14:** copiar de Clavero una URL `ht<TAB>tp:@banco.es` y otra `<U+0001>http:@banco.es`, pegarlas en Chrome y en el navegador del fabricante, y apuntar qué abren: `http://banco.es/`, una búsqueda o nada. Eso decide si el impacto es real o solo de presentación.
2. Siguen las de los arreglos anteriores (P-9 y las de RT-9, RT-10 y RT-13, en el informe del ataque y en el README).

### Revisión experto-seguridad-cripto (tercera comprobación)

- **RT-14** es un desajuste entre el analizador y el navegador (CWE-436, *interpretation conflict*). `parseLink` sigue el URL Standard, que primero quita los tabuladores y los saltos de línea y recorta los controles C0 y los espacios, y después lee el esquema. `comparedLink` lee el esquema antes de hacerlo. Al aplicar la misma limpieza antes de `schemeOf`, `urlKey`, `urlHost` y `shownUrl` leen igual que el navegador. No toca primitivas ni formatos: lo guardado sigue siendo el texto tal como llegó (R9 y R10).
- **R49:** comparé los datos con Unicode 17.0 (la última versión publicada). La lista fija es la base correcta (UAX #44; UTS #39 la usa para lo que no se ve), y deja de depender de la ICU de cada móvil.
- **R45:** con host, la fila solo escribe el host. Sin host, tapa hasta la última «@» antes de «/», «?» o «#». No he encontrado ninguna forma que enseñe la información de usuario.
- **Sin logs ni secretos:** los tests usan contraseñas inventadas («clave», «la-del-atacante») y no escriben nada fuera del árbol de tests.

### Revisión Ingeniero Móvil Senior (tercera comprobación)

- **MASVS:** RT-14 es MASVS-CODE-4, por la validación de lo importado; lo que enseña la fila es MASVS-PLATFORM-3.
- **El arreglo propuesto** es Kotlin puro en `imports/` y `tools/qr/`, y se prueba en `jvm-check`. No toca Compose, el estado ni la concurrencia: `planMerge` sigue en `Dispatchers.Default`.
- **Coste:** la limpieza es una pasada más por cada URL comparada, igual que la que ya hace `parseLink`.
- **Los tests de todo Unicode** que añado (`:425` y `:463`) tardan menos de un segundo cada uno en `jvm-check`.
- **El archivo de test:** ningún aviso `w:`, y `internal` solo se usa dentro del módulo (`drawsNothing`, `VISIBLE_PREPENDED_MARKS`, `isHiddenInName`, `parseLink`).

### Textos para el Abogado Digital (tercera comprobación)

- **Ningún texto nuevo.** Con RT-14 arreglado, la fila de una URL como `ht<TAB>tp:@banco.es` dirá «ana · http://banco.es», que es la forma que ya fija R45 (`task-12-report.md`, punto 12). El README (P-9) no necesita cambiar.

## Cuarta comprobación (f6148bb)

**Qué es.** La decisión (f) otra vez, sobre `f6148bb`: los arreglos de RT-14 (R54) y del I-1 de la revisión (R53). Hay tres cambios:
- **El paso previo común** `asBrowserReadsIt` (`tools/qr/LinkSignals.kt:62-63`). Lo usan `parseLink`, `urlHost`, `urlKey`, la clave de R51 y `shownUrl` (`imports/MergePlan.kt:393`, `:411` y `:456`).
- **La regla de R53:** «palabra:cifras» es host y puerto solo si la palabra lleva «.» o es `localhost` (`imports/MergePlan.kt:439`).
- **La fila,** que oculta hasta la última «@» (`:463`).

Sigo sin cambiar código de producción y sin hacer commits. Los tests nuevos están en `RedTeamRecheckTest.kt`, a partir de `:485`.

### Veredictos

| RT / decisión | Veredicto |
|---|---|
| **RT-14 (R54)** | **Se sostiene.** El paso previo hace lo mismo que el URL Standard antes del estado del esquema: recorta los controles C0 y los espacios de los dos extremos y quita todo tabulador, LF y CR. Una URL que el navegador lee como `http:` o `javascript:` nunca coincide con `https`, ni con una entrada sin URL, y la fila dice el esquema. |
| **RT-8 (R48)** | **Se sostiene.** Con el paso previo, la vía de RT-14 está cerrada. Una entrada sin URL solo toma lo que el navegador abre como https en banco.es, o lo que no tiene esquema. |
| **R53** | **Se sostiene.** `localhost`, `localhost.`, una palabra con «.», IPv4 en hexadecimal o corto, e IPv6 entre corchetes se leen como host. Cualquier otra palabra es un esquema, también delante de cifras (`javascript:1/alert(1)`, `data:1/x`). |
| **R45 con N-1 (la fila)** | **Se sostiene.** La información de usuario no sale con «/», «?» o «#» delante de la «@», ni con tabuladores o controles. |
| **`parseLink` y el Inspector de QR** | **Sin cambios de comportamiento.** El paso previo es la misma expresión que tenía `parseLink` en `50b0d51`, no cambia si se aplica dos veces, y `parseLink` lee igual un texto y su forma ya limpia. |

**Hallazgos nuevos: ninguno.** `RedTeamRecheckTest` tiene ahora 26 tests y pasan los 26. No queda ningún `@Ignore`: `rt14_` ya pasa sin él desde `f6148bb`.

Lo ejecuté con `./gradlew -p jvm-check test --tests 'app.seguridad.redteam.*' --no-daemon --max-workers=1`: **BUILD SUCCESSFUL**, 0 fallos, 0 omitidos y ningún aviso `w:`. Las otras clases del equipo rojo siguen igual: `RedTeamImportTest` 12, `RedTeamCryptoTest` 8 y `RedTeamSecondAttackTest` 13.

### Ataques intentados y resistidos (cuarta comprobación)

1. **El paso previo frente al URL Standard** (`resistsTheRt14FixAgainstThePrePass`, `:498`):
   - cada control C0 y el espacio (U+0000 a U+0020) delante y detrás de `http:@banco.es`;
   - un tabulador, un LF o un CR en cada posición de `http:`;
   - mayúsculas y minúsculas mezcladas (`hTtP\t:`, `HtTp:\t/\n/x@`) y `h\tt\nt\rp:\\\\@banco.es`.

   Todos dan la clave «hhttp://banco.es», no son duplicado de la entrada `https` ni de la que no tiene URL, y la fila dice «ana · http://banco.es». Lo mismo con `javascript:` escrito con tabuladores, saltos de línea, un NUL delante o en mayúsculas: la clave es «tjavascript:…» y la fila, «javascript:…@banco.es».
2. **Lo que el URL Standard no toma como esquema** (`resistsWhatTheUrlStandardDoesNotTakeAsAScheme`, `:524`): un NBSP, un U+3000, un U+200B, un U+00AD o un control C0 dentro del esquema, NUL antes de «:», los dos puntos de ancho completo «：» y `ｈｔｔｐ` en ancho completo. El analizador del navegador no encuentra esquema en ninguno (`parseLink(u).scheme` vacío), así que no los abre como `http` ni como `javascript`. La fusión los lee como texto sin esquema, es decir, https, y su clave nunca es `hhttp://` ni `tjavascript:`. Qué hace la barra de direcciones al pegarlos queda para el móvil (abajo).
3. **R53** (`resistsTheR53HostAndPortRule`, `:536`):
   - se leen como host: `localhost:8080`, `LOCALHOST:80`, `localhost.:8080`, `banco.es:8443`, `Banco.ES:443/x`, `a.b:1/`, `0x7f.1:80` y `127.1:80` (los dos son 127.0.0.1) y `[::1]:8080`;
   - se leen como esquema: `javascript:1/alert(1)`, `data:1/x`, `intranet:8080` (el coste que acepta R53), `JavaScript:1` y `localhostx:80`;
   - `javascript.:1/alert(1)` se lee como el host «javascript». El navegador lo lee como el esquema «javascript.», que no es `javascript`;
   - `com.evil.app:1/@banco.es` tiene el host com.evil.app, no banco.es, y `com.evil.app:1@banco.es` es una clave «t»;
   - IPv6 con zona (`[fe80::1%25eth0]:80`) nunca es banco.es;
   - ninguno es duplicado de banco.es ni de una entrada sin URL.
4. **Propiedad sobre 300 000 URL al azar** (`resistsAPropertyOverRandomLinks`, `:566`), con un alfabeto de los caracteres que importan: letras de esquema, «:», «/», «\», «@», «.», cifras, tabulador, LF, CR, controles, espacios, NBSP, U+3000, U+200B, «：», «%», «#», «?», «[», «]», «<!--», `http`, `https`, `javascript`, `banco.es`, `www.` y `localhost`. Cada vez que la fusión lee una URL como https en banco.es (más de 2 000 veces en la tanda), se cumple que:
   - el navegador también la lee como https en banco.es, o no le encuentra esquema, o es una palabra con «.» delante de un puerto;
   - la fila dice «ana · banco.es».
5. **La fila con la última «@»** (`resistsTheRowWithTheLastAtRule`, `:594`):
   - «/», «?» o «#» dentro de la contraseña;
   - `mailto:a@b.es?cc=clave@d.es`, que da «mailto:…@d.es»;
   - tabuladores dentro de `mailto` y de la contraseña, y un control o un NUL delante;
   - `javascript:alert('clave')//@banco.es` y `data:clave@`.

   Ninguna enseña «clave».
6. **`parseLink` no cambia** (`resistsAChangeInParseLink`, `:610`): con 50 000 textos al azar de tabuladores, controles, espacios Unicode, U+2028 y U+200B, `asBrowserReadsIt` da lo mismo que la expresión de `50b0d51`, aplicarlo dos veces da lo mismo, y `parseLink(u)` coincide con `parseLink(asBrowserReadsIt(u))` en esquema, host, puerto, usuario, IP y host oculto. El Inspector de QR usa `parseLink` y `hasHiddenChars`, que no cambian, y `resistsTheQrInspectorsOwnList` y `resistsTheRt10FixWithEveryPointAndTheEmojiExceptions` siguen en verde.
7. **Los anteriores** siguen en verde: `rt14_` sin `@Ignore`, y `resistsSchemeAndAuthorityVariantsAroundTheRt4Fix` con `ht\ttps://banco.es` y `\u0001https://banco.es` ya entre los duplicados (R54).

**Diferencias con el URL Standard, aceptadas y del lado seguro:**
- el paso previo también recorta los espacios Unicode de los extremos (NBSP, U+3000), como una barra de direcciones. El navegador no los recorta y entonces no encuentra esquema, así que la fusión no lee nunca un esquema que el navegador no lea;
- una sola letra delante de «:» no es esquema para Clavero (la letra de una unidad): `x:@banco.es` se lee como https. El navegador lo lee como el esquema «x», que no abre nada.

### Necesita prueba en el móvil (cuarta comprobación)

1. **Pegar en la barra.** Pegar en Chrome y en el navegador del fabricante, copiadas de Clavero, `ht<NBSP>tp:@banco.es`, `ht<U+0001>tp:@banco.es`, `http：@banco.es` (los dos puntos de ancho completo) y `ht<U+200B>tp:@banco.es`. El analizador del navegador no ve esquema en ellas y la fusión las trata como https en banco.es. Si alguna barra las «arregla» al pegar y abre `http://banco.es`, se abre un hallazgo nuevo, informativo o bajo: la fusión tendría que comparar entero un texto con esos caracteres antes de los dos puntos.
2. Siguen las de las rondas anteriores (RT-14 con tabulador y control, P-9 y P-27 del README).

### Revisión experto-seguridad-cripto (cuarta comprobación)

- **R54** cierra la clase de RT-14 donde nacía: hay una sola lectura del texto, la del URL Standard (pasos de recorte y de quitar tabuladores y saltos de línea del analizador básico), antes de `schemeOf` (`tools/qr/LinkSignals.kt:62-63`, `imports/MergePlan.kt:393`, `:411` y `:456`). Ya no hay una lectura para la clave y otra para el navegador (CWE-436). La propiedad sobre 300 000 URL lo fija para lo que importa: la fusión no lee https en banco.es donde el navegador lee otro esquema.
- **R53** (`imports/MergePlan.kt:439`) quita la lectura de host y puerto a cualquier palabra sin «.» que no sea `localhost`, así que `javascript:1/…` ya no puede pasar por web.
- **La fila** (`:463`) oculta de más antes que de menos: hasta la última «@» del texto.
- **Nada toca primitivas, formatos ni lo guardado** (R9 y R10). Sin logs ni secretos: los tests usan textos inventados («clave»).

### Revisión Ingeniero Móvil Senior (cuarta comprobación)

- **MASVS-CODE-4** (validación de lo importado) y **MASVS-PLATFORM-3** (la fila). Es Kotlin puro en `imports/` y `tools/qr/`, probado en `jvm-check`. No cambia Compose, el estado ni la concurrencia.
- **Coste:** el paso previo es una pasada más por URL comparada; antes `parseLink` ya la hacía. Los tests de propiedad tardan menos de un segundo (`resistsAPropertyOverRandomLinks`, 0,7 s en este contenedor).
- **El archivo de test:** ningún aviso `w:`, con semillas fijas de `SplittableRandom` para que cada ejecución sea igual. `internal` solo se usa dentro del módulo (`urlKey`, `asBrowserReadsIt` y `parseLink`).

### Textos para el Abogado Digital (cuarta comprobación)

Ninguno. R53 y R54 no cambian ningún texto. La fila sigue la forma de R45, y ahora oculta más («mailto:…@d.es»): eso ya está apuntado en `task-12-report.md`.
