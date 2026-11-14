package com.vintagevinyl.dsp.modules

import com.vintagevinyl.dsp.RecordCondition
import com.vintagevinyl.dsp.util.Rng
import com.vintagevinyl.dsp.util.Smoother
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Fully procedural record-surface simulation. Nothing is looped or pre-recorded.
 *
 *  * **Surface noise (hiss)**: white noise, high-passed and low-passed per [RecordCondition],
 *    independently on each channel, with slow irregular level drift plus a once-per-revolution
 *    swell (eccentric groove / off-centre dust).
 *  * **Crackle**: clicks arrive as a Poisson process (random, memoryless gaps) whose rate is
 *    modulated by a two-state Markov chain, so they come in *clusters* like real dust and
 *    scratches. Amplitudes are log-uniform over 24 dB (many small ticks, few loud ones). Each
 *    click is a short damped resonance, i.e. an impulse as heard through the stylus and
 *    cartridge, with one of three timbres: bright tick, hard "dust" snap, or duller crunch.
 *    Clicks are panned mostly to one channel, as a groove-wall defect is.
 *  * **Pops**: rarer, larger events: a sharp tick followed by a low-frequency thump (damped
 *    55-140 Hz resonance, 20-55 ms).
 *  * **Scratches**: worn and shellac records get 2-3 deep scratches that recur once per
 *    revolution at fixed angles (with a little level jitter), the way a real scratch does.
 *    They are driven by the pop amount.
 *
 * Slider mapping: amount 0.5 reproduces the [RecordCondition] numbers, 0 is silence, 1.0 is
 * twice the event rate and +6 dB (hiss) or +3 dB (clicks).
 */
class SurfaceNoise(private val fs: Int, seed: Long) {

    private companion object {
        const val MAX_VOICES = 40
        const val MAX_SCRATCHES = 4
        const val TWO_PI = 2.0 * PI
        val LN10 = ln(10.0)
    }

    private val rng = Rng(seed xor 0x1234ABCDL)

    private val hissAmt = Smoother(0f, 0.05f)
    private val crackleAmt = Smoother(0f, 0.05f)
    private val popAmt = Smoother(0f, 0.05f)
    private val width = Smoother(1f, 0.06f)
    private val revHz = Smoother(0.5556f, 0.3f)
    private var condition = RecordCondition.GOOD
    private var blockDur = 0.0013f

    // --- hiss chain (per channel) ---
    private var aHp = 0.0
    private var aLp = 0.0
    private var hissNorm = 1.0
    private val hpL = doubleArrayOf(0.0, 0.0)
    private val lp1 = doubleArrayOf(0.0, 0.0)
    private val lp2 = doubleArrayOf(0.0, 0.0)
    private var slowNoise = 0.0
    private val aSlow = 1.0 - exp(-TWO_PI * 0.7 / fs)
    private val slowNorm = 0.4 / sqrt((1.0 / 3.0) * aSlow / (2.0 - aSlow))

    // --- event machinery ---
    private var inBurst = false
    private var revPhase = rng.nextDouble()

    private val vActive = BooleanArray(MAX_VOICES)
    private val vInput = DoubleArray(MAX_VOICES)
    private val vC1 = DoubleArray(MAX_VOICES)
    private val vC2 = DoubleArray(MAX_VOICES)
    private val vY1 = DoubleArray(MAX_VOICES)
    private val vY2 = DoubleArray(MAX_VOICES)
    private val vGl = FloatArray(MAX_VOICES)
    private val vGr = FloatArray(MAX_VOICES)
    private val vLife = IntArray(MAX_VOICES)
    private var stealIdx = 0
    private var activeVoices = 0

    private val scratchAngle = DoubleArray(MAX_SCRATCHES)
    private val scratchDb = FloatArray(MAX_SCRATCHES)
    private val scratchHz = FloatArray(MAX_SCRATCHES)
    private val scratchTau = FloatArray(MAX_SCRATCHES)
    private val scratchGl = FloatArray(MAX_SCRATCHES)
    private val scratchGr = FloatArray(MAX_SCRATCHES)
    private var scratchCount = 0

    init {
        applyCondition(condition)
    }

    fun configure(blockDurationSec: Float) {
        blockDur = blockDurationSec
        hissAmt.configure(blockDurationSec); crackleAmt.configure(blockDurationSec)
        popAmt.configure(blockDurationSec); width.configure(blockDurationSec)
        revHz.configure(blockDurationSec)
    }

    fun setTargets(
        c: RecordCondition, hiss: Float, crackle: Float, pops: Float,
        stereoWidth: Float, revolutionHz: Double,
    ) {
        if (c != condition) applyCondition(c)
        hissAmt.target = hiss
        crackleAmt.target = crackle
        popAmt.target = pops
        width.target = stereoWidth
        revHz.target = revolutionHz.toFloat()
    }

