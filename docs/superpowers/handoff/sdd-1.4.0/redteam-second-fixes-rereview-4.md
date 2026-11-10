# Repaso acotado 4 de `2eaf393`: N-6, la fila del Info, RT-15, RT-16 y RT-17 (R57 a R60)

**Alcance:** repaso acotado de `797ff1f..2eaf393`, con el paquete `.superpowers/sdd/2026-11-03-clavero-1.4.0/review-797ff1f..2eaf393.diff`.
- **Decisiones que obligan:** R57 a R60 (`progress.md:288-292`).
- **Afirmaciones que verifico:** las de «Ronda 5 (R57 a R60)» en `redteam-recheck-fixes-report.md`, con su tabla campo por campo, fila a fila.

Solo lectura y sin subagentes. No volví a ejecutar la suite; la puerta que se informa es app 1886 / jvm-check 1881, 0 fallos, 0 omitidos, 0 `w:`.

Para las dudas concretas hice una prueba de punta a punta (`planMerge`, la fila y «Reemplazar»):
- antes comprobé que `app/src/main` del árbol es `2eaf393` y que las clases compiladas de `jvm-check` traen ya el código de R58 y R60 (`javap -c`: `replaceWith` llama a `titleKey` e `importTags` llama a `plainReportText`);
- la prueba corrió sobre una **copia nueva** de esas clases, en el *scratchpad* de la sesión, sin Gradle.

Era la ronda 5 de 5. Lo que queda está clasificado al final con precisión, para que lo decida el controlador.

Rutas de Kotlin relativas a `app/src/main/java/app/seguridad/`, las de los tests a `app/src/test/java/app/seguridad/`. Líneas de `2eaf393`.

### Spec Compliance

| Punto | Veredicto | Evidencia |
|---|---|---|
| **N-6** (R57) | ✅ Arreglado | `imports/MergePlan.kt:281`: el emisor y la cuenta usan `showsNothing`. Lo fija `MergePlanTest.replaceTakesNoFieldThatShowsNothing` con U+0001, U+200B, U+2800, U+3164 y « \t» |
| **La fila del Info** (R57) | ✅ Arreglado | `:518`: `filterNot { showsNothing(it) }` antes de unir, así que no queda « · » colgando (`theRowHasNoSeparatorBeforeWhatShowsNothing`) |
| **RT-15** (R58) | ✅ Arreglado | `:275-276`: el título y el usuario se quedan los de la bóveda si `titleKey` o `userKey` coinciden, o si los del archivo no enseñan nada. Probado: «Ban⠀co» / «ana​» contra «Banco» / «ana» da un duplicado, y tras «Reemplazar» quedan el título «Banco», el usuario «ana» y la contraseña «otra». Un acceso y una nota solo son duplicado con esas claves iguales, así que su título y su usuario no cambian nunca. Un 2FA (que se empareja por el secreto) trae el título del archivo si es otro y se ve (`replaceKeepsATitleAndUserWhoseKeysMatch`). `rt15_` ya no lleva `@Ignore` |
| **RT-16** (R59) | ✅ Arreglado | Las notas, en `:264`: `showsNothing(incoming.notes)` → se quedan las de la bóveda, sin la línea «Importado el …». El emisor y la cuenta, en `:281`. `rt16_` ya no lleva `@Ignore`. La contraseña es la excepción documentada (decisión pendiente D-2, abajo) |
| **RT-17** (R60) | ✅ Arreglado, con N-9 | `importTags`, `:153-163`: la carpeta pasa por `plainReportText` antes de `normalizeTags`. Probado: «Banco​» da «banco», y U+200B, U+2800, U+3164, U+0001 y «­﻿» no dan etiqueta (`aFolderIsReadAsItShows`). `rt17_` ya no lleva `@Ignore`. Un NBSP dentro de la carpeta sigue dando una etiqueta que se ve igual que otra: N-9 |
| **I-2, otra vez** | ✅ Se sostiene | `\u0001`, `\u0000\u001F` y `\t\u0001\n` son duplicado con la fila «ana», y la web de la bóveda sigue siendo `https://banco.es/` |
| **«Reemplazar» nunca escribe lo que no enseña nada encima de lo que sí** | ✅ en título, usuario, URL, notas, emisor, cuenta y etiquetas | Título y usuario (`:275-276`); URL (`isNoUrl`, `:277`; una URL que no está vacía pero no enseña nada, como `​`, da una clave «t», que solo casa con el mismo texto); notas (`:264`; solo se añade, nunca se sustituye lo que había salvo unas notas en blanco); emisor y cuenta (`:281`); etiquetas (`:285`, solo se añaden; las del archivo vienen de `importTags`, que ya no deja nada que no se vea). La **contraseña** (`:288`, `isEmpty`) es la excepción documentada (D-2) |
| **El título y el usuario se conservan cuando las claves coinciden** | ✅ | Arriba (RT-15) |
| **R60 con las etiquetas de la bóveda, `MAX_TAGS` y el cupo `IMPORT_MAX_NEW_TAGS`** | ✅ | `TagBudget.tagsFor` (`:136-143`) cuenta sobre `importTags`, es decir, después de limpiar la carpeta: «Banco​» da «banco», que ya está en `had` y no gasta cupo (antes gastaba uno). `lost` (`:222` y `:229`) usa la misma `importTags`. `replaceWith` une las etiquetas sin perder las propias, con un máximo de `maxOf(MAX_TAGS, existing.tags.size)` (`:285`, sin cambios). `aFolderIsReadAsItShows` lo comprueba de punta a punta: la entrada queda con `[banco, importado-navegador]` |
| **El test de propiedad es real** | ✅ | `MergePlanTest.replaceNeverWritesWhatShowsNothing` hace 20 000 archivos de dos elementos (un acceso y un 2FA con el secreto de la bóveda) con título, usuario, notas, carpeta, emisor y cuenta al azar, y para cada uno `planMerge` y `mergedEntries(REPLACE)`. Comprueba que toda etiqueta enseña algo; que ningún título, usuario, URL, notas, emisor o cuenta pasa de enseñar algo a no enseñar nada; que las notas solo crecen al final y con algo que se ve; y que el título y el usuario de un acceso duplicado no cambian. No varía ni la contraseña ni el secreto del 2FA de un acceso |
| **La tabla campo por campo del informe** | ⚠️ Dos filas inexactas (N-8) | Repaso fila a fila, abajo |
| **Documentos** | ✅ (con D-1) | README P-9 (`README.md:348`) y P-27 (`:366`), `.maestro/README.md:88`, `ataque-1.4.0.md:11` y `:329-349` («Quinta comprobación y tercer repaso») y `task-12-report.md:201`, punto 18, dicen lo que hace el código. P-9 dice «su título y su usuario se quedan como los tenía la bóveda (son los que la hacen repetida…)», y en un 2FA no son los que la hacen repetida (Info, abajo) |
| **Palabras prohibidas; correo del dueño; enlaces de sesión** | ✅ | Nada en las líneas añadidas de `app/src/main`, README, `.maestro`, `ataque-1.4.0.md`, `task-12-report.md` ni en el informe de trabajo |

