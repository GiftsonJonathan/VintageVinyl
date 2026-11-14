package com.vintagevinyl.dsp.modules

import com.vintagevinyl.dsp.util.Smoother
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

/**
 * "Dynamic range colouring": a gentle, stereo-linked feed-forward compressor with a soft knee
 * and partial make-up gain. It imitates the limited dynamic range of lathe-cut records, old
 * radio chains and simple valve amplifiers: quiet detail comes up a little, peaks are
 * rounded off. It is deliberately mild (ratio 1.0 to 2.8).
 */
class DynamicsColor(fs: Int) {
    private val amount = Smoother(0f, 0.08f)
    private var env = 0.0
    private val attack = 1.0 - exp(-1.0 / (0.008 * fs))
    private val release = 1.0 - exp(-1.0 / (0.16 * fs))

    fun configure(blockDurationSec: Float) = amount.configure(blockDurationSec)

    fun setTarget(a: Float) {
        amount.target = a
    }

    fun reset() {
        env = 0.0
    }

    fun process(l: FloatArray, r: FloatArray, n: Int) {
        amount.step()
        val a0 = amount.previous
        val a1 = amount.current
        if (a0 == 0f && a1 == 0f) {
            env = 0.0
            return
        }
        val inv = 1f / n
        val kneeDb = 8.0
        for (i in 0 until n) {
            val a = (a0 + (a1 - a0) * (i + 1) * inv).toDouble()
            val thresholdDb = -8.0 - 14.0 * a
            val ratio = 1.0 + 1.8 * a
            val makeupDb = 0.4 * -thresholdDb * (1.0 - 1.0 / ratio)

            val level = max(abs(l[i]), abs(r[i])).toDouble()
            env += (if (level > env) attack else release) * (level - env)
            val levelDb = 20.0 * log10(env + 1e-9)
            val over = levelDb - thresholdDb
            val slope = 1.0 / ratio - 1.0
            val reductionDb = when {
                2.0 * over < -kneeDb -> 0.0
                2.0 * abs(over) <= kneeDb -> slope * (over + kneeDb / 2.0).pow(2) / (2.0 * kneeDb)
                else -> slope * over
            }
            val g = 10.0.pow((reductionDb + makeupDb) / 20.0).toFloat()
            l[i] *= g
            r[i] *= g
        }
    }
}
