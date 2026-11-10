# Tarea 12 (1.4.0): repaso de la ronda 1 de arreglos

**Revisor:** el de `task-12-review.md`. **Alcance:** `ee30fee..4929ef6`. `1eb33ae`, que va encima, solo añade líneas al registro. Para el contexto leí R31 a R33 y la decisión (g); los hallazgos RT-1 a RT-3, R34 y (h) no entran en este repaso.
**Veredicto: Needs fixes.** Hay 1 menor nuevo (N-1). Los 21 hallazgos de la revisión (I-1 a I-5 y M-1 a M-16) están arreglados y lo que dicen es verdad.

## Hallazgos de la revisión

| Hallazgo | Estado | Evidencia |
|---|---|---|
| I-1 | ✅ | P-4 (`README.md:343`): una foto o un PDF dan «El archivo está dañado o no es texto.» y un `.txt` cualquiera, «Clavero no reconoce este archivo.». Es lo que da `imports/Detect.kt:47-48` (con `imports/TextDecode.kt:19-27`), y coincide con lo que comprobé ejecutando `readImport` en la revisión. |
| I-2 | ✅ | P-12 (`README.md:351`): el archivo empieza por `{` y la prueba espera el texto exacto `IMPORT_JSON_TOO_MANY_VALUES`. Es lo que da `imports/ImportJson.kt:196-197` (comprobado en la revisión con el mismo archivo). |
| I-3 (R33) | ✅ (con N-1) | `imports/ImportTexts.kt:218-222` y `:394-397`, fijados con el texto exacto y prohibiendo las frases de antes (`ImportTextsTest.kt:169-186`). Lo mismo dicen `README.md:35`, `docs/ficha-google-play.md:61`, la nota de GitHub y la auditoría §1.2 y §6. La veracidad, abajo. |
| I-4 | ✅ | `README.md:38` dice «si la contraseña la abre y sus entradas se leen» y «No prueba lo que solo hace la importación…». Coincide con `BACKUP_CHECK_NOT_PROVEN` y con `vault/BackupCheck.kt:36-39`. |
| I-5 | ✅ | `docs/ficha-google-play.md:28`, las «Novedades» del traspaso y la auditoría y el traspaso §1 dicen «algunos gestores». En la ficha y en las «Novedades» no sale ninguna marca nueva. La única es `:32`, que ya estaba y decide R30. |
| M-1 | ✅ | `CLAUDE.md`: el bloque de la 1.4.0 está en `:62`, antes de los de la 1.3.x (`:92`, `:118`, `:124`); «(tarea 1, arriba)»; no quedan líneas en blanco dobles (comprobado con awk). |
| M-2, M-3 | ✅ | `CLAUDE.md:140` dice P-4 a P-20; `:147` tiene el verbo y nombra los AAB de la 1.3.1 y de la 1.4.0. |
| M-4 | ✅ | `CLAUDE.md` ya tiene R4, R5/R6, R23, R25, R30, R31 y R33. La auditoría §3 llega a R33, y el traspaso dice «R1 a R33» y «(a) a (g)». |
| M-5 (R31) | ✅ | `CLAUDE.md:71` y «Reglas del proyecto»: texto visible de la app, lo publicado (README y notas de GitHub, con la línea de marcas), nunca la ficha ni las «Novedades», y los documentos técnicos pueden nombrarlas. Coincide con R31 en `progress.md:217`. |
| M-6 (R32) | ✅ | La tabla de `CLAUDE.md:54,57`, (d) y R32 en `CLAUDE.md`, el traspaso §3 (puntos 1 a 3) y §4, y la auditoría §1.3, punto 2, coinciden con R32 (`progress.md:218`). |
| M-7 | ✅ | `CLAUDE.md:53`, la fila 12 de la auditoría y el traspaso §1. |
| M-8 | ✅ | `README.md:35`, `:52`, P-8 y P-15 a P-17 ya dicen «(1 minuto como mínimo)», como `ImportTexts.kt` (`IMPORT_WIPE_NOTE`) y `tools/ToolTexts.kt:628-630`. |
| M-9 | ✅ | `README.md:50` dice «en número par» (`tools/slip39/SecretShareInput.kt:35`). |
| M-10 | ✅ | P-11 (`README.md:350`): la huella se prueba antes del cambio y después queda desactivada (`ui/screens/VaultBackup.kt:128-131`). |
| M-11, M-12 | ✅ | P-17 «anota cuánto tarda»; P-6 «sus recuentos de entradas son los de antes de importar». |
| M-13, M-14 | ✅ | El traspaso §1 dice «Esa sesión se cortó…»; la auditoría §1.2 separa lo que dice la pantalla de lo de las cadenas de Kotlin. |
| M-15 | ✅ | En `README.md:108-111` ya solo queda `./gradlew -p jvm-check`. |
| M-16 | ✅ | Era una propuesta para el Abogado: está en la lista de `task-12-report.md` (A.7), igual que los nombres ajenos que ya estaban en la política (A.8) y la nota de la fecha (A.10). `docs/privacidad.html` solo cambia en la frase de R15, como manda R30. |

