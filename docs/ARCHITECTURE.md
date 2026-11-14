# Architecture

## Why three Gradle modules

```
:dsp      pure Kotlin  - audio engine, settings model, presets          (no Android types)
:domain   pure Kotlin  - models, library index, codecs, preset book     (depends on :dsp)
:app      Android      - Media3 service, data layer, Compose UI         (depends on :domain, :dsp)
```

The split keeps everything that can be reasoned about and unit-tested (all DSP maths, search, playlist and settings serialisation) free of Android, so it runs on a plain JVM. Android code is kept thin: it adapts the engine to Media3 and renders state.

## Layers (MVVM, clean-ish)

```
UI (Compose screens, components)
  -> ViewModels (PlayerViewModel, LibraryViewModel, SoundViewModel)  expose StateFlow
     -> Repositories / data sources (LibraryRepository, PlaylistRepository, SoundRepository, ArtworkRepository, ImportedTracksStore)
        -> MediaStore, SAF, DataStore
     -> PlaybackConnection (MediaController)  <->  VintagePlaybackService (ExoPlayer + MediaSession)
                                                       -> VintageAudioProcessor -> VintageEngine (:dsp)
```

### Dependency injection
Manual DI (`AppContainer`, `AppViewModelFactory`). Hilt was **not** used: the graph is about a dozen singletons, Hilt would add KSP/KAPT and generated code to the build, and the playback service already reaches the container through the `Application`. Hilt becomes worthwhile if the graph grows or needs scoping.

## Playback

* `VintagePlaybackService` (a `MediaSessionService`) owns the ExoPlayer and the `MediaSession`. Because the player lives in the service, playback continues when the UI is gone; Media3 provides the media notification, lock-screen / Bluetooth controls and foreground-service handling.
* ExoPlayer is built with a `DefaultRenderersFactory` whose `buildAudioSink` installs `VintageAudioProcessor` into `DefaultAudioSink`. The processor sits in the PCM path, so seeking, gapless transitions and format changes are handled by Media3 (`onFlush` resets the engine, end of stream drains the tail).
* The UI talks to the service through a `MediaController` wrapped in `PlaybackConnection`, which publishes an immutable `PlayerState`. Commands issued before the controller connects are queued. Playback position is a separate polling `Flow` so the screens do not recompose every tick; the deck only recomposes when the whole-percent progress changes.
* The service collects `SoundRepository.settings` and pushes it to the processor; the engine reads parameters through smoothers, so changes never need a restart.
* Unplayable items are skipped (`onPlayerError`) and the error is shown on the plaque.

## Library
`MediaStoreSource` scans audio, `ImportedTracksStore` persists SAF imports (with persistable URI permissions), `LibraryIndex` (in `:domain`) builds albums / artists and does tokenised search. A `ContentObserver` refreshes after changes. Artwork is loaded lazily and cached by `ArtworkRepository`.

## Persistence
DataStore (Preferences) holds the sound settings, custom presets, playlists and imported-track records, encoded by the codecs in `:domain` (which are unit-tested, including corrupt input).

## UI
Single-activity Compose, `NavHost` with Deck / Library / Sound tabs and a slide-up Now Playing destination, custom bottom bar and mini player. All controls (rotary knob, fader, toggle, brass button, seek bar) are custom composables with semantics and are bound to real parameters. The turntable deck is drawn with Canvas; platter rotation is an `Animatable` read inside `graphicsLayer`, so spinning causes no recomposition.
