# Task 6 report: Inspector de QR, tipos y señales de alerta

Status: DONE (with the concerns below).

## Implemented
Pure Kotlin, package `app.seguridad.tools.qr`, no new dependency, no ui/android code touched.
- `Visible.kt`: `visibleText`, `hasHiddenChars`. Code-point based. Marks bidi controls (asked of `withoutBidiControls` itself, so the set lives in one place), U+200B-U+200D, U+2060, U+FEFF, U+E0000-U+E007F and ISO controls except `\n`; format `⟨U+%04X⟩` (5-6 digits for supplementary).
- `LinkSignals.kt`: `Signal`, `SHORTENERS` (exact list), `linkSignals`, internal `parseLink`/`signalsOf`/`schemeOf`.
- `QrContent.kt`: `QrKind` (all cases/signatures as in the brief), `classifyQr`, `MAX_QR_TEXT_BYTES = 4096`, field/percent-decoding helpers.
- `tools/ToolTexts.kt`: new `QR_TOO_BIG = "El código QR tiene demasiado texto para inspeccionarlo."`, added to `ALL_TOOL_TEXTS`.
- Tests: `LinkSignalsTest.kt` (25), `QrContentTest.kt` (21, incl. 2 seeded fuzz tests: 20,000 + 3,000 random strings plus 4 KB delimiter walls, with control, bidi, lone-surrogate and tag chars).