## Veracidad de los dos textos nuevos (R33) frente al código

«Si lo protegiste con contraseña, sus entradas siguen cifradas con esa contraseña» es verdad para los tres formatos con contraseña que Clavero lee. Lo comprobé en los lectores y en los archivos de prueba:
- **Bitwarden «Protegida con contraseña»:** todo el contenido va en `data`, un EncString cifrado con una clave sacada de la contraseña con PBKDF2 o Argon2id y HKDF (`imports/BitwardenCrypto.kt`, `imports/ImportKdf.kt`). En claro solo quedan `salt`, los parámetros de la KDF y `encKeyValidation` (`bitwarden-protegido-pbkdf2.json`).
- **Aegis cifrado:** `db`, con todas las entradas, va cifrado con AES-GCM con la clave maestra. La ranura de contraseña la envuelve con scrypt (`imports/AegisCrypto.kt`). En claro solo quedan la cabecera y las ranuras (`aegis-cifrado.json`). Si Aegis tiene otra ranura (la de su huella), esa clave abre lo mismo, pero eso es de Aegis y el texto sigue siendo verdad.
- **2FAS con contraseña:** las entradas van en `servicesEncrypted` (PBKDF2 y AES-GCM, `imports/TwoFasCrypto.kt`), y `services` queda vacío. Los **nombres de los grupos** (`groups`) quedan en claro (`2fas-cifrado.2fas`; `task-10-report.md:97`). Los grupos no son entradas, así que el texto es verdad; lo apunto para el Abogado.
- «Si lo exportaste sin contraseña, está sin cifrar en tu móvil» es verdad para todo lo que Clavero importa en claro: los CSV, los JSON en claro, los TXT y las listas. Hay una excepción, en N-1.
- `IMPORT_REMOVE_EXPLAIN` («sin cifrar si lo exportaste sin contraseña») solo sale después de una importación terminada (`ui/screens/ImportScreen.kt:619-621`), así que la excepción de N-1 no le afecta.

## Nuevo

### Minor

**N-1. `IMPORT_NOT_PROTECTED` contradice al mensaje de Bitwarden «Cuenta restringida» en la misma pantalla.** `IMPORT_NOT_PROTECTED` sale en todos los pasos, también en el de error (`ui/screens/ImportScreen.kt:541-547`). Si se elige una exportación de Bitwarden «Cuenta restringida» (`imports/BitwardenJson.kt:38`), salen juntos dos textos:
- arriba, «Esta exportación está cifrada con tu cuenta de Bitwarden…»;
- debajo, «Si lo exportaste sin contraseña, está sin cifrar en tu móvil».

