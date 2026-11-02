# Putting Stride on Google Play

One page. Do these in order. Claude Code prepared the files; every step in the Play Console is yours. Wording in the
console changes, so go by what it asks.

## 1. Before you start
- **Final package name: pending Play Console.** The app's package name (`applicationId`) is `io.github.rickpro.stride` today.
  Play reported it as already in use and then rejected `com.ricardouzcategui.stride` as well, which looks like an account or
  draft problem. **It cannot be changed after the app is on Play**, so settle it in the console first and tell Claude Code
  the final name; the change is one line plus the checks in `scripts/` (see `docs/plan.md`, Needs Ricardo). Do not upload
  an `.aab` until then.
- **Developer account:** a Google Play developer account (one-time fee) with identity **verified**. A personal account is
  enough. Verification can take days, so start it first.
- **Release key:** done once, steps in `docs/plan.md` section 5 (`scripts/make-release-key.sh`). Then run the Release
  workflow for `v1.0.0`. It publishes `stride-v1.0.0.aab` (this is the file for Play) and `stride-v1.0.0.apk` (for people
  who install by hand), each with a `.sha256`.
- **Privacy policy at a public web address.** `docs/privacy.md` says Stride collects nothing, and `docs/privacy.html` is the
  same text as a page. GitHub Pages serves `/docs` on `main`, so the address is
  **https://rick-pro.github.io/stride-android/privacy.html**. Open it in a private window before you submit: Play needs a
  URL anyone can open (not a file download, and not behind a login). The About screen links to the same address.
- **Screenshots from your own Pixel** (at least 2 phone screenshots), an app icon 512x512 (export `ic_launcher_foreground`
  on the `#0F1419` background) and a feature graphic 1024x500.

## 2. Create the app
Play Console, Create app: name **Stride**, English (United States), **App** (not game), **Free** (a free app cannot be made
paid later), accept the declarations. The package name is chosen by the first bundle you upload, so the name from step 1 must
be final before that upload.

## 3. Play App Signing (upload the .aab)
On the first upload, use **Play App Signing**: Google holds the key that signs what users get, and **our release key becomes
the upload key** (Play checks every upload against it). If the upload key is ever lost, Play support can reset it; with the
sideload key alone a loss would be final. **One consequence:** the app from Play and the APK from GitHub are signed with
different keys, so one cannot update over the other. Pick one channel per phone, or uninstall first.
The version code goes up with every upload by itself: `major x 1,000,000 + minor x 1,000 + patch` (1.0.0 is 1000000).

## 4. Store listing (drafts, within the limits)
- **Title (30 characters at most):** `Stride: Walking Pad Remote`. Do not put "PitPat" in the title, since it is someone else's brand.
- **Short description (80 at most):** `Start, stop and set the speed of your walking pad. Keeps a history of walks.`
- **Full description (4,000 at most):**
  > Stride is a simple remote for the PitPat walking pad model HY-BA06B1 (sold as BA06-B1, shows as PitPat-T01 over Bluetooth).
  > Other PitPat pads that use the same protocol may work.
  > Start, pause and stop the belt, change the speed in mph or km/h, see time, distance, calories and steps, and keep a
  > history of every walk with a speed chart. Add your height and weight (optional, kept on your phone) for better
  > calorie and step estimates.
  > No account, no internet, no ads, no tracking. Everything stays on your phone.
  > Stride is an independent app and is not affiliated with or endorsed by PitPat.
- **Category:** Health & Fitness. **Contact:** your email and https://ricardouzcategui.com. **Privacy policy:** https://rick-pro.github.io/stride-android/privacy.html (from step 1).

## 5. App content forms
- **Data safety:** *Does the app collect or share any of the required user data types?* **No.** Nothing is sent off the
  device (the profile, walks and settings stay in private storage). If the form asks about encryption in transit or data
  deletion, say there is no data collected, so neither applies.
- **App access:** everything works without a login. **Ads:** none. **News, government, financial features:** none.
- **Target audience:** 18 and over (not a children's app). **Health:** if the console shows a health apps declaration, say
  Stride is a fitness utility, not a medical app, and does not use Health Connect.
- **Content rating:** complete the questionnaire (no violence, no user content, no data sharing). Expect "Everyone".
- **Permissions:** Nearby devices (Bluetooth scan with `neverForLocation`, and connect) and Vibrate. Reason: find and control the treadmill.
- **Target API:** Stride targets API 37, which is fine.
- **Donation link:** the About screen has a Support Stride row that opens Buy Me a Coffee in the browser. Google Play's payments
  policy limits apps that ask for money outside Google Play Billing, and a tip link may be read that way. Read the current policy
  text in the Play Console help before the closed test. If it is a problem, tell Claude Code and the Play build ships without the
  row (the GitHub build can keep it).

## 6. The closed test (required for personal accounts)
A personal developer account created after November 2023 must run a **closed test with at least 12 testers opted in for 14
days in a row** before it can apply for production. As of this writing; check the console for the current numbers.
1. Testing, Closed testing, create a track, and upload `stride-v1.0.0.aab`.
2. Add testers (an email list or a Google group) of 12 or more real Google accounts. They accept the invite link and keep Stride installed.
3. After 14 days, in the console apply for production access and answer its short questionnaire.
4. Fix what testers report with a new version (new `VERSION`, Release workflow, upload the new `.aab`). The 14 days run on the track, not on a version.

## 7. Production
After Google grants access: Production, create a release, upload the `.aab`, write release notes from the README What's new
row, review, and roll out. Keep the README in step with the store listing. Stride is open source under the MIT license
(`LICENSE`), so the listing may say so and link the repo; keep out "free of charge forever", "popular" and "reviewed" claims
that the app does not earn.
