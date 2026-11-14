package com.vintagevinyl.dsp.modules

import com.vintagevinyl.dsp.util.DcBlocker
import com.vintagevinyl.dsp.util.Smoother
import com.vintagevinyl.dsp.util.StereoBiquad
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.ln1p
import kotlin.math.min
import kotlin.math.tanh

/**
 * Three independent non-linear colour stages:
 *
 *  1. **Saturation**: asymmetric `tanh` soft clipping (valve / transformer-like). The slight
 *     asymmetry adds even harmonics (the "warm" ones).
 *  2. **Tape saturation**: the signal is pre-emphasised in the treble, saturated, then
 *     de-emphasised, so highs compress earlier than lows (tape's "glue"), plus a small
 *     low-frequency "head bump".
 *  3. **Harmonic distortion**: explicit, very low-order 2nd and 3rd harmonic generation.
 *
 * ### Design rules that keep these stages click-free
 *  * **Transparent at zero.** The soft clipper is `tanh(g x) / g` with the drive `g` proportional
 *    to the amount. As the amount goes to 0, `g` goes to 0 and the curve becomes exactly the
 *    identity, so a stage fading in or out never jumps between "linear" and "curved". (A
 *    clipper with a fixed minimum drive, e.g. `g = 1 + a`, is already a 6 % compressor at
 *    `a = 0` and clicks the moment it is engaged.)
 *  * **DC blocking only on the residual.** Even-harmonic generation creates a DC offset that
 *    must be removed, but a DC-blocking high-pass on the *whole* signal shifts the phase of low
 *    tones (a 7 Hz high-pass leads 220 Hz by 2 degrees, a 0.014 step on a 0.44 signal when the
 *    stage engages). So the blocker only sees `f(x) - x`, which is exactly 0 when idle.
 *  * Idle stages reset their memory, so re-engaging them never replays stale state.
 *
 * Waveshaping creates harmonics beyond Nyquist that fold back as harsh aliasing. The `tanh`
 * stages use first-order antiderivative anti-aliasing (ADAA, Parker et al. 2016): instead of
 * evaluating the curve at x[n] we use its *average* between x[n-1] and x[n], computed exactly
 * from the antiderivative. That removes most aliasing at no oversampling cost.
 *
 * ADAA is applied to the **nonlinear residual only**: `f(x) = x + R(x)` where `R` is what is
 * left after subtracting the curve's tangent line. Plain ADAA on `f` itself is a 2-tap average
 * `(x[n] + x[n-1]) / 2` in the linear regime, i.e. a half-sample delay and a treble loss even
 * at amount 0. Both samples of the average are evaluated on the *current* curve (current drive
 * and bias), so per-sample amount ramps cannot create a mismatch between them.
 */
class Saturator(private val fs: Int) {

    private companion object {
        const val SAT_DRIVE = 3.0   // g at saturation = 1.0
        const val TAPE_DRIVE = 2.0  // g at tape = 1.0
        const val SAT_BIAS = 0.18   // asymmetry at saturation = 1.0
        const val COEF_STEP = 16    // frames between tape filter coefficient updates (power of two)
    }

    private val sat = Smoother(0f, 0.05f)
    private val tape = Smoother(0f, 0.05f)
    private val harm = Smoother(0f, 0.05f)

    private val satAdaa = arrayOf(Adaa(), Adaa())
    private val tapeAdaa = arrayOf(Adaa(), Adaa())
    private val satDc = arrayOf(DcBlocker(fs), DcBlocker(fs))
    private val harmDc = arrayOf(DcBlocker(fs), DcBlocker(fs))
    private val tapePre = StereoBiquad()
    private val tapePost = StereoBiquad()
    private val headBump = StereoBiquad()

    fun configure(blockDurationSec: Float) {
        sat.configure(blockDurationSec)
        tape.configure(blockDurationSec)
        harm.configure(blockDurationSec)
    }

    fun setTargets(saturation: Float, tapeAmount: Float, harmonics: Float) {
        sat.target = saturation
        tape.target = tapeAmount
        harm.target = harmonics
    }

    fun reset() {
        resetSat(); resetTape(); resetHarm()
    }

    private fun resetSat() { satAdaa.forEach { it.reset() }; satDc.forEach { it.reset() } }
    private fun resetTape() { tapeAdaa.forEach { it.reset() }; tapePre.reset(); tapePost.reset(); headBump.reset() }
    private fun resetHarm() { harmDc.forEach { it.reset() } }

    fun process(l: FloatArray, r: FloatArray, n: Int) {
        sat.step(); tape.step(); harm.step()
        if (sat.previous == 0f && sat.current == 0f) resetSat() else processSaturation(l, r, n)
        if (tape.previous == 0f && tape.current == 0f) resetTape() else processTape(l, r, n)
        if (harm.previous == 0f && harm.current == 0f) resetHarm() else processHarmonics(l, r, n)
    }

