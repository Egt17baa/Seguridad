# Repaso acotado 3 de `fd9bd4b`: I-2, N-4, N-5, P-27 (RT-15) y `hostOf` (R55, R56)

**Alcance:** repaso acotado de `14bb786..fd9bd4b`, con el paquete `.superpowers/sdd/2026-11-03-clavero-1.4.0/review-14bb786..fd9bd4b.diff`.
- **Decisiones que obligan:** R55 y R56 (`progress.md:282-285`).
- **Afirmaciones que verifico:** las de «Ronda 4 (R55)» en `redteam-recheck-fixes-report.md`.

Solo lectura y sin subagentes. No volví a ejecutar la suite; la puerta que se informa es app 1875 / jvm-check 1870, 0 fallos, 0 omitidos, 0 `w:`, lint 0 errores / 17 avisos.

Para las dudas concretas volví a pasar mi prueba de I-2 de punta a punta (`planMerge`, la fila y «Reemplazar») y añadí los casos de título, R56, 2FA y RT-15:
- antes comprobé que `app/src/main` del árbol es `fd9bd4b`, y que las clases compiladas de `jvm-check` traen ya `isNoUrl`, `showsNothing` y `hostOf` (`javap -p`);
- la prueba corrió sobre una **copia** de esas clases, en el *scratchpad* de la sesión, sin Gradle.

Hallazgos nuevos: solo sobre código que cambia este commit.

Rutas de Kotlin relativas a `app/src/main/java/app/seguridad/`, las de los tests a `app/src/test/java/app/seguridad/`. Líneas de `fd9bd4b`.

### Spec Compliance

| Punto | Veredicto | Evidencia |
|---|---|---|
| **I-2** (R55) | ✅ Arreglado | `isNoUrl` (`imports/MergePlan.kt:428`) es la única prueba de «sin URL»: la usan `urlKey` (`:415`) y `replaceWith` (`:269`). Lo confirmo de punta a punta contra «Banco» / «ana» / `https://banco.es/`: `\u0001`, `\u0000\u001F`, `\t\u0001\n`, `" "` y ` ` son duplicado con la fila «ana», y después de «Reemplazar» la web de la bóveda sigue siendo `https://banco.es/`. `​`, que el navegador no lee como vacío, da una clave «t» y entra como nueva, sin tocar la bóveda. Lo fija `MergePlanTest.aUrlOfOnlyControlsIsNoUrlForReplaceToo` |
| **R55, título y usuario** (`showsNothing`) | ✅ | `replaceWith` usa `showsNothing` (`:434`, `compareKey` vacío) para el título y el usuario (`:267-268`), que es lo mismo que compara la regla (`titleKey` y `userKey`). Para todo texto en blanco, `compareKey` es «», así que no pierde ningún caso de `ifBlank`. Un título que no enseña nada contra uno de la bóveda que sí («​» contra «Banco»): no es duplicado (las claves «» y «banco» son distintas), entra como nueva y el título de la bóveda sigue «Banco» (probado). Así que un título o un usuario solo pueden sustituir a otro que tampoco enseña nada, y «Reemplazar» ya no deja en blanco esos campos. También arregla el título de un 2FA, que se empareja por el secreto: con «\u0001» contra «Correo», el título queda «Correo» (probado) |
| **R56, `hostOf`** | ✅ | `:441`; lo usan `urlHost` (`:399`), `urlKey` (`:418`) y `shownUrl` (`:484`). Probado: `httpshttp@ /1HTTPS` contra una entrada sin web ya no es duplicado (clave «t»); entra como nueva, con la fila «ana · …@ /1HTTPS», y la bóveda no cambia. `hostOf` es `private` de `MergePlan.kt`: ni `parseLink` ni el Inspector de QR cambian. El oráculo de `theHostAgreesWithTheQrReaderForEveryWebLink` (`MergePlanTest.kt:130`) aplica la misma regla, que es lo que decide R56 |
| **La clave, la fila y «Reemplazar» dicen lo mismo** | ✅ para título, usuario y URL; ⚠️ emisor y cuenta del 2FA (N-6) | Clave nula ⇔ `isNoUrl` ⇔ «Reemplazar» conserva la URL ⇔ la fila sin web. Clave https ⇔ `hostOf` no vacío ⇔ la fila enseña ese host. Clave «t» ⇔ la fila enseña el texto (con la información de usuario tapada). No encontré ningún texto en la bóveda que una URL pueda borrar ni cambiar sin que la fila lo enseñe |
| **El test de propiedad ampliado es de punta a punta** | ✅ | `MergePlanTest.noCleanedSchemeButHttpsGivesAnHttpsKeyAndNoRowShowsTheUserInfo` hace, por cada uno de los 20 000 textos (`:373-385`), un `planMerge` y un `mergedEntries(…, REPLACE, …)` contra una entrada con `https://banco.es/` y contra otra sin web. Si la web cambia, exige `urlKey(after) == "hhttps://banco.es"` (la primera), o una clave https con la fila igual a `plainReportText("ana · " + urlHost(after))` (la segunda). Es real: encontró R56. Solo varía la URL; el título, el usuario y el 2FA no entran |
| **N-4** | ✅ Arreglado | `README.md:366`: tres CSV de una fila, y lo que saldría con uno solo. `.maestro/README.md:88`, igual |
| **N-5** | ✅ Arreglado | `redteam-recheck-fixes-report.md:192`: «hasta la última «@» de todo el texto (ronda 3, N-1; en `db42d3e` era …)». El punto N-5 del mismo informe (`:172`) cita la línea antigua, `:157` (Info) |
| **P-27, lo añadido de RT-15** | ✅ | `README.md:366` y `.maestro/README.md:88`. Probado: `ht<NBSP>tp:@banco.es`, `ht<U+3000>tp:@banco.es`, `ht<U+200B>tp:@banco.es`, `ht<U+0001>tp:@banco.es` y `http：@banco.es` dan `hhttps://banco.es` y la fila «ana · banco.es», como dice P-27 («Clavero las compara como https en banco.es»). Los navegadores van con la línea de marcas debajo |
| **`ataque-1.4.0.md`, la sección nueva** | ✅ | El estado (`:10`) y «Cuarta comprobación y segundo repaso» (`:311-326`): I-2 tal como pasaba, R55, los cuatro puntos del arreglo (también `httpshttp@ /1HTTPS`, verificado) y «Nada cambia un formato, un texto ni lo guardado fuera de «Reemplazar»», que es verdad. La prueba RT-15, con la línea de marcas (`:326`) |
| **Palabras prohibidas; correo del dueño; enlaces de sesión** | ✅ | Nada en las líneas añadidas de README, `.maestro`, `ataque-1.4.0.md` ni `app/src/main`. El informe de trabajo ya solo nombra «limpia», «limpio» y «de verdad» entre comillas, para decir que no las usa (`:172`) |

