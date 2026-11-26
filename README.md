# Every N Days

A small, private reminder app for Android, built for things that repeat (supplements, skincare,
plants, lenses) and for one-off events.

## What it does

- **Repeat options:** once; every N days; N days on / M days off; weekly on chosen days (every
  1–8 weeks); monthly on a day or the last day (every 1–12 months); yearly.
- "Every N days" and "days on / off" count from the day you actually mark it done (or, if you
  choose, keep a fixed rhythm). Weekly, monthly and yearly stay on the calendar.
- **Ends:** never, on a date, or after a number of times.
- **Notifications** arrive on time and stay until you mark the reminder done. Optional heads-up
  before (15 minutes to 1 week).
- **Skip** one occurrence, **pause** and resume, full **history**.
- **Backup / restore** to a file from the ⋮ menu on the main screen.

Everything is stored on the phone. No account, no internet.

## Build

Pushing to `main` builds the APK on GitHub (after running the unit tests) and publishes it as
the **latest** release: https://github.com/maxr777/reminder-app/releases/latest

Locally: JDK 17 + Android SDK (API 35), then `./gradlew assembleRelease`.

### Signing

Updates only install over an existing copy if every build is signed with the same key. The
workflow uses two repository secrets for this:

- `KEYSTORE_BASE64`: the keystore file, base64-encoded
- `KEYSTORE_PASSWORD`: its password (key alias `everyndays`)

Without them the build is signed with a temporary debug key.

## Install

Open the APK on the phone. Android will ask you to allow installing apps from that source
(Chrome or Files). Allow it, install, and you can turn the permission off again.
