package com.vintagevinyl.dsp.util

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Second-order IIR section (transposed direct form II) with the RBJ "Audio EQ Cookbook"
 * designs. Coefficients and state are Doubles: low-frequency high-pass filters (20-40 Hz at
 * 48 kHz) lose too much precision in single-precision arithmetic.
 */
class Biquad {
    private var b0 = 1.0
    private var b1 = 0.0
    private var b2 = 0.0
    private var a1 = 0.0
    private var a2 = 0.0
    private var z1 = 0.0
    private var z2 = 0.0

    fun reset() {
        z1 = 0.0
        z2 = 0.0
    }

    fun process(x: Float): Float {
        val xd = x.toDouble()
        val y = b0 * xd + z1
        z1 = b1 * xd - a1 * y + z2
        z2 = b2 * xd - a2 * y
        return y.toFloat()
    }

    fun copyCoefficientsFrom(o: Biquad) {
        b0 = o.b0; b1 = o.b1; b2 = o.b2; a1 = o.a1; a2 = o.a2
    }

    fun setIdentity() {
        b0 = 1.0; b1 = 0.0; b2 = 0.0; a1 = 0.0; a2 = 0.0
    }

    private fun set(nb0: Double, nb1: Double, nb2: Double, na0: Double, na1: Double, na2: Double) {
        val inv = 1.0 / na0
        b0 = nb0 * inv; b1 = nb1 * inv; b2 = nb2 * inv; a1 = na1 * inv; a2 = na2 * inv
    }

    private fun clampF(fs: Double, f: Double) = min(max(f, 5.0), 0.49 * fs)

    fun lowPass(fs: Int, f0: Double, q: Double) {
        val w0 = 2.0 * PI * clampF(fs.toDouble(), f0) / fs
        val c = cos(w0)
        val alpha = sin(w0) / (2.0 * max(q, 0.1))
        set((1 - c) / 2, 1 - c, (1 - c) / 2, 1 + alpha, -2 * c, 1 - alpha)
    }

    fun highPass(fs: Int, f0: Double, q: Double) {
        val w0 = 2.0 * PI * clampF(fs.toDouble(), f0) / fs
        val c = cos(w0)
        val alpha = sin(w0) / (2.0 * max(q, 0.1))
        set((1 + c) / 2, -(1 + c), (1 + c) / 2, 1 + alpha, -2 * c, 1 - alpha)
    }

    /** Band-pass with constant 0 dB peak gain. */
    fun bandPass(fs: Int, f0: Double, q: Double) {
        val w0 = 2.0 * PI * clampF(fs.toDouble(), f0) / fs
        val c = cos(w0)
        val alpha = sin(w0) / (2.0 * max(q, 0.1))
        set(alpha, 0.0, -alpha, 1 + alpha, -2 * c, 1 - alpha)
    }

    fun peaking(fs: Int, f0: Double, gainDb: Double, q: Double) {
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * clampF(fs.toDouble(), f0) / fs
        val c = cos(w0)
        val alpha = sin(w0) / (2.0 * max(q, 0.1))
        set(1 + alpha * a, -2 * c, 1 - alpha * a, 1 + alpha / a, -2 * c, 1 - alpha / a)
    }

    fun lowShelf(fs: Int, f0: Double, gainDb: Double, q: Double = 0.7071) {
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * clampF(fs.toDouble(), f0) / fs
        val c = cos(w0)
        val beta = 2.0 * sqrt(a) * (sin(w0) / (2.0 * max(q, 0.1)))
        set(
            a * ((a + 1) - (a - 1) * c + beta),
            2 * a * ((a - 1) - (a + 1) * c),
            a * ((a + 1) - (a - 1) * c - beta),
            (a + 1) + (a - 1) * c + beta,
            -2 * ((a - 1) + (a + 1) * c),
            (a + 1) + (a - 1) * c - beta,
        )
    }

    fun highShelf(fs: Int, f0: Double, gainDb: Double, q: Double = 0.7071) {
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * clampF(fs.toDouble(), f0) / fs
        val c = cos(w0)
        val beta = 2.0 * sqrt(a) * (sin(w0) / (2.0 * max(q, 0.1)))
        set(
            a * ((a + 1) + (a - 1) * c + beta),
            -2 * a * ((a - 1) + (a + 1) * c),
            a * ((a + 1) + (a - 1) * c - beta),
            (a + 1) - (a - 1) * c + beta,
            2 * ((a - 1) - (a + 1) * c),
            (a + 1) - (a - 1) * c - beta,
        )
    }

    /** Magnitude response in dB at [freq] Hz (for tests and documentation plots). */
    fun magnitudeDb(fs: Int, freq: Double): Double {
        val w = 2.0 * PI * freq / fs
        val cw = cos(w); val sw = sin(w)
        val c2 = cos(2 * w); val s2 = sin(2 * w)
        val nr = b0 + b1 * cw + b2 * c2
        val ni = -(b1 * sw + b2 * s2)
        val dr = 1 + a1 * cw + a2 * c2
        val di = -(a1 * sw + a2 * s2)
        val mag = sqrt((nr * nr + ni * ni) / (dr * dr + di * di))
        return 20.0 * kotlin.math.log10(max(mag, 1e-12))
    }
}

/** A pair of identically-tuned [Biquad]s, one per channel. */
class StereoBiquad {
    val left = Biquad()
    val right = Biquad()

    fun reset() { left.reset(); right.reset() }

    inline fun design(block: Biquad.() -> Unit) {
        left.block()
        right.copyCoefficientsFrom(left)
    }

    fun process(l: FloatArray, r: FloatArray, n: Int) {
        for (i in 0 until n) {
            l[i] = left.process(l[i])
            r[i] = right.process(r[i])
        }
    }

    /**
     * Wet/dry processing: `y = x + b * (filter(x) - x)` with `b` ramping linearly from [b0] to
     * [b1] across the block.
     *
     * This is how every stage that is not exactly transparent at its "off" setting (high-pass,
     * low-pass) is faded in and out. A filter that is merely *skipped* when "off" produces a
     * click, because even a 10 Hz high-pass or a 20 kHz low-pass shifts the phase of
     * audible tones by a couple of samples; skipping it makes the signal jump by that shift.
     * Fading the wet amount to exactly 0 avoids the jump, and once `b == 0` the caller can
     * skip the filter (and [reset] it) at zero cost.
     */
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

/** First-order DC blocker (high-pass at roughly [cutoffHz]). */
class DcBlocker(fs: Int, cutoffHz: Double = 7.0) {
    private val r = 1.0 - 2.0 * PI * cutoffHz / fs
    private var x1 = 0.0
    private var y1 = 0.0

    fun reset() { x1 = 0.0; y1 = 0.0 }

    fun process(x: Float): Float {
        val xd = x.toDouble()
        val y = xd - x1 + r * y1
        x1 = xd
        y1 = y
        return y.toFloat()
    }
}