Ese archivo se exportó sin contraseña de archivo y está cifrado. La afirmación general es falsa en un caso que la app sí enseña.
*Arreglo* (el pin de `ImportTextsTest.kt:172-178` se cambia a la vez): «Clavero no protege el archivo exportado. Un archivo exportado sin contraseña suele estar sin cifrar en tu móvil; si lo protegiste con contraseña, sus entradas siguen cifradas con esa contraseña. En los dos casos puede haber copias en la nube, en la papelera o en otras apps. Al terminar, Clavero puede intentar borrar ese archivo (algunas apps no lo dejan), pero no esas copias.» Así dice lo mismo que la ficha («suele estar sin cifrar, salvo si lo proteges con contraseña»). Otra opción es no enseñar `IMPORT_NOT_PROTECTED` en el paso de error de ese archivo.

## Nada roto

- Volví a ejecutar con `--rerun`, sobre `1eb33ae`, `PublishedTextsTest`, `AppNameTest`, `FinalLegalReviewTest`, `SourceTextsTest`, `ImportTextsTest` y `ToolTextsTest`: 7 + 8 + 14 + 12 + 13 + 22 = 76 tests, 0 fallos. Según el registro, la suite entera da 1751 en la app y 1746 en `jvm-check`.
- **Palabras prohibidas** (`ForbiddenWords.kt:5-7`, más «a prueba de cuánticos» y «como Signal»): en todas las líneas añadidas de la ronda no hay ninguna. Los seis «garantiza» van detrás de «no».
- **Marcas (R31):** el código solo cambia dentro de `imports/ImportTexts.kt`, que es el sitio permitido. La ficha y las «Novedades» no tienen nombres nuevos. La nota de GitHub los usa con la línea de marcas. «Android qa libre», en el README y en `CLAUDE.md`, es el plugin del dueño (decisión (g)), no una app de la que se lea un formato.
- **Correos y secretos:** la ronda no añade ninguno.
- **El tiempo del README sobre `.maestro/`** («irán»): es futuro, y es verdad. Según el traspaso §3, paso 2, los flujos «todavía no están».

## Revisión experto-seguridad-cripto

- Los dos textos de R33 ya no dicen que una exportación cifrada esté en claro. Lo que afirman de cada formato coincide con lo que dejan en claro sus lectores: Bitwarden, solo la KDF y la sal; Aegis, la cabecera y las ranuras; 2FAS, los nombres de grupo (`imports/BitwardenCrypto.kt`, `imports/AegisCrypto.kt`, `imports/TwoFasCrypto.kt`).
- Ninguno promete protección: los dos empiezan por lo que Clavero no protege (`imports/ImportTexts.kt:218-222`, `:394-397`).
- N-1 es de veracidad, no de seguridad: exagera el riesgo, no la protección.
- No cambia nada de criptografía ni de formatos.

## Revisión Ingeniero Móvil Senior

- El único código que cambia son dos constantes de texto y su pin (`ImportTextsTest.kt:169-186`, primero en rojo y luego en verde, según el informe). La pantalla no cambia: `ui/screens/ImportScreen.kt:545,621` sigue usando las mismas constantes.
- Las pruebas del móvil P-4, P-11 y P-12 ya esperan lo que da el código. La decisión (g) (el emulador o el móvil por USB en el ordenador del dueño, sin `/dev/kvm` aquí) está en `CLAUDE.md`, el README y el traspaso, y todos dicen lo mismo.
- No cambian el manifiesto, los recursos ni las dependencias.

## Textos para el Abogado Digital (revisión final)

1. `IMPORT_NOT_PROTECTED` e `IMPORT_REMOVE_EXPLAIN` en su versión de R33 (`imports/ImportTexts.kt:218-222`, `:394-397`), con el cambio de N-1 cuando se aplique.
2. Para saber: con una copia de 2FAS protegida con contraseña, los nombres de sus grupos quedan en claro en el archivo. Si el Abogado quiere que el texto lo diga, «sus entradas siguen cifradas» ya es verdad y basta añadir «(los nombres de grupo de 2FAS, no)».
3. `docs/ficha-google-play.md:28` («de los navegadores más usados … de algunos gestores … (la lista está en la app y en el código fuente)») y `:61` («suele estar sin cifrar, salvo si lo proteges con contraseña»).
4. El párrafo de las pruebas en el móvil del README (`:338`), que cita el plugin «Android qa libre» del dueño.
