@file:OptIn(UnstableApi::class)

package com.vintagevinyl.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.vintagevinyl.dsp.Pcm
import com.vintagevinyl.dsp.VintageEngine
import com.vintagevinyl.dsp.VintageSettings
import java.nio.ByteBuffer

/**
 * Adapter that runs the pure-Kotlin [VintageEngine] inside ExoPlayer's audio pipeline.
 *
 * ExoPlayer decodes every format it supports (MP3, FLAC, WAV, AAC, OGG, Opus ...) to PCM and
 * hands the PCM to the processors in `DefaultAudioSink`, *before* it is written to the
 * `AudioTrack`. Because the effect sits in that chain, it is independent of the file format, it
 * adds no extra thread, and all processing happens inside the player's own audio thread (so
 * there is no extra buffering and no way for it to underrun on its own).
 *
 * Formats
 *  * Input: 16-bit or float PCM, mono or stereo (what virtually all music decodes to).
 *  * Output: always 16-bit stereo, with TPDF dither whenever the engine actually changed the
 *    signal. In pure bypass the samples are passed through bit-exact (mono is duplicated).
 *  * Anything else (surround, 24/32-bit integer PCM): the processor reports itself inactive for
 *    that stream, so the audio plays normally without effects rather than failing.
 *
 * Settings are published from the UI thread through [updateSettings]; the engine picks them up
 * at the start of its next block and smooths every parameter, so nothing clicks.
 */
class VintageAudioProcessor : BaseAudioProcessor() {

    @Volatile private var settings: VintageSettings = VintageSettings.DEFAULT
    private var engine: VintageEngine? = null
    private var inputChannels = 2
    private var inputIsFloat = false
    private var scratch = FloatArray(0)

    /** Thread-safe. Called whenever the user moves a control, also while nothing is playing. */
    fun updateSettings(newSettings: VintageSettings) {
        settings = newSettings
        engine?.updateSettings(newSettings)
    }

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        val encoding = inputAudioFormat.encoding
        val supported = (encoding == C.ENCODING_PCM_16BIT || encoding == C.ENCODING_PCM_FLOAT) &&
            inputAudioFormat.channelCount in 1..2 && inputAudioFormat.sampleRate > 0
        if (!supported) {
            engine = null
            return AudioFormat.NOT_SET // inactive: this stream is left untouched
        }
        inputChannels = inputAudioFormat.channelCount
        inputIsFloat = encoding == C.ENCODING_PCM_FLOAT
        engine = VintageEngine(inputAudioFormat.sampleRate).also { it.updateSettings(settings) }
        return AudioFormat(inputAudioFormat.sampleRate, 2, C.ENCODING_PCM_16BIT)
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val eng = engine ?: return
        val bytesPerFrame = inputChannels * if (inputIsFloat) 4 else 2
        val frames = inputBuffer.remaining() / bytesPerFrame
        if (frames == 0) {
            inputBuffer.position(inputBuffer.limit())
            return
        }
        ensureScratch(frames)

        var p = 0
        for (i in 0 until frames) {
            val l = readSample(inputBuffer)
            val r = if (inputChannels == 2) readSample(inputBuffer) else l
            scratch[p++] = l
            scratch[p++] = r
        }
        emit(eng, frames)
    }

    override fun onQueueEndOfStream() {
        // The engine delays the signal by ~4 ms; push that much silence through so the very
        // end of the track (and the last record crackle) is not cut off.
        val eng = engine ?: return
        val frames = eng.latencyFrames
        ensureScratch(frames)
        java.util.Arrays.fill(scratch, 0, frames * 2, 0f)
        emit(eng, frames)
    }

    /** Processes `scratch[0 until frames*2]` and writes it to the output buffer as 16-bit PCM. */
    private fun emit(eng: VintageEngine, frames: Int) {
        val bypass = eng.processInterleaved(scratch, frames)
        val out = replaceOutputBuffer(frames * 4) // native byte order, reused between calls
        val n = frames * 2
        if (bypass) {
            for (i in 0 until n) out.putShort(Pcm.floatToShort(scratch[i], 0f))
        } else {
            for (i in 0 until n) out.putShort(Pcm.floatToShort(scratch[i], eng.nextDitherLsb()))
        }
        out.flip()
    }

    private fun readSample(b: ByteBuffer): Float =
        if (inputIsFloat) b.getFloat() else Pcm.shortToFloat(b.getShort())

    private fun ensureScratch(frames: Int) {
        if (scratch.size < frames * 2) scratch = FloatArray(frames * 2)
    }

    /** Seek or track change: forget the delay line and filter memory, keep the settings. */
    override fun onFlush() {
        engine?.reset()
    }

    override fun onReset() {
        engine = null
        scratch = FloatArray(0)
    }
}
