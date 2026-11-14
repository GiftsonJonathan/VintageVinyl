package com.vintagevinyl.ui.viewmodel

import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.vintagevinyl.AppContainer
import com.vintagevinyl.data.ArtworkRepository
import com.vintagevinyl.data.ImportedTracksStore
import com.vintagevinyl.data.LibraryRepository
import com.vintagevinyl.data.PlaylistRepository
import com.vintagevinyl.data.SoundRepository
import com.vintagevinyl.domain.library.LibraryIndex
import com.vintagevinyl.domain.model.Album
import com.vintagevinyl.domain.model.Artist
import com.vintagevinyl.domain.model.PlayerState
import com.vintagevinyl.domain.model.Playlist
import com.vintagevinyl.domain.model.Track
import com.vintagevinyl.dsp.VintagePreset
import com.vintagevinyl.dsp.VintageSettings
import com.vintagevinyl.playback.PlaybackConnection
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Playback state and transport commands for the turntable and now-playing screens. */
class PlayerViewModel(
    private val playback: PlaybackConnection,
    library: LibraryRepository,
    artwork: ArtworkRepository,
    private val sound: SoundRepository,
) : ViewModel() {

    val state: StateFlow<PlayerState> = playback.state

    /** The library entry for the current queue item (null if it is no longer in the library). */
    val currentTrack: StateFlow<Track?> = combine(
        state.map { it.currentTrackId }.distinctUntilChanged(),
        library.index,
    ) { id, index -> id?.let { index.track(it) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Artwork of the current track, large enough for the record label and the sleeve. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val artwork: StateFlow<ImageBitmap?> = currentTrack
        .flatMapLatest { t -> flow { emit(t?.let { artwork.load(it, 640)?.asImageBitmap() }) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val settings: StateFlow<VintageSettings> = sound.settings
    val presets: StateFlow<List<VintagePreset>> = sound.presets
    val activePresetId: StateFlow<String?> = sound.activePresetId

    val position: Flow<Long> get() = playback.positionFlow()

    fun togglePlay() = playback.togglePlay()
    fun next() = playback.next()
    fun previous() = playback.previous()
    fun seekTo(ms: Long) = playback.seekTo(ms)
    fun toggleShuffle() = playback.setShuffle(!state.value.shuffle)
    fun cycleRepeat() = playback.cycleRepeat()
    fun setVolume(v: Float) = playback.setVolume(v)
    fun skipTo(index: Int) = playback.skipTo(index)
    fun removeFromQueue(index: Int) = playback.removeFromQueue(index)
    fun moveInQueue(from: Int, to: Int) = playback.moveInQueue(from, to)
    fun clearQueue() = playback.clearQueue()

    fun applyPreset(p: VintagePreset) = sound.applyPreset(p)
    fun update(transform: (VintageSettings) -> VintageSettings) = sound.update(transform)
}

/** Library browsing, search, import and playlists. */
class LibraryViewModel(
    private val library: LibraryRepository,
    private val playlistRepo: PlaylistRepository,
    private val imported: ImportedTracksStore,
    private val playback: PlaybackConnection,
) : ViewModel() {

    val index: StateFlow<LibraryIndex> = library.index
    val scanning: StateFlow<Boolean> = library.scanning
    val hasPermission: StateFlow<Boolean> = library.hasPermission
    val playlists: StateFlow<List<Playlist>> = playlistRepo.playlists
    val importFailures: StateFlow<Int> = imported.lastFailedCount
    val currentTrackId: Flow<String?> = playback.state.map { it.currentTrackId }.distinctUntilChanged()

    val query = MutableStateFlow("")

    val songs: StateFlow<List<Track>> = combine(index, query) { i, q -> i.search(q) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val albums: StateFlow<List<Album>> = combine(index, query) { i, q -> i.searchAlbums(q) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val artists: StateFlow<List<Artist>> = combine(index, query) { i, q -> i.searchArtists(q) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(q: String) {
        query.value = q
    }

    fun refresh() = library.refresh()
    fun onPermissionResult() = library.onPermissionResult()

    fun import(uris: List<Uri>) {
        viewModelScope.launch { imported.import(uris) }
    }

    fun removeImported(trackId: String) {
        viewModelScope.launch { imported.remove(trackId) }
    }

    // ---- playback --------------------------------------------------------------------------

    fun play(tracks: List<Track>, startIndex: Int = 0) = playback.playTracks(tracks, startIndex)
    fun shuffle(tracks: List<Track>) {
        if (tracks.isNotEmpty()) playback.playTracks(tracks, (tracks.indices).random(), shuffle = true)
    }
    fun playNext(track: Track) = playback.playNext(listOf(track))
    fun enqueue(tracks: List<Track>) = playback.enqueue(tracks)

    // ---- playlists -------------------------------------------------------------------------

    fun createPlaylist(name: String, trackIds: List<String> = emptyList()) {
        viewModelScope.launch { playlistRepo.create(name, trackIds) }
    }

    fun renamePlaylist(id: String, name: String) {
        viewModelScope.launch { playlistRepo.rename(id, name) }
    }

    fun deletePlaylist(id: String) {
        viewModelScope.launch { playlistRepo.delete(id) }
    }

    fun addToPlaylist(id: String, trackIds: List<String>) {
        viewModelScope.launch { playlistRepo.addTracks(id, trackIds) }
    }

    fun removeFromPlaylist(id: String, trackId: String) {
        viewModelScope.launch { playlistRepo.removeTrack(id, trackId) }
    }
}

/** The sound panel: live settings, presets and their persistence. */
class SoundViewModel(private val sound: SoundRepository) : ViewModel() {
    val settings: StateFlow<VintageSettings> = sound.settings
    val presets: StateFlow<List<VintagePreset>> = sound.presets
    val activePresetId: StateFlow<String?> = sound.activePresetId

    fun update(transform: (VintageSettings) -> VintageSettings) = sound.update(transform)
    fun applyPreset(p: VintagePreset) = sound.applyPreset(p)
    fun saveCurrentAsPreset(name: String) = sound.saveCurrentAsPreset(name)
    fun deleteCustomPreset(id: String) = sound.deleteCustomPreset(id)
}

/** Builds the view models from the hand-written [AppContainer] (no Hilt: see VintageVinylApp). */
class AppViewModelFactory(private val c: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(PlayerViewModel::class.java) ->
            PlayerViewModel(c.playback, c.library, c.artwork, c.sound) as T
        modelClass.isAssignableFrom(LibraryViewModel::class.java) ->
            LibraryViewModel(c.library, c.playlists, c.importedTracks, c.playback) as T
        modelClass.isAssignableFrom(SoundViewModel::class.java) -> SoundViewModel(c.sound) as T
        else -> throw IllegalArgumentException("Unknown ViewModel ${modelClass.name}")
    }
}
