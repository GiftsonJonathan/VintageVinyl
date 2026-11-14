package com.vintagevinyl.dsp.modules

import com.vintagevinyl.dsp.util.Rng
import com.vintagevinyl.dsp.util.Smoother
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Wow and flutter as a modulated fractional delay line.
 *
 * A turntable running at a slightly varying speed plays the music slightly faster and slower.
 * Doing that to a *stream* by resampling would change its duration (and desync the player's
 * position). Instead we use the classic vibrato trick: read the audio through a delay whose
 * length changes. If the delay grows by `dD` samples per sample, the signal is replayed at
 * rate `1 - dD`, so the pitch deviation is exactly `-dD`. Total duration is unchanged.
 *
 * The modulation is synthesised **in the pitch domain** (a signal `p[n]`, as a fraction) and
 * integrated into the delay with a slightly leaky integrator: `D[n] = D[n-1](1-leak) - p[n]`.
 * Building it that way, rather than scaling a delay-domain LFO, matters: changing the *depth* of
 * a delay-domain LFO while it is swinging adds a pitch glide proportional to the depth change,
 * which would make every slider move or preset change audibly bend the pitch.
 *
 *  * **Wow** (slow): a component locked to the platter rotation (eccentric spindle hole,
 *    off-centre pressing), its second harmonic, and slow random drift (two-pole low-pass
 *    noise, corner 0.6 Hz).
 *  * **Flutter** (fast): a belt/idler-like tone at 6.3 Hz plus band-limited random jitter
 *    (2.5 to 12 Hz).
 *
 * Noise generators are normalised analytically (frequency-domain variance), so the slider
 * maps to a predictable pitch deviation: at 1.0, wow peaks at about 1.2 % and flutter at about
 * 0.45 %.
 *
 * Fractional reads use 4-point Catmull-Rom (cubic Hermite) interpolation. The delay line also
 * defines the engine's fixed latency ([centerDelay], about 4.2 ms): every path through the
 * engine (including the dry/bypass path) is read from the same line, so the master-switch
 * crossfade stays phase aligned.
 */
class SpeedVariation(private val fs: Int, seed: Long) {

    companion object {
        private const val SIZE = 8192
        private const val MASK = SIZE - 1
        private const val TWO_PI = 2.0 * PI

        /** Peak pitch deviation at wow = 1.0 (1.2 %: a very tired old deck). */
        const val MAX_WOW_PITCH = 0.012

        /** Peak pitch deviation at flutter = 1.0 (0.45 %). */
        const val MAX_FLUTTER_PITCH = 0.0045

        private const val FLUTTER_TONE_HZ = 6.3
        private const val WOW_NOISE_HZ = 0.6
        private const val FLUTTER_HI_HZ = 12.0
        private const val FLUTTER_LO_HZ = 2.5

        // RMS of the random component, as a fraction of the maximum pitch deviation.
        private const val SIGMA_WOW = 0.12
        private const val SIGMA_FLUTTER = 0.15
    }

    /** Fixed latency in frames that every signal passes through. */
    val centerDelay: Int = ceil(0.0042 * fs).toInt()

    private val bufL = FloatArray(SIZE)
    private val bufR = FloatArray(SIZE)
    private var w = 0
    private val rng = Rng(seed xor 0x5DEECE66DL)

    private val wow = Smoother(0f, 0.05f)
    private val flutter = Smoother(0f, 0.05f)
    private val rotationHz = Smoother(0.5556f, 0.3f)

    private var phWow1 = rng.nextDouble()
    private var phWow2 = rng.nextDouble()
    private var phFlutter = rng.nextDouble()

    private val aWow = 1.0 - exp(-TWO_PI * WOW_NOISE_HZ / fs)
    private val aFlHi = 1.0 - exp(-TWO_PI * FLUTTER_HI_HZ / fs)
    private val aFlLo = 1.0 - exp(-TWO_PI * FLUTTER_LO_HZ / fs)
    private var nw1 = 0.0
    private var nw2 = 0.0
    private var nh1 = 0.0
    private var nh2 = 0.0
    private var nl1 = 0.0
    private var nl2 = 0.0
    private val wowNorm = SIGMA_WOW / sqrt(noiseVariance(aWow, null))
    private val flutterNorm = SIGMA_FLUTTER / sqrt(noiseVariance(aFlHi, aFlLo))

    /** Current delay offset from [centerDelay], in samples. */
    private var dev = 0.0

    /**
     * Variance of uniform(-1,1) white noise (1/3) after two cascaded one-poles `a`, minus
     * (optionally) two cascaded one-poles `aLo` driven by the same noise. Computed by numerically
     * integrating |H|^2 over frequency on a log grid.
     */
    private fun noiseVariance(a: Double, aLo: Double?): Double {
        val steps = 20000
        val lnMin = ln(1e-7)
        val lnMax = ln(PI)
        val dLn = (lnMax - lnMin) / steps
        var acc = 0.0
        var prev = 0.0
        for (k in 0..steps) {
            val om = exp(lnMin + dLn * k)
            val f = gainSq(om, a, aLo) * om // d(omega) = omega * d(ln omega)
            if (k > 0) acc += 0.5 * (f + prev) * dLn
            prev = f
        }
        return (1.0 / 3.0) * acc / PI
    }

