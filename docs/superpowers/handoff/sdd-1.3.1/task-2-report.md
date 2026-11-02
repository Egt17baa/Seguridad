# Tarea 2, CRY-A01: una maestra nueva tiene que pasar la estimación de fuerza

Estado: DONE (con dos observaciones en «Concerns»).
Commit: `d981e72` «vault: una maestra nueva tiene que pasar la estimación de fuerza (CRY-A01)», empujado a `ccr-a1a6eabb-765f9g`.
El mensaje acaba solo en `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` (`grep -cE "Claude-Session|claude\.ai"` da 0).

## Qué se ha implementado

- **`vault/MasterPolicy.kt` (nuevo, JVM puro, entra en `jvm-check`):**
  - `const val MIN_MASTER = 10`, movido desde `ui/screens/VaultBackup.kt`;
  - `const val MASTER_TOO_SHORT = "Mínimo $MIN_MASTER caracteres"` (constante de compilación; vale exactamente «Mínimo 10 caracteres», lo fija un test);
  - `const val MASTER_TOO_WEAK`, el texto del punto 1 de «Textos para el Abogado Digital» de `04-cripto-parteA.md`, palabra por palabra;
  - `fun newMasterProblem(candidate: String): String?`: primero `MASTER_TOO_SHORT`, después `MASTER_TOO_WEAK` (`estimateBits(candidate) < WEAK_BITS`), o `null`.
  - El KDoc dice que solo vale para contraseñas nuevas, que abrir/importar no pasan por aquí, que es una estimación sin diccionario («Barcelona1» da unos 59 bits y pasa) y que `VaultStore` no cambia.
- **`PasswordDialog`** gana `newPassword: Boolean = false`. Con `true`, `ok` exige `newMasterProblem(a) == null` y el problema se enseña en `NegativeColor` en cuanto hay texto. Con `false` (importar) sigue `a.length >= MIN_MASTER`, como antes.
- **«Nueva contraseña maestra»** (`"master"`) pasa `newPassword = true`. **«Importar»** (`"import"`) no lo pasa.
- **`CreateVault`** («Crea tu bóveda») calcula `problem = newMasterProblem(a)`, lo enseña en `NegativeColor` si `a` no está vacío, y el botón solo se activa con `problem == null && a == b && understood`.
- La etiqueta «Contraseña maestra (mínimo $MIN_MASTER caracteres)» no cambia (ahora importa `MIN_MASTER` de `vault`).
- Los dos únicos sitios que crean una maestra (`store.create` en `VaultScreen.kt:268` y `store.changeMaster` en `VaultBackup.kt:123`) están cubiertos. `unlock`, la huella e `importReplace` no cambian. No se ha tocado ningún formato, `VaultStore`, `Health.kt` ni nada de CriptoPanel.

## Tests y resultados

Antes: 1213 app, 1208 jvm-check. Después: **1227 app (0 fallos, 0 omitidos) y 1222 jvm-check (0 fallos)**. Suben 14 en cada uno: los de `MasterPolicyTest`.

`MasterPolicyTest` (14 tests, `app/src/test/java/app/seguridad/vault/MasterPolicyTest.kt`):
- los textos exactos (`MIN_MASTER`, `MASTER_TOO_SHORT`, `MASTER_TOO_WEAK`);
- «corta», vacío, 9 caracteres y un texto fuerte de 9 caracteres dan `MASTER_TOO_SHORT` (la longitud va primero);
- «contraseña», «Contraseña», «CONTRASEÑA», «1234567890», «aaaaaaaaaa», «qwertyuiop», diez espacios, «abcdefghij» y «0000000000» dan `MASTER_TOO_WEAK`;
- «casa perro luz mar sol», su versión con guiones y «Barcelona1» (el límite aceptado, entre 50 y 65 bits) dan `null`;
- el umbral es el de «Salud» (`WEAK_BITS == 50.0`, y el rechazo coincide con `estimateBits < WEAK_BITS`);
- 200 frases de 5 palabras por lista y separador (EFF, BIP-39 español y BIP-39 inglés; separadores «-», « », «.», «_», los cuatro que ofrece `GeneratorScreen`) dan `null`;
- también con mayúscula inicial y con número añadido (50 por combinación, EFF y español);
- 200 contraseñas de 16 caracteres con las opciones por defecto, y 200 sin símbolos, dan `null`;
- código fuente (se leen los `.kt` de `ui/screens/` sin comentarios, localizados desde `app/` o `jvm-check/`):
  - `VaultScreen.kt` y `VaultBackup.kt` llaman a `newMasterProblem(`, y el botón de crear espera `problem == null`;
  - el `PasswordDialog` de `"master"` pasa `newPassword = true` y el de `"import"` no menciona `newPassword`;
  - `length >= MIN_MASTER` aparece una sola vez en todo `ui/screens/`, dentro de `PasswordDialog` en `VaultBackup.kt`;
  - `MIN_MASTER` no se define en `ui/` y las dos pantallas lo importan de `app.seguridad.vault`.

Los textos nuevos los cubren `SourceTextsTest` y `ToolTextsTest`, que pasan en las dos ejecuciones completas (se recorren todas las cadenas de `app/src/main/java`).

### Evidencia TDD

RED 1 (el test escrito antes del código; no compila):
```
$ gradle -p jvm-check test --tests '*MasterPolicyTest*' --offline --no-daemon --max-workers=1
e: .../MasterPolicyTest.kt:67:58 Unresolved reference 'newMasterProblem'.
e: .../MasterPolicyTest.kt:67:81 Unresolved reference 'MASTER_TOO_WEAK'.
> Task :compileTestKotlin FAILED
BUILD FAILED in 14s
```
RED 2 (solo `MasterPolicy.kt`, aún sin tocar la interfaz; los tests de comportamiento ya pasan y fallan los cuatro de código fuente):
```
MasterPolicyTest > creating the vault and changing the master call newMasterProblem FAILED
MasterPolicyTest > MIN_MASTER has one definition, in vault FAILED
MasterPolicyTest > no screen but the dialog itself checks only the length of a master FAILED
    java.lang.AssertionError: only PasswordDialog (the import) keeps the length check: [VaultBackup.kt, VaultScreen.kt] expected:<[VaultBackup.kt]> but was:<[VaultBackup.kt, VaultScreen.kt]>
MasterPolicyTest > the master dialog asks for a new password and the import dialog does not FAILED
14 tests completed, 4 failed
```
GREEN (después de `VaultBackup.kt` y `VaultScreen.kt`):
```
$ gradle -p jvm-check test --tests '*MasterPolicyTest*' --offline --no-daemon --max-workers=1
... 14 líneas PASSED ...
BUILD SUCCESSFUL in 18s
```
Comprobación de mutaciones del test de código fuente (con `cleanTest`, ver «Concerns»):
- `newPassword = true` añadido al diálogo de importar: `the master dialog asks for a new password and the import dialog does not FAILED`;
- `newPassword = true` quitado del diálogo de la maestra: el mismo test falla.
Los dos cambios se revirtieron (el diff volvió a ser el de arriba).

### Verificaciones globales (una vez, antes del commit)

