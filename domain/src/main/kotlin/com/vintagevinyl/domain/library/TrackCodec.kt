package com.vintagevinyl.domain.library

import com.vintagevinyl.domain.model.Track
import com.vintagevinyl.domain.model.TrackSource
import java.net.URLDecoder
import java.net.URLEncoder

/** Plain-text persistence for tracks the user imported via the Storage Access Framework. */
object TrackCodec {

    fun encode(tracks: List<Track>): String = tracks.joinToString("\n") { t ->
        listOf(
            t.id, t.uri, t.title, t.artist, t.album, t.albumId.toString(), t.durationMs.toString(),
            t.trackNumber.toString(), t.year.toString(), t.mimeType.orEmpty(),
        ).joinToString("\t") { enc(it) }
    }

    fun decode(text: String?): List<Track> {
        if (text.isNullOrBlank()) return emptyList()
        return text.lines().mapNotNull { line ->
            val f = line.split('\t')
            if (f.size != 10) return@mapNotNull null
            runCatching {
                Track(
                    id = dec(f[0]), uri = dec(f[1]), title = dec(f[2]), artist = dec(f[3]), album = dec(f[4]),
                    albumId = dec(f[5]).toLong(), durationMs = dec(f[6]).toLong(),
                    trackNumber = dec(f[7]).toInt(), year = dec(f[8]).toInt(),
                    mimeType = dec(f[9]).ifEmpty { null },
                    source = TrackSource.IMPORTED, mediaStoreId = null,
                )
            }.getOrNull()
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    private fun dec(s: String) = URLDecoder.decode(s, "UTF-8")
}