    fun reset() {
        hpL.fill(0.0); lp1.fill(0.0); lp2.fill(0.0)
        vActive.fill(false)
        activeVoices = 0
    }

    private fun applyCondition(c: RecordCondition) {
        condition = c
        aHp = 1.0 - exp(-TWO_PI * c.hissHighPassHz / fs)
        aLp = 1.0 - exp(-TWO_PI * c.hissLowPassHz / fs)
        hissNorm = measureHissNorm()
        // Deterministic-per-session scratch layout, regenerated when the record changes.
        scratchCount = c.scratchesPerRevolution.coerceAtMost(MAX_SCRATCHES)
        for (k in 0 until scratchCount) {
            scratchAngle[k] = rng.nextDouble()
            scratchDb[k] = rng.range(-7f, 0f)
            scratchHz[k] = rng.range(2500f, 7500f)
            scratchTau[k] = rng.range(0.0003f, 0.0012f)
            if (rng.nextBoolean()) { scratchGl[k] = 1f; scratchGr[k] = rng.range(0.05f, .35f) }
            else { scratchGr[k] = 1f; scratchGl[k] = rng.range(0.05f, .35f) }
        }
    }

    /** Measures the RMS of the shaped white-noise chain so that "1.0" really is unit RMS. */
    private fun measureHissNorm(): Double {
        val r = Rng(777)
        var hp = 0.0; var l1 = 0.0; var l2 = 0.0; var acc = 0.0
        val warm = 2048; val count = 16384
        for (i in 0 until warm + count) {
            val x = r.nextSigned().toDouble()
            hp += aHp * (x - hp)
            val y = x - hp
            l1 += aLp * (y - l1)
            l2 += aLp * (l1 - l2)
            if (i >= warm) acc += l2 * l2
        }
        return 1.0 / sqrt(acc / count)
    }

    private fun dbToLin(db: Float): Double = 10.0.pow(db / 20.0)

    private fun freeVoice(): Int {
        for (i in 0 until MAX_VOICES) if (!vActive[i]) return i
        stealIdx = (stealIdx + 1) % MAX_VOICES // all busy: steal round-robin
        return stealIdx
    }

    /** Excites a damped resonance (frequency [hz], decay time constant [tau] seconds). */
    private fun startVoice(amp: Double, hz: Double, tau: Double, gl: Float, gr: Float, lifeTaus: Double) {
        val idx = freeVoice()
        val w = TWO_PI * hz / fs
        val rr = exp(-1.0 / (tau * fs))
        vC1[idx] = 2.0 * rr * cos(w)
        vC2[idx] = -rr * rr
        vInput[idx] = amp * sin(w) // makes the first output sample ~= amp
        vY1[idx] = 0.0; vY2[idx] = 0.0
        vGl[idx] = gl; vGr[idx] = gr
        vLife[idx] = (tau * fs * lifeTaus).toInt().coerceAtLeast(8)
        if (!vActive[idx]) activeVoices++
        vActive[idx] = true
    }

    private fun randomPan(out: FloatArray) {
        if (rng.nextFloat() < 0.5f) {
            val main = 1f
            val other = rng.range(0f, 0.3f)
            if (rng.nextBoolean()) { out[0] = main; out[1] = other } else { out[0] = other; out[1] = main }
        } else {
            out[0] = rng.range(0.7f, 1f); out[1] = rng.range(0.7f, 1f)
        }
    }

    private val pan = FloatArray(2)

    private fun spawnCrackle(level: Double) {
        val amp = level * exp(-LN10 * 24.0 / 20.0 * rng.nextFloat()) * (if (rng.nextBoolean()) 1.0 else -1.0)
        randomPan(pan)
        val kind = rng.nextFloat()
        when {
            kind < 0.45f -> startVoice(amp, rng.range(2500f, 8500f).toDouble(), rng.range(0.00015f, 0.0006f).toDouble(), pan[0], pan[1], 8.0)
            kind < 0.75f -> startVoice(amp, fs * rng.range(0.15f, 0.30f).toDouble(), 0.00007, pan[0], pan[1], 10.0)
            else -> startVoice(amp, rng.range(900f, 3500f).toDouble(), rng.range(0.0005f, 0.002f).toDouble(), pan[0], pan[1], 8.0)
        }
    }

    private fun spawnPop(level: Double, hz: Double, tau: Double, gl: Float, gr: Float) {
        startVoice(level, hz, tau, gl, gr, 6.0)                       // thump
        startVoice(level * 0.5, rng.range(3000f, 7000f).toDouble(), 0.0004, gl, gr, 8.0) // onset tick
    }

