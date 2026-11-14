@file:OptIn(ExperimentalMaterial3Api::class)

package com.vintagevinyl.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vintagevinyl.domain.model.PlayerState
import com.vintagevinyl.domain.model.RepeatMode
import com.vintagevinyl.ui.components.BrassButton
import com.vintagevinyl.ui.components.Glyph
import com.vintagevinyl.ui.components.GlyphIcon
import com.vintagevinyl.ui.components.HorizontalFader
import com.vintagevinyl.ui.components.SectionLabel
import com.vintagevinyl.ui.components.SeekSection
import com.vintagevinyl.ui.components.ToggleSwitch
import com.vintagevinyl.ui.components.TurntableDeck
import com.vintagevinyl.ui.theme.VV
import com.vintagevinyl.ui.viewmodel.PlayerViewModel

/**
 * The immersive listening screen: a big deck, full transport (shuffle, repeat, volume), the
 * turntable speed, the vintage switch and the queue.
 */
@Composable
fun NowPlayingScreen(player: PlayerViewModel, onBack: () -> Unit) {
    val state by player.state.collectAsStateWithLifecycle()
    val track by player.currentTrack.collectAsStateWithLifecycle()
    val art by player.artwork.collectAsStateWithLifecycle()
    val settings by player.settings.collectAsStateWithLifecycle()
    val presets by player.presets.collectAsStateWithLifecycle()
    val activeId by player.activePresetId.collectAsStateWithLifecycle()
    var showQueue by remember { mutableStateOf(false) }

    val positionFlow = remember(player) { player.position }
    val positionState: State<Long> = positionFlow.collectAsStateWithLifecycle(initialValue = 0L)
    val percent by remember { derivedStateOf { progressPercent(positionState.value, state.durationMs) } }

    val topBar: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BrassButton(onBack, "Back", buttonSize = 44.dp) { GlyphIcon(Glyph.CHEVRON_DOWN, Modifier.size(22.dp), VV.Cream) }
            Text(
                "NOW PLAYING", style = MaterialTheme.typography.labelLarge, color = VV.Gold,
                modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            BrassButton({ showQueue = true }, "Show queue", buttonSize = 44.dp) { GlyphIcon(Glyph.QUEUE, Modifier.size(22.dp), VV.Cream) }
        }
    }

    val controls: @Composable () -> Unit = {
        Plaque(track, state)
        SeekSection({ positionState.value }, state.durationMs, state.hasTrack, player::seekTo)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            BrassButton(player::toggleShuffle, if (state.shuffle) "Shuffle on" else "Shuffle off", buttonSize = 46.dp) {
                GlyphIcon(Glyph.SHUFFLE, Modifier.size(22.dp), if (state.shuffle) VV.Gold else VV.CreamDim)
            }
            TransportRow(state, player::previous, player::togglePlay, player::next, Modifier.weight(1f))
            BrassButton(player::cycleRepeat, "Repeat ${state.repeat.name.lowercase()}", buttonSize = 46.dp) {
                GlyphIcon(
                    if (state.repeat == RepeatMode.ONE) Glyph.REPEAT_ONE else Glyph.REPEAT, Modifier.size(22.dp),
                    if (state.repeat == RepeatMode.OFF) VV.CreamDim else VV.Gold,
                )
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlyphIcon(Glyph.VOLUME, Modifier.size(22.dp), VV.CreamDim)
            HorizontalFader(state.volume, player::setVolume, "Volume", Modifier.weight(1f))
        }
        SectionLabel("Turntable")
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            SpeedSelector(settings.turntableSpeed, { s -> player.update { it.copy(turntableSpeed = s) } })
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ToggleSwitch(settings.masterEnabled, { on -> player.update { it.copy(masterEnabled = on) } }, "Vintage sound processing")
                Text(if (settings.masterEnabled) "VINTAGE" else "BYPASS", style = MaterialTheme.typography.labelSmall, color = if (settings.masterEnabled) VV.Lamp else VV.CreamDim)
            }
        }
        PresetStrip(presets, activeId, player::applyPreset)
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        val deck: @Composable (Modifier) -> Unit = { m ->
            TurntableDeck(
                isPlaying = state.isPlaying, hasTrack = state.hasTrack, progressPercent = percent,
                rpm = settings.turntableSpeed.rpm, speedLabel = settings.turntableSpeed.label,
                artwork = art, modifier = m,
            )
        }
        if (landscape) {
            Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                deck(Modifier.fillMaxHeight())
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    topBar()
                    controls()
                }
            }
        } else {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                topBar()
                deck(Modifier.fillMaxWidth())
                controls()
            }
        }
    }

    if (showQueue) QueueSheet(state, player, onDismiss = { showQueue = false })
}

@Composable
private fun QueueSheet(state: PlayerState, player: PlayerViewModel, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) {
        if (state.currentIndex > 1) listState.scrollToItem(state.currentIndex - 1)
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = VV.Walnut, contentColor = VV.Cream) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("QUEUE  ·  ${state.queue.size}", style = MaterialTheme.typography.labelLarge, color = VV.Gold, modifier = Modifier.weight(1f))
                if (state.queue.isNotEmpty()) {
                    TextButton(onClick = { player.clearQueue(); onDismiss() }) { Text("CLEAR", color = VV.CreamDim) }
                }
            }
            if (state.queue.isEmpty()) {
                Text("The queue is empty.", style = MaterialTheme.typography.bodyMedium, color = VV.CreamDim, modifier = Modifier.padding(20.dp))
            }
            LazyColumn(state = listState, modifier = Modifier.weight(1f, fill = false)) {
                itemsIndexed(state.queue, key = { index, entry -> "$index:${entry.trackId}" }) { index, entry ->
                    val current = index == state.currentIndex
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(if (current) VV.WalnutMid else androidx.compose.ui.graphics.Color.Transparent)
                            .clickable { player.skipTo(index) }
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                            if (current) GlyphIcon(Glyph.PLAY, Modifier.size(16.dp), VV.Gold)
                            else Text("${index + 1}", style = MaterialTheme.typography.labelSmall, color = VV.CreamDim)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(entry.title, style = MaterialTheme.typography.titleSmall, color = if (current) VV.Gold else VV.Cream, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(entry.artist, style = MaterialTheme.typography.bodySmall, color = VV.CreamDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        QueueIconButton(Glyph.CHEVRON_UP, "Move up", index > 0) { player.moveInQueue(index, index - 1) }
                        QueueIconButton(Glyph.CHEVRON_DOWN, "Move down", index < state.queue.lastIndex) { player.moveInQueue(index, index + 1) }
                        QueueIconButton(Glyph.CLOSE, "Remove from queue", true) { player.removeFromQueue(index) }
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueIconButton(glyph: Glyph, description: String, enabled: Boolean, onClick: () -> Unit) {
    BrassButton(
        onClick = { if (enabled) onClick() },
        contentLabel = description,
        buttonSize = 34.dp,
    ) { GlyphIcon(glyph, Modifier.size(16.dp), if (enabled) VV.Cream else VV.GoldDim.copy(alpha = 0.4f)) }
}
