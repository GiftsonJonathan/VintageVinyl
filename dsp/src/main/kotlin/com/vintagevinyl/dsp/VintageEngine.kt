package com.vintagevinyl.dsp

import com.vintagevinyl.dsp.modules.DynamicsColor
import com.vintagevinyl.dsp.modules.HumRumble
import com.vintagevinyl.dsp.modules.Saturator
import com.vintagevinyl.dsp.modules.SpeakerCabinet
import com.vintagevinyl.dsp.modules.SpeedVariation
import com.vintagevinyl.dsp.modules.StereoField
import com.vintagevinyl.dsp.modules.SurfaceNoise
import com.vintagevinyl.dsp.modules.VintageEq
import com.vintagevinyl.dsp.util.Rng
import com.vintagevinyl.dsp.util.Smoother
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.tanh

/**
 * The vintage playback chain. Platform-independent: it only knows about interleaved stereo
 * float buffers, so it runs identically inside the Android audio pipeline and in a desktop JVM
 * unit test or offline renderer.
 *
 * ```
 * input -> StereoField -> SpeedVariation (wow/flutter, fixed ~4 ms latency)
 *       -> DynamicsColor -> Saturator (saturation, tape, harmonics) -> VintageEq
 *       -> (+ SurfaceNoise) -> (+ HumRumble) -> SpeakerCabinet -> soft limiter
 *       -> crossfade with the latency-aligned dry signal (master switch) -> output
 * ```
 *
 * ### Real-time rules
 *  * No allocation, locking or I/O in [processInterleaved]; all state is preallocated.
 *  * [updateSettings] may be called from any thread. It publishes an immutable snapshot through
 *    a volatile field; the audio thread picks it up at the start of its next call.
 *  * Every parameter is smoothed, so switching effects or presets *during playback* does not
 *    click. When the master switch is off and the crossfade has finished, the chain is skipped
 *    entirely: the output is then a bit-exact copy of the input delayed by [latencyFrames].
 */
class VintageEngine(val sampleRate: Int, seed: Long = System.nanoTime()) {

    companion object {
        /** Control-rate block size in frames. */
        const val BLOCK = 64
        private const val LIMIT_KNEE = 0.89f
    }

    private val stereo = StereoField(sampleRate)
    private val speed = SpeedVariation(sampleRate, seed)
    private val dynamics = DynamicsColor(sampleRate)
    private val saturator = Saturator(sampleRate)
    private val eq = VintageEq(sampleRate)
    private val noise = SurfaceNoise(sampleRate, seed)
    private val hum = HumRumble(sampleRate, seed)
    private val cabinet = SpeakerCabinet(sampleRate)
    private val mix = Smoother(1f, 0.04f)
    private val ditherRng = Rng(seed xor 0xD17E5L)

    private val l = FloatArray(BLOCK)
    private val r = FloatArray(BLOCK)
    private val dryL = FloatArray(BLOCK)
    private val dryR = FloatArray(BLOCK)
    private val nl = FloatArray(BLOCK)
    private val nr = FloatArray(BLOCK)

    @Volatile private var pending: VintageSettings = VintageSettings.DEFAULT
    private var applied: VintageSettings? = null
    private var wasBypassed = true

    /** Fixed latency introduced by the engine, in frames (about 4 ms). */
    val latencyFrames: Int get() = speed.centerDelay

    init {
        val dur = BLOCK.toFloat() / sampleRate
        stereo.configure(dur); speed.configure(dur); dynamics.configure(dur)
        saturator.configure(dur); eq.configure(dur); noise.configure(dur)
        hum.configure(dur); cabinet.configure(dur); mix.configure(dur)
    }

    /** Thread-safe. Takes effect (smoothly) on the audio thread's next call. */
    fun updateSettings(settings: VintageSettings) {
        pending = settings
    }

    /** Clears filter and delay memory (call after a seek/flush). Settings are kept. */
    fun reset() {
        stereo.reset(); speed.reset(); dynamics.reset(); saturator.reset()
        eq.reset(); noise.reset(); hum.reset(); cabinet.reset()
        wasBypassed = true
    }

