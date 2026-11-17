# VintageVinyl 🎵

![License](https://img.shields.io/github/license/GiftsonJonathan/VintageVinyl)
![Release](https://img.shields.io/github/v/release/GiftsonJonathan/VintageVinyl)
![Platform](https://img.shields.io/badge/platform-Android-green)

**Give your digital music the warmth and character of a vintage record player.**

VintageVinyl is an Android music player that transforms your digital music listening experience through a real-time vintage-inspired audio processing engine.

Bring the character of vinyl records, gramophones and classic radio equipment to your own music library. Explore warm tonal coloration, surface noise, crackle and pops, wow and flutter, vintage EQ voicings, saturation and more.

Every effect has independent controls and works live while your music plays.

[**Download VintageVinyl v1.0.0**](https://github.com/GiftsonJonathan/VintageVinyl/releases)

---

## Features

### 🎧 Your Music Library

- Scan your device's music library using MediaStore.
- Import individual audio files using the Storage Access Framework.
- Browse Songs, Albums, Artists and Playlists.
- Search your music collection.
- View album artwork.
- Create, rename and delete playlists.
- Add and remove songs from playlists.

### ▶️ Music Playback

- Play, pause, previous and next.
- Seek through tracks.
- Shuffle and repeat modes.
- Volume control.
- Queue management with reordering and removal.
- Background playback through Media3.
- Media notification and lock-screen controls.
- Audio focus handling and automatic skipping of unreadable files.

### 🎚️ Vintage Sound Lab

Transform your music with a collection of independently adjustable audio effects.

- Master effect switch.
- Eight built-in sound presets.
- Create and save custom presets.
- Record condition simulation: Mint to Worn.
- Turntable speeds: 33⅓, 45 and 78 RPM.
- Stereo character: Mono, Narrow, Vintage and Full.
- 50 Hz and 60 Hz hum.
- Six vintage EQ voicings.
- Fifteen independently adjustable effects.

### 💿 Vintage Interface

- Walnut, cream, antique-gold and copper visual theme.
- Animated spinning vinyl record.
- Record grooves, label and album artwork.
- Animated tonearm that follows playback progress.
- Turntable spin-up and spin-down animations.
- Custom rotary knobs, faders and brass toggles.
- Portrait and landscape layouts.
- Mini player and accessibility labels.

---

## Sound Presets

VintageVinyl includes eight built-in presets:

| Preset | Character |
|---|---|
| Clean Vinyl | Subtle vinyl-inspired coloration |
| Dusty Vinyl | Surface noise and worn-record character |
| Old Gramophone | Antique playback character |
| Antique Radio | Classic radio-inspired tone |
| 1950s Vintage | Mid-century tonal character |
| 1960s Warmth | Warm vintage coloration |
| 1970s Analog | Analog-inspired sound |
| Deep Nostalgia | A more pronounced nostalgic character |

These presets and the six EQ profiles are creative voicings inspired by vintage equipment, not exact emulations of specific products.

---

## Screenshots

Screenshots of the turntable, music library and Sound Lab will be added here.

---

## Download and Installation

**Latest release: v1.0.0**

[Download VintageVinyl from GitHub Releases](https://github.com/GiftsonJonathan/VintageVinyl/releases)

### Requirements

- Android 8.0 (API 26) or newer.
- An Android device with compatible audio playback support.

### Installation

1. Open the [GitHub Releases page](https://github.com/GiftsonJonathan/VintageVinyl/releases).
2. Download `VintageVinyl-v1.0.0.apk`.
3. Open the downloaded APK on your Android device.
4. If prompted, allow installation from that source.
5. Complete installation and launch VintageVinyl.

VintageVinyl plays music from your own digital music library.

---

## Technology Stack

| Component | Technology |
|---|---|
| Platform | Android |
| Language | Kotlin |
| UI | Jetpack Compose |
| Playback | AndroidX Media3 / ExoPlayer |
| Background playback | MediaSessionService |
| Local storage | MediaStore and Storage Access Framework |
| Settings persistence | Jetpack DataStore |
| Audio processing | Custom Kotlin DSP engine |
| Build system | Gradle |

### Project Architecture

VintageVinyl is organized into three modules:

| Module | Responsibility |
|---|---|
| `:dsp` | Audio processing engine, presets, settings model and offline RenderCli |
| `:domain` | Track, album and artist models, search index, playlist and settings codecs |
| `:app` | Android playback service, audio processor adapter, data layer and Compose interface |

---

## Audio Processing Engine

The audio engine is built around a real-time signal-processing chain.

Its processing modules include:

- Vintage equalization.
- Surface noise generation.
- Speed variation and wow/flutter.
- Saturation and dynamics coloration.
- Hum and rumble.
- Speaker cabinet coloration.
- Stereo field processing.

The engine also includes preset management, configurable parameters and an offline rendering utility.

### Technical Limitations

VintageVinyl aims to recreate the listening character of vintage audio equipment, not physically reproduce the mechanics of a vinyl record.

- The effects create an impression of vinyl and vintage playback rather than a physical model of a stylus moving through a groove.
- Audio processing runs inside Media3's audio sink.
- Output uses 16-bit audio with TPDF dithering.
- Only mono/stereo 16-bit PCM and float PCM are processed. Other formats play without this processing.

---

## Build from Source

### Requirements

- Android Studio Ladybug (2024.2) or newer.
- JDK 17.
- Android SDK 35.

The project uses:

- Gradle 8.9.
- Android Gradle Plugin 8.7.3.
- Kotlin 2.0.21.

### Getting Started

Clone the repository:

```bash
git clone https://github.com/GiftsonJonathan/VintageVinyl.git
cd VintageVinyl
```

Open the project in Android Studio and allow Gradle synchronization to complete.

Run the debug build:

**Windows PowerShell**

```powershell
.\gradlew.bat :app:assembleDebug
```

**macOS / Linux**

```bash
./gradlew :app:assembleDebug
```

The debug APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Run the unit tests:

**Windows PowerShell**

```powershell
.\gradlew.bat :dsp:test :domain:test
```

**macOS / Linux**

```bash
./gradlew :dsp:test :domain:test
```

### Release Builds

Release builds require a locally configured Android signing keystore.

Never commit your signing keystore, signing passwords or `keystore.properties` to the repository.

---

## Verification Status

The project has successfully completed its first Android release build.

| Verification | Status |
|---|---|
| DSP module compilation | Passed |
| DSP unit tests | 32 passed |
| Domain module compilation | Passed |
| Domain unit tests | 14 passed |
| Android release compilation | Passed |
| Release APK signing | Verified |
| Physical device installation | Successful |
| Core playback and Sound Lab testing | Passed on Xiaomi device |

**Release:** v1.0.0

**Release artifact:** `VintageVinyl-v1.0.0.apk`

See the following documentation for additional technical details:

- [Architecture](docs/ARCHITECTURE.md)
- [DSP documentation](docs/DSP.md)
- [Verification details](docs/VERIFICATION.md)

---

## Feedback and Contributions

VintageVinyl is at its first public release.

If you encounter a bug, have a suggestion or want to discuss an improvement, please use the repository's [GitHub Issues](https://github.com/GiftsonJonathan/VintageVinyl/issues).

Feedback from early users will help guide future improvements.

---

## License

VintageVinyl is licensed under the [MIT License](LICENSE).

You are free to use, copy, modify, merge, publish, distribute, sublicense, and sell copies of the software, subject to the conditions of the MIT License.

See the [`LICENSE`](LICENSE) file for the complete license text.

Copyright © 2026 Giftson Jonathan.

---

**VintageVinyl — Rediscover the warmth of listening.** 💿
