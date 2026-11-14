package com.vintagevinyl.dsp

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/** Shared helpers for the DSP tests: signal generators, measurements and a small FFT. */
object T {
    const val FS = 48000

    fun settings(
        vararg effects: Pair<EffectId, Float>,
        stereo: StereoMode = StereoMode.FULL,
        condition: RecordCondition = RecordCondition.GOOD,
        eq: EqProfile = EqProfile.WARM_VINYL,
        speed: TurntableSpeed = TurntableSpeed.RPM_33,
        master: Boolean = true,
    ) = VintageSettings(
        masterEnabled = master,
        amounts = effects.toMap(),
        enabled = effects.map { it.first }.toSet(),
        eqProfile = eq, stereoMode = stereo, recordCondition = condition, turntableSpeed = speed,
    )

    fun sineStereo(freq: Double, amp: Double, frames: Int, fs: Int = FS): FloatArray {
        val out = FloatArray(frames * 2)
        for (i in 0 until frames) {
            val v = (amp * sin(2.0 * PI * freq * i / fs)).toFloat()
            out[2 * i] = v; out[2 * i + 1] = v
        }
        return out
    }

    fun silence(frames: Int) = FloatArray(frames * 2)

    fun render(engine: VintageEngine, input: FloatArray, chunkFrames: Int = 1024): FloatArray {
        val out = input.copyOf()
        val frames = out.size / 2
        val tmp = FloatArray(chunkFrames * 2)
        var pos = 0
        while (pos < frames) {
            val n = minOf(chunkFrames, frames - pos)
            System.arraycopy(out, pos * 2, tmp, 0, n * 2)
            engine.processInterleaved(tmp, n)
            System.arraycopy(tmp, 0, out, pos * 2, n * 2)
            pos += n
        }
        return out
    }

    fun channel(x: FloatArray, ch: Int): FloatArray = FloatArray(x.size / 2) { x[2 * it + ch] }

    fun rms(x: FloatArray, from: Int = 0, to: Int = x.size): Double {
        var acc = 0.0
        for (i in from until to) acc += x[i].toDouble() * x[i]
        return sqrt(acc / max(1, to - from))
    }

    fun db(x: Double) = 20.0 * log10(max(x, 1e-12))

    fun peak(x: FloatArray): Float = x.maxOf { abs(it) }

    /** Gain in dB of a steady sine through the engine (measured over the last second). */
    fun gainDbAt(s: VintageSettings, freq: Double, fs: Int = FS): Double {
        val engine = VintageEngine(fs, 1L)
        engine.updateSettings(s)
        val x = channel(sineStereo(freq, 0.1, fs * 3, fs), 0)
        val y = channel(render(engine, sineStereo(freq, 0.1, fs * 3, fs)), 0)
        return db(rms(y, fs * 2, fs * 3) / rms(x, fs * 2, fs * 3))
    }

    // ---- FFT -------------------------------------------------------------------------------

    fun fft(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) { val tr = re[i]; re[i] = re[j]; re[j] = tr; val ti = im[i]; im[i] = im[j]; im[j] = ti }
        }
        var len = 2
        while (len <= n) {
            val ang = -2.0 * PI / len
            val wr = cos(ang); val wi = sin(ang)
            var i = 0
            while (i < n) {
                var cr = 1.0; var ci = 0.0
                for (k in 0 until len / 2) {
                    val ur = re[i + k]; val ui = im[i + k]
                    val vr = re[i + k + len / 2] * cr - im[i + k + len / 2] * ci
                    val vi = re[i + k + len / 2] * ci + im[i + k + len / 2] * cr
                    re[i + k] = ur + vr; im[i + k] = ui + vi
                    re[i + k + len / 2] = ur - vr; im[i + k + len / 2] = ui - vi
                    val ncr = cr * wr - ci * wi
                    ci = cr * wi + ci * wr; cr = ncr
                }
                i += len
            }
            len = len shl 1
        }
    }

    /** Power spectrum (bins 0..n/2) of a length-n (power of two) window of [x] starting at [from]. */
    fun powerSpectrum(x: FloatArray, from: Int, n: Int): DoubleArray {
        val re = DoubleArray(n) { x[from + it].toDouble() }
        val im = DoubleArray(n)
        fft(re, im)
        return DoubleArray(n / 2 + 1) { re[it] * re[it] + im[it] * im[it] }
    }

    /**
     * Instantaneous fractional frequency deviation of a sine at nominal [f0] Hz (quadrature
     * demodulation, 10 ms smoothing). Result is one value per sample.
     */
    fun pitchDeviation(y: FloatArray, f0: Double, fs: Int = FS): DoubleArray {
        val n = y.size
        val re = DoubleArray(n); val im = DoubleArray(n)
        for (i in 0 until n) {
            val ph = 2.0 * PI * f0 * i / fs
            re[i] = y[i] * cos(ph); im[i] = -y[i] * sin(ph)
        }
        val w = fs / 100
        fun movAvg(a: DoubleArray): DoubleArray {
            val o = DoubleArray(n); var acc = 0.0
            for (i in 0 until n) { acc += a[i]; if (i >= w) acc -= a[i - w]; o[i] = acc / w }
            return o
        }
        val r2 = movAvg(re); val i2 = movAvg(im)
        val phase = DoubleArray(n)
        var prev = atan2(i2[w], r2[w]); var unwrapped = prev
        for (i in w until n) {
            val p = atan2(i2[i], r2[i])
            var d = p - prev
            while (d > PI) d -= 2 * PI
            while (d < -PI) d += 2 * PI
            unwrapped += d; prev = p; phase[i] = unwrapped
        }
        val dev = DoubleArray(n)
        for (i in w + 1 until n) dev[i] = (phase[i] - phase[i - 1]) * fs / (2.0 * PI) / f0
        return movAvg(dev)
    }

    /**
     * Click/event onsets: sample indices where |x| first exceeds [threshold] after at least
     * [quietSamples] of quiet.
     */
    fun eventOnsets(x: FloatArray, threshold: Float, quietSamples: Int): IntArray {
        val out = ArrayList<Int>()
        var lastAbove = -quietSamples - 1
        for (i in x.indices) {
            if (abs(x[i]) > threshold) {
                if (i - lastAbove > quietSamples) out.add(i)
                lastAbove = i
            }
        }
        return out.toIntArray()
    }

    /** Max normalised autocorrelation of [env] over lags in [minLag, maxLag] (FFT based). */
    fun maxAutocorr(env: DoubleArray, minLag: Int, maxLag: Int): Double {
        val mean = env.average()
        var size = 1
        while (size < env.size * 2) size = size shl 1
        val re = DoubleArray(size); val im = DoubleArray(size)
        for (i in env.indices) re[i] = env[i] - mean
        fft(re, im)
        for (i in 0 until size) { re[i] = re[i] * re[i] + im[i] * im[i]; im[i] = 0.0 }
        fft(re, im) // real, symmetric: forward FFT == inverse up to scale
        val zero = re[0]
        var best = -1.0
        for (lag in minLag..minOf(maxLag, env.size - 1)) best = max(best, re[lag] / zero)
        return best
    }
}
