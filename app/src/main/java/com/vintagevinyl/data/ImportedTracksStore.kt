package com.vintagevinyl.data

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.vintagevinyl.domain.library.TrackCodec
import com.vintagevinyl.domain.model.Track
import com.vintagevinyl.domain.model.TrackSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

/**
 * Files the user picked with the system file picker (Storage Access Framework).
 *
 * The picker grants a *persistable* read permission on each document, which is kept across
 * restarts, so imported tracks keep playing without any storage permission. Their metadata is
 * read once with [MediaMetadataRetriever] and stored in DataStore.
 */
class ImportedTracksStore(
    private val context: Context,
    private val dataStore: DataStore<Preferences>,
    scope: CoroutineScope,
) {
    val tracks: StateFlow<List<Track>> = dataStore.data
        .map { TrackCodec.decode(it[Keys.IMPORTED_TRACKS]) }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** Number of files from the last [import] call that could not be read as audio. */
    val lastFailedCount = MutableStateFlow(0)

    suspend fun import(uris: List<Uri>) {
        val added = ArrayList<Track>()
        var failed = 0
        withContext(Dispatchers.IO) {
            for (uri in uris) {
                try {
                    context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: SecurityException) {
                    // Not every provider offers persistable grants; the file still plays this session.
                } catch (_: IllegalArgumentException) {
                }
                val track = readMetadata(uri)
                if (track == null) failed++ else added += track
            }
        }
        lastFailedCount.value = failed
        if (added.isEmpty()) return
        dataStore.edit { prefs ->
            val existing = TrackCodec.decode(prefs[Keys.IMPORTED_TRACKS])
            val byId = LinkedHashMap<String, Track>()
            existing.forEach { byId[it.id] = it }
            added.forEach { byId[it.id] = it } // re-importing refreshes metadata
            prefs[Keys.IMPORTED_TRACKS] = TrackCodec.encode(byId.values.toList())
        }
    }

    suspend fun remove(trackId: String) {
        dataStore.edit { prefs ->
            val kept = TrackCodec.decode(prefs[Keys.IMPORTED_TRACKS]).filterNot { it.id == trackId }
            prefs[Keys.IMPORTED_TRACKS] = TrackCodec.encode(kept)
        }
    }

    private fun readMetadata(uri: Uri): Track? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            val hasAudio = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)
            // A file the platform cannot decode has no duration (or explicitly no audio): reject it.
            if (duration == null || duration <= 0 || hasAudio == "no") return null

            fun meta(key: Int) = retriever.extractMetadata(key)?.trim()?.takeIf { it.isNotEmpty() }
            val title = meta(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: displayName(uri)
            val artist = meta(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?: meta(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST) ?: "Unknown artist"
            val album = meta(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: "Imported files"
            val trackNo = meta(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
                ?.substringBefore('/')?.trim()?.toIntOrNull() ?: 0
            val year = meta(MediaMetadataRetriever.METADATA_KEY_YEAR)?.take(4)?.toIntOrNull() ?: 0
            Track(
                id = uri.toString(),
                uri = uri.toString(),
                title = title,
                artist = artist,
                album = album,
                albumId = album.hashCode().toLong(),
                durationMs = duration,
                trackNumber = trackNo,
                year = year,
                mimeType = meta(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: context.contentResolver.getType(uri),
                source = TrackSource.IMPORTED,
            )
        } catch (_: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    private fun displayName(uri: Uri): String {
        val name = try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        } catch (_: Exception) {
            null
        } ?: uri.lastPathSegment ?: "Imported track"
        return name.substringBeforeLast('.').ifEmpty { name }
    }
}
