# Task 7, re-review of fix round 1 (fb195cd..776b27e)

### Finding Verdicts

- **I-1: NOT ADDRESSED.** The scan is in the right place and the arithmetic is safe. But the estimate is not an upper bound: a non-empty array costs more than `JSON_TREE_ARRAY_BYTES`.
  - Placement and arithmetic: `ImportJson.kt:457` runs `scanJson` before parsing, `bytes` is a `Long`, and the per-char term is bounded by Int. An escaped string is costed by its raw length, which is at least the decoded length. Every node kind is costed (object, array, member via `:`, primitive, per-char). `{"":""}`×N measured about 229 B against an estimate of 352 B, so that shape passes.
  - Why it is not a bound: `JsonArray` wraps an `ArrayList` that allocates a capacity-10 backing array (about 56 B) on its first element. `JSON_TREE_ARRAY_BYTES = 48` (`ImportJson.kt:428`) only covers the empty array (45 B), and the 80 B per primitive leaves no slack.
  - Measured with my own script, `/tmp/claude-0/-home-user-Seguridad/afa106da-72c3-5be8-bbce-c61e4a0844be/scratchpad/rr/R.java`. Same setup as the reviewer's: kotlinx-serialization-json 1.11.0, SerialGC, 200 000 repetitions inside an outer array. The estimate is a copy of `scanJson`.

    | Shape | Measured per element | Estimate | Ratio |
    |---|---|---|---|
    | `[1]` | 173.5 B | 130 B | 1.33 times over the estimate |
    | `[true]` | 172.8 B | 136 B | 1.27 |
    | `{"a":[1]}` | 420.8 B | 404 B | 1.04 |
    | `[[[[[[[[[[1]]]]]]]]]]` (10 levels) | 1036.8 B | 562 B | 1.84 |

  - Each nested one-element array really costs about 103 B (45 + 56 + slot) against 48 estimated. Extrapolated to depth 62 (`JSON_MAX_DEPTH` is 64) the ratio is about 2.1. A file the scan accepts at exactly 48 MiB could therefore build a tree of about 100 MiB or more. The scanner accepts that file, which is the I-1 failure again, only better hidden. The tests miss it because every array shape they use is either empty or one huge array (`ImportJsonTest.kt` `theEstimateIsNeverBelowTheMeasuredTree`).
  - Suggested fix: count 112 B per array (48 + 56 rounded up; `[]`×N is then over-counted, which is safe). Then `[1]` gives 194, the depth-10 shape 1202, and `{"a":[1]}` 468, all at or above measured. Add the `[1]`×N, `{"a":[1]}`×N and nested `[[[…]]]`×N shapes to the deterministic test, and correct the KDoc table, which currently says 45 B per array.
- **M-1: ADDRESSED.** `ImportJson.kt:563` (`folderNames` takes `wellFormed`) and `AegisJson.kt:42` (old `group`). Tests: `BitwardenJsonTest`, `AegisJsonTest` and `TwoFasJsonTest` `…NulOrALoneSurrogateGivesNoFolder`. The report shows a mutation RED for them.
- **M-2: ADDRESSED.**
  - `otpName` cuts by code points (`Totp.kt:195-199`), so the cut cannot split a pair.
  - `namesCut` and `otpNameFits` are used on every path:
    - `readOtpAuth` (`Totp.kt:253-266`) and `addOtpAuth` (`ImportModel.kt:622`);
    - `addTotp`, which checks before building the link (`ImportModel.kt:657-658`);
    - migration (`OtpMigration.kt:86-87,143-145,160`) and its list reader (`OtpAuthList.kt:751`).
  - Tests cover the 256 boundary with an emoji straddling it, in each reader.
  - Migration `rawIssuer` follows the same issuer-wins rule as `finalIssuer`, and the account uses the same `substringAfter(':')`.
