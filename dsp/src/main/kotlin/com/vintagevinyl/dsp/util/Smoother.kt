package com.vintagevinyl.dsp.util

import kotlin.math.abs
import kotlin.math.exp

/**
 * Control-rate one-pole parameter smoother.
 *
 * The engine renders audio in small blocks. At the start of each block every smoother is
 * advanced once ([step]); modules then either interpolate linearly per sample between
 * [previous] and [current] (gains, depths) or recompute filter coefficients from [current].
 * That is what lets the user flip switches and drag sliders *during playback* without zipper
 * noise or clicks.
 */
class Smoother(initial: Float, private val tauSec: Float) {
    var target: Float = initial
    var current: Float = initial
        private set
    var previous: Float = initial
        private set

    private var alpha = 0.2f

    fun configure(blockDurationSec: Float) {
        alpha = (1.0 - exp(-blockDurationSec.toDouble() / tauSec)).toFloat()
    }

    /** Advances one control block. Returns true if the value moved. */
    fun step(): Boolean {
        previous = current
        if (current == target) return false
        current += (target - current) * alpha
        if (abs(target - current) <= 1e-4f * (abs(target) + 0.01f)) current = target
        return current != previous
    }

    fun snap(value: Float) {
        target = value
        current = value
        previous = value
    }

    /** True when the value is exactly zero and heading nowhere else: the stage can be skipped. */
    val isIdleAtZero: Boolean get() = current == 0f && previous == 0f && target == 0f
}
