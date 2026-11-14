package com.vintagevinyl.dsp.modules

import com.vintagevinyl.dsp.util.Smoother
import com.vintagevinyl.dsp.util.StereoBiquad
import com.vintagevinyl.dsp.util.StereoSvf
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.pow

/**
 * The sound of the *playback device*, applied last so that it colours the music, the record
 * noise and the hum alike (as a real horn or cabinet would).
 *
 *  * **Speaker coloration**: the band-limiting of a small driver (high-pass up to ~280 Hz,
 *    low-pass down to ~4.2 kHz) plus a mid "honk" around 1.5 kHz.
 *  * **Acoustic resonance**: a parallel bank of three narrow resonators (190 / 520 / 1350 Hz)
 *    that ring slightly, imitating cabinet panels, a horn throat or a hollow tonearm.
 */
class SpeakerCabinet(private val fs: Int) {

    private val speaker = Smoother(0f, 0.06f)
    private val resonance = Smoother(0f, 0.06f)

    private val hp = StereoSvf(high = true)
    private val lp = StereoSvf(high = false)
    private val honk = StereoBiquad()
    private val bank = arrayOf(StereoBiquad(), StereoBiquad(), StereoBiquad())
    private val bankHz = doubleArrayOf(190.0, 520.0, 1350.0)
    private val bankQ = doubleArrayOf(9.0, 12.0, 14.0)
    private val bankGain = floatArrayOf(0.28f, 0.20f, 0.14f)
    private val nyq = min(19000f, 0.45f * fs)

    init {
        for (k in bank.indices) bank[k].design { bandPass(fs, bankHz[k], bankQ[k]) }
    }

    fun configure(blockDurationSec: Float) {
        speaker.configure(blockDurationSec)
        resonance.configure(blockDurationSec)
    }

    fun setTargets(speakerAmount: Float, resonanceAmount: Float) {
        speaker.target = speakerAmount
        resonance.target = resonanceAmount
    }

    fun reset() {
        hp.reset(); lp.reset(); honk.reset(); bank.forEach { it.reset() }
    }

    private fun logLerp(a: Double, b: Double, t: Double) = a * (b / a).pow(t)

    fun process(l: FloatArray, r: FloatArray, n: Int) {
        speaker.step()
        resonance.step()

        if (!(speaker.previous == 0f && speaker.current == 0f)) {
            val a = speaker.current.toDouble()
            hp.design(fs, logLerp(30.0, 280.0, a), 0.75)
            lp.design(fs, logLerp(nyq.toDouble(), 4200.0, a), 0.75)
            honk.design { peaking(fs, 1500.0, 4.0 * a, 1.1) }
            // HP/LP are not transparent at any corner, so they fade in over the first 12 % of
            // the slider (see StereoBiquad.processBlend) instead of switching on at a threshold.
            val b0 = minOf(1f, speaker.previous * 8f)
            val b1 = minOf(1f, speaker.current * 8f)
            hp.processBlend(l, r, n, b0, b1)
            lp.processBlend(l, r, n, b0, b1)
            honk.process(l, r, n)
        } else {
            hp.reset(); lp.reset(); honk.reset()
        }

        val r0 = resonance.previous
        val r1 = resonance.current
        if (r0 != 0f || r1 != 0f) {
            val inv = 1f / n
            for (i in 0 until n) {
                val a = r0 + (r1 - r0) * (i + 1) * inv
                val xl = l[i]
                val xr = r[i]
                var yl = xl
                var yr = xr
                for (k in bank.indices) {
                    val g = bankGain[k] * a
                    yl += g * bank[k].left.process(xl)
                    yr += g * bank[k].right.process(xr)
                }
                l[i] = yl
                r[i] = yr
            }
        }
    }
}
