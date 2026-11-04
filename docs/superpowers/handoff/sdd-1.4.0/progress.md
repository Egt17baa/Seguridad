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
Task 4: dispatched (BASE 556ed66, implementer opus abb0fa550dc82639f, ruling R3 carried)
Owner decision (3-11-2026): every task review, the final branch review and the audits must check two threat classes, everywhere input enters the app (passwords, text fields, paste, files, imports, QR, shares, intents):
  (a) huge or malicious input (e.g. a 200 GB file or a giant pasted password): size caps enforced BEFORE reading or allocating, streaming with bounded buffers, no whole-file reads, no unbounded parsing (depth/count), cancellable work off the main thread, no OOM/ANR/disk fill;
  (b) race windows between check and use (TOCTOU): validate-then-reopen of the same Uri/file, state that can change between a check and the action (lock, wipe, recreate, second tap), late results after cancel/wipe, and timing side channels in secret comparisons (constant-time compare).
  Applies retroactively to Tasks 1-3 in the final review; carried in every dispatch from Task 4 on.
Task 4: implementer DONE_WITH_CONCERNS (0b9cb46). 1395 app tests / 1390 jvm-check green.
Ruling R3a (Task 4): «wallet» → «cartera» in the SLIP-0039 warning — why: FinalLegalReviewTest.noAppTextSaysWallet enforces the Abogado Digital's R-14 (1.3.0), which outranks my R3 wording; meaning unchanged. Cost if wrong: one word.
Ruling R4 (Task 4): accept the deviations — Slip39(words) class with check callback (word list from assets, like Bip39); PBKDF2 via Bouncy Castle PKCS5S2ParametersGenerator (allowed primitive, exact passphrase bytes, zeroable key); exponent capped at 8, split always e=1; combine accepts up to 64-byte secrets. Reviewer still checks each against SLIP-0039.
Task 4: reviewer opus acc26165ae59c85f6 (review-556ed66..0b9cb46.diff)
Task 4: review → spec ✅, quality Needs fixes (I-1 «Como frase BIP-39» shown for any 16..32-byte secret, misleading for wallet seeds; M-1 parts field cap 40000 chars below 256×33-word parts; M-2 invalid vectors don't check which failure; M-3 unused suggestions computed on main thread in split). ⚠️ lint resolved by controller: lint-results-debug.xml has 16 warnings, all in gradle/libs.versions.toml (GradleDependency, NewerVersionAvailable, AGP version: online checks), AndroidManifest (ObsoleteSdkInt) and mipmap (DiscouragedApi); none in task files. Phone test noted for T12.
Task 4: fix round 1 dispatched (FIX_BASE 0b9cb46, resume abb0fa550dc82639f)
Task 4: fix round 1 implemented (54161a8)
Task 4: re-review 1 sonnet a33bb8c0665473375
Task 4: fix round 1/5 (4 addressed, 0 open; commits 0b9cb46..54161a8)
Task 4: complete (commits 556ed66..54161a8, review clean)
Task 4: phone tests for T12 README — Repartir un secreto: split a 12- and a 24-word phrase and recombine; one official vector; time with exponent 8 and «Cancelar»; lock/away wipes everything; TalkBack on the warning card.
Task 5: dispatched (BASE 2db90e1, implementer opus adf266f9c07dd5775, owner decision (a)/(b) carried: bounded temp, single read / import what was checked, unique run dir)
Task 5: implementer DONE_WITH_CONCERNS (d27b5e2). 1421 app / 1416 jvm-check green (one test-only lateinit→val after full run; BackupCheck* re-run green).
Ruling R5 (Task 5): accept importReplace sharing checkCopyEntries/attachmentInCopy instead of calling checkBackup whole — why: calling it would need a second Argon2id or moving the KDF out of the mutex, and checkBackup deletes its temp; shared functions in the same order keep the checks from drifting. Reviewer verifies no drift and no TOCTOU. New BACKUP_MAX_READ_BYTES 110 MiB in BackupReader: reviewer must confirm every backup an earlier version could write stays readable.
Task 5: reviewer opus a004a745505510fd9 (review-2db90e1..d27b5e2.diff)
Task 5: review → spec ✅ (R5), quality Needs fixes (I1 data key not zeroed when cancelled during Argon2id — needs NonCancellable; I2 'never two Argon2id at once' broken after Cancelar: stop() nulls job, join cancellable; m1 empty run-… dir left if cancelled at mkdir; m2 BackupCheckReport KDoc overstates — only A8 re-wrap is import-only; ⚠️ picker without keepOpenOnce with vault open → treated as m3, follow «Importar copia» picker pattern). Fix round 1 dispatched (FIX_BASE d27b5e2, resume adf266f9c07dd5775)
Task 5: fix round 1 implemented (22cee96); m3 adds store.touch() (inactivity + A11 mark) — re-review checks against 'writes nothing'
Task 5: re-review 1 sonnet a6d9336810fb5dee7
Task 5: fix round 1/5 (5 addressed, 0 open, 2 new minors — touch() may write A11 clock mark so «no toca tus ajustes»/KDoc/brief «writes nothing else» overstate; PlatformSourcesTest picker assertion whitespace-exact; commits d27b5e2..22cee96)
Ruling R6 (Task 5): keep store.touch() (same as «Importar copia»: resets inactivity, may raise the A11 clock mark only upward); make BACKUP_CHECK_INTRO and the KDoc true instead — why: the A11 mark is a security mark, consistent with every unlock; the texts must not claim more. Cost if wrong: drop touch() later. Fix round 2 dispatched.
Task 5: fix round 2 implemented (5958932); re-review 2 resume a6d9336810fb5dee7
Task 5: fix round 2/5 (2 addressed, 0 open; commits 22cee96..5958932)
Task 5: complete (commits 2db90e1..5958932, review clean)
Task 5: phone tests for T12 README — Comprobar una copia: a real backup (size with Android zlib), wrong password, Cancelar during Argon2id then start again, pick the file with the vault open (no lock/wipe), leave the app mid-run (no temp left in cacheDir/comprobar).
Ruling R7 (Task 6): the SkipReason text «Código que no es TOTP (HOTP, Steam…)» and the originTag values «importado-bitwarden/aegis/2fas/firefox/apple» name other products, so their literals live in imports/ImportTexts.kt (enum fields reference constants); same for «Clavero no reconoce este archivo.». Why: R2 (names only in ImportTexts.kt literals); originTag becomes a visible tag. Cost if wrong: moving strings back.
Task 6: dispatch carries owner decision (a): bound columns per row and total cells, not only bytes/rows (a 16 MiB file of commas = 16M fields → OOM); linear-time parser; no regex backtracking; ImportFailure never escapes readImport.
Task 6: dispatched (BASE 364d4f7, implementer opus a454fb2d0d05ca075, R2/R7 + owner (a) carried)
Container restart: Task 6 implementer stopped mid-work (untracked imports/ files present, no report, no commit). Resumed via SendMessage.
Owner decision (4-11-2026), (c): anything imported into the vault (CSV, JSON, otpauth, backups) is treated as hostile data, never as code. Checked by controller at 364d4f7: no WebView, no HTML rendering (fromHtml/Linkify/autoLink: none), the only openUri is SettingsScreen.kt:496-498 (fixed links). Every review and the final audit must confirm: imported text is only ever shown as plain Text, stored verbatim, never executed/interpreted/opened; no imported URL is opened or turned into a link or an Intent (javascript:, intent:, file:, content: schemes); control characters, NUL, and bidi overrides (U+202A–202E, U+2066–2069) cannot spoof a title/URL in lists (strip or make visible on display, stored verbatim); otpauth parameters parsed strictly; JSON depth/size bounded; no deserialisation of types from the file (no reflection/polymorphic decoding); CSV formula injection noted for any future export.
Owner decision (4-11-2026), (d): a red-team pass is part of the quality tests. After the final whole-branch review and its fix wave, and before code-review/security-review/the full audit/the Abogado: dispatch a hostile-attacker agent (most capable model) over the WHOLE app, not only the 1.4.0 diff. It plays a malicious app/file/person with the phone in hand and tries to break Clavero through every entry: exported components and intents, share/OPEN_DOCUMENT/content Uris, every file format (sgenc, vault, backup, SGM1, imports CSV/JSON/otpauth, QR, Base45 cards), clipboard, backups and restore, fingerprint/Keystore flows, network client (pwnedpasswords pinning), WebView/links, logs, FLAG_SECURE, races (TOCTOU), huge inputs (a), hostile content (c), crypto misuse, dependency CVEs, the build/CI and the signing workflow. Rules: own app, local only; PoCs are malicious INPUTS written as failing unit tests/fixtures in the repo test tree (no real malware, no network attacks, nothing outside the repo). Output: docs/auditorias/2026-11-XX-ataque-1.4.0.md, each finding with severity, how to reproduce, impact and the fix. Every finding is fixed (owner rule: no deferral), each with its PoC test turned green, then a scoped re-attack. Permanent part: the hostile-input tests stay in the suite so CI runs them on every PR.
Task 6: implementer DONE_WITH_CONCERNS (754e200). 1481 app / 1476 jvm-check green. Display helper plainReportText (ImportModel.kt:91). Step 5 pending (owner CSVs).
Ruling R8 (Task 6/9): pre-existing «Google Authenticator» literals (VaultEntryScreen.kt:249,310,536; ToolTexts.kt:648) predate 1.4.0 and passed the 1.3.x Abogado review; ImportTextsTest allowlists exactly those literals (by exact string, not by the word), and they go on the Abogado final-review list. Cost if wrong: move 4 strings.
Ruling R9 (Task 9): per decision (c), stored titles stay verbatim; DISPLAY sanitising (plainReportText-style: controls→space, bidi controls dropped) applies wherever a title/username/URL is shown in lists, detail headers and the import report — for every entry, not only imported ones. Task 9 owns it. Cost if wrong: one helper call per Text.
Task 6: note for Task 8 — IMPORT_MAX_ITEMS counts rows; Apple rows with OTPAuth can give 2× items; Task 8 must bound the merged result (count and backup size) before saving.
Task 6: reviewer opus a9aa211aab182a11d (review-364d4f7..754e200.diff)
Task 6: review → spec ✅ except I1; quality Needs fixes (I1 ImportTextsTest exempts «google» everywhere instead of R8 exact literals; I2 plainReportText misses Cf/zero-width/tag chars and combining-mark towers; M1 a list per blank line → 400 MiB garbage on 16 MiB of newlines; M2 memory-probe skip drops the whole commas test; M3 Detect KDoc silent on who zeroes bytes/password; M4 delimiter detection tested one way only).
Ruling R8a: the licence-attribution literal LicensesScreen.kt:31 (github.com/google/guava) joins the R8 exact allowlist — attribution required by licence, same as SLIP-0039's. Fix round 1 dispatched (FIX_BASE 754e200, resume a454fb2d0d05ca075).
Task 6: fix round 1 implemented (afeb362; also csvDelimiter skips leading blank lines; whole tag block dropped)
Task 6: re-review 1 sonnet a64882da49130d009
Task 6: fix round 1/5 (6 addressed, 0 open; new minors: ZWNJ dropped, combining cap 3 trims Hebrew/Tibetan in display, namesIn misses names glued to letters; out-of-scope: csvDelimiter toggles quotes on any " while parser opens quotes only at field start; commits 754e200..afeb362)
Ruling R10 (Task 6): ZWNJ drop and the 3-mark cap stay — display only, stored verbatim, documented in KDoc; anti-spoofing outweighs rare-script rendering in a Spanish-only UI. Cost if wrong: loosen the helper. Fix round 2: namesIn glued names, and csvDelimiter quote handling aligned with the parser.
Task 6: fix round 2 implemented (705b65b); re-review 2 resume a64882da49130d009
Final fix wave (to be FIXED): pre-1.4.0 literal «Nombre (p. ej. «Binance» o «Ledger»)» in the vault form names two products — replace with generic examples (owner rule: other names only on the import screen/README); goes to the Abogado list.
Task 6: fix round 2/5 (2 addressed, 0 open; commits afeb362..705b65b)
Task 6: complete (commits 364d4f7..705b65b, review clean)
Final fix wave (to be FIXED): ImportTextsTest «edge» whole-word only misses glued forms like «EdgeBrowser»/«MiEdge» (match substring with an explicit Spanish-word exception list instead); csvDelimiter does not count the last cell after an unclosed quote (harmless: file rejected by IMPORT_CSV_UNCLOSED_QUOTE) — add a comment and a test pinning it.
Task 6: step 5 pending — owner's real CSV exports.
