package com.vintagevinyl.dsp

import com.vintagevinyl.dsp.util.Biquad
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.math.tanh

class EngineTests {

    private val fs = T.FS

    // ------------------------------------------------------------------ bypass & transparency

    @Test
    fun masterOff_isBitExactDelayedCopy() {
        val engine = VintageEngine(fs, 7L)
        engine.updateSettings(VintagePresets.DEEP_NOSTALGIA.settings.copy(masterEnabled = false))
        val frames = fs * 2
        val shorts = ShortArray(frames * 2) { ((it * 7919L) % 65536 - 32768).toShort() }
        val input = FloatArray(shorts.size) { Pcm.shortToFloat(shorts[it]) }
        val out = FloatArray(input.size)
        var pos = 0
        val tmp = FloatArray(1000 * 2)
        var allBypass = true
        while (pos < frames) {
            val n = minOf(1000, frames - pos)
            System.arraycopy(input, pos * 2, tmp, 0, n * 2)
            if (!engine.processInterleaved(tmp, n)) allBypass = false
            System.arraycopy(tmp, 0, out, pos * 2, n * 2)
            pos += n
        }
        assertTrue("engine should report pure bypass", allBypass)
        val d = engine.latencyFrames
        for (i in d until frames) {
            for (ch in 0..1) {
                val expected = shorts[(i - d) * 2 + ch]
                val actual = Pcm.floatToShort(out[i * 2 + ch], 0f)
                assertEquals("frame $i ch $ch", expected, actual)
            }
        }
    }

    @Test
    fun allEffectsAtZero_isTransparent() {
        val engine = VintageEngine(fs, 3L)
        engine.updateSettings(T.settings(EffectId.WARMTH to 0f, EffectId.WOW to 0f))
        val x = T.sineStereo(440.0, 0.5, fs)
        val y = T.render(engine, x)
        val d = engine.latencyFrames
        var maxErr = 0.0
        for (i in fs / 2 until x.size / 2) maxErr = max(maxErr, abs(y[2 * i] - x[2 * (i - d)]).toDouble())
        assertTrue("max error $maxErr", maxErr < 1e-5)
    }

    @Test
    fun pcmRoundTripIsExact() {
        for (v in listOf(-32768, -32767, -1, 0, 1, 12345, 32766, 32767)) {
            val s = v.toShort()
            assertEquals(s, Pcm.floatToShort(Pcm.shortToFloat(s)))
        }
        assertEquals(32767.toShort(), Pcm.floatToShort(1.5f))
        assertEquals((-32768).toShort(), Pcm.floatToShort(-1.5f))
    }

    // ------------------------------------------------------------------ switching without clicks

    /** Max |second difference| in [from, to): zero for DC, small for smooth tones, large for clicks. */
    private fun maxSecondDiff(y: FloatArray, from: Int, to: Int): Double {
        var m = 0.0
        for (i in max(1, from) until minOf(to, y.size - 1)) m = max(m, abs(y[i + 1] - 2f * y[i] + y[i - 1]).toDouble())
        return m
    }

    private fun noiseFree(s: VintageSettings) = s
        .withEnabled(EffectId.SURFACE_NOISE, false).withEnabled(EffectId.CRACKLE, false)
        .withEnabled(EffectId.POPS, false).withEnabled(EffectId.HUM, false)

    private fun twoTone(frames: Int): FloatArray {
        val a = T.sineStereo(220.0, 0.22, frames); val b = T.sineStereo(331.0, 0.22, frames)
        return FloatArray(a.size) { a[it] + b[it] }
    }

    @Test
    fun masterToggle_producesNoClicks() {
        val engine = VintageEngine(fs, 11L)
        val s = noiseFree(VintagePresets.OLD_GRAMOPHONE.settings)
        engine.updateSettings(s)
        val frames = fs * 6
        val input = twoTone(frames)
        val out = FloatArray(input.size)
        val toggles = listOf(fs * 2, fs * 3 + 12345, fs * 4 + 777) // off, on, off
        var state = true
        var pos = 0
        val chunk = 480
        val tmp = FloatArray(chunk * 2)
        var next = 0
        while (pos < frames) {
            if (next < toggles.size && pos >= toggles[next]) {
                state = !state; next++
                engine.updateSettings(s.copy(masterEnabled = state))
            }
            val n = minOf(chunk, frames - pos)
            System.arraycopy(input, pos * 2, tmp, 0, n * 2)
            engine.processInterleaved(tmp, n)
            System.arraycopy(tmp, 0, out, pos * 2, n * 2)
            pos += n
        }
        val ch = T.channel(out, 0)
        val baseline = maxSecondDiff(ch, fs / 2, fs * 2 - 1000)
        for (t in toggles) {
            val worst = maxSecondDiff(ch, t - 200, t + fs / 2)
            assertTrue("click at toggle $t: $worst vs baseline $baseline", worst < baseline * 3.0 + 1e-4)
        }
    }

