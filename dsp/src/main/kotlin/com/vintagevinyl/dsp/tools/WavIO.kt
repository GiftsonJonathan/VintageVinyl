package com.vintagevinyl.dsp.tools

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Minimal RIFF/WAVE reader and writer (PCM 8/16/24/32 and IEEE float32), for offline tools. */
class WavData(val sampleRate: Int, val channels: Int, val samples: FloatArray) {
    val frames: Int get() = samples.size / channels
}

object WavIO {

    fun read(file: File): WavData {
        val bb = ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        require(bb.remaining() > 12 && bb.getInt(0) == 0x46464952 && bb.getInt(8) == 0x45564157) { "Not a RIFF/WAVE file" }
        var pos = 12
        var format = 1; var channels = 2; var rate = 44100; var bits = 16
        var dataPos = -1; var dataLen = 0
        while (pos + 8 <= bb.limit()) {
            val id = bb.getInt(pos)
            val size = bb.getInt(pos + 4)
            val body = pos + 8
            when (id) {
                0x20746D66 -> { // "fmt "
                    format = bb.getShort(body).toInt() and 0xFFFF
                    channels = bb.getShort(body + 2).toInt()
                    rate = bb.getInt(body + 4)
                    bits = bb.getShort(body + 14).toInt()
                    if (format == 0xFFFE && size >= 26) format = bb.getShort(body + 24).toInt() and 0xFFFF
                }
                0x61746164 -> { // "data"
                    dataPos = body
                    dataLen = if (size < 0 || body + size > bb.limit()) bb.limit() - body else size
                }
            }
            if (dataPos >= 0) break
            pos = body + size + (size and 1)
        }
        require(dataPos >= 0) { "No data chunk" }
        val bytesPer = bits / 8
        val count = dataLen / bytesPer
        val out = FloatArray(count)
        for (i in 0 until count) {
            val p = dataPos + i * bytesPer
            out[i] = when {
                format == 3 && bits == 32 -> bb.getFloat(p)
                bits == 16 -> bb.getShort(p) / 32768f
                bits == 8 -> ((bb.get(p).toInt() and 0xFF) - 128) / 128f
                bits == 24 -> {
                    val v = (bb.get(p).toInt() and 0xFF) or ((bb.get(p + 1).toInt() and 0xFF) shl 8) or (bb.get(p + 2).toInt() shl 16)
                    v / 8388608f
                }
                bits == 32 -> bb.getInt(p) / 2147483648f
                else -> error("Unsupported WAV format $format/$bits")
            }
        }
        return WavData(rate, channels, out)
    }

    /** Writes 16-bit PCM. [samples] are interleaved and are hard-clipped to full scale. */
    fun write16(file: File, sampleRate: Int, channels: Int, samples: FloatArray) {
        val dataBytes = samples.size * 2
        val bb = ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN)
        bb.putInt(0x46464952).putInt(36 + dataBytes).putInt(0x45564157)
        bb.putInt(0x20746D66).putInt(16).putShort(1).putShort(channels.toShort())
        bb.putInt(sampleRate).putInt(sampleRate * channels * 2).putShort((channels * 2).toShort()).putShort(16)
        bb.putInt(0x61746164).putInt(dataBytes)
        for (x in samples) {
            val v = Math.round(x * 32768f).coerceIn(-32768, 32767)
            bb.putShort(v.toShort())
        }
        file.writeBytes(bb.array())
    }
}
