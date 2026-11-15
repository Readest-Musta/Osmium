# Osmium 🔘 — ওসমিয়াম

A **powerful, lightweight, open-source Bengali keyboard for Android** with **zero tracking**.
Powered by [riti](https://github.com/OpenBangla/riti) — the Rust input engine behind
[OpenBangla Keyboard](https://github.com/OpenBangla/OpenBangla-Keyboard).

```
ami banglay gan gai   →   আমি বাংলায় গান গাই
```

## Highlights

- **Avro Phonetic** (via riti — 100% scheme-compatible with Avro Keyboard)
- **Probhat** fixed layout + **English** toggle
- Emoji panel with recents
- On-device learning for suggestions (never leaves the phone)
- **Incognito mode** — learning fully disabled, on your terms
- **No INTERNET permission. No analytics. No trackers.** Verifiable in the manifest.

## Architecture

```
Kotlin IME (InputMethodService)
  ├─ KeyboardView, SuggestionBar, EmojiPanel
  ├─ ML module (on-device, later milestones)
  └─ uniffi bindings  →  osmium-native (Rust)  →  riti
```

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) and [docs/PRIVACY.md](docs/PRIVACY.md).

## Building

Requirements: JDK 17, Android SDK (API 35), Rust + cargo-ndk + Android NDK r27.

```bash
./scripts/build-native.sh   # builds riti + uniffi Kotlin bindings
./gradlew assembleDebug     # builds the APK
```

On low-power machines, open the repo in **GitHub Codespaces** and run
`./scripts/setup-codespace.sh` first — it installs the whole toolchain.

## License

MPL-2.0 (same as riti). The riti engine and OpenBangla data are © OpenBangla contributors,
MPL-2.0. Probhat layout is imported from Avro Keyboard via OpenBangla.
