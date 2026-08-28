package com.example.song.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import kotlinx.coroutines.delay
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import coil.compose.AsyncImage
import com.example.song.SongApplication
import com.example.song.data.model.Playlist
import com.example.song.data.model.Song
import com.example.song.ui.components.SongListItem
import com.example.song.util.dragGestureHandler
import com.example.song.viewmodel.DownloadState
import com.example.song.viewmodel.SongViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: SongViewModel,
    onPlaylistClick: (Playlist) -> Unit,
    onFavoritesClick: () -> Unit,
    onSongClick: () -> Unit
) {
    val songs by viewModel.filteredSongs.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val favoriteSongs by viewModel.favoriteSongs.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val downloadState by viewModel.downloadState.collectAsState()
    val isExtracting by viewModel.isExtracting.collectAsState()
    val pendingDownloadItems by viewModel.pendingDownloadItems.collectAsState()
    val isEngineReady by SongApplication.getInstance().isReady.collectAsState()
    var isSearching by remember { mutableStateOf(false) }
    var showAddMenu by remember { mutableStateOf(false) }
    var showPlaylistDialog by remember { mutableStateOf(false) }
    var showDownloadDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var youtubeUrl by remember { mutableStateOf("") }
    val isUrlValid = remember(youtubeUrl) { 
        youtubeUrl.isBlank() || (youtubeUrl.startsWith("http") && 
            (youtubeUrl.contains("youtube.com") || youtubeUrl.contains("youtu.be") || youtubeUrl.contains("spotify.com")))
    }

    val addIconRotation by animateFloatAsState(
        targetValue = if (showAddMenu) 135f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "AddIconRotation"
    )
    
    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? ->
            uri?.let {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        it,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                val song = Song(
                    title = it.lastPathSegment ?: "Unknown",
                    audioUri = it.toString(),
                    artist = "Unknown Artist"
                )
                viewModel.addSong(song)
            }
        }
    )

    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    val selectedSongIds by viewModel.selectedSongIds.collectAsState()
    val currentSong by viewModel.currentPlayingSong.collectAsState()
    
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Auto-scroll logic handled by multiSelectDragHandler

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                CenterAlignedTopAppBar(
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent
                    ),
                    title = {
                        if (isSearching) {
                            TextField(
                                value = searchQuery,
                                onValueChange = { viewModel.setSearchQuery(it) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                placeholder = { Text("Search songs...") },
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.White.copy(alpha = 0.2f),
                                    unfocusedContainerColor = Color.White.copy(alpha = 0.1f),
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                shape = RoundedCornerShape(24.dp),
                                trailingIcon = {
                                    IconButton(onClick = { 
                                        isSearching = false
                                        viewModel.setSearchQuery("")
                                    }) {
                                        Icon(Icons.Default.Close, contentDescription = "Close search")
                                    }
                                }
                            )
                        } else {
                            Text(
                                "My Library",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF333333)
                                )
                            )
                        }
                    },
                    navigationIcon = {
                        if (!isSearching) {
                            IconButton(
                                onClick = { isSearching = true },
                                modifier = Modifier.background(Color.White.copy(alpha = 0.3f), CircleShape)
                            ) {
                                Icon(Icons.Default.Search, contentDescription = "Search", tint = Color(0xFF424242))
                            }
                        }
                    },
                    actions = {
                        if (!isSearching) {
                            IconButton(
                                onClick = { showAddMenu = !showAddMenu },
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(Color.White.copy(alpha = 0.3f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add Options",
                                    tint = Color(0xFF424242),
                                    modifier = Modifier
                                        .size(28.dp)
                                        .rotate(addIconRotation)
                                )
                            }
                        }
                    }
                )
            }
        ) { padding ->
            Column(modifier = Modifier.padding(padding)) {
                if (isExtracting || downloadState is DownloadState.Downloading) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White.copy(alpha = 0.8f),
                        shadowElevation = 8.dp,
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                color = Color(0xFFE91E63),
                                trackColor = Color.Black.copy(alpha = 0.05f)
                            )
                            
                            val progressText = if (isExtracting) {
                                "Checking link..."
                            } else {
                                val state = downloadState as DownloadState.Downloading
                                if (state.total > 1) {
                                    "Batch Download: ${state.current} of ${state.total} (${state.progress.toInt()}%)"
                                } else {
                                    "Downloading... ${state.progress.toInt()}%"
                                }
                            }
                            
                            Text(
                                text = progressText,
                                modifier = Modifier.padding(top = 8.dp),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFFE91E63)
                            )
                        }
                    }
                }
                
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .dragGestureHandler(
                            listState = listState,
                            onSelectStart = { key ->
                                if (key is Int) {
                                    viewModel.startRangeSelection(key, songs.map { it.id })
                                }
                            },
                            onSelectUpdate = { key ->
                                if (key is Int) {
                                    viewModel.updateRangeSelection(key, songs.map { it.id })
                                }
                            },
                            onSelectEnd = {
                                viewModel.endRangeSelection()
                            }
                        ),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    if (!isSearching) {
                        // Collections Section (Playlists)
                        item {
                            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                                Text(
                                    "Collections",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF424242)
                                    )
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    contentPadding = PaddingValues(end = 24.dp)
                                ) {
                                    items(playlists, key = { it.id }) { playlist ->
                                        PlaylistCard(playlist, viewModel) { onPlaylistClick(playlist) }
                                    }
                                    // Hardcoded Favorites Card
                                    item {
                                        FavoritesCollectionCard(favoriteSongs.size) {
                                            onFavoritesClick()
                                        }
                                    }
                                }
                            }
                        }

                        // Recommended Section Header
                        item {
                            Text(
                                "Library Tracks",
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF424242)
                                )
                            )
                        }
                    }

                    items(songs, key = { it.id }) { song ->
                        SongListItem(
                            song = song,
                            onPlayClick = {
                                if (isSelectionMode) {
                                    viewModel.toggleSongSelection(song.id)
                                } else {
                                    viewModel.playSong(song, songs)
                                    onSongClick()
                                }
                            },
                            onFavoriteToggle = {
                                viewModel.updateFavorite(song, !song.isFavorite)
                            },
                            onDelete = {
                                viewModel.deleteSong(song.id)
                            },
                            isSelected = selectedSongIds.contains(song.id),
                            onLongClick = {
                                viewModel.toggleSelectionMode(true)
                                viewModel.toggleSongSelection(song.id)
                            },
                            selectionMode = isSelectionMode,
                            isPlaying = currentSong?.id == song.id
                        )
                    }
                }
            }
        }

        // Click-away layer
        if (showAddMenu) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { showAddMenu = false }
            )
        }

        // Add Options Menu
        AnimatedVisibility(
            visible = showAddMenu,
            enter = fadeIn() + scaleIn(initialScale = 0.4f, transformOrigin = TransformOrigin(0.9f, 0.1f)),
            exit = fadeOut() + scaleOut(targetScale = 0.4f, transformOrigin = TransformOrigin(0.9f, 0.1f)),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 70.dp, end = 16.dp)
                .zIndex(10f)
        ) {
            Box(
                modifier = Modifier
                    .width(260.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color.White.copy(alpha = 0.4f))
                    .border(1.5.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(28.dp))
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AddMenuOption(
                        text = "Create Playlist",
                        icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                        onClick = {
                            showAddMenu = false
                            showPlaylistDialog = true
                        }
                    )
                    AddMenuOption(
                        text = "Add Songs",
                        icon = Icons.Default.LibraryMusic,
                        onClick = {
                            showAddMenu = false
                            launcher.launch(arrayOf("audio/*"))
                        }
                    )
                    AddMenuOption(
                        text = "Download from YT & Spotify",
                        icon = Icons.Default.CloudDownload,
                        onClick = {
                            showAddMenu = false
                            showDownloadDialog = true
                        }
                    )
                }
            }
        }

        if (showPlaylistDialog) {
            AlertDialog(
                onDismissRequest = { showPlaylistDialog = false },
                title = { Text("New Playlist") },
                text = {
                    TextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
                        placeholder = { Text("Playlist Name") },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        )
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        if (newPlaylistName.isNotBlank()) {
                            viewModel.createPlaylist(newPlaylistName)
                            newPlaylistName = ""
                            showPlaylistDialog = false
                        }
                    }) {
                        Text("Create")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPlaylistDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
        
        if (showDownloadDialog) {
            AlertDialog(
                onDismissRequest = { 
                    if (downloadState !is DownloadState.Downloading) {
                        showDownloadDialog = false
                    }
                },
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, tint = Color(0xFFE91E63))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Import from Link")
                    }
                },
                text = {
                    Column {
                        if (isExtracting) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(color = Color(0xFFE91E63))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Extracting...", style = MaterialTheme.typography.labelMedium)
                            }
                        } else {
                            Text(
                                "Enter a YouTube or Spotify URL to extract and download audio.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            TextField(
                                value = youtubeUrl,
                                onValueChange = { youtubeUrl = it },
                                placeholder = { Text("https://youtube.com/...") },
                                singleLine = true,
                                isError = !isUrlValid && youtubeUrl.isNotBlank(),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Black.copy(alpha = 0.05f),
                                    unfocusedContainerColor = Color.Black.copy(alpha = 0.03f),
                                    focusedIndicatorColor = Color(0xFFE91E63),
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            if (!isUrlValid && youtubeUrl.isNotBlank()) {
                                Text(
                                    "Invalid URL (YouTube or Spotify only)",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(top = 4.dp, start = 8.dp)
                                )
                            }

                            if (downloadState is DownloadState.Error) {
                                Text(
                                    (downloadState as DownloadState.Error).message,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    if (downloadState is DownloadState.Downloading) {
                        TextButton(
                            onClick = { viewModel.cancelDownload() },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Cancel Download")
                        }
                    } else {
                        Button(
                            onClick = {
                                if (youtubeUrl.isNotBlank() && isUrlValid && isEngineReady) {
                                    val urlToDownload = youtubeUrl.trim()
                                    youtubeUrl = ""
                                    viewModel.fetchDownloadMetadata(urlToDownload)
                                    showDownloadDialog = false
                                }
                            },
                            enabled = youtubeUrl.isNotBlank() && isUrlValid && isEngineReady,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFE91E63),
                                disabledContainerColor = Color(0xFFE91E63).copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(if (isEngineReady) "Download" else "Initializing...")
                        }
                    }
                },
                dismissButton = {
                    if (downloadState !is DownloadState.Downloading) {
                        TextButton(onClick = { 
                            showDownloadDialog = false 
                            viewModel.resetDownloadState()
                        }) {
                            Text("Close", color = Color.Gray)
                        }
                    }
                },
                shape = RoundedCornerShape(28.dp),
                containerColor = Color.White
            )
        }

        // Batch Download Options Dialog
        if (pendingDownloadItems.isNotEmpty()) {
            val playlistItem = pendingDownloadItems.find { it.isPlaylist }
            val firstTrack = pendingDownloadItems.find { !it.isPlaylist }
            
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { viewModel.clearPendingDownloadItems() },
                properties = androidx.compose.ui.window.DialogProperties(
                    usePlatformDefaultWidth = false
                )
            ) {
                var isVisible by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { isVisible = true }

                AnimatedVisibility(
                    visible = isVisible,
                    enter = fadeIn(tween(400)) + scaleIn(initialScale = 0.8f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)),
                    exit = fadeOut(tween(300)) + scaleOut(targetScale = 0.8f)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .clip(RoundedCornerShape(32.dp))
                            .background(Color.White.copy(alpha = 0.4f))
                            .border(1.5.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(32.dp))
                            .padding(24.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Immersive Header with Overlapping Images
                            Box(
                                modifier = Modifier
                                    .height(180.dp)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                // Background Image
                                firstTrack?.thumbnailUrl?.let { url ->
                                    AsyncImage(
                                        model = url,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(130.dp)
                                            .rotate(-10f)
                                            .offset(x = (-30).dp)
                                            .clip(RoundedCornerShape(24.dp))
                                            .border(2.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                                            .shadow(8.dp),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                                
                                // Foreground Image
                                (playlistItem?.thumbnailUrl ?: firstTrack?.thumbnailUrl)?.let { url ->
                                    AsyncImage(
                                        model = url,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(140.dp)
                                            .rotate(5f)
                                            .offset(x = 20.dp)
                                            .clip(RoundedCornerShape(24.dp))
                                            .border(2.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(24.dp))
                                            .shadow(16.dp),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "Download Playlist",
                                    style = MaterialTheme.typography.headlineSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF333333)
                                    )
                                )
                                playlistItem?.let {
                                    Text(
                                        it.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color(0xFF666666),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }

                            Text(
                                "How would you like to save these songs in your Library?",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF424242),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Action Buttons
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.startBatchDownload(asPlaylist = true) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                                    contentPadding = PaddingValues(),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                Brush.horizontalGradient(
                                                    colors = listOf(Color(0xFFE040FB), Color(0xFFFF4081))
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Folder, contentDescription = null, tint = Color.White)
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text("Save as Collection", fontWeight = FontWeight.Bold, color = Color.White)
                                        }
                                    }
                                }
                                
                                Surface(
                                    onClick = { viewModel.startBatchDownload(asPlaylist = false) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp),
                                    color = Color.White.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.3f))
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxSize(),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, tint = Color(0xFF424242))
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            "Individual Tracks",
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF424242)
                                            )
                                        )
                                    }
                                }

                                TextButton(
                                    onClick = { 
                                        isVisible = false
                                        scope.launch {
                                            delay(300)
                                            viewModel.clearPendingDownloadItems()
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Cancel", color = Color(0xFF666666), fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Selection Top Bar Overlay
        AnimatedVisibility(
            visible = isSelectionMode,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier.zIndex(10f)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(12.dp),
                shape = RoundedCornerShape(24.dp),
                color = Color.White.copy(alpha = 0.85f),
                tonalElevation = 8.dp,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { viewModel.toggleSelectionMode(false) }) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color(0xFF424242))
                    }
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    Text(
                        text = "${selectedSongIds.size} Selected",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF333333)
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    
                    IconButton(onClick = {
                        val count = selectedSongIds.size
                        viewModel.deleteSelectedItems() 
                        scope.launch {
                            snackbarHostState.showSnackbar("Deleted $count items")
                        }
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Selected", tint = Color.Red)
                    }
                }
            }
        }
    }
}

