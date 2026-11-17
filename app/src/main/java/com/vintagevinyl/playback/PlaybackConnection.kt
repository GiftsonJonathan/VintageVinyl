package com.vintagevinyl.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.net.toUri
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.vintagevinyl.domain.model.PlayerState
import com.vintagevinyl.domain.model.QueueEntry
import com.vintagevinyl.domain.model.RepeatMode
import com.vintagevinyl.domain.model.Track
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlin.time.Duration.Companion.milliseconds

/**
 * The UI's handle on the player. Wraps a [MediaController] connected to
 * [VintagePlaybackService] and publishes an immutable [PlayerState].
 *
 * Everything here must be called on the main thread (a MediaController requirement; all callers
 * are UI code or view-model scopes, which are main-thread).
 *
 * The playback position is *not* part of the state flow (it would re-render the screen several
 * times a second even when nothing visible changed). Screens that show it collect [positionFlow].
 */
@UnstableApi
class PlaybackConnection(private val context: Context) {

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private var future: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var pending: ((MediaController) -> Unit)? = null
    private var queueCache: List<QueueEntry> = emptyList()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            val queueChanged = events.contains(Player.EVENT_TIMELINE_CHANGED)
            if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION) || events.contains(Player.EVENT_IS_PLAYING_CHANGED)) {
                _state.update { it.copy(error = null) }
            }
            publish(player, rebuildQueue = queueChanged)
        }

        override fun onPlayerError(error: PlaybackException) {
            _state.update { it.copy(error = error.message ?: error.errorCodeName) }
        }
    }

    fun connect() {
        if (future != null) return
        val token = SessionToken(context, ComponentName(context, VintagePlaybackService::class.java))
        val f = MediaController.Builder(context, token).buildAsync()
        future = f
        f.addListener({
            if (future !== f) return@addListener // disconnected while connecting
            try {
                val c = f.get()
                controller = c
                c.addListener(listener)
                publish(c, rebuildQueue = true)
                pending?.invoke(c)
                pending = null
            } catch (e: Exception) {
                future = null
                _state.update { it.copy(connected = false, error = "Could not start the player: ${e.message}") }
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /** Releases the controller (the service, and any music, keeps going). */
    fun disconnect() {
        controller?.removeListener(listener)
        future?.let { MediaController.releaseFuture(it) }
        controller = null
        future = null
        pending = null
        _state.update { it.copy(connected = false) }
    }

    private fun publish(p: Player, rebuildQueue: Boolean) {
        if (rebuildQueue) {
            queueCache = (0 until p.mediaItemCount).map { i ->
                val item = p.getMediaItemAt(i)
                QueueEntry(
                    trackId = item.mediaId,
                    title = item.mediaMetadata.title?.toString().orEmpty(),
                    artist = item.mediaMetadata.artist?.toString().orEmpty(),
                )
            }
        }
        val item = p.currentMediaItem
        val duration = p.duration
        _state.update { old ->
            PlayerState(
                connected = true,
                isPlaying = p.isPlaying,
                isBuffering = p.playbackState == Player.STATE_BUFFERING,
                currentTrackId = item?.mediaId,
                title = item?.mediaMetadata?.title?.toString().orEmpty(),
                artist = item?.mediaMetadata?.artist?.toString().orEmpty(),
                album = item?.mediaMetadata?.albumTitle?.toString().orEmpty(),
                positionMs = p.currentPosition.coerceAtLeast(0),
                durationMs = if (duration == C.TIME_UNSET) 0 else duration,
                shuffle = p.shuffleModeEnabled,
                repeat = when (p.repeatMode) {
                    Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                    Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                    else -> RepeatMode.OFF
                },
                volume = p.volume,
                queue = queueCache,
                currentIndex = if (item == null) -1 else p.currentMediaItemIndex,
                error = old.error,
            )
        }
    }

    /** Current playback position, ~5 times a second while collected (so: only while visible). */
    fun positionFlow(): Flow<Long> = flow {
        while (true) {
            controller?.let { emit(it.currentPosition.coerceAtLeast(0)) }
            delay(200.milliseconds)
        }
    }

    // ---- commands -------------------------------------------------------------------------

    private fun run(block: (MediaController) -> Unit) {
        val c = controller
        if (c != null) block(c) else pending = block // run as soon as the connection is up
    }

    /** Replaces the queue with [tracks] and starts playing at [startIndex]. */
    fun playTracks(tracks: List<Track>, startIndex: Int = 0, shuffle: Boolean = false) {
        if (tracks.isEmpty()) return
        run { c ->
            c.setMediaItems(tracks.map(::toMediaItem), startIndex.coerceIn(0, tracks.lastIndex), 0L)
            c.shuffleModeEnabled = shuffle
            c.prepare()
            c.play()
        }
    }

    fun togglePlay() = run { c ->
        when {
            c.mediaItemCount == 0 -> Unit
            c.isPlaying -> c.pause()
            else -> {
                if (c.playbackState == Player.STATE_IDLE) c.prepare()
                if (c.playbackState == Player.STATE_ENDED) c.seekToDefaultPosition()
                c.play()
            }
        }
    }

    fun next() = run { it.seekToNext() }
    fun previous() = run { it.seekToPrevious() } // restarts the track if it is past 3 s
    fun seekTo(positionMs: Long) = run { it.seekTo(positionMs) }
    fun skipTo(index: Int) = run { c ->
        if (index in 0 until c.mediaItemCount) {
            c.seekTo(index, 0L)
            c.play()
        }
    }

    fun setShuffle(enabled: Boolean) = run { it.shuffleModeEnabled = enabled }

    fun cycleRepeat() = run { c ->
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun setVolume(volume: Float) = run { it.volume = volume.coerceIn(0f, 1f) }

    /** Appends [tracks] to the queue; starts playback if the queue was empty. */
    fun enqueue(tracks: List<Track>) = run { c ->
        if (tracks.isEmpty()) return@run
        val wasEmpty = c.mediaItemCount == 0
        c.addMediaItems(tracks.map(::toMediaItem))
        if (wasEmpty) {
            c.prepare()
            c.play()
        }
    }

    /** Inserts [tracks] right after the current one; starts playback if the queue was empty. */
    fun playNext(tracks: List<Track>) = run { c ->
        if (tracks.isEmpty()) return@run
        val wasEmpty = c.mediaItemCount == 0
        val at = if (wasEmpty) 0 else c.currentMediaItemIndex + 1
        c.addMediaItems(at, tracks.map(::toMediaItem))
        if (wasEmpty) {
            c.prepare()
            c.play()
        }
    }

    fun removeFromQueue(index: Int) = run { c ->
        if (index in 0 until c.mediaItemCount) c.removeMediaItem(index)
    }

    fun moveInQueue(from: Int, to: Int) = run { c ->
        if (from in 0 until c.mediaItemCount && to in 0 until c.mediaItemCount) c.moveMediaItem(from, to)
    }

    fun clearQueue() = run { it.clearMediaItems() }

    private fun toMediaItem(t: Track): MediaItem {
        val uri = t.uri.toUri()
        return MediaItem.Builder()
            .setMediaId(t.id)
            .setUri(uri)
            // The session service rebuilds the playable item from this (see its Callback).
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(t.title)
                    .setArtist(t.artist)
                    .setAlbumTitle(t.album)
                    .build()
            )
            .build()
    }
}