    /**
     * Fills [outL]/[outR] (overwriting) with this block's noise.
     * @return false if the output is silent (caller may skip mixing it)
     */
    fun process(outL: FloatArray, outR: FloatArray, n: Int): Boolean {
        hissAmt.step(); crackleAmt.step(); popAmt.step(); width.step(); revHz.step()
        val idle = hissAmt.isIdleAtZero && crackleAmt.isIdleAtZero && popAmt.isIdleAtZero && activeVoices == 0
        if (idle) {
            for (i in 0 until n) { outL[i] = 0f; outR[i] = 0f }
            return false
        }

        val c = condition
        val intensityC = 2.0 * crackleAmt.current
        val intensityP = 2.0 * popAmt.current

        // Two-state Markov burst model (mean 1.5 s calm / 0.12 s burst, x5 rate inside a burst).
        if (!inBurst) { if (rng.nextFloat() < blockDur / 1.5f) inBurst = true }
        else if (rng.nextFloat() < blockDur / 0.12f) inBurst = false
        val rateMult = (if (inBurst) 5.0 else 1.0) * 0.77 // 0.77 keeps the long-run mean at nominal

        val pCrackle = c.crackleRatePerSec * intensityC * rateMult / fs
        val pPop = c.popRatePerSec * intensityP / fs
        val crackleLevel = dbToLin(c.cracklePeakDb) * sqrt(intensityC)
        val popLevel = dbToLin(c.popPeakDb) * sqrt(intensityP)

        val hissGain0 = dbToLin(c.hissRmsDb) * 2.0 * hissAmt.previous
        val hissGain1 = dbToLin(c.hissRmsDb) * 2.0 * hissAmt.current
        val rev = revHz.current.toDouble()
        val revInc = rev / fs
        val inv = 1.0 / n

        for (i in 0 until n) {
            val t = (i + 1) * inv

            // ---- spawn events -------------------------------------------------------------
            if (pCrackle > 0.0 && rng.nextFloat() < pCrackle) spawnCrackle(crackleLevel)
            if (pPop > 0.0 && rng.nextFloat() < pPop) {
                randomPan(pan)
                spawnPop(popLevel * exp(-LN10 * 8.0 / 20.0 * rng.nextFloat()),
                    rng.range(55f, 140f).toDouble(), rng.range(0.018f, 0.055f).toDouble(), pan[0], pan[1])
            }
            val prev = revPhase
            revPhase += revInc
            if (revPhase >= 1.0) revPhase -= 1.0
            if (intensityP > 0.0) {
                for (k in 0 until scratchCount) {
                    val a = scratchAngle[k]
                    val crossed = if (revPhase >= prev) (prev < a && a <= revPhase) else (a > prev || a <= revPhase)
                    if (crossed) {
                        val jitter = rng.range(-2f, 2f)
                        val lvl = popLevel * dbToLin(scratchDb[k] + jitter)
                        spawnPop(lvl, 90.0, 0.03, scratchGl[k], scratchGr[k])
                        startVoice(lvl * 0.7, scratchHz[k].toDouble(), scratchTau[k].toDouble(), scratchGl[k], scratchGr[k], 8.0)
                    }
                }
            }

            // ---- hiss ---------------------------------------------------------------------
            val gainNow = hissGain0 + (hissGain1 - hissGain0) * t
            slowNoise += aSlow * (rng.nextSigned() - slowNoise)
            val env = 1.0 + 0.18 * slowNoise * slowNorm + 0.10 * sin(TWO_PI * revPhase)
            val g = gainNow * hissNorm * env
            var nl = 0.0
            var nr = 0.0
            for (ch in 0..1) {
                val x = rng.nextSigned().toDouble()
                hpL[ch] += aHp * (x - hpL[ch])
                val y = x - hpL[ch]
                lp1[ch] += aLp * (y - lp1[ch])
                lp2[ch] += aLp * (lp1[ch] - lp2[ch])
                if (ch == 0) nl = lp2[ch] * g else nr = lp2[ch] * g
            }

            // ---- sum click voices ---------------------------------------------------------
            if (activeVoices > 0) {
                for (v in 0 until MAX_VOICES) {
                    if (!vActive[v]) continue
                    val x = vInput[v]
                    vInput[v] = 0.0
                    val y = x + vC1[v] * vY1[v] + vC2[v] * vY2[v]
                    vY2[v] = vY1[v]
                    vY1[v] = y
                    nl += y * vGl[v]
                    nr += y * vGr[v]
                    if (--vLife[v] <= 0) {
                        vActive[v] = false
                        activeVoices--
                    }
                }
            }
            outL[i] = nl.toFloat()
            outR[i] = nr.toFloat()
        }

        // Mono records have mono surface noise: apply the stereo width to the noise as well.
        val w0 = width.previous
        val w1 = width.current
        if (!(w0 == 1f && w1 == 1f)) {
            for (i in 0 until n) {
                val w = w0 + (w1 - w0) * ((i + 1) * inv.toFloat())
                val m = (outL[i] + outR[i]) * 0.5f
                val s = (outL[i] - outR[i]) * 0.5f * w
                outL[i] = m + s
                outR[i] = m - s
            }
        }
        return true
    }
}
