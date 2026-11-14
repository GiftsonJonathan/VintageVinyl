# Vintage Vinyl

An Android music player that plays your own digital music through a real-time vintage "record player" signal chain: warm tone, procedural surface noise, crackle and pops, wow and flutter, vintage EQ voicings, saturation, hum, speaker coloration and more. Every effect has its own control and works live while the music plays.

> **Read this first: verification status.**
> The sandbox this project was written in cannot reach Google's Maven repository or the Android SDK, so **the `:app` module has never been compiled, run on an emulator or tested on a device**, and **no APK exists**. The audio engine (`:dsp`) and the library/playlist logic (`:domain`) are pure Kotlin and **were** compiled and tested (32 + 14 JUnit tests, all passing). Details in [`docs/VERIFICATION.md`](docs/VERIFICATION.md). Expect to fix a few compile errors on first build (most likely Media3 / Compose signature drift); the likely spots are listed there.

## Build and run

Requirements: Android Studio Ladybug (2024.2) or newer, JDK 17, Android SDK 35.

1. Open the `VintageVinyl` folder in Android Studio and let Gradle sync (wrapper pins Gradle 8.9, AGP 8.7.3, Kotlin 2.0.21).
2. Run the `app` configuration on a device or emulator (Android 8.0 / API 26 or newer). Use headphones or speakers; a real device is recommended for judging the sound.
3. Command line: `./gradlew :app:assembleDebug` (APK in `app/build/outputs/apk/debug/`), `./gradlew :dsp:test :domain:test` for the unit tests.

On first launch, allow audio access (and notifications on Android 13+). You can also import individual files with the import button in the Library (Storage Access Framework, no storage permission needed for those).

## Modules

| Module | Kind | What it holds | Status |
|---|---|---|---|
| `:dsp` | pure Kotlin/JVM | the whole audio engine, presets, settings model, offline `RenderCli` | compiled, 32 tests pass |
| `:domain` | pure Kotlin/JVM | track/album/artist models, search index, playlist and settings codecs, preset book | compiled, 14 tests pass |
| `:app` | Android | Media3 playback service, audio processor adapter, data layer, Compose UI | written, **not compiled** |

## Features

- **Library**: scans MediaStore, imports files via SAF, search, Songs / Albums / Artists / Playlists, album artwork, playlist create / rename / delete, add and remove songs.
- **Playback**: play/pause, previous/next, seek, shuffle, repeat (off/all/one), volume, queue view with reorder and remove, background playback with a media notification and lock-screen controls (Media3 `MediaSessionService`), audio focus and becoming-noisy handling by ExoPlayer, automatic skip on unreadable files.
- **Sound Lab**: master switch, 8 presets plus your own saved presets, record condition (Mint → Worn), turntable speed 33⅓ / 45 / 78 rpm, stereo character (mono / narrow / vintage / full), 50 / 60 Hz hum, 6 EQ voicings, and independent amount + on/off for 15 effects.
- **Interface**: walnut / cream / antique-gold / copper theme, spinning record with grooves, label and artwork, tonearm that travels across the record with progress, spin-up / spin-down animation, hand-drawn rotary knobs, faders and brass toggles, portrait and landscape layouts, mini player, accessibility labels on every control.

## Presets

Clean Vinyl, Dusty Vinyl, Old Gramophone, Antique Radio, 1950s Vintage, 1960s Warmth, 1970s Analog, Deep Nostalgia. These and the six EQ profiles are **creative voicings inspired by vintage gear, not emulations of specific products**.

## Honest limits

- EQ + noise + modulation is an *impression* of vinyl and old equipment, not a physical model of a stylus in a groove. The docs say exactly what is modelled and what is not ([`docs/DSP.md`](docs/DSP.md)).
- Processing runs inside Media3's audio sink, X. Output is 16-bit with TPDF dither.
- Only mono/stereo 16-bit or float PCM is processed; other formats play unprocessed.

See also [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).
