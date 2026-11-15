# Architecture

```
┌──────────────────────────────────────────────────────────────┐
│ Osmium IME (Kotlin, InputMethodService)                      │
│   KeyboardView • SuggestionBar • EmojiPanel • incognito UI   │
├──────────────────────────────────────────────────────────────┤
│ osmium-native (Rust, UniFFI)                                 │
│   thin, Kotlin-friendly wrapper around riti's Rust API       │
├──────────────────────────────────────────────────────────────┤
│ riti (Rust, MPL-2.0)                                         │
│   Avro Phonetic • fixed layouts • dictionary suggestions     │
│   autocorrect/suffix data embedded at compile time           │
└──────────────────────────────────────────────────────────────┘
```

## Input flow (Bangla modes)

1. A key tap gives us the physical character ("q", "w", ...).
2. `osmium-native::keycode_for_char()` maps it to riti's keycode
   (shifted characters map to riti's shifted keycodes).
3. `riti.suggest_for_key()` returns the full suggestion state:
   auxiliary text (already-composed words), candidate list, selection index.
4. The IME renders `auxiliary + candidates[selection]` via
   `InputConnection.setComposingText()` and shows candidates in the bar.
5. Space / suggestion tap / enter commits the candidate
   (`riti.candidate_committed()` ends the session).
6. Backspace inside a session is routed to `riti.backspace()` — the engine
   owns its composition.

English mode bypasses riti and commits ASCII directly.

## Layouts

- **Avro Phonetic**: built into riti (`avro_phonetic` layout name).
- **Probhat**: fixed layout loaded from the bundled
  `assets/riti/Probhat.json` (same file OpenBangla Keyboard ships).
- Key *labels* for Probhat come from `assets/layouts/probhat-labels.json`,
  generated from that layout file (vowel kars are unshifted, independent
  vowels shifted — that is how Probhat works).

## Privacy by construction

- The manifest declares **no INTERNET permission** — the OS enforces it.
- riti's autocorrect/suffix databases are compiled into the library.
- User learning (M4) writes only to app-private storage and is disabled by
  incognito mode, password fields, and `IME_FLAG_NO_PERSONALIZED_LEARNING`.
- `dataExtractionRules` block cloud backup of user data.

## Incognito mode

When incognito is active, the engine's writable user directory is swapped to
a scratch directory in app cache; the scratch directory is deleted when
incognito ends. Dictionary suggestions (static data) still work.

## Milestones

- M0–M3 (done in v0.1): typing core, layouts, emoji, setup screen.
- M4: on-device suggestion learning (user lexicon + trigram, later a tiny
  quantized neural LM via LiteRT).
- M5: incognito UX polish.
- M6: glide typing (key-path segmentation first, neural decoder later).

## Building

`./scripts/build-native.sh` → `./gradlew assembleDebug`. On weak machines use
GitHub Codespaces: `./scripts/setup-codespace.sh` installs the toolchain.
