package com.resonance.music.ui.screens.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.resonance.music.R
import com.resonance.music.data.api.models.SongItem
import com.resonance.music.ui.components.AddToPlaylistDialog
import com.resonance.music.ui.components.AlbumCard
import com.resonance.music.ui.components.AlbumListItem
import com.resonance.music.ui.components.PlaylistNameDialog
import com.resonance.music.ui.components.SlimTopBar
import com.resonance.music.ui.components.SongActions
import com.resonance.music.ui.components.SongListItem

enum class LibraryTab { Artists, Albums, Genres, Playlists, Radios, Favorites }

private fun LibraryTab.labelRes(): Int = when (this) {
    LibraryTab.Artists -> R.string.label_artists
    LibraryTab.Albums -> R.string.label_albums
    LibraryTab.Genres -> R.string.label_genres
    LibraryTab.Playlists -> R.string.label_playlists
    LibraryTab.Radios -> R.string.label_radios
    LibraryTab.Favorites -> R.string.label_favorites
}

private fun AlbumSort.labelRes(): Int = when (this) {
    AlbumSort.AZ -> R.string.sort_az
    AlbumSort.NEWEST -> R.string.sort_newest
    AlbumSort.RECENT -> R.string.sort_recent
    AlbumSort.FREQUENT -> R.string.sort_frequent
    AlbumSort.RANDOM -> R.string.sort_random
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onArtistClick: (String) -> Unit,
    onAlbumClick: (String) -> Unit,
    onPlaylistClick: (String) -> Unit,
    onGenreClick: (String) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    var selectedTab by remember { mutableStateOf(LibraryTab.Artists) }
    val uiState by viewModel.uiState.collectAsState()
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var addToPlaylistSong by remember { mutableStateOf<SongItem?>(null) }

    // Load data when tab changes
    LaunchedEffect(selectedTab) {
        when (selectedTab) {
            LibraryTab.Artists -> viewModel.loadArtists()
            LibraryTab.Albums -> viewModel.loadAlbums()
            LibraryTab.Genres -> viewModel.loadGenres()
            LibraryTab.Playlists -> viewModel.loadPlaylists()
            LibraryTab.Radios -> viewModel.loadRadios()
            LibraryTab.Favorites -> viewModel.loadFavorites()
        }
    }

    val pullState = rememberPullToRefreshState()
    if (pullState.isRefreshing) {
        LaunchedEffect(true) {
            viewModel.refresh(selectedTab)
            pullState.endRefresh()
        }
    }

    if (showCreatePlaylistDialog) {
        PlaylistNameDialog(
            title = stringResource(R.string.action_new_playlist),
            confirmLabel = stringResource(R.string.action_create),
            onConfirm = { name ->
                showCreatePlaylistDialog = false
                viewModel.createPlaylist(name)
            },
            onDismiss = { showCreatePlaylistDialog = false }
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
            SlimTopBar(stringResource(R.string.library_title))
        },
        floatingActionButton = {
            if (selectedTab == LibraryTab.Playlists) {
                FloatingActionButton(onClick = { showCreatePlaylistDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_new_playlist))
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            ScrollableTabRow(selectedTabIndex = selectedTab.ordinal, edgePadding = 0.dp) {
                LibraryTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = { Text(stringResource(tab.labelRes())) }
                    )
                }
            }

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                Box(modifier = Modifier.fillMaxSize().nestedScroll(pullState.nestedScrollConnection)) {
                    when (selectedTab) {
                        LibraryTab.Artists -> ArtistsTab(uiState, onArtistClick, viewModel::shufflePlayArtist)
                        LibraryTab.Albums -> AlbumsTab(uiState, onAlbumClick, viewModel::setAlbumSort, viewModel::playAlbum)
                        LibraryTab.Genres -> GenresTab(uiState, onGenreClick)
                        LibraryTab.Playlists -> PlaylistsTab(uiState, onPlaylistClick, viewModel::shufflePlayPlaylist)
                        LibraryTab.Radios -> RadiosTab(uiState, viewModel::playRadioStation)
                        LibraryTab.Favorites -> FavoritesTab(uiState, onAlbumClick, onArtistClick, viewModel) { addToPlaylistSong = it }
                    }
                    PullToRefreshContainer(
                        state = pullState,
                        // The 1.2.x container draws its circle even at rest; fade it with the
                        // pull progress so it only appears while pulling or refreshing.
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .graphicsLayer {
                                alpha = if (pullState.isRefreshing) 1f
                                        else pullState.progress.coerceIn(0f, 1f)
                            }
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtistsTab(
    uiState: LibraryUiState,
    onArtistClick: (String) -> Unit,
    onShuffleClick: (String) -> Unit
) {
    if (uiState.artists.isEmpty()) {
        EmptyState(Icons.Default.Person, stringResource(R.string.label_artists), stringResource(R.string.library_no_artists_found))
    } else {
        LazyColumn(contentPadding = PaddingValues(bottom = 12.dp)) {
            items(uiState.artists, key = { it.id }, contentType = { "artist" }) { artist ->
                ListItem(
                    headlineContent = {
                        Text(artist.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    supportingContent = {
                        artist.albumCount?.let { Text(pluralStringResource(R.plurals.album_count, it, it)) }
                    },
                    leadingContent = {
                        Icon(Icons.Default.Person, contentDescription = null)
                    },
                    trailingContent = {
                        IconButton(onClick = { onShuffleClick(artist.id) }) {
                            Icon(Icons.Default.Shuffle, contentDescription = stringResource(R.string.action_shuffle))
                        }
                    },
                    modifier = Modifier.clickable { onArtistClick(artist.id) }
                )
            }
        }
    }
}

@Composable
private fun AlbumsTab(
    uiState: LibraryUiState,
    onAlbumClick: (String) -> Unit,
    onSortChange: (AlbumSort) -> Unit,
    onPlayClick: (String) -> Unit
) {
    Column {
        var menuOpen by remember { mutableStateOf(false) }
        Box(modifier = Modifier.padding(start = 8.dp, top = 4.dp)) {
            TextButton(onClick = { menuOpen = true }) {
                Icon(Icons.Default.Sort, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(uiState.albumSort.labelRes()))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                AlbumSort.entries.forEach { sort ->
                    DropdownMenuItem(
                        text = { Text(stringResource(sort.labelRes())) },
                        onClick = {
                            menuOpen = false
                            onSortChange(sort)
                        },
                        trailingIcon = {
                            if (sort == uiState.albumSort) {
                                Icon(Icons.Default.Check, contentDescription = null)
                            }
                        }
                    )
                }
            }
        }
        if (uiState.albums.isEmpty()) {
            EmptyState(Icons.Default.Album, stringResource(R.string.label_albums), stringResource(R.string.library_no_albums_found))
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(uiState.albums, key = { it.id }) { album ->
                    AlbumCard(
                        album = album,
                        coverArtUrl = album.coverArt?.let { uiState.coverArtUrlBuilder?.invoke(it) },
                        modifier = Modifier.fillMaxWidth(),
                        onPlayClick = { onPlayClick(album.id) },
                        onClick = { onAlbumClick(album.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun GenresTab(uiState: LibraryUiState, onGenreClick: (String) -> Unit) {
    if (uiState.genres.isEmpty()) {
        EmptyState(Icons.Default.Category, stringResource(R.string.label_genres), stringResource(R.string.library_no_genres_found))
    } else {
        LazyColumn(contentPadding = PaddingValues(bottom = 12.dp)) {
            items(uiState.genres, key = { it.name }, contentType = { "genre" }) { genre ->
                ListItem(
                    headlineContent = { Text(genre.name) },
                    supportingContent = { genre.albumCount?.let { Text(pluralStringResource(R.plurals.album_count, it, it)) } },
                    leadingContent = { Icon(Icons.Default.Category, contentDescription = null) },
                    modifier = Modifier.clickable { onGenreClick(genre.name) }
                )
            }
        }
    }
}

@Composable
private fun PlaylistsTab(
    uiState: LibraryUiState,
    onPlaylistClick: (String) -> Unit,
    onShuffleClick: (String) -> Unit
) {
    if (uiState.playlists.isEmpty()) {
        EmptyState(Icons.AutoMirrored.Filled.QueueMusic, stringResource(R.string.label_playlists), stringResource(R.string.library_no_playlists_found))
    } else {
        LazyColumn(contentPadding = PaddingValues(bottom = 12.dp)) {
            items(uiState.playlists, key = { it.id }, contentType = { "playlist" }) { playlist ->
                ListItem(
                    headlineContent = {
                        Text(playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    supportingContent = {
                        val details = listOfNotNull(
                            playlist.songCount?.let { pluralStringResource(R.plurals.song_count, it, it) },
                            playlist.owner
                        ).joinToString(" \u2022 ")
                        if (details.isNotEmpty()) Text(details)
                    },
                    leadingContent = {
                        Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null)
                    },
                    trailingContent = {
                        IconButton(onClick = { onShuffleClick(playlist.id) }) {
                            Icon(Icons.Default.Shuffle, contentDescription = stringResource(R.string.action_shuffle))
                        }
                    },
                    modifier = Modifier.clickable { onPlaylistClick(playlist.id) }
                )
            }
        }
    }
}

@Composable
private fun RadiosTab(
    uiState: LibraryUiState,
    onPlayClick: (com.resonance.music.data.api.models.InternetRadioStation) -> Unit
) {
    if (uiState.radioStations.isEmpty()) {
        EmptyState(Icons.Default.Radio, stringResource(R.string.label_radios), stringResource(R.string.library_no_radios_found))
    } else {
        LazyColumn(contentPadding = PaddingValues(bottom = 12.dp)) {
            items(uiState.radioStations, key = { it.id }, contentType = { "radio" }) { station ->
                ListItem(
                    headlineContent = {
                        Text(station.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    supportingContent = station.homePageUrl?.let { url ->
                        { Text(url, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    },
                    leadingContent = {
                        Icon(Icons.Default.Radio, contentDescription = null)
                    },
                    trailingContent = {
                        IconButton(onClick = { onPlayClick(station) }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.action_play))
                        }
                    },
                    modifier = Modifier.clickable { onPlayClick(station) }
                )
            }
        }
    }
}

@Composable
private fun FavoritesTab(
    uiState: LibraryUiState,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    viewModel: LibraryViewModel,
    onAddToPlaylistClick: (SongItem) -> Unit
) {
    val hasContent = uiState.starredArtists.isNotEmpty() ||
            uiState.starredAlbums.isNotEmpty() ||
            uiState.starredSongs.isNotEmpty()

    if (!hasContent) {
        EmptyState(Icons.Default.Favorite, stringResource(R.string.label_favorites), stringResource(R.string.library_no_favorites_yet))
    } else {
        LazyColumn(contentPadding = PaddingValues(bottom = 12.dp)) {
            if (uiState.starredArtists.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.label_artists),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                items(uiState.starredArtists, key = { it.id }, contentType = { "artist" }) { artist ->
                    ListItem(
                        headlineContent = { Text(artist.name) },
                        leadingContent = { Icon(Icons.Default.Person, contentDescription = null) },
                        trailingContent = {
                            IconButton(onClick = { viewModel.shufflePlayArtist(artist.id) }) {
                                Icon(Icons.Default.Shuffle, contentDescription = stringResource(R.string.action_shuffle))
                            }
                        },
                        modifier = Modifier.clickable { onArtistClick(artist.id) }
                    )
                }
            }

            if (uiState.starredAlbums.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.label_albums),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                items(uiState.starredAlbums, key = { it.id }, contentType = { "album" }) { album ->
                    AlbumListItem(
                        album = album,
                        coverArtUrl = album.coverArt?.let { uiState.coverArtUrlBuilder?.invoke(it) },
                        onPlayClick = { viewModel.playAlbum(album.id) },
                        onClick = { onAlbumClick(album.id) }
                    )
                }
            }

            if (uiState.starredSongs.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.label_songs),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                items(uiState.starredSongs, key = { it.id }, contentType = { "song" }) { song ->
                    SongListItem(
                        song = song,
                        onClick = { viewModel.playStarredSong(song) },
                        actions = SongActions(
                            onPlayNext = { viewModel.playNext(song) },
                            onAddToQueue = { viewModel.addToQueue(song) },
                            onAddToPlaylist = { onAddToPlaylistClick(song) },
                            onGoToArtist = song.artistId?.let { id -> { onArtistClick(id) } },
                            onGoToAlbum = song.albumId?.let { id -> { onAlbumClick(id) } }
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
