package com.vintagevinyl.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vintagevinyl.domain.model.PlayerState
import com.vintagevinyl.domain.model.Track
import com.vintagevinyl.dsp.TurntableSpeed
import com.vintagevinyl.dsp.VintagePreset
import com.vintagevinyl.ui.components.BrassButton
import com.vintagevinyl.ui.components.ChipRow
import com.vintagevinyl.ui.components.Glyph
import com.vintagevinyl.ui.components.GlyphIcon
import com.vintagevinyl.ui.components.PlayPauseButton
import com.vintagevinyl.ui.components.SelectorChip
import com.vintagevinyl.ui.components.ToggleSwitch
import com.vintagevinyl.ui.theme.VV

/** Brand line and the master "vintage" switch. */
@Composable
fun DeckHeader(masterOn: Boolean, onMasterChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("VINTAGE VINYL", style = MaterialTheme.typography.titleLarge, color = VV.Gold, letterSpacing = 3.sp)
            Text("Turntable", style = MaterialTheme.typography.bodySmall, color = VV.CreamDim)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ToggleSwitch(masterOn, onMasterChange, contentLabel = "Vintage sound processing")
            Text(if (masterOn) "VINTAGE ON" else "BYPASS", style = MaterialTheme.typography.labelSmall, color = if (masterOn) VV.Lamp else VV.CreamDim)
        }
    }
}

/** Title, artist and album of the record on the platter, or an invitation to pick one. */
@Composable
fun Plaque(track: Track?, state: PlayerState, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (!state.hasTrack) {
            Text("No record on the platter", style = MaterialTheme.typography.titleLarge, color = VV.Cream, textAlign = TextAlign.Center)
            Text("Choose something from the library", style = MaterialTheme.typography.bodySmall, color = VV.CreamDim, textAlign = TextAlign.Center)
            return@Column
        }
        val title = track?.title ?: state.title
        val artist = track?.artist ?: state.artist
        val album = track?.album ?: state.album
        Text(title, style = MaterialTheme.typography.headlineSmall, color = VV.Cream, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        Spacer(Modifier.height(2.dp))
        Text(artist, style = MaterialTheme.typography.titleMedium, color = VV.Copper, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        val detail = listOfNotNull(album.takeIf { it.isNotBlank() }, track?.formatLabel?.takeIf { it.isNotEmpty() }).joinToString("  ·  ")
        if (detail.isNotEmpty()) {
            Text(detail, style = MaterialTheme.typography.bodySmall, color = VV.CreamDim, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
        if (state.error != null) {
            Spacer(Modifier.height(4.dp))
            Text(state.error!!, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error, maxLines = 2, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun TransportRow(
    state: PlayerState,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
        BrassButton(onPrevious, "Previous track", buttonSize = 52.dp) { GlyphIcon(Glyph.PREV, Modifier.size(24.dp), VV.Cream) }
        PlayPauseButton(state.isPlaying, onPlayPause, buttonSize = 72.dp)
        BrassButton(onNext, "Next track", buttonSize = 52.dp) { GlyphIcon(Glyph.NEXT, Modifier.size(24.dp), VV.Cream) }
    }
}

/** A scrolling row of preset chips. The active one is highlighted. */
@Composable
fun PresetStrip(presets: List<VintagePreset>, activeId: String?, onPick: (VintagePreset) -> Unit, modifier: Modifier = Modifier) {
    ChipRow(modifier.fillMaxWidth()) {
        presets.forEach { p -> SelectorChip(p.name, selected = p.id == activeId, onClick = { onPick(p) }) }
    }
}

@Composable
fun SpeedSelector(current: TurntableSpeed, onPick: (TurntableSpeed) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        TurntableSpeed.values().forEach { s -> SelectorChip(s.label, selected = s == current, onClick = { onPick(s) }) }
    }
}

/** Whole-percent playback progress, as used by the tonearm. */
fun progressPercent(positionMs: Long, durationMs: Long): Int =
    if (durationMs > 0) ((positionMs * 100) / durationMs).toInt().coerceIn(0, 100) else 0