```
$ gradle -p jvm-check cleanTest test --offline --no-daemon --max-workers=1
1222 PASSED, 0 FAILED   BUILD SUCCESSFUL in 1m 4s
$ ./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets --no-daemon --max-workers=1 --offline
1227 tests, 0 fallos, 0 errores, 0 omitidos   BUILD SUCCESSFUL in 3m 22s
```
Después se movió un comentario a su propia línea en `VaultBackup.kt:113` (para que un comentario al final de la línea no pueda engañar al test de código fuente) y se volvió a ejecutar `MasterPolicyTest` en `jvm-check` (14 en verde) y `:app:compileDebugKotlin` (verde). Fue un cambio solo de comentario.

## Archivos cambiados

- Nuevo: `app/src/main/java/app/seguridad/vault/MasterPolicy.kt`
- Nuevo: `app/src/test/java/app/seguridad/vault/MasterPolicyTest.kt`
- `app/src/main/java/app/seguridad/ui/screens/VaultBackup.kt`: sale `MIN_MASTER`, entran dos imports, `newPassword` en `PasswordDialog` y en la llamada de `"master"`.
- `app/src/main/java/app/seguridad/ui/screens/VaultScreen.kt`: `CreateVault` usa `newMasterProblem`.

## Autorrevisión del diff

- Sin hallazgos que cambien el diseño. Revisado: orden de imports (ASCII, como el resto), `a.isNotEmpty()` para no enseñar el error en un campo vacío, el texto de longitud aparece mientras se escribe y no solo desactivando el botón (lo pide el diseño), el botón de importar no cambia, no hay `rememberSaveable` ni `Bundle`, no hay logs, `fill('\u0000')` de `CreateVault` y del diálogo no cambia.
- `newMasterProblem` es pura, sin estado; se recalcula en cada recomposición del campo (coste de una pasada por la cadena).

## Concerns

1. **El Generador, con opciones que no son las de siempre, puede proponer algo que la regla rechaza.** Medido con 2000 muestras (prueba temporal que se borró):
   - Con las opciones por defecto (minúsculas, mayúsculas, números y símbolos) y desde 12 caracteres: 0 rechazos. A 8 caracteres se rechaza todo, porque la regla pide 10.
   - Solo minúsculas: 144/2000 rechazadas a 12 caracteres, 0 a 16.
   - Solo números: 662/2000 rechazadas a 16 caracteres, 44/2000 a 20 y 2/2000 a 24 (la estimación cuenta las secuencias «123», «321» y los repetidos como un solo carácter, y 10 símbolos dan 3,3 bits por carácter).
   - Frases de 4 y de 5 palabras, cualquier separador, lista EFF: 0 rechazos en 5000.
   La regla pedida (frases de 5 palabras y 16 caracteres por defecto) se cumple siempre. Un usuario que genere solo números de 16 tiene que alargar la contraseña. El mensaje le dice que use una frase. No lo arreglo porque el Generador no está en el alcance, pero conviene saberlo en la prueba del móvil.
2. **`jvm-check` no vuelve a ejecutar los tests de código fuente si solo cambian ficheros de `ui/`.** `ui/**` está excluido de la compilación de `jvm-check`, así que Gradle da `test` por actualizado (lo vi en una mutación: `BUILD SUCCESSFUL in 9s` con el test sin ejecutarse). `:app:testDebugUnitTest` sí los vuelve a ejecutar, porque compila `ui/`. Es el mismo comportamiento de `PlatformSourcesTest` y `DocumentUriTest`. Para la verificación de cada tarea, `cleanTest` antes de `test` en `jvm-check` lo evita.
3. **Copia transitoria del texto:** `estimateBits` hace `password.lowercase(Locale.ROOT)`, lo que crea un `String` copia de lo escrito en cada recomposición. El campo ya es un `String` en el estado de Compose (existente), que no se puede poner a cero, y la copia es corta. Es el mismo riesgo residual que el de CRY-A03 (cadenas que no se pueden poner a cero). No toco `Health.kt`.
4. El límite que el propio informe reconoce sigue ahí: la estimación no tiene diccionario, así que «Barcelona1» pasa. Está en el KDoc y en un test, para que nadie crea que la regla garantiza una maestra fuerte.

## Revisión experto-seguridad-cripto

Lista de `references/` aplicada al diff (contraseñas y KDF, gestión de claves, secretos en memoria, entradas manipuladas, compatibilidad):
- **Amenaza cubierta:** quien tiene una copia `.sgbak` y prueba un diccionario sin límite de intentos. La regla rechaza lo que la propia app llama «Débil» en «Salud» (`vault/MasterPolicy.kt:31-35`, `WEAK_BITS` de `health/Health.kt`). Referencia: OWASP ASVS V2.1 (longitud y comprobación de contraseñas nuevas) y NIST SP 800-63B §5.1.1.2 (rechazar secretos de la lista de comunes), con la diferencia declarada de que aquí no hay diccionario.
- **Solo contraseñas nuevas:** `create` (`ui/screens/VaultScreen.kt:259`) y `changeMaster` (`ui/screens/VaultBackup.kt:114,227-228`). El desbloqueo, la huella y `importReplace` no pasan por la regla, y el diálogo de importar conserva `a.length >= MIN_MASTER` (`VaultBackup.kt:228`, rama `newPassword = false`). Una copia hecha con una maestra más débil (por ejemplo «contraseña123») sigue importándose. Lo fija el test de código fuente.
- **Sin cambio de formato ni de KDF:** `VaultStore`, `Vault.kt` y `Crypto.kt` no se tocan; `SG1:`, `.sgenc`, `seguridad-vault`, `.sgbak` y los datos de prueba siguen igual (1222 y 1227 tests en verde, incluidos los de compatibilidad).
- **Secretos en memoria:** sin `rememberSaveable`, `Bundle`, logs ni copia nueva en el estado. Los `fill('\u0000')` de `CreateVault` (`VaultScreen.kt:272`) y del cambio de maestra (`VaultBackup.kt:147,178`) siguen. La copia transitoria de `lowercase` se declara en «Concerns» (3).
- **Límite honesto:** el KDoc (`MasterPolicy.kt:25-29`) y `Barcelona1 is the known limit…` (`MasterPolicyTest.kt:57`) dicen que la regla no garantiza una maestra fuerte.
- **Lo que propone el Generador pasa** (punto 3 del contexto): frases de 5 palabras con tres listas y cuatro separadores (`MasterPolicyTest.kt:82`), con mayúscula y número (`:95`) y contraseñas de 16 (`:108`). Limitación medida: solo números de 16, ver «Concerns» (1).
- Sin permisos, dependencias, red ni componentes nuevos; `FLAG_SECURE` como estaba.

## Revisión Ingeniero Móvil Senior

