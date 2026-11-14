package com.vintagevinyl.dsp

import kotlin.math.roundToInt

/** 16-bit PCM <-> float helpers shared by the Android adapter and the tests. */
object Pcm {
    private const val SCALE = 32768f

    fun shortToFloat(s: Short): Float = s * (1f / SCALE)

    /**
     * Converts back to 16 bit with rounding and clipping. [ditherLsb] (in LSBs, e.g. TPDF in
     * [-1, 1)) is added before rounding; pass 0 for a bit-exact round trip.
     */
    fun floatToShort(x: Float, ditherLsb: Float = 0f): Short {
        val v = (x * SCALE + ditherLsb).roundToInt()
        return (if (v > 32767) 32767 else if (v < -32768) -32768 else v).toShort()
    }
}
