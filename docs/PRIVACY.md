# Privacy

Osmium is designed so that it has no way to phone home. The statements below
describe the manifest, dependencies and code paths of the shipped app; they are
not a substitute for an independent security audit.

## Verified mechanisms

- **No network permission.** The manifest declares only `VIBRATE` (for key-press
  feedback). There is no `INTERNET` or `ACCESS_NETWORK_STATE` permission, so the
  Android OS blocks any attempt to open a socket.
- **No analytics, crash reporting, ads or tracking SDKs.** The only third-party
  dependency is JNA, a native-call library used by the bundled riti engine;
  neither it nor AndroidX Core transmits data.
- **On-device storage only.** Learned words, emoji/symbol recents, clipboard
  history, preferences and imported layouts live in the app's own private
  storage and are readable only by Osmium.
- **Excluded from backup and device transfer.** The manifest sets
  `allowBackup="false"` and `fullBackupContent="false"`, and
  `dataExtractionRules` excludes root, file, database and shared-preference
  data from both cloud backup and device-to-device transfer.
- **Incognito mode.** Disables learning and redirects the engine's writable
  data to a throwaway cache directory that is deleted on shutdown. It is also
  forced automatically in password fields and whenever the app requests
  `IME_FLAG_NO_PERSONALIZED_LEARNING`. While incognito, clipboard history is
  not recorded; password fields additionally hide the suggestion strip.
- **User-controlled data.** Preferences (including learned words and clipboard
  history) can be exported to and imported from a local JSON file and cleared
  from the Settings screen. No account, server or sync service is involved.
- **Auditable source.** The full source is published under MPL-2.0. Build
  instructions are in the README so the APK can be rebuilt from source.

## What we do not claim

We do not claim an independent privacy audit, a certified reproducible build,
or an automated publishing pipeline. Those require verification that has not
been published here.

## What we'd like to know (but never collect)

Nothing. If you want to help improve Osmium, file an issue on GitHub —
we do not use telemetry.
