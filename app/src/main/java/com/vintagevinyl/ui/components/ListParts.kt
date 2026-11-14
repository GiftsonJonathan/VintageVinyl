package com.vintagevinyl.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vintagevinyl.data.ArtworkRepository
import com.vintagevinyl.domain.model.Playlist
import com.vintagevinyl.domain.model.Track
import com.vintagevinyl.ui.theme.VV

/** Provided once by MainActivity; avoids threading the repository through every screen. */
val LocalArtwork = staticCompositionLocalOf<ArtworkRepository> { error("ArtworkRepository not provided") }

/** Square album art with a note-glyph placeholder while loading or when the file has none. */
@Composable
fun ArtworkImage(track: Track?, modifier: Modifier = Modifier, size: Dp = 48.dp, corner: Dp = 6.dp) {
    val repo = LocalArtwork.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    val bitmap: ImageBitmap? by produceState<ImageBitmap?>(null, track?.id) {
        value = track?.let { repo.load(it, px)?.asImageBitmap() }
    }
    Box(
        modifier.size(size).clip(RoundedCornerShape(corner)).background(VV.WalnutMid),
        contentAlignment = Alignment.Center,
    ) {
        val b = bitmap
        if (b != null) Image(b, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else GlyphIcon(Glyph.NOTE, Modifier.size(size * 0.5f), VV.GoldDim)
    }
}

/** One entry of a row's overflow menu. */
data class MenuAction(val label: String, val onClick: () -> Unit)

@Composable
fun OverflowMenu(actions: List<MenuAction>, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(20.dp)).clickable { open = true },
            contentAlignment = Alignment.Center,
        ) { GlyphIcon(Glyph.MORE, Modifier.size(20.dp), VV.CreamDim) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = VV.WalnutMid) {
            actions.forEach { a ->
                DropdownMenuItem(
                    text = { Text(a.label, color = VV.Cream) },
                    onClick = { open = false; a.onClick() },
                )
            }
        }
    }
}

@Composable
fun TrackRow(
    track: Track,
    isCurrent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    actions: List<MenuAction> = emptyList(),
) {
    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ArtworkImage(track, size = 48.dp)
        Column(Modifier.weight(1f)) {
            Text(
                track.title, style = MaterialTheme.typography.titleSmall,
                color = if (isCurrent) VV.Gold else VV.Cream, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            val sub = buildString {
                append(track.artist)
                if (track.album.isNotBlank()) append("  ·  ").append(track.album)
            }
            Text(sub, style = MaterialTheme.typography.bodySmall, color = VV.CreamDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(formatTime(track.durationMs), style = MaterialTheme.typography.labelMedium, color = VV.CreamDim)
            if (track.formatLabel.isNotEmpty()) {
                Text(track.formatLabel, style = MaterialTheme.typography.labelSmall, color = VV.GoldDim)
            }
        }
        if (actions.isNotEmpty()) OverflowMenu(actions)
    }
}

@Composable
fun TextInputDialog(
    title: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    initial: String = "",
    hint: String = "Name",
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VV.Walnut,
        titleContentColor = VV.Cream,
        textContentColor = VV.Cream,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { text = it.take(60) }, singleLine = true,
                label = { Text(hint) },
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) {
                Text(confirmLabel.uppercase(), color = if (text.isNotBlank()) VV.Gold else VV.GoldDim)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL", color = VV.CreamDim) } },
    )
}

@Composable
fun ConfirmDialog(title: String, message: String, confirmLabel: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VV.Walnut,
        titleContentColor = VV.Cream,
        textContentColor = VV.CreamDim,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel.uppercase(), color = VV.Gold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL", color = VV.CreamDim) } },
    )
}

/** "Add to playlist": pick an existing playlist or start a new one. */
@Composable
fun PlaylistPickerDialog(
    playlists: List<Playlist>,
    onPick: (Playlist) -> Unit,
    onCreateNew: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VV.Walnut,
        titleContentColor = VV.Cream,
        title = { Text("Add to playlist", style = MaterialTheme.typography.titleLarge) },
        text = {
            LazyColumn(Modifier.heightIn(max = 320.dp)) {
                items(playlists, key = { it.id }) { p ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(p) }.padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(p.name, color = VV.Cream, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text("${p.trackIds.size}", color = VV.CreamDim, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onCreateNew) { Text("NEW PLAYLIST", color = VV.Gold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL", color = VV.CreamDim) } },
    )
}
