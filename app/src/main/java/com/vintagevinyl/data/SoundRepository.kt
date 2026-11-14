package com.vintagevinyl.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.vintagevinyl.domain.sound.PresetBook
import com.vintagevinyl.domain.sound.SettingsCodec
import com.vintagevinyl.dsp.VintagePreset
import com.vintagevinyl.dsp.VintageSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Single source of truth for the vintage sound: the live [VintageSettings] (observed by the
 * playback service, which forwards them to the DSP engine), the preset list and which preset
 * (if any) is currently active.
 *
 * Settings changes are applied instantly and written to disk 400 ms after the last change, so
 * dragging a knob does not hammer storage.
 */
@OptIn(FlowPreview::class)
class SoundRepository(
    private val dataStore: DataStore<Preferences>,
    private val scope: CoroutineScope,
) {
    private val _settings = MutableStateFlow(VintageSettings.DEFAULT)
    val settings: StateFlow<VintageSettings> = _settings.asStateFlow()

    private val _custom = MutableStateFlow<List<VintagePreset>>(emptyList())

    /** Built-in presets followed by the user's own. */
    val presets: StateFlow<List<VintagePreset>> = _custom
        .combine(_settings) { custom, _ -> PresetBook.all(custom) }
        .stateIn(scope, SharingStarted.Eagerly, PresetBook.all(emptyList()))

    /** Id of the preset equal to the current settings, or null when they have been tweaked. */
    val activePresetId: StateFlow<String?> = _settings
        .combine(_custom) { s, custom -> PresetBook.matching(s, custom)?.id }
        .stateIn(scope, SharingStarted.Eagerly, VintageSettings.DEFAULT.let { PresetBook.matching(it, emptyList())?.id })

    init {
        scope.launch {
            val prefs = dataStore.data.first()
            _custom.value = SettingsCodec.decodePresets(prefs[Keys.CUSTOM_PRESETS])
            _settings.value = SettingsCodec.decode(prefs[Keys.SOUND_SETTINGS])
            // Persist later changes (the load above is the first emission, hence drop(1)).
            _settings.debounce(400).drop(1).collect { s ->
                dataStore.edit { it[Keys.SOUND_SETTINGS] = SettingsCodec.encode(s) }
            }
        }
    }

    /** Applies [transform] to the live settings. Takes effect in the audio engine immediately. */
    fun update(transform: (VintageSettings) -> VintageSettings) {
        _settings.update(transform)
    }

    fun applyPreset(preset: VintagePreset) {
        _settings.value = preset.settings
    }

    /** Saves the current settings as a new custom preset and returns it. */
    fun saveCurrentAsPreset(name: String): VintagePreset {
        val preset = PresetBook.createCustom(name, _settings.value, _custom.value) { UUID.randomUUID().toString() }
        _custom.update { it + preset }
        persistCustom()
        return preset
    }

    fun deleteCustomPreset(id: String) {
        _custom.update { list -> list.filterNot { it.id == id } }
        persistCustom()
    }

    private fun persistCustom() {
        val snapshot = _custom.value
        scope.launch { dataStore.edit { it[Keys.CUSTOM_PRESETS] = SettingsCodec.encodePresets(snapshot) } }
    }
}
