package com.vintagevinyl.dsp.tools

/**import java.io.ByteArrayOutputStream */
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/** Minimal RIFF/WAVE reader and writer (PCM 8/16/24/32 and IEEE float32), for offline tools. */
class WavData(val sampleRate: Int, val channels: Int, val samples: FloatArray) {
    val frames: Int get() = samples.size / channels
}

object WavIO {

    private const val MAX_FILE_BYTES = 64 * 1024 * 1024
    private const val MAX_SAMPLES = 16_000_000
    private const val MAX_CHANNELS = 8
    private const val MAX_SAMPLE_RATE = 384_000

    fun read(file: File): WavData {
        val fileSize = file.length()
        require(fileSize in 12..MAX_FILE_BYTES.toLong()) {"WAV file size is  unsupported"}

        val bytes = file.readBytes()
        require(bytes.size.toLong() == fileSize) {"WAV file changed while being read"}
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        require(bb.getInt(0) == 0x46464952 &&  bb.getInt(8) == 0x45564157) { "Not a RIFF/WAVE file" }
        var pos = 12
        var format = -1; var channels = 0; var rate = 0; var bits = 0; var blockAlign = 0
        var dataPos = -1; var dataLen = 0
        while (pos + 8 <= bb.limit()) {
            val id = bb.getInt(pos)
            val size = bb.getInt(pos + 4)
            val body = pos + 8
            val padding = size and 1
            require(size >= 0 && size <= bb.limit() - body - padding) {"Invalid WAV chunk size"}
            when (id) {
                0x20746D66 -> { // "fmt "
                    require(size >= 16) {"Invalid WAV format chunk"}
                    format = bb.getShort(body).toInt() and 0xFFFF
                    channels = bb.getShort(body + 2).toInt() and 0xFFFF
                    rate = bb.getInt(body + 4)
                    bits = bb.getShort(body + 14).toInt()
                    blockAlign = bb.getShort(body + 12).toInt() and 0xFFFF
                    if (format == 0xFFFE && size >= 26) format = bb.getShort(body + 24).toInt() and 0xFFFF
                }
                0x61746164 -> { // "data"
                    dataPos = body
                    dataLen = size
                }
            }
            if (dataPos >= 0) break
            pos = body + size + padding
        }
        require(format >= 0 &&  dataPos >= 0) {"Missing WAV format or data chunk"}
        require(channels in 1..MAX_CHANNELS && rate in 1..MAX_SAMPLE_RATE) {"Unsupported WAV layout"}
        require(format == 1 || format == 3) {"Unsupported WAV format $format"}
        require(bits in setOf(8,16,24,32) && (format != 3 || bits == 32)) {"Unsupported WAV format $format/$bits"}
        val bytesPer = bits / 8
        require(blockAlign == channels * bytesPer && dataLen % blockAlign == 0) {"Invalid WAV block alignment"}
        val count = dataLen / bytesPer
        require(count <= MAX_SAMPLES) {"WAV contains too many samples"}
        val out = FloatArray(count)
        for (i in 0 until count) {
            val p = dataPos + i * bytesPer
            out[i] = when {
                format == 3 -> bb.getFloat(p)
                bits == 16 -> bb.getShort(p) / 32768f
                bits == 8 -> ((bb.get(p).toInt() and 0xFF) - 128) / 128f
                bits == 24 -> {
                    val v = (bb.get(p).toInt() and 0xFF) or ((bb.get(p + 1).toInt() and 0xFF) shl 8) or (bb.get(p + 2).toInt() shl 16)
                    v / 8388608f
                }
                else -> bb.getInt(p).toDouble().div(2147483648.0).toFloat()
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
            val rounded = if (x.isNaN()) 0 else (x * 32768f).roundToInt()
            val v = rounded.coerceIn(-32768, 32767)
            bb.putShort(v.toShort())
        }
        file.writeBytes(bb.array())
    }
}