    /** Random value in [-1, 1) LSBs for TPDF dither when quantising to 16 bit. */
    fun nextDitherLsb(): Float = ditherRng.nextFloat() - ditherRng.nextFloat()

    private fun applyTargets(s: VintageSettings, first: Boolean) {
        stereo.setMode(s.stereoMode)
        speed.setTargets(s.amount(EffectId.WOW), s.amount(EffectId.FLUTTER), s.turntableSpeed.revolutionHz)
        dynamics.setTarget(s.amount(EffectId.DYNAMICS))
        saturator.setTargets(s.amount(EffectId.SATURATION), s.amount(EffectId.TAPE), s.amount(EffectId.HARMONICS))
        eq.setTargets(s.eqProfile, s.amount(EffectId.EQ_PROFILE), s.amount(EffectId.WARMTH), s.amount(EffectId.ROLLOFF))
        noise.setTargets(
            s.recordCondition, s.amount(EffectId.SURFACE_NOISE), s.amount(EffectId.CRACKLE),
            s.amount(EffectId.POPS), s.stereoMode.width, s.turntableSpeed.revolutionHz,
        )
        hum.setTargets(s.amount(EffectId.HUM), s.humFrequency.hz)
        cabinet.setTargets(s.amount(EffectId.SPEAKER), s.amount(EffectId.RESONANCE))
        val m = if (s.masterEnabled) 1f else 0f
        if (first) mix.snap(m) else mix.target = m
    }

    private fun softLimit(x: Float): Float {
        val a = abs(x)
        if (a <= LIMIT_KNEE) return x
        val o = LIMIT_KNEE + (1f - LIMIT_KNEE) * tanh((a - LIMIT_KNEE) / (1f - LIMIT_KNEE))
        return if (x < 0f) -o else o
    }

    /**
     * Processes [frames] stereo frames in place. [buf] holds interleaved L,R floats in -1..1.
     *
     * @return true if every block was a pure bypass (output == delayed input, bit-exact), in
     *         which case the caller should skip dithering when converting back to integers.
     */
    fun processInterleaved(buf: FloatArray, frames: Int): Boolean {
        val s = pending
        if (s !== applied) {
            applyTargets(s, first = applied == null)
            applied = s
        }

        var allBypass = true
        var off = 0
        while (off < frames) {
            val n = min(BLOCK, frames - off)
            var p = off * 2
            for (i in 0 until n) {
                l[i] = buf[p]
                r[i] = buf[p + 1]
                p += 2
            }

            mix.step()
            val m0 = mix.previous
            val m1 = mix.current
            if (m0 == 0f && m1 == 0f && mix.target == 0f) {
                speed.passThrough(l, r, n)
                wasBypassed = true
            } else {
                allBypass = false
                if (wasBypassed) {
                    stereo.reset(); dynamics.reset(); saturator.reset()
                    eq.reset(); hum.reset(); cabinet.reset(); speed.restartModulation()
                    wasBypassed = false
                }
                val needDry = !(m0 == 1f && m1 == 1f)

                stereo.process(l, r, n)
                speed.process(l, r, n, if (needDry) dryL else null, if (needDry) dryR else null)
                dynamics.process(l, r, n)
                saturator.process(l, r, n)
                eq.process(l, r, n)
                if (noise.process(nl, nr, n)) {
                    for (i in 0 until n) {
                        l[i] += nl[i]
                        r[i] += nr[i]
                    }
                }
                hum.process(l, r, n)
                cabinet.process(l, r, n)

                val inv = 1f / n
                for (i in 0 until n) {
                    var wl = softLimit(l[i])
                    var wr = softLimit(r[i])
                    if (needDry) {
                        val m = m0 + (m1 - m0) * (i + 1) * inv
                        wl = dryL[i] * (1f - m) + wl * m
                        wr = dryR[i] * (1f - m) + wr * m
                    }
                    l[i] = wl
                    r[i] = wr
                }
            }

            p = off * 2
            for (i in 0 until n) {
                buf[p] = l[i]
                buf[p + 1] = r[i]
                p += 2
            }
            off += n
        }
        return allBypass
    }
}
