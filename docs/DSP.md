# Audio engine (`:dsp`)

## What it is, and what it is not

The engine is a real-time stereo effects chain that makes digital music *sound like it is being played from an old record player or radio*. It combines tone shaping, nonlinear distortion, pitch instability and procedurally generated surface noise. It is an **artistic approximation**. It is not a physical simulation of a stylus tracking a groove, a cartridge's electromechanics, a valve amplifier or a horn, and an EQ plus a crackle overlay is not the same thing as real vinyl. Presets and EQ profiles are **creative voicings inspired by** each era, not emulations of any particular product.

## Signal flow

```
input (float stereo; tested at 22.05, 32, 44.1, 48, 88.2 and 96 kHz)
 -> StereoField      mono / narrow / vintage / full, bass-to-mono
 -> SpeedVariation   wow + flutter (modulated fractional delay, fixed ~4.2 ms latency)
 -> DynamicsColor    gentle compression / level colouring
 -> Saturator        saturation, tape-style soft clip, odd/even harmonics
 -> VintageEq        profile voicing + warmth + roll-off
 -> + SurfaceNoise   hiss, crackle, pops/scratches (generated, not sampled)
 -> + HumRumble      50/60 Hz mains hum and harmonics, low rumble
 -> SpeakerCabinet   small-speaker band limiting + cabinet resonance
 -> soft limiter
 -> master crossfade against the latency-aligned dry signal
 -> 16-bit with TPDF dither
```

Blocks of 64 frames are processed; every parameter goes through a one-pole smoother, so any control can move while music plays without zipper noise or restarts.

## Modules

| Module | Technique |
|---|---|
| Wow / flutter | Pitch deviation is synthesised in the *pitch domain* (wow: slow, tied to the record rotation rate; flutter: faster, smaller, band-limited noise) and integrated into a Catmull-Rom interpolated delay, so changing depth changes deviation without bending the average pitch. Targets: wow up to about 1.2 % peak, flutter about 0.45 %. |
| Surface noise | Hiss shaped by the *record condition* (level, corner frequencies), so a worn record is brighter and louder than a mint pressing. |
| Crackle | Poisson process with burst clustering (a two-state Markov chain), log-uniform amplitudes and short damped-resonance click voices; never a loop. Density follows the slider and the record condition. |
| Pops / scratches | Rare low-frequency thumps, plus per-revolution scratches that repeat once per turn at the selected speed (33⅓ / 45 / 78 rpm). |
| Saturation, tape, harmonics | `tanh`-family waveshapers with first-order antiderivative anti-aliasing (ADAA) to avoid inharmonic aliasing, and a wet/dry blend so the effect fades out cleanly. |
| EQ voicings | RBJ biquads and state-variable filters arranged as six profiles: warm vinyl, vintage radio, antique gramophone, 1950s record player, 1960s hi-fi, 1970s analogue stereo. |
| Speaker / resonance | Band-limiting plus a resonant peak to suggest a small cabinet or horn. |
| Stereo | Mono, narrow, vintage (bass folded to mono with a 4th-order high-pass on the side signal at 150 Hz) and full. |
| Hum | 50 or 60 Hz fundamental with harmonics. |

All effects have an independent amount and on/off switch; switching an effect crossfades it, it never hard-bypasses.

## Master bypass
When the master switch is off the chain is skipped after a short crossfade and the output is the latency-aligned dry signal, tested to be **bit-exact**. On re-entry the stateful stages are reset and the wow/flutter integrator is restarted from centre so the sound does not start mid-deviation.

## Android integration and its compromises

* The engine runs as a Media3 `BaseAudioProcessor` inside `DefaultAudioSink`. This gives low latency and correct behaviour with seek, pause and gapless playback, but it means processing happens only for this app's playback and only for PCM that reaches the sink.
* Input accepted: 16-bit or float PCM, mono or stereo. Anything else (for example multichannel) makes the processor inactive and the track plays untouched rather than failing.
* Output is 16-bit stereo. Float output would avoid quantisation but not all devices handle it well; TPDF dither is applied when reducing to 16 bit.
* Added latency is ~4.2 ms (the wow/flutter delay line), which is irrelevant for music playback but means the engine is not suited to live monitoring.
* Audio offload and encoded passthrough are not used, because they bypass the processing chain. This costs some battery on long listening sessions compared with offloaded playback.
* The random generators are seeded from the system clock when an engine is created, so each session's crackle differs; the seed can be fixed for reproducible renders, and tests cover determinism for a fixed seed and difference between seeds. Crackle is generated continuously, so it never loops.

## Measured (JVM, not a phone)
The offline render of the test signal ran at about 44x real time for 30 s of stereo 44.1 kHz audio with the Deep Nostalgia preset (the heaviest preset), using about 2.3 % of one core on the sandbox JVM. This is **not** a measurement on an Android device; ARM phones, thermal limits and the Android audio thread will differ. See `VERIFICATION.md`.

## Offline renderer
`dsp/src/main/kotlin/com/vintagevinyl/dsp/tools/RenderCli.kt` renders a WAV through any preset:
`RenderCli in.wav out.wav "Dusty Vinyl" [seed]`. It is useful for auditioning changes without a phone, although it cannot substitute for listening on real hardware.
