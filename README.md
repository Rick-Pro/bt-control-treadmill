# Stride

**Your walking pad, one screen.**

Start, pause, stop, faster, slower. Time, distance, speed, calories, steps. Every walk saved on the phone. That is the whole app.

Landing page: https://rick-pro.github.io/bt-control-treadmill/ (GitHub Pages from `docs/`, turn it on once in the repo settings).

## Why Stride

The PitPat app that comes with the pad wants an account, plays sounds, shows a 3D race and a leaderboard, and buries the one thing a walking pad needs: a speed control you can hit with a thumb. Stride keeps only that.

- **One screen.** The home screen has everything a walk needs. History and Settings are one tap away and Back brings you home.
- **Connects by itself.** Set it up once. Every later launch finds the pad and connects without a tap.
- **Honest about the pad.** If the Bluetooth link drops, the belt keeps running at the same speed. Stride buzzes, says so, and reconnects. The pad's own remote keeps working.
- **No account, no internet, no ads, no telemetry.** The app has no internet permission at all. The build fails if it ever asks for one.

## Compatibility

Made for Ricardo's PitPat **HY-BA06B1** (it advertises as `BA06-73F6`), which speaks the PitPat Bluetooth LE protocol on service `FBA0`. Other PitPat-T01 family pads with the same service (DeerRun, Superun, PitPat-T01) are likely to work.

Stride looks at what the pad offers and uses the first of three layouts it finds: `FBA0` ("PitPat"), `FF01`/`FF02` with a 4-byte header ("PitPat wrapped"), or `FFF1`/`FFF2` ("PitPat FFF"). The first has been verified on real pads by other projects. The second is what Ricardo's HY-BA06B1 offers: Stride connects to it and reads its status, but starting and stopping the belt over it are still being tested. The third is written from published protocol work and has not been tried on a pad. Settings shows which layout is in use, and the Diagnostics log lists every service the pad offers. The protocol is in `docs/protocol.md`.

Works on Android 13 or newer. Tested first on a Google Pixel 8 Pro.

## Install

Stride is not on Google Play, so you install the file yourself. No root, no account.

