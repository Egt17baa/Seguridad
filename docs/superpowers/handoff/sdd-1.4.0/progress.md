# SDD ledger — plan: docs/superpowers/plans/2026-11-03-clavero-1.4.0.md

Spec: docs/superpowers/specs/2026-11-03-clavero-1.4.0-design.md (aprobado por el dueño, 3-11-2026).
Base: main 7c8dea7 (PR #16 fusionado). Rama ccr-a1a6eabb-765f9g.
Owner decision (3-11-2026): ejecución por subagentes (SDD), «Ok» al plan.

## Pre-flight scan

| Tareas | Comparten | Hallazgo |
|---|---|---|
| T1 | KeyOpsQueue / BiometricVault / 6 llamadas | `VaultScreen.kt:323` está dentro del callback `onError` de `unlock`, no en una corrutina. Ruling abajo (R1). |
| T2–T3–T4 | `AppNavHost.kt`, `ToolsScreen.kt` | Secuenciales; cada una añade su ruta y su fila. Sin conflicto. |
| T5–T8 | `VaultStore.kt` | T5 toca `importReplace` (`:810`); T8 añade `applyImport` junto a `addAll` (`:642`). Sin conflicto. |
| T6→T7→T10 | `Detect.kt`, modelo `ImportRead`, `SkipReason`, `ImportFailure` | Interfaces coinciden. |
| T6/T7 vs Global «nombres ajenos solo en la pantalla de importar» y T9 `ImportTextsTest` («ningún nombre ajeno fuera de `ImportTexts.kt`») | El mensaje de Bitwarden cifrado con la cuenta (T7) y los nombres de formato viven en `imports/` | `ImportTexts.kt` no está en ninguna lista de archivos. Ruling R2. |
| T8→T9 | `ImportFlow.copy` | La copia por diálogo (sin carpeta) se pasa como otro lambda `copy`; `CopyResult` sirve igual. Sin conflicto. |
| T10 | `argon2id` (`Crypto.kt:221`), `checkSane` | Existen. Sin conflicto. |
| T11 | build | Independiente; revertible. |
| Commits | Global Constraints vs recordatorio del sistema | El plan y el dueño: solo `Co-Authored-By`, sin `Claude-Session`. Manda la regla del dueño. |
| Cada tarea consigo misma | — | T1–T12 coherentes (tests ↔ firmas ↔ archivos). T2 paso 6 borra tests de `FileHashScreen` pero exige conservar sus asertos SHA-256: coherente. |

Ruling R1: una llamada a `disable` que esté en un callback (no en corrutina) se lanza en el `scope` de la composición que ya tiene la pantalla, y lo que lee `enabled()` después va dentro de esa misma corrutina — el plan dice «ya están en corrutinas», y no todas lo están — si es errónea, solo cambia el sitio del `launch`.
Ruling R2: T6 crea `imports/ImportTexts.kt` con todos los textos visibles de importar (mensajes de error, línea de marcas, `SkipReason.text` puede quedarse en el enum si no lleva nombres ajenos); los nombres ajenos solo pueden aparecer en literales de `imports/ImportTexts.kt` (y en el README); `ImportTextsTest` (T9) lo comprueba sobre `app/src/main/java` excepto ese archivo; los identificadores Kotlin (`BitwardenJson`, `ImportFormat.BITWARDEN`) no son texto visible — el plan nombra `ImportTexts.kt` en T9 sin crearlo — si es errónea, mover cadenas cuesta poco.

## Tareas
Task 1: dispatched (BASE 7c8dea7, implementer opus addf288836cebc2e9)
Owner decision (3-11-2026, durante T1): «Hazlo con agentes y tú, con el ingeniero móvil senior y todos los plugins y al finalizar auditorías, que no tengan ni un error, vulnerabilidades, inyecciones, etc.» → cada revisor de tarea comprueba también entradas hostiles (inyección en CSV/JSON/fórmulas, rutas, desbordamientos, DoS por tamaño o profundidad, fugas en logs o portapapeles); al final: code-review, security-review, auditoría completa (experto-seguridad-cripto, auditoria-seguridad-movil, auditoria-seguridad) y Abogado Digital; todo hallazgo se arregla, ninguno se aplaza.
Task 1: implementer DONE_WITH_CONCERNS (d59c743; 5 concerns, todas de diseño/prueba en móvil → al revisor como riesgos nombrados); reviewer opus aab11c214497614c1
Task 1: review → Needs fixes (I1 aviso M-4 falso tras girar con doFinal fuera de Main; M1 ticket de unlock no se suelta en ON_DESTROY; M2 falta test ENABLE después de DISABLE). Review: task-1-review.md
Ruling: los menores entran en la ronda de arreglos — regla del dueño «Detalles menores: se arreglan todos» manda sobre el aplazamiento de la skill — coste si es errónea: una ronda algo más larga.
Task 1: fix round 1 dispatched (FIX_BASE d59c743, resume addf288836cebc2e9) — I1, M1, M2
Task 1: fix round 1 implemented (516c544); re-review sonnet ab3dc9022a17d6356
Task 1: fix round 1/5 (3 addressed, 0 open — I1, M1, M2; commits d59c743..516c544)
Task 1: complete (commits 7c8dea7..516c544, review clean)
Task 1: phone tests for T12 README — enable then disable at once; disable during key generation and during the prompt; rotate at each enable step (incl. right after the finger is accepted); unlock while disabling; rotate with the unlock prompt shown; background the app with each prompt shown, return, then «Borrar bóveda» / «Activar huella».
Task 2: dispatched (BASE fba2725, implementer sonnet a9d25f44d2cfdcf3b)
Task 2: implementer DONE_WITH_CONCERNS (c36b08a). Note for T12: README.md:283,:291 and docs/ficha-google-play.md:46 still say «Huella SHA-256».
Task 2: reviewer opus a54d234e94b4b8826
Task 2: review → Needs fixes (I1 secretos no se borran al bloquear/salir/cambiar de pestaña; I2 aserto M8 debilitado; 7 menores). Fix round 1 dispatched (FIX_BASE c36b08a, resume a9d25f44d2cfdcf3b)
Task 2: fix round 1 implemented (8690564); re-review sonnet a5647184ef0eb4ee5
Task 2: fix round 1/5 (9 addressed, 1 new minor — nota de borrado exagera para el resultado de Archivo; commits c36b08a..8690564). Fix round 2 dispatched.
Task 2: fix round 2/5 (1 addressed, 0 open; commits 8690564..66eedd9)
Task 2: complete (commits fba2725..66eedd9, review clean)
Task 2: minor (deferred to final fix wave, to be FIXED there per owner rule): the wipe does not reset `readBytes`/`totalBytes`/`fileAlgo` in HashLabViewModel, and the ViewModel `run` is not bumped by `clear()` (one last progress write could land); one long KDoc line in HashLabSession.
Task 2: phone tests for T12 README — Laboratorio de hashes: each tab; file pick with progress and Cancelar on a large file; «Calcular otra vez con X»; lock/away wipes every tab incl. Archivo.
Task 3: dispatched (BASE e107b20, implementer sonnet ad251fa5820bb982e)
Task 3: implementer DONE_WITH_CONCERNS (082df93): TDD by mutation; UI not run on device; rotor/reflector chips survive wipe; Barbarossa source is a simulator README (plaintext confirms key).
Task 3: reviewer opus a020acc3743510a58 (package review-e107b20..082df93.diff)
Task 3: review → Needs fixes (I-1 ENIGMA_INTRO wrong reason; M-1 non-ASCII in Enigma form; M-2 NFD combining marks; M-3 silent refusal over limit in key fields; M-4 rotor order/reflector not wiped; M-5 indicators untested + only simulator source; M-6 two misleading settingsValidation cases). Fix round 1 dispatched (FIX_BASE 082df93, resume ad251fa5820bb982e)
Task 3: fix round 1 implemented (031ef66); M-5 indicators checked by decryption, no standard source found (fallback as instructed)
Task 3: re-review 1 sonnet ae95863ddeeef1317 (review-082df93..031ef66.diff)
Task 4: pre-check — trezor/python-shamir-mnemonic LICENSE is MIT (Copyright 2019 SatoshiLabs), checked by controller
Ruling R3 (Task 4): the SLIP-0039 warning drops the brand names. Text: «Estas partes no son una copia SLIP-39 de wallet. Metidas en una wallet de hardware como recuperación SLIP-39 darían otra wallet distinta. Recomponlas en Clavero o con una herramienta SLIP-0039 que devuelva la entropía.» — why: the owner's rule (other names only on the import screen and in the README; "no problems with anyone") outranks the spec's verbatim copy; the meaning is unchanged; «Recompónlas» is misspelled (RAE: enclitics follow the general accent rules → «Recomponlas»). Cost if wrong: a one-line text change. The MIT copyright line (SatoshiLabs) stays in the licence file, NOTICE and Licencias, because attribution is required; the passphrase "TREZOR" stays only in the test vectors.
Task 3: fix round 1/5 (7 addressed, 0 open, 4 new minors N-1 nfc folds singletons/text not literal, N-2 first-person source comment, N-3 duplicate assert, N-4 refused note survives tab switch; commits 082df93..031ef66). Fix round 2 dispatched (FIX_BASE 031ef66).
Task 3: fix round 2 implemented (ad7e744); re-review 2 resume ae95863ddeeef1317
Task 3: fix round 2/5 (4 addressed, 0 open; commits 031ef66..ad7e744)
Task 3: complete (commits e107b20..ad7e744, review clean)
Task 3: phone tests for T12 README — Laboratorio clásico: each tab (César, Vigenère, Enigma); Barbarossa settings decrypt; paste over limit in a key field shows the note; lock/away wipes text, key, rings, positions, plugs, rotor order and reflector.
