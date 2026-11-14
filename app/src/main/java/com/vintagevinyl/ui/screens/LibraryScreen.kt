package com.vintagevinyl.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vintagevinyl.data.LibraryRepository
import com.vintagevinyl.domain.model.Album
import com.vintagevinyl.domain.model.Artist
import com.vintagevinyl.domain.model.Playlist
import com.vintagevinyl.domain.model.Track
import com.vintagevinyl.domain.model.TrackSource
import com.vintagevinyl.ui.components.ArtworkImage
import com.vintagevinyl.ui.components.BrassButton
import com.vintagevinyl.ui.components.ChipRow
import com.vintagevinyl.ui.components.ConfirmDialog
import com.vintagevinyl.ui.components.Glyph
import com.vintagevinyl.ui.components.GlyphIcon
import com.vintagevinyl.ui.components.MenuAction
import com.vintagevinyl.ui.components.OverflowMenu
import com.vintagevinyl.ui.components.PlaylistPickerDialog
import com.vintagevinyl.ui.components.SelectorChip
import com.vintagevinyl.ui.components.TextInputDialog
import com.vintagevinyl.ui.components.TrackRow
import com.vintagevinyl.ui.theme.VV
import com.vintagevinyl.ui.viewmodel.LibraryViewModel

/** Which drill-down view (if any) is open on top of the tabs. */
private sealed interface Detail {
    data class OfAlbum(val key: String) : Detail
    data class OfArtist(val name: String) : Detail
    data class OfPlaylist(val id: String) : Detail
}

private val tabs = listOf("Songs", "Albums", "Artists", "Playlists")

