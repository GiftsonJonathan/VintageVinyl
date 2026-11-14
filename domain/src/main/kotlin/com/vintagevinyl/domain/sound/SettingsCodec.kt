package com.vintagevinyl.domain.sound

import com.vintagevinyl.dsp.EffectId
import com.vintagevinyl.dsp.EqProfile
import com.vintagevinyl.dsp.HumFrequency
import com.vintagevinyl.dsp.RecordCondition
import com.vintagevinyl.dsp.StereoMode
import com.vintagevinyl.dsp.TurntableSpeed
import com.vintagevinyl.dsp.VintagePreset
import com.vintagevinyl.dsp.VintageSettings
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Compact text serialisation of [VintageSettings] and custom presets, used by the DataStore
 * based persistence. Hand-rolled (instead of `org.json`) so that it is plain Kotlin and
 * unit-testable on the JVM.
 *
 * Settings line (version 1):
 * `v1|master=1|eq=WARM_VINYL|stereo=FULL|cond=GOOD|speed=RPM_33|hum=HZ_50|fx=WARMTH:0.350:1,ROLLOFF:0.150:0,...`
 *
 * Decoding is tolerant: unknown keys are ignored, missing ones keep their defaults, and a
 * malformed value falls back to the default for that field, so an app update that adds an
 * effect (or a corrupted preference) can never crash start-up.
 */
object SettingsCodec {

    fun encode(s: VintageSettings): String = buildString {
        append("v1")
        append("|master=").append(if (s.masterEnabled) 1 else 0)
        append("|eq=").append(s.eqProfile.name)
        append("|stereo=").append(s.stereoMode.name)
        append("|cond=").append(s.recordCondition.name)
        append("|speed=").append(s.turntableSpeed.name)
        append("|hum=").append(s.humFrequency.name)
        append("|fx=")
        append(EffectId.values().joinToString(",") { id ->
            "%s:%.3f:%d".format(java.util.Locale.ROOT, id.name, s.rawAmount(id), if (s.isEnabled(id)) 1 else 0)
        })
    }

    fun decode(text: String?, fallback: VintageSettings = VintageSettings.DEFAULT): VintageSettings {
        if (text.isNullOrBlank()) return fallback
        val parts = text.split('|')
        if (parts.firstOrNull() != "v1") return fallback
        val map = parts.drop(1).mapNotNull { p ->
            val i = p.indexOf('=')
            if (i <= 0) null else p.substring(0, i) to p.substring(i + 1)
        }.toMap()

        var s = fallback
        map["master"]?.let { s = s.copy(masterEnabled = it == "1") }
        map["eq"]?.let { v -> enumOrNull<EqProfile>(v)?.let { s = s.copy(eqProfile = it) } }
        map["stereo"]?.let { v -> enumOrNull<StereoMode>(v)?.let { s = s.copy(stereoMode = it) } }
        map["cond"]?.let { v -> enumOrNull<RecordCondition>(v)?.let { s = s.copy(recordCondition = it) } }
        map["speed"]?.let { v -> enumOrNull<TurntableSpeed>(v)?.let { s = s.copy(turntableSpeed = it) } }
        map["hum"]?.let { v -> enumOrNull<HumFrequency>(v)?.let { s = s.copy(humFrequency = it) } }
        map["fx"]?.split(',')?.forEach { item ->
            val f = item.split(':')
            if (f.size == 3) {
                val id = enumOrNull<EffectId>(f[0])
                val amount = f[1].toFloatOrNull()
                if (id != null && amount != null) {
                    s = s.withAmount(id, amount).withEnabled(id, f[2] == "1")
                }
            }
        }
        return s
    }

    // ---- custom presets ----------------------------------------------------------------------

    /** One preset per line: `id<TAB>name<TAB>description<TAB>settings`, text fields URL-encoded. */
    fun encodePresets(presets: List<VintagePreset>): String =
        presets.joinToString("\n") { p ->
            listOf(p.id, p.name, p.description, encode(p.settings)).joinToString("\t") { enc(it) }
        }

    fun decodePresets(text: String?): List<VintagePreset> {
        if (text.isNullOrBlank()) return emptyList()
        return text.lines().mapNotNull { line ->
            val f = line.split('\t')
            if (f.size != 4) return@mapNotNull null
            runCatching {
                VintagePreset(
                    id = dec(f[0]), name = dec(f[1]), description = dec(f[2]),
                    settings = decode(dec(f[3])), builtIn = false,
                )
            }.getOrNull()
        }
    }

    private inline fun <reified E : Enum<E>> enumOrNull(name: String): E? =
        enumValues<E>().firstOrNull { it.name == name }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    private fun dec(s: String) = URLDecoder.decode(s, "UTF-8")
}