- **Arquitectura:** la regla es una función pura en `vault/`, sin Android, probada en la JVM (`vault/MasterPolicy.kt:31`); la interfaz solo la llama. `MIN_MASTER` tiene una sola definición (`MasterPolicy.kt:7`) y las dos pantallas lo importan (`VaultBackup.kt:33`, `VaultScreen.kt:77`).
- **Compose:** `PasswordDialog` sigue con el estado hacia dentro (`var a/b by remember`) y gana un parámetro explícito con valor por defecto `false` (`VaultBackup.kt:219`), así que ningún otro llamador cambia y importar no se endurece por descuido. El problema se calcula en el cuerpo (`:227`) y se enseña con `Text(..., color = NegativeColor)` (`:238`); en `CreateVault` igual (`VaultScreen.kt:245-246`). Sin `LaunchedEffect` ni estado derivado almacenado, así que no hay estado que pueda quedar atrasado.
- **Accesibilidad:** el problema es un `Text` junto al campo, que TalkBack lee en el recorrido; no se ha enlazado con la propiedad `error` del campo (`PasswordField` es un componente propio y no se ha tocado). Queda para la prueba de TalkBack que ya está pendiente.
- **Ciclo de vida y giro:** nada nuevo en `rememberSaveable`; `a` y `b` siguen en `remember` como antes.
- **Pruebas:** comportamiento en la JVM y el cableado de la interfaz como test de código fuente (la interfaz no se compila en `jvm-check`); la prueba en el móvil sigue siendo del dueño: escribir «contraseña» en «Crea tu bóveda» y ver el aviso, y lo mismo en «Cambiar contraseña maestra».

## Textos para el Abogado Digital (revisión final)

Todos son constantes en `app/src/main/java/app/seguridad/vault/MasterPolicy.kt`. Aparecen en rojo (`NegativeColor`) en cuanto hay texto en «Crea tu bóveda» (`ui/screens/VaultScreen.kt:246`) y en «Nueva contraseña maestra» (`ui/screens/VaultBackup.kt:238`). Nunca al abrir ni al importar.

1. **`MASTER_TOO_WEAK`** (`MasterPolicy.kt:13`): «Esta contraseña es fácil de adivinar. Usa una frase de 5 o 6 palabras que solo tú conozcas (el Generador te puede dar una).»
   - (Matizado por la ronda 2: «fácil de adivinar» es ahora la estimación de `estimateBits` y además «es una sola palabra de las listas de la app»; ver la nota final.) Afirmación sobre el código a comprobar: «fácil de adivinar» era la estimación de `estimateBits` (menos de 50 bits), no un diccionario; una contraseña como «Barcelona1» no se marca y puede ser fácil de adivinar. El texto no promete lo contrario, pero la revisión final debería confirmar que no hay otro sitio (ficha de Play, política) que diga que la app «comprueba que la maestra sea fuerte».
2. **`MASTER_TOO_SHORT`** (`MasterPolicy.kt:10`): «Mínimo 10 caracteres». Antes esta información solo estaba en la etiqueta del campo. La etiqueta «Contraseña maestra (mínimo 10 caracteres)» no cambia.
3. Nada más. No hay cambios en `docs/privacidad.html`, README ni la ficha; si la política o la ficha describen los requisitos de la maestra, esa revisión final debe alinearlos con «10 caracteres y que no sea fácil de adivinar, solo al crear la bóveda o cambiar la maestra».

---

# Ronda de arreglos 1 (revisión de la tarea 2)

Decisión del controlador: la regla no cambia (`MIN_MASTER` y `estimateBits >= WEAK_BITS`, sin diccionario, sin plegar acentos, `Health.kt` intacto). Solo se dicen sus límites con verdad, y se arreglan los menores.

## Cambios

- **Important 1 (límites verdaderos).**
  - `MasterPolicyTest.kt`: test nuevo `a word with an accent or an enye gets a large bonus, so contrasena123 and Contrasena2024 pass`, junto al de «Barcelona1». Fija como aceptadas (`null`) «contraseña1», «contraseña123» y «Contraseña2024» (con ≥ 50 bits), con un comentario que explica que cualquier carácter fuera de ASCII añade 100 símbolos al conjunto (`Health.kt:89`), para que quien cambie `estimateBits` vea el efecto en la regla. También fija que «contrasena123» (sin ñ) baja pero sigue aceptada, y que «contraseña» solo se rechaza por estar en la lista de comunes. El test de los débiles lleva ahora un KDoc que dice por qué se rechaza cada uno.
  - `MasterPolicy.kt` (KDoc de `newMasterProblem`): dice que quita solo los casos obvios (corta, repetidos, secuencias y las palabras exactas de la lista de comunes de «Salud»), que «contraseña» se rechaza solo por estar en esa lista, que «Barcelona1» pasa, y que una palabra con ñ o acento recibe una bonificación grande, así que «contraseña123» y «Contraseña2024» pasan.
- **Minor 2.** KDoc: ahora dice «passes in the tests» (frase de 5 palabras con cualquier separador y lista, y contraseña de 16 caracteres), y que una de solo números se rechaza a veces hasta unos 24 caracteres.
- **Minor 3.** `MasterPolicyTest.kt`, test de 16 caracteres: el comentario ya dice «letras y números» para `symbols = false`, y se añade el caso real de solo letras (`digits = false, symbols = false`), 200 contraseñas, todas aceptadas.
- **Minor 4.** `VaultScreen.kt:254-261` (condición en `:255`): el «Consejo: usa una frase…» fijo solo se enseña si `problem != MASTER_TOO_WEAK`; con otro estado (campo vacío, «Mínimo 10 caracteres», o sin problema) sigue visible. Test de código fuente `the hint of a phrase is not shown together with the same advice`.
- **Minor 5.** `jvm-check/build.gradle.kts`: `tasks.test` declara como entradas los ficheros que se excluyen de la compilación (`ui/**`, `android/**`, `MainActivity.kt`, `SeguridadApp.kt`, la lista `androidOnly` ya existente) con `inputs.files(fileTree("../app/src/main/java") { include(androidOnly) })`. Así también `PlatformSourcesTest` y `DocumentUriTest` se vuelven a ejecutar.
- **Minor 6.** Los dos textos rojos nuevos llevan `Modifier.semantics { liveRegion = LiveRegionMode.Polite }` (`VaultScreen.kt:251`, `VaultBackup.kt:243`; imports de `LiveRegionMode`, `liveRegion`, `semantics` y `Modifier`). «No coinciden» no cambia. Test de código fuente `the problem of a new master is announced by TalkBack and No coinciden is left as it was`: la línea del problema lleva la región, hay una sola en cada fichero y ninguna línea de «No coinciden» la tiene.

## TDD de esta ronda

RED (los tests nuevos, con el código sin tocar; el test de la ñ pasa de inmediato porque fija el comportamiento actual, a propósito):
```
$ gradle -p jvm-check cleanTest test --tests '*MasterPolicyTest*' --offline --no-daemon --max-workers=1
MasterPolicyTest > the hint of a phrase is not shown together with the same advice FAILED
MasterPolicyTest > the problem of a new master is announced by TalkBack and No coinciden is left as it was FAILED
    java.lang.AssertionError: VaultScreen.kt:         if (a.isNotEmpty()) problem?.let { Text(it, color = NegativeColor) }
MasterPolicyTest > a word with an accent or an enye gets a large bonus, so contrasena123 and Contrasena2024 pass PASSED
17 tests completed, 2 failed
```
GREEN (después de los cambios de interfaz, KDoc y build):
```
$ gradle -p jvm-check test --tests '*MasterPolicyTest*' --offline --no-daemon --max-workers=1
> Task :test   (17 tests, 0 fallos)   BUILD SUCCESSFUL in 32s
```

## Comprobación del arreglo 5 (el test se vuelve a ejecutar)

