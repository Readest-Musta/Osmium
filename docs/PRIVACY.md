# Privacy

Osmium is built so it *cannot* phone home.

- **No network access**: the manifest has no `INTERNET` permission. The
  Android OS enforces this — Osmium cannot open a socket even if it wanted to.
- **No analytics, no crash reporting, no ads, no SDKs** that could transmit data.
- **On-device only**: all user data (learned words, emoji recents, preferences)
  stays in the app's private storage.
- **No cloud backups**: `dataExtractionRules` excludes all user data from
  Android backup and device transfer.
- **Incognito mode**: disables all learning; also forced automatically in
  password fields and whenever the app requests
  `IME_FLAG_NO_PERSONALIZED_LEARNING`.
- **Open source**: every line can be audited; the release pipeline (CI) builds
  from source.

## What we'd like to know (but never collect)

Nothing. If you want to help improve Osmium, file an issue on GitHub —
we do not use telemetry.
