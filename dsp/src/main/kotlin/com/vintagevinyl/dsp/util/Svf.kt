package com.vintagevinyl.dsp.util

import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.tan

/**
 * Second-order low-pass / high-pass as a topology-preserving-transform (TPT) state-variable
 * filter (Zavalishin, "The Art of VA Filter Design").
 *
 * Its magnitude and phase response equal the bilinear-transform [Biquad] designs, but unlike a
 * transposed-direct-form biquad its two integrator states are *physical* quantities (voltages
 * on capacitors), so changing the cutoff while the filter is running produces no state
 * mismatch glitch. That matters here because the roll-off, speaker and profile corner
 * frequencies glide while the music plays.
 */
class Svf(private val high: Boolean) {
    private var a1 = 0.0
    private var a2 = 0.0
    private var a3 = 0.0
    private var k = 1.4142
    private var ic1 = 0.0
    private var ic2 = 0.0

    fun reset() {
        ic1 = 0.0
        ic2 = 0.0
    }

    fun design(fs: Int, cutoffHz: Double, q: Double) {
        val f = min(max(cutoffHz, 5.0), 0.49 * fs)
        val g = tan(PI * f / fs)
        k = 1.0 / max(q, 0.1)
        a1 = 1.0 / (1.0 + g * (g + k))
        a2 = g * a1
        a3 = g * a2
    }

    fun copyCoefficientsFrom(o: Svf) {
        a1 = o.a1; a2 = o.a2; a3 = o.a3; k = o.k
    }

    fun process(x: Float): Float {
        val xd = x.toDouble()
        val v3 = xd - ic2
        val v1 = a1 * ic1 + a2 * v3
        val v2 = ic2 + a2 * ic1 + a3 * v3
        ic1 = 2.0 * v1 - ic1
        ic2 = 2.0 * v2 - ic2
        return (if (high) xd - k * v1 - v2 else v2).toFloat()
    }
}

/** Stereo pair of identically tuned [Svf]s with wet/dry blending (see [StereoBiquad.processBlend]). */
class StereoSvf(high: Boolean) {
    val left = Svf(high)
    val right = Svf(high)

    fun reset() { left.reset(); right.reset() }

    fun design(fs: Int, cutoffHz: Double, q: Double) {
        left.design(fs, cutoffHz, q)
        right.copyCoefficientsFrom(left)
    }

    /** `y = x + b * (filter(x) - x)`, `b` ramping linearly from [b0] to [b1] over the block. */
    fun processBlend(l: FloatArray, r: FloatArray, n: Int, b0: Float, b1: Float) {
        val inv = 1f / n
        for (i in 0 until n) {
            val b = b0 + (b1 - b0) * (i + 1) * inv
            val xl = l[i]
            val xr = r[i]
            l[i] = xl + b * (left.process(xl) - xl)
            r[i] = xr + b * (right.process(xr) - xr)
        }
    }
}
