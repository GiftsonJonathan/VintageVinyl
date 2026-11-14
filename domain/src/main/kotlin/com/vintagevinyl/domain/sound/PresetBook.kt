package com.vintagevinyl.domain.sound

import com.vintagevinyl.dsp.VintagePreset
import com.vintagevinyl.dsp.VintagePresets
import com.vintagevinyl.dsp.VintageSettings

/**
 * Pure logic for the preset list: built-ins first, then the user's own, unique ids and names,
 * and "which preset (if any) do the current settings correspond to?".
 */
object PresetBook {

    fun all(custom: List<VintagePreset>): List<VintagePreset> = VintagePresets.ALL + custom

    /** The preset whose settings equal [settings] exactly, or null when the user has tweaked them. */
    fun matching(settings: VintageSettings, custom: List<VintagePreset>): VintagePreset? =
        all(custom).firstOrNull { sameSound(it.settings, settings) }

    /**
     * Two settings are "the same sound" when every audible parameter matches. Amounts of
     * switched-off effects are ignored, because they are inaudible.
     */
    fun sameSound(a: VintageSettings, b: VintageSettings): Boolean {
        if (a.masterEnabled != b.masterEnabled || a.eqProfile != b.eqProfile ||
            a.stereoMode != b.stereoMode || a.recordCondition != b.recordCondition ||
            a.turntableSpeed != b.turntableSpeed || a.humFrequency != b.humFrequency
        ) return false
        return com.vintagevinyl.dsp.EffectId.values().all { id ->
            kotlin.math.abs(a.amount(id) - b.amount(id)) < 0.0005f
        }
    }

    /** Creates a custom preset from [settings]; the name is trimmed and de-duplicated ("Name (2)"). */
    fun createCustom(
        name: String, settings: VintageSettings, existing: List<VintagePreset>, newId: () -> String,
    ): VintagePreset {
        val base = name.trim().ifEmpty { "My preset" }
        val taken = all(existing).map { it.name.lowercase() }.toSet()
        var candidate = base
        var n = 2
        while (candidate.lowercase() in taken) { candidate = "$base ($n)"; n++ }
        return VintagePreset(
            id = newId(), name = candidate, description = "Custom preset",
            settings = settings.copy(masterEnabled = true), builtIn = false,
        )
    }
}
