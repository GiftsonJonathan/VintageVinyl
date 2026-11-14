package com.vintagevinyl.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vintagevinyl.dsp.EffectId
import com.vintagevinyl.dsp.EqProfile
import com.vintagevinyl.dsp.HumFrequency
import com.vintagevinyl.dsp.RecordCondition
import com.vintagevinyl.dsp.StereoMode
import com.vintagevinyl.dsp.VintagePreset
import com.vintagevinyl.dsp.VintageSettings
import com.vintagevinyl.ui.components.ChipRow
import com.vintagevinyl.ui.components.ConfirmDialog
import com.vintagevinyl.ui.components.Glyph
import com.vintagevinyl.ui.components.GlyphIcon
import com.vintagevinyl.ui.components.HorizontalFader
import com.vintagevinyl.ui.components.RotaryKnob
import com.vintagevinyl.ui.components.SectionLabel
import com.vintagevinyl.ui.components.SelectorChip
import com.vintagevinyl.ui.components.TextInputDialog
import com.vintagevinyl.ui.components.ToggleSwitch
import com.vintagevinyl.ui.theme.VV
import com.vintagevinyl.ui.viewmodel.SoundViewModel

/** Short engraved names and one-line hints for the control panel. */
private val effectName = mapOf(
    EffectId.WARMTH to "Warmth", EffectId.ROLLOFF to "Roll-off", EffectId.EQ_PROFILE to "Profile",
    EffectId.SURFACE_NOISE to "Surface noise", EffectId.CRACKLE to "Crackle", EffectId.POPS to "Pops",
    EffectId.WOW to "Wow", EffectId.FLUTTER to "Flutter", EffectId.HUM to "Hum",
    EffectId.SATURATION to "Saturation", EffectId.TAPE to "Tape", EffectId.HARMONICS to "Harmonics",
    EffectId.DYNAMICS to "Dynamics", EffectId.SPEAKER to "Speaker", EffectId.RESONANCE to "Resonance",
)

private val effectHint = mapOf(
    EffectId.SURFACE_NOISE to "Continuous hiss of the groove",
    EffectId.CRACKLE to "Clustered dust and static ticks",
    EffectId.POPS to "Rare bumps and repeating scratches",
)

/**
 * The control panel. Every knob, fader and switch is bound to one parameter of the real DSP
 * engine and takes effect immediately, while music plays; nothing here is decorative.
 */
