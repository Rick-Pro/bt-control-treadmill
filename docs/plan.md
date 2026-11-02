# Stride build plan

## 1. Scope
One Android app, one screen, for Ricardo's PitPat walking pad (HY-BA06B1, advertises as "BA06-73F6"):
start, pause, stop, speed up, speed down, live time, distance, speed, calories and steps, and a history of every
walk kept on the phone. First run: find the pad, tap it, done. Every later launch reconnects on its own.
No account, no internet, no ads, no telemetry.

Not in scope: training plans, 3D maps, leaderboards, voice, heart rate, incline (this pad has none), Health Connect.

## 2. Stack
Kotlin 2.4.21, AGP 9.4.0, Jetpack Compose with Material 3 (BOM 2026.09.00), AndroidX only, JUnit 4 for `core/`.
minSdk 33, targetSdk 37. Gradle 9.8.2. Same build shape as Rick-Pro/kestrel-android, which already passes
Google Play Protect: `:core` (pure Kotlin, tests) plus `:app` (Android), CI that enforces permission and dependency
allow-lists and an 8 MB APK budget, a rolling debug test build, and a manual Release workflow that signs with a
release key kept in GitHub secrets.

## 3. Module map
| Module | Package | What |
|---|---|---|
| `core/protocol` | `io.github.rickpro.stride.core.protocol` | PitPat frames: build commands, parse status, reassemble split notifications |
| `core/control` | `io.github.rickpro.stride.core.control` | Command queue (STOP first), session safety rules, step estimate |
| `core/units` | `io.github.rickpro.stride.core.units` | km/h and km to mph and miles, formatting of speed, distance, time, pace |
| `core/history` | `io.github.rickpro.stride.core.history` | Walk record, walk detection from the status stream, JSON line encode/decode, totals |
| `app/ble` | `io.github.rickpro.stride.ble` | Permissions, scanner, one GATT connection |
| `app/session` | `io.github.rickpro.stride.session` | `TreadmillManager` (owns the link for the whole app), settings, haptics, log |
| `app/history` | `io.github.rickpro.stride.history` | `HistoryStore`: `filesDir/workouts.jsonl` |
| `app/ui` | `io.github.rickpro.stride.ui` | Setup, Control (home), History, Settings; theme |

Full contract: `docs/design.md`.

## 4. Decision log
| # | Decision | Why |
|---|---|---|
| D1 | App label **Stride**, package `io.github.rickpro.stride`, repo stays `bt-control-treadmill` | Short, one word, says what it is for. Renaming later is one string and one package. |
| D2 | **mph and miles by default**, km one tap away | Ricardo is in the US and the PitPat app showed him mph. The protocol stays km/h underneath; conversion lives in `core/units` with tests. |
| D3 | **No INTERNET permission** | The app has nothing to fetch. Zero network is the simplest privacy claim and a smaller Play Protect surface. Update checks are the GitHub Releases page, opened by hand. |
| D4 | **History as JSON lines in a private file**, not Room | Room needs KSP and three more libraries. A few hundred walks are a tiny file. Encode/decode is hand-written in `core/history` and tested. |
| D5 | **Protocol and transport ported from Franrodr10/pitpat-controller (MIT)** | Clean Kotlin, already tested on a PitPat-T01 family pad with service 0xFBA0, and the safety rules (STOP first, resend, no auto-resend after reconnect) are the ones we want. MIT notice kept in THIRD_PARTY_NOTICES.md. |
| D6 | **Committed debug keystore** with the standard Android debug settings | Same as kestrel: every test build has the same signature so each one installs over the last. It signs test builds only; the release key never touches the repo. |
| D7 | **Beeper on/off is experimental** | The frame was captured on a BA09-B, not on this BA06. It ships behind a Settings toggle marked experimental until Ricardo confirms it on the pad. |
| D8 | **minSdk 33** | Ricardo's devices run Android 13 or newer. It drops the legacy Bluetooth and location permissions entirely. |
| D9 | Reconnect on every foreground; keep a live link in the background; let the link go when the app is closed and the belt is stopped | Opening the app should just work. A moving belt keeps its link so STOP keeps working from the notification shade or after a quick app switch. |
| D10 | Speed step is 0.1 km/h (the pad's own step), shown rounded in the chosen unit | Sending anything finer than the pad's step is noise. In mph the display moves in 0.1 mph steps about every other tap, which matches the PitPat app. |

## 5. Needs Ricardo
| When | What |
|---|---|
| Now (one tap) | Set `main` as the default branch: Settings, Branches, switch default to `main`. The repo was empty, so GitHub first made `claude/stride-0.1.0` the default. Claude Code could not do it: the agent proxy blocks repository settings writes. Then delete the old default branch if it is merged. |
| Before the first release | Run `scripts/make-release-key.sh` once on his own computer. It makes the release key and sets the 4 GitHub settings. Back up the key in the password manager. |
| First device test | Install the Latest test build on the Pixel 8 Pro, close the PitPat app, turn the pad on, run the checklist in section 8. |
| First device test | Confirm the max speed the pad reports (shown in Settings, Diagnostics). The app caps the speed slider at it. |
| First device test | Try the beeper toggle (D7). If it does nothing, leave it; if it works, drop the "experimental" word. |
| Each release | Play Protect check: install the release APK from the browser, tap Scan app, note "This app looks safe" in the release PR. |

## 6. Allow-lists and budget
Permissions: `config/allowed-permissions.txt` (BLUETOOTH_SCAN, BLUETOOTH_CONNECT, VIBRATE, plus the androidx.core
signature permission). Dependencies: `config/allowed-dependency-groups.txt` (this repo, AndroidX, Kotlin, two
annotation jars, one Guava interface jar). Release APK 8 MB or less. CI fails on anything else; adding a line needs
a written reason in the PR.

## 7. Safety rules (tested in core)
- STOP jumps the queue, clears everything pending, is resent up to 2 times if the belt keeps moving, then the app
  shows "Stop not confirmed, use the treadmill's remote" and vibrates.
- Speed changes go only while RUNNING and the last status is 3 s old or less.
- A speed chosen during the start countdown is applied once the belt runs.
- After a reconnect nothing pending is resent.
- Link lost while the belt moves: vibrate, orange banner, Reconnect button. The belt keeps running on its own; the
  app says so.

## 8. First device run checklist
1. PitPat app closed (force stop). Pad on, display shows 0:00.
2. Open Stride. Tap Find. The pad shows up as BA06-73F6 within 15 s. Tap it (or it is picked on its own if it is the only one).
3. Connected within 10 s. The status line goes green. Numbers show 0.
4. Tap Start. The pad counts down and the belt moves at 1.0 km/h (0.6 mph). Speed on screen matches the pad display.
5. Tap SPD + five times. Target rises in steps; belt follows within 2 s. SPD - works the same.
6. Tap a preset. Belt goes there.
7. Tap Pause. Belt stops, time holds. Tap Start. Belt resumes at the last speed.
8. Tap STOP. Belt stops within 3 s. A walk appears in History with the same time, distance and calories as the pad.
9. Walk again for 1 minute, then turn Bluetooth off on the phone. Phone vibrates, orange banner. Belt keeps running. Use the physical remote to stop. Turn Bluetooth on: app reconnects on its own.
10. Close the app fully. Open it. It reconnects without any tap.
11. Settings: switch to km, numbers convert. Switch back.
12. History: long-press a walk, Delete, confirm. It is gone after a restart too.
13. Diagnostics: note the "max" value the pad reports in the status line.
