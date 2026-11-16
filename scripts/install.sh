#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
./gw assembleDebug
serial=${ANDROID_SERIAL:-$(adb devices | awk 'NR > 1 && $2 == "device" { print $1; exit }')}
[ -n "$serial" ] || { echo "tablet not connected over adb" >&2; exit 1; }
adb -s "$serial" install -r app/build/outputs/apk/debug/app-debug.apk
# The panel is an overlay window; this grants "Display over other apps" without a settings screen.
adb -s "$serial" shell appops set dev.victorstein.reycast SYSTEM_ALERT_WINDOW allow
