# Stride

**Your walking pad, one screen.**

One-screen Android remote for PitPat walking pads. No account, no internet, no ads.

[![CI](https://github.com/Rick-Pro/stride-android/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/Rick-Pro/stride-android/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/license-MIT-green.svg)](LICENSE)

Start, pause, stop, faster, slower. Time, distance, speed, calories, steps. Every walk saved on the phone. That is the whole app.

Landing page: https://rick-pro.github.io/stride-android/ (GitHub Pages, served from `docs/` on `main`). Privacy policy: https://rick-pro.github.io/stride-android/privacy.html

## Why Stride

The PitPat app that comes with the pad wants an account, plays sounds, shows a 3D race and a leaderboard, and buries the one thing a walking pad needs: a speed control you can hit with a thumb. Stride keeps only that.

- **One screen.** The home screen has everything a walk needs. History and Settings are one tap away and Back brings you home.
- **Connects by itself.** Set it up once. Every later launch finds the pad and connects without a tap.
- **Honest about the pad.** If the Bluetooth link drops, the belt keeps running at the same speed. Stride buzzes, says so, and reconnects. The pad's own remote keeps working.
- **No account, no internet, no ads, no telemetry.** The app has no internet permission at all. The build fails if it ever asks for one.

## Compatible pads

Stride is for walking pads that use the **PitPat app**. Brands that sell them include **PitPat**, **Superun** and **DeerRun**.

**Rule of thumb:** if the pad's manual says to download the PitPat app, Stride should work with it.

| Pad | Status |
|---|---|
| PitPat **HY-BA06B1** (sold as BA06-B1, shows as `PitPat-T01` over Bluetooth) | **Tested** on a Google Pixel 8 Pro: connect, start, speed, stop and live status |
| Other PitPat, Superun and DeerRun pads | Expected to work, **not tested** by us. [Tell us how it went](https://github.com/Rick-Pro/stride-android/issues/new/choose) |

Stride finds a pad by its Bluetooth name (it starts with `PitPat` or `BA0`) or by the Bluetooth service it advertises. If **Find** never lists your pad, open an issue with the pad's model and the name Android's Bluetooth settings shows for it.

**How it talks to the pad.** The HY-BA06B1 speaks the PitPat Bluetooth LE protocol. Stride looks at what the pad offers and uses the first of three layouts it finds: `FBA0` ("PitPat"), `FF01`/`FF02` with a 4-byte header ("PitPat wrapped"), or `FFF1`/`FFF2` ("PitPat FFF"). The first has been verified on real pads by other projects. The second is what Ricardo's HY-BA06B1 offers: Stride connects to it, reads its status, starts, changes speed and stops the belt (all tried on the pad). The third is written from published protocol work and has not been tried on a pad. Settings shows which layout is in use, and the Diagnostics log lists every service the pad offers. The protocol is in `docs/protocol.md`.

Works on Android 13 or newer. Tested on a Google Pixel 8 Pro.

## Install

Stride is not on Google Play, so you install the file yourself. No root, no account.

1. **Download.** On the phone, open the [latest release](https://github.com/Rick-Pro/stride-android/releases) and tap `stride-vX.Y.Z.apk`. If the browser warns that the file can harm your device, tap Download anyway.
2. **Open it.** Tap Open when the download finishes, or open Files, then Downloads, and tap the file.
3. **Allow installs from your browser.** Android says your browser is not allowed to install unknown apps. Tap Settings, turn on Allow from this source, and go back. You can turn it off again later in Settings, Apps, Special app access, Install unknown apps.
4. **Let Google Play Protect scan it.** Play Protect has not seen Stride before, so it says App scan recommended. Tap Scan app. It takes a few seconds.
5. **Install.** When Play Protect says This app looks safe, tap Install.
6. **Open Stride** from the app drawer and follow Setup below.

**If the install is blocked.** Samsung Auto Blocker and Google Advanced Protection (Android 16 and newer) stop apps installed from a file. Turn the one you have off, install, then turn it back on. "App not installed as package conflicts with an existing package" means a copy made with a different key is installed: uninstall it first.

**Updating.** Download the new file and install it the same way. It installs over the old copy and keeps your settings and history, because every release carries the same certificate.

**Check the download (optional).** Each release has a `.sha256` file next to each file it carries (the APK, and the `.aab` that is only for the Play Console). On a computer, put both in one folder and run `sha256sum -c stride-vX.Y.Z.apk.sha256`. The release notes also list the certificate SHA-256, which is the same for every release.

**Test builds (for Ricardo's own devices).** The [Latest test build](https://github.com/Rick-Pro/stride-android/releases/tag/test-latest) is rebuilt after every change to `main`. Download `stride-test-<version>.apk` (the version is in the file name) and install it the same way. It shows up as **Stride Dev**, next to a release copy, and each new test build installs over the last one.

**Uninstall.** Touch and hold the Stride icon, tap App info, then Uninstall. Android removes the app, its settings and its history. Share a walk as CSV first if you want to keep it.

## Setup (once)

1. Close the PitPat app (swipe it away or force stop it). The pad accepts one connection at a time, and while PitPat holds it the pad is invisible to everything else.
2. Turn the pad on. Its display shows `0:00`.
3. Open Stride and tap **Find**. Allow Nearby devices when Android asks (that is the Bluetooth permission; Stride never uses location).
4. The pad shows up within 15 seconds under its Bluetooth name (for example `BA06-73F6` or `PitPat-T01`). Tap it. If it is the only pad around, Stride picks it on its own.
5. The status line turns green: connected. Done. From now on every launch reconnects by itself.

## What it does

**The home screen**, top to bottom:

- Status line: the pad's name and Connected, Connecting, Reconnecting or Not found.
- The speed, big, in mph (or km/h, one tap in Settings).
- Four numbers: time, distance, calories, steps. Steps are marked **est.** when the pad does not count them and Stride estimates them from the distance (with your step length, if you filled in **Me**). Calories are the pad's own number until you fill in **Me**; then they are Stride's estimate, marked **est.** The HY-BA06B1 reports 0 calories and 0 steps on every walk, so both come from Stride.
- **SPD -** and **SPD +** on either side of **Start / Pause**. Each tap moves the target one step (0.1 km/h, the pad's own step). They work while the belt runs; the preset chips pick the speed for the next start. Start sends the last speed you used; Pause holds the belt and the time.
- **The countdown.** After Start the pad beeps for about 3 seconds before the belt moves. The screen shows a big **Starting** with a pulsing ring and a 3-2-1 count, and changes by itself when the pad says the belt is running. **Paused** is a clear badge, and **Stopping** shows while the pad says stopped but the belt is still slowing down.
- **STOP**, wide and red. It goes first in line, before anything else waiting to be sent, and is resent until the belt stops.
- Four preset chips (1.0, 1.5, 2.0, 2.5 mph by default; change them in Settings).
- A small row: units, History, Settings.

When the app opens from scratch (a cold start, for example after swiping it out of Recents), Android's own splash screen shows the Stride icon on the app's dark background until the first screen is ready. It is the system splash from the theme alone: no library, no extra screen and no added wait, and on a fast phone it is gone in a fraction of a second. Opening Stride while it is still in memory shows no splash; that is how Android works.

The screen stays on while the belt moves. Buttons that cannot work right now say why (not connected, no data yet).

**History.** Every walk longer than 30 seconds is saved when the belt stops: date, time, distance, average and max speed, calories, steps, and a speed chart. Totals for this week and all time at the top. Long-press a walk to delete it (it asks first). Share exports every walk as CSV. Walks are not editable; the numbers are the pad's.

**Treadmill (in Settings).** A small card with what the pad reports about itself: the protocol Stride is using, firmware, the pad's id, max speed, and the pad's own step count next to Stride's estimate. Heart rate and incline appear only when a pad sends a value other than 0. An unknown internal value that follows belt load is written to the Diagnostics log as `load` and never shown. The pad's id is masked in that log.

**Me (in Settings, optional).** Sex, height, weight and, if you like, age, in the units you picked. They stay in this app's private storage on the phone: no account, no network, no backup. They are used for three things only: the calorie estimate (the standard walking equation from the American College of Sports Medicine: MET from speed, then MET x kg x hours; it is an estimate, not a measurement); the step length for the step estimate (0.413 x height for women, 0.415 x height for men, in between for other); and your weight, sent to the pad in each command so the pad's own calorie count has it. The weight is hidden in the Diagnostics log. Remove clears everything.

**Settings.** Units (mph or km), keep the screen on, presets, Me, treadmill beeper on or off (experimental, see `docs/plan.md` D7), Forget treadmill, Diagnostics log (connection events and frames, shareable for a bug report, no addresses), and **About**: the version, which pad Stride is for, the developer (a tap opens ricardouzcategui.com in the browser), credits for the work Stride stands on, the GitHub project, the MIT license line and the privacy policy. Links open in the browser; Stride itself still has no internet permission.

## What it will not do

- No training plans, maps, races, leaderboards or voice. Heart rate and incline show in Settings only if a pad sends them; the HY-BA06B1 has neither.
- **No Wi-Fi.** The pad's Wi-Fi only talks to the PitPat cloud; it has no local interface Stride could use. Everything Stride does goes over Bluetooth.
- No account, no sync, no cloud, no Health Connect. The data stays on the phone.
- No background control. Stride talks to the pad while it is open or just switched away from. Closing the app with the belt stopped lets the link go.
- It cannot stop the belt if Bluetooth is off or the link is gone. The pad's own remote can.

## Privacy

- **No network, ever.** There is no internet permission in the app. CI checks the built APK against `config/allowed-permissions.txt`: Bluetooth scan, Bluetooth connect, vibrate, and nothing else.
- No ads, no telemetry, no analytics, no crash reporting. The library list is AndroidX and Kotlin, enforced by CI against `config/allowed-dependency-groups.txt`.
- Everything stays on the phone: the pad's address in the app's private preferences, walks in one private file. Uninstalling removes all of it.
- You decide what leaves. Share (CSV) and the Diagnostics log only go somewhere when you tap Share.
- The full policy is short: [privacy.html](https://rick-pro.github.io/stride-android/privacy.html) (the same text is in `docs/privacy.md`).

## Troubleshooting

- **Find shows nothing.** The PitPat app is probably still connected: force stop it. Make sure the pad is on and Bluetooth is on. Stand within a few metres.
- **The pad holds one connection.** While Stride (or the PitPat app) is connected, nothing else can find the pad. Tap the treadmill's name at the top of the main screen: Disconnect lets go, Reconnect connects again, Find a treadmill scans again, Forget treadmill starts from scratch.
- **"This device does not speak the PitPat protocol."** The pad offers none of the three layouts. Open Settings, Diagnostics, tap Share, and send it in a bug report: the log lists every service and characteristic the pad offers.
- **Connected, but Start does nothing and says "no data yet".** The pad has not sent a status frame in the last 3 seconds. Wait a moment; if it stays, tap Reconnect in the banner.
- **The belt kept running after the phone lost the link.** That is the pad: it keeps the last speed when the link drops. Use the physical remote. Stride reconnects on its own and will show the walk when the belt stops.
- **Stop not confirmed.** Stride sent STOP three times and the pad still reports the belt moving. Use the remote. Then share the Diagnostics log in a bug report.
- **Speed will not go above a point.** The pad reports its own maximum and Stride caps there (6 km/h, about 3.7 mph, on this family).
- **Play Protect blocks the install.** See Install, If the install is blocked. A new signing key or a new permission makes Play Protect scan it as a new app, so each release is checked.

## Report a bug

Open a [new issue](https://github.com/Rick-Pro/stride-android/issues/new/choose) with the Bug report form. It asks for the pad model, its Bluetooth name and the Diagnostics log: Settings, Diagnostics log, Share, then paste. The log has connection events and frames; it never has the pad's Bluetooth address, your weight or the pad's own id. Issues are public, so read the log before you post it.

## Support

- **Something broke, or you have a different pad?** Use the Bug report form above. A report from a pad we have not tried is the most useful thing you can send.
- **Like it?** Star the repo and tell another walker.
- **Chip in.** The Sponsor button on the repo page goes to [ricardouzcategui.com](https://ricardouzcategui.com) for now. Stride is free to use, with no ads and nothing to buy inside the app.
- **Help build it.** See [CONTRIBUTING.md](CONTRIBUTING.md): the rules are short.

## Repo map

Agents: read this map first, don't scan the repo. Then read `docs/design.md`, `docs/plan.md` and `AGENTS.md`.

| Path | What's inside |
|---|---|
| `docs/design.md` | What Stride is and the contract between `core/` and `app/` |
| `docs/plan.md` | Scope, stack, module map, decision log, what only Ricardo can do, first device run checklist |
| `docs/play-store.md` | One page: putting Stride on Google Play (account, Play App Signing, listing, data safety form, the closed test) |
| `docs/privacy.md`, `docs/privacy.html` | The privacy policy: Stride collects nothing. Same text twice; the page is https://rick-pro.github.io/stride-android/privacy.html, which the About screen and the Play listing link to |
| `docs/protocol.md` | The PitPat BLE protocol with the exact frames and their sources |
| `docs/index.html` | The landing page (GitHub Pages from `docs/`) |
| `core/` | Pure Kotlin logic with JUnit 4 tests, no Android imports: `protocol` (frames, the three pad layouts and how one is picked, the wrapped-pad link), `control` (STOP-first queue, safety rules, step estimate), `units` (mph and miles), `history` (walk record, walk detection, JSON lines, totals) |
| `app/` | The Android app: `ble/` (permissions, scanner, GATT), `session/` (the treadmill manager that owns the link, settings, haptics, log), `history/` (the walks file), `ui/` (Setup, Control, History, Settings, theme). `debug.keystore` is the public Android debug key for test builds, the same file as Kestrel's, not a secret (decisions D6 and D11) |
| `settings.gradle.kts`, `build.gradle.kts`, `gradle/`, `gradlew` | Gradle build. All versions are in `gradle/libs.versions.toml` |
| `VERSION` | The next release version. The Release workflow refuses a tag that does not match |
| `gradle.properties` | Gradle settings: JVM memory, build cache, parallel builds, AndroidX, Kotlin code style |
| `.github/FUNDING.yml`, `.github/ISSUE_TEMPLATE/` | The Sponsor button's target (the developer's site for now) and the Bug report form |
| `.github/workflows/` | `ci.yml`: core tests, then the app build, lint, allow-lists and APK size, on every PR and push to main. `test-build.yml`: the rolling Latest test build after each push to main. `release.yml`: the manual Release (Actions, Release, Run workflow, vX.Y.Z), see Releasing a new version |
| `config/` | Allow-lists CI enforces: the permissions the app may ask for and the dependency groups it may ship |
| `scripts/` | `ci-*.sh`: CI helpers (Gradle error summary, permission, dependency, APK size and debug signer checks). `core-check-offline.sh`: compiles and tests `core/` without network, with the Kotlin compiler from a local Gradle install. `make-release-key.sh`: makes the release key once and sets the GitHub secrets. `release-preflight.sh`, `release-sign.sh`, `release-sign-aab.sh`: the Release workflow's checks and signing of the APK and the App Bundle |
| `.gitignore` | Keeps build output, signing keys (all but the public `app/debug.keystore`), `.env*` files and editor or OS junk out of git |
| `AGENTS.md`, `CLAUDE.md` | Rules for agents working here |
| `LICENSE`, `THIRD_PARTY_NOTICES.md` | The MIT license for Stride; the notices for the work it is built on |
| `CONTRIBUTING.md` | How to help: issues, pull requests, the core tests, the allow-lists, protocol captures |

Any PR that adds, moves or removes a folder or key file updates this table.

## Running the tests

`./gradlew -Pstride.coreOnly=true :core:test` runs the core tests without the Android SDK. Where Gradle has no network either, `scripts/core-check-offline.sh` compiles and tests `core/` with the Kotlin compiler bundled in a local Gradle install. CI builds and lints the app.

## Releasing a new version

1. In the release PR, set `VERSION` to the new X.Y.Z and update this README to match the build: What's new (one row per version) and the Repo map.
2. Merge it to `main`.
3. Go to Actions, Release, Run workflow, keep `main`, and enter `vX.Y.Z` (the same as `VERSION`).
4. The workflow checks the tag, `VERSION` and the release key settings, runs the tests, builds the APK and the Android App Bundle, signs both with the release key (the APK with `apksigner`, the bundle with `jarsigner`), checks that their certificate matches `STRIDE_CERT_SHA256`, and that the APK's permissions are on the allow-list and its size is 8 MB or less, then publishes `stride-vX.Y.Z.apk` and `stride-vX.Y.Z.aab`, each with its `.sha256`. People install the `.apk`. The `.aab` is for the Google Play Console, see `docs/play-store.md`.
5. **Play Protect check.** Install the new APK on the Pixel from the browser, tap Scan app when Play Protect asks, and note "This app looks safe" in the release PR.

**Once, before the first release:** Ricardo runs `scripts/make-release-key.sh` on his own computer. It makes the release key, prints its certificate SHA-256 and, with the GitHub CLI signed in, sets the secrets `STRIDE_KEYSTORE_BASE64` and `STRIDE_KEYSTORE_PASSWORD` and the variables `STRIDE_KEY_ALIAS` and `STRIDE_CERT_SHA256`. Back up the key file and its password in the password manager. If the key is lost, every installed copy must be uninstalled before a new version will install. Until the four settings exist, the Release workflow stops before building and names what is missing.

The exact steps for the key are in `docs/plan.md`, section 5. Playbook: mission-control/sops/android-app-release.md.

## What's new

| Version | What changed |
|---|---|
| 1.0.0 | First release. A one-screen remote for the PitPat walking pad HY-BA06B1 (it shows as PitPat-T01 over Bluetooth): find and remember the pad, reconnect by itself, start with a 3-2-1 countdown, pause, stop, speed up and down, presets, and live time, distance, speed, calories and steps in mph or km. Optional **Me** profile (kept on the phone) for better calories, steps and the pad's own count. A history of every walk with a speed chart, totals, delete and CSV share. A connection menu (reconnect, disconnect, find, forget), a Treadmill card, an About page, a diagnostics log, and an experimental beeper toggle. Two more pad layouts (`FF01`/`FF02`, `FFF1`/`FFF2`) are included; the `FF01`/`FF02` one is what the HY-BA06B1 uses. No account, no internet, no ads. Open source under the MIT license, with a License line and a privacy policy link on the About screen, a CONTRIBUTING guide, a Bug report form and a Compatible pads list. |

## Credits

- The Bluetooth protocol layer, safety rules and GATT transport are ported from [Franrodr10/pitpat-controller](https://github.com/Franrodr10/pitpat-controller) (MIT). Full notice in `THIRD_PARTY_NOTICES.md`.
- The `FF01`/`FF02` layout (4-byte header, write counter, heartbeat after each notification) follows [azmke/pitpat-treadmill-control](https://github.com/azmke/pitpat-treadmill-control) (MIT). Re-implemented in Kotlin, no code copied. Notice in `THIRD_PARTY_NOTICES.md`.
- Protocol facts were cross-checked against [Razzolate99/pitpat-treadmill](https://github.com/Razzolate99/pitpat-treadmill) and [sirfergy/HomeAssistantWalkingPad](https://github.com/sirfergy/HomeAssistantWalkingPad). No code was copied from them.
- Build, CI and release shape from Rick-Pro/kestrel-android.

## The developer

Built by Ricardo Uzcategui, a three-time founder based in San Francisco. Ricardo is building Ethoz, an AI workspace for founders. [ricardouzcategui.com](https://ricardouzcategui.com)

## License

MIT, copyright 2026 Ricardo Uzcategui. See [`LICENSE`](LICENSE). The ported protocol code keeps its own MIT notices, and the other work Stride follows is listed in [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).