**La tabla del informe** (`redteam-recheck-fixes-report.md:209-221`), fila a fila, contra el código:

| Fila | ¿Es verdad? |
|---|---|
| Título | ✅ `titleKey` en `Matcher` para el acceso y la nota (`:554` y `:560`); vacío = `showsNothing`; igual = `titleKey` (`:275`) |
| Usuario | ✅ `userKey` (`:554`); `showsNothing`; `userKey` (`:276`) |
| URL | ⚠️ «Vacío = `isNoUrl`» es verdad. «Solo llega aquí con la misma clave https» no lo es: con cualquier clave con host igual (`hhttp://foro.es`, `hjavascript://…`) «Reemplazar» trae la URL del archivo, y también cuando el archivo no tiene URL (por `g.any`, y entonces se queda la de la bóveda). Lo que es cierto es «con la misma clave (con host, de cualquier esquema, o de texto), o sin URL en la bóveda y https en el archivo (R48)». N-8 |
| Contraseña | ✅ Describe el código (`isEmpty`, `withNewPassword`, `:288`), y declara la excepción a R59: D-2 |
| Notas de un acceso o un 2FA | ✅ No se comparan; vacío = `showsNothing` (`:264`); igual = `trim()` o «terminan con ellas». Lo de «lo mismo»: Info abajo |
| Notas de una nota | ✅ `notes.trim()` (`:560`); un duplicado tiene las mismas notas recortadas, así que no añade nada |
| Secreto del 2FA | ⚠️ Para una entrada `TOTP` es verdad. Para un **acceso que lleva un 2FA** (el `login.totp` de Bitwarden, `imports/BitwardenJson.kt:122`) no: el acceso es duplicado por título, usuario y URL, el secreto no se compara, y `replaceWith` (`:279-283`) pone el del archivo. Probado: el secreto `JBSWY3DPEHPK3PXP` de la bóveda pasa a `GEZDGNBVGY3TQOJQ`, con la fila «ana · banco.es», y no queda copia del anterior (D-3) |
| Emisor y cuenta | ✅ `:281` |
| Etiquetas | ✅ `importTags`, `:285` y `TagBudget` |
| Favorito, adjuntos, `createdAt`, historial | ✅ `existing.copy`. El historial de contraseñas crece con `withNewPassword` |
| Tipo | ✅ `:259`, `existing.type.isPersonal \|\| incoming.type != existing.type` |

