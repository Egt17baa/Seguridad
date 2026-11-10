# Task 7, re-review of fix round 2 (776b27e..795d3bd)

### Finding Verdicts
- **I-1: ADDRESSED.**
  - `scanJson` (`ImportJson.kt`, new version) keeps one slot per level for "is an object", the element or member count, and the room made. The model:
    - An array's first element charges `refArrayBytes(10)`. Each time it is full it grows by half (10, 15, 22, 33, 49…), charging the difference.
    - An object's first `:` charges a 16-slot table, which doubles at more than ¾ full (13, 25…).
    - An element is any value that starts at an array level. A member is a `:` at an object level.
  - This matches how `ArrayList` and `LinkedHashMap` grow.
  - The arrays are made once per scan and sized `JSON_MAX_DEPTH + 1`. `open()` throws before indexing past that. `depth--` is guarded.
  - Arithmetic is `Long`. It stops early at the limit.
  - I ported the new scanner to Java independently from the diff (`/tmp/claude-0/-home-user-Seguridad/afa106da-72c3-5be8-bbce-c61e4a0844be/scratchpad/rr/R2.java`) and measured on kotlinx-serialization-json 1.11.0, SerialGC, bytes per element of an outer array:

    | Shape (the implementer did not list it) | Measured | Estimate | Estimate / measured |
    |---|---|---|---|
    | `{"k":[[…[1]…]]}`, 30 levels | 3207.6 | 3464.2 | 1.08 |
    | array of 11 arrays of 11 `{}` | 1004.8 | 1100.8 | 1.10 |
    | 11 × `{"x":[2,3]}` in an array | 5493.6 | 5919.6 | 1.08 |
    | object of 14 members whose values are `[1,"s",[true]]` | 7173.3 | 7973.4 | 1.11 |
    | `[[],[1],[1,2]]` | 548.5 | 610.5 | 1.11 |
    | `{"a":{"b":{"c":[{}]}}}` | 924.7 | 970.7 | 1.05 |

    (The last column is the estimate over the measured value; the script's own ratio column is mis-scaled and should be ignored.) The estimate is at or above the measurement everywhere, with 5% to 11% margin. The earlier failing shapes (`[1]`, nested) are covered in the table of the fix report and by `theEstimateIsNeverBelowTheMeasuredTree`, which now lists about 45 shapes, including growth boundaries and two real Bitwarden items.
  - `theModelFollowsHowTheTreeGrows` checks the exact model at the boundaries. The mutation RED in the report is credible.
- **Minor, long KDoc lines: ADDRESSED.** `ImportJson.kt` `folderNames` and the `isJsonNumber` KDoc, `OtpAuthList.kt` `addMigration`, and the others in `ImportModel.kt` and `ImportTexts.kt` are rewrapped.
- **Minor, tens-of-MB test strings: ADDRESSED.** The budget is a parameter. The under/over test uses 200 000 B. Only one scan-only test uses the real 48 MiB on an open file of about 1.3 MB.
  - **Reachability of the overload:** `checkJsonShape(text, budget)` and `readJsonImport(text, treeBudget)` are `internal` with the default `JSON_MAX_TREE_BYTES`. The only production caller is `Detect.kt:20`, `readJsonImport(text)`, so it always uses the default. Nothing else in `app/src/main` calls either function.
- **Minor, `OtpMigration.kt:172` take(80): ADDRESSED.** It now uses `takeCodePoints` (`crypto/FileCrypto.kt:279`, same package). `otpName` shares it. The test `theNameInALeftOutTextIsCutWithoutSplittingAPair` covers it.

### New Breakage in the Fix Diff
None. No Critical, Important or Minor findings.

### Out-of-Scope Observations
- `vault/EntryQuery.kt:40` (tags cut by `take`) remains for Task 8, as ruled.
- ART objects are smaller, so the real margin is larger. The OOM guard in Task 9 (R11b) stays as a last line.
- Estimate looseness (1.1 times typical; null/empty-string shapes are over-counted much more, which is safe) only costs the user a refusal for an extreme export.

### Textos para el Abogado Digital (revisión final)
None new. `IMPORT_JSON_TOO_MANY_VALUES` («El archivo tiene demasiados datos. Clavero no lo lee.») is unchanged; only its comment changed.

### Revisión experto-seguridad-cripto
- CWE-770 is now bounded: the pre-parse estimate is an upper bound on every shape I tried. This includes small non-empty arrays, deep nesting and growth boundaries. The production code path cannot reach a different budget.
- Surrogate-safe cuts are now done by one helper (`Totp.kt`, `OtpMigration.kt:173`).

### Revisión Ingeniero Móvil Senior
- The scan is linear, with three small arrays allocated per call, no recursion, and `Long` arithmetic. Tests are fast (the report says at most 0.17 s each). I did not re-run the suite; the report shows 1570/1570 in jvm-check.

### Verdict
**Fix round:** All findings addressed, no new Critical/Important breakage.
