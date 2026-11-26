# Every N Days

A small Android app for things you do every N days: supplements, skincare, plants, lenses.

- Add a reminder with a name, "repeat every N days", a time of day, and a color.
- At that time you get a notification with **Mark done**. It stays until you mark it done (or swipe it away).
- The next date is counted from the day you actually mark it done.
- Each reminder keeps a history of when you did it.

Everything is stored on the phone (`reminders.json` in the app's private storage). No account, no internet.

## Build

Needs JDK 17 and the Android SDK (API 35).

```
./gradlew assembleRelease
```

The APK lands in `app/build/outputs/apk/release/app-release.apk`. It's signed with the debug key, which is fine for installing it on your own phone.

Pushing to GitHub also builds the APK automatically (Actions → latest run → Artifacts).

## Install

Copy the APK to the phone and open it. Android will ask you to allow installing apps from that source (Files or Chrome). Allow it once, install, and you can turn the permission off again.
