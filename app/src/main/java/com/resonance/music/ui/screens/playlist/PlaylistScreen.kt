package com.resonance.music.ui.screens.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.resonance.music.R
import com.resonance.music.data.api.models.SongItem
import com.resonance.music.ui.components.AddToPlaylistDialog
import com.resonance.music.ui.components.PlaylistNameDialog
import com.resonance.music.ui.components.SongActions
import com.resonance.music.ui.components.SongListItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistScreen(
    playlistId: String,
    onBackClick: () -> Unit,
    onAlbumClick: (String) -> Unit = {},
    onArtistClick: (String) -> Unit = {},
    viewModel: PlaylistViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var addToPlaylistSong by remember { mutableStateOf<SongItem?>(null) }

    LaunchedEffect(playlistId) {
        viewModel.loadPlaylist(playlistId)
    }

    LaunchedEffect(uiState.deleted) {
        if (uiState.deleted) onBackClick()
    }

    if (showRenameDialog) {
        PlaylistNameDialog(
            title = stringResource(R.string.action_rename_playlist),
            confirmLabel = stringResource(R.string.action_save),
            initialName = uiState.name,
            onConfirm = { name ->
                showRenameDialog = false
                viewModel.rename(name)
            },
            onDismiss = { showRenameDialog = false }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.action_delete_playlist)) },
            text = { Text(stringResource(R.string.playlist_delete_confirm_message, uiState.name)) },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; viewModel.delete() }) {
                    Text(stringResource(R.string.action_delete_playlist))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    uiState.actionError?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::clearActionError,
            title = { Text(stringResource(R.string.error_generic)) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = viewModel::clearActionError) { Text(stringResource(R.string.action_ok)) }
            }
        )
    }

    addToPlaylistSong?.let { song ->
        AddToPlaylistDialog(
            song = song,
            onDismiss = { addToPlaylistSong = null },
            onAdded = { addToPlaylistSong = null }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        uiState.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.content_desc_back))
                    }
                },
                actions = {
                    var showPlaylistMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { showPlaylistMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.playlist_options))
                        }
                        DropdownMenu(expanded = showPlaylistMenu, onDismissRequest = { showPlaylistMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_play_next)) },
                                onClick = { showPlaylistMenu = false; viewModel.playAllNext() },
                                leadingIcon = { Icon(Icons.Default.QueuePlayNext, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_add_to_queue)) },
                                onClick = { showPlaylistMenu = false; viewModel.addAllToQueue() },
                                leadingIcon = { Icon(Icons.Default.PlaylistAdd, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_rename_playlist)) },
                                onClick = { showPlaylistMenu = false; showRenameDialog = true },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_delete_playlist)) },
                                onClick = { showPlaylistMenu = false; showDeleteConfirm = true },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (uiState.error != null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(uiState.error ?: stringResource(R.string.error_generic), color = MaterialTheme.colorScheme.error)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 12.dp)
            ) {
                // Playlist header
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(200.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            if (uiState.coverArtUrl != null) {
                                AsyncImage(
                                    model = uiState.coverArtUrl,
                                    contentDescription = stringResource(R.string.content_desc_playlist_art),
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    Icons.AutoMirrored.Filled.QueueMusic,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = uiState.name,
                            style = MaterialTheme.typography.headlineSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        uiState.songCount?.let { count ->
                            Text(
                                text = pluralStringResource(R.plurals.song_count, count, count),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        uiState.comment?.let { comment ->
                            if (comment.isNotBlank()) {
                                Text(
                                    text = comment,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = { viewModel.playAll(shuffle = false) }) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.action_play))
                            }
                            OutlinedButton(onClick = { viewModel.playAll(shuffle = true) }) {
                                Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.action_shuffle))
                            }
                        }
                    }
                }

                // Song list
                itemsIndexed(uiState.songs, key = { index, song -> "${song.id}_$index" }) { index, song ->
                    SongListItem(
                        song = song,
                        trackNumber = index + 1,
                        onClick = { viewModel.playSongAt(index) },
                        actions = SongActions(
                            onPlayNext = { viewModel.playNext(song) },
                            onAddToQueue = { viewModel.addToQueue(song) },
                            onAddToPlaylist = { addToPlaylistSong = song },
                            onRemoveFromPlaylist = { viewModel.removeSongAt(index) },
                            onGoToArtist = song.artistId?.let { id -> { onArtistClick(id) } },
                            onGoToAlbum = song.albumId?.let { id -> { onAlbumClick(id) } }
                        )
                    )
                }
            }
        }
    }
}
