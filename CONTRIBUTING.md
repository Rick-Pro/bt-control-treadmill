# Contributing to Stride

Thanks for looking. Stride is a small app with a narrow job, so the rules are short.

- **Issues are welcome.** Bugs, a pad that does not connect, a pad that works. Use the Bug report form and paste the
  Diagnostics log (Settings, Diagnostics log, Share). Tell us the pad model and what Bluetooth name it shows.
- **Pull requests are reviewed by the maintainer** (Ricardo). Open an issue first for anything bigger than a small fix, so
  nobody builds something that will not fit. One change per PR.
- **Run the core tests before you push:** `./gradlew -Pstride.coreOnly=true :core:test`. They need no Android SDK. CI
  builds and lints the app, checks the permission and dependency allow-lists and the APK size.
- **No new permissions and no new libraries.** The app has no internet permission and CI fails if the manifest asks for
  anything outside `config/allowed-permissions.txt` or ships a library group outside `config/allowed-dependency-groups.txt`.
  If you think one is needed, explain why in the issue first.
- **Protocol changes need a capture from a real pad.** Frames in `docs/protocol.md` are what real pads sent. A new frame or a
  changed byte needs the raw hex from a real pad (say which model and firmware), and a test that decodes it. Leave the
  pad's own id out of anything you commit; Stride masks it in the Diagnostics log.
- **Keep the safety rules in `core/`.** STOP goes first, speed commands go only while the belt runs and the status is
  fresh, and nothing is resent after a reconnect. They are tested; do not move them into the UI.
- **Plain words, no em dashes** in app text and docs. No secrets, tokens or personal data in code, tests, docs or logs.

Where things live is in the repo map in `README.md`. Stride is MIT licensed (`LICENSE`); by sending a pull request you agree
your change is under the same license.
