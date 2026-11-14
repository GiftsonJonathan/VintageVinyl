package com.vintagevinyl.dsp.tools

import com.vintagevinyl.dsp.Pcm
import com.vintagevinyl.dsp.VintageEngine
import com.vintagevinyl.dsp.VintagePresets
import com.vintagevinyl.dsp.VintageSettings
import java.io.File

/**
 * Offline renderer: pushes a WAV file through the very same [VintageEngine] the app uses.
 *
 *     java -cp dsp.jar com.vintagevinyl.dsp.tools.RenderCli in.wav out.wav "Dusty Vinyl" [seed]
 *
 * The preset may be an id (`dusty_vinyl`), a display name, or `dry` for a latency-matched copy.
 * Handy for auditioning presets on a desktop before installing the app.
 */
fun main(args: Array<String>) {
    if (args.size < 3) {
        System.err.println("usage: RenderCli <in.wav> <out.wav> <preset id|name|dry> [seed]")
        System.err.println("presets: " + VintagePresets.ALL.joinToString { it.id })
        return
    }
    val input = WavIO.read(File(args[0]))
    val seed = args.getOrNull(3)?.toLong() ?: 12345L
    val preset = VintagePresets.ALL.firstOrNull { it.id.equals(args[2], true) || it.name.equals(args[2], true) }
    val settings: VintageSettings = when {
        args[2].equals("dry", true) -> VintageSettings.DEFAULT.copy(masterEnabled = false)
        preset != null -> preset.settings
        else -> error("Unknown preset '${args[2]}'")
    }

    // Normalise to interleaved stereo.
    val frames = input.frames
    val stereo = FloatArray(frames * 2)
    for (i in 0 until frames) {
        if (input.channels == 1) {
            stereo[2 * i] = input.samples[i]; stereo[2 * i + 1] = input.samples[i]
        } else {
            stereo[2 * i] = input.samples[i * input.channels]
            stereo[2 * i + 1] = input.samples[i * input.channels + 1]
        }
    }

    val engine = VintageEngine(input.sampleRate, seed)
    engine.updateSettings(settings)
    val chunk = 1024
    val tmp = FloatArray(chunk * 2)
    var pos = 0
    val t0 = System.nanoTime()
    while (pos < frames) {
        val n = minOf(chunk, frames - pos)
        System.arraycopy(stereo, pos * 2, tmp, 0, n * 2)
        engine.processInterleaved(tmp, n)
        // Quantise exactly like the Android adapter does (TPDF dither) so the sound matches.
        for (i in 0 until n * 2) {
            stereo[pos * 2 + i] = Pcm.shortToFloat(Pcm.floatToShort(tmp[i], engine.nextDitherLsb()))
        }
        pos += n
    }
    val ms = (System.nanoTime() - t0) / 1e6
    WavIO.write16(File(args[1]), input.sampleRate, 2, stereo)
    val audioMs = frames * 1000.0 / input.sampleRate
    System.err.println("rendered %.1f s of audio in %.0f ms (%.0fx realtime) -> %s".format(audioMs / 1000, ms, audioMs / ms, args[1]))
}