- **M-3: ADDRESSED.** `BitwardenJson.kt:359-362`, the first non-empty URI is the URL. `theUrlIsTheFirstUriThatIsNotEmpty`. A whitespace-only URI still counts as non-empty (the same as any other text; not a problem).
- **M-4: ADDRESSED.** `BitwardenJson.kt:377` and `ImportTexts.kt:725` give «Campo: valor» for an empty or blank name. Test `aFieldWithoutANameGetsAGenericLabel`.
- **M-5: ADDRESSED.** `otpEncode` is internal in `Totp.kt:277` and `addTotp` uses it (`ImportModel.kt:666-668`). `linkPart` is removed.

### New Breakage in the Fix Diff

1. **Important**, the same defect as I-1 (see above): `ImportJson.kt:428` `JSON_TREE_ARRAY_BYTES = 48` undercounts non-empty arrays, and the KDoc and `theEstimateIsNeverBelowTheMeasuredTree` claim a bound that does not hold.
2. **Minor**, `ImportJson.kt:553` (the KDoc of `folderNames`): the added sentence runs on to a very long line and breaks the wrapping of the paragraph. Likewise `OtpAuthList.kt:742` has a very long KDoc line.
3. **Minor**, `ImportJsonTest.kt` `aLongStringIsWithinTheBudget` and the budget test build tens of MB strings with a 20 s timeout. This is fine if the CI is not slow, but it is a flake risk. I did not run them.
4. No surrogate split on the changed paths:
   - `otpName` cuts at a code-point offset, and `otpNameFits` counts code points.
   - `.take(80)` at `OtpMigration.kt:172` is old code and outside this diff (see below).

Second named risk (the change to count code points): no downstream limit assumes 256 UTF-16 units.
- The TOTP issuer and account have no length limit in the vault (`VaultEntryScreen.kt:452` has none, and there is no `take` in `vault/`).
- The title of an import is at most 512 units, below `MAX_TITLE = 1024` (`ImportLimits.kt:15`).
- Tests that depend on the old count still pass according to the report.

### Out-of-Scope Observations
- `OtpMigration.kt:172`: `.take(80)` on `who`, which goes into the skipped text shown to the user, can split a surrogate pair (old code).
- `vault/EntryQuery.kt:40`: `take(MAX_TAG_LENGTH)` on tags can also split a surrogate pair (already noted in the report for Task 8).
- ART objects are smaller than the JVM ones, which gives some slack, but a 2 times undercount is bigger than that slack. An `OutOfMemoryError` guard stays planned in Task 9 (R11b).

### Textos para el Abogado Digital (revisión final)
- `ImportTexts.kt:725` `NOTE_LINE_UNNAMED_FIELD` = «Campo» (label in the notes of an imported custom field without a name, shown as «Campo: valor»).
- `IMPORT_JSON_TOO_MANY_VALUES` («El archivo tiene demasiados datos. Clavero no lo lee.») is unchanged; only the KDoc changed.

### Revisión experto-seguridad-cripto
- CWE-770 remains open (`ImportJson.kt:428-473`). The memory bound is claimed by the KDoc but is not true for arrays of arrays.
- The Unicode fixes (M-1, M-2) are correct. `wellFormed` is applied to names (`ImportJson.kt:563`, `AegisJson.kt:42`). Never-cut-silently now holds on all TOTP routes (`ImportModel.kt:622,657`, `OtpAuthList.kt:751`).
- One encoder for the link (`Totp.kt:277`), and the injection tests are kept (`ImportModelTest` `addTotpNeverCuts…`).

### Revisión Ingeniero Móvil Senior
- The scan is one linear pass with no allocation and no overflow (`Long`, early exit at `limit`). The structure is sound; only the constant is wrong.
- The new fields have defaults (`Totp.kt:229`, `OtpMigration.kt:44`), so the callers compile unchanged.
- Tests: the fix report names the covering tests and gives output (jvm-check 1569/1569, app 1574/1574, lint with the 16 known warnings). I did not re-run them. The tests that assert the bound do not include the failing shape, so they pass while the bound is false.

### Verdict
**Fix round:** Findings remain open — I-1. M-1 to M-5 are addressed. Raise `JSON_TREE_ARRAY_BYTES` to about 112 (the backing array of a non-empty ArrayList), re-round the KDoc table, and add `[1]`×N, `{"a":[1]}`×N and nested `[[…[1]…]]`×N (depth up to 62) to the test of the bound.
