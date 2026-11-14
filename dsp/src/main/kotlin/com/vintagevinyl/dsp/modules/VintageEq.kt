package com.vintagevinyl.dsp.modules

import com.vintagevinyl.dsp.EqProfile
import com.vintagevinyl.dsp.util.Smoother
import com.vintagevinyl.dsp.util.StereoBiquad
import com.vintagevinyl.dsp.util.StereoSvf
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.pow

/**
 * Tone shaping: a creative "vintage EQ profile" plus the user's warmth and roll-off controls.
 *
 * Signal path (all linear, all smoothed):
 * profile high-pass -> low shelf -> two peaking bands -> high shelf -> profile low-pass
 * -> warmth low shelf (+bass bloom) -> warmth high shelf (-softened digital harshness)
 * -> roll-off low-pass -> make-up gain.
 *
 * ### Switching without clicks
 * Shelves and peaking bands are exactly transparent at 0 dB, so their gain simply glides to 0.
 * High-pass and low-pass stages are *not* transparent at any corner frequency, so they are
 * faded in and out with a wet/dry blend ([StereoBiquad.processBlend]) and skipped only once the
 * blend has reached exactly zero. Every numeric parameter of the profile (corner frequencies in
 * log2 space, gains in dB, Q) is a smoothed value, so switching profiles during playback glides
 * from one voicing to the next over ~100 ms. The profile amount scales gains and the HP/LP blend.
 */
class VintageEq(private val fs: Int) {

    private companion object {
        const val SUB = 16
    }

    private val nyquistCap = min(20000f, 0.45f * fs)
    private val tau = 0.06f

    private fun log2(x: Float) = (ln(x.toDouble()) / ln(2.0)).toFloat()
    private fun hz(log2Value: Float) = 2.0.pow(log2Value.toDouble())

    private val neutralLp = log2(nyquistCap)

    private val hpF = Smoother(log2(30f), tau)
    private val hpQ = Smoother(.707f, tau)
    private val hpBlend = Smoother(0f, tau)
    private val lpF = Smoother(neutralLp, tau)
    private val lpQ = Smoother(.707f, tau)
    private val lpBlend = Smoother(0f, tau)
    private val lsF = Smoother(log2(150f), tau)
    private val lsG = Smoother(0f, tau)
    private val pk1F = Smoother(log2(1000f), tau)
    private val pk1G = Smoother(0f, tau)
    private val pk1Q = Smoother(1f, tau)
    private val pk2F = Smoother(log2(2500f), tau)
    private val pk2G = Smoother(0f, tau)
    private val pk2Q = Smoother(1f, tau)
    private val hsF = Smoother(log2(8000f), tau)
    private val hsG = Smoother(0f, tau)
    private val makeup = Smoother(0f, tau)
    private val warmLowG = Smoother(0f, tau)
    private val warmHighG = Smoother(0f, tau)
    private val rolloffF = Smoother(neutralLp, tau)
    private val rolloffBlend = Smoother(0f, tau)

    private val all = arrayOf(
        hpF, hpQ, hpBlend, lpF, lpQ, lpBlend, lsF, lsG, pk1F, pk1G, pk1Q, pk2F, pk2G, pk2Q,
        hsF, hsG, makeup, warmLowG, warmHighG, rolloffF, rolloffBlend,
    )

    // Sweeping corner filters are TPT state-variable filters: glitch-free under modulation.
    private val hp = StereoSvf(high = true)
    private val lp = StereoSvf(high = false)
    private val ls = StereoBiquad()
    private val pk1 = StereoBiquad()
    private val pk2 = StereoBiquad()
    private val hs = StereoBiquad()
    private val warmLow = StereoBiquad()
    private val warmHigh = StereoBiquad()
    private val rolloff = StereoSvf(high = false)
    private var dirty = true

    /**
     * The EQ runs its own, finer control rate ([SUB] frames) regardless of the engine block size:
     * biquad coefficients that change in big steps every 64 frames leave audible state-mismatch
     * ticks on loud low-frequency material, whereas 16-frame steps are inaudible. The extra
     * cost is a handful of trig calls per sub-block (about 0.2 % of one core).
     */
    @Suppress("UNUSED_PARAMETER")
    fun configure(blockDurationSec: Float) = all.forEach { it.configure(SUB.toFloat() / fs) }

    private val tl = FloatArray(SUB)
    private val tr = FloatArray(SUB)

    fun reset() {
        hp.reset(); lp.reset(); rolloff.reset()
        listOf(ls, pk1, pk2, hs, warmLow, warmHigh).forEach { it.reset() }
    }

