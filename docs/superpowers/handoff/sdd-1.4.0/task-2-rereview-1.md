### Finding Verdicts
- **Important 1 (guard/wipe, veracity)** — ADDRESSED. `HashLabSession.kt:125-143` (GuardedSession; forget() wipes text, expected, key, message, codecInput); `HashLabScreen.kt` ViewModel `init { watchAway(session.guard, store, lockAfterMs, awayInOtherApp) }`, `GuardEffects(s.guard, lockAfterMs)`, `ToolColumn(..., s.guard)`, `guard.typed()` in `limited`, `onCleared -> session.wipe()`; `AppNavHost.kt:203` passes vault, lockAfterMs, awayInOtherApp. Same pattern as the other tools. Bottom-tab case: `GuardEffects` has `DisposableEffect.onDispose -> guard.stopped(...)` (`ScreenGuardWiring.kt:73`) and ON_START re-entry calls `started`, so the away timer covers a tab switch; `TOOLS_AWAY_WIPE_NOTE` ("más tiempo que el bloqueo, 1 minuto como mínimo") and the new HMAC intro ("no se guardan en el móvil... el aviso de abajo dice cuándo se borran") are true. Source test and guard test added.
- **Important 2 (M8 assertion)** — ADDRESSED. `PlatformSourcesTest.kt` asserts `"if (f.failed) Text(HASH_LAB_FILE_FAILED" in src` and the NOT_PROTECTED texts.
- **Minor 1 (BLAKE3 multi-chunk)** — ADDRESSED. `HashLabTest.blake3MultiChunkOfficialVectors`, 1025 and 102400 bytes, hashes equal those in the review.
- **Minor 2 (over-limit note)** — ADDRESSED. Per-field `Field` enum, `TooLongNote` under text, key, message, codec and the expected field (in `ResultAndCheck`, so also File tab); test counts 6 (definition + 5).
- **Minor 3 (hex duplicate/slow)** — ADDRESSED. `hexEncode` removed; table-based `hex`/`hexLower` in `Hashes.kt`. Output unchanged (see risk a below); test over all 256 bytes.
- **Minor 4 (progress)** — ADDRESSED. `run` is @Volatile; write only if `run == mine` and every 1 MiB (`PROGRESS_STEP`).
- **Minor 5 (wrong size)** — ADDRESSED. `want.size != result.size -> hashLabWrongSize(...)` before the comparison; HMAC variant; test.
- **Minor 6 (algo change re-read)** — ADDRESSED. `chooseAlgo` only sets; `rehash()` on request. Risk c: `pick` sets `fileAlgo` and `file.start()` before the run, so no stale hash under a wrong label; the result shows only if `h != null && fileAlgo == algo`, otherwise the "Elegiste otro algoritmo" note plus button (`enabled = hasFile && !busy`), one read per click. After Cancelar `hasFile=false`.
- **Minor 7 (weak asserts)** — ADDRESSED. `assertEquals(2, calls)` (check before the 3-byte read and before EOF is correct); NOT_PROTECTED rendering asserted.

### New Breakage in the Fix Diff
- None Critical/Important.
- Minor (veracity, optional): `TOOLS_AWAY_WIPE_NOTE` says "lo que escribes aquí y el resultado se borran", but `HashLabSession.forget()` does not clear the File tab's result (`FileHashSession` hash/name) nor `lastUri`. The text/HMAC/codec results are derived from wiped fields, so they go; only the file's hash stays. It is not a secret and the brief excludes the file, but the sentence slightly overclaims for that tab. Either wipe `file` in the guard's wipe or accept (the note is shared with the other tools).
- Risk (a): `hex` callers are only `Crypto.kt:404` (`.lowercase()`), `sha256Hex` (now `hexLower`, same lowercase) and tests that `.lowercase()`; old `"%02X".format(byte)` gives two uppercase digits with the unsigned value, identical to the table. Byte-for-byte same.
- Risk (d): the new texts (`HASH_LAB_ALGO_CHANGED`, `hashLabRecalc`, `hashLabWrongSize`, HMAC intro) are in Spanish and contain none of the forbidden words.

### Out-of-Scope Observations
- Not run on a device; the guard behaviour on tab re-entry rests on the existing shared wiring.

### Revisión experto-seguridad-cripto
- Secrets (key, message, text, decoded text, pasted hash) are now wiped on vault lock, other app, long away and back (`HashLabSession.kt:137-143`, `HashLabScreen.kt` `watchAway`/`onCleared`); still Strings, references dropped, as in the other tools. No `rememberSaveable`. BLAKE3 multi-chunk path pinned to the reference. Wrong-size hashes no longer shown as a mismatch. No crypto change.

### Revisión Ingeniero Móvil Senior
- Wiring identical to `TextCryptoScreen`/`BreachScreen`; progress throttled and run-checked; no hidden multi-GB re-reads; hex off `String.format`. Source and JVM tests cover the wiring.

### Verdict
**Fix round:** All findings addressed, no new Critical/Important breakage. Open: none (one optional Minor above on "el resultado" for the File tab).
