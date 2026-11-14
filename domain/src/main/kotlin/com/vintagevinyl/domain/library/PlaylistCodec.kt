package com.vintagevinyl.domain.library

import com.vintagevinyl.domain.model.Playlist
import java.net.URLDecoder
import java.net.URLEncoder

/** Plain-text persistence for playlists: `id<TAB>name<TAB>createdAt<TAB>trackId,trackId,...` per line. */
object PlaylistCodec {

    fun encode(playlists: List<Playlist>): String = playlists.joinToString("\n") { p ->
        listOf(p.id, p.name, p.createdAt.toString(), p.trackIds.joinToString(",") { enc(it) }).joinToString("\t") {
            // The track list is already encoded per id; encode the whole field again so commas
            // and tabs of *names* can never collide with the separators.
            enc(it)
        }
    }

    fun decode(text: String?): List<Playlist> {
        if (text.isNullOrBlank()) return emptyList()
        return text.lines().mapNotNull { line ->
            val f = line.split('\t')
            if (f.size != 4) return@mapNotNull null
            runCatching {
                Playlist(
                    id = dec(f[0]),
                    name = dec(f[1]),
                    createdAt = dec(f[2]).toLong(),
                    trackIds = dec(f[3]).split(',').filter { it.isNotEmpty() }.map { dec(it) },
                )
            }.getOrNull()
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    private fun dec(s: String) = URLDecoder.decode(s, "UTF-8")
}