### Strengths

- **R55 resuelve la causa:** una función por cada idea de «vacío» (`isNoUrl`, `showsNothing`, `hostOf`, `imports/MergePlan.kt:422-441`), compartida por quien lee (la regla de duplicados y la fila) y quien escribe («Reemplazar»).
- **El test de propiedad prueba ya el camino de escritura** y encontró otro caso de la misma clase (R56) antes que cualquier revisor.
- **`showsNothing` también protege el título de un 2FA,** que se empareja por el secreto y que antes se podía sustituir por texto invisible.

### Issues

#### Critical

Ninguno.

#### Important

Ninguno.

#### Minor

**N-6. «Reemplazar» todavía pone un emisor o una cuenta de 2FA que no enseñan nada en lugar de los de la bóveda.**
- **Dónde:** `imports/MergePlan.kt:272`: `t.issuer.ifBlank { e.issuer }` y `t.account.ifBlank { e.account }`. El KDoc está en `:239-242`, y lo que el implementador repasó, en `redteam-recheck-fixes-report.md`, «Ronda 4», punto del 2FA.
- **Qué pasa:** probado de punta a punta. Un 2FA de la bóveda con el título «Correo», el emisor «Correo» y la cuenta «ana», y en el archivo el mismo secreto con el emisor `\u0001` y la cuenta `​`. Es duplicado (por el secreto) y su fila no enseña nada legible. Después de «Reemplazar», el título sigue «Correo» (R55), pero el emisor queda `[U+0001]` y la cuenta, `[U+200B]`.
- **Por qué importa:** el emisor y la cuenta son lo que la fila de un 2FA enseña (`duplicateSubline`, `:503-506`) y el nombre con el que el usuario reconoce el código. R55 pide una sola idea de vacío en «Reemplazar», y el repaso pedía que «Reemplazar» no vacíe ningún campo de la bóveda. Hace falta tener el mismo secreto TOTP, así que el impacto es casi nulo (una etiqueta). Por eso es Minor y no Important.
- **Arreglo:** `issuer = if (showsNothing(t.issuer)) e.issuer else t.issuer`, lo mismo con `account`, una línea en el KDoc y una aserción en `aTitleOrUserThatShowsNothingKeepsTheVaults`.

