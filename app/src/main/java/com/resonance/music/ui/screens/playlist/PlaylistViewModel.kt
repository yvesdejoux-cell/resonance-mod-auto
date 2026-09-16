package com.resonance.music.ui.screens.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resonance.music.data.api.models.SongItem
import com.resonance.music.data.repository.MusicRepository
import com.resonance.music.playback.PlaybackManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlaylistUiState(
    val isLoading: Boolean = false,
    val name: String = "",
    val comment: String? = null,
    val songCount: Int? = null,
    val coverArtUrl: String? = null,
    val songs: List<SongItem> = emptyList(),
    val error: String? = null,
    val actionError: String? = null,
    val deleted: Boolean = false
)

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val playbackManager: PlaybackManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlaylistUiState())
    val uiState: StateFlow<PlaylistUiState> = _uiState.asStateFlow()

    private var playlistId: String? = null

    fun loadPlaylist(playlistId: String) {
        this.playlistId = playlistId
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val detail = musicRepository.getPlaylistDetail(playlistId)
                if (detail != null) {
                    _uiState.value = PlaylistUiState(
                        name = detail.name,
                        comment = detail.comment,
                        songCount = detail.songCount,
                        coverArtUrl = detail.coverArt?.let { musicRepository.getCoverArtUrl(it) },
                        songs = detail.entry ?: emptyList()
                    )
                } else {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = "Playlist not found")
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.localizedMessage ?: "Failed to load")
            }
        }
    }

    fun rename(name: String) {
        val id = playlistId ?: return
        if (name.isBlank() || name == _uiState.value.name) return
        viewModelScope.launch {
            try {
                musicRepository.renamePlaylist(id, name)
                _uiState.value = _uiState.value.copy(name = name)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(actionError = e.localizedMessage)
            }
        }
    }

    fun delete() {
        val id = playlistId ?: return
        viewModelScope.launch {
            try {
                musicRepository.deletePlaylist(id)
                _uiState.value = _uiState.value.copy(deleted = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(actionError = e.localizedMessage)
            }
        }
    }

    fun removeSongAt(index: Int) {
        val id = playlistId ?: return
        val songs = _uiState.value.songs
        if (index !in songs.indices) return
        viewModelScope.launch {
            try {
                musicRepository.removeSongFromPlaylist(id, index)
                _uiState.value = _uiState.value.copy(
                    songs = songs.filterIndexed { i, _ -> i != index },
                    songCount = (_uiState.value.songCount ?: songs.size) - 1
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(actionError = e.localizedMessage)
            }
        }
    }

    fun clearActionError() {
        _uiState.value = _uiState.value.copy(actionError = null)
    }

    fun playAll(shuffle: Boolean) {
        val songs = _uiState.value.songs
        if (songs.isEmpty()) return
        val list = if (shuffle) songs.shuffled() else songs
        playbackManager.playSongs(list)
    }

    fun playSongAt(index: Int) {
        val songs = _uiState.value.songs
        if (songs.isEmpty()) return
        playbackManager.playSongs(songs, index)
    }

    fun playNext(song: SongItem) = playbackManager.playNext(listOf(song))
    fun addToQueue(song: SongItem) = playbackManager.addToQueue(listOf(song))
    fun playAllNext() = _uiState.value.songs.takeIf { it.isNotEmpty() }?.let { playbackManager.playNext(it) }
    fun addAllToQueue() = _uiState.value.songs.takeIf { it.isNotEmpty() }?.let { playbackManager.addToQueue(it) }
}