    @Test
    fun presetSwitching_duringPlayback_producesNoClicks() {
        val presets = VintagePresets.ALL.map { noiseFree(it.settings) }
        val engine = VintageEngine(fs, 5L)
        engine.updateSettings(presets[0])
        val seg = fs / 2
        val frames = seg * (presets.size * 2)
        val input = twoTone(frames)
        val out = FloatArray(input.size)
        var pos = 0
        val chunk = 512
        val tmp = FloatArray(chunk * 2)
        var nextSwitch = seg
        var idx = 0
        val switchPoints = ArrayList<Int>()
        while (pos < frames) {
            if (pos >= nextSwitch) {
                idx = (idx + 1) % presets.size
                engine.updateSettings(presets[idx]); switchPoints.add(pos); nextSwitch += seg
            }
            val n = minOf(chunk, frames - pos)
            System.arraycopy(input, pos * 2, tmp, 0, n * 2)
            engine.processInterleaved(tmp, n)
            System.arraycopy(tmp, 0, out, pos * 2, n * 2)
            pos += n
        }
        val ch = T.channel(out, 0)
        // Reference: worst second difference in the settled tail of each segment.
        var baseline = 0.0
        for (p in switchPoints) baseline = max(baseline, maxSecondDiff(ch, p - seg / 3, p - 50))
        for (p in switchPoints) {
            val worst = maxSecondDiff(ch, p - 20, p + seg / 3)
            assertTrue("click at preset switch $p: $worst vs baseline $baseline", worst < baseline * 3.0 + 1e-4)
        }
    }

    @Test
    fun everyEffectCanBeToggledLive_withoutClicks() {
        for (id in EffectId.values()) {
            if (id == EffectId.SURFACE_NOISE || id == EffectId.CRACKLE || id == EffectId.POPS || id == EffectId.HUM) continue
            val on = T.settings(id to 0.8f, EffectId.WARMTH to 0.3f)
            val engine = VintageEngine(fs, 2L)
            engine.updateSettings(on)
            val frames = fs * 3
            val input = twoTone(frames)
            val out = FloatArray(input.size)
            var pos = 0; val tmp = FloatArray(1024)
            while (pos < frames) {
                if (pos >= fs && pos < fs + 512) engine.updateSettings(on.withEnabled(id, false))
                if (pos >= fs * 2 && pos < fs * 2 + 512) engine.updateSettings(on)
                val n = minOf(512, frames - pos)
                System.arraycopy(input, pos * 2, tmp, 0, n * 2)
                engine.processInterleaved(tmp, n)
                System.arraycopy(tmp, 0, out, pos * 2, n * 2)
                pos += n
            }
            val ch = T.channel(out, 0)
            val base = maxSecondDiff(ch, fs / 2, fs - 200)
            val a = maxSecondDiff(ch, fs - 100, fs + fs / 2)
            val b = maxSecondDiff(ch, fs * 2 - 100, fs * 2 + fs / 2)
            assertTrue("$id off: $a vs $base", a < base * 3.0 + 1e-4)
            assertTrue("$id on: $b vs $base", b < base * 3.0 + 1e-4)
        }
    }

    // ------------------------------------------------------------------ wow & flutter

