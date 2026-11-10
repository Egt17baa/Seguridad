# Task 10 re-review, fix round 3 (commit 507fb58, ruling R27)

Review only. `./gradlew :app:testDebugUnitTest --tests 'app.seguridad.imports.*'`: BUILD SUCCESSFUL.

### Verdicts
- **Trailing comma on the first URI: ADDRESSED.** `CsvFormats.kt` `loginUris` drops trailing commas and whitespace from every piece but the last (`trimEnd { ',' or whitespace }` then `trim()`); the last piece only gets `trim()`, so a URL's own trailing comma survives. Test `bitwardenCsvSplitsOnlyBeforeAScheme`: `https://a/ ,, https://b/` -> URL `https://a/`, note `URL: https://b/`; this fails on the old code (URL was `https://a/ ,`). The case `https://a/x,` (last piece keeps its comma) passes on the old code too; it is a pin against over-trimming, not a regression test.
- **`?a=1,b:2` tightening: ADDRESSED.** `LOGIN_URI_SEPARATOR = ,\s*(?=[A-Za-z][A-Za-z0-9+.-]*://)`. Test: `https://a.example/?a=1,b:2` stays one URL with empty notes; it fails on the round-2 regex (which split `b:2`). Existing splits (`https://`, `androidapp://`) still pass. Accepted cost per R27: a `mailto:`-style URI is not split from the previous one.
- **Regex stays linear: CONFIRMED.** Python `re` (backtracking, as Java's), new regex, 64 KiB cells: `", "` x32000, comma + 65000 spaces, `",a"` x32000, 2000 words, comma + 65000 letters, `",a:"` x20000, `",a"` x5000 + `://`, 65000 spaces, 20000 `ab` words + `://`, `", a"` x21000: every case under 2 ms. The class cannot contain a comma and the spaces it backtracks over follow one comma, so the cost is O(n). The KDoc sentence saying so is accurate.
- **KDoc and report: ACCURATE.** The header KDoc, the `LOGIN_URI_SEPARATOR` KDoc (names `?q=1,2` and `?a=1,b:2`) and `loginUris` KDoc match the code. `task-10-report.md` gives the new regex, the `mailto:` consequence and the dropped commas. Nit: the report line still shows the round-2 regex first, then the final one (history, not wrong).

### New issues
None. One accepted ambiguity: a non-last URL that really ends in a comma (`https://a/x,, https://b/`) loses it; indistinguishable from an empty URI, and the Task 12 real-export check covers it.

### Verdict
**fix round 3/5 (2 addressed, 0 open; no new issues).**
