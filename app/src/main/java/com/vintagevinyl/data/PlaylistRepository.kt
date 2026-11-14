package com.vintagevinyl.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.vintagevinyl.domain.library.PlaylistCodec
import com.vintagevinyl.domain.model.Playlist
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.UUID

/** Playlists, persisted in DataStore. All mutations are atomic read-modify-write transactions. */
class PlaylistRepository(
    private val dataStore: DataStore<Preferences>,
    scope: CoroutineScope,
) {
    val playlists: StateFlow<List<Playlist>> = dataStore.data
        .map { PlaylistCodec.decode(it[Keys.PLAYLISTS]) }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** Creates a playlist, optionally pre-filled, and returns its id. */
    suspend fun create(name: String, trackIds: List<String> = emptyList()): String {
        val id = UUID.randomUUID().toString()
        mutate { list ->
            list + Playlist(id, name.trim().ifEmpty { "New playlist" }, trackIds.distinct(), System.currentTimeMillis())
        }
        return id
    }

    suspend fun rename(id: String, name: String) = mutate { list ->
        list.map { if (it.id == id) it.copy(name = name.trim().ifEmpty { it.name }) else it }
    }

    suspend fun delete(id: String) = mutate { list -> list.filterNot { it.id == id } }

    /** Appends tracks, skipping ones already in the playlist. */
    suspend fun addTracks(id: String, trackIds: List<String>) = mutate { list ->
        list.map { p ->
            if (p.id != id) p else p.copy(trackIds = p.trackIds + trackIds.filterNot { it in p.trackIds }.distinct())
        }
    }

    suspend fun removeTrack(id: String, trackId: String) = mutate { list ->
        list.map { if (it.id == id) it.copy(trackIds = it.trackIds - trackId) else it }
    }

    suspend fun moveTrack(id: String, from: Int, to: Int) = mutate { list ->
        list.map { p ->
            if (p.id != id || from !in p.trackIds.indices || to !in p.trackIds.indices) p
            else p.copy(trackIds = p.trackIds.toMutableList().apply { add(to, removeAt(from)) })
        }
    }

    private suspend fun mutate(transform: (List<Playlist>) -> List<Playlist>) {
        dataStore.edit { prefs ->
            prefs[Keys.PLAYLISTS] = PlaylistCodec.encode(transform(PlaylistCodec.decode(prefs[Keys.PLAYLISTS])))
        }
    }
}
