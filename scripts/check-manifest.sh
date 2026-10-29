#!/usr/bin/env bash
# A17: checks the final merged manifest of a built APK.
#
#   bash scripts/check-manifest.sh [--release] ruta/al.apk
#
# Fails (exit 1) if:
#   - a component (activity, activity-alias, service, receiver, provider) is exported, other than
#     app.seguridad.MainActivity and androidx.profileinstaller.ProfileInstallReceiver (this one only with
#     android.permission.DUMP). A component with an intent-filter and no android:exported counts as exported;
#   - androidx.core.content.FileProvider is missing, exported or without grantUriPermissions="true";
#   - the permissions (aapt2 dump permissions) are not exactly those of Seguridad 1.2.2 (EXPECTED_PERMISSIONS);
#   - with --release, the APK is debuggable.
# A debuggable APK (android:debuggable="true", the local debug APK) also may export
# androidx.compose.ui.tooling.PreviewActivity, which Compose's ui-tooling (debugImplementation only) adds; a
# non-debuggable APK never may. CI runs this with --release on the release APK, so a release that were made
# debuggable fails there instead of being checked with the debug rules.
#
# aapt2: $AAPT2, else the newest $ANDROID_HOME/build-tools/*/aapt2 (or /opt/android-sdk), else the one on PATH.
set -euo pipefail

# From `aapt2 dump permissions` on the Seguridad 1.2.2 release APK (commit 71c9964); its debug APK gives the same.
# A new or removed permission changes this list, in review.
EXPECTED_PERMISSIONS="package: io.github.egt17baa.seguridad
uses-permission: name='android.permission.INTERNET'
uses-permission: name='android.permission.CAMERA'
uses-permission: name='android.permission.USE_BIOMETRIC'
uses-permission: name='android.permission.USE_FINGERPRINT'
permission: io.github.egt17baa.seguridad.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION
uses-permission: name='io.github.egt17baa.seguridad.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION'"

MAIN_ACTIVITY="app.seguridad.MainActivity"
PROFILE_RECEIVER="androidx.profileinstaller.ProfileInstallReceiver"
PREVIEW_ACTIVITY="androidx.compose.ui.tooling.PreviewActivity"
FILE_PROVIDER="androidx.core.content.FileProvider"

usage() {
  echo "Uso: bash scripts/check-manifest.sh [--release] ruta/al.apk" >&2
  exit 2
}

release=false
apk=""
for arg in "$@"; do
  case "$arg" in
    --release) release=true ;;
    -*) usage ;;
    *) [ -z "$apk" ] || usage; apk="$arg" ;;
  esac
done
[ -n "$apk" ] || usage
[ -f "$apk" ] || { echo "check-manifest: no existe el APK $apk" >&2; exit 2; }

find_aapt2() {
  if [ -n "${AAPT2:-}" ]; then echo "$AAPT2"; return; fi
  local sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/opt/android-sdk}}" newest
  newest="$(ls -d "$sdk"/build-tools/*/ 2>/dev/null | sort -V | tail -n 1)"
  if [ -n "$newest" ] && [ -x "${newest}aapt2" ]; then echo "${newest}aapt2"; return; fi
  command -v aapt2 || true
}
AAPT2_BIN="$(find_aapt2)"
[ -n "$AAPT2_BIN" ] && [ -x "$AAPT2_BIN" ] || { echo "check-manifest: no se encuentra aapt2 (define AAPT2 o ANDROID_HOME)" >&2; exit 2; }

tree="$("$AAPT2_BIN" dump xmltree --file AndroidManifest.xml "$apk")"
perms="$("$AAPT2_BIN" dump permissions "$apk")"

errors=0
fail() { echo "check-manifest: ERROR: $*" >&2; errors=$((errors + 1)); }