@Composable
fun AddMenuOption(text: String, icon: ImageVector, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .clip(RoundedCornerShape(18.dp)),
        color = Color.White.copy(alpha = 0.2f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(Color.Black.copy(alpha = 0.05f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Color(0xFF424242), modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF333333)
                )
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistCard(playlist: Playlist, viewModel: SongViewModel, onClick: () -> Unit) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    val playlistSongs by viewModel.getSongsInPlaylist(playlist.id).collectAsState(initial = emptyList())
    val coverImage = remember(playlistSongs) { playlistSongs.firstOrNull()?.imageUrl }

    Column(
        modifier = Modifier
            .width(120.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showDeleteDialog = true }
            ),
        horizontalAlignment = Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .shadow(12.dp, RoundedCornerShape(24.dp))
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White.copy(alpha = 0.2f))
                .border(1.5.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (coverImage != null) {
                AsyncImage(
                    model = coverImage,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    Icons.Default.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = Color.White
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = playlist.name,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                color = Color(0xFF333333)
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "Playlist",
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF666666)
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Playlist") },
            text = { Text("Are you sure you want to delete '${playlist.name}'?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePlaylist(playlist.id)
                    showDeleteDialog = false
                }) {
                    Text("Delete", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun FavoritesCollectionCard(count: Int, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(120.dp)
            .clickable { onClick() },
        horizontalAlignment = Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .shadow(12.dp, RoundedCornerShape(24.dp))
                .clip(RoundedCornerShape(24.dp))
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFFFF4081).copy(alpha = 0.8f), Color(0xFFE040FB).copy(alpha = 0.8f))
                    )
                )
                .border(1.5.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(24.dp))
        ) {
            Icon(
                Icons.Default.Favorite,
                contentDescription = null,
                modifier = Modifier.align(Alignment.Center).size(48.dp),
                tint = Color.White
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Favorites",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                color = Color(0xFF333333)
            ),
            maxLines = 1
        )
        Text(
            text = "$count songs",
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF666666)
        )
    }
}
