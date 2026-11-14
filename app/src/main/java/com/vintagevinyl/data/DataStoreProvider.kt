package com.vintagevinyl.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

/** The single preferences file holding settings, custom presets, playlists and imported tracks. */
val Context.vvDataStore: DataStore<Preferences> by preferencesDataStore(name = "vintage_vinyl")

internal object Keys {
    val SOUND_SETTINGS = stringPreferencesKey("sound_settings")
    val CUSTOM_PRESETS = stringPreferencesKey("custom_presets")
    val PLAYLISTS = stringPreferencesKey("playlists")
    val IMPORTED_TRACKS = stringPreferencesKey("imported_tracks")
}
