package com.resonance.music.ui.screens.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resonance.music.data.api.models.PlaylistItem
import com.resonance.music.data.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AddToPlaylistUiState(
    val isLoading: Boolean = false,
    val playlists: List<PlaylistItem> = emptyList(),
    val error: String? = null,
    val success: Boolean = false
)

/** Backs the "add to playlist" picker shown from any song's overflow menu. */
@HiltViewModel
class AddToPlaylistViewModel @Inject constructor(
    private val musicRepository: MusicRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddToPlaylistUiState())
    val uiState: StateFlow<AddToPlaylistUiState> = _uiState.asStateFlow()

    fun loadPlaylists() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val playlists = musicRepository.getPlaylists()
                _uiState.value = _uiState.value.copy(isLoading = false, playlists = playlists)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.localizedMessage)
            }
        }
    }

    fun addToExistingPlaylist(playlistId: String, songId: String) {
        viewModelScope.launch {
            try {
                musicRepository.addSongToPlaylist(playlistId, songId)
                _uiState.value = _uiState.value.copy(success = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.localizedMessage)
            }
        }
    }

    fun createPlaylistWithSong(name: String, songId: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            try {
                musicRepository.createPlaylist(name, listOf(songId))
                _uiState.value = _uiState.value.copy(success = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.localizedMessage)
            }
        }
    }

    fun consumeSuccess() {
        _uiState.value = _uiState.value.copy(success = false)
    }
}
