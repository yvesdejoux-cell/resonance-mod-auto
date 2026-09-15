package com.resonance.music.playback

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.resonance.music.MainActivity
import com.resonance.music.data.repository.MusicRepository
import com.resonance.music.data.repository.SettingsStore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Owns the ExoPlayer and MediaLibrarySession. The UI never touches the player
 * directly; it connects a MediaController to this session via [PlaybackManager].
 *
 * Extending [MediaLibraryService] (rather than plain MediaSessionService) also
 * publishes a browsable library tree — Artistes, Albums, Playlists, Favoris —
 * built by [MusicLibrarySessionCallback], which Android Auto/Automotive use to
 * let the car head unit browse and play music without the phone UI.
 */
@AndroidEntryPoint
class PlaybackService : MediaLibraryService() {

    @Inject
    lateinit var musicRepository: MusicRepository

    @Inject
    lateinit var settingsStore: SettingsStore

    private var mediaLibrarySession: MediaLibrarySession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        val sessionCallback = MusicLibrarySessionCallback(musicRepository, settingsStore, serviceScope)

        // Skip past unplayable tracks instead of crashing, with a small guard
        // against an infinite skip loop when many tracks fail in a row.
        var consecutiveFailures = 0
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Log.e("PlaybackService", "Playback error: ${error.message}", error)
                consecutiveFailures++
                if (consecutiveFailures > 3) {
                    player.pause()
                    return
                }
                if (player.hasNextMediaItem()) {
                    player.seekToNextMediaItem()
                    player.prepare()
                    player.play()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) consecutiveFailures = 0
            }

            // Keeps the car/notification favorite (heart) button in sync with whichever
            // track is now playing, instead of showing the previous track's state.
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                mediaLibrarySession?.let { sessionCallback.onTrackChanged(it, mediaItem) }
                persistQueueState(player)
            }

            // Checkpoint the position whenever playback pauses (leaving the car, the app
            // backgrounding, etc.) so a later reconnect with a fresh session — see
            // MusicLibrarySessionCallback.onPlaybackResumption — restores this exact spot.
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying) persistQueueState(player)
            }
        })

        mediaLibrarySession = MediaLibrarySession.Builder(this, player, sessionCallback)
            .setSessionActivity(buildContentIntent())
            .build()
    }

    // Radio streams have no meaningful "resume position" and aren't real library songs,
    // so they're excluded — a "radio" queue is simply never persisted for resumption.
    private fun persistQueueState(player: Player) {
        if (player.mediaItemCount == 0) return
        val ids = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }
        if (ids.any { it.startsWith("radio") }) return
        // Read player state here, on the main thread the listener callback runs on — ExoPlayer
        // forbids touching the player from any other thread, and the coroutine below runs on IO.
        val index = player.currentMediaItemIndex
        val position = player.currentPosition
        serviceScope.launch {
            settingsStore.savePlaybackState(ids, index, position)
        }
    }

    // Tapping the media notification opens the app.
    private fun buildContentIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = mediaLibrarySession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = mediaLibrarySession?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaLibrarySession?.run {
            player.release()
            release()
        }
        mediaLibrarySession = null
        serviceScope.cancel()
        super.onDestroy()
    }
}
