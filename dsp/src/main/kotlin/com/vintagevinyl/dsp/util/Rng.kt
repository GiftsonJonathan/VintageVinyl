package com.vintagevinyl.dsp.util

/**
 * Tiny allocation-free pseudo random generator (SplitMix64 seeding + xorshift64).
 *
 * Used on the audio thread, where `java.util.Random` (synchronised, allocating helpers) is not
 * welcome. Deterministic for a given seed, which is what makes the DSP unit tests reproducible.
 */
class Rng(seed: Long) {
    private var s: Long

    init {
        // SplitMix64 scramble so that small seeds (1, 2, 3 ...) give decorrelated streams.
        var z = seed + -7046029254386353131L              // 0x9E3779B97F4A7C15
        z = (z xor (z ushr 30)) * -4658895280553007687L   // 0xBF58476D1CE4E5B9
        z = (z xor (z ushr 27)) * -7723592293110705685L   // 0x94D049BB133111EB
        z = z xor (z ushr 31)
        s = if (z == 0L) -7046029254386353131L else z
    }

    fun nextLong(): Long {
        var x = s
        x = x xor (x shl 13)
        x = x xor (x ushr 7)
        x = x xor (x shl 17)
        s = x
        return x
    }

    /** Uniform in [0, 1). Uses the high bits, which are the best-distributed ones. */
    fun nextFloat(): Float = (nextLong() ushr 40).toInt() * (1.0f / 16777216f)

    /** Uniform in [-1, 1). */
    fun nextSigned(): Float = nextFloat() * 2f - 1f

    fun nextDouble(): Double = (nextLong() ushr 11).toDouble() * (1.0 / 9007199254740992.0)

    /** Uniform in [lo, hi). */
    fun range(lo: Float, hi: Float): Float = lo + (hi - lo) * nextFloat()

    fun nextBoolean(): Boolean = nextLong() < 0
}
