#!/bin/sh
# 4K screenshots for the README: installs the debug APK on a running Android TV emulator, logs in
# with demo (fake data), walks through the screens with D-pad keys and renders each one at 2×
# (3840×2160) with the debug-only ScreenshotProvider. Usage: ./tools/screenshots.sh [out-dir]
set -eu
cd "$(dirname "$0")/.."
OUT="${1:-screenshots}"
ADB="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
PKG=pl.prywatny.dziennik.tv
APK=app/build/outputs/apk/debug/app-debug.apk
[ -f "$APK" ] || { echo "Brak $APK. Najpierw: ./gradlew assembleDebug" >&2; exit 1; }
mkdir -p "$OUT"

k() { "$ADB" shell input keyevent "$1"; sleep "${2:-0.8}"; }
shot() {
  sleep 1.5
  "$ADB" shell am broadcast -a "$PKG.SCREENSHOT" --es name "$1" --ef scale 2 -p "$PKG" >/dev/null
  "$ADB" pull "/sdcard/Android/data/$PKG/files/screenshots/$1.png" "$OUT/$1.png" >/dev/null 2>&1
  echo "$1"
}
open_section() { # back to the menu, move down N items, open with OK
  k KEYCODE_BACK 1
  i=0; while [ "$i" -lt "$1" ]; do k KEYCODE_DPAD_DOWN 0.5; i=$((i + 1)); done
  sleep 0.8
  k KEYCODE_DPAD_CENTER 2
}

"$ADB" install -r "$APK" >/dev/null
"$ADB" shell pm clear "$PKG" >/dev/null
"$ADB" shell am start -n "$PKG/.MainActivity" >/dev/null
sleep 6
shot 01-logowanie
k KEYCODE_DPAD_DOWN; k KEYCODE_DPAD_DOWN; k KEYCODE_DPAD_RIGHT
k KEYCODE_DPAD_CENTER 6
k KEYCODE_DPAD_RIGHT 1
shot 02-start
open_section 1
shot 03-plan-lekcji
open_section 1; k KEYCODE_DPAD_DOWN
shot 04-oceny
open_section 1
shot 05-terminarz
open_section 1
shot 06-frekwencja
open_section 1; sleep 2
shot 07-wiadomosci
k KEYCODE_DPAD_CENTER 3
shot 08-wiadomosc
k KEYCODE_BACK 1.5
open_section 3
shot 09-konto