    @Test
    fun wow_pitchDeviationMatchesTargetAndRotationRate() {
        val engine = VintageEngine(fs, 21L)
        engine.updateSettings(T.settings(EffectId.WOW to 1f, speed = TurntableSpeed.RPM_33))
        val seconds = 24
        val y = T.channel(T.render(engine, T.sineStereo(1000.0, 0.5, fs * seconds)), 0)
        val dev = T.pitchDeviation(y, 1000.0)
        val settled = dev.copyOfRange(fs * 2, dev.size - fs / 10)
        val peak = settled.maxOf { abs(it) }
        // Configured peak is 1.2 %; measured peak is a bit lower because the random drift
        // component and the harmonic add in quadrature rather than at their maxima.
        assertTrue("peak wow deviation $peak", peak in 0.004..0.016)

        // The dominant modulation frequency must be the platter rotation rate (0.556 Hz).
        val n = 1 shl 20
        val d = DoubleArray(n)
        // decimate to 100 Hz for a fine-resolution FFT
        val dec = settled.indices.step(480).map { settled[it] }
        val m = 2048
        val re = DoubleArray(m) { if (it < dec.size) dec[it] - dec.average() else 0.0 }
        val im = DoubleArray(m)
        T.fft(re, im)
        var best = 1; var bestP = 0.0
        for (k in 1 until m / 2) {
            val hz = k * 100.0 / m
            if (hz in 0.25..3.0) { val p = re[k] * re[k] + im[k] * im[k]; if (p > bestP) { bestP = p; best = k } }
        }
        val hz = best * 100.0 / m
        assertTrue("dominant wow frequency $hz Hz", hz in 0.4..0.75)
        assertTrue(d.size == n)
    }

    @Test
    fun flutter_isFasterAndSmallerThanWow() {
        val e1 = VintageEngine(fs, 31L); e1.updateSettings(T.settings(EffectId.FLUTTER to 1f))
        val y = T.channel(T.render(e1, T.sineStereo(1000.0, 0.5, fs * 8)), 0)
        val dev = T.pitchDeviation(y, 1000.0).copyOfRange(fs, fs * 8 - fs / 10)
        val peak = dev.maxOf { abs(it) }
        assertTrue("flutter peak $peak", peak in 0.0012..0.007)
        // Spectral centroid of the deviation signal should sit in the flutter band (> 3 Hz).
        val dec = dev.indices.step(240).map { dev[it] }.toDoubleArray() // 200 Hz
        val m = 1024
        val re = DoubleArray(m) { if (it < dec.size) dec[it] - dec.average() else 0.0 }
        val im = DoubleArray(m)
        T.fft(re, im)
        var num = 0.0; var den = 0.0
        for (k in 1 until m / 2) { val p = re[k] * re[k] + im[k] * im[k]; num += p * (k * 200.0 / m); den += p }
        assertTrue("flutter centroid ${num / den} Hz", num / den > 3.0)
    }

    @Test
    fun noWowOrFlutter_meansNoPitchModulation() {
        val engine = VintageEngine(fs, 1L)
        engine.updateSettings(T.settings(EffectId.WARMTH to 0.5f))
        val y = T.channel(T.render(engine, T.sineStereo(1000.0, 0.5, fs * 4)), 0)
        val dev = T.pitchDeviation(y, 1000.0).copyOfRange(fs, fs * 4 - fs / 10)
        assertTrue("deviation ${dev.maxOf { abs(it) }}", dev.maxOf { abs(it) } < 2e-4)
    }

    // ------------------------------------------------------------------ surface noise

    @Test
    fun hiss_levelMatchesRecordConditionAndSlider() {
        fun rmsDb(amount: Float, cond: RecordCondition): Double {
            val engine = VintageEngine(fs, 41L)
            engine.updateSettings(T.settings(EffectId.SURFACE_NOISE to amount, condition = cond))
            val y = T.channel(T.render(engine, T.silence(fs * 8)), 0)
            return T.db(T.rms(y, fs, y.size))
        }
        assertEquals(-64.0, rmsDb(0.5f, RecordCondition.GOOD), 2.0)
        assertEquals(-58.0, rmsDb(0.5f, RecordCondition.DUSTY), 2.0)
        assertEquals(-46.0, rmsDb(0.5f, RecordCondition.SHELLAC_78), 2.0)
        assertEquals(6.0, rmsDb(1.0f, RecordCondition.GOOD) - rmsDb(0.5f, RecordCondition.GOOD), 1.5)
    }

    @Test
    fun hiss_isHighFrequencyNoise_notRumble() {
        val engine = VintageEngine(fs, 42L)
        engine.updateSettings(T.settings(EffectId.SURFACE_NOISE to 0.5f, condition = RecordCondition.GOOD))
        val y = T.channel(T.render(engine, T.silence(fs * 4)), 0)
        val p = T.powerSpectrum(y, fs, 16384)
        val binHz = fs / 16384.0
        var low = 0.0; var total = 0.0
        for (k in 1 until p.size) { total += p[k]; if (k * binHz < 300) low += p[k] }
        assertTrue("fraction below 300 Hz: ${low / total}", low / total < 0.03)
    }

