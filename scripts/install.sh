#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
./gw assembleDebug
serial=$(adb devices | awk 'NR > 1 && $2 == "device" { print $1; exit }')
[ -n "$serial" ] || { echo "tablet not connected over adb" >&2; exit 1; }
adb -s "$serial" install -r app/build/outputs/apk/debug/app-debug.apk
