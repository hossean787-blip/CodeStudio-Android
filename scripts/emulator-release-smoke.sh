#!/usr/bin/env bash
set -euo pipefail
apk=$(realpath "${1:?Release APK required}")
out=delivery-emulator
package=ir.sabagostar.sabastudio
mkdir -p "$out"
# This probe intentionally refuses a physical phone.
test "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" = 1
adb install -r "$apk" | tee "$out/install.txt"
adb shell pm grant "$package" android.permission.POST_NOTIFICATIONS
adb shell cmd uimode night yes
adb logcat -c
# AppCompat performs legacy locale migration on its first Activity launch.
# Set the requested locale after that migration, then verify the actual UI.
adb shell am start -W -n "$package/com.androidharness.app.MainActivity" > "$out/first-launch.txt"
sleep 2
adb shell am force-stop "$package"
adb shell cmd locale set-app-locales "$package" --user 0 --locales fa
adb shell cmd locale get-app-locales "$package" --user 0 | tee "$out/locales.txt"
grep -q 'fa' "$out/locales.txt"
adb shell am start -W -n "$package/com.androidharness.app.MainActivity" | tee "$out/launch.txt"
grep -q 'Status: ok' "$out/launch.txt"
# Give asynchronous Application/Activity startup a bounded observation window.
for attempt in $(seq 1 10); do
    sleep 1
    adb shell pidof "$package" > "$out/process.txt"
done
adb logcat -d -b crash > "$out/crash-buffer.txt"
if grep -q "Process: $package," "$out/crash-buffer.txt"; then
    echo 'Release app crashed during startup' >&2
    exit 1
fi
adb shell uiautomator dump --compressed /sdcard/saba-release-window.xml
adb pull /sdcard/saba-release-window.xml "$out/window.xml"
python3 - "$out/window.xml" <<'PY'
import sys
from pathlib import Path
text = Path(sys.argv[1]).read_text()
assert "به صبا استودیو خوش آمدید" in text, "Persian Saba welcome text missing from real release UI"
assert "Welcome to AndroidHarness" not in text
PY
adb shell screencap -p /sdcard/saba-release-fa-dark.png
adb pull /sdcard/saba-release-fa-dark.png "$out/release-fa-dark.png"
adb shell dumpsys package "$package" > "$out/package.txt"
printf 'Emulator only: release install and 10-second startup liveness passed.\n' > "$out/result.txt"