    @Test
    fun silenceInSilenceOut_whenNoiseEffectsOff() {
        val engine = VintageEngine(fs, 43L)
        engine.updateSettings(T.settings(EffectId.WARMTH to 0.8f, EffectId.SATURATION to 0.8f, EffectId.DYNAMICS to 0.5f))
        val y = T.render(engine, T.silence(fs))
        assertTrue(T.peak(y) < 1e-9f)
    }

    @Test
    fun crackle_isIrregularClusteredAndNotALoop() {
        val engine = VintageEngine(fs, 51L)
        engine.updateSettings(T.settings(EffectId.CRACKLE to 0.5f, condition = RecordCondition.DUSTY))
        val seconds = 120
        val y = T.channel(T.render(engine, T.silence(fs * seconds)), 0)
        val onsets = T.eventOnsets(y, 0.003f /* -50 dBFS */, fs / 100)
        val rate = onsets.size / seconds.toDouble()
        // Nominal 24/s; ~70 % of the log-uniform amplitude range is above the threshold and
        // close events merge, so ~10-20 detectable events per second are expected.
        assertTrue("detected click rate $rate/s", rate in 8.0..24.0)

        val gaps = DoubleArray(onsets.size - 1) { (onsets[it + 1] - onsets[it]).toDouble() / fs }
        val mean = gaps.average()
        val sd = sqrt(gaps.sumOf { (it - mean) * (it - mean) } / gaps.size)
        assertTrue("inter-click CV ${sd / mean} (Poisson = 1, clustered > 1)", sd / mean > 0.8)

        // No loop: the 1 ms envelope must not correlate with itself at any lag >= 0.5 s.
        val ms = fs / 1000
        val env = DoubleArray(y.size / ms) { k -> var m = 0f; for (i in 0 until ms) m = max(m, abs(y[k * ms + i])); m.toDouble() }
        val ac = T.maxAutocorr(env, 500, 60000)
        assertTrue("envelope autocorrelation at lag >= 0.5 s: $ac", ac < 0.1)
    }

    @Test
    fun crackle_differsBetweenSeeds_andIsReproducibleForOneSeed() {
        fun run(seed: Long): FloatArray {
            val e = VintageEngine(fs, seed)
            e.updateSettings(T.settings(EffectId.CRACKLE to 0.5f, EffectId.SURFACE_NOISE to 0.5f, condition = RecordCondition.DUSTY))
            return T.render(e, T.silence(fs * 3))
        }
        val a = run(1); val a2 = run(1); val b = run(2)
        assertTrue(a.contentEquals(a2))
        assertFalse(a.contentEquals(b))
    }

    @Test
    fun crackleDensityScalesWithSliderAndCondition() {
        fun clicksPerSec(amount: Float, cond: RecordCondition): Double {
            val e = VintageEngine(fs, 52L)
            e.updateSettings(T.settings(EffectId.CRACKLE to amount, condition = cond))
            val y = T.channel(T.render(e, T.silence(fs * 40)), 0)
            return T.eventOnsets(y, 0.003f, fs / 100).size / 40.0
        }
        assertTrue(clicksPerSec(0f, RecordCondition.DUSTY) == 0.0)
        assertTrue(clicksPerSec(0.25f, RecordCondition.DUSTY) < clicksPerSec(0.5f, RecordCondition.DUSTY))
        assertTrue(clicksPerSec(0.5f, RecordCondition.DUSTY) < clicksPerSec(1f, RecordCondition.DUSTY))
        assertTrue(clicksPerSec(0.5f, RecordCondition.MINT) < clicksPerSec(0.5f, RecordCondition.WORN))
    }

    @Test
    fun pops_areRareAndLowFrequency_andScratchesRepeatEveryRevolution() {
        val e = VintageEngine(fs, 61L)
        e.updateSettings(T.settings(EffectId.POPS to 0.5f, condition = RecordCondition.WORN))
        val seconds = 90
        val y = T.channel(T.render(e, T.silence(fs * seconds)), 0)
        val onsets = T.eventOnsets(y, 0.003f, fs / 5)
        val rate = onsets.size / seconds.toDouble()
        assertTrue("pop+scratch rate $rate/s", rate in 0.8..3.5)

        // Low-frequency energy: pops carry a 55-140 Hz thump that ordinary clicks do not.
        val p = T.powerSpectrum(y, fs * 5, 1 shl 18)
        var low = 0.0; var total = 0.0
        val binHz = fs / (1 shl 18).toDouble()
        for (k in 1 until p.size) { total += p[k]; if (k * binHz < 200) low += p[k] }
        assertTrue("low-frequency energy share ${low / total}", low / total > 0.3)

        // Scratches: onsets modulo the revolution period must cluster at fixed angles.
        val period = fs / TurntableSpeed.RPM_33.revolutionHz
        val bins = IntArray(100)
        val onsetsFine = T.eventOnsets(y, 0.003f, fs / 50)
        for (o in onsetsFine) bins[(((o % period) / period) * 100).toInt().coerceIn(0, 99)]++
        val revs = (seconds * TurntableSpeed.RPM_33.revolutionHz).toInt()
        val top = bins.sortedDescending()
        assertTrue("top phase bins ${top.take(3)} of $revs revolutions", top[0] >= revs * 0.8 && top[1] >= revs * 0.8)
    }

