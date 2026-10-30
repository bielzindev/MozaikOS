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

seed_data() {
  adb root >/dev/null 2>&1; sleep 3; adb wait-for-device
  # Contacts
  for spec in "Ana Souza|+5511988887777|1" "Bruno Lima|+5521977776666|0" "Carla Dias|+5531966665555|1" "Diego Alves|+5541955554444|0"; do
    IFS='|' read -r name phone star <<< "$spec"
    adb shell "content insert --uri content://com.android.contacts/raw_contacts --bind account_type:n: --bind account_name:n: --bind starred:i:$star"
    id=$(adb shell "content query --uri content://com.android.contacts/raw_contacts --projection _id" | tail -1 | grep -oE '_id=[0-9]+' | cut -d= -f2)
    adb shell "content insert --uri content://com.android.contacts/data --bind raw_contact_id:i:$id --bind mimetype:s:vnd.android.cursor.item/name --bind data1:s:'$name'"
    adb shell "content insert --uri content://com.android.contacts/data --bind raw_contact_id:i:$id --bind mimetype:s:vnd.android.cursor.item/phone_v2 --bind data1:s:'$phone' --bind data2:i:2"
  done
  # Incoming SMS through the emulator modem (delivered like a real SMS)
  adb emu sms send 11988887777 "Oi! Tudo bem? Viu o novo Lumia?" || true
  adb emu sms send 21977776666 "Reuniao amanha as 10h" || true
  sleep 2
  adb emu sms send 11988887777 "Me liga quando puder" || true
  # Call log
  now=$(date +%s)000
  adb shell "content insert --uri content://call_log/calls --bind number:s:+5511988887777 --bind type:i:3 --bind date:l:$now --bind duration:i:0 --bind new:i:1" || true
  adb shell "content insert --uri content://call_log/calls --bind number:s:+5521977776666 --bind type:i:2 --bind date:l:$now --bind duration:i:75 --bind new:i:0" || true
  # Media
  adb shell mkdir -p /sdcard/Music /sdcard/DCIM/Camera
  for f in testmedia/*.mp3; do adb push "$f" /sdcard/Music/ >/dev/null; done
  for f in testmedia/*.jpg; do adb push "$f" /sdcard/DCIM/Camera/ >/dev/null; done
  for f in $(adb shell ls /sdcard/Music/*.mp3 /sdcard/DCIM/Camera/*.jpg); do
    adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d "file://$f" >/dev/null
  done
  adb shell content call --uri content://media --method scan_volume --arg external_primary >/dev/null 2>&1 || true
  sleep 5
  echo "seeded:"; adb shell content query --uri content://media/external/audio/media --projection title:artist | head
}

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
# zunex_own: open the ZUNEX screens even though Phone/People/Calendar prefer the phone's apps.
start() { adb shell am start -W -n "$PKG/$1" --ez zunex_own true >/dev/null; }

seed_data
adb shell am force-stop $PKG

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

# By default the Phone and Calendar tiles hand over to the phone's own apps.
adb shell am start -W -n "$PKG/com.wpreborn.apps.phone.PhoneActivity" >/dev/null
shot 08-phone-system 4
adb shell input keyevent KEYCODE_HOME
adb shell am start -W -n "$PKG/com.wpreborn.apps.calendar.CalendarActivity" >/dev/null
shot 09-calendar-system 4
adb shell input keyevent KEYCODE_HOME
sleep 1

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

# Real-data flows
start com.wpreborn.apps.people.PeopleActivity
sleep 3
tap_text "Ana Souza"
shot 40-people-profile 3
adb shell input keyevent KEYCODE_HOME

start com.wpreborn.apps.messages.MessagingActivity
sleep 4
shot 41-messages-threads 1
tap_text "Ana Souza"
shot 42-messages-conversation 3
adb shell input keyevent KEYCODE_HOME

start com.wpreborn.apps.music.MusicActivity
sleep 4
swipe_left; sleep 1
shot 43-music-albums 2
swipe_left; sleep 1
shot 44-music-songs 2
tap_text "Lumia Song"
shot 45-music-now-playing 4
adb shell dumpsys media_session | grep -iE "state=|description" | head -5 > "$OUT/media_session.txt"
adb shell input keyevent KEYCODE_HOME

start com.wpreborn.apps.photos.PhotosActivity
sleep 4
shot 46-photos-collection 1
swipe_left
shot 47-photos-albums 2
swipe_left; sleep 1
adb shell input keyevent KEYCODE_BACK; sleep 1
swipe_left; sleep 1
adb shell input tap 200 500
shot 48-photos-viewer 3
adb shell input keyevent KEYCODE_HOME
shot 49-start-live-tiles 6

# Personalization: press and hold empty Start space
adb shell input keyevent KEYCODE_HOME; sleep 2
adb shell input swipe 540 2000 540 2000 1300
shot 50-personalize 3
adb shell input keyevent KEYCODE_BACK; sleep 2

# All apps with the Metro icon pack
swipe_left
shot 51-all-apps-metro 2

# ZUNEX keyboard: enable it, then open the app list search box
adb shell ime enable $PKG/com.wpreborn.keyboard.ZunexKeyboardService || true
adb shell ime set $PKG/com.wpreborn.keyboard.ZunexKeyboardService || true
adb shell input tap 100 160
shot 52-keyboard 3
adb shell input tap 70 1720
shot 53-keyboard-symbols 2
adb shell input keyevent KEYCODE_BACK; adb shell input keyevent KEYCODE_BACK
adb shell input keyevent KEYCODE_HOME

# Photo editor
start com.wpreborn.apps.photos.PhotosActivity
sleep 4
adb shell input tap 165 570
sleep 3
tap_text "edit"
shot 54-photo-editor 4
tap_text "filter"; sleep 1; tap_text "filter"
shot 55-photo-editor-filter 3
adb shell input keyevent KEYCODE_HOME

# Keyboard settings app
start com.wpreborn.keyboard.KeyboardSettingsActivity
shot 56-keyboard-settings 3
adb shell input keyevent KEYCODE_HOME

# Lock screen preview
adb shell am start -W -n "$PKG/com.wpreborn.launcher.lock.LockActivity" >/dev/null 2>&1 || true
shot 30-lock 3
# Swipe up: the lock screen steps aside (kept alive), then comes back already drawn.
adb shell input swipe 540 1800 540 300 250
shot 31-after-unlock 2
adb shell am start -n "$PKG/com.wpreborn.launcher.lock.LockActivity" >/dev/null 2>&1 || true
shot 32-lock-again 0.3

adb logcat -d -b crash > "$OUT/crashes.txt"
# Crashes of any process (ours or system) are failures: count them for the summary.
grep -c "FATAL EXCEPTION" "$OUT/crashes.txt" > "$OUT/crash_count.txt" || true
adb logcat -d '*:E' | grep -iE "wpreborn|AndroidRuntime|FATAL" > "$OUT/errors.txt" || true
echo "=== crashes ==="
cat "$OUT/crashes.txt"