```
$ gradle -p jvm-check test --tests '*MasterPolicyTest*' ...            (sin cambios)
> Task :test UP-TO-DATE
$ echo "// probe" >> .../ui/screens/VaultBackup.kt ; gradle -p jvm-check test --tests '*MasterPolicyTest*' ...
> Task :test                       <- se ejecuta, solo cambió un fichero de ui/
$ (se restaura el fichero) ; gradle -p jvm-check test ...
> Task :test                       <- se ejecuta otra vez
```
El fichero de prueba se restauró; el diff final de `VaultBackup.kt` no contiene la línea de prueba.

## Verificaciones globales (una vez, antes del commit)

```
$ gradle -p jvm-check test --offline --no-daemon --max-workers=1
1225 PASSED, 0 FAILED   BUILD SUCCESSFUL in 1m 8s      (antes de la ronda: 1222)
$ ./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets --no-daemon --max-workers=1 --offline
1230 tests, 0 fallos, 0 errores, 0 omitidos   BUILD SUCCESSFUL in 3m 34s      (antes de la ronda: 1227)
```
Suben 3 en cada uno (los tres tests nuevos de `MasterPolicyTest`, ahora 17). El `jvm-check` se ejecutó sin `cleanTest`, porque el arreglo 5 hace que se ejecute solo.

## Textos para el Abogado Digital (revisión final), añadido

Sin textos nuevos para el usuario. Un dato que el Abogado Digital debe tener en cuenta (lo dice la revisión): la política y la ficha de Play no pueden decir que la app «comprueba que la maestra sea fuerte» ni que rechaza las contraseñas débiles o comunes. (La descripción de la regla de esta nota, y la lista de lo que pasa, **quedan reemplazadas por la nota de la ronda 2, «Textos para el Abogado Digital (revisión final)», más abajo**: desde la ampliación del dueño «contraseña123» y «Contraseña2024» se rechazan.) El consejo fijo de «Crea tu bóveda» («Consejo: usa una frase de 5 o 6 palabras…») ya no se enseña mientras sale `MASTER_TOO_WEAK`, que dice lo mismo.

## Revisión experto-seguridad-cripto (ronda 1)

- Regla sin cambios (`vault/MasterPolicy.kt:36-40`); solo cambian KDoc, tests, el consejo duplicado y la accesibilidad. Los límites reales quedan dichos y fijados: la bonificación por caracteres fuera de ASCII (`health/Health.kt:89`) está en el KDoc (`MasterPolicy.kt:25-31`) y en el test que fija «contraseña123» y «Contraseña2024» como aceptadas, así que un cambio futuro de `estimateBits` se nota.
- Nadie tiene que creer que la regla es más estricta de lo que es: el KDoc ya no dice «always» para el Generador (`MasterPolicy.kt:32-34`).
- Sin cambios en formatos, KDF, secretos ni red. `jvm-check/build.gradle.kts` solo cambia qué ficheros hacen que se vuelvan a ejecutar los tests; no cambia dependencias ni compilación.

## Revisión Ingeniero Móvil Senior (ronda 1)

- **Accesibilidad:** región viva educada en los dos textos nuevos (`ui/screens/VaultScreen.kt:251`, `ui/screens/VaultBackup.kt:243`), sin tocar «No coinciden». La prueba con TalkBack del móvil sigue siendo del dueño (¿se anuncia al cambiar de «Mínimo 10 caracteres» al texto de «fácil de adivinar»?).
- **UX:** el consejo fijo no se repite con `MASTER_TOO_WEAK` (`VaultScreen.kt:254-261` (condición en `:255`)); se sigue enseñando con el campo vacío, con «Mínimo 10 caracteres» y cuando la maestra vale.
- **Build:** `fileTree` con `PathSensitivity.RELATIVE` como entrada de `tasks.test`; comprobado que `test` ya no sale `UP-TO-DATE` al cambiar solo un fichero de `ui/`.
- **Compose:** sin estado nuevo; el consejo depende de `problem`, que se deriva en cada recomposición.

---

# Ronda 2 (ampliación del dueño)

Estado: DONE_WITH_CONCERNS (las observaciones están en «Concerns»).
Commit: `1be454e` «vault: la maestra nueva no puede ser una sola palabra conocida (CRY-A01, ampliación del dueño)», empujado a `ccr-a1a6eabb-765f9g` (`20d8e69..1be454e`). Autor `Claude <noreply@anthropic.com>`; el mensaje acaba solo en `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` (`grep -cE "Claude-Session|claude\.ai"` da 0). Sin `--amend`, `rebase` ni `push --force`.

## Comprobación de la ronda 1 (cada hallazgo de `task-2-review.md`, en `e76565a`/HEAD)

| Hallazgo | Estado en el código | Dónde |
|---|---|---|
| **Important 1** (límite real más ancho que el documentado: «contraseña123» pasa) | Arreglado en la ronda 1 y ahora **cerrado para una sola palabra** por la ampliación. Test que fija «contraseña1», «contraseña123» y «Contraseña2024» como aceptados por la estimación sola, con comentario del bono de 100 símbolos; KDoc con el límite | `MasterPolicyTest.kt` (test `a word with an accent or an enye gets a large bonus…`, ahora con una línea que remite al test de la ampliación); `vault/MasterPolicy.kt` KDoc de `newMasterProblem` |
| **Minor 2** (KDoc: «solo números» a veces se rechaza hasta ~24; «always») | Arreglado: «passes in the tests», «sometimes refused up to about 24 characters» | KDoc de `newMasterProblem` (`MasterPolicy.kt`) |
| **Minor 3** (comentario falso «only letters») | Arreglado: comentario «letters and digits» para `symbols = false` y caso real `digits = false, symbols = false` | `MasterPolicyTest.kt`, test `a password of 16 characters of the Generator passes` |
| **Minor 4** (consejo duplicado en «Crea tu bóveda») | Arreglado: `if (problem != MASTER_TOO_WEAK)` rodea el «Consejo: usa una frase…» (con la ampliación el mismo texto sale también por la regla de la palabra, y la condición lo cubre igual) y hay test | `ui/screens/VaultScreen.kt:258` (la condición), test `the hint of a phrase is not shown together with the same advice` |
| **Minor 5** (`jvm-check` no vuelve a ejecutar los tests de código fuente si solo cambia `ui/`) | Arreglado y **comprobado ahora**: `inputs.files(fileTree("../app/src/main/java") { include(androidOnly) })` en `jvm-check/build.gradle.kts` | Ver «Comprobación del arreglo 5» abajo |
| **Minor 6** (el texto rojo no es región viva) | Arreglado: `Modifier.semantics { liveRegion = LiveRegionMode.Polite }` en los dos textos; «No coinciden» intacto; test de código fuente | `ui/screens/VaultScreen.kt:254`, `ui/screens/VaultBackup.kt:246`, test `the problem of a new master is announced by TalkBack…` |

Los seis estaban hechos; no hubo que arreglar nada de la ronda 1. Sí se comprobó que sus 17 tests pasan en HEAD antes de empezar (`gradle -p jvm-check test --tests '*MasterPolicyTest*'`: 17 en verde, `BUILD SUCCESSFUL in 31s`).