## Decisions and deviations (please read)
1. **No `java.net.URI`.** The brief says URI first, manual parse as fallback. I used a single manual parser (`parseLink`). Reason: `java.net.URI` rejects spaces, backslashes, `_` and non-ASCII hosts, and where it does parse it can disagree with the browser that will open the link; two parsers that disagree is a security hole in a tool that says «the real host is X». The parser follows WHATWG on what matters: tab/CR/LF inside are dropped (`https://banco.es\n.evil.com` -> host `banco.es.evil.com`); `\` ends the authority in web schemes (`https://banco.es\@evil.com` -> host `banco.es`, no USERINFO, as a browser reads it); `http:evil.com` / `https:///evil.com` reach `evil.com`; userinfo ends at the LAST `@`; host percent-decoded, lower-cased, trailing dots and U+3002/U+FF0E/U+FF61 normalised. Total function, never throws.
2. **MIXED_SCRIPTS** exactly as the brief (per label, `Character.UnicodeScript.of`, ignoring COMMON/INHERITED), evaluated on the **Unicode** form of the host, so a mixed label hidden inside `xn--` is also caught (test `mixedScriptsAreFoundInsideAPunycodeLabelToo`). The extra case «whole label Cyrillic/Greek with a Latin rest» was **not** added: it does not fall out naturally (it would flag `банк.es`, which you said is fine) and needs a confusables table. Whole-script lookalikes (`аррӏе.com`) are therefore not flagged; the screen shows `unicodeHost`, which helps.
3. `Link.host` is lower-case, no trailing dot, IPv6 kept with brackets; `unicodeHost` = `IDN.toUnicode(host, ALLOW_UNASSIGNED)` in try/catch (null on failure; same as host for a plain name).
4. IP_HOST: IPv6 in brackets, or last label numeric (decimal or `0x` hex), so `3232235777`, `127.1`, `0x7f.0.0.1` count.
5. ODD_PORT: any explicit digits port that is not the scheme default (http 80, https 443, ftp 21, ftps 990, ws 80, wss 443); `:0443` = 443; empty port `:` is nothing. MANY_LEVELS: labels - 2 > 4 (`a.b.c.d.e.banco.es` yes, 4 subdomains no); not applied to IPs.
6. HIDDEN_CHARS also fires on an inner `\n` of a link (a browser drops it, so it can hide an address), beyond `hasHiddenChars` (which excludes `\n` by spec).
7. `Link` is: scheme in {http, https, ftp, ftps, ws, wss, file, javascript, data, intent} or any `scheme://`. Other `x:y` (e.g. `hora:10:30`, `Nota: ...`, `C:\...`) stay PlainText. Scheme-less `www.banco.es` is PlainText.
8. `classifyQr`: size check first (`length > 4096` short-circuits before encoding, then UTF-8 bytes), message is exactly `QR_TOO_BIG`. The rest runs inside a catch-all `Exception -> PlainText`, a second line of defence; the fuzz test also calls the unguarded `classifyQrUnguarded` so the parsers are proven total on their own.
9. Order: otpauth, `SGK1:` at start (a card's Base45 body may contain `SGM1:`), WIFI, EPC, vCard, MECARD, MATMSG, sms/mms(to), tel, mailto, geo, crypto, link, then the app's "last prefix wins" rule for `SGM1:/SGS1:/SGK1:` anywhere (signed text carries `SGS1:` last; a link containing `SGM1:` stays a link). Prefix constants come from `Card.kt`, `Message.kt`, `Signatures.kt`; I did not call `detectReceived` (it needs a valid card; the brief wants `SGK1:` -> SeguridadCard on prefix). Same for otpauth: prefix only, `parseOtpAuth`/`otpName` not needed here (the screen hands the text to the existing import; fields are shown raw through `visibleText`).
10. Wifi: `security` is `"nopass"` (T absent/empty/nopass) or the type upper-cased (`WPA`, `WEP`); `\x` escapes any char; surrounding `"` removed from S and P; SSID required (else PlainText). Task 7 should compare `security` against `"nopass"`/`"WEP"`.
11. EPC: lines BCD, version, charset, `SCT*`, BIC, name, IBAN, amount, purpose, structured ref (else free text). IBAN not checksum-validated (display only). Crypto: bitcoin/ethereum/lightning; amount from `amount`/`value`/`uint256`; ethereum `pay-` and `@chain/...` stripped from the address.

## Tests and results
- RED: `gradle -p jvm-check test --tests 'app.seguridad.tools.qr.*'` before any production code: `compileTestKotlin FAILED` (`Unresolved reference 'classifyQrUnguarded'`, `'classifyQr'`, `'MAX_QR_TEXT_BYTES'`, ...).
- GREEN: same command after implementing: BUILD SUCCESSFUL, all qr tests pass (first runs failed on three mistakes in my own tests: wrong expectation for `SGM1:..\nSGK1:..`, a wrong expected text, and the 4 KB fuzz wall exceeding 4096 bytes with 3-byte chars; one of them showed a doc/behaviour mismatch which I fixed by simplifying `seguridadAtStart` to `SGK1:` only).
- Full suite once before commit: `gradle -p jvm-check test --no-daemon --max-workers=1 --offline`: BUILD SUCCESSFUL, 753 tests, 0 failures, 0 skipped (ToolTextsTest `noForbiddenWords` and `everyTextConstantIsChecked` included, with `QR_TOO_BIG`).
- Android line not run (no ui/ or android/ touched).

## Files changed
- A `app/src/main/java/app/seguridad/tools/qr/QrContent.kt`, `LinkSignals.kt`, `Visible.kt`
- A `app/src/test/java/app/seguridad/tools/qr/QrContentTest.kt`, `LinkSignalsTest.kt`
- M `app/src/main/java/app/seguridad/tools/ToolTexts.kt` (+`QR_TOO_BIG`)

## Self-review
- Formats `SG1:`, `.sgenc`, vault, backup, `CP1:`: untouched. No permission, network, dependency, log or exported component.
- Nothing in the code says «seguro»; no signal list means nothing.
- Everything is data in, data out: nothing is opened, connected or paid.

## Concerns
- `visibleText` marks exactly the brief's set. Other invisible/format characters stay unmarked: U+00AD soft hyphen, U+034F, U+061C is covered, U+115F/U+1160/U+3164 Hangul fillers, U+180E, U+2028/2029, U+2061-2064, variation selectors U+FE00-FE0F. Cheap to add if you want it; I kept to the spec.
- MIXED_SCRIPTS (brief rule) flags legitimate Japanese labels mixing Han + Hiragana/Katakana; harmless extra warning, no allow-list (UTS #39) implemented.
- Whole-script homograph hosts are not detected (see 2).
- Scheme-less `www.` text is PlainText, not a Link, so it gets no signals; the text is still shown in full.
- Task 7: show `Link.uri`/fields through `visibleText`; `Wifi.security` convention above.

## Revisión experto-seguridad-cripto
Skill read (SKILL.md, auditoria/cripto references not needed: no crypto added). Threat model: a hostile QR/text (phishing, homographs, parser differentials, DoS by size) against a viewer that must never act.
- Untrusted input size checked before reading: `QrContent.kt:49-54` (length short-circuit, then UTF-8 bytes; `IllegalArgumentException(QR_TOO_BIG)`), limit `QrContent.kt:11`.
- Parser differential (CWE-601/CWE-1007 class): single browser-like parser `LinkSignals.kt:50-100`; last `@` `LinkSignals.kt:62`; backslash `LinkSignals.kt:61`; tab/newline stripping `LinkSignals.kt:51`; tests `LinkSignalsTest.backslashEndsTheAuthorityLikeABrowserReadsIt`, `tabsAndNewlinesInsideAreRemovedLikeABrowserDoes`, `userInfoHidesTheRealHost`.
- Homograph (CWE-1007, UTS #39): per-label script check on the Unicode form `LinkSignals.kt:116-123,154-170`; punycode decoded in try/catch `LinkSignals.kt:109-113`.
- Numeric hosts as the browser reads them `LinkSignals.kt:145-151`.
- Hidden/bidi text (Trojan Source, CVE-2021-42574): `Visible.kt:14-45`, bidi set reused from `crypto/FileCrypto.kt` `withoutBidiControls` (`Visible.kt:43`), tag characters U+E0000-E007F `Visible.kt:40`.
- Robustness (CWE-248/CWE-20): all parsers total, catch-all only as second line `QrContent.kt:55-59`; fuzz tests `QrContentTest.fuzzNeverThrowsAnythingButTheSizeError`, `fuzzFromRandomCharsAndLongInputs` (23k seeded inputs + 4 KB walls); `classifyQrUnguarded` `QrContent.kt:63` proves totality without the catch.
- No ReDoS: only fixed small regexes (`LINE_END`, `COORDINATE`) on a bounded (4 KB) input, no nested quantifiers.
- Copy honesty: no «seguro» claims; `QR_TOO_BIG` passes ToolTextsTest forbidden-word checks.
- No crypto primitives, no logs, no network.

---

# Fix round 1 (review of b4b82f8)

## Changes
- IMPORTANT 1, host mapped like a browser (`LinkSignals.kt` `parseLink`, `mapHost`, `normalizeLabel`): after percent-decoding, ideographic/full-width dots become `.`, one trailing dot goes, and every non-ASCII label goes through `IDN.toASCII(label, ALLOW_UNASSIGNED)` (nameprep: NFKC, case folding, invisible characters dropped), lower-cased. Done per label so one bad label does not lose the rest; a label the encoder rejects (too long, or a full-width `xn--` that Java refuses because it starts with the ACE prefix) falls back to NFKC + lower-case + default-ignorables stripped. `Link.host` and IP/SHORTENER/PUNYCODE/MANY_LEVELS all use that ASCII host; `unicodeHost = IDN.toUnicode(host)`. The percent-decoded raw host also goes through `hasHiddenChars` (`ParsedLink.hostHidden` -> HIDDEN_CHARS).
- IMPORTANT 2, PUNYCODE fires for any host whose ASCII form has an `xn--` label (also for a non-ASCII label that could not be encoded). cyrillicA is now `[MIXED_SCRIPTS, PUNYCODE]`; `аррӏе.com` and `банк.es` give `[PUNYCODE]`.
- IMPORTANT 3, ERC-681 (`QrContent.kt` `ethereum`): `.../transfer?address=R` shows `R`, amount null. 13: for ethereum only `value` is read and shown as «N wei»; no `amount`/`uint256` amount (unit unknown). Bitcoin keeps `amount` (BTC).
- 4 `mixesScripts` allows Han+Hiragana+Katakana, Han+Bopomofo, Han+Hangul (subset of one of those sets).
- 5 `Visible.kt` also marks U+00AD, U+115F, U+1160, U+3164, U+FFA0, U+2061-2064, U+180E, U+2028, U+2029, FE00-FE0F, E0100-E01EF. Note: U+FE0F (emoji variation selector) is now marked inside emoji such as ❤️.
- 6 numeric hosts: `canonicalIpv4` (WHATWG: decimal/octal/hex parts, last part fills remaining bytes). `3232235777` -> `192.168.1.1`, `0x7f.1` -> `127.0.0.1`. An invalid numeric host (`1.2.3.256`, `evil.123`) is kept as written, still IP_HOST.
- 7 the port starts at the first `:` outside brackets. 8 `ftps` is no longer special (still a link scheme, default port 990). 9 only one trailing dot is removed.
- 10 documented by test `aLinkKeepsBeingALinkEvenWithASignedTextMarkerInIt` (Link, with HIDDEN_CHARS from the inner line break).
- 11 `www.` + no whitespace, longer than 4 chars -> `Link(uri = text, scheme = "", host…, signals = those of "https://" + text)`. Task 7: `scheme` can be the empty string.
- 12 the 4 KB delimiter-wall fuzz now calls `classifyQrUnguarded` (no catch-all, no size check) before `classifyQr`.

## Covering tests
LinkSignalsTest: `invisibleCharacterInTheHostDoesNotHideAShortener`, `fullWidthDigitsStillMakeAnIpHost`, `fullWidthLettersStillMakeAShortener`, `fullWidthXnStillMakesPunycode`, `aLabelTheEncoderRejectsStillCountsAsPunycode`, `cyrillicAInsideALatinLabelIsMixedScripts` (updated), `aWholeLabelInOneForeignScriptIsNotMixedButIsPunycode`, `scriptsWrittenTogetherInOneLanguageAreNotMixed`, `numericHostsShowTheAddressTheBrowserOpens`, `onlyOneTrailingDotGoes`, `thePortStartsAtTheFirstColonAfterTheHost`, `ftpsHasNoSpecialSlashHandling`. QrContentTest: `ethereumTokenTransferShowsTheRecipientNotTheContract`, `cryptoPayments` (wei), `aLinkKeepsBeingALinkEvenWithASignedTextMarkerInIt`, `wwwWithoutSchemeIsALinkWithTheSignalsOfHttps`, `visibleTextMarksTheOtherInvisibleCharacters`, fuzz wall.

## Commands and output
- RED: with the fix reverted (`git stash push -- app/src/main`), `gradle -p jvm-check test --tests 'app.seguridad.tools.qr.*'`: `60 tests completed, 18 failed` (all the new/updated tests above except the documentation test of item 10).
- GREEN: same command with the fix: `BUILD SUCCESSFUL` (LinkSignalsTest 35, QrContentTest 25 tests).
- Full suite once: `gradle -p jvm-check test --no-daemon --max-workers=1 --offline`: `BUILD SUCCESSFUL`, 767 tests, 0 failures, 0 skipped; ToolTextsTest included.
- Android line not run (no ui/ or android/ touched).

## Revisión experto-seguridad-cripto (fix round)
- Browser/inspector differential closed for the host: `LinkSignals.kt` `mapHost` (per-label UTS #46 mapping, fallback `normalizeLabel`), `parseLink` hostHidden/isIp/canonical IPv4; tests above.
- Whole-script homograph visible through `xn--`: `signalsOf` PUNYCODE branch; `LinkSignalsTest.aWholeLabelInOneForeignScriptIsNotMixedButIsPunycode`.
- Token-transfer phishing display (CWE-451): `QrContent.kt` `ethereum`; test `ethereumTokenTransferShowsTheRecipientNotTheContract`.
- Invisible/format characters (Trojan Source family): `Visible.kt` `isHiddenCodePoint`; test `visibleTextMarksTheOtherInvisibleCharacters`.
- Totality: `QrContentTest.fuzzFromRandomCharsAndLongInputs` runs the unguarded classifier on the 4 KB walls; no new regex; `IDN` calls in try/catch.
- Remaining limits: confusables inside one script (Latin-only lookalikes such as `rn`/`m`) are not detected; UI must show `unicodeHost` and `host` both for xn-- hosts.

---

# Fix round 2 (re-review of 8ea2557)

## Changes
- IMPORTANT, IDNA2003 vs UTS #46 (`LinkSignals.kt` `mapLabel`): a non-ASCII label is NFKC-folded and lower-cased first; if it still has `ß` (U+00DF) or `ς` (U+03C2) it is NOT given to `IDN.toASCII` (which would give `ss` / `σ`) but kept Unicode via `normalizeLabel`, so PUNYCODE fires (non-ASCII label) and the host keeps the `ß`. `https://faß.de` -> host `faß.de`, `[PUNYCODE]`; `straße-bank.de` likewise; `ςα.gr` likewise; `faẞ` folds to `faß`. `unicodeHost` is now built label by label (`toUnicodeHost`): only `xn--` labels are decoded, so a kept `ß` is not turned into `ss` by `IDN.toUnicode`.
- Minor 1: NFKC before `toASCII` (same `mapLabel`); an all-ASCII result skips `IDN` entirely. `🄱🄸🅃.ly` -> host `bit.ly`, `[SHORTENER]`.
- Minor 2 (`QrContent.kt` `crypto`): if `address`, `value` or `amount` appears twice (case-insensitive key) the QR is `PlainText(original text)`, no payment framing (also for `bitcoin://` which would otherwise become a Link). New `queryPairs` keeps order and repeats.
- Minor 3 (`ethereum`): any function call other than `transfer` with a query `address` (`approve`, `transferFrom`, `transfer` without recipient) -> `PlainText`.
- Minor 4 (`Visible.kt`): U+FE0E/U+FE0F directly after an emoji or symbol (previous code point >= U+1F000 or `Character.OTHER_SYMBOL`) are not marked by `visibleText` or `hasHiddenChars`; elsewhere (and FE00-FE0D, E0100-E01EF always) they are. `hasHiddenChars(text, allowEmojiVariation = true)` has a new optional parameter; the host check (`parseLink`) passes `false`, so any variation selector in a host raises HIDDEN_CHARS (a browser drops it: `❤️.ws` = `❤.ws`). In a path after an emoji it does not.

## Covering tests
LinkSignalsTest: `sharpSAndFinalSigmaAreKeptLikeABrowserDoes`, `charactersNewerThanUnicode32AreFoldedBeforeEncoding`. QrContentTest: `repeatedPaymentKeysAreNotShownAsAPayment`, `ethereumTokenTransferShowsTheRecipientNotTheContract` (updated: approve, transfer without recipient, transferFrom -> PlainText), `emojiVariationSelectorIsNotMarkedAfterAnEmojiOrSymbol`, `variationSelectorInAHostStillRaisesHiddenChars`, and the earlier `visibleTextMarksTheOtherInvisibleCharacters` (still green: its selectors follow a letter).

## Commands and output
- RED: `git stash push -- app/src/main` then `gradle -p jvm-check test --tests 'app.seguridad.tools.qr.*'`: `65 tests completed, 6 failed` (the six tests above).
- GREEN: same command with the fix: `BUILD SUCCESSFUL` (LinkSignalsTest 37, QrContentTest 28).
- Full suite once: `gradle -p jvm-check test --no-daemon --max-workers=1 --offline`: `BUILD SUCCESSFUL`, 772 tests, 0 failures, 0 skipped; ToolTextsTest included. Android line not run (no ui/ or android/ touched).

## Revisión experto-seguridad-cripto (fix round 2)
- IDNA2003/UTS #46 deviation characters: `LinkSignals.kt` `mapLabel`, `toUnicodeHost`; tests `sharpSAndFinalSigmaAreKeptLikeABrowserDoes`.
- Normalisation before encoding: `mapLabel`; test `charactersNewerThanUnicode32AreFoldedBeforeEncoding`.
- Ambiguous payment requests are not framed as payments (CWE-451): `QrContent.kt` `crypto` (repeated keys), `ethereum` (function calls); tests above.
- Emoji presentation vs hidden-character marking: `Visible.kt` `isHiddenCodePoint`; host stays strict.
- Known limit: a final-sigma/sharp-s host is shown with its Unicode letters and PUNYCODE, but the `xn--` form the browser actually connects to is not computed (Java cannot encode it with UTS #46 rules); the screen should show `host` and `unicodeHost` as given.