#### Info (sin arreglo)

- `redteam-recheck-fixes-report.md:172` (punto N-5) cita `:157`, pero la frase corregida está ahora en `:192`.
- Una URL `​` entra como nueva (bien), y su fila, que solo se vería si fuera duplicado, sería «ana · » con el separador colgando. Para que sea duplicado, la bóveda tendría que tener el mismo texto. Es cosmético y no lo cambia este commit.

### Assessment

**Task quality:** Needs fixes, solo con un Minor (N-6). No hay ningún Critical ni Important.
- I-2, N-4, N-5 y lo añadido de RT-15 en P-27 están arreglados y verificados de punta a punta.
- `isNoUrl`, `showsNothing` y `hostOf` dicen lo mismo en la clave, la fila y «Reemplazar» para la URL, el título y el usuario.
- El test de propiedad recorre de verdad `planMerge` y «Reemplazar».
- N-6 es una línea. Como esta es la ronda 4 de 5, se puede arreglar sin otra ronda completa: un repaso de esa línea basta.

### Revisión experto-seguridad-cripto

- **Modelo de amenaza:** un archivo importado hostil (decisión (c)). Nada toca primitivas, claves ni formatos. Lo guardado solo cambia por «Reemplazar» (`imports/MergePlan.kt:252-281`), y ahora menos.
- **CWE-436 (dos lectores que no coinciden):**
  - URL: cerrado con `isNoUrl` (`:428`), en `urlKey` (`:415`) y `replaceWith` (`:269`);
  - título y usuario: cerrado con `showsNothing` (`:434`, `:267-268`);
  - host que no enseña nada: cerrado con `hostOf` (`:441`), así que `HTTPS_KEY` (`:547`) ya no sale de un host de solo espacios.
  Queda el emisor y la cuenta del 2FA (N-6), que no forman parte de la clave pero sí de la fila.
- **R48 y R45:** con el test de propiedad de punta a punta, una entrada sin web solo toma una URL https cuyo host enseña la fila, y la web https de la bóveda solo la sustituye una que da `hhttps://banco.es`.
- **RT-15** sigue siendo una prueba en el móvil: los cinco textos de P-27 dan `hhttps://banco.es`. Si una barra los abre como `http://banco.es`, es RT-15 para la 1.4.1, como dicen P-27 y `ataque-1.4.0.md:326`.

### Revisión Ingeniero Móvil Senior

- **Kotlin puro** (`imports/MergePlan.kt`), en `jvm-check`. No hay pantalla, estado ni concurrencia nuevos; no hay nada en `rememberSaveable` ni en un `Bundle`.
- **Coste:** `showsNothing` llama a `compareKey` (NFKC), una vez por título y por usuario de cada elemento reemplazado y una vez por host en `hostOf`. Es lineal, en `Dispatchers.Default`, y despreciable con 20 000 elementos. El test de propiedad, con «Reemplazar», corre en menos de un segundo, según el informe (no lo volví a medir).
- **API:** `isNoUrl` es `internal` (la usan los tests), y `showsNothing` y `hostOf` son `private`. No hay nada público nuevo.

### Textos para el Abogado Digital (revisión final)

- **README P-27** (`README.md:366`) y su fila de `.maestro/README.md:88` cambian: tres CSV de una fila y la prueba de pegar con NBSP, U+3000, U+200B, un control o «：». Nombran Chrome y el navegador de Honor, debajo de la línea de marcas que ya había.
- **`docs/auditorias/2026-11-08-ataque-1.4.0.md:10` y `:311-326`** (publicado): la sección «Cuarta comprobación y segundo repaso», con la línea de marcas en `:326`.
- No cambia ningún texto de la interfaz. Con N-6 arreglado, tampoco.
