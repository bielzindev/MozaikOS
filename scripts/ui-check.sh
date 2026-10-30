#!/usr/bin/env bash
# Smoke test on an emulator: installs the app, opens every screen, takes screenshots and
# collects crashes. Results go to ui-results/ (pushed to the ci-screenshots branch by CI).
set -u
PKG=com.wpreborn.launcher
OUT=ui-results
mkdir -p "$OUT"
APK=$(ls app/build/outputs/apk/full/debug/*.apk | head -1)

adb wait-for-device
adb shell settings put global window_animation_scale 1
adb logcat -c
adb install -r -g "$APK" || { echo "INSTALL FAILED"; exit 0; }
adb shell appops set $PKG SYSTEM_ALERT_WINDOW allow || true
adb shell cmd package set-home-activity $PKG/com.wpreborn.launcher.MainActivity || true

shot() { sleep "${2:-3}"; adb exec-out screencap -p > "$OUT/$1.png"; echo "shot $1"; }
swipe_left() { adb shell input swipe 900 1100 150 1100 250; }
swipe_up() { adb shell input swipe 540 1700 540 600 300; }
# Taps the first view whose text or content-desc matches $1 (uses the accessibility tree).
tap_text() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  local b
  b=$(adb shell cat /sdcard/ui.xml | tr '>' '\n' | grep -iE "(text|content-desc)=\"$1\"" | head -1 | grep -oE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' | grep -oE '[0-9]+' | tr '\n' ' ')
  if [ -n "$b" ]; then
    set -- $b
    adb shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))
    echo "tapped $*"
  else
    echo "text not found: $1"
  fi
}
start() { adb shell am start -W -n "$PKG/$1" >/dev/null; }

# Launcher
adb shell input keyevent KEYCODE_HOME
shot 00-welcome 5
tap_text "done"
shot 01-start 4
swipe_up
shot 02-start-scrolled 2
adb shell input keyevent KEYCODE_HOME
swipe_left
shot 03-all-apps 3
adb shell input keyevent KEYCODE_BACK
# Long press a tile -> edit mode
adb shell input swipe 200 500 200 500 1200
shot 04-edit-mode 2
adb shell input keyevent KEYCODE_BACK
# Action Center
adb shell input swipe 540 30 540 1400 400
shot 05-action-center 2
adb shell input keyevent KEYCODE_BACK
# Volume flyout
adb shell input keyevent KEYCODE_VOLUME_UP
shot 06-volume 1

# Built-in apps: first page, then swipe to the second pivot item
for app in phone.PhoneActivity messages.MessagingActivity music.MusicActivity people.PeopleActivity \
           calendar.CalendarActivity photos.PhotosActivity settings.SettingsActivity; do
  name=${app%%.*}
  start "com.wpreborn.apps.$app"
  shot "10-$name" 4
  swipe_left
  shot "11-$name-pivot2" 2
  adb shell input keyevent KEYCODE_BACK
  sleep 1
  adb shell input keyevent KEYCODE_HOME
  sleep 1
done

# Settings subpages
start com.wpreborn.apps.settings.SettingsActivity
sleep 3
tap_text "start+theme"
shot 20-settings-theme 3
tap_text "Accent color"
shot 21-settings-accent 3
adb shell input keyevent KEYCODE_BACK
adb shell input keyevent KEYCODE_BACK
sleep 1
tap_text "about"
shot 22-settings-about 3
adb shell input keyevent KEYCODE_HOME

# Phone keypad
start com.wpreborn.apps.phone.PhoneActivity
sleep 3
tap_text "keypad"
shot 23-phone-keypad 3

# Lock screen preview
adb shell am start -W -n "$PKG/com.wpreborn.launcher.lock.LockActivity" >/dev/null 2>&1 || true
shot 30-lock 3

adb logcat -d -b crash > "$OUT/crashes.txt"
adb logcat -d '*:E' | grep -iE "wpreborn|AndroidRuntime|FATAL" > "$OUT/errors.txt" || true
echo "=== crashes ==="
cat "$OUT/crashes.txt"
