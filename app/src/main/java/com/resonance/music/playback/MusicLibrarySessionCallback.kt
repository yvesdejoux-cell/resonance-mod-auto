package com.resonance.music.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.resonance.music.R
import com.resonance.music.data.api.models.AlbumItem
import com.resonance.music.data.api.models.ArtistItem
import com.resonance.music.data.api.models.PlaylistItem
import com.resonance.music.data.api.models.SongItem
import com.resonance.music.data.repository.MusicRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.guava.future

private const val ROOT_ID = "root"
private const val ARTISTS_ID = "artists"
private const val ALBUMS_ID = "albums"
private const val PLAYLISTS_ID = "playlists"
private const val FAVORITES_ID = "favorites"
private const val FAVORITE_SONGS_ID = "favorite_songs"
private const val FAVORITE_ALBUMS_ID = "favorite_albums"
private const val FAVORITE_ARTISTS_ID = "favorite_artists"
private const val ARTIST_PREFIX = "artist/"
private const val ALBUM_PREFIX = "album/"
private const val PLAYLIST_PREFIX = "playlist/"

// Quick-action leaves prepended inside an artist/album/playlist's own children list, so
// browsing into it (tap the row, as always) offers a distinct "play everything" entry
// alongside the normal browsable content — instead of overloading the row itself with a
// second meaning, which car head units aren't guaranteed to expose as two separate actions.
private const val SHUFFLE_ARTIST_PREFIX = "shuffle_artist/"
private const val PLAY_ALBUM_PREFIX = "play_album/"
private const val SHUFFLE_PLAYLIST_PREFIX = "shuffle_playlist/"
private const val SHUFFLE_FAVORITE_SONGS_ID = "shuffle_favorite_songs"

// Custom session command that surfaces as a heart button on the car's Now Playing
// screen (and the notification), alongside the standard transport controls.
private const val COMMAND_TOGGLE_FAVORITE = "com.resonance.music.TOGGLE_FAVORITE"

/**
 * Builds the Android Auto / Automotive browse tree (Artistes, Albums, Playlists,
 * Favoris) on top of [MusicRepository] and resolves songs to playable [MediaItem]s
 * with a real stream [Uri] already attached, so tapping a track in the car UI
 * plays it directly without a further resolution round-trip.
 */