### Comprobación del arreglo 5 (la entrada de `ui/` funciona)
```
$ gradle -p jvm-check test --tests '*MasterPolicyTest*' --offline --no-daemon --max-workers=1     (sin cambios)
> Task :test UP-TO-DATE
$ echo "// probe" >> app/src/main/java/app/seguridad/ui/screens/VaultBackup.kt ; (misma orden)
> Task :test                                   <- se ejecuta, solo cambió un fichero de ui/
$ git checkout app/src/main/java/app/seguridad/ui/screens/VaultBackup.kt                          (restaurado)
```

## Qué se ha implementado

- **`vault/MasterPolicy.kt`** (JVM puro, entra en `jvm-check`):
  - `fun masterCore(candidate: String): String`: `lowercase(Locale.ROOT)`, `Normalizer` NFD, se quitan las marcas `\p{Mn}` y quedan solo las letras (`Character.isLetter` por punto de código, para no partir un par subrogado). «Contraseña2024!» da «contrasena»; «Ábaco-12345» da «abaco».
  - `fun masterWords(lists: List<List<String>>): Set<String>`: cada palabra de las listas más las de `commonPasswords()` de «Salud», todas por `masterCore`, sin vacías (si no, una lista con «123456» rechazaría toda contraseña de solo dígitos). «drop-down» queda «dropdown».
  - `fun newMasterProblem(candidate: String, words: Set<String> = emptySet()): String?`: `MASTER_TOO_SHORT`, después `MASTER_TOO_WEAK` por la estimación, después `MASTER_TOO_WEAK` si `masterCore(candidate) in words`. Sin `words` hace lo mismo que antes. Mismo texto, sin texto nuevo.
  - `val MASTER_WORD_LISTS` (los tres nombres de `assets/`) y `fun loadMasterWords(open: (String) -> InputStream): Set<String>`: lee las tres listas (UTF-8, cierra cada flujo) y devuelve `masterWords`; ante cualquier fallo de lectura devuelve `emptySet()` (no media lista), así que la regla se queda en la longitud y la estimación. Está en `vault/` para probar en la JVM el éxito, el fallo y el cierre de flujos. Captura `Exception`, no solo `IOException`: es código bloqueante (no traga cancelaciones) y un fallo raro no debe tumbar la pantalla de crear la bóveda.
  - El KDoc dice el límite nuevo: «una sola palabra» es el núcleo entero; dos palabras juntas («abandon-ability») pasan si la estimación llega, y una palabra que no está en ninguna lista («Barcelona1») también.
- **`health/Health.kt`:** `internal fun commonPasswords(): Set<String> = COMMON` (3 líneas con el KDoc). `COMMON` sigue `private`; `estimateBits` y «Salud» no cambian.
- **`ui/screens/MasterWords.kt` (nuevo):** `rememberMasterWords(): Set<String>?` con `remember { mutableStateOf<Set<String>?>(null) }` y `LaunchedEffect(Unit) { words = withContext(Dispatchers.IO) { loadMasterWords { assets.open(it) } } }`. Una vez por pantalla abierta, `null` mientras carga, nunca `rememberSaveable` ni `Bundle`.
- **`ui/screens/VaultScreen.kt`** («Crea tu bóveda»): `val words = rememberMasterWords()`, `newMasterProblem(a, words ?: emptySet())` y el botón con `words != null && problem == null && …`.
- **`ui/screens/VaultBackup.kt`** (`PasswordDialog`): `val words = if (newPassword) rememberMasterWords() else null`; `ok = (if (newPassword) words != null && problem == null else a.length >= MIN_MASTER) && …`. El diálogo de importar ni lee las listas ni espera. Abrir la bóveda, la huella e `importReplace` no cambian.
- Los formatos, `VaultStore`, las dependencias, los permisos y todo lo de CriptoPanel no se tocan. No hay texto nuevo para el usuario.

### Lo que dicen las listas (comprobado antes de normalizar)
- La lista EFF (`eff_large_wordlist.txt`, 7776 líneas) **ya no tiene números de dados**: una palabra por línea. Cuatro líneas llevan guion (`drop-down`, `felt-tip`, `t-shirt`, `yo-yo`) y `masterCore` las deja sin él.
- `bip39_spanish.txt` está **guardada en NFD** (la «á» de «ábaco» son dos puntos de código: `a` + U+0301), no en NFC. Un test usa esa forma para que nadie «simplifique» la normalización y rompa la comparación con lo que escribe un teclado (NFC).
- Palabras distintas tras normalizar: unas 10 937 (las tres listas más la de «Salud»). La palabra más larga de las listas tiene 9 letras, con «contrasena» (10) de «Salud» el máximo es 10, y la más corta tiene 3.

## Tests y resultados

Antes (informe de la ronda 1): 1230 app, 1225 jvm-check. Después: **1244 app (0 fallos, 0 errores, 0 omitidos) y 1239 jvm-check (0 fallos)**. Suben 14 en cada uno: `MasterPolicyTest` pasa de 17 a 31 tests.

Tests nuevos (`app/src/test/java/app/seguridad/vault/MasterPolicyTest.kt`):
- `masterCore`: «Contraseña2024!» da «contrasena», «Ábaco-12345» da «abaco», la misma con acento combinante, «ÑANDÚ 99», espacios y guiones, un emoji, solo dígitos y vacío; y no depende del idioma del móvil (con `tr-TR` por defecto, «ILOVEYOU12» da «iloveyou»).
- `masterWords`: normaliza, quita vacías, contiene la lista de «Salud» y las tres listas reales enteras (más de 9000 palabras).
- `MASTER_WORD_LISTS` son los tres ficheros de `assets/`, y EFF no tiene dígitos.
- Con las palabras de las tres listas reales: `MASTER_TOO_WEAK` con «contraseña123», «Contraseña2024», «Password1!», «iloveyou12», «abandon1234», «Ábaco12345» (también con el acento combinante) y más variantes (`ABANDON-2024!`, `1234abandon`, `a.b.a.n.d.o.n.1`, `Admin12345`, `Abc1234567`…); y **todas las palabras de la lista EFF con 7 letras o más con «123» detrás**, tal cual y con mayúscula inicial.
- «Lo que la estimación deja pasar y la palabra atrapa»: «contraseña123», «Contraseña2024», «Password1!», «iloveyou12» tienen ≥ 50 bits, dan `null` sin palabras y `MASTER_TOO_WEAK` con ellas.
- El orden (longitud, estimación, palabra), el límite aceptado (dos palabras juntas, frases con espacios, «Barcelona1» con la guarda «barcelona» no está en ninguna lista) y «sin palabras hace lo de antes» (16 candidatos, `newMasterProblem(c)` y `newMasterProblem(c, emptySet())` iguales a la regla de antes).
- La lista de «Salud» que lee la regla es la que `estimateBits` da 0 bits.
- `loadMasterWords`: lee los tres ficheros en orden, cierra los tres flujos, da el mismo conjunto que `masterWords`; con un fichero que falla, con un `IOException` y con una `IllegalStateException` da `emptySet()`; y con ese conjunto «contraseña123» pasa (la regla vuelve a la estimación).
- Las 200 frases de 5 palabras por lista y separador, las de mayúsculas y número, y las contraseñas de 16 caracteres se comprueban ahora con y sin palabras (`assertAccepted`).
- Un test que fija el precio de la regla: «!7#Act4$9&2@1» (letras: «act») pasa la estimación y se rechaza con las palabras.
- Código fuente: las dos pantallas llaman una vez a `newMasterProblem(a, words…`, `CreateVault` carga `rememberMasterWords()` y el botón lleva `words != null`, el diálogo solo carga con `newPassword` y `ok` espera solo en esa rama; `MasterWords.kt` usa `LaunchedEffect(Unit)`, `withContext(Dispatchers.IO)`, `loadMasterWords`, no tiene `rememberSaveable`, `Bundle` ni `catch`, y ninguna otra pantalla nombra las listas salvo las dos que ya las leían.

