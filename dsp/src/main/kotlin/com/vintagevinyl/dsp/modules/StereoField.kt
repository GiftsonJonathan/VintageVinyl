package com.vintagevinyl.dsp.modules

import com.vintagevinyl.dsp.StereoMode
import com.vintagevinyl.dsp.util.Biquad
import com.vintagevinyl.dsp.util.Smoother

/**
 * Mono / narrow / vintage / full stereo.
 *
 * Works in mid/side. The side signal is scaled by `width` (0 = true mono, 1 = untouched) and,
 * for the vintage modes, its content below ~150 Hz is removed ("elliptical" bass mono): real
 * lathe-cut records cannot carry out-of-phase bass without making the stylus jump the groove,
 * so cutting engineers fold the low end to mono. Both values glide, so changing mode during
 * playback is a smooth morph rather than a switch.
 */
class StereoField(fs: Int) {
    private val width = Smoother(1f, 0.06f)
    private val bassMono = Smoother(0f, 0.06f)
    // Linkwitz-Riley 4th order high-pass (two cascaded Butterworth sections) on the side signal.
    private val hp1 = Biquad()
    private val hp2 = Biquad()

    init {
        hp1.highPass(fs, 150.0, 0.7071)
        hp2.highPass(fs, 150.0, 0.7071)
    }

    fun configure(blockDurationSec: Float) {
        width.configure(blockDurationSec)
        bassMono.configure(blockDurationSec)
    }

    fun setMode(mode: StereoMode) {
        width.target = mode.width
        bassMono.target = mode.bassMono
    }

    fun reset() {
        hp1.reset(); hp2.reset()
    }

    fun process(l: FloatArray, r: FloatArray, n: Int) {
        width.step()
        bassMono.step()
        val neutral = width.current == 1f && width.target == 1f &&
            bassMono.current == 0f && bassMono.target == 0f
        if (neutral) return

        val w0 = width.previous
        val w1 = width.current
        val b0 = bassMono.previous
        val b1 = bassMono.current
        val inv = 1f / n
        for (i in 0 until n) {
            val t = (i + 1) * inv
            val w = w0 + (w1 - w0) * t
            val b = b0 + (b1 - b0) * t
            val m = (l[i] + r[i]) * 0.5f
            val s = (l[i] - r[i]) * 0.5f
            // Blend between the full side signal (b = 0) and its high-passed version (b = 1).
            // (Subtracting a low-pass instead would leave most of the bass: the phase shift of
            // the low-pass means s - LP(s) is a poor high-pass at 1/3 of the corner frequency.)
            val sHigh = hp2.process(hp1.process(s))
            val s2 = (s + b * (sHigh - s)) * w
            l[i] = m + s2
            r[i] = m - s2
        }
    }
}
