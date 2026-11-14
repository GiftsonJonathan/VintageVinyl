package com.vintagevinyl.domain.library

import com.vintagevinyl.domain.model.Album
import com.vintagevinyl.domain.model.Artist
import com.vintagevinyl.domain.model.Track
import java.text.Normalizer

/**
 * Immutable, precomputed view of the library: songs sorted by title, albums grouped by
 * (album, artist), artists grouped by name, plus accent- and case-insensitive search.
 * Built off the main thread whenever the list of tracks changes.
 */
class LibraryIndex private constructor(
    val tracks: List<Track>,
    val albums: List<Album>,
    val artists: List<Artist>,
    private val byId: Map<String, Track>,
    private val searchKeys: List<Pair<Track, String>>,
) {
    fun track(id: String): Track? = byId[id]

    fun tracks(ids: List<String>): List<Track> = ids.mapNotNull { byId[it] }

    /**
     * All tracks whose title, artist or album contain every word of [query] (any order).
     * An empty query returns every track.
     */
    fun search(query: String): List<Track> {
        val words = normalize(query).split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return tracks
        return searchKeys.filter { (_, key) -> words.all { key.contains(it) } }.map { it.first }
    }

    fun searchAlbums(query: String): List<Album> {
        val words = normalize(query).split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return albums
        return albums.filter { a -> val k = normalize(a.title + " " + a.artist); words.all { k.contains(it) } }
    }

    fun searchArtists(query: String): List<Artist> {
        val words = normalize(query).split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return artists
        return artists.filter { a -> val k = normalize(a.name); words.all { k.contains(it) } }
    }

    companion object {
        val EMPTY = build(emptyList())

        private val DIACRITICS = Regex("\\p{InCombiningDiacriticalMarks}+")

        /** Lower-cases and strips accents, so "Beyoncé" matches "beyonce". */
        fun normalize(s: String): String =
            DIACRITICS.replace(Normalizer.normalize(s, Normalizer.Form.NFD), "").lowercase().trim()

        /** Sort key that ignores a leading "The "/"A " and case/accents. */
        fun sortKey(s: String): String {
            val n = normalize(s)
            return when {
                n.startsWith("the ") -> n.substring(4)
                n.startsWith("a ") && n.length > 2 -> n.substring(2)
                else -> n
            }
        }

        fun build(input: List<Track>): LibraryIndex {
            // De-duplicate by id (a file can be both scanned and imported).
            val unique = LinkedHashMap<String, Track>()
            for (t in input) unique.putIfAbsent(t.id, t)
            val tracks = unique.values.sortedWith(compareBy({ sortKey(it.title) }, { sortKey(it.artist) }))

            val albums = tracks
                .groupBy { normalize(it.album) + "\u0000" + normalize(it.artist) }
                .map { (key, list) ->
                    val ordered = list.sortedWith(compareBy({ it.trackNumber.takeIf { n -> n > 0 } ?: Int.MAX_VALUE }, { sortKey(it.title) }))
                    val first = ordered.first()
                    Album(
                        key = key,
                        title = first.album,
                        artist = first.artist,
                        year = ordered.maxOf { it.year },
                        albumId = first.albumId,
                        tracks = ordered,
                    )
                }
                .sortedWith(compareBy({ sortKey(it.title) }, { sortKey(it.artist) }))

            val artists = albums
                .groupBy { normalize(it.artist) }
                .map { (_, list) -> Artist(name = list.first().artist, albums = list) }
                .sortedBy { sortKey(it.name) }

            val keys = tracks.map { it to normalize(it.title + " " + it.artist + " " + it.album) }
            return LibraryIndex(tracks, albums, artists, unique, keys)
        }
    }
}
