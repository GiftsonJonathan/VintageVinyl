# Verification report

This separates what was **actually run** from what is **only implemented**. Nothing is listed as passing unless it was executed.

## Verified (executed in the build sandbox, JVM only)

Method: `:dsp` and `:domain` were compiled with `kotlinc` and tested with JUnit 4 directly, because Gradle could not download the Kotlin/Android plugins (see below). The same sources are what Gradle builds.

**`:dsp` - 32 tests, all pass** (`dsp/src/test/.../EngineTests.kt`), covering:

| Area | Tests |
|---|---|
| Bypass and transparency | master off is a bit-exact delayed copy; all effects at zero is transparent; PCM round trip |
| Glitch-free control | master toggle, preset switching during playback and toggling every effect live produce no clicks (discontinuity measurements) |
| Wow / flutter | measured pitch deviation matches target and rotation rate; flutter is faster and smaller; no deviation when both are zero |
| Surface noise | hiss level follows condition and slider; spectrum is high-frequency, not rumble; silence in = silence out when noise off |
| Crackle / pops | irregular, clustered, not a loop; differs between seeds, reproducible for one seed; density scales with slider and condition; pops are rare and low-frequency; scratches repeat per revolution |
| Tone | EQ profiles are measurably distinct; roll-off and warmth shape the spectrum; speaker band-limits |
| Stereo | modes behave as described; vintage mode folds bass to mono |
| Nonlinear | saturation adds harmonics monotonically; aliasing is suppressed; dynamics colour reduces crest factor; hum adds mains harmonics |
| Robustness | every preset is finite and never exceeds full scale; deterministic for a seed; chunk size does not change the sound; sample rates 22.05-96 kHz; biquad designs match their specification; settings model semantics |
| Performance | 30 s of stereo 44.1 kHz with the heaviest preset rendered at ~44x real time (~2.3 % of one core) on the sandbox JVM |

**`:domain` - 14 tests, all pass** (`DomainTests.kt`): library index grouping and search, playlist / track / settings codecs including corrupt input, preset book.

Tests were re-run after the last engine change (`speed.restartModulation()` on leaving bypass): 32/32 and 14/14.

These tests show the engine does what it was written to do. They do **not** show it *sounds good*; nobody has listened to it yet.

## Implemented but NOT verified

The whole `:app` module (25 Kotlin files, manifest, resources, Gradle config) has **never been compiled or run**. The sandbox's network allowlist blocks Google Maven, Maven Central and the Android SDK, so there is no AGP, no Compose, no Media3, no emulator and no APK. Consequently, none of the following has been tested:

* that `:app` compiles at all
* playback of any format (MP3, AAC, FLAC, OGG, WAV, Opus) and gapless transitions
* the audio processor inside the real ExoPlayer sink; live parameter changes on a device
* background playback, notification, lock-screen and Bluetooth controls, audio focus, headphone unplug
* MediaStore scan, SAF import, permissions flow, DataStore persistence, playlist persistence
* Compose rendering, animations, layouts on phones/tablets, landscape, accessibility behaviour
* CPU, battery and memory on a device; long sessions; rotation and process death
* no APK is included

## Where compile errors are most likely

Written from API knowledge without a compiler, so check these first:

1. `VintagePlaybackService`: `DefaultRenderersFactory.buildAudioSink` override signature, `DefaultAudioSink.Builder.setAudioProcessors`, `DefaultMediaNotificationProvider.Builder.setChannelName`.
2. `VintageAudioProcessor`: `BaseAudioProcessor.onConfigure / onFlush / queueInput` signatures in Media3 1.5.1.
3. `NowPlayingScreen`: `ModalBottomSheet` experimental opt-in (already annotated at file level).
4. Compose details: modifier and `Text` parameter names, `semantics` usage, `AnimatedContentTransitionScope` APIs in navigation-compose 2.8.5 transitions.
5. Version-catalog aliases in `app/build.gradle.kts` versus `gradle/libs.versions.toml`.

## Suggested first manual test pass (not yet done)

1. Build, install on a device with some music; confirm scan and playback.
2. Toggle master on/off while playing; listen for clicks.
3. Step through all presets; sweep each knob; confirm no glitches.
4. Check notification controls, headphone unplug, incoming-call interruption.
5. Profile CPU with the Android Studio profiler on the Deep Nostalgia preset.
