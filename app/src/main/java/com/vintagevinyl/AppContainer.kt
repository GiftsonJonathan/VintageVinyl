package com.vintagevinyl

import android.app.Application
import androidx.media3.common.util.UnstableApi
import com.vintagevinyl.data.ArtworkRepository
import com.vintagevinyl.data.ImportedTracksStore
import com.vintagevinyl.data.LibraryRepository
import com.vintagevinyl.data.PlaylistRepository
import com.vintagevinyl.data.SoundRepository
import com.vintagevinyl.data.vvDataStore
import com.vintagevinyl.playback.PlaybackConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Manual dependency graph: one instance of each repository for the lifetime of the process. */
@UnstableApi
class AppContainer(app: Application) {

    /** Background work that must outlive any screen. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val dataStore = app.vvDataStore

    val importedTracks = ImportedTracksStore(app, dataStore, appScope)
    val library = LibraryRepository(app, appScope, importedTracks)
    val playlists = PlaylistRepository(dataStore, appScope)
    val sound = SoundRepository(dataStore, appScope)
    val artwork = ArtworkRepository(app)
    val playback = PlaybackConnection(app)
}