    // ------------------------------------------------------------------ tone & stereo

    @Test
    fun eqProfiles_areAudiblyDistinct() {
        fun g(p: EqProfile, f: Double) = T.gainDbAt(T.settings(EffectId.EQ_PROFILE to 1f, eq = p), f)
        // Radio: strongly band-limited around ~300 Hz .. 4.8 kHz.
        assertTrue(g(EqProfile.VINTAGE_RADIO, 60.0) < g(EqProfile.VINTAGE_RADIO, 1100.0) - 15)
        assertTrue(g(EqProfile.VINTAGE_RADIO, 12000.0) < g(EqProfile.VINTAGE_RADIO, 1100.0) - 15)
        // Gramophone: horn peak near 1 kHz.
        assertTrue(g(EqProfile.ANTIQUE_GRAMOPHONE, 950.0) > g(EqProfile.ANTIQUE_GRAMOPHONE, 5000.0) + 12)
        // Warm vinyl: bass lift, treble softened.
        assertTrue(g(EqProfile.WARM_VINYL, 100.0) > g(EqProfile.WARM_VINYL, 1000.0) + 1.5)
        assertTrue(g(EqProfile.WARM_VINYL, 12000.0) < g(EqProfile.WARM_VINYL, 1000.0) - 3)
        // 1970s: deep bass extension (a small 1950s cabinet cannot reproduce 40 Hz; it does lift
        // 90-150 Hz, so the difference is in the extension, not at 90 Hz) and a lifted top.
        assertTrue(g(EqProfile.ANALOG_1970S, 40.0) > g(EqProfile.RECORD_PLAYER_1950S, 40.0) + 6)
        assertTrue(g(EqProfile.ANALOG_1970S, 12000.0) > g(EqProfile.RECORD_PLAYER_1950S, 12000.0) + 6)
        // Amount 0 is flat.
        assertEquals(0.0, T.gainDbAt(T.settings(EffectId.EQ_PROFILE to 0f, eq = EqProfile.VINTAGE_RADIO), 100.0), 0.05)
    }

    @Test
    fun rolloffAndWarmth_shapeTheSpectrum() {
        val flat = T.gainDbAt(T.settings(EffectId.ROLLOFF to 0f), 10000.0)
        val half = T.gainDbAt(T.settings(EffectId.ROLLOFF to 0.5f), 10000.0)
        val full = T.gainDbAt(T.settings(EffectId.ROLLOFF to 1f), 10000.0)
        assertEquals(0.0, flat, 0.1)
        assertTrue("half $half full $full", half < -6 && full < half - 6)
        assertEquals(0.0, T.gainDbAt(T.settings(EffectId.ROLLOFF to 1f), 200.0), 0.5)
        val bass = T.gainDbAt(T.settings(EffectId.WARMTH to 1f), 60.0)
        val treble = T.gainDbAt(T.settings(EffectId.WARMTH to 1f), 12000.0)
        assertTrue("bass +$bass treble $treble", bass > 3.5 && treble < -2.0)
    }

    @Test
    fun speakerColoration_bandLimits() {
        val s = T.settings(EffectId.SPEAKER to 1f)
        assertTrue(T.gainDbAt(s, 60.0) < T.gainDbAt(s, 1500.0) - 15)
        assertTrue(T.gainDbAt(s, 12000.0) < T.gainDbAt(s, 1500.0) - 12)
    }