### Evidencia TDD

RED 1 (los tests antes del código, no compilan):
```
$ gradle -p jvm-check test --tests '*MasterPolicyTest*' --offline --no-daemon --max-workers=1
e: .../MasterPolicyTest.kt:110:36 Unresolved reference 'masterCore'.
e: .../MasterPolicyTest.kt:132:21 Unresolved reference 'masterWords'.
e: .../MasterPolicyTest.kt:139:22 Unresolved reference 'MASTER_WORD_LISTS'.
e: .../MasterPolicyTest.kt:156:102 Too many arguments for 'fun newMasterProblem(candidate: String): String?'.
... (compileTestKotlin FAILED)
```
RED 2 (solo `MasterPolicy.kt` y `Health.kt` hechos, sin tocar la interfaz; los de comportamiento ya pasan, los de código fuente fallan):
```
MasterPolicyTest > the two screens pass the loaded words to newMasterProblem and wait for them FAILED
    java.lang.AssertionError: VaultScreen.kt passes the loaded words
MasterPolicyTest > the words are loaded once per screen, off the main thread, and never saved FAILED
30 tests completed, 2 failed        BUILD FAILED in 28s
```
(Un tercer fallo, de una aserción mía con `loadMasterWords(` en lugar de `loadMasterWords {`, se corrigió en el test.)

GREEN (con `MasterWords.kt` y las dos pantallas):
```
$ gradle -p jvm-check test --tests '*MasterPolicyTest*' --offline --no-daemon --max-workers=1
BUILD SUCCESSFUL in 20s      (31 tests, 0 fallos)
```

Mutaciones (cada una se revierte con `cmp` contra una copia; el diff final es el del commit):
| Mutación | Tests que fallan |
|---|---|
| `Normalizer.Form.NFD` por `NFC` | `the core of a password is its letters…`, `the words are the cores of the lists…` |
| `newMasterProblem` sin la comprobación de la palabra | `what the estimate lets through…`, `one known word is refused…`, `a random password whose only letters…` |
| `masterWords` sin la lista de «Salud» | `what the estimate lets through…`, `one known word…`, `the words are the cores…` |
| El botón de crear sin `words != null` | `the two screens pass the loaded words…` |
| El diálogo de importar también carga las palabras | `the two screens pass the loaded words…` |

### Verificaciones globales (una vez, antes del commit)
```
$ gradle -p jvm-check test --offline --no-daemon --max-workers=1
1239 PASSED, 0 FAILED   BUILD SUCCESSFUL in 1m   (MasterPolicyTest: 31)
$ ./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets --no-daemon --max-workers=1 --offline
1244 tests, 0 fallos, 0 errores, 0 omitidos   BUILD SUCCESSFUL in 4m 9s   (lint: 0 errors, 10 warnings, ninguna en estos ficheros)
```
`SourceTextsTest` y `ToolTextsTest` pasan: no hay cadenas de usuario nuevas.

## Archivos cambiados
- Nuevo: `app/src/main/java/app/seguridad/ui/screens/MasterWords.kt`
- `app/src/main/java/app/seguridad/vault/MasterPolicy.kt` (`masterCore`, `masterWords`, `loadMasterWords`, `MASTER_WORD_LISTS`, `newMasterProblem` con `words`, KDoc)
- `app/src/main/java/app/seguridad/health/Health.kt` (`commonPasswords()`)
- `app/src/main/java/app/seguridad/ui/screens/VaultScreen.kt`, `app/src/main/java/app/seguridad/ui/screens/VaultBackup.kt`
- `app/src/test/java/app/seguridad/vault/MasterPolicyTest.kt`

## Autorrevisión del diff
- La regla nueva está en `vault/` como función pura; la interfaz solo carga y pasa. Lo que no pasa por `newMasterProblem` (abrir, huella, `importReplace`, el diálogo de importar) no carga ni espera las listas.
- Con el conjunto vacío (lista no leída o fallo), `newMasterProblem` ni siquiera calcula `masterCore` (`words.isNotEmpty() &&`).
- Mientras `words == null` el texto rojo ya sale con la longitud y la estimación; solo el botón espera. Esa ventana dura unos milisegundos (12 000 líneas de `assets/` en `Dispatchers.IO`).
- Sin `rememberSaveable`, sin `Bundle`, sin logs, sin `catch` dentro de Compose; `fill('\u0000')` de `CreateVault` y del cambio de maestra intactos.
- Las listas se leen cada vez que se abre «Crea tu bóveda» o «Nueva contraseña maestra» (no se guardan entre pantallas, como pide el encargo): el coste es una lectura de unos 150 KB.

## Concerns

