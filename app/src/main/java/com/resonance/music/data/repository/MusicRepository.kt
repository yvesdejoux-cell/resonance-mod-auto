package com.resonance.music.data.repository

import com.resonance.music.data.api.SubsonicApi
import com.resonance.music.data.api.SubsonicApiHelper
import com.resonance.music.data.api.models.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicRepository @Inject constructor(
    private val api: SubsonicApi,
    private val apiHelper: SubsonicApiHelper
) {
    // --- Artists ---

    suspend fun getArtists(): List<ArtistItem> {
        val root = api.getArtists()
        val env = root.response
        if (!env.isOk) throw SubsonicException(env.error)
        return env.artists?.index?.flatMap { it.artist ?: emptyList() } ?: emptyList()
    }

    suspend fun getArtistDetail(id: String): ArtistDetail? {
        val root = api.getArtist(id)
        val env = root.response
        if (!env.isOk) throw SubsonicException(env.error)
        return env.artist
    }

    /** Subsonic has no "all songs by artist" endpoint: gather each album's songs
     *  in parallel and flatten in album order. Used to shuffle-play an artist. */
    suspend fun getArtistSongs(artistId: String): List<SongItem> {
        val albums = getArtistDetail(artistId)?.album ?: return emptyList()
        return coroutineScope {
            albums.map { album -> async { getAlbumDetail(album.id)?.song ?: emptyList() } }
                .awaitAll()
                .flatten()
        }
    }

    /** Artist biography + similar artists. Best-effort: older servers lack getArtistInfo2. */
    suspend fun getArtistInfo(id: String): ArtistInfo2? {
        return try {
            val root = api.getArtistInfo2(id)
            if (!root.response.isOk) null else root.response.artistInfo2
        } catch (_: Exception) {
            null
        }
    }

    // --- Albums ---

    suspend fun getAlbumList(type: String, size: Int = 20, offset: Int = 0, genre: String? = null): List<AlbumItem> {
        val root = api.getAlbumList(type, size, offset, genre)
        val env = root.response
        if (!env.isOk) throw SubsonicException(env.error)
        return env.albumList2?.album ?: emptyList()
    }

    suspend fun getAlbumsByGenre(genre: String, size: Int = 100, offset: Int = 0): List<AlbumItem> =
        getAlbumList("byGenre", size, offset, genre)

    suspend fun getGenres(): List<GenreItem> {
        val root = api.getGenres()
        val env = root.response
        if (!env.isOk) throw SubsonicException(env.error)
        return env.genres?.genre?.sortedByDescending { it.albumCount ?: 0 } ?: emptyList()
    }

    suspend fun getRecentAlbums(size: Int = 20): List<AlbumItem> = getAlbumList("recent", size)
    suspend fun getNewestAlbums(size: Int = 20): List<AlbumItem> = getAlbumList("newest", size)
    suspend fun getFrequentAlbums(size: Int = 20): List<AlbumItem> = getAlbumList("frequent", size)
    suspend fun getRandomAlbums(size: Int = 20): List<AlbumItem> = getAlbumList("random", size)

    suspend fun getAlbumDetail(id: String): AlbumDetail? {
        val root = api.getAlbum(id)
        val env = root.response
        if (!env.isOk) throw SubsonicException(env.error)
        return env.album
    }

    /** Resolves a bare song id to its full metadata — needed when a client (e.g. Android
     *  Auto's legacy playFromMediaId) hands us only an id with no cached [SongItem]. */
    suspend fun getSong(id: String): SongItem? {
        val root = api.getSong(id)
        val env = root.response
        if (!env.isOk) return null
        return env.song
    }

    // --- Search ---

    suspend fun search(query: String): SearchResult {
        val root = api.search(query)
        val env = root.response
        if (!env.isOk) throw SubsonicException(env.error)
        return env.searchResult3 ?: SearchResult()
    }

    // --- Playlists ---

    suspend fun getPlaylists(): List<PlaylistItem> {
        val root = api.getPlaylists()
        val env = root.response
        if (!env.isOk) throw SubsonicException(env.error)
        return env.playlists?.playlist ?: emptyList()
    }

    suspend fun getPlaylistDetail(id: String): PlaylistDetail? {
        val root = api.getPlaylist(id)
        val env = root.response
        if (!env.isOk) throw SubsonicException(env.error)
        return env.playlist
    }

    suspend fun createPlaylist(name: String, songIds: List<String>? = null) {
        val root = api.createPlaylist(name, songIds)
        if (!root.response.isOk) throw SubsonicException(root.response.error)
    }

    suspend fun deletePlaylist(id: String) {
        val root = api.deletePlaylist(id)
        if (!root.response.isOk) throw SubsonicException(root.response.error)
    }

    suspend fun renamePlaylist(id: String, name: String) {
        val root = api.updatePlaylist(playlistId = id, name = name)
        if (!root.response.isOk) throw SubsonicException(root.response.error)
    }

    suspend fun addSongToPlaylist(playlistId: String, songId: String) {
        val root = api.updatePlaylist(playlistId = playlistId, songIdsToAdd = listOf(songId))
        if (!root.response.isOk) throw SubsonicException(root.response.error)
    }

    // songIndex is the song's 0-based position in the playlist, as required by Subsonic's
    // updatePlaylist — not the song id, since a playlist can contain the same song twice.
    suspend fun removeSongFromPlaylist(playlistId: String, songIndex: Int) {
        val root = api.updatePlaylist(playlistId = playlistId, songIndexesToRemove = listOf(songIndex))
        if (!root.response.isOk) throw SubsonicException(root.response.error)
    }

    // --- Favorites ---

    suspend fun star(id: String) = checkOk(api.star(id = id))

    suspend fun unstar(id: String) = checkOk(api.unstar(id = id))

    suspend fun starAlbum(albumId: String) = checkOk(api.star(albumId = albumId))

    suspend fun unstarAlbum(albumId: String) = checkOk(api.unstar(albumId = albumId))

    suspend fun starArtist(artistId: String) = checkOk(api.star(artistId = artistId))

    suspend fun unstarArtist(artistId: String) = checkOk(api.unstar(artistId = artistId))

    // A Subsonic failure comes back as HTTP 200 with status "failed", so it must be checked
    // explicitly or a rejected favorite change looks like a success.
    private fun checkOk(root: SubsonicRoot) {
        if (!root.response.isOk) throw SubsonicException(root.response.error)
    }

    suspend fun getStarred(): StarredResult {
        val root = api.getStarred()
        val env = root.response
        if (!env.isOk) throw SubsonicException(env.error)
        return env.starred2 ?: StarredResult()
    }

    // --- Random songs ---

    suspend fun getRandomSongs(size: Int = 20): List<SongItem> {
        val root = api.getRandomSongs(size)
        val env = root.response
        if (!env.isOk) throw SubsonicException(env.error)
        return env.randomSongs?.song ?: emptyList()
    }

    // --- Scrobble ---

    suspend fun scrobble(songId: String, submission: Boolean = true) {
        api.scrobble(songId, submission)
    }

    // --- Lyrics ---

    suspend fun getLyrics(artist: String?, title: String?): LyricsResult? {
        val root = api.getLyrics(artist, title)
        val env = root.response
        if (!env.isOk) return null
        return env.lyrics
    }

    suspend fun getStructuredLyrics(songId: String): List<StructuredLyrics> {
        return try {
            val root = api.getLyricsBySongId(songId)
            val env = root.response
            if (!env.isOk) return emptyList()
            env.lyricsList?.structuredLyrics ?: emptyList()
        } catch (_: Exception) {
            // getLyricsBySongId may not be supported on older servers
            emptyList()
        }
    }

    // --- Internet Radio ---

    suspend fun getRadioStations(): List<InternetRadioStation> {
        val root = api.getInternetRadioStations()
        val env = root.response
        if (!env.isOk) throw SubsonicException(env.error)
        return env.internetRadioStations?.internetRadioStation ?: emptyList()
    }

    // --- URLs ---

    fun getStreamUrl(songId: String): String? = apiHelper.getStreamUrl(songId)
    fun getCoverArtUrl(coverArtId: String, size: Int = 300): String? = apiHelper.getCoverArtUrl(coverArtId, size)
}

class SubsonicException(error: SubsonicError?) : Exception(
    error?.message ?: "Unknown Subsonic error (code: ${error?.code})"
)
