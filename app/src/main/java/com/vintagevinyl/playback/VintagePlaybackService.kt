

package com.vintagevinyl.playback

import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.vintagevinyl.MainActivity
import com.vintagevinyl.R
import com.vintagevinyl.VintageVinylApp
import com.vintagevinyl.audio.VintageAudioProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Owns the ExoPlayer and the MediaSession. Running as a foreground `mediaPlayback` service is
 * what keeps music playing with the screen off and the app in the background; Media3 builds the
 * notification (with play/pause/previous/next and the lock-screen controls) from the session.
 *
 * The [VintageAudioProcessor] is installed in the player's `DefaultAudioSink`, and a coroutine
 * forwards every change of the user's sound settings to it.
 */

@UnstableApi
class VintagePlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private val processor = VintageAudioProcessor()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        // Live settings -> DSP engine. (Collects on the main thread; the hand-over is a volatile write.)
        scope.launch { (application as VintageVinylApp).container.sound.settings.collect { processor.updateSettings(it) } }

        val renderers = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): AudioSink = DefaultAudioSink.Builder(context)
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .setAudioProcessors(arrayOf<AudioProcessor>(processor))
                .build()
        }.setEnableDecoderFallback(true)

        val player = ExoPlayer.Builder(this, renderers)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true) // pause when headphones are unplugged
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        // A file that cannot be decoded must not stop the music: skip to the next one.
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                if (player.hasNextMediaItem()) {
                    player.seekToNextMediaItem()
                    player.prepare()
                    player.play()
                }
            }
        })

        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, player)
            .setCallback(Callback())
            .setSessionActivity(openApp)
            .build()

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelName(R.string.notification_channel_name)
                .build()
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /** Swiping the app away stops the service unless music is actually playing. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    /**
     * Controllers send media items without a playable URI (for security); the URI travels in the
     * request metadata and is restored here.
     */
    private inner class Callback : MediaSession.Callback {
        override fun onConnect(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult =
            if (controller.packageName == this@VintagePlaybackService.packageName || controller.isTrusted) {
                super.onConnect(mediaSession, controller)
            }
        else MediaSession.ConnectionResult.reject()

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            val resolved = mediaItems.mapNotNull { item ->
                val uri = item.requestMetadata.mediaUri ?: item.localConfiguration?.uri
                if (uri?.isPlayableByApp() == true) item.buildUpon().setUri(uri).build()
                else null
            }.toMutableList()
            return Futures.immediateFuture(resolved)
        }

        private fun Uri.isPlayableByApp(): Boolean {
            if  (scheme != ContentResolver.SCHEME_CONTENT) return false
            if (authority == MediaStore.AUTHORITY) return true
            return contentResolver.persistedUriPermissions.any { permission ->
                permission.isReadPermission && permission.uri == this
            }
        }
    }
}