@Composable
fun LibraryScreen(vm: LibraryViewModel) {
    val index by vm.index.collectAsStateWithLifecycle()
    val songs by vm.songs.collectAsStateWithLifecycle()
    val albums by vm.albums.collectAsStateWithLifecycle()
    val artists by vm.artists.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val scanning by vm.scanning.collectAsStateWithLifecycle()
    val hasPermission by vm.hasPermission.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val failed by vm.importFailures.collectAsStateWithLifecycle()
    val currentId by vm.currentTrackId.collectAsStateWithLifecycle(initialValue = null)

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var detail by remember { mutableStateOf<Detail?>(null) }
    var pickerTracks by remember { mutableStateOf<List<Track>?>(null) }
    var newPlaylistFor by remember { mutableStateOf<List<Track>?>(null) }
    var creatingEmptyPlaylist by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.onPermissionResult() }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) vm.import(uris)
    }
    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(LibraryRepository.audioPermission())
    }
    BackHandler(enabled = detail != null) { detail = null }

    fun rowActions(t: Track, extra: List<MenuAction> = emptyList()): List<MenuAction> = buildList {
        add(MenuAction("Play next") { vm.playNext(t) })
        add(MenuAction("Add to queue") { vm.enqueue(listOf(t)) })
        add(MenuAction("Add to playlist…") { pickerTracks = listOf(t) })
        if (t.source == TrackSource.IMPORTED) add(MenuAction("Remove imported file") { vm.removeImported(t.id) })
        addAll(extra)
    }

    Column(Modifier.fillMaxSize()) {
        val open = detail
        if (open != null) {
            when (open) {
                is Detail.OfAlbum -> index.albums.firstOrNull { it.key == open.key }?.let { album ->
                    TrackListDetail(
                        title = album.title, subtitle = "${album.artist}  ·  ${album.tracks.size} songs",
                        tracks = album.tracks, currentId = currentId, onBack = { detail = null },
                        onPlay = { vm.play(album.tracks, it) }, onShuffle = { vm.shuffle(album.tracks) },
                        headerActions = listOf(
                            MenuAction("Add to queue") { vm.enqueue(album.tracks) },
                            MenuAction("Add to playlist…") { pickerTracks = album.tracks },
                        ),
                        rowActions = { rowActions(it) },
                    )
                } ?: run { detail = null }

                is Detail.OfArtist -> index.artists.firstOrNull { it.name == open.name }?.let { artist ->
                    TrackListDetail(
                        title = artist.name, subtitle = "${artist.albums.size} albums  ·  ${artist.tracks.size} songs",
                        tracks = artist.tracks, currentId = currentId, onBack = { detail = null },
                        onPlay = { vm.play(artist.tracks, it) }, onShuffle = { vm.shuffle(artist.tracks) },
                        headerActions = listOf(MenuAction("Add to queue") { vm.enqueue(artist.tracks) }),
                        rowActions = { rowActions(it) },
                    )
                } ?: run { detail = null }

                is Detail.OfPlaylist -> playlists.firstOrNull { it.id == open.id }?.let { playlist ->
                    PlaylistDetail(vm, playlist, index.tracks(playlist.trackIds), currentId, { detail = null }) { t -> rowActions(t) }
                } ?: run { detail = null }
            }
        } else {
            // ---- header -------------------------------------------------------------------
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("LIBRARY", style = MaterialTheme.typography.titleLarge, color = VV.Gold)
                    Text(
                        if (scanning) "Scanning…" else "${index.tracks.size} songs  ·  ${index.albums.size} albums  ·  ${index.artists.size} artists",
                        style = MaterialTheme.typography.bodySmall, color = VV.CreamDim,
                    )
                }
                BrassButton({ importLauncher.launch(arrayOf("audio/*")) }, "Import audio files", buttonSize = 44.dp) {
                    GlyphIcon(Glyph.IMPORT, Modifier.size(22.dp), VV.Cream)
                }
            }
            OutlinedTextField(
                value = query, onValueChange = vm::setQuery, singleLine = true,
                placeholder = { Text("Search songs, albums, artists") },
                leadingIcon = { GlyphIcon(Glyph.SEARCH, Modifier.size(20.dp), VV.CreamDim) },
                trailingIcon = if (query.isNotEmpty()) {
                    { Box(Modifier.size(40.dp).clickable { vm.setQuery("") }, contentAlignment = Alignment.Center) { GlyphIcon(Glyph.CLOSE, Modifier.size(18.dp), VV.CreamDim) } }
                } else null,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = VV.Cream, unfocusedTextColor = VV.Cream, cursorColor = VV.Gold,
                    focusedBorderColor = VV.Gold, unfocusedBorderColor = VV.GoldDim.copy(alpha = 0.6f),
                    focusedPlaceholderColor = VV.CreamDim, unfocusedPlaceholderColor = VV.CreamDim,
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )

            if (!hasPermission) {
                Banner("Allow access to your music to browse it here, or import individual files.", "ALLOW") {
                    permissionLauncher.launch(LibraryRepository.audioPermission())
                }
            }
            if (failed > 0) {
                Banner("$failed file(s) could not be read and were not imported.", "")
            }

            ChipRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                tabs.forEachIndexed { i, name -> SelectorChip(name, tab == i, { tab = i }) }
            }

            when (tab) {
                0 -> SongsTab(songs, currentId, vm, { t, x -> rowActions(t, x) }, hasPermission, query.isNotBlank())
                1 -> AlbumsTab(albums) { detail = Detail.OfAlbum(it.key) }
                2 -> ArtistsTab(artists) { detail = Detail.OfArtist(it.name) }
                else -> PlaylistsTab(playlists, { detail = Detail.OfPlaylist(it.id) }, { creatingEmptyPlaylist = true })
            }
        }
    }

    // ---- dialogs ----------------------------------------------------------------------------
    pickerTracks?.let { tracks ->
        PlaylistPickerDialog(
            playlists = playlists,
            onPick = { p -> vm.addToPlaylist(p.id, tracks.map { it.id }); pickerTracks = null },
            onCreateNew = { newPlaylistFor = tracks; pickerTracks = null },
            onDismiss = { pickerTracks = null },
        )
    }
    newPlaylistFor?.let { tracks ->
        TextInputDialog(
            title = "New playlist", confirmLabel = "Create", hint = "Playlist name",
            onDismiss = { newPlaylistFor = null },
            onConfirm = { name -> vm.createPlaylist(name.trim(), tracks.map { it.id }); newPlaylistFor = null },
        )
    }
    if (creatingEmptyPlaylist) {
        TextInputDialog(
            title = "New playlist", confirmLabel = "Create", hint = "Playlist name",
            onDismiss = { creatingEmptyPlaylist = false },
            onConfirm = { name -> vm.createPlaylist(name.trim()); creatingEmptyPlaylist = false },
        )
    }
}

