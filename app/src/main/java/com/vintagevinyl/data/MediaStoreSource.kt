package com.vintagevinyl.data

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.vintagevinyl.domain.model.Track
import com.vintagevinyl.domain.model.TrackSource

/** Reads the device's music from MediaStore. Blocking: call from a background dispatcher. */
class MediaStoreSource(private val context: Context) {

    fun query(): List<Track> {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.MIME_TYPE,
        )
        // Music only; ignore ringtones, notification sounds and clips shorter than 10 s.
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 10000"
        val out = ArrayList<Track>()
        try {
            context.contentResolver.query(collection, projection, selection, null, null)?.use { c ->
                val iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val iTitle = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val iArtist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val iAlbum = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val iAlbumId = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val iDur = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val iTrack = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
                val iYear = c.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
                val iMime = c.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                while (c.moveToNext()) {
                    val id = c.getLong(iId)
                    out += Track(
                        id = "ms:$id",
                        uri = ContentUris.withAppendedId(collection, id).toString(),
                        title = c.getString(iTitle).clean("Unknown title"),
                        artist = c.getString(iArtist).clean("Unknown artist"),
                        album = c.getString(iAlbum).clean("Unknown album"),
                        albumId = c.getLong(iAlbumId),
                        durationMs = c.getLong(iDur),
                        // Some taggers store disc*1000 + track in this column.
                        trackNumber = c.getInt(iTrack) % 1000,
                        year = c.getInt(iYear),
                        mimeType = c.getString(iMime),
                        source = TrackSource.MEDIA_STORE,
                        mediaStoreId = id,
                    )
                }
            }
        } catch (_: SecurityException) {
            // Permission revoked between the check and the query: report an empty library.
        }
        return out
    }

    private fun String?.clean(fallback: String): String {
        val v = this?.trim().orEmpty()
        return if (v.isEmpty() || v == MediaStore.UNKNOWN_STRING) fallback else v
    }
}
