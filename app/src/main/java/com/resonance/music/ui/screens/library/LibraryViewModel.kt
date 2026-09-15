package com.resonance.music.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resonance.music.data.api.models.*
import com.resonance.music.data.repository.MusicRepository
import com.resonance.music.playback.PlaybackManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AlbumSort(val type: String) {
    AZ("alphabeticalByName"),
    NEWEST("newest"),
    RECENT("recent"),
    FREQUENT("frequent"),
    RANDOM("random")
}

data class LibraryUiState(
    val isLoading: Boolean = false,
    val artists: List<ArtistItem> = emptyList(),
    val albums: List<AlbumItem> = emptyList(),
    val albumSort: AlbumSort = AlbumSort.AZ,
    val genres: List<GenreItem> = emptyList(),
    val playlists: List<PlaylistItem> = emptyList(),
    val radioStations: List<InternetRadioStation> = emptyList(),
    val starredArtists: List<ArtistItem> = emptyList(),
    val starredAlbums: List<AlbumItem> = emptyList(),
    val starredSongs: List<SongItem> = emptyList(),
    val error: String? = null,
    val coverArtUrlBuilder: ((String) -> String?)? = null
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val playbackManager: PlaybackManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private var artistsLoaded = false
    private var albumsLoaded = false
    private var playlistsLoaded = false
    private var favoritesLoaded = false
    private var genresLoaded = false
    private var radiosLoaded = false

    // One stable instance so UiState copies compare equal (a fresh lambda each
    // copy would break equality and force recomposition). 128px suits 48dp rows.
    private val coverArtBuilder: (String) -> String? = { musicRepository.getCoverArtUrl(it, 128) }

    fun loadArtists() {
        if (artistsLoaded) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val artists = musicRepository.getArtists()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    artists = artists,
                    coverArtUrlBuilder = coverArtBuilder
                )
                artistsLoaded = true
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.localizedMessage)
            }
        }
    }

    fun loadAlbums() {
        if (albumsLoaded) return
        albumsLoaded = true
        loadAlbumsWith(_uiState.value.albumSort)
    }

    fun setAlbumSort(sort: AlbumSort) {
        if (sort == _uiState.value.albumSort) return
        _uiState.value = _uiState.value.copy(albumSort = sort)
        loadAlbumsWith(sort)
    }

    private fun loadAlbumsWith(sort: AlbumSort) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val albums = musicRepository.getAlbumList(sort.type, size = 200)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    albums = albums,
                    coverArtUrlBuilder = coverArtBuilder
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.localizedMessage)
            }
        }
    }

    fun loadGenres() {
        if (genresLoaded) return
        genresLoaded = true
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val genres = musicRepository.getGenres()
                _uiState.value = _uiState.value.copy(isLoading = false, genres = genres)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.localizedMessage)
            }
        }
    }

    fun loadPlaylists() {
        if (playlistsLoaded) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val playlists = musicRepository.getPlaylists()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    playlists = playlists,
                    coverArtUrlBuilder = coverArtBuilder
                )
                playlistsLoaded = true
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.localizedMessage)
            }
        }
    }

    fun loadFavorites() {
        if (favoritesLoaded) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val starred = musicRepository.getStarred()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    starredArtists = starred.artist ?: emptyList(),
                    starredAlbums = starred.album ?: emptyList(),
                    starredSongs = starred.song ?: emptyList(),
                    coverArtUrlBuilder = coverArtBuilder
                )
                favoritesLoaded = true
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.localizedMessage)
            }
        }
    }

    fun loadRadios() {
        if (radiosLoaded) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val stations = musicRepository.getRadioStations()
                _uiState.value = _uiState.value.copy(isLoading = false, radioStations = stations)
                radiosLoaded = true
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.localizedMessage)
            }
        }
    }

    fun playRadioStation(station: InternetRadioStation) = playbackManager.playRadioStation(station)

    fun playStarredSong(song: SongItem) {
        val songs = _uiState.value.starredSongs
        val index = songs.indexOf(song).coerceAtLeast(0)
        playbackManager.playSongs(songs, index)
    }

    fun playNext(song: SongItem) = playbackManager.playNext(listOf(song))
    fun addToQueue(song: SongItem) = playbackManager.addToQueue(listOf(song))

    /** Fetch an album's tracks and start playing it. */
    fun playAlbum(albumId: String) {
        viewModelScope.launch {
            val songs = runCatching { musicRepository.getAlbumDetail(albumId)?.song }.getOrNull().orEmpty()
            if (songs.isNotEmpty()) playbackManager.playSongs(songs)
        }
    }

    /** Fetch every song by an artist (across all their albums) and shuffle-play it. */
    fun shufflePlayArtist(artistId: String) {
        viewModelScope.launch {
            val songs = runCatching { musicRepository.getArtistSongs(artistId) }.getOrDefault(emptyList())
            if (songs.isNotEmpty()) playbackManager.playSongs(songs.shuffled())
        }
    }

    /** Fetch a playlist's tracks and shuffle-play it. */
    fun shufflePlayPlaylist(playlistId: String) {
        viewModelScope.launch {
            val songs = runCatching { musicRepository.getPlaylistDetail(playlistId)?.entry }.getOrNull().orEmpty()
            if (songs.isNotEmpty()) playbackManager.playSongs(songs.shuffled())
        }
    }

    /**
     * Re-fetch the visible tab for pull-to-refresh. Suspends until done so the
     * caller can stop the refresh indicator, and never flips isLoading — the
     * current list stays on screen with the pull spinner over it.
     */
    suspend fun refresh(tab: LibraryTab) {
        try {
            when (tab) {
                LibraryTab.Artists -> {
                    val artists = musicRepository.getArtists()
                    _uiState.value = _uiState.value.copy(
                        artists = artists, coverArtUrlBuilder = coverArtBuilder, error = null
                    )
                    artistsLoaded = true
                }
                LibraryTab.Albums -> {
                    val albums = musicRepository.getAlbumList(_uiState.value.albumSort.type, size = 200)
                    _uiState.value = _uiState.value.copy(
                        albums = albums, coverArtUrlBuilder = coverArtBuilder, error = null
                    )
                    albumsLoaded = true
                }
                LibraryTab.Genres -> {
                    val genres = musicRepository.getGenres()
                    _uiState.value = _uiState.value.copy(genres = genres, error = null)
                    genresLoaded = true
                }
                LibraryTab.Playlists -> {
                    val playlists = musicRepository.getPlaylists()
                    _uiState.value = _uiState.value.copy(
                        playlists = playlists, coverArtUrlBuilder = coverArtBuilder, error = null
                    )
                    playlistsLoaded = true
                }
                LibraryTab.Favorites -> {
                    val starred = musicRepository.getStarred()
                    _uiState.value = _uiState.value.copy(
                        starredArtists = starred.artist ?: emptyList(),
                        starredAlbums = starred.album ?: emptyList(),
                        starredSongs = starred.song ?: emptyList(),
                        coverArtUrlBuilder = coverArtBuilder,
                        error = null
                    )
                    favoritesLoaded = true
                }
                LibraryTab.Radios -> {
                    val stations = musicRepository.getRadioStations()
                    _uiState.value = _uiState.value.copy(radioStations = stations, error = null)
                    radiosLoaded = true
                }
            }
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(error = e.localizedMessage)
        }
    }
}