@Composable
private fun Banner(text: String, actionLabel: String, onAction: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp)).background(VV.WalnutMid).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = VV.Cream, modifier = Modifier.weight(1f))
        if (onAction != null && actionLabel.isNotEmpty()) TextButton(onClick = onAction) { Text(actionLabel, color = VV.Gold) }
    }
}

@Composable
private fun EmptyNote(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.TopCenter) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = VV.CreamDim)
    }
}

@Composable
private fun SongsTab(
    songs: List<Track>, currentId: String?, vm: LibraryViewModel,
    rowActions: (Track, List<MenuAction>) -> List<MenuAction>, hasPermission: Boolean, searching: Boolean,
) {
    if (songs.isEmpty()) {
        EmptyNote(
            when {
                searching -> "Nothing matches your search."
                !hasPermission -> "No music yet. Allow access or import files with the button above."
                else -> "No music found on this device. Import files with the button above."
            }
        )
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
        item(key = "actions") {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectorChip("▶  PLAY ALL", false, { vm.play(songs) })
                SelectorChip("SHUFFLE", false, { vm.shuffle(songs) })
            }
        }
        items(songs, key = { it.id }) { track ->
            TrackRow(
                track, isCurrent = track.id == currentId,
                onClick = { vm.play(songs, songs.indexOfFirst { it.id == track.id }.coerceAtLeast(0)) },
                actions = rowActions(track, emptyList()),
            )
        }
    }
}

@Composable
private fun AlbumsTab(albums: List<Album>, onOpen: (Album) -> Unit) {
    if (albums.isEmpty()) { EmptyNote("No albums."); return }
    LazyVerticalGrid(
        GridCells.Adaptive(150.dp), Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(albums, key = { it.key }) { album ->
            Column(Modifier.clickable { onOpen(album) }) {
                ArtworkImage(album.tracks.firstOrNull(), Modifier.fillMaxWidth(), size = 150.dp, corner = 8.dp)
                Text(album.title, style = MaterialTheme.typography.titleSmall, color = VV.Cream, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                Text(album.artist, style = MaterialTheme.typography.bodySmall, color = VV.CreamDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ArtistsTab(artists: List<Artist>, onOpen: (Artist) -> Unit) {
    if (artists.isEmpty()) { EmptyNote("No artists."); return }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
        items(artists, key = { it.name }) { artist ->
            Row(
                Modifier.fillMaxWidth().clickable { onOpen(artist) }.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ArtworkImage(artist.albums.firstOrNull()?.tracks?.firstOrNull(), size = 48.dp, corner = 24.dp)
                Column(Modifier.weight(1f)) {
                    Text(artist.name, style = MaterialTheme.typography.titleSmall, color = VV.Cream, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${artist.albums.size} albums  ·  ${artist.tracks.size} songs", style = MaterialTheme.typography.bodySmall, color = VV.CreamDim)
                }
            }
        }
    }
}

@Composable
private fun PlaylistsTab(playlists: List<Playlist>, onOpen: (Playlist) -> Unit, onCreate: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
        item(key = "new") {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) { SelectorChip("+ NEW PLAYLIST", false, onCreate) }
        }
        if (playlists.isEmpty()) {
            item(key = "empty") {
                Text("No playlists yet. Create one here, or use “Add to playlist” on any song.", style = MaterialTheme.typography.bodyMedium, color = VV.CreamDim, modifier = Modifier.padding(16.dp))
            }
        }
        items(playlists, key = { it.id }) { p ->
            Row(
                Modifier.fillMaxWidth().clickable { onOpen(p) }.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)).background(VV.WalnutMid), contentAlignment = Alignment.Center) {
                    GlyphIcon(Glyph.QUEUE, Modifier.size(24.dp), VV.Gold)
                }
                Column(Modifier.weight(1f)) {
                    Text(p.name, style = MaterialTheme.typography.titleSmall, color = VV.Cream, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${p.trackIds.size} songs", style = MaterialTheme.typography.bodySmall, color = VV.CreamDim)
                }
            }
        }
    }
}

/** Shared album / artist detail: header with play and shuffle, then the songs. */
@Composable
private fun TrackListDetail(
    title: String, subtitle: String, tracks: List<Track>, currentId: String?,
    onBack: () -> Unit, onPlay: (Int) -> Unit, onShuffle: () -> Unit,
    headerActions: List<MenuAction>, rowActions: (Track) -> List<MenuAction>,
) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            BrassButton(onBack, "Back", buttonSize = 44.dp) { GlyphIcon(Glyph.CHEVRON_LEFT, Modifier.size(22.dp), VV.Cream) }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = VV.Gold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = VV.CreamDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (headerActions.isNotEmpty()) OverflowMenu(headerActions)
        }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectorChip("▶  PLAY", false,{ onPlay(0) })
            SelectorChip("SHUFFLE", false, onShuffle)
        }
        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(tracks, key = { i, t -> "$i:${t.id}" }) { i, t ->
                TrackRow(t, t.id == currentId, onClick = { onPlay(i) }, actions = rowActions(t))
            }
        }
    }
}

