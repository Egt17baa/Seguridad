# Re-revisión de la tarea 6, ronda 1 (`ba572c0..875f037`)

### Finding Verdicts

1. **`Vault.kt:246`, comentario «Not the default…»** — ADDRESSED. Ahora dice `// Required (no default), so it is always written out.` (`Vault.kt:246`), y es verdad: `format` no tiene valor por defecto (`Vault.kt:118`).
2. **`Vault.kt:332`, línea de 166 caracteres** — ADDRESSED. El primer párrafo del KDoc de `afterMasterUnlock` está partido otra vez en `Vault.kt:327-339`; medido en caracteres, la más larga mide 120 (`:334`, `:336`). El texto es el mismo, solo recolocado. Confirmado lo que dice el informe: `Vault.kt:343` (párrafo CR-2, sin tocar) mide 126.
3. **`Vault.kt:122-124`, KDoc de `version` contradictorio** — ADDRESSED. `Vault.kt:119-125`: «This app always writes it: 2 (1.0 and 1.1) or 3. A file without it ([VERSION_UNKNOWN], edited by hand) is tried as both…». Concuerda con `Vault.kt:151` («What 1.1 wrote…») y con `wrap`, que siempre escribe `version` (`Vault.kt:247`).
4. **`Vault.kt:399`, guarda `version == 1` arbitraria** — ADDRESSED.
   - Código: `!supported(file) -> BiometricRefusal.MASTER_DUE` (`Vault.kt:401`), con el KDoc de `biometricAllowed` reescrito (`Vault.kt:388-396`).
   - `supported` es `format == FORMAT && version in {0, 2, 3}` (`Vault.kt:166-167`). Cambia solo lo que antes llegaba a `opened` → `read` y fallaba: versión 4, -1 y cualquier otra. La 1 y otro formato siguen en `MASTER_DUE`. La versión 0 (sin `version`), la 2 y la 3 no cambian: pasan a las ramas de reloj y de 7 días, como antes.
   - Test `VaultV3Test.kt:682` `biometricRefusalAsksForTheMasterForAVersionOrFormatThatDoesNotOpen`: versiones 1, 4 y -1 y otro `format` dan `MASTER_DUE`; 0, 2 y 3 dan `null`; y por la tienda, un `vault.bin` con versión 4 da `MasterRequired(MASTER_DUE)`, no se abre y el archivo queda igual. El test de la 1 (`VaultV3Test.kt:676`) sigue.
   - Comprobado por mutación en una copia del commit (`git archive 875f037`, fuera del repositorio): con la guarda vieja (`format != FORMAT || version == 1`) el test falla con `version 4 expected:<MASTER_DUE> but was:<null>`; con la nueva pasa. `VaultV3Test` entero, 34 tests: PASSED.
   - Llamadores, comprobados en el código:
     - `VaultScreen.kt:310` decide el botón y el aviso de la huella con `store.biometricAllowed` sobre el archivo tal como está en disco (`VaultStore.kt:425`), antes de pedir la clave. `VaultScreen.kt:352` solo elige el texto.
     - `VaultStore.kt:454`, en `unlockWithKeyLocked`: `resolve` no usa la clave si el archivo dice su versión (`Vault.kt:304`), y la decisión llega antes de `opened`. Un `MasterRequired` no llega a `onWrongKey` (`VaultStore.kt:87-102`), así que no desactiva la huella.
     - `unlockWithKey` solo lo llama `VaultScreen.kt:318`. No hay ningún otro llamador que pida la maestra donde no debe.
5. **`DialectTest.kt:24-25`, procedencia de `criptopanel/ref.cpenc`** — ADDRESSED. `DialectTest.kt:22-31`, comprobado dato a dato:
   - Los ocho `CP1:` de `texts.json` son de «CriptoPanel 1.10.0 (bac076a)» (campo `generator`), y su lista `files` nombra `file-aes.cpenc` y `file-cascade-rawkey.cpenc`, que ya no existen: dicho en el KDoc.
   - `ref.cpenc`: `CP`, versión 1, archivo, AES-256-GCM, Argon2id, 1024 KiB, 1 pasada, 1 carril (leído de la cabecera); nombre sellado de 27 bytes (= «foto ñ.jpg», 11 bytes UTF-8, más 16 de etiqueta); 65 578 bytes de registros (= 65 536 + 10 más dos etiquetas): dos registros de 65 546 bytes. Contraseña «pw» (el test borrado `CryptoTest`).
   - Llegó con `c70f932` (`git log --diff-filter=A`), cuyo `CryptoTest` lo llama «written by an independent implementation» (`c70f932:…/CryptoTest.kt:120`). El KDoc dice que el repositorio no guarda qué lo escribió, que es lo cierto.
   - Los datos de `criptopanel/` no cambian: `git diff --stat ba572c0..875f037 -- app/src/test/resources` da 0 líneas. `ls` muestra los 4 archivos que había.