### Strengths

- La ronda va al fondo: una tabla de cada campo del camino de la fusión, con su prueba de «vacío» y de «lo mismo», y un test de propiedad sobre seis campos con «Reemplazar» de punta a punta. Así se dejan de arreglar casos sueltos.
- **R58 es la regla más sencilla posible:** lo que hizo el duplicado no lo cambia el archivo. Cierra RT-15 sin una lista de excepciones.
- **R60 deja el cupo de etiquetas más justo:** una carpeta igual a una etiqueta de la bóveda, salvo por lo invisible, ya no gasta cupo de `IMPORT_MAX_NEW_TAGS`.

### Issues

#### Critical

Ninguno.

#### Important

Ninguno en el código que cambia este commit.

#### Minor

**N-8. Dos filas de la tabla campo por campo no son exactas.**
- **Dónde:** `redteam-recheck-fixes-report.md:213` (URL) y `:217` (secreto del 2FA).
- **Qué pasa:**
  - URL: dice «solo llega aquí con la misma clave https». Llega con cualquier clave igual con host (también `http`, `javascript://…`) o de texto, o sin URL en la bóveda y https en el archivo.
  - Secreto: dice «el duplicado tiene la misma clave», y eso solo vale para una entrada `TOTP`. Un acceso con 2FA es duplicado sin comparar el secreto (D-3).
- **Arreglo:** esas dos frases, y en la fila del secreto, añadir «en un acceso con 2FA no se compara y se trae el del archivo».

**N-9. Una carpeta con un NBSP dentro sigue dando una etiqueta que se ve igual que otra.**
- **Dónde:** `imports/MergePlan.kt:160-161` (la línea cambia en este commit): `SPACES = Regex("\\s+")` (`:381`) no casa con U+00A0, y `normalizeTags` (`vault/EntryQuery.kt:43-45`) solo recorta los extremos.
- **Qué pasa:** probado: «Ban co» da la etiqueta `ban<U+00A0>co`, y «Ban co» da `ban co`. Son dos chips «#ban co» que se ven igual, dos filtros. Es la misma clase que RT-17 (Info), con un carácter que se dibuja como un espacio. Una carpeta en ancho completo («Ｂanco» → `ｂanco`) se ve distinta y está bien que sea otra etiqueta.
- **Arreglo:** leer la carpeta como la compara la regla: `compareKey(folder)` en lugar de `plainReportText(folder, Int.MAX_VALUE)`, que además junta los espacios Unicode y aplica NFKC. R60 ya dice que las etiquetas son de Clavero y que R9/R10 no se aplican.
- **Coste:** una línea y dos aserciones. «Ｂanco» pasaría a «banco», que cumple el «se lee como se ve» de R60.

#### Pendiente de decidir (no lo introduce este commit; lo clasifico para el controlador)

**D-2. La contraseña no usa `showsNothing` (R59 dice «todo campo»).**
- **Dónde:** `imports/MergePlan.kt:288` (`isEmpty`) y `redteam-recheck-fixes-report.md:214`.
- **Recomendación: aceptar la excepción y apuntarla como aclaración de R59.**
  - Una contraseña es un secreto opaco: «   » o un carácter invisible pueden serlo de verdad, y el archivo, aunque sea hostil, ya puede poner la contraseña que quiera (también una visible).
  - «Reemplazar» la cambia por diseño, y la anterior queda en el historial (`withNewPassword`). Con `showsNothing` no se gana nada.
- **Severidad si se tomara como defecto:** ninguna.

**D-3. «Reemplazar» cambia el secreto del 2FA de un acceso sin que la fila lo diga y sin guardar el anterior.**
- **Dónde:** `imports/MergePlan.kt:279-283`. Las líneas del emisor y de la cuenta cambian en este commit; la del secreto (`?: t`) es anterior y sigue el spec («Reemplazar» toma el 2FA del archivo).
- **Qué pasa:** probado de punta a punta. Un acceso «Banco» / «ana» / `https://banco.es/` con el 2FA `JBSWY3DPEHPK3PXP`, y en el archivo la misma fila con `GEZDGNBVGY3TQOJQ`. Es duplicado con la fila «ana · banco.es»; tras «Reemplazar» el 2FA es el del archivo, y `passwordHistory` no guarda nada del anterior.
- **Por qué importa:** el KDoc de `replaceWith` dice «so that replacing never loses anything». Perder la semilla del 2FA puede dejar al usuario sin entrar en esa cuenta. Solo se recupera con la copia de antes de importar, que siempre se hace (R37).
- **Severidad:**
  - como defecto, Minor: hace falta elegir «Reemplazar», que no es lo de por defecto, y la copia previa lo conserva;
  - como decisión de diseño, la mantiene el spec.