@Composable
private fun PlaylistDetail(
    vm: LibraryViewModel, playlist: Playlist, tracks: List<Track>, currentId: String?,
    onBack: () -> Unit, rowActions: (Track) -> List<MenuAction>,
) {
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            BrassButton(onBack, "Back", buttonSize = 44.dp) { GlyphIcon(Glyph.CHEVRON_LEFT, Modifier.size(22.dp), VV.Cream) }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(playlist.name, style = MaterialTheme.typography.titleLarge, color = VV.Gold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${tracks.size} songs", style = MaterialTheme.typography.bodySmall, color = VV.CreamDim)
            }
            OverflowMenu(
                listOf(
                    MenuAction("Add to queue") { vm.enqueue(tracks) },
                    MenuAction("Rename\u2026") { renaming = true },
                    MenuAction("Delete playlist") { deleting = true },
                )
            )
        }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectorChip("\u25B6  PLAY", false, { vm.play(tracks) })
            SelectorChip("SHUFFLE", false, { vm.shuffle(tracks) })
        }
        if (tracks.isEmpty()) EmptyNote("This playlist is empty. Use \u201CAdd to playlist\u201D on any song.")
        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(tracks, key = { i, t -> "$i:${t.id}" }) { i, t ->
                TrackRow(
                    t, t.id == currentId, onClick = { vm.play(tracks, i) },
                    actions = rowActions(t) + MenuAction("Remove from playlist") { vm.removeFromPlaylist(playlist.id, t.id) },
                )
            }
        }
    }
    if (renaming) {
        TextInputDialog(
            title = "Rename playlist", confirmLabel = "Rename", initial = playlist.name, hint = "Playlist name",
            onDismiss = { renaming = false },
            onConfirm = { n -> vm.renamePlaylist(playlist.id, n.trim()); renaming = false },
        )
    }
    if (deleting) {
        ConfirmDialog(
            title = "Delete playlist?", message = "\u201C${playlist.name}\u201D will be deleted. Your songs are not affected.", confirmLabel = "Delete",
            onDismiss = { deleting = false },
            onConfirm = { vm.deletePlaylist(playlist.id); deleting = false },
        )
    }
}