1. **Download.** On the phone, open the [latest release](https://github.com/Rick-Pro/bt-control-treadmill/releases) and tap `stride-vX.Y.Z.apk`. While the repo is private, sign in to GitHub in the browser first. If the browser warns that the file can harm your device, tap Download anyway.
2. **Open it.** Tap Open when the download finishes, or open Files, then Downloads, and tap the file.
3. **Allow installs from your browser.** Android says your browser is not allowed to install unknown apps. Tap Settings, turn on Allow from this source, and go back. You can turn it off again later in Settings, Apps, Special app access, Install unknown apps.
4. **Let Google Play Protect scan it.** Play Protect has not seen Stride before, so it says App scan recommended. Tap Scan app. It takes a few seconds.
5. **Install.** When Play Protect says This app looks safe, tap Install.
6. **Open Stride** from the app drawer and follow Setup below.

**If the install is blocked.** Samsung Auto Blocker and Google Advanced Protection (Android 16 and newer) stop apps installed from a file. Turn the one you have off, install, then turn it back on. "App not installed as package conflicts with an existing package" means a copy made with a different key is installed: uninstall it first.

**Updating.** Download the new file and install it the same way. It installs over the old copy and keeps your settings and history, because every release carries the same certificate.

**Check the download (optional).** Each release has a `.sha256` file next to the APK. On a computer, put both in one folder and run `sha256sum -c stride-vX.Y.Z.apk.sha256`. The release notes also list the certificate SHA-256, which is the same for every release.

**Test builds (for Ricardo's own devices).** The [Latest test build](https://github.com/Rick-Pro/bt-control-treadmill/releases/tag/test-latest) is rebuilt after every change to `main`. Download `stride-test.apk` and install it the same way. It shows up as **Stride Dev**, next to a release copy, and each new test build installs over the last one.

**Uninstall.** Touch and hold the Stride icon, tap App info, then Uninstall. Android removes the app, its settings and its history. Share a walk as CSV first if you want to keep it.

## Setup (once)

1. Close the PitPat app (swipe it away or force stop it). The pad accepts one connection at a time, and while PitPat holds it the pad is invisible to everything else.
2. Turn the pad on. Its display shows `0:00`.
3. Open Stride and tap **Find**. Allow Nearby devices when Android asks (that is the Bluetooth permission; Stride never uses location).
4. The pad shows up within 15 seconds as `BA06-73F6`. Tap it. If it is the only pad around, Stride picks it on its own.
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

The screen stays on while the belt moves. Buttons that cannot work right now say why (not connected, no data yet).

**History.** Every walk longer than 30 seconds is saved when the belt stops: date, time, distance, average and max speed, calories, steps, and a speed chart. Totals for this week and all time at the top. Long-press a walk to delete it (it asks first). Share exports every walk as CSV. Walks are not editable; the numbers are the pad's.

**Treadmill (in Settings).** A small card with what the pad reports about itself: the protocol Stride is using, firmware, the pad's id, max speed, and the pad's own step count next to Stride's estimate. Heart rate and incline appear only when a pad sends a value other than 0. An unknown internal value that follows belt load is written to the Diagnostics log as `load` and never shown. The pad's id is masked in that log.

**Me (in Settings, optional).** Sex, height, weight and, if you like, age, in the units you picked. They stay in this app's private storage on the phone: no account, no network, no backup. They are used for three things only: the calorie estimate (the standard walking equation from the American College of Sports Medicine: MET from speed, then MET x kg x hours; it is an estimate, not a measurement); the step length for the step estimate (0.413 x height for women, 0.415 x height for men, in between for other); and your weight, sent to the pad in each command so the pad's own calorie count has it. The weight is hidden in the Diagnostics log. Remove clears everything.

**Settings.** Units (mph or km), keep the screen on, presets, Me, treadmill beeper on or off (experimental, see `docs/plan.md` D7), Forget treadmill, Diagnostics log (connection events and frames, shareable for a bug report, no addresses), and **About**: the version, which pad Stride is for, the developer (a tap opens ricardouzcategui.com in the browser), credits for the work Stride stands on, and the GitHub project. Links open in the browser; Stride itself still has no internet permission.

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

Settings, Diagnostics, Share, and paste it into a [new issue](https://github.com/Rick-Pro/bt-control-treadmill/issues/new). The log has connection events and frames; it never has the pad's Bluetooth address.

## Repo map

Agents: read this map first, don't scan the repo. Then read `docs/design.md`, `docs/plan.md` and `AGENTS.md`.

| Path | What's inside |
|---|---|
| `docs/design.md` | What Stride is and the contract between `core/` and `app/` |
| `docs/plan.md` | Scope, stack, module map, decision log, what only Ricardo can do, first device run checklist |
| `docs/protocol.md` | The PitPat BLE protocol with the exact frames and their sources |
| `docs/index.html` | The landing page (GitHub Pages from `docs/`) |
| `core/` | Pure Kotlin logic with JUnit 4 tests, no Android imports: `protocol` (frames, the three pad layouts and how one is picked, the wrapped-pad link), `control` (STOP-first queue, safety rules, step estimate), `units` (mph and miles), `history` (walk record, walk detection, JSON lines, totals) |
| `app/` | The Android app: `ble/` (permissions, scanner, GATT), `session/` (the treadmill manager that owns the link, settings, haptics, log), `history/` (the walks file), `ui/` (Setup, Control, History, Settings, theme). `debug.keystore` is the public Android debug key for test builds, the same file as Kestrel's, not a secret (decisions D6 and D11) |
| `settings.gradle.kts`, `build.gradle.kts`, `gradle/`, `gradlew` | Gradle build. All versions are in `gradle/libs.versions.toml` |
| `VERSION` | The next release version. The Release workflow refuses a tag that does not match |
| `gradle.properties` | Gradle settings: JVM memory, build cache, parallel builds, AndroidX, Kotlin code style |
| `.github/workflows/` | `ci.yml`: core tests, then the app build, lint, allow-lists and APK size, on every PR and push to main. `test-build.yml`: the rolling Latest test build after each push to main. `release.yml`: the manual Release (Actions, Release, Run workflow, vX.Y.Z), see Releasing a new version |
| `config/` | Allow-lists CI enforces: the permissions the app may ask for and the dependency groups it may ship |
| `scripts/` | `ci-*.sh`: CI helpers (Gradle error summary, permission, dependency, APK size and debug signer checks). `core-check-offline.sh`: compiles and tests `core/` without network, with the Kotlin compiler from a local Gradle install. `make-release-key.sh`: makes the release key once and sets the GitHub secrets. `release-preflight.sh`, `release-sign.sh`: the Release workflow's checks and signing |
| `.gitignore` | Keeps build output, signing keys (all but the public `app/debug.keystore`), `.env*` files and editor or OS junk out of git |
| `AGENTS.md`, `CLAUDE.md` | Rules for agents working here |
| `LICENSE`, `THIRD_PARTY_NOTICES.md` | All rights reserved for now; the MIT notice for the ported protocol code |

Any PR that adds, moves or removes a folder or key file updates this table.

## Running the tests

`./gradlew -Pstride.coreOnly=true :core:test` runs the core tests without the Android SDK. Where Gradle has no network either, `scripts/core-check-offline.sh` compiles and tests `core/` with the Kotlin compiler bundled in a local Gradle install. CI builds and lints the app.

## Releasing a new version

1. In the release PR, set `VERSION` to the new X.Y.Z and update this README to match the build: What's new (one row per version) and the Repo map.
2. Merge it to `main`.
3. Go to Actions, Release, Run workflow, keep `main`, and enter `vX.Y.Z` (the same as `VERSION`).
4. The workflow checks the tag, `VERSION` and the release key settings, runs the tests, builds the APK, signs it with the release key, checks that its certificate matches `STRIDE_CERT_SHA256` and that its permissions are on the allow-list, then publishes `stride-vX.Y.Z.apk` and its `.sha256`.
5. **Play Protect check.** Install the new APK on the Pixel from the browser, tap Scan app when Play Protect asks, and note "This app looks safe" in the release PR.

**Once, before the first release:** Ricardo runs `scripts/make-release-key.sh` on his own computer. It makes the release key, prints its certificate SHA-256 and, with the GitHub CLI signed in, sets the secrets `STRIDE_KEYSTORE_BASE64` and `STRIDE_KEYSTORE_PASSWORD` and the variables `STRIDE_KEY_ALIAS` and `STRIDE_CERT_SHA256`. Back up the key file and its password in the password manager. If the key is lost, every installed copy must be uninstalled before a new version will install. Until the four settings exist, the Release workflow stops before building and names what is missing.

Playbook: mission-control/sops/android-app-release.md.

## What's new

| Version | What changed |
|---|---|
| 0.1.0 | First version. One-screen remote for PitPat pads (`FBA0`; `FF01`/`FF02` and `FFF1`/`FFF2` layouts too, untried on a pad yet): find and remember the pad, auto-reconnect, start, pause, stop, speed up and down, presets, live time, distance, speed, calories and steps in mph or km, a history of every walk with a speed chart, totals, delete and CSV share, a diagnostics log, and an experimental beeper toggle. |

## Credits

- The Bluetooth protocol layer, safety rules and GATT transport are ported from [Franrodr10/pitpat-controller](https://github.com/Franrodr10/pitpat-controller) (MIT). Full notice in `THIRD_PARTY_NOTICES.md`.
- The `FF01`/`FF02` layout (4-byte header, write counter, heartbeat after each notification) follows [azmke/pitpat-treadmill-control](https://github.com/azmke/pitpat-treadmill-control) (MIT). Re-implemented in Kotlin, no code copied. Notice in `THIRD_PARTY_NOTICES.md`.
- Protocol facts were cross-checked against [Razzolate99/pitpat-treadmill](https://github.com/Razzolate99/pitpat-treadmill) and [sirfergy/HomeAssistantWalkingPad](https://github.com/sirfergy/HomeAssistantWalkingPad). No code was copied from them.
- Build, CI and release shape from Rick-Pro/kestrel-android.

## The developer

Built by Ricardo Uzcategui, a three-time founder based in San Francisco. Ricardo is building Ethoz, an AI workspace for founders. [ricardouzcategui.com](https://ricardouzcategui.com)

## License

All rights reserved for now. See `LICENSE` and `THIRD_PARTY_NOTICES.md`.