    /**
     * @param profileAmount 0 = flat, 1 = full voicing
     * @param warmth        0..1, up to +4.5 dB bass bloom and -3 dB treble softening
     * @param rolloffAmount 0..1, high-frequency low-pass sweeping from "off" down to 3 kHz
     */
    fun setTargets(profile: EqProfile, profileAmount: Float, warmth: Float, rolloffAmount: Float) {
        val s = profile.spec
        val pa = profileAmount
        hpF.target = log2(s.hpHz); hpQ.target = s.hpQ; hpBlend.target = pa
        lpF.target = log2(min(s.lpHz, nyquistCap)); lpQ.target = s.lpQ; lpBlend.target = pa
        lsF.target = log2(s.lowShelfHz); lsG.target = s.lowShelfDb * pa
        pk1F.target = log2(s.pk1Hz); pk1G.target = s.pk1Db * pa; pk1Q.target = s.pk1Q
        pk2F.target = log2(s.pk2Hz); pk2G.target = s.pk2Db * pa; pk2Q.target = s.pk2Q
        hsF.target = log2(s.highShelfHz); hsG.target = s.highShelfDb * pa
        makeup.target = s.makeupDb * pa
        warmLowG.target = 4.5f * warmth
        warmHighG.target = -3.0f * warmth
        // The cutoff sweeps 20 kHz -> 3 kHz; the blend fades the filter in over the first 12 %
        // of the slider so that "0" is exactly transparent and there is no step at the start.
        rolloffF.target = neutralLp + (log2(3000f) - neutralLp) * rolloffAmount
        rolloffBlend.target = min(1f, rolloffAmount * 8f)
    }

    private fun refreshCoefficients() {
        hp.design(fs, hz(hpF.current), hpQ.current.toDouble())
        lp.design(fs, hz(lpF.current), lpQ.current.toDouble())
        ls.design { lowShelf(fs, hz(lsF.current), lsG.current.toDouble()) }
        pk1.design { peaking(fs, hz(pk1F.current), pk1G.current.toDouble(), pk1Q.current.toDouble()) }
        pk2.design { peaking(fs, hz(pk2F.current), pk2G.current.toDouble(), pk2Q.current.toDouble()) }
        hs.design { highShelf(fs, hz(hsF.current), hsG.current.toDouble()) }
        warmLow.design { lowShelf(fs, 180.0, warmLowG.current.toDouble()) }
        warmHigh.design { highShelf(fs, 5500.0, warmHighG.current.toDouble()) }
        rolloff.design(fs, hz(rolloffF.current), 0.7071)
    }

    private fun blended(stage: StereoSvf, blend: Smoother, l: FloatArray, r: FloatArray, n: Int) {
        if (blend.previous == 0f && blend.current == 0f) {
            stage.reset() // keep the idle filter's memory clean for the next fade-in
        } else {
            stage.processBlend(l, r, n, blend.previous, blend.current)
        }
    }

    /** Shelf / peak stages are transparent at 0 dB. Below 0.002 dB the residual is < -100 dBFS. */
    private fun gained(stage: StereoBiquad, gain: Smoother, l: FloatArray, r: FloatArray, n: Int) {
        if (abs(gain.current) > 0.002f || abs(gain.previous) > 0.002f) stage.process(l, r, n) else stage.reset()
    }

    fun process(l: FloatArray, r: FloatArray, n: Int) {
        var off = 0
        while (off < n) {
            val m = min(SUB, n - off)
            System.arraycopy(l, off, tl, 0, m)
            System.arraycopy(r, off, tr, 0, m)
            processSub(tl, tr, m)
            System.arraycopy(tl, 0, l, off, m)
            System.arraycopy(tr, 0, r, off, m)
            off += m
        }
    }

    private fun processSub(l: FloatArray, r: FloatArray, n: Int) {
        var moved = false
        for (s in all) if (s.step()) moved = true
        if (moved || dirty) {
            refreshCoefficients()
            dirty = false
        }

        blended(hp, hpBlend, l, r, n)
        gained(ls, lsG, l, r, n)
        gained(pk1, pk1G, l, r, n)
        gained(pk2, pk2G, l, r, n)
        gained(hs, hsG, l, r, n)
        blended(lp, lpBlend, l, r, n)
        gained(warmLow, warmLowG, l, r, n)
        gained(warmHigh, warmHighG, l, r, n)
        blended(rolloff, rolloffBlend, l, r, n)

        if (makeup.previous != 0f || makeup.current != 0f) {
            val g0 = 10.0.pow(makeup.previous / 20.0).toFloat()
            val g1 = 10.0.pow(makeup.current / 20.0).toFloat()
            val inv = 1f / n
            for (i in 0 until n) {
                val g = g0 + (g1 - g0) * (i + 1) * inv
                l[i] *= g
                r[i] *= g
            }
        }
    }
}