    @Test
    fun stereoModes_behaveAsAdvertised() {
        val frames = fs * 3
        val rng = java.util.Random(9)
        val x = FloatArray(frames * 2) { (rng.nextGaussian() * 0.1).toFloat() }
        fun sideMidRatio(mode: StereoMode): Double {
            val e = VintageEngine(fs, 1L)
            e.updateSettings(T.settings(EffectId.WARMTH to 0f, stereo = mode))
            val y = T.render(e, x)
            var sSum = 0.0; var mSum = 0.0
            for (i in fs * 2 until frames) {
                val l = y[2 * i]; val r = y[2 * i + 1]
                sSum += (l - r) * (l - r) / 4.0; mSum += (l + r) * (l + r) / 4.0
            }
            return sqrt(sSum / mSum)
        }
        val full = sideMidRatio(StereoMode.FULL)
        val vintage = sideMidRatio(StereoMode.VINTAGE)
        val narrow = sideMidRatio(StereoMode.NARROW)
        val mono = sideMidRatio(StereoMode.MONO)
        assertTrue("full $full vintage $vintage narrow $narrow mono $mono", full > vintage && vintage > narrow && narrow > mono)
        assertTrue(mono < 1e-5)

        // Mono output channels are identical sample for sample.
        val e = VintageEngine(fs, 1L)
        e.updateSettings(T.settings(stereo = StereoMode.MONO))
        val y = T.render(e, x)
        var maxDiff = 0f
        for (i in fs * 2 until frames) maxDiff = max(maxDiff, abs(y[2 * i] - y[2 * i + 1]))
        assertTrue(maxDiff < 1e-6f)
    }

    @Test
    fun vintageStereo_foldsBassToMono() {
        val frames = fs * 3
        fun sideLevelDb(f: Double, mode: StereoMode): Double {
            val e = VintageEngine(fs, 1L); e.updateSettings(T.settings(stereo = mode))
            val l = T.sineStereo(f, 0.2, frames)
            val x = FloatArray(l.size) { if (it % 2 == 0) l[it] else -l[it] } // pure side signal
            val y = T.render(e, x)
            return T.db(T.rms(T.channel(y, 0), fs * 2, frames))
        }
        val bassNarrow = sideLevelDb(50.0, StereoMode.NARROW)
        val bassFull = sideLevelDb(50.0, StereoMode.FULL)
        val midNarrow = sideLevelDb(2000.0, StereoMode.NARROW)
        assertTrue("out-of-phase bass: full $bassFull narrow $bassNarrow", bassNarrow < bassFull - 25)
        assertTrue("2 kHz side survives at reduced width: $midNarrow", midNarrow > bassNarrow + 15)
    }

    // ------------------------------------------------------------------ non-linear colour

    private fun thd(y: FloatArray, f: Double, from: Int, n: Int): Double {
        val p = T.powerSpectrum(y, from, n)
        val k1 = Math.round(f * n / fs).toInt()
        var fund = 0.0; var harm = 0.0
        for (h in 1..8) {
            val k = k1 * h
            if (k >= p.size - 2) break
            val e = p[k - 1] + p[k] + p[k + 1]
            if (h == 1) fund = e else harm += e
        }
        return sqrt(harm / fund)
    }

    @Test
    fun saturation_addsHarmonicsMonotonically() {
        // Coherent sampling: exactly 341 periods in the 16384-point analysis window (~999.2 Hz),
        // so the rectangular-window FFT has no leakage and the THD floor is genuinely ~0.
        val f = 341 * fs / 16384.0
        fun distortion(id: EffectId, a: Float): Double {
            val e = VintageEngine(fs, 1L)
            e.updateSettings(T.settings(id to a))
            val y = T.channel(T.render(e, T.sineStereo(f, 0.7, fs * 3)), 0)
            return thd(y, f, fs * 2, 16384)
        }
        for (id in listOf(EffectId.SATURATION, EffectId.TAPE, EffectId.HARMONICS)) {
            val lo = distortion(id, 0.2f); val hi = distortion(id, 1.0f)
            assertTrue("$id: THD(0.2)=$lo THD(1.0)=$hi", hi > lo * 1.5 && hi > 0.003 && hi < 0.4)
        }
        assertTrue(distortion(EffectId.SATURATION, 0f) < 1e-4)
    }

