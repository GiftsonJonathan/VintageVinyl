package com.vintagevinyl.dsp.modules

import com.vintagevinyl.dsp.util.Rng
import com.vintagevinyl.dsp.util.Smoother
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Mechanical hum and motor rumble.
 *
 *  * Hum: mains frequency (50 or 60 Hz) with 2nd and 3rd harmonics and a slow amplitude drift
 *    (earth-loop buzz from an old amplifier or a poorly shielded cartridge lead).
 *  * Rumble: random noise low-passed at ~30 Hz (platter bearing and motor vibration).
 *
 * Level at hum = 1.0 is about -40 dBFS (clearly audible), at 0.5 about -56 dBFS (only in quiet
 * passages / on headphones). Output is mono and added to both channels.
 */
class HumRumble(private val fs: Int, seed: Long) {

    private val amount = Smoother(0f, 0.08f)
    private val mainsHz = Smoother(50f, 0.2f)
    private val rng = Rng(seed xor 0x7A11AB1EL)
    private var ph = 0.0
    private var phAm = 0.0
    private var rumbleState = 0.0
    private val aRumble = 1.0 - exp(-2.0 * PI * 30.0 / fs)
    private val rumbleNorm = 1.0 / sqrt((1.0 / 3.0) * aRumble / (2.0 - aRumble))

    fun configure(blockDurationSec: Float) {
        amount.configure(blockDurationSec)
        mainsHz.configure(blockDurationSec)
    }

    fun setTargets(a: Float, hz: Double) {
        amount.target = a
        mainsHz.target = hz.toFloat()
    }

    fun reset() {
        rumbleState = 0.0
    }

    /** Adds hum + rumble to [l] and [r]. */
    fun process(l: FloatArray, r: FloatArray, n: Int) {
        amount.step()
        mainsHz.step()
        val a0 = amount.previous
        val a1 = amount.current
        if (a0 == 0f && a1 == 0f) return

        fun humGain(a: Float) = if (a <= 0f) 0.0 else 10.0.pow((-72.0 + 32.0 * a) / 20.0)
        fun rumbleGain(a: Float) = if (a <= 0f) 0.0 else 10.0.pow((-66.0 + 22.0 * a) / 20.0)
        val h0 = humGain(a0)
        val h1 = humGain(a1)
        val u0 = rumbleGain(a0)
        val u1 = rumbleGain(a1)
        val f = mainsHz.current.toDouble()
        val inv = 1.0 / n
        val twoPi = 2.0 * PI

        for (i in 0 until n) {
            val t = (i + 1) * inv
            val hg = h0 + (h1 - h0) * t
            val ug = u0 + (u1 - u0) * t

            ph += f / fs; if (ph >= 1.0) ph -= 1.0
            phAm += 0.37 / fs; if (phAm >= 1.0) phAm -= 1.0
            val drift = 1.0 + 0.12 * sin(twoPi * phAm)
            val hum = (sin(twoPi * ph) + 0.55 * sin(twoPi * 2.0 * ph + 0.7) +
                0.30 * sin(twoPi * 3.0 * ph + 1.9)) * drift

            rumbleState += aRumble * (rng.nextSigned() - rumbleState)
            val rumble = rumbleState * rumbleNorm

            val v = (hum * hg + rumble * ug).toFloat()
            l[i] += v
            r[i] += v
        }
    }
}
