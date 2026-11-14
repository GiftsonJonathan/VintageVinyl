package com.vintagevinyl.domain

import com.vintagevinyl.domain.library.LibraryIndex
import com.vintagevinyl.domain.library.PlaylistCodec
import com.vintagevinyl.domain.library.TrackCodec
import com.vintagevinyl.domain.model.TrackSource
import com.vintagevinyl.domain.model.PlayerState
import com.vintagevinyl.domain.model.Playlist
import com.vintagevinyl.domain.model.RepeatMode
import com.vintagevinyl.domain.model.Track
import com.vintagevinyl.domain.sound.PresetBook
import com.vintagevinyl.domain.sound.SettingsCodec
import com.vintagevinyl.dsp.EffectId
import com.vintagevinyl.dsp.EqProfile
import com.vintagevinyl.dsp.RecordCondition
import com.vintagevinyl.dsp.StereoMode
import com.vintagevinyl.dsp.VintagePresets
import com.vintagevinyl.dsp.VintageSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainTests {

    private fun t(id: Int, title: String, artist: String, album: String, no: Int = 0, year: Int = 0, ms: Long = 180_000) =
        Track("ms:$id", "content://media/$id", title, artist, album, albumId = album.hashCode().toLong(), durationMs = ms, trackNumber = no, year = year, mimeType = "audio/flac")

    private val sample = listOf(
        t(1, "So What", "Miles Davis", "Kind of Blue", 1, 1959),
        t(2, "Freddie Freeloader", "Miles Davis", "Kind of Blue", 2, 1959),
        t(3, "Blue in Green", "Miles Davis", "Kind of Blue", 3, 1959),
        t(4, "Take Five", "The Dave Brubeck Quartet", "Time Out", 3, 1959),
        t(5, "Café del Mar", "Énergie", "Sunsets"),
        t(6, "A Love Supreme, Pt. I", "John Coltrane", "A Love Supreme", 1, 1965),
    )

    // ------------------------------------------------------------------ settings codec

    @Test
    fun settingsRoundTrip_forEveryPreset() {
        for (p in VintagePresets.ALL) {
            val decoded = SettingsCodec.decode(SettingsCodec.encode(p.settings))
            assertTrue("${p.name} changed after round trip", PresetBook.sameSound(p.settings, decoded))
            assertEquals(p.settings.eqProfile, decoded.eqProfile)
            assertEquals(p.settings.stereoMode, decoded.stereoMode)
            assertEquals(p.settings.recordCondition, decoded.recordCondition)
            assertEquals(p.settings.turntableSpeed, decoded.turntableSpeed)
        }
    }

    @Test
    fun settingsRoundTrip_preservesDisabledEffectAmounts() {
        val s = VintageSettings.DEFAULT.withAmount(EffectId.WOW, 0.77f).withEnabled(EffectId.WOW, false)
        val d = SettingsCodec.decode(SettingsCodec.encode(s))
        assertFalse(d.isEnabled(EffectId.WOW))
        assertEquals(0.77f, d.rawAmount(EffectId.WOW), 0.001f)
        assertEquals(0f, d.amount(EffectId.WOW), 0f)
    }

    @Test
    fun settingsDecode_isTolerant() {
        val d = VintageSettings.DEFAULT
        assertEquals(d, SettingsCodec.decode(null))
        assertEquals(d, SettingsCodec.decode(""))
        assertEquals(d, SettingsCodec.decode("garbage"))
        assertEquals(d, SettingsCodec.decode("v9|master=0"))
        // unknown effect, bad number, unknown enum: ignored; valid parts still applied
        val s = SettingsCodec.decode("v1|master=0|eq=NOPE|stereo=MONO|fx=NOT_AN_EFFECT:0.5:1,WOW:abc:1,FLUTTER:0.400:1")
        assertFalse(s.masterEnabled)
        assertEquals(StereoMode.MONO, s.stereoMode)
        assertEquals(d.eqProfile, s.eqProfile)
        assertEquals(0.4f, s.amount(EffectId.FLUTTER), 0.001f)
    }

    @Test
    fun customPresetsRoundTrip_withAwkwardNames() {
        val s = VintagePresets.DUSTY_VINYL.settings.copy(eqProfile = EqProfile.VINTAGE_RADIO, recordCondition = RecordCondition.WORN)
        val p = PresetBook.createCustom("Grandpa's | radio\ttab, \"quotes\" é中", s, emptyList()) { "custom-1" }
        val decoded = SettingsCodec.decodePresets(SettingsCodec.encodePresets(listOf(p, p.copy(id = "custom-2", name = "Two"))))
        assertEquals(2, decoded.size)
        assertEquals(p.name, decoded[0].name)
        assertEquals("custom-1", decoded[0].id)
        assertFalse(decoded[0].builtIn)
        assertTrue(PresetBook.sameSound(s, decoded[0].settings))
        assertEquals(emptyList<Any>(), SettingsCodec.decodePresets(null))
        assertEquals(emptyList<Any>(), SettingsCodec.decodePresets("not\ta\tvalid"))
    }

    // ------------------------------------------------------------------ preset book

    @Test
    fun presetBook_matchingAndNaming() {
        assertEquals("clean_vinyl", PresetBook.matching(VintagePresets.CLEAN_VINYL.settings, emptyList())?.id)
        val tweaked = VintagePresets.CLEAN_VINYL.settings.withAmount(EffectId.WARMTH, 0.9f)
        assertNull(PresetBook.matching(tweaked, emptyList()))

        val first = PresetBook.createCustom("  Cozy  ", tweaked, emptyList()) { "a" }
        assertEquals("Cozy", first.name)
        val second = PresetBook.createCustom("cozy", tweaked, listOf(first)) { "b" }
        assertEquals("cozy (2)", second.name)
        assertEquals("Clean Vinyl (2)", PresetBook.createCustom("Clean Vinyl", tweaked, emptyList()) { "c" }.name)
        assertEquals("My preset", PresetBook.createCustom("   ", tweaked, emptyList()) { "d" }.name)
        // A saved custom preset is found again as the match.
        assertEquals("a", PresetBook.matching(tweaked, listOf(first))?.id)
    }

    @Test
    fun sameSound_ignoresAmountsOfDisabledEffects() {
        val a = VintageSettings.DEFAULT.withAmount(EffectId.WOW, 0.2f).withEnabled(EffectId.WOW, false)
        val b = VintageSettings.DEFAULT.withAmount(EffectId.WOW, 0.9f).withEnabled(EffectId.WOW, false)
        assertTrue(PresetBook.sameSound(a, b))
        assertFalse(PresetBook.sameSound(a.withEnabled(EffectId.WOW, true), b.withEnabled(EffectId.WOW, true)))
    }

    // ------------------------------------------------------------------ library index

    @Test
    fun index_groupsAlbumsAndArtists() {
        val idx = LibraryIndex.build(sample)
        assertEquals(6, idx.tracks.size)
        assertEquals(4, idx.albums.size)
        val kind = idx.albums.first { it.title == "Kind of Blue" }
        assertEquals(listOf("So What", "Freddie Freeloader", "Blue in Green"), kind.tracks.map { it.title }) // track order
        assertEquals(1959, kind.year)
        assertEquals(540_000, kind.durationMs)
        assertEquals(4, idx.artists.size)
        assertEquals(3, idx.artists.first { it.name == "Miles Davis" }.tracks.size)
    }

    @Test
    fun index_sortsIgnoringArticlesAndAccents() {
        val idx = LibraryIndex.build(sample)
        // "The Dave Brubeck Quartet" sorts under D, "Énergie" under E, "A Love Supreme" album under L.
        assertEquals(listOf("Énergie", "John Coltrane", "Miles Davis", "The Dave Brubeck Quartet").sortedBy { LibraryIndex.sortKey(it) }, idx.artists.map { it.name }.sortedBy { LibraryIndex.sortKey(it) })
        assertEquals("The Dave Brubeck Quartet", idx.artists.first { LibraryIndex.sortKey(it.name).startsWith("dave") }.name)
        val order = idx.artists.map { it.name }
        assertTrue(order.indexOf("The Dave Brubeck Quartet") < order.indexOf("Énergie"))
        assertTrue(order.indexOf("Énergie") < order.indexOf("John Coltrane"))
        assertTrue(order.indexOf("John Coltrane") < order.indexOf("Miles Davis"))
    }

    @Test
    fun search_isCaseAccentInsensitiveAndMultiWord() {
        val idx = LibraryIndex.build(sample)
        assertEquals(listOf("Café del Mar"), idx.search("cafe").map { it.title })
        assertEquals(listOf("Café del Mar"), idx.search("ENERGIE sunsets").map { it.title })
        assertEquals(3, idx.search("miles").size)
        assertEquals(listOf("Blue in Green"), idx.search("blue green miles").map { it.title })
        assertEquals(6, idx.search("   ").size)
        assertEquals(0, idx.search("zzzz").size)
        assertEquals(1, idx.searchAlbums("time out").size)
        assertEquals(1, idx.searchArtists("brubeck").size)
    }

    @Test
    fun index_deduplicatesAndResolvesIds() {
        val idx = LibraryIndex.build(sample + sample.take(2))
        assertEquals(6, idx.tracks.size)
        assertNotNull(idx.track("ms:3"))
        assertNull(idx.track("ms:999"))
        assertEquals(listOf("ms:2", "ms:4"), idx.tracks(listOf("ms:2", "gone", "ms:4")).map { it.id })
        assertEquals(0, LibraryIndex.EMPTY.tracks.size)
    }

    // ------------------------------------------------------------------ playlists & models

    @Test
    fun playlistsRoundTrip_withNastyNames() {
        val lists = listOf(
            Playlist("p1", "Sunday morning, slow\tjazz | \"quotes\"", listOf("ms:1", "content://a/b?c=d&e=f,g", "ms:3"), 1_700_000_000_000),
            Playlist("p2", "Empty", emptyList(), 5),
        )
        val back = PlaylistCodec.decode(PlaylistCodec.encode(lists))
        assertEquals(lists, back)
        assertEquals(emptyList<Playlist>(), PlaylistCodec.decode(null))
        assertEquals(emptyList<Playlist>(), PlaylistCodec.decode("broken line"))
    }

    @Test
    fun importedTracksRoundTrip() {
        val a = Track(
            "content://com.android.providers.downloads/document/1%3A2", "content://com.android.providers.downloads/document/1%3A2",
            "Tab\there, \"quoted\" \u00e9", "Artist | X", "", 0, 123_456, 7, 1969, null, TrackSource.IMPORTED,
        )
        val b = a.copy(id = "second", mimeType = "audio/flac")
        val back = TrackCodec.decode(TrackCodec.encode(listOf(a, b)))
        assertEquals(listOf(a, b), back)
        assertEquals(emptyList<Track>(), TrackCodec.decode(null))
        assertEquals(emptyList<Track>(), TrackCodec.decode("only\ttwo"))
    }

    @Test
    fun repeatModeCycles_andPlayerStateProgress() {
        assertEquals(RepeatMode.ALL, RepeatMode.OFF.next())
        assertEquals(RepeatMode.ONE, RepeatMode.ALL.next())
        assertEquals(RepeatMode.OFF, RepeatMode.ONE.next())
        assertEquals(0.25f, PlayerState(positionMs = 25, durationMs = 100).progress, 1e-6f)
        assertEquals(0f, PlayerState(positionMs = 25, durationMs = 0).progress, 0f)
        assertEquals(1f, PlayerState(positionMs = 250, durationMs = 100).progress, 0f)
    }

    @Test
    fun formatLabels() {
        fun fmt(m: String?) = t(1, "x", "y", "z").copy(mimeType = m).formatLabel
        assertEquals("FLAC", fmt("audio/flac"))
        assertEquals("MP3", fmt("audio/mpeg"))
        assertEquals("AAC", fmt("audio/mp4"))
        assertEquals("WAV", fmt("audio/x-wav"))
        assertEquals("OGG", fmt("audio/ogg"))
        assertEquals("AMR", fmt("audio/amr"))
        assertEquals("", fmt(null))
    }
}
