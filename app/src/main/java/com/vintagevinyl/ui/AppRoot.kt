package com.vintagevinyl.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.vintagevinyl.ui.components.BrassButton
import com.vintagevinyl.ui.components.Glyph
import com.vintagevinyl.ui.components.GlyphIcon
import com.vintagevinyl.ui.components.PlayPauseButton
import com.vintagevinyl.ui.screens.DeckScreen
import com.vintagevinyl.ui.screens.LibraryScreen
import com.vintagevinyl.ui.screens.NowPlayingScreen
import com.vintagevinyl.ui.screens.SoundScreen
import com.vintagevinyl.ui.theme.VV
import com.vintagevinyl.ui.theme.woodGrain
import com.vintagevinyl.ui.viewmodel.AppViewModelFactory
import com.vintagevinyl.ui.viewmodel.LibraryViewModel
import com.vintagevinyl.ui.viewmodel.PlayerViewModel
import com.vintagevinyl.ui.viewmodel.SoundViewModel

private object Route {
    const val DECK = "deck"
    const val LIBRARY = "library"
    const val SOUND = "sound"
    const val NOW = "now"
}

private data class Destination(val route: String, val label: String, val glyph: Glyph)

private val destinations = listOf(
    Destination(Route.DECK, "Deck", Glyph.DISC),
    Destination(Route.LIBRARY, "Library", Glyph.LIBRARY),
    Destination(Route.SOUND, "Sound", Glyph.SLIDERS),
)

/** Navigation host, the custom bottom bar and the mini player. */
@Composable
fun AppRoot(factory: AppViewModelFactory) {
    val nav = rememberNavController()
    val player: PlayerViewModel = viewModel(factory = factory)
    val library: LibraryViewModel = viewModel(factory = factory)
    val sound: SoundViewModel = viewModel(factory = factory)

    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val immersive = route == Route.NOW

    fun goTab(target: String) {
        nav.navigate(target) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Column(Modifier.fillMaxSize().woodGrain().windowInsetsPadding(WindowInsets.safeDrawing)) {
        NavHost(
            navController = nav,
            startDestination = Route.DECK,
            modifier = Modifier.weight(1f),
            enterTransition = { fadeIn(tween(220)) },
            exitTransition = { fadeOut(tween(160)) },
            popEnterTransition = { fadeIn(tween(220)) },
            popExitTransition = { fadeOut(tween(160)) },
        ) {
            composable(Route.DECK) {
                DeckScreen(player, onOpenNowPlaying = { nav.navigate(Route.NOW) { launchSingleTop = true } }, onOpenLibrary = { goTab(Route.LIBRARY) })
            }
            composable(Route.LIBRARY) { LibraryScreen(library) }
            composable(Route.SOUND) { SoundScreen(sound) }
            composable(
                Route.NOW,
                enterTransition = { slideInVertically(tween(320)) { it } + fadeIn(tween(200)) },
                exitTransition = { fadeOut(tween(160)) },
                popEnterTransition = { fadeIn(tween(200)) },
                popExitTransition = { slideOutVertically(tween(280)) { it } + fadeOut(tween(200)) },
            ) {
                NowPlayingScreen(player, onBack = { nav.popBackStack() })
            }
        }

        if (!immersive) {
            if (route != Route.DECK) MiniPlayer(player) { nav.navigate(Route.NOW) { launchSingleTop = true } }
            BottomBar(route) { goTab(it) }
        }
    }
}

@Composable
private fun BottomBar(current: String?, onSelect: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(VV.WalnutDeep).height(60.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        destinations.forEach { d ->
            val selected = d.route == current
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .semantics(mergeDescendants = true) { role = Role.Tab; this.selected = selected }
                    .clickable { onSelect(d.route) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                GlyphIcon(d.glyph, Modifier.size(24.dp), if (selected) VV.Gold else VV.CreamDim)
                Text(d.label.uppercase(), style = MaterialTheme.typography.labelSmall, color = if (selected) VV.Gold else VV.CreamDim)
            }
        }
    }
}

/** Compact now-playing strip shown above the bottom bar on screens other than the deck. */
@Composable
private fun MiniPlayer(player: PlayerViewModel, onOpen: () -> Unit) {
    val state by player.state.collectAsStateWithLifecycle()
    val track by player.currentTrack.collectAsStateWithLifecycle()
    if (!state.hasTrack) return
    Row(
        Modifier
            .fillMaxWidth()
            .background(VV.WalnutMid)
            .clickable(onClick = onOpen)
            .semantics { contentDescription = "Now playing: ${track?.title ?: state.title}. Open player" }
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(track?.title ?: state.title, style = MaterialTheme.typography.titleSmall, color = VV.Cream, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track?.artist ?: state.artist, style = MaterialTheme.typography.bodySmall, color = VV.CreamDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        BrassButton(player::previous, "Previous track", buttonSize = 40.dp) { GlyphIcon(Glyph.PREV, Modifier.size(18.dp), VV.Cream) }
        PlayPauseButton(state.isPlaying, player::togglePlay, buttonSize = 48.dp)
        BrassButton(player::next, "Next track", buttonSize = 40.dp) { GlyphIcon(Glyph.NEXT, Modifier.size(18.dp), VV.Cream) }
    }
}