    @Test
    fun saturation_isAntiAliased() {
        // Coherent 16384-point window: sine on bin 3770 (10.15 kHz at 44.1 kHz).
        val rate = 44100; val n = 16384; val bin = 3770
        val f = bin * rate.toDouble() / n
        val e = VintageEngine(rate, 1L)
        e.updateSettings(T.settings(EffectId.SATURATION to 1f))
        val y = T.channel(T.render(e, T.sineStereo(f, 0.9, rate * 3, rate)), 0)

        // Naive tanh reference with the same drive (g = 3.0, bias = 0.18) at saturation = 1.
        val g = 3.0; val b = 0.18; val tb = tanh(b); val norm = 1.0 / (g * (1 - tb * tb))
        val naive = FloatArray(rate * 3) { i ->
            val x = 0.9 * kotlin.math.sin(2.0 * Math.PI * f * i / rate)
            ((tanh(g * x + b) - tb) * norm).toFloat()
        }
        fun aliasPower(sig: FloatArray): Double {
            val p = T.powerSpectrum(sig, rate * 2, n)
            var alias = 0.0
            for (k in 1 until p.size) {
                val legit = abs(k - bin) <= 2 || abs(k - 2 * bin) <= 2 // 10.15 k fundamental, 20.3 k 2nd harmonic
                if (!legit && k > 30) alias += p[k]
            }
            return alias
        }
        val adaa = aliasPower(y); val ref = aliasPower(naive)
        assertTrue("alias power ADAA ${T.db(sqrt(adaa))} dB vs naive ${T.db(sqrt(ref))} dB", adaa < ref / 4.0) // >= 6 dB lower
    }

    @Test
    fun dynamicsColour_reducesCrestFactor() {
        val e = VintageEngine(fs, 1L)
        e.updateSettings(T.settings(EffectId.DYNAMICS to 1f))
        // Loud/quiet alternating bursts.
        val frames = fs * 6
        val x = FloatArray(frames * 2)
        for (i in 0 until frames) { val a = if ((i / (fs / 2)) % 2 == 0) 0.7 else 0.05; val v = (a * kotlin.math.sin(2 * Math.PI * 300 * i / fs)).toFloat(); x[2 * i] = v; x[2 * i + 1] = v }
        val y = T.channel(T.render(e, x), 0)
        fun seg(sig: FloatArray, s: Int) = T.rms(sig, (s * fs / 2 + fs / 4).coerceAtMost(sig.size - 1), ((s + 1) * fs / 2).coerceAtMost(sig.size))
        val inX = T.channel(x, 0)
        val rangeIn = T.db(seg(inX, 2) / seg(inX, 3))
        val rangeOut = T.db(seg(y, 2) / seg(y, 3))
        assertTrue("loud/quiet range in $rangeIn dB, out $rangeOut dB", rangeOut < rangeIn - 3)
    }

    @Test
    fun humAddsMainsHarmonics() {
        val e = VintageEngine(fs, 1L)
        e.updateSettings(T.settings(EffectId.HUM to 1f).copy(humFrequency = HumFrequency.HZ_60))
        val y = T.channel(T.render(e, T.silence(fs * 4)), 0)
        val n = 1 shl 17
        val p = T.powerSpectrum(y, fs, n)
        fun at(hz: Double): Double { val k = Math.round(hz * n / fs).toInt(); return p[k - 2] + p[k - 1] + p[k] + p[k + 1] + p[k + 2] }
        assertTrue("60 Hz ${at(60.0)} vs 250 Hz ${at(250.0)}", at(60.0) > at(250.0) * 100)
        assertTrue(at(120.0) > at(250.0) * 10)
    }

    // ------------------------------------------------------------------ robustness

    @Test
    fun everyPreset_isFiniteAndNeverExceedsFullScale() {
        val rng = java.util.Random(3)
        for (p in VintagePresets.ALL) {
            val e = VintageEngine(44100, 77L)
            e.updateSettings(p.settings)
            val x = FloatArray(44100 * 2 * 8) { i ->
                val loud = (0.95 * kotlin.math.sin(2 * Math.PI * 100 * (i / 2) / 44100.0)).toFloat()
                (loud * 0.6f + (rng.nextFloat() * 2 - 1) * 0.4f).coerceIn(-1f, 1f)
            }
            val y = T.render(e, x, 777)
            for (v in y) {
                assertTrue("${p.name}: non-finite output", v.isFinite())
                assertTrue("${p.name}: |y|=${abs(v)} > 1", abs(v) <= 1.0f)
            }
        }
    }

    @Test
    fun deterministicForSeed() {
        fun run(seed: Long): FloatArray {
            val e = VintageEngine(fs, seed); e.updateSettings(VintagePresets.DEEP_NOSTALGIA.settings)
            return T.render(e, T.sineStereo(440.0, 0.3, fs))
        }
        assertTrue(run(5).contentEquals(run(5)))
        assertFalse(run(5).contentEquals(run(6)))
    }