    private fun processSaturation(l: FloatArray, r: FloatArray, n: Int) {
        val a0 = sat.previous
        val a1 = sat.current
        val inv = 1f / n
        for (i in 0 until n) {
            val a = (a0 + (a1 - a0) * (i + 1) * inv).toDouble()
            val g = SAT_DRIVE * a
            val bias = SAT_BIAS * a
            val tb = tanh(bias)
            val sech2 = 1.0 - tb * tb
            val xl = l[i].toDouble()
            val xr = r[i].toDouble()
            // Residual relative to the tangent at the bias point, normalised to unit small-signal gain.
            val rl = satAdaa[0].residual(xl, g, bias, tb, sech2)
            val rr = satAdaa[1].residual(xr, g, bias, tb, sech2)
            l[i] = (xl + satDc[0].process(rl.toFloat())).toFloat()
            r[i] = (xr + satDc[1].process(rr.toFloat())).toFloat()
        }
    }

    private fun processTape(l: FloatArray, r: FloatArray, n: Int) {
        val a0 = tape.previous
        val a1 = tape.current
        val inv = 1f / n
        for (i in 0 until n) {
            val a = (a0 + (a1 - a0) * (i + 1) * inv).toDouble()
            // The pre/post emphasis shelves are time-varying filters whose gains follow the amount.
            // Updating their coefficients every 16 samples (not once per 64-sample block) keeps the
            // state-mismatch ticks of a transposed-form biquad inaudible while the amount glides.
            if (i and (COEF_STEP - 1) == 0) {
                tapePre.design { highShelf(fs, 2500.0, 7.0 * a) }
                tapePost.design { highShelf(fs, 2500.0, -7.0 * a) }
                headBump.design { peaking(fs, 90.0, 1.5 * a, 1.0) }
            }
            val g = TAPE_DRIVE * a
            val vl = tapePre.left.process(l[i]).toDouble()
            val vr = tapePre.right.process(r[i]).toDouble()
            val yl = vl + tapeAdaa[0].residual(vl, g, 0.0, 0.0, 1.0)
            val yr = vr + tapeAdaa[1].residual(vr, g, 0.0, 0.0, 1.0)
            l[i] = headBump.left.process(tapePost.left.process(yl.toFloat()))
            r[i] = headBump.right.process(tapePost.right.process(yr.toFloat()))
        }
    }

    private fun processHarmonics(l: FloatArray, r: FloatArray, n: Int) {
        val a0 = harm.previous
        val a1 = harm.current
        val inv = 1f / n
        for (i in 0 until n) {
            val a = a0 + (a1 - a0) * (i + 1) * inv
            val h2 = 0.030f * a
            val h3 = 0.020f * a
            val xl = l[i]
            val xr = r[i]
            // Only the generated harmonics pass through the DC blocker (x^2 has a DC component).
            l[i] = xl + harmDc[0].process(h2 * xl * xl + h3 * xl * xl * xl)
            r[i] = xr + harmDc[1].process(h2 * xr * xr + h3 * xr * xr * xr)
        }
    }

    /**
     * Anti-aliased nonlinear residual of the soft clipper
     * `f(x) = (tanh(g x + b) - tanh b) / (g sech^2 b)` (small-signal gain 1).
     *
     * `residual = f(x) - x = R(u) / (g sech^2 b)` with `u = g x + b` and
     * `R(u) = tanh u - tanh b - sech^2 b (u - b)`. Its antiderivative is
     * `A(u) = ln cosh u - tanh(b) u - sech^2 b (u - b)^2 / 2`, so the ADAA output is
     * `(A(u[n]) - A(u[n-1])) / (u[n] - u[n-1])`, with a midpoint fallback when the two samples
     * (almost) coincide.
     */
    private class Adaa {
        private var xPrev = 0.0
        private var primed = false

        fun reset() {
            xPrev = 0.0
            primed = false
        }

        fun residual(x: Double, g: Double, b: Double, tb: Double, sech2: Double): Double {
            if (!primed) { xPrev = x; primed = true }
            val u = g * x + b
            val up = g * xPrev + b
            val d = u - up
            val r = if (abs(d) < 1e-5) {
                val m = 0.5 * (u + up)
                tanh(m) - tb - sech2 * (m - b)
            } else {
                (antiderivative(u, b, tb, sech2) - antiderivative(up, b, tb, sech2)) / d
            }
            xPrev = x
            return r / (g * sech2 + 1e-12)
        }

        private fun antiderivative(v: Double, b: Double, tb: Double, sech2: Double): Double =
            lnCosh(v) - tb * v - 0.5 * sech2 * (v - b) * (v - b)

        private fun lnCosh(x: Double): Double {
            val a = abs(x)
            return a + ln1p(exp(-2.0 * a)) - LN2
        }

        private companion object {
            val LN2 = ln(2.0)
        }
    }
}
