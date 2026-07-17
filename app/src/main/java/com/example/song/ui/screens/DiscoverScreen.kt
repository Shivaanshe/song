package com.example.song.ui.screens

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import kotlinx.coroutines.delay
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.media3.common.util.UnstableApi
import coil.compose.AsyncImage
import com.example.song.SongApplication
import com.example.song.data.model.StreamingItem
import com.example.song.util.multiSelectDragHandler
import com.example.song.viewmodel.SongViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun DiscoverScreen(
    viewModel: SongViewModel,
    onSongClick: () -> Unit
) {
    val items by viewModel.topLevelStreamingItems.collectAsState()
    val isExtracting by viewModel.isExtracting.collectAsState()
    val isEngineReady by SongApplication.getInstance().isReady.collectAsState()
    val resolvingUrlId by viewModel.resolvingUrlId.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentPlayingSong by viewModel.currentPlayingSong.collectAsState()
    var isSearching by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showAddMenu by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var youtubeUrl by remember { mutableStateOf("") }
    var selectedPlaylist by remember { mutableStateOf<StreamingItem?>(null) }
    
    val isUrlValid = remember(youtubeUrl) { 
        youtubeUrl.isBlank() || (youtubeUrl.startsWith("http") && (youtubeUrl.contains("youtube.com") || youtubeUrl.contains("youtu.be")))
    }

    val addIconRotation by animateFloatAsState(
        targetValue = if (showAddMenu) 135f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "AddIconRotation"
    )

    val backgroundGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFFD1C4E9),
            Color(0xFFBBDEFB)
        )
    )

    val filteredItems = remember(items, searchQuery) {
        if (searchQuery.isBlank()) items
        else items.filter { it.title.contains(searchQuery, ignoreCase = true) }
    }

    val playlists = remember(filteredItems) { filteredItems.filter { it.isPlaylist } }
    val singleSongs = remember(filteredItems) { 
        // Logic: Show items that are not playlists OR are individual songs from playlists
        // but only if they are not explicitly marked as part of the "Recommended" (top-level) list
        // Actually, the user wants playlist songs in Recommended too if they are "more than one".
        // Let's just show all non-playlist items in Recommended for now to ensure visibility.
        filteredItems.filter { !it.isPlaylist } 
    }

    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    val selectedStreamingIds by viewModel.selectedStreamingIds.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Auto-scroll logic handled by multiSelectDragHandler

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundGradient)
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                if (isSelectionMode) {
                    CenterAlignedTopAppBar(
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = Color.White.copy(alpha = 0.4f)
                        ),
                        title = {
                            Text(
                                "${selectedStreamingIds.size} Selected",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = { viewModel.toggleSelectionMode(false) }) {
                                Icon(Icons.Default.Close, contentDescription = "Cancel Selection")
                            }
                        },
                        actions = {
                            IconButton(onClick = { 
                                val count = selectedStreamingIds.size
                                viewModel.deleteSelectedItems() 
                                scope.launch {
                                    snackbarHostState.showSnackbar("Deleted $count items")
                                }
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Selected", tint = Color.Red)
                            }
                        }
                    )
                } else {
                    CenterAlignedTopAppBar(
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = Color.Transparent
                        ),
                        title = {
                            if (isSearching) {
                                TextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp),
                                    placeholder = { Text("Search streaming...") },
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
                                            searchQuery = ""
                                        }) {
                                            Icon(Icons.Default.Close, contentDescription = "Close search")
                                        }
                                    }
                                )
                            } else {
                                Text(
                                    selectedPlaylist?.title ?: "Discover",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF424242)
                                    )
                                )
                            }
                        },
                        navigationIcon = {
                            if (!isSearching) {
                                if (selectedPlaylist != null) {
                                    IconButton(
                                        onClick = { selectedPlaylist = null },
                                        modifier = Modifier
                                            .padding(8.dp)
                                            .background(Color.White.copy(alpha = 0.3f), CircleShape)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = "Back",
                                            tint = Color(0xFF424242)
                                        )
                                    }
                                } else {
                                    IconButton(
                                        onClick = { isSearching = true },
                                        modifier = Modifier.background(Color.White.copy(alpha = 0.3f), CircleShape)
                                    ) {
                                        Icon(Icons.Default.Search, contentDescription = "Search", tint = Color(0xFF424242))
                                    }
                                }
                            }
                        },
                        actions = {
                            if (!isSearching && selectedPlaylist == null) {
                                IconButton(
                                    onClick = { if (isEngineReady) showAddMenu = !showAddMenu },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(
                                            if (isEngineReady) Color.White.copy(alpha = 0.3f) else Color.Gray.copy(alpha = 0.2f),
                                            CircleShape
                                        ),
                                    enabled = isEngineReady
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
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                if (isExtracting) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFFE91E63)
                    )
                }

                if (selectedPlaylist == null) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .multiSelectDragHandler(
                                listState = listState,
                                isSelectionMode = isSelectionMode,
                                onSelect = { key ->
                                    if (key is Int) {
                                        viewModel.selectStreamingItem(key)
                                    }
                                },
                                onDragStart = {
                                    viewModel.toggleSelectionMode(true)
                                }
                            ),
                        contentPadding = PaddingValues(bottom = 80.dp),
                    ) {
                        if (playlists.isNotEmpty()) {
                            item {
                                Column(modifier = Modifier.padding(vertical = 16.dp)) {
                                    Text(
                                        "Collections",
                                        modifier = Modifier.padding(horizontal = 24.dp),
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF424242)
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                        contentPadding = PaddingValues(horizontal = 24.dp)
                                    ) {
                                        items(playlists) { playlist ->
                                            StreamingPlaylistCard(playlist) { 
                                                selectedPlaylist = playlist 
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (singleSongs.isNotEmpty()) {
                            item {
                                Text(
                                    "Recommended",
                                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF424242)
                                    )
                                )
                            }
                            items(singleSongs, key = { it.id }) { item ->
                                val isCurrentItemPlaying = currentPlayingSong?.audioUri == item.youtubeUrl
                                StreamingItemCard(
                                    item = item,
                                    enabled = isEngineReady,
                                    isResolving = resolvingUrlId == item.id,
                                    isPlaying = isPlaying && isCurrentItemPlaying,
                                    onClick = {
                                        if (isSelectionMode) {
                                            viewModel.toggleStreamingSelection(item.id)
                                        } else if (isEngineReady) {
                                            if (isCurrentItemPlaying) {
                                                viewModel.togglePlayPause()
                                            } else {
                                                viewModel.playStreamingItem(item, singleSongs)
                                                onSongClick()
                                            }
                                        }
                                    },
                                    onDelete = {
                                        viewModel.deleteStreamingItem(item)
                                    },
                                    isSelected = selectedStreamingIds.contains(item.id),
                                    onLongClick = {
                                        viewModel.toggleSelectionMode(true)
                                        viewModel.toggleStreamingSelection(item.id)
                                    },
                                    selectionMode = isSelectionMode
                                )
                            }
                        } else if (playlists.isEmpty() && !isExtracting) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().padding(top = 100.dp), contentAlignment = Alignment.Center) {
                                    Text("No items found", color = Color.Gray)
                                }
                            }
                        }
                    }
                } else {
                    val playlistItems by viewModel.getItemsForStreamingPlaylist(selectedPlaylist!!.youtubeUrl).collectAsState(initial = emptyList())
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .multiSelectDragHandler(
                                listState = listState,
                                isSelectionMode = isSelectionMode,
                                onSelect = { key ->
                                    if (key is Int) {
                                        viewModel.selectStreamingItem(key)
                                    }
                                },
                                onDragStart = {
                                    viewModel.toggleSelectionMode(true)
                                }
                            ),
                        contentPadding = PaddingValues(bottom = 80.dp),
                    ) {
                        item {
                            Text(
                                "Playlist Content",
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF424242)
                                )
                            )
                        }
                        items(playlistItems, key = { it.id }) { item ->
                            val isCurrentItemPlaying = currentPlayingSong?.audioUri == item.youtubeUrl
                            StreamingItemCard(
                                item = item,
                                enabled = isEngineReady,
                                isResolving = resolvingUrlId == item.id,
                                isPlaying = isPlaying && isCurrentItemPlaying,
                                onClick = {
                                    if (isSelectionMode) {
                                        viewModel.toggleStreamingSelection(item.id)
                                    } else if (isEngineReady) {
                                        if (isCurrentItemPlaying) {
                                            viewModel.togglePlayPause()
                                        } else {
                                            viewModel.playStreamingItem(item, playlistItems)
                                            onSongClick()
                                        }
                                    }
                                },
                                onDelete = {
                                    viewModel.deleteStreamingItem(item)
                                },
                                isSelected = selectedStreamingIds.contains(item.id),
                                onLongClick = {
                                    viewModel.toggleStreamingSelection(item.id)
                                },
                                selectionMode = isSelectionMode
                            )
                        }
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

        // Add Options Menu (Matching Library)
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
                    // Logic Rule: Submit through this dialog should call StreamingItem saving logic
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { 
                                showAddMenu = false
                                showAddDialog = true
                            }
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
                                Icon(Icons.Default.Link, contentDescription = null, tint = Color(0xFF424242), modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = "Add from YouTube",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF424242)
                                )
                            )
                        }
                    }
                }
            }
        }

        if (showAddDialog) {
            AlertDialog(
                onDismissRequest = { 
                    if (!isExtracting) {
                        showAddDialog = false 
                    }
                },
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, tint = Color(0xFFE91E63))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Stream from YT")
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
                                "Enter the YouTube video or playlist URL to add to your Discover list.",
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
                                    "Invalid YouTube URL",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(top = 4.dp, start = 8.dp)
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    if (!isExtracting) {
                        Button(
                            onClick = {
                                if (youtubeUrl.isNotBlank() && isUrlValid && isEngineReady) {
                                    val urlToAdd = youtubeUrl.trim()
                                    youtubeUrl = ""
                                    viewModel.addStreamingItem(urlToAdd)
                                    showAddDialog = false
                                }
                            },
                            enabled = youtubeUrl.isNotBlank() && isUrlValid && isEngineReady,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFE91E63),
                                disabledContainerColor = Color(0xFFE91E63).copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(if (isEngineReady) "Add to Discover" else "Initializing...")
                        }
                    }
                },
                dismissButton = {
                    if (!isExtracting) {
                        TextButton(onClick = { showAddDialog = false }) {
                            Text("Close", color = Color.Gray)
                        }
                    }
                },
                shape = RoundedCornerShape(28.dp),
                containerColor = Color.White
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StreamingItemCard(
    item: StreamingItem, 
    enabled: Boolean = true, 
    isResolving: Boolean = false,
    isPlaying: Boolean = false,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    isSelected: Boolean = false,
    onLongClick: () -> Unit = {},
    selectionMode: Boolean = false
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .clickable(
                enabled = enabled && !isResolving,
                onClick = onClick
            )
            .border(
                if (isSelected) 2.dp else 1.5.dp,
                if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.3f),
                RoundedCornerShape(20.dp)
            ),
        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.3f),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Gray.copy(alpha = 0.2f))
            ) {
                if (item.thumbnailUrl != null) {
                    AsyncImage(
                        model = item.thumbnailUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.MusicNote, 
                            contentDescription = null, 
                            tint = Color.White.copy(alpha = 0.5f)
                        )
                    }
                }
                if (item.isPlaylist) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = null, tint = Color.White)
                    }
                }
                
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF424242)
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (item.isPlaylist) "YouTube Playlist" else "YouTube Stream",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color(0xFF666666)
                    )
                )
            }
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isResolving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFFE91E63)
                    )
                } else {
                    IconButton(
                        onClick = onClick,
                        modifier = Modifier.background(Color.White.copy(alpha = 0.5f), CircleShape).size(32.dp),
                        enabled = enabled
                    ) {
                        Icon(
                            imageVector = when {
                                isPlaying -> Icons.Default.Pause
                                else -> Icons.Default.PlayArrow
                            },
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color(0xFF424242),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Stream") },
            text = { Text("Are you sure you want to remove '${item.title}' from your Discover list?") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
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
fun StreamingPlaylistCard(item: StreamingItem, onClick: () -> Unit) {
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
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFFF06292), Color(0xFFBA68C8))
                    )
                )
                .border(1.5.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (!item.thumbnailUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = item.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                // Overlay a small playlist icon in the corner
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    contentAlignment = Alignment.BottomEnd
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.PlaylistPlay,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = Color.White
                    )
                }
            } else {
                Icon(
                    Icons.Default.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = Color.White.copy(alpha = 0.5f)
                )
                // Overlay a small playlist icon in the corner even if no thumb
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    contentAlignment = Alignment.BottomEnd
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.PlaylistPlay,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = Color.White
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                color = Color(0xFF424242)
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "YouTube Playlist",
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF666666)
        )
    }
}

