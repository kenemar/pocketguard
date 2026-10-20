# PocketGuard

Pocket keyguard for the **TIQ M5 mini** (Android 13, numeric keypad + touchscreen).

While the proximity sensor is covered (phone in a pocket) and the display is on, an
accessibility service swallows every keypad press and touch, shows a small
"Input blocked" pill, and turns the display off after 10 s (configurable).
Blocking starts 0.5 s after the sensor trips, so a finger crossing the sensor during a
swipe from the top edge does not cancel the gesture.

* Toggle from the app or its Quick Settings tile. No notification, no foreground
  service, no wakelocks; the only cost is one on-change sensor listener while the
  screen is on.
* Escape sequence: press `* # * #` on the keypad while blocked (max 2 s between keys)
  to turn the guard off, in case the sensor misbehaves. Editable in the app.
* POWER cannot be blocked by any app; it only wakes the display, after which the guard
  re-engages.

## Install on the TIQ M5

Enable USB debugging on the phone, connect it, then from a PC with `adb`:

```sh
adb install -r pocketguard.apk          # from the Releases page, or build/ after ./build.sh
adb shell settings put secure enabled_accessibility_services dev.kendall.pocketguard/.GuardService
adb shell settings put secure accessibility_enabled 1
adb shell cmd statusbar add-tile dev.kendall.pocketguard/.GuardTileService   # optional tile
```

Alternatively enable "PocketGuard" under Settings > Accessibility on the phone. If
Android shows "Restricted setting", run
`adb shell appops set dev.kendall.pocketguard ACCESS_RESTRICTED_SETTINGS allow` and retry.

Last-resort disable from a PC: `adb shell settings put secure enabled_accessibility_services ""`.

## Build (no Android Studio)

Needs JDK 17 and the Android command-line SDK (`platforms;android-33`,
`build-tools;34.0.0`) in `~/android-sdk`; paths are in `env.sh`.

```sh
./build.sh            # -> build/pocketguard.apk
./build.sh install    # build + adb install -r
```

A debug signing key is generated into `keystore/` on first build (ignored by git).

## Debugging

```sh
adb logcat -s PocketGuard
B="adb shell am broadcast -n dev.kendall.pocketguard/.DebugReceiver"   # adb-only test hook
$B --ez near true            # simulate a covered sensor (screen must be on)
$B --ez near false
$B --es escape "'1 3 7 9'" --ei delay 30    # change settings (note the inner quotes)
```

`adb shell input keyevent` bypasses the accessibility filter and is not a valid test of
key blocking; real keypad presses, or a virtual HID keyboard via `adb shell hid`, are.
