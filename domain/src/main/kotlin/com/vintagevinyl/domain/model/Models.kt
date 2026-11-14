package com.vintagevinyl.domain.model

/** Where a track comes from. Determines how its artwork and URI permission are handled. */
enum class TrackSource { MEDIA_STORE, IMPORTED }

/**
 * One playable audio file.
 *
 * @property id Stable key: `ms:<mediaStoreId>` for scanned files, the URI string for files the
 *              user imported through the Storage Access Framework. Playlists store these ids.
 */
data class Track(
    val id: String,
    val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val trackNumber: Int = 0,
    val year: Int = 0,
    val mimeType: String? = null,
    val source: TrackSource = TrackSource.MEDIA_STORE,
    val mediaStoreId: Long? = null,
) {
    /** The container extension / format label shown in the track details ("FLAC", "MP3"). */
    val formatLabel: String
        get() = when (mimeType?.lowercase()) {
            "audio/flac", "audio/x-flac" -> "FLAC"
            "audio/mpeg", "audio/mp3" -> "MP3"
            "audio/mp4", "audio/m4a", "audio/x-m4a", "audio/aac", "audio/aacp" -> "AAC"
            "audio/wav", "audio/x-wav", "audio/wave" -> "WAV"
            "audio/ogg", "application/ogg" -> "OGG"
            "audio/opus" -> "OPUS"
            null -> ""
            else -> mimeType.substringAfter('/').uppercase()
        }
}

data class Album(
    val key: String,
    val title: String,
    val artist: String,
    val year: Int,
    val albumId: Long,
    val tracks: List<Track>,
) {
    val durationMs: Long get() = tracks.sumOf { it.durationMs }
}

data class Artist(
    val name: String,
    val albums: List<Album>,
) {
    val tracks: List<Track> get() = albums.flatMap { it.tracks }
}

data class Playlist(
    val id: String,
    val name: String,
    val trackIds: List<String>,
    val createdAt: Long,
)

enum class RepeatMode { OFF, ALL, ONE;

    fun next(): RepeatMode = when (this) { OFF -> ALL; ALL -> ONE; ONE -> OFF }
}

/** Everything the UI needs to know about one queue entry. */
data class QueueEntry(
    val trackId: String,
    val title: String,
    val artist: String,
)

/** Immutable snapshot of the player, published by the playback connection. */
data class PlayerState(
    val connected: Boolean = false,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val currentTrackId: String? = null,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeat: RepeatMode = RepeatMode.OFF,
    val volume: Float = 1f,
    val queue: List<QueueEntry> = emptyList(),
    val currentIndex: Int = -1,
    val error: String? = null,
) {
    val hasTrack: Boolean get() = currentTrackId != null
    val progress: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}
