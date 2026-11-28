#!/usr/bin/env bash
# A17: checks the final merged manifest of a built APK or, since 2.1.2 (R-29), of an Android App Bundle (AAB).
#
#   bash scripts/check-manifest.sh [--release] ruta/al.apk
#   bash scripts/check-manifest.sh [--release] ruta/al.aab
#
# An .aab is read through its base module: base/manifest/AndroidManifest.xml and base/resources.pb (the proto
# manifest and resource table that Play turns into the APKs it serves) are copied into a temporary proto APK, which
# aapt2 reads like any other APK, so the AAB gets exactly the same checks with no other tool (no bundletool). An AAB
# with any other module (a manifest that is not base/'s) fails: Clavero has only the base module.
#
# Fails (exit 1) if:
#   - a component (activity, activity-alias, service, receiver, provider) is exported, other than
#     app.seguridad.MainActivity and androidx.profileinstaller.ProfileInstallReceiver (this one only with
#     android.permission.DUMP). A component with an intent-filter and no android:exported counts as exported;
#   - androidx.core.content.FileProvider is missing, exported or without grantUriPermissions="true";
#   - app.seguridad.MainActivity is missing or has no empty android:taskAffinity (2.0, audit B-2, the owner's decision
#     of 21-11-2026: in aapt2 dump xmltree, A: android:taskAffinity(0x01010012)="" inside its E: activity), so no other
#     app's activity can join Clavero's task. It is the only difference of the manifest from Clavero 1.4.3 besides
#     versionName and versionCode (the CI sets it, the run's number);
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
  echo "Uso: bash scripts/check-manifest.sh [--release] ruta/al.apk|ruta/al.aab" >&2
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
[ -f "$apk" ] || { echo "check-manifest: no existe el APK o el AAB $apk" >&2; exit 2; }

find_aapt2() {
  if [ -n "${AAPT2:-}" ]; then echo "$AAPT2"; return; fi
  local sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/opt/android-sdk}}" newest
  newest="$(ls -d "$sdk"/build-tools/*/ 2>/dev/null | sort -V | tail -n 1)"
  if [ -n "$newest" ] && [ -x "${newest}aapt2" ]; then echo "${newest}aapt2"; return; fi
  command -v aapt2 || true
}
AAPT2_BIN="$(find_aapt2)"
[ -n "$AAPT2_BIN" ] && [ -x "$AAPT2_BIN" ] || { echo "check-manifest: no se encuentra aapt2 (define AAPT2 o ANDROID_HOME)" >&2; exit 2; }

# R-29: an AAB is checked through a proto APK with its base module's manifest and resource table.
dumped="$apk"
if [[ "$apk" == *.aab ]]; then
  command -v unzip >/dev/null && command -v zip >/dev/null || { echo "check-manifest: faltan unzip o zip para leer el AAB" >&2; exit 2; }
  modules="$(unzip -Z1 "$apk" | grep -E '^[^/]+/manifest/AndroidManifest\.xml$' || true)"
  [ "$modules" = "base/manifest/AndroidManifest.xml" ] || { echo "check-manifest: el AAB no tiene solo el módulo base (manifiestos: ${modules:-ninguno})" >&2; exit 1; }
  aab_tmp="$(mktemp -d)"
  trap 'rm -rf -- "$aab_tmp"' EXIT
  unzip -q -j "$apk" base/manifest/AndroidManifest.xml base/resources.pb -d "$aab_tmp" \
    || { echo "check-manifest: no se pueden sacar el manifiesto y resources.pb del módulo base de $apk" >&2; exit 2; }
  (cd "$aab_tmp" && zip -q -X base.apk AndroidManifest.xml resources.pb)
  dumped="$aab_tmp/base.apk"
fi

tree="$("$AAPT2_BIN" dump xmltree --file AndroidManifest.xml "$dumped")"
perms="$("$AAPT2_BIN" dump permissions "$dumped")"

errors=0
fail() { echo "check-manifest: ERROR: $*" >&2; errors=$((errors + 1)); }

# One tab-separated line per component: kind, name, exported, permission, has an intent-filter, grantUriPermissions,
# taskAffinity, and one line "debuggable<TAB>value" for <application>. An attribute belongs to the element whose indentation is two
# spaces less than its own. "-" means the attribute is absent. Values are aapt2's (true, false, or @0x... for a
# resource reference, which the checks below treat as "not false", i.e. fail closed).
components="$(printf '%s\n' "$tree" | awk '
  function indent(s) { match(s, /^ */); return RLENGTH }
  function flush() {
    if (kind != "") print kind "\t" name "\t" exported "\t" perm "\t" filter "\t" grant "\t" affinity
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
      kind = el; compIndent = i; name = "-"; exported = "-"; perm = "-"; filter = "no"; grant = "-"; affinity = "-"
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
      else if (key == "taskAffinity") affinity = (value(attr) == "" ? "(vacía)" : value(attr))
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
main_activity=0
while IFS=$'\t' read -r kind name exported perm filter grant affinity; do
  [ -n "$kind" ] && [ "$kind" != "debuggable" ] || continue
  if [ "$kind" = "activity" ] && [ "$name" = "$MAIN_ACTIVITY" ]; then
    main_activity=$((main_activity + 1))
    [ "$affinity" = "(vacía)" ] || fail "$MAIN_ACTIVITY sin android:taskAffinity=\"\" (taskAffinity=$affinity): otra app podría meter una pantalla suya en la tarea de Clavero (B-2)"
  fi
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

[ "$main_activity" -eq 1 ] || fail "$MAIN_ACTIVITY aparece $main_activity veces en el manifiesto (debe estar una vez)"
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
