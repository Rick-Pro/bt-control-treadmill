Before any work, read Ricardo's shared context: https://github.com/Rick-Pro/mission-control (start at AGENTS.md). Follow its rules, incl. rule 8: sign your work.
Read the repo map in README.md first; don't scan the repo. Then read `docs/design.md` (what the app is and the module contract) and `docs/plan.md` (the decision log and the list of what only Ricardo can do).

## How we work here (Ricardo's standing rule, 2026-10-24)
- **Never wait on Ricardo.** Pick the best default, write the decision and the reason in the Decision log in `docs/plan.md`, and keep going.
- If something truly needs Ricardo (an account, a secret, spending, a device test, a legal call), add it to "Needs Ricardo" in `docs/plan.md` with when it is needed, then finish every other part you can. Do not stop the build for it.
- Ask-first still applies (mission-control rule 7: deleting, spending, sending, posting). Those go on the list, not into chat.
- Sign every PR, commit and comment "Claude Code:" (or your agent name). Never push to `main`. Open a PR; merge after CI is green.

## The app
- Kotlin, Jetpack Compose and Material 3. AndroidX only. No other library, SDK or service without a written reason in the PR (mission-control rule 17). No Room, no serialization plugin, no KSP: history is one JSON line per walk (decision D4).
- **No internet, ever.** The app has no INTERNET permission. The only thing it talks to is the treadmill over Bluetooth LE. A test fails the build if the merged manifest asks for anything outside `config/allowed-permissions.txt`.
- Logic lives in `core/` (pure Kotlin/JVM, no Android imports) and is unit-tested with JUnit 4. Android calls (Bluetooth, files, preferences) live only in `app/.../ble/`, `app/.../session/` and `app/.../history/`. Compose only renders state that `core/` and the session layer produce.
- The Android SDK cannot be downloaded in agent sessions, so only CI compiles `app/`. Before every PR run `./gradlew -Pstride.coreOnly=true :core:test`. When Gradle has no network either, run `scripts/core-check-offline.sh` (compiles and tests `core/` with the Kotlin compiler bundled in a local Gradle install). Then watch the PR's CI result and fix it until green.
- Safety first. STOP always goes first in the write queue and is resent until the belt stops. Speed commands go only while the belt runs and the last status is fresh. Nothing is resent after a reconnect. Keep these rules in `core/` where they are tested (`SessionController`, `CommandScheduler`).
- The protocol is documented in `docs/protocol.md`. Change a frame only with a capture from the real treadmill noted in the PR.
- One screen. The home screen has everything a walk needs; History and Settings are reached by a tap and come back with Back. No bottom bar, no onboarding carousel, no account.
- Be honest in the app and the README. Say what the app does and what the treadmill does on its own (the belt keeps running if the link drops; the physical remote still works). The repo is public and the license is MIT (decision D23), so saying "open source" is fine. No claims of signed, popular or reviewed that the app has not earned.
- No em dashes in copy. No secrets or personal data in code, tests, docs, logs, issues or PRs: secret names only. The repo is public, so that includes the pad's own id: captures use the placeholder from decision D24. The treadmill's Bluetooth address stays in the app's private preferences and never in a log that gets shared.
- **Every release passes Google Play Protect and stays lightweight (Ricardo, 2026-10-25).** Before a release, install the release APK on the Pixel from the browser, let Play Protect scan it, and record "This app looks safe" in the release PR. Keep the release APK at 8 MB or less (CI checks it) and the permission and dependency allow-lists short: no new permission or library without a written reason.
- Every release PR keeps the README true to the build: What's new (one row per version) and the Repo map.
- Tasks live as GitHub issues (mission-control rule 28). Use sub-agents, one per task (rule 14).
