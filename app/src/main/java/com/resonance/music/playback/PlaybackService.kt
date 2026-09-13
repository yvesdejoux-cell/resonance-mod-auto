package com.resonance.music.playback

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.resonance.music.MainActivity
import com.resonance.music.data.repository.MusicRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
        })

        mediaLibrarySession = MediaLibrarySession.Builder(
            this,
            player,
            MusicLibrarySessionCallback(musicRepository, serviceScope)
        )
            .setSessionActivity(buildContentIntent())
            .build()
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