# One tab-separated line per component: kind, name, exported, permission, has an intent-filter, grantUriPermissions,
# and one line "debuggable<TAB>value" for <application>. An attribute belongs to the element whose indentation is two
# spaces less than its own. "-" means the attribute is absent. Values are aapt2's (true, false, or @0x... for a
# resource reference, which the checks below treat as "not false", i.e. fail closed).
components="$(printf '%s\n' "$tree" | awk '
  function indent(s) { match(s, /^ */); return RLENGTH }
  function flush() {
    if (kind != "") print kind "\t" name "\t" exported "\t" perm "\t" filter "\t" grant
    kind = ""
  }
  function value(s,   v) {
    v = s
    sub(/^[^=]*=/, "", v)
    sub(/ \(Raw: .*$/, "", v)
    gsub(/"/, "", v)
    return v
  }
  BEGIN { kind = ""; compIndent = -1; appIndent = -1 }
  /^ *E: / {
    i = indent($0)
    el = $0; sub(/^ *E: /, "", el); sub(/ .*/, "", el)
    if (kind != "" && i <= compIndent) flush()
    if (el == "application") appIndent = i
    if (el == "activity" || el == "activity-alias" || el == "service" || el == "receiver" || el == "provider") {
      kind = el; compIndent = i; name = "-"; exported = "-"; perm = "-"; filter = "no"; grant = "-"
    } else if (kind != "" && el == "intent-filter" && i > compIndent) {
      filter = "yes"
    }
    next
  }
  /^ *A: / {
    a = indent($0) - 2
    attr = $0; sub(/^ *A: /, "", attr)
    key = attr; sub(/\(0x[0-9a-f]+\)=.*/, "", key); sub(/=.*/, "", key); sub(/^.*:/, "", key)
    if (a == appIndent && key == "debuggable") print "debuggable\t" value(attr)
    if (kind != "" && a == compIndent) {
      if (key == "name") name = value(attr)
      else if (key == "exported") exported = value(attr)
      else if (key == "permission") perm = value(attr)
      else if (key == "grantUriPermissions") grant = value(attr)
    }
  }
  END { flush() }
')"

# Debug rules only for an APK that says it is debuggable, and never with --release.
debuggable="$(printf '%s\n' "$components" | awk -F '\t' '$1 == "debuggable" { print $2 }')"
debug=false
if [ "$debuggable" = "true" ]; then
  if [ "$release" = true ]; then
    fail "--release con un APK debuggable (android:debuggable=true): una release no puede serlo"
  else
    debug=true
  fi
elif [ -n "$debuggable" ] && [ "$debuggable" != "false" ]; then
  fail "android:debuggable con un valor que no es true ni false: $debuggable"
fi

file_provider=0
while IFS=$'\t' read -r kind name exported perm filter grant; do
  [ -n "$kind" ] && [ "$kind" != "debuggable" ] || continue
  # targetSdk >= 31 requires android:exported with an intent-filter; without one, older rules would export it.
  if [ "$exported" = "-" ]; then
    if [ "$filter" = "yes" ]; then is_exported=true; else is_exported=false; fi
  elif [ "$exported" = "false" ]; then
    is_exported=false
  else
    is_exported=true
  fi

  if [ "$name" = "$FILE_PROVIDER" ]; then
    file_provider=$((file_provider + 1))
    [ "$kind" = "provider" ] || fail "$FILE_PROVIDER no es un <provider> ($kind)"
    [ "$is_exported" = false ] || fail "$FILE_PROVIDER está exportado (android:exported=$exported)"
    [ "$grant" = "true" ] || fail "$FILE_PROVIDER sin android:grantUriPermissions=\"true\" ($grant)"
    continue
  fi

  [ "$is_exported" = true ] || continue
  if [ "$kind" = "activity" ] && [ "$name" = "$MAIN_ACTIVITY" ]; then
    continue
  elif [ "$kind" = "receiver" ] && [ "$name" = "$PROFILE_RECEIVER" ]; then
    [ "$perm" = "android.permission.DUMP" ] || fail "$PROFILE_RECEIVER exportado sin android.permission.DUMP (permission=$perm)"
  elif [ "$debug" = true ] && [ "$kind" = "activity" ] && [ "$name" = "$PREVIEW_ACTIVITY" ]; then
    echo "check-manifest: $PREVIEW_ACTIVITY exportado, permitido solo porque el APK es debuggable"
  else
    fail "componente exportado no permitido: <$kind> $name (android:exported=$exported, intent-filter=$filter)"
  fi
done <<< "$components"

[ "$file_provider" -eq 1 ] || fail "$FILE_PROVIDER aparece $file_provider veces en el manifiesto (debe estar una vez)"

expected_sorted="$(printf '%s\n' "$EXPECTED_PERMISSIONS" | LC_ALL=C sort)"
actual_sorted="$(printf '%s\n' "$perms" | sed '/^$/d' | LC_ALL=C sort)"
if [ "$expected_sorted" != "$actual_sorted" ]; then
  fail "los permisos no son exactamente los de la 1.2.2 (- esperado, + en el APK):"
  diff <(printf '%s\n' "$expected_sorted") <(printf '%s\n' "$actual_sorted") | grep '^[<>]' | sed 's/^</  -/; s/^>/  +/' >&2 || true
fi

if [ "$errors" -ne 0 ]; then
  echo "check-manifest: $errors error(es) en $apk" >&2
  exit 1
fi
echo "check-manifest: OK ($apk$( [ "$debug" = true ] && echo ', debug'))"
