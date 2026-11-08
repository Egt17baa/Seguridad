# Task 10 re-review, fix round 2 (a354817..68b84ca, commit 68b84ca, ruling R26)

Review only. I read the diff and ran `./gradlew :app:testDebugUnitTest --tests 'app.seguridad.imports.*'`: BUILD SUCCESSFUL. I also tried the regex (Python `re`, a backtracking engine like Java's) on hostile 64 KiB cells.

### Finding Verdicts

- **N-1: ADDRESSED.** `CsvFormats.kt` `LOGIN_URI_SEPARATOR = ,\s*(?=[A-Za-z][A-Za-z0-9+.-]*:)` and `loginUris` (end of file); KDoc at the top and on `readBitwardenRow` updated. New test `CsvFormatsTest.bitwardenCsvSplitsOnlyBeforeAScheme`: `https://a.example/x?q=1,2` stays one URL with empty notes; `https://a/,androidapp://b` splits. On the old code (split on every comma) the first assertion fails (`url` would be `…q=1`). The round-1 test with `" ,https://a.example/ , https://b.example/,https://c.example/ "` still holds. Report and Task 12 list updated, and the report states the remaining false split.
- **N-2: ADDRESSED (documentation only).** `ImportKdf.kt` `scryptKey` KDoc says every caller must run `checkScrypt` first, plus a comment at the catch. No behaviour change and no test; none is needed. Nothing here can fail on old code.
- **N-3: ADDRESSED.** `ImportKdfTest.base64WithSpareBytesKeepsTheSizeAndLeavesTheSpareZero`: sizes for `AA==`, `AAA=`, `AAAA`, `/w==`, `//8=`, `////` (decoded bytes equal the JDK's, the 16 spare bytes zero) and `""`, `=`, `A===`, `AA=A`, `====`, `A`, `AAA` all `IMPORT_BROKEN` with spare 0 and 16. This pins existing behaviour, so it passes on the old code too (the code was already correct, as round 1 found); it would fail on a mutated size formula (`- count('=')` off by one, or `size` without `spare`).
- **N-4: ADDRESSED.** Named constants with one KDoc line each in `AegisCryptoTest`, `BitwardenCryptoTest`, `TwoFasCryptoTest` (`OPEN_KEPT`, `WRONG_KEPT`, `PBKDF2_KEPT`, `ARGON2_KEPT`, `PBKDF2_WRONG_KEPT`, `ARGON2_WRONG_KEPT`); values unchanged (4/3, 9/10/6/7, 3/3).
- **N-5: ADDRESSED.** Stray blank lines before the closing brace removed in the Aegis and Bitwarden test files.

### Questions from the coordinator

1. **Trailing comma, `a/ ,, https://b`.** Confirmed: the first comma is followed by another comma, so no scheme follows and it does not split; the second comma splits. Piece 1 is `a/ ,` and after `trim()` the URL is `a/ ,` (I reproduced it). Verdict: **Minor, not worth a fix.** It needs an empty URI in the cell, which Bitwarden's own UI does not produce, and the result is the same kind of text the user would see in the cell. Stripping trailing commas would also change a legitimate URL that ends in a comma. If the owner wants it anyway: `.trim().trimEnd(',').trimEnd()` on each piece, with a test for `a/ ,, https://b` (and a note that a URL ending in a comma loses it).
2. **Hostile 64 KiB cells, backtracking.** No catastrophic case. `\s*` is followed by a lookahead that needs a letter, so on a run of whitespace it tries each shorter length once (O(run) per comma, and runs are disjoint, so O(n) in all). The class `[A-Za-z0-9+.-]*` cannot contain a comma, so each lookahead scan ends at the next comma or at the end: also linear. I measured 64 KiB of `", "` repeated, a comma plus 64 K spaces, `,a,a,…`, 2000 thirty-letter words, and a comma plus 64 K letters: all under 1 ms. The reader already caps a cell at 64 KiB + 1, so the split yields at most about 32 K pieces.
3. **`?a=1,b:2`.** It splits (`https://x/?a=1` and a note line `URL: b:2`). Known and documented in `task-10-report.md`. Optional tightening: require `://` after the scheme (`(?=[A-Za-z][A-Za-z0-9+.-]*://)`), which still splits `https://`, `androidapp://`, `iosapp://` and removes this false split, at the cost of not splitting scheme-only URIs such as `mailto:`. Not required; I would take it if the owner prefers fewer wrong splits, since Bitwarden's non-web URIs use `://`. Either way the real export check (Task 12) decides.

### New Issues

None Critical or Important. One Minor (same as the trailing-comma point above, optional) and one optional tightening (`://`). Out of scope: nothing new.

### Revisión experto-seguridad-cripto
- No crypto code changed; the `scryptKey` text now states the precondition truthfully. Regex linear on hostile input, so no new DoS path in the CSV reader.

### Revisión Ingeniero Móvil Senior
- `Regex` is a file-level `val`, compiled once; split work is bounded by the 64 KiB cell cap. Test constants are `private companion object` inside test classes, no effect on the app.

### Textos para el Abogado Digital (revisión final)
- None new. `task-10-report.md` gained one sentence on the comma rule (internal).

### Verdict

**fix round 2/5 (5 addressed, 0 open; new minors: trailing comma on the first URI when a cell has an empty URI (leave as is or `trimEnd(',')`), and the optional `://` tightening for `?a=1,b:2`).**