class MusicLibrarySessionCallback(
    private val musicRepository: MusicRepository,
    private val scope: CoroutineScope
) : MediaLibrarySession.Callback {

    private val toggleFavoriteCommand = SessionCommand(COMMAND_TOGGLE_FAVORITE, Bundle.EMPTY)
    private var currentSongId: String? = null
    private var currentIsFavorite: Boolean = false

    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo
    ): MediaSession.ConnectionResult {
        val result = super.onConnect(session, controller)
        val sessionCommands = result.availableSessionCommands.buildUpon()
            .add(toggleFavoriteCommand)
            .build()
        return MediaSession.ConnectionResult.accept(sessionCommands, result.availablePlayerCommands)
    }

    override fun onPostConnect(session: MediaSession, controller: MediaSession.ControllerInfo) {
        session.setCustomLayout(controller, listOf(favoriteButton()))
    }

    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: SessionCommand,
        args: Bundle
    ): ListenableFuture<SessionResult> {
        if (customCommand.customAction != COMMAND_TOGGLE_FAVORITE) {
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
        }
        val songId = currentSongId ?: session.player.currentMediaItem?.mediaId
        if (songId == null) {
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_BAD_VALUE))
        }
        val wasFavorite = currentIsFavorite
        currentIsFavorite = !wasFavorite
        refreshFavoriteLayout(session)
        return scope.future {
            try {
                if (wasFavorite) musicRepository.unstar(songId) else musicRepository.star(songId)
                SessionResult(SessionResult.RESULT_SUCCESS)
            } catch (_: Exception) {
                currentIsFavorite = wasFavorite
                refreshFavoriteLayout(session)
                SessionResult(SessionResult.RESULT_ERROR_UNKNOWN)
            }
        }
    }

    /** Called by [PlaybackService] whenever the player moves to a new media item, so the
     *  heart button reflects that track's favorite status instead of the previous one. */
    fun onTrackChanged(session: MediaSession, mediaItem: MediaItem?) {
        currentSongId = mediaItem?.mediaId
        currentIsFavorite = mediaItem?.mediaMetadata?.extras?.getBoolean(PlaybackManager.EXTRA_STARRED) ?: false
        refreshFavoriteLayout(session)
    }

    private fun refreshFavoriteLayout(session: MediaSession) {
        val layout = listOf(favoriteButton())
        session.connectedControllers.forEach { controller ->
            session.setCustomLayout(controller, layout)
        }
    }

    private fun favoriteButton(): CommandButton =
        CommandButton.Builder()
            .setDisplayName(if (currentIsFavorite) "Retirer des favoris" else "Ajouter aux favoris")
            .setSessionCommand(toggleFavoriteCommand)
            .setIconResId(if (currentIsFavorite) R.drawable.ic_favorite_filled else R.drawable.ic_favorite_outline)
            .setEnabled(true)
            .build()

    override fun onGetLibraryRoot(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: LibraryParams?
    ): ListenableFuture<LibraryResult<MediaItem>> =
        Futures.immediateFuture(LibraryResult.ofItem(folderItem(ROOT_ID, "Resonance"), params))

    override fun onGetChildren(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: LibraryParams?
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = scope.future {
        val children: List<MediaItem>? = try {
            when {
                parentId == ROOT_ID -> rootChildren()
                parentId == ARTISTS_ID -> artistItems(musicRepository.getArtists())
                parentId == ALBUMS_ID -> albumItems(
                    musicRepository.getAlbumList(
                        type = "alphabeticalByName",
                        size = if (pageSize in 1..500) pageSize else 200,
                        offset = if (pageSize > 0) page * pageSize else 0
                    )
                )
                parentId == PLAYLISTS_ID -> playlistItems(musicRepository.getPlaylists())
                parentId == FAVORITES_ID -> favoritesChildren()
                parentId == FAVORITE_SONGS_ID -> {
                    val songs = musicRepository.getStarred().song.orEmpty()
                    if (songs.isEmpty()) null else listOf(shuffleFavoriteSongsItem()) + songItems(songs)
                }
                parentId == FAVORITE_ALBUMS_ID -> albumItems(musicRepository.getStarred().album.orEmpty())
                parentId == FAVORITE_ARTISTS_ID -> artistItems(musicRepository.getStarred().artist.orEmpty())
                parentId.startsWith(ARTIST_PREFIX) -> {
                    val artistId = parentId.removePrefix(ARTIST_PREFIX)
                    val albums = musicRepository.getArtistDetail(artistId)?.album.orEmpty()
                    if (albums.isEmpty()) null else listOf(shuffleArtistItem(artistId)) + albumItems(albums)
                }
                parentId.startsWith(ALBUM_PREFIX) -> {
                    val albumId = parentId.removePrefix(ALBUM_PREFIX)
                    val songs = musicRepository.getAlbumDetail(albumId)?.song.orEmpty()
                    if (songs.isEmpty()) null else listOf(playAlbumItem(albumId)) + songItems(songs)
                }
                parentId.startsWith(PLAYLIST_PREFIX) -> {
                    val playlistId = parentId.removePrefix(PLAYLIST_PREFIX)
                    val songs = musicRepository.getPlaylistDetail(playlistId)?.entry.orEmpty()
                    if (songs.isEmpty()) null else listOf(shufflePlaylistItem(playlistId)) + songItems(songs)
                }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
        if (children == null) {
            LibraryResult.ofError<ImmutableList<MediaItem>>(SessionResult.RESULT_ERROR_BAD_VALUE)
        } else {
            LibraryResult.ofItemList(ImmutableList.copyOf(children), params)
        }
    }

    override fun onGetItem(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        mediaId: String
    ): ListenableFuture<LibraryResult<MediaItem>> = scope.future {
        val item: MediaItem? = try {
            when {
                mediaId == ROOT_ID -> folderItem(ROOT_ID, "Resonance")
                mediaId == ARTISTS_ID -> folderItem(ARTISTS_ID, "Artistes")
                mediaId == ALBUMS_ID -> folderItem(ALBUMS_ID, "Albums")
                mediaId == PLAYLISTS_ID -> folderItem(PLAYLISTS_ID, "Playlists")
                mediaId == FAVORITES_ID -> folderItem(FAVORITES_ID, "Favoris")
                mediaId.startsWith(ALBUM_PREFIX) ->
                    musicRepository.getAlbumDetail(mediaId.removePrefix(ALBUM_PREFIX))?.let {
                        albumBrowsableItem(it.id, it.name, it.artist, it.coverArt)
                    }
                mediaId.startsWith(ARTIST_PREFIX) ->
                    musicRepository.getArtistDetail(mediaId.removePrefix(ARTIST_PREFIX))?.let {
                        artistBrowsableItem(it.id, it.name, it.coverArt)
                    }
                mediaId.startsWith(PLAYLIST_PREFIX) ->
                    musicRepository.getPlaylistDetail(mediaId.removePrefix(PLAYLIST_PREFIX))?.let {
                        playlistBrowsableItem(it.id, it.name, it.coverArt)
                    }
                else -> musicRepository.getSong(mediaId)?.let { songPlayableItem(it) }
            }
        } catch (_: Exception) {
            null
        }
        if (item == null) LibraryResult.ofError<MediaItem>(SessionResult.RESULT_ERROR_BAD_VALUE) else LibraryResult.ofItem(item, null)
    }

    // Legacy MediaBrowserCompat clients (Android Auto, Bluetooth, Assistant) drive playback
    // via playFromMediaId/addQueueItem, which Media3 bridges into these calls with a MediaItem
    // that carries only a mediaId — no URI. Resolve it against the real song(s) before it
    // reaches the player, otherwise ExoPlayer has nothing to play and tapping a row silently
    // no-ops. This also expands the shuffleArtistItem/playAlbumItem/shufflePlaylistItem quick
    // actions into their full, resolved track list (shuffled for artist/playlist, in order
    // for albums).
    override fun onAddMediaItems(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: List<MediaItem>
    ): ListenableFuture<List<MediaItem>> = scope.future {
        mediaItems.flatMap { resolvePlayableItems(it) }
    }

    override fun onSetMediaItems(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = scope.future {
        val resolved = mediaItems.flatMap { resolvePlayableItems(it) }
        val safeStartIndex = if (resolved.isEmpty()) 0 else startIndex.coerceIn(0, resolved.size - 1)
        MediaSession.MediaItemsWithStartPosition(resolved, safeStartIndex, startPositionMs)
    }

    private suspend fun resolvePlayableItems(item: MediaItem): List<MediaItem> {
        if (item.localConfiguration != null) return listOf(item)
        val id = item.mediaId
        val resolved = try {
            when {
                id.startsWith(PLAY_ALBUM_PREFIX) ->
                    musicRepository.getAlbumDetail(id.removePrefix(PLAY_ALBUM_PREFIX))?.song
                        ?.mapNotNull { songPlayableItem(it) }
                id.startsWith(SHUFFLE_ARTIST_PREFIX) ->
                    musicRepository.getArtistSongs(id.removePrefix(SHUFFLE_ARTIST_PREFIX))
                        .shuffled()
                        .mapNotNull { songPlayableItem(it) }
                id.startsWith(SHUFFLE_PLAYLIST_PREFIX) ->
                    musicRepository.getPlaylistDetail(id.removePrefix(SHUFFLE_PLAYLIST_PREFIX))?.entry
                        ?.shuffled()
                        ?.mapNotNull { songPlayableItem(it) }
                id == SHUFFLE_FAVORITE_SONGS_ID ->
                    musicRepository.getStarred().song.orEmpty()
                        .shuffled()
                        .mapNotNull { songPlayableItem(it) }
                id.startsWith(ALBUM_PREFIX) ->
                    musicRepository.getAlbumDetail(id.removePrefix(ALBUM_PREFIX))?.song
                        ?.mapNotNull { songPlayableItem(it) }
                id.startsWith(ARTIST_PREFIX) ->
                    musicRepository.getArtistSongs(id.removePrefix(ARTIST_PREFIX))
                        .shuffled()
                        .mapNotNull { songPlayableItem(it) }
                id.startsWith(PLAYLIST_PREFIX) ->
                    musicRepository.getPlaylistDetail(id.removePrefix(PLAYLIST_PREFIX))?.entry
                        ?.shuffled()
                        ?.mapNotNull { songPlayableItem(it) }
                else -> musicRepository.getSong(id)?.let { songPlayableItem(it) }?.let { listOf(it) }
            }
        } catch (_: Exception) {
            null
        }
        return resolved?.takeIf { it.isNotEmpty() } ?: listOf(item)
    }

    // --- Tree building ---

    private fun rootChildren(): List<MediaItem> = listOf(
        folderItem(ARTISTS_ID, "Artistes"),
        folderItem(ALBUMS_ID, "Albums"),
        folderItem(PLAYLISTS_ID, "Playlists"),
        folderItem(FAVORITES_ID, "Favoris")
    )

    private fun favoritesChildren(): List<MediaItem> = listOf(
        folderItem(FAVORITE_SONGS_ID, "Titres favoris"),
        folderItem(FAVORITE_ALBUMS_ID, "Albums favoris"),
        folderItem(FAVORITE_ARTISTS_ID, "Artistes favoris")
    )

    private fun artistItems(artists: List<ArtistItem>): List<MediaItem> =
        artists.map { artistBrowsableItem(it.id, it.name, it.artistImageUrl ?: it.coverArt) }

    private fun albumItems(albums: List<AlbumItem>): List<MediaItem> =
        albums.map { albumBrowsableItem(it.id, it.name, it.artist, it.coverArt) }

    private fun playlistItems(playlists: List<PlaylistItem>): List<MediaItem> =
        playlists.map { playlistBrowsableItem(it.id, it.name, it.coverArt) }

    private fun songItems(songs: List<SongItem>): List<MediaItem> =
        songs.mapNotNull { songPlayableItem(it) }

    private fun folderItem(id: String, title: String): MediaItem =
        MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                    .build()
            )
            .build()

    // Purely browsable: tapping the row always opens it. The "play everything" action lives
    // as a separate quick-action item inside the folder's own children (see shuffleArtistItem
    // / playAlbumItem / shufflePlaylistItem below) so both actions are always distinct and
    // available, regardless of whether a given head unit would otherwise collapse a
    // browsable+playable row into a single tap target.
    private fun artistBrowsableItem(id: String, name: String, artworkId: String?): MediaItem =
        MediaItem.Builder()
            .setMediaId(ARTIST_PREFIX + id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(name)
                    .setArtist(name)
                    .setArtworkUri(artworkUri(artworkId))
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_ARTIST)
                    .build()
            )
            .build()

    private fun albumBrowsableItem(id: String, name: String, artist: String?, coverArt: String?): MediaItem =
        MediaItem.Builder()
            .setMediaId(ALBUM_PREFIX + id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(name)
                    .setArtist(artist)
                    .setArtworkUri(artworkUri(coverArt))
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_ALBUM)
                    .build()
            )
            .build()

    private fun playlistBrowsableItem(id: String, name: String, artworkId: String?): MediaItem =
        MediaItem.Builder()
            .setMediaId(PLAYLIST_PREFIX + id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(name)
                    .setArtworkUri(artworkUri(artworkId))
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST)
                    .build()
            )
            .build()

    private fun shuffleArtistItem(artistId: String): MediaItem =
        quickActionItem(SHUFFLE_ARTIST_PREFIX + artistId, "🔀 Lecture aléatoire")

    private fun playAlbumItem(albumId: String): MediaItem =
        quickActionItem(PLAY_ALBUM_PREFIX + albumId, "▶ Tout écouter")

    private fun shufflePlaylistItem(playlistId: String): MediaItem =
        quickActionItem(SHUFFLE_PLAYLIST_PREFIX + playlistId, "🔀 Lecture aléatoire")

    private fun shuffleFavoriteSongsItem(): MediaItem =
        quickActionItem(SHUFFLE_FAVORITE_SONGS_ID, "🔀 Lecture aléatoire")

    private fun quickActionItem(id: String, title: String): MediaItem =
        MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .build()
            )
            .build()

    private fun songPlayableItem(song: SongItem): MediaItem? {
        val uri = musicRepository.getStreamUrl(song.id) ?: return null
        val extras = Bundle().apply {
            song.coverArt?.let { putString(PlaybackManager.EXTRA_COVER_ART, it) }
            putBoolean(PlaybackManager.EXTRA_STARRED, song.starred != null)
        }
        return MediaItem.Builder()
            .setMediaId(song.id)
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.artist)
                    .setAlbumTitle(song.album)
                    .setArtworkUri(artworkUri(song.coverArt))
                    .setExtras(extras)
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .build()
            )
            .build()
    }

    private fun artworkUri(coverArtId: String?): Uri? =
        coverArtId?.let { musicRepository.getCoverArtUrl(it, 600) }?.let { Uri.parse(it) }
}