6. **Se pierde la única comprobación independiente del contenedor** — ADDRESSED.
   - Test `DialectTest.kt:128` `SG1 texts and sgenc files written outside Clavero with Python cryptography open`:
     - texto `SG1:` con contraseña, AES-256-GCM, Argon2id m = 1024 KiB, t = 2, p = 2 (el test comprueba que se leen de la cabecera, `:134-135`);
     - `.sgenc` con ChaCha20-Poly1305, Argon2id m = 2048 KiB, t = 1, p = 1, nombre «informe ñ.txt» y 300 bytes de datos, un registro;
     - se abren con `inspectText`, `decryptText` y `decryptStream`, la API pública (`Header.parse` solo se usa para comprobar parámetros).
   - **Generador**: el informe lo cita entero (sección «El generador…», líneas 374-481 de `task-6-report.md`). Lo ejecuté con `python3 -I` en `…/scratchpad/rereview6/` con `cryptography` 50.0.1 (el mismo que dice el informe):
     - termina con código 0, y comprueba antes la librería contra el vector RFC 9106 de Argon2id;
     - el `TOKEN=` y el `SGENC=` que imprime son **idénticos carácter a carácter** a las constantes de `DialectTest.kt` (comparados con un script, 512 caracteres de Base64 en el `.sgenc`);
     - la parte final del script abre con Python los vectores `t1` y `f1` de la 1.3.0 de `DialectTest.kt:81` y `:94`, que son las mismas cadenas (comparadas): imprime `CLAVERO_1_3_0_VECTORS_OPEN_IN_PYTHON=OK`.
   - Independencia: Argon2id, AES-GCM y ChaCha20-Poly1305 son de `cryptography`; sal, nonce y prefijo son bytes consecutivos fijos; el script solo usa la descripción del formato, no código de Clavero. No hay nada que indique que se ajustaran a Clavero: el test pasa con el código de antes de la ronda (RED del informe) y con el de ahora (lo repetí: `DialectTest` 7 tests PASSED).
   - Sin archivos nuevos en `app/src/test/resources/seguridad/` ni `vectors/` (el diff solo toca 4 archivos de código y el registro).
7. **`AppNameTest.kt:103`, solo literales enteros** — ADDRESSED. `AppNameTest.kt:108`: `readsIt(lit) = lit in formats || "CP1:" in lit || ".cpenc" in lit.lowercase()`. Hoy sigue en verde: `grep -rniE "cp1:|\.cpenc" app/src/main/java` no da nada. Comprobado por mutación en la copia fuera del repositorio: con dos literales nuevos «Elegir archivo .sgenc o .cpenc» y «empieza por SG1: o CP1:» el test falla y los nombra (`Mut.kt:3`, `Mut.kt:4`); sin ellos, `AppNameTest` entero (8 tests) PASSED.

### New Breakage in the Fix Diff

Ninguno que rompa nada. Un detalle cosmético:
- **Minor (cosmético):** el informe dice que los párrafos tocados tienen «como mucho 120 caracteres», pero `Vault.kt:391` y `:393` (el KDoc de `biometricAllowed`, reescrito en esta ronda) miden 121, y `DialectTest.kt:27-28` miden 122 y 123. No hay ninguna regla de longitud en el proyecto (`Vault.kt` ya tenía 33 líneas de más de 120 en la base y tiene 32 ahora), y ni la guarda ni el texto cambian. Se arregla al pasar por ahí, sin bloquear.

Comprobado y limpio:
- `!supported(file)` no cambia nada para la versión 0, 2 ni 3: el test lo fija con `assertNull` a `now + 1_000`, y los tests de huella con versión cambiada (`VaultV3Test` `deLaVersion3NoSePasaALa2PorLaHuella` y los híbridos) siguen esperando `CryptoFailure` y pasan.
- Ningún llamador pide la maestra donde no debe: solo cambia el caso de un archivo que la maestra tampoco abriría («No es una bóveda compatible»).
- El diff no toca `VaultStore.kt`, la pantalla ni ningún texto; las tres clases que repetí en una copia del commit (`DialectTest`, `AppNameTest` y `VaultV3Test`, con `gradle -p jvm-check test --offline`) pasan: `BUILD SUCCESSFUL`, sin ningún FAILED.