    @Test
    fun chunkSizeDoesNotChangeTheSound() {
        fun run(chunk: Int): FloatArray {
            val e = VintageEngine(fs, 9L); e.updateSettings(VintagePresets.DUSTY_VINYL.settings)
            return T.render(e, T.sineStereo(440.0, 0.3, fs), chunk)
        }
        val a = run(64); val b = run(64 * 7)
        assertTrue("block-aligned chunk sizes must render identically", a.contentEquals(b))
        // Arbitrary (non block-aligned) sizes use different control-block boundaries, so they
        // are only required to be statistically equivalent.
        val c = run(1000)
        assertEquals(T.rms(a), T.rms(c), 0.01)
    }

    @Test
    fun sampleRatesAreSupported() {
        for (rate in listOf(22050, 32000, 44100, 48000, 88200, 96000)) {
            val e = VintageEngine(rate, 1L); e.updateSettings(VintagePresets.OLD_GRAMOPHONE.settings)
            val y = T.render(e, T.sineStereo(440.0, 0.3, rate * 2, rate))
            assertTrue("rate $rate", y.all { it.isFinite() && abs(it) <= 1f })
            assertTrue("rate $rate silent?", T.rms(y, rate, y.size) > 0.001)
        }
    }

    @Test
    fun realtimeBudget() {
        val rate = 44100
        val e = VintageEngine(rate, 1L); e.updateSettings(VintagePresets.DEEP_NOSTALGIA.settings)
        val seconds = 30
        val x = T.sineStereo(330.0, 0.3, rate * seconds, rate)
        val tmp = FloatArray(2048 * 2)
        // warm-up (JIT)
        repeat(3) { System.arraycopy(x, 0, tmp, 0, tmp.size); e.processInterleaved(tmp, 2048) }
        val t0 = System.nanoTime()
        var pos = 0
        while (pos < rate * seconds) {
            val n = minOf(2048, rate * seconds - pos)
            System.arraycopy(x, pos * 2, tmp, 0, n * 2)
            e.processInterleaved(tmp, n)
            pos += n
        }
        val ms = (System.nanoTime() - t0) / 1e6
        val factor = seconds * 1000.0 / ms
        println("REALTIME: %.0fx realtime on this JVM (%.2f%% of one core)".format(factor, 100.0 / factor))
        assertTrue("only $factor x realtime", factor > 20)
    }

    // ------------------------------------------------------------------ building blocks

    @Test
    fun biquadDesignsMatchTheirSpecification() {
        val b = Biquad()
        b.peaking(fs, 1000.0, 6.0, 1.0)
        assertEquals(6.0, b.magnitudeDb(fs, 1000.0), 0.01)
        assertEquals(0.0, b.magnitudeDb(fs, 50.0), 0.3)
        b.lowShelf(fs, 200.0, 4.0)
        assertEquals(4.0, b.magnitudeDb(fs, 20.0), 0.1)
        assertEquals(0.0, b.magnitudeDb(fs, 10000.0), 0.1)
        b.highShelf(fs, 5000.0, -3.0)
        assertEquals(-3.0, b.magnitudeDb(fs, 20000.0), 0.15)
        b.lowPass(fs, 1000.0, 0.7071)
        assertEquals(-3.01, b.magnitudeDb(fs, 1000.0), 0.05)
        b.highPass(fs, 1000.0, 0.7071)
        assertEquals(-3.01, b.magnitudeDb(fs, 1000.0), 0.05)
    }

    @Test
    fun settingsModelSemantics() {
        val s = VintageSettings().withAmount(EffectId.WOW, 0.7f)
        assertEquals(0.7f, s.amount(EffectId.WOW), 1e-6f)
        assertEquals(0f, s.withEnabled(EffectId.WOW, false).amount(EffectId.WOW), 0f)
        assertEquals(0.7f, s.withEnabled(EffectId.WOW, false).rawAmount(EffectId.WOW), 1e-6f)
        assertEquals(1f, s.withAmount(EffectId.WOW, 3f).amount(EffectId.WOW), 0f)
        assertEquals(8, VintagePresets.ALL.size)
        assertEquals(VintagePresets.ALL.size, VintagePresets.ALL.map { it.id }.toSet().size)
        assertEquals(
            listOf("Clean Vinyl", "Dusty Vinyl", "Old Gramophone", "Antique Radio", "1950s Vintage", "1960s Warmth", "1970s Analog", "Deep Nostalgia"),
            VintagePresets.ALL.map { it.name },
        )
        assertNotEquals(VintagePresets.CLEAN_VINYL.settings, VintagePresets.DEEP_NOSTALGIA.settings)
    }
}
