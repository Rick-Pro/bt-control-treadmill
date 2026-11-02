# Stride privacy policy

Last updated: 2 November 2026

Stride is an Android app that remotely controls the PitPat walking pad (HY-BA06B1, sold as BA06-B1). **Stride collects
nothing.** It does not send any information about you or your phone to its developer or to anyone else. Stride is open
source (MIT license), so you can read the code and check every line of this page.

## What Stride does not do
- No account, no sign-in, no email or phone number.
- No internet access. The app does not hold the Android internet permission, so it cannot send data anywhere.
- No ads, no analytics, no crash reporting, no trackers, and no third-party analytics or advertising code.
- No location, contacts, photos, files, camera or microphone.
- No cloud backup. Android backup is switched off for the app.

## What stays on your phone
Stride keeps a few things in its private storage. Only Stride can read them, and uninstalling Stride deletes them.
- **The treadmill you picked:** its name and Bluetooth address, so the app can reconnect.
- **Your walks:** date, time, distance, speed and the calories and steps shown for each.
- **Settings:** units, presets, and whether the screen stays on.
- **Your profile, only if you fill it in:** sex, height, weight and, if you choose, age. It is used on the phone to estimate
  calories and steps, and your weight is sent to the treadmill over Bluetooth with each command so the treadmill's own
  calorie count can use it. You can remove it at any time in Settings.

## What leaves the phone, and only when you choose
- **Bluetooth to your treadmill.** Stride talks to the treadmill over Bluetooth Low Energy: speed commands out, status
  (speed, distance, time) back. That is a direct link between your phone and your treadmill.
- **Share.** If you tap Share in History or in the Diagnostics log, Android opens its share sheet and the text goes only to
  the app you pick. The Diagnostics log has connection events and frames. It never contains your profile values or the
  treadmill's Bluetooth address, and it hides the treadmill's own id.
- **Links.** The About page has links (the developer's site, the project page, the licenses and this policy). They open
  in your browser, which is then governed by that site's own policy.

## Permissions
- **Nearby devices (Bluetooth scan and connect):** to find your treadmill and talk to it. Stride declares that it does not
  use Bluetooth to work out your location.
- **Vibrate:** to buzz the phone if the connection to a moving belt is lost.

## Children
Stride is a utility for treadmill owners. It is not directed at children and collects no information from anyone.

## Changes
If this policy changes, the new version will replace this page and its date will change.

## Contact
Ricardo Uzcategui, https://ricardouzcategui.com. You can also open an issue at
https://github.com/Rick-Pro/stride-android/issues
