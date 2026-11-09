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
