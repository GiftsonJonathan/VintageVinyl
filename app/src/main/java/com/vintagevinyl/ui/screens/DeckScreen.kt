package com.vintagevinyl.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vintagevinyl.ui.components.SeekSection
import com.vintagevinyl.ui.components.TurntableDeck
import com.vintagevinyl.ui.theme.VV
import com.vintagevinyl.ui.viewmodel.PlayerViewModel

/**
 * Home screen: the turntable with the record spinning on it, what is playing, the progress
 * rail, transport buttons and a quick preset selector.
 */
@Composable
fun DeckScreen(
    player: PlayerViewModel,
    onOpenNowPlaying: () -> Unit,
    onOpenLibrary: () -> Unit,
) {
    val state by player.state.collectAsStateWithLifecycle()
    val track by player.currentTrack.collectAsStateWithLifecycle()
    val art by player.artwork.collectAsStateWithLifecycle()
    val settings by player.settings.collectAsStateWithLifecycle()
    val presets by player.presets.collectAsStateWithLifecycle()
    val activeId by player.activePresetId.collectAsStateWithLifecycle()

    val positionFlow = remember(player) { player.position }
    val positionState: State<Long> = positionFlow.collectAsStateWithLifecycle(initialValue = 0L)
    // Only recomposes the deck when the whole-percent value changes (<= 100 times per track).
    val percent by remember { derivedStateOf { progressPercent(positionState.value, state.durationMs) } }

    val deck: @Composable (Modifier) -> Unit = { m ->
        TurntableDeck(
            isPlaying = state.isPlaying,
            hasTrack = state.hasTrack,
            progressPercent = percent,
            rpm = settings.turntableSpeed.rpm,
            speedLabel = settings.turntableSpeed.label,
            artwork = art,
            modifier = m,
            onClick = if (state.hasTrack) onOpenNowPlaying else null,
        )
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        if (landscape) {
            Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                deck(Modifier.fillMaxHeight())
                Column(
                    Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    DeckHeader(settings.masterEnabled, { on -> player.update { it.copy(masterEnabled = on) } })
                    Plaque(track, state)
                    SeekSection({ positionState.value }, state.durationMs, state.hasTrack, player::seekTo)
                    TransportRow(state, player::previous, player::togglePlay, player::next)
                    if (!state.hasTrack) TextButton(onClick = onOpenLibrary) { Text("OPEN LIBRARY", color = VV.Gold) }
                    PresetStrip(presets, activeId, player::applyPreset)
                }
            }
        } else {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                DeckHeader(settings.masterEnabled, { on -> player.update { it.copy(masterEnabled = on) } })
                deck(Modifier.fillMaxWidth())
                Plaque(track, state)
                SeekSection({ positionState.value }, state.durationMs, state.hasTrack, player::seekTo)
                TransportRow(state, player::previous, player::togglePlay, player::next)
                if (!state.hasTrack) TextButton(onClick = onOpenLibrary) { Text("OPEN LIBRARY", color = VV.Gold) }
                PresetStrip(presets, activeId, player::applyPreset)
            }
        }
    }
}