@Composable
fun SoundScreen(vm: SoundViewModel) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val presets by vm.presets.collectAsStateWithLifecycle()
    val activeId by vm.activePresetId.collectAsStateWithLifecycle()

    var showSave by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<VintagePreset?>(null) }

    fun edit(transform: (VintageSettings) -> VintageSettings) = vm.update(transform)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        // ---- master ----------------------------------------------------------------------
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("SOUND LAB", style = MaterialTheme.typography.titleLarge, color = VV.Gold)
                Text("Vintage playback processor", style = MaterialTheme.typography.bodySmall, color = VV.CreamDim)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ToggleSwitch(s.masterEnabled, { on -> edit { it.copy(masterEnabled = on) } }, "Master switch for all vintage processing", width = 64.dp)
                Text(if (s.masterEnabled) "POWER ON" else "BYPASS", style = MaterialTheme.typography.labelSmall, color = if (s.masterEnabled) VV.Lamp else VV.CreamDim)
            }
        }
        Text(
            "Presets and tone profiles are creative voicings inspired by vintage equipment. They are not exact emulations of any historical turntable, radio or gramophone.",
            style = MaterialTheme.typography.bodySmall, color = VV.CreamDim, modifier = Modifier.padding(top = 8.dp),
        )

        Column(Modifier.alpha(if (s.masterEnabled) 1f else 0.55f)) {
            // ---- presets -----------------------------------------------------------------
            SectionLabel("Presets")
            ChipRow(Modifier.fillMaxWidth()) {
                presets.forEach { p ->
                    SelectorChip(
                        p.name, selected = p.id == activeId, onClick = { vm.applyPreset(p) },
                        trailing = if (!p.builtIn) {
                            {
                                Box(
                                    Modifier.padding(start = 8.dp).size(18.dp).clickable { deleting = p },
                                    contentAlignment = Alignment.Center,
                                ) { GlyphIcon(Glyph.CLOSE, Modifier.size(14.dp), VV.CreamDim) }
                            }
                        } else null,
                    )
                }
                SelectorChip("+ SAVE CURRENT", selected = false, onClick = { showSave = true })
            }
            val active = presets.firstOrNull { it.id == activeId }
            Text(
                active?.description ?: "Custom settings",
                style = MaterialTheme.typography.bodySmall, color = VV.CreamDim, modifier = Modifier.padding(top = 8.dp),
            )

            // ---- record, deck, stereo ------------------------------------------------------
            SectionLabel("Record & deck")
            Caption("Record condition")
            ChipRow(Modifier.fillMaxWidth()) {
                RecordCondition.values().forEach { c ->
                    SelectorChip(c.label, c == s.recordCondition, { edit { it.copy(recordCondition = c) } })
                }
            }
            Caption("Turntable speed")
            SpeedSelector(s.turntableSpeed, { sp -> edit { it.copy(turntableSpeed = sp) } })
            Caption("Stereo character")
            ChipRow(Modifier.fillMaxWidth()) {
                StereoMode.values().forEach { m ->
                    SelectorChip(m.label, m == s.stereoMode, { edit { it.copy(stereoMode = m) } })
                }
            }
            Caption("Mains frequency (hum)")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HumFrequency.values().forEach { h ->
                    SelectorChip(h.label, h == s.humFrequency, { edit { it.copy(humFrequency = h) } })
                }
            }

            // ---- tone ------------------------------------------------------------------------
            SectionLabel("Tone")
            Caption("Vintage equaliser voicing")
            ChipRow(Modifier.fillMaxWidth()) {
                EqProfile.values().forEach { p ->
                    SelectorChip(p.label, p == s.eqProfile, { edit { it.copy(eqProfile = p) } })
                }
            }
            Text(s.eqProfile.blurb, style = MaterialTheme.typography.bodySmall, color = VV.CreamDim, modifier = Modifier.padding(top = 6.dp))
            KnobRow(s, ::edit, EffectId.WARMTH, EffectId.ROLLOFF, EffectId.EQ_PROFILE)

            // ---- surface -----------------------------------------------------------------
            SectionLabel("Record surface")
            FaderRow(s, ::edit, EffectId.SURFACE_NOISE)
            FaderRow(s, ::edit, EffectId.CRACKLE)
            FaderRow(s, ::edit, EffectId.POPS)

            // ---- mechanics ---------------------------------------------------------------
            SectionLabel("Turntable mechanics")
            KnobRow(s, ::edit, EffectId.WOW, EffectId.FLUTTER, EffectId.HUM)

            // ---- colour --------------------------------------------------------------------
            SectionLabel("Harmonic colour")
            KnobRow(s, ::edit, EffectId.SATURATION, EffectId.TAPE, EffectId.HARMONICS, EffectId.DYNAMICS)

            // ---- acoustics ------------------------------------------------------------------
            SectionLabel("Playback acoustics")
            KnobRow(s, ::edit, EffectId.SPEAKER, EffectId.RESONANCE)
        }
        Text(
            "Each lamp switches a single effect on or off without interrupting the music. A dimmed knob belongs to a switched-off effect.",
            style = MaterialTheme.typography.bodySmall, color = VV.CreamDim, modifier = Modifier.padding(top = 20.dp, bottom = 12.dp),
        )
    }

    if (showSave) {
        TextInputDialog(
            title = "Save preset", confirmLabel = "Save", hint = "Preset name",
            onDismiss = { showSave = false },
            onConfirm = { name -> vm.saveCurrentAsPreset(name.trim()); showSave = false },
        )
    }
    deleting?.let { p ->
        ConfirmDialog(
            title = "Delete preset?", message = "“${p.name}” will be removed.", confirmLabel = "Delete",
            onDismiss = { deleting = null },
            onConfirm = { vm.deleteCustomPreset(p.id); deleting = null },
        )
    }
}

@Composable
private fun Caption(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = VV.CreamDim, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
}

/** A row of knobs, each with a lamp switch underneath. */
@Composable
private fun KnobRow(s: VintageSettings, edit: ((VintageSettings) -> VintageSettings) -> Unit, vararg ids: EffectId) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Top) {
        ids.forEach { id ->
            val on = s.isEnabled(id)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                RotaryKnob(
                    value = s.rawAmount(id),
                    onValueChange = { v -> edit { it.withAmount(id, v) } },
                    label = effectName.getValue(id),
                    knobSize = if (ids.size > 3) 60.dp else 68.dp,
                    enabled = on,
                )
                ToggleSwitch(on, { checked -> edit { it.withEnabled(id, checked) } }, "${id.label} on or off", Modifier.padding(top = 6.dp), width = 40.dp)
            }
        }
    }
}

/** A fader with its name, a hint and a lamp switch. */
@Composable
private fun FaderRow(s: VintageSettings, edit: ((VintageSettings) -> VintageSettings) -> Unit, id: EffectId) {
    val on = s.isEnabled(id)
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(effectName.getValue(id).uppercase(), style = MaterialTheme.typography.labelMedium, color = VV.Cream)
                effectHint[id]?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = VV.CreamDim) }
            }
            Text("${(s.rawAmount(id) * 100).toInt()}", style = MaterialTheme.typography.labelMedium, color = VV.Gold, modifier = Modifier.padding(end = 12.dp))
            ToggleSwitch(on, { checked -> edit { it.withEnabled(id, checked) } }, "${id.label} on or off", width = 40.dp)
        }
        HorizontalFader(s.rawAmount(id), { v -> edit { it.withAmount(id, v) } }, id.label, enabled = on)
    }
}
