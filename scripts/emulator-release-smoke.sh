#!/usr/bin/env bash
set -euo pipefail
apk=$(realpath "${1:?Release APK required}")
out=delivery-emulator
package=ir.sabagostar.sabastudio
mkdir -p "$out"
# This probe intentionally refuses a physical phone.
test "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" = 1
adb install -r "$apk" | tee "$out/install.txt"
adb shell cmd locale set-app-locales "$package" --locales fa
adb shell cmd uimode night yes
adb logcat -c
adb shell am force-stop "$package"
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
adb shell screencap -p /sdcard/saba-release-fa-dark.png
adb pull /sdcard/saba-release-fa-dark.png "$out/release-fa-dark.png"
adb shell dumpsys package "$package" > "$out/package.txt"
printf 'Emulator only: release install and 10-second startup liveness passed.\n' > "$out/result.txt"
