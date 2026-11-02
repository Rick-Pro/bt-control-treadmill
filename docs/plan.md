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
| D11 | **`app/debug.keystore` is now the one from Rick-Pro/kestrel-android** (Ricardo, 2026-11-02). Certificate SHA-256 `FF:4A:50:CD:D7:47:A7:D6:D2:FA:47:2E:28:C5:68:C5:7A:A3:DE:AE:91:89:7D:ED:07:6F:C3:E5:96:0F:FE:EA` | One shared test certificate across Ricardo's apps; D6 said "same as kestrel" and now it is literally the same file. It is still the public Android debug key (alias `androiddebugkey`, password `android`), signs test builds only, and is not a secret. The release key never touches the repo. Cost: a Stride Dev build signed with the old test key cannot be updated in place, so it is uninstalled once. |
| D12 | **Read the whole GATT table, log it, and pick the transport the pad offers**: `FBA0` first, then `FF01`/`FF02` ("PitPat wrapped", 4-byte header), then `FFF1`/`FFF2` ("PitPat FFF") | A pad advertising as "PitPat-T01" connected but has no `FBA0`, so Stride said it does not speak PitPat. The frames are the same in every layout; only the envelope differs. The table is always logged and is part of the error, so the next pad that does not fit can be read from a shared log. Variant 2 is written from azmke (MIT) and is now confirmed on Ricardo's pad: it offers `FF01`/`FF02` in service `FFFF`, and its idle status frames decode (captures in `docs/protocol.md`; the first build dropped them because the pad's header says `04` where Stride expected `00`). Build 3 then confirmed connect, Start, speed changes and Stop over it, and the connection menu. Variant 3 is untried. |
| D13 | **Speed scale: start with thousandths, switch to hundredths on evidence, only on variants 2 and 3** | azmke uses thousandths of km/h, qdomyos-zwift is reported to use hundredths. Switch when the pad's max speed reads below 1.0 km/h, or the echoed target is a tenth of what was sent. A thousandths pad cannot trigger either. Not covered: a pad that echoes our thousandths back as if they were hundredths. Keep the 1.0 to 6.0 km/h clamp. See `docs/protocol.md`. |
| D14 | **Connection menu on the Control screen.** Tap the status line: Reconnect, Disconnect, Find a treadmill, Forget treadmill | The pad takes one connection and stays silent to everyone else while it holds one, so Stride needs a way to let go. **Disconnect** closes the GATT link and stops all retrying and auto-connect until Reconnect (kept in memory only: a fresh launch connects again, as before). **Find** closes the link, waits for Android to confirm it is down (2 s at most), then opens Setup and scans at once; Cancel there takes the remembered pad back. **Forget** always asks. Disconnect and Find ask only while the belt moves, because it keeps running at the same speed with nobody in control. Every session end now waits for the link-down confirmation before releasing the handle. While reconnecting, the status line shows the attempt number and the last problem in plain words. |
| D15 | **Facts from the build 3 log are the reference for the pad's behaviour** (Ricardo, 2026-11-02): countdown about 3 s, `STOPPED` reported at once while the belt still ramps down, distance in metres, duration in milliseconds, steps and kcal always 0 | These came from a real walk on the HY-BA06B1, so the countdown screen, the "Stopping" state and the Stride-side kcal and step estimates are built on them, not on guesses. The captures are in `docs/protocol.md` and `PadCapturesTest`. The unknown u16 ("load") is logged and never shown; its offset (41 or 43) is open. |
| D16 | **Countdown screen, and SPD +/- only while the belt runs** (Ricardo, 2026-11-02) | The pad counts down for about 3 s after Start (D15). The Control screen shows a big Starting with a pulsing ring and a 3-2-1 that stays on 1 if the pad is slow, then switches by itself on RUNNING. How long STARTING really lasted is logged ("Countdown: STARTING lasted 3.1 s, then running"). PAUSED is a badge; a STOPPED status with the belt still above 0.1 km/h reads "Stopping". SPD +/- are disabled unless the pad says RUNNING, as asked; the preset chips still set the speed for the next start, since SPD +/- can no longer do that while stopped. STOP is never gated. |
| D17 | **Local profile ("Me"): calories, step length and the pad's weight byte** (Ricardo, 2026-11-02) | The HY-BA06B1 reports 0 kcal and 0 steps (D15), so both are Stride's. With a profile: calories = MET x kg x hours, with MET from speed by the ACSM walking equation at level grade (MET = 1 + m/min / 35: 2.5 at 3.2 km/h, 3.3 at 4.8, 4.0 at 6.4), summed on the pad's own duration counter so pauses cost nothing and a late join is caught up from the average speed. Step length = 0.413 x height (women), 0.415 x (men), 0.414 (other), used only while the pad reports 0 steps. The weight (1 to 255 kg) goes into byte 10 of every control frame by `PitPatLink`, so the pad's own count improves, and is masked as `WW` in the Diagnostics log. Walks keep both numbers: the pad's `kcal` and Stride's `kcalEstimate`; totals and lists show the estimate when there is one. Stored in SharedPreferences only: no network, backups are off, and the log never carries the values. Chose the ACSM equation over a speed-to-MET table because it is continuous, documented and needs no table I could mis-copy; the table (Compendium of Physical Activities) would read about 10 to 20% higher at 3 to 4 km/h. |
| D18 | **A Treadmill info card in Settings, built on what this pad really sends** (Ricardo, 2026-11-02) | The status frame carries more than the Control screen uses. The card shows the protocol in use, firmware, the pad's id, max speed, and the pad's step count next to Stride's estimate; heart rate and incline only when a pad sends a value above 0 (this one sends 0 for both). `docs/protocol.md` has a table of which fields the BA06-B1 fills in. The unknown u16 ("load") is logged and never shown; its offset is 41 or 43, so the parser takes whichever is non-zero. The pad's id is shown on the card only; in the Diagnostics log its bytes are masked as `**`, so a shared log does not carry it (the one status frame that has it then cannot be checked against its XOR). The pad's Wi-Fi is left out: it only talks to the PitPat cloud and there is no local interface, which the README now says. |
| D19 | **An About screen, reached from Settings** (Ricardo, 2026-11-02) | Says in plain words what Stride is for: it replaces the PitPat app for the HY-BA06B1 (sold as BA06-B1, shows as PitPat-T01 over Bluetooth) and other PitPat pads on the same protocol may work. Developer link, GitHub link and the notices link open in the browser with `ACTION_VIEW`, so the app keeps no INTERNET permission. Credits name pitpat-controller and pitpat-treadmill-control (both MIT, with their authors), the protocol notes that gave facts only, the Kotlin and AndroidX libraries, and the ACSM walking equation. It is a screen with Back, not a dialog, so the credits can scroll. The exact wording is the one Ricardo gave. |
| D20 | **The splash is Android 12's own, set in the theme only** (Ricardo, 2026-11-02) | `windowSplashScreenBackground` is the app background (`stride_bg`) and `windowSplashScreenAnimatedIcon` is the existing launcher foreground drawable. minSdk is 33, so the attributes need no `core-splashscreen` library, no `SplashScreen` call, no extra activity and no timer: the system shows it until the first frame is drawn, so it adds no wait. The foreground drawable was first used as it is; D25 replaced it with an inset copy, because the artwork reaches the edge of the splash's circle. No new dependency, permission or APK weight. |
| D21 | **Release 1.0.0 ships an APK and an App Bundle, signed with the same release key; Google Play comes after a closed test** (Ricardo, 2026-11-02) | `VERSION` is 1.0.0 (version code 1,000,000). The Release workflow builds `:app:bundleRelease` next to the APK and signs it with `jarsigner` (`scripts/release-sign-aab.sh`), reading only the four existing settings, and publishes `stride-vX.Y.Z.aab` and `.aab.sha256` beside the `.apk` and `.apk.sha256`. CI runs the bundle script on every PR against the **public debug key** (no release key involved) so the first real release is not the first time it runs. With **Play App Signing** the release key is only the upload key and Google signs what users get, so a phone with the GitHub APK cannot update to the Play build in place (different certificates): uninstall first, or pick one channel per phone. Test builds are named `stride-test-<version>.apk`. **Claude Code created no key and holds none**; the steps for Ricardo are in section 5. Play's personal-account rule (a closed test with at least 12 testers for 14 days before production) is in `docs/play-store.md`. |
| D22 | **HOLD: the package name stays `io.github.rickpro.stride` until Ricardo confirms the final name in the Play Console** (Ricardo, 2026-11-02) | Play reported `io.github.rickpro.stride` as already in use, and then rejected `com.ricardouzcategui.stride` too, which points at an account or draft problem, not a real collision. Changing `applicationId` is cheap now and permanent once an app is on Play, so nothing is renamed on a guess. The scripts that check the package (`release-sign.sh`, `release-sign-aab.sh`) and the docs name the current id and are updated together with the one-line change once the name is final. |
| D23 | **Stride is open source under the MIT license** (Ricardo, 2026-11-02) | The repo goes public. `LICENSE` is the plain MIT text, copyright 2026 Ricardo Uzcategui (kept verbatim so GitHub recognises it). `THIRD_PARTY_NOTICES.md` stays: the ported protocol code keeps its MIT notices. The old all-rights-reserved notice, and the rule that required it, are gone from `LICENSE`, the README, `AGENTS.md` and the landing page, and saying "open source" is now true and allowed; "signed", "popular" and "reviewed" still need to be earned. Community files: `CONTRIBUTING.md` (issues welcome, PRs reviewed by the maintainer, run the core tests, no new permissions or libraries, protocol changes need a capture from a real pad), a Bug report form that asks for the pad model and the Diagnostics log, and `.github/FUNDING.yml` (the Sponsor button points at ricardouzcategui.com until Ricardo picks Buy Me a Coffee or GitHub Sponsors). The README gets a CI badge (GitHub's own) and a license badge (a static shields.io image, in the README only; the app uses no such service). "Compatible pads" lists PitPat, Superun and DeerRun, says plainly that only the HY-BA06B1 has been tested, and gives Ricardo's rule of thumb (if the manual says to download the PitPat app, Stride should work). |
| D24 | **Public-repo hygiene: the pad's own id is a placeholder in the committed captures** (Claude Code, 2026-11-02) | The 52-byte capture in `docs/protocol.md`, `PadCapturesTest` and `LogMaskTest` carried the real 17-character id of Ricardo's pad. It is now `EXAMPLESERIAL0001` with the XOR byte recomputed (`44`), so every test still decodes the same frame shape. The rest of the frame is the real capture. Anything already merged still holds the old id in git history (the squash commits for #3 and #7, and older PR branches). Removing it would mean rewriting history and force-pushing `main`, which Claude Code does not do on its own; it identifies one pad, not a person, so the default is to leave it. Ricardo's call (Needs Ricardo). A scan for addresses, e-mail addresses, tokens and keys found nothing else; `app/debug.keystore` is the public Android debug key (D6, D11). |
| D25 | **The splash icon is the launcher foreground inset to 80%, and the splash is a cold-start thing** (Claude Code, 2026-11-02) | Ricardo saw no splash on test 9. From the files: the theme is on `<application>` and `MainActivity` sets none of its own, there is no `setTheme`, no `windowDisablePreview` and no splash library, and the theme does set `windowSplashScreenAnimatedIcon`, so the setup was right. One fault was real: the system masks the icon to a circle two thirds of its width, and the foreground's belt corners and top stride mark sit exactly on that circle's edge (36 of 108 units from the centre), so any rounding clips them and the icon looks cut off. `drawable/ic_splash_icon.xml` insets the foreground by 10% on each side, which leaves 20% clearance; the launcher icon is untouched. What is left is how Android works: the splash exists only on a **cold start** (the process is not running, for example after swiping Stride out of Recents), is removed as soon as the first frame is drawn, and on a Pixel 8 Pro that is a fraction of a second. A warm start brings the old window back with no splash. We still add no delay. If it proves too brief, the options are a hold of a few hundred ms with the platform `SplashScreen.setOnExitAnimationListener` (no library) or `androidx.core:core-splashscreen`'s keep-on-screen condition (a new dependency, so it would need a written reason). Ricardo decides. |
| D26 | **`VERSION` stays 1.0.0 until the v1.0.0 release is cut; the next cycle bumps to 1.1.0** (Ricardo, 2026-11-02) | Everything merged since the 1.0.0 work (including the license change, the community files and the splash fix) belongs to 1.0.0, so it goes into the 1.0.0 What's new row and `VERSION` is not touched. The first PR after the release sets `VERSION` to 1.1.0 and starts a new row. |

## 5. Needs Ricardo
| When | What |
|---|---|
| Done | `main` is the default branch. Optional tidy: delete the merged branch `claude/stride-0.1.0` (Branches page). |
| Now, once the repo is public | **Turn on GitHub Pages** so the landing page and the privacy policy have addresses. Claude Code tried the API and the agent proxy blocks repository settings writes (and the repo was still private when it tried). In the repo: Settings, Pages, Build and deployment, Source **Deploy from a branch**, Branch `main`, folder **/docs**, Save. After a minute the page says the site is live. Then open these in a private window: https://rick-pro.github.io/bt-control-treadmill/ and https://rick-pro.github.io/bt-control-treadmill/privacy.html (the About screen and the Play listing both link to the second). Or, on your computer: `gh api -X POST repos/Rick-Pro/bt-control-treadmill/pages -f "source[branch]=main" -f "source[path]=/docs"`. Pages on a free account needs a public repo. |
| Now, once the repo is public | **Repo description and topics** (blocked for Claude Code the same way). Either use the gear next to About on the repo page, or on your computer: `gh repo edit Rick-Pro/bt-control-treadmill --description "One-screen Android remote for PitPat walking pads. No account, no internet, no ads." --homepage "https://rick-pro.github.io/bt-control-treadmill/" --add-topic android --add-topic kotlin --add-topic jetpack-compose --add-topic bluetooth-le --add-topic treadmill --add-topic walking-pad --add-topic pitpat`. |
| Now (optional, free on public repos) | Settings, Code security: turn on **Secret scanning**, **Push protection** and **Private vulnerability reporting**. |
| Later | **Funding.** `.github/FUNDING.yml` points the Sponsor button at ricardouzcategui.com for now. When you pick Buy Me a Coffee or GitHub Sponsors, change it to `buy_me_a_coffee: <username>` or `github: [Rick-Pro]` (the file has the lines ready) and update the Support section of the README. |
| Your call | **The pad's own id is still in git history** (D24): the working files now use a placeholder. Leave it (the default, it names one pad, not a person), or rewrite history before anyone clones. Claude Code will not rewrite or force-push `main` on its own. |
| Before the first release | Make the release key, once, on your own computer. Exact steps are right below this table. Claude Code does not create, hold or see this key. |
| Before the 1.0.0 release build | **Final package name pending Play Console.** Stride's `applicationId` is `io.github.rickpro.stride` today and is deliberately unchanged. Play said that name is already in use and then also rejected `com.ricardouzcategui.stride`, which looks like an account or draft problem rather than a real collision. A package name cannot be changed once an app is on Play, so confirm the name in the Play Console (create the app, or ask Play support about the rejection) **before** the Release workflow builds 1.0.0, then tell Claude Code the final name. The switch is one line, `applicationId` in `app/build.gradle.kts` (the Kotlin namespace stays, so no source moves), plus the package check in `scripts/release-sign.sh` and `scripts/release-sign-aab.sh` and the docs. Debug builds then become `<name>.debug`: uninstall the old "Stride Dev" once (it is a different package) and note that walks and settings do not carry over. Do not upload an `.aab` to Play before the name is final. |
| Before the first release | Run the device checklist in section 8 on the latest test build (the countdown screen, Me, the Treadmill card and About are new since build 3), then bump nothing: `VERSION` is already 1.0.0. |
| Before the first release | Try the beeper toggle (D7). If it does nothing, leave it; if it works, drop the "experimental" word. |
| Each release | Play Protect check: install the release APK from the browser, tap Scan app, note "This app looks safe" in the release PR. |
| Before Google Play | Everything in `docs/play-store.md`: a verified developer account, the privacy policy at a public web address (`docs/privacy.html`, which is https://rick-pro.github.io/bt-control-treadmill/privacy.html once Pages is on, see above), screenshots from your own Pixel, and a closed test with at least 12 testers for 14 days. |

### The release key, step by step (Ricardo, once, about 10 minutes)
You need: a computer you trust (Linux, or Windows with Git for Windows and its Git Bash), a JDK 17 or newer for `keytool`
(Linux: `sudo apt install openjdk-21-jdk-headless`; Windows: install Temurin 21 and let it add itself to PATH; check with
`keytool -help`), the GitHub CLI `gh` signed in as the repo owner (`gh auth login`), and your password manager open.

1. Open a terminal (Git Bash on Windows) in a clone of this repo and run:
   ```
   scripts/make-release-key.sh
   ```
   The key goes to `~/stride-release-key` by default. It must be a folder outside every git repo; the script refuses
   otherwise. To pick another: `scripts/make-release-key.sh /some/other/folder`.
2. It asks, in this order:
   - `New key password, 12 or more characters (hidden):` Make a long random password in the password manager and paste it. Nothing shows as you type.
   - `Same password again (hidden):` Paste it again.
3. It creates `stride-release.p12` (PKCS12, RSA 4096, valid 10,000 days, alias `stride`) and prints `Created ...`.
4. It prints `Certificate SHA-256 (public, fine to share):` followed by 64 hex characters. That is public. The release notes show it too.
5. If `gh` is installed and signed in, it asks `Set secrets STRIDE_KEYSTORE_BASE64 and STRIDE_KEYSTORE_PASSWORD and variables
   STRIDE_KEY_ALIAS and STRIDE_CERT_SHA256 on Rick-Pro/bt-control-treadmill now? [y/N]`. Type `y`. It sets the four values
   itself, with the values going to `gh` on stdin (so they never land on a command line or in shell history), and prints
   `Done: 2 secrets and 2 variables set on Rick-Pro/bt-control-treadmill. The Release workflow can sign now.`
6. It ends with a back-up reminder. Do it now: save `stride-release.p12` and its password in the password manager (as an
   attachment) and keep one offline copy. GitHub secrets cannot be read back, so they are not a backup. Then you can delete
   the folder from the computer.
7. Check: GitHub, Settings, Secrets and variables, Actions: two secrets and two variables with those names.

If `gh` is missing or not signed in, the script says so and sets nothing. Install it, run `gh auth login`, and run the script
again: it finds the existing key, asks for the same password and sets the four values. **Never make a second key.** The
script refuses and offers to reuse the first, because a second key would stop updates from installing over the first one
(and, on Google Play, would not match the upload key you registered).

Then release: GitHub, Actions, Release, Run workflow, keep `main`, enter `v1.0.0`. The workflow stops before building and names
what is missing if any of the four values is absent. It publishes four files: `stride-v1.0.0.apk` and `.apk.sha256` (what
people install) and `stride-v1.0.0.aab` and `.aab.sha256` (what you upload to the Play Console).

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
14. Settings: the Treadmill section shows "Protocol: PitPat" (or "PitPat wrapped", or "PitPat FFF"). Diagnostics has the GATT table at the top of each connection and a "Transport:" line. Share the log once, whatever the result.
15. Only for "PitPat wrapped" or "PitPat FFF": look for a `speed scale:` line. "thousandths" means the pad echoed the target as sent. "hundredths" means Stride switched; check that the speed on screen matches the pad.
16. Tap the status line at the top. Disconnect: the status says "Disconnected by you", nothing retries, and the PitPat app can now connect (the pad takes one connection). Reconnect brings Stride back. Find a treadmill: Setup opens and scans on its own, and picking the pad (or Cancel) returns to the Control screen. Forget treadmill asks first. With the belt moving, Disconnect and Find ask before letting go.
17. While Stride cannot connect, the status line says "Reconnecting, attempt N" and "Last problem: ..." in plain words. Note what it says if you ever see it.
18. Press Start and watch the countdown: a big Starting with a pulsing ring and 3-2-1, about 3 seconds, then Walking. SPD + and - are greyed until the belt runs. Pause shows a clear PAUSED badge; STOP shows "Stopping" while the belt slows.
19. Settings, Me: fill in sex, height, weight (try both units), walk a minute, and the calories tile reads "Calories (est.)". The Diagnostics log shows `WW`, never your weight. Remove clears it.
20. Settings, Treadmill: firmware 27, the pad's id, max speed 6.0 km/h, steps "Treadmill 0, Stride estimate N". Settings, About Stride: the text, the developer link and the GitHub link open the browser.
21. About Stride, at the bottom: the License line (MIT, 2026 Ricardo Uzcategui) and the Read the license and Privacy policy links open the browser. The privacy link needs GitHub Pages to be on (see Needs Ricardo).
22. Splash: swipe Stride out of Recents, then tap its icon. You should see the Stride icon, whole and not cut off, on the dark background for a moment before the Setup or Control screen. Opening it again while it is still in memory shows no splash; that is normal (D25). Tell Claude Code if it is still too brief to see.