### Out-of-Scope Observations

1. `Vault.kt:343`: el párrafo CR-2 del KDoc de `afterMasterUnlock` (sin tocar por la ronda) mide 126 caracteres, como dice el informe.
2. `VaultStore.kt:447-454` (sin tocar): `unlockWithKeyLocked` llama a `resolve` antes de `biometricRefusal`. Con un `vault.bin` de **otro formato y sin `version`** (editado a mano), `resolve` sí usa la clave de datos y fallaría con `CryptoFailure`, que desactiva la huella. No se alcanza desde la interfaz: `VaultScreen.kt:310` no ofrece la huella con ese archivo (`biometricAllowed` es falso) y `unlockWithKey` solo se llama desde ahí. Era igual antes de la ronda. Informativo; si se quiere cerrar del todo, bastaría consultar `biometricRefusal(v, …)` antes de `resolve` y de nuevo después.
3. `VaultScreen.kt:352-354` (sin tocar): con `MASTER_DUE` la pantalla dice «cada 7 días hay que escribir la contraseña maestra», también para un archivo que la maestra no abre (versión 1, 4…). Ya pasaba con la 1 y con otro formato, y es el texto que decidió el controlador; después la maestra dice «No es una bóveda compatible». Para el Abogado Digital solo si se quiere afinar.

### Verdict
**Fix round:** All findings addressed, no new Critical/Important breakage. Los siete hallazgos están cerrados; abiertos: ninguno. Solo queda un detalle cosmético de longitud de línea (121 caracteres en `Vault.kt:391` y `:393`).

### Revisión experto-seguridad-cripto
- **Hallazgo 4 (huella).** Un `vault.bin` con una versión que la app no lee ya no llega a leer las entradas con la clave de datos: `Vault.kt:401` decide antes de `opened` (`VaultStore.kt:454`) y `resolve` no usa la clave cuando el archivo dice su versión (`Vault.kt:304`). Un `MasterRequired` no desactiva la huella (`VaultStore.kt:87-102`), así que no se pierde el segundo factor por un archivo manipulado. No cambia ningún formato ni ninguna AAD (`Vault.kt:160-163`, `:194`, `:200` quedan igual). Con la versión 0, 2 o 3 no cambia nada.
- **Hallazgo 6 (comprobación independiente).** El contenedor `SG1:`/`.sgenc` tiene otra vez un contraste con código distinto del de la app: Argon2id leído de la cabecera (con m, t y p que no son los de la app), AEAD con la cabecera entera como AAD, nonce STREAM (prefijo, contador y marca de último) y registro 0 del nombre (`DialectTest.kt:128-148`). El generador lo reproduje y da las mismas constantes; el script comprueba antes su Argon2id con el vector RFC 9106. Va en los dos sentidos (Python también abre los vectores de la 1.3.0). Límite honesto: un solo registro de datos; el contador por encima de 1 lo cubren las idas y vueltas.
- **Hallazgo 7.** La prueba ya no deja pasar un texto que vuelva a ofrecer `CP1:` o `.cpenc` (`AppNameTest.kt:108`), comprobado por mutación.
- **Sin degradación ni oráculos nuevos.** El diff de código de producción es una condición y comentarios (`Vault.kt`); no toca primitivas, parámetros, secretos en memoria, red, permisos, logs ni `FLAG_SECURE`.

### Revisión Ingeniero Móvil Senior
- **Ciclo de vida y huella.** El orden es correcto: la pantalla filtra con `biometricAllowed` antes del aviso (`VaultScreen.kt:310`) y la tienda decide antes de `opened` (`VaultStore.kt:454`). `supported` es una función privada pura, sin E/S ni derivar claves (`Vault.kt:166-167`), así que no añade coste al hilo de la interfaz.
- **Tests.** `VaultV3Test.kt:682` cubre la función y el recorrido por la tienda (estado, archivo igual en disco). `DialectTest.kt:128` y `AppNameTest.kt:106-110` fallan cuando deben (mutaciones hechas en una copia fuera del repositorio) y pasan hoy. No hay archivos de prueba nuevos ni dependencias nuevas.
- **Comentarios y KDoc.** Dicen la verdad sobre el código (`Vault.kt:119-125`, `:246`, `:388-396`); solo dos líneas de 121 caracteres, sin regla que las impida.
- **Repositorio.** Lectura solamente: las pruebas se hicieron sobre un `git archive` en el scratchpad; `git status --short` del repositorio queda vacío.