1. **El Generador puede proponer, muy de vez en cuando, una contraseña que la regla nueva rechaza.** Es una consecuencia de la regla pedida: una contraseña aleatoria de 16 caracteres con las cuatro clases cuyas pocas letras (3 a 5) forman una palabra de las listas («!7#Act4$9&2@1») tiene como núcleo «act». **Cifras corregidas en la ronda 3** (las de la ronda 2, 1 de cada 27 600 a 16, eran el doble de pesimistas: trataban cada posición como independiente y el Generador fuerza una minúscula y una mayúscula): cálculo exacto sobre las 10 937 palabras con el algoritmo real del Generador, **1 de cada 61 000 a 16 caracteres, 1 de cada 3 900 a 12 y 1 de cada 1,1 millones a 20** (el largo por defecto); una tanda de 200 contraseñas fallaría una vez de cada 308 ejecuciones. Las frases de 5 palabras no pueden fallar nunca (≥ 15 letras, y ninguna palabra conocida pasa de 10: «contrasena», de «Salud»; las listas llegan a 9). El usuario pulsa «↻ Otra». Lo declaro porque el punto 3 de «Puntos que revisar» dice «siempre pasa»: con la ampliación es «casi siempre» para contraseñas de caracteres. Los tests de 16 caracteres usan una secuencia fija (`SHA1PRNG` con semilla) para no fallar por azar una vez de cada 308 ejecuciones; el caso rechazado está fijado en su propio test, y el KDoc lo dice. Si el controlador prefiere otra cosa (por ejemplo, exigir un largo mínimo del núcleo, de modo que una palabra de 3 letras no cuente), es decisión del dueño: no lo he cambiado.
2. **Límite aceptado, declarado en el KDoc y en un test:** «una sola palabra» es el núcleo entero. Dos palabras juntas («abandon-ability»), un nombre propio o una ciudad que no esté en las listas («Barcelona1») y una palabra con un error tipográfico pasan si la estimación llega a 50 bits. La regla sigue sin garantizar una maestra fuerte; la política y la ficha no deben decir que la app «comprueba que la maestra sea fuerte».
3. **Copia transitoria del texto:** `masterCore` crea un `String` en minúsculas, otro descompuesto y otro filtrado en cada recomposición del campo, aparte de la de `estimateBits` (ronda 1, Concerns 3). El campo ya es un `String` inmutable del estado de Compose, que no se puede poner a cero; es el mismo riesgo residual y no una clase nueva. Está dicho en el KDoc de `masterCore`.
4. **Si la lectura de las listas falla se pierde la regla de la palabra** (decisión del dueño y del diseño; el fallo es casi imposible con un asset del propio APK, y `checkAssets` comprueba que van). No se avisa al usuario: sería un texto nuevo para el Abogado Digital y un caso que no ocurre en la práctica.
5. **El comportamiento en el móvil no se ha visto** (la interfaz no se ejecuta aquí). Para la prueba del dueño: en «Crea tu bóveda» y en «Cambiar contraseña maestra», escribir «contraseña123», «Password1!» y «Ábaco12345» debe sacar el texto rojo de «fácil de adivinar» y dejar el botón apagado, y una frase de 5 palabras debe dejarlo encendido; el botón debe encenderse sin retraso perceptible. Abrir la bóveda e importar una copia cuya maestra sea «contraseña123» tiene que seguir funcionando.

## Revisión experto-seguridad-cripto

Lista de `references/` (contraseñas y KDF, gestión de claves, secretos en memoria, entradas manipuladas, compatibilidad) aplicada al diff:
- **Amenaza cubierta:** quien tiene una copia `.sgbak` y prueba, sin límite de intentos, las contraseñas de un diccionario con las variaciones habituales (mayúscula inicial, número al final, símbolo). Una maestra que es una palabra de las listas EFF/BIP-39 o de la lista de las más usadas, con esas decoraciones, es el primer sitio donde un atacante busca; ahora se rechaza al crearla (`vault/MasterPolicy.kt:101-106`, `masterCore` en `:34-44`). Referencia: NIST SP 800-63B §5.1.1.2 (rechazar secretos que están en listas de comunes o de palabras de diccionario) y OWASP ASVS V2.1.7; con las diferencias declaradas (listas de unas 11 000 palabras, no una lista de millones de contraseñas filtradas; ni el nombre del usuario).
- **Normalización:** `Locale.ROOT` (no depende del idioma del móvil; test con `tr-TR`), NFD con `\p{Mn}` fuera (el teclado da NFC y la lista española está guardada en NFD: sin NFD la comparación fallaría, y un test lo fija), letras por punto de código. La lista se normaliza igual que el candidato, así que es simétrica (`MasterPolicy.kt:34-44,51-56`).
- **Solo contraseñas nuevas:** el único llamador con palabras es `CreateVault` (`ui/screens/VaultScreen.kt:242,253`, botón en `:270`) y el `PasswordDialog` con `newPassword = true` (`ui/screens/VaultBackup.kt:233-235`). El diálogo de importar no carga ni espera (`val words = if (newPassword) … else null`, `ok` con `a.length >= MIN_MASTER` en la otra rama). Abrir la bóveda, la huella e `importReplace` no pasan por la regla: una copia con maestra «contraseña123» sigue importándose. Un test de código fuente lo fija.
- **Sin cambio de formato ni de KDF:** `VaultStore`, `Vault.kt`, `Crypto.kt`, los datos de prueba y los formatos no se tocan (1239 y 1244 tests, con los de compatibilidad, en verde).
- **Secretos en memoria:** sin `rememberSaveable`, `Bundle` ni logs; las listas no son secretas y viven en un `remember` de la pantalla. Copias transitorias del candidato declaradas (Concerns 3).
- **Fallo seguro hacia el uso:** si no se leen las listas la regla se queda en la estimación en vez de dejar a alguien sin poder crear la bóveda (decisión del diseño); no hay otra puerta que se abra por ese fallo, porque la longitud y la estimación siguen.
- **Sin permisos, red, dependencias ni componentes nuevos;** `FLAG_SECURE` y `FilterObscuredTouches` como estaban. Los tres ficheros ya iban en el APK (`checkAssets`).
- **Honestidad del alcance:** el KDoc y los tests dicen lo que la regla no cubre (dos palabras, palabras que no están en las listas, ciudades). Concern 1 es una consecuencia real de la regla, medida y dicha.

## Revisión Ingeniero Móvil Senior

- **Arquitectura:** la lógica (normalizar, construir el conjunto, leer las listas con un `open` inyectado) está en `vault/`, sin Android y probada en la JVM, incluidos el fallo y el cierre de flujos (`MasterPolicy.kt:34,51,64`); la interfaz solo llama (`ui/screens/MasterWords.kt:24-28`). `Health.kt` solo gana un acceso `internal` de una línea, sin tocar el comportamiento de «Salud».
- **Concurrencia:** lectura en `Dispatchers.IO` dentro de `LaunchedEffect(Unit)`, que se cancela al salir de la composición; el estado se escribe en el hilo principal; nada bloquea la interfaz (`MasterWords.kt:27`). Se captura `Exception` en `loadMasterWords` (código bloqueante, sin `suspend`), no en el composable.
- **Compose:** `remember` (no `rememberSaveable`) y `null` hasta cargar; el valor devuelto por un `@Composable` se lee en el cuerpo del llamador, que se recompone al llegar. En `PasswordDialog`, la llamada condicional a `rememberMasterWords()` depende de un parámetro que no cambia durante la vida del diálogo; solo el diálogo de la maestra nueva lee `assets/`. `problem`/`ok` se derivan en cada recomposición (sin estado derivado guardado que se quede atrasado). Sin cambios en la accesibilidad de los textos (región viva de la ronda 1).
- **Ciclo de vida y giro:** al girar la pantalla las listas se vuelven a leer (la contraseña ya se perdía con el giro, como antes) y el botón queda apagado unos milisegundos; sin `ViewModel` nuevo ni estado que sobreviva.
- **Rendimiento:** unas 11 900 líneas leídas una vez por apertura de la pantalla, un `HashSet` de unas 10 900 cadenas (≈ 1 MB, liberado al salir de la pantalla); cada pulsación hace una pasada corta de `masterCore` y una búsqueda en el conjunto.
- **Pruebas:** comportamiento en la JVM (los lectores de `assets/` se encuentran desde `app/` o `jvm-check/` con la misma ayuda que el resto de tests del repositorio) y el cableado de la interfaz como texto fuente, con la entrada `ui/**` de `jvm-check` comprobada. Mutaciones de los dos lados con los tests rojos (tabla de arriba). La prueba en el móvil sigue siendo del dueño (Concerns 5).

## Textos para el Abogado Digital (revisión final)

Ninguno nuevo. La ampliación reutiliza `MASTER_TOO_WEAK` (sin cambiar sus palabras) y no añade cadenas de usuario. **Esta nota reemplaza a la de la ronda 1** (la que decía que «contraseña123» y «Contraseña2024» pasan). La regla vigente, solo al crear la bóveda o al cambiar la maestra, es: **10 caracteres, una estimación de al menos 50 bits (`estimateBits`, la de «Salud») y que no sea una sola palabra de las listas de la app** (la lista de las más usadas de «Salud» y las listas EFF, BIP-39 inglés y BIP-39 español, sin mayúsculas, acentos, números ni símbolos). Por tanto «contraseña123», «Contraseña2024», «Password1!» o «Ábaco12345» **se rechazan**; «Barcelona1» (una palabra que no está en ninguna lista) y **dos palabras juntas** («abandon-ability») **siguen pasando** si la estimación llega. No es un diccionario completo ni una garantía de fuerza, y abrir la bóveda o importar una copia no pasan por la regla. La política y la ficha no deben prometer más (por ejemplo, que la app «comprueba que la maestra sea fuerte» o que rechaza todas las contraseñas débiles o comunes).

---

# Ronda 3 (re-revisión de la tarea 2: N1 y N2, más una entrada de `jvm-check`)

Estado: DONE. Commit: `b5c72b1` «vault: cifras y nombres de la ampliación de CRY-A01, y los assets como entrada de jvm-check», empujado a `ccr-a1a6eabb-765f9g` (`74b8f4a..b5c72b1`; el mensaje acaba solo en `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`). Cambios solo de texto, de un comentario de test y de una línea de `jvm-check/build.gradle.kts`; la regla y su comportamiento no cambian.

## Cambios

- **N1 (a), el nombre y el mensaje del test.** `MasterPolicyTest.kt`:
  - el test pasa de `a word with an accent or an enye gets a large bonus, so contrasena123 and Contrasena2024 pass` a `… so the estimate alone accepts contrasena123 and Contrasena2024`;
  - el mensaje de la aserción pasa de «is accepted today» a «is accepted by the estimate alone (no word lists)»;
  - el comentario de «Without the ñ…» y los KDoc de los tests de las líneas 59 y 82 dicen también «la estimación sola».
- **N1 (b), la nota del Abogado Digital.** En este informe:
  - la nota de la ronda 1 ya no describe la regla: lleva una frase que dice que la sustituye la nota de la ronda 2, y se quita la lista «pasan «Barcelona1», «contraseña123» y «Contraseña2024»»;
  - la nota de la ronda 2 es ahora autónoma y dice que **reemplaza** a la de la ronda 1. La regla: 10 caracteres, estimación ≥ 50 bits y que no sea una sola palabra de las listas de la app, solo al crear la bóveda o cambiar la maestra. «contraseña123» se rechaza; «Barcelona1» y dos palabras juntas pasan. Abrir e importar no pasan por la regla;
  - el punto 1 de la nota base también lleva un matiz («fácil de adivinar» ya no es solo la estimación).
- **N2, las cifras.** Reformuladas con las del revisor:
  - `vault/MasterPolicy.kt` (KDoc de `newMasterProblem`): «about 1 time in 3,900 at 12 characters, 1 in 61,000 at 16 and 1 in 1.1 million at 20 (the default length)», y «no known word has more than 10 [letters]: «contrasena», from «Salud»; the lists have 9 at most»;
  - `MasterPolicyTest.kt` (comentario de `fixedRandom`): «about 1 password in 61,000 of 16 characters» y «200 fresh draws would fail about one run in 308 by chance»;
  - este informe: Concern 1 de la ronda 2 (con la causa del error: cada posición se trató como independiente, y el Generador fuerza una minúscula y una mayúscula) y la línea de «la palabra más larga».
  - **Comprobado por mi cuenta antes de escribirlo** (Python sobre `app/src/main/assets`, con el algoritmo de `Generator.kt:29-39`: 4 posiciones forzadas, 12 libres con 52 letras de 89 caracteres, el núcleo es una palabra con probabilidad `26^-k` por longitud `k`): 1 de cada 3 897 a 12, 61 458 a 16 y 1 128 353 a 20; una tanda de 200 falla una de cada 308. Cuenta de palabras por longitud: 214 de 3 letras, 1020 de 4, 1924 de 5, 2253 de 6, 2097 de 7, 1872 de 8, 1556 de 9 y 1 de 10 («contrasena»). Coincide con el revisor.
- **Entrada de `jvm-check`.** `jvm-check/build.gradle.kts`: `inputs.dir("../app/src/main/assets")` (con `withPropertyName("appAssets")` y `PathSensitivity.RELATIVE`, el mismo estilo que la entrada de `ui/`), porque `MasterPolicyTest` lee las listas.

### Comprobación de la entrada de `assets/`
```
$ gradle -p jvm-check test --tests '*MasterPolicyTest*' --offline --no-daemon --max-workers=1     (tras los cambios)
> Task :test                                   BUILD SUCCESSFUL in 36s
$ (misma orden, sin cambios)
> Task :test UP-TO-DATE                        BUILD SUCCESSFUL in 9s
$ echo "" >> app/src/main/assets/bip39_english.txt ; (misma orden)
> Task :test                                   <- se ejecuta, solo cambió un asset
$ git checkout app/src/main/assets/bip39_english.txt                                               (restaurado)
```
`git status` no muestra ningún cambio en `app/src/main/assets/`.

## Verificaciones globales (una vez, antes del commit)
```
$ gradle -p jvm-check test --offline --no-daemon --max-workers=1
1239 PASSED, 0 FAILED   BUILD SUCCESSFUL in 1m 2s   (MasterPolicyTest: 31)
$ ./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets --no-daemon --max-workers=1 --offline
1244 tests, 0 fallos, 0 errores, 0 omitidos   BUILD SUCCESSFUL in 3m 8s   (lint: 0 errors, 10 warnings, las de siempre)
```
Los conteos no cambian respecto de la ronda 2 (1239 jvm-check, 1244 app): esta ronda no añade ni quita tests.

## Archivos cambiados
- `app/src/main/java/app/seguridad/vault/MasterPolicy.kt` (solo KDoc)
- `app/src/test/java/app/seguridad/vault/MasterPolicyTest.kt` (nombre, mensaje y comentarios)
- `jvm-check/build.gradle.kts` (una entrada de `tasks.test`)
- este informe (no está en git: `.superpowers/sdd/.gitignore`)

## Revisión experto-seguridad-cripto
- Sin cambio de regla, de formato, de KDF, de secretos, de red ni de permisos; solo se corrigen cifras y se aclara qué acepta «la estimación sola» (`MasterPolicy.kt:95-100`, `MasterPolicyTest.kt:90,94`).
- La nota para el Abogado Digital deja de afirmar algo falso sobre el código («contraseña123» pasa): hoy se rechaza al crear la bóveda y al cambiar la maestra (`MasterPolicy.kt:105`), y la nota lo dice con sus límites («Barcelona1», dos palabras juntas).

## Revisión Ingeniero Móvil Senior
- Build: la entrada nueva es una `inputs.dir` relativa, como la de `ui/`; comprobado que cambiar solo un asset hace que `test` se ejecute y que sin cambios sale `UP-TO-DATE` (`jvm-check/build.gradle.kts:47-50`).
- Sin cambios de interfaz, ciclo de vida ni concurrencia.

## Textos para el Abogado Digital (revisión final)
Ninguno nuevo. La nota vigente es la de la ronda 2 (ya corregida para reemplazar a la de la ronda 1).