    private fun gainSq(om: Double, a: Double, aLo: Double?): Double {
        var gr: Double
        var gi: Double
        run {
            val re = 1 - (1 - a) * cos(om)
            val im = (1 - a) * sin(om)
            val den = re * re + im * im
            val hr = a * re / den
            val hi = -a * im / den
            gr = hr * hr - hi * hi
            gi = 2 * hr * hi
        }
        if (aLo != null) {
            val re = 1 - (1 - aLo) * cos(om)
            val im = (1 - aLo) * sin(om)
            val den = re * re + im * im
            val hr = aLo * re / den
            val hi = -aLo * im / den
            gr -= hr * hr - hi * hi
            gi -= 2 * hr * hi
        }
        return gr * gr + gi * gi
    }

    fun configure(blockDurationSec: Float) {
        wow.configure(blockDurationSec)
        flutter.configure(blockDurationSec)
        rotationHz.configure(blockDurationSec)
    }

    fun setTargets(wowAmount: Float, flutterAmount: Float, revolutionHz: Double) {
        wow.target = wowAmount
        flutter.target = flutterAmount
        rotationHz.target = revolutionHz.toFloat()
    }

    /** Clears the audio memory (seek/flush). */
    fun reset() {
        bufL.fill(0f)
        bufR.fill(0f)
        dev = 0.0
    }

    /** Starts the modulation from the centre position (used when the engine leaves bypass). */
    fun restartModulation() {
        dev = 0.0
    }

    /** Idle path: pure fixed delay. Used while the whole engine is bypassed. */
    fun passThrough(l: FloatArray, r: FloatArray, n: Int) {
        for (i in 0 until n) {
            bufL[w] = l[i]
            bufR[w] = r[i]
            val c = (w - centerDelay) and MASK
            l[i] = bufL[c]
            r[i] = bufR[c]
            w = (w + 1) and MASK
        }
    }

    /**
     * Applies wow/flutter in place. If [dryL]/[dryR] are given they receive the unmodulated,
     * latency-aligned input (needed for the master-switch crossfade).
     */
    fun process(l: FloatArray, r: FloatArray, n: Int, dryL: FloatArray?, dryR: FloatArray?) {
        wow.step()
        flutter.step()
        rotationHz.step()
        val ampOff = wow.current == 0f && wow.target == 0f && wow.previous == 0f &&
            flutter.current == 0f && flutter.target == 0f && flutter.previous == 0f
        // After the effect is switched off the delay offset relaxes back to the centre with a
        // 0.4 s time constant (a barely audible ~0.4 % glide) instead of jumping.
        if (ampOff && abs(dev) < 0.01) dev = 0.0
        val idle = ampOff && dev == 0.0
        val relaxing = wow.target == 0f && flutter.target == 0f
        val leak = TWO_PI * (if (relaxing) 0.4 else 0.03) / fs

        val fsD = fs.toDouble()
        val rotHz = rotationHz.current.toDouble().coerceAtLeast(0.2)
        val inv = 1f / n
        val limit = centerDelay - 4.0

        for (i in 0 until n) {
            bufL[w] = l[i]
            bufR[w] = r[i]
            val c = (w - centerDelay) and MASK
            if (dryL != null && dryR != null) {
                dryL[i] = bufL[c]
                dryR[i] = bufR[c]
            }
            if (idle) {
                l[i] = bufL[c]
                r[i] = bufR[c]
            } else {
                val t = (i + 1) * inv
                val wa = (wow.previous + (wow.current - wow.previous) * t).toDouble()
                val fa = (flutter.previous + (flutter.current - flutter.previous) * t).toDouble()

                phWow1 += rotHz / fsD; if (phWow1 >= 1.0) phWow1 -= 1.0
                phWow2 += 2.0 * rotHz / fsD; if (phWow2 >= 1.0) phWow2 -= 1.0
                phFlutter += FLUTTER_TONE_HZ / fsD; if (phFlutter >= 1.0) phFlutter -= 1.0

                val x1 = rng.nextSigned().toDouble()
                val x2 = rng.nextSigned().toDouble()
                nw1 += aWow * (x1 - nw1); nw2 += aWow * (nw1 - nw2)
                nh1 += aFlHi * (x2 - nh1); nh2 += aFlHi * (nh1 - nh2)
                nl1 += aFlLo * (x2 - nl1); nl2 += aFlLo * (nl1 - nl2)

                // Pitch deviation (fraction). sqrt(a) in the depth makes the slider feel even.
                val pw = MAX_WOW_PITCH * wa * sqrt(wa)
                val pf = MAX_FLUTTER_PITCH * fa * sqrt(fa)
                val p = pw * (0.50 * sin(TWO_PI * phWow1) + 0.12 * sin(TWO_PI * phWow2) + nw2 * wowNorm) +
                    pf * (0.40 * sin(TWO_PI * phFlutter) + (nh2 - nl2) * flutterNorm)

                dev = dev * (1.0 - leak) - p
                if (dev > limit) dev = limit else if (dev < -limit) dev = -limit

                val pos = w - (centerDelay + dev)
                val i0 = floor(pos).toInt()
                val frac = (pos - i0).toFloat()
                l[i] = hermite(bufL, i0, frac)
                r[i] = hermite(bufR, i0, frac)
            }
            w = (w + 1) and MASK
        }
    }

    private fun hermite(b: FloatArray, i0: Int, f: Float): Float {
        val y0 = b[(i0 - 1) and MASK]
        val y1 = b[i0 and MASK]
        val y2 = b[(i0 + 1) and MASK]
        val y3 = b[(i0 + 2) and MASK]
        val c1 = 0.5f * (y2 - y0)
        val c2 = y0 - 2.5f * y1 + 2f * y2 - 0.5f * y3
        val c3 = 0.5f * (y3 - y0) + 1.5f * (y1 - y2)
        return ((c3 * f + c2) * f + c1) * f + y1
    }
}