- **Lo que propongo para que el controlador decida:**
  - o se acepta y se dice en la fila de la tabla y en P-9 («Reemplazar» también cambia el 2FA del acceso);
  - o, si los dos tienen 2FA con claves distintas, se conserva el de la bóveda y el del archivo pasa a las notas (como las demás URL), con un test.

#### Info (sin arreglo)

- **README P-9** (`README.md:348`): «su título y su usuario se quedan como los tenía la bóveda (son los que la hacen repetida…)». Es verdad en un acceso y en una nota. En un 2FA el título se trae si es otro y se ve, y el paréntesis lo deja entender. Si se quiere, «en un acceso».
- **Notas que solo cambian por un invisible:** «mis​ notas» contra «mis notas» se añaden debajo de «Importado el …» (probado). Se ve un texto repetido, pero no se pierde nada ni se escribe nada que no se vea (`trim()` como «lo mismo», `:264`). Si se quiere, comparar con `compareKey` en ese punto.
- **Una carpeta de solo una marca combinante** (U+0301) da una etiqueta de una marca sola. `compareKey` la ve como algo, así que el test de propiedad la acepta.

### Assessment

**Task quality:** Needs fixes, solo con dos Minor (N-8, que es de documento, y N-9, de una línea).
- **Arreglados y verificados de punta a punta:** N-6, la fila del Info, RT-15, RT-16 y RT-17 (con N-9 al lado). I-2 se sostiene.
- **Lo que pedía el repaso, comprobado:** «Reemplazar» ya no escribe lo que no enseña nada encima de lo que sí en el título, el usuario, la URL, las notas, el emisor, la cuenta ni las etiquetas; el cupo de etiquetas se cuenta después de limpiar la carpeta; y el test de propiedad recorre de verdad el camino de escritura.
- **Para que el controlador decida, sin bloquear este commit:**
  - D-2 (la contraseña): recomiendo aceptar la excepción;
  - D-3 (el 2FA de un acceso al reemplazar): es anterior y lo pide el spec; recomiendo decidirlo para la 1.4.0 o pasarlo a la 1.4.1.

### Revisión experto-seguridad-cripto

- **Modelo de amenaza:** un archivo importado hostil (decisión (c)). Lo guardado solo cambia por «Reemplazar» (`imports/MergePlan.kt:258-291`), que el usuario elige, con la copia previa obligatoria. Nada toca primitivas, claves ni formatos.
- **CWE-436:** el lector (`Matcher`, `:554-560`) y el escritor (`replaceWith`) usan ya la misma idea de «vacío» y de «lo mismo» en el título y el usuario (R58), en la URL (R55) y en el emisor, la cuenta y las notas (R57, R59). La tabla lo deja escrito, salvo las dos inexactitudes de N-8.
- **El secreto del 2FA de un acceso (D-3)** es el único campo con valor de seguridad que «Reemplazar» cambia sin enseñarlo y sin historial. Es del spec, no de este commit, pero choca con la promesa «replacing never loses anything».
- **La contraseña (D-2):** que la sustituya otra que no se ve no da al atacante nada que no pudiera con una contraseña visible; la anterior queda en el historial.

### Revisión Ingeniero Móvil Senior

- **Kotlin puro en `imports/`**, en `jvm-check`. No hay pantalla, estado ni concurrencia nuevos; no hay nada en `rememberSaveable` ni en un `Bundle`.
- **Coste:** `replaceWith` calcula dos `titleKey` y dos `userKey` (NFKC) por cada elemento reemplazado, e `importTags`, un `plainReportText` por carpeta. Es lineal y en `Dispatchers.Default`. Según el informe, el test de propiedad tarda unos segundos en `jvm-check`; no lo volví a medir.
- **Etiquetas:** `normalizeTags` (`vault/EntryQuery.kt:43-45`) no cambia. El arreglo de N-9 sería una línea en `importTags`, sin tocar las etiquetas que ya hay en la bóveda.

### Textos para el Abogado Digital (revisión final)

- **README P-9** (`README.md:348`): la frase nueva sobre el título y el usuario. Si se acepta el Info, sería «en un acceso». Si D-3 se acepta como está, P-9 tendría que decir que «Reemplazar» también cambia el 2FA de un acceso.
- **README P-27** (`README.md:366`) y **`.maestro/README.md:88`**: las pruebas en el móvil de RT-15 y RT-17.
- **`docs/auditorias/2026-11-08-ataque-1.4.0.md:11` y `:329-349`** (publicado): «Quinta comprobación y tercer repaso».
- **`task-12-report.md:201`**, punto 18. No cambia ningún texto de la interfaz.
