#!/usr/bin/env bash
# Verify the exact deliverable, not the Gradle variant name.
set -euo pipefail
apk=$(realpath "${1:?Usage: verify_release.sh APK}")
out=$(dirname "$apk")
sdk=${ANDROID_HOME:-${ANDROID_SDK_ROOT:?Android SDK required}}
tools="$sdk/build-tools/37.0.0"
test -s "$apk"
"$tools/apksigner" verify --verbose --print-certs "$apk" | tee "$out/signature.txt"
"$tools/aapt" dump badging "$apk" > "$out/identity.txt"
grep -q "package: name='ir.sabagostar.sabastudio' versionCode='2' versionName='0.1.0-alpha.2'" "$out/identity.txt"
if grep -q '^application-debuggable' "$out/identity.txt"; then
    echo 'Refusing debuggable APK' >&2
    exit 1
fi
# Retain a machine-readable manifest dump as independent evidence.
"$tools/aapt" dump xmltree "$apk" AndroidManifest.xml > "$out/manifest.txt"
if grep -Eq 'android:debuggable.*(0xffffffff|true)' "$out/manifest.txt"; then
    echo 'Refusing debuggable manifest' >&2
    exit 1
fi
# This build can omit native inference when its NDK is absent. Never hide that.
python3 - "$apk" "$out/native-libraries.txt" <<'PY'
import sys, zipfile
from pathlib import Path
with zipfile.ZipFile(sys.argv[1]) as apk:
    libs = sorted(n for n in apk.namelist() if n.startswith('lib/') and n.endswith('.so'))
Path(sys.argv[2]).write_text('\n'.join(libs) + '\n')
print('Local inference native library bundled:', any(n.endswith('/libharness_local.so') for n in libs))
PY
(cd "$out" && sha256sum "$(basename "$apk")" > SHA256SUMS.txt)
git rev-parse HEAD > "$out/source-commit.txt"
printf 'Non-debuggable manifest verified. Test signing only; see delivery report.\n' > "$out/verification.txt"
cat "$out/SHA256SUMS.txt"
