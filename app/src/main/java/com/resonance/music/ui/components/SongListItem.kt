package com.resonance.music.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.resonance.music.R
import com.resonance.music.data.api.models.SongItem

@Composable
fun SongListItem(
    song: SongItem,
    trackNumber: Int? = null,
    onClick: () -> Unit,
    actions: SongActions = SongActions()
) {
    var showMenu by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = {
            Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Text(
                text = listOfNotNull(song.artist, song.duration?.let { formatSongDuration(it) })
                    .joinToString(" \u2022 "),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingContent = {
            if (trackNumber != null) {
                Text(
                    text = trackNumber.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(24.dp)
                )
            } else {
                Icon(
                    Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        trailingContent = {
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.content_desc_more_options))
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    actions.onPlayNext?.let { action ->
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_play_next)) },
                            onClick = { showMenu = false; action() },
                            leadingIcon = { Icon(Icons.Default.QueuePlayNext, contentDescription = null) }
                        )
                    }
                    actions.onAddToQueue?.let { action ->
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_add_to_queue)) },
                            onClick = { showMenu = false; action() },
                            leadingIcon = { Icon(Icons.Default.PlaylistAdd, contentDescription = null) }
                        )
                    }
                    actions.onAddToPlaylist?.let { action ->
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_add_to_playlist)) },
                            onClick = { showMenu = false; action() },
                            leadingIcon = { Icon(Icons.Default.LibraryAdd, contentDescription = null) }
                        )
                    }
                    actions.onRemoveFromPlaylist?.let { action ->
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_remove_from_playlist)) },
                            onClick = { showMenu = false; action() },
                            leadingIcon = { Icon(Icons.Default.PlaylistRemove, contentDescription = null) }
                        )
                    }
                    actions.onGoToAlbum?.let { action ->
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_go_to_album)) },
                            onClick = { showMenu = false; action() },
                            leadingIcon = { Icon(Icons.Default.Album, contentDescription = null) }
                        )
                    }
                    actions.onGoToArtist?.let { action ->
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_go_to_artist)) },
                            onClick = { showMenu = false; action() },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) }
                        )
                    }
                }
            }
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

private fun formatSongDuration(seconds: Int): String {
    val min = seconds / 60
    val sec = seconds % 60
    return "%d:%02d".format(min, sec)
}
